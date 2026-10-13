# Stage 2: Requirement & Test Specification - ATT-3312: Persist sensor battery state continuously during workout and reflect live battery levels in sensors list

**Ticket**: [ATT-3312](https://rainerblind.atlassian.net/browse/ATT-3312)  
**Sub-task**: [ATT-3408](https://rainerblind.atlassian.net/browse/ATT-3408) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-CON-007` (extended & refined)  
**Test Mapping**: `TST-CON-010` (extended & refined)  
**Branch**: `feature/ATT-3312`  
**Author**: AI Agent 1 (Requirement & Verification Engineer)  
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

## 1. Formal Requirement Specification

### REQ-CON-007: Continuous Sensor Battery State Persistence, Live Battery Display in Sensors List & Post-Workout Alerting

The system SHALL continuously persist remote sensor battery level updates, maintain real-time session participation records in `ActiveDevicesDbHelper`, dynamically reflect live battery levels in "Meine Sensoren", and reliably alert athletes about critical battery levels following a completed workout (ATT-2192, ATT-3312):

1. **Elimination of Modal Disruption**:
   - The system SHALL NOT display modal dialogs (`AlertDialog`) upon workout finalization or stopping.

2. **Session-Aware Critical Battery Evaluation**:
   - Upon tracking finalization (`TRACKING_FINISHED_INTENT`), the system SHALL evaluate the battery levels of remote sensors that were actively connected at any point during the workout session via `ActiveDevicesDbHelper.getDatabaseIdsOfActiveDevices(workoutId)`.
   - Evaluation SHALL execute asynchronously on a background I/O dispatcher (`Dispatchers.IO`).
   - A sensor SHALL be classified as critical if its recorded `LAST_BATTERY_PERCENTAGE` is $\ge 0\%$ AND $\le 20\%$ (`CRITICAL_BATTERY_LEVEL = 20`).

3. **In-App Material 3 Snackbar Presentation**:
   - When one or more session sensors are critical, the system SHALL emit a sticky `LowBatteryAlert` event via `WorkoutNavigationEvents` (`replay = 1`, `DROP_OLDEST`).
   - Upon composition of the post-workout destination screen (`WorkoutSummariesTabbedScreen`), the screen SHALL collect the alert and display a non-blocking Material 3 `Snackbar`.
   - Format: `⚠️ [Sensor Name]: Akku schwach ([X]%)` (single sensor) or `⚠️ [N] Sensoren: Akku schwach ([Names])` (multiple sensors).
   - Action button: `[Zu Sensoren]` (EN: `[View Sensors]`). Tapping the action SHALL invoke `navigateToDrawerItem(R.id.drawer_my_sensors)`.
   - Once displayed, the alert SHALL be cleared via `WorkoutNavigationEvents.consumeLowBatteryAlert()`.

4. **Persistent Android System Notification**:
   - The system SHALL post an Android system notification on channel `NOTIFICATION_CHANNEL__SENSOR_BATTERY` (ID 4300).
   - Tap Action: PendingIntent targeting `MainActivityWithNavigation` launching into "Meine Sensoren" with `EXTRA_FILTER_CRITICAL_BATTERY = true`.

5. **Normal Battery Neutrality**:
   - When all sensors active in the workout have battery $> 20\%$ (or if no remote sensors were connected), zero notifications and zero snackbars SHALL be dispatched.

6. **100% 9-Language Localization Parity**:
   - All notification channels, descriptions, titles, text, and snackbar actions SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

7. **Continuous Active Session Device Tracking (`TrackerService.java`, `ActiveDevicesDbHelper.java`)**:
   - `TrackerService` SHALL maintain an in-memory session set of remote device IDs that connected or transmitted telemetry at any point during `mWorkoutID`.
   - Whenever active remote devices are reported by `mBanalService.getDatabaseIdsOfActiveRemoteDevices()`, newly observed device IDs SHALL be immediately inserted into `ActiveDevicesDbHelper` (`ActiveDevices.TABLE`) for `mWorkoutID`.
   - This ensures sensors that disconnect or deplete their battery mid-workout are reliably preserved in `ActiveDevicesDbHelper` for post-workout evaluation.

8. **GATT Characteristic Notification Subscription (`MyBTLEDevice.java`)**:
   - Upon discovering the standard Bluetooth SIG Battery Service (UUID `0x180F`) and Battery Level Characteristic (UUID `0x2A19`), `MyBTLEDevice` SHALL enable notifications via `gatt.setCharacteristicNotification(btGattChar, true)`.
   - If the characteristic properties include `PROPERTY_NOTIFY`, `MyBTLEDevice` SHALL write the Client Characteristic Configuration Descriptor (CCCD UUID `0x2902`) with `BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE`.
   - In `onCharacteristicChanged()`, incoming battery updates SHALL immediately invoke `setBatteryPercentage(batteryPercentage)`.

9. **Live Battery Display in Sensors List (`GetMergedDevicesUseCase.kt`, `DeviceDataRepository.kt`)**:
   - In `GetMergedDevicesUseCase.kt`, when merging active device telemetry, `knownDevice.copy()` SHALL set `batteryPercentage = if (telemetry.batteryPercentage >= 0) telemetry.batteryPercentage else knownDevice.batteryPercentage`, immediately propagating live battery levels into `mergedDevices` StateFlow.
   - In `DeviceDataRepository.kt`, when `DevicesDatabaseManager.setBatteryPercentage()` updates the `DEVICES` table, `DeviceDataRepository` SHALL update its cached `_allDevices` list, ensuring immediate UI reflection even when telemetry stream is inactive or upon sensor disconnection.

---

## 2. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Live Battery Update in Sensors List via Telemetry**
  - *Given* an athlete viewing the "Meine Sensoren" list with a connected sensor initially showing 80% battery,
  - *When* a new telemetry frame arrives with battery percentage 15%,
  - *Then* `GetMergedDevicesUseCase.mergedDevices` immediately emits the device with `batteryPercentage = 15`, updating the sensor card UI without requiring a manual refresh.

- **Scenario 2: Mid-Workout Depleted Sensor Post-Workout Warning**
  - *Given* an active workout tracking session with connected sensor ID 42,
  - *When* sensor ID 42 transmits battery percentage 12% and subsequently powers off / disconnects 10 minutes before workout completion,
  - *Then* sensor ID 42 is already recorded in `ActiveDevicesDbHelper` for the workout ID, and upon stopping the workout, `checkSensorBatteryAfterWorkout` evaluates sensor ID 42 as critical and posts the low battery notification and snackbar.

- **Scenario 3: GATT Notification Reception on BLE Battery Change**
  - *Given* a connected BLE peripheral exposing Battery Service (0x180F) and Battery Level (0x2A19),
  - *When* the peripheral sends a GATT notification with battery level 18%,
  - *Then* `MyBTLEDevice.onCharacteristicChanged` receives the characteristic and invokes `setBatteryPercentage(18)`, persisting 18% to `DevicesDatabaseManager`.

- **Scenario 4: Normal Battery Neutrality on Workout End**
  - *Given* an active workout where all session sensors maintained battery $> 20\%$,
  - *When* the workout is stopped,
  - *Then* zero system notifications and zero snackbars are dispatched.

---

## 3. Test Specification & Traceability Matrix

### TST-CON-010: Continuous Sensor Battery Persistence, Live Display & Post-Workout Alerting Verification

| Test Case ID | Test Target | Verification Description |
|:---|:---|:---|
| **TST-CON-010.1** | `GetMergedDevicesUseCaseTest.kt` | Verify `mergedDevices` maps `batteryPercentage` from `telemetry.batteryPercentage` when $\ge 0$, and preserves `knownDevice.batteryPercentage` when telemetry is $-1$. |
| **TST-CON-010.2** | `DeviceDataRepositoryBatteryTest.kt` | Verify `DeviceDataRepository.notifyBatteryUpdated(deviceId, percentage)` updates `_allDevices` reactively in memory without full SQLite re-query. |
| **TST-CON-010.3** | `ActiveDevicesDbHelperTest.kt` | Verify `ActiveDevicesDbHelper` correctly persists and retrieves active device IDs for a workout ID, supporting incremental session inserts. |
| **TST-CON-010.4** | `SensorBatteryAlertTest.kt` | Verify disconnected sensors that were active during the session are evaluated for critical battery post-workout. |
| **TST-CON-010.5** | `TranslationParityTest.kt` | Verify 100% translation parity across all 9 supported locales. |
| **TST-CON-010.6** | Full Clean-Room Suite | Execute `./gradlew testDebugUnitTest` verifying 100% pass rate. |
