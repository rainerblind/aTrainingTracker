# Walkthrough: Reordering Period Map FABs (SCRUM-160)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-MAP-014** | The `PeriodMapScreen` SHALL position the Sharing FAB at the top of the overlay stack. | Verified |

## 2. Verification Evidence (TST-UI-057)
*   **Procedure**:
    1. Open the Period Map for any period.
    2. Observe the vertical stack of floating action buttons on the top-right.
*   **Observation**:
    *   The **Share** button is now the first (topmost) item in the stack.
    *   The **Marker Options** button follows below it.
    *   The **Heatmap Toggle** button is at the bottom of the stack.
*   **Result**: **PASS**

## 3. Technical Changes
### UI Layer (PeriodMapScreen.kt)
*   Reordered the components within the `Column` defining the map overlay buttons.
*   The `Share` Surface is now declared before the `Marker Options` Box and the `Heatmap` Surface.
*   Verified that the vertical spacing (12.dp) and alignment remain consistent with the professional design standard.
