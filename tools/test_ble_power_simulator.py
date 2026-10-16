#!/usr/bin/env python3
"""
tools/test_ble_power_simulator.py - Unit Tests for ATT-3678 BLE Power Simulator
==============================================================================
Tests Bluetooth SIG Cycling Power Service (UUID 0x1818) pedal power balance:
- Feature Characteristic (UUID 0x2A65) bitmask (bit 0 and bit 3 set -> 0x09)
- Measurement Characteristic (UUID 0x2A63) flags and payload encoding at offset 4
- Physiological asymmetry tiers: Optimal Green (50%, 48%), Mild Amber (54%), Severe Red (40%)
- Boundary clamping: [0.0, 100.0] -> [0, 200]
- Balance disabled fallback: flags 0x0020, 8-byte payload
- Interactive CLI command handling: '+', '-', 'w', 'c', 'b <num>', 'b off', invalid inputs
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from tools.ble_power_simulator import (
    CyclingPowerFeatureCharacteristic,
    CyclingPowerMeasurementCharacteristic,
    format_status,
    handle_cli_command,
)


class MockService:
    def __init__(self, path="/test/service"):
        self.path = path


class TestBlePowerSimulator(unittest.TestCase):

    def setUp(self):
        self.service = MockService()
        self.feature_char = CyclingPowerFeatureCharacteristic(None, 0, self.service)
        self.meas_char = CyclingPowerMeasurementCharacteristic(None, 1, self.service)

    def test_feature_characteristic_bitmask(self):
        """Verify Cycling Power Feature (0x2A65) exposes both Pedal Power Balance (bit 0) and Crank Rev (bit 3)."""
        value_bytes = [int(b) for b in self.feature_char.value]
        self.assertEqual(len(value_bytes), 4)
        self.assertEqual(value_bytes[0], 0x09, "Feature bit 0 (0x01) and bit 3 (0x08) must be set (0x09)")
        self.assertEqual(value_bytes[1:], [0, 0, 0])

        read_bytes = [int(b) for b in self.feature_char.ReadValue({})]
        self.assertEqual(read_bytes, [0x09, 0, 0, 0])

    def test_measurement_default_payload_balanced(self):
        """Verify default 50.0% balance: flags 0x0021, length 9, offset 4 is 100 (50.0%)."""
        self.assertEqual(self.meas_char.pedal_balance, 50.0)
        self.meas_char.cadence = 0  # Zero cadence simplifies crank payload
        payload = [int(b) for b in self.meas_char.build_payload()]

        self.assertEqual(len(payload), 9, "Expected 9-byte payload when pedal balance is present")
        flags = payload[0] | (payload[1] << 8)
        self.assertEqual(flags & 0x0001, 0x0001, "Bit 0 (Pedal Power Balance Present) must be set")
        self.assertEqual(flags & 0x0002, 0x0000, "Bit 1 (Reference) must be 0 (Left reference)")
        self.assertEqual(flags & 0x0020, 0x0020, "Bit 5 (Crank Rev Data Present) must be set")

        # Instantaneous power at offset 2..3
        power = payload[2] | (payload[3] << 8)
        self.assertEqual(power, 200)

        # Raw balance at offset 4: 50.0 * 2 = 100
        self.assertEqual(payload[4], 100)

    def test_measurement_asymmetry_tiers(self):
        """Verify physiological asymmetry tiers in 1/2 % resolution."""
        self.meas_char.cadence = 0

        # Optimal Tier: 48.0% Right (52.0% Left) -> raw = 96
        self.meas_char.pedal_balance = 48.0
        p_opt = [int(b) for b in self.meas_char.build_payload()]
        self.assertEqual(p_opt[4], 96)

        # Mild Asymmetry Tier: 54.0% Right (46.0% Left) -> raw = 108
        self.meas_char.pedal_balance = 54.0
        p_mild = [int(b) for b in self.meas_char.build_payload()]
        self.assertEqual(p_mild[4], 108)

        # Severe Asymmetry Tier: 40.0% Right (60.0% Left) -> raw = 80
        self.meas_char.pedal_balance = 40.0
        p_severe = [int(b) for b in self.meas_char.build_payload()]
        self.assertEqual(p_severe[4], 80)

        # Clamping at 0.0% and 100.0%
        self.meas_char.pedal_balance = 0.0
        self.assertEqual(int(self.meas_char.build_payload()[4]), 0)

        self.meas_char.pedal_balance = 100.0
        self.assertEqual(int(self.meas_char.build_payload()[4]), 200)

        # Extreme values clamped to [0, 200]
        self.meas_char.pedal_balance = -10.0
        self.assertEqual(int(self.meas_char.build_payload()[4]), 0)

        self.meas_char.pedal_balance = 150.0
        self.assertEqual(int(self.meas_char.build_payload()[4]), 200)

    def test_measurement_balance_disabled(self):
        """Verify balance disabled (None): flags 0x0020, 8-byte payload, no balance byte."""
        self.meas_char.pedal_balance = None
        self.meas_char.cadence = 0
        payload = [int(b) for b in self.meas_char.build_payload()]

        self.assertEqual(len(payload), 8, "Expected 8-byte legacy payload when balance is disabled")
        flags = payload[0] | (payload[1] << 8)
        self.assertEqual(flags & 0x0001, 0x0000, "Bit 0 must NOT be set when disabled")
        self.assertEqual(flags, 0x0020)

    def test_cli_command_balance(self):
        """Verify CLI parser commands for setting and disabling pedal balance."""
        # 'b 54'
        msg, quit_flag = handle_cli_command("b 54", self.meas_char)
        self.assertFalse(quit_flag)
        self.assertEqual(self.meas_char.pedal_balance, 54.0)
        self.assertIn("54.0 %", msg)
        self.assertIn("46/54", msg)

        # 'b52.5'
        msg, quit_flag = handle_cli_command("b52.5", self.meas_char)
        self.assertEqual(self.meas_char.pedal_balance, 52.5)

        # 'b off'
        msg, quit_flag = handle_cli_command("b off", self.meas_char)
        self.assertIsNone(self.meas_char.pedal_balance)
        self.assertIn("deaktiviert", msg)

        # 'b OFF' case-insensitivity
        self.meas_char.pedal_balance = 50.0
        msg, quit_flag = handle_cli_command("b OFF", self.meas_char)
        self.assertIsNone(self.meas_char.pedal_balance)

        # Invalid balance out of range: state remains unchanged
        self.meas_char.pedal_balance = 50.0
        handle_cli_command("b 150", self.meas_char)
        self.assertEqual(self.meas_char.pedal_balance, 50.0)

        handle_cli_command("b -5", self.meas_char)
        self.assertEqual(self.meas_char.pedal_balance, 50.0)

        handle_cli_command("b invalid", self.meas_char)
        self.assertEqual(self.meas_char.pedal_balance, 50.0)

    def test_cli_command_power_and_cadence(self):
        """Verify CLI parser commands for power (+, -, w) and cadence (c)."""
        self.meas_char.power = 200
        handle_cli_command("+", self.meas_char)
        self.assertEqual(self.meas_char.power, 210)

        handle_cli_command("-", self.meas_char)
        self.assertEqual(self.meas_char.power, 200)

        handle_cli_command("w300", self.meas_char)
        self.assertEqual(self.meas_char.power, 300)

        handle_cli_command("c95", self.meas_char)
        self.assertEqual(self.meas_char.cadence, 95)

        msg, quit_flag = handle_cli_command("q", self.meas_char)
        self.assertTrue(quit_flag)


if __name__ == '__main__':
    unittest.main()
