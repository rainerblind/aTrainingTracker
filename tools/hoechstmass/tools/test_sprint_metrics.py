#!/usr/bin/env python3
"""
tools/test_sprint_metrics.py

Automated unit tests for tools/sprint_metrics.py.
Validates PA 4.1 Quantitative Metrics computation (Gate 1–5 FTPR, Swiss Cheese escape rates,
Human Acceptance Rate) and PA 4.2 Statistical Process Control (mu +/- 2sigma outlier detection,
JSONL time-series persistence, and formatting).
"""

import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from sprint_metrics import (
    calculate_spc_limits,
    format_metrics_markdown,
    identify_gate_number,
    load_historical_metrics,
    parse_final_review_agent_verdicts_from_comments,
    parse_sprint_gate_metrics,
    parse_verdicts_from_comments,
    record_sprint_metrics,
)


class TestSprintMetrics(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="att_spc_test_")
        self.metrics_file = os.path.join(self.test_dir, "test_metrics.jsonl")

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_identify_gate_number(self):
        self.assertEqual(identify_gate_number("[Analysis] Problem Investigation"), 1)
        self.assertEqual(identify_gate_number("[Req & Test Spec] Specification"), 2)
        self.assertEqual(identify_gate_number("[Impl-Plan] Architecture Blueprint"), 3)
        self.assertEqual(identify_gate_number("[Implementation] Code Construction"), 4)
        self.assertEqual(identify_gate_number("[Test] Verification & Walkthrough"), 5)
        self.assertIsNone(identify_gate_number("Regular Task Without Stage Prefix"))

    def test_parse_verdicts_from_comments(self):
        # 1. First-time pass comment
        c1 = [{"author": {"displayName": "Agent 2 (Reviewer)"}, "body": "[ROLE: agent2]\nGate 1 PASS: Verified."}]
        self.assertEqual(parse_verdicts_from_comments(c1), ["PASS"])

        # 2. Revision followed by re-review pass
        c2 = [
            {"author": {"displayName": "Agent 2 (Reviewer)"}, "body": "[ROLE: agent2]\nGate 4 Audit: REVISION required."},
            {"author": {"displayName": "Agent 1 (Implementer)"}, "body": "[ROLE: agent1]\nFixed feedback."},
            {"author": {"displayName": "Agent 2 (Reviewer)"}, "body": "[ROLE: agent2]\nGate 4 Audit PASS (Re-Review): Verified."},
        ]
        self.assertEqual(parse_verdicts_from_comments(c2), ["REVISION", "PASS"])

        # 3. Non-agent2 comments ignored
        c3 = [{"author": {"displayName": "Human Developer"}, "body": "Please review this ticket."}]
        self.assertEqual(parse_verdicts_from_comments(c3), [])

    def test_parse_sprint_gate_metrics(self):
        parent_issues = [
            {
                "key": "ATT-100",
                "fields": {
                    "summary": "Feature 1",
                    "status": {"name": "Final Review (Human)"},
                    "priority": {"name": "Medium"},
                    "customfield_10016": 3.0,
                    "subtasks": [
                        {"fields": {"summary": "[Analysis] F1", "status": {"name": "Erledigt"}}, "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 1 PASS"}]},
                        {"fields": {"summary": "[Req & Test Spec] F1", "status": {"name": "Erledigt"}}, "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 2 PASS"}]},
                        {"fields": {"summary": "[Impl-Plan] F1", "status": {"name": "Erledigt"}}, "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 3 PASS"}]},
                        {"fields": {"summary": "[Implementation] F1", "status": {"name": "Erledigt"}}, "comments": [
                            {"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 4 REVISION"},
                            {"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 4 PASS"}
                        ]},
                        {"fields": {"summary": "[Test] F1", "status": {"name": "Erledigt"}}, "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 5 PASS"}]},
                    ]
                }
            },
            {
                "key": "ATT-101",
                "fields": {
                    "summary": "Feature 2",
                    "status": {"name": "In Bearbeitung"},
                    "priority": {"name": "High"},
                    "customfield_10016": 2.0,
                    "subtasks": [
                        {"fields": {"summary": "[Analysis] F2", "status": {"name": "Erledigt"}}, "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 1 PASS"}]},
                    ]
                }
            }
        ]

        sprint_info = {"id": 999, "name": "Sprint 2026-Test"}
        metrics = parse_sprint_gate_metrics(parent_issues, sprint_info)

        # Throughput assertions
        tp = metrics["throughput"]
        self.assertEqual(tp["total_tickets"], 2)
        self.assertEqual(tp["completed_tickets"], 1)
        self.assertEqual(tp["total_story_points"], 5.0)
        self.assertEqual(tp["completed_story_points"], 3.0)

        # Gate assertions
        qg = metrics["quality_gates"]
        self.assertEqual(qg["total_subtasks_evaluated"], 6)
        # Gate 4 had 1 revision, others passed first time -> 5 out of 6 passed first time
        self.assertAlmostEqual(qg["overall_ftpr_percent"], round((5 / 6) * 100.0, 1))

        # Gate 4 specific
        g4 = qg["gates"]["gate_4"]
        self.assertEqual(g4["total_subtasks"], 1)
        self.assertEqual(g4["passed_first_time"], 0)
        self.assertEqual(g4["revisions"], 1)
        self.assertEqual(g4["ftpr_percent"], 0.0)
        self.assertEqual(g4["escape_rate_p"], 1.0)

        # Swiss Cheese layers
        sc = qg["swiss_cheese"]
        self.assertIn("p1_analysis_escape", sc)
        self.assertIn("theoretical_cumulative_escape_prob", sc)

    def test_calculate_spc_limits_and_outlier_detection(self):
        # Historical baseline: 3 sprints with steady ~95% FTPR
        history = [
            {"quality_gates": {"overall_ftpr_percent": 96.0, "human_acceptance_rate_percent": 100.0}, "throughput": {"completed_tickets": 10, "completed_story_points": 20.0}},
            {"quality_gates": {"overall_ftpr_percent": 94.0, "human_acceptance_rate_percent": 100.0}, "throughput": {"completed_tickets": 11, "completed_story_points": 22.0}},
            {"quality_gates": {"overall_ftpr_percent": 95.0, "human_acceptance_rate_percent": 100.0}, "throughput": {"completed_tickets": 9, "completed_story_points": 18.0}},
        ]

        spc_base = calculate_spc_limits(history)
        self.assertTrue(spc_base["has_history"])
        self.assertEqual(spc_base["sample_size"], 3)
        ftpr_stat = spc_base["limits"]["overall_ftpr"]
        self.assertAlmostEqual(ftpr_stat["mu"], 95.0, places=1)
        self.assertTrue(ftpr_stat["sigma"] > 0)
        self.assertTrue(ftpr_stat["lcl"] < 95.0)

        # 1. Normal current sprint (in statistical control)
        curr_normal = {"quality_gates": {"overall_ftpr_percent": 95.0, "human_acceptance_rate_percent": 100.0}, "throughput": {"completed_tickets": 10, "completed_story_points": 20.0}}
        res_normal = calculate_spc_limits(history, current_metrics=curr_normal)
        self.assertEqual(len(res_normal["warnings"]), 0)

        # 2. Outlier sprint (severe drop in FTPR to 50%) -> triggers Special Cause Variation
        curr_anomaly = {"quality_gates": {"overall_ftpr_percent": 50.0, "human_acceptance_rate_percent": 60.0}, "throughput": {"completed_tickets": 3, "completed_story_points": 6.0}}
        res_anomaly = calculate_spc_limits(history, current_metrics=curr_anomaly)
        self.assertTrue(len(res_anomaly["warnings"]) > 0)
        self.assertTrue(any("PA 4.2 Special Cause Variation" in w for w in res_anomaly["warnings"]))

    def test_record_and_load_sprint_metrics_idempotent(self):
        m1 = {"sprint_id": 101, "sprint_name": "2026-40.1", "quality_gates": {"overall_ftpr_percent": 90.0}}
        m2 = {"sprint_id": 102, "sprint_name": "2026-40.2", "quality_gates": {"overall_ftpr_percent": 92.0}}

        record_sprint_metrics(m1, filepath=self.metrics_file)
        record_sprint_metrics(m2, filepath=self.metrics_file)

        loaded = load_historical_metrics(filepath=self.metrics_file)
        self.assertEqual(len(loaded), 2)
        self.assertEqual(loaded[0]["sprint_name"], "2026-40.1")

        # Upsert m1 with updated data
        m1_updated = {"sprint_id": 101, "sprint_name": "2026-40.1", "quality_gates": {"overall_ftpr_percent": 95.0}}
        record_sprint_metrics(m1_updated, filepath=self.metrics_file)

        reloaded = load_historical_metrics(filepath=self.metrics_file)
        # Length remains 2 (idempotent overwrite)
        self.assertEqual(len(reloaded), 2)
        self.assertEqual(reloaded[0]["quality_gates"]["overall_ftpr_percent"], 95.0)

    def test_format_metrics_markdown(self):
        metrics = {
            "sprint_name": "2026-45.12",
            "throughput": {"total_tickets": 10, "completed_tickets": 9, "completion_rate_percent": 90.0, "completed_story_points": 18.0},
            "quality_gates": {
                "overall_ftpr_percent": 88.5,
                "total_subtasks_evaluated": 25,
                "gates": {
                    f"gate_{i}": {"name": f"Gate {i}", "total_subtasks": 5, "passed_first_time": 4, "revisions": 1, "ftpr_percent": 80.0, "escape_rate_p": 0.2}
                    for i in range(1, 6)
                },
                "swiss_cheese": {
                    "p1_analysis_escape": 0.2, "p2_req_test_spec_escape": 0.2, "p3_impl_plan_escape": 0.2, "p4_implementation_escape": 0.2, "p5_test_verification_escape": 0.2,
                    "theoretical_cumulative_escape_prob": 0.00032
                },
                "human_acceptance_rate_percent": 100.0,
            }
        }

        spc = {
            "has_history": True,
            "sample_size": 5,
            "limits": {"overall_ftpr": {"mu": 90.0, "sigma": 3.0, "lcl": 84.0, "ucl": 96.0}},
            "warnings": [],
        }

        md = format_metrics_markdown(metrics, spc)
        self.assertIn("Quantitative Sprint Process Metrics", md)
        self.assertIn("Gate / Stage", md)
        self.assertIn("Empirical Swiss Cheese Multiplicative Defect Reduction", md)
        self.assertIn("Statistical Process Control", md)

    def test_gate6_final_review_agent_metrics(self):
        # Test parse_final_review_agent_verdicts_from_comments
        parent_comments_bounce_then_pass = [
            {"author": {"displayName": "Agent 2 (Reviewer)"}, "body": "[ROLE: agent2]\nStage 6 Final Review (Agent) BOUNCE (1/3): Defect found."},
            {"author": {"displayName": "Agent 2 (Reviewer)"}, "body": "[ROLE: agent2]\nStage 6 Final Review (Agent) PASS: Clean re-review."},
        ]
        self.assertEqual(
            parse_final_review_agent_verdicts_from_comments(parent_comments_bounce_then_pass),
            ["BOUNCE", "PASS"]
        )

        parent_issues = [
            {
                "key": "ATT-200",
                "fields": {
                    "summary": "Ticket with Stage 6 First-Time Pass",
                    "status": {"name": "Final Review (Human)"},
                    "priority": {"name": "Medium"},
                    "customfield_10016": 2.0,
                    "comment": {
                        "comments": [
                            {"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Stage 6 Final Review (Agent) PASS: Ready."}
                        ]
                    },
                    "subtasks": []
                }
            },
            {
                "key": "ATT-201",
                "fields": {
                    "summary": "Ticket with Stage 6 Bounce then Pass",
                    "status": {"name": "Final Review (Human)"},
                    "priority": {"name": "High"},
                    "customfield_10016": 3.0,
                    "comment": {
                        "comments": parent_comments_bounce_then_pass
                    },
                    "subtasks": []
                }
            }
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 439, "name": "Sprint 2026-46.2"})
        g6 = metrics["quality_gates"]["gates"]["gate_6"]
        self.assertEqual(g6["total_subtasks"], 2)
        self.assertEqual(g6["passed_first_time"], 1)
        self.assertEqual(g6["revisions"], 1)
        self.assertEqual(g6["ftpr_percent"], 50.0)
        self.assertEqual(g6["escape_rate_p"], 0.5)
        self.assertEqual(metrics["quality_gates"]["swiss_cheese"]["p6_final_review_agent_escape"], 0.5)

    def test_parse_sprint_gate_metrics_problem_trends(self):
        """TST-PRO-063.2: Verify problem trends extraction by severity and defect resolution rates."""
        parent_issues = [
            {
                "key": "ATT-301",
                "fields": {
                    "summary": "Critical crash in sensor listener",
                    "status": {"name": "Erledigt"},
                    "priority": {"name": "Highest"},
                    "issuetype": {"id": "10006", "name": "Bug"},
                    "labels": ["severity-critical"],
                    "customfield_10016": 1.0,
                    "subtasks": [],
                },
            },
            {
                "key": "ATT-302",
                "fields": {
                    "summary": "Major calculation error in cadence",
                    "status": {"name": "Erledigt"},
                    "priority": {"name": "High"},
                    "issuetype": {"id": "10006", "name": "Fehler"},
                    "labels": ["severity-major"],
                    "customfield_10016": 2.0,
                    "subtasks": [],
                },
            },
            {
                "key": "ATT-303",
                "fields": {
                    "summary": "Minor label overlap on dashboard",
                    "status": {"name": "In Bearbeitung"},
                    "priority": {"name": "Medium"},
                    "issuetype": {"id": "10006", "name": "Bug"},
                    "labels": ["severity-minor"],
                    "customfield_10016": 1.0,
                    "subtasks": [],
                },
            },
            {
                "key": "ATT-304",
                "fields": {
                    "summary": "Feature ticket: add heart rate export",
                    "status": {"name": "Erledigt"},
                    "priority": {"name": "Medium"},
                    "issuetype": {"id": "10007", "name": "Feature"},
                    "customfield_10016": 3.0,
                    "subtasks": [],
                },
            },
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 440, "name": "Sprint 2026-46.3"})
        self.assertIn("problem_trends", metrics)
        pt = metrics["problem_trends"]
        self.assertEqual(pt["total_bugs"], 3)
        self.assertEqual(pt["closed_bugs"], 2)
        self.assertEqual(pt["open_bugs"], 1)
        self.assertEqual(pt["resolution_rate_percent"], 66.7)
        self.assertEqual(pt["by_severity"]["Critical"], 1)
        self.assertEqual(pt["by_severity"]["Major"], 1)
        self.assertEqual(pt["by_severity"]["Minor"], 1)
        self.assertEqual(pt["by_severity"]["Cosmetic"], 0)
        self.assertEqual(pt["closed_by_severity"]["Critical"], 1)
        self.assertEqual(pt["closed_by_severity"]["Major"], 1)
        self.assertEqual(pt["open_by_severity"]["Minor"], 1)

    def test_format_metrics_markdown_includes_problem_trends(self):
        """TST-PRO-063.3: Verify format_metrics_markdown renders problem trends section."""
        metrics = {
            "sprint_name": "2026-46.3",
            "throughput": {"completed_tickets": 2, "total_tickets": 3, "completion_rate_percent": 66.7, "completed_story_points": 5.0},
            "quality_gates": {
                "overall_ftpr_percent": 100.0,
                "total_subtasks_evaluated": 5,
                "gates": {},
                "swiss_cheese": {"theoretical_cumulative_escape_prob": 0.001},
                "human_acceptance_rate_percent": 100.0,
            },
            "problem_trends": {
                "total_bugs": 2,
                "open_bugs": 0,
                "closed_bugs": 2,
                "resolution_rate_percent": 100.0,
                "by_severity": {"Critical": 1, "Major": 1, "Minor": 0, "Cosmetic": 0},
                "open_by_severity": {"Critical": 0, "Major": 0, "Minor": 0, "Cosmetic": 0},
                "closed_by_severity": {"Critical": 1, "Major": 1, "Minor": 0, "Cosmetic": 0},
            },
        }
        md = format_metrics_markdown(metrics)
        self.assertIn("Problem Trends & Defect Severity", md)
        self.assertIn("*Total Bugs*: 2", md)
        self.assertIn("Critical", md)
        self.assertIn("Major", md)
        self.assertIn("*Resolution Rate*: *100.0%*", md)


if __name__ == "__main__":
    unittest.main()
