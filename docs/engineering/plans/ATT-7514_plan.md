# Stage 3: Implementation Plan - ATT-7514: SmartBrakeLightDetector: Auto-bypass leak across sequential workouts and stuck brake light on pause in RADAR_ONLY_LIGHT_OFF

**Ticket**: [ATT-7514](https://rainerblind.atlassian.net/browse/ATT-7514)  
**Sub-task**: [ATT-7896](https://rainerblind.atlassian.net/browse/ATT-7896) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-035` (`REQ-CON-035.5`, `REQ-CON-035.7`)  
**Test Mapping**: `TST-CON-035` (`TST-CON-035.5`, `TST-CON-035.7`, `TST-CON-035.8`)  
**Branch**: `feat/ATT-7514`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
In `SmartBrakeLightDetector.kt` and `RadarLightController.kt`:
1. **Sequential Workout Auto-Bypass Leak**: On workout finish, `RadarLightController.onWorkoutStopped()` invokes `SmartBrakeLightDetector.reset()`. Because `reset()` is designed for test isolation, it resets `isAutoBypassed = false`. While `RadarLightController._isAutoBypassed.value` remains `true` for a connected Garmin Varia RearVue 820, `SmartBrakeLightDetector.isAutoBypassed` is wiped, causing `onWorkoutStarted()` in a sequential workout to register 50 Hz Android `SensorEventListener` listeners (`Sensor.TYPE_LINEAR_ACCELERATION` & `Sensor.TYPE_GRAVITY`), defeating hardware brake sensor auto-bypass and draining battery.
2. **Stuck Brake Light on Pause in RADAR_ONLY_LIGHT_OFF**: In `RadarLightController.onWorkoutPaused()`, lines 308–313 guard mode restoration with `if (preBrakeMode != RadarLightMode.OFF)`. When a cyclist rides with tail light OFF (`RadarLightMode.OFF`) and decelerates hard to an intersection stop triggering auto-pause, braking activates `setLightMode(DAY_FLASH/SOLID)` with `preBrakeMode = OFF`. On auto-pause, the guard prevents restoring `RadarLightMode.OFF`. The light remains stuck on in high-intensity brake illumination indefinitely while the workout is paused.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-035` (*Smartphone IMU/GPS Deceleration Brake-Light Trigger for BLE & ANT+ [0x23] Radar Tail Lights*)
  * `REQ-CON-035.5`: Hardware Brake Sensor Auto-Bypass Retention across sequential workouts.
  * `REQ-CON-035.7`: Active Workout Lifecycle Management & Symmetric Pause Restoration (unconditional `preBrakeMode` restoration).
* **Test Mapping**: `TST-CON-035` (*Smartphone IMU/GPS Deceleration Brake-Light Trigger Test Suite*)
  * `TST-CON-035.5`: Auto-bypass retention test and detector motion state reset test.
  * `TST-CON-035.7`: Pause restoration test from `preBrakeMode == OFF`.
  * `TST-CON-035.8`: Targeted radar unit test suite.

---

## 3. System Invariants & Preserved Behavior
1. **StVZO Anti-Glare Clamping**: Clamping to steady `SOLID` under StVZO regulations or dark/night profiles (`REQ-CON-035.4`) remains invariant.
2. **Battery Conservation**: 50 Hz IMU listeners remain active exclusively during cycling tracking sessions (`REQ-CON-035.7`).
3. **Thread Safety & Lock Invariant**: Peripheral `setLightMode` dispatch in `onWorkoutPaused()` executes outside `synchronized(this)` to prevent re-entrancy and deadlock.
4. **Unit Test Isolation**: Complete state reset via `reset()` on both `SmartBrakeLightDetector` and `RadarLightController` is preserved.
5. **Zero SQLite Schema Modifications**: No database migrations or schema adjustments.

---

## 4. Proposed Architectural Changes

### Component 1: `SmartBrakeLightDetector.kt`
* Introduce `@JvmStatic fun resetMotionState()`:
  * Stops listening and clears transient motion state: `isBraking = false`, `currentSpeedMps = 0f`, `currentDvDt = 0f`, `lastSpeedMps = 0f`, `lastSpeedTimestampMs = 0L`, `lastForwardDeceleration = 0f`, `lastVerticalShock = 0f`, `gravityX = 0f`, `gravityY = 0f`, `gravityZ = 9.81f`, `decelerationStartRealtimeMs = 0L`.
  * Strictly preserves `isAutoBypassed` and `isEnabled`.
* Refactor `@JvmStatic fun reset()`:
  * Calls `resetMotionState()`.
  * Resets `isEnabled = true` and `isAutoBypassed = false` for unit test isolation.

### Component 2: `RadarLightController.kt`
* In `onWorkoutStopped(context: Context? = null)`:
  * Replace `SmartBrakeLightDetector.reset()` with `SmartBrakeLightDetector.resetMotionState()`.
  * Keep `SmartBrakeLightDetector.reset()` in `RadarLightController.reset()`.
* In `onWorkoutPaused()`:
  * Capture `val modeToRestore = if (isBrakeActive) preBrakeMode else null` and clear `isBrakeActive = false` inside `synchronized(this)`.
  * Invoke `modeToRestore?.let { setLightMode(it) }` outside `synchronized(this)`.
  * Removes `if (preBrakeMode != RadarLightMode.OFF)` check, restoring `RadarLightMode.OFF` when pre-brake mode was `OFF`.

### UI Consistency (Rule 23)
* No UI components modified.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `resetMotionState()` in `SmartBrakeLightDetector.kt` (`REQ-CON-035.5`)
* Add `resetMotionState()` method and refactor `reset()` to call `resetMotionState()` before resetting `isAutoBypassed` and `isEnabled`.
* Add unit test in `SmartBrakeLightDetectorTest.kt` verifying `resetMotionState()` preserves `isAutoBypassed` and `isEnabled`.

### Step 2: Update `RadarLightController.kt` Workout Stop and Pause Semantics (`REQ-CON-035.5`, `REQ-CON-035.7`)
* In `onWorkoutStopped()`: replace `SmartBrakeLightDetector.reset()` with `SmartBrakeLightDetector.resetMotionState()`.
* In `onWorkoutPaused()`: capture `modeToRestore` when `isBrakeActive` is true, clear `isBrakeActive = false`, and call `modeToRestore?.let { setLightMode(it) }` outside the synchronized lock.

### Step 3: Add Regression Unit Tests in `RadarLightControllerTest.kt` (`TST-CON-035.5`, `TST-CON-035.7`)
* Add `testRadarLightController_sequentialWorkouts_hardwareBrakeAutoBypassPersists()`: asserts `isAutoBypassed` remains true and IMU listeners do not register across consecutive workouts with a hardware-brake radar.
* Add `testRadarLightController_onWorkoutPaused_withLightOffPreBrake_restoresOffMode()`: asserts pausing while braking with `preBrakeMode == OFF` reverts light mode to `OFF`.

### Step 4: Verification via Targeted Radar Test Suite (`TST-CON-035.8`)
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*"`.
* Verify 100% pass rate with zero regressions.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests in `RadarLightControllerTest.kt` and `SmartBrakeLightDetectorTest.kt`.
* **Rollback**: Clean git branch isolation on `feat/ATT-7514`.
