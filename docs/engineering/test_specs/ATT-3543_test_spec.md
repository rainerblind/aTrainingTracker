# Stage 2: Requirement & Test Specification - ATT-3543: Lossless WorkoutRrIntervals, Centered Recompute & Garmin FIT HrvMesg

**Ticket**: [ATT-3543](https://rainerblind.atlassian.net/browse/ATT-3543)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3543`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  
**Requirement Mapping**: `REQ-PER-034` (`docs/requirements/req_aftermath_periods.md`)  
**Test Spec ID**: `TST-PER-042` (`docs/tests/tst_aftermath_periods.md`)  

---

## 1. Requirement Specification (REQ-PER-034)

### 1.1 Problem Statement & Rationale
Live workout tracking computes DFA $\alpha_1$ and $\text{rMSSD}$ using causal trailing windows (~60s delay) and discards raw beat-to-beat intervals upon session completion. Athletes need raw RR interval persistence to recompute lag-free centered-window metrics and enable interoperable Garmin FIT `HrvMesg` export/import.

### 1.2 Functional & Architectural Requirements
1. **Lossless Persistence (`REQ-PER-034.1`)**: The system SHALL store beat-to-beat intervals in `WorkoutRr.db` table `workout_rr_intervals` (`workout_id, beat_index, beat_timestamp_ms, rr_ms, artifact_flag, gap_beats`). Handled via `WorkoutRrDatabaseManager`, registered in `DatabaseSchemaRegistry`, and cascaded on delete via `WorkoutDeletionHelper`.
2. **Monotonic Timestamp Reconstruction (`REQ-PER-034.2`)**: Live intervals received via `RrBeatListener` SHALL use cumulative reconstruction $t_k = t_{k-1} + \text{round}(\text{rr\_ms}_k)$ with drift resync when $|t_{\text{pkt}} - t_{\text{beat}}| > 2000\text{ ms}$, ensuring exact monotonic UTC alignment with `WorkoutSamples`.
3. **Centered-Window Post-Workout Recomputation (`REQ-PER-034.3`)**: `HrvPostWorkoutRecomputer` SHALL evaluate lag-free centered windows ($t \pm 60\text{s}$ for DFA $\alpha_1$, $t \pm 30\text{s}$ for $\text{rMSSD}$) via `RrArtifactFilter.filterBidirectional`, updating `WorkoutSamples` and `WorkoutSummaries` session extrema.
4. **Garmin FIT Export (`REQ-PER-034.4`)**: `FitFileWriter` SHALL interleave standard `HrvMesg` (up to 5 intervals in seconds) with `RecordMesg` trackpoints when RR data exists. Workouts without RR data SHALL omit `HrvMesg`.
5. **Garmin FIT Import (`REQ-PER-034.5`)**: `FitWorkoutParser` and `LegacyImportEngine` SHALL extract `HrvMesg` intervals, reconstruct timestamps anchored to session start UTC with pause resync, persist beats into `WorkoutRr.db`, and trigger post-workout recomputation.
6. **Non-HRV Invariant & Asynchronous Execution (`REQ-PER-034.6`)**: Non-HRV workouts SHALL incur zero persistence overhead (0 rows, 0 `HrvMesg`). Recomputation SHALL execute asynchronously on `Dispatchers.IO` without delaying UI finalization.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Persistence & Recomputation)**:
  * *Given* a workout recorded with BLE/ANT+ RR sensor stream
  * *When* the workout is completed and finalized
  * *Then* raw beats are persisted in `WorkoutRr.db` and `HrvPostWorkoutRecomputer` populates centered-window HRV metrics in `WorkoutSamples`.
* **Criterion 2 (Garmin FIT Round-Trip)**:
  * *Given* a workout with persisted RR intervals
  * *When* exported to FIT format and subsequently imported on another device
  * *Then* `HrvMesg` intervals are preserved and post-workout HRV streams recompute identically.
* **Criterion 3 (Non-HRV Workout Invariant)**:
  * *Given* a workout recorded without an RR sensor
  * *When* saved or exported to FIT
  * *Then* zero rows are inserted in `workout_rr_intervals` and zero `HrvMesg` are written.

### 1.4 System Invariants
* Raw RR interval values preserved losslessly unmodified alongside artifact flags.
* Monotonic beat timestamps strictly anchored in UTC epoch milliseconds.
* Zero overhead and 100% backward compatibility for non-HRV workouts.

---

## 2. Test Specification (TST-PER-042)

### Test Case 1: WorkoutRrDatabaseManager & Schema Integrity (`TST-PER-042.1`)
* **Scope**: Database Contract Test (`WorkoutRrDatabaseManagerTest.kt`)
* **Assertions**: Batch insert of `WorkoutRrInterval`, indexed lookup by `workout_id`, cascade deletion via `WorkoutDeletionHelper`, registration in `DatabaseSchemaRegistry`.

### Test Case 2: Beat Timestamp Cumulative Reconstruction (`TST-PER-042.2`)
* **Scope**: Unit Test (`RrBeatTimestampReconstructionTest.kt`)
* **Assertions**: Monotonic timestamp accumulation $t_k = t_{k-1} + \text{rr\_ms}_k$; drift resync when $|t_{\text{pkt}} - t_{\text{beat}}| > 2000\text{ ms}$.

### Test Case 3: Centered-Window Post-Workout Recomputation (`TST-PER-042.3`)
* **Scope**: Unit Test (`HrvPostWorkoutRecomputerTest.kt`)
* **Assertions**: Recomputes DFA $\alpha_1$ ($t \pm 60\text{s}$) and $\text{rMSSD}$ ($t \pm 30\text{s}$); confirms elimination of 60s trailing filter lag; updates `WorkoutSamples` and `WorkoutSummaries`.

### Test Case 4: Garmin FIT HrvMesg Export Interleaving (`TST-PER-042.4`)
* **Scope**: Unit Test (`FitFileWriterHrvTest.kt`)
* **Assertions**: Writes `HrvMesg` interleaved with `RecordMesg` trackpoints with time scaled in seconds; omits `HrvMesg` when RR intervals are absent.

### Test Case 5: Garmin FIT HrvMesg Import & Recomputation Trigger (`TST-PER-042.5`)
* **Scope**: Unit Test (`LegacyImportEngineFitHrvTest.kt`)
* **Assertions**: Parses `HrvMesg`, reconstructs pause-aligned timestamps, persists intervals in `WorkoutRr.db`, and triggers recomputation.

### Test Case 6: Tier 1 Clean-Room Regression Suite (`TST-PER-042.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.exporter.writer.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% clean-room pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-042.1` | Database | `WorkoutRrDatabaseManager` | `REQ-PER-034.1` | Specified |
| `TST-PER-042.2` | Unit | `TrackerService` / `RrBeatTimestampReconstruction` | `REQ-PER-034.2` | Specified |
| `TST-PER-042.3` | Engine | `HrvPostWorkoutRecomputer` | `REQ-PER-034.3`, `REQ-PER-034.6` | Specified |
| `TST-PER-042.4` | Export | `FitFileWriter` (`HrvMesg`) | `REQ-PER-034.4`, `REQ-PER-034.6` | Specified |
| `TST-PER-042.5` | Import | `FitWorkoutParser` / `LegacyImportEngine` | `REQ-PER-034.5` | Specified |
| `TST-PER-042.6` | Regression | Tier 1 Unit Test Suite | `REQ-PRO-001` | Specified |
