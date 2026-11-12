# Stage 1 Analysis: ATT-7310 - Enable Anchor-Based Heart Rate Training Zone Updates from HRVT1/HRVT2 Consensus

**Ticket**: [ATT-7310](https://rainerblind.atlassian.net/browse/ATT-7310)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-1-Agent---ATT-7310-self-d5962554` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
In Sprint 2026-46.3 (`ATT-3555`), automated consensus gating and anchor-based training zone updates were introduced exclusively for cycling power (`FTP`). Heart rate comparisons against configured boundaries were restricted to informational deltas under a strict non-overwrite invariant (`REQ-PER-037.2`).

During the Sprint 2026-46.3 Joint Review, the Product Owner requested extending this anchor-based update mechanism to Heart Rate training zones:
1. *Physiological Anchoring Gap*: Athletes currently have to manually recalculate or type in their heart rate training zones even when verified physiological anchors ($HRVT_1$ aerobic threshold and $HRVT_2$ anaerobic threshold / LTHR) are detected with high confidence or multi-session consensus.
2. *Safeguards Against Single-Session HR Spikes*: Heart rate is subject to acute diurnal fluctuations, hydration status, cardiac drift, and sensor artifacts. Anchor-based updates must strictly reject single uncalibrated sessions and enforce multi-session consensus ($\ge 3$ sessions agreeing within $\le 6\text{ bpm}$) or a high-confidence guided step/ramp test protocol.
3. *Preservation of User Authority & Manual Overrides*: Updates must never occur silently. A dedicated modal confirmation dialog must show old vs. proposed boundaries ($Z_2$ anchored at $HRVT_1$, $Z_4$ anchored at $HRVT_2$) with clear physiological notes, while preserving the ability for athletes to manually adjust zones in Zone Settings.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Underlying Architecture**:
  * `ZoneCalculationMath.kt` currently only supports `% HRmax` (`calculateHeartRateZonesMax`) and Karvonen HRR (`calculateKarvonenHeartRateZones`). It lacks a dedicated threshold-anchored calculation deriving 5 standard zones from $HRVT_1$ and $HRVT_2$.
  * `HrvZoneComparisonMath.kt` computes informational HR deltas (`compareHeartRateZones`), but `evaluateConsensusGate` only evaluates power (`hrvt2Watts`). `HrvSessionThreshold` does not persist or evaluate $HRVT_1$ / $HRVT_2$ heart rate consensus.
  * `WorkoutRepository.getRecentHrvSessionThresholds` only extracts power metrics, discarding heart rate threshold estimations.
  * `HrvThresholdSummaryCard.kt` only exposes `onUpdateFtp` and `hrv_btn_update_power_zones`.
  * `TrackOnMapAftermathViewModel.kt` only exposes `updateFtp(newFtp: Int)` without a companion `updateHrZones(hrvt1: Int, hrvt2: Int)`.
* **Independent Peer Review & Verification**:
  * *Threshold Anchoring Mapping*:
    * Zone 2 upper bound ($Z_2$) anchors strictly at $HRVT_1$ (Aerobic Threshold / VT1).
    * Zone 4 upper bound ($Z_4$) anchors strictly at $HRVT_2$ (Anaerobic Threshold / VT2 / LTHR).
    * Zone 3 upper bound ($Z_3$) is calculated as the physiological midpoint: $\text{round}((HRVT_1 + HRVT_2) / 2.0)$.
    * Zone 1 upper bound ($Z_1$) is calculated as $\text{round}(HR_{\text{rest}} + 0.70 \times (HRVT_1 - HR_{\text{rest}}))$ if $HR_{\text{rest}}$ is valid and $< HRVT_1$, else $\text{round}(0.85 \times HRVT_1)$.
    * Monotonic rationalization ensures $Z_1 < Z_2 < Z_3 < Z_4 < HR_{\max}$.
  * *Heart Rate Consensus Rule*:
    * Condition A: Guided Ramp/Step Test (`HrvProtocolType.GUIDED_STEP` or `GUIDED_RAMP`) with `ThresholdConfidence.HIGH` and valid $(HRVT_1, HRVT_2)$ where $HRVT_2 \ge HRVT_1 + 5\text{ bpm}$.
    * Condition B: Multi-Session Consensus across $M \ge 3$ sessions within 30 days (`HIGH` or `MEDIUM` confidence) where $\max(HRVT_1) - \min(HRVT_1) \le 6\text{ bpm}$ and $\max(HRVT_2) - \min(HRVT_2) \le 6\text{ bpm}$. Suggested anchors are the statistical medians of each threshold series.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones(hrvt1, hrvt2, hrMax, hrRest)` with strict monotonic guarantees.
  2. Extend `HrvSessionThreshold`, `evaluateHrConsensusGate`, and `WorkoutRepository.getRecentHrvSessionThresholds` to track and evaluate heart rate consensus.
  3. Add "Update HR Zones" action button and modal confirmation dialog in `HrvThresholdSummaryCard.kt`.
  4. Implement `updateHrZones(hrvt1, hrvt2)` in `TrackOnMapAftermathViewModel` and wire through `TrackOnMapScreen`, `WorkoutDetailTabbedContent`, and workout list screens.
  5. Save calculated zones to `SettingsDataStore.saveCalculatedHeartRateZones` with method `HRVT_ANCHORS`.
  6. Maintain 100% localization parity across all 9 languages in `strings_hrv.xml`.
* **Out-of-Scope Non-Goals**:
  * Modifying existing `% HRmax` or Karvonen formulas in `ZoneCalculationMath.kt`.
  * Automatic silent background updating of HR zones without athlete confirmation.
  * Altering live tracking cockpit heart rate gauge rendering.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refinement of `REQ-PER-037.2` and net-new addition `REQ-PER-037.7` (*Anchor-Based Heart Rate Training Zone Updates from HRVT1/HRVT2 Consensus*) in `docs/requirements/req_aftermath_periods.md`.
* **Historical Origin & Commit Trace**: `REQ-PER-037.2` introduced in `ATT-3555` (commit `a216f4a3`).
* **Root Reason for Existing Formulation**: `REQ-PER-037.2` forbade automatic silent overwriting of HR zones to protect athletes against single-session HR spikes and uncalibrated submaximal errors.
* **Preservation of Core Invariants**:
  * Silent automatic overwriting remains strictly prohibited.
  * Updates are gated behind high-confidence guided tests or multi-session consensus ($M \ge 3$, spread $\le 6\text{ bpm}$).
  * Explicit modal confirmation dialog is mandatory prior to committing changes.
  * Athletes retain full ability to manually edit zone boundaries in Zone Settings (`isCustomized` semantics).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-PER-037.2` (clarified), `REQ-PER-037.7` (net-new).
* **Affected Architecture Elements**:
  * `ZoneCalculationMath.kt`
  * `HrvZoneComparisonMath.kt`
  * `WorkoutRepository.kt`
  * `HrvThresholdSummaryCard.kt`
  * `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`
  * `TrackOnMapAftermathViewModel.kt`
  * `res/values*/strings_hrv.xml` (all 9 supported languages)
* **Verification Impact & Test Surface**:
  * `TST-PER-045.8` (Anchor-based HR zone calculation in `ZoneCalculationMathTest`)
  * `TST-PER-045.9` (HR consensus gating and median aggregation in `HrvZoneComparisonMathTest`)
  * `TST-PER-045.10` (Contract and dialog UI verification in `HrvThresholdSummaryCardTest`)
  * `WorkoutRepositoryHrvConsensusTest` & `TrackOnMapAftermathViewModelTest`
  * `TranslationParityTest` for all 9 locales
* **Risk Assessment & Invariants**:
  * Monotonic ascending zone order ($Z_1 < Z_2 < Z_3 < Z_4$) guaranteed.
  * No regression on existing power zone comparison or FTP update flows.

---

## 6. Architectural Strategy & High-Level Solution
1. **Math Layer**:
   * Add `calculateThresholdAnchoredHeartRateZones(hrvt1: Int, hrvt2: Int, hrMax: Int? = null, hrRest: Int? = null): HeartRateZoneThresholds` to `ZoneCalculationMath.kt`.
   * Add `HrConsensusGateResult` and `evaluateHrConsensusGate(...)` to `HrvZoneComparisonMath.kt`.
2. **Repository Layer**:
   * Enhance `WorkoutRepository.getRecentHrvSessionThresholds` to evaluate heart rate telemetry in addition to power.
3. **ViewModel & State Layer**:
   * Add `updateHrZones(hrvt1: Int, hrvt2: Int)` to `TrackOnMapAftermathViewModel`, resolving sport type (`HR_BIKE` vs `HR_RUN`), computing threshold-anchored zones, and saving via `SettingsDataStore.saveCalculatedHeartRateZones`.
4. **UI Layer**:
   * Add "Update HR Zones" action button and confirmation dialog in `HrvThresholdSummaryCard.kt`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. No automatic/unprompted overwriting of heart rate zones without explicit user confirmation.
  2. Single noisy workouts without consensus remain locked.
  3. Full 9-language translation parity maintained.
  4. Parent ticket Human Decision Gate preserved.
* **Risk Rating**: **LOW**. Follows the exact established architecture and safety patterns of `ATT-3555`.
