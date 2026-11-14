# Stage 1 Analysis: ATT-7514 - SmartBrakeLightDetector: Auto-bypass leak across sequential workouts and stuck brake light on pause in RADAR_ONLY_LIGHT_OFF

**Ticket**: [ATT-7514](https://rainerblind.atlassian.net/browse/ATT-7514)  
**Sub-task**: [ATT-7892](https://rainerblind.atlassian.net/browse/ATT-7892) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7514`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
During Stage 6 Cold Carbon Review of ATT-5121, two edge-case regressions were identified in the smartphone IMU/GPS deceleration brake-light engine:
1. **Sequential Workout Auto-Bypass Leak**: In `RadarLightController.onWorkoutStopped()`, `SmartBrakeLightDetector.reset()` is invoked. `reset()` was designed as a unit test isolation helper and resets `isAutoBypassed = false`. Consequently, when a physical cycling radar with hardware-level brake sensing (Garmin Varia RearVue 820) remains paired across sequential workouts, `RadarLightController._isAutoBypassed.value` remains `true` while `SmartBrakeLightDetector.isAutoBypassed` is cleared to `false`. On starting a sequential workout (`onWorkoutStarted()`), `!SmartBrakeLightDetector.isAutoBypassed` evaluates to true, re-registering 50 Hz smartphone IMU sensor listeners (`Sensor.TYPE_LINEAR_ACCELERATION` & `Sensor.TYPE_GRAVITY`) and draining battery unnecessarily despite hardware brake sensing being present.
2. **Stuck Brake Light on Pause in RADAR_ONLY_LIGHT_OFF**: In `RadarLightController.onWorkoutPaused()`, lines 308–313 check `if (preBrakeMode != RadarLightMode.OFF)` before invoking `setLightMode(preBrakeMode)`. When a cyclist rides with tail light OFF (e.g. daytime radar-only tracking with `RadarLightMode.OFF`) and decelerates hard to an intersection stop triggering auto-pause, braking activates `setLightMode(DAY_FLASH/SOLID)` and sets `preBrakeMode = OFF`. When `onWorkoutPaused()` is triggered upon stopping, the guard `preBrakeMode != OFF` prevents `setLightMode(RadarLightMode.OFF)` from restoring pre-brake mode. Meanwhile, `isBrakeActive` is cleared to false and `brakeHoldRunnable` is cancelled, stranding the radar tail light in high-intensity brake illumination indefinitely while paused.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Codebase Verification**:
  * Inspected `RadarLightController.kt:386`: `onWorkoutStopped()` calls `SmartBrakeLightDetector.reset()`.
  * Inspected `SmartBrakeLightDetector.kt:345-361`: `reset()` sets `isAutoBypassed = false` in addition to clearing velocity, acceleration, and deceleration accumulators.
  * Line reference audit: Ticket cited `RadarLightController.kt:256, 270-272` and `SmartBrakeLightDetector.kt:348`. Code inspection confirms lines in current sprint codebase:
    - Start check: `RadarLightController.kt:292` (and resume check `l.359`).
    - Pause check: `RadarLightController.kt:308-313`.
    - Stop reset: `RadarLightController.kt:386`.
    - Detector reset: `SmartBrakeLightDetector.kt:345-361`.
  * Inspected `RadarLightController.kt:308-313`: Inside `onWorkoutPaused()`, `if (preBrakeMode != RadarLightMode.OFF) { setLightMode(preBrakeMode) }`. When `preBrakeMode == OFF`, `setLightMode` is skipped, leaving the light in the brake mode (`DAY_FLASH` or `SOLID`).
* **Independent Peer Review & Verification**:
  * Confirmed that `registerRadarDevice()` sets both `_isAutoBypassed.value` and `SmartBrakeLightDetector.isAutoBypassed` to `hasHardwareBrakeSensor`. In sequential workouts without unregistering, `_isAutoBypassed.value` remains true, but `SmartBrakeLightDetector.reset()` in `onWorkoutStopped()` wiped `SmartBrakeLightDetector.isAutoBypassed`, breaking synchronization between the controller and detector.
  * Confirmed that `setLightMode` in `onWorkoutPaused()` was invoked inside `synchronized(this)`. Moving restoration outside the synchronized block prevents potential deadlock/re-entrancy during peripheral command dispatch.
  * Confirmed that `checkAndRestorePreBrakeMode()` already restores `setLightMode(preBrakeMode)` unconditionally upon hold expiration (`l.470`). `onWorkoutPaused()` must match this behavior to restore `preBrakeMode = RadarLightMode.OFF`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * In `SmartBrakeLightDetector.kt`: Add `resetMotionState()` to reset transient deceleration tracking variables (speed, dv/dt, gravity, acceleration accumulators, braking flag) while preserving `isAutoBypassed` and `isEnabled`. Make `reset()` invoke `resetMotionState()` and reset `isAutoBypassed = false` and `isEnabled = true` for complete test isolation.
  * In `RadarLightController.kt`: In `onWorkoutStopped()`, invoke `SmartBrakeLightDetector.resetMotionState()` instead of `SmartBrakeLightDetector.reset()`. Keep `SmartBrakeLightDetector.reset()` in `RadarLightController.reset()`.
  * In `RadarLightController.kt`: In `onWorkoutPaused()`, capture `modeToRestore = if (isBrakeActive) preBrakeMode else null`, reset `isBrakeActive = false`, and unconditionally invoke `modeToRestore?.let { setLightMode(it) }` outside the synchronized block.
  * Add unit tests in `RadarLightControllerTest.kt` covering sequential workouts with hardware-brake radar and pause while braking from `RadarLightMode.OFF`.
* **Out-of-Scope Non-Goals**:
  * Altering StVZO steady clamping rules (`REQ-CON-035.4`), speed derivative threshold, or changing preference keys in `TuningPreferencesDataStore`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-035.5` (Hardware Brake Sensor Auto-Bypass) and `REQ-CON-035.7` / `REQ-CON-035.3` (Active Workout Lifecycle Management & Restoration) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Commit `1a6606d501417727dff28b9f38ca6cb581c9d040` (ATT-5121).
* **Root Reason for Existing Formulation**: In ATT-5121, `SmartBrakeLightDetector.reset()` was added as a test isolation helper; its invocation in `onWorkoutStopped()` was intended to clear leftover accelerometer drift between workouts, but inadvertently wiped hardware-gating state. The guard `if (preBrakeMode != RadarLightMode.OFF)` in `onWorkoutPaused()` was defensively added under the mistaken assumption that if the light was already OFF before braking, turning it off on pause was unnecessary—failing to account for the fact that active braking had turned the light ON.
* **Preservation of Core Invariants**: Hardware auto-bypass for Garmin Varia RearVue 820 preserved across workouts; 2.0s hold cancellation on pause/stop preserved; battery conservation preserved; test isolation across unit tests preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-035.5` (Auto-bypass retention across workouts), `REQ-CON-035.7` (Lifecycle sensor shutdown and pre-brake restoration on pause).
* **Affected Architecture Elements**: `SmartBrakeLightDetector.kt`, `RadarLightController.kt`.
* **Verification Impact & Test Surface**: `SmartBrakeLightDetectorTest.kt`, `RadarLightControllerTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Pure logic correction isolating transient motion state from hardware configuration, and ensuring symmetric mode restoration.

---

## 6. Architectural Strategy & High-Level Solution
1. **Motion State vs Hardware Config Separation (`SmartBrakeLightDetector.kt`)**:
   * Implement `resetMotionState()`: stops listening, clears `isBraking`, speed, dv/dt, gravity, acceleration components, and deceleration timers, without altering `isAutoBypassed` or `isEnabled`.
   * Update `reset()`: calls `resetMotionState()` and resets `isAutoBypassed = false`, `isEnabled = true`.
2. **Sequential Workout Hygiene (`RadarLightController.kt`)**:
   * Replace `SmartBrakeLightDetector.reset()` in `onWorkoutStopped()` with `SmartBrakeLightDetector.resetMotionState()`.
3. **Unconditional Pre-Brake Restoration on Pause (`RadarLightController.kt`)**:
   * In `onWorkoutPaused()`, capture `val modeToRestore = if (isBrakeActive) preBrakeMode else null`, clear `isBrakeActive = false`, and call `modeToRestore?.let { setLightMode(it) }` outside `synchronized(this)`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  - StVZO steady clamping invariant (`REQ-CON-035.4`) preserved (zero flashing modes).
  - Sensor listening lifecycle bound to active cycling tracking sessions (`REQ-CON-035.7`).
  - Unit test isolation guaranteed via `SmartBrakeLightDetector.reset()` and `RadarLightController.reset()`.
* **Risk Rating**: LOW. Changes are fully localized to state reset and mode restoration within the radar light package.
