# Stage 3: Implementation Plan - ATT-6210: Sport Type Breakdown on KnownLocationCard

**Ticket**: [ATT-6210](https://atrainingtracker.atlassian.net/browse/ATT-6210)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6210`  
**Requirement Mapping**: `REQ-UI-470` (`REQ-UI-470.6`) | **Test Mapping**: `TST-UI-470` (`TST-UI-470.7`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Description & Objective
`KnownLocationCard` (`KnownLocationsScreen.kt`) currently renders a static runner icon (`Icons.AutoMirrored.Filled.DirectionsRun`) before the starts count, regardless of which sports actually started at that location. For locations predominantly used for cycling (e.g. *IAV*, *Zu Hause*) or mixed sports, this is misleading and conceals the athletic profile of the location. This plan partitions starts by `BSportType` into `SportStartsCount(bike, run, other)` and displays them using authentic multi-color sport logos (`Color.Unspecified`) in canonical order `[Bike, Run, Other]`.

---

## 2. Requirements & Living Documentation Mapping
* **Requirement**: `REQ-UI-470.6` (*Workout Starts Breakdown by Sport Type*).
  * `SportStartsCount(bike: Int, run: Int, other: Int)` computed in `KnownLocationsViewModel` from `workoutRepo.allWorkouts` (`bSportType` + `startLatLng`).
  * `KnownLocationCard` displays sports with `count > 0` in canonical order: Bike (`R.drawable.bsport_bike`), Run (`R.drawable.bsport_run`), Other (`R.drawable.bsport_other` on far right) with `tint = Color.Unspecified`.
  * Fallback to `0 Starts` when `sportStarts.total == 0`. Click action `onShowWorkouts` and test tag `location_starts_badge_${item.id}` preserved.
* **Test Mapping**: `TST-UI-470.7` (`TST-UI-470.7a` ViewModel unit test, `TST-UI-470.7b` Card layout contract test, `TST-UI-470.7c` Tier 1 regression).

---

## 3. System Invariants & UI Consistency
1. **Canonical Sport Order**: `design_guidelines.md` §1.2 canonical order (`Bike`, `Run`, `Other` on far right) strictly enforced.
2. **Authentic Multi-Color Graphics**: Uses `tint = Color.Unspecified` to preserve multi-color master paths (`R.drawable.bsport_*`) without monochrome tinting.
3. **Layout & Tag Invariant**: Unboxed bottom-anchored alignment, test tag `location_starts_badge_${item.id}`, and `onShowWorkouts` drill-down strictly preserved.
4. **Database Non-Invasive**: 100% reactive in-memory calculation; zero SQLite schema modifications or migrations.

---

## 4. Architectural Strategy & Component Design
* **Component 1: `KnownLocationsViewModel.kt`**:
  * Define `data class SportStartsCount(val bike: Int = 0, val run: Int = 0, val other: Int = 0) { val total: Int get() = bike + run + other }`.
  * Add `sportStartsByLocationId: Map<Long, SportStartsCount>` to `KnownLocationsUiState`.
  * Refactor `groupStartsByLocation` to partition workouts into `SportStartsCount` by mapping `workout.bSportType` (`BIKE` -> `bike`, `RUN` -> `run`, others -> `other`).
  * Populate `startsByLocationId` via `sportStartsMapping.mapValues { it.value.total }` to preserve sort/filter compatibility.
* **Component 2: `KnownLocationsScreen.kt`**:
  * In `KnownLocationCard`, accept `sportStarts: SportStartsCount`.
  * In `location_starts_badge_${item.id}`, if `sportStarts.total > 0`, render each active sport as `[bsport_logo] count` (16.dp, `Color.Unspecified`, `bodyMedium` bold primary text, 8.dp item spacing, 3.dp icon-text spacing). If `total == 0`, render `pluralStringResource(R.plurals.known_locations_starts, startsCount, startsCount)`.
* **Component 3: Unit & Contract Tests**:
  * `KnownLocationsViewModelTest.kt`: Test `groupStartsByLocation` partitions Bike, Run, Other counts.
  * `KnownLocationCardLayoutTest.kt`: Update `testUnboxedPrimaryStartsMetric` to verify `bsport_bike`, `bsport_run`, `bsport_other`, `Color.Unspecified`, and canonical ordering.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Check
* Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` to verify Gate 3 PASS before code edits.

### Step 2: Implement `SportStartsCount` and Aggregation in `KnownLocationsViewModel.kt`
* Add `SportStartsCount` data class and `sportStartsByLocationId` in `KnownLocationsUiState`.
* Refactor `groupStartsByLocation` to return `Pair<Map<Long, Int>, Map<Long, SportStartsCount>>` or derive total counts from breakdown.

### Step 3: Implement Dynamic Sport Starts Display in `KnownLocationsScreen.kt`
* Add `SportStartsItem` composable using `bsport_*` drawables and `tint = Color.Unspecified`.
* Wire `sportStarts` parameter to `KnownLocationCard` and update Compose Previews.

### Step 4: Update Unit & Contract Tests
* Update `KnownLocationsViewModelTest.kt` with sport breakdown tests.
* Update `KnownLocationCardLayoutTest.kt` and `KnownLocationsScreenTest.kt` with sport icon contracts.

### Step 5: Verification & Governance
* Targeted tests: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Governance audit: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-45.13`
