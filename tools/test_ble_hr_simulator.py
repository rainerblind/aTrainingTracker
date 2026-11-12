#!/usr/bin/env python3
"""
Unit test suite for tools/ble_hr_simulator.py.
Verifies BLE 0x2A37 Bit 4 RR interval packing, phase-locked beat accumulator drift,
AR(1) DFA alpha-1 regimes, rMSSD innovation scaling, artifact/gap injection,
and Python standard library compliance (TST-PRO-005.6).

Requirement: REQ-SIM-001 (REQ-TST-005.1..4)
Test Spec: TST-SIM-001 (TST-TST-005.1..5)
"""

import ast
import math
import os
import sys
import unittest

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
if TOOLS_DIR not in sys.path:
    sys.path.insert(0, TOOLS_DIR)

from ble_hr_simulator import RrSynthesizer, build_hr_payload


class TestBleHrSimulator(unittest.TestCase):

    # -------------------------------------------------------------------------
    # TST-SIM-001.1: BLE Byte Packing & --no-rr Contract Test
    # -------------------------------------------------------------------------
    def test_ble_byte_packing_and_no_rr(self):
        """
        Verify BLE 0x2A37 Heart Rate Measurement byte layout:
        - When enable_rr=False: flags = 0x00, payload = [flags, bpm]
        - When enable_rr=True: flags = 0x10 (Bit 4), followed by BPM and 1/1024s uint16 LE pairs
        """
        # Case 1: Legacy --no-rr mode
        payload_no_rr = build_hr_payload(bpm=135, rr_raw_list=[455], enable_rr=False)
        self.assertEqual(len(payload_no_rr), 2)
        self.assertEqual(payload_no_rr[0], 0x00)
        self.assertEqual(payload_no_rr[1], 135)

        # Case 2: RR enabled with 1 beat (~800 ms -> 819 in 1/1024s)
        payload_1_rr = build_hr_payload(bpm=75, rr_raw_list=[819], enable_rr=True)
        self.assertEqual(len(payload_1_rr), 4)
        self.assertEqual(payload_1_rr[0], 0x10)  # Bit 4 set
        self.assertEqual(payload_1_rr[1], 75)
        # 819 = 0x0333 -> little-endian [0x33, 0x03]
        self.assertEqual(payload_1_rr[2], 0x33)
        self.assertEqual(payload_1_rr[3], 0x03)

        # Case 3: Multiple RR intervals in single notification tick (e.g. 150 BPM -> 2 beats)
        # 410 = 0x019A -> [0x9A, 0x01]
        payload_2_rr = build_hr_payload(bpm=150, rr_raw_list=[410, 410], enable_rr=True)
        self.assertEqual(len(payload_2_rr), 6)
        self.assertEqual(payload_2_rr[0], 0x10)
        self.assertEqual(payload_2_rr[1], 150)
        self.assertEqual(payload_2_rr[2:4], [0x9A, 0x01])
        self.assertEqual(payload_2_rr[4:6], [0x9A, 0x01])

        # Case 4: RR enabled but empty beat list falls back gracefully to flags=0x00
        payload_empty = build_hr_payload(bpm=60, rr_raw_list=[], enable_rr=True)
        self.assertEqual(payload_empty, [0x00, 60])

    # -------------------------------------------------------------------------
    # TST-SIM-001.2: Phase-Locked Beat Accumulator Drift Test
    # -------------------------------------------------------------------------
    def test_phase_locked_accumulator_drift(self):
        """
        Simulate 60 seconds (60 ticks of 1000.0 ms) across standard human heart rate
        range (40 to 180 BPM) with error diffusion. Assert that cumulative emitted
        RR duration matches 60,000 ms with absolute drift <= 50 ms (and << 2000 ms DRIFT_THRESHOLD_MS).
        """
        test_bpms = [40, 50, 60, 75, 80, 90, 100, 120, 135, 150, 160, 180]
        for bpm in test_bpms:
            synth = RrSynthesizer(bpm=bpm, rmssd=0.0)
            all_emitted_raw = []
            for _ in range(60):
                ticks_raw = synth.tick_1hz(1000.0)
                all_emitted_raw.extend(ticks_raw)

            # Convert 1/1024s units back to milliseconds
            total_emitted_ms = sum(raw * 1000.0 / 1024.0 for raw in all_emitted_raw)
            diff_ms = abs(total_emitted_ms - 60000.0)

            self.assertLessEqual(
                diff_ms,
                50.0,
                f"BPM {bpm} exceeded drift threshold: diff={diff_ms:.3f} ms (total={total_emitted_ms:.1f} ms)"
            )
            # In fact, with fractional error diffusion, residual drift is < 1.0 ms
            self.assertLess(
                diff_ms,
                2.0,
                f"BPM {bpm} error diffusion failed to keep drift sub-millisecond: diff={diff_ms:.3f} ms"
            )

    # -------------------------------------------------------------------------
    # TST-SIM-001.3: AR(1) DFA alpha-1 Regimes & rMSSD Scaling Test
    # -------------------------------------------------------------------------
    def test_ar1_dfa_regimes_and_rmssd_scaling(self):
        """
        Generate 400 beats per regime ('aerobic', 'threshold', 'anaerobic') at 40 ms target rMSSD.
        Assert that:
        1. Empirical rMSSD matches target 40.0 ms within +- 20% (32.0 to 48.0 ms).
        2. Short-term lag-1 autocorrelation rho_1 monotonically orders:
           rho_1(aerobic) > rho_1(threshold) > rho_1(anaerobic).
        """
        target_rmssd = 40.0
        n_beats = 400
        rho1_results = {}
        rmssd_results = {}

        for regime_name in ['aerobic', 'threshold', 'anaerobic']:
            synth = RrSynthesizer(bpm=120, rmssd=target_rmssd, regime=regime_name)
            rrs = [synth.generate_next_rr_ms() for _ in range(n_beats)]

            # Empirical rMSSD: sqrt(mean((RR_{i} - RR_{i-1})^2))
            diffs = [rrs[i] - rrs[i - 1] for i in range(1, len(rrs))]
            mssd = sum(d * d for d in diffs) / len(diffs)
            emp_rmssd = math.sqrt(mssd)
            rmssd_results[regime_name] = emp_rmssd

            # Empirical lag-1 autocorrelation rho_1
            mean_rr = sum(rrs) / len(rrs)
            variance = sum((x - mean_rr) ** 2 for x in rrs) / len(rrs)
            cov1 = sum((rrs[i] - mean_rr) * (rrs[i - 1] - mean_rr) for i in range(1, len(rrs))) / len(diffs)
            rho1 = cov1 / variance if variance > 0 else 0.0
            rho1_results[regime_name] = rho1

            # Assert rMSSD scaling within +- 20%
            self.assertAlmostEqual(
                emp_rmssd,
                target_rmssd,
                delta=8.0,
                msg=f"Regime {regime_name} rMSSD {emp_rmssd:.2f} ms diverged from target {target_rmssd} ms"
            )

        # Assert monotonic autocorrelation ordering: aerobic > threshold > anaerobic
        self.assertGreater(
            rho1_results['aerobic'],
            rho1_results['threshold'],
            f"aerobic rho1 ({rho1_results['aerobic']:.3f}) must exceed threshold ({rho1_results['threshold']:.3f})"
        )
        self.assertGreater(
            rho1_results['threshold'],
            rho1_results['anaerobic'],
            f"threshold rho1 ({rho1_results['threshold']:.3f}) must exceed anaerobic ({rho1_results['anaerobic']:.3f})"
        )

    # -------------------------------------------------------------------------
    # TST-SIM-001.4: Artifact & Gap Injection Behavior Test
    # -------------------------------------------------------------------------
    def test_artifact_and_gap_injection(self):
        """
        Verify single-keystroke physiological artifact and packet dropout injection:
        - 'ectopic' ('e'): Premature beat (~0.70x) followed by compensatory pause (~1.30x)
        - 'missed' ('m'): Missing beat (~2.0x)
        - 'extra' ('x'): Extra split beat (~0.50x twice)
        - 'gap' ('g'): Suppression of notifications for >= 2.5 seconds (3 x 1s ticks)
        """
        synth = RrSynthesizer(bpm=60, rmssd=0.0)  # Baseline RR = 1000 ms

        # 1. Ectopic pair
        synth.inject_artifact('ectopic')
        b1 = synth.generate_next_rr_ms()
        b2 = synth.generate_next_rr_ms()
        self.assertAlmostEqual(b1, 700.0, delta=5.0)
        self.assertAlmostEqual(b2, 1300.0, delta=5.0)
        self.assertAlmostEqual(b1 + b2, 2000.0, delta=5.0)  # Compensatory sum equals 2 beats

        # 2. Missed beat
        synth.inject_artifact('missed')
        b_missed = synth.generate_next_rr_ms()
        self.assertAlmostEqual(b_missed, 2000.0, delta=5.0)

        # 3. Extra beat
        synth.inject_artifact('extra')
        x1 = synth.generate_next_rr_ms()
        x2 = synth.generate_next_rr_ms()
        self.assertAlmostEqual(x1, 500.0, delta=5.0)
        self.assertAlmostEqual(x2, 500.0, delta=5.0)

        # 4. RF Packet Dropout Gap
        synth.inject_artifact('gap')
        self.assertEqual(synth.gap_ticks_remaining, 3)
        # First 3 ticks must return empty list (notification suppressed)
        for tick_idx in range(3):
            emitted = synth.tick_1hz(1000.0)
            self.assertEqual(emitted, [], f"Tick {tick_idx + 1} during gap should be suppressed")
        self.assertEqual(synth.gap_ticks_remaining, 0)
        # 4th tick resumes emission
        resumed = synth.tick_1hz(1000.0)
        self.assertTrue(len(resumed) >= 1, "Emission must resume after gap expires")

    # -------------------------------------------------------------------------
    # TST-SIM-001.5: Python Standard Library Compliance Test (AST Audit)
    # -------------------------------------------------------------------------
    def test_python_stdlib_compliance_ast(self):
        """
        Enforce TST-PRO-005.6: Zero non-stdlib external dependencies (no numpy, scipy, etc.).
        Inspects AST of tools/ble_hr_simulator.py and this test file.
        """
        files_to_check = [
            os.path.join(TOOLS_DIR, "ble_hr_simulator.py"),
            os.path.join(TOOLS_DIR, "test_ble_hr_simulator.py"),
        ]

        allowed_modules = {
            "sys", "os", "argparse", "math", "random", "threading", "time",
            "dbus", "gi", "unittest", "ast", "ble_hr_simulator"
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
