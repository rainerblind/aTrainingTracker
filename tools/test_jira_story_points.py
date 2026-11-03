#!/usr/bin/env python3
"""
Unit and Integration Test Suite for Story Point Estimation, Complexity Split Guardrail & Sprint Capacity Tracking.
Verifies:
- REQ-PRO-029: AAA 1/2/3/5+ Story Point Estimation, Complexity Split Guardrail & Sprint Capacity Tracking
- TST-PRO-022: AAA Story Point Estimation, CLI Operations, Complexity Split Guardrail & Sprint Capacity Tracking Verification
"""

import io
import os
import sys
import unittest
from unittest.mock import patch, MagicMock

# Ensure tools directory is on sys.path
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TOOLS_DIR = os.path.join(REPO_ROOT, "tools")
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

import jira_util


class TestJiraStoryPoints(unittest.TestCase):
    """Verifies TST-PRO-022.1: Jira CLI Story Point Unit & Integration Tests."""

    def setUp(self):
        self.mock_config = {
            "JIRA_URL": "https://example.atlassian.net",
            "JIRA_USER": "test@example.com",
            "JIRA_TOKEN": "token_123"
        }

    @patch("jira_util.get_config")
    @patch("jira_util.jira_request")
    def test_set_story_points_integer(self, mock_request, mock_get_config):
        """Setting an integer story point value sets customfield_10016 as float."""
        mock_get_config.return_value = self.mock_config
        stdout_capture = io.StringIO()
        with patch("sys.stdout", stdout_capture):
            jira_util.set_story_points("ATT-1234", "2")

        mock_request.assert_called_once_with(
            "https://example.atlassian.net/rest/api/2/issue/ATT-1234",
            method="PUT",
            payload={"fields": {"customfield_10016": 2.0}},
            role="agent1"
        )
        self.assertIn("Updated story points for ATT-1234 to 2 SP", stdout_capture.getvalue())

    @patch("jira_util.get_config")
    @patch("jira_util.jira_request")
    def test_set_story_points_float(self, mock_request, mock_get_config):
        """Setting a fractional story point value formats correctly."""
        mock_get_config.return_value = self.mock_config
        stdout_capture = io.StringIO()
        with patch("sys.stdout", stdout_capture):
            jira_util.set_story_points("ATT-1234", 1.5, role="agent2")

        mock_request.assert_called_once_with(
            "https://example.atlassian.net/rest/api/2/issue/ATT-1234",
            method="PUT",
            payload={"fields": {"customfield_10016": 1.5}},
            role="agent2"
        )
        self.assertIn("Updated story points for ATT-1234 to 1.5 SP", stdout_capture.getvalue())

    @patch("jira_util.get_config")
    @patch("jira_util.jira_request")
    def test_set_story_points_clear(self, mock_request, mock_get_config):
        """Passing 'none', 'clear', or empty string resets customfield_10016 to None."""
        mock_get_config.return_value = self.mock_config
        for clear_val in ("none", "clear", "null", ""):
            mock_request.reset_mock()
            jira_util.set_story_points("ATT-1234", clear_val)
            mock_request.assert_called_once_with(
                "https://example.atlassian.net/rest/api/2/issue/ATT-1234",
                method="PUT",
                payload={"fields": {"customfield_10016": None}},
                role="agent1"
            )

    @patch("jira_util.get_config")
    def test_set_story_points_negative_rejected(self, mock_get_config):
        """Negative story points must be rejected with exit code 1."""
        mock_get_config.return_value = self.mock_config
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            with self.assertRaises(SystemExit) as cm:
                jira_util.set_story_points("ATT-1234", "-1")
            self.assertEqual(cm.exception.code, 1)
        self.assertIn("cannot be negative", stderr_capture.getvalue())

    @patch("jira_util.get_config")
    def test_set_story_points_invalid_string_rejected(self, mock_get_config):
        """Non-numeric string values must be rejected with exit code 1."""
        mock_get_config.return_value = self.mock_config
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            with self.assertRaises(SystemExit) as cm:
                jira_util.set_story_points("ATT-1234", "huge")
            self.assertEqual(cm.exception.code, 1)
        self.assertIn("Invalid story points", stderr_capture.getvalue())

    @patch("jira_util.get_config")
    @patch("jira_util.jira_request")
    def test_create_issue_with_story_points(self, mock_request, mock_get_config):
        """create_issue with points populates customfield_10016 in payload."""
        mock_get_config.return_value = self.mock_config
        mock_request.return_value = {"key": "ATT-9999"}

        with patch("jira_util.rank_at_top_of_backlog"):
            new_key = jira_util.create_issue(
                summary="Test ticket with SP",
                description="Ticket description",
                issuetype_id="10008",
                points="3"
            )

        self.assertEqual(new_key, "ATT-9999")
        post_call = [c for c in mock_request.call_args_list if c[1].get("method") == "POST"][0]
        fields = post_call[1]["payload"]["fields"]
        self.assertEqual(fields.get("customfield_10016"), 3.0)

    @patch("jira_util.get_config")
    @patch("jira_util.jira_request")
    def test_show_issue_displays_story_points(self, mock_request, mock_get_config):
        """show_issue correctly formats story points when present and when None."""
        mock_get_config.return_value = self.mock_config
        mock_issue_with_sp = {
            "key": "ATT-1001",
            "fields": {
                "summary": "Sample Issue",
                "description": "Desc",
                "issuetype": {"name": "Verbesserung"},
                "status": {"name": "Analysis"},
                "assignee": {"displayName": "Agent 1"},
                "priority": {"name": "Medium"},
                "labels": ["process"],
                "customfield_10016": 2.0,
                "fixVersions": [],
                "comment": {"comments": []},
                "attachment": [],
                "subtasks": []
            }
        }
        mock_request.return_value = mock_issue_with_sp
        stdout_capture = io.StringIO()
        with patch("sys.stdout", stdout_capture):
            jira_util.show_issue("ATT-1001")
        self.assertIn("*Story Points*: 2 SP", stdout_capture.getvalue())

        mock_issue_no_sp = dict(mock_issue_with_sp)
        mock_issue_no_sp["fields"] = dict(mock_issue_with_sp["fields"], customfield_10016=None)
        mock_request.return_value = mock_issue_no_sp
        stdout_capture = io.StringIO()
        with patch("sys.stdout", stdout_capture):
            jira_util.show_issue("ATT-1001")
        self.assertIn("*Story Points*: None", stdout_capture.getvalue())

    @patch("jira_util.get_config")
    @patch("jira_util.resolve_active_sprint")
    @patch("jira_util.jira_request")
    def test_list_sprint_issues_aggregation(self, mock_request, mock_resolve_sprint, mock_get_config):
        """list_sprint_issues aggregates story points and counts unestimated tickets."""
        mock_get_config.return_value = self.mock_config
        mock_resolve_sprint.return_value = {"id": 100, "name": "Sprint 2026-45.2"}

        def side_effect(url, method="GET", payload=None, role="agent1"):
            if "/board" in url:
                return {"values": [{"id": 1}]}
            if "/sprint/100/issue" in url:
                return {
                    "issues": [
                        {
                            "key": "ATT-1",
                            "fields": {
                                "summary": "Ticket 1",
                                "issuetype": {"name": "Verbesserung"},
                                "status": {"name": "Analysis"},
                                "fixVersions": [],
                                "customfield_10016": 1.0
                            }
                        },
                        {
                            "key": "ATT-2",
                            "fields": {
                                "summary": "Ticket 2",
                                "issuetype": {"name": "Bug"},
                                "status": {"name": "Test"},
                                "fixVersions": [{"name": "V4.9.41"}],
                                "customfield_10016": 2.5
                            }
                        },
                        {
                            "key": "ATT-3",
                            "fields": {
                                "summary": "Ticket 3",
                                "issuetype": {"name": "Feature"},
                                "status": {"name": "Zu erledigen"},
                                "fixVersions": [],
                                "customfield_10016": None
                            }
                        }
                    ]
                }
            return {}

        mock_request.side_effect = side_effect
        stdout_capture = io.StringIO()
        with patch("sys.stdout", stdout_capture):
            jira_util.list_sprint_issues()

        out = stdout_capture.getvalue()
        self.assertIn("ATT-1: [Verbesserung] [1 SP] Ticket 1", out)
        self.assertIn("ATT-2: [Bug] [2.5 SP] Ticket 2", out)
        self.assertIn("ATT-3: [Feature] [No SP] Ticket 3", out)
        self.assertIn("Sprint Total: 3 issues, 3.5 Story Points (1 unestimated)", out)

    @patch("jira_util.get_config")
    @patch("jira_util.jira_request")
    def test_complexity_split_guardrail_blocks_oversized_tickets(self, mock_request, mock_get_config):
        """transition_issue blocks moving >= 5 SP parent tickets to analysis without --force."""
        mock_get_config.return_value = self.mock_config
        mock_request.return_value = {
            "fields": {
                "issuetype": {"subtask": False},
                "status": {"name": "Zu erledigen"},
                "summary": "Massive Epic-Like Ticket",
                "customfield_10016": 5.0
            }
        }

        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            with patch("sys.argv", ["jira_util.py", "move", "ATT-5000", "analysis"]):
                with self.assertRaises(SystemExit) as cm:
                    jira_util.transition_issue("ATT-5000", "analysis")
                self.assertEqual(cm.exception.code, 1)

        err_out = stderr_capture.getvalue()
        self.assertIn("estimated at 5 Story Points (>= 5 SP)", err_out)
        self.assertIn("must be split into smaller, atomic tickets (1-3 SP)", err_out)

    @patch("jira_util.get_config")
    @patch("jira_util.jira_request")
    def test_complexity_split_guardrail_permits_split_tickets(self, mock_request, mock_get_config):
        """transition_issue permits moving <= 3 SP tickets to analysis."""
        mock_get_config.return_value = self.mock_config

        def side_effect(url, method="GET", payload=None, role="agent1"):
            if "/issue/ATT-5001" in url and "transitions" not in url:
                return {
                    "fields": {
                        "issuetype": {"subtask": False},
                        "status": {"name": "Zu erledigen"},
                        "summary": "Atomic Feature",
                        "customfield_10016": 2.0
                    }
                }
            if "transitions" in url:
                if method == "POST":
                    return {}
                return {"transitions": [{"id": "21", "name": "Start Analysis", "to": {"name": "Analysis"}}]}
            return {}

        mock_request.side_effect = side_effect
        with patch("sys.argv", ["jira_util.py", "move", "ATT-5001", "analysis"]):
            with patch("jira_util.inject_stage_context"):
                jira_util.transition_issue("ATT-5001", "analysis", inject_context=False)


if __name__ == "__main__":
    unittest.main()
