# Stage 2: Requirement & Test Specification - ATT-7524: Default ZoneDisplayOptions to Background + Left & Right Bars and Persist HRV Display Options

**Ticket**: [ATT-7524](https://rainerblind.atlassian.net/browse/ATT-7524)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-172` (*Configurable Zone Visual Presentation*), `REQ-TRK-061.3` (*Dedicated HRV ZoneDisplayOptions*)  
**Test Spec ID**: `TST-UI-124`, `TST-TRK-061.3`  
**Branch**: `feature/ATT-7524` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-UI-172 & REQ-TRK-061.3)

### 1.1 Problem Statement & Rationale
`ZoneDisplayOptions` defaults to `showBackground = true`, `showLeftBar = true`, `showRightBar = false`, `showTextColor = false`. Enabling both left and right bars symmetrically frames Cockpit telemetry tiles with high glanceability while keeping text color neutral for legibility. Furthermore, the 4th `HRV (DFA a1)` tab in `ZonesSettingsActivity` currently stores toggles only in ephemeral `remember` state, and `TrackingViewModel` maps HRV to HR run/bike zones rather than dedicated HRV display options.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `ZoneDisplayOptions` constructor defaults SHALL be: `showBackground = true`, `showLeftBar = true`, `showRightBar = true`, `showTextColor = false`, `showGauge = false`.
2. `SettingsDataStore` SHALL default missing `rightBarKey` preferences to `true` while strictly preserving explicitly stored `false` user settings.
3. `SettingsDataStore` SHALL persist dedicated `HRV (DFA a1)` display options (`hrv_dfa_a1_zone_show_background`, `show_left_bar`, `show_right_bar`, `show_text_color`, `show_gauge`), expose `hrvZoneDisplayOptionsFlow`, and cache `hrvDisplayOptions` in `SettingsSnapshot`.
4. In `ZonesSettingsActivity.kt`, `HrvDfaA1ZoneSettingsContent` SHALL bind "Zonendarstellung" toggles reactively to `SettingsDataStore`, persisting changes immediately.
5. In `TrackingViewModel.kt`, `SensorType.HRV_DFA_A1` SHALL resolve its `ZoneDisplayOptions` directly from `SettingsSnapshot.hrvDisplayOptions` across all sports (`RUN`, `BIKE`, `UNKNOWN`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Symmetrical Defaults)**: *Given* fresh install or uncustomized DataStore, *When* reading `ZoneDisplayOptions` for `HR_RUN`, `HR_BIKE`, `PWR_BIKE`, or `HRV`, *Then* `showBackground == true`, `showLeftBar == true`, `showRightBar == true`, `showTextColor == false`.
* **Criterion 2 (HRV Persistence & Decoupling)**: *Given* user modifies any toggle on the HRV tab, *When* switching tabs or viewing active `HRV_DFA_A1` in Cockpit under any sport (`RUN`, `BIKE`, `UNKNOWN`), *Then* options persist in DataStore and render identically on the live tile.
* **Criterion 3 (Explicit Preference Preservation)**: *Given* an existing user with explicitly stored `showRightBar = false`, *When* reading DataStore, *Then* `showRightBar == false` is preserved without overwrite.

### 1.5 System Invariants
- 9-language translation parity maintained across all `strings*.xml`.
- Zone color hex palette (`TTColor.Zone1`..`Zone7`) and threshold math ($T_1 \ge T_2 + 0.05$) remain unchanged.

---

## 2. Test Specification (TST-UI-124 & TST-TRK-061.3)

### Test Case 1: Default Constructor & Symmetrical Defaults (`[TST-UI-124.1]`)
* **Scope**: Unit Test (`ZoneDisplayOptionsTest.kt`, `SensorFieldZoneDisplayTest.kt`)
* **Preconditions**: Default instantiation `ZoneDisplayOptions()`.
* **Action**: Assert all 5 boolean properties.
* **Expected Result**: `showBackground = true`, `showLeftBar = true`, `showRightBar = true`, `showTextColor = false`, `showGauge = false`.

### Test Case 2: DataStore HRV Persistence & Explicit False Preservation (`[TST-UI-124.2]`)
* **Scope**: Unit Test (`SettingsDataStoreZoneDisplayTest.kt`, `SettingsDataStoreZoneAtomicTest.kt`)
* **Preconditions**: Uninitialized DataStore vs. DataStore with explicit `right_bar = false`.
* **Action**: Read flows for all profiles and save custom HRV display options.
* **Expected Result**: Unset preferences default to `showRightBar = true`; explicit `false` is preserved; HRV options persist independently.

### Test Case 3: TrackingViewModel Dedicated HRV Resolution (`[TST-TRK-061.3]`)
* **Scope**: Unit Test (`TrackingViewModelHrvTest.kt`)
* **Preconditions**: Sport set to `BSportType.UNKNOWN`, `RUN`, and `BIKE`.
* **Action**: Receive `HRV_DFA_A1` sensor data.
* **Expected Result**: Resolves `ZoneDisplayOptions` from dedicated HRV options for all sport profiles.

### Test Case 4: 9-Language Localization & Specifier Audit (`[TST-UI-124.3]`)
* **Scope**: Unit / Lint (`ZoneDisplayLocalizationTest.kt`, `TranslationParityTest.kt`)
* **Goal**: Verify 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 5: Tier 1 Targeted Regression Suite (`[TST-UI-124.4]`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-124.1]` | Unit | `ZoneDisplayOptions()` | `REQ-UI-172` | Specified |
| `[TST-UI-124.2]` | Unit | `SettingsDataStore.hrvZoneDisplayOptionsFlow` | `REQ-UI-172` | Specified |
| `[TST-TRK-061.3]` | Unit | `TrackingViewModel.calculateZoneColor / displayOptions` | `REQ-TRK-061.3` | Specified |
| `[TST-UI-124.3]` | Localization | `TranslationParityTest` | `REQ-UI-172`, `REQ-UI-106` | Specified |
| `[TST-UI-124.4]` | Regression (Tier 1) | `Settings & Tracking test suites` | `REQ-PRO-001` | Specified |
