# Stage 5 Walkthrough - ATT-8017: Polar Live Running Cadence & Stride Counter

**Ticket**: [ATT-8017](https://rainerblind.atlassian.net/browse/ATT-8017) (`3 SP`) | **Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Sprint**: `2026-47.1`  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8017` | **Stage 4 Commit**: `cceee132`  
**Requirements**: `REQ-SET-114` (`REQ-SET-114.1`..`REQ-SET-114.4`) | **Test Spec**: `TST-SET-116` (`TST-SET-116.1`..`TST-SET-116.5`)  
**Author**: AI Agent 2 (`Lane 3 Flash`, Stage 5 Verification) | **UI Changes**: None (non-UI DSP engine)

---

## 1. Implementation Summary
- **Pure-Kotlin DSP Engine (`PolarLiveRunningCadenceEstimator.kt`, `REQ-SET-114.1`..`114.4`)**:
  - Resamples arbitrary input streams (`50`/`100`/`200 Hz` H10, `52 Hz` Verity Sense) onto a uniform `50 Hz` (`20 ms`) grid (`UniformGridResampler`).
  - **H10 Chest (`Mode.H10_CHEST`)**: `1.2–4.0 Hz` 2nd-order Butterworth band-pass on acceleration magnitude + `5 s` sliding normalized autocorrelation (`DoubleRingBuffer`) with parabolic sub-sample interpolation and fundamental/harmonic disambiguation, halved to canonical **strides/min** (`36.0–120.0`), plus adaptive step peak detection (`+1` stride per `2` steps).
  - **Verity Sense Armband (`Mode.VERITY_SENSE_ARM`)**: `0.6–2.0 Hz` rotation-invariant 3D vector autocorrelation fusing `GYRO` (`0.70`) + `ACC` (`0.30`) arm-swing cycles (falling back to `ACC` when `GYRO` is absent) and principal-axis peak tracking (`+1` stride per cycle).
  - **1-Hz Updates & Standstill Zeroing**: Emits steady `1 Hz` `SensorType.CADENCE` updates and deterministically zeros to `0.0` within `2 s` (`<= 3 s`) of standstill without phantom strides.
- **Bridge Integration (`PolarOnlineStreamingBridge.kt`) & Docs (`docs/architecture.md`)**:
  - Wired `onAccSample` / `onGyroSample` under `cadenceEstimatorLock`, seeded `mStridesSensor.accumulate(0)` on `BSportType.RUN` registration so stride `1` counts immediately, reset estimator on unregister/sport switch, and updated `docs/architecture.md` Section 3.4.

---

## 2. Verification Evidence (`Rule 7.5` — Reused Stage 4 Suite at Unchanged Code SHA `cceee132`)
- **Tier 1 Targeted Unit & Parity Suite (`26/26 PASS`, `0 failures`, `0 errors`, `0 skipped`)**:
  - `PolarLiveRunningCadenceEstimatorTest`: **5/5 PASS** (H10 170 spm -> 85 strides/min, 2f harmonic/drift rejection + 200 Hz, Verity 52 Hz rotated axes, 1-Hz emission + standstill zeroing).
  - `PolarOnlineStreamingBridgeTest`: **5/5 PASS** (H10/Verity bridge wiring, `accumulate(0)` stride `1` baseline seeding, `BSportType.RUN`/`BIKE` gating, `HR`/`RR`).
  - `MySensorManagerCadenceArbitrationTest`: **2/2 PASS** (`RUN_SPEED`/`BIKE_*` priority above Polar `HRM`).
  - `FitFileWriterTest`: **14/14 PASS** (canonical strides/min `RecordMesg.cadence`/`SessionMesg.avgCadence` parity).
- **Non-UI Exemption**: Pure DSP engine with zero UI/resource edits; no emulator screenshot or `TranslationParityTest` required.

---

## 3. Traceability & Governance Matrix

| Spec ID | Scope | Evidence | Status |
| :--- | :--- | :--- | :--- |
| `TST-SET-116.1` | H10 `ACC` `1.2–4.0 Hz` + `5s` ACF | `PolarLiveRunningCadenceEstimatorTest` (5/5) | Verified |
| `TST-SET-116.2` | Verity `GYRO+ACC` `0.6–2.0 Hz` Fusion | `PolarLiveRunningCadenceEstimatorTest`, `PolarOnlineStreamingBridgeTest` | Verified |
| `TST-SET-116.3` | `1 Hz` Updates & `<= 3s` Standstill `0.0` | `PolarLiveRunningCadenceEstimatorTest`, `PolarOnlineStreamingBridgeTest` | Verified |
| `TST-SET-116.4` | `mStridesSensor.accumulate(0)` Seeding | `PolarOnlineStreamingBridgeTest` (5/5) | Verified |
| `TST-SET-116.5` | Strides/Min FIT Export Parity | `FitFileWriterTest` (14/14), `MySensorManagerCadenceArbitrationTest` (2/2) | Verified |
