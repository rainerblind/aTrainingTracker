#!/usr/bin/env python3
"""
tools/test_extract_pixel_field_evidence.py
Unit tests for Google Pixel 10 field verification telemetry extraction & gap coaching
(ASPICE SYS.4 BP3/BP5, SYS.5 BP3/BP5, SWE.6 BP3/BP5, REQ-PRO-098 / TST-PRO-098).
"""

import json
import os
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from extract_pixel_field_evidence import (
    DEFAULT_PIXEL_10_SERIAL,
    WorkoutSession,
    evaluate_verification_coverage,
    generate_mock_pixel10_workouts,
    parse_sqlite_database,
    parse_tcx_file,
    pull_device_evidence_adb,
    render_markdown_gap_report,
    update_sys_verification_record_markdown,
)


class TestExtractPixelFieldEvidence(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_workout_session_to_dict(self):
        """TST-PRO-098.3: Verify WorkoutSession properties and dict serialization."""
        # Session with explicit metrics
        s = WorkoutSession(
            session_id="TEST-001",
            start_time="2026-11-16T10:00:00",
            duration_sec=3600.0,
            distance_m=30000.0,
            has_hrm=True,
            has_power=True,
            has_cadence=True,
            has_radar=True,
            has_gnss=True,
            has_barometer=True,
            elevation_gain_m=350.0,
            sample_count=3580,
            sample_loss_pct=0.2,
            battery_drain_pct_hr=5.5,
            sport_type="ROAD_CYCLING",
            environment="E2 Rural / E3 Climb",
            fit_valid=True,
            cloud_synced=True,
        )
        d = s.to_dict()
        self.assertEqual(d["session_id"], "TEST-001")
        self.assertEqual(d["duration_sec"], 3600.0)
        self.assertTrue(d["has_hrm"])
        self.assertTrue(d["has_radar"])
        self.assertEqual(d["elevation_gain_m"], 350.0)

        # Session with defaults (unmeasured metrics must default to None per Finding K1)
        s_default = WorkoutSession(
            session_id="TEST-DEF",
            start_time="2026-11-16T11:00:00",
        )
        self.assertIsNone(s_default.sample_loss_pct)
        self.assertIsNone(s_default.battery_drain_pct_hr)
        self.assertIsNone(s_default.max_rf_dropout_sec)
        self.assertIsNone(s_default.fit_valid)
        self.assertIsNone(s_default.cloud_synced)
        self.assertFalse(s_default.has_radar)

    def test_evaluate_verification_coverage_with_mock_data(self):
        """TST-PRO-098.3: Evaluate matrix coverage and gap generation with authentic mock rides."""
        sessions = generate_mock_pixel10_workouts()
        self.assertGreaterEqual(len(sessions), 2)

        matrix, gap_summary = evaluate_verification_coverage(sessions)

        self.assertEqual(gap_summary["covered_cases_count"], 8)
        self.assertEqual(gap_summary["open_cases_count"], 0)
        self.assertTrue(matrix["SYS-VRF-001"]["covered"])
        self.assertEqual(matrix["SYS-VRF-001"]["result"], "EXTRACTED_AWAITING_PO_ATTESTATION")
        self.assertTrue(matrix["SYS-VRF-006"]["covered"])
        self.assertEqual(matrix["SYS-VRF-006"]["result"], "EXTRACTED_AWAITING_PO_ATTESTATION")

        # Check environment gap analysis (mock data has E2, E3, E4, missing E1, E5)
        self.assertIn("E1 Urban", gap_summary["missing_environments"])
        self.assertTrue(len(gap_summary["coaching_tips"]) > 0)

    def test_evaluate_verification_coverage_empty(self):
        """TST-PRO-098.3: Verify behavior when zero sessions exist (all cases open)."""
        matrix, gap_summary = evaluate_verification_coverage([])
        self.assertEqual(gap_summary["covered_cases_count"], 0)
        self.assertEqual(gap_summary["open_cases_count"], 8)
        for i in range(1, 9):
            cid = f"SYS-VRF-00{i}"
            self.assertFalse(matrix[cid]["covered"])
            self.assertEqual(matrix[cid]["result"], "OPEN")
        self.assertEqual(len(gap_summary["missing_environments"]), 5)

    def test_parse_tcx_file(self):
        """TST-PRO-098.3: Parse authentic TCX XML export into a WorkoutSession."""
        tcx_content = """<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
  <Activities>
    <Activity Sport="Biking">
      <Id>2026-11-16T09:00:00Z</Id>
      <Lap StartTime="2026-11-16T09:00:00Z">
        <Track>
          <Trackpoint>
            <Time>2026-11-16T09:00:00Z</Time>
            <Position>
              <LatitudeDegrees>48.137154</LatitudeDegrees>
              <LongitudeDegrees>11.576124</LongitudeDegrees>
            </Position>
            <AltitudeMeters>515.0</AltitudeMeters>
            <HeartRateBpm><Value>142</Value></HeartRateBpm>
            <Cadence>85</Cadence>
          </Trackpoint>
          <Trackpoint>
            <Time>2026-11-16T09:00:01Z</Time>
            <Position>
              <LatitudeDegrees>48.137160</LatitudeDegrees>
              <LongitudeDegrees>11.576130</LongitudeDegrees>
            </Position>
            <AltitudeMeters>517.5</AltitudeMeters>
            <HeartRateBpm><Value>145</Value></HeartRateBpm>
            <Cadence>86</Cadence>
          </Trackpoint>
        </Track>
      </Lap>
    </Activity>
  </Activities>
</TrainingCenterDatabase>
"""
        tcx_path = os.path.join(self.test_dir, "workout.tcx")
        with open(tcx_path, "w", encoding="utf-8") as f:
            f.write(tcx_content)

        session = parse_tcx_file(tcx_path)
        self.assertIsNotNone(session)
        self.assertEqual(session.session_id, "2026-11-16T09:00:00Z")
        self.assertTrue(session.has_hrm)
        self.assertTrue(session.has_cadence)
        self.assertTrue(session.has_gnss)
        self.assertTrue(session.has_barometer)
        self.assertFalse(session.has_radar)
        self.assertIsNone(session.sample_loss_pct)
        self.assertIsNone(session.battery_drain_pct_hr)
        self.assertIsNone(session.fit_valid)
        self.assertGreater(session.elevation_gain_m, 2.0)

    def test_parse_sqlite_database(self):
        """TST-PRO-098.3: Query SQLite WorkoutSummaries.db using authentic schema from WorkoutSummariesDatabaseManager."""
        db_path = os.path.join(self.test_dir, "WorkoutSummaries.db")
        conn = sqlite3.connect(db_path)
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE WorkoutSummaries (
                _id INTEGER PRIMARY KEY,
                timeStart TEXT,
                timeActive_s REAL,
                distanceTotal_m REAL,
                ascending REAL,
                Sport TEXT,
                uploadToStrava INTEGER DEFAULT -1
            )
        """)
        cur.execute("""
            CREATE TABLE ExtremumValues (
                _id INTEGER PRIMARY KEY,
                workoutID INTEGER,
                sensorType TEXT,
                extremumType TEXT,
                value REAL
            )
        """)
        # Session 101: Uploaded to Strava (1), has HR, Power, Cadence, Radar
        cur.execute("""
            INSERT INTO WorkoutSummaries VALUES (
                101, '2026-11-16T08:00:00', 5400.0, 42000.0, 450.0, 'BICYCLING', 1
            )
        """)
        cur.executemany("""
            INSERT INTO ExtremumValues (workoutID, sensorType, extremumType, value) VALUES (?, ?, ?, ?)
        """, [
            (101, "HR", "AVG", 150.0),
            (101, "POWER", "AVG", 210.0),
            (101, "CADENCE", "AVG", 88.0),
            (101, "RADAR_THREAT_COUNT", "MAX", 8.0),
        ])

        # Session 102: Not uploaded (-1), only HR, no power, no cadence, no radar
        cur.execute("""
            INSERT INTO WorkoutSummaries VALUES (
                102, '2026-11-16T12:00:00', 1800.0, 5000.0, 20.0, 'RUNNING', -1
            )
        """)
        cur.execute("""
            INSERT INTO ExtremumValues (workoutID, sensorType, extremumType, value) VALUES (?, ?, ?, ?)
        """, (102, "HR", "AVG", 140.0))

        conn.commit()
        conn.close()

        sessions = parse_sqlite_database(db_path)
        self.assertEqual(len(sessions), 2)

        # Sorted DESC by ID: s102 is first, s101 is second
        s102 = sessions[0]
        self.assertEqual(s102.session_id, "DB-SES-102")
        self.assertEqual(s102.duration_sec, 1800.0)
        self.assertTrue(s102.has_hrm)
        self.assertFalse(s102.has_power)
        self.assertFalse(s102.has_cadence)
        self.assertFalse(s102.has_radar)
        self.assertIsNone(s102.cloud_synced)
        self.assertIsNone(s102.sample_loss_pct)
        self.assertIsNone(s102.battery_drain_pct_hr)
        self.assertIsNone(s102.fit_valid)

        s101 = sessions[1]
        self.assertEqual(s101.session_id, "DB-SES-101")
        self.assertEqual(s101.duration_sec, 5400.0)
        self.assertTrue(s101.has_hrm)
        self.assertTrue(s101.has_power)
        self.assertTrue(s101.has_cadence)
        self.assertTrue(s101.has_radar)
        self.assertTrue(s101.cloud_synced)
        self.assertEqual(s101.elevation_gain_m, 450.0)
        self.assertIsNone(s101.sample_loss_pct)
        self.assertIsNone(s101.battery_drain_pct_hr)
        self.assertIsNone(s101.fit_valid)

    @patch("subprocess.run")
    def test_pull_device_evidence_adb_exec_out(self, mock_run):
        """TST-PRO-098.3: Stream WorkoutSummaries.db via adb exec-out run-as with SQLite header validation."""
        temp_dest = os.path.join(self.test_dir, "adb_out")

        # Mock 1: adb devices reports DEFAULT_PIXEL_10_SERIAL
        # Mock 2: external storage list fails / returns empty
        # Mock 3: second external storage list fails / returns empty
        # Mock 4: exec-out run-as cat databases/WorkoutSummaries.db writes authentic SQLite header
        def run_side_effect(*args, **kwargs):
            cmd = args[0]
            m = MagicMock()
            m.returncode = 0
            if "devices" in cmd:
                m.stdout = f"List of devices attached\n{DEFAULT_PIXEL_10_SERIAL}\tdevice\n"
            elif "ls" in cmd:
                m.returncode = 1
                m.stdout = ""
            elif "exec-out" in cmd:
                if "stdout" in kwargs and hasattr(kwargs["stdout"], "write"):
                    # Write authentic SQLite 3 format header (at least 16 bytes starting with b"SQLite format 3\0")
                    kwargs["stdout"].write(b"SQLite format 3\x00\x10\x00\x01\x01\x00@  ")
                m.returncode = 0
            return m

        mock_run.side_effect = run_side_effect

        pulled = pull_device_evidence_adb(
            device_serial=DEFAULT_PIXEL_10_SERIAL,
            package_name="com.atrainingtracker.trainingtracker",
            dest_dir=temp_dest,
        )
        expected_db = os.path.join(temp_dest, "WorkoutSummaries.db")
        self.assertIn(expected_db, pulled)
        self.assertTrue(os.path.exists(expected_db))

        # Test rejection of corrupt / non-sqlite stream
        def run_corrupt_side_effect(*args, **kwargs):
            cmd = args[0]
            m = MagicMock()
            m.returncode = 0
            if "devices" in cmd:
                m.stdout = f"List of devices attached\n{DEFAULT_PIXEL_10_SERIAL}\tdevice\n"
            elif "ls" in cmd:
                m.returncode = 1
                m.stdout = ""
            elif "exec-out" in cmd:
                if "stdout" in kwargs and hasattr(kwargs["stdout"], "write"):
                    kwargs["stdout"].write(b"run-as: package not debuggable\n")
                m.returncode = 0
            return m

        mock_run.side_effect = run_corrupt_side_effect
        temp_dest_corrupt = os.path.join(self.test_dir, "adb_out_corrupt")
        pulled_corrupt = pull_device_evidence_adb(
            device_serial=DEFAULT_PIXEL_10_SERIAL,
            package_name="com.atrainingtracker.trainingtracker",
            dest_dir=temp_dest_corrupt,
        )
        self.assertEqual(len(pulled_corrupt), 0)
        self.assertFalse(os.path.exists(os.path.join(temp_dest_corrupt, "WorkoutSummaries.db")))

    def test_update_sys_verification_record_markdown(self):
        """TST-PRO-098.3: Update sys_verification_record.md table rows and session IDs."""
        record_content = """# System Verification Sign-Off Record: Release V5.0.0

## 1. Hardware & Environment Log
* **Primary Reference Phone (`SYS-HW-01`)**: Google Pixel 10 (Serial: `OLD-SERIAL`, Build: `BP2A`) - Android 16

## 2. Verification Test Results Matrix
| Test ID | Title | Traced Requirements | Environment | Result | Notes / Artifacts |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `SYS-VRF-001` | Multi-Sensor Ingestion | `SYSREQ-TRK-001` | E2 / E4 | OPEN | Awaiting real workout extraction |
| `SYS-VRF-006` | Radar & Cockpit Alerts | `SYSREQ-TRK-006` | E2 | OPEN | Awaiting real workout extraction |

## 4. Formal Sign-Off & Human PO Attestation
* **Attested Session IDs / FIT Hashes**: `[PENDING EXTRACTION]`
"""
        record_path = os.path.join(self.test_dir, "sys_verification_record.md")
        with open(record_path, "w", encoding="utf-8") as f:
            f.write(record_content)

        sessions = generate_mock_pixel10_workouts()
        matrix, gap_summary = evaluate_verification_coverage(sessions)

        updated = update_sys_verification_record_markdown(
            record_path, matrix, sessions, device_serial="66020DLCR002FL"
        )
        self.assertTrue(updated)

        with open(record_path, "r", encoding="utf-8") as f:
            new_content = f.read()

        self.assertIn("66020DLCR002FL", new_content)
        self.assertIn("EXTRACTED_AWAITING_PO_ATTESTATION", new_content)
        self.assertIn("P10-RIDE-", new_content)

    def test_render_markdown_gap_report(self):
        """TST-PRO-098.3: Render comprehensive markdown coaching report."""
        sessions = generate_mock_pixel10_workouts()
        matrix, gap_summary = evaluate_verification_coverage(sessions)
        report_md = render_markdown_gap_report(gap_summary, matrix, sessions, device_serial="66020DLCR002FL")

        self.assertIn("Google Pixel 10 System Verification Telemetry & Gap Report", report_md)
        self.assertIn("66020DLCR002FL", report_md)
        self.assertIn("Extracted Sessions Summary", report_md)
        self.assertIn("SYS.5 Verification Protocol Status", report_md)
        self.assertIn("Prioritized Verification Gap & Coaching Plan for PO", report_md)


if __name__ == "__main__":
    unittest.main()
