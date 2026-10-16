# Stage 5 Walkthrough & Verification Report: ATT-3862

**Parent Ticket**: `ATT-3862` — Support Critical Power (CP), W' (Anaerobic Work Capacity), and W'bal (Anaerobic Battery) in Athlete Profile, Live Cockpit, and Aftermath
**Requirement ID**: `REQ-UI-399` (`REQ-UI-399.1` – `REQ-UI-399.4`)
**Test ID**: `TST-UI-362`
**Stage 5 Sub-Task**: `ATT-3893`
**Sprint Branch**: `sprint/2026-42.7`

---

## 1. Executive Summary of Delivered Architecture

1. **Canonical Physiology Calculator (`PowerPhysiologyCalculator.kt` — Rule 36 Single Source of Truth)**:
   - Implemented the 2-point linear work-time model solver `estimateCpAndWPrime(p1Watts, t1Seconds, p2Watts, t2Seconds)` solving $W_i = P_i \cdot t_i = CP \cdot t_i + W'$, validating physiological bounds ($CP \in [50, 1000]\text{ W}$, $W' \in [1.0, 100.0]\text{ kJ}$).
   - Implemented Skiba's 1-Hz differential $W'_{\text{bal}}$ algorithm (`stepWPrimeBalanceJoules`, `calculateSkibaTauSeconds`, `calculateWPrimeBalancePct`, `computeWPrimeBalanceSeries`):
     - Above $CP$ ($P(t) > CP$): linear depletion $W'_{\text{bal}}(t) = \max(0, W'_{\text{bal}}(t - \Delta t) - (P(t) - CP)\Delta t)$.
     - At or below $CP$ ($P(t) \le CP$): exponential reconstitution with dynamic recovery time constant $\tau_{W'}(t) = 546 \cdot e^{-0.01(CP - P(t))} + 316$.
     - Raises `isEmptyTankWarning` when minimum $W'_{\text{bal}}$ drops below $10.0\%$ of $W'$.
2. **Athlete Profile & Unidirectional DataStore Synchronization (`REQ-UI-399.1`)**:
   - Extended `AthleteMetricType` with `CP_WATTS` (`50.0..1000.0`) and `W_PRIME_KJ` (`5.0..60.0`).
   - Added `cpWattsFlow`, `wPrimeKjFlow`, `setCpWatts`, `setWPrimeKj` in `SettingsDataStore.kt` and synchronous bridges `getCpWatts`, `getEffectiveCpWatts` (falling back to FTP when CP is unconfigured), `getWPrimeKj`, and `getEffectiveWPrimeKj` (defaulting to `20.0f` kJ) in `SettingsDataStoreJavaHelper.kt`.
   - Extended `AthleteMetricsRepository.syncLatestToDataStoreLocked()`, `AthleteMetricsPlotMath.kt` (`PlotMetricType.CP`, `PlotMetricType.W_PRIME`), `AthleteProfileViewModel.kt`, and `AthleteProfileScreen.kt` (3x2 summary cards, plot pills, history filter pills, and interactive 2-point CP & W' estimator in `AthleteMetricEntryDialog`).
3. **Live Cockpit `W_PRIME_BAL` Sensor & Session Persistence (`REQ-UI-399.2`, `REQ-UI-399.3`)**:
   - Added `SensorType.W_PRIME_BAL` (`SensorCategory.POWER_AND_PEDALING`, `44` total sensors).
   - Updated `CyclingPowerMetricsDevice.java` to step and publish 1-Hz `W_PRIME_BAL` in kJ, reconstituting exponentially at $0\text{ W}$ during pauses and resetting on `resetSession()`.
   - Updated `LiveWorkoutSession.java` and `TrackerService.java` (`IMPORTANT_SENSOR_TYPES`, `SENSORS_TO_TRACK`, and `ExtremaType.END` persistence).
4. **Aftermath Summaries, Details, Interactive Telemetry Chart & File Imports (`REQ-UI-399.3`)**:
   - Updated `LegacyImportEngine.kt` (`recalculateStats()`) to compute and persist `W_PRIME_BAL` (`MIN`, `AVG`, `END`) extrema from `POWER` samples.
   - Updated `WorkoutData.kt` and `WorkoutDataMapper.kt` (`resolveWPrimeBalSummary` in both `fromCursor` and `fromSnapshot` per Rule 20), `WorkoutDetailsData.kt`, `WorkoutDetails.kt` (`workout_w_prime_bal_min_format` + `workout_w_prime_empty_tank` badge), `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `MapDetailLayout.kt`, `MapModels.kt` (`PathPoint.wPrimeBalKj`), `WorkoutRepository.kt` (`enrichPointsWithWPrimeBal`), `TelemetryMetricGraph.kt` (`TelemetryMetricType.W_PRIME_BAL`), and `TelemetryMetricUtils.kt`.
5. **9-Language Localization Parity (`REQ-UI-399.4`)**:
   - Added all 12 localized strings across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, and `values-pt/strings.xml`.

---

## 2. Verification & Clean-Room Regression Results

| Test Suite / Check | Scope | Result |
| :--- | :--- | :---: |
| `PowerPhysiologyCalculatorTest` | 2-point CP/W' solver, Skiba $\tau_{W'}$, 1-Hz depletion/reconstitution, clamping at $0\text{ J}$, empty-tank warning ($<10\%$) | **PASS** |
| `CyclingPowerMetricsDeviceTest` | Live 1-Hz `W_PRIME_BAL` depletion, CP$\to$FTP fallback, pause reconstitution at $0\text{ W}$, `resetSession()` | **PASS** |
| `SensorCategoryAndSelectorContractTest` | Exhaustive 44-sensor `SensorCategory` partition (`14` in `POWER_AND_PEDALING`) | **PASS** |
| `AthleteMetricsDtoAndMathTest` & `AthleteMetricsRepositorySyncTest` | `CP_WATTS` & `W_PRIME_KJ` validation bounds, `PlotMetricType` filtering/formatting, and DataStore projection | **PASS** |
| `AthleteProfileContractAndLocalizationTest` | 9-locale parity and positional format specifiers (`%1$.1f`, `%2$.0f`) | **PASS** |
| `WorkoutDataMappingSymmetryTest` | Rule 20 symmetry for `minWPrimeBalKj`, `minWPrimeBalPct`, `isEmptyTankWarning` across `fromCursor` and `fromSnapshot` | **PASS** |
| Full Clean-Room Regression (`./gradlew testDebugUnitTest`) | Complete unit test suite across all modules | **PASS (100%)** |

---

## 3. Traceability Matrix Synchronization

- `docs/requirements.md`: `REQ-UI-399` updated from `Planned` to `Verified`.
- `docs/tests.md`: `TST-UI-362` updated from `Planned` to `Verified`.
