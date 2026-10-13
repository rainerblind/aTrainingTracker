# Stage 5: Walkthrough & Verification Report - ATT-3313: Display low sensor battery alert snackbars on workout start and during in-ride tracking

**Ticket**: [ATT-3313](https://rainerblind.atlassian.net/browse/ATT-3313)  
**Sub-task**: [ATT-3416](https://rainerblind.atlassian.net/browse/ATT-3416) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-356` (extended & refined)  
**Test Mapping**: `TST-UI-316` (extended & refined)  
**Branch**: `feature/ATT-3313`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-356` (*Non-Blocking Low Sensor Battery Alert Snackbars on Workout Start and In-Ride Live Drops*), targeting `MainActivityWithNavigation.kt`, `TrackingTabsScreen.kt`, and `WorkoutNavigationEvents.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.16 (`ATT-2192`): Introduced post-workout low battery alerting via system notifications and snackbars upon workout completion.
   - Sprint 2026-41.9 (`ATT-3167`): Added edge-to-edge snackbar padding and window insets handling across main navigation views.
   - Sprint 2026-42.1 (`ATT-3312`): Implemented continuous active device persistence during tracking (`ActiveDevicesDbHelper`) and real-time BLE GATT notification updates.
3. **Root Reason for Existing Formulation**:
   - Prior to ATT-3313, low battery alerting only triggered *after* workout completion (`MainActivityWithNavigation.kt:checkLowBatteryDevices()`).
   - If an athlete began tracking with a depleted heart rate strap or power meter (e.g., 10%), or if a sensor dropped below 20% midway through a ride, no feedback was presented on the active tracking screen.
   - The athlete remained unaware of impending data loss until after the ride was already finished.
4. **Preservation of Core Invariants**:
   - Critical Threshold: Battery level $\le 20\%$ (`CRITICAL_BATTERY_LEVEL = 20`) strictly preserved.
   - Non-Blocking M3 UI: Snackbars must never block touch interaction, map navigation, or sensor metric viewing.
   - Inset Compliance: `SnackbarHost` in `TrackingTabsScreen.kt` must adhere to system navigation bar window insets (`navigationBarsPadding()`) and clear the Lap button when visible.
   - Deduplication: In-ride alerts for a given sensor are displayed at most once per workout session to prevent notification spam.
   - 100% 9-Language Localization Parity: All 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) maintained.
   - Clean-Room Test Pass Rate: 100% test pass rate across the full test suite.

---

## 1. Problem Domain & Implementation Summary

In previous releases, sensor battery alerting was solely reactive at the end of a workout. Athletes starting rides with depleted batteries received no immediate warning, leading to silent sensor dropouts mid-workout.

### Key Architectural Solutions Implemented:
1. **Workout Start Battery Evaluation (`MainActivityWithNavigation.kt`)**:
   - Upon starting a workout (`onWorkoutStarted()`), `checkActiveSensorsBatteryAtWorkoutStart()` inspects connected and paired sensors.
   - Any sensor reporting a battery level $\le 20\%$ immediately triggers a non-blocking snackbar via `WorkoutNavigationEvents.postLowBatteryAlert(sensorName, batteryPercentage)`.
   - Sensors reporting battery $> 20\%$ or negative values (unmeasured) remain silent.
2. **In-Ride Continuous Battery Monitoring & Session Deduplication (`MainActivityWithNavigation.kt`)**:
   - `handleLiveBatteryUpdate(deviceId, percentage)` monitors live telemetry during active tracking.
   - If a sensor drops to $\le 20\%$, an alert is triggered.
   - A thread-safe session tracking set (`mAlertedLowBatterySensorIds`) ensures that each sensor triggers at most one in-ride snackbar per workout session.
3. **TrackingTabsScreen M3 SnackbarHost with Inset Compliance (`TrackingTabsScreen.kt`)**:
   - Instantiated local `SnackbarHostState` inside `TrackingTabsScreen`.
   - Rendered `SnackbarHost` positioned at `Alignment.BottomCenter`.
   - Applied `Modifier.navigationBarsPadding()` ensuring clear separation from gesture/navigation bars.
   - Applied dynamic bottom clearance when the Lap button is visible to prevent UI overlap.
   - Subscribed to `WorkoutNavigationEvents.lowBatteryAlert` with lifecycle-aware collection and consumption.

---

## 2. Verification & Test Execution Results

1. **Targeted Unit & Contract Tests**:
   - `SensorBatteryAlertTest.kt`:
     - Verified workout start evaluation emits alert when paired sensor $\le 20\%$.
     - Verified workout start evaluation suppresses alerts when all sensors $> 20\%$.
     - Verified in-ride battery drop triggers alert upon crossing the 20% boundary.
     - Verified session deduplication prevents repeated alerts for the same sensor during a ride.
   - `TrackingTabsScreenLayoutTest.kt`:
     - Verified `TrackingTabsScreen` hosts `SnackbarHost` with `Modifier.navigationBarsPadding()`.
     - Verified dynamic bottom clearance for `LapButton`.
     - Verified `WorkoutNavigationEvents.consumeLowBatteryAlert()` invocation upon snackbar presentation.
   - `TranslationParityTest.kt`:
     - 100% string parity verified across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

2. **Clean-Room Regression Suite**:
   - Command: `./gradlew testDebugUnitTest`
   - Result: **BUILD SUCCESSFUL** (100% pass rate across entire repository in 2m 25s).

3. **Living Documentation Synchronization**:
   - `docs/requirements.md`: `REQ-UI-356` updated to `Verified`.
   - `docs/tests.md`: `TST-UI-316` updated to `Verified`.

---

## 3. Visual Consistency & UI Layout Check (Rule 23)

- **Reference Component**: Main navigation snackbar pattern and `TrackingTabsScreen` HUD overlay.
- **Layout & Spacing**:
  - `SnackbarHost` is anchored to `Alignment.BottomCenter`.
  - Window insets: Strictly wrapped with `Modifier.navigationBarsPadding()` ensuring zero occlusion by system gesture bars.
  - Floating action overlap: Bottom clearance dynamically offsets the snackbar above the Lap button when present.
- **Typography & Theme**: Standard Material 3 `Snackbar` styling adhering to app color tokens and typography hierarchy.
