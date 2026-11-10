# Stage 3: Implementation Plan - ATT-3544: Interactive DFA a1/rMSSD Charts & a1 Scatter Plot

**Ticket**: [ATT-3544](https://rainerblind.atlassian.net/browse/ATT-3544)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3544`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  
**Requirement Mapping**: `REQ-PER-035` (`REQ-PER-035.1`..`REQ-PER-035.6`)  
**Test Mapping**: `TST-PER-043` (`TST-PER-043.1`..`TST-PER-043.6`)  

---

## 1. Problem Description & Background
Following ATT-3543, lossless beat intervals and centered post-workout HRV streams (`HRV_DFA_A1`, `HRV_RMSSD`, `HRV_ARTIFACT_RATE`) are persisted in `WorkoutSamples.db`. Workout Details currently lacks visual inspection for these streams. The implementation adds interactive time-series tracks (`HRV_DFA_A1`, `HRV_RMSSD`) in `MapDetailLayout` / `TelemetryMetricGraph` with artifact suppression gap breaking and hatched overlays, horizontal plot alignment parity (`startPadding = 50.dp`, Rule 64), dashed threshold lines ($\alpha_1 = 0.75, 0.50$), and an interactive intensity scatter card (`HrvAlpha1ScatterCard.kt`) in Workout Details.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PER-035.1` (HRV telemetry tracks), `REQ-PER-035.2` (Artifact gap breaking & hatching), `REQ-PER-035.3` (Threshold lines & Y-clamping), `REQ-PER-035.4` ($\alpha_1$ vs. HR/Power scatter plot), `REQ-PER-035.5` (Graceful omission for non-HRV workouts), `REQ-PER-035.6` (Touch disambiguation & localization).
* **Test Mapping**: `TST-PER-043.1` (`PathPoint` enrichment), `TST-PER-043.2` (`TelemetryMetricGraph` tracks & bounds), `TST-PER-043.3` (Artifact gap polyline break), `TST-PER-043.4` (Scatter plot regression & point filtering), `TST-PER-043.5` (Localization parity & token audit), `TST-PER-043.6` (Tier 1 regression suite).

---

## 3. System Invariants & Preserved Behavior
1. **Plot Alignment Parity (Rule 64)**: Stacked charts share `startPadding = 50.dp` and right boundary (`0.72f` landscape split).
2. **Architecture Conformance**: UI classes in `ui/**` query strictly via `WorkoutRepository`; zero raw database manager calls.
3. **Composition State Stability (Rule 66)**: Zero snapshot state writes during composition.
4. **Graceful Omission**: Non-HRV workouts completely omit HRV tracks and scatter card with zero layout shifts.

---

## 4. Proposed Architectural Changes
* **Models & Data Layer (`MapModels.kt`, `WorkoutRepository.kt`)**: Add `@JvmOverloads constructor` fields `hrvDfaA1`, `hrvRmssd`, `hrvArtifactRate` to `PathPoint`. Populate from `WorkoutSamples` in `WorkoutRepository.kt`.
* **Time-Series Chart Tracks (`TelemetryMetricGraph.kt`, `MapDetailLayout.kt`)**: Extend `TelemetryMetricType` with `HRV_DFA_A1` and `HRV_RMSSD`. Break polyline when artifact rate $> 5\%$ or sample null; draw hatched muted band over gap. Draw dashed threshold lines at $\alpha_1 = 0.75$ and $0.50$, clamped to $[0.2, 1.4]$ (Rule 48).
* **$\alpha_1$ Intensity Scatter Plot (`HrvAlpha1ScatterChart.kt`, `HrvAlpha1ScatterCard.kt`)**: Interactive card plotting intensity (HR bpm / Power Watts) vs. $\alpha_1 \in [0.35, 1.15]$ for artifact-free points ($\le 5\%$) with least-squares linear regression line and threshold crosshairs.
* **UI Consistency**: Reference screen `MapDetailLayout.kt`; theme tokens `TTColor`, `MaterialTheme.colorScheme`; shapes `MaterialTheme.shapes.medium`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Enrich PathPoint and WorkoutRepository Telemetry Loading
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`, `WorkoutRepository.kt`
* Add `@JvmOverloads` fields `hrvDfaA1`, `hrvRmssd`, `hrvArtifactRate` to `PathPoint`. Read cursor columns in `WorkoutRepository`.

### Step 2: Implement HRV Tracks, Threshold Lines & Gap Hatching in TelemetryMetricGraph
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`, `TelemetryMetricUtils.kt`
* Add `HRV_DFA_A1` and `HRV_RMSSD` to `TelemetryMetricType`. Implement non-continuous polyline and hatched background gap overlay.

### Step 3: Implement HrvAlpha1ScatterChart & HrvAlpha1ScatterCard
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/hrv/HrvAlpha1ScatterChart.kt`, `HrvAlpha1ScatterCard.kt`
* Implement point filtering ($\alpha_1 \in [0.35, 1.15]$, artifact rate $\le 5\%$), linear regression line fitting, and toggleable HR/Power axis.

### Step 4: Integrate into MapDetailLayout & WorkoutDetailTabbedContent
* Files: `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`
* Conditionally render HRV tracks and scatter card when `TelemetryMetricUtils.hasHrvData(path)` is true.

### Step 5: Unit & Contract Tests
* Files: `HrvPathPointEnrichmentTest.kt`, `TelemetryMetricGraphHrvTest.kt`, `HrvAlpha1ScatterChartTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.hrv.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests verifying `PathPoint` backwards compatibility, canvas plot bounds, artifact gap breaking, regression line calculation, and architecture conformance.
* **Rollback**: Branch isolation (`feature/ATT-3544`) allows safe revert without impacting `sprint/2026-46.1`.
