# Stage 3: Implementation Plan - ATT-7538: 3-Way Radar Tail Light Control Mode Selector

**Ticket**: [ATT-7538](https://rainerblind.atlassian.net/browse/ATT-7538) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5` | **Branch**: `feature/ATT-7538`  
**Requirement Mapping**: `REQ-CON-031` (`REQ-CON-031.4`, `REQ-CON-031.5`), `REQ-CON-034` (`REQ-CON-034.6`)  
**Test Mapping**: `TST-CON-031` (`TST-CON-031.5`), `TST-CON-034` (`TST-CON-034.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
In `RadarSettingsSection.kt`, tail light configuration currently shows two independent switches (`radarAutoLightControl` and `radarSmartLightAutomation`) along with a `Standard-Lichtmodus` dropdown. This causes a runtime race condition where `RadarLightController.onWorkoutStarted()` dispatches the fixed default mode, which `SmartRadarLightEngine` immediately overwrites. Additionally, the dropdown is displayed even when auto-light control is disabled. This plan unifies both switches into a 3-way `RadarLightControlMode` (`MANUAL`, `FIXED_WORKOUT_MODE`, `SMART_AUTOMATION`) with conditional dropdown gating.

---

## 2. Traceability & Requirements Mapping
* **REQ-CON-031.5a,b**: `TuningPreferencesDataStore.kt` defines `enum class RadarLightControlMode { MANUAL, FIXED_WORKOUT_MODE, SMART_AUTOMATION }` with backward-compatible migration (`TST-CON-031.5d`).
* **REQ-CON-031.5c**: `RadarSettingsSection.kt` renders a 3-choice `TuningSegmentedButtonRow` for `RadarLightControlMode` replacing the two legacy switches (`TST-CON-031.5d`).
* **REQ-CON-031.5d**: Default light mode dropdown is conditionally rendered ONLY when `controlMode == FIXED_WORKOUT_MODE` (`TST-CON-031.5d`).
* **REQ-CON-031.4a..c**: `RadarLightController` executes mutual exclusion: `MANUAL` -> no auto dispatch; `FIXED_WORKOUT_MODE` -> fixed mode on start, `OFF` on stop; `SMART_AUTOMATION` -> start smart engine without pre-dispatching default mode (`TST-CON-031.5e`).
* **REQ-CON-031.7**: 100% 9-language localization parity in `strings_radar.xml` (`TST-CON-031.5f`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Preference Loss / Downgrade Safety**: If `radarSmartLightAutomation == true` -> `SMART_AUTOMATION`; else if `radarAutoLightControl == true` -> `FIXED_WORKOUT_MODE`; else -> `MANUAL`. Legacy keys remain updated.
2. **Race-Free Workout Hooks**: `onWorkoutStarted()` never dispatches `defaultMode` when `SMART_AUTOMATION` is active.
3. **Decoupled Features**: Deceleration brake light (`radarBrakeLightEnabled`) remains a completely independent toggle.
4. **StVZO Compliance Invariant**: Mode choices continue to respect StVZO flashing restrictions.

---

## 4. Proposed Architectural Changes (SWE.2)
* **TuningPreferencesDataStore.kt**:
  - Define `RadarLightControlMode { MANUAL, FIXED_WORKOUT_MODE, SMART_AUTOMATION }`.
  - Add `KEY_RADAR_LIGHT_CONTROL_MODE`, migration logic from legacy keys, and `updateRadarLightControlMode()`.
* **RadarSettingsSection.kt**:
  - Replace `radarAutoLightControl` and `radarSmartLightAutomation` toggles with `TuningSegmentedButtonRow`.
  - Conditionally wrap `TuningDropdownRow(radarLightDefaultMode)` inside `if (tuningConfig.radarLightControlMode == RadarLightControlMode.FIXED_WORKOUT_MODE)`.
  - For `MANUAL` and `SMART_AUTOMATION`, display a concise descriptive summary below the segmented button.
* **RadarLightController.kt**:
  - Update `resolveActiveConfig()` to cache `RadarLightControlMode`.
  - In `onWorkoutStarted()`, branch on `controlMode`: `MANUAL` (no-op), `FIXED_WORKOUT_MODE` (dispatch default mode), `SMART_AUTOMATION` (start `SmartRadarLightEngine`).
  - In `onWorkoutStopped()`, stop engine and turn off light appropriately.
* **UI Consistency (`docs/design_guidelines.md`)**:
  - Uses `TuningSegmentedButtonRow` with 3 exclusive choices per §1.1. Single-line German labels per §2.4.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: DataStore Model, Migration & Parity Strings
* Files: `TuningPreferencesDataStore.kt`, `strings_radar.xml` (all 9 languages).
* Add `RadarLightControlMode`, preference key, migration logic, and localized string resources.

### Step 2: RadarSettingsSection UI Consolidation
* Files: `RadarSettingsSection.kt`.
* Replace toggles with `TuningSegmentedButtonRow`; gate `radarLightDefaultMode` dropdown for `FIXED_WORKOUT_MODE`.

### Step 3: RadarLightController Exclusive Dispatch
* Files: `RadarLightController.kt`.
* Update `resolveActiveConfig()` and workout lifecycle hooks (`onWorkoutStarted`, `onWorkoutStopped`).

### Step 4: Unit Testing & Governance Verification
* Files: `TuningPreferencesDataStoreTest.kt`, `RadarLightControllerTest.kt`, `RadarSettingsSectionTest.kt`.
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.radar.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" && python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: All unit tests pass, zero governance violations, 100% translation parity.
* **Rollback**: Revert working branch commits cleanly.
