# Stage 3: Implementation Plan - ATT-5121: Implement smartphone IMU/GPS deceleration brake-light trigger for BLE and ANT+ (0x23) radar tail lights lacking hardware brake sensors

**Ticket**: [ATT-5121](https://rainerblind.atlassian.net/browse/ATT-5121)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-CON-035` (`REQ-CON-035.1`..`REQ-CON-035.6`)  
**Test Mapping**: `TST-CON-035` (`TST-CON-035.1`..`TST-CON-035.7`)  
**Branch**: `subagent-ATT-5121---ATT-1286-Pro-Agent-self-13a798a7`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
Controllable radar tail lights (Garmin Varia RTL515, RTL516, Bryton Gardia R300L, Magene L508) lack hardware deceleration sensors. This plan implements `SmartBrakeLightDetector` and extends `RadarLightController` to fuse smartphone linear acceleration (`Sensor.TYPE_LINEAR_ACCELERATION`), gravity attitude compensation (`Sensor.TYPE_GRAVITY`), and speed telemetry ($v, dv/dt$) to trigger an immediate, high-visibility brake light during hard braking ($a \le -2.0\text{ m/s}^2$ for $\ge 250\text{ ms}$ at $v \ge 2.5\text{ m/s}$), reject road shocks/potholes, hold for $2.0\text{ s}$ post-braking before restoring previous mode, and auto-bypass when connected to Garmin Varia RearVue 820.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-035` (*Smartphone IMU/GPS Deceleration Brake-Light Trigger for BLE & ANT+ [0x23] Radar Tail Lights*)
* **Test Mapping**: `TST-CON-035` (*Smartphone IMU/GPS Deceleration Brake-Light Trigger Test Suite*)
  - `REQ-CON-035.1` / `TST-CON-035.1`: Linear acceleration & attitude fusion deceleration trigger ($a \le -2.0\text{ m/s}^2$, $\ge 250\text{ ms}$, $v \ge 2.5\text{ m/s}$, $dv/dt \le 0$).
  - `REQ-CON-035.2` / `TST-CON-035.2`: Road bump and vertical shock rejection ($|a_v| > 4.0\text{ m/s}^2$ without sustained longitudinal deceleration).
  - `REQ-CON-035.3` / `TST-CON-035.3`: Immediate priority override of rate limits, continuous hold during braking, and $2.0\text{ s}$ post-brake hold before pre-brake restoration.
  - `REQ-CON-035.4` / `TST-CON-035.4`: StVZO steady clamping invariant (`DAY_FLASH` strictly clamped to `SOLID`, zero flashing).
  - `REQ-CON-035.5` / `TST-CON-035.5`: Garmin Varia RearVue 820 onboard hardware brake sensor auto-bypass (`010-03180-00`/`010-03180-10`).
  - `REQ-CON-035.6` / `TST-CON-035.6`: Preference persistence (`radarBrakeLightEnabled`) & 100% 9-language UI localization parity in `strings_radar.xml`.

---

## 3. System Invariants & Preserved Behavior
1. **StVZO Legal Invariant**: Under StVZO certification (`isStvzo == true`), brake activation MUST strictly clamp to `RadarLightMode.SOLID` (never flash).
2. **Pothole & Shock Immunity**: Road bumps and potholes inducing vertical shock without sustained longitudinal deceleration must NEVER trigger brake light.
3. **Pre-Brake Restoration**: System accurately restores exact operating mode active prior to braking ($2.0\text{ s}$ post-brake hold window).
4. **Queue Rate-Limit Priority Exception**: Braking immediately overrides `SmartRadarLightEngine` rate limits for safety, resuming normal limits post-restoration.
5. **Zero SQLite Migrations**: Persisted in `TuningPreferencesDataStore`; `DB_VERSION` unchanged.

---

## 4. Proposed Architectural Changes
### Component 1: SmartBrakeLightDetector (`SmartBrakeLightDetector.kt`)
- Fuses `Sensor.TYPE_LINEAR_ACCELERATION` ($\vec{a}_{\text{lin}}$) and `Sensor.TYPE_GRAVITY` ($\hat{g}$) for dynamic attitude compensation.
- Isolates vertical shock $a_v = \vec{a}_{\text{lin}} \cdot \hat{g}$ and forward deceleration $a_{\text{long}} = \vec{a}_{\text{lin}} \cdot \hat{f}$, cross-referenced with speed ($v \ge 2.5\text{ m/s}$) and $dv/dt \le 0$.
- Emits confirmed Brake Event to `RadarLightController` when $a_{\text{long}} \le -2.0\text{ m/s}^2$ sustained for $\ge 250\text{ ms}$.

### Component 2: RadarLightController Extension (`RadarLightController.kt`)
- Adds `triggerBrakeLight(active: Boolean, brakeMode: RadarLightMode?, nowMs: Long)` with immediate dispatch priority override.
- Maintains `preBrakeMode` and handles $2.0\text{ s}$ post-brake hold window before smoothly restoring previous operating mode.
- Identifies Garmin Varia RearVue 820 (`010-03180-00`/`010-03180-10`) to auto-bypass software trigger.

### Component 3: Preferences & Settings UI (`TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`)
- Adds `radarBrakeLightEnabled` preference key in `TuningPreferencesDataStore.kt`.
- Renders brake light toggle in `RadarSettingsSection.kt` under Tail Light settings card.
- Adds 9-language string resources across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### UI Consistency (Rule 23)
- **Reference screen**: `RadarSettingsSection.kt` Tail Light Settings Card.
- **Reused components**: Compose `ElevatedCard`, `Row`, `Text`, `Switch`.
- **Theme tokens**: `MaterialTheme.typography.bodyLarge`/`bodySmall`, `MaterialTheme.colorScheme.onSurface`/`onSurfaceVariant`.

---

## 5. Step-by-Step Implementation Sequence
### Step 1: SmartBrakeLightDetector (`REQ-CON-035.1-2`, `TST-CON-035.1-2`)
* Target: `SmartBrakeLightDetector.kt`, `SmartBrakeLightDetectorTest.kt` (attitude fusion, duration gating, shock rejection).

### Step 2: RadarLightController Brake Priority & Hold (`REQ-CON-035.3-5`, `TST-CON-035.3-5`)
* Target: `RadarLightController.kt`, `RadarLightControllerTest.kt` (priority override, 2.0s hold, StVZO clamp, auto-bypass).

### Step 3: Preferences, Settings UI & 9-Language Parity (`REQ-CON-035.6-7`, `TST-CON-035.6-7`)
* Target: `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`, `TranslationParityTest.kt`.

---

## 6. Verification & Rollback Plan
* **Verification**: Run `SmartBrakeLightDetectorTest`, `RadarLightControllerTest`, and `TranslationParityTest`.
* **Rollback**: Working tree branch isolation (`git checkout subagent-ATT-5121...`).
