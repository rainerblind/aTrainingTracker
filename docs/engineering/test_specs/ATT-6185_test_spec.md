# Stage 2: Requirement & Test Specification - ATT-6185: [Verbesserung] Add integration tests for TrackerService, sensor layer and database

**Ticket**: [ATT-6185](https://rainerblind.atlassian.net/browse/ATT-6185)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-TRK-075` (*Core Tracking Pipeline Integration Verification*)  
**Test Spec ID**: `TST-TRK-075`  
**Branch**: `subagent-Lane-1-Agent---ATT-6185-self-1e7c93c0`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-TRK-075)

### 1.1 Problem Statement & Rationale
Prior unit testing of the tracking pipeline relied heavily on isolated mocks for SQLite databases and Android services, omitting validation of the integrated chain across sensor ingestion, state transitions (tracking/pause/resume/auto-pause), and SQLite persistence. In accordance with ASPICE SWE.5 (Remediation ID C-11), `REQ-TRK-075` mandates end-to-end integration verification across `TrackerService`, `LiveWorkoutSession`, `WorkoutRepository`, and authentic SQLite databases.

### 1.2 Functional & Architectural Requirements
1. **Start Tracking & Ingestion (`REQ-TRK-075.1`)**: The system SHALL initialize a new workout, create dynamic sample tables (`workout_samples_<baseFileName>`), ingest multi-sensor samples (HR, Speed, Cadence, Power, GPS), write sample rows to SQLite, and stream beat-to-beat RR intervals to `workout_rr_intervals`.
2. **Manual Pause Invariant (`REQ-TRK-075.2`)**: In paused state, the system SHALL suspend sample row writes and freeze active time (`TIME_ACTIVE_s`), while continuing to update total time (`TIME_TOTAL_s`) in `workout_summaries`.
3. **Manual Resume Continuity (`REQ-TRK-075.3`)**: Resuming tracking SHALL resume active time accumulation and resume inserting sample rows into the dynamic samples table.
4. **Auto-Pause State Transitions (`REQ-TRK-075.4`)**: The system SHALL evaluate sensor speed against configured threshold; dropping below threshold SHALL transition to `AUTO_PAUSED` and trigger pause broadcast; exceeding threshold SHALL transition to `TRACKING` and trigger resume broadcast.
5. **Finalization & Summary Persistence (`REQ-TRK-075.5`)**: Stopping tracking SHALL close the active lap in `laps`, flush RR intervals synchronously, compute terminal summary statistics (distance, active time, total time, average speed, avg/max HR, ascent), and commit `FINISHED = 1` in `workout_summaries`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Start & Ingest)**:
  * *Given* TrackerService initialized with simulated multi-sensor telemetry (HR, Speed, Cadence, Power, GPS),
  * *When* tracking is started and sample tick executes,
  * *Then* `workout_summaries`, `workout_samples_<baseFileName>`, and `workout_rr_intervals` tables in SQLite contain matching populated records.
* **Criterion 2 (Pause)**:
  * *Given* active tracking session,
  * *When* tracking is paused,
  * *Then* sample row count in SQLite remains constant, `TIME_ACTIVE_s` freezes, and `TIME_TOTAL_s` increments.
* **Criterion 3 (Resume)**:
  * *Given* paused tracking session,
  * *When* tracking resumes,
  * *Then* `TIME_ACTIVE_s` resumes accumulating and new sample rows are written to SQLite.
* **Criterion 4 (Auto-Pause)**:
  * *Given* tracking with auto-pause enabled,
  * *When* speed drops below threshold,
  * *Then* state transitions to `AUTO_PAUSED`; when speed subsequently exceeds threshold, state transitions to `TRACKING`.
* **Criterion 5 (Stop & Finalize)**:
  * *Given* active tracking session with accumulated metrics,
  * *When* `endWorkout()` is invoked,
  * *Then* `FINISHED = 1` is committed, final lap is saved in `laps`, and computed summary statistics match recorded telemetry.

### 1.4 System Invariants
* Zero modifications to production source code or database schemas.
* Authentic SQLite persistence assertions via Robolectric and `RealSqliteTestHelper`.
* Complete database isolation per test execution.

---

## 2. Test Specification (TST-TRK-075)

### Test Case 1: `startTracking_ingestsMultiSensorSamplesAndPopulatesSqlite` (`TST-TRK-075.1`)
* **Scope**: Integration Test (Robolectric + Real SQLite)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`
* **Preconditions**: Real SQLite databases initialized; simulated BANALServiceComm providing HR (145 bpm), Speed (8.33 m/s), Cadence (85 rpm), Power (220 W), Distance (100 m), and RR intervals.
* **Action**: Invoke `TrackerService.onStartCommand(START_NORMAL)`, emit sensor samples via `sampleAndWriteToDb()`, trigger RR beat.
* **Expected Result**: Row inserted into `workout_summaries` (`workout_id > 0`); dynamic `workout_samples_*` table created with matching columns; `workout_rr_intervals` has recorded beat.

### Test Case 2: `pauseTracking_suspendsSampleWritesAndFreezesActiveTime` (`TST-TRK-075.2`)
* **Scope**: Integration Test (Robolectric + Real SQLite)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`
* **Preconditions**: Active workout running with 2 recorded samples; `TIME_ACTIVE_s = 2`, `TIME_TOTAL_s = 2`.
* **Action**: Set paused state (`TrainingApplication.pauseTracking(false)`), trigger sample tick with `TIME_TOTAL_s = 5`.
* **Expected Result**: Row count in `workout_samples_*` remains 2; `TIME_ACTIVE_s` in `workout_summaries` remains 2; `TIME_TOTAL_s` updates to 5.

### Test Case 3: `resumeTracking_resumesSampleIngestionAndActiveTimeAccumulation` (`TST-TRK-075.3`)
* **Scope**: Integration Test (Robolectric + Real SQLite)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`
* **Preconditions**: Paused workout; resume invoked.
* **Action**: Transition to tracking (`TrainingApplication.setTracking()`), emit sample tick with `TIME_ACTIVE_s = 3`, `TIME_TOTAL_s = 6`.
* **Expected Result**: Row count in `workout_samples_*` increments to 3; `TIME_ACTIVE_s` updates to 3.

### Test Case 4: `autoPause_transitionsStateOnSpeedDropAndResumesOnMotion` (`TST-TRK-075.4`)
* **Scope**: Integration Test (Robolectric + Real SQLite)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`
* **Preconditions**: Auto-pause configured with threshold $1.0\text{ m/s}$.
* **Action**: Feed low speed ($0.2\text{ m/s}$) across debounce interval; then feed moving speed ($5.0\text{ m/s}$).
* **Expected Result**: AutoPauseManager transitions to `AUTO_PAUSED` and broadcasts pause request; then transitions back to `TRACKING` and broadcasts resume request.

### Test Case 5: `stopTracking_persistsLapsAndFinalSummaryMetricsInSqlite` (`TST-TRK-075.5`)
* **Scope**: Integration Test (Robolectric + Real SQLite)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/integration/TrackerServiceIntegrationTest.kt`
* **Preconditions**: Active workout with distance, active time, heart rate samples, ascent.
* **Action**: Invoke `TrackerService.endWorkout()`.
* **Expected Result**: `workout_summaries` row has `FINISHED = 1`; `laps` table contains final lap; total distance, ascent, and avg/max HR are persisted accurately in SQLite.

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-TRK-075.6`)
* **Scope**: Targeted Unit & Integration Test Suite
* **Command**: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.integration.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-075.1` | Integration | `TrackerService.sampleAndWriteToDb` | `REQ-TRK-075.1` | Specified |
| `TST-TRK-075.2` | Integration | `TrackerService.sampleAndWriteToDb` | `REQ-TRK-075.2` | Specified |
| `TST-TRK-075.3` | Integration | `TrackerService.sampleAndWriteToDb` | `REQ-TRK-075.3` | Specified |
| `TST-TRK-075.4` | Integration | `AutoPauseManager.evaluateTick` | `REQ-TRK-075.4` | Specified |
| `TST-TRK-075.5` | Integration | `TrackerService.endWorkout` | `REQ-TRK-075.5` | Specified |
| `TST-TRK-075.6` | Regression (Tier 1) | `--tests "com.atrainingtracker.trainingtracker.integration.*"` | `REQ-PRO-001`, `REQ-TRK-075` | Specified |
