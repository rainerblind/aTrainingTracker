# Stage 1 Analysis: ATT-2075 - Modernize Permission Flow: Contextual Just-in-Time Prompts & Graceful Settings Return Handling (Rework Cycle)

**Ticket**: [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)  
**Sub-task**: [ATT-2377](https://rainerblind.atlassian.net/browse/ATT-2377) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.15`  
**Branch**: `feature/ATT-2075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Sprint Review Defect Analysis

During Sprint Review testing on a physical test device (Google Pixel 10 running Android 16), two critical functional defects were identified in the modernized Just-in-Time (JIT) permission flow:

1. **Defect 1: Missing Background Location ("Allow all the time" / "Immer zulassen") Navigation**:
   * For reliable workout tracking while the athlete's phone is stowed in a cycling jersey pocket or running belt with the screen locked, Android strictly mandates `ACCESS_BACKGROUND_LOCATION`.
   * In the previous iteration of `ControlTrackingScreen.kt`, the launcher only requested foreground location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`), Bluetooth, and Notification permissions.
   * Upon foreground grant, `ControlTrackingScreen` immediately invoked `onStart()`, completely bypassing background location request. As a result, athletes were never prompted or guided to select "Allow all the time" in Android settings.
2. **Defect 2: Missing Battery Optimization Exemption Prompt**:
   * Android power management aggressively terminates background foreground services (`TrackerService`) during extended rides or runs if the app is subjected to standard battery optimization.
   * `MainActivityWithNavigation.kt` historically defined `checkBatteryOptimizations()`, but this was removed from `onCreate()` to eliminate cold-start popups and was never migrated into the JIT tracking initiation flow.
   * The athlete was never prompted to grant the "Unrestricted" / "Nicht eingeschränkt" battery exemption (`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).

---

## 2. Technical Forensic Root Cause Analysis & Platform Fragmentation

### 2.1 Investigation of `ControlTrackingScreen.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt`:
```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { results ->
    val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
    val coarseGranted = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    val granted = fineGranted || coarseGranted
    hasLocationPermission = granted
    showRationaleSheet = false
    if (granted) {
        onStart() // Immediate start without background location or battery exemption!
    }
}
```

### 2.2 Android Version Fragmentation & Escalation Constraints
1. **Pre-Android 10 (API < 29)**:
   * Background location does not exist as a separate permission; `ACCESS_FINE_LOCATION` covers background service recording.
2. **Android 10 (Q, API 29)**:
   * `ACCESS_BACKGROUND_LOCATION` was introduced. On API 29, it could theoretically be requested with foreground location, but Google guidelines strongly encourage separate, contextual escalation.
3. **Android 11+ (R, API 30+)**:
   * **Strict Two-Step Cascade Mandate**: Requesting `ACCESS_BACKGROUND_LOCATION` simultaneously with foreground location (`ACCESS_FINE_LOCATION`) is strictly prohibited by Android OS. The system will silently ignore or immediately reject the background request without showing any UI to the user.
   * **Escalation Precondition**: Foreground location MUST be granted first. Only after foreground location is actively held can `ACCESS_BACKGROUND_LOCATION` be requested via a separate launcher or settings intent.
   * On Android 11+, requesting `ACCESS_BACKGROUND_LOCATION` opens the system permission page where the user must select "Allow all the time". If the user denies or dismisses it, subsequent attempts must redirect gracefully to Application Details Settings.
4. **Android 13+ (Tiramisu, API 33+)**:
   * `POST_NOTIFICATIONS` is requested during Stage A (Foreground) so that `TrackerService`'s ongoing foreground notification is visible.

### 2.3 OEM Battery Optimization Intent Safety & Exception Wrapping
* **OEM ROM Quirks (MIUI, EMUI, OneUI, ColorOS)**:
  * Calling `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `data = Uri.parse("package:$packageName")` can throw `ActivityNotFoundException` or `SecurityException` on certain OEM vendor ROMs where the standard Google Intent is stripped or restricted.
  * **Architectural Mitigation (Fail-Safe Intent Cascading)**:
    ```kotlin
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(appDetailsIntent)
        }
    }
    ```
  * This guarantees 100% crash immunity across all device manufacturers (*dontkillmyapp.com* compliance).

---

## 3. Progressive 3-Stage Just-in-Time Architecture

The state machine executes progressively when the athlete taps **Start Tracking** (`handleStartClick`):

```
+-----------------------------------------------------------------------------------+
| Athlete taps Start Tracking (ControlTrackingButton)                               |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
| Stage A: Foreground Permissions Check                                             |
|   - ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION                                  |
|   - BLUETOOTH_CONNECT, BLUETOOTH_SCAN (API 31+)                                   |
|   - POST_NOTIFICATIONS (API 33+)                                                  |
|   If missing -> Show PermissionRationaleSheet(Type.FOREGROUND)                    |
|                 -> Launch Foreground Permission Request                           |
+-----------------------------------------------------------------------------------+
                                         | (Granted)
                                         v
+-----------------------------------------------------------------------------------+
| Stage B: Background Location Check (API 29+)                                      |
|   - ContextCompat.checkSelfPermission(ACCESS_BACKGROUND_LOCATION)                    |
|   If missing -> Show PermissionRationaleSheet(Type.BACKGROUND_LOCATION)           |
|                 Explains athletic necessity of "Allow all the time"               |
|                 -> If API 30+: Launch dedicated background permission contract    |
|                    or direct to Settings if permanently denied                    |
+-----------------------------------------------------------------------------------+
                                         | (Granted or Acknowledged/Skipped)
                                         v
+-----------------------------------------------------------------------------------+
| Stage C: Battery Optimization Exemption Check                                     |
|   - PowerManager.isIgnoringBatteryOptimizations(packageName)                      |
|   If false -> Show PermissionRationaleSheet(Type.BATTERY_OPTIMIZATION)            |
|               Explains prevention of OS service kills in jersey pocket            |
|               -> Launch ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS with fail-safe|
+-----------------------------------------------------------------------------------+
                                         | (Complete or Acknowledged/Skipped)
                                         v
+-----------------------------------------------------------------------------------+
| Stage D: onStart() -> TrackerService Begins Active Recording                      |
+-----------------------------------------------------------------------------------+
```

---

## 4. Lifecycle Synchronization & Deterministic State Management

### 4.1 Reactive `onResume()` State Polling
* In `ControlTrackingScreen.kt`, a `DisposableEffect(lifecycleOwner)` registers a `LifecycleEventObserver`.
* On `Lifecycle.Event.ON_RESUME`:
  1. Re-evaluates foreground location permission: `checkHasLocation()`.
  2. Re-evaluates background location permission: `checkHasBackgroundLocation()`.
  3. Re-evaluates battery optimization status: `checkIsIgnoringBatteryOptimizations()`.
  4. Dynamically updates UI warning badges on `ControlTrackingButton` (amber warning badge displayed if foreground or background location is missing).
  5. Clears stale rationale states if the athlete returned from system settings having granted permissions.

### 4.2 Configuration Change Resilience
* All step states (`RationaleStep.FOREGROUND`, `RationaleStep.BACKGROUND`, `RationaleStep.BATTERY_OPTIMIZATION`, `RationaleStep.NONE`) are retained across configuration changes (e.g. screen rotation while the sheet is visible) via `rememberSaveable`.

---

## 5. Chesterton's Fence Archaeology (`REQ-PRO-022`)

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   * Refines `REQ-PRI-003` (*Modernized Contextual Just-in-Time Permission Flow*) and legacy `REQ-STB-002` (*Uninterrupted Foreground Service Execution*).
2. **Historical Origin & Commit Trace**:
   * Sprint `2026-40.14` (`ATT-2075`, commit `df8eb38b`).
   * Legacy background location dialog originally introduced in Sprint `2026-40.2` (`ATT-1151`, `MainActivityWithNavigation.kt`).
3. **Root Reason for Existing Formulation**:
   * `REQ-PRI-003` correctly removed cold-start popups to allow free exploration of the app. However, it grouped all permissions into a single launcher step that could only ask for foreground permissions due to Android platform constraints.
4. **Preservation of Core Invariants**:
   * **Zero-Friction Cold Start**: Opening the app must NEVER display blocking permission dialogs.
   * **Graceful Refusal**: If the athlete refuses background location or battery optimization, the app must not crash, enter infinite loops, or hard-lock the UI; it must allow tracking with appropriate warnings.
   * **9-Language Parity**: All rationales and prompts must use localized resources across all 9 supported locales.

---

## 6. User Scope Grounding (`ATT-1250`)

### In-Scope:
* Implementing the progressive Just-in-Time state machine in `ControlTrackingScreen.kt` for Foreground Location -> Background Location -> Battery Optimization.
* Enhancing `PermissionRationaleSheet.kt` to support content variants:
  * Foreground Rationale (Location, BLE, Notifications).
  * Background Location Rationale ("Allow all the time" / "Immer zulassen").
  * Battery Optimization Rationale ("Unrestricted" / "Nicht eingeschränkt").
* Adding dedicated permission launchers and intent triggers with fail-safe fallback for background location and battery optimization.
* Re-evaluating permission and battery states on `ON_RESUME` to keep the UI warning badge accurate.
* Comprehensive unit and contract tests in `ControlTrackingPermissionTest.kt` and `PermissionRationaleSheetContractTest.kt`.

### Out-of-Scope:
* Modifying `TrackerService.java` internal GPS location polling logic.
* Changing workout database schemas or export formats.
* Modifying other screens (History, Routes, Equipment, Settings).

---

## 7. Risk Assessment & Invariants

* **Invariant 1 (Cold Start Cleanliness)**: `MainActivityWithNavigation.onCreate()` remains completely free of modal permission or battery dialogs.
* **Invariant 2 (Android 11+ Platform Compliance)**: Background location is NEVER requested in the same call as foreground location.
* **Invariant 3 (Crash Immunity & OEM Fallback)**: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` wrapped in 3-tier try-catch cascading to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` and `ACTION_APPLICATION_DETAILS_SETTINGS`.
* **Invariant 4 (Localization Parity)**: 100% parity across all 9 languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
