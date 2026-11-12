#!/usr/bin/env python3
"""
tools/test_system_integration_tests.py
======================================
Unit tests for the system integration test runner (tools/run_system_integration_tests.py).
Verifies:
- Execution of all 6 formal integration test cases (SYS-INT-001 through SYS-INT-006).
- Targeted test filtering via target_test argument.
- JSON report export schema and artifact validity.
- Pure Python 3 standard library compliance (zero non-stdlib imports).

Requirement: REQ-PRO-068 (SYS.4)
Test Spec:   TST-PRO-068.3, TST-PRO-068.4
"""

import ast
import json
import os
import sys
import tempfile
import unittest

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

from run_system_integration_tests import SystemIntegrationRunner, get_git_commit


class TestSystemIntegrationRunner(unittest.TestCase):

    def setUp(self):
        self.runner = SystemIntegrationRunner(verbose=False)

    def test_sys_int_001_ble_hr(self):
        passed, msg, details = self.runner.run_sys_int_001_ble_hr()
        self.assertTrue(passed, f"SYS-INT-001 failed: {msg}")
        self.assertEqual(details.get("mean_bpm"), 140)
        self.assertIn("phase_conservation_drift_ms", details)
        self.assertLessEqual(details["phase_conservation_drift_ms"], 5.0)
        self.assertEqual(
            details.get("exponential_backoff_schedule_ms"),
            [1000, 2000, 4000, 8000, 16000],
        )

    def test_sys_int_002_ble_power(self):
        passed, msg, details = self.runner.run_sys_int_002_ble_power()
        self.assertTrue(passed, f"SYS-INT-002 failed: {msg}")
        self.assertEqual(details.get("power_watts"), 200)
        self.assertEqual(details.get("cadence_rpm"), 90)
        self.assertIn("asymmetry_tested", details)

    def test_sys_int_003_ble_radar(self):
        passed, msg, details = self.runner.run_sys_int_003_ble_radar()
        self.assertTrue(passed, f"SYS-INT-003 failed: {msg}")
        self.assertIn("initial_target_count", details)
        self.assertIn("light_command_opcodes", details)
        self.assertEqual(details.get("starvation_watchdog_ms"), 3500)
        self.assertTrue(details.get("starvation_suppression_verified"))

    def test_sys_int_004_ant_ipc(self):
        passed, msg, details = self.runner.run_sys_int_004_ant_ipc()
        self.assertTrue(passed, f"SYS-INT-004 failed: {msg}")
        self.assertEqual(details.get("nominal_broadcast_interval_ms"), 250)
        self.assertEqual(details.get("timing_tolerance_ms"), 25)
        self.assertIn("ANT_HR", details.get("supported_device_types", {}))

    def test_sys_int_005_gnss_location(self):
        passed, msg, details = self.runner.run_sys_int_005_gnss_location()
        self.assertTrue(passed, f"SYS-INT-005 failed: {msg}")
        self.assertGreater(details.get("total_distance_m", 0.0), 200.0)
        self.assertEqual(details.get("null_island_rejection"), "VERIFIED")
        self.assertEqual(details.get("sampling_period_target_ms"), 1000)

    def test_sys_int_006_cloud_sync(self):
        passed, msg, details = self.runner.run_sys_int_006_cloud_sync()
        self.assertTrue(passed, f"SYS-INT-006 failed: {msg}")
        self.assertIn("upload_form_fields", details)
        self.assertEqual(details.get("expected_terminal_status"), "Your activity is ready.")

    def test_execute_all_overall_pass(self):
        success = self.runner.execute_all()
        self.assertTrue(success)
        self.assertEqual(len(self.runner.results), 6)
        for test_id, res in self.runner.results.items():
            self.assertTrue(res["passed"], f"Test {test_id} failed: {res['message']}")
            self.assertEqual(res["status"], "PASS")

    def test_execute_target_filter(self):
        success = self.runner.execute_all(target_test="SYS-INT-001")
        self.assertTrue(success)
        self.assertEqual(len(self.runner.results), 1)
        self.assertIn("SYS-INT-001", self.runner.results)

    def test_export_json_schema(self):
        self.runner.execute_all()
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tmp:
            tmp_path = tmp.name

        try:
            self.runner.export_json(tmp_path)
            self.assertTrue(os.path.exists(tmp_path))
            with open(tmp_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.assertEqual(data.get("overall_status"), "PASS")
            self.assertEqual(data.get("total_test_cases"), 6)
            self.assertEqual(data.get("passed_test_cases"), 6)
            self.assertEqual(data.get("failed_test_cases"), 0)
            self.assertIn("test_results", data)
            self.assertIn("SYS-INT-001", data["test_results"])
            self.assertIn("SYS-INT-006", data["test_results"])
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    def test_standard_library_compliance(self):
        """Verifies run_system_integration_tests.py uses only Python 3 standard library."""
        runner_file = os.path.join(TOOLS_DIR, "run_system_integration_tests.py")
        with open(runner_file, "r", encoding="utf-8") as f:
            tree = ast.parse(f.read(), filename=runner_file)

        allowed_stdlib = {
            "argparse", "datetime", "json", "math", "os", "subprocess",
            "sys", "time", "typing", "ble_hr_simulator", "ble_power_simulator",
            "ble_radar_simulator", "fake_gps", "replay_workout"
        }

        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    root = alias.name.split(".")[0]
                    self.assertIn(root, allowed_stdlib, f"Non-stdlib import detected: {alias.name}")
            elif isinstance(node, ast.ImportFrom):
                if node.module:
                    root = node.module.split(".")[0]
                    self.assertIn(root, allowed_stdlib, f"Non-stdlib import detected: {node.module}")


if __name__ == "__main__":
    unittest.main()
