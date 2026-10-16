# Walkthrough: ATT-3858 — Display Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) in Workout Summaries and Workout Details

## 1. Summary of Changes
Implemented `REQ-UI-402` and `TST-UI-365` to map and display session-level power-based training metrics — Normalized Power ($\text{NP}$), Intensity Factor ($\text{IF}$), Training Stress Score ($\text{TSS}$), Mechanical Work ($\text{Work kJ}$), Power-to-Weight ($\text{W/kg}$), and Variability Index ($\text{VI}$) — across Workout Summaries (`WorkoutSummary`) and Workout Details (`TrackOnMapScreen` / `MapDetailLayout`):
- **DTO Enrichment (`WorkoutData.kt` & `WorkoutDetailsData.kt`)**:
  - Added `normalizedPowerWatts: Double? = null`, `intensityFactor: Double? = null`, `tss: Double? = null`, `workKj: Double? = null`, `avgPowerToWeight: Double? = null`, `maxPowerToWeight: Double? = null`, and `variabilityIndex: Double? = null`.
- **Symmetric DTO & Snapshot Resolution (`WorkoutDataMapper.kt` — Rule 20)**:
  - Added `PowerTrainingSummary` and `resolvePowerTrainingSummary(...)`, invoked identically in both `fromCursor(cursor)` and `fromSnapshot(snapshot, batch)`.
  - Prioritizes `ExtremaType.END` (falling back to `ExtremaType.AVG` / `ExtremaType.MAX`) for cumulative session metrics (`SensorType.NORMALIZED_POWER`, `SensorType.INTENSITY_FACTOR`, `SensorType.TSS`, `SensorType.WORK_KJ`), extracts `AVG` and `MAX` for `SensorType.POWER_TO_WEIGHT` and `SensorType.POWER`, computes `variabilityIndex = PowerPhysiologyCalculator.calculateVariabilityIndex(np, avgPower)`, and provides on-the-fly fallback derivation from `avgPowerWatts`, `maxPowerWatts`, `activeTimeSec`, `effectiveFtpWatts`, and `effectiveWeightKg` for historical workouts.
- **Shared Aftermath UI Presentation (`WorkoutDetails.kt` — Rule 23)**:
  - Added `PowerTrainingMetricsSection` rendering single-line non-wrapping badges (`maxLines = 1, softWrap = false`) for `NP`, `IF`, `TSS`, `Work kJ`, `W/kg`, and `VI` using `MaterialTheme.colorScheme.surfaceContainerHighest` and `MaterialTheme.colorScheme.onSurface` (zero raw hex literals outside `Color.kt`).
- **9-Language Localization Parity (`values*/strings.xml`)**:
  - Added all 6 `workout_power_metrics_*` string keys (`workout_power_metrics_np_format`, `workout_power_metrics_if_format`, `workout_power_metrics_tss_format`, `workout_power_metrics_work_format`, `workout_power_metrics_wkg_format`, `workout_power_metrics_vi_format`) across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

## 2. Verification & Regression Results
- **Governance Check (`python3 tools/verify_requirement_governance.py`)**: Passed (`[PASS] All Requirement Governance invariants verified successfully`).
- **Unit & Contract Tests (`TST-UI-365`)**:
  - `WorkoutDataMappingSymmetryTest`: Verified 100% Rule 20 DTO symmetry across `fromCursor` (single & batch) and `fromSnapshot`, `ExtremaType.END` priority over `AVG`, `variabilityIndex` calculation, and on-the-fly fallback derivation.
  - `AthleteProfileContractAndLocalizationTest`: Verified `WorkoutDetails.kt` renders `PowerTrainingMetricsSection` with all 6 localized format strings and verified 9-locale parity across all 9 `values*/strings.xml` files.
  - `ThemeColorCentralizationAuditTest`: Verified zero raw hex color literals outside `Color.kt`.
- **Clean-Room Full Unit Test Suite (`./gradlew testDebugUnitTest`)**: Executed and passed 100% (`BUILD SUCCESSFUL`).
