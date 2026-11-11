# Stage 2 Requirement & Test Specification: ATT-6717 - Deduplicate Scatter Card, SegmentedButton & Configured DFA a1 Thresholds

**Ticket**: [ATT-6717](https://rainerblind.atlassian.net/browse/ATT-6717)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-035` (`REQ-PER-035.1`, `REQ-PER-035.3`, `REQ-PER-035.4`), `REQ-UI-106`  
**Test Spec ID**: `TST-PER-043` (`TST-PER-043.2`, `TST-PER-043.4`, `TST-PER-043.5`, `TST-PER-043.6`, `TST-PER-043.7`)  
**Branch**: `feature/ATT-6717` | **Author**: AI Agent 2 (Reviewer / Specifier, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PER-035)

### 1.1 Problem Statement & User Scope
* In `ATT-3544` (commit `3db57718`), `HrvAlpha1ScatterCard` was integrated in both `MapDetailLayout.kt` (lines 801–809) and `WorkoutDetailTabbedContent.kt` (lines 856–863), duplicating the card in Workout Details. Redundant forwarding wrappers in `ui/aftermath/hrv/` have zero production callers.
* The intensity toggle in `HrvAlpha1ScatterCard.kt` uses binary `FilterChip` (violating `design_guidelines.md` §1.1) and lacks running pace mode (`PaceFormatter`).
* Reference lines, Y-axis labels, and regression crossing estimates in `TelemetryMetricGraph.kt` and `HrvAlpha1ScatterChart.kt` hardcode `0.75` and `0.50`, ignoring athlete-configured `dfaA1ThresholdT1` and `dfaA1ThresholdT2` in `TuningPreferencesDataStore`.
* `selectedMode` unkeyed defaults to `POWER` on running workouts, `HrvScatterMath` plots overlapping 1-Hz samples without 5s decimation, and `MapDetailLayout.kt` passes `isTimeDomain = isTrackless`, breaking time-domain scrub headers on GPS workouts.

### 1.2 Functional & Architectural Requirements
1. **Deduplicated Placement & Wrapper Removal (`REQ-PER-035.4`)**: Retain `HrvAlpha1ScatterCard` strictly once in Workout Details under `WorkoutDetailSectionType.ZONES` in `WorkoutDetailTabbedContent.kt`. Delete duplicate rendering in `MapDetailLayout.kt` and redundant wrappers in `ui/aftermath/hrv/`.
2. **Material 3 SegmentedButton & Pace Mode (`REQ-PER-035.4`)**: Replace `FilterChip` with Material 3 `SingleChoiceSegmentedButtonRow` (`SegmentedButton`). Add `HrvScatterIntensityMode.PACE` utilizing `PaceFormatter` when cycling power is absent.
3. **Dynamic Mode Keying & 5s Decimation (`REQ-PER-035.4`)**: Key `selectedMode` via `rememberSaveable(hasHr, hasPower, hasPace)` to dynamically adapt to available streams. Decimate samples to $\ge 5\text{ s}$ intervals in `HrvScatterMath.filterScatterPoints`.
4. **Configured DFA $\alpha_1$ Thresholds (`REQ-PER-035.3`)**: Wire `dfaA1ThresholdT1` and `dfaA1ThresholdT2` from `TuningPreferencesDataStore` into `TelemetryMetricGraph.kt` and `HrvAlpha1ScatterChart.kt` (dashed lines, labels, crossing estimates).
5. **Time-Domain Scrub Parity (`REQ-PER-035.1`)**: Pass `isTimeDomain = (activeTelemetryDomain == ProfileXAxisDomain.TIME)` to `findNearestPoint` for DFA $\alpha_1$ and rMSSD scrub headers in `MapDetailLayout.kt`.
6. **9-Language Parity (`REQ-UI-106`)**: Provide localized strings for pace toggle and dynamic threshold labels across all 9 supported locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Single Rendering)**: *Given* a workout with HRV telemetry, *When* viewing Workout Details, *Then* `HrvAlpha1ScatterCard` renders strictly once under `ZONES`.
* **AC 2 (SegmentedButton & Running Pace)**: *Given* a running workout with pace and HRV data, *When* viewing `HrvAlpha1ScatterCard`, *Then* M3 `SingleChoiceSegmentedButtonRow` displays available streams and formats pace crossings via `PaceFormatter`.
* **AC 3 (Configured Thresholds)**: *Given* configured thresholds $T_1 = 0.80$ and $T_2 = 0.55$, *When* viewing HRV telemetry charts and scatter card, *Then* dashed reference lines and labels display $0.80$ and $0.55$.
* **AC 4 (Decimation & Scrub Accuracy)**: *Given* a GPS workout with 1-Hz telemetry scrubbed in time domain, *When* moving the scrub cursor, *Then* DFA $\alpha_1$ and rMSSD headers accurately show the scrubbed second's value, and scatter points are decimated to 5s steps.

### 1.4 System Invariants
* Default thresholds strictly fall back to physiological population values ($0.75$ and $0.50$).
* Physiological filter bounds ($\alpha_1 \in [0.35, 1.15]$, artifact rate $\le 5.0\%$) and Rule 64 plot bounds (`startPadding = 50.dp`) strictly preserved.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PER-035` (`REQ-PER-035.1`, `REQ-PER-035.3`, `REQ-PER-035.4`), targeting `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `HrvAlpha1ScatterCard.kt`, `HrvAlpha1ScatterChart.kt`, `TelemetryMetricGraph.kt`, and `strings_hrv.xml`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3544` (commit `3db57718`).
3. *Root Reason for Existing Formulation*: ATT-3544 established initial post-workout HRV charts with hardcoded population thresholds ($0.75 / 0.50$) and binary `FilterChip` (HR vs Power) without running pace support or user threshold customization.
4. *Preservation of Core Invariants*: Population defaults preserved when custom preferences are absent; OLS regression math and artifact suppression bounds remain strictly intact; zero regressions in non-HRV workouts.

---

## 3. Test Specification (TST-PER-043: Interactive DFA a1/rMSSD Charts & a1 Scatter Plot Suite)

### Test Case 1: Configured Threshold Guide Lines & Labels (`TST-PER-043.2`)
* **Scope**: `TelemetryMetricGraphHrvTest.kt`. Verify `TelemetryMetricGraph` renders horizontal dashed lines and Y-axis text matching configured `dfaA1ThresholdT1` and `dfaA1ThresholdT2`.

### Test Case 2: 5s Decimation, Pace Mode & Dynamic Thresholds (`TST-PER-043.4`)
* **Scope**: `HrvAlpha1ScatterChartTest.kt`. Verify `HrvScatterMath.filterScatterPoints` decimates 1-Hz points to 5s steps, filters and regresses `HrvScatterIntensityMode.PACE`, and calculates regression intersection for configured $T_1$ and $T_2$.

### Test Case 3: 9-Language Localization Audit (`TST-PER-043.5`)
* **Scope**: `TranslationParityTest.kt`. Verify that `hrv_scatter_toggle_pace`, `hrv_scatter_aerobic_threshold_fmt`, and `hrv_scatter_anaerobic_threshold_fmt` exist with matching format specifiers across all 9 locales.

### Test Case 4: Deduplicated Rendering & Wrapper Cleanliness (`TST-PER-043.7`)
* **Scope**: `HrvAlpha1ScatterChartTest.kt` & Architecture build audit. Verify single canonical invocation in `WorkoutDetailTabbedContent.kt`, removal from `MapDetailLayout.kt`, and zero references to `ui.aftermath.hrv.*`.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-PER-043.6`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-043.2]` | Unit / UI | `TelemetryMetricGraphHrvTest`: Configured threshold lines & labels | `REQ-PER-035.3` | Specified |
| `[TST-PER-043.4]` | Unit | `HrvAlpha1ScatterChartTest`: 5s decimation, Pace mode & $T_1/T_2$ crossings | `REQ-PER-035.4` | Specified |
| `[TST-PER-043.5]` | Localization | `TranslationParityTest`: 9-language pace & threshold format parity | `REQ-UI-106` | Specified |
| `[TST-PER-043.7]` | Contract / UI | Canonical placement in `WorkoutDetailTabbedContent` & dead file removal | `REQ-PER-035.4` | Specified |
| `[TST-PER-043.6]` | Regression | Tier 1 test execution across modified packages | `REQ-PRO-001`, `REQ-PER-035.1` | Specified |
