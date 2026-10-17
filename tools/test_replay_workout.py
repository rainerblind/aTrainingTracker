#!/usr/bin/env python3
"""
tools/test_replay_workout.py - Unit Tests for TCX Workout Replay Tool
===================================================================
Verifies TCX parsing, ReplayEngine timeline mechanics, BLE payload
construction, and PyQt6 GUI contracts (TST-TOOL-002 / REQ-TOOL-002).
"""

import os
import sys
import unittest
import tempfile

from tools.replay_workout import (
    TcxParser, TcxPoint, ReplayEngine,
    build_power_payload, build_hr_payload, build_battery_payload,
    format_duration, haversine_distance, calculate_bearing,
    BleReplayServer, AdbGpsInjector,
    HostPreFlightDiagnostics, PreFlightReport,
    CYCLING_POWER_SERVICE_UUID, HEART_RATE_SERVICE_UUID,
    BATTERY_SERVICE_UUID, BATTERY_LEVEL_CHARACTERISTIC_UUID,
    build_arg_parser, ScheduledTriggerManager
)

try:
    from PyQt6.QtWidgets import QApplication
    from tools.replay_workout import ReplayGuiWindow
    HAS_PYQT6 = True
except ImportError:
    HAS_PYQT6 = False


class TestGeodesicsAndHelpers(unittest.TestCase):
    def test_haversine_distance(self):
        # Distance between Munich Marienplatz and Odeonsplatz (~850m)
        lat1, lon1 = 48.137154, 11.576124
        lat2, lon2 = 48.142800, 11.577500
        d = haversine_distance(lat1, lon1, lat2, lon2)
        self.assertTrue(600.0 < d < 1000.0, f"Distance expected ~630m, got {d}")

    def test_calculate_bearing(self):
        # Directly north: bearing ~ 0 deg
        b_north = calculate_bearing(48.0, 11.0, 49.0, 11.0)
        self.assertAlmostEqual(b_north, 0.0, delta=1.0)

        # Directly east: bearing ~ 90 deg
        b_east = calculate_bearing(48.0, 11.0, 48.0, 12.0)
        self.assertAlmostEqual(b_east, 90.0, delta=2.0)

    def test_format_duration(self):
        self.assertEqual(format_duration(0), "00:00:00")
        self.assertEqual(format_duration(65), "00:01:05")
        self.assertEqual(format_duration(3665), "01:01:05")


class TestTcxParser(unittest.TestCase):
    def test_parse_sample_workout(self):
        sample_path = os.path.join(os.path.dirname(__file__), "sample_workout.tcx")
        self.assertTrue(os.path.isfile(sample_path), "sample_workout.tcx must exist")

        points = TcxParser.parse_file(sample_path)
        self.assertTrue(len(points) >= 20, f"Expected >= 20 points, got {len(points)}")

        # Validate first point
        p0 = points[0]
        self.assertAlmostEqual(p0.lat, 48.173200, places=4)
        self.assertAlmostEqual(p0.lon, 11.546300, places=4)
        self.assertEqual(p0.ele, 510.0)
        self.assertEqual(p0.hr, 130)
        self.assertEqual(p0.cadence, 80)
        self.assertEqual(p0.watts, 180)
        self.assertGreater(p0.speed_mps, 0.0)

        # Validate progression and bearings
        p_last = points[-1]
        self.assertGreater(p_last.dist, 1000.0)
        self.assertGreater(p_last.time_sec, 0.0)
        for p in points:
            self.assertTrue(0.0 <= p.bearing < 360.0)

    def test_missing_file_raises_error(self):
        with self.assertRaises(FileNotFoundError):
            TcxParser.parse_file("/tmp/non_existent_file_xyz.tcx")

    def test_empty_xml_raises_error(self):
        with tempfile.NamedTemporaryFile("w", suffix=".tcx", delete=False) as f:
            f.write("<TrainingCenterDatabase></TrainingCenterDatabase>")
            tmp_name = f.name
        try:
            with self.assertRaises(ValueError):
                TcxParser.parse_file(tmp_name)
        finally:
            os.remove(tmp_name)

    def test_tilde_path_expansion(self):
        home_path = "~/sample.tcx"
        expanded = os.path.expanduser(home_path)
        self.assertFalse(expanded.startswith("~"))
        self.assertTrue(os.path.isabs(expanded))

    def test_variable_interval_tcx_interpolation_and_fallback_speed(self):
        """TST-TOOL-006.1: Retains 1-Hz HR/Power trackpoints missing <Position>, linearly interpolates lat/lon/ele, and computes speed_mps = step_d / dt."""
        tcx_xml = """<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase>
  <Activities>
    <Activity Sport="Biking">
      <Lap StartTime="2026-10-10T10:00:00Z">
        <Track>
          <Trackpoint>
            <Time>2026-10-10T10:00:00Z</Time>
            <Position><LatitudeDegrees>48.000000</LatitudeDegrees><LongitudeDegrees>11.000000</LongitudeDegrees></Position>
            <AltitudeMeters>500.0</AltitudeMeters>
            <HeartRateBpm><Value>130</Value></HeartRateBpm>
            <Cadence>80</Cadence>
            <Extensions><TPX><Watts>200</Watts></TPX></Extensions>
          </Trackpoint>
          <Trackpoint>
            <Time>2026-10-10T10:00:01Z</Time>
            <HeartRateBpm><Value>132</Value></HeartRateBpm>
            <Cadence>82</Cadence>
            <Extensions><TPX><Watts>210</Watts></TPX></Extensions>
          </Trackpoint>
          <Trackpoint>
            <Time>2026-10-10T10:00:02Z</Time>
            <HeartRateBpm><Value>134</Value></HeartRateBpm>
            <Cadence>84</Cadence>
            <Extensions><TPX><Watts>220</Watts></TPX></Extensions>
          </Trackpoint>
          <Trackpoint>
            <Time>2026-10-10T10:00:03Z</Time>
            <HeartRateBpm><Value>136</Value></HeartRateBpm>
            <Cadence>86</Cadence>
            <Extensions><TPX><Watts>230</Watts></TPX></Extensions>
          </Trackpoint>
          <Trackpoint>
            <Time>2026-10-10T10:00:04Z</Time>
            <HeartRateBpm><Value>138</Value></HeartRateBpm>
            <Cadence>88</Cadence>
            <Extensions><TPX><Watts>240</Watts></TPX></Extensions>
          </Trackpoint>
          <Trackpoint>
            <Time>2026-10-10T10:00:05Z</Time>
            <Position><LatitudeDegrees>48.000500</LatitudeDegrees><LongitudeDegrees>11.000500</LongitudeDegrees></Position>
            <AltitudeMeters>510.0</AltitudeMeters>
            <HeartRateBpm><Value>140</Value></HeartRateBpm>
            <Cadence>90</Cadence>
            <Extensions><TPX><Watts>250</Watts></TPX></Extensions>
          </Trackpoint>
        </Track>
      </Lap>
    </Activity>
  </Activities>
</TrainingCenterDatabase>"""
        with tempfile.NamedTemporaryFile("w", suffix=".tcx", delete=False) as f:
            f.write(tcx_xml)
            tmp_name = f.name
        try:
            points = TcxParser.parse_file(tmp_name)
            self.assertEqual(len(points), 6, "All 6 1-Hz trackpoints must be retained")
            # Check preserved 1-Hz HR and Power on intermediate non-Position trackpoints
            self.assertEqual([p.hr for p in points], [130, 132, 134, 136, 138, 140])
            self.assertEqual([p.watts for p in points], [200, 210, 220, 230, 240, 250])
            # Check linear coordinate & altitude interpolation at t = 2.0s (alpha = 0.4)
            self.assertAlmostEqual(points[2].lat, 48.000200, places=6)
            self.assertAlmostEqual(points[2].lon, 11.000200, places=6)
            self.assertAlmostEqual(points[2].ele, 504.0, places=4)
            # Check fallback speed_mps > 0 on all points when <Speed> is omitted
            for p in points:
                self.assertGreater(p.speed_mps, 10.0)
                self.assertLess(p.speed_mps, 18.0)
        finally:
            os.remove(tmp_name)

    def test_duplicate_timestamp_dt_zero_safety(self):
        """TST-TOOL-006.1: Duplicate timestamps (dt < 0.001) safely yield speed_mps = 0.0 without ZeroDivisionError or Inf/NaN."""
        tcx_xml = """<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase>
  <Activities>
    <Activity Sport="Biking">
      <Lap StartTime="2026-10-10T10:00:00Z">
        <Track>
          <Trackpoint>
            <Time>2026-10-10T10:00:00Z</Time>
            <Position><LatitudeDegrees>48.000000</LatitudeDegrees><LongitudeDegrees>11.000000</LongitudeDegrees></Position>
          </Trackpoint>
          <Trackpoint>
            <Time>2026-10-10T10:00:00Z</Time>
            <Position><LatitudeDegrees>48.000100</LatitudeDegrees><LongitudeDegrees>11.000100</LongitudeDegrees></Position>
          </Trackpoint>
        </Track>
      </Lap>
    </Activity>
  </Activities>
</TrainingCenterDatabase>"""
        with tempfile.NamedTemporaryFile("w", suffix=".tcx", delete=False) as f:
            f.write(tcx_xml)
            tmp_name = f.name
        try:
            points = TcxParser.parse_file(tmp_name)
            self.assertEqual(len(points), 2)
            self.assertEqual(points[1].speed_mps, 0.0)
        finally:
            os.remove(tmp_name)


class TestBlePayloads(unittest.TestCase):
    def test_build_power_payload(self):
        # 200 Watts, 85 RPM, revs=100, event_time=2048
        payload = build_power_payload(200, 85, cumulative_revs=100, event_time_1024=2048)
        self.assertEqual(len(payload), 8)
        # Flags: 0x0020
        self.assertEqual(payload[0], 0x20)
        self.assertEqual(payload[1], 0x00)
        # Power: 200 (0x00C8) -> little endian: 0xC8, 0x00
        self.assertEqual(payload[2], 0xC8)
        self.assertEqual(payload[3], 0x00)
        # Cumulative revs: 100 -> 0x64, 0x00
        self.assertEqual(payload[4], 0x64)
        self.assertEqual(payload[5], 0x00)
        # Event time: 2048 -> 0x00, 0x08
        self.assertEqual(payload[6], 0x00)
        self.assertEqual(payload[7], 0x08)

    def test_build_hr_payload(self):
        payload = build_hr_payload(145)
        self.assertEqual(len(payload), 2)
        self.assertEqual(payload[0], 0x00)  # Flags
        self.assertEqual(payload[1], 145)   # BPM

    def test_build_hr_payload_clamping(self):
        payload_high = build_hr_payload(300)
        self.assertEqual(payload_high[1], 255)
        payload_low = build_hr_payload(-10)
        self.assertEqual(payload_low[1], 0)

    def test_build_battery_payload(self):
        self.assertEqual(build_battery_payload(100), [100])
        self.assertEqual(build_battery_payload(50), [50])
        self.assertEqual(build_battery_payload(15), [15])
        self.assertEqual(build_battery_payload(0), [0])

    def test_build_battery_payload_clamping(self):
        self.assertEqual(build_battery_payload(150), [100])
        self.assertEqual(build_battery_payload(-20), [0])


class TestBleReplayServer(unittest.TestCase):
    def test_battery_level_management(self):
        server = BleReplayServer(enabled=False, hr_battery=80, power_battery=60)
        self.assertEqual(server.hr_battery, 80)
        self.assertEqual(server.power_battery, 60)

        # Update via setters
        server.set_hr_battery(15)
        self.assertEqual(server.hr_battery, 15)
        server.set_power_battery(5)
        self.assertEqual(server.power_battery, 5)

        # Clamping
        server.set_hr_battery(150)
        self.assertEqual(server.hr_battery, 100)
        server.set_power_battery(-10)
        self.assertEqual(server.power_battery, 0)

    def test_ble_kill_and_revive_hr(self):
        server = BleReplayServer(enabled=False)
        self.assertFalse(server.is_hr_killed)

        server.kill_hr()
        self.assertTrue(server.is_hr_killed)
        # Calling update_telemetry while killed should not fail
        server.update_telemetry(watts=200, cadence=90, hr_bpm=140)

        server.toggle_hr()
        self.assertFalse(server.is_hr_killed)

        server.toggle_hr()
        self.assertTrue(server.is_hr_killed)

        server.revive_hr()
        self.assertFalse(server.is_hr_killed)

    def test_ble_kill_and_revive_power(self):
        server = BleReplayServer(enabled=False)
        self.assertFalse(server.is_power_killed)

        server.kill_power()
        self.assertTrue(server.is_power_killed)
        # Calling update_telemetry while killed should not fail
        server.update_telemetry(watts=200, cadence=90, hr_bpm=140)

        server.toggle_power()
        self.assertFalse(server.is_power_killed)

        server.toggle_power()
        self.assertTrue(server.is_power_killed)

        server.revive_power()
        self.assertFalse(server.is_power_killed)


class TestReplayEngine(unittest.TestCase):
    def setUp(self):
        self.points = [
            TcxPoint("2026-10-10T10:00:00Z", 0.0, 48.0, 11.0, 500.0, 0.0, 120, 75, 150, 5.0, 45.0),
            TcxPoint("2026-10-10T10:00:05Z", 5.0, 48.001, 11.001, 505.0, 50.0, 130, 80, 180, 6.0, 45.0),
            TcxPoint("2026-10-10T10:00:10Z", 10.0, 48.002, 11.002, 510.0, 100.0, 140, 85, 210, 7.0, 45.0),
            TcxPoint("2026-10-10T10:00:15Z", 15.0, 48.003, 11.003, 515.0, 150.0, 150, 90, 240, 8.0, 45.0),
        ]
        self.engine = ReplayEngine(self.points)

    def test_initial_state(self):
        self.assertEqual(self.engine.status, "STOPPED")
        self.assertEqual(self.engine.current_index, 0)
        self.assertEqual(self.engine.elapsed_time_sec, 0.0)
        self.assertEqual(self.engine.progress_ratio, 0.0)

    def test_playback_lifecycle_and_elapsed_time_stepping(self):
        """TST-TOOL-006.2: Advances by workout elapsed time (1.0s * speed_multiplier) rather than raw array index."""
        self.engine.play()
        self.assertEqual(self.engine.status, "PLAYING")

        # 4 steps at 1x = 4.0s elapsed -> still at index 0 (t=0.0s, next is t=5.0s)
        for _ in range(4):
            self.engine.step()
        self.assertEqual(self.engine.current_index, 0)
        self.assertAlmostEqual(self.engine.elapsed_time_sec, 4.0)

        # 5th step at 1x = 5.0s elapsed -> advances to index 1 (t=5.0s)
        self.engine.step()
        self.assertEqual(self.engine.current_index, 1)
        self.assertAlmostEqual(self.engine.elapsed_time_sec, 5.0)

        self.engine.pause()
        self.assertEqual(self.engine.status, "PAUSED")

        self.engine.stop()
        self.assertEqual(self.engine.status, "STOPPED")
        self.assertEqual(self.engine.current_index, 0)
        self.assertEqual(self.engine.elapsed_time_sec, 0.0)

    def test_seek_ratio(self):
        self.engine.seek_ratio(0.5)
        self.assertIn(self.engine.current_index, [1, 2])
        self.assertEqual(self.engine.elapsed_time_sec, self.engine.current_point.time_sec)

        self.engine.seek_ratio(1.0)
        self.assertEqual(self.engine.current_index, 3)
        self.assertEqual(self.engine.elapsed_time_sec, 15.0)

        self.engine.seek_ratio(0.0)
        self.assertEqual(self.engine.current_index, 0)
        self.assertEqual(self.engine.elapsed_time_sec, 0.0)

    def test_seek_time(self):
        self.engine.seek_time(10.0)
        self.assertEqual(self.engine.current_point.time_sec, 10.0)
        self.assertEqual(self.engine.elapsed_time_sec, 10.0)

    def test_speed_scaling_and_final_tick_termination(self):
        """TST-TOOL-006.2: Speed multiplier scales elapsed_time_sec and final point transitions to PAUSED without out-of-bounds."""
        self.engine.set_speed(5.0)
        self.assertEqual(self.engine.speed_multiplier, 5.0)
        self.engine.play()

        # 1 step at 5x = +5.0s -> index 1 (t=5.0s)
        self.engine.step()
        self.assertEqual(self.engine.current_index, 1)
        self.assertEqual(self.engine.status, "PLAYING")

        # 2nd step at 5x = +10.0s -> index 2 (t=10.0s)
        self.engine.step()
        self.assertEqual(self.engine.current_index, 2)
        self.assertEqual(self.engine.status, "PLAYING")

        # 3rd step at 5x = +15.0s -> reaches final point (index 3, t=15.0s) and transitions to PAUSED
        self.engine.step()
        self.assertEqual(self.engine.current_index, 3)
        self.assertEqual(self.engine.elapsed_time_sec, 15.0)
        self.assertEqual(self.engine.status, "PAUSED")


@unittest.skipUnless(HAS_PYQT6, "PyQt6 is required for GUI tests")
class TestPyQt6GuiContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Create offscreen QApplication if not exists
        os.environ["QT_QPA_PLATFORM"] = "offscreen"
        cls.app = QApplication.instance() or QApplication(sys.argv)

    def test_gui_initialization(self):
        sample_path = os.path.join(os.path.dirname(__file__), "sample_workout.tcx")
        points = TcxParser.parse_file(sample_path)
        engine = ReplayEngine(points)
        ble = BleReplayServer(enabled=False)
        gps = AdbGpsInjector(enabled=False)

        window = ReplayGuiWindow(sample_path, engine, ble, gps)
        self.assertIsNotNone(window)
        self.assertIn("aTrainingTracker", window.windowTitle())

        # Check UI components
        self.assertIsNotNone(window.btn_play_pause)
        self.assertIsNotNone(window.btn_stop)
        self.assertIsNotNone(window.slider)
        self.assertIsNotNone(window.val_power)
        self.assertIsNotNone(window.val_cadence)
        self.assertIsNotNone(window.val_hr)
        self.assertIsNotNone(window.val_speed)

        # Check play/pause toggle
        window.toggle_play_pause()
        self.assertEqual(engine.status, "PLAYING")
        self.assertEqual(window.btn_play_pause.text(), "Pause")

        window.toggle_play_pause()
        self.assertEqual(engine.status, "PAUSED")
        self.assertEqual(window.btn_play_pause.text(), "Play")

        window.close()

    def test_gui_battery_controls(self):
        sample_path = os.path.join(os.path.dirname(__file__), "sample_workout.tcx")
        points = TcxParser.parse_file(sample_path)
        engine = ReplayEngine(points)
        ble = BleReplayServer(enabled=False, hr_battery=85, power_battery=45)
        gps = AdbGpsInjector(enabled=False)

        window = ReplayGuiWindow(sample_path, engine, ble, gps)
        self.assertIsNotNone(window.slider_hr_battery)
        self.assertIsNotNone(window.slider_pwr_battery)
        self.assertIsNotNone(window.lbl_hr_battery_val)
        self.assertIsNotNone(window.lbl_pwr_battery_val)

        # Verify initial values reflected in GUI
        self.assertEqual(window.slider_hr_battery.value(), 85)
        self.assertEqual(window.slider_pwr_battery.value(), 45)
        self.assertIn("85%", window.lbl_hr_battery_val.text())
        self.assertIn("45%", window.lbl_pwr_battery_val.text())

        # Move sliders
        window.slider_hr_battery.setValue(15)
        self.assertEqual(ble.hr_battery, 15)
        self.assertIn("15%", window.lbl_hr_battery_val.text())

        window.slider_pwr_battery.setValue(5)
        self.assertEqual(ble.power_battery, 5)
        self.assertIn("5%", window.lbl_pwr_battery_val.text())

        # Test preset buttons (HR)
        window.btn_hr_100.click()
        self.assertEqual(window.slider_hr_battery.value(), 100)
        self.assertEqual(ble.hr_battery, 100)
        self.assertIn("100%", window.lbl_hr_battery_val.text())

        window.btn_hr_50.click()
        self.assertEqual(window.slider_hr_battery.value(), 50)
        self.assertEqual(ble.hr_battery, 50)

        window.btn_hr_15.click()
        self.assertEqual(window.slider_hr_battery.value(), 15)
        self.assertEqual(ble.hr_battery, 15)

        window.btn_hr_5.click()
        self.assertEqual(window.slider_hr_battery.value(), 5)
        self.assertEqual(ble.hr_battery, 5)

        # Test preset buttons (Power)
        window.btn_pwr_100.click()
        self.assertEqual(window.slider_pwr_battery.value(), 100)
        self.assertEqual(ble.power_battery, 100)
        self.assertIn("100%", window.lbl_pwr_battery_val.text())

        window.btn_pwr_50.click()
        self.assertEqual(window.slider_pwr_battery.value(), 50)
        self.assertEqual(ble.power_battery, 50)

        window.btn_pwr_15.click()
        self.assertEqual(window.slider_pwr_battery.value(), 15)
        self.assertEqual(ble.power_battery, 15)

        window.btn_pwr_5.click()
        self.assertEqual(window.slider_pwr_battery.value(), 5)
        self.assertEqual(ble.power_battery, 5)

        window.close()

    def test_gui_connectivity_controls(self):
        sample_path = os.path.join(os.path.dirname(__file__), "sample_workout.tcx")
        points = TcxParser.parse_file(sample_path)
        engine = ReplayEngine(points)
        ble = BleReplayServer(enabled=False)
        gps = AdbGpsInjector(enabled=False)

        window = ReplayGuiWindow(sample_path, engine, ble, gps)
        self.assertIsNotNone(window.btn_toggle_hr)
        self.assertIsNotNone(window.btn_toggle_pwr)
        self.assertIsNotNone(window.btn_toggle_gps)
        self.assertIsNotNone(window.lbl_status_hrm_badge)
        self.assertIsNotNone(window.lbl_status_pwr_badge)
        self.assertIsNotNone(window.lbl_status_gps_badge)

        # Initial states
        self.assertEqual(window.lbl_status_hrm_badge.text(), "● ATT-HRM Active")
        self.assertEqual(window.lbl_status_pwr_badge.text(), "● ATT-Pwr Active")
        self.assertEqual(window.lbl_status_gps_badge.text(), "● GPS Active")
        self.assertEqual(window.btn_toggle_hr.text(), "Kill HR")
        self.assertEqual(window.btn_toggle_pwr.text(), "Kill Power")
        self.assertEqual(window.btn_toggle_gps.text(), "Kill GPS")

        # Kill HR
        window.btn_toggle_hr.click()
        self.assertTrue(ble.is_hr_killed)
        self.assertEqual(window.lbl_status_hrm_badge.text(), "■ ATT-HRM Killed")
        self.assertEqual(window.btn_toggle_hr.text(), "Revive HR")

        # Revive HR
        window.btn_toggle_hr.click()
        self.assertFalse(ble.is_hr_killed)
        self.assertEqual(window.lbl_status_hrm_badge.text(), "● ATT-HRM Active")
        self.assertEqual(window.btn_toggle_hr.text(), "Kill HR")

        # Kill Power
        window.btn_toggle_pwr.click()
        self.assertTrue(ble.is_power_killed)
        self.assertEqual(window.lbl_status_pwr_badge.text(), "■ ATT-Pwr Killed")
        self.assertEqual(window.btn_toggle_pwr.text(), "Revive Power")

        # Revive Power
        window.btn_toggle_pwr.click()
        self.assertFalse(ble.is_power_killed)
        self.assertEqual(window.lbl_status_pwr_badge.text(), "● ATT-Pwr Active")
        self.assertEqual(window.btn_toggle_pwr.text(), "Kill Power")

        # Kill GPS
        window.btn_toggle_gps.click()
        self.assertTrue(gps.is_killed)
        self.assertEqual(window.lbl_status_gps_badge.text(), "■ GPS Suppressed")
        self.assertEqual(window.btn_toggle_gps.text(), "Revive GPS")

        # Revive GPS
        window.btn_toggle_gps.click()
        self.assertFalse(gps.is_killed)
        self.assertEqual(window.lbl_status_gps_badge.text(), "● GPS Active")
        self.assertEqual(window.btn_toggle_gps.text(), "Kill GPS")

        window.close()


class TestAdbGpsInjector(unittest.TestCase):
    def test_build_inject_cmd_android14_syntax(self):
        cmd = AdbGpsInjector.build_inject_cmd("gps", 48.123456, 11.654321, 1700000000000, accuracy=2.5)
        # Must contain exact command line
        self.assertEqual(
            cmd,
            "cmd location providers set-test-provider-location gps --location 48.123456,11.654321 --accuracy 2.5 --time 1700000000000"
        )
        # Crucial: Must NOT contain unsupported flags rejected on Android 14+
        self.assertNotIn("--altitude", cmd)
        self.assertNotIn("--speed", cmd)
        self.assertNotIn("--bearing", cmd)

    def test_default_providers_contain_gps_network_fused(self):
        injector = AdbGpsInjector(enabled=False)
        self.assertEqual(injector.DEFAULT_PROVIDERS, ("gps", "network", "fused"))

    def test_async_queue_injection(self):
        injector = AdbGpsInjector(enabled=True, device_serial="DEVICE123")
        injector.is_registered = True
        self.assertEqual(injector._queue.qsize(), 0)
        injector.inject_location(48.1, 11.5, 500.0, 10.0, 90.0)
        self.assertEqual(injector._queue.qsize(), 1)
        item = injector._queue.get_nowait()
        self.assertEqual(item, (48.1, 11.5))

    def test_queue_overflow_drops_oldest(self):
        injector = AdbGpsInjector(enabled=True, device_serial="DEVICE123")
        injector.is_registered = True
        for i in range(5):
            injector.inject_location(48.0 + i, 11.0 + i, 500.0, 10.0, 90.0)
        self.assertEqual(injector._queue.qsize(), 5)
        # Push 6th item - drops oldest (48.0, 11.0)
        injector.inject_location(48.9, 11.9, 500.0, 10.0, 90.0)
        self.assertEqual(injector._queue.qsize(), 5)
        first_item = injector._queue.get_nowait()
        self.assertEqual(first_item, (48.0 + 1, 11.0 + 1))

    def test_gps_kill_and_revive(self):
        injector = AdbGpsInjector(enabled=True, device_serial="DEVICE123")
        injector.is_registered = True
        self.assertFalse(injector.is_killed)

        injector.kill()
        self.assertTrue(injector.is_killed)
        injector.inject_location(48.1, 11.5, 500.0, 10.0, 90.0)
        self.assertEqual(injector._queue.qsize(), 0)  # Dropped!

        injector.revive()
        self.assertFalse(injector.is_killed)
        injector.inject_location(48.2, 11.6, 500.0, 10.0, 90.0)
        self.assertEqual(injector._queue.qsize(), 1)  # Enqueued!

        injector.toggle()
        self.assertTrue(injector.is_killed)
        injector.toggle()
        self.assertFalse(injector.is_killed)

    def test_workout_relative_timestamps_and_reset_epoch(self):
        """TST-TOOL-006.3: Stamps --time with base_epoch_ms + int(round(time_sec * 1000.0)) and resets epoch cleanly."""
        injector = AdbGpsInjector(enabled=True, device_serial="DEVICE123")
        injector.is_registered = True
        injector.base_epoch_ms = 1700000000000

        injector.inject_location(48.0, 11.0, 500.0, 6.0, 45.0, time_sec=0.0)
        injector.inject_location(48.0005, 11.0005, 505.0, 6.0, 45.0, time_sec=5.0)
        self.assertEqual(injector._queue.qsize(), 2)

        fix0 = injector._queue.get_nowait()
        fix1 = injector._queue.get_nowait()
        self.assertEqual(fix0, (48.0, 11.0, 1700000000000))
        self.assertEqual(fix1, (48.0005, 11.0005, 1700000005000))
        self.assertEqual(fix1[2] - fix0[2], 5000)

        # Verify reset_epoch anchors base_epoch_ms so time_sec offset is preserved
        injector.reset_epoch(time_sec=10.0)
        injector.inject_location(48.001, 11.001, 510.0, 6.0, 45.0, time_sec=10.0)
        injector.inject_location(48.0015, 11.0015, 515.0, 6.0, 45.0, time_sec=15.0)
        fix_seek0 = injector._queue.get_nowait()
        fix_seek1 = injector._queue.get_nowait()
        self.assertEqual(fix_seek1[2] - fix_seek0[2], 5000)


class TestHostPreFlightDiagnostics(unittest.TestCase):
    def test_preflight_report_structure(self):
        report = PreFlightReport(
            bt_ok=True, bt_message="BlueZ OK", bt_advice=[],
            adb_ok=True, adb_message="ADB OK", adb_advice=[],
            device_serial="DEVICE123"
        )
        self.assertTrue(report.bt_ok)
        self.assertTrue(report.adb_ok)
        self.assertEqual(report.device_serial, "DEVICE123")

    def test_bluez_service_uuids(self):
        self.assertEqual(CYCLING_POWER_SERVICE_UUID, '00001818-0000-1000-8000-00805f9b34fb')
        self.assertEqual(HEART_RATE_SERVICE_UUID, '0000180d-0000-1000-8000-00805f9b34fb')
        self.assertEqual(BATTERY_SERVICE_UUID, '0000180f-0000-1000-8000-00805f9b34fb')
        self.assertEqual(BATTERY_LEVEL_CHARACTERISTIC_UUID, '00002a19-0000-1000-8000-00805f9b34fb')


class TestCliArguments(unittest.TestCase):
    def test_default_battery_arguments(self):
        parser = build_arg_parser()
        args = parser.parse_args([])
        self.assertEqual(args.hr_battery, 100)
        self.assertEqual(args.power_battery, 100)

    def test_custom_battery_arguments(self):
        parser = build_arg_parser()
        args = parser.parse_args(["--hr-battery", "25", "--power-battery", "8"])
        self.assertEqual(args.hr_battery, 25)
        self.assertEqual(args.power_battery, 8)

    def test_scheduled_kill_cli_arguments(self):
        parser = build_arg_parser()
        args = parser.parse_args([
            "--kill-hr-at", "10",
            "--revive-hr-at", "20",
            "--kill-power-at", "15",
            "--revive-power-at", "25",
            "--kill-gps-at", "12.5",
            "--revive-gps-at", "22.5"
        ])
        self.assertEqual(args.kill_hr_at, 10.0)
        self.assertEqual(args.revive_hr_at, 20.0)
        self.assertEqual(args.kill_power_at, 15.0)
        self.assertEqual(args.revive_power_at, 25.0)
        self.assertEqual(args.kill_gps_at, 12.5)
        self.assertEqual(args.revive_gps_at, 22.5)


class TestScheduledTriggerManager(unittest.TestCase):
    def test_scheduled_triggers_execution(self):
        ble = BleReplayServer(enabled=False)
        gps = AdbGpsInjector(enabled=False)
        mgr = ScheduledTriggerManager(
            ble, gps,
            kill_hr_at=10.0, revive_hr_at=20.0,
            kill_power_at=15.0, revive_power_at=25.0,
            kill_gps_at=12.0, revive_gps_at=22.0
        )

        # t = 5.0 -> nothing triggered
        mgr.evaluate(5.0)
        self.assertFalse(ble.is_hr_killed)
        self.assertFalse(ble.is_power_killed)
        self.assertFalse(gps.is_killed)

        # t = 10.0 -> HR killed
        mgr.evaluate(10.0)
        self.assertTrue(ble.is_hr_killed)
        self.assertFalse(ble.is_power_killed)
        self.assertFalse(gps.is_killed)

        # t = 12.0 -> GPS killed
        mgr.evaluate(12.0)
        self.assertTrue(ble.is_hr_killed)
        self.assertFalse(ble.is_power_killed)
        self.assertTrue(gps.is_killed)

        # t = 15.0 -> Power killed
        mgr.evaluate(15.0)
        self.assertTrue(ble.is_hr_killed)
        self.assertTrue(ble.is_power_killed)
        self.assertTrue(gps.is_killed)

        # t = 20.0 -> HR revived
        mgr.evaluate(20.0)
        self.assertFalse(ble.is_hr_killed)
        self.assertTrue(ble.is_power_killed)
        self.assertTrue(gps.is_killed)

        # t = 22.0 -> GPS revived
        mgr.evaluate(22.0)
        self.assertFalse(ble.is_hr_killed)
        self.assertTrue(ble.is_power_killed)
        self.assertFalse(gps.is_killed)

        # t = 25.0 -> Power revived
        mgr.evaluate(25.0)
        self.assertFalse(ble.is_hr_killed)
        self.assertFalse(ble.is_power_killed)
        self.assertFalse(gps.is_killed)


if __name__ == "__main__":
    unittest.main()
