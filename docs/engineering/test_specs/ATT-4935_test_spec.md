# Stage 2: Requirement & Test Specification - ATT-4935: Periods Heatmap Sport Filtering & Unified Region Clustering

**Ticket**: [ATT-4935](https://rainerblind.atlassian.net/browse/ATT-4935)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-PER-024` (`REQ-PER-024.5`, refining `REQ-PER-024.1` and `REQ-PER-013`)  
**Test Mapping**: `TST-PER-032` (`TST-PER-032.1`..`TST-PER-032.5`)  
**Branch**: `feature/ATT-4935`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Functional Requirement Specification (`REQ-PER-024.5`)

* **Master Sport Filter Authority (`REQ-PER-024.5a`)**: The Periods header filter (`selectedSports: StateFlow<Set<BSportType>>`) SHALL serve as the authoritative Master Sport Filter governing both card mini-maps (`PeriodSummaryCard`) and the detailed map (`PeriodMapScreen` / `InteractivePeriodMap`).
* **Symmetric DTO Polyline Filtering (`REQ-PER-024.5b`)**: In `filterPeriodSummary()`, `workoutIdToAllPolylinesMap` and `workoutIdToPathMap` SHALL be filtered symmetrically alongside `workoutIdToPolylineMap` so preview generation consumes only matching sport routes.
* **Filtered Map & Region Clustering Initialization (`REQ-PER-024.5c`)**: In `selectPeriodForMap()`, loaded workouts SHALL be filtered by `selectedSports.value` before calling `SpatialRegionEngine.detectRegions()` and decoding `heatmapPathMap`.
* **Spatial Region Clustering Accuracy (`REQ-PER-024.5d`)**: Region detection and region chips (`Hauptregion` and secondary chips) SHALL reflect exclusively the geographic clusters and workout counts of the selected sports.
* **UI Streamlining (`REQ-PER-024.5e`)**: Redundant local sport selector state in `PeriodMapScreen.kt` SHALL be eliminated, slaving map rendering directly to the master filter.
* **Corridor Calibration Pipeline (`REQ-PER-024.5f`)**: Heatmap per-workout counting (`ATT-4932`) and logarithmic corridor normalization (`ATT-4929`) SHALL operate strictly on the sport-filtered path set.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-PER-024.1` (*Line-Density Heatmap Visuals & Adaptive Filtering*) and `REQ-PER-013` (*Spatial Region Partitioning*) targeting `PeriodsViewModel.kt`, `PeriodMapScreen.kt`, and `PeriodMapUtils.kt`.
2. **Historical Origin & Commit Trace**: Commit `548f3995` (`ATT-4480`) and commit `2bea16b7` (`ATT-4833`).
3. **Root Reason for Existing Formulation**: `PeriodMapScreen` originally hosted a standalone sport selector prototype before the Periods master header filter in `PeriodsTabsScreen` was established.
4. **Preservation of Core Invariants**: 60 fps main-thread performance, thread-safe asynchronous tile generation on `Dispatchers.Default`, and fallback to all sports when no chips are selected remain 100% preserved.

---

## 3. Verification Test Specification (`TST-PER-032`)

| Test Spec ID | Test Scope & Procedure | Expected Result | Target Test File |
| :--- | :--- | :--- | :--- |
| `TST-PER-032.1` | Verify `filterPeriodSummary()` filters `workoutIdToAllPolylinesMap` and `workoutIdToPathMap` symmetrically by `selectedSports`. | Unselected sport workout IDs and paths excluded from preview maps. | `PeriodsViewModelTest.kt` |
| `TST-PER-032.2` | Verify `selectPeriodForMap()` filters workouts by `selectedSports` before spatial clustering and heatmap map construction. | `heatmapPathMap` contains strictly selected sports; excluded sports omitted. | `PeriodsViewModelTest.kt` |
| `TST-PER-032.3` | Verify `SpatialRegionEngine.detectRegions()` yields region chips and `Hauptregion` matching the sport-filtered distribution. | Region counts and bounding boxes match selected sports accurately. | `PeriodsViewModelTest.kt` |
| `TST-PER-032.4` | Verify corridor normalization (`ATT-4929`) and per-workout counting (`ATT-4932`) compute strictly on filtered paths. | Max corridor overlap reflects only selected sport activities. | `PeriodCardMapStateTest.kt` |
| `TST-PER-032.5` | Clean-room full unit test regression suite execution (`./gradlew testDebugUnitTest`). | 100% test pass rate with zero regressions. | Full Suite |

---

## 4. Acceptance Criteria (Given-When-Then)

* **AC-1 (Card Mini-Map Filtering)**: Given a month with 28 bike and 3 run workouts, when filtering by Bike, then the mini-map renders only bike paths.
* **AC-2 (Detailed Map & Region Parity)**: Given a period with bike and run workouts in distinct regions, when navigating to `PeriodMapScreen` with Run selected, then `Hauptregion` and region chips reflect run coordinates and counts.
* **AC-3 (Symmetric DTO Filtering)**: Given `filterPeriodSummary()`, when a sport filter is applied, then `workoutIdToAllPolylinesMap` and `workoutIdToPathMap` contain only matching sport IDs.
