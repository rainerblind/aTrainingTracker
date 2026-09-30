# Stage 3: Implementation Plan - ATT-1734: [Lieblingsorte] Number of recorded starts on KnownLocationCard differs significantly from actual workout count

**Ticket**: [ATT-1734](https://rainerblind.atlassian.net/browse/ATT-1734)  
**Sub-task**: [ATT-1782](https://rainerblind.atlassian.net/browse/ATT-1782) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Requirement Mapping**: `REQ-DAT-016`  
**Test Mapping**: `TST-DAT-011`  
**Branch**: `feature/ATT-1734`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

Athletes reported that the number of starts shown on `KnownLocationCard` (e.g. 625 Starts for "Zu Hause") differs massively from the actual recorded workouts starting at this location (e.g. 45 workouts). While `ATT-1447` decoupled altimeter calibration from workout start tracking for new sessions, historical bloated counts in `StartLocation2Altitude.db` were preserved without recalculation ("Variante A"). Additionally, `upsertLocationByGeofence()` still incremented `hitCount` during DEM elevation healing, workout deletions never decremented `hitCount`, and imported workouts were omitted.

This plan details the software construction steps to implement automated database self-healing reconciliation and synchronized reactive starts counting between data and presentation layers.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-016` (*Authoritative Workout Start Count Self-Healing Reconciliation & Reactive Presentation*)
* **Test Mapping**: `TST-DAT-011` (*Authoritative Workout Start Count Self-Healing Reconciliation & Reactive Presentation Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites, database helpers, and UI components continue to pass cleanly.
2. **Thread Safety & Dispatcher Affinity**: All SQLite transactions and updates in `KnownLocationsDatabaseManager` remain confined to `KnownLocationsDB-Thread` (`dbDispatcher`).
3. **Immutability of Locked Locations**: User-locked reference altitudes (`is_locked = 1`) remain protected from alteration.
4. **Live Workout Recording Invariant**: Real-time workout start recording in `TrackerService.recordWorkoutStart()` remains authoritative for active recording sessions.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

```
┌─────────────────────────────────┐
│ WorkoutSummariesDatabaseManager │
│  + getAllWorkoutStartLocations()│
└────────────────┬────────────────┘
                 │ List<LatLng>
                 ▼
┌─────────────────────────────────┐
│ KnownLocationsDatabaseManager   │
│  - fix upsertLocationByGeofence │
│  + reconcileHitCounts(...)      │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ KnownLocationsRepository        │
│  + reconcileHitCounts() in init │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐       ┌───────────────────────┐
│ KnownLocationsViewModel         │ ◄───  │ WorkoutRepository     │
│  + startsByLocationId Flow      │       │  .allWorkouts Flow    │
│  + applySort(STARTS) with map   │       └───────────────────────┘
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ KnownLocationsScreen            │
│  KnownLocationCard: startsCount │
└─────────────────────────────────┘
```

### Component 1: `WorkoutSummariesDatabaseManager.java`
* Query `TABLE_EXTREMA_VALUES` for `LATITUDE` and `LONGITUDE` where `SENSOR_TYPE == LATITUDE` and `EXTREMA_TYPE == START`.
* Return all recorded start points as `List<LatLng>`.

### Component 2: `KnownLocationsDatabaseManager.java`
* Remove defective `hitCount + 1` increment in `upsertLocationByGeofence()` (line 403).
* Provide `reconcileHitCountsWithWorkoutSummaries(List<LatLng> startLocations)` to count matching starts ($d \le r$) and update `my_locations.hitCount`.

### Component 3: `KnownLocationsRepository.kt`
* Add `reconcileHitCounts()` and invoke it on `repositoryScope` during repository initialization.

### Component 4: `KnownLocationsViewModel.kt` & `KnownLocationsScreen.kt`
* Observe `workoutRepository.allWorkouts` and maintain `startsByLocationId: Map<Long, Int>`.
* Update sort order `STARTS` to use `startsByLocationId[it.id] ?: it.hitCount`.
* Render `startsCount` on `KnownLocationCard`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Authoritative Start Extraction in `WorkoutSummariesDatabaseManager.java`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
* Implement `public List<LatLng> getAllWorkoutStartLocations()`.
* Add unit test in `WorkoutSummariesDatabaseManagerTest.kt` verifying extraction of valid start coordinates.

### Step 2: Fix DEM Healing & Add Self-Healing in `KnownLocationsDatabaseManager.java`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java`
* In `upsertLocationByGeofence()`: remove `values.put(KnownLocationsDbHelper.HIT_COUNT, existing.hitCount + 1);`.
* Implement `public int reconcileHitCountsWithWorkoutSummaries(@NonNull List<LatLng> startLocations)`.

### Step 3: Startup Reconciliation in `KnownLocationsRepository.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/KnownLocationsRepository.kt`
* Add `suspend fun reconcileHitCounts(): Int` delegating to `databaseManager`.
* Dispatch `reconcileHitCounts()` in `init` block on `repositoryScope`.

### Step 4: Reactive Starts Counting in `KnownLocationsViewModel.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModel.kt`
* Add `workoutRepository: WorkoutRepository` dependency injection.
* Add `startsByLocationId: Map<Long, Int>` to `KnownLocationsUiState`.
* Collect `workoutRepository.allWorkouts` and compute real-time starts mapping.
* Update `applySort(KnownLocationSortOrder.STARTS)` to sort by `startsByLocationId[it.id] ?: it.hitCount`.

### Step 5: Screen & Card Starts Display in `KnownLocationsScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* In `KnownLocationCard`, accept parameter `startsCount: Int` (defaulting to `item.hitCount`).
* Display `startsCount` in `pluralStringResource(R.plurals.known_locations_starts, startsCount, startsCount)`.

### Step 6: Unit Test Construction & Execution
* Target Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModelTest.kt`
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsViewModelTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Step-by-step unit testing on each modified component.
  - Full clean-room regression run `./gradlew testDebugUnitTest`.
  - Manual code walkthrough verifying zero regressions in `upsertLocationByGeofence` and `KnownLocationCard`.
* **Rollback**:
  - Feature branch `feature/ATT-1734` is isolated from `sprint/2026-40.6`. If any regression is detected, the branch can be cleanly reset with `git checkout sprint/2026-40.6`.
