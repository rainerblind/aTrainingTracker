# Stage 1 Analysis: ATT-2357 - Require Precise Location Permission and Dynamically Recheck Permission State to Instantiate GPS Device

**Ticket**: [ATT-2357](https://atrainingtracker.atlassian.net/browse/ATT-2357)  
**Sub-task**: [ATT-2515](https://atrainingtracker.atlassian.net/browse/ATT-2515) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*) / [ATT-2075](https://atrainingtracker.atlassian.net/browse/ATT-2075) (*JIT Permissions*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2357`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

On Android 12+ (API 31+), the runtime location permission prompt offers two precision tiers:
- **Precise Location** (`android.Manifest.permission.ACCESS_FINE_LOCATION`)
- **Approximate Location** (`android.Manifest.permission.ACCESS_COARSE_LOCATION`)

When an athlete grants "Approximate" (or if "Precise" location is disabled in Android app settings):
1. **False-Positive Permission Gating**:
   - `ControlTrackingScreen.kt` checks:
     ```kotlin
     val checkHasLocation = {
         ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
         ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
     }
     ```
   - If the user selects "Approximate", `checkHasLocation()` evaluates to `true`, clearing permission warnings and allowing workout tracking to start without requesting precision upgrade.
   - `MainActivityWithNavigation.kt` likewise evaluates `ACCESS_FINE_LOCATION || ACCESS_COARSE_LOCATION` in `onRequestPermissionsResult`, masking the missing fine permission.
2. **DeviceManager Initialization Failure & Silent Dropout**:
   - `DeviceManager.java` strictly checks `TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)` before initializing `SpeedAndLocationDevice_GPS` and `SpeedAndLocationDevice_GoogleFused`.
   - Because `ACCESS_FINE_LOCATION` is denied, neither location device is created.
   - `SpeedAndLocationDevice_Network` is unpaired by default (`paired=false`), resulting in **zero active location devices**.
   - As a result, the workout begins, but distance, speed, trackpoints, and elevation remain completely frozen or zeroed out throughout the session with zero feedback to the athlete.
3. **Static Initialization Lifecycle & Missing Lazy Re-evaluation**:
   - `DeviceManager` only instantiates `mSpeedAndLocationDevice_GPS` and `mSpeedAndLocationDevice_GoogleFused` in its constructor (during `BANALService.onCreate()`) or upon pairing intent broadcasts (`MY_DEVICE_PAIRED_STATE_CHANGED`).
   - If the user subsequently grants `ACCESS_FINE_LOCATION` in system settings or permission dialogs while `BANALService` is running, `DeviceManager` never re-evaluates or instantiates `mSpeedAndLocationDevice_GPS`.
   - The user must force-stop the app or kill the background service to recover GPS recording.

---

## 2. Root Cause Analysis (Forensic Investigation & Architecture Gaps)

1. **`ControlTrackingScreen.kt` Permission Checking**:
   - `checkHasLocation`:
     - Evaluates `ACCESS_FINE_LOCATION || ACCESS_COARSE_LOCATION` instead of strictly requiring `ACCESS_FINE_LOCATION`.
   - `permissionLauncher` callback:
     - Merges `val granted = fineGranted || coarseGranted`, bypassing rationale and setting `hasLocationPermission = true`.
   - Rationale step logic:
     - Does not distinguish between completely denied location and approximate-only location.
2. **`MainActivityWithNavigation.kt` Permission Gating**:
   - `onRequestPermissionsResult`:
     - Checks `(permissions[i] == ACCESS_FINE_LOCATION || permissions[i] == ACCESS_COARSE_LOCATION) && grantResults[i] == PERMISSION_GRANTED`.
     - Fails to enforce `ACCESS_FINE_LOCATION`.
   - `onResume()`:
     - Does not notify `BANALService` / `DeviceManager` to check or instantiate missing location devices when `ACCESS_FINE_LOCATION` is now present.
3. **`DeviceManager.java` Lifecycle Architecture**:
   - Location devices (`mSpeedAndLocationDevice_GPS`, `mSpeedAndLocationDevice_GoogleFused`) are instantiated only in constructor lines 284–310 and pairing change handler lines 384–420.
   - There is no public or lifecycle method to re-evaluate permission state and lazily create `mSpeedAndLocationDevice_GPS` / `mSpeedAndLocationDevice_GoogleFused` if they are null.
4. **`BANALService.java` Exposure**:
   - Lacks a delegate method `checkOrInitializeLocationDevices()` to invoke `cDeviceManager.checkOrInitializeLocationDevices()`.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Objectives
1. **Strict Fine Location Gating in Tracking UI (`ControlTrackingScreen.kt`)**:
   - Enforce `checkHasLocation` to require `ACCESS_FINE_LOCATION == PERMISSION_GRANTED`.
   - If only `ACCESS_COARSE_LOCATION` is granted, treat location as unsatisfied (`hasLocationPermission = false`), show permission warning badge on start button, and launch rationale explaining that Precise Location is required.
2. **Precise Location Rationale & Upgrade Message**:
   - Provide clear, athlete-friendly explanation when coarse location is granted but precise is denied (`permission_rationale_precise_location_explanation`).
   - Support seamless 9-language localization parity across all supported application locales.
3. **Dynamic Location Device Instantiation (`DeviceManager.java` & `BANALService.java`)**:
   - Introduce `public synchronized void checkOrInitializeLocationDevices()` in `DeviceManager.java`.
   - If `mSpeedAndLocationDevice_GPS == null`, GPS device is paired in database, and `ACCESS_FINE_LOCATION` is granted, instantiate `SpeedAndLocationDevice_GPS`.
   - If `mSpeedAndLocationDevice_GoogleFused == null`, Google Fused device is paired, and `ACCESS_FINE_LOCATION` is granted, instantiate `SpeedAndLocationDevice_GoogleFused`.
   - Expose `checkOrInitializeLocationDevices()` in `BANALService.java`.
4. **Lifecycle Hooks on Resume & Permission Result**:
   - In `ControlTrackingScreen.kt`: when `fineGranted == true` in `permissionLauncher`, immediately trigger `BANALService.getDeviceManager()?.checkOrInitializeLocationDevices()`.
   - In `MainActivityWithNavigation.kt`: on `onResume()` and in `onRequestPermissionsResult` when fine location is granted, trigger `BANALService.getDeviceManager()?.checkOrInitializeLocationDevices()`.
5. **Comprehensive Unit & Contract Tests**:
   - Add unit tests in `ControlTrackingPermissionTest.kt` verifying:
     - Coarse-only permission state triggers the fine location upgrade prompt.
     - Fine location granted state satisfies permission check.
   - Add unit tests for `DeviceManager` / `BANALService` verifying dynamic instantiation when `ACCESS_FINE_LOCATION` becomes granted.

### Out-of-Scope (Forbidden Scope Creep)
- Modifying Bluetooth sensor permission requirements (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`).
- Changing background location rationale logic (`ACCESS_BACKGROUND_LOCATION`) or battery optimization exempt flow.
- Modifying `SpeedAndLocationDevice_Network` pairing defaults.

---

## 4. Architectural Invariants & ASPICE Constraints

1. **Clean-Room Test Suite Pass Rate**:
   - 100% pass rate must be maintained across all unit and contract tests (`./gradlew testDebugUnitTest`).
2. **Localization Parity (Rule 9)**:
   - Any new or updated string resources must exist and be non-empty in all 9 supported locales: `values/` (EN), `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
3. **Chesterton's Fence Respect**:
   - Preserve existing fallback logic for devices without GPS provider (`isProviderAvailableSafely`).
   - Preserve existing paired-state checks in `DevicesDatabaseManager`.

---

## 5. Risk Assessment & Mitigations

| Risk | Impact | Mitigation Strategy |
| :--- | :--- | :--- |
| Athlete denies Precise Location on Android 12+ dialog | High (no GPS) | Show explicit rationale sheet explaining that approximate location cannot record route tracks or speed, directing user to select "Precise" or open Settings. |
| Multiple threads invoking `checkOrInitializeLocationDevices()` concurrently | Medium | Make method `synchronized` on `DeviceManager` instance and guard null checks defensively. |
| Re-instantiating already active device | Medium | Strictly check `mSpeedAndLocationDevice_GPS == null` before creating new instance. |
