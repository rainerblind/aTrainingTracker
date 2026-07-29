# Implementation Plan - ATT-353: Self-Healing Pressure Sensor Registration

Address the bug where the barometric pressure sensor is hidden on compatible devices (like Pixel 10) by implementing a robust "Just-in-Time" database registration mechanism.

## User Review Required

> [!IMPORTANT]
> - **Automatic Discovery**: The app will now automatically register the pressure sensor in your "My Sensors" list if the hardware is detected, even if it was previously skipped or deleted.
> - **Technical Stability**: This ensures that high-precision barometric altitude always has a valid identity in the system, allowing it to be auditable in the source dialog.

## Proposed Changes

### 1. Database Layer: Assurance Logic
Fulfills REQ-DAT-006 | Test: TST-BUG-008

#### [MODIFY] [DevicesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManager.java)
- **New Method**: Add `ensureSmartphoneDeviceExists(DeviceType type, String name)`.
- **Logic**:
    1. Query the `Devices` table for a record matching the `DeviceType` and `Protocol.SMARTPHONE`.
    2. If the count is 0, surgically insert a new record with the provided name and `PAIRED = true`.
    3. Use the device's brand (e.g., Google/Samsung) as the manufacturer.

### 2. Device Layer: Just-in-Time Initialization
Fulfills REQ-DAT-006 | Test: TST-BUG-008

#### [MODIFY] [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java)
- **Constructor Update**: Call `mDevicesDatabaseManager.ensureSmartphoneDeviceExists(DeviceType.ALTITUDE_FROM_PRESSURE, ...)` before resolving the `mDeviceId`.
- **Rationale**: This guarantees that a database entry exists before the sensor manager attempts to link the hardware to an ID, fixing the "ghosting" issue.

## Verification Plan

### Manual Verification (TST-BUG-008)
1. Open the app and verify the pressure sensor is missing (if applicable).
2. Open the Database Inspector.
3. If the 'Altitude from Pressure' entry exists, delete it.
4. Restart the app.
5. **Verify** that the 'Altitude from Pressure' sensor is automatically re-registered in the "My Sensors" list.
6. **Verify** that tapping the Altitude icon in the header now correctly shows the pressure sensor with its real-time value.
