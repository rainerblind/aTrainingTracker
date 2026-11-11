# Stage 1 Analysis: ATT-3555 - Compare Estimated HRVT1/HRVT2 Against Configured Training Zones with Multi-Session Consensus and Anchor-Based Updates (ZoneCalculationMath)

**Ticket**: [ATT-3555](https://rainerblind.atlassian.net/browse/ATT-3555)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-3555` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Following the introduction of lag-aligned threshold estimation (`ATT-3554`) and guided step/ramp tests (`ATT-4969`), athletes can inspect their physiological $HRVT_1$ and $HRVT_2$ thresholds. However, Workout Details currently lacks integration with configured training zones (`ZoneCalculationMath.kt`):
1. *Missing Zone Comparison & Delta Context*: Athletes cannot see how estimated $HRVT_1$ compares to their Coggan Zone 2 aerobic ceiling (`z2Max = 0.75 * FTP`) or how $HRVT_2$ compares to their Functional Threshold Power (`FTP = 100%`).
2. *Risk of Unverified Baseline Overwriting*: Overwriting an athlete's FTP from a single uncalibrated free ride risks polluting training prescriptions. Zone updates must enforce multi-session consensus or high-confidence guided test gating.
3. *Physiological Asymmetry (Power vs. Heart Rate)*: In `ZoneCalculationMath.kt`, HR zones are anchored to $HR_{\max}$ (where legacy $Z_2 \le 69\% HR_{\max}$), whereas in endurance athletes physiological $HRVT_1$ occurs at $75\text{--}82\% HR_{\max}$. Overwriting $HR_{\max}$ from submaximal $HRVT_1$ is scientifically invalid.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Underlying Architecture**:
  * `ZoneCalculationMath.calculateCogganPowerZones(ftp)` calculates Coggan 7-zone power boundaries with `z2Max = round(0.75 * ftp)` and `z4Max = round(1.05 * ftp)`.
  * `HrvThresholdSummaryCard.kt` displays threshold values but does not query user-configured FTP or HR zones.
* **Independent Peer Review & Verification**:
  * *Power Mapping*: $HRVT_1$ power ($P_{\text{HRVT1,ss}}$) aligns with `z2Max`, while steady-state $HRVT_2$ power ($P_{\text{HRVT2,ss}}$) aligns with `100% FTP`.
  * *Gating Invariant*: Updating FTP must require EITHER a `Confidence.HIGH` Guided Step/Ramp Test (`ATT-4969`), OR $M \ge 3$ recent `HIGH`/`MEDIUM` workouts (within 30 days) agreeing within $\pm 10\text{ W}$ (using median).
  * *Heart Rate Informational Isolation*: HR zones are strictly informational comparisons (displaying bpm deltas and $\% HR_{\max}$) with explanatory copy, avoiding false $HR_{\max}$ overwrites.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Implement `HrvZoneComparisonMath.kt` computing deltas between $(P_{\text{HRVT1}}, P_{\text{HRVT2}})$ and configured power zones (`z2Max`, `FTP`), and between $(HR_{\text{HRVT1}}, HR_{\text{HRVT2}})$ and HR zones (`z2Max`, `z4Max`, $\% HR_{\max}$).
  * Implement multi-session consensus evaluator: checks if estimate is `Confidence.HIGH` Guided Test OR $M \ge 3$ consistent sessions ($\pm 10\text{ W}$) within 30 days.
  * Enhance `HrvThresholdSummaryCard.kt` with Zone Comparison section: delta chips, consensus lock indicator, and "Update Power Zones" action button.
  * Confirmation dialog showing consensus median FTP, protocol provenance, ramp slope & MRT adjustment, with MLSS cautionary note, updating `SettingsDataStore.setFtp`.
  * Maintain 100% 9-language localization parity in `strings_hrv.xml`.
  * Unit tests in `HrvZoneComparisonMathTest.kt` and Compose tests in `HrvThresholdSummaryCardTest.kt`.
* **Out-of-Scope Non-Goals**:
  * Modifying `ZoneCalculationMath.kt` Coggan percentage formulas or 2500 W ceiling.
  * Overwriting Heart Rate zones or $HR_{\max}$.
  * Mader-Heck metabolic simulation engine implementation (`ATT-4799`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PER-037` (*HRV Threshold Training Zone Comparison, Consensus Gating & Anchor-Based Power Zone Updates*) in `docs/requirements/req_aftermath_periods.md`, extending `REQ-PER-036` and integrating with `REQ-UI-373` (`ZoneCalculationMath.kt`).
* **Historical Origin & Commit Trace**: Conceived in Epic `ATT-3539`; companion to `ATT-3554` (`5deec7cd`).
* **Root Reason for Existing Formulation**: `ZoneCalculationMath` originally supported only manual calculator entry without automated physiological anchor feedback.
* **Preservation of Core Invariants**:
  * Integer monotonicity ($Z_n \ge Z_{n-1} + 1$) and 2500 W ceiling in `ZoneCalculationMath` strictly preserved.
  * Heart Rate zones remain anchored strictly to $HR_{\max}$.

---

## 5. Architectural Strategy & High-Level Solution
* **Math & Consensus Layer (`HrvZoneComparisonMath.kt`)**:
  * Input: `HrvThresholdEstimateResult`, configured FTP, configured $HR_{\max}$ / HR zones, and recent session history.
  * Output: `HrvZoneComparisonResult` containing power deltas, HR deltas, consensus gate status (`LOCKED` vs `UNLOCKED`), and suggested FTP.
* **UI Integration (`HrvThresholdSummaryCard.kt`)**:
  * Section rendering power deltas ($\Delta \text{W}$ vs `z2Max` and `FTP`), HR deltas ($\Delta \text{bpm}$ and $\% HR_{\max}$).
  * Action button to update FTP when consensus gate is unlocked; modal confirmation dialog before saving to `SettingsDataStore`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. No automatic overwriting of training zones without explicit user confirmation.
  2. HR zones remain strictly informational.
  3. Single unverified free workouts cannot unlock FTP updates.
  4. 100% localization parity across all 9 languages.
  5. Parent ticket Human Decision Gate strictly preserved.
* **Risk Rating**: **LOW**. Pure mathematical comparison and non-destructive UI action with confirmation dialog.
