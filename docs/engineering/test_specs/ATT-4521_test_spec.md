# Stage 2: Requirement & Test Specification - ATT-4521: [Verbesserung] Update default Cycling FTP (240 W) and Heart Rate / Power zone boundaries to realistic, calculator-aligned baselines

**Ticket**: [ATT-4521](https://rainerblind.atlassian.net/browse/ATT-4521) | **Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4521`  
**Requirement Mapping**: `REQ-UI-426` (amending `REQ-UI-370`, `REQ-UI-373`) | **Test Spec ID**: `TST-UI-391`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-UI-426)

### 1.1 Problem Statement & Rationale
Default training zone thresholds across `SettingsDataStore.kt` and `ZoneCalculatorDialog.kt` are discordant:
* `DEFAULT_CYCLING_FTP` is 200 W, but default `PWR_BIKE` cutoffs are hardcoded to `150, 200, 250, 300, 350, 450 W` (elite ~285–300 W FTP).
* Default HR zones are `135, 155, 175, 185 bpm` (`HR_RUN`) and `130, 150, 170, 180 bpm` (`HR_BIKE`), while `ZoneCalculatorDialog` defaults to `190 / 50 bpm`.
System SHALL align canonical defaults to **FTP = 240 W** (Coggan cutoffs: `132, 180, 216, 252, 288, 360 W`) and Karvonen **HRmax = 185 bpm, HRrest = 55 bpm** (`HR_RUN`: `132, 145, 158, 171 bpm`; `HR_BIKE`: `127, 140, 153, 166 bpm`).

### 1.2 Functional & Architectural Requirements
* **Cycling Power Defaults (`REQ-UI-426.1`)**: `DEFAULT_CYCLING_FTP` SHALL be `240` in `SettingsDataStore.kt`. `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, and `resetToDefaults()` SHALL set `PWR_BIKE` cutoffs to `132, 180, 216, 252, 288, 360`. `ZoneCalculatorDialog.kt` fallback FTP input SHALL be `"240"`.
* **Heart Rate Defaults (`REQ-UI-426.2`)**: `SettingsDataStore.kt` SHALL define `DEFAULT_HR_MAX = 185` and `DEFAULT_HR_REST = 55`. `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, and `resetToDefaults()` SHALL set `HR_RUN` cutoffs to `132, 145, 158, 171` and `HR_BIKE` to `127, 140, 153, 166`. `ZoneCalculatorDialog.kt` fallbacks SHALL be `"185"` and `"55"`.
* **Tracking HUD Fallback Alignment (`REQ-UI-426.3`)**: `TrackingViewModel.kt`'s `zoneCutoffsMap` fallback lists SHALL match canonical defaults.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* a fresh install or reset to defaults, *When* `SettingsDataStore` loads snapshot, *Then* `pwrBikeZoneMax` is `[132, 180, 216, 252, 288, 360]`, `hrRunZoneMax` is `[132, 145, 158, 171]`, and `hrBikeZoneMax` is `[127, 140, 153, 166]`.
* *Given* `ZoneCalculatorDialog` opened without prior stored benchmarks, *When* inspected, *Then* inputs default to FTP `"240"`, HRmax `"185"`, and HRrest `"55"`.
* *Given* customized zones (`isCustomized == true`), *When* app starts or settings are loaded, *Then* custom values are strictly preserved.

### 1.4 System Invariants
1. Existing customized profiles (`isCustomized == true`) MUST NOT be overwritten.
2. Historical workout summaries and samples in SQLite MUST remain immutable.
3. Strict integer monotonicity ($Z_n \ge Z_{n-1} + 1$) and 2500 W ceiling MUST remain guaranteed.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

* `Original Requirement ID & Target`: Refines `REQ-UI-370` (Power 7-Zone) and `REQ-UI-373` (Zone Calculator). Targets `SettingsDataStore.kt`, `ZoneCalculatorDialog.kt`, `TrackingViewModel.kt`.
* `Historical Origin & Commit Trace`: `ATT-3184` (`25828a66`), `ATT-3188` (`889fcebf`), and `ATT-1289` (`559376e2`).
* `Root Reason for Existing Formulation`: Placeholder thresholds were chosen before calculator integration; when the calculator dialog was built, defaults were hardcoded locally without refactoring existing DataStore defaults.
* `Preservation of Core Invariants`: Customized user profiles retain stored values; strict integer monotonicity ($Z_n \ge Z_{n-1} + 1$) and 2500 W ceiling remain guaranteed; formulas in `ZoneCalculationMath` remain pure and unchanged.

---

## 3. Test Specification (TST-UI-391)

* **`TST-UI-391.1`**: SettingsDataStore Baseline Constants & Snapshot Defaults Unit Tests (`SettingsDataStoreBaselinesTest.kt`).
* **`TST-UI-391.2`**: SettingsDataStore `getDefaultValue()` & `resetToDefaults()` Symmetry Test (`SettingsDataStoreBaselinesTest.kt`).
* **`TST-UI-391.3`**: ZoneCalculatorDialog Fallback & Initial Calculation Contract Tests (`ZoneCalculatorDialogContractTest.kt`).
* **`TST-UI-391.4`**: TrackingViewModel Runtime Fallback Cutoffs Contract Test (`TrackingViewModelGridTest.kt`).
* **`TST-UI-391.5`**: BatterySaver & JavaHelper Contract Test Baseline Updates (`BatterySaverModelPureTest.kt`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`).
* **`TST-UI-391.6`**: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`).

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test / Subject | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-391.1]` | Unit | `SettingsDataStore` default constants & `SettingsSnapshot.DEFAULT` | `REQ-UI-426.1`, `REQ-UI-426.2` | Planned (ATT-4521) |
| `[TST-UI-391.2]` | Unit | `SettingsDataStore.getDefaultValue` & `resetToDefaults` | `REQ-UI-426.1`, `REQ-UI-426.2` | Planned (ATT-4521) |
| `[TST-UI-391.3]` | Contract | `ZoneCalculatorUiState` & `ZoneCalculatorStateHolder` fallbacks | `REQ-UI-426.1`, `REQ-UI-426.2` | Planned (ATT-4521) |
| `[TST-UI-391.4]` | Contract | `TrackingViewModel.zoneCutoffsMap` initial fallback lists | `REQ-UI-426.3` | Planned (ATT-4521) |
| `[TST-UI-391.5]` | Unit | Existing test suites expecting old placeholder cutoffs | `REQ-UI-426.1`, `REQ-UI-426.2` | Planned (ATT-4521) |
| `[TST-UI-391.6]` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Planned (ATT-4521) |
