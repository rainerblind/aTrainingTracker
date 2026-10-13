# Stage 3: Implementation Plan - ATT-2357: Require Precise Location Permission and Dynamically Recheck Permission State to Instantiate GPS Device

**Ticket**: [ATT-2357](https://atrainingtracker.atlassian.net/browse/ATT-2357)  
**Sub-task**: [ATT-2517](https://atrainingtracker.atlassian.net/browse/ATT-2517) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*) / [ATT-2075](https://atrainingtracker.atlassian.net/browse/ATT-2075) (*JIT Permissions*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-PRI-004` (*Mandatory Precise Location Gating and Dynamic Sensor Device Instantiation on Permission Escalation*)  
**Test Mapping**: `TST-PRI-003` (*Precise Location Gating and Dynamic Sensor Device Instantiation Verification*)  
**Branch**: `feature/ATT-2357`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

On Android 12+ (API 31+), the runtime location permission prompt offers two precision tiers: "Precise" (`ACCESS_FINE_LOCATION`) and "Approximate" (`ACCESS_COARSE_LOCATION`). When an athlete chooses "Approximate", the workout control interface previously treated location permissions as satisfied, but `DeviceManager` strictly refused to create `SpeedAndLocationDevice_GPS` without `ACCESS_FINE_LOCATION`. Consequently, workouts began with zero active location devices and frozen metrics. Furthermore, if the athlete later granted `ACCESS_FINE_LOCATION` in settings, `DeviceManager` never re-evaluated permission state, requiring an app force-kill.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRI-004` (*Mandatory Precise Location Gating and Dynamic Sensor Device Instantiation on Permission Escalation*)
  * Refines and extends `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow*) and `REQ-SEN-001` (*GPS and Sensor Device Lifecycle*).
  * Strict Fine Location Gating in `ControlTrackingScreen.kt` (`ACCESS_FINE_LOCATION`).
  * Precise Location Educational Rationale in `PermissionRationaleSheet.kt`.
  * Dynamic Location Device Instantiation in `DeviceManager.java` (`checkOrInitializeLocationDevices()`).
  * Service Delegation in `BANALService.java`.
  * Lifecycle Hooks on Resume and Permission Callback in `MainActivityWithNavigation.kt` and `ControlTrackingScreen.kt`.
  * 100% 9-Language Localization Parity.
* **Test Mapping**: `TST-PRI-003` (*Precise Location Gating and Dynamic Sensor Device Instantiation Verification*)
  * `TST-PRI-003.1`: Unit test verifying that Approximate (coarse-only) permission state returns `checkHasLocation() == false` and triggers fine location upgrade prompt (`ControlTrackingPermissionTest.kt`).
  * `TST-PRI-003.2`: Unit test verifying that `ControlTrackingButton` displays permission warning badge on coarse-only state and clears on fine granted (`ControlTrackingPermissionTest.kt`).
  * `TST-PRI-003.3`: Unit test verifying `DeviceManager.checkOrInitializeLocationDevices()` dynamically creates `SpeedAndLocationDevice_GPS` when `ACCESS_FINE_LOCATION` is granted during an active service session without duplicating existing devices (`DeviceManagerLocationInitTest.kt`).
  * `TST-PRI-003.4`: Contract test verifying 100% translation parity across all 9 application locales for new string keys (`PermissionLocalizationTest.kt`).
  * `TST-PRI-003.5`: Clean-room full suite regression execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Production Regressions**: Full clean-room test suite executed with 100% pass rate.
2. **Backward-Compatible Permission Flow**: The 3-stage JIT progressive permission flow (Foreground -> Background Location -> Battery Optimization) established in `REQ-PRI-003` / `ATT-2075` is strictly preserved.
3. **Safe Provider Availability**: Existing checks in `DeviceManager` (`isProviderAvailableSafely(locationManager, GPS_PROVIDER)`) are preserved to prevent crashes on provider-less environments or emulators.
4. **Subtask Self-Sufficiency**: Subtask [ATT-2517](https://atrainingtracker.atlassian.net/browse/ATT-2517) transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket [ATT-2357](https://atrainingtracker.atlassian.net/browse/ATT-2357) remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2 Architecture)

```
┌────────────────────────────────────────────────────────┐
│ UI Layer: ControlTrackingScreen & PermissionRationale  │
│ - checkHasLocation strictly requires ACCESS_FINE_LOC   │
│ - Coarse-only state triggers precise location rationale│
│ - permissionLauncher: fineGranted triggers             │
│   BANALService.checkOrInitializeLocationDevices()      │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ Activity / Lifecycle: MainActivityWithNavigation       │
│ - onResume: checks and invokes                         │
│   BANALService.checkOrInitializeLocationDevices()      │
│ - onRequestPermissionsResult: checks fine location     │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ Service Layer: BANALService.java                       │
│ - checkOrInitializeLocationDevices() delegates to      │
│   cDeviceManager.checkOrInitializeLocationDevices()    │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ Device Management: DeviceManager.java                  │
│ - public synchronized checkOrInitializeLocationDevices │
│ - Lazily creates SpeedAndLocationDevice_GPS and        │
│   SpeedAndLocationDevice_GoogleFused if null and fine  │
│   permission is now granted                            │
└────────────────────────────────────────────────────────┘
```

---

## 5. Step-by-Step Implementation Sequence

### Step 1: Strict Precise Location Gating in Tracking UI (`ControlTrackingScreen.kt`)
* Modify `checkHasLocation`:
  ```kotlin
  val checkHasLocation = {
      androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          android.Manifest.permission.ACCESS_FINE_LOCATION
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
  }
  ```
* Add helper to detect coarse-only state:
  ```kotlin
  val checkHasCoarseOnly = {
      androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          android.Manifest.permission.ACCESS_COARSE_LOCATION
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
      androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          android.Manifest.permission.ACCESS_FINE_LOCATION
      ) != android.content.pm.PackageManager.PERMISSION_GRANTED
  }
  ```
* In `permissionLauncher`:
  ```kotlin
  val fineGranted = results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
  hasLocationPermission = fineGranted
  if (fineGranted) {
      isPermanentlyDenied = false
      com.atrainingtracker.banalservice.BANALService.getDeviceManager()?.checkOrInitializeLocationDevices()
      proceedAfterPermissions()
  } else { ... }
  ```

### Step 2: Educational Rationale in `PermissionRationaleSheet.kt`
* In `PermissionRationaleSheet.kt`, when `isCoarseOnly` or fine is denied, display `permission_rationale_precise_location_explanation`.

### Step 3: Dynamic Location Device Instantiation in `DeviceManager.java`
* Add public synchronized method in `DeviceManager.java`:
  ```java
  public synchronized void checkOrInitializeLocationDevices() {
      DevicesDatabaseManager devicesDatabaseManager = DevicesDatabaseManager.getInstance(mContext);
      LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

      long gpsDeviceId = devicesDatabaseManager.getSpeedAndLocationGPSDeviceId();
      if (mSpeedAndLocationDevice_GPS == null && devicesDatabaseManager.isPaired(gpsDeviceId)
              && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
          if (isProviderAvailableSafely(locationManager, LocationManager.GPS_PROVIDER)) {
              if (DEBUG) Log.i(TAG, "checkOrInitializeLocationDevices: creating GPS location device");
              try {
                  mSpeedAndLocationDevice_GPS = new SpeedAndLocationDevice_GPS(mContext, mSensorManager);
              } catch (Exception e) {
                  Log.w(TAG, "Could not initialize GPS location device: " + e.getMessage());
              }
          }
      }

      long fusedDeviceId = devicesDatabaseManager.getSpeedAndLocationGoogleFusedDeviceId();
      if (mSpeedAndLocationDevice_GoogleFused == null && devicesDatabaseManager.isPaired(fusedDeviceId)
              && TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)
              && GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS) {
          if (DEBUG) Log.i(TAG, "checkOrInitializeLocationDevices: creating Google Fused location device");
          try {
              mSpeedAndLocationDevice_GoogleFused = new SpeedAndLocationDevice_GoogleFused(mContext, mSensorManager);
          } catch (Exception e) {
              Log.w(TAG, "Could not initialize Google Fused location device: " + e.getMessage());
          }
      }
  }
  ```

### Step 4: Service Delegation & Lifecycle Hooks (`BANALService.java` & `MainActivityWithNavigation.kt`)
* In `BANALService.java`:
  ```java
  public void checkOrInitializeLocationDevices() {
      if (cDeviceManager != null) {
          cDeviceManager.checkOrInitializeLocationDevices();
      }
  }
  ```
* In `MainActivityWithNavigation.kt`:
  * In `onResume()`:
    ```kotlin
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        BANALService.getDeviceManager()?.checkOrInitializeLocationDevices()
    }
    ```
  * In `onRequestPermissionsResult()`:
    ```kotlin
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        BANALService.getDeviceManager()?.checkOrInitializeLocationDevices()
    }
    ```

### Step 5: 9-Language Localization Parity
* Add string resource `permission_rationale_precise_location_explanation`:
  * EN: "Precise Location (GPS) is required for workout tracking. Approximate location cannot record distance, speed, route coordinates, or elevation. Please allow Precise Location in permissions."
  * DE: "Der genaue Standort (GPS) ist für die Trainingsaufzeichnung erforderlich. Der ungefähre Standort kann Distanz, Geschwindigkeit, Route und Höhenmeter nicht erfassen. Bitte wähle 'Genau' in den Berechtigungen."
  * ES, FR, IT, JA, NL, PL, PT.

### Step 6: Targeted Unit & Contract Tests
* Extend `ControlTrackingPermissionTest.kt` with tests for coarse-only rejection and fine-location requirement.
* Create `DeviceManagerLocationInitTest.kt` verifying dynamic instantiation and idempotence.
* Create `PermissionLocalizationTest.kt` verifying all 9 locales.
* Execute clean-room full test suite (`./gradlew testDebugUnitTest`).
