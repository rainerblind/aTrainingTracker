# Test Specification - ATT-1353: Fatal JNI SIGABRT in AltitudeFromPressureDevice.setAltitudeCorrection

**Ticket**: [ATT-1353](https://rainerblind.atlassian.net/browse/ATT-1353)  
**Sub-task**: [ATT-1362](https://rainerblind.atlassian.net/browse/ATT-1362)  
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

## 1. Overview & Verification Strategy

This test specification defines the test suite and verification criteria to prove that `AltitudeFromPressureDevice` safely and deterministically handles barometric pressure events, sensor initialization, and altitude correction without throwing `NullPointerException` or triggering JNI SIGABRT aborts (`ATT-1353`).

The test suite validates:
1. **Cold-Start Null Sensor Value Resilience**: Verifying that invoking `setAltitudeCorrection(double correctAltitude)` when `mAltitudeSensor.getValue()` is null safely resolves `mLastRawAltitude`, correctly calculates `mAltitudeCorrection = correctAltitude - mLastRawAltitude`, and dispatches the broadcast atomically without throwing a `NullPointerException`.
2. **Post-Warm-up Non-Null Calibration**: Verifying that when `mAltitudeSensor.getValue()` is already populated (e.g. baseline established after several sensor readings), `mAltitudeCorrection` evaluates to `correctAltitude - mAltitudeSensor.getValue().doubleValue()`.
3. **Double-Fault Graceful Degradation**: Verifying that if both `mAltitudeSensor.getValue()` is null and `mLastRawAltitude` is `Double.NaN`, the method logs an advisory warning and returns gracefully without mutating `mAltitudeCorrection` or throwing any exception.
4. **Broadcast Dispatch Integrity & Zero-Correction Suppression**: Verifying that non-zero corrections broadcast `ALTITUDE_CORRECTION_INTENT` with `ALTITUDE_CORRECTION_VALUE`, while a correction of `0.0` suppresses the broadcast.
5. **End-to-End Cold-Start Sensor Event Integration**: Verifying that on the first `onSensorChanged()` event with GPS coordinates and known reference location available, the device completes `initPressureSensor()` cleanly, updates `mAltitudeSensor` to the reference altitude, and dispatches the correction atomically with zero crashes.
6. **Full Suite Regression Cleanliness**: Verifying that `./gradlew testDebugUnitTest` executes cleanly with zero regressions.

---

## 2. Test Cases & Verification Matrix

### Test Case 1: `testSetAltitudeCorrection_whenSensorValueNull_fallsBackToLastRawAltitude` (Unit Test - `TST-CON-004.1`)
* **Goal**: Verify that when `mAltitudeSensor.getValue()` is null, `setAltitudeCorrection` uses `mLastRawAltitude` as fallback, computes `mAltitudeCorrection = correctAltitude - mLastRawAltitude`, dispatches the broadcast, and does NOT throw `NullPointerException`.
* **Preconditions**:
  - `AltitudeFromPressureDevice` initialized with mock context and sensor manager.
  - `mAltitudeSensor.getValue()` is `null`.
  - `mLastRawAltitude` set to `500.0`.
* **Action**:
  - Invoke `setAltitudeCorrection(520.0)` via reflection or package-private helper.
* **Expected Result**:
  - Zero exceptions thrown.
  - `mAltitudeCorrection` evaluates to `20.0` (`520.0 - 500.0`).
  - Broadcast `ALTITUDE_CORRECTION_INTENT` is captured with extra `ALTITUDE_CORRECTION_VALUE == 20.0`.

### Test Case 2: `testSetAltitudeCorrection_whenSensorValuePresent_calculatesDeltaFromCurrentValue` (Unit Test - `TST-CON-004.2`)
* **Goal**: Verify normal operation when `mAltitudeSensor.getValue()` is non-null.
* **Preconditions**:
  - `mAltitudeSensor.newValue(505.0)`.
  - `mLastRawAltitude` set to `500.0`.
* **Action**:
  - Invoke `setAltitudeCorrection(520.0)`.
* **Expected Result**:
  - `mAltitudeCorrection` evaluates to `15.0` (`520.0 - 505.0`).
  - Broadcast `ALTITUDE_CORRECTION_INTENT` is captured with extra `ALTITUDE_CORRECTION_VALUE == 15.0`.

### Test Case 3: `testSetAltitudeCorrection_whenBothSensorValueAndLastRawAltitudeUnavailable_handlesGracefully` (Unit Test - `TST-CON-004.3`)
* **Goal**: Verify double-fault safety when neither current sensor value nor last raw altitude is available.
* **Preconditions**:
  - `mAltitudeSensor.getValue()` is `null`.
  - `mLastRawAltitude` is `Double.NaN`.
* **Action**:
  - Invoke `setAltitudeCorrection(520.0)`.
* **Expected Result**:
  - Zero exceptions thrown.
  - Warning logged.
  - `mAltitudeCorrection` remains `0.0`.
  - Zero broadcasts dispatched.

### Test Case 4: `testSetAltitudeCorrection_whenCorrectionIsZero_suppressesBroadcast` (Unit Test - `TST-CON-004.4`)
* **Goal**: Verify that when target altitude equals current reading, no broadcast is emitted.
* **Preconditions**:
  - `mAltitudeSensor.newValue(520.0)`.
* **Action**:
  - Invoke `setAltitudeCorrection(520.0)`.
* **Expected Result**:
  - `mAltitudeCorrection == 0.0`.
  - Zero broadcasts sent.

### Test Case 5: `testOnSensorChanged_coldStartWithKnownLocation_initializesAndEmitsWithoutCrashing` (Integration Unit Test - `TST-CON-004.5`)
* **Goal**: Verify that the exact production crash sequence (first pressure event with known location available) executes cleanly without NPE or JNI SIGABRT.
* **Preconditions**:
  - Known location configured in `KnownLocationsDatabaseManager` (e.g. Lat/Lng at altitude 520.0m).
  - GPS sensors in `mMySensorManager` populated with matching Lat/Lng.
  - `mPressureSensorInitialized` is `false`.
* **Action**:
  - Dispatch a simulated `SensorEvent` with standard pressure corresponding to raw altitude 500.0m to `onSensorChanged()`.
* **Expected Result**:
  - `onSensorChanged()` completes without throwing `NullPointerException`.
  - `mPressureSensorInitialized` is `true`.
  - `mAltitudeSensor.getValue()` evaluates to `520.0`.
  - Broadcast `ALTITUDE_CORRECTION_INTENT` with value `20.0` is dispatched.

### Test Case 6: `testCleanRoomRegressionSuite` (Regression Verification - `TST-CON-004.6`)
* **Goal**: Ensure zero regressions across the entire project.
* **Action**:
  - Run `./gradlew testDebugUnitTest`.
* **Expected Result**:
  - `BUILD SUCCESSFUL`.
  - 100% of unit tests pass with zero failures.

---

## 3. Traceability Matrix

| Requirement | Test Spec ID | Test Case Name / Method | Target File | Type |
|:---|:---|:---|:---|:---|
| `REQ-CON-013` | `TST-CON-004.1` | `testSetAltitudeCorrection_whenSensorValueNull_fallsBackToLastRawAltitude` | `AltitudeFromPressureDeviceTest.kt` | Unit Test |
| `REQ-CON-013` | `TST-CON-004.2` | `testSetAltitudeCorrection_whenSensorValuePresent_calculatesDeltaFromCurrentValue` | `AltitudeFromPressureDeviceTest.kt` | Unit Test |
| `REQ-CON-013` | `TST-CON-004.3` | `testSetAltitudeCorrection_whenBothSensorValueAndLastRawAltitudeUnavailable_handlesGracefully` | `AltitudeFromPressureDeviceTest.kt` | Unit Test |
| `REQ-CON-013` | `TST-CON-004.4` | `testSetAltitudeCorrection_whenCorrectionIsZero_suppressesBroadcast` | `AltitudeFromPressureDeviceTest.kt` | Unit Test |
| `REQ-CON-013` | `TST-CON-004.5` | `testOnSensorChanged_coldStartWithKnownLocation_initializesAndEmitsWithoutCrashing` | `AltitudeFromPressureDeviceTest.kt` | Integration Unit Test |
| `REQ-CON-013` | `TST-CON-004.6` | Full Unit Test Suite (`./gradlew testDebugUnitTest`) | Entire Project | Regression Test |

---

## 4. Acceptance Criteria & Invariants Check

- [x] Requirement `REQ-CON-013` synchronized in `docs/requirements.md` and verified via `verify_requirement_governance.py`.
- [x] Test Spec `TST-CON-004` synchronized in `docs/tests.md`.
- [x] All 6 test cases formulated with explicit Preconditions, Actions, and Expected Results.
- [x] System Invariants explicitly preserved:
  - Downstream atomic shift in `TrackerService` (`REQ-FIL-004`) preserved.
  - Raw barometric altitude learning in `KnownLocationsDatabaseManager` (`REQ-DAT-007`) preserved.
  - Cold-start baseline protection (`REQ-CON-011`) preserved.
