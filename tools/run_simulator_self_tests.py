#!/usr/bin/env python3
"""
tools/run_simulator_self_tests.py
=================================
Developer Sensor Simulator Self-Test Runner (REQ-PRO-068, REQ-PRO-112).

SCOPE & CLASSIFICATION:
  - Tests ONLY the Python sensor/GPS/replay simulator scripts in tools/:
      * tools/ble_hr_simulator.py
      * tools/ble_power_simulator.py
      * tools/ble_radar_simulator.py
      * tools/fake_gps.py
      * tools/replay_workout.py
  - Does NOT launch, bind to, or verify the Android APK, emulator, or physical sensors.
  - Is NOT ASPICE SYS.4 app-level integration verification and is NOT a release gate.
    Android integration is verified by INT-SWE-01..08 and AppSmokeAndroidTest.kt;
    physical sensor verification is manual hardware-in-the-loop (ASPICE SYS.5).

Requirements: REQ-PRO-068, REQ-PRO-112
Test Spec:    TST-PRO-068.3, TST-PRO-112.1
Specification: docs/system/sys_integration_tests.md
"""

import argparse
import datetime
import json
import os
import subprocess
import sys
import time
from typing import Any, Dict, Optional, Tuple

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

try:
    from ble_hr_simulator import RrSynthesizer, build_hr_payload
except ImportError:
    RrSynthesizer = None
    build_hr_payload = None

try:
    from ble_power_simulator import (
        CyclingPowerFeatureCharacteristic,
        CyclingPowerMeasurementCharacteristic,
        handle_cli_command,
    )
except ImportError:
    CyclingPowerFeatureCharacteristic = None
    CyclingPowerMeasurementCharacteristic = None
    handle_cli_command = None

try:
    from ble_radar_simulator import (
        LaneState,
        RadarKinematicsEngine,
        RadarLightMode,
        RadarPayloadBuilder,
        RadarTargetState,
        ThreatLevel,
        VehicleSizeCategory,
        compute_advertising_pdu_size,
    )
except ImportError:
    RadarPayloadBuilder = None
    RadarKinematicsEngine = None
    compute_advertising_pdu_size = None

try:
    from fake_gps import (
        calculate_bearing,
        compute_off_route_point,
        get_sample_route,
        haversine_distance,
        heading_to_cardinal,
    )
except ImportError:
    get_sample_route = None
    haversine_distance = None

try:
    from replay_workout import (
        build_battery_payload,
        build_hr_payload as replay_build_hr_payload,
        build_power_payload as replay_build_power_payload,
        compute_auto_hrv_params,
        haversine_distance as replay_haversine_distance,
    )
except ImportError:
    replay_build_power_payload = None
    compute_auto_hrv_params = None


def get_git_commit() -> str:
    """Retrieves current git commit hash if available."""
    try:
        res = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=TOOLS_DIR,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=5,
        )
        if res.returncode == 0:
            return res.stdout.strip()
    except Exception:
        pass
    return "UNKNOWN"


class MockDbusService:
    """Minimal D-Bus service stub for exercising simulator characteristic builders headless."""

    def __init__(self, path: str = "/test/service"):
        self.path = path


class SimulatorSelfTestRunner:
    """Executes pure-Python developer simulator self-tests (SIM-HR/PWR/RDR/GPS/RPL)."""

    def __init__(self, verbose: bool = False):
        self.verbose = verbose
        self.results: Dict[str, Dict[str, Any]] = {}

    def log(self, message: str) -> None:
        if self.verbose:
            print(f"[{datetime.datetime.now().strftime('%H:%M:%S.%f')[:-3]}] {message}")

    def run_sim_hr_self_test(self) -> Tuple[bool, str, Dict[str, Any]]:
        """SIM-HR-001: Self-test ble_hr_simulator payload packing and RrSynthesizer phase conservation."""
        self.log("Running SIM-HR-001: ble_hr_simulator self-test...")
        details: Dict[str, Any] = {}
        if build_hr_payload is None or RrSynthesizer is None:
            return False, "ble_hr_simulator module components missing", details

        payload_no_rr = build_hr_payload(bpm=140, rr_raw_list=[], enable_rr=False)
        if payload_no_rr != [0x00, 140]:
            return False, f"Unexpected no-RR payload: {payload_no_rr}", details

        raw_rr = 438
        payload_rr = build_hr_payload(bpm=140, rr_raw_list=[raw_rr], enable_rr=True)
        if len(payload_rr) != 4 or payload_rr[0] != 0x10 or payload_rr[1] != 140:
            return False, f"Unexpected RR payload layout: {payload_rr}", details
        rr_unpacked = payload_rr[2] | (payload_rr[3] << 8)
        if rr_unpacked != raw_rr:
            return False, f"RR interval unpacking mismatch: {rr_unpacked} != {raw_rr}", details

        synth = RrSynthesizer(bpm=140, rmssd=35.0)
        total_time_ms = 0.0
        beat_count = 0
        for _ in range(60):
            beats = synth.tick_1hz(1000.0)
            beat_count += len(beats)
            for b in beats:
                total_time_ms += (b / 1024.0) * 1000.0

        drift_ms = abs((total_time_ms + synth.phase_ms) - 60000.0)
        details["mean_bpm"] = 140
        details["beat_count"] = beat_count
        details["phase_conservation_drift_ms"] = round(drift_ms, 2)
        if drift_ms > 5.0:
            return False, f"RrSynthesizer phase drift exceeded 5 ms: {drift_ms}", details

        return True, "ble_hr_simulator payload packing and RrSynthesizer phase conservation passed", details

    def run_sim_power_self_test(self) -> Tuple[bool, str, Dict[str, Any]]:
        """SIM-PWR-001: Self-test ble_power_simulator GATT characteristics and CLI parser."""
        self.log("Running SIM-PWR-001: ble_power_simulator self-test...")
        details: Dict[str, Any] = {}
        if (
            CyclingPowerMeasurementCharacteristic is None
            or CyclingPowerFeatureCharacteristic is None
            or handle_cli_command is None
        ):
            return False, "ble_power_simulator module components missing", details

        service = MockDbusService()
        feat = CyclingPowerFeatureCharacteristic(None, 0, service)
        meas = CyclingPowerMeasurementCharacteristic(None, 1, service)

        feat_bytes = [int(b) for b in feat.ReadValue({})]
        details["feature_bitmask"] = feat_bytes
        if len(feat_bytes) < 1 or feat_bytes[0] != 0x09:
            return False, f"Feature characteristic bitmask mismatch: {feat_bytes}", details

        meas.cadence = 0
        meas.pedal_balance = 50.0
        payload_50 = [int(b) for b in meas.build_payload()]
        if len(payload_50) != 9:
            return False, f"Expected 9-byte payload for power balance, got {len(payload_50)}", details
        power_val = payload_50[2] | (payload_50[3] << 8)
        balance_raw = payload_50[4]
        if power_val != 200 or balance_raw != 100:
            return False, f"Payload decoding mismatch: power={power_val}, balance={balance_raw}", details

        meas.pedal_balance = 54.0
        if int(meas.build_payload()[4]) != 108:
            return False, "Mild asymmetry tier (54%) encoding error", details
        meas.pedal_balance = 40.0
        if int(meas.build_payload()[4]) != 80:
            return False, "Severe asymmetry tier (40%) encoding error", details

        meas.cumulative_crank_revs = 100
        meas.last_crank_event_time = 0
        p_crank = [int(b) for b in meas.build_payload()]
        crank_revs_decoded = p_crank[5] | (p_crank[6] << 8)
        if crank_revs_decoded != 100:
            return False, f"Crank revs mismatch: {crank_revs_decoded}", details

        handle_cli_command("w275", meas)
        if meas.power != 275:
            return False, f"CLI power command failed: expected 275, got {meas.power}", details

        details["power_watts_default"] = power_val
        details["power_watts_updated"] = meas.power
        details["asymmetry_tested"] = ["Optimal 50%", "Mild 54%", "Severe 40%"]
        return True, "ble_power_simulator feature bitmask, asymmetry encoding, and CLI passed", details

    def run_sim_radar_self_test(self) -> Tuple[bool, str, Dict[str, Any]]:
        """SIM-RDR-001: Self-test ble_radar_simulator V2 payload serialization and kinematics engine."""
        self.log("Running SIM-RDR-001: ble_radar_simulator self-test...")
        details: Dict[str, Any] = {}
        if RadarPayloadBuilder is None or RadarKinematicsEngine is None or compute_advertising_pdu_size is None:
            return False, "ble_radar_simulator module components missing", details

        empty_payload = RadarPayloadBuilder.build_v2_payload([])
        if empty_payload != [0x02, 0x00]:
            return False, f"Expected all-clear frame [0x02, 0x00], got {empty_payload}", details

        target = RadarTargetState(
            target_id=1,
            range_m=120.5,
            speed_mps=22.4,
            lateral_m=-1.2,
            threat_level=ThreatLevel.APPROACHING,
            size_category=VehicleSizeCategory.CAR,
            lane_state=LaneState.PASSING_LANE,
        )
        payload = RadarPayloadBuilder.build_v2_payload([target])
        if len(payload) != 11 or payload[0:2] != [0x02, 0x38]:
            return False, f"Expected 11-byte V2 payload [0x02, 0x38, ...], got {payload}", details
        lat_unpacked, range_unpacked = RadarPayloadBuilder.unpack_v2_target_range(
            payload[4], payload[5], payload[6]
        )
        if abs(range_unpacked - 120.5) > 0.2 or abs(lat_unpacked - (-1.2)) > 0.2:
            return False, f"Radar kinematic unpack error: r={range_unpacked}, lat={lat_unpacked}", details

        pdu_bytes = compute_advertising_pdu_size("VariaSim", ["6a4e3200-667b-11e3-949a-0800200c9a66"])
        if pdu_bytes <= 0 or pdu_bytes > 31:
            return False, f"Advertising PDU size out of BLE legacy bounds: {pdu_bytes}", details

        engine = RadarKinematicsEngine()
        engine.spawn_preset("c")
        t0 = engine.tick()
        details["initial_target_count"] = len(t0) if t0 else 0
        details["initial_range_m"] = t0[0].range_m if t0 else None
        details["advertising_pdu_bytes"] = pdu_bytes
        details["light_command_opcodes"] = {
            "OFF": RadarLightMode.OFF,
            "SOLID": RadarLightMode.SOLID,
            "PELOTON": RadarLightMode.PELOTON,
            "DAY_FLASH": RadarLightMode.DAY_FLASH,
            "NIGHT_FLASH": RadarLightMode.NIGHT_FLASH,
        }

        engine.trigger_watchdog_starvation(duration_sec=4.5)
        if not engine.is_starving():
            return False, "Watchdog starvation trigger failed", details
        if engine.tick() is not None:
            return False, "Expected suppressed notification during starvation", details
        details["starvation_suppression_verified"] = True

        return True, "ble_radar_simulator V2 frames, advertising PDU, kinematics, and starvation passed", details

    def run_sim_gps_self_test(self) -> Tuple[bool, str, Dict[str, Any]]:
        """SIM-GPS-001: Self-test fake_gps route geometry, geodesic math, and off-route offsets."""
        self.log("Running SIM-GPS-001: fake_gps self-test...")
        details: Dict[str, Any] = {}
        if get_sample_route is None or haversine_distance is None:
            return False, "fake_gps module components missing", details

        route_name, points = get_sample_route("munich")
        if len(points) < 5:
            return False, f"Expected >= 5 sample points in munich route, got {len(points)}", details

        total_dist_m = 0.0
        for i in range(len(points) - 1):
            total_dist_m += haversine_distance(
                points[i].lat, points[i].lon, points[i + 1].lat, points[i + 1].lon
            )
        if total_dist_m < 1000.0:
            return False, f"Unexpected short munich sample route distance: {total_dist_m}", details

        bearing = calculate_bearing(points[0].lat, points[0].lon, points[1].lat, points[1].lon)
        cardinal = heading_to_cardinal(bearing)
        off_lat, off_lon = compute_off_route_point(points[0].lat, points[0].lon, bearing, offset_meters=75.0)
        off_dist = haversine_distance(points[0].lat, points[0].lon, off_lat, off_lon)
        if abs(off_dist - 75.0) > 2.0:
            return False, f"Off-route offset distance mismatch: {off_dist} != 75.0", details

        details["route_name"] = route_name
        details["point_count"] = len(points)
        details["total_distance_m"] = round(total_dist_m, 2)
        details["initial_bearing_deg"] = round(bearing, 1)
        details["initial_cardinal"] = cardinal
        details["off_route_offset_m"] = round(off_dist, 2)
        return True, "fake_gps route generation, haversine distance, bearing, and off-route offset passed", details

    def run_sim_replay_self_test(self) -> Tuple[bool, str, Dict[str, Any]]:
        """SIM-RPL-001: Self-test replay_workout GATT builders and auto-HRV physiology curves."""
        self.log("Running SIM-RPL-001: replay_workout self-test...")
        details: Dict[str, Any] = {}
        if replay_build_power_payload is None or compute_auto_hrv_params is None:
            return False, "replay_workout module components missing", details

        pwr_bytes = replay_build_power_payload(watts=260, cadence=92, cumulative_revs=42, event_time_1024=2048)
        if len(pwr_bytes) != 8 or (pwr_bytes[0] | (pwr_bytes[1] << 8)) != 0x0020:
            return False, f"Unexpected replay power payload: {pwr_bytes}", details
        decoded_watts = int.from_bytes(bytes(pwr_bytes[2:4]), byteorder="little", signed=True)
        if decoded_watts != 260:
            return False, f"Power bytes decode mismatch: {decoded_watts} != 260", details

        hr_bytes = replay_build_hr_payload(bpm=152, rr_raw_list=[404], enable_rr=True)
        if hr_bytes[0:2] != [0x10, 152]:
            return False, f"Unexpected replay HR payload header: {hr_bytes}", details

        if build_battery_payload(120) != [100] or build_battery_payload(-5) != [0]:
            return False, "Battery payload clamping error", details

        a1_low, rmssd_low = compute_auto_hrv_params(110.0)
        a1_high, rmssd_high = compute_auto_hrv_params(175.0)
        if not (a1_low > a1_high and rmssd_low > rmssd_high):
            return False, f"Auto-HRV intensity trend invalid: {(a1_low, rmssd_low)} vs {(a1_high, rmssd_high)}", details

        geo_dist = replay_haversine_distance(48.1750, 11.5510, 48.1760, 11.5520)
        if geo_dist <= 0.0:
            return False, f"Invalid geodesic distance: {geo_dist}", details

        details["decoded_watts"] = decoded_watts
        details["auto_hrv_110bpm"] = {"alpha1": a1_low, "rmssd_ms": rmssd_low}
        details["auto_hrv_175bpm"] = {"alpha1": a1_high, "rmssd_ms": rmssd_high}
        return True, "replay_workout BLE payload builders and auto-HRV curves passed", details

    def execute_all(self, target_test: Optional[str] = None) -> bool:
        """Executes all simulator self-tests or a single target test ID."""
        test_map = {
            "SIM-HR-001": ("BLE HR Simulator Self-Test", self.run_sim_hr_self_test),
            "SIM-PWR-001": ("BLE Cycling Power Simulator Self-Test", self.run_sim_power_self_test),
            "SIM-RDR-001": ("BLE Varia Radar Simulator Self-Test", self.run_sim_radar_self_test),
            "SIM-GPS-001": ("Fake GPS Route & Geometry Self-Test", self.run_sim_gps_self_test),
            "SIM-RPL-001": ("Workout Replay Payload & HRV Self-Test", self.run_sim_replay_self_test),
        }

        selected_tests = (
            {target_test: test_map[target_test]}
            if target_test and target_test in test_map
            else test_map
        )

        all_passed = True
        print("=" * 70)
        print("aTrainingTracker: Developer Sensor Simulator Self-Tests (Non-Gating)")
        print("Scope: Python simulator modules in tools/ ONLY (zero Android/APK runs)")
        print(f"Git Commit: {get_git_commit()}")
        print(f"Started At: {datetime.datetime.now().isoformat()}")
        print("=" * 70)

        for test_id, (test_name, test_func) in selected_tests.items():
            start_t = time.time()
            passed, message, details = test_func()
            elapsed_ms = round((time.time() - start_t) * 1000, 2)
            status_str = "PASS" if passed else "FAIL"

            self.results[test_id] = {
                "name": test_name,
                "status": status_str,
                "passed": passed,
                "duration_ms": elapsed_ms,
                "message": message,
                "details": details,
            }

            print(f"[{status_str}] {test_id}: {test_name} ({elapsed_ms} ms)")
            if not passed:
                all_passed = False
                print(f"       -> Error: {message}")

        print("=" * 70)
        passed_count = sum(1 for r in self.results.values() if r["passed"])
        total_count = len(self.results)
        print(f"Simulator Self-Test Verdict: {'PASS' if all_passed else 'FAIL'} ({passed_count}/{total_count} passed; non-gating)")
        print("=" * 70)
        return all_passed

    def export_json(self, output_path: str) -> None:
        """Exports developer simulator self-test results with explicit non-gating classification."""
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        report = {
            "title": "Developer Sensor Simulator Self-Test Report (Non-Gating)",
            "classification": "developer-simulator-self-tests",
            "executes_android_app": False,
            "release_gate": False,
            "timestamp": datetime.datetime.now().isoformat(),
            "git_commit": get_git_commit(),
            "overall_status": "PASS" if all(r["passed"] for r in self.results.values()) else "FAIL",
            "total_test_cases": len(self.results),
            "passed_test_cases": sum(1 for r in self.results.values() if r["passed"]),
            "failed_test_cases": sum(1 for r in self.results.values() if not r["passed"]),
            "test_results": self.results,
        }
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)
        print(f"Saved simulator self-test report: {output_path}")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run pure-Python developer sensor simulator self-tests (NOT an Android release gate)."
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        default=True,
        help="Run simulator self-tests without hardware (default: True).",
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="Optional path to write structured JSON simulator self-test results.",
    )
    parser.add_argument(
        "--test",
        type=str,
        default=None,
        help="Specific simulator self-test ID to run (e.g. SIM-HR-001).",
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Enable verbose output logging.",
    )

    args = parser.parse_args()
    runner = SimulatorSelfTestRunner(verbose=args.verbose)
    success = runner.execute_all(target_test=args.test)

    if args.output:
        runner.export_json(args.output)

    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
