#!/usr/bin/env python3
"""
tools/test_baseline_manager.py
Offline unit tests for tools/baseline_manager.py (REQ-PRO-119 / TST-PRO-119.1..3).
Uses isolated temporary Git repositories; never mutates the live workspace or Jira.
"""

import json
import os
import subprocess
import tempfile
import unittest

from tools.baseline_manager import (
    audit_and_cleanup_branches,
    create_annotated_tag,
    generate_baseline_manifest,
    main as baseline_main,
    sanitize_baseline_dirname,
)


def _git_env():
    env = os.environ.copy()
    env.update({
        "GIT_AUTHOR_NAME": "Test Agent",
        "GIT_AUTHOR_EMAIL": "test@example.com",
        "GIT_COMMITTER_NAME": "Test Agent",
        "GIT_COMMITTER_EMAIL": "test@example.com",
    })
    return env


def _init_temp_git_repo(tmpdir: str) -> str:
    env = _git_env()
    subprocess.check_call(["git", "init", "-b", "develop"], cwd=tmpdir, env=env, stdout=subprocess.DEVNULL)
    readme = os.path.join(tmpdir, "README.md")
    with open(readme, "w", encoding="utf-8") as f:
        f.write("seed\n")
    subprocess.check_call(["git", "add", "README.md"], cwd=tmpdir, env=env, stdout=subprocess.DEVNULL)
    subprocess.check_call(["git", "commit", "-m", "initial commit"], cwd=tmpdir, env=env, stdout=subprocess.DEVNULL)
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=tmpdir, text=True).strip()
    return sha


class TestBaselineManager(unittest.TestCase):

    def test_sanitize_baseline_dirname(self):
        self.assertEqual(sanitize_baseline_dirname("sprint/2026-11-17_02_Spät"), "sprint_2026-11-17_02_Spät")
        self.assertEqual(sanitize_baseline_dirname("V5.0.0"), "V5.0.0")

    def test_tst_pro_119_1_create_annotated_tag_and_refuse_overwrite_and_collision(self):
        """TST-PRO-119.1: Annotated tag creation, overwrite refusal, and branch/tag collision refusal."""
        with tempfile.TemporaryDirectory() as tmpdir:
            head_sha = _init_temp_git_repo(tmpdir)

            # 1. Create immutable annotated tag
            ok, msg = create_annotated_tag("sprint/2026-99.1", target_ref="HEAD", message="Sprint 99.1", repo_root=tmpdir)
            self.assertTrue(ok, msg)
            tag_type = subprocess.check_output(
                ["git", "cat-file", "-t", "refs/tags/sprint/2026-99.1"], cwd=tmpdir, text=True
            ).strip()
            self.assertEqual(tag_type, "tag")

            # 2. Refuse overwriting existing tag (even via CLI)
            rc = baseline_main(["--repo-root", tmpdir, "tag", "sprint/2026-99.1", "--target", head_sha])
            self.assertNotEqual(rc, 0)

            # 3. Refuse branch/tag ref collision
            subprocess.check_call(
                ["git", "branch", "sprint/2026-99.2"], cwd=tmpdir, env=_git_env(), stdout=subprocess.DEVNULL
            )
            ok_col, msg_col = create_annotated_tag("sprint/2026-99.2", repo_root=tmpdir)
            self.assertFalse(ok_col)
            self.assertIn("collision", msg_col.lower())

    def test_tst_pro_119_2_generate_baseline_manifest(self):
        """TST-PRO-119.2: Deterministic baseline content manifest generation."""
        with tempfile.TemporaryDirectory() as tmpdir:
            head_sha = _init_temp_git_repo(tmpdir)
            ok_tag, msg_tag = create_annotated_tag("sprint/2026-99.1", repo_root=tmpdir)
            self.assertTrue(ok_tag, msg_tag)

            os.makedirs(os.path.join(tmpdir, "docs", "engineering", "test_evidence"), exist_ok=True)
            os.makedirs(os.path.join(tmpdir, "docs", "management"), exist_ok=True)

            with open(os.path.join(tmpdir, "docs", "requirements.md"), "w", encoding="utf-8") as f:
                f.write(
                    "| [Core](requirements/req.md) | Scope | `PRO` | 10 | 8 | 1 | 1 |\n\n"
                    "**Total Living Requirements**: 10 across 1 shards.\n"
                )
            with open(os.path.join(tmpdir, "docs", "tests.md"), "w", encoding="utf-8") as f:
                f.write(
                    "| [Core](tests/tst.md) | Scope | `PRO` | 12 | 9 | 2 | 1 |\n\n"
                    "**Total Living Tests**: 12 across 1 shards.\n"
                )
            ev_json = os.path.join(tmpdir, "docs", "engineering", "test_evidence", "sprint_2026-99.1.json")
            ev_md = os.path.join(tmpdir, "docs", "engineering", "test_evidence", "sprint_2026-99.1.md")
            with open(ev_json, "w", encoding="utf-8") as f:
                json.dump({"unit_tests": {"total": 42, "passed": 42, "failures": 0, "errors": 0}}, f)
            with open(ev_md, "w", encoding="utf-8") as f:
                f.write("# Test Evidence\n")
            with open(os.path.join(tmpdir, "docs", "management", "risk_register.md"), "w", encoding="utf-8") as f:
                f.write(
                    "| **RISK-001** | Operational | **Key Bottleneck**: desc | 4 | 4 | **16** | **High** | mit | ATT-1 | RM |\n"
                )

            ok_m, out_file, msg_m = generate_baseline_manifest(
                "sprint/2026-99.1",
                target_ref="sprint/2026-99.1",
                governance_status="PASS (exit 0)",
                repo_root=tmpdir,
            )
            self.assertTrue(ok_m, msg_m)
            expected_path = os.path.join(tmpdir, "docs", "releases", "sprint_2026-99.1", "manifest.md")
            self.assertEqual(os.path.abspath(out_file), os.path.abspath(expected_path))
            with open(out_file, "r", encoding="utf-8") as f:
                content = f.read()

            self.assertIn("sprint/2026-99.1", content)
            self.assertIn(head_sha, content)
            self.assertIn("| `docs/requirements.md` | 10 | 8 | 1 | 1 |", content)
            self.assertIn("| `docs/tests.md` | 12 | 9 | 2 | 1 |", content)
            self.assertIn("docs/engineering/test_evidence/sprint_2026-99.1.md", content)
            self.assertIn("PASS (42/42 passed", content)
            self.assertIn("RISK-001 (High, E=16)", content)
            self.assertIn("PASS (exit 0)", content)

    def test_tst_pro_119_3_cleanup_audit_dry_run_and_po_gate(self):
        """TST-PRO-119.3: Dry-run reports without mutation; --execute requires --confirm-po and only deletes merged."""
        with tempfile.TemporaryDirectory() as tmpdir:
            env = _git_env()
            _init_temp_git_repo(tmpdir)

            # Create a merged branch and a lightweight tag
            subprocess.check_call(["git", "branch", "feature/ATT-merged"], cwd=tmpdir, env=env)
            subprocess.check_call(["git", "tag", "sprint/2026-00.0-lightweight"], cwd=tmpdir, env=env)

            # Create an unmerged branch with a new commit
            subprocess.check_call(["git", "checkout", "-b", "feature/ATT-unmerged"], cwd=tmpdir, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            with open(os.path.join(tmpdir, "wip.txt"), "w", encoding="utf-8") as f:
                f.write("wip\n")
            subprocess.check_call(["git", "add", "wip.txt"], cwd=tmpdir, env=env)
            subprocess.check_call(["git", "commit", "-m", "wip"], cwd=tmpdir, env=env, stdout=subprocess.DEVNULL)
            subprocess.check_call(["git", "checkout", "develop"], cwd=tmpdir, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

            # 1. Dry-run mutates nothing
            ok_dry, rep_dry, _ = audit_and_cleanup_branches(base_ref="develop", execute=False, repo_root=tmpdir)
            self.assertTrue(ok_dry)
            self.assertEqual(rep_dry["mode"], "dry-run")
            self.assertIn("feature/ATT-merged", rep_dry["merged_local_branches"])
            self.assertNotIn("feature/ATT-unmerged", rep_dry["merged_local_branches"])
            self.assertEqual(rep_dry["lightweight_tags_count"], 1)
            self.assertEqual(rep_dry["deleted_branches"], [])

            # 2. --execute without --confirm-po fails loudly
            ok_no_po, _, msg_no_po = audit_and_cleanup_branches(base_ref="develop", execute=True, confirm_po=False, repo_root=tmpdir)
            self.assertFalse(ok_no_po)
            self.assertIn("--confirm-po", msg_no_po)

            # 3. --execute --confirm-po refuses unmerged branch
            ok_unm, _, msg_unm = audit_and_cleanup_branches(
                base_ref="develop",
                execute=True,
                confirm_po=True,
                target_branches=["feature/ATT-unmerged"],
                repo_root=tmpdir,
            )
            self.assertFalse(ok_unm)
            self.assertIn("unmerged", msg_unm.lower())

            # 4. --execute --confirm-po deletes merged local branch only
            ok_exec, rep_exec, _ = audit_and_cleanup_branches(
                base_ref="develop",
                execute=True,
                confirm_po=True,
                target_branches=["feature/ATT-merged"],
                repo_root=tmpdir,
            )
            self.assertTrue(ok_exec)
            self.assertEqual(rep_exec["deleted_branches"], ["feature/ATT-merged"])
            branches_after = subprocess.check_output(["git", "branch"], cwd=tmpdir, text=True)
            self.assertNotIn("feature/ATT-merged", branches_after)
            self.assertIn("feature/ATT-unmerged", branches_after)


if __name__ == "__main__":
    unittest.main()
