# Stage 2 Requirement & Test Specification: ATT-3045 - App does not receive location updates when starting tracking immediately after granting permissions on fresh install

**Ticket**: [ATT-3045](https://atrainingtracker.atlassian.net/browse/ATT-3045)  
**Sub-task**: [ATT-3083](https://atrainingtracker.atlassian.net/browse/ATT-3083) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2617](https://atrainingtracker.atlassian.net/browse/ATT-2617) (*Track Telemetry: Signal Quality, Sensor Redundancy & Anomaly Detection*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Formal Requirement Specification (`REQ-TRK-017`)

### 1.1 Requirement Statement
The system SHALL guarantee that location sensor devices (`SpeedAndLocationDevice_GPS` and `SpeedAndLocationDevice_GoogleFused`) are defensively initialized, verified, and attached when tracking starts, even if tracking is initiated immediately after granting location permissions on a fresh install or when permissions were granted while `BANALService` was not yet bound (ATT-3045):

1. **Defensive Location Device Initialization in `TrackerService`**:
   - In `TrackerService.java`, upon service initialization (`onCreate` / `onStartCommand`) and upon `BANALService` connection establishment (`mBanalConnection.onServiceConnected`), the service SHALL invoke `BANALService.checkOrInitializeLocationDevices()`.
   - If `mBanalService` is bound and active, `TrackerService` SHALL verify that `cDeviceManager.checkOrInitializeLocationDevices()` has been executed before searching finishes and sensor listeners are queried.

2. **Dynamic Location Device Instantiation in `DeviceManager`**:
   - In `DeviceManager.java`, `checkOrInitializeLocationDevices()` SHALL check `TrainingApplication.havePermission(ACCESS_FINE_LOCATION)`.
   - If permission is granted:
     - If `mSpeedAndLocationDevice_GPS == null`, it SHALL instantiate `mSpeedAndLocationDevice_GPS = new SpeedAndLocationDevice_GPS(mContext)` and register its sensors (`SensorType.SPEED`, `SensorType.DISTANCE`, `SensorType.GPS`, `SensorType.ALTITUDE`) into `MySensorManager`.
     - If `mSpeedAndLocationDevice_GoogleFused == null`, it SHALL instantiate `mSpeedAndLocationDevice_GoogleFused = new SpeedAndLocationDevice_GoogleFused(mContext)` and register its sensors into `MySensorManager`.
   - If tracking is actively running or starting, `checkOrInitializeLocationDevices()` SHALL verify that `mSpeedAndLocationDevice_GPS` and `mSpeedAndLocationDevice_GoogleFused` have their location update listeners active.

3. **GPS Re-registration Resilience in `SpeedAndLocationDevice_GPS`**:
   - `SpeedAndLocationDevice_GPS.java` SHALL provide a defensive re-registration mechanism (`checkOrReRegisterLocationUpdates()`) to verify that `mLocationManager.requestLocationUpdates(...)` is active when permissions are present, even if initial registration failed due to timing, disabled providers, or permission latency.

4. **Sensor Flag Parity in Tracking Telemetry**:
   - When location tracking is successfully initiated on a fresh install with granted permissions, `TrackerService.getGCDataString()` SHALL contain `'G'`, `'D'`, and `'S'` flags (`GCData = 'T-G--DAS---'`).
   - The first and subsequent recorded trackpoints in Room database (`trackpoints` table) SHALL contain non-null, valid latitude, longitude, and incremental distance values.

5. **System Invariants**:
   - Mock location filtering (`REQ-LOC-004`), GPS accuracy validation, existing Bluetooth/ANT+ device lifecycle, foreground notification channels, and 100% full clean-room unit test pass rate MUST be strictly preserved.

---

## 2. Test Specification (`TST-TRK-009`)

### 2.1 Acceptance Criteria (Given-When-Then)

- **Criterion 1 (Permission Escalation Lazy GPS Instantiation)**:
  - *Given* an app on a fresh install where `ACCESS_FINE_LOCATION` was initially denied or not yet granted when `DeviceManager` was constructed,
  - *When* the athlete grants location permissions and `DeviceManager.checkOrInitializeLocationDevices()` is executed,
  - *Then* `mSpeedAndLocationDevice_GPS` is instantiated, and `SensorType.GPS`, `SPEED`, `DISTANCE`, and `ALTITUDE` are registered in `MySensorManager`.

- **Criterion 2 (TrackerService Connection Initialization Flow)**:
  - *Given* an athlete who grants location permission and immediately taps "Start Tracking",
  - *When* `TrackerService` starts and establishes connection with `BANALService` via `mBanalConnection.onServiceConnected()`,
  - *Then* `BANALService.checkOrInitializeLocationDevices()` is invoked immediately, ensuring location devices exist prior to `onSearchingFinished()`.

- **Criterion 3 (GCData Telemetry Completeness)**:
  - *Given* an active tracking session started immediately post-permission grant,
  - *When* `TrackerService.getGCDataString()` is queried,
  - *Then* the telemetry descriptor string contains `'G'` (GPS), `'D'` (Distance), and `'S'` (Speed).

- **Criterion 4 (Clean-Room Test Suite Non-Regression)**:
  - *Given* the complete unit test suite across all modules,
  - *When* executed via `./gradlew testDebugUnitTest`,
  - *Then* 100% of unit tests pass with 0 failures and 0 regressions.

---

## 3. Targeted Test Verification Plan

1. **Unit Tests (`FreshInstallLocationInitTest.kt`)**:
   - `testDeviceManager_whenPermissionsGrantedLater_checkOrInitializeInstantiatesLocationDevices()`:
     - Verifies `checkOrInitializeLocationDevices()` creates `SpeedAndLocationDevice_GPS` when permission is granted post-construction.
   - `testTrackerService_onServiceConnected_invokesLocationDeviceInitialization()`:
     - Verifies `TrackerService` defensive call to `checkOrInitializeLocationDevices()` upon `onServiceConnected()`.
   - `testSpeedAndLocationDeviceGPS_reregisterUpdates_whenLocationManagerAvailable()`:
     - Verifies resilient update request re-attachment.

2. **Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` across all modules.
