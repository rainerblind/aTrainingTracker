# Stage 2: Requirement & Test Specification - ATT-6321: Filter Workout List by Clicked BSportType on KnownLocationCard

**Ticket**: [ATT-6321](https://atrainingtracker.atlassian.net/browse/ATT-6321) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6321`  
**Requirement Mapping**: `REQ-MAP-062` (`REQ-MAP-062.1`..`REQ-MAP-062.4`) | **Test Spec ID**: `TST-MAP-064` (`TST-MAP-064.1`..`TST-MAP-064.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-MAP-062`)

### 1.1 Problem Statement & Rationale
In [ATT-6210], `KnownLocationCard` (`KnownLocationsScreen.kt`) was enhanced to render workout start counts partitioned by `BSportType` (`Bike`, `Run`, `Other`) via `SportStartsItem`. However, `clickable(onClick = onShowWorkouts)` was bound to the enclosing `Row`, opening all workouts unfiltered instead of filtering by the tapped sport.

### 1.2 Functional & Architectural Requirements
* **REQ-MAP-062.1** (*Independent Sport Starts Touch Targets on KnownLocationCard*): In `KnownLocationCard` (`KnownLocationsScreen.kt`), when `sportStarts.total > 0`, each rendered `SportStartsItem` (`Bike`, `Run`, `Other`) SHALL be an independent clickable element (`Modifier.clickable`) with dedicated test tag `location_starts_badge_${item.id}_${sportName.lowercase()}`. Tapping a sport badge SHALL invoke `onShowWorkoutsForSport(item, bSportType)` with the respective `BSportType` (`BSportType.BIKE`, `BSportType.RUN`, `BSportType.UNKNOWN`).
* **REQ-MAP-062.2** (*Fallback Total Starts Touch Target*): When `sportStarts.total == 0` (or when tapping the surrounding container without a sport badge hit), tapping the starts badge SHALL invoke `onShowWorkouts(item)` to display all workouts at that location unfiltered.
* **REQ-MAP-062.3** (*Base Sport Type Filter Dimension in WorkoutFilterCriteria*): `WorkoutFilterCriteria` SHALL declare optional `bSportType: BSportType? = null`. When non-null, `matches(workout)` SHALL evaluate `workout.bSportType == bSportType`, increment `activeFilterCount`, support JSON serialization (`toJson()` / `fromJson()`), and render a removable filter chip in `ActiveFilterChipsRow`.
* **REQ-MAP-062.4** (*Cross-Screen Navigation & Tab Synchronization*): In `ATrainingTrackerApp.kt`, `onShowWorkoutsForSport` SHALL configure `WorkoutFilterCriteria` with location bounds (`startLocationName`, `startLocationLat`, `startLocationLng`, `startLocationRadiusM`) and `bSportType`, and navigate to `NavRoutes.WORKOUTS`. `WorkoutSummariesTabbedScreen` SHALL synchronize its active tab index to the corresponding sport tab (Index 1 = Bike, Index 2 = Run, Index 3 = Other).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Sport-Specific Badge Tap)**: *Given* a `KnownLocationCard` with sport starts breakdown (`sportStarts.total > 0`), *When* the athlete taps the Bike badge (`location_starts_badge_${item.id}_bike`), *Then* `onShowWorkoutsForSport` is triggered with `BSportType.BIKE`, opening `NavRoutes.WORKOUTS` with criteria pre-filtered for that location and `BSportType.BIKE`, and activating the Bike tab.
* **AC-2 (Fallback 0-Starts Tap)**: *Given* a `KnownLocationCard` with `sportStarts.total == 0`, *When* the athlete taps the starts badge, *Then* `onShowWorkouts` is triggered without sport filter, opening all workouts at that location.
* **AC-3 (Active Filter Chip & Removal)**: *Given* the workout list filtered by location and `BSportType.BIKE`, *When* viewing the top filter chip row, *Then* an active chip for `Bike` is displayed, and dismissing it clears the sport constraint while keeping location filtering active.

---

## 2. Test Specification & Traceability Matrix (`TST-MAP-064`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-062.1` | `TST-MAP-064.1` | `KnownLocationsScreenDrillDownTest.kt` | SportStartsItem clicks invoke `onShowWorkoutsForSport` with correct `BSportType` and test tags |
| `REQ-MAP-062.2` | `TST-MAP-064.2` | `KnownLocationsScreenDrillDownTest.kt` | Fallback 0-starts badge click invokes `onShowWorkouts` with location item |
| `REQ-MAP-062.3` | `TST-MAP-064.3` | `WorkoutFilterCriteriaTest.kt` | `bSportType` predicate matching, `activeFilterCount` increment, and JSON serialization parity |
| `REQ-MAP-062.4` | `TST-MAP-064.4` | `KnownLocationsScreenDrillDownTest.kt` | `ATrainingTrackerApp` wiring sets criteria with location + `bSportType` and syncs sport tab |
| `REQ-MAP-062` | `TST-MAP-064.5` | Targeted Test Suite | Targeted tests: `ui.knownlocations.*`, `ui.aftermath.workoutlist.*`, `ui.theme.*` |
