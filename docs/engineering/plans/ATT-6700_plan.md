# Stage 3: Implementation Plan - ATT-6700: Fix RadarWindowAggregator 1-second rolling latch window, road-clear null distance reset, and workout-start accumulator reset

**Ticket**: [ATT-6700](https://rainerblind.atlassian.net/browse/ATT-6700)  
**Sub-task**: [ATT-6921](https://rainerblind.atlassian.net/browse/ATT-6921) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-028.5a..d`  
**Test Mapping**: `TST-CON-028.3a..d`  
**Branch**: `feature/ATT-6700`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
`RadarWindowAggregator.kt` currently resets its peak metrics on every call to `sampleAndResetWindow()`. Because `BTLERadarDevice.java` called this method per BLE notification (4–8 Hz / 125 ms), sub-second vehicle passes were erased before 1 Hz `TrackerService` DB polling. Additionally, `BTLERadarDevice.java` prevented null propagation via defensive `!= null` guards, leaving distance tiles frozen at stale values after road clear, and failed to override `onAccumulatorsReset()` on `BANALService.RESET_ACCUMULATORS_INTENT`.

---

## 2. Traceability & Requirements Mapping
* **REQ-CON-028.5a (1,000 ms Rolling Window)**: `RadarWindowAggregator` buffers frames over `WINDOW_DURATION_MS = 1000L` -> `TST-CON-028.3a`
* **REQ-CON-028.5b (Road-Clear Null Dispatches)**: Dispatches `null` to `mClosestDistanceSensor` and `mPassingDistanceSensor` when clear -> `TST-CON-028.3b`
* **REQ-CON-028.5c (Workout Accumulator Reset)**: `onAccumulatorsReset()` resets aggregator and publishes 0 -> `TST-CON-028.3c`
* **REQ-CON-028.5d (Disconnect/Watchdog Fail-Safe)**: GATT disconnect or watchdog clears sensors -> `TST-CON-028.3b`

---

## 3. System Invariants & Preserved Behavior
1. **Rolling Duration**: Window spans exactly 1,000 ms (`WINDOW_DURATION_MS = 1000L`) based on `timestampMs: Long = SystemClock.elapsedRealtime()`.
2. **Hysteresis & Size Classification**: Vehicle overtaking detection (`minRange <= 18m`) and large vehicle counting logic strictly preserved.
3. **Constructor Parity**: Rule 1.8 constructor state initialization parity with `resetSession()` preserved.
4. **Thread Safety**: Aggregator internal mutations synchronized on `lock`.
5. **No DB Migration**: Uses existing `WorkoutSamples.db` null-compatible distance columns.

---

## 4. Proposed Architectural Changes
* **Component 1 (`RadarWindowAggregator.kt`)**: Implement sliding window buffer with time-based eviction (`now - 1000L`). Latched snapshot evaluates peak threat count, min closest distance, max approach speed, and min passing distance over all active observations within the window. Returns `null` distances, `0` threats, and `0.0` speed when window is clear.
* **Component 2 (`BTLERadarDevice.java`)**: Remove `!= null` checks on lines 252 & 261 to propagate `null` to `mClosestDistanceSensor` and `mPassingDistanceSensor`. Override `onAccumulatorsReset()` to invoke `mWindowAggregator.resetSession()` and push `0` to vehicle count sensors. Clear sensors to baseline on disconnect and watchdog timeout.
* **Component 3 (`RadarTrafficSimulator.kt`)**: Verify rolling window compatibility during synchronous and asynchronous scenario ticks.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 1,000 ms Rolling Sliding Window Buffer (`RadarWindowAggregator.kt`)
* Implement timestamped frame buffer (`timestampMs: Long = SystemClock.elapsedRealtime()`).
* Prune entries older than `timestampMs - 1000L`. Calculate snapshot across surviving observations.

### Step 2: Road-Clear Null Propagation & Disconnect Reset (`BTLERadarDevice.java`)
* Forward `snapshot.getClosestDistanceMeters()` and `snapshot.getPassingDistanceMeters()` unconditionally to sensors.
* Reset distance sensors to `null`, threat to `0`, and speed to `0.0` in `disconnectFromGatt()` and `mWatchdogRunnable`.

### Step 3: Accumulator Reset Hook (`BTLERadarDevice.java`)
* Override `onAccumulatorsReset()`: call `mWindowAggregator.resetSession()`, set `mVehicleCountSensor.newValue(0)`, and set `mLargeVehicleCountSensor.newValue(0)`.

### Step 4: Unit Test Suite (`RadarWindowAggregatorTest.kt`)
* Add test cases: 8 Hz multi-packet burst with 1,000 ms retention, road-clear null transition after 1,000 ms, and Rule 1.8 constructor parity.

### Step 5: Unit Test Suite (`BTLERadarDeviceTest.kt`)
* Add test cases: road-clear null distance emission, `RESET_ACCUMULATORS_INTENT` broadcast handling, and disconnect sensor reset.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted radar suite: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDeviceTest"`
* **Rollback**: Branch revert via `git checkout feature/ATT-6700`.
