# Stage 1 Analysis: ATT-2621 - Background energy / battery optimization permission not prompted when revoked

**Ticket**: [ATT-2621](https://atrainingtracker.atlassian.net/browse/ATT-2621)  
**Sub-task**: [ATT-2710](https://atrainingtracker.atlassian.net/browse/ATT-2710) (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://atrainingtracker.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2621`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.1 physical device review (Pixel 10) on ticket `ATT-2357`, the athlete revoked the permission to consume energy in the background (i.e. changed battery usage in Android system settings from "Unrestricted" to "Optimized" or "Restricted").
Upon returning to the app and navigating through the tracking controls, the athlete was **never visibly informed, prompted, or requested** to grant background energy / battery optimization exemption.

### Background & System Risk
Android Doze and modern OEM battery savers aggressively throttle background CPU, terminate foreground services without exemption, and drop BLE/ANT+ and GPS telemetry during workouts exceeding 15–30 minutes unless the application is explicitly exempted from battery optimization via `PowerManager.isIgnoringBatteryOptimizations(packageName)`.
If an athlete unknowingly operates with revoked background energy:
1. The phone will enter Doze mode during longer rides/runs when the screen turns off.
2. The operating system's Low Memory Killer (LMK) or Excessive Resource Usage daemon will kill `TrackerService`.
3. Sensor connections will disconnect and GPS trackpoints will be silenced, resulting in data loss.

### Objective
Ensure that whenever background energy / battery optimization exemption is revoked or missing:
1. The athlete is proactively alerted directly on the Control Tracking screen via a prominent, localized warning banner/card and an updated warning badge on the Start button.
2. Tapping the warning banner or the Start button seamlessly opens the system battery optimization exemption prompt (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) without being blocked or shadowed by background location denials.
3. Returning from system settings dynamically re-evaluates `isIgnoringBatteryOptimizations()` via lifecycle observation (`ON_RESUME`) and automatically dismisses the warning in real time.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 Absence of Proactive State Evaluation on Control Tracking Screen
In `ControlTrackingScreen.kt`:
* While idle on the screen, battery optimization is never evaluated as a reactive state (`mutableStateOf`).
* The warning badge on `ControlTrackingButton` (line 307) is hardcoded as:
  ```kotlin
  hasPermissionWarning = !hasLocationPermission
  ```
  It completely ignores `isIgnoringBatteryOptimizations`! If the athlete has granted Precise Location, `hasPermissionWarning` evaluates to `false`, rendering a normal active Start button despite battery optimization being revoked.
* There is no banner, card, or visual alert anywhere on `ControlTrackingScreen` informing the athlete that background energy is restricted before they tap Start.

### 2.2 Battery Optimization Rationale is Gated and Blocked by Background Location Cascade
In `ControlTrackingScreen.kt` (lines 134–157):
```kotlin
val proceedAfterPermissions: () -> Unit = {
    if (!checkHasBackgroundLocation()) {
        ...
        rationaleStep = RationaleStep.BACKGROUND_LOCATION
    } else if (!checkIsIgnoringBatteryOptimizations()) {
        isPermanentlyDenied = false
        rationaleStep = RationaleStep.BATTERY_OPTIMIZATION
    } else {
        ...
        onStart()
    }
}
```
* On Android 11+ (API 30+), Google mandates a strict separation between foreground location and background location ("Allow all the time").
* If the athlete granted "While using the app", `checkHasBackgroundLocation()` evaluates to `false`.
* When the athlete taps Start, the system displays `RationaleStep.BACKGROUND_LOCATION`.
* If the athlete chooses "Continue" but selects "While using the app" in the system settings, or if they tap "Not now", `rationaleStep` remains locked to `BACKGROUND_LOCATION` or dismisses directly without ever reaching `RationaleStep.BATTERY_OPTIMIZATION`.
* As a result, the battery optimization check is completely shadowed whenever background location is not granted.

### 2.3 Premature Reset in Rationale Sheet & State Machine Loop Prevention
In `ControlTrackingScreen.kt` (lines 385–388):
```kotlin
RationaleStep.BATTERY_OPTIMIZATION -> {
    launchBatteryOptimizationIntent(context)
    rationaleStep = RationaleStep.NONE
}
```
* When the athlete taps "Continue" in `PermissionRationaleSheet`, `rationaleStep` is reset to `NONE` before the settings intent even launches.
* In the `ON_RESUME` lifecycle observer:
  ```kotlin
  else if (checkIsIgnoringBatteryOptimizations() && rationaleStep == RationaleStep.BATTERY_OPTIMIZATION) {
      rationaleStep = RationaleStep.NONE
      onStart()
  }
  ```
  Because `rationaleStep` was already reset to `NONE`, this condition never triggers upon return, failing to auto-start or update reactive UI state.
* **Infinite Loop Prevention**: If the user repeatedly declines or cancels the system battery prompt, the application must NEVER continuously pop up modal dialogs or re-trigger the intent automatically. State must transition cleanly to `RationaleStep.NONE`, allowing the athlete to either start tracking anyway (graceful degradation) or manually tap the banner when ready.

### 2.4 Call-Site Audit for Tracking Initiation
A complete audit of all entry points triggering `onStart()` or workout recording was conducted:
1. `ControlTrackingScreen.kt` (`handleStartClick` -> `ControlTrackingButton`): Primary user-facing entry point.
2. `MainActivityWithNavigation.kt` (`checkBatteryOptimizations()`): Legacy orphaned method (line 425), never invoked. Can be safely retained or harmonized.
3. Shortcut / Intent launches: Workout tracking cannot be initiated directly via deep links or widget shortcuts without opening `MainActivityWithNavigation` and passing through `ControlTrackingScreen`.
Therefore, securing `ControlTrackingScreen.kt` comprehensively addresses 100% of active user workout start paths.

---

## 3. Chesterton's Fence & Requirement Archaeology

### 3.1 Traceability to Requirement Specifications
* **Primary Target Requirement**: `REQ-PRI-005` (*Proactive Background Energy & Battery Optimization Exemption Governance and Cascaded Rationale Decoupling*).
* **Refined Baseline Requirements**:
  - `REQ-PRI-003`: *Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation, Battery Optimization Exemption & Graceful Settings Return Handling* (Sprint 2026-40.12, `ATT-2075`).
  - `REQ-PRI-004`: *Mandatory Precise Location Gating and Dynamic Sensor Device Instantiation on Permission Escalation* (Sprint 2026-41.1, `ATT-2357`).
  - `REQ-STB-012`: *Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance* (Sprint 2026-40.12, `ATT-2079`).
* **Corresponding Test Specification**: `TST-PRI-004`.

### 3.2 Historical Origin & Commit Trace
- Commit `b28b74a2` (`ATT-2075`): Introduced Material 3 Just-In-Time bottom sheet for permissions and battery optimization.
- Commit `1f016d92` (`ATT-2357`): Enforced Precise Location gating (`REQ-PRI-004`).
- Root cause for existing formulation: `REQ-PRI-003` eliminated startup popups, placing the battery check exclusively upon Start. However, chaining it sequentially after background location caused it to be completely shadowed when background location was skipped or denied.

### 3.3 Google Play Policy & Invariant Safety
1. **Google Play Policy Exemption Invariant**: Google Play developer policy regarding `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` forbids requesting battery optimization exemption automatically or unconditionally.
   - **MANDATORY INVARIANT**: `launchBatteryOptimizationIntent` SHALL ONLY be invoked upon explicit athlete interaction (tapping the proactive warning banner or tapping "Continue" on the Start rationale sheet). It SHALL NEVER be automatically dispatched from `ON_RESUME`, `LaunchedEffect`, or application cold start.
2. **Zero Startup Popups**: Cold start remains zero-friction (`REQ-PRI-003`). The banner on `ControlTrackingScreen` is non-modal and non-blocking.
3. **Graceful Degradation & Loop Prevention**: If the athlete dismisses the banner or chooses "Not now" in the rationale sheet, tracking can still be started. The app never enters an infinite rationale loop.
4. **100% 9-Language Localization Parity**: All new banner copy must be localized across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 4. Proposed Architectural Solution

### 4.1 Reactive Battery State & Robust Lifecycle Synchronization
In `ControlTrackingScreen.kt`:
1. Maintain reactive Compose state:
   ```kotlin
   var isIgnoringBatteryOptimizations by remember {
       mutableStateOf(checkIsIgnoringBatteryOptimizations())
   }
   ```
2. In `DisposableEffect(lifecycleOwner)` on `ON_RESUME`:
   - System settings activities do not return a standardized `ActivityResult` code indicating grant status. Therefore, on `ON_RESUME`, `PowerManager.isIgnoringBatteryOptimizations(context.packageName)` is actively re-queried:
     ```kotlin
     val batteryExempt = checkIsIgnoringBatteryOptimizations()
     isIgnoringBatteryOptimizations = batteryExempt
     ```
   - If `pendingStartAfterBatteryExemption` is true and `batteryExempt` is true, automatically trigger `onStart()` and reset `pendingStartAfterBatteryExemption = false`.

### 4.2 Proactive In-App Warning Banner (`BatteryOptimizationWarningBanner`)
Render a non-blocking, prominent warning card on `ControlTrackingScreen` when `!isIgnoringBatteryOptimizations`:
- Position: Immediately below `LocationCalibrationBadge` / above the central Information Area.
- Design Tokens: Material 3 `Card` with warning container styling (`Color(0xFFFFF3CD)` in light mode, `Color(0xFF422C00)` in dark mode, or `MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)`).
- Visual Elements: Battery alert icon (`R.drawable.ic_battery_full`), headline (`battery_optimization_warning_banner_title`), informative description (`battery_optimization_warning_banner_desc`), and an actionable button ("Optimieren" / "Aktivieren" / "Einstellungen") launching `launchBatteryOptimizationIntent(context)`.
- Session dismissibility: Athlete can dismiss the banner for the current session via an "X" button (`isBannerDismissed`).

### 4.3 Unified Start Button Warning Indicator
Update `ControlTrackingButton` invocation in `ControlTrackingScreen.kt`:
```kotlin
hasPermissionWarning = !hasLocationPermission || !isIgnoringBatteryOptimizations
```
This ensures the amber warning badge is shown on the Start button if either location permission or battery optimization exemption is missing.

### 4.4 Start Flow Decoupling & Rationale Sequencing
In `proceedAfterPermissions`:
- Check both background location and battery optimization independently.
- If background location is skipped ("Not now") or denied, immediately present `RationaleStep.BATTERY_OPTIMIZATION` rather than prematurely proceeding to `onStart()`.
- When the athlete taps "Continue" in `RationaleStep.BATTERY_OPTIMIZATION`:
  - Launch `launchBatteryOptimizationIntent(context)`.
  - Set `pendingStartAfterBatteryExemption = true`.
  - Transition `rationaleStep = RationaleStep.NONE` cleanly to prevent dialog flicker.
  - Upon return in `ON_RESUME`, if `isIgnoringBatteryOptimizations` is true and `pendingStartAfterBatteryExemption` is true, invoke `onStart()`.

---

## 5. User Scope Grounding (ATT-1250)

### In-Scope
- Reactive battery optimization state tracking in `ControlTrackingScreen.kt`.
- Dedicated `BatteryOptimizationWarningBanner` composable on `ControlTrackingScreen.kt`.
- Updated `hasPermissionWarning` logic on `ControlTrackingButton`.
- Decoupled `proceedAfterPermissions` cascade to ensure battery optimization rationale is never shadowed.
- Loop-prevention guard and direct user-action constraint for Google Play policy compliance.
- 9-language localization parity for new banner strings across EN, DE, ES, FR, IT, JA, NL, PL, PT.
- Automated unit, contract, and localization tests.

### Out-of-Scope
- Modifying `TrackerService` foreground service notifications or notification channels.
- Altering `ProcessExitReasonHelper.kt` forensic exit classification (`ATT-2079`).
- Adding modal popups on application startup (strictly prohibited by `REQ-PRI-003`).

---

## 6. Deliverables & Next Steps
1. Create Stage 2 Requirement (`REQ-PRI-005`) in `docs/requirements.md` and Test Specification (`TST-PRI-004`) in `docs/tests.md`.
2. Author `docs/engineering/test_specs/ATT-2621_test_spec.md`.
3. Submit subtask `ATT-2710` for Gate 1 Re-Audit.
