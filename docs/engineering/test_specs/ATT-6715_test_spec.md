# Stage 2: Requirement & Test Specification - ATT-6715: DFA a1 Thresholds UI, Artifact Warning Icon & HR ZoneDisplayOptions Parity

**Ticket**: [ATT-6715](https://rainerblind.atlassian.net/browse/ATT-6715)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-061` (`REQ-TRK-061.1`, `REQ-TRK-061.2`, `REQ-TRK-061.3`, `REQ-TRK-061.4`, `REQ-TRK-061.5`)  
**Test Spec ID**: `TST-TRK-061` (`TST-TRK-061.1`, `TST-TRK-061.2`, `TST-TRK-061.3`, `TST-TRK-061.4`, `TST-TRK-061.5`, `TST-TRK-061.6`)  
**Branch**: `feature/ATT-6715` | **Author**: AI Agent 1 (Specifier, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-061)

### 1.1 Problem Statement & Rationale
Follow-up from Sprint `2026-46.1` (ATT-3542) identified five live HRV tracking gaps: uncalled threshold setters in `TuningPreferencesDataStore.kt` without settings UI, hardcoded 0.75/0.50 threshold text in `HrvInfoBottomSheet.kt` lacking `@Preview` composables, ambiguity between rolling-window warm-up and artifact-gated suppression on cockpit tiles, unconditional exposure of HRV metrics on non-HR profiles in `ActivityType.java`, and non-integer formatting of `HRV_RMSSD` alongside missing HR `ZoneDisplayOptions` parity on `HRV_DFA_A1` tiles.

### 1.2 Functional & Architectural Requirements
1. **Activity Profile Gating (`REQ-TRK-061.1`)**: `ActivityType.getSensorTypeArray()` SHALL gate HRV sensors on HR capability: they SHALL be exposed only when the profile contains `SensorType.HR` or `DevicesDatabaseManager.haveHeartRateDevice()`.
2. **Artifact Warning Indicator & RMSSD Formatting (`REQ-TRK-061.2`)**: `SensorFieldState` SHALL provide `isArtifactGated: Boolean`. When `true`, `SensorFieldView.kt` SHALL render a subtle warning icon next to `--.--`. `SensorType.HRV_RMSSD` SHALL use `IntegerFormatter` (`0 ms`).
3. **HR ZoneDisplayOptions Parity (`REQ-TRK-061.3`)**: `TrackingViewModel.getZoneType` SHALL map `HRV_DFA_A1` to the sport's HR zone type (`HR_RUN` / `HR_BIKE`), honoring configured HR `ZoneDisplayOptions` (background, left/right bar, text color, zone gauge).
4. **User-Configurable Threshold Controls & Invariant (`REQ-TRK-061.4`)**: `PhysiologyModelsSection.kt` / `AdvancedTuningScreen.kt` SHALL expose interactive controls for $T_1$ and $T_2$ with direction hints ("Higher = more fractal / aerobic"). `TuningPreferencesDataStore.kt` SHALL enforce $T_1 \ge T_2 + 0.05f$.
5. **Dynamic Info Sheet & Compose Previews (`REQ-TRK-061.5`)**: `HrvInfoBottomSheet.kt` SHALL dynamically display active $T_1 / T_2$ thresholds and provide Light, Dark, and AMOLED `@Preview` composables wrapped in `ATrainingTrackerTheme`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Profile Gating)**: *Given* an activity profile, *When* it lacks `SensorType.HR` and no HR device is paired, *Then* HRV fields are hidden; *When* HR is present/paired, *Then* HRV fields are available.
* **AC 2 (Artifact Warning & RMSSD Format)**: *Given* an active HRV tile, *When* artifact rate $>5.0\%$, *Then* `--.--` displays with an amber warning icon; *When* `HRV_RMSSD` has a value, *Then* it formats as an integer (`0 ms`).
* **AC 3 (ZoneDisplayOptions & Threshold Invariant)**: *Given* custom thresholds, *When* $T_1$ or $T_2$ is set, *Then* $T_1 \ge T_2 + 0.05f$ is strictly enforced, `HRV_DFA_A1` reflects HR `ZoneDisplayOptions`, and `HrvInfoBottomSheet` renders active threshold values.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-TRK-061` (`REQ-TRK-061.1`..`REQ-TRK-061.5`), targeting `SensorFieldView.kt`, `SensorFieldState.kt`, `TrackingViewModel.kt`, `PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `HrvInfoBottomSheet.kt`, `ActivityType.java`, `SensorType.java`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3542` (commit `14292b35206`), refined in Sprint `2026-46.1` follow-up (`ATT-6715`).
3. *Root Reason for Existing Formulation*: ATT-3542 established live telemetry tile rendering. Settings UI controls were deferred, `ActivityType` exposed sensors globally to simplify initial bring-up, and warm-up vs. artifact suppression both rendered identical `--.--` text without icons.
4. *Preservation of Core Invariants*: Population defaults ($0.75 / 0.50$) preserved; theme color centralization (Rule 71); modal bottom sheet scroll safety (Rule 72); 100% clean-room test pass rate and 9-language translation parity strictly preserved.

---

## 3. Test Specification (TST-TRK-061)

### Test Case 1: Sensor Profile Gating Contract Tests (`TST-TRK-061.1`)
* **Scope**: `ActivityTypeTest.kt` / `SensorCategoryAndSelectorContractTest.kt`. Verify that HRV sensor types are omitted from `ActivityType.getSensorTypeArray()` when `SensorType.HR` is absent and no HR device is paired, and present when HR is available.

### Test Case 2: RMSSD Integer Formatter & Artifact-Gated Warning Icon (`TST-TRK-061.2`)
* **Scope**: `SensorTypeHrvTest.kt` / `SensorFieldViewTest.kt`. Verify `SensorType.HRV_RMSSD` uses `IntegerFormatter` emitting `0 ms`, and `isArtifactGated == true` surfaces warning indicator alongside `--.--`.

### Test Case 3: ZoneDisplayOptions Parity & Threshold Invariant Tests (`TST-TRK-061.3`, `TST-TRK-061.4`)
* **Scope**: `TrackingViewModelTest.kt` / `TuningPreferencesDataStoreTest.kt`. Verify `HRV_DFA_A1` inherits HR `ZoneDisplayOptions`, and setting $T_1$ or $T_2$ enforces $T_1 \ge T_2 + 0.05f$.

### Test Case 4: HrvInfoBottomSheet Dynamic Thresholds & Localization Parity (`TST-TRK-061.5`)
* **Scope**: `HrvInfoBottomSheetTest.kt` / `TranslationParityTest.kt`. Verify dynamic threshold string injection, Light/Dark/AMOLED `@Preview` rendering, and 100% 9-language translation parity in `strings_hrv.xml`.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-TRK-061.6`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.build.*"`.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-061.1]` | Contract | Activity profile gating based on HR capability | `REQ-TRK-061.1` | Specified |
| `[TST-TRK-061.2]` | Unit / UI | RMSSD `IntegerFormatter` & `isArtifactGated` icon | `REQ-TRK-061.2` | Specified |
| `[TST-TRK-061.3]` | ViewModel | `HRV_DFA_A1` inherits HR `ZoneDisplayOptions` | `REQ-TRK-061.3` | Specified |
| `[TST-TRK-061.4]` | DataStore | Invariant enforcement $T_1 \ge T_2 + 0.05f$ | `REQ-TRK-061.4` | Specified |
| `[TST-TRK-061.5]` | Compose / i18n | Dynamic thresholds & Light/Dark/AMOLED `@Preview` | `REQ-TRK-061.5` | Specified |
| `[TST-TRK-061.6]` | Regression | Tier 1 clean-room regression across modified packages | `REQ-PRO-001` | Specified |
