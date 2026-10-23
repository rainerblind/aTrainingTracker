# Stage 3: Implementation Plan - ATT-4475: Decouple Anchor Tracks, Eliminate UI-Thread Polyline Decoding & Restore Clickability

**Ticket**: [ATT-4475](https://rainerblind.atlassian.net/browse/ATT-4475)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-4475`  
**Requirement Mapping**: `REQ-PER-021` (`REQ-PER-021.1`..`REQ-PER-021.5`)  
**Test Mapping**: `TST-PER-027` (`TST-PER-027.1`..`TST-PER-027.5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Description & Background
In `InteractivePeriodMap.kt` and `PeriodsRepository.kt`, `summary.workoutIdToPolylineMap` is populated with all period workouts, causing `InteractivePeriodMap` to render anchor tracks ($\alpha=0.5$) and member tracks ($\alpha=0.3$) simultaneously for $\le 30$ workouts (double rendering). On $> 30$ workouts, `PeriodsViewModel` sets `tracks = emptyList()`, but `anchorTracks` synchronously decodes all 300+ polylines on the UI thread inside Compose `remember`, defeating the OOM guard and leaving routes unclickable. Additionally, `allPolylineMap` is duplicated 4x in RAM across Day, Week, Month, and Year in `groupedPeriods`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-021` (*Decoupled Anchor Routes, Deduplicated Map Rendering, Zero Main-Thread Decoding & Spatial Hit-Testing*)
* **Test Mapping**: `TST-PER-027` (`TST-PER-027.1`..`TST-PER-027.5`)
* **Invariants**: Coding Standards §1.1 (DRY/reuse), §1.2 (Chesterton's fence), non-blocking UI thread, parent Human Decision Gate.

---

## 3. Proposed Architectural Changes
* **Repository Anchor Isolation (`PeriodsRepository.kt`)**:
  - In `enrich()`, populate `summary.workoutIdToPolylineMap` strictly with `anchorWorkouts` ($\le 5$ workouts: N, S, E, W, Longest).
  - Cease attaching full `allPolylineMap` to every period in `groupedPeriods`, eliminating 4x heap polyline duplication.
* **Map Deduplication & Main-Thread Decoding Elimination (`InteractivePeriodMap.kt`)**:
  - Render `anchorTracks` exclusively while `mapState.isLoading == true`; hide anchor tracks once `mapState` finishes loading so only `memberTracks` render.
  - Eliminate synchronous `PolyUtil.decode` calls in Compose `remember`.
* **Spatial Tap Hit-Testing for Large Periods (`PeriodMapUtils.kt` / `PeriodsViewModel.kt`)**:
  - Implement `findClosestTrackOnMapClick(tapLatLng, workoutPaths, zoom)` using `PolyUtil.isLocationOnPath` with zoom-adaptive tolerance (10m–50m).
  - Support tapping routes in large periods (> 30 workouts) rendered via `PolylineHeatmapTileProvider` (ATT-4480), highlighting the selected workout as a single active vector `Polyline`.
* **UI Consistency (Rule 23)**:
  - Reference: Existing `InteractivePeriodMap` & `ATrainingTrackerMap`.
  - Reused components: `PolylineHeatmapTileProvider`, `LocationMarker`, `MapTrack`.
  - Theme tokens: `TTColor.RouteActiveNavigation`, `MaterialTheme.colorScheme.primary`.
  - New one-off styles: None.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Repository Anchor Isolation (`PeriodsRepository.kt`)**: Restrict `summary.workoutIdToPolylineMap` strictly to `anchorWorkouts` ($\le 5$). Full polylines load on-demand in `showPeriodMap()`.
2. **Spatial Tap Hit-Testing Utility (`PeriodMapUtils.kt`)**: Add `findClosestTrackOnMapClick(tap: LatLng, paths: Map<Long, List<LatLng>>, zoom: Float): Long?` using `PolyUtil.isLocationOnPath`.
3. **Interactive Map Deduplication & Tap Wiring (`InteractivePeriodMap.kt`)**:
   - Condition `anchorTracks` rendering on `mapState.isLoading`.
   - Wire map click listener (`onMapClick`) to resolve tapped workout via `findClosestTrackOnMapClick`.
   - Render a highlighted vector `Polyline` for the currently selected/peeked workout.
4. **ViewModel State & Path Management (`PeriodsViewModel.kt`)**: Ensure `showPeriodMap` exposes pre-decoded paths on `Dispatchers.Default` and handles selected workout peeking.
5. **Contract & Unit Testing (`InteractivePeriodMapTest.kt`)**: Implement `TST-PER-027.1`..`TST-PER-027.5` verifying anchor isolation, track deduplication, zero UI-thread decoding, and spatial tap hit-testing. Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`.

---

## 5. Verification & Rollback Plan
* **Verification**: Targeted package unit tests + `./gradlew processDebugResources` + `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation allows full revert to `sprint/2026-43.9` without affecting existing commits.
