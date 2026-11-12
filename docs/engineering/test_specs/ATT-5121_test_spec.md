# Stage 2: Requirement & Test Specification - ATT-5121: Implement smartphone IMU/GPS deceleration brake-light trigger for BLE and ANT+ (0x23) radar tail lights lacking hardware brake sensors

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

## 1. Traceability & Requirements Mapping

* **Requirement**: `REQ-CON-035` (*Smartphone IMU/GPS Deceleration Brake-Light Trigger for BLE & ANT+ [0x23] Radar Tail Lights*)
* **Test Specification**: `TST-CON-035` (*Smartphone IMU/GPS Deceleration Brake-Light Trigger Test Suite*)

---

## 2. Requirements & Verification Matrix

| Requirement ID | Description | Test Case | Target Test Class |
| :--- | :--- | :--- | :--- |
| `REQ-CON-035.1` | Forward deceleration detection ($a \le -2.0\text{ m/s}^2$ sustained $\ge 250\text{ ms}$, $v \ge 2.5\text{ m/s}$, $dv/dt \le 0$) | `TST-CON-035.1` | `SmartBrakeLightDetectorTest.kt` |
| `REQ-CON-035.2` | Road bump and vertical shock rejection ($|a_v| > 4.0\text{ m/s}^2$ without longitudinal deceleration) | `TST-CON-035.2` | `SmartBrakeLightDetectorTest.kt` |
| `REQ-CON-035.3` | Immediate priority override of rate limits, continuous hold during braking, and $2.0\text{ s}$ post-brake hold before pre-brake restoration | `TST-CON-035.3` | `RadarLightControllerTest.kt` |
| `REQ-CON-035.4` | StVZO mode clamping invariant (strictly clamps brake mode to steady `SOLID`, zero flashing) | `TST-CON-035.4` | `RadarLightControllerTest.kt` |
| `REQ-CON-035.5` | Garmin Varia RearVue 820 onboard hardware brake sensor auto-bypass (BLE `0x2A24` / ANT+ Page `0x50`) | `TST-CON-035.5` | `RadarLightControllerTest.kt` |
| `REQ-CON-035.6` | Preference persistence (`radarBrakeLightEnabled`) and 100% 9-language UI localization parity in `strings_radar.xml` | `TST-CON-035.6` | `TranslationParityTest.kt` |

---

## 3. Detailed Acceptance Criteria & Verification Procedures

### TST-CON-035.1: Deceleration Trigger & Duration Gating
* **Given**: Cyclist riding steadily at $25\text{ km/h}$ ($v \approx 6.94\text{ m/s}$) with Software Brake Light enabled and tail light in `PELOTON` mode.
* **When**: Cyclist initiates hard braking producing forward deceleration $a \le -2.0\text{ m/s}^2$ sustained for $\ge 250\text{ ms}$.
* **Then**: `SmartBrakeLightDetector` confirms a Brake Event and immediately invokes `RadarLightController.triggerBrakeLight(true)`.
* **When**: Transient deceleration lasts $< 250\text{ ms}$ or occurs at low rolling speed ($v < 2.5\text{ m/s}$).
* **Then**: Zero Brake Event is emitted.

### TST-CON-035.2: Road Shock & Pothole Immunity
* **Given**: Cyclist riding at constant speed over rough asphalt or a pothole causing high vertical shock ($|a_v| = |\vec{a}_{\text{lin}} \cdot \hat{g}| > 4.0\text{ m/s}^2$).
* **When**: Evaluated by `SmartBrakeLightDetector`.
* **Then**: High vertical acceleration without sustained longitudinal deceleration is rejected, producing zero false brake activations.

### TST-CON-035.3: Priority Preemption, Hold Duration & Pre-Brake Restoration
* **Given**: `SmartRadarLightEngine` actively rate-limited ($\ge 1.5\text{ s}$ window) in daytime mode (`OFF`).
* **When**: Brake Event triggers.
* **Then**: `RadarLightController` immediately overrides rate limits and dispatches brake mode (`DAY_FLASH` or `SOLID`).
* **When**: Deceleration ceases after $1.5\text{ s}$ of active braking.
* **Then**: Brake mode is held through deceleration plus an additional $2.0\text{ s}$ post-brake hold window, after which previous mode (`OFF`) is smoothly restored.

### TST-CON-035.4: StVZO Steady Clamping
* **Given**: StVZO-certified peripheral connected (`isStvzo == true`).
* **When**: Brake Event triggers in daytime.
* **Then**: Commanded brake mode is strictly clamped to `RadarLightMode.SOLID` (5 lm steady), and `DAY_FLASH` is strictly forbidden.

### TST-CON-035.5: Hardware Sensor Auto-Bypass
* **Given**: Connected radar peripheral identifies as Garmin Varia RearVue 820 (`010-03180-00` or `010-03180-10`).
* **When**: Device connection completes.
* **Then**: Software brake triggering defaults to bypassed (`isAutoBypassed = true`), deferring to onboard hardware braking.

---

## 4. Invariants & Governance

1. **StVZO Legal Compliance**: Flashing modes under StVZO regulations are strictly prohibited; brake trigger strictly emits steady `SOLID`.
2. **Pothole Immunity**: Dynamic attitude compensation via $\hat{g}$ prevents false activations from vertical bumps.
3. **Pre-Brake Restoration**: System accurately restores the exact operating mode active prior to braking ($2.0\text{ s}$ post-brake hold).
4. **Zero SQLite Migrations**: Preference persisted in `TuningPreferencesDataStore` without database schema changes.
5. **100% 9-Language Localization Parity**: Settings strings verified across EN, DE, ES, FR, IT, JA, NL, PL, PT.
