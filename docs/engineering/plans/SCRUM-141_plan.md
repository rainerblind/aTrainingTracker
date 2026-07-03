# Implementation Plan - SCRUM-141: Clean Sensor Status Header

The `SensorStatus` header currently displays text labels below sensor icons, causing visual clutter. This plan removes the labels and optimizes the icon layout for a cleaner look.

## 1. Requirement Fulfillment
| Requirement ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-UI-048** | The `SensorStatus` header SHALL display only technical icons for active/available sensors. Descriptive text names BELOW the icons SHALL be removed to minimize vertical footprint and visual clutter. | `SensorStatus.kt` | `TST-UI-048` |

## 2. Proposed Changes

### UI Components (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/`)

#### [MODIFIED] `SensorStatus.kt`
*   Remove the `Text` component from the sensor loop.
*   Remove the `Column` wrapper for each sensor, replacing it with a simple `Box` or direct `Icon` if padding allows.
*   Update `Icon` configuration:
    *   `size = 24.dp` (increased from 20.dp).
    *   `tint` remains logic-based (onSurface for active, outline for inactive).
*   Adjust `Row` padding/arrangement to ensure icons are balanced and high-density.
*   Remove unused `context` and `sp` imports.

## 3. Impact Analysis
*   **Android System**: No impact. Pure UI modification.
*   **Component Interfaces**: No change to `SensorStatus` callback signature.
*   **Visual Consistency**: Improved vertical density in the main tracking header.

## 4. Verification Plan (TST-UI-048)
*   **Automated**: Run `app:assembleDebug` to ensure no regressions.
*   **Manual**: 
    1.  Open the Tracking screen.
    2.  Verify that no text labels are visible below the sensor icons in the header.
    3.  Verify that icons are 24dp in size and correctly tinted based on availability.
