# Walkthrough: ATT-3169

## Active Sensor Deletion Disconnection Lifecycle and Defensive Telemetry Boundary Filtering

### Executive Summary
When deleting an active sensor from *Meine Sensoren* while the sensor was actively transmitting data, an orphaned ghost icon labeled "New Sensor" with a search icon lingered in the Control Tracking screen. Tapping this ghost icon caused the screen to dim into an unresponsive blank modal scrim.
Investigation revealed that:
1. `DeviceDataRepository.deleteDevice` deleted the sensor from SQLite without notifying `BANALService` or `DeviceManager`, leaving the physical Bluetooth/ANT+ connection and active device ID intact in memory.
2. `ControlTrackingViewModel` combined active remote device IDs with the database device list. When the database record was missing, it fell back to creating a dummy sensor entity (`DeviceType.DUMMY`, `"New Sensor"`).
3. Tapping this dummy tile launched `ComposableEditDeviceDialogFragment`, which attached a modal window with `FLAG_DIM_BEHIND` and rendered an empty dialog body because `EditDeviceViewModel` could not load the device from the database.

This ticket resolves the defect comprehensively:
- **Teardown on Deletion**: `DeviceDataRepository.deleteDevice` now dispatches `sendPairingChangedBroadcast(deviceId, paired = false)`, triggering `DeviceManager.removeAndStopRemoteDevice` to shut down hardware connections and unregister the sensor.
- **Defensive Teardown Safety**: `DeviceManager.removeAndStopRemoteDevice` null-checks `myRemoteDevice` to guarantee crash-free teardown even for unmapped devices.
- **Defensive Boundary Filtering**: `ControlTrackingViewModel.remoteDevices` now uses `mapNotNull` to strictly filter out any connected device ID absent from the database, eliminating dummy placeholder tiles.
- **Resilient Dialog Auto-Dismiss**: `EditDeviceViewModel` tracks `deviceNotFound: StateFlow<Boolean>`. If a device snapshot cannot be loaded, `EditDeviceDialog` observes this state and immediately dismisses itself, preventing screen dimming traps.

---

## Changes Made

### 1. Repository & Service Teardown
* [DeviceDataRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt#L313-L320):
  In `deleteDevice(deviceId: Long)`, invoke `sendPairingChangedBroadcast(deviceId, false)` alongside database deletion and list reloading.
* [DeviceManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java#L806-L817):
  In `removeAndStopRemoteDevice(long deviceId)`, guard with `if (myRemoteDevice != null)` before accessing properties, removing from search queue, and shutting down.

### 2. ViewModel & UI Defensive Filtering
* [ControlTrackingViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt#L120-L144):
  In `remoteDevices: StateFlow<List<RemoteDeviceUIData>>`, replace `ids.map` with `ids.mapNotNull` returning `null` when `dbDevice == null`. Defensively drops orphaned or deleted device IDs.
* [EditDeviceViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceViewModel.kt#L74-L95):
  Introduce `deviceNotFound: StateFlow<Boolean>`. Set `deviceNotFound = true` when `devicesRepository.getDeviceSnapshotById(deviceId) == null`.
* [EditDeviceDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt#L60-L75):
  Collect `deviceNotFound` and trigger `LaunchedEffect(deviceNotFound) { if (deviceNotFound) onDismiss() }`.

### 3. Requirements & Living Documentation
* [requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md):
  Added `REQ-UI-349` (*Active Sensor Deletion Disconnection Lifecycle and Defensive Telemetry Boundary Filtering Architecture*), marked as `Verified`.
* [tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md):
  Added `TST-UI-309` (*Active Sensor Deletion Disconnection Lifecycle and Defensive Telemetry Boundary Filtering Verification*), marked as `Verified`.

---

## Verification Results

### Targeted Unit Tests
| Test Class | Test Method | Scope / Target | Result |
| :--- | :--- | :--- | :--- |
| `ControlTrackingViewModelTest` | `testRemoteDevices_filtersOutOrphanedOrDeletedConnectedSensors` | Filters out non-existent DB devices, drops `DeviceType.DUMMY` | **PASSED** (100%) |
| `DeviceDataRepositoryEquipmentSyncTest` | `testDeleteDevice_broadcastsPairingChangedWithUnpairedFlag` | Broadcasts `PAIRING_CHANGED` with `paired = false` on delete | **PASSED** (100%) |
| `DeviceDataRepositoryEquipmentSyncTest` | `testEditDeviceViewModel_deviceNotFound_whenSnapshotIsNull` | Sets `deviceNotFound = true` when device snapshot is missing | **PASSED** (100%) |
| `DeviceManagerTeardownTest` | `testRemoveAndStopRemoteDevice_safeWhenDeviceNotRegistered` | Executes defensively without NPE on unmapped device ID | **PASSED** (100%) |

### Clean-Room Full Suite Regression
- Executed `./gradlew testDebugUnitTest` across all test suites to confirm 100% test pass rate with zero regressions.
