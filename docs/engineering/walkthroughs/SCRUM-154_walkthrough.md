# Walkthrough: Selective Period Markers (SCRUM-154)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-033** | The `PeriodMapScreen` SHALL display optional and selectable markers for the Start, End, Maximum Altitude, and Maximum Line Distance. | Verified |

## 2. Verification Evidence (TST-UI-041)
*   **Procedure**:
    1. Open the Period Map for a Month or Year.
    2. Tap the **drop (Place)** icon on the floating action button.
    3. Verify that a dropdown menu appears with localized marker roles.
    4. Toggle the options and verify that markers react in real-time.
*   **Observation**:
    *   Markers are correctly filtered based on user selection.
    *   **Start** and **End** markers are deactivated by default to maintain overview clarity.
    *   The user's preference is persisted via DataStore.
*   **Result**: **PASS**

## 3. Technical Changes
### Logic Layer (PeriodsViewModel.kt)
*   Aggregated technical marker types (Start, End, Max Alt, Max Dist) by assigning them specific `PeriodMarkerType` identifiers.
*   Implemented polyline decoding in the aggregator to derive precise start/stop coordinates for every workout in the period.

### UI Layer (PeriodMapScreen.kt)
*   Consolidated map configuration into a specialized FAB menu using the **22dp Place (drop)** icon.
*   Removed technical configuration from the period list headers to reduce visual clutter and unify mapping controls.
*   Integrated the **"Tracked"** nomenclature into all technical tooltips.

### Performance
*   Optimized marker rendering by moving selection logic to a data-driven model, ensuring large periods (e.g., full year) remain responsive.
