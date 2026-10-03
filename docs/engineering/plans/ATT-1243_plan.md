# Architecture & Implementation Plan: Resilient Permission-Gated Location Access & Startup Crash Immunity (ATT-1243 / Gate 3)

## 1. Context & Objectives
In production, Android 9+ (API 28–34) and OEM custom ROMs crashed on cold start with `java.lang.SecurityException: "gps" location provider requires ACCESS_FINE_LOCATION permission` (Crashlytics issue `91baac6c7cecfe1953f184a54816f31e`) when `MainActivityWithNavigation.onCreate()` invoked `LocationManager.getProvider(LocationManager.GPS_PROVIDER)` without pre-checking runtime permissions.

This implementation plan defines the complete architectural design and code modifications across Kotlin UI components and Java sensor modules to enforce resilient permission gating, defensive exception boundaries, and full ASPICE SWE.2 / SWE.3 compliance.

---

## 2. Requirement & Test Specification Traceability
- **Parent Issue**: `ATT-1243` (`[Bug] MainActivityWithNavigation.onCreate`)
- **Fix Version**: `V4.9.38`
- **System Requirement**: `REQ-STB-008` (*Resilient Permission-Gated Location Provider Access & Startup Crash Immunity*) in `docs/requirements.md`
- **Test Specification**: `TST-STB-008` (*Resilient Permission-Gated Location Provider Access & SecurityException Immunity Verification*) in `docs/tests.md`
- **Sub-task**: `ATT-1256` (`[Impl-Plan]`)

---

## 3. Detailed Component Architecture & Modifications

### 3.1 `MainActivityWithNavigation.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`)
1. **Extract Dedicated Safe Helper `checkGpsEnabledIfPermitted()`**:
   - Check `TrainingApplication.trackLocation()`: if false, return immediately.
   - Check `ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED`: if ungranted or revoked, bypass provider lookup cleanly without showing dialogs or throwing exceptions.
   - Obtain `val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return`.
   - Wrap provider resolution and state query inside explicit exception boundaries:
     ```kotlin
     try {
         val provider = locationManager.getProvider(LocationManager.GPS_PROVIDER)
         if (provider != null && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
             showGPSDisabledAlertToUser()
         }
     } catch (e: SecurityException) {
         Log.w(TAG, "SecurityException while checking GPS provider: ${e.message}")
     } catch (e: IllegalArgumentException) {
         Log.w(TAG, "IllegalArgumentException while checking GPS provider: ${e.message}")
     }
     ```
2. **Refactor `onCreate()`**:
   - Replace lines 383–394 with a direct call to `checkGpsEnabledIfPermitted()`.
3. **Enhance `onRequestPermissionsResult()`**:
   - In the `MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION` handler, when `foregroundLocationGranted == true`, invoke `checkGpsEnabledIfPermitted()` so that newly permitted users whose device GPS is turned off receive the prompt seamlessly upon granting permission.

### 3.2 Java Sensor Drivers (`SpeedAndLocationDevice_GPS.java` & `SpeedAndLocationDevice_Network.java`)
1. **`SpeedAndLocationDevice_GPS.java`**:
   - Move `mLocationManager.getProvider(LocationManager.GPS_PROVIDER)` inside the `try { ... } catch (IllegalArgumentException | SecurityException e)` block in both the constructor and `onProviderEnabled()`.
   - Verify `provider != null` before calling `requestLocationUpdates()`.
   - On exception or null provider, log diagnostic warning and invoke `LocationUnavailable()`, ensuring no listener leak or unhandled exception.
2. **`SpeedAndLocationDevice_Network.java`**:
   - Apply identical defensive refactoring for `LocationManager.NETWORK_PROVIDER`.
3. **`DeviceManager.java`**:
   - Add safe helper `isProviderAvailableSafely(LocationManager lm, String provider)` catching `SecurityException` and `IllegalArgumentException` around `locationManager.getProvider(...)`.

---

## 4. Invariants & Safety Verification
1. **Startup Crash Immunity**: Cold starts without location permissions guaranteed 0 crashes across all API levels (API 21 to 34+).
2. **UX Non-Intrusiveness**: Premature "GPS disabled" alerts suppressed during onboarding before user grants permission.
3. **Preference Integrity**: `TrainingApplication.trackLocation()` is never cleared or altered by missing permissions.
4. **Sensor Driver Isolation**: Faulty or revoked permissions during sensor initialization cleanly trigger `LocationUnavailable()` broadcast without hanging background services.

---

## 5. Verification & Testing Strategy
1. **Unit Testing (`LocationPermissionSafetyTest.kt`)**:
   - TC-1: Cold start without permission bypasses `getProvider()` and suppresses alert dialog.
   - TC-2: Cold start with permission granted and GPS enabled checks provider and shows no dialog.
   - TC-3: Cold start with permission granted and GPS disabled triggers `showGPSDisabledAlertToUser()`.
   - TC-4: `SecurityException` during `getProvider()` is caught and logged without crashing.
   - TC-5: `IllegalArgumentException` or null provider handled cleanly.
   - TC-6: Granting permission via `onRequestPermissionsResult` triggers re-check safely.
2. **Driver Resilience Testing (`SpeedAndLocationDeviceResilienceTest.kt`)**:
   - Add tests verifying constructor and `onProviderEnabled` when `getProvider()` throws `SecurityException` or `IllegalArgumentException`.
3. **Full Clean-Room Verification**:
   - Execute `./gradlew testDebugUnitTest` to guarantee 0 regressions across the entire suite.
