# Stage 3: Implementation Plan - ATT-4970: Model DFA a1 Autonomic Durability and Drift Across Multi-Hour Workouts

**Ticket**: [ATT-4970](https://rainerblind.atlassian.net/browse/ATT-4970)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-038` (*Model DFA a1 Autonomic Durability, Drift & Iso-Intensity Bins Across Multi-Hour Workouts*)  
**Test Mapping**: `TST-PER-046` (*DFA a1 Autonomic Durability, Drift & Iso-Intensity Bins Verification Suite*)  
**Branch**: `feature/ATT-4970` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
During long endurance workouts ($\ge 90\text{ min}$), `DFA a1` drops at matched workload due to autonomic fatigue and vagal withdrawal. While `HrvThresholdEstimator` ignores data $> 60\text{ min}$ to protect threshold regression accuracy, `ATT-4970` models this late decline as an Autonomic Durability metric ($\Delta\alpha_{1,\%}$) paired with stroke-volume Heart Efficiency drift ($\Delta C_{\text{O}_2}\%$) in `ATT-4842`. Steady Zone 2 workouts collapse naive linear regression due to narrow intensity spreads, requiring median aerobic reference intensity ($P_{\text{ref}}$ or $v_{\text{ref}}$) over the fresh baseline (`15–45 min`) and iso-intensity binning ($\pm 10\%$) with a 10-minute minimum data guard.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-038` (`REQ-PER-038.1` to `REQ-PER-038.6`)
* **Test Mapping**: `TST-PER-046` (`TST-PER-046.1` to `TST-PER-046.7`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Database Schema Migrations**: Durability summary metrics persist to `WorkoutSummaries.TABLE_EXTREMA_VALUES` via new `ExtremaType` enum keys without schema version bumps.
2. **Strict 10-Minute Matched Data Guard**: Returns `INSUFFICIENT_MATCHED_DATA` if either fresh baseline or late segment has $< 600\text{ s}$ (120 5s-samples) in the $\pm 10\%$ band.
3. **Threshold Estimator Isolation**: Existing `HrvThresholdEstimator` constants, bounds, and tests remain untouched.
4. **100% Localization Parity**: All 11 new strings in `strings_hrv.xml` maintained across all 9 languages.
5. **Human Decision Gate**: Parent ticket ATT-4970 advances to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Durability Calculator (`ui/aftermath/workoutdetail/HrvDurabilityCalculator.kt`)
* Enums & Data Classes: `HrvDurabilityStatus`, `HrvDurabilitySlice`, `HrvDurabilityResult`.
* Logic: Fresh baseline $[900, 2700]\text{ s}$, late segment $> 3600\text{ s}$, artifact filtering $\le 5.0\%$, median $I_{\text{ref}}$, $\pm 10\%$ iso-intensity binning, autonomic efficiency normalization $\alpha_{1,\text{norm}} = \alpha_1 / (I / I_{\text{ref}})$, drift percentage $\Delta\alpha_{1,\%}$, and 15-minute slice trajectory aggregation.

### Component 2: Extrema Persistence & Mapping (`ExtremaType.java`, `HrvPostWorkoutRecomputer.kt`, `WorkoutDataMapper.kt`)
* Add `DFA_A1_FRESH_BASELINE`, `DFA_A1_LATE_MEAN`, `DFA_A1_DRIFT_PCT` to `ExtremaType.java`.
* In `HrvPostWorkoutRecomputer.kt`: Trigger durability calculation and persist extrema via `updateExtremaValue`.
* In `WorkoutData.kt` & `WorkoutDataMapper.kt`: Add and hydrate fields `dfaA1FreshBaseline`, `dfaA1LateMean`, `dfaA1DurabilityDriftPct`.

### Component 3: Presentation & Live Host Mounting (`HrvDurabilityCard.kt`, Rule 81)
* Composable `HrvDurabilityCard` rendering fresh baseline, late mean, drift % chip, and slice trajectory chips.
* Mounted in `WorkoutDetailTabbedContent.kt` under `WorkoutDetailSectionType.ZONES` when `TelemetryMetricUtils.hasHrvData(activeScrubPath)`.
* **UI Consistency**: Reuses `MaterialTheme.colorScheme`, `RoundedCornerShape(12.dp)`, and `HrvThresholdSummaryCard` styling.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add `ExtremaType` Enums & String Resources
* Files: `ExtremaType.java`, `res/values/strings_hrv.xml` (all 9 locales).

### Step 2: Implement `HrvDurabilityCalculator.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvDurabilityCalculator.kt`.

### Step 3: Implement Unit Tests in `HrvDurabilityCalculatorTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvDurabilityCalculatorTest.kt`.

### Step 4: Wire Persistence in `HrvPostWorkoutRecomputer.kt` & `WorkoutDataMapper.kt`
* Files: `HrvPostWorkoutRecomputer.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`.

### Step 5: Implement UI Card `HrvDurabilityCard.kt` & Mount in `WorkoutDetailTabbedContent.kt` (Rule 81)
* Files: `HrvDurabilityCard.kt`, `WorkoutDetailTabbedContent.kt`.

### Step 6: Implement Compose & Mapping Contract Tests
* Files: `HrvDurabilityCardTest.kt`, `WorkoutDataMapperDurabilityTest.kt`.

### Step 7: Clean-Room Verification & Localization Parity
* Run targeted tests, `TranslationParityTest`, and `verify_requirement_governance.py`.
