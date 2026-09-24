# Walkthrough - ATT-1278: Altitude Correction: Get Correct Altitude from the Internet

## 1. Executive Summary

Under **ATT-1278** (the first stage of the altitude cluster: `ATT-1278`, `ATT-1366`, `ATT-919`), the system has been equipped with an authoritative, open Digital Elevation Model (DEM) client using the Open-Meteo Elevation API. This establishes ground-truth reference altitudes for workout start locations to compensate for barometric pressure differences without relying on flawed moving average algorithms.

All five ASPICE engineering stages for **ATT-1278** were executed rigorously:
1. **Analysis** (`ATT-1367`): [docs/engineering/analysis/ATT-1278_analysis.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/analysis/ATT-1278_analysis.md) (Gate 1 Passed).
2. **Test-Spec** (`ATT-1368`): [docs/engineering/test_specs/ATT-1278_test_spec.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/test_specs/ATT-1278_test_spec.md) (Gate 2 Passed).
3. **Impl-Plan** (`ATT-1369`): [docs/engineering/plans/ATT-1278_plan.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/plans/ATT-1278_plan.md) (Gate 3 Passed).
4. **Implementation** (`ATT-1370`): Commit `9ea6eadf` on `feature/ATT-1278` (Gate 4 Passed).
5. **Test Execution** (`ATT-1371`): Commit `cd31e018` verifying living documentation parity (Gate 5 Passed).

---

## 2. Changes Implemented

### A. Digital Elevation Model (DEM) Client Architecture
Created package `com.atrainingtracker.trainingtracker.elevation`:
* [ElevationSource.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/elevation/ElevationSource.kt):
  Enumerates the provenance of stored altitudes: `AUTO_LEARNED`, `INTERNET_DEM`, `MANUAL_USER`, `GPS_FALLBACK`, `LEGACY_RAW`.
* [ElevationResult.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/elevation/ElevationResult.kt):
  Sealed model capturing:
  - `Success(elevationMeters, latitude, longitude)`
  - `BatchSuccess(elevations)`
  - `RateLimited(retryAfterSeconds)`
  - `ServerError(statusCode)`
  - `CircuitBreakerOpen`
  - `NetworkError(message)`
* [ElevationService.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/elevation/ElevationService.kt):
  - **Open-Meteo REST API**: Uses `https://api.open-meteo.com/v1/elevation` (Copernicus 30m/90m & SRTM).
  - **Coordinate Quantization**: Quantizes coordinates to 5 decimal places ($\approx 1.1\text{m}$ precision at the equator) via `quantizeCoordinate()` to eliminate microscopic GPS jitter and avoid redundant API requests.
  - **Fault Resilience & Circuit Breaker**:
    - 5-second connection and read timeouts.
    - Automatic exponential backoff on HTTP 429 (`Retry-After`).
    - 5-minute circuit breaker on HTTP 503 or 5xx server errors.
    - Multi-coordinate batch querying (`fetchBatchElevations`) for legacy location healing.

---

### B. Database Schema Evolution (Schema V5) & Legacy Healing
Updated [KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java):
* **Schema Upgrade (`DB_VERSION = 5`)**:
  - Incremented version from 4 to 5.
  - Added columns `is_locked INTEGER DEFAULT 0` and `source TEXT DEFAULT 'LEGACY_RAW'` via `ALTER TABLE`.
  - Adhered strictly to `REQ-DAT-008`: zero manual calls to `beginTransaction()` or `setTransactionSuccessful()`, delegating 100% to Android's framework transaction wrapper.
* **Locked Location Immutability**:
  - Enforced in `learnLocation()`: if `existing.isLocked` is true, or if `source` is already `INTERNET_DEM` or `MANUAL_USER`, auto-refinement is strictly bypassed.
* **Batch Healing (`healLegacyLocations` / `healLegacyLocationsAsync`)**:
  - Automatically triggered on application startup in [TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java#L1011) via a dedicated background thread (`LegacyLocationHealer`), as well as during sensor initialization in [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java#L150).
  - Queries all unlocked records where `is_locked == 0 AND (source = 'LEGACY_RAW' OR source = 'AUTO_LEARNED' OR source IS NULL)`.
  - Chunks coordinates into URL-safe batches of 50 points to respect HTTP URL length limitations.
  - Queries `ElevationService.fetchBatchElevations()` per chunk and atomically updates rows with authoritative DEM elevations, setting their source to `INTERNET_DEM`.

---

### C. Sensor Device Calibration Integration
Updated [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java):
* **Non-Blocking Architecture**:
  - Delegated all asynchronous DEM lookups and healing calls to a dedicated single-threaded `ExecutorService`.
  - Guaranteed that the hardware sensor binder thread (`onSensorChanged`) is never blocked by network calls.
* **Immediate Baseline Application**:
  - If a known location exists in `StartLocation2Altitude.db`, cached reference altitude is applied immediately to avoid tracking startup latency.
  - Background healing is dispatched asynchronously if the cached record is unhealed (`LEGACY_RAW`).
* **Clean Fallback**:
  - If network or API is unavailable, gracefully falls back to GPS altitude.

---

## 3. Verification & Test Evidence

### A. Automated Unit Tests
* [ElevationServiceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/elevation/ElevationServiceTest.kt):
  - `testFetchElevation_nominalSuccess` (`TST-DAT-008.1`): Verified 200 OK parsing, 5-decimal quantization, and `ElevationResult.Success`.
  - `testCoordinateQuantization`: Verified quantization helper with jitter coordinates.
  - `testFetchElevation_rateLimited429` (`TST-DAT-008.3`): Verified 429 header parsing and circuit breaker trip.
  - `testFetchElevation_serverError503_circuitBreaker` (`TST-DAT-008.4`): Verified 503 circuit breaker and fast-fail during cooldown.
  - `testFetchElevation_timeoutOfflineFallback` (`TST-DAT-008.5`): Verified 5s timeout socket handling.
  - `testFetchBatchElevations_nominalSuccess`: Verified multi-point batch elevation querying.
* [KnownLocationsDatabaseManagerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerTest.kt):
  - `testSpatialGeofenceCache_hitWithinRadius` (`TST-DAT-008.2`): Verified 200m spatial geofence caching and coordinate jitter quantization.
  - `testDatabaseUpgrade_V4toV5` (`TST-DAT-008.6`): Verified non-destructive atomic V4->V5 upgrade with column defaults and zero manual transaction calls.
  - `testLockedLocation_immutability` (`TST-DAT-008.7`): Verified `is_locked == 1` records reject modification in `learnLocation()`.
  - `testLegacyBatchHealing_updatesUnlockedToDEM` (`TST-DAT-008.8`): Verified batch query heals unlocked `LEGACY_RAW` rows to `INTERNET_DEM`.
  - `testLegacyBatchHealing_chunksRequestsAtFifty`: Verified batch healing divides large datasets (>50) into sequential 50-item batches.
  - `testLegacyBatchHealing_whenNoLegacyRows_returnsZero`: Verified zero network overhead when database is already healed.
  - `testAddNewLocation_withLockAndSource`: Verified persistence of lock and source fields.

### B. Clean-Room Regression
```bash
./gradlew testDebugUnitTest
```
* **Result**: `BUILD SUCCESSFUL` (32 actionable tasks, 0 failures, 0 errors, 0 regressions across all project test suites).

### C. Governance Verification
```bash
python3 tools/verify_requirement_governance.py
```
* **Result**: Passed (exit code 0).
* [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): `REQ-DAT-014` updated to `Verified`.
* [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): `TST-DAT-008` updated to `Verified`.

---

## 4. Preserved Invariants & Anti-Regression Rules

1. **Cold-Start Baseline Protection (`REQ-CON-011`)**: Uncalibrated standard atmosphere values are never emitted before baseline calibration.
2. **Null-Safe Correction Dispatch (`REQ-CON-013`)**: Defensive resolution prevents unboxing NPEs.
3. **Atomic SQLite Upgrades (`REQ-DAT-008`)**: Migration relies exclusively on Android's automatic transaction wrapper inside `SQLiteOpenHelper.onUpgrade()`.
4. **Locked Location Immutability**: Any record with `is_locked == 1` or authoritative source is strictly protected against automated overwrites.
5. **Non-Blocking Sensor Processing**: Hardware sensor callbacks are never delayed by network I/O.
