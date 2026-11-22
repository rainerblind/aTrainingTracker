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
        """TST-PRO-135.1: Serial Multi-OS (--apk/--test-apk, API<33 + API>=33) + 9-language UI sweep + finally cleanup."""
        boot_order = []
        active_avds = []
        killed_serials = []
        locale_calls = []
        geo_fix_calls = []
        installed_apks = []
        probe_sequence = []
        dump_paths_seen = []
        state = {"app_loc": "en", "sys_loc": "en-US"}

        sdk_by_avd = {
            "Pixel_5_Android_12": "31",
            "Pixel_6_Pro_Android_12": "31",
            "Pixel_8_Android_15": "35",
            "Pixel_7_Android_36": "36",
        }

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
            res.stderr = ""
            current_avd = active_avds[-1] if active_avds else "Pixel_8_Android_15"
            if "kill" in cmd and "emu" in cmd:
                killed_serials.append(cmd[2])
                active_avds.clear()
            elif "install" in cmd:
                installed_apks.append(cmd[-1])
                res.stdout = "Success"
            elif "ro.build.version.sdk" in cmd:
                res.stdout = sdk_by_avd.get(current_avd, "35")
            elif "getprop" in cmd and "persist.sys.locale" in cmd:
                res.stdout = state["sys_loc"]
            elif "setprop" in cmd and "persist.sys.locale" in cmd:
                state["sys_loc"] = cmd[-1]
                locale_calls.append(cmd[-1])
            elif "id" == cmd[-1]:
                res.stdout = "uid=0(root) gid=0(root)"
            elif "set-app-locales" in cmd:
                state["app_loc"] = cmd[-1]
                locale_calls.append(cmd[-1])
            elif "get-app-locales" in cmd:
                res.stdout = f"Locales for com.atrainingtracker.debug are [{state['app_loc']}]"
            elif "am" in cmd and "instrument" in cmd:
                probe_sequence.append("instrument")
                res.stdout = "Time: 0.5\n\nOK (1 test)\n"
            elif "am" in cmd and "start" in cmd:
                probe_sequence.append("am_start")
                res.stdout = "Status: ok\nComplete\n"
            elif "rm" in cmd and any("att_sweep_dump.xml" in tok for tok in cmd):
                probe_sequence.append("rm_dump")
                dump_paths_seen.append(("rm", cmd[-1]))
            elif "uiautomator" in cmd and "dump" in cmd:
                probe_sequence.append("uiautomator_dump")
                dump_paths_seen.append(("dump", cmd[-1]))
                res.stdout = "UI hierchary dumped to: /data/local/tmp/att_sweep_dump.xml"
            elif "geo" in cmd and "fix" in cmd:
                geo_fix_calls.append((cmd[-2], cmd[-1]))
            elif "cat" in cmd and any("att_sweep_dump.xml" in tok for tok in cmd):
                probe_sequence.append("cat_dump")
                dump_paths_seen.append(("cat", cmd[-1]))
                res.stdout = (
                    '<hierarchy rotation="0">'
                    '<node package="com.atrainingtracker.debug" text="Start Tracking" bounds="[40,100][500,180]" />'
                    '<node package="com.atrainingtracker.debug" text="Workouts" bounds="[40,220][500,300]" />'
                    '</hierarchy>'
                )
            elif "logcat" in cmd and "-d" in cmd:
                res.stdout = "01-01 12:00:00.000 I/ActivityTaskManager: Displayed MainActivityWithNavigation"
            elif "screencap" in cmd:
                probe_sequence.append("screencap")
                res.stdout = b"\x89PNG\r\n\x1a\nFAKEPNG"
            return res

        with tempfile.TemporaryDirectory() as tmpdir:
            ok, report_path, os_res, loc_res = sweep.run_sweep(
                apk_path="/mock/app-debug.apk",
                test_apk_path="/mock/app-debug-androidTest.apk",
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
            self.assertEqual(installed_apks.count("/mock/app-debug.apk"), 3)
            self.assertEqual(installed_apks.count("/mock/app-debug-androidTest.apk"), 3)
            # Verify foreground App-UI launch precedes stale-dump removal, uiautomator dump, cat, cleanup & screencap
            self.assertEqual(
                probe_sequence[:7],
                ["instrument", "am_start", "rm_dump", "uiautomator_dump", "cat_dump", "rm_dump", "screencap"],
            )
            self.assertTrue(dump_paths_seen)
            self.assertTrue(
                all(path == "/data/local/tmp/att_sweep_dump.xml" for _, path in dump_paths_seen),
                f"Unexpected dump path(s): {dump_paths_seen}",
            )

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

    def test_tst_pro_135_1_api31_locale_fallback_and_non_rootable_error(self):
        """TST-PRO-135.1: Android 12 (API<33) switches via adb root + persist.sys.locale + stop/start; rejects non-root."""
        cmds_seen = []
        state = {"sys_loc": "en-US"}

        def api31_root_runner(cmd, **_kwargs):
            cmds_seen.append(list(cmd))
            res = MagicMock()
            res.returncode = 0
            res.stdout = ""
            res.stderr = ""
            if "ro.build.version.sdk" in cmd:
                res.stdout = "31"
            elif "getprop" in cmd and "persist.sys.locale" in cmd:
                res.stdout = state["sys_loc"]
            elif "setprop" in cmd and "persist.sys.locale" in cmd:
                state["sys_loc"] = cmd[-1]
            elif "service" in cmd and "activity" in cmd:
                res.stdout = "Service activity: found\n"
            elif cmd[-1] == "id":
                res.stdout = "uid=0(root) gid=0(root)" if any("root" in c for c in cmds_seen) else "uid=2000(shell)"
            return res

        tuned = []
        sweep.set_app_locale(
            "emulator-5554",
            "com.atrainingtracker.debug",
            "de",
            runner=api31_root_runner,
            boot_check_fn=lambda _s, _a="adb": True,
            tune_fn=lambda s, _a="adb": tuned.append(s),
            sleep_fn=lambda _x: None,
        )
        flat_cmds = [" ".join(c) for c in cmds_seen]
        self.assertTrue(any("adb -s emulator-5554 root" in c for c in flat_cmds))
        self.assertTrue(any("setprop persist.sys.locale de" in c for c in flat_cmds))
        self.assertTrue(any("settings put system system_locales de" in c for c in flat_cmds))
        self.assertTrue(any("shell stop" in c for c in flat_cmds))
        self.assertTrue(any("shell start" in c for c in flat_cmds))
        self.assertEqual(tuned, ["emulator-5554"])

        # Non-rootable Play Store API 31 image must raise RuntimeError transparently
        def non_root_runner(cmd, **_kwargs):
            res = MagicMock()
            res.returncode = 0
            res.stdout = "31" if "ro.build.version.sdk" in cmd else ("uid=2000(shell)" if cmd[-1] == "id" else "en-US")
            res.stderr = ""
            return res

        with self.assertRaises(RuntimeError) as ctx:
            sweep.set_app_locale(
                "emulator-5554",
                "com.atrainingtracker.debug",
                "fr",
                runner=non_root_runner,
                sleep_fn=lambda _x: None,
            )
        self.assertIn("uid=0", str(ctx.exception))

        # CLI parser accepts --test-apk and --locales
        parsed = sweep.build_cli_parser().parse_args(
            ["--test-apk", "/tmp/t.apk", "--locales", "de", "ja", "--allow-fewer-avds"]
        )
        self.assertEqual(parsed.test_apk, "/tmp/t.apk")
        self.assertEqual(parsed.locales, ["de", "ja"])

    def test_tst_pro_135_2_fail_closed_install_instrument_launcher_and_screencap(self):
        """TST-PRO-135.2: Rejects failed install, instrument failures, launcher/empty XML, and bad screencap."""
        # 1. Failed adb install
        def bad_install(cmd, **_kwargs):
            res = MagicMock()
            res.returncode = 1
            res.stdout = "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]"
            res.stderr = ""
            return res

        with self.assertRaises(RuntimeError):
            sweep.install_apk("emulator-5554", "/tmp/bad.apk", runner=bad_install)

        # 2. Instrumentation failure variants (non-zero, FAILURES!!!, INSTRUMENTATION_FAILED, missing OK)
        with self.assertRaises(RuntimeError):
            sweep.verify_instrumentation_output(1, "OK (1 test)")
        with self.assertRaises(RuntimeError):
            sweep.verify_instrumentation_output(0, "Tests run: 1, Failures: 1\nFAILURES!!!")
        with self.assertRaises(RuntimeError):
            sweep.verify_instrumentation_output(0, "INSTRUMENTATION_FAILED: com.atrainingtracker.debug.test")
        with self.assertRaises(RuntimeError):
            sweep.verify_instrumentation_output(0, "")
        sweep.verify_instrumentation_output(0, "OK (1 test)\n")

        # 3. Launcher & empty XML validation
        with self.assertRaises(RuntimeError):
            sweep.validate_captured_ui_xml("", "com.atrainingtracker.debug")
        with self.assertRaises(RuntimeError) as launcher_ctx:
            sweep.validate_captured_ui_xml(
                '<hierarchy><node package="com.google.android.apps.nexuslauncher" text="Search" bounds="[0,0][100,100]"/></hierarchy>',
                "com.atrainingtracker.debug",
            )
        self.assertIn("Launcher", str(launcher_ctx.exception))

        # 4. Invalid screencap PNG must raise inside run_ui_probe
        def bad_screencap_runner(cmd, **_kwargs):
            res = MagicMock()
            res.returncode = 0
            res.stderr = ""
            if "am" in cmd and "instrument" in cmd:
                res.stdout = "OK (1 test)\n"
            elif "cat" in cmd and any("att_sweep_dump.xml" in tok for tok in cmd):
                res.stdout = '<hierarchy><node package="com.atrainingtracker.debug" text="Track" bounds="[10,10][200,80]"/></hierarchy>'
            elif "screencap" in cmd:
                res.returncode = 1
                res.stdout = b""
            else:
                res.stdout = "ok"
            return res

        with tempfile.TemporaryDirectory() as tmpdir:
            with self.assertRaises(RuntimeError):
                sweep.run_ui_probe(
                    "emulator-5554",
                    "com.atrainingtracker.debug",
                    "Pixel_8_Android_15",
                    "de",
                    tmpdir,
                    runner=bad_screencap_runner,
                    sleep_fn=lambda _x: None,
                )

        # 5. API >= 33 bracketed get-app-locales verification (rejects substring false positives)
        def make_api35_runner(get_locales_stdout: str):
            def _runner(cmd, **_kwargs):
                res = MagicMock()
                res.returncode = 0
                res.stderr = ""
                if "ro.build.version.sdk" in cmd:
                    res.stdout = "35"
                elif "get-app-locales" in cmd:
                    res.stdout = get_locales_stdout
                else:
                    res.stdout = ""
                return res

            return _runner

        # Empty brackets `[]` must fail even when `"de"` appears in `"debug"` and `"es"` in `"locales"`
        for tricky_locale in ("de", "es"):
            with self.assertRaises(RuntimeError) as loc_ctx:
                sweep.set_app_locale(
                    "emulator-5554",
                    "com.atrainingtracker.debug",
                    tricky_locale,
                    runner=make_api35_runner("Locales for com.atrainingtracker.debug are []\n"),
                )
            self.assertIn(tricky_locale, str(loc_ctx.exception))

        # `[en]` must fail when `"de"` is requested
        with self.assertRaises(RuntimeError):
            sweep.set_app_locale(
                "emulator-5554",
                "com.atrainingtracker.debug",
                "de",
                runner=make_api35_runner("Locales for com.atrainingtracker.debug are [en]\n"),
            )

        # `[de]` and `[es-ES]` pass cleanly; missing brackets fail closed
        with self.assertRaises(RuntimeError):
            sweep.verify_get_app_locales_output("unexpected output", "en")
        sweep.set_app_locale(
            "emulator-5554",
            "com.atrainingtracker.debug",
            "de",
            runner=make_api35_runner("Locales for com.atrainingtracker.debug are [de]\n"),
        )
        sweep.set_app_locale(
            "emulator-5554",
            "com.atrainingtracker.debug",
            "es",
            runner=make_api35_runner("Locales for com.atrainingtracker.debug are [es-ES]\n"),
        )

    def test_uiautomator_dump_uses_data_local_tmp_and_cleans_up_on_read_failure(self):
        """ATT-9510: Uses /data/local/tmp/att_sweep_dump.xml for dump/cat/rm -f and cleans up on read failure."""
        expected_path = "/data/local/tmp/att_sweep_dump.xml"
        seen_ops = []

        def success_runner(cmd, **_kwargs):
            res = MagicMock()
            res.returncode = 0
            res.stderr = ""
            if "am" in cmd and "start" in cmd:
                res.stdout = "Status: ok\nComplete\n"
            elif "rm" in cmd and "-f" in cmd:
                seen_ops.append(("rm", cmd[-1]))
                res.stdout = ""
            elif "uiautomator" in cmd and "dump" in cmd:
                seen_ops.append(("dump", cmd[-1]))
                res.stdout = f"UI hierchary dumped to: {expected_path}"
            elif "cat" in cmd:
                seen_ops.append(("cat", cmd[-1]))
                res.stdout = (
                    '<hierarchy rotation="0">'
                    '<node package="com.atrainingtracker.debug" text="Track" bounds="[10,10][200,80]" />'
                    "</hierarchy>"
                )
            elif "screencap" in cmd:
                res.stdout = b"\x89PNG\r\n\x1a\nFAKEPNG"
            else:
                res.stdout = ""
            return res

        with tempfile.TemporaryDirectory() as tmpdir:
            sweep.run_ui_probe(
                "emulator-5554",
                "com.atrainingtracker.debug",
                "Pixel_7_Android_36",
                "en",
                tmpdir,
                instrumentation_class=None,
                runner=success_runner,
                sleep_fn=lambda _x: None,
            )

        self.assertEqual(
            seen_ops,
            [
                ("rm", expected_path),
                ("dump", expected_path),
                ("cat", expected_path),
                ("rm", expected_path),
            ],
        )

        # Verify finally cleanup still runs rm -f /data/local/tmp/att_sweep_dump.xml if cat fails
        for fail_mode in ("nonzero", "exception"):
            seen_ops.clear()

            def failing_cat_runner(cmd, _mode=fail_mode, **_kwargs):
                res = MagicMock()
                res.returncode = 0
                res.stderr = ""
                if "am" in cmd and "start" in cmd:
                    res.stdout = "Status: ok\nComplete\n"
                elif "rm" in cmd and "-f" in cmd:
                    seen_ops.append(("rm", cmd[-1]))
                    res.stdout = ""
                elif "uiautomator" in cmd and "dump" in cmd:
                    seen_ops.append(("dump", cmd[-1]))
                    res.stdout = f"UI hierchary dumped to: {expected_path}"
                elif "cat" in cmd:
                    seen_ops.append(("cat", cmd[-1]))
                    if _mode == "exception":
                        raise OSError("Simulated cat read error")
                    res.returncode = 1
                    res.stderr = f"cat: {expected_path}: No such file or directory"
                    res.stdout = ""
                else:
                    res.stdout = ""
                return res

            with tempfile.TemporaryDirectory() as tmpdir:
                with self.assertRaises((RuntimeError, OSError)):
                    sweep.run_ui_probe(
                        "emulator-5554",
                        "com.atrainingtracker.debug",
                        "Pixel_7_Android_36",
                        "en",
                        tmpdir,
                        instrumentation_class=None,
                        runner=failing_cat_runner,
                        sleep_fn=lambda _x: None,
                    )

            self.assertEqual(
                seen_ops,
                [
                    ("rm", expected_path),
                    ("dump", expected_path),
                    ("cat", expected_path),
                    ("rm", expected_path),
                ],
            )

    def test_att_9363_scan_logcat_defects_filters_foreign_pids_and_catches_app_defects(self):
        """ATT-9363: Ignores foreign-PID StrictMode/crashes while catching app PIDs, restarted PIDs, and package blocks."""
        pkg = "com.atrainingtracker.debug"

        # 1. Foreign system StrictMode / crash / ANR noise only -> 0 defects
        foreign_noise = (
            "11-20 21:05:12.345 D/StrictMode( 5807): StrictMode policy violation; ~duration=42 ms: DiskReadViolation\n"
            "11-20 21:05:13.100 D/StrictMode( 6591): StrictMode policy violation; ~duration=18 ms: UnbufferedIoViolation\n"
            "11-20 21:05:14.000 E/AndroidRuntime( 5807): FATAL EXCEPTION: AsyncTask #1\n"
            "11-20 21:05:14.001 E/AndroidRuntime( 5807): Process: com.google.android.gms.gcore.dynamite, PID: 5807\n"
            "11-20 21:05:14.002 E/AndroidRuntime( 5807): java.util.FormatFlagsConversionMismatchException: Conversion = d\n"
            "11-20 21:05:15.000 E/ActivityManager(  512): ANR in com.android.providers.contacts\n"
            "11-20 21:05:16.000 E/ActivityManager(  512): ANR in com.atrainingtracker.debug2\n"
            "11-20 21:05:17.000 E/ActivityManager(  512): ANR in com.atrainingtracker.debug.other\n"
        )
        self.assertEqual(
            sweep.scan_logcat_defects(foreign_noise, app_pids={"7412"}, package=pkg),
            [],
        )

        # 2. Mix of foreign StrictMode + own-app StrictMode + restarted PID discovered from logcat + system-logged ANR
        mixed_logcat = (
            "11-20 21:05:10.000 I/ActivityManager(  512): Start proc 7100:com.atrainingtracker.debug/u0a198 for activity\n"
            "11-20 21:05:11.000 D/StrictMode( 5807): StrictMode policy violation: foreign gcore.dynamite\n"
            "11-20 21:05:12.000 D/StrictMode( 6591): StrictMode policy violation: foreign contacts\n"
            "11-20 21:05:12.500 D/StrictMode( 7100): StrictMode policy violation: app restarted PID violation\n"
            "11-20 21:05:13.000 D/StrictMode( 7412): StrictMode policy violation: app current PID violation\n"
            "11-20 21:05:14.000 E/AndroidRuntime( 7100): FATAL EXCEPTION: main\n"
            "11-20 21:05:14.001 I/SystemDaemon(  400): interleaved system log line\n"
            "11-20 21:05:14.002 E/AndroidRuntime( 7100): Process: com.atrainingtracker.debug, PID: 7100\n"
            "11-20 21:05:14.003 E/AndroidRuntime( 7100): java.util.IllegalFormatConversionException: d != java.lang.String\n"
            "11-20 21:05:15.000 E/ActivityManager(  512): ANR in com.atrainingtracker.debug (com.atrainingtracker.debug/.Main)\n"
        )
        # Only current PID 7412 came from pidof; 7100 must still be caught via Start proc / Process: lines
        defects = sweep.scan_logcat_defects(mixed_logcat, app_pids={"7412"}, package=pkg)
        self.assertEqual(len(defects), 5)
        self.assertTrue(any("[STRICT_MODE]" in d and "7100" in d for d in defects))
        self.assertTrue(any("[STRICT_MODE]" in d and "7412" in d for d in defects))
        self.assertTrue(any("[FATAL_EXCEPTION]" in d and "7100" in d for d in defects))
        self.assertTrue(any("[FORMAT_EXCEPTION]" in d and "7100" in d for d in defects))
        self.assertTrue(any("[ANR]" in d and pkg in d for d in defects))
        self.assertFalse(any("5807" in d or "6591" in d for d in defects))

        # 3. PID-less fatal block mentioning target package vs foreign package
        pidless_mixed = (
            "01-01 12:00:01.000 E/AndroidRuntime: FATAL EXCEPTION: Worker\n"
            "01-01 12:00:01.001 E/AndroidRuntime: Process: com.google.android.gms, PID: 5807\n"
            "01-01 12:00:01.002 E/AndroidRuntime: java.util.IllegalFormatConversionException: d\n"
            "\n"
            "01-01 12:00:02.000 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "01-01 12:00:02.001 E/AndroidRuntime: Process: com.atrainingtracker.debug, PID: 8888\n"
            "01-01 12:00:02.002 E/AndroidRuntime: java.util.UnknownFormatConversionException: Conversion = q\n"
        )
        pidless_defects = sweep.scan_logcat_defects(pidless_mixed, app_pids=set(), package=pkg)
        self.assertEqual(len(pidless_defects), 2)
        self.assertTrue(any("[FATAL_EXCEPTION]" in d and "main" in d for d in pidless_defects))
        self.assertTrue(any("[FORMAT_EXCEPTION]" in d and "UnknownFormatConversionException" in d for d in pidless_defects))
        self.assertFalse(any("Worker" in d for d in pidless_defects))

    def test_att_9363_run_ui_probe_tracks_multi_pid_across_instrument_and_start(self):
        """ATT-9363: run_ui_probe tracks pidof across am instrument and am start -W and ignores foreign StrictMode."""
        pkg = "com.atrainingtracker.debug"

        def make_probe_runner(logcat_stdout: str, pid_sequence):
            pid_iter = iter(pid_sequence)

            def _runner(cmd, **_kwargs):
                res = MagicMock()
                res.returncode = 0
                res.stderr = ""
                if "am" in cmd and "instrument" in cmd:
                    res.stdout = "OK (1 test)\n"
                elif "am" in cmd and "start" in cmd:
                    res.stdout = "Status: ok\nComplete\n"
                elif "pidof" in cmd:
                    rc, out = next(pid_iter, (1, ""))
                    res.returncode = rc
                    res.stdout = out
                elif "uiautomator" in cmd and "dump" in cmd:
                    res.stdout = "UI hierchary dumped to: /data/local/tmp/att_sweep_dump.xml"
                elif "cat" in cmd:
                    res.stdout = (
                        '<hierarchy rotation="0">'
                        '<node package="com.atrainingtracker.debug" text="Track" bounds="[10,10][200,80]" />'
                        "</hierarchy>"
                    )
                elif "logcat" in cmd and "-d" in cmd:
                    res.stdout = logcat_stdout
                elif "screencap" in cmd:
                    res.stdout = b"\x89PNG\r\n\x1a\nFAKEPNG"
                else:
                    res.stdout = ""
                return res

            return _runner

        # Case A: Foreign StrictMode noise only -> 0 log_defects
        foreign_only_logcat = (
            "11-20 21:05:12.345 D/StrictMode( 5807): StrictMode policy violation: gcore.dynamite\n"
            "11-20 21:05:13.000 D/StrictMode( 6591): StrictMode policy violation: com.android.providers.contacts\n"
            "11-20 21:05:14.000 I/ActivityTaskManager(  512): Displayed com.atrainingtracker.debug/.MainActivity\n"
        )
        with tempfile.TemporaryDirectory() as tmpdir:
            log_defects, bounds_defects, shot = sweep.run_ui_probe(
                "emulator-5554",
                pkg,
                "Pixel_5_Android_12",
                "en",
                tmpdir,
                runner=make_probe_runner(foreign_only_logcat, [(0, "7100 7101\n"), (0, "7412\n"), (0, "7412\n")]),
                sleep_fn=lambda _x: None,
            )
            self.assertEqual(log_defects, [])
            self.assertEqual(bounds_defects, [])
            self.assertTrue(os.path.isfile(shot))

        # Case B: Violation from earlier instrumentation PID (7100) + foreground PID (7412) both caught; foreign 5807 ignored
        mixed_probe_logcat = (
            "11-20 21:05:11.000 D/StrictMode( 5807): StrictMode policy violation: gcore.dynamite\n"
            "11-20 21:05:12.000 D/StrictMode( 7100): StrictMode policy violation: DiskRead during instrument\n"
            "11-20 21:05:13.000 D/StrictMode( 7412): StrictMode policy violation: DiskRead after am start\n"
        )
        with tempfile.TemporaryDirectory() as tmpdir:
            log_defects, _, _ = sweep.run_ui_probe(
                "emulator-5554",
                pkg,
                "Pixel_5_Android_12",
                "en",
                tmpdir,
                runner=make_probe_runner(mixed_probe_logcat, [(0, "7100\n"), (0, "7412\n"), (1, "")]),
                sleep_fn=lambda _x: None,
            )
            self.assertEqual(len(log_defects), 2)
            self.assertTrue(any("7100" in d for d in log_defects))
            self.assertTrue(any("7412" in d for d in log_defects))
            self.assertFalse(any("5807" in d for d in log_defects))


if __name__ == "__main__":
    unittest.main()
