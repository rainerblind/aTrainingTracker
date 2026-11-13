# Stage 1 Analysis: ATT-7538 - 3-Way Radar Tail Light Control Mode Selector

**Ticket**: [ATT-7538](https://rainerblind.atlassian.net/browse/ATT-7538)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7538`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
In `RadarSettingsSection.kt`, tail light configuration currently displays a `Standard-Lichtmodus` dropdown alongside two independent switches: `radarAutoLightControl` (*Licht beim Workout automatisch schalten*) and `radarSmartLightAutomation` (*Intelligente Lichtautomatik*). This produces UI ambiguity and conflicting runtime states:
1. **Conflicting Runtime Race**: When both switches are enabled, `RadarLightController.onWorkoutStarted()` dispatches `setLightMode(defaultMode)` and milliseconds later starts `SmartRadarLightEngine`, immediately overriding the user's default mode.
2. **Dead UI Control**: When `radarAutoLightControl` is `false`, the prominent `Standard-Lichtmodus` dropdown is still displayed despite never being applied on workout start.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  - `TuningPreferencesDataStore.kt:443-446`: Stores `radarLightDefaultMode`, `radarAutoLightControl`, and `radarSmartLightAutomation` as disjoint boolean/enum keys.
  - `RadarSettingsSection.kt:397-434`: Renders the default mode dropdown followed by two separate `TuningToggleItem` toggles without mutual exclusion.
  - `RadarLightController.kt:261-272`: `onWorkoutStarted()` sequentially executes `if (autoControl) setLightMode(defaultMode)` and then unconditionally starts `SmartRadarLightEngine.start(ctx)` if cycling, triggering immediate state overwriting.
  - **3-Way Unified Architecture**: Unify into `enum class RadarLightControlMode { MANUAL, FIXED_WORKOUT_MODE, SMART_AUTOMATION }`.
  - **Conditional UI Gating**: Display `Standard-Lichtmodus` dropdown strictly when `controlMode == FIXED_WORKOUT_MODE`. For `MANUAL` and `SMART_AUTOMATION`, hide the dropdown and display a concise explanatory summary.
  - **Decoupled Features**: Deceleration brake light (`radarBrakeLightEnabled`) remains a fully independent toggle.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Introduce `RadarLightControlMode` in `TuningPreferencesDataStore` with backward-compatible migration for existing installations.
  - In `RadarSettingsSection.kt`, replace the two conflicting switches with a 3-choice `TuningSegmentedButtonRow` and conditionally gate the default mode dropdown.
  - Update `RadarLightController` workout hooks (`onWorkoutStarted`, `onWorkoutResumed`, `onWorkoutStopped`) to execute mutual exclusion without racing.
  - Maintain 100% 9-language translation parity in `strings_radar.xml`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying internal smart light algorithms in `SmartRadarLightEngine.kt`.
  - Modifying live cockpit quick switch sheet (`RadarLightQuickSwitchSheet.kt`) or `EditDeviceDialog.kt` manual controls.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-CON-031.5` (*Tail Light Settings Configuration*) and `REQ-CON-034.6` (*Smart Light Automation*).
* **Historical Origin & Commit Trace**: `ATT-5102` (`69fec8e7`) introduced basic auto-switching; `ATT-5104` added smart automation as an ad-hoc second switch without refactoring the state machine.
* **Preservation of Core Invariants**: Existing user preference values must seamlessly migrate without losing configuration; old keys kept backward-compatible; manual in-ride cockpit overrides remain fully functional.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-031.5`, `REQ-CON-034.6` (in `docs/requirements/req_tracking_cockpit.md`).
* **Affected Architecture Elements**: `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, `RadarLightController.kt`.
* **Verification Impact & Test Surface**: `RadarLightControllerTest.kt`, `TuningPreferencesDataStoreTest.kt`, `RadarSettingsSectionTest.kt`, `TranslationParityTest.kt`.
* **Risk Assessment & Invariants**: Low-to-Medium risk (preference schema migration and state machine dispatch).

---

## 6. Architectural Strategy & High-Level Solution
- Model: `RadarLightControlMode { MANUAL, FIXED_WORKOUT_MODE, SMART_AUTOMATION }` in `TuningPreferencesDataStore.kt`.
- Migration: Read `KEY_RADAR_LIGHT_CONTROL_MODE` if present; fallback: `smartLight -> SMART_AUTOMATION`, `autoLight -> FIXED_WORKOUT_MODE`, else `MANUAL`. Sync legacy keys for backward compatibility.
- UI: Segmented button row with 3 modes; show default mode dropdown only when `FIXED_WORKOUT_MODE`.
- Controller: In `onWorkoutStarted()`, branch cleanly: `MANUAL` -> no-op; `FIXED_WORKOUT_MODE` -> `setLightMode(defaultMode)`; `SMART_AUTOMATION` -> `SmartRadarLightEngine.start()`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. No double-dispatch or racing mode commands on workout start.
  2. `radarBrakeLightEnabled` operates independently regardless of selected light control mode.
  3. Clean downgrade and upgrade path with zero lost preferences.
  4. 100% 9-language translation parity.
* **Risk Rating**: **LOW** (UI consolidation and preference enum unification).
