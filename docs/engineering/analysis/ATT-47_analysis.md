# Stage 1 Analysis: ATT-47 - Indoor GPS suppression & external sensor / IMU rest-variance coupling for stationary coordinate pinning

**Ticket**: [ATT-47](https://rainerblind.atlassian.net/browse/ATT-47)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Branch**: `feat/ATT-47`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
During indoor/trainer workouts and rest stops (e.g. café/bakery stops), `SpeedAndLocationDevice.java` currently evaluates only GNSS Doppler velocity and spatial displacement in isolation (`REQ-TRK-048`, `REQ-TRK-056`, `REQ-TRK-074`). Two critical real-world failure modes remain:
1. **Indoor/Trainer GPS Wander**: In indoor sessions, multipath jumps easily exceed $1.5 \times \text{accuracy}$, repeatedly unpinning `mStationaryAnchor` and recording disfigured spiderweb polylines and spurious GPS distance creep while external trainer sensors are active.
2. **Rest Stops with Stationary Wheel / Phone at Rest**: When a paired ANT+/BLE wheel speed sensor (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, `BIKE_POWER`) reports $0.0\text{ m/s}$, or when phone IMU acceleration norm variance is at the static rest floor, indoor multipath spikes still trigger false unpinning and distance accumulation.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  1. **Isolated GNSS Gating in `SpeedAndLocationDevice.onNewLocation()`**: Lines 358–393 evaluate unpinning solely against `rawSpeed > SPEED_ZERO_THRESHOLD` and `distFromAnchor > escapeRadius`. The device has zero visibility into: (a) indoor mode flags, (b) external speed sensor readings from `mMySensorManager`, or (c) low-power IMU rest variance.
  2. **False Distance Accumulation**: Unpinning indoors allows `delta_distance` to increment `mDistanceSensor` and `mLapDistanceSensor` (lines 453–456), polluting total workout distance even when paired trainer/wheel sensors provide ground-truth speed/distance.
  3. **Hypothesis Confirmation**: Verifying against live `sprint/2026-46.6` shows `SpeedAndLocationDevice` already possesses `mMySensorManager` (inherited from `MyDevice`) and can inspect `mMySensorManager.getSensorForDeviceType(SensorType.SPEED_mps, ...)`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. **Indoor GPS Suppression**: When indoor mode is active (`mIsIndoorMode == true`), pin to initial coordinate anchor, suppress GPS coordinate drift emission, clamp GPS speed to $0.0\text{ m/s}$, and suppress GPS distance increments while allowing external sensors to record normally.
  2. **External Sensor Standstill Coupling**: If a connected external speed sensor (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, `BIKE_POWER`) reports $0.0\text{ m/s}$ with update timestamp $\le 5\text{ s}$, lock pinning to `mStationaryAnchor` and block unpinning regardless of GNSS multipath.
  3. **Accelerometer Rest-Variance Gating**: Compute rolling variance of low-power accelerometer norm $||a||$ over 1.5–2.0 s; when $\sigma^2 \le 0.05\text{ (m/s}^2)^2$ (static floor), reinforce stationary pinning against GNSS spikes; release within $\le 1\text{ s}$ upon motion.
  4. **Fallback Parity**: When external sensors/accelerometer are absent (e.g. mock GPS replay), existing GNSS/Doppler behavior operates identically with zero regressions.
* **Out-of-Scope Non-Goals**:
  * No 3D accelerometer double-integration ($\iint a\,dt^2$ is strictly prohibited by Epic ATT-166 scope).
  * No changes to barometric elevation (`AltitudeFromPressureDevice`) or topographic DEM filters.
  * No database schema migrations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-TRK-077`, refining `REQ-TRK-048` (`ATT-4850`), `REQ-TRK-056` (`ATT-6256`), and `REQ-TRK-074` (`ATT-6682`) in `SpeedAndLocationDevice.java`.
* **Historical Origin & Commit Trace**: `REQ-TRK-048` added Doppler zero velocity pinning; `REQ-TRK-056` added escape radius gating; `REQ-TRK-074` added 3-tick entry hysteresis.
* **Root Reason for Existing Formulation**: Prior rules handled GPS-only dynamics at traffic lights and outdoor riding. They did not consider indoor trainer workouts or café stops where external hardware or IMUs provide conclusive evidence of zero velocity.
* **Preservation of Core Invariants**: Outdoor movement unpinning, 3-tick entry hysteresis, start-line pinning (`mPrevLocation == null`), and teleportation spike filtering ($\le 70\text{ m/s}$, `REQ-TRK-041.1`) remain 100% intact.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-TRK-077` in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `SpeedAndLocationDevice.java`, `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_GoogleFused.java`.
* **Verification Impact & Test Surface**: `SpeedAndLocationDeviceStationaryJitterTest.kt`, new targeted test class `SpeedAndLocationDeviceIndoorAndRestCouplingTest.kt`, and `tools/replay_workout.py`.
* **Risk Assessment & Invariants**: Low risk. All gating degrades gracefully to existing standalone GPS logic if external sensors or accelerometer hardware are unavailable.

---

## 6. Architectural Strategy & High-Level Solution
1. **State & Control**: Add `protected boolean mIsIndoorMode = false` with public getter/setter in `SpeedAndLocationDevice`.
2. **External Sensor Standstill Check**: Helper `isExternalSpeedSensorStationary()` checks `mMySensorManager` for active speed sensors (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, `BIKE_POWER`) updated within 5 s reporting $\le 0.1\text{ m/s}$.
3. **IMU Rest-Variance Gate**: Low-power `SensorEventListener` for `Sensor.TYPE_ACCELEROMETER` maintains a rolling ring buffer of $||a||$ over 1.5–2.0 s. Helper `isAccelerometerAtRest()` returns true when $\sigma^2 \le 0.05\text{ (m/s}^2)^2$.
4. **Coupled Pinning Decision**: In `onNewLocation()`, if `mIsIndoorMode`, `isExternalSpeedSensorStationary()`, or `isAccelerometerAtRest()` is true, force `shouldUnpin = false` and suppress distance accumulation.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regressions in standalone GPS replay and existing unit tests.
  2. Immediate unpinning ($\le 1\text{ s}$) when external speed or IMU physical motion resumes.
  3. Strict POSIX build serialization and thread-safe sensor state dispatch.
* **Risk Rating**: **LOW** — Clean additive decoupling with neutral default fallbacks when hardware sensors are absent.
