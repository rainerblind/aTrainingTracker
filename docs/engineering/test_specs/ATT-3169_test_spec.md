# Test Specification: ATT-3169

## 1. System-Level Requirement Traceability
* **Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)
* **Primary Requirement**: `REQ-UI-349` (*Active Sensor Deletion Disconnection Lifecycle and Defensive Telemetry Boundary Filtering*)
* **Test Case Identifier**: `TST-UI-309`

---

## 2. Requirement Details (REQ-UI-349)
The system SHALL ensure that deleting an active sensor terminates all background connections and never leaks orphaned state into the UI or tracking session:
1. When a user deletes a sensor from the devices management list (`DeviceDataRepository.deleteDevice`), the system SHALL broadcast `BANALService.PAIRING_CHANGED` with `paired = false` and `deviceId` to notify `BANALService` and `DeviceManager` to disconnect and unregister the sensor.
2. In `ControlTrackingViewModel`, the active remote sensor mapping logic SHALL defensively filter the active connected sensor ID list against `allDevicesFromDb`: any remote device ID present in the active connected list that does not exist in `allDevicesFromDb` SHALL be discarded and SHALL NOT emit `DeviceType.DUMMY` or placeholder sensor items.
3. In `EditDeviceDialog`, if the device snapshot cannot be resolved from the database (e.g., deleted asynchronously while dialog open), the dialog SHALL immediately dismiss cleanly without crashing or rendering empty invalid fields.
4. In `DeviceManager.removeAndStopRemoteDevice`, the method SHALL execute defensively and gracefully without NPE or crash if the device is not registered in `mMyRemoteDevices`.

---

## 3. Detailed Acceptance Criteria & Test Scenarios

### Scenario 1: ViewModel Defensive Filtering Drops Orphaned Active Remote IDs
* **Given** `ControlTrackingViewModel` receives `allDevicesFromDb` containing devices `[#10, #20]`.
* **When** `connectedDevices` emits `[#10, #30]` (where `#30` is deleted from DB).
* **Then** `remoteDevices` emits only `RemoteDeviceUIData` for `#10`, and strictly 0 items for `#30`. Zero `DeviceType.DUMMY` or `"New Sensor"` fallback tiles are rendered.

### Scenario 2: Repository Deletion Broadcasts PAIRING_CHANGED Disconnection
* **Given** device `#42` exists in the database.
* **When** `DeviceDataRepository.deleteDevice(42L)` is called.
* **Then** `context.sendBroadcast(...)` is dispatched with action `BANALService.PAIRING_CHANGED`, extra `BANALService.EXTRA_DEVICE_ID = 42L`, and extra `BANALService.EXTRA_PAIRED = false`.
* **And** `devicesDatabaseManager.deleteDevice(42L)` is executed.

### Scenario 3: EditDeviceDialog Auto-Dismiss on Missing Snapshot
* **Given** `EditDeviceDialog` is opened for a device ID that no longer exists in the database.
* **When** `EditDeviceViewModel` resolves the device snapshot to `null`.
* **Then** the dialog dismisses itself immediately via `onDismiss()` without crashing or displaying empty controls.

### Scenario 4: Clean-Room Full Suite Regression Execution
* **Given** all architectural improvements and targeted tests are implemented.
* **When** running `./gradlew testDebugUnitTest`.
* **Then** all unit tests pass 100% with zero regressions across the codebase.

---

## 4. Verification Matrix
| Test Case | Method / Component Under Test | Scope / Verification Target | Status |
| :--- | :--- | :--- | :--- |
| `testRemoteDevicesFiltersOutDeletedOrOrphanedConnectedSensors` | `ControlTrackingViewModelTest` | Filters out non-existent DB devices, avoids `DeviceType.DUMMY` fallback | Verified |
| `testDeleteDeviceBroadcastsPairingChangedWithUnpairedFlag` | `DeviceDataRepositoryTest` | Broadcasts `PAIRING_CHANGED` with `paired = false` and deletes DB record | Verified |
| `testEditDeviceDialogAutoDismissOnMissingDevice` | `EditDeviceDialogTest` / `EditDeviceViewModelTest` | Dismisses on null snapshot | Verified |
| `testRemoveAndStopRemoteDeviceSafeWhenNotRegistered` | `DeviceManagerTest` | Defensively handles absent device ID | Verified |
| Full Suite Regression | `./gradlew testDebugUnitTest` | Zero test regressions | Verified |
