# Test Specification - ATT-1278: Altitude Correction - Get Correct Altitude from the Internet

**Ticket**: [ATT-1278](https://rainerblind.atlassian.net/browse/ATT-1278)  
**Sub-task**: [ATT-1368](https://rainerblind.atlassian.net/browse/ATT-1368)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**:
* `app/src/main/java/com/atrainingtracker/trainingtracker/elevation/ElevationService.kt`
* `app/src/main/java/com/atrainingtracker/trainingtracker/elevation/ElevationSource.kt`
* `app/src/main/java/com/atrainingtracker/trainingtracker/elevation/ElevationResult.kt`
* `app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java`
* `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java`
* `app/src/test/java/com/atrainingtracker/trainingtracker/elevation/ElevationServiceTest.kt`
* `app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerTest.kt`

**Requirement**: `REQ-DAT-014` (*Internet Digital Elevation Model (DEM) Reference Altitude Retrieval & Spatial Caching*)  
**Test Spec ID**: `TST-DAT-008`  
**Branch**: `feature/ATT-1278`  

---

## 1. Overview & Verification Strategy

This test specification defines the verification criteria, automated unit test suites, integration edge-case validations, and clean-room regression benchmarks to prove that:
1. `ElevationService` asynchronously queries the Open-Meteo Elevation API on `Dispatchers.IO`, properly quantizes coordinates to 5 decimal places ($\approx 1.1\text{m}$), parses topographic elevation responses accurately, and enforces a strict 5-second timeout.
2. HTTP failure modes (429 Rate Limit with exponential backoff, 503/5xx Server Errors with circuit-breaker protection, socket timeouts, and offline status) degrade gracefully without throwing exceptions or blocking the sensor thread.
3. `KnownLocationsDatabaseManager` successfully executes an atomic schema upgrade from `DB_VERSION = 4` to `DB_VERSION = 5` (adding `is_locked` and `source` columns) adhering strictly to `REQ-DAT-008` (framework automatic transactions).
4. Spatial geofence querying correctly detects existing locations within 200m radius to eliminate redundant network queries.
5. Locations marked with `is_locked == 1` are strictly immutable against automated mutations and refinements.
6. Downstream sensor baseline calibration in `AltitudeFromPressureDevice` applies the retrieved DEM elevation safely, satisfying `REQ-CON-011` and `REQ-CON-013`.

---

## 2. Test Cases & Verification Matrix

### Test Case 1: `testFetchElevation_nominalSuccess_returnsParsedElevation` (Unit Test - `TST-DAT-008.1`)
* **Goal**: Verify that a successful 200 OK response from Open-Meteo parses the elevation float/double array, returns `ElevationResult.Success`, and quantizes input coordinates.
* **Preconditions**:
  - Mock HTTP client or `MockWebServer` configured to respond with `200 OK` and payload `{"elevation": [312.5]}`.
  - Coordinate: latitude `48.137154`, longitude `11.576124`.
* **Action**:
  - Invoke `ElevationService.fetchElevation(48.137154, 11.576124)`.
* **Expected Result**:
  - Result is `ElevationResult.Success`.
  - Returned elevation is `312.5` meters.
  - Request URL queried contains quantized coordinates: `latitude=48.13715&longitude=11.57612`.
  - Zero exceptions thrown.

### Test Case 2: `testSpatialGeofenceCache_whenWithinRadius_reusesCachedAltitude` (Unit Test - `TST-DAT-008.2`)
* **Goal**: Verify that starting a workout within 200m of an existing known location hits the local database cache and skips network lookup.
* **Preconditions**:
  - `KnownLocationsDatabaseManager` populated with location "Home" at `(52.52000, 13.41000)` with `radius = 200m`, `altitude = 45.0m`, and `source = INTERNET_DEM`.
  - Current GPS location at `(52.52050, 13.41050)` (geodesic distance $\approx 65\text{m} < 200\text{m}$).
* **Action**:
  - Invoke `KnownLocationsDatabaseManager.getMyLocation(currentLatLng)`.
* **Expected Result**:
  - Returns existing `MyLocation` record with `id`, `altitude = 45.0m`, and `name = "Home"`.
  - No network call is dispatched.

### Test Case 3: `testFetchElevation_rateLimited429_activatesBackoffAndReturnsError` (Unit Test - `TST-DAT-008.3`)
* **Goal**: Verify proper handling of HTTP 429 Too Many Requests.
* **Preconditions**:
  - Mock server configured to return HTTP `429 Too Many Requests`.
* **Action**:
  - Invoke `ElevationService.fetchElevation(52.52000, 13.41000)`.
* **Expected Result**:
  - Result is `ElevationResult.RateLimited`.
  - Exponential backoff / retry-after is scheduled.
  - Caller falls back to GPS altitude (`source = GPS_FALLBACK`).
  - Zero crashes or ANRs.

### Test Case 4: `testFetchElevation_serverError503_tripsCircuitBreaker` (Unit Test - `TST-DAT-008.4`)
* **Goal**: Verify that consecutive HTTP 503 or 5xx server errors trip the circuit breaker.
* **Preconditions**:
  - Mock server configured to return HTTP `503 Service Unavailable`.
* **Action**:
  - Invoke `ElevationService.fetchElevation(52.52000, 13.41000)`.
* **Expected Result**:
  - Result is `ElevationResult.ServerError(503)`.
  - Circuit breaker trips to OPEN state for 5 minutes.
  - Subsequent calls during the cooldown immediately return `ElevationResult.CircuitBreakerOpen` without opening network connections.

### Test Case 5: `testFetchElevation_timeoutOrOffline_degradesGracefully` (Unit Test - `TST-DAT-008.5`)
* **Goal**: Verify that socket timeouts (>5s) or total lack of internet connectivity degrade gracefully.
* **Preconditions**:
  - Network disconnected or mock client configured with 6-second response delay.
* **Action**:
  - Invoke `ElevationService.fetchElevation(...)`.
* **Expected Result**:
  - Timeout terminates at 5 seconds.
  - Result is `ElevationResult.NetworkError`.
  - System logs warning and applies GPS altitude fallback.
  - Active tracking and sensor initialization proceed without interruption.

### Test Case 6: `testDatabaseUpgrade_fromV4toV5_preservesDataAndAddsColumns` (Unit Test - `TST-DAT-008.6`)
* **Goal**: Verify SQLite schema migration from V4 to V5 adheres to `REQ-DAT-008` (atomic transaction wrapper) and preserves historical data.
* **Preconditions**:
  - SQLite database initialized at V4 schema with test records:
    - Row 1: `name = "Trailhead"`, `altitude = 540.0`, `radius = 200`, `hitCount = 3`.
* **Action**:
  - Trigger `KnownLocationsDbHelper.onUpgrade(db, 4, 5)`.
* **Expected Result**:
  - Columns `is_locked` and `source` exist in `StartLocation2Altitude`.
  - Historical Row 1 has `is_locked == 0` (default) and `source == "AUTO_LEARNED"` (default).
  - Row 1 coordinates, name, altitude, and hitCount are 100% preserved.
  - Migration completes without manual transaction calls (`REQ-DAT-008`).

### Test Case 7: `testLockedLocation_immutabilityAgainstLearnLocation` (Unit Test - `TST-DAT-008.7`)
* **Goal**: Verify that an entry with `is_locked == 1` is strictly protected against automated refinement.
* **Preconditions**:
  - Location in DB with `altitude = 600.0`, `hitCount = 5`, `is_locked = 1`.
* **Action**:
  - Invoke `KnownLocationsDatabaseManager.learnLocation(pos, 520.0, ExtremaType.START)`.
* **Expected Result**:
  - Stored `altitude` remains strictly `600.0`.
  - `hitCount` remains `5`.
  - Zero mutations written to SQLite for that row.

### Test Case 8: `testFullCleanRoomRegressionSuite` (Regression Test - `TST-DAT-008.8`)
* **Goal**: Verify that the entire unit test suite passes with zero regressions.
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: `BUILD SUCCESSFUL`, 632+ tests passed, 0 failures, 0 errors.

---

## 3. Requirement Traceability Matrix

| Test Case | Requirement ID | Verified Functionality | Status |
| :--- | :--- | :--- | :--- |
| `TST-DAT-008.1` | `REQ-DAT-014` | Open-Meteo DEM API retrieval & 5-decimal quantization | Draft |
| `TST-DAT-008.2` | `REQ-DAT-014` | Spatial geofence caching within 200m radius | Draft |
| `TST-DAT-008.3` | `REQ-DAT-014` | HTTP 429 Rate Limit & exponential backoff | Draft |
| `TST-DAT-008.4` | `REQ-DAT-014` | HTTP 503 Server Error & circuit breaker | Draft |
| `TST-DAT-008.5` | `REQ-DAT-014` | 5s Timeout & offline GPS fallback | Draft |
| `TST-DAT-008.6` | `REQ-DAT-008`, `REQ-DAT-014` | Database V4 -> V5 atomic migration | Draft |
| `TST-DAT-008.7` | `REQ-DAT-014` | Locked location immutability invariant | Draft |
| `TST-DAT-008.8` | `REQ-CON-011`, `REQ-CON-013`, `REQ-DAT-014` | Full clean-room test suite regression | Draft |
