# Stage 3: Implementation Plan - ATT-4935: Periods Heatmap Sport Filtering & Unified Region Clustering

**Ticket**: [ATT-4935](https://rainerblind.atlassian.net/browse/ATT-4935)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-PER-024` (`REQ-PER-024.5`)  
**Test Mapping**: `TST-PER-032` (`TST-PER-032.1`..`TST-PER-032.5`)  
**Branch**: `feature/ATT-4935`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
Periods heatmaps currently combine all sports into one layer, making distinct cycling corridors vs. running trail patterns indistinguishable. Although the Periods header provides sport filter chips (`selectedSports`), these filters do not propagate to card mini-maps, detailed map views, or spatial region clustering (`SpatialRegionEngine.detectRegions`). Furthermore, `PeriodMapScreen` maintains a disconnected local sport state, and `filterPeriodSummary` omits `workoutIdToAllPolylinesMap` from filtering. `ATT-4935` binds the header filter as the authoritative master filter across all map and region layers.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PER-024.5a/b` / `TST-PER-032.1`**: Symmetric filtering in `filterPeriodSummary()` for `workoutIdToAllPolylinesMap` and `workoutIdToPathMap`.
* **`REQ-PER-024.5c/e` / `TST-PER-032.2`**: `selectPeriodForMap()` filters workouts by `selectedSports.value` before generating `heatmapPathMap`; redundant local toggle in `PeriodMapScreen` removed.
* **`REQ-PER-024.5d` / `TST-PER-032.3`**: Spatial region detection and region chips (`Hauptregion`) reflect only sport-filtered workouts and counts.
* **`REQ-PER-024.5f` / `TST-PER-032.4`**: `ATT-4929` corridor frequency normalization and `ATT-4932` pixel counting operate strictly on the filtered routes.

---

## 3. System Invariants & Preserved Behavior
1. **Master Filter Dominance**: Header sport chips immediately update card previews, full-screen heatmaps, and region metrics.
2. **Empty Filter Fallback**: If no sport chips are active, system defaults gracefully to all recorded sports.
3. **60 FPS Fluidity & Thread Safety**: All polyline decoding and spatial clustering run on `Dispatchers.Default`.
4. **Code Lock (Rule 4)**: Production source code remains locked until Gate 3 audit passes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **Symmetric DTO Polyline Filtering (`PeriodsViewModel.kt`)**:
  * In `filterPeriodSummary()`, filter `workoutIdToAllPolylinesMap` and `workoutIdToPathMap` using `summary.workoutIdToSportMap[id] in sports`.
* **Filtered Map State Generation (`PeriodsViewModel.kt`)**:
  * In `selectPeriodForMap()`, filter loaded workouts by `selectedSports.value` prior to calling `SpatialRegionEngine.detectRegions()` and populating `heatmapPathMap`.
* **UI Streamlining (`PeriodMapScreen.kt`)**:
  * Remove detached `selectedSports` state and redundant sport chips row from `PeriodMapScreen.kt`. Map layers render directly from `mapState`.
* **Card Map Invalidation (`PeriodSummaryCard.kt`)**:
  * Key `produceState` on `summary.polylines.hashCode()` ensuring card mini-maps refresh immediately on sport filter toggles.

### UI Consistency (Rule 23)
* **Reference Component**: Existing Periods tabs and maps (`PeriodsTabsScreen.kt`, `PeriodMapScreen.kt`).
* **Visual Theme Tokens**: Canonical header sport chips (`BSportType`) and `SpatialRegion` chip styling preserved.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`PeriodsViewModel.kt`)**: Update `filterPeriodSummary()` to filter `workoutIdToAllPolylinesMap` and `workoutIdToPathMap` symmetrically.
2. **Step 2 (`PeriodsViewModel.kt`)**: Update `selectPeriodForMap()` to filter workouts by `selectedSports.value` before clustering regions and generating heatmap paths.
3. **Step 3 (`PeriodMapScreen.kt`)**: Remove local sport selector state and chips row; bind screen directly to `mapState`.
4. **Step 4 (`PeriodSummaryCard.kt`)**: Verify reactive `produceState` invalidation on filtered summary updates.
5. **Step 5 (`PeriodsViewModelTest.kt` & `PeriodCardMapStateTest.kt`)**: Implement unit tests for `TST-PER-032.1`..`TST-PER-032.4`.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodCardMapStateTest" --tests "com.atrainingtracker.trainingtracker.ui.map.*"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Full git branch isolation on `feature/ATT-4935`.
