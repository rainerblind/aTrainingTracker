# Stage 3: Implementation Plan - ATT-3998: [Bug] [High] Bluetooth and notification permissions only requested when location is missing

**Ticket**: [ATT-3998](https://rainerblind.atlassian.net/browse/ATT-3998)  
**Sub-task**: `[Impl-Plan]` (to be created via `submit-stage`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-PRI-006` (`REQ-PRI-006.1` .. `REQ-PRI-006.6`)  
**Test Mapping**: `TST-PRI-005` (`TST-PRI-005.1` .. `TST-PRI-005.5`)  
**Branch**: `feature/ATT-3998`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In **aTrainingTracker**, tracking an active athletic workout and communicating with external sensors requires several distinct Android runtime permissions depending on the device's operating system version:
- **Precise Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`): Required across all Android versions for recording GPS trackpoints, route navigation, and speed/distance telemetry.
- **Post Notifications** (`POST_NOTIFICATIONS`): Required on Android 13+ (API 33+) for foreground service notifications (`TrackerService`), lock-screen split timers, and quick pause/resume controls.
- **Bluetooth Nearby Devices** (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` on API 31+; `ACCESS_FINE_LOCATION`, `BLUETOOTH_ADMIN` on API < 31): Required for discovering nearby BLE peripherals (`BTSearchForNewDevicesEngine`) and maintaining GATT connections to paired heart rate monitors, power meters, speed, and cadence sensors (`MyBTLEDevice`).

### The Defect Mechanism
1. In `ControlTrackingScreen.kt`, all foreground permissions (`ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`) were bundled into a single step (`RationaleStep.FOREGROUND`) whose trigger was strictly gated on `if (!checkHasLocation())`.
2. When an athlete upgrades the application or has previously granted location in system settings, `checkHasLocation()` returns `true`. Consequently, `handleStartClick` skips `RationaleStep.FOREGROUND` entirely and proceeds to `proceedAfterPermissions()`.
3. `proceedAfterPermissions()` evaluates only background location (`ACCESS_BACKGROUND_LOCATION`) and battery optimization exemptions. It never checks or prompts for missing `POST_NOTIFICATIONS` (API 33+) or `BLUETOOTH_CONNECT` (API 31+).
4. In sensor discovery (`DevicesTabbedScreen.kt` and `ResearchButton` in `ControlTrackingScreen.kt`), sensor search is initiated without checking runtime Bluetooth permissions. On Android 12+, `BTSearchForNewDevicesEngine.startAsyncSearch()` logs `"BLUETOOTH_SCAN permission not granted"` and silently aborts, leaving the UI showing an active spinner while scanning never runs.
5. In `MainActivityWithNavigation.kt`, dead legacy permission methods (`getPermissions()`, `getPermissions(popup: Boolean)`, `onRequestPermissionsResult`, `showBackgroundLocationDialog()`) remain in the codebase with inverted rationale logic, maintained solely by an obsolete reflection assertion in `MainActivityStartupPermissionContractTest.kt`.

This implementation plan details the SWE.2 architecture and step-by-step construction to decouple permission checks, establish contextual prompting, provide just-in-time sensor discovery gating, eliminate legacy dead code, and ensure 100% 9-language localization parity without breaking system invariants.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRI-006` (*Decoupled Multi-Permission Cascade, Contextual Bluetooth & Notification Gating, Sensor Discovery Permission Handling, and Legacy Code Retirement*)
  * `REQ-PRI-006.1`: Decoupled permission check predicates in `ControlTrackingScreen.kt` (`checkHasLocation`, `checkHasNotification`, `checkHasBluetoothConnect`, `checkHasBluetoothScan`).
  * `REQ-PRI-006.2`: Decoupled multi-step cascade for workout start (`handleStartClick` & `proceedAfterPermissions()`) evaluating location, notifications, and bluetooth connect independently with non-blocking dismissal.
  * `REQ-PRI-006.3`: Just-in-time Bluetooth permission gating for sensor discovery in `ControlTrackingScreen.kt` (`ResearchButton`) and `DevicesTabbedScreen.kt` (with visible required indicator).
  * `REQ-PRI-006.4`: Synchronization of `ControlTrackingButton` warning badge with missing location, battery optimization, or paired Bluetooth connect permissions.
  * `REQ-PRI-006.5`: Complete removal of obsolete legacy permission methods in `MainActivityWithNavigation.kt` and contract test cleanup.
  * `REQ-PRI-006.6`: Full 9-language localization parity across all supported application locales.
* **Test Mapping**: `TST-PRI-005` (*Decoupled Multi-Permission Cascade, Contextual Bluetooth & Notification Gating, Sensor Discovery Permission Handling, and Legacy Code Retirement Verification Suite*)
  * `[TST-PRI-005.1]`: Decoupled multi-permission cascade unit tests in `ControlTrackingPermissionTest.kt`.
  * `[TST-PRI-005.2]`: Sensor discovery just-in-time permission gating tests in `DevicesTabbedPermissionContractTest.kt` / `ControlTrackingPermissionTest.kt`.
  * `[TST-PRI-005.3]`: Legacy permission method retirement and contract safety tests in `MainActivityStartupPermissionContractTest.kt`.
  * `[TST-PRI-005.4]`: 9-language localization parity and format specifier audit in `PermissionLocalizationParityTest.kt`.
  * `[TST-PRI-005.5]`: Full clean-room regression suite pass rate (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Cold-Start Popups (`REQ-PRI-003`)**: Application startup (`MainActivityWithNavigation.onCreate()`, `onResume()`) SHALL NOT present modal permission dialogs or battery optimization popups.
2. **Mandatory Precise Location (`REQ-PRI-004`)**: Workout tracking strictly requires fine location; coarse-only location cannot start GPS recording.
3. **Android 11+ Two-Step Background Location Cascade**: Foreground location is always requested and granted prior to escalating to background location (`ACCESS_BACKGROUND_LOCATION`).
4. **Fail-Safe Battery Optimization Intent Handling (`REQ-PRI-005`)**: Catch `ActivityNotFoundException` and `SecurityException` with graceful fallback to system settings.
5. **Android 8–11 BLE Compatibility (`REQ-CON-021`, ATT-3997)**: `BLUETOOTH_ADMIN` manifest declaration and defensive exception handling remain strictly preserved.
6. **Zero Database Schema Changes**: No modifications to SQLite schema (`ActiveDevices.db`, `WorkoutSamples.db`).
7. **Thread Safety & Dispatcher Affinity**: UI mutations remain bound to Compose / Main thread; database operations remain isolated to designated dispatchers.
8. **Parent Human Gate Invariance**: Moving parent ticket `ATT-3998` to `Erledigt` is reserved exclusively for the human user.

---

## 4. Proposed Architectural Changes (SWE.2)

```
+-----------------------------------------------------------------------------------------+
|                                    UI Layer (Compose)                                   |
|                                                                                         |
|  +---------------------------+     +--------------------------+     +----------------+  |
|  |   ControlTrackingScreen   |     |    DevicesTabbedScreen   |     | RationaleSheet |  |
|  | - checkHasLocation        |     | - BLE Scan Perm Launcher |     | - FOREGROUND   |  |
|  | - checkHasNotification    |     | - Perm Required Banner   |     | - NOTIFICATION |  |
|  | - checkHasBluetoothConnect|     | - JIT Search Gating      |     | - BLUETOOTH    |  |
|  | - Decoupled Cascade       |     +--------------------------+     | - BG_LOCATION  |  |
|  | - Warning Badge Sync      |                                      | - BATTERY_OPT  |  |
|  +---------------------------+                                      +----------------+  |
+-----------------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------------+
|                                    ViewModel Layer                                      |
|                                                                                         |
|  +-------------------------------------+      +--------------------------------------+  |
|  |      ControlTrackingViewModel       |      |        DevicesTabbedViewModel        |  |
|  | - hasPairedBluetoothSensors: Flow   |      | - startSearching()                   |  |
|  | - onSearchClicked()                 |      | - startPairingScan()                 |  |
|  +-------------------------------------+      +--------------------------------------+  |
+-----------------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------------+
|                             Service & Discovery Backend Layer                           |
|                                                                                         |
|  +-------------------------------------+      +--------------------------------------+  |
|  |       DevicesDatabaseManager        |      |      BTSearchForNewDevicesEngine     |  |
|  | - hasPairedBluetoothDevices(): bool |      | - startAsyncSearch()                 |  |
|  +-------------------------------------+      +--------------------------------------+  |
+-----------------------------------------------------------------------------------------+
```

### Component 1: 9-Language Localization Resources (`res/values*/strings.xml`)
* **Layer**: Presentation Resources
* **Changes**: Add four localized keys to all 9 supported locales:
  - `bluetooth_permission_scan_required`
  - `grant_permission`
  - `permission_rationale_notification_subtitle`
  - `permission_rationale_bluetooth_subtitle`

### Component 2: Permission Rationale Types & Content (`PermissionRationaleSheet.kt`)
* **Layer**: UI Components (`ui/tracking/controltracking`)
* **Changes**:
  1. Extend `RationaleType`:
     ```kotlin
     enum class RationaleType {
         FOREGROUND,
         NOTIFICATION,
         BLUETOOTH,
         BACKGROUND_LOCATION,
         BATTERY_OPTIMIZATION
     }
     ```
  2. In `PermissionRationaleContent`:
     - Header icons: map `NOTIFICATION` to `R.drawable.ic_lap_timer` and `BLUETOOTH` to `R.drawable.ic_my_paired_devices`.
     - Titles: map `NOTIFICATION` to `R.string.permission_rationale_notification_title` and `BLUETOOTH` to `R.string.permission_rationale_bluetooth_title`.
     - Subtitles: map to localized subtitles `permission_rationale_notification_subtitle` and `permission_rationale_bluetooth_subtitle`.
     - Value items: render dedicated explanatory card for `NOTIFICATION` and `BLUETOOTH`.

### Component 3: Decoupled Multi-Permission Cascade (`ControlTrackingScreen.kt`)
* **Layer**: UI Screen (`ui/tracking/controltracking`)
* **Changes**:
  1. Extend `RationaleStep`:
     ```kotlin
     enum class RationaleStep {
         NONE,
         FOREGROUND,
         NOTIFICATION,
         BLUETOOTH,
         BACKGROUND_LOCATION,
         BATTERY_OPTIMIZATION
     }
     ```
  2. Implement granular permission check predicates:
     - `checkHasLocation()`: verifies `ACCESS_FINE_LOCATION`.
     - `checkHasNotification()`: verifies `POST_NOTIFICATIONS` on API 33+ (returns true on API < 33).
     - `checkHasBluetoothConnect()`: verifies `BLUETOOTH_CONNECT` on API 31+ (returns true on API < 31).
     - `checkHasBluetoothScan()`: verifies `BLUETOOTH_SCAN` on API 31+ (returns true on API < 31).
  3. Register dedicated permission launchers:
     - `notificationPermissionLauncher`: requests `POST_NOTIFICATIONS` on API 33+.
     - `bluetoothPermissionLauncher`: requests `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` on API 31+.
  4. Track session dismissals:
     - `isNotificationDismissed`: session state so user is not repeatedly prompted in the same session if dismissed with "Not Now".
     - `isBluetoothDismissed`: session state so user is not repeatedly prompted in the same session if dismissed with "Not Now".
  5. Refactor `proceedAfterPermissions()`:
     - If `!checkHasNotification() && !isNotificationDismissed` -> show `RationaleStep.NOTIFICATION`.
     - Else if `hasPairedBluetoothSensors && !checkHasBluetoothConnect() && !isBluetoothDismissed` -> show `RationaleStep.BLUETOOTH`.
     - Else if `!checkHasBackgroundLocation()` -> show `RationaleStep.BACKGROUND_LOCATION`.
     - Else if `!checkIsIgnoringBatteryOptimizations()` -> show `RationaleStep.BATTERY_OPTIMIZATION`.
     - Else -> `onStart()`.
  6. Update `handleStartClick`:
     - If `!checkHasLocation()` -> show `RationaleStep.FOREGROUND`.
     - Else -> evaluate `proceedAfterPermissions()`.
  7. Update `ResearchButton` (`onSearch`):
     - If on Android 12+ and `!checkHasBluetoothScan()`, trigger `bluetoothPermissionLauncher`.
     - Else dispatch `controlViewModel.onSearchClicked()`.
  8. Synchronize warning badge on `ControlTrackingButton`:
     ```kotlin
     hasPermissionWarning = !hasLocationPermission || 
                            !isIgnoringBatteryOptimizations || 
                            (hasPairedBluetoothSensors && !hasBluetoothPermission)
     ```

### Component 4: Reactive Bluetooth Sensor Presence (`ControlTrackingViewModel.kt` & `DevicesDatabaseManager.java`)
* **Layer**: ViewModel & SQLite Persistence
* **Changes**:
  1. In `DevicesDatabaseManager.java`, add:
     ```java
     public boolean hasPairedBluetoothDevices() { ... }
     ```
     querying SQLite for `PAIRED > 0 AND PROTOCOL = 'BLUETOOTH_LE'`.
  2. In `ControlTrackingViewModel.kt`, expose:
     ```kotlin
     val hasPairedBluetoothSensors: StateFlow<Boolean> = allDevicesFromDb
         .map { devices -> devices.any { it.isPaired && it.protocol == Protocol.BLUETOOTH_LE } }
         .stateIn(
             scope = viewModelScope,
             started = SharingStarted.WhileSubscribed(5000),
             initialValue = devicesDatabaseManager.hasPairedBluetoothDevices()
         )
     ```
  3. Pass `hasPairedBluetoothSensors` into `ControlTrackingScreen` via `TrackingTabsScreen.kt`.

### Component 5: Just-In-Time BLE Discovery Gating (`DevicesTabbedScreen.kt`)
* **Layer**: UI Screen (`banalservice.ui.devices.devicetabs`)
* **Changes**:
  1. Register ActivityResult launcher for `BLUETOOTH_SCAN` on API 31+.
  2. Track `hasBluetoothScanPermission` via Compose state and `LocalLifecycleOwner` onResume.
  3. If `!hasBluetoothScanPermission` on Android 12+:
     - Render informative inline warning banner at the top of the sensor list using `MaterialTheme.colorScheme.errorContainer` with action button to request permission.
     - Withhold auto-discovery until permission is granted.
  4. In `PairingProtocolBottomSheet`, when `Protocol.BLUETOOTH_LE` is selected:
     - If `!hasBluetoothScanPermission`, request permission before initiating scan.

### Component 6: Legacy Permission Dead Code Retirement (`MainActivityWithNavigation.kt`)
* **Layer**: Activity Lifecycle (`trainingtracker.activities`)
* **Changes**:
  - Remove dead methods:
    - `private fun getPermissions(): List<String>`
    - `fun getPermissions(popup: Boolean)`
    - `override fun onRequestPermissionsResult(...)`
    - `private fun showBackgroundLocationDialog()`
  - Clean up imports and unused constants (`MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION`).

---

## 5. UI Consistency (Rule 23 — Mandatory UI Audit)

* **Reference screen / component**:
  - `ControlTrackingScreen` existing `PermissionRationaleSheet` (`RationaleType.FOREGROUND`, `BACKGROUND_LOCATION`).
  - `DevicesTabbedScreen` existing warning banners and snackbars.
* **Reused components**:
  - `PermissionRationaleSheet`, `PermissionRationaleContent`, `RationaleValueItem`.
  - Material 3 `Surface`, `Icon`, `Text`, `TextButton`, `Button`.
  - Icons: `R.drawable.ic_lap_timer`, `R.drawable.ic_my_paired_devices`, `Icons.Default.BluetoothDisabled`.
* **Theme tokens**:
  - Shapes: `RoundedCornerShape(12.dp)` (dominant card and button shape).
  - Spacing: `8.dp`, `12.dp`, `16.dp`, `24.dp` adhering to the 4dp/8dp scale.
  - Colors: `MaterialTheme.colorScheme.surface`, `primary`, `errorContainer`, `onErrorContainer`, `onSurface`, `onSurfaceVariant`.
* **New one-off styles & justification**: None. All controls strictly conform to `docs/design_guidelines.md`.

---

## 6. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization String Resource Parity
* **Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Changes**: Insert translations for `bluetooth_permission_scan_required`, `grant_permission`, `permission_rationale_notification_subtitle`, and `permission_rationale_bluetooth_subtitle` across all 9 files.
* **Target Test**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PermissionLocalizationParityTest"
  ```

### Step 2: PermissionRationaleSheet & RationaleType Expansion
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PermissionRationaleSheet.kt`
* **Changes**: Add `NOTIFICATION` and `BLUETOOTH` to `RationaleType` and implement matching content rendering in `PermissionRationaleContent`.
* **Target Test**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PermissionRationaleSheetContractTest"
  ```

### Step 3: SQLite Query & ViewModel Bluetooth Presence Flow
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManager.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* **Changes**: Add `hasPairedBluetoothDevices()` to `DevicesDatabaseManager`, expose `hasPairedBluetoothSensors` on `ControlTrackingViewModel`, and wire into `TrackingTabsScreen`.
* **Target Test**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModelTest"
  ```

### Step 4: Decoupled Multi-Permission Cascade in ControlTrackingScreen
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt`
* **Changes**:
  - Decouple `checkHasLocation`, `checkHasNotification`, `checkHasBluetoothConnect`, `checkHasBluetoothScan`.
  - Add `notificationPermissionLauncher` and `bluetoothPermissionLauncher`.
  - Refactor `handleStartClick` and `proceedAfterPermissions()` to evaluate independent steps with non-blocking dismissals.
  - Wire JIT check on `ResearchButton` (`onSearch`).
  - Synchronize `hasPermissionWarning` on `ControlTrackingButton`.
* **Target Test**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingPermissionTest"
  ```

### Step 5: JIT Permission Gating in DevicesTabbedScreen
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`
* **Changes**: Add runtime `BLUETOOTH_SCAN` verification, ActivityResult launcher, visible banner for denied state, and scan withholding when ungranted.
* **Target Test**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedPermissionContractTest"
  ```

### Step 6: Legacy Permission Dead Code Removal in MainActivityWithNavigation
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/activities/MainActivityStartupPermissionContractTest.kt`
* **Changes**: Remove `getPermissions`, `onRequestPermissionsResult`, `showBackgroundLocationDialog`, and update contract test assertions.
* **Target Test**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.activities.MainActivityStartupPermissionContractTest"
  ```

### Step 7: Clean-Room Full Test Suite Regression Pass
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Expected Result**: 100% pass rate across the full application test suite with 0 failures and 0 regressions.

---

## 7. Verification & Rollback Plan

* **Verification**:
  - Fast feedback unit testing during construction (Steps 1–6).
  - Clean-room regression suite in Stage 5 (`./gradlew testDebugUnitTest`).
  - Manual verification on Android 12+ and 13+ devices / emulators verifying prompt appearance when location is pre-granted.
* **Rollback Plan**:
  - Changes are strictly isolated to the `feature/ATT-3998` branch.
  - If unexpected regressions occur, the branch can be cleanly reset to the branch point commit prior to any sprint integration.
