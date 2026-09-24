# Implementation Plan - ATT-1366: Auto Learned Altitude Does Not Work Properly

## 1. Executive Summary & Objective

* **Issue Key**: `ATT-1366` / `ATT-1374`
* **Parent Epic**: `ATT-235` / Sensor Calibration & Data Integrity
* **Target Version**: `V4.9.38` (Sprint `2026-39.2`)
* **Associated Requirements**: `REQ-DAT-007` (Automated Altitude Reference Discovery & Stable Reference Elevation Preservation), `REQ-DAT-014`, `REQ-CON-011`, `REQ-CON-013`
* **Associated Verification Test**: `TST-DAT-009`

### Core Objective
Eliminate the mathematically and meteorologically flawed running mean altitude calculation in `KnownLocationsDatabaseManager.learnLocation()`. Ensure that reference elevations (particularly authoritative `INTERNET_DEM` and `MANUAL_USER` entries) are strictly preserved against barometric weather pressure drift, while continuing to track location usage frequency via `hitCount` increments. Introduce explicit SQLite transaction boundaries to prevent TOCTOU race conditions between synchronous sensor starts and asynchronous DEM resolution.

---

## 2. Impact Analysis & Call-Site Audit (SWE.1.BP.5 Phase)

### A. Call-Site Audit (`find_usages` / `grep`)
1. **`KnownLocationsDatabaseManager.learnLocation(LatLng, Double, ExtremaType)`**:
   - `AltitudeFromPressureDevice.java:159`: Currently invoked unconditionally at workout start.
   - `AltitudeFromPressureDeviceTest.kt:266`: Mocks and verifies `learnLocation` invocation.
   - `KnownLocationsDatabaseManagerTest.kt:222`: Verifies immutability of locked records.
2. **`AltitudeFromPressureDevice.initPressureSensor()`**:
   - Manages barometric sensor calibration at workout start.
   - Must be decoupled from synchronous speculative uncalibrated location creation.

### B. Mapped Requirements Verification
* **`REQ-DAT-007`**: Fulfills new specification (zero altitude mutation, hitCount frequency tracking, atomic geofence upsert).
* **`REQ-DAT-014`**: Fully preserved (Copernicus 30m DEM ground-truth, 200m spatial caching, schema V5 defaults, legacy healing).
* **`REQ-CON-011`**: Fully preserved (cold-start baseline protection ensures standard atmosphere values are never broadcast prior to calibration).
* **`REQ-CON-013`**: Fully preserved (defensive resolution and null-safety during altitude correction calculation).
* **`REQ-DAT-008`**: Fully preserved (`SQLiteOpenHelper.onUpgrade()` remains untouched with zero manual transaction management).

### C. System Risk Assessment
* **Concurrency / Deadlock Risk**: `KnownLocationsDatabaseManager` methods already synchronize on `this`. Adding an explicit SQLite transaction boundary inside the monitor is safe because transactions are thread-confined and execute in < 2ms without holding network locks.
* **Backward Compatibility**: Database remains at **Schema V5** (`DB_VERSION = 5`). No table modifications or data migrations are required.
* **Battery / Performance**: CPU overhead is reduced by replacing floating-point running mean math with a simple integer increment (`hitCount + 1`).

---

## 3. Detailed Architectural & Technical Changes

### Component 1: `KnownLocationsDatabaseManager.java`
1. **Eliminate Running Mean Altitude Computation in `learnLocation()`**:
   - In `learnLocation(LatLng pos, Double altitude, ExtremaType type)`:
     - Wrap database operations in an explicit SQLite transaction (`db.beginTransaction()` / `db.setTransactionSuccessful()` / `db.endTransaction()`).
     - Query existing location within 200m radius (`getMyLocation(pos)`).
     - If location exists:
       - If `existing.isLocked`: do not modify any fields (`return`).
       - If not locked: update **ONLY** `hitCount = existing.hitCount + 1`. Do **NOT** mutate `altitude`.
     - If location does not exist:
       - Insert new location with `hitCount = 1`, preserving the passed altitude and marking source as `AUTO_LEARNED` (or fallback).
2. **Atomic Geofence Upsert Method**:
   - Provide an atomic upsert helper `upsertLocationByGeofence(LatLng latLng, double altitude, String name, ExtremaType type, ElevationSource source, boolean isLocked)` wrapped in an explicit SQLite transaction.
   - Checks for existing location within 200m radius under the transaction lock:
     - If exists and unlocked: updates `altitude` (only if upgrading to authoritative DEM) and increments `hitCount`.
     - If not exists: inserts a new record with the authoritative DEM altitude.
3. **Session-Level Idempotency Guard**:
   - Maintain an `AtomicBoolean sHealingDispatched = new AtomicBoolean(false)` to prevent duplicate legacy healing sweeps within a single application process session.

### Component 2: `AltitudeFromPressureDevice.java`
1. **Eliminate Synchronous `AUTO_LEARNED` Cold-Start Race**:
   - In `initPressureSensor()`:
     - When `myLocation != null`: apply reference altitude, update sensor, dispatch `healLocationAsync(myLocation)` if uncalibrated, and call `knownLocationsDb.learnLocation(currentLatLng, mLastRawAltitude, ExtremaType.START)` to increment `hitCount`.
     - When `myLocation == null`: dispatch `fetchDemOrFallbackAsync(latitude, longitude)`. Do **NOT** synchronously call `learnLocation()` with `mLastRawAltitude`.
2. **Thread-Safe DEM Upsert in `fetchDemOrFallbackAsync()`**:
   - When Open-Meteo returns DEM elevation, invoke `knownLocationsDb.upsertLocationByGeofence()` to atomically insert or update without racing against workout starts.

---

## 4. Test Implementation Plan (`TST-DAT-009`)

### Unit Tests in `KnownLocationsDatabaseManagerTest.kt`:
1. **`testLearnLocation_preservesExistingAltitude_andIncrementsHitCount`** (`TST-DAT-009.1`):
   - Insert an entry with `altitude = 520.0m`, `hitCount = 2`, `source = INTERNET_DEM`.
   - Call `manager.learnLocation(pos, 470.0, ExtremaType.START)`.
   - Assert `altitude` remains strictly `520.0m` (not mutated to 486.6m).
   - Assert `hitCount` becomes `3`.
2. **`testLearnLocation_lockedRecord_strictlyImmutable`** (`TST-DAT-009.2`):
   - Insert entry with `is_locked = 1`.
   - Call `manager.learnLocation(pos, 600.0, ExtremaType.START)`.
   - Assert 0 updates and 0 inserts executed.
3. **`testConcurrentUpsert_eliminatesTOCTOURace`** (`TST-DAT-009.3`):
   - Concurrently trigger `learnLocation()` and `upsertLocationByGeofence()` on 2 threads using `CountDownLatch`.
   - Assert exactly 1 row exists in SQLite with no constraint violations.

### Integration Tests in `AltitudeFromPressureDeviceTest.kt`:
4. **`testInitPressureSensor_whenLocationNotFound_doesNotCallLearnLocation`** (`TST-DAT-009.4`):
   - Verify `fetchDemOrFallbackAsync()` is triggered without calling `learnLocation()` synchronously.

---

## 5. ASPICE Traceability Matrix

| Requirement ID | Component Slated for Edit | Test Verification ID |
| :--- | :--- | :--- |
| `REQ-DAT-007` | `KnownLocationsDatabaseManager.java` | `TST-DAT-009.1`, `TST-DAT-009.2`, `TST-DAT-009.3` |
| `REQ-DAT-007` | `AltitudeFromPressureDevice.java` | `TST-DAT-009.4` |
| `REQ-DAT-014` | `KnownLocationsDatabaseManager.java` | `TST-DAT-008` (Regression) |
| `REQ-CON-011` | `AltitudeFromPressureDevice.java` | `TST-CON-002` (Regression) |
| `REQ-CON-013` | `AltitudeFromPressureDevice.java` | `TST-CON-004` (Regression) |
