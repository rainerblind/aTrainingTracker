# Stage 2: Requirement & Test Specification - ATT-5279: [Bug] ClusterOnMapSheet: remove duplicate metadata and show detailed map on expansion

**Ticket**: [ATT-5279](https://rainerblind.atlassian.net/browse/ATT-5279)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.3`  
**Requirement Mapping**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*)  
**Test Spec ID**: `TST-UI-282`  
**Branch**: `feature/ATT-5279`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-322)
### 1.1 Problem Statement & Rationale
`ClusterOnMapSheet` (`MapScreenWithTrack.kt`) stacked redundant statistics by hosting `WorkoutClusterDetailDashboard` inside `MapDetailLayout.metadataContent` alongside `WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock` in `header`. Furthermore, it set `showMap = false`, leaving the bottom sheet empty when expanded instead of showing the detailed cluster map (Section 5.15 / Rule 68).

### 1.2 Functional & Architectural Requirements
* `REQ-UI-322.13` (Cluster Header vs Metadata Deduplication): In `ClusterOnMapSheet` (`MapScreenWithTrack.kt`), `MapDetailLayout` SHALL render cluster header information strictly in the `header` slot (`WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock`) and SHALL NOT render duplicate cluster summary statistics in `metadataContent` (omitting `WorkoutClusterDetailDashboard`).
* `REQ-UI-322.14` (Expanded Cluster Detailed Map Parity): In `ClusterOnMapSheet`, `MapDetailLayout` SHALL set `showMap = true` and `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS` (bounding to cluster `minLat/maxLat/minLng/maxLng` or `latLngs`), rendering the cluster's detailed entity map in `mapContent` (including cluster heatmap, linked route if present, workout member tracks, and fingerprint markers) when dragged upward into the expanded state, matching `RouteOnMapScreen` and `SegmentOnMapScreen`.
* `REQ-UI-322.15` (Peek State Map Concealment): When peeked (`PartiallyExpanded`), only the header slot (`WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock`) SHALL be visible above the navigation bar (`measuredClusterHeaderHeight + navBarHeight`), keeping the detailed map concealed below the fold until the sheet is dragged upward.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Deduplicated Header Presentation)**:
  * *Given* the athlete taps a favorite route cluster polyline on `MapScreenWithTrack`,
  * *When* `ClusterOnMapSheet` is displayed,
  * *Then* cluster identity and metrics are rendered once in the header slot, with zero duplicate dashboard statistics stacked in `metadataContent`.
* **Criterion 2 (Detailed Entity Map on Expansion)**:
  * *Given* `ClusterOnMapSheet` in peeked state,
  * *When* dragged upward to expanded state,
  * *Then* the lower section displays the detailed cluster map (`showMap = true`) with heatmap, tracks, linked route, and fingerprint markers.

### 1.4 Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Section 4.
* `Historical Origin & Commit Trace`: Tickets `ATT-4847` (Sprint 2026-44.2), `ATT-5075`/`ATT-5074` (Sprint 2026-45.1), `ATT-5197`/`ATT-5196` (Sprint 2026-45.2), and `ATT-5279` (Sprint 2026-45.3).
* `Root Reason for Existing Formulation`: In ATT-5196, `ClusterOnMapSheet` was migrated to `MapDetailLayout`, but `showMap = false` was set and `WorkoutClusterDetailDashboard` was slotted into `metadataContent`. In `MapDetailLayout.kt`, `metadataContent` is rendered inside the top header Column, duplicating metrics already shown in `WorkoutClusterMetadataBlock`. Furthermore, setting `showMap = false` left the bottom sheet empty when expanded. Removing redundant `metadataContent` and setting `showMap = true` restores 1:1 parity with `RouteOnMapScreen` and `SegmentOnMapScreen` (Section 5.15 / Rule 68).
* `Preservation of Core Invariants`: List component visual identity (`WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock`), `MinimumDragHandle()`, dynamic peek height measurement flush to navigation bar, off-main decoding, and non-purple styling are strictly maintained.

### 1.5 System Invariants
* Zero UI thread blocking during cluster state hydration or map rendering.
* Strict 1:1 peek/expansion parity with `RouteOnMapScreen` and `SegmentOnMapScreen`.
* Human Decision Gate on parent ticket `ATT-5279` strictly enforced (`Rule 3`).

---

## 2. Test Specification (TST-UI-282)
### Test Case 1: Cluster Sheet Contract & Map Expansion Parity (`TST-UI-282.15`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/GeneralMapLayersContractTest.kt`
* **Preconditions**: `MapScreenWithTrack.kt` contains `ClusterOnMapSheet`.
* **Action**: Assert `ClusterOnMapSheet` sets `showMap = true`, uses `MapZoomFocus.EXPLICIT_BOUNDS`, provides `mapContent`, and omits `WorkoutClusterDetailDashboard`.
* **Expected Result**: Contract test passes with 0 violations.

### Test Case 2: 9-Language Localization Audit (`TST-UI-282.2`)
* **Target**: `GeneralMapLayersLocalizationTest.kt` & `TranslationParityTest.kt`
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 3: Targeted Unit Test Suite (`TST-UI-282.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Test Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-282.15` | Contract | `GeneralMapLayersContractTest.testClusterOnMapSheet_deduplicatesMetadataAndEnablesExpandedMap_TST_UI_282_15` | `REQ-UI-322.13`, `REQ-UI-322.14`, `REQ-UI-322.15` | Specified |
| `TST-UI-282.2` | Localization | `GeneralMapLayersLocalizationTest` | `REQ-UI-106`, `REQ-UI-322` | Specified |
| `TST-UI-282.3` | Regression | Targeted Test Suite | `REQ-PRO-001` | Specified |
