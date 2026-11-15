# Stage 1 Analysis: ATT-7991 - Unify Radar Tail Light Mode & Auto Persistence

**Ticket**: [ATT-7991](https://rainerblind.atlassian.net/browse/ATT-7991) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7991`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
Radar tail light control is split across a 3-way selector (`MANUAL`, `FIXED_WORKOUT_MODE`, `SMART_AUTOMATION`) in Expert Settings (`RadarSettingsSection.kt`) and live overrides in `RadarLightQuickSwitchSheet` and `EditDeviceDialog`. Live overrides suspend automation (`SmartRadarLightEngine.onManualOverride`) with no path back to `Automatik` from the sheet. Cyclists need a single unified light model (`Automatik` + physical modes) remembering preferred state across rides, turning off on workout stop, and retiring redundant Expert Settings mode controls (`Design Guidelines §3.2`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  - `TuningPreferencesDataStore.kt:456-459, 946-955`: Stores disjoint `radarLightDefaultMode` and `radarLightControlMode`. Fresh installs lack unified auto-persistence.
  - `RadarSettingsSection.kt:406-451`: Hosts duplicate 3-way `TuningSegmentedButtonRow` and conditional dropdown.
  - `RadarLightQuickSwitchSheet.kt:145-185` & `EditDeviceDialog.kt:1145-1211`: Only iterate raw `RadarLightMode` entries without `Automatik`; tapping any mode permanently traps automation in suspended state.
  - `RadarLightController.kt:51-70, 224-227`: Exposes only `currentMode`. Missing `isAutoMode` `StateFlow` and debounced `cycleNextMode()`.
  - `RadarLightController.kt:371-396`: Workout stop hooks correctly extinguish hardware but legacy config resolution couples shutdown to settings erasure.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Unified persistence: `isAutoMode: Boolean` (default `true`) + `radarLightDefaultMode: RadarLightMode` in `TuningPreferencesDataStore` with backward migration.
  - Expose distinct reactive `isAutoMode` and `currentMode` `StateFlow`s on `RadarLightController` and `RadarState`.
  - Implement `RadarLightController.cycleNextMode()` with ~400 ms hardware command debounce and StVZO pruning.
  - Workout start/stop: auto-activate persisted choice on cycling start; dispatch `OFF` on stop without overwriting preference.
  - Add `Automatik` entry to `RadarLightQuickSwitchSheet.kt` and `EditDeviceDialog.kt`. Retire mode selector/dropdown from `RadarSettingsSection.kt` while retaining `radarBrakeLightEnabled`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Cockpit Tail Light button integration (reserved for `ATT-7972`).
  - Modifying `SmartRadarLightEngine` lux thresholds or `SmartBrakeLightDetector` IMU kinematics.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-CON-031.3` (Quick-Switch Sheet), `REQ-CON-031.4` (Workout Lifecycle), `REQ-CON-031.5` (Settings & Device Controls).
* **Historical Origin & Commit Trace**: `ATT-5102` (`c1d27049`), `ATT-6707` (`fe09f672`), `ATT-7540`, `ATT-7538` (`b645651f`).
* **Root Reason for Existing Formulation**: `ATT-7538` created the 3-way Expert Settings mode to eliminate conflicting booleans, but isolated `Automatik` in settings, leaving live sheets as one-way manual traps.
* **Preservation of Core Invariants**: StVZO legal compliance (skip flashing modes when `isStvzo == true`); pause/stop illumination safety; independent brake-light trigger (`radarBrakeLightEnabled`); backward-compatible preference migration; 100% 9-language translation parity.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-031.3`, `REQ-CON-031.4`, `REQ-CON-031.5`, `REQ-CON-034.6` in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `TuningPreferencesDataStore.kt`, `RadarLightController.kt`, `SmartRadarLightEngine.kt`, `RadarState.kt`, `RadarLightQuickSwitchSheet.kt`, `EditDeviceDialog.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`.
* **Verification Impact & Test Surface**: `RadarLightControllerTest.kt`, `TuningPreferencesDataStoreRadarLightControlModeTest.kt`, `RadarSettingsSectionTest.kt`, `RadarLightQuickSwitchSheetTest.kt`, `EditDeviceDialogTest.kt`, `TranslationParityTest`.
* **Risk Assessment & Invariants**: Low-to-Medium risk. Hardware command debouncing (~400 ms) protects GATT queue under rapid cycling; defensive preference migration ensures zero configuration loss.

---

## 6. Architectural Strategy & High-Level Solution
- **State Separation**: `RadarLightController` exposes `isAutoMode` (`StateFlow<Boolean>`) and `currentMode` (`StateFlow<RadarLightMode>`). `RadarState` mirrors both.
- **Debounced Cycling**: `cycleNextMode()` updates UI state immediately, stepping non-StVZO (`AUTO` -> `DAY_FLASH` -> `SOLID` -> `PELOTON` -> `NIGHT_FLASH` -> `OFF` -> `AUTO`) or StVZO (`AUTO` -> `SOLID` -> `OFF` -> `AUTO`), debouncing hardware GATT/ANT+ writes over ~400 ms.
- **UI Consolidation**: Add `Automatik` option with live active mode subtitle in `RadarLightQuickSwitchSheet` and `EditDeviceDialog`. Remove redundant mode controls in `RadarSettingsSection.kt`.
- **Lifecycle**: Workout start applies persisted mode (`AUTO` starts smart engine; explicit mode dispatches directly). Workout stop turns light `OFF` without resetting user preference.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero GATT queue flooding; StVZO compliance preserved; pause illumination preserved; zero SQLite schema change.
* **Risk Rating**: **LOW** (Targeted state-machine consolidation with zero database schema impact).
