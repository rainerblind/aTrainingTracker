# Implementation Plan - ATT-353 Refinement: Eager Sensor Registration

Address the circular dependency in the pressure sensor registration logic to ensure the "Altitude from Pressure" sensor is immediately visible and controllable on compatible devices.

## User Review Required

> [!IMPORTANT]
> - **Zero-Wait Discovery**: I am moving the "Self-Healing" logic up to the very beginning of the app's sensor engine. 
> - **Immediate Visibility**: This change guarantees that your Pixel 10 will register its pressure sensor in the database *before* the UI tries to display it. You will no longer need to restart the app twice to see the sensor.
> - **Focused fix**: As requested, I am focusing this refined registration strictly on the **Altitude from Pressure** sensor.

## Proposed Changes

### 1. Management Layer: Eager Assurance
Fulfills REQ-DAT-006 | Test: TST-BUG-011

#### [MODIFY] [DeviceManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java)
- **Top-of-Constructor Logic**:
    - Call `mDevicesDatabaseManager.ensureSmartphoneDeviceExists(DeviceType.ALTITUDE_FROM_PRESSURE, ...)` at the very beginning of the `DeviceManager` constructor.
    - **Rationale**: This breaks the circular dependency. By registering the device first, the subsequent `getSmartphoneDeviceId` and `isPaired` calls will correctly resolve the identity and allow the `AltitudeFromPressureDevice` to be created on the very first run.

### 2. Device Layer: Logic Cleanup
Fulfills REQ-UI-118 | Test: TST-BUG-011

#### [MODIFY] [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java)
- **Remove Registration Logic**: Remove the call to `ensureSmartphoneDeviceExists` from the constructor, as this is now handled eagerly by the `DeviceManager`.
- **Identity Linkage**: Keep the direct assignment of `mDeviceId` via `getSmartphoneDeviceId`.

## Verification Plan

### Manual Verification (TST-BUG-011)
1. Use the Database Inspector to **delete** the 'Altitude from Pressure' entry from the `Devices` table.
2. Restart the app.
3. **Verify** that the sensor is immediately visible in the "My Sensors" list on this first run.
4. **Verify** that the pairing toggle works and correctly starts/stops the telemetry LED.
