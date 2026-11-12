#!/usr/bin/env python3
"""
parallel_sprint_lock.py

Provides POSIX flock-based mutual exclusion (REQ-PRO-041.2, TST-PRO-041.2) for concurrent
sprint operations across disjoint domain Epics.
Serializes resource-heavy Gradle test runs and Stage 5 git squash-merges onto sprint/<SPRINT_NAME>.
Provides the deterministic --merge-ticket subcommand for collision-free multi-worktree merges.

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


def git_cmd(args, cwd=None, check=True):
    """Executes a git command with output capture and error checking."""
    cmd = ["git"] + args
    res = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if check and res.returncode != 0:
        err = res.stderr.strip() if res.stderr else res.stdout.strip()
        raise RuntimeError(f"Git command failed: {' '.join(cmd)}: {err}")
    return res


def get_worktrees(cwd=None):
    """
    Parses 'git worktree list --porcelain'.
    Returns list of dicts: [{'path': ..., 'head': ..., 'branch': ...}, ...]
    The first entry is always the main worktree.
    """
    res = git_cmd(["worktree", "list", "--porcelain"], cwd=cwd, check=True)
    worktrees = []
    current = {}
    for raw_line in res.stdout.splitlines():
        line = raw_line.strip()
        if not line:
            if current:
                worktrees.append(current)
                current = {}
            continue
        if line.startswith("worktree "):
            current["path"] = os.path.abspath(line[len("worktree "):].strip())
        elif line.startswith("HEAD "):
            current["head"] = line[len("HEAD "):].strip()
        elif line.startswith("branch "):
            current["branch"] = line[len("branch "):].strip()
        elif line == "detached":
            current["detached"] = True
        elif line == "bare":
            current["bare"] = True
    if current:
        worktrees.append(current)
    return worktrees


def guard_submodules(cwd):
    """Restores ANT-Android-SDKs if it was accidentally replaced by a filesystem symlink."""
    submodule_path = os.path.join(cwd, "ANT-Android-SDKs")
    if os.path.islink(submodule_path):
        print(f"[merge-ticket] Detected symlink at {submodule_path}, removing symlink...", file=sys.stderr)
        try:
            os.unlink(submodule_path)
            subprocess.run(["git", "checkout", "HEAD", "--", "ANT-Android-SDKs"], cwd=cwd, check=False)
        except OSError:
            pass


def guard_submodules_staged(cwd):
    """Unstages ANT-Android-SDKs if staged as a symlink (mode 120000)."""
    res = subprocess.run(["git", "diff", "--cached", "--raw"], cwd=cwd, capture_output=True, text=True)
    if res.returncode == 0:
        for line in res.stdout.splitlines():
            if "ANT-Android-SDKs" in line and "120000" in line:
                print("[merge-ticket] Resetting staged symlink for ANT-Android-SDKs...", file=sys.stderr)
                subprocess.run(["git", "reset", "HEAD", "--", "ANT-Android-SDKs"], cwd=cwd, check=False)
                break


def _merge_ticket_impl(ticket_key, sprint=None, summary=None, cwd=None):
    """
    Internal execution logic for --merge-ticket.
    Must be invoked under sprint_lock.
    """
    if cwd is None:
        cwd = os.getcwd()

    current_wt = os.path.abspath(git_cmd(["rev-parse", "--show-toplevel"], cwd=cwd).stdout.strip())
    worktrees = get_worktrees(cwd=current_wt)
    if not worktrees:
        raise RuntimeError("No git worktrees found.")

    main_wt = worktrees[0]["path"]

    # Normalize ticket key and feature branch
    clean_key = ticket_key
    if clean_key.startswith("feature/"):
        clean_key = clean_key[len("feature/"):]
    feature_branch = f"feature/{clean_key}"

    if not summary:
        summary = f"{clean_key}: Implement {clean_key}"

    # Determine / normalize sprint branch
    if not sprint:
        main_branch = worktrees[0].get("branch", "")
        if main_branch.startswith("refs/heads/sprint/"):
            sprint = main_branch[len("refs/heads/"):]
        else:
            current_branch = git_cmd(["rev-parse", "--abbrev-ref", "HEAD"], cwd=current_wt).stdout.strip()
            if current_branch.startswith("sprint/"):
                sprint = current_branch
            else:
                raise RuntimeError("Sprint branch not specified and could not be auto-detected.")

    sprint_ref = sprint
    if sprint_ref.startswith("refs/heads/"):
        sprint_ref = sprint_ref[len("refs/heads/"):]
    if not sprint_ref.startswith("sprint/"):
        sprint_ref = f"sprint/{sprint_ref}"

    res_sprint = git_cmd(["show-ref", "--verify", f"refs/heads/{sprint_ref}"], cwd=current_wt, check=False)
    if res_sprint.returncode != 0:
        raise RuntimeError(f"Local sprint branch '{sprint_ref}' does not exist.")

    print(f"[merge-ticket] Merging {clean_key} into {sprint_ref}...", file=sys.stderr)
    print(f"[merge-ticket] Current worktree: {current_wt}", file=sys.stderr)
    print(f"[merge-ticket] Main worktree: {main_wt}", file=sys.stderr)

    # 1. Guard against submodule symlink corruption
    guard_submodules(current_wt)

    # 2. Detach HEAD first so uncommitted wip commit does not advance sprint_ref if already on it
    git_cmd(["checkout", "--detach", "HEAD"], cwd=current_wt)

    # 3. Stage and commit uncommitted changes in current worktree
    git_cmd(["add", "-A"], cwd=current_wt)
    guard_submodules_staged(current_wt)
    diff_cached = git_cmd(["diff", "--cached", "--quiet"], cwd=current_wt, check=False)
    if diff_cached.returncode != 0:
        git_cmd(["commit", "-m", f"wip({clean_key})"], cwd=current_wt)

    # 4. Rebase onto local sprint branch
    try:
        git_cmd(["rebase", sprint_ref], cwd=current_wt)
    except Exception as exc:
        print(f"[merge-ticket] Rebase failed, aborting rebase: {exc}", file=sys.stderr)
        subprocess.run(["git", "rebase", "--abort"], cwd=current_wt, check=False)
        raise

    # Soft-reset onto sprint_ref to collapse all ticket changes onto a single commit
    git_cmd(["reset", "--soft", sprint_ref], cwd=current_wt)

    # 5. Recalculate living documentation master indexes if verify tool exists
    gov_tool = os.path.join(current_wt, "tools", "verify_requirement_governance.py")
    if not os.path.exists(gov_tool):
        gov_tool = os.path.join(main_wt, "tools", "verify_requirement_governance.py")

    if os.path.exists(gov_tool):
        print("[merge-ticket] Recalculating living documentation master indexes...", file=sys.stderr)
        subprocess.run([sys.executable, gov_tool, "--sync-indexes"], cwd=current_wt, check=False)

    # Stage any index updates
    guard_submodules(current_wt)
    git_cmd(["add", "-A"], cwd=current_wt)
    guard_submodules_staged(current_wt)

    # Create the single atomic commit if there are changes
    diff_final = git_cmd(["diff", "--cached", "--quiet"], cwd=current_wt, check=False)
    sprint_sha = git_cmd(["rev-parse", f"refs/heads/{sprint_ref}"], cwd=current_wt).stdout.strip()
    if diff_final.returncode != 0:
        git_cmd(["commit", "-m", summary], cwd=current_wt)
        new_sha = git_cmd(["rev-parse", "HEAD"], cwd=current_wt).stdout.strip()
    else:
        new_sha = git_cmd(["rev-parse", "HEAD"], cwd=current_wt).stdout.strip()
        print(f"[merge-ticket] Note: No staged diff detected against {sprint_ref}.", file=sys.stderr)

    # 5. Fast-forward sprint_ref without branch checkout collision
    if new_sha != sprint_sha:
        worktrees = get_worktrees(cwd=current_wt)
        sprint_full_ref = f"refs/heads/{sprint_ref}"
        sprint_worktree = None
        for wt in worktrees:
            if wt.get("branch") == sprint_full_ref:
                sprint_worktree = wt["path"]
                break

        if sprint_worktree:
            print(
                f"[merge-ticket] Fast-forwarding {sprint_ref} at worktree '{sprint_worktree}' to {new_sha[:8]}...",
                file=sys.stderr,
            )
            git_cmd(["-C", sprint_worktree, "merge", "--ff-only", new_sha], cwd=current_wt)
        else:
            print(
                f"[merge-ticket] Updating branch ref {sprint_ref} directly to {new_sha[:8]}...",
                file=sys.stderr,
            )
            git_cmd(["branch", "-f", sprint_ref, new_sha], cwd=current_wt)

    # 6. Detach any sibling worktree from feature_branch and delete feature_branch
    worktrees = get_worktrees(cwd=current_wt)
    feature_full_ref = f"refs/heads/{feature_branch}"
    for wt in worktrees:
        if wt.get("branch") == feature_full_ref:
            wt_path = wt["path"]
            print(f"[merge-ticket] Detaching worktree '{wt_path}' from {feature_branch}...", file=sys.stderr)
            git_cmd(["-C", wt_path, "checkout", "--detach", sprint_ref], cwd=current_wt)

    # Delete feature branch if it exists
    res_feat = git_cmd(["show-ref", "--verify", feature_full_ref], cwd=current_wt, check=False)
    if res_feat.returncode == 0:
        print(f"[merge-ticket] Deleting branch {feature_branch}...", file=sys.stderr)
        git_cmd(["branch", "-D", feature_branch], cwd=current_wt, check=False)

    # Clean up any temporary branches
    for temp_name in [f"temp_{clean_key}", f"temp_sprint_{clean_key}"]:
        temp_full = f"refs/heads/{temp_name}"
        if git_cmd(["show-ref", "--verify", temp_full], cwd=current_wt, check=False).returncode == 0:
            git_cmd(["branch", "-D", temp_name], cwd=current_wt, check=False)

    # Leave current worktree cleanly aligned at sprint_ref
    if current_wt == main_wt:
        git_cmd(["checkout", sprint_ref], cwd=current_wt, check=False)
    else:
        git_cmd(["checkout", "--detach", sprint_ref], cwd=current_wt, check=False)

    print(f"[merge-ticket] Successfully merged {clean_key} onto {sprint_ref} ({new_sha[:8]}).", file=sys.stderr)
    return 0


def run_merge_ticket(
    ticket_key,
    sprint=None,
    summary=None,
    cwd=None,
    lock_path=DEFAULT_LOCK_FILE,
    timeout_sec=DEFAULT_TIMEOUT_SEC,
):
    """
    Deterministically squash-merges ticket_key onto sprint branch under exclusive POSIX flock.
    """
    with sprint_lock(lock_path=lock_path, timeout_sec=timeout_sec):
        return _merge_ticket_impl(ticket_key, sprint=sprint, summary=summary, cwd=cwd)


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
        "--merge-ticket",
        metavar="KEY",
        help="Deterministically squash-merge ticket KEY onto the sprint branch under flock.",
    )
    parser.add_argument(
        "--sprint",
        help="Sprint branch name (e.g. sprint/2026-46.3). Defaults to active sprint branch.",
    )
    parser.add_argument(
        "--summary",
        help="Atomic commit summary for the merged ticket.",
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

    if args.merge_ticket:
        try:
            ret = run_merge_ticket(
                ticket_key=args.merge_ticket,
                sprint=args.sprint,
                summary=args.summary,
                lock_path=args.lock_file,
                timeout_sec=args.timeout,
            )
            sys.exit(ret)
        except TimeoutError as te:
            print(f"ERROR: {te}", file=sys.stderr)
            sys.exit(124)
        except Exception as exc:
            print(f"ERROR: Failed to merge ticket {args.merge_ticket}: {exc}", file=sys.stderr)
            sys.exit(1)

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
