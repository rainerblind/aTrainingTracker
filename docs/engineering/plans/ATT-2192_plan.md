# Stage 3: Implementation Plan - ATT-2192: Modernized Post-Workout Low Sensor Battery Alerting

**Ticket**: [ATT-2192](https://rainerblind.atlassian.net/browse/ATT-2192)  
**Sub-task**: [ATT-2448](https://rainerblind.atlassian.net/browse/ATT-2448) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-CON-007`  
**Test Mapping**: `TST-CON-010`  
**Branch**: `feature/ATT-2192`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

At the conclusion of a workout, aTrainingTracker historically alerted athletes about low peripheral sensor batteries using a blocking Android modal `AlertDialog` in `MainActivityWithNavigation.kt` (`checkBatteryStatus()`). This modal popup is disruptive: it halts the screen transition to the workout list, causes popup fatigue, and is permanently lost if dismissed reflexively or if the athlete immediately pockets the device.

To modernize this experience according to ASPICE standards and `REQ-CON-007`, the modal dialog is eliminated in favor of a dual-channel alerting strategy:
1. **In-App Material 3 Snackbar**: An unobtrusive snackbar on the post-workout destination screen (`WorkoutSummariesTabbedScreen`) featuring an actionable button navigating directly to "Meine Sensoren" (`R.id.drawer_my_sensors`).
2. **Persistent System Notification**: A standard Android notification posted to a dedicated notification channel (`NOTIFICATION_CHANNEL__SENSOR_BATTERY`, ID `4300`) with `setAutoCancel(true)` and a PendingIntent launching sensor settings.
3. **Session-Aware Filtering & Practical Threshold**: Evaluating only sensors actively connected to the completed workout via `ActiveDevicesDbHelper.getDatabaseIdsOfActiveDevices(workoutId)` with an industry-standard threshold of $\le 20\%$ (eliminating false alarms at 30%).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-007` (*Modernized Post-Workout Low Sensor Battery Alerting (In-App Snackbar & System Notification)*)
* **Test Mapping**: `TST-CON-010` (*Modernized Post-Workout Low Sensor Battery Alerting Verification*)
* **Verification Files**:
  - `SensorBatteryAlertTest.kt`: Unit tests verifying session-aware evaluation, modal dialog removal, and neutrality.
  - `SensorBatteryNotificationManagerTest.kt`: Notification channel, properties, and intent verification.
  - `WorkoutNavigationEventsTest.kt`: SharedFlow event emission and consumption tests.
  - `TranslationParityTest.kt`: 9-language localization audit.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Modal Interruptions**: `AlertDialog` is never instantiated or displayed upon workout finalization or stopping.
2. **Critical Threshold Alignment**: Threshold is adjusted from legacy 30% to $\le 20\%$ (`CRITICAL_BATTERY_LEVEL = 20`) to eliminate premature warnings.
3. **Thread Safety & Non-Blocking Transitions**: All SQLite evaluations (`ActiveDevicesDbHelper`, `DevicesDatabaseManager`) execute on `Dispatchers.IO` in background coroutines, ensuring zero UI thread jank during screen transitions.
4. **Normal Battery Neutrality**: Zero notifications and zero snackbars are dispatched when connected sensors have $> 20\%$ battery (or when no remote sensors were connected).
5. **Actionable Direct Navigation**: Both notification tap and snackbar action open "Meine Sensoren" (`R.id.drawer_my_sensors` / `SelectedFragment.SENSORS`).
6. **9-Language Parity**: All string resources, notification channel names, notification descriptions, snackbar copy, and action labels maintain strict translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 4. Proposed Architectural Changes

### Component 1: `SensorBatteryNotificationManager.kt`
- Location: `com.atrainingtracker.trainingtracker.notifications.SensorBatteryNotificationManager`
- Purpose: Encapsulates channel registration (`NOTIFICATION_CHANNEL__SENSOR_BATTERY`), notification construction, localized string formatting, and dispatch via `NotificationManagerCompat`.
- Injects `NotificationManagerCompat` for clean unit testability.

### Component 2: `WorkoutNavigationEvents.kt`
- Extend decoupled event bus with `LowBatteryAlert` data class:
  ```kotlin
  data class LowBatteryAlert(
      val message: String,
      val deviceNames: List<String> = emptyList()
  )
  ```
- Exposes `val lowBatteryAlert: SharedFlow<LowBatteryAlert?>` (`replay = 1`, `BufferOverflow.DROP_OLDEST`).
- Provides `triggerLowBatteryAlert(alert: LowBatteryAlert)` and `consumeLowBatteryAlert()`.

### Component 3: `MainActivityWithNavigation.kt`
- Remove legacy `checkBatteryStatus()` `AlertDialog.Builder` popup logic.
- Add `SelectedFragment.SENSORS` to `SelectedFragment` enum and support `EXTRA_DRAWER_ITEM_ID` in `handleIntent`.
- In `mTrackingStoppedReceiver`: extract `workoutId`, launch coroutine on `Dispatchers.IO`, query active devices via `ActiveDevicesDbHelper`, evaluate critical battery ($\le 20\%$) from `DevicesDatabaseManager`, post system notification via `SensorBatteryNotificationManager`, and emit `LowBatteryAlert` event.

### Component 4: `WorkoutSummariesTabbedScreen.kt`
- Collect `WorkoutNavigationEvents.lowBatteryAlert`.
- When non-null, display Material 3 `Snackbar` via existing `snackbarHostState` with action button `R.string.sensor_battery_action_view_sensors`.
- Upon action click, trigger `(activity as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors)`.
- Consume alert via `WorkoutNavigationEvents.consumeLowBatteryAlert()`.

### Component 5: Localization & Resources
- Add notification channel and alert string resources to `values/strings.xml`, `values-de/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml`, `values-it/strings.xml`, `values-ja/strings.xml`, `values-nl/strings.xml`, `values-pl/strings.xml`, and `values-pt/strings.xml`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: String Resources & 9-Language Localization
* Add strings:
  - `notification_channel_name__sensor_battery`
  - `notification_channel_description__sensor_battery`
  - `sensor_battery_notification_title_1`
  - `sensor_battery_notification_title_many`
  - `sensor_battery_snackbar_single`
  - `sensor_battery_snackbar_many`
  - `sensor_battery_action_view_sensors`
* Provide translations across all 9 locales.

### Step 2: Decoupled Navigation Bus (`WorkoutNavigationEvents.kt`)
* Define `LowBatteryAlert` model.
* Add sticky `lowBatteryAlert` SharedFlow, `triggerLowBatteryAlert`, and `consumeLowBatteryAlert`.

### Step 3: Notification Manager (`SensorBatteryNotificationManager.kt`)
* Implement notification channel setup, notification construction with channel ID `NOTIFICATION_CHANNEL__SENSOR_BATTERY` and ID `4300`.
* Attach PendingIntent with target `MainActivityWithNavigation` and `EXTRA_DRAWER_ITEM_ID = R.id.drawer_my_sensors`.

### Step 4: Refactor `MainActivityWithNavigation.kt`
* Remove modal `AlertDialog` in `checkBatteryStatus()`.
* Add `SelectedFragment.SENSORS` and handle navigation extras in `handleIntent`.
* Implement asynchronous post-workout battery check on `Dispatchers.IO`.

### Step 5: Post-Workout Snackbar in `WorkoutSummariesTabbedScreen.kt`
* Collect `lowBatteryAlert` and display M3 snackbar with action.

### Step 6: Targeted Unit Tests
* `SensorBatteryAlertTest.kt`
* `SensorBatteryNotificationManagerTest.kt`
* `WorkoutNavigationEventsTest.kt`
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.notifications.*" --tests "com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEventsTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests verifying all 6 acceptance cases, followed by full clean-room suite `./gradlew testDebugUnitTest` and 9-language translation parity verification.
* **Rollback**: Branch isolation (`feature/ATT-2192`) allows complete atomic revert via `git reset` if unexpected regressions emerge.
