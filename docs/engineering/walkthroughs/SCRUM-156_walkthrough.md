# Walkthrough: LED and Battery Icon Alignment (SCRUM-156)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-050** | Technical status LED SHALL be vertically aligned with the battery icon by sharing a common 18dp horizontal center axis. | Verified |

## 2. Verification Evidence (TST-UI-050)
*   **Procedure**:
    1. Open `EditDeviceDialog`.
    2. Open `SensorSourceDialog`.
    3. View a device in the `DeviceListScreen`.
*   **Observation**:
    *   In all three views, the green/grey LED dot is perfectly centered above/below the battery icon.
    *   The alignment is achieved by wrapping the LED in a fixed 18dp width container matching the battery icon size.
*   **Result**: **PASS**

## 3. Technical Changes
### Sensor Identity Alignment
*   Modified `EditDeviceDialog.kt`, `SensorSourceDialog.kt`, and `DeviceItem.kt`.
*   Wrapped the technical status LED `Surface` in a `Box` with a fixed size of `18.dp`.
*   Set `contentAlignment = Alignment.Center` for the box.
*   This ensures the LED (10dp or 12dp) is centered on the same 18dp axis as the `DeviceStatusRow` icons.
