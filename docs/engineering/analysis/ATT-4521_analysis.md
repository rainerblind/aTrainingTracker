# Stage 1 Analysis: ATT-4521 - Update default Cycling FTP (240 W) and Heart Rate / Power zone boundaries to realistic, calculator-aligned baselines

**Ticket**: [ATT-4521](https://rainerblind.atlassian.net/browse/ATT-4521)  
**Sub-task**: [ATT-4532](https://rainerblind.atlassian.net/browse/ATT-4532) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.5`  
**Branch**: `feature/ATT-4521`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation
* **Current Discordance**: Default zone thresholds across `SettingsDataStore.kt` and `ZoneCalculatorDialog.kt` are inconsistent:
  * `DEFAULT_CYCLING_FTP` is `200 W`, but default `PWR_BIKE` cutoffs are hardcoded to `150, 200, 250, 300, 350, 450 W` (corresponding to an elite ~285–300 W FTP where Z4 max is 300 W).
  * Default HR zones are `135, 155, 175, 185 bpm` (`HR_RUN`) and `130, 150, 170, 180 bpm` (`HR_BIKE`), while `ZoneCalculatorDialog` defaults to `HRmax = 190 bpm, HRrest = 50 bpm`.
* **Expected Baseline**:
  * **Cycling Power (`PWR_BIKE`)**: Align to realistic recreational baseline **FTP = 240 W** (~3.0 W/kg for 80 kg). Cutoffs from `calculateCogganPowerZones(240)`: Z1=132 W (55%), Z2=180 W (75%), Z3=216 W (90%), Z4=252 W (105%), Z5=288 W (120%), Z6=360 W (150%).
  * **Heart Rate (`HR_RUN` & `HR_BIKE`)**: Align to Karvonen baseline **HRmax = 185 bpm, HRrest = 55 bpm** (HRR = 130 bpm).
    * `HR_RUN` cutoffs: Z1=132 bpm (59%), Z2=145 bpm (69%), Z3=158 bpm (79%), Z4=171 bpm (89%).
    * `HR_BIKE` cutoffs: ~5 bpm lower cycling HR baseline: Z1=127, Z2=140, Z3=153, Z4=166 bpm.

---

## 2. Root Cause Analysis (Forensic Investigation)
* **Historical Origin**: In ATT-3184 (`25828a66`), 7-zone power was introduced with round placeholder numbers (150..450 W). ATT-3188 (`889fcebf`) introduced `ZoneCalculationMath` and `ZoneCalculatorDialog` with hardcoded inputs (200 W, 190/50 bpm). ATT-1289 (`559376e2`) introduced `DEFAULT_CYCLING_FTP = 200`. The defaults in `SettingsDataStore.kt`, `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, `resetToDefaults()`, and `ZoneCalculatorDialog.kt` were never synchronized.
* **Call Site & Component Impact**:
  * `SettingsDataStore.kt`: `DEFAULT_CYCLING_FTP` (200 -> 240); add `DEFAULT_HR_MAX = 185`, `DEFAULT_HR_REST = 55`; update `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, `resetToDefaults()`, and `mapSettingsSnapshot()` fallbacks.
  * `ZoneCalculatorDialog.kt`: Update `ZoneCalculatorUiState` defaults and `ZoneCalculatorStateHolder` fallback values (240, 185, 55).
  * `TrackingViewModel.kt`: Update fallback cutoffs in `zoneCutoffsMap` to match new canonical defaults.
  * Unit Tests: Update `ZoneCalculatorDialogContractTest.kt`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`, and `BatterySaverModelPureTest.kt`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Update default cycling FTP from 200 W to 240 W in `SettingsDataStore.kt` and `ZoneCalculatorDialog.kt`.
  * Define `DEFAULT_HR_MAX = 185` and `DEFAULT_HR_REST = 55` in `SettingsDataStore.kt` and align `ZoneCalculatorDialog.kt`.
  * Update default cutoff lists for `PWR_BIKE` (132, 180, 216, 252, 288, 360 W), `HR_RUN` (132, 145, 158, 171 bpm), and `HR_BIKE` (127, 140, 153, 166 bpm) in `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, `resetToDefaults()`, and `TrackingViewModel`.
  * Update all affected contract and unit tests asserting on baseline defaults.
* **Out-of-Scope Non-Goals**:
  * No modification to mathematical formulas in `ZoneCalculationMath.kt`.
  * No modification of user-customized zone configurations (`isCustomized == true`).
  * No modification to SQLite historical workout tables (`WorkoutSummaries.db`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-UI-370` (Power 7-Zone) and `REQ-UI-373` (Zone Calculator).
* **Historical Origin & Commit Trace**: `ATT-3184` (`25828a66`), `ATT-3188` (`889fcebf`), and `ATT-1289` (`559376e2`).
* **Root Reason for Existing Formulation**: Placeholder thresholds were chosen before calculator integration; when the calculator dialog was built, defaults were hardcoded locally without refactoring existing DataStore defaults.
* **Preservation of Core Invariants**: Customized user profiles retain stored values; strict integer monotonicity ($Z_n \ge Z_{n-1} + 1$) and 2500 W ceiling remain guaranteed; formulas in `ZoneCalculationMath` remain pure and unchanged.

---

## 5. Architectural Strategy & High-Level Solution
* **Single Source of Truth**: Centralize baseline defaults (`DEFAULT_CYCLING_FTP = 240`, `DEFAULT_HR_MAX = 185`, `DEFAULT_HR_REST = 55`) in `SettingsDataStore.kt`.
* **Symmetric Default Alignment**: Ensure `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, `resetToDefaults()`, and `TrackingViewModel.kt` use identical calculator-derived cutoffs.
* **Contract Test Refresh**: Update test assertions expecting old defaults to the new 240 W / 185 bpm / 55 bpm baselines.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression for athletes with customized zone profiles (`isCustomized == true`).
  2. Immutability of past recorded workout analytics in SQLite.
  3. Strict monotonic ascending boundary order: $0 < Z_1 < Z_2 < Z_3 < Z_4 < Z_5 < Z_6$.
* **Risk Rating**: **LOW**. Pure constant baseline alignment without schema or calculation engine modifications.
