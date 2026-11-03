# Stage 1 Analysis: ATT-5279 - [Bug] ClusterOnMapSheet: remove duplicate metadata and show detailed map on expansion

**Ticket**: [ATT-5279](https://rainerblind.atlassian.net/browse/ATT-5279)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `Sprint 2026-45.3`  
**Branch**: `feature/ATT-5279`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
During on-device review of `ATT-5196` (Sprint 2026-45.2), two defects were identified in `ClusterOnMapSheet` (`MapScreenWithTrack.kt`):
1. **Duplicate Metadata**: `ClusterOnMapSheet` renders `WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock` in `MapDetailLayout.header` *and* `WorkoutClusterDetailDashboard` in `metadataContent`. Because `MapDetailLayout` renders both inside its top header `Column`, distance, sport, PR best time, avg time, sessions, and recency are rendered twice, stacked on top of each other.
2. **Missing Detailed Map on Expansion**: Unlike `RouteOnMapScreen` and `SegmentOnMapScreen` (which set `showMap = true` in `MapDetailLayout`), `ClusterOnMapSheet` sets `showMap = false`. When expanded by dragging upward, no map appears in the lower section, leaving blank whitespace below the fold.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: In commit `ec65167f` (`ATT-5196`), `ClusterOnMapSheet` was migrated to `MapDetailLayout`, but `showMap = false` was set and `WorkoutClusterDetailDashboard` was slotted into `metadataContent`. In `MapDetailLayout.kt:612-636`, `metadataContent` is rendered inside the top header `Column`, colliding with `WorkoutClusterMetadataBlock` and measuring into peek height.
* **Independent Peer Review & Verification**:
  * Inspected live code on `feature/ATT-5279`: `MapScreenWithTrack.kt:590` sets `showMap = false` and line 618 hosts `WorkoutClusterDetailDashboard`.
  * Verified `RouteOnMapScreen.kt:164` and `SegmentOnMapScreen.kt:108` set `showMap = true` and provide `mapContent`, hiding the map below the fold when peeked (`measuredHeaderHeight + navBarHeight`) and revealing the entity map when expanded.
  * Verified `WorkoutClusterHeatmapScreen.kt:559-590` canonical cluster map layer composition (`heatmap`, `path(linkedRoute)`, `mapState.tracks`, and `markers(fingerprintMarkers)`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Remove duplicate `metadataContent = { WorkoutClusterDetailDashboard(...) }` from `ClusterOnMapSheet`.
  2. Set `showMap = true` in `MapDetailLayout`, configure `clusterBounds` (`minLat/maxLat/minLng/maxLng`), and wire `mapContent` with heatmap, linked route, tracks, and fingerprint markers.
  3. Select cluster in `clustersViewModel` via `LaunchedEffect(resolvedCluster.id)` to hydrate `mapState` and `linkedRoute`.
  4. Update `GeneralMapLayersContractTest.kt` and synchronize living docs (`REQ-UI-322` / `TST-UI-282`).
* **Out-of-Scope Non-Goals**:
  * Modifying `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterDetailDashboard.kt`, or clustering algorithms.
  * Modifying tracking cockpit map behavior (clusters remain strictly isolated per `ATT-5197`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-322` (*General Map Layer Visibility Controls & Dynamic Decluttering Menu*), Section 5.
* **Historical Origin & Commit Trace**: `ATT-4847` (Sprint 2026-44.2), `ATT-5075`/`ATT-5074` (Sprint 2026-45.1), and `ATT-5196` (`ec65167f`, Sprint 2026-45.2).
* **Root Reason for Existing Formulation**: ATT-5196 introduced `MapDetailLayout` to replace a static un-draggable column, but mistakenly set `showMap = false` and slotted `WorkoutClusterDetailDashboard` into `metadataContent`.
* **Preservation of Core Invariants**: Top drag handle (`MinimumDragHandle()`), dynamic peek height measurement (`measuredClusterHeaderHeight + navBarHeight`), and list-item visual parity (`WorkoutClusterIdentityRow` + `WorkoutClusterMetadataBlock`) remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Side-Effect & Cross-Caller Impact Audit**:
  * `ClusterOnMapSheet` is called exclusively in `MapScreenWithTrack.kt:245`. No other caller exists.
  * `clustersViewModel` is scoped to `ClusterOnMapSheet` (`viewModel()`); calling `selectCluster(resolvedCluster)` offloads map decoding to background dispatchers without side-effects on other screens.
* **High-Level Solution**:
  1. `MapScreenWithTrack.kt`: Remove `metadataContent`, set `showMap = true`, compute `clusterBounds`, and implement `mapContent` displaying heatmap, tracks, linked route, and fingerprint markers.
  2. `GeneralMapLayersContractTest.kt`: Update contract assertions to verify `showMap = true` and removal of `WorkoutClusterDetailDashboard`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero UI thread blocking; 1:1 peek/expansion parity with Routes and Segments; 100% test pass rate; Human Decision Gate on parent ticket `ATT-5279` strictly preserved.
* **Risk Rating**: **LOW**. Direct declarative cleanup and map composition following established `RouteOnMapScreen` / `SegmentOnMapScreen` patterns.
