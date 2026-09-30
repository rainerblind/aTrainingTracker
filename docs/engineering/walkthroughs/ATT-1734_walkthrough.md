# Stage 5 Verification & Walkthrough: ATT-1734

## 1. Ticket Information
- **Parent Ticket**: [ATT-1734](https://atrainingtracker.atlassian.net/browse/ATT-1734) - `[Bug] [Lieblingsorte] Number of recorded starts on KnownLocationCard differs significantly from actual workout count`
- **Subtask**: [ATT-1784](https://atrainingtracker.atlassian.net/browse/ATT-1784) - `Stage 5: Verification & Clean-Room Regression`
- **Fix Version**: `V4.9.38`
- **Target Branch**: `sprint/2026-40.6`
- **Feature Branch**: `feature/ATT-1734`
- **Author**: AI Agent 1 (Implementer)
- **Auditor**: AI Agent 2 (Auditor)
- **Date**: 2026-10-01

---

## 2. Executive Summary of Changes
Resolved the discrepancy identified in Sprint Review 2026-40.5 where `KnownLocationCard` displayed an inflated count of recorded starts (e.g. 625 starts) when only a modest count of workouts (e.g. 45 workouts) actually started at the location.

1. **Root Cause Resolution**:
   - Eliminated residual bloat from pre-`ATT-1447` sensor warmup increments by implementing one-time self-healing database reconciliation.
   - Removed `hitCount` increment in `KnownLocationsDatabaseManager.upsertLocationByGeofence()`, ensuring background DEM elevation updates never artificially increase location start frequency.

2. **Authoritative Workout Starts Extraction (`WorkoutSummariesDatabaseManager.java`)**:
   - Added `getAllWorkoutStartLocations()` to extract geodetic starting points (`LATITUDE`, `START`) from `TABLE_EXTREMA_VALUES`.
   - Bumped database version to `22` and added composite index `idx_extrema_sensor_extrema` on `(sensor_type, extrema_type)` in `onCreate` and `onUpgrade`, ensuring instantaneous lookups.

3. **Database-Level Self-Healing Reconciliation (`KnownLocationsDatabaseManager.java`, `KnownLocationsRepository.kt`)**:
   - Implemented `reconcileHitCountsWithWorkoutSummaries(@Nullable List<LatLng> startLocations)` utilizing $O(1)$ spatial bounding-box pre-filtering before haversine geofence calculations.
   - Dispatched reconciliation during `KnownLocationsRepository` initialization on `KnownLocationsDB-Thread`, transparently healing legacy databases upon startup.

4. **Reactive UI Starts Counting & Sort Synchronization (`KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`)**:
   - Injected `WorkoutRepository` and `CoroutineDispatcher` (defaulting to `Dispatchers.Default`) into `KnownLocationsViewModel`.
   - Exposed reactive `startsByLocationId: Map<Long, Int>` computed over `repository.locationsFlow.value` and `workoutRepository.allWorkouts.value`.
   - Updated sort order `KnownLocationSortOrder.STARTS` to prioritize `startsByLocationId[it.id] ?: it.hitCount`.
   - Bound `KnownLocationCard(startsCount = uiState.startsByLocationId[item.id] ?: item.hitCount)` in `KnownLocationsScreen.kt`.

---

## 3. Test & Verification Results

### A. Targeted Unit Test Suite
- Test Files:
  - [KnownLocationsDatabaseManagerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerTest.kt)
  - [KnownLocationsRepositoryTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepositoryTest.kt)
  - [KnownLocationsViewModelTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModelTest.kt)
  - [KnownLocationsViewModelRoutesTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModelRoutesTest.kt)
- Results:
  - `testReconcileHitCounts_updatesBloatedCountsToMatchWorkouts`: PASSED (bloated legacy 625 correctly self-healed to 5).
  - `testUpsertLocationByGeofence_doesNotIncrementHitCount`: PASSED (DEM elevation updates keep hitCount strictly invariant).
  - `testDynamicStartsCountReflectsWorkoutRepository`: PASSED (reactive start counts compute accurately and sort accordingly).
  - Localization plurals audit: PASSED across all 9 supported languages (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

### B. Clean-Room Full Suite Regression Execution
- Command: `./gradlew testDebugUnitTest`
- Result: Clean-room test suite executed with 1111 tests completed, 0 failed (100% pass rate).

---

## 4. Traceability & Living Documentation
- **Requirements**:
  - `REQ-DAT-016`: Authoritative Workout Start Count Self-Healing Reconciliation & Reactive Presentation.
  - Status in `docs/requirements.md`: **Verified**
- **Test Specifications**:
  - `TST-DAT-011`: Authoritative Workout Start Count Self-Healing Reconciliation & Presentation Verification.
  - Status in `docs/tests.md`: **Verified**
