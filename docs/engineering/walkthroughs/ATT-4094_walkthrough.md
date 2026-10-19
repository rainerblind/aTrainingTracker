# Stage 5 Walkthrough: ATT-4094 - Distinguish speed sensor standstill from in-motion signal loss (tunnel/dropout) in AutoPauseManager based on last valid speed

**Ticket**: [ATT-4094](https://atrainingtracker.atlassian.net/browse/ATT-4094)  
**Sub-task**: [ATT-4275](https://atrainingtracker.atlassian.net/browse/ATT-4275) (`[Test]`)  
**Parent Epic**: [ATT-1153](https://atrainingtracker.atlassian.net/browse/ATT-1153) (*Sensor Architecture & Concurrency Stability*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-TRK-037` (*Speed Sensor Standstill vs. In-Motion Signal Loss (Tunnel / Dropout) Discrimination in AutoPauseManager and Static Coupling Decoupling*)  
**Test Spec ID**: `TST-TRK-030`  
**Branch**: `feature/ATT-4094`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Verification Outcome

Ticket `ATT-4094` resolves a key telemetry defect in `AutoPauseManager` introduced during `ATT-3984`: when an athlete was cruising at speed and encountered an in-motion signal dropout (e.g. entering a tunnel, underpass, bridge, or experiencing an RF dropout), `AutoPauseManager` previously treated packet silence unconditionally as `0.0 m/s`, causing false auto-pauses that halted active workout recording.

With `ATT-4094` implemented:
1. **Speed-Dependent Staleness Classification (`REQ-TRK-037.1`)**:
   - `AutoPauseManager` tracks the last recorded valid speed (`lastValidSpeedMps`).
   - If packets cease while `lastValidSpeedMps > resumeThresholdMps` (athlete was moving at speed before signal loss), the system transitions to `SENSOR_LOST` (`"SENSOR_LOST_IN_MOTION_DROPOUT"`), keeping active workout time running and resetting the debounce counter.
   - If packets cease when `lastValidSpeedMps <= resumeThresholdMps` or `null` (athlete decelerated to standstill before silence), effective speed is treated as `0.0 m/s`, cleanly debouncing to `AUTO_PAUSED`.
2. **Dynamic Unpairing / Power-Off Reset (`REQ-TRK-037.2`)**:
   - When a speed sensor is unpaired or powered off mid-session (`!hasSpeedSource`), `AutoPauseManager` transitions to `SENSOR_LOST` and explicitly resets `lastValidSpeedMps = null`, preventing stale historic speeds from lingering across sensor lifecycles.
3. **Sensor Recovery Hysteresis (`REQ-TRK-037.3`)**:
   - `SENSOR_LOST` recovery strictly requires 2 consecutive fresh valid speed readings before returning to `TRACKING` (`"SENSOR_RECOVERED"`).
4. **Decoupled Architecture (`REQ-TRK-037.4`)**:
   - `AutoPauseManager.onManualResume(manualPauseLocked: Boolean = false)` eliminates static access to `TrainingApplication.isManualPauseLocked()`.
   - All 3 call sites in `TrackerService.java` (`notifyManualResume()`, `RESUME_BY_USER`, `evaluateAutoPauseTick(...)`) explicitly pass `TrainingApplication.isManualPauseLocked()`.

---

## 2. Requirement & Test Traceability Matrix

| Requirement | Test Spec | Scope | Test Target | Result | Status |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `REQ-TRK-037.1` | `TST-TRK-030.1` | Unit / Telemetry | `AutoPauseManagerTest.TST-TRK-030_1 in-motion dropout above resumeThreshold transitions to SENSOR_LOST without pausing` | **PASSED** | `Verified` |
| `REQ-TRK-037.1` | `TST-TRK-030.2` | Unit / Telemetry | `AutoPauseManagerTest.TST-TRK-030_2 standstill packet silence debounces to AUTO_PAUSED` | **PASSED** | `Verified` |
| `REQ-TRK-037.2` | `TST-TRK-030.3` | Unit / Lifecycle | `AutoPauseManagerTest.TST-TRK-030_3 dynamic unpairing transitions to SENSOR_LOST and clears lastValidSpeedMps` | **PASSED** | `Verified` |
| `REQ-TRK-037.3` | `TST-TRK-030.4` | Unit / Recovery | `AutoPauseManagerTest.TST-TRK-030_4 sensor recovery hysteresis requires two consecutive valid packets` | **PASSED** | `Verified` |
| `REQ-TRK-037.4` | `TST-TRK-030.5` | Unit / Control | `AutoPauseManagerTest.TST-TRK-030_5 decoupled onManualResume respects manualPauseLocked parameter` | **PASSED** | `Verified` |
| `REQ-TRK-037` | `TST-TRK-030.6` | Full Regression | Clean-room test suite (`./gradlew testDebugUnitTest`) | **PASSED** | `Verified` |

---

## 3. Test Execution Verification

### 3.1 Targeted Test Suite
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManagerTest"`
* Result: 24 tests completed, 0 failures, 100% pass rate in 7 seconds.
* `TrackerServiceFinalizationTest`: 100% pass rate.

### 3.2 Clean-Room Full Regression Suite
* Command: `./gradlew testDebugUnitTest`
* Result: 100% pass rate across the full repository test suite.

---

## 4. Modified & Created Files

* [AutoPauseManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManager.kt):
  * Added `lastValidSpeedMps: Double? = null; private set`.
  * Updated `reset()` and `setState(...)` to clear `lastValidSpeedMps`.
  * In `evaluateTick(...)`:
    * Recorded `lastValidSpeedMps = currentSpeedMps` when fresh valid packets arrive.
    * Explicitly cleared `lastValidSpeedMps = null` on corrupt speed or `!hasSpeedSource`.
    * Implemented in-motion signal loss branch: when `!hasFreshValidReading && lastValidSpeedMps > resumeThresholdMps`, transitioned to `SENSOR_LOST` (`"SENSOR_LOST_IN_MOTION_DROPOUT"`).
    * Maintained standstill silence evaluation: when `lastValidSpeedMps <= resumeThresholdMps`, treated speed as `0.0 m/s` to debounce to `AUTO_PAUSED`.
  * Updated `onManualResume(manualPauseLocked: Boolean = false)` with `@JvmOverloads`, removing static coupling.
* [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java):
  * Updated all 3 call sites (lines 429, 835, 2326) to explicitly pass `TrainingApplication.isManualPauseLocked()`.
* [AutoPauseManagerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManagerTest.kt):
  * Updated `TST-TRK-020_4` for standstill silence semantics.
  * Added test cases `TST-TRK-030_1` to `TST-TRK-030_5`.
* Living documentation:
  * [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) (`REQ-TRK-037` -> `Verified`)
  * [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) (`TST-TRK-030` -> `Verified`)
