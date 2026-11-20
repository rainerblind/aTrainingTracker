#!/usr/bin/env python3
"""
Offline unit tests for tools/run_multi_os_emulator_sweep.py (ATT-8222 / REQ-PRO-135 / TST-PRO-135).
All subprocess, ADB, emulator, logcat, and uiautomator calls are 100% mocked (Rule 5).
"""

import os
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import MagicMock

_tools_dir = os.path.dirname(os.path.abspath(__file__))
if _tools_dir not in sys.path:
    sys.path.insert(0, _tools_dir)

import agent_emulator
import run_multi_os_emulator_sweep as sweep


class TestMultiOsEmulatorSweep(unittest.TestCase):
    """Offline tests covering TST-PRO-135.1 and TST-PRO-135.2."""

    def test_tst_pro_135_1_serial_multi_os_and_9_locale_sweep_and_cleanup_on_failure(self):
        """TST-PRO-135.1: Serial Multi-OS AVDs + 9-language UI sweep on ref AVD + finally cleanup."""
        boot_order = []
        active_avds = []
        killed_serials = []
        locale_calls = []
        geo_fix_calls = []

        def fake_avds(_emu_bin):
            return ["Pixel_5_Android_12", "Pixel_6_Pro_Android_12", "Pixel_8_Android_15", "Pixel_7_Android_36"]

        def fake_devices(_adb_bin="adb"):
            # Return currently active single emulator if booted
            return [
                agent_emulator.AdbDevice("emulator-5554", "device", True, "Pixel", "sdk")
                for _ in active_avds
            ]

        class FakeProc:
            def terminate(self):
                pass

            def wait(self, timeout=None):
                return 0

            def kill(self):
                pass

        def fake_popen(cmd, **_kwargs):
            avd_idx = cmd.index("-avd") + 1
            avd_name = cmd[avd_idx]
            # Assert strictly 0 other emulators active before starting a new one
            self.assertEqual(len(active_avds), 0, f"Concurrent emulator detected while booting {avd_name}")
            self.assertIn("-no-snapshot-save", cmd)
            active_avds.append(avd_name)
            boot_order.append(avd_name)
            return FakeProc()

        def fake_runner(cmd, **_kwargs):
            res = MagicMock()
            res.returncode = 0
            res.stdout = ""
            if "kill" in cmd and "emu" in cmd:
                killed_serials.append(cmd[2])
                active_avds.clear()
            elif "set-app-locales" in cmd:
                locale_calls.append(cmd[-1])
            elif "geo" in cmd and "fix" in cmd:
                geo_fix_calls.append((cmd[-2], cmd[-1]))
            elif "cat" in cmd and "att_sweep_dump.xml" in cmd:
                res.stdout = (
                    '<hierarchy rotation="0">'
                    '<node text="Start Tracking" bounds="[40,100][500,180]" />'
                    '<node text="Workouts" bounds="[40,220][500,300]" />'
                    '</hierarchy>'
                )
            elif "logcat" in cmd and "-d" in cmd:
                res.stdout = "01-01 12:00:00.000 I/ActivityTaskManager: Displayed MainActivityWithNavigation"
            elif "screencap" in cmd:
                res.stdout = b"\x89PNG\r\n\x1a\nFAKEPNG"
            return res

        with tempfile.TemporaryDirectory() as tmpdir:
            ok, report_path, os_res, loc_res = sweep.run_sweep(
                out_dir=tmpdir,
                emulator_bin="/mock/emulator",
                adb_bin="adb",
                runner=fake_runner,
                popen_fn=fake_popen,
                devices_fn=fake_devices,
                avds_fn=fake_avds,
                boot_check_fn=lambda _s, _a="adb": True,
                tune_fn=lambda _s, _a="adb": None,
                configure_fn=lambda _s, package="", adb_bin="adb": None,
                sleep_fn=lambda _x: None,
            )

            self.assertTrue(ok)
            self.assertTrue(os.path.isfile(report_path))
            # Dynamically chose 3 distinct OS levels (12, 15, 36), skipping redundant Pixel_6_Pro_Android_12
            self.assertEqual(
                boot_order,
                ["Pixel_5_Android_12", "Pixel_8_Android_15", "Pixel_7_Android_36"],
            )
            self.assertEqual(len(os_res), 3)
            self.assertEqual(len(loc_res), 9)
            self.assertEqual(tuple(r.locale for r in loc_res), sweep.SUPPORTED_LOCALES)
            self.assertGreaterEqual(len(geo_fix_calls), 12)
            self.assertEqual(len(killed_serials), 3)
            self.assertEqual(len(active_avds), 0)

            with open(report_path, encoding="utf-8") as rf:
                content = rf.read()
            self.assertIn("Pixel_8_Android_15", content)
            self.assertIn("`ja`", content)

            # Verify finally cleanup still runs adb emu kill when a mid-run probe raises
            killed_serials.clear()
            active_avds.clear()

            def failing_runner(cmd, **kwargs):
                if "am" in cmd and "instrument" in cmd:
                    raise RuntimeError("Simulated mid-run instrumentation crash")
                return fake_runner(cmd, **kwargs)

            ok2, _, os_res2, _ = sweep.run_sweep(
                requested_avds=["Pixel_8_Android_15"],
                min_levels=1,
                allow_fewer_avds=True,
                out_dir=tmpdir,
                emulator_bin="/mock/emulator",
                runner=failing_runner,
                popen_fn=fake_popen,
                devices_fn=fake_devices,
                avds_fn=fake_avds,
                boot_check_fn=lambda _s, _a="adb": True,
                tune_fn=lambda _s, _a="adb": None,
                configure_fn=lambda _s, package="", adb_bin="adb": None,
                sleep_fn=lambda _x: None,
            )
            self.assertFalse(ok2)
            self.assertFalse(os_res2[0].passed)
            self.assertEqual(killed_serials, ["emulator-5554"])
            self.assertEqual(len(active_avds), 0)

    def test_tst_pro_135_2_physical_device_refusal_and_logcat_and_bounds_defects(self):
        """TST-PRO-135.2: Rejects physical serial 66020DLCR002FL & flags logcat/bounds defects."""
        with self.assertRaises(RuntimeError) as ctx:
            sweep.assert_emulator_only_serial("66020DLCR002FL")
        self.assertIn("Physical-Phone Guardrail", str(ctx.exception))

        with self.assertRaises(RuntimeError):
            sweep._run_adb("adb", "66020DLCR002FL", ["shell", "echo", "nope"])

        self.assertEqual(sweep.assert_emulator_only_serial("emulator-5554"), "emulator-5554")

        # Logcat fatal/ANR/StrictMode/FormatException scan
        bad_logcat = (
            "01-01 12:00:01.000 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "01-01 12:00:02.000 E/ActivityManager: ANR in com.atrainingtracker.debug\n"
            "01-01 12:00:03.000 E/StrictMode: StrictMode policy violation: DiskReadViolation\n"
            "01-01 12:00:04.000 E/AndroidRuntime: java.util.FormatFlagsConversionMismatchException: Conversion = d\n"
        )
        log_defects = sweep.scan_logcat_defects(bad_logcat)
        self.assertEqual(len(log_defects), 4)
        self.assertTrue(any("[FATAL_EXCEPTION]" in d for d in log_defects))
        self.assertTrue(any("[ANR]" in d for d in log_defects))
        self.assertTrue(any("[STRICT_MODE]" in d for d in log_defects))
        self.assertTrue(any("[FORMAT_EXCEPTION]" in d for d in log_defects))

        # UIAutomator XML bounds analyzer: clipped, zero-size, ellipsized, overlapping siblings
        bad_xml = (
            '<hierarchy rotation="0">'
            '<node text="" bounds="[0,0][1080,2400]">'
            '  <node text="ZeroLabel" bounds="[100,100][100,140]" />'
            '  <node text="OffscreenClipped" bounds="[900,100][1200,160]" />'
            '  <node text="Long German Text..." bounds="[20,200][300,260]" />'
            '  <node text="OverlappingSibling" bounds="[250,210][500,270]" />'
            '</node>'
            '</hierarchy>'
        )
        bounds_defects = sweep.analyze_uiautomator_bounds(bad_xml, screen_width=1080, screen_height=2400)
        kinds = {d.kind for d in bounds_defects}
        self.assertIn("ZERO_OR_INVERTED_BOUNDS", kinds)
        self.assertIn("OUT_OF_SCREEN_CLIP", kinds)
        self.assertIn("ELLIPSIZED_TEXT", kinds)
        self.assertIn("OVERLAPPING_TEXT", kinds)


if __name__ == "__main__":
    unittest.main()
