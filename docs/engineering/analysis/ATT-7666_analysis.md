# Stage 1 Analysis: ATT-7666 - Fix OutOfMemoryError in PeriodsRepository and PeriodSummaryCard

**Ticket**: [ATT-7666](https://rainerblind.atlassian.net/browse/ATT-7666)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.6`  
**Branch**: `feature/ATT-7666`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
During long-running sessions (~10.8h uptime on `v273 / 5.0.0-dev`), the app crashes with a fatal `java.lang.OutOfMemoryError` (507MB / 512MB heap occupied, <1% free) followed by an Input Dispatching ANR. Forensic investigation traced memory exhaustion to:
1. **Unbounded eager polyline decoding & collection duplication in `PeriodsRepository`**: `enrich()` eagerly decodes `List<LatLng>` for up to 5 spatial anchors across all Day, Week, Month, and Year periods, retaining tens of thousands of `LatLng` objects in the singleton `_groupedPeriods` `StateFlow`, while duplicating full polyline collections (`polylines` and `workoutIdToAllPolylinesMap`) 4x across hierarchy levels.
2. **Full OpenGL `GoogleMap` churn in `PeriodSummaryCard`**: `PeriodMultiWorkoutMap` instantiates a live interactive OpenGL `GoogleMap` (without `liteMode(true)`) in every scrolling `LazyColumn` item across 4 tabs, leaking ~65 threads (`GL-Map`, `androidmapsapi-*`) and ~160 map engines over time.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * **Verified `PeriodsRepository.kt:1018-1022`**: Eagerly invokes `PolyUtil.decode(...)` into `workoutIdToPathMap` for all periods. The UI (`PeriodSummaryCard`) never reads `workoutIdToPathMap` (it uses `prepareCardMapPreview` asynchronously), while `InteractivePeriodMap` already has a fallback to decode `workoutIdToPolylineMap` lazily on demand.
  * **Verified `PeriodsRepository.kt:1002, 1140-1146`**: Bypasses the intended `MAX_SUMMARY_THUMBNAIL_TRACKS` (8) cap by copying all workouts' polylines into `polylines` and `workoutIdToAllPolylinesMap` across all 4 levels, duplicating `WorkoutRepository.allWorkouts` multiple times in memory.
  * **Verified `PeriodSummaryCard.kt:653`**: `GoogleMap` lacks `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }`, launching full OpenGL ES engines for simple thumbnail previews.
  * **Verified `PeriodMapUtils.kt:362`**: `prepareCardMapPreview` lacks bounded caching, causing repeated re-decoding and re-simplification on every scroll pass.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Defer `LatLng` path decoding to on-demand consumption (`workoutIdToPathMap = emptyMap()` in `PeriodsRepository.enrich()`).
  * Bound `PeriodSummary.polylines` strictly to `previewWorkouts` (`<= 8` tracks) and eliminate 4x `workoutIdToAllPolylinesMap` duplication.
  * Enable `GoogleMapOptions().liteMode(true)` for vector polyline card previews in `PeriodSummaryCard.kt` (`visuals.heatmapProvider == null`).
  * Introduce a thread-safe `LruCache<String, PeriodCardMapState>(64)` in `PeriodMapUtils.kt` to eliminate scroll re-decoding.
* **Out-of-Scope Non-Goals**:
  * Full-screen `PeriodMapScreen` remains a fully interactive OpenGL `GoogleMap` with zoom/pan and track-tap inspection.
  * No modifications to spatial region clustering algorithms or thermal heatmaps.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-021.3` (*Pre-decode anchor paths off main thread*).
* **Historical Origin & Commit Trace**: Added in ATT-4475 to prevent main-thread jank when opening interactive maps.
* **Root Reason for Existing Formulation**: Aimed to avoid `PolyUtil.decode` on UI thread, but eagerly decoded for all periods at startup into a singleton, exhausting the 512 MB heap.
* **Preservation of Core Invariants**: `InteractivePeriodMap` already decodes `workoutIdToPolylineMap` off main thread when launched. Setting `workoutIdToPathMap = emptyMap()` in the singleton eliminates OOM while preserving zero main-thread decoding. Net-new requirement: `REQ-PER-039`.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-PER-039` (Net-new), `REQ-PER-021.3` (Refined).
* **Affected Architecture Elements**: `PeriodsRepository.kt`, `PeriodSummaryCard.kt`, `PeriodMapUtils.kt`.
* **Verification Impact & Test Surface**: `TST-PER-047` (`PeriodsRepositoryMemoryTest.kt`), plus regression across `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*`.
* **Risk Assessment & Invariants**: Low risk. Visual parity, regional framing, and interactive navigation are 100% preserved.

---

## 6. Architectural Strategy & High-Level Solution
1. In `PeriodsRepository.enrich()`: set `workoutIdToPathMap = emptyMap()`, set `polylines = previewWorkouts.map { it.mapPolyline }`, and omit `workoutIdToAllPolylinesMap`.
2. In `PeriodMapUtils.kt`: wrap `prepareCardMapPreview` with a 64-entry `LruCache<String, PeriodCardMapState>`.
3. In `PeriodSummaryCard.kt`: pass `googleMapOptionsFactory = { GoogleMapOptions().liteMode(visuals.heatmapProvider == null) }`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero visual regression in period card map thumbnails; tap-to-open `PeriodMapScreen` continues to load all workouts; memory stays bounded during infinite scrolling.
* **Risk Rating**: **LOW** (Memory footprint reduction and standard Google Maps Lite Mode adoption).
