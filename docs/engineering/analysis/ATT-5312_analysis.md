# Stage 1 Analysis: ATT-5312 - Prevent 0.0m ROUTE_SEED Cache Poisoning and Detail Telemetry NULL Altitude Fallback

**Ticket**: [ATT-5312](https://rainerblind.atlassian.net/browse/ATT-5312)  
**Sub-task**: [[ATT-5315]](https://rainerblind.atlassian.net/browse/ATT-5315) (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Branch**: `feature/ATT-5312`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
During workout aftermath inspection ("Zum Bäcker #46", 433–446 m MSL), the elevation profile Y-axis spanned 0 to 486 m with deep black dropouts (slope $\ge 25\%$, Zone 7) plunging to 0.0 m at start (0.0 km) and near the end (1.78 km):
1. **Cache Poisoning**: `ElevationCacheDatabaseManager.seedFromRoutes()` imported 2D routes (Route 42/45 with all-zero elevations) into `ElevationCache.db` as 609 `ROUTE_SEED` cells with `altitude = 0.0`.
2. **Neighborhood Trap**: `AltitudeFromPositionDevice`'s $\pm 2.2$ m tolerance match (`toleranceE5 = 2`) matched a poisoned `0.0 m` seed neighbor cell. Subsequent asynchronous DEM fetch updated only the exact GPS point, leaving the poisoned neighbor intact to poison the return trip.
3. **Telemetry NULL Fallback Discrepancy**: `WorkoutRepository.kt:855` (`loadWorkoutDetailTelemetry`) defaulted missing altitude to `0.0` instead of `null` (diverging from lines 438 and 516).
4. **Outlier Scaling & WGS84 Fallback**: `calculateElevationBounds` collapsed baseline to 0.0 m; 1-second initial fallback to Google Fused Altitude jumped to 485.6 m WGS84 ellipsoid height.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  - `ElevationCacheDatabaseManager.kt#L341-L368`: `seedFromRoutes()` and `seedFromSegments()` query `elevation IS NOT NULL` without validating non-zero or real elevation variation, inserting 2D dummy `0.0` points as `ROUTE_SEED`/`SEGMENT_SEED`. No self-healing purge exists for corrupted `0.0 m` seed entries.
  - `AltitudeFromPositionDevice.java#L187-L236`: When `getElevationItem(lat, lon, 2)` hits a non-authoritative neighbor seed (`cachedItem.latE5 != latE5 || cachedItem.lonE5 != lonE5`), `fetchDemElevationAsync` writes DEM only to `(latE5, lonE5)`, leaving the matched neighbor cell un-upgraded in `ElevationCache.db`.
  - `WorkoutRepository.kt#L855`: `alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else 0.0` returns `0.0` instead of `null`, preventing `ElevationSmoothingMath` from interpolating unrecorded altitude rows.
  - `ElevationSmoothingMath.kt` & `ElevationProfile.kt#L138-L151`: `calculateElevationBounds` only filters high spikes ($> 3000$ m when median $< 1500$ m); isolated $0.0$ m dropouts on workouts where median altitude $> 50$ m pull down `streamMin` to 0.0 m.
* **Independent Peer Review & Verification (ATT-3534, Rules 69 & 70)**:
  - *Hypothesis Audit*: Confirmed all 4 failure vectors against live code. 2D routes in `Routes.db` have `elevation_gain = 0.0` and all points with `elevation = 0.0`. `loadWorkoutDetailTelemetry` line 855 is confirmed divergent from lines 438 and 516.
  - *Edge-Case Verification*: Real sea-level workouts (median $\le 50$ m) must legitimately permit $0.0$ m; filtering must be conditioned on `medianAlt > 50.0`. Upgrading matched neighbor cells must safely handle coordinates within E5 bounding box without index collisions.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Prevent seeding `0.0 m` route/segment points without true elevation and add startup self-healing purge of `0.0 m` `ROUTE_SEED`/`SEGMENT_SEED` entries in `ElevationCacheDatabaseManager`.
  - Upgrade/cleanse matched non-authoritative neighbor seed cells in `AltitudeFromPositionDevice` when `fetchDemElevationAsync` resolves DEM.
  - Fix `WorkoutRepository.kt:855` to return `null` instead of `0.0` for missing altitude.
  - Add isolated `0.0 m` dropout outlier protection in `ElevationSmoothingMath` / `calculateElevationBounds` / `recalculateExtrema` when workout median altitude $> 50$ m.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No changes to Open-Meteo API network protocol or schema in `ElevationService.kt`.
  - No alteration of `KnownLocations.db` (`KnownLocationsDatabaseManager`) per `REQ-SEN-025.4`.
  - No changes to Strava segment sync protocol or GPX parser core format.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement IDs & Targets**: `REQ-SEN-025` (clauses 2 & 3, `ElevationCacheDatabaseManager.kt`, `AltitudeFromPositionDevice.java`), `REQ-DAT-033` (clause 4, `WorkoutRepository.kt`), `REQ-UI-297` (clause 1, `ElevationSmoothingMath.kt`, `ElevationProfile.kt`).
* **Historical Origin & Commit Trace**: `ATT-4859` (`a11e13d7`) & `ATT-5199` (`fa3c161b`) for `ElevationCache`; `ATT-4003` (`6073cf43`) & `ATT-4707` (`cf6444d9`) for `loadWorkoutDetailTelemetry`; `ATT-2512` (`7630148d`) & `ATT-2746` (`2efca0f1`) for `ElevationSmoothingMath`.
* **Root Reason for Existing Formulation**: `seedFromRoutes` assumed any non-null `elevation` in `Routes.db` was valid; `loadWorkoutDetailTelemetry` used an ad-hoc `0.0` default; `ElevationSmoothingMath` only guarded upper-bound non-physical spikes ($> 3000$ m).
* **Preservation of Core Invariants**: Authentic coastal $0.0$ m altitudes (median $\le 50$ m) remain valid; offline zero-lag seed display is preserved; `KnownLocations.db` isolation and SQLite single-thread safety remain intact.

---

## 5. Architectural Strategy & High-Level Solution
1. **ElevationCache Cleanse & Guard (`ElevationCacheDatabaseManager.kt`)**:
   - Filter `abs(elevation) < 1e-4` during `seedFromRoutes()` and `seedFromSegments()`, and skip routes/segments where all elevations are zero.
   - Add `purgeCorruptZeroSeeds(): Int` executing `DELETE FROM elevation_cache WHERE altitude == 0.0 AND source IN ('ROUTE_SEED', 'SEGMENT_SEED')` on startup/seeding.
2. **Neighbor Seed Cell Upgrade (`AltitudeFromPositionDevice.java`)**:
   - Pass matched `ElevationCacheItem?` to `fetchDemElevationAsync`.
   - Upon successful DEM resolution, update the matched neighbor cell `(cachedItem.latE5, cachedItem.lonE5)` to `demElevation` with `DEM_CACHE` (or purge) if `cachedItem` was a seed.
3. **Telemetry Null-Safety Parity (`WorkoutRepository.kt:855`)**:
   - Set `alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else null`.
4. **Outlier Interpolation (`ElevationSmoothingMath.kt`, `ElevationProfile.kt`, `CropWorkoutMath.kt`)**:
   - In `ElevationSmoothingMath.smoothAltitudes` and `calculateElevationBounds`, evaluate `medianAlt`: if `medianAlt > 50.0`, treat isolated points with `abs(alt) < 1e-4` as invalid/missing, interpolating them cleanly across the terrain.
5. **Side-Effect & Cross-Caller Impact Audit (Rule 68)**:
   - `AltitudeFromPositionDevice`: Preserves offline responsiveness; prevents stale neighbor re-poisoning.
   - `WorkoutRepository` & `TrackOnMapAftermathViewModel`: Downstream consumers receive `null` instead of phantom `0.0`, triggering smooth Loess interpolation.
   - `ElevationCacheDatabaseManagerTest` & `AltitudeFromPositionDeviceTest`: Update and expand unit tests to cover 2D route rejection, neighbor upgrade, and self-healing purge.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Coastal workouts at real sea level ($\le 50$ m) are never corrupted.
  2. Authoritative `DEM_CACHE` entries are never purged or overwritten by lower-priority sources.
  3. Single-thread database access and Rule 20 mapping symmetry are preserved.
* **Risk Rating**: **MEDIUM**. Touches spatial elevation cache, device DEM fetcher, repository telemetry projection, and profile smoothing; fully verified by targeted JVM contract and unit tests.
