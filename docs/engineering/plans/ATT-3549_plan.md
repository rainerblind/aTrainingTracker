# Stage 3: Implementation Plan - ATT-3549: Replace Unbounded Full-Table Query in TrackerService Crash-Resume with Targeted LIMIT 1 Query

**Ticket**: [ATT-3549](https://rainerblind.atlassian.net/browse/ATT-3549)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Sub-task**: [ATT-3672](https://rainerblind.atlassian.net/browse/ATT-3672)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Overview & Architectural Goals

The goal of this task is to eliminate the unbounded full-table query in `TrackerService.java:recreateValuesWhenResuming()`, replacing it with an indexed single-row query (`ORDER BY _id DESC LIMIT 1`) with a 7-column scalar projection enclosed in `try-with-resources`.

---

## 2. Technical Architecture & SWE.2 Structural Design

### Component Impact
- **Modified**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
  - Introduce `RESUME_PROJECTION` containing only the 7 scalar fields.
  - Refactor `recreateValuesWhenResuming()` to query strictly with `RESUME_PROJECTION`, `orderBy = C_ID + " DESC"`, `limit = "1"`.
  - Wrap query in `try (Cursor cursor = ...)`.
  - Guard row navigation via `if (cursor != null && cursor.moveToFirst())`.
  - Extract column values with bounds checking (`getColumnIndex(...) != -1`).
  - Read `mWorkoutID` via `cursor.getLong(idCol)` (aligning with `volatile long mWorkoutID`).
  - Annotate with `@androidx.annotation.VisibleForTesting` and adjust visibility to package-private for deterministic unit testability.
- **Added**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
  - Unit tests covering populated and empty `WorkoutSummaries` table conditions.

---

## 3. Step-by-Step Atomic Construction Sequence

### Step 1: Refactor `recreateValuesWhenResuming()` in `TrackerService.java`
1. Define constant `RESUME_WORKOUT_PROJECTION`:
   ```java
   private static final String[] RESUME_WORKOUT_PROJECTION = new String[] {
       WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME,
       WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID,
       WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s,
       WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s,
       WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES,
       WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS,
       WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m
   };
   ```
2. Refactor method implementation:
   ```java
   @VisibleForTesting
   void recreateValuesWhenResuming() {
       SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance(this).getDatabase();
       try (Cursor cursor = db.query(
               WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
               RESUME_WORKOUT_PROJECTION,
               null,
               null,
               null,
               null,
               WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID + " DESC",
               "1")) {

           if (cursor != null && cursor.moveToFirst()) {
               int baseFileNameCol = cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME);
               int idCol = cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID);
               int timeTotalCol = cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s);
               int timeActiveCol = cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s);
               int caloriesCol = cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES);
               int lapsCol = cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS);
               int distanceCol = cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m);

               mBaseFileName = baseFileNameCol != -1 ? cursor.getString(baseFileNameCol) : "";
               mWorkoutID = idCol != -1 ? cursor.getLong(idCol) : -1L;
               mTimeTotal_s = timeTotalCol != -1 ? cursor.getInt(timeTotalCol) : 0;
               mTimeActive_s = timeActiveCol != -1 ? cursor.getInt(timeActiveCol) : 0;
               int calories = caloriesCol != -1 ? cursor.getInt(caloriesCol) : 0;
               int lapNr = lapsCol != -1 ? cursor.getInt(lapsCol) : 0;
               mDistanceTotal_m = distanceCol != -1 ? cursor.getDouble(distanceCol) : 0.0;

               Log.d(TAG, "resuming with mBaseFileName=" + mBaseFileName
                       + ", mWorkoutId=" + mWorkoutID
                       + ", time_total_s=" + mTimeTotal_s
                       + ", time_active_s=" + mTimeActive_s
                       + ", calories=" + calories
                       + ", lapNr=" + lapNr
                       + ", distance_m=" + mDistanceTotal_m);

               BANALService.setInitialSensorValue(SensorType.TIME_TOTAL, mTimeTotal_s);
               BANALService.setInitialSensorValue(SensorType.TIME_ACTIVE, mTimeActive_s);
               BANALService.setInitialSensorValue(SensorType.CALORIES, calories);
               BANALService.setInitialSensorValue(SensorType.LAP_NR, lapNr);
               BANALService.setInitialSensorValue(SensorType.DISTANCE_m, mDistanceTotal_m);
           } else {
               Log.w(TAG, "recreateValuesWhenResuming: No workout summary record found to resume.");
           }
       }
   }
   ```

### Step 2: Unit Testing in `TrackerServiceResumeTest.kt`
Create unit test suite verifying:
1. `testRecreateValuesWhenResuming_populatedTable_queriesLimitOneAndPopulatesFields`:
   - Mock SQLite query result with 7 projected columns.
   - Assert `mBaseFileName`, `mWorkoutID`, `mTimeTotal_s`, `mTimeActive_s`, and `mDistanceTotal_m` are correctly updated.
   - Assert `db.query` arguments match `RESUME_WORKOUT_PROJECTION`, `_id DESC`, and limit `"1"`.
2. `testRecreateValuesWhenResuming_emptyTable_logsWarningAndExitsCleanly`:
   - Mock cursor returning `moveToFirst() = false`.
   - Assert method executes without throwing `CursorIndexOutOfBoundsException`.

### Step 3: Verification & Clean-Room Regression
Run targeted unit tests and full test suite to guarantee 100% pass rate.

---

## 4. Invariants & Risk Mitigation

- **Zero Cursor Leaks**: Guaranteed by Java `try-with-resources`.
- **Zero OOB Crashes**: Guaranteed by `if (cursor != null && cursor.moveToFirst())` guard.
- **Exact Telemetry Invariants**: Initial accumulator sensor values passed to `BANALService` remain identical.
- **Single-Row Memory Footprint**: Window buffer allocation is reduced from $O(N)$ historical blobs to a single 7-column row ($O(1)$).
