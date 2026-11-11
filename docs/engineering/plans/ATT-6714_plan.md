# Stage 3: Implementation Plan - ATT-6714: Split/Merge Artifact Beats, NeuroKit2 DFA Parity & Stale HRV State Handling

**Ticket**: [ATT-6714](https://rainerblind.atlassian.net/browse/ATT-6714)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-060` (`REQ-TRK-060.2`, `REQ-TRK-060.4`, `REQ-TRK-060.5`, `REQ-TRK-060.6`)  
**Test Mapping**: `TST-TRK-060` (`TST-TRK-060.2`, `TST-TRK-060.4`, `TST-TRK-060.5`, `TST-TRK-060.6`, `TST-TRK-060.7`)  
**Branch**: `feature/ATT-6714` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint `2026-46.1` follow-up identified four defects and architectural divergences: 1-for-1 substitution of missed beats and RF gaps distorts cumulative beat-index axes; extra short beats are not merged; `HRVMetricsDevice` bypasses `filterLiveCausal()`; `DfaCalculator.kt` uses non-overlapping segments rather than 50% overlapping windows matching pinned `neurokit2 == 0.2.10`; `HRVMetricsDevice` freezes stale telemetry upon strap disconnection and fails to reset on `RESET_ACCUMULATORS_INTENT`; and the artifact gate threshold is hardcoded.

---

## 2. Traceability & Requirements Mapping
* `REQ-TRK-060.2` (Beat split/merge/gap expansion & `filterLiveCausal`) $\rightarrow$ `TST-TRK-060.2` (`RrArtifactFilterTest`)
* `REQ-TRK-060.4` (DFA 50% overlap & configurable 3–5% artifact gate) $\rightarrow$ `TST-TRK-060.4` (`DfaCalculatorTest`, `TuningPreferencesDataStoreTest`)
* `REQ-TRK-060.5` (Causal device wiring, stale clearance & reset lifecycle) $\rightarrow$ `TST-TRK-060.5` (`HRVMetricsDeviceTest`)
* `REQ-TRK-060.6` (NeuroKit2 0.2.10 golden oracle parity across 4 regimes) $\rightarrow$ `TST-TRK-060.6` (`HrvGoldenOracleTest`)
* `REQ-TRK-060.1` (9-language localization parity in `strings_hrv.xml`) $\rightarrow$ `TST-TRK-060.1` (`TranslationParityTest`)
* `REQ-PRO-001` (Tier 1 regression suite) $\rightarrow$ `TST-TRK-060.7` (Clean-room build & test pass)

---

## 3. System Invariants & Preserved Behavior
1. **Lifecycle & State Gating**: Rule 51 (constructor state seeds identical baseline as `resetSession`) and Rule 52 (zero telemetry rows emitted before physical RR data arrives) strictly preserved.
2. **DFA Algorithmic Invariants**: Scale range $n \in [4, 16]$ with linear detrending (order 1) and exact 50% overlap (`step = boxSize / 2`), matching `nk.fractal_dfa(scale=range(4, 17), overlap=True)`.
3. **Living Doc Parity**: Zero regressions across existing test suites; living documentation indexes synchronized.

---

## 4. Proposed Architectural Changes

### UI Consistency (`design_guidelines.md` §3.1, §5)
* **Reference Component**: `PhysiologyModelsSection.kt`.
* **Reused Components**: `TuningSliderItem`.
* **Theme Tokens**: `MaterialTheme.colorScheme.primary`, `outlineVariant`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Beat Splitting, Short Beat Merging & Gap Expansion (`RrArtifactFilter.kt`)
* Files: `RrArtifactFilter.kt`, `MorningHrvCheckViewModel.kt`.
* Action: In `filterBidirectionalBeats` and `filterLiveCausal`, split missed beats into $k = \max(2, \text{round}(rr / med))$ equal intervals ($rr / k$), merge adjacent short beats ($rr_1 + rr_2$), and expand `gapBeats` to $gapBeats + 1$ equal intervals. Update `filterLiveCausal` return type to `FilterResult` and adapt `MorningHrvCheckViewModel.kt`.

### Step 2: DFA 50% Overlapping Windows (`DfaCalculator.kt`)
* Files: `DfaCalculator.kt`.
* Action: Replace non-overlapping $2K$ segments with 50% overlapping windows (`step = max(1, boxSize / 2)`), computing linear detrending, average segment variance, and log-log slope matching NeuroKit2 0.2.10.

### Step 3: Pinned NeuroKit2 Golden Reference Vectors (`HrvGoldenOracleTest.kt`)
* Files: `HrvGoldenOracleTest.kt`.
* Action: Add 4 pinned golden vectors: White noise ($\approx 0.5144$), Threshold ($\approx 0.7550$), Pink noise ($\approx 0.9622$), and Aerobic ($\approx 1.4379$) verifying $|\Delta\alpha_1| \le 0.02$.

### Step 4: Causal Wiring, Stale Metric Clearance & Reset Lifecycle (`HRVMetricsDevice.kt`)
* Files: `HRVMetricsDevice.kt`.
* Action: In `calculateMetrics()`, call `artifactFilter.filterLiveCausal(allBeats)`. Emit `null` to `mHrvRmssdSensor`, `mHrvDfaA1Sensor`, and `mHrvArtifactRateSensor` when queue drains after active data. Override `onAccumulatorsReset()` to invoke `resetSession()`. Read configurable artifact gate threshold.

### Step 5: Configurable Artifact Gate Setting & Localization (`TuningPreferencesDataStore.kt`, `PhysiologyModelsSection.kt`, `strings_hrv.xml`)
* Files: `TuningPreferencesDataStore.kt`, `PhysiologyModelsSection.kt`, `app/src/main/res/values*/strings_hrv.xml`.
* Action: Add `KEY_HRV_ARTIFACT_GATE_THRESHOLD_PERCENT` (3.0%..5.0%, default 5.0%) with Flow/getter/setter. Add `TuningSliderItem` in `PhysiologyModelsSection.kt`. Maintain 100% 9-language translation parity in `strings_hrv.xml`.

### Step 6: Targeted Unit & Regression Test Verification
* Files: `RrArtifactFilterTest.kt`, `DfaCalculatorTest.kt`, `HrvGoldenOracleTest.kt`, `HRVMetricsDeviceTest.kt`, `TuningPreferencesDataStoreTest.kt`, `TranslationParityTest.kt`.
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.hrv.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests, golden oracle assertions, and translation parity tests executed under `/tmp/att_build_merge.lock`.
* **Rollback**: Isolated branch `feature/ATT-6714` guarantees zero impact on sprint branch until Stage 5 squash merge.
