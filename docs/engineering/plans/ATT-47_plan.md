# Stage 3: Implementation Plan - ATT-47: Indoor GPS suppression & external sensor / IMU rest-variance coupling for stationary coordinate pinning

**Ticket**: [ATT-47](https://rainerblind.atlassian.net/browse/ATT-47)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-TRK-077` (`REQ-TRK-077.1`..`REQ-TRK-077.4`)  
**Test Mapping**: `TST-TRK-077` (`TST-TRK-077.1`..`TST-TRK-077.5`)  
**Branch**: `feat/ATT-47`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
GNSS Doppler and coordinate displacement gating in `SpeedAndLocationDevice.java` (`REQ-TRK-048`, `REQ-TRK-056`, `REQ-TRK-074`) evaluate satellite signals in isolation. During indoor workouts and café rest stops, multipath jumps exceeding $1.5 \times \text{accuracy}$ cause false unpinning, recording erratic spiderweb polylines and accumulating spurious GPS distance. This plan couples stationary pinning to indoor mode flags, active connected external speed sensors reporting standstill ($0.0\text{ m/s}$), and low-power accelerometer norm rest variance ($\sigma^2 \le 0.05\text{ (m/s}^2)^2$).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-077` (*Indoor GPS Suppression, External Speed Sensor Coupling & IMU Rest-Variance Stationary Pinning*)
* **Test Mapping**: `TST-TRK-077` (*Unit & Regression Verification in SpeedAndLocationDeviceIndoorAndRestCouplingTest.kt*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Standalone GPS Regressions**: All existing unit and replay tests (`SpeedAndLocationDeviceStationaryJitterTest.kt`, `tools/replay_workout.py`) MUST pass identically when external sensors or accelerometer are absent.
2. **Immediate Movement Unpinning**: Standstill locks disengage immediately ($\le 1.0\text{ s}$) upon external speed $> 0.1\text{ m/s}$, external sensor staleness ($> 5\text{ s}$), or IMU motion variance ($\sigma^2 > 0.05$).
3. **No 3D Accelerometer Integration**: Raw accelerometer is strictly utilized for scalar norm variance computation; no double-integration into 2D/3D displacement.
4. **Thread Safety**: Sensor callbacks and state mutations execute safely on service handler and lock monitors.

---

## 4. Proposed Architectural Changes

### Component 1: `SpeedAndLocationDevice.java`
* **Indoor Mode State**: Add `protected volatile boolean mIsIndoorMode = false` with thread-safe `setIndoorMode(boolean)` and `isIndoorMode()`. In `onNewLocation()`, pin to anchor, clamp speed to $0.0\text{ m/s}$, and zero `delta_distance` when active.
* **External Sensor Coupling**: Implement `isExternalSpeedSensorStationary()` querying `mMySensorManager` for `BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, `BIKE_POWER`. Returns true if fresh ($\le 5000\text{ ms}$) and speed $\le 0.1\text{ m/s}$.
* **IMU Rest-Variance Gate**: Register `Sensor.TYPE_ACCELEROMETER` at `SENSOR_DELAY_UI`. Buffer norm $||a|| = \sqrt{a_x^2 + a_y^2 + a_z^2}$ over $1.5\text{--}2.0\text{ s}$. Helper `isAccelerometerAtRest()` returns true when $\sigma^2 \le 0.05\text{ (m/s}^2)^2$.
* **Pinning Decision Coupling**: In `onNewLocation()`, block unpinning (`shouldUnpin = false`) while stationary anchor is held if indoor mode, external sensor standstill, or IMU rest variance is detected.

### Component 2: Hardware Sensor Lifecycle & Teardown
* Safely register accelerometer listener in `SpeedAndLocationDevice` when location updates activate; cleanly unregister in `shutDown()` and `LocationUnavailable()` to prevent battery drain.

### UI Consistency (Rule 23)
* No UI changes. Pure sensor driver logic and telemetry filtering.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Indoor Mode State & External Sensor Standstill Inspection
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java`
* Add `mIsIndoorMode`, `setIndoorMode(boolean)`, `isIndoorMode()`, and `isExternalSpeedSensorStationary()`.

### Step 2: Implement Low-Power Accelerometer Rest-Variance Engine
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java`
* Implement `SensorEventListener` for `Sensor.TYPE_ACCELEROMETER`, rolling norm window, and `isAccelerometerAtRest()`.

### Step 3: Wire Gating Decisions in `onNewLocation()` & Lifecycle Cleanup
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java`
* Gate `shouldUnpin` and `delta_distance` on indoor mode, external standstill, and IMU rest floor. Clean up listeners in `shutDown()`.

### Step 4: Author Comprehensive Unit Tests (`TST-TRK-077`)
* Files: `app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDeviceIndoorAndRestCouplingTest.kt`
* Verify indoor suppression, external speed coupling, rest-variance gating, staleness release, and mock fallback.

### Step 5: Execute Tier 1 Targeted Package Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run `SpeedAndLocationDeviceIndoorAndRestCouplingTest` and `SpeedAndLocationDeviceStationaryJitterTest`.
* **Rollback**: Clean git branch revert on `feat/ATT-47` leaves `sprint/2026-46.6` untouched.
