# Stage 2: Requirement & Test Specification - ATT-5196: [Verbesserung] Lieblingsstrecken map popup: drag handle, expandable details, component reuse & stats hydration

**Ticket**: [ATT-5196](https://rainerblind.atlassian.net/browse/ATT-5196)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.2`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Spec ID**: `TST-UI-282`  
**Branch**: `feature/ATT-5196`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-322)
### 1.1 Problem Statement & Rationale
When opening the central map on cold start, `loadAllClustersLightweight()` bypassed `_clusterStats` aggregation, leaving `cluster.stats == null` (hiding best time, elevation gain, and relative recency). Furthermore, `ClusterOnMapSheet` was a standalone un-draggable `Column` lacking `MinimumDragHandle()`, dynamic peek measurement, expandable details (`MapDetailLayout`), and list item component reuse (Section 5.15 & Section 5.8 / Rule 68).

### 1.2 Functional & Architectural Requirements
* `REQ-UI-322.10` (Companion Stats Hydration): In `WorkoutClusterRepository.loadAllClustersLightweight()`, if `_clusterStats.value.isEmpty()`, the system SHALL asynchronously populate `_clusterStats.value = summariesManager.getWorkoutClusterStatsForAllClusters()`.
* `REQ-UI-322.11` (Component Reuse & MapDetailLayout): `ClusterOnMapSheet` SHALL wrap `MapDetailLayout(useStatusBarsPadding = false)`, rendering `MinimumDragHandle()` at the top, reusing `WorkoutClusterIdentityRow` and `WorkoutClusterMetadataBlock` in the header, and exposing full cluster details and workout heatmap when expanded.
* `REQ-UI-322.12` (Dynamic Peek Height Measurement): `MapScreenWithTrack.kt` SHALL dynamically measure `measuredClusterHeaderHeight` via `onHeaderHeightMeasured` to set the sheet peek height flush above navigation bars.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Companion Stats Hydration)**:
  * *Given* cold app start with unpopulated `_clusterStats`,
  * *When* `loadAllClustersLightweight()` executes,
  * *Then* `_clusterStats.value` is populated from `summariesManager`, and `MapCluster.stats` contains valid metric summaries.
* **Criterion 2 (Bottom Sheet Parity & Drag Handle)**:
  * *Given* the athlete viewing `MapScreenWithTrack` and tapping a cluster polyline,
  * *When* `ClusterOnMapSheet` is displayed,
  * *Then* `MinimumDragHandle()` is visible at the top, list identity and metadata blocks match list items 1:1, and dragging up reveals full details.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Sections 4 & 5.
* `Historical Origin & Commit Trace`: Tickets `ATT-4847` (Sprint 2026-44.2), `ATT-5075` & `ATT-5074` (Sprint 2026-45.1), `ATT-5196` (Sprint 2026-45.2).
* `Root Reason for Existing Formulation`: Fast cluster loading in ATT-5074 omitted `clusterStats` aggregation, leaving stats null on cold map opens. ATT-5075 drafted a static Column without `MapDetailLayout`.
* `Preservation of Core Invariants`: Off-main-thread lightweight loading on `Dispatchers.IO` is preserved; polyline decoding remains on `Dispatchers.Default`; Material 3 color tokens and non-purple styling strictly maintained.

### 1.5 System Invariants
* Zero UI main-thread blocking; all DB and aggregation queries run on `Dispatchers.IO`.
* Exact 1:1 visual match with list items in `WorkoutClusterComponents.kt`.
* Human Decision Gate on parent ticket `ATT-5196` strictly enforced (`Rule 3`).

---
## 2. Test Specification (TST-UI-282)
### Test Case 1: Companion Stats Hydration in `loadAllClustersLightweight` (`TST-UI-282.13`)
* **Scope**: Repository Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepositoryTest.kt`
* **Preconditions**: `_clusterStats` is empty.
* **Action**: Invoke `repo.loadAllClustersLightweight()`.
* **Expected Result**: `repo.clusterStats.value` is populated with stats from `summariesManager`.

### Test Case 2: Cluster Sheet Structure & Component Reuse Contract (`TST-UI-282.14`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayersContractTest.kt`
* **Action**: Assert `ClusterOnMapSheet` uses `MapDetailLayout`, `WorkoutClusterIdentityRow`, `WorkoutClusterMetadataBlock`, and `MinimumDragHandle`.
* **Expected Result**: Contract passes with 0 violations.

### Test Case 3: 9-Language Localization Audit (`TST-UI-282.2`)
* **Target**: `GeneralMapLayersLocalizationTest.kt`
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 4: Targeted Unit Test Suite (`TST-UI-282.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterRepositoryTest" --tests "com.atrainingtracker.trainingtracker.ui.map.*"`
* **Expected Result**: 100% pass rate with zero regressions.

---
## 3. Traceability Matrix

| Test Case | Scope | Method / Test Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-282.13` | Unit | `loadAllClustersLightweight_hydratesClusterStatsWhenEmpty` | `REQ-UI-322.10` | Specified |
| `TST-UI-282.14` | Contract | `ClusterOnMapSheet_usesMapDetailLayoutAndCanonicalComponents` | `REQ-UI-322.11`, `REQ-UI-322.12` | Specified |
| `TST-UI-282.2` | Localization | `GeneralMapLayersLocalizationTest` | `REQ-UI-106`, `REQ-UI-322` | Specified |
| `TST-UI-282.3` | Regression | Targeted Test Suite | `REQ-PRO-001` | Specified |
