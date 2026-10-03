#!/usr/bin/env python3
"""Supervise one preparation lane and capture bounded resource evidence."""

from __future__ import annotations

import datetime as dt
import os
import shutil
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path

SAMPLE_INTERVAL_SECONDS = 30
SAMPLE_COMMAND_TIMEOUT_SECONDS = 1.5
SAMPLE_JOIN_TIMEOUT_SECONDS = 2
SHUTDOWN_GRACE_SECONDS = 2
LANES = {"linux", "web", "managed", "apple"}
MODES = {"reference", "candidate"}


class Supervisor:
    def __init__(self, lane: str, mode: str):
        self.lane = lane
        self.mode = mode
        self.metrics = Path(os.environ["RUNNER_TEMP"]) / "dependency-preparation-resources.log"
        self.metrics_lock = threading.Lock()
        self.probe_lock = threading.Lock()
        self.stop_sampler = threading.Event()
        self.signal_number: int | None = None
        self.signal_time: str | None = None
        self.child: subprocess.Popen | None = None
        self.sampler: threading.Thread | None = None
        self.active_probe_pid: int | None = None
        self.signal_reported = False

    def write_metrics(self, line: str) -> None:
        with self.metrics_lock:
            with self.metrics.open("a", encoding="utf-8") as stream:
                stream.write(line.rstrip() + "\n")

    def run_probe(self, args: list[str], background: bool = False) -> str:
        probe = None
        try:
            with self.probe_lock:
                if self.signal_number is not None or (background and self.stop_sampler.is_set()):
                    return ""
                probe = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                         text=True, start_new_session=True)
                self.active_probe_pid = probe.pid
            stdout, _ = probe.communicate(timeout=SAMPLE_COMMAND_TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            if probe is not None:
                self.kill_probe_group(probe.pid)
                try:
                    probe.communicate(timeout=0.5)
                except subprocess.TimeoutExpired:
                    pass
            return ""
        except OSError:
            return ""
        finally:
            if probe is not None:
                with self.probe_lock:
                    if self.active_probe_pid == probe.pid:
                        self.active_probe_pid = None
        return stdout if probe is not None and probe.returncode == 0 else ""

    @staticmethod
    def kill_probe_group(pid: int) -> None:
        try:
            os.killpg(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass

    def sample(self, background: bool = False) -> None:
        if self.signal_number is not None or (background and self.stop_sampler.is_set()):
            return
        should_stop = lambda: self.signal_number is not None or (background and self.stop_sampler.is_set())
        lines = [dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")]
        if shutil.which("free"):
            lines.append(self.run_probe(["free", "-m"], background))
        elif shutil.which("vm_stat"):
            lines.append(self.run_probe(["vm_stat"], background))
            if should_stop():
                return
            lines.append(self.run_probe(["sysctl", "hw.memsize"], background))
        if should_stop():
            return
        lines.append(self.run_probe(["df", "-h", "."], background))
        if should_stop():
            return
        if sys.platform == "darwin":
            process_list = self.run_probe(["ps", "-axo", "pid,rss,comm", "-m"], background)
        else:
            process_list = self.run_probe(["ps", "-eo", "pid,rss,comm", "--sort=-rss"], background)
        if should_stop():
            return
        if process_list:
            process_lines = process_list.splitlines()
            lines.append("\n".join(process_lines[:13]))  # Header and the twelve largest processes.
        content = "\n".join(line.rstrip() for line in lines if line).strip()
        if content:
            self.write_metrics(content)

    def sampler_loop(self) -> None:
        while not self.stop_sampler.wait(SAMPLE_INTERVAL_SECONDS):
            self.sample(background=True)

    def handle_signal(self, signum: int, _frame: object) -> None:
        if self.signal_number is not None:
            return
        self.signal_number = signum
        self.signal_time = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        if self.child is not None:
            self.signal_group(signum)
        if self.active_probe_pid is not None:
            self.kill_probe_group(self.active_probe_pid)

    def report_signal(self) -> None:
        if self.signal_number is None or self.signal_reported:
            return
        self.signal_reported = True
        label = signal.Signals(self.signal_number).name
        message = (f"Preparation supervisor captured {label} at "
                   f"{self.signal_time or dt.datetime.now(dt.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}; "
                   "cleaning up owned process groups.")
        print(message, flush=True)
        try:
            self.write_metrics(message)
        except OSError:
            pass
        self.stop_sampler.set()

    def signal_group(self, signum: int) -> None:
        if self.child is None:
            return
        try:
            os.killpg(self.child.pid, signum)
        except ProcessLookupError:
            pass

    def stop_child_group(self) -> int:
        assert self.child is not None
        child = self.child
        if self.signal_number is None:
            status = child.wait()
            return 128 + abs(status) if status < 0 else status
        self.report_signal()
        try:
            child.wait(timeout=SHUTDOWN_GRACE_SECONDS)
        except subprocess.TimeoutExpired:
            self.signal_group(signal.SIGKILL)
            try:
                child.wait(timeout=SHUTDOWN_GRACE_SECONDS)
            except subprocess.TimeoutExpired:
                print("Preparation lane did not exit after bounded SIGKILL escalation.", file=sys.stderr, flush=True)
        else:
            # The direct child can exit while a descendant ignores TERM. Reap the
            # rest of the isolated process group before this supervisor exits.
            try:
                os.killpg(child.pid, 0)
            except ProcessLookupError:
                pass
            else:
                self.signal_group(signal.SIGKILL)
        return 128 + self.signal_number

    def stop_sampler_thread(self) -> None:
        self.stop_sampler.set()
        with self.probe_lock:
            if self.active_probe_pid is not None:
                self.kill_probe_group(self.active_probe_pid)
        if self.sampler is not None:
            self.sampler.join(timeout=SAMPLE_JOIN_TIMEOUT_SECONDS)

    def run(self) -> int:
        self.metrics.parent.mkdir(parents=True, exist_ok=True)
        signal.signal(signal.SIGTERM, self.handle_signal)
        signal.signal(signal.SIGINT, self.handle_signal)
        source_ref = os.environ.get("PREPARATION_SOURCE_REF", "unknown")
        head = os.environ.get("PREPARATION_HEAD", "unknown")
        preparation_mode = os.environ.get("PREPARATION_MODE", self.mode)
        self.write_metrics(
            f"Evidence identity: lane={self.lane} mode={preparation_mode} graph={self.mode} "
            f"run={os.environ.get('GITHUB_RUN_ID', 'local')} "
            f"attempt={os.environ.get('GITHUB_RUN_ATTEMPT', 'local')} "
            f"head={head} source={source_ref}"
        )
        self.sample()
        if self.signal_number is not None:
            self.report_signal()
            self.write_metrics(f"Preparation lane: {self.lane}; mode: {self.mode}; elapsed seconds: 0; exit status: {128 + self.signal_number}")
            return 128 + self.signal_number
        self.sampler = threading.Thread(target=self.sampler_loop, name="preparation-resource-sampler", daemon=True)
        self.sampler.start()

        started = time.monotonic()
        try:
            if self.signal_number is not None:
                self.report_signal()
                status = 128 + self.signal_number
            else:
                self.child = subprocess.Popen(
                    ["make", "verification-metadata-lane", f"LANE={self.lane}", f"MODE={self.mode}"],
                    start_new_session=True,
                )
                if self.signal_number is not None:
                    self.signal_group(self.signal_number)
                while self.child.poll() is None and self.signal_number is None:
                    time.sleep(0.05)
                status = self.stop_child_group()
        except OSError as error:
            print(f"Unable to start preparation lane: {error}", file=sys.stderr, flush=True)
            status = 127
        finally:
            self.stop_sampler_thread()

        elapsed = int(time.monotonic() - started)
        if self.signal_number is None:
            self.sample()
        if self.signal_number is not None:
            self.report_signal()
            status = 128 + self.signal_number
        self.write_metrics(f"Preparation lane: {self.lane}; mode: {self.mode}; elapsed seconds: {elapsed}; exit status: {status}")
        return status


def main() -> int:
    if len(sys.argv) != 3 or sys.argv[1] not in LANES or sys.argv[2] not in MODES:
        return 1
    if not os.environ.get("RUNNER_TEMP"):
        print("RUNNER_TEMP is required to capture preparation resources.", file=sys.stderr)
        return 1
    return Supervisor(sys.argv[1], sys.argv[2]).run()


if __name__ == "__main__":
    raise SystemExit(main())
