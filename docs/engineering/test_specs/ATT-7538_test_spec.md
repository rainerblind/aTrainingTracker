# Stage 2: Requirement & Test Specification - ATT-7538: 3-Way Radar Tail Light Control Mode Selector

**Ticket**: [ATT-7538](https://rainerblind.atlassian.net/browse/ATT-7538)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-CON-031` (`REQ-CON-031.4`, `REQ-CON-031.5`), `REQ-CON-034` (`REQ-CON-034.6`)  
**Test Spec ID**: `TST-CON-031` (`TST-CON-031.5`), `TST-CON-034` (`TST-CON-034.6`) | **Branch**: `feature/ATT-7538`  
**Author**: AI Agent 2 (Flash) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-CON-031.5, REQ-CON-034.6)

### 1.1 Problem Statement & Rationale
`RadarSettingsSection.kt` currently presents two independent switches (`radarAutoLightControl` and `radarSmartLightAutomation`) alongside a `Standard-Lichtmodus` dropdown. When both switches are active, `RadarLightController` commands a fixed mode that `SmartRadarLightEngine` immediately overwrites. When auto-light is disabled, the dropdown remains visible despite being completely ineffective. The UI and backend must unify into a 3-way mutually exclusive `RadarLightControlMode`.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `TuningPreferencesDataStore.kt` SHALL define `enum class RadarLightControlMode { MANUAL, FIXED_WORKOUT_MODE, SMART_AUTOMATION }` (`REQ-CON-031.5a`).
2. Schema migration SHALL map legacy preferences: if `radarSmartLightAutomation == true` -> `SMART_AUTOMATION`; else if `radarAutoLightControl == true` -> `FIXED_WORKOUT_MODE`; else -> `MANUAL`. Legacy keys SHALL remain synchronized for downgrade safety (`REQ-CON-031.5b`).
3. `RadarSettingsSection.kt` SHALL render a 3-choice selector (`TuningSegmentedButtonRow`) for `RadarLightControlMode` replacing the two legacy toggles (`REQ-CON-031.5c`).
4. The `Standard-Lichtmodus` (`radarLightDefaultMode`) dropdown SHALL be rendered conditionally ONLY when `controlMode == FIXED_WORKOUT_MODE`. For `MANUAL` and `SMART_AUTOMATION`, it SHALL be hidden and replaced with a concise descriptive text (`REQ-CON-031.5d`).
5. In `RadarLightController`:
   - `MANUAL`: SHALL NOT dispatch light mode changes on workout start/stop and SHALL NOT start `SmartRadarLightEngine` (`REQ-CON-031.4a`).
   - `FIXED_WORKOUT_MODE`: SHALL dispatch `setLightMode(defaultMode)` on workout start and `OFF` on stop, without starting `SmartRadarLightEngine` (`REQ-CON-031.4b`).
   - `SMART_AUTOMATION`: SHALL start `SmartRadarLightEngine` on workout start without first dispatching a conflicting fixed default mode (`REQ-CON-031.4c`).
6. Deceleration brake light (`radarBrakeLightEnabled`) SHALL remain an independent toggle (`REQ-CON-035.6`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC1 (3-Way Control Mode Selector)**:
  * *Given* the athlete views `RadarSettingsSection`,
  * *When* configuring Tail Light Control,
  * *Then* a 3-way segmented selector (*Manuell | Fester Modus | Automatik*) is displayed.
* **AC2 (Conditional Dropdown Gating)**:
  * *Given* the 3-way control mode selector,
  * *When* `FIXED_WORKOUT_MODE` is selected,
  * *Then* the default light mode dropdown is visible; when `MANUAL` or `SMART_AUTOMATION` is selected, the dropdown is hidden.
* **AC3 (Clean Exclusive Dispatch)**:
  * *Given* `SMART_AUTOMATION` is active,
  * *When* a workout starts,
  * *Then* `SmartRadarLightEngine` controls the light without an initial conflicting dispatch of `defaultMode`.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-CON-031.5`, `REQ-CON-034.6` targeting `RadarSettingsSection.kt`, `TuningPreferencesDataStore.kt`, `RadarLightController.kt`.
* `Historical Origin & Commit Trace`: `ATT-5102` (`3532c253`), `ATT-5104` (`48202d6b`), refined via `ATT-7538`.
* `Root Reason for Existing Formulation`: Auto-light and smart automation were introduced in separate sprints as disjoint boolean flags.
* `Preservation of Core Invariants`: Zero lost athlete preferences via automatic migration; legacy keys preserved read-only; independent brake light functionality intact; 100% 9-language localization parity.

---

## 2. Test Specification (TST-CON-031.5, TST-CON-034.6)

### Test Case 1: Preference Schema Migration & Downgrade Safety (`TST-CON-031.5d`)
* **Scope**: Unit (`TuningPreferencesDataStoreTest.kt`)
* **Verification**: Assert all 4 permutations of legacy flags (`(false, false)`, `(true, false)`, `(false, true)`, `(true, true)`) correctly migrate to `MANUAL`, `FIXED_WORKOUT_MODE`, `SMART_AUTOMATION`, and sync back to legacy keys.

### Test Case 2: Clean Exclusive Controller Dispatch (`TST-CON-031.5e`)
* **Scope**: Unit (`RadarLightControllerTest.kt`)
* **Verification**: Assert workout start under `MANUAL` dispatches nothing, under `FIXED_WORKOUT_MODE` dispatches `defaultMode` without starting smart engine, and under `SMART_AUTOMATION` starts smart engine without pre-dispatching `defaultMode`.

### Test Case 3: 9-Language Localization Parity (`TST-CON-031.5f`)
* **Scope**: Unit / Static (`strings_radar.xml`)
* **Verification**: Assert new string keys exist with 100% parity across all 9 supported locales.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-031.5d` | Unit | `TuningPreferencesDataStoreTest.kt` | `REQ-CON-031.5a,b` | Specified |
| `TST-CON-031.5e` | Unit | `RadarLightControllerTest.kt` | `REQ-CON-031.4a..c` | Specified |
| `TST-CON-031.5f` | Parity | `strings_radar.xml` (9 locales) | `REQ-CON-031.7` | Specified |
