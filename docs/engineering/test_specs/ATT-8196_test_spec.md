# Stage 2: Requirement & Test Specification - ATT-8196: Export recorded L/R pedal power balance, torque effectiveness, and pedal smoothness in FitFileWriter

**Ticket**: [ATT-8196](https://rainerblind.atlassian.net/browse/ATT-8196) | **Sub-task**: [ATT-8231](https://rainerblind.atlassian.net/browse/ATT-8231) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6075](https://rainerblind.atlassian.net/browse/ATT-6075) (*Cycling Power Dynamics & Advanced Power Meter Integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8196`  
**Requirement Mapping**: `REQ-EXP-043` (*Garmin FIT Pedal Dynamics Serialization & Lossless Round-Trip*)  
**Test Spec ID**: `TST-EXP-043` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-EXP-043)
* **Problem & Rationale**: `aTrainingTracker` records dual-sided cycling dynamics in `WorkoutSamples.db` (`SensorType.PEDAL_POWER_BALANCE`, `TORQUE_EFFECTIVENESS_L/R`, `PEDAL_SMOOTHNESS_L/R/Combined`), but `FitFileWriter.java` omits them from `.fit` exports and `FitWorkoutParser.kt` omits them on import, causing data loss on external platforms (Intervals.icu, Strava, GoldenCheetah, WKO5) and breaking lossless `.fit` backup/restore (`REQ-MIG-053`).
* **Attributes**: **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
* **Functional & Architectural Requirements**:
  1. `WorkoutSampleRecord` (`WorkoutExportHeader.kt`) SHALL expose nullable `Double?` fields: `pedalPowerBalance`, `torqueEffectivenessL/R`, and `pedalSmoothnessL/R/Combined`.
  2. `WorkoutRepository.streamSamples(...)` SHALL project the 6 corresponding `SensorType` columns in $O(1)$ memory (`REQ-DAT-042.2`).
  3. `FitFileWriter.java` SHALL encode trackpoint `RecordMesg`:
     - `setLeftRightBalance((short)((Math.round(bal) & LeftRightBalance.MASK) | LeftRightBalance.RIGHT))` (`0x80 | (right% & 0x7F)`) per `REQ-CON-022`.
     - `setLeftTorqueEffectiveness(Float)` and `setRightTorqueEffectiveness(Float)`.
     - `setLeftPedalSmoothness(Float)`, `setRightPedalSmoothness(Float)`, and `setCombinedPedalSmoothness(Float)`.
  4. `FitFileWriter.java` SHALL accumulate $O(1)$ online averages and encode `LapMesg` and `SessionMesg`:
     - `setLeftRightBalance(Integer)` using `LeftRightBalance100` (`(Math.round(avg * 100.0) & LeftRightBalance100.MASK) | LeftRightBalance100.RIGHT`, `0.01%` resolution).
     - `setAvgLeft/RightTorqueEffectiveness` and `setAvgLeft/Right/CombinedPedalSmoothness`.
     - Omit all fields (`null`) when pedal dynamics are absent (no artificial 0% artifacts).
  5. `FitWorkoutParser.kt` SHALL decode `RecordMesg` pedal dynamics into `bufferedSamples` and `foundSensors` without L/R inversion.
* **Acceptance Criteria (Given-When-Then)**:
  * *Criterion 1 (Trackpoint Encoding)*: Given recorded sample with `PEDAL_POWER_BALANCE=52.0`, `TE_L=78.5`, `TE_R=81.0`, `PS_L=24.0`, `PS_R=25.5`; when exported via `FitFileWriter` and decoded with FIT SDK; then `RecordMesg.getLeftRightBalance()` equals `(52 | 0x80)`, `TE_L/R` equal `78.5f/81.0f`, `PS_L/R` equal `24.0f/25.5f`.
  * *Criterion 2 (Summary Aggregates)*: Given a workout with recorded pedal dynamics; when exported to `.fit`; then `LapMesg` and `SessionMesg` contain `LeftRightBalance100` and average TE/PS, while non-pedal workouts omit all fields cleanly.
  * *Criterion 3 (Lossless Round-Trip)*: Given a workout exported by `FitFileWriter`; when imported via `FitWorkoutParser`; then `PEDAL_POWER_BALANCE`, `TORQUE_EFFECTIVENESS_L/R`, and `PEDAL_SMOOTHNESS_L/R/Combined` are restored in `bufferedSamples` and `foundSensors`.
* **System Invariants**: $O(1)$ heap memory streaming (`REQ-DAT-042.2`); zero direct SQLite imports in `FitFileWriter.java` (`REQ-DAT-042.5`); Right-pedal 0..100% normalization preserved (`REQ-CON-022.1`).

---

## 2. Test Specification (TST-EXP-043)
* **Test Case 1 (`FitFileWriterTest.kt` / `TST-EXP-043.1`)**: Unit test with synthetic samples verifying `RecordMesg`, `LapMesg`, and `SessionMesg` match bitmasks, scale factors, and online averages.
* **Test Case 2 (`FitLosslessRoundTripTest.kt` / `TST-EXP-043.2`)**: Integration test verifying `FitWorkoutParser` decodes balance, TE, and PS into `bufferedSamples` and `foundSensors` without inversion.
* **Test Case 3 (`TST-EXP-043.3`)**: Tier 1 regression `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.*" --tests "com.atrainingtracker.trainingtracker.migration.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-EXP-043.1]` | Unit | `FitFileWriterTest.kt` | `REQ-EXP-043` | Specified |
| `[TST-EXP-043.2]` | Integration | `FitLosslessRoundTripTest.kt` | `REQ-EXP-043`, `REQ-MIG-053` | Specified |
| `[TST-EXP-043.3]` | Regression | `exporter.*`, `migration.*` | `REQ-PRO-001` | Specified |
