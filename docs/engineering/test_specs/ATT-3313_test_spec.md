# Stage 2: Requirement & Test Specification - ATT-3313: Display low sensor battery alert snackbars on workout start and during in-ride tracking

**Ticket**: [ATT-3313](https://rainerblind.atlassian.net/browse/ATT-3313)  
**Sub-task**: [ATT-3413](https://rainerblind.atlassian.net/browse/ATT-3413) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-356` (extending and refining `REQ-UI-350` and `REQ-CON-007`)  
**Test Spec ID**: `TST-UI-316`  
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

## 1. Requirement Specification (REQ-UI-356)

### 1.1 Problem Statement & Rationale
Athletes currently receive low sensor battery alert snackbars exclusively on the post-workout summary screen. When starting a workout with a depleted sensor ($\le 20\%$) or when an active sensor battery drops into a critical state mid-ride, no in-app alert is displayed on the tracking screen. This requirement provides timely, non-blocking in-app warnings at workout start and in-ride live transitions, fully adhering to system navigation bar insets and bottom control ergonomics.

### 1.2 Functional & Architectural Requirements

#### REQ-UI-356: Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance
1. *Workout Start Evaluation*:
   - Upon tracking start (`REQUEST_START_TRACKING`), the system SHALL asynchronously evaluate all paired remote devices in the database.
   - If one or more paired remote sensors have `LAST_BATTERY_PERCENTAGE >= 0` and `<= 20`, the system SHALL emit a `LowBatteryAlert` event via `WorkoutNavigationEvents.triggerLowBatteryAlert(...)`.
2. *In-Ride Real-Time Battery Drop Evaluation*:
   - During active tracking (`TrackingMode.TRACKING` or `TrackingMode.PAUSED`), whenever an active session sensor reports a battery percentage in $[0, 20]$ via `DeviceDataRepository.deviceBatteryUpdates`, the system SHALL evaluate whether the sensor has already been alerted in the current session.
   - If not yet alerted, the sensor ID SHALL be recorded in an in-memory session set `alertedSensorIds` and a `LowBatteryAlert` event SHALL be emitted via `WorkoutNavigationEvents.triggerLowBatteryAlert(...)`.
   - The `alertedSensorIds` set SHALL be cleared whenever tracking is stopped or a new workout is created.
3. *Tracking Screen Presentation (`TrackingTabsScreen.kt`)*:
   - `TrackingTabsScreen.kt` SHALL instantiate and host a `SnackbarHost` connected to a dedicated `SnackbarHostState`.
   - `TrackingTabsScreen.kt` SHALL collect `WorkoutNavigationEvents.lowBatteryAlert`. Upon receipt of an alert, it SHALL display a Material 3 `Snackbar` with duration `SnackbarDuration.Long` and action `[Zu Sensoren]` (`R.string.sensor_battery_action_view_sensors`).
   - If the user clicks `[Zu Sensoren]`, the application SHALL navigate to `R.id.drawer_my_sensors` via `MainActivityWithNavigation.navigateToDrawerItem()`.
   - Once displayed, the alert event SHALL be cleared via `WorkoutNavigationEvents.consumeLowBatteryAlert()`.
4. *Navigation Bar Inset & Floating Control Ergonomics*:
   - The `SnackbarHost` in `TrackingTabsScreen.kt` SHALL configure `Modifier.navigationBarsPadding()` ensuring zero visual occlusion by the Android system navigation bar or gesture handle.
   - When the `LapButton` is visible and active on the current tab, the `SnackbarHost` SHALL apply additional vertical clearance (`bottom = 64.dp`), ensuring the snackbar floats cleanly above the lap button without overlapping. When the lap button is suppressed, standard padding (`bottom = 8.dp`) SHALL be applied.
5. *Normal Battery Neutrality*:
   - When all paired and active sensors have battery $> 20\%$ (or negative unmeasured values), zero snackbars SHALL be triggered.
6. *100% 9-Language Localization Parity*:
   - All snackbar messages and action labels SHALL maintain 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Workout Start with Low Battery Paired Sensor**
  * *Given* a paired sensor with recorded battery percentage 15% in the database,
  * *When* the athlete starts a workout,
  * *Then* a snackbar is displayed on `TrackingTabsScreen` stating `⚠️ [Sensor Name]: Akku schwach (15%)` with action `[Zu Sensoren]`.

* **Criterion 2: In-Ride Sensor Battery Transition**
  * *Given* an active workout tracking session where an active sensor originally had 25% battery,
  * *When* an incoming telemetry frame updates the sensor's battery to 18%,
  * *Then* a snackbar is immediately triggered on `TrackingTabsScreen` warning the athlete.
  * *And* subsequent telemetry frames with 18% do not re-trigger a duplicate snackbar during the same session.

* **Criterion 3: Navigation Bar Inset & Lap Button Clearance**
  * *Given* `TrackingTabsScreen` displaying a low battery snackbar on a tab with the lap button enabled,
  * *When* inspected on an Android device with gesture navigation,
  * *Then* the snackbar renders above the navigation bar handle and with clearance above the lap button.

* **Criterion 4: Action Button Navigation**
  * *Given* a low battery snackbar displayed on `TrackingTabsScreen`,
  * *When* the athlete taps `[Zu Sensoren]`,
  * *Then* the app navigates to "Meine Sensoren" and clears the active alert.

---

## 2. Test Specification (TST-UI-316)

### Test Case 1: `testWorkoutStart_criticalPairedSensor_emitsLowBatteryAlert` (`[TST-UI-316.1]`)
* **Scope**: Unit Test (`SensorBatteryAlertTest.kt`)
* **Preconditions**: Paired sensor ID 1 with 15% battery in database; paired sensor ID 2 with 80% battery.
* **Action**: Execute start workout battery evaluation.
* **Expected Result**: Single critical alert emitted for sensor ID 1 with correct message format.

### Test Case 2: `testInRideBatteryDrop_newlyCritical_emitsAlertAndDeduplicates` (`[TST-UI-316.2]`)
* **Scope**: Unit Test (`SensorBatteryAlertTest.kt`)
* **Preconditions**: Active workout tracking session; sensor ID 1 reporting 25% (normal).
* **Action**: Emit battery update 18% (critical), then emit 18% again.
* **Expected Result**: Alert is triggered on first transition to $\le 20\%$; second update is suppressed by session deduplication.

### Test Case 3: `testTrackingTabsScreen_structuralContracts` (`[TST-UI-316.3]`)
* **Scope**: Contract Test (`TrackingTabsScreenLayoutTest.kt`)
* **Preconditions**: Parse `TrackingTabsScreen.kt`.
* **Action**: Assert layout structure.
* **Expected Result**:
  - `TrackingTabsScreen.kt` contains `SnackbarHost`.
  - `SnackbarHost` applies `navigationBarsPadding()`.
  - `SnackbarHost` adjusts bottom padding for `LapButton`.
  - Collects `WorkoutNavigationEvents.lowBatteryAlert` and invokes `consumeLowBatteryAlert()`.

### Test Case 4: 9-Language Localization & Specifier Audit (`[TST-UI-316.4]`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 5: Clean-Room Full Regression Suite (`[TST-UI-316.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-316.1]` | Unit | `MainActivityWithNavigation.checkSensorBatteryOnStart` | `REQ-UI-356.1` | Specified |
| `[TST-UI-316.2]` | Unit | In-Ride Battery Drop Monitor & Deduplicator | `REQ-UI-356.2` | Specified |
| `[TST-UI-316.3]` | Contract | `TrackingTabsScreen.kt` (`SnackbarHost`) | `REQ-UI-356.3`, `REQ-UI-356.4` | Specified |
| `[TST-UI-316.4]` | Parity | `TranslationParityTest` | `REQ-UI-356.6` | Specified |
| `[TST-UI-316.5]` | Full Suite | `./gradlew testDebugUnitTest` | `REQ-UI-356` | Specified |
