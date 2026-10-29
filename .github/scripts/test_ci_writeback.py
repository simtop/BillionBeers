from __future__ import annotations

import importlib.util
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("ci-writeback.py")
SPEC = importlib.util.spec_from_file_location("ci_writeback", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "simtop/BillionBeers"
SHA = "a" * 40


def run(path=".github/workflows/format_fix.yml", **overrides):
    source = {"full_name": REPOSITORY, "id": 123}
    result = {
        "path": path, "event": "workflow_dispatch", "conclusion": "success",
        "repository": dict(source), "head_repository": dict(source),
        "head_branch": "feature/format", "head_sha": SHA,
        "run_attempt": 2, "id": 987,
    }
    result.update(overrides)
    return result


class CiWritebackTest(unittest.TestCase):
    def test_resolves_only_successful_same_repository_dispatches(self):
        self.assertEqual(
            {"kind": "format", "branch": "feature/format", "sha": SHA, "run_id": 987, "attempt": 2},
            MODULE.validate_event(run(), REPOSITORY, "master"),
        )
        self.assertEqual("detekt", MODULE.validate_event(
            run(path=".github/workflows/detekt_baseline.yml"), REPOSITORY, "master"
        )["kind"])
        self.assertEqual("screenshots", MODULE.validate_event(
            run(path=".github/workflows/record_screenshots.yml"), REPOSITORY, "master"
        )["kind"])

    def test_rejects_wrong_event_workflow_repository_or_default_branch(self):
        invalid = [
            run(path=".github/workflows/ci.yml"),
            run(event="push"),
            run(conclusion="failure"),
            run(repository={"full_name": "attacker/fork", "id": 999}),
            run(head_repository={"full_name": "attacker/fork", "id": 999}),
            run(head_branch="master"),
            run(head_branch="../outside"),
            run(head_sha="not-a-sha"),
            run(id=True),
            run(run_attempt=0),
        ]
        for candidate in invalid:
            with self.subTest(candidate=candidate):
                with self.assertRaises(MODULE.WritebackError):
                    MODULE.validate_event(candidate, REPOSITORY, "master")

    def test_requires_one_bounded_artifact_from_the_current_attempt(self):
        # Match the documented artifact API shape: workflow_run has an id, not run_attempt.
        expected = {"name": "generated-output-2", "expired": False, "size_in_bytes": 42,
                    "workflow_run": {"id": 987}}
        self.assertEqual("generated-output-2", MODULE.validate_artifact([expected], 987, 2))
        invalid = [
            [],
            [expected, dict(expected)],
            [dict(expected, expired=True)],
            [dict(expected, size_in_bytes=MODULE.MAX_PATCH_BYTES + 1)],
            [dict(expected, workflow_run={"id": 986})],
            [dict(expected, name="generated-output-1")],
        ]
        for artifacts in invalid:
            with self.subTest(artifacts=artifacts):
                with self.assertRaises(MODULE.WritebackError):
                    MODULE.validate_artifact(artifacts, 987, 2)

    def test_generated_patch_paths_are_limited_to_generator_outputs(self):
        self.assertTrue(MODULE.path_allowed("format", "feature/demo/src/Main.kt"))
        self.assertTrue(MODULE.path_allowed("format", "feature/demo/build.gradle.kts"))
        self.assertFalse(MODULE.path_allowed("format", "gradle/verification-metadata.xml"))
        self.assertTrue(MODULE.path_allowed("detekt", "feature/demo/detekt-baseline.xml"))
        self.assertFalse(MODULE.path_allowed("detekt", "feature/demo/build.gradle.kts"))
        self.assertTrue(MODULE.path_allowed(
            "screenshots", "feature/demo/src/test/snapshots/images/Preview.png"
        ))
        self.assertFalse(MODULE.path_allowed(
            "screenshots", "feature/demo/src/test/snapshots/images/../../secrets.png"
        ))
        self.assertFalse(MODULE.path_allowed(
            "screenshots", "feature/demo/src/test/snapshots/images/Preview.jpg"
        ))

    def test_applies_a_safe_format_patch_and_stages_only_its_source_file(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            subprocess.run(["git", "init", "-q", str(repo)], check=True)
            subprocess.run(["git", "-C", str(repo), "config", "user.name", "Test"], check=True)
            subprocess.run(["git", "-C", str(repo), "config", "user.email", "test@example.invalid"], check=True)
            source = repo / "feature" / "Main.kt"
            source.parent.mkdir()
            source.write_text("fun main() = Unit\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(repo), "add", "--", "feature/Main.kt"], check=True)
            subprocess.run(["git", "-C", str(repo), "commit", "-qm", "base"], check=True)
            source.write_text("fun main() = println(\"formatted\")\n", encoding="utf-8")
            patch = subprocess.run(
                ["git", "-C", str(repo), "diff", "--binary", "--no-renames", "HEAD"],
                check=True, capture_output=True,
            ).stdout
            patch_path = repo / "generated.patch"
            patch_path.write_bytes(patch)
            source.write_text("fun main() = Unit\n", encoding="utf-8")

            paths = MODULE.apply_generated_patch(repo, patch_path, "format")

            self.assertEqual(["feature/Main.kt"], paths)
            self.assertIn("formatted", source.read_text(encoding="utf-8"))
            self.assertEqual("feature/Main.kt", subprocess.run(
                ["git", "-C", str(repo), "diff", "--cached", "--name-only"],
                check=True, capture_output=True, text=True,
            ).stdout.strip())

    def test_rejects_an_out_of_scope_patch_before_applying_it(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            subprocess.run(["git", "init", "-q", str(repo)], check=True)
            subprocess.run(["git", "-C", str(repo), "config", "user.name", "Test"], check=True)
            subprocess.run(["git", "-C", str(repo), "config", "user.email", "test@example.invalid"], check=True)
            source = repo / "Makefile"
            source.write_text("all: test\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(repo), "add", "--", "Makefile"], check=True)
            subprocess.run(["git", "-C", str(repo), "commit", "-qm", "base"], check=True)
            source.write_text("all: dangerous\n", encoding="utf-8")
            patch = subprocess.run(
                ["git", "-C", str(repo), "diff", "--binary", "HEAD"], check=True, capture_output=True
            ).stdout
            patch_path = repo / "generated.patch"
            patch_path.write_bytes(patch)
            source.write_text("all: test\n", encoding="utf-8")

            with self.assertRaisesRegex(MODULE.WritebackError, "outside the format allowlist"):
                MODULE.apply_generated_patch(repo, patch_path, "format")
            self.assertEqual("all: test\n", source.read_text(encoding="utf-8"))

    def test_intent_to_add_captures_new_detekt_baselines(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            subprocess.run(["git", "init", "-q", str(repo)], check=True)
            subprocess.run(["git", "-C", str(repo), "config", "user.name", "Test"], check=True)
            subprocess.run(["git", "-C", str(repo), "config", "user.email", "test@example.invalid"], check=True)
            base = repo / "README.md"
            base.write_text("base\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(repo), "add", "--", "README.md"], check=True)
            subprocess.run(["git", "-C", str(repo), "commit", "-qm", "base"], check=True)
            patch_path = repo / "generated.patch"

            baseline = repo / "feature" / "detekt-baseline.xml"
            baseline.parent.mkdir()
            baseline.write_text("<SmellBaseline>\n</SmellBaseline>\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(repo), "add", "-N", "--", "feature/detekt-baseline.xml"], check=True)
            patch_path.write_bytes(subprocess.run(
                ["git", "-C", str(repo), "diff", "--binary", "--no-renames", "HEAD", "--",
                 "**/detekt-baseline.xml"], check=True, capture_output=True,
            ).stdout)
            self.assertTrue(MODULE.patch_paths(repo, patch_path.read_bytes()))
            subprocess.run(["git", "-C", str(repo), "reset", "-q", "HEAD", "--",
                            "feature/detekt-baseline.xml"], check=True)
            baseline.unlink()
            self.assertEqual(["feature/detekt-baseline.xml"],
                             MODULE.apply_generated_patch(repo, patch_path, "detekt"))
            subprocess.run(["git", "-C", str(repo), "commit", "-qm", "baseline"], check=True)


    def test_screenshot_archive_preserves_raw_pngs_and_stages_lfs_objects(self):
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            producer = temporary / "producer"
            producer.mkdir()
            subprocess.run(["git", "init", "-q", str(producer)], check=True)
            subprocess.run(["git", "-C", str(producer), "config", "user.name", "Test"], check=True)
            subprocess.run(["git", "-C", str(producer), "config", "user.email",
                            "test@example.invalid"], check=True)
            subprocess.run(["git", "-C", str(producer), "lfs", "install", "--local"], check=True)
            (producer / ".gitattributes").write_text(
                "**/src/test/snapshots/images/*.png filter=lfs diff=lfs merge=lfs -text\n",
                encoding="utf-8",
            )
            folder = producer / "feature" / "src" / "test" / "snapshots" / "images"
            folder.mkdir(parents=True)
            old_png = folder / "Old.png"
            deleted_png = folder / "Deleted.png"
            old_png.write_bytes(b"old tracked image")
            deleted_png.write_bytes(b"deleted image")
            subprocess.run(["git", "-C", str(producer), "add", "--", ".gitattributes",
                            "feature/src/test/snapshots/images"], check=True)
            subprocess.run(["git", "-C", str(producer), "commit", "-qm", "base"], check=True)

            old_png.write_bytes(b"updated raw image bytes")
            deleted_png.unlink()
            new_png = folder / "New.png"
            new_png.write_bytes(b"new raw image bytes")
            archive_path = temporary / "screenshots.zip"
            manifest = MODULE.snapshot_screenshots(producer, archive_path)
            self.assertEqual({"A", "M", "D"}, {entry["status"] for entry in manifest})

            old_png.write_bytes(b"old tracked image")
            deleted_png.write_bytes(b"deleted image")
            new_png.unlink()
            paths = MODULE.apply_screenshot_archive(producer, archive_path)

            self.assertCountEqual([
                "feature/src/test/snapshots/images/Old.png",
                "feature/src/test/snapshots/images/Deleted.png",
                "feature/src/test/snapshots/images/New.png",
            ], paths)
            self.assertEqual(b"updated raw image bytes", old_png.read_bytes())
            self.assertFalse(deleted_png.exists())
            self.assertEqual(b"new raw image bytes", new_png.read_bytes())
            staged_pointer = subprocess.run(
                ["git", "-C", str(producer), "show",
                 ":feature/src/test/snapshots/images/New.png"], check=True, capture_output=True,
            ).stdout
            self.assertTrue(staged_pointer.startswith(b"version https://git-lfs.github.com/spec/v1\n"))
            self.assertNotIn(b"new raw image bytes", staged_pointer)
            self.assertTrue(list((producer / ".git" / "lfs" / "objects").rglob("*")))

    def test_rejects_screenshot_archive_with_unlisted_or_unsafe_member(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            archive_path = repo / "screenshots.zip"
            import json
            import zipfile

            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr("manifest.json", json.dumps({
                    "schema": 1,
                    "files": [{"status": "A", "path":
                               "feature/src/test/snapshots/images/../../outside.png",
                               "size": 4, "sha256": "0" * 64}],
                }))
                archive.writestr("files/../../outside.png", b"evil")
            with self.assertRaises(MODULE.WritebackError):
                MODULE.apply_screenshot_archive(repo, archive_path)

    def test_rejects_tracked_screenshot_symlinks_before_writing_any_file(self):
        for destination in ("outside", "inside", "missing"):
            with self.subTest(destination=destination), tempfile.TemporaryDirectory() as directory:
                temporary = Path(directory)
                repo = temporary / "repo"
                repo.mkdir()
                subprocess.run(["git", "init", "-q", str(repo)], check=True)
                folder = repo / "feature/src/test/snapshots/images"
                folder.mkdir(parents=True)
                safe = folder / "Safe.png"
                safe.write_bytes(b"original screenshot")
                sentinel = (repo if destination == "inside" else temporary) / "sentinel"
                if destination != "missing":
                    sentinel.write_bytes(b"original sentinel")
                unsafe = folder / "Unsafe.png"
                unsafe.symlink_to(sentinel)
                subprocess.run(["git", "-C", str(repo), "add", "--", str(folder)], check=True)
                archive_path = temporary / "screenshots.zip"
                entries = []
                with zipfile.ZipFile(archive_path, "w") as archive:
                    for target in (safe, unsafe):
                        path = target.relative_to(repo).as_posix()
                        content = b"replacement"
                        entries.append({"status": "M", "path": path, "size": len(content),
                                        "sha256": hashlib.sha256(content).hexdigest()})
                        archive.writestr(f"files/{path}", content)
                    archive.writestr("manifest.json", json.dumps({"schema": 1, "files": entries}))

                with self.assertRaisesRegex(MODULE.WritebackError, "target is a symlink"):
                    MODULE.apply_screenshot_archive(repo, archive_path)

                self.assertEqual(b"original screenshot", safe.read_bytes())
                self.assertTrue(unsafe.is_symlink())
                if destination == "missing":
                    self.assertFalse(sentinel.exists())
                else:
                    self.assertEqual(b"original sentinel", sentinel.read_bytes())

    def test_rejects_screenshot_destination_with_any_symlink_ancestor(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            real = repo / "real/test/snapshots/images"
            real.mkdir(parents=True)
            (repo / "feature").mkdir()
            (repo / "feature/src").symlink_to(repo / "real", target_is_directory=True)
            with self.assertRaisesRegex(MODULE.WritebackError, "symlink parent"):
                MODULE.validate_screenshot_destination(
                    repo, "feature/src/test/snapshots/images/New.png", "A"
                )

    def test_publishes_format_patch_as_fast_forward_to_exact_source_branch(self):
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            bare = temporary / "origin.git"
            producer = temporary / "producer"
            subprocess.run(["git", "init", "--bare", "-q", str(bare)], check=True)
            subprocess.run(["git", "init", "-q", str(producer)], check=True)
            for key, value in (("user.name", "Test"), ("user.email", "test@example.invalid")):
                subprocess.run(["git", "-C", str(producer), "config", key, value], check=True)
            readme = producer / "README.md"
            readme.write_text("base\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(producer), "add", "--", "README.md"], check=True)
            subprocess.run(["git", "-C", str(producer), "commit", "-qm", "base"], check=True)
            subprocess.run(["git", "-C", str(producer), "branch", "-M", "master"], check=True)
            subprocess.run(["git", "-C", str(producer), "remote", "add", "origin", str(bare)], check=True)
            subprocess.run(["git", "-C", str(producer), "push", "-q", "-u", "origin", "master"], check=True)
            subprocess.run(["git", "-C", str(producer), "switch", "-q", "-c", "feature/format"], check=True)
            source = producer / "feature" / "Main.kt"
            source.parent.mkdir()
            source.write_text("fun main() = Unit\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(producer), "add", "--", "feature/Main.kt"], check=True)
            subprocess.run(["git", "-C", str(producer), "commit", "-qm", "source"], check=True)
            subprocess.run(["git", "-C", str(producer), "push", "-q", "-u", "origin", "feature/format"], check=True)
            source_sha = subprocess.run(
                ["git", "-C", str(producer), "rev-parse", "HEAD"], check=True,
                capture_output=True, text=True,
            ).stdout.strip()
            source.write_text("fun main() = println(\"formatted\")\n", encoding="utf-8")
            patch_path = temporary / "generated.patch"
            patch_path.write_bytes(subprocess.run(
                ["git", "-C", str(producer), "diff", "--binary", "--no-renames", "HEAD"],
                check=True, capture_output=True,
            ).stdout)
            source.write_text("fun main() = Unit\n", encoding="utf-8")

            trusted = temporary / "trusted"
            subprocess.run(["git", "clone", "-q", str(bare), str(trusted)], check=True)
            MODULE.publish(trusted, "format", "feature/format", source_sha, patch_path,
                           "master", REPOSITORY)

            pushed = subprocess.run(
                ["git", "--git-dir", str(bare), "rev-parse", "refs/heads/feature/format"],
                check=True, capture_output=True, text=True,
            ).stdout.strip()
            parent = subprocess.run(
                ["git", "--git-dir", str(bare), "rev-parse", f"{pushed}^"],
                check=True, capture_output=True, text=True,
            ).stdout.strip()
            self.assertNotEqual(source_sha, pushed)
            self.assertEqual(source_sha, parent)
            self.assertIn("formatted", (trusted / "feature" / "Main.kt").read_text(encoding="utf-8"))

    def test_source_workflows_have_no_write_credentials_or_push_step(self):
        paths = (
            ".github/workflows/format_fix.yml",
            ".github/workflows/detekt_baseline.yml",
            ".github/workflows/record_screenshots.yml",
        )
        for path in paths:
            workflow = (ROOT / path).read_text(encoding="utf-8")
            with self.subTest(path=path):
                self.assertNotIn("VERIFICATION_METADATA_DEPLOY_KEY", workflow)
                self.assertNotIn("ssh-key:", workflow)
                self.assertIn("persist-credentials: false", workflow)
                self.assertIn("generated-output-${{ github.run_attempt }}", workflow)
                self.assertIn("actions/upload-artifact", workflow)
                self.assertNotRegex(workflow, r"git\s+push\b")
        screenshots = (ROOT / ".github/workflows/record_screenshots.yml").read_text(encoding="utf-8")
        self.assertIn("snapshot-screenshots", screenshots)
        self.assertIn("screenshots.zip", screenshots)
        self.assertNotIn("generated.patch", screenshots)

    def test_only_trusted_default_branch_writeback_has_deploy_key(self):
        workflow = (ROOT / ".github/workflows/ci-writeback.yml").read_text(encoding="utf-8")
        self.assertIn("workflow_run:", workflow)
        self.assertIn("workflows: [Format Fix, Detekt Baseline, Record Screenshots]", workflow)
        self.assertIn("ref: ${{ github.event.repository.default_branch }}", workflow)
        self.assertEqual(1, workflow.count("secrets.VERIFICATION_METADATA_DEPLOY_KEY"))
        self.assertIn("ci-writeback.py resolve", workflow)
        self.assertIn("ci-writeback.py publish", workflow)
        self.assertIn("--screenshots-archive", workflow)
        self.assertIn("lfs: true", workflow)
        self.assertIn("GITHUB_TOKEN: ${{ github.token }}", workflow)
        self.assertNotIn("gradlew", workflow)


if __name__ == "__main__":
    unittest.main()
