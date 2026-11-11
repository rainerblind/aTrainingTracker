# Stage 3: Implementation Plan - ATT-6682: SpeedAndLocationDevice: Require entry hysteresis and ignore sub-second duplicate callbacks before stationary pinning

**Ticket**: [ATT-6682](https://rainerblind.atlassian.net/browse/ATT-6682)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-TRK-074` (*Duplicate Location Callback Filtering, Asymmetric Deadband Separation & Stationary Pinning Entry Hysteresis in SpeedAndLocationDevice*)  
**Test Mapping**: `TST-TRK-074`  
**Branch**: `feature/ATT-6682`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

During live simulation on Pixel 10 (`ATT-6256` review), `GoogleFusedSpeedAndLocationDevice` velocity repeatedly dropped to `0.0 km/h` during active cycling. Two distinct root causes were forensically established in `SpeedAndLocationDevice.java`:
1. Sub-second duplicate location callbacks (50–150 ms) with identical coordinates (`rawDelta == 0.0`) arrived with `speed == 0.0f` from motionless IMUs, immediately triggering lines 372–378 and overriding moving speed with `0.0 km/h`.
2. Using `escapeRadius` (3.75–6.0 m) for entry into stationary pinning without multi-tick hysteresis caused single-sample IMU noise spikes during low-speed climbing ($\le 15\text{--}21\text{ km/h}$) to instantly pin the device.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-074` (*Duplicate Location Callback Filtering, Asymmetric Deadband Separation & Stationary Pinning Entry Hysteresis*)
  * `REQ-TRK-074.1`: Sub-interval & duplicate callback filtering (`dt_ms < 250 || (rawDelta == 0.0 && dt_ms < 900)`).
  * `REQ-TRK-074.2`: Entry spatial bound separation (`rawDelta <= deadbandRadius` for entry vs `distFromAnchor > escapeRadius` for exit).
  * `REQ-TRK-074.3`: 3-tick stationary entry hysteresis (`STATIONARY_PINNING_HYSTERESIS_TICKS = 3`) with geometric speed fallback during transient ticks.
  * `REQ-TRK-074.4`: Movement streak reset (`rawDelta > deadbandRadius || rawSpeed > SPEED_ZERO_THRESHOLD`).
  * `REQ-TRK-074.5`: Startup fix (`mPrevLocation == null`) immediate anchor pinning preservation.
* **Test Mapping**: `TST-TRK-074` (`TST-TRK-074.1` through `TST-TRK-074.6` in `SpeedAndLocationDeviceStationaryJitterTest.kt`).

---

## 3. System Invariants & Preserved Behavior

1. **Start-Line Immediate Pinning**: First location fix (`mPrevLocation == null`) pins immediately with zero delay, suppressing startup GPS wander.
2. **True Standstill Guarantees**: Upon completing 3 stationary ticks at a stop light, anchor pins cleanly, speed clamps to 0.0 m/s, distance increments halt, and bearing locks (`REQ-TRK-048.1`..`REQ-TRK-048.3`).
3. **Teleportation Spike Invariance**: Multipath jumps exceeding $70.0\text{ m/s}$ remain clamped to `delta_distance = 0.0` (`REQ-TRK-041.1`).
4. **Thread Safety**: All state updates remain confined to synchronized methods and sensor dispatchers.

---

## 4. Proposed Architectural Changes

### Component 1: `SpeedAndLocationDevice.java`
* **Constants & Fields**:
  ```java
  public static final int STATIONARY_PINNING_HYSTERESIS_TICKS = 3;
  private int mStationaryHysteresisCount = 0;
  ```
* **Lifecycle Clears**:
  Reset `mStationaryHysteresisCount = 0` in `resetDeviceState()`, `shutDown()`, and `onAccumulatorsReset()`.
* **Duplicate / Sub-Interval Filter** (`onNewLocation()`):
  Immediately after accuracy gating, if `mPrevLocation != null`:
  ```java
  long dt_ms = location.getTime() - mPrevLocation.getTime();
  double rawDelta = mPrevLocation.distanceTo(location);
  if (dt_ms < 250 || (rawDelta == 0.0 && dt_ms < 900)) {
      return;
  }
  ```
* **Entry Hysteresis State Machine** (`onNewLocation()`):
  When `!mIsStationaryPinned`:
  * If `mPrevLocation == null && (isDopplerStationary || !hasSpeed)`: pin anchor immediately (`mIsStationaryPinned = true`, `mStationaryAnchor = location`, `delta_distance = 0.0`, `speed = 0.0`).
  * Else if candidate stationary: `(isDopplerStationary && rawDelta <= deadbandRadius) || (!hasSpeed && rawDelta <= deadbandRadius && mSpeed <= SPEED_ZERO_THRESHOLD)`:
    * `mStationaryHysteresisCount++`
    * If `mStationaryHysteresisCount >= STATIONARY_PINNING_HYSTERESIS_TICKS`: pin anchor (`mIsStationaryPinned = true`, `mStationaryAnchor = location`, `delta_distance = 0.0`, `speed = 0.0`, `mStationaryHysteresisCount = 0`).
    * Else (`count < 3`): treat as legitimate movement (`delta_distance = rawDelta`, compute `calcSpeed`, fall back to geometric speed).
  * Else (`rawDelta > deadbandRadius` or `rawSpeed > SPEED_ZERO_THRESHOLD`):
    * `mStationaryHysteresisCount = 0;` (reset candidate streak). Treat as legitimate movement.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Fields & Lifecycle Resets in `SpeedAndLocationDevice.java` (`REQ-TRK-074.3`)
* Add `STATIONARY_PINNING_HYSTERESIS_TICKS = 3` and `mStationaryHysteresisCount = 0`.
* Reset `mStationaryHysteresisCount = 0` on accumulators reset and shutdown.

### Step 2: Implement Duplicate Callback Guard in `SpeedAndLocationDevice.java` (`REQ-TRK-074.1`)
* In `onNewLocation()`, evaluate `dt_ms < 250 || (rawDelta == 0.0 && dt_ms < 900)` when `mPrevLocation != null` and return early.

### Step 3: Implement Asymmetric Deadband Separation & Entry Hysteresis (`REQ-TRK-074.2`, `REQ-TRK-074.3`, `REQ-TRK-074.4`)
* Replace single-sample `escapeRadius` check on unpinned branch with `deadbandRadius` candidate check and 3-tick counter.
* Fall back to geometric speed during ticks 1 and 2. Pin anchor on tick 3.
* Reset counter on movement resume.

### Step 4: Author Unit Tests in `SpeedAndLocationDeviceStationaryJitterTest.kt` (`TST-TRK-074.1` – `TST-TRK-074.5`)
* Implement tests for duplicate callback dropping, climbing resilience without pinning, 3-tick hysteresis progression, streak reset on motion, and immediate initial fix pinning.
* Update existing tests (`testStoppingAtRedLight` and `testStandstillJitter`) to account for 3-tick hysteresis.

### Step 5: Verification & Targeted Regression (`TST-TRK-074.6`)
* Command: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*"`
* Ensure 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted device tests and verify living documentation parity.
* **Rollback**: Branch isolation allows full discard (`git restore .`) without affecting `sprint/2026-46.2`.
