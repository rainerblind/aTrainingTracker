# Implementation Plan - ATT-623: Resilient Location Provider Initialization & Device Hardware Fault Tolerance

## 1. Executive Summary & Problem Context
Ticket **ATT-623** addresses a fatal startup crash reported via Firebase Crashlytics:
```
Fatal Exception: java.lang.RuntimeException: Unable to create service com.atrainingtracker.banalservice.BANALService: java.lang.IllegalArgumentException: provider "gps" does not exist
       at android.location.LocationManager.requestLocationUpdates(LocationManager.java:1595)
       at com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_GPS.<init>(SpeedAndLocationDevice_GPS.java:52)
       at com.atrainingtracker.banalservice.devices.DeviceManager.<init>(DeviceManager.java:283)
       at com.atrainingtracker.banalservice.BANALService.onCreate(BANALService.java:493)
```
On devices, custom ROMs, tablets, emulators, or specialized Android environments lacking physical GPS hardware or where the platform `LocationManagerService` does not register the `"gps"` provider, calling `LocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, ...)` unconditionally throws `java.lang.IllegalArgumentException: provider "gps" does not exist`. Because this happens synchronously in `BANALService.onCreate()`, the entire service crashes, preventing the user from using any part of the application.

Furthermore, an identical vulnerability exists in `SpeedAndLocationDevice_Network` for the `"network"` provider, as well as in runtime provider enable callbacks (`onProviderEnabled`).

---

## 2. Requirement & Test Mapping
- **Requirement**: `REQ-STB-004` (*Resilient Location Provider Initialization & Device Hardware Fault Tolerance*)
- **Verification Test**: `TST-STB-004` (*Location Provider Availability & Exception Shielding Verification*)
- **Jira Tickets**: `ATT-623` (Parent), `ATT-640` (RCA - Erledigt), `ATT-641` (Plan), `ATT-642` (Test)

---

## 3. Architecture & Technical Design

### A. SpeedAndLocationDevice_GPS Hardening
1. **Constructor**:
   - Query `mLocationManager.getProvider(LocationManager.GPS_PROVIDER) != null`.
   - Wrap `requestLocationUpdates(...)` in a `try ... catch (IllegalArgumentException | SecurityException e)` block.
   - If `mLocationManager` is null, provider does not exist, or `requestLocationUpdates` fails:
     - Log a clear warning: `Log.w(TAG, "GPS provider not available or registration failed: " + e.getMessage());`.
     - Invoke `LocationUnavailable()` to ensure internal state is consistent.
     - Return cleanly without throwing an unhandled exception.
2. **Provider Lifecycle Callbacks (`onProviderEnabled`, `onProviderDisabled`)**:
   - In `onProviderEnabled(provider)`: Check `mLocationManager != null && mLocationManager.getProvider(LocationManager.GPS_PROVIDER) != null`, wrap `requestLocationUpdates()` in `try ... catch (IllegalArgumentException | SecurityException e)`.
   - In `onProviderDisabled(provider)`: Check `mLocationManager != null`, wrap `removeUpdates(this)` in `try ... catch (Exception e)`.
3. **Shutdown Safety**:
   - In `shutDown()`: Null-check `mLocationManager` and wrap `removeUpdates(this)` in `try ... catch (Exception e)`.

### B. SpeedAndLocationDevice_Network Hardening
- Apply the identical defensive pattern for `LocationManager.NETWORK_PROVIDER`:
  - Constructor: Check `mLocationManager != null && mLocationManager.getProvider(LocationManager.NETWORK_PROVIDER) != null`; catch `IllegalArgumentException` and `SecurityException`.
  - `onProviderEnabled()` and `onProviderDisabled()`: Provider checks and exception guards.
  - `shutDown()`: Null-check and safe `removeUpdates(this)`.

### C. DeviceManager Fault Isolation
1. **Safe Instantiation Preconditions**:
   - In `DeviceManager.<init>`:
     - Before instantiating `SpeedAndLocationDevice_GPS`, verify that `locationManager.getProvider(LocationManager.GPS_PROVIDER) != null`.
     - Before instantiating `SpeedAndLocationDevice_Network`, verify that `locationManager.getProvider(LocationManager.NETWORK_PROVIDER) != null`.
2. **Dynamic Pairing Changes (`pairingChanged`)**:
   - Apply the same provider availability checks before creating `SpeedAndLocationDevice_GPS` or `SpeedAndLocationDevice_Network` when paired.
3. **Fault Containment**:
   - Wrap sensor/device creation blocks in localized try-catch blocks with descriptive logging so that an anomaly in one hardware sensor never aborts `BANALService.onCreate()` or stops other devices (ANT+, BLE, heart rate, cadence, power) from functioning.

### D. MainActivityWithNavigation Alert Guard
- In `MainActivityWithNavigation.onCreate()`:
  - Check `locationManager != null && locationManager.getProvider(LocationManager.GPS_PROVIDER) != null` before calling `isProviderEnabled(LocationManager.GPS_PROVIDER)`.
  - Prevents showing a confusing "GPS is disabled" prompt on devices that do not have GPS hardware.

---

## 4. Implementation Steps & File Modifications

| Component | File Path | Scope of Modification |
| :--- | :--- | :--- |
| **GPS Device** | [`SpeedAndLocationDevice_GPS.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_GPS.java) | Add `getProvider` check, wrap `requestLocationUpdates` in try-catch, handle missing provider gracefully with `LocationUnavailable()`. |
| **Network Device** | [`SpeedAndLocationDevice_Network.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_Network.java) | Add `getProvider` check, wrap `requestLocationUpdates` in try-catch, handle missing provider gracefully with `LocationUnavailable()`. |
| **Device Manager** | [`DeviceManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceManager.java) | Check provider availability before instantiating GPS and Network location devices; isolate initialization errors. |
| **Navigation UI** | [`MainActivityWithNavigation.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.java) | Verify GPS provider existence before querying `isProviderEnabled()`. |
| **Documentation** | [`docs/requirements.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) | Add `REQ-STB-004` specification. |
| **Documentation** | [`docs/tests.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) | Add `TST-STB-004` test specification. |
| **Unit Tests** | `app/src/test/.../SpeedAndLocationDeviceResilienceTest.kt` | Unit tests for missing providers, `IllegalArgumentException`, `SecurityException`, and `DeviceManager` resilience. |

---

## 5. System Invariants & Regression Safety Checklist
- [x] **GPS Device Tracking Invariant**: Standard devices with functioning GPS hardware continue tracking at the exact same rate (`SAMPLING_TIME = 1000ms`, `MIN_DISTANCE = 0m`) without behavioral difference.
- [x] **Service Startup Invariant**: `BANALService.onCreate()` must never throw unhandled runtime exceptions during device initialization.
- [x] **Non-GPS Sensor Continuity Invariant**: ANT+, BLE, pressure, and Google Fused location sensors operate normally regardless of GPS hardware presence.
- [x] **Clean Error Logging**: When a provider is absent or registration fails, a clear warning is logged with no stack trace noise or unhandled crashes.

---

## 6. Verification & Test Strategy
1. **Automated Unit Tests (`SpeedAndLocationDeviceResilienceTest.kt`)**:
   - `testGpsDevice_missingGpsProvider_initializesCleanlyWithoutCrashing`: Verify that when `getProvider(GPS_PROVIDER)` returns `null`, `SpeedAndLocationDevice_GPS` does not invoke `requestLocationUpdates` and does not throw.
   - `testGpsDevice_requestLocationUpdatesThrowsIllegalArgument_caughtCleanly`: Verify that when `requestLocationUpdates` throws `IllegalArgumentException: provider "gps" does not exist`, the exception is caught, logged, and `LocationUnavailable()` is called.
   - `testGpsDevice_requestLocationUpdatesThrowsSecurityException_caughtCleanly`: Verify graceful handling of `SecurityException`.
   - `testNetworkDevice_missingNetworkProvider_initializesCleanlyWithoutCrashing`: Verify identical protection for `SpeedAndLocationDevice_Network`.
   - `testNetworkDevice_requestLocationUpdatesThrowsIllegalArgument_caughtCleanly`: Verify `SpeedAndLocationDevice_Network` catches `IllegalArgumentException: provider "network" does not exist`.
   - `testDeviceManager_noGpsProvider_doesNotInstantiateGpsDevice`: Verify `DeviceManager` skips instantiation or isolates failure when provider is absent.
2. **Regression Suite**:
   - Execute `./gradlew testDebugUnitTest` to ensure all existing tests pass with 0 regressions.
3. **Physical / Emulator Verification**:
   - Build and test debug APK.
