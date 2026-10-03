#!/usr/bin/env python3

from __future__ import annotations

import os
import signal
import subprocess
import sys
import tempfile
import time
import unittest
import importlib.util
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
WRAPPER = ROOT / ".github/scripts/run-dependabot-preparation.sh"
STEP_WRAPPER = ROOT / ".github/scripts/run-dependabot-preparation-step.sh"
SUPERVISOR_PATH = ROOT / ".github/scripts/run-dependabot-preparation.py"
SUPERVISOR_SPEC = importlib.util.spec_from_file_location("run_dependabot_preparation", SUPERVISOR_PATH)
SUPERVISOR = importlib.util.module_from_spec(SUPERVISOR_SPEC)
SUPERVISOR_SPEC.loader.exec_module(SUPERVISOR)


class PreparationSupervisorTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.fake_bin = self.root / "bin"
        self.fake_bin.mkdir()
        self.pid_file = self.root / "processes.txt"
        self.addCleanup(self.cleanup_owned_processes)
        fake_make = self.fake_bin / "make"
        fake_make.write_text(
            "#!/usr/bin/env python3\n"
            "import os, signal, subprocess, sys, time\n"
            "mode = os.environ.get('FAKE_MAKE_MODE', 'exit')\n"
            "if mode == 'build_failure_then_shutdown':\n"
            "    print('> Task :compile FAILED', flush=True)\n"
            "    print('##[error]The runner has received a shutdown signal.', flush=True)\n"
            "    raise SystemExit(143)\n"
            "if mode == 'delay_exit':\n"
            "    time.sleep(0.3)\n"
            "    raise SystemExit(0)\n"
            "if mode == 'exit':\n"
            "    print('completed-output', flush=True)\n"
            "    open(os.environ['FAKE_MAKE_FINISHED_FILE'], 'w').write('done')\n"
            "    raise SystemExit(int(os.environ.get('FAKE_MAKE_EXIT', '0')))\n"
            "signal.signal(signal.SIGTERM, signal.SIG_IGN)\n"
            "child_code = \"import os,signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); open(os.environ['FAKE_CHILD_PID_FILE'],'w').write(str(os.getpid())); time.sleep(120)\"\n"
            "child = subprocess.Popen([sys.executable, '-c', child_code])\n"
            "open(os.environ['FAKE_PARENT_PID_FILE'], 'w').write(str(os.getpid()))\n"
            "if mode == 'graceful_parent': signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))\n"
            "while True: time.sleep(1)\n",
            encoding="utf-8",
        )
        fake_make.chmod(0o755)
        self.environment = os.environ.copy()
        self.environment.update({
            "PATH": f"{self.fake_bin}{os.pathsep}{os.environ.get('PATH', '')}",
            "RUNNER_TEMP": str(self.root),
            "PREPARATION_MODE": "write",
            "PREPARATION_HEAD": "a" * 40,
            "PREPARATION_SOURCE_REF": "dependabot/gradle/sample",
            "GITHUB_RUN_ID": "123",
            "GITHUB_RUN_ATTEMPT": "1",
            "FAKE_MAKE_MODE": "exit",
            "FAKE_MAKE_EXIT": "0",
            "GITHUB_OUTPUT": str(self.root / "step-output.txt"),
            "FAKE_PARENT_PID_FILE": str(self.root / "parent.pid"),
            "FAKE_CHILD_PID_FILE": str(self.root / "child.pid"),
            "FAKE_MAKE_FINISHED_FILE": str(self.root / "make-finished.txt"),
        })

    def run_wrapper(self, *args):
        log = self.root / f"{args[0]}-{args[1]}.log"
        return subprocess.run(["bash", str(STEP_WRAPPER), *args, str(log)], env=self.environment,
                              cwd=ROOT, capture_output=True, text=True, timeout=10)

    def test_success_and_lane_failure_status_are_preserved(self):
        success = self.run_wrapper("linux", "reference")
        self.assertEqual(0, success.returncode, success.stderr)
        self.assertIn("completed-output", (self.root / "linux-reference.log").read_text())
        metrics = (self.root / "dependency-preparation-resources.log").read_text()
        self.assertIn("lane=linux mode=write graph=reference run=123 attempt=1", metrics)
        self.assertIn("exit status: 0", metrics)

        self.environment["FAKE_MAKE_EXIT"] = "23"
        failure = self.run_wrapper("web", "candidate")
        self.assertEqual(23, failure.returncode, failure.stderr)
        metrics = (self.root / "dependency-preparation-resources.log").read_text()
        self.assertIn("lane: web; mode: candidate", metrics)
        self.assertIn("exit status: 23", metrics)
        step_output = (self.root / "step-output.txt").read_text()
        self.assertIn("classification=success", step_output)
        self.assertIn("classification=build_test_or_policy_failure", step_output)

    def test_bare_signal_statuses_are_diagnostic_only_and_tee_is_drained(self):
        for status in (143, 130):
            self.environment["FAKE_MAKE_EXIT"] = str(status)
            result = self.run_wrapper("web", "reference")
            self.assertEqual(status, result.returncode, result.stderr)
            self.assertIn("completed-output", (self.root / "web-reference.log").read_text())
        self.assertIn("classification=process_termination",
                      (self.root / "step-output.txt").read_text())
        self.environment["FAKE_MAKE_MODE"] = "build_failure_then_shutdown"
        result = self.run_wrapper("web", "candidate")
        self.assertEqual(143, result.returncode)
        self.assertIn("classification=build_test_or_policy_failure",
                      (self.root / "step-output.txt").read_text())

    def test_normal_completion_stops_inflight_background_probe_before_final_sample(self):
        free_calls = self.root / "free-calls"
        probe_pid = self.root / "background-probe.pid"
        probe_child_pid = self.root / "background-probe-child.pid"
        fake_free = self.fake_bin / "free"
        fake_free.write_text(
            "#!/usr/bin/env python3\n"
            "import os, pathlib, subprocess, sys\n"
            f"counter = pathlib.Path({str(free_calls)!r})\n"
            "count = int(counter.read_text()) + 1 if counter.exists() else 1\n"
            "counter.write_text(str(count))\n"
            "if count == 2:\n"
            f"    child = subprocess.Popen([sys.executable, '-c', \"import os,time; open({str(probe_child_pid)!r},'w').write(str(os.getpid())); time.sleep(120)\"])\n"
            f"    pathlib.Path({str(probe_pid)!r}).write_text(str(os.getpid()))\n"
            "    child.wait()\n"
            "else:\n"
            "    print('Mem: test')\n",
            encoding="utf-8",
        )
        fake_free.chmod(0o755)
        self.environment["FAKE_MAKE_MODE"] = "delay_exit"
        self.environment["RUNNER_TEMP"] = str(self.root)
        old_handlers = {sig: signal.getsignal(sig) for sig in (signal.SIGTERM, signal.SIGINT)}
        try:
            with patch.dict(os.environ, self.environment), patch.object(SUPERVISOR, "SAMPLE_INTERVAL_SECONDS", 0.02):
                self.assertEqual(0, SUPERVISOR.Supervisor("linux", "reference").run())
        finally:
            for sig, handler in old_handlers.items():
                signal.signal(sig, handler)
            self.cleanup_owned_processes()
        self.assertTrue(probe_pid.exists())
        self.assertTrue(probe_child_pid.exists())
        self.assert_process_stopped(int(probe_pid.read_text()))
        self.assert_process_stopped(int(probe_child_pid.read_text()))

    def test_signal_during_final_probe_overrides_successful_lane_status(self):
        free_calls = self.root / "final-free-calls"
        probe_pid = self.root / "final-probe.pid"
        probe_child_pid = self.root / "final-probe-child.pid"
        fake_free = self.fake_bin / "free"
        fake_free.write_text(
            "#!/usr/bin/env python3\n"
            "import os, pathlib, subprocess, sys\n"
            f"counter = pathlib.Path({str(free_calls)!r})\n"
            "count = int(counter.read_text()) + 1 if counter.exists() else 1\n"
            "counter.write_text(str(count))\n"
            "if count == 2:\n"
            f"    child = subprocess.Popen([sys.executable, '-c', \"import os,time; open({str(probe_child_pid)!r},'w').write(str(os.getpid())); time.sleep(120)\"])\n"
            f"    pathlib.Path({str(probe_pid)!r}).write_text(str(os.getpid()))\n"
            "    child.wait()\n"
            "else:\n"
            "    print('Mem: test')\n",
            encoding="utf-8",
        )
        fake_free.chmod(0o755)
        log = self.root / "final-sample.log"
        wrapper = subprocess.Popen(["bash", str(STEP_WRAPPER), "linux", "reference", str(log)],
                                   env=self.environment, cwd=ROOT,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            self.wait_for_file(self.root / "make-finished.txt")
            self.wait_for_file(probe_pid)
            self.wait_for_file(probe_child_pid)
            wrapper.send_signal(signal.SIGTERM)
            self.assertEqual(143, wrapper.wait(timeout=5))
            self.assert_process_stopped(int(probe_pid.read_text()))
            self.assert_process_stopped(int(probe_child_pid.read_text()))
            metrics = (self.root / "dependency-preparation-resources.log").read_text()
            self.assertIn("captured SIGTERM", metrics)
            self.assertIn("exit status: 143", metrics)
            self.assertIn("classification=process_termination", (self.root / "step-output.txt").read_text())
        finally:
            if wrapper.poll() is None:
                wrapper.kill()
                wrapper.wait(timeout=2)
            self.cleanup_owned_processes()

    def test_graceful_parent_exit_still_kills_stubborn_descendant(self):
        self.environment["FAKE_MAKE_MODE"] = "graceful_parent"
        wrapper = subprocess.Popen(["bash", str(STEP_WRAPPER), "managed", "reference",
                                    str(self.root / "graceful.log")], env=self.environment, cwd=ROOT,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            self.wait_for_file(self.root / "parent.pid")
            self.wait_for_file(self.root / "child.pid")
            parent_pid = int((self.root / "parent.pid").read_text())
            child_pid = int((self.root / "child.pid").read_text())
            wrapper.send_signal(signal.SIGTERM)
            self.assertEqual(143, wrapper.wait(timeout=5))
            self.assert_process_stopped(parent_pid)
            self.assert_process_stopped(child_pid)
        finally:
            if wrapper.poll() is None:
                wrapper.kill()
                wrapper.wait(timeout=2)
            self.cleanup_owned_processes()

    def test_term_forwards_to_stubborn_child_group_and_escalates_promptly(self):
        self.environment["FAKE_MAKE_MODE"] = "stubborn"
        started = time.monotonic()
        log = self.root / "signal.log"
        wrapper = subprocess.Popen(["bash", str(STEP_WRAPPER), "managed", "reference", str(log)],
                                   env=self.environment, cwd=ROOT,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            self.wait_for_file(self.root / "parent.pid")
            self.wait_for_file(self.root / "child.pid")
            parent_pid = int((self.root / "parent.pid").read_text())
            child_pid = int((self.root / "child.pid").read_text())
            wrapper.send_signal(signal.SIGTERM)
            time.sleep(0.05)
            wrapper.send_signal(signal.SIGINT)
            self.assertEqual(143, wrapper.wait(timeout=6))
            self.assertLess(time.monotonic() - started, 5)
            self.assert_process_stopped(parent_pid)
            self.assert_process_stopped(child_pid)
            metrics = (self.root / "dependency-preparation-resources.log").read_text()
            self.assertIn("captured SIGTERM", metrics)
            self.assertIn("exit status: 143", metrics)
            self.assertIn("classification=process_termination", (self.root / "step-output.txt").read_text())
        finally:
            if wrapper.poll() is None:
                wrapper.kill()
                wrapper.wait(timeout=2)
            self.cleanup_owned_processes()

    def test_int_is_captured_but_keeps_interrupt_exit_status(self):
        self.environment["FAKE_MAKE_MODE"] = "stubborn"
        log = self.root / "interrupt.log"
        wrapper = subprocess.Popen(["bash", str(STEP_WRAPPER), "apple", "candidate", str(log)],
                                   env=self.environment, cwd=ROOT,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            self.wait_for_file(self.root / "parent.pid")
            self.wait_for_file(self.root / "child.pid")
            parent_pid = int((self.root / "parent.pid").read_text())
            child_pid = int((self.root / "child.pid").read_text())
            wrapper.send_signal(signal.SIGINT)
            self.assertEqual(130, wrapper.wait(timeout=6))
            self.assert_process_stopped(parent_pid)
            self.assert_process_stopped(child_pid)
            metrics = (self.root / "dependency-preparation-resources.log").read_text()
            self.assertIn("captured SIGINT", metrics)
            self.assertIn("exit status: 130", metrics)
            self.assertIn("classification=process_termination", (self.root / "step-output.txt").read_text())
        finally:
            if wrapper.poll() is None:
                wrapper.kill()
                wrapper.wait(timeout=2)
            self.cleanup_owned_processes()

    def test_invalid_lane_and_mode_fail_before_launch(self):
        self.assertEqual(1, self.run_wrapper("other", "reference").returncode)
        self.assertEqual(1, self.run_wrapper("linux", "write").returncode)

    def test_term_during_sampler_probe_stops_probe_group_without_starting_make(self):
        probe_pid = self.root / "probe.pid"
        child_pid = self.root / "probe-child.pid"
        fake_free = self.fake_bin / "free"
        fake_free.write_text(
            "#!/usr/bin/env python3\n"
            "import os, subprocess, sys, time\n"
            "child = subprocess.Popen([sys.executable, '-c', \"import os,time; open(os.environ['FAKE_PROBE_CHILD_PID_FILE'],'w').write(str(os.getpid())); time.sleep(120)\"])\n"
            "open(os.environ['FAKE_PROBE_PID_FILE'], 'w').write(str(os.getpid()))\n"
            "child.wait()\n",
            encoding="utf-8",
        )
        fake_free.chmod(0o755)
        self.environment["FAKE_PROBE_PID_FILE"] = str(probe_pid)
        self.environment["FAKE_PROBE_CHILD_PID_FILE"] = str(child_pid)
        wrapper = subprocess.Popen(["bash", str(STEP_WRAPPER), "linux", "reference",
                                    str(self.root / "sampling.log")],
                                   env=self.environment, cwd=ROOT,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            self.wait_for_file(probe_pid)
            self.wait_for_file(child_pid)
            pids = [int(probe_pid.read_text()), int(child_pid.read_text())]
            wrapper.send_signal(signal.SIGTERM)
            self.assertEqual(143, wrapper.wait(timeout=5))
            for pid in pids:
                self.assert_process_stopped(pid)
            self.assertFalse((self.root / "parent.pid").exists())
            self.assertIn("captured SIGTERM", (self.root / "dependency-preparation-resources.log").read_text())
        finally:
            if wrapper.poll() is None:
                wrapper.kill()
                wrapper.wait(timeout=2)
            self.cleanup_owned_processes()

    def wait_for_file(self, path: Path):
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            if path.exists():
                return
            time.sleep(0.02)
        self.fail(f"timed out waiting for {path.name}")

    def assert_process_stopped(self, pid: int):
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            try:
                os.kill(pid, 0)
            except ProcessLookupError:
                return
            proc_stat = Path(f"/proc/{pid}/stat")
            if proc_stat.is_file() and proc_stat.read_text().split()[2] == "Z":
                return
            time.sleep(0.02)
        self.fail(f"process {pid} was not reaped or stopped")

    def cleanup_owned_processes(self):
        """Stop test-owned lane/probe groups even when an assertion fails."""
        for name in ("parent.pid", "probe.pid", "background-probe.pid", "final-probe.pid"):
            path = self.root / name
            if not path.exists():
                continue
            try:
                pid = int(path.read_text())
                os.killpg(pid, signal.SIGKILL)
            except (ProcessLookupError, ValueError):
                pass


if __name__ == "__main__":
    unittest.main()
