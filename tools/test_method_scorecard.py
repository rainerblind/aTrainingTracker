#!/usr/bin/env python3
"""
Unit tests for tools/method_scorecard.py (ATT-9539, Lean AAA v2 Weekly Method Scorecard).
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


class TestMethodScorecard(unittest.TestCase):
    def test_parse_iso_week(self):
        label, start, end = method_scorecard.parse_iso_week("2026-KW47")
        self.assertEqual(label, "2026-KW47")
        self.assertEqual(start.isoformat(), "2026-11-16")
        self.assertEqual(end.isoformat(), "2026-11-22")

        label2, _, _ = method_scorecard.parse_iso_week("2026-W48")
        self.assertEqual(label2, "2026-KW48")

        with self.assertRaises(ValueError):
            method_scorecard.parse_iso_week("2026-99-bad")

    def test_classify_path_five_protocol_classes(self):
        self.assertEqual(method_scorecard.classify_path("app/src/main/java/Foo.kt"), "production_code")
        self.assertEqual(method_scorecard.classify_path("app/src/test/java/FooTest.kt"), "tests")
        self.assertEqual(method_scorecard.classify_path("app/src/androidTest/java/BarTest.kt"), "tests")
        self.assertEqual(method_scorecard.classify_path("docs/engineering/tickets/ATT-1.md"), "per_ticket_docs")
        self.assertEqual(method_scorecard.classify_path("docs/engineering/analysis/ATT-1_analysis.md"), "per_ticket_docs")
        self.assertEqual(method_scorecard.classify_path("docs/requirements/req_ui.md"), "living_specs")
        self.assertEqual(method_scorecard.classify_path("docs/architecture.md"), "living_specs")
        self.assertEqual(method_scorecard.classify_path("tools/jira_util.py"), "process_tooling_and_other_docs")
        self.assertEqual(method_scorecard.classify_path(".agents/rules/aspice_governance.md"), "process_tooling_and_other_docs")

    def test_parse_git_numstat_output(self):
        sample = "\n".join([
            "COMMIT:aaaa",
            "60\t10\tapp/src/main/java/Foo.kt",
            "40\t5\tapp/src/test/java/FooTest.kt",
            "COMMIT:bbbb",
            "100\t0\tdocs/engineering/tickets/ATT-1.md",
        ])
        res = method_scorecard.parse_git_numstat_output(sample)
        self.assertEqual(res["effort_split"]["total_lines_added"], 200)
        self.assertEqual(res["effort_split"]["classes"]["production_code"]["share_percent"], 30.0)
        self.assertEqual(res["effort_split"]["classes"]["tests"]["share_percent"], 20.0)
        self.assertEqual(res["effort_split"]["classes"]["per_ticket_docs"]["share_percent"], 50.0)
        self.assertEqual(res["commits"]["total_non_merge_commits"], 2)
        self.assertEqual(res["commits"]["commits_touching_production_code"], 1)
        self.assertEqual(res["commits"]["commits_touching_production_code_percent"], 50.0)

    def test_compute_and_render_with_baseline_deltas_and_idempotent_jsonl(self):
        with tempfile.TemporaryDirectory() as tmp:
            baseline_path = os.path.join(tmp, "baseline.json")
            with open(baseline_path, "w", encoding="utf-8") as f:
                json.dump({
                    "iso_week": "2026-KW47",
                    "effort_split_by_path_class": {
                        "classes": {
                            "production_code": {"share_percent": 18.6},
                            "tests": {"share_percent": 27.2},
                            "per_ticket_docs": {"share_percent": 13.2},
                            "living_specs": {"share_percent": 15.9},
                            "process_tooling_and_other_docs": {"share_percent": 25.0},
                        }
                    },
                    "commits": {"commits_touching_production_code_percent": 52.3},
                    "gate_and_review_effectiveness": {
                        "gates_1_to_5": {"revision_rate_percent": 6.16},
                        "stage_6_cold_review": {"ticket_bounce_rate_percent": 34.5},
                    },
                    "escapes": {"process_escape_bugs_count": 5},
                    "throughput_and_cost": {
                        "completed_tickets": 160,
                        "completed_story_points": 313.0,
                        "mean_wall_minutes_per_ticket_across_3_lanes": 43.9,
                    },
                    "human_po_effort": {"weekly_po_minutes_measured": None},
                }, f)

            pm_path = os.path.join(tmp, "process_metrics.jsonl")
            with open(pm_path, "w", encoding="utf-8") as f:
                f.write(json.dumps({
                    "sprint_name": "2026-11-23_01_Früh",
                    "process_version": "aaa-v2",
                    "throughput": {"completed_tickets": 10, "completed_story_points": 25.0},
                    "quality_gates": {
                        "gates": {
                            "gate_1": {"total_subtasks": 10, "revisions": 1},
                            "gate_6": {"total_tickets": 10, "revisions": 4, "bounces": 5},
                        }
                    },
                }) + "\n")

            fake_git = method_scorecard.parse_git_numstat_output(
                "COMMIT:1\n30\t0\tapp/src/main/A.kt\n70\t0\tapp/src/test/ATest.kt\n"
            )
            with patch("method_scorecard.collect_git_metrics", return_value=fake_git):
                card = method_scorecard.compute_scorecard(
                    iso_week="2026-KW48",
                    process_version="aaa-v2",
                    baseline_path=baseline_path,
                    process_metrics_path=pm_path,
                    po_minutes_override=45.0,
                    query_jira=False,
                    filter_process_version=True,
                )

            self.assertEqual(card["comparisons"]["production_code_share_percent"]["delta"], 11.4)
            self.assertEqual(card["comparisons"]["stage_6_ticket_bounce_rate_percent"]["current"], 40.0)
            self.assertEqual(card["comparisons"]["stage_6_ticket_bounce_rate_percent"]["delta"], 5.5)
            self.assertEqual(card["human_po_minutes"], 45.0)

            md = method_scorecard.render_scorecard_markdown(card)
            self.assertIn("[method-scorecard:2026-KW48:aaa-v2]", md)
            self.assertIn("+11.4 pp", md)
            self.assertIn("45.0 min", md)

            # Upsert twice -> strictly 1 row
            out_jsonl = os.path.join(tmp, "method_scorecard.jsonl")
            method_scorecard.persist_scorecard_jsonl(card, out_jsonl)
            method_scorecard.persist_scorecard_jsonl(card, out_jsonl)
            with open(out_jsonl, "r", encoding="utf-8") as f:
                rows = [json.loads(l) for l in f if l.strip()]
            self.assertEqual(len(rows), 1)

    def test_post_scorecard_to_weekly_retro_idempotent(self):
        card = {
            "iso_week": "2026-KW48",
            "window_start": "2026-11-23",
            "window_end": "2026-11-29",
            "process_version": "aaa-v2",
            "baseline_iso_week": "2026-KW47",
            "tiers": {"tier-a": {"tickets": 1, "story_points": 5.0}},
            "comparisons": {
                k: {"baseline": 0, "current": 0, "delta": 0}
                for k in (
                    "production_code_share_percent",
                    "tests_share_percent",
                    "per_ticket_docs_share_percent",
                    "living_specs_share_percent",
                    "process_tooling_share_percent",
                    "commits_touching_prod_percent",
                    "gates_1_to_5_revision_rate_percent",
                    "stage_6_ticket_bounce_rate_percent",
                    "process_escape_bugs_count",
                    "completed_tickets",
                    "completed_story_points",
                    "mean_wall_minutes_per_ticket",
                    "po_minutes_weekly",
                )
            },
        }
        with patch("jira_util.ensure_weekly_retro_ticket", return_value="ATT-8223"), \
             patch("jira_util._weekly_rollup_already_posted", side_effect=[False, True]), \
             patch("jira_util.add_comment") as mock_comment:
            key1, posted1 = method_scorecard.post_scorecard_to_weekly_retro(card)
            key2, posted2 = method_scorecard.post_scorecard_to_weekly_retro(card)
            self.assertEqual(key1, "ATT-8223")
            self.assertTrue(posted1)
            self.assertEqual(key2, "ATT-8223")
            self.assertFalse(posted2)
            mock_comment.assert_called_once()

    def test_classify_po_acceptance_buckets_distinguishes_clean_refinement_bug_and_ignores_beifang(self):
        accepted = [
            {"key": "ATT-1", "fields": {"issuelinks": []}},
            {"key": "ATT-2", "fields": {"issuelinks": [{"inwardIssue": {"key": "ATT-20", "fields": {"issuetype": {"name": "Verbesserung"}}}}]}},
            {"key": "ATT-3", "fields": {"issuelinks": [{"inwardIssue": {"key": "ATT-30", "fields": {"issuetype": {"name": "Bug"}}}}]}},
            # ATT-4 has an unrelated non-followup link (e.g. Epic/Beifang not in po-review-followup) -> still clean
            {"key": "ATT-4", "fields": {"issuelinks": [{"inwardIssue": {"key": "ATT-40", "fields": {"issuetype": {"name": "Bug"}}}}]}},
        ]
        followup_types = {"ATT-20": "Verbesserung", "ATT-30": "Bug"}
        split = method_scorecard.classify_po_acceptance_buckets(accepted, followup_types)
        self.assertEqual(split, {
            "total_accepted": 4,
            "clean_accepted": 2,
            "accepted_with_refinement": 1,
            "accepted_with_bug_escape": 1,
        })


if __name__ == "__main__":
    unittest.main()
