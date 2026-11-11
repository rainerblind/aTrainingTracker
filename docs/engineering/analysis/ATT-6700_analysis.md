# Stage 1 Analysis: ATT-6700 - Fix RadarWindowAggregator 1-second rolling latch window, road-clear null distance reset, and workout-start accumulator reset

**Ticket**: [ATT-6700](https://rainerblind.atlassian.net/browse/ATT-6700)  
**Sub-task**: [ATT-6917](https://rainerblind.atlassian.net/browse/ATT-6917) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6700`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
`RadarWindowAggregator` is designed to latch high-rate (4–8 Hz / 125 ms) radar frames into 1 Hz peak threat count, max approach speed, and closest/passing distances so transient sub-second vehicle passes are captured by 1 Hz `TrackerService` DB polling. However:
1. `BTLERadarDevice.java` called `mWindowAggregator.sampleAndResetWindow()` immediately on every BLE packet notification (4–8 Hz), wiping latched state every ~125 ms and destroying the 1,000 ms rolling latch window before `TrackerService` samples.
2. In `BTLERadarDevice.java`, sensor updates explicitly check `&& snapshot.getClosestDistanceMeters() != null`, preventing `null` from being dispatched when the road clears. Consequently, cockpit distance tiles remain permanently frozen at the last observed distance (e.g., 5 m) and `WorkoutSamples.db` records duplicate stale distances over clear road segments.
3. `BTLERadarDevice.java` does not override `onAccumulatorsReset()`, leaving session vehicle counters (`RADAR_VEHICLE_COUNT`, `RADAR_LARGE_VEHICLE_COUNT`) unreset when `BANALService.RESET_ACCUMULATORS_INTENT` fires at workout start.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Per-Packet Reset Defeating Rolling Window)**: `sampleAndResetWindow()` was originally designed for periodic 1 Hz invocation. Calling it inside `processTargets()` per packet cleared `currentWindowMaxThreats`, `currentWindowMinDistance`, and `currentWindowMaxSpeed` every 125 ms. Transient sub-second passes (<1 s) were erased before 1 Hz DB poll ticks.
* **RCA 2 (Sticky Stale Distance Values)**: In `BTLERadarDevice.java` lines 252 and 261, `newValue()` is guarded by `&& snapshot.getClosestDistanceMeters() != null`. When road clears, `snapshot` returns `null`, but the guard skips `mClosestDistanceSensor.newValue(null)`. `MySensor.mValue` retains stale distance indefinitely.
* **RCA 3 (Missing Accumulator Reset)**: `MyDevice.java` receives `BANALService.RESET_ACCUMULATORS_INTENT` and triggers `onAccumulatorsReset()`. `BTLERadarDevice.java` never overrides this hook, so `mWindowAggregator.resetSession()` is not called and sensors are not reset to 0.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Refactor `RadarWindowAggregator` to maintain a true 1,000 ms rolling sliding window (`WINDOW_DURATION_MS = 1000L`) using timestamped observations (`timestampMs: Long = SystemClock.elapsedRealtime()`) so older frames expire naturally.
  2. Return `null` for `closestDistanceMeters` and `passingDistanceMeters`, `0` for `threatCount`, and `0.0` for `approachSpeedMps` when no active approaching targets exist in the 1,000 ms rolling window.
  3. Remove the null checks in `BTLERadarDevice.java` (lines 252 & 261) to allow `null` propagation to `mClosestDistanceSensor` and `mPassingDistanceSensor`.
  4. Override `onAccumulatorsReset()` in `BTLERadarDevice.java` to call `mWindowAggregator.resetSession()` and publish 0 to `mVehicleCountSensor` and `mLargeVehicleCountSensor`. Also reset sensors on disconnect/watchdog.
  5. Author unit tests covering 8 Hz sub-second bursts, 1,000 ms rolling window expiration, road-clear null distance transitions, and workout-start accumulator resets.
* **Out-of-Scope Non-Goals**:
  * Changing BLE packet parsing or ANT+ driver logic.
  * Altering `TrackerService` 1 Hz DB poll cadence.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Reference**: `REQ-CON-028.5` (Window aggregation), `REQ-CON-030.4` (Lateral clearance), `REQ-CON-030.6` (Vehicle counting hysteresis).
* **Historical Origin**: Commit `d23ef741` (initial radar aggregator). The non-null sensor check was a defensive carry-over from scalar sensor classes, but `MySensor` properly handles `null` values.
* **Preservation of Invariants**: Rule 1.8 constructor state initialization parity; passing hysteresis logic preserved; deterministic time injection for unit tests.

---

## 5. Architectural Strategy & High-Level Solution
* **Sliding Window Buffer**: Store timestamped frames/targets in `RadarWindowAggregator`. On each update or sample, prune frames with `timestampMs < now - 1000L`. Peak threats, min distance, max speed, and min passing distance are computed over active entries in the 1,000 ms window.
* **Road-Clear Null Handling**: When the window has no targets, `closestDistanceMeters` and `passingDistanceMeters` return `null`. `BTLERadarDevice` passes `null` to `mClosestDistanceSensor.newValue(...)` and `mPassingDistanceSensor.newValue(...)`.
* **Session Lifecycle**: In `BTLERadarDevice.onAccumulatorsReset()`, reset aggregator session and reset vehicle sensors to 0.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Rolling window duration exactly 1,000 ms.
  2. Passing vehicle counting hysteresis unaffected.
  3. Constructor baseline state parity with `resetSession()` (Rule 1.8).
* **Risk Rating**: **LOW** (Targeted logic fix in aggregation and sensor forwarding with comprehensive unit test coverage).
