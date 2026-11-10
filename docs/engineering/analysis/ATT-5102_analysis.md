# Stage 1 Analysis: ATT-5102 - BLE Radar Tail Light Mode Control & Workout Auto-Switch

**Ticket**: [ATT-5102](https://rainerblind.atlassian.net/browse/ATT-5102)  
**Sub-task**: [ATT-6613](https://rainerblind.atlassian.net/browse/ATT-6613) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5102`  
**Requirement Mapping**: `REQ-CON-031` | **Test Mapping**: `TST-CON-031`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Modern cycling radar tail lights like the Garmin Varia RearVue 820 (`010-03180-00` international & `010-03180-10` StVZO) support direct light mode control over Bluetooth LE (BLE). Cyclists currently lack a handlebar interface in *aTrainingTracker* to:
1. **In-Ride Quick Switching**: Switch between *Day Flash* (100 lm), *Night Flash* (40 lm), *Solid* (25 lm), *Peloton* (8 lm group ride), and *Radar-Only (Light Off)* without reaching behind the seatpost.
2. **Workout Lifecycle Automation**: Automatically turn the tail light on when starting a workout and off/standby when finishing, preventing battery drain and forgotten lights.
3. **Companion Integration**: Control the light directly over the existing `BTLERadarDevice` BLE GATT link without requiring duplicate device pairing or separate database rows.

---

## 2. Root Cause Analysis, Gap Analysis & Independent Peer Review
* **Architectural Gap**: `BTLERadarDevice.java` exclusively demuxes radar threat packets, ignoring the light control characteristic (`6a4e3202-667b-11e3-949a-0800200c9a66`) on service `6a4e3200`. The Cockpit status badge has no interaction target, and `RadarSettingsSection` lacks tail light preferences.
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  - *Serialized GATT Pipeline*: Light mode writes must use `MyBTLEDevice.enqueueWriteCharacteristic(...)` from `ATT-5083` to prevent colliding with CCCD notifications or battery queries.
  - *StVZO Compliance & Capability Pruning*: If connected to an StVZO variant, flashing modes (`DAY_FLASH`, `NIGHT_FLASH`, `PELOTON`) must be pruned, exposing only `SOLID` and `OFF`. If connected to radar-only hardware (e.g. `RVR315` or legacy `RTL515` without BLE light service), light UI must gracefully hide.
  - *Workout Automation Coupling*: `TrackerService` must dispatch start/stop hooks to `RadarLightController`, honoring `radarAutoLightControl` preferences.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit
* **In-Scope Goals (ATT-5102 - 2 SP)**:
  - Discover and bind light control characteristic `6a4e3202` on `BTLERadarDevice`.
  - Introduce `RadarLightMode` enum (`OFF`, `DAY_FLASH`, `NIGHT_FLASH`, `SOLID`, `PELOTON`) and `RadarLightController`.
  - Add Cockpit Quick-Switch sheet triggered by tapping the `RadarEdgeOverlay` status badge.
  - Implement workout start/finish auto-switch automation via `TuningPreferencesDataStore`.
  - Add Tail Light preferences to `RadarSettingsSection.kt` and update `RadarTrafficSimulator.kt`.
  - 100% 9-language localization parity in `strings_radar.xml` (zero inline emojis).
* **Out-of-Scope Non-Goals**:
  - `ATT-5103`: ANT+ Bike Lights profile `0x23` for legacy RTL515/516.
  - `ATT-5104`: Ambient lux / tunnel / GPS solar 3-stage light escalation.
  - `ATT-5121`: Smartphone IMU/GPS deceleration brake-light trigger.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shard**: `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Refined Requirements**: Net-new `REQ-CON-031` (*BLE Radar Tail Light Mode Control & Workout Auto-Switch*) and `TST-CON-031`.
* **Chesterton's Fence**: Radar threat stream processing, 3.5s watchdog, and 1 Hz metric aggregation must not be blocked or delayed by light control GATT writes. All writes are non-blocking and queued via the serialized FIFO queue.

---

## 5. Architectural Strategy & System Invariants
* **Components**: `BTLERadarDevice.java`, `RadarLightController.kt`, `RadarEdgeOverlay.kt`, `RadarSettingsSection.kt`, `TuningPreferencesDataStore.kt`, `TrackerService.java`.
* **Core Invariants**:
  1. Zero duplicate pairing: Single BLE connection serves both radar telemetry and light control.
  2. Non-blocking GATT: All light control commands routed via `enqueueWriteCharacteristic`.
  3. Fail-safe degradation: Missing light characteristic on legacy radars simply disables light UI without affecting radar tracking.
  4. 100% 9-language translation parity in `strings_radar.xml`.
* **Risk Rating**: **LOW** (Standard GATT characteristic write on existing serialized queue; zero SQLite schema migrations).
