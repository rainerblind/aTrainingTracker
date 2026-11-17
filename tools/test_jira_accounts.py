#!/usr/bin/env python3
"""
Unit and Integration Test Suite for Multi-Account Jira Authentication and Coordinator Governance.
Verifies:
- REQ-PRO-019 / TST-PRO-012: Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling
- REQ-PRO-020 / TST-PRO-013: Role Locking, Precedence, Anti-Spoofing & Actor Comment Attribution
- REQ-PRO-021 / TST-PRO-014: Jira Credential & Token Secret Masking in Tooling
"""

import unittest
import os
import sys
import io
import base64
import tempfile
from datetime import datetime, timedelta
from unittest.mock import patch, MagicMock

# Ensure tools directory is on sys.path
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TOOLS_DIR = os.path.join(REPO_ROOT, "tools")
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

import jira_util
import review_agent


class TestJiraAccountResolution(unittest.TestCase):
    """Verifies REQ-PRO-019 / TST-PRO-012: Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling."""

    def test_full_role_credentials_resolution(self):
        """When role-specific user and token are both non-empty, resolve_account returns them."""
        config = {
            "JIRA_USER": "fallback_user@example.com",
            "JIRA_TOKEN": "fallback_token_12345",
            "JIRA_AGENT1_USER": "agent1@example.com",
            "JIRA_AGENT1_TOKEN": "agent1_secret_token",
            "JIRA_AGENT2_USER": "agent2@example.com",
            "JIRA_AGENT2_TOKEN": "agent2_secret_token",
            "JIRA_COORDINATOR_USER": "coordinator@example.com",
            "JIRA_COORDINATOR_TOKEN": "coordinator_secret_token",
        }
        self.assertEqual(jira_util.resolve_account("agent1", config), ("agent1@example.com", "agent1_secret_token"))
        self.assertEqual(jira_util.resolve_account("agent2", config), ("agent2@example.com", "agent2_secret_token"))
        self.assertEqual(jira_util.resolve_account("coordinator", config), ("coordinator@example.com", "coordinator_secret_token"))

    def test_role_credential_isolation_when_token_missing(self):
        """When role user is set but role token is missing for an agent, abort without falling back."""
        config = {
            "JIRA_USER": "fallback_user@example.com",
            "JIRA_TOKEN": "fallback_token_12345",
            "JIRA_AGENT1_USER": "agent1@example.com",
            "JIRA_AGENT1_TOKEN": "",  # Missing token
        }
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            with self.assertRaises(SystemExit) as cm:
                jira_util.resolve_account("agent1", config)
            self.assertEqual(cm.exception.code, 1)
        log_output = stderr_capture.getvalue()
        self.assertIn("Missing role-specific credentials for role 'agent1'", log_output)
        self.assertIn("Fallback to human default credentials", log_output)
        # Verify secret masking: neither token is exposed in stderr
        self.assertNotIn("fallback_token_12345", log_output)

    def test_role_credential_isolation_when_user_missing(self):
        """When role token is set but role user is missing for an agent, abort without falling back."""
        config = {
            "JIRA_USER": "fallback_user@example.com",
            "JIRA_TOKEN": "fallback_token_12345",
            "JIRA_COORDINATOR_TOKEN": "coordinator_secret_token",
            # JIRA_COORDINATOR_USER is absent
        }
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            with self.assertRaises(SystemExit) as cm:
                jira_util.resolve_account("coordinator", config)
            self.assertEqual(cm.exception.code, 1)
        log_output = stderr_capture.getvalue()
        self.assertIn("Missing role-specific credentials for role 'coordinator'", log_output)
        self.assertIn("Fallback to human default credentials", log_output)
        self.assertNotIn("coordinator_secret_token", log_output)

    def test_role_credential_isolation_when_role_unconfigured(self):
        """When agent role keys are not defined at all, abort without falling back to human credentials."""
        config = {
            "JIRA_USER": "default_user@example.com",
            "JIRA_TOKEN": "default_token_xyz",
        }
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            with self.assertRaises(SystemExit) as cm:
                jira_util.resolve_account("agent2", config)
            self.assertEqual(cm.exception.code, 1)
        log_output = stderr_capture.getvalue()
        self.assertIn("Missing role-specific credentials for role 'agent2'", log_output)

    def test_human_role_resolution_uses_default_credentials(self):
        """When resolving credentials for human role, default JIRA_USER and JIRA_TOKEN are used."""
        config = {
            "JIRA_USER": "human_user@example.com",
            "JIRA_TOKEN": "human_token_xyz",
        }
        user, token = jira_util.resolve_account("human", config)
        self.assertEqual(user, "human_user@example.com")
        self.assertEqual(token, "human_token_xyz")

    def test_terminal_failure_when_no_credentials_exist(self):
        """When neither role nor default credentials exist, exit code 1 is triggered with clear diagnostic."""
        config = {}
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            with self.assertRaises(SystemExit) as cm:
                jira_util.resolve_account("agent1", config)
            self.assertEqual(cm.exception.code, 1)
        err_msg = stderr_capture.getvalue()
        self.assertIn("Missing role-specific credentials for role 'agent1'", err_msg)

    def test_basic_auth_header_encoding(self):
        """Headers generate valid HTTP Basic Auth using resolved credentials."""
        config = {
            "JIRA_AGENT1_USER": "test_agent1@example.com",
            "JIRA_AGENT1_TOKEN": "token_abc_123",
        }
        headers = jira_util.get_headers(config, role="agent1")
        self.assertIn("Authorization", headers)
        auth_val = headers["Authorization"]
        self.assertTrue(auth_val.startswith("Basic "))
        encoded = auth_val.split("Basic ")[1]
        decoded = base64.b64decode(encoded.encode("ascii")).decode("ascii")
        self.assertEqual(decoded, "test_agent1@example.com:token_abc_123")

    def test_per_role_config_file_resolution(self):
        """get_config(role=...) loads per-role files and isolates role credentials (ATT-6147)."""
        import tempfile
        with tempfile.TemporaryDirectory() as tmpdir:
            base_file = os.path.join(tmpdir, ".env.jira")
            with open(base_file, "w") as f:
                f.write("JIRA_URL=https://jira.example.com\nJIRA_PROJECT=ATT\n")
            a1_file = os.path.join(tmpdir, ".env.jira.agent1")
            with open(a1_file, "w") as f:
                f.write("JIRA_AGENT1_USER=a1@example.com\nJIRA_AGENT1_TOKEN=token1\n")
            a2_file = os.path.join(tmpdir, ".env.jira.agent2")
            with open(a2_file, "w") as f:
                f.write("JIRA_AGENT2_USER=a2@example.com\nJIRA_AGENT2_TOKEN=token2\n")

            with patch("os.path.dirname", return_value=os.path.join(tmpdir, "tools")):
                cfg_a1 = jira_util.get_config(role="agent1")
                self.assertEqual(cfg_a1.get("JIRA_URL"), "https://jira.example.com")
                self.assertEqual(cfg_a1.get("JIRA_AGENT1_USER"), "a1@example.com")
                self.assertEqual(cfg_a1.get("JIRA_AGENT1_TOKEN"), "token1")
                self.assertNotIn("JIRA_AGENT2_USER", cfg_a1)

                cfg_a2 = jira_util.get_config(role="agent2")
                self.assertEqual(cfg_a2.get("JIRA_URL"), "https://jira.example.com")
                self.assertEqual(cfg_a2.get("JIRA_AGENT2_USER"), "a2@example.com")
                self.assertEqual(cfg_a2.get("JIRA_AGENT2_TOKEN"), "token2")
                self.assertNotIn("JIRA_AGENT1_USER", cfg_a2)

    def test_strict_per_role_isolation_cross_role_access(self):
        """When only agent1 credential file exists, resolving agent2 aborts with exit code 1 (ATT-6147 AC-2)."""
        import tempfile
        with tempfile.TemporaryDirectory() as tmpdir:
            base_file = os.path.join(tmpdir, ".env.jira")
            with open(base_file, "w") as f:
                f.write("JIRA_URL=https://jira.example.com\nJIRA_PROJECT=ATT\n")
            a1_file = os.path.join(tmpdir, ".env.jira.agent1")
            with open(a1_file, "w") as f:
                f.write("JIRA_AGENT1_USER=a1@example.com\nJIRA_AGENT1_TOKEN=token1\n")

            with patch("os.path.dirname", return_value=os.path.join(tmpdir, "tools")):
                cfg_a2 = jira_util.get_config(role="agent2")
                stderr_capture = io.StringIO()
                with patch("sys.stderr", stderr_capture):
                    with self.assertRaises(SystemExit) as cm:
                        jira_util.resolve_account("agent2", cfg_a2)
                    self.assertEqual(cm.exception.code, 1)
                self.assertIn("Missing role-specific credentials for role 'agent2'", stderr_capture.getvalue())

    def test_gitignore_contains_env_jira_wildcard(self):
        """.gitignore must contain .env.jira* to prevent committing per-role credentials (ATT-6147)."""
        gitignore_path = os.path.join(REPO_ROOT, ".gitignore")
        with open(gitignore_path, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertIn(".env.jira*", content)

    def test_hoechstmass_example_has_no_human_credentials(self):
        """.env.jira.example must not contain human credentials JIRA_USER or JIRA_TOKEN (ATT-6147 AC-1)."""
        example_path = os.path.join(REPO_ROOT, "tools", "hoechstmass", ".env.jira.example")
        with open(example_path, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertNotIn("JIRA_USER=", content)
        self.assertNotIn("JIRA_TOKEN=", content)


class TestRolePrecedenceAndAttribution(unittest.TestCase):
    """Verifies REQ-PRO-020 / TST-PRO-013: Role Locking, Precedence, Anti-Spoofing & Actor Comment Attribution."""

    def test_role_precedence_cli_over_env_and_default(self):
        """When JIRA_ACTOR is set, mismatched --as <role> is rejected (REQ-PRO-108.1); matching role succeeds."""
        # Mismatched --as raises SystemExit(1)
        with patch.dict(os.environ, {"JIRA_ACTOR": "agent2"}):
            with self.assertRaises(SystemExit) as cm:
                jira_util.parse_role_from_args(["--as", "coordinator", "status", "ATT-1201"])
            self.assertEqual(cm.exception.code, 1)

        # Matching --as succeeds
        with patch.dict(os.environ, {"JIRA_ACTOR": "coordinator"}):
            parsed_role, remaining_args = jira_util.parse_role_from_args(["--as", "coordinator", "status", "ATT-1201"])
            self.assertEqual(parsed_role, "coordinator")
            self.assertEqual(remaining_args, ["status", "ATT-1201"])

    def test_role_precedence_env_over_default(self):
        """When no CLI flag is passed, JIRA_ACTOR takes precedence over default."""
        with patch.dict(os.environ, {"JIRA_ACTOR": "coordinator"}):
            parsed_role, remaining_args = jira_util.parse_role_from_args(["status", "ATT-1201"])
            self.assertEqual(parsed_role, "coordinator")
            self.assertEqual(remaining_args, ["status", "ATT-1201"])

    def test_role_precedence_default_agent1(self):
        """When neither CLI flag nor JIRA_ACTOR is provided, default to agent1."""
        with patch.dict(os.environ, {}, clear=True):
            parsed_role, remaining_args = jira_util.parse_role_from_args(["show", "ATT-1123"])
            self.assertEqual(parsed_role, "agent1")
            self.assertEqual(remaining_args, ["show", "ATT-1123"])

    def test_invalid_role_rejection(self):
        """An unrecognized role string causes terminal exit with code 1."""
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            with self.assertRaises(SystemExit) as cm:
                jira_util.parse_role_from_args(["--as", "imposter_role", "status", "ATT-1201"])
            self.assertEqual(cm.exception.code, 1)
        self.assertIn("Invalid role 'imposter_role'", stderr_capture.getvalue())

    def test_valid_roles_excludes_human(self):
        """VALID_ROLES must not contain human (REQ-PRO-020.5), but must include qa (REQ-PRO-091.1)."""
        self.assertNotIn("human", jira_util.VALID_ROLES)
        self.assertEqual(jira_util.VALID_ROLES, ["agent1", "agent2", "coordinator", "qa"])
        self.assertIn("qa", jira_util.VALID_ROLES)

    def test_human_role_selection_rejected_from_cli(self):
        """Selecting human role via --as human or --as=human causes fatal governance abort (AC-1)."""
        for flag in [["--as", "human"], ["--as=human"], ["--as", "user"], ["--as", "rainer"]]:
            stderr_capture = io.StringIO()
            with patch("sys.stderr", stderr_capture):
                with self.assertRaises(SystemExit) as cm:
                    jira_util.parse_role_from_args(flag + ["status", "ATT-1201"])
                self.assertEqual(cm.exception.code, 1)
            err_msg = stderr_capture.getvalue()
            self.assertIn("Error: 'human' role cannot be selected via agent CLI or environment variable", err_msg)

    def test_human_role_selection_rejected_from_env(self):
        """Selecting human role via JIRA_ACTOR causes fatal governance abort (AC-1)."""
        for actor in ["human", "user", "rainer", "HUMAN"]:
            with patch.dict(os.environ, {"JIRA_ACTOR": actor}):
                stderr_capture = io.StringIO()
                with patch("sys.stderr", stderr_capture):
                    with self.assertRaises(SystemExit) as cm:
                        jira_util.parse_role_from_args(["status", "ATT-1201"])
                    self.assertEqual(cm.exception.code, 1)
                err_msg = stderr_capture.getvalue()
                self.assertIn("Error: 'human' role cannot be selected via agent CLI or environment variable", err_msg)

    def test_comment_attribution_prefix(self):
        """add_comment prepends the correct role-specific attribution prefix."""
        prefixes = {
            "agent1": "[Automated comment by AI Agent 1 (Implementer)]\n\n",
            "agent2": "[Automated comment by AI Agent 2 (Auditor)]\n\n",
            "coordinator": "[Automated comment by AI Coordinator]\n\n",
            "unknown": "[Automated comment by AI Agent]\n\n",
        }
        for role, expected_prefix in prefixes.items():
            self.assertEqual(jira_util.get_comment_prefix(role), expected_prefix)

    def test_review_agent_hardwired_to_agent2(self):
        """tools/review_agent.py must permanently hardwire role to agent2."""
        self.assertEqual(review_agent.AUDITOR_ROLE, "agent2")
        # Ensure audit_subtask ignores JIRA_ACTOR or external spoofing
        with patch.dict(os.environ, {"JIRA_ACTOR": "agent1"}):
            self.assertEqual(review_agent.AUDITOR_ROLE, "agent2")

    def test_create_subtask_defaults_to_coordinator(self):
        """create_subtask defaults to the coordinator role unless explicitly overridden."""
        with patch("jira_util.jira_request") as mock_request, patch("jira_util.get_config") as mock_config:
            mock_config.return_value = {"JIRA_URL": "https://example.atlassian.net"}
            mock_request.return_value = {"key": "ATT-9999"}
            
            # Default invocation (no role passed)
            jira_util.create_subtask("ATT-1000", "Test Subtask", "Test Desc")
            self.assertGreaterEqual(mock_request.call_count, 1)
            _, kwargs = mock_request.call_args_list[0]
            self.assertEqual(kwargs.get("role"), "coordinator")

    def test_human_gate_transition_blocked_across_all_roles(self):
        """Attempting to transition parent ticket to Erledigt is blocked across all roles."""
        prohibited_targets = ["erledigt", "Erledigt", "DONE", "freigabe erteilt", "Freigabe erteilt"]
        for target in prohibited_targets:
            for role in ["agent1", "agent2", "coordinator"]:
                stderr_capture = io.StringIO()
                stdout_capture = io.StringIO()
                with patch("sys.stderr", stderr_capture), patch("sys.stdout", stdout_capture), patch("jira_util.jira_request") as mock_request, patch("jira_util.get_config") as mock_config:
                    mock_config.return_value = {"JIRA_URL": "https://example.atlassian.net"}
                    mock_request.return_value = {"fields": {"issuetype": {"subtask": False}}}
                    with self.assertRaises(SystemExit) as cm:
                        jira_util.transition_issue("ATT-1202", target, role=role)
                    self.assertEqual(cm.exception.code, 1)

    def test_subtask_freigabe_transitions_to_erledigt(self):
        """Subtasks can transition directly to Erledigt via freigabe without human review stop."""
        with patch("jira_util.jira_request") as mock_request, patch("jira_util.get_config") as mock_config:
            mock_config.return_value = {"JIRA_URL": "https://example.atlassian.net"}
            mock_request.side_effect = [
                # 1st call: issue type query
                {"fields": {"issuetype": {"subtask": True}}},
                # 2nd call: transitions query
                {"transitions": [{"id": "4", "name": "Freigabe", "to": {"name": "Erledigt"}}]},
                # 3rd call: post transition
                {}
            ]
            jira_util.transition_issue("ATT-9999", "freigabe", role="agent1")
            self.assertEqual(mock_request.call_count, 3)
            post_call = mock_request.call_args_list[2]
            self.assertEqual(post_call[1].get("payload"), {"transition": {"id": "4"}})

    def test_parent_ticket_final_review_transitions_and_assigns_human(self):
        """Parent tickets in Test transition to Final Review (Human) and assign to human."""
        with patch("jira_util.jira_request") as mock_request, patch("jira_util.get_config") as mock_config, patch("jira_util.assign_issue") as mock_assign:
            mock_config.return_value = {"JIRA_URL": "https://example.atlassian.net"}
            mock_request.side_effect = [
                # 1st call: issue type query (parent ticket)
                {"fields": {"issuetype": {"subtask": False}}},
                # 2nd call: transitions query
                {"transitions": [{"id": "8", "name": "Final Review", "to": {"name": "Final Review (Human)"}}]},
                # 3rd call: post transition
                {}
            ]
            jira_util.transition_issue("ATT-1508", "final review", role="agent1")
            mock_assign.assert_called_once_with("ATT-1508", "human", role="agent1")


class TestSecretMasking(unittest.TestCase):
    """Verifies REQ-PRO-021 / TST-PRO-014: Jira Credential & Token Secret Masking in Tooling."""

    def test_sanitize_output_scrubs_tokens_and_auth_headers(self):
        """Sanitization ensures tokens, passwords, and Basic Auth strings are stripped."""
        token_sample = "ATATT3xFfGF0ZK7rgYGkmCQ0dceSW52SYsfQCnaeZXsm817-QMYdEDteK1rgD9fDyILSYo49RVAnh64ypLc3gX8pe9IHZsrJLCQVyxyT76AWmCEAf5ao6ATvoaqk3l3VN_yblm8MPT-VQMRFpMfrgeVMFlfqVnA1cxdvAHCQ3Ar6I7ZehS5SCok=00583D6A"
        raw_auth_header = "Basic dGVzdF91c2VyOnNlY3JldF90b2tlbg=="
        sensitive_text = f"API Error at https://example.atlassian.net?token={token_sample} with header {raw_auth_header}"

        sanitized = jira_util.sanitize_secrets(sensitive_text, [token_sample, raw_auth_header])
        self.assertNotIn(token_sample, sanitized)
        self.assertNotIn(raw_auth_header, sanitized)
        self.assertIn("API Error at https://example.atlassian.net?token=[MASKED]", sanitized)

    def test_advisory_notice_never_prints_tokens(self):
        """Advisory messages on fallback must never contain token values."""
        config = {
            "JIRA_USER": "main@example.com",
            "JIRA_TOKEN": "SUPER_SECRET_FALLBACK_TOKEN_999",
            "JIRA_AGENT1_USER": "agent1@example.com",
            # Token missing
        }
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            with self.assertRaises(SystemExit):
                jira_util.resolve_account("agent1", config)
        output = stderr_capture.getvalue()
        self.assertNotIn("SUPER_SECRET_FALLBACK_TOKEN_999", output)


class TestLivingDocumentationAndGovernance(unittest.TestCase):
    """Verifies living documentation consistency and Coordinator governance rules."""

    def test_project_protocol_defines_coordinator_and_multi_account(self):
        """docs/project_protocol.md must document Coordinator persona, multi-account, and invariants."""
        protocol_path = os.path.join(REPO_ROOT, "docs", "project_protocol.md")
        with open(protocol_path, "r", encoding="utf-8") as f:
            content = f.read()

        self.assertIn("Coordinator", content)
        self.assertIn("JIRA_AGENT1_USER", content)
        self.assertIn("JIRA_AGENT2_USER", content)
        self.assertIn("JIRA_COORDINATOR_USER", content)
        # Verify Coordinator cannot override Auditor or move to Erledigt
        self.assertIn("cannot override", content.lower())
        self.assertIn("erledigt", content.lower())

    def test_cursorrules_defines_coordinator_and_multi_account(self):
        """.cursorrules must reflect Coordinator persona and multi-account credentials."""
        cursorrules_path = os.path.join(REPO_ROOT, ".cursorrules")
        with open(cursorrules_path, "r", encoding="utf-8") as f:
            content = f.read()

        self.assertIn("Coordinator", content)
        self.assertIn("JIRA_AGENT1_USER", content)
        self.assertIn("JIRA_AGENT2_USER", content)
        self.assertIn("JIRA_COORDINATOR_USER", content)


class TestJiraCliTooling(unittest.TestCase):
    """Verifies Jira CLI governance guards, composite commands, and context injection."""

    @patch("jira_util.jira_request")
    @patch("jira_util.get_config")
    @patch("jira_util.add_to_active_sprint")
    def test_create_subtask_rejects_fixversion_and_auto_attaches_sprint(self, mock_add_sprint, mock_config, mock_request):
        mock_config.return_value = {"JIRA_URL": "https://jira.test"}
        mock_request.return_value = {"key": "ATT-9999"}

        with self.assertRaises(SystemExit) as cm:
            jira_util.create_subtask(
                "ATT-1000",
                "Subtask Summary",
                "Subtask Description",
                role="coordinator",
                add_to_sprint=True,
                fix_version="V4.9.38"
            )
        self.assertEqual(cm.exception.code, 1)

        key = jira_util.create_subtask(
            "ATT-1000",
            "Subtask Summary",
            "Subtask Description",
            role="coordinator",
            add_to_sprint=True
        )
        self.assertEqual(key, "ATT-9999")
        mock_add_sprint.assert_called_once_with("ATT-9999", role="coordinator")

    @patch("jira_util.jira_request")
    @patch("jira_util.get_config")
    def test_create_issue_rejects_sprint_assignment_and_sets_fixversion(self, mock_config, mock_request):
        mock_config.return_value = {"JIRA_URL": "https://jira.test"}

        def side_effect(path, method="GET", **kwargs):
            if method == "GET" and path.startswith("issue/ATT-500"):
                return {"fields": {"issuetype": {"name": "Epic"}}}
            return {"key": "ATT-8888"}

        mock_request.side_effect = side_effect

        with self.assertRaises(SystemExit) as cm:
            jira_util.create_issue(
                "Issue Summary",
                "Issue Description",
                parent_key="ATT-500",
                role="agent1",
                add_to_sprint=True
            )
        self.assertEqual(cm.exception.code, 1)

        key = jira_util.create_issue(
            "Issue Summary",
            "Issue Description",
            parent_key="ATT-500",
            role="agent1",
            add_to_sprint=False,
            fix_version="V4.9.38"
        )
        self.assertEqual(key, "ATT-8888")

    @patch("jira_util.transition_issue")
    @patch("jira_util.update_issue_description")
    @patch("jira_util.jira_request")
    @patch("jira_util.get_config")
    def test_submit_stage_reuses_existing_subtask(
        self, mock_config, mock_request, mock_update_desc, mock_transition
    ):
        mock_config.return_value = {"JIRA_URL": "https://jira.test"}
        mock_request.return_value = {
            "fields": {
                "summary": "Fix crash on startup",
                "status": {"name": "Analysis"},
                "subtasks": [
                    {
                        "key": "ATT-4301",
                        "fields": {
                            "summary": "[Analysis] Fix crash on startup",
                            "status": {"name": "In Bearbeitung"}
                        }
                    }
                ]
            }
        }
        subtask_key = jira_util.submit_stage(
            "ATT-4300",
            "1",
            "# Updated Stage 1 Analysis",
            role="agent1"
        )
        self.assertEqual(subtask_key, "ATT-4301")
        mock_update_desc.assert_called_once_with("ATT-4301", "# Updated Stage 1 Analysis", role="agent1")
        mock_transition.assert_called_once_with("ATT-4301", "in_review", role="agent1", inject_context=False)

    @patch("jira_util.transition_issue")
    @patch("jira_util.assign_issue")
    @patch("jira_util.add_comment")
    def test_gate_verdict_pass_advances_parent_stage(
        self, mock_comment, mock_assign, mock_transition
    ):
        report_text = "PASS: Verified in tools/jira_util.py:100 [x] commit 3a31b9ad"
        jira_util.gate_verdict("ATT-4301", "PASS", report_text, role="agent2")
        mock_assign.assert_called_once_with("ATT-4301", "agent2", role="agent2")
        mock_comment.assert_called_once_with("ATT-4301", report_text, role="agent2")
        mock_transition.assert_called_once_with("ATT-4301", "freigabe", role="agent2", inject_context=True)

    def test_auto_verify_living_docs_on_stage5(self):
        """TST-PRO-099.4: auto_verify_living_docs_on_stage5 promotes backed items and keeps unbacked items Implemented."""
        import tempfile
        with tempfile.TemporaryDirectory() as tmpdir:
            docs_dir = os.path.join(tmpdir, "docs")
            test_dir = os.path.join(tmpdir, "app", "src", "test")
            os.makedirs(docs_dir, exist_ok=True)
            os.makedirs(test_dir, exist_ok=True)
            with open(os.path.join(test_dir, "UiWidgetTest.kt"), "w", encoding="utf-8") as f:
                f.write("// TST-UI-001\n")
            req_path = os.path.join(docs_dir, "requirements.md")
            tst_path = os.path.join(docs_dir, "tests.md")
            with open(req_path, "w", encoding="utf-8") as f:
                f.write(
                    "| **REQ-UI-001** | `ATT-4400` | Desc | Crit | Status: Planned (ATT-4450) |\n"
                    "| **REQ-UI-002** | `ATT-4450` | Desc | Crit | Planned |\n"
                    "| **REQ-UI-003** | `ATT-4399` | Desc | Crit | Verified |\n"
                )
            with open(tst_path, "w", encoding="utf-8") as f:
                f.write(
                    "| **TST-UI-001** | `ATT-4450` | Desc | `REQ-UI-001` | Proc | Res | Planned (ATT-4450) |\n"
                )
            count = jira_util.auto_verify_living_docs_on_stage5("ATT-4450", repo_root=tmpdir)
            self.assertEqual(count, 3)
            with open(req_path, "r", encoding="utf-8") as f:
                req_content = f.read()
            with open(tst_path, "r", encoding="utf-8") as f:
                tst_content = f.read()
            self.assertNotIn("Planned", req_content)
            # Unbacked requirements remain/flip to Implemented under REQ-PRO-099.4
            self.assertIn("Implemented", req_content)
            self.assertNotIn("Planned", tst_content)
            # TST-UI-001 has test code backing in tmpdir, so it promotes to Verified
            self.assertIn("| Verified |", tst_content)

    def test_stage5_evidence_gating_unbacked_and_backed(self):
        """TST-PRO-099.4: Verify auto_verify_living_docs_on_stage5 conditions Verified on passing test evidence."""
        import tempfile
        with tempfile.TemporaryDirectory() as tmpdir:
            shards_req = os.path.join(tmpdir, "docs", "requirements")
            shards_tst = os.path.join(tmpdir, "docs", "tests")
            os.makedirs(shards_req, exist_ok=True)
            os.makedirs(shards_tst, exist_ok=True)

            tst_shard_file = os.path.join(shards_tst, "tst_test.md")
            with open(tst_shard_file, "w", encoding="utf-8") as f:
                f.write(
                    "### TST-NOVEL-999: Novel Test Without Evidence [Planned]\n"
                    "- **Tickets**: `ATT-8888`\n"
                    "- **Requirements**: `REQ-NOVEL-999`\n"
                )

            tst_backed_file = os.path.join(shards_tst, "tst_backed.md")
            with open(tst_backed_file, "w", encoding="utf-8") as f:
                f.write(
                    "### TST-PRO-099: Backed Test [Planned]\n"
                    "- **Tickets**: `ATT-8888`\n"
                    "- **Requirements**: `REQ-PRO-099`\n"
                )

            with patch("jira_util.has_verified_test_evidence") as mock_evidence:
                def side_effect(tst_id, **kwargs):
                    if tst_id == "TST-PRO-099":
                        return True, "Passing evidence"
                    return False, "Unbacked"
                mock_evidence.side_effect = side_effect

                jira_util.auto_verify_living_docs_on_stage5("ATT-8888", repo_root=tmpdir)

            with open(tst_shard_file, "r", encoding="utf-8") as f:
                novel_text = f.read()
            with open(tst_backed_file, "r", encoding="utf-8") as f:
                backed_text = f.read()

            self.assertIn("### TST-NOVEL-999: Novel Test Without Evidence [Implemented]", novel_text)
            self.assertIn("### TST-PRO-099: Backed Test [Verified]", backed_text)

    @patch("jira_util.jira_request")
    def test_export_gates_command_generates_markdown(self, mock_request):
        """TST-PRO-059.1: export-gates CLI extracts verdicts and writes structured markdown."""
        import tempfile

        def mock_jira_side_effect(url, method="GET", payload=None, role=None, **kwargs):
            if "/rest/agile/1.0/board" in url and "sprint" not in url:
                return {"values": [{"id": 1, "name": "Sprint Board"}]}
            elif "/rest/agile/1.0/board/1/sprint?state=active" in url:
                return {"values": [{"id": 440, "name": "2026-46.3"}]}
            elif "/rest/agile/1.0/sprint/440/issue" in url:
                return {
                    "issues": [
                        {
                            "key": "ATT-6149",
                            "fields": {
                                "summary": "Export Jira gate records",
                                "status": {"name": "Implementation"},
                                "issuetype": {"subtask": False},
                                "comment": {
                                    "comments": [
                                        {
                                            "body": "[Automated comment by AI Agent 2 (Auditor)]\n\n[FINAL REVIEW (AGENT) — PASS]\nStage 6 Cold Review passed.",
                                            "author": {"displayName": "Agent 2 (Reviewer)", "accountId": "agent2_id"},
                                            "created": "2026-11-12T03:00:00.000+0100",
                                        }
                                    ]
                                },
                                "subtasks": [
                                    {
                                        "key": "ATT-7247",
                                        "fields": {
                                            "summary": "[Analysis] Export gate records",
                                            "status": {"name": "Erledigt"},
                                        },
                                        "comments": [
                                            {
                                                "body": "[Automated comment by AI Agent 2 (Auditor)]\n\nGATE_VERDICT: PASS\nGate 1 Audit verified.",
                                                "author": {"displayName": "Agent 2 (Reviewer)", "accountId": "agent2_id"},
                                                "created": "2026-11-12T02:00:00.000+0100",
                                            }
                                        ],
                                    }
                                ],
                            },
                        }
                    ],
                    "total": 1,
                }
            elif "/comment" in url:
                return {"comments": []}
            return {}

        mock_request.side_effect = mock_jira_side_effect

        with tempfile.TemporaryDirectory() as tmpdir:
            out_file = os.path.join(tmpdir, "docs", "engineering", "gates", "sprint_2026-46.3.md")
            res_path, records = jira_util.export_gates(sprint_arg=None, out_path=out_file, role="coordinator")
            self.assertEqual(res_path, out_file)
            self.assertTrue(os.path.exists(out_file))
            self.assertEqual(len(records), 2)

            with open(out_file, "r", encoding="utf-8") as f:
                content = f.read()

            self.assertIn("# Sprint Quality Gate Records: 2026-46.3", content)
            self.assertIn("**Sprint ID**: `440`", content)
            self.assertIn("Gate 1 | Analysis (Gate 1)", content)
            self.assertIn("Gate 6 | Final Review (Agent) (Gate 6)", content)
            self.assertIn("| ATT-6149 | ATT-7247 | Gate 1 | Analysis (Gate 1) | PASS | agent2 | Agent 2 (Reviewer) |", content)
            self.assertIn("| ATT-6149 | - | Gate 6 | Final Review (Agent) (Gate 6) | PASS | agent2 | Agent 2 (Reviewer) |", content)

    @patch("jira_util.jira_request")
    @patch("jira_util.get_config")
    def test_export_test_evidence_command_generates_files(self, mock_config, mock_request):
        """TST-PRO-100.1 / TST-PRO-100.4: export-test-evidence CLI exports persistent test and coverage evidence."""
        import tempfile
        mock_config.return_value = {
            "JIRA_URL": "https://example.atlassian.net",
            "JIRA_USER": "test@example.com",
            "JIRA_API_TOKEN": "token",
        }
        mock_request.side_effect = lambda url, **kwargs: (
            {"values": [{"id": 1, "name": "Sprint Board"}]} if "/rest/agile/1.0/board" in url and "sprint" not in url
            else ({"values": [{"id": 440, "name": "2026-46.3"}]} if "/rest/agile/1.0/board/1/sprint?state=active" in url
            else {})
        )
        with tempfile.TemporaryDirectory() as tmp_root:
            out_dir = os.path.join(tmp_root, "test_evidence")
            results_dir = os.path.join(tmp_root, "test-results")
            os.makedirs(results_dir, exist_ok=True)
            with open(os.path.join(results_dir, "TEST-Sample.xml"), "w", encoding="utf-8") as f:
                f.write('<?xml version="1.0"?><testsuite tests="1" failures="0" errors="0" skipped="0" time="0.1"><testcase name="testA_TST_PRO_100" classname="Sample"/></testsuite>')

            md_path, json_path, payload = jira_util.export_test_evidence(
                sprint_arg="2026-46.3",
                out_dir=out_dir,
                test_results_dir=results_dir,
            )
            self.assertTrue(os.path.isfile(md_path))
            self.assertTrue(os.path.isfile(json_path))
            self.assertEqual(payload["target"], "sprint_2026-46.3")
            self.assertEqual(payload["unit_tests"]["total"], 1)

    @patch("jira_util.jira_request")
    def test_qa_audit_command_generates_markdown(self, mock_request):
        """TST-PRO-061.1: Verify qa-audit CLI command generates structured report for >= 2 tickets."""
        stages = [
            (1, "[Analysis]"),
            (2, "[Req & Test Spec]"),
            (3, "[Impl-Plan]"),
            (4, "[Implementation]"),
            (5, "[Test]"),
        ]

        def make_subtasks(prefix, parent_key):
            return [
                {
                    "key": f"{prefix}-{g_idx}",
                    "fields": {
                        "summary": f"{name} {parent_key}",
                        "status": {"name": "Erledigt"},
                    },
                }
                for g_idx, name in stages
            ]

        def mock_jira_side_effect(url, method="GET", payload=None, role="coordinator"):
            if "/rest/agile/1.0/board" in url and "/sprint" not in url:
                return {"values": [{"id": 1, "name": "ATT Board"}]}
            elif "/rest/agile/1.0/board/1/sprint?state=active" in url:
                return {"values": [{"id": 440, "name": "2026-46.3", "state": "active"}]}
            elif "/rest/agile/1.0/sprint/440/issue" in url:
                return {
                    "issues": [
                        {
                            "key": "ATT-6149",
                            "fields": {
                                "summary": "Export Jira gate records REQ-PRO-059",
                                "description": "Addresses REQ-PRO-059.1",
                                "status": {"name": "Final Review (Human)"},
                                "issuetype": {"subtask": False},
                                "subtasks": make_subtasks("ATT-724", "ATT-6149"),
                            },
                        },
                        {
                            "key": "ATT-6150",
                            "fields": {
                                "summary": "Record story points and sprint metrics REQ-PRO-060",
                                "description": "Addresses REQ-PRO-060.1",
                                "status": {"name": "Final Review (Human)"},
                                "issuetype": {"subtask": False},
                                "subtasks": make_subtasks("ATT-725", "ATT-6150"),
                            },
                        },
                    ],
                    "total": 2,
                }
            elif "/comment" in url:
                return {
                    "comments": [
                        {
                            "body": "[Automated comment by AI Agent 2 (Reviewer)]\n\n[ROLE: agent2]\nGATE_VERDICT: PASS recorded by agent2.",
                            "author": {"displayName": "AI Agent 2"},
                        }
                    ]
                }
            return {}

        mock_request.side_effect = mock_jira_side_effect

        with tempfile.TemporaryDirectory() as tmpdir:
            # 1. Setup git repo and commits
            import subprocess
            subprocess.run(["git", "init"], cwd=tmpdir, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "config", "user.name", "Tester"], cwd=tmpdir, check=True)
            subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=tmpdir, check=True)
            subprocess.run(["git", "commit", "--allow-empty", "-m", "ATT-6149: Export Jira gate records"], cwd=tmpdir, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "commit", "--allow-empty", "-m", "ATT-6150: Record story points and sprint metrics"], cwd=tmpdir, check=True, stdout=subprocess.DEVNULL)

            # 2. Setup deliverables
            for k in ["ATT-6149", "ATT-6150"]:
                for subfolder, suffix in [
                    ("analysis", "_analysis.md"),
                    ("test_specs", "_test_spec.md"),
                    ("plans", "_plan.md"),
                    ("walkthroughs", "_walkthrough.md"),
                ]:
                    d = os.path.join(tmpdir, "docs", "engineering", subfolder)
                    os.makedirs(d, exist_ok=True)
                    with open(os.path.join(d, f"{k}{suffix}"), "w", encoding="utf-8") as f:
                        f.write(f"# Deliverable for {k}")

            # 3. Setup living docs master table
            os.makedirs(os.path.join(tmpdir, "docs"), exist_ok=True)
            with open(os.path.join(tmpdir, "docs", "requirements.md"), "w", encoding="utf-8") as f:
                f.write("""| Requirement ID | Summary | Target | Status | Jira Ticket |
| :--- | :--- | :--- | :--- | :--- |
| REQ-PRO-059 | Gate records | Tool | Verified | ATT-6149 |
| REQ-PRO-060 | Metrics | Tool | Verified | ATT-6150 |
""")

            out_file = os.path.join(tmpdir, "docs", "engineering", "qa_audits", "sprint_2026-46.3.md")
            res_path, results = jira_util.qa_audit(sprint_arg=None, sample_size=2, out_path=out_file, role="qa", repo_root=tmpdir)
            self.assertEqual(res_path, out_file)
            self.assertTrue(os.path.exists(out_file))
            self.assertEqual(len(results), 2)
            self.assertTrue(all(r["compliant"] for r in results))

            with open(out_file, "r", encoding="utf-8") as f:
                content = f.read()

            self.assertIn("# Sprint QA Process-Compliance Audit: Sprint 2026-46.3", content)
            self.assertIn("Overall Process Verdict", content)
            self.assertIn("Auditor Role", content)
            self.assertIn("`qa`", content)
            self.assertIn("ATT-6149", content)
            self.assertIn("ATT-6150", content)
            self.assertIn("COMPLIANT", content)

    @patch("jira_util.jira_request")
    def test_qa_audit_rejects_non_compliant_tickets(self, mock_request):
        """TST-PRO-061.1: Verify qa-audit flags missing deliverables, incomplete gates, role violation, and missing commits."""
        def mock_jira_side_effect(url, method="GET", payload=None, role="coordinator"):
            if "/rest/agile/1.0/board" in url and "/sprint" not in url:
                return {"values": [{"id": 1, "name": "ATT Board"}]}
            elif "/rest/agile/1.0/board/1/sprint?state=active" in url:
                return {"values": [{"id": 440, "name": "2026-46.3", "state": "active"}]}
            elif "/rest/agile/1.0/sprint/440/issue" in url:
                return {
                    "issues": [
                        {
                            "key": "ATT-9001",
                            "fields": {
                                "summary": "Ticket with 1 subtask and agent1 verdict",
                                "status": {"name": "Final Review (Human)"},
                                "issuetype": {"subtask": False},
                                "subtasks": [
                                    {"key": "ATT-9002", "fields": {"summary": "[Analysis] Task", "status": {"name": "In Bearbeitung"}}}
                                ],
                            },
                        },
                        {
                            "key": "ATT-9003",
                            "fields": {
                                "summary": "Ticket without deliverables or commits",
                                "status": {"name": "Final Review (Human)"},
                                "issuetype": {"subtask": False},
                                "subtasks": [],
                            },
                        },
                    ],
                    "total": 2,
                }
            elif "/comment" in url:
                return {
                    "comments": [
                        {
                            "body": "[Automated comment by AI Agent 1 (Implementer)]\n\n[ROLE: agent1]\nGATE_VERDICT: PASS recorded by agent1.",
                            "author": {"displayName": "AI Agent 1"},
                        }
                    ]
                }
            return {}

        mock_request.side_effect = mock_jira_side_effect

        with tempfile.TemporaryDirectory() as tmpdir:
            out_file = os.path.join(tmpdir, "docs", "engineering", "qa_audits", "sprint_2026-46.3.md")
            res_path, results = jira_util.qa_audit(sprint_arg=None, sample_size=2, out_path=out_file, role="qa", repo_root=tmpdir)
            self.assertEqual(len(results), 2)
            # Both tickets should be NON-COMPLIANT
            self.assertFalse(results[0]["compliant"])
            self.assertFalse(results[0]["gate_pass"])       # only 1 subtask, In Bearbeitung
            self.assertFalse(results[0]["role_sep_pass"])   # agent1 recorded verdict!
            self.assertFalse(results[0]["deliv_pass"])      # deliverables missing
            self.assertFalse(results[0]["commit_pass"])     # no commit

            self.assertFalse(results[1]["compliant"])
            self.assertFalse(results[1]["gate_pass"])       # 0 subtasks

            with open(out_file, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("NON-COMPLIANT", content)
            self.assertIn("Corrective Action Ticket:", content)
            self.assertIn("**ATT-9001**:", content)
            self.assertIn("**ATT-9003**:", content)
            self.assertIn("[ATT-CA-", content)

    @patch("jira_util.jira_request")
    def test_qa_audit_enforces_independent_role(self, mock_request):
        """TST-PRO-091.1: Verify qa-audit rejects coordinator and agent1 roles, allowing qa and agent2."""
        with tempfile.TemporaryDirectory() as tmpdir:
            out_file = os.path.join(tmpdir, "docs", "engineering", "qa_audits", "sprint_test.md")
            # 1. coordinator role rejected
            with self.assertRaises(ValueError) as cm:
                jira_util.qa_audit(role="coordinator", out_path=out_file, repo_root=tmpdir)
            self.assertIn("Auditor role cannot be 'coordinator'", str(cm.exception))

            # 2. agent1 role rejected
            with self.assertRaises(ValueError) as cm2:
                jira_util.qa_audit(role="agent1", out_path=out_file, repo_root=tmpdir)
            self.assertIn("Auditor role cannot be 'agent1'", str(cm2.exception))

    @patch("jira_util.jira_request")
    def test_qa_audit_dynamic_sample_size(self, mock_request):
        """TST-PRO-091.2: Verify qa-audit dynamically scales sample size to >= max(2, ceil(N * 0.10))."""
        def mock_jira_side_effect(url, method="GET", payload=None, role="qa"):
            if "/rest/agile/1.0/board" in url and "/sprint" not in url:
                return {"values": [{"id": 1, "name": "ATT Board"}]}
            elif "/rest/agile/1.0/board/1/sprint?state=active" in url:
                return {"values": [{"id": 440, "name": "2026-46.3", "state": "active"}]}
            elif "/rest/agile/1.0/sprint/440/issue" in url:
                issues = [
                    {
                        "key": f"ATT-{7000 + idx}",
                        "fields": {
                            "summary": f"Sprint task {idx}",
                            "status": {"name": "Final Review (Human)"},
                            "issuetype": {"subtask": False},
                            "subtasks": [],
                        },
                    }
                    for idx in range(25)
                ]
                return {"issues": issues, "total": 25}
            return {}

        mock_request.side_effect = mock_jira_side_effect

        with tempfile.TemporaryDirectory() as tmpdir:
            out_file = os.path.join(tmpdir, "docs", "engineering", "qa_audits", "sprint_2026-46.3.md")
            res_path, results = jira_util.qa_audit(sprint_arg=None, sample_size=None, out_path=out_file, role="qa", repo_root=tmpdir)
            # 25 * 0.10 = 2.5 -> ceil is 3. Max(2, 3) = 3 tickets sampled
            self.assertEqual(len(results), 3)
            with open(out_file, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("3 / 25 tickets (>= 10% dynamic threshold enforced)", content)

    @patch("jira_util.jira_request")
    def test_record_review_command_generates_markdown(self, mock_request):
        """TST-PRO-062.1: Verify record-review generates structured review markdown for Human and AI reviews."""
        def mock_jira_side_effect(url, method="GET", payload=None, role="coordinator"):
            if "/rest/agile/1.0/board" in url and "/sprint" not in url:
                return {"values": [{"id": 1, "name": "ATT Board"}]}
            elif "/rest/agile/1.0/board/1/sprint?state=active" in url:
                return {"values": [{"id": 440, "name": "2026-46.3", "state": "active"}]}
            elif "/rest/agile/1.0/sprint/440/issue" in url:
                return {
                    "issues": [
                        {"key": "ATT-6150", "fields": {"summary": "Issue 1", "issuetype": {"subtask": False}}},
                        {"key": "ATT-6154", "fields": {"summary": "Issue 2", "issuetype": {"subtask": False}}},
                    ],
                    "total": 2,
                }
            return {}

        mock_request.side_effect = mock_jira_side_effect

        with tempfile.TemporaryDirectory() as tmpdir:
            # 1. Human Ceremony 2 Review
            out_human = os.path.join(tmpdir, "docs", "engineering", "reviews", "sprint_2026-46.3.md")
            path1, meta1 = jira_util.record_review(
                sprint_arg=None,
                ticket_key="ATT-6150,ATT-6154",
                reviewer="Human",
                verdict="PASS",
                findings="Sprint tickets accepted during Ceremony 2.",
                out_path=out_human,
                repo_root=tmpdir,
            )
            self.assertEqual(path1, out_human)
            self.assertTrue(os.path.exists(out_human))
            self.assertEqual(meta1["reviewer"], "Human")
            self.assertEqual(meta1["review_type"], "Human Sprint Review (Ceremony 2)")

            with open(out_human, "r", encoding="utf-8") as f:
                content_h = f.read()

            self.assertIn("# Independent QA Review Record: Sprint 2026-46.3", content_h)
            self.assertIn("**Reviewer**: Human", content_h)
            self.assertIn("**Review Type**: Human Sprint Review (Ceremony 2)", content_h)
            self.assertIn("**Vendor**: Human", content_h)
            self.assertIn("**Scope**: Ceremony 2 Sprint Review", content_h)
            self.assertIn("**Verdict**: **PASS**", content_h)
            self.assertIn("ATT-6150", content_h)
            self.assertIn("ATT-6154", content_h)
            self.assertIn("Sprint tickets accepted during Ceremony 2.", content_h)

            # 2. Cross-Vendor AI Review (Layer 5)
            out_ai = os.path.join(tmpdir, "docs", "engineering", "reviews", "layer5_claude.md")
            path2, meta2 = jira_util.record_review(
                sprint_arg="2026-46.3",
                ticket_key="ATT-6155",
                reviewer="Anthropic Claude",
                vendor="Anthropic",
                model="claude-3-7-sonnet",
                version="20250219",
                scope="Layer 5 Cross-Vendor Audit",
                verdict="PASS",
                findings="ASPICE compliance and role integrity verified.",
                out_path=out_ai,
                repo_root=tmpdir,
            )
            self.assertEqual(path2, out_ai)
            self.assertTrue(os.path.exists(out_ai))
            self.assertEqual(meta2["vendor"], "Anthropic")
            self.assertEqual(meta2["model"], "claude-3-7-sonnet")
            self.assertEqual(meta2["version"], "20250219")

            with open(out_ai, "r", encoding="utf-8") as f:
                content_ai = f.read()

            self.assertIn("# Independent QA Review Record: Sprint 2026-46.3", content_ai)
            self.assertIn("**Reviewer**: Anthropic Claude", content_ai)
            self.assertIn("**Review Type**: Cross-Vendor AI Review (Layer 5)", content_ai)
            self.assertIn("**Vendor**: Anthropic", content_ai)
            self.assertIn("**Model & Version**: claude-3-7-sonnet (v20250219)", content_ai)
            self.assertIn("**Scope**: Layer 5 Cross-Vendor Audit", content_ai)
            self.assertIn("**Verdict**: **PASS**", content_ai)
            self.assertIn("ATT-6155", content_ai)
            self.assertIn("ASPICE compliance and role integrity verified.", content_ai)

    @patch("jira_util.jira_request")
    def test_record_review_validation_and_skipped_handling(self, mock_request):
        """TST-PRO-062.1: Verify record-review validates verdicts, mandates findings, and handles SKIPPED/NOT PERFORMED."""
        mock_request.return_value = {}
        with tempfile.TemporaryDirectory() as tmpdir:
            # 1. Missing verdict raises ValueError
            with self.assertRaises(ValueError):
                jira_util.record_review(repo_root=tmpdir, verdict=None, findings="notes")

            # 2. Invalid verdict raises ValueError
            with self.assertRaises(ValueError):
                jira_util.record_review(repo_root=tmpdir, verdict="INVALID_VERDICT", findings="notes")

            # 3. Missing findings for PASS raises ValueError
            with self.assertRaises(ValueError):
                jira_util.record_review(repo_root=tmpdir, verdict="PASS", findings=None)
            with self.assertRaises(ValueError):
                jira_util.record_review(repo_root=tmpdir, verdict="PASS", findings="")

            # 4. Missing findings for FAIL raises ValueError
            with self.assertRaises(ValueError):
                jira_util.record_review(repo_root=tmpdir, verdict="FAIL", findings="")

            # 5. Missing findings for SKIPPED raises ValueError
            with self.assertRaises(ValueError):
                jira_util.record_review(repo_root=tmpdir, verdict="SKIPPED", findings="")

            # 6. Valid SKIPPED review generates Rule 1.3 deviation header and omits default success text
            out_skip = os.path.join(tmpdir, "docs", "engineering", "reviews", "sprint_2026-46.6.md")
            path_s, meta_s = jira_util.record_review(
                sprint_arg="2026-46.6",
                ticket_key="ATT-7755",
                reviewer="Human",
                scope="Ceremony 2 Sprint Review (Skipped per PO instruction)",
                verdict="SKIPPED",
                findings="Individual ticket review skipped per PO instruction to proceed directly to Retro.",
                out_path=out_skip,
                repo_root=tmpdir,
            )
            self.assertEqual(path_s, out_skip)
            with open(out_skip, "r", encoding="utf-8") as f:
                content_s = f.read()

            self.assertIn("**Verdict**: **SKIPPED**", content_s)
            self.assertIn("[PROCESS DEVIATION — Rule 1.3]", content_s)
            self.assertIn("Individual ticket review skipped per PO instruction", content_s)
            self.assertNotIn("Independent review conducted successfully", content_s)
            self.assertNotIn("Zero critical process or technical deviations", content_s)

            # 7. Valid PARTIAL review
            out_part = os.path.join(tmpdir, "docs", "engineering", "reviews", "partial.md")
            path_p, _ = jira_util.record_review(
                sprint_arg="2026-46.8",
                ticket_key="ATT-8142",
                reviewer="Human",
                verdict="PARTIAL",
                findings="Partial review conducted for high-risk items only.",
                out_path=out_part,
                repo_root=tmpdir,
            )
            with open(out_part, "r", encoding="utf-8") as f:
                content_p = f.read()
            self.assertIn("**Verdict**: **PARTIAL**", content_p)
            self.assertIn("Partial review conducted for high-risk items only.", content_p)

    @patch("jira_util.rank_at_top_of_backlog")
    @patch("jira_util.jira_request")
    def test_create_issue_requires_severity_for_bugs(self, mock_request, mock_rank):
        """TST-PRO-063.1: Verify create_issue mandates severity for bugs and passes non-bugs."""
        mock_request.return_value = {"key": "ATT-9999"}

        # 1. Bug without severity -> SystemExit(1)
        with self.assertRaises(SystemExit) as cm:
            jira_util.create_issue("Bug without severity", "Description", issuetype_id="10006")
        self.assertEqual(cm.exception.code, 1)

        # 2. Bug with invalid severity -> SystemExit(1)
        with self.assertRaises(SystemExit) as cm:
            jira_util.create_issue("Bug with invalid severity", "Description", issuetype_id="10006", severity="catastrophic")
        self.assertEqual(cm.exception.code, 1)

        # 3. Bug with valid severity -> succeeds and tags severity label
        mock_request.reset_mock()
        key = jira_util.create_issue("Bug with critical severity", "Description", issuetype_id="10006", severity="critical")
        self.assertEqual(key, "ATT-9999")
        posted_fields = mock_request.call_args[1]["payload"]["fields"]
        self.assertIn("severity-critical", posted_fields["labels"])
        self.assertIn("**Severity**: Critical", posted_fields["description"])

        # 4. Non-bug (Feature 10007) without severity -> succeeds
        mock_request.reset_mock()
        key_feat = jira_util.create_issue("Feature without severity", "Description", issuetype_id="10007")
        self.assertEqual(key_feat, "ATT-9999")
        posted_feat = mock_request.call_args[1]["payload"]["fields"]
        self.assertNotIn("labels", posted_feat)

    @patch("jira_util.rank_at_top_of_backlog")
    @patch("jira_util.jira_request")
    def test_bug_severity_rubric_enforcement(self, mock_request, mock_rank):
        """TST-PRO-092.6: create_issue enforces Bug Severity Rubric for crash/ANR origin bugs."""
        mock_request.return_value = {"key": "ATT-9999"}

        # 1. Bug mentioning "crash" with severity=minor -> rejected with exit code 1
        with self.assertRaises(SystemExit) as cm:
            jira_util.create_issue("Fatal crash on startup", "App terminates immediately", issuetype_id="10006", severity="minor")
        self.assertEqual(cm.exception.code, 1)

        # 2. Bug mentioning "ANR" with severity=cosmetic -> rejected with exit code 1
        with self.assertRaises(SystemExit) as cm:
            jira_util.create_issue("UI freezing during workout", "ANR dialog triggered", issuetype_id="10006", severity="cosmetic")
        self.assertEqual(cm.exception.code, 1)

        # 3. Bug mentioning "crash" with severity=major -> accepted
        mock_request.reset_mock()
        key_major = jira_util.create_issue("Crash in background export", "Occasional crash during export", issuetype_id="10006", severity="major")
        self.assertEqual(key_major, "ATT-9999")
        posted = mock_request.call_args[1]["payload"]["fields"]
        self.assertIn("severity-major", posted["labels"])

        # 4. Bug mentioning "ANR" with severity=critical -> accepted
        mock_request.reset_mock()
        key_crit = jira_util.create_issue("Main loop ANR", "Fatal ANR blocks workout", issuetype_id="10006", severity="critical")
        self.assertEqual(key_crit, "ATT-9999")
        posted_crit = mock_request.call_args[1]["payload"]["fields"]
        self.assertIn("severity-critical", posted_crit["labels"])

    @patch("jira_util.transition_issue")
    @patch("jira_util.assign_issue")
    @patch("jira_util.add_comment")
    @patch("jira_util.get_subtask_summary_and_desc")
    def test_gate_verdict_enforces_impact_analysis_on_analysis_subtasks(
        self, mock_get_subtask, mock_comment, mock_assign, mock_transition
    ):
        """TST-PRO-064.3: Verify gate_verdict enforces Change-Request Impact Analysis on [Analysis] subtasks."""
        # 1. [Analysis] subtask with missing Impact Analysis -> SystemExit(1)
        mock_get_subtask.return_value = ("[Analysis] ATT-4300: Problem Domain Analysis", "No impact analysis here.", "ATT-4300")
        with self.assertRaises(SystemExit) as cm:
            jira_util.gate_verdict("ATT-4301", "PASS", "Gate 1 PASS: [x] tools/jira_util.py:100", role="agent2")
        self.assertEqual(cm.exception.code, 1)

        # 2. [Analysis] subtask with valid Impact Analysis -> succeeds and transitions
        valid_desc = """
# Stage 1 Analysis
## Change Impact Analysis
* **Affected Requirements**: REQ-PRO-064
* **Affected Architecture Elements**: tools/jira_util.py
* **Verification Impact & Test Surface**: TST-PRO-064
* **Risk Assessment & Invariants**: Low risk
"""
        mock_get_subtask.return_value = ("[Analysis] ATT-4300: Problem Domain Analysis", valid_desc, "ATT-4300")
        jira_util.gate_verdict("ATT-4301", "PASS", "Gate 1 PASS: [x] tools/jira_util.py:100", role="agent2")
        mock_transition.assert_called_with("ATT-4301", "freigabe", role="agent2", inject_context=True)

        # 3. Non-analysis subtask (e.g. [Req & Test Spec]) without impact analysis -> succeeds normally
        mock_transition.reset_mock()
        mock_get_subtask.return_value = ("[Req & Test Spec] ATT-4300: Test Spec", "Test spec details without impact section", "ATT-4300")
        jira_util.gate_verdict("ATT-4302", "PASS", "Gate 2 PASS: [x] tools/jira_util.py:100", role="agent2")
        mock_transition.assert_called_with("ATT-4302", "freigabe", role="agent2", inject_context=True)

    @patch("jira_util.get_config", return_value={"JIRA_URL": "https://example.atlassian.net"})
    @patch("jira_util.jira_request")
    def test_po_queue_447_tamper_protection_and_bin_packing(self, mock_request, mock_config):
        """ATT-8030: Enforce Vom PO Freigegeben (ID 447) tamper guard, cutoff return, greedy bin-packing & swap."""
        # 1. Pushing Backlog tickets into ID 447 must exit(1) even for coordinator
        with self.assertRaises(SystemExit) as cm:
            jira_util.move_issues_to_sprint_guarded(447, ["ATT-9001"], role="coordinator", cutoff_return=False)
        self.assertEqual(cm.exception.code, 1)
        mock_request.assert_not_called()

        # 2. agent1/agent2 cannot move sprint memberships at all
        with self.assertRaises(SystemExit):
            jira_util.move_issues_to_sprint_guarded(448, ["ATT-9001"], role="agent1")

        # 3. Greedy bin-packing selects strictly in rank order across disjoint Epics up to max_lanes
        ranked = [
            {"key": "ATT-1", "fields": {"issuetype": {"name": "Bug"}, "summary": "A1", "status": {"name": "Zu erledigen"}, "parent": {"key": "EPIC-A"}}},
            {"key": "ATT-2", "fields": {"issuetype": {"name": "Bug"}, "summary": "A2", "status": {"name": "Zu erledigen"}, "parent": {"key": "EPIC-A"}}},
            {"key": "ATT-3", "fields": {"issuetype": {"name": "Feature"}, "summary": "B1", "status": {"name": "Implementation"}, "parent": {"key": "EPIC-B"}}},
            {"key": "ATT-4", "fields": {"issuetype": {"name": "Feature"}, "summary": "C1", "status": {"name": "Zu erledigen"}, "parent": {"key": "EPIC-C"}}},
            {"key": "ATT-5", "fields": {"issuetype": {"name": "Feature"}, "summary": "D1", "status": {"name": "Zu erledigen"}, "parent": {"key": "EPIC-D"}}},
        ]
        packed = jira_util.select_greedy_bin_pack_tickets(ranked, max_lanes=3)
        self.assertEqual([i["key"] for i in packed], ["ATT-1", "ATT-4", "ATT-5"])

        # 4. Cutoff return-unstarted moves ONLY untouched Zu erledigen (excluding Review & Retro) to 447 and ranks top
        mock_request.side_effect = [
            {"values": [{"id": 1}]},
            {
                "issues": [
                    {"key": "ATT-10", "fields": {"issuetype": {"subtask": False}, "summary": "Untouched Bug", "status": {"name": "Zu erledigen"}}},
                    {"key": "ATT-11", "fields": {"issuetype": {"subtask": False}, "summary": "Done Item", "status": {"name": "Final Review (Human)"}}},
                    {"key": "ATT-8030", "fields": {"issuetype": {"subtask": False}, "summary": "Review & Retro - Sprint 2026-46.7", "status": {"name": "Zu erledigen"}}},
                ],
                "total": 3,
            },
            {"issues": [{"key": "ATT-200"}]},  # existing top item in 447
            {},  # POST /sprint/447/issue
            {},  # PUT /issue/rank
        ]
        with patch("jira_util.resolve_sprint_object", return_value={"id": 444, "name": "2026-46.7"}):
            returned = jira_util.return_unstarted_to_po_queue("444", role="coordinator")
        self.assertEqual(returned, ["ATT-10"])
        self.assertEqual(mock_request.call_args_list[-2][1]["payload"], {"issues": ["ATT-10"]})
        self.assertEqual(mock_request.call_args_list[-1][1]["payload"], {"issues": ["ATT-10"], "rankBeforeIssue": "ATT-200"})

        # 5. Swap future sprint keeps ID 447 adjacent to Backlog
        mock_request.reset_mock()
        mock_request.side_effect = None
        mock_request.return_value = {}
        self.assertTrue(jira_util.swap_future_sprint_keep_po_queue_last(450, role="coordinator"))
        self.assertIn("/rest/agile/1.0/sprint/450/swap", mock_request.call_args[0][0])
        self.assertEqual(mock_request.call_args[1]["payload"], {"sprintToSwapWith": 447})

    @patch("jira_util.create_issue", return_value="ATT-9100")
    @patch("jira_util.paged_jql_search", return_value=[])
    @patch("jira_util.get_config", return_value={"JIRA_URL": "https://example.atlassian.net"})
    def test_ensure_weekly_retro_creates_and_reuses_ticket(self, mock_config, mock_search, mock_create):
        """ATT-8030: Dual-ticket rolling Wochen Review & Retro creates when missing and reuses when open."""
        key1 = jira_util.ensure_weekly_retro_ticket(role="coordinator")
        self.assertEqual(key1, "ATT-9100")
        mock_create.assert_called_once()

        mock_create.reset_mock()
        mock_search.return_value = [
            {"key": "ATT-9100", "fields": {"summary": "Wochen Review & Retro - 2026-KW46", "status": {"name": "Zu erledigen"}}}
        ]
        key2 = jira_util.ensure_weekly_retro_ticket(role="coordinator")
        self.assertEqual(key2, "ATT-9100")
        mock_create.assert_not_called()

    def test_get_next_review_subsystem_unreviewed_and_rotation(self):
        """TST-PRO-095.2 / ATT-8426: Parsing coverage_matrix.md and rotating through UNREVIEWED and oldest AUDITED slices."""
        # Traces to: REQ-PRO-095.2, TST-PRO-095.2
        with tempfile.TemporaryDirectory() as tmpdir:
            matrix_file = os.path.join(tmpdir, "coverage_matrix.md")
            # 1. When UNREVIEWED rows exist, pick first UNREVIEWED
            content_unreviewed = (
                "# Matrix\n\n"
                "| Subsystem Domain | Target Path(s) | Last Audited Date | Audited Commit SHA | Gemini Model | Claude Model | Status | Active / Next Target |\n"
                "| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n"
                "| `tracker/` | `app/tracker/` | 2026-11-01 | abc | Pro | Sonnet | `AUDITED` | - |\n"
                "| `repositories/` | `app/repositories/` | - | - | - | - | `UNREVIEWED` | `NEXT` |\n"
                "| `cloud/` | `app/cloud/` | - | - | - | - | `UNREVIEWED` | - |\n"
                "| `banalservice/` | `app/banalservice/` | - | - | - | - | `UNREVIEWED` | - |\n"
                "| `database/` | `app/database/` | - | - | - | - | `UNREVIEWED` | - |\n"
            )
            with open(matrix_file, "w", encoding="utf-8") as f:
                f.write(content_unreviewed)

            domain, path, status = jira_util.get_next_review_subsystem(matrix_path=matrix_file)
            self.assertEqual(domain, "repositories/")
            self.assertEqual(status, "UNREVIEWED")

            # 2. When all are AUDITED, pick oldest date
            content_all_audited = (
                "# Matrix\n\n"
                "| Subsystem Domain | Target Path(s) | Last Audited Date | Audited Commit SHA | Gemini Model | Claude Model | Status | Active / Next Target |\n"
                "| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n"
                "| `tracker/` | `app/tracker/` | 2026-11-10 | abc | Pro | Sonnet | `AUDITED` | - |\n"
                "| `repositories/` | `app/repositories/` | 2026-11-01 | def | Pro | Sonnet | `AUDITED` | - |\n"
                "| `database/` | `app/database/` | 2026-11-05 | ghi | Pro | Sonnet | `AUDITED` | - |\n"
            )
            with open(matrix_file, "w", encoding="utf-8") as f:
                f.write(content_all_audited)

            domain2, path2, status2 = jira_util.get_next_review_subsystem(matrix_path=matrix_file)
            self.assertEqual(domain2, "repositories/")
            self.assertEqual(status2, "AUDITED")

    def test_is_weekly_review_overdue(self):
        """TST-PRO-095.4 / ATT-8426: Decoupled 7-day review currency check (not blocked by UNREVIEWED)."""
        # Traces to: REQ-PRO-095.3, REQ-PRO-095.4, TST-PRO-095.4
        with tempfile.TemporaryDirectory() as tmpdir:
            reviews_dir = os.path.join(tmpdir, "code_reviews")
            os.makedirs(reviews_dir, exist_ok=True)

            # 1. No review files -> overdue (True)
            self.assertTrue(jira_util.is_weekly_review_overdue(reviews_dir=reviews_dir, days_threshold=7))

            # 2. Recent review (2 days ago) -> not overdue (False), even when get_next_review_subsystem has UNREVIEWED slices
            two_days_ago = (datetime.now() - timedelta(days=2)).strftime("%Y-%m-%d")
            review_file = os.path.join(reviews_dir, f"{two_days_ago}_dual_vendor_code_review.md")
            with open(review_file, "w", encoding="utf-8") as f:
                f.write("# Review")

            with patch("jira_util.get_next_review_subsystem", return_value=("repositories/", "app/repositories/", "UNREVIEWED")):
                overdue = jira_util.is_weekly_review_overdue(reviews_dir=reviews_dir, days_threshold=7)
                self.assertFalse(overdue, "Recent review within 7 days must not be overdue even if slices remain UNREVIEWED")

            # 3. Old review (10 days ago) -> overdue (True)
            os.remove(review_file)
            ten_days_ago = (datetime.now() - timedelta(days=10)).strftime("%Y-%m-%d")
            with open(os.path.join(reviews_dir, f"{ten_days_ago}_dual_vendor_code_review.md"), "w") as f:
                f.write("# Review")

            overdue_10 = jira_util.is_weekly_review_overdue(reviews_dir=reviews_dir, days_threshold=7)
            self.assertTrue(overdue_10, "Review older than 7 days must be overdue")

            # 4. Non-existent directory -> overdue (True)
            self.assertTrue(jira_util.is_weekly_review_overdue(reviews_dir=os.path.join(tmpdir, "nonexistent"), days_threshold=7))

    @patch("jira_util.create_issue", return_value="ATT-9200")
    @patch("jira_util.paged_jql_search", return_value=[])
    @patch("jira_util.get_config", return_value={"JIRA_URL": "https://example.atlassian.net"})
    def test_ensure_weekly_review_creates_and_deduplicates(self, mock_config, mock_search, mock_create):
        """TST-PRO-095.3 / ATT-8426: Automated review ticket creation with generic models and Claude non-simulation."""
        # Traces to: REQ-PRO-095.1, REQ-PRO-095.3, TST-PRO-095.3, TST-PRO-095.5
        with patch("jira_util.is_weekly_review_overdue", return_value=True), \
             patch("jira_util.get_next_review_subsystem", return_value=("tracker/", "app/tracker/", "UNREVIEWED")):
            key1 = jira_util.ensure_weekly_review_ticket(role="coordinator")
            self.assertEqual(key1, "ATT-9200")
            mock_create.assert_called_once()
            self.assertTrue(mock_create.call_args[1].get("rank_top"))
            self.assertEqual(mock_create.call_args[1].get("parent_key"), "ATT-232")
            desc = mock_create.call_args[1].get("description", "")
            self.assertIn("Current Top Model of Google Gemini + Current Top Model of Anthropic Claude", desc)
            self.assertIn("Claude Non-Simulation Protocol", desc)
            self.assertIn("PENDING / EXTERNAL HANDOFF REQUIRED", desc)

        # Deduplication test: when already open
        mock_create.reset_mock()
        mock_search.return_value = [
            {"key": "ATT-9200", "fields": {"summary": "Weekly Dual-Vendor Gemini and Claude Code Review - tracker/ (2026-KW46)", "status": {"name": "Zu erledigen"}}}
        ]
        key2 = jira_util.ensure_weekly_review_ticket(role="coordinator")
        self.assertEqual(key2, "ATT-9200")
        mock_create.assert_not_called()

    def test_is_weekly_aspice_audit_overdue(self):
        """TST-PRO-096.3: Overdue determination for ASPICE audit based on elapsed days."""
        with tempfile.TemporaryDirectory() as tmpdir:
            audits_dir = os.path.join(tmpdir, "aspice_audits")
            os.makedirs(audits_dir, exist_ok=True)

            # 1. No audit files -> overdue
            self.assertTrue(jira_util.is_weekly_aspice_audit_overdue(audits_dir=audits_dir, days_threshold=7))

            # 2. Recent audit (2 days ago) -> not overdue
            two_days_ago = (datetime.now() - timedelta(days=2)).strftime("%Y-%m-%d")
            recent_file = os.path.join(audits_dir, f"{two_days_ago}_dual_vendor_aspice_audit.md")
            with open(recent_file, "w", encoding="utf-8") as f:
                f.write("# Audit")
            self.assertFalse(jira_util.is_weekly_aspice_audit_overdue(audits_dir=audits_dir, days_threshold=7))

            # 3. Old audit (10 days ago) -> overdue
            os.remove(recent_file)
            ten_days_ago = (datetime.now() - timedelta(days=10)).strftime("%Y-%m-%d")
            with open(os.path.join(audits_dir, f"{ten_days_ago}_dual_vendor_aspice_audit.md"), "w", encoding="utf-8") as f:
                f.write("# Audit")
            self.assertTrue(jira_util.is_weekly_aspice_audit_overdue(audits_dir=audits_dir, days_threshold=7))

    @patch("jira_util.create_issue", return_value="ATT-9201")
    @patch("jira_util.paged_jql_search", return_value=[])
    @patch("jira_util.get_config", return_value={"JIRA_URL": "https://example.atlassian.net"})
    def test_ensure_weekly_aspice_audit_creates_and_deduplicates(self, mock_config, mock_search, mock_create):
        """TST-PRO-096.3: Automated weekly ASPICE audit ticket creates when overdue and reuses when open."""
        with patch("jira_util.is_weekly_aspice_audit_overdue", return_value=True):
            key1 = jira_util.ensure_weekly_aspice_audit_ticket(role="coordinator")
            self.assertEqual(key1, "ATT-9201")
            mock_create.assert_called_once()
            self.assertTrue(mock_create.call_args[1].get("rank_top"))
            self.assertEqual(mock_create.call_args[1].get("parent_key"), "ATT-232")

        # Deduplication test: when already open
        mock_create.reset_mock()
        mock_search.return_value = [
            {"key": "ATT-9201", "fields": {"summary": "Weekly Dual-Vendor Gemini and Claude ASPICE 4.0 Cross-Audit (2026-KW46)", "status": {"name": "Zu erledigen"}}}
        ]
        key2 = jira_util.ensure_weekly_aspice_audit_ticket(role="coordinator")
        self.assertEqual(key2, "ATT-9201")
        mock_create.assert_not_called()

    @patch("jira_util.ensure_weekly_aspice_audit_ticket", return_value="ATT-9201")
    @patch("jira_util.ensure_weekly_review_ticket", return_value="ATT-9200")
    def test_ensure_weekly_reviews_coordinates_both_pillars(self, mock_review, mock_audit):
        """TST-PRO-096.3: ensure_weekly_reviews dispatches both code review and ASPICE audit tickets."""
        res = jira_util.ensure_weekly_reviews(role="coordinator", force=True)
        self.assertEqual(res, {"code_review": "ATT-9200", "aspice_audit": "ATT-9201"})
        mock_review.assert_called_once_with(role="coordinator", force=True)
        mock_audit.assert_called_once_with(role="coordinator", force=True)

    def test_select_greedy_bin_pack_tickets_with_capacity_ceiling(self):
        """TST-PRO-097.1: Multi-lane greedy bin-packing accumulates tickets up to max_sp_per_lane."""
        ranked = [
            {"key": "ATT-1", "fields": {"issuetype": {"name": "Bug"}, "summary": "A1", "status": {"name": "Zu erledigen"}, "parent": {"key": "EPIC-A"}, jira_util.STORY_POINTS_FIELD: 3.0}},
            {"key": "ATT-2", "fields": {"issuetype": {"name": "Bug"}, "summary": "A2", "status": {"name": "Zu erledigen"}, "parent": {"key": "EPIC-A"}, jira_util.STORY_POINTS_FIELD: 5.0}},
            {"key": "ATT-3", "fields": {"issuetype": {"name": "Bug"}, "summary": "A3", "status": {"name": "Zu erledigen"}, "parent": {"key": "EPIC-A"}, jira_util.STORY_POINTS_FIELD: 5.0}},
            {"key": "ATT-4", "fields": {"issuetype": {"name": "Feature"}, "summary": "B1", "status": {"name": "Zu erledigen"}, "parent": {"key": "EPIC-B"}, jira_util.STORY_POINTS_FIELD: 8.0}},
            {"key": "ATT-5", "fields": {"issuetype": {"name": "Feature"}, "summary": "C1", "status": {"name": "Zu erledigen"}, "parent": {"key": "EPIC-C"}, jira_util.STORY_POINTS_FIELD: 3.0}},
            {"key": "ATT-6", "fields": {"issuetype": {"name": "Feature"}, "summary": "D1", "status": {"name": "Zu erledigen"}, "parent": {"key": "EPIC-D"}, jira_util.STORY_POINTS_FIELD: 2.0}},
        ]
        # With max_lanes=3 and max_sp_per_lane=10:
        # Lane 1 (EPIC-A): ATT-1 (3 SP) + ATT-2 (5 SP) = 8 SP <= 10. ATT-3 (5 SP) -> 13 SP > 10, left in queue.
        # Lane 2 (EPIC-B): ATT-4 (8 SP).
        # Lane 3 (EPIC-C): ATT-5 (3 SP).
        # ATT-6 (EPIC-D): 4th Epic, exceeds max_lanes=3.
        packed = jira_util.select_greedy_bin_pack_tickets(ranked, max_lanes=3, max_sp_per_lane=10.0)
        self.assertEqual([i["key"] for i in packed], ["ATT-1", "ATT-2", "ATT-4", "ATT-5"])

    @patch("sys.stdout", new_callable=io.StringIO)
    @patch("jira_util.jira_request")
    @patch("jira_util.get_config", return_value={"JIRA_URL": "https://example.atlassian.net"})
    def test_show_issue_fixversion_warning_alignment(self, mock_config, mock_request, mock_stdout):
        """TST-PRO-097.2: FixVersion warning suppressed for active in-sprint issues and emitted for completed ones."""
        # 1. Active in-sprint issue (In Bearbeitung) without FixVersion -> No warning
        mock_request.return_value = {
            "key": "ATT-100",
            "fields": {
                "summary": "Active Issue",
                "issuetype": {"name": "Verbesserung", "subtask": False},
                "status": {"name": "In Bearbeitung"},
                "fixVersions": [],
                "description": "Active in-sprint description",
                "comment": {"comments": []},
            }
        }
        jira_util.show_issue("ATT-100")
        output1 = mock_stdout.getvalue()
        self.assertIn("*Lösungsversion (Fix Version/s)*: None (In-Sprint / Set upon completion per Rule 8.3)", output1)
        self.assertNotIn("WARNING: Mandatory field missing!", output1)

        # 2. Completed issue (Erledigt) without FixVersion -> Warning
        mock_stdout.truncate(0)
        mock_stdout.seek(0)
        mock_request.return_value = {
            "key": "ATT-101",
            "fields": {
                "summary": "Completed Issue",
                "issuetype": {"name": "Verbesserung", "subtask": False},
                "status": {"name": "Erledigt"},
                "fixVersions": [],
                "description": "Completed description",
                "comment": {"comments": []},
            }
        }
        jira_util.show_issue("ATT-101")
        output2 = mock_stdout.getvalue()
        self.assertIn("*Lösungsversion (Fix Version/s)*: None (WARNING: Mandatory field missing on completed issue!)", output2)

        # 3. Subtask without FixVersion -> Subtask message, no warning
        mock_stdout.truncate(0)
        mock_stdout.seek(0)
        mock_subtask = {
            "key": "ATT-102",
            "fields": {
                "summary": "Sub-task Issue",
                "issuetype": {"name": "Sub-task", "subtask": True},
                "status": {"name": "In Bearbeitung"},
                "fixVersions": [],
                "parent": {"key": "ATT-100", "fields": {"summary": "Parent", "issuetype": {"name": "Verbesserung"}}},
                "description": "Subtask description",
                "comment": {"comments": []},
            }
        }
        # Parent lookup returns no fixVersions
        mock_request.side_effect = [
            mock_subtask,
            {"fields": {"fixVersions": []}}
        ]
        jira_util.show_issue("ATT-102")
        output3 = mock_stdout.getvalue()
        self.assertIn("*Lösungsversion (Fix Version/s)*: None on subtask (per Core Rule 8.3)", output3)
        self.assertNotIn("WARNING: Mandatory field missing!", output3)


class TestPoApprovalAndChangelogProvenance(unittest.TestCase):
    """
    Verifies REQ-PRO-105 / TST-PRO-105:
    Explicit PO approval & changelog provenance for agent-originated change requests (ATT-8166).
    """

    @patch("jira_util.jira_request")
    def test_tst_pro_105_1_create_issue_blocks_add_to_sprint_for_agents(self, mock_request):
        # TST-PRO-105.1: Agent Backlog Placement & Direct Sprint Block Test
        mock_request.return_value = {"key": "ATT-9999"}
        with patch("sys.stderr", new_callable=io.StringIO) as mock_stderr:
            for role in ("agent1", "agent2", "coordinator"):
                with self.assertRaises(SystemExit) as cm:
                    jira_util.create_issue("Agent Task", "Desc", role=role, add_to_sprint=True)
                self.assertEqual(cm.exception.code, 1)
                self.assertIn("Agents must not move tickets to sprints", mock_stderr.getvalue())

    @patch("jira_util.jira_request")
    def test_tst_pro_105_2_backlog_to_po_queue_two_way_guard(self, mock_request):
        # TST-PRO-105.2: Backlog-to-PO-Queue Two-Way Guard Test
        mock_request.return_value = {}

        # Moving to 447 without cutoff_return=True is rejected
        with patch("sys.stderr", new_callable=io.StringIO) as mock_stderr:
            with self.assertRaises(SystemExit) as cm:
                jira_util.move_issues_to_sprint_guarded(447, ["ATT-100"], role="coordinator", cutoff_return=False)
            self.assertEqual(cm.exception.code, 1)
            self.assertIn("Adding Backlog tickets into 'Vom PO Freigegeben' (ID 447) is strictly forbidden", mock_stderr.getvalue())

        # Moving raw Backlog directly to active sprint (450) without source_sprint_id=447 is rejected
        with patch("sys.stderr", new_callable=io.StringIO) as mock_stderr:
            with self.assertRaises(SystemExit) as cm:
                jira_util.move_issues_to_sprint_guarded(450, ["ATT-100"], role="coordinator", source_sprint_id=None)
            self.assertEqual(cm.exception.code, 1)
            self.assertIn("strictly forbidden for AI agents (REQ-PRO-105.2)", mock_stderr.getvalue())

        # Pack-sprint from 447 to active sprint succeeds
        res = jira_util.move_issues_to_sprint_guarded(450, ["ATT-100"], role="coordinator", source_sprint_id=447)
        self.assertEqual(res, ["ATT-100"])

        # Return unstarted from active sprint to 447 with cutoff_return=True succeeds
        res_return = jira_util.move_issues_to_sprint_guarded(447, ["ATT-100"], role="coordinator", cutoff_return=True, source_sprint_id=450)
        self.assertEqual(res_return, ["ATT-100"])

    def test_tst_pro_105_3_verify_po_approval_changelog(self):
        # TST-PRO-105.3: PO Changelog Provenance & QA Audit Dimension 6 Test
        human_id = jira_util.ROLE_ACCOUNT_IDS["human"]

        # 1. Ceremony ticket is exempt
        ceremony_issue = {"key": "ATT-9100", "fields": {"summary": "Review & Retro - Sprint 2026-47.1"}}
        appr, reason = jira_util.verify_po_approval_changelog(ceremony_issue)
        self.assertTrue(appr)
        self.assertIn("Ceremony ticket exempt", reason)

        # 2. Directly created by human PO
        po_created = {"key": "ATT-9101", "fields": {"summary": "Feature", "reporter": {"accountId": human_id}}}
        appr, reason = jira_util.verify_po_approval_changelog(po_created)
        self.assertTrue(appr)
        self.assertIn("created directly by Product Owner", reason)

        # 3. Approved in changelog into Vom PO Freigegeben (ID 447) by PO
        po_changelog = {
            "key": "ATT-9102",
            "fields": {"summary": "Feature", "reporter": {"accountId": "agent1"}},
            "changelog": {
                "histories": [
                    {
                        "author": {"accountId": human_id, "displayName": "Rainer Blind"},
                        "created": "2026-11-15T19:05:18.523+0100",
                        "items": [{"field": "Sprint", "fromString": "", "toString": "Vom PO Freigegeben", "from": "", "to": "447"}],
                    }
                ]
            },
        }
        appr, reason = jira_util.verify_po_approval_changelog(po_changelog)
        self.assertTrue(appr)
        self.assertIn("approved sprint placement into 'Vom PO Freigegeben'", reason)

        # 4. Agent moved issue to sprint without PO approval -> REJECTED
        agent_changelog = {
            "key": "ATT-9103",
            "fields": {"summary": "Feature", "reporter": {"accountId": "agent1"}},
            "changelog": {
                "histories": [
                    {
                        "author": {"accountId": "agent_id", "displayName": "AI Agent"},
                        "created": "2026-11-15T19:05:18.523+0100",
                        "items": [{"field": "Sprint", "fromString": "", "toString": "2026-47.1", "from": "", "to": "450"}],
                    }
                ]
            },
        }
        appr, reason = jira_util.verify_po_approval_changelog(agent_changelog)
        self.assertFalse(appr)
        self.assertIn("lacks Product Owner approval", reason)

    def test_tst_pro_105_4_att_7902_disposition_record_and_governance(self):
        # TST-PRO-105.4: Historical Exception Disposition & Living Doc Parity Test
        human_id = jira_util.ROLE_ACCOUNT_IDS["human"]
        att_7902_data = {
            "key": "ATT-7902",
            "fields": {"summary": "Characterisation test scaffolding", "reporter": {"accountId": "agent_id"}},
            "changelog": {
                "histories": [
                    {
                        "author": {"accountId": human_id, "displayName": "Rainer Blind"},
                        "created": "2026-11-14T20:28:50.000+0100",
                        "items": [{"field": "Sprint", "fromString": "", "toString": "Vom PO Freigegeben", "from": "", "to": "447"}],
                    }
                ]
            },
        }
        appr, reason = jira_util.verify_po_approval_changelog(att_7902_data)
        self.assertTrue(appr)
        self.assertIn("approved sprint placement", reason)


def _concurrent_reservation_worker(ledger_dir, repo_root, domain, id_type, ticket, count, results_queue):
    import os
    import sys
    import importlib.util

    root_tools_dir = os.path.dirname(os.path.abspath(__file__))
    sys.path = [p for p in sys.path if "hoechstmass" not in p]
    if root_tools_dir not in sys.path:
        sys.path.insert(0, root_tools_dir)
    else:
        sys.path.remove(root_tools_dir)
        sys.path.insert(0, root_tools_dir)

    spec = importlib.util.spec_from_file_location("root_jira_util", os.path.join(root_tools_dir, "jira_util.py"))
    jira_util_mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(jira_util_mod)

    try:
        allocated = jira_util_mod.reserve_id(
            domain,
            id_type,
            ticket,
            count=count,
            ledger_dir=ledger_dir,
            repo_root=repo_root,
        )
        results_queue.put((ticket, allocated, None))
    except Exception as e:
        results_queue.put((ticket, [], str(e)))


class TestIdReservationGovernance(unittest.TestCase):
    """
    TST-PRO-106: Atomic REQ and TST ID Reservation Across Parallel Sprint Lanes Suite (ATT-8149).
    Verifies REQ-PRO-106.1..REQ-PRO-106.4.
    """

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.ledger_dir = self.temp_dir.name
        self.repo_dir = tempfile.TemporaryDirectory()
        os.makedirs(os.path.join(self.repo_dir.name, "docs", "requirements"), exist_ok=True)
        os.makedirs(os.path.join(self.repo_dir.name, "docs", "tests"), exist_ok=True)
        os.makedirs(os.path.join(self.repo_dir.name, "docs", "engineering", "test_specs"), exist_ok=True)

    def tearDown(self):
        self.temp_dir.cleanup()
        self.repo_dir.cleanup()

    def test_tst_pro_106_1_atomic_cross_worktree_id_reservation(self):
        # TST-PRO-106.1: Atomic Cross-Worktree ID Reservation Test
        import multiprocessing
        q = multiprocessing.Queue()
        processes = []
        concurrency = 5
        count_per_proc = 2

        for i in range(concurrency):
            ticket = f"ATT-900{i}"
            p = multiprocessing.Process(
                target=_concurrent_reservation_worker,
                args=(self.ledger_dir, self.repo_dir.name, "SIM", "REQ", ticket, count_per_proc, q)
            )
            processes.append(p)

        for p in processes:
            p.start()
        for p in processes:
            p.join(timeout=10)

        results = []
        while not q.empty():
            results.append(q.get())

        self.assertEqual(len(results), concurrency)
        all_allocated = []
        for ticket, allocated, err in results:
            self.assertIsNone(err, f"Worker failed: {err}")
            self.assertEqual(len(allocated), count_per_proc)
            all_allocated.extend(allocated)

        self.assertEqual(len(all_allocated), concurrency * count_per_proc)
        # Verify 100% uniqueness - zero ID collisions under concurrent processes
        self.assertEqual(len(set(all_allocated)), len(all_allocated))

    def test_tst_pro_106_2_monotonic_next_id_calculation(self):
        # TST-PRO-106.2: Monotonic Next-ID Calculation with Active Reservation Awareness Test
        req_file = os.path.join(self.repo_dir.name, "docs", "requirements", "req_sim.md")
        with open(req_file, "w", encoding="utf-8") as f:
            f.write("### REQ-SIM-010: Prior requirement [Implemented]\n")

        # First reservation should allocate REQ-SIM-011
        r1 = jira_util.reserve_id("SIM", "REQ", "ATT-1001", count=1, ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name)
        self.assertEqual(r1, ["REQ-SIM-011"])

        # Second reservation without committing to doc should observe active reservation and allocate REQ-SIM-012
        r2 = jira_util.reserve_id("SIM", "REQ", "ATT-1002", count=2, ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name)
        self.assertEqual(r2, ["REQ-SIM-012", "REQ-SIM-013"])

        # BOTH allocation aligns REQ and TST numbers
        r3 = jira_util.reserve_id("SIM", "BOTH", "ATT-1003", count=1, ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name)
        self.assertEqual(r3, ["REQ-SIM-014", "TST-SIM-014"])

        # Specific ID reservation with conflict check
        with self.assertRaises(ValueError):
            jira_util.reserve_id("SIM", "REQ", "ATT-1004", specific_id=11, ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name)

        # Renewing specific ID by owner succeeds
        r_renew = jira_util.reserve_id("SIM", "REQ", "ATT-1001", specific_id=11, ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name)
        self.assertEqual(r_renew, ["REQ-SIM-011"])

    def test_tst_pro_106_3_gate_2_reservation_provenance_verification(self):
        # TST-PRO-106.3: Gate 2 Reservation Provenance Verification Test
        test_spec_path = os.path.join(self.repo_dir.name, "docs", "engineering", "test_specs", "ATT-2000_test_spec.md")
        with open(test_spec_path, "w", encoding="utf-8") as f:
            f.write("""# Stage 2: Test Spec
## 1. Requirement Specification (REQ-SIM-050)
* **REQ-SIM-050.1**: Something
## 4. Test Specification (TST-SIM-050)
* **TST-SIM-050.1**: Test procedure
""")

        # 1. Unreserved net-new ID -> fails Gate 2 check
        is_valid, errors = jira_util.verify_stage2_id_reservations(
            "ATT-2001", "", parent_key="ATT-2000", ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name
        )
        self.assertFalse(is_valid)
        self.assertTrue(any("has not been reserved" in e for e in errors))

        # 2. Reserved for a DIFFERENT ticket -> fails Gate 2 check
        jira_util.reserve_id("SIM", "BOTH", "ATT-9999", specific_id=50, ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name)
        is_valid, errors = jira_util.verify_stage2_id_reservations(
            "ATT-2001", "", parent_key="ATT-2000", ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name
        )
        self.assertFalse(is_valid)
        self.assertTrue(any("reserved for ticket 'ATT-9999', not 'ATT-2000'" in e for e in errors))

        # 3. Reserved for the matching ticket -> passes Gate 2 check
        jira_util.release_id("ATT-9999", ledger_dir=self.ledger_dir)
        jira_util.reserve_id("SIM", "BOTH", "ATT-2000", specific_id=50, ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name)
        is_valid, errors = jira_util.verify_stage2_id_reservations(
            "ATT-2001", "", parent_key="ATT-2000", ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name
        )
        self.assertTrue(is_valid)
        self.assertEqual(len(errors), 0)

    def test_tst_pro_106_4_reservation_hygiene_expiration_and_orphan_parity(self):
        # TST-PRO-106.4: Reservation Hygiene, Expiration & Code Orphan Parity Test
        # 1. Reserve and list
        allocated = jira_util.reserve_id("SIM", "REQ", "ATT-3000", count=2, ledger_dir=self.ledger_dir, repo_root=self.repo_dir.name)
        res_list = jira_util.list_reservations(ticket="ATT-3000", ledger_dir=self.ledger_dir)
        self.assertEqual(len(res_list), 2)

        # 2. Release by ticket
        rel = jira_util.release_id("ATT-3000", ledger_dir=self.ledger_dir)
        self.assertEqual(len(rel), 2)
        res_list_after = jira_util.list_reservations(ticket="ATT-3000", ledger_dir=self.ledger_dir)
        self.assertEqual(len(res_list_after), 0)

        # 3. Active expiration cleanup
        ledger_path = jira_util.resolve_shared_ledger_path(self.ledger_dir)
        expired_entry = {
            "version": 1,
            "reservations": [
                {
                    "id": "REQ-SIM-099",
                    "type": "REQ",
                    "domain": "SIM",
                    "ticket": "ATT-OLD",
                    "reserved_at": "2020-01-01T00:00:00Z",
                    "expires_at": "2020-01-08T00:00:00Z",
                }
            ]
        }
        jira_util.save_reservation_ledger(ledger_path, expired_entry)
        active_res = jira_util.list_reservations(ledger_dir=self.ledger_dir)
        self.assertEqual(len(active_res), 0)


class TestSeparatedAuthorshipAndGateEvidence(unittest.TestCase):
    """
    TST-PRO-107: Separated Gate Review Authorship, Evidence Checklists & Zero-Revision Alerts Suite (ATT-8150).
    Verifies REQ-PRO-107.1..REQ-PRO-107.4.
    """

    @patch("jira_util.get_config", return_value={"JIRA_URL": "https://example.atlassian.net"})
    @patch("jira_util.jira_request")
    @patch("jira_util.create_subtask", return_value="ATT-9999")
    @patch("jira_util.transition_issue")
    def test_tst_pro_107_1_separated_authorship_enforcement(
        self, mock_transition, mock_create, mock_request, mock_config
    ):
        # TST-PRO-107.1: Separated Authorship Enforcement Test
        # In Jira Cloud REST v2, subtasks embedded in parent issues omit 'assignee'
        parent_info = {
            "fields": {
                "summary": "Parent Issue",
                "status": {"name": "Test Spec"},
                "subtasks": [
                    {
                        "key": "ATT-9001",
                        "fields": {
                            "summary": "[Analysis] Parent Issue",
                            "status": {"name": "Erledigt"},
                        },
                    }
                ],
            }
        }
        subtask_info = {
            "key": "ATT-9001",
            "fields": {
                "assignee": {"displayName": "Agent 2", "name": "agent2", "accountId": "agent2_id"},
            },
        }

        def mock_request_impl(url, **kwargs):
            if "ATT-9001" in url and "fields=assignee" in url:
                return subtask_info
            return parent_info

        mock_request.side_effect = mock_request_impl

        # 1. Author matches preceding Gate 1 reviewer (both agent2) -> SystemExit(1)
        with self.assertRaises(SystemExit) as cm:
            jira_util.submit_stage("ATT-9000", "2", "Stage 2 spec", role="agent2")
        self.assertEqual(cm.exception.code, 1)

        # 2. Distinct author (agent1 vs preceding reviewer agent2) -> succeeds
        mock_transition.reset_mock()
        st_key = jira_util.submit_stage("ATT-9000", "2", "Stage 2 spec", role="agent1")
        self.assertEqual(st_key, "ATT-9999")

        # 3. Explicit author parameter override (author="agent1" under role="agent2") -> succeeds
        mock_transition.reset_mock()
        st_key = jira_util.submit_stage("ATT-9000", "2", "Stage 2 spec", role="agent2", author="agent1")
        self.assertEqual(st_key, "ATT-9999")

        # 4. Bypass flag (--bypass-authorship) -> succeeds even if author matches
        mock_transition.reset_mock()
        st_key = jira_util.submit_stage("ATT-9000", "2", "Stage 2 spec", role="agent2", bypass_authorship=True)
        self.assertEqual(st_key, "ATT-9999")

        # 5. Stage 1 submission (no preceding stage) -> succeeds
        mock_transition.reset_mock()
        st_key = jira_util.submit_stage("ATT-9000", "1", "Stage 1 analysis", role="agent2")
        self.assertEqual(st_key, "ATT-9999")

    @patch("jira_util.get_config", return_value={"JIRA_URL": "https://example.atlassian.net"})
    @patch("jira_util.assign_issue")
    @patch("jira_util.add_comment")
    @patch("jira_util.transition_issue")
    @patch("jira_util.get_subtask_summary_and_desc")
    @patch("jira_util.get_subtask_start_time", return_value=None)
    def test_tst_pro_107_2_evidence_backed_gate_checklist_validation(
        self, mock_get_start_time, mock_get_subtask, mock_transition, mock_comment, mock_assign, mock_config
    ):
        # TST-PRO-107.2: Evidence-Backed Gate 1-5 Checklist Validation Test
        mock_get_subtask.return_value = ("[Impl-Plan] ATT-9000: Plan", "Plan details", "ATT-9000")

        # 1. Plain text without checklist or evidence -> SystemExit(1)
        with self.assertRaises(SystemExit) as cm:
            jira_util.gate_verdict("ATT-9003", "PASS", "Looks good, approved.", role="agent2")
        self.assertEqual(cm.exception.code, 1)

        # 2. Has checklist item but lacks evidence anchor -> SystemExit(1)
        with self.assertRaises(SystemExit) as cm:
            jira_util.gate_verdict("ATT-9003", "PASS", "PASS: All requirements checked [x]", role="agent2")
        self.assertEqual(cm.exception.code, 1)

        # 3. Has evidence anchor but lacks checklist pattern -> SystemExit(1)
        with self.assertRaises(SystemExit) as cm:
            jira_util.gate_verdict("ATT-9003", "PASS", "Reviewed commit 3a31b9ad and tools/jira_util.py:100", role="agent2")
        self.assertEqual(cm.exception.code, 1)

        # 4. Valid checklist + concrete evidence anchor -> succeeds
        valid_report = """### Gate 3 Independent Review: PASS
* [x] Traceability: REQ-PRO-107 verified in tools/jira_util.py:1450
* [x] Commit SHA: 3a31b9ad verified clean
* [x] Test output: BUILD SUCCESSFUL
Verdict: PASS."""
        jira_util.gate_verdict("ATT-9003", "PASS", valid_report, role="agent2")
        mock_transition.assert_called_with("ATT-9003", "freigabe", role="agent2", inject_context=True)

    @patch("jira_util.get_config", return_value={"JIRA_URL": "https://example.atlassian.net"})
    @patch("jira_util.assign_issue")
    @patch("jira_util.add_comment")
    @patch("jira_util.transition_issue")
    @patch("jira_util.get_subtask_summary_and_desc")
    @patch("jira_util.get_subtask_start_time")
    def test_tst_pro_107_2_rapid_review_warning(
        self, mock_get_start_time, mock_get_subtask, mock_transition, mock_comment, mock_assign, mock_config
    ):
        # REQ-PRO-107.2 / TST-PRO-107.2: Rapid Gate Review Warning (< 5s elapsed)
        from datetime import timezone
        mock_get_subtask.return_value = ("[Impl-Plan] ATT-9000: Plan", "Plan details", "ATT-9000")
        valid_report = """### Gate 3 Independent Review: PASS
* [x] Traceability: REQ-PRO-107 verified in tools/jira_util.py:1450
* [x] Commit SHA: 3a31b9ad verified clean
* [x] Test output: BUILD SUCCESSFUL
Verdict: PASS."""

        # 1. Rapid review (< 5s, e.g. 2s ago) -> emits [AUDIT WARNING] Rapid Gate Review (< 5s) to stderr
        mock_get_start_time.return_value = datetime.now(timezone.utc) - timedelta(seconds=2)
        with patch("sys.stderr", new_callable=io.StringIO) as mock_stderr:
            jira_util.gate_verdict("ATT-9003", "PASS", valid_report, role="agent2")
            stderr_output = mock_stderr.getvalue()
            self.assertIn("[AUDIT WARNING] Rapid Gate Review (< 5s)", stderr_output)

        # 2. Normal review (>= 5s, e.g. 15s ago) -> no rapid review warning
        mock_get_start_time.return_value = datetime.now(timezone.utc) - timedelta(seconds=15)
        with patch("sys.stderr", new_callable=io.StringIO) as mock_stderr:
            jira_util.gate_verdict("ATT-9003", "PASS", valid_report, role="agent2")
            stderr_output = mock_stderr.getvalue()
            self.assertNotIn("[AUDIT WARNING] Rapid Gate Review (< 5s)", stderr_output)

    def test_tst_pro_107_3_multi_sprint_100_percent_ftpr_rubber_stamping_alert(self):
        # TST-PRO-107.3: Multi-Sprint 100% FTPR Rubber-Stamping Alert Test
        import sprint_metrics

        # 1. Single sprint with 100% FTPR -> no multi-sprint alert
        hist_1 = [
            {"sprint_name": "Sprint 1", "quality_gates": {"overall_ftpr_percent": 90.0, "total_subtasks_evaluated": 10}}
        ]
        curr_1 = {"sprint_name": "Sprint 2", "quality_gates": {"overall_ftpr_percent": 100.0, "total_subtasks_evaluated": 10}}
        spc_1 = sprint_metrics.calculate_spc_limits(hist_1, current_metrics=curr_1)
        self.assertFalse(any("Finding M3 Rubber-Stamping Alert" in w for w in spc_1.get("warnings", [])))

        # 2. Two consecutive sprints with 100% FTPR -> triggers Rubber-Stamping Alert!
        hist_2 = [
            {"sprint_name": "Sprint 1", "quality_gates": {"overall_ftpr_percent": 100.0, "total_subtasks_evaluated": 10}}
        ]
        curr_2 = {"sprint_name": "Sprint 2", "quality_gates": {"overall_ftpr_percent": 100.0, "total_subtasks_evaluated": 10}}
        spc_2 = sprint_metrics.calculate_spc_limits(hist_2, current_metrics=curr_2)
        self.assertTrue(any("Finding M3 Rubber-Stamping Alert" in w for w in spc_2.get("warnings", [])))

        # 3. Check markdown report formatting includes the alert
        report_md = sprint_metrics.format_metrics_markdown(curr_2, spc_2)
        self.assertIn("Finding M3 Rubber-Stamping Alert", report_md)

    def test_tst_pro_107_4_governance_rules_and_skill_parity(self):
        # TST-PRO-107.4: Rule & Living Documentation Parity Test
        gov_path = os.path.join(REPO_ROOT, ".agents", "rules", "aspice_governance.md")
        with open(gov_path, "r", encoding="utf-8") as f:
            gov_text = f.read()
        self.assertIn("REQ-PRO-107", gov_text)
        self.assertIn("Separated Review Authorship", gov_text)

        skill_path = os.path.join(REPO_ROOT, ".agents", "skills", "jira-workflow", "SKILL.md")
        with open(skill_path, "r", encoding="utf-8") as f:
            skill_text = f.read()
        self.assertIn("REQ-PRO-107", skill_text)

    @patch("jira_util.get_config", return_value={"JIRA_URL": "https://example.atlassian.net"})
    @patch("jira_util.jira_request")
    def test_systemtest_pseudo_sprint_454_and_human_execution_guard(self, mock_request, mock_config):
        """Protect Bereit fuer PO Systemtest (ID 454) and skip human-execution in greedy bin-packing."""
        # 1. Greedy bin-packing must skip tickets labeled 'human-execution'
        ranked = [
            {"key": "ATT-8423", "fields": {"issuetype": {"name": "Feature"}, "summary": "SysVer", "status": {"name": "Zu erledigen"}, "labels": ["human-execution"], "parent": {"key": "ATT-6142"}}},
            {"key": "ATT-8424", "fields": {"issuetype": {"name": "Bug"}, "summary": "Normal", "status": {"name": "Zu erledigen"}, "labels": [], "parent": {"key": "ATT-166"}}},
        ]
        packed = jira_util.select_greedy_bin_pack_tickets(ranked, max_lanes=3)
        self.assertEqual([i["key"] for i in packed], ["ATT-8424"])

        # 2. pack_sprint and return_unstarted reject protected pseudo-sprint ID 454
        with self.assertRaises(SystemExit):
            jira_util.pack_sprint_from_po_queue(454, role="coordinator")
        with patch("jira_util.resolve_sprint_object", return_value={"id": 454, "name": "Bereit für PO Systemtest"}):
            mock_request.return_value = {"values": [{"id": 1}]}
            with self.assertRaises(SystemExit):
                jira_util.return_unstarted_to_po_queue("454", role="coordinator")

        # 3. defer_to_systemtest_queue adds human-execution label, assigns human, and moves to ID 454
        with patch("jira_util.add_label") as mock_add_lbl, \
             patch("jira_util.assign_issue") as mock_assign, \
             patch("jira_util.move_issues_to_sprint_guarded") as mock_move:
            self.assertTrue(jira_util.defer_to_systemtest_queue("ATT-8423", role="coordinator"))
        mock_add_lbl.assert_called_once_with("ATT-8423", ["human-execution"], role="coordinator")
        mock_assign.assert_called_once_with("ATT-8423", "human", role="coordinator")
        mock_move.assert_called_once_with(454, ["ATT-8423"], role="coordinator")

    def test_tst_pro_108_1_subagent_role_binding_enforcement(self):
        # TST-PRO-108.1: Subagent Identity & Role Binding Enforcement Test
        # 1. When JIRA_ACTOR is set to 'agent1', passing mismatched '--as agent2' raises SystemExit
        with patch.dict(os.environ, {"JIRA_ACTOR": "agent1"}, clear=False):
            with self.assertRaises(SystemExit) as cm:
                jira_util.get_active_role(["--as", "agent2", "show", "ATT-8151"])
            self.assertEqual(cm.exception.code, 1)

        # 2. When JIRA_ACTOR is set to 'agent1', passing matching '--as agent1' succeeds
        with patch.dict(os.environ, {"JIRA_ACTOR": "agent1"}, clear=False):
            role, rem = jira_util.get_active_role(["--as", "agent1", "show", "ATT-8151"])
            self.assertEqual(role, "agent1")
            self.assertEqual(rem, ["show", "ATT-8151"])

        # 3. When JIRA_ACTOR is unset, passing '--as coordinator' succeeds without rejection
        env_copy = os.environ.copy()
        env_copy.pop("JIRA_ACTOR", None)
        with patch.dict(os.environ, env_copy, clear=True):
            role, rem = jira_util.get_active_role(["--as", "coordinator", "show", "ATT-8151"])
            self.assertEqual(role, "coordinator")
            self.assertEqual(rem, ["show", "ATT-8151"])

    def test_tst_pro_108_2_dedicated_qa_credentials_and_account_independence(self):
        # TST-PRO-108.2: Dedicated QA Role Credential Mapping & Account Independence Test
        cfg_qa = {
            "JIRA_URL": "https://example.atlassian.net",
            "JIRA_AGENT2_USER": "agent2_user",
            "JIRA_AGENT2_TOKEN": "agent2_token",
            "JIRA_AGENT2_ACCOUNT_ID": "agent2_id",
            "JIRA_QA_USER": "qa_user",
            "JIRA_QA_TOKEN": "qa_token",
            "JIRA_QA_ACCOUNT_ID": "qa_id",
        }
        # 1. resolve_account with role='qa' resolves JIRA_QA_USER and JIRA_QA_TOKEN
        u, t = jira_util.resolve_account(role="qa", config=cfg_qa)
        self.assertEqual(u, "qa_user")
        self.assertEqual(t, "qa_token")

        # 2. ROLE_ACCOUNT_IDS['qa'] is distinct from ROLE_ACCOUNT_IDS['agent2']
        self.assertNotEqual(jira_util.ROLE_ACCOUNT_IDS.get("qa"), jira_util.ROLE_ACCOUNT_IDS.get("agent2"))

        # 3. validate_account_separation returns True when distinct
        is_sep, err = jira_util.validate_account_separation(cfg_qa)
        self.assertTrue(is_sep)
        self.assertIsNone(err)

        # 4. validate_account_separation returns False when qa and agent2 share username
        cfg_conflict_user = dict(cfg_qa, JIRA_QA_USER="agent2_user")
        is_sep, err = jira_util.validate_account_separation(cfg_conflict_user)
        self.assertFalse(is_sep)
        self.assertIn("share identical username", err)

        # 5. validate_account_separation returns False when qa and agent2 share accountId
        cfg_conflict_id = dict(cfg_qa, JIRA_QA_ACCOUNT_ID="agent2_id")
        is_sep, err = jira_util.validate_account_separation(cfg_conflict_id)
        self.assertFalse(is_sep)
        self.assertIn("share identical accountId", err)

    def test_tst_pro_108_3_git_commit_trailers_formatting(self):
        # TST-PRO-108.3: Producing Agent Identity & Reviewer Git Commit Trailers Test
        import parallel_sprint_lock
        msg = parallel_sprint_lock.format_commit_with_trailers(
            "ATT-8151: Test commit",
            agent_role="agent1",
            agent_model="flash",
            reviewed_by="Agent 2 (Reviewer)"
        )
        self.assertIn("Agent-Role: agent1", msg)
        self.assertIn("Agent-Model: flash", msg)
        self.assertIn("Reviewed-by: Agent 2 (Reviewer)", msg)

        valid, missing = parallel_sprint_lock.validate_commit_trailers(msg)
        self.assertTrue(valid)
        self.assertEqual(missing, [])

    def test_tst_pro_108_4_risk_register_single_os_user_residual_risk(self):
        # TST-PRO-108.4: Single-OS User Residual Risk Review Test
        risk_path = os.path.join(REPO_ROOT, "docs", "management", "risk_register.md")
        with open(risk_path, "r", encoding="utf-8") as f:
            risk_text = f.read()
        self.assertIn("RISK-007", risk_text)
        self.assertIn("Single-OS User", risk_text)
        self.assertIn("ATT-8151", risk_text)

    @patch("jira_util.jira_request")
    def test_tst_pro_091_7_living_doc_shard_resolution(self, mock_request):
        # TST-PRO-091.7: Living Document Shard Resolution Test
        import subprocess
        def mock_jira(url, method="GET", payload=None, role="coordinator"):
            if "/rest/agile/1.0/board" in url and "/sprint" not in url:
                return {"values": [{"id": 1, "name": "ATT Board"}]}
            elif "/rest/agile/1.0/board/1/sprint?state=active" in url:
                return {"values": [{"id": 440, "name": "2026-46.3", "state": "active"}]}
            elif "/rest/agile/1.0/sprint/440/issue" in url:
                return {
                    "issues": [
                        {
                            "key": "ATT-9101",
                            "fields": {
                                "summary": "Ticket referencing REQ-SHARD-001",
                                "description": "Details about REQ-SHARD-001",
                                "status": {"name": "Erledigt"},
                                "issuetype": {"subtask": False},
                                "subtasks": [
                                    {
                                        "key": "ATT-9102",
                                        "fields": {"summary": "[Analysis] Task", "status": {"name": "Erledigt"}},
                                        "comments": [{"author": {"accountId": jira_util.ROLE_ACCOUNT_IDS["agent2"]}, "body": "GATE_VERDICT: PASS"}]
                                    },
                                    {
                                        "key": "ATT-9103",
                                        "fields": {"summary": "[Spec] Task", "status": {"name": "Erledigt"}},
                                        "comments": [{"author": {"accountId": jira_util.ROLE_ACCOUNT_IDS["agent2"]}, "body": "GATE_VERDICT: PASS"}]
                                    },
                                    {
                                        "key": "ATT-9104",
                                        "fields": {"summary": "[Plan] Task", "status": {"name": "Erledigt"}},
                                        "comments": [{"author": {"accountId": jira_util.ROLE_ACCOUNT_IDS["agent2"]}, "body": "GATE_VERDICT: PASS"}]
                                    },
                                    {
                                        "key": "ATT-9105",
                                        "fields": {"summary": "[Impl] Task", "status": {"name": "Erledigt"}},
                                        "comments": [{"author": {"accountId": jira_util.ROLE_ACCOUNT_IDS["agent2"]}, "body": "GATE_VERDICT: PASS"}]
                                    },
                                    {
                                        "key": "ATT-9106",
                                        "fields": {"summary": "[Verif] Task", "status": {"name": "Erledigt"}},
                                        "comments": [{"author": {"accountId": jira_util.ROLE_ACCOUNT_IDS["agent2"]}, "body": "GATE_VERDICT: PASS"}]
                                    },
                                ],
                            },
                        },
                    ]
                }
            elif "/changelog" in url:
                return {
                    "values": [
                        {
                            "author": {"accountId": jira_util.ROLE_ACCOUNT_IDS["human"], "displayName": "Rainer Blind"},
                            "items": [{"field": "status", "toString": "Approved"}],
                        }
                    ]
                }
            return {}

        mock_request.side_effect = mock_jira

        with tempfile.TemporaryDirectory() as tmpdir:
            subprocess.run(["git", "init"], cwd=tmpdir, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "config", "user.name", "Tester"], cwd=tmpdir, check=True)
            subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=tmpdir, check=True)
            subprocess.run(["git", "commit", "--allow-empty", "-m", "ATT-9101: Test commit"], cwd=tmpdir, check=True, stdout=subprocess.DEVNULL)

            for subfolder, suffix in [
                ("analysis", "_analysis.md"),
                ("test_specs", "_test_spec.md"),
                ("plans", "_plan.md"),
                ("walkthroughs", "_walkthrough.md"),
            ]:
                d = os.path.join(tmpdir, "docs", "engineering", subfolder)
                os.makedirs(d, exist_ok=True)
                with open(os.path.join(d, f"ATT-9101{suffix}"), "w", encoding="utf-8") as f:
                    f.write("# Deliverable citing REQ-SHARD-001")

            shards_dir = os.path.join(tmpdir, "docs", "requirements")
            os.makedirs(shards_dir, exist_ok=True)
            with open(os.path.join(shards_dir, "req_test_domain.md"), "w", encoding="utf-8") as f:
                f.write("| Requirement ID | Summary |\n| :--- | :--- |\n| REQ-SHARD-001 | Shard req | ATT-9101 |\n")

            out_file = os.path.join(tmpdir, "audit.md")
            _, results = jira_util.qa_audit(sample_size=1, out_path=out_file, role="qa", repo_root=tmpdir)
            self.assertEqual(len(results), 1)
            self.assertTrue(results[0]["living_doc_pass"])

    def test_tst_pro_091_8_issue_description_ingestion(self):
        # TST-PRO-091.8: Issue Description Ingestion Test
        self.assertEqual(jira_util.extract_text_from_description(None), "")
        self.assertEqual(jira_util.extract_text_from_description("Direct string REQ-PRO-059"), "Direct string REQ-PRO-059")

        adf = {
            "version": 1,
            "type": "doc",
            "content": [
                {
                    "type": "paragraph",
                    "content": [
                        {"type": "text", "text": "ADF paragraph mentioning "},
                        {"type": "text", "text": "REQ-PRO-060"},
                    ],
                }
            ],
        }
        extracted = jira_util.extract_text_from_description(adf)
        self.assertIn("REQ-PRO-060", extracted)

    @patch("jira_util.jira_request")
    def test_tst_pro_091_9_account_id_role_separation(self, mock_request):
        # TST-PRO-091.9: AccountId Role Separation Test
        def make_mock(reviewer_account_id):
            return {
                "issues": [
                    {
                        "key": "ATT-9201",
                        "fields": {
                            "summary": "Ticket testing accountId separation",
                            "status": {"name": "Erledigt"},
                            "issuetype": {"subtask": False},
                            "subtasks": [
                                {
                                    "key": "ATT-9202",
                                    "fields": {"summary": "[Analysis] Task", "status": {"name": "Erledigt"}},
                                    "comments": [{"author": {"accountId": reviewer_account_id, "displayName": "Reviewer"}, "body": "GATE_VERDICT: PASS"}],
                                },
                                {
                                    "key": "ATT-9203",
                                    "fields": {"summary": "[Spec] Task", "status": {"name": "Erledigt"}},
                                    "comments": [{"author": {"accountId": reviewer_account_id, "displayName": "Reviewer"}, "body": "GATE_VERDICT: PASS"}],
                                },
                                {
                                    "key": "ATT-9204",
                                    "fields": {"summary": "[Plan] Task", "status": {"name": "Erledigt"}},
                                    "comments": [{"author": {"accountId": reviewer_account_id, "displayName": "Reviewer"}, "body": "GATE_VERDICT: PASS"}],
                                },
                                {
                                    "key": "ATT-9205",
                                    "fields": {"summary": "[Impl] Task", "status": {"name": "Erledigt"}},
                                    "comments": [{"author": {"accountId": reviewer_account_id, "displayName": "Reviewer"}, "body": "GATE_VERDICT: PASS"}],
                                },
                                {
                                    "key": "ATT-9206",
                                    "fields": {"summary": "[Verif] Task", "status": {"name": "Erledigt"}},
                                    "comments": [{"author": {"accountId": reviewer_account_id, "displayName": "Reviewer"}, "body": "GATE_VERDICT: PASS"}],
                                },
                            ],
                        },
                    }
                ]
            }

        agent1_id = jira_util.ROLE_ACCOUNT_IDS["agent1"]
        agent2_id = jira_util.ROLE_ACCOUNT_IDS["agent2"]

        with tempfile.TemporaryDirectory() as tmpdir:
            # 1. Review by Agent 2 (valid separation)
            mock_request.side_effect = lambda url, **kwargs: (
                {"values": [{"id": 1, "name": "B"}]} if "/board" in url and "/sprint" not in url
                else {"values": [{"id": 440, "name": "S", "state": "active"}]} if "/sprint?state=active" in url
                else make_mock(agent2_id) if "/sprint/440/issue" in url
                else {"values": [{"author": {"accountId": jira_util.ROLE_ACCOUNT_IDS["human"]}, "items": [{"field": "status", "toString": "Approved"}]}]}
            )
            _, res_valid = jira_util.qa_audit(sample_size=1, out_path=os.path.join(tmpdir, "a1.md"), role="qa", repo_root=tmpdir)
            self.assertTrue(res_valid[0]["role_sep_pass"])

            # 2. Review by Agent 1 (violation)
            mock_request.side_effect = lambda url, **kwargs: (
                {"values": [{"id": 1, "name": "B"}]} if "/board" in url and "/sprint" not in url
                else {"values": [{"id": 440, "name": "S", "state": "active"}]} if "/sprint?state=active" in url
                else make_mock(agent1_id) if "/sprint/440/issue" in url
                else {"values": [{"author": {"accountId": jira_util.ROLE_ACCOUNT_IDS["human"]}, "items": [{"field": "status", "toString": "Approved"}]}]}
            )
            _, res_invalid = jira_util.qa_audit(sample_size=1, out_path=os.path.join(tmpdir, "a2.md"), role="qa", repo_root=tmpdir)
            self.assertFalse(res_invalid[0]["role_sep_pass"])

    @patch("jira_util.jira_request")
    def test_tst_pro_091_10_seeded_random_sampling(self, mock_request):
        # TST-PRO-091.10: Seeded Random Sampling Test
        ten_issues = [
            {
                "key": f"ATT-93{i:02d}",
                "fields": {
                    "summary": f"Issue {i}",
                    "status": {"name": "Erledigt"},
                    "issuetype": {"subtask": False},
                    "subtasks": [],
                },
            }
            for i in range(10)
        ]

        def mock_jira(url, **kwargs):
            if "/board" in url and "/sprint" not in url:
                return {"values": [{"id": 1, "name": "B"}]}
            elif "/sprint?state=active" in url:
                return {"values": [{"id": 440, "name": "S", "state": "active"}]}
            elif "/sprint/440/issue" in url:
                return {"issues": ten_issues}
            return {}

        mock_request.side_effect = mock_jira

        with tempfile.TemporaryDirectory() as tmpdir:
            out_file1 = os.path.join(tmpdir, "audit1.md")
            out_file2 = os.path.join(tmpdir, "audit2.md")
            out_file3 = os.path.join(tmpdir, "audit3.md")

            # Seed 42 runs
            _, res1 = jira_util.qa_audit(sample_size=3, seed=42, out_path=out_file1, role="qa", repo_root=tmpdir)
            _, res2 = jira_util.qa_audit(sample_size=3, seed=42, out_path=out_file2, role="qa", repo_root=tmpdir)
            # Seed 99 runs
            _, res3 = jira_util.qa_audit(sample_size=3, seed=99, out_path=out_file3, role="qa", repo_root=tmpdir)

            keys1 = [r["key"] for r in res1]
            keys2 = [r["key"] for r in res2]
            keys3 = [r["key"] for r in res3]

            self.assertEqual(keys1, keys2)
            self.assertEqual(len(keys1), 3)

            with open(out_file1, "r", encoding="utf-8") as f:
                report_content = f.read()

            self.assertIn("Random Seed", report_content)
            self.assertIn("`42`", report_content)
            for k in keys1:
                self.assertIn(k, report_content)

    @patch("jira_util.jira_request")
    def test_tst_pro_091_11_dedicated_corrective_action_creation_and_deduplication(self, mock_request):
        # TST-PRO-091.11: Dedicated Corrective Action Ticket Creation & Deduplication Test
        created_issues = []
        def mock_jira(url, method="GET", payload=None, role="qa"):
            if "/rest/agile/1.0/board" in url and "/sprint" not in url:
                return {"values": [{"id": 1, "name": "B"}]}
            elif "/sprint?state=active" in url:
                return {"values": [{"id": 440, "name": "Sprint 2026-48.1", "state": "active"}]}
            elif "/rest/agile/1.0/sprint/440/issue" in url:
                return {
                    "issues": [
                        {
                            "key": "ATT-9401",
                            "fields": {
                                "summary": "Ticket 1 failing role separation",
                                "status": {"name": "Erledigt"},
                                "issuetype": {"subtask": False},
                                "subtasks": [
                                    {"key": "ATT-9402", "fields": {"summary": "[Analysis] Task", "status": {"name": "Erledigt"}}, "comments": [{"author": {"accountId": jira_util.ROLE_ACCOUNT_IDS["agent1"]}, "body": "GATE_VERDICT: PASS"}]}
                                ],
                            },
                        },
                        {
                            "key": "ATT-9403",
                            "fields": {
                                "summary": "Ticket 2 failing role separation",
                                "status": {"name": "Erledigt"},
                                "issuetype": {"subtask": False},
                                "subtasks": [
                                    {"key": "ATT-9404", "fields": {"summary": "[Analysis] Task", "status": {"name": "Erledigt"}}, "comments": [{"author": {"accountId": jira_util.ROLE_ACCOUNT_IDS["agent1"]}, "body": "GATE_VERDICT: PASS"}]}
                                ],
                            },
                        },
                    ]
                }
            elif method == "POST" and "/rest/api/2/issue" in url:
                new_key = f"ATT-CA-{len(created_issues) + 100}"
                created_issues.append({"key": new_key, "payload": payload})
                return {"key": new_key}
            elif "/rest/api/2/search" in url:
                return {"issues": []}
            return {}

        mock_request.side_effect = mock_jira

        with tempfile.TemporaryDirectory() as tmpdir:
            out_file = os.path.join(tmpdir, "audit.md")
            _, results = jira_util.qa_audit(sample_size=2, out_path=out_file, role="qa", repo_root=tmpdir)
            self.assertEqual(len(results), 2)
            self.assertFalse(results[0]["compliant"])
            self.assertFalse(results[1]["compliant"])

            # Verify deduplicated corrective action ticket created
            self.assertIn("ca_keys", results[0])
            self.assertIn("ca_keys", results[1])
            self.assertEqual(results[0]["ca_keys"], results[1]["ca_keys"])
            self.assertNotEqual(results[0]["ca_keys"][0], results[0]["key"])
            self.assertNotEqual(results[1]["ca_keys"][0], results[1]["key"])

            with open(out_file, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("Corrective Action Ticket:", content)
            self.assertNotIn("Corrective Action Ticket: [ATT-9401]", content)
            self.assertNotIn("Corrective Action Ticket: [ATT-9403]", content)

    def test_tst_pro_091_13_ceremony2_retrospective_qa_tracking_protocol(self):
        # TST-PRO-091.13: Ceremony 2 Retrospective QA Tracking Protocol Test
        skill_path = os.path.join(REPO_ROOT, ".agents", "skills", "sprint-planner", "SKILL.md")
        with open(skill_path, "r", encoding="utf-8") as f:
            skill_text = f.read()
        self.assertIn("QA Findings & Corrective Actions", skill_text)
        self.assertIn("> 2 sprints", skill_text)
        self.assertIn("PO Escalation", skill_text)


if __name__ == "__main__":
    unittest.main()

