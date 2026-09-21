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

    def test_atomic_pair_fallback_when_token_missing(self):
        """When role user is set but role token is missing, atomically fall back to default credentials."""
        config = {
            "JIRA_USER": "fallback_user@example.com",
            "JIRA_TOKEN": "fallback_token_12345",
            "JIRA_AGENT1_USER": "agent1@example.com",
            "JIRA_AGENT1_TOKEN": "",  # Missing token
        }
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            user, token = jira_util.resolve_account("agent1", config)
        self.assertEqual(user, "fallback_user@example.com")
        self.assertEqual(token, "fallback_token_12345")
        log_output = stderr_capture.getvalue()
        self.assertIn("Advisory", log_output)
        self.assertIn("agent1", log_output)
        # Verify secret masking: neither token is exposed in stderr
        self.assertNotIn("fallback_token_12345", log_output)

    def test_atomic_pair_fallback_when_user_missing(self):
        """When role token is set but role user is missing, atomically fall back to default credentials."""
        config = {
            "JIRA_USER": "fallback_user@example.com",
            "JIRA_TOKEN": "fallback_token_12345",
            "JIRA_COORDINATOR_TOKEN": "coordinator_secret_token",
            # JIRA_COORDINATOR_USER is absent
        }
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            user, token = jira_util.resolve_account("coordinator", config)
        self.assertEqual(user, "fallback_user@example.com")
        self.assertEqual(token, "fallback_token_12345")
        log_output = stderr_capture.getvalue()
        self.assertIn("Advisory", log_output)
        self.assertIn("coordinator", log_output)
        self.assertNotIn("coordinator_secret_token", log_output)

    def test_clean_fallback_when_role_unconfigured(self):
        """When role keys are not defined at all, fall back cleanly without mixing."""
        config = {
            "JIRA_USER": "default_user@example.com",
            "JIRA_TOKEN": "default_token_xyz",
        }
        user, token = jira_util.resolve_account("agent2", config)
        self.assertEqual(user, "default_user@example.com")
        self.assertEqual(token, "default_token_xyz")

    def test_terminal_failure_when_no_credentials_exist(self):
        """When neither role nor default credentials exist, exit code 1 is triggered with clear diagnostic."""
        config = {}
        stderr_capture = io.StringIO()
        with patch("sys.stderr", stderr_capture):
            with self.assertRaises(SystemExit) as cm:
                jira_util.resolve_account("agent1", config)
            self.assertEqual(cm.exception.code, 1)
        err_msg = stderr_capture.getvalue()
        self.assertIn("Error: No Jira credentials found", err_msg)

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


class TestRolePrecedenceAndAttribution(unittest.TestCase):
    """Verifies REQ-PRO-020 / TST-PRO-013: Role Locking, Precedence, Anti-Spoofing & Actor Comment Attribution."""

    def test_role_precedence_cli_over_env_and_default(self):
        """CLI flag --as <role> takes highest priority over JIRA_ACTOR and default."""
        with patch.dict(os.environ, {"JIRA_ACTOR": "agent2"}):
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
            mock_request.assert_called_once()
            _, kwargs = mock_request.call_args
            self.assertEqual(kwargs.get("role"), "coordinator")

    def test_human_gate_transition_blocked_across_all_roles(self):
        """Attempting to transition to Erledigt is blocked across all roles."""
        prohibited_targets = ["erledigt", "Erledigt", "DONE", "freigabe erteilt", "Freigabe erteilt"]
        for target in prohibited_targets:
            for role in ["agent1", "agent2", "coordinator"]:
                stderr_capture = io.StringIO()
                stdout_capture = io.StringIO()
                with patch("sys.stderr", stderr_capture), patch("sys.stdout", stdout_capture):
                    with self.assertRaises(SystemExit) as cm:
                        jira_util.transition_issue("ATT-1202", target, role=role)
                    self.assertEqual(cm.exception.code, 1)


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


if __name__ == "__main__":
    unittest.main()
