# Stage 1 Analysis: ATT-8196 - Export recorded L/R pedal power balance, torque effectiveness, and pedal smoothness in FitFileWriter

**Ticket**: [ATT-8196](https://rainerblind.atlassian.net/browse/ATT-8196) | **Sub-task**: [ATT-8227](https://rainerblind.atlassian.net/browse/ATT-8227) (`[Analysis]`)  
**Parent Epic**: [ATT-6075](https://rainerblind.atlassian.net/browse/ATT-6075) (*Cycling Power Dynamics & Advanced Power Meter Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8196`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
`aTrainingTracker` decodes, normalizes, and persists dual-sided cycling power dynamics in `WorkoutSamples.db`: `SensorType.PEDAL_POWER_BALANCE` (normalized to Right % `0..100%` per `REQ-CON-022`), `TORQUE_EFFECTIVENESS_L`/`_R` (`0..100%`), and `PEDAL_SMOOTHNESS_L`/`_R`/`PEDAL_SMOOTHNESS` (`0..100%`).
While `GCFileWriter.java` exports these to GoldenCheetah JSON, `WorkoutRepository.streamSamples(...)` and `FitFileWriter.java` serialize only `RecordMesg.setPower(...)`, omitting pedal dynamics from Garmin FIT exports. In addition, `FitWorkoutParser.kt` omits them on FIT import, causing data loss on external platforms (Intervals.icu, Strava, TrainingPeaks, GoldenCheetah, WKO5) and breaking lossless `.fit` backup/restore round-trip (`REQ-MIG-053`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**:
  * `WorkoutSampleRecord` (`WorkoutExportHeader.kt:59-77`) lacks fields for balance, TE, and PS.
  * `WorkoutRepository.streamSamples(...)` (`WorkoutRepository.kt:614-685`) does not project the 6 pedal dynamics columns from `WorkoutSamples.db`.
  * `FitFileWriter.java` does not populate `left_right_balance`, `torque_effectiveness`, or `pedal_smoothness` on `RecordMesg`, `LapMesg`, or `SessionMesg`.
  * `FitWorkoutParser.kt:270-282` does not decode these fields from imported FIT `RecordMesg`.
* **Independent Peer Review & Verification**:
  * Verified Garmin FIT SDK v21.141.0: `RecordMesg` supports `setLeftRightBalance(Short)` (`LEFT_RIGHT_BALANCE`, `MASK = 0x7F`, `RIGHT = 0x80`), `setLeft/RightTorqueEffectiveness(Float)` (fields 43/44, `percent`), and `setLeft/Right/CombinedPedalSmoothness(Float)` (fields 45/46/47, `percent`).
  * Verified `LapMesg` (fields 34, 91–95) and `SessionMesg` (fields 37, 101–105) support `setLeftRightBalance(Integer)` (`LEFT_RIGHT_BALANCE_100`, `MASK = 0x3FFF`, `RIGHT = 0x8000`, `0.01%` scale) and `setAvgLeft/RightTorqueEffectiveness` + `setAvgLeft/Right/CombinedPedalSmoothness`.
  * Verified memory invariant: Accumulating online sums/counts during `streamSamples(...)` preserves the $O(1)$ heap memory streaming contract (`REQ-DAT-042.2`) and zero SQLite imports in `FitFileWriter` (`REQ-DAT-042.5`).
  * Blind spot check: Workouts lacking dual-sided dynamics must keep all fields unset (`null`) on `RecordMesg`, `LapMesg`, and `SessionMesg`—avoiding artificial `0%` or `0x8000` artifacts.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Extend `WorkoutSampleRecord` with 6 nullable Double fields with default nulls (100% binary/source compatible).
  * Project the 6 `SensorType` columns in `WorkoutRepository.streamSamples(...)`.
  * Encode `RecordMesg`, `LapMesg`, and `SessionMesg` fields in `FitFileWriter.java` using Garmin SDK bitmasks.
  * Ingest `RecordMesg` pedal dynamics into `bufferedSamples` and `foundSensors` in `FitWorkoutParser.kt`.
  * Verify per-record, summary aggregate, and lossless round-trip behavior in `FitFileWriterTest.kt` and `FitLosslessRoundTripTest.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Zero SQLite schema migrations (columns already exist in `WorkoutSamples.db`).
  * Cockpit UI gauges, Aftermath cards, or BLE/ANT+ driver changes (handled in adjacent `ATT-6075` tickets).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement only (`REQ-EXP-043` / `TST-EXP-043`). Refines `REQ-DAT-042` and complements `REQ-CON-022` and `REQ-MIG-053`. No existing requirements modified.
* **Historical Origin & Commit Trace**: `REQ-CON-022` (`ATT-4554`), `REQ-DAT-042` (`ATT-6698`), `REQ-MIG-053` (`ATT-7747`).
* **Root Reason for Existing Formulation**: `FitFileWriter` initially covered core telemetry and radar developer fields (`REQ-EXP-042`), deferring dual-sided cycling dynamics until the `ATT-6075` power meter initiative.
* **Preservation of Core Invariants**: Right-pedal 0..100% normalization (`REQ-CON-022`) preserved; $O(1)$ streaming memory bounded (`REQ-DAT-042.2`); zero SQLite imports in exporter (`REQ-DAT-042.5`); lossless round-trip (`REQ-MIG-053`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-EXP-043` and `TST-EXP-043` in `docs/requirements/req_data_migration_export.md` and `docs/tests/tst_data_migration_export.md`.
* **Affected Architecture Elements**: `WorkoutExportHeader.kt` (`WorkoutSampleRecord`), `WorkoutRepository.kt` (`streamSamples`), `FitFileWriter.java` (`RecordMesg`, `LapMesg`, `SessionMesg`), `FitWorkoutParser.kt` (`RecordMesgListener`). Zero DB schema or UI changes.
* **Verification Impact & Test Surface**: Targeted unit tests in `FitFileWriterTest.kt` and `FitLosslessRoundTripTest.kt`. Contract test `RepositoryEngineCleanlinessContractTest` verifying zero SQLite imports in `FitFileWriter`. Full clean-room pass on Tier 1 unit test suite.
* **Risk Assessment & Invariants**: `LOW` risk (1 SP). Additive field projection and standard Garmin FIT protocol serialization. Preserves $O(1)$ streaming memory and lossless round-trip fidelity.

---

## 6. Architectural Strategy & High-Level Solution
1. Extend `WorkoutSampleRecord` in `WorkoutExportHeader.kt` with `pedalPowerBalance`, `torqueEffectivenessL/R`, and `pedalSmoothnessL/R/Combined` (`Double? = null`).
2. Project `SensorType.PEDAL_POWER_BALANCE`, `TORQUE_EFFECTIVENESS_L/R`, `PEDAL_SMOOTHNESS_L/R`, and `PEDAL_SMOOTHNESS` in `WorkoutRepository.streamSamples(...)`.
3. In `FitFileWriter.java`: encode `RecordMesg.setLeftRightBalance((short)((Math.round(bal) & LeftRightBalance.MASK) | LeftRightBalance.RIGHT))` and TE/PS floats; accumulate online running sums/counts and write `LeftRightBalance100` (`(Math.round(avg * 100.0) & LeftRightBalance100.MASK) | LeftRightBalance100.RIGHT`) and averages on `LapMesg` and `SessionMesg`.
4. In `FitWorkoutParser.kt`: decode `RecordMesg` pedal dynamics into `bufferedSamples` and `foundSensors`.
5. Verify via `FitFileWriterTest.kt` and `FitLosslessRoundTripTest.kt`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. $O(1)$ heap memory overhead during FIT export stream (`REQ-DAT-042.2`).
  2. Zero SQLite imports or direct database calls in `FitFileWriter.java` (`REQ-DAT-042.5`).
  3. Strict Right-pedal 0..100% normalization invariant without L/R inversion (`REQ-CON-022.1`, `REQ-CON-022.2`).
  4. Clean omission (null/unset) when sensors are absent; zero artificial 0% artifacts.
* **Risk Rating**: **LOW** (1 SP) — Self-contained DTO extension, standard FIT SDK serialization, zero SQLite migrations.
