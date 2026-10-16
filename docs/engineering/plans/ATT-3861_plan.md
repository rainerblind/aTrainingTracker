# Stage 3 Implementation Plan: ATT-3861 — Calculate and Display Efficiency Factor (EF) and Aerobic Decoupling (Pw:Hr / Pa:Hr) in Workout Summaries, Details, and Imports

## 1. Architectural Overview (SWE.2)
The implementation decomposes `REQ-UI-400` across four clean architectural layers:
1. **Pure Domain Physiology Engine (`com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.kt`)**:
   - Extend the stateless, pure Kotlin physiology calculator (Rule 36 — Single Source of Truth & DRY) with:
     - `AEROBIC_DECOUPLING_DRIFT_THRESHOLD_PCT: Double = 5.0`
     - `data class AerobicEfficiencyResult(val efficiencyFactor: Double?, val decouplingPct: Double?, val efFirstHalf: Double?, val efSecondHalf: Double?, val isPowerBased: Boolean, val isHighDrift: Boolean)`
     - `calculateNormalizedPower(powerSamplesWatts: List<Double?>): Double?`: Computes Dr. Andrew Coggan's 30-second rolling average (with growing $1..29\text{ s}$ warm-up window per Rule 40) raised to the 4th power, averaged across active samples, and taken to the $1/4$ root.
     - `calculateEfficiencyFactorPower(normalizedPowerWatts: Double, avgHeartRateBpm: Double): Double?`: Returns $\text{NP} / \text{Avg\_HR}$ (in $\text{W/bpm}$) when $\text{NP} > 0$ and $\text{Avg\_HR} > 0$.
     - `calculateEfficiencyFactorSpeed(avgSpeedMps: Double, avgHeartRateBpm: Double): Double?`: Returns $(\text{avgSpeedMps} \times 60.0) / \text{Avg\_HR}$ (in $\text{m/min/bpm}$) when $\text{avgSpeedMps} > 0$ and $\text{Avg\_HR} > 0$.
     - `calculateAerobicDecouplingFromEf(efFirstHalf: Double, efSecondHalf: Double): Double?`: Returns $((\text{EF}_1 - \text{EF}_2) / \text{EF}_1) \times 100.0$ when $\text{EF}_1 > 0$ and $\text{EF}_2 > 0$.
     - `computeAerobicEfficiencyAndDecoupling(powerSamplesWatts: List<Double?>, speedSamplesMps: List<Double?>, hrSamplesBpm: List<Double?>, bSportType: BSportType): AerobicEfficiencyResult`: Filters paired active samples, prefers Power + HR (`Pw:Hr`, `isPowerBased = true`) when valid positive power and HR samples exist, falls back to Speed + HR (`Pa:Hr`, `isPowerBased = false`) when valid positive speed and HR samples exist, splits the active paired stream into two equal halves ($H_1 = [0, N/2)$, $H_2 = [N/2, N)$), computes $\text{EF}$, $\text{EF}_1$, $\text{EF}_2$, and $\text{Decoupling }\%$, and flags `isHighDrift = decouplingPct >= 5.0`.
2. **Persistence in Live Finalization & File Imports (`ExtremaType.java`, `TrackerService.java`, `LegacyImportEngine.kt`)**:
   - `ExtremaType.java`: Add `EFFICIENCY_FACTOR(R.string.efficiency_factor_short)` and `AEROBIC_DECOUPLING(R.string.aerobic_decoupling_short)`.
   - `LegacyImportEngine.kt` (`recalculateStats`): Query `POWER`, `SPEED_mps`, and `HR` sample streams from the workout's sample table (`WorkoutSamplesDatabaseManager`), invoke `PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(...)`, and persist `ExtremaType.EFFICIENCY_FACTOR` and `ExtremaType.AEROBIC_DECOUPLING` under `SensorType.POWER` (when `isPowerBased == true`) or `SensorType.SPEED_mps` (when `isPowerBased == false`).
   - `TrackerService.java` (`finalizeLiveSession`): Query `POWER`, `SPEED_mps`, and `HR` from the workout's sample table on `mDbExecutor` / during session finalization, compute `AerobicEfficiencyResult`, and persist `ExtremaType.EFFICIENCY_FACTOR` and `ExtremaType.AEROBIC_DECOUPLING` into `WorkoutSummariesDatabaseManager` and `WorkoutRepository`.
3. **Symmetric DTO Mapping (`WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutDetailsData.kt`)**:
   - Add `efficiencyFactor: Double? = null`, `aerobicDecouplingPct: Double? = null`, `isPowerBasedEfficiency: Boolean = true`, and `isAerobicDecouplingHighDrift: Boolean = false` to `WorkoutData` and `WorkoutDetailsData`.
   - In `WorkoutDataMapper.kt`, implement `resolveAerobicEfficiencySummary(...)` and invoke it symmetrically in both `fromCursor(cursor)` and `fromSnapshot(snapshot, batch)` (Rule 20), reading `EFFICIENCY_FACTOR` and `AEROBIC_DECOUPLING` from `SensorType.POWER` or `SensorType.SPEED_mps` (with fallback calculation of `efficiencyFactor` when `HR (AVG)` and `POWER`/`SPEED_mps` averages exist).
4. **Aftermath UI & 9-Locale Resources (`WorkoutDetails.kt`, `values*/strings.xml`)**:
   - In `WorkoutDetails.kt` (shared by `WorkoutSummary.kt` and `TrackOnMapScreen.kt`), render a compact row when `data.efficiencyFactor != null` displaying the formatted Efficiency Factor (`EF: 1.65 W/bpm` or `EF: 1.80 m/min/bpm`) and Aerobic Decoupling (`Pw:Hr: 3.2%` or `Pa:Hr: 6.1%`), accompanied by a semantic status badge:
     - Coupled ($< 5.0\%$): `MaterialTheme.colorScheme.secondaryContainer` / `onSecondaryContainer` (`R.string.workout_decoupling_coupled`).
     - High Drift ($\ge 5.0\%$): `MaterialTheme.colorScheme.errorContainer` / `onErrorContainer` (`R.string.workout_decoupling_drift`).

---

## 2. UI Consistency (Rule 23 & `docs/design_guidelines.md` Section 5)
- **Reference Component**: `WorkoutDetails.kt` (`Section 4: Anaerobic Battery (W'bal) Minimum & Empty-Tank Badge`).
- **Reused Components & Tokens**:
  - Uses `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically)` inside `WorkoutDetails.kt` with `MaterialTheme.typography.bodyMedium` (`FontWeight.Medium`, `onSurface`) and `Surface(shape = MaterialTheme.shapes.small)` status badge (`errorContainer`/`onErrorContainer` for $\ge 5.0\%$ drift per `docs/design_guidelines.md` Section 5.4, and `secondaryContainer`/`onSecondaryContainer` for $< 5.0\%$ coupled).
  - Zero raw `Color(0x...)` literals outside `Color.kt` (`ThemeColorCentralizationAuditTest` compliant) and zero modifications to `// 8. Telemetry Metric Graphs` in `WorkoutSummary.kt` (`WorkoutSummaryClickContractTest` compliant).

---

## 3. Atomic Step Breakdown

| Step | Target Files | Description | Verification |
| :--- | :--- | :--- | :--- |
| **Step 1** | `PowerPhysiologyCalculator.kt` | Implement `calculateNormalizedPower`, `calculateEfficiencyFactorPower`, `calculateEfficiencyFactorSpeed`, `calculateAerobicDecouplingFromEf`, and `computeAerobicEfficiencyAndDecoupling`. | `PowerPhysiologyCalculatorTest` |
| **Step 2** | `ExtremaType.java`, `LegacyImportEngine.kt`, `TrackerService.java` | Add `EFFICIENCY_FACTOR` and `AEROBIC_DECOUPLING` to `ExtremaType`, and compute/persist them during `LegacyImportEngine.recalculateStats()` and `TrackerService.finalizeLiveSession()`. | Targeted unit tests |
| **Step 3** | `WorkoutData.kt`, `WorkoutDetailsData.kt`, `WorkoutDataMapper.kt`, `WorkoutDetails.kt`, `values*/strings.xml` | Add DTO fields, symmetric `resolveAerobicEfficiencySummary` in `WorkoutDataMapper`, UI rendering in `WorkoutDetails.kt`, and 9-locale string resources. | `WorkoutDataMappingSymmetryTest`, `AthleteProfileContractAndLocalizationTest` |

---

## 4. Invariants & Rollback Safety
- **Rule 4 Gate 3 Pre-Check**: `python3 tools/jira_util.py check-gate <Impl-Plan-Key>` MUST exit with `0` before modifying any production file in `app/src/...`.
- **Rule 20 Mapping Symmetry**: `fromCursor(cursor)`, `fromCursor(cursor, batch)`, and `fromSnapshot(snapshot, batch)` MUST populate `efficiencyFactor`, `aerobicDecouplingPct`, `isPowerBasedEfficiency`, and `isAerobicDecouplingHighDrift` identically.
- **9-Locale Parity**: All 8 new string keys MUST exist across all 9 `values*/strings.xml` files with matching positional format specifiers.
