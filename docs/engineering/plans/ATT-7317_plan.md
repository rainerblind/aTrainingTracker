# Stage 3: Implementation Plan - ATT-7317: Promote HRV DFA a1 aerobic and anaerobic thresholds into a dedicated Training Zones tab

**Ticket**: [ATT-7317](https://rainerblind.atlassian.net/browse/ATT-7317)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability / HRV*)  
**Target Release**: `Sprint 2026-46.4`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-TRK-061.4`, `REQ-TRK-072.5`  
**Test Mapping**: `TST-TRK-061.4`, `TST-TRK-072.3`  
**Branch**: `subagent-Lane-1-Agent---ATT-7317-self-7f572b08`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
Athletic training zones in *aTrainingTracker* are managed in `ZoneSettingsScreen` (`ZonesSettingsActivity.kt`) for `HR Run`, `HR Bike`, and `Power Bike`. However, HRV DFA $\alpha_1$ aerobic ($T_1$) and anaerobic ($T_2$) threshold boundaries currently reside disconnected in Advanced Settings (`PhysiologyModelsSection.kt`). This plan establishes a dedicated 4th tab `R.string.tab_hrv_dfa_a1` in `ZoneSettingsScreen` with 3 inverse-intensity physiological bands, $0.01$ precision editing, $T_1 \ge T_2 + 0.05$ invariant clamping, Reset to Defaults action, and live theme-aware `SensorFieldView` preview, removes redundant sliders from Advanced Settings, and wires dynamic cutoff into `HrvRampTestViewModel.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-061.4` (*Promote HRV DFA a1 Thresholds to Training Zones 4th Tab*)
  * **Test Mapping**: `TST-TRK-061.4` (`HrvDfaA1SettingsContractTest.kt`, `TuningPreferencesDataStoreDfaA1Test.kt`)
* **Requirement**: `REQ-TRK-072.5` (*Dynamic Guided Ramp Test Anaerobic Cutoff*)
  * **Test Mapping**: `TST-TRK-072.3` (`HrvRampTestViewModelTest.kt`)

---

## 3. System Invariants & Preserved Behavior
1. **$T_1 \ge T_2 + 0.05$ Invariant**: The aerobic threshold must strictly exceed anaerobic threshold by at least `0.05` across all UI inputs and DataStore transactions.
2. **Rule 71 Theme Token Color Invariance**: Semantic zone colors strictly use `TTColor.Zone2` (Green), `TTColor.Zone3` (Orange), `TTColor.Zone4` (Red), with zero hardcoded hex literals.
3. **DataStore Single Source of Truth**: Existing DataStore keys `KEY_DFA_A1_THRESHOLD_T1` and `KEY_DFA_A1_THRESHOLD_T2` are retained; existing athlete thresholds carry forward seamlessly.
4. **9-Language Parity**: All newly introduced strings are populated across all 9 canonical languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
5. **Human Gate Governance**: Parent ticket completion remains strictly reserved for `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ZonesSettingsActivity.kt` (`ZoneSettingsScreen` & `HrvDfaA1SettingsContent`)
* Extend `profileNameResIds` list to 4 entries: `R.string.tab_hr_run`, `R.string.tab_hr_bike`, `R.string.tab_pwr_bike`, and `R.string.tab_hrv_dfa_a1`.
* Top bar action icons: Guard `Icons.Default.Calculate` and `Icons.Default.History` with `if (pagerState.currentPage < 3)` so they are hidden on the HRV tab.
* In `HorizontalPager`, for `page == 3`, render `HrvDfaA1SettingsContent(tuningDataStore = remember { TuningPreferencesDataStore(context) }, onOpenHrvTest = ...)`:
  * Read `dfaA1ThresholdT1Flow` and `dfaA1ThresholdT2Flow`.
  * Sliders and stepper/numeric controls for $T_1$ ($[0.60, 1.20]$, step $0.01$) and $T_2$ ($[0.30, 0.80]$, step $0.01$).
  * Enforce $T_1 \ge T_2 + 0.05$ on value change and commit via `tuningDataStore.setDfaA1Thresholds(t1, t2)`.
  * 3 physiological bands displayed with semantic colors (`TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4`).
  * "Reset to Defaults" button invoking `tuningDataStore.setDfaA1Thresholds(0.75f, 0.50f)`.
  * Info button opening `HrvInfoBottomSheet(dfaA1ThresholdT1 = t1, dfaA1ThresholdT2 = t2)`.
  * Live theme-aware `SensorFieldView` preview with interactive pills: `Aerobic` ($\alpha_1 = 0.90$), `Transition` ($\alpha_1 = (t_1 + t_2) / 2$), `Anaerobic` ($\alpha_1 = 0.40$), using `HrvZoneIndicator.calculateZoneGaugeState`.

### Component 2: `PhysiologyModelsSection.kt` / `AdvancedTuningScreen.kt`
* Remove redundant $T_1$ and $T_2$ sliders from `PhysiologyModelsSection.kt`.
* Add an informational card pointing athletes to `Training Zones -> HRV (DFA a1)`.
* Retain `HRV Artifact Gate` slider ($3.0\% - 5.0\%$).
* Update constructor/callers in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` to omit redundant callbacks.

### Component 3: `HrvRampTestViewModel.kt`
* Inject `TuningPreferencesDataStore` into `HrvRampTestViewModel`.
* Collect `dfaA1ThresholdT2Flow` into `private var dfaA1ThresholdT2 = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2`.
* In `HrvTestPhase.TESTING` early completion branch, dynamically evaluate:
  `val cutoff = (dfaA1ThresholdT2 - 0.05f).coerceAtLeast(0.25f)`
  `if (dfaA1 != null && dfaA1 < cutoff) ...`

### UI Consistency (Rule 23)
* **Reference screen**: `ZoneSettingsScreen` (`ZonesSettingsActivity.kt`) and `SensorFieldView.kt`.
* **Reused components**: `ZonePreviewPill`, `SensorFieldView`, `HrvInfoBottomSheet`, `PrimaryScrollableTabRow`, `TuningSliderItem` / `AppOutlinedCard`.
* **Theme tokens**: `MaterialTheme.colorScheme.primaryContainer`, `TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4`, `MaterialTheme.shapes.medium`.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: String Resources & Localization Parity
* Add string key `tab_hrv_dfa_a1` ("HRV (DFA a1)" / "HRV (DFA a1)") and associated zone descriptions to all 9 languages in `strings.xml` / `strings_hrv.xml`.

### Step 2: `ZonesSettingsActivity.kt` 4th Tab & `HrvDfaA1SettingsContent`
* Update `profileNameResIds` to include `R.string.tab_hrv_dfa_a1`.
* Hide calculator and history icons in top bar when `pagerState.currentPage == 3`.
* Author `HrvDfaA1SettingsContent` with $T_1$/$T_2$ 0.01-step controls, invariant enforcement, live `SensorFieldView` preview, reset to defaults, and info sheet.

### Step 3: `PhysiologyModelsSection.kt` Cleanup
* Remove redundant $T_1$ and $T_2$ sliders from `PhysiologyModelsSection.kt`.
* Clean up call sites in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`.

### Step 4: `HrvRampTestViewModel.kt` Dynamic Early Cutoff
* Inject `TuningPreferencesDataStore` and replace hardcoded `0.45` with `(dfaA1ThresholdT2 - 0.05f).coerceAtLeast(0.25f)`.

### Step 5: Unit & Contract Tests
* Create `HrvDfaA1SettingsContractTest.kt` verifying 4th tab structure, 0.01 precision, and invariant clamping.
* Update `HrvRampTestViewModelTest.kt` verifying dynamic cutoff.
* Update `PhysiologyModelsVisibilityContractTest.kt` and `TranslationParityTest.kt`.
* Run targeted tests via `tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest ...`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted tests across `zones`, `hrv`, and `tuning` packages under sprint lock.
* **Rollback**: Changes isolated in git branch `subagent-Lane-1-Agent---ATT-7317-self-7f572b08`.
