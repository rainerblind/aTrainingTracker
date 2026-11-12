#!/usr/bin/env python3
"""
Unit test suite for tools/ble_hr_simulator.py.
Verifies BLE 0x2A37 Bit 4 RR interval packing, phase-locked beat accumulator drift with real variance,
AR(1) DFA alpha-1 regimes, rMSSD innovation scaling, artifact/gap injection with packet drop,
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
    # TST-SIM-001.2: Phase-Locked Beat Accumulator Drift Test (with rmssd=35.0 & 50.0)
    # -------------------------------------------------------------------------
    def test_phase_locked_accumulator_drift(self):
        """
        Simulate 60 seconds (60 ticks of 1000.0 ms) across standard human heart rate
        range (40 to 180 BPM) with real physiological variance (rmssd=35.0 ms).
        Assert that:
        1. Cumulative emitted RR duration + residual phase strictly matches 60,000 ms (< 1.0 ms error).
        2. Residual wall-clock drift is strictly bounded by <= 1 beat interval (< 2000 ms DRIFT_THRESHOLD_MS).
        3. Specifically at 150 BPM, timing drift is bounded (< 400 ms), eliminating runaway drift.
        4. Over extended 300 seconds (5 minutes) at rmssd=50.0 ms, timing drift stays bounded.
        """
        test_bpms = [40, 50, 60, 75, 80, 90, 100, 120, 135, 150, 160, 180]

        # 1. Real physiological variance (rmssd=35.0 ms, 60 seconds)
        for bpm in test_bpms:
            synth = RrSynthesizer(bpm=bpm, rmssd=35.0)
            all_emitted_raw = []
            for _ in range(60):
                ticks_raw = synth.tick_1hz(1000.0)
                all_emitted_raw.extend(ticks_raw)

            total_emitted_ms = sum(raw * 1000.0 / 1024.0 for raw in all_emitted_raw)
            drift_ms = 60000.0 - total_emitted_ms
            rr_mean = 60000.0 / bpm

            # Total tracked time must strictly conserve wall-clock time
            self.assertAlmostEqual(
                total_emitted_ms + synth.phase_ms,
                60000.0,
                delta=1.0,
                msg=f"BPM {bpm} phase conservation violated: total={total_emitted_ms:.1f}, phase={synth.phase_ms:.1f}"
            )

            # Cumulative drift must be at most 1 in-progress beat interval, well under DRIFT_THRESHOLD_MS (2000 ms)
            max_allowed_drift = rr_mean + 150.0
            self.assertLessEqual(
                drift_ms,
                max_allowed_drift,
                f"BPM {bpm} exceeded 1-beat drift threshold: drift={drift_ms:.1f} ms > {max_allowed_drift:.1f} ms"
            )
            self.assertGreaterEqual(
                drift_ms,
                0.0,
                f"BPM {bpm} negative drift (emitted beats in the future): drift={drift_ms:.1f} ms"
            )

            # Specifically at 150 BPM, verify drift is well within a single beat (< 400 ms)
            if bpm == 150:
                self.assertLess(
                    abs(drift_ms),
                    400.0,
                    f"BPM 150 failed sub-400ms timing drift check: drift={drift_ms:.1f} ms"
                )

        # 2. Baseline deterministic pacing (rmssd=0.0)
        for bpm in [60, 120, 135]:
            synth_zero = RrSynthesizer(bpm=bpm, rmssd=0.0)
            raw_list = []
            for _ in range(60):
                raw_list.extend(synth_zero.tick_1hz(1000.0))
            tot_ms = sum(r * 1000.0 / 1024.0 for r in raw_list)
            self.assertLess(abs(tot_ms - 60000.0), 2.0)

        # 3. Long-running simulation (300 seconds = 5 minutes) with high variance (rmssd=50.0 ms)
        for bpm in [135, 150]:
            synth_long = RrSynthesizer(bpm=bpm, rmssd=50.0)
            long_raw = []
            for _ in range(300):
                long_raw.extend(synth_long.tick_1hz(1000.0))
            tot_long_ms = sum(r * 1000.0 / 1024.0 for r in long_raw)
            long_drift = 300000.0 - tot_long_ms
            rr_mean = 60000.0 / bpm
            self.assertLessEqual(
                long_drift,
                rr_mean + 150.0,
                f"BPM {bpm} 300s drift {long_drift:.1f} ms exceeded 1 beat interval"
            )
            self.assertGreaterEqual(
                long_drift,
                0.0,
                f"BPM {bpm} 300s negative drift: {long_drift:.1f} ms"
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
        - 'extra' ('x'): Extra split beat (~0.50x twice, emitted in same 1s tick at 60 BPM)
        - 'gap' ('g'): Suppression of notifications for >= 2.5 seconds (3 x 1s ticks),
          dropping intermediate beats so resume packet is NOT an oversized buffer dump.
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

        # 3. Extra beat: both halves emitted in same 1-second tick at 60 BPM
        synth_extra = RrSynthesizer(bpm=60, rmssd=0.0)
        synth_extra.inject_artifact('extra')
        extra_emitted = synth_extra.tick_1hz(1000.0)
        self.assertEqual(len(extra_emitted), 2, "Both extra beat halves must be emitted in the same tick")
        self.assertAlmostEqual(extra_emitted[0] * 1000.0 / 1024.0, 500.0, delta=5.0)
        self.assertAlmostEqual(extra_emitted[1] * 1000.0 / 1024.0, 500.0, delta=5.0)

        # 4. RF Packet Dropout Gap with true over-the-air packet loss
        synth_gap = RrSynthesizer(bpm=60, rmssd=0.0)
        synth_gap.inject_artifact('gap')
        self.assertEqual(synth_gap.gap_ticks_remaining, 3)
        # First 3 ticks must return empty list (notification suppressed)
        for tick_idx in range(3):
            emitted = synth_gap.tick_1hz(1000.0)
            self.assertEqual(emitted, [], f"Tick {tick_idx + 1} during gap should be suppressed")
        self.assertEqual(synth_gap.gap_ticks_remaining, 0)

        # 4th tick resumes emission: must emit normal 1-second beats (~1 beat at 60 BPM), NOT a bloated 4-second buffer dump!
        resumed = synth_gap.tick_1hz(1000.0)
        self.assertEqual(len(resumed), 1, "Resumed packet at 60 BPM must contain exactly 1 beat, not a bloated buffer dump")
        self.assertAlmostEqual(resumed[0] * 1000.0 / 1024.0, 1000.0, delta=5.0)

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
