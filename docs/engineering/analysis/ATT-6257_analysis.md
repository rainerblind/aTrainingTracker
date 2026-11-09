# Stage 1 Analysis: ATT-6257 - Re-bind filters when ProxySensor/MySensor is re-registered or service restarts

**Ticket**: [ATT-6257](https://rainerblind.atlassian.net/browse/ATT-6257)  
**Sub-task**: [ATT-6299](https://rainerblind.atlassian.net/browse/ATT-6299) (`[Analysis]`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-6257`  
**Requirement Mapping**: `REQ-TRK-055`  
**Test Mapping**: `TST-TRK-055`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-09  

---

## 1. Problem Statement & Motivation
During live tracking, Cockpit tiles (`SPEED_mps`, `DISTANCE_m`, `ALTITUDE`, `SLOPE`) and derived metrics in `VerticalSpeedAndSlopeDevice` (`ASCENT`, `DESCENT`, `SLOPE`, `VERTICAL_SPEED`) freeze or show `--` / `0,00 km`, while `TrackerService` continues logging raw samples every second to `WorkoutSamples.db`, map polylines update, and time tiles advance. This occurs whenever a sensor disconnects/reconnects (e.g. GPS loss/recovery via `LocationUnavailable()`, BLE/ANT+ reconnect, pairing change, or 5-minute background service disconnect/restart).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
Cockpit tiles (`TrackingViewModel` via `allFilteredSensorData`) and `VerticalSpeedAndSlopeDevice` read strictly through `FilterManager`. Three interacting lifecycle defects break the pipeline:
1. **Orphaned `ProxySensor` Binding**: In `MySensorManager.unregisterSensor()` (L279–284) and `setBestSensorForProxySensor()` (L320–326), when the last active sensor for a `SensorType` unregisters (`newBestSensor == null`), `MySensorManager` calls `removeSensor(sensorType)` and deletes the `ProxySensor` from `mSensorMap`. On sensor re-registration (`registerSensor()`, L191–194), a *new* `ProxySensor` is created. `FilterManager.createFilter()` (L101–104) early-returns on `containsKey()`, and `mSensorsChangedReceiver` only retries `mRequiredButNotAvailableFilters`. Thus, existing filters in `myFilterMap` and `mMapFilter2Sensor` remain permanently bound to the *old, discarded* `ProxySensor`, never receiving updates from the new one.
2. **Missing Re-Registration on Device Re-Creation (`deviceName != null`)**: When devices are recreated (via `DeviceManager.pairingChanged()`, search timeout, or provider toggles), new `MySensor` instances are instantiated, while device-specific filters remain bound to the previous dead `MySensor`.
3. **Stale Static Managers Across Service Lifecycles**: `BANALService.onDestroy()` (L583–605) calls `shutDown()` on `cDeviceManager`, `cSensorManager`, and `cFilterManager`, but leaves static references non-null. After background idle timeout restarts, static calls hit shut-down managers, and filters are not re-bound to active sensors.
* **Independent Peer Review & Verification**:
  * Verified live code: `removeSensor` unregisters the `ProxySensor` in `MySensorManager`, destroying listener continuity despite `ProxySensor.setSourceSensor()` already supporting listener migration. Verified `FilterManager.createFilter()` discards existing hashkeys without auditing whether the backing sensor instance changed.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Retain persistent `ProxySensor` instances per `SensorType` in `MySensorManager` across sensor unregister/register cycles, calling `proxy.setSourceSensor(null)` on disconnect and `proxy.setSourceSensor(newBestSensor)` on reconnect.
  * Implement dynamic filter re-binding in `FilterManager`: in `createFilter()` and on `SENSORS_CHANGED`, rebind existing filters in `myFilterMap` if their backing sensor instance has changed.
  * Clean up static references in `BANALService.onDestroy()` and ensure clean manager initialization on service restart.
  * Maintain continuous live telemetry in Cockpit tiles and `VerticalSpeedAndSlopeDevice` across disconnect/reconnect and service restarts.
  * Add unit and contract tests in `FilterManagerRebindTest.kt` and `MySensorManagerProxyTest.kt`.
* **Out-of-Scope Non-Goals**:
  * Modifying `VerticalSpeedAndSlopeDevice` VAM/deadband math or `TrackerService` database persistence.
  * Redesigning Cockpit Compose UI layouts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-055` (*Persistent Sensor Proxy Binding & Automatic Filter Re-Connection*), augmenting `REQ-SEN-021` (*ProxySensor Thread Safety & Listener Migration*) and `REQ-FIL-011`/`REQ-FIL-013` (*Filter Lifecycle & Reset*).
* **Historical Origin & Commit Trace**: Commit `98fb9019` (`ATT-4096`, `REQ-SEN-021`) eliminated lock-order inversions during listener migration; commit `1b0313c1` (`ATT-5191`, `REQ-FIL-013`) added accumulator re-seeding with `ProxySensor` unwrapping.
* **Root Reason for Existing Formulation**: Early designs assumed sensors register only once at startup. Destroying `ProxySensor` on disconnect broke listener continuity.
* **Preservation of Core Invariants**: Lock-order inversion prevention outside monitors (`REQ-SEN-021.2/.3`), priority lists (`REQ-CON-003`), and zero-value accumulator seeding (`REQ-FIL-013`) MUST remain preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Persistent `ProxySensor` in `MySensorManager`**: Keep `ProxySensor` in `mSensorMap` when `newBestSensor == null`; dispatch `setSourceSensor(null)`. On reconnect, reuse the existing `ProxySensor` and dispatch `setSourceSensor(newBestSensor)`. Update `computeGCDataLocked()` and `getAvailableSensorTypeSet()` to evaluate `proxy.getSourceSensor() != null`.
2. **Filter Re-Binding in `FilterManager`**: If `myFilterMap.containsKey(hashKey)`, check whether the active sensor matches `mMapFilter2Sensor`. If changed, migrate listeners and update `mMapFilter2Sensor`. Rebind existing filters upon `SENSORS_CHANGED`.
3. **Service Lifecycle Hygiene**: Clear static references in `BANALService.onDestroy()` and ensure fresh initialization in `onCreate()`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing filter/sensor tests (`FilterManagerTest`, `VerticalSpeedAndSlopeDeviceTest`).
  2. No deadlocks or lock-order inversions during listener migrations.
  3. Parent ticket Human Decision Gate strictly enforced.
* **Risk Rating**: **HIGH** (Core sensor dispatch and filter infrastructure across all live telemetry and derived devices).
