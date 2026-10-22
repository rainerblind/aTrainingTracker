# Stage 2: Requirement & Test Specification - ATT-4094: [Medium] Distinguish speed sensor standstill from in-motion signal loss (tunnel/dropout) in AutoPauseManager based on last valid speed

**Ticket**: [ATT-4094](https://atrainingtracker.atlassian.net/browse/ATT-4094)  
**Sub-task**: [ATT-4272](https://atrainingtracker.atlassian.net/browse/ATT-4272) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1153](https://atrainingtracker.atlassian.net/browse/ATT-1153) (*Sensor Architecture & Concurrency Stability*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-TRK-037` (*Speed Sensor Standstill vs. In-Motion Signal Loss (Tunnel / Dropout) Discrimination in AutoPauseManager*)  
**Test Spec ID**: `TST-TRK-030`  
**Branch**: `feature/ATT-4094`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (`REQ-TRK-037`)

### 1.1 Problem Statement & Rationale
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

### 1.2 Functional & Architectural Requirements (`REQ-TRK-037`)

1. **Stale Packet Speed Classification (`REQ-TRK-037.1`)**:
   - In `AutoPauseManager.evaluateTick(...)`, when a speed source is registered (`hasSpeedSource == true`) and packet updates become stale (`isPacketStale == true`, delta > `STALE_PACKET_TIMEOUT_MS = 5000L`):
     - The system SHALL inspect the last recorded valid speed (`lastValidSpeedMps`).
     - If `lastValidSpeedMps != null && lastValidSpeedMps > resumeThresholdMps`:
       The athlete was in motion at high speed before packets stopped arriving (e.g. tunnel entry, GPS multipath shadow, BLE wheel sensor dropout). `AutoPauseManager` SHALL transition to `AutoPauseState.SENSOR_LOST` (from `TRACKING` or `PAUSE_DEBOUNCE_PENDING`), log `auditLogEvent = "SENSOR_LOST_IN_MOTION_DROPOUT"`, and reset `debounceCounter = 0`. It SHALL NOT evaluate effective speed as `0.0 m/s` and SHALL NOT transition to `AUTO_PAUSED`.
     - If `lastValidSpeedMps == null || lastValidSpeedMps <= resumeThresholdMps`:
       The athlete decelerated to standstill or was already stationary before packet silence (e.g. traffic light stop). `AutoPauseManager` SHALL evaluate effective speed as `0.0 m/s`, advancing `debounceCounter` toward `AUTO_PAUSED` after `pauseDebounceSeconds`.

2. **Dynamic Sensor Unpairing & Disconnection Reset (`REQ-TRK-037.2`)**:
   - When a speed sensor is dynamically unpaired, powered off, or disconnected mid-session such that `hasSpeedSource == false`:
     `AutoPauseManager` SHALL immediately transition to `SENSOR_LOST` (`auditLogEvent = "SENSOR_LOST_STALE_TTL_EXCEEDED"`).
   - In addition, `AutoPauseManager` SHALL explicitly reset `lastValidSpeedMps = null` upon `!hasSpeedSource` or when reset via `setState(TRACKING)` / `setState(MANUAL_PAUSED)`, guaranteeing that stale historic speed values cannot linger across sensor unpairing or distinct workout sessions.

3. **Sensor Recovery Invariant (`REQ-TRK-037.3`)**:
   - When in `SENSOR_LOST`, the engine SHALL require 2 consecutive fresh valid readings (`hasFreshValidReading == true`) to recover back to `TRACKING` (`auditLogEvent = "SENSOR_RECOVERED"`).

4. **Decoupled Manual Resume Method Signature (`REQ-TRK-037.4`)**:
   - `AutoPauseManager.onManualResume(manualPauseLocked: Boolean = false)` SHALL accept `manualPauseLocked: Boolean` with `@JvmOverloads` defaulting to `false`, eliminating static access to `TrainingApplication.isManualPauseLocked()`.
   - Callers in `TrackerService.java` (`notifyManualResume()`, `onStartCommand()` for `RESUME_BY_USER`, and `evaluateAutoPauseTick(...)`) SHALL explicitly pass `TrainingApplication.isManualPauseLocked()` to `onManualResume(...)`.
   - When `manualPauseLocked == true` or `currentState == AutoPauseState.MANUAL_PAUSED`, `onManualResume` SHALL return immediately without modifying state.

---

### 1.3 Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-037`, extending `REQ-TRK-018` (`ATT-2642`) and `REQ-TRK-028` (`ATT-3984`). Targets `AutoPauseManager.kt`, `TrackerService.java`, and `AutoPauseManagerTest.kt`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-4094`, Sprint `2026-42.10`, following Sprint `2026-42.9` code review of `ATT-3984` (commit `69e984f1`).
3. **Root Reason for Existing Formulation**: `ATT-3984` fixed the bug where auto-pause failed to trigger due to zero timestamps in `ProxySensor`, but unconditionally evaluated stale packets as `0.0 m/s`. This worked for standstill red lights, but caused false auto-pauses when cruising athletes entered tunnels or suffered temporary RF dropouts.
4. **Preservation of Core Invariants**: Standstill auto-pause debounce, 2-tick `SENSOR_LOST` recovery hysteresis, manual pause lock precedence, and 100% clean-room test pass rate strictly preserved.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (In-Motion Signal Loss Dropout)**:
  * *Given* an athlete cruising at 8.0 m/s with `resumeThresholdMps = 1.0 m/s`,
  * *When* speed packets stop arriving for > 5000 ms (e.g. entering a tunnel),
  * *Then* `AutoPauseManager` transitions to `SENSOR_LOST` with audit event `"SENSOR_LOST_IN_MOTION_DROPOUT"` and does NOT enter `AUTO_PAUSED`.
* **Criterion 2 (Standstill Silence Debounce)**:
  * *Given* an athlete slowing down to 0.5 m/s ($\le resumeThresholdMps$),
  * *When* speed packets stop arriving for > 5000 ms,
  * *Then* `AutoPauseManager` evaluates effective speed as `0.0 m/s`, increments debounce counter, and transitions to `AUTO_PAUSED` after debounce expires.
* **Criterion 3 (Dynamic Unpairing Reset)**:
  * *Given* an active speed sensor mid-session,
  * *When* the sensor is unpaired or powered off (`hasSpeedSource == false`),
  * *Then* `AutoPauseManager` transitions to `SENSOR_LOST` and resets `lastValidSpeedMps` to `null`.
* **Criterion 4 (Sensor Recovery Hysteresis)**:
  * *Given* `AutoPauseManager` in `SENSOR_LOST`,
  * *When* 2 consecutive fresh valid readings arrive,
  * *Then* `AutoPauseManager` transitions back to `TRACKING` with audit event `"SENSOR_RECOVERED"`.
* **Criterion 5 (Decoupled Manual Resume Lock Guard)**:
  * *Given* `AutoPauseManager` in `AUTO_PAUSED` state,
  * *When* `onManualResume(manualPauseLocked = true)` is invoked,
  * *Then* the state remains `AUTO_PAUSED` without clearing debounce or recovery counters.
* **Criterion 6 (Decoupled Manual Resume Execution)**:
  * *Given* `AutoPauseManager` in `AUTO_PAUSED` state,
  * *When* `onManualResume(manualPauseLocked = false)` is invoked,
  * *Then* the state transitions to `TRACKING` and debounce counters are reset.

---

### 1.5 System Invariants
* **Thread Safety**: All state mutations in `AutoPauseManager` MUST be `@Synchronized`.
* **Clean Architecture**: `AutoPauseManager` MUST NOT reference `TrainingApplication` or any Android Context singleton statically.
* **Clean-Room Regression Suite**: 100% pass rate across the full test suite (`./gradlew testDebugUnitTest`).
* **Localization Invariant**: No user-facing text strings are altered.

---

## 2. Test Specification (`TST-TRK-030`)

### Test Case 1: In-Motion Dropout Transitions to `SENSOR_LOST` (`TST-TRK-030.1`)
* **Scope**: Unit test / Telemetry evaluation
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/AutoPauseManagerTest.kt`
* **Preconditions**: `AutoPauseManager` configured with `resumeThresholdMps = 1.0`, `pauseDebounceSeconds = 2`.
* **Action**:
  1. Emit valid speed reading of `8.0 m/s` at `t = 1000L` -> State is `TRACKING`, `lastValidSpeedMps == 8.0`.
  2. Advance time to `t = 7000L` (delta = 6000ms > 5000ms TTL) with `packetTimestampMs = 1000L` (`isPacketStale = true`).
  3. Evaluate tick.
* **Expected Result**:
  - `manager.currentState == AutoPauseState.SENSOR_LOST`.
  - `manager.lastValidSpeedMps == 8.0`.
  - `manager.debounceCounter == 0`.
  - State does NOT transition to `AUTO_PAUSED` or `PAUSE_DEBOUNCE_PENDING`.

### Test Case 2: Standstill Silence Debounces to `AUTO_PAUSED` (`TST-TRK-030.2`)
* **Scope**: Unit test / Telemetry evaluation
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/AutoPauseManagerTest.kt`
* **Preconditions**: `AutoPauseManager` configured with `resumeThresholdMps = 1.0`, `pauseDebounceSeconds = 2`.
* **Action**:
  1. Emit valid speed reading of `0.4 m/s` ($\le 1.0$) at `t = 1000L` -> `lastValidSpeedMps == 0.4`.
  2. Advance time to `t = 7000L` (delta = 6000ms > 5000ms TTL) with `packetTimestampMs = 1000L`.
  3. Evaluate tick 1 -> State transitions to `PAUSE_DEBOUNCE_PENDING`, `debounceCounter == 1`.
  4. Advance time to `t = 8000L` with stale packet.
  5. Evaluate tick 2 -> `debounceCounter == 2`.
* **Expected Result**:
  - `manager.currentState == AutoPauseState.AUTO_PAUSED`.
  - State cleanly pauses after deceleration.

### Test Case 3: Dynamic Unpairing Transitions to `SENSOR_LOST` and Clears `lastValidSpeedMps` (`TST-TRK-030.3`)
* **Scope**: Unit test / Lifecycle
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/AutoPauseManagerTest.kt`
* **Preconditions**: `AutoPauseManager` in `TRACKING` with `lastValidSpeedMps == 5.0`.
* **Action**:
  1. Evaluate tick with `hasSpeedSource = false`.
* **Expected Result**:
  - `manager.currentState == AutoPauseState.SENSOR_LOST`.
  - `manager.lastValidSpeedMps == null`.

### Test Case 4: Sensor Recovery Hysteresis (`TST-TRK-030.4`)
* **Scope**: Unit test / Recovery
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/AutoPauseManagerTest.kt`
* **Preconditions**: `AutoPauseManager` in `SENSOR_LOST`.
* **Action**:
  1. Emit 1st fresh valid reading -> Remains `SENSOR_LOST` (`sensorLostRecoveryTicks == 1`).
  2. Emit 2nd fresh valid reading -> Transitions to `TRACKING` (`sensorLostRecoveryTicks == 0`).
* **Expected Result**:
  - State cleanly recovers to `TRACKING` after 2 consecutive valid packets.

### Test Case 5: Decoupled Manual Resume Lock Guard (`TST-TRK-030.5`)
* **Scope**: Unit test / Control logic
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/AutoPauseManagerTest.kt`
* **Preconditions**: `AutoPauseManager` in `AUTO_PAUSED`.
* **Action**:
  1. Invoke `manager.onManualResume(manualPauseLocked = true)`.
  2. Check state.
  3. Invoke `manager.onManualResume(manualPauseLocked = false)`.
  4. Check state.
* **Expected Result**:
  - In step 2: `manager.currentState == AutoPauseState.AUTO_PAUSED`.
  - In step 4: `manager.currentState == AutoPauseState.TRACKING`.

### Test Case 6: Full Clean-Room Regression Suite (`TST-TRK-030.6`)
* **Scope**: Clean-room test suite execution
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**:
  - 100% test pass rate across all unit tests with 0 regressions.

---

## 3. Traceability Matrix

| Requirement ID | Test Case ID | Test Target File | Description |
|---|---|---|---|
| `REQ-TRK-037.1` | `TST-TRK-030.1` | `AutoPauseManagerTest.kt` | In-motion signal loss transitions to `SENSOR_LOST` without pausing |
| `REQ-TRK-037.1` | `TST-TRK-030.2` | `AutoPauseManagerTest.kt` | Standstill silence ($\le resumeThreshold$) debounces to `AUTO_PAUSED` |
| `REQ-TRK-037.2` | `TST-TRK-030.3` | `AutoPauseManagerTest.kt` | Unpairing (`!hasSpeedSource`) transitions to `SENSOR_LOST` and clears `lastValidSpeedMps` |
| `REQ-TRK-037.3` | `TST-TRK-030.4` | `AutoPauseManagerTest.kt` | Recovery from `SENSOR_LOST` requires 2 consecutive valid readings |
| `REQ-TRK-037.4` | `TST-TRK-030.5` | `AutoPauseManagerTest.kt` | Decoupled `onManualResume` respects `manualPauseLocked` parameter |
| `REQ-TRK-037` | `TST-TRK-030.6` | Whole repository | Clean-room test suite pass rate |
