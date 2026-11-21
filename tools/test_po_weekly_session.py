#!/usr/bin/env python3
"""
Unit tests for tools/po_weekly_session.py (ATT-9545 Weekly PO Decision Session & Signed Baseline).
"""

import json
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TOOLS_DIR = os.path.join(REPO_ROOT, "tools")
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

import method_scorecard
import po_weekly_session


class TestPoWeeklySession(unittest.TestCase):
    def test_record_approved_session_and_scorecard_reads_minutes(self):
        """ATT-9545 Criterion 1: Approved session writes Human-Approved record referencing baseline commit."""
        with tempfile.TemporaryDirectory() as tmp:
            jsonl = os.path.join(tmp, "po_sessions.jsonl")
            reviews = os.path.join(tmp, "reviews")
            rec = po_weekly_session.record_po_session(
                iso_week="2026-KW48",
                performed=True,
                baseline_sha="8bcbb880",
                minutes=42.5,
                approver="Rainer",
                notes="Approved KW48 baseline and Lean AAA v2 rules.",
                po_sessions_path=jsonl,
                reviews_dir=reviews,
            )
            self.assertEqual(rec["status"], "APPROVED")
            self.assertIn("Human-Approved: Rainer", rec["human_approved_trailer"])
            self.assertIn("baseline=8bcbb880", rec["human_approved_trailer"])
            self.assertIsNone(rec["deviation"])
            self.assertEqual(po_weekly_session.get_last_approved_baseline_sha(jsonl), "8bcbb880")
            self.assertEqual(method_scorecard.read_po_minutes(jsonl, "2026-KW48"), 42.5)

    def test_unperformed_session_records_truthful_deviation_never_synthetic_approval(self):
        """ATT-9545 Criterion 2: Missed week records 'PO session not performed' and never synthetic approval."""
        with tempfile.TemporaryDirectory() as tmp:
            jsonl = os.path.join(tmp, "po_sessions.jsonl")
            reviews = os.path.join(tmp, "reviews")
            rec = po_weekly_session.ensure_weekly_session_truthfulness(
                "2026-KW49", po_sessions_path=jsonl, reviews_dir=reviews
            )
            self.assertEqual(rec["status"], "NOT_PERFORMED")
            self.assertEqual(rec["deviation"], "PO session not performed")
            self.assertIsNone(rec["human_approved_trailer"])
            self.assertIsNone(rec["minutes"])
            self.assertIsNone(method_scorecard.read_po_minutes(jsonl, "2026-KW49"))
            dev_file = os.path.join(reviews, "po_deviation_2026-KW49.md")
            self.assertTrue(os.path.isfile(dev_file))
            with open(dev_file, "r", encoding="utf-8") as f:
                self.assertIn("PO session not performed", f.read())

    def test_generate_weekly_digest_contains_all_required_sections(self):
        with tempfile.TemporaryDirectory() as tmp:
            jsonl = os.path.join(tmp, "po_sessions.jsonl")
            out_dir = os.path.join(tmp, "reviews")
            fake_git = method_scorecard.parse_git_numstat_output("COMMIT:1\n50\t0\tapp/src/main/A.kt\n")
            with patch("po_weekly_session.collect_req_diff_headings", return_value=["REQ-PRO-200: Lean Gate"]), \
                 patch("po_weekly_session.collect_release_plan_diff", return_value="release_plan.md | 2 +-"), \
                 patch("po_weekly_session.sample_accepted_tickets", return_value=[
                     {"key": "ATT-9540", "commit_sha": "45f1ca4b", "summary": "Risk tiers", "evidence_bundle": "docs/engineering/test_evidence/tickets/ATT-9540", "ticket_doc": None},
                     {"key": "ATT-9541", "commit_sha": "f14ffa73", "summary": "Tier B doc", "evidence_bundle": None, "ticket_doc": "docs/engineering/tickets/ATT-9541.md"},
                 ]), \
                 patch("method_scorecard.collect_git_metrics", return_value=fake_git):
                digest_path, _ = po_weekly_session.generate_weekly_digest(
                    iso_week="2026-KW48",
                    repo_root=REPO_ROOT,
                    po_sessions_path=jsonl,
                    out_dir=out_dir,
                    query_jira=False,
                    seed=1,
                )
            with open(digest_path, "r", encoding="utf-8") as f:
                text = f.read()
            self.assertIn("REQ-PRO-200: Lean Gate", text)
            self.assertIn("ATT-9540", text)
            self.assertIn("ATT-9541", text)
            self.assertIn("Weekly Method Scorecard", text)
            self.assertIn("record-session", text)


if __name__ == "__main__":
    unittest.main()
