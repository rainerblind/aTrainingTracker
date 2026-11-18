#!/usr/bin/env python3
"""
tools/test_check_static_analysis_baselines.py

Offline unit tests for tools/check_static_analysis_baselines.py (REQ-PRO-123.2 / TST-PRO-123.2).
Uses temporary Git repositories and synthetic XML baselines (zero network/Jira mutations).
"""

import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from check_static_analysis_baselines import (
    DEFAULT_DETEKT_CEILING,
    DEFAULT_LINT_CEILING,
    check_baseline_ratchet,
    collect_baseline_metrics,
    count_detekt_baseline_entries,
    count_lint_baseline_entries,
    format_static_analysis_lines,
    main,
)


def _detekt_xml(count):
    ids = "\n".join(f"    <ID>Rule:File{i}.kt</ID>" for i in range(count))
    return f"<SmellBaseline>\n  <CurrentIssues>\n{ids}\n  </CurrentIssues>\n</SmellBaseline>\n"


def _lint_xml(count):
    issues = "\n".join(f'  <issue id="Sample{i}" message="msg"/>' for i in range(count))
    return f'<?xml version="1.0" encoding="UTF-8"?>\n<issues format="6">\n{issues}\n</issues>\n'


def _git(cwd, *args):
    subprocess.run(["git", "-C", str(cwd), *args], check=True, capture_output=True, text=True)


class TestCheckStaticAnalysisBaselines(unittest.TestCase):
    def setUp(self):
        self.tmpdir = Path(tempfile.mkdtemp(prefix="att_baseline_ratchet_"))
        (self.tmpdir / "app").mkdir(parents=True)

    def tearDown(self):
        shutil.rmtree(self.tmpdir, ignore_errors=True)

    def _init_git_repo(self, detekt_n, lint_n):
        _git(self.tmpdir, "init")
        _git(self.tmpdir, "config", "user.email", "ci@example.com")
        _git(self.tmpdir, "config", "user.name", "CI")
        (self.tmpdir / "app" / "detekt-baseline.xml").write_text(_detekt_xml(detekt_n), encoding="utf-8")
        (self.tmpdir / "app" / "lint-baseline.xml").write_text(_lint_xml(lint_n), encoding="utf-8")
        _git(self.tmpdir, "add", ".")
        _git(self.tmpdir, "commit", "-m", "ATT-0: base")
        _git(self.tmpdir, "branch", "-M", "develop")
        _git(self.tmpdir, "checkout", "-b", "feature/test")

    def test_xml_counters_and_live_repo_baselines(self):
        self.assertEqual(count_detekt_baseline_entries(_detekt_xml(3)), 3)
        self.assertEqual(count_lint_baseline_entries(_lint_xml(4)), 4)

        passed, details, _ = check_baseline_ratchet()
        self.assertTrue(passed)
        self.assertEqual(details["detekt_baseline_count"], DEFAULT_DETEKT_CEILING)
        self.assertEqual(details["lint_baseline_count"], DEFAULT_LINT_CEILING)

    def test_ratchet_passes_on_equal_or_reduced_vs_base_ref(self):
        self._init_git_repo(detekt_n=100, lint_n=20)
        passed, details, _ = check_baseline_ratchet(
            repo_root=self.tmpdir, base_ref="develop", max_detekt=200, max_lint=50
        )
        self.assertTrue(passed)
        self.assertEqual(details["detekt_base_count"], 100)
        self.assertEqual(details["lint_base_count"], 20)

        # Reduced baselines pass
        (self.tmpdir / "app" / "detekt-baseline.xml").write_text(_detekt_xml(95), encoding="utf-8")
        (self.tmpdir / "app" / "lint-baseline.xml").write_text(_lint_xml(18), encoding="utf-8")
        self.assertEqual(
            main(["--repo-root", str(self.tmpdir), "--base-ref", "develop", "--max-detekt", "200", "--max-lint", "50"]),
            0,
        )

    def test_ratchet_fails_when_detekt_or_lint_grows_above_base_ref(self):
        self._init_git_repo(detekt_n=100, lint_n=20)
        (self.tmpdir / "app" / "detekt-baseline.xml").write_text(_detekt_xml(101), encoding="utf-8")
        self.assertEqual(
            main(["--repo-root", str(self.tmpdir), "--base-ref", "develop", "--max-detekt", "200", "--max-lint", "50"]),
            1,
        )

        (self.tmpdir / "app" / "detekt-baseline.xml").write_text(_detekt_xml(100), encoding="utf-8")
        (self.tmpdir / "app" / "lint-baseline.xml").write_text(_lint_xml(21), encoding="utf-8")
        self.assertEqual(
            main(["--repo-root", str(self.tmpdir), "--base-ref", "develop", "--max-detekt", "200", "--max-lint", "50"]),
            1,
        )

    def test_shallow_clone_missing_base_ref_falls_back_to_ceiling(self):
        (self.tmpdir / "app" / "detekt-baseline.xml").write_text(_detekt_xml(50), encoding="utf-8")
        (self.tmpdir / "app" / "lint-baseline.xml").write_text(_lint_xml(10), encoding="utf-8")

        passed, details, messages = check_baseline_ratchet(
            repo_root=self.tmpdir, base_ref="origin/nonexistent", max_detekt=50, max_lint=10
        )
        self.assertTrue(passed)
        self.assertTrue(details["fallback_used"])
        self.assertTrue(any("falling back to ceiling" in m for m in messages))

        passed_over, _, _ = check_baseline_ratchet(
            repo_root=self.tmpdir, base_ref="origin/nonexistent", max_detekt=49, max_lint=10
        )
        self.assertFalse(passed_over)

    def test_collect_baseline_metrics_and_markdown_deltas(self):
        (self.tmpdir / "app" / "detekt-baseline.xml").write_text(_detekt_xml(9500), encoding="utf-8")
        (self.tmpdir / "app" / "lint-baseline.xml").write_text(_lint_xml(230), encoding="utf-8")
        history = [{"static_analysis": {"detekt_baseline_count": 9516, "lint_baseline_count": 238}}]

        sa = collect_baseline_metrics(repo_root=self.tmpdir, history=history)
        self.assertEqual(sa["detekt_baseline_count"], 9500)
        self.assertEqual(sa["lint_baseline_count"], 230)
        self.assertEqual(sa["detekt_delta"], -16)
        self.assertEqual(sa["lint_delta"], -8)

        md = "\n".join(format_static_analysis_lines(sa, 5))
        self.assertIn("Static Analysis Baseline Ratchet", md)
        self.assertIn("9500", md)
        self.assertIn("-16", md)
        self.assertIn("-8", md)


if __name__ == "__main__":
    unittest.main()
