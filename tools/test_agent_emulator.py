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
import tempfile
import unittest
from unittest.mock import MagicMock, patch

# Ensure tools directory is in sys.path
TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(TOOLS_DIR)
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

import agent_emulator
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

    def test_pixel10_debug_heap_hygiene_workflow_documented(self):
        """TST-STB-022.4: Periodic Pixel 10 debug_heap & debug_hprof triage/cleanup rules & skills."""
        for rel_path in (
            os.path.join(".agents", "rules", "aspice_governance.md"),
            os.path.join(".agents", "skills", "sprint-planner", "SKILL.md"),
        ):
            full_path = os.path.join(REPO_ROOT, rel_path)
            with open(full_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("files/debug_hprof/", content)
            self.assertIn("files/debug_heap/", content)
            self.assertIn("ATT-235", content)
            self.assertIn("run-as com.atrainingtracker.debug rm", content)

    def test_stage6_warm_worktree_non_ui_skip_and_telemetry_documented(self):
        """ATT-9581: Governance rules and Stage 6 skill document warm verify, non-UI skip, and lock telemetry."""
        for rel_path in (
            os.path.join(".agents", "rules", "aspice_governance.md"),
            os.path.join(".agents", "skills", "stage6-final-review", "SKILL.md"),
        ):
            full_path = os.path.join(REPO_ROOT, rel_path)
            with open(full_path, "r", encoding="utf-8") as f:
                content = f.read()
            self.assertIn("--verify-warm", content)
            self.assertIn("att_wt_verify_warm", content)
            self.assertIn("check-install-required", content)
            self.assertIn("att_emulator_lock_telemetry.jsonl", content)


class TestStdlibCompliance(unittest.TestCase):
    """TST-TST-004.6: Python Standard Library Compliance."""

    def test_agent_emulator_imports_only_standard_library(self):
        script_path = os.path.join(TOOLS_DIR, "agent_emulator.py")
        with open(script_path, "r", encoding="utf-8") as f:
            tree = ast.parse(f.read(), filename="agent_emulator.py")

        allowed_stdlib = {
            "argparse",
            "contextlib",
            "fcntl",
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


class TestEmulatorSessionLock(unittest.TestCase):
    """Tests for exclusive POSIX /tmp/att_emulator.lock session serialization."""

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp(prefix="test_emu_lock_")
        self.lock_file = os.path.join(self.tmp_dir, "emu.lock")

    def tearDown(self):
        import shutil
        shutil.rmtree(self.tmp_dir, ignore_errors=True)

    def test_concurrent_processes_are_strictly_serialized(self):
        """Two concurrent 'agent_emulator.py exec' processes must never overlap."""
        import subprocess
        import time

        events_file = os.path.join(self.tmp_dir, "events.log")
        script = os.path.join(os.path.dirname(__file__), "agent_emulator.py")
        worker_py = (
            "import sys, time; "
            f"f=open({events_file!r}, 'a'); "
            "wid=sys.argv[1]; "
            "f.write(f'{wid}:start:{time.monotonic():.4f}\\n'); f.flush(); "
            "time.sleep(0.35); "
            "f.write(f'{wid}:end:{time.monotonic():.4f}\\n'); f.flush(); f.close()"
        )
        env = os.environ.copy()
        env.pop("ATT_EMULATOR_LOCK_HELD", None)
        env.pop("ATT_SPRINT_LOCK_HELD", None)

        p1 = subprocess.Popen(
            [sys.executable, script, "exec", "--lock-file", self.lock_file, "--timeout", "10", "--", sys.executable, "-c", worker_py, "W1"],
            env=env,
        )
        time.sleep(0.05)
        p2 = subprocess.Popen(
            [sys.executable, script, "exec", "--lock-file", self.lock_file, "--timeout", "10", "--", sys.executable, "-c", worker_py, "W2"],
            env=env,
        )
        rc1 = p1.wait(timeout=15)
        rc2 = p2.wait(timeout=15)
        self.assertEqual(rc1, 0)
        self.assertEqual(rc2, 0)

        with open(events_file, "r", encoding="utf-8") as f:
            lines = [line.strip().split(":") for line in f if line.strip()]
        self.assertEqual(len(lines), 4)
        # Strict non-overlapping execution: first worker must finish before second starts
        first_worker = lines[0][0]
        second_worker = lines[2][0]
        self.assertNotEqual(first_worker, second_worker)
        self.assertEqual(lines[1][0], first_worker)
        self.assertEqual(lines[1][1], "end")
        self.assertEqual(lines[2][1], "start")
        self.assertEqual(lines[3][0], second_worker)
        self.assertEqual(lines[3][1], "end")

    def test_lock_timeout_raises_when_busy(self):
        """Acquiring an already-held lock beyond timeout raises TimeoutError."""
        import fcntl

        fd = os.open(self.lock_file, os.O_CREAT | os.O_RDWR, 0o666)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaises(TimeoutError):
                with agent_emulator.emulator_session_lock(
                    lock_path=self.lock_file,
                    timeout_sec=0.15,
                    poll_interval_sec=0.05,
                ):
                    pass
        finally:
            fcntl.flock(fd, fcntl.LOCK_UN)
            os.close(fd)

    def test_lock_order_inversion_is_rejected(self):
        """Calling emulator_session_lock while ATT_SPRINT_LOCK_HELD=1 must fail fast."""
        with patch.dict(os.environ, {"ATT_SPRINT_LOCK_HELD": "1"}, clear=False):
            os.environ.pop("ATT_EMULATOR_LOCK_HELD", None)
            with self.assertRaises(RuntimeError) as ctx:
                with agent_emulator.emulator_session_lock(lock_path=agent_emulator.DEFAULT_EMULATOR_LOCK):
                    pass
            self.assertIn("Lock-order inversion", str(ctx.exception))

    def test_reentrant_default_lock_when_env_set(self):
        """Nested calls when ATT_EMULATOR_LOCK_HELD=1 must not self-deadlock."""
        with patch.dict(os.environ, {"ATT_EMULATOR_LOCK_HELD": "1"}, clear=False):
            entered = False
            with agent_emulator.emulator_session_lock(lock_path=agent_emulator.DEFAULT_EMULATOR_LOCK, timeout_sec=0.1):
                entered = True
            self.assertTrue(entered)

    def test_exec_locked_session_propagates_exit_code_and_releases_lock(self):
        """Non-zero child exit code is propagated and lock is released afterward."""
        rc = agent_emulator.exec_locked_session(
            cmd=[sys.executable, "-c", "import sys; sys.exit(7)"],
            ensure=False,
            lock_path=self.lock_file,
            lock_timeout_sec=5.0,
        )
        self.assertEqual(rc, 7)
        # Lock must be free immediately after child failure
        with agent_emulator.emulator_session_lock(lock_path=self.lock_file, timeout_sec=0.5):
            pass


class TestStage6InstallClassifier(unittest.TestCase):
    """ATT-9581: Non-UI diff classification & redundant installDebug skip."""

    def test_non_ui_diffs_do_not_require_install_debug(self):
        non_ui_cases = [
            ["tools/agent_emulator.py", "tools/test_agent_emulator.py", ".agents/skills/stage6-final-review/SKILL.md"],
            ["app/src/test/java/com/atrainingtracker/trainingtracker/domain/ZoneCalculatorTest.kt"],
            [
                "app/src/main/java/com/atrainingtracker/trainingtracker/domain/ZoneCalculator.kt",
                "app/src/main/java/com/atrainingtracker/trainingtracker/physiology/HrZoneMath.kt",
            ],
            ["docs/architecture.md", "README.md"],
        ]
        for files in non_ui_cases:
            res = agent_emulator.classify_stage6_install_need(files)
            self.assertFalse(res["install_required"], f"Expected skip for {files}, got {res}")
            self.assertEqual(res["matched_files"], [])

    def test_ui_and_runtime_diffs_require_install_debug(self):
        ui_cases = [
            ["app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/RadarSettingsScreen.kt"],
            ["app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt"],
            ["app/src/main/java/com/atrainingtracker/trainingtracker/fragments/TrackingFragment.java"],
            ["app/src/main/AndroidManifest.xml"],
            ["app/src/main/res/values/strings.xml"],
            ["app/src/main/java/com/atrainingtracker/banalservice/BANALService.java"],
            ["app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java"],
            ["app/build.gradle"],
        ]
        for files in ui_cases:
            res = agent_emulator.classify_stage6_install_need(files)
            self.assertTrue(res["install_required"], f"Expected install_required=True for {files}, got {res}")
            self.assertGreaterEqual(len(res["matched_files"]), 1)

    def test_composable_symbol_in_util_package_triggers_install_required(self):
        with tempfile.TemporaryDirectory() as tmp:
            util_dir = os.path.join(tmp, "app", "src", "main", "java", "com", "atrainingtracker", "util")
            os.makedirs(util_dir, exist_ok=True)
            rel = "app/src/main/java/com/atrainingtracker/util/PillHelper.kt"
            with open(os.path.join(tmp, rel), "w", encoding="utf-8") as f:
                f.write("@Composable\nfun PillHelper() {}\n")
            res = agent_emulator.classify_stage6_install_need([rel], repo_root=tmp)
            self.assertTrue(res["install_required"])
            self.assertEqual(res["matched_files"][0]["reason"], "android_runtime_or_compose_symbol")

    def test_cli_check_install_required_and_exec_skip_if_non_ui(self):
        import json

        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(["git", "init", "-b", "sprint/test", tmp], check=True, capture_output=True)
            subprocess.run(["git", "config", "user.name", "Tester"], cwd=tmp, check=True)
            subprocess.run(["git", "config", "user.email", "tester@example.com"], cwd=tmp, check=True)
            os.makedirs(os.path.join(tmp, "tools"), exist_ok=True)
            with open(os.path.join(tmp, "tools", "helper.py"), "w") as f:
                f.write("x = 1\n")
            subprocess.run(["git", "add", "."], cwd=tmp, check=True)
            subprocess.run(["git", "commit", "-m", "base"], cwd=tmp, check=True, capture_output=True)

            # Non-UI branch
            subprocess.run(["git", "checkout", "-b", "feature/non-ui"], cwd=tmp, check=True, capture_output=True)
            with open(os.path.join(tmp, "tools", "helper.py"), "w") as f:
                f.write("x = 2\n")
            subprocess.run(["git", "add", "."], cwd=tmp, check=True)
            subprocess.run(["git", "commit", "-m", "non-ui"], cwd=tmp, check=True, capture_output=True)

            script = os.path.join(TOOLS_DIR, "agent_emulator.py")
            chk = subprocess.run(
                [
                    sys.executable,
                    script,
                    "check-install-required",
                    "--base",
                    "sprint/test",
                    "--head",
                    "feature/non-ui",
                    "--repo-root",
                    tmp,
                    "--json",
                ],
                capture_output=True,
                text=True,
                check=True,
            )
            payload = json.loads(chk.stdout)
            self.assertFalse(payload["install_required"])

            # exec --skip-if-non-ui skips running command and never touches lock file
            sentinel = os.path.join(tmp, "ran.txt")
            lock_file = os.path.join(tmp, "emu.lock")
            rc = agent_emulator.exec_locked_session(
                cmd=[sys.executable, "-c", f"open({sentinel!r}, 'w').write('ran')"],
                ensure=False,
                lock_path=lock_file,
                skip_if_non_ui=True,
                base_ref="sprint/test",
                head_ref="feature/non-ui",
                repo_root=tmp,
            )
            self.assertEqual(rc, 0)
            self.assertFalse(os.path.exists(sentinel))
            self.assertFalse(os.path.exists(lock_file))

            # UI branch executes normally
            subprocess.run(["git", "checkout", "-b", "feature/ui", "sprint/test"], cwd=tmp, check=True, capture_output=True)
            ui_dir = os.path.join(tmp, "app", "src", "main", "res", "values")
            os.makedirs(ui_dir, exist_ok=True)
            with open(os.path.join(ui_dir, "strings.xml"), "w") as f:
                f.write("<resources/>\n")
            subprocess.run(["git", "add", "."], cwd=tmp, check=True)
            subprocess.run(["git", "commit", "-m", "ui"], cwd=tmp, check=True, capture_output=True)

            rc_ui = agent_emulator.exec_locked_session(
                cmd=[sys.executable, "-c", f"open({sentinel!r}, 'w').write('ran')"],
                ensure=False,
                lock_path=lock_file,
                skip_if_non_ui=True,
                base_ref="sprint/test",
                head_ref="feature/ui",
                repo_root=tmp,
            )
            self.assertEqual(rc_ui, 0)
            self.assertTrue(os.path.isfile(sentinel))


class TestEmulatorLockTelemetry(unittest.TestCase):
    """ATT-9581: Lock wait duration stderr logging and JSONL telemetry for Option 2 Gate."""

    def test_contended_lock_logs_wait_to_stderr_and_records_jsonl_telemetry(self):
        import fcntl
        import json
        import time

        with tempfile.TemporaryDirectory() as tmp:
            lock_file = os.path.join(tmp, "emu.lock")
            tel_file = os.path.join(tmp, "telemetry.jsonl")
            script = os.path.join(TOOLS_DIR, "agent_emulator.py")

            # Hold lock briefly in child, then launch second child that must wait
            holder = subprocess.Popen(
                [
                    sys.executable,
                    script,
                    "exec",
                    "--lock-file",
                    lock_file,
                    "--telemetry-file",
                    tel_file,
                    "--timeout",
                    "10",
                    "--",
                    sys.executable,
                    "-c",
                    "import time; time.sleep(0.35)",
                ],
                stderr=subprocess.PIPE,
                text=True,
            )
            time.sleep(0.08)
            waiter = subprocess.Popen(
                [
                    sys.executable,
                    script,
                    "exec",
                    "--lock-file",
                    lock_file,
                    "--telemetry-file",
                    tel_file,
                    "--timeout",
                    "10",
                    "--",
                    sys.executable,
                    "-c",
                    "pass",
                ],
                stderr=subprocess.PIPE,
                text=True,
            )
            _, holder_err = holder.communicate(timeout=15)
            _, waiter_err = waiter.communicate(timeout=15)
            self.assertEqual(holder.returncode, 0)
            self.assertEqual(waiter.returncode, 0)

            self.assertIn("[emulator-lock] Acquired", waiter_err)
            self.assertIn("contended=true", waiter_err)

            with open(tel_file, "r", encoding="utf-8") as f:
                entries = [json.loads(line) for line in f if line.strip()]
            self.assertEqual(len(entries), 2)
            self.assertTrue(any(e["contended"] and e["wait_sec"] >= 0.1 and not e["timed_out"] for e in entries))

            # Verify timeout also appends timed_out=True telemetry
            fd = os.open(lock_file, os.O_CREAT | os.O_RDWR, 0o666)
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                with self.assertRaises(TimeoutError):
                    with agent_emulator.emulator_session_lock(
                        lock_path=lock_file,
                        timeout_sec=0.12,
                        poll_interval_sec=0.04,
                        telemetry_path=tel_file,
                    ):
                        pass
            finally:
                fcntl.flock(fd, fcntl.LOCK_UN)
                os.close(fd)

            stats = subprocess.run(
                [sys.executable, script, "lock-stats", "--telemetry-file", tel_file, "--json"],
                capture_output=True,
                text=True,
                check=True,
            )
            summary = json.loads(stats.stdout)
            self.assertEqual(summary["total_sessions"], 3)
            self.assertGreaterEqual(summary["contended_sessions"], 2)
            self.assertEqual(summary["timeouts"], 1)
            self.assertGreater(summary["max_wait_sec"], 0.1)


if __name__ == "__main__":
    unittest.main()
