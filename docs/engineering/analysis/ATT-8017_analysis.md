# Stage 1 Analysis: ATT-8017 — Polar Live Running Cadence & Stride Counter

**Ticket**: [ATT-8017](https://issue-tracker/ATT-8017) (`3 SP`) | **Parent Epic**: `ATT-5910` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8017` | **Author**: `agent1` (`subagent_pro`)  

## 1. Problem Statement & Motivation
- Runners using a Polar H10 (sternum `ACC`) or Verity Sense (armband `ACC` + `GYRO`) without a footpod—or on a treadmill where the smartphone is stationary—lack accurate live running cadence (`SensorType.CADENCE`) and stride counts (`SensorType.STRIDES`).
- `ATT-8016` landed `PolarOnlineStreamingBridge` delivering raw PMD `ACC`/`GYRO` frames, but its provisional 3-point threshold peak detector emits steps/min instead of strides/min, lacks signal conditioning and standstill zeroing, and has no Verity Sense arm-swing `GYRO+ACC` fusion.

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Current Codebase Gaps (`PolarOnlineStreamingBridge.kt:474-586`)**:
  - *Unit Mismatch (`L534-536`)*: Provisional `detectStepPeak()` computes `MILLIS_PER_MINUTE / deltaMs` (steps/min ~170–180) rather than **strides/min** (~85–90), violating parity with `ANTRunSpeedDevice` (`L127-132`), `BTLERunSpeedDevice` (`L147`, BLE RSC `0x2A53`), `TCXFileWriter` (`L322`), and `FitFileWriter` (`L581, L864-867` `UINT8` `RecordMesg.cadence`/`SessionMesg.avgCadence`).
  - *No Band-Pass / Autocorrelation / Arm-Swing Fusion*: `processAccData` uses raw `|mag - 1000| > 180` mG peaks without H10 1.2–4.0 Hz band-pass + 5s autocorrelation or Verity Sense 0.6–2.0 Hz `GYRO` + `ACC` arm-swing envelope tracking; `processGyroData` (`L502-511`) drops `GYRO` frames without feeding cadence estimation.
  - *Missing Standstill Zeroing & 1-Hz Emission*: `mCadenceSensor` stays latched at the last non-zero value indefinitely when a runner stops instead of dropping to `0.0` after `>= 3s` stillness.
  - *First-Stride Baseline Swallowing (`MyAccumulatorSensor.java:44-59`)*: `mStridesSensor.accumulate(N)` treats the first call (`mLastAbsoluteValue == null`) as baseline without incrementing `mValue`; `syncCadenceSensorRegistration` (`L565-568`) never seeds `accumulate(0)`.
- **Independent Peer Review & Verification**:
  - Verified `FitFileWriter.kt` (`L581, L864-867`) already writes `SensorType.CADENCE` to `RecordMesg.cadence` and `SessionMesg.avgCadence`/`maxCadence` without scaling, so emitting strides/min in `PolarOnlineStreamingBridge` yields immediate FIT/Strava parity (~170–180 spm display on Strava for 85–90 strides/min) with zero exporter changes.
  - Confirmed zero UI files require modification (non-UI DSP ticket).

## 3. Scope Grounding & Non-Goals
- **In-Scope**: Pure-Kotlin `PolarLiveRunningCadenceEstimator` (H10 sternum `ACC` 1.2–4.0 Hz band-pass + adaptive peak + 5s autocorrelation; Verity Sense `GYRO+ACC` 0.6–2.0 Hz arm-swing fusion; `>= 3s` stillness -> `0.0`; 1-Hz emission; strides/min + 2-step stride count); wiring into `PolarOnlineStreamingBridge.kt` + seeding `mStridesSensor.accumulate(0)`.
- **Out-of-Scope**: Offline `ACC` flash recording/backfill (`PolarVeritySenseOfflineController` remains `HR`-only per `REQ-SET-112.3`); UI changes; `FitFileWriter`/`TCXFileWriter` edits; `ANT-Android-SDKs` edits.
- **Stage 4 Risk Classification (`Rule 7.1`)**: **HIGH-RISK** — Dynamical DSP / autocorrelation & signal-processing engine + `PolarOnlineStreamingBridge` integration (`subagent_pro` Stage 4 Doing / `subagent_flash` Gate 4; zero UI screenshot required).

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: Extends `REQ-SET-112.2` / `REQ-SET-112.4` (`PolarOnlineStreamingBridge.kt`) and relies on `REQ-EXP-001` (`FitFileWriter.kt`, `TCXFileWriter.kt`) & `REQ-SEN-021` (`MySensorManager.CADENCE_PRIORITY`).
- **Historical Origin & Commit Trace**: `f4bef44c` (`ATT-8016` landed `PolarOnlineStreamingBridge`); `ANTRunSpeedDevice.java` / `BTLERunSpeedDevice.java` established strides/min `SensorType.CADENCE` convention.
- **Root Reason for Existing Formulation**: Running cadence in FIT/TCX/ANT+/BLE RSC uses full 2-step strides/min (`UINT8` 0–254) and cumulative stride counts.
- **Preservation of Core Invariants**: Preserve `CADENCE_PRIORITY` (`RUN_SPEED` / `BIKE_*` > `HRM`), `BSportType.RUN`-only gating, `MyAccumulatorSensor` baseline semantics, and `FitFileWriter` serialization unchanged.

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Net-new `REQ-SET-113` (Polar live running cadence & stride estimation); preserves `REQ-SET-112`, `REQ-EXP-001`, `REQ-SEN-021`.
- **Affected Architecture Elements**: New `PolarLiveRunningCadenceEstimator.kt`, updated `PolarOnlineStreamingBridge.kt`, updated `docs/architecture.md` (`Rule 8.8`).
- **Verification Impact & Test Surface**: Synthetic H10 `ACC` (50/100 Hz) and Verity Sense `ACC+GYRO` (52 Hz) DSP accuracy tests across 60–100 strides/min (±2 strides/min), armband rotation invariance, `>= 3s` standstill zeroing, `MyAccumulatorSensor` first-stride count, `PolarOnlineStreamingBridge` 1-Hz emission, and FIT cadence scaling verification.
- **Risk Assessment & Invariants**: Band-pass settling lag, harmonic doubling/halving (1x vs 2x step frequency), and sample-rate jitter (50 vs 100 vs 200 vs 52 Hz) mitigated via timestamp-driven sample-rate tracking (`dt`), fundamental-vs-harmonic autocorrelation weighting, and `mCadenceSensor` strides/min invariant.

## 6. System Invariants & Risk Assessment
- **Core Invariants**: `SensorType.CADENCE` published strictly in strides/min (`36–120` active or `0.0` standstill); `BSportType.RUN`-only gating & dedicated sensor priority preserved; zero UI/DB/exporter changes; `ANT-Android-SDKs` stays `160000`.
- **Risk Rating**: **HIGH** (DSP frequency estimation, harmonic disambiguation, `PolarOnlineStreamingBridge` live stream integration).
