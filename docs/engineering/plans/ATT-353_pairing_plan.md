# Implementation Plan - ATT-353: Internal Sensor Pairing Control

Implement manual "Pairing" support for the internal barometric pressure sensor, allowing users to activate or deactivate the hardware via the "My Sensors" list.

## User Review Required

> [!IMPORTANT]
> - **User Control**: You can now explicitly turn the "Altitude from Pressure" sensor ON or OFF using the standard toggle switch in the "My Sensors" list.
> - **Privacy & Choice**: If you prefer GPS-based altitude or simply want to disable internal hardware telemetry, the app will now strictly respect your "Paired" preference for the pressure sensor.
> - **Mirroring Hardware**: This brings the internal pressure sensor into parity with your external ANT+ and BLE heart rate or speed sensors.

## Proposed Changes

### 1. Management Layer: Preference-Aware Initialization
Fulfills REQ-CON-010 | Test: TST-BUG-009

#### [MODIFY] [DeviceManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java)
- **Selective Creation**: In the constructor, only initialize `mAltitudeFromPressureDevice` if `mDevicesDatabaseManager.isPaired(id)` is true.
- **Dynamic Activation**: Update the `pairingChanged()` method:
    - Add a block to handle the `ALTITUDE_FROM_PRESSURE` device ID.
    - If `paired` is true and device is null: Create and start the sensor.
    - If `paired` is false and device is not null: Shut down and remove the sensor.

### 2. Device Layer: Stable Identity Reference
Fulfills REQ-CON-010 | Test: TST-BUG-009

#### [MODIFY] [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java)
- (Carried forward from previous plan): Ensure `mDeviceId` is correctly set via the database to enable the pairing link.

## Verification Plan

### Manual Verification (TST-BUG-009)
1. Navigate to 'My Sensors' in the application drawer.
2. Find the 'Altitude from Pressure' card.
3. **Verify** that the toggle switch correctly reflects and updates the state.
4. **Deactivate** the sensor and verify that the green status LED turns grey and live altitude updates from the pressure source cease.
5. **Reactivate** the sensor and verify that telemetry resumes immediately.
