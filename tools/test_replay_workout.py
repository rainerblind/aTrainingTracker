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
    build_power_payload, build_hr_payload,
    format_duration, haversine_distance, calculate_bearing,
    BleReplayServer, AdbGpsInjector
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
        self.assertEqual(self.engine.progress_ratio, 0.0)

    def test_playback_lifecycle(self):
        self.engine.play()
        self.assertEqual(self.engine.status, "PLAYING")

        self.engine.step()
        self.assertEqual(self.engine.current_index, 1)

        self.engine.pause()
        self.assertEqual(self.engine.status, "PAUSED")

        self.engine.stop()
        self.assertEqual(self.engine.status, "STOPPED")
        self.assertEqual(self.engine.current_index, 0)

    def test_seek_ratio(self):
        self.engine.seek_ratio(0.5)
        # points length 4, index should be 1 or 2
        self.assertIn(self.engine.current_index, [1, 2])

        self.engine.seek_ratio(1.0)
        self.assertEqual(self.engine.current_index, 3)

        self.engine.seek_ratio(0.0)
        self.assertEqual(self.engine.current_index, 0)

    def test_seek_time(self):
        self.engine.seek_time(10.0)
        self.assertEqual(self.engine.current_point.time_sec, 10.0)

    def test_speed_scaling(self):
        self.engine.set_speed(2.0)
        self.assertEqual(self.engine.speed_multiplier, 2.0)
        self.engine.play()
        self.engine.step()
        self.assertEqual(self.engine.current_index, 2)


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


if __name__ == "__main__":
    unittest.main()
