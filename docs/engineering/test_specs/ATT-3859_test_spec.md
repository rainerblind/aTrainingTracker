# Stage 2 Requirement & Test Specification: `ATT-3859` — Add Performance Management Chart (PMC) for Fitness (CTL), Fatigue (ATL), and Form / Race-Readiness (TSB)

## 1. Formal Requirement Specification (`REQ-UI-401`)
- **ID**: `REQ-UI-401`
- **Title**: **Performance Management Chart (PMC) for Fitness (CTL), Fatigue (ATL), Form / Race-Readiness (TSB), Workload Ratio (ACWR), and Weekly Ramp Rate**
- **Specification**:
  The system SHALL compute and display a longitudinal Performance Management Chart (PMC) modeling Chronic Training Load ($\text{CTL}$), Acute Training Load ($\text{ATL}$), Training Stress Balance ($\text{TSB}$), Acute:Chronic Workload Ratio ($\text{ACWR}$), and 7-Day Fitness Ramp Rate ($\Delta \text{CTL} / 7\text{d}$) with multi-sport Heart-Rate fallback ($\text{hrTSS}$ / Banister $\text{TRIMP}$) across `PowerPhysiologyCalculator`, `WorkoutSummariesDatabaseManager`, `AthleteProfileViewModel`, and `AthleteProfileScreen` (`ATT-3859`, extending `REQ-UI-391`, `REQ-TRK-021`, and `REQ-MIG-039`):
  1. *Canonical Impulse-Response PMC & Multi-Sport $\text{hrTSS}$ Domain Engine (`REQ-UI-401.1` — Rule 36)*:
     - `PowerPhysiologyCalculator.kt` SHALL define:
       - `TsbReadinessZone`: `FRESH_RACE_READY` ($\text{TSB} \ge +5.0$), `NEUTRAL` ($-10.0 \le \text{TSB} < +5.0$), `OPTIMAL_OVERLOAD` ($-30.0 \le \text{TSB} < -10.0$), and `HIGH_FATIGUE_RISK` ($\text{TSB} < -30.0$).
       - `AcwrZone`: `UNDER_TRAINING` ($\text{ACWR} < 0.80$), `SWEET_SPOT` ($0.80 \le \text{ACWR} \le 1.30$), `HIGH_LOAD_CAUTION` ($1.30 < \text{ACWR} \le 1.50$), and `DANGER_ZONE` ($\text{ACWR} > 1.50$).
       - `WorkoutTrainingLoadSample(timestampMs, activeTimeSeconds, powerTss, normalizedPowerWatts, avgPowerWatts, avgHrBpm)`.
       - `PmcDailyPoint(dayEpochIndex, dayStartTimestampMs, dailyTss, ctl, atl, tsb, acwr, rampRate7d, tsbZone, acwrZone, isAggressiveRampUp)`.
       - `PmcSummaryResult(dailyPoints, currentCtl, currentAtl, currentTsb, currentAcwr, currentRampRate7d, currentTsbZone, currentAcwrZone, isAggressiveRampUp)`.
       - `calculateHrTss(activeTimeSeconds, avgHrBpm, hrRestBpm, hrMaxBpm)`: Estimates training stress for non-power workouts with valid HR using Banister's $\text{TRIMP}$ normalized to 1 hour at Lactate Threshold ($HR_{r,\text{thr}} = 0.85$): $HR_r = \text{clamp}\left(\frac{HR_{\text{avg}} - HR_{\text{rest}}}{HR_{\text{max}} - HR_{\text{rest}}}, 0.0, 1.0\right)$ and $\text{hrTSS} = 100.0 \cdot \frac{(T_{\text{active}} / 60.0) \cdot HR_r \cdot e^{1.92 \cdot HR_r}}{60.0 \cdot 0.85 \cdot e^{1.92 \cdot 0.85}}$.
       - `resolveWorkoutTrainingLoad(...)`: Resolves workout training stress using a 3-tier hierarchy: (1) stored `powerTss`, (2) power-derived `TSS` from `normalizedPowerWatts` (or `avgPowerWatts`) + `activeTimeSeconds` + `ftpWatts`, or (3) `hrTSS` from `avgHrBpm` + `activeTimeSeconds` + `hrRestBpm` / `hrMaxBpm`.
       - `computePerformanceManagementChart(...)`: Aggregates daily training stress $TSS_t$ across calendar days ($t = \lfloor \text{timestampMs} / 86{,}400{,}000 \rfloor$, with $TSS_t = 0.0$ on rest days) through `nowMs`, evaluating:
         - $\text{CTL}_t = \text{CTL}_{t-1} + \frac{TSS_t - \text{CTL}_{t-1}}{42.0}$ ($\tau_{\text{CTL}} = 42\text{ days}$)
         - $\text{ATL}_t = \text{ATL}_{t-1} + \frac{TSS_t - \text{ATL}_{t-1}}{7.0}$ ($\tau_{\text{ATL}} = 7\text{ days}$)
         - $\text{TSB}_t = \text{CTL}_{t-1} - \text{ATL}_{t-1}$
         - $\text{ACWR}_t = \text{ATL}_t / \text{CTL}_t$ (when $\text{CTL}_t > 0$)
         - $\text{RampRate}_t = \text{CTL}_t - \text{CTL}_{t-7}$ (flagging `isAggressiveRampUp = rampRate7d > 8.0`).
  2. *Batch Workout Load Retrieval (`REQ-UI-401.2`)*:
     - `WorkoutSummariesDatabaseManager.java` SHALL provide `getWorkoutTrainingLoadRecords()` returning a list of `WorkoutTrainingLoadSample` records for all finished workouts (`FINISHED = 1`), extracting `strftime('%s', timeStart) * 1000`, `timeActive_s`, and `ExtremumValues` for `TSS`, `NORMALIZED_POWER`, `POWER`, and `HR`.
  3. *Athlete Profile PMC Summary Cards & Interactive Multi-Curve Chart (`REQ-UI-401.3` — Rule 23 & Rule 38)*:
     - `AthleteProfileUiState` and `AthleteProfileViewModel` SHALL expose `pmcSummary: PmcSummaryResult` and `pmcPlotPoints: List<PmcDailyPoint>` filtered by `selectedTimeRange`.
     - `AthleteProfileScreen.kt` SHALL render `PerformanceManagementChartCard` displaying current status summary badges for **Fitness (CTL)**, **Fatigue (ATL)**, **Form (TSB)** (with `TsbReadinessZone` badge), **Workload Ratio (ACWR)** (with `AcwrZone` badge), and **7d Ramp Rate** (`%1$+.1f CTL/wk` with `isAggressiveRampUp` warning badge), together with a multi-line `PerformanceManagementChartCanvas` plotting `CTL` (blue area/line), `ATL` (magenta/purple line), and `TSB` (form curve).
  4. *9-Language Localization Parity (`REQ-UI-401.4`)*:
     - All 17 `athlete_profile_pmc_*` string resources SHALL exist and be non-blank across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

## 2. Acceptance Criteria (Given-When-Then)
1. **Given** a daily training load sequence (e.g., $100\text{ TSS/day}$ for 42 days followed by rest days),
   **When** `PowerPhysiologyCalculator.computePerformanceManagementChart` is evaluated,
   **Then** $\text{CTL}_t$, $\text{ATL}_t$, $\text{TSB}_t = \text{CTL}_{t-1} - \text{ATL}_{t-1}$, $\text{ACWR}_t = \text{ATL}_t / \text{CTL}_t$, and $\text{RampRate}_t = \text{CTL}_t - \text{CTL}_{t-7}$ match the exact EWMA equations and classify `TsbReadinessZone`, `AcwrZone`, and `isAggressiveRampUp` accurately.
2. **Given** a workout without power data ($3600\text{ s}$ at threshold heart rate $HR_r = 0.85$),
   **When** `PowerPhysiologyCalculator.calculateHrTss` is evaluated,
   **Then** $\text{hrTSS} = 100.0$, matching 1 hour at FTP.
3. **Given** `AthleteProfileScreen.kt` and all 9 `values*/strings.xml` files,
   **When** audited by `AthleteProfileContractAndLocalizationTest`,
   **Then** `PerformanceManagementChartCard` and all 17 `athlete_profile_pmc_*` strings are present with matching format specifiers across all 9 locales.

## 3. Test Specification (`TST-UI-364`)
- **Test Suite ID**: `TST-UI-364`
- **Target Test Classes**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/physiology/PowerPhysiologyCalculatorTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`
- **Test Cases**:
  1. `calculateHrTss and resolveWorkoutTrainingLoad follow 3-tier power TSS, NP fallback, and hrTSS fallback`: Verifies 1 hour at $HR_r = 0.85$ yields $\text{hrTSS} = 100.0$, and `resolveWorkoutTrainingLoad` prioritizes `powerTss` -> power-derived `TSS` -> `hrTSS`.
  2. `computePerformanceManagementChart computes 42d CTL, 7d ATL, TSB, ACWR, 7d Ramp Rate, and zones`: Verifies EWMA progression, rest-day decay, readiness zones (`FRESH_RACE_READY`, `NEUTRAL`, `OPTIMAL_OVERLOAD`, `HIGH_FATIGUE_RISK`), ACWR zones (`UNDER_TRAINING`, `SWEET_SPOT`, `HIGH_LOAD_CAUTION`, `DANGER_ZONE`), and `isAggressiveRampUp` ($> 8.0\text{ CTL/wk}$).
  3. `all 9 locales contain Athlete Profile and PMC strings with matching positional format specifiers`: Verifies all 17 `athlete_profile_pmc_*` string keys across all 9 locales.

## 4. Traceability Matrix
| Requirement ID | Sub-clause | Test ID | Test Method / Verification Target |
| :--- | :--- | :--- | :--- |
| `REQ-UI-401` | `REQ-UI-401.1` | `TST-UI-364` | `PowerPhysiologyCalculatorTest` (`calculateHrTss`, `resolveWorkoutTrainingLoad`, `computePerformanceManagementChart`) |
| `REQ-UI-401` | `REQ-UI-401.2` | `TST-UI-364` | `PowerPhysiologyCalculatorTest` (`getWorkoutTrainingLoadRecords` contract) |
| `REQ-UI-401` | `REQ-UI-401.3` | `TST-UI-364` | `AthleteProfileContractAndLocalizationTest` (`PerformanceManagementChartCard` contract) |
| `REQ-UI-401` | `REQ-UI-401.4` | `TST-UI-364` | `AthleteProfileContractAndLocalizationTest` (9-locale parity audit) |
