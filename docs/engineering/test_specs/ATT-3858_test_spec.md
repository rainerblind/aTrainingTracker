# Stage 2 Requirement & Test Specification: `ATT-3858` — Display Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) in Workout Summaries and Workout Details

## 1. Formal Requirement (`REQ-UI-402`)
**`REQ-UI-402`: Symmetric DTO Mapping & Aftermath Display of Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) in Workout Summaries and Workout Details.**
The system SHALL map and display session-level power-based training metrics — Normalized Power ($\text{NP}$, in $\text{W}$), Intensity Factor ($\text{IF} = \text{NP} / \text{FTP}$), Training Stress Score ($\text{TSS} = \frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$), Mechanical Work ($\text{Work}$, in $\text{kJ}$), Power-to-Weight ($\text{W/kg}$), and Variability Index ($\text{VI} = \text{NP} / \text{Avg\_Power}$) — across `WorkoutData`, `WorkoutDetailsData`, `WorkoutDataMapper`, and `WorkoutDetails` (shared by `WorkoutSummary` and `TrackOnMapScreen` / `MapDetailLayout`) (`ATT-3858`, extending `REQ-MIG-039`, `REQ-UI-399`, `REQ-UI-400`, and `REQ-STB-013`):
1. *DTO Properties & Symmetric Resolution (`REQ-UI-402.1` — Rule 20)*:
   - `WorkoutData` and `WorkoutDetailsData` SHALL expose `normalizedPowerWatts: Double? = null`, `intensityFactor: Double? = null`, `tss: Double? = null`, `workKj: Double? = null`, `avgPowerToWeight: Double? = null`, `maxPowerToWeight: Double? = null`, and `variabilityIndex: Double? = null`.
   - `WorkoutDataMapper` SHALL define `internal data class PowerTrainingSummary(...)` and `internal fun resolvePowerTrainingSummary(...)`, invoked symmetrically in both `fromCursor(cursor)` and `fromSnapshot(snapshot, batch)`.
   - `resolvePowerTrainingSummary(...)` SHALL prioritize `ExtremaType.END` (falling back to `ExtremaType.AVG` / `ExtremaType.MAX`) for cumulative session metrics (`SensorType.NORMALIZED_POWER`, `SensorType.INTENSITY_FACTOR`, `SensorType.TSS`, `SensorType.WORK_KJ`), read `ExtremaType.AVG` and `ExtremaType.MAX` for `SensorType.POWER_TO_WEIGHT` and `SensorType.POWER`, compute `variabilityIndex = PowerPhysiologyCalculator.calculateVariabilityIndex(np, avgPower)`, and provide an on-the-fly fallback from `avgPowerWatts`, `maxPowerWatts`, `activeTimeSec`, `effectiveFtpWatts`, and `effectiveWeightKg` for historical power workouts.
2. *Shared Aftermath UI Presentation (`REQ-UI-402.2` — Rule 23)*:
   - `WorkoutDetails.kt` SHALL render a dedicated Power Training Metrics summary section whenever power training metrics are present (`data.normalizedPowerWatts != null || data.tss != null || data.workKj != null || data.avgPowerToWeight != null`), displaying formatted badges/items for **NP** (`%1$.0f W`), **IF** (`%1$.2f`), **TSS** (`%1$.0f`), **Work** (`%1$.0f kJ`), **W/kg** (`%1$.2f W/kg`), and **VI** (`%1$.2f`).
   - Non-power workouts SHALL render zero power training metric rows.
3. *9-Language Localization Parity (`REQ-UI-402.3`)*:
   - All 6 new string keys (`workout_power_metrics_np_format`, `workout_power_metrics_if_format`, `workout_power_metrics_tss_format`, `workout_power_metrics_work_format`, `workout_power_metrics_wkg_format`, `workout_power_metrics_vi_format`) SHALL exist and be non-blank with matching positional format specifiers across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

## 2. Test Specification (`TST-UI-365`)
1. **Rule 20 DTO & Snapshot Mapping Symmetry Tests (`WorkoutDataMappingSymmetryTest.kt`)**:
   - Verify `WorkoutDataMapper.fromCursor` (single & batch) and `WorkoutDataMapper.fromSnapshot` produce identical `normalizedPowerWatts`, `intensityFactor`, `tss`, `workKj`, `avgPowerToWeight`, `maxPowerToWeight`, and `variabilityIndex` values on both `WorkoutData` and `WorkoutDetailsData`.
   - Verify `resolvePowerTrainingSummary` prioritizes `ExtremaType.END` over `ExtremaType.AVG` for cumulative metrics and computes `variabilityIndex = NP / Avg_Power`, as well as on-the-fly fallback derivation from `avgPowerWatts`, `activeTimeSec`, `effectiveFtpWatts`, and `effectiveWeightKg`.
2. **UI Source Contract & 9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)**:
   - Verify `WorkoutDetails.kt` renders the Power Training Metrics section using `R.string.workout_power_metrics_np_format`, `R.string.workout_power_metrics_if_format`, `R.string.workout_power_metrics_tss_format`, `R.string.workout_power_metrics_work_format`, `R.string.workout_power_metrics_wkg_format`, and `R.string.workout_power_metrics_vi_format`.
   - Verify all 6 `workout_power_metrics_*` string keys exist and are non-blank with matching positional specifiers across all 9 locales.
3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

## 3. Traceability Matrix
| Requirement ID | Clause | Test ID | Test Class / Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-402.1` | Symmetric DTO & Mapper Resolution (Rule 20) | `TST-UI-365` | `WorkoutDataMappingSymmetryTest` |
| `REQ-UI-402.2` | Shared Aftermath `WorkoutDetails` UI Rendering | `TST-UI-365` | `AthleteProfileContractAndLocalizationTest` |
| `REQ-UI-402.3` | 9-Language Localization Parity | `TST-UI-365` | `AthleteProfileContractAndLocalizationTest` |
