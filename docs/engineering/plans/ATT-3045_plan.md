# Stage 3 Implementation Plan: ATT-3045 - App does not receive location updates when starting tracking immediately after granting permissions on fresh install

**Ticket**: [ATT-3045](https://atrainingtracker.atlassian.net/browse/ATT-3045)  
**Sub-task**: [ATT-3084](https://atrainingtracker.atlassian.net/browse/ATT-3084) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2617](https://atrainingtracker.atlassian.net/browse/ATT-2617) (*Track Telemetry: Signal Quality, Sensor Redundancy & Anomaly Detection*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Architectural Overview & SWE.2 Design

### 1.1 Problem Summary
On a fresh install of the application:
1. `BANALService` and `DeviceManager` are instantiated during early app startup before the athlete has been prompted for Android runtime permissions.
2. `DeviceManager` evaluates `TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)`, which returns `false`.
3. Consequently, `mSpeedAndLocationDevice_GPS` and `mSpeedAndLocationDevice_GoogleFused` are not instantiated and remain `null`.
4. When the athlete grants location permissions on the tracking screen and immediately taps "Start Tracking":
   - `TrainingApplication.startTracking()` launches `TrackerService`.
   - `TrackerService` binds to `BANALService`.
   - In `mBanalConnection.onServiceConnected()`, `TrackerService` immediately executes `onSearchingFinished()` when searching is inactive, reading `mBanalService.getGCDataString()`.
   - Neither `TrainingApplication.startTracking()` nor `TrackerService.onServiceConnected()` / `onCreate()` invoked `BANALService.checkOrInitializeLocationDevices()`.
   - `DeviceManager`'s GPS and Fused location devices remain `null`.
   - No location sensors are registered in `MySensorManager`, yielding null trackpoint coordinates (`GCData = 'T------A---'`).

### 1.2 Multi-Layered Defensive Architecture
To ensure deterministic, race-free location device initialization across all application entry points:
1. **`DeviceManager.java`**:
   - Enhance `checkOrInitializeLocationDevices()`:
     - Check `TrainingApplication.havePermission(ACCESS_FINE_LOCATION)`.
     - If `mSpeedAndLocationDevice_GPS == null && devicesDatabaseManager.isPaired(gpsDeviceId)`: instantiate `SpeedAndLocationDevice_GPS(mContext, mSensorManager)`.
     - If `mSpeedAndLocationDevice_GPS != null`: invoke `mSpeedAndLocationDevice_GPS.checkOrReRegisterLocationUpdates()` to re-attempt location update attachment if previously failed or disabled.
     - If `mSpeedAndLocationDevice_GoogleFused == null && devicesDatabaseManager.isPaired(fusedDeviceId)`: instantiate `SpeedAndLocationDevice_GoogleFused(mContext, mSensorManager)`.
     - Also check `mSpeedAndLocationDevice_Network` for coarse location if paired.
2. **`SpeedAndLocationDevice_GPS.java`**:
   - Add `public synchronized void checkOrReRegisterLocationUpdates()`:
     - Checks if `mLocationManager` exists and provider `GPS_PROVIDER` is available.
     - If `ACCESS_FINE_LOCATION` is granted and updates were not attached (or failed previously), request location updates defensively with error logging.
3. **`TrackerService.java`**:
   - In `onCreate()`: invoke `BANALService.checkOrInitializeLocationDevices()`.
   - In `mBanalConnection.onServiceConnected()`: invoke `BANALService.checkOrInitializeLocationDevices()` prior to calling `onSearchingFinished()`.
4. **`TrainingApplication.java`**:
   - In `startTracking()`: invoke `BANALService.checkOrInitializeLocationDevices()`.

---

## 2. Atomic Implementation Steps

### Step 1: Enhance `SpeedAndLocationDevice_GPS.java`
- Introduce state flag `mUpdatesRegistered = false`.
- Set `mUpdatesRegistered = true` upon successful `requestLocationUpdates`.
- Add `public synchronized void checkOrReRegisterLocationUpdates()` to re-request location updates if `!mUpdatesRegistered`.
- Reset `mUpdatesRegistered = false` in `shutDown()`.

### Step 2: Update `DeviceManager.java`
- In `checkOrInitializeLocationDevices()`:
  - If `mSpeedAndLocationDevice_GPS != null`, invoke `mSpeedAndLocationDevice_GPS.checkOrReRegisterLocationUpdates()`.
  - If `mSpeedAndLocationDevice_GPS == null`, create it if permission and provider available.
  - If `mSpeedAndLocationDevice_GoogleFused == null`, create it if permission and Play Services available.
  - If `mSpeedAndLocationDevice_Network == null`, create it if coarse permission and network provider available.

### Step 3: Wire Defensive Triggers in `TrackerService.java` and `TrainingApplication.java`
- In `TrackerService.onCreate()`, invoke `BANALService.checkOrInitializeLocationDevices()`.
- In `TrackerService.mBanalConnection.onServiceConnected()`, invoke `BANALService.checkOrInitializeLocationDevices()` before `onSearchingFinished()`.
- In `TrainingApplication.startTracking()`, invoke `BANALService.checkOrInitializeLocationDevices()`.

### Step 4: Author Contract & Unit Tests (`FreshInstallLocationInitTest.kt`)
- Verify lazy instantiation of `SpeedAndLocationDevice_GPS` when permissions are granted post-init.
- Verify `checkOrReRegisterLocationUpdates()` recovers update listener registration.
- Verify `TrackerService` flow initializes location devices upon `onServiceConnected()`.

### Step 5: Full Clean-Room Regression Verification
- Run `./gradlew testDebugUnitTest` verifying 0 failures and 0 regressions.

---

## 3. UI Consistency Audit (Rule 23)

N/A - Non-UI backend/logic ticket; does not introduce or modify any UI components. Affects backend sensor management and tracking services (`DeviceManager.java`, `SpeedAndLocationDevice_GPS.java`, `TrackerService.java`, `TrainingApplication.java`).

---

## 4. Invariant Preservation Checklist

- [x] Mock location detection (`REQ-LOC-004`) unchanged.
- [x] Battery and clock device lifecycles preserved.
- [x] Room database trackpoint persistence unchanged.
- [x] BLE/ANT+ device discovery and pairing unchanged.
- [x] 100% full unit test pass rate preserved.
