# Stage 2: Requirement & Test Specification - ATT-2192: Modernized Post-Workout Low Sensor Battery Alerting

**Ticket**: [ATT-2192](https://rainerblind.atlassian.net/browse/ATT-2192)  
**Sub-task**: [ATT-2442](https://rainerblind.atlassian.net/browse/ATT-2442) (`[Req & Test Spec] Modernized Post-Workout Low Sensor Battery Alerting`)  
**Parent Epic**: [ATT-73](https://rainerblind.atlassian.net/browse/ATT-73) (*[Epic] Sensor Management*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-CON-007` (Modernized)  
**Test Mapping**: `TST-CON-010`  
**Branch**: `feature/ATT-2192`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Traceability Matrix

| Requirement ID | Requirement Title | Test Specification ID | Verification File(s) | Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-CON-007** | Modernized Post-Workout Low Sensor Battery Alerting (In-App Snackbar & System Notification) | **TST-CON-010** | `SensorBatteryAlertTest.kt`, `SensorBatteryNotificationManagerTest.kt`, `WorkoutNavigationEventsTest.kt` | **In Progress** |

---

## 2. Requirement Specification: REQ-CON-007

### 2.1 Formal Definition
The system SHALL notify athletes about low peripheral sensor battery levels following a completed workout session via non-blocking dual-channel alerts, eliminating disruptive modal dialogs (ATT-2192):

1. **Elimination of Modal Disruption**:
   - The system SHALL NOT display modal dialogs (`AlertDialog`) upon workout finalization or stopping (`checkBatteryStatus()`).
2. **Session-Aware Critical Battery Evaluation**:
   - Upon tracking finalization (`TRACKING_FINISHED_INTENT`), the system SHALL evaluate the battery levels of remote sensors that were actively connected during the workout session via `ActiveDevicesDbHelper.getDatabaseIdsOfActiveDevices(workoutId)`.
   - Evaluation SHALL execute asynchronously on a background I/O dispatcher (`Dispatchers.IO`) to eliminate SQLite query latency on the Main Looper during screen transitions.
   - A sensor SHALL be classified as critical if its recorded `LAST_BATTERY_PERCENTAGE` is $\ge 0\%$ AND $\le 20\%$ (`CRITICAL_BATTERY_LEVEL = 20`).
3. **In-App Material 3 Snackbar Presentation**:
   - When one or more session sensors are critical, the system SHALL emit a sticky `LowBatteryAlert` event via `WorkoutNavigationEvents` (`replay = 1`, `DROP_OLDEST`).
   - Upon composition of the post-workout destination screen (`WorkoutSummariesTabbedScreen`), the screen SHALL collect the alert and display a non-blocking Material 3 `Snackbar`.
   - Format: `⚠️ [Sensor Name]: Akku schwach ([X]%)` (single sensor) or `⚠️ [N] Sensoren: Akku schwach ([Names])` (multiple sensors).
   - Action button: `[Zu Sensoren]` (EN: `[View Sensors]`). Tapping the action SHALL invoke `navigateToDrawerItem(R.id.drawer_my_sensors)`.
   - Once displayed, the alert SHALL be cleared via `WorkoutNavigationEvents.consumeLowBatteryAlert()` to prevent duplicate popups upon recomposition.
4. **Persistent Android System Notification**:
   - The system SHALL post an Android system notification on a dedicated notification channel `NOTIFICATION_CHANNEL__SENSOR_BATTERY` (`"NOTIFICATION_CHANNEL__SENSOR_BATTERY"`, importance `IMPORTANCE_DEFAULT`).
   - Notification ID: `NOTIFICATION_ID_SENSOR_BATTERY = 4300`.
   - Title: Localized `R.string.sensor_battery_notification_title_1` or `R.string.sensor_battery_notification_title_many`.
   - Text: Localized sensor name(s) and battery percentage.
   - Tap Action: PendingIntent targeting `MainActivityWithNavigation` with extra `SELECTED_FRAGMENT = SelectedFragment.SENSORS.name` / `EXTRA_DRAWER_ITEM_ID = R.id.drawer_my_sensors` to launch directly into "Meine Sensoren".
   - Behavior: `setAutoCancel(true)`, persisting across screen sleep and lock screen until dismissed or tapped.
5. **Normal Battery Neutrality**:
   - When all sensors active in the workout have battery $> 20\%$ (or if no remote sensors were connected), the system SHALL NOT post any system notification or in-app Snackbar.
6. **9-Language Localization Parity**:
   - All notification channel names, descriptions, notification titles, notification text, snackbar messages, and action buttons SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-007` (*Notify user about low battery levels in sensors*), targeting `MainActivityWithNavigation.kt` and `BatteryStatusHelper.java`.
* **Historical Origin & Commit Trace**: Commit `ac493d2bdf652746b9177a2c574b220285b3bf8b` (Initial ASPICE baseline import), ticket `ATT-2192`, Sprint `2026-40.16`.
* **Root Reason for Existing Formulation**: In the initial Android architecture, an immediate modal `AlertDialog` was used to notify the athlete upon stopping tracking. However, modal dialogs interrupt the transition to the workout list, cause popup fatigue, and are lost if dismissed reflexively or if the device is immediately pocketed.
* **Preservation of Core Invariants**: Critical battery evaluation threshold ($\le 20\%$), sensor name mapping, non-blocking asynchronous dispatch, navigation to sensor settings (`drawer_my_sensors`), and 9-language translation parity MUST NOT be broken. Normal battery levels ($> 20\%$) remain silent without alerts.

### 2.2 Acceptance Criteria (Given-When-Then)
- **AC-1 (Disruption-Free Workout Finalization)**:
  - *Given* an active workout with a sensor whose battery level is 15%,
  - *When* the workout is stopped and finalized,
  - *Then* no modal `AlertDialog` SHALL be shown over the screen transition, an Android system notification SHALL be posted on `NOTIFICATION_CHANNEL__SENSOR_BATTERY`, and an M3 Snackbar SHALL appear on `WorkoutSummariesTabbedScreen` offering navigation to sensor management.
- **AC-2 (Normal Battery Neutrality)**:
  - *Given* an active workout where all sensors have battery $\ge 50\%$,
  - *When* the workout is stopped,
  - *Then* zero system notifications and zero snackbars SHALL be dispatched.
- **AC-3 (Actionable Sensor Navigation)**:
  - *Given* an athlete tapping the notification or Snackbar action,
  - *When* triggered,
  - *Then* the app SHALL navigate directly to "Meine Sensoren" (`R.id.drawer_my_sensors`).

---

## 3. Test Specification: TST-CON-010

### 3.1 Verification Scope
The test suite validates modal dialog removal, session-aware critical battery evaluation, system notification generation, M3 snackbar emission and event consumption, normal battery neutrality, and 9-language translation parity.

### 3.2 Test Cases

#### Case 1: Modal Dialog Elimination Unit Test (`SensorBatteryAlertTest.kt`)
- **Objective**: Verify that `MainActivityWithNavigation.checkBatteryStatus` does not instantiate or display any `AlertDialog`.
- **Assertions**: Zero modal dialogs invoked during workout stop transition.

#### Case 2: Session-Aware Battery Evaluation Unit Test (`SensorBatteryAlertTest.kt`)
- **Objective**: Verify that only sensors actively connected to the finalized workout session with battery $\le 20\%$ trigger alerts.
- **Setup**: Inactive sensors in database with low battery and active sensors with normal battery.
- **Assertions**: Only active session sensors with $\le 20\%$ are flagged.

#### Case 3: In-App Material 3 Snackbar & Lifecycle Event Test (`WorkoutNavigationEventsTest.kt`)
- **Objective**: Verify sticky `LowBatteryAlert` event emission and consumption.
- **Assertions**: Event is received once by the summary screen, cleared upon consumption, and navigates to `R.id.drawer_my_sensors`.

#### Case 4: System Notification Construction & Channel Test (`SensorBatteryNotificationManagerTest.kt`)
- **Objective**: Verify notification properties, channel ID `NOTIFICATION_CHANNEL__SENSOR_BATTERY`, notification ID 4300, and pending intent payload.
- **Assertions**: Notification is properly constructed with correct title, body, and navigation pending intent.

#### Case 5: Normal Battery Neutrality Test (`SensorBatteryAlertTest.kt`)
- **Objective**: Verify that when all active sensors have battery $> 20\%$, zero notifications and zero snackbars are generated.
- **Assertions**: Zero notifications posted, zero events emitted.

#### Case 6: 9-Language Localization Audit
- **Objective**: Verify 100% presence and validity of all sensor battery string resources across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
- **Assertions**: Zero missing strings or formatting defects.

#### Case 7: Full Clean-Room Regression
- **Objective**: Run `./gradlew testDebugUnitTest` across all modules.
- **Assertions**: 100% test pass rate with zero regressions.
