#!/usr/bin/env python3
"""
tools/hoechstmass/tools/test_hoechstmass_tools.py

Unit tests for Höchstmaß parameterized automation and governance tools.
Executes with standard library unittest.
"""

import os
import unittest
import json

from jira_util import (
    load_hoechstmass_config,
    load_env_config,
    mask_secret,
    get_account_id_for_role,
    resolve_account,
    is_datacenter,
    get_epic_link_field,
    get_search_endpoint,
    get_headers,
    resolve_assignee_identifier,
)
from verify_requirement_governance import (
    load_config,
    validate_archaeology_section,
    verify_engineering_directory_structure,
)


class TestHoechstmassConfig(unittest.TestCase):

    def test_load_hoechstmass_config_structure(self):
        config = load_hoechstmass_config()
        self.assertIn("project_key", config)
        self.assertIn("transitions", config)
        self.assertIn("roles", config)
        self.assertIn("custom_fields", config)
        self.assertEqual(config.get("project_key"), "PROJ")

    def test_mask_secret(self):
        self.assertEqual(mask_secret(""), "")
        self.assertEqual(mask_secret("short"), "***")
        self.assertEqual(mask_secret("my_super_secret_token"), "my***en")

    def test_get_account_id_for_role(self):
        h_config = {
            "roles": {
                "agent1": "role_agent1_id",
                "agent2": "role_agent2_id",
                "coordinator": "role_coord_id",
                "human": "role_human_id",
            }
        }
        env_config = {
            "JIRA_AGENT1_ACCOUNT_ID": "env_agent1_id"
        }
        # Env override takes priority
        self.assertEqual(get_account_id_for_role("agent1", config=env_config, h_config=h_config), "env_agent1_id")
        # Fallback to json config
        self.assertEqual(get_account_id_for_role("agent2", config=env_config, h_config=h_config), "role_agent2_id")
        self.assertEqual(get_account_id_for_role("human", config=env_config, h_config=h_config), "role_human_id")

    def test_resolve_account_role_and_fallback(self):
        env_config = {
            "JIRA_AGENT1_USER": "agent1@example.com",
            "JIRA_AGENT1_TOKEN": "token1",
            "JIRA_USER": "fallback@example.com",
            "JIRA_TOKEN": "fallback_token",
        }
        # Specific role present
        u, t = resolve_account("agent1", config=env_config)
        self.assertEqual(u, "agent1@example.com")
        self.assertEqual(t, "token1")

        # Fallback to default
        u2, t2 = resolve_account("coordinator", config=env_config)
        self.assertEqual(u2, "fallback@example.com")
        self.assertEqual(t2, "fallback_token")

    def test_datacenter_helpers_and_auth(self):
        dc_config = {
            "JIRA_URL": "https://jira.internal.corp",
            "JIRA_DEPLOYMENT": "datacenter",
            "JIRA_AGENT1_NAME": "dc_agent1_user",
            "JIRA_TOKEN": "datacenter_pat_token_abc"
        }
        self.assertTrue(is_datacenter(dc_config))
        self.assertEqual(get_search_endpoint(dc_config), "https://jira.internal.corp/rest/api/2/search")
        self.assertEqual(get_epic_link_field(dc_config), "customfield_10014")
        self.assertEqual(resolve_assignee_identifier("agent1", config=dc_config), "dc_agent1_user")

        headers = get_headers(dc_config, role="agent1")
        self.assertEqual(headers["Authorization"], "Bearer datacenter_pat_token_abc")


class TestGovernanceVerification(unittest.TestCase):

    def test_validate_archaeology_section_valid(self):
        valid_text = """
### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: REQ-001 targeting Component
* `Historical Origin & Commit Trace`: Commit 123456 in Sprint 1
* `Root Reason for Existing Formulation`: Needed for thread safety
* `Preservation of Core Invariants`: Invariant remains preserved
"""
        is_valid, errors = validate_archaeology_section(valid_text)
        self.assertTrue(is_valid, f"Validation should succeed but failed with: {errors}")
        self.assertEqual(len(errors), 0)

    def test_validate_archaeology_section_missing_field(self):
        invalid_text = """
### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: REQ-001 targeting Component
* `Historical Origin & Commit Trace`: Commit 123456 in Sprint 1
* `Root Reason for Existing Formulation`: Needed for thread safety
"""
        is_valid, errors = validate_archaeology_section(invalid_text)
        self.assertFalse(is_valid)
        self.assertTrue(any("Preservation of Core Invariants" in e for e in errors))

    def test_validate_archaeology_section_placeholder_content(self):
        placeholder_text = """
### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: REQ-001
* `Historical Origin & Commit Trace`: TBD
* `Root Reason for Existing Formulation`: TODO
* `Preservation of Core Invariants`: None
"""
        is_valid, errors = validate_archaeology_section(placeholder_text)
        self.assertFalse(is_valid)
        self.assertTrue(len(errors) >= 3)


if __name__ == "__main__":
    unittest.main()
