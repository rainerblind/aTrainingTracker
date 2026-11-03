# Stage 1 Analysis: ATT-5199 - Prevent Inaccurate Route/Segment Altitudes Overwriting DEM Cache

**Ticket**: [ATT-5199](https://rainerblind.atlassian.net/browse/ATT-5199)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5199`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
In `ElevationCacheDatabaseManager.kt`, `seedFromRoutes()` (`source = "ROUTE_SEED"`) and `seedFromSegments()` (`source = "SEGMENT_SEED"`) insert trackpoint altitudes into `ElevationCache.db` using unconditional `CONFLICT_REPLACE` on every `AltitudeFromPositionDevice` startup.
1. Raw Strava segment streams (often carrying 20–50m uncalibrated barometric drift or GPS noise) overwrite planned route elevations and authoritative Open-Meteo `DEM_CACHE` entries.
2. In `AltitudeFromPositionDevice.onNewLocation()`, any non-null cache hit treats the seeded altitude as permanent and suppresses outbound `ElevationService` DEM queries, permanently masking accurate terrain data.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  * `seedFromRoutes()` and `seedFromSegments()` delegate to `putBatchElevations()`, which executes `SQLiteDatabase.CONFLICT_REPLACE` across the unique `(lat_e5, lon_e5)` index without checking existing `source` authority.
  * `AltitudeFromPositionDevice.onNewLocation()` invokes `mElevationCacheDb.getElevation(lat, lon, 2)`: any non-null return value skips `fetchDemElevationAsync()`, preventing online refinement of unverified seeds.
* **Independent Peer Review & Verification**:
  * Confirmed in `ElevationCacheDatabaseManager.kt#L278`: unconditional `CONFLICT_REPLACE` replaces `DEM_CACHE` rows.
  * Confirmed in `AltitudeFromPositionDevice.java#L181-L188`: cache hits discard source metadata and suppress DEM lookups.
  * MinSdk is 26 (Android 8.0), precluding SQLite 3.24+ `ON CONFLICT DO UPDATE` upsert syntax. Conflict priority must be handled via `CONFLICT_IGNORE` / conditional priority checks.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Enforce strict source priority (`DEM_CACHE` > `ROUTE_SEED` > `SEGMENT_SEED`) in `ElevationCacheDatabaseManager.kt`.
  * Prevent `seedFromRoutes()` and `seedFromSegments()` from overwriting authoritative `DEM_CACHE` (using `CONFLICT_IGNORE` and priority updates).
  * Update `AltitudeFromPositionDevice.java` to expose `getElevationItem()`, emit seeded altitudes immediately for zero-latency offline display, and asynchronously upgrade non-`DEM_CACHE` coordinates via `ElevationService` when online.
  * Guard async DEM requests with an atomic in-flight query flag to prevent executor queue flooding when offline or traversing dense points.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No SQLite schema alterations or `DB_VERSION` bump (`elevation_cache` already contains `source TEXT`).
  * No modifications to `KnownLocations.db` (preserving strict `REQ-SEN-025.4` decoupling).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SEN-025.2` (Spatial Cache) & `REQ-SEN-025.3` (Seeding & DEM Caching).
* **Historical Origin & Commit Trace**: `ATT-4859` (commit `a11e13d7`, Sprint 2026-44.2) established `AltitudeFromPositionDevice`.
* **Root Reason for Existing Formulation**: `ATT-4859` introduced route/segment seeding to provide offline elevation without network roundtrips, but naively applied `CONFLICT_REPLACE` without distinguishing authoritative DEM from noisy user uploads.
* **Preservation of Core Invariants**: Offline tracking continuity is preserved; seeded points continue to provide instant fallback while online fixes refine to authoritative DEM. Zero interaction with `KnownLocations.db` (`REQ-SEN-025.4`).

---

## 5. Architectural Strategy & High-Level Solution
1. **Priority Hierarchy (`ElevationCacheDatabaseManager.kt`)**: Define constants `SOURCE_DEM_CACHE` (30), `SOURCE_ROUTE_SEED` (20), `SOURCE_SEGMENT_SEED` (10).
2. **Conflict Invariant**: `seedFromRoutes()` and `seedFromSegments()` use `CONFLICT_IGNORE` (never overwriting `DEM_CACHE`). `seedFromRoutes()` additionally updates matching `SEGMENT_SEED` rows. `putElevation()` with `DEM_CACHE` replaces lower-priority records.
3. **Cache Inspection & Online Refinement (`AltitudeFromPositionDevice.java`)**: Add `getElevationItem(lat, lon, tolerance)`. In `onNewLocation()`, emit cached value immediately; if `source != SOURCE_DEM_CACHE`, dispatch background `fetchDemElevationAsync()`.
4. **Side-Effect & Cross-Caller Audit (Rule 68)**:
   * Single-thread executor protected via `AtomicBoolean mIsDemQueryInFlight` to avoid queue buildup during offline or low-connectivity rides.
   * `ElevationCacheDatabaseManager.getElevation()` signature retained for full backward compatibility.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `DEM_CACHE` entries are permanent against subsequent seeding passes.
  2. Offline elevation availability for seeded routes is never interrupted.
  3. `KnownLocations.db` isolation (`REQ-SEN-025.4`) remains strictly intact.
* **Risk Rating**: **LOW**. Logic is confined to `ElevationCacheDatabaseManager.kt` and `AltitudeFromPositionDevice.java` without schema migrations or sensor priority alterations.
