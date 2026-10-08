# Stage 2 Requirement & Test Specification: ATT-2621 - Background energy / battery optimization permission not prompted when revoked

**Ticket**: [ATT-2621](https://atrainingtracker.atlassian.net/browse/ATT-2621)  
**Sub-task**: [ATT-2711](https://atrainingtracker.atlassian.net/browse/ATT-2711) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://atrainingtracker.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2621`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-PRI-005)

### 1.1 Requirement Definition
The system SHALL actively monitor battery optimization exemption (`PowerManager.isIgnoringBatteryOptimizations`), render a prominent non-blocking proactive warning banner and Start button warning badge when background energy consumption is restricted, decouple the Just-in-Time rationale cascade so battery optimization is never shadowed by background location denials, and strictly adhere to Google Play policy by launching system settings ONLY upon direct athlete interaction (ATT-2621):

1. **Reactive Battery Optimization State & Lifecycle Synchronization (`ControlTrackingScreen.kt`)**:
   - `ControlTrackingScreen` SHALL maintain a reactive Compose state `isIgnoringBatteryOptimizations` reflecting `PowerManager.isIgnoringBatteryOptimizations(context.packageName)`.
   - On `Lifecycle.Event.ON_RESUME`, `isIgnoringBatteryOptimizations` SHALL be re-evaluated to detect exemption grants or revocations performed in Android system settings in real time.

2. **Proactive Warning Banner (`BatteryOptimizationWarningBanner`)**:
   - When `isIgnoringBatteryOptimizations == false`, `ControlTrackingScreen` SHALL render a non-blocking `BatteryOptimizationWarningBanner` below `LocationCalibrationBadge` and above the central Information Area.
   - The banner SHALL display an alert icon (`ic_battery_full`), localized headline (`battery_optimization_warning_banner_title`), informative description (`battery_optimization_warning_banner_desc`), and an actionable trigger ("Aktivieren" / "Einstellungen") that directly dispatches `launchBatteryOptimizationIntent(context)`.
   - The banner SHALL provide a session-level dismissal option ("X"), hiding the banner for the active screen session via `rememberSaveable { mutableStateOf(false) }`.

3. **Unified Start Button Warning Indicator**:
   - The warning badge on `ControlTrackingButton` SHALL evaluate to `true` whenever Precise Location is missing OR battery optimization exemption is missing: `hasPermissionWarning = !hasLocationPermission || !isIgnoringBatteryOptimizations`.

4. **Decoupled Just-in-Time Rationale Cascade**:
   - In `proceedAfterPermissions`, when background location is skipped ("Not now") or denied, the system SHALL NOT bypass battery optimization; if `checkIsIgnoringBatteryOptimizations() == false`, it SHALL present `RationaleStep.BATTERY_OPTIMIZATION`.
   - When the athlete taps Continue on `RationaleStep.BATTERY_OPTIMIZATION`, the system SHALL launch `launchBatteryOptimizationIntent(context)` and set `pendingStartAfterBatteryExemption = true`. If exemption is confirmed upon `ON_RESUME`, `onStart()` SHALL be automatically triggered.
   - If the athlete taps "Not now", `rationaleStep` SHALL reset to `NONE` and tracking SHALL start, preventing infinite rationale loops.

5. **Google Play Policy Invariant**:
   - `launchBatteryOptimizationIntent` SHALL ONLY be invoked upon explicit athlete interaction (tapping the banner or tapping Continue in the rationale sheet). It SHALL NEVER be automatically triggered from `ON_RESUME` or application launch.

6. **100% 9-Language Localization Parity**:
   - All banner string resources (`battery_optimization_warning_banner_title`, `battery_optimization_warning_banner_desc`, `battery_optimization_action_fix`) SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.2 Acceptance Criteria (Given-When-Then)
- **AC-1 (Proactive Banner & Start Warning Display)**:
  - *Given* an athlete on `ControlTrackingScreen` with battery optimization enabled ("Optimized" or "Restricted"),
  - *When* the screen renders or resumes,
  - *Then* `BatteryOptimizationWarningBanner` SHALL be displayed, and `ControlTrackingButton` SHALL show the warning badge.
- **AC-2 (Banner Action Trigger)**:
  - *Given* an athlete tapping the action on `BatteryOptimizationWarningBanner`,
  - *When* tapped,
  - *Then* `launchBatteryOptimizationIntent` SHALL launch the system battery optimization dialog/settings.
- **AC-3 (Real-Time Dismissal on Grant)**:
  - *Given* an athlete granting battery optimization exemption in system settings and returning to the app,
  - *When* `ON_RESUME` executes,
  - *Then* `BatteryOptimizationWarningBanner` and the Start button warning badge SHALL automatically disappear.
- **AC-4 (Decoupled Start Flow)**:
  - *Given* an athlete tapping Start when background location is denied but battery optimization is missing,
  - *When* dismissing background location rationale,
  - *Then* `RationaleStep.BATTERY_OPTIMIZATION` SHALL be presented.
- **AC-5 (Loop Prevention & Graceful Continuation)**:
  - *Given* an athlete in `RationaleStep.BATTERY_OPTIMIZATION` tapping "Not now",
  - *When* dismissed,
  - *Then* `rationaleStep` SHALL reset to `NONE` and tracking SHALL start without modal loops.
- **AC-6 (Localization Parity)**:
  - *Given* all 9 application locales,
  - *When* running translation parity tests,
  - *Then* `battery_optimization_warning_banner_title`, `battery_optimization_warning_banner_desc`, and `battery_optimization_action_fix` SHALL exist across all 9 `strings.xml` files.

---

## 2. Test Specification (TST-PRI-004)

### 2.1 Test Suite Structure

| Test Class | Focus | Type | Target Requirements |
|:---|:---|:---|:---|
| `BatteryOptimizationWarningBannerTest` | Banner rendering, dismissibility, and click dispatch | Unit / Compose Contract | `REQ-PRI-005` (Clauses 2, 5) |
| `ControlTrackingScreenBatteryTest` | Start button badge, cascade decoupling, and lifecycle synchronization | Unit / Contract | `REQ-PRI-005` (Clauses 1, 3, 4) |
| `BatteryOptimizationBannerLocalizationTest` | 9-language translation parity and XML validity | Localization Audit | `REQ-PRI-005` (Clause 6) |

---

## 3. Concrete Test Scenarios

### 3.1 `BatteryOptimizationWarningBannerTest`
- `test_banner_renders_when_battery_optimization_not_ignored`:
  - Provide `isIgnoringBatteryOptimizations = false` and `isDismissed = false`.
  - Assert banner composable is present in hierarchy.
- `test_banner_hides_when_battery_optimization_ignored`:
  - Provide `isIgnoringBatteryOptimizations = true`.
  - Assert banner is not rendered.
- `test_banner_hides_when_dismissed_by_user`:
  - Click dismiss action; assert `isDismissed` updates and banner is hidden.
- `test_banner_action_triggers_battery_intent`:
  - Click action button; verify intent callback is invoked.

### 3.2 `ControlTrackingScreenBatteryTest`
- `test_hasPermissionWarning_true_when_battery_optimization_revoked_even_if_location_granted`:
  - Given `hasLocationPermission = true` and `isIgnoringBatteryOptimizations = false`.
  - Assert `hasPermissionWarning` is `true`.
- `test_hasPermissionWarning_false_when_all_granted`:
  - Given `hasLocationPermission = true` and `isIgnoringBatteryOptimizations = true`.
  - Assert `hasPermissionWarning` is `false`.
- `test_proceedAfterPermissions_triggers_battery_step_when_bg_location_denied`:
  - Simulate background location denied / dismissed; verify next step is `RationaleStep.BATTERY_OPTIMIZATION`.
- `test_battery_step_continue_sets_pending_start`:
  - Tapping continue dispatches intent and sets pending start flag without looping.

### 3.3 `BatteryOptimizationBannerLocalizationTest`
- Parse all 9 `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
- Assert non-empty presence for:
  - `battery_optimization_warning_banner_title`
  - `battery_optimization_warning_banner_desc`
  - `battery_optimization_action_fix`
- Assert zero unescaped apostrophes or formatting mismatches.

---

## 4. Traceability Matrix

| Requirement Clause | Test Case | Verification Criteria |
|:---|:---|:---|
| Clause 1 (Reactive State) | `ControlTrackingScreenBatteryTest` | `isIgnoringBatteryOptimizations` tracks `PowerManager` dynamically on resume. |
| Clause 2 (Warning Banner) | `BatteryOptimizationWarningBannerTest` | Banner renders when revoked; collapses when granted or dismissed. |
| Clause 3 (Start Badge) | `ControlTrackingScreenBatteryTest` | Start button warning badge shows if battery exemption is missing. |
| Clause 4 (Decoupled Cascade) | `ControlTrackingScreenBatteryTest` | Background location denial seamlessly falls through to battery check. |
| Clause 5 (Policy Invariant) | `BatteryOptimizationWarningBannerTest` | Intent only launched on explicit user tap. |
| Clause 6 (Localization) | `BatteryOptimizationBannerLocalizationTest` | 100% parity across all 9 locales. |
