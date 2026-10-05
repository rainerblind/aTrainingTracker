# Stage 1 Analysis: ATT-2075 - Modernize Permission Flow: Contextual Just-in-Time Prompts & Graceful Settings Return Handling (Rework Cycle 2: Direct Platform Intents)

**Ticket**: [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)  
**Sub-task**: [ATT-2390](https://rainerblind.atlassian.net/browse/ATT-2390) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.16`  
**Branch**: `feature/ATT-2075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-40.15 Review of [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075) on a physical Google Pixel 10 (Android 16), the human user rejected the implementation with the following feedback:
> *"In der vorherigen Version wurde ich auf die jeweiligen Einstellungen gestoßen. Jetzt werde ich nur noch zu der Einstellung der App navigiert und muss mich dann dort irgendwie durcklickern. Das ist so n.i.O. (Spezifische Direkt-Intents zu Berechtigungs- und Akku-Optimierungs-Einstellungen wiederherstellen statt allgemeiner App-Details-Einstellungen)."*

In response, the retrospective codified **Rule 21 (Specific Direct Platform Intents Over Generic App Settings)** in `aspice_governance.md`:
> *"User prompts for system permissions, battery optimization, or hardware settings must target the most specific direct intent (e.g. direct system permission request, `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). Navigating to generic Application Details Settings (`ACTION_APPLICATION_DETAILS_SETTINGS`) is only permissible as a last-resort fallback when direct intents are unavailable or permissions permanently blocked."*

The objective of this Stage 1 analysis is to perform forensic root-cause analysis on why the user was routed to generic App Details Settings, identify all offending codepaths in `ControlTrackingScreen.kt` and `PermissionRationaleSheet.kt`, and design targeted direct platform intent dispatches.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Coupling of `isPermanentlyDenied` across Progressive Setup Steps
In `ControlTrackingScreen.kt`:
```kotlin
val isPermanentlyDenied = if (activity != null) {
    if (isForegroundMissing) {
        val missingPerms = mutableListOf<String>()
        if (!(hasFine || hasCoarse)) missingPerms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBtScan) missingPerms.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!hasBtConnect) missingPerms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifications) {
            missingPerms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        missingPerms.any { perm ->
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm) &&
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
    } else false
} else false
```
And in `PermissionRationaleSheet.kt`:
```kotlin
Button(
    onClick = if (isPermanentlyDenied) onOpenSettings else onContinue,
    modifier = Modifier.weight(1f),
    shape = RoundedCornerShape(12.dp)
) {
    Text(
        text = if (isPermanentlyDenied) {
            stringResource(id = R.string.permission_rationale_open_settings)
        } else {
            stringResource(id = R.string.permission_rationale_continue)
        }
    )
}
```

This produced two critical flaws:
1. **Flaw A (Premature Permanent Denial)**: On fresh installations or unrequested permissions, `shouldShowRequestPermissionRationale()` returns `false` by Android platform design. Computing `!shouldShowRequestPermissionRationale && !isGranted` evaluated to `true`, incorrectly flagging fresh permissions as "permanently denied" and hijacking the button to `onOpenSettings` (`ACTION_APPLICATION_DETAILS_SETTINGS`) instead of triggering system prompts (`onContinue`).
2. **Flaw B (Global State Bleeding into Step 2 and Step 3)**:
   - When transitioning to `RationaleStep.BACKGROUND_LOCATION` or `RationaleStep.BATTERY_OPTIMIZATION`, the single boolean `isPermanentlyDenied` was still active.
   - For `BATTERY_OPTIMIZATION`: Battery optimization is **never** a runtime permission and has no permanent denial state in the Android framework. Passing `isPermanentlyDenied = true` caused the primary button to call `openSettingsAction` (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`), completely bypassing `launchBatteryOptimizationIntent`!

### 2.2 Background Location Direct Platform Intent on Android 11+
* Under Android 11+ (API 30+), launching `bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)` causes the Android OS to show its dedicated, direct system prompt with choices ("While using the app", "Keep while using", "Change in settings").
* Tapping "Change in settings" in the OS dialog navigates the user **directly to the Location Permissions sub-screen** with the radio buttons ("Allow all the time", "Allow only while using the app", "Don't allow").
* In the previous implementation, if `isPermanentlyDenied` was true, the app bypassed `bgLocationLauncher.launch` and dumped the athlete on the top-level Application Details page (`ACTION_APPLICATION_DETAILS_SETTINGS`), forcing the athlete to navigate through multiple nested menus ("Berechtigungen" -> "Standort" -> "Immer zulassen").

### 2.3 Battery Optimization Direct Platform Intent
* The historical implementation in `MainActivityWithNavigation.kt` directly invoked:
  ```kotlin
  Intent().apply {
      action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
      data = Uri.parse("package:$packageName")
  }
  ```
  This immediately opens the native, focused system dialog:
  *"Akkuoptimierung ignorieren? Möchtest du, dass die App im Hintergrund ausgeführt wird? [Zulassen] [Abbrechen]"*.
* The direct intent was obscured because the bottom sheet was delegating to `onOpenSettings` instead of dispatching `launchBatteryOptimizationIntent`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Restore direct platform intent invocation for Battery Optimization (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
  2. Restore direct platform permission dispatch for Background Location (`ACCESS_BACKGROUND_LOCATION` via `bgLocationLauncher`) without premature routing to `ACTION_APPLICATION_DETAILS_SETTINGS`.
  3. Decouple `isPermanentlyDenied` so each rationale step independently evaluates its own permission state.
  4. Ensure `ACTION_APPLICATION_DETAILS_SETTINGS` is strictly retained only as an exceptional fallback for truly permanently denied foreground permissions or restricted OEM ROMs (complying with Rule 21).
  5. Update and pass all contract and unit tests.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Do not re-introduce cold-start modal dialogs in `MainActivityWithNavigation.onCreate()` (violates AC-1 and zero-friction start).
  - Do not alter the 3-step progressive sequence (`FOREGROUND` -> `BACKGROUND_LOCATION` -> `BATTERY_OPTIMIZATION` -> `onStart()`).
  - Do not alter `TrackerService` foreground service lifecycle or battery saver wake-up hooks.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRI-003` (Contextual Just-in-Time Permission Flow)
* **Historical Origin & Commit Trace**: Commit `5aa2b5a2` (ATT-2075 initial), refined in commit `6a6c7569` (progressive 3-stage JIT setup cascade).
* **Root Reason for Existing Formulation**: `REQ-PRI-003` was designed to eliminate cold-start popups and introduce Material 3 educational sheets explaining athletic value (jersey pocket tracking, BLE sensors, background service life).
* **Preservation of Core Invariants**: Retaining the educational rationale sheets while fixing the button dispatch actions strictly aligns with Rule 21 and preserves the cold-start decoupling invariant (`REQ-STB-008`).

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Step-Specific Action Handlers
In `ControlTrackingScreen.kt` and `PermissionRationaleSheet.kt`:
1. **Decouple Action Logic per Step**:
   - `RationaleType.FOREGROUND`:
     - If permanently denied: `openAppSettings()` (unavoidable since OS disables system dialog).
     - Otherwise: `permissionLauncher.launch(permissionsToRequest)`.
   - `RationaleType.BACKGROUND_LOCATION`:
     - Primary button MUST ALWAYS trigger `bgLocationLauncher.launch(ACCESS_BACKGROUND_LOCATION)`. The Android OS manages the direct transition to the Location Permission screen.
     - Only if the athlete previously saw the OS prompt and permanently denied it does the secondary fallback apply.
   - `RationaleType.BATTERY_OPTIMIZATION`:
     - Primary button MUST ALWAYS trigger `launchBatteryOptimizationIntent(context)`. It is never treated as permanently denied.
2. **Proper Tracking of Permission Request History**:
   - Maintain a preference/state flag (`hasRequestedPermissionsBefore`) so `isPermanentlyDenied` is only evaluated when `!shouldShowRequestPermissionRationale` occurs *after* at least one user interaction, preventing first-launch false positives.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Rule 21 compliance: Direct platform intents over generic settings.
  2. Rule 19 compliance: `fixVersions` remains unset until final acceptance.
  3. Android 11+ two-step cascade preserved: Background location never requested simultaneously with foreground location.
  4. 100% clean-room test suite pass rate (`./gradlew testDebugUnitTest`).
  5. 9-language localization parity maintained across all locales.

* **Risk Rating**: **LOW**
  - The architectural state machine and UI sheets are already in place and tested.
  - The fix cleanly recalibrates the intent dispatch logic to point directly to platform dialogs/subscreens as requested by the user.
