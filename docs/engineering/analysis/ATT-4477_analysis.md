# Stage 1 Analysis: ATT-4477 - Replace 8-Track Compromise in PeriodSummaryCard with Full-Period Async Preview

**Ticket**: [ATT-4477](https://rainerblind.atlassian.net/browse/ATT-4477)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.9 | **Active Sprint**: `2026-43.9`  
**Branch**: `feature/ATT-4477`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation

`PeriodSummaryCard.kt` currently suffers from a double bottleneck during `LazyColumn` scrolling:
* **8-Track Spatial Truncation**: `PeriodsRepository.enrich()` truncates `summary.polylines` to at most 8 tracks (`MAX_SUMMARY_THUMBNAIL_TRACKS = 8`). For Month (20–40 workouts) and Year (100–300 workouts) cards, 90%+ of the athlete's routes are completely omitted from the card preview and heatmap.
* **Synchronous UI-Thread Decoding & Region Detection**: Inside `PeriodSummaryCard.kt:199-261` and `PeriodMultiWorkoutMap:528-535`, `PolyUtil.decode`, `SpatialRegionEngine.detectRegionsFromPaths`, and `getPeriodMapVisuals` execute synchronously on the Android main thread inside `remember` blocks on every card composition during list scroll.

---

## 2. Root Cause Analysis & Independent Peer Review (ATT-3534)

* **Root Cause**: `MAX_SUMMARY_THUMBNAIL_TRACKS = 8` was introduced in `REQ-PER-012` as a temporary memory hedge against OOM crashes when Google Maps `MapView` instances rendered unsimplified full-resolution vector polylines.
* **Independent Verification & Feasibility**:
  * *Full Route Availability*: `summary.workoutIdToPolylineMap` already contains 100% of the period's workout polylines.
  * *Background Asynchronous Pipeline*: Offload decoding, Douglas-Peucker simplification (`PolyUtil.simplify`), region detection (`SpatialRegionEngine`), and `PolylineHeatmapTileProvider` creation to `Dispatchers.Default` via `produceState<PeriodCardMapState>`.
  * *Adaptive Simplification Tolerance*: Scale simplification tolerance by period type (`DAY`: 5m, `WEEK`: 15m, `MONTH`: 35m, `YEAR`: 75m) to bound total vertices per card ($\le 2,000$ points) regardless of workout volume.
  * *Heatmap Integration (ATT-4480)*: Feed all simplified regional paths into `PolylineHeatmapTileProvider` (`REQ-PER-017`), providing rich line-density heatmaps without UI-thread overhead.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Offload card polyline decoding, simplification, and region detection to `Dispatchers.Default`.
  * Define `PeriodCardMapState` holding resolved primary paths, bounds, visuals, and region count.
  * Feed complete period route network into `PolylineHeatmapTileProvider` on Week, Month, and Year cards.
  * Ensure smooth 60 fps `LazyColumn` scrolling with zero main-thread polyline decoding or GIS clustering.
  * Unit tests validating asynchronous state production, simplification bounds, and region resolution.
* **Out-of-Scope Non-Goals**:
  * `InteractivePeriodMap.kt` interactive hit-testing (`ATT-4475`).
  * `PeriodSummaries.db` persistent N+1 query elimination (`ATT-4476`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirements**: `REQ-PER-010`, `REQ-PER-012`, `REQ-PER-013` in `docs/requirements/req_aftermath_periods.md`.
* **Historical Origin**: Sprint 2026-40.16 (ATT-440), Sprint 2026-41.2 (ATT-1151 / REQ-PER-012).
* **Root Reason for Existing Formulation**: 8-track cap prevented UI-thread ANRs when all polylines were decoded synchronously inside `remember`. Moving the pipeline to `Dispatchers.Default` eliminates the ANR root cause.
* **Preservation of Core Invariants**: Multi-region badge indicator (`REQ-PER-013`), click-to-detail navigation, single-region camera framing, and `DarkMapAntiFlashOverlay` remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

| Component | Responsibility |
| :--- | :--- |
| `PeriodCardMapState` | Immutable data model holding `primaryPaths`, `bounds`, `visuals`, `regionCount`, and `isReady`. |
| `PeriodSummaryCard.kt` | Uses `produceState` on `Dispatchers.Default` to decode, simplify, and frame routes off the main thread. |
| `PeriodMultiWorkoutMap` | Renders precomputed `TileOverlay` and prominent simplified vectors without redundant `remember` decoding. |

---

## 6. System Invariants & Risk Assessment

* **System Invariants**: Zero changes to `PeriodSummaries.db` schema; zero main-thread polyline decoding; 100% clean-room test pass rate; non-crashing on empty GPS data.
* **Risk Rating**: **LOW** (UI optimization and background computation within `PeriodSummaryCard`; no database mutations or foreground sensor services touched).
