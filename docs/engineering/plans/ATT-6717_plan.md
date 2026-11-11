# Stage 3: Implementation Plan - ATT-6717: Deduplicate Scatter Card, SegmentedButton & Configured Thresholds

**Ticket**: [ATT-6717](https://rainerblind.atlassian.net/browse/ATT-6717)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-035` (`REQ-PER-035.1`, `REQ-PER-035.3`, `REQ-PER-035.4`), `REQ-UI-106`  
**Test Mapping**: `TST-PER-043` (`TST-PER-043.2`, `TST-PER-043.4`, `TST-PER-043.5`, `TST-PER-043.6`, `TST-PER-043.7`)  
**Branch**: `feature/ATT-6717` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Following Sprint `2026-46.1` Joint Review of ATT-3544, `HrvAlpha1ScatterCard` is duplicated across `MapDetailLayout.kt` and `WorkoutDetailTabbedContent.kt`, dead forwarding wrappers linger in `ui/aftermath/hrv/`, binary `FilterChip` violates `design_guidelines.md` §1.1, running pace mode is absent, thresholds are hardcoded ($0.75 / 0.50$), `selectedMode` latches to unrecorded streams, samples lack 5s decimation, and GPS time-domain scrub headers desync.

---

## 2. Traceability & Requirements Mapping
* `REQ-PER-035.1` (Telemetry tracks & scrub lookup) $\rightarrow$ `TST-PER-043.6` (Scrub sync)
* `REQ-PER-035.3` (Configured threshold guide lines & labels) $\rightarrow$ `TST-PER-043.2` (`TelemetryMetricGraphHrvTest`)
* `REQ-PER-035.4` (Single card, M3 SegmentedButton, Pace mode, 5s decimation) $\rightarrow$ `TST-PER-043.4`, `TST-PER-043.7`
* `REQ-UI-106` (9-language localization parity) $\rightarrow$ `TST-PER-043.5` (`TranslationParityTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Fallback Stability**: Default thresholds fall back to population physiological constants ($0.75$ and $0.50$) when unconfigured.
2. **Mathematical Invariants**: OLS regression, physiological filter bounds ($\alpha_1 \in [0.35, 1.15]$, artifact rate $\le 5\%$), and Rule 64 plot padding (`startPadding = 50.dp`) strictly preserved.
3. **Card Cleanliness**: Single canonical card rendering under `ZONES` in `WorkoutDetailTabbedContent.kt`; zero nested cards inside `MapDetailLayout.kt`.

---

## 4. Proposed Architectural Changes

### UI Consistency (`design_guidelines.md` §1.1, §5)
* **Reference Component**: `HeartRateZoneDistributionCard` / `PowerZoneDistributionCard` (M3 `SingleChoiceSegmentedButtonRow`).
* **Reused Components**: `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `SegmentedButtonDefaults`, `MetricItem`, `PaceFormatter`.
* **Theme Tokens**: `MaterialTheme.colorScheme.primary/surface`, `TTColor.Zone2`, `TTColor.Zone4`, `RoundedCornerShape(12.dp)`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Layout Deduplication & Dead Wrapper Cleanup
* Files: `MapDetailLayout.kt`, delete `ui/aftermath/hrv/{HrvAlpha1ScatterCard.kt, HrvAlpha1ScatterChart.kt}`, delete `test/.../ui/aftermath/hrv/HrvAlpha1ScatterChartTest.kt`.
* Action: Remove lines 800–809 in `MapDetailLayout.kt`; retain single canonical placement in `WorkoutDetailTabbedContent.kt`.

### Step 2: Time-Domain Scrub Parity in `MapDetailLayout.kt`
* Files: `MapDetailLayout.kt`.
* Action: Pass `isTimeDomain = (activeTelemetryDomain == ProfileXAxisDomain.TIME)` to `findNearestPoint` for DFA $\alpha_1$ and rMSSD.

### Step 3: Decimation, Pace Mode & Configured Thresholds in `HrvAlpha1ScatterChart.kt`
* Files: `HrvAlpha1ScatterChart.kt`.
* Action: Add `HrvScatterIntensityMode.PACE`. In `filterScatterPoints`, calculate pace intensity (`1.0 / speedMps`) and decimate to 5s steps (`stepSec = 5L`). Pass configured $T_1 / T_2$ into `calculateLinearRegression` and draw dynamic threshold lines/labels.

### Step 4: Material 3 SegmentedButton, Dynamic Keying & 9-Language Strings in `HrvAlpha1ScatterCard.kt`
* Files: `HrvAlpha1ScatterCard.kt`, `app/src/main/res/values*/strings_hrv.xml`.
* Action: Replace `FilterChip` with `SingleChoiceSegmentedButtonRow` supporting HR, Power, and Pace. Key `selectedMode` via `rememberSaveable(hasHr, hasPower, hasPace)`. Format pace crossings with `PaceFormatter`. Add localized strings across all 9 languages.

### Step 5: Wire Configured Thresholds into `TelemetryMetricGraph.kt`
* Files: `TelemetryMetricGraph.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`.
* Action: Accept `dfaA1ThresholdT1` and `dfaA1ThresholdT2` in `TelemetryMetricGraph.kt` and render dynamic threshold lines/labels. Pass from `TuningConfig`.

### Step 6: Targeted Unit & Localization Tests
* Files: `HrvAlpha1ScatterChartTest.kt`, `TelemetryMetricGraphHrvTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests, localization parity, and verification of zero references to `ui.aftermath.hrv.*`.
* **Rollback**: Isolated branch `feature/ATT-6717` allows clean revert before squash integration.
