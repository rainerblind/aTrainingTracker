# Stage 2: Requirement & Test Specification - ATT-4859: Pairable AltitudeFromPositionDevice & DEM Elevation Cache

**Ticket**: [ATT-4859](https://rainerblind.atlassian.net/browse/ATT-4859)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-SEN-025` (*Pairable Position-Based Elevation Sensor & Dedicated Spatial DEM Cache*)  
**Test Spec ID**: `TST-SEN-025`  
**Branch**: `feature/ATT-4859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Requirement Specification (REQ-SEN-025)

### 1.1 Problem Statement & Rationale
`SensorType.ALTITUDE` currently depends on internal barometric pressure or noisy GNSS altitude ($\pm 20\text{--}50\text{ m}$ vertical multipath error). `AltitudeFromPositionDevice` (`DeviceType.ALTITUDE_FROM_POSITION`, `Protocol.SMARTPHONE`) introduces a pairable, continuous topographic DEM altitude provider backed by a standalone spatial elevation database (`ElevationCache.db`), seeded from routes and asynchronous Open-Meteo DEM queries, completely decoupled from *Lieblingsorte* (`KnownLocations.db`).

### 1.2 Functional & Architectural Requirements
1. `REQ-SEN-025.1`: The system SHALL register `DeviceType.ALTITUDE_FROM_POSITION` under `Protocol.SMARTPHONE` with main sensor type `SensorType.ALTITUDE` and dynamic pairing lifecycle in `DeviceManager` and `DevicesDatabaseManager`.
2. `REQ-SEN-025.2`: The system SHALL persist elevation points in dedicated `ElevationCache.db` (`DB_VERSION = 1`, table `elevation_cache(lat_e5, lon_e5, altitude, source, updated_at)`), registered in `DatabaseSchemaRegistry` per Rule 62, supporting $O(\log N)$ spatial query with nearest-neighbor tolerance ($\Delta\text{E5} \le 2$).
3. `REQ-SEN-025.3`: The system SHALL asynchronously seed `ElevationCache.db` from stored routes in `Routes.db`, query `ElevationService` (Open-Meteo DEM) on cache misses, and continuously publish altitude readings to `mAltitudeSensor`.
4. `REQ-SEN-025.4`: General position tracking and DEM resolution SHALL NEVER insert, update, or alter records in `KnownLocations.db`.
5. `REQ-SEN-025.5`: All device strings SHALL maintain 100% translation parity across all 9 supported locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* the Smartphone devices list in Settings, *When* the user pairs `AltitudeFromPositionDevice`, *Then* it emits continuous topographic elevation derived from `ElevationCache.db` or `ElevationService`.
* *Given* stored routes in `Routes.db`, *When* cache seeding runs, *Then* route elevation points are indexed into `ElevationCache.db` for offline queries.
* *Given* active GPS tracking, *When* `ElevationCache.db` is updated, *Then* zero records are created in `KnownLocations.db`.

### 1.4 System Invariants
* Zero mutation of `KnownLocations.db` during position tracking; non-blocking background I/O on `Dispatchers.IO`; Rule 62 schema registry conformance; 100% clean-room test pass rate.

---

## 2. Test Specification (TST-SEN-025)

### Test Case 1: Spatial Cache CRUD & Nearest-Neighbor Interpolation (`TST-SEN-025.1`)
* **Scope**: Unit Test (`ElevationCacheDatabaseManagerTest.kt`)
* **Preconditions**: Mock SQLite database injected into `ElevationCacheDatabaseManager`.
* **Action**: Insert quantized `(latE5, lonE5, alt, source)`. Query exact coordinates, query within tolerance ($\Delta\text{E5} \le 2$), query beyond tolerance.
* **Expected Result**: Exact match returns elevation; within tolerance returns nearest neighbor; beyond tolerance returns null.

### Test Case 2: Background Route Elevation Seeding (`TST-SEN-025.2`)
* **Scope**: Unit Test (`ElevationCacheDatabaseManagerTest.kt`)
* **Preconditions**: Mock `RoutesDatabaseManager` containing trackpoints with elevation data.
* **Action**: Invoke `seedFromRoutes(routesManager)`.
* **Expected Result**: All valid trackpoint altitudes are indexed into `ElevationCache.db`; duplicate trackpoints deduplicated safely.

### Test Case 3: Device Lifecycle, Pairing Toggle & Unfiltered Altitude Emission (`TST-SEN-025.3`)
* **Scope**: Unit Test (`AltitudeFromPositionDeviceTest.kt`, `DeviceManagerTest.kt`)
* **Preconditions**: Paired `AltitudeFromPositionDevice` with mock `ElevationCacheDatabaseManager`.
* **Action**: Simulate location updates with cache hits and misses; toggle pairing off and on.
* **Expected Result**: Sensor publishes resolved altitude on location updates; unpairing destroys device and releases listeners cleanly.

### Test Case 4: Zero Contamination of Known Locations (*Lieblingsorte*) (`TST-SEN-025.4`)
* **Scope**: Architectural Isolation Unit Test (`AltitudeFromPositionDeviceTest.kt`)
* **Action**: Execute continuous position fixes and cache updates.
* **Expected Result**: Zero invocations and zero records written to `KnownLocationsDatabaseManager`.

### Test Case 5: 9-Language Localization Parity (`TST-SEN-025.5`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all 4 new strings.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-025.1` | Unit | `ElevationCacheDatabaseManager.getElevation` | `REQ-SEN-025.2` | Specified |
| `TST-SEN-025.2` | Unit | `ElevationCacheDatabaseManager.seedFromRoutes` | `REQ-SEN-025.3` | Specified |
| `TST-SEN-025.3` | Unit | `AltitudeFromPositionDevice.onLocationChanged` | `REQ-SEN-025.1`, `REQ-SEN-025.3` | Specified |
| `TST-SEN-025.4` | Isolation | `AltitudeFromPositionDevice` vs `KnownLocations.db` | `REQ-SEN-025.4` | Specified |
| `TST-SEN-025.5` | Localization | `TranslationParityTest` | `REQ-SEN-025.5`, `REQ-UI-106` | Specified |
