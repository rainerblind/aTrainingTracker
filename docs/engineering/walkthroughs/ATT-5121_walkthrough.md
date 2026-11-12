# Stage 5: Walkthrough & Verification - ATT-5121: Implement smartphone IMU/GPS deceleration brake-light trigger for BLE and ANT+ (0x23) radar tail lights lacking hardware brake sensors

**Ticket**: [ATT-5121](https://rainerblind.atlassian.net/browse/ATT-5121)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-CON-035`  
**Test Mapping**: `TST-CON-035`  
**Branch**: `subagent-ATT-5121---ATT-1286-Pro-Agent-self-13a798a7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview

This walkthrough validates the software deceleration brake-light trigger for **ATT-5121** and confirms the resolution of all Stage 6 Cold Carbon Review bounce findings (Bounces 1/3, 2/3, and 3/3):
1. **Resume Light Mode Preservation (Bounce 3/3 Finding 1)**:
   - In `RadarLightController.onWorkoutResumed`, the active operating mode (e.g. `PELOTON` 8 lm or night steady modes) is preserved upon unpausing, only defaulting to `defaultMode` if the light is currently `OFF`.
   - On workout pause, any active brake light cleanly reverts to `preBrakeMode` so lights do not freeze in brake mode during stops.
2. **Session Cycling State Tracking (Bounce 3/3 Finding 2)**:
   - `RadarLightController` tracks `isCyclingSession` set during `onWorkoutStarted(ctx, isCycling)` and defaults to it in `onWorkoutResumed`. Non-cycling sessions never activate IMU listening or control lights upon unpause (`REQ-CON-035.7`).
3. **Rigorous Pause/Resume & Session Isolation Assertions (Bounce 3/3 Finding 3)**:
   - `RadarLightControllerTest` verifies mock `SensorManager` listening, `PELOTON` preservation upon unpause, and non-cycling session isolation.
4. **Sensor Lifecycle & Active Tracking Wire-up (`REQ-CON-035.1`)**:
   - `SmartBrakeLightDetector` implements `SensorEventListener`, registering `TYPE_LINEAR_ACCELERATION` & `TYPE_GRAVITY` with Android `SensorManager` and unregistering on pause/stop.
5. **Deceleration Detection & Speed Derivative ($dv/dt \le 0$) (`REQ-CON-035.1`)**:
   - Confirms deceleration $a \le -2.0\text{ m/s}^2$ for $\ge 250\text{ ms}$ at $v \ge 2.5\text{ m/s}$ with $dv/dt \le 0$, preventing false triggers during acceleration.
6. **Night Anti-Glare & StVZO Clamping (`REQ-CON-035.4`)**:
   - Clamps brake mode to steady `SOLID` under StVZO regulations and in dark/tunnel/night environments, strictly prohibiting dazzling 100 lm `DAY_FLASH` at night.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-035.1` | `TST-CON-035.1` | Deceleration trigger, $dv/dt \le 0$ & duration gating (`SmartBrakeLightDetectorTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.2` | `TST-CON-035.2` | Vertical shock & pothole rejection test (`SmartBrakeLightDetectorTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.3` | `TST-CON-035.3` | Priority override, 2.0s hold, workout stop/pause cleanup (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.4` | `TST-CON-035.4` | StVZO and night anti-glare steady clamping (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.5` | `TST-CON-035.5` | RearVue 820 hardware auto-bypass test (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.6` | `TST-CON-035.6` | Preference persistence & 9-language parity (`TranslationParityTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.7` | `TST-CON-035.7` | Cycling workout session gating & pause/resume mode preservation (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PRO-001` | Living requirement governance audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Regression Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Result**: `BUILD SUCCESSFUL` (All 74 radar unit tests and translation parity passed, 0 failures, 0 regressions).

### Living Requirement Governance Audit
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Result**: `PASS` (Master index tables match 100%, 0 unmounted islands, 0 encapsulation violations).

---

## 4. Invariant & Governance Verification

1. **Light Mode Preservation**: Workout resume preserves current active mode (e.g. `PELOTON`) without resetting to default `DAY_FLASH`.
2. **Session Gating**: Non-cycling sessions never activate IMU sensors or tail light controls across workout pauses or resumes.
3. **StVZO & Night Anti-Glare**: Flashing modes under StVZO prohibited; steady `SOLID` (25 lm) enforced in dark/tunnel/night.
4. **Pothole Immunity**: Attitude compensation via $\hat{g}$ prevents false activations from vertical shocks ($> 4.0\text{ m/s}^2$).
5. **Zero SQLite Migrations**: Preference persisted in `TuningPreferencesDataStore` without database schema changes.
