# Stage 1 Analysis: ATT-6745 - Surface Smoothed/Unsuitable Chest-Strap Warnings & Sensor Guidance

**Ticket**: [ATT-6745](https://rainerblind.atlassian.net/browse/ATT-6745)  
**Sub-task**: [ATT-6777](https://rainerblind.atlassian.net/browse/ATT-6777) (`[Analysis]`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6745` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
When athletes use budget dual-band ECG chest straps with firmware moving-average smoothing (e.g. Magene H64/H303, CooSpo) or synthetic constant-BPM streams, the outlier filter reports 0.0% artifacts while DFA $\alpha_1$ is falsely inflated (>1.40) and $rMSSD$ collapsed (3–7 ms). This misleads athletes across 4 key touchpoints:
1. *Live Cockpit*: An artificial $\alpha_1 > 1.40$ renders a green Zone 2 indicator, falsely suggesting easy aerobic effort during hard intervals.
2. *Morning HRV Check*: Athletes unknowingly save smoothed readings ($rMSSD = 5\text{ ms}$), polluting their 60-day Smallest Worthwhile Change ($SWC$) baseline in `HrvReadiness.db`.
3. *In-App Guidance (`HrvInfoBottomSheet`)*: Existing text only warns against optical wrist sensors; owners of smoothed budget straps assume their hardware is fully suitable for DFA $\alpha_1$.
4. *Post-Workout Aftermath (`HrvAlpha1ScatterCard`)*: Threshold estimates ($\alpha_1 = 0.75 / 0.50$) computed from smoothed sessions are unreliable yet lack cautionary labeling.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Underlying Cause**: ATT-6744 constructed `HrvSignalQualityAnalyzer` and exposed `HrvSignalQualityResult` on `HRVMetricsDevice`, `MorningHrvUiState`, and `HrvPostWorkoutRecomputer`. However, presentation and guidance layers currently ignore this signal quality status.
* **Independent Peer Review & Verification**:
  * *`HrvInfoBottomSheet.kt`*: Audited `strings_hrv.xml`. String `hrv_info_sensor_recommendation_desc` only mentions optical wrist sensors. Explicit categorization of recommended straps (Polar H10/H9, Garmin HRM-Dual/Pro/Pro Plus) vs. unsuitable smoothed straps (Magene, CooSpo) is missing.
  * *`SensorFieldView.kt` / `TrackingViewModel.kt`*: DFA $\alpha_1$ tile queries `HrvZoneIndicator.calculateZoneColor(alpha1, t1, t2)`. It does not check if the active stream is smoothed, allowing green Zone 2 to render even when `HRVMetricsDevice.signalQualityResult.status == SMOOTHED_STREAM`.
  * *`MorningHrvCheckScreen.kt`*: Displays `ArtifactWarningCard` only when `uiState.highArtifactWarning` is true. `uiState.signalQualityStatus` is populated by `MorningHrvCheckViewModel` (ATT-6744) but has no corresponding UI warning card.
  * *`HrvAlpha1ScatterCard.kt`*: Lacks a warning banner for sessions where recorded RR intervals were smoothed.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Update `hrv_info_sensor_recommendation_desc` in `strings_hrv.xml` across all 9 canonical languages (EN, DE, ES, FR, IT, JA, NL, PL, PT) to distinguish recommended unsmoothed straps from unsuitable smoothed/optical sensors.
  * Add conditional warning banner at the top of `HrvInfoBottomSheet.kt` when `signalQualityStatus` is `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`.
  * Suppress/caution the green aerobic zone bar in `HrvZoneIndicator.calculateZoneColor` and `TrackingViewModel.kt` when `isSmoothedStream` is true.
  * Surface a dedicated warning card in `MorningHrvCheckScreen.kt` when a completed morning check is flagged as `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC` (advising against saving into baseline).
  * Surface an inline warning banner in `HrvAlpha1ScatterCard.kt` when `isSmoothedStream` is true.
  * Unit and Compose tests verifying warning displays and 100% 9-language translation parity.
* **Out-of-Scope Non-Goals**:
  * Modifying DSP classifier algorithms in `HrvSignalQualityAnalyzer` (verified in ATT-6744).
  * Altering SQLite database schemas or `WorkoutSummaries.db` / `HrvReadiness.db` column layouts.
  * Modifying non-HRV sensors or non-HRV telemetry tiles.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-068` (*Firmware-Smoothed Chest Strap UI Warnings & Sensor Compatibility Guidance*) in `docs/requirements/req_tracking_cockpit.md`, extending `REQ-TRK-061` (*Live HRV Telemetry & Info Sheet*), `REQ-PER-032` (*Morning HRV*), and `REQ-PER-035` (*Post-Workout DFA Scatter*).
* **Historical Origin & Commit Trace**: Commit `14292b35` (`ATT-3542`) created `HrvInfoBottomSheet` and `HrvZoneIndicator`; Commit `5551c89e` (`ATT-6744`) delivered the detection engine.
* **Root Reason for Existing Formulation**: Earlier versions assumed all ECG chest straps produced true raw R-waves, whereas only optical wrist sensors suffered interval smoothing.
* **Preservation of Core Invariants**:
  * *Standard Zone Pacing*: Valid unsmoothed streams maintain identical threshold zone coloring (`TTColor.Zone2`, `Zone3`, `Zone4`).
  * *Artifact Warning Parity*: The existing `> 5%` artifact warning cards in live cockpit and morning check continue to function independently without interference.
  * *Rule 71 & 72*: Centralized theme tokens (`TTColor.ArtifactWarning*`) and zero nested `verticalScroll` inside `AppModalBottomSheet`.

---

## 5. Architectural Strategy & High-Level Solution
* **UI Presentation Layer (Compose)**:
  * `HrvInfoBottomSheet.kt`: Conditional warning card when `signalQualityStatus` is smoothed/synthetic.
  * `MorningHrvCheckScreen.kt`: `SmoothedSignalWarningCard` when `uiState.signalQualityStatus` is `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`.
  * `HrvAlpha1ScatterCard.kt`: `SmoothedWorkoutWarningBanner` when `isSmoothedStream` is flagged.
  * `HrvZoneIndicator.kt` / `TrackingViewModel.kt`: Gated zone resolution via `isSmoothedStream`.
* **Localization**:
  * Domain-partitioned additions in `strings_hrv.xml` across all 9 canonical languages with 100% format string and parity compliance.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero layout regression or UI clipping; Rule 72 modal bottom sheet scroll safety strictly enforced.
  2. 100% 9-language translation parity in `strings_hrv.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  3. No breaking changes or regressions in existing HRV unit/UI test suites.
* **Risk Rating**: **LOW** (Presentation-only enhancements with localized string additions; strictly bounded at 2 SP).
