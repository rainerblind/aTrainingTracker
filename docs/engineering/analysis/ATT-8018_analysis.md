# Stage 1 Analysis: ATT-8018 — Polar H10 Live Running Dynamics (200 Hz ACC) & FIT Export

**Ticket**: [ATT-8018](https://issue-tracker/ATT-8018) (`3 SP`) | **Parent Epic**: `ATT-5910` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8018` | **Author**: `agent1` (`subagent_flash`, Role Rotation `Rule 7.1`)  

## 1. Problem Statement & Motivation
- The Polar H10 chest strap sits near the runner's center of mass and supports 3-axis `ACC` streaming at **200 Hz** (5 ms resolution). Building on `ATT-8016` (`PolarOnlineStreamingBridge`) and `ATT-8017` (`PolarLiveRunningCadenceEstimator`), live 200 Hz sternum `ACC` can yield **Vertical Oscillation (cm)** and **Ground Contact Time (GCT, ms)** for Cockpit display, 1-Hz `WorkoutSamples.db` / `WorkoutSummaries.db` persistence, Aftermath summaries, and native Garmin FIT Running Dynamics export.

## 2. Root Cause Analysis & Independent Peer Review & Verification
- **Codebase Gaps Verified**:
  1. *200 Hz ACC Never Activated (`PolarOnlineStreamingBridge.kt:135, 345`)*: `highRateRunningDynamicsActive` defaults to `false` and has zero callers setting it `true`, capping H10 `ACC` at 100/50 Hz instead of 200 Hz (`H10_HIGH_RATE_ACC_HZ`).
  2. *Missing Biomechanics DSP & Sensor Channels*: No `PolarH10RunningDynamicsEstimator` exists; `SensorType.java` lacks `VERTICAL_OSCILLATION` and `GROUND_CONTACT_TIME`; `ActivityType.RUN_SPEED_AND_CADENCE` (`ActivityType.java:97-117`) and `DeviceType.getDeviceTypeList()` (`DeviceType.java:125`) lack these channels; `res/values/strings.xml` has `units_time_ms` (`L162`) but lacks `units_cm` and localized metric labels across 9 locales.
  3. *Persistence & Aftermath Wiring*: `WorkoutSamples.db` builds columns dynamically from `SensorType.values()` (`TrackerService.java:1271`, `WorkoutSamplesDatabaseManager.makeColumns` `L1444`), and `WorkoutSummaries.db` `TABLE_EXTREMA_VALUES` stores arbitrary `SensorType` extrema **without schema bumps**. However, `TrackerService.IMPORTANT_SENSOR_TYPES`/`SENSORS_TO_TRACK` (`L165-181`), `WorkoutDataMapper.sensorsToCheck` (`L68`), and `WorkoutRepository.calculateAndSaveExtrema()` (`L1393`) currently omit Running Dynamics, and `WorkoutSampleRecord` (`WorkoutExportHeader.kt:59-83`) + `WorkoutRepository.streamSamples()` (`L657-725`) do not project them.
  4. *FIT SDK Unit Scaling Trap (`FitFileWriter.kt`, Garmin FIT SDK `21.141.0`)*: Inspected `RecordMesg.java` (`L903, L943`), `LapMesg.java` (`L2040, L2080`), and `SessionMesg.java` (`L2485, L2525`): `setVerticalOscillation(Float)` and `setAvgVerticalOscillation(Float)` expect **millimeters (`mm`)**, whereas Cockpit/UI displays **centimeters (`cm`)** — requiring exact `cm * 10f` conversion in `FitFileWriter.kt`! (`setStanceTime` / `setAvgStanceTime` expect `ms` as `Float`).
- **Side-Effect Audit (`Rule 68`)**: `SensorCategoryAndSelectorContractTest.kt` (`L109-115, L227-256`) strictly asserts every `SensorType` partition; `PolarLiveRunningCadenceEstimator` already resamples arbitrary rates onto 50 Hz so switching H10 to 200 Hz preserves `ATT-8017` cadence accuracy.

## 3. Scope Grounding & Non-Goals
- **In-Scope**: Pure-Kotlin `PolarH10RunningDynamicsEstimator` (200 Hz ~0.25 Hz gravity alignment, per-step detrended double-integration VO in cm over 5–8 steps, 5 ms foot-strike-to-toe-off GCT in 140–400 ms with `< 130 spm` walking GCT suppression); enabling 200 Hz on H10 `BSportType.RUN` in `PolarOnlineStreamingBridge`; `SensorType.VERTICAL_OSCILLATION` & `GROUND_CONTACT_TIME` + `ActivityType` + `DeviceType` + 9-locale strings; `TrackerService`/`WorkoutRepository`/`WorkoutDataMapper` persistence & Aftermath extrema; `FitFileWriter.kt` (`RecordMesg`, `LapMesg`, `SessionMesg`).
- **Out-of-Scope**: Left/Right Ground Contact Balance (single sternum ACC cannot reliably separate L/R without footpod/asymmetrical assumptions, and AC 1–2 scope VO + GCT); Verity Sense armband VO/GCT; `WorkoutSamples.db`/`WorkoutSummaries.db` schema bumps; `ANT-Android-SDKs`.
- **Complexity & Risk (`REQ-PRO-029`, `Rule 7.1`)**: `3 SP` confirmed (no split needed); **HIGH-RISK** -> Role Rotation applies (`agent1` Flash: S1, G2+S3, G4+S5; `agent2` Pro: G1+S2, G3+S4, G5+merge).

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-SET-112` / `REQ-SET-114` (`PolarOnlineStreamingBridge`, `PolarLiveRunningCadenceEstimator`), `REQ-TRK-070` (`SensorCategory` / `SensorCategoryAndSelectorContractTest`), `REQ-DAT-042` (`WorkoutRepository.streamSamples`), `REQ-EXP-043` (`FitFileWriter.kt`).
- **Historical Origin & Commit Trace**: `438233eb` (`ATT-8016`/`ATT-8017` Polar PMD bridge & cadence DSP), `ATT-6726` (`SensorCategory` partitions), `ATT-7877`/`ATT-8196` (`FitFileWriter.kt` Kotlin migration & dynamics).
- **Root Reason for Existing Formulation**: `highRateRunningDynamicsActive` was pre-wired as a flag in `ATT-8016` to avoid burning 200 Hz BLE bandwidth until Running Dynamics existed; FIT SDK mandates `mm` for vertical oscillation.
- **Preservation of Core Invariants**: Keep 50/100 Hz default when Running Dynamics is inactive (e.g., Verity Sense or non-running); preserve `CADENCE_PRIORITY`, `ATT-8017` strides/min, zero SQLite schema bumps, and `SensorCategoryAndSelectorContractTest` parity.

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Net-new `REQ-SET-115` (Polar H10 200 Hz Running Dynamics & FIT Export); preserves `REQ-SET-112`, `REQ-SET-114`, `REQ-TRK-070`, `REQ-DAT-042`.
- **Affected Architecture Elements**: New `PolarH10RunningDynamicsEstimator.kt`; `PolarOnlineStreamingBridge.kt`, `SensorType.java`, `ActivityType.java`, `DeviceType.java`, `TrackerService.java`, `WorkoutExportHeader.kt`, `WorkoutRepository.kt`, `WorkoutDataMapper.kt`, `FitFileWriter.kt`, `res/values*/strings*.xml` (9 locales), `docs/architecture.md`.
- **Verification Impact & Test Surface**: Synthetic 200 Hz sternum biomechanics unit tests (tilted gravity axes, 150–195 spm VO/GCT bounds, `< 130 spm` walking GCT suppression, standstill reset), `PolarOnlineStreamingBridgeTest` (200 Hz negotiation & channel publishing), `SensorCategoryAndSelectorContractTest`, `ActivityTypeTest`, `FitFileWriterTest` (`mm` VO & `ms` GCT in `RecordMesg`/`LapMesg`/`SessionMesg`), `TranslationParityTest`.
- **Risk Assessment & Invariants**: Integration drift mitigated by per-step zero-mean detrending; walking double-support false positives prevented by `< 130 spm` gate; `cm -> mm` FIT scaling enforced explicitly.

## 6. System Invariants & Risk Assessment
- **Core Invariants**: `FitFileWriter` encodes VO in `mm` (`cm * 10f`) and stance time in `ms`; walking (`< 130 spm`) emits no invalid GCT; `BSportType.RUN`-only gating & `CADENCE_PRIORITY` preserved; zero DB schema bumps; `ANT-Android-SDKs` stays `160000`.
- **Risk Rating**: **HIGH** (200 Hz sternum double-integration biomechanics DSP, `TrackerService`/`SensorType` core telemetry, and binary FIT SDK encoding).
