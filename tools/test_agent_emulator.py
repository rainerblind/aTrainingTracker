#!/usr/bin/env python3
"""
test_agent_emulator.py - Unit Test Suite for Android Emulator Lifecycle & Guardrail (ATT-6506 / TST-TST-004)

Validates:
- TST-TST-004.1: ADB device table parsing and multi-device prioritization
- TST-TST-004.2: Physical-phone guardrail and agent mode rejection
- TST-TST-004.3: Emulator lifecycle auto-boot and app configuration command generation
- TST-TST-004.4: Database seeding and extraction pipeline commands
- TST-TST-004.5: Governance rules and agent skills Two-Tier policy parity
- TST-TST-004.6: Python standard library compliance (TST-PRO-005.6)
"""

import ast
import os
import subprocess
import sys
import unittest
from unittest.mock import MagicMock, patch

# Ensure tools directory is in sys.path
TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(TOOLS_DIR)
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

from agent_emulator import (
    AdbDevice,
    CORE_DATABASES,
    CORE_PERMISSIONS,
    DEFAULT_AVD,
    DEFAULT_PACKAGE,
    TARGET_SERIAL,
    configure_app,
    ensure_emulator,
    is_boot_completed,
    parse_adb_devices,
    resolve_target_device,
    seed_db,
    sync_from_physical,
    tune_emulator_settings,
)


SAMPLE_ADB_OUTPUT = """List of devices attached
66020DLCR002FL         device usb:1-5.1 product:frankel model:Pixel_10 device:frankel transport_id:7
emulator-5554          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64x transport_id:8
emulator-5556          offline transport_id:9
"""

SAMPLE_PHYSICAL_ONLY_OUTPUT = """List of devices attached
66020DLCR002FL         device usb:1-5.1 product:frankel model:Pixel_10 device:frankel transport_id:7
"""

SAMPLE_EMULATOR_ONLY_OUTPUT = """List of devices attached
emulator-5554          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64x transport_id:8
"""


class TestAdbDeviceParsing(unittest.TestCase):
    """TST-TST-004.1: ADB Device Table Parsing & Multi-Device Prioritization."""

    def test_parse_adb_devices_partitions_devices_correctly(self):
        devices = parse_adb_devices(SAMPLE_ADB_OUTPUT)
        self.assertEqual(len(devices), 3)

        dev0 = devices[0]
        self.assertEqual(dev0.serial, "66020DLCR002FL")
        self.assertEqual(dev0.state, "device")
        self.assertFalse(dev0.is_emulator)
        self.assertEqual(dev0.model, "Pixel_10")
        self.assertEqual(dev0.product, "frankel")

        dev1 = devices[1]
        self.assertEqual(dev1.serial, "emulator-5554")
        self.assertEqual(dev1.state, "device")
        self.assertTrue(dev1.is_emulator)
        self.assertEqual(dev1.model, "sdk_gphone64_x86_64")

        dev2 = devices[2]
        self.assertEqual(dev2.serial, "emulator-5556")
        self.assertEqual(dev2.state, "offline")
        self.assertTrue(dev2.is_emulator)

    @patch("agent_emulator.get_adb_devices")
    def test_multi_device_resolution_prioritizes_emulator_5554(self, mock_get_devices):
        mock_get_devices.return_value = parse_adb_devices(SAMPLE_ADB_OUTPUT)
        # When both physical phone and emulator are present, defaults to emulator-5554
        resolved = resolve_target_device(device_serial=None, agent_mode=False)
        self.assertEqual(resolved, TARGET_SERIAL)

    @patch("agent_emulator.get_adb_devices")
    def test_multi_device_resolution_in_agent_mode_prioritizes_emulator(self, mock_get_devices):
        mock_get_devices.return_value = parse_adb_devices(SAMPLE_ADB_OUTPUT)
        resolved = resolve_target_device(device_serial=None, agent_mode=True)
        self.assertEqual(resolved, TARGET_SERIAL)


class TestPhysicalGuardrail(unittest.TestCase):
    """TST-TST-004.2: Physical-Phone Protection & Agent Mode Rejection."""

    @patch("agent_emulator.get_adb_devices")
    def test_agent_mode_rejects_physical_device_when_no_emulator(self, mock_get_devices):
        mock_get_devices.return_value = parse_adb_devices(SAMPLE_PHYSICAL_ONLY_OUTPUT)
        with self.assertRaises(RuntimeError) as ctx:
            resolve_target_device(device_serial=None, agent_mode=True)
        self.assertIn("Physical-Phone Guardrail Violation", str(ctx.exception))
        self.assertIn("no Android emulator is running", str(ctx.exception))

    @patch("agent_emulator.get_adb_devices")
    def test_agent_mode_rejects_explicit_physical_serial(self, mock_get_devices):
        mock_get_devices.return_value = parse_adb_devices(SAMPLE_ADB_OUTPUT)
        with self.assertRaises(RuntimeError) as ctx:
            resolve_target_device(device_serial="66020DLCR002FL", agent_mode=True)
        self.assertIn("Physical-Phone Guardrail Violation", str(ctx.exception))
        self.assertIn("is a physical USB device", str(ctx.exception))

    @patch("agent_emulator.get_adb_devices")
    def test_agent_mode_allows_explicit_emulator_serial(self, mock_get_devices):
        mock_get_devices.return_value = parse_adb_devices(SAMPLE_ADB_OUTPUT)
        target = resolve_target_device(device_serial="emulator-5554", agent_mode=True)
        self.assertEqual(target, "emulator-5554")

    @patch("agent_emulator.get_adb_devices")
    def test_human_mode_allows_physical_device_when_no_emulator(self, mock_get_devices):
        mock_get_devices.return_value = parse_adb_devices(SAMPLE_PHYSICAL_ONLY_OUTPUT)
        target = resolve_target_device(device_serial=None, agent_mode=False)
        self.assertEqual(target, "66020DLCR002FL")

    @patch.dict(os.environ, {"ATT_AGENT_MODE": "1"}, clear=False)
    @patch("agent_emulator.get_adb_devices")
    def test_att_agent_mode_env_var_activates_guardrail(self, mock_get_devices):
        mock_get_devices.return_value = parse_adb_devices(SAMPLE_PHYSICAL_ONLY_OUTPUT)
        with self.assertRaises(RuntimeError) as ctx:
            resolve_target_device(device_serial=None, agent_mode=None)
        self.assertIn("Physical-Phone Guardrail Violation", str(ctx.exception))


class TestLifecycleAndConfigCommands(unittest.TestCase):
    """TST-TST-004.3: Emulator Lifecycle & Configuration Command Generation."""

    @patch("subprocess.run")
    def test_is_boot_completed_checks_sys_boot_completed_property(self, mock_run):
        mock_run.return_value = MagicMock(stdout="1\n", returncode=0)
        self.assertTrue(is_boot_completed("emulator-5554"))
        mock_run.assert_called_with(
            ["adb", "-s", "emulator-5554", "shell", "getprop", "sys.boot_completed"],
            capture_output=True,
            text=True,
            timeout=5,
        )

        mock_run.return_value = MagicMock(stdout="0\n", returncode=0)
        self.assertFalse(is_boot_completed("emulator-5554"))

    @patch("subprocess.run")
    def test_tune_emulator_settings_disables_animations_and_unlocks(self, mock_run):
        tune_emulator_settings("emulator-5554")
        calls = [c[0][0] for c in mock_run.call_args_list]

        # Keyguard unlock
        self.assertIn(["adb", "-s", "emulator-5554", "shell", "input", "keyevent", "82"], calls)
        # Animation scales 0.0
        self.assertIn(["adb", "-s", "emulator-5554", "shell", "settings", "put", "global", "window_animation_scale", "0.0"], calls)
        self.assertIn(["adb", "-s", "emulator-5554", "shell", "settings", "put", "global", "transition_animation_scale", "0.0"], calls)
        self.assertIn(["adb", "-s", "emulator-5554", "shell", "settings", "put", "global", "animator_duration_scale", "0.0"], calls)

    @patch("subprocess.run")
    def test_configure_app_grants_permissions_and_mock_location(self, mock_run):
        configure_app("emulator-5554", package="com.atrainingtracker.debug")
        calls = [c[0][0] for c in mock_run.call_args_list]

        for perm in CORE_PERMISSIONS:
            self.assertIn(["adb", "-s", "emulator-5554", "shell", "pm", "grant", "com.atrainingtracker.debug", perm], calls)

        # Mock location appops
        self.assertIn(["adb", "-s", "emulator-5554", "shell", "appops", "set", "com.atrainingtracker.debug", "android:mock_location", "allow"], calls)
        self.assertIn(["adb", "-s", "emulator-5554", "shell", "appops", "set", "2000", "android:mock_location", "allow"], calls)

    @patch("agent_emulator.is_boot_completed", return_value=True)
    @patch("agent_emulator.tune_emulator_settings")
    @patch("agent_emulator.get_adb_devices")
    def test_ensure_emulator_returns_running_instance(self, mock_devices, mock_tune, mock_boot):
        mock_devices.return_value = parse_adb_devices(SAMPLE_EMULATOR_ONLY_OUTPUT)
        serial = ensure_emulator(target_serial="emulator-5554")
        self.assertEqual(serial, "emulator-5554")
        mock_tune.assert_called_once_with("emulator-5554", "adb")


class TestDatabaseSeeding(unittest.TestCase):
    """TST-TST-004.4: Database Seeding & Extraction Pipeline."""

    @patch("os.path.isdir", return_value=True)
    @patch("os.path.isfile", return_value=True)
    @patch("os.path.getsize", return_value=1024)
    @patch("subprocess.run")
    def test_seed_db_pushes_via_tmp_and_run_as(self, mock_run, mock_size, mock_isfile, mock_isdir):
        mock_run.return_value = MagicMock(returncode=0)
        seeded = seed_db(target_serial="emulator-5554", golden_dir="/fake/golden", package="com.atrainingtracker.debug")
        self.assertEqual(seeded, CORE_DATABASES)

        calls = [c[0][0] for c in mock_run.call_args_list]
        # Verify tmp push call
        self.assertTrue(any(c[:5] == ["adb", "-s", "emulator-5554", "push", "/fake/golden/WorkoutSummaries.db"] for c in calls))
        # Verify run-as install call
        self.assertTrue(any("run-as com.atrainingtracker.debug cp /data/local/tmp/WorkoutSummaries.db" in str(c) for c in calls))

    @patch("os.makedirs")
    @patch("agent_emulator.get_adb_devices")
    @patch("subprocess.run")
    def test_sync_from_physical_streams_via_run_as_cat(self, mock_run, mock_devices, mock_makedirs):
        mock_devices.return_value = parse_adb_devices(SAMPLE_PHYSICAL_ONLY_OUTPUT)
        mock_run.return_value = MagicMock(returncode=0, stdout=b"SQLITE_DATA_BINARY")

        with patch("builtins.open", unittest.mock.mock_open()) as mock_file:
            synced = sync_from_physical(golden_dir="/fake/golden", package="com.atrainingtracker.debug")
            self.assertEqual(synced, CORE_DATABASES)
            mock_run.assert_called()
            # Verify exec-out run-as cat command
            last_cmd = mock_run.call_args_list[0][0][0]
            self.assertEqual(last_cmd[:6], ["adb", "-s", "66020DLCR002FL", "exec-out", "run-as", "com.atrainingtracker.debug"])


class TestGovernanceParity(unittest.TestCase):
    """TST-TST-004.5: Governance & Skills Two-Tier Policy Parity."""

    def test_aspice_governance_enforces_two_tier_policy(self):
        gov_path = os.path.join(REPO_ROOT, ".agents", "rules", "aspice_governance.md")
        with open(gov_path, "r", encoding="utf-8") as f:
            content = f.read()

        self.assertIn("Two-Tier Device Policy", content)
        self.assertIn("emulator-5554", content)
        self.assertIn("agent_emulator.py ensure", content)
        self.assertIn("Pixel 10", content)
        self.assertIn("Ceremony 2", content)
        self.assertIn("REQ-TST-004", content)

    def test_stage5_verification_skill_enforces_emulator_for_agents(self):
        skill_path = os.path.join(REPO_ROOT, ".agents", "skills", "stage5-verification", "SKILL.md")
        with open(skill_path, "r", encoding="utf-8") as f:
            content = f.read()

        self.assertIn("Two-Tier Device Policy", content)
        self.assertIn("emulator-5554", content)
        self.assertIn("agent_emulator.py ensure", content)
        self.assertIn("ANDROID_SERIAL=emulator-5554", content)
        self.assertIn("Ceremony 2", content)

    def test_sprint_planner_skill_enforces_physical_phone_for_ceremony2(self):
        skill_path = os.path.join(REPO_ROOT, ".agents", "skills", "sprint-planner", "SKILL.md")
        with open(skill_path, "r", encoding="utf-8") as f:
            content = f.read()

        self.assertIn("Pixel 10", content)
        self.assertIn("Two-Tier Device Policy", content)
        self.assertIn("REQ-TST-004", content)

    def test_ui_designer_skill_enforces_emulator_policy(self):
        skill_path = os.path.join(REPO_ROOT, ".agents", "skills", "ui-designer", "SKILL.md")
        with open(skill_path, "r", encoding="utf-8") as f:
            content = f.read()

        self.assertIn("emulator-5554", content)
        self.assertIn("agent_emulator.py ensure", content)

    def test_project_protocol_documents_two_tier_policy(self):
        proto_path = os.path.join(REPO_ROOT, "docs", "project_protocol.md")
        with open(proto_path, "r", encoding="utf-8") as f:
            content = f.read()

        self.assertIn("Two-Tier Device Verification Policy", content)
        self.assertIn("emulator-5554", content)
        self.assertIn("Pixel 10", content)
        self.assertIn("REQ-TST-004", content)


class TestStdlibCompliance(unittest.TestCase):
    """TST-TST-004.6: Python Standard Library Compliance."""

    def test_agent_emulator_imports_only_standard_library(self):
        script_path = os.path.join(TOOLS_DIR, "agent_emulator.py")
        with open(script_path, "r", encoding="utf-8") as f:
            tree = ast.parse(f.read(), filename="agent_emulator.py")

        allowed_stdlib = {
            "argparse",
            "os",
            "re",
            "shutil",
            "subprocess",
            "sys",
            "time",
            "typing",
            "json",
        }

        imported_modules = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    imported_modules.add(alias.name.split(".")[0])
            elif isinstance(node, ast.ImportFrom):
                if node.module:
                    imported_modules.add(node.module.split(".")[0])

        for mod in imported_modules:
            self.assertIn(
                mod,
                allowed_stdlib,
                f"Disallowed third-party dependency '{mod}' found in agent_emulator.py (violates TST-PRO-005.6)",
            )


if __name__ == "__main__":
    unittest.main()
