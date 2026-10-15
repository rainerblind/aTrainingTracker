# Stage 2: Requirement & Test Specification - ATT-3549: Replace Unbounded Full-Table Query in TrackerService Crash-Resume with Targeted LIMIT 1 Query

**Ticket**: [ATT-3549](https://rainerblind.atlassian.net/browse/ATT-3549)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Sub-task**: [ATT-3671](https://rainerblind.atlassian.net/browse/ATT-3671)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Requirement Specification (REQ-DAT-002)

### REQ-DAT-002: Targeted Single-Row Query and Bounds-Checked Resume Reconstruction in TrackerService

The system SHALL optimize crash recovery in `TrackerService.java` by constraining database access on `WorkoutSummaries.TABLE` to a targeted projection of required scalar columns and a single latest row, enclosed in try-with-resources with defensive cursor bounds checking (ATT-3549, parent epic ATT-431):

1. **Constrained Projection & Single-Row Index Scan (`REQ-DAT-002.1`)**:
   - `TrackerService.recreateValuesWhenResuming()` SHALL NOT execute full-table queries (`SELECT * FROM WorkoutSummaries`).
   - The query SHALL project strictly the 7 required scalar columns:
     - `WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME`
     - `WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID`
     - `WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s`
     - `WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s`
     - `WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES`
     - `WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS`
     - `WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m`
   - The query SHALL order by `WorkoutSummaries.C_ID + " DESC"` with limit `"1"`.

2. **Deterministic Resource Management & Defensive Column Extraction (`REQ-DAT-002.2`)**:
   - The `Cursor` instance SHALL be allocated inside a `try (Cursor cursor = ...)` block.
   - Cursor navigation SHALL use `if (cursor != null && cursor.moveToFirst())`.
   - Column extraction SHALL check column index validity (`getColumnIndex(...) != -1`) before invocation of getter methods.
   - `mWorkoutID` SHALL be extracted as a 64-bit integer (`cursor.getLong(...)`), aligning with `volatile long mWorkoutID`.

3. **Empty Table & Error Resilience (`REQ-DAT-002.3`)**:
   - If `cursor == null` or `cursor.moveToFirst() == false` (empty database), the method SHALL log a warning (`Log.w(TAG, "recreateValuesWhenResuming: No workout summary record found to resume.")`), retain safe canonical sentinel defaults (`mWorkoutID = -1L`, `mBaseFileName = ""`, `mTimeTotal_s = 0`, `mTimeActive_s = 0`, `mDistanceTotal_m = 0.0`), and return cleanly without throwing `CursorIndexOutOfBoundsException` or `NullPointerException`.

4. **Resumed Telemetry Parity (`REQ-DAT-002.4`)**:
   - When a valid workout record is present, the values assigned to `mBaseFileName`, `mWorkoutID`, `mTimeTotal_s`, `mTimeActive_s`, `calories`, `lapNr`, and `mDistanceTotal_m`, as well as values passed to `BANALService.setInitialSensorValue(...)`, SHALL match exactly the latest record's data.

---

## 2. Test Specification (TST-DAT-002)

### Test Suite Structure
A dedicated test suite in `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt` will verify crash resume query safety and data reconstruction.

| Test ID | Requirement | Description | Given-When-Then Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **TST-DAT-002.1** | `REQ-DAT-002.1`, `REQ-DAT-002.4` | Resume with populated table | **Given** a database with multiple workout summary records (rows 1..5)<br>**When** `recreateValuesWhenResuming()` is executed<br>**Then** it queries strictly with the 7-column projection, `ORDER BY _id DESC LIMIT 1`, and accurately reconstructs fields from the 5th record. |
| **TST-DAT-002.2** | `REQ-DAT-002.3` | Resume with empty table | **Given** an empty `WorkoutSummaries` table (0 rows)<br>**When** `recreateValuesWhenResuming()` is executed<br>**Then** it returns cleanly without throwing `CursorIndexOutOfBoundsException` or leaking resources. |
| **TST-DAT-002.3** | `REQ-DAT-002.2` | Static audit verification | **Given** the source code of `TrackerService.java`<br>**When** analyzed<br>**Then** zero unbounded `query(..., null, null, null, null, null, null)` or unshielded `cursor.moveToLast()` calls exist. |
| **TST-DAT-002.4** | Clean-room regression | Full suite regression | **Given** the complete unit test suite<br>**When** `./gradlew testDebugUnitTest` runs<br>**Then** 100% of unit tests pass with zero regressions. |

---

## 3. Living Documentation Traceability Matrix

- **Requirement ID**: `REQ-DAT-002` -> `docs/requirements.md`
- **Test ID**: `TST-DAT-002` -> `docs/tests.md`
- **Target Implementation File**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
- **Target Test File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
