#!/usr/bin/env python3
"""Freeze screenshot evidence and build a comparable, offline UI review report."""

from __future__ import annotations

import argparse
import base64
import hashlib
import html
import json
import re
import shutil
import struct
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse


class ReviewError(ValueError):
    """Evidence is incomplete or cannot support a comparable review."""


def read_json(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ReviewError(f"Expected a JSON object: {path}")
    return value


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def png_size(path: Path) -> list[int]:
    header = path.read_bytes()[:24]
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ReviewError(f"Not a PNG: {path}")
    size = list(struct.unpack(">II", header[16:24]))
    if not all(size):
        raise ReviewError(f"Empty PNG dimensions: {path}")
    return size


def inside(root: Path, relative: str) -> Path:
    path = (root / relative).resolve()
    try:
        path.relative_to(root.resolve())
    except ValueError as error:
        raise ReviewError(f"Path escapes its evidence root: {relative}") from error
    return path


def source_hashes(root: Path, patterns: list[str]) -> dict[str, str]:
    result = {}
    for pattern in patterns:
        matches = sorted(path for path in root.glob(pattern) if path.is_file())
        if not matches:
            raise ReviewError(f"Source pattern matched no files: {pattern}")
        for path in matches:
            relative = str(path.relative_to(root))
            inside(root, relative)
            result[relative] = digest(path)
    if not result:
        raise ReviewError("Declare source and fixture files to establish capture provenance")
    return result


def git_state(root: Path) -> dict:
    def git(*args):
        result = subprocess.run(["git", "-C", str(root), *args], capture_output=True, text=True)
        return result.stdout.strip() if result.returncode == 0 else None
    return {"revision": git("rev-parse", "HEAD"), "working_tree": git("status", "--short")}


def capture(config: Path, screen: str, output: Path, run: str, root: Path | None = None) -> Path:
    config = config.resolve()
    root = root.resolve() if root else config.parent.parent
    settings = read_json(config)
    if settings.get("schema") != 1 or screen not in settings.get("screens", {}):
        raise ReviewError(f"Unknown screen or config schema: {screen}")
    spec = settings["screens"][screen]
    for field in ("platform", "renderer", "image_directory", "sources", "fixtures", "cases"):
        if not spec.get(field):
            raise ReviewError(f"Screen is missing {field}")
    cases = spec["cases"]
    ids = [case.get("id", "") for case in cases]
    if len(set(ids)) != len(ids) or any(not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", key) for key in ids):
        raise ReviewError("Case IDs must be unique lowercase names")
    for case in cases:
        if not case.get("match") or not isinstance(case.get("conditions"), dict) or not case["conditions"]:
            raise ReviewError("Each case needs a literal filename match and explicit conditions")
    source_before = source_hashes(root, spec["sources"])
    fixtures = source_hashes(root, spec["fixtures"])
    output = output.resolve()
    if output.exists():
        raise ReviewError(f"Capture output already exists; use a new directory: {output}")
    image_root = inside(root, spec["image_directory"])
    if image_root == output or image_root in output.parents:
        raise ReviewError("Capture output must be separate from renderer images")
    output.mkdir(parents=True)
    command = None
    if run != "existing":
        command = spec.get(run)
        if not isinstance(command, list) or not command or any(not isinstance(arg, str) for arg in command):
            raise ReviewError(f"Missing argument-array command for {run}")
        with (output / "render.log").open("w", encoding="utf-8") as log:
            result = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode:
            raise ReviewError(f"Renderer failed with exit {result.returncode}; see {output / 'render.log'}")
    if source_before != source_hashes(root, spec["sources"]) or fixtures != source_hashes(root, spec["fixtures"]):
        raise ReviewError("Source or fixtures changed during rendering; recapture")
    images = sorted(image_root.glob("*.png"))
    records = []
    used = set()
    for case in cases:
        matches = [path for path in images if case["match"] in path.name]
        if len(matches) != 1:
            raise ReviewError(f"Case {case['id']} matched {len(matches)} images; expected exactly one")
        source = matches[0]
        if source in used:
            raise ReviewError(f"Multiple cases select the same image: {source.name}")
        used.add(source)
        size = png_size(source)
        target = output / (case["id"] + ".png")
        shutil.copyfile(source, target)
        records.append({"id": case["id"], "image": target.name, "sha256": digest(target),
                        "pixels": size, "conditions": case["conditions"],
                        "source_image": str(source.relative_to(root))})
    manifest = {"schema": 1, "screen": screen, "platform": spec["platform"],
                "renderer": spec["renderer"], "captured_at": datetime.now(timezone.utc).isoformat(),
                "freshness": "existing-unverified" if run == "existing" else run,
                "command": command, "git": git_state(root), "sources": source_before,
                "fixtures": fixtures, "cases": records}
    path = output / "capture.json"
    path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return path


def load_capture(path: Path) -> tuple[dict, dict[str, Path]]:
    data = read_json(path)
    if data.get("schema") != 1 or not data.get("cases") or not data.get("fixtures"):
        raise ReviewError(f"Incomplete capture manifest: {path}")
    images = {}
    for case in data["cases"]:
        key = case["id"]
        if key in images:
            raise ReviewError(f"Duplicate capture case: {key}")
        image = inside(path.parent, case["image"])
        if digest(image) != case["sha256"] or png_size(image) != case["pixels"]:
            raise ReviewError(f"Image changed after capture: {image}")
        images[key] = image
    return data, images


def assert_comparable(before: dict, after: dict) -> None:
    for field in ("screen", "platform", "renderer", "fixtures"):
        if before.get(field) != after.get(field):
            raise ReviewError(f"Captures differ in {field}; cannot compare as the same input")
    a = {case["id"]: (case["conditions"], case["pixels"]) for case in before["cases"]}
    b = {case["id"]: (case["conditions"], case["pixels"]) for case in after["cases"]}
    if a != b:
        raise ReviewError("Capture case sets, conditions, or dimensions differ")


def required_text(data: dict, field: str) -> str:
    value = data.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ReviewError(f"Review needs nonempty {field}")
    return value


def text_list(data: dict, field: str) -> list[str]:
    value = data.get(field)
    if not isinstance(value, list) or any(not isinstance(item, str) or not item.strip() for item in value):
        raise ReviewError(f"Review {field} must be a list of text values")
    return value


def default_output(root: Path | None = None) -> Path:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    return (root or Path.cwd()) / "design" / "reviews" / stamp


def stash_experiment(root: Path, experiment_id: str, paths: list[str], output: Path | None = None) -> Path:
    """Save only a named experiment from an isolated checkout; retain the stash after recovery."""
    root = root.resolve()
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", experiment_id):
        raise ReviewError("Experiment ID must be a lowercase name")
    if not paths:
        raise ReviewError("Declare the experiment source paths")
    allowed = []
    for value in paths:
        path = inside(root, value)
        if path == root or ".git" in path.relative_to(root).parts:
            raise ReviewError("Experiment paths cannot select the whole checkout or Git metadata")
        allowed.append(path)
    output = (output or default_output(root) / experiment_id / "stash.json").resolve()
    if output.exists():
        raise ReviewError(f"Stash manifest already exists: {output}")

    def git(*args, optional=False):
        result = subprocess.run(["git", "-C", str(root), *args], capture_output=True)
        if result.returncode and not optional:
            raise ReviewError(result.stderr.decode(errors="replace").strip() or "Git command failed")
        return result.stdout

    top = Path(git("rev-parse", "--show-toplevel").decode().strip()).resolve()
    if top != root:
        raise ReviewError("Root must be the experiment checkout root")
    message = "ui-design-review: " + experiment_id
    if any(line.endswith(message) for line in git("stash", "list", "--format=%gs").decode().splitlines()):
        raise ReviewError("Experiment ID already has a stash; use a new ID for a revision")
    changed = set(git("diff", "--name-only", "--no-renames", "-z", "HEAD").split(b"\0"))
    changed.update(git("ls-files", "--others", "--exclude-standard", "-z").split(b"\0"))
    changed.discard(b"")
    if not changed:
        raise ReviewError("Experiment has no source changes")
    files = sorted(value.decode() for value in changed)
    for name in files:
        path = inside(root, name)
        if not any(path == selected or selected in path.parents for selected in allowed):
            raise ReviewError(f"Unrelated changes in experiment checkout: {name}")
    sources = {name: digest(root / name) if (root / name).is_file() else None for name in files}
    base = git("rev-parse", "HEAD").decode().strip()
    previous = git("rev-parse", "--verify", "refs/stash", optional=True).decode().strip()
    git("stash", "push", "--include-untracked", "--message", message, "--", *paths)
    oid = git("rev-parse", "--verify", "refs/stash").decode().strip()
    if oid == previous:
        raise ReviewError("Git did not create an experiment stash")
    if git("status", "--porcelain", "--untracked-files=all"):
        raise ReviewError(f"Stash {oid} was saved, but the isolated checkout is not clean")
    manifest = {"schema": 1, "id": experiment_id, "stash_oid": oid, "base_revision": base,
                "saved_at": datetime.now(timezone.utc).isoformat(), "sources": sources,
                "restore": ["git", "stash", "apply", oid]}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return output


def report(baseline: Path, review_path: Path, output: Path | None = None) -> Path:
    baseline = baseline.resolve()
    review_path = review_path.resolve()
    before, before_images = load_capture(baseline)
    review = read_json(review_path)
    for field in ("title", "scope", "recommendation", "conclusion"):
        required_text(review, field)
    for field in ("strengths", "findings", "limitations"):
        text_list(review, field)
    prior_evidence = text_list(review, "prior_evidence") if "prior_evidence" in review else []
    references = review.get("references", [])
    if not isinstance(references, list):
        raise ReviewError("References must be a list")
    for reference in references:
        for field in ("title", "url", "contribution"):
            required_text(reference, field)
        url = urlparse(reference["url"])
        if url.scheme not in ("https", "http") or not url.netloc:
            raise ReviewError("Reference URL must use http or https")
    options = review.get("options", [])
    if not options or not isinstance(options, list):
        raise ReviewError("Review needs rendered options")
    ids = [required_text(option, "id") for option in options]
    if len(ids) != len(set(ids)) or "baseline" in ids:
        raise ReviewError("Option IDs must be unique and cannot be baseline")
    if review["recommendation"] not in ["baseline", *ids]:
        raise ReviewError("Recommendation must name a rendered option or baseline")
    recommendation_title = next(
        (option["title"] for option in options if option["id"] == review["recommendation"]),
        "Keep the current screen",
    )
    loaded = []
    for option in options:
        for field in ("title", "capture", "critique", "assessment"):
            required_text(option, field)
        for field in ("changes", "tradeoffs"):
            text_list(option, field)
        candidate_path = (review_path.parent / option["capture"]).resolve()
        after, after_images = load_capture(candidate_path)
        assert_comparable(before, after)
        loaded.append((option, after, after_images))
    output = (output or default_output()).resolve()
    output.mkdir(parents=True, exist_ok=True)
    esc = html.escape
    def items(values):
        return "<ul>" + "".join("<li>" + esc(value) + "</li>" for value in values) + "</ul>"
    def uri(path):
        return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")
    def pair(key, after_images):
        return (f'<div class="pair"><figure><figcaption>Current screen</figcaption><img alt="Current {esc(key)}" src="{uri(before_images[key])}"></figure>'
                f'<figure><figcaption>Rendered proposal</figcaption><img alt="Proposed {esc(key)}" src="{uri(after_images[key])}"></figure></div>')
    sections = []
    md = ["# " + review["title"], "", review["scope"], "", "## Conclusion", "", review["conclusion"], "",
          "Recommended option: " + recommendation_title, "", "## What already works", ""]
    md.extend("- " + value for value in review["strengths"])
    md.extend(["", "## Findings", "", *("- " + value for value in review["findings"])])
    if prior_evidence:
        md.extend(["", "## Lessons from earlier experiments", "", *("- " + value for value in prior_evidence)])
    if references:
        md.extend(["", "## Design references", "", *(
            "- [" + ref["title"] + "](" + ref["url"] + "): " + ref["contribution"] for ref in references)])
    reference_html = "".join(
        f'<li><a href="{esc(ref["url"], quote=True)}">{esc(ref["title"])}</a>: {esc(ref["contribution"])}</li>'
        for ref in references)
    context_html = ("<h2>Lessons from earlier experiments</h2>" + items(prior_evidence) if prior_evidence else "")
    context_html += ("<h2>Design references</h2><ul>" + reference_html + "</ul>" if references else "")
    images_out = output / "images"
    images_out.mkdir(exist_ok=True)
    for option, after, after_images in loaded:
        patch_detail = ""
        if "patch" in option:
            patch_path = (review_path.parent / required_text(option, "patch")).resolve()
            patch_text = patch_path.read_text(encoding="utf-8")
            patch_name = "option-" + str(ids.index(option["id"])) + ".patch"
            (output / patch_name).write_text(patch_text, encoding="utf-8")
            patch_detail = f'<details><summary>Source patch</summary><pre>{esc(patch_text)}</pre></details>'
        main_case = before["cases"][0]["id"]
        state_pairs = []
        for case in before["cases"][1:]:
            key = case["id"]
            label = key + " · " + json.dumps(case["conditions"], sort_keys=True)
            state_pairs.append(f'<details><summary>{esc(label)}</summary>{pair(key, after_images)}</details>')
        imports = before.get("freshness") == "existing-unverified" or after.get("freshness") == "existing-unverified"
        note = '<p class="warning">Existing images imported without a fresh renderer run.</p>' if imports else ""
        sections.append(f'<section id="{esc(option["id"], quote=True)}"><h2>{esc(option["title"])}</h2>{note}{pair(main_case, after_images)}'
                        f'<div class="notes"><div><h3>Critique of the current screen</h3><p>{esc(option["critique"])}</p>'
                        f'<h3>Changes rendered</h3>{items(option["changes"])}</div>'
                        f'<div><h3>Result</h3><p>{esc(option["assessment"])}</p><h3>Tradeoffs</h3>{items(option["tradeoffs"])}</div></div>'
                        f'<h3>Other states</h3>{"".join(state_pairs)}{patch_detail}</section>')
        md.extend(["", "## " + option["title"], "", "Critique: " + option["critique"], "", "Changes:", "",
                   *("- " + value for value in option["changes"]), "", "Result: " + option["assessment"], "", "Tradeoffs:", "",
                   *("- " + value for value in option["tradeoffs"]), ""])
        if imports:
            md.extend(["Existing images imported without a fresh renderer run.", ""])
        if patch_detail:
            md.extend([f"[Source patch]({patch_name})", ""])
        # Markdown images are copied so moving the report directory preserves its evidence.
        for case in before["cases"]:
            key = case["id"]
            before_name = "before-" + key + ".png"
            after_name = "option-" + str(ids.index(option["id"])) + "-" + key + ".png"
            shutil.copyfile(before_images[key], images_out / before_name)
            shutil.copyfile(after_images[key], images_out / after_name)
            md.extend(["### " + key, "", f"![Current screen](images/{before_name})", "", f"![Rendered proposal](images/{after_name})", ""])
    md.extend(["## Limits and verification", "", *("- " + value for value in review["limitations"])])
    provenance = {"baseline": before, "options": [{"id": option["id"], "capture": after} for option, after, _ in loaded]}
    document = '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TITLE</title><style>
*{box-sizing:border-box}body{margin:0;background:#f2f4f7;color:#192537;font:16px/1.6 system-ui,sans-serif}main{max-width:1120px;margin:auto;padding:44px 24px}h1{font-size:36px;line-height:1.2}h2{font-size:26px}h3{font-size:17px;margin:20px 0 8px}p{margin:8px 0}section,.conclusion{background:white;padding:28px;border:1px solid #dae0e7;border-radius:10px;margin:28px 0}.pair,.notes{display:grid;grid-template-columns:1fr 1fr;gap:24px}.pair{background:#edf1f5;padding:18px;margin-top:20px}.pair figure{margin:0;min-width:0}figcaption{font-size:14px;font-weight:600;margin-bottom:10px}img{display:block;max-width:100%;height:auto}.notes{margin:12px 0}.notes p,li{color:#425166}details{border-top:1px solid #dae0e7;padding:12px 0}summary{cursor:pointer;color:#1856a6;font-size:14px;overflow-wrap:anywhere}.warning{color:#8b3c00}pre{font-size:12px;white-space:pre-wrap;overflow-wrap:anywhere}li+li{margin-top:7px}@media(max-width:650px){main{padding:20px 12px}section,.conclusion{padding:16px}.pair{gap:10px;padding:10px}.notes{grid-template-columns:1fr;gap:0}h1{font-size:28px}}
</style><main><h1>TITLE</h1><p>SCOPE</p><p>PLATFORM screenshot evidence. Comparisons use matching cases, conditions, dimensions, and fixtures.</p>
<div class="conclusion"><h2>Recommendation: RECOMMENDATION</h2><p>CONCLUSION</p></div>
<h2>What already works</h2>STRENGTHS<h2>Findings</h2>FINDINGS
CONTEXT
SECTIONS<h2>Limits and verification</h2>LIMITS<details><summary>Capture provenance</summary><pre>PROVENANCE</pre></details></main></html>'''
    replacements = {"TITLE": esc(review["title"]), "SCOPE": esc(review["scope"]),
                    "PLATFORM": esc({"android": "Android", "ios": "iOS", "web": "Web", "desktop": "Desktop"}.get(before["platform"], before["platform"])),
                    "RECOMMENDATION": esc(recommendation_title),
                    "CONCLUSION": esc(review["conclusion"]), "STRENGTHS": items(review["strengths"]),
                    "FINDINGS": items(review["findings"]), "CONTEXT": context_html, "SECTIONS": "".join(sections),
                    "LIMITS": items(review["limitations"]), "PROVENANCE": esc(json.dumps(provenance, indent=2))}
    document = re.sub("|".join(replacements), lambda match: replacements[match.group()], document)
    (output / "report.html").write_text(document, encoding="utf-8")
    (output / "report.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    return output / "report.html"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    record = commands.add_parser("capture", help="Freeze a configured screen's PNGs and provenance")
    record.add_argument("--config", type=Path, required=True)
    record.add_argument("--screen", required=True)
    record.add_argument("--output", type=Path, required=True)
    record.add_argument("--root", type=Path, help="Checkout root; defaults to the config's grandparent")
    record.add_argument("--run", choices=("record", "verify", "existing"), default="verify")
    build = commands.add_parser("report", help="Validate captures and write an offline comparison")
    build.add_argument("--baseline", type=Path, required=True)
    build.add_argument("--review", type=Path, required=True)
    build.add_argument("--output", type=Path, help="Defaults to design/reviews/<UTC run>/")
    preserve = commands.add_parser("stash", help="Preserve an isolated experiment as a named Git stash")
    preserve.add_argument("--root", type=Path, required=True)
    preserve.add_argument("--id", required=True)
    preserve.add_argument("--paths", nargs="+", required=True)
    preserve.add_argument("--output", type=Path, help="Stash manifest; defaults under design/reviews/")
    args = parser.parse_args()
    try:
        if args.command == "capture":
            path = capture(args.config, args.screen, args.output, args.run, args.root)
        elif args.command == "stash":
            path = stash_experiment(args.root, args.id, args.paths, args.output)
        else:
            path = report(args.baseline, args.review, args.output)
        print(path)
        return 0
    except (ReviewError, OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"UI review failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
