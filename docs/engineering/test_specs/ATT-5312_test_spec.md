# Stage 2: Requirement & Test Specification - ATT-5312

**Ticket**: [ATT-5312](https://rainerblind.atlassian.net/browse/ATT-5312)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.4` | **Branch**: `feature/ATT-5312`  
**Requirement Mapping**: `REQ-SEN-025` (*Pairable Position-Based Elevation Sensor & Dedicated Spatial DEM Cache*)  
**Test Spec ID**: `TST-SEN-025` | **Author**: AI Agent 1 (Flash Implementer) | **Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-SEN-025)
* `REQ-SEN-025.6` (**2D Route Zero Elevation Rejection & Self-Healing Purge**): `seedFromRoutes()` and `seedFromSegments()` SHALL reject trackpoints where `abs(elevation) < 1e-4` or routes with all-zero elevations, preventing dummy 2D points from entering `ElevationCache.db`. The manager SHALL run `purgeCorruptZeroSeeds()` on startup/seeding to remove existing `0.0 m` entries with source `ROUTE_SEED` or `SEGMENT_SEED`.
* `REQ-SEN-025.7` (**Neighbor Seed DEM Upgrade & Anti-Poisoning**): When `AltitudeFromPositionDevice` matches a non-authoritative neighbor seed cell (`cachedItem.latE5 != latE5 || cachedItem.lonE5 != lonE5`), upon successful `fetchDemElevationAsync` DEM query, the system SHALL update both the current position and the matched neighbor seed cell to `DEM_CACHE` (or purge the corrupt seed), preventing repetitive re-poisoning.
* `REQ-SEN-025.8` (**Telemetry Null-Altitude Parity & Outlier Smoothing**): `WorkoutRepository.loadWorkoutDetailTelemetry` SHALL map missing altitude to `null` (`altIdx != -1 && !cursor.isNull(altIdx)`). In `ElevationSmoothingMath`, isolated `0.0 m` dropouts during workouts with `medianAlt > 50.0` m SHALL be treated as invalid/missing and interpolated across terrain, while legitimate sea-level workouts (`medianAlt <= 50.0` m) preserve `0.0 m` samples.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-SEN-025.2`, `REQ-SEN-025.3`, `REQ-SEN-025.6`, `REQ-SEN-025.7`, `REQ-SEN-025.8`. Targets `ElevationCacheDatabaseManager.kt`, `AltitudeFromPositionDevice.java`, `WorkoutRepository.kt`, and `ElevationSmoothingMath.kt`.
* `Historical Origin & Commit Trace`: Sprint 2026-44.2 (`ATT-4859`, commit `a11e13d7`) & Sprint 2026-45.2 (`ATT-5199`, commit `fa3c161b`); amended in Sprint 2026-45.4 (`ATT-5312`).
* `Root Reason for Existing Formulation`: Route/segment seeding previously accepted any non-null elevation, allowing imported 2D routes with dummy 0.0m points to poison the cache as `ROUTE_SEED`. Neighbor tolerance matching hit poisoned seeds without upgrading the neighbor cell upon DEM resolution. Additionally, `loadWorkoutDetailTelemetry` defaulted missing altitude to `0.0` instead of `null`, preventing Loess smoothing interpolation and causing 0.0m profile drops.
* `Preservation of Core Invariants`: Valid coastal workouts at true sea level (`medianAlt <= 50.0` m) preserve `0.0 m` values. Authoritative `DEM_CACHE` entries are never purged or overwritten by seeded points. Offline zero-latency seed emission is preserved while corrupt zero-seeds are self-healed.

---

## 2. Acceptance Criteria & System Invariants
* **Criterion 1 (2D Route Seeding & Self-Healing)**:
  * *Given* routes with all-zero elevations and an `ElevationCache.db` containing legacy 0.0m seed rows,
  * *When* `seedFromRoutes()` and `purgeCorruptZeroSeeds()` execute,
  * *Then* 0.0m dummy points are rejected and legacy 0.0m seed records are cleanly purged.
* **Criterion 2 (Neighbor Seed Upgrade)**:
  * *Given* a position query matching a neighbor `ROUTE_SEED` cell within tolerance,
  * *When* `fetchDemElevationAsync` successfully fetches DEM elevation from Open-Meteo,
  * *Then* both queried coordinates and the neighbor seed cell are upgraded to `DEM_CACHE`.
* **Criterion 3 (Telemetry NULL Parity & Smoothing Interpolation)**:
  * *Given* a workout with unrecorded altitude rows and isolated 0.0m dropouts on terrain with `medianAlt > 50` m,
  * *When* `loadWorkoutDetailTelemetry` loads telemetry and `smoothAltitudes` calculates profile,
  * *Then* missing altitude is loaded as `null` and isolated 0.0m dropouts are interpolated cleanly without plunging the Y-axis.
* **System Invariants**: Sea-level workouts (`medianAlt <= 50.0` m) retain 0.0m values; `KnownLocations.db` is never touched; `DEM_CACHE` records have highest priority and are never overwritten by seeds.

---

## 3. Test Specification (TST-SEN-025)
* `[TST-SEN-025.6]` (**Zero Elevation Seed Rejection & Purge**, Unit Test): `ElevationCacheDatabaseManagerTest.kt` asserts dummy 0.0m route points are skipped during seeding and `purgeCorruptZeroSeeds()` removes 0.0m `ROUTE_SEED`/`SEGMENT_SEED` rows while preserving `DEM_CACHE`.
* `[TST-SEN-025.7]` (**Neighbor Seed DEM Upgrade**, Unit Test): `AltitudeFromPositionDeviceTest.kt` verifies that matching a non-authoritative neighbor seed cell triggers DEM fetch and upgrades the neighbor seed cell to `DEM_CACHE`.
* `[TST-SEN-025.8]` (**Telemetry NULL Parity & Outlier Interpolation**, Unit Test): `WorkoutRepositoryTelemetryTest.kt` asserts `loadWorkoutDetailTelemetry` returns `null` for unrecorded altitude. `ElevationSmoothingMathTest.kt` asserts isolated 0.0m points on `medianAlt > 50` m terrain are interpolated, while `medianAlt <= 50` m preserves 0.0m.
* `[TST-SEN-025.5]` (**9-Language Parity**, Unit Test): `TranslationParityTest.kt` validates 100% string parity across all 9 locales.
* `[TST-REG-001]` (**Clean-Room Regression Suite**): `./gradlew testDebugUnitTest` runs cleanly with 100% pass rate.

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SEN-025.6]` | Unit | `ElevationCacheDatabaseManager.seedFromRoutes` & `purgeCorruptZeroSeeds` | `REQ-SEN-025.6` | Specified |
| `[TST-SEN-025.7]` | Unit | `AltitudeFromPositionDevice.onLocationChanged` & `fetchDemElevationAsync` | `REQ-SEN-025.7` | Specified |
| `[TST-SEN-025.8]` | Unit | `WorkoutRepository.loadWorkoutDetailTelemetry` & `ElevationSmoothingMath` | `REQ-SEN-025.8` | Specified |
| `[TST-SEN-025.5]` | Localization | `TranslationParityTest` | `REQ-SEN-025.5` | Specified |
| `[TST-REG-001]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
