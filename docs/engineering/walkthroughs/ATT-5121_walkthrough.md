# Stage 5: Walkthrough & Verification - ATT-5121: Implement smartphone IMU/GPS deceleration brake-light trigger for BLE and ANT+ (0x23) radar tail lights lacking hardware brake sensors

**Ticket**: [ATT-5121](https://rainerblind.atlassian.net/browse/ATT-5121)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-CON-035`  
**Test Mapping**: `TST-CON-035`  
**Branch**: `subagent-ATT-5121---ATT-1286-Flash-Agent-self-c035df4d`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview

This walkthrough validates the software deceleration brake-light trigger for **ATT-5121**:
1. **Dynamic Attitude Compensation & Deceleration Detection (`REQ-CON-035.1`, `SmartBrakeLightDetector.kt`)**:
   - Fuses `Sensor.TYPE_LINEAR_ACCELERATION` with `Sensor.TYPE_GRAVITY` to isolate forward longitudinal deceleration $a_{\text{long}}$.
   - Triggers a Brake Event when forward deceleration $a \le -2.0\text{ m/s}^2$ is sustained for $\ge 250\text{ ms}$ at riding speeds $v \ge 2.5\text{ m/s}$.
2. **Road Shock & Pothole Rejection (`REQ-CON-035.2`, `SmartBrakeLightDetector.kt`)**:
   - Isolates vertical shock $|a_v| > 4.0\text{ m/s}^2$, rejecting pothole spikes that lack sustained longitudinal deceleration.
3. **Immediate Priority Preemption, Hold Duration & Pre-Brake Restoration (`REQ-CON-035.3`, `RadarLightController.kt`)**:
   - Overrides `SmartRadarLightEngine` rate limits upon braking, commanding high-visibility brake mode (`DAY_FLASH` / `SOLID`).
   - Maintains brake mode through deceleration plus $2.0\text{ s}$ post-brake hold window before smoothly restoring previous operating mode.
4. **StVZO Compliance Invariant (`REQ-CON-035.4`, `RadarLightController.kt`)**:
   - Strictly clamps commanded brake mode to steady `RadarLightMode.SOLID` (5 lm) under StVZO certification.
5. **Hardware Auto-Bypass (`REQ-CON-035.5`, `RadarLightController.kt`, `BTLERadarDevice.java`)**:
   - Detects Garmin Varia RearVue 820 (`010-03180*`), automatically bypassing software trigger to defer to onboard hardware brake sensor.
6. **Preferences & 9-Language Localization Parity (`REQ-CON-035.6`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`)**:
   - Persisted `radarBrakeLightEnabled` preference toggle with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-035.1` | `TST-CON-035.1` | Deceleration trigger & duration gating test (`SmartBrakeLightDetectorTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.2` | `TST-CON-035.2` | Vertical shock & pothole rejection test (`SmartBrakeLightDetectorTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.3` | `TST-CON-035.3` | Priority override, 2.0s hold & pre-brake restore test (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.4` | `TST-CON-035.4` | StVZO steady clamping invariant test (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.5` | `TST-CON-035.5` | RearVue 820 hardware auto-bypass test (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.6` | `TST-CON-035.6` | 9-Language localization parity audit (`TranslationParityTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PRO-001` | Living requirement governance audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Regression Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.RadarLightControllerTest" --tests "com.atrainingtracker.banalservice.devices.radar.SmartBrakeLightDetectorTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Result**: `BUILD SUCCESSFUL in 3m 55s` (11 targeted unit tests passed, 0 failures, 0 regressions).

### Living Requirement Governance Audit
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Result**: `PASS` (Master tables match 100%, 0 unmounted Composable islands, 0 encapsulation violations, net-new `REQ-CON-035` cleanly mapped).

---

## 4. Invariant & Governance Verification

1. **StVZO Legal Compliance**: Flashing modes under StVZO regulations are strictly prohibited; brake trigger strictly emits steady `SOLID`.
2. **Pothole Immunity**: Attitude compensation via $\hat{g}$ prevents false activations from vertical shocks ($> 4.0\text{ m/s}^2$).
3. **Pre-Brake Restoration**: System accurately restores exact operating mode active prior to braking after $2.0\text{ s}$ hold window.
4. **Zero SQLite Migrations**: Preference persisted in `TuningPreferencesDataStore` without database schema changes.
