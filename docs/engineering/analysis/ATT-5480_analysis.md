# Stage 1 Analysis: ATT-5480 - CreateLapOnMapSheet & MoveLapBoundaryOnMapSheet: Blue Track (TrackType.BEST), Scissors Icon for Lap Split, and Dedicated Map Pin Icons

**Ticket**: [ATT-5480](https://rainerblind.atlassian.net/browse/ATT-5480)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5480`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During Sprint Review 2026-45.6 for ATT-907 (*Create Lap on Map*) and ATT-908 (*Move Lap Boundary on Map*), two visual and semantic inconsistencies were identified in `CreateLapOnMapSheet` and `MoveLapBoundaryOnMapSheet`:
1. Polylines render in green (`TrackType.GPS`) instead of canonical blue (`TrackType.BEST` / `TTColor.TrackBest`) used for primary workout routes across Aftermath.
2. The movable split/boundary map pin reuses `PathPreviewMap`'s hardcoded `R.drawable.ic_distance` (the ruler icon reserved for *Max Line Distance*), and action buttons reuse generic `ic_lap_add` or stopwatch `ic_lap_timer` rather than dedicated scissors and shift-boundary icons.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Track Color Origin**: In `CreateLapOnMapSheet.kt` (L187) and `MoveLapBoundaryOnMapSheet.kt` (L244), `MapTrack` was instantiated with `type = TrackType.GPS` (`Color.Green`), cloned from `CropWorkoutScreen.kt` (`ATT-3274`). Canonical workout route polylines across Aftermath use `TrackType.BEST` (`Color.Blue` / `TTColor.TrackBest`).
* **Hardcoded Ruler Marker**: `PathPreviewMap.kt` (L121) was originally built for route apex points (SCRUM-233) and hardcodes `createSensorMarker(context, R.drawable.ic_distance, TTColor.ApexPoint)`. Passing `apex = currentSplitPoint` / `currentJunctionPoint` caused the ruler icon to render on both lap map sheets.
* **Independent Peer Review & Verification**:
  * Audited `PathPreviewMap.kt`: Adding optional parameters `apexIconRes: Int = R.drawable.ic_distance`, `apexColor: Color = TTColor.ApexPoint`, and `apexTitle: String? = null` allows custom pins with zero regressions for existing callers (`CropWorkoutScreen`, `WorkoutSummary`, `RouteItem`).
  * Audited Action Buttons: `WorkoutLaps.kt` (L120, L134), `LapEditBottomSheet.kt` (L414), `LapSplitVisualizer.kt` (L268, L282), and `MapDetailLayout.kt` (L231) currently reuse `ic_lap_add` and `ic_lap_timer`. Introducing `ic_lap_split_scissors.xml` and `ic_lap_move_boundary.xml` standardizes iconography across both UI buttons and map pins.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Switch `MapTrack` to `type = TrackType.BEST` (blue) in `CreateLapOnMapSheet.kt` and `MoveLapBoundaryOnMapSheet.kt`.
  * Parameterize `PathPreviewMap.kt` with optional `apexIconRes`, `apexColor`, and `apexTitle`.
  * Create vector drawables `ic_lap_split_scissors.xml` and `ic_lap_move_boundary.xml` in `res/drawable/`.
  * Wire `ic_lap_split_scissors` on map pin and split buttons (`WorkoutLaps`, `LapSplitVisualizer`, `MapDetailLayout`, `CreateLapOnMapSheet`).
  * Wire `ic_lap_move_boundary` on map pin and move buttons (`WorkoutLaps`, `LapSplitVisualizer`, `LapEditBottomSheet`, `MoveLapBoundaryOnMapSheet`).
* **Out-of-Scope Non-Goals**:
  * Changing `TrackType.GPS` behavior or colors in `CropWorkoutScreen.kt`.
  * Altering lap splitting, boundary moving, or database transactions.
* **Complexity Rating**: **2 SP (Standard)** (Targeted UI, Compose map overlay, and vector icon enhancements).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-449.2` (*Interactive Split Bottom Sheet*) and `REQ-UI-450.2` (*Interactive Boundary Move Bottom Sheet*). Targets `CreateLapOnMapSheet.kt`, `MoveLapBoundaryOnMapSheet.kt`, `PathPreviewMap.kt`, `WorkoutLaps.kt`, `LapEditBottomSheet.kt`, `LapSplitVisualizer.kt`, and `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**: `ATT-907` (`f1663727`, map lap creation), `ATT-908` (boundary moving), and `ATT-3274` (`6ac6e7aa`, workout cropping).
* **Root Reason for Existing Formulation**: `ATT-3274` used `TrackType.GPS` for raw telemetry cropping; `PathPreviewMap` hardcoded `ic_distance` for max line distance. ATT-907/ATT-908 quickly adapted these without visual specialization.
* **Preservation of Core Invariants**:
  1. Backward compatibility: Default parameters in `PathPreviewMap` ensure zero breakage for existing callers.
  2. Data integrity: All lap calculations (`LapSplitAndMoveCalculator`) and SQLite transactions remain untouched.
  3. Polyline downsampling and geometry bounding invariants strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Vector Assets**: Add clean, monochrome vector drawables `ic_lap_split_scissors.xml` and `ic_lap_move_boundary.xml` in `app/src/main/res/drawable/`.
* **Map Component**: Extend `PathPreviewMap` with default `apexIconRes = R.drawable.ic_distance` and `apexColor = TTColor.ApexPoint`.
* **Sheet Map & Track**: In `CreateLapOnMapSheet.kt` and `MoveLapBoundaryOnMapSheet.kt`, instantiate `MapTrack` with `type = TrackType.BEST` and pass dedicated `apexIconRes`.
* **UI Entry Points**: Update action button icons across `WorkoutLaps.kt`, `LapEditBottomSheet.kt`, `LapSplitVisualizer.kt`, and `MapDetailLayout.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in `PathPreviewMap` callers, unchanged polyline geometries, preserved lap calculation math, 100% clean vector assets.
* **Risk Rating**: **LOW** (Presentation-layer polish; zero changes to database logic, domain calculation engines, or navigation lifecycles).
