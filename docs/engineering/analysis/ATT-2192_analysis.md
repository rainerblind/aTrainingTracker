# Stage 1: Problem Domain & Root Cause Analysis - ATT-2192: Modernize post-workout low sensor battery warning with system notification and in-app snackbar

**Ticket**: [ATT-2192](https://rainerblind.atlassian.net/browse/ATT-2192)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-CON-007` (Refinement)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & User Value

### Current Deficiencies
At the conclusion of a tracked workout session, aTrainingTracker currently alerts athletes about low sensor batteries via a legacy modal dialog (`AlertDialog.Builder`) launched directly from `MainActivityWithNavigation.kt` (`checkBatteryStatus()`):
1. **Modal Screen-Transition Disruption**: When the workout finishes, the app transitions navigation from the cockpit to the workout history list (`R.id.drawer_workouts`). Popping up a modal `AlertDialog` over this navigation transition interrupts the athlete's cognitive flow, introduces visual glitching during Compose reconstruction, and causes popup fatigue.
2. **Ephemeral Warning Loss**: If the athlete reflexively taps "Cancel" or taps outside the dialog, or if the phone screen shuts off or is pocketed immediately after hitting Stop, the warning is permanently gone without any persistent visual reminder.
3. **Session-Agnostic Querying**: `checkBatteryStatus()` queries `DevicesDatabaseManager.getCriticalBatteryDevices(CRITICAL_BATTERY_LEVEL)` with a hardcoded $30\%$ threshold across *all* paired devices in the SQLite database—including inactive sensors stored in a drawer that were never used during the workout. This causes irrelevant false alarms.

### Proposed User Value
Modernize post-workout low sensor battery alerting into an unobtrusive, multi-channel approach:
1. **In-App Material 3 Snackbar**: Render a clean, non-blocking M3 Snackbar on the post-workout destination screen (`WorkoutSummariesTabbedScreen`), formatted as `⚠️ [Sensor Name]: Akku schwach ([X]%)` with a direct action button `[Zu Sensoren]` that navigates to the sensor management screen (`R.id.drawer_my_sensors`).
2. **Persistent System Notification**: Post an Android system notification to the notification shade on a dedicated `NotificationChannel` (`NOTIFICATION_CHANNEL__SENSOR_BATTERY`). Tapping the notification deep-links directly to "Meine Sensoren". The notification persists on the lock screen until dismissed or acted upon.
3. **Session-Aware Filtering**: Only warn about sensors that were actively connected and receiving telemetry during the just-completed workout session (`ActiveDevicesDbHelper`), using a realistic critical threshold ($\le 20\%$).
4. **Quiet on Normal Batteries**: When all connected sensors are above $20\%$, zero dialogs, snackbars, or notifications are generated.

---

## 2. Technical Architecture & Forensic Root Cause

### 1. Existing Legacy Mechanism
In `MainActivityWithNavigation.kt`:
```kotlin
internal val mStopTrackingReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        mDrawerController.startTrackingTitleRes = R.string.Start
        checkBatteryStatus()
    }
}

protected fun checkBatteryStatus() {
    val criticalBatteryDevices = DevicesDatabaseManager.getInstance(applicationContext).getCriticalBatteryDevices(CRITICAL_BATTERY_LEVEL)
    if (criticalBatteryDevices.isNotEmpty()) {
        val stringList = LinkedList<String>()
        for (device in criticalBatteryDevices) {
            stringList.add(
                getString(
                    R.string.critical_battery_message_format,
                    device.name,
                    getString(BatteryStatusHelper.getBatteryStatusNameId(device.batteryPercentage))
                )
            )
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle(if (criticalBatteryDevices.size == 1) R.string.check_battery_status_title_1 else R.string.check_battery_status_title_many)
        builder.setItems(stringList.toTypedArray()) { _, which ->
            val deviceId = criticalBatteryDevices[which].deviceId
            val devicesDatabaseManager = DevicesDatabaseManager.getInstance(applicationContext)
            val deviceType = devicesDatabaseManager.getDeviceType(deviceId)
            val editDeviceDialogFragment = EditDeviceFragmentFactory.create(deviceId, deviceType)
            editDeviceDialogFragment.show(supportFragmentManager, "EditDeviceDialogFragment")
        }
        builder.create().show()
    }
}
```

### 2. Session Awareness via `ActiveDevicesDbHelper` & Asynchronous I/O Dispatch
During workout finalization in `TrackerService.java:endWorkout()`:
```java
// Active devices are recorded in ActiveDevices.TABLE before TRACKING_FINISHED_INTENT is broadcast:
SQLiteDatabase activeDevicesDb = new ActiveDevicesDbHelper(this).getWritableDatabase();
ContentValues values = new ContentValues();
values.put(ActiveDevices.WORKOUT_ID, mWorkoutID);
if (mBanalService != null) {
    for (long deviceDbId : mBanalService.getDatabaseIdsOfActiveRemoteDevices()) {
        values.put(ActiveDevices.DEVICE_DB_ID, deviceDbId);
        activeDevicesDb.insert(ActiveDevices.TABLE, null, values);
    }
}
```
When `TrackerService.TRACKING_FINISHED_INTENT` is broadcast with extra `WORKOUT_ID`, `ActiveDevicesDbHelper.getDatabaseIdsOfActiveDevices(workoutId)` reliably yields the exact database IDs of all sensors active in that session.

**Thread Safety & Non-Blocking I/O Invariant**:
- Querying `ActiveDevicesDbHelper.getDatabaseIdsOfActiveDevices(workoutId)` and querying `DevicesDatabaseManager` for device names and battery percentages is executed strictly on a background coroutine dispatcher (`Dispatchers.IO`).
- This guarantees zero SQLite disk I/O on the Android Main Looper during the critical Compose screen transition from the tracking cockpit to `WorkoutSummariesTabbedScreen`.

### 3. Decoupled In-App Alerting & Lifecycle State Retention (`WorkoutNavigationEvents`)
`WorkoutNavigationEvents` acts as the central decoupled event bus between background broadcast receivers/activities and Compose screens.
To guarantee that the in-app Snackbar is never dropped if navigation completes after or before UI observation begins, we use a sticky replay buffer pattern:
```kotlin
data class LowBatteryAlert(
    val deviceCount: Int,
    val primaryDeviceName: String,
    val primaryBatteryPercentage: Int,
    val allDeviceNames: List<String>
)

object WorkoutNavigationEvents {
    // Isolated sticky shared flow with replay = 1 to survive screen transition latency
    private val _lowBatteryAlert = MutableSharedFlow<LowBatteryAlert?>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val lowBatteryAlert = _lowBatteryAlert.asSharedFlow()

    fun triggerLowBatteryAlert(alert: LowBatteryAlert) {
        _lowBatteryAlert.tryEmit(alert)
    }

    fun consumeLowBatteryAlert() {
        _lowBatteryAlert.tryEmit(null)
    }
}
```
- **Lifecycle & Background Handling**: If the athlete backgrounds the app immediately upon tapping Stop, the Android system notification is posted immediately (independent of UI lifecycle). When the athlete returns to the app, the sticky `replay = 1` buffer delivers the `LowBatteryAlert` to `WorkoutSummariesTabbedScreen` upon composable composition.
- **Consumption Safety**: Once displayed, `WorkoutSummariesTabbedScreen` invokes `WorkoutNavigationEvents.consumeLowBatteryAlert()`, clearing the replay buffer and preventing stale repeated popups on subsequent recompositions.
- **Stream Isolation**: The new `_lowBatteryAlert` flow is completely isolated from existing flows (`_navigateToEdit`, `_navigateToCluster`), guaranteeing zero backpressure or interference with existing navigation events.

### 4. Dedicated System Notification (`SensorBatteryNotificationManager`)
- Dedicated `NotificationChannel`: `NOTIFICATION_CHANNEL__SENSOR_BATTERY = "NOTIFICATION_CHANNEL__SENSOR_BATTERY"` configured with `IMPORTANCE_DEFAULT`.
- Notification ID: `NOTIFICATION_ID_SENSOR_BATTERY = 4300`.
- Intent: Launches `MainActivityWithNavigation` with extra `SELECTED_FRAGMENT = SelectedFragment.SENSORS.name` or `EXTRA_DRAWER_ITEM_ID = R.id.drawer_my_sensors`.
- `setAutoCancel(true)`: Clicking the notification clears it and opens "Meine Sensoren".
- Target API compatibility: Checks `NotificationManagerCompat.areNotificationsEnabled()` and `Manifest.permission.POST_NOTIFICATIONS` before dispatching, degrading gracefully to the in-app Snackbar if system notifications are suppressed.

### 5. Schema & Telemetry Type Fidelity
- The battery percentage values fetched from `DevicesDbHelper.LAST_BATTERY_PERCENTAGE` remain primitive `Int` values representing $0..100\%$. Zero type conversions, rounding drifts, or floating-point conversions are introduced into the database layer.

---

## 3. Chesterton's Fence Requirement Archaeology

### Archaeology Findings
1. **Original Requirement ID & Target**:
   - `REQ-CON-007` (*Notify user about low battery levels in sensors*), targeting `MainActivityWithNavigation.kt` and `BatteryStatusHelper.java`.
2. **Historical Origin & Commit Trace**:
   - Commit `ac493d2bdf652746b9177a2c574b220285b3bf8b` (Initial ASPICE baseline import), ticket `ATT-2192`, Sprint `2026-40.16`.
3. **Root Reason for Existing Formulation**:
   - In the initial Android architecture, an immediate modal `AlertDialog` was used to notify the athlete upon stopping tracking. However, modal dialogs interrupt the transition to the workout list, cause popup fatigue, and are lost if dismissed reflexively or if the device is immediately pocketed.
4. **Preservation of Core Invariants**:
   - Critical battery evaluation threshold ($\le 20\%$), sensor name mapping, non-blocking asynchronous dispatch, navigation to sensor settings (`drawer_my_sensors`), and 9-language translation parity MUST NOT be broken. Normal battery levels ($> 20\%$) remain silent without alerts.

---

## 4. Scope Bounding (ATT-1250)

### In-Scope
1. Remove modal `AlertDialog.Builder` and `EditDeviceDialogFragment` invocation in `MainActivityWithNavigation.kt`.
2. Implement session-aware battery evaluation for sensors active during the workout session (`ActiveDevicesDbHelper`) with threshold $\le 20\%$.
3. Implement `SensorBatteryNotificationManager` with `NOTIFICATION_CHANNEL__SENSOR_BATTERY`, posting a persistent system notification with tap navigation to "Meine Sensoren".
4. Add `LowBatteryAlert` event in `WorkoutNavigationEvents` and display an M3 Snackbar in `WorkoutSummariesTabbedScreen` with action button navigating to `drawer_my_sensors`.
5. Support single-sensor and multi-sensor localized messages across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. Verify normal battery neutrality (no alerts when all sensors $> 20\%$).

### Out-of-Scope
1. In-workout live battery telemetry sensors (`PHONE_BATTERY`, `BATTERY_REMAINING_TIME`).
2. Redesigning the sensor list screen (`DevicesTabbedScreen` / `DeviceItem`).
3. Battery charging animations or notifications for phone battery.
4. Automated background Bluetooth scanning outside of workout tracking.

---

## 5. Requirement Traceability Matrix

| Acceptance Criterion | Description | Architectural Component | Verification Test Case |
| :--- | :--- | :--- | :--- |
| **AC-1: No Modal Popup on Workout Stop** | Modal `AlertDialog` completely eliminated during workout stop transition | `MainActivityWithNavigation.kt` (`checkBatteryStatus()`) | `SensorBatteryAlertTest.kt`: Verify no dialog builder invoked |
| **AC-2: In-App Snackbar Presentation** | Non-blocking M3 Snackbar displayed on `WorkoutSummariesTabbedScreen` with sensor name, battery %, and action button navigating to `drawer_my_sensors` | `WorkoutSummariesTabbedScreen.kt`, `WorkoutNavigationEvents.kt` | `WorkoutNavigationEventsTest.kt`, `SensorBatteryAlertTest.kt` |
| **AC-3: Persistent System Notification** | Notification posted on `NOTIFICATION_CHANNEL__SENSOR_BATTERY` with tap intent to "Meine Sensoren" | `SensorBatteryNotificationManager.kt`, `TrainingApplication.java` | `SensorBatteryNotificationManagerTest.kt` |
| **AC-4: Normal Battery Neutrality** | When all active sensors have battery $> 20\%$, zero snackbars or notifications are dispatched | `MainActivityWithNavigation.kt`, `SensorBatteryNotificationManager.kt` | `SensorBatteryAlertTest.kt`: Verify 0 dispatches |

---

## 6. Risk Assessment & Mitigation

| Risk | Impact | Mitigation |
| :--- | :--- | :--- |
| **Notification Permission on Android 13+ (TIRAMISU)** | System notification blocked if `POST_NOTIFICATIONS` not granted | Check `NotificationManagerCompat.areNotificationsEnabled()` before posting; the in-app Snackbar serves as a guaranteed fallback. |
| **Race between Workout Stop and DB Write** | `ActiveDevicesDbHelper` not populated yet when intent received | In `TrackerService.java:endWorkout()`, `activeDevicesDb.insert` executes synchronously before `sendBroadcast(TRACKING_FINISHED_INTENT)`. `evaluateSessionBattery` runs after `TRACKING_FINISHED_INTENT` on `Dispatchers.IO`. |
| **Snackbar Dropped Due to Screen Transition Latency** | UI collection begins after shared flow emission | `WorkoutNavigationEvents._lowBatteryAlert` configured with `replay = 1` and `consumeLowBatteryAlert()` explicit reset. |

---

## 7. Deliverable Conclusion
The problem domain, forensic root cause, Chesterton's Fence archaeology, lifecycle retention, threading safety, and traceability matrix are fully documented. We are ready to re-submit for Gate 1 audit.
