# Stage 1 Analysis: ATT-5121 - [Feature] Implement smartphone IMU/GPS deceleration brake-light trigger for BLE and ANT+ (0x23) radar tail lights lacking hardware brake sensors

**Ticket**: [ATT-5121](https://rainerblind.atlassian.net/browse/ATT-5121)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Branch**: `subagent-ATT-5121---ATT-1286-Pro-Agent-self-13a798a7`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Controllable cycling radar tail lights such as Garmin Varia RTL515, RTL516, RTL510, Bryton Gardia R300L, and Magene L508 lack an integrated accelerometer brake sensor. Cyclists riding with these devices require an automated, low-latency brake light trigger during sharp deceleration to signal following traffic and group riders without reaching behind the saddle or pressing buttons. Because smartphones mounted on handlebars or carried on rides possess high-frequency IMU sensors (`Sensor.TYPE_LINEAR_ACCELERATION`, `Sensor.TYPE_GRAVITY`) and GPS/wheel-speed telemetry, *aTrainingTracker* can provide an intelligent software deceleration brake-light controller.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534, Rules 69 & 70)**:
  * *Audit of Hypothesis against Live Codebase*:
    1. **Sensor Fusion Deceleration Detection**: `Sensor.TYPE_LINEAR_ACCELERATION` provides 3-axis motion $\vec{a}_{\text{lin}}$ with gravity removed. `Sensor.TYPE_GRAVITY` defines down-vector $\hat{g}$, enabling attitude compensation for handlebar mounts. Horizontal motion $\vec{a}_h = \vec{a}_{\text{lin}} - (\vec{a}_{\text{lin}} \cdot \hat{g})\hat{g}$ is cross-validated against speed drop ($v \ge 2.5\text{ m/s}$ [$9\text{ km/h}$], $dv/dt \le 0$). Forward deceleration $a \le -2.0\text{ m/s}^2$ sustained for $\ge 250\text{ ms}$ triggers a confirmed Brake Event.
    2. **Pothole & Vertical Shock Rejection**: Road bumps and potholes induce high-frequency vertical acceleration spikes ($|a_v| = |\vec{a}_{\text{lin}} \cdot \hat{g}| \gg 0$) lasting $< 80\text{ ms}$ without sustained forward deceleration or speed drop. Enforcing duration $\ge 250\text{ ms}$, speed threshold $v \ge 2.5\text{ m/s}$, and vertical shock rejection eliminates false positives.
    3. **Controller Priority Override & 2.0s Hold**: `SmartRadarLightEngine` rate-limits commands to $\ge 1.5\text{ s}$. Safety-critical braking must immediately override rate-limiting via `RadarLightController` to activate high-visibility mode (`SOLID` or `DAY_FLASH`), hold through deceleration $+ 2.0\text{ s}$ post-brake hold window, and smoothly restore the previous mode (`preBrakeMode`).
    4. **Hardware Auto-Bypass**: Garmin Varia RearVue 820 (`010-03180-00`/`010-03180-10`) features onboard hardware brake sensing. When RearVue 820 is detected via BLE (`0x2A24`) or ANT+ (`0x50`), software braking automatically defaults to bypassed to prevent redundant actuation.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals (ATT-5121 - 2 SP)**:
  * Implement `SmartBrakeLightDetector.kt` fusing linear acceleration, gravity, and speed/dv/dt with pothole rejection ($a \le -2.0\text{ m/s}^2$ for $\ge 250\text{ ms}$ at $v \ge 2.5\text{ m/s}$).
  * Extend `RadarLightController.kt` with immediate priority brake-light override, $2.0\text{ s}$ post-brake hold timer, and pre-brake mode restoration.
  * Implement RearVue 820 auto-bypass check based on BLE model number and ANT+ manufacturer/product telemetry.
  * Add `radarBrakeLightEnabled` preference in `TuningPreferencesDataStore.kt` and toggle in `RadarSettingsSection.kt` with 100% 9-language localization parity.
  * Author unit test suite verifying brake activation, pothole rejection, hold window, StVZO fallback, and auto-bypass.
* **Out-of-Scope Non-Goals**:
  * Headlight mesh networking or turn indicator signals (`ATT-5122`).
  * Radar RF target tracking alterations (governed by `REQ-CON-028`/`REQ-CON-030`/`REQ-CON-032`).
  * Zero SQLite schema changes (`DB_VERSION` unchanged).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shard**: `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Refined Requirements**: Net-new `REQ-CON-035` (*Smartphone IMU/GPS Deceleration Brake-Light Trigger for BLE & ANT+ Radar Tail Lights*) and `TST-CON-035`.
* **Chesterton's Fence Audit**:
  - `REQ-CON-031.2` (*StVZO Capability Pruning*): StVZO strictly prohibits flashing bicycle lights. When `isStvzo == true`, brake mode MUST clamp `DAY_FLASH` to `SOLID` (5 lm StVZO steady).
  - `REQ-CON-034.5` (*Command Rate-Limiting*): Rate-limiting ($\ge 1.5\text{ s}$) protects peripheral queues; emergency braking preempts rate limits on entry, but normal rate-limiting and threat evaluation resume upon post-brake restoration.

---

## 5. Architectural Strategy & High-Level Solution
```
[ Sensor.TYPE_LINEAR_ACCELERATION ] ──┐
[ Sensor.TYPE_GRAVITY ] ─────────────┼──► [ SmartBrakeLightDetector ]
[ Speed & Location (v, dv/dt) ] ─────┘          │ (a <= -2.0 m/s², >= 250ms, v >= 2.5 m/s)
                                                ▼ (Immediate Priority Override)
                                     [ RadarLightController ] ──► SOLID / DAY_FLASH (clamped if StVZO)
                                                │ (Deceleration ceases + 2.0s hold)
                                                ▼
                                     [ Restore Pre-Brake Mode ] ──► Resume SmartRadarLightEngine
```

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. StVZO Safety Invariant: StVZO devices (`010-03180-10`, `RTL516`) MUST clamp brake mode to steady `SOLID` (never flash).
  2. Shock & Pothole Immunity: Vertical shock spikes ($|a_v| > 4.0\text{ m/s}^2$) without sustained longitudinal deceleration must NEVER trigger the brake light.
  3. Pre-Brake Restoration: Exactly $2.0\text{ s}$ after deceleration ceases, previous active mode (`OFF`, `PELOTON`, `NIGHT_FLASH`, etc.) is restored.
  4. RearVue 820 Auto-Bypass: Devices with onboard hardware brake sensors default to bypassed unless explicitly enabled by user.
  5. 100% 9-language localization parity in `strings_radar.xml`.
* **Risk Rating**: **LOW** (Mathematical signal fusion and controller priority override; zero SQLite changes).
