#!/usr/bin/env python3
"""
tools/test_generate_release_verification_summary.py
Unit tests for release verification summary generator
(ASPICE SWE.6 BP5, REQ-PRO-074, TST-PRO-074).
"""

import json
import os
import shutil
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from generate_release_verification_summary import (
    collect_coverage_summary,
    collect_sys4_summary,
    collect_sys5_summary,
    collect_unit_test_summary,
    compile_deviations,
    generate_release_verification_summary,
    parse_args,
    render_markdown_summary,
)


class TestGenerateReleaseVerificationSummary(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp()
        self.tag = "V5.0.0"

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_parse_args_defaults(self):
        """TST-PRO-074.1: Verify default CLI argument parsing."""
        args = parse_args(["--tag", "V5.0.0"])
        self.assertEqual(args.tag, "V5.0.0")
        self.assertIsNone(args.commit)
        self.assertIsNone(args.out)
        self.assertFalse(args.fail_on_deviations)

    def test_parse_args_custom(self):
        """TST-PRO-074.1: Verify custom CLI flags."""
        args = parse_args([
            "--tag", "V5.0.0__2026111201",
            "--commit", "abcdef123456",
            "--out", "/custom/path/summary.md",
            "--min-req-tst-coverage", "50.0",
            "--min-tst-code-coverage", "60.0",
            "--fail-on-deviations",
        ])
        self.assertEqual(args.tag, "V5.0.0__2026111201")
        self.assertEqual(args.commit, "abcdef123456")
        self.assertEqual(args.out, "/custom/path/summary.md")
        self.assertEqual(args.min_req_tst_coverage, 50.0)
        self.assertEqual(args.min_tst_code_coverage, 60.0)
        self.assertTrue(args.fail_on_deviations)

    def test_collect_unit_test_summary_success_xml(self):
        """TST-PRO-074.2: Parse successful JUnit XML test reports."""
        results_dir = os.path.join(self.test_dir, "test-results")
        os.makedirs(results_dir, exist_ok=True)
        xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.SampleTest" tests="5" failures="0" errors="0" skipped="1" time="2.34">
  <testcase name="testOne" classname="com.example.SampleTest" time="0.5"/>
  <testcase name="testTwo" classname="com.example.SampleTest" time="0.5"/>
  <testcase name="testThree" classname="com.example.SampleTest" time="0.5"/>
  <testcase name="testFour" classname="com.example.SampleTest" time="0.5"/>
  <testcase name="testSkipped" classname="com.example.SampleTest" time="0.0">
    <skipped/>
  </testcase>
</testsuite>
"""
        with open(os.path.join(results_dir, "TEST-SampleTest.xml"), "w") as f:
            f.write(xml_content)

        summary = collect_unit_test_summary(
            self.test_dir, self.tag, results_dir=results_dir
        )
        self.assertTrue(summary["available"])
        self.assertEqual(summary["total_tests"], 5)
        self.assertEqual(summary["passed_tests"], 4)
        self.assertEqual(summary["failed_tests"], 0)
        self.assertEqual(summary["skipped_tests"], 1)
        self.assertEqual(summary["execution_time_sec"], 2.34)
        self.assertEqual(len(summary["deviations"]), 0)

    def test_collect_unit_test_summary_with_failures(self):
        """TST-PRO-074.2: Detect unit test failures from XML."""
        results_dir = os.path.join(self.test_dir, "test-results")
        os.makedirs(results_dir, exist_ok=True)
        xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.FailingTest" tests="2" failures="1" errors="0" skipped="0" time="1.0">
  <testcase name="testPass" classname="com.example.FailingTest" time="0.5"/>
  <testcase name="testFail" classname="com.example.FailingTest" time="0.5">
    <failure message="AssertionError: Expected true but was false"/>
  </testcase>
</testsuite>
"""
        with open(os.path.join(results_dir, "TEST-FailingTest.xml"), "w") as f:
            f.write(xml_content)

        summary = collect_unit_test_summary(
            self.test_dir, self.tag, results_dir=results_dir
        )
        self.assertTrue(summary["available"])
        self.assertEqual(summary["total_tests"], 2)
        self.assertEqual(summary["failed_tests"], 1)
        self.assertEqual(len(summary["failed_cases"]), 1)
        self.assertEqual(summary["failed_cases"][0]["name"], "testFail")
        self.assertIn("Unit test failures", summary["deviations"][0])

    def test_collect_unit_test_summary_from_zip(self):
        """TST-PRO-074.2: Parse JUnit XML from versioned zip archive."""
        archive_path = os.path.join(self.test_dir, f"junit-test-results-{self.tag}.zip")
        xml_content = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.ArchiveTest" tests="3" failures="0" errors="0" skipped="0" time="0.75">
  <testcase name="testA" classname="com.example.ArchiveTest" time="0.25"/>
  <testcase name="testB" classname="com.example.ArchiveTest" time="0.25"/>
  <testcase name="testC" classname="com.example.ArchiveTest" time="0.25"/>
</testsuite>
"""
        with zipfile.ZipFile(archive_path, "w") as z:
            z.writestr("test-results/TEST-ArchiveTest.xml", xml_content)

        summary = collect_unit_test_summary(
            self.test_dir, self.tag, results_dir=os.path.join(self.test_dir, "nonexistent"), archive_path=archive_path
        )
        self.assertTrue(summary["available"])
        self.assertEqual(summary["total_tests"], 3)
        self.assertEqual(summary["passed_tests"], 3)
        self.assertEqual(len(summary["deviations"]), 0)

    def test_collect_unit_test_summary_missing(self):
        """TST-PRO-074.2: Flag deviation when unit test evidence is absent."""
        summary = collect_unit_test_summary(
            self.test_dir, self.tag, results_dir=os.path.join(self.test_dir, "nonexistent")
        )
        self.assertFalse(summary["available"])
        self.assertIn("Unit test evidence missing", summary["deviations"][0])

    def test_collect_coverage_summary_threshold_detection(self):
        """TST-PRO-074.3: Evaluate coverage and detect threshold violations."""
        mock_report = {
            "summary": {
                "total_reqs": 100,
                "reqs_with_tst": 45,
                "req_tst_coverage_pct": 45.0,
                "total_tsts": 100,
                "tsts_with_code": 60,
                "tst_code_coverage_pct": 60.0,
                "code_tags_without_spec": 0,
                "bidirectional_coverage_pct": 40.0,
            },
        }
        mock_diag = ["FAIL: REQ-to-TST coverage 45.00% is below minimum threshold 50.00%."]

        with patch("generate_release_verification_summary.calculate_traceability_coverage", return_value=mock_report), \
             patch("generate_release_verification_summary.verify_coverage_thresholds", return_value=(False, mock_diag)):
            cov_summary = collect_coverage_summary(
                self.test_dir, min_req_tst_coverage=50.0
            )
            self.assertTrue(cov_summary["available"])
            self.assertFalse(cov_summary["gate_passed"])
            self.assertTrue(any("threshold violation" in d for d in cov_summary["deviations"]))

    def test_collect_coverage_summary_subprocess_fallback(self):
        """TST-PRO-074.3: Verify fallback to subprocess when direct module invocation fails."""
        mock_report = {
            "summary": {
                "total_reqs": 50,
                "reqs_with_tst": 25,
                "req_tst_coverage_pct": 50.0,
                "total_tsts": 50,
                "tsts_with_code": 25,
                "tst_code_coverage_pct": 50.0,
                "code_tags_without_spec": 0,
                "bidirectional_coverage_pct": 40.0,
            },
            "gate_result": {
                "passed": True,
                "diagnostics": ["PASS: OK"]
            }
        }
        with patch("generate_release_verification_summary.calculate_traceability_coverage", None), \
             patch("generate_release_verification_summary.subprocess.run") as mock_run:
            mock_run.return_value.stdout = json.dumps(mock_report)
            cov_summary = collect_coverage_summary(self.test_dir)
            self.assertTrue(cov_summary["available"])
            self.assertTrue(cov_summary["gate_passed"])
            self.assertEqual(len(cov_summary["deviations"]), 0)



    def test_collect_sys4_summary_pass(self):
        """TST-PRO-074.4: Parse valid SYS.4 JSON report."""
        rel_dir = os.path.join(self.test_dir, "docs", "releases", self.tag)
        os.makedirs(rel_dir, exist_ok=True)
        sys4_file = os.path.join(rel_dir, "system_integration_results.json")
        data = {
            "overall_status": "PASS",
            "total_test_cases": 6,
            "passed_test_cases": 6,
            "failed_test_cases": 0,
            "timestamp": "2026-11-12T10:00:00",
            "git_commit": "abcdef123",
            "test_results": {
                "SYS-INT-001": {"name": "BLE HRM Ingestion", "passed": True, "details": "PASS"},
                "SYS-INT-002": {"name": "BLE Cycling Power", "passed": True, "details": "PASS"},
                "SYS-INT-003": {"name": "ANT+ Cadence", "passed": True, "details": "PASS"},
                "SYS-INT-004": {"name": "GNSS Multipath", "passed": True, "details": "PASS"},
                "SYS-INT-005": {"name": "Radar Alerting", "passed": True, "details": "PASS"},
                "SYS-INT-006": {"name": "FIT/TCX Storage", "passed": True, "details": "PASS"},
            }
        }
        with open(sys4_file, "w") as f:
            json.dump(data, f)

        res = collect_sys4_summary(self.test_dir, self.tag)
        self.assertTrue(res["available"])
        self.assertEqual(res["overall_status"], "PASS")
        self.assertEqual(res["passed_cases"], 6)
        self.assertEqual(len(res["deviations"]), 0)

    def test_collect_sys4_summary_failing(self):
        """TST-PRO-074.4: Detect failing SYS.4 test cases."""
        rel_dir = os.path.join(self.test_dir, "docs", "releases", self.tag)
        os.makedirs(rel_dir, exist_ok=True)
        sys4_file = os.path.join(rel_dir, "system_integration_results.json")
        data = {
            "overall_status": "FAIL",
            "total_test_cases": 6,
            "passed_test_cases": 5,
            "failed_test_cases": 1,
            "test_results": {
                "SYS-INT-001": {"name": "BLE HRM Ingestion", "passed": False, "details": "Timeout"},
                "SYS-INT-002": {"name": "BLE Cycling Power", "passed": True, "details": "PASS"},
            }
        }
        with open(sys4_file, "w") as f:
            json.dump(data, f)

        res = collect_sys4_summary(self.test_dir, self.tag)
        self.assertTrue(res["available"])
        self.assertEqual(res["overall_status"], "FAIL")
        self.assertTrue(any("SYS.4 verification non-pass" in d for d in res["deviations"]))
        self.assertTrue(any("SYS-INT-001" in d for d in res["deviations"]))

    def test_collect_sys4_summary_missing(self):
        """TST-PRO-074.4: Flag missing SYS.4 evidence."""
        res = collect_sys4_summary(self.test_dir, self.tag)
        self.assertFalse(res["available"])
        self.assertIn("SYS.4 evidence missing", res["deviations"][0])

    def test_collect_sys5_summary_pass(self):
        """TST-PRO-074.4: Parse valid SYS.5 markdown sign-off record."""
        rel_dir = os.path.join(self.test_dir, "docs", "releases", self.tag)
        os.makedirs(rel_dir, exist_ok=True)
        sys5_file = os.path.join(rel_dir, "sys_verification_record.md")
        content = """# System Verification Sign-Off Record: Release V5.0.0

**Release Tag**: `V5.0.0`
**Overall Verdict**: **PASS**

## 2. Verification Test Results Matrix
| Test ID | Title | Traced Requirements | Environment | Result | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `SYS-VRF-001` | Multi-Sensor | `SYSREQ-TRK-001` | E2 | PASS | OK |
| `SYS-VRF-002` | RF Dropout | `SYSREQ-TRK-002` | E1 | PASS | OK |
| `SYS-VRF-003` | 1-Hz Sampling | `SYSREQ-TRK-003` | E4 | PASS | OK |
| `SYS-VRF-004` | GNSS Fusion | `SYSREQ-TRK-004` | E1 | PASS | OK |
| `SYS-VRF-005` | Battery | `SYSREQ-TRK-005` | E5 | PASS | OK |
| `SYS-VRF-006` | Radar Alert | `SYSREQ-TRK-006` | E2 | PASS | OK |
| `SYS-VRF-007` | FIT Export | `SYSREQ-DEX-001` | Storage | PASS | OK |
| `SYS-VRF-008` | Cloud Sync | `SYSREQ-DEX-003` | Cloud | PASS | OK |

## 4. Formal Sign-Off
* **System Verification Engineer**: John Doe Date: 2026-11-12
* **Process Owner / Quality Gatekeeper**: Jane Smith Date: 2026-11-12
"""
        with open(sys5_file, "w") as f:
            f.write(content)

        res = collect_sys5_summary(self.test_dir, self.tag)
        self.assertTrue(res["available"])
        self.assertEqual(res["overall_verdict"], "PASS")
        self.assertTrue(res["has_signoff"])
        self.assertEqual(len(res["deviations"]), 0)

    def test_collect_sys5_summary_placeholders(self):
        """TST-PRO-074.4: Detect unedited placeholder template in SYS.5 record."""
        rel_dir = os.path.join(self.test_dir, "docs", "releases", self.tag)
        os.makedirs(rel_dir, exist_ok=True)
        sys5_file = os.path.join(rel_dir, "sys_verification_record.md")
        content = """# System Verification Sign-Off Record: Release V5.0.0

**Overall Verdict**: **[ PASS / REVISION / FAIL ]**

## 2. Verification Test Results Matrix
| Test ID | Title | Traced Requirements | Environment | Result | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `SYS-VRF-001` | Multi-Sensor | `SYSREQ-TRK-001` | E2 | [ PASS / FAIL ] | |

## 4. Formal Sign-Off
* **System Verification Engineer**: ___________________________ Date: ____________
* **Process Owner / Quality Gatekeeper**: _____________________ Date: ____________
"""
        with open(sys5_file, "w") as f:
            f.write(content)

        res = collect_sys5_summary(self.test_dir, self.tag)
        self.assertTrue(res["available"])
        self.assertTrue(any("SYS.5 overall verdict unapproved" in d for d in res["deviations"]))
        self.assertTrue(any("SYS.5 formal sign-off incomplete" in d for d in res["deviations"]))

    def test_compile_deviations_status(self):
        """TST-PRO-074.5: Compile deviations and set overall status."""
        # 1. Clean run -> PASS
        u1 = {"deviations": []}
        c1 = {"deviations": []}
        s4_1 = {"deviations": []}
        s5_1 = {"deviations": []}
        devs, status = compile_deviations(u1, c1, s4_1, s5_1)
        self.assertEqual(len(devs), 0)
        self.assertEqual(status, "PASS (READY FOR RELEASE)")

        # 2. Deviations present -> BLOCKED
        u2 = {"deviations": ["Test failure"]}
        devs, status = compile_deviations(u2, c1, s4_1, s5_1)
        self.assertEqual(len(devs), 1)
        self.assertEqual(status, "BLOCKED / REQUIRES HUMAN APPROVAL")

    def test_render_markdown_and_end_to_end(self):
        """TST-PRO-074.5: End-to-end execution generates verification_summary.md."""
        rel_dir = os.path.join(self.test_dir, "docs", "releases", self.tag)
        os.makedirs(rel_dir, exist_ok=True)

        # Create valid SYS.4
        with open(os.path.join(rel_dir, "system_integration_results.json"), "w") as f:
            json.dump({
                "overall_status": "PASS",
                "total_test_cases": 6,
                "passed_test_cases": 6,
                "failed_test_cases": 0,
                "timestamp": "2026-11-12",
                "git_commit": "123456",
                "test_results": {f"SYS-INT-00{i}": {"name": f"Test {i}", "passed": True} for i in range(1, 7)}
            }, f)

        # Create valid SYS.5
        with open(os.path.join(rel_dir, "sys_verification_record.md"), "w") as f:
            f.write("""# Record
**Overall Verdict**: **PASS**
| `SYS-VRF-001` | T1 | R1 | Env | PASS | Notes |
| `SYS-VRF-002` | T2 | R2 | Env | PASS | Notes |
| `SYS-VRF-003` | T3 | R3 | Env | PASS | Notes |
| `SYS-VRF-004` | T4 | R4 | Env | PASS | Notes |
| `SYS-VRF-005` | T5 | R5 | Env | PASS | Notes |
| `SYS-VRF-006` | T6 | R6 | Env | PASS | Notes |
| `SYS-VRF-007` | T7 | R7 | Env | PASS | Notes |
| `SYS-VRF-008` | T8 | R8 | Env | PASS | Notes |
* **System Verification Engineer**: QA Lead Date: 2026-11-12
* **Process Owner / Quality Gatekeeper**: Lead Date: 2026-11-12
""")

        # Create valid unit test results
        test_res_dir = os.path.join(self.test_dir, "app", "build", "test-results", "testDebugUnitTest")
        os.makedirs(test_res_dir, exist_ok=True)
        with open(os.path.join(test_res_dir, "TEST-A.xml"), "w") as f:
            f.write('<testsuite name="A" tests="10" failures="0" errors="0" skipped="0" time="1.0"/>')

        out_file = os.path.join(rel_dir, "verification_summary.md")
        generated_path, passed = generate_release_verification_summary(
            tag=self.tag,
            commit_sha="fedcba987654",
            out_path=out_file,
            repo_root=self.test_dir,
            test_results_dir=test_res_dir,
        )

        self.assertEqual(generated_path, out_file)
        self.assertTrue(os.path.isfile(out_file))

        with open(out_file, "r") as f:
            content = f.read()

        self.assertIn(f"Release Verification Summary: Release {self.tag}", content)
        self.assertIn("fedcba987654", content)
        self.assertIn("Executive Verification Dashboard", content)
        self.assertIn("Unit & Regression Testing Evidence", content)
        self.assertIn("Bidirectional Requirement & Test Traceability Matrix", content)
        self.assertIn("System Integration Verification Summary", content)
        self.assertIn("System Verification Protocol Sign-Off Summary", content)
        self.assertIn("Deviations Requiring Human Approval", content)


if __name__ == "__main__":
    unittest.main()
