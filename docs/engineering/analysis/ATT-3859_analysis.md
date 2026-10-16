# Stage 1 Analysis: `ATT-3859` — Add Performance Management Chart (PMC) for Fitness (CTL), Fatigue (ATL), and Form / Race-Readiness (TSB)

## 1. Executive Summary & Problem Statement
While individual workout Training Stress Score (`TSS`) is now computed and persisted for both live sessions and imported TCX/GPX/FIT files (`ATT-1291`, `ATT-3860`), the application lacks a longitudinal **Performance Management Chart (PMC)** that aggregates daily training stress ($TSS_t$) across calendar days to model:
1. **Chronic Training Load ($\text{CTL}_t$ — Fitness)**: 42-day exponentially weighted moving average ($\tau_{\text{CTL}} = 42\text{ days}$).
2. **Acute Training Load ($\text{ATL}_t$ — Fatigue / Tiredness)**: 7-day exponentially weighted moving average ($\tau_{\text{ATL}} = 7\text{ days}$).
3. **Training Stress Balance ($\text{TSB}_t$ — Form / Race-Readiness)**: $\text{TSB}_t = \text{CTL}_{t-1} - \text{ATL}_{t-1}$ with semantic readiness zones (`Fresh / Race-Ready`, `Neutral / Grey Zone`, `Optimal Overload`, `High Fatigue Risk`).
4. **Acute:Chronic Workload Ratio ($\text{ACWR}_t$ — Normalized Workload Ratio, Gabbett)**: $\text{ACWR}_t = \text{ATL}_t / \text{CTL}_t$ (when $\text{CTL}_t > 0$) with workload zones (`Sweet Spot 0.80–1.30`, `Under-training < 0.80`, `High Load 1.30–1.50`, `Danger Zone > 1.50`).
5. **Weekly Fitness Ramp Rate ($\Delta \text{CTL} / 7\text{d}$)**: $\text{RampRate}_t = \text{CTL}_t - \text{CTL}_{t-7}$, flagging aggressive ramp-up when $> +8.0\text{ CTL/week}$.
6. **Multi-Sport Heart-Rate Fallback ($\text{hrTSS}$ / Banister $\text{TRIMP}$)**: Estimation of training stress for running or cycling workouts without a power meter when heart rate telemetry (`HR` average and active duration) is present, ensuring continuous 42-day CTL and 7-day ATL curves across all sports.

## 2. Forensic Root Cause & Gap Analysis
1. **Pure Domain PMC & $\text{hrTSS}$ Calculator (`PowerPhysiologyCalculator.kt`)**:
   - `PowerPhysiologyCalculator` needs stateless, pure-Kotlin domain functions and data models:
     - `TsbReadinessZone`:
       - `FRESH_RACE_READY` ($\text{TSB} \ge +5.0$, including $+5\text{ to }+25$)
       - `NEUTRAL` ($-10.0 \le \text{TSB} < +5.0$)
       - `OPTIMAL_OVERLOAD` ($-30.0 \le \text{TSB} < -10.0$)
       - `HIGH_FATIGUE_RISK` ($\text{TSB} < -30.0$)
     - `AcwrZone`:
       - `UNDER_TRAINING` ($\text{ACWR} < 0.80$)
       - `SWEET_SPOT` ($0.80 \le \text{ACWR} \le 1.30$)
       - `HIGH_LOAD_CAUTION` ($1.30 < \text{ACWR} \le 1.50$)
       - `DANGER_ZONE` ($\text{ACWR} > 1.50$)
     - `WorkoutTrainingLoadSample`: Input record representing a workout (`timestampMs`, `activeTimeSeconds`, `powerTss`, `normalizedPowerWatts`, `avgPowerWatts`, `avgHrBpm`).
     - `PmcDailyPoint`: Daily PMC point (`dayEpochIndex`, `dayStartTimestampMs`, `dailyTss`, `ctl`, `atl`, `tsb`, `acwr`, `rampRate7d`, `tsbZone`, `acwrZone`, `isAggressiveRampUp`).
     - `PmcSummaryResult`: Complete PMC series (`dailyPoints`, `currentCtl`, `currentAtl`, `currentTsb`, `currentAcwr`, `currentRampRate7d`, `currentTsbZone`, `currentAcwrZone`, `isAggressiveRampUp`).
     - `calculateHrTss(activeTimeSeconds, avgHrBpm, hrRestBpm, hrMaxBpm)`: Computes Banister $\text{TRIMP}$ normalized to 1 hour at Lactate Threshold ($HR_{r,\text{thr}} = 0.85$), returning $\text{hrTSS} = 100.0 \cdot \frac{t_{\text{min}} \cdot HR_r \cdot e^{1.92 \cdot HR_r}}{60.0 \cdot 0.85 \cdot e^{1.92 \cdot 0.85}}$.
     - `resolveWorkoutTrainingLoad(...)`: Resolves a workout's effective training stress score using a 3-tier hierarchy: (1) stored `powerTss`, (2) power-derived `TSS` from `NP` (or `avgPower`) + `activeTimeSeconds` + `ftpWatts`, or (3) `hrTSS` from `avgHrBpm` + `activeTimeSeconds` + `hrRestBpm` / `hrMaxBpm`.
     - `computePerformanceManagementChart(...)`: Aggregates workout loads by UTC calendar day ($t = \lfloor \text{timestampMs} / 86{,}400{,}000 \rfloor$), fills rest days with $TSS_t = 0.0$ through `nowMs`, and iteratively evaluates $\text{CTL}_t = \text{CTL}_{t-1} + \frac{TSS_t - \text{CTL}_{t-1}}{42.0}$, $\text{ATL}_t = \text{ATL}_{t-1} + \frac{TSS_t - \text{ATL}_{t-1}}{7.0}$, $\text{TSB}_t = \text{CTL}_{t-1} - \text{ATL}_{t-1}$, $\text{ACWR}_t = \text{ATL}_t / \text{CTL}_t$, and $\text{RampRate}_t = \text{CTL}_t - \text{CTL}_{t-7}$.
2. **Database Batch Workout Load Query (`WorkoutSummariesDatabaseManager.java`)**:
   - Add `getWorkoutTrainingLoadRecords()` to `WorkoutSummariesDatabaseManager` to fetch all finished workouts in a single efficient SQL query joining `WorkoutSummaries` (`timeStart`, `timeActive_s`) with `ExtremumValues` (`TSS`, `NORMALIZED_POWER`, `POWER`, `HR`).
3. **Athlete Profile UI & ViewModel Integration (`AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`)**:
   - Expose `pmcSummary: PmcSummaryResult` and filtered `pmcPlotPoints: List<PmcDailyPoint>` (respecting `selectedTimeRange`) in `AthleteProfileUiState`.
   - Render a dedicated **Performance Management (CTL / ATL / TSB)** card on the Athlete Profile tab (`AthleteProfileScreen.kt`) displaying:
     - Current status badges for **Fitness (CTL)**, **Fatigue (ATL)**, **Form (TSB)** with readiness zone badge, **Workload Ratio (ACWR)** with zone badge, and **7d Ramp Rate** (`CTL/wk`) with aggressive ramp warning when $> +8.0$.
     - Interactive multi-line Canvas chart (`PerformanceManagementChartCanvas`) plotting the `CTL` (blue area/line), `ATL` (magenta/purple line), and `TSB` (amber/green/red form curve with zero reference line) across the selected time range (`1M`, `3M`, `6M`, `1Y`, `All`).

## 3. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-401`, extending `REQ-UI-391` (*Athlete Profile Screen*, `ATT-3704`), `REQ-TRK-021` (*Mechanical Work & TSS*, `ATT-1291`), and `REQ-MIG-039` (*Imported Power Metrics Persistence*, `ATT-3860`).
   - Targets `PowerPhysiologyCalculator.kt`, `WorkoutSummariesDatabaseManager.java`, `AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, and `values*/strings.xml`.
2. **Historical Origin & Commit Trace**:
   - `ATT-3704` introduced `AthleteProfileScreen.kt` and `AthleteMetricsPlotMath.kt`.
   - `ATT-3860` (`e5d9225e`) added `PowerTrainingMetricsResult` and persisted `SensorType.TSS` into `WorkoutSummariesDatabaseManager.TABLE_EXTREMA_VALUES`.
3. **Root Reason for Existing Formulation**:
   - `AthleteProfileScreen.kt` initially plotted only discrete athlete baseline measurements (`WEIGHT`, `HR_REST`, `FTP`, `CP`, `W_PRIME`, `POWER_TO_WEIGHT`) stored in `AthleteMetrics.db`, without querying longitudinal workout training stress from `WorkoutSummaries.db`.
4. **Preservation of Core Invariants**:
   - Existing 4-tab `Profile & Zones` layout, `WindowInsets.navigationBars` bottom padding (Rule 41), and single-metric history plot in `AthleteProfileScreen.kt` remain 100% intact.
   - Zero database schema migrations required.

## 4. User Scope Grounding (`ATT-1250`)
- **In Scope**:
  - Pure domain PMC (`CTL`, `ATL`, `TSB`, `ACWR`, `7d Ramp Rate`, readiness/workload zones) and `hrTSS` fallback in `PowerPhysiologyCalculator.kt`.
  - Single-query workout training load retrieval in `WorkoutSummariesDatabaseManager.java`.
  - PMC status summary cards/badges and multi-curve time-series chart in `AthleteProfileViewModel.kt` and `AthleteProfileScreen.kt`.
  - 9-language localization parity across `values*/strings.xml`.
- **Out of Scope**:
  - Displaying session-level `NP`, `IF`, `TSS`, `Work kJ`, `W/kg`, and `VI` in `WorkoutSummary.kt` and `TrackOnMapScreen.kt` (handled next in `ATT-3858`).
