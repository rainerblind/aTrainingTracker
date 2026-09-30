# Stage 2: Requirement & Test Specification - ATT-1734: [Lieblingsorte] Number of recorded starts on KnownLocationCard differs significantly from actual workout count

**Ticket**: [ATT-1734](https://rainerblind.atlassian.net/browse/ATT-1734)  
**Sub-task**: [ATT-1781](https://rainerblind.atlassian.net/browse/ATT-1781) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1396](https://rainerblind.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Requirement Mapping**: `REQ-DAT-016` (*Authoritative Workout Start Count Self-Healing Reconciliation & Reactive Presentation*)  
**Test Spec ID**: `TST-DAT-011`  
**Branch**: `feature/ATT-1734`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-DAT-016)

### 1.1 Problem Statement & Rationale
In Sprint Review 2026-40.5 (ATT-1643), athletes identified that the starts count displayed on `KnownLocationCard` (e.g. "Zu Hause: 625 Starts") diverged massively from the actual recorded workouts starting at that location (e.g. 45 workouts). While `ATT-1447` stopped new false increments during sensor warmup, it retained bloated legacy values ("Variante A"). Furthermore, `upsertLocationByGeofence()` still incremented `hitCount` on DEM elevation healing, deleted workouts never decremented `hitCount`, and imported workouts were never added. This requirement establishes authoritative self-healing reconciliation in SQLite and synchronized reactive starts counting in the UI.

### 1.2 Functional & Architectural Requirements

1. **Authoritative Workout Start Extraction (`WorkoutSummariesDatabaseManager.java`)**:
   - The database manager SHALL provide `public List<LatLng> getAllWorkoutStartLocations()`.
   - The method SHALL query `TABLE_EXTREMA_VALUES` where `SENSOR_TYPE == LATITUDE`, `EXTREMA_TYPE == START`, and both `LATITUDE` and `LONGITUDE` columns are non-null, returning a list of valid geodetic coordinates representing all recorded workout starting points.

2. **Elimination of DEM Healing Hit Count Increments (`KnownLocationsDatabaseManager.java`)**:
   - In `upsertLocationByGeofence()`, the system SHALL NOT increment `hitCount` (`existing.hitCount + 1`) when updating elevation or source for an existing location. DEM elevation healing MUST remain strictly read-only with respect to location start frequency.

3. **Database-Level Self-Healing Reconciliation (`KnownLocationsDatabaseManager.java`, `KnownLocationsRepository.kt`)**:
   - `KnownLocationsDatabaseManager` SHALL provide `public int reconcileHitCountsWithWorkoutSummaries(@NonNull List<LatLng> startLocations)`.
   - For each known location, the system SHALL calculate the exact count of coordinates in `startLocations` satisfying `WorkoutClusterEngine.distanceBetween(location.latLng, startPos) <= location.radius`.
   - If the calculated count differs from the persisted `hitCount`, the system SHALL update `hitCount` in `my_locations` table in SQLite within an atomic transaction.
   - During initialization of `KnownLocationsRepository`, the system SHALL dispatch this reconciliation asynchronously on `KnownLocationsDB-Thread` (`dbDispatcher`), self-healing legacy databases on cold start.

4. **Reactive UI Starts Counting (`KnownLocationsViewModel.kt`)**:
   - `KnownLocationsViewModel` SHALL inject `WorkoutRepository` and observe `workoutRepository.allWorkouts`.
   - `KnownLocationsUiState` SHALL expose `startsByLocationId: Map<Long, Int>`.
   - When either `locationsFlow` or `allWorkouts` emits, the system SHALL compute `startsByLocationId` matching each location against all workouts where `workout.startLatLng != null` and `WorkoutClusterEngine.distanceBetween(location.latLng, workout.startLatLng) <= location.radius`.
   - Sorting by `KnownLocationSortOrder.STARTS` SHALL order locations descending by `startsByLocationId[it.id] ?: it.hitCount`.

5. **Presentation & Navigation Consistency (`KnownLocationsScreen.kt`)**:
   - `KnownLocationCard` SHALL display `startsCount = uiState.startsByLocationId[item.id] ?: item.hitCount`.
   - When the athlete taps the Starts badge, the resulting filtered workout list SHALL display exactly the same count of sessions shown on the badge.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-DAT-015` (item 4: *Historical Data Preservation (Variante A)*), `REQ-DAT-007` (item 2: *Hit Count Frequency Tracking*), targeting `KnownLocationsDatabaseManager.java` and `KnownLocationsViewModel.kt`.
* **Historical Origin & Commit Trace**: Introduced in sprint `2026-39.3` (`ATT-1447`) via commit `bcfa9317`.
* **Root Reason for Existing Formulation**: `ATT-1447` preserved existing historical `hitCount` values without recalculation out of caution to avoid destructive database wipes, leaving pre-ATT-1447 cold-start bloat uncorrected.
* **Preservation of Core Invariants**: Decoupled sensor warmup (`REQ-DAT-015`), authoritative live workout start tracking (`TrackerService.recordWorkoutStart`), locked location immutability (`REQ-DAT-014`), and single-thread SQLite confinement (`KnownLocationsDB-Thread`) remain 100% strictly intact.

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Database Reconciliation)**:
  * *Given* a database containing a known location with bloated historical `hitCount = 625` and 45 workouts starting within its 200m radius,
  * *When* `reconcileHitCountsWithWorkoutSummaries()` executes,
  * *Then* `hitCount` in `my_locations` table SHALL be updated to 45.

* **Criterion 2 (DEM Healing Non-Mutation)**:
  * *Given* an existing known location with `hitCount = 10`,
  * *When* `upsertLocationByGeofence()` executes following an asynchronous Open-Meteo DEM resolution,
  * *Then* `hitCount` SHALL remain strictly 10.

* **Criterion 3 (Reactive UI State Update on Workout Deletion/Addition)**:
  * *Given* `KnownLocationsViewModel` observing 5 workouts at "Zu Hause" (`startsCount = 5`),
  * *When* a workout is deleted from `WorkoutRepository`,
  * *Then* `startsByLocationId` for "Zu Hause" SHALL immediately emit 4 without requiring an app restart.

* **Criterion 4 (Filter Navigation Parity)**:
  * *Given* a `KnownLocationCard` displaying "45 Starts",
  * *When* the user taps the Starts badge to filter workouts,
  * *Then* the number of workouts displayed in `WorkoutList` SHALL equal exactly 45.

### 1.5 System Invariants
1. Zero regression in existing unit tests and features.
2. Single-thread SQLite confinement on `KnownLocationsDB-Thread`.
3. Locked locations (`is_locked = 1`) preserve user-edited altitudes (`REQ-DAT-014`).
4. Live workout start tracking in `TrackerService.recordWorkoutStart()` remains active and unchanged (`REQ-DAT-015`).

---

## 2. Test Specification (TST-DAT-011)

### Test Case 1: `testReconcileHitCounts_updatesBloatedCountsToMatchWorkouts` (`TST-DAT-011.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerTest.kt`
* **Preconditions**: SQLite database with known location "Home" (lat 48.0, lon 11.0, radius 200m) having `hitCount = 625`.
* **Action**: Provide a list of 5 start coordinates within 200m and call `reconcileHitCountsWithWorkoutSummaries()`.
* **Expected Result**: "Home" location `hitCount` equals 5.

### Test Case 2: `testUpsertLocationByGeofence_doesNotIncrementHitCount` (`TST-DAT-011.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerTest.kt`
* **Preconditions**: Location "Home" with `hitCount = 10`.
* **Action**: Invoke `upsertLocationByGeofence()` with new DEM altitude.
* **Expected Result**: Location altitude is updated, but `hitCount` remains 10.

### Test Case 3: `testKnownLocationsViewModel_computesReactiveStartsCount` (`TST-DAT-011.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModelTest.kt`
* **Preconditions**: ViewModel initialized with test locations and `WorkoutRepository` emitting workouts.
* **Action**: Verify `uiState.value.startsByLocationId` and sort order `STARTS`.
* **Expected Result**: Starts count matches matching workouts; sort order places highest starts first.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-DAT-011.4`)
* **Scope**: Localization Parity Test
* **Goal**: Verify `R.plurals.known_locations_starts` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 5: Clean-Room Full Regression Suite (`TST-DAT-011.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate with zero failures across the entire suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-DAT-011.1` | Unit | `reconcileHitCountsWithWorkoutSummaries` | `REQ-DAT-016.3` | Specified |
| `TST-DAT-011.2` | Unit | `upsertLocationByGeofence` | `REQ-DAT-016.2` | Specified |
| `TST-DAT-011.3` | Unit | `KnownLocationsViewModel` | `REQ-DAT-016.4` | Specified |
| `TST-DAT-011.4` | Localization | Plurals audit (all 9 locales) | `REQ-DAT-016.5`, `REQ-UI-106` | Specified |
| `TST-DAT-011.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
