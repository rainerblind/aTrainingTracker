# Implementation Plan - ATT-353: Internal Altitude Sensor Transparency

Ensure that the internal barometric pressure sensor is correctly identified and displayed in the `SensorSourceDialog` and the "My Sensors" list.

## User Review Required

> [!IMPORTANT]
> - **Technical Identity**: The "Altitude from Pressure" sensor will now be assigned a stable technical identifier from the database. 
> - **Visibility in Lists**: The sensor will appear in the "My Sensors" list under the "Smartphone" section, consistent with other internal sources like GPS.
> - **Real-time Source Auditing**: Tapping on the "Altitude" icon in the tracking header will now explicitly show "Altitude from Pressure" as an active source or backup, including its current real-time value.

## Proposed Changes

### 1. Database Layer: Access Alignment
#### [MODIFY] [DevicesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManager.java)
- Make `getSmartphoneDeviceId(DeviceType)` **public** to allow internal device implementations to resolve their IDs.

### 2. Device Layer: Identity & Telemetry
Fulfills REQ-UI-118 | Test: TST-BUG-007

#### [MODIFY] [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java)
- **Identity Initialization**: In the constructor, initialize `mDeviceId` using `mDevicesDatabaseManager.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_PRESSURE)`.
- **Eager Sensor Registration**: Move `addSensor(mAltitudeSensor)` into the `addSensors()` method to ensure the telemetry stream is defined immediately upon device creation.

### 3. Management Layer: Full Active Device List
Fulfills REQ-UI-118 | Test: TST-BUG-007

#### [MODIFY] [DeviceManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java)
- Update **`getActiveDevicesIncludingSpeedAndLocationDevices`**:
    - Explicitly include `mAltitudeFromPressureDevice` if it is non-null.
    - This ensures the sensor is reported as an active hardware entity to the `BANALServiceRepository` and subsequently the UI.

## Verification Plan

### Manual Verification (TST-BUG-007)
1. Open the app on a device with a barometric pressure sensor.
2. Ensure Altitude tracking is active.
3. **Verify** that "Altitude from Pressure" is visible in the "My Sensors" list under the Smartphone category.
4. Tap the Altitude icon in the header.
5. **Verify** that 'Altitude from Pressure' is visible as the source or backup with its real-time value.
