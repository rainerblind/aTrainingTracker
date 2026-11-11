# Stage 2: Requirement & Test Specification - ATT-6700: Fix RadarWindowAggregator 1-second rolling latch window, road-clear null distance reset, and workout-start accumulator reset

**Ticket**: [ATT-6700](https://rainerblind.atlassian.net/browse/ATT-6700)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-028.5` (*Scalar 1 Hz Latch Aggregator, Vehicle Counter & Zero-Migration Persistence*)  
**Test Spec ID**: `TST-CON-028.3` | **Branch**: `feature/ATT-6700`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-CON-028.5 Refinement)

### 1.1 Problem Statement & Rationale
`RadarWindowAggregator` is designed to latch high-rate (4–8 Hz) target frames across a 1,000 ms window for 1 Hz `TrackerService` DB polling. Currently:
1. Calling `sampleAndResetWindow()` per BLE packet (4–8 Hz) resets latched metrics every ~125 ms, erasing transient sub-second vehicle passes before 1 Hz polling.
2. `BTLERadarDevice` guards sensor updates with `&& snapshot.getClosestDistanceMeters() != null`, never dispatching `null` when the road clears, freezing cockpit tiles and recording stale duplicate distances in `WorkoutSamples.db`.
3. `BTLERadarDevice` does not override `onAccumulatorsReset()`, causing pre-ride vehicle counts to persist past workout start.

### 1.2 Functional & Architectural Requirements
1. **1,000 ms Rolling Latch Window (`REQ-CON-028.5a`)**: `RadarWindowAggregator` SHALL maintain a 1,000 ms sliding window using timestamped target observations (`WINDOW_DURATION_MS = 1000L`, `nowMs: Long = SystemClock.elapsedRealtime()`), retaining peak threat count, max approach speed, minimum closest distance, and minimum passing clearance across the last 1,000 ms.
2. **Road-Clear Null Distance Propagation (`REQ-CON-028.5b`)**: When no active approaching targets exist in the 1,000 ms window (clear road), `RadarWindowAggregator` SHALL return `null` for `closestDistanceMeters` and `passingDistanceMeters`, `0` for `threatCount`, and `0.0` for `approachSpeedMps`. `BTLERadarDevice` SHALL forward `null` to `mClosestDistanceSensor` and `mPassingDistanceSensor` so cockpit displays `--`.
3. **Workout Accumulator Reset (`REQ-CON-028.5c`)**: Upon receiving `BANALService.RESET_ACCUMULATORS_INTENT`, `BTLERadarDevice.onAccumulatorsReset()` SHALL invoke `mWindowAggregator.resetSession()` and publish `0` to `mVehicleCountSensor` and `mLargeVehicleCountSensor`.
4. **Disconnect & Watchdog Sensor Reset (`REQ-CON-028.5d`)**: On GATT disconnect or packet starvation timeout, `BTLERadarDevice` SHALL reset `mClosestDistanceSensor` and `mPassingDistanceSensor` to `null`, `mThreatCountSensor` to `0`, and `mApproachSpeedSensor` to `0.0`.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-CON-028` (specifically `REQ-CON-028.5`) targeting `RadarWindowAggregator.kt` and `BTLERadarDevice.java`.
* `Historical Origin & Commit Trace`: Commit `d23ef741` (initial radar aggregator); `ATT-6700` fixes rolling latch window, road-clear null distance reset, and workout-start accumulator reset.
* `Root Reason for Existing Formulation`: In `ATT-5083`, `sampleAndResetWindow()` was intended for periodic 1 Hz polling, but was called per packet (8 Hz), wiping latches every 125 ms. Defensive non-null guards in `BTLERadarDevice` prevented null propagation when the road cleared, and missing `onAccumulatorsReset()` left session counters unreset.
* `Preservation of Core Invariants`: Rule 1.8 constructor state initialization parity; passing hysteresis logic preserved; zero SQLite schema migrations; 100% 9-language translation parity.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* a vehicle passing at 4 m within a 125 ms burst followed by clear packets, *When* sampled at 1 Hz within 1,000 ms, *Then* peak speed and 4 m closest distance are preserved.
* *Given* road clears and 1,000 ms elapse without targets, *When* sampled, *Then* `mClosestDistanceSensor` receives `null` and cockpit displays `--`.
* *Given* active vehicle counts before workout start, *When* `BANALService.RESET_ACCUMULATORS_INTENT` fires, *Then* vehicle counts reset to 0.

---

## 2. Test Specification (TST-CON-028.3 Refinement)

### Test Case 1: 1,000 ms Rolling Latch Window Across 8 Hz Bursts (`TST-CON-028.3a`)
* **Scope**: JVM Unit Test (`RadarWindowAggregatorTest.kt`)
* **Assertions**: Verifies frames within 1,000 ms retain peak threat count, max approach speed, and minimum distance across multi-packet bursts, and expire to 0/null after 1,000 ms.

### Test Case 2: Road-Clear Null Distance Transition (`TST-CON-028.3b`)
* **Scope**: JVM Unit Test (`BTLERadarDeviceTest.kt`)
* **Assertions**: Verifies clear frames emit `null` to `mClosestDistanceSensor` and `mPassingDistanceSensor`, returning `getValue() == null`.

### Test Case 3: RESET_ACCUMULATORS_INTENT Session Reset (`TST-CON-028.3c`)
* **Scope**: JVM Unit Test (`BTLERadarDeviceTest.kt`)
* **Assertions**: Verifies receiving `RESET_ACCUMULATORS_INTENT` resets aggregator session and sets `mVehicleCountSensor` and `mLargeVehicleCountSensor` to 0.

### Test Case 4: Tier 1 Targeted Radar Suite (`TST-CON-028.3d`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDeviceTest"`

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-028.3a` | Unit | `RadarWindowAggregatorTest.kt` | `REQ-CON-028.5a`, `REQ-CON-028.5b` | Specified |
| `TST-CON-028.3b` | Unit | `BTLERadarDeviceTest.kt` | `REQ-CON-028.5b`, `REQ-CON-028.5d` | Specified |
| `TST-CON-028.3c` | Unit | `BTLERadarDeviceTest.kt` | `REQ-CON-028.5c` | Specified |
| `TST-CON-028.3d` | Suite | Full Radar Package Suite | `REQ-CON-028.5a..d` | Specified |
