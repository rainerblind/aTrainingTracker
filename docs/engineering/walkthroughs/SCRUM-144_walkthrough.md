# Walkthrough: Sensor Source Redundancy Chain (SCRUM-144)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-049** | Tapping a sensor icon in the `SensorStatus` header SHALL display a dialog showing current telemetry and the sensor redundancy chain grouped into: Source Device (active), Active Backups, and Not connected devices. | Verified |

## 2. Verification Evidence (TST-UI-049)
*   **Procedure**:
    1. Paired three Heart Rate straps (HR1, HR2, HR3).
    2. Connected HR1 and HR2.
    3. Tapped the active HR icon in the tracking header.
*   **Observation**:
    *   **Section 1: Source Device**: Correctly identified HR1 (primary) with its live value and unit (e.g., "145 bpm").
    *   **Section 2: Active Backups**: Listed HR2 as connected with its own real-time value.
    *   **Section 3: Not connected devices**: Listed HR3 as paired but inactive.
    *   **Layout**: Each entry used the unified 3-row identity block.
*   **Result**: **PASS**

## 3. Technical Changes
### SensorSourceDialog.kt
*   Implemented complex grouping logic to categorize paired hardware into **Source**, **Backups**, and **Not Connected**.
*   Integrated live values with localized units directly into the `DeviceIdentityBlock` for active devices.
*   Added a scrollable container to handle large numbers of potential sensor sources.
*   Ensured internal fallbacks (Smartphone Sensors) are only shown when no external hardware is available.

### DeviceType.java
*   Added missing technical mappings for `CLOCK` and `VERTICAL_SPEED_AND_SLOPE` to ensure all dashboard icons have a source audit path.
