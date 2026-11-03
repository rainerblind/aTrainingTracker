# Stage 2: Requirement & Test Specification - ATT-5075: Harmonize Lieblingsstrecken Map Popup

**Ticket**: [ATT-5075](https://rainerblind.atlassian.net/browse/ATT-5075)  
**Sub-task**: [Req & Test Spec]  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Spec ID**: `TST-UI-282`  
**Branch**: `feature/ATT-5075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (REQ-UI-322)

### 1.1 Problem Statement & Rationale
`ClusterOnMapSheet` in `MapScreenWithTrack.kt` rendered an ad-hoc layout with purple/lila (`TTColor.Cluster`) text and icon tinting, lacking cluster statistics (sport subtitle, elevation, PR trophy/best time, relative recency) present in `WorkoutClusterComponents.kt`. The system SHALL modernize `ClusterOnMapSheet` to match the 3-row layout and tokens of list items, eliminate purple text styling, and enrich `MapCluster` with `WorkoutClusterStats`.

### 1.2 Functional & Architectural Requirements
1. `MapCluster` SHALL declare `val stats: WorkoutClusterStats? = null` in `MapModels.kt`.
2. `MapFragmentWithTrackViewModel` SHALL combine `allClusters` and `clusterStats` in `mapClustersFlow`, mapping stats into `MapCluster`.
3. `ClusterOnMapSheet` SHALL display 3 rows:
   - Row 1: 32dp sport icon (`Color.Unspecified`), cluster title (`onSurface`), and sport subtitle (`onSurfaceVariant`).
   - Row 2: Distance, avg elevation gain (when $> 0$), and PR best time (when present, `TTColor.Gold` trophy).
   - Row 3: Recording count in `primary` (bold), bullet separator, and localized relative recency text.
4. `ClusterOnMapSheet` SHALL NOT tint text or sport/metric icons with `TTColor.Cluster`.
5. Map canvas polyline stroke color SHALL strictly remain `TTColor.Cluster` with zIndex `12f`.
6. Sheet peek height for selected cluster SHALL use `BottomSheetDesign.PeekHeightWorkout + navBarHeight` (~140dp).

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* Original Requirement ID & Target: Refines `REQ-UI-322.4` and `TST-UI-282.5` under `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Epic `ATT-176`.
* Historical Origin & Commit Trace: Ticket `ATT-4847` (commit `4d3f24f2`, Sprint 2026-44.2).
* Root Reason for Existing Formulation: In ATT-4847, `ClusterOnMapSheet` was quickly composed with purple tint to mirror the polyline layer color, omitting vital cluster statistics and breaking design system color standards.
* Preservation of Core Invariants: Polyline canvas stroke color (`TTColor.Cluster`), zIndex (`12f`), layer visibility controls, and referential coordinate memoization (`REQ-MAP-051`) remain 100% intact.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* the athlete viewing `MapScreenWithTrack`,
  * *When* tapping a cluster path on the map,
  * *Then* `ClusterOnMapSheet` displays 32dp sport icon (`Color.Unspecified`), title, sport subtitle, metrics (distance, elevation, PR trophy), and recency without purple text tinting.
* **Criterion 2**:
  * *Given* an active cluster selection,
  * *When* sheet is peeked,
  * *Then* peek height is calibrated to `BottomSheetDesign.PeekHeightWorkout + navBarHeight`.

### 1.5 System Invariants
Zero regression in route, segment, or location sheets; referential coordinate memoization (`REQ-MAP-051`); and 100% test pass rate.

---

## 2. Test Specification (TST-UI-282)

### Test Case 1: `GeneralMapLayersContractTest.testClusterOnMapSheet_harmonizedLayoutAndColorTokens` (TST-UI-282.5)
* **Scope**: Contract Test (`GeneralMapLayersContractTest.kt`)
* **Expected Result**: Verifies 32dp sport icon with `Color.Unspecified`, absence of `tint = TTColor.Cluster` on text/icons, and peek height `PeekHeightWorkout`.

### Test Case 2: `MapModelsTest.testMapCluster_statsEnrichmentAndReferentialMemoization` (TST-UI-282.6)
* **Scope**: Unit Test (`MapModelsTest.kt`, `MapFragmentWithTrackViewModelTest.kt`)
* **Expected Result**: Verifies `MapCluster.stats` propagation from repository and referential equality of `latLngs`.

### Test Case 3: 9-Language Localization & Specifier Audit (TST-UI-282.7)
* **Scope**: Localization Parity Test (`GeneralMapLayersLocalizationTest.kt`)
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all cluster strings.

### Test Case 4: Clean-Room Regression Suite (TST-UI-282.8)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% test pass rate across all unit test suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-282.5` | Contract | `ClusterOnMapSheet` UI tokens & peek height | `REQ-UI-322.4` | Specified |
| `TST-UI-282.6` | Unit | `MapCluster` stats & `mapClustersFlow` | `REQ-UI-322.2` | Specified |
| `TST-UI-282.7` | Localization | `GeneralMapLayersLocalizationTest` | `REQ-UI-322.6`, `REQ-UI-106` | Specified |
| `TST-UI-282.8` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
