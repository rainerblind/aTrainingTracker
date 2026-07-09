# Implementation Plan: Manually Change Cluster Signature Points (SCRUM-197)

## 1. Problem Statement
The automated centroids of route clusters can sometimes be skewed by GPS noise. Users need a way to manually correct the Start, End, and Max Line Distance (Apex) positions to ensure accurate future matching.

## 2. Requirement Traceability
| Requirement ID | Component | Description | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-021** | UI/Logic | Manual adjustment of cluster signature points. | TST-SET-013 |

## 3. Impact Analysis
* **Data Layer**: Standard `updateCluster` in the repository will be used.
* **UI Layer**: `FrequentPathHeatmapScreen` needs a new selection mode for picking points, similar to `ManualClusterScreen`.
* **UX**: Added an "Edit Fingerprint" action to the detail view.

## 4. Proposed Changes
### `FrequentPathsViewModel.kt`
* Add `updateClusterFingerprint(cluster, start, end, apex)` method.

### `FrequentPathHeatmapScreen.kt`
* Add an "Edit Fingerprint" action to the `TopAppBar`.
* Implement a state-based selection mode (Picking Start, Picking End, Picking Apex).
* Update `MapDetailLayout` content to show selection guidance overlays.
* Enable tapping on the map to set coordinates when in selection mode.

## 5. Verification Criteria (TST-SET-013)
1. Open a Frequent Path heatmap.
2. Tap "Edit Fingerprint" (or similar icon) in the TopAppBar.
3. Select "Edit Start".
4. Tap a new location on the map.
5. Save.
6. Verify the Green Start marker has moved to the new location.
