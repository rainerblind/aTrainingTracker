# Stage 3: Implementation Plan - ATT-5196: [Verbesserung] Lieblingsstrecken map popup: drag handle, expandable details, component reuse & stats hydration

**Ticket**: [ATT-5196](https://rainerblind.atlassian.net/browse/ATT-5196)  
**Sub-task**: [ATT-5231](https://rainerblind.atlassian.net/browse/ATT-5231) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.2`  
**Requirement Mapping**: `REQ-UI-322` (`REQ-UI-322.10`, `REQ-UI-322.11`, `REQ-UI-322.12`)  
**Test Mapping**: `TST-UI-282` (`TST-UI-282.13`, `TST-UI-282.14`, `TST-UI-282.2`, `TST-UI-282.3`)  
**Branch**: `feature/ATT-5196`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
When opening the central map on cold-start, `loadAllClustersLightweight()` bypassed `_clusterStats`, leaving `cluster.stats == null` (missing best time, elevation gain, and recency) until the user visited the clusters list. Furthermore, `ClusterOnMapSheet` lacked `MinimumDragHandle()`, dynamic peek-height measurement, expandable details via `MapDetailLayout`, and list item component reuse (Design Guidelines Section 5.15 & Coding Standards Section 5.8 / Rule 68).

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-322.10`** $\rightarrow$ `TST-UI-282.13`: Companion stats hydration in `WorkoutClusterRepository.loadAllClustersLightweight()`.
* **`REQ-UI-322.11`** $\rightarrow$ `TST-UI-282.14`: `ClusterOnMapSheet` wraps `MapDetailLayout(useStatusBarsPadding = false)`, rendering `MinimumDragHandle()` and reusing `WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock`.
* **`REQ-UI-322.12`** $\rightarrow$ `TST-UI-282.14`: Dynamic peek measurement via `measuredClusterHeaderHeight` in `MapScreenWithTrack.kt`.
* **`REQ-UI-106` / `REQ-PRO-001`** $\rightarrow$ `TST-UI-282.2`, `TST-UI-282.3`: Localization parity and targeted regression test suite.

---

## 3. System Invariants & Preserved Behavior
1. **Thread Isolation**: All SQLite queries and cluster stats aggregation run strictly on `Dispatchers.IO`.
2. **Decoding Performance**: Representative polylines decode on `Dispatchers.Default` (`flowOn(defaultDispatcher)`).
3. **Subtask Self-Sufficiency**: Subtasks close via `gate-verdict PASS`; parent ticket completion reserved for human.

---

## 4. Proposed Architectural Changes
* **`WorkoutClusterRepository.kt`**: In `loadAllClustersLightweight()`, hydrate `_clusterStats.value = summariesManager.getWorkoutClusterStatsForAllClusters()` if `_clusterStats.value.isEmpty()`.
* **`MapScreenWithTrack.kt`**:
  * Collect `allClusters` flow from `MapFragmentWithTrackViewModel`.
  * Track `var measuredClusterHeaderHeight by remember(selectedClusterId) { mutableStateOf<Dp?>(null) }` and incorporate it into `sheetPeekHeight` and re-alignment `LaunchedEffect`.
  * Refactor `ClusterOnMapSheet` to host `MapDetailLayout(useStatusBarsPadding = false, onHeaderHeightMeasured = ...)` with `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock` in header, and expandable detail content in `metadataContent`.
* **UI Consistency (Rule 23 / Section 5.15)**: Reuses `SegmentOnMapScreen`/`RouteOnMapScreen` bottom sheet pattern and canonical `WorkoutClusterComponents.kt` list headers.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Repository Companion Stats Hydration
* File: `WorkoutClusterRepository.kt`
* Change: In `loadAllClustersLightweight()`, conditionally hydrate `_clusterStats` from `summariesManager`.

### Step 2: Dynamic Peek Measurement & State Wiring in `MapScreenWithTrack.kt`
* File: `MapScreenWithTrack.kt`
* Change: Collect `allClusters`, track `measuredClusterHeaderHeight`, and wire into peek height and `LaunchedEffect`.

### Step 3: Refactor `ClusterOnMapSheet`
* File: `MapScreenWithTrack.kt`
* Change: Rewrite `ClusterOnMapSheet` to embed `MapDetailLayout(useStatusBarsPadding = false)`, rendering `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock`.

### Step 4: Unit & Contract Tests
* Files: `WorkoutClusterRepositoryTest.kt`, `GeneralMapLayersContractTest.kt`
* Change: Add `TST-UI-282.13` verifying `loadAllClustersLightweight` populates `clusterStats`; update `TST-UI-282.14` contract tests.

### Step 5: Verification Suite
* Commands:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterRepositoryTest" --tests "com.atrainingtracker.trainingtracker.ui.map.*"`
  `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests covering repository and map UI, followed by `ThemeColorCentralizationAuditTest`.
* **Rollback**: Branch isolation permits clean revert via `git reset --hard HEAD` without affecting `sprint/2026-45.2`.
