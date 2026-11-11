# Stage 2: Requirement & Test Specification - ATT-4970: Model DFA a1 Autonomic Durability and Drift Across Multi-Hour Workouts (Paired with ATT-4842 Heart Efficiency)

**Ticket**: [ATT-4970](https://rainerblind.atlassian.net/browse/ATT-4970)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-038` (*Model DFA a1 Autonomic Durability, Drift & Iso-Intensity Bins Across Multi-Hour Workouts*)  
**Test Spec ID**: `TST-PER-046` (*DFA a1 Autonomic Durability, Drift & Iso-Intensity Bins Verification Suite*)  
**Branch**: `feature/ATT-4970` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PER-038)

### 1.1 Problem Statement & Rationale
During multi-hour endurance workouts ($\ge 90\text{ min}$), `DFA a1` progressively declines at a given external workload due to autonomic fatigue, vagal withdrawal, and cardiovascular drift (*Rogers et al. 2021; Gronwald et al. 2021*). While `REQ-PER-036` truncates threshold estimation at 60 minutes to prevent fatigue distortion, quantifying the rate of `DFA a1` decline at matched intensity provides an actionable Autonomic Durability metric ($\Delta\alpha_{1,\%}$) that complements cardiovascular stroke-volume Heart Efficiency drift ($\Delta C_{\text{O}_2}\%$) in `ATT-4842`.

### 1.2 Functional & Architectural Requirements
1. **Fresh Baseline & Late Segment Partitioning (`REQ-PER-038.1`)**: Fresh baseline window spans $t \in [900, 2700]\text{ s}$ (`15–45 min`). Late fatigue segment evaluates all valid elapsed workout time $t > 3600\text{ s}$ (`> 60 min`). Only valid 5s-stepped centered-window trackpoints with artifact rate $\le 5.0\%$ are evaluated.
2. **Iso-Intensity Matched-Power & Matched-Pace Binning (`REQ-PER-038.2`)**: Establish median aerobic reference intensity $I_{\text{ref}}$ ($P_{\text{ref}}$ for power, $v_{\text{ref}}$ for speed) over the fresh baseline. Filter samples to the iso-intensity band $[0.90 \cdot I_{\text{ref}}, 1.10 \cdot I_{\text{ref}}]$. Normalize minor residual intensity variations via autonomic efficiency ratio $\alpha_{1,\text{norm}} = \alpha_1 / (I / I_{\text{ref}})$.
3. **Autonomic Durability Drift & Minimum Data Guard (`REQ-PER-038.3`)**: Calculate durability drift as $\Delta\alpha_{1,\%} = \frac{\overline{\alpha}_{1,\text{late,norm}} - \overline{\alpha}_{1,\text{fresh,norm}}}{\overline{\alpha}_{1,\text{fresh,norm}}} \times 100\%$. Require $\ge 10\text{ min}$ (600 s / 120 points) of valid matched-intensity data in both fresh baseline and late segment; return `null` (`INSUFFICIENT_MATCHED_DATA`) if unmet.
4. **15-Minute Slice Trajectory Analysis (`REQ-PER-038.4`)**: Aggregate mean normalized $\alpha_1$ across 15-minute slices (`15–30m`, `30–45m`, `45–60m`, ...) where valid matched points exist.
5. **Zero-Schema-Migration Extrema Persistence (`REQ-PER-038.5`)**: Add `ExtremaType` entries `DFA_A1_FRESH_BASELINE`, `DFA_A1_LATE_MEAN`, and `DFA_A1_DRIFT_PCT`. Persist via `WorkoutSummariesDatabaseManager.updateExtremaValue` without database schema version bump. Map to `WorkoutData`.
6. **Presentation via `HrvDurabilityCard` & Localization Parity (`REQ-PER-038.6`)**: Render `HrvDurabilityCard` in `WorkoutDetailTabbedContent.kt` under `ZONES`. Omit gracefully for non-HRV workouts or sessions $< 60\text{ min}$. Maintain 100% localization parity across all 9 locales in `strings_hrv.xml`.

### 1.3 System Invariants
- Zero SQLite schema migrations (`TABLE_EXTREMA_VALUES` dynamic key storage).
- Strict 10-minute matched data guard prevents noisy drift reporting on short/interval workouts.
- 100% 9-language localization parity (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Test Specification (TST-PER-046)

### Test Case 1: Fresh Baseline & Late Segment Window Selection (`TST-PER-046.1`)
* **Scope**: Unit Test (`HrvDurabilityCalculatorTest.kt`)
* **Preconditions**: Trackpoints with varying elapsed times $t \in [0, 7200\text{ s}]$ and artifact rates.
* **Action**: Evaluate `HrvDurabilityCalculator.partitionWindows`.
* **Expected Result**: Fresh window strictly bounded to $[900, 2700]\text{ s}$; late window strictly $t > 3600\text{ s}$; samples with artifact rate $> 5.0\%$ excluded.

### Test Case 2: Iso-Intensity Matched-Power & Pace Bin Filtering (`TST-PER-046.2`)
* **Scope**: Unit Test (`HrvDurabilityCalculatorTest.kt`)
* **Preconditions**: Fresh baseline median power $P_{\text{ref}} = 200\text{ W}$; samples at $170\text{ W}, 190\text{ W}, 210\text{ W}, 230\text{ W}$.
* **Action**: Apply iso-intensity bin filter $[180\text{ W}, 220\text{ W}]$.
* **Expected Result**: Excludes $170\text{ W}$ and $230\text{ W}$; computes $\alpha_{1,\text{norm}} = \alpha_1 / (P / 200.0)$.

### Test Case 3: Autonomic Durability Drift & 10-Minute Minimum Guard (`TST-PER-046.3`)
* **Scope**: Unit Test (`HrvDurabilityCalculatorTest.kt`)
* **Preconditions**: Fresh matched mean $\alpha_1 = 0.85$, late matched mean $\alpha_1 = 0.68$; varying data durations.
* **Action**: Calculate $\Delta\alpha_{1,\%}$.
* **Expected Result**: Emits $-20.0\%$ when both windows have $\ge 600\text{ s}$ matched data; returns `null` if fresh or late window has $< 600\text{ s}$ matched data.

### Test Case 4: 15-Minute Slice Trajectory Aggregation (`TST-PER-046.4`)
* **Scope**: Unit Test (`HrvDurabilityCalculatorTest.kt`)
* **Preconditions**: 120-minute endurance session with steady matched intervals.
* **Action**: Compute slice trajectory.
* **Expected Result**: Emits ordered slices (`15-30m`, `30-45m`, `45-60m`, `60-75m`, `75-90m`, `90-105m`, `105-120m`) with mean normalized $\alpha_1$; empty slices evaluate to null.

### Test Case 5: ExtremaType Dynamic Persistence & WorkoutData Mapping (`TST-PER-046.5`)
* **Scope**: Unit Test (`WorkoutDataMapperTest.kt`, `HrvPostWorkoutRecomputerTest.kt`)
* **Preconditions**: `ExtremaType` enum entries registered; mock database with extrema records.
* **Action**: Persist durability extrema and map to `WorkoutData`.
* **Expected Result**: Successfully stores and retrieves `dfaA1FreshBaseline`, `dfaA1LateMean`, and `dfaA1DurabilityDriftPct` without schema migration.

### Test Case 6: UI Rendering of HrvDurabilityCard & Graceful Omission (`TST-PER-046.6`)
* **Scope**: Compose UI Test (`HrvDurabilityCardTest.kt`)
* **Preconditions**: Workout with durability metrics vs. non-HRV workout.
* **Action**: Render card in `WorkoutDetailTabbedContent`.
* **Expected Result**: Displays baseline, late mean, drift chip, and trajectory chips for durability workout; completely omitted for non-HRV workout.

### Test Case 7: 9-Language Localization Parity (`TST-PER-046.7`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Preconditions**: 11 new durability string keys in `strings_hrv.xml`.
* **Action**: Run `TranslationParityTest`.
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT with valid specifiers.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-046.1` | Unit | `HrvDurabilityCalculator.kt` | `REQ-PER-038.1` | Specified |
| `TST-PER-046.2` | Unit | `HrvDurabilityCalculator.kt` | `REQ-PER-038.2` | Specified |
| `TST-PER-046.3` | Unit | `HrvDurabilityCalculator.kt` | `REQ-PER-038.3` | Specified |
| `TST-PER-046.4` | Unit | `HrvDurabilityCalculator.kt` | `REQ-PER-038.4` | Specified |
| `TST-PER-046.5` | Unit / DB | `ExtremaType.java`, `WorkoutDataMapper.kt` | `REQ-PER-038.5` | Specified |
| `TST-PER-046.6` | Compose | `HrvDurabilityCard.kt` | `REQ-PER-038.6` | Specified |
| `TST-PER-046.7` | Locale | `strings_hrv.xml` | `REQ-PER-038.6` | Specified |
