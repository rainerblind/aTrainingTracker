# Stage 3: Implementation Plan - ATT-7991: Unify Radar Tail Light Mode & Auto Persistence

**Ticket**: [ATT-7991](https://rainerblind.atlassian.net/browse/ATT-7991) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7991`  
**Requirement Mapping**: `REQ-CON-031.3`, `REQ-CON-031.4`, `REQ-CON-031.5`, `REQ-CON-031.8`  
**Test Mapping**: `TST-CON-031.3`, `TST-CON-031.4`, `TST-CON-031.5`, `TST-CON-031.9`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
Radar tail light controls are fragmented between a 3-way Expert Settings mode (`MANUAL`, `FIXED_WORKOUT_MODE`, `SMART_AUTOMATION`) and live Cockpit/Device overrides that permanently trap automation. The system will unify preference persistence around a single state model (`Automatik` + physical modes), retire redundant mode controls in Expert Settings (`Design Guidelines §3.2`), and debounce mode cycling (~400 ms) to protect the GATT queue.

---

## 2. Traceability & Requirements Mapping
* `REQ-CON-031.5` -> `TST-CON-031.5a`: Unified persistence and migration in `TuningPreferencesDataStore`.
* `REQ-CON-031.8` -> `TST-CON-031.9a`: Debounced `cycleNextMode()` and dual `isAutoMode` / `currentMode` flows.
* `REQ-CON-031.4` -> `TST-CON-031.4a`: Workout start/stop lifecycle persistence preservation in `RadarLightController`.
* `REQ-CON-031.3` / `REQ-CON-031.5` -> `TST-CON-031.3a` / `TST-CON-031.5b/c`: UI updates in `QuickSwitchSheet`, `EditDeviceDialog`, and retirement in `RadarSettingsSection`.

---

## 3. System Invariants & Preserved Behavior
1. **Queue Safety**: ~400 ms debounce prevents GATT / ANT+ 0x23 queue flooding during rapid taps.
2. **StVZO Compliance**: Non-StVZO flashing modes are skipped when `isStvzo == true`.
3. **Safety Illumination**: Tail light is never turned off on workout pause; turns `OFF` on stop without preference reset.
4. **Decoupled Deceleration Brake**: `radarBrakeLightEnabled` remains independent in Expert Settings.

---

## 4. Proposed Architectural Changes
* **Component 1 (`TuningPreferencesDataStore.kt`)**: Persist `radarLightAutoMode: Boolean` (default `true`) and `radarLightDefaultMode: RadarLightMode` (default `DAY_FLASH`). Defensive migration: `SMART_AUTOMATION` -> `true`, `FIXED_WORKOUT_MODE` -> `false`, `MANUAL` -> `false` with `OFF` or last mode.
* **Component 2 (`RadarLightController.kt` & `RadarState.kt`)**: Expose distinct `isAutoMode: StateFlow<Boolean>` and `currentMode: StateFlow<RadarLightMode>`. Implement debounced `cycleNextMode()` (~400 ms delay). Update `onWorkoutStarted` to activate persisted mode, and `onWorkoutStopped` to dispatch `OFF` without wiping preferences.
* **Component 3 (UI Consolidation)**: In `RadarLightQuickSwitchSheet.kt` and `EditDeviceDialog.kt`, add `Automatik` as first option with subtitle showing active physical mode. In `RadarSettingsSection.kt`, retire 3-way segmented button and dropdown; retain `radarBrakeLightEnabled`.
* **UI Consistency (`Rule 23`)**: Reuses `AppModalBottomSheet`, M3 `RadioButton`, `SingleChoiceSegmentedButtonRow(icon = {})`, `TuningToggleItem`, and `Modifier.weight(1f)` for labels per `docs/design_guidelines.md` §1.4, §3.2, §5.3, §5.20.
* **Existing User Migration Behavior (`Rule 5.6`)**: Resolves legacy `KEY_RADAR_LIGHT_CONTROL_MODE` into unified `isAutoMode` and `defaultMode` on first read; fresh installs default to `AUTO` (`isAutoMode = true`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (DataStore Persistence & Migration)**: Update `TuningPreferencesDataStore.kt` with `radarLightAutoMode` flow/mutator, defensive migration, and tests in `TuningPreferencesDataStoreRadarLightControlModeTest.kt`.
2. **Step 2 (Controller Dual Flows & Debounced Cycling)**: Add `isAutoMode` `StateFlow` and ~400 ms debounced `cycleNextMode()` to `RadarLightController.kt` and `RadarState.kt`; update workout start/stop hooks and `RadarLightControllerTest.kt`.
3. **Step 3 (Live Sheets Automatik Option)**: Add `Automatik` option with live active mode subtitle to `RadarLightQuickSwitchSheet.kt` and `EditDeviceDialog.kt`; update Compose tests.
4. **Step 4 (Expert Settings Retirement)**: Remove 3-way selector and dropdown from `RadarSettingsSection.kt`; retain `radarBrakeLightEnabled`; update `RadarSettingsSectionTest.kt`.
5. **Step 5 (Verification Suite)**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*"` and `TranslationParityTest`.

---

## 6. Verification, Risk Classification & Rollback Plan
* **Risk Classification**: **VISUAL/UI** (Touches Compose sheets, device dialog, and settings UI; Stage 4 Doing will run on `subagent_pro` per `Rule 7.1`).
* **Verification**: Tier 1 targeted unit & Compose tests, followed by Clean-Room regression in Stage 5.
* **Rollback**: Atomic Git revert of `feature/ATT-7991` prior to squash merge.
