# Root Cause Analysis - ATT-1353: Fatal JNI SIGABRT in AltitudeFromPressureDevice.setAltitudeCorrection

## 1. Defect Overview & Crash Telemetry

* **Issue Key**: ATT-1353
* **Requirement Mapping**: `REQ-CON-012` (Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch; extends `REQ-CON-006`, `REQ-FIL-004`, `REQ-CON-011`, `REQ-DAT-007`) in `docs/requirements.md`
* **Target Version**: `V4.9.38` (Sprint `2026-39.2`)
* **Crash Type**: Fatal Native Abort (`SIGABRT`) triggered by unhandled `NullPointerException` inside JNI `CallObjectMethodV`

### Crash Stacktrace
```text
09-24 19:19:44.063 28782 28782 F DEBUG   : Abort message: 'JNI DETECTED ERROR IN APPLICATION: JNI CallObjectMethodV called with pending exception java.lang.NullPointerException: Attempt to invoke virtual method 'double java.lang.Number.doubleValue()' on a null object reference
09-24 19:19:44.063 28782 28782 F DEBUG   :   at void com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice.setAltitudeCorrection(double) (AltitudeFromPressureDevice.java:150)
09-24 19:19:44.063 28782 28782 F DEBUG   :   at void com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice.initPressureSensor() (AltitudeFromPressureDevice.java:133)
09-24 19:19:44.063 28782 28782 F DEBUG   :   at void com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice.onSensorChanged(android.hardware.SensorEvent) (AltitudeFromPressureDevice.java:182)
09-24 19:19:44.063 28782 28782 F DEBUG   :   at void android.hardware.SystemSensorManager$SensorEventQueue.dispatchSensorEvent(int, float[], int, long) (SystemSensorManager.java:1101)
```

---

## 2. Forensic Root Cause Analysis (RCA)

### A. The Evolution of the Bug Across Commits
1. **Initial Safe State**:
   Historically, `AltitudeFromPressureDevice.onSensorChanged()` emitted the raw altitude reading to `mAltitudeSensor` *first*:
   ```java
   mLastRawAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]);
   mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection);
   if (!mPressureSensorInitialized) {
       initPressureSensor();
   }
   ```
   At this point, `mAltitudeSensor.getValue()` was always populated when `initPressureSensor()` was invoked from `onSensorChanged()`.
   Furthermore, `initPressureSensor()` explicitly guarded:
   ```java
   && mAltitudeSensor != null
   && mAltitudeSensor.getValue() != null
   ```

2. **Commit `308f9392655` (ATT-448 - Automated Altitude Reference Discovery)**:
   In commit `308f9392655`, the guard condition in `initPressureSensor()` was modified:
   ```diff
   -        if (mMySensorManager.getSensor(SensorType.LATITUDE) != null
   +        if (!Double.isNaN(mLastRawAltitude)
                    && mMySensorManager.getSensor(SensorType.LATITUDE) != null
                    && mMySensorManager.getSensor(SensorType.LONGITUDE) != null
                    && mMySensorManager.getSensor(SensorType.LATITUDE).getValue() != null
                    && mMySensorManager.getSensor(SensorType.LONGITUDE).getValue() != null
   -                && mAltitudeSensor != null
   -                && mAltitudeSensor.getValue() != null) {
   +                && mAltitudeSensor != null) {
   ```
   The check `mAltitudeSensor.getValue() != null` was removed. At the time of ATT-448, this change did not immediately crash because `onSensorChanged()` still called `mAltitudeSensor.newValue()` *before* `initPressureSensor()`.

3. **Commit `422ee3a2283` (ATT-508 - Barometric Cold-Start Baseline Protection)**:
   To satisfy requirement `REQ-CON-011` (preventing uncalibrated standard atmosphere values from entering active sessions prior to baseline calibration), commit `422ee3a2283` reordered `onSensorChanged()`:
   ```diff
            mLastRawAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]);
   -        mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection);
            if (!mPressureSensorInitialized) {
                initPressureSensor();
            }
   +        mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection);
   ```
   This created a critical latent race/initialization trap:
   - On the very first `SensorEvent`, `mAltitudeSensor.newValue()` has **never been called**.
   - Therefore, `mAltitudeSensor.getValue()` is **`null`**.
   - `initPressureSensor()` executes because `!Double.isNaN(mLastRawAltitude)` is true, GPS coordinates are known, and `mAltitudeSensor != null` is true.
   - If the workout starts near a known location (`myLocation != null`), line 133 executes:
     ```java
     setAltitudeCorrection(myLocation.altitude);
     ```
   - In `setAltitudeCorrection(double)` (line 150):
     ```java
     mAltitudeCorrection = correctAltitude - mAltitudeSensor.getValue().doubleValue();
     ```
   - Because `mAltitudeSensor.getValue()` is `null`, invoking `.doubleValue()` throws an immediate `NullPointerException`.
   - Because `onSensorChanged()` was invoked by native code via JNI (`SystemSensorManager$SensorEventQueue.dispatchSensorEvent`), the unhandled exception causes Android Runtime JNI to abort the process fatally with `SIGABRT`.

---

## 3. Impact Analysis & Scope Boundary

* **Affected Component**: `com.atrainingtracker.banalservice.devices.AltitudeFromPressureDevice`
* **Trigger Conditions**:
  - Device possesses a physical barometric pressure sensor.
  - GPS fix / location is already available when the pressure sensor produces its initial reading (e.g. starting a workout where location is already acquired).
  - The starting location matches a known location in `KnownLocationsDatabaseManager` (`myLocation != null`).
* **Consequences**:
  - Immediate application crash / native process termination (`SIGABRT`) on workout start.
* **Collateral Systems**:
  - BLE and ANT+ sensor pipelines: Untouched.
  - GPS location tracking: Untouched.
  - Database schema: Untouched.
  - UI presentation: Untouched.

---

## 4. Call Site Audit

| File | Line | Method / Scope | Impact |
| :--- | :--- | :--- | :--- |
| `AltitudeFromPressureDevice.java` | 150 | `setAltitudeCorrection(double)` | Primary defect site: unboxing null `mAltitudeSensor.getValue()`. |
| `AltitudeFromPressureDevice.java` | 133 | `initPressureSensor()` | Caller: invokes `setAltitudeCorrection(myLocation.altitude)` when `myLocation != null`. |
| `AltitudeFromPressureDevice.java` | 182 | `onSensorChanged(SensorEvent)` | Caller: invokes `initPressureSensor()` before `mAltitudeSensor.newValue(...)`. |
| `AltitudeFromPressureDevice.java` | 70 | `mGPSProviderEnabledReceiver.onReceive()` | Asynchronous trigger: invokes `initPressureSensor()` when GPS becomes available. |
| `TrackerService.java` | 221 | `mAltitudeCorrectionReceiver.onReceive()` | Consumer of `ALTITUDE_CORRECTION_INTENT`: shifts existing samples by `ALTITUDE_CORRECTION_VALUE`. |

---

## 5. Requirement Mapping & Alignment

* **Existing Mapped Requirements**:
  - `REQ-CON-006`: Support barometric pressure sensors for altitude (`AltitudeFromPressureDevice.java`).
  - `REQ-FIL-004`: Atomic Altitude Correction (`TrackerService.java`, `KnownLocationsDatabaseManager.java`).
  - `REQ-CON-011`: Barometric Cold-Start Baseline Protection (`AltitudeFromPressureDevice.java`).
  - `REQ-DAT-007`: Automated Altitude Reference Discovery (`KnownLocationsDatabaseManager.java`, `AltitudeFromPressureDevice.java`).
* **New Requirement to be Specified in Stage 2**:
  - `REQ-CON-012`: **Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch.**
    - The `AltitudeFromPressureDevice.setAltitudeCorrection` method SHALL guard against null `mAltitudeSensor.getValue()`.
    - If `mAltitudeSensor.getValue()` is null, the system SHALL calculate the altitude correction relative to `mLastRawAltitude`.
    - If both `mAltitudeSensor.getValue()` is null and `mLastRawAltitude` is `Double.NaN`, the system SHALL log a warning and return gracefully without mutating `mAltitudeCorrection` or throwing an exception.

---

## 6. System Invariants & Preserved Behavior

1. **Mathematical Invariant**:
   When `mAltitudeSensor.getValue()` is populated (e.g. when calibration occurs after several sensor readings have already been recorded), `mAltitudeCorrection` SHALL continue to evaluate to `correctAltitude - mAltitudeSensor.getValue().doubleValue()`.
2. **Cold-Start Fallback Invariant**:
   When `mAltitudeSensor.getValue()` is null and `mLastRawAltitude` is valid, `mAltitudeCorrection` SHALL evaluate to `correctAltitude - mLastRawAltitude`.
   The subsequent assignment in `initPressureSensor()` (`mAltitudeSensor.newValue(myLocation.altitude)`) and in `onSensorChanged()` (`mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection)`) will evaluate consistently to `myLocation.altitude`.
3. **Broadcast & Atomic Shift Invariant**:
   The broadcast of `ALTITUDE_CORRECTION_INTENT` with `ALTITUDE_CORRECTION_VALUE` MUST remain intact for consumption by `TrackerService` (`REQ-FIL-004`).
4. **Learning Loop Invariant**:
   `KnownLocationsDatabaseManager.getInstance(mContext).learnLocation(...)` MUST continue to receive the uncorrected `mLastRawAltitude` as established in `ATT-448` (`REQ-DAT-007`).

---

## 7. Remediation Strategy

1. **Defensive Value Resolution in `setAltitudeCorrection`**:
   ```java
   private void setAltitudeCorrection(double correctAltitude) {
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
           Intent intent = new Intent(ALTITUDE_CORRECTION_INTENT)
                   .setPackage(mContext.getPackageName())
                   .putExtra(ALTITUDE_CORRECTION_VALUE, mAltitudeCorrection);
           mContext.sendBroadcast(intent);
       }
   }
   ```
2. **Automated Unit Testing (`TST-CON-003`)**:
   Create a comprehensive unit test suite in `AltitudeFromPressureDeviceTest.kt` verifying:
   - Initial `SensorEvent` with known location and null sensor value computes correct correction and does not throw NPE.
   - Subsequent calibration when sensor value is non-null computes delta correction accurately.
   - Invocations when both sensor value and `mLastRawAltitude` are unavailable handle the edge case gracefully without throwing.

---

## 8. Risk Assessment

* **Risk Rating**: `LOW`
* **Justification**: The change is strictly localized to defensive null-handling and fallback computation within `AltitudeFromPressureDevice.java`. No database schemas, broadcast actions, or threading architectures are altered.
