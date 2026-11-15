# Stage 2: Requirement & Test Specification - ATT-7991: Unify Radar Tail Light Mode & Auto Persistence

**Ticket**: [ATT-7991](https://rainerblind.atlassian.net/browse/ATT-7991) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Radar*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7991`  
**Requirement Mapping**: `REQ-CON-031` (`REQ-CON-031.3/4/5/8`) | **Test Spec ID**: `TST-CON-031` (`TST-CON-031.3/4/5/9`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-CON-031 Refinement)

### 1.1 Problem Statement & Rationale
Radar tail light operating mode is split across a 3-way Expert Settings control (`MANUAL`, `FIXED_WORKOUT_MODE`, `SMART_AUTOMATION`) and live overrides in `RadarLightQuickSwitchSheet` and `EditDeviceDialog`. Live overrides trap smart automation permanently, while workout stops couple shutdown to setting erasure. The system must unify persistence around a single state (`Automatik` + physical modes), retire redundant Expert Settings mode controls (`Design Guidelines §3.2`), and debounce mode stepping (~400 ms) to protect the GATT queue.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional / Architecture | **Verification Method**: Unit Test / Integration Test

### 1.3 Functional & Architectural Requirements
1. `TuningPreferencesDataStore` SHALL persist tail light preference as unified selection: `isAutoMode: Boolean` (default `true`) and `radarLightDefaultMode: RadarLightMode` (default `DAY_FLASH`), with reactive flows and defensive migration from legacy `RadarLightControlMode`.
2. `RadarLightController` and `RadarState` SHALL expose distinct reactive `isAutoMode: StateFlow<Boolean>` and `currentMode: StateFlow<RadarLightMode>`.
3. `RadarLightController.cycleNextMode()` SHALL step non-StVZO (`AUTO` -> `DAY_FLASH` -> `SOLID` -> `PELOTON` -> `NIGHT_FLASH` -> `OFF` -> `AUTO`) or StVZO (`AUTO` -> `SOLID` -> `OFF` -> `AUTO`), updating UI immediately and debouncing hardware writes over ~400 ms.
4. On cycling workout start, the system SHALL activate persisted selection (`AUTO` starts `SmartRadarLightEngine`; explicit mode dispatches directly). On workout stop, the system SHALL command `OFF` without erasing persisted user preference.
5. `RadarLightQuickSwitchSheet.kt` and `EditDeviceDialog.kt` SHALL expose `Automatik` as the first selectable option with live active mode indication. `RadarSettingsSection.kt` SHALL retire redundant 3-way mode controls and dropdown, retaining `radarBrakeLightEnabled`.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* cyclist selects `Tag-Blinken` (or `Automatik`), *When* workout stops and starts later, *Then* tail light turns `OFF` on stop, and automatically resumes selection on next start.
* *Given* connected non-StVZO light in `Automatik`, *When* `cycleNextMode()` is tapped 3 times in 250 ms, *Then* UI state updates immediately on each step, and only one hardware command dispatches after ~400 ms.
* *Given* `Automatik` selected, *When* `SmartRadarLightEngine` escalates due to traffic, *Then* `isAutoMode` remains `true` while `currentMode` reactively emits the active physical mode.
* *Given* user opens Expert Settings, `RadarLightQuickSwitchSheet`, or `EditDeviceDialog`, *When* inspecting controls, *Then* legacy 3-way controls are absent from Expert Settings, `radarBrakeLightEnabled` remains, and `Automatik` is selectable in both sheets.

### 1.5 System Invariants
100% 9-language translation parity; zero GATT queue flooding via ~400 ms debouncing; StVZO mode pruning preserved; zero SQLite schema changes.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-031.3` (Quick-Switch Sheet), `REQ-CON-031.4` (Workout Lifecycle), `REQ-CON-031.5` (Settings & Device Controls)
* **Historical Origin & Commit Trace**: Introduced in `ATT-5102` (`c1d27049`), refined in `ATT-6707` (`fe09f672`), `ATT-7545` (`6bfac996`), `ATT-7540`, and `ATT-7538` (`b645651f`).
* **Root Reason for Existing Formulation**: `ATT-7538` added a 3-way Expert Settings mode (`MANUAL`, `FIXED_WORKOUT_MODE`, `SMART_AUTOMATION`) to eliminate boolean race conditions, but isolated `Automatik` in settings, leaving live sheets as one-way manual traps with no return path and coupling workout stop to setting erasure.
* **Preservation of Core Invariants**: StVZO regulatory compliance (`isStvzo` skips flashing modes); non-blocking GATT FIFO queueing with ~400 ms debounce; safety illumination during pause; independent deceleration brake light (`radarBrakeLightEnabled`); backward-compatible preference migration; 100% 9-language translation parity.

---

## 2. Test Specification (TST-CON-031 Refinement)

### Test Case 1: `testDataStore_legacyControlModeMigration` (`TST-CON-031.5a`)
* **Scope**: Unit Test (`TuningPreferencesDataStoreRadarLightControlModeTest.kt`)
* **Expected Result**: Migrates legacy prefs to `isAutoMode = true`/`false` with mode preserved; defaults are `true` and `DAY_FLASH`.

### Test Case 2: `testRadarLightController_debouncedCycleNextMode_andStvzo` (`TST-CON-031.9a`)
* **Scope**: Unit Test (`RadarLightControllerTest.kt`)
* **Expected Result**: Non-StVZO cycles 6 modes; StVZO cycles 3 modes. UI updates instantly; GATT command dispatches once after ~400 ms.

### Test Case 3: `testRadarLightController_workoutStartStop_persistsAcrossRides` (`TST-CON-031.4a`)
* **Scope**: Unit Test (`RadarLightControllerTest.kt`)
* **Expected Result**: Commands `OFF` on stop; start restores persisted mode without preference erasure.

### Test Case 4: `testRadarSettingsSection_retiresModeControls_retainsBrakeLight` (`TST-CON-031.5b`)
* **Scope**: Compose UI Test (`RadarSettingsSectionTest.kt`)
* **Expected Result**: Legacy 3-way selector and dropdown are absent; brake light toggle is present.

### Test Case 5: `testQuickSwitchAndEditDevice_exposesAutomatik` (`TST-CON-031.3a`, `TST-CON-031.5c`)
* **Scope**: Compose UI Test (`RadarLightQuickSwitchSheetTest.kt`, `EditDeviceDialogTest.kt`)
* **Expected Result**: `Automatik` is first option, indicates active state, and updates `RadarLightController`.

### Test Case 6: 9-Language Localization & Specifier Audit (`TST-CON-031.7`)
* **Scope**: `TranslationParityTest` asserting 100% parity across `values/` and `values-{de,es,fr,it,ja,nl,pl,pt}/strings_radar.xml`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-031.5a` | Unit | `TuningPreferencesDataStore` migration | `REQ-CON-031.5` | Specified |
| `TST-CON-031.9a` | Unit | `RadarLightController.cycleNextMode` | `REQ-CON-031.8` | Specified |
| `TST-CON-031.4a` | Unit | `RadarLightController.onWorkout*` | `REQ-CON-031.4` | Specified |
| `TST-CON-031.5b` | Compose | `RadarSettingsSection` | `REQ-CON-031.5` | Specified |
| `TST-CON-031.3a` | Compose | `RadarLightQuickSwitchSheet` / `EditDeviceDialog` | `REQ-CON-031.3`, `REQ-CON-031.5` | Specified |
| `TST-CON-031.7` | Localization | `TranslationParityTest` | `REQ-CON-031.7` | Specified |
| `TST-CON-031.8` | Regression | Targeted package test suite | `REQ-PRO-001` | Specified |