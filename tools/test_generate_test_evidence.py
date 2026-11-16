#!/usr/bin/env python3
"""
tools/test_generate_test_evidence.py
Unit test suite for tools/generate_test_evidence.py (REQ-PRO-100 / TST-PRO-100).
"""

import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))

try:
    from tools.generate_test_evidence import (
        CRITICAL_SUBSYSTEMS,
        generate_test_evidence,
        get_git_commit,
        parse_kover_xml_report,
        render_markdown_evidence,
    )
except ImportError:
    from generate_test_evidence import (  # type: ignore
        CRITICAL_SUBSYSTEMS,
        generate_test_evidence,
        get_git_commit,
        parse_kover_xml_report,
        render_markdown_evidence,
    )


class TestGenerateTestEvidence(unittest.TestCase):
    """
    TST-PRO-100: Unit test suite for test and coverage evidence generation (REQ-PRO-100).
    """

    def setUp(self):
        self.sample_kover_xml = """<?xml version="1.0" encoding="UTF-8"?>
<report name="reportDebug">
    <counter type="INSTRUCTION" missed="500" covered="1500"/>
    <counter type="LINE" missed="200" covered="800"/>
    <counter type="BRANCH" missed="100" covered="300"/>
    <package name="com/atrainingtracker/trainingtracker/tracker">
        <counter type="LINE" missed="40" covered="60"/>
        <counter type="BRANCH" missed="50" covered="50"/>
    </package>
    <package name="com/atrainingtracker/banalservice/filters">
        <counter type="LINE" missed="20" covered="80"/>
        <counter type="BRANCH" missed="30" covered="70"/>
    </package>
    <package name="com/atrainingtracker/banalservice/devices">
        <counter type="LINE" missed="30" covered="70"/>
        <counter type="BRANCH" missed="40" covered="60"/>
    </package>
    <package name="com/atrainingtracker/banalservice/sensor">
        <counter type="LINE" missed="10" covered="30"/>
        <counter type="BRANCH" missed="10" covered="30"/>
    </package>
</report>
"""

    def test_parse_kover_xml_report_with_metrics(self):
        """TST-PRO-100.3: Verify Kover XML parser extracts global and critical package metrics."""
        with tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False) as f:
            f.write(self.sample_kover_xml)
            tmp_path = f.name

        try:
            res = parse_kover_xml_report(tmp_path)
            self.assertTrue(res["measured"])
            self.assertEqual(res["global"]["line_percent"], 80.0)  # 800 / 1000
            self.assertEqual(res["global"]["branch_percent"], 75.0)  # 300 / 400

            crit = res["critical_packages"]
            self.assertIn("tracking", crit)
            self.assertIn("filters", crit)
            self.assertIn("sensors", crit)

            # Tracking: 60/100 = 60.0% LINE (floor 55.0 => PASS), 50/100 = 50.0% BRANCH (floor 45.0 => PASS)
            self.assertEqual(crit["tracking"]["line_percent"], 60.0)
            self.assertEqual(crit["tracking"]["line_status"], "PASS")
            self.assertEqual(crit["tracking"]["branch_percent"], 50.0)
            self.assertEqual(crit["tracking"]["branch_status"], "PASS")

            # Filters: 80/100 = 80.0% LINE (floor 70.0 => PASS), 70/100 = 70.0% BRANCH (floor 50.0 => PASS)
            self.assertEqual(crit["filters"]["line_percent"], 80.0)
            self.assertEqual(crit["filters"]["line_status"], "PASS")
            self.assertEqual(crit["filters"]["branch_percent"], 70.0)
            self.assertEqual(crit["filters"]["branch_status"], "PASS")

            # Sensors (devices + sensor aggregated):
            # LINE: (70+30)/(70+30 + 30+10) = 100/140 = 71.43% (floor 60.0 => PASS)
            # BRANCH: (60+30)/(60+30 + 40+10) = 90/140 = 64.29% (floor 50.0 => PASS)
            self.assertEqual(crit["sensors"]["line_percent"], 71.43)
            self.assertEqual(crit["sensors"]["line_status"], "PASS")
            self.assertEqual(crit["sensors"]["branch_percent"], 64.29)
            self.assertEqual(crit["sensors"]["branch_status"], "PASS")
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    def test_parse_kover_xml_report_missing_file(self):
        """TST-PRO-100.3: Verify parser handles missing Kover XML gracefully without failure."""
        res = parse_kover_xml_report("/non/existent/path/reportDebug.xml")
        self.assertFalse(res["measured"])
        self.assertIn("not found", res["reason"])
        self.assertIsNone(res["global"]["line_percent"])
        self.assertEqual(res["critical_packages"], {})

    def test_parse_kover_xml_report_malformed_xml(self):
        """TST-PRO-100.3: Verify parser handles malformed XML gracefully."""
        with tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False) as f:
            f.write("<not>closed xml report")
            tmp_path = f.name

        try:
            res = parse_kover_xml_report(tmp_path)
            self.assertFalse(res["measured"])
            self.assertIn("Failed to parse", res["reason"])
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    def test_render_markdown_evidence(self):
        """TST-PRO-100.1: Verify Markdown test evidence render structure and tables."""
        test_summary = {
            "total": 100,
            "passed": 98,
            "failures": 2,
            "errors": 0,
            "skipped": 0,
            "time": 4.5,
        }
        tst_results = {
            "TST-PRO-100": {"status": "PASS", "passing_cases": [{"name": "testA"}], "failing_cases": []},
            "TST-BUG-001": {"status": "FAIL", "passing_cases": [], "failing_cases": [{"name": "testB"}]},
        }
        cov_summary = {
            "measured": True,
            "global": {"line_percent": 55.5, "branch_percent": 45.0},
            "critical_packages": {
                "tracking": {
                    "name": "Critical Tracking",
                    "line_percent": 60.0,
                    "line_floor": 55.0,
                    "line_status": "PASS",
                    "branch_percent": 50.0,
                    "branch_floor": 45.0,
                    "branch_status": "PASS",
                }
            },
        }

        md = render_markdown_evidence(
            target_id="sprint_2026-46.8",
            target_type="sprint",
            commit_sha="abcd1234efgh5678",
            timestamp="2026-11-16T00:00:00Z",
            test_summary=test_summary,
            tst_results=tst_results,
            coverage_summary=cov_summary,
        )

        self.assertIn("# Test & Coverage Evidence: sprint_2026-46.8", md)
        self.assertIn("abcd1234efgh5678", md)
        self.assertIn("FAIL", md)  # overall result due to 2 failures
        self.assertIn("Critical Tracking", md)
        self.assertIn("`TST-PRO-100`", md)
        self.assertIn("`TST-BUG-001`", md)

    def test_generate_test_evidence_e2e_sprint_and_release(self):
        """TST-PRO-100.1 / TST-PRO-100.4: End-to-end evidence generation for sprint and release targets."""
        with tempfile.TemporaryDirectory() as tmp_root:
            out_dir = os.path.join(tmp_root, "docs", "engineering", "test_evidence")
            results_dir = os.path.join(tmp_root, "app", "build", "test-results")
            os.makedirs(results_dir, exist_ok=True)

            # Synthetic passing JUnit XML
            junit_xml = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.atrainingtracker.TestEvidenceTest" tests="2" failures="0" errors="0" skipped="0" time="1.2">
    <testcase name="testA_TST_PRO_100" classname="com.atrainingtracker.TestEvidenceTest" time="0.6"/>
    <testcase name="testB" classname="com.atrainingtracker.TestEvidenceTest" time="0.6"/>
</testsuite>
"""
            with open(os.path.join(results_dir, "TEST-SampleTest.xml"), "w", encoding="utf-8") as f:
                f.write(junit_xml)

            # Synthetic Kover XML
            kover_path = os.path.join(tmp_root, "reportDebug.xml")
            with open(kover_path, "w", encoding="utf-8") as f:
                f.write(self.sample_kover_xml)

            # 1. Sprint target
            md_path, json_path, payload = generate_test_evidence(
                target_id="sprint_2026-46.8",
                target_type="sprint",
                repo_root=tmp_root,
                results_dir=results_dir,
                kover_xml_path=kover_path,
                out_dir=out_dir,
                commit_sha="c0ffee1234567890",
                timestamp="2026-11-16T01:00:00Z",
            )

            self.assertTrue(os.path.isfile(md_path))
            self.assertTrue(os.path.isfile(json_path))
            self.assertEqual(payload["target"], "sprint_2026-46.8")
            self.assertEqual(payload["unit_tests"]["total"], 2)
            self.assertEqual(payload["unit_tests"]["failures"], 0)
            self.assertTrue(payload["coverage"]["measured"])

            with open(json_path, "r", encoding="utf-8") as f:
                disk_json = json.load(f)
            self.assertEqual(disk_json["commit"], "c0ffee1234567890")

            # 2. Release target
            md_rel, json_rel, payload_rel = generate_test_evidence(
                target_id="V5.0.0",
                target_type="release",
                repo_root=tmp_root,
                results_dir=results_dir,
                kover_xml_path=kover_path,
                out_dir=out_dir,
                commit_sha="c0ffee1234567890",
            )
            self.assertTrue(os.path.isfile(md_rel))
            self.assertTrue(os.path.isfile(json_rel))
            self.assertEqual(payload_rel["type"], "release")


    def test_kover_skipped_for_frueh_and_nacht_even_with_stale_xml(self):
        """01_Früh and 03_Nacht skip Kover (preventing stale reportDebug.xml leaks), while 02_Spät measures it."""
        with tempfile.TemporaryDirectory() as tmp_root:
            out_dir = os.path.join(tmp_root, "docs", "engineering", "test_evidence")
            results_dir = os.path.join(tmp_root, "app", "build", "test-results")
            kover_dir = os.path.join(tmp_root, "app", "build", "reports", "kover")
            os.makedirs(results_dir, exist_ok=True)
            os.makedirs(kover_dir, exist_ok=True)
            with open(os.path.join(results_dir, "TEST-SampleTest.xml"), "w", encoding="utf-8") as f:
                f.write('<testsuite name="T" tests="1" failures="0" errors="0" skipped="0" time="0.1"><testcase name="t" classname="T" time="0.1"/></testsuite>')
            with open(os.path.join(kover_dir, "reportDebug.xml"), "w", encoding="utf-8") as f:
                f.write(self.sample_kover_xml)

            for sprint_id in ("sprint_2026-11-17_01_Früh", "sprint_2026-11-16_03_Nacht"):
                _, _, payload = generate_test_evidence(
                    target_id=sprint_id,
                    target_type="sprint",
                    repo_root=tmp_root,
                    results_dir=results_dir,
                    out_dir=out_dir,
                    commit_sha="deadbeef",
                )
                self.assertFalse(payload["coverage"]["measured"], f"Expected Kover skipped for {sprint_id}")

            _, _, payload_spaet = generate_test_evidence(
                target_id="sprint_2026-11-16_02_Spät",
                target_type="sprint",
                repo_root=tmp_root,
                results_dir=results_dir,
                out_dir=out_dir,
                commit_sha="deadbeef",
            )
            self.assertTrue(payload_spaet["coverage"]["measured"])

            _, _, payload_forced = generate_test_evidence(
                target_id="sprint_2026-11-16_02_Spät",
                target_type="sprint",
                repo_root=tmp_root,
                results_dir=results_dir,
                out_dir=out_dir,
                commit_sha="deadbeef",
                skip_kover=True,
            )
            self.assertFalse(payload_forced["coverage"]["measured"])


if __name__ == "__main__":
    unittest.main()
