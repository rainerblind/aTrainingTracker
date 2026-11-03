# Stage 1 Analysis: ATT-5196 - [Verbesserung] Lieblingsstrecken map popup: drag handle, expandable details, component reuse & stats hydration

**Ticket**: [ATT-5196](https://rainerblind.atlassian.net/browse/ATT-5196)  
**Sub-task**: [ATT-5229](https://rainerblind.atlassian.net/browse/ATT-5229) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.2`  
**Branch**: `feature/ATT-5196`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
In Sprint 2026-45.1 (`ATT-5075`, `ATT-5074`), two defects were identified on the central map's *Lieblingsstrecken* sheet:
1. When opening the map before the clusters list screen, `loadAllClustersLightweight()` left `_clusterStats` empty (`stats == null`), hiding best time, elevation gain, and relative recency.
2. `ClusterOnMapSheet` was implemented as a standalone static `Column` with a fixed `PeekHeightWorkout`, lacking the top drag handle bar (`MinimumDragHandle()`), dynamic header height measurement, and expandable detail view (`MapDetailLayout`). It also duplicated metadata formatting rather than reusing canonical `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock` from `WorkoutClusterComponents.kt` (violating Design Guidelines Section 5.15 and Rule 68).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Lightweight Hydration Gap**: `WorkoutClusterRepository.loadAllClustersLightweight()` queries `RouteClusters` but bypasses `summariesManager.getWorkoutClusterStatsForAllClusters()`. Downstream `MapCluster.stats` is `null` until the user opens `WorkoutClustersList`.
* **Map Bottom Sheet Inconsistency**: `SegmentOnMapScreen` and `RouteOnMapScreen` reuse domain list headers inside `MapDetailLayout(useStatusBarsPadding = false, onHeaderHeightMeasured = ...)`. `ClusterOnMapSheet` instead implemented an un-draggable custom `Column` with white space on drag.
* **Independent Verification**: Confirmed `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock` in `WorkoutClusterComponents.kt` already format all required fields (start location, sport type, equipment, hit count, best time, distance). Wiring them into `MapDetailLayout` with `onHeaderHeightMeasured` brings 100% architectural parity across map sheets.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Hydrate `_clusterStats` in `WorkoutClusterRepository.loadAllClustersLightweight()` if empty.
  2. Refactor `ClusterOnMapSheet` to use `MapDetailLayout(useStatusBarsPadding = false, onHeaderHeightMeasured = ...)`, exposing `MinimumDragHandle()` and full expandable content.
  3. Reuse `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock` in the sheet header.
  4. Dynamically measure `measuredClusterHeaderHeight` in `MapScreenWithTrack.kt` for flush bottom peek alignment.
* **Out-of-Scope Non-Goals**:
  * Modifying clustering algorithms, fingerprint spatial calculations, or database schemas.
  * Altering tracking cockpit map behavior (which was cleanly decoupled in ATT-5197).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Sections 4 & 5.
* **Historical Origin & Commit Trace**: `ATT-4847` (Sprint 2026-44.2), `ATT-5075` & `ATT-5074` (Sprint 2026-45.1).
* **Root Reason for Existing Formulation**: ATT-5074 decoupled cluster loading to prevent UI stalls, but omitted stats hydration. ATT-5075 implemented an initial sheet without `MapDetailLayout`.
* **Preservation of Core Invariants**: Off-main thread decoding and lightweight loading performance are strictly maintained; `_clusterStats` is hydrated on `Dispatchers.IO` only when empty.

---

## 5. Architectural Strategy & High-Level Solution
* **`WorkoutClusterRepository.kt`**: In `loadAllClustersLightweight()`, call `_clusterStats.value = summariesManager.getWorkoutClusterStatsForAllClusters()` if `_clusterStats.value.isEmpty()`.
* **`MapScreenWithTrack.kt`**: Collect `allClusters`, add `measuredClusterHeaderHeight`, integrate into `sheetPeekHeight`, and update `ClusterOnMapSheet` to wrap `MapDetailLayout` with `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock`.
* **Testing**: Update `WorkoutClusterRepositoryTest` and `GeneralMapLayersContractTest`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero UI main-thread blocking; exact 1:1 look match with list items; backward compatibility with existing map sheets; Human Decision Gate on parent ticket ATT-5196 preserved.
* **Risk Rating**: **LOW**. Standard UI component consolidation and companion state flow hydration adhering to established Section 5.15 patterns.
