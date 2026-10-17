# Stage 2 Requirement & Test Specification: ATT-3862 — Support Critical Power (CP), W' (Anaerobic Work Capacity), and W'bal (Anaerobic Battery) in Athlete Profile, Live Cockpit, and Aftermath

## 1. Formal Requirement Specification (`REQ-UI-399`)

### `REQ-UI-399`: Critical Power (CP), W' (Anaerobic Work Capacity), and W'bal (Anaerobic Battery) Across Athlete Profile, Live Cockpit, and Aftermath
The system SHALL support Critical Power ($CP$, in W), $W'$ (Anaerobic Work Capacity, in kJ), and Skiba's 1-Hz $W'_{\text{bal}}$ (Anaerobic Battery, in kJ and $\%$) across the Athlete Profile, Live Cockpit (`CyclingPowerMetricsDevice`), Aftermath Workout Summaries/Details/Telemetry Graph, and Legacy File Imports (`LegacyImportEngine`) (`ATT-3862`, extending `REQ-UI-391` and `REQ-TRK-021`):

1. **Athlete Profile Baselines, Unidirectional Sync, Fallback & 2-Point Estimator (`REQ-UI-399.1`)**:
   - `AthleteMetricType` in `AthleteMetricsDatabaseManager.kt` SHALL define `CP_WATTS("CP_WATTS", 50.0, 1000.0)` and `W_PRIME_KJ("W_PRIME_KJ", 5.0, 60.0)`.
   - `SettingsDataStore` and `SettingsDataStoreJavaHelper` SHALL persist `KEY_CYCLING_CP_WATTS` (`Int?`) and `KEY_CYCLING_W_PRIME_KJ` (`Float?`) and provide canonical fallback resolvers `getEffectiveCpWatts(context)` (returning `cpWatts` when $> 0$, else falling back to `getFtp(context)`) and `getEffectiveWPrimeKj(context)` (returning `wPrimeKj` when $> 0\text{f}$, else defaulting to `20.0f` kJ).
   - `AthleteMetricsRepository.syncLatestToDataStoreLocked()` SHALL synchronize the latest `CP_WATTS` and `W_PRIME_KJ` entries unidirectionally to `SettingsDataStore`.
   - `AthleteMetricsPlotMath` and `PowerPhysiologyCalculator` SHALL provide a 2-point work-time solver `estimateCpAndWPrime(p1Watts, t1Sec, p2Watts, t2Sec)` solving $W_i = P_i \cdot t_i = CP \cdot t_i + W'$, exposed in `AthleteProfileScreen` (`PlotMetricType.CP`, `PlotMetricType.W_PRIME`, summary cards, and a 2-point calculator helper inside `MetricEntryDialog`).

2. **Skiba 1-Hz $W'_{\text{bal}}$ Physiology Engine & Live Cockpit Sensor (`REQ-UI-399.2`)**:
   - `PowerPhysiologyCalculator` (`com.atrainingtracker.trainingtracker.physiology`) SHALL implement Skiba's differential $W'_{\text{bal}}$ algorithm (Rule 36 — DRY):
     - Depletion ($P(t) > CP$): $W'_{\text{bal}}(t) = \max\left(0,\; W'_{\text{bal}}(t-\Delta t) - (P(t)-CP)\Delta t\right)$
     - Reconstitution ($P(t) \le CP$): $\tau_{W'}(t) = 546 \cdot e^{-0.01(CP - P(t))} + 316$ and $W'_{\text{bal}}(t) = W' - (W' - W'_{\text{bal}}(t-\Delta t))e^{-\Delta t / \tau_{W'}(t)}$
     - Percentage: $W'_{\text{bal},\%}(t) = (W'_{\text{bal}}(t)/W') \times 100$.
   - `SensorType.java` SHALL define `W_PRIME_BAL(SensorCategory.POWER_AND_PEDALING, R.string.w_prime_bal, R.string.w_prime_bal_short, R.string.units_work_kj, SensorValueType.DOUBLE, new PowerToWeightFormatter(), false)` (total sensors = `44`, `POWER_AND_PEDALING` = `14`).
   - `CyclingPowerMetricsDevice.java` SHALL maintain live $W'_{\text{bal}}$ in Joules, initializing to $W'$ on `resetSession()`, stepping at 1 Hz (including exponential reconstitution at $P = 0\text{ W}$ when paused), and emitting `mWPrimeBalSensor` in kJ.

3. **Aftermath Summaries, Interactive Telemetry Chart & Import Recalculation (`REQ-UI-399.3`)**:
   - `TrackerService.finalizeLiveSession()` and `LegacyImportEngine.recalculateStats()` SHALL persist `SensorType.W_PRIME_BAL` (`MIN`, `AVG`, `END`) extrema.
   - `WorkoutData` and `WorkoutDataMapper` SHALL expose `minWPrimeBalKj: Double?`, `minWPrimeBalPct: Double?`, and `isEmptyTankWarning: Boolean` ($\min W'_{\text{bal},\%} < 10.0\%$), displayed in `WorkoutSummary.kt` and `TrackOnMapScreen.kt` when available.
   - `PathPoint` (`MapModels.kt`), `WorkoutRepository.loadTrackPoints()`, and `TelemetryMetricGraph.kt` (`TelemetryMetricType.W_PRIME_BAL`) SHALL support interactive $W'_{\text{bal}}$ (`kJ`) curve inspection.

4. **9-Language Localization Parity (`REQ-UI-399.4`)**:
   - All new string resources SHALL exist across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. Acceptance Criteria (Given-When-Then)
- **AC-1 (2-Point CP & W' Work-Time Solver)**:
  - *Given* two maximal efforts ($P_1 = 420\text{ W}$ for $t_1 = 180\text{ s}$ and $P_2 = 320\text{ W}$ for $t_2 = 720\text{ s}$),
  - *When* `PowerPhysiologyCalculator.estimateCpAndWPrime(420.0, 180, 320.0, 720)` is evaluated,
  - *Then* it returns $CP = 286.67\text{ W}$ (`287 W`) and $W' = 24.0\text{ kJ}$.
- **AC-2 (Skiba 1-Hz Depletion, Zero Floor & Exponential Reconstitution)**:
  - *Given* $CP = 300\text{ W}$ and $W' = 20.0\text{ kJ}$,
  - *When* an athlete rides at $400\text{ W}$ for $100\text{ s}$ and then recovers at $200\text{ W}$ for $100\text{ s}$,
  - *Then* $W'_{\text{bal}}$ depletes linearly to $10.0\text{ kJ}$ ($50\%$) and reconstitutes exponentially with $\tau_{W'} = 546 \cdot e^{-1.0} + 316 \approx 516.86\text{ s}$ to $\approx 11.76\text{ kJ}$, and never drops below $0.0\text{ kJ}$ even under prolonged supra-CP efforts.
- **AC-3 (Live Cockpit & Pause Reconstitution)**:
  - *Given* `CyclingPowerMetricsDevice` with $CP = 300\text{ W}$ and $W' = 20.0\text{ kJ}$,
  - *When* depleted to $10.0\text{ kJ}$ and the session is paused for $60\text{ s}$,
  - *Then* `W_PRIME_BAL` continues exponential reconstitution at $P = 0\text{ W}$ ($\tau_{W'} = 546 e^{-3.0} + 316 \approx 343.18\text{ s}$) during the pause and resets to $20.0\text{ kJ}$ upon `resetSession()`.
- **AC-4 (Aftermath & Empty-Tank Warning)**:
  - *Given* a workout whose minimum $W'_{\text{bal}}$ reaches $1.5\text{ kJ}$ out of $20.0\text{ kJ}$ ($7.5\% < 10\%$),
  - *When* mapped into `WorkoutData`,
  - *Then* `minWPrimeBalKj == 1.5`, `minWPrimeBalPct == 7.5`, and `isEmptyTankWarning == true`.

---

## 3. Formal Test Specification (`TST-UI-362`)

| Test ID | Target Test Class | Verification Scope |
| :--- | :--- | :--- |
| `TST-UI-362.1` | `PowerPhysiologyCalculatorTest.kt` | Pure JVM unit tests for 2-point CP/W' solver, Skiba 1-Hz $W'_{\text{bal}}$ depletion, zero-clamping, exponential $\tau_{W'}$ reconstitution, series calculation, and empty-tank warning ($<10\%$). |
| `TST-UI-362.2` | `AthleteMetricsDtoAndMathTest.kt` & `AthleteMetricsRepositorySyncTest.kt` | Bounds validation for `CP_WATTS` (`50..1000`) and `W_PRIME_KJ` (`5..60`), `PlotMetricType.CP`/`W_PRIME` series filtering, and unidirectional `SettingsDataStore` sync (`cpWattsFlow`, `wPrimeKjFlow`). |
| `TST-UI-362.3` | `CyclingPowerMetricsDeviceTest.kt` & `SensorCategoryAndSelectorContractTest.kt` | Live `W_PRIME_BAL` sensor emission on 1-Hz ticks, CP->FTP fallback, pause reconstitution, session reset, and 44-sensor (`14` in `POWER_AND_PEDALING`) contract parity. |
| `TST-UI-362.4` | `AthleteProfileContractAndLocalizationTest.kt` | 9-locale resource parity (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) for all new CP, W', and W'bal strings. |

---

## 4. Bidirectional Traceability Matrix

| Requirement ID | Sub-Clauses | Test ID | Target Production & Test Files |
| :--- | :--- | :--- | :--- |
| `REQ-UI-399` | `REQ-UI-399.1` – `REQ-UI-399.4` | `TST-UI-362` | `PowerPhysiologyCalculator.kt`, `AthleteMetricsDatabaseManager.kt`, `SettingsDataStore.kt`, `AthleteMetricsRepository.kt`, `AthleteMetricsPlotMath.kt`, `AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `SensorType.java`, `CyclingPowerMetricsDevice.java`, `TrackerService.java`, `LegacyImportEngine.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `TelemetryMetricGraph.kt`, `PowerPhysiologyCalculatorTest.kt`, `CyclingPowerMetricsDeviceTest.kt`, `AthleteMetricsDtoAndMathTest.kt`, `AthleteMetricsRepositorySyncTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`, `SensorCategoryAndSelectorContractTest.kt` |
