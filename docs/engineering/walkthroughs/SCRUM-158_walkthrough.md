# Walkthrough: Location Accuracy Auditing (SCRUM-158)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-053** | The tracking header SHALL use `SensorType.ACCURACY` as the primary location indicator. Tapping this icon SHALL display the `SensorSourceDialog` focused on technical accuracy. | Verified |

## 2. Verification Evidence (TST-UI-054)
*   **Procedure**:
    1. Started a session with GPS active.
    2. Tapped the location pin icon in the header.
*   **Observation**:
    *   The dialog title correctly shows "Accuracy" (or localized equivalent).
    *   The primary value displays the current precision in meters (e.g., "3.5 m").
    *   The location providers (GPS, Fused, Network) are correctly identified as source candidates.
*   **Result**: **PASS**

## 3. Technical Changes
### SensorType.java
*   Updated `getIconResId()` to map `SensorType.ACCURACY` to `R.drawable.ic_location`. This ensures the location pin is used to represent the accuracy data stream.

### SensorStatus.kt
*   Replaced `SensorType.LONGITUDE` with `SensorType.ACCURACY` in the `sensorDefinitions` list.
*   This naturally shifts the tracking header's location context from raw coordinates to technical quality metrics.
