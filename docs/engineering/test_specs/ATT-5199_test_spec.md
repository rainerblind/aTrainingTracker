# Stage 2: Requirement & Test Specification - ATT-5199: Elevation Cache Priority & Non-Destructive Seeding

**Ticket**: [ATT-5199](https://rainerblind.atlassian.net/browse/ATT-5199)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-SEN-025` (*Pairable Position-Based Elevation Sensor & Dedicated Spatial DEM Cache*)  
**Test Spec ID**: `TST-SEN-025`  
**Branch**: `feature/ATT-5199`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-SEN-025)

### 1.1 Problem Statement & Rationale
In `ElevationCacheDatabaseManager.kt`, route and segment seeding uses unconditional `CONFLICT_REPLACE`, overwriting authoritative `DEM_CACHE` and planned route elevations with noisy segment trackpoints. Furthermore, `AltitudeFromPositionDevice` treats any non-null cache hit as permanent, suppressing online Open-Meteo DEM queries.

### 1.2 Functional & Architectural Requirements
1. **Source Priority Hierarchy (`REQ-SEN-025.2`)**: The system SHALL prioritize sources: `DEM_CACHE` (30) > `ROUTE_SEED` (20) > `SEGMENT_SEED` (10). Lower-priority insertions SHALL NEVER overwrite higher-priority records.
2. **Non-Destructive Seeding (`REQ-SEN-025.2`)**: `seedFromRoutes()` and `seedFromSegments()` SHALL insert with `CONFLICT_IGNORE`. `seedFromRoutes()` SHALL upgrade existing `SEGMENT_SEED` entries to `ROUTE_SEED`. Authoritative `DEM_CACHE` insertions via `putElevation()` SHALL overwrite lower-priority seeded records.
3. **Cache Inspection & Online Refinement (`REQ-SEN-025.3`)**: `AltitudeFromPositionDevice` SHALL retrieve the cached entity via `getElevationItem()`. If `source != DEM_CACHE`, it SHALL publish the seeded altitude immediately for offline zero-latency display AND dispatch an asynchronous `ElevationService` query to upgrade the cache to `DEM_CACHE`.
4. **In-Flight Query Guard (`REQ-SEN-025.3`)**: Asynchronous DEM queries SHALL be guarded with `AtomicBoolean mIsDemQueryInFlight` to prevent thread pool exhaustion during offline rides or dense coordinate streams.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Source Priority)**:
  * *Given* a coordinate cached as `DEM_CACHE`,
  * *When* `seedFromSegments()` or `seedFromRoutes()` indexes the same coordinate,
  * *Then* the existing `DEM_CACHE` altitude and source remain untouched.
* **Criterion 2 (Online DEM Refinement)**:
  * *Given* a coordinate cached with `source = ROUTE_SEED` or `SEGMENT_SEED`,
  * *When* `onNewLocation()` receives this coordinate while online,
  * *Then* the seeded altitude is published immediately and an async DEM query is dispatched, updating the cache to `DEM_CACHE`.

### 1.4 System Invariants
* Zero schema migrations or `DB_VERSION` changes (`source TEXT` already exists in `elevation_cache`).
* Zero mutation or queries against `KnownLocations.db` (`REQ-SEN-025.4`).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-SEN-025.2` (Spatial Cache) & `REQ-SEN-025.3` (Seeding & DEM Caching). Targets `ElevationCacheDatabaseManager.kt` and `AltitudeFromPositionDevice.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 (`ATT-4859`, commit `a11e13d7`). Builds upon `ATT-1366` (DEM resolution) and `ATT-166` (sensor fusion epic).
3. *Root Reason for Existing Formulation*: Route/segment seeding was originally implemented with unconditional `CONFLICT_REPLACE` to provide immediate offline elevation without network roundtrips. However, this caused noisy Strava segment streams to clobber authoritative DEM entries and caused cache hits on seeded points to permanently suppress online DEM queries.
4. *Preservation of Core Invariants*: Offline elevation availability for seeded routes is preserved with instant display; online fixes upgrade to authoritative DEM. Strict isolation from `KnownLocations.db` (`REQ-SEN-025.4`) is maintained.

---

## 2. Test Specification (TST-SEN-025)

### Test Case 1: `ElevationCacheDatabaseManagerTest` Priority Hierarchy (`TST-SEN-025.2`)
* **Scope**: Unit Test (`app/src/test/.../database/ElevationCacheDatabaseManagerTest.kt`)
* **Action**: Verify `DEM_CACHE` is preserved when seeding routes/segments; verify `ROUTE_SEED` overwrites `SEGMENT_SEED`; verify `DEM_CACHE` overwrites seeded entries.
* **Expected Result**: Priority order `DEM_CACHE` > `ROUTE_SEED` > `SEGMENT_SEED` strictly maintained.

### Test Case 2: `AltitudeFromPositionDeviceTest` Online Upgrade & In-Flight Guard (`TST-SEN-025.3`)
* **Scope**: Unit Test (`app/src/test/.../devices/AltitudeFromPositionDeviceTest.kt`)
* **Action**: Feed location with seeded cache hit; verify immediate emission and DEM query dispatch; verify concurrent fixes do not enqueue duplicate queries while in-flight.
* **Expected Result**: Seeded altitude emitted immediately, DEM query triggers cache upgrade to `DEM_CACHE`, and duplicate in-flight requests are skipped.

### Test Case 3: Clean-Room Regression Suite (`REQ-PRO-001`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.AltitudeFromPositionDeviceTest" --tests "com.atrainingtracker.trainingtracker.database.ElevationCacheDatabaseManagerTest"`
* **Expected Result**: 100% PASS across targeted and regression suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-025.1` | Unit | `ElevationCacheDatabaseManager.getElevation` | `REQ-SEN-025.2` | Implemented |
| `TST-SEN-025.2` | Unit | `ElevationCacheDatabaseManager.putElevation/seed*` | `REQ-SEN-025.2` | Specified |
| `TST-SEN-025.3` | Unit | `AltitudeFromPositionDevice.onNewLocation` | `REQ-SEN-025.3` | Specified |
| `TST-SEN-025.4` | Unit | `AltitudeFromPositionDevice` isolation | `REQ-SEN-025.4` | Implemented |
| `TST-SEN-025.5` | Parity | `TranslationParityTest` | `REQ-SEN-025.5` | Implemented |
