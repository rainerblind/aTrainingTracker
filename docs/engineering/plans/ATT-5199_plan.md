# Stage 3: Implementation Plan - ATT-5199: Elevation Cache Priority & Non-Destructive Seeding

**Ticket**: [ATT-5199](https://rainerblind.atlassian.net/browse/ATT-5199)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-SEN-025` (`REQ-SEN-025.2`, `REQ-SEN-025.3`)  
**Test Mapping**: `TST-SEN-025` (`TST-SEN-025.2`, `TST-SEN-025.3`)  
**Branch**: `feature/ATT-5199`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
In `ElevationCacheDatabaseManager.kt`, route and segment seeding currently uses unconditional `CONFLICT_REPLACE`, overwriting authoritative Open-Meteo `DEM_CACHE` and planned route elevations with noisy Strava segment points. Furthermore, `AltitudeFromPositionDevice` treats any cached entry as permanent, suppressing live Open-Meteo DEM queries for seeded coordinates.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SEN-025.2` (Dedicated Spatial Cache & Source Priority), `REQ-SEN-025.3` (Non-Destructive Seeding, Online Refinement & In-Flight Guard).
* **Test Mapping**: `TST-SEN-025.2` (Source Priority & Seeding Unit Tests), `TST-SEN-025.3` (Online DEM Refinement & In-Flight Guard Unit Tests).

---

## 3. System Invariants & Preserved Behavior
1. **Schema & Registry Invariance**: Zero database migrations or `DB_VERSION` bump (`elevation_cache` already contains `source TEXT`).
2. **Offline Continuity**: Seeded route elevations continue to provide immediate offline elevation display.
3. **Isolation Decoupling**: Zero queries or writes to `KnownLocations.db` (`REQ-SEN-025.4`).
4. **Subtask & Gate Invariants**: Subtasks transition directly via `freigabe`; parent ticket ends at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ElevationCacheDatabaseManager.kt`
* Define constants `SOURCE_DEM_CACHE = "DEM_CACHE"` (30), `SOURCE_ROUTE_SEED = "ROUTE_SEED"` (20), `SOURCE_SEGMENT_SEED = "SEGMENT_SEED"` (10).
* Add priority resolver `getSourcePriority(source: String): Int`.
* Update `putElevation()`: Only overwrite existing coordinate if incoming source priority $\ge$ existing source priority.
* Add `getElevationItem(lat, lon, tolerance)` returning the full `ElevationCacheItem` (preserving `getElevation()` as wrapper).
* Update `seedFromRoutes()`: Upgrade existing `SEGMENT_SEED` rows to `ROUTE_SEED`; insert new points with `CONFLICT_IGNORE` (never overwriting `DEM_CACHE`).
* Update `seedFromSegments()`: Insert points with `CONFLICT_IGNORE` (never overwriting `DEM_CACHE` or `ROUTE_SEED`).

### Component 2: `AltitudeFromPositionDevice.java`
* Add `AtomicBoolean mIsDemQueryInFlight = new AtomicBoolean(false)`.
* In `onNewLocation()`, evaluate `getElevationItem()`: emit altitude immediately for zero UI lag / offline fallback; if `source != DEM_CACHE`, dispatch async DEM fetch.
* In `fetchDemElevationAsync()`, guard with `mIsDemQueryInFlight` to prevent thread pool exhaustion offline or during high-frequency location fixes.

### UI Consistency (Rule 23)
* No UI changes (sensor driver and database logic only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Priority & Non-Destructive Seeding
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/database/ElevationCacheDatabaseManager.kt`
* Changes: Define source constants and priority values; add `getElevationItem()`; enforce source priority in `putElevation()`; implement non-destructive seeding in `seedFromRoutes()` and `seedFromSegments()`.

### Step 2: Driver Online DEM Refinement & In-Flight Guard
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPositionDevice.java`
* Changes: Add `mIsDemQueryInFlight`; call `getElevationItem()`; emit seeded altitude immediately; trigger async DEM upgrade for non-`DEM_CACHE` entries; guard async queries.

### Step 3: Targeted Unit Tests
* Files: `ElevationCacheDatabaseManagerTest.kt`, `AltitudeFromPositionDeviceTest.kt`
* Changes: Add tests for source priority (`DEM_CACHE` > `ROUTE_SEED` > `SEGMENT_SEED`), non-destructive seeding, immediate emission + async DEM upgrade, and in-flight deduplication.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.AltitudeFromPositionDeviceTest" --tests "com.atrainingtracker.trainingtracker.database.ElevationCacheDatabaseManagerTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted package unit tests, `verify_requirement_governance.py`, and full test suite before Stage 5 integration.
* **Rollback**: Clean git branch revert on `feature/ATT-5199`.
