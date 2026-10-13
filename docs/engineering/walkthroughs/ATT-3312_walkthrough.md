# Stage 5: Walkthrough & Verification Report - ATT-3312: Persist sensor battery state continuously during workout and reflect live battery levels in sensors list

**Ticket**: [ATT-3312](https://rainerblind.atlassian.net/browse/ATT-3312)  
**Sub-task**: [ATT-3411](https://rainerblind.atlassian.net/browse/ATT-3411) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-CON-007` (extended & refined)  
**Test Mapping**: `TST-CON-010` (extended & refined)  
**Branch**: `feature/ATT-3312`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
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

## 1. Problem Domain & Implementation Summary

In previous iterations, athlete feedback revealed two critical sensor battery usability gaps:
1. **Sensors List Battery Lag**: When viewing the "Meine Sensoren" (My Sensors) list, battery levels remained static from the initial database load and did not reflect real-time battery status updates from live telemetry streaming or GATT notifications.
2. **Lost Session Device History for Disconnected Sensors**: If a sensor's battery died or disconnected 5 minutes before the end of a 3-hour ride, `TrackerService.endWorkout()` queried active devices only at the final second. As a result, the dead sensor was omitted from `ActiveDevicesDbHelper`, preventing the athlete from receiving the vital post-workout low battery notification and snackbar warning.

### Key Architectural Solutions Implemented:
- **Continuous Session Device Tracking in `TrackerService.java`**:
  - Implemented `mSessionActiveDeviceIds` tracking set within `TrackerService`.
  - On incoming BANAL updates (`ACTION_NEW_VALUES_AVAILABLE_INTENT`), any remote device transmitting valid telemetry is immediately and incrementally recorded into `ActiveDevicesDbHelper.addActiveDevice(mWorkoutID, deviceId)`.
  - Disconnected or depleted sensors remain recorded for the lifetime of the session.
- **GATT Characteristic Notification Subscription in `MyBTLEDevice.java`**:
  - When the Battery Level characteristic (UUID `0x2A19`) is discovered with `PROPERTY_NOTIFY`, `MyBTLEDevice` automatically enables local characteristic notifications and writes `ENABLE_NOTIFICATION_VALUE` to the Client Characteristic Configuration Descriptor (CCCD `0x2902`).
  - Incoming GATT notifications in `onCharacteristicChanged` immediately update the internal battery percentage and broadcast updates.
- **Real-Time Telemetry Battery Merging in `GetMergedDevicesUseCase.kt`**:
  - `mergedDevices` flow actively maps incoming telemetry's `batteryPercentage` to `DeviceUiData` when $\ge 0$, providing immediate live updates in the sensor card list.
- **Reactive Cache Invalidation in `DeviceDataRepository.kt`**:
  - Introduced `notifyBatteryUpdated(deviceId, percentage)` allowing immediate in-memory state update to `_allDevices` without requiring a blocking full-table SQLite query.

---

## 2. Verification & Test Execution Results

1. **Targeted Unit & Integration Tests**:
   - `GetMergedDevicesUseCaseTest`:
     - Verified live merging of telemetry battery percentages ($\ge 0$) onto UI device models.
     - Verified preservation of known device battery percentages when telemetry is $-1$ or unmeasured.
   - `DeviceDataRepositoryBatteryTest`:
     - Verified in-memory reactive update of cached device battery percentages upon notification.
   - `ActiveDevicesDbHelperTest`:
     - Verified incremental continuous persistence of active devices during workout tracking.
     - Verified idempotent retrieval for specified workout IDs.
   - `SensorBatteryAlertTest`:
     - Verified detection of critical battery sensors ($\le 20\%$) that disconnected mid-workout.
     - Verified neutrality (no notifications/snackbars) when all sensors $> 20\%$.
   - `TranslationParityTest`:
     - 100% string parity verified across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

2. **Full Clean-Room Regression Suite**:
   - Command: `./gradlew testDebugUnitTest`
   - Result: **BUILD SUCCESSFUL** (100% pass rate across entire repository).

3. **Living Documentation Synchronization**:
   - `docs/requirements.md`: `REQ-CON-007` updated to `Verified`.
   - `docs/tests.md`: `TST-CON-010` updated to `Verified`.
