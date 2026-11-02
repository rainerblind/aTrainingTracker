# Stage 1 Analysis: ATT-4859 - Pairable AltitudeFromPositionDevice with Dedicated DEM and Route Elevation Cache Database

**Ticket**: [ATT-4859](https://rainerblind.atlassian.net/browse/ATT-4859)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: ATT-166 (Filtering to improve sensor values)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Currently, `SensorType.ALTITUDE` is supplied either by internal barometric pressure (`AltitudeFromPressureDevice`) or raw GNSS altitude, which exhibits vertical multipath errors of $\pm 20\text{--}50\text{ m}$. Phones lacking a barometer must rely on noisy GPS altitude. Introducing a pairable `AltitudeFromPositionDevice` (`DeviceType.ALTITUDE_FROM_POSITION`, `Protocol.SMARTPHONE`) backed by a dedicated spatial cache (`ElevationCache.db`) provides continuous topographic/DEM altitude based on GPS coordinates, seeded from route elevations (`Routes.db`) and Open-Meteo DEM queries (`ElevationService`), strictly separated from *Lieblingsorte* (`KnownLocations.db`).

---

## 2. Architectural Gap Analysis & Independent Hypothesis Verification
* **Forensic Gap Analysis**:
  * `DeviceType.java`: Only `ALTITUDE_FROM_PRESSURE` and GPS/Network devices supply `SensorType.ALTITUDE`. No pairable position-based DEM device exists.
  * `DeviceManager.java` & `DevicesDatabaseManager.java`: Smartphone device instantiation and pairing management support only `ALTITUDE_FROM_PRESSURE`.
  * `ElevationCache.db`: No standalone spatial elevation database exists. `ElevationService.kt` fetches DEM tiles from Open-Meteo, but only stores results into `KnownLocations.db` (contaminating athlete favorite spots) or discards them.
  * `MySensorManager.java`: `ALTITUDE_PRIORITY` iterates `ALTITUDE_FROM_PRESSURE` $\rightarrow$ `GPS` $\rightarrow$ `GoogleFused` $\rightarrow$ `Network`, lacking a prioritized position-based DEM fallback.
* **Independent Hypothesis Verification (ATT-3534)**:
  * Verified: `ElevationService.kt` provides `quantizeCoordinate(Double)` at 5 decimal places ($\approx 1.1\text{ m}$ precision) and non-blocking `fetchElevation(lat, lon)`.
  * Verified: `RoutesDatabaseManager.kt` stores thousands of trackpoints with `COLUMN_ALTITUDE` in `TABLE_POINTS`. A dedicated background indexer can seed `ElevationCache.db` without network access.
  * Verified: Decoupling `ElevationCache.db` from `KnownLocations.db` preserves the integrity of athlete *Lieblingsorte*.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Objectives**:
  1. `DeviceType.ALTITUDE_FROM_POSITION`: Register pairable `Protocol.SMARTPHONE` device with 9-language UI labels and `ic_altitude` icon.
  2. `ElevationCacheDatabaseManager` (`ElevationCache.db`): Create independent database with `elevation_cache(lat_e5, lon_e5, altitude, source, updated_at)` and unique index `idx_elev_cache_coords`. Register in `DatabaseSchemaRegistry` per Rule 62.
  3. Spatial Indexing & Caching: $O(\log N)$ lookup with nearest-neighbor tolerance; background seeding from `Routes.db`; asynchronous `ElevationService` fetches on cache misses.
  4. Device Implementation (`AltitudeFromPositionDevice.java`): Subscribe to `LATITUDE`/`LONGITUDE` fixes, resolve altitude from cache/DEM, and publish `SensorType.ALTITUDE`. Wire pairing lifecycle in `DeviceManager.java`.
* **Out-of-Scope Non-Goals**:
  1. Modifying `KnownLocations.db` schema or *Lieblingsorte* geofence logic.
  2. Complex Kalman sensor fusion (handled in separate downstream filter tickets).
  3. Pre-fetching offline elevation tiles for whole countries (on-demand route and GPS caching only).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Reference**: Net-new `REQ-SEN-025` (Pairable Position-Based Elevation Sensor & Dedicated Spatial DEM Cache).
* **Chesterton's Fence**:
  * `REQ-DAT-014` (Open-Meteo DEM Integration): Preserved and reused via `ElevationService.getInstance()`.
  * `REQ-UI-199` (*Lieblingsorte* Altimeter Dispatch): Strictly insulated from DEM cache queries.
  * Invariant: `AltitudeFromPressureDevice` priority and barometric drift correction remain untouched.

---

## 5. Architectural Strategy & High-Level Solution
* **Database Layer (`ElevationCacheDatabaseManager.kt`)**:
  * Singleton SQLite helper managing `ElevationCache.db` (`DB_VERSION = 1`).
  * Table `elevation_cache`: `(_id INTEGER PRIMARY KEY, lat_e5 INT, lon_e5 INT, altitude REAL, source TEXT, updated_at INT)`.
  * Methods: `getElevation(lat, lon, toleranceE5 = 2)`, `putElevation(...)`, `putBatchElevations(...)`, `seedFromRoutes(routesDb)`.
* **Device Driver (`AltitudeFromPositionDevice.java`)**:
  * Extends `MyDevice(DeviceType.ALTITUDE_FROM_POSITION, Protocol.SMARTPHONE)`.
  * Listens for location updates via `mGPSProviderEnabledReceiver` / sensor manager, evaluates `ElevationCacheDatabaseManager`, dispatches async `ElevationService.fetchElevation` on miss, and publishes `mAltitudeSensor.newValue(elevation)`.
* **Service & UI Integration (`DeviceManager.java`, `SensorSourceDialog.kt`)**:
  * Add `ensureSmartphoneDeviceExists(ALTITUDE_FROM_POSITION, ...)` and instantiate device when paired.
  * Update `SensorSourceDialog.kt` and `MySensorManager.ALTITUDE_PRIORITY` (Pressure $\rightarrow$ Position $\rightarrow$ GPS).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Separation of Concerns: Zero writes to `KnownLocations.db` from general position tracking.
  2. Rule 62 Registry Integrity: `DatabaseSchemaRegistry` references `ElevationCacheDatabaseManager.DB_VERSION` directly.
  3. Non-Blocking I/O: All DEM network lookups and batch route indexing execute on background threads (`Dispatchers.IO`).
* **Risk Rating**: **MEDIUM** — Introduces a new database and smartphone sensor driver. Mitigated by isolation from core tracking lifecycles and unit/contract test coverage.
