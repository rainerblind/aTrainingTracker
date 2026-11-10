# Stage 3: Implementation Plan - ATT-3906: Cardio-Power Longitudinal Fitness Trends in Athlete Profile

**Ticket**: [ATT-3906](https://rainerblind.atlassian.net/browse/ATT-3906)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Cardio-Power Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3906`  
**Requirement Mapping**: `REQ-REV-011` | **Test Mapping**: `TST-REV-012`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background

Outdoor 1st-order dynamical Power-HR parameters identified in fresh windows track long-term aerobic conditioning (submaximal aerobic economy $\text{HR@200W}$, cardiorespiratory lag constant $\tau$, and stroke-volume slope $1/K$).
`ATT-3906` introduces a dedicated `CardioPowerTrendsCard` in `AthleteProfileScreen.kt`, driven by `AthleteProfileViewModel.kt` and `WorkoutSummariesDatabaseManager.java`, displaying interactive scatter points, 30-day rolling trend curve, Rule 48 explicit axis scale markers, and outlier-resilient Y-axis domain clamping.

---

## 2. System Invariants & Preserved Behavior

1. **Mandatory Axis Scale Labels & Outlier-Resilient Y-Scaling (Rule 48 / Design Guidelines §2.6)**: Explicit X-axis date labels and Y-axis scale markers; Tukey IQR domain clamping ($[Q_1 - 1.5\cdot\text{IQR}, Q_3 + 1.5\cdot\text{IQR}]$) when $N \ge 4$ to prevent single anomalous workouts from flattening the seasonal trend.
2. **Parallel Sprint Isolation Invariant**: Changes are strictly restricted to `AthleteProfileScreen.kt` and `AthleteProfileViewModel.kt`. Do NOT touch `PeriodsScreen.kt` (0% overlap with `ATT-3556`).
3. **Quality Filtering & Gating**: Only rides with $R^2 \ge 0.50$ are plotted; card renders only when `enableCardioPowerDynamics == true`. Clean empty state when no qualifying rides exist.
4. **9-Locale Parity**: Domain-scoped tokens in `strings_cardio_power.xml` across all 9 locales (zero inline emojis).

---

## 3. Proposed Architectural Changes (SWE.2)

* **Data Layer**: `WorkoutSummariesDatabaseManager.getCardioPowerDynamicsFitnessTrendSamples(minR2 = 0.50)` querying `cardio_power_dynamics` joined with `WorkoutSummaries` ordered by `start_ms ASC`.
* **Math & Domain**: `CardioPowerTrendsPlotMath.kt` with pure functions for metric point conversion, 30-day rolling trend smoothing, and IQR domain clamping.
* **ViewModel**: `AthleteProfileViewModel.kt` exposes `cardioTrendPoints`, `cardioTrendRollingPoints`, `selectedCardioTrendMetric`, and `selectedCardioTimeRange`.
* **Presentation**: `CardioPowerTrendsCard.kt` in `ui/athleteprofile/` embedded into `AthleteProfileScreen.kt` with selector pills, semantic direction subtitles, `(i)` info action, and Light/Dark/AMOLED previews.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Time-Series Query (`REQ-REV-011.1`, `TST-REV-012.1`)
* **Files**: `WorkoutSummariesDatabaseManager.java`, `CardioPowerDynamicsRecord.kt`, `WorkoutSummariesCardioPowerTrendTest.kt`.
* **Details**: Add `getCardioPowerDynamicsFitnessTrendSamples(minR2 = 0.50)` returning time-stamped dynamical samples sorted by `start_ms ASC`.

### Step 2: Pure Math & Trend Calculations (`REQ-REV-011.3`, `TST-REV-012.3`)
* **Files**: `CardioPowerTrendsPlotMath.kt`, `CardioPowerTrendsPlotMathTest.kt`.
* **Details**: Implement point transformation, IQR domain clamping, 30-day rolling trend, and Rule 48 tick value formatters.

### Step 3: ViewModel Reactive State & Gating (`REQ-REV-011.2`, `TST-REV-012.2`)
* **Files**: `AthleteProfileViewModel.kt`, `AthleteProfileViewModelCardioPowerTest.kt`.
* **Details**: Integrate `CardioTrendMetric`, `MetricsTimeRange`, and reactive filtering gated by `enableCardioPowerDynamics`.

### Step 4: Cardio-Power Trends Card Composable (`REQ-REV-011.2`, `TST-REV-012.4`)
* **Files**: `CardioPowerTrendsCard.kt`, `AthleteProfileScreen.kt`, `CardioPowerTrendsCardTest.kt`.
* **Details**: Implement Canvas trend chart with Rule 48 markers, direction subtitles, selector pills, and info dialog trigger.

### Step 5: Verification & Contract Tests (`REQ-REV-011.4`, `TST-REV-012.5`)
* **Files**: `res/values*/strings_cardio_power.xml`, targeted unit tests.
* **Details**: Run targeted test suite:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*CardioPower*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
