# Stage 2: Requirement & Test Specification - ATT-6745: Smoothed RR Warnings & Sensor Guidance

**Ticket**: [ATT-6745](https://rainerblind.atlassian.net/browse/ATT-6745) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6745`  
**Requirement Mapping**: `REQ-TRK-068` | **Test Spec ID**: `TST-TRK-068`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-068)

### 1.1 Problem Statement & Rationale
When athletes pair budget ECG chest straps applying firmware moving-average smoothing (e.g., Magene H64/H303, CooSpo) or synthetic stepped RR streams, the outlier filter reports 0.0% artifacts while DFA $\alpha_1$ is falsely inflated (>1.40) into an easy aerobic Zone 2 and $rMSSD$ collapses. Without explicit UI warnings, athletes pacing live efforts receive deceptive guidance, morning readiness baselines ($SWC$) are polluted with artificially low $rMSSD$, and athletes are unaware their sensor hardware is unsuitable for aerobic threshold estimation. The presentation layer must surface prominent cautionary warnings across all 4 touchpoints and provide clear sensor hardware recommendations with 9-language parity.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-068.1** (*HrvInfoBottomSheet Sensor Recommendation*): Update `hrv_info_sensor_recommendation_desc` in `strings_hrv.xml` to distinguish recommended unsmoothed ECG straps (Polar H10/H9, Garmin HRM-Dual/Pro/Pro Plus) from unsuitable sensors (optical wrist sensors, Magene, CooSpo firmware-smoothed straps).
* **REQ-TRK-068.2** (*Live Cockpit Smoothed Warning & Zone Suppression*): `HrvInfoBottomSheet.kt` SHALL render a top warning banner when active `signalQualityStatus` is `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`; `HrvZoneIndicator.kt` and `TrackingViewModel.kt` SHALL suppress/caution the green aerobic Zone 2 indicator when `isSmoothedStream` is true.
* **REQ-TRK-068.3** (*Morning HRV Check Smoothed Warning Card*): `MorningHrvCheckScreen.kt` SHALL render a dedicated `SmoothedSignalWarningCard` when measurement completion yields `signalQualityStatus == SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`, cautioning the athlete against saving into longitudinal readiness baselines.
* **REQ-TRK-068.4** (*Post-Workout DFA Alpha 1 Scatter Warning Banner*): `HrvAlpha1ScatterCard.kt` SHALL render an inline warning banner when workout RR intervals were detected as firmware-smoothed or synthetic (`isSmoothedStream == true`), indicating threshold estimates are uncalibrated.
* **REQ-TRK-068.5** (*9-Language Localization Parity*): All warning and guidance strings SHALL maintain 100% key and format-specifier parity across all 9 canonical locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
* **REQ-TRK-068.6** (*Theme Tokens & Invariants*): Centralized `TTColor.ArtifactWarning*` / `TTColor.Warning` theme tokens (Rule 71) SHALL be used; standard zone pacing and independent $>5\%$ artifact warnings SHALL remain unaffected.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* `HrvInfoBottomSheet`, *When* rendered, *Then* sensor guidance distinguishes recommended from unsuitable smoothed straps.
* *Given* `signalQualityStatus == SMOOTHED_STREAM`, *When* viewing `HrvInfoBottomSheet` or cockpit tile, *Then* a top warning banner appears and green Zone 2 indicator is suppressed.
* *Given* completed morning check with smoothed intervals, *When* viewing `MorningHrvCheckScreen`, *Then* `SmoothedSignalWarningCard` warns against saving to baseline.
* *Given* post-workout DFA chart with smoothed RR stream, *When* rendering `HrvAlpha1ScatterCard`, *Then* an inline warning banner is shown.
* *Invariants*: Independent $> 5\%$ artifact warnings preserved; Rule 71 theme tokens enforced; Rule 72 modal sheet scrolling safety maintained; zero SQLite schema modifications; 100% unit test pass rate.

---

## 2. Test Specification (TST-TRK-068)

### Test Case 1: HrvInfoBottomSheet Guidance & Top Warning (`TST-TRK-068.1`)
* **Scope**: UI / Compose Unit Test (`HrvInfoBottomSheetTest.kt`)
* **Assertion**: Verify Polar/Garmin recommended and Magene/CooSpo/optical unsuitable guidance; verify conditional top warning banner on `SMOOTHED_STREAM` and `QUANTIZED_SYNTHETIC`.

### Test Case 2: Cockpit DFA a1 Zone 2 Suppression (`TST-TRK-068.2`)
* **Scope**: Logic & ViewModel Unit Tests (`HrvZoneIndicatorTest.kt`, `TrackingViewModelHrvTest.kt`)
* **Assertion**: Verify green Zone 2 suppressed when `isSmoothedStream` is true; verify standard Zone 2 rendered on valid unsmoothed streams.

### Test Case 3: Morning HRV Check Smoothed Warning Card (`TST-TRK-068.3`)
* **Scope**: UI / Compose Unit Test (`MorningHrvCheckScreenTest.kt`)
* **Assertion**: Verify `SmoothedSignalWarningCard` renders on smoothed status and co-exists independently with `highArtifactWarning`.

### Test Case 4: HrvAlpha1ScatterCard Warning Banner (`TST-TRK-068.4`)
* **Scope**: UI / Compose Unit Test (`HrvAlpha1ScatterCardTest.kt`)
* **Assertion**: Verify inline warning banner renders when `isSmoothedStream == true` and hides when false.

### Test Case 5: 9-Language Translation Parity Audit (`TST-TRK-068.5`)
* **Scope**: Localization Contract Test (`TranslationParityTest.kt`)
* **Assertion**: Verify 100% key and format specifier parity in `strings_hrv.xml` across all 9 supported locales.

### Test Case 6: Tier 1 Targeted Test Suite Execution (`TST-TRK-068.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.cockpit.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-068.1` | UI (Compose) | `HrvInfoBottomSheet.kt` | `REQ-TRK-068.1`, `REQ-TRK-068.2` | Specified |
| `TST-TRK-068.2` | Logic (Indicator) | `HrvZoneIndicator.kt`, `TrackingViewModel.kt` | `REQ-TRK-068.2` | Specified |
| `TST-TRK-068.3` | UI (Compose) | `MorningHrvCheckScreen.kt` | `REQ-TRK-068.3` | Specified |
| `TST-TRK-068.4` | UI (Compose) | `HrvAlpha1ScatterCard.kt` | `REQ-TRK-068.4` | Specified |
| `TST-TRK-068.5` | Localization | `strings_hrv.xml` (9 locales) | `REQ-TRK-068.5` | Specified |
| `TST-TRK-068.6` | Regression | `ui.hrv.*`, `ui.cockpit.*`, `localization.*` | `REQ-PRO-001` | Specified |
