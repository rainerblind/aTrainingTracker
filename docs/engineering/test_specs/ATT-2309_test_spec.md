# Stage 2: Requirement & Test Specification - ATT-2309: CursorWindow IllegalStateException in WorkoutRepository.loadAllWorkouts on Large Databases

**Ticket**: [ATT-2309](https://rainerblind.atlassian.net/browse/ATT-2309)  
**Sub-task**: [ATT-2437](https://rainerblind.atlassian.net/browse/ATT-2437) (`[Req & Test Spec] CursorWindow IllegalStateException in WorkoutRepository.loadAllWorkouts on Large Databases`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*[Epic] Improve WorkoutSummaries*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-STB-013` (Net-new stability requirement)  
**Test Mapping**: `TST-STB-013`  
**Branch**: `feature/ATT-2309`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Traceability Matrix

| Requirement ID | Requirement Title | Test Specification ID | Verification File(s) | Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-STB-013** | Resilient Single-Pass Workout History Streaming & CursorWindow Crash Protection | **TST-STB-013** | `WorkoutRepositoryStreamTest.kt`, `WorkoutRepositoryStressTest.kt` | **In Progress** |

---

## 2. Requirement Specification: REQ-STB-013

### 2.1 Formal Definition
The system SHALL stream historical workout summaries from SQLite in a strictly monotonic, forward-only cursor pass and protect against fatal `CursorWindow` buffer allocation exceptions during large database loads (>1,600 workouts) (ATT-2309):

1. **Monotonic Forward-Only Cursor Traversal (`WorkoutRepository.kt`)**:
   - `WorkoutRepository.loadAllWorkouts()` SHALL traverse the database cursor strictly forward using monotonic `c.moveToNext()` calls.
   - The repository SHALL NEVER invoke `c.moveToPosition(...)` or seek backwards across row/window boundaries during batch loading, eliminating Android `CursorWindow` page thrashing and desynchronization.
2. **Two-Stage Extraction & Vectorized Batch Enrichment**:
   - During the forward cursor scan, row column values SHALL be read in a single pass into in-memory row snapshots (`RawCursorSnapshot`) in chunks of 50.
   - The repository SHALL execute vectorized batch metadata queries (`extrema`, `stravaData`, `clusterNames`, `laps`) in memory using the extracted chunk IDs.
   - The snapshots SHALL be enriched into complete `WorkoutData` domain objects (`mapper.fromSnapshot(snapshot, batchMetadata)`) and progressively pumped to the UI without re-querying or re-reading the SQLite cursor.
3. **Defensive CursorWindow Exception Shielding**:
   - Individual cursor row extraction calls SHALL be encapsulated in defensive try/catch blocks catching `IllegalStateException`, `CursorWindowAllocationException`, or SQLite buffer exceptions, logging diagnostics and safely skipping corrupted/truncated rows without terminating the application process.
4. **Stress & Stability Compliance**:
   - The system SHALL stream databases containing $> 1,600$ workouts completely to completion with zero `IllegalStateException` crashes.

### 2.2 Acceptance Criteria (Given-When-Then)
- **AC-1 (Forward-Only Traversal & CursorWindow Stability)**:
  - *Given* a workout database containing $> 1,600$ workouts,
  - *When* `WorkoutRepository.loadAllWorkouts()` executes,
  - *Then* all workouts SHALL stream completely to completion in a single forward pass without calling `moveToPosition` backward, and zero `IllegalStateException` crashes SHALL occur.
- **AC-2 (Exception Shielding)**:
  - *Given* an isolated cursor row that throws a `CursorWindow` or SQLite exception during extraction,
  - *When* the row is read,
  - *Then* the repository SHALL catch and log the exception, safely skip the corrupted row, and continue streaming remaining workouts without process death.
- **AC-3 (Vectorized Query Preservation)**:
  - *Given* batch metadata queries (`extrema`, `stravaData`, `clusterNames`, `laps`),
  - *When* processing chunks of 50 workouts,
  - *Then* queries SHALL execute in vectorized batches without introducing N+1 individual queries.

### 2.3 System Invariants
- SQLite table schemas for `WorkoutSummaries.db`, `Extrema.db`, `StravaUpload.db`, `WorkoutClusters.db`, and `Laps.db` MUST NOT be altered.
- Progressive UI emissions (`_allWorkouts.value`) continue to update at expected thresholds (first 10, each batch of 50, and upon completion).
- Existing test suites across all modules MUST continue to pass with 100% success rate.

---

## 3. Test Specification: TST-STB-013

### 3.1 Verification Scope
The test suite validates sequential cursor processing, decoupled data extraction, vectorized metadata enrichment, large dataset stress streaming (>1,600 rows), and defensive exception shielding.

### 3.2 Test Cases

#### Case 1: Single-Pass Sequential Traversal & Vectorized Mapping (`WorkoutRepositoryStreamTest.kt`)
- **Objective**: Verify that `loadAllWorkouts()` iterates strictly forward and maps all records accurately.
- **Setup**: Populate 120 synthetic workouts with associated extrema, Strava data, cluster names, and laps.
- **Execution**: Run `workoutRepository.loadAllWorkouts()`.
- **Assertions**:
  - All 120 workouts appear in `allWorkouts.value`.
  - Chunk metadata (extrema, Strava activity, cluster name) is properly associated.
  - The cursor is never navigated backward.

#### Case 2: 2,000-Workout Synthetic Stress Test (`WorkoutRepositoryStressTest.kt`)
- **Objective**: Validate AC-1 and AC-3 by streaming a massive dataset exceeding the 1,600-workout threshold.
- **Setup**: Construct a MockCursor with 2,000 rows across 34 columns.
- **Execution**: Run `workoutRepository.loadAllWorkouts()`.
- **Assertions**:
  - `loadAllWorkouts()` completes successfully without throwing `IllegalStateException`.
  - `allWorkouts.value.size` equals 2,000.
  - Progressive UI emission emissions occur at batch intervals.

#### Case 3: Defensive Exception Shielding on Faulty Row
- **Objective**: Validate AC-2 by simulating a cursor row that throws `IllegalStateException`.
- **Setup**: Construct a cursor where row 5 throws `IllegalStateException("Couldn't read row 5, col 0 from CursorWindow")`.
- **Execution**: Run `workoutRepository.loadAllWorkouts()`.
- **Assertions**:
  - The repository catches the error, logs a diagnostic warning, skips row 5, and loads all other rows successfully.
  - No crash terminates the coroutine or application process.

#### Case 4: Full Clean-Room Test Suite Regression
- **Objective**: Execute `./gradlew testDebugUnitTest` across all modules.
- **Assertions**: 100% test pass rate with zero regressions.
