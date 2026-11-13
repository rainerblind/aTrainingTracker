#!/usr/bin/env python3
"""
Unit test suite for tools/ble_radar_simulator.py.
Verifies Garmin Varia BLE V1/V2 binary payload encoding, authentic 24-bit coordinate packing,
Garmin RearVue 820 Control Service (6a4e2f00) write/notify operations, legacy tail light sync,
kinematics engine physics and presets, watchdog starvation, 31-byte advertising PDU budget,
and Python standard library compliance (TST-PRO-005.6).

Requirement: REQ-SIM-002 (REQ-SIM-002.1..4)
Test Spec: TST-SIM-002 (TST-SIM-002.1..6)
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
    RADAR_CONTROL_SERVICE_UUID,
    RadarControlWriteCharacteristic,
    RadarControlNotifyCharacteristic,
    RadarLightControlCharacteristic,
    InvalidArgsException,
)


class TestBleRadarSimulator(unittest.TestCase):

    # -------------------------------------------------------------------------
    # TST-SIM-002.1: V2 Binary Threat Stream Payload Encoding Test
    # -------------------------------------------------------------------------
    def test_v2_payload_encoding(self):
        """
        Verify V2 2D target stream binary serialization (characteristic 6a4e3204):
        - Authentic mode (default):
          * Empty target list emits 2-byte idle heartbeat [0x02, 0x00].
          * Target frames: 2-byte LE header [0x02, 0x38] + N * 9 bytes:
            +0: targetId (uint8)
            +1: targetClass (36 TRUCK, 23 CAR, 16 UNKNOWN/LIGHT)
            +2..4: 24-bit LE packed coordinate word (11-bit lateral X, 13-bit range Y)
            +5: lengthMeters (uint8, 0.25m units)
            +6: widthMeters (uint8, 0.25m units)
            +7: speedY (int8, 0.5 m/s units, negative = approaching)
            +8: speedX (int8, 0.5 m/s units, 0x80 sentinel)
          * Total length: 2 + 9*N bytes.
        - Synthetic fallback (authentic=False):
          * Empty target list returns [0x01].
          * Target frames: 1 + 8*N bytes.
        """
        # --- Authentic Garmin Varia V2 Stream ---
        # Case 1: Empty target list -> Authentic 2-byte idle heartbeat
        empty_payload = RadarPayloadBuilder.build_v2_payload([], authentic=True)
        self.assertEqual(empty_payload, [0x02, 0x00])
        self.assertEqual(len(empty_payload), 2)

        # Case 2: Single target (approaching car)
        target1 = RadarTargetState(
            target_id=1, range_m=140.0, speed_mps=12.0, lateral_m=-1.8,
            threat_level=ThreatLevel.APPROACHING, size_category=VehicleSizeCategory.CAR,
            lane_state=LaneState.PASSING_LANE
        )
        payload1 = RadarPayloadBuilder.build_v2_payload([target1], authentic=True)
        self.assertEqual(len(payload1), 11)  # 2 + 9*1
        self.assertEqual(payload1[0:2], [0x02, 0x38])  # 2-byte LE header
        self.assertEqual(payload1[2], 1)  # targetId
        self.assertEqual(payload1[3], 23)  # targetClass CAR

        # Unpack 24-bit packed range word
        b0, b1, b2 = payload1[4], payload1[5], payload1[6]
        lat_unpacked, range_unpacked = RadarPayloadBuilder.unpack_v2_target_range(b0, b1, b2)
        self.assertAlmostEqual(lat_unpacked, -1.8, places=1)
        self.assertAlmostEqual(range_unpacked, 140.0, places=1)

        # Length and Width (0.25m resolution)
        self.assertEqual(payload1[7], int(round(4.5 / 0.25)))  # lengthMeters
        self.assertEqual(payload1[8], int(round(1.8 / 0.25)))  # widthMeters

        # Speed Y: closing speed 12.0 m/s -> -24 int8 -> 0xE8 uint8
        expected_speed_y = (-24) & 0xFF
        self.assertEqual(payload1[9], expected_speed_y)
        self.assertEqual(payload1[10], 0x80)  # speedX sentinel

        # Case 3: Multiple targets (3-target frame: Car, Truck, Light)
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
        payload_multi = RadarPayloadBuilder.build_v2_payload([target1, target2, target3], authentic=True)
        self.assertEqual(len(payload_multi), 29)  # 2 + 9*3
        self.assertEqual(payload_multi[0:2], [0x02, 0x38])

        # Verify second target (Truck)
        off2 = 2 + 9 * 1
        self.assertEqual(payload_multi[off2], 2)  # tid
        self.assertEqual(payload_multi[off2 + 1], 36)  # TRUCK_OR_BUS class
        lat2, range2 = RadarPayloadBuilder.unpack_v2_target_range(
            payload_multi[off2 + 2], payload_multi[off2 + 3], payload_multi[off2 + 4]
        )
        self.assertAlmostEqual(lat2, 1.2, places=1)
        self.assertAlmostEqual(range2, 80.5, places=1)

        # Verify third target (Unknown/Light)
        off3 = 2 + 9 * 2
        self.assertEqual(payload_multi[off3], 3)
        self.assertEqual(payload_multi[off3 + 1], 16)  # UNKNOWN_OR_LIGHT class

        # --- Backward-Compatible Synthetic Layout ---
        empty_synth = RadarPayloadBuilder.build_v2_payload([], authentic=False)
        self.assertEqual(empty_synth, [PACKET_TYPE_V2_THREAT])
        payload1_synth = RadarPayloadBuilder.build_v2_payload([target1], authentic=False)
        self.assertEqual(len(payload1_synth), 9)  # 1 + 8*1
        self.assertEqual(payload1_synth[0], PACKET_TYPE_V2_THREAT)

    # -------------------------------------------------------------------------
    # TST-SIM-002.1b: 24-Bit Coordinate Packing Round-Trip Boundary Test
    # -------------------------------------------------------------------------
    def test_v2_packed_range_roundtrip(self):
        """
        Verify mathematical 24-bit little-endian range word packing/unpacking:
        - 11-bit signed lateral X offset (*10, -1024..1023).
        - 13-bit signed longitudinal Y range (*10, -4096..4095).
        - Enforces zero bit-drift across negative and extreme physical boundaries.
        """
        test_cases = [
            (-1.8, 140.0),
            (-10.0, 175.0),
            (0.0, 0.0),
            (1.5, 50.0),
            (-0.1, 15.2),
            (3.4, 160.8),
            (-102.4, 409.5),  # 11-bit min / 13-bit max signed bounds
            (102.3, 0.0),     # 11-bit max signed bound
        ]
        for lat, rng in test_cases:
            b0, b1, b2 = RadarPayloadBuilder.pack_v2_target_range(lat, rng)
            unpacked_lat, unpacked_rng = RadarPayloadBuilder.unpack_v2_target_range(b0, b1, b2)
            self.assertAlmostEqual(
                unpacked_lat, lat, places=1,
                msg=f"Lateral bit drift for input {lat}m -> {unpacked_lat}m"
            )
            self.assertAlmostEqual(
                unpacked_rng, rng, places=1,
                msg=f"Range bit drift for input {rng}m -> {unpacked_rng}m"
            )

    # -------------------------------------------------------------------------
    # TST-SIM-002.2: V1 Authentic Sequence Header & Threat Stream Test
    # -------------------------------------------------------------------------
    def test_v1_payload_encoding(self):
        """
        Verify V1 target stream binary serialization (characteristic 6a4e3203):
        - Authentic mode (default):
          * Sequence counter cycling in high nibble: seq = ((counter & 0x0F) << 4) | 0x02.
          * Empty target list emits 1-byte link-alive heartbeat [seq].
          * Target frames: [seq] + N * 3 bytes:
            +0: 0x80 | (targetId & 0x7F) (bit 7 valid vehicle indicator)
            +1: rangeMeters (uint8, 0..255m)
            +2: flag (0x01 if FAST_APPROACHING else 0x00)
          * Total length: 1 + 3*N bytes.
        - Synthetic fallback (authentic=False):
          * Header byte 0x00, total length 1 + 3*N bytes.
        """
        # --- Authentic Garmin Varia V1 Stream ---
        # Case 1: Empty target list -> 1-byte link-alive heartbeat
        empty_payload0 = RadarPayloadBuilder.build_v1_payload([], seq_counter=0, authentic=True)
        self.assertEqual(empty_payload0, [0x02])
        self.assertEqual(len(empty_payload0), 1)

        empty_payload5 = RadarPayloadBuilder.build_v1_payload([], seq_counter=5, authentic=True)
        self.assertEqual(empty_payload5, [0x52])
        self.assertEqual(len(empty_payload5), 1)

        # Case 2: Single target (approaching)
        target = RadarTargetState(target_id=5, range_m=42.4, speed_mps=11.2, threat_level=ThreatLevel.APPROACHING)
        payload = RadarPayloadBuilder.build_v1_payload([target], seq_counter=1, authentic=True)
        self.assertEqual(len(payload), 4)  # 1 + 3*1
        self.assertEqual(payload[0], 0x12)  # seq header
        self.assertEqual(payload[1], 0x80 | 5)  # bit 7 valid vehicle flag
        self.assertEqual(payload[2], 42)  # range meters
        self.assertEqual(payload[3], 0x00)  # flag (APPROACHING)

        # Case 3: Fast approaching target
        target_fast = RadarTargetState(target_id=9, range_m=88.0, speed_mps=22.0, threat_level=ThreatLevel.FAST_APPROACHING)
        payload_fast = RadarPayloadBuilder.build_v1_payload([target_fast], seq_counter=2, authentic=True)
        self.assertEqual(len(payload_fast), 4)
        self.assertEqual(payload_fast[0], 0x22)
        self.assertEqual(payload_fast[1], 0x80 | 9)
        self.assertEqual(payload_fast[2], 88)
        self.assertEqual(payload_fast[3], 0x01)  # flag (FAST_APPROACHING)

        # --- Backward-Compatible Synthetic Layout ---
        empty_synth = RadarPayloadBuilder.build_v1_payload([], authentic=False)
        self.assertEqual(empty_synth, [PACKET_TYPE_THREAT])
        payload_synth = RadarPayloadBuilder.build_v1_payload([target], authentic=False)
        self.assertEqual(len(payload_synth), 4)
        self.assertEqual(payload_synth[0], PACKET_TYPE_THREAT)
        self.assertEqual(payload_synth[1], 5)
        self.assertEqual(payload_synth[2], 42)

    # -------------------------------------------------------------------------
    # TST-SIM-002.3: Tail Light Control & RearVue 820 Service 6a4e2f00 Test
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

    def test_rearvue_light_control_service(self):
        """
        Verify Garmin RearVue 820 dedicated Control Service 6a4e2f00 (REQ-SIM-002.3):
        - Characteristic 6a4e2f11 accepts 4-byte command '06 09 01 TT'.
        - Characteristic 6a4e2f14 emits 4-byte slot notifications '01 00 ff TT'.
        - Bidirectional synchronization with legacy 6a4e3202 characteristic.
        - Invalid command formats rejected with InvalidArgsException.
        """
        engine = RadarKinematicsEngine(is_stvzo=False)

        # Instantiate characteristics headlessly (bus=None)
        notify_char = RadarControlNotifyCharacteristic(None, 0, None, engine)
        notify_char.StartNotify()

        legacy_char = RadarLightControlCharacteristic(None, 2, None, engine)
        legacy_char.StartNotify()

        write_char = RadarControlWriteCharacteristic(
            None, 1, None, engine, notify_char=notify_char, legacy_char=legacy_char
        )
        legacy_char.set_peer_notify_char(notify_char)

        # 1. Test 6a4e2f11 write command for DAY_FLASH (0x13)
        write_char.WriteValue([0x06, 0x09, 0x01, 0x13], {})
        self.assertEqual(engine.light_mode, RadarLightMode.DAY_FLASH)
        self.assertEqual(notify_char.last_notified_value, [0x01, 0x00, 0xFF, 0x13])
        self.assertEqual(legacy_char.last_notified_value, [RadarLightMode.DAY_FLASH])

        # 2. Test 6a4e2f11 write command for PELOTON (0x12)
        write_char.WriteValue([0x06, 0x09, 0x01, 0x12], {})
        self.assertEqual(engine.light_mode, RadarLightMode.PELOTON)
        self.assertEqual(notify_char.last_notified_value, [0x01, 0x00, 0xFF, 0x12])
        self.assertEqual(legacy_char.last_notified_value, [RadarLightMode.PELOTON])

        # 3. Test 6a4e2f11 write command for OFF (0x1F)
        write_char.WriteValue([0x06, 0x09, 0x01, 0x1F], {})
        self.assertEqual(engine.light_mode, RadarLightMode.OFF)
        self.assertEqual(notify_char.last_notified_value, [0x01, 0x00, 0xFF, 0x1F])
        self.assertEqual(legacy_char.last_notified_value, [RadarLightMode.OFF])

        # 4. Test 6a4e2f11 write command for SOLID (0x11)
        write_char.WriteValue([0x06, 0x09, 0x01, 0x11], {})
        self.assertEqual(engine.light_mode, RadarLightMode.SOLID)
        self.assertEqual(notify_char.last_notified_value, [0x01, 0x00, 0xFF, 0x11])
        self.assertEqual(legacy_char.last_notified_value, [RadarLightMode.SOLID])

        # 5. Test invalid commands rejected with InvalidArgsException
        with self.assertRaises(InvalidArgsException):
            write_char.WriteValue([], {})
        with self.assertRaises(InvalidArgsException):
            write_char.WriteValue([0x06, 0x09], {})
        with self.assertRaises(InvalidArgsException):
            write_char.WriteValue([0x00, 0x00, 0x00, 0x11], {})
        with self.assertRaises(InvalidArgsException):
            write_char.WriteValue([0x06, 0x09, 0x01, 0x99], {})

        # 6. Test legacy 6a4e3202 write triggers RearVue 6a4e2f14 notification
        legacy_char.WriteValue([RadarLightMode.NIGHT_FLASH], {})
        self.assertEqual(engine.light_mode, RadarLightMode.NIGHT_FLASH)
        self.assertEqual(legacy_char.last_notified_value, [RadarLightMode.NIGHT_FLASH])
        self.assertEqual(notify_char.last_notified_value, [0x01, 0x00, 0xFF, 0x14])

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
        - Enforces that adding 6a4e2f00 to advertisement would violate 31B limit.
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

        # Proving invariant REQ-SIM-002.4: 6a4e2f00 must NOT be advertised
        pdu_with_control = compute_advertising_pdu_size('VariaSim', [RADAR_SERVICE_UUID, RADAR_CONTROL_SERVICE_UUID])
        self.assertGreater(pdu_with_control, 31, "Advertising both services exceeds 31-byte budget")
        self.assertEqual(pdu_with_control, 47)

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
