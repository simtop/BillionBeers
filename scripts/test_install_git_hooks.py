from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path


INSTALLER = Path(__file__).with_name("install-git-hooks.sh")


def git(cwd: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(cwd), *args],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


class InstallGitHooksTest(unittest.TestCase):
    def test_installs_pre_commit_hook_from_a_linked_worktree(self) -> None:
        with tempfile.TemporaryDirectory(prefix="git hooks worktree ") as temporary:
            root = Path(temporary)
            repository = root / "repository"
            repository.mkdir()
            (repository / "config/git-hooks").mkdir(parents=True)
            (repository / "scripts").mkdir()
            hook_source = repository / "config/git-hooks/pre-commit"
            hook_source.write_text("#!/bin/sh\necho installed\n", encoding="utf-8")
            installer = repository / "scripts/install-git-hooks.sh"
            installer.write_bytes(INSTALLER.read_bytes())

            git(repository, "init", "-q")
            git(repository, "config", "user.name", "Hook Test")
            git(repository, "config", "user.email", "hook-test@example.invalid")
            git(repository, "add", "config/git-hooks/pre-commit", "scripts/install-git-hooks.sh")
            git(repository, "commit", "-q", "-m", "add test hook")

            worktree = root / "linked-worktree"
            git(repository, "worktree", "add", "-q", "-b", "linked", str(worktree))
            self.assertTrue((worktree / ".git").is_file())

            subprocess.run(["bash", str(worktree / "scripts/install-git-hooks.sh")], cwd=worktree, check=True)

            hooks_dir = Path(git(worktree, "rev-parse", "--git-path", "hooks")).resolve()
            installed_hook = hooks_dir / "pre-commit"
            self.assertEqual(hook_source.read_text(encoding="utf-8"), installed_hook.read_text(encoding="utf-8"))
            self.assertTrue(installed_hook.stat().st_mode & 0o111)


if __name__ == "__main__":
    unittest.main()
