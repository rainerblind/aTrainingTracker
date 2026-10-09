#!/usr/bin/env python3
"""
tools/test_fake_gps.py - Unit Tests for ATT-2467 Fake GPS Simulation Tool
========================================================================
Tests geodesic math, bearing calculations, GPX parsing, simulation physics,
speed scaling, scrubbing, and off-route deviation.
"""

import unittest
import math
import os
import tempfile
from tools.fake_gps import (
    haversine_distance,
    calculate_bearing,
    compute_destination_point,
    compute_off_route_point,
    heading_to_cardinal,
    parse_gpx_file,
    get_sample_route,
    GpxPoint,
    GpxSimulationEngine,
)


class TestGeodesicMath(unittest.TestCase):

    def test_haversine_known_distance(self):
        # Marienplatz to Odeonsplatz in Munich (~850m)
        lat1, lon1 = 48.137154, 11.576124
        lat2, lon2 = 48.144421, 11.577663
        dist = haversine_distance(lat1, lon1, lat2, lon2)
        # Should be between 800 and 900 meters
        self.assertTrue(800.0 < dist < 900.0, f"Unexpected distance: {dist}")

    def test_haversine_zero_distance(self):
        dist = haversine_distance(48.0, 11.0, 48.0, 11.0)
        self.assertAlmostEqual(dist, 0.0, places=3)

    def test_calculate_bearing_cardinals(self):
        # Directly North
        b_north = calculate_bearing(48.0, 11.0, 49.0, 11.0)
        self.assertAlmostEqual(b_north, 0.0, delta=1.0)

        # Directly East
        b_east = calculate_bearing(48.0, 11.0, 48.0, 12.0)
        self.assertAlmostEqual(b_east, 90.0, delta=1.0)

        # Directly South
        b_south = calculate_bearing(48.0, 11.0, 47.0, 11.0)
        self.assertAlmostEqual(b_south, 180.0, delta=1.0)

        # Directly West
        b_west = calculate_bearing(48.0, 11.0, 48.0, 10.0)
        self.assertAlmostEqual(b_west, 270.0, delta=1.0)

    def test_heading_to_cardinal(self):
        self.assertEqual(heading_to_cardinal(0.0), "N")
        self.assertEqual(heading_to_cardinal(45.0), "NE")
        self.assertEqual(heading_to_cardinal(90.0), "E")
        self.assertEqual(heading_to_cardinal(135.0), "SE")
        self.assertEqual(heading_to_cardinal(180.0), "S")
        self.assertEqual(heading_to_cardinal(225.0), "SW")
        self.assertEqual(heading_to_cardinal(270.0), "W")
        self.assertEqual(heading_to_cardinal(315.0), "NW")
        self.assertEqual(heading_to_cardinal(359.0), "N")

    def test_compute_off_route_point_displacement(self):
        lat, lon = 48.137, 11.576
        forward_bearing = 0.0  # Heading North
        # Deviating 75 meters perpendicular (+90 deg -> East)
        off_lat, off_lon = compute_off_route_point(lat, lon, forward_bearing, offset_meters=75.0)

        dist = haversine_distance(lat, lon, off_lat, off_lon)
        self.assertAlmostEqual(dist, 75.0, delta=0.5)

        bearing_to_offset = calculate_bearing(lat, lon, off_lat, off_lon)
        self.assertAlmostEqual(bearing_to_offset, 90.0, delta=1.0)


class TestGpxParsingAndSamples(unittest.TestCase):

    def test_sample_routes(self):
        for name in ("munich", "fork", "straight"):
            route_name, points = get_sample_route(name)
            self.assertTrue(len(points) >= 5, f"Route {name} had too few points: {len(points)}")
            self.assertTrue(len(route_name) > 0)
            # Verify coordinates are valid
            for p in points:
                self.assertTrue(-90.0 <= p.lat <= 90.0)
                self.assertTrue(-180.0 <= p.lon <= 180.0)

    def test_parse_gpx_valid_xml(self):
        gpx_xml = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="TestRunner" xmlns="http://www.topografix.com/GPX/1/1">
  <trk>
    <name>Test Track</name>
    <trkseg>
      <trkpt lat="48.1371" lon="11.5761">
        <ele>520.5</ele>
        <time>2026-10-09T12:00:00Z</time>
      </trkpt>
      <trkpt lat="48.1380" lon="11.5770">
        <ele>522.0</ele>
        <time>2026-10-09T12:01:00Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>
"""
        with tempfile.NamedTemporaryFile("w", suffix=".gpx", delete=False) as tmp:
            tmp.write(gpx_xml)
            tmp_path = tmp.name

        try:
            points = parse_gpx_file(tmp_path)
            self.assertEqual(len(points), 2)
            self.assertAlmostEqual(points[0].lat, 48.1371)
            self.assertAlmostEqual(points[0].lon, 11.5761)
            self.assertAlmostEqual(points[0].ele, 520.5)
            self.assertEqual(points[0].time, "2026-10-09T12:00:00Z")
            self.assertAlmostEqual(points[1].lat, 48.1380)
            self.assertAlmostEqual(points[1].ele, 522.0)
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)


class TestGpxSimulationEngine(unittest.TestCase):

    def setUp(self):
        # Simple 1000m route North
        p0 = GpxPoint(48.000, 11.000, 500.0)
        p1 = GpxPoint(48.009, 11.000, 550.0)  # ~1000m North
        self.engine = GpxSimulationEngine("Test Sprint", [p0, p1], base_speed_kmh=36.0)
        # 36 km/h = 10 m/s

    def test_initial_state(self):
        self.assertEqual(self.engine.status, "PLAYING")
        self.assertAlmostEqual(self.engine.current_distance, 0.0)
        self.assertAlmostEqual(self.engine.effective_speed_kmh, 36.0)
        self.assertAlmostEqual(self.engine.effective_speed_mps, 10.0)

    def test_single_step_advancement(self):
        # 10 m/s for 1.0 second = 10.0 meters
        lat, lon, bearing, ele = self.engine.step(1.0)
        self.assertAlmostEqual(self.engine.current_distance, 10.0, delta=0.1)
        self.assertAlmostEqual(self.engine.elapsed_time_sec, 1.0)
        self.assertAlmostEqual(bearing, 0.0, delta=1.0)  # Headed North
        self.assertTrue(ele >= 500.0)

    def test_speed_multipliers(self):
        # Toggle 5x speed
        self.engine.speed_multiplier = 5.0
        self.assertAlmostEqual(self.engine.effective_speed_kmh, 180.0)
        self.assertAlmostEqual(self.engine.effective_speed_mps, 50.0)

        # Step 2 seconds at 50 m/s = 100 meters
        self.engine.step(2.0)
        self.assertAlmostEqual(self.engine.current_distance, 100.0, delta=0.5)

    def test_seeking_progress(self):
        # Seek to 50%
        self.engine.seek_progress(0.5)
        self.assertAlmostEqual(self.engine.progress_ratio, 0.5, delta=0.01)
        expected_dist = self.engine.total_distance * 0.5
        self.assertAlmostEqual(self.engine.current_distance, expected_dist, delta=0.5)
        self.assertAlmostEqual(self.engine.current_ele, 525.0, delta=1.0)

    def test_off_route_deviation(self):
        self.engine.is_deviating = True
        self.engine.deviation_offset_meters = 75.0

        lat, lon, bearing, ele = self.engine.step(1.0)
        # The deviation point should be ~75m east of the track line (lon 11.000)
        d_from_center = haversine_distance(lat, 11.000, lat, lon)
        self.assertAlmostEqual(d_from_center, 75.0, delta=1.0)

    def test_route_completion_stops_engine(self):
        # Step beyond total distance
        seconds_to_finish = (self.engine.total_distance / self.engine.effective_speed_mps) + 5.0
        self.engine.step(seconds_to_finish)
        self.assertEqual(self.engine.status, "STOPPED")
        self.assertAlmostEqual(self.engine.current_distance, self.engine.total_distance)
        self.assertAlmostEqual(self.engine.progress_ratio, 1.0)


if __name__ == "__main__":
    unittest.main()
