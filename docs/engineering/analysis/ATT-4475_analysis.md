# Stage 1 Analysis: ATT-4475 - Decouple Anchor Tracks, Eliminate UI-Thread Polyline Decoding & Restore Clickability

**Ticket**: [ATT-4475](https://rainerblind.atlassian.net/browse/ATT-4475)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-4475`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation
In `InteractivePeriodMap.kt` and `PeriodsRepository.kt`, historical compromises cause duplicate rendering on small periods, severe OOM-guard bypass on large periods, and in-memory heap duplication:
1. *Accidental Double-Rendering ($\le 30$ Workouts)*: `PeriodsRepository.enrich()` populates `summary.workoutIdToPolylineMap` with *all* workouts in the period. In `InteractivePeriodMap.kt:93-100`, `anchorTracks` assumes this map only contains 5 spatial anchors, rendering all tracks at $\alpha=0.5$, while `memberTracks` (lines 138-140) renders them again at $\alpha=0.3$.
2. *OOM Guard Bypass on UI Thread ($> 30$ Workouts)*: `PeriodsViewModel` suppresses `memberTracks` above `MAX_PERIOD_VECTOR_TRACKS = 30`, but `InteractivePeriodMap` synchronously decodes all 300+ polylines (`PolyUtil.decode`) on the Android UI thread inside Compose `remember` via `anchorTracks`, instantiating 300+ Google Maps `Polyline` objects and defeating the OOM guard.
3. *4x In-Memory Polyline Duplication*: Populating full `workoutIdToPolylineMap` on every `PeriodSummary` duplicates polyline strings across Day, Week, Month, and Year in `groupedPeriods`.
4. *Loss of Clickability Above 30 Workouts*: Large periods drop all clickable vector tracks.

---

## 2. Root Cause Analysis & Independent Peer Review (ATT-3534)
* **Live Code Verification**:
  1. *Double Rendering*: Confirmed in `InteractivePeriodMap.kt:114-116` (`anchorTracks`) and `138-140` (`memberTracks`). Both iterate through full workout lists for $\le 30$ workouts.
  2. *Synchronous UI Polyline Decoding*: Confirmed in `InteractivePeriodMap.kt:93-100` (`val anchorTracks = remember(summary.workoutIdToPolylineMap) { ... PolyUtil.decode(polyline) ... }`). For large periods, 300+ decodes and `Polyline` allocations execute directly on the UI thread.
  3. *Heap Duplication*: Confirmed `PeriodsRepository.kt:1002-1042` attaches `allPolylineMap` to every period in `groupedPeriods`.
  4. *Clickability Cliff*: Confirmed in `PeriodsViewModel.kt:142-159` where `tracks = emptyList()` for `workouts.size > 30`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Scope `PeriodSummary.workoutIdToPolylineMap` strictly to true spatial anchors ($\le 5$ workouts: N, S, E, W, Longest) in `groupedPeriods`.
  * Eliminate double rendering in `InteractivePeriodMap.kt` by displaying `anchorTracks` strictly as loading placeholders (`mapState.isLoading`) or deduplicating against `memberTracks`.
  * Eliminate UI-thread polyline decoding by moving all `PolyUtil.decode` operations to `Dispatchers.Default`.
  * Enable full-period route visibility via `PolylineHeatmapTileProvider` (ATT-4480) and preserve workout clickability for large periods (> 30 workouts) via spatial tap hit-testing or simplified vector peek overlays.
* **Out-of-Scope Non-Goals**:
  * Modifying SQLite schemas in `PeriodSummaries.db` (completed in ATT-4476).
  * Modifying `PeriodSummaryCard` thumbnail rendering (completed in ATT-4477).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PER-012` (*Vector Track Safety Threshold*) and `REQ-PER-013` (*Period Map Performance*); introduces `REQ-PER-021`.
* **Historical Origin & Commit Trace**: Sprint 2026-40.16 (ATT-346 / commit `7a192b0c` for anchor framing; ATT-440 for discrete `PeriodMapState`).
* **Root Reason for Existing Formulation**: Anchors were designed for instant framing before full data loaded. When `allPolylineMap` was added to feed heatmaps, `workoutIdToPolylineMap` was overloaded without isolating anchor IDs.
* **Preservation of Core Invariants**: Instant geographic framing, zero OOMs, 60 fps map interaction, and 100% clean-room test pass rate remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Repository Anchor Isolation**: In `PeriodsRepository.enrich()`, populate `workoutIdToPolylineMap` strictly with `anchorWorkouts` ($\le 5$). Full member polylines are fetched on-demand in `PeriodsViewModel.showPeriodMap()`.
2. **Deduplicated Map Rendering**: In `InteractivePeriodMap.kt`, hide `anchorTracks` once `mapState.isLoading == false` (or deduplicate member tracks).
3. **Async Decoding & Spatial Hit-Testing**: Pre-decode and simplify all paths off the main thread in `PeriodsViewModel` (`Dispatchers.Default`). Support spatial tap hit-testing (`PolyUtil.isLocationOnPath`) to highlight and peek individual workouts even in 100+ workout periods.

---

## 6. System Invariants & Risk Assessment
* **Invariants**: Strict bounding of UI-thread allocations; zero duplicate `Polyline` rendering; 100% unit test pass rate.
* **Risk Rating**: **LOW** (UI presentation and background dispatch optimization, zero database schema mutations).
