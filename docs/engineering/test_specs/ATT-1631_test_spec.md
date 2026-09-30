# Stage 2 Requirement & Test Specification: ATT-1631

**Ticket**: [ATT-1631](https://atrainingtracker.atlassian.net/browse/ATT-1631)  
**Sub-task**: [ATT-1680](https://atrainingtracker.atlassian.net/browse/ATT-1680) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashes / Sensor Calibration & Data Integrity*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1631`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Requirement Specification (REQ-CON-016)

### REQ-CON-016: Smartphone Battery Remaining Duration Continuous Countdown & Discrete Transition Drain Rate Architecture
The system SHALL compute smartphone battery drain rate strictly across discrete percentage step-down transition events and provide a continuous, monotonic countdown during periods of constant battery level, eliminating sampling-induced upward drift and abrupt multi-hour estimate plunges (ATT-1631):

1. **Discrete Transition-Triggered Drain Rate Computation (`BatteryDevice.java`)**:
   - The system SHALL record discrete battery percentage transition points: `BatteryStep(int activeSeconds, int percent)`.
   - Baseline initialization: At workout start or accumulator reset, record `(mActiveRecordingSeconds, mBatteryLevel)`.
   - Step-down recording: A new `BatteryStep` SHALL be recorded strictly when `mBatteryLevel < lastRecordedStep.percent`.
   - Drain rate evaluation: When evaluating drain rate, the system SHALL calculate the rate between the oldest step in the rolling window ($t_0, P_0$) and the latest step ($t_k, P_k$):
     $$\Delta t = t_k - t_0, \quad \Delta P = P_0 - P_k$$
     $$\text{drainRatePerHour} = \frac{\Delta P}{\Delta t / 3600.0}$$
   - When $\Delta t \ge \text{MIN\_STABILIZATION\_SECONDS}$ (300s) and $\Delta P \ge 1$:
     $$\text{mEstablishedDrainRatePerHour} = \text{drainRatePerHour}$$
     $$\text{mBaseRemainingSeconds} = \text{round}\left(\frac{\text{mBatteryLevel}}{\text{drainRatePerHour}} \times 3600.0\right)$$
     $$\text{mLastStepActiveSeconds} = t_k$$
2. **Continuous Monotonic Countdown Between Step Transitions**:
   - During active recording while the battery percentage remains constant ($t > mLastStepActiveSeconds$):
     $$\text{elapsedSinceStep} = mActiveRecordingSeconds - mLastStepActiveSeconds$$
     $$\text{remainingSeconds} = \text{mBaseRemainingSeconds} - \text{elapsedSinceStep}$$
   - **Monotonicity Invariant**: Remaining duration SHALL NEVER increase or drift upwards while the battery percentage is constant.
   - **Lower Bound Floor Clamping**: To prevent the countdown from falsely collapsing below the next battery level threshold if an interval takes longer than the historical average, remaining seconds SHALL be clamped to the floor of $(P - 1)$:
     $$\text{floorSeconds} = \text{round}\left(\frac{\text{mBatteryLevel} - 1}{\text{mEstablishedDrainRatePerHour}} \times 3600.0\right)$$
     $$\text{remainingSeconds} = \max(\text{floorSeconds}, \text{remainingSeconds})$$
     $$\text{remainingSeconds} = \max(0, \text{remainingSeconds})$$
3. **Warmup & Stabilization**:
   - The sensor SHALL emit `BatteryRemainingTimeFormatter.STABILIZING_STATUS_CODE` (`-2`, formatting as `--:--`) until active recording time reaches $\ge 300\text{s}$ AND a drop of $\ge 1\%$ is measured.
   - If a 1% drop occurs prior to 300s, stabilization completes once total active recording reaches 300s using the effective elapsed time.
4. **Charging & Pause Invariants**:
   - When connected to charger (`mIsCharging == true`), the sensor emits `CHARGING_STATUS_CODE` (`-1`).
   - When tracking is paused, `mActiveRecordingSeconds` does not increment, preserving countdown state without drift.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

1. **Original Requirement ID & Target**: `REQ-CON-015` (*Smartphone Battery Level and Estimated Remaining Duration Telemetry Sensors*), targeting `BatteryDevice.java`.
2. **Historical Origin & Commit Trace**: Commit `809bc479` (Ticket `ATT-1454`, Sprint `2026-40.4`).
3. **Root Reason for Existing Formulation**: `ATT-1454` added periodic 30s samples to estimate drain rate, not anticipating that discrete 1% quantization causes time $\Delta t$ to increase while $\Delta P$ is frozen at an integer, driving projected remaining time artificially upward.
4. **Refinement Reason**: On-road endurance testing demonstrated that constant battery levels caused remaining time to climb by several hours, followed by a violent downward jump upon the next 1% drop. Transition-based rate calculation combined with intra-step countdown restores accurate physical behavior.
5. **Preservation of Core Invariants**: 1Hz sensor dispatch, zero wake-locks, zero disk I/O, `PHONE_BATTERY` accuracy, and charging detection remain 100% intact.

---

## 3. Acceptance Criteria (Given-When-Then)

* **AC-1 (Monotonic Countdown on Constant Level)**:
  - *Given* an active workout tracking session where a battery drop has established a drain rate (e.g. 5.0%/h at 80%),
  - *When* the battery level remains at 80% for several subsequent minutes,
  - *Then* `BATTERY_REMAINING_TIME` SHALL count down steadily (decreasing second-by-second) and SHALL NEVER increase.
* **AC-2 (Smooth Transition Recalibration)**:
  - *Given* an established drain rate and countdown at level $N$,
  - *When* the battery level transitions from $N$ to $N - 1$,
  - *Then* the new drain rate computed from the transition window SHALL smoothly adjust without erratic multi-hour jumps.
* **AC-3 (Warmup Stabilization)**:
  - *Given* active tracking in the first 300 seconds without confirmed drop,
  - *When* `BATTERY_REMAINING_TIME` is queried,
  - *Then* it SHALL return `STABILIZING_STATUS_CODE` (`--:--`).
* **AC-4 (Floor Clamping on Extended Intervals)**:
  - *Given* an established drain rate of 10%/h (expected 360s per 1%),
  - *When* active recording extends past 360s at the same percentage (e.g. 600s),
  - *Then* remaining seconds SHALL NOT count down below the $(P - 1)$ threshold.

---

## 4. Test Case Specification (TST-CON-007)

### TST-CON-007: Battery Remaining Duration Monotonic Countdown and Step-Transition Recalibration Verification
- **Target Component**: [BatteryDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/BatteryDevice.java)
- **Test File**: [BatteryDeviceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/banalservice/devices/BatteryDeviceTest.kt)
- **Scenarios**:
  1. `testRemainingDuration_constantPercentage_countsDownMonotonicallyWithoutDrift`: Verify that after an established drain rate, elapsed seconds at constant battery level produce a strict monotonic decrease without upward drift.
  2. `testRemainingDuration_stepDrop_recalibratesSmoothly`: Verify that subsequent percentage drops recalculate the rate based on transition points.
  3. `testRemainingDuration_floorClamping_preventsPrematureDepletion`: Verify that remaining duration is clamped by the floor of $(P - 1)$ during unexpectedly slow drain.
  4. Preservation of all existing `BatteryDeviceTest` test cases.
  5. Full regression test suite execution (`./gradlew testDebugUnitTest`).
