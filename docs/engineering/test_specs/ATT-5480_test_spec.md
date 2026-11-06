# Stage 2: Requirement & Test Specification - ATT-5480: Blue Track, Scissors Icon & Map Pins

**Ticket**: [ATT-5480](https://rainerblind.atlassian.net/browse/ATT-5480)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5480`  
**Requirement Mapping**: `REQ-UI-449`, `REQ-UI-450` | **Test Spec ID**: `TST-UI-449`, `TST-UI-450`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-449`, `REQ-UI-450`)

### 1.1 Problem Statement & Rationale
In `CreateLapOnMapSheet` and `MoveLapBoundaryOnMapSheet` (ATT-907, ATT-908), polylines render in green (`TrackType.GPS`) instead of canonical blue (`TrackType.BEST` / `TTColor.TrackBest`), and the movable split/boundary map pin reuses `PathPreviewMap`'s hardcoded Max Line Distance ruler icon (`R.drawable.ic_distance`). Furthermore, action buttons reuse generic lap add or stopwatch timer icons. The system SHALL instantiate `MapTrack` with `type = TrackType.BEST` in both sheets, parameterize `PathPreviewMap` with `apexIconRes: Int = R.drawable.ic_distance`, and wire dedicated vector drawables (`ic_lap_split_scissors.xml` and `ic_lap_move_boundary.xml`) across action buttons and map preview pins.

### 1.2 Functional & Architectural Requirements
1. **Canonical Blue Track (`REQ-UI-449.2`, `REQ-UI-450.2`)**: `CreateLapOnMapSheet.kt` and `MoveLapBoundaryOnMapSheet.kt` SHALL instantiate `MapTrack` with `type = TrackType.BEST` (rendering `TTColor.TrackBest` blue polyline).
2. **PathPreviewMap Marker Parameterization (`REQ-UI-449.2`, `REQ-UI-450.2`)**: `PathPreviewMap.kt` SHALL accept `apexIconRes: Int = R.drawable.ic_distance` (defaulting to the ruler icon to preserve backward compatibility for existing callers), passing `apexIconRes` to `createSensorMarker`.
3. **Dedicated Vector Drawables & Action Buttons (`REQ-UI-449.1`, `REQ-UI-450.1`)**:
   - Provide `ic_lap_split_scissors.xml` (scissors silhouette) and `ic_lap_move_boundary.xml` (bidirectional horizontal shift arrows) in `res/drawable/`.
   - Wire `ic_lap_split_scissors` on the split pin in `CreateLapOnMapSheet` and action buttons in `WorkoutLaps`, `LapSplitVisualizer`, and `MapDetailLayout`.
   - Wire `ic_lap_move_boundary` on the boundary pin in `MoveLapBoundaryOnMapSheet` and action buttons in `WorkoutLaps`, `LapEditBottomSheet`, `LapSplitVisualizer`, and `MoveLapBoundaryOnMapSheet`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Canonical Blue Track)**: *Given* `CreateLapOnMapSheet` or `MoveLapBoundaryOnMapSheet`, *When* rendered, *Then* the GPS track displays in canonical blue (`TrackType.BEST` / `TTColor.TrackBest`).
* **AC-2 (Dedicated Map Pins)**: *Given* map preview in `CreateLapOnMapSheet` / `MoveLapBoundaryOnMapSheet`, *When* rendered, *Then* the movable pin renders the scissors icon (`ic_lap_split_scissors`) for lap splitting and the shift boundary icon (`ic_lap_move_boundary`) for boundary moving, without displaying the ruler icon (`ic_distance`).
* **AC-3 (Action Button Iconography)**: *Given* lap split and move action buttons across `WorkoutLaps`, `LapEditBottomSheet`, `LapSplitVisualizer`, and `MapDetailLayout`, *When* rendered, *Then* each action displays its dedicated vector asset (`ic_lap_split_scissors` or `ic_lap_move_boundary`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-449.1`, `REQ-UI-449.2`, `REQ-UI-450.1`, `REQ-UI-450.2` / `TST-UI-449.3`, `TST-UI-450.3`. Targets `CreateLapOnMapSheet.kt`, `MoveLapBoundaryOnMapSheet.kt`, `PathPreviewMap.kt`, and lap action buttons across Aftermath.
* **Historical Origin & Commit Trace**: Sprint 2026-45.6 (`ATT-907`, commit `f1663727`; `ATT-908`, boundary moving) and Sprint 2026-42.3 (`ATT-3274`, commit `6ac6e7aa`, workout cropping).
* **Root Reason for Existing Formulation**: In ATT-907/ATT-908, `CreateLapOnMapSheet` and `MoveLapBoundaryOnMapSheet` borrowed `TrackType.GPS` (green polyline) from `CropWorkoutScreen` and used `PathPreviewMap`'s default `apex` marker which hardcoded `R.drawable.ic_distance` (Max Line Distance ruler icon). Action buttons also reused generic lap icons (`ic_lap_add`, `ic_lap_timer`).
* **Preservation of Core Invariants**: Default argument `apexIconRes = R.drawable.ic_distance` preserves 100% backward compatibility for existing callers (`CropWorkoutScreen`, `WorkoutSummary`, `RouteItem`); lap splitting/moving calculation math (`LapSplitAndMoveCalculator`) and SQLite transactions remain completely untouched; polyline downsampling and geometry bounding invariants are strictly preserved.

---

## 2. Test Specification (`TST-UI-449`, `TST-UI-450`)

### 2.1 Test Cases
1. **TC-1: CreateLapOnMapSheet Blue Track & Scissors Pin Contract (`TST-UI-449.3`)**:
   - *Scope*: Compose UI unit / contract test (`CreateLapOnMapSheetTest.kt`).
   - *Procedure*: Verify `CreateLapOnMapSheet` instantiates `MapTrack` with `type = TrackType.BEST` and passes `apexIconRes = R.drawable.ic_lap_split_scissors` to `PathPreviewMap`.
2. **TC-2: MoveLapBoundaryOnMapSheet Blue Track & Move Pin Contract (`TST-UI-450.3`)**:
   - *Scope*: Compose UI unit / contract test (`MoveLapBoundaryOnMapSheetTest.kt`).
   - *Procedure*: Verify `MoveLapBoundaryOnMapSheet` instantiates `MapTrack` with `type = TrackType.BEST` and passes `apexIconRes = R.drawable.ic_lap_move_boundary` to `PathPreviewMap`.
3. **TC-3: PathPreviewMap Parameterization & Default Backward Compatibility (`TST-UI-449.3`, `TST-UI-450.3`)**:
   - *Scope*: Compose UI contract test (`PathPreviewMapTest.kt`).
   - *Procedure*: Verify `PathPreviewMap` defaults to `apexIconRes = R.drawable.ic_distance` when omitted, and passes custom `apexIconRes` to `createSensorMarker` when specified.
4. **TC-4: Action Button Iconography Contract (`TST-UI-449.3`, `TST-UI-450.3`)**:
   - *Scope*: Compose UI contract test (`WorkoutLapsIconContractTest.kt`).
   - *Procedure*: Verify `WorkoutLaps`, `LapEditBottomSheet`, `LapSplitVisualizer`, and `MapDetailLayout` render `ic_lap_split_scissors` for split actions and `ic_lap_move_boundary` for boundary move actions.
5. **TC-5: Clean-Room Regression Suite (`TST-UI-449.5`, `TST-UI-450.5`)**:
   - *Command*: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.domain.laps.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
   - *Expected*: 100% pass rate with zero failures.

---

## 3. Traceability Matrix
| Requirement ID | Test Case ID | Scope | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-449.1` | `TST-UI-449.3` (TC-4) | Split Action Button Icon (`WorkoutLapsIconContractTest`) | Planned (`ATT-5480`) |
| `REQ-UI-449.2` | `TST-UI-449.3` (TC-1, TC-3) | Blue Track & Scissors Pin (`CreateLapOnMapSheetTest`) | Planned (`ATT-5480`) |
| `REQ-UI-450.1` | `TST-UI-450.3` (TC-4) | Move Action Button Icon (`WorkoutLapsIconContractTest`) | Planned (`ATT-5480`) |
| `REQ-UI-450.2` | `TST-UI-450.3` (TC-2, TC-3) | Blue Track & Move Pin (`MoveLapBoundaryOnMapSheetTest`) | Planned (`ATT-5480`) |
| `REQ-PRO-034` | `TST-PRO-034` (TC-5) | ASPICE SWE Audit (`AspiceSweAuditContractTest`) | Verified |
