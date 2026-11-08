#!/usr/bin/env python3
"""
test_flock_serialization.py

Unit and integration tests for parallel_sprint_lock.py / locked_build_merge.py (REQ-PRO-041.2, TST-PRO-041.2).
Validates exclusive POSIX flock serialization, timeout handling, exit code propagation,
and automatic cleanup on normal and exceptional terminations.
"""

import os
import subprocess
import sys
import tempfile
import time
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from parallel_sprint_lock import is_lock_busy, run_locked, sprint_lock


class TestFlockSerialization(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.lock_file = os.path.join(self.temp_dir.name, "test_sprint.lock")

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_run_locked_success(self):
        """Verifies successful command execution under lock."""
        ret = run_locked([sys.executable, "-c", "import sys; sys.exit(0)"], lock_path=self.lock_file)
        self.assertEqual(ret, 0)
        self.assertFalse(is_lock_busy(self.lock_file))

    def test_run_locked_exit_code_propagation(self):
        """Verifies that non-zero exit codes are cleanly propagated."""
        ret = run_locked([sys.executable, "-c", "import sys; sys.exit(42)"], lock_path=self.lock_file)
        self.assertEqual(ret, 42)
        self.assertFalse(is_lock_busy(self.lock_file))

    def test_context_manager_lifecycle(self):
        """Verifies that lock is reported busy while held and free after release."""
        self.assertFalse(is_lock_busy(self.lock_file))
        with sprint_lock(lock_path=self.lock_file):
            self.assertTrue(is_lock_busy(self.lock_file))
        self.assertFalse(is_lock_busy(self.lock_file))

    def test_exception_releases_lock(self):
        """Verifies that an unhandled exception inside the context block releases the lock."""
        with self.assertRaises(ValueError):
            with sprint_lock(lock_path=self.lock_file):
                raise ValueError("Forced error inside locked block")
        self.assertFalse(is_lock_busy(self.lock_file))

    def test_timeout_on_contention(self):
        """Verifies that TimeoutError is raised when another process holds the lock beyond timeout."""
        with sprint_lock(lock_path=self.lock_file):
            with self.assertRaises(TimeoutError):
                run_locked([sys.executable, "-c", "print('should not run')"], lock_path=self.lock_file, timeout_sec=0.4)

    def test_concurrent_serialization(self):
        """
        Verifies that two competing processes execute sequentially and serialize cleanly.
        Process 1 writes timestamp A1 and sleeps 0.6s, then writes A2.
        Process 2 writes timestamp B1 and sleeps 0.2s, then writes B2.
        Timestamp sequence must show non-overlapping execution (B1 >= A2).
        """
        trace_file = os.path.join(self.temp_dir.name, "trace.log")
        script = (
            "import sys, time\n"
            "tag, sleep_dur, trace_path = sys.argv[1], float(sys.argv[2]), sys.argv[3]\n"
            "with open(trace_path, 'a') as f:\n"
            "    f.write(f'{tag}_START:{time.time()}\\n')\n"
            "time.sleep(sleep_dur)\n"
            "with open(trace_path, 'a') as f:\n"
            "    f.write(f'{tag}_END:{time.time()}\\n')\n"
        )

        p1_cmd = [
            sys.executable,
            os.path.join(os.path.dirname(__file__), "parallel_sprint_lock.py"),
            "--lock-file",
            self.lock_file,
            "--",
            sys.executable,
            "-c",
            script,
            "PROC1",
            "0.6",
            trace_file,
        ]
        p2_cmd = [
            sys.executable,
            os.path.join(os.path.dirname(__file__), "parallel_sprint_lock.py"),
            "--lock-file",
            self.lock_file,
            "--",
            sys.executable,
            "-c",
            script,
            "PROC2",
            "0.2",
            trace_file,
        ]

        # Launch p1, wait briefly, launch p2
        proc1 = subprocess.Popen(p1_cmd)
        time.sleep(0.15)
        proc2 = subprocess.Popen(p2_cmd)

        proc1.wait(timeout=10)
        proc2.wait(timeout=10)

        self.assertEqual(proc1.returncode, 0)
        self.assertEqual(proc2.returncode, 0)

        with open(trace_file, "r") as f:
            lines = [line.strip().split(":") for line in f.readlines()]

        events = {k: float(v) for k, v in lines}
        self.assertIn("PROC1_START", events)
        self.assertIn("PROC1_END", events)
        self.assertIn("PROC2_START", events)
        self.assertIn("PROC2_END", events)

        # PROC2 must have started after PROC1 ended
        self.assertGreaterEqual(events["PROC2_START"], events["PROC1_END"])

    def test_cli_check_status(self):
        """Verifies CLI --check-status return codes."""
        cli_path = os.path.join(os.path.dirname(__file__), "parallel_sprint_lock.py")

        # Unlocked: exit 0
        res = subprocess.run([sys.executable, cli_path, "--lock-file", self.lock_file, "--check-status"])
        self.assertEqual(res.returncode, 0)

        # Locked: exit 1
        with sprint_lock(lock_path=self.lock_file):
            res = subprocess.run([sys.executable, cli_path, "--lock-file", self.lock_file, "--check-status"])
            self.assertEqual(res.returncode, 1)


if __name__ == "__main__":
    unittest.main()
