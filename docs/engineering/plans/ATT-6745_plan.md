# Stage 3: Implementation Plan - ATT-6745: Surface Smoothed Chest-Strap Warnings & Guidance

**Ticket**: [ATT-6745](https://rainerblind.atlassian.net/browse/ATT-6745)  
**Sub-task**: [ATT-6779](https://rainerblind.atlassian.net/browse/ATT-6779) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-068` | **Test Mapping**: `TST-TRK-068`  
**Branch**: `feature/ATT-6745` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
When athletes use heart rate straps with internal firmware smoothing (Magene H64/H303, CooSpo) or quantized synthetic RR streams, $rMSSD$ collapses while DFA $\alpha_1$ is falsely inflated into an easy aerobic Zone 2 (>1.40). ATT-6744 constructed the DSP detection engine; this ticket completes the user-facing integration by modernizing sensor guidance in `HrvInfoBottomSheet`, suppressing green aerobic bars on smoothed live tiles, cautioning against saving corrupted morning readiness baselines in `MorningHrvCheckScreen`, and displaying inline aftermath warnings in `HrvAlpha1ScatterCard`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-068` (*Smoothed RR Stream Warnings & Sensor Compatibility Guidance*)
  * `REQ-TRK-068.1`: Modernize `hrv_info_sensor_recommendation_desc` across 9 locales.
  * `REQ-TRK-068.2`: `HrvInfoBottomSheet` top warning card and `HrvZoneIndicator` green zone suppression.
  * `REQ-TRK-068.3`: `MorningHrvCheckScreen` dedicated `SmoothedSignalWarningCard`.
  * `REQ-TRK-068.4`: `HrvAlpha1ScatterCard` inline warning banner.
  * `REQ-TRK-068.5`: 100% 9-language translation parity in `strings_hrv.xml`.
  * `REQ-TRK-068.6`: Theme tokens (`TTColor.ArtifactWarning*`) and invariant protection.
* **Test Mapping**: `TST-TRK-068` (`TST-TRK-068.1` to `TST-TRK-068.6`).

---

## 3. System Invariants & Preserved Behavior
1. **Independent > 5% Artifact Warnings**: Live cockpit and morning readiness high-artifact warnings continue to operate without interference.
2. **Valid Pacing Parity**: Authentic unsmoothed streams maintain identical threshold zone coloring (`TTColor.Zone2`, `Zone3`, `Zone4`).
3. **UI Consistency (Rule 23 & 71)**: Reuses `TTColor.ArtifactWarningContainer`, `Border`, `Title`, `Desc` tokens; shapes `RoundedCornerShape(12.dp)`; typography `titleSmall` and `bodySmall`.
4. **Modal Scroll Safety (Rule 72)**: Zero nested `verticalScroll` inside `AppModalBottomSheet`.
5. **Zero Database Modifications**: No SQLite schema changes; transient UI state only.

---

## 4. Proposed Architectural Changes
* **Localization Layer (`strings_hrv.xml` across all 9 locales)**:
  * Update `hrv_info_sensor_recommendation_desc` to distinguish Polar/Garmin recommended from optical & Magene/CooSpo smoothed unsuitable straps.
  * Add string keys for sheet top warning, morning check warning, and aftermath scatter warning.
* **Live Cockpit Guidance & Pacing**:
  * `HrvInfoBottomSheet.kt`: Accepts optional `signalQualityStatus`, rendering a top warning card on smoothed streams.
  * `HrvZoneIndicator.kt` & `TrackingViewModel.kt`: Gated `calculateZoneColor` with `isSmoothedStream` suppressing green Zone 2.
* **Morning HRV Screen (`MorningHrvCheckScreen.kt`)**:
  * Renders `SmoothedSignalWarningCard` when `uiState.signalQualityStatus` indicates smoothed/quantized signal.
* **Aftermath Analysis (`HrvAlpha1ScatterCard.kt`)**:
  * Accepts optional `isSmoothedStream`, rendering an inline warning banner when true.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (9-Locale Localized Strings)**: Update `hrv_info_sensor_recommendation_desc` and add warning string keys in `strings_hrv.xml` across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
2. **Step 2 (HrvInfoBottomSheet Guidance & Top Warning)**: Add top warning card in `HrvInfoBottomSheet.kt` when `signalQualityStatus` is smoothed/quantized; update `HrvInfoBottomSheetTest.kt`.
3. **Step 3 (Cockpit DFA a1 Zone Suppression)**: Add `isSmoothedStream` gate in `HrvZoneIndicator.kt` and wire in `TrackingViewModel.kt`; update `TrackingHrvZoneIndicatorTest.kt`.
4. **Step 4 (Morning HRV Check Warning Card)**: Implement `SmoothedSignalWarningCard` in `MorningHrvCheckScreen.kt` conditioned on `uiState.signalQualityStatus`.
5. **Step 5 (HrvAlpha1ScatterCard Warning Banner)**: Add `isSmoothedStream` parameter and warning banner in `HrvAlpha1ScatterCard.kt`; update `HrvAlpha1ScatterChartTest.kt`.
6. **Step 6 (Contract & Parity Verification)**: Execute `TranslationParityTest.kt` and targeted test suites:
   `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: 100% translation parity across 9 locales, zero scroll regressions (Rule 72), and green zone suppression assertions.
* **Rollback**: Branch isolation allows full revert without impacting `develop` or `sprint/2026-46.2`.
