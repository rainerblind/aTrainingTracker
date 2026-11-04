# Stage 3: Implementation Plan - ATT-5312: Prevent 0.0m ROUTE_SEED Cache Poisoning & Detail Telemetry NULL Altitude Fallback

**Ticket**: [ATT-5312](https://rainerblind.atlassian.net/browse/ATT-5312)  
**Sub-task**: [[ATT-5317]](https://rainerblind.atlassian.net/browse/ATT-5317) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-SEN-025.6`, `REQ-SEN-025.7`, `REQ-SEN-025.8`  
**Test Mapping**: `TST-SEN-025.6`, `TST-SEN-025.7`, `TST-SEN-025.8`  
**Branch**: `feature/ATT-5312`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
Forensic investigation of "Zum Bäcker #46" revealed the elevation profile Y-axis stretched from 0 to 486m due to:
1. `ElevationCache.db` poisoned by 609 `0.0 m` `ROUTE_SEED` points from imported 2D routes in `Routes.db`.
2. `AltitudeFromPositionDevice` neighbor lookup (`toleranceE5 = 2`) hitting a `0.0 m` seed without upgrading the neighbor cell upon Open-Meteo DEM resolution.
3. `WorkoutRepository.loadWorkoutDetailTelemetry` defaulting missing altitude to `0.0` instead of `null` (diverging from lines 438 and 516).
4. `ElevationSmoothingMath` and `calculateElevationBounds` treating isolated `0.0 m` dropouts on inland terrain (> 50 m MSL) as real elevations, collapsing profile baseline.

---

## 2. Traceability & Requirements Mapping
* **`REQ-SEN-025.6` (2D Route Seeding Rejection & Self-Healing Purge)** $\leftrightarrow$ `TST-SEN-025.6`: Reject 2D dummy `0.0 m` routes and purge existing `0.0 m` seeds in `ElevationCacheDatabaseManager.kt`.
* **`REQ-SEN-025.7` (Neighbor Seed DEM Upgrade)** $\leftrightarrow$ `TST-SEN-025.7`: Upgrade matched neighbor seed cell to `DEM_CACHE` in `AltitudeFromPositionDevice.java`.
* **`REQ-SEN-025.8` (Telemetry NULL Parity & Outlier Interpolation)** $\leftrightarrow$ `TST-SEN-025.8`: Return `null` for missing altitude in `WorkoutRepository.kt`; interpolate isolated `0.0 m` dropouts when `medianAlt > 50` m in `ElevationSmoothingMath.kt` and `calculateElevationBounds`.

---

## 3. System Invariants & Preserved Behavior
1. **Sea-Level Preservation**: Coastal rides (`medianAlt <= 50.0` m) preserve authentic `0.0 m` elevation samples.
2. **DEM Priority Invariant**: Authoritative `DEM_CACHE` (priority 30) is never overwritten by seeds or purged.
3. **Offline Responsiveness & Decoupling**: Instant local seed emission preserved; `KnownLocations.db` never mutated (`REQ-SEN-025.4`).

---

## 4. Proposed Architectural Changes
* **Database (`ElevationCacheDatabaseManager.kt`)**: Add `purgeCorruptZeroSeeds(): Int` (`DELETE FROM elevation_cache WHERE altitude == 0.0 AND source IN ('ROUTE_SEED', 'SEGMENT_SEED')`); filter `abs(elevation) < 1e-4` and skip all-zero routes in `seedFromRoutes()` / `seedFromSegments()`; add `putElevationE5(...)`.
* **Device (`AltitudeFromPositionDevice.java`)**: Pass matched seed `ElevationCacheItem?` to `fetchDemElevationAsync`; upon DEM resolution, upgrade matched neighbor cell to `DEM_CACHE` via `putElevationE5`.
* **Repository (`WorkoutRepository.kt:855`)**: `alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else null`.
* **Math & Profile (`ElevationSmoothingMath.kt`, `ElevationProfile.kt`)**: In `smoothAltitudes` and `calculateElevationBounds`, evaluate `medianAlt`: if `medianAlt > 50.0`, treat `abs(alt) < 1e-4` as invalid/missing and interpolate across terrain.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: ElevationCache 2D Route Rejection & Purge (`ElevationCacheDatabaseManager.kt`, `TST-SEN-025.6`)
* Files: `ElevationCacheDatabaseManager.kt`, `ElevationCacheDatabaseManagerTest.kt`
* Implement `purgeCorruptZeroSeeds()`, call in seeding methods, filter `abs(elev) < 1e-4`, add `putElevationE5`. Add unit tests.

### Step 2: Neighbor Seed Cell DEM Upgrade (`AltitudeFromPositionDevice.java`, `TST-SEN-025.7`)
* Files: `AltitudeFromPositionDevice.java`, `AltitudeFromPositionDeviceTest.kt`
* Pass matched seed to `fetchDemElevationAsync`; update matched neighbor cell upon DEM resolution. Add unit tests.

### Step 3: Telemetry NULL Parity (`WorkoutRepository.kt:855`, `TST-SEN-025.8`)
* Files: `WorkoutRepository.kt`, `WorkoutRepositoryTelemetryTest.kt`
* Change `else 0.0` to `else null` in `loadWorkoutDetailTelemetry`. Add unit test asserting `null` altitude output.

### Step 4: Elevation Outlier Interpolation (`ElevationSmoothingMath.kt`, `ElevationProfile.kt`, `TST-SEN-025.8`)
* Files: `ElevationSmoothingMath.kt`, `ElevationProfile.kt`, `ElevationSmoothingMathTest.kt`
* Evaluate `medianAlt`: interpolate isolated `0.0 m` points when `medianAlt > 50.0`, while preserving them when `medianAlt <= 50.0`. Add unit tests.

### Step 5: Targeted Unit Test Suite & Governance Verification
* Commands:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.ElevationCacheDatabaseManagerTest" --tests "com.atrainingtracker.banalservice.devices.AltitudeFromPositionDeviceTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryTelemetryTest" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationSmoothingMathTest"`
  `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests across cache, device, repository, and smoothing math; clean-room full suite in Stage 5.
* **Rollback**: Clean git revert of `feature/ATT-5312`; no SQLite table migrations or external API breaking changes.
