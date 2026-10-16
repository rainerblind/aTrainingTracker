# Stage 1 Analysis: ATT-3862 — Support Critical Power (CP), W' (Anaerobic Work Capacity), and W'bal (Anaerobic Battery) in Athlete Profile, Live Cockpit, and Aftermath

## 1. Problem Domain & Motivation
Modern cycling physiology models athlete performance using the two-parameter **Critical Power (CP)** and **$W'$ (W-Prime / Anaerobic Work Capacity)** framework alongside Skiba's **$W'_{\text{bal}}$ (Anaerobic Battery)** differential/integral expenditure-and-reconstitution algorithm:
- **Critical Power ($CP$, in W)**: The asymptotic sustainable aerobic power boundary. Above $CP$, the athlete draws from their finite anaerobic work capacity ($W'$); at or below $CP$, $W'_{\text{bal}}$ recharges exponentially.
- **$W'$ (in J or kJ, typically $15.0\text{–}30.0\text{ kJ}$)**: The finite anaerobic work capacity ("matchbook" or anaerobic battery) available above $CP$.
- **$W'_{\text{bal}}(t)$ (in kJ and $\%$)**: The remaining anaerobic battery at time $t$, governed by Skiba's 1-Hz differential formulation:
  - **Depletion ($P(t) > CP$)**:
    $$W'_{\text{bal}}(t) = \max\left(0,\; W'_{\text{bal}}(t - \Delta t) - (P(t) - CP) \cdot \Delta t\right)$$
  - **Exponential Reconstitution ($P(t) \le CP$)**:
    $$\tau_{W'}(t) = 546 \cdot e^{-0.01 \cdot (CP - P(t))} + 316$$
    $$W'_{\text{bal}}(t) = W' - \left(W' - W'_{\text{bal}}(t - \Delta t)\right) \cdot e^{-\Delta t / \tau_{W'}(t)}$$
  - **Remaining Battery Percentage**:
    $$W'_{\text{bal},\%}(t) = \frac{W'_{\text{bal}}(t)}{W'} \times 100$$

Currently, **aTrainingTracker** supports `FTP` in the Athlete Profile (`AthleteMetricType.FTP_WATTS`, `SettingsDataStore.KEY_CYCLING_FTP`) and live power metrics (`NP`, `IF`, `TSS`, `VI`, `POWER_TO_WEIGHT`) in `CyclingPowerMetricsDevice`, but lacks:
1. Historical & current **Critical Power ($CP$)** and **$W'$ (kJ)** entries in the Athlete Profile (`AthleteMetricsDatabaseManager`, `SettingsDataStore`, `AthleteProfileScreen`), fallback logic ($CP \to FTP$ when unconfigured; $W' \to 20.0\text{ kJ}$ default), and a 2-point work-time solver ($W_i = P_i \cdot t_i = CP \cdot t_i + W'$) to estimate $CP$ and $W'$ from two maximal effort durations (e.g., 3-min and 12-min MMP).
2. A live **`SensorType.W_PRIME_BAL`** sensor in `CyclingPowerMetricsDevice` that computes real-time $W'_{\text{bal}}$ (in kJ) at 1 Hz during active rides and recharges during rests/pauses.
3. Post-workout **Aftermath & Import** support for $W'_{\text{bal}}$ extrema (minimum $W'_{\text{bal}}$ in kJ and $\%$, empty-tank warning indicator when $\min W'_{\text{bal},\%} < 10\%$) and interactive $W'_{\text{bal}}$ telemetry chart inspection in `TrackOnMapScreen` / `TelemetryMetricGraph`.

---

## 2. Forensic Root Cause & Gap Analysis

### 2.1 Athlete Profile & Settings DataStore (`database/AthleteMetricsDatabaseManager.kt`, `settings/SettingsDataStore.kt`, `ui/athleteprofile/*`)
- `AthleteMetricType` currently enumerates `WEIGHT_KG`, `BODY_FAT_PCT`, `RESTING_HR_BPM`, `MAX_HR_BPM`, `FTP_WATTS`, `THRESHOLD_HR_BPM`, and `THRESHOLD_PACE_SEC_PER_KM`.
- Adding `CP_WATTS("CP_WATTS", 50.0, 1000.0)` and `W_PRIME_KJ("W_PRIME_KJ", 5.0, 60.0)` preserves the append-only time-series table schema (`athlete_metrics`) with zero schema migration needed.
- `SettingsDataStore` & `SettingsDataStoreJavaHelper` need `KEY_CYCLING_CP_WATTS` (`Int?`) and `KEY_CYCLING_W_PRIME_KJ` (`Float?`), plus canonical helper methods:
  - `getEffectiveCpWatts(context)`: returns `cpWatts` if configured ($> 0$), else falls back to `getFtp(context)` (defaulting to `200 W`).
  - `getEffectiveWPrimeKj(context)`: returns `wPrimeKj` if configured ($> 0\text{f}$), else defaults to `20.0f` kJ ($20,000\text{ J}$).
- `AthleteMetricsRepository.syncLatestToDataStoreLocked()` must synchronize `CP_WATTS` and `W_PRIME_KJ` to `SettingsDataStore`.
- `AthleteMetricsPlotMath` needs `PlotMetricType.CP` and `PlotMetricType.W_PRIME` plus a pure two-point work-time solver `estimateCpAndWPrime(p1Watts: Double, t1Seconds: Int, p2Watts: Double, t2Seconds: Int): CpWPrimeEstimate?` where:
  $$W_1 = P_1 \cdot t_1,\quad W_2 = P_2 \cdot t_2,\quad CP = \frac{W_2 - W_1}{t_2 - t_1},\quad W'_{\text{J}} = W_1 - CP \cdot t_1,\quad W'_{\text{kJ}} = \frac{W'_{\text{J}}}{1000.0}$$
- `AthleteProfileScreen` needs cards/filter pills for $CP$ (`W`) and $W'$ (`kJ`), and an optional 2-point calculator helper inside the metric entry dialog when logging $CP$ or $W'$.

### 2.2 Shared Pure Domain Physiology Engine (`physiology/PowerPhysiologyCalculator.kt`)
- In compliance with `docs/coding_standards.md` Section 1.1 (**Rule 36 — Single Source of Truth & DRY**), all power physiology formulas ($W'_{\text{bal}}$ step & series math, plus shared helpers for $NP$, $IF$, $TSS$, $VI$, $\text{Work (kJ)}$, $\text{W/kg}$, $EF$, and Aerobic Decoupling across the sprint's physiology tickets) should live in a pure, stateless JVM-testable utility `PowerPhysiologyCalculator` in `com.atrainingtracker.trainingtracker.physiology`.

### 2.3 Live Cockpit Sensor (`SensorType.java`, `CyclingPowerMetricsDevice.java`, `TrackerService.java`)
- `SensorType.java` defines all sensors in `POWER_AND_PEDALING`. Adding `W_PRIME_BAL(SensorCategory.POWER_AND_PEDALING, R.string.w_prime_bal, R.string.w_prime_bal_short, R.string.units_work_kj, SensorValueType.DOUBLE, new PowerToWeightFormatter(), false)` (using 1 decimal place `%.1f` for kJ) exposes $W'_{\text{bal}}$ across live cockpit grids, sensor selectors, and database sample/extrema tables.
- **Contract Test Lockstep**: `SensorCategoryAndSelectorContractTest.kt` asserts exact total sensor count (`43` $\to$ `44`) and `POWER_AND_PEDALING` count (`13` $\to$ `14`). It must be updated in lockstep.
- `CyclingPowerMetricsDevice.java` runs a 1-Hz `ScheduledExecutorService` tick (`onOneSecondTick()`). We can inject `CpProvider` and `WPrimeKjProvider` (defaulting to `SettingsDataStoreJavaHelper::getEffectiveCpWatts` and `SettingsDataStoreJavaHelper::getEffectiveWPrimeKj`), initialize $W'_{\text{bal}}$ to $W'$ on `resetSession()`, update $W'_{\text{bal}}$ every second via `PowerPhysiologyCalculator.stepWPrimeBalanceJoules(...)` (treating paused seconds as $P(t) = 0\text{ W}$ so $W'_{\text{bal}}$ continues recharging during rest stops!), and publish `mWPrimeBalSensor` in kJ.
- `TrackerService.finalizeLiveSession()` persists extrema for all active sensors (`AVG`, `MAX`, `MIN`, `END`).

### 2.4 Aftermath, Telemetry Chart & Legacy Import (`WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `TelemetryMetricGraph.kt`, `LegacyImportEngine.kt`)
- `PathPoint` (`MapModels.kt`) and `WorkoutRepository.loadTrackPoints()` can compute or carry `wPrimeBalKj: Float?` across the workout's power stream using `PowerPhysiologyCalculator`.
- `TelemetryMetricType` (`TelemetryMetricGraph.kt`) can include `W_PRIME_BAL` (`kJ`, emerald/amber/red battery-inspired color `#10B981`), allowing interactive scrubbing on the Aftermath telemetry graph.
- `WorkoutData` (`WorkoutData.kt`) and `WorkoutDataMapper.kt` can expose `minWPrimeBalKj: Double?`, `minWPrimeBalPct: Double?`, and `isEmptyTankWarning: Boolean` ($\min W'_{\text{bal},\%} < 10.0\%$), rendered in `WorkoutSummary.kt` and `TrackOnMapScreen.kt` when power data is present.
- `LegacyImportEngine.recalculateStats()` will also compute and persist `SensorType.W_PRIME_BAL` (`MIN`, `AVG`, `END`) from imported power streams so imported TCX/GPX/FIT workouts display $W'_{\text{bal}}$ extrema immediately.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)
- **Existing Requirements Inspected**:
  - `REQ-UI-393` (`ATT-3855`): Athlete Profile & Physiological Baselines (`WEIGHT_KG`, `FTP_WATTS`, etc.).
  - `REQ-TRK-022` / `REQ-UI-394` (`ATT-3856`): Live Power Metrics (`NP`, `IF`, `TSS`, `VI`, `POWER_TO_WEIGHT`) in `CyclingPowerMetricsDevice`.
- **Preservation of Core Invariants**:
  - Existing requirements are **not** modified or weakened; a new requirement (`REQ-UI-399`) and test specification (`TST-UI-362`) are added to extend the Athlete Profile, Live Cockpit, and Aftermath with $CP$, $W'$, and $W'_{\text{bal}}$.

---

## 4. User Scope Grounding (`ATT-1250`)
### In Scope
1. Athlete Profile support for **Critical Power ($CP$, W)** and **$W'$ (kJ)** with historical tracking, `SettingsDataStore` sync, fallback ($CP \to FTP$, $W' \to 20.0\text{ kJ}$), and a 2-point work-time $CP / W'$ estimator helper.
2. Pure Kotlin **`PowerPhysiologyCalculator`** implementing Skiba's 1-Hz $W'_{\text{bal}}$ depletion/reconstitution model, summary extrema ($\min W'_{\text{bal}}$ in kJ & $\%$, empty-tank detection $<10\%$), and 2-point $CP / W'$ estimation.
3. Live **`SensorType.W_PRIME_BAL`** in `CyclingPowerMetricsDevice` updating at 1 Hz (including exponential recharge during pauses/coasting) and persisting `MIN`/`AVG`/`END` extrema.
4. Aftermath & Import integration: $W'_{\text{bal}}$ extrema & empty-tank badge in Workout Summary / Details, `TelemetryMetricType.W_PRIME_BAL` in `TelemetryMetricGraph`, and `W_PRIME_BAL` extrema calculation during `LegacyImportEngine.recalculateStats()`.
5. 9-language localization parity across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-ko`, `values-pl`, and `values-zh-rCN`.

### Out of Scope
- Multi-point ($\ge 3$ points) non-linear regression power-duration curve fitting (2-point linear work-time model is standard and deterministic).
- Separate running-specific critical speed models.
