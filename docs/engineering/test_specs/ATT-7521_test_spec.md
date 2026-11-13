# Stage 2: Requirement & Test Specification - ATT-7521: Global Typography DFA α1

**Ticket**: [ATT-7521](https://rainerblind.atlassian.net/browse/ATT-7521)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-UI-526` (*Global Physiological Typography Standardization for DFA α1 and α1*)  
**Test Spec ID**: `TST-UI-526` (`TST-UI-526.1`–`TST-UI-526.5`) | **Branch**: `feature/ATT-7521`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-UI-526)

### 1.1 Problem Statement & Rationale
Detrended Fluctuation Analysis alpha-1 nomenclature was historically inconsistent across the application due to early ASCII representations (`DFA a1` / `a1`) in `strings_hrv.xml`, subscript Greek notations (`α₁`) in `ZonesSettingsActivity.kt` range badges, and standard Greek notations (`DFA α1`, `Δα1`) in newer durability and ramp test components. Standardizing globally on authentic Greek letter α (`DFA α1` and `α1`) ensures professional, consistent physiological typography across all user-facing screens and all 9 supported locales while strictly maintaining internal enum and preference key stability.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. All user-facing string definitions in `app/src/main/res/values*/strings_hrv.xml` referencing Detrended Fluctuation Analysis alpha-1 SHALL use authentic Greek letter α (`DFA α1` and `α1`) across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`), including `hrv_dfa_a1` (`DFA α1`), `hrv_dfa_a1_short` (`α1`), `tab_hrv_dfa_a1` (`HRV (DFA α1)` / `VFC (DFA α1)`), `hrv_test_live_a1` (`Live α1: %1$.2f`), and all related threshold, tuning, and guided test descriptions (`REQ-UI-526.1`).
2. In `ZonesSettingsActivity.kt`, dynamic zone range badge strings SHALL use standard `α1` (`"α1 > %.2f"`, `"%.2f ≤ α1 ≤ %.2f"`, `"α1 < %.2f"`) instead of subscript `α₁`, aligning with app-wide typography (`REQ-UI-526.2`).
3. `SensorType.HRV_DFA_A1` SHALL resolve its user-facing full label and short title from `R.string.hrv_dfa_a1` (`DFA α1`) and `R.string.hrv_dfa_a1_short` (`α1`) (`REQ-UI-526.3`).
4. Internal symbols, enum names (`SensorType.HRV_DFA_A1`), resource IDs (`R.string.hrv_dfa_a1`, `R.string.hrv_dfa_a1_short`, `R.string.tab_hrv_dfa_a1`), DataStore preference keys (`tuning_dfa_a1_threshold_t1`, `tuning_dfa_a1_threshold_t2`, `hrv_dfa_a1_zone_*`), and UI test tags (`hrv_test_live_a1`, `hrv_alpha1_scatter_card`) SHALL remain strictly unchanged (`REQ-UI-526.4`).
5. 100% key and format specifier parity SHALL be preserved across all 9 localized resource files with zero missing keys and zero AAPT2 formatting errors (`REQ-UI-526.5`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC1 (Typography Parity Across 9 Locales)**:
  * *Given* the application running under any of the 9 supported locales,
  * *When* viewing any screen referencing Detrended Fluctuation Analysis alpha-1,
  * *Then* the metric is displayed using Greek letter α (`DFA α1` or `α1`) with zero occurrences of ASCII `DFA a1` or subscript `α₁`.
* **AC2 (Sensor Type Resolution)**:
  * *Given* `SensorType.HRV_DFA_A1`,
  * *When* resolving `fullNameId` and `shortNameId`,
  * *Then* they yield string resources displaying `DFA α1` and `α1` respectively.
* **AC3 (Invariant Preservation)**:
  * *Given* DataStore keys and SQLite schema,
  * *When* reading or writing preferences,
  * *Then* all keys, database columns, and resource IDs remain 100% invariant.

### 1.5 System Invariants
- Zero changes to internal enum constants, DataStore keys, resource IDs, or database schemas.
- Zero alterations to mathematical DSP calculation algorithms or threshold invariant $T_1 \ge T_2 + 0.05f$.

---

## 2. Test Specification (TST-UI-526)

### Test Case 1: SensorType HRV_DFA_A1 Typography Verification (`TST-UI-526.1`)
* **Scope**: Unit (`SensorTypeHrvTest.kt`)
* **Verification**: Verify `SensorType.HRV_DFA_A1` retains `fullNameId = R.string.hrv_dfa_a1` and `shortNameId = R.string.hrv_dfa_a1_short`. Assert that resolved string contents for default locale contain `'DFA α1'` and `'α1'`.

### Test Case 2: Zone Settings Badge Contract Verification (`TST-UI-526.2`)
* **Scope**: Unit (`HrvDfaA1SettingsContractTest.kt`)
* **Verification**: Verify `ZonesSettingsActivity.kt` source code contains standard Greek `"α1"` in range badge templates (`"α1 > "`, `" ≤ α1 ≤ "`, `"α1 < "`) and contains zero occurrences of subscript `"α₁"`.

### Test Case 3: UI Test Fixture Typography Alignment (`TST-UI-526.3`)
* **Scope**: Unit (`HrvInfoBottomSheetTest.kt`, `SensorFieldZoneDisplayTest.kt`, `TrackingViewModelHrvTest.kt`)
* **Verification**: Update mock sensor field labels and test expectations from `"DFA a1"` to `"DFA α1"` across affected UI test fixtures.

### Test Case 4: 9-Language Localization Parity Verification (`TST-UI-526.4`)
* **Scope**: Unit (`TranslationParityTest.kt`)
* **Verification**: Verify 100% translation parity across all 9 locales with zero missing keys and matching `%1$.2f` format specifiers.

### Test Case 5: Tier 1 Targeted Unit Test Regression Suite (`TST-UI-526.5`)
* **Scope**: Integration / Unit
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-526.1` | Unit | `SensorTypeHrvTest.kt` | `REQ-UI-526.1`, `REQ-UI-526.3` | Specified |
| `TST-UI-526.2` | Unit | `HrvDfaA1SettingsContractTest.kt` | `REQ-UI-526.2` | Specified |
| `TST-UI-526.3` | Unit | UI Test Fixtures | `REQ-UI-526.3`, `REQ-UI-526.4` | Specified |
| `TST-UI-526.4` | Unit | `TranslationParityTest.kt` | `REQ-UI-526.1`, `REQ-UI-526.5` | Specified |
| `TST-UI-526.5` | Tier 1 Regr | Targeted test suites | `REQ-PRO-001` | Specified |
