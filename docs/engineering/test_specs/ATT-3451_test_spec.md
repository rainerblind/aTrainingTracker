# Stage 2: Requirement & Test Specification - ATT-3451: Use warning colors instead of blue for sensor low battery snackbar alerts

**Ticket**: [ATT-3451](https://atrainingtracker.atlassian.net/browse/ATT-3451)  
**Sub-task**: [ATT-3463](https://atrainingtracker.atlassian.net/browse/ATT-3463) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-356` (*Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance*)  
**Test Spec ID**: `TST-UI-316`  
**Branch**: `feature/ATT-3451`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Requirement Specification (Amending REQ-UI-356)

### 1.1 Problem Statement & Rationale
In `TrackingTabsScreen.kt` and `WorkoutSummariesTabbedScreen.kt`, peripheral sensor low battery alert snackbars currently render with the application's default primary/blue theme container colors (`MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`, `primary`).

This presents two major usability defects:
1. **Semantic Color Misalignment**: Primary blue is used throughout `aTrainingTracker` for normal informational status, active tab selection, and route lines. Using calm blue for battery exhaustion warnings dilutes visual urgency and violates Material 3 semantic conventions.
2. **Absence of Severity Tiering**: Moderate battery depletion ($11\%\dots 20\%$) and critical battery depletion ($\le 10\%$, imminent hardware failure mid-workout) are styled identically, leaving the athlete unaware of how urgently the sensor requires replacement or recharging.

ATT-3451 updates the alert system to provide two distinct, semantic severity tiers:
* **Warning Tier ($> 10\%$ and $\le 20\%$)**: Dedicated warm amber container with high-contrast brown/dark text and action button ($\ge 7:1$ contrast ratio).
* **Critical Tier ($\le 10\%$)**: Red/error container (`errorContainer`, `onErrorContainer`, `error`).
* **Instantaneous Preemption**: If an alert escalates from Warning to Critical, any currently displayed warning snackbar is preempted and dismissed immediately to display the critical warning without delay.

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-356` (*Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance*, ATT-3313) and `REQ-CON-007` (*Post-Workout Alerting*, ATT-2192), targeting `TrackingTabsScreen.kt`, `WorkoutSummariesTabbedScreen.kt`, `MainActivityWithNavigation.kt`, and `WorkoutNavigationEvents.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-40.16 (`ATT-2192`, commit `ac493d2b`); refined in Sprint 2026-41.9 (`ATT-3167`, commit `53b219ac`); expanded to tracking screen in Sprint 2026-42.1 (`ATT-3313`, commit `479c7ee7`).
* **Root Reason for Existing Formulation**: ATT-3313 wired `SnackbarHost` into `TrackingTabsScreen` with navigation bar padding and lap button clearance. It used `primaryContainer` as a safe initial placeholder.
* **Preservation of Core Invariants**: Non-blocking Material 3 snackbars, edge-to-edge system navigation bar padding (`Modifier.navigationBarsPadding()`), floating control clearance above `LapButton` (`64.dp` vs `8.dp`), action button navigation to `R.id.drawer_my_sensors`, alert consumption via `consumeLowBatteryAlert()`, and 100% clean-room test pass rate remain strictly preserved.

### 1.2 Functional & Architectural Requirements
1. **Severity Classification Model (`WorkoutNavigationEvents.kt`)**:
   - The system SHALL define `enum class BatteryAlertSeverity { WARNING, CRITICAL }`.
   - `LowBatteryAlert` SHALL include `val severity: BatteryAlertSeverity = BatteryAlertSeverity.WARNING` and `val lowestBatteryPercentage: Int? = null`.
   - The system SHALL define typed visuals `data class BatterySnackbarVisuals(...) : SnackbarVisuals` carrying `val severity: BatteryAlertSeverity`.
2. **Thresholds & In-Ride Escalation (`MainActivityWithNavigation.kt`)**:
   - `LOW_BATTERY_WARNING_LEVEL = 20` (Warning tier: $\le 20\%$ and $> 10\%$).
   - `CRITICAL_BATTERY_LEVEL = 10` (Critical tier: $\le 10\%$).
   - Alerts covering multiple sensors SHALL resolve to `CRITICAL` if any sensor is $\le 10\%$, and `WARNING` if all sensors are $> 10\%$.
   - In-ride telemetry updates: An active session sensor that previously triggered a `WARNING` alert SHALL be allowed to trigger a `CRITICAL` alert if its battery subsequently drops to $\le 10\%$. Duplicate alerts within the same severity tier remain deduplicated.
3. **Dedicated Semantic Warning Tokens & WCAG Contrast (`Color.kt`)**:
   - The system SHALL define:
     - `LightWarningContainer = Color(0xFFFFE082)` (Amber 200)
     - `LightOnWarningContainer = Color(0xFF3E2723)` (Deep Brown, contrast $> 10:1$)
     - `LightWarningAction = Color(0xFF6D3B00)` (Deep Amber, contrast $> 7:1$)
     - `DarkWarningContainer = Color(0xFF4E2C00)` (Deep warm amber-brown)
     - `DarkOnWarningContainer = Color(0xFFFFE082)` (Soft pale amber, contrast $> 9:1$)
     - `DarkWarningAction = Color(0xFFFFD54F)` (Vibrant Amber 300, contrast $> 8:1$)
   - Critical severity SHALL resolve to Material 3 `errorContainer`, `onErrorContainer`, and `error`.
   - Non-battery snackbars (fallback) SHALL resolve to standard Material 3 colors (`inverseSurface`, `inverseOnSurface`, `primary`).
4. **Snackbar Presentation & Preemption (`TrackingTabsScreen.kt` & `WorkoutSummariesTabbedScreen.kt`)**:
   - Both screens SHALL display battery snackbars using `BatterySnackbarVisuals`.
   - When a `CRITICAL` alert arrives while a snackbar is currently displayed, the active snackbar SHALL be dismissed immediately via `snackbarHostState.currentSnackbarData?.dismiss()` so the critical alert appears without waiting for previous snackbar timeouts.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Warning Tier Presentation (Amber)**:
  * *Given* an active workout tracking session or workout launch with a paired sensor at $15\%$ battery,
  * *When* the low battery snackbar appears on screen,
  * *Then* the snackbar renders with an amber container (`LightWarningContainer` / `DarkWarningContainer`), high-contrast dark/amber text, and high-contrast action button.
* **Criterion 2: Critical Tier Presentation (Red/Error)**:
  * *Given* an active workout tracking session or workout launch with a paired sensor at $8\%$ battery,
  * *When* the low battery snackbar appears on screen,
  * *Then* the snackbar renders with red error styling (`MaterialTheme.colorScheme.errorContainer` and `onErrorContainer`).
* **Criterion 3: In-Ride Critical Escalation**:
  * *Given* an in-ride session where a sensor triggered an initial warning alert at $18\%$,
  * *When* the sensor's battery subsequently drops to $9\%$,
  * *Then* an escalated `CRITICAL` alert is dispatched.
* **Criterion 4: Instantaneous Critical Preemption**:
  * *Given* a `WARNING` snackbar currently resting on screen,
  * *When* a `CRITICAL` alert is emitted,
  * *Then* the active `WARNING` snackbar is immediately dismissed and replaced by the `CRITICAL` snackbar.
* **Criterion 5: Preservation of Non-Battery Snackbars**:
  * *Given* a standard non-battery snackbar shown in `TrackingTabsScreen` or `WorkoutSummariesTabbedScreen`,
  * *When* rendered by `SnackbarHost`,
  * *Then* standard Material 3 colors (`inverseSurface` / `inverseOnSurface`) are applied without forcing battery colors.

### 1.4 System Invariants
1. System navigation bar insets (`Modifier.navigationBarsPadding()`) preserved.
2. Floating `LapButton` clearance (`64.dp` vs `8.dp`) preserved.
3. Action button `[Zu Sensoren]` continues navigating to `R.id.drawer_my_sensors`.
4. Sticky alert consumed via `WorkoutNavigationEvents.consumeLowBatteryAlert()`.
5. 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
6. 100% full-suite unit test pass rate.

---

## 2. Test Specification (Amending TST-UI-316)

### Test Case 1: `testSeverityThresholds_warningAndCritical` (`[TST-UI-316.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/notifications/SensorBatteryAlertTest.kt`
* **Preconditions**: `WorkoutNavigationEvents` and mock database manager loaded.
* **Action**:
  1. Test device at 20% -> classified as `BatteryAlertSeverity.WARNING`.
  2. Test device at 11% -> classified as `BatteryAlertSeverity.WARNING`.
  3. Test device at 10% -> classified as `BatteryAlertSeverity.CRITICAL`.
  4. Test device at 1% -> classified as `BatteryAlertSeverity.CRITICAL`.
  5. Test multiple devices: Sensor 1 (18%) and Sensor 2 (7%) -> resolves to `BatteryAlertSeverity.CRITICAL`.
  6. Test multiple devices: Sensor 1 (18%) and Sensor 2 (12%) -> resolves to `BatteryAlertSeverity.WARNING`.
* **Expected Result**: Exact threshold boundary classification verified.

### Test Case 2: `testInRideBatteryDrop_allowsCriticalEscalation` (`[TST-UI-316.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/notifications/SensorBatteryAlertTest.kt`
* **Preconditions**: In-ride battery drop simulation.
* **Action**:
  1. Device drops to 18% -> emits initial `WARNING` alert.
  2. Same device reports 18% or 15% -> duplicate suppressed.
  3. Same device drops to 8% -> triggers `CRITICAL` escalation alert.
  4. Same device reports 8% -> duplicate critical suppressed.
* **Expected Result**: Escalation from Warning to Critical succeeds, while duplicate alerts at the same tier are suppressed.

### Test Case 3: `testSnackbarPreemption_criticalDismissesActiveWarning` (`[TST-UI-316.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/notifications/SensorBatteryAlertTest.kt`
* **Preconditions**: Mock `SnackbarHostState`.
* **Action**:
  1. Emit `WARNING` alert with mock active snackbar.
  2. Emit `CRITICAL` alert.
  3. Verify `currentSnackbarData?.dismiss()` is invoked prior to showing the critical snackbar.
* **Expected Result**: Preemption verified.

### Test Case 4: `testSemanticWarningColors_andContrast` (`[TST-UI-316.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/SensorBatteryAlertColorsTest.kt`
* **Preconditions**: Color tokens loaded.
* **Action**:
  1. Verify `LightWarningContainer`, `LightOnWarningContainer`, `LightWarningAction`.
  2. Verify `DarkWarningContainer`, `DarkOnWarningContainer`, `DarkWarningAction`.
  3. Calculate relative luminance and assert WCAG contrast ratio $\ge 4.5:1$ for action text and $\ge 7:1$ for container text.
* **Expected Result**: WCAG compliance verified.

### Test Case 5: `testTrackingTabsScreen_usesDynamicBatterySnackbarColors` (`[TST-UI-316.5]`)
* **Scope**: Contract / Layout Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreenLayoutTest.kt`
* **Preconditions**: Analyze `TrackingTabsScreen.kt` and `WorkoutSummariesTabbedScreen.kt`.
* **Action**:
  1. Verify removal of static `MaterialTheme.colorScheme.primaryContainer`.
  2. Verify invocation of `getBatterySnackbarColors(visuals.severity)` or dynamic color resolver.
  3. Verify presence of `BatterySnackbarVisuals`.
* **Expected Result**: Dynamic color styling verified.

### Test Case 6: 9-Language Localization Audit (`[TST-UI-316.6]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/TranslationParityTest.kt`
* **Preconditions**: Load all 9 localized `strings.xml`.
* **Action**: Verify `sensor_battery_snackbar_single`, `sensor_battery_snackbar_many`, and `sensor_battery_action_view_sensors` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% translation parity confirmed.

### Test Case 7: Clean-Room Full Suite Regression Execution (`[TST-UI-316.7]`)
* **Scope**: Full Clean-Room Test Suite
* **Action**: Execute `./gradlew testDebugUnitTest`.
* **Expected Result**: 100% test pass rate with 0 failures across all modules.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Target Files | Scope |
| :--- | :--- | :--- | :--- |
| `REQ-UI-356.1` (Start Evaluation & Severity Model) | `[TST-UI-316.1]` | `SensorBatteryAlertTest.kt` | Unit |
| `REQ-UI-356.2` (In-Ride Drop & Critical Escalation) | `[TST-UI-316.2]` | `SensorBatteryAlertTest.kt` | Unit |
| `REQ-UI-356.3` (Preemption & Visuals Model) | `[TST-UI-316.3]` | `SensorBatteryAlertTest.kt` | Unit |
| `REQ-UI-356.3` (Semantic Warning Colors & WCAG) | `[TST-UI-316.4]` | `SensorBatteryAlertColorsTest.kt` | Unit |
| `REQ-UI-356.3` (Dynamic UI Presentation) | `[TST-UI-316.5]` | `TrackingTabsScreenLayoutTest.kt` | Contract |
| `REQ-UI-356.6` (9-Language Parity) | `[TST-UI-316.6]` | `TranslationParityTest.kt` | Parity |
| Regression Protection | `[TST-UI-316.7]` | Entire test suite | Regression |
