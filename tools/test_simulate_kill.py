#!/usr/bin/env python3
"""
tools/test_simulate_kill.py - Unit tests for Process Kill & Exit Reason Simulator
================================================================================
Verifies parsing, argument handling, mock ADB interactions, and state evaluations
for tools/simulate_kill.py without requiring a connected physical device.
"""

import unittest
from unittest.mock import patch, MagicMock
import subprocess

import os
import sys

# Ensure repository root and tools directory are in sys.path
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if REPO_ROOT not in sys.path:
    sys.path.insert(0, REPO_ROOT)

from tools.simulate_kill import (
    KillSimulatorController,
    ExitInfoRecord,
    AppProcessStatus,
    print_status_banner
)


class TestKillSimulator(unittest.TestCase):

    def setUp(self):
        self.mock_device = "TEST_DEVICE_123"
        self.mock_pkg = "com.atrainingtracker.debug"

    @patch("subprocess.run")
    def test_detect_device_success(self, mock_run):
        mock_run.return_value = subprocess.CompletedProcess(
            args=["adb", "devices"],
            returncode=0,
            stdout="List of devices attached\nTEST_DEVICE_123\tdevice\n\n",
            stderr=""
        )
        ctrl = KillSimulatorController(device_serial=None, package_name=self.mock_pkg)
        self.assertEqual(ctrl.device_serial, "TEST_DEVICE_123")

    @patch("subprocess.run")
    def test_detect_device_failure(self, mock_run):
        mock_run.return_value = subprocess.CompletedProcess(
            args=["adb", "devices"],
            returncode=0,
            stdout="List of devices attached\n\n",
            stderr=""
        )
        with self.assertRaises(RuntimeError):
            KillSimulatorController(device_serial=None, package_name=self.mock_pkg)

    @patch("subprocess.run")
    def test_get_running_pids(self, mock_run):
        mock_run.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="12345 12346\n",
            stderr=""
        )
        ctrl = KillSimulatorController(device_serial=self.mock_device, package_name=self.mock_pkg)
        pids = ctrl.get_running_pids()
        self.assertEqual(pids, [12345, 12346])

    @patch("subprocess.run")
    def test_is_battery_whitelisted(self, mock_run):
        mock_run.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="system,com.android.providers,1000\nuser,com.atrainingtracker.debug,10450\n",
            stderr=""
        )
        ctrl = KillSimulatorController(device_serial=self.mock_device, package_name=self.mock_pkg)
        self.assertTrue(ctrl.is_battery_whitelisted())

        # Test false condition
        mock_run.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="system,com.android.providers,1000\n",
            stderr=""
        )
        self.assertFalse(ctrl.is_battery_whitelisted())

    @patch("subprocess.run")
    def test_parse_exit_info(self, mock_run):
        sample_output = """
ACTIVITY MANAGER PROCESS EXIT INFO (dumpsys activity exit-info)
Last Timestamp of Persistence Into Persistent Storage: 2026-10-09 18:48:24.107
  package: com.atrainingtracker.debug
    Historical Process Exit for uid=10450
        ApplicationExitInfo #0:
          timestamp=2026-10-09 17:59:22.518 pid=23671 realUid=10450 packageUid=10450 definingUid=10450 user=0
          process=com.atrainingtracker.debug reason=16 (PACKAGE UPDATED) subreason=0 (UNKNOWN) status=0
          importance=100 pss=0,00 rss=557MB state=empty trace=null
          description=stop com.atrainingtracker.debug due to killDueToPackageUpdate
        ApplicationExitInfo #1:
          timestamp=2026-10-09 17:45:47.983 pid=17580 realUid=10450 packageUid=10450 definingUid=10450 user=0
          process=com.atrainingtracker.debug reason=10 (USER REQUESTED) subreason=22 (REMOVE TASK) status=0
        """
        mock_run.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout=sample_output,
            stderr=""
        )
        ctrl = KillSimulatorController(device_serial=self.mock_device, package_name=self.mock_pkg)
        exit_info = ctrl.get_latest_exit_info()
        self.assertIsNotNone(exit_info)
        self.assertEqual(exit_info.pid, 23671)
        self.assertEqual(exit_info.reason_code, 16)
        self.assertEqual(exit_info.reason_name, "PACKAGE UPDATED")
        self.assertIn("killDueToPackageUpdate", exit_info.description)

    @patch("subprocess.run")
    def test_read_preferences(self, mock_run):
        xml_content = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="pref_battery_kill_count" value="2" />
    <long name="pref_last_evaluated_exit_timestamp" value="1728490000000" />
</map>
"""
        mock_run.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout=xml_content,
            stderr=""
        )
        ctrl = KillSimulatorController(device_serial=self.mock_device, package_name=self.mock_pkg)
        prefs = ctrl.read_preferences()
        self.assertEqual(prefs["pref_battery_kill_count"], 2)
        self.assertEqual(prefs["pref_last_evaluated_exit_timestamp"], 1728490000000)

    @patch("subprocess.run")
    def test_permission_granted_check(self, mock_run):
        mock_run.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="android.permission.ACCESS_FINE_LOCATION: granted=true, flags=[USER_SET]\n",
            stderr=""
        )
        ctrl = KillSimulatorController(device_serial=self.mock_device, package_name=self.mock_pkg)
        self.assertTrue(ctrl.is_permission_granted("android.permission.ACCESS_FINE_LOCATION"))

    def test_print_status_banner_does_not_crash(self):
        status = AppProcessStatus(
            package_name=self.mock_pkg,
            device_serial=self.mock_device,
            pids=[1234],
            is_whitelisted=False,
            location_granted=True,
            notification_granted=True,
            battery_kill_count=2,
            last_evaluated_timestamp=1728490000000,
            latest_exit=ExitInfoRecord(
                index=0,
                timestamp_str="2026-10-09 18:00:00",
                pid=1234,
                reason_code=10,
                reason_name="USER REQUESTED",
                subreason="22 (REMOVE TASK)",
                description="Test description"
            )
        )
        # Verify printing produces no exceptions
        try:
            print_status_banner(status)
        except Exception as e:
            self.fail(f"print_status_banner raised exception: {e}")

    def test_web_gui_html_structure(self):
        from tools.simulate_kill import WEB_GUI_HTML
        self.assertIn("Process Kill & Exit Simulator", WEB_GUI_HTML)
        self.assertIn("/api/status", WEB_GUI_HTML)
        self.assertIn("/api/action", WEB_GUI_HTML)
        self.assertIn("kill_battery", WEB_GUI_HTML)
        self.assertIn("kill_permission", WEB_GUI_HTML)


if __name__ == "__main__":
    unittest.main()
