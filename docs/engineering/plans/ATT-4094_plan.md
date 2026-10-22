# Stage 3: Implementation Plan - ATT-4094: [Medium] Distinguish speed sensor standstill from in-motion signal loss (tunnel/dropout) in AutoPauseManager based on last valid speed

**Ticket**: [ATT-4094](https://atrainingtracker.atlassian.net/browse/ATT-4094)  
**Sub-task**: [ATT-4273](https://atrainingtracker.atlassian.net/browse/ATT-4273) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1153](https://atrainingtracker.atlassian.net/browse/ATT-1153) (*Sensor Architecture & Concurrency Stability*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-TRK-037` (*Speed Sensor Standstill vs. In-Motion Signal Loss (Tunnel / Dropout) Discrimination in AutoPauseManager and Static Coupling Decoupling*)  
**Test Mapping**: `TST-TRK-030` (*AutoPauseManager In-Motion Dropout vs. Standstill Silence Verification Suite*)  
**Branch**: `feature/ATT-4094`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

In Sprint `2026-42.9`, ticket `ATT-3984` resolved an issue where Auto-Pause never triggered because `ProxySensor` inherited a timestamp of `0L`. As part of that change, `AutoPauseManager.evaluateTick(...)` was modified so that when a speed sensor is registered (`hasSpeedSource == true`) and packet updates become stale (`nowMs - packetTimestampMs > 5000L`), the effective speed was unconditionally treated as `0.0 m/s`.

During the Sprint 2026-42.9 review, a key architectural shortcoming was identified:
1. **False Auto-Pauses During In-Motion Signal Loss (Tunnel / Bridge / Underpass / RF Dropout)**:
   If an athlete is cruising at 30 km/h (8.3 m/s) on a road or cycling track and enters a tunnel, GPS signals abruptly drop. A physical athlete cannot decelerate instantaneously from 30 km/h to 0 km/h within a single millisecond without emitting intermediate decelerating speed readings. Treating sudden packet silence while cruising as standstill (`0.0 m/s`) causes `AutoPauseManager` to falsely advance its debounce counter and transition to `AUTO_PAUSED`. Consequently, active workout time (`TIME_ACTIVE`) halts, and route segments or splits inside the tunnel are missed.
   The correct state transition for sudden packet loss while in motion is `SENSOR_LOST`. In `SENSOR_LOST`, the workout continues recording active time while waiting for signal recovery.
2. **Standstill Silence Detection (Deceleration Before Packet Silence)**:
   Conversely, when an athlete approaches a red light, building, or café, their speed slows down below the resume threshold before wheel/GPS sensors stop transmitting. In this scenario, the last valid speed was low ($\le$ resume threshold), so packet silence reliably signifies that the athlete came to a complete physical standstill. Treating this condition as `0.0 m/s` allows `AUTO_PAUSED` to trigger cleanly as intended by `ATT-3984`.
3. **Decouple Static `TrainingApplication.isManualPauseLocked()` Coupling**:
   `AutoPauseManager.onManualResume()` statically queried `TrainingApplication.isManualPauseLocked()`. Decoupling this domain engine by passing `manualPauseLocked: Boolean = false` explicitly via `@JvmOverloads` makes the engine purely unit-testable and adheres to clean architecture principles.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-037` (*Speed Sensor Standstill vs. In-Motion Signal Loss (Tunnel / Dropout) Discrimination in AutoPauseManager and Static Coupling Decoupling*)
  * `REQ-TRK-037.1`: Stale Packet Speed Classification: If `lastValidSpeedMps > resumeThresholdMps`, transition to `SENSOR_LOST` (`"SENSOR_LOST_IN_MOTION_DROPOUT"`); if `lastValidSpeedMps <= resumeThresholdMps` or `null`, evaluate effective speed as `0.0 m/s` and debounce to `AUTO_PAUSED`.
  * `REQ-TRK-037.2`: Dynamic Sensor Unpairing & Disconnection Reset: Transition to `SENSOR_LOST` and explicitly reset `lastValidSpeedMps = null` upon `!hasSpeedSource` or when reset via `setState(TRACKING)` / `setState(MANUAL_PAUSED)`.
  * `REQ-TRK-037.3`: Sensor Recovery Invariant: Require 2 consecutive fresh valid readings to transition from `SENSOR_LOST` back to `TRACKING` (`"SENSOR_RECOVERED"`).
  * `REQ-TRK-037.4`: Decoupled Manual Resume Method Signature: `AutoPauseManager.onManualResume(manualPauseLocked: Boolean = false)` with `@JvmOverloads`, updating all 3 call sites in `TrackerService.java`.
* **Test Mapping**: `TST-TRK-030` (`TST-TRK-030.1` to `TST-TRK-030.6`)
  * `TST-TRK-030.1`: In-motion dropout (> resumeThreshold) transitions to `SENSOR_LOST` without pausing.
  * `TST-TRK-030.2`: Standstill silence ($\le$ resumeThreshold) debounces to `AUTO_PAUSED`.
  * `TST-TRK-030.3`: Dynamic unpairing (`!hasSpeedSource`) transitions to `SENSOR_LOST` and clears `lastValidSpeedMps`.
  * `TST-TRK-030.4`: Sensor recovery hysteresis (2 consecutive valid packets).
  * `TST-TRK-030.5`: Decoupled `onManualResume(manualPauseLocked)` respects lock parameter.
  * `TST-TRK-030.6`: Clean-room test suite execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Thread Safety & Mutex Guard**: All mutable properties and state transitions in `AutoPauseManager` MUST remain `@Synchronized`.
2. **Standstill Auto-Pause Debounce Invariant**: When an athlete decelerates to standstill ($\le$ resume threshold), packet silence must continue to evaluate as `0.0 m/s`, advancing the debounce counter and auto-pausing after `sportDebounceSec`.
3. **Sensor Recovery Contract**: The 2-consecutive-second fresh valid reading hysteresis for `SENSOR_LOST -> TRACKING` recovery MUST NOT be bypassed or degraded.
4. **Manual Pause Precedence Invariant**: Manual pause lock (`manualPauseLocked == true`) continues to override all auto-pause and auto-resume behaviors.
5. **Decoupled Architecture**: Domain logic in `AutoPauseManager` MUST NOT have static dependencies on Android Context or Application singletons (`TrainingApplication`).
6. **Chesterton's Fence Audit for Existing Test `TST-TRK-020_4`**:
   `TST-TRK-020_4` previously asserted that a moving speed of 5.0 m/s followed by packet silence decayed to 0.0 m/s and auto-paused. That assertion embodied the exact problem ticket `ATT-4094` was filed to correct. Under `REQ-TRK-037`, a stale speed of 5.0 m/s (> resume threshold of ~1.0 m/s) is classified as an in-motion dropout (`SENSOR_LOST_IN_MOTION_DROPOUT`) rather than pausing. `TST-TRK-020_4` will be updated to test standstill silence deceleration (e.g. 0.3 m/s $\le$ resume threshold decaying to 0.0 m/s and triggering `AUTO_PAUSED`), while `TST-TRK-030_1` will verify the in-motion dropout to `SENSOR_LOST`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `AutoPauseManager.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManager.kt`)
1. **Property Addition**:
   ```kotlin
   var lastValidSpeedMps: Double? = null
       private set
   ```
2. **Lifecycle Resets**:
   - In `reset()`: set `lastValidSpeedMps = null`.
   - In `setState(newState: AutoPauseState)`: if `newState == AutoPauseState.TRACKING || newState == AutoPauseState.MANUAL_PAUSED`, set `lastValidSpeedMps = null`.
3. **Update on Valid Packet**:
   - In Step 4 of `evaluateTick`:
     ```kotlin
     if (hasFreshValidReading) {
         lastValidSpeedPacketTimestampMs = packetTimestampMs
         lastValidSpeedMps = currentSpeedMps
     }
     ```
4. **Dynamic Unpairing Handling**:
   - When `isSpeedCorrupt || !hasSpeedSource`:
     ```kotlin
     lastValidSpeedMps = null
     if (currentState == AutoPauseState.TRACKING || currentState == AutoPauseState.PAUSE_DEBOUNCE_PENDING) {
         currentState = AutoPauseState.SENSOR_LOST
         debounceCounter = 0
         sensorLostRecoveryTicks = 0
         return AutoPauseTransition(
             previousState = prevState,
             newState = AutoPauseState.SENSOR_LOST,
             auditLogEvent = "SENSOR_LOST_STALE_TTL_EXCEEDED"
         )
     }
     ```
5. **Speed-Dependent Staleness Evaluation**:
   - Calculate thresholds:
     ```kotlin
     val sportThreshold = config.getSpeedThresholdForSport(sportType)
     val sportDebounceSec = config.getDebounceSecForSport(sportType)
     val unit = if (config.isMetric) MyUnits.METRIC else MyUnits.IMPERIAL
     val pauseThresholdMps = MyHelper.UserUnit2mps(sportThreshold, unit)
     val resumeThresholdUserUnit = calculateResumeSpeedThresholdUserUnit(sportThreshold, config.isMetric)
     val resumeThresholdMps = MyHelper.UserUnit2mps(resumeThresholdUserUnit, unit)
     ```
   - Before evaluating pause debounce:
     ```kotlin
     if (!hasFreshValidReading && lastValidSpeedMps != null && lastValidSpeedMps!! > resumeThresholdMps) {
         // In-motion signal loss (tunnel / RF dropout)
         if (currentState == AutoPauseState.TRACKING || currentState == AutoPauseState.PAUSE_DEBOUNCE_PENDING) {
             currentState = AutoPauseState.SENSOR_LOST
             debounceCounter = 0
             sensorLostRecoveryTicks = 0
             return AutoPauseTransition(
                 previousState = prevState,
                 newState = AutoPauseState.SENSOR_LOST,
                 auditLogEvent = "SENSOR_LOST_IN_MOTION_DROPOUT"
             )
         } else if (currentState == AutoPauseState.SENSOR_LOST) {
             sensorLostRecoveryTicks = 0
             return AutoPauseTransition(previousState = prevState, newState = AutoPauseState.SENSOR_LOST)
         }
         return AutoPauseTransition(previousState = prevState, newState = currentState)
     }
     ```
   - For standstill or low speed:
     ```kotlin
     val speedMps = if (hasFreshValidReading) currentSpeedMps else 0.0
     ```
     Proceed with standard pause debounce.
6. **Decouple `onManualResume`**:
   ```kotlin
   @JvmOverloads
   @Synchronized
   fun onManualResume(manualPauseLocked: Boolean = false) {
       if (manualPauseLocked || currentState == AutoPauseState.MANUAL_PAUSED) {
           return
       }
       debounceCounter = 0
       sensorLostRecoveryTicks = 0
       if (currentState == AutoPauseState.AUTO_PAUSED || currentState == AutoPauseState.PAUSE_DEBOUNCE_PENDING) {
           currentState = AutoPauseState.TRACKING
       }
   }
   ```

### Component 2: `TrackerService.java` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`)
Update all 3 call sites to pass `TrainingApplication.isManualPauseLocked()`:
1. Line 429 in `notifyManualResume()`:
   ```java
   service.mAutoPauseManager.onManualResume(TrainingApplication.isManualPauseLocked());
   ```
2. Line 835 in `onStartCommand()` for `RESUME_BY_USER`:
   ```java
   mAutoPauseManager.onManualResume(TrainingApplication.isManualPauseLocked());
   ```
3. Line 2326 in `evaluateAutoPauseTick(...)`:
   ```java
   mAutoPauseManager.onManualResume(manualPauseLocked);
   ```

### Component 3: Unit Tests (`AutoPauseManagerTest.kt`)
Update and add unit tests covering:
- In-motion dropout (> resumeThreshold) to `SENSOR_LOST`.
- Standstill silence ($\le$ resumeThreshold) to `AUTO_PAUSED`.
- Dynamic unpairing reset of `lastValidSpeedMps`.
- `SENSOR_LOST` 2-tick recovery hysteresis.
- Decoupled `onManualResume(manualPauseLocked)` parameter tests.
- Update `TST-TRK-020_4` to test low-speed deceleration silence cleanly.

---

## 5. UI Consistency (Rule 23)
* **Scope**: N/A - Non-UI change. This is a pure background telemetry engine and service state machine change. Zero layouts, views, composables, or UI theme tokens are modified.

---

## 6. Verification & Test Plan

1. **`AutoPauseManagerTest.kt` Targeted Test Execution**:
   - `testInMotionDropoutTransitionsToSensorLostWithoutPausing` (`TST-TRK-030.1`)
   - `testStandstillSilenceDebouncesToAutoPaused` (`TST-TRK-030.2`)
   - `testDynamicUnpairingTransitionsToSensorLostAndResetsSpeed` (`TST-TRK-030.3`)
   - `testSensorRecoveryHysteresisRequiresTwoConsecutiveTicks` (`TST-TRK-030.4`)
   - `testDecoupledManualResumeRespectsLockedParameter` (`TST-TRK-030.5`)
   - Execute: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManagerTest"`
2. **Clean-Room Regression Suite**:
   - Execute: `./gradlew testDebugUnitTest` verifying 100% pass rate across the full repository.

---

## 7. Step-by-Step Implementation Sequence

1. **Step 1**: Modify `AutoPauseManager.kt`:
   - Add `var lastValidSpeedMps: Double? = null; private set`.
   - Update `reset()` and `setState(...)` to clear `lastValidSpeedMps`.
   - In `evaluateTick`, track `lastValidSpeedMps = currentSpeedMps` when fresh valid.
   - In `evaluateTick`, clear `lastValidSpeedMps = null` on `!hasSpeedSource` / corrupt speed.
   - In `evaluateTick`, classify stale packet when `lastValidSpeedMps > resumeThresholdMps` as `SENSOR_LOST` with event `"SENSOR_LOST_IN_MOTION_DROPOUT"`.
   - Update `onManualResume(manualPauseLocked: Boolean = false)` signature with `@JvmOverloads`.
2. **Step 2**: Update `TrackerService.java`:
   - Explicitly pass `TrainingApplication.isManualPauseLocked()` at lines 429, 835, 2326.
3. **Step 3**: Update and author unit tests in `AutoPauseManagerTest.kt`:
   - Update `TST-TRK-020_4` for standstill silence semantics.
   - Add test cases `TST-TRK-030_1` to `TST-TRK-030_5`.
4. **Step 4**: Run targeted unit tests:
   - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManagerTest"`
5. **Step 5**: Run full clean-room unit test suite:
   - `./gradlew testDebugUnitTest`
