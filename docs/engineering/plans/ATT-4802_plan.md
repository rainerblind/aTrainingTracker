# Stage 3: Implementation Plan - ATT-4802: Speed toggles periodically between plausible value and near-zero on 7-8s location update intervals

**Ticket**: [ATT-4802](https://rainerblind.atlassian.net/browse/ATT-4802)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4802`  
**Requirement Mapping**: `REQ-TRK-013` (GPS Speed Inactivity Watchdog, Gradual Speed Decay & Pace Nullification Architecture)  
**Test Mapping**: `TST-TRK-005` (GPS Speed Inactivity Watchdog, Progressive Decay & Recovery Verification)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-24  

---

## 1. Problem Description & Background
When location fixes arrive on 7–8s intervals (Wi-Fi/Cell Fused provider or degraded GNSS), `SpeedAndLocationDevice`'s 5s watchdog (`INACTIVITY_TIMEOUT_MS = 5000L`) triggers premature speed decay ticks before the next fix. Upon fix arrival, `mSpeed = (mSpeed + speed) / 2` averages incoming velocity with the decayed artifact, producing a periodic 7–8s sawtooth oscillating between ~16 km/h and ~0 km/h.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-013` (GPS Speed Inactivity Watchdog, Gradual Speed Decay & Pace Nullification Architecture)
* **Test Mapping**: `TST-TRK-005` (GPS Speed Inactivity Watchdog, Progressive Decay & Recovery Verification)
  - `TST-TRK-005.1`: 10s watchdog timeout and progressive exponential decay to 0.0 m/s
  - `TST-TRK-005.2`: 7–8s cadence immunity (no premature decay during active motion)
  - `TST-TRK-005.3`: Decoupled recovery (`mSpeed = speed` without averaging against decayed artifact)
  - `TST-TRK-005.4`: Pace nullification and zero phantom distance accumulation
  - `TST-TRK-005.5`: Clean-room test suite regression execution

---

## 3. System Invariants & Preserved Behavior
1. **True Stop Decay**: Complete stops or GPS loss still decay speed exponentially to 0.0 m/s after 10s (`REQ-TRK-013`).
2. **Zero Phantom Distance**: Watchdog decay ticks never increment distance accumulators (`mDistanceSensor`, `mLapDistanceSensor`).
3. **Pace Nullification**: Pace emits `null` (cockpit `'--:--'`) when speed $\le 0.1\text{ m/s}$ without division-by-zero.
4. **Clean Provider Teardown**: Lingering ADB mock providers are prevented on replay EOF; teardown releases all callbacks.

---

## 4. Proposed Architectural Changes
* **`SpeedAndLocationDevice.java`**: Update `INACTIVITY_TIMEOUT_MS = 10000L`. Introduce `mIsDecaying` state flag. In `onNewLocation()`, assign `mSpeed = speed` directly when `mIsDecaying == true`, `mPrevLocation == null`, or $\Delta t \ge 3000\text{ ms}$; clear `mIsDecaying = false`.
* **`SpeedAndLocationDevice_GoogleFused.java`**: Set `mLocationRequest.setFastestInterval(SAMPLING_TIME)` (1000 ms).
* **`tools/replay_workout.py`**: Guard `_worker_loop` against `release_providers()` with `threading.Lock`; suppress location queuing on `reached_eof`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Watchdog Timeout & Decouple Decay Recovery (`SpeedAndLocationDevice.java`)
* Set `INACTIVITY_TIMEOUT_MS = 10000L`. Add `mIsDecaying` flag toggled in `triggerDecayTick()` and cleared in `onNewLocation()`.
* Bypass `(mSpeed + speed) / 2` when `mIsDecaying`, `mPrevLocation == null`, or `dt_ms >= 3000`.

### Step 2: Configure Fastest Update Interval (`SpeedAndLocationDevice_GoogleFused.java`)
* Add `mLocationRequest.setFastestInterval(SAMPLING_TIME)` in constructor.

### Step 3: Synchronize Mock GPS Provider Cleanup (`tools/replay_workout.py`)
* Add mutex lock between `_worker_loop` and `release_providers()`; suppress `on_update` at `reached_eof`.

### Step 4: Unit Test Updates & Multi-Second Cadence Coverage (`SpeedAndLocationDeviceDecayTest.kt`)
* Update timeout assertions for 10000L. Add tests for 7–8s cadence immunity and decoupled speed recovery.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDeviceDecayTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*"` and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Atomic git commit isolation on branch `feature/ATT-4802` allows clean revert if regression occurs.
