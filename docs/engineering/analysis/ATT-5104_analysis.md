# Stage 1 Analysis: ATT-5104 - Automate radar tail light modes based on ambient light sensor (tunnels/dusk), GPS sunrise/sunset, and approaching traffic

**Ticket**: [ATT-5104](https://rainerblind.atlassian.net/browse/ATT-5104)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-5104`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Cycling tail lights require distinct visibility profiles depending on environmental illuminance and traffic threats:
1. **Environmental Adaptation**: Bright daylight demands high-visibility flash (Day Flash 100 lm) or power-saving off, while night riding requires steady/low-intensity modes (Solid 25 lm or Peloton 8 lm) to avoid dazzling motorists or group riders. Sudden dark tunnels require rapid (< 2s) illumination.
2. **False Trigger Suppression**: Phones stowed in jersey pockets or bags experience 0 lx darkness; ambient sensors (`Sensor.TYPE_LIGHT`) must not trigger false "tunnel" modes when the screen is off or the proximity sensor reports `NEAR`.
3. **Traffic Threat Escalation**: When rear radar detects approaching or in-lane vehicles, tail light intensity should automatically escalate without driver dazzle (anti-glare cap at night) and without flooding BLE/ANT+ command queues.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  * *Audit of Hypothesis against Codebase*:
    1. **Sensor Gating & Pocket Protection**: `Sensor.TYPE_LIGHT` reports real-time illuminance, but drops to 0 lx when occluded. Gating ambient readings behind `PowerManager.isInteractive` (screen ON) and `Sensor.TYPE_PROXIMITY` (`FAR`) prevents jersey pocket occlusion from falsely triggering tunnel mode.
    2. **Astronomical Solar Fallback**: When screen is off or proximity is `NEAR`, astronomical solar elevation angle computed from GPS (`LATITUDE`, `LONGITUDE`) and timestamp provides an authoritative day vs. dusk/night classification ($\text{elevation} < -3.0^\circ$ = dusk/night).
    3. **3-Stage Threat Escalation Matrix**:
       - *Night / Dusk (Strict Anti-Glare Cap)*: Stage 0 (None) $\rightarrow$ `PELOTON` (8 lm); Stage 1 (Approaching) $\rightarrow$ `SOLID` (25 lm); Stage 2 (Fast Approaching / In-Lane) $\rightarrow$ `NIGHT_FLASH` (40 lm, never 100 lm Day Flash).
       - *Daytime*: Stage 0 $\rightarrow$ `OFF` (or `PELOTON`); Stage 1 $\rightarrow$ `SOLID` / `DAY_FLASH`; Stage 2 $\rightarrow$ `DAY_FLASH` (100 lm).
    4. **Rate-Limiting & Post-Pass Hysteresis**: Rapid 4–8 Hz radar frame updates must not flood BLE GATT queues (`BTLERadarDevice`) or ANT+ 8 Hz ACK channels (`ANTBikeLightDevice`). Enforcing a minimum command interval of $\ge 1.5\text{ s}$ and a 3–5s cooldown hysteresis post-threat prevents command churn.
    5. **Manual Cockpit Override**: Tapping manual modes in `RadarLightQuickSwitchSheet` overrides automation until the workout ends or user resumes smart mode.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit
* **In-Scope Goals (ATT-5104 - 2 SP)**:
  * Implement `SolarElevationCalculator.kt` computing solar elevation angle from GPS coordinates and epoch time.
  * Implement `SmartRadarLightEngine.kt` managing ambient light monitoring, tunnel detection, pocket gating, 3-stage escalation, and command rate-limiting.
  * Integrate with `RadarLightController.kt` to dispatch mode commands to active BLE or ANT+ hardware.
  * Add `radarSmartLightAutomation` preference to `TuningPreferencesDataStore.kt` and UI toggle in `RadarSettingsSection.kt` (100% 9-language localized).
  * Unit test suite verifying tunnel detection, pocket gating, solar angle, and escalation rate-limiting.
* **Out-of-Scope Non-Goals**:
  * IMU deceleration brake-light trigger (`ATT-5121`).
  * Front headlight network mesh coordination (`ATT-5122`).
  * Zero database migrations (`DB_VERSION` unchanged).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shard**: `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Refined Requirements**: Net-new `REQ-CON-034` (*Environmental & Traffic-Adaptive Smart Tail Light Mode Automation*) and `TST-CON-034`.
* **Chesterton's Fence**: `RadarLightController` StVZO pruning (mandatory clamp of flashing modes to `SOLID` on German models) and serialized non-blocking GATT/ANT+ queueing must remain strictly intact.

---

## 5. Architectural Strategy & High-Level Solution
```
[ Ambient Light (TYPE_LIGHT) ] ── (gated by Screen ON & Proximity FAR) ──┐
[ GPS Lat/Lon + Time ] ──────────► [ SolarElevationCalculator ] ─────────┼──► [ SmartRadarLightEngine ]
[ RadarStateRepository ] ────────► 3-Stage Threat Escalation ────────────┘              │
                                                                           Rate-limited (>= 1.5s)
                                                                                        ▼
                                                                           [ RadarLightController ]
                                                                                        │
                                                                       ┌────────────────┴────────────────┐
                                                                       ▼                                 ▼
                                                            [ BTLERadarDevice ]              [ ANTBikeLightDevice ]
```

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Pocket/Screen-off immunity: 0 lx pocket occlusion never triggers tunnel mode; falls back to GPS solar elevation.
  2. Anti-glare night protection: 100 lm Day Flash is strictly forbidden during night/dusk profile.
  3. Non-blocking rate-limiting: Dispatches rate-limited to $\ge 1.5\text{ s}$ with 3–5s cooldown hysteresis.
  4. 100% 9-language translation parity in `strings_radar.xml`.
* **Risk Rating**: **LOW** (Pure calculation and controller orchestration; zero SQLite changes).
