#!/usr/bin/env python3
"""
Unit tests for tools/auto_gate.py (ATT-9543 automated gates & ATT-9544 evidence bundles).
"""

import json
import os
import sys
import tempfile
import unittest
from unittest.mock import patch

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TOOLS_DIR = os.path.join(REPO_ROOT, "tools")
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

import auto_gate


class TestAutoGate(unittest.TestCase):
    def test_select_dependent_tests_finds_direct_and_caller_tests(self):
        with tempfile.TemporaryDirectory() as tmp:
            main_dir = os.path.join(tmp, "app", "src", "main", "java", "com", "example")
            test_dir = os.path.join(tmp, "app", "src", "test", "java", "com", "example")
            tools_dir = os.path.join(tmp, "tools")
            os.makedirs(main_dir)
            os.makedirs(test_dir)
            os.makedirs(tools_dir)
            with open(os.path.join(main_dir, "FooEngine.kt"), "w") as f:
                f.write("class FooEngine\n")
            with open(os.path.join(test_dir, "FooEngineTest.kt"), "w") as f:
                f.write("class FooEngineTest\n")
            with open(os.path.join(test_dir, "CallerServiceTest.kt"), "w") as f:
                f.write("class CallerServiceTest { val x = FooEngine() }\n")
            with open(os.path.join(test_dir, "UnrelatedTest.kt"), "w") as f:
                f.write("class UnrelatedTest\n")
            with open(os.path.join(tools_dir, "bar.py"), "w") as f:
                f.write("x = 1\n")
            with open(os.path.join(tools_dir, "test_bar.py"), "w") as f:
                f.write("import unittest\n")

            sel = auto_gate.select_dependent_tests(
                ["app/src/main/java/com/example/FooEngine.kt", "tools/bar.py"],
                repo_root=tmp,
            )
            self.assertEqual(sel["android_tests"], ["com.example.CallerServiceTest", "com.example.FooEngineTest"])
            self.assertEqual(sel["python_tests"], ["tools/test_bar.py"])

    def test_failing_dependent_test_fails_gate_and_names_test(self):
        with tempfile.TemporaryDirectory() as tmp:
            xml_path = os.path.join(tmp, "TEST-com.example.FooTest.xml")
            with open(xml_path, "w", encoding="utf-8") as f:
                f.write(
                    '<testsuite name="com.example.FooTest" tests="2" failures="1" errors="0">'
                    '<testcase classname="com.example.FooTest" name="passesOk"/>'
                    '<testcase classname="com.example.FooTest" name="failsOnBadInput"><failure message="boom"/></testcase>'
                    '</testsuite>'
                )
            parsed = auto_gate.parse_junit_xml_files([xml_path])
            self.assertEqual(parsed["total"], 2)
            self.assertEqual(parsed["failures"], 1)
            self.assertEqual(parsed["failing_tests"], ["com.example.FooTest.failsOnBadInput"])

            with patch("auto_gate.run_android_tests", return_value={
                "passed": False, "ran": 2, "failing_tests": ["com.example.FooTest.failsOnBadInput"],
                "xml_files": [xml_path], "details": "Failed Android test(s): com.example.FooTest.failsOnBadInput",
            }), patch("auto_gate.run_python_tests", return_value={
                "passed": True, "ran": 0, "failing_tests": [], "details": "No Python tests selected.",
            }):
                res = auto_gate.run_automated_gates(
                    ticket_key="ATT-9900",
                    tier="tier-c",
                    repo_root=tmp,
                    changed_files=["app/src/test/java/com/example/FooTest.kt"],
                )
            self.assertEqual(res["verdict"], "FAIL")
            self.assertIn("com.example.FooTest.failsOnBadInput", res["failing_tests"])
            md = auto_gate.render_gate_report_markdown(res)
            self.assertIn("com.example.FooTest.failsOnBadInput", md)

    def test_deliverable_placeholder_lint_catches_tbd_and_broken_link(self):
        with tempfile.TemporaryDirectory() as tmp:
            tdir = os.path.join(tmp, "docs", "engineering", "tickets")
            os.makedirs(tdir)
            doc = os.path.join(tdir, "ATT-9901.md")
            with open(doc, "w") as f:
                f.write("# ATT-9901\nTODO fill later [broken](missing_file.md)\n")
            ok, msgs = auto_gate.lint_ticket_deliverables("ATT-9901", "tier-b", repo_root=tmp)
            self.assertFalse(ok)
            self.assertTrue(any("TODO" in m for m in msgs))
            self.assertTrue(any("missing_file.md" in m for m in msgs))

    def test_tier_b_passing_auto_gate_advances_to_stage6_without_subtasks(self):
        result = {
            "ticket_key": "ATT-9902",
            "risk_tier": "tier-b",
            "commit_sha": "abcdef123456",
            "run_started_utc": "2026-11-21T20:40:00+00:00",
            "checks": [{"name": "dependent_unit_tests", "passed": True, "details": "5 passed"}],
            "verdict": "PASS",
            "failing_tests": [],
        }
        with patch("jira_util.add_comment") as mock_comment, \
             patch("jira_util.transition_issue") as mock_trans:
            advanced = auto_gate.advance_jira_after_auto_gate(result, role="agent1")
            self.assertTrue(advanced)
            mock_comment.assert_called_once()
            self.assertIn("[auto-gate:ATT-9902:abcdef12:PASS]", mock_comment.call_args[0][1])
            mock_trans.assert_called_once_with("ATT-9902", "final review (agent)", role="agent1", inject_context=False)

    def test_evidence_bundle_junit_sha_manifest_and_tamper_detection(self):
        """ATT-9544 Criterion 1: Evidence bundle binds JUnit XML to commit SHA + run time + SHA-256 manifest."""
        import verify_requirement_governance as vrg
        with tempfile.TemporaryDirectory() as tmp:
            xml_src = os.path.join(tmp, "TEST-com.example.DemoTest.xml")
            with open(xml_src, "w", encoding="utf-8") as f:
                f.write('<testsuite name="com.example.DemoTest" tests="1" failures="0"><testcase classname="com.example.DemoTest" name="ok"/></testsuite>')
            result = {
                "ticket_key": "ATT-9910",
                "risk_tier": "tier-b",
                "commit_sha": "deadbeef12345678",
                "run_started_utc": "2026-11-21T20:42:00+00:00",
                "ui_touching": False,
                "verdict": "PASS",
                "android_xml_files": [xml_src],
                "android_test_summary": {"passed": True, "ran": 1},
                "python_test_summary": {"passed": True, "ran": 0},
                "checks": [{"name": "dependent_unit_tests", "passed": True, "details": "1 passed"}],
            }
            ok, bundle_rel, msgs = auto_gate.build_evidence_bundle(result, repo_root=tmp, ci_run_url="https://github.com/example/runs/42")
            self.assertTrue(ok, msgs)
            self.assertEqual(bundle_rel, os.path.join("docs", "engineering", "test_evidence", "tickets", "ATT-9910"))

            v_ok, v_msgs = vrg.verify_ticket_evidence_bundle("ATT-9910", repo_root=tmp)
            self.assertTrue(v_ok, v_msgs)

            # Tampering with archived XML must break SHA-256 verification
            archived_xml = os.path.join(tmp, bundle_rel, "junit", "TEST-com.example.DemoTest.xml")
            with open(archived_xml, "a", encoding="utf-8") as f:
                f.write("<!-- tampered -->")
            v_ok2, v_msgs2 = vrg.verify_ticket_evidence_bundle("ATT-9910", repo_root=tmp)
            self.assertFalse(v_ok2)
            self.assertTrue(any("SHA-256 mismatch" in m for m in v_msgs2))

    def test_evidence_bundle_requires_screenshot_for_ui_ticket(self):
        """ATT-9544 Criterion 2: UI ticket bundle fails if zero screenshots, passes when screenshot attached."""
        with tempfile.TemporaryDirectory() as tmp:
            result = {
                "ticket_key": "ATT-9911",
                "risk_tier": "tier-b",
                "commit_sha": "cafebabe9999",
                "run_started_utc": "2026-11-21T20:43:00+00:00",
                "ui_touching": True,
                "verdict": "PASS",
                "android_xml_files": [],
                "android_test_summary": {"passed": True, "ran": 0},
                "python_test_summary": {"passed": True, "ran": 0},
                "checks": [],
            }
            ok_missing, _, msgs_missing = auto_gate.build_evidence_bundle(result, repo_root=tmp)
            self.assertFalse(ok_missing)
            self.assertTrue(any("screenshot" in m.lower() for m in msgs_missing))

            shot = os.path.join(tmp, "after.png")
            with open(shot, "wb") as f:
                f.write(b"\x89PNG\r\n\x1a\nfake")
            ok_present, bundle_rel, msgs_present = auto_gate.build_evidence_bundle(result, repo_root=tmp, screenshots=[shot])
            self.assertTrue(ok_present, msgs_present)
            self.assertTrue(os.path.isfile(os.path.join(tmp, bundle_rel, "screenshots", "after.png")))


if __name__ == "__main__":
    unittest.main()
