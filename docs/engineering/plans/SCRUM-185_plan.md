# Implementation Plan: Unified Spatial Markers (SCRUM-185)

## 1. Problem Statement
Spatial markers (Start, End, Apex) are inconsistently styled across different modules. Some use the `ic_location` icon with varying colors, while others use the specialized `createSensorMarker` style.

## 2. Requirement Mapping
| Requirement ID | Component | Test ID | Description |
|:---|:---|:---|:---|
| **REQ-SET-041** | `MapUtils`, `InteractivePeriodMap`, `WorkoutClusterHeatmapScreen`, `TrackOnMapScreen`, `RouteItem` | **TST-SET-031** | All geographical views SHALL use standardized technical markers. |

## 3. Impact Analysis
*   **Component: UI Layer**:
    *   Widespread changes in marker definition across 4 screens.
    *   No change to background logic or database.
*   **Visual Integrity**: Ensures that Start is always Green (`control_start`) and End is always Red (`control_stop`) with the unified pin base.

## 4. Proposed Changes

### `WorkoutClusterHeatmapScreen.kt`
*   Replace `R.drawable.ic_location` with `R.drawable.control_start` for `editStart`.
*   Replace `R.drawable.ic_location` with `R.drawable.control_stop` for `editEnd`.

### `InteractivePeriodMap.kt`
*   Update `LocationMarker` creation for Start/End points to use `createSensorMarker` with the correct color/icon pairs.

### `TrackOnMapScreen.kt`
*   Standardize the Start/End markers using the unified style.

### `RouteItem.kt`
*   Update the mini-map preview to use the unified style for its Start/End markers.

## 5. Verification Plan
### Manual Verification (TST-SET-031)
1.  Open **Favorite Tracks** Heatmap: Verify Green/Start and Red/Stop markers.
2.  Open **Yearly Period Map**: Verify the same markers appear for all workout cloud points.
3.  Open **Workout Details**: Verify the single track map uses the same markers.
4.  Open **Routes List**: Verify mini-maps use the unified markers.
