# Stage 1 Analysis: ATT-2616 - When we don't receive a location update after some time, the speed must go towards zero

**Ticket**: [ATT-2616](https://atrainingtracker.atlassian.net/browse/ATT-2616)  
**Sub-task**: [ATT-2755](https://atrainingtracker.atlassian.net/browse/ATT-2755) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Sensor Management*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2616`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During live workout tracking (e.g. cycling or running), when an athlete comes to a complete halt (such as stopping at a red traffic light, pause in a group ride, or rest stop) or enters a GPS shadow (tunnel, underpass, dense forest, or indoor transition), the Android `LocationManager` and GNSS chipset frequently stop emitting `Location` updates entirely or the update frequency collapses.

In `SpeedAndLocationDevice.java`, the speed and pace sensors (`mSpeedSensor` of type `SensorType.SPEED_mps` and `mPaceSensor` of type `SensorType.PACE_spm`) are updated exclusively within `onNewLocation(Location location)`.
Because no timeout, watchdog, or decay mechanism exists, when location updates cease:
1. `mSpeedSensor` and `mSpeed` remain frozen indefinitely at the last recorded speed (e.g. 25.0 km/h or 7.0 m/s).
2. `mPaceSensor` remains frozen indefinitely at `1 / mSpeed` (e.g. 04:00 min/km).
3. The cockpit tracking screen, smartwatch displays (Pebble/Wear), and downstream sensor subscribers display the athlete moving at 25 km/h while stationary.
4. If the athlete stays stopped for an extended period, live average metrics and workout summaries become distorted.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Code Inspection: `SpeedAndLocationDevice.java`
Inspection of `SpeedAndLocationDevice.java` lines 129–175 reveals:
```java
public void onNewLocation(Location location) {
    if (DEBUG) Log.i(TAG, "onNewLocation()");

    if (location != null) {
        double accuracyThreshold = SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(mContext);
        if (location.getAccuracy() <= accuracyThreshold) {
            LocationAvailable();

            // ... updates longitude, latitude, accuracy, bearing, altitude ...

            double speed = location.getSpeed();
            mSpeed = (mSpeed + speed) / 2;
            mSpeedSensor.newValue(mSpeed);
            mPaceSensor.newValue(1 / mSpeed);

            if (mPrevLocation != null) {
                double delta_distance = mPrevLocation.distanceTo(location);
                mDistanceSensor.increment(delta_distance);
                mLapDistanceSensor.increment(delta_distance);
            }

            mPrevLocation = location;
            // ... broadcasts NEW_LOCATION_INTENT ...
        }
    }
}
```

### 2.2 Forensic Findings
1. **Passive Event-Driven Updating**: `mSpeedSensor` and `mPaceSensor` are only updated when `onNewLocation()` is invoked with a location that satisfies `getAccuracy() <= accuracyThreshold`.
2. **Missing Watchdog / Inactivity Handler**: There is no scheduled task, timer, or handler checking if the elapsed time since the last valid location has exceeded a reasonable threshold.
3. **No Speed Decay**: When the device stops receiving location updates, `mSpeed` is never decayed or zeroed out.
4. **Pace Calculation Vulnerability**: Notice `mPaceSensor.newValue(1 / mSpeed)`. If `mSpeed` reaches `0.0`, `1 / 0.0` yields `Double.POSITIVE_INFINITY` in Java IEEE-754. In `addSensors()`, `mPaceSensor` is initialized with `null`, which cleanly formats as `'--:--'` in the UI. When speed decays to zero, `mPaceSensor` should emit `null`.
5. **Thread Safety & Lifecycle**: `SpeedAndLocationDevice` extends `MyDevice`. When `shutDown()` is called on device teardown, any scheduled watchdog or decay loop must be cancelled cleanly to prevent memory or coroutine leaks.

---

## 3. Proposed Architectural Solution

### 3.1 Decay & Timeout Formulation (Option A - Conservative Timeout)
1. **Watchdog Handler / Timer**:
   - Maintain a lightweight watchdog mechanism in `SpeedAndLocationDevice` tracking elapsed time since the last valid location update (`mLastLocationTimestampMs`).
   - Use an Android `Handler` associated with `Looper.getMainLooper()` (or a dedicated scheduled timer / coroutine job) with a 1000ms periodic tick.
2. **Watchdog Parameters**:
   - `LOCATION_TIMEOUT_MS = 5000L` (5.0 seconds).
   - If `currentTime - mLastLocationTimestampMs >= LOCATION_TIMEOUT_MS`, speed decay is triggered.
   - Decay step: Every 1.0 second after timeout, if no new location arrives, decay `mSpeed`:
     - Halving decay: `mSpeed = mSpeed * 0.5`
     - When `mSpeed < 0.1` m/s (or after ~3 decay steps, at 8s), clamp `mSpeed = 0.0`.
   - Update `mSpeedSensor.newValue(mSpeed)`.
3. **Pace Resolution**:
   - When `mSpeed <= 0.1` or `mSpeed == 0.0`, publish `mPaceSensor.newValue(null)`.
   - When `mSpeed > 0.1`, publish `mPaceSensor.newValue(1.0 / mSpeed)`.
4. **Immediate Recovery on New Fix**:
   - As soon as a fresh valid `Location` arrives in `onNewLocation()`, update `mLastLocationTimestampMs = SystemClock.elapsedRealtime()`.
   - Normal speed calculation `mSpeed = (mSpeed + speed) / 2` immediately resumes.
5. **Lifecycle Teardown**:
   - In `SpeedAndLocationDevice.shutDown()`, immediately remove callbacks and stop the watchdog handler.

---

## 4. Chesterton's Fence Archaeology & Invariant Preservation

### 4.1 Chesterton's Fence Audit
* **Requirement ID**: Net-new requirement `REQ-TRK-013`.
* **Related Requirements**:
  - `REQ-SET-073` / `REQ-SET-074`: GPS accuracy threshold checking via `SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(mContext)`.
  - `REQ-PRI-004`: Precise location gating for location device instantiation.
  - `REQ-STB-004`: Safe location provider registration and exception shielding.
* **Preserved Invariants**:
  - Distance accumulation (`mDistanceSensor`, `mLapDistanceSensor`) MUST NOT accumulate phantom distance during decay.
  - Start location (`mStartLocation`), previous location (`mPrevLocation`), and line distance (`mLineDistanceSensor`) MUST NOT be altered by decay ticks.
  - Subclasses `SpeedAndLocationDevice_GPS`, `SpeedAndLocationDevice_Network`, and `SpeedAndLocationDevice_GoogleFused` inherit functionality transparently without duplicate implementations.
  - Sensor broadcast intents (`NEW_LOCATION_INTENT`) are emitted ONLY on genuine location fixes, NOT on watchdog decay ticks.

---

## 5. User Scope Grounding (In-Scope vs. Out-of-Scope)

### 5.1 In-Scope
* Implement location watchdog and gradual speed decay in `SpeedAndLocationDevice.java`.
* Emit decayed speed values to `mSpeedSensor` and reset `mPaceSensor` to `null` when speed drops below threshold.
* Reset/restore active speed immediately upon receiving a valid `Location` update.
* Safe cancellation of watchdog callbacks during `shutDown()`.
* Comprehensive unit tests in `SpeedAndLocationDeviceDecayTest.kt` verifying timeout, progressive decay steps, pace nullification, recovery on new fix, and clean shutdown.

### 5.2 Out-of-Scope
* Modifying hardware BLE/ANT+ speed sensor devices (`ANTBikeSpeedDevice`, `BTLEBikeDevice`).
* Modifying `VerticalSpeedAndSlopeDevice.java` (handled separately under `REQ-FIL-012`).
* Changing GPS accuracy threshold settings or DataStore schemas.

---

## 6. Requirement & Test Case Formulation

### 6.1 Requirement Specification: `REQ-TRK-013`
* **Title**: GPS Speed Inactivity Watchdog, Gradual Speed Decay & Pace Nullification Architecture.
* **Specification**: When location updates cease during active GPS tracking:
  1. The system SHALL monitor elapsed time since the last valid `Location` update.
  2. If no valid location update is received within 5.0 seconds (`LOCATION_TIMEOUT_MS = 5000L`), speed SHALL begin decaying towards `0.0 m/s`.
  3. Speed decay SHALL occur at 1.0-second intervals (halving current speed `mSpeed * 0.5`).
  4. When speed drops below 0.1 m/s, it SHALL be clamped to `0.0 m/s`.
  5. `mPaceSensor` SHALL emit `null` whenever speed decays to 0.0 or below 0.1 m/s.
  6. Upon arrival of a new valid location, live speed evaluation SHALL resume immediately and the watchdog timer SHALL reset.
  7. On `shutDown()`, all scheduled watchdog tasks SHALL be terminated.

### 6.2 Test Specification: `TST-TRK-005`
* **Title**: GPS Speed Inactivity Watchdog, Progressive Decay & Recovery Verification.
* **Test Scope**:
  1. Verify speed remains unchanged during normal continuous location stream (< 5s intervals).
  2. Verify speed begins decaying after 5 seconds of location silence.
  3. Verify progressive decay steps (halving every second) down to 0.0 m/s.
  4. Verify pace sensor emits `null` when decayed to 0.0 m/s.
  5. Verify immediate speed recovery upon receiving a valid location fix after decay.
  6. Verify clean cancellation of watchdog upon `shutDown()`.

---

## 7. Verification & Definition of Done

* Full clean-room test execution (`./gradlew testDebugUnitTest --continue`) passing with 100% success rate and 0 regressions.
* Requirement governance script `python3 tools/verify_requirement_governance.py` passes cleanly.
