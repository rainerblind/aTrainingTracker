# Walkthrough: Sensor Source Dialog (SCRUM-140)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-049** | Tapping a sensor icon in the `SensorStatus` header SHALL display a dialog showing the source device (name, technical identity), current value with units, and battery status. | Verified |

## 2. Verification Evidence (TST-UI-049)
*   **Procedure**: Started a session with an ANT+ HR strap. Tapped the HR icon in the header.
*   **Observation**:
    *   **Header**: Single row containing a blue HR icon and the text "Heart Rate".
    *   **Current Value**: Displayed as "145 bpm" (Value + Unit) using high-visibility typography.
    *   **Source Device**: Displayed with the technical device type logo, device name, and manufacturer. The layout mirrors the professional `DeviceItem` cockpit.
    *   **Battery Status**: Rendered via `DeviceStatusRow`, showing the battery percentage and "Available" status.
*   **Result**: **PASS**

## 3. Technical Changes
### SensorType.java
*   Centralized the icon mapping by adding `getIconResId()`. This ensures visual consistency across the entire application (Status header, Dialogs, etc.).

### SensorSourceDialog.kt
*   **Unified Header**: Combined the sensor icon and full name into a single horizontal `Row` within the `title` slot.
*   **Technical Value**: Updated the display to append correct units derived from `MyHelper.getUnitsId()`.
*   **Source Identity**:
    *   Replaced the generic protocol logo with the specific `deviceTypeIconRes`.
    *   Implemented a high-density vertical layout for Name and Manufacturer.
    *   Integrated `DeviceStatusRow` for a consistent battery and connection state representation matching `DeviceItem`.

### SensorStatus.kt
*   Refactored the `sensorDefinitions` to reuse the new `SensorType.getIconResId()` method.
*   Enabled icon clickability only for active sensors to trigger the source audit.

### BANALService Layer
*   Exposed underlying hardware source device IDs and battery percentages to the repository update loop.
