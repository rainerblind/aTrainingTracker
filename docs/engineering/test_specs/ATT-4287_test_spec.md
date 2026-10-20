# Stage 2: Requirement & Test Specification - ATT-4287: Add X/Y axis labels and outlier-resilient Y-scaling to Workout Cluster progression chart

**Ticket**: [ATT-4287](https://rainerblind.atlassian.net/browse/ATT-4287)  
**Sub-task**: [ATT-4331](https://rainerblind.atlassian.net/browse/ATT-4331) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters (Lieblingsstrecken)*)  
**Test Spec ID**: `TST-SET-078`  
**Branch**: `feature/ATT-4287`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-SET-078)

### 1.1 Problem Statement & Rationale
During the on-device Sprint Review of `ATT-4067` on a Google Pixel 10 inspecting the historical route cluster *"Zum Bäcker"* (comprising 43 recorded workouts), three critical usability and visualization deficiencies were identified in the progression trend chart (`WorkoutClusterTrendChartCard`):
1. **Missing Axis Markers and Scale Labels (X-Axis and Y-Axis)**: The canvas trend curve renders data points and a horizontal dashed average line (`Ø`), but completely lacks visible X-axis date/effort scale markers and Y-axis scale values (fastest/top, average baseline, and slowest/bottom). Athletes inspecting the chart cannot determine absolute performance values (e.g. was the best time 32 minutes or 45 minutes? was the speed 28 km/h or 35 km/h?) nor identify the chronological timeline span (earliest vs. latest session) without tapping individual points one by one.
2. **Severe Y-Axis Distortion & Compression from Extreme Outliers**: In real-world athletic logs, an athlete occasionally records an anomalous workout (e.g. an exploratory ride with an unpaused stop, or device left recording post-workout) yielding an extreme duration (e.g. 115:52 vs. normal cluster times of 32:00–38:00) or abnormally low average speed. Because `WorkoutClusterTrendMath` currently scales the vertical axis linearly across the raw un-clamped extrema (`minDuration..maxDuration`), a single slow session expands the denominator by a factor of 3 to 5. Consequently, all other 42 normal workouts are compressed into a flat, narrow band spanning only the top ~10–15% of the canvas height. This flattens meaningful day-to-day fitness progress and obscures the dashed average line (`Ø`).
3. **Dense Point Crowding for High Workout Counts ($N > 20$)**: For heavily used route clusters ($N = 43$), drawing static `4.dp` radius points (diameter 8 dp) with outer selection halos across a mobile viewport width (~300–360 dp) causes adjacent points to collide into a continuous, cluttered chain where individual efforts cannot be visually distinguished.

This requirement specification extends and refines `REQ-SET-078` pursuant to `docs/design_guidelines.md` Section 2.6 (Rule 48) to mandate explicit X/Y axis scale labels, outlier-resilient Y-scaling via Tukey interquartile range (IQR) fencing, and density-adaptive point sizing.

---

### 1.2 Functional & Architectural Requirements

1. **Statistical Outlier Fencing & Resilient Y-Scaling (`REQ-SET-078.6`)**:
   - When the count of valid cluster member workouts is $N \ge 4$, `WorkoutClusterTrendMath` SHALL calculate the vertical axis scaling domain using Tukey's Interquartile Range ($\text{IQR} = Q_3 - Q_1$).
   - $Q_1$ (25th percentile) and $Q_3$ (75th percentile) SHALL be computed via linear rank interpolation on sorted performance values (duration, pace, or speed).
   - When $\text{IQR} > 0$:
     - The lower fence SHALL evaluate to $L = Q_1 - 1.5 \times \text{IQR}$.
     - The upper fence SHALL evaluate to $U = Q_3 + 1.5 \times \text{IQR}$.
     - The effective scale domain $[\text{scaleMin}, \text{scaleMax}]$ SHALL clamp against $[\max(\minVal, L), \min(\maxVal, U)]$.
   - Any data point whose metric value lies outside $[\text{scaleMin}, \text{scaleMax}]$ SHALL be clamped to the canvas vertical boundary ($\text{normY} = 0.0f$ or $1.0f$) and SHALL have `isClampedOutlier = true`.
   - When $N < 4$ or $\text{IQR} == 0$, the scaling engine SHALL fall back safely to raw $\min..\max$ scaling with `isClampedOutlier = false`.
   - The cluster average value (`avgNormalizedY`) SHALL be mapped against $[\text{scaleMin}, \text{scaleMax}]$ and clamped to $[0.0f, 1.0f]$.

2. **Explicit Y-Axis & X-Axis Scale Markers (`REQ-SET-078.7`)**:
   - `ClusterTrendSeries` SHALL expose formatted scale boundary strings:
     - `yAxisTopLabel`: Formatted value corresponding to $\text{normY} = 1.0f$ (fastest duration / fastest pace / highest speed).
     - `yAxisBottomLabel`: Formatted value corresponding to $\text{normY} = 0.0f$ (slowest duration / slowest pace / lowest speed).
     - `xAxisStartLabel`: Formatted short date of the earliest member workout in the series.
     - `xAxisEndLabel`: Formatted short date of the latest member workout in the series.
   - `WorkoutClusterTrendChartCard` SHALL render these scale markers on the chart canvas with clear visual hierarchy and high contrast:
     - Top Y-axis label aligned with the top gridline level ($\text{normY} = 1.0f$).
     - Bottom Y-axis label aligned with the bottom gridline level ($\text{normY} = 0.0f$).
     - Average Y-axis label displaying formatted average value paired with `Ø` (`cluster_trend_avg`) aligned with the dashed average line.
     - X-axis boundary labels displaying `xAxisStartLabel` (left-aligned) and `xAxisEndLabel` (right-aligned) along the bottom axis baseline.

3. **Density-Adaptive Point Sizing & Outlier Representation (`REQ-SET-078.8`)**:
   - To prevent visual crowding on clusters with large workout counts ($N > 20$):
     - For $N \le 20$: standard non-PR point radius SHALL be $4\text{ dp}$ (inner dot $1.8\text{ dp}$).
     - For $N > 20$: standard non-PR point radius SHALL adaptively reduce to $2\text{ dp}$ (inner dot $1.0\text{ dp}$).
   - PR efforts SHALL retain prominent gold decoration ($6\text{ dp}$, `TTColor.Gold`).
   - Selected effort SHALL retain full highlight halo ($5\text{ dp}$ inner dot with $9\text{ dp}$ outer ring).
   - Data points with `isClampedOutlier = true` SHALL be visually differentiated at the canvas boundary with an outlier glyph / ring styling.
   - When an outlier point is selected, the inspector callout card SHALL display a distinct outlier indicator badge labeled with `R.string.cluster_trend_outlier`.

4. **9-Language Localization Parity (`REQ-SET-078.9`)**:
   - The new string resource `cluster_trend_outlier` SHALL exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   - 100% translation parity and format specifier compatibility SHALL be enforced.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Outlier-Resilient Scaling)**:
  * *Given* a workout cluster with $N = 43$ sessions where 42 sessions have durations between 32:00 and 38:00 and 1 anomalous session has duration 115:52,
  * *When* `WorkoutClusterTrendMath.buildTrendSeries` computes the series,
  * *Then* the effective scale domain clamps against Tukey's IQR fences ($[Q_1 - 1.5 \times \text{IQR}, Q_3 + 1.5 \times \text{IQR}]$), the 42 normal sessions span the full vertical dynamic range ($[0f, 1f]$), and the anomalous session is clamped to $\text{normY} = 0.0f$ with `isClampedOutlier = true`.

* **Criterion 2 (Explicit Y-Axis Scale Labels)**:
  * *Given* a progression chart rendered in Duration mode,
  * *When* the athlete inspects the chart,
  * *Then* the top Y-axis gridline displays the fastest scale boundary (e.g. `"32:15"`), the bottom Y-axis gridline displays the slowest scale boundary (e.g. `"38:30"`), and the dashed average line displays the formatted average duration preceded by `Ø`.

* **Criterion 3 (Dual-Metric Axis Formatting Parity)**:
  * *Given* a progression chart toggled to Speed/Pace mode,
  * *When* cluster sport is Cycling (`BSportType.BIKE`),
  * *Then* the Y-axis labels format speeds with units (e.g. top `"32.5 km/h"`, bottom `"24.1 km/h"` via `SpeedFormatter`).
  * *When* cluster sport is Running (`BSportType.RUN`),
  * *Then* the Y-axis labels format paces with units (e.g. top `"4:30 /km"`, bottom `"6:15 /km"` via `PaceFormatter`).

* **Criterion 4 (Explicit X-Axis Timeline Markers)**:
  * *Given* a progression chart with sessions spanning from 2025-05-12 to 2026-10-19,
  * *When* inspecting the bottom baseline of the chart canvas,
  * *Then* the earliest workout date is displayed at the left boundary and the latest workout date is displayed at the right boundary.

* **Criterion 5 (Density-Adaptive Point Sizing)**:
  * *Given* a workout cluster with $N = 43$ sessions,
  * *When* the chart canvas renders data points,
  * *Then* non-PR points render with a compact $2\text{ dp}$ radius (eliminating visual overlap), while the PR point renders at $6\text{ dp}$ with gold styling.

* **Criterion 6 (Outlier Callout Badge)**:
  * *Given* a clamped outlier point selected by the user,
  * *When* the callout card renders beneath the chart,
  * *Then* it displays the outlier badge (`R.string.cluster_trend_outlier`) alongside the workout date and formatted duration.

* **Criterion 7 (Small Dataset Safe Fallback)**:
  * *Given* a workout cluster with $N = 2$ or $N = 3$ sessions (where IQR fencing is not statistically reliable) or with identical metric values ($\text{IQR} == 0$),
  * *When* `buildTrendSeries` executes,
  * *Then* it falls back safely to raw $\min..\max$ scaling without errors, zero division, or exceptions, and `isClampedOutlier` evaluates to `false`.

---

### 1.4 System Invariants

1. **"Faster-is-Higher" Athletic Coordinate Invariant**: Top of the canvas ($\text{normY} = 1.0f$) represents superior athletic performance across all modes (minimum duration, minimum running pace, maximum cycling speed).
2. **Arithmetic & Division-by-Zero Safety**: When dynamic range is zero ($\text{scaleMax} == \text{scaleMin}$), `normY` safely evaluates to `0.5f`; zero division, `NaN`, and `Infinity` are strictly precluded.
3. **Modularity & 500-Line Code Limit**: `WorkoutClusterTrendMath.kt` and `WorkoutClusterTrendChartCard.kt` MUST remain strictly below 500 lines of code.
4. **Single Source of Truth & DRY**: Velocity formatting delegates strictly to `SpeedFormatter` and `PaceFormatter`; time formatting delegates to `TimeFormatter`; short date formatting delegates to canonical date formatting.
5. **Clean-Room Test Pass Rate**: Full test suite (`./gradlew testDebugUnitTest`) MUST achieve 100% pass rate.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters (Lieblingsstrecken)*). Targets `WorkoutClusterTrendChartCard.kt`, `WorkoutClusterTrendMath.kt`, `WorkoutClusterHeatmapScreen.kt`, and `values*/strings.xml`.
* **Historical Origin & Commit Trace**: Implemented in Sprint `2026-42.10` under ticket `ATT-4067` (commit `feat(ATT-4067): Render historical duration and speed/pace trend chart for Workout Clusters (Lieblingsstrecken)`). Codified in `docs/requirements.md` under `REQ-SET-078` and `docs/design_guidelines.md` Section 2.6 (Rule 48).
* **Root Reason for Existing Formulation**: `REQ-SET-078` established the foundational progression trend card with dual metric modes (Duration vs Speed/Pace), "faster-is-higher" coordinate inversion, dashed average line, and PR gold highlighting. The initial implementation utilized straightforward linear scaling across raw min..max extrema without axis text labels to establish basic chart rendering quickly. When an anomalous workout occurs (e.g. 115 min vs 35 min normal), naive linear min..max scaling compresses 90%+ of sessions into an unreadable 10% band at the top of the canvas, while the lack of scale labels prevents athletes from reading absolute performance levels or chronological timeline span.
* **Preservation of Core Invariants**: The athletic "faster-is-higher" coordinate orientation (higher placement = superior athletic performance) remains strictly preserved. The minimum workout threshold ($N \ge 2$) for displaying the card is strictly preserved. The Personal Best gold badge, dashed average line, and interactive tap-to-peek launch remain 100% preserved. Introducing outlier-resilient Y-scaling and explicit axis scale labels directly resolves the distortion observed on-device in `ATT-4067`, fulfilling `docs/design_guidelines.md` Section 2.6 and Rule 48 without breaking any existing contract.

---

## 2. Test Specification (TST-SET-078)

### Test Case 1: Chronological Sorting & Cleansing (`[TST-SET-078.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendMathTest.kt`
* **Preconditions**: Member workouts provided in arbitrary order with positive and non-positive durations and velocities.
* **Action**: Invoke `WorkoutClusterTrendMath.buildTrendSeries(...)`.
* **Expected Result**: Output points are sorted ascending by `startTimeS`, non-positive durations and velocities are filtered out, and normalized coordinates lie within $[0f, 1f]$.

### Test Case 2: Faster-Is-Higher Coordinate Inversion (`[TST-SET-078.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendMathTest.kt`
* **Preconditions**: Multiple workouts with distinct durations, speeds, and running paces.
* **Action**: Compute series across Duration, Cycling Speed, and Running Pace modes.
* **Expected Result**: Faster duration produces higher normalized $Y$ (`normY(fast) > normY(slow)`); faster pace produces higher normalized $Y$; higher speed produces higher normalized $Y$.

### Test Case 3: Boundary & Edge Case Resilience (`[TST-SET-078.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendMathTest.kt`
* **Preconditions**: $N = 0$, $N = 1$, and identical durations (min == max).
* **Action**: Compute series.
* **Expected Result**: Safe handling without division-by-zero, `NaN`, or exceptions; identical values clamp safely to $Y = 0.5f$.

### Test Case 4: PR Determination & Touch Hit Testing (`[TST-SET-078.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendMathTest.kt`
* **Preconditions**: Series of workouts with a distinct minimum duration.
* **Action**: Verify `prWorkoutId` and execute `findNearestPoint`.
* **Expected Result**: PR index matches minimum duration effort; touch within radius selects nearest point, touch beyond radius returns null.

### Test Case 5: Tukey IQR Outlier Fencing & Resilient Y-Scaling (`[TST-SET-078.7]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendMathTest.kt`
* **Preconditions**:
  - Series of $N = 8$ workouts with 7 normal sessions (active durations $2000s, 2050s, 2100s, 2150s, 2200s, 2250s, 2300s$) and 1 extreme outlier session ($6952s$, 115 minutes).
* **Action**: Invoke `WorkoutClusterTrendMath.buildTrendSeries` in Duration mode.
* **Expected Result**:
  - $Q_1$, $Q_3$, and $\text{IQR}$ are computed correctly via rank interpolation.
  - Scale domain is clamped to $[Q_1 - 1.5 \times \text{IQR}, Q_3 + 1.5 \times \text{IQR}]$.
  - The 7 normal sessions expand across the full vertical canvas range ($[0.0f, 1.0f]$).
  - The outlier session is clamped to $\text{normY} = 0.0f$ with `isClampedOutlier = true`.
  - Fallback logic: when $N = 3$, raw $\min..\max$ scaling is applied with `isClampedOutlier = false`.

### Test Case 6: X-Axis and Y-Axis Scale Bounds Calculation & Formatting (`[TST-SET-078.8]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendMathTest.kt`
* **Preconditions**: Valid workouts spanning from timestamp $T_1$ to $T_2$ with known durations and velocities.
* **Action**: Evaluate `yAxisTopLabel`, `yAxisBottomLabel`, `xAxisStartLabel`, and `xAxisEndLabel` in `ClusterTrendSeries`.
* **Expected Result**:
  - In Duration mode: `yAxisTopLabel` contains formatted fastest duration (e.g. `"33:20"`), `yAxisBottomLabel` contains formatted slowest clamped duration (e.g. `"38:20"`).
  - In Speed mode: labels format velocity with units (e.g. `"32.5 km/h"`).
  - In Pace mode: labels format running pace with units (e.g. `"4:30 /km"`).
  - `xAxisStartLabel` and `xAxisEndLabel` match formatted short dates for earliest and latest workouts.

### Test Case 7: Density-Adaptive Point Sizing & Layout Contract (`[TST-SET-078.9]`)
* **Scope**: Unit / Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendLayoutAndVisibilityTest.kt`
* **Preconditions**: Verify source contracts in `WorkoutClusterTrendChartCard.kt`.
* **Action**: Validate point sizing conditions and file length invariants.
* **Expected Result**:
  - When $N > 20$, non-PR point radius evaluates to $2\text{ dp}$ (vs. standard $4\text{ dp}$ for $N \le 20$).
  - PR point retains gold $6\text{ dp}$ and selected point retains $5\text{ dp}$ + $9\text{ dp}$ ring.
  - Clamped outlier points render with distinct visual indicator.
  - Both `WorkoutClusterTrendMath.kt` and `WorkoutClusterTrendChartCard.kt` strictly remain $< 500$ lines.

### Test Case 8: 9-Language Localization & Specifier Audit (`[TST-SET-078.10]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendLocalizationTest.kt` & `TranslationParityTest.kt`
* **Goal**: Verify string presence and non-emptiness across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) for all trend keys including `cluster_trend_outlier`:

| Locale | Folder | Key | Expected Localized Value |
| :--- | :--- | :--- | :--- |
| English | `values` | `cluster_trend_outlier` | `Outlier` |
| German | `values-de` | `cluster_trend_outlier` | `Ausreißer` |
| Spanish | `values-es` | `cluster_trend_outlier` | `Atípico` |
| French | `values-fr` | `cluster_trend_outlier` | `Valeur aberrante` |
| Italian | `values-it` | `cluster_trend_outlier` | `Anomalo` |
| Japanese | `values-ja` | `cluster_trend_outlier` | `外れ値` |
| Dutch | `values-nl` | `cluster_trend_outlier` | `Uitschieter` |
| Polish | `values-pl` | `cluster_trend_outlier` | `Wartość odstająca` |
| Portuguese | `values-pt` | `cluster_trend_outlier` | `Ponto discrepante` |

* **Expected Result**: 100% parity across all 9 locales with zero missing keys and zero format specifier mismatches.

### Test Case 9: Clean-Room Regression Suite (`[TST-SET-078.11]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SET-078.1]` | Unit | `WorkoutClusterTrendMath.buildTrendSeries` | `REQ-SET-078.1` | Verified |
| `[TST-SET-078.2]` | Unit | `WorkoutClusterTrendMath.buildTrendSeries` | `REQ-SET-078.2` | Verified |
| `[TST-SET-078.3]` | Unit | `WorkoutClusterTrendMath.buildTrendSeries` | `REQ-SET-078.3` | Verified |
| `[TST-SET-078.4]` | Unit | `WorkoutClusterTrendMath.findNearestPoint` | `REQ-SET-078.4` | Verified |
| `[TST-SET-078.5]` | Localization | `WorkoutClusterTrendLocalizationTest` | `REQ-SET-078.5` | Verified |
| `[TST-SET-078.6]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Verified |
| `[TST-SET-078.7]` | Unit | `WorkoutClusterTrendMath.buildTrendSeries` | `REQ-SET-078.6` | Specified |
| `[TST-SET-078.8]` | Unit | `WorkoutClusterTrendMath.buildTrendSeries` | `REQ-SET-078.7` | Specified |
| `[TST-SET-078.9]` | Layout Contract | `WorkoutClusterTrendChartCard` | `REQ-SET-078.8` | Specified |
| `[TST-SET-078.10]` | Localization | `WorkoutClusterTrendLocalizationTest` | `REQ-SET-078.9`, `REQ-UI-106` | Specified |
| `[TST-SET-078.11]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
