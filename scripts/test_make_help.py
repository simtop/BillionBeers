#!/usr/bin/env python3
"""Check the public Make help interface without building or installing anything."""

from __future__ import annotations

from collections import Counter
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
ANSI = re.compile(r"\x1b\[[0-9;]*m")
TARGET = re.compile(r"^([A-Za-z0-9_-]+):.*## (.+)$", re.MULTILINE)


def run_make(*args: str, directory: Path = ROOT) -> str:
    environment = os.environ.copy()
    environment.pop("MAKEFLAGS", None)
    # Any unintended build invocation must fail instead of invoking Gradle.
    result = subprocess.run(
        ["make", "--no-print-directory", "GRADLE_RUNNER=false", *args],
        cwd=directory, env=environment, capture_output=True, text=True, timeout=10,
    )
    if result.returncode:
        raise AssertionError(result.stdout + result.stderr)
    return ANSI.sub("", result.stdout)


def help_commands(output: str) -> list[str]:
    return re.findall(r"^([A-Za-z0-9_-]+)\s{2,}\S.*$", output, re.MULTILINE)


class MakeHelpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = (ROOT / "Makefile").read_text()
        cls.help = run_make("help")

    def test_every_documented_target_appears_once(self) -> None:
        expected = [name for name, _ in TARGET.findall(self.source)]
        self.assertEqual(Counter(expected), Counter(help_commands(self.help)))
        self.assertTrue(all(count == 1 for count in Counter(expected).values()))

    def test_groups_follow_source_ownership_and_are_not_empty(self) -> None:
        groups = re.findall(r"^##@ (.+)$", self.source, re.MULTILINE)
        self.assertGreater(len(groups), 1)
        self.assertEqual(len(groups), len(set(groups)))
        previous = -1
        for group in groups:
            position = self.help.find(f"\n{group}\n")
            self.assertGreater(position, previous, group)
            previous = position
        # Walk source annotations to verify every command lands in its own group.
        current = None
        for line in self.source.splitlines():
            if line.startswith("##@ "):
                current = line[4:]
            elif match := TARGET.match(line):
                self.assertIsNotNone(current, match[1])
                block = self.help.split(f"\n{current}\n", 1)[1]
                next_group = groups.index(current) + 1
                if next_group < len(groups):
                    block = block.split(f"\n{groups[next_group]}\n", 1)[0]
                self.assertIn(match[1], help_commands(block))
        for index, group in enumerate(groups):
            block = self.help.split(f"\n{group}\n", 1)[1]
            if index + 1 < len(groups):
                block = block.split(f"\n{groups[index + 1]}\n", 1)[0]
            self.assertTrue(help_commands(block), group)

    def test_default_invocation_is_help_even_with_module_override(self) -> None:
        self.assertEqual(self.help, run_make())
        self.assertEqual(self.help, run_make("MODULE=:feature:beerslist"))

    def test_all_documented_actions_are_phony(self) -> None:
        phony = set(" ".join(re.findall(r"^\.PHONY:\s*(.*)$", self.source, re.MULTILINE)).split())
        missing = {name for name, _ in TARGET.findall(self.source)} - phony
        self.assertEqual(set(), missing)

    def test_action_files_do_not_skip_metadata_recipes(self) -> None:
        with tempfile.TemporaryDirectory(prefix="make-help-actions-") as temporary:
            directory = Path(temporary)
            shutil.copy2(ROOT / "Makefile", directory / "Makefile")
            for target, task in (
                ("dependency-guard-baseline-unverified", ":app:dependencyGuardBaseline"),
                ("verification-metadata-format", "spotlessApply"),
            ):
                with self.subTest(target=target):
                    (directory / target).touch()
                    self.assertIn(task, run_make("-n", target, directory=directory))

    def test_multiple_makefiles_have_no_filename_prefix_and_include_digit_targets(self) -> None:
        with tempfile.TemporaryDirectory(prefix="make-help-files-") as temporary:
            directory = Path(temporary)
            shutil.copy2(ROOT / "Makefile", directory / "Makefile")
            (directory / "extra.mk").write_text(
                "##@ Help fixture\n.PHONY: sample-2\nsample-2: ## Fixture command.\n\t@false\n"
            )
            output = run_make("-f", "Makefile", "-f", "extra.mk", "help", directory=directory)
            self.assertNotIn("Makefile:", output)
            self.assertNotIn("extra.mk:", output)
            self.assertIn("\nHelp fixture\n", output)
            self.assertEqual(Counter(help_commands(self.help) + ["sample-2"]), Counter(help_commands(output)))
            self.assertEqual(output, run_make("-f", "Makefile", "-f", "extra.mk", directory=directory))

    def test_descriptions_match_current_wrapper_scope(self) -> None:
        descriptions = dict(TARGET.findall(self.source))
        self.assertIn("beerdetail and beerbrowse", descriptions["install"])
        self.assertIn("both dynamic features", descriptions["bundle-release"])
        self.assertIn("Gradle check lifecycle", descriptions["check"])
        self.assertIn("registered tier", descriptions["test"])


if __name__ == "__main__":
    unittest.main()
