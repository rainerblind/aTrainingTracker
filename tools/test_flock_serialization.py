#!/usr/bin/env python3
"""
test_flock_serialization.py

Unit and integration tests for parallel_sprint_lock.py / locked_build_merge.py (REQ-PRO-041.2, TST-PRO-041.2).
Validates exclusive POSIX flock serialization, timeout handling, exit code propagation,
automatic cleanup on normal and exceptional terminations, and deterministic multi-worktree --merge-ticket execution.
"""

import importlib.util
import os
import subprocess
import sys
import tempfile
import time
import unittest

_TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
if _TOOLS_DIR not in sys.path:
    sys.path.insert(0, _TOOLS_DIR)

_SIBLING_PSL_PATH = os.path.abspath(os.path.join(_TOOLS_DIR, "parallel_sprint_lock.py"))
_cached_psl = sys.modules.get("parallel_sprint_lock")
if _cached_psl is not None and os.path.abspath(getattr(_cached_psl, "__file__", "") or "") == _SIBLING_PSL_PATH:
    _psl_mod = _cached_psl
else:
    _psl_spec = importlib.util.spec_from_file_location("_att_sibling_parallel_sprint_lock", _SIBLING_PSL_PATH)
    _psl_mod = importlib.util.module_from_spec(_psl_spec)
    _psl_spec.loader.exec_module(_psl_mod)

format_commit_with_trailers = _psl_mod.format_commit_with_trailers
is_lock_busy = _psl_mod.is_lock_busy
run_locked = _psl_mod.run_locked
run_merge_ticket = _psl_mod.run_merge_ticket
sprint_lock = _psl_mod.sprint_lock
validate_commit_trailers = _psl_mod.validate_commit_trailers


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

    def _init_git_repo(self, sprint_branch="sprint/2026-46.3"):
        """Creates a temporary git repository with initial commit on sprint_branch."""
        repo_dir = os.path.join(self.temp_dir.name, "main_repo")
        os.makedirs(repo_dir, exist_ok=True)
        subprocess.run(["git", "init", "-b", sprint_branch, repo_dir], check=True, capture_output=True)
        subprocess.run(["git", "config", "user.name", "Tester"], cwd=repo_dir, check=True)
        subprocess.run(["git", "config", "user.email", "tester@example.com"], cwd=repo_dir, check=True)
        readme = os.path.join(repo_dir, "README.md")
        with open(readme, "w") as f:
            f.write("# Main Repo\n")
        subprocess.run(["git", "add", "README.md"], cwd=repo_dir, check=True)
        subprocess.run(["git", "commit", "-m", "Initial commit"], cwd=repo_dir, check=True, capture_output=True)
        return repo_dir

    def test_merge_ticket_multi_worktree_success(self):
        """
        Verifies that --merge-ticket merges changes from a child worktree into local sprint branch
        without branch checkout collisions, deletes the feature branch, and fast-forwards the main repo.
        """
        main_repo = self._init_git_repo()
        wt1 = os.path.join(self.temp_dir.name, "wt1")
        subprocess.run(
            ["git", "worktree", "add", "-b", "feature/ATT-TEST1", wt1, "sprint/2026-46.3"],
            cwd=main_repo,
            check=True,
            capture_output=True,
        )

        # Create changes in wt1
        doc_file = os.path.join(wt1, "feature1.txt")
        with open(doc_file, "w") as f:
            f.write("Feature 1 implementation\n")

        ret = run_merge_ticket(
            ticket_key="ATT-TEST1",
            sprint="sprint/2026-46.3",
            summary="ATT-TEST1: Add feature 1",
            cwd=wt1,
            lock_path=self.lock_file,
        )
        self.assertEqual(ret, 0)

        # Main repo must have received the commit and the file
        log_res = subprocess.run(
            ["git", "log", "--oneline", "-n", "1"],
            cwd=main_repo,
            capture_output=True,
            text=True,
            check=True,
        )
        self.assertIn("ATT-TEST1: Add feature 1", log_res.stdout)
        self.assertTrue(os.path.exists(os.path.join(main_repo, "feature1.txt")))

        # feature/ATT-TEST1 branch must be deleted
        ref_check = subprocess.run(
            ["git", "show-ref", "--verify", "refs/heads/feature/ATT-TEST1"],
            cwd=main_repo,
            capture_output=True,
        )
        self.assertNotEqual(ref_check.returncode, 0)

    def test_merge_ticket_from_main_repo(self):
        """
        Verifies that --merge-ticket executed directly from the main repository worktree
        (where sprint branch is currently checked out) collapses changes to a single commit
        with the requested summary without advancing sprint prematurely with wip.
        """
        main_repo = self._init_git_repo()
        doc_file = os.path.join(main_repo, "main_change.txt")
        with open(doc_file, "w") as f:
            f.write("Change directly in main repo\n")

        ret = run_merge_ticket(
            ticket_key="ATT-MAIN",
            sprint="sprint/2026-46.3",
            summary="ATT-MAIN: Direct commit summary",
            cwd=main_repo,
            lock_path=self.lock_file,
        )
        self.assertEqual(ret, 0)

        # Check commit summary in main repo
        log_res = subprocess.run(
            ["git", "log", "--oneline", "-n", "1"],
            cwd=main_repo,
            capture_output=True,
            text=True,
            check=True,
        )
        self.assertIn("ATT-MAIN: Direct commit summary", log_res.stdout)
        self.assertNotIn("wip(ATT-MAIN)", log_res.stdout)

    def test_merge_ticket_concurrent_worktrees_serialization(self):
        """
        Verifies that two worktrees executing --merge-ticket under POSIX flock serialize cleanly,
        rebase disjoint changes linearly onto the sprint branch, delete both feature branches,
        and leave zero checkout conflicts.
        """
        main_repo = self._init_git_repo()
        cli_path = os.path.join(os.path.dirname(__file__), "parallel_sprint_lock.py")

        wt1 = os.path.join(self.temp_dir.name, "wt1")
        wt2 = os.path.join(self.temp_dir.name, "wt2")
        subprocess.run(
            ["git", "worktree", "add", "-b", "feature/ATT-101", wt1, "sprint/2026-46.3"],
            cwd=main_repo,
            check=True,
            capture_output=True,
        )
        subprocess.run(
            ["git", "worktree", "add", "-b", "feature/ATT-102", wt2, "sprint/2026-46.3"],
            cwd=main_repo,
            check=True,
            capture_output=True,
        )

        with open(os.path.join(wt1, "mod1.txt"), "w") as f:
            f.write("Changes 101\n")
        with open(os.path.join(wt2, "mod2.txt"), "w") as f:
            f.write("Changes 102\n")

        p1_cmd = [
            sys.executable,
            cli_path,
            "--lock-file",
            self.lock_file,
            "--merge-ticket",
            "ATT-101",
            "--sprint",
            "sprint/2026-46.3",
            "--summary",
            "ATT-101: Add mod1",
        ]
        p2_cmd = [
            sys.executable,
            cli_path,
            "--lock-file",
            self.lock_file,
            "--merge-ticket",
            "ATT-102",
            "--sprint",
            "sprint/2026-46.3",
            "--summary",
            "ATT-102: Add mod2",
        ]

        proc1 = subprocess.Popen(p1_cmd, cwd=wt1)
        time.sleep(0.1)
        proc2 = subprocess.Popen(p2_cmd, cwd=wt2)

        proc1.wait(timeout=15)
        proc2.wait(timeout=15)

        self.assertEqual(proc1.returncode, 0)
        self.assertEqual(proc2.returncode, 0)

        # Both files must exist in the main repository
        self.assertTrue(os.path.exists(os.path.join(main_repo, "mod1.txt")))
        self.assertTrue(os.path.exists(os.path.join(main_repo, "mod2.txt")))

        # Check git log in main repo
        log_res = subprocess.run(
            ["git", "log", "--oneline", "-n", "3"],
            cwd=main_repo,
            capture_output=True,
            text=True,
            check=True,
        )
        self.assertIn("ATT-101: Add mod1", log_res.stdout)
        self.assertIn("ATT-102: Add mod2", log_res.stdout)

        # Neither feature branch should exist
        ref1 = subprocess.run(["git", "show-ref", "--verify", "refs/heads/feature/ATT-101"], cwd=main_repo, capture_output=True)
        ref2 = subprocess.run(["git", "show-ref", "--verify", "refs/heads/feature/ATT-102"], cwd=main_repo, capture_output=True)
        self.assertNotEqual(ref1.returncode, 0)
        self.assertNotEqual(ref2.returncode, 0)

    def test_merge_ticket_with_agent_trailers(self):
        """Verifies that format_commit_with_trailers properly appends mandatory commit trailers (REQ-PRO-108.3)."""
        msg = format_commit_with_trailers(
            "ATT-8151: Fix role separation",
            agent_role="agent1",
            agent_model="pro",
            reviewed_by="Agent 2 (Reviewer) <agent2@atrainingtracker.local>",
        )
        self.assertIn("ATT-8151: Fix role separation", msg)
        self.assertIn("Agent-Role: agent1", msg)
        self.assertIn("Agent-Model: pro", msg)
        self.assertIn("Reviewed-by: Agent 2 (Reviewer) <agent2@atrainingtracker.local>", msg)
        is_valid, missing = validate_commit_trailers(msg)
        self.assertTrue(is_valid)
        self.assertEqual(missing, [])

    def test_merge_ticket_cross_worktree_post_stage6_and_dirty_guard(self):
        """
        TST-PRO-125.1: Verifies --merge-ticket invoked post-Stage-6 from a separate worktree
        fails fast if that worktree is dirty, and succeeds cleanly when clean without blind git add -A.
        """
        main_repo = self._init_git_repo()
        wt_feat = os.path.join(self.temp_dir.name, "wt_feat")
        wt_cold = os.path.join(self.temp_dir.name, "wt_cold")
        subprocess.run(
            ["git", "worktree", "add", "-b", "feature/ATT-8207-X", wt_feat, "sprint/2026-46.3"],
            cwd=main_repo,
            check=True,
            capture_output=True,
        )
        subprocess.run(
            ["git", "worktree", "add", "--detach", wt_cold, "sprint/2026-46.3"],
            cwd=main_repo,
            check=True,
            capture_output=True,
        )

        with open(os.path.join(wt_feat, "stage6_ok.txt"), "w") as f:
            f.write("Verified feature branch change\n")
        subprocess.run(["git", "add", "stage6_ok.txt"], cwd=wt_feat, check=True)
        subprocess.run(["git", "commit", "-m", "ATT-8207-X: Stage 5 verified"], cwd=wt_feat, check=True, capture_output=True)

        # Dirty unrelated worktree must fail fast (never blindly staged)
        dirty_file = os.path.join(wt_cold, "stray_untracked.txt")
        with open(dirty_file, "w") as f:
            f.write("unrelated\n")
        with self.assertRaises(RuntimeError):
            run_merge_ticket(
                ticket_key="ATT-8207-X",
                sprint="sprint/2026-46.3",
                summary="ATT-8207-X: Post-Stage-6 squash",
                cwd=wt_cold,
                lock_path=self.lock_file,
            )
        os.remove(dirty_file)

        # Clean cross-worktree post-PASS merge succeeds and deletes feature branch
        ret = run_merge_ticket(
            ticket_key="ATT-8207-X",
            sprint="sprint/2026-46.3",
            summary="ATT-8207-X: Post-Stage-6 squash",
            cwd=wt_cold,
            lock_path=self.lock_file,
        )
        self.assertEqual(ret, 0)
        self.assertTrue(os.path.exists(os.path.join(main_repo, "stage6_ok.txt")))
        ref_check = subprocess.run(
            ["git", "show-ref", "--verify", "refs/heads/feature/ATT-8207-X"],
            cwd=main_repo,
            capture_output=True,
        )
        self.assertNotEqual(ref_check.returncode, 0)

    def test_merge_ticket_resolves_master_index_rebase_conflicts_and_runs_sync_indexes(self):
        """
        TST-PRO-125.1/.2: Verifies that when sprint/<SPRINT> advances docs/requirements.md and
        docs/tests.md while a feature branch awaits Stage 6, --merge-ticket auto-resolves
        index conflicts, runs --sync-indexes, and aborts on non-index conflicts.
        """
        main_repo = self._init_git_repo()
        os.makedirs(os.path.join(main_repo, "docs"), exist_ok=True)
        os.makedirs(os.path.join(main_repo, "tools"), exist_ok=True)
        with open(os.path.join(main_repo, "docs", "requirements.md"), "w") as f:
            f.write("**Total Living Requirements**: 10 across 9 domain shards.\n")
        with open(os.path.join(main_repo, "docs", "tests.md"), "w") as f:
            f.write("**Total Living Tests**: 10 across 9 domain shards.\n")
        with open(os.path.join(main_repo, "shared.txt"), "w") as f:
            f.write("base\n")
        with open(os.path.join(main_repo, "tools", "verify_requirement_governance.py"), "w") as f:
            f.write(
                "import sys\n"
                "if '--sync-indexes' in sys.argv:\n"
                "    open('docs/requirements.md', 'w').write('**Total Living Requirements**: 12 across 9 domain shards.\\n')\n"
                "    open('docs/tests.md', 'w').write('**Total Living Tests**: 12 across 9 domain shards.\\n')\n"
            )
        subprocess.run(["git", "add", "."], cwd=main_repo, check=True)
        subprocess.run(["git", "commit", "-m", "Add index baseline"], cwd=main_repo, check=True, capture_output=True)

        wt_feat = os.path.join(self.temp_dir.name, "wt_idx")
        subprocess.run(
            ["git", "worktree", "add", "-b", "feature/ATT-IDX", wt_feat, "sprint/2026-46.3"],
            cwd=main_repo,
            check=True,
            capture_output=True,
        )

        # Sibling merges on sprint/2026-46.3 updating master indexes
        with open(os.path.join(main_repo, "docs", "requirements.md"), "w") as f:
            f.write("**Total Living Requirements**: 11 across 9 domain shards.\n")
        with open(os.path.join(main_repo, "docs", "tests.md"), "w") as f:
            f.write("**Total Living Tests**: 11 across 9 domain shards.\n")
        subprocess.run(["git", "add", "docs/requirements.md", "docs/tests.md"], cwd=main_repo, check=True)
        subprocess.run(["git", "commit", "-m", "ATT-SIB: Sibling index update"], cwd=main_repo, check=True, capture_output=True)

        # Feature branch modifies same index lines + feature file
        with open(os.path.join(wt_feat, "docs", "requirements.md"), "w") as f:
            f.write("**Total Living Requirements**: 11-feature across 9 domain shards.\n")
        with open(os.path.join(wt_feat, "docs", "tests.md"), "w") as f:
            f.write("**Total Living Tests**: 11-feature across 9 domain shards.\n")
        with open(os.path.join(wt_feat, "feature_idx.txt"), "w") as f:
            f.write("idx feature\n")
        subprocess.run(["git", "add", "."], cwd=wt_feat, check=True)
        subprocess.run(["git", "commit", "-m", "ATT-IDX: Stage 5 verified"], cwd=wt_feat, check=True, capture_output=True)

        ret = run_merge_ticket(
            ticket_key="ATT-IDX",
            sprint="sprint/2026-46.3",
            summary="ATT-IDX: Index conflict safe merge",
            cwd=wt_feat,
            lock_path=self.lock_file,
        )
        self.assertEqual(ret, 0)
        self.assertTrue(os.path.exists(os.path.join(main_repo, "feature_idx.txt")))
        with open(os.path.join(main_repo, "docs", "requirements.md")) as f:
            self.assertIn("12 across 9", f.read())

        # Non-index conflict must abort rebase cleanly and raise RuntimeError
        wt_bad = os.path.join(self.temp_dir.name, "wt_bad")
        subprocess.run(
            ["git", "worktree", "add", "-b", "feature/ATT-BAD", wt_bad, "sprint/2026-46.3"],
            cwd=main_repo,
            check=True,
            capture_output=True,
        )
        with open(os.path.join(main_repo, "shared.txt"), "w") as f:
            f.write("sprint side\n")
        subprocess.run(["git", "add", "shared.txt"], cwd=main_repo, check=True)
        subprocess.run(["git", "commit", "-m", "ATT-SIB2: edit shared"], cwd=main_repo, check=True, capture_output=True)

        with open(os.path.join(wt_bad, "shared.txt"), "w") as f:
            f.write("feature side\n")
        subprocess.run(["git", "add", "shared.txt"], cwd=wt_bad, check=True)
        subprocess.run(["git", "commit", "-m", "ATT-BAD: edit shared"], cwd=wt_bad, check=True, capture_output=True)

        with self.assertRaises(RuntimeError):
            run_merge_ticket(
                ticket_key="ATT-BAD",
                sprint="sprint/2026-46.3",
                summary="ATT-BAD: Should abort",
                cwd=wt_bad,
                lock_path=self.lock_file,
            )
        self.assertFalse(os.path.exists(os.path.join(wt_bad, ".git", "rebase-merge")))

    def test_merge_ticket_requires_stage6_pass_verdict_when_enforced(self):
        """ATT-9519 / Finding C-05: Refuse squash-merge unless [FINAL REVIEW (AGENT) — PASS] exists in Jira."""
        from unittest.mock import patch

        main_repo = self._init_git_repo()
        wt_feat = os.path.join(self.temp_dir.name, "wt_stage6_guard")
        subprocess.run(
            ["git", "worktree", "add", "-b", "feature/ATT-9519", wt_feat, "sprint/2026-46.3"],
            cwd=main_repo,
            check=True,
            capture_output=True,
        )
        with open(os.path.join(wt_feat, "guard.txt"), "w") as f:
            f.write("verified\n")
        subprocess.run(["git", "add", "guard.txt"], cwd=wt_feat, check=True)
        subprocess.run(["git", "commit", "-m", "ATT-9519: work"], cwd=wt_feat, check=True, capture_output=True)

        # Missing Stage 6 PASS verdict -> RuntimeError, sprint branch unchanged
        with patch("jira_util.verify_stage6_pass_verdict", return_value=(False, "No [FINAL REVIEW (AGENT) — PASS] verdict comment found on ATT-9519.")):
            with self.assertRaises(RuntimeError) as ctx:
                run_merge_ticket(
                    ticket_key="ATT-9519",
                    sprint="sprint/2026-46.3",
                    summary="ATT-9519: Guarded merge",
                    cwd=wt_feat,
                    lock_path=self.lock_file,
                    verify_stage6_verdict=True,
                )
            self.assertIn("FINAL REVIEW (AGENT)", str(ctx.exception))
        self.assertFalse(os.path.exists(os.path.join(main_repo, "guard.txt")))

        # With Stage 6 PASS verdict -> merge succeeds
        with patch("jira_util.verify_stage6_pass_verdict", return_value=(True, "Verified Stage 6 PASS verdict record on ATT-9519.")):
            ret = run_merge_ticket(
                ticket_key="ATT-9519",
                sprint="sprint/2026-46.3",
                summary="ATT-9519: Guarded merge",
                cwd=wt_feat,
                lock_path=self.lock_file,
                verify_stage6_verdict=True,
            )
            self.assertEqual(ret, 0)
        self.assertTrue(os.path.exists(os.path.join(main_repo, "guard.txt")))


if __name__ == "__main__":
    unittest.main()
