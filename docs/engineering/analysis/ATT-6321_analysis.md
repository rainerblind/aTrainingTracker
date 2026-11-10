# Stage 1 Analysis: ATT-6321 - Filter Workout List by Clicked BSportType on KnownLocationCard

**Ticket**: [ATT-6321](https://atrainingtracker.atlassian.net/browse/ATT-6321)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6321`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
* **Context**: In [ATT-6210], `KnownLocationCard` (`KnownLocationsScreen.kt`) was enhanced to render workout start counts partitioned by `BSportType` (`Bike`, `Run`, `Other`) using authentic multi-color vector icons (`SportStartsItem`).
* **Defect**: The click modifier `Modifier.clickable(onClick = onShowWorkouts)` resides on the outer enclosing `Row`. Tapping an individual sport badge (e.g., *Radfahren 364* or *Laufen 159*) navigates to the workout list without sport filtering, displaying all workouts at that location unfiltered.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * In `KnownLocationsScreen.kt:921-947`, `onShowWorkouts` is bound to the parent `Row` of the starts metric. Individual `SportStartsItem` composables are non-interactive presentation elements without dedicated click callbacks or sport context.
  * In `WorkoutFilterCriteria.kt`, filter criteria models sub-sport ID (`sportTypeId`), but lacks a first-class base sport type (`bSportType: BSportType?`) dimension for cross-screen drill-down.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `KnownLocationsScreen.kt:921-947`: Clicking any badge invokes `onShowWorkouts`, passing only `KnownLocationItem` without sport context.
  * Inspected `ATrainingTrackerApp.kt:419-428`: Sets `WorkoutFilterCriteria(startLocationName, lat, lng, radius)` and navigates to `NavRoutes.WORKOUTS`.
  * Inspected `WorkoutTabsScreen.kt:211-230`: Pager pages naturally partition by `BSportType` (0 = All, 1 = Bike, 2 = Run, 3 = Other).
  * Audited `ActiveFilterChipsRow.kt:168`: Supports sub-sport chips (`sportTypeId`), but does not expose a chip for base `bSportType`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add dedicated `onShowWorkoutsForSport: (KnownLocationItem, BSportType) -> Unit` callback to `KnownLocationsScreen`, `KnownLocationsListContent`, and `KnownLocationCard`.
  * Make each `SportStartsItem` (`Bike`, `Run`, `Other`) independently clickable with dedicated test tags (`location_starts_badge_${item.id}_bike`, etc.).
  * Add `bSportType: BSportType? = null` to `WorkoutFilterCriteria` with JSON serialization, predicate evaluation in `matches()`, and removable chip in `ActiveFilterChipsRow`.
  * Wire `onShowWorkoutsForSport` in `ATrainingTrackerApp.kt` to pre-filter `WorkoutFilterCriteria` and synchronize `WorkoutSummariesTabbedScreen` pager tab.
  * Preserve fallback click `onShowWorkouts` when `sportStarts.total == 0` (displaying `0 Starts`).
  * Add comprehensive unit tests in `KnownLocationsScreenDrillDownTest.kt` and `WorkoutFilterCriteriaTest.kt`.
* **Out-of-Scope Non-Goals**: Database schema modifications, altering GPS start clustering logic, or modifying workout recording engine.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: `REQ-UI-470.6` (*Workout Starts Breakdown by Sport Type on KnownLocationCard*), refining `REQ-UI-185` (*Drill-Down Filter to View Workouts by Starting Location*) and `REQ-UI-469.2` (*Unboxed Starts Metric*).
* **Historical Origin & Commit Trace**: `ATT-1643` (`REQ-UI-185`), `ATT-5887` (`REQ-UI-469.2`), `ATT-6210` (commit `71f11f9e8c`, `REQ-UI-470.6`).
* **Root Reason for Existing Formulation**: Outer `Row` click was an interim implementation in ATT-6210 to retain the single-tap contract of `REQ-UI-185` before individual sport touch targets were separated.
* **Preservation of Core Invariants**: Outer test tag `location_starts_badge_${item.id}`, unboxed typography, multi-color icons (`tint = Color.Unspecified`), 0-starts fallback, card edit/delete interactions, and 9-language parity strictly preserved.
* **Side-Effect Audit (Rule 68)**: Zero SQLite changes; `WorkoutFilterCriteria` changes are fully backward-compatible (defaults to `null`); existing navigation routes unaffected.

---

## 5. Architectural Strategy & High-Level Solution
* **Component Refactoring**:
  * `SportStartsItem`: Accepts `onClick: () -> Unit` and `modifier: Modifier`.
  * `KnownLocationCard`: Emits `onShowWorkoutsForSport(item, BSportType.BIKE | RUN | UNKNOWN)` per sport badge when `sportStarts.total > 0`.
  * `WorkoutFilterCriteria`: Extends with `bSportType: BSportType? = null`. Updates `matches()`, `activeFilterCount`, `toJson()`, and `fromJson()`.
  * `ActiveFilterChipsRow`: Renders removable chip for active `bSportType` (e.g. `Radfahren ✕`).
  * `WorkoutSummariesTabbedScreen`: Observes `filterCriteria.bSportType` to sync pager tab (1 for Bike, 2 for Run, 3 for Other).
  * `ATrainingTrackerApp`: Supplies `onShowWorkoutsForSport` lambda constructing criteria with location + `bSportType`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regressions in existing location drill-down tests (`KnownLocationsScreenDrillDownTest`, `KnownLocationCardLayoutTest`).
  2. Backward compatibility for `WorkoutFilterCriteria` JSON serialization across app restarts.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Pure UI callback refinement and immutable filter criteria model extension with zero database or background service risk.
