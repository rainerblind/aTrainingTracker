# Stage 3 Implementation Plan: `ATT-3858` — Display Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) in Workout Summaries and Workout Details

## 1. Architectural Overview (SWE.2)
The implementation decomposes `REQ-UI-402` across three clean layers:
1. **Symmetric DTO & Mapper Layer (`WorkoutData.kt`, `WorkoutDetailsData.kt`, `WorkoutDataMapper.kt`)**:
   - Extend `WorkoutData` and `WorkoutDetailsData` with:
     - `normalizedPowerWatts: Double? = null`
     - `intensityFactor: Double? = null`
     - `tss: Double? = null`
     - `workKj: Double? = null`
     - `avgPowerToWeight: Double? = null`
     - `maxPowerToWeight: Double? = null`
     - `variabilityIndex: Double? = null`
   - In `WorkoutDataMapper.kt`, implement `internal data class PowerTrainingSummary(...)` and `internal fun resolvePowerTrainingSummary(...)`:
     - Prioritizes `ExtremaType.END` (falling back to `ExtremaType.AVG` / `ExtremaType.MAX`) for cumulative session metrics (`SensorType.NORMALIZED_POWER`, `SensorType.INTENSITY_FACTOR`, `SensorType.TSS`, `SensorType.WORK_KJ`), and reads `ExtremaType.AVG` / `ExtremaType.MAX` for `SensorType.POWER_TO_WEIGHT` and `SensorType.POWER`.
     - Computes `variabilityIndex = PowerPhysiologyCalculator.calculateVariabilityIndex(np, avgPower)` when both `NP` and `Avg_Power` are positive.
     - Provides on-the-fly fallback derivation from `avgPowerWatts`, `maxPowerWatts`, `activeTimeSec`, `effectiveFtpWatts` (`SettingsDataStoreJavaHelper.resolveEffectiveFtpAt`), and `effectiveWeightKg` (`SettingsDataStoreJavaHelper.resolveEffectiveWeightKgAt`) for historical power workouts.
     - Invoked symmetrically in both `fromCursor(cursor)` and `fromSnapshot(snapshot, batch)` (Rule 20).
2. **Aftermath Shared UI Component (`WorkoutDetails.kt`)**:
   - In `WorkoutDetails.kt` (shared by `WorkoutSummary.kt` and `TrackOnMapScreen.kt`), add **Section 6: Power Training Metrics (`REQ-UI-402.2`)** rendered whenever `data.normalizedPowerWatts != null || data.tss != null || data.workKj != null || data.avgPowerToWeight != null`.
   - Displays formatted single-line metric badges (`maxLines = 1, softWrap = false`, Rule 38) using `MaterialTheme.colorScheme.surfaceContainerHighest` / `onSurface` (zero hardcoded `Color(0x...)` literals) for:
     - `NP` (`R.string.workout_power_metrics_np_format`, e.g., `NP: 245 W`)
     - `IF` (`R.string.workout_power_metrics_if_format`, e.g., `IF: 0.92`)
     - `TSS` (`R.string.workout_power_metrics_tss_format`, e.g., `TSS: 85`)
     - `Work` (`R.string.workout_power_metrics_work_format`, e.g., `Work: 820 kJ`)
     - `W/kg` (`R.string.workout_power_metrics_wkg_format`, e.g., `3.27 W/kg`)
     - `VI` (`R.string.workout_power_metrics_vi_format`, e.g., `VI: 1.05`)
3. **9-Locale String Resources (`values*/strings.xml`)**:
   - Add the 6 `workout_power_metrics_*` string keys across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

## 2. UI Consistency (Rule 23 & `docs/design_guidelines.md` Section 5)
- **Reference Component**: `WorkoutDetails.kt` (`Section 4: Anaerobic Battery (W'bal)` and `Section 5: Efficiency Factor (EF) & Aerobic Decoupling`).
- **Reused Components & Theme Tokens**:
  - Uses `Row` / `Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest)` and `Text(style = MaterialTheme.typography.bodySmall / labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)` inside `WorkoutDetails.kt`.
  - Zero raw `Color(0x...)` literals outside `Color.kt` (`ThemeColorCentralizationAuditTest` compliant) and zero modifications to `// 8. Telemetry Metric Graphs` in `WorkoutSummary.kt` (`WorkoutSummaryClickContractTest` compliant).

## 3. Atomic Step Breakdown
| Step | Target Files | Description | Verification |
| :--- | :--- | :--- | :--- |
| **Step 1** | `WorkoutData.kt`, `WorkoutDetailsData.kt`, `WorkoutDataMapper.kt` | Add power training metric fields to `WorkoutData` and `WorkoutDetailsData`, and implement symmetric `resolvePowerTrainingSummary` in `WorkoutDataMapper`. | `WorkoutDataMappingSymmetryTest` |
| **Step 2** | `WorkoutDetails.kt`, `values*/strings.xml` | Render Power Training Metrics section in `WorkoutDetails.kt` and localize all 6 `workout_power_metrics_*` strings across all 9 locales. | `AthleteProfileContractAndLocalizationTest` |
| **Step 3** | `WorkoutDataMappingSymmetryTest.kt`, `AthleteProfileContractAndLocalizationTest.kt` | Add Rule 20 DTO symmetry, fallback resolution, UI contract, and 9-locale tests (`TST-UI-365`). | `./gradlew testDebugUnitTest --tests ...` |

## 4. Invariants & Rollback Safety
- **Rule 4 Gate 3 Pre-Check**: `python3 tools/jira_util.py check-gate <Impl-Plan-Key>` MUST exit with `0` before modifying any production code.
- **Rule 20 Mapping Symmetry**: `fromCursor(cursor)`, `fromCursor(cursor, batch)`, and `fromSnapshot(snapshot, batch)` MUST populate all 7 power training metric fields identically.
- **9-Locale Parity**: All 6 `workout_power_metrics_*` keys MUST exist across all 9 `values*/strings.xml` files with matching positional format specifiers.
