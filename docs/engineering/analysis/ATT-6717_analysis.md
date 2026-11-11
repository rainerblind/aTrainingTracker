# Stage 1 Analysis: ATT-6717 - Deduplicate HrvAlpha1ScatterCard, SegmentedButton & Configured DFA a1 Thresholds

**Ticket**: [ATT-6717](https://rainerblind.atlassian.net/browse/ATT-6717)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6717` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Following Sprint `2026-46.1` Joint Review of ATT-3544, four visual and functional defects were identified in Workout Details HRV analytics:
1. **Duplicate Scatter Card & Dead Wrappers**: `HrvAlpha1ScatterCard` is rendered in both `MapDetailLayout.kt` (lines 801–809) and `WorkoutDetailTabbedContent.kt` (lines 856–863 under `WorkoutDetailSectionType.ZONES`), rendering duplicate cards. Redundant forwarding wrappers in `ui/aftermath/hrv/` have zero production callers.
2. **Chip Anti-Pattern & Missing Pace**: Binary `FilterChip` in `HrvAlpha1ScatterCard.kt` violates `design_guidelines.md` §1.1 (mandating Material 3 `SingleChoiceSegmentedButtonRow`), and lacks Running Pace mode for running workouts.
3. **Hardcoded Thresholds**: Reference lines, labels, and regression crossings in `TelemetryMetricGraph.kt` and `HrvAlpha1ScatterChart.kt` hardcode `0.75` and `0.50`, ignoring athlete-configured `dfaA1ThresholdT1` and `dfaA1ThresholdT2` in `TuningPreferencesDataStore`.
4. **State Latching, Sample Overlap & Scrub Desync**: `selectedMode` is not keyed on stream availability (`rememberSaveable(hasHr, hasPower, hasPace)`), leaving it stuck on `POWER` for running workouts. `HrvScatterMath` plots overlapping 1-Hz points instead of 5s steps, and `MapDetailLayout.kt` passes `isTimeDomain = isTrackless` to `findNearestPoint`, corrupting time-domain scrub headers on GPS workouts.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * *`MapDetailLayout.kt` (lines 801–809)*: Confirmed `HrvAlpha1ScatterCard` is nested inside the telemetry graphs `ElevatedCard`, duplicating the canonical card rendered in `WorkoutDetailTabbedContent.kt` under `WorkoutDetailSectionType.ZONES`.
  * *`ui/aftermath/hrv/`*: Verified zero callers in `app/src/main/` for `HrvAlpha1ScatterCard.kt` and `HrvAlpha1ScatterChart.kt`; existing test merely delegates to `ui.aftermath.workoutdetail.HrvAlpha1ScatterChartTest`.
  * *`HrvAlpha1ScatterCard.kt` (lines 82–84, 172–190)*: Unkeyed `rememberSaveable` defaults to `POWER` if ever toggled; `FilterChip` row lacks M3 `SingleChoiceSegmentedButtonRow` and `PACE` intensity mode.
  * *`TelemetryMetricGraph.kt` (lines 314, 678–681) & `HrvAlpha1ScatterChart.kt` (lines 247–270)*: Reference lines and Y-axis labels hardcode `0.75` / `0.50` rather than consuming configured values from `TuningConfig`.
  * *`MapDetailLayout.kt` (lines 725, 764)*: `findNearestPoint(..., isTimeDomain = isTrackless)` treats scrub seconds as distance meters when GPS workouts are displayed in Time domain (`activeTelemetryDomain == ProfileXAxisDomain.TIME`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Deduplicate `HrvAlpha1ScatterCard`: retain single canonical placement in `WorkoutDetailTabbedContent.kt` under `WorkoutDetailSectionType.ZONES` and remove lines 800–809 in `MapDetailLayout.kt`.
  * Delete redundant dead forwarding files `ui/aftermath/hrv/HrvAlpha1ScatterCard.kt`, `ui/aftermath/hrv/HrvAlpha1ScatterChart.kt`, and test wrapper `ui/aftermath/hrv/HrvAlpha1ScatterChartTest.kt`.
  * Replace `FilterChip` toggle with Material 3 `SingleChoiceSegmentedButtonRow` / `SegmentedButton` supporting HR, Power, and Pace (`PaceFormatter`).
  * Key `selectedMode` via `rememberSaveable(hasHr, hasPower, hasPace)` to dynamically adapt to available telemetry streams.
  * Wire configured `dfaA1ThresholdT1` and `dfaA1ThresholdT2` from `TuningPreferencesDataStore` into `TelemetryMetricGraph.kt` and `HrvAlpha1ScatterChart.kt`.
  * Decimate `PathPoint` samples to 5s steps in `HrvScatterMath.filterScatterPoints`.
  * Fix scrub lookup in `MapDetailLayout.kt` using `isTimeDomain = (activeTelemetryDomain == ProfileXAxisDomain.TIME)`.
* **Out-of-Scope Non-Goals**:
  * Altering live tracking cockpit DFA $\alpha_1$ indicators or morning readiness checks.
  * Modifying `WorkoutSamples.db` schema or altering raw `hrvDfaA1` persistence.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-035.3` (Threshold Reference Lines) & `REQ-PER-035.4` ($\alpha_1$ vs. Power/HR Scatter Plot) in `docs/requirements/req_aftermath_periods.md`.
* **Historical Origin & Commit Trace**: Added in commit `3db57718` (Ticket `ATT-3544`, Sprint `2026-46.1`).
* **Root Reason for Existing Formulation**: ATT-3544 initially targeted basic cycling/running post-workout charts with hardcoded population thresholds ($\alpha_1 = 0.75, 0.50$) without runner-specific pace mode or customized threshold datastore wiring.
* **Preservation of Core Invariants**: Defaults fall back to standard physiological thresholds (0.75 and 0.50); OLS linear regression math and artifact suppression bounds ($\le 5\%$) remain strictly intact.

---

## 5. Architectural Strategy & High-Level Solution
1. **Layout Deduplication & Cleanliness**: Remove duplicate `HrvAlpha1ScatterCard` invocation from `MapDetailLayout.kt`. Delete dead files in `ui/aftermath/hrv/`.
2. **SegmentedButton & Running Pace**: In `HrvAlpha1ScatterCard.kt`, extend `HrvScatterIntensityMode` with `PACE`. Render `SingleChoiceSegmentedButtonRow` when multiple streams are present. Format pace values and threshold crossing metrics with `PaceFormatter`.
3. **Decimation & Dynamic Keying**: Update `HrvScatterMath.filterScatterPoints` with 5s time decimation. Key `selectedMode` on `rememberSaveable(hasHr, hasPower, hasPace)`.
4. **Configured Thresholds**: Pass `dfaA1ThresholdT1` and `dfaA1ThresholdT2` from `TuningConfig` into `TelemetryMetricGraph.kt` and `HrvAlpha1ScatterCard.kt` $\rightarrow$ `HrvAlpha1ScatterChart.kt`.
5. **Time-Domain Scrub Fix**: Pass `isTimeDomain = (activeTelemetryDomain == ProfileXAxisDomain.TIME)` to `findNearestPoint` for both DFA $\alpha_1$ and rMSSD in `MapDetailLayout.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regressions in existing `TelemetryMetricGraph` and `ElevationProfile` scrubbing contracts.
  2. Single canonical rendering of `HrvAlpha1ScatterCard` in Workout Details.
  3. Strict adherence to `design_guidelines.md` §1.1 mode switcher conventions.
* **Risk Rating**: **LOW**. Scoped exclusively to UI presentation, formatting, and layout deduplication with zero database migrations or background service impacts.
