# Stage 3: Implementation Plan - ATT-4477: Replace 8-Track Compromise in PeriodSummaryCard with Full-Period Async Preview

**Ticket**: [ATT-4477](https://rainerblind.atlassian.net/browse/ATT-4477)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.9 | **Active Sprint**: `2026-43.9`  
**Requirement Mapping**: `REQ-PER-019` | **Test Mapping**: `TST-PER-025`  
**Branch**: `feature/ATT-4477`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Description & Background

`PeriodSummaryCard.kt` currently truncates map previews to at most 8 tracks (`MAX_SUMMARY_THUMBNAIL_TRACKS = 8`), concealing 90%+ of routes on Month and Year cards. Additionally, polyline decoding, Douglas-Peucker simplification, spatial region detection (`SpatialRegionEngine`), and `PolylineHeatmapTileProvider` construction execute synchronously inside Compose `remember` blocks on the Android main thread, causing severe frame drops during `LazyColumn` scrolling.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PER-019` (*Full-Period Asynchronous Mini-Map Preview & Background Simplification Pipeline*)
  * `TST-PER-025.1` (Full-period route ingestion without 8-track truncation)
  * `TST-PER-025.2` (Adaptive Douglas-Peucker simplification scaling by period type)
  * `TST-PER-025.3` (Spatial region clustering & camera framing off the main thread)
  * `TST-PER-025.4` (PolylineHeatmapTileProvider integration for Week, Month, and Year cards)
  * `TST-PER-025.5` (Empty and malformed GPS resilience)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database Migrations**: Zero changes to `PeriodSummaries.db` table schemas or column definitions.
2. **Main-Thread Purity**: Zero synchronous polyline decoding, decimation, or GIS clustering on the main thread during card composition.
3. **Multi-Region Badge Parity**: Region count and primary workout badges (`REQ-PER-013`) remain intact.
4. **Visual & Anti-Flash Parity**: Map container dimensions (250 dp), `DarkMapAntiFlashOverlay`, and dark AMOLED styling preserved.

---

## 4. Proposed Architectural Changes

### Component 1: `PeriodCardMapState.kt` Pipeline (`ui.aftermath.periodlist`)
* Define immutable `PeriodCardMapState(primaryPaths, bounds, visuals, regionCount, primaryWorkoutCount, isReady)`.
* Implement pure background builder `buildPeriodCardMapState(summary: PeriodSummary): PeriodCardMapState`:
  * Ingests 100% of available workout polylines (`workoutIdToPolylineMap` or `polylines`).
  * Decimates polylines via `PolyUtil.simplify` with period-scaled tolerances (`DAY`: 5m, `WEEK`: 15m, `MONTH`: 35m, `YEAR`: 75m) bounding vertices ($\le 2,000$ points).
  * Executes `SpatialRegionEngine.detectRegionsFromPaths` on background thread to select primary region and bounds.
  * Builds `PeriodMapVisuals` with `PolylineHeatmapTileProvider` across all primary region paths.

### Component 2: `PeriodSummaryCard.kt` Integration & UI Consistency (Rule 23)
* Replace synchronous `remember` blocks in `PeriodSummaryCard` with `produceState<PeriodCardMapState>` executing on `Dispatchers.Default`.
* `PeriodMultiWorkoutMap` directly consumes precomputed `PeriodCardMapState`.
* Reference screen: `PeriodSummaryCard` & `InteractivePeriodMap`. Reuses `DarkMapStyle`, `TileOverlay`, and layout tokens (`250.dp` height, rounded corners, multi-region chip).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `PeriodCardMapState.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodCardMapState.kt`
* Define data model and `buildPeriodCardMapState` background builder.

### Step 2: Refactor `PeriodSummaryCard.kt` to Asynchronous Pipeline
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt`
* Wire `produceState` off the UI thread; remove synchronous main-thread `remember` decoding and region detection.

### Step 3: Implement Unit Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodCardMapStateTest.kt`
* Test cases `TST-PER-025.1` through `TST-PER-025.5`.
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted package unit tests, `verify_requirement_governance.py`, and clean-room full suite in Stage 5.
* **Rollback**: Clean git revert of Stage 4 commits on `feature/ATT-4477`.
