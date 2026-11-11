#!/usr/bin/env python3
"""
Unit tests for Jira Epic Lifecycle CLI Operations & Taxonomy Alignment (REQ-PRO-040, TST-PRO-040).

Verifies:
- TST-PRO-040.1: List Epics Command & Child Status Aggregation (Cloud & Data Center)
- TST-PRO-040.2: Close Epic Triple Guardrail Enforcement (Role, IssueType, Completeness)
- TST-PRO-040.3: Transition Issue Epic Protection & Human Gate Preservation
- TST-PRO-040.4: Tooling Parity between root and hoechstmass jira_util.py
"""

import importlib.util
import io
import os
import sys
import unittest
from unittest.mock import patch, MagicMock

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TOOLS_DIR = os.path.join(REPO_ROOT, "tools")
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

import jira_util as root_jira

# Load hoechstmass jira_util under distinct module name
hm_path = os.path.join(TOOLS_DIR, "hoechstmass", "tools", "jira_util.py")
spec = importlib.util.spec_from_file_location("hm_jira_util", hm_path)
hm_jira = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hm_jira)


class TestListEpics(unittest.TestCase):
    """TST-PRO-040.1: list-epics command & child count aggregation."""

    @patch("jira_util.jira_request")
    def test_list_epics_cloud_aggregation(self, mock_request):
        # 1. Epics query response
        epics_response = {
            "issues": [
                {
                    "key": "ATT-100",
                    "fields": {
                        "summary": "Milestone Epic A",
                        "status": {"name": "Zu erledigen"},
                        "issuetype": {"name": "Epic"}
                    }
                },
                {
                    "key": "ATT-200",
                    "fields": {
                        "summary": "Evergreen Epic B",
                        "status": {"name": "Zu erledigen"},
                        "issuetype": {"name": "Epic"}
                    }
                }
            ],
            "isLast": True
        }

        # 2. Child query response
        children_response = {
            "issues": [
                {
                    "key": "ATT-101",
                    "fields": {
                        "status": {"name": "Erledigt"},
                        "parent": {"key": "ATT-100"}
                    }
                },
                {
                    "key": "ATT-102",
                    "fields": {
                        "status": {"name": "In Bearbeitung"},
                        "parent": {"key": "ATT-100"}
                    }
                },
                {
                    "key": "ATT-201",
                    "fields": {
                        "status": {"name": "Done"},
                        "parent": {"key": "ATT-200"}
                    }
                }
            ],
            "isLast": True
        }

        mock_request.side_effect = [epics_response, children_response]

        out = io.StringIO()
        with patch("sys.stdout", out):
            root_jira.list_epics(role="agent1", config={"JIRA_URL": "https://example.atlassian.net"})

        output = out.getvalue()
        self.assertIn("Milestone Epic A", output)
        self.assertIn("Evergreen Epic B", output)
        self.assertIn("ATT-100", output)
        self.assertIn("ATT-200", output)
        self.assertIn("Total Epics: 2", output)

    @patch("jira_util.jira_request")
    def test_list_epics_empty_response(self, mock_request):
        mock_request.return_value = {"issues": [], "isLast": True}
        out = io.StringIO()
        with patch("sys.stdout", out):
            root_jira.list_epics(role="agent1", config={"JIRA_URL": "https://example.atlassian.net"})
        self.assertIn("No Epics found in project.", out.getvalue())


class TestCloseEpicGuardrails(unittest.TestCase):
    """TST-PRO-040.2: close-epic triple guardrail enforcement."""

    def test_close_epic_role_guard_rejects_agent1_and_agent2(self):
        for role in ["agent1", "agent2"]:
            with self.assertRaises(SystemExit) as cm:
                err = io.StringIO()
                with patch("sys.stderr", err):
                    root_jira.close_epic("ATT-100", role=role)
            self.assertEqual(cm.exception.code, 1)
            self.assertIn(f"Role '{role}' is not authorized to close Epics", err.getvalue())

    @patch("jira_util.jira_request")
    def test_close_epic_issue_type_guard_rejects_standard_tickets(self, mock_request):
        mock_request.return_value = {
            "key": "ATT-5000",
            "fields": {
                "issuetype": {"name": "Verbesserung"},
                "status": {"name": "Implementation"},
                "summary": "Regular Improvement Ticket"
            }
        }
        with self.assertRaises(SystemExit) as cm:
            err = io.StringIO()
            with patch("sys.stderr", err):
                root_jira.close_epic("ATT-5000", role="coordinator", config={"JIRA_URL": "https://example.atlassian.net"})
        self.assertEqual(cm.exception.code, 1)
        self.assertIn("not an Epic (issue type is 'Verbesserung')", err.getvalue())
        self.assertIn("REQ-PRO-016 Human Decision Gate", err.getvalue())

    @patch("jira_util.jira_request")
    def test_close_epic_completeness_guard_aborts_with_open_children(self, mock_request):
        # 1. Epic lookup
        epic_data = {
            "key": "ATT-100",
            "fields": {
                "issuetype": {"name": "Epic"},
                "status": {"name": "Zu erledigen"},
                "summary": "Target Milestone Epic"
            }
        }
        # 2. Child lookup returning open children
        children_data = {
            "issues": [
                {
                    "key": "ATT-101",
                    "fields": {
                        "status": {"name": "Erledigt"},
                        "summary": "Completed Child"
                    }
                },
                {
                    "key": "ATT-102",
                    "fields": {
                        "status": {"name": "In Bearbeitung"},
                        "summary": "Unfinished Child Task"
                    }
                }
            ],
            "isLast": True
        }
        mock_request.side_effect = [epic_data, children_data]

        with self.assertRaises(SystemExit) as cm:
            err = io.StringIO()
            with patch("sys.stderr", err):
                root_jira.close_epic("ATT-100", role="coordinator", config={"JIRA_URL": "https://example.atlassian.net"})
        self.assertEqual(cm.exception.code, 1)
        err_val = err.getvalue()
        self.assertIn("1 open child ticket(s) remain", err_val)
        self.assertIn("ATT-102", err_val)
        self.assertIn("Unfinished Child Task", err_val)

    @patch("jira_util.transition_issue")
    @patch("jira_util.jira_request")
    def test_close_epic_success_when_all_children_done(self, mock_request, mock_transition):
        # 1. Epic lookup
        epic_data = {
            "key": "ATT-100",
            "fields": {
                "issuetype": {"name": "Epic"},
                "status": {"name": "Zu erledigen"},
                "summary": "Completed Milestone Initiative"
            }
        }
        # 2. Child lookup returning only completed tickets
        children_data = {
            "issues": [
                {
                    "key": "ATT-101",
                    "fields": {
                        "status": {"name": "Erledigt"},
                        "summary": "Child A"
                    }
                },
                {
                    "key": "ATT-102",
                    "fields": {
                        "status": {"name": "Done"},
                        "summary": "Child B"
                    }
                }
            ],
            "isLast": True
        }
        mock_request.side_effect = [epic_data, children_data]

        out = io.StringIO()
        with patch("sys.stdout", out):
            root_jira.close_epic("ATT-100", role="coordinator", config={"JIRA_URL": "https://example.atlassian.net"})

        mock_transition.assert_called_once_with("ATT-100", "erledigt", role="coordinator", allow_epic_close=True)
        self.assertIn("successfully verified and closed", out.getvalue())


class TestTransitionIssueEpicProtection(unittest.TestCase):
    """TST-PRO-040.3: Preservation of Human Gate on parent tickets in transition_issue."""

    @patch("jira_util.jira_request")
    def test_transition_issue_blocks_standard_ticket_to_erledigt(self, mock_request):
        mock_request.return_value = {
            "key": "ATT-5911",
            "fields": {
                "issuetype": {"name": "Verbesserung", "subtask": False},
                "status": {"name": "Implementation"},
                "summary": "Standard Parent Ticket"
            }
        }
        with self.assertRaises(SystemExit) as cm:
            err = io.StringIO()
            with patch("sys.stderr", err):
                root_jira.transition_issue("ATT-5911", "erledigt", role="agent1")
        self.assertEqual(cm.exception.code, 1)
        self.assertIn("strictly prohibited for AI agents", err.getvalue())

    @patch("jira_util.jira_request")
    def test_transition_issue_blocks_epic_when_allow_epic_close_is_false(self, mock_request):
        mock_request.return_value = {
            "key": "ATT-281",
            "fields": {
                "issuetype": {"name": "Epic", "subtask": False},
                "status": {"name": "Zu erledigen"},
                "summary": "Import Export Epic"
            }
        }
        with self.assertRaises(SystemExit) as cm:
            err = io.StringIO()
            with patch("sys.stderr", err):
                root_jira.transition_issue("ATT-281", "erledigt", role="agent1", allow_epic_close=False)
        self.assertEqual(cm.exception.code, 1)
        self.assertIn("strictly prohibited for AI agents", err.getvalue())

    @patch("jira_util.jira_request")
    def test_transition_issue_blocks_non_epic_even_if_allow_epic_close_is_true(self, mock_request):
        mock_request.return_value = {
            "key": "ATT-5911",
            "fields": {
                "issuetype": {"name": "Verbesserung", "subtask": False},
                "status": {"name": "Implementation"},
                "summary": "Non-Epic Ticket"
            }
        }
        with self.assertRaises(SystemExit) as cm:
            err = io.StringIO()
            with patch("sys.stderr", err):
                root_jira.transition_issue("ATT-5911", "erledigt", role="coordinator", allow_epic_close=True)
        self.assertEqual(cm.exception.code, 1)
        self.assertIn("strictly prohibited for AI agents", err.getvalue())


class TestHoechstmassEpicParity(unittest.TestCase):
    """TST-PRO-040.4: DRY Parity with tools/hoechstmass/tools/jira_util.py."""

    def test_hoechstmass_functions_exist(self):
        for fn in ["list_epics", "close_epic", "paged_jql_search", "transition_issue"]:
            self.assertTrue(hasattr(hm_jira, fn), f"hm_jira missing function '{fn}'")

    def test_hoechstmass_close_epic_role_guard(self):
        for role in ["agent1", "agent2"]:
            with self.assertRaises(SystemExit) as cm:
                err = io.StringIO()
                with patch("sys.stderr", err):
                    hm_jira.close_epic("PROJ-100", role=role)
            self.assertEqual(cm.exception.code, 1)
            self.assertIn(f"Role '{role}' is not authorized to close Epics", err.getvalue())

    @patch.object(hm_jira, "jira_request")
    def test_hoechstmass_close_epic_issue_type_guard(self, mock_request):
        mock_request.return_value = {
            "key": "PROJ-200",
            "fields": {
                "issuetype": {"name": "Story"},
                "status": {"name": "In Progress"},
                "summary": "Regular Story"
            }
        }
        with self.assertRaises(SystemExit) as cm:
            err = io.StringIO()
            with patch("sys.stderr", err):
                hm_jira.close_epic("PROJ-200", role="coordinator", config={"JIRA_URL": "https://jira.corp"})
        self.assertEqual(cm.exception.code, 1)
        self.assertIn("not an Epic (issue type is 'Story')", err.getvalue())


class TestStage6FinalReview(unittest.TestCase):
    """TST-PRO-058.1 & TST-PRO-058.2: Stage 6 Final Review (Agent) state machine, disambiguated transitions, and 3-bounce healing."""

    def test_parent_stage_order_and_skill_dir_mapping(self):
        for mod in [root_jira, hm_jira]:
            order = mod.PARENT_STAGE_ORDER
            self.assertIn("final review (agent)", order)
            self.assertIn("final review (human)", order)
            idx_test = order.index("test")
            idx_agent = order.index("final review (agent)")
            idx_human = order.index("final review (human)")
            self.assertTrue(idx_test < idx_agent < idx_human)
            self.assertEqual(mod.STAGE_TO_SKILL_DIR.get("final review (agent)"), "stage6-final-review")
            self.assertEqual(
                mod.resolve_desired_parent_stage_for_subtask("[Test] Stage 5 Verification", True),
                "final review (agent)"
            )

    @patch("jira_util.inject_stage_context")
    @patch("jira_util.assign_issue")
    @patch("jira_util.jira_request")
    def test_transition_issue_disambiguates_agent_vs_human_final_review(self, mock_req, mock_assign, mock_inject):
        # 1. Transition to Final Review (Agent) -> assigns to agent2
        mock_req.side_effect = [
            {"key": "ATT-7101", "fields": {"issuetype": {"name": "Verbesserung", "subtask": False}, "status": {"name": "Test"}}},
            {
                "transitions": [
                    {"id": "6", "name": "Test abgeschlossen", "to": {"name": "Final Review (Agent)"}},
                    {"id": "9", "name": "I.O.", "to": {"name": "Final Review (Human)"}},
                ]
            },
            None,
        ]
        out = io.StringIO()
        with patch("sys.stdout", out):
            root_jira.transition_issue("ATT-7101", "final review (agent)", role="agent2")
        mock_assign.assert_called_once_with("ATT-7101", "agent2", role="agent2")

        # 2. Transition to Final Review (Human) -> assigns to human
        mock_assign.reset_mock()
        mock_req.side_effect = [
            {"key": "ATT-7101", "fields": {"issuetype": {"name": "Verbesserung", "subtask": False}, "status": {"name": "Final Review (Agent)"}}},
            {
                "transitions": [
                    {"id": "6", "name": "Test abgeschlossen", "to": {"name": "Final Review (Agent)"}},
                    {"id": "9", "name": "I.O.", "to": {"name": "Final Review (Human)"}},
                ]
            },
            None,
        ]
        with patch("sys.stdout", out):
            root_jira.transition_issue("ATT-7101", "final review (human)", role="agent2")
        mock_assign.assert_called_once_with("ATT-7101", "human", role="agent2")

    def test_count_final_review_bounces(self):
        comments = [
            {"body": "[ROLE: agent2] Stage 6 Final Review (Agent) BOUNCE (1/3): Defect A."},
            {"body": "[ROLE: agent1] Fixed Defect A."},
            {"body": "[ROLE: agent2] [FINAL REVIEW (AGENT) — BOUNCE 2/3]\nDefect B."},
            {"body": "[ROLE: agent2] [FINAL REVIEW (AGENT) — PASS]\nClean."},
        ]
        self.assertEqual(root_jira.count_final_review_bounces(comments), 2)
        self.assertEqual(hm_jira.count_final_review_bounces(comments), 2)

    @patch("jira_util.assign_issue")
    @patch("jira_util.transition_issue")
    @patch("jira_util.add_comment")
    @patch("jira_util.jira_request")
    def test_final_review_verdict_pass_and_bounce_and_escalation(
        self, mock_req, mock_add_comment, mock_transition, mock_assign
    ):
        # Case 1: PASS -> transitions to Final Review (Human)
        mock_req.return_value = {
            "key": "ATT-7101",
            "fields": {
                "issuetype": {"name": "Verbesserung", "subtask": False},
                "status": {"name": "Final Review (Agent)"},
                "comment": {"comments": []},
            },
        }
        res_pass = root_jira.final_review_verdict("ATT-7101", "PASS", "All criteria satisfied.", role="agent2")
        self.assertEqual(res_pass, "PASS")
        mock_transition.assert_called_with("ATT-7101", "final review (human)", role="agent2", inject_context=False)

        # Case 2: BOUNCE (1/3) -> transitions to implementation
        mock_transition.reset_mock()
        res_b1 = root_jira.final_review_verdict(
            "ATT-7101", "BOUNCE", "Missing test case.", bounce_target="implementation", role="agent2"
        )
        self.assertEqual(res_b1, "BOUNCE_1")
        mock_transition.assert_called_with("ATT-7101", "implementation", role="agent2", inject_context=False)

        # Case 3: 4th BOUNCE (> 3) -> ESCALATED to Final Review (Human)
        mock_transition.reset_mock()
        mock_req.return_value = {
            "key": "ATT-7101",
            "fields": {
                "issuetype": {"name": "Verbesserung", "subtask": False},
                "status": {"name": "Final Review (Agent)"},
                "comment": {
                    "comments": [
                        {"body": "[FINAL REVIEW (AGENT) — BOUNCE 1/3]\nFirst"},
                        {"body": "[FINAL REVIEW (AGENT) — BOUNCE 2/3]\nSecond"},
                        {"body": "[FINAL REVIEW (AGENT) — BOUNCE 3/3]\nThird"},
                    ]
                },
            },
        }
        res_esc = root_jira.final_review_verdict(
            "ATT-7101", "BOUNCE", "Still failing on 4th attempt.", bounce_target="implementation", role="agent2"
        )
        self.assertEqual(res_esc, "ESCALATED")
        mock_transition.assert_called_with("ATT-7101", "final review (human)", role="agent2", inject_context=False)


if __name__ == "__main__":
    unittest.main()
