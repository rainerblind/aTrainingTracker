# Stage 3: Implementation Plan - ATT-4859: Pairable AltitudeFromPositionDevice & DEM Elevation Cache

**Ticket**: [ATT-4859](https://rainerblind.atlassian.net/browse/ATT-4859)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-SEN-025`  
**Test Mapping**: `TST-SEN-025`  
**Branch**: `feature/ATT-4859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
GNSS altitude has $\pm 20\text{--}50\text{ m}$ vertical noise, and devices without barometers lack reliable altitude. We introduce pairable `AltitudeFromPositionDevice` (`DeviceType.ALTITUDE_FROM_POSITION`, `Protocol.SMARTPHONE`) providing continuous topographic DEM altitude via a standalone spatial database (`ElevationCache.db`), seeded from routes and asynchronous Open-Meteo DEM queries, strictly decoupled from *Lieblingsorte* (`KnownLocations.db`).

---

## 2. Traceability & Requirements Mapping
* **REQ-SEN-025.1**: Pairable `DeviceType.ALTITUDE_FROM_POSITION` (`Protocol.SMARTPHONE`) registered in `Devices.db`, `DeviceManager`, and `SensorSourceDialog`.
* **REQ-SEN-025.2**: Dedicated `ElevationCache.db` (`DB_VERSION = 1`, Rule 62/64/20 compliant) with quantized `(lat_e5, lon_e5)` index and nearest-neighbor lookup.
* **REQ-SEN-025.3**: Non-blocking route/segment seeding and background DEM fetching via `ElevationService` on cache misses.
* **REQ-SEN-025.4**: Zero writes or queries to `KnownLocations.db`.
* **REQ-SEN-025.5**: 100% 9-language translation parity in `strings.xml`.

---

## 3. System Invariants & Upgrade Path (Rule 66)
1. **Existing User Invariant (Rule 66 Sec 5.6)**: New device defaults to `paired = false` in `Devices.db` / `ensureSmartphoneDeviceExists`; existing users' altitude sources and tracking behavior remain unchanged until paired.
2. **Database Isolation**: `ElevationCache.db` operations never mutate `KnownLocations.db`.
3. **Database Governance (Rules 20, 62, 64)**: `DatabaseSchemaRegistry` references `ElevationCacheDbHelper.DB_VERSION` directly; cursor mappers are symmetric; `onCreate`/`onUpgrade` parity is enforced.
4. **Thread Isolation**: All spatial queries, seeding, and DEM network calls execute on `Dispatchers.IO` / background executor.

---

## 4. Proposed Architectural Changes
* **Database Layer (`ElevationCacheDatabaseManager.kt`)**: Standalone SQLite helper (`ElevationCache.db`, table `elevation_cache(lat_e5 INT, lon_e5 INT, altitude REAL, source TEXT, updated_at INT)`, compound unique index on `(lat_e5, lon_e5)`). Provides `getElevation(lat, lon, toleranceE5 = 2)`, `putElevation(...)`, `seedFromRoutes(routesDb)`, `seedFromSegments(segmentsDb)`.
* **Device Driver (`AltitudeFromPositionDevice.java`)**: Extends `MyDevice(DeviceType.ALTITUDE_FROM_POSITION)`. Receives `BANALService.LOCATION_AVAILABLE_INTENT`, queries `ElevationCache.db`, triggers async `ElevationService.fetchElevation` on misses, and emits to `mAltitudeSensor`.
* **Device Lifecycle & Settings (`DeviceType.java`, `DeviceManager.java`, `DevicesDatabaseManager.java`, `SensorSourceDialog.kt`, `UIHelper.java`, `DeviceData.kt`)**: Add `ALTITUDE_FROM_POSITION` to `DeviceType`, `UIHelper`, `DeviceData` (`ic_altitude`), `MySensorManager.ALTITUDE_PRIORITY` (Pressure $\rightarrow$ Position $\rightarrow$ GPS), and `DeviceManager` pairing lifecycle.
* **UI Consistency (Rule 23)**: Uses existing `SensorSourceDialog` bottom sheet and `DeviceData` styling with `ic_altitude` icon; zero new one-off UI styles.

---

## 5. Step-by-Step Implementation Sequence
* **Step 1: Database Construction (`ElevationCacheDatabaseManager.kt`)**: Create `ElevationCacheDbHelper` (`DB_VERSION = 1`) and manager with symmetric cursor mappers, nearest-neighbor lookup, and batch seeding from `RoutesDatabaseManager` and `SegmentsDatabaseManager`. Register in `DatabaseSchemaRegistry.kt` (Rule 62).
* **Step 2: Device Model & Pairing Registration**: Add `DeviceType.ALTITUDE_FROM_POSITION(SensorType.ALTITUDE, BSportType.UNKNOWN, 1)`, wire in `UIHelper.java`, `DeviceData.kt`, `MySensorManager.ALTITUDE_PRIORITY`, `DevicesDatabaseManager.java` (`onCreate` + `ensureSmartphoneDeviceExists(..., pairedDefault=false)`), and `DeviceManager.java`.
* **Step 3: Driver Implementation (`AltitudeFromPositionDevice.java`)**: Implement sensor driver listening to location updates, resolving elevations via cache or async `ElevationService` without blocking sensor threads, publishing to `mAltitudeSensor`.
* **Step 4: Unit & Contract Tests (`TST-SEN-025.1`..`5`)**:
  * Unit test `ElevationCacheDatabaseManagerTest.kt` (CRUD, nearest neighbor, route/segment seeding).
  * Unit test `AltitudeFromPositionDeviceTest.kt` (device lifecycle, location fixes, DEM fallback, zero `KnownLocations.db` mutation).
  * Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.elevation.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted package unit tests, `TranslationParityTest`, and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Isolated branch `feature/ATT-4859` ensures clean rollback without side effects on `sprint/2026-44.2`.
