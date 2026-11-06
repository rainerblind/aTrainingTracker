# Stage 3: Implementation Plan - ATT-5480: Blue Track, Scissors Icon & Map Pins

**Ticket**: [ATT-5480](https://rainerblind.atlassian.net/browse/ATT-5480)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5480`  
**Requirement Mapping**: `REQ-UI-449`, `REQ-UI-450` (`REQ-UI-449.1`, `REQ-UI-449.2`, `REQ-UI-450.1`, `REQ-UI-450.2`)  
**Test Mapping**: `TST-UI-449`, `TST-UI-450` (`TST-UI-449.3`, `TST-UI-450.3`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `CreateLapOnMapSheet` and `MoveLapBoundaryOnMapSheet`, GPS polylines render green (`TrackType.GPS`) instead of canonical blue (`TrackType.BEST`), map apex pins hardcode `R.drawable.ic_distance` (Max Line Distance ruler), and action buttons reuse generic `ic_lap_add` or stopwatch `ic_lap_timer`. This plan specifies introducing dedicated vector assets, parameterizing `PathPreviewMap`, switching track types to `TrackType.BEST`, and updating action icons across Aftermath.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-449.1` (Scissors action icon), `REQ-UI-449.2` (TrackType.BEST blue track & scissors pin), `REQ-UI-450.1` (Shift boundary action icon), `REQ-UI-450.2` (TrackType.BEST blue track & move pin).
* **Test Mapping**: `TST-UI-449.3` (`CreateLapOnMapSheetTest.kt`, `WorkoutLapsIconContractTest.kt`), `TST-UI-450.3` (`MoveLapBoundaryOnMapSheetTest.kt`, `PathPreviewMapTest.kt`), `TST-PRO-034` (ASPICE SWE audit).

---

## 3. System Invariants & Preserved Behavior
1. **Backward Compatibility**: `PathPreviewMap` default parameters (`apexIconRes = R.drawable.ic_distance`, `apexColor = TTColor.ApexPoint`) ensure zero regressions in existing callers (`CropWorkoutScreen`, `WorkoutSummary`, `RouteItem`).
2. **Domain & Calculation Invariance**: `LapSplitAndMoveCalculator` math, sample bounds validation, and SQLite transactions remain 100% unaltered.
3. **Monolithic Density Parity**: New vector assets (`res/drawable/`) scale losslessly without density duplicates (Rule 67).
4. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 passes audit.

---

## 4. Proposed Architectural Changes
### Component 1: Dedicated Vector Drawables (`app/src/main/res/drawable/`)
* Create `ic_lap_split_scissors.xml` (24dp Material `ContentCut` scissors silhouette).
* Create `ic_lap_move_boundary.xml` (24dp Material `SwapHoriz` horizontal bidirectional shift arrows).

### Component 2: `PathPreviewMap.kt` Parameterization (`ui/map/`)
* Extend signature with optional parameters `apexIconRes: Int = R.drawable.ic_distance`, `apexColor: Color = TTColor.ApexPoint`, and `apexTitle: String? = null`.
* Pass `apexIconRes` and `apexColor` to `createSensorMarker(context, apexIconRes, apexColor)`.

### Component 3: `CreateLapOnMapSheet.kt` & `MoveLapBoundaryOnMapSheet.kt`
* `CreateLapOnMapSheet.kt`: Change `MapTrack.type` to `TrackType.BEST`; set `apexIconRes = R.drawable.ic_lap_split_scissors`; update `AppModalBottomSheet` and confirm button icons.
* `MoveLapBoundaryOnMapSheet.kt`: Change `MapTrack.type` to `TrackType.BEST`; set `apexIconRes = R.drawable.ic_lap_move_boundary`; update `AppModalBottomSheet` and confirm button icons.

### Component 4: Action Button Iconography Across Aftermath
* Update split action icons to `R.drawable.ic_lap_split_scissors` in `WorkoutLaps.kt`, `LapSplitVisualizer.kt`, and `MapDetailLayout.kt`.
* Update move boundary icons to `R.drawable.ic_lap_move_boundary` in `WorkoutLaps.kt`, `LapEditBottomSheet.kt`, and `LapSplitVisualizer.kt`.

### UI Consistency (Rule 23)
* **Reused components**: `PathPreviewMap`, `AppModalBottomSheet`, `createSensorMarker`.
* **Theme tokens**: `TTColor.TrackBest` (`Color.Blue`), `TTColor.ApexPoint`, `MaterialTheme.colorScheme.primary`. New one-off styles: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Create Vector Drawables (`ic_lap_split_scissors.xml`, `ic_lap_move_boundary.xml`)
* Author clean vector drawables in `app/src/main/res/drawable/`.

### Step 2: Parameterize `PathPreviewMap.kt` (`TST-UI-449.3`, `TST-UI-450.3`)
* Add `apexIconRes`, `apexColor`, and `apexTitle` with backward-compatible defaults.

### Step 3: Update `CreateLapOnMapSheet.kt` & `MoveLapBoundaryOnMapSheet.kt`
* Switch `MapTrack` to `TrackType.BEST` and pass dedicated `apexIconRes` to `PathPreviewMap`. Update sheet header and confirm icons.

### Step 4: Update Action Button Icons in Aftermath Call Sites
* Wire `ic_lap_split_scissors` and `ic_lap_move_boundary` in `WorkoutLaps.kt`, `LapEditBottomSheet.kt`, `LapSplitVisualizer.kt`, and `MapDetailLayout.kt`.

### Step 5: Author Contract Tests & Verification Gate (`TST-UI-449.5`, `TST-UI-450.5`)
* Create `WorkoutLapsIconContractTest.kt` verifying vector assets, TrackType.BEST usage, and icon bindings; run targeted regression suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: `WorkoutLapsIconContractTest`, `CreateLapOnMapSheetTest`, `MoveLapBoundaryOnMapSheetTest`, AAPT2 validation (`processDebugResources`).
* **Rollback**: `git checkout feature/ATT-5480 -- .` cleanly reverts branch changes.
