# Stage 3 Implementation Plan: `ATT-3859` — Add Performance Management Chart (PMC) for Fitness (CTL), Fatigue (ATL), and Form / Race-Readiness (TSB)

## 1. Architectural Overview (SWE.2)
To satisfy `REQ-UI-401` and `TST-UI-364` while adhering to Rule 36 (DRY Single Source of Truth), Rule 23 (Material 3 UI tokens & reusable components), Rule 38 (non-wrapping compact pills), and Rule 41 (navigation bar bottom inset clearance), the implementation is structured into 4 atomic steps:

1. **Step 1 — Canonical Impulse-Response PMC & Multi-Sport $\text{hrTSS}$ Domain Engine (`PowerPhysiologyCalculator.kt`)**:
   - Define `TsbReadinessZone` (`FRESH_RACE_READY`, `NEUTRAL`, `OPTIMAL_OVERLOAD`, `HIGH_FATIGUE_RISK`), `AcwrZone` (`UNDER_TRAINING`, `SWEET_SPOT`, `HIGH_LOAD_CAUTION`, `DANGER_ZONE`), `WorkoutTrainingLoadSample`, `PmcDailyPoint`, and `PmcSummaryResult`.
   - Implement `calculateHrTss(activeTimeSeconds, avgHrBpm, hrRestBpm, hrMaxBpm)` using Banister's $\text{TRIMP}$ normalized to 1 hour at Lactate Threshold ($HR_{r,\text{thr}} = 0.85$):
     $$\text{hrTSS} = 100.0 \cdot \frac{(T_{\text{active}} / 60.0) \cdot HR_r \cdot e^{1.92 \cdot HR_r}}{60.0 \cdot 0.85 \cdot e^{1.92 \cdot 0.85}}$$
   - Implement `resolveWorkoutTrainingLoad(...)` resolving a workout's daily training stress via 3 tiers:
     1. Stored `powerTss` (`> 0.0`)
     2. Power-derived `TSS` from `normalizedPowerWatts` (or `avgPowerWatts`), `activeTimeSeconds`, and `ftpWatts`
     3. Heart-rate-derived `hrTSS` from `avgHrBpm`, `activeTimeSeconds`, `hrRestBpm`, and `hrMaxBpm`.
   - Implement `computePerformanceManagementChart(...)` aggregating daily training load $TSS_t$ across calendar days ($t = \lfloor \text{timestampMs} / 86{,}400{,}000 \rfloor$, with $TSS_t = 0.0$ on rest days) through `nowMs` and computing:
     - $\text{CTL}_t = \text{CTL}_{t-1} + (TSS_t - \text{CTL}_{t-1}) / 42.0$
     - $\text{ATL}_t = \text{ATL}_{t-1} + (TSS_t - \text{ATL}_{t-1}) / 7.0$
     - $\text{TSB}_t = \text{CTL}_{t-1} - \text{ATL}_{t-1}$
     - $\text{ACWR}_t = \text{ATL}_t / \text{CTL}_t$ (when $\text{CTL}_t > 0$)
     - $\text{RampRate}_t = \text{CTL}_t - \text{CTL}_{t-7}$ (`isAggressiveRampUp = rampRate7d > 8.0`).
   - Add `filterPmcPointsByTimeRange(points, timeRange, nowMs)` in `AthleteMetricsPlotMath.kt`.

2. **Step 2 — Batch Workout Training Load Retrieval (`WorkoutSummariesDatabaseManager.java`)**:
   - Add `getWorkoutTrainingLoadRecords()` to `WorkoutSummariesDatabaseManager` to query all finished workouts (`FINISHED = 1`) in a single SQL query joining `WorkoutSummaries` (`timeStart`, `timeActive_s`) with `ExtremumValues` (`TSS`, `NORMALIZED_POWER`, `POWER`, `HR`).

3. **Step 3 — Athlete Profile ViewModel, UI Cards & Multi-Curve PMC Canvas (`AthleteProfileViewModel.kt`, `AthleteProfileScreen.kt`, `values*/strings.xml`)**:
   - Localize the 17 `athlete_profile_pmc_*` string keys across all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
   - Expose `pmcSummary: PmcSummaryResult` and `pmcPlotPoints: List<PmcDailyPoint>` on `AthleteProfileUiState` in `AthleteProfileViewModel.kt`.
   - Render `PerformanceManagementChartCard` and `PerformanceManagementChartCanvas` in `AthleteProfileScreen.kt` displaying current `CTL`, `ATL`, `TSB` (with readiness zone badge), `ACWR` (with workload zone badge), `7d Ramp Rate` (`%1$+.1f CTL/wk` with warning badge when $> +8.0$), and the multi-line `CTL` / `ATL` / `TSB` Canvas chart.

4. **Step 4 — Unit, Contract & 9-Language Parity Verification (`PowerPhysiologyCalculatorTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)**:
   - Add unit tests in `PowerPhysiologyCalculatorTest.kt` for `calculateHrTss`, `resolveWorkoutTrainingLoad`, and `computePerformanceManagementChart`.
   - Add contract and 9-locale verification in `AthleteProfileContractAndLocalizationTest.kt` for `PerformanceManagementChartCard` and all 17 `athlete_profile_pmc_*` string keys.

## 2. File-by-File Modification Matrix
| File | Action | Purpose |
| :--- | :--- | :--- |
| `app/src/main/java/com/atrainingtracker/trainingtracker/physiology/PowerPhysiologyCalculator.kt` | Modify | Add `TsbReadinessZone`, `AcwrZone`, `WorkoutTrainingLoadSample`, `PmcDailyPoint`, `PmcSummaryResult`, `calculateHrTss`, `resolveWorkoutTrainingLoad`, `computePerformanceManagementChart` |
| `app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsPlotMath.kt` | Modify | Add `filterPmcPointsByTimeRange` |
| `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java` | Modify | Add `getWorkoutTrainingLoadRecords()` |
| `app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileViewModel.kt` | Modify | Populate `pmcSummary` and `pmcPlotPoints` in `AthleteProfileUiState` |
| `app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileScreen.kt` | Modify | Add `PerformanceManagementChartCard` and `PerformanceManagementChartCanvas` |
| `app/src/main/res/values*/strings.xml` (9 locales) | Modify | Add 17 `athlete_profile_pmc_*` localized strings |
| `app/src/test/java/com/atrainingtracker/trainingtracker/physiology/PowerPhysiologyCalculatorTest.kt` | Modify | Add PMC and `hrTSS` unit tests (`TST-UI-364`) |
| `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt` | Modify | Add PMC UI contract and 9-locale parity tests (`TST-UI-364`) |

## 3. Invariants & Verification Strategy
- Zero SQLite schema changes.
- All existing `AthleteProfileScreen` tabs, `WindowInsets.navigationBars` bottom padding (Rule 41), and single-metric history plots remain 100% intact.
- Full clean-room unit test verification via `./gradlew testDebugUnitTest`.
