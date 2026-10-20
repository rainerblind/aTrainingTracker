# Stage 1 Analysis: ATT-3998 - [Bug] [High] Bluetooth and notification permissions only requested when location is missing

**Ticket**: [ATT-3998](https://rainerblind.atlassian.net/browse/ATT-3998)  
**Sub-task**: [ATT-4365](https://rainerblind.atlassian.net/browse/ATT-4365) (`[Analysis]`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-3998`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

In **aTrainingTracker**, tracking an active workout and connecting to wireless peripherals relies on specific Android runtime permissions:
- **Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`): Required on all Android versions for recording GPS trackpoints and route telemetry.
- **Notifications** (`POST_NOTIFICATIONS`): Required on Android 13+ (API 33+) for the persistent foreground service notification, lock-screen controls, split-lap feedback, and battery alerts (`TrackerService`).
- **Bluetooth** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`): Required on Android 12+ (API 31+) for discovering nearby Bluetooth Low Energy (BLE) sensors (`BTSearchForNewDevicesEngine`) and establishing GATT connections to paired heart rate monitors, power meters, and speed/cadence sensors (`MyBTLEDevice`).

### The Bug Mechanism
In the current implementation, all three permission categories were grouped into `RationaleStep.FOREGROUND` in `ControlTrackingScreen.kt`. However, the trigger for `RationaleStep.FOREGROUND` was gated strictly and exclusively on whether location permission is missing:
```kotlin
val handleStartClick = {
    if (!checkHasLocation()) {
        ...
        rationaleStep = RationaleStep.FOREGROUND
    } else {
        proceedAfterPermissions()
    }
}
```
If `checkHasLocation()` returns `true`—which is standard whenever a user upgrades the application from an earlier version, restores app settings, grants location during onboarding, or previously granted location in system settings:
1. `handleStartClick` skips `RationaleStep.FOREGROUND` entirely and jumps directly to `proceedAfterPermissions()`.
2. `proceedAfterPermissions()` evaluates only background location (`ACCESS_BACKGROUND_LOCATION`) and battery optimization exemptions. It never checks or requests `POST_NOTIFICATIONS` (API 33+) or `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` (API 31+).
3. Even when `RationaleStep.FOREGROUND` is shown (because location was initially missing), `permissionLauncher` solely checks `results[ACCESS_FINE_LOCATION] == true`. If the athlete grants location but denies notification or Bluetooth, the callback immediately advances to `proceedAfterPermissions()`, never recording or re-prompting for the missing permissions.
4. On subsequent workout launches, `checkHasLocation()` is `true`, so neither `POST_NOTIFICATIONS` nor Bluetooth permissions are ever prompted again.
5. In sensor discovery screens (`DevicesTabbedScreen.kt`, and `ResearchButton` in `ControlTrackingScreen.kt`), sensor search is initiated without checking runtime Bluetooth permissions. On Android 12+, `BTSearchForNewDevicesEngine.startAsyncSearch()` checks `BLUETOOTH_SCAN`, logs `"BLUETOOTH_SCAN permission not granted"`, and silently aborts without requesting permission or showing any visible feedback to the athlete.
6. In `MainActivityWithNavigation.kt`, lines 509–623 contain obsolete, dead legacy permission logic (`getPermissions()`, `getPermissions(popup: Boolean)`) featuring inverted rationale logic (`ActivityCompat.shouldShowRequestPermissionRationale` was treated as an explicit denial and suppressed requests).

### Observable Failure Scenarios & User Impact
- **Silent BLE Failure**: Athletes upgrading on Android 12+ or Android 13+ who already granted location are never prompted for "Nearby Devices" (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`). When opening the sensor pairing tab (`DevicesTabbedScreen`), discovery stays perpetually empty. When starting a workout, paired BLE sensors (heart rate, power, cadence) fail to connect (`MyBTLEDevice` logs `"BLUETOOTH_CONNECT permission not granted"`).
- **Missing Workout Notifications**: Athletes on Android 13+ never receive the system notification prompt. Tracking starts, but no notification is visible in the status bar or lock screen, depriving the athlete of active workout duration, distance, and quick pause/stop controls.
- **Dead Code Debt**: `MainActivityWithNavigation.getPermissions(popup: Boolean)` remains in the codebase with flawed rationale logic, maintained solely by an obsolete backwards-compatibility contract test.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Monolithic Coupling in `ControlTrackingScreen.kt`
Lines 250–269 of `ControlTrackingScreen.kt` bind the entire foreground permission evaluation to `checkHasLocation()`:
```kotlin
val handleStartClick = {
    if (!checkHasLocation()) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val hasRequestedForeground = prefs.getBoolean("pref_has_requested_foreground_perms", false)
        val activity = context as? android.app.Activity
        isCoarseOnly = checkIsCoarseOnly()
        if (activity != null && hasRequestedForeground) {
            val showRationale = androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            isPermanentlyDenied = !showRationale && !hasLocationPermission
        } else {
            isPermanentlyDenied = false
        }
        rationaleStep = RationaleStep.FOREGROUND
    } else {
        proceedAfterPermissions()
    }
}
```
`permissionsToRequest` (lines 396–409) constructs the full composite array:
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `POST_NOTIFICATIONS` (on API 33+)
- `BLUETOOTH_CONNECT` (on API 31+)
- `BLUETOOTH_SCAN` (on API 31+)

However, this bundle is only requested if `!checkHasLocation()`. The system treats "location granted" as equivalent to "all foreground permissions granted".

In `permissionLauncher` (lines 170–192):
```kotlin
val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
) { results ->
    ...
    val fineGranted = results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
    hasLocationPermission = fineGranted
    isCoarseOnly = checkIsCoarseOnly()
    if (fineGranted) {
        isPermanentlyDenied = false
        com.atrainingtracker.banalservice.BANALService.checkOrInitializeLocationDevices()
        proceedAfterPermissions()
    } else {
        ...
    }
}
```
The result callback inspects ONLY `ACCESS_FINE_LOCATION`. If location is granted, it proceeds to `proceedAfterPermissions()`, completely ignoring whether `POST_NOTIFICATIONS` or `BLUETOOTH_*` were granted or denied.

In `proceedAfterPermissions()` (lines 145–168):
```kotlin
val proceedAfterPermissions: () -> Unit = {
    if (!checkHasBackgroundLocation()) {
        ...
        rationaleStep = RationaleStep.BACKGROUND_LOCATION
    } else if (!checkIsIgnoringBatteryOptimizations()) {
        isPermanentlyDenied = false
        rationaleStep = RationaleStep.BATTERY_OPTIMIZATION
    } else {
        isPermanentlyDenied = false
        rationaleStep = RationaleStep.NONE
        onStart()
    }
}
```
The cascade transitions only to `BACKGROUND_LOCATION` and `BATTERY_OPTIMIZATION`. If location is granted, missing `POST_NOTIFICATIONS` or `BLUETOOTH_*` permissions are never checked or requested before `onStart()`.

### 2.2 Blind Sensor Discovery Initiation Without Permission Gating
In `ControlTrackingScreen.kt` (lines 302–339), tapping `ResearchButton` triggers:
```kotlin
onSearch = { controlViewModel.onSearchClicked() }
```
In `ControlTrackingViewModel.kt` (line 154):
```kotlin
fun onSearchClicked() {
    banalServiceRepository.startSearchingForPairedDevices()
}
```
This dispatches `REQUEST_START_SEARCH_FOR_PAIRED_DEVICES` to `BANALService`. There is zero check whether `BLUETOOTH_CONNECT` (API 31+) or `BLUETOOTH_SCAN` is held.

In `DevicesTabbedScreen.kt` (lines 405–412):
```kotlin
DisposableEffect(Unit) {
    tabViewModel.startSearching()
    onDispose {
        tabViewModel.stopSearching()
        ...
    }
}
```
And in `PairingProtocolBottomSheet`: selecting `Protocol.BLUETOOTH_LE` invokes `tabViewModel.startPairingScan(Protocol.BLUETOOTH_LE, deviceType)`.
Neither `DevicesTabbedScreen.kt` nor `DevicesTabbedViewModel.kt` checks or requests runtime permissions.

In `BTSearchForNewDevicesEngine.java` (lines 368–372):
```java
if (getBuildVersionSdkInt() >= Build.VERSION_CODES.S) {
    if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "BLUETOOTH_SCAN permission not granted");
        return;
    }
}
```
When `BLUETOOTH_SCAN` is missing on Android 12+, `BTSearchForNewDevicesEngine` logs a warning and returns early. Because the UI has no permission check and no callback for permission denial, the UI displays an active searching indicator while the engine never scans.

### 2.3 Dead and Inverted Legacy Permission Code in `MainActivityWithNavigation.kt`
In `MainActivityWithNavigation.kt` (lines 509–623):
```kotlin
private fun getPermissions(): List<String> { ... }
fun getPermissions(popup: Boolean) {
    if (!popup) return
    var missingAnyPermission = false
    val requiredPerms = getPermissions()
    val requestPerms = ArrayList<String>()

    for (perm in requiredPerms) {
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            missingAnyPermission = true
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, perm)) {
                val s = "Permission $perm is explicitly denied"
                Log.i(javaClass.name, s)
            } else {
                requestPerms.add(perm)
            }
        }
    }
    ...
}
```
Flaws:
1. **Zero Production Callers**: `getPermissions(popup: Boolean)` has no call sites in production code. The method was retained in commit `4a12158e` (`ATT-2075`) only because `MainActivityStartupPermissionContractTest.kt` asserted its existence via reflection.
2. **Inverted Rationale Logic**: If `ActivityCompat.shouldShowRequestPermissionRationale(this, perm)` returns `true` (indicating the user has previously denied and the app should explain why it needs the permission before prompting again), the method logged that the permission was "explicitly denied" and **omitted** it from `requestPerms`! Conversely, it only added permissions if `shouldShowRequestPermissionRationale` was `false` (never asked before OR permanently denied).

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals
1. **Decoupled Permission Checking in `ControlTrackingScreen.kt`**:
   - Check `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS` (on Android 13+ / API 33+), and Bluetooth permissions independently.
   - When the athlete taps Start, if location is already granted but `POST_NOTIFICATIONS` is missing on Android 13+, contextually request notification permission before starting tracking.
   - If paired Bluetooth sensors exist and `BLUETOOTH_CONNECT` is missing on Android 12+, contextually prompt for Bluetooth permission.
2. **Just-In-Time Bluetooth Permission Flow for Sensor Discovery**:
   - In `ControlTrackingScreen.kt` (`ResearchButton` / `onSearch`): verify `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` (API 31+) or `ACCESS_FINE_LOCATION` (API < 31) before dispatching the search broadcast; launch permission request if missing.
   - In `DevicesTabbedScreen.kt`: verify `BLUETOOTH_SCAN` before initiating BLE discovery; if missing, request permission and display an informative state/banner rather than scanning silently with zero results.
3. **Visible Missing Permission State**:
   - In `ControlTrackingScreen.kt`: update `hasPermissionWarning` on `ControlTrackingButton` so that missing notifications or missing sensor permissions contribute to the warning indicator when relevant.
   - In `DevicesTabbedScreen.kt`: provide a visible "Bluetooth permission required" indicator/action when BLE scanning permission is not granted.
4. **Dead Code Elimination**:
   - Safely remove unused legacy methods `getPermissions()`, `getPermissions(popup: Boolean)`, `onRequestPermissionsResult`, and `showBackgroundLocationDialog()` in `MainActivityWithNavigation.kt`.
   - Update `MainActivityStartupPermissionContractTest.kt` to retire the reflection check while continuing to assert zero cold-start dialogs and location safety.

### Out-of-Scope Non-Goals (Scope Bounding)
1. **Manifest Declarations**: No modifications to `<uses-permission>` tags in `AndroidManifest.xml` (all required permissions `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, `ACCESS_FINE_LOCATION` are already declared and verified under ATT-3997).
2. **Foreground Service Types**: No modifications to Android 14+ FGS bitmask derivation in `BANALService.java` or `TrackerService.java`.
3. **ANT+ Discovery**: No changes to ANT+ plugin or radio discovery logic (`ANTSearchForNewDevicesEngineMultiDeviceSearch.java`).
4. **Database Schemas**: No changes to SQLite databases (`ActiveDevices.db`, `WorkoutSamples.db`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Modifies and refines `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation, Battery Optimization Exemption & Graceful Settings Return Handling*) and test specification `TST-PRI-002`. Targets `ControlTrackingScreen.kt`, `DevicesTabbedScreen.kt`, `MainActivityWithNavigation.kt`, and `BTSearchForNewDevicesEngine.java`.
* **Historical Origin & Commit Trace**:
  - `REQ-PRI-003` was introduced in Sprint 2026-40.16 under ticket `ATT-2075` (commits `009c8f49`, `4a12158e`, `6b53f065`) to replace eager startup dialogs with contextual just-in-time permission sheets.
  - Refined in Sprint 2026-41.2 under `ATT-2621` (commit `057d5583`) to decouple battery optimization warnings.
  - Discovered in whole-app architectural code review of `feature/ATT-3921` (`0234bfc0`), filed under ticket `ATT-3998`.
* **Root Reason for Existing Formulation**:
  - In `ATT-2075`, the primary goal was to eliminate cold-start modal dialogs on `MainActivity` startup.
  - The author created `RationaleStep.FOREGROUND` as a single bundled step containing all initial runtime permissions (`ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`).
  - The author gated this step on `if (!checkHasLocation())` under the assumption that fresh installations would always lack location.
  - Upgraded installations where location was already granted were not accounted for, resulting in notification and Bluetooth permission requests being completely bypassed.
  - Sensor discovery in `DevicesTabbedScreen.kt` was built without Compose-level permission integration, relying on `BTSearchForNewDevicesEngine` to silently log failures.
  - `MainActivityWithNavigation.getPermissions(popup: Boolean)` was kept to prevent breaking tests during the initial Jetpack Compose transition.
* **Preservation of Core Invariants**:
  - **Zero Cold-Start Popups**: The core invariant of `REQ-PRI-003` (no modal dialogs or popups during application startup) remains strictly guarded.
  - **Two-Step Location Cascade**: Foreground location is granted before requesting background location (`ACCESS_BACKGROUND_LOCATION`) on Android 11+ per Google Play policy.
  - **Fail-Safe Battery Optimization**: Battery optimization exemption intent handling (`REQ-PRI-005`) with OEM fallback is preserved.
  - **Mandatory Precise Location**: Tracking still strictly requires fine location (`REQ-PRI-004`).
  - **Android 8–11 BLE Compatibility**: `BLUETOOTH_ADMIN` manifest declaration and defensive exception handling (`REQ-CON-021`, ATT-3997) remain untouched.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Decoupled Permission State & Cascade in `ControlTrackingScreen.kt`
1. Define granular, isolated permission check functions:
   ```kotlin
   val checkHasLocation = {
       ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
   }
   val checkHasNotification = {
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
           ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
       } else {
           true
       }
   }
   val checkHasBluetoothConnect = {
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
           ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
       } else {
           true
       }
   }
   ```
2. Update `handleStartClick` to evaluate the permission steps sequentially:
   - If `!checkHasLocation()` -> Trigger `RationaleStep.FOREGROUND` (requesting location).
   - If location is granted, evaluate `proceedAfterPermissions()`:
     - If `!checkHasNotification()` (on API 33+) -> Trigger `RationaleStep.NOTIFICATION` (or request `POST_NOTIFICATIONS`).
     - If paired Bluetooth sensors are present and `!checkHasBluetoothConnect()` -> Request `BLUETOOTH_CONNECT`.
     - If `!checkHasBackgroundLocation()` -> Trigger `RationaleStep.BACKGROUND_LOCATION`.
     - If `!checkIsIgnoringBatteryOptimizations()` -> Trigger `RationaleStep.BATTERY_OPTIMIZATION`.
     - Else -> `onStart()`.
3. Support dismissing optional permissions: If the athlete dismisses the notification or Bluetooth rationale sheet, proceed gracefully to the next step (background location / battery / tracking start) without blocking tracking.

### 5.2 Just-in-Time Bluetooth Permissions for Sensor Discovery
1. **ControlTrackingScreen Research Button**:
   - When `onSearch` is clicked, verify `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` on API 31+ (and `ACCESS_FINE_LOCATION` on API < 31).
   - If missing, launch a permission request launcher rather than dispatching the search broadcast blindly.
2. **DevicesTabbedScreen Sensor Search**:
   - Provide a permission launcher in `DevicesTabbedScreen.kt`.
   - Before executing `tabViewModel.startSearching()` or `startPairingScan(Protocol.BLUETOOTH_LE, ...)`, check if `BLUETOOTH_SCAN` is granted on API 31+ (and `ACCESS_FINE_LOCATION` / `BLUETOOTH_ADMIN` on API < 31).
   - If missing, request the permission. If denied, show an informative inline banner or snackbar indicating that Bluetooth scan permission is required to discover nearby sensors.

### 5.3 Dead Code Retirement in `MainActivityWithNavigation.kt`
- Delete `private fun getPermissions(): List<String>`, `fun getPermissions(popup: Boolean)`, `override fun onRequestPermissionsResult(...)`, and `private fun showBackgroundLocationDialog()`.
- Update `MainActivityStartupPermissionContractTest.kt`: remove the reflection test asserting `getPermissions` exists, while maintaining tests for `LocationPermissionSafetyTest` and zero-popup cold-start behavior.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero modal dialogs or permission prompts on cold start (`REQ-PRI-003`).
  2. Strict fine location requirement for tracking start (`REQ-PRI-004`).
  3. Defensive FGS permission gating and graceful lifecycle teardown (Rule 29, `docs/coding_standards.md`).
  4. 100% clean-room full test suite pass rate (`./gradlew testDebugUnitTest`).
  5. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **HIGH**
  - **Justification**: Changes directly impact the core workout start flow (`ControlTrackingScreen.kt`) and sensor pairing discovery (`DevicesTabbedScreen.kt`) across Android versions 8 through 14+ (API 26–34). Any regression in permission checks could prevent athletes from starting workouts or pairing hardware.
