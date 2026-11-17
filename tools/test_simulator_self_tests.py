#!/usr/bin/env python3
"""
tools/test_simulator_self_tests.py
==================================
Unit tests for tools/run_simulator_self_tests.py.
Verifies:
- Execution of all 5 developer simulator self-test cases (SIM-HR/PWR/RDR/GPS/RPL-001).
- Targeted filtering via target_test argument.
- JSON report schema explicitly marks executes_android_app=False and release_gate=False.
- CLI --output flag execution succeeds cleanly with exit code 0.
- Pure Python 3 standard library + repository simulator module compliance.

Requirement: REQ-PRO-068, REQ-PRO-112
Test Spec:   TST-PRO-068.3, TST-PRO-068.4, TST-PRO-112.1
"""

import ast
import json
import os
import subprocess
import sys
import tempfile
import unittest

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

from run_simulator_self_tests import SimulatorSelfTestRunner, get_git_commit


class TestSimulatorSelfTestRunner(unittest.TestCase):

    def setUp(self):
        self.runner = SimulatorSelfTestRunner(verbose=False)

    def test_sim_hr_self_test(self):
        passed, msg, details = self.runner.run_sim_hr_self_test()
        self.assertTrue(passed, f"SIM-HR-001 failed: {msg}")
        self.assertEqual(details.get("mean_bpm"), 140)
        self.assertLessEqual(details["phase_conservation_drift_ms"], 5.0)
        self.assertNotIn("exponential_backoff_schedule_ms", details)

    def test_sim_power_self_test(self):
        passed, msg, details = self.runner.run_sim_power_self_test()
        self.assertTrue(passed, f"SIM-PWR-001 failed: {msg}")
        self.assertEqual(details.get("power_watts_default"), 200)
        self.assertEqual(details.get("power_watts_updated"), 275)
        self.assertIn("asymmetry_tested", details)

    def test_sim_radar_self_test(self):
        passed, msg, details = self.runner.run_sim_radar_self_test()
        self.assertTrue(passed, f"SIM-RDR-001 failed: {msg}")
        self.assertGreaterEqual(details.get("initial_target_count", 0), 1)
        self.assertTrue(details.get("starvation_suppression_verified"))
        self.assertIn("light_command_opcodes", details)

    def test_sim_gps_self_test(self):
        passed, msg, details = self.runner.run_sim_gps_self_test()
        self.assertTrue(passed, f"SIM-GPS-001 failed: {msg}")
        self.assertGreater(details.get("total_distance_m", 0.0), 1000.0)
        self.assertAlmostEqual(details.get("off_route_offset_m", 0.0), 75.0, delta=2.0)

    def test_sim_replay_self_test(self):
        passed, msg, details = self.runner.run_sim_replay_self_test()
        self.assertTrue(passed, f"SIM-RPL-001 failed: {msg}")
        self.assertEqual(details.get("decoded_watts"), 260)
        self.assertIn("auto_hrv_110bpm", details)
        self.assertIn("auto_hrv_175bpm", details)

    def test_execute_all_overall_pass(self):
        success = self.runner.execute_all()
        self.assertTrue(success)
        self.assertEqual(len(self.runner.results), 5)
        for test_id, res in self.runner.results.items():
            self.assertTrue(res["passed"], f"Test {test_id} failed: {res['message']}")
            self.assertEqual(res["status"], "PASS")

    def test_execute_target_filter(self):
        success = self.runner.execute_all(target_test="SIM-HR-001")
        self.assertTrue(success)
        self.assertEqual(len(self.runner.results), 1)
        self.assertIn("SIM-HR-001", self.runner.results)

    def test_export_json_schema_and_non_gating_flags(self):
        self.runner.execute_all()
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tmp:
            tmp_path = tmp.name

        try:
            self.runner.export_json(tmp_path)
            self.assertTrue(os.path.exists(tmp_path))
            with open(tmp_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.assertEqual(data.get("classification"), "developer-simulator-self-tests")
            self.assertIs(data.get("executes_android_app"), False)
            self.assertIs(data.get("release_gate"), False)
            self.assertEqual(data.get("overall_status"), "PASS")
            self.assertEqual(data.get("total_test_cases"), 5)
            self.assertEqual(data.get("passed_test_cases"), 5)
            self.assertEqual(data.get("failed_test_cases"), 0)
            self.assertIn("SIM-HR-001", data["test_results"])
            self.assertIn("SIM-RPL-001", data["test_results"])
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    def test_cli_output_flag_execution(self):
        runner_file = os.path.join(TOOLS_DIR, "run_simulator_self_tests.py")
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tmp:
            tmp_path = tmp.name
        try:
            proc = subprocess.run(
                [sys.executable, runner_file, "--headless", "--output", tmp_path],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(proc.returncode, 0, proc.stderr)
            with open(tmp_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.assertIs(data.get("release_gate"), False)
            self.assertEqual(data.get("passed_test_cases"), 5)
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    def test_standard_library_compliance(self):
        """Verifies run_simulator_self_tests.py uses only Python 3 stdlib and repo simulators."""
        runner_file = os.path.join(TOOLS_DIR, "run_simulator_self_tests.py")
        with open(runner_file, "r", encoding="utf-8") as f:
            tree = ast.parse(f.read(), filename=runner_file)

        allowed_modules = {
            "argparse", "datetime", "json", "os", "subprocess",
            "sys", "time", "typing", "ble_hr_simulator", "ble_power_simulator",
            "ble_radar_simulator", "fake_gps", "replay_workout"
        }

        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    root = alias.name.split(".")[0]
                    self.assertIn(root, allowed_modules, f"Non-stdlib import detected: {alias.name}")
            elif isinstance(node, ast.ImportFrom):
                if node.module:
                    root = node.module.split(".")[0]
                    self.assertIn(root, allowed_modules, f"Non-stdlib import detected: {node.module}")


if __name__ == "__main__":
    unittest.main()
