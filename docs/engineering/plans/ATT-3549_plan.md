# Stage 3: Implementation Plan - ATT-3549: Replace Unbounded Full-Table Query in TrackerService Crash-Resume with Targeted LIMIT 1 Query

**Ticket**: [ATT-3549](https://rainerblind.atlassian.net/browse/ATT-3549)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Sub-task**: [ATT-3672](https://rainerblind.atlassian.net/browse/ATT-3672)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15 (Revision 2 - Addressing Auditor Gate 3 Feedback)  

---

## 1. Overview & Architectural Goals

The goal of this task is to eliminate the unbounded full-table query in `TrackerService.java:recreateValuesWhenResuming()`, replacing it with an indexed single-row query (`ORDER BY _id DESC LIMIT 1`) with an explicit 7-column scalar projection enclosed in `try-with-resources`.

In response to Gate 3 auditor recommendations:
1. **Atomic State Update via Local Snapshot / DTO**: All 7 scalar metrics will be parsed into an immutable local data carrier `ResumedWorkoutState` before mutating any `TrackerService` instance fields or dispatching to `BANALService.setInitialSensorValue(...)`.
2. **Strict Column Index Validation & Fail-Fast**: Column indices will be verified using `getColumnIndexOrThrow` or strict non-negative validation (`idx != -1`). If a column is missing, the operation will fail-fast with a descriptive `IllegalStateException` or `Log.e`, preventing partial or corrupted state restoration.
3. **Encapsulation**: Scope `recreateValuesWhenResuming()` with `@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)`.

---

## 2. Technical Architecture & SWE.2 Structural Design

### Component Impact
- **Modified**: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
  - Introduce `RESUME_WORKOUT_PROJECTION`:
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
  - Define private immutable snapshot record/class:
    ```java
    @VisibleForTesting
    static final class ResumedWorkoutState {
        final String baseFileName;
        final long workoutId;
        final int timeTotal_s;
        final int timeActive_s;
        final int calories;
        final int lapNr;
        final double distanceTotal_m;

        ResumedWorkoutState(String baseFileName, long workoutId, int timeTotal_s, int timeActive_s, int calories, int lapNr, double distanceTotal_m) {
            this.baseFileName = baseFileName;
            this.workoutId = workoutId;
            this.timeTotal_s = timeTotal_s;
            this.timeActive_s = timeActive_s;
            this.calories = calories;
            this.lapNr = lapNr;
            this.distanceTotal_m = distanceTotal_m;
        }
    }
    ```
  - Parse the cursor into `ResumedWorkoutState` inside `try (Cursor cursor = ...)`:
    ```java
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void recreateValuesWhenResuming() {
        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance(this).getDatabase();
        ResumedWorkoutState state = null;

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
                int baseFileNameCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME);
                int idCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID);
                int timeTotalCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_TOTAL_s);
                int timeActiveCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.TIME_ACTIVE_s);
                int caloriesCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.CALORIES);
                int lapsCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.LAPS);
                int distanceCol = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m);

                state = new ResumedWorkoutState(
                        cursor.getString(baseFileNameCol),
                        cursor.getLong(idCol),
                        cursor.getInt(timeTotalCol),
                        cursor.getInt(timeActiveCol),
                        cursor.getInt(caloriesCol),
                        cursor.getInt(lapsCol),
                        cursor.getDouble(distanceCol)
                );
            } else {
                Log.w(TAG, "recreateValuesWhenResuming: No workout summary record found to resume.");
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "recreateValuesWhenResuming: Failed to extract resume record from database", e);
            return;
        }

        // Apply atomic state transition only after successful, complete database extraction
        applyResumedState(state);
    }

    private void applyResumedState(@NonNull ResumedWorkoutState state) {
        mBaseFileName = state.baseFileName;
        mWorkoutID = state.workoutId;
        mTimeTotal_s = state.timeTotal_s;
        mTimeActive_s = state.timeActive_s;
        mDistanceTotal_m = state.distanceTotal_m;

        Log.d(TAG, "resuming with mBaseFileName=" + mBaseFileName
                + ", mWorkoutId=" + mWorkoutID
                + ", time_total_s=" + mTimeTotal_s
                + ", time_active_s=" + mTimeActive_s
                + ", calories=" + state.calories
                + ", lapNr=" + state.lapNr
                + ", distance_m=" + mDistanceTotal_m);

        BANALService.setInitialSensorValue(SensorType.TIME_TOTAL, mTimeTotal_s);
        BANALService.setInitialSensorValue(SensorType.TIME_ACTIVE, mTimeActive_s);
        BANALService.setInitialSensorValue(SensorType.CALORIES, state.calories);
        BANALService.setInitialSensorValue(SensorType.LAP_NR, state.lapNr);
        BANALService.setInitialSensorValue(SensorType.DISTANCE_m, mDistanceTotal_m);
    }
    ```

---

## 3. Step-by-Step Atomic Construction Sequence

### Step 1: Implement `RESUME_WORKOUT_PROJECTION`, `ResumedWorkoutState`, and Refactored `recreateValuesWhenResuming()`
- Add constants and helper struct to `TrackerService.java`.
- Refactor `recreateValuesWhenResuming()` and `applyResumedState(...)` using `getColumnIndexOrThrow` and atomic commit.

### Step 2: Comprehensive Unit Testing in `TrackerServiceResumeTest.kt`
Author unit test suite covering:
1. `testRecreateValuesWhenResuming_populatedTable_queriesLimitOneAndPopulatesFields`:
   - Mock query returning valid 7-column row.
   - Verify `ORDER BY _id DESC LIMIT 1` and 7-column projection arguments.
   - Verify atomic field assignment and `BANALService.setInitialSensorValue` calls.
2. `testRecreateValuesWhenResuming_emptyTable_logsWarningAndEarlyReturnsWithoutMutation`:
   - Mock cursor returning `moveToFirst() = false`.
   - Verify zero exceptions and that service state remains uncorrupted.
3. `testRecreateValuesWhenResuming_missingColumn_failsFastWithoutPartialMutation`:
   - Mock cursor throwing `IllegalArgumentException` on `getColumnIndexOrThrow`.
   - Verify atomic guard catches error, logs `Log.e`, and aborts before applying any state.

### Step 3: Verification & Clean-Room Regression
Run `./gradlew testDebugUnitTest` ensuring 100% test pass rate across the full suite.

---

## 4. Invariants & Risk Mitigation

- **Atomic State Consistency**: Guaranteed by reading into `ResumedWorkoutState` before mutating `TrackerService` fields or calling `BANALService.setInitialSensorValue(...)`.
- **Fail-Fast Schema Verification**: Guaranteed by `getColumnIndexOrThrow`. If the schema or projection is compromised, the process logs a critical error and aborts instead of injecting corrupted 0 values.
- **Zero Cursor Leaks**: Guaranteed by `try-with-resources`.
- **Zero OOB Crashes**: Guaranteed by `if (cursor != null && cursor.moveToFirst())`.
- **Single-Row Memory Footprint**: Bounded to $O(1)$ memory usage.
