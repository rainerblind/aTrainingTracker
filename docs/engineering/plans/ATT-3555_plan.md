# Stage 3: Implementation Plan - ATT-3555: Compare Estimated HRVT1/HRVT2 Against Configured Training Zones with Multi-Session Consensus and Anchor-Based Updates (ZoneCalculationMath)

**Ticket**: [ATT-3555](https://rainerblind.atlassian.net/browse/ATT-3555)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-037` (*HRV Threshold Training Zone Comparison, Multi-Session Consensus Gating & Anchor-Based Power Zone Updates*)  
**Test Mapping**: `TST-PER-045` (*HRV Threshold Training Zone Comparison, Consensus Gating & Power Zone Update Suite*)  
**Branch**: `feature/ATT-3555` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Athletes can estimate physiological $HRVT_1$ and $HRVT_2$ thresholds (`ATT-3554`, `ATT-4969`), but Workout Details lacks contextual comparison against configured training zones (`ZoneCalculationMath.kt`). Blindly overwriting training zones from single uncalibrated rides risks training pollution. Furthermore, modifying HR zones from submaximal thresholds is physiologically invalid because $HR_{\max}$ cannot be observed. ATT-3555 delivers `HrvZoneComparisonMath.kt`, consensus gating, power zone (`FTP`) anchor update with confirmation dialog, informational Heart Rate delta displays, and Mader-Heck forwarding.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-037` (*HRV Threshold Training Zone Comparison, Multi-Session Consensus Gating & Anchor-Based Power Zone Updates*, `REQ-PER-037.1` to `REQ-PER-037.6`)
* **Test Mapping**: `TST-PER-045` (*HRV Threshold Training Zone Comparison, Consensus Gating & Power Zone Update Suite*, `TST-PER-045.1` to `TST-PER-045.7`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: Existing `ZoneCalculationMathTest`, `HrvThresholdEstimatorTest`, and `HrvThresholdSummaryCardTest` continue to pass 100%.
2. **Informational Heart Rate Invariant**: Heart Rate zones remain strictly anchored to $HR_{\max}$; no automatic HR zone overwriting occurs.
3. **Consensus Gating Lock**: Updating FTP requires EITHER a `Confidence.HIGH` Guided Step/Ramp Test (`ATT-4969`), OR $M \ge 3$ recent consistent sessions ($\pm 10\text{ W}$) within 30 days. Single unverified free rides are locked.
4. **User Confirmation Invariant**: FTP is updated only after explicit user confirmation in a dedicated dialog displaying protocol provenance, slope/MRT adjustment, and MLSS caveat.
5. **Human Decision Gate**: Parent ticket ATT-3555 advances to `Final Review (Human)` and is never closed by agents.

---

## 4. Proposed Architectural Changes

### Component 1: Domain Math & Consensus Engine (`HrvZoneComparisonMath.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.zones`
* **Models**:
  * `enum class ConsensusGateStatus { LOCKED, UNLOCKED }`
  * `data class PowerZoneDelta(val targetWatts: Int, val deltaWatts: Int, val deltaPercent: Double)`
  * `data class HeartRateZoneDelta(val targetBpm: Int, val deltaBpm: Int, val percentHrMax: Double)`
  * `data class HrvZoneComparisonResult(...)`
* **Calculations**:
  * Power comparison: $HRVT_1$ vs `z2Max = round(0.75 * ftp)` ($\Delta P_{\text{T1}}$); $HRVT_2$ vs `FTP` ($\Delta P_{\text{T2}}$).
  * Heart Rate comparison: $HRVT_1$ vs `z2Max` ($\Delta \text{HR}_{\text{T1}}$, $\% HR_{\max}$); $HRVT_2$ vs `z4Max` ($\Delta \text{HR}_{\text{T2}}$, $\% HR_{\max}$).
  * Consensus evaluator: Unlocks on Guided Test HIGH or $M \ge 3$ consistent sessions ($\le 10\text{ W}$ spread) using median FTP.

### Component 2: Presentation Layer Enhancement (`HrvThresholdSummaryCard.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail`
* **UI Additions**:
  * Zone comparison section with power and HR delta chips.
  * Informational footnote explaining $HRVT_1$ vs $HR_{\max}$ relationship in trained athletes.
  * Consensus status indicator and "Update Power Zones (FTP)" button.
  * Confirmation Dialog displaying target FTP, protocol, slope, MRT adjustment, and MLSS caution.
  * Action callback for Mader-Heck calibration forwarding (`ATT-4799`).

### Component 3: Wiring in `WorkoutDetailTabbedContent.kt`
* Pass configured FTP, $HR_{\max}$, and `onUpdateFtp` into `HrvThresholdSummaryCard`.

### UI Consistency (Rule 23)
* **Reference Component**: Existing `HrvThresholdSummaryCard.kt` and `ZoneSettingsScreen`.
* **Reused Components**: `MetricItem`, `HorizontalDivider`, `ElevatedCard`, `AlertDialog`, `TTColor`.
* **Theme Tokens**: `RoundedCornerShape(12.dp)`, `MaterialTheme.colorScheme`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `HrvZoneComparisonMath.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/zones/HrvZoneComparisonMath.kt`
* Details: Implement power delta math, HR informational delta math, consensus gating logic, and median FTP calculation.

### Step 2: Implement Unit Tests in `HrvZoneComparisonMathTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvZoneComparisonMathTest.kt`
* Verification: Test power deltas, HR informational comparison, consensus gating conditions (Guided HIGH vs $M \ge 3$ sessions vs single free ride), and median FTP aggregation.

### Step 3: Enhance `HrvThresholdSummaryCard.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvThresholdSummaryCard.kt`
* Details: Render zone comparison section, delta badges, consensus lock/unlock states, confirmation dialog, and Mader-Heck forwarding.

### Step 4: Wire in `WorkoutDetailTabbedContent.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`
* Details: Supply configured FTP and $HR_{\max}$ parameters to `HrvThresholdSummaryCard`.

### Step 5: Implement Compose & Contract Tests in `HrvThresholdSummaryCardTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvThresholdSummaryCardTest.kt`
* Verification: Test delta rendering, consensus lock states, update button behavior, and confirmation dialog flow.

### Step 6: Target Test Execution & Localization Parity
* Execute:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and Compose tests in Step 6, followed by clean-room Tier 1 suite in Stage 5.
* **Rollback Plan**: All changes isolated on `feature/ATT-3555`. Clean branch checkout reverts cleanly.
