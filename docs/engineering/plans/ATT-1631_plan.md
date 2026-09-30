# Stage 3 Implementation Plan: ATT-1631

**Ticket**: [ATT-1631](https://atrainingtracker.atlassian.net/browse/ATT-1631)  
**Sub-task**: [ATT-1681](https://atrainingtracker.atlassian.net/browse/ATT-1681) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashes / Sensor Calibration & Data Integrity*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1631`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Traceability & Scope Matrix
* **Requirements Traced**: `REQ-CON-016` (*Smartphone Battery Remaining Duration Continuous Countdown & Discrete Transition Drain Rate Architecture*)
* **Tests Traced**: `TST-CON-007` (*Battery Remaining Duration Monotonic Countdown and Step-Transition Recalibration Verification*)
* **Scope Definition**: Refactor `BatteryDevice.java` to compute drain rates across confirmed battery step-down events, maintain a continuous countdown during constant percentage periods with floor clamping, and author unit tests in `BatteryDeviceTest.kt`.

---

## 2. Step-by-Step Atomic Implementation Tasks

### Task 1: Refactor `BatteryDevice.java` State & Transition Logic
- File: [BatteryDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/BatteryDevice.java)
- Add state variables:
  ```java
  private double mEstablishedDrainRatePerHour = -1.0;
  private int mBaseRemainingSeconds = -1;
  private int mLastStepActiveSeconds = 0;
  private int mLastRecordedLevel = -1;
  ```
- Implement `recordBatteryStep(int activeSeconds, int percent)`:
  - Add new step to `mSampleHistory`.
  - Update `mLastRecordedLevel = percent`.
  - Update `mLastStepActiveSeconds = activeSeconds`.
  - Prune steps older than `activeSeconds - ROLLING_WINDOW_SECONDS` (maintaining at least 2 steps).
  - Compute $\Delta t$ and $\Delta P$ between oldest and latest steps.
  - If $\Delta t > 0$ and $\Delta P \ge 1$:
    - $\text{drainRatePerHour} = \frac{\Delta P}{\Delta t / 3600.0}$.
    - If $\Delta t \ge \text{MIN\_STABILIZATION\_SECONDS}$ or $activeSeconds \ge \text{MIN\_STABILIZATION\_SECONDS}$:
      - `mEstablishedDrainRatePerHour = drainRatePerHour;`
      - `mBaseRemainingSeconds = (int) Math.round((percent / drainRatePerHour) * 3600.0);`
- Update `onBatteryChanged`:
  - Detect step drop (`mBatteryLevel < mLastRecordedLevel`): invoke `recordBatteryStep`.
  - Detect charge increase (`mBatteryLevel > mLastRecordedLevel` without charging flag): reset baseline.
- Update `onTimeTick`:
  - Advance `mActiveRecordingSeconds`.
  - If rate not yet established but active time reaches `MIN_STABILIZATION_SECONDS` and total drop $\ge 1\%$: establish rate.
- Update `updateRemainingTime`:
  - Handle charging (`CHARGING_STATUS_CODE`).
  - Handle warmup / no rate (`STABILIZING_STATUS_CODE`).
  - Calculate countdown:
    ```java
    int elapsedSinceStep = mActiveRecordingSeconds - mLastStepActiveSeconds;
    int remainingSeconds = mBaseRemainingSeconds - elapsedSinceStep;
    int floorSeconds = (int) Math.round(((mBatteryLevel - 1) / mEstablishedDrainRatePerHour) * 3600.0);
    remainingSeconds = Math.max(floorSeconds, remainingSeconds);
    remainingSeconds = Math.max(0, remainingSeconds);
    mBatteryRemainingTimeSensor.newValue(remainingSeconds);
    ```

### Task 2: Enhance Unit Tests in `BatteryDeviceTest.kt`
- File: [BatteryDeviceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/banalservice/devices/BatteryDeviceTest.kt)
- Add tests:
  1. `testRemainingDuration_constantPercentage_countsDownMonotonicallyWithoutDrift`:
     - Baseline 85% at $t=0$, 80% at $t=3600$ (57600s remaining).
     - At $t=3660$ (80%): assert remaining is $57540\text{s} < 57600\text{s}$.
     - At $t=3900$ (80%): assert remaining is $57300\text{s} < 57540\text{s}$.
     - Assert zero upward drift.
  2. `testRemainingDuration_stepDrop_recalibratesSmoothly`:
     - Baseline 85% at $t=0$, 80% at $t=3600$.
     - At $t=4320$ (drop to 79%): assert smooth recalibration.
  3. `testRemainingDuration_floorClamping_preventsPrematureDepletion`:
     - At constant percentage for long period ($t > 3600 + 720$), assert remaining time does not drop below floor of $(P - 1)$.
- Verify existing tests pass with 100% fidelity.

### Task 3: Targeted Verification & Clean-Room Regression
- Execute targeted unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.BatteryDeviceTest"
  ```
- Execute full regression test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Invariant & Regression Guards
1. **Monotonicity Guard**: Between percentage drops, `remainingSeconds` counts down or holds at floor; it is mathematically impossible to drift upwards.
2. **Backward Compatibility**: `PHONE_BATTERY` sensor, charging detection, and formatter contracts are 100% preserved.
3. **No External Dependencies**: Pure in-memory algorithm running during 1Hz telemetry updates with zero wake-locks and zero disk I/O.
