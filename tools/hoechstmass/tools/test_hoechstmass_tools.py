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
    export_test_evidence,
    collect_sprint_metrics_for_id,
    VALID_ROLES,
    qa_audit,
    record_review,
    create_issue,
    gate_verdict,
    get_subtask_summary_and_desc,
)
from tools.hoechstmass.tools.verify_requirement_governance import (
    load_config,
    validate_archaeology_section,
    verify_engineering_directory_structure,
    verify_sprint_metrics,
    verify_qa_audits,
    verify_independent_reviews,
    validate_impact_analysis_section,
    verify_stage1_impact_analysis,
    triage_unbacked_verified_tsts,
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
        self.assertEqual(VALID_ROLES, ["agent1", "agent2", "coordinator", "qa"])

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

    def test_verify_qa_audits_valid_and_invalid(self):
        """TST-PRO-061.2: Verify verify_qa_audits governance check in Höchstmaß mirror."""
        import tempfile
        with tempfile.TemporaryDirectory() as tmpdir:
            qa_dir = os.path.join(tmpdir, "docs", "engineering", "qa_audits")
            os.makedirs(qa_dir, exist_ok=True)
            audit_file = os.path.join(qa_dir, "sprint_2026-46.3.md")

            # Missing file
            ok, errors = verify_qa_audits("sprint_2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(len(errors) > 0)

            # Valid with >= 2 sampled tickets and COMPLIANT verdict
            with open(audit_file, "w", encoding="utf-8") as f:
                f.write("""# Sprint QA Process-Compliance Audit: Sprint 2026-46.3
**Auditor Role**: `qa`

## 2. Sampled Tickets Audit
### Sample 1: ATT-6149
* Verdict: COMPLIANT
### Sample 2: ATT-6150
* Verdict: COMPLIANT
## 3. Overall Process Verdict: COMPLIANT
""")
            ok, msgs = verify_qa_audits("sprint_2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok)
            self.assertTrue(any("PASS:" in m for m in msgs))

            # Invalid: < 2 sampled tickets
            with open(audit_file, "w", encoding="utf-8") as f:
                f.write("""# Sprint QA Process-Compliance Audit: Sprint 2026-46.3
**Auditor Role**: `qa`

## 2. Sampled Tickets Audit
### Sample 1: ATT-6149
* Verdict: COMPLIANT
## 3. Overall Process Verdict: COMPLIANT
""")
            ok, errors = verify_qa_audits("sprint_2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(len(errors) > 0)

    def test_validate_impact_analysis_section_hoechstmass(self):
        """TST-PRO-064.1 / TST-PRO-064.4: Verify validate_impact_analysis_section in Höchstmaß mirror."""
        valid_text = """
## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: REQ-PRO-064
* **Affected Architecture Elements**: tools/jira_util.py
* **Verification Impact & Test Surface**: TST-PRO-064
* **Risk Assessment & Invariants**: Low risk
"""
        ok, errors = validate_impact_analysis_section(valid_text)
        self.assertTrue(ok, f"Expected valid impact analysis to pass, got: {errors}")

        invalid_text = """
## Change Impact Analysis
* **Affected Requirements**: REQ-PRO-064
* **Affected Architecture Elements**: tools/jira_util.py
"""
        ok_inv, errors_inv = validate_impact_analysis_section(invalid_text)
        self.assertFalse(ok_inv)
        self.assertTrue(any("Verification Impact" in e for e in errors_inv))

    def test_analysis_templates_contain_impact_analysis(self):
        """TST-PRO-064.4: Verify root and Höchstmaß analysis templates feature Change Impact Analysis."""
        root_template = os.path.join(_repo_root, ".agents", "skills", "stage1-analysis", "templates", "analysis_template.md")
        hoechstmass_template = os.path.join(_repo_root, "tools", "hoechstmass", "templates", "analysis_template.md")

        for tmpl_path in [root_template, hoechstmass_template]:
            self.assertTrue(os.path.exists(tmpl_path), f"Template not found: {tmpl_path}")
            with open(tmpl_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("Change-Request Impact Analysis", content)
            self.assertIn("Affected Requirements", content)
            self.assertIn("Affected Architecture Elements", content)
            self.assertIn("Verification Impact & Test Surface", content)
            self.assertIn("Risk Assessment & Invariants", content)

    def test_gate_verdict_enforces_impact_analysis_hoechstmass(self):
        """TST-PRO-064.3: Verify gate_verdict in Höchstmaß mirror validates Impact Analysis on [Analysis] subtasks."""
        from unittest.mock import patch

        # 1. [Analysis] subtask with missing Impact Analysis -> SystemExit(1)
        with patch("tools.hoechstmass.tools.jira_util.get_subtask_summary_and_desc") as mock_subtask, \
             patch("tools.hoechstmass.tools.jira_util.assign_issue"), \
             patch("tools.hoechstmass.tools.jira_util.add_comment"), \
             patch("tools.hoechstmass.tools.jira_util.jira_request"):
            mock_subtask.return_value = ("[Analysis] PROJ-100: Analysis", "Missing impact analysis", "PROJ-100")
            with self.assertRaises(SystemExit) as cm:
                gate_verdict("PROJ-101", "PASS", "Gate 1 audit report", as_role="agent2")
            self.assertEqual(cm.exception.code, 1)

            # 2. [Analysis] subtask with valid Impact Analysis -> succeeds
            valid_desc = """
## Change Impact Analysis
* **Affected Requirements**: REQ-PRO-064
* **Affected Architecture Elements**: tools/jira_util.py
* **Verification Impact & Test Surface**: TST-PRO-064
* **Risk Assessment & Invariants**: Low risk
"""
            mock_subtask.return_value = ("[Analysis] PROJ-100: Analysis", valid_desc, "PROJ-100")
            gate_verdict("PROJ-101", "PASS", "Gate 1 audit report", as_role="agent2")

            # 3. Non-analysis subtask [Req & Test Spec] without impact analysis -> succeeds normally
            mock_subtask.return_value = ("[Req & Test Spec] PROJ-100: Spec", "No impact analysis", "PROJ-100")
            gate_verdict("PROJ-102", "PASS", "Gate 2 audit report", as_role="agent2")


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

    def test_extract_sprint_gate_records_hoechstmass_stage6_adf_dict(self):
        """TST-PRO-128.1: Hoechstmass extract_sprint_gate_records handles ADF dict Stage 6 parent comments without TypeError."""
        from tools.hoechstmass.tools.jira_util import extract_sprint_gate_records

        adf_fr_pass = {
            "type": "doc",
            "version": 1,
            "content": [{"type": "paragraph", "content": [{"type": "text", "text": "[FINAL REVIEW (AGENT) — PASS] Clean."}]}],
        }
        records, gate_stats = extract_sprint_gate_records(
            [
                {
                    "key": "PROJ-200",
                    "fields": {
                        "summary": "ADF Stage 6 parent",
                        "comment": {"comments": [{"author": {"displayName": "Agent 2"}, "body": adf_fr_pass, "created": "2026-11-18T06:00:00Z"}]},
                        "subtasks": [],
                    },
                }
            ],
            {"id": 100, "name": "Sprint 100"},
        )
        self.assertEqual(len(records), 1)
        self.assertEqual(records[0]["gate"], 6)
        self.assertEqual(records[0]["verdict"], "PASS")
        self.assertEqual(gate_stats[6]["passed_first_time"], 1)

    def test_export_test_evidence_hoechstmass(self):
        """TST-PRO-100.1 / TST-PRO-100.4: export_test_evidence command in hoechstmass."""
        import tempfile
        with tempfile.TemporaryDirectory() as tmpdir:
            out_dir = os.path.join(tmpdir, "test_evidence")
            res_dir = os.path.join(tmpdir, "test-results")
            os.makedirs(res_dir, exist_ok=True)
            with open(os.path.join(res_dir, "TEST-Sample.xml"), "w", encoding="utf-8") as f:
                f.write('<?xml version="1.0"?><testsuite tests="1" failures="0" errors="0" skipped="0" time="0.1"><testcase name="testA_TST_PRO_100" classname="Sample"/></testsuite>')

            md_path, json_path, payload = export_test_evidence(
                sprint_arg="sprint_100",
                out_dir=out_dir,
                test_results_dir=res_dir,
                as_role="coordinator",
            )
            self.assertTrue(os.path.isfile(md_path))
            self.assertTrue(os.path.isfile(json_path))
            self.assertEqual(payload["target"], "sprint_100")
            self.assertEqual(payload["unit_tests"]["total"], 1)

    def test_verify_sprint_metrics_hoechstmass(self):
        """TST-PRO-060.2 / TST-PRO-060.3: verify_sprint_metrics in hoechstmass."""
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            metrics_dir = os.path.join(tmpdir, "docs", "engineering", "metrics")
            os.makedirs(metrics_dir, exist_ok=True)
            metrics_file = os.path.join(metrics_dir, "process_metrics.jsonl")

            # 1. Missing file
            ok, errs = verify_sprint_metrics(sprint_name="2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)

            # 2. Valid metrics file
            record = {
                "sprint_id": 100,
                "sprint_name": "2026-46.3",
                "recorded_at": "2026-11-12T04:00:00Z",
                "throughput": {
                    "total_tickets": 8,
                    "completed_tickets": 8,
                    "total_story_points": 18.0,
                    "completed_story_points": 18.0,
                },
                "quality_gates": {"overall_ftpr_percent": 90.0},
            }
            with open(metrics_file, "w", encoding="utf-8") as f:
                f.write(json.dumps(record) + "\n")

            ok, msgs = verify_sprint_metrics(sprint_name="2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok)
            self.assertTrue(any("PASS" in m for m in msgs))

    def test_collect_sprint_metrics_preindexed_comments(self):
        """TST-PRO-060.3 / REQ-PRO-060.4: Pre-indexed subtask comments without extra REST calls."""
        from unittest.mock import patch

        call_urls = []

        def mock_jira_req(url, role=None, config=None, **kwargs):
            call_urls.append(url)
            if "/rest/agile/1.0/sprint/200/issue" in url:
                return {
                    "issues": [
                        {
                            "key": "PARENT-1",
                            "fields": {
                                "summary": "Parent Feature",
                                "status": {"name": "Final Review (Human)"},
                                "issuetype": {"subtask": False},
                                "customfield_10016": 3.0,
                                "subtasks": [
                                    {"key": "ST-1", "fields": {"summary": "[Analysis] Task", "status": {"name": "Erledigt"}}}
                                ],
                            },
                        },
                        {
                            "key": "ST-1",
                            "fields": {
                                "issuetype": {"subtask": True},
                                "comment": {
                                    "comments": [
                                        {"body": "GATE_VERDICT: PASS", "author": {"displayName": "Agent 2"}}
                                    ]
                                },
                            },
                        },
                    ]
                }
            return {}

        with patch("tools.hoechstmass.tools.jira_util.jira_request", side_effect=mock_jira_req):
            metrics = collect_sprint_metrics_for_id(200, "Sprint 200", 1)
            self.assertEqual(metrics["throughput"]["total_story_points"], 3.0)
            self.assertEqual(metrics["throughput"]["completed_story_points"], 3.0)
            # Assert only 1 request was made (fetch_all_sprint_issues), zero sequential comment calls for ST-1
            self.assertEqual(len(call_urls), 1)
            self.assertTrue(any("/rest/agile/1.0/sprint/200/issue" in u for u in call_urls))

    def test_hoechstmass_qa_audit_execution(self):
        """TST-PRO-061.1: Verify Höchstmaß qa_audit execution and markdown formatting."""
        from unittest.mock import patch
        import tempfile
        import subprocess

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

        def mock_jira_req(url, method="GET", payload=None, role="coordinator", **kwargs):
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

        with patch("tools.hoechstmass.tools.jira_util.jira_request", side_effect=mock_jira_req):
            with tempfile.TemporaryDirectory() as tmpdir:
                # 1. Setup git repo and commits
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
                res_path, results = qa_audit(sprint_arg=None, sample_size=2, out_path=out_file, role="qa", repo_root=tmpdir)
                self.assertEqual(res_path, out_file)
                self.assertTrue(os.path.exists(out_file))
                self.assertEqual(len(results), 2)
                self.assertTrue(all(r["compliant"] for r in results))

                with open(out_file, "r", encoding="utf-8") as f:
                    content = f.read()

                self.assertIn("# Sprint QA Process-Compliance Audit: Sprint 2026-46.3", content)
                self.assertIn("ATT-6149", content)
                self.assertIn("ATT-6150", content)
                self.assertIn("COMPLIANT", content)

    def test_hoechstmass_record_review(self):
        from unittest.mock import patch
        import tempfile

        def mock_jira_req(url, method="GET", payload=None, role="coordinator", **kwargs):
            if "/rest/agile/1.0/board" in url and "/sprint" not in url:
                return {"values": [{"id": 1, "name": "ATT Board"}]}
            elif "/rest/agile/1.0/board/1/sprint?state=active" in url:
                return {"values": [{"id": 440, "name": "2026-46.3", "state": "active"}]}
            elif "/rest/agile/1.0/sprint/440/issue" in url:
                return {
                    "issues": [
                        {"key": "ATT-6150", "fields": {"summary": "Issue 1", "issuetype": {"subtask": False}}},
                    ],
                    "total": 1,
                }
            return {}

        with patch("tools.hoechstmass.tools.jira_util.jira_request", side_effect=mock_jira_req):
            with tempfile.TemporaryDirectory() as tmpdir:
                out_file = os.path.join(tmpdir, "docs", "engineering", "reviews", "sprint_2026-46.3.md")
                res_path, meta = record_review(
                    sprint_arg="2026-46.3",
                    ticket_key="ATT-6150",
                    reviewer="Human",
                    verdict="PASS",
                    findings="Human ceremony 2 acceptance notes.",
                    out_path=out_file,
                    repo_root=tmpdir,
                )
                self.assertEqual(res_path, out_file)
                self.assertTrue(os.path.exists(out_file))
                self.assertEqual(meta["reviewer"], "Human")

                with open(out_file, "r", encoding="utf-8") as f:
                    content = f.read()

                self.assertIn("# Independent QA Review Record: Sprint 2026-46.3", content)
                self.assertIn("**Reviewer**: Human", content)
                self.assertIn("**Verdict**: **PASS**", content)
                self.assertIn("ATT-6150", content)

    def test_verify_independent_reviews_hoechstmass(self):
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            # 1. Missing directory
            ok, errs = verify_independent_reviews(sprint_name="2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("does not exist" in e for e in errs))

            rev_dir = os.path.join(tmpdir, "docs", "engineering", "reviews")
            os.makedirs(rev_dir, exist_ok=True)

            # 2. Missing record
            ok, errs = verify_independent_reviews(sprint_name="2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(any("Missing independent review record" in e for e in errs))

            # 3. Valid record
            target = os.path.join(rev_dir, "sprint_2026-46.3.md")
            with open(target, "w", encoding="utf-8") as f:
                f.write(
                    "# Independent QA Review Record: Sprint 2026-46.3\n\n"
                    "* **Reviewer**: Human\n"
                    "* **Date**: 2026-11-12 04:00:00\n"
                    "* **Scope**: Ceremony 2 Sprint Review\n"
                    "* **Verdict**: **PASS**\n"
                )

            ok, msgs = verify_independent_reviews(sprint_name="2026-46.3", repo_root=tmpdir)
            self.assertTrue(ok, f"Expected pass, got: {msgs}")
            self.assertTrue(any("PASS" in m for m in msgs))

            # 4. Valid SKIPPED, NOT PERFORMED, and PARTIAL verdicts
            for alt_v in ("SKIPPED", "NOT PERFORMED", "PARTIAL"):
                with open(target, "w", encoding="utf-8") as f:
                    f.write(
                        f"# Independent QA Review Record: Sprint 2026-46.3\n\n"
                        f"* **Reviewer**: Human\n"
                        f"* **Date**: 2026-11-12 04:00:00\n"
                        f"* **Scope**: Ceremony 2 Sprint Review\n"
                        f"* **Verdict**: **{alt_v}**\n"
                    )
                ok_alt, msgs_alt = verify_independent_reviews(sprint_name="2026-46.3", repo_root=tmpdir)
                self.assertTrue(ok_alt, f"Expected pass for verdict {alt_v}, got: {msgs_alt}")

            # 5. Wildcard
            ok_any, msgs_any = verify_independent_reviews(sprint_name=None, repo_root=tmpdir)
            self.assertTrue(ok_any)
            self.assertTrue(any("Verified 1 independent review record" in m for m in msgs_any))

    def test_hoechstmass_create_issue_severity_enforcement(self):
        """TST-PRO-063.4: Verify create_issue severity enforcement in hoechstmass."""
        from unittest.mock import patch

        def mock_jira_req(url, method="GET", payload=None, role="coordinator", **kwargs):
            return {"key": "PROJ-8888"}

        with patch("tools.hoechstmass.tools.jira_util.jira_request", side_effect=mock_jira_req):
            # 1. Bug without severity -> exit 1
            with self.assertRaises(SystemExit) as cm:
                create_issue("Bug without severity", "Description", issuetype_id="10006")
            self.assertEqual(cm.exception.code, 1)

            # 2. Bug with invalid severity -> exit 1
            with self.assertRaises(SystemExit) as cm:
                create_issue("Bug with invalid severity", "Description", issuetype_id="10006", severity="catastrophic")
            self.assertEqual(cm.exception.code, 1)

            # 3. Bug with valid severity -> succeeds
            key = create_issue("Bug with valid severity", "Description", issuetype_id="10006", severity="major")
            self.assertEqual(key, "PROJ-8888")

            # 4. Non-bug without severity -> succeeds
            key_feat = create_issue("Feature without severity", "Description", issuetype_id="10007")
            self.assertEqual(key_feat, "PROJ-8888")

    def test_hoechstmass_verify_sprint_metrics_problem_trends(self):
        """TST-PRO-063.4: Verify problem_trends schema validation in hoechstmass verify_sprint_metrics."""
        import tempfile
        import json

        with tempfile.TemporaryDirectory() as tmpdir:
            metrics_dir = os.path.join(tmpdir, "docs", "engineering", "metrics")
            os.makedirs(metrics_dir, exist_ok=True)
            metrics_file = os.path.join(metrics_dir, "process_metrics.jsonl")

            valid_record = {
                "sprint_id": 101,
                "sprint_name": "Sprint 101",
                "throughput": {"total_story_points": 10.0, "completed_story_points": 10.0},
                "quality_gates": {},
                "problem_trends": {
                    "total_bugs": 2,
                    "open_bugs": 0,
                    "closed_bugs": 2,
                    "resolution_rate_percent": 100.0,
                    "by_severity": {"Critical": 1, "Major": 1, "Minor": 0, "Cosmetic": 0},
                },
            }
            with open(metrics_file, "w", encoding="utf-8") as f:
                f.write(json.dumps(valid_record) + "\n")

            ok, msgs = verify_sprint_metrics(sprint_name="Sprint 101", repo_root=tmpdir)
            self.assertTrue(ok, f"Expected valid metrics with problem_trends to pass, got: {msgs}")

    def test_hoechstmass_triage_unbacked_verified_tsts(self):
        """TST-PRO-101.5: Verify parameterized triage_unbacked_verified_tsts in hoechstmass."""
        import tempfile

        with tempfile.TemporaryDirectory() as tmpdir:
            csv_out = os.path.join(tmpdir, "triage.csv")
            report, _ = triage_unbacked_verified_tsts(
                repo_root=tmpdir,
                out_csv=csv_out,
                tst_shards={
                    "docs/tests/tst_core.md": (
                        "### TST-CORE-001: Backed [Verified]\n- **Requirement(s)**: `REQ-CORE-001`\n- **Component**: `CoreTest.kt`\n"
                        "### TST-CORE-002: Missing [Verified]\n- **Requirement(s)**: `REQ-CORE-002`\n"
                    )
                },
                req_shards={
                    "docs/requirements/req_core.md": (
                        "### REQ-CORE-001: R1 [Verified]\n- **Verification Method**: Unit\n"
                        "### REQ-CORE-002: R2 [Verified]\n- **Verification Method**: Unit\n"
                    )
                },
                test_sources={"app/src/test/CoreTest.kt": "class CoreTest {}"},
            )
            self.assertEqual(report["total_unbacked_verified_tsts"], 2)
            self.assertEqual(report["by_category"]["unit-test-backed"], 1)
            self.assertEqual(report["by_category"]["missing test code"], 1)
            self.assertTrue(os.path.isfile(csv_out))



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


class TestHoechstmassSprintMetricsParity(unittest.TestCase):
    """TST-PRO-122.5: Verify tools/hoechstmass/tools/sprint_metrics.py parity."""

    def test_hoechstmass_sprint_metrics_spc_bounds_and_acceptance(self):
        from tools.hoechstmass.tools import sprint_metrics as hm_sm

        hist = [
            {"process_version": "1.0", "quality_gates": {"overall_ftpr_percent": 65.0, "human_acceptance_rate_percent": 40.0, "total_subtasks_evaluated": 40}, "throughput": {"total_tickets": 20, "completed_tickets": 8, "completed_story_points": 16.0}},
            {"process_version": "1.0", "quality_gates": {"overall_ftpr_percent": 100.0, "human_acceptance_rate_percent": 100.0, "total_subtasks_evaluated": 40}, "throughput": {"total_tickets": 20, "completed_tickets": 20, "completed_story_points": 40.0}},
        ]
        spc = hm_sm.calculate_spc_limits(hist)
        self.assertLessEqual(spc["limits"]["overall_ftpr"]["ucl"], 100.0)
        self.assertLessEqual(spc["limits"]["human_acceptance"]["ucl"], 100.0)
        reset = hm_sm.calculate_spc_limits(hist, current_metrics={"process_version": "2.0"})
        self.assertTrue(reset.get("baseline_reset"))

    def test_hoechstmass_sprint_metrics_static_analysis_baselines(self):
        """TST-PRO-123.2: Verify tools/hoechstmass/tools/sprint_metrics.py static_analysis reporting."""
        import tempfile
        from pathlib import Path
        from tools.hoechstmass.tools import sprint_metrics as hm_sm

        with tempfile.TemporaryDirectory() as tmpdir:
            repo_root = Path(tmpdir)
            (repo_root / "app").mkdir(parents=True)
            (repo_root / "app" / "detekt-baseline.xml").write_text("<ID>a</ID>\n", encoding="utf-8")
            (repo_root / "app" / "lint-baseline.xml").write_text('<issue id="x"/>\n', encoding="utf-8")
            metrics = hm_sm.parse_sprint_gate_metrics(
                [], {"id": 12, "name": "Mirror-SA"},
                history=[{"static_analysis": {"detekt_baseline_count": 3, "lint_baseline_count": 2}}],
                repo_root=repo_root,
            )
            self.assertEqual(metrics["static_analysis"]["detekt_delta"], -2)
            self.assertEqual(metrics["static_analysis"]["lint_delta"], -1)


class TestHoechstmassPostStage6MergeAndBounceParity(unittest.TestCase):
    """TST-PRO-125.4: Verify tools/hoechstmass post-Stage-6 merge, bounce subtask reuse & skill parity."""

    def test_hoechstmass_bounce_subtask_reuse_and_locked_erledigt_fallback(self):
        from unittest.mock import patch
        from tools.hoechstmass.tools import jira_util as hm_ju
        from tools.hoechstmass.tools import sprint_metrics as hm_sm

        state = {
            "subtasks": [
                {"key": "PROJ-11", "fields": {"summary": "[Implementation] Feature", "status": {"name": "Erledigt"}}},
                {"key": "PROJ-12", "fields": {"summary": "[Test] Feature", "status": {"name": "Erledigt"}}},
            ],
            "comments": [],
            "transitions_mode": "reopenable",
        }

        def fake_request(url, method="GET", payload=None, role="agent1", config=None, h_config=None):
            if "/rest/api/2/issue/PROJ-10?" in url:
                return {
                    "fields": {
                        "issuetype": {"subtask": False},
                        "summary": "Feature",
                        "status": {"name": "Final Review (Agent)"},
                        "subtasks": state["subtasks"],
                        "comment": {"comments": state["comments"]},
                    }
                }
            if "/transitions" in url and method == "GET":
                if state["transitions_mode"] == "reopenable":
                    return {"transitions": [{"id": "21", "name": "Reopen", "to": {"name": "In Progress"}}]}
                return {"transitions": []}
            if "/rest/api/3/issue/PROJ-11/transitions" in url and method == "POST":
                state["subtasks"][0]["fields"]["status"]["name"] = "In Progress"
                return {}
            if "/rest/api/3/issue/PROJ-12/transitions" in url and method == "POST":
                state["subtasks"][1]["fields"]["status"]["name"] = "In Progress"
                return {}
            if url.endswith("/rest/api/2/issue") and method == "POST":
                return {"key": "PROJ-13"}
            return {}

        with patch.object(hm_ju, "jira_request", side_effect=fake_request), \
             patch.object(hm_ju, "assign_issue"), \
             patch.object(hm_ju, "add_comment"), \
             patch.object(hm_ju, "transition_issue"):
            res = hm_ju.final_review_verdict("PROJ-10", "BOUNCE", "Fix defect", bounce_target="implementation", as_role="agent2")
            self.assertEqual(res, "BOUNCE_1")
            self.assertEqual(state["subtasks"][0]["fields"]["status"]["name"], "In Progress")

            sub_reused = hm_ju.submit_stage("PROJ-10", 4, "Healed", as_role="agent1")
            self.assertEqual(sub_reused, "PROJ-11")

            # Locked Erledigt fallback creates [Implementation - Bounce 1]
            state["subtasks"][0]["fields"]["status"]["name"] = "Erledigt"
            state["transitions_mode"] = "locked"
            state["comments"].append({"body": "[FINAL REVIEW (AGENT) — BOUNCE 1/3]\n\nFix defect"})
            sub_bounce = hm_ju.submit_stage("PROJ-10", 4, "Healed 2", as_role="agent1")
            self.assertEqual(sub_bounce, "PROJ-13")

        self.assertEqual(hm_ju.resolve_desired_parent_stage_for_subtask("[Implementation - Bounce 1] Feature", True), "test")
        self.assertEqual(hm_sm.identify_gate_number("[Implementation - Bounce 1] Feature"), 4)
        self.assertEqual(hm_sm.identify_gate_number("[Test - Bounce 1] Feature"), 5)

        # Verify bounce target alias coverage in hoechstmass mirror
        self.assertEqual(hm_ju._resolve_bounce_stage_names("impl-plan"), ("Impl-Plan", "Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("impl_plan"), ("Impl-Plan", "Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("test-spec"), ("Req & Test Spec", "Impl-Plan", "Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("req-test-spec"), ("Req & Test Spec", "Impl-Plan", "Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("spec"), ("Req & Test Spec", "Impl-Plan", "Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("revision"), ("Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("rework"), ("Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("verification"), ("Test",))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("analysis"), ("Analysis", "Req & Test Spec", "Impl-Plan", "Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("stage 1"), ("Analysis", "Req & Test Spec", "Impl-Plan", "Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("stage 2"), ("Req & Test Spec", "Impl-Plan", "Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("stage 3"), ("Impl-Plan", "Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("stage 4"), ("Implementation", "Test"))
        self.assertEqual(hm_ju._resolve_bounce_stage_names("stage 5"), ("Test",))

        # Verify bounce_target="stage 4" reopens ONLY [Implementation] & [Test], never [Impl-Plan]
        reopened_hm = []
        subtasks_hm = [
            {"key": "PROJ-10", "fields": {"summary": "[Impl-Plan] Feature", "status": {"name": "Erledigt"}}},
            {"key": "PROJ-11", "fields": {"summary": "[Implementation] Feature", "status": {"name": "Erledigt"}}},
            {"key": "PROJ-12", "fields": {"summary": "[Test] Feature", "status": {"name": "Erledigt"}}},
        ]

        def fake_hm_s4(url, method="GET", payload=None, role="agent2", config=None, h_config=None):
            if "/rest/api/2/issue/PROJ-20?" in url:
                return {
                    "fields": {
                        "issuetype": {"subtask": False},
                        "summary": "Feature",
                        "status": {"name": "Final Review (Agent)"},
                        "subtasks": subtasks_hm,
                        "comment": {"comments": []},
                    }
                }
            if "/transitions" in url and method == "GET":
                return {"transitions": [{"id": "21", "name": "Reopen", "to": {"name": "In Progress"}}]}
            if "/transitions" in url and method == "POST":
                reopened_hm.append(url.split("/")[-2])
                return {}
            return {}

        with patch.object(hm_ju, "load_env_config", return_value={"JIRA_URL": "https://jira.test"}), \
             patch.object(hm_ju, "jira_request", side_effect=fake_hm_s4), \
             patch.object(hm_ju, "assign_issue"), \
             patch.object(hm_ju, "add_comment"), \
             patch.object(hm_ju, "transition_issue"):
            res_s4 = hm_ju.final_review_verdict("PROJ-20", "BOUNCE", "Fix", bounce_target="stage 4", as_role="agent2")
            self.assertEqual(res_s4, "BOUNCE_1")
            self.assertEqual(reopened_hm, ["PROJ-11", "PROJ-12"])

        # Verify Gate 1 Impact Analysis check triggers on [Analysis - Bounce 1] in hoechstmass mirror
        with patch.object(hm_ju, "load_env_config", return_value={"JIRA_URL": "https://jira.test"}), \
             patch.object(hm_ju, "get_subtask_summary_and_desc", return_value=("[Analysis - Bounce 1] Feature", "No impact section", "PROJ-999")):
            with self.assertRaises(SystemExit) as cm:
                hm_ju.gate_verdict("PROJ-14", "PASS", "LGTM", as_role="agent2")
            self.assertEqual(cm.exception.code, 1)

    def test_hoechstmass_skill_mirrors_defer_merge_until_stage6_pass(self):
        root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        with open(os.path.join(root, ".agents", "skills", "stage5-verification", "SKILL.md"), encoding="utf-8") as f:
            stage5 = f.read()
        with open(os.path.join(root, ".agents", "skills", "stage6-final-review", "SKILL.md"), encoding="utf-8") as f:
            stage6 = f.read()
        self.assertIn("Do **NOT** invoke `parallel_sprint_lock.py --merge-ticket` at Gate 5", stage5)
        self.assertIn("git diff sprint/<SPRINT_NAME>...feature/<KEY>", stage6)
        self.assertIn("strictly *after* `final-review-verdict PASS`", stage6)


if __name__ == "__main__":
    unittest.main()
