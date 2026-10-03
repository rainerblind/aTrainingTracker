# Implementation Plan - ATT-1278: Altitude Correction - Get Correct Altitude from the Internet

**Ticket**: [ATT-1278](https://rainerblind.atlassian.net/browse/ATT-1278)  
**Sub-task**: [ATT-1369](https://rainerblind.atlassian.net/browse/ATT-1369)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**:
* `app/src/main/java/com/atrainingtracker/trainingtracker/elevation/ElevationSource.kt`
* `app/src/main/java/com/atrainingtracker/trainingtracker/elevation/ElevationResult.kt`
* `app/src/main/java/com/atrainingtracker/trainingtracker/elevation/ElevationService.kt`
* `app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java`
* `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java`
* `app/src/test/java/com/atrainingtracker/trainingtracker/elevation/ElevationServiceTest.kt`
* `app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerTest.kt`

**Requirement**: `REQ-DAT-014` (*Internet Digital Elevation Model (DEM) Reference Altitude Retrieval, Spatial Caching & Legacy Location Healing*)  
**Test Spec ID**: `TST-DAT-008`  
**Branch**: `feature/ATT-1278`  

---

## 1. Architectural Strategy & Component Boundaries (SWE.2)

To cleanly separate concerns, prevent UI/sensor thread blocking, and preserve architectural layering:
1. **Network & DEM Layer (`com.atrainingtracker.trainingtracker.elevation`)**:
   - `ElevationService`: Standalone, stateless, singleton HTTP service communicating with the Open-Meteo Elevation REST API.
   - Enforces 5-decimal coordinate quantization ($\approx 1.1\text{m}$), 5-second socket timeouts, exponential backoff on HTTP 429, and a 5-minute circuit breaker on HTTP 503/5xx.
   - Decoupled from Android UI; runs strictly on `Dispatchers.IO`.
2. **Spatial Persistence Layer (`com.atrainingtracker.trainingtracker.database`)**:
   - `KnownLocationsDatabaseManager`: Manages `StartLocation2Altitude.db`. Upgrades schema from V4 to V5, adding `is_locked INTEGER DEFAULT 0` and `source TEXT DEFAULT 'LEGACY_RAW'`.
   - Adheres strictly to `REQ-DAT-008` by utilizing the Android SQLite framework's automatic transaction wrapper during `onUpgrade`.
   - Protects entries with `is_locked == 1` as strictly immutable against automated mutations and refinements.
   - Provides asynchronous batch healing (`healLegacyLocations`) to upgrade unlocked `LEGACY_RAW` entries to `INTERNET_DEM` via multi-point queries when online.
3. **Sensor Device Layer (`com.atrainingtracker.banalservice.devices`)**:
   - `AltitudeFromPressureDevice`: Coordinates barometric calibration during `initPressureSensor()`.
   - Queries spatial database; on cache hit uses stored altitude; on cache miss dispatches non-blocking async DEM elevation retrieval via `ElevationService`.
   - Falls back gracefully to GPS altitude if offline or if network fails, preserving `REQ-CON-011` and `REQ-CON-013`.

---

## 2. Technical Modifications & Class Designs

### 2.1 Elevation Metadata Models (`ElevationSource.kt` & `ElevationResult.kt`)

```kotlin
package com.atrainingtracker.trainingtracker.elevation

enum class ElevationSource {
    AUTO_LEARNED,
    INTERNET_DEM,
    MANUAL_USER,
    GPS_FALLBACK,
    LEGACY_RAW;

    companion object {
        @JvmStatic
        fun fromString(value: String?): ElevationSource {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LEGACY_RAW
        }
    }
}
```

```kotlin
package com.atrainingtracker.trainingtracker.elevation

sealed class ElevationResult {
    data class Success(val elevationMeters: Double, val latitude: Double, val longitude: Double) : ElevationResult()
    data class BatchSuccess(val results: List<Pair<Double, Double?>>) : ElevationResult()
    data class RateLimited(val retryAfterSeconds: Long = 60) : ElevationResult()
    data class ServerError(val statusCode: Int) : ElevationResult()
    data object CircuitBreakerOpen : ElevationResult()
    data class NetworkError(val message: String) : ElevationResult()
}
```

---

### 2.2 Open-Meteo Elevation Client (`ElevationService.kt`)

* **HTTP Client**: Uses `okhttp3.OkHttpClient` with 5000ms connect, read, and write timeouts.
* **Coordinate Quantization**:
  ```kotlin
  fun quantizeCoordinate(coord: Double): Double {
      return (coord * 100000.0).roundToLong() / 100000.0
  }
  ```
* **Single-Point API Request**:
  `GET https://api.open-meteo.com/v1/elevation?latitude={quantizedLat}&longitude={quantizedLng}`
* **Multi-Point Batch Request**:
  `GET https://api.open-meteo.com/v1/elevation?latitude={lat1},{lat2}...&longitude={lng1},{lng2}...`
* **Circuit Breaker**:
  - `circuitBreakerOpenUntil: Long = 0L`
  - If `System.currentTimeMillis() < circuitBreakerOpenUntil`, immediately returns `ElevationResult.CircuitBreakerOpen`.
  - On HTTP 503 or repeated 5xx, sets `circuitBreakerOpenUntil = System.currentTimeMillis() + 300_000L` (5 minutes).

---

### 2.3 SQLite Schema Evolution & Manager Updates (`KnownLocationsDatabaseManager.java`)

1. **Schema Upgrade (V4 -> V5)**:
   - Increment `DB_VERSION` from 4 to 5.
   - In `KnownLocationsDbHelper.onUpgrade()`:
     ```java
     if (oldVersion < 5) {
         addColumn(db, IS_LOCKED, "INTEGER DEFAULT 0");
         addColumn(db, SOURCE, "TEXT DEFAULT '" + ElevationSource.LEGACY_RAW.name() + "'");
     }
     ```
   - Zero manual transaction management (`REQ-DAT-008`).
2. **Model Enrichment**:
   - `MyLocation` fields: `public final boolean isLocked;` and `public final ElevationSource source;`.
3. **Lock Protection Invariant**:
   - In `learnLocation(LatLng pos, Double altitude, ExtremaType type)`:
     ```java
     MyLocation existing = getMyLocation(pos);
     if (existing != null) {
         if (existing.isLocked) {
             if (DEBUG) Log.d(TAG, "Location '" + existing.name + "' is locked. Skipping auto-refinement.");
             return;
         }
         ...
     }
     ```
4. **Batch Healing (`healLegacyLocations`)**:
   - Queries all records where `is_locked == 0 AND source = 'LEGACY_RAW'`.
   - If records exist and device is online: invokes `ElevationService.fetchBatchElevations()`.
   - In a single synchronized block, updates the rows with the retrieved DEM elevations and sets `source = 'INTERNET_DEM'`.

---

### 2.4 Sensor Device Calibration Integration (`AltitudeFromPressureDevice.java`)

1. **Spatial Check**:
   - In `initPressureSensor()`:
     - Check `myLocation = getMyLocation(currentLatLng)`.
     - If `myLocation != null`:
       - Calibrate `setAltitudeCorrection(myLocation.altitude)` immediately.
       - If `myLocation.source == ElevationSource.LEGACY_RAW` and device is online: trigger background DEM refresh.
     - If `myLocation == null`:
       - Launch async elevation lookup on `Dispatchers.IO`.
       - On success: persist new location with `source = INTERNET_DEM`, calibrate `setAltitudeCorrection(demElevation)`, and update sensor.
       - On failure / offline: fall back to GPS altitude (from `SensorType.ALTITUDE` or `SpeedAndLocationDevice`), persist with `source = GPS_FALLBACK`, and calibrate.

---

## 3. Preserved System Invariants & Anti-Regression Rules

1. **Cold-Start Baseline Protection (`REQ-CON-011`)**:
   - `mAltitudeSensor.newValue()` MUST NOT be invoked with uncalibrated standard atmosphere values.
2. **Null-Safe Correction Dispatch (`REQ-CON-013`)**:
   - When DEM elevation arrives asynchronously, `setAltitudeCorrection(demElevation)` MUST utilize defensive null checks to avoid unboxing NPEs.
3. **Atomic Database Upgrades (`REQ-DAT-008`)**:
   - `onUpgrade()` MUST NOT invoke `beginTransaction()` or `setTransactionSuccessful()`; must rely exclusively on Android's automatic transaction wrapper.
4. **Locked Location Immutability**:
   - Any location with `is_locked == 1` MUST NEVER be modified by automated learning or batch healing.
5. **Non-Blocking Sensor Processing**:
   - Sensor Binder thread (`onSensorChanged`) MUST NEVER execute HTTP calls or block waiting for network responses.

---

## 4. Verification Coverage Plan

| Test ID | Test Method & Class | Target Requirement |
| :--- | :--- | :--- |
| `TST-DAT-008.1` | `ElevationServiceTest.testFetchElevation_nominalSuccess()` | `REQ-DAT-014` |
| `TST-DAT-008.2` | `KnownLocationsDatabaseManagerTest.testSpatialGeofenceCache_hitWithinRadius()` | `REQ-DAT-014` |
| `TST-DAT-008.3` | `ElevationServiceTest.testFetchElevation_rateLimited429()` | `REQ-DAT-014` |
| `TST-DAT-008.4` | `ElevationServiceTest.testFetchElevation_serverError503_circuitBreaker()` | `REQ-DAT-014` |
| `TST-DAT-008.5` | `ElevationServiceTest.testFetchElevation_timeoutOfflineFallback()` | `REQ-DAT-014` |
| `TST-DAT-008.6` | `KnownLocationsDatabaseManagerTest.testDatabaseUpgrade_V4toV5()` | `REQ-DAT-008`, `REQ-DAT-014` |
| `TST-DAT-008.7` | `KnownLocationsDatabaseManagerTest.testLockedLocation_immutability()` | `REQ-DAT-014` |
| `TST-DAT-008.8` | `KnownLocationsDatabaseManagerTest.testLegacyBatchHealing_updatesUnlockedToDEM()` | `REQ-DAT-014` |
| `TST-DAT-008.9` | Clean-room full regression `./gradlew testDebugUnitTest` | `REQ-CON-011`, `REQ-CON-013`, `REQ-DAT-014` |

---

## 5. Migration & Rollback Strategy

* **Rollback Safety**:
  - In SQLite, column additions via `ALTER TABLE ADD COLUMN` are backwards-compatible: older code querying specific columns or `SELECT *` ignores extra columns unless referenced.
  - If a transaction fails mid-migration, Android's automatic transaction wrapper rolls back to intact V4 state.
* **Deterministic Unit Tests**:
  - Migration tests will execute against in-memory SQLite instances to verify both fresh V5 table creation and V4 -> V5 upgrade.
