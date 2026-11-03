# Stage 2: Requirement & Test Specification - ATT-4477: Replace 8-Track Compromise in PeriodSummaryCard with Full-Period Async Preview

**Ticket**: [ATT-4477](https://rainerblind.atlassian.net/browse/ATT-4477)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.9 | **Active Sprint**: `2026-43.9` | **Branch**: `feature/ATT-4477`  
**Requirement Mapping**: `REQ-PER-019` (refining `REQ-PER-012`, `REQ-PER-013`, `REQ-PER-017`)  
**Test Spec ID**: `TST-PER-025`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-PER-019`)

### 1.1 Problem Statement & Rationale
`PeriodSummaryCard.kt` currently truncates route previews to at most 8 tracks (`MAX_SUMMARY_THUMBNAIL_TRACKS = 8`), concealing 90%+ of an athlete's workouts on Month and Year cards. Additionally, polyline decoding (`PolyUtil.decode`), Douglas-Peucker simplification, region detection (`SpatialRegionEngine.detectRegionsFromPaths`), and `PolylineHeatmapTileProvider` construction execute synchronously inside Compose `remember` blocks on the Android main thread, causing severe frame drops during `LazyColumn` scrolling. Offloading this pipeline to `Dispatchers.Default` via `produceState<PeriodCardMapState>` and adaptively decimating polylines bounds vertices ($\le 2,000$ points) while rendering the full seasonal route network at 60 fps.

### 1.2 Functional & Architectural Requirements
1. **Asynchronous Background Offload (`REQ-PER-019.1`)**: `PeriodSummaryCard` SHALL NOT truncate route previews to 8 tracks. Polyline decoding, Douglas-Peucker decimation, region clustering, and `PolylineHeatmapTileProvider` construction SHALL execute exclusively on `Dispatchers.Default` via `produceState<PeriodCardMapState>`. Zero GIS or polyline computations SHALL occur on the main thread.
2. **Adaptive Douglas-Peucker Simplification (`REQ-PER-019.2`)**: Polylines SHALL be simplified using Douglas-Peucker decimation (`PolyUtil.simplify`) with tolerances scaled by period type: `DAY` (5.0m), `WEEK` (15.0m), `MONTH` (35.0m), and `YEAR` (75.0m), bounding total card vertices to $\le 2,000$ points.
3. **Background Region Clustering & Framing (`REQ-PER-019.3`)**: For multi-region periods ($> 75\text{ km}$ geodesic span), `SpatialRegionEngine.detectRegionsFromPaths` SHALL partition simplified paths and select the primary region for camera framing (`bounds`) and badge counts (`REQ-PER-013`).
4. **Line-Density Heatmap Integration (`REQ-PER-019.4`)**: For Week, Month, and Year periods, `PeriodCardMapState` SHALL supply all simplified primary region paths to `PolylineHeatmapTileProvider` (`REQ-PER-017`). Day periods SHALL emit `null` heatmap provider and render vector polylines.
5. **System Invariants & Resilience (`REQ-PER-019.5`)**: Empty or malformed GPS polylines SHALL resolve gracefully to `PeriodCardMapState(isReady = true)` without throwing exceptions. Zero `PeriodSummaries.db` schema mutations.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-012` (*Period Map Scalability, Memory Budget & Adaptive Layering Invariant*), targeting `PeriodSummaryCard.kt` and `PeriodsRepository.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-41.2 (commit `1c89f50e` / ATT-1151 / `MAX_SUMMARY_THUMBNAIL_TRACKS = 8`).
* **Root Reason for Existing Formulation**: Imposed an 8-track hard cap as a temporary mitigation against UI-thread ANRs and OOM crashes when Google Maps `MapView` and `HeatmapTileProvider` decoded and rendered unsimplified vector polylines synchronously inside Compose `remember` blocks during list scrolling.
* **Preservation of Core Invariants**: Memory safety without OOMs, multi-region indicator badge (`REQ-PER-013`), single-region camera framing, `DarkMapAntiFlashOverlay`, and non-blocking 60 fps `LazyColumn` scrolling remain 100% strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Full-Period Coverage)**: *Given* a Month or Year period with 30+ workouts, *When* `PeriodSummaryCard` is composed, *Then* all member workout polylines are decoded and simplified on `Dispatchers.Default` and rendered via `PolylineHeatmapTileProvider` without 8-track truncation.
* **Criterion 2 (Background Thread Decoupling)**: *Given* `LazyColumn` scrolling through period cards, *When* cards compose, *Then* zero `PolyUtil.decode`, simplification, or clustering calls execute on the main thread.
* **Criterion 3 (Empty GPS Fallback)**: *Given* a period with empty GPS data, *When* `PeriodCardMapState` evaluates, *Then* `isReady` is true with empty paths and null bounds, displaying the localized "No GPS data" fallback without crashing.

---

## 2. Test Specification (`TST-PER-025`)

### Test Cases
* `TST-PER-025.1` (**Full-Period Route Ingestion**): Assert `buildPeriodCardMapState` processes 100% of member polylines into `primaryPaths` when workout count $> 8$, eliminating the 8-track truncation cap.
* `TST-PER-025.2` (**Douglas-Peucker Adaptive Decimation**): Assert decimation tolerances scale by period type (`DAY`: 5m, `WEEK`: 15m, `MONTH`: 35m, `YEAR`: 75m) and bound vertex budgets.
* `TST-PER-025.3` (**Spatial Region Clustering Offload**): Assert multi-region clustering partitions paths, designates primary region, and sets tight bounds off the main thread.
* `TST-PER-025.4` (**PolylineHeatmapTileProvider Integration**): Assert `heatmapProvider` is non-null with all primary region paths for Week/Month/Year cards, and null for Day cards.
* `TST-PER-025.5` (**Empty & Malformed GPS Resilience**): Assert empty polyline lists or malformed strings produce a valid empty `PeriodCardMapState` with `isReady = true` without exceptions.
* `TST-PER-025.6` (**Clean-Room Regression Suite Execution**): Run targeted unit test suite `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-025.1` | Unit | `PeriodCardMapState.kt` / `buildPeriodCardMapState` | `REQ-PER-019.1` | Planned |
| `TST-PER-025.2` | Unit | `PeriodCardMapState.kt` / `buildPeriodCardMapState` | `REQ-PER-019.2` | Planned |
| `TST-PER-025.3` | Unit | `PeriodCardMapState.kt` / `SpatialRegionEngine` | `REQ-PER-019.3` | Planned |
| `TST-PER-025.4` | Unit | `PeriodCardMapState.kt` / `PolylineHeatmapTileProvider` | `REQ-PER-019.4` | Planned |
| `TST-PER-025.5` | Unit | `PeriodCardMapState.kt` / `buildPeriodCardMapState` | `REQ-PER-019.5` | Planned |
| `TST-PER-025.6` | Regression | `PeriodCardMapStateTest.kt` | `REQ-PER-019.5` | Planned |
