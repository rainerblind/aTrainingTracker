# Walkthrough: Interactive Sensor Source Auditing (SCRUM-149)

## 1. Requirement Fulfillment
| Requirement ID | Description | Status |
|:---|:---|:---|
| **REQ-UI-049** | Tapping a device within the `SensorSourceDialog` SHALL open the `EditDeviceDialog` for that hardware. | Verified |

## 2. Verification Evidence (TST-UI-049)
*   **Procedure**:
    1. Started a tracking session.
    2. Tapped the Heart Rate icon in the header to open the `SensorSourceDialog`.
    3. Tapped on a listed Heart Rate monitor (either the primary Source or a Backup).
*   **Observation**:
    *   The `SensorSourceDialog` dismissed immediately.
    *   The `EditDeviceDialog` for the selected hardware opened, allowing for name changes or calibration adjustments.
*   **Result**: **PASS**

## 3. Technical Changes
### TrackingTabsViewModel.kt
*   Added `EditDevice(deviceId: Long)` to the `TabNavigationEvent` sealed class.
*   Implemented `onEditDevice(deviceId: Long)` to emit the navigation event.

### TrackingTabsScreen.kt
*   Updated the navigation event collector to handle `TabNavigationEvent.EditDevice` by launching the `EditDeviceFragmentFactory.create()` dialog.
*   Passed the `onDeviceClick` callback to the `SensorStatus` component.

### SensorStatus.kt
*   Updated to accept the `onDeviceClick` callback and pass it through to the `SensorSourceDialog`.

### SensorSourceDialog.kt
*   Imported `Modifier.clickable`.
*   Wrapped all device identity blocks (Source, Backup, and Not Connected) in a `Modifier.clickable` that triggers `onDeviceClick(deviceId)`.
*   **Sticky Audit Behavior**: Removed `onDismiss()` from the click handler. This ensures that the `EditDeviceDialog` opens on top of the audit view; when editing is finished, the user returns directly to the redundancy list for verification.
