# Stage 5 Walkthrough: ATT-6700 - Fix RadarWindowAggregator 1-second rolling latch window, road-clear null distance reset, and workout-start accumulator reset

**Ticket**: [ATT-6700](https://rainerblind.atlassian.net/browse/ATT-6700)  
**Sub-task**: [ATT-6928](https://rainerblind.atlassian.net/browse/ATT-6928) (`[Test]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-028.5` (*Scalar 1 Hz Latch Aggregator, Vehicle Counter & Zero-Migration Persistence*)  
**Test Spec Mapping**: `TST-CON-028.3` (*1 Hz Window Aggregator & Vehicle Counter Hysteresis Unit Tests*)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Problem & Scope Overview
Under high-frequency (4–8 Hz) radar streams, `RadarWindowAggregator` previously cleared metrics per incoming frame, causing transient sub-second vehicle passes to be missed between 1 Hz `TrackerService` database polls. Additionally, defensive non-null checks in `BTLERadarDevice.java` prevented dispatching `null` when the road cleared (causing live tiles and database records to repeat stale distances indefinitely), and workout-start accumulator resets did not clear the radar aggregator session. `ATT-6700` implements a 1,000 ms sliding latch window, road-clear null distance/passing dispatch, and full session reset on `RESET_ACCUMULATORS_INTENT`.

---

## 2. Key Changes Implemented

1. **1,000 ms Sliding Latch Window (`RadarWindowAggregator.kt`)**:
   - Replaced instantaneous frame latching with a timestamped FIFO buffer (`frameBuffer = ArrayDeque<TimedFrame>()`, `WINDOW_DURATION_MS = 1000L`).
   - Injected monotonic high-resolution time provider (`clock: () -> Long = { System.nanoTime() / 1_000_000L }`), ensuring rock-solid deterministic unit testing and zero Android framework stubs.
   - `onTargetsUpdated(targets, nowMs)` prunes frames older than `nowMs - 1000L` and buffers incoming observations.
   - `sampleAndResetWindow(nowMs)` computes latched peak threats, min closest distance, max approach speed, min passing clearance, and large vehicle count across all frames surviving in the 1,000 ms window.
   - Adheres to Rule 1.8 constructor baseline state initialization parity with `resetSession()`.

2. **Road-Clear Null Propagation (`BTLERadarDevice.java`)**:
   - Stripped defensive `!= null` checks in `processTargets()` to allow `snapshot.getClosestDistanceMeters()` (`null`) and `snapshot.getPassingDistanceMeters()` (`null`) to propagate to `mClosestDistanceSensor` and `mPassingDistanceSensor`. Live cockpit tiles return to `--` and database records stop repeating stale distances.
   - Added `resetSensorsToClear()` invoked on GATT disconnect and 3.5s starvation watchdog.

3. **Workout-Start Accumulator Reset (`BTLERadarDevice.java`)**:
   - Overrode `onAccumulatorsReset()` hook from `MyDevice.java` (triggered on `BANALService.RESET_ACCUMULATORS_INTENT`) to invoke `mWindowAggregator.resetSession()` and push `0` to `mVehicleCountSensor` and `mLargeVehicleCountSensor`.

---

## 3. Verification & Test Evidence

| Test Suite | Scope | Target | Result |
| :--- | :--- | :--- | :--- |
| `RadarWindowAggregatorTest` | Unit | 8 Hz burst latching, 1,000 ms decay, passing hysteresis, resetSession | **PASS** (11/11 tests) |
| `BTLERadarDeviceTest` | Unit | Road-clear null propagation, onAccumulatorsReset, watchdog starvation reset | **PASS** (12/12 tests) |
| `RadarTrafficSimulatorTest` | Unit | 8 Hz scenario generation, vehicle overtaking counts, hardware preemption | **PASS** (8/8 tests) |
| `RadarLightControllerTest` | Unit | Day/night flash transitions, simulator integration | **PASS** (3/3 tests) |
| `verify_requirement_governance.py` | Governance | Living doc master sync, 0 unmounted islands, Chesterton audit | **PASS** (100% parity) |

---

## 4. Conclusion & Next Steps
All requirements for `REQ-CON-028.5` and `TST-CON-028.3` are verified and marked `[Verified]`. Ready for Gate 5 Review by agent2 (`subagent_pro`), parent advancement to `Final Review (Human)`, and squash merge onto `sprint/2026-46.2`.
