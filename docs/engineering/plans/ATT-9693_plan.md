# Stage 3 Implementation Plan — ATT-9693: Datum-Invariant 3-State EKF & Walking-Speed ERTS Regularization

- **Ticket**: `ATT-9693` (`3 SP`, Bug, `tier-a`, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Traceability**: `REQ-FIL-048` <-> `TST-FIL-048`
- **Production Files (2 files, both `<= 1,500` LoC)**: `KinematicAltitudeSlopeEkf.kt` (`697 -> ~740` LoC), `ExtendedRtsAltitudeSlopeSmoother.kt` (`757 -> ~815` LoC). Zero UI, resource, manifest, or SQLite schema edits.

## 1. SWE.2 Architecture & Mathematical Invariants
1. **Datum-Invariant Reference Coupling (`H_ref = [1, 0, 0]`, `H_baro = [1, 0, 1]`)**:
   - When a trusted barometer is active (`baroTrusted` in EKF; `hasBaroData` in ERTS), `updateReferenceAltitude` sets `k1 = 0.0` and **`k2 = -k0`**.
   - Exact invariants: `Δ(h + b_baro) = (k0 - k0) * y = 0`; `[1, 0, 1] (I - K H_ref) = [1, 0, 1]`; `[1, 0, 1] K = 0` -> `var(h + b_baro)` and `cov(g, h + b_baro)` are identically invariant across DEM/GPS updates.
   - Barometer-free (`!hasBarometerSample` / `!hasBaroData`) and corrupted-baro (`varBaro > 16.0`) paths retain `k2 = p20 / s` and DEM slope coupling untouched.
2. **Exact Symmetric 3×3 Joseph SPD Updates**:
   - Both `updateBarometer()` (`H = [1, 0, 1]`) and `updateReferenceAltitude()` (`H = [1, 0, 0]`) in `KinematicAltitudeSlopeEkf` use exact Joseph form `P+ = (I - K H) P (I - K H)^T + K R K^T`, guaranteeing symmetry and strict positive-definiteness even under damped `k1Eff` (`gainScale < 1.0`) and `k2 = -k0`.
3. **Orthogonal Walking-Speed ERTS Regularization**:
   - Keep `ExtendedRtsAltitudeSlopeSmoother.lowSpeedSlopeGainFactor(speedMps)` unchanged (`TST-FIL-033.1`).
   - Add `internal fun walkingSpeedSlopeProcessScale(speedMps: Double): Double` interpolating `[WALKING_SLOPE_QG_FLOOR = 0.04, 1.0]` quadratically over `[0.3, 2.8] m/s` (`0.0` for non-finite/`< 0.3`, `1.0` for `>= 2.8`), multiplying `qgVal` in `smoothContiguousSegment()` (works identically on baro and baro-free phones).
   - Seed `x[2] = firstSample.rawAltitudeM - firstSample.referenceAltitudeM` when `hasInitialRef && firstSample.rawAltitudeM.isFinite()` so `> 50 m` QNH weather offsets never saturate `applyHuber()`.
   - When `hasBaroData == true`, use `BARO_EQUIPPED_VAR_DEM = 16.0` + post-anchor step-discrepancy inflation (`|deltaRef - deltaBaro| > 3.0 m` -> `baseVar * (1 + disc²)`) in ERTS reference updates; keep `DEFAULT_VAR_DEM = 4.0` when `hasBaroData == false`.

## 2. Bite-Sized Atomic Implementation Steps (Post-Gate 3 ONLY)
- **Step 1 (`KinematicAltitudeSlopeEkf.kt`)**:
  - Add `private fun josephUpdate3x3Baro(k0, k1, k2, rVar)` for `H = [1, 0, 1]` and replace non-Joseph subtraction in 3-State `updateBarometer()` (`lines 429–441`).
  - In `updateReferenceAltitude()` (`lines 571–578`), set `val k2 = if (baroTrusted) -k0 else p20 / s` and pass into existing `josephUpdate3x3(k0, k1Eff, k2, effectiveVarRef)`.
  - Preserve `@Synchronized` locking and all public constants/signatures (`InterfaceSignatureDriftContractTest`).
- **Step 2 (`ExtendedRtsAltitudeSlopeSmoother.kt`)**:
  - Add constants `WALKING_SLOPE_REG_CEILING_MPS = 2.8`, `WALKING_SLOPE_QG_FLOOR = 0.04`, `BARO_EQUIPPED_VAR_DEM = 16.0`, `DEM_STEP_GATE_THRESHOLD_M = 3.0` and `internal fun walkingSpeedSlopeProcessScale(speedMps: Double): Double`.
  - In `smoothContiguousSegment()`: (a) seed `x[2] = firstSample.rawAltitudeM - firstSample.referenceAltitudeM` when `hasInitialRef && firstSample.rawAltitudeM.isFinite()`; (b) multiply `qgVal` by `walkingSpeedSlopeProcessScale(speedMps)`; (c) in Step 3 reference ingestion, when `hasBaroData` use `BARO_EQUIPPED_VAR_DEM` + `|deltaRef - deltaBaro| > 3.0` step-discrepancy variance inflation and `k2 = -k0`, updating `lastRefAltM` / `lastRefBaroAltM` across ticks.
- **Step 3 (Unit Tests — `KinematicAltitudeSlopeEkfTest.kt`, `ExtendedRtsAltitudeSlopeSmootherTest.kt`)**:
  - Add `TST-FIL-048.1` tests: 7-min `+12 m` urban DEM plateau (`varBaro = 1.5` & `4.0 m²`), `assertSymmetricSpd3x3` under damped `gainScale < 1.0`, `36,000`-tick SPD stability, and `12%` hiking climb / `10%` cycling descent (baro & baro-free).
  - Add `TST-FIL-048.2` & `TST-FIL-048.3` tests: `walkingSpeedSlopeProcessScale()` helper bounds, `108 m` QNH offset + `0.20 m/s` walking noise + `+12 m` DEM plateau (`abs(slope) < 5%`), and `12%` hiking climb (`1.2 m/s`, `100 s`) + `10%` cycling descent (`12 m/s`, `100 s`) for both baro-equipped and baro-free phones.
- **Step 4 (Verification & Replay)**:
  - Run incremental `--rerun` targeted suites (`KinematicAltitudeSlopeEkfTest`, `ExtendedRtsAltitudeSlopeSmootherTest`, `AltitudeAndSlopeDevice_ATTFusedTest`, `ErtsPostWorkoutRecomputerTest`, `OfflineTelemetryReplayHarnessTest`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`).
  - Run `./gradlew :app:replayRawTelemetry` across all 4 archived Pixel 10 logs (`2026-11-23_093732`, `_171814`, `_182416`, `2026-11-22_081402`) and verify before/after ERTS & EKF slope improvements.
  - Promote `REQ-FIL-048` / `TST-FIL-048` to `[Verified]`, author walkthrough, run `tools/auto_gate.py --ticket ATT-9693 --tier tier-a --base-ref sprint/2026-11-25_01_Früh --write-bundle --advance-jira` (respecting `Rule 1.3` if pre-existing `ATT-9639` surfaces), and advance to `Final Review (Agent)`.

## 3. Gate 3 Review Focus
- Confirm mathematical SPD preservation of `k2 = -k0` + `josephUpdate3x3Baro` (`H = [1, 0, 1]`), zero regressions on `TST-FIL-033.1`/`.3`, `TST-FIL-037.1`/`.2`, `TST-FIL-040.2`/`.4`, `TST-FIL-045.3`, and `TST-STB-025.4`, and `0` edits under `app/src/main/` prior to Gate 3 PASS.
