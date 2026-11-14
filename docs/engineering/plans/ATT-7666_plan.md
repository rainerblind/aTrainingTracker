# Stage 3: Implementation Plan - ATT-7666: Fix OutOfMemoryError in PeriodsRepository and PeriodSummaryCard

**Ticket**: [ATT-7666](https://rainerblind.atlassian.net/browse/ATT-7666)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-PER-039` (*Lazy Decoded Path Resolution, Bounded Period Mini-Map Caching and Lite Mode Rendering*)  
**Test Mapping**: `TST-PER-047` (*Memory-Bounded Period Polyline Retention and Lightweight GoogleMap Lifecycle Verification Suite*)  
**Branch**: `feature/ATT-7666`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
Multi-hour user sessions (~10.8h uptime) encounter fatal `OutOfMemoryError` (507MB / 512MB heap exhausted) followed by an Input Dispatching ANR. Memory profiling identified two major memory sinks:
1. `PeriodsRepository.enrich()` eagerly decodes `List<LatLng>` for up to 5 spatial anchors across all Day, Week, Month, and Year periods, pinning tens of thousands of `LatLng` objects in the singleton `_groupedPeriods` `StateFlow`, while duplicating full polyline collections 4x.
2. `PeriodSummaryCard` instantiates live interactive OpenGL `GoogleMap` composables in scrolling `LazyColumn` items without `liteMode(true)`, leaking ~65 background threads (`GL-Map`, `androidmapsapi-*`) and ~160 map engines.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-039` (`REQ-PER-039.1`..`REQ-PER-039.4`)
* **Test Mapping**: `TST-PER-047` (`TST-PER-047.1`..`TST-PER-047.5`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Visual Regression**: Card thumbnail framing, track colors, dark map styling, and multi-region badges remain 100% identical.
2. **Interactive Map Parity**: Tapping a card opens `PeriodMapScreen`, which continues to load all workouts and heatmaps via `periodsRepo.getWorkoutsForRange`.
3. **Thread Safety & Bounded Memory**: Lazy decoding occurs off main thread; LRU cache is bounded to 64 entries.
4. **Parent Human Gate Invariance**: Terminal ticket completion remains strictly reserved for the human user.

---

## 4. Proposed Architectural Changes
* **`PeriodsRepository.kt`**:
  * Set `workoutIdToPathMap = emptyMap()` in `enrich()` to eliminate eager `LatLng` graph retention in the singleton repository.
  * Restrict `PeriodSummary.polylines` strictly to `previewWorkouts` (`<= 8` tracks, `MAX_SUMMARY_THUMBNAIL_TRACKS`) instead of dumping all workout polylines.
  * Omit full collection duplication into `workoutIdToAllPolylinesMap` across all hierarchy levels.
* **`PeriodMapUtils.kt`**:
  * Implement thread-safe `LruCache<String, PeriodCardMapState>(64)` in `prepareCardMapPreview` keyed by `${summary.periodType}_${summary.sortKey}_${summary.polylines.hashCode()}` to eliminate redundant scroll re-decoding.
* **`PeriodSummaryCard.kt`**:
  * Configure `googleMapOptionsFactory = { GoogleMapOptions().liteMode(visuals.heatmapProvider == null) }` in `PeriodMultiWorkoutMap` to run vector polyline card previews in Lite Mode (bitmap-rendered, zero OpenGL threads).
* **UI Consistency (Rule 23)**:
  * Reference: `PathPreviewMap.kt` / `WorkoutClusterComponents.kt` (`liteMode(true)` for thumbnail maps). Zero new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Memory-Bounded Enrichment (`PeriodsRepository.kt`)
* In `enrich()`: set `workoutIdToPathMap = emptyMap()`, set `polylines = previewWorkouts.map { it.mapPolyline }.filter { it.isNotEmpty() }`, and avoid duplicating all polylines into `workoutIdToAllPolylinesMap`.
### Step 2: Bounded LRU Cache (`PeriodMapUtils.kt`)
* Implement thread-safe 64-entry `LruCache<String, PeriodCardMapState>` and query/update it within `prepareCardMapPreviewSync` / `prepareCardMapPreview`.
### Step 3: Lite Mode Map Rendering (`PeriodSummaryCard.kt`)
* In `PeriodMultiWorkoutMap`: pass `googleMapOptionsFactory = { GoogleMapOptions().liteMode(visuals.heatmapProvider == null) }`.
### Step 4: Unit Test Suite (`PeriodsRepositoryMemoryTest.kt`)
* Implement unit tests for `TST-PER-047.1`..`TST-PER-047.4` asserting empty `workoutIdToPathMap`, bounded `polylines.size <= 8`, LRU cache hit, and Lite Mode configuration.
### Step 5: Targeted Test Execution
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`TST-PER-047.1`..`TST-PER-047.5`), followed by full clean-room suite in Stage 5.
* **Rollback**: Clean git branch isolation on `feature/ATT-7666` allows full revert if necessary.
