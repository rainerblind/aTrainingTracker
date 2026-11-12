#!/usr/bin/env python3
"""
Unit tests for Jira Data Center & Enterprise Support (REQ-PRO-039, TST-PRO-039).
Verifies:
- TST-PRO-039.1: Deployment Mode & Config Parsing (Cloud default, Data Center detection)
- TST-PRO-039.2: Bearer PAT & Corporate CA Bundle / TLS Verification
- TST-PRO-039.3: Username vs AccountId Assignee Dispatch
- TST-PRO-039.4: Epic Link Custom Field vs Parent Payload Dispatch
- TST-PRO-039.5: JQL Search API v2 vs v3 Routing
- TST-PRO-039.6: Tooling Parity between root and hoechstmass jira_util.py
"""

import base64
import importlib.util
import os
import ssl
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


class TestJiraDataCenterConfig(unittest.TestCase):
    """TST-PRO-039.1: Deployment Mode & Config Parsing."""

    def test_default_deployment_is_cloud(self):
        empty_config = {"JIRA_URL": "https://example.atlassian.net"}
        self.assertFalse(root_jira.is_datacenter(empty_config))
        self.assertFalse(hm_jira.is_datacenter(empty_config))

    def test_env_deployment_override_datacenter(self):
        dc_env = {"JIRA_DEPLOYMENT": "datacenter", "JIRA_URL": "https://jira.internal.corp"}
        self.assertTrue(root_jira.is_datacenter(dc_env))
        self.assertTrue(hm_jira.is_datacenter(dc_env))

    def test_env_deployment_aliases(self):
        for alias in ["server", "onprem", "on_premise", "data_center"]:
            env = {"JIRA_DEPLOYMENT": alias}
            self.assertTrue(root_jira.is_datacenter(env), f"Failed for alias {alias}")
            self.assertTrue(hm_jira.is_datacenter(env), f"Failed for alias {alias} in hm")

    def test_hoechstmass_config_deployment_type(self):
        h_cfg = {"deployment_type": "datacenter"}
        env = {}
        self.assertTrue(root_jira.is_datacenter(env, h_config=h_cfg))
        self.assertTrue(hm_jira.is_datacenter(env, h_config=h_cfg))

    def test_epic_link_custom_field_resolution(self):
        # Default
        self.assertEqual(root_jira.get_epic_link_field({}), "customfield_10014")
        self.assertEqual(hm_jira.get_epic_link_field({}), "customfield_10014")

        # Environment variable override
        env = {"JIRA_EPIC_LINK_FIELD": "customfield_12345"}
        self.assertEqual(root_jira.get_epic_link_field(env), "customfield_12345")
        self.assertEqual(hm_jira.get_epic_link_field(env), "customfield_12345")

        # Config JSON override
        h_cfg = {"custom_fields": {"epic_link": "customfield_99999"}}
        self.assertEqual(root_jira.get_epic_link_field({}, h_config=h_cfg), "customfield_99999")
        self.assertEqual(hm_jira.get_epic_link_field({}, h_config=h_cfg), "customfield_99999")


class TestBearerAuthAndTLS(unittest.TestCase):
    """TST-PRO-039.2: Bearer PAT & Corporate CA Bundle Verification."""

    def test_bearer_header_in_datacenter_with_pat(self):
        dc_config = {
            "JIRA_URL": "https://jira.internal.corp",
            "JIRA_DEPLOYMENT": "datacenter",
            "JIRA_AGENT1_TOKEN": "my_datacenter_pat_token_xyz"
        }
        headers = root_jira.get_headers(dc_config, role="agent1")
        self.assertEqual(headers["Authorization"], "Bearer my_datacenter_pat_token_xyz")

        hm_headers = hm_jira.get_headers(dc_config, role="agent1")
        self.assertEqual(hm_headers["Authorization"], "Bearer my_datacenter_pat_token_xyz")

    def test_explicit_bearer_auth_type(self):
        config = {
            "JIRA_URL": "https://jira.internal.corp",
            "JIRA_AUTH_TYPE": "bearer",
            "JIRA_AGENT1_USER": "some_user",
            "JIRA_AGENT1_TOKEN": "pat_token_123"
        }
        headers = root_jira.get_headers(config, role="agent1")
        self.assertEqual(headers["Authorization"], "Bearer pat_token_123")
        self.assertEqual(hm_jira.get_headers(config, role="agent1")["Authorization"], "Bearer pat_token_123")

    def test_basic_auth_in_cloud_retained(self):
        cloud_config = {
            "JIRA_URL": "https://company.atlassian.net",
            "JIRA_AGENT1_USER": "dev@company.com",
            "JIRA_AGENT1_TOKEN": "api_token_abc"
        }
        headers = root_jira.get_headers(cloud_config, role="agent1")
        expected_b64 = base64.b64encode(b"dev@company.com:api_token_abc").decode("ascii")
        self.assertEqual(headers["Authorization"], f"Basic {expected_b64}")

        hm_headers = hm_jira.get_headers(cloud_config, role="agent1")
        self.assertEqual(hm_headers["Authorization"], f"Basic {expected_b64}")

    def test_ssl_verify_disabled(self):
        config = {"JIRA_SSL_VERIFY": "false"}
        ctx = root_jira.get_ssl_context(config)
        self.assertIsNotNone(ctx)
        self.assertFalse(ctx.check_hostname)
        self.assertEqual(ctx.verify_mode, ssl.CERT_NONE)

        hm_ctx = hm_jira.get_ssl_context(config)
        self.assertIsNotNone(hm_ctx)
        self.assertFalse(hm_ctx.check_hostname)
        self.assertEqual(hm_ctx.verify_mode, ssl.CERT_NONE)


class TestAssigneeDispatch(unittest.TestCase):
    """TST-PRO-039.3: Username vs AccountId Assignee Dispatch."""

    def test_resolve_assignee_identifier_datacenter(self):
        dc_config = {
            "JIRA_DEPLOYMENT": "datacenter",
            "JIRA_AGENT1_NAME": "jira_agent1_user",
            "JIRA_AGENT1_ACCOUNT_ID": "should_be_ignored_in_dc"
        }
        self.assertEqual(root_jira.resolve_assignee_identifier("agent1", config=dc_config), "jira_agent1_user")
        self.assertEqual(hm_jira.resolve_assignee_identifier("agent1", config=dc_config), "jira_agent1_user")

    def test_assign_issue_datacenter_payload(self):
        dc_config = {
            "JIRA_URL": "https://jira.internal.corp",
            "JIRA_DEPLOYMENT": "datacenter",
            "JIRA_AGENT1_NAME": "dc_user_agent1",
            "JIRA_USER": "fallback",
            "JIRA_TOKEN": "token"
        }
        with patch.object(root_jira, "jira_request") as mock_request:
            with patch.object(root_jira, "get_config", return_value=dc_config):
                root_jira.assign_issue("PROJ-100", "agent1")
            mock_request.assert_called_once_with(
                "https://jira.internal.corp/rest/api/2/issue/PROJ-100/assignee",
                method="PUT",
                payload={"name": "dc_user_agent1"},
                role="agent1"
            )

    def test_hm_assign_issue_datacenter_payload(self):
        dc_config = {
            "JIRA_URL": "https://jira.internal.corp",
            "JIRA_DEPLOYMENT": "datacenter",
            "JIRA_AGENT1_NAME": "dc_user_agent1",
            "JIRA_USER": "fallback",
            "JIRA_TOKEN": "token"
        }
        with patch.object(hm_jira, "jira_request") as mock_request:
            with patch.object(hm_jira, "load_env_config", return_value=dc_config):
                hm_jira.assign_issue("PROJ-100", "agent1")
            mock_request.assert_called_once()
            args, kwargs = mock_request.call_args
            self.assertEqual(kwargs.get("payload"), {"name": "dc_user_agent1"})

    def test_assign_issue_cloud_payload(self):
        cloud_config = {
            "JIRA_URL": "https://company.atlassian.net",
            "JIRA_DEPLOYMENT": "cloud",
            "JIRA_AGENT1_ACCOUNT_ID": "712020:uuid-agent1",
            "JIRA_USER": "dev@company.com",
            "JIRA_TOKEN": "token"
        }
        with patch.object(root_jira, "jira_request") as mock_request:
            with patch.object(root_jira, "get_config", return_value=cloud_config):
                root_jira.assign_issue("ATT-500", "agent1")
            mock_request.assert_called_once_with(
                "https://company.atlassian.net/rest/api/2/issue/ATT-500/assignee",
                method="PUT",
                payload={"accountId": "712020:uuid-agent1"},
                role="agent1"
            )


class TestEpicLinkAndParentDispatch(unittest.TestCase):
    """TST-PRO-039.4: Epic Link Custom Field vs Parent Dispatch."""

    def test_create_issue_datacenter_sets_epic_link(self):
        dc_config = {
            "JIRA_URL": "https://jira.internal.corp",
            "JIRA_DEPLOYMENT": "datacenter",
            "JIRA_EPIC_LINK_FIELD": "customfield_10014",
            "JIRA_USER": "dev",
            "JIRA_TOKEN": "token"
        }
        with patch.object(root_jira, "jira_request") as mock_request:
            mock_request.side_effect = [
                {"fields": {"issuetype": {"name": "Epic", "id": "10001"}}},  # resolve_valid_epic_key
                {"key": "PROJ-101"}  # create_issue response
            ]
            with patch.object(root_jira, "get_config", return_value=dc_config):
                key = root_jira.create_issue("Story Title", "Description", parent_key="PROJ-50", rank_top=False)
            self.assertEqual(key, "PROJ-101")
            create_call = mock_request.call_args_list[1]
            payload = create_call[1]["payload"]
            self.assertIn("customfield_10014", payload["fields"])
            self.assertEqual(payload["fields"]["customfield_10014"], "PROJ-50")
            self.assertNotIn("parent", payload["fields"])

    def test_set_parent_datacenter_sets_epic_link(self):
        dc_config = {
            "JIRA_URL": "https://jira.internal.corp",
            "JIRA_DEPLOYMENT": "datacenter",
            "JIRA_EPIC_LINK_FIELD": "customfield_10014",
            "JIRA_USER": "dev",
            "JIRA_TOKEN": "token"
        }
        with patch.object(root_jira, "jira_request") as mock_request:
            with patch.object(root_jira, "get_config", return_value=dc_config):
                root_jira.set_parent("PROJ-102", "PROJ-50")
            mock_request.assert_called_once_with(
                "https://jira.internal.corp/rest/api/2/issue/PROJ-102",
                method="PUT",
                payload={"fields": {"customfield_10014": "PROJ-50"}},
                role="agent1"
            )

    def test_create_subtask_datacenter_preserves_parent(self):
        dc_config = {
            "JIRA_URL": "https://jira.internal.corp",
            "JIRA_DEPLOYMENT": "datacenter",
            "JIRA_USER": "dev",
            "JIRA_TOKEN": "token"
        }
        with patch.object(root_jira, "jira_request") as mock_request:
            mock_request.side_effect = [
                {"fields": {"summary": "Parent Issue Summary"}},
                {"key": "PROJ-103"}
            ]
            with patch.object(root_jira, "get_config", return_value=dc_config):
                with patch.object(root_jira, "assign_issue"):
                    root_jira.create_subtask("PROJ-100", "[Analysis] Problem domain", "Details")
            create_call = mock_request.call_args_list[1]
            payload = create_call[1]["payload"]
            self.assertIn("parent", payload["fields"])
            self.assertEqual(payload["fields"]["parent"], {"key": "PROJ-100"})
            self.assertNotIn("customfield_10014", payload["fields"])


class TestJQLSearchRouting(unittest.TestCase):
    """TST-PRO-039.5: JQL Search API v2 Routing."""

    def test_search_endpoint_datacenter(self):
        dc_config = {"JIRA_URL": "https://jira.internal.corp", "JIRA_DEPLOYMENT": "datacenter"}
        self.assertEqual(root_jira.get_search_endpoint(dc_config), "https://jira.internal.corp/rest/api/2/search")
        self.assertEqual(hm_jira.get_search_endpoint(dc_config), "https://jira.internal.corp/rest/api/2/search")

    def test_search_endpoint_cloud(self):
        cloud_config = {"JIRA_URL": "https://company.atlassian.net", "JIRA_DEPLOYMENT": "cloud"}
        self.assertEqual(root_jira.get_search_endpoint(cloud_config), "https://company.atlassian.net/rest/api/3/search/jql")
        self.assertEqual(hm_jira.get_search_endpoint(cloud_config), "https://company.atlassian.net/rest/api/3/search/jql")

    def test_search_issues_datacenter_dispatch(self):
        dc_config = {
            "JIRA_URL": "https://jira.internal.corp",
            "JIRA_DEPLOYMENT": "datacenter",
            "JIRA_USER": "dev",
            "JIRA_TOKEN": "token"
        }
        with patch.object(root_jira, "jira_request") as mock_request:
            mock_request.return_value = {"issues": []}
            with patch.object(root_jira, "get_config", return_value=dc_config):
                root_jira.search_issues("project = PROJ")
            mock_request.assert_called_once_with(
                "https://jira.internal.corp/rest/api/2/search",
                method="POST",
                payload={"jql": "project = PROJ", "fields": ["summary", "status", "issuetype"]},
                role="agent1"
            )


class TestToolingParity(unittest.TestCase):
    """TST-PRO-039.6: Tooling Parity between root and hoechstmass."""

    def test_shared_functions_exist_in_both(self):
        funcs = [
            "is_datacenter",
            "get_epic_link_field",
            "get_search_endpoint",
            "get_ssl_context",
            "resolve_assignee_identifier",
            "resolve_account",
            "get_headers",
            "jira_request",
            "assign_issue",
            "search_issues",
        ]
        for f in funcs:
            self.assertTrue(hasattr(root_jira, f), f"root_jira missing {f}")
            self.assertTrue(hasattr(hm_jira, f), f"hm_jira missing {f}")


if __name__ == "__main__":
    unittest.main()
