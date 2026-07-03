# Walkthrough: Live Segment Sheet Height Constraint (SCRUM-143)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-047** | The Live Segment bottom sheet SHALL NOT expand to the top of the screen; its maximum height SHALL be limited to display the header and the elevation profile only. | Verified |

## 2. Verification Evidence (TST-UI-047)
*   **Procedure**: Started tracking with a live segment. Swiped up on the bottom sheet.
*   **Observation**: The sheet expands to show the full header and the elevation profile, but stops there. It does not continue to the top of the screen or reach the status bar.
*   **Result**: **PASS**

## 3. Technical Changes
### MapDetailLayout.kt
*   Implemented conditional layout logic:
    *   If `showMap` is **true** (Standalone screens), the layout uses `Modifier.fillMaxSize()`.
    *   If `showMap` is **false** (Live Segment Popup), the layout uses `Modifier.wrapContentHeight()`.
*   This approach centralizes height management and eliminates the need for redundant modifier overrides in caller files.

### Feature Screens
*   Reverted explicit `fillMaxSize()` and `wrapContentHeight()` overrides in `SegmentOnMapScreen.kt`, `RouteOnMapScreen.kt`, `TrackOnMapScreen.kt`, and `LiveSegmentSheet.kt`.
*   All features now rely on the automated technical logic within `MapDetailLayout`.
