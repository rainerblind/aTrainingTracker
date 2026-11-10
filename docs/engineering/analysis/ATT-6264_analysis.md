# Stage 1 Analysis: ATT-6264 - Prioritize Topographic Altitude (DEM) Above Barometer When Explicitly Paired

**Ticket**: [ATT-6264](https://rainerblind.atlassian.net/browse/ATT-6264)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6264`  
**Requirement Mapping**: `REQ-TRK-024` (`REQ-TRK-024.1`, modifying priority order) & `REQ-SEN-025` | **Test Mapping**: `TST-TRK-016` / `TST-SEN-025`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Empirical evaluation in `WorkoutSamples.db` confirms that *Topografische Höhe (DEM)* (`AltitudeFromPositionDevice`) provides absolute, weather- and pocket-pressure-independent elevation. However, in `MySensorManager.ALTITUDE_PRIORITY`, `DeviceType.ALTITUDE_FROM_PRESSURE` (rank 0) currently precedes `DeviceType.ALTITUDE_FROM_POSITION` (rank 1). Because `ALTITUDE_FROM_PRESSURE` is paired by default on all smartphones with a barometric pressure sensor, an athlete who explicitly seeks out and pairs `Topografische Höhe (DEM)` in settings observes zero effect on the primary `ALTITUDE` sensor, telemetry logging, or `VerticalSpeedAndSlopeDevice` (ascent/descent/slope) unless they also manually unpair `Höhe aus Luftdruck`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Hardcoded Priority Inversion**: In `MySensorManager.java:64-70`, `ALTITUDE_PRIORITY` statically places `ALTITUDE_FROM_PRESSURE` at index 0 and `ALTITUDE_FROM_POSITION` at index 1.
2. **Asymmetric Default Pairing**: In `DevicesDatabaseManager.java:1394` and `DeviceManager.java:355`, `ALTITUDE_FROM_POSITION` is default unpaired (`PAIRED = false`), whereas `ALTITUDE_FROM_PRESSURE` is default paired (`PAIRED = true`).
3. **Delayed Registration Design**: `AltitudeFromPositionDevice.java:218` only invokes `registerSensors()` after resolving its first valid elevation fix from `ElevationCache.db` or `ElevationService`.
* **Independent Peer Review & Verification**:
  * Audited `MySensorManager.java` (`getBestSensor` L339–361, `getPriorityRank` L462–463): swapping array elements at ranks 0 and 1 gives DEM precedence when active without affecting unmapped devices.
  * Audited `VerticalSpeedAndSlopeDevice.java` (`checkAltitudeSourceTransition` L339–365): transitions between barometer and DEM automatically trigger `reseedAltitudeFilters()`, eliminating transient vertical speed or slope spikes.
  * Audited `SensorSourceDialog.kt` (L304, L314): device telemetry lists use `MySensorManager.getPriorityRank(sensorType, ...)` for deterministic sorting, naturally placing DEM first when prioritized.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Reorder `MySensorManager.ALTITUDE_PRIORITY`: place `DeviceType.ALTITUDE_FROM_POSITION` at rank 0 and `DeviceType.ALTITUDE_FROM_PRESSURE` at rank 1.
  * Retain `defaultPaired = false` for `ALTITUDE_FROM_POSITION` in `DeviceManager.java` and `DevicesDatabaseManager.java`.
  * Update `MySensorManagerPriorityTest.kt` to assert the updated rank order (`ALTITUDE_FROM_POSITION` > `ALTITUDE_FROM_PRESSURE`).
  * Update living documentation in `docs/requirements/req_tracking_cockpit.md` (`REQ-TRK-024.1`).
* **Out-of-Scope Non-Goals**:
  * Do not alter `DeviceType.java` enum declaration order (maintains ordinal stability).
  * Do not alter `ElevationCache.db` schema, cache resolution, or network query lifecycles.
  * Do not enable `ALTITUDE_FROM_POSITION` by default on fresh installs.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Modifies `REQ-TRK-024` (`REQ-TRK-024.1`) and complements `REQ-SEN-025`. Targets `MySensorManager.java` (`ALTITUDE_PRIORITY`) and `MySensorManagerPriorityTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-4859` (Sprint 2026-44.2, commit `a11e13d7`) introduced `ALTITUDE_FROM_POSITION`. `ATT-5491` / `ATT-5492` (Sprint 2026-45.3) codified `ALTITUDE_PRIORITY` with barometer at rank 0.
* **Root Reason for Existing Formulation**: Barometer was originally prioritized because it provides smooth 1-Hz relative elevation without requiring a GPS fix or network connectivity. However, hardcoding it above an explicitly paired virtual sensor rendered user activation ineffective without manual unpairing.
* **Preservation of Core Invariants**: Out of the box, `ALTITUDE_FROM_PRESSURE` remains the active default sensor since DEM is unpaired. Fallback to barometer before initial GPS lock or indoors remains instantaneous.

---

## 5. Architectural Strategy & High-Level Solution
* **`MySensorManager.java`**: Update `ALTITUDE_PRIORITY`:
  `[DeviceType.ALTITUDE_FROM_POSITION, DeviceType.ALTITUDE_FROM_PRESSURE, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED, DeviceType.SPEED_AND_LOCATION_GPS]`.
* **Dynamic Priority Behavior**:
  * *Default state*: DEM unpaired $\to$ `getBestSensor(ALTITUDE)` selects `ALTITUDE_FROM_PRESSURE`.
  * *DEM paired & active*: Once first DEM fix resolves, `registerSensors()` registers DEM $\to$ `getBestSensor(ALTITUDE)` selects `ALTITUDE_FROM_POSITION`.
  * *Indoor / Startup*: Before GPS fix arrives, DEM is not registered $\to$ seamlessly defaults to barometer.
  * *SensorSourceDialog*: Correctly displays DEM above barometer in source selection.

---

## 6. System Invariants & Risk Assessment (Side-Effect Audit)
* **Core Invariants**: Out-of-the-box barometer behavior intact; zero startup lag; filter re-seeding on transition (`REQ-FIL-012.5`); 100% test pass rate across `MySensorManagerPriorityTest`.
* **Risk Rating**: **LOW** (Clean array reordering in sensor priority list; fully decoupled from database schema and network logic).
