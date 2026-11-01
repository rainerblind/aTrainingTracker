# Stage 1 Analysis: ATT-4935 - Periods Heatmap Sport Filtering & Unified Region Clustering

**Ticket**: [ATT-4932](https://rainerblind.atlassian.net/browse/ATT-4935)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4935`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Sprint Review 2026-44.1 of `ATT-4833` on Pixel 10 (Juli 2026: 28 bike + 3 run workouts) highlighted that Periods heatmaps conflate all sports into a single composite layer. Running and cycling exhibit vastly different spatial footprints (e.g. forest trail loops vs. city commuter arteries), making mixed density overlays difficult to interpret. While the Periods header provides sport filter chips (`selectedSports`), these filters do not propagate to the card mini-maps, full-screen map, or region clustering.

---

## 2. Root Cause Analysis & Independent Verification (4-Augen-Prinzip)
Codebase audit of `PeriodsViewModel.kt`, `PeriodMapScreen.kt`, and `PeriodMapUtils.kt` reveals four architectural disconnects:
* **Unfiltered Map Loading**: In `PeriodsViewModel.selectPeriodForMap()`, `periodsRepo.getWorkoutsForRange()` loads all workouts for the date range without filtering by `selectedSports.value`. Spatial regions (`detectRegions`) and `heatmapPathMap` are constructed across all sports.
* **Detached Screen State**: `PeriodMapScreen.kt` declares a private, detached `rememberSaveable { mutableStateOf(setOf<BSportType>()) }` defaulting to `emptySet()` (all sports), immediately discarding the athlete's header filter selection upon navigating from card to map.
* **Asymmetric Summary Filtering**: `PeriodsViewModel.filterPeriodSummary()` filters `workoutIdToPolylineMap` but fails to filter `workoutIdToAllPolylinesMap` and `workoutIdToPathMap`, causing `prepareCardMapPreviewSync()` to ingest unselected sport routes.
* **Unfiltered Corridor Normalization**: `ATT-4929` corridor frequency estimation and `ATT-4932` per-workout pixel accumulation execute over unselected paths, skewing max pass counts and color thresholds.

---

## 3. User Scope Grounding & Human Clarification (Ceremony 1)
* **Human Clarification (Ceremony 1)**: The Periods header filter (`selectedSports`) is the authoritative **Master Sport Filter**. It controls heatmap paths (card mini-map & detailed map) and region detection/chips (`Hauptregion` and workout counts). No secondary sport toggle on the map is needed.
* **In-Scope**:
  * Filter `PeriodSummary` polylines symmetrically (`polylines`, `workoutIdToPolylineMap`, `workoutIdToAllPolylinesMap`, `workoutIdToPathMap`) in `filterPeriodSummary`.
  * Pass `selectedSports` into `selectPeriodForMap()`, clustering `SpatialRegionEngine.detectRegions()` and generating `heatmapPathMap` exclusively from matching workouts.
  * Remove redundant local sport toggle row from `PeriodMapScreen.kt`, binding map rendering directly to the master filter.
  * Recompute `ATT-4929` corridor normalization and `ATT-4932` pixel counts on the sport-filtered path set.
  * Unit tests validating sport-filtered path decimation, region clustering, and map state parity.
* **Out-of-Scope Non-Goals**:
  * Adding a dedicated sport selector inside `PeriodSummaryCard` (header filter governs cards).
  * Altering SQLite persistence or schema in `PeriodSummariesDatabaseManager.kt`.
  * Modifying `ThermalColorLut.kt` gradient stops or alpha curves.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-024.1` (*Line-Density Heatmap Visuals & Adaptive Filtering*) and `REQ-PER-013` (*Spatial Region Partitioning*).
* **Historical Origin & Commit Trace**: Commit `548f3995` (`ATT-4480`) and commit `2bea16b7` (`ATT-4833`).
* **Root Reason for Existing Formulation**: `PeriodMapScreen` originally had its own sport chips during initial prototyping before the master header filter in `PeriodsTabsScreen` was established.
* **Preservation of Core Invariants**: 60 fps main-thread performance, thread-safe asynchronous tile generation on `Dispatchers.Default`, and fallback to all sports when no chips are selected remain preserved.

---

## 5. Architectural Strategy & Technical Design
1. **Symmetric DTO Filtering**: In `filterPeriodSummary()`, filter `workoutIdToAllPolylinesMap` and `workoutIdToPathMap` using `summary.workoutIdToSportMap[id] in sports`.
2. **Filtered Map State Generation**: In `selectPeriodForMap(summary, initialRegionId)`, filter `workouts` by `selectedSports.value` before calling `SpatialRegionEngine.detectRegions()` and decoding `heatmapPathMap`.
3. **UI Streamlining**: Remove the redundant local sport row from `PeriodMapScreen.kt`. The map displays filtered workouts and region chips corresponding to the header's active sport selection.
4. **Calibrated Tile Normalization**: Pass filtered paths to `estimateMaxCorridorPassCount` so corridor frequency is normalized strictly against the filtered sport collection.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Header sport chip toggle immediately updates card mini-maps and detailed map views.
  2. Region chips and `Hauptregion` display accurate workout counts strictly matching selected sports.
  3. Single-sport and multi-sport selections preserve `ATT-4932` (no beads/blobs) and `ATT-4929` (logarithmic normalization).
* **Risk Rating**: **LOW** — UI/ViewModel state unification and DTO filtering; zero database schema changes or background service alterations.
