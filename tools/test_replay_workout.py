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
import ast
import struct

from tools.replay_workout import (
    TcxParser, TcxPoint, ReplayEngine,
    build_power_payload, build_hr_payload, build_battery_payload,
    compute_auto_hrv_params,
    format_duration, haversine_distance, calculate_bearing,
    BleReplayServer, AdbGpsInjector,
    HostPreFlightDiagnostics, PreFlightReport,
    CYCLING_POWER_SERVICE_UUID, HEART_RATE_SERVICE_UUID,
    RADAR_SERVICE_UUID, RADAR_LIGHT_CONTROL_UUID,
    RADAR_V1_CHARACTERISTIC_UUID, RADAR_V2_CHARACTERISTIC_UUID,
    BATTERY_SERVICE_UUID, BATTERY_LEVEL_CHARACTERISTIC_UUID,
    build_arg_parser, ScheduledTriggerManager
)
from tools.ble_hr_simulator import RrSynthesizer
from tools.ble_radar_simulator import (
    RadarKinematicsEngine, RadarPayloadBuilder, RadarLightMode,
    RadarTargetState, ThreatLevel, VehicleSizeCategory, LaneState
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

    def test_radar_and_hrv_gui_controls(self):
        """Verifies GUI controls for Radar toggle/presets/badges and HRV regime/artifact buttons (REQ-SIM-003.5)."""
        sample_path = os.path.join(os.path.dirname(__file__), "sample_workout.tcx")
        if not os.path.exists(sample_path):
            sample_path = "tools/sample_workout.tcx"
        points = TcxParser.parse_file(sample_path)
        engine = ReplayEngine(points)
        ble = BleReplayServer(enabled=False)
        gps = AdbGpsInjector(enabled=False)

        window = ReplayGuiWindow(sample_path, engine, ble, gps)

        # 1. Radar Kill/Revive Toggle & Badge
        self.assertIsNotNone(window.btn_toggle_radar)
        self.assertIsNotNone(window.lbl_status_radar_badge)
        self.assertEqual(window.lbl_status_radar_badge.text(), "● Radar Active")
        self.assertEqual(window.btn_toggle_radar.text(), "Kill Radar")

        window.btn_toggle_radar.click()
        self.assertTrue(ble.is_radar_killed)
        self.assertEqual(window.lbl_status_radar_badge.text(), "■ Radar Killed")
        self.assertEqual(window.btn_toggle_radar.text(), "Revive Radar")

        window.btn_toggle_radar.click()
        self.assertFalse(ble.is_radar_killed)
        self.assertEqual(window.lbl_status_radar_badge.text(), "● Radar Active")
        self.assertEqual(window.btn_toggle_radar.text(), "Kill Radar")

        # 2. Radar Light Badge & Mode Handling
        self.assertIsNotNone(window.lbl_radar_light_badge)
        self.assertIn("SOLID", window.lbl_radar_light_badge.text())
        if ble.radar_engine:
            ble.radar_engine.set_light_mode(RadarLightMode.DAY_FLASH)
            window._update_radar_ui()
            self.assertIn("DAY_FLASH", window.lbl_radar_light_badge.text())

        # 3. Radar Preset Buttons
        self.assertIsNotNone(window.btn_radar_car)
        self.assertIsNotNone(window.btn_radar_fast)
        self.assertIsNotNone(window.btn_radar_truck)
        self.assertIsNotNone(window.btn_radar_close)
        self.assertIsNotNone(window.btn_radar_clear)
        self.assertIsNotNone(window.btn_radar_loop)

        # Trigger presets via button clicks
        window.btn_radar_car.click()
        self.assertEqual(len(ble.radar_engine.targets), 1)
        self.assertEqual(ble.radar_engine.targets[0].target_id, 1)

        window.btn_radar_fast.click()
        self.assertEqual(len(ble.radar_engine.targets), 2)

        window.btn_radar_close.click()
        close_targets = [t for t in ble.radar_engine.targets if t.target_id == 4]
        self.assertEqual(len(close_targets), 1)
        self.assertLess(abs(close_targets[0].lateral_m), 1.5)

        window.btn_radar_clear.click()
        self.assertEqual(len(ble.radar_engine.targets), 0)

        # Country loop toggle button
        self.assertFalse(ble.radar_engine.country_loop_active)
        window.btn_radar_loop.click()
        self.assertTrue(ble.radar_engine.country_loop_active)
        window.btn_radar_loop.click()
        self.assertFalse(ble.radar_engine.country_loop_active)

        # 4. HRV Regime Combobox & Badges in Mock Mode
        self.assertIsNotNone(window.combo_hrv_regime)
        self.assertIsNotNone(window.lbl_hrv_rmssd_val)
        self.assertIsNotNone(window.lbl_hrv_a1_val)

        # Switch to Aerobic
        window.combo_hrv_regime.setCurrentIndex(1)
        self.assertEqual(ble.hrv_mode, "aerobic")
        self.assertEqual(window.lbl_hrv_a1_val.text(), "1.00")

        # Switch to Threshold
        window.combo_hrv_regime.setCurrentIndex(2)
        self.assertEqual(ble.hrv_mode, "threshold")
        self.assertEqual(window.lbl_hrv_a1_val.text(), "0.70")

        # Switch to Anaerobic
        window.combo_hrv_regime.setCurrentIndex(3)
        self.assertEqual(ble.hrv_mode, "anaerobic")
        self.assertEqual(window.lbl_hrv_a1_val.text(), "0.45")

        # Switch back to Auto-HRV & verify update via engine update
        window.combo_hrv_regime.setCurrentIndex(0)
        self.assertEqual(ble.hrv_mode, "auto")
        test_pt = TcxPoint(time_iso="2026-11-12T00:00:10Z", time_sec=10.0, lat=48.0, lon=11.0, ele=500.0, dist=100.0, hr=145, watts=200, cadence=90, speed_mps=8.0, bearing=0.0)
        window.on_engine_update(test_pt)
        self.assertEqual(window.lbl_hrv_a1_val.text(), "0.75")
        self.assertEqual(window.lbl_hrv_rmssd_val.text(), "35.0 ms")

        # 5. HRV Artifact Buttons
        self.assertIsNotNone(window.btn_art_ectopic)
        self.assertIsNotNone(window.btn_art_missed)
        self.assertIsNotNone(window.btn_art_extra)
        self.assertIsNotNone(window.btn_art_gap)

        window.btn_art_ectopic.click()
        window.btn_art_missed.click()
        window.btn_art_extra.click()
        window.btn_art_gap.click()
        self.assertTrue(len(ble.rr_synthesizer.artifact_queue) > 0 or ble.rr_synthesizer.gap_ticks_remaining > 0)

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
        self.assertEqual(fix0, (48.0, 11.0, 1700000000000, 6.0))
        self.assertEqual(fix1, (48.0005, 11.0005, 1700000005000, 6.0))
        self.assertEqual(fix1[2] - fix0[2], 5000)

        # Verify reset_epoch anchors base_epoch_ms so time_sec offset is preserved
        injector.reset_epoch(time_sec=10.0)
        injector.inject_location(48.001, 11.001, 510.0, 6.0, 45.0, time_sec=10.0)
        injector.inject_location(48.0015, 11.0015, 515.0, 6.0, 45.0, time_sec=15.0)
        fix_seek0 = injector._queue.get_nowait()
        fix_seek1 = injector._queue.get_nowait()
        self.assertEqual(fix_seek1[2] - fix_seek0[2], 5000)

    def test_build_inject_cmd_with_speed(self):
        """TST-TOOL-007.1: Appends --speed {speed_mps:.2f} when speed_mps is provided and >= 0.0."""
        cmd = AdbGpsInjector.build_inject_cmd("gps", 48.1, 11.5, 1700000000000, accuracy=2.5, speed_mps=5.5)
        self.assertEqual(
            cmd,
            "cmd location providers set-test-provider-location gps --location 48.100000,11.500000 --accuracy 2.5 --time 1700000000000 --speed 5.50"
        )
        self.assertIn("--speed 5.50", cmd)

    def test_build_inject_cmd_backward_compatibility(self):
        """TST-TOOL-007.2: Omits --speed when speed_mps is None or negative, preserving exact Android 14+ syntax."""
        cmd_none = AdbGpsInjector.build_inject_cmd("gps", 48.1, 11.5, 1700000000000, accuracy=2.5, speed_mps=None)
        self.assertEqual(
            cmd_none,
            "cmd location providers set-test-provider-location gps --location 48.100000,11.500000 --accuracy 2.5 --time 1700000000000"
        )
        self.assertNotIn("--speed", cmd_none)

        cmd_neg = AdbGpsInjector.build_inject_cmd("gps", 48.1, 11.5, 1700000000000, accuracy=2.5, speed_mps=-1.0)
        self.assertNotIn("--speed", cmd_neg)

    def test_async_queue_injection_with_speed(self):
        """TST-TOOL-007.3: Forwards 4-tuple (lat, lon, timestamp_ms, speed_mps) through async queue."""
        injector = AdbGpsInjector(enabled=True, device_serial="DEVICE123")
        injector.is_registered = True
        injector.base_epoch_ms = 1700000000000

        injector.inject_location(48.123, 11.456, 500.0, 8.25, 90.0, time_sec=2.5)
        self.assertEqual(injector._queue.qsize(), 1)
        item = injector._queue.get_nowait()
        self.assertEqual(item, (48.123, 11.456, 1700000002500, 8.25))

    def test_speed_fallback_on_unsupported_option(self):
        """TST-TOOL-007.4: Defensively catches 'Unknown option: --speed' from ADB shell, disables speed flag, and retries."""
        injector = AdbGpsInjector(enabled=True, device_serial="DEVICE123")
        injector.is_registered = True

        called_cmds = []

        class MockCompletedProcess:
            def __init__(self, returncode, stdout="", stderr=""):
                self.returncode = returncode
                self.stdout = stdout
                self.stderr = stderr

        def mock_run_adb(args, check=False):
            called_cmds.append(args)
            shell_cmd = args[1]
            if "--speed" in shell_cmd:
                return MockCompletedProcess(255, "", "java.lang.IllegalArgumentException: Unknown option: --speed\n")
            else:
                return MockCompletedProcess(0, "", "")

        injector._run_adb = mock_run_adb

        # Enqueue item with speed
        injector._queue.put_nowait((48.1, 11.5, 1700000000000, 7.5))
        injector._queue.put_nowait(None)

        injector._worker_loop()

        self.assertFalse(injector._supports_speed_flag)
        self.assertEqual(len(called_cmds), 2)
        self.assertIn("--speed 7.50", called_cmds[0][1])
        self.assertNotIn("--speed", called_cmds[1][1])

        # Enqueue second item with speed; should skip --speed immediately since _supports_speed_flag is False
        injector._queue.put_nowait((48.2, 11.6, 1700000001000, 7.5))
        injector._queue.put_nowait(None)
        injector._worker_loop()
        self.assertEqual(len(called_cmds), 3)
        self.assertNotIn("--speed", called_cmds[2][1])

    def test_adb_gps_injector_removes_test_providers_on_pause_stop_and_eof(self):
        """TST-TRK-017.1: Verifies startup cleanup, lazy add-test-provider on PLAYING, and immediate remove-test-provider on PAUSED, STOPPED, kill, and EOF."""
        executed_cmds = []

        class RecordingInjector(AdbGpsInjector):
            def _run_adb(self, cmd_args, check=False):
                executed_cmds.append(" ".join(cmd_args))
                return None

        injector = RecordingInjector(enabled=True, device_serial="DEVICE123")
        points = [
            TcxPoint("2026-10-10T10:00:00Z", 0.0, 48.0, 11.0, 500.0, 0.0, 120, 80, 180, 6.0, 45.0),
            TcxPoint("2026-10-10T10:00:05Z", 5.0, 48.001, 11.001, 505.0, 30.0, 130, 85, 200, 6.0, 45.0),
        ]
        engine = ReplayEngine(points, gps=injector)

        # 1. Startup setup() grants mock_location and clears leftover providers while keeping is_registered=False in STOPPED
        injector.setup()
        self.assertFalse(injector.is_registered)
        self.assertTrue(any("remove-test-provider gps" in c for c in executed_cmds))
        self.assertFalse(any("add-test-provider gps" in c for c in executed_cmds))
        executed_cmds.clear()

        # 2. Entering PLAYING lazily registers test providers
        engine.play()
        self.assertTrue(injector.is_registered)
        self.assertTrue(any("add-test-provider gps" in c and "add-test-provider fused" in c for c in executed_cmds))
        executed_cmds.clear()

        # 3. Pausing immediately removes test providers
        engine.pause()
        self.assertFalse(injector.is_registered)
        self.assertTrue(any("remove-test-provider gps" in c and "remove-test-provider fused" in c for c in executed_cmds))
        executed_cmds.clear()

        # 4. Resuming PLAYING re-registers test providers, and stopping removes them
        engine.play()
        self.assertTrue(injector.is_registered)
        executed_cmds.clear()

        engine.stop()
        self.assertFalse(injector.is_registered)
        self.assertTrue(any("remove-test-provider gps" in c for c in executed_cmds))
        executed_cmds.clear()

        # 5. Stepping to timeline EOF automatically transitions to PAUSED and removes test providers
        engine.play()
        self.assertTrue(injector.is_registered)
        executed_cmds.clear()

        engine.step(dt_sec=5.0)
        self.assertEqual(engine.status, "PAUSED")
        self.assertFalse(injector.is_registered)
        self.assertTrue(any("remove-test-provider gps" in c and "remove-test-provider network" in c for c in executed_cmds))
        executed_cmds.clear()

        # 6. Calling kill() while PLAYING immediately removes test providers; revive() re-registers them
        engine.stop()
        engine.play()
        self.assertTrue(injector.is_registered)
        executed_cmds.clear()

        injector.kill()
        self.assertFalse(injector.is_registered)
        self.assertTrue(any("remove-test-provider gps" in c and "remove-test-provider fused" in c for c in executed_cmds))
        executed_cmds.clear()

        injector.revive()
        self.assertTrue(injector.is_registered)
        self.assertTrue(any("add-test-provider gps" in c for c in executed_cmds))

        injector.teardown()


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
        self.assertFalse(args.cleanup)

    def test_cleanup_cli_argument(self):
        parser = build_arg_parser()
        args = parser.parse_args(["--cleanup"])
        self.assertTrue(args.cleanup)

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

    def test_speed_injection_cli_arguments(self):
        parser = build_arg_parser()
        args_default = parser.parse_args([])
        self.assertTrue(args_default.speed_injection)

        args_disabled = parser.parse_args(["--no-speed-injection"])
        self.assertFalse(args_disabled.speed_injection)

        args_enabled = parser.parse_args(["--speed-injection"])
        self.assertTrue(args_enabled.speed_injection)


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

    def test_scheduled_radar_triggers(self):
        ble = BleReplayServer(enabled=False)
        gps = AdbGpsInjector(enabled=False)
        mgr = ScheduledTriggerManager(
            ble, gps,
            kill_radar_at=10.0, revive_radar_at=20.0
        )

        mgr.evaluate(5.0)
        self.assertFalse(ble.is_radar_killed)

        mgr.evaluate(10.0)
        self.assertTrue(ble.is_radar_killed)

        mgr.evaluate(15.0)
        self.assertTrue(ble.is_radar_killed)

        mgr.evaluate(20.0)
        self.assertFalse(ble.is_radar_killed)


# ==============================================================================
# ATT-6677: BLE HRV Synthesis & Garmin Varia Radar Test Suites (TST-SIM-003)
# ==============================================================================

class TestHrPayloadPackingAndHrvCurve(unittest.TestCase):
    """Verifies BLE 0x2A37 Bit 4 RR packing and Auto-HRV intensity mapping (TST-SIM-003.1 & .2)."""

    def test_build_hr_payload_rr_packing(self):
        # 140 BPM, RR raw intervals: 800 (0x0320) and 850 (0x0352)
        payload = build_hr_payload(140, rr_raw_list=[800, 850], enable_rr=True)
        self.assertEqual(len(payload), 6)
        # Flag byte bit 4 (0x10) set
        self.assertEqual(payload[0], 0x10)
        # BPM byte
        self.assertEqual(payload[1], 140)
        # RR1: 800 -> 0x20, 0x03
        self.assertEqual(payload[2], 0x20)
        self.assertEqual(payload[3], 0x03)
        # RR2: 850 -> 0x52, 0x03
        self.assertEqual(payload[4], 0x52)
        self.assertEqual(payload[5], 0x03)

    def test_build_hr_payload_rr_disabled(self):
        # When enable_rr=False, Bit 4 is NOT set and payload is legacy 2 bytes
        payload = build_hr_payload(140, rr_raw_list=[800, 850], enable_rr=False)
        self.assertEqual(len(payload), 2)
        self.assertEqual(payload[0], 0x00)
        self.assertEqual(payload[1], 140)

        # When rr_raw_list is None or empty
        payload_none = build_hr_payload(140, rr_raw_list=None, enable_rr=True)
        self.assertEqual(payload_none, [0x00, 140])
        payload_empty = build_hr_payload(140, rr_raw_list=[], enable_rr=True)
        self.assertEqual(payload_empty, [0x00, 140])

    def test_build_hr_payload_raw_clamping(self):
        payload = build_hr_payload(140, rr_raw_list=[70000, -10], enable_rr=True)
        self.assertEqual(len(payload), 6)
        # 70000 clamped to 65535 (0xFFFF)
        self.assertEqual(payload[2], 0xFF)
        self.assertEqual(payload[3], 0xFF)
        # -10 clamped to 0 (0x0000)
        self.assertEqual(payload[4], 0x00)
        self.assertEqual(payload[5], 0x00)

    def test_compute_auto_hrv_params_curve(self):
        # BPM <= 120: a1 = 1.05, rmssd = 60.0
        a1, rmssd = compute_auto_hrv_params(90.0)
        self.assertEqual(a1, 1.05)
        self.assertEqual(rmssd, 60.0)

        a1, rmssd = compute_auto_hrv_params(120.0)
        self.assertEqual(a1, 1.05)
        self.assertEqual(rmssd, 60.0)

        # BPM = 145 (aerobic HRVT1 threshold): a1 = 0.75, rmssd = 35.0
        a1, rmssd = compute_auto_hrv_params(145.0)
        self.assertEqual(a1, 0.75)
        self.assertEqual(rmssd, 35.0)

        # BPM = 165 (anaerobic HRVT2 threshold): a1 = 0.50, rmssd = 15.0
        a1, rmssd = compute_auto_hrv_params(165.0)
        self.assertEqual(a1, 0.50)
        self.assertEqual(rmssd, 15.0)

        # BPM >= 170: a1 = 0.50, rmssd = 10.0
        a1, rmssd = compute_auto_hrv_params(175.0)
        self.assertEqual(a1, 0.50)
        self.assertEqual(rmssd, 10.0)

        a1, rmssd = compute_auto_hrv_params(200.0)
        self.assertEqual(a1, 0.50)
        self.assertEqual(rmssd, 10.0)

    def test_compute_auto_hrv_params_monotonicity(self):
        prev_a1, prev_rmssd = 10.0, 1000.0
        for bpm in range(100, 190, 2):
            a1, rmssd = compute_auto_hrv_params(float(bpm))
            self.assertLessEqual(a1, prev_a1 + 1e-6)
            self.assertLessEqual(rmssd, prev_rmssd + 1e-6)
            prev_a1, prev_rmssd = a1, rmssd

    def test_phase_accumulator_60s_drift(self):
        synth = RrSynthesizer(bpm=135, rmssd=35.0, regime='aerobic')
        total_emitted_ms = 0.0
        for _ in range(60):
            raws = synth.tick_1hz(1000.0)
            for raw in raws:
                total_emitted_ms += raw * 1000.0 / 1024.0

        # Phase tracking invariant: total elapsed time + residual phase tracks 60,000ms within <= 50ms
        effective_elapsed = total_emitted_ms + synth.phase_ms + synth.quant_error_ms
        drift_ms = abs(effective_elapsed - 60000.0)
        self.assertLessEqual(drift_ms, 50.0, f"60s cumulative RR drift exceeded: {drift_ms:.2f} ms > 50 ms")


class TestRadarProtocolAndService(unittest.TestCase):
    """Verifies Garmin Varia Radar UUIDs, Light Mode enum, and payload serialization (TST-SIM-003.3)."""

    def test_radar_uuids(self):
        self.assertEqual(RADAR_SERVICE_UUID, "6a4e3200-667b-11e3-949a-0800200c9a66")
        self.assertEqual(RADAR_LIGHT_CONTROL_UUID, "6a4e3202-667b-11e3-949a-0800200c9a66")
        self.assertEqual(RADAR_V1_CHARACTERISTIC_UUID, "6a4e3203-667b-11e3-949a-0800200c9a66")
        self.assertEqual(RADAR_V2_CHARACTERISTIC_UUID, "6a4e3204-667b-11e3-949a-0800200c9a66")

    def test_radar_light_mode_enum_and_validation(self):
        self.assertEqual(RadarLightMode.OFF, 0x00)
        self.assertEqual(RadarLightMode.SOLID, 0x01)
        self.assertEqual(RadarLightMode.PELOTON, 0x05)
        self.assertEqual(RadarLightMode.NIGHT_FLASH, 0x06)
        self.assertEqual(RadarLightMode.DAY_FLASH, 0x07)

        self.assertTrue(RadarLightMode.is_valid(0x01))
        self.assertTrue(RadarLightMode.is_valid(0x07))
        self.assertFalse(RadarLightMode.is_valid(0x02))
        self.assertFalse(RadarLightMode.is_valid(0xFF))

        self.assertEqual(RadarLightMode.name_of(0x01), "SOLID")
        self.assertEqual(RadarLightMode.name_of(0x07), "DAY_FLASH")

    def test_radar_v2_payload_serialization(self):
        target = RadarTargetState(
            target_id=1, range_m=120.5, speed_mps=15.2, lateral_m=-1.8,
            threat_level=ThreatLevel.APPROACHING,
            size_category=VehicleSizeCategory.CAR,
            lane_state=LaneState.PASSING_LANE
        )
        payload = RadarPayloadBuilder.build_v2_payload([target])
        # Header byte 0x01 + 8 stride bytes = 9 bytes
        self.assertEqual(len(payload), 9)
        self.assertEqual(payload[0], 0x01)
        self.assertEqual(payload[1], 1)  # Target ID
        # Range 120.5m -> 1205 raw (0x04B5) -> 0xB5, 0x04
        self.assertEqual(payload[2], 0xB5)
        self.assertEqual(payload[3], 0x04)

    def test_radar_v1_payload_serialization(self):
        target = RadarTargetState(
            target_id=1, range_m=120.0, speed_mps=12.0, lateral_m=-1.8,
            threat_level=ThreatLevel.APPROACHING,
            size_category=VehicleSizeCategory.CAR,
            lane_state=LaneState.PASSING_LANE
        )
        payload = RadarPayloadBuilder.build_v1_payload([target])
        # Header byte 0x00 + 3 stride bytes = 4 bytes
        self.assertEqual(len(payload), 4)
        self.assertEqual(payload[0], 0x00)
        self.assertEqual(payload[1], 1)  # Target ID
        self.assertEqual(payload[2], 120)  # Range meters (uint8)

    def test_radar_empty_payloads(self):
        v2_empty = RadarPayloadBuilder.build_v2_payload([])
        self.assertEqual(v2_empty, [0x01])
        v1_empty = RadarPayloadBuilder.build_v1_payload([])
        self.assertEqual(v1_empty, [0x00])


class TestRadarKinematicsEngine(unittest.TestCase):
    """Verifies 5 Hz kinematics, presets, and overtake removal (TST-SIM-003.4)."""

    def test_spawn_presets(self):
        engine = RadarKinematicsEngine(tick_rate_hz=5.0)

        # Preset 'c' (Car)
        engine.spawn_preset('c')
        self.assertEqual(len(engine.targets), 1)
        self.assertEqual(engine.targets[0].target_id, 1)
        self.assertEqual(engine.targets[0].range_m, 140.0)

        # Preset 'f' (Fast car)
        engine.spawn_preset('f')
        self.assertEqual(len(engine.targets), 2)
        fast_t = [t for t in engine.targets if t.target_id == 2][0]
        self.assertEqual(fast_t.speed_mps, 22.0)

        # Preset 't' (Truck)
        engine.spawn_preset('t')
        self.assertEqual(len(engine.targets), 3)

        # Preset 'p' (Close pass)
        engine.spawn_preset('p')
        close_t = [t for t in engine.targets if t.target_id == 4][0]
        self.assertLess(abs(close_t.lateral_m), 1.5)

        # Clear '0'
        engine.spawn_preset('0')
        self.assertEqual(len(engine.targets), 0)

    def test_closing_range_and_overtake_removal(self):
        engine = RadarKinematicsEngine(tick_rate_hz=5.0)
        engine.spawn_preset('c')  # 140m at 12m/s
        self.assertEqual(len(engine.targets), 1)

        # 1 tick of dt=0.2s -> closes by 12 * 0.2 = 2.4m -> range = 137.6m
        engine.tick(0.2)
        self.assertAlmostEqual(engine.targets[0].range_m, 137.6, places=2)

        # Large tick to pass rider (overtake completed)
        engine.tick(15.0)
        self.assertEqual(len(engine.targets), 0)

    def test_watchdog_starvation(self):
        engine = RadarKinematicsEngine(tick_rate_hz=5.0)
        engine.spawn_preset('c')
        engine.trigger_watchdog_starvation(duration_sec=1.0)  # 5 ticks

        self.assertTrue(engine.is_starving())
        for _ in range(5):
            res = engine.tick()
            self.assertIsNone(res)

        self.assertFalse(engine.is_starving())
        res = engine.tick()
        self.assertIsNotNone(res)


class TestBleReplayServerRadarAndHrv(unittest.TestCase):
    """Verifies BleReplayServer radar controls and HRV artifact injection (REQ-SIM-003.2 & .3)."""

    def test_server_initialization_defaults(self):
        server = BleReplayServer(enabled=False)
        self.assertTrue(server.enable_rr)
        self.assertTrue(server.enable_radar)
        self.assertEqual(server.hrv_regime, 'auto')
        self.assertIsNotNone(server.radar_engine)
        self.assertFalse(server.is_radar_killed)

    def test_server_radar_kill_and_revive(self):
        server = BleReplayServer(enabled=False)
        self.assertFalse(server.is_radar_killed)

        server.kill_radar()
        self.assertTrue(server.is_radar_killed)

        server.toggle_radar()
        self.assertFalse(server.is_radar_killed)

        server.toggle_radar()
        self.assertTrue(server.is_radar_killed)

        server.revive_radar()
        self.assertFalse(server.is_radar_killed)

    def test_server_hrv_regime_and_artifacts(self):
        server = BleReplayServer(enabled=False)
        server.set_hrv_mode('threshold')
        self.assertEqual(server.hrv_regime, 'threshold')

        # Artifact injection triggers should execute cleanly
        server.inject_hrv_artifact('ectopic')
        server.inject_hrv_artifact('missed')
        server.inject_hrv_artifact('extra')
        server.inject_hrv_artifact('gap')

    def test_server_mock_mode_hrv_telemetry_progression(self):
        """Verifies HRV parameters update in mock mode (--no-ble) without BlueZ (Finding 4)."""
        server = BleReplayServer(enabled=False)
        self.assertIsNotNone(server.rr_synthesizer)

        # In Auto-HRV mode, update telemetry with HR=145 (aerobic HRVT1)
        server.update_telemetry(watts=200, cadence=90, hr_bpm=145)
        self.assertEqual(server.current_hrv_a1, 0.75)
        self.assertEqual(server.current_hrv_rmssd, 35.0)

        # Update telemetry with HR=165 (anaerobic HRVT2)
        server.update_telemetry(watts=250, cadence=95, hr_bpm=165)
        self.assertEqual(server.current_hrv_a1, 0.50)
        self.assertEqual(server.current_hrv_rmssd, 15.0)

        # Switch to threshold regime
        server.set_hrv_mode('threshold')
        self.assertEqual(server.hrv_mode, 'threshold')
        self.assertEqual(server.current_hrv_a1, 0.70)

        # Artifact injection adds to queue in mock mode
        server.inject_hrv_artifact('ectopic')
        self.assertEqual(len(server.rr_synthesizer.artifact_queue), 2)


class TestCliParserAndStdLibCompliance(unittest.TestCase):
    """Verifies CLI argument parser options and stdlib compliance (TST-SIM-003.5 / TST-PRO-005.6)."""

    def test_cli_parser_radar_and_hrv_options(self):
        parser = build_arg_parser()
        args = parser.parse_args([])
        self.assertFalse(args.no_rr)
        self.assertFalse(args.no_radar)
        self.assertTrue(args.radar)
        self.assertEqual(args.hrv_regime, 'auto')

        args_custom = parser.parse_args([
            "--no-rr",
            "--no-radar",
            "--hrv-regime", "anaerobic",
            "--kill-radar-at", "30.0",
            "--revive-radar-at", "60.0"
        ])
        self.assertTrue(args_custom.no_rr)
        self.assertTrue(args_custom.no_radar)
        self.assertEqual(args_custom.hrv_regime, "anaerobic")
        self.assertEqual(args_custom.kill_radar_at, 30.0)
        self.assertEqual(args_custom.revive_radar_at, 60.0)

    def test_python_stdlib_compliance(self):
        """Validates zero disallowed pip packages in tools/replay_workout.py (TST-PRO-005.6)."""
        replay_script = os.path.join(os.path.dirname(__file__), "replay_workout.py")
        with open(replay_script, "r", encoding="utf-8") as f:
            tree = ast.parse(f.read(), filename="replay_workout.py")

        disallowed_modules = {
            "numpy", "scipy", "pandas", "requests", "matplotlib", "sklearn", "torch", "seaborn"
        }
        imported_modules = set()
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    imported_modules.add(alias.name.split('.')[0])
            elif isinstance(node, ast.ImportFrom):
                if node.module:
                    imported_modules.add(node.module.split('.')[0])

        violations = imported_modules.intersection(disallowed_modules)
        self.assertEqual(len(violations), 0, f"Disallowed external pip libraries found: {violations}")


if __name__ == "__main__":
    unittest.main()
