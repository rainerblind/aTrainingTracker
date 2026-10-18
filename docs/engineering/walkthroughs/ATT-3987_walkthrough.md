# Stage 5: Walkthrough & Verification - ATT-3987: [High] Resumed workouts have no LiveWorkoutSession and miss extrema, map and derived metrics

**Ticket**: [ATT-3987](https://rainerblind.atlassian.net/browse/ATT-3987)  
**Sub-task**: [ATT-4078](https://rainerblind.atlassian.net/browse/ATT-4078) (`[Test]`)  
**Parent Epic**: [ATT-3978](https://rainerblind.atlassian.net/browse/ATT-3978) (*Core Tracking Reliability & Data Integrity*)  
**Target Release**: `V4.9.40` (Assigned at Sprint Review per Rule 14)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-031`  
**Test Mapping**: `TST-TRK-023`  
**Branch**: `feature/ATT-3987`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Verification Overview

This ticket resolves the defect where resuming a workout (`StartMode.RESUME_BY_USER` or `StartMode.RESUME_SERVICE_RECREATION`) left `TrackerService.mLiveSession == null`, causing all post-resume sensor extrema (`MIN`/`MAX`/`AVG`), 20-second delta-encoded map polylines, altitude/distance streams, and post-workout `finalizeLiveSession()` steps (route simplification, aerobic efficiency/decoupling, power training metrics, route clustering, and auto-naming) to be completely skipped:

1. **Sample Count Persistence in `TABLE_EXTREMA_VALUES` (`REQ-TRK-031.1` — Rule 20)**:
   - [`WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java#L1712-L2120) increments `DB_VERSION` from `24` to `25`, declares `WorkoutSummaries.COUNT = "count"` on `TABLE_EXTREMA_VALUES` (`ExtremumValues`), and executes `addColumnIfNotExists(db, WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.COUNT, "INTEGER")` on upgrade (`oldVersion < 25`).
   - [`WorkoutSummariesDatabaseManager.ExtremaRecord`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java#L418-L448) includes `@Nullable public final Integer count` (with a 6-argument constructor and backward-compatible 5-argument constructor), mapped symmetrically in [`getExtremaForWorkouts(...)`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java#L951-L1020) and persisted via [`updateExtremaValue(workoutId, sensorType, extremaType, value, count)`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java#L922-L950).
   - [`WorkoutSummariesDatabaseManager.EncodedStreams`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java#L450-L469) and [`getEncodedStreams(workoutId)`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java#L1022-L1057) fetch pre-resume `MAP_POLYLINE`, `ALTITUDE_STREAM`, and `DISTANCE_STREAM` strings for rehydration without altering the 7-column `RESUME_WORKOUT_PROJECTION`.
2. **`LiveWorkoutSession` Stream & Sensor Stats Rehydration API (`REQ-TRK-031.2`)**:
   - [`LiveWorkoutSession.RunningStats`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java#L29-L90) exposes `getCount()` and `rehydrate(minValue, maxValue, avgValue, sampleCount)` restoring `min`, `max`, `count`, and `sum = avgValue * count` while keeping `minChanged = false` and `maxChanged = false`.
   - [`LiveWorkoutSession`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java#L168-L270) exposes `setStartLatLng`, `setLastLatLng`, `rehydrateSensorStats(...)`, and `rehydrateStreams(...)` restoring `rawLatLngs`, `sampledLatLngs`, `sampledAltitudes`, `sampledDistances`, and the delta-encoding anchors (`lastLatE5`, `lastLngE5`, `lastAltE2`, `lastDistE2`) so post-resume 20-second `StreamIncrement` strings concatenate seamlessly onto pre-resume DB columns without coordinate, altitude, or distance jumps.
3. **`TrackerService` Resume Rehydration, Concurrency Guard & Finalization Continuity (`REQ-TRK-031.3`)**:
   - [`TrackerService.applyResumedState(...)`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L920-L955) instantiates `mLiveSession = new LiveWorkoutSession(mTimeActive_s, IMPORTANT_SENSOR_TYPES)`, sets `mIsRehydrating = true`, and offloads [`rehydrateLiveSession(...)`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L957-L1064) to `mDbExecutor` (or runs synchronously when invoked directly on a background thread) with per-stream `try / catch (Exception e)` fault isolation (`PolyUtil.decode`, `NumericalEncodingUtils.decode`) and extrema/sample-count restoration (`count > 0` or fallback to `mTimeActive_s`).
   - [`TrackerService.sampleAndWriteToDb()`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1425-L1575) gates `mLiveSession` updates on `mLiveSession != null && !mIsRehydrating`, persists `stats.getCount()` on `AVG`/`MIN`/`MAX` extrema updates, and preserves any pre-resume `ExtremaType.START` position.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-031.2` | `TST-TRK-023.1` | Automated Unit Test (`LiveWorkoutSessionTest`) | **PASSED** | `Verified` |
| `REQ-TRK-031.1`, `REQ-TRK-031.2` | `TST-TRK-023.2` | Automated Unit Test (`LiveWorkoutSessionTest`) | **PASSED** | `Verified` |
| `REQ-TRK-031.1`, `REQ-TRK-031.3` | `TST-TRK-023.3` | Automated Integration Test (`TrackerServiceResumeTest`) | **PASSED** | `Verified` |
| `REQ-TRK-031.1`, `REQ-PRO-001` | `TST-TRK-023.4` | DB v25 Migration Test (`WorkoutSummariesDatabaseSourceMigrationTest`) & Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Integration Tests (`WorkoutSummariesDatabaseSourceMigrationTest`, `LiveWorkoutSessionTest`, `TrackerServiceResumeTest`)
```text
> Task :app:testDebugUnitTest
WorkoutSummariesDatabaseSourceMigrationTest > onUpgrade_fromV21_addsSourceColumn PASSED
WorkoutSummariesDatabaseSourceMigrationTest > onCreate_includesSourceColumn PASSED
WorkoutSummariesDatabaseSourceMigrationTest > onUpgrade_fromV22_setsExistingRowsSourceToTracker PASSED
WorkoutSummariesDatabaseSourceMigrationTest > onCreate_defaultsSourceToTracker PASSED
WorkoutSummariesDatabaseSourceMigrationTest > onCreate_and_onUpgrade_fromV24_includesExtremaCountColumn PASSED
WorkoutSummariesDatabaseSourceMigrationTest > extremaRecord_and_updateExtremaValue_roundTripsCountAndStreams PASSED
LiveWorkoutSessionTest > testExtremaAndAverages PASSED
LiveWorkoutSessionTest > testStartAndEndPositions PASSED
LiveWorkoutSessionTest > testStreamSamplingAndEncoding PASSED
LiveWorkoutSessionTest > TST_TRK_023_1_rehydrateStreams_PreservesDeltaEncodingAnchors_AndFinalizedStreams PASSED
LiveWorkoutSessionTest > TST_TRK_023_2_rehydrateSensorStats_RestoresMinMaxAvgAndSampleCount_WithExactWeightedAverage PASSED
LiveWorkoutSessionTest > rehydrateStreams_HandlesPartialStreamsIndependently PASSED
TrackerServiceResumeTest > recreateValuesWhenResuming_updatesBaseFileNameAndSamplesTableName_andCompletesFuture PASSED
TrackerServiceResumeTest > deferredResumeServiceRecreation_bindsBanalService_restoresTableNameBeforeCreatingNewLap_andPersistsSamples PASSED
TrackerServiceResumeTest > deferredResumeByUser_bindsBanalService_restoresTableNameBeforeOnSearchingFinished_andPersistsSamples PASSED
TrackerServiceResumeTest > TST_TRK_023_3_resumeWorkout_RehydratesLiveWorkoutSession_UpdatesExtremaWithCount_AndFinalizesMapAndStreams PASSED
TrackerServiceResumeTest > TST_TRK_023_3_corruptedAltitudeStream_DoesNotAbortPolylineOrDistanceRehydration PASSED

32 tests completed, 0 failed
BUILD SUCCESSFUL in 18s
```

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest

3140 tests completed, 0 failed

BUILD SUCCESSFUL
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Pure backend tracking service (`TrackerService.java`, `LiveWorkoutSession.java`) and SQLite persistence (`WorkoutSummariesDatabaseManager.java`) reliability fix with zero UI layout, theme, or string resource changes; verified via Robolectric SQLite integration tests (`WorkoutSummariesDatabaseSourceMigrationTest`, `LiveWorkoutSessionTest`, `TrackerServiceResumeTest`) and full 3,140-test clean-room regression suite (`./gradlew testDebugUnitTest`). On-device installation onto Google Pixel 10 occurs at the start of Ceremony 2 (Sprint Review) per Rule 10.

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite (`./gradlew testDebugUnitTest`) executed with 100% pass rate across all 3,140 unit tests.
2. **Living Documentation Synchronized**: Status of `REQ-TRK-031` in `docs/requirements.md` and `TST-TRK-023` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-4078` updated with walkthrough, audited via Gate 5 (`review_agent.py`), and transitioned to `Erledigt` via `freigabe`.
4. **Continuous Sprint Integration & Parent Ticket Final Review**: `feature/ATT-3987` merged into `sprint/2026-42.9` (`--no-ff`), feature branch deleted, and parent ticket `ATT-3987` transitioned to `Final Review (Human)` for final release sign-off.
