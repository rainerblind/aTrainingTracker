# Walkthrough - ATT-353: Self-Healing Pressure Sensor Pairing

Successfully resolved the issue where the barometric pressure sensor was missing on compatible devices (like Pixel 10) by implementing a "Self-Healing Registration" mechanism and adding full manual "Pairing" support.

## Fulfilled Requirements

| ID | Description | Rationale |
|:---|:---|:---|
| **REQ-DAT-006** | The system SHALL automatically detect and register missing internal sensors in the database upon initialization. | Ensure that analytical components can correctly identify and display internal hardware even if it was skipped during previous installations. |
| **REQ-CON-010** | The system SHALL allow the user to manually activate or deactivate internal smartphone sensors via the \"My Sensors\" interface. | Provide consistent control for both internal and external sensors, respecting user preference for telemetry sources. |

## Changes Made

### 🚀 Self-Healing Registration

#### [DevicesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManager.java)
- **Assurance Logic**: Implemented `ensureSmartphoneDeviceExists(DeviceType, Name)`. This method surgically checks for and inserts missing internal devices into the `Devices` table without creating duplicates. 
- **Stable Identity**: By ensuring a database record exists, the pressure sensor is now assigned a stable unique technical identifier, which is essential for visual tracking in the UI.

#### [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java)
- **Just-in-Time Identity**: Updated the constructor to call the self-healing registration pass before resolving its ID. This guarantees that your Pixel 10 (or any compatible device) will now "discover" its own pressure sensor even if it was missing from the database.
- **Eager Stream Visibility**: Refactored the telemetry management to add the altitude sensor stream immediately upon device creation, ensuring it appears in lists even before the first barometric reading arrives.

### 🔄 Manual Pairing & Backpressure

#### [DeviceManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java)
- **Selective Startup**: Refactored the engine constructor to strictly respect the user's "Paired" preference. The pressure sensor logic is now only initialized if it is explicitly enabled in the UI.
- **Dynamic Control**: Implemented dynamic creation and destruction within the `pairingChanged` event loop. You can now toggle the pressure sensor ON or OFF in real-time from the sensors list.

### 📊 Professional UI Integration

- **Visual Parity**: The "Altitude from Pressure" sensor now appears in the **Smartphone** section of the **"My Sensors"** list. It features its own identity card, a functional status LED, and a standard Material 3 toggle switch.
- **Source Audit**: Tapping the Altitude icon in the tracking header now explicitly lists the pressure sensor as an active provider with its real-time value.

## Verification Results

### Integration Verification (SWE.5)
- **Test ID**: TST-BUG-008 (Self-Healing Registration)
- **Result**: **PASS**. Deleting the sensor from the database and restarting the app correctly re-registers it and makes it visible in the list.
- **Test ID**: TST-BUG-009 (Pairing Control)
- **Result**: **PASS**. Verified that the toggle switch correctly starts and stops the barometric telemetry. The status LED reflects the "Connected" state accurately.

> [!TIP]
> This improvement brings your internal hardware sensors into full visual and logical parity with your external ANT+ and BLE devices, providing total technical transparency for your training data.
