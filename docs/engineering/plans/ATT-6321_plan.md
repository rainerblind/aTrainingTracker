# Stage 3: Implementation Plan - ATT-6321: Filter Workout List by Clicked BSportType on KnownLocationCard

**Ticket**: [ATT-6321](https://atrainingtracker.atlassian.net/browse/ATT-6321)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6321`  
**Requirement Mapping**: `REQ-MAP-062` (`REQ-MAP-062.1`..`REQ-MAP-062.4`) | **Test Mapping**: `TST-MAP-064` (`TST-MAP-064.1`..`TST-MAP-064.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
In [ATT-6210], `KnownLocationCard` (`KnownLocationsScreen.kt`) was enhanced to render workout start counts partitioned by `BSportType` (`Bike`, `Run`, `Other`) via `SportStartsItem`. However, `clickable(onClick = onShowWorkouts)` was bound to the enclosing `Row`, opening all workouts unfiltered instead of filtering by the tapped sport. This ticket provides independent click targets on individual sport badges, routes them via `onShowWorkoutsForSport(item, bSportType)`, filters `WorkoutFilterCriteria` by `bSportType`, and synchronizes the active tab in `WorkoutSummariesTabbedScreen`.

---

## 2. Traceability & Requirements Mapping
* **REQ-MAP-062.1**: Independent sport starts touch targets on `KnownLocationCard` with test tags `location_starts_badge_${item.id}_${sport}` -> `TST-MAP-064.1`.
* **REQ-MAP-062.2**: Fallback 0-starts touch target invoking `onShowWorkouts(item)` -> `TST-MAP-064.2`.
* **REQ-MAP-062.3**: `bSportType: BSportType?` in `WorkoutFilterCriteria` with JSON serialization, predicate, and chip -> `TST-MAP-064.3`.
* **REQ-MAP-062.4**: Cross-screen navigation and tab synchronization in `ATrainingTrackerApp.kt` & `WorkoutSummariesTabbedScreen.kt` -> `TST-MAP-064.4`.

---

## 3. System Invariants & Preserved Behavior
1. **Interaction Invariants**: 0-starts fallback click target (`onShowWorkouts(item)`) and outer test tag `location_starts_badge_${item.id}` preserved.
2. **Card Integrity**: Card body single-tap edit, long-press delete/merge menu, and multi-color vector icons (`tint = Color.Unspecified`, 16.dp) in canonical order `[Bike, Run, Other]` remain intact.
3. **Database Invariants**: Zero alterations to SQLite databases or schemas.
4. **Serialization Parity**: `WorkoutFilterCriteria` JSON serialization remains 100% backward-compatible.

---

## 4. Proposed Architectural Changes
* **UI Component (`KnownLocationsScreen.kt`)**: Add `onClick: () -> Unit` and `modifier: Modifier` to `SportStartsItem`. Add `onShowWorkoutsForSport: (KnownLocationItem, BSportType) -> Unit` callback up the composable hierarchy (`KnownLocationCard` -> `KnownLocationsListContent` -> `KnownLocationsScreen`).
* **Domain Model (`WorkoutFilterCriteria.kt`)**: Add `val bSportType: BSportType? = null`. Update `matches(workout)`, `activeFilterCount`, `toJson()`, and `fromJson()`.
* **Filter Presentation (`ActiveFilterChipsRow.kt`)**: Render removable chip for active `bSportType` with `onRemoveBSportType`.
* **Navigation & Tab Sync (`ATrainingTrackerApp.kt`, `WorkoutSummariesTabbedScreen.kt`)**: Wire `onShowWorkoutsForSport` to build criteria with location + `bSportType` and activate matching sport tab in `pagerState`.
* **UI Consistency**: Reuses `SportStartsItem`, `RemovableFilterChip`, and `MaterialTheme.colorScheme.primary`. No new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Domain Model & Filter Chip (`WorkoutFilterCriteria.kt`, `ActiveFilterChipsRow.kt`)**:
  * Add `bSportType: BSportType? = null` to `WorkoutFilterCriteria`.
  * Update `matches()`, `activeFilterCount`, `toJson()`, `fromJson()`. Add chip in `ActiveFilterChipsRow.kt`.
* **Step 2: Composable Refinement (`KnownLocationsScreen.kt`)**:
  * Make `SportStartsItem` clickable with `onClick: () -> Unit` and test tags `location_starts_badge_${item.id}_${sport}`.
  * Wire `onShowWorkoutsForSport(item, bSportType)` across card and list composables, retaining 0-starts fallback.
* **Step 3: App Navigation & Tab Synchronization (`ATrainingTrackerApp.kt`, `WorkoutSummariesTabbedScreen.kt`)**:
  * Implement `onShowWorkoutsForSport` in `ATrainingTrackerApp.kt` navigating to `NavRoutes.WORKOUTS` with filtered criteria.
  * Sync `pagerState` in `WorkoutSummariesTabbedScreen.kt` when `filterCriteria.bSportType` is set.
* **Step 4: Unit Test Suite (`KnownLocationsScreenDrillDownTest.kt`, `WorkoutFilterCriteriaTest.kt`)**:
  * Implement `TST-MAP-064.1`..`TST-MAP-064.4` asserting callbacks, test tags, predicate matching, and navigation wiring.
* **Step 5: Targeted Unit Test Execution**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suites during construction; clean-room full suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-6321` allows full revert without affecting `sprint/2026-46.1`.
