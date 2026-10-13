# Stage 1 Analysis: ATT-3313 - Display low sensor battery alert snackbars on workout start and during in-ride tracking

**Ticket**: [ATT-3313](https://rainerblind.atlassian.net/browse/ATT-3313)  
**Sub-task**: [ATT-3412](https://rainerblind.atlassian.net/browse/ATT-3412) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Branch**: `feature/ATT-3313`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends and extends `REQ-UI-350` (*Edge-to-Edge System Navigation Bar Inset Padding for Post-Workout Alert Snackbars and Action Controls*, `ATT-3167`) and `REQ-CON-007` (*Modernized Post-Workout Low Sensor Battery Alerting*, `ATT-2192`), targeting `TrackingTabsScreen.kt`, `MainActivityWithNavigation.kt`, and `WorkoutNavigationEvents.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.16 (`ATT-2192`, commit `ac493d2b` baseline / post-workout snackbar).
   - Sprint 2026-41.9 (`ATT-3167`, commit `53b219ac` navigation bar padding on aftermath screens).
3. **Root Reason for Existing Formulation**:
   - In ATT-2192 and ATT-3167, low battery alerting was intentionally scoped to post-workout aftermath to prioritize replacing the legacy modal `AlertDialog` without risking tracking UI instability.
   - User feedback during the Sprint 2026-41.10 Joint Review demonstrated that discovering a dead sensor after a ride is too late; alerting must also occur at workout launch and during active riding.
4. **Preservation of Core Invariants**:
   - The 20% critical threshold (`CRITICAL_BATTERY_LEVEL = 20`) is strictly preserved.
   - Non-blocking Material 3 snackbar ergonomics (no modal dialogs during or after tracking) are strictly preserved.
   - System navigation bar inset padding (`navigationBarsPadding()`) per REQ-UI-350 / Rule 23 is strictly preserved.
   - Action navigation to `drawer_my_sensors` and sticky event consumption via `consumeLowBatteryAlert()` are strictly preserved.
   - 100% clean-room test pass rate across all modules is strictly preserved.

---

## 1. Problem Statement & Motivation

In Sprint 2026-40.16 (`ATT-2192`) and Sprint 2026-41.9 (`ATT-3167`), low sensor battery alerting was modernized from a blocking modal `AlertDialog` into a non-disruptive dual-channel alert: an Android system notification and an in-app Material 3 `Snackbar`. However, this snackbar alerting mechanism is currently wired exclusively to the post-workout aftermath destination (`WorkoutSummariesTabbedScreen.kt`), triggered only when `TrackerService.TRACKING_FINISHED_INTENT` is received upon stopping a ride.

During the Sprint 2026-41.10 Joint Review of `ATT-3167`, athletes identified a significant usability limitation:
1. **Pre-Start / Workout Start Blind Spot**: If an athlete starts a ride with an already depleted or critical sensor ($\le 20\%$), no alert is presented on the tracking screen.
2. **In-Ride Battery Drop Blind Spot**: If an active sensor's battery drops below the critical threshold ($\le 20\%$) during live tracking (e.g. 1 hour into a 4-hour ride), no visual warning appears on the tracking HUD or sensor grid.

In both cases, the athlete only discovers that a sensor died *after* finishing the entire workout, having lost critical telemetry (heart rate, cadence, or power) during the ride.

The goal of ATT-3313 is to extend low sensor battery alert snackbars to the workout start lifecycle and live in-ride tracking screen, ensuring athletes receive timely, non-blocking warnings while riding, fully compliant with system navigation bar insets (`Modifier.navigationBarsPadding()`).

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Workflow Architecture & Call-Site Audit
A forensic audit of sensor battery alerting components revealed the following architectural gaps:

1. **`MainActivityWithNavigation.kt`**:
   - `checkSensorBatteryAfterWorkout(workoutId)` is invoked solely by `mTrackingStoppedReceiver` (line 240).
   - In `mStartTrackingReceiver` (line 217), the receiver responds to `REQUEST_START_TRACKING` merely by updating the drawer title: `mDrawerController.startTrackingTitleRes = R.string.Tracking`. It performs zero evaluation of paired or active sensor battery states.
2. **`TrackingTabsScreen.kt` (Tracking HUD & Cockpit)**:
   - `TrackingTabsScreen.kt` hosts the HorizontalPager containing the Control tab (Page 0) and all configured sensor grid / map tabs.
   - It contains a floating `LapButton` positioned at `Alignment.BottomCenter` with `Modifier.navigationBarsPadding().padding(bottom = 8.dp)`.
   - However, `TrackingTabsScreen.kt` possesses **no `SnackbarHost` or `SnackbarHostState`**. Consequently, even if `WorkoutNavigationEvents.triggerLowBatteryAlert()` is called during tracking, the tracking screen cannot render the snackbar.
3. **Telemetry & Live Battery Drop Detection**:
   - In ATT-3312, `DeviceDataRepository.deviceBatteryUpdates` (`SharedFlow<Pair<Long, Int>>`) and `DevicesDatabaseManager.setBatteryPercentage(deviceId, percentage)` were introduced, broadcasting live battery updates immediately.
   - However, there is no active observer checking whether an incoming battery update for a currently active session device crosses the critical threshold ($\le 20\%$) during a workout.
   - Without session deduplication, continuous battery telemetry frames could trigger repeated snackbar alerts for the same sensor, causing notification spam during a ride.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. *Workout Start Alert*: When starting a workout session (`REQUEST_START_TRACKING`), evaluate paired and active sensors. If any sensor has a battery percentage $\le 20\%$ (and $\ge 0\%$), emit a non-blocking `LowBatteryAlert` event via `WorkoutNavigationEvents`.
  2. *In-Ride Live Drop Alert*: When an active session sensor transitions to $\le 20\%$ during an active ride, emit a `LowBatteryAlert` event. Enforce session deduplication so each sensor triggers at most once per workout.
  3. *Tracking Screen Snackbar Host*: Integrate `SnackbarHostState` and `SnackbarHost` into `TrackingTabsScreen.kt`, observing `WorkoutNavigationEvents.lowBatteryAlert`.
  4. *Navigation Bar Inset Compliance & Layout Harmony*: Apply `Modifier.navigationBarsPadding()` to the snackbar host in `TrackingTabsScreen.kt` and ensure vertical clearance above the floating `LapButton` (when visible) so controls do not overlap.
  5. *Action Target Navigation*: Ensure tapping `[Zu Sensoren]` / `[View Sensors]` on the tracking screen snackbar navigates cleanly to "Meine Sensoren" (`R.id.drawer_my_sensors`) and consumes the alert.
  6. *Localization & Testing*: Maintain 100% 9-language parity across EN, DE, ES, FR, IT, JA, NL, PL, PT and verify with clean-room unit tests.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying post-workout summary alerting in `WorkoutSummariesTabbedScreen.kt` (already compliant per REQ-UI-350).
  - Altering the Android system notification channel or notification ID (`NOTIFICATION_ID_SENSOR_BATTERY = 4300`).
  - Altering the critical battery threshold constant ($\le 20\%$).
  - Modifying GATT Bluetooth LE discovery or ANT+ connection management.

---

## 4. Architectural Strategy & High-Level Solution

```
                                      +---------------------------------------------+
                                      |   Workout Start (REQUEST_START_TRACKING)   |
                                      +---------------------------------------------+
                                                             |
                                                             v
+-----------------------------+     checkSensorBatteryOnStart() (paired/active sensors <= 20%)
| Incoming Battery Telemetry  |                              |
| (<= 20% & newly critical)   |                              v
+-----------------------------+               +-------------------------------+
               |                              | WorkoutNavigationEvents       |
               +----------------------------> | triggerLowBatteryAlert(...)   |
                                              +-------------------------------+
                                                             |
                                      +----------------------+----------------------+
                                      | (during workout)                            | (post workout)
                                      v                                             v
                       +-----------------------------+               +-----------------------------+
                       | TrackingTabsScreen.kt       |               | WorkoutSummariesTabbedScreen|
                       | - SnackbarHost              |               | - SnackbarHost              |
                       | - navigationBarsPadding()   |               | - navigationBarsPadding()   |
                       | - LapButton clearance       |               +-----------------------------+
                       +-----------------------------+
```

### 4.1 Workout Start Evaluation (`MainActivityWithNavigation.kt`)
In `MainActivityWithNavigation.kt`, introduce `checkSensorBatteryOnStart()`:
- Executed asynchronously on `Dispatchers.IO` when `mStartTrackingReceiver` receives `REQUEST_START_TRACKING`.
- Queries `DevicesDatabaseManager.getCriticalBatteryDevices(CRITICAL_BATTERY_LEVEL)` filtered to paired remote devices (`isPaired > 0`).
- If any paired devices have critical battery, formats the localized snackbar string and triggers `WorkoutNavigationEvents.triggerLowBatteryAlert(...)`.

### 4.2 In-Ride Live Drop Evaluation
- Monitor `DeviceDataRepository.deviceBatteryUpdates` during active tracking (`TrackingMode.TRACKING` / `PAUSED`).
- Maintain an in-memory session set `alertedSensorIds: MutableSet<Long>`.
- When an active sensor reports battery $\le 20\%$ for the first time during the current workout:
  - Add sensor ID to `alertedSensorIds`.
  - Format the localized alert string and dispatch `WorkoutNavigationEvents.triggerLowBatteryAlert(...)`.
- Reset `alertedSensorIds` when workout finishes.

### 4.3 Tracking Screen Presentation (`TrackingTabsScreen.kt`)
- In `TrackingTabsScreen.kt`, instantiate `val snackbarHostState = remember { SnackbarHostState() }`.
- Launch a coroutine collecting `WorkoutNavigationEvents.lowBatteryAlert`:
  - Display snackbar with `SnackbarDuration.Long` and action `[Zu Sensoren]` (`R.string.sensor_battery_action_view_sensors`).
  - If action clicked: `(context as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors)`.
  - Consume alert: `WorkoutNavigationEvents.consumeLowBatteryAlert()`.
- Add `SnackbarHost` inside the root layout:
  - `modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (shouldShowLapButton) 64.dp else 8.dp)`.
  - Consumes Material 3 `primaryContainer` and `onPrimaryContainer` styling matching `WorkoutSummariesTabbedScreen.kt`.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. *Zero Regressions*: All existing tests in `testDebugUnitTest` must pass with 100% success.
  2. *Non-Blocking Ergonomics*: Zero modal dialogs. Tracking HUD interaction remains fluid.
  3. *Navigation Bar Inset Integrity*: Snackbars and action buttons must never be occluded by Android 15 gesture handles or 3-button bars.
  4. *Anti-Spam Session Throttling*: An active sensor dropping into critical battery triggers at most one snackbar per workout session.
  5. *Localization Parity*: Complete translation parity across all 9 supported languages.
* **Risk Rating**: **LOW**
  - The design reuses existing, audited event pipelines (`WorkoutNavigationEvents`) and standardized Material 3 components (`SnackbarHost`).
