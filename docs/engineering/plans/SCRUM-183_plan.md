# Implementation Plan: Remove Empty Elevation Profile (SCRUM-183)

## 1. Problem Statement
In the Route Cluster heatmap view, the elevation profile is currently displayed based on the first track in the cluster. This is misleading as the view represents an aggregate of paths, and often the profile appears "empty" or irrelevant in the context of a heatmap analysis.

## 2. Requirement Traceability
| Requirement ID | Component | Description | Test ID |
|:---|:---|:---|:---|
| **REQ-UI-058** | UI | Support explicit hiding of elevation profile in `MapDetailLayout`. | TST-UI-066 |

## 3. Impact Analysis
* **UI Layer**: Modifies the core `MapDetailLayout` used across the app (Aftermath, Routes, Segments). 
* **Side Effects**: All existing callers must be checked, though a default `true` value for the new parameter will preserve existing behavior.

## 4. Proposed Changes
### `MapDetailLayout.kt`
* Add a new parameter: `showElevationProfile: Boolean = true`.
* Update the conditional rendering of the elevation profile section to use this new flag.

### `FrequentPathHeatmapScreen.kt`
* Set `showElevationProfile = false` in the `MapDetailLayout` call.
* Set `activeScrubPath = null` to completely decouple the scrubbing logic from this view.

## 5. Verification Criteria (TST-UI-066)
1. Open a "Frequent Path" heatmap.
2. Confirm that the map and the summary stats overlay are visible.
3. Confirm that NO elevation profile chart appears at the bottom.
