# Implementation Plan - ATT-1353: Fatal JNI SIGABRT in AltitudeFromPressureDevice.setAltitudeCorrection

**Ticket**: [ATT-1353](https://rainerblind.atlassian.net/browse/ATT-1353)  
**Sub-task**: [ATT-1363](https://rainerblind.atlassian.net/browse/ATT-1363)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**:
* `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java`
* `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDeviceTest.kt`
**Requirement**: `REQ-CON-013` (Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch)  
**Test Spec ID**: `TST-CON-004`  
**Branch**: `bugfix/ATT-1353`  

---

## 1. Technical Architecture & Modifications

### 1.1 Defensive Value Resolution in `AltitudeFromPressureDevice.setAltitudeCorrection`
To permanently eliminate unhandled `NullPointerException` crashes during JNI callback dispatch on startup, `setAltitudeCorrection(double correctAltitude)` is refactored to defensively resolve the current altitude baseline:

```java
    /**
     * set the field mAltitudeCorrection
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void setAltitudeCorrection(double correctAltitude) {
        if (DEBUG) Log.d(TAG, "setAltitudeCorrection");

        double currentAltitude;
        if (mAltitudeSensor != null && mAltitudeSensor.getValue() != null) {
            currentAltitude = mAltitudeSensor.getValue().doubleValue();
        } else if (!Double.isNaN(mLastRawAltitude)) {
            currentAltitude = mLastRawAltitude;
        } else {
            Log.w(TAG, "Cannot set altitude correction: neither current sensor value nor last raw altitude is available.");
            return;
        }

        mAltitudeCorrection = correctAltitude - currentAltitude;

        if (mAltitudeCorrection != 0.0) {
            // also send broadcast to inform the others (like a tracker) of this change such that they can update all previous samples accordingly!
            Intent intent = new Intent(ALTITUDE_CORRECTION_INTENT)
                    .setPackage(mContext.getPackageName())
                    .putExtra(ALTITUDE_CORRECTION_VALUE, mAltitudeCorrection);
            mContext.sendBroadcast(intent);
        }
    }
```

### 1.2 Test Hooks & Package-Private Accessors
To enable clean, deterministic, and reflection-free unit testing without altering production semantics:
- Make `setAltitudeCorrection(double)` package-private and annotate with `@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)`.
- Expose `@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) double getAltitudeCorrection()` returning `mAltitudeCorrection`.
- Expose `@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) void setLastRawAltitude(double rawAltitude)` updating `mLastRawAltitude`.
- Expose `@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) MySensor<Number> getAltitudeSensor()` returning `mAltitudeSensor`.

### 1.3 Automated Unit Test Suite (`AltitudeFromPressureDeviceTest.kt`)
Implement comprehensive test coverage in `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDeviceTest.kt` verifying:
1. `testSetAltitudeCorrection_whenSensorValueNull_fallsBackToLastRawAltitude` (`TST-CON-004.1`):
   - With `mAltitudeSensor.getValue() == null` and `mLastRawAltitude == 500.0`, calling `setAltitudeCorrection(520.0)` sets `mAltitudeCorrection == 20.0`, broadcasts 20.0, and throws 0 exceptions.
2. `testSetAltitudeCorrection_whenSensorValuePresent_calculatesDeltaFromCurrentValue` (`TST-CON-004.2`):
   - With `mAltitudeSensor.getValue() == 505.0`, calling `setAltitudeCorrection(520.0)` sets `mAltitudeCorrection == 15.0` and broadcasts 15.0.
3. `testSetAltitudeCorrection_whenBothSensorValueAndLastRawAltitudeUnavailable_handlesGracefully` (`TST-CON-004.3`):
   - With `mAltitudeSensor.getValue() == null` and `mLastRawAltitude` NaN, calling `setAltitudeCorrection(520.0)` leaves `mAltitudeCorrection == 0.0` and logs warning without throwing.
4. `testSetAltitudeCorrection_whenCorrectionIsZero_suppressesBroadcast` (`TST-CON-004.4`):
   - When target altitude equals current reading, no broadcast is dispatched.
5. `testOnSensorChanged_coldStartWithKnownLocation_initializesAndEmitsWithoutCrashing` (`TST-CON-004.5`):
   - Simulates the exact production crash sequence on cold start with GPS fix and known location.
6. Full regression suite verification (`TST-CON-004.6`).

---

## 2. Impact Analysis & Mapped Requirements Audit

### 2.1 Mapped Requirements Cross-Check
The following requirements from `docs/requirements.md` map to `AltitudeFromPressureDevice.java`:
* `REQ-CON-006` (Support barometric pressure sensors for altitude): **Preserved**. Barometric conversion via `SensorManager.getAltitude` remains unchanged.
* `REQ-FIL-004` (Atomic Altitude Correction): **Preserved**. Broadcast of `ALTITUDE_CORRECTION_INTENT` with `ALTITUDE_CORRECTION_VALUE` is strictly maintained.
* `REQ-CON-010` (Internal Sensor Pairing): **Preserved**. Activation and pairing state handling is unchanged.
* `REQ-CON-011` (Barometric Cold-Start Baseline Protection): **Preserved**. Execution ordering in `onSensorChanged()` is preserved.
* `REQ-DAT-007` (Automated Altitude Reference Discovery): **Preserved**. `learnLocation` receives uncorrected `mLastRawAltitude` as established in ATT-448.
* `REQ-UI-118` (Internal Sensor Transparency): **Preserved**. Device ID and naming remain untouched.
* `REQ-CON-013` (Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch): **Implemented**.

### 2.2 Subsystem Side-Effect Evaluation
* **Battery & Power Consumption**: Zero impact. Computation is instantaneous in-memory arithmetic.
* **Threading & Concurrency**: Executed synchronously on the sensor event dispatcher thread; local reference capture eliminates race hazards.
* **Data Schemas & Storage**: Zero schema modifications.
* **Component Interfaces / IPC**: Broadcast contract (`ALTITUDE_CORRECTION_INTENT`, `ALTITUDE_CORRECTION_VALUE`) is 100% backward compatible.

---

## 3. Step-by-Step Implementation Sequence

1. **Gate Check**: Verify sub-task `ATT-1363` reaches `Erledigt` in Jira via `python3 tools/jira_util.py check-gate ATT-1363`.
2. **Refactor `AltitudeFromPressureDevice.java`**:
   - Update `setAltitudeCorrection(double)` with null guard, fallback to `mLastRawAltitude`, and double-fault graceful return.
   - Add `@VisibleForTesting` accessors for testing.
3. **Construct Unit Test Suite**:
   - Implement `AltitudeFromPressureDeviceTest.kt` in `app/src/test/java/com/atrainingtracker/banalservice/devices/` covering all 6 test cases in `TST-CON-004`.
4. **Verification & Walkthrough**:
   - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.AltitudeFromPressureDeviceTest"`.
   - Run `./gradlew testDebugUnitTest` across all modules.
   - Create walkthrough document `docs/engineering/walkthroughs/ATT-1353_walkthrough.md`.
