# Implementation Plan: ATT-3169

## 1. Traceability & Requirements Mapping
* **Parent Ticket**: [ATT-3169](https://atrainingtracker.atlassian.net/browse/ATT-3169) (`[Bug] Deleting active connected sensor leaves orphaned ghost 'New Sensor' in Control Tracking leading to dimmed blank dialog`)
* **Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)
* **Primary Requirement**: `REQ-UI-349` (*Active Sensor Deletion Disconnection Lifecycle and Defensive Telemetry Boundary Filtering*)
* **Test Case**: `TST-UI-309`

---

## 2. Architecture & Design Decisions (SWE.2)
To resolve the bug comprehensively while maintaining strict ASPICE boundary defense and zero regression risk:
1. **Repository Lifecycle Teardown (`DeviceDataRepository`)**:
   - `DeviceDataRepository.deleteDevice(deviceId: Long)` deletes the record from SQLite database.
   - It must also invoke `sendPairingChangedBroadcast(deviceId, false)` to notify `BANALService` and `DeviceManager` that the device is no longer paired.
   - `DeviceManager` handles `PAIRING_CHANGED` by invoking `removeAndStopRemoteDevice(deviceId)`, shutting down the Bluetooth/ANT+ connection and unregistering the sensor from memory.
2. **Defensive Disconnection Teardown (`DeviceManager`)**:
   - In `DeviceManager.removeAndStopRemoteDevice(long deviceId)`, guard against null `MyRemoteDevice`:
     `if (myRemoteDevice != null) { ... myRemoteDevice.shutDown(); }`
     This prevents `NullPointerException` if `removeAndStopRemoteDevice` is called for an uninitialized or already removed ID.
3. **Defensive Filtering in ViewModel (`ControlTrackingViewModel`)**:
   - In `ControlTrackingViewModel.remoteDevices`, `ids.map` currently falls back to `RemoteDeviceUIData(id, DeviceType.DUMMY, "New Sensor", ...)` when `dbDevice == null`.
   - In Control Tracking, only valid sensors present in the database should ever be rendered.
   - Change `ids.map { ... }` to `ids.mapNotNull { ... }`: if `dbDevice == null`, return `null`.
   - This prevents any ghost tiles or dummy placeholders from being rendered when a device is deleted or transiently unsynchronized.
4. **Resilient Dialog Auto-Dismiss (`EditDeviceViewModel` & `EditDeviceDialog`)**:
   - In `EditDeviceViewModel`, introduce `val deviceNotFound: StateFlow<Boolean>`.
   - In `loadInitialDeviceData(deviceId)`, if `devicesRepository.getDeviceSnapshotById(deviceId) == null`, set `_deviceNotFound.value = true`.
   - In `EditDeviceDialog`, observe `deviceNotFound` and call `onDismiss()` when true. This guarantees that even if a dialog is somehow launched for a non-existent device ID, the dialog immediately dismisses itself instead of rendering an empty dim-behind scrim.

---

## 3. Preserved Invariants & Chesterton's Fence
* **Connected Remote Devices Invariant**: Valid connected sensors with database records continue to render their proper icons, battery state, and titles on the Control Tracking screen.
* **Database Sync Invariant**: Database deletions continue to update `allDevicesFromDb` via existing flow collectors and Room/SQLite callbacks.
* **Legacy Compatibility**: `ComposableEditDeviceDialogFragment` and `EditDeviceFragmentFactory` remain binary compatible with all existing Java fragment callers.
* **Scope Bounding (ATT-1250 / Rule 26)**: Strictly confined to sensor deletion teardown broadcast, defensive null-checking in `DeviceManager`, defensive ViewModel mapping in `ControlTrackingViewModel`, and auto-dismiss in `EditDeviceDialog`. No unrelated sensor streaming or database schema changes.

---

## 4. Step-by-Step Atomic Construction Steps

### Step 1: Disconnection Broadcast on Delete (`DeviceDataRepository.kt`)
* In `com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository.kt`:
  In `deleteDevice(deviceId: Long)`:
  Call `sendPairingChangedBroadcast(deviceId, false)` alongside `devicesDatabaseManager.deleteDevice(deviceId)` and `loadAllDevices()`.

### Step 2: Defensive Null-Check in Disconnection (`DeviceManager.java`)
* In `com.atrainingtracker.banalservice.devices.DeviceManager.java`:
  In `removeAndStopRemoteDevice(long deviceId)`:
  Wrap the teardown block with `if (myRemoteDevice != null) { ... }`.

### Step 3: Defensive Telemetry Boundary Filtering (`ControlTrackingViewModel.kt`)
* In `com.atrainingtracker.trainingtracker.ui.tracking.controltracking/ControlTrackingViewModel.kt`:
  In `remoteDevices: StateFlow<List<RemoteDeviceUIData>>`:
  Replace `ids.map` fallback with `ids.mapNotNull`:
  ```kotlin
  ids.mapNotNull { id ->
      val dbDevice = dbDevices.find { it.id == id }
      if (dbDevice != null) {
          RemoteDeviceUIData(
              id = dbDevice.id,
              deviceType = dbDevice.deviceType,
              name = dbDevice.deviceName,
              iconRes = UIHelper.getIconId(dbDevice.deviceType, dbDevice.protocol)
          )
      } else {
          null
      }
  }
  ```

### Step 4: Missing Snapshot Auto-Dismiss (`EditDeviceViewModel.kt` & `EditDeviceDialog.kt`)
* In `EditDeviceViewModel.kt`:
  Add `_deviceNotFound = MutableStateFlow(false)` and `deviceNotFound: StateFlow<Boolean> = _deviceNotFound.asStateFlow()`.
  In `loadInitialDeviceData(deviceId: Long)`:
  If snapshot is null, set `_deviceNotFound.value = true`.
* In `EditDeviceDialog.kt`:
  Collect `deviceNotFound` and trigger `LaunchedEffect(deviceNotFound) { if (deviceNotFound) onDismiss() }`.

### Step 5: Unit Tests Construction & Verification
* In `ControlTrackingViewModelTest.kt`:
  Add unit tests verifying that orphaned sensor IDs in `activeRemoteDevicesIds` without matching DB devices are omitted and never emit `DeviceType.DUMMY`.
* In `DeviceDataRepositoryTest.kt`:
  Add unit test verifying `deleteDevice` broadcasts `PAIRING_CHANGED` with `paired = false`.
* In `EditDeviceViewModelTest.kt`:
  Add unit test verifying `deviceNotFound` transitions to `true` when snapshot is missing.
* Run targeted tests via Gradle with `BypassSandbox: true`.

### Step 6: Full Suite Regression
* Run `./gradlew testDebugUnitTest` with `BypassSandbox: true` to assert 100% test pass rate.
