# Stage 3 Implementation Plan: ATT-3862 — Support Critical Power (CP), W' (Anaerobic Work Capacity), and W'bal (Anaerobic Battery) in Athlete Profile, Live Cockpit, and Aftermath

## 1. Architectural Overview (SWE.2)
The implementation decomposes `REQ-UI-399` across five clean architectural layers:
1. **Pure Domain Physiology Engine (`com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.kt`)**:
   - Stateless, pure Kotlin math engine (Rule 36 — Single Source of Truth & DRY) providing:
     - `estimateCpAndWPrime(p1Watts: Double, t1Seconds: Int, p2Watts: Double, t2Seconds: Int): CpWPrimeEstimate?` (2-point linear work-time model $W_i = P_i \cdot t_i = CP \cdot t_i + W'$).
     - `stepWPrimeBalanceJoules(prevWPrimeBalJ: Double, powerWatts: Double, cpWatts: Double, wPrimeJoules: Double, deltaTimeSec: Double = 1.0): Double` (Skiba's differential expenditure $P > CP$ and exponential reconstitution $\tau_{W'} = 546 e^{-0.01(CP - P)} + 316$ for $P \le CP$).
     - `computeWPrimeBalanceSeries(powerSamplesWatts: List<Double?>, cpWatts: Double, wPrimeKj: Double, sampleIntervalSec: Double = 1.0): WPrimeSummaryResult` returning the 1-Hz `seriesKj: List<Float?>`, `minWPrimeBalKj: Double?`, `avgWPrimeBalKj: Double?`, `endWPrimeBalKj: Double?`, `minWPrimeBalPct: Double?`, and `isEmptyTankWarning: Boolean` ($\min W'_{\text{bal},\%} < 10.0\%$).
2. **Persistence & Unidirectional DataStore Layer**:
   - `AthleteMetricType` in `AthleteMetricsDatabaseManager.kt`: add `CP_WATTS("CP_WATTS", 50.0, 1000.0)` and `W_PRIME_KJ("W_PRIME_KJ", 5.0, 60.0)`.
   - `SettingsDataStore.kt` & `SettingsDataStoreJavaHelper.kt`: add `KEY_CYCLING_CP_WATTS` (`cpWattsFlow`, `setCpWatts(Int?)`, `getCpWatts(context)`, `getEffectiveCpWatts(context)` falling back to `getFtp(context)`) and `KEY_CYCLING_W_PRIME_KJ` (`wPrimeKjFlow`, `setWPrimeKj(Float?)`, `getWPrimeKj(context)`, `getEffectiveWPrimeKj(context)` defaulting to `20.0f` kJ).
   - `AthleteMetricsRepository.kt`: synchronize latest `CP_WATTS` and `W_PRIME_KJ` entries unidirectionally to `SettingsDataStore` in `syncLatestToDataStoreLocked()`.
3. **Athlete Profile UI & ViewModel Layer**:
   - `AthleteMetricsPlotMath.kt`: add `PlotMetricType.CP` and `PlotMetricType.W_PRIME` and delegate `estimateCpAndWPrime` to `PowerPhysiologyCalculator`.
   - `AthleteProfileViewModel.kt` & `AthleteProfileScreen.kt`: expose `latestCpWatts` and `latestWPrimeKj` in `AthleteProfileUiState`, render summary cards & filter pills for `CP` (`W`) and `W'` (`kJ`), and include an inline expandable 2-point CP & W' estimator inside `MetricEntryDialog` when `CP_WATTS` or `W_PRIME_KJ` is selected.
4. **Live Cockpit Sensor Layer (`SensorType.java`, `CyclingPowerMetricsDevice.java`, `TrackerService.java`)**:
   - `SensorType.java`: add `W_PRIME_BAL(SensorCategory.POWER_AND_PEDALING, R.string.w_prime_bal, R.string.w_prime_bal_short, R.string.units_work_kj, SensorValueType.DOUBLE, new PowerToWeightFormatter(), false)`.
   - `CyclingPowerMetricsDevice.java`: add `CpProvider` and `WPrimeKjProvider` functional interfaces (defaulting to `SettingsDataStoreJavaHelper::getEffectiveCpWatts` and `SettingsDataStoreJavaHelper::getEffectiveWPrimeKj`), register `mWPrimeBalSensor`, reset `mCurrentWPrimeBalJoules` to $W'$ on `resetSession()`, step $W'_{\text{bal}}$ every 1-Hz tick (recharging at $P = 0\text{ W}$ when paused), and publish `mWPrimeBalSensor` in kJ.
   - `TrackerService.java`: persist `SensorType.W_PRIME_BAL` (`MIN`, `AVG`, `END`) extrema during `finalizeLiveSession()`.
5. **Aftermath, Telemetry Chart & Legacy Import Layer**:
   - `LegacyImportEngine.kt`: compute and persist `SensorType.W_PRIME_BAL` (`MIN`, `AVG`, `END`) extrema in `recalculateStats()`.
   - `MapModels.kt` (`PathPoint`), `WorkoutRepository.kt`, and `TelemetryMetricGraph.kt` (`TelemetryMetricType.W_PRIME_BAL`): compute per-sample `wPrimeBalKj` from the workout's power stream and expose interactive $W'_{\text{bal}}$ (`kJ`) chart inspection.
   - `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutSummary.kt`, and `TrackOnMapScreen.kt`: expose and display `minWPrimeBalKj`, `minWPrimeBalPct`, and `isEmptyTankWarning` ($\min W'_{\text{bal},\%} < 10\%$).

---

## 2. UI Consistency (Rule 23 & `docs/design_guidelines.md` Section 5)
- **Reference Screens**: `AthleteProfileScreen.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, and `TelemetryMetricGraph.kt`.
- **Reused Components & Tokens**:
  - `AthleteProfileScreen.kt` reuses `MetricSummaryCard`, `PlotMetricPill` (Rule 38 compact single-line pills), `OutlinedCard`, and `MetricEntryDialog` with `MaterialTheme.colorScheme` tokens (`surfaceContainerLow`, `primary`, `error` for empty-tank badge).
  - `TelemetryMetricGraph.kt` adds `TelemetryMetricType.W_PRIME_BAL` using emerald green `Color(0xFF10B981)` (`TTColor` / Material palette) and `16.dp` / `8.dp` spacing scale.

---

## 3. Atomic Step Breakdown

| Step | Target Files | Description | Verification |
| :--- | :--- | :--- | :--- |
| **Step 1** | `PowerPhysiologyCalculator.kt` | Create pure Kotlin physiology calculator implementing 2-point CP/W' solver and Skiba's 1-Hz $W'_{\text{bal}}$ step & series engine. | `PowerPhysiologyCalculatorTest` |
| **Step 2** | `AthleteMetricsDatabaseManager.kt`, `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `AthleteMetricsRepository.kt`, `AthleteMetricsPlotMath.kt` | Add `CP_WATTS` and `W_PRIME_KJ` to `AthleteMetricType`, add DataStore keys/flows/helpers (`getEffectiveCpWatts`, `getEffectiveWPrimeKj`), sync in `AthleteMetricsRepository`, and add `PlotMetricType.CP`/`W_PRIME`. | `AthleteMetricsDtoAndMathTest`, `AthleteMetricsRepositorySyncTest` |
| **Step 3** | `AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `values*/strings.xml` | Expose CP and W' in `AthleteProfileUiState`, render summary cards, plot pills, and 2-point estimator in `MetricEntryDialog`, and add 9-locale string resources. | `AthleteProfileContractAndLocalizationTest` |
| **Step 4** | `SensorType.java`, `CyclingPowerMetricsDevice.java`, `TrackerService.java` | Add `SensorType.W_PRIME_BAL`, implement live 1-Hz $W'_{\text{bal}}$ tracking in `CyclingPowerMetricsDevice`, and persist `MIN`/`AVG`/`END` extrema in `TrackerService`. | `CyclingPowerMetricsDeviceTest`, `SensorCategoryAndSelectorContractTest` |
| **Step 5** | `LegacyImportEngine.kt`, `MapModels.kt`, `WorkoutRepository.kt`, `TelemetryMetricGraph.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt` | Persist `W_PRIME_BAL` extrema on import, populate `PathPoint.wPrimeBalKj` & `TelemetryMetricType.W_PRIME_BAL`, and surface `minWPrimeBalKj`/`minWPrimeBalPct`/`isEmptyTankWarning` in Aftermath. | `PowerPhysiologyCalculatorTest`, full unit test suite |

---

## 4. Invariants & Rollback Safety
- **Rule 4 Gate 3 Pre-Check**: `python3 tools/jira_util.py check-gate <Impl-Plan-Key>` MUST pass before any file in `app/src/...` is created or modified.
- **Sensor Contract Lockstep**: Updating `SensorType.java` (`43` $\to$ `44` sensors; `POWER_AND_PEDALING` `13` $\to$ `14`) MUST be accompanied by updating `SensorCategoryAndSelectorContractTest.kt`.
- **9-Locale Parity**: All new string keys MUST be present in all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
