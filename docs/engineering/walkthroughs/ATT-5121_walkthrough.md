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

This walkthrough validates the software deceleration brake-light trigger for **ATT-5121** and confirms the resolution of all Stage 6 Cold Review bounce findings (Bounces 1/3 and 2/3):
1. **Cycling Activity & Light Availability Gating (`REQ-CON-035.7`, Bounce 2/3 Finding 1)**:
   - `RadarLightController.onWorkoutStarted` and `onWorkoutResumed` accept `isCycling: Boolean = true` and only activate IMU sensors if light control is actually supported/connected (`_isLightControlAvailable.value || RadarTrafficSimulator.isSimulating`).
   - `TrackerService` dispatches cycling checks on workout start and mid-ride sport changes, unregistering IMU listeners during non-cycling activities (running/gym) to conserve battery.
2. **Preference Preservation Across Lifecycle (Bounce 2/3 Finding 2)**:
   - `SmartBrakeLightDetector.reset()` no longer overwrites `isEnabled = true`, reliably preserving user-disabled settings across workout lifecycles.
3. **Sensor Lifecycle & Active Tracking Wire-up (`REQ-CON-035.1`)**:
   - `SmartBrakeLightDetector` implements `SensorEventListener`, registering `TYPE_LINEAR_ACCELERATION` and `TYPE_GRAVITY` with Android `SensorManager` and unregistering on pause/stop.
4. **Speed Derivative & Deceleration Detection (`REQ-CON-035.1`)**:
   - Confirms deceleration $a \le -2.0\text{ m/s}^2$ for $\ge 250\text{ ms}$ at $v \ge 2.5\text{ m/s}$ with speed derivative $dv/dt \le 0$, preventing false triggers during acceleration.
5. **Pothole Shock Rejection & Anti-Resuscitation (`REQ-CON-035.2`, `REQ-CON-035.3`)**:
   - Rejects vertical shocks ($|a_v| > 4.0\text{ m/s}^2$) lacking sustained longitudinal deceleration.
   - `RadarLightController.onWorkoutStopped` cancels pending `brakeHoldRunnable`, resets `isBrakeActive = false`, and restores mode to `OFF`.
6. **Night Anti-Glare & StVZO Clamping (`REQ-CON-035.4`)**:
   - Clamps brake mode to steady `SOLID` under StVZO regulations and in dark/tunnel/night environments, strictly prohibiting dazzling 100 lm `DAY_FLASH` at night.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-035.1` | `TST-CON-035.1` | Deceleration trigger, $dv/dt \le 0$ & duration gating (`SmartBrakeLightDetectorTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.2` | `TST-CON-035.2` | Vertical shock & pothole rejection test (`SmartBrakeLightDetectorTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.3` | `TST-CON-035.3` | Priority override, 2.0s hold, workout stop cleanup (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.4` | `TST-CON-035.4` | StVZO and night anti-glare steady clamping (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.5` | `TST-CON-035.5` | RearVue 820 hardware auto-bypass test (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.6` | `TST-CON-035.6` | Preference persistence & 9-language parity (`TranslationParityTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PRO-001` | Living requirement governance audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Regression Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Result**: `BUILD SUCCESSFUL` (All 72 radar unit tests and translation parity passed, 0 failures, 0 regressions).

### Living Requirement Governance Audit
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Result**: `PASS` (Master index tables match 100%, 0 unmounted islands, 0 encapsulation violations).

---

## 4. Invariant & Governance Verification

1. **Resource Conservation**: IMU sensors active strictly during cycling workouts with connected/simulated radar light controls.
2. **Preference Invariance**: `reset()` preserves user settings without clobbering disabled state.
3. **StVZO & Night Anti-Glare**: Flashing modes under StVZO prohibited; steady `SOLID` (25 lm) enforced in dark/tunnel/night.
4. **Pothole Immunity**: Attitude compensation via $\hat{g}$ prevents false activations from vertical shocks ($> 4.0\text{ m/s}^2$).
5. **Zero SQLite Migrations**: Preference persisted in `TuningPreferencesDataStore` without database schema changes.
