#!/usr/bin/env python3
"""
parallel_sprint_lock.py

Provides POSIX flock-based mutual exclusion (REQ-PRO-041.2, TST-PRO-041.2) for concurrent
sprint operations across disjoint domain Epics.
Serializes resource-heavy Gradle test runs and Stage 5 git squash-merges onto sprint/<SPRINT_NAME>.

Default lock path: /tmp/att_build_merge.lock
Invariants:
  - Strictly Python 3 standard library (no pip packages).
  - Deterministic exit code propagation from wrapped commands.
  - Safe timeout handling and guaranteed lock release on termination or error.
"""

import argparse
import contextlib
import fcntl
import os
import subprocess
import sys
import time

DEFAULT_LOCK_FILE = "/tmp/att_build_merge.lock"
DEFAULT_TIMEOUT_SEC = 600.0
POLL_INTERVAL_SEC = 0.25


@contextlib.contextmanager
def sprint_lock(
    lock_path=DEFAULT_LOCK_FILE,
    timeout_sec=DEFAULT_TIMEOUT_SEC,
    poll_interval=POLL_INTERVAL_SEC,
):
    """
    Context manager acquiring an exclusive POSIX flock on lock_path.
    Polls with non-blocking LOCK_NB until acquired or timeout_sec is exceeded.
    Releases lock and closes fd in finally block.
    """
    lock_dir = os.path.dirname(os.path.abspath(lock_path))
    if lock_dir and not os.path.exists(lock_dir):
        os.makedirs(lock_dir, exist_ok=True)

    fd = os.open(lock_path, os.O_CREAT | os.O_RDWR, 0o666)
    start_time = time.time()
    acquired = False
    printed_wait = False

    try:
        while True:
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                acquired = True
                break
            except (BlockingIOError, IOError, OSError):
                elapsed = time.time() - start_time
                if elapsed >= timeout_sec:
                    raise TimeoutError(
                        f"Timeout ({timeout_sec:.1f}s) exceeded waiting for lock on {lock_path}"
                    )
                if not printed_wait:
                    print(
                        f"[flock] Waiting for lock on {lock_path} (held by concurrent worker)...",
                        file=sys.stderr,
                        flush=True,
                    )
                    printed_wait = True
                time.sleep(poll_interval)

        yield fd
    finally:
        if acquired:
            try:
                fcntl.flock(fd, fcntl.LOCK_UN)
            except OSError:
                pass
        try:
            os.close(fd)
        except OSError:
            pass


def is_lock_busy(lock_path=DEFAULT_LOCK_FILE):
    """Returns True if the lock is currently held by another process."""
    if not os.path.exists(lock_path):
        return False
    try:
        fd = os.open(lock_path, os.O_RDWR, 0o666)
    except OSError:
        return False

    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        fcntl.flock(fd, fcntl.LOCK_UN)
        return False
    except (BlockingIOError, IOError, OSError):
        return True
    finally:
        try:
            os.close(fd)
        except OSError:
            pass


def run_locked(cmd, lock_path=DEFAULT_LOCK_FILE, timeout_sec=DEFAULT_TIMEOUT_SEC):
    """
    Executes cmd under an exclusive POSIX flock.
    cmd can be a list of string args or a single shell string.
    Returns the subprocess exit code.
    """
    with sprint_lock(lock_path=lock_path, timeout_sec=timeout_sec):
        if isinstance(cmd, str):
            res = subprocess.run(cmd, shell=True)
        else:
            res = subprocess.run(cmd)
        return res.returncode


def main():
    parser = argparse.ArgumentParser(
        description="POSIX flock serialization wrapper for parallel sprint execution (REQ-PRO-041.2).",
        usage="%(prog)s [options] [--] <command> [args...]",
    )
    parser.add_argument(
        "--lock-file",
        default=os.environ.get("ATT_LOCK_FILE", DEFAULT_LOCK_FILE),
        help=f"Path to lock file (default: {DEFAULT_LOCK_FILE} or $ATT_LOCK_FILE).",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=float(os.environ.get("ATT_LOCK_TIMEOUT", DEFAULT_TIMEOUT_SEC)),
        help=f"Timeout in seconds waiting for lock (default: {DEFAULT_TIMEOUT_SEC}s or $ATT_LOCK_TIMEOUT).",
    )
    parser.add_argument(
        "--check-status",
        action="store_true",
        help="Check whether lock is currently held and exit (0=unlocked, 1=locked).",
    )
    parser.add_argument(
        "cmd",
        nargs=argparse.REMAINDER,
        help="Command to run under lock.",
    )

    args = parser.parse_args()

    if args.check_status:
        busy = is_lock_busy(args.lock_file)
        if busy:
            print(f"LOCKED: {args.lock_file} is held by another process.")
            sys.exit(1)
        else:
            print(f"UNLOCKED: {args.lock_file} is free.")
            sys.exit(0)

    cmd = args.cmd
    if cmd and cmd[0] == "--":
        cmd = cmd[1:]

    if not cmd:
        parser.print_help(sys.stderr)
        sys.exit(2)

    try:
        ret = run_locked(cmd, lock_path=args.lock_file, timeout_sec=args.timeout)
        sys.exit(ret)
    except TimeoutError as te:
        print(f"ERROR: {te}", file=sys.stderr)
        sys.exit(124)
    except Exception as exc:
        print(f"ERROR: Failed to run command under lock: {exc}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
