# Stage 2: Requirement & Test Specification - ATT-7317: Promote HRV DFA a1 aerobic and anaerobic thresholds into a dedicated Training Zones tab

**Ticket**: [ATT-7317](https://rainerblind.atlassian.net/browse/ATT-7317)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability / HRV*)  
**Target Release**: `Sprint 2026-46.4`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-TRK-061.4` (*Promote HRV DFA a1 Thresholds to Training Zones 4th Tab*), `REQ-TRK-072.5` (*Dynamic Guided Ramp Test Anaerobic Cutoff*)  
**Test Spec ID**: `TST-TRK-061.4`, `TST-TRK-072.3`  
**Branch**: `subagent-Lane-1-Agent---ATT-7317-self-7f572b08`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-TRK-061.4`, `REQ-TRK-072.5`)

### 1.1 Problem Statement & Rationale
Athletic training zone configuration currently groups `HR Run`, `HR Bike`, and `Power Bike` in `ZoneSettingsScreen`, while HRV DFA $\alpha_1$ aerobic ($T_1$) and anaerobic ($T_2$) threshold boundaries reside in Advanced Settings (`PhysiologyModelsSection.kt`). Placing physiological pacing boundaries in disjoint locations causes friction and confusion. Promoting HRV DFA $\alpha_1$ into a dedicated 4th tab with 3 inverse-intensity physiological bands (Zone 2/Green $> T_1$, Zone 3/Orange $T_2..T_1$, Zone 4/Red $< T_2$), live theme-aware preview, 0.01 precision, reset to defaults, and dynamic sub-exhaustion early completion in Guided Ramp Tests provides a cohesive single-pane-of-glass experience.

### 1.2 Functional & Architectural Requirements
1. **Dedicated 4th Tab in `ZoneSettingsScreen`**:
   - `ZoneSettingsScreen` SHALL include a 4th tab `R.string.tab_hrv_dfa_a1` (`HRV (DFA a1)`) deep-linkable via `NavRoutes.trainingZones(initialTab = 3)`.
   - The top-bar action buttons for `ZoneCalculatorDialog` and `ZoneHistoryDialog` SHALL be hidden when the 4th tab (`HRV (DFA a1)`) is active.
2. **HRV Threshold Controls & Invariant Enforcement**:
   - The tab SHALL provide controls for $T_1$ (Aerobic Threshold, $[0.60, 1.20]$, default `0.75`) and $T_2$ (Anaerobic Threshold, $[0.30, 0.80]$, default `0.50`) with `0.01` step precision.
   - The system SHALL strictly enforce the invariant $T_1 \ge T_2 + 0.05$ (`TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP`).
   - Edited values SHALL persist immediately to `TuningPreferencesDataStore` (`KEY_DFA_A1_THRESHOLD_T1`, `KEY_DFA_A1_THRESHOLD_T2`).
3. **Reset to Defaults & Info Sheet**:
   - A "Reset to Defaults" button SHALL restore $T_1$ to `0.75` and $T_2$ to `0.50`.
   - An Info (`?`) action SHALL present `HrvInfoBottomSheet` with live active $T_1$ and $T_2$ values.
4. **Live Theme-Aware `SensorFieldView` Preview**:
   - The tab SHALL display interactive pills for `Aerobic` ($\alpha_1 > T_1$), `Transition` ($T_2 \le \alpha_1 \le T_1$), and `Anaerobic` ($\alpha_1 < T_2$).
   - Selecting a pill SHALL render the live `SensorFieldView` preview displaying the zone color (`TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4`) and 5-zone gauge state computed via `HrvZoneIndicator.calculateZoneGaugeState`.
5. **Advanced Tuning Cleanup**:
   - Redundant $T_1$ and $T_2$ sliders SHALL be removed from `PhysiologyModelsSection.kt` in `AdvancedTuningScreen.kt`, while retaining the `HRV Artifact Gate` slider ($3.0\% - 5.0\%$).
6. **Dynamic Guided Ramp Test Cutoff**:
   - `HrvRampTestViewModel` SHALL read `dfaA1ThresholdT2Flow` from `TuningPreferencesDataStore` and trigger early completion during `TESTING` when live `dfaA1 < (dfaA1ThresholdT2 - 0.05f).coerceAtLeast(0.25f)` sustainably for $\ge 60\text{ s}$ instead of hardcoded `0.45`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (4th Tab Navigation & Top-Bar Visibility)**:
  * *Given* the athlete navigates to `NavRoutes.trainingZones(initialTab = 3)`,
  * *When* the screen renders,
  * *Then* the 4th tab (`HRV (DFA a1)`) is selected, showing $T_1$ and $T_2$ controls, and calculator/history icons are absent from the top bar.
* **Criterion 2 (0.01 Precision & Invariant Enforcement)**:
  * *Given* active thresholds $T_1 = 0.75$, $T_2 = 0.50$,
  * *When* adjusting $T_1$ to `0.82` and $T_2$ to `0.54`,
  * *Then* values are accepted with 2 decimal precision, $T_1 \ge T_2 + 0.05$ invariant is maintained, and values are written to `TuningPreferencesDataStore`.
* **Criterion 3 (Live SensorFieldView Preview & Reset Action)**:
  * *Given* modified $T_1$ and $T_2$ thresholds,
  * *When* athlete toggles between `Aerobic`, `Transition`, and `Anaerobic` preview pills,
  * *Then* `SensorFieldView` preview reflects respective semantic colors and gauge needle positions; *When* tapping Reset to Defaults, *Then* $T_1$ resets to `0.75` and $T_2$ to `0.50`.
* **Criterion 4 (Dynamic Ramp Test Cutoff)**:
  * *Given* an athlete with customized $T_2 = 0.60$,
  * *When* performing a Guided Step Test and live $\alpha_1 = 0.53$ ($< 0.60 - 0.05 = 0.55$) for 60 seconds,
  * *Then* `HrvRampTestViewModel` triggers early completion state.

### 1.4 System Invariants
1. Zero hardcoded hex colors outside `Color.kt` per Rule 71 (uses `TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4`).
2. $T_1 \ge T_2 + 0.05$ strictly preserved across all entry points.
3. 100% 9-language localization parity across `values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`.

---

## 2. Test Specification

### Test Case 1: `HrvDfaA1SettingsContractTest` (`TST-TRK-061.4a`)
* **Scope**: Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1SettingsContractTest.kt`
* **Preconditions**: `ZonesSettingsActivity.kt` source code and compiled classes.
* **Action**: Verify `profileNameResIds` includes 4 tabs, `HrvDfaA1SettingsContent` composable exists and uses `0.01` stepping, invariant clamping, `SensorFieldView` preview, and hides calculator/history icons on tab 3.
* **Expected Result**: Assertions pass, verifying full contract adherence.

### Test Case 2: `HrvRampTestViewModelDynamicCutoffTest` (`TST-TRK-072.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestViewModelTest.kt`
* **Preconditions**: Mocked `TuningPreferencesDataStore` with customized $T_2 = 0.60f$ (cutoff $0.55f$).
* **Action**: Emit $\alpha_1 = 0.53f$ for 60 ticks during `TESTING` phase.
* **Expected Result**: Early completion phase is entered; when $\alpha_1 = 0.56f$ ($> 0.55f$), counter resets to 0.

### Test Case 3: `PhysiologyModelsSectionCleanupTest` (`TST-TRK-061.4b`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/PhysiologyModelsVisibilityContractTest.kt`
* **Preconditions**: `PhysiologyModelsSection.kt` source and reflection.
* **Action**: Verify absence of redundant $T_1$ and $T_2$ sliders and preservation of artifact gate slider.
* **Expected Result**: Sliders removed, artifact gate retained.

### Test Case 4: 9-Language Localization & Format Parity Audit (`TST-TRK-061.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Action**: Check `R.string.tab_hrv_dfa_a1` and all related strings across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% parity across all 9 canonical languages.

### Test Case 5: Tier 1 Targeted Regression Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`
* **Expected Result**: All tests pass with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-061.4a` | Contract / UI | `ZonesSettingsActivity.HrvDfaA1SettingsContent` | `REQ-TRK-061.4` | Specified |
| `TST-TRK-061.4b` | Contract | `PhysiologyModelsSection` | `REQ-TRK-061.4` | Specified |
| `TST-TRK-072.3` | Unit | `HrvRampTestViewModel` early completion | `REQ-TRK-072.5` | Specified |
| `TST-TRK-061.5` | Localization | `TranslationParityTest` (tab and threshold strings) | `REQ-TRK-061.4`, `REQ-UI-106` | Specified |
| Tier 1 Suite | Regression | Zones, HRV, and Tuning packages | `REQ-PRO-001` | Specified |
