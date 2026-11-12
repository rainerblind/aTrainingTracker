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
    collect_sprint_metrics_for_id,
    VALID_ROLES,
    qa_audit,
    record_review,
)
from tools.hoechstmass.tools.verify_requirement_governance import (
    load_config,
    validate_archaeology_section,
    verify_engineering_directory_structure,
    verify_sprint_metrics,
    verify_qa_audits,
    verify_independent_reviews,
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
## 2. Sampled Tickets Audit
### Sample 1: ATT-6149
* Verdict: COMPLIANT
## 3. Overall Process Verdict: COMPLIANT
""")
            ok, errors = verify_qa_audits("sprint_2026-46.3", repo_root=tmpdir)
            self.assertFalse(ok)
            self.assertTrue(len(errors) > 0)


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
                res_path, results = qa_audit(sprint_arg=None, sample_size=2, out_path=out_file, role="coordinator", repo_root=tmpdir)
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

            # 4. Wildcard
            ok_any, msgs_any = verify_independent_reviews(sprint_name=None, repo_root=tmpdir)
            self.assertTrue(ok_any)
            self.assertTrue(any("Verified 1 independent review record" in m for m in msgs_any))



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
