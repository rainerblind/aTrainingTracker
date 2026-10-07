# Stage 2: Requirement & Test Specification - ATT-2357: Require Precise Location Permission and Dynamically Recheck Permission State to Instantiate GPS Device

**Ticket**: [ATT-2357](https://atrainingtracker.atlassian.net/browse/ATT-2357)  
**Sub-task**: [ATT-2516](https://atrainingtracker.atlassian.net/browse/ATT-2516) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*) / [ATT-2075](https://atrainingtracker.atlassian.net/browse/ATT-2075) (*JIT Permissions*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-PRI-004` (*Mandatory Precise Location Gating and Dynamic Sensor Device Instantiation on Permission Escalation*)  
**Test Spec ID**: `TST-PRI-003` (*Precise Location Gating and Dynamic Sensor Device Instantiation Verification*)  
**Branch**: `feature/ATT-2357`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (`REQ-PRI-004`)

### 1.1 Problem Statement & Rationale
On Android 12+ (API 31+), the runtime location permission prompt offers two precision tiers: "Precise" (`ACCESS_FINE_LOCATION`) and "Approximate" (`ACCESS_COARSE_LOCATION`). When an athlete chooses "Approximate", the workout control interface previously treated location permissions as satisfied, but `DeviceManager` strictly refused to create `SpeedAndLocationDevice_GPS` without `ACCESS_FINE_LOCATION`. Consequently, workouts began with zero active location devices and frozen metrics. Furthermore, if the athlete later granted `ACCESS_FINE_LOCATION` in settings, `DeviceManager` never re-evaluated permission state, requiring an app force-kill.

### 1.2 Functional & Architectural Requirements
The system SHALL strictly enforce Precise Location (`ACCESS_FINE_LOCATION`) for workout tracking and dynamically recheck permission state to lazily instantiate GPS location devices (ATT-2357):

1. **Mandatory Precise Location Gating in Tracking UI (`ControlTrackingScreen.kt`)**:
   - `checkHasLocation` SHALL return `true` IF AND ONLY IF `ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED`.
   - If only `ACCESS_COARSE_LOCATION` is granted, `checkHasLocation` SHALL evaluate to `false`, the Start button SHALL display the amber permission warning badge, and tapping Start SHALL trigger the permission rationale flow explaining that Precise Location is required.
2. **Precise Location Educational Rationale (`PermissionRationaleSheet.kt`)**:
   - When location permission is missing or downgraded to Approximate, the rationale sheet SHALL display an explicit explanation (`permission_rationale_precise_location_explanation`) clarifying that Approximate location cannot record speed, distance, route trackpoints, or elevation.
3. **Dynamic Location Device Instantiation (`DeviceManager.java`)**:
   - `DeviceManager` SHALL expose a `public synchronized void checkOrInitializeLocationDevices()` method:
     - If `mSpeedAndLocationDevice_GPS == null`, `isPaired(gpsDeviceId) == true`, `TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) == true`, and GPS provider is safely available, the system SHALL instantiate `mSpeedAndLocationDevice_GPS = new SpeedAndLocationDevice_GPS(mContext, mSensorManager)`.
     - If `mSpeedAndLocationDevice_GoogleFused == null`, `isPaired(fusedDeviceId) == true`, `TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) == true`, and Google Play Services is available, the system SHALL instantiate `mSpeedAndLocationDevice_GoogleFused = new SpeedAndLocationDevice_GoogleFused(mContext, mSensorManager)`.
     - If devices are already instantiated, they SHALL NOT be re-created or duplicate-registered.
4. **Service Delegation (`BANALService.java`)**:
   - `BANALService` SHALL expose `public void checkOrInitializeLocationDevices()` delegating to `cDeviceManager.checkOrInitializeLocationDevices()` when `cDeviceManager != null`.
5. **Dynamic Lifecycle Re-evaluation (`MainActivityWithNavigation.kt` & `ControlTrackingScreen.kt`)**:
   - In `ControlTrackingScreen.kt`: when `fineGranted == true` in `permissionLauncher`, the system SHALL immediately invoke `BANALService.getDeviceManager()?.checkOrInitializeLocationDevices()`.
   - In `MainActivityWithNavigation.kt`: in `onResume()` and in `onRequestPermissionsResult` when `ACCESS_FINE_LOCATION` is granted, the system SHALL invoke `BANALService.getDeviceManager()?.checkOrInitializeLocationDevices()`.
6. **100% 9-Language Localization Parity**:
   - All newly introduced or modified string resources SHALL be localized with 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow*) and `REQ-SEN-001` (*GPS and Sensor Device Lifecycle*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.12 (`ATT-2075` introduced JIT Material 3 permission rationale). Android 12 introduced coarse vs. fine permission separation.
3. *Root Reason for Existing Formulation*: `checkHasLocation` used `|| ACCESS_COARSE_LOCATION` under the assumption that coarse location could serve as a degraded fallback. However, `SpeedAndLocationDevice_GPS` strictly requires `ACCESS_FINE_LOCATION` to construct Android `LocationManager.requestLocationUpdates` with `GPS_PROVIDER`, causing complete GPS silence when only coarse was granted.
4. *Preservation of Core Invariants*: JIT permission sheet flow, battery optimization exemption handling, background location cascade, and 100% test pass rate are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
- **AC-1 (Approximate Location Only)**:
  - *Given* an athlete on Android 12+ who granted only "Approximate Location" (`ACCESS_COARSE_LOCATION` granted, `ACCESS_FINE_LOCATION` denied),
  - *When* viewing `ControlTrackingScreen`,
  - *Then* the Start button SHALL display the amber permission warning badge, and tapping Start SHALL trigger the rationale sheet explaining that Precise Location is required for workout tracking.
- **AC-2 (Precise Location Granted)**:
  - *Given* an athlete who grants "Precise Location" in the permission prompt or system settings,
  - *When* returning to the app or completing the prompt,
  - *Then* `hasLocationPermission` SHALL evaluate to `true`, the amber warning badge SHALL be cleared, and `checkOrInitializeLocationDevices()` SHALL instantiate `SpeedAndLocationDevice_GPS` without requiring an app restart.
- **AC-3 (Dynamic Service Instantiation)**:
  - *Given* `BANALService` running with `mSpeedAndLocationDevice_GPS == null` due to initial permission denial,
  - *When* `checkOrInitializeLocationDevices()` is invoked after permission grant,
  - *Then* `mSpeedAndLocationDevice_GPS` SHALL be instantiated and added to active devices.

---

## 2. Test Specification (`TST-PRI-003`)

### 2.1 Test Verification Matrix

| Test ID | Test Scope | Verification Method | Target Class / File |
| :--- | :--- | :--- | :--- |
| `TST-PRI-003.1` | Precise Location Gating | Unit Test | `ControlTrackingPermissionTest.kt` |
| `TST-PRI-003.2` | Warning Badge on Coarse Only | Unit Test | `ControlTrackingPermissionTest.kt` |
| `TST-PRI-003.3` | Dynamic Device Instantiation | Unit Test | `DeviceManagerLocationInitTest.kt` |
| `TST-PRI-003.4` | 9-Language Localization Audit | Contract Test | `PermissionLocalizationTest.kt` |
| `TST-PRI-003.5` | Clean-Room Full Suite Regression | Full Suite | `./gradlew testDebugUnitTest` |

### 2.2 Detailed Test Method Outlines

1. **`testPreciseLocationGating_coarseOnlyRequiresFineUpgrade`**:
   - Simulate `checkSelfPermission(ACCESS_COARSE_LOCATION) == GRANTED` and `checkSelfPermission(ACCESS_FINE_LOCATION) == DENIED`.
   - Verify `checkHasLocation()` returns `false`.
   - Verify active step is `RationaleStep.FOREGROUND`.
2. **`testPreciseLocationGating_fineGrantedSatisfiesRequirement`**:
   - Simulate `checkSelfPermission(ACCESS_FINE_LOCATION) == GRANTED`.
   - Verify `checkHasLocation()` returns `true`.
3. **`testDynamicLocationDeviceInstantiation_createsDeviceWhenFineGranted`**:
   - Instantiate `DeviceManager` with `havePermission(ACCESS_FINE_LOCATION) == false`.
   - Verify `mSpeedAndLocationDevice_GPS` is initially `null`.
   - Simulate permission grant and invoke `checkOrInitializeLocationDevices()`.
   - Verify `mSpeedAndLocationDevice_GPS` is now non-null.
4. **`testDynamicLocationDeviceInstantiation_doesNotDuplicateIfAlreadyActive`**:
   - Invoke `checkOrInitializeLocationDevices()` when device already exists.
   - Verify existing device reference is preserved.
5. **`testPermissionLocalizationParity`**:
   - Verify all 9 `strings.xml` files contain `permission_rationale_precise_location_explanation` non-empty.
