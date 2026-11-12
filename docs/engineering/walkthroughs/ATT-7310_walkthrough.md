# Walkthrough: Enable Anchor-Based Heart Rate Training Zone Updates from HRVT1/HRVT2 Consensus (ATT-7310)

* **Parent Ticket**: [ATT-7310](https://rainerblind.atlassian.net/browse/ATT-7310) (*[Feature] Enable anchor-based Heart Rate training zone updates from HRVT1/HRVT2 consensus*)
* **Sub-Tasks**:
  * [ATT-7373](https://rainerblind.atlassian.net/browse/ATT-7373) (*[Analysis] Enable anchor-based Heart Rate training zone updates from HRVT1/HRVT2 consensus*) - `Erledigt`
  * [ATT-7376](https://rainerblind.atlassian.net/browse/ATT-7376) (*[Req & Test Spec] Enable anchor-based Heart Rate training zone updates from HRVT1/HRVT2 consensus*) - `Erledigt`
  * [ATT-7377](https://rainerblind.atlassian.net/browse/ATT-7377) (*[Impl-Plan] Enable anchor-based Heart Rate training zone updates from HRVT1/HRVT2 consensus*) - `Erledigt`
  * [ATT-7385](https://rainerblind.atlassian.net/browse/ATT-7385) (*[SWE.3] Software Construction & Implementation*) - `Erledigt`
* **Target Version**: `V5.0.0`
* **Requirements**:
  * [`REQ-PER-037.7`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements/req_aftermath_periods.md#L457) (*Anchor-Based Heart Rate Training Zone Updates & Multi-Session Consensus Gating*) - `[Verified]`
* **Test Specifications**:
  * [`TST-PER-045.8`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_aftermath_periods.md#L476) (*HR Consensus Gating Evaluation*) - `[Verified]`
  * [`TST-PER-045.9`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_aftermath_periods.md#L487) (*Threshold-Anchored Heart Rate Zone Boundary Calculation*) - `[Verified]`
  * [`TST-PER-045.10`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_aftermath_periods.md#L498) (*Heart Rate Zone Update ViewModel & DataStore Persistence*) - `[Verified]`
  * [`TST-PER-045.11`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_aftermath_periods.md#L509) (*HrvThresholdSummaryCard HR Confirmation Flow & Localization Parity*) - `[Verified]`
* **Sprint**: `sprint/2026-46.4`
* **Branch**: `subagent-Lane-1-Agent---ATT-7310-self-d5962554`

---

## 1. Problem Domain & Root Cause Analysis

Prior to ATT-7310, `ATT-3555` introduced multi-session consensus gating and a one-tap update flow exclusively for Power zones (FTP). In contrast, Heart Rate thresholds derived from HRV DFA $\alpha_1$ analysis ($HRVT_1$ for aerobic threshold, $HRVT_2$ for anaerobic / LTHR threshold) were presented purely as informational comparisons in `HrvThresholdSummaryCard.kt`:
1. **Asymmetric Zone Calibration**: Athletes completing guided tests or consistent endurance sessions could update their cycling Power zones directly, but had to manually transcribe Heart Rate values into `ZoneSettingsScreen`.
2. **Missing Physiological Anchoring**: Standard Heart Rate zone models (e.g. `% HRmax` or Karvonen HRR) rely on generic statistical population percentages that frequently misclassify individual aerobic base ceiling ($HRVT_1$) and lactate threshold ceiling ($HRVT_2$).
3. **Safeguard Requirements**: Heart rate is influenced by transient daily autonomic stressors (sleep, caffeine, cardiac drift, temperature). A single-session HR estimate must not silently overwrite athlete zones; updates require multi-session consensus or a validated high-confidence guided test protocol.

---

## 2. Implemented Architecture & Logic

### 2.1 Pure Mathematical Engine (`ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones`)
* Anchors Zone 2 ceiling ($Z_{2,\max}$) strictly at $HRVT_1$ (aerobic base).
* Anchors Zone 4 ceiling ($Z_{4,\max}$) strictly at $HRVT_2$ (anaerobic threshold / LTHR).
* Computes Zone 3 ceiling ($Z_{3,\max}$) as deterministic midpoint: $\text{round}((HRVT_1 + HRVT_2) / 2.0)$.
* Calculates Zone 1 ceiling ($Z_{1,\max}$) utilizing resting heart rate if available ($HR_{\text{rest}} + 0.70 \times (HRVT_1 - HR_{\text{rest}})$) or falling back to $0.85 \times HRVT_1$.
* Enforces strict monotonic progression $Z_1 < Z_2 < Z_3 < Z_4 < HR_{\max}$ and input boundary checks ($HRVT_1, HRVT_2, HR_{\max} \in [80, 240]$, $HRVT_2 > HRVT_1$).

### 2.2 Heart Rate Multi-Session Consensus Gating (`HrvZoneComparisonMath.evaluateHrConsensusGate`)
Evaluates consensus gating for heart rate threshold application:
* **Unlocked (Guided Protocol)**: `currentProtocol in (STEP_TEST, RAMP_TEST)` AND `currentConfidence == HIGH` AND $HRVT_2 \ge HRVT_1 + 5\text{ bpm}$.
* **Unlocked (Multi-Session Consensus)**: $\ge 3$ eligible sessions within 30 days (each with $\text{confidence} \ne \text{LOW}$) where both $HRVT_1$ and $HRVT_2$ have $\text{spread} = (\max - \min) \le 6\text{ bpm}$. Suggested thresholds are calculated using the statistical median.
* **Locked**: In all other cases (insufficient sessions, excessive spread $> 6\text{ bpm}$, or single free workout), with clear localized explanatory reason strings.

### 2.3 Telemetry & Repository Extension (`WorkoutRepository.kt`)
* Extended `HrvSessionThreshold` to capture `hrvt1Bpm`, `hrvt2Bpm`, and `hrConfidence`.
* Updated `WorkoutRepository.getRecentHrvSessionThresholds` to inspect both Power and Heart Rate intensity modes, extracting valid $HRVT_1$ and $HRVT_2$ estimates across recent workouts.

### 2.4 User Experience & Confirmation Flow (`HrvThresholdSummaryCard.kt`)
* Added Heart Rate consensus status indicator, badge (`hrv_hr_consensus_badge`), and "Update HR Zones" button (`hrv_btn_update_hr_zones`).
* Implemented modal confirmation dialog (`hrv_hr_zone_confirm_dialog`) displaying current vs. new proposed zone boundaries, requiring explicit athlete confirmation before mutating preferences.
* Full accessibility semantics and test tags for Compose testing.

### 2.5 ViewModel & Persistence (`TrackOnMapAftermathViewModel.kt`)
* Added `updateHrZones(newHrvt1: Int, newHrvt2: Int)` mapping sport types (cycling -> `BSportType.BIKE`, running -> `BSportType.RUN`).
* Persists computed boundaries to `SettingsDataStore.saveCalculatedHeartRateZones(method = "HRVT_ANCHORS")`.
* Updates `uiState.heartRateThresholds` synchronously for instant feedback in the Aftermath screen.
* Safely clamps $HR_{\max} = \max(HR_{\max}, newHrvt2 + 10)$ to maintain the invariant $Z_4 < HR_{\max}$.

### 2.6 Localization Parity (100% across 9 locales)
Added 9 localized string keys to `strings_hrv.xml`:
* `hrv_hr_consensus_badge_unlocked`
* `hrv_hr_consensus_badge_locked`
* `hrv_hr_consensus_reason_guided_high`
* `hrv_hr_consensus_reason_multi_session`
* `hrv_hr_consensus_reason_insufficient_sessions`
* `hrv_hr_consensus_reason_spread_too_high`
* `hrv_zone_comparison_btn_update_hr`
* `hrv_zone_comparison_dialog_title_hr`
* `hrv_zone_comparison_dialog_body_hr`
Translated across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Test Evidence & Verification

### 3.1 Unit Test Coverage
* `ZoneCalculationMathTest`:
  * `calculateThresholdAnchoredHeartRateZones_withRestingHr_calculatesStrictAscendingBoundaries`: Verifies deterministic boundaries with resting HR.
  * `calculateThresholdAnchoredHeartRateZones_withoutRestingHr_usesPercentageFallback`: Verifies percentage fallback.
  * `calculateThresholdAnchoredHeartRateZones_invalidInputs_throws`: Verifies safety exceptions for $HRVT_2 \le HRVT_1$ and $HR_{\max} \le HRVT_2$.
* `HrvZoneComparisonMathTest`:
  * `testEvaluateHrConsensusGate_guidedHighConfidence_unlocks`: Guided test unlocks.
  * `testEvaluateHrConsensusGate_guidedHighSpreadTooNarrow_remainsLocked`: Narrow spread locks.
  * `testEvaluateHrConsensusGate_singleFreeRide_remainsLocked`: Single session locks.
  * `testEvaluateHrConsensusGate_threeSessionsWithin6Bpm_unlocksWithMedian`: 3 sessions $\le 6\text{ bpm}$ unlocks with median.
  * `testEvaluateHrConsensusGate_threeSessionsSpreadExceeds6Bpm_remainsLocked`: Spread $> 6\text{ bpm}$ locks.
* `TrackOnMapAftermathViewModelTest`:
  * `updateHrZones_persistsHeartRateZonesAndUpdatesUiState`: Verifies DataStore persistence and UI state update.
* `HrvThresholdSummaryCardTest`:
  * `all zone comparison and consensus string keys exist across canonical resources (TST-PER-045_7)`: Verifies presence and formatting of all keys.
* `TranslationParityTest`:
  * Confirms 100% translation parity across all 9 locales.

### 3.2 Targeted Test Execution & Clean-Room Pass
```bash
python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.zones.ZoneCalculationMathTest" \
    --tests "com.atrainingtracker.trainingtracker.zones.HrvZoneComparisonMathTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvThresholdSummaryCardTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModelTest" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
```
**Result**: `BUILD SUCCESSFUL in 23s`, all 56 tests passing with 100% clean-room success rate.

