# Walkthrough: Promote HRV DFA a1 Aerobic and Anaerobic Thresholds into Dedicated Training Zones Tab (ATT-7317)

* **Parent Ticket**: [ATT-7317](https://rainerblind.atlassian.net/browse/ATT-7317) (*[Verbesserung] Promote HRV DFA a1 aerobic and anaerobic thresholds into a dedicated Training Zones tab*)
* **Sub-Tasks**:
  * [ATT-7353](https://rainerblind.atlassian.net/browse/ATT-7353) (*[SWE.1] Problem Domain & Root Cause Analysis*) - `Erledigt`
  * [ATT-7355](https://rainerblind.atlassian.net/browse/ATT-7355) (*[SWE.4] Requirement & Test Specification*) - `Erledigt`
  * [ATT-7357](https://rainerblind.atlassian.net/browse/ATT-7357) (*[SWE.2 / SWE.3] Architecture, Detailed Design & Implementation Plan*) - `Erledigt`
  * [ATT-7366](https://rainerblind.atlassian.net/browse/ATT-7366) (*[SWE.3] Software Construction & Implementation*) - `Erledigt`
* **Target Version**: `V5.0.0`
* **Requirements**:
  * [`REQ-TRK-061.4`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements/req_tracking_cockpit.md#L3351) (*User-Configurable Individual Threshold Overrides & Invariant*) - `[Verified]`
  * [`REQ-TRK-072.5`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements/req_tracking_cockpit.md#L4171) (*Early Sub-Exhaustion Auto-Completion Detection*) - `[Verified]`
* **Test Specifications**:
  * [`TST-TRK-061.4`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_tracking_cockpit.md#L3351) (*HRV DFA a1 Training Zones Tab & Invariant Verification*) - `[Verified]`
  * [`TST-TRK-072.3`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_tracking_cockpit.md#L4171) (*HRV Ramp Test Dynamic Cutoff Verification*) - `[Verified]`
* **Sprint**: `sprint/2026-46.4`
* **Branch**: `subagent-Lane-1-Agent---ATT-7317-self-7f572b08`

---

## 1. Problem Domain & Root Cause Summary

In *aTrainingTracker*, athletic training zones for Heart Rate (`HR Run`, `HR Bike`) and Power (`Power Bike`) were centralized within `ZoneSettingsScreen` (`ZonesSettingsActivity.kt`). However, HRV DFA $\alpha_1$ aerobic ($T_1$) and anaerobic ($T_2$) threshold boundaries were temporarily placed inside `PhysiologyModelsSection.kt` under **Advanced Tuning** (`ATT-6715`), leading to several key issues:
1. **Fragmented User Experience & Discovery**: Athletes adjusting their physiological training zones had to configure cardiovascular zones in Training Zones, but navigate to Advanced Settings -> Tuning -> Physiology Models to calibrate their autonomic HRV pacing thresholds.
2. **Coarse Step Precision**: Sliders in `PhysiologyModelsSection` lacked fine $0.01$ precision steppers, limiting exact threshold configuration (e.g. configuring $T_1 = 0.77$ or $T_2 = 0.52$).
3. **Missing Immediate Feedback**: No live theme-aware `SensorFieldView` preview was available to visualize how customized thresholds affect Cockpit tile color coding (`Zone 2` / Green, `Zone 3` / Orange, `Zone 4` / Red) and gauge fills.
4. **Hardcoded Ramp Test Cutoff**: In `HrvRampTestViewModel.kt`, the sub-exhaustion early completion detection was hardcoded to $0.45$, ignoring athlete-customized anaerobic threshold values $T_2$.

---

## 2. Implemented Architecture & UI Changes

### 2.1 Dedicated 4th Tab in `ZoneSettingsScreen` (`ZonesSettingsActivity.kt`)
* Added a 4th tab `R.string.tab_hrv_dfa_a1` (`HRV (DFA a1)`) to `ZoneSettingsScreen`.
* Deep-linkable via `NavRoutes.trainingZones(initialTab = 3)`.
* Guarded `currentType = zoneTypes.getOrElse(pagerState.currentPage) { ZoneType.HR_RUN }` ensuring out-of-bounds safety on page 3.
* Hides HR/Power-specific calculator (`ZoneCalculatorDialog`) and history (`ZoneHistoryDialog`) top-bar action buttons when the HRV tab is selected (`pagerState.currentPage < 3`).

### 2.2 Three Inverse-Intensity Physiological Bands (`TTColor`)
* Rendered 3 distinct cards representing the physiological zones conforming strictly to `TTColor` (Rule 71):
  * **Aerobic Base** (`Zone 2` / Green): $\alpha_1 > T_1$ (`TTColor.Zone2`).
  * **Threshold Transition** (`Zone 3` / Orange): $T_2 \le \alpha_1 \le T_1$ (`TTColor.Zone3`).
  * **High Intensity / Anaerobic** (`Zone 4` / Red): $\alpha_1 < T_2$ (`TTColor.Zone4`).

### 2.3 Fine 0.01-Step Threshold Controls with Invariant Enforcement
* Implemented `HrvThresholdControlRow` combining continuous sliders with precision `-` / `+` stepper buttons ($0.01$ step).
* Enforces invariant $T_1 \ge T_2 + 0.05$ (`TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP`).
* Clamping logic ensures $T_1 \in [T_2 + 0.05, 1.20]$ and $T_2 \in [0.30, T_1 - 0.05]$.
* Persists asynchronously into `TuningPreferencesDataStore` via `setDfaA1ThresholdT1` and `setDfaA1ThresholdT2`.

### 2.4 Actions: Reset to Defaults & Info Sheet
* **Reset to Defaults**: Quick text-button resetting both thresholds to population defaults ($T_1 = 0.75$, $T_2 = 0.50$).
* **Info Action (`?`)**: Opens `HrvInfoBottomSheet` with dynamically resolved $T_1$ and $T_2$ values.
* **Optional CTA**: Optional button linking directly to Guided HRV Step/Ramp Test when hosted inside app navigation.

### 2.5 Live Theme-Aware `SensorFieldView` Preview
* Live interactive preview rendering real `SensorFieldView` and `HrvZoneIndicator.calculateZoneGaugeState`.
* Interactive pills (`Aerobic (0.85)`, `Transition (0.62)`, `Anaerobic (0.42)`) allow athletes to test threshold rendering.
* Configurable `ZoneDisplayOptions` (Background, Left Bar, Right Bar, Text Color, Zone Gauge) matching athlete preferences.

### 2.6 Cleanup of Redundant Sliders (`PhysiologyModelsSection.kt`)
* Removed duplicate $T_1$ and $T_2$ sliders from `PhysiologyModelsSection.kt` under Advanced Tuning.
* Retained technical signal-quality `HRV Artifact Gate` slider ($3.0\% - 5.0\%$).
* Added an informative navigation card directing athletes to **Training Zones -> HRV (DFA a1)**.

### 2.7 Dynamic Ramp Test Sub-Exhaustion Cutoff (`HrvRampTestViewModel.kt`)
* Updated `HrvRampTestViewModel` to observe `TuningPreferencesDataStore.dfaA1ThresholdT2Flow`.
* Replaced hardcoded $0.45$ cutoff with dynamic formula $(T_2 - 0.05f).\text{coerceAtLeast}(0.25f)$ for early sub-exhaustion completion sustained $\ge 60\text{ s}$.

### 2.8 100% 9-Language Localization Parity
* Added full string resources in `strings_hrv.xml` across all 9 supported locales:
  * `values` (EN), `values-de` (DE), `values-es` (ES), `values-fr` (FR), `values-it` (IT), `values-ja` (JA), `values-nl` (NL), `values-pl` (PL), `values-pt` (PT).

---

## 3. Automated Test Verification & Evidence

### 3.1 Contract & Functional Unit Tests
1. **`HrvDfaA1SettingsContractTest`**:
   * `defaultThresholds_matchPhysiologicalPopulationStandards`: Confirms defaults $T_1 = 0.75$, $T_2 = 0.50$.
   * `invariant_T1MustBeAtLeastT2PlusGap`: Validates minimum $0.05$ gap enforcement.
   * `stepPrecision_supportsTwoDecimalPlaces`: Validates $0.01$ rounding and formatting.
   * `physiologicalZoneClassification_matchesInverseIntensitySemantics`: Validates zone mapping ($>T_1 \to \text{Zone 2}$, $T_2..T_1 \to \text{Zone 3}$, $<T_2 \to \text{Zone 4}$).
   * `gaugeFraction_computesNormalizedFillAcrossBands`: Confirms continuous gauge fill behavior.
2. **`PhysiologyModelsVisibilityContractTest`**:
   * `redundantT1T2SlidersAreRemovedFromPhysiologyModelsSection`: Validates removal of old sliders from `PhysiologyModelsSection.kt`.
   * `hrvArtifactGateSliderIsPreservedInPhysiologyModelsSection`: Validates preservation of Artifact Gate slider.
3. **`HrvRampTestViewModelTest`**:
   * `earlyCompletion_usesDynamicCutoff_basedOnConfiguredT2Threshold`: Validates dynamic cutoff $(T_2 - 0.05)$ and early finish triggers.
4. **`TranslationParityTest`**:
   * Confirms 100% translation parity across all 9 localized `strings_hrv.xml` files.

### 3.2 Test Execution Command & Result
```bash
python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.hrv.HrvRampTestViewModelTest" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
```
**Result**: `BUILD SUCCESSFUL in 19s` (33 actionable tasks: 1 executed, 32 up-to-date, 0 failures).

Full zones package regression:
```bash
python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*"
```
**Result**: `BUILD SUCCESSFUL in 15s` (0 failures).

---

## 4. Traceability & Stage Status

| Artifact / Entity | ID | Status | Notes |
|:---|:---|:---|:---|
| Requirement | `REQ-TRK-061.4` | **Verified** | Traceability updated in `docs/requirements/req_tracking_cockpit.md` |
| Requirement | `REQ-TRK-072.5` | **Verified** | Traceability updated in `docs/requirements/req_tracking_cockpit.md` |
| Test Case | `TST-TRK-061.4` | **Verified** | Traceability updated in `docs/tests/tst_tracking_cockpit.md` |
| Test Case | `TST-TRK-072.3` | **Verified** | Traceability updated in `docs/tests/tst_tracking_cockpit.md` |
| Parent Ticket | `ATT-7317` | **In Testing** | Ready for Stage 5 submission & Gate 5 verdict |
| Stage 1 Subtask | `ATT-7353` | **Erledigt** | Analysis approved |
| Stage 2 Subtask | `ATT-7355` | **Erledigt** | Req & Test Spec approved |
| Stage 3 Subtask | `ATT-7357` | **Erledigt** | Implementation Plan approved |
| Stage 4 Subtask | `ATT-7366` | **Erledigt** | Implementation approved |
| Stage 5 Subtask | In progress | Submitting | Verification and walkthrough |
