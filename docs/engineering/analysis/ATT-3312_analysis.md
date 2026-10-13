# Stage 1: Problem Domain & Root Cause Analysis - ATT-3312: Persist sensor battery state continuously during workout and reflect live battery levels in sensors list

**Ticket**: [ATT-3312](https://rainerblind.atlassian.net/browse/ATT-3312)  
**Sub-task**: [ATT-3407](https://rainerblind.atlassian.net/browse/ATT-3407) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-CON-007` (extended & refined)  
**Test Mapping**: `TST-CON-010` (extended)  
**Branch**: `feature/ATT-3312`  
**Author**: AI Agent 1 (Forensic Systems Architect)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-CON-007` (*Modernized Post-Workout Low Sensor Battery Alerting (In-App Snackbar & System Notification)*), targeting `MainActivityWithNavigation.kt`, `TrackerService.java`, `ActiveDevicesDbHelper.java`, `DevicesDatabaseManager.java`, `MyDevice.java`, `MyBTLEDevice.java`, `GetMergedDevicesUseCase.kt`, and `DeviceDataRepository.kt`.
2. **Historical Origin & Commit Trace**:
   - Baseline ASPICE import `ac493d2bdf652746b9177a2c574b220285b3bf8b`.
   - Sprint 2026-40.16 (`ATT-2192`): Replaced blocking modal `AlertDialog` with dual-channel notification & M3 snackbar, and introduced session-aware active device tracking via `ActiveDevicesDbHelper`.
   - Sprint 2026-41.9 (`ATT-3166`, `ATT-3167`, `ATT-3178`): Added multi-dimensional sensor filtering by battery health, edge-to-edge snackbar padding, and GATT Battery Service replay simulation.
3. **Root Reason for Existing Formulation**:
   - In ATT-2192, `ActiveDevicesDbHelper` was populated exclusively within `TrackerService.java:endWorkout()` by querying `mBanalService.getDatabaseIdsOfActiveRemoteDevices()`.
   - This assumed that all devices active during the workout would still be connected and receiving data at the exact second the athlete pressed "Stop".
   - Furthermore, `MyBTLEDevice.java` only scheduled a periodic poll every 5 minutes (`READ_BATTERY_PERCENTAGE_PERIOD = 300000`) without registering for GATT characteristic notifications on Battery Level (0x2A19).
   - In the presentation layer, `GetMergedDevicesUseCase.kt` mapped incoming `DeviceTelemetry` onto `DeviceUiData` but omitted `telemetry.batteryPercentage`, retaining only the initial static database snapshot.
4. **Preservation of Core Invariants**:
   - Critical Threshold: Sensor battery level $\le 20\%$ (`CRITICAL_BATTERY_LEVEL = 20`) strictly preserved.
   - Dual-Channel Alerting: Non-blocking system notification (`NOTIFICATION_ID_SENSOR_BATTERY = 4300`) and M3 snackbar upon workout stop.
   - Normal Battery Neutrality: Sensors $> 20\%$ remain silent.
   - Thread Confinement: All SQLite persistence operations confined to background dispatchers (`Dispatchers.IO` / BANAL IPC threads).
   - 100% 9-Language Localization Parity: All 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) maintained.
   - Full Suite Test Pass Rate: 100% clean-room test pass rate across all modules.

---

## 1. Problem Statement & On-Device Findings

During the Sprint 2026-41.10 Joint Review of `ATT-2192`, testing on Google Pixel 10 revealed that post-workout low sensor battery alerts failed to trigger reliably when sensor batteries depleted during active tracking, and the "Meine Sensoren" list did not dynamically reflect live battery levels received from active peripherals.

Forensic code investigation identified four interrelated failure points:

1. **Premature Omission of Depleted Sensors in `ActiveDevicesDbHelper`**:
   - In `TrackerService.java:947`, `ActiveDevicesDbHelper` is written exclusively inside `endWorkout()`, querying `mBanalService.getDatabaseIdsOfActiveRemoteDevices()`.
   - `getDatabaseIdsOfActiveRemoteDevices()` only includes devices where `remoteDevice.isReceivingData()` is currently true.
   - If a sensor battery drops to critical and the sensor powers down or disconnects 5 minutes before workout end, `isReceivingData()` is false at teardown.
   - Consequently, `ActiveDevicesDbHelper` records zero entries for that device for `mWorkoutID`, and `MainActivityWithNavigation.checkSensorBatteryAfterWorkout(workoutId)` completely excludes it from critical evaluation.

2. **Lack of Real-Time GATT Battery Notifications in `MyBTLEDevice.java`**:
   - In `MyBTLEDevice.java:125-135`, when the Battery Service (UUID `0x180F`) is discovered, the characteristic (UUID `0x2A19`) is added to `mReadCharacteristicQueue` for a one-time read, and a timer is posted for 5 minutes (`READ_BATTERY_PERCENTAGE_PERIOD`).
   - `gatt.setCharacteristicNotification(btGattChar, true)` is never called for the battery characteristic, and the Client Characteristic Configuration Descriptor (CCCD `0x2902`) is never written with `ENABLE_NOTIFICATION_VALUE`.
   - As a result, when a physical sensor or the TCX replay simulator (`tools/replay_workout.py`, `REQ-TOOL-003`) emits real-time battery changes via GATT notifications, the Android client ignores them until the 5-minute timer elapses.

3. **Stale Battery Reflection in `GetMergedDevicesUseCase.kt`**:
   - `BANALServiceRepository.kt` correctly captures `batteryPercentage = device.batteryPercentage` into `DeviceTelemetry` every second (lines 323–331).
   - However, in `GetMergedDevicesUseCase.kt:52-71`, when copying `knownDevice` with active telemetry, `batteryPercentage` is omitted from `copy()`.
   - The UI continues to display whatever `knownDevice.batteryPercentage` was read from SQLite when the repository was initialized, never reflecting live telemetry battery updates in the "Meine Sensoren" list.

4. **Cache Invalidation Gap in `DeviceDataRepository.kt`**:
   - When `DevicesDatabaseManager.setBatteryPercentage(deviceId, percentage)` updates the `DEVICES` table, `DeviceDataRepository` is never notified to refresh `_allDevices`.
   - If a sensor disconnects or active telemetry ceases, the cached `_allDevices` list continues to hold the stale battery percentage until an app restart or equipment link event occurs.

---

## 2. Proposed Architectural Solution

### 2.1 Continuous Active Device Tracking in `TrackerService`
- In `TrackerService.java`, maintain a session set `mActiveSessionDeviceIds = Collections.synchronizedSet(new HashSet<Long>())`.
- In `writeNextTrackpoint()` (or periodic tracking loop), evaluate `mBanalService.getDatabaseIdsOfActiveRemoteDevices()`.
- Whenever a new device ID is observed:
  ```java
  if (mBanalService != null) {
      for (long deviceDbId : mBanalService.getDatabaseIdsOfActiveRemoteDevices()) {
          if (mActiveSessionDeviceIds.add(deviceDbId)) {
              // Persist immediately to ActiveDevicesDbHelper
              persistActiveDevice(mWorkoutID, deviceDbId);
          }
      }
  }
  ```
- This guarantees that any device that connected and provided telemetry at any point during `mWorkoutID` is immediately recorded in `ActiveDevicesDbHelper`, even if it subsequently disconnects or dies.
- In `endWorkout()`, perform a final flush of any remaining active devices from `mActiveSessionDeviceIds`.

### 2.2 GATT Battery Notification Subscription in `MyBTLEDevice`
- In `MyBTLEDevice.java`, upon discovering the Battery Service (UUID `0x180F`) and Characteristic (UUID `0x2A19`):
  - Request initial read via `mReadCharacteristicQueue`.
  - Enable notifications: `gatt.setCharacteristicNotification(batteryChar, true)`.
  - Write Client Characteristic Configuration Descriptor (CCCD `0x2902`) with `BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE` if `(batteryChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0`.
- In `onCharacteristicChanged()`, battery updates immediately invoke `setBatteryPercentage(batteryPercentage)`.

### 2.3 Live Battery Reflection in `GetMergedDevicesUseCase`
- In `GetMergedDevicesUseCase.kt`:
  - When mapping active telemetry:
    ```kotlin
    val liveBattery = if (telemetry.batteryPercentage >= 0) telemetry.batteryPercentage else knownDevice.batteryPercentage
    knownDevice.copy(
        isConnected = true,
        lastSeen = application.getString(R.string.devices_now),
        mainValue = ...,
        allValues = ...,
        batteryPercentage = liveBattery,
        isNewlyDiscovered = isFound
    )
    ```
  - This ensures that live battery level updates immediately propagate into `mergedDevices` StateFlow and reflect in real time on sensor cards.

### 2.4 Reactive Battery Notifications in `DeviceDataRepository`
- In `DeviceDataRepository.kt`:
  - Provide a reactive notification channel `notifyBatteryUpdated(deviceId: Long, batteryPercentage: Int)`.
  - Update `_allDevices.value`:
    ```kotlin
    _allDevices.value = _allDevices.value.map {
        if (it.id == deviceId) it.copy(batteryPercentage = batteryPercentage) else it
    }
    ```
- In `DevicesDatabaseManager.java:setInt()`, when updating `LAST_BATTERY_PERCENTAGE`, dispatch to `DeviceDataRepository.notifyBatteryUpdated(deviceID, value)` or emit via an application-level shared flow / broadcast.

---

## 3. User Scope Grounding (ATT-1250)

- **In-Scope**:
  - Accumulating all active session devices continuously into `ActiveDevicesDbHelper` during tracking.
  - Subscribing to BLE Battery Service GATT notifications (0x2A19) in `MyBTLEDevice.java`.
  - Propagating `telemetry.batteryPercentage` into `DeviceUiData` in `GetMergedDevicesUseCase.kt`.
  - Updating `DeviceDataRepository` cache when `DevicesDatabaseManager.setBatteryPercentage` persists battery levels.
  - Unit and contract tests covering continuous active device tracking, live battery merging, and repository cache invalidation.
- **Out-of-Scope**:
  - In-ride low battery alert snackbars (handled separately under `ATT-3313`).
  - Redesigning sensor list cards or battery level icon visuals (already established in `ATT-3166`).
