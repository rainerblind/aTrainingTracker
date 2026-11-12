#!/usr/bin/env python3
"""
tools/hoechstmass/tools/test_hoechstmass_tools.py

Unit tests for Höchstmaß parameterized automation and governance tools.
Executes with standard library unittest.
"""

import json
import os
import sys
import unittest

_tools_dir = os.path.dirname(os.path.abspath(__file__))
_repo_root = os.path.abspath(os.path.join(_tools_dir, "..", "..", ".."))
if _repo_root not in sys.path:
    sys.path.insert(0, _repo_root)

from tools.hoechstmass.tools.jira_util import (
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
    list_epics,
    close_epic,
    paged_jql_search,
    transition_issue,
    PARENT_STAGE_ORDER,
    STAGE_TO_SKILL_DIR,
    resolve_desired_parent_stage_for_subtask,
    count_final_review_bounces,
    final_review_verdict,
    export_gates,
    VALID_ROLES,
)
from tools.hoechstmass.tools.verify_requirement_governance import (
    load_config,
    validate_archaeology_section,
    verify_engineering_directory_structure,
)
from tools.hoechstmass.tools.parallel_sprint_lock import (
    sprint_lock,
    is_lock_busy,
    run_locked,
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

        # Fallback to default is prohibited for agent roles under ASPICE SUP.1 (ATT-6146)
        with self.assertRaises(SystemExit) as cm:
            resolve_account("coordinator", config=env_config)
        self.assertEqual(cm.exception.code, 1)

        # Fallback to default is permitted for human role
        u_human, t_human = resolve_account("human", config=env_config)
        self.assertEqual(u_human, "fallback@example.com")
        self.assertEqual(t_human, "fallback_token")

    def test_datacenter_helpers_and_auth(self):
        dc_config = {
            "JIRA_URL": "https://jira.internal.corp",
            "JIRA_DEPLOYMENT": "datacenter",
            "JIRA_AGENT1_NAME": "dc_agent1_user",
            "JIRA_AGENT1_TOKEN": "datacenter_pat_token_abc",
        }
        self.assertTrue(is_datacenter(dc_config))
        self.assertEqual(get_search_endpoint(dc_config), "https://jira.internal.corp/rest/api/2/search")
        self.assertEqual(get_epic_link_field(dc_config), "customfield_10014")
        self.assertEqual(resolve_assignee_identifier("agent1", config=dc_config), "dc_agent1_user")

        headers = get_headers(dc_config, role="agent1")
        self.assertEqual(headers["Authorization"], "Bearer datacenter_pat_token_abc")

    def test_valid_roles_excludes_human(self):
        self.assertNotIn("human", VALID_ROLES)
        self.assertEqual(VALID_ROLES, ["agent1", "agent2", "coordinator"])

    def test_load_env_config_per_role(self):
        """load_env_config(role=...) loads per-role files and isolates role credentials (ATT-6147)."""
        import tempfile
        with tempfile.TemporaryDirectory() as tmpdir:
            base_file = os.path.join(tmpdir, ".env.jira")
            with open(base_file, "w") as f:
                f.write("JIRA_URL=https://jira.corp.internal\nJIRA_AGENT1_USER=old@corp\n")
            a1_file = os.path.join(tmpdir, ".env.jira.agent1")
            with open(a1_file, "w") as f:
                f.write("JIRA_AGENT1_USER=a1@corp\nJIRA_AGENT1_TOKEN=t1\n")
            a2_file = os.path.join(tmpdir, ".env.jira.agent2")
            with open(a2_file, "w") as f:
                f.write("JIRA_AGENT2_USER=a2@corp\nJIRA_AGENT2_TOKEN=t2\n")

            cfg_a1 = load_env_config(custom_path=base_file, role="agent1")
            self.assertEqual(cfg_a1.get("JIRA_AGENT1_USER"), "a1@corp")
            self.assertEqual(cfg_a1.get("JIRA_AGENT1_TOKEN"), "t1")
            self.assertNotIn("JIRA_AGENT2_USER", cfg_a1)

            cfg_a2 = load_env_config(custom_path=base_file, role="agent2")
            self.assertEqual(cfg_a2.get("JIRA_AGENT2_USER"), "a2@corp")
            self.assertEqual(cfg_a2.get("JIRA_AGENT2_TOKEN"), "t2")
            self.assertNotIn("JIRA_AGENT1_USER", cfg_a2)


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


class TestEpicCommands(unittest.TestCase):
    """TST-PRO-040.3: Hoechstmass parameterized epic commands."""

    def test_close_epic_role_guard_rejects_agent(self):
        import io
        from unittest.mock import patch
        for role in ["agent1", "agent2"]:
            with self.assertRaises(SystemExit) as cm:
                err = io.StringIO()
                with patch("sys.stderr", err):
                    close_epic("PROJ-100", role=role)
            self.assertEqual(cm.exception.code, 1)
            self.assertIn(f"Role '{role}' is not authorized to close Epics", err.getvalue())

    def test_transition_issue_blocks_parent_to_erledigt(self):
        import io
        from unittest.mock import patch
        with patch("tools.hoechstmass.tools.jira_util.jira_request") as mock_req:
            mock_req.return_value = {
                "key": "PROJ-100",
                "fields": {
                    "issuetype": {"name": "Story", "subtask": False},
                    "status": {"name": "In Progress"}
                }
            }
            with self.assertRaises(SystemExit) as cm:
                err = io.StringIO()
                with patch("sys.stderr", err):
                    transition_issue("PROJ-100", "erledigt", as_role="agent1", allow_epic_close=False)
            self.assertEqual(cm.exception.code, 1)
            self.assertIn("strictly prohibited for AI agents", err.getvalue())

    def test_stage6_final_review_state_and_bounce_logic(self):
        from unittest.mock import patch
        self.assertIn("final review (agent)", PARENT_STAGE_ORDER)
        self.assertEqual(STAGE_TO_SKILL_DIR.get("final review (agent)"), "stage6-final-review")
        self.assertEqual(
            resolve_desired_parent_stage_for_subtask("[Test] Verification", True),
            "final review (agent)"
        )
        comments = [
            {"body": "[ROLE: agent2] Stage 6 Final Review (Agent) BOUNCE (1/3): Defect 1"},
            {"body": "[ROLE: agent2] [FINAL REVIEW (AGENT) — BOUNCE 2/3]\nDefect 2"},
        ]
        self.assertEqual(count_final_review_bounces(comments), 2)

        with patch("tools.hoechstmass.tools.jira_util.jira_request") as mock_req, \
             patch("tools.hoechstmass.tools.jira_util.assign_issue"), \
             patch("tools.hoechstmass.tools.jira_util.add_comment"), \
             patch("tools.hoechstmass.tools.jira_util.transition_issue") as mock_trans:
            mock_req.return_value = {
                "key": "PROJ-101",
                "fields": {
                    "issuetype": {"name": "Story", "subtask": False},
                    "status": {"name": "Final Review (Agent)"},
                    "comment": {"comments": comments},
                },
            }
            res = final_review_verdict("PROJ-101", "BOUNCE", "Defect 3", bounce_target="implementation", as_role="agent2")
            self.assertEqual(res, "BOUNCE_3")
            mock_trans.assert_called()

    def test_export_gates_parameterized(self):
        """TST-PRO-059.1: Parameterized export_gates in hoechstmass."""
        import tempfile
        from unittest.mock import patch

        def mock_side_effect(url, method="GET", payload=None, role=None, **kwargs):
            if "/rest/agile/1.0/board/1/sprint?state=active" in url:
                return {"values": [{"id": 100, "name": "Sprint 100"}]}
            elif "/rest/agile/1.0/sprint/100/issue" in url:
                return {
                    "issues": [
                        {
                            "key": "PROJ-101",
                            "fields": {
                                "summary": "Sample Task",
                                "status": {"name": "Implementation"},
                                "issuetype": {"subtask": False},
                                "comment": {"comments": []},
                                "subtasks": [
                                    {
                                        "key": "PROJ-102",
                                        "fields": {
                                            "summary": "[Analysis] Task",
                                            "status": {"name": "Erledigt"},
                                        },
                                        "comments": [
                                            {
                                                "body": "[Automated comment by AI Agent 2 (Auditor)]\n\nGATE_VERDICT: PASS\nAnalysis verified.",
                                                "author": {"displayName": "Agent 2", "accountId": "a2"},
                                                "created": "2026-11-12T01:00:00Z",
                                            }
                                        ],
                                    }
                                ],
                            },
                        }
                    ]
                }
            return {}

        with patch("tools.hoechstmass.tools.jira_util.jira_request") as mock_req:
            mock_req.side_effect = mock_side_effect
            with tempfile.TemporaryDirectory() as tmpdir:
                out_file = os.path.join(tmpdir, "docs", "engineering", "gates", "sprint_100.md")
                res_path, records = export_gates(sprint_arg=None, out_path=out_file, as_role="coordinator")
                self.assertEqual(res_path, out_file)
                self.assertTrue(os.path.exists(out_file))
                self.assertEqual(len(records), 1)
                with open(out_file, "r", encoding="utf-8") as f:
                    content = f.read()
                self.assertIn("# Sprint Quality Gate Records: Sprint 100", content)
                self.assertIn("| PROJ-101 | PROJ-102 | Gate 1 | Analysis (Gate 1) | PASS | agent2 | Agent 2 |", content)


class TestHoechstmassFlock(unittest.TestCase):
    """TST-PRO-041.2: Hoechstmass flock serialization tests."""

    def setUp(self):
        import tempfile
        self.temp_dir = tempfile.TemporaryDirectory()
        self.lock_file = os.path.join(self.temp_dir.name, "test_hoechstmass.lock")

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_run_locked_success(self):
        import sys
        ret = run_locked([sys.executable, "-c", "import sys; sys.exit(0)"], lock_path=self.lock_file)
        self.assertEqual(ret, 0)
        self.assertFalse(is_lock_busy(self.lock_file))

    def test_run_locked_exit_code_propagation(self):
        import sys
        ret = run_locked([sys.executable, "-c", "import sys; sys.exit(17)"], lock_path=self.lock_file)
        self.assertEqual(ret, 17)
        self.assertFalse(is_lock_busy(self.lock_file))

    def test_context_manager_lifecycle(self):
        self.assertFalse(is_lock_busy(self.lock_file))
        with sprint_lock(lock_path=self.lock_file):
            self.assertTrue(is_lock_busy(self.lock_file))
        self.assertFalse(is_lock_busy(self.lock_file))

    def test_timeout_on_contention(self):
        import sys
        with sprint_lock(lock_path=self.lock_file):
            with self.assertRaises(TimeoutError):
                run_locked([sys.executable, "-c", "pass"], lock_path=self.lock_file, timeout_sec=0.3)


if __name__ == "__main__":
    unittest.main()
