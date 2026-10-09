#!/usr/bin/env python3
"""Generate/search a source-backed QA project index without building or launching the app."""

from __future__ import annotations

import argparse
import copy
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import re
import sys

class QaError(ValueError):
    pass


def project_path(root: Path, value: str) -> Path:
    if not isinstance(value, str) or not value or Path(value).is_absolute():
        raise QaError(f"Expected a project-relative path: {value!r}")
    path = (root / value).resolve()
    if not path.is_relative_to(root):
        raise QaError(f"Path leaves the project: {value}")
    return path


def read_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise QaError(f"Cannot read {path}: {error}") from error


def strings(value, label: str, *, nonempty: bool = True) -> list[str]:
    if not isinstance(value, list) or (nonempty and not value):
        raise QaError(f"{label} must be a {'nonempty ' if nonempty else ''}list")
    if any(not isinstance(item, str) or not item.strip() for item in value):
        raise QaError(f"{label} must contain nonempty strings")
    if len(value) != len(set(value)):
        raise QaError(f"{label} contains duplicates")
    return value


def generate(root: Path, config_path: Path) -> dict:
    config = read_json(config_path)
    if not isinstance(config, dict) or config.get("schema") != 1:
        raise QaError("Project config schema must be 1")
    contents = {}
    hashes = {}

    def source(value):
        path = project_path(root, value)
        if not path.is_file():
            raise QaError(f"Missing indexed source: {value}")
        if value not in contents:
            raw = path.read_bytes()
            contents[value] = raw.decode("utf-8")
            hashes[value] = hashlib.sha256(raw).hexdigest()
        return contents[value]

    def reference(ref):
        if not isinstance(ref, dict):
            raise QaError("Source references must be objects")
        text = source(ref.get("path"))
        needle = ref.get("needle")
        if not isinstance(needle, str) or not needle.strip() or needle not in text:
            raise QaError(f"Missing source anchor: {ref}")
        offset = text.index(needle)
        result = {"path": ref["path"], "needle": needle,
                  "line": text.count("\n", 0, offset) + 1,
                  "match_count": text.count(needle)}
        if "hosts" in ref:
            result["hosts"] = strings(ref["hosts"], "reference.hosts")
        return result

    def references(refs):
        if not isinstance(refs, list) or not refs:
            raise QaError("Owners/recipes need source references")
        return [reference(ref) for ref in refs]

    def identifier(value):
        if not isinstance(value, str) or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", value):
            raise QaError(f"Invalid ID: {value}")
        return value

    def text_field(item, field):
        value = item.get(field)
        if not isinstance(value, str) or not value.strip():
            raise QaError(f"Missing {field}")
        return value

    hosts = config.get("hosts")
    if not isinstance(hosts, dict) or not hosts:
        raise QaError("Project config needs hosts")
    hosts = copy.deepcopy(hosts)
    for host_id, host in hosts.items():
        identifier(host_id)
        if not isinstance(host, dict):
            raise QaError("Host must be an object")
        strings(host.get("launch"), "host.launch")
        strings(host.get("preconditions"), "host.preconditions")
        host["evidence"] = references(host.get("evidence"))

    def host_list(item):
        selected = strings(item.get("hosts"), "hosts")
        if set(selected) - hosts.keys():
            raise QaError(f"Unknown hosts: {selected}")
        return selected

    screens = config.get("screens")
    if not isinstance(screens, list) or not screens:
        raise QaError("Project config needs screens")
    screens = copy.deepcopy(screens)
    ids = set()
    for screen in screens:
        if not isinstance(screen, dict):
            raise QaError("Screen must be an object")
        screen_id = identifier(screen.get("id"))
        if screen_id in ids:
            raise QaError(f"Duplicate screen: {screen_id}")
        ids.add(screen_id)
        text_field(screen, "name")
        selected = host_list(screen)
        strings(screen.get("recognition"), "recognition")
        screen["owners"] = references(screen.get("owners"))
        if any(set(owner.get("hosts", selected)) - set(selected) for owner in screen["owners"]):
            raise QaError("Owner hosts must belong to the screen")
        strings(screen.get("limits"), "screen.limits")
        entry = screen.get("entry")
        if not isinstance(entry, dict):
            raise QaError("Screen needs an entry recipe")
        strings(entry.get("steps"), "entry.steps")
        strings(entry.get("preconditions"), "entry.preconditions", nonempty=False)
        entry["evidence"] = references(entry.get("evidence"))
        states = screen.get("states", [])
        if not isinstance(states, list):
            raise QaError("states must be a list")
        state_ids = set()
        for state in states:
            if not isinstance(state, dict):
                raise QaError("State must be an object")
            state_id = identifier(state.get("id"))
            if state_id in state_ids:
                raise QaError(f"Duplicate state: {state_id}")
            state_ids.add(state_id)
            if set(host_list(state)) - set(selected):
                raise QaError("State hosts must belong to the screen")
            if state.get("mechanism") not in ("ui", "environment", "fixture", "unavailable"):
                raise QaError("State mechanism must be ui/environment/fixture/unavailable")
            if state.get("scope") not in ("live-app", "test", "preview", "none"):
                raise QaError("State scope must be live-app/test/preview/none")
            if (state["mechanism"] == "unavailable") != (state["scope"] == "none"):
                raise QaError("Unavailable states must have scope none, and vice versa")
            strings(state.get("steps"), "state.steps")
            text_field(state, "observe")
            strings(state.get("limits"), "state.limits")
            state["evidence"] = references(state.get("evidence"))

    scan = config.get("scan", {})
    if not isinstance(scan, dict):
        raise QaError("scan must be an object")
    rules = []
    extractors = scan.get("extractors", [])
    if not isinstance(extractors, list):
        raise QaError("scan.extractors must be a list")
    for rule in extractors:
        if not isinstance(rule, dict):
            raise QaError("Extractor must be an object")
        kind = text_field(rule, "kind")
        files = text_field(rule, "files")
        try:
            pattern = re.compile(text_field(rule, "pattern"))
        except re.error as error:
            raise QaError(f"Invalid extractor: {error}") from error
        if pattern.groups != 1 and not (pattern.groups == 2 and set(pattern.groupindex) == {"key", "value"}):
            raise QaError("Extractor needs one capture group, or named key/value groups")
        rules.append((kind, files, pattern))
    discovery = []
    scanned = set()
    for pattern in strings(scan.get("include", []), "scan.include", nonempty=False):
        if Path(pattern).is_absolute() or ".." in Path(pattern).parts:
            raise QaError("Scan patterns must stay project-relative")
        for path in sorted(root.glob(pattern)):
            if not path.is_file() or path.is_symlink() or {"build", "bin", ".git"} & set(path.relative_to(root).parts):
                continue
            value = path.relative_to(root).as_posix()
            if value in scanned:
                continue
            scanned.add(value)
            text = source(value)
            for kind, files, extractor in rules:
                if not fnmatch.fnmatch(value, files):
                    continue
                for match in extractor.finditer(text):
                    extracted = match.groupdict().get("value", match[1])
                    if not extracted or not extracted.strip():
                        continue
                    record = {"kind": kind, "value": extracted, "path": value,
                              "line": text.count("\n", 0, match.start()) + 1,
                              "evidence_level": "textual-source-match"}
                    if "key" in match.groupdict():
                        record["key"] = match["key"]
                    discovery.append(record)
    source_files = dict(sorted(hashes.items()))
    return {"schema": 1, "config": config_path.relative_to(root).as_posix(),
            "config_sha256": hashlib.sha256(config_path.read_bytes()).hexdigest(),
            "source_files": source_files, "hosts": hosts, "screens": screens,
            "discovery": sorted(discovery, key=lambda item: (item["path"], item["line"], item["kind"])),
            "limits": ["Source matches are lexical candidates, not an AST/call graph or runtime screen identity.",
                       "Screen recognition, entry and state recipes are declared configuration with checked source anchors; anchors do not prove behavior.",
                       "This index describes actions; it does not execute them or add state-forcing controls."]}


def screen_facts(index: dict, screen: dict, host: str | None) -> list:
    owners = {owner["path"] for owner in screen["owners"] if not host or host in owner.get("hosts", screen["hosts"])}
    facts = [fact for fact in index["discovery"] if fact["path"] in owners]
    resource_keys = {fact["value"] for fact in facts if fact["kind"] == "resource-reference"}
    facts.extend(fact for fact in index["discovery"] if fact.get("key") in resource_keys)
    return facts


def search(index: dict, query: str, host: str | None, limit: int) -> dict:
    if host and host not in index["hosts"]:
        raise QaError(f"Unknown host: {host}")
    terms = query.casefold().split() if isinstance(query, str) else []
    if not terms:
        raise QaError("Search needs visible text, a symbol, selector or state")
    matches = []
    for screen in index["screens"]:
        if host and host not in screen["hosts"]:
            continue
        facts = screen_facts(index, screen, host)
        primary_values = [screen["id"], screen["name"], *screen["recognition"]]
        primary = " ".join(primary_values).casefold()
        selected = copy.deepcopy(screen)
        selected["states"] = [state for state in screen.get("states", []) if not host or host in state["hosts"]]
        selected["owners"] = [owner for owner in screen["owners"] if not host or host in owner.get("hosts", screen["hosts"])]
        contextual_values = [*screen["entry"]["steps"], *screen["entry"]["preconditions"], *screen["limits"],
                             *(owner["needle"] for owner in selected["owners"])]
        for state in selected["states"]:
            contextual_values.extend([state["id"], state["observe"], *state["steps"], *state["limits"]])
        supplemental = " ".join(contextual_values).casefold()
        fact_text = " ".join(fact["value"] for fact in facts).casefold()
        candidate_values = [*primary_values, *contextual_values, *(fact["value"] for fact in facts)]
        if not any(all(term in value.casefold() for term in terms) for value in candidate_values):
            continue
        score = sum(4 if term in primary else 2 if term in supplemental else 1 if term in fact_text else 0
                    for term in terms)
        phrase = query.casefold().strip()
        if any(phrase in value.casefold() for value in primary_values):
            score += 12
        elif any(phrase in fact["value"].casefold() for fact in facts):
            score += 8
        if score:
            matches.append({"screen": screen["id"], "name": screen["name"], "hosts": screen["hosts"],
                            "score": score, "recognition": screen["recognition"],
                            "matched_source": [fact for fact in facts if all(term in fact["value"].casefold() for term in terms)][:5],
                            "states": [{key: state[key] for key in ("id", "hosts", "mechanism", "scope")}
                                       for state in selected["states"]]})
    discoveries = [fact for fact in index["discovery"]
                   if all(term in fact["value"].casefold() for term in terms)]
    return {"query": query, "host": host, "screens": sorted(matches, key=lambda item: (-item["score"], item["screen"]))[:limit],
            "source_matches": discoveries[:limit],
            "limits": ["Scores rank text candidates; they are not probabilities or confirmed screenshot identities.",
                       "Source matches can be unregistered components/previews. Confirm the destination in the live app."]}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--config", default="qa/project.config.json")
    commands = parser.add_subparsers(dest="action", required=True)
    generate_command = commands.add_parser("generate")
    generate_command.add_argument("--output", default="build/qa/project.json")
    find = commands.add_parser("search")
    find.add_argument("query", nargs="?", default=os.environ.get("QA_QUERY"))
    find.add_argument("--host", default=os.environ.get("QA_HOST") or None)
    find.add_argument("--limit", type=int, default=5)
    show = commands.add_parser("show")
    show.add_argument("screen", nargs="?", default=os.environ.get("QA_SCREEN"))
    show.add_argument("--host", default=os.environ.get("QA_HOST") or None)
    commands.add_parser("validate")
    args = parser.parse_args(argv)
    root = args.root.resolve()
    try:
        index = generate(root, project_path(root, args.config))
        if args.action == "generate":
            output = project_path(root, args.output)
            if output == project_path(root, args.config) or output.relative_to(root).as_posix() in index["source_files"]:
                raise QaError("Index output cannot overwrite config/source")
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
            print(f"Indexed {len(index['screens'])} screens and {len(index['discovery'])} source matches: {output.relative_to(root)}")
        elif args.action == "search":
            if not 1 <= args.limit <= 50:
                raise QaError("Search limit must be 1..50")
            print(json.dumps(search(index, args.query, args.host, args.limit), indent=2, ensure_ascii=False))
        elif args.action == "show":
            if args.host and args.host not in index["hosts"]:
                raise QaError(f"Unknown host: {args.host}")
            screen = next((item for item in index["screens"] if item["id"] == args.screen
                           and (not args.host or args.host in item["hosts"])), None)
            if screen is None:
                raise QaError(f"Unknown screen/host: {args.screen}/{args.host}")
            result = copy.deepcopy(screen)
            if args.host:
                result["states"] = [state for state in result.get("states", []) if args.host in state["hosts"]]
                result["owners"] = [owner for owner in result["owners"] if args.host in owner.get("hosts", screen["hosts"])]
            result["host_setup"] = {host: index["hosts"][host] for host in screen["hosts"] if not args.host or host == args.host}
            result["source_matches"] = screen_facts(index, screen, args.host)
            result["index_limits"] = index["limits"]
            print(json.dumps(result, indent=2, ensure_ascii=False))
        else:
            print(f"Validated project index: {len(index['screens'])} screens, {len(index['source_files'])} source files.")
    except (QaError, OSError) as error:
        print(f"QA index: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
