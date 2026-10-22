# Stage 3: Implementation Plan - ATT-4287: Add X/Y axis labels and outlier-resilient Y-scaling to Workout Cluster progression chart

**Ticket**: [[ATT-4287]](https://rainerblind.atlassian.net/browse/ATT-4287)  
**Sub-task**: [[ATT-4332]](https://rainerblind.atlassian.net/browse/ATT-4332) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-176]](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-SET-078` (`REQ-SET-078.6`, `REQ-SET-078.7`, `REQ-SET-078.8`, `REQ-SET-078.9`)  
**Test Mapping**: `TST-SET-078.7`, `TST-SET-078.8`, `TST-SET-078.9`, `TST-SET-078.10`, `TST-SET-078.11`  
**Branch**: `feature/ATT-4287`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During on-device testing of `ATT-4067` on a Google Pixel 10 (Sprint Review 2026-42.10, documented in Jira review attachment `cluster_trend_review.png`), inspecting the historical route cluster *"Zum Bäcker"* (comprising 43 recorded workouts) revealed three critical visualization and usability deficiencies in the progression trend chart (`WorkoutClusterTrendChartCard.kt`):

1. **Missing Axis Markers and Scale Labels (X-Axis and Y-Axis)**:
   The canvas trend curve renders data points and a horizontal dashed average line (`Ø`), but completely lacks visible X-axis date/timeline scale markers and Y-axis scale values (fastest/top, average baseline, and slowest/bottom). Athletes inspecting the chart cannot determine absolute performance values (e.g. was the best time 32 minutes or 45 minutes? was the speed 28 km/h or 35 km/h?) nor identify the chronological timeline span (earliest vs. latest session) without tapping individual points one by one.
2. **Severe Y-Axis Distortion & Compression from Extreme Outliers**:
   In real-world athletic logs, an athlete occasionally records an anomalous workout (e.g. an initial exploratory ride with a long unpaused stop, or device left recording post-workout) yielding an extreme duration (e.g. 115:52 vs. normal cluster times of 32:00–38:00) or abnormally low average speed. Because `WorkoutClusterTrendMath.kt` currently scales the vertical axis linearly across the raw un-clamped extrema (`minDuration..maxDuration`), a single slow session expands the denominator by a factor of 3 to 5. Consequently, all other 42 normal workouts are compressed into a flat, narrow band spanning only the top ~10–15% of the canvas height. This flattens meaningful day-to-day fitness progress and obscures the dashed average line (`Ø`).
3. **Dense Point Crowding for High Workout Counts ($N > 20$)**:
   For heavily used route clusters ($N = 43$), drawing static `4.dp` radius points (diameter 8 dp) with outer selection halos across a mobile viewport width (~300–360 dp) causes adjacent points to collide into a continuous, cluttered chain where individual efforts cannot be visually distinguished.

Pursuant to **Governance Rule 48** and **Design Guidelines Section 2.6** (*Mandatory Axis Scale Labels & Outlier-Resilient Y-Scaling on Telemetry & Progression Charts*), this plan details the technical architecture and step-by-step construction sequence for Stage 4 implementation.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters (Lieblingsstrecken)*)
  * `REQ-SET-078.6` (*Statistical Outlier Fencing & Resilient Y-Scaling*):
    `WorkoutClusterTrendMath` SHALL calculate the vertical axis scaling domain using Tukey's Interquartile Range ($\text{IQR} = Q_3 - Q_1$) when $N \ge 4$. $Q_1$ and $Q_3$ SHALL be computed via linear rank interpolation on sorted performance values. When $\text{IQR} > 0$, the scale domain SHALL clamp against $[Q_1 - 1.5 \times \text{IQR}, Q_3 + 1.5 \times \text{IQR}]$. Outliers outside the fences SHALL be pinned to the canvas vertical boundary with `isClampedOutlier = true`. Small datasets ($N < 4$) or zero variance ($\text{IQR} == 0$) SHALL fall back safely to raw $\min..\max$ scaling with `isClampedOutlier = false`.
  * `REQ-SET-078.7` (*Explicit Y-Axis & X-Axis Scale Markers*):
    `ClusterTrendSeries` SHALL expose formatted boundary strings: `yAxisTopLabel` (fastest performance at $\text{normY} = 1.0f$), `yAxisBottomLabel` (slowest clamped performance at $\text{normY} = 0.0f$), `xAxisStartLabel` (earliest workout date), and `xAxisEndLabel` (latest workout date). `WorkoutClusterTrendChartCard` SHALL render these scale markers on the chart canvas with clear visual hierarchy and high contrast.
  * `REQ-SET-078.8` (*Density-Adaptive Point Sizing & Outlier Representation*):
    When $N > 20$, standard non-PR point radius SHALL adaptively reduce from $4\text{ dp}$ to $2\text{ dp}$ (inner dot $1.0\text{ dp}$). PR efforts SHALL retain gold decoration ($6\text{ dp}$, `TTColor.Gold`). Selected effort SHALL retain full highlight halo ($5\text{ dp}$ inner dot with $9\text{ dp}$ outer ring). Data points with `isClampedOutlier = true` SHALL be visually differentiated at the canvas boundary, and their selection callout card SHALL display an outlier indicator badge labeled with `R.string.cluster_trend_outlier`.
  * `REQ-SET-078.9` (*9-Language Localization Parity*):
    The string resource `cluster_trend_outlier` SHALL exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Test Mapping**: `TST-SET-078`
  * `TST-SET-078.7`: `WorkoutClusterTrendMathTest.testTukeyIqrOutlierFencingAndResilientYScaling` — validates IQR rank interpolation, fence calculation, outlier clamping, and fallback for $N < 4$.
  * `TST-SET-078.8`: `WorkoutClusterTrendMathTest.testXAxisAndYAxisScaleBoundsCalculationAndFormatting` — validates calculation and formatting of `yAxisTopLabel`, `yAxisBottomLabel`, `xAxisStartLabel`, and `xAxisEndLabel` across Duration, Speed, and Pace modes.
  * `TST-SET-078.9`: `WorkoutClusterTrendLayoutAndVisibilityTest` — validates adaptive point sizing ($2\text{ dp}$ for $N > 20$ vs. $4\text{ dp}$ for $N \le 20$), outlier visual cues, and the 500-line modularity limit.
  * `TST-SET-078.10`: `WorkoutClusterTrendLocalizationTest.testAllTrendStringsPresentAndNonEmptyInAll9Locales` — asserts that `cluster_trend_outlier` is present and non-empty in all 9 supported locales.
  * `TST-SET-078.11`: Full clean-room regression suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **"Faster-is-Higher" Athletic Coordinate Invariant**:
   Top of the canvas ($\text{normY} = 1.0f$) represents the highest athletic achievement across all modes:
   - Duration Mode: Minimum seconds maps to $1.0f$, maximum seconds maps to $0.0f$.
   - Running Pace Mode (`BSportType.RUN`): Minimum sec/m maps to $1.0f$, maximum sec/m maps to $0.0f$.
   - Cycling / Speed Mode: Maximum speed maps to $1.0f$, minimum speed maps to $0.0f$.
2. **Arithmetic & Division-by-Zero Safety**:
   When all valid workouts have identical performance metrics ($\text{scaleMax} == \text{scaleMin}$ or $\text{IQR} == 0$), normalization denominators are guarded against zero division. In this edge case, `normY` safely evaluates to `0.5f` (centered baseline), completely preventing `NaN`, `Infinity`, or arithmetic exceptions.
3. **Small Dataset Safe Fallback**:
   When $N < 4$, Tukey's IQR statistical fencing is statistically unreliable. The scaling engine falls back safely to raw $\min..\max$ scaling with `isClampedOutlier = false`, ensuring identical behavior to the baseline implementation for clusters with 2 or 3 workouts.
4. **Modularity & 500-Line Code Limit**:
   Both `WorkoutClusterTrendMath.kt` (currently 257 lines) and `WorkoutClusterTrendChartCard.kt` (currently 410 lines) MUST remain strictly below 500 lines of code. Rendering routines will be implemented concisely with helper functions to maintain clean architecture.
5. **Touch Hit Detection Precision**:
   `findNearestPoint` continues to resolve touches accurately within a $36\text{ dp}$ radius in canvas pixel space $(normX \times plotWidth, (1f - normY) \times plotHeight)$, correctly targeting clamped outlier points at the boundaries as well as interior points.
6. **Zero Database / Background Service Alterations**:
   The changes are strictly confined to Compose UI presentation and decoupled pure math logic. Zero SQLite schemas, ContentProviders, or `BANALService` tracking components are touched.
7. **Human Decision Gate on Parent Ticket (Rule 3) & Autonomous Subtask Progression**:
   Subtask `ATT-4332` transitions directly to `Erledigt` via `freigabe` upon passing Gate 3 audit, which automatically advances `ATT-4287` to `Implementation`. Terminal completion of `ATT-4287` to `Erledigt` remains strictly reserved for the human user in `Final Review (Human)`.
8. **Strict Code Lock in Stage 3**:
   Zero production code or unit test edits are committed or altered during Stage 3 planning. Modifications occur strictly in Stage 4 following Gate 3 approval (`check-gate`).

---

## 4. Proposed Architectural Changes (SWE.2)

### Architectural Decomposition
The implementation cleanly separates pure domain calculations from Compose UI drawing and localization:
* **Domain & Math Layer**: `WorkoutClusterTrendMath.kt` (Pure Kotlin object, zero Android SDK dependencies) handles chronological sorting, Tukey IQR fence calculation, Y-scaling, outlier clamping, and axis label metadata generation.
* **UI & Rendering Layer**: `WorkoutClusterTrendChartCard.kt` (Jetpack Compose Canvas) handles canvas layout geometry, native canvas text drawing for X/Y axis labels, density-adaptive point sizing, outlier point glyphs, and the interactive callout card with outlier badge.
* **Resource Layer**: `res/values*/strings.xml` defines `cluster_trend_outlier` across all 9 supported locales.
* **Test Layer**: `WorkoutClusterTrendMathTest.kt`, `WorkoutClusterTrendLayoutAndVisibilityTest.kt`, and `WorkoutClusterTrendLocalizationTest.kt` provide comprehensive unit, layout contract, and localization verification.

```
┌─────────────────────────────────────────────────────────────┐
│                       Jetpack Compose UI                    │
│   WorkoutClusterTrendChartCard.kt                           │
│   - Canvas layout with axis padding                         │
│   - drawText() for X-axis dates & Y-axis scale markers      │
│   - Density-adaptive point radii (2dp vs 4dp)               │
│   - Clamped outlier ring glyph & Outlier badge callout      │
└──────────────────────────────┬──────────────────────────────┘
                               │ reads ClusterTrendSeries
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 Pure Math & Statistical Engine              │
│   WorkoutClusterTrendMath.kt                                │
│   - Linear rank interpolation for Q1, Q3, and IQR           │
│   - Tukey fencing: [Q1 - 1.5*IQR, Q3 + 1.5*IQR]             │
│   - normY calculation & isClampedOutlier detection          │
│   - Boundary label formatting (yAxisTop, yAxisBottom, etc.) │
└──────────────────────────────┬──────────────────────────────┘
                               │ uses
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                     Canonical Formatters                    │
│   TimeFormatter, SpeedFormatter, PaceFormatter              │
└─────────────────────────────────────────────────────────────┘
```

### Component 1: Statistical Scaling Engine & Axis Metadata (`WorkoutClusterTrendMath.kt`)

1. **Model Enhancements**:
   Extend `ClusterTrendDataPoint` with outlier indicator:
   ```kotlin
   @Immutable
   data class ClusterTrendDataPoint(
       val workoutId: Long,
       val timestampS: Long,
       val rawDurationSec: Long,
       val rawSpeedMps: Double,
       val formattedDate: String,
       val formattedDuration: String,
       val formattedSpeedOrPace: String,
       val isPr: Boolean,
       val normalizedX: Float,
       val normalizedY: Float,
       val isClampedOutlier: Boolean = false
   )
   ```

   Extend `ClusterTrendSeries` with explicit axis labels:
   ```kotlin
   @Immutable
   data class ClusterTrendSeries(
       val points: List<ClusterTrendDataPoint>,
       val metricMode: ClusterTrendMetricMode,
       val avgNormalizedY: Float,
       val formattedAverage: String,
       val prWorkoutId: Long?,
       val isPace: Boolean,
       val yAxisTopLabel: String = "",
       val yAxisBottomLabel: String = "",
       val xAxisStartLabel: String = "",
       val xAxisEndLabel: String = ""
   )
   ```

2. **Statistical Fencing Logic**:
   In `WorkoutClusterTrendMath.kt`, introduce rank-interpolated percentile calculation and Tukey outlier fencing:
   - Rank interpolation for percentile $p \in [0.0, 1.0]$ over sorted array of size $N$:
     $$k = p \times (N - 1)$$
     $$i = \lfloor k \rfloor, \quad f = k - i$$
     $$P(p) = (1 - f) \times \text{sorted}[i] + f \times \text{sorted}[i + 1]$$
   - Percentiles: $Q_1 = P(0.25)$, $Q_3 = P(0.75)$, $\text{IQR} = Q_3 - Q_1$.
   - Fences (when $N \ge 4$ and $\text{IQR} > 0$):
     $$\text{lowerFence} = Q_1 - 1.5 \times \text{IQR}$$
     $$\text{upperFence} = Q_3 + 1.5 \times \text{IQR}$$
     $$\text{scaleMin} = \max(\text{rawMin}, \text{lowerFence})$$
     $$\text{scaleMax} = \min(\text{rawMax}, \text{upperFence})$$
   - Outlier detection:
     A workout is an outlier if its raw metric value falls strictly outside $[\text{scaleMin}, \text{scaleMax}]$.
   - Normalized Y calculation:
     $$\text{normY} = \begin{cases}
       \frac{\text{scaleMax} - \text{val}}{\text{scaleMax} - \text{scaleMin}} & \text{if inverted (Duration, Pace)} \\
       \frac{\text{val} - \text{scaleMin}}{\text{scaleMax} - \text{scaleMin}} & \text{if standard (Speed)}
     \end{cases}.\text{coerceIn}(0f, 1f)$$
   - Scale boundary labels:
     - Duration Mode: `yAxisTopLabel = timeFormatter.format(scaleMin.toLong())`, `yAxisBottomLabel = timeFormatter.format(scaleMax.toLong())`.
     - Speed Mode: `yAxisTopLabel = speedFormatter.format_with_units(scaleMax)`, `yAxisBottomLabel = speedFormatter.format_with_units(scaleMin)`.
     - Pace Mode: `yAxisTopLabel = paceFormatter.format_with_units(scaleMin)`, `yAxisBottomLabel = paceFormatter.format_with_units(scaleMax)`.
     - Timeline labels: `xAxisStartLabel = validWorkouts.first().formattedDate`, `xAxisEndLabel = validWorkouts.last().formattedDate`.

### Component 2: Compose Visualization Component (`WorkoutClusterTrendChartCard.kt`)

1. **Canvas Insets & Geometry Allocation**:
   Adjust canvas insets to reserve dedicated margin space for axis text:
   - `chartHeight = 150.dp` (providing adequate vertical room for axis labels without squishing plot).
   - Insets:
     - `leftPadding = 12.dp.toPx()`
     - `rightPadding = 12.dp.toPx()`
     - `topPadding = 16.dp.toPx()` (space for top Y-axis label)
     - `bottomPadding = 24.dp.toPx()` (space for bottom Y-axis and X-axis labels)
     - `plotWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)`
     - `plotHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)`

2. **Axis Text Rendering**:
   Render axis scale text using `drawContext.canvas.nativeCanvas.drawText()` with `android.graphics.Paint`:
   - Paint configured with `android.graphics.Typeface.DEFAULT`, text size $10\text{ sp}$, anti-aliased, and `onSurfaceVariant` color.
   - **Y-Axis Top Label**: Rendered at $X = \text{leftPadding}$, $Y = \text{topPadding} - 4\text{ dp}$.
   - **Y-Axis Bottom Label**: Rendered at $X = \text{leftPadding}$, $Y = \text{topPadding} + \text{plotHeight} + 12\text{ dp}$.
   - **Y-Axis Average Label**: Rendered adjacent to the dashed line at $X = \text{size.width} - \text{rightPadding} - \text{textWidth}$, $Y = \text{avgCanvasY} - 4\text{ dp}$.
   - **X-Axis Start Date**: Left-aligned at $X = \text{leftPadding}$, $Y = \text{size.height} - 4\text{ dp}$.
   - **X-Axis End Date**: Right-aligned at $X = \text{size.width} - \text{rightPadding}$, $Y = \text{size.height} - 4\text{ dp}$.

3. **Density-Adaptive Point Sizing & Clamped Outlier Visuals**:
   - Dynamic point radius:
     $$\text{pointRadius} = \begin{cases} 2.dp & \text{if } N > 20 \\ 4.dp & \text{if } N \le 20 \end{cases}, \quad \text{innerRadius} = \begin{cases} 1.dp & \text{if } N > 20 \\ 1.8.dp & \text{if } N \le 20 \end{cases}$$
   - Personal Best (PR): always $6\text{ dp}$ with `TTColor.Gold`.
   - Selected Point: always $5\text{ dp}$ with $9\text{ dp}$ outer halo.
   - Clamped Outlier Point: when `p.isClampedOutlier == true` and not selected/PR, rendered with distinct outer border or hollow glyph at the canvas edge.

4. **Inspector Callout Outlier Badge**:
   When `selectedPoint.isClampedOutlier == true`, the inspector callout card displays an Outlier pill badge adjacent to the date:
   ```kotlin
   if (selectedPoint.isClampedOutlier) {
       Surface(
           shape = RoundedCornerShape(4.dp),
           color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
       ) {
           Text(
               text = stringResource(R.string.cluster_trend_outlier),
               style = MaterialTheme.typography.labelSmall,
               fontWeight = FontWeight.Bold,
               color = MaterialTheme.colorScheme.onTertiaryContainer,
               modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
           )
       }
   }
   ```

### Component 3: 9-Locale XML String Resource Parity (`res/values*/strings.xml`)

Add `cluster_trend_outlier` across all 9 application locales:

| Locale | Path | Key | Value |
| :--- | :--- | :--- | :--- |
| **English** | `app/src/main/res/values/strings.xml` | `cluster_trend_outlier` | `Outlier` |
| **German** | `app/src/main/res/values-de/strings.xml` | `cluster_trend_outlier` | `Ausreißer` |
| **Spanish** | `app/src/main/res/values-es/strings.xml` | `cluster_trend_outlier` | `Atípico` |
| **French** | `app/src/main/res/values-fr/strings.xml` | `cluster_trend_outlier` | `Valeur aberrante` |
| **Italian** | `app/src/main/res/values-it/strings.xml` | `cluster_trend_outlier` | `Anomalo` |
| **Japanese** | `app/src/main/res/values-ja/strings.xml` | `cluster_trend_outlier` | `外れ値` |
| **Dutch** | `app/src/main/res/values-nl/strings.xml` | `cluster_trend_outlier` | `Uitschieter` |
| **Polish** | `app/src/main/res/values-pl/strings.xml` | `cluster_trend_outlier` | `Wartość odstająca` |
| **Portuguese** | `app/src/main/res/values-pt/strings.xml` | `cluster_trend_outlier` | `Ponto discrepante` |

---

## 5. UI Consistency (Rule 23 — Mandatory for UI Changes)

Per `docs/design_guidelines.md` Section 5:
* **Reference screen / component**:
  - `WorkoutClusterDetailDashboard` in `WorkoutClusterHeatmapScreen.kt`.
  - `WorkoutClusterTrendChartCard.kt` baseline.
  - `ElevationProfile.kt` for Canvas curve styling.
* **Reused components**:
  - `Surface`, `Column`, `Row`, `Text`, `Icon`, `FilterChip`.
  - Canonical formatters: `TimeFormatter`, `SpeedFormatter`, `PaceFormatter`.
* **Theme tokens**:
  - **Shapes**: `RoundedCornerShape(10.dp)` for chart card, `RoundedCornerShape(4.dp)` for badges, `RoundedCornerShape(8.dp)` for callout card.
  - **Spacing**: Canonical spacing scale: `4.dp`, `8.dp`, `12.dp`, `16.dp`.
  - **Colors**: `MaterialTheme.colorScheme.primary`, `surfaceContainerHigh`, `outlineVariant`, `onSurface`, `onSurfaceVariant`, `TTColor.Gold` for PR accents, and `tertiaryContainer` / `onTertiaryContainer` for outlier callout badge.
* **New one-off styles & justification**: None. Axis scale markers utilize standard `MaterialTheme.typography.labelSmall` typography drawn directly on canvas for 120 Hz performance.

---

## 6. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Check Gate 3 Approval (Rule 4)
* Verify that Stage 3 subtask `ATT-4332` is `Erledigt` before modifying any files:
  ```bash
  python3 tools/jira_util.py check-gate ATT-4332
  ```

### Step 2: 9-Locale XML String Resource Population
* Add `cluster_trend_outlier` to:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`

### Step 3: Implement Tukey IQR Fencing & Axis Metadata in `WorkoutClusterTrendMath.kt`
* Add `isClampedOutlier` property to `ClusterTrendDataPoint`.
* Add `yAxisTopLabel`, `yAxisBottomLabel`, `xAxisStartLabel`, `xAxisEndLabel` to `ClusterTrendSeries`.
* Implement rank interpolation percentile calculation and Tukey IQR fencing.
* Compute clamped normalization bounds and boundary labels.
* Maintain file length strictly $< 500$ lines.

### Step 4: Unit Testing in `WorkoutClusterTrendMathTest.kt` & `WorkoutClusterTrendLocalizationTest.kt`
* Add `testTukeyIqrOutlierFencingAndResilientYScaling` (`[TST-SET-078.7]`).
* Add `testXAxisAndYAxisScaleBoundsCalculationAndFormatting` (`[TST-SET-078.8]`).
* Update `WorkoutClusterTrendLocalizationTest.kt` to assert `cluster_trend_outlier` presence across all 9 locales (`[TST-SET-078.10]`).
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterTrendMathTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterTrendLocalizationTest"
  ```

### Step 5: Implement Axis Labels, Adaptive Points & Outlier Styling in `WorkoutClusterTrendChartCard.kt`
* Update canvas insets and layout geometry.
* Implement native canvas text rendering for top/bottom Y-axis scale labels and start/end X-axis timeline dates.
* Implement density-adaptive point radius ($2\text{ dp}$ for $N > 20$).
* Implement visual outlier indicator glyph for clamped points.
* Add Outlier badge to inspector callout card.
* Maintain file length strictly $< 500$ lines.

### Step 6: Layout Contract Verification in `WorkoutClusterTrendLayoutAndVisibilityTest.kt`
* Add layout contract test verifying point radius conditions and file length limits (`[TST-SET-078.9]`).
* Run targeted test:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterTrendLayoutAndVisibilityTest"
  ```

### Step 7: Fast Cluster Unit Loop Execution
* Execute all cluster unit and layout tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*"
  ```

---

## 7. Verification & Rollback Plan

### Verification
1. **Targeted Unit & Layout Tests (Stage 4)**:
   - `WorkoutClusterTrendMathTest`: Validates Tukey IQR scaling, boundary fallback, coordinate inversion, and label formatting.
   - `WorkoutClusterTrendLocalizationTest`: Validates 9-locale parity for all trend keys including `cluster_trend_outlier`.
   - `WorkoutClusterTrendLayoutAndVisibilityTest`: Validates density-adaptive point sizing and 500-line invariant.
2. **Clean-Room Regression Suite (Stage 5)**:
   - Full test suite `./gradlew testDebugUnitTest` must pass with 100% success (0 failures, 0 regressions).
3. **On-Device Walkthrough Verification**:
   - Inspect cluster *"Zum Bäcker"* ($N=43$) on Google Pixel 10 to confirm:
     - The 42 normal workouts now expand across the full vertical canvas height.
     - Outlier workout ($115:52$) is clamped to the bottom boundary with outlier styling.
     - Fastest and slowest scale labels appear on the Y-axis.
     - Earliest and latest dates appear on the X-axis baseline.
     - Points render cleanly with compact $2\text{ dp}$ radius without visual overlap.
     - Tapping the outlier point reveals the "Ausreißer" / "Outlier" badge in the callout card.

### Rollback Plan
* If unexpected issues arise, `git restore` on modified files restores the branch to pristine state.
* Feature branch isolation (`feature/ATT-4287`) ensures that `sprint/2026-43.1` and `develop` remain completely isolated and protected.
