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
        GROUP_B_MANUAL_NFRS,
        generate_test_evidence,
        get_git_commit,
        parse_kover_xml_report,
        parse_nfr_measurements,
        render_markdown_evidence,
    )
except ImportError:
    from generate_test_evidence import (  # type: ignore
        CRITICAL_SUBSYSTEMS,
        GROUP_B_MANUAL_NFRS,
        generate_test_evidence,
        get_git_commit,
        parse_kover_xml_report,
        parse_nfr_measurements,
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


    def test_kover_skipped_for_all_regular_schichten_but_measured_for_weekly_and_release(self):
        """All regular Schicht sprints skip Kover; weekly audit targets and releases measure it."""
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

            for sprint_id in ("sprint_2026-11-17_01_Früh", "sprint_2026-11-22_02_Spät", "sprint_2026-11-16_03_Nacht"):
                _, _, payload = generate_test_evidence(
                    target_id=sprint_id,
                    target_type="sprint",
                    repo_root=tmp_root,
                    results_dir=results_dir,
                    out_dir=out_dir,
                    commit_sha="deadbeef",
                )
                self.assertFalse(payload["coverage"]["measured"], f"Expected Kover skipped for {sprint_id}")

            _, _, payload_weekly = generate_test_evidence(
                target_id="weekly_2026-KW47",
                target_type="generic",
                repo_root=tmp_root,
                results_dir=results_dir,
                out_dir=out_dir,
                commit_sha="deadbeef",
            )
            self.assertTrue(payload_weekly["coverage"]["measured"])

            _, _, payload_forced = generate_test_evidence(
                target_id="weekly_2026-KW47",
                target_type="generic",
                repo_root=tmp_root,
                results_dir=results_dir,
                out_dir=out_dir,
                commit_sha="deadbeef",
                skip_kover=True,
            )
            self.assertFalse(payload_forced["coverage"]["measured"])

    def test_parse_nfr_measurements_valid_missing_and_malformed(self):
        """TST-PRO-113.3: Verify NFR JSON parser handles valid, missing, and malformed inputs cleanly."""
        with tempfile.TemporaryDirectory() as tmp_dir:
            manual_tpl = os.path.join(tmp_dir, "nfr_manual_protocol_template.md")
            with open(manual_tpl, "w", encoding="utf-8") as f:
                f.write("[MANUAL PROTOCOL — NOT EXECUTED IN JVM]\n")

            missing_res = parse_nfr_measurements(
                os.path.join(tmp_dir, "missing.json"),
                manual_protocol_path=manual_tpl,
            )
            self.assertFalse(missing_res["measured"])
            self.assertEqual(missing_res["measured_count"], 0)
            self.assertTrue(missing_res["manual_protocol_present"])
            self.assertEqual(len(missing_res["manual_protocols"]), len(GROUP_B_MANUAL_NFRS))
            self.assertTrue(all(m["status"] == "PENDING MANUAL" for m in missing_res["manual_protocols"]))

            malformed_path = os.path.join(tmp_dir, "bad.json")
            with open(malformed_path, "w", encoding="utf-8") as f:
                f.write("{broken json")
            malformed_res = parse_nfr_measurements(malformed_path, manual_protocol_path=manual_tpl)
            self.assertFalse(malformed_res["measured"])
            self.assertIn("Failed to parse NFR measurement report", malformed_res["reason"])

            valid_path = os.path.join(tmp_dir, "nfr_measurements.json")
            with open(valid_path, "w", encoding="utf-8") as f:
                json.dump(
                    {
                        "schema_version": 1,
                        "suite": "MeasuredNfrBenchmarkTest",
                        "measurements": [
                            {
                                "req_id": "REQ-NFR-001",
                                "tst_id": "TST-NFR-001",
                                "name": "Deterministic EKF Timing",
                                "metric": "ekf_cycle_p95_ms",
                                "measured_value": 0.042,
                                "unit": "ms",
                                "budget": "<= 2.0 ms",
                                "passed": True,
                                "warmup_iterations": 200,
                                "measured_iterations": 1000,
                                "details": "max_dt_error_ms=0.0000",
                            },
                            {
                                "req_id": "REQ-NFR-011",
                                "tst_id": "TST-NFR-011",
                                "name": "Telemetry Storage Growth Rate Ceiling",
                                "metric": "storage_growth_kb_per_h",
                                "measured_value": 240.0,
                                "unit": "KB/h",
                                "budget": "<= 360.0 KB/h",
                                "passed": True,
                                "warmup_iterations": 0,
                                "measured_iterations": 3600,
                                "details": "bytes_per_sample=68.2",
                            },
                        ],
                    },
                    f,
                )

            valid_res = parse_nfr_measurements(valid_path, manual_protocol_path=manual_tpl)
            self.assertTrue(valid_res["measured"])
            self.assertEqual(valid_res["measured_count"], 2)
            self.assertEqual(valid_res["passed_count"], 2)
            self.assertEqual(valid_res["failed_count"], 0)

            # Verify missing manual protocol template reports MISSING PROTOCOL honestly
            missing_tpl_res = parse_nfr_measurements(
                valid_path,
                manual_protocol_path=os.path.join(tmp_dir, "absent_template.md"),
            )
            self.assertFalse(missing_tpl_res["manual_protocol_present"])
            self.assertTrue(all(m["status"] == "MISSING PROTOCOL" for m in missing_tpl_res["manual_protocols"]))

            # Verify attested PASS / FAIL rows in nfr_manual_protocol_template.md are parsed accurately
            attested_tpl = os.path.join(tmp_dir, "attested_manual.md")
            with open(attested_tpl, "w", encoding="utf-8") as f:
                f.write(
                    "| REQ / TST ID | Budget Target | Verification Procedure & Exact Tooling | Device / Build | Date | Operator | Measured Value | Verdict |\n"
                    "| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n"
                    "| `REQ-NFR-004` / `TST-NFR-004` | `< 5.0%` | simpleperf | `Pixel 8 / AP3A` | `2026-11-17` | `Rainer` | `3.2%` | `PASS` |\n"
                    "| `REQ-NFR-006` / `TST-NFR-006` | `<= 5.0%/h` | Battery Historian | `Pixel 8 / AP3A` | `2026-11-17` | `Rainer` | `6.1%/h` | `FAIL` |\n"
                    "| `REQ-NFR-012` / `TST-NFR-012` | `< 16.6 ms` | gfxinfo | `<TBD>` | `<TBD>` | `<TBD>` | `<NOT MEASURED>` | `PENDING MANUAL` |\n"
                )
            attested_res = parse_nfr_measurements(valid_path, manual_protocol_path=attested_tpl)
            by_req = {m["req_id"]: m for m in attested_res["manual_protocols"]}
            self.assertEqual(by_req["REQ-NFR-004"]["status"], "PASS")
            self.assertEqual(by_req["REQ-NFR-004"]["device_build"], "Pixel 8 / AP3A")
            self.assertEqual(by_req["REQ-NFR-004"]["measured_value"], "3.2%")
            self.assertEqual(by_req["REQ-NFR-006"]["status"], "FAIL")
            self.assertEqual(by_req["REQ-NFR-012"]["status"], "PENDING MANUAL")

            # Verify per-NFR atomic REQ-NFR-*.json fallback/merge without combined nfr_measurements.json
            per_nfr_path = os.path.join(tmp_dir, "REQ-NFR-007.json")
            with open(per_nfr_path, "w", encoding="utf-8") as f:
                json.dump(
                    {
                        "req_id": "REQ-NFR-007",
                        "tst_id": "TST-NFR-007",
                        "name": "Sensor Dispatch",
                        "metric": "dispatch_p99_ms",
                        "measured_value": 0.01,
                        "unit": "ms",
                        "budget": "< 25.0 ms",
                        "passed": True,
                        "warmup_iterations": 200,
                        "measured_iterations": 1000,
                        "details": "ok",
                    },
                    f,
                )
            merged_res = parse_nfr_measurements(
                os.path.join(tmp_dir, "missing_combined.json"),
                manual_protocol_path=attested_tpl,
            )
            self.assertTrue(merged_res["measured"])
            self.assertEqual(merged_res["measured_count"], 1)
            self.assertEqual(merged_res["measurements"][0]["req_id"], "REQ-NFR-007")

    def test_generate_test_evidence_ingests_nfr_measurements_and_manual_protocols(self):
        """TST-PRO-113.3: Verify end-to-end evidence generation renders NFR Group A metrics & Group B manual status."""
        with tempfile.TemporaryDirectory() as tmp_root:
            out_dir = os.path.join(tmp_root, "docs", "engineering", "test_evidence")
            results_dir = os.path.join(tmp_root, "app", "build", "test-results")
            nfr_dir = os.path.join(tmp_root, "app", "build", "reports", "nfr")
            os.makedirs(out_dir, exist_ok=True)
            os.makedirs(results_dir, exist_ok=True)
            os.makedirs(nfr_dir, exist_ok=True)

            with open(os.path.join(out_dir, "nfr_manual_protocol_template.md"), "w", encoding="utf-8") as f:
                f.write("[MANUAL PROTOCOL — NOT EXECUTED IN JVM]\n")
            with open(os.path.join(results_dir, "TEST-SampleTest.xml"), "w", encoding="utf-8") as f:
                f.write('<testsuite name="T" tests="1" failures="0" errors="0" skipped="0" time="0.1"><testcase name="t_TST_PRO_113" classname="T" time="0.1"/></testsuite>')

            nfr_json = os.path.join(nfr_dir, "nfr_measurements.json")
            with open(nfr_json, "w", encoding="utf-8") as f:
                json.dump(
                    {
                        "schema_version": 1,
                        "suite": "MeasuredNfrBenchmarkTest",
                        "measurements": [
                            {
                                "req_id": "REQ-NFR-003",
                                "tst_id": "TST-NFR-003",
                                "name": "Periodic Tick Latency Budget",
                                "metric": "tick_p99_ms",
                                "measured_value": 4.25,
                                "unit": "ms",
                                "budget": "< 50.0 ms",
                                "passed": True,
                                "warmup_iterations": 20,
                                "measured_iterations": 200,
                                "details": "p99_ms=4.250",
                            }
                        ],
                    },
                    f,
                )

            md_path, json_path, payload = generate_test_evidence(
                target_id="sprint_2026-11-17_01_Früh",
                target_type="sprint",
                repo_root=tmp_root,
                results_dir=results_dir,
                out_dir=out_dir,
                commit_sha="12345678",
                timestamp="2026-11-17T09:00:00Z",
            )

            self.assertTrue(payload["nfr_verification"]["measured"])
            self.assertEqual(payload["nfr_verification"]["passed_count"], 1)
            self.assertEqual(len(payload["nfr_verification"]["manual_protocols"]), 7)

            with open(md_path, "r", encoding="utf-8") as f:
                md_text = f.read()
            self.assertIn("4. Non-Functional Requirements (NFR) Verification Summary", md_text)
            self.assertIn("`REQ-NFR-003`", md_text)
            self.assertIn("`4.2500 ms`", md_text)
            self.assertIn("`REQ-NFR-004`", md_text)
            self.assertIn("PENDING MANUAL", md_text)
            self.assertIn("[MANUAL PROTOCOL — NOT EXECUTED IN JVM]", md_text)

            with open(json_path, "r", encoding="utf-8") as f:
                disk_payload = json.load(f)
            self.assertIn("nfr_verification", disk_payload)
            self.assertEqual(disk_payload["nfr_verification"]["measurements"][0]["measured_value"], 4.25)

    def test_snapshot_merge_and_guardrails_reject_truncated_or_failing_evidence(self):
        """ATT-9497 / Finding K3: Merge full Tier-2 snapshot with targeted re-run and enforce min_tests/allow_failure."""
        try:
            from tools.generate_test_evidence import main as gte_main, snapshot_junit_results
        except ImportError:
            from generate_test_evidence import main as gte_main, snapshot_junit_results

        with tempfile.TemporaryDirectory() as tmp_root:
            tier2_dir = os.path.join(tmp_root, "tier2_results")
            snap_dir = os.path.join(tmp_root, "tier2_snapshot")
            rerun_dir = os.path.join(tmp_root, "rerun_results")
            out_dir = os.path.join(tmp_root, "out")
            os.makedirs(tier2_dir, exist_ok=True)
            os.makedirs(rerun_dir, exist_ok=True)

            # Full Tier-2 suite A (2 tests, passing) + flaky suite B (1 failing)
            with open(os.path.join(tier2_dir, "TEST-SuiteA.xml"), "w", encoding="utf-8") as f:
                f.write('<testsuite name="SuiteA" tests="2" failures="0" errors="0" skipped="0" time="0.2">'
                        '<testcase name="t1" classname="SuiteA" time="0.1"/>'
                        '<testcase name="t2" classname="SuiteA" time="0.1"/></testsuite>')
            with open(os.path.join(tier2_dir, "TEST-SuiteB.xml"), "w", encoding="utf-8") as f:
                f.write('<testsuite name="SuiteB" tests="1" failures="1" errors="0" skipped="0" time="0.1">'
                        '<testcase name="t3" classname="SuiteB" time="0.1"><failure message="flaky"/></testcase></testsuite>')

            copied = snapshot_junit_results(tier2_dir, snap_dir)
            self.assertEqual(copied, 2)

            # Targeted re-run only has SuiteB (now passing)
            with open(os.path.join(rerun_dir, "TEST-SuiteB.xml"), "w", encoding="utf-8") as f:
                f.write('<testsuite name="SuiteB" tests="1" failures="0" errors="0" skipped="0" time="0.1">'
                        '<testcase name="t3" classname="SuiteB" time="0.1"/></testsuite>')

            # 1. Refuses failing evidence without allow_failure=True
            with self.assertRaises(ValueError):
                generate_test_evidence(
                    target_id="sprint_2026-47.9",
                    target_type="sprint",
                    repo_root=tmp_root,
                    results_dir=tier2_dir,
                    out_dir=out_dir,
                    commit_sha="deadbeef",
                    skip_kover=True,
                )

            # 2. Explicit allow_failure=True permits archiving failing evidence
            _, _, fail_payload = generate_test_evidence(
                target_id="sprint_2026-47.9_fail",
                target_type="sprint",
                repo_root=tmp_root,
                results_dir=tier2_dir,
                out_dir=out_dir,
                commit_sha="deadbeef",
                skip_kover=True,
                allow_failure=True,
            )
            self.assertEqual(fail_payload["unit_tests"]["failures"], 1)

            # 3. Refuses truncated test count below min_tests threshold
            with self.assertRaises(ValueError):
                generate_test_evidence(
                    target_id="sprint_2026-47.9_trunc",
                    target_type="sprint",
                    repo_root=tmp_root,
                    results_dir=rerun_dir,
                    out_dir=out_dir,
                    commit_sha="deadbeef",
                    skip_kover=True,
                    min_tests=3,
                )

            # 4. Merging snapshot + targeted re-run preserves SuiteA and updates SuiteB -> 3 passing tests!
            md_path, _, merged_payload = generate_test_evidence(
                target_id="sprint_2026-47.9_merged",
                target_type="sprint",
                repo_root=tmp_root,
                results_dir=rerun_dir,
                merge_results_dirs=[snap_dir],
                out_dir=out_dir,
                commit_sha="deadbeef",
                skip_kover=True,
                min_tests=3,
            )
            self.assertTrue(os.path.isfile(md_path))
            self.assertEqual(merged_payload["unit_tests"]["total"], 3)
            self.assertEqual(merged_payload["unit_tests"]["failures"], 0)

            # 5. CLI exits non-zero when default min-tests threshold (100) is not met
            rc = gte_main([
                "--sprint", "2026-47.9_cli",
                "--repo-root", tmp_root,
                "--test-results-dir", rerun_dir,
                "--out-dir", out_dir,
                "--commit", "deadbeef",
                "--no-kover",
            ])
            self.assertEqual(rc, 1)


if __name__ == "__main__":
    unittest.main()
