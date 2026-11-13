# Stage 1 Analysis: ATT-7521 - Global Typography: Use real Greek letter α ('DFA α1' / 'α1') across all 9 locales and UI screens

**Ticket**: [ATT-7521](https://rainerblind.atlassian.net/browse/ATT-7521)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7521` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
Across the application, Detrended Fluctuation Analysis alpha-1 nomenclature is inconsistently formatted:
* ASCII 'a' (`DFA a1` / `a1`) is used in tab titles (`tab_hrv_dfa_a1`), sensor type labels/shorts (`hrv_dfa_a1`, `hrv_dfa_a1_short`), settings descriptions, Workout Details cards/scatter plots, and Guided Ramp Test UI strings.
* Greek subscript 'α₁' is used in `ZonesSettingsActivity.kt` range badges (`α₁ > %.2f`).
* Greek 'α' (`DFA α1`, `Δα1`) is used in newer cards (`HrvDurabilityCard`, FTP comparison dialog).

Standardizing globally on the real Greek letter α (`DFA α1` / `α1`) ensures consistent, professional physiological typography across all 9 application locales without altering internal symbols.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Historical ASCII Pragmatism)**: `ATT-3541` initially defined `strings_hrv.xml` using ASCII `DFA a1` and `a1` to avoid potential XML encoding or parser issues during early DSP engine construction.
* **RCA 2 (Organic Accretion without Typography Governance)**: Subsequent tickets (`ATT-3544`, `ATT-4969`, `ATT-7317`) duplicated ASCII string patterns, while newer features (`ATT-4970`, `ATT-3555`, `ZonesSettingsActivity`) adopted Greek variants (`α₁`, `DFA α1`, `Δα1`), resulting in visible UI inconsistency.
* **Independent Codebase Verification**:
  * Confirmed 26 string definitions in `strings_hrv.xml` across all 9 locales containing ASCII `DFA a1` / `a1`.
  * Verified `SensorType.HRV_DFA_A1` delegates to `R.string.hrv_dfa_a1` and `R.string.hrv_dfa_a1_short`; updating string resources automatically updates sensor labels and short titles.
  * Identified 3 occurrences of subscript `α₁` in `ZonesSettingsActivity.kt` (lines 1162, 1170, 1178) to be aligned with standard `α1`.
  * Confirmed `HrvAlpha1ScatterCard`, `HrvThresholdSummaryCard`, `HrvRampTestScreen`, `MapDetailLayout`, and `PhysiologyModelsSection` resolve text via `R.string.*` with zero hardcoded text strings.

---

## 3. User Scope Grounding & Complexity Assessment (ATT-1250)
* **In-Scope Goals (2 SP - Standard)**:
  1. Update 26 string definitions in `app/src/main/res/values*/strings_hrv.xml` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) to replace `DFA a1` / `a1` with `DFA α1` / `α1`.
  2. Standardize `ZonesSettingsActivity.kt` range badges to `α1` (`α1 > %.2f`, `%.2f ≤ α1 ≤ %.2f`, `α1 < %.2f`).
  3. Update unit test fixtures (`HrvInfoBottomSheetTest`, `SensorFieldZoneDisplayTest`, `TrackingViewModelHrvTest`) and add character assertions in `SensorTypeHrvTest`.
  4. Update living documentation references in `docs/requirements/` and `docs/tests/`.
* **Out-of-Scope Non-Goals**:
  * Internal enum names (`SensorType.HRV_DFA_A1`), resource IDs (`R.string.hrv_dfa_a1`), DataStore preference keys (`hrv_dfa_a1_*`), and test tags (`hrv_test_live_a1`) remain strictly unchanged.
  * No modifications to DSP filtering, artifact correction algorithms, or sensor drivers.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-TRK-061` (*Live HRV Telemetry & Configurable DFA a1 Thresholds*), `REQ-PER-035` (*Workout Details DFA a1 Charts & Scatter Plot*), `REQ-TRK-072` (*Guided DFA a1 Step & Ramp Test Protocol*), and `REQ-UI-240` (*Zone Visualization Options*).
* **Historical Origin & Commit Trace**: `ATT-3541` (`4bad7272`, Sprint 2026-46.2) introduced `strings_hrv.xml` with ASCII strings `hrv_dfa_a1` and `hrv_dfa_a1_short`. `ATT-7317` (`fce6777b`) introduced `tab_hrv_dfa_a1`.
* **Root Reason for Existing Formulation**: Early development avoided non-ASCII characters in baseline files; typography was never unified as features accreted.
* **Preservation of Core Invariants**: Enum ordinal/name parity (`HRV_DFA_A1`), DataStore keys, and resource IDs are 100% preserved. 9-locale parity is maintained across all `strings_hrv.xml` files.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-061`, `REQ-PER-035`, `REQ-TRK-072`, `REQ-UI-240`.
* **Affected Architecture Elements**: `app/src/main/res/values*/strings_hrv.xml`, `ZonesSettingsActivity.kt`, `SensorTypeHrvTest.kt`.
* **Verification Impact & Test Surface**: `TranslationParityTest`, `SensorTypeHrvTest`, `HrvDfaA1SettingsContractTest`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk (UI typography standardization). No schema, protocol, or algorithmic impact.

---

## 6. Living Documentation Plan
* Update `docs/requirements/req_tracking_cockpit.md`, `docs/requirements/req_aftermath_periods.md`, `docs/requirements/req_ui_design_system.md`, and corresponding test suites under `docs/tests/` to standardize on `DFA α1` / `α1`.
