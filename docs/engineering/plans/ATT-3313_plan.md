# Stage 3: Implementation Plan - ATT-3313: Display low sensor battery alert snackbars on workout start and during in-ride tracking

**Ticket**: [ATT-3313](https://rainerblind.atlassian.net/browse/ATT-3313)  
**Sub-task**: [ATT-3414](https://rainerblind.atlassian.net/browse/ATT-3414) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-356`  
**Test Mapping**: `TST-UI-316`  
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

## 1. Problem Description & Background

Athletes currently receive low sensor battery alert snackbars exclusively on the post-workout summary screen. When starting a workout with a depleted sensor ($\le 20\%$) or when an active sensor battery drops into a critical state mid-ride, no in-app alert is displayed on the tracking screen. This plan extends low sensor battery alerting to:
1. Workout launch (`REQUEST_START_TRACKING`): evaluate paired sensors and display a warning snackbar if any have critical battery.
2. In-ride telemetry updates: observe live battery updates from `DeviceDataRepository.deviceBatteryUpdates`, deduplicating alerts per workout session.
3. Tracking cockpit UI: integrate `SnackbarHost` with `Modifier.navigationBarsPadding()` and `LapButton` clearance in `TrackingTabsScreen.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-356` (*Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance*)
* **Test Mapping**: `TST-UI-316` (*Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites (`./gradlew testDebugUnitTest`) continue to pass cleanly.
2. **Non-Blocking Ergonomics**: All warnings are Material 3 snackbars; zero modal dialogs are presented during workout preparation or active tracking.
3. **Navigation Bar Inset Integrity (Rule 23)**: `SnackbarHost` in `TrackingTabsScreen.kt` applies `Modifier.navigationBarsPadding()` to prevent gesture navigation handle occlusion.
4. **Session Deduplication**: Each sensor triggers at most one in-ride battery drop warning per workout session.
5. **Thread Safety & Dispatcher Affinity**: Database queries execute asynchronously on `Dispatchers.IO`.
6. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Workout Start Evaluation (`MainActivityWithNavigation.kt`)
- Introduce `checkSensorBatteryOnStart()`:
  - Invoked from `mStartTrackingReceiver` upon receiving `REQUEST_START_TRACKING`.
  - Dispatches to `lifecycleScope.launch(Dispatchers.IO)`.
  - Queries `DevicesDatabaseManager.getCriticalBatteryDevices(CRITICAL_BATTERY_LEVEL)` filtered to paired remote devices (`it.isPaired > 0`).
  - If critical devices are found, formats localized message (`R.string.sensor_battery_alert_single` or `R.string.sensor_battery_alert_multiple`) and triggers `WorkoutNavigationEvents.triggerLowBatteryAlert(...)`.

### Component 2: In-Ride Live Drop Monitor (`MainActivityWithNavigation.kt`)
- Introduce `mInRideBatteryAlertJob: Job?` and `mInRideAlertedSensorIds: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())`.
- When tracking starts (`mStartTrackingReceiver`):
  - Clear `mInRideAlertedSensorIds`.
  - Cancel any existing `mInRideBatteryAlertJob`.
  - Launch coroutine collecting `DeviceDataRepository.deviceBatteryUpdates`:
    - On `(deviceId, percentage)`:
    - If tracking is active (`TrackerService.isTracking()`), `percentage in 0..CRITICAL_BATTERY_LEVEL`, and `mInRideAlertedSensorIds.add(deviceId)` returns true:
      - Look up device name via `DevicesDatabaseManager.getDevice(deviceId)`.
      - Format single alert message `R.string.sensor_battery_alert_single`.
      - Dispatch `WorkoutNavigationEvents.triggerLowBatteryAlert(...)`.
- When tracking finishes (`mTrackingStoppedReceiver`):
  - Cancel `mInRideBatteryAlertJob`.
  - Clear `mInRideAlertedSensorIds`.

### Component 3: Tracking Screen Presentation (`TrackingTabsScreen.kt`)
- Instantiate `val snackbarHostState = remember { SnackbarHostState() }`.
- Observe `WorkoutNavigationEvents.lowBatteryAlert` in `LaunchedEffect(Unit)`:
  - Display snackbar with `duration = SnackbarDuration.Long`, `actionLabel = stringResource(R.string.sensor_battery_action_view_sensors)`.
  - If action clicked: `(context as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors)`.
  - Clear alert: `WorkoutNavigationEvents.consumeLowBatteryAlert()`.
- Add `SnackbarHost` in root layout:
  - `Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (shouldShowLapButton) 64.dp else 8.dp)`.
  - Custom container and content colors matching `WorkoutSummariesTabbedScreen.kt`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `WorkoutSummariesTabbedScreen.kt` lines 145–165 (canonical low battery snackbar host implementation).
* **Reused components**: `SnackbarHost`, `Snackbar`, `WorkoutNavigationEvents.lowBatteryAlert`, `navigateToDrawerItem(R.id.drawer_my_sensors)`.
* **Theme tokens**:
  - `containerColor = MaterialTheme.colorScheme.primaryContainer`
  - `contentColor = MaterialTheme.colorScheme.onPrimaryContainer`
  - `actionColor = MaterialTheme.colorScheme.primary`
  - `navigationBarsPadding()`
* **New one-off styles & justification**: None. Exactly matches the Material 3 snackbar visual tokens and dimensions established in `WorkoutSummariesTabbedScreen.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Workout Start & In-Ride Alert Logic in `MainActivityWithNavigation.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/MainActivityWithNavigation.kt`
* Changes:
  - Add `checkSensorBatteryOnStart()`.
  - Add `startInRideBatteryMonitoring()` and `stopInRideBatteryMonitoring()`.
  - Wire into `mStartTrackingReceiver` and `mTrackingStoppedReceiver`.

### Step 2: Tracking Screen Snackbar Integration in `TrackingTabsScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingTabsScreen.kt`
* Changes:
  - Add `snackbarHostState` and `LaunchedEffect` collecting `WorkoutNavigationEvents.lowBatteryAlert`.
  - Render `SnackbarHost` with `navigationBarsPadding()` and `LapButton` clearance.

### Step 3: Unit & Contract Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorBatteryAlertTest.kt` (new unit tests)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingTabsScreenLayoutTest.kt` (new contract test)
* Changes:
  - Implement `[TST-UI-316.1]`, `[TST-UI-316.2]`, `[TST-UI-316.3]`.

### Step 4: Localization Parity & Full Suite Regression Verification
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* Verification:
  - `./gradlew testDebugUnitTest` asserting 100% clean-room test pass rate.
