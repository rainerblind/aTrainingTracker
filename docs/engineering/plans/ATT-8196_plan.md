# Stage 3: Implementation Plan - ATT-8196: Export recorded L/R pedal power balance, torque effectiveness, and pedal smoothness in FitFileWriter

**Ticket**: [ATT-8196](https://rainerblind.atlassian.net/browse/ATT-8196) | **Sub-task**: [ATT-8233](https://rainerblind.atlassian.net/browse/ATT-8233) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6075](https://rainerblind.atlassian.net/browse/ATT-6075) (*Cycling Power Dynamics & Advanced Power Meter Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8196`  
**Requirement Mapping**: `REQ-EXP-043` | **Test Mapping**: `TST-EXP-043` (`TST-EXP-043.1`..`TST-EXP-043.3`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
`aTrainingTracker` records dual-sided cycling dynamics in `WorkoutSamples.db` (`SensorType.PEDAL_POWER_BALANCE`, `TORQUE_EFFECTIVENESS_L/R`, `PEDAL_SMOOTHNESS_L/R/Combined`), but `FitFileWriter.java` currently omits them from `.fit` exports and `FitWorkoutParser.kt` omits them on import, losing pedal metrics on external platforms (Intervals.icu, Strava, GoldenCheetah, WKO5) and breaking lossless `.fit` backup/restore (`REQ-MIG-053`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-EXP-043` (*Standard Garmin FIT Pedal Power Balance, Torque Effectiveness & Pedal Smoothness Serialization and Lossless Round-Trip*)
* **Test Mapping**: `TST-EXP-043` (`TST-EXP-043.1`..`TST-EXP-043.3`)

---

## 3. System Invariants & Preserved Behavior
1. **$O(1)$ Memory Overhead**: Online streaming sums/counts maintain bounded memory consumption during sample iteration (`REQ-DAT-042.2`).
2. **Encapsulation Cleanliness**: Zero direct SQLite imports or queries in `FitFileWriter.java` (`REQ-DAT-042.5`).
3. **Right-Pedal 0..100% Invariant**: `PEDAL_POWER_BALANCE` strictly follows normalized Right-% (`REQ-CON-022.1`); `0x80` / `0x8000` bitmask ensures zero L/R inversion.
4. **Clean Absence**: Workouts lacking dual-sided dynamics omit all fields cleanly (`null`) without writing 0% artifacts.
5. **Human Decision Gate**: Terminal closure of parent ticket is reserved for the human user.

---

## 4. Proposed Architectural Changes

### Component 1: Sample Record DTO (`ui/aftermath/WorkoutExportHeader.kt`)
* Extend `WorkoutSampleRecord` with 6 nullable Double fields: `pedalPowerBalance: Double? = null`, `torqueEffectivenessL: Double? = null`, `torqueEffectivenessR: Double? = null`, `pedalSmoothnessL: Double? = null`, `pedalSmoothnessR: Double? = null`, and `pedalSmoothnessCombined: Double? = null`.

### Component 2: Streaming Projection (`ui/aftermath/WorkoutRepository.kt`)
* In `streamSamples(...)`, project columns `SensorType.PEDAL_POWER_BALANCE`, `TORQUE_EFFECTIVENESS_L/R`, and `PEDAL_SMOOTHNESS_L/R/Combined` via `getDoubleOrNull(idx)` into `WorkoutSampleRecord`.

### Component 3: Standard Garmin FIT Serialization (`exporter/writer/FitFileWriter.java`)
* `RecordMesg`: Encode `LeftRightBalance` with `LeftRightBalance.RIGHT` (0x80) bitmask (`(Math.round(bal) & 0x7F) | 0x80`), `leftTorqueEffectiveness`, `rightTorqueEffectiveness`, `leftPedalSmoothness`, `rightPedalSmoothness`, and `combinedPedalSmoothness`.
* `LapMesg` & `SessionMesg`: Accumulate streaming sums/counts in $O(1)$ memory; write `LeftRightBalance100` (`(Math.round(avg * 100.0) & 0x3FFF) | 0x8000`) and average TE/PS; omit when absent.

### Component 4: Lossless FIT Ingestion (`migration/parsers/FitWorkoutParser.kt`)
* Decode `record.leftRightBalance` (verifying `LeftRightBalance.RIGHT` bitmask), `leftTorqueEffectiveness`, `rightTorqueEffectiveness`, `leftPedalSmoothness`, `rightPedalSmoothness`, and `combinedPedalSmoothness` into `bufferedSamples` and `foundSensors`.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

1. **Step 1 (`REQ-EXP-043.1`)**: Add 6 nullable fields to `WorkoutSampleRecord` in `WorkoutExportHeader.kt`.
2. **Step 2 (`REQ-EXP-043.1`)**: Project the 6 `SensorType` columns in `WorkoutRepository.streamSamples(...)`.
3. **Step 3 (`REQ-EXP-043.2`, `REQ-EXP-043.3`)**: Implement `RecordMesg`, `LapMesg`, and `SessionMesg` pedal dynamics serialization and $O(1)$ online accumulation in `FitFileWriter.java`.
4. **Step 4 (`REQ-EXP-043.4`)**: Implement pedal dynamics decoding in `FitWorkoutParser.kt` `RecordMesgListener`.
5. **Step 5 (`TST-EXP-043.1`, `TST-EXP-043.2`)**: Add unit & round-trip tests to `FitFileWriterTest.kt` and `FitLosslessRoundTripTest.kt`.
6. **Step 6 (Tier 1 Verification)**: Execute targeted unit tests:
   `./gradlew :app:testDebugUnitTest --tests com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriterTest --tests com.atrainingtracker.trainingtracker.migration.FitLosslessRoundTripTest`.

---

## 6. Stage 4 Execution Model & Verification Plan
* **Stage 4 Risk Classification**: **Standard (`LOW` Risk, 1 SP)**. Assigned to **`subagent_flash`** for Stage 4 Doing (`--as agent1`), audited by **`subagent_pro`** at Gate 4 (`--as agent2`).
* **Rollback Plan**: Isolated on detached `HEAD` ref `feature/ATT-8196`; revertible without affecting `sprint/2026-46.8`.
