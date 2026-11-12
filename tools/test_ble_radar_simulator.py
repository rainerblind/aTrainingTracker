#!/usr/bin/env python3
"""
Unit test suite for tools/ble_radar_simulator.py.
Verifies Garmin Varia BLE V1/V2 binary payload encoding, bidirectional tail light control,
kinematics engine physics and presets, watchdog starvation, 31-byte advertising PDU budget,
and Python standard library compliance (TST-PRO-005.6).

Requirement: REQ-SIM-002 (REQ-TST-006.1..4)
Test Spec: TST-SIM-002 (TST-TST-006.1..6)
"""

import ast
import os
import struct
import sys
import unittest

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

from ble_radar_simulator import (
    compute_advertising_pdu_size,
    RadarKinematicsEngine,
    RadarLightMode,
    RadarPayloadBuilder,
    RadarTargetState,
    ThreatLevel,
    VehicleSizeCategory,
    LaneState,
    PACKET_TYPE_THREAT,
    PACKET_TYPE_V2_THREAT,
    RADAR_SERVICE_UUID,
)


class TestBleRadarSimulator(unittest.TestCase):

    # -------------------------------------------------------------------------
    # TST-SIM-002.1: V2 Binary Threat Stream Payload Encoding Test
    # -------------------------------------------------------------------------
    def test_v2_payload_encoding(self):
        """
        Verify V2 2D target stream binary serialization (characteristic 6a4e3204):
        - Header byte must be 0x01 (PACKET_TYPE_V2_THREAT)
        - Target record stride is exactly 8 bytes:
          +0: targetId (uint8)
          +1..2: rangeRaw (uint16 LE, 0.1m precision)
          +3..4: speedRaw (int16 LE signed, 0.1 m/s precision)
          +5..6: lateralRaw (int16 LE signed, 0.1m precision, negative=left)
          +7: sizeAndThreat (bits 0..1 threat, bits 2..3 size, bits 4..5 lane)
        - Total length: 1 + 8*N bytes.
        - Empty target list returns [0x01] (all-clear).
        """
        # Case 1: Empty target list -> All-clear frame
        empty_payload = RadarPayloadBuilder.build_v2_payload([])
        self.assertEqual(empty_payload, [PACKET_TYPE_V2_THREAT])
        self.assertEqual(len(empty_payload), 1)

        # Case 2: Single target (approaching car in passing lane)
        # ID=1, range=140.0m -> 1400 (0x0578 LE: [0x78, 0x05])
        # speed=12.0m/s -> 120 (0x0078 LE: [0x78, 0x00])
        # lateral=-1.8m -> -18 (0xFFEE LE: [0xEE, 0xFF])
        # threat=APPROACHING (1), size=CAR (1), lane=PASSING_LANE (3)
        # sizeAndThreat = 1 | (1 << 2) | (3 << 4) = 1 | 4 | 48 = 53 (0x35)
        target1 = RadarTargetState(
            target_id=1, range_m=140.0, speed_mps=12.0, lateral_m=-1.8,
            threat_level=ThreatLevel.APPROACHING, size_category=VehicleSizeCategory.CAR,
            lane_state=LaneState.PASSING_LANE
        )
        payload1 = RadarPayloadBuilder.build_v2_payload([target1])
        self.assertEqual(len(payload1), 9)  # 1 + 8*1
        self.assertEqual(payload1[0], PACKET_TYPE_V2_THREAT)
        self.assertEqual(payload1[1], 1)  # targetId
        self.assertEqual(payload1[2:4], [0x78, 0x05])  # rangeRaw 1400 uint16 LE
        self.assertEqual(payload1[4:6], [0x78, 0x00])  # speedRaw 120 int16 LE
        self.assertEqual(payload1[6:8], [0xEE, 0xFF])  # lateralRaw -18 int16 LE
        self.assertEqual(payload1[8], 0x35)  # sizeAndThreat

        # Case 3: Multiple targets (3-target frame)
        target2 = RadarTargetState(
            target_id=2, range_m=80.5, speed_mps=20.0, lateral_m=1.2,
            threat_level=ThreatLevel.FAST_APPROACHING, size_category=VehicleSizeCategory.TRUCK_OR_BUS,
            lane_state=LaneState.CHANGING_LANE
        )
        target3 = RadarTargetState(
            target_id=3, range_m=15.0, speed_mps=8.5, lateral_m=-0.5,
            threat_level=ThreatLevel.APPROACHING, size_category=VehicleSizeCategory.UNKNOWN_OR_LIGHT,
            lane_state=LaneState.IN_RIDER_LANE
        )
        payload_multi = RadarPayloadBuilder.build_v2_payload([target1, target2, target3])
        self.assertEqual(len(payload_multi), 25)  # 1 + 8*3
        self.assertEqual(payload_multi[0], PACKET_TYPE_V2_THREAT)

        # Verify second target decoding
        off = 1 + 8 * 1
        self.assertEqual(payload_multi[off], 2)
        # range 80.5 -> 805 = 0x0325 LE: [0x25, 0x03]
        self.assertEqual(payload_multi[off+1:off+3], [0x25, 0x03])
        # speed 20.0 -> 200 = 0x00C8 LE: [0xC8, 0x00]
        self.assertEqual(payload_multi[off+3:off+5], [0xC8, 0x00])
        # lateral +1.2m -> 12 = 0x000C LE: [0x0C, 0x00]
        self.assertEqual(payload_multi[off+5:off+7], [0x0C, 0x00])
        # threat=FAST_APPROACHING(2), size=TRUCK(2), lane=CHANGING(2)
        # 2 | (2 << 2) | (2 << 4) = 2 | 8 | 32 = 42 (0x2A)
        self.assertEqual(payload_multi[off+7], 0x2A)

    # -------------------------------------------------------------------------
    # TST-SIM-002.2: V1 Legacy Binary Threat Stream Payload Encoding Test
    # -------------------------------------------------------------------------
    def test_v1_payload_encoding(self):
        """
        Verify V1 legacy target stream binary serialization (characteristic 6a4e3203):
        - Header byte must be 0x00 (PACKET_TYPE_THREAT)
        - Target record stride is exactly 3 bytes:
          +0: targetId (uint8)
          +1: rangeMeters (uint8, 0..255m)
          +2: (threatBits << 4) | (speedMps & 0x0F)
        - Total length: 1 + 3*N bytes.
        - Empty target list returns [0x00] (all-clear).
        - Clamping: range > 255 clamped to 255; speed > 15 clamped to 15.
        """
        # Case 1: Empty target list -> All-clear frame
        empty_payload = RadarPayloadBuilder.build_v1_payload([])
        self.assertEqual(empty_payload, [PACKET_TYPE_THREAT])
        self.assertEqual(len(empty_payload), 1)

        # Case 2: Single target
        # ID=5, range=42.4m -> 42, speed=11.2m/s -> 11, threat=APPROACHING (1)
        # byte[2] = (1 << 4) | 11 = 16 | 11 = 27 (0x1B)
        target = RadarTargetState(target_id=5, range_m=42.4, speed_mps=11.2, threat_level=ThreatLevel.APPROACHING)
        payload = RadarPayloadBuilder.build_v1_payload([target])
        self.assertEqual(len(payload), 4)  # 1 + 3*1
        self.assertEqual(payload[0], PACKET_TYPE_THREAT)
        self.assertEqual(payload[1], 5)
        self.assertEqual(payload[2], 42)
        self.assertEqual(payload[3], 0x1B)

        # Case 3: Multiple targets with clamping
        target_clamped = RadarTargetState(
            target_id=9, range_m=300.0, speed_mps=25.0, threat_level=ThreatLevel.FAST_APPROACHING
        )
        payload_clamped = RadarPayloadBuilder.build_v1_payload([target, target_clamped])
        self.assertEqual(len(payload_clamped), 7)  # 1 + 3*2
        # Target 9 range clamped to 255, speed clamped to 15 (0x0F), threat=2 (0x20) -> 0x2F
        off = 1 + 3 * 1
        self.assertEqual(payload_clamped[off], 9)
        self.assertEqual(payload_clamped[off+1], 255)
        self.assertEqual(payload_clamped[off+2], 0x2F)

    # -------------------------------------------------------------------------
    # TST-SIM-002.3: Tail Light Control 6a4e3202 Read/Write Contract Test
    # -------------------------------------------------------------------------
    def test_light_control_read_write(self):
        """
        Verify bidirectional tail light mode state machine on characteristic 6a4e3202:
        - Valid modes: 0x00 (OFF), 0x01 (SOLID), 0x05 (PELOTON), 0x06 (NIGHT_FLASH), 0x07 (DAY_FLASH).
        - set_light_mode updates internal state and returns True.
        - Invalid modes are rejected (returns False).
        - StVZO mode restrictions: When is_stvzo=True, flashing modes are restricted to SOLID (0x01).
        """
        engine = RadarKinematicsEngine(is_stvzo=False)

        # Default mode is SOLID (0x01)
        self.assertEqual(engine.light_mode, RadarLightMode.SOLID)

        # Switch to DAY_FLASH
        self.assertTrue(engine.set_light_mode(RadarLightMode.DAY_FLASH))
        self.assertEqual(engine.light_mode, RadarLightMode.DAY_FLASH)

        # Switch to PELOTON
        self.assertTrue(engine.set_light_mode(RadarLightMode.PELOTON))
        self.assertEqual(engine.light_mode, RadarLightMode.PELOTON)

        # Switch to NIGHT_FLASH
        self.assertTrue(engine.set_light_mode(RadarLightMode.NIGHT_FLASH))
        self.assertEqual(engine.light_mode, RadarLightMode.NIGHT_FLASH)

        # Switch to OFF
        self.assertTrue(engine.set_light_mode(RadarLightMode.OFF))
        self.assertEqual(engine.light_mode, RadarLightMode.OFF)

        # Invalid mode byte rejected
        self.assertFalse(engine.set_light_mode(0x42))
        self.assertEqual(engine.light_mode, RadarLightMode.OFF)

        # StVZO Compliance: Flashing modes restricted to SOLID
        engine_stvzo = RadarKinematicsEngine(is_stvzo=True)
        self.assertTrue(engine_stvzo.set_light_mode(RadarLightMode.DAY_FLASH))
        self.assertEqual(engine_stvzo.light_mode, RadarLightMode.SOLID, "StVZO must clamp DAY_FLASH to SOLID")

        self.assertTrue(engine_stvzo.set_light_mode(RadarLightMode.NIGHT_FLASH))
        self.assertEqual(engine_stvzo.light_mode, RadarLightMode.SOLID, "StVZO must clamp NIGHT_FLASH to SOLID")

        self.assertTrue(engine_stvzo.set_light_mode(RadarLightMode.OFF))
        self.assertEqual(engine_stvzo.light_mode, RadarLightMode.OFF, "StVZO permits OFF")

    # -------------------------------------------------------------------------
    # TST-SIM-002.4: Kinematics Engine Physics & Traffic Presets Test
    # -------------------------------------------------------------------------
    def test_kinematics_engine_and_presets(self):
        """
        Verify kinematics engine progression and presets:
        - Target distance closes each tick: r_{t+1} = r_t - v * dt.
        - Target removed upon pass completion (range <= 0.0m).
        - Presets 'c', 'f', 't', 'p', 'm', '0' spawn expected configurations.
        """
        engine = RadarKinematicsEngine(tick_rate_hz=5.0)  # dt = 0.200s

        # 1. Physics update: Car at 10.0m approaching at 10.0 m/s
        target = RadarTargetState(target_id=1, range_m=10.0, speed_mps=10.0)
        engine._upsert_target(target)

        # Tick 1: 10.0 - 10.0*0.2 = 8.0m
        active = engine.tick()
        self.assertEqual(len(active), 1)
        self.assertAlmostEqual(active[0].range_m, 8.0, places=2)

        # Tick 2: 8.0 - 2.0 = 6.0m
        active = engine.tick()
        self.assertEqual(len(active), 1)
        self.assertAlmostEqual(active[0].range_m, 6.0, places=2)

        # 3 more ticks: 4.0m, 2.0m, 0.0m -> removed on next tick
        engine.tick()  # 4.0m
        engine.tick()  # 2.0m
        active = engine.tick()  # 0.0m -> removed
        self.assertEqual(len(active), 0, "Target must be removed upon completing pass (range <= 0.0m)")

        # 2. Test Presets
        engine.clear_targets()

        # Preset 'c': Single car
        engine.spawn_preset('c')
        self.assertEqual(len(engine.targets), 1)
        self.assertEqual(engine.targets[0].target_id, 1)
        self.assertEqual(engine.targets[0].size_category, VehicleSizeCategory.CAR)

        # Preset 'f': Fast car
        engine.spawn_preset('f')
        self.assertEqual(len(engine.targets), 2)
        fast_car = [t for t in engine.targets if t.target_id == 2][0]
        self.assertEqual(fast_car.threat_level, ThreatLevel.FAST_APPROACHING)
        self.assertAlmostEqual(fast_car.speed_mps, 22.0)

        # Preset 't': Heavy truck/bus
        engine.spawn_preset('t')
        truck = [t for t in engine.targets if t.target_id == 3][0]
        self.assertEqual(truck.size_category, VehicleSizeCategory.TRUCK_OR_BUS)

        # Preset 'p': Close pass (< 1.5m passing distance)
        engine.spawn_preset('p')
        close_pass = [t for t in engine.targets if t.target_id == 4][0]
        self.assertLess(abs(close_pass.lateral_m), 1.5, "Close pass lateral offset must be < 1.5m")
        self.assertEqual(close_pass.lane_state, LaneState.CHANGING_LANE)

        # Preset 'm': 3-car convoy
        engine.clear_targets()
        engine.spawn_preset('m')
        self.assertEqual(len(engine.targets), 3)
        self.assertEqual([t.target_id for t in engine.targets], [11, 12, 13])

        # Preset '0': All-clear
        engine.spawn_preset('0')
        self.assertEqual(len(engine.targets), 0)

    # -------------------------------------------------------------------------
    # TST-SIM-002.5: Watchdog Starvation & 31-Byte PDU Budget Audit Test
    # -------------------------------------------------------------------------
    def test_watchdog_and_pdu_budget(self):
        """
        Verify watchdog starvation timing and BLE advertisement PDU constraint:
        - Watchdog starvation command 'w' suppresses notifications for >= 4.5s (23 ticks @ 5 Hz).
        - While starving, tick() returns None to indicate suppressed transmission.
        - Resumes normal target emission after starvation expires.
        - Legacy BLE advertising PDU budget <= 31 bytes.
        """
        engine = RadarKinematicsEngine(tick_rate_hz=5.0)  # 200ms per tick
        engine.spawn_preset('c')
        self.assertEqual(len(engine.targets), 1)

        # Trigger 4.5s starvation -> 23 ticks
        engine.trigger_watchdog_starvation(duration_sec=4.5)
        self.assertTrue(engine.is_starving())

        # For 23 ticks, tick() must return None (notification suppressed)
        for tick_idx in range(23):
            self.assertTrue(engine.is_starving(), f"Must be starving at tick {tick_idx}")
            result = engine.tick()
            self.assertIsNone(result, f"Tick {tick_idx} must return None during starvation")

        # Starvation now ended
        self.assertFalse(engine.is_starving())
        resumed = engine.tick()
        self.assertIsNotNone(resumed)
        self.assertIsInstance(resumed, list)

        # PDU Budget Calculation
        # Flags (3B) + 128-bit UUID (18B) + LocalName 'VariaSim' (10B) = 31B
        pdu_size = compute_advertising_pdu_size('VariaSim', [RADAR_SERVICE_UUID])
        self.assertEqual(pdu_size, 31, "Advertising PDU size for VariaSim must be exactly 31 bytes")
        self.assertLessEqual(pdu_size, 31, "Advertising PDU size must not exceed legacy 31-byte limit")

        # Longer local name must exceed 31 bytes
        oversized = compute_advertising_pdu_size('VariaSimulator', [RADAR_SERVICE_UUID])
        self.assertGreater(oversized, 31, "14-char name must exceed 31-byte budget")

    # -------------------------------------------------------------------------
    # TST-SIM-002.6: Python Standard Library Compliance Test (AST Audit)
    # -------------------------------------------------------------------------
    def test_python_stdlib_compliance_ast(self):
        """
        Enforce TST-PRO-005.6: Zero non-stdlib external dependencies (no numpy, scipy, etc.).
        Inspects AST of tools/ble_radar_simulator.py and this test file.
        """
        files_to_check = [
            os.path.join(TOOLS_DIR, "ble_radar_simulator.py"),
            os.path.join(TOOLS_DIR, "test_ble_radar_simulator.py"),
        ]

        allowed_modules = {
            "sys", "os", "argparse", "math", "random", "struct", "threading", "time",
            "dbus", "gi", "unittest", "ast", "ble_radar_simulator"
        }

        for file_path in files_to_check:
            with open(file_path, "r", encoding="utf-8") as f:
                tree = ast.parse(f.read(), filename=file_path)

            imported = set()
            for node in ast.walk(tree):
                if isinstance(node, ast.Import):
                    for alias in node.names:
                        imported.add(alias.name.split(".")[0])
                elif isinstance(node, ast.ImportFrom):
                    if node.module:
                        imported.add(node.module.split(".")[0])

            disallowed = imported - allowed_modules
            self.assertEqual(
                disallowed,
                set(),
                f"Disallowed third-party dependency found in {os.path.basename(file_path)}: {disallowed} (violates TST-PRO-005.6)"
            )


if __name__ == "__main__":
    unittest.main()
