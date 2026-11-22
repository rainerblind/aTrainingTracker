#!/usr/bin/env python3
"""
tools/test_sprint_metrics.py

Automated unit tests for tools/sprint_metrics.py.
Validates PA 4.1 Quantitative Metrics computation (Gate 1–5 FTPR, Swiss Cheese escape rates,
Human Acceptance Rate) and PA 4.2 Statistical Process Control (mu +/- 2sigma outlier detection,
JSONL time-series persistence, and formatting).
"""

import datetime
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from sprint_metrics import (
    calculate_spc_limits,
    classify_comment_actor_role,
    extract_text_from_comment_body,
    format_metrics_markdown,
    identify_gate_number,
    load_historical_metrics,
    parse_final_review_agent_verdicts_from_comments,
    parse_sprint_gate_metrics,
    parse_subtask_verdict_from_text,
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

    def test_collect_sprint_metrics_extracts_labels_and_description(self):
        """TST-PRO-092.1: Priority to labels and description for bug severity classification."""
        parent_issues = [
            {
                "key": "ATT-401",
                "fields": {
                    "summary": "Crash on sensor connect",
                    "status": {"name": "Erledigt"},
                    "priority": {"name": "Lowest"},  # would fallback to Cosmetic without label
                    "issuetype": {"id": "10006", "name": "Bug"},
                    "labels": ["severity-critical"],
                    "customfield_10016": 1.0,
                    "subtasks": [],
                },
            },
            {
                "key": "ATT-402",
                "fields": {
                    "summary": "ANR in workout summary",
                    "status": {"name": "Erledigt"},
                    "priority": {"name": "Medium"},  # would fallback to Minor without description
                    "issuetype": {"id": "10006", "name": "Bug"},
                    "description": "App freezes on save.\n\n**Severity**: Major",
                    "labels": [],
                    "customfield_10016": 2.0,
                    "subtasks": [],
                },
            },
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 441, "name": "Sprint 2026-46.4"})
        pt = metrics["problem_trends"]
        self.assertEqual(pt["by_severity"]["Critical"], 1)
        self.assertEqual(pt["by_severity"]["Major"], 1)
        self.assertEqual(pt["by_severity"]["Minor"], 0)
        self.assertEqual(pt["by_severity"]["Cosmetic"], 0)

    def test_parse_verdicts_from_adf_comments(self):
        """TST-PRO-092.2: Parse ADF dict comment structures recursively without exception."""
        adf_rev = {
            "type": "doc",
            "version": 1,
            "content": [
                {
                    "type": "paragraph",
                    "content": [
                        {"type": "text", "text": "[ROLE: agent2] Gate 4 Audit: REVISION required."}
                    ]
                }
            ]
        }
        adf_pass = {
            "type": "doc",
            "version": 1,
            "content": [
                {
                    "type": "paragraph",
                    "content": [
                        {"type": "text", "text": "[ROLE: agent2] Gate 4 Audit PASS (Re-Review): Verified."}
                    ]
                }
            ]
        }
        adf_fr_bounce = {
            "type": "doc",
            "version": 1,
            "content": [
                {
                    "type": "paragraph",
                    "content": [
                        {"type": "text", "text": "[ROLE: agent2] Stage 6 Final Review (Agent) BOUNCE (1/3): Architecture violation."}
                    ]
                }
            ]
        }

        # Subtask comments
        comments = [
            {"author": {"displayName": "Agent 2"}, "body": adf_rev},
            {"author": {"displayName": "Agent 2"}, "body": adf_pass},
        ]
        verdicts = parse_verdicts_from_comments(comments)
        self.assertEqual(verdicts, ["REVISION", "PASS"])

        # Final review comments
        fr_comments = [
            {"author": {"displayName": "Agent 2"}, "body": adf_fr_bounce},
        ]
        fr_verdicts = parse_final_review_agent_verdicts_from_comments(fr_comments)
        self.assertEqual(fr_verdicts, ["BOUNCE"])

        # Direct text extractor test
        extracted = extract_text_from_comment_body(adf_rev)
        self.assertIn("[ROLE: agent2]", extracted)
        self.assertIn("REVISION", extracted)

    def test_gate_subtasks_without_verdicts_marked_unverified(self):
        """TST-PRO-092.3: Unverified subtasks in Erledigt are segregated and do not increment passed_first_time."""
        parent_issues = [
            {
                "key": "ATT-501",
                "fields": {
                    "summary": "Feature without reviewer comment",
                    "status": {"name": "Erledigt"},
                    "priority": {"name": "Medium"},
                    "customfield_10016": 3.0,
                    "subtasks": [
                        {"fields": {"summary": "[Analysis] F501", "status": {"name": "Erledigt"}}, "comments": []},
                        {"fields": {"summary": "[Req & Test Spec] F501", "status": {"name": "Erledigt"}}, "comments": []},
                    ],
                },
            }
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 442, "name": "Sprint 2026-46.5"})
        qg = metrics["quality_gates"]
        self.assertEqual(qg["total_unverified_subtasks"], 2)
        self.assertEqual(qg["total_subtasks_evaluated"], 0)
        self.assertEqual(qg["gates"]["gate_1"]["unverified"], 1)
        self.assertEqual(qg["gates"]["gate_1"]["passed_first_time"], 0)
        self.assertEqual(qg["gates"]["gate_2"]["unverified"], 1)
        self.assertEqual(qg["gates"]["gate_2"]["passed_first_time"], 0)

    def test_downstream_stage6_bounces_attributed_to_escape_rate(self):
        """TST-PRO-092.4: Stage 6 Cold Review defect bounces are attributed to upstream gate escape rates."""
        parent_issues = [
            {
                "key": "ATT-601",
                "fields": {
                    "summary": "Feature ticket with Stage 6 defect bounce",
                    "status": {"name": "Final Review (Human)"},
                    "priority": {"name": "High"},
                    "customfield_10016": 5.0,
                    "comment": {
                        "comments": [
                            {"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Stage 6 Final Review (Agent) BOUNCE (1/3): Code flaw."}
                            for _ in range(5)
                        ]
                    },
                    "subtasks": [
                        {"fields": {"summary": "[Analysis] F601", "status": {"name": "Erledigt"}}, "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 1 PASS"}]},
                        {"fields": {"summary": "[Req & Test Spec] F601", "status": {"name": "Erledigt"}}, "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 2 PASS"}]},
                        {"fields": {"summary": "[Impl-Plan] F601", "status": {"name": "Erledigt"}}, "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 3 PASS"}]},
                        {"fields": {"summary": "[Implementation] F601", "status": {"name": "Erledigt"}}, "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 4 PASS"}]},
                        {"fields": {"summary": "[Test] F601", "status": {"name": "Erledigt"}}, "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Gate 5 PASS"}]},
                    ],
                },
            }
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 443, "name": "Sprint 2026-46.6"})
        sc = metrics["quality_gates"]["swiss_cheese"]
        # Analysis and Req/Spec are unaffected by Stage 6 code defect
        self.assertEqual(sc["p1_analysis_escape"], 0.0)
        self.assertEqual(sc["p2_req_test_spec_escape"], 0.0)
        # Upstream gates (Impl-Plan, Implementation, Test) must have escape rates > 0 due to downstream escape attribution
        self.assertTrue(sc["p3_impl_plan_escape"] > 0.0, f"Expected p3 > 0, got {sc['p3_impl_plan_escape']}")
        self.assertTrue(sc["p4_implementation_escape"] > 0.0, f"Expected p4 > 0, got {sc['p4_implementation_escape']}")
        self.assertTrue(sc["p5_test_verification_escape"] > 0.0, f"Expected p5 > 0, got {sc['p5_test_verification_escape']}")

    def test_plausibility_warning_emitted_on_100_percent_pass(self):
        """TST-PRO-092.5: Plausibility warning emitted when FTPR is 100% across >= 20 gate subtasks."""
        subtasks = []
        for i in range(20):
            g = (i % 5) + 1
            stage_name = ["Analysis", "Req & Test Spec", "Impl-Plan", "Implementation", "Test"][g - 1]
            subtasks.append({
                "fields": {"summary": f"[{stage_name}] Subtask {i}", "status": {"name": "Erledigt"}},
                "comments": [{"author": {"displayName": "Agent 2"}, "body": f"[ROLE: agent2] Gate {g} PASS: Clean."}],
            })

        parent_issues = [
            {
                "key": "ATT-701",
                "fields": {
                    "summary": "Large sprint with 100% gate pass rate",
                    "status": {"name": "Erledigt"},
                    "priority": {"name": "Medium"},
                    "customfield_10016": 20.0,
                    "subtasks": subtasks,
                },
            }
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 444, "name": "Sprint 2026-47.1"})
        warnings = metrics.get("plausibility_warnings", [])
        self.assertTrue(len(warnings) > 0, "Expected plausibility warning for 100% FTPR across 20 subtasks")
        self.assertTrue(any("PA 4.1 Plausibility Warning" in w for w in warnings))

        md = format_metrics_markdown(metrics)
        self.assertIn("PA 4.1 Plausibility Warning", md)

    def test_bug_age_resolution_states_and_multi_sprint_trend(self):
        """TST-PRO-121.1: Verify open bug age, awaiting-human/non-fix resolutions, and multi-sprint trend chart."""
        ref_now = datetime.datetime(2026, 11, 17, 18, 0, 0, tzinfo=datetime.timezone.utc)
        parent_issues = [
            {
                "key": "ATT-901",
                "fields": {
                    "summary": "Critical open bug aged 10 days",
                    "status": {"name": "Analysis"},
                    "priority": {"name": "Highest"},
                    "issuetype": {"id": "10006", "name": "Bug"},
                    "labels": ["severity-critical"],
                    "created": "2026-11-07T18:00:00.000+0000",
                    "customfield_10016": 2.0,
                    "subtasks": [],
                },
            },
            {
                "key": "ATT-902",
                "fields": {
                    "summary": "Major bug fixed awaiting human sign-off",
                    "status": {"name": "Final Review (Human)"},
                    "priority": {"name": "High"},
                    "issuetype": {"id": "10006", "name": "Bug"},
                    "labels": ["severity-major", "resolution-fixed"],
                    "created": "2026-11-15T18:00:00.000+0000",
                    "customfield_10016": 1.0,
                    "subtasks": [],
                },
            },
            {
                "key": "ATT-903",
                "fields": {
                    "summary": "Minor duplicate bug closed via label",
                    "status": {"name": "Final Review (Human)"},
                    "priority": {"name": "Medium"},
                    "issuetype": {"id": "10006", "name": "Bug"},
                    "labels": ["severity-minor", "resolution-duplicate"],
                    "created": "2026-11-16T18:00:00.000+0000",
                    "customfield_10016": 1.0,
                    "subtasks": [],
                },
            },
        ]
        history = [
            {
                "sprint_id": 443,
                "sprint_name": "2026-46.6",
                "problem_trends": {
                    "total_bugs": 17,
                    "open_bugs": 17,
                    "closed_bugs": 0,
                    "resolution_rate_percent": 0.0,
                    "by_severity": {"Critical": 3, "Major": 8, "Minor": 6, "Cosmetic": 0},
                },
            }
        ]
        metrics = parse_sprint_gate_metrics(
            parent_issues,
            {"id": 457, "name": "2026-11-17_02_Spät"},
            history=history,
            now=ref_now,
        )
        pt = metrics["problem_trends"]
        self.assertEqual(pt["total_bugs"], 3)
        self.assertEqual(pt["open_bugs"], 1)
        self.assertEqual(pt["fixed_awaiting_human"], 1)
        self.assertEqual(pt["closed_bugs"], 1)
        self.assertEqual(pt["resolution_rate_percent"], 66.7)
        self.assertEqual(pt["by_resolution"]["Duplicate"], 1)
        self.assertEqual(pt["by_resolution"]["Fixed"], 1)
        age = pt["open_bug_age_days"]
        self.assertEqual(age["avg"], 10.0)
        self.assertEqual(age["max"], 10.0)
        self.assertEqual(age["gt_7d"], 1)
        self.assertEqual(age["by_severity"]["Critical"]["gt_7d"], 1)
        self.assertEqual(len(pt["bug_trend_history"]), 2)

        md = format_metrics_markdown(metrics)
        self.assertIn("Open Bug Age (days)", md)
        self.assertIn("Awaiting Human", md)
        self.assertIn("Bug Trend vs Previous Sprints", md)
        self.assertIn("2026-46.6", md)
        self.assertIn("2026-11-17_02_Spät", md)

    def test_spc_percentage_bounds_and_process_version_reset(self):
        """TST-PRO-122.1: Percentage SPC limits stay in [0, 100] and reset on process_version change."""
        # High variance history that previously yielded UCL > 100% (e.g. 116.58%)
        legacy_history = [
            {
                "process_version": "1.0",
                "quality_gates": {"overall_ftpr_percent": 68.0, "human_acceptance_rate_percent": 40.0, "total_subtasks_evaluated": 50},
                "throughput": {"total_tickets": 20, "completed_tickets": 8, "completed_story_points": 16.0},
            },
            {
                "process_version": "1.0",
                "quality_gates": {"overall_ftpr_percent": 100.0, "human_acceptance_rate_percent": 100.0, "total_subtasks_evaluated": 50},
                "throughput": {"total_tickets": 20, "completed_tickets": 20, "completed_story_points": 40.0},
            },
            {
                "process_version": "1.0",
                "quality_gates": {"overall_ftpr_percent": 100.0, "human_acceptance_rate_percent": 95.0, "total_subtasks_evaluated": 50},
                "throughput": {"total_tickets": 20, "completed_tickets": 19, "completed_story_points": 38.0},
            },
        ]
        spc_v1 = calculate_spc_limits(legacy_history)
        self.assertTrue(spc_v1["has_history"])
        for metric_key in ("overall_ftpr", "human_acceptance"):
            lim = spc_v1["limits"][metric_key]
            self.assertGreaterEqual(lim["lcl"], 0.0)
            self.assertLessEqual(lim["ucl"], 100.0)
            self.assertEqual(lim["method"], "p_chart")

        # Also test clamped_normal fallback when denominators are missing
        no_denom_hist = [
            {"quality_gates": {"overall_ftpr_percent": 60.0, "human_acceptance_rate_percent": 40.0}, "throughput": {"completed_tickets": 5, "completed_story_points": 10.0}},
            {"quality_gates": {"overall_ftpr_percent": 100.0, "human_acceptance_rate_percent": 100.0}, "throughput": {"completed_tickets": 15, "completed_story_points": 30.0}},
        ]
        spc_nod = calculate_spc_limits(no_denom_hist)
        self.assertLessEqual(spc_nod["limits"]["overall_ftpr"]["ucl"], 100.0)
        self.assertLessEqual(spc_nod["limits"]["human_acceptance"]["ucl"], 100.0)
        self.assertEqual(spc_nod["limits"]["overall_ftpr"]["method"], "clamped_normal")

        # Baseline reset when current_metrics introduces a new process_version
        curr_v2 = {
            "process_version": "2.0",
            "quality_gates": {"overall_ftpr_percent": 80.0, "human_acceptance_rate_percent": 50.0, "total_subtasks_evaluated": 25},
            "throughput": {"total_tickets": 10, "completed_tickets": 5, "completed_story_points": 10.0},
        }
        spc_reset = calculate_spc_limits(legacy_history, current_metrics=curr_v2)
        self.assertFalse(spc_reset["has_history"])
        self.assertTrue(spc_reset.get("baseline_reset"))
        self.assertEqual(spc_reset["sample_size"], 0)
        self.assertEqual(spc_reset["limits"], {})

    def test_human_acceptance_rate_separated_from_completion_rate(self):
        """TST-PRO-122.2: human_acceptance_rate_percent counts terminal Erledigt/Done/Closed only."""
        parent_issues = [
            {"key": "ATT-1", "fields": {"summary": "Accepted", "status": {"name": "Erledigt"}, "priority": {"name": "Medium"}, "customfield_10016": 2.0, "subtasks": []}},
            {"key": "ATT-2", "fields": {"summary": "Awaiting Human", "status": {"name": "Final Review (Human)"}, "priority": {"name": "Medium"}, "customfield_10016": 2.0, "subtasks": []}},
            {"key": "ATT-3", "fields": {"summary": "Awaiting Agent", "status": {"name": "Final Review (Agent)"}, "priority": {"name": "Medium"}, "customfield_10016": 2.0, "subtasks": []}},
            {"key": "ATT-4", "fields": {"summary": "In Work", "status": {"name": "In Bearbeitung"}, "priority": {"name": "Medium"}, "customfield_10016": 2.0, "subtasks": []}},
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 458, "name": "Sprint 2026-11-17_03_Nacht"})
        self.assertEqual(metrics["throughput"]["completed_tickets"], 3)
        self.assertEqual(metrics["throughput"]["completion_rate_percent"], 75.0)
        self.assertEqual(metrics["quality_gates"]["human_accepted_tickets"], 1)
        self.assertEqual(metrics["quality_gates"]["human_acceptance_rate_percent"], 25.0)
        self.assertEqual(metrics["process_version"], "aaa-v2")

    def test_zero_revision_gates_no_synthetic_002_floor_and_unmasked_plausibility(self):
        """TST-PRO-122.3 & TST-PRO-122.4: No p=0.02 floor and Gate 6 bounces never mask Gate 1-5 plausibility."""
        subtasks = []
        for i in range(20):
            g = (i % 5) + 1
            stage_name = ["Analysis", "Req & Test Spec", "Impl-Plan", "Implementation", "Test"][g - 1]
            subtasks.append({
                "fields": {"summary": f"[{stage_name}] S{i}", "status": {"name": "Erledigt"}},
                "comments": [{"author": {"displayName": "Agent 2"}, "body": f"[ROLE: agent2] Gate {g} PASS"}],
            })
        parent_issues = [
            {
                "key": "ATT-801",
                "fields": {
                    "summary": "Ticket with 100% Gate 1-5 FTPR but Gate 6 bounce",
                    "status": {"name": "Final Review (Human)"},
                    "priority": {"name": "Medium"},
                    "customfield_10016": 3.0,
                    "comment": {"comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Stage 6 Final Review (Agent) BOUNCE (1/3): Missed bug."}]},
                    "subtasks": subtasks,
                },
            }
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 459, "name": "Sprint 2026-Test-Blindspot"})
        sc = metrics["quality_gates"]["swiss_cheese"]
        self.assertEqual(sc["p1_analysis_escape"], 0.0)
        self.assertEqual(sc["p2_req_test_spec_escape"], 0.0)
        self.assertEqual(sc["theoretical_cumulative_escape_prob"], 0.0)
        warnings = metrics.get("plausibility_warnings", [])
        self.assertTrue(any("PA 4.1 Plausibility Warning" in w for w in warnings), f"Expected unmasked warning, got {warnings}")

    def test_static_analysis_baseline_metrics_and_markdown(self):
        """TST-PRO-123.2: Verify sprint_metrics captures Detekt/Lint baseline counts, deltas, and markdown."""
        repo_root = Path(self.test_dir) / "repo"
        (repo_root / "app").mkdir(parents=True)
        (repo_root / "app" / "detekt-baseline.xml").write_text("<ID>a</ID>\n<ID>b</ID>\n", encoding="utf-8")
        (repo_root / "app" / "lint-baseline.xml").write_text('<issue id="x"/>\n', encoding="utf-8")
        history = [{"static_analysis": {"detekt_baseline_count": 5, "lint_baseline_count": 3}}]
        metrics = parse_sprint_gate_metrics([], {"id": 460, "name": "Sprint 2026-SA"}, history=history, repo_root=repo_root)
        sa = metrics.get("static_analysis")
        self.assertIsNotNone(sa)
        self.assertEqual(sa["detekt_baseline_count"], 2)
        self.assertEqual(sa["lint_baseline_count"], 1)
        self.assertEqual(sa["detekt_delta"], -3)
        self.assertEqual(sa["lint_delta"], -2)
        md = format_metrics_markdown(metrics, history=history)
        self.assertIn("Static Analysis Baseline Ratchet", md)
        self.assertIn("-3", md)
        self.assertIn("-2", md)

    def test_extended_verdict_synonyms_adf_and_role_filtering(self):
        """TST-PRO-128.1: Parse APPROVED/FREIGABE/REWORK/FAIL in plain text & ADF while ignoring non-reviewer authors."""
        adf_rework = {
            "type": "doc",
            "version": 1,
            "content": [{"type": "paragraph", "content": [{"type": "text", "text": "[ROLE: agent2] REWORK needed."}]}],
        }
        adf_freigabe = {
            "type": "doc",
            "version": 1,
            "content": [{"type": "paragraph", "content": [{"type": "text", "text": "[ROLE: agent2] Freigabe erteilt."}]}],
        }
        comments = [
            {"author": {"displayName": "Some Dev"}, "body": "Approved by peer, please pass."},
            {"author": {"displayName": "Agent 2"}, "body": "Approved: Gate 1 clean."},
            {"author": {"displayName": "Agent 2"}, "body": adf_rework},
            {"author": {"displayName": "Agent 2"}, "body": "Gate FAIL due to missing test."},
            {"author": {"displayName": "Agent 2"}, "body": adf_freigabe},
        ]
        self.assertEqual(parse_verdicts_from_comments(comments), ["PASS", "REVISION", "REVISION", "PASS"])

    def test_end_to_end_ftpr_additive_preserves_gates_1_to_5_overall_ftpr(self):
        """TST-PRO-128.2: Additive end_to_end_ftpr_percent (Gates 1-6) preserves Gate 1-5 overall_ftpr_percent and plausibility warnings."""
        subtasks = []
        for i in range(20):
            g = (i % 5) + 1
            stage_name = ["Analysis", "Req & Test Spec", "Impl-Plan", "Implementation", "Test"][g - 1]
            subtasks.append({
                "fields": {"summary": f"[{stage_name}] Sub {i}", "status": {"name": "Erledigt"}},
                "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] APPROVED"}],
            })
        parent_issues = [
            {
                "key": "ATT-950",
                "fields": {
                    "summary": "Gate 1-5 100% but Gate 6 bounced",
                    "status": {"name": "Final Review (Human)"},
                    "priority": {"name": "Medium"},
                    "customfield_10016": 2.0,
                    "comment": {"comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] Stage 6 Final Review (Agent) BOUNCE (1/3): Miss."}]},
                    "subtasks": subtasks,
                },
            }
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 470, "name": "Sprint 2026-E2E"})
        qg = metrics["quality_gates"]
        self.assertEqual(qg["overall_ftpr_percent"], 100.0)
        self.assertEqual(qg["end_to_end_ftpr_percent"], round((20 / 21) * 100.0, 1))
        self.assertTrue(any("PA 4.1 Plausibility Warning" in w for w in metrics["plausibility_warnings"]))
        md = format_metrics_markdown(metrics)
        self.assertIn("End-to-End Gate FTPR (Gates 1–6)", md)

    def test_multi_signal_risk_distribution_classification(self):
        """TST-PRO-128.3: Derive risk_distribution from severity-* labels, summary tags, and Stage 1 Risk Rating before priority fallback."""
        repo_root = Path(self.test_dir) / "risk_repo"
        ana_dir = repo_root / "docs" / "engineering" / "analysis"
        ana_dir.mkdir(parents=True)
        (ana_dir / "ATT-963_analysis.md").write_text("**Risk Rating**: **Low** (1 SP)\n", encoding="utf-8")

        parent_issues = [
            {"key": "ATT-961", "fields": {"summary": "Bug A", "status": {"name": "Erledigt"}, "priority": {"name": "Medium"}, "labels": ["severity-critical"], "subtasks": []}},
            {"key": "ATT-962", "fields": {"summary": "[High] Feature B", "status": {"name": "Erledigt"}, "priority": {"name": "Medium"}, "labels": [], "subtasks": []}},
            {"key": "ATT-963", "fields": {"summary": "Doc tweak C", "status": {"name": "Erledigt"}, "priority": {"name": "Medium"}, "labels": [], "subtasks": []}},
            {"key": "ATT-964", "fields": {"summary": "Standard D", "status": {"name": "Erledigt"}, "priority": {"name": "Medium"}, "labels": [], "subtasks": []}},
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 471, "name": "Sprint 2026-Risk"}, repo_root=repo_root)
        self.assertEqual(metrics["risk_distribution"], {"Low": 1, "Medium": 1, "High": 1, "Highest": 1})

    def test_extended_german_english_bug_and_terminal_statuses(self):
        """TST-PRO-128.4: Recognize abgelehnt, rejected, geschlossen, resolved as terminal closed statuses."""
        parent_issues = [
            {"key": "ATT-971", "fields": {"summary": "Bug 1", "status": {"name": "Abgelehnt"}, "priority": {"name": "High"}, "issuetype": {"id": "10006", "name": "Bug"}, "labels": ["severity-major"], "subtasks": []}},
            {"key": "ATT-972", "fields": {"summary": "Bug 2", "status": {"name": "Rejected"}, "priority": {"name": "Medium"}, "issuetype": {"id": "10006", "name": "Bug"}, "labels": ["severity-minor"], "subtasks": []}},
            {"key": "ATT-973", "fields": {"summary": "Bug 3", "status": {"name": "Geschlossen"}, "priority": {"name": "Low"}, "issuetype": {"id": "10006", "name": "Bug"}, "labels": ["severity-cosmetic"], "subtasks": []}},
            {"key": "ATT-974", "fields": {"summary": "Bug 4", "status": {"name": "Resolved"}, "priority": {"name": "Medium"}, "issuetype": {"id": "10006", "name": "Bug"}, "labels": ["severity-minor"], "subtasks": []}},
        ]
        metrics = parse_sprint_gate_metrics(parent_issues, {"id": 472, "name": "Sprint 2026-Status"})
        pt = metrics["problem_trends"]
        self.assertEqual(pt["total_bugs"], 4)
        self.assertEqual(pt["closed_bugs"], 4)
        self.assertEqual(pt["open_bugs"], 0)
        self.assertEqual(pt["resolution_rate_percent"], 100.0)
        self.assertEqual(metrics["quality_gates"]["human_accepted_tickets"], 4)

    def test_verdict_negation_rubric_precedence_role_precedence_and_backtick_risk(self):
        """TST-PRO-128.1 / TST-PRO-128.3 / TST-PRO-128.4: Negated PASS, ATT-8856 rubric PASS, agent1 role precedence, TERMINAL_CLOSED_STATUSES unverified, and backticked Risk Rating."""
        self.assertEqual(parse_subtask_verdict_from_text("not approved"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("non-PASS"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("non-approved"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("did not pass"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("failed to pass"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("keine freigabe"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("nicht freigegeben"), "REVISION")
        att8856_comment = (
            "Gate 4 Independent Code Review PASS (ATT-8856, diff d518a72c..c588c61c):\n"
            "- [x] Extended Verdict Parsing (PASS/APPROVED/FREIGABE vs REVISION/REWORK/FAIL): Verified."
        )
        self.assertEqual(parse_subtask_verdict_from_text(att8856_comment), "PASS")

        quoted_agent1 = {
            "author": {"displayName": "Agent 1 (Implementer)"},
            "body": "[Automated comment by AI Agent 1 (Implementer)]\n\nAddressing [ROLE: agent2] feedback: GATE_VERDICT: PASS",
        }
        self.assertEqual(classify_comment_actor_role(quoted_agent1), "agent1")
        self.assertEqual(parse_verdicts_from_comments([quoted_agent1]), [])

        repo_root = Path(self.test_dir) / "backtick_risk_repo"
        ana_dir = repo_root / "docs" / "engineering" / "analysis"
        ana_dir.mkdir(parents=True)
        (ana_dir / "ATT-985_analysis.md").write_text("* **Risk Rating**: `LOW` (1 SP)\n", encoding="utf-8")
        issues = [
            {
                "key": "ATT-985",
                "fields": {
                    "summary": "Backtick risk and abgelehnt unverified subtask",
                    "status": {"name": "Erledigt"},
                    "priority": {"name": "Medium"},
                    "subtasks": [
                        {"fields": {"summary": "[Analysis] Zero comment", "status": {"name": "Abgelehnt"}}, "comments": []}
                    ],
                },
            }
        ]
        m = parse_sprint_gate_metrics(issues, {"id": 473, "name": "Sprint 2026-Bounce1"}, repo_root=repo_root)
        self.assertEqual(m["risk_distribution"], {"Low": 1, "Medium": 0, "High": 0, "Highest": 0})
        self.assertEqual(m["quality_gates"]["gates"]["gate_1"]["unverified"], 1)

    def test_r2_lead_line_negation_markdown_wrappers_and_multiline_verdict(self):
        """TST-PRO-128.1: R2 lead-line negation guard, markdown wrappers, multiline header, and body negation after explicit lead approval."""
        self.assertEqual(parse_subtask_verdict_from_text("Gate 4 not approved"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("[Automated comment by AI Agent 2 (Auditor)]\n\nGate 4 - Not approved"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("Gate 4 is not approved"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("Gate 4 will not pass"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("Gate 4 (Impl) - Rejected / Not Approved"), "REVISION")
        self.assertEqual(
            parse_subtask_verdict_from_text(
                "[Automated comment by AI Agent 2 (Auditor)]\n\nApproved: fixed issue where tests did not pass previously."
            ),
            "PASS",
        )
        self.assertEqual(parse_subtask_verdict_from_text("Gate 4: **PASS**"), "PASS")
        self.assertEqual(parse_subtask_verdict_from_text("**Verdict**: **PASS**"), "PASS")
        self.assertEqual(
            parse_subtask_verdict_from_text("Gate 4 Audit (ATT-8856):\nPASS\nDetails: checked PASS vs REVISION/REWORK/FAIL."),
            "PASS",
        )
        self.assertEqual(parse_subtask_verdict_from_text("Tests failed, did not pass checks."), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("GATE_VERDICT: PASS (fixed prior REVISION)"), "PASS")
        self.assertEqual(
            parse_subtask_verdict_from_text("Review note\nGATE_VERDICT: PASS\nChecked REVISION/REWORK/FAIL"),
            "PASS",
        )
        self.assertEqual(parse_subtask_verdict_from_text("non_PASS"), "REVISION")
        self.assertEqual(parse_subtask_verdict_from_text("Gate 4: __PASS__"), "PASS")
        self.assertEqual(parse_subtask_verdict_from_text("_APPROVED_"), "PASS")

    def test_gate3_implementation_plan_alias_and_zero_subtask_na_ftpr_and_backfill(self):
        """ATT-9499 / Findings M14 & M15: Match [Implementation Plan] at Gate 3, report None/N/A for 0/0 FTPR, and backfill human acceptance."""
        from sprint_metrics import refresh_human_acceptance_rates

        # 1. Both [Impl-Plan] and [Implementation Plan] (plus bounce suffixes) map to Gate 3
        self.assertEqual(identify_gate_number("[Impl-Plan] Design sync"), 3)
        self.assertEqual(identify_gate_number("[Implementation Plan] Design sync"), 3)
        self.assertEqual(identify_gate_number("[Implementation Plan - Bounce 2] Design sync"), 3)

        # 2. Gate with 0 evaluated subtasks reports None (rendered N/A), never fabricated 100.0%
        issues = [
            {
                "key": "ATT-9901",
                "fields": {
                    "summary": "Ticket with Implementation Plan subtask only",
                    "status": {"name": "Final Review (Human)"},
                    "priority": {"name": "Medium"},
                    "subtasks": [
                        {
                            "fields": {"summary": "[Implementation Plan] Atomic steps", "status": {"name": "Erledigt"}},
                            "comments": [{"author": {"displayName": "Agent 2"}, "body": "[ROLE: agent2] GATE_VERDICT: PASS"}],
                        }
                    ],
                },
            }
        ]
        m = parse_sprint_gate_metrics(issues, {"id": 464, "name": "2026-47.4"})
        g3 = m["quality_gates"]["gates"]["gate_3"]
        g1 = m["quality_gates"]["gates"]["gate_1"]
        self.assertEqual(g3["total_subtasks"], 1)
        self.assertEqual(g3["ftpr_percent"], 100.0)
        self.assertEqual(g1["total_subtasks"], 0)
        self.assertIsNone(g1["ftpr_percent"])
        md = format_metrics_markdown(m)
        self.assertIn("| Analysis (Gate 1) | 0 | 0 | 0 | *N/A* |", md)

        # 3. Sprint with zero evaluated subtasks reports overall_ftpr_percent = None (N/A)
        m_empty = parse_sprint_gate_metrics(
            [{"key": "ATT-9902", "fields": {"summary": "Empty", "status": {"name": "Zu erledigen"}, "subtasks": []}}],
            {"id": 465, "name": "2026-47.5"},
        )
        self.assertIsNone(m_empty["quality_gates"]["overall_ftpr_percent"])
        self.assertIn("*Overall Gate FTPR*: *N/A*", format_metrics_markdown(m_empty))

        # 4. Backfill/refresh updates flatlined 0.0% human_acceptance_rate_percent from terminal Jira statuses
        record_sprint_metrics(m, filepath=self.metrics_file)
        self.assertEqual(load_historical_metrics(filepath=self.metrics_file)[0]["quality_gates"]["human_acceptance_rate_percent"], 0.0)

        def fake_fetch(s_id, s_name):
            self.assertEqual(s_id, 464)
            return [
                {"key": "ATT-9901", "fields": {"summary": "Ticket 1", "status": {"name": "Erledigt"}, "issuetype": {"subtask": False}}},
                {"key": "ATT-9902", "fields": {"summary": "Ticket 2", "status": {"name": "Erledigt"}, "issuetype": {"subtask": False}}},
                {"key": "ATT-9903", "fields": {"summary": "Ticket 3", "status": {"name": "Final Review (Human)"}, "issuetype": {"subtask": False}}},
                {"key": "ATT-9904", "fields": {"summary": "Review & Retro - 2026-47.4", "status": {"name": "Erledigt"}, "issuetype": {"subtask": False}}},
            ]

        updated = refresh_human_acceptance_rates(fake_fetch, filepath=self.metrics_file)
        self.assertEqual(len(updated), 1)
        self.assertEqual(updated[0]["accepted"], 2)
        self.assertEqual(updated[0]["total"], 3)
        self.assertEqual(updated[0]["new_rate"], 66.7)
        reloaded = load_historical_metrics(filepath=self.metrics_file)[0]
        self.assertEqual(reloaded["quality_gates"]["human_acceptance_rate_percent"], 66.7)
        self.assertEqual(reloaded["quality_gates"]["human_accepted_tickets"], 2)

    def test_sprint_metrics_po_acceptance_followup_split_and_comment_coverage(self):
        """PO Acceptance stats in sprint_metrics track linked follow-ups (clean/refinement/bug) & [PO Review Comment] coverage."""
        from sprint_metrics import refresh_human_acceptance_rates

        followup_types = {"ATT-9951": "Verbesserung", "ATT-9952": "Bug"}
        parent_issues = [
            {
                "key": "ATT-9940",
                "fields": {
                    "summary": "Clean ticket",
                    "status": {"name": "Erledigt"},
                    "issuelinks": [],
                    "comment": {"comments": [{"body": "[PO Review Comment]: Verified on Pixel 10, works cleanly."}]},
                    "subtasks": [],
                },
            },
            {
                "key": "ATT-9941",
                "fields": {
                    "summary": "Accepted with refinement",
                    "status": {"name": "Erledigt"},
                    "issuelinks": [{"inwardIssue": {"key": "ATT-9951", "fields": {"issuetype": {"name": "Verbesserung"}}}}],
                    "comment": {"comments": [{"body": "[PO Review Comment]: Accepted; follow-up ATT-9951 filed for contrast."}]},
                    "subtasks": [],
                },
            },
            {
                "key": "ATT-9942",
                "fields": {
                    "summary": "Accepted with bug escape",
                    "status": {"name": "Erledigt"},
                    "issuelinks": [{"inwardIssue": {"key": "ATT-9952", "fields": {"issuetype": {"name": "Bug"}}}}],
                    "comment": {"comments": [{"body": "Agent comment only"}]},
                    "subtasks": [],
                },
            },
            {
                "key": "ATT-9943",
                "fields": {
                    "summary": "Still awaiting PO",
                    "status": {"name": "Final Review (Human)"},
                    "issuelinks": [],
                    "comment": {"comments": []},
                    "subtasks": [],
                },
            },
        ]

        m = parse_sprint_gate_metrics(
            parent_issues,
            {"id": 474, "name": "2026-11-22_01_Früh"},
            followup_issue_types_by_key=followup_types,
        )
        split = m["quality_gates"]["po_acceptance_split"]
        self.assertEqual(split, {
            "total_accepted": 3,
            "clean_accepted": 1,
            "accepted_with_refinement": 1,
            "accepted_with_bug_escape": 1,
            "with_po_comment": 2,
            "po_comment_coverage_percent": 66.7,
        })
        md = format_metrics_markdown(m)
        self.assertIn("PO Acceptance Breakdown", md)
        self.assertIn("Clean=1 | Refinement (Verbesserung/Feature)=1 | Bug Escape=1", md)
        self.assertIn("PO Comment Coverage=2/3 (66.7%)", md)

        # Verify refresh_human_acceptance_rates also persists po_acceptance_split
        record_sprint_metrics(m, filepath=self.metrics_file)
        updated = refresh_human_acceptance_rates(
            lambda sid, sname: parent_issues,
            filepath=self.metrics_file,
            followup_issue_types_by_key=followup_types,
        )
        self.assertEqual(updated[0]["po_acceptance_split"]["with_po_comment"], 2)
        reloaded = load_historical_metrics(filepath=self.metrics_file)[0]
        self.assertEqual(reloaded["quality_gates"]["po_acceptance_split"]["accepted_with_bug_escape"], 1)


if __name__ == "__main__":
    unittest.main()
