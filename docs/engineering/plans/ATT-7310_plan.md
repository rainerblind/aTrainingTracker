# Stage 3: Implementation Plan - ATT-7310: Enable Anchor-Based Heart Rate Training Zone Updates from HRVT1/HRVT2 Consensus

**Ticket**: [ATT-7310](https://rainerblind.atlassian.net/browse/ATT-7310)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PER-037.2`, `REQ-PER-037.7`  
**Test Mapping**: `TST-PER-045` (`TST-PER-045.8`..`TST-PER-045.11`)  
**Branch**: `subagent-Lane-1-Agent---ATT-7310-self-d5962554` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
Building on `ATT-7317` (promoted DFA $\alpha_1$ thresholds in Zone Settings) and `ATT-3555` (anchor-based FTP updates), `ATT-7310` introduces automated, consensus-gated Heart Rate training zone updates:
- Anchoring Zone 2 aerobic ceiling at $HRVT_1$ and Zone 4 anaerobic / LTHR ceiling at $HRVT_2$.
- Protecting athletes against single-session HR spikes through multi-session consensus ($\ge 3$ sessions within 30 days agreeing within $\le 6\text{ bpm}$) or high-confidence guided ramp/step tests.
- Presenting a clear modal confirmation dialog detailing old vs. new zone boundaries, preserving the athlete's ability to tweak zones manually in Zone Settings.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-037.2`, `REQ-PER-037.7` (*Anchor-Based Heart Rate Training Zone Updates from HRVT1/HRVT2 Consensus*)
* **Test Mapping**: `TST-PER-045` (`TST-PER-045.8`..`TST-PER-045.11` in `docs/tests/tst_aftermath_periods.md`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: Existing `ZoneCalculationMathTest`, `HrvZoneComparisonMathTest`, and `TrackOnMapAftermathViewModelTest` continue to pass 100%.
2. **Strict Monotonicity Invariant**: Zone thresholds calculated from anchors guarantee $Z_1 < Z_2 < Z_3 < Z_4 < HR_{\max}$.
3. **No Silent Overwrites**: Heart rate zones are updated only upon explicit user confirmation via a modal dialog.
4. **Single-Session Noise Rejection**: Single free-ride workouts with aberrant HR spikes evaluate to `LOCKED`.
5. **Localization Parity**: 100% string parity across all 9 supported locales in `strings_hrv.xml`.
6. **Parent Human Gate Invariance**: The parent ticket advances to `Final Review (Human)` and is never closed by agents.

---

## 4. Proposed Architectural Changes

### Component 1: Zone Calculation Math (`ZoneCalculationMath.kt`)
* Add `calculateThresholdAnchoredHeartRateZones(hrvt1: Int, hrvt2: Int, hrMax: Int? = null, hrRest: Int? = null): HeartRateZoneThresholds`.
* Sets $Z_2 = hrvt1$, $Z_4 = hrvt2$, $Z_3 = \text{round}((hrvt1 + hrvt2)/2.0)$, and $Z_1 = \text{round}(hrRest + 0.70 \times (hrvt1 - hrRest))$ if $hrRest < hrvt1$, else $\text{round}(0.85 \times hrvt1)$. Enforces strict ascending order.

### Component 2: HRV Consensus & Comparison Math (`HrvZoneComparisonMath.kt`)
* Extend `HrvSessionThreshold` with `hrvt1Bpm: Int? = null`, `hrvt2Bpm: Int? = null`, `hrConfidence: ThresholdConfidence = confidence`.
* Add `HrConsensusGateResult` data class and `evaluateHrConsensusGate(...)` with `MAX_HR_CONSENSUS_SPREAD_BPM = 6`.
* Add `hrConsensusGate: HrConsensusGateResult` to `HrvZoneComparisonResult`.

### Component 3: Workout Repository Telemetry Extraction (`WorkoutRepository.kt`)
* Update `getRecentHrvSessionThresholds` to extract `hrvt1Bpm`, `hrvt2Bpm`, and `hrConfidence` from heart rate telemetry.

### Component 4: Summary Card UI & Confirmation Dialog (`HrvThresholdSummaryCard.kt`)
* Add `onUpdateHrZones: ((hrvt1: Int, hrvt2: Int) -> Unit)? = null`.
* Render "Update HR Zones" button (`hrv_btn_update_hr_zones`) and modal confirmation dialog (`hrv_hr_zone_confirm_dialog`).

### Component 5: ViewModel State & DataStore Wiring (`TrackOnMapAftermathViewModel.kt`, `TrackOnMapScreen.kt`)
* Implement `TrackOnMapAftermathViewModel.updateHrZones(newHrvt1: Int, newHrvt2: Int)` mapping sport type to `HR_BIKE` / `HR_RUN`, computing anchored zones, saving to `SettingsDataStore.saveCalculatedHeartRateZones(method = "HRVT_ANCHORS")`, and updating `_uiState.value.hrThresholds`.

### UI Consistency (Rule 23)
* **Reference Component**: Existing `HrvThresholdSummaryCard.kt` and `ZoneCalculatorDialog.kt`.
* **Reused Components**: `AlertDialog`, `Button`, `TextButton`, `Text`, `TTColor`.
* **Theme Tokens**: `MaterialTheme.typography`, `MaterialTheme.colorScheme`, `RoundedCornerShape(16.dp)`.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `ZoneCalculationMath.kt` & Unit Tests (`TST-PER-045.8`)
* Implement `calculateThresholdAnchoredHeartRateZones` in `ZoneCalculationMath.kt`.
* Add unit test `testCalculateThresholdAnchoredHeartRateZones` in `ZoneCalculationMathTest.kt`.

### Step 2: `HrvZoneComparisonMath.kt` & Unit Tests (`TST-PER-045.9`)
* Implement `HrConsensusGateResult`, extend `HrvSessionThreshold`, and implement `evaluateHrConsensusGate`.
* Add unit tests in `HrvZoneComparisonMathTest.kt`.

### Step 3: `WorkoutRepository.kt` HR Threshold Extraction
* Enhance `getRecentHrvSessionThresholds` to evaluate HR threshold points alongside power.
* Verify via `WorkoutRepositoryHrvConsensusTest.kt`.

### Step 4: String Resources in 9 Locales (`TST-PER-045.11`)
* Add new strings for HR zone update button, lock reasons, and confirmation dialog across `res/values*/strings_hrv.xml`.
* Verify via `TranslationParityTest.kt`.

### Step 5: `HrvThresholdSummaryCard.kt` UI & Dialog (`TST-PER-045.10`)
* Add `onUpdateHrZones` callback, button, and confirmation dialog.
* Add contract tests in `HrvThresholdSummaryCardTest.kt`.

### Step 6: ViewModel & Screen Wiring
* Implement `updateHrZones` in `TrackOnMapAftermathViewModel.kt` and wire through `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummariesTabbedScreen.kt`, and `WorkoutSummariesListFragment.kt`.
* Add unit test in `TrackOnMapAftermathViewModelTest.kt`.

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted unit tests (`testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*"`) wrapped with `parallel_sprint_lock.py`, followed by full Clean-Room suite in Stage 5.
* **Rollback**: Branch isolation allows full revert without impacting `develop` or concurrent sprint lanes.
