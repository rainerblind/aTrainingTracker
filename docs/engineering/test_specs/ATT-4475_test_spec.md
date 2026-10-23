# Stage 2: Requirement & Test Specification - ATT-4475: Decouple Anchor Tracks, Eliminate UI-Thread Polyline Decoding & Restore Clickability

**Ticket**: [ATT-4475](https://rainerblind.atlassian.net/browse/ATT-4475)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-4475`  
**Requirement Mapping**: `REQ-PER-021` (refining `REQ-PER-012`, `REQ-PER-013`)  
**Test Spec ID**: `TST-PER-027`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-PER-021`)

### 1.1 Problem Statement & Rationale
In `InteractivePeriodMap.kt` and `PeriodsRepository.kt`, `summary.workoutIdToPolylineMap` was overloaded to contain all workouts in the period rather than only the 5 spatial anchors. This causes periods with $\le 30$ workouts to render every track twice (via `anchorTracks` at $\alpha=0.5$ and `memberTracks` at $\alpha=0.3$). For large periods ($> 30$ workouts), `InteractivePeriodMap` synchronously decodes all 300+ polylines on the Android UI thread inside `remember`, defeating the OOM guard while simultaneously dropping vector track clickability.

### 1.2 Functional & Architectural Requirements
1. **Spatial Anchor Scope Isolation in `groupedPeriods` (`REQ-PER-021.1`)**: In `PeriodsRepository.enrich()`, `PeriodSummary.workoutIdToPolylineMap` SHALL be strictly scoped to spatial anchor workouts ($\le 5$ workouts: N, S, E, W, Longest). Full member polylines SHALL NOT be duplicated across Day, Week, Month, and Year lists in `groupedPeriods`, but loaded on-demand in `PeriodsViewModel.showPeriodMap()` into `PeriodMapState`.
2. **Double-Rendering Elimination in `InteractivePeriodMap` (`REQ-PER-021.2`)**: In `InteractivePeriodMap.kt`, `anchorTracks` SHALL NOT render simultaneously with `memberTracks`. When `mapState.isLoading == false`, `anchorTracks` SHALL be hidden or deduplicated, preventing identical paths from being rendered twice at differing alpha values.
3. **Asynchronous Background Polyline Decoding (`REQ-PER-021.3`)**: All `PolyUtil.decode` operations for period map tracks SHALL execute exclusively on worker threads (`Dispatchers.Default`) in `PeriodsViewModel` or repository loaders. Synchronous `PolyUtil.decode` execution inside Compose `remember` on the Android UI thread SHALL be eliminated.
4. **Large-Period Route Network & Spatial Tap Hit-Testing (`REQ-PER-021.4`)**: For periods with $> 30$ workouts, routes SHALL be rendered via `PolylineHeatmapTileProvider` (`ATT-4480`), avoiding Google Maps `Polyline` object allocation overhead. Workout track clickability and peeking SHALL be supported on large periods via spatial tap hit-testing (`onMapClick(latLng)` invoking `PolyUtil.isLocationOnPath(latLng, path, true, toleranceMeters)`), dynamically selecting and highlighting the clicked workout route without instantiating hundreds of vector polylines.
5. **System & Localization Invariants (`REQ-PER-021.5`)**: Zero modifications to `PeriodSummaries.db` schema. Instant camera framing via bounds and anchor markers preserved. 100% clean-room test pass rate under `./gradlew testDebugUnitTest`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PER-012` (*Vector Track Safety Threshold*) and `REQ-PER-013` (*Period Map Performance*). Targets `InteractivePeriodMap.kt`, `PeriodMapUtils.kt`, `PeriodsRepository.kt`, `PeriodsViewModel.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-40.16 (ATT-346 / commit `7a192b0c` for anchor framing; ATT-440 for `PeriodMapState`).
* **Root Reason for Existing Formulation**: Anchors were designed for instant framing before full data loaded. When `allPolylineMap` was added to feed heatmaps, `workoutIdToPolylineMap` was overloaded without isolating anchor IDs.
* **Preservation of Core Invariants**: Instant geographic framing, zero OOMs, 60 fps map interaction, and 100% clean-room test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Deduplicated Rendering)**: *Given* a period with $\le 30$ workouts, *When* `InteractivePeriodMap` finishes loading (`mapState.isLoading == false`), *Then* member tracks are rendered exactly once without duplicate underlying anchor vector tracks.
* **Criterion 2 (Zero UI Polyline Decoding)**: *Given* a period with $> 30$ workouts, *When* `InteractivePeriodMap` renders, *Then* the Android UI thread executes zero synchronous `PolyUtil.decode` calls in Compose `remember`.
* **Criterion 3 (Large-Period Hit-Testing)**: *Given* a Month or Year period with $> 30$ workouts, *When* an athlete taps a route on the map, *Then* spatial hit-testing (`PolyUtil.isLocationOnPath`) resolves the clicked workout and highlights its route.
* **Criterion 4 (Anchor Scope Isolation)**: *Given* `groupedPeriods` in `PeriodsRepository`, *When* period summaries are enriched, *Then* `summary.workoutIdToPolylineMap` contains at most 5 spatial anchor polylines rather than the full period workout dataset.

---

## 2. Test Specification (`TST-PER-027`)

### Test Cases
* `TST-PER-027.1` (**Spatial Anchor Scope Isolation**): Assert `PeriodsRepository.enrich()` restricts `summary.workoutIdToPolylineMap` strictly to $\le 5$ spatial anchor workouts (N, S, E, W, Longest).
* `TST-PER-027.2` (**Interactive Map Track Deduplication**): Assert that when `mapState.isLoading == false`, `InteractivePeriodMap` suppresses or deduplicates `anchorTracks`.
* `TST-PER-027.3` (**Zero-UI-Thread Polyline Decoding**): Assert that polyline decoding for member and anchor tracks executes on `Dispatchers.Default` and not synchronously inside Compose `remember`.
* `TST-PER-027.4` (**Large-Period Route Visualization & Spatial Tap Hit-Testing**): Assert that periods with $> 30$ workouts render routes via `PolylineHeatmapTileProvider` without instantiating 50+ Google Maps `Polyline` objects, and spatial tap hit-testing resolves tapped workout paths accurately.
* `TST-PER-027.5` (**Clean-Room Regression Suite Execution**): Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-027.1` | Unit | `PeriodsRepository.kt` / `enrich()` | `REQ-PER-021.1` | Planned |
| `TST-PER-027.2` | UI / Unit | `InteractivePeriodMap.kt` / `ATrainingTrackerMap` content | `REQ-PER-021.2` | Planned |
| `TST-PER-027.3` | Unit | `PeriodsViewModel.kt` / `showPeriodMap()`, `Dispatchers.Default` | `REQ-PER-021.3` | Planned |
| `TST-PER-027.4` | Unit | `PeriodMapUtils.kt` / `PolylineHeatmapTileProvider`, `isLocationOnPath` | `REQ-PER-021.4` | Planned |
| `TST-PER-027.5` | Regression | Full Period List Suite (`ui.aftermath.periodlist.*`) | `REQ-PER-021.5` | Planned |
