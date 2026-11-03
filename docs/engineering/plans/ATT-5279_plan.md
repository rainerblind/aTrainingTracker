# Stage 3: Implementation Plan - ATT-5279: [Bug] ClusterOnMapSheet: remove duplicate metadata and show detailed map on expansion

**Ticket**: [ATT-5279](https://rainerblind.atlassian.net/browse/ATT-5279)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.3`  
**Requirement Mapping**: `REQ-UI-322.13`, `REQ-UI-322.14`, `REQ-UI-322.15`  
**Test Mapping**: `TST-UI-282.15`, `TST-UI-282.2`, `TST-UI-282.3`  
**Branch**: `feature/ATT-5279`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
In `ClusterOnMapSheet` (`MapScreenWithTrack.kt`), duplicate cluster statistics are rendered stacked vertically because both `WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock` (in `header`) and `WorkoutClusterDetailDashboard` (in `metadataContent`) are hosted in `MapDetailLayout`, whose top header `Column` contains both. In addition, `showMap = false` leaves the sheet blank below the fold when expanded. This plan removes the redundant `metadataContent` slot and enables `showMap = true` with detailed cluster map layers matching `RouteOnMapScreen` and `SegmentOnMapScreen` (Section 5.15 / Rule 68).

---

## 2. Traceability & Requirements Mapping
* **REQ-UI-322.13**: Deduplicate header and excise `WorkoutClusterDetailDashboard` from `metadataContent`.
* **REQ-UI-322.14**: Enable `showMap = true` on `MapDetailLayout` with `EXPLICIT_BOUNDS` and cluster `mapContent` (heatmap, linked route, member tracks, fingerprint markers).
* **REQ-UI-322.15**: Maintain peek state concealment with header flush above navigation bar (`measuredClusterHeaderHeight + navBarHeight`).
* **TST-UI-282.15**: Contract test in `GeneralMapLayersContractTest.kt` verifying slot usage, map expansion, and duplicate metadata removal.

---

## 3. System Invariants & Preserved Behavior
1. **Visual Parity**: Reused canonical `WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock` header formatting is 100% preserved.
2. **Concealment in Peek**: Detailed map remains hidden below the fold when peeked; only header is visible.
3. **Decoupled Live Tracking**: Live tracking cockpit remains strictly isolated from clusters (`ATT-5197`).
4. **Code Lock & Human Gate**: Stage 4 production edits locked until Gate 3 passes; parent terminal progression strictly reserved for human.

---

## 4. Proposed Architectural Changes
### Component 1: `MapScreenWithTrack.kt` (`ClusterOnMapSheet`)
* Collect `mapState`, `linkedRoute`, and `enabledMarkerTypes` from `clustersViewModel`.
* Trigger `LaunchedEffect(resolvedCluster.id)` calling `clustersViewModel.selectCluster(resolvedCluster)`.
* Calculate `clusterBounds` (`minLat/maxLat/minLng/maxLng` or coordinate bounding) and pass to `initialBounds`.
* Build `fingerprintMarkers` (Start, End, Distance Apex, Min/Max Altitude) respecting `enabledMarkerTypes`.
* Set `showMap = true`, `zoomFocus = if (clusterBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY`.
* Remove `metadataContent` entirely; wire `mapContent` with heatmap, linked route, tracks, and markers.

### UI Consistency (Rule 23)
* **Reference screen**: `RouteOnMapScreen` and `SegmentOnMapScreen` in `MapDetailLayout.kt`, and `WorkoutClusterHeatmapScreen.kt`.
* **Reused components**: `MapDetailLayout`, `WorkoutClusterIdentityRow`, `WorkoutClusterMetadataBlock`, `MinimumDragHandle()`.
* **Theme tokens**: `MaterialTheme.colorScheme.surface`, `onSurface`, `primary`, `TTColor.StartPoint`, `TTColor.EndPoint`, `TTColor.ApexPoint`.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: `MapScreenWithTrack.kt` Refactoring
* Remove `metadataContent = { WorkoutClusterDetailDashboard(...) }`.
* Add `showMap = true`, compute `clusterBounds`, wire `clustersViewModel.selectCluster(resolvedCluster)`, and implement `mapContent`.

### Step 2: Contract Test Updates (`GeneralMapLayersContractTest.kt`)
* Update `testClusterOnMapSheet_harmonizedLayoutAndColorTokens_TST_UI_282_5` to assert `showMap = true`, `EXPLICIT_BOUNDS`, `mapContent`, and absence of `WorkoutClusterDetailDashboard`.

### Step 3: Targeted Verification & Governance Checks
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Governance: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit/contract test suite and verify 0 regressions.
* **Rollback**: Clean branch isolation on `feature/ATT-5279` allows clean discard via `git checkout .`.
