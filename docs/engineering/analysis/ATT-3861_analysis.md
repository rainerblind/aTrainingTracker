# Stage 1 Analysis: ATT-3861 — Calculate and Display Efficiency Factor (EF) and Aerobic Decoupling (Pw:Hr / Pa:Hr) in Workout Summaries, Details, and Imports

## 1. Problem Domain & Motivation
While mechanical power metrics (`NP`, `IF`, `TSS`) quantify external athletic output, endurance physiology requires comparing external output against internal cardiovascular cost (Heart Rate) to evaluate aerobic efficiency and cardiovascular durability over a workout session:
1. **Efficiency Factor ($\text{EF}$)**: Quantifies how much mechanical output (power in Watts or running speed in $\text{m/min}$) the athlete produces per heartbeat ($\text{bpm}$):
   - **Cycling ($\text{Power} + \text{HR}$)**:
     $$\text{EF}_{\text{pw}} = \frac{\text{NP}}{\text{Avg\_HR}} \quad (\text{in W/bpm, e.g., } 1.65)$$
   - **Running ($\text{Speed} + \text{HR}$)**:
     $$\text{EF}_{\text{pa}} = \frac{\text{Avg\_Speed}_{\text{m/s}} \times 60.0}{\text{Avg\_HR}} \quad (\text{in m/min/bpm, e.g., } 1.80)$$
2. **Aerobic Decoupling ($\text{Pw:Hr }\%$ for cycling, $\text{Pa:Hr }\%$ for running)**: Quantifies cardiac drift between the first half ($H_1$) and second half ($H_2$) of an active workout stream split into two equal halves by duration/sample count:
   - Compute first-half efficiency $\text{EF}_1$ on $H_1$ and second-half efficiency $\text{EF}_2$ on $H_2$:
     - Cycling: $\text{EF}_1 = \text{NP}_1 / \text{Avg\_HR}_1$, $\text{EF}_2 = \text{NP}_2 / \text{Avg\_HR}_2$.
     - Running: $\text{EF}_1 = \text{Avg\_Speed}_1 / \text{Avg\_HR}_1$, $\text{EF}_2 = \text{Avg\_Speed}_2 / \text{Avg\_HR}_2$.
   - Compute percentage decoupling:
     $$\text{Decoupling }\% = \frac{\text{EF}_1 - \text{EF}_2}{\text{EF}_1} \times 100.0$$
   - **Physiological Interpretation**:
     - $< 5.0\%$: **Coupled** cardiovascular response (good aerobic endurance durability).
     - $\ge 5.0\%$: **Uncoupled / High Drift** (significant cardiovascular drift due to aerobic fatigue, dehydration, heat stress, or glycogen depletion).

Currently, **aTrainingTracker** records 1-Hz `POWER`, `SPEED_mps`, and `HR` samples in `WorkoutSamplesDatabaseManager` and persists `MIN`/`AVG`/`MAX` extrema in `WorkoutSummaries.TABLE_EXTREMA_VALUES`, but neither computes nor displays Efficiency Factor ($\text{EF}$) or Aerobic Decoupling ($\text{Pw:Hr}$ / $\text{Pa:Hr}$) during live session finalization, TCX/GPX/FIT imports, Workout Summary cards, or Workout Details views.

---

## 2. Forensic Root Cause & Gap Analysis

### 2.1 Shared Pure Domain Physiology Engine (`physiology/PowerPhysiologyCalculator.kt`)
- In compliance with `docs/coding_standards.md` Section 1.1 (**Rule 36 — Single Source of Truth & DRY**), `PowerPhysiologyCalculator` (introduced in `ATT-3862`) is the canonical stateless utility for physiology math.
- We need to extend `PowerPhysiologyCalculator` with:
  1. `calculateNormalizedPower(powerSamplesWatts: List<Double?>): Double?`: Computes Dr. Andrew Coggan's 30-second rolling average (using the growing $1..29\text{ s}$ warm-up window per Rule 40) raised to the 4th power, averaged, and taken to the $1/4$ root.
  2. `calculateEfficiencyFactorPower(normalizedPowerWatts: Double, avgHeartRateBpm: Double): Double?`: Returns $\text{NP} / \text{Avg\_HR}$ when both inputs are finite and $> 0$.
  3. `calculateEfficiencyFactorSpeed(avgSpeedMps: Double, avgHeartRateBpm: Double): Double?`: Returns $(\text{avgSpeedMps} \times 60.0) / \text{Avg\_HR}$ (in $\text{m/min/bpm}$) when both inputs are finite and $> 0$.
  4. `calculateAerobicDecouplingFromEf(efFirstHalf: Double, efSecondHalf: Double): Double?`: Returns $((ef_1 - ef_2) / ef_1) \times 100.0$ when $ef_1 > 0$ and $ef_2 > 0$.
  5. `computeAerobicEfficiencyAndDecoupling(powerSamplesWatts: List<Double?>, speedSamplesMps: List<Double?>, hrSamplesBpm: List<Double?>, bSportType: BSportType): AerobicEfficiencyResult`:
     - Filters active paired samples where HR $> 0$ and output (Power $\ge 0$ with positive mean, or Speed $> 0$) is valid.
     - Prefers Power-based (`Pw:Hr`, modality `POWER`) when valid power + HR samples exist (or `bSportType == BSportType.BIKE` with power), and falls back to Speed/Pace-based (`Pa:Hr`, modality `SPEED_mps`) when valid speed + HR samples exist.
     - Splits the active paired stream into two equal halves ($H_1 = [0, N/2)$, $H_2 = [N/2, N)$) when $N \ge 2$, computes full-session $\text{EF}$, $\text{EF}_1$, $\text{EF}_2$, $\text{Decoupling }\%$, and `isCoupled = decouplingPct < 5.0`.

### 2.2 Database Persistence (`ExtremaType.java`, `TrackerService.java`, `LegacyImportEngine.kt`)
- `WorkoutSummaries.TABLE_EXTREMA_VALUES` stores `(workoutID, sensorType, extremumType, value)` and is batch-loaded in a single query by `WorkoutSummariesDatabaseManager.getExtremaValuesForWorkouts(workoutIds)`.
- By adding `EFFICIENCY_FACTOR(R.string.efficiency_factor_short)` and `AEROBIC_DECOUPLING(R.string.aerobic_decoupling_short)` to `ExtremaType.java`:
  - Storing `(SensorType.POWER, ExtremaType.EFFICIENCY_FACTOR, ef)` and `(SensorType.POWER, ExtremaType.AEROBIC_DECOUPLING, decouplingPct)` persists cycling `Pw:Hr` metrics.
  - Storing `(SensorType.SPEED_mps, ExtremaType.EFFICIENCY_FACTOR, ef)` and `(SensorType.SPEED_mps, ExtremaType.AEROBIC_DECOUPLING, decouplingPct)` persists running/speed `Pa:Hr` metrics.
  - This requires **zero** SQLite schema migration (`DB_VERSION` remains `24`) and preserves `SensorType` count (`44`) in `SensorCategoryAndSelectorContractTest`.
- Both `LegacyImportEngine.recalculateStats()` (called for TCX, GPX, and FIT imports) and `TrackerService.finalizeLiveSession()` (called at the end of live recording) will query the workout's sample table (`POWER`, `SPEED_mps`, `HR`), compute `AerobicEfficiencyResult`, and persist `EFFICIENCY_FACTOR` and `AEROBIC_DECOUPLING` into `WorkoutSummariesDatabaseManager` and `WorkoutRepository`.

### 2.3 DTO & UI Presentation (`WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutDetailsData.kt`, `WorkoutDetails.kt`)
- `WorkoutData` and `WorkoutDetailsData` will expose:
  - `efficiencyFactor: Double? = null`
  - `aerobicDecouplingPct: Double? = null`
  - `isPowerBasedEfficiency: Boolean = true` (`true` for `Pw:Hr` in `W/bpm`, `false` for `Pa:Hr` in `m/min/bpm`)
  - `isAerobicDecouplingHighDrift: Boolean = false` (`true` when `aerobicDecouplingPct >= 5.0`)
- **Rule 20 Symmetry**: `WorkoutDataMapper.fromCursor(cursor)` and `WorkoutDataMapper.fromSnapshot(snapshot, batch)` will both resolve these fields via a shared helper `resolveAerobicEfficiencySummary(...)`, including an on-the-fly fallback for `efficiencyFactor` if `HR (AVG)` and `NORMALIZED_POWER` / `POWER (AVG)` / `avgSpeedMps` exist on historical workouts prior to sample recalculation.
- **UI Rendering (`WorkoutDetails.kt`)**: Since `WorkoutDetails` is rendered inside both `WorkoutSummary.kt` (Workout Summary cards) and `TrackOnMapScreen.kt` / `MapDetailLayout.kt` (Workout Details screen), rendering a clean aerobic efficiency & decoupling row with a semantic Coupled ($< 5\%$) vs. High Drift ($\ge 5\%$) badge inside `WorkoutDetails.kt` surfaces the metrics consistently across both views without disturbing `WorkoutSummaryClickContractTest` (which checks the 4 telemetry graphs in `// 8. Telemetry Metric Graphs`).

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)
- **Existing Requirements Inspected**:
  - `REQ-UI-399` (`ATT-3862`): Critical Power ($CP$), $W'$, and $W'_{\text{bal}}$ in Athlete Profile, Live Cockpit, and Aftermath.
  - `REQ-STB-013` / Rule 20: Cursor and batch snapshot mapping symmetry in `WorkoutDataMapper`.
- **Preservation of Core Invariants**:
  - Existing requirements are **not** modified or weakened. A new requirement (`REQ-UI-400`) and test specification (`TST-UI-363`) are added for Efficiency Factor ($\text{EF}$) and Aerobic Decoupling ($\text{Pw:Hr}$ / $\text{Pa:Hr}$).

---

## 4. User Scope Grounding (`ATT-1250`)
### In Scope
1. Pure domain calculation of `calculateNormalizedPower`, `calculateEfficiencyFactorPower`, `calculateEfficiencyFactorSpeed`, `calculateAerobicDecouplingFromEf`, and `computeAerobicEfficiencyAndDecoupling` in `PowerPhysiologyCalculator.kt`.
2. Persistence of `ExtremaType.EFFICIENCY_FACTOR` and `ExtremaType.AEROBIC_DECOUPLING` on `SensorType.POWER` (`Pw:Hr`) or `SensorType.SPEED_mps` (`Pa:Hr`) during live workout finalization (`TrackerService.finalizeLiveSession`) and TCX/GPX/FIT imports (`LegacyImportEngine.recalculateStats`).
3. Symmetric mapping in `WorkoutDataMapper` (`fromCursor` and `fromSnapshot`) and UI presentation in `WorkoutDetails.kt` (shared by `WorkoutSummary.kt` and `TrackOnMapScreen.kt`) with semantic Coupled ($< 5.0\%$) vs. Drift ($\ge 5.0\%$) indicator badge.
4. 9-language localization parity across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, and `values-pt`.

### Out of Scope
- Real-time 1-Hz cockpit tile for aerobic decoupling (decoupling compares the first half and second half of a completed endurance session).
- Elevation-grade-adjusted running pace (NGP) modeling beyond actual horizontal speed/pace and heart rate streams.
