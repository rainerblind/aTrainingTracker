# Stage 1 Analysis: ATT-4287 - Add X/Y axis labels and outlier-resilient Y-scaling to Workout Cluster progression chart

**Ticket**: [ATT-4287](https://rainerblind.atlassian.net/browse/ATT-4287)  
**Sub-task**: [ATT-4330](https://rainerblind.atlassian.net/browse/ATT-4330) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4287`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During the on-device Sprint Review of `ATT-4067` on a Google Pixel 10 inspecting the historical route cluster *"Zum Bäcker"* (comprising 43 recorded workouts; see attached review evidence `cluster_trend_review.png`), three critical usability and visualization deficiencies were observed in the progression trend chart (`WorkoutClusterTrendChartCard`):

1. **Missing Axis Markers and Scale Labels (X-Axis and Y-Axis)**:
   - The canvas trend curve renders data points and a horizontal dashed average line (`Ø`), but completely lacks visible X-axis date/effort scale markers and Y-axis scale values (fastest/top, average baseline, and slowest/bottom).
   - Athletes inspecting the chart cannot determine absolute performance values (e.g. was the best time 32 minutes or 45 minutes? was the speed 28 km/h or 35 km/h?) nor identify the chronological timeline span (earliest vs. latest session) without tapping individual points one by one.
2. **Severe Y-Axis Distortion & Compression from Extreme Outliers**:
   - In real-world athletic logs, an athlete occasionally records an anomalous workout (e.g. an initial exploratory ride with a long unpaused stop, or device left recording post-workout) yielding an extreme duration (e.g. 115:52 vs. normal cluster times of 32:00–38:00) or abnormally low average speed.
   - Because `WorkoutClusterTrendMath` currently scales the vertical axis linearly across the raw un-clamped extrema (`minDuration..maxDuration`), a single slow session expands the denominator by a factor of 3 to 5.
   - Consequently, all other 42 normal workouts are compressed into a flat, narrow band spanning only the top ~10–15% of the canvas height. This flattens meaningful day-to-day fitness progress and obscures the dashed average line (`Ø`).
3. **Dense Point Crowding for High Workout Counts ($N > 20$)**:
   - For heavily used route clusters ($N = 43$), drawing static `4.dp` radius points (diameter 8 dp) with outer selection halos across a mobile viewport width (~300–360 dp) causes adjacent points to collide into a continuous, cluttered chain where individual efforts cannot be visually distinguished.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic code audit of `WorkoutClusterTrendMath.kt` and `WorkoutClusterTrendChartCard.kt` reveals the architectural causes:

### A. Naive Min/Max Extrema in `WorkoutClusterTrendMath.kt` (Lines 141–175)
In `WorkoutClusterTrendMath.buildTrendSeries()`, vertical normalization currently evaluates raw minimum and maximum values without outlier detection:
```kotlin
val minDuration = validWorkouts.minOf { it.activeTimeSec }
val maxDuration = validWorkouts.maxOf { it.activeTimeSec }
// ...
val normY = ((maxDuration - w.activeTimeSec).toDouble() / (maxDuration - minDuration).toDouble()).toFloat().coerceIn(0f, 1f)
```
- When an outlier session has `activeTimeSec = 6952s` (115 min) while all other sessions fall within $[1920s, 2400s]$ (32 to 40 min), the dynamic range is $6952 - 1920 = 5032s$.
- The normal 8-minute spread ($480s$) occupies only $\frac{480}{5032} \approx 9.5\%$ of the vertical canvas height.
- The average duration is also skewed upward, dragging the dashed average line away from the cluster's true modal performance band.

### B. Lack of Text Measure & Canvas Axis Label Integration in `WorkoutClusterTrendChartCard.kt` (Lines 177–318)
The Compose `Canvas` block sets fixed insets (`horizontalPadding = 24.dp.toPx()`, `verticalPadding = 12.dp.toPx()`) and strictly renders geometry paths (`dashPath`, `fillPath`, `linePath`, `drawCircle`):
- No `android.graphics.Paint` / `drawContext.canvas.nativeCanvas.drawText()` routines or Compose layout labels are incorporated.
- The chart does not compute or expose formatted scale bounds for top, bottom, and average values on the Y-axis.
- The chart does not expose chronological boundaries (earliest date, latest date) for the X-axis.

### C. Static Point Radius in Canvas Drawing Loop (Lines 268–316)
The point rendering loop draws all non-PR points with fixed dimensions:
```kotlin
drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = c)
drawCircle(color = surfaceColor, radius = 1.8.dp.toPx(), center = c)
```
- At $N = 43$, available plot width is $\sim 280\text{ px} \approx 105\text{ dp}$. Point spacing is $\Delta x \approx 2.5\text{ dp}$, which is smaller than the point diameter ($8\text{ dp}$), creating unavoidable visual overlap.
- The rendering loop lacks density-adaptive point sizing based on workout count $N$.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Outlier-Resilient Y-Axis Scaling**: Implement robust statistical fencing (Tukey's Interquartile Range $\text{IQR} = Q_3 - Q_1$ with $[Q_1 - 1.5 \times \text{IQR}, Q_3 + 1.5 \times \text{IQR}]$ clamping when $N \ge 4$) in `WorkoutClusterTrendMath.kt`, mapping extreme outliers to canvas bounds and marking them with an outlier clamping flag (`isClampedOutlier = true`).
  * **Explicit Y-Axis Scale Labels**: Compute and expose top/fastest, average (`Ø`), and bottom/slowest scale labels in `ClusterTrendSeries`, rendered with crisp typography and contrast along the chart's reference lines.
  * **Explicit X-Axis Scale Labels**: Compute and expose chronological boundary labels (earliest workout date and latest workout date) formatted via canonical localized short date formatting (e.g. `formatShortDate`), rendered along the bottom axis baseline.
  * **Density-Adaptive Point Sizing**: When $N > 20$, reduce standard point radius to compact size (`2.dp` vs. standard `4.dp`), while preserving prominent styling for Personal Best (PR, gold `6.dp`) and Selected Point (`5.dp` + `9.dp` halo).
  * **Outlier Visual Differentiation**: Distinctly style or cap outlier points clamped at the boundary so the athlete understands they fall outside the primary scale.
  * **Modularity and Quality Verification**: Maintain file sizes strictly below 500 lines (`WorkoutClusterTrendChartCard.kt`, `WorkoutClusterTrendMath.kt`) and enforce 100% test pass rate across unit and layout test suites.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT alter spatial cluster grouping, centroid calculation, or GPS matching logic (`WorkoutClusterDatabaseManager`, `WorkoutClusterHeatmapScreen`).
  * Do NOT modify SQLite schemas, cursor mapping DTOs (`WorkoutData`, `WorkoutSummaries`), or room entities.
  * Do NOT introduce heavyweight third-party charting libraries (MPAndroidChart, Vico, etc.) — adhere strictly to the lightweight Compose Canvas architecture.
  * Do NOT modify other unrelated telemetry graphs (`DetailedWorkoutView`, `ElevationProfile`, `TelemetryMetricGraph`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters (Lieblingsstrecken)*). Targets `WorkoutClusterTrendChartCard.kt`, `WorkoutClusterTrendMath.kt`, `WorkoutClusterHeatmapScreen.kt`.
* **Historical Origin & Commit Trace**:
  - Implemented in Sprint `2026-42.10` under ticket `ATT-4067` (commit `feat(ATT-4067): Render historical duration and speed/pace trend chart for Workout Clusters (Lieblingsstrecken)`).
  - Codified in `docs/requirements.md` under `REQ-SET-078` and `docs/design_guidelines.md` Section 2.6 (Rule 48).
* **Root Reason for Existing Formulation**:
  - `REQ-SET-078` introduced the foundational progression trend card with dual metric modes (Duration vs. Speed/Pace), "faster-is-higher" coordinate inversion, dashed average line, and PR gold highlighting.
  - The initial version utilized straightforward linear scaling across raw $\min..\max$ values without axis text labels to establish basic chart rendering quickly.
* **Preservation of Core Invariants**:
  - The athletic "faster-is-higher" coordinate orientation (where higher vertical placement signifies superior performance) remains strictly preserved.
  - The minimum workout threshold ($N \ge 2$) for displaying the card is strictly preserved.
  - The Personal Best gold badge, dashed average line, and interactive tap-to-peek launch remain 100% preserved.
  - Introducing outlier-resilient Y-scaling and explicit axis scale labels directly resolves the distortion observed on-device in `ATT-4067`, fulfilling `docs/design_guidelines.md` Section 2.6 and Rule 48 without breaking any existing contract.

---

## 5. Architectural Strategy & High-Level Solution

### Component 1: Statistical Scaling Engine (`WorkoutClusterTrendMath.kt`)
1. **IQR Statistical Outlier Fencing**:
   - For valid sorted workouts where $N \ge 4$:
     - Sort metric values (active duration, speed, or pace).
     - Calculate $Q_1$ (25th percentile) and $Q_3$ (75th percentile) using standard linear rank interpolation.
     - Calculate $\text{IQR} = Q_3 - Q_1$.
     - If $\text{IQR} > 0$:
       - Calculate lower fence: $L = Q_1 - 1.5 \times \text{IQR}$.
       - Calculate upper fence: $U = Q_3 + 1.5 \times \text{IQR}$.
       - Compute effective scale domain: $[\text{scaleMin}, \text{scaleMax}] = [\max(\minVal, L), \min(\maxVal, U)]$.
     - If $\text{IQR} == 0$ (all central values identical) or $N < 4$:
       - Fall back safely to raw $[\minVal, \maxVal]$.
2. **Normalized Coordinate Mapping**:
   - Map each data point's `normY` against $[\text{scaleMin}, \text{scaleMax}]$ with `.coerceIn(0f, 1f)`.
   - Set `isClampedOutlier = (rawVal < scaleMin || rawVal > scaleMax)`.
   - Map `avgNormalizedY` against $[\text{scaleMin}, \text{scaleMax}]$ with `.coerceIn(0f, 1f)`.
3. **Axis Metadata in `ClusterTrendSeries`**:
   - `yAxisTopLabel`: Formatted value corresponding to $\text{normY} = 1.0f$ (fastest duration / fastest pace / highest speed).
   - `yAxisBottomLabel`: Formatted value corresponding to $\text{normY} = 0.0f$ (slowest duration / slowest pace / lowest speed).
   - `xAxisStartLabel`: Formatted short date of the earliest workout in the series.
   - `xAxisEndLabel`: Formatted short date of the latest workout in the series.

### Component 2: Compose Visualization Component (`WorkoutClusterTrendChartCard.kt`)
1. **Canvas Padding & Layout Allocation**:
   - Structure canvas insets to cleanly accommodate axis scale labels:
     - Top padding ($16\text{ dp}$) for top Y-axis label.
     - Bottom padding ($20\text{ dp}$) for bottom Y-axis label and X-axis date labels.
     - Left/Right horizontal padding ($24\text{ dp}$) for plot margins.
2. **Axis Text Rendering**:
   - Render Y-axis top, average (`Ø`), and bottom labels along the respective gridline/dash levels with secondary text styling.
   - Render X-axis start date (left-aligned) and end date (right-aligned) along the bottom axis baseline.
3. **Adaptive Point Sizing & Outlier Glyph**:
   - Point radius dynamically set:
     - $N \le 20 \implies \text{radius} = 4.dp$, inner = $1.8.dp$.
     - $N > 20 \implies \text{radius} = 2.dp$, inner = $1.dp$.
   - PR Point always rendered with distinct gold accent ($\text{radius} = 6.dp$).
   - Selected Point always rendered with full highlight ($\text{radius} = 5.dp$ + $9.dp$ outer ring).
   - Clamped outlier points rendered with distinctive visual cue (e.g. pinned circle with stroke styling).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **"Faster-is-Higher" Athletic Coordinate Invariant**: Top of the canvas ($\text{normY} = 1.0f$) represents the highest athletic achievement (minimum duration, minimum running pace, maximum cycling speed).
  2. **Arithmetic & Boundary Safety**: Zero variance ($\text{scaleMax} == \text{scaleMin}$) safely falls back to `0.5f` baseline; zero division, `NaN`, and `Infinity` are strictly precluded.
  3. **Modularity & 500-Line Limit**: Both `WorkoutClusterTrendMath.kt` and `WorkoutClusterTrendChartCard.kt` remain strictly below 500 lines of code.
  4. **Single Source of Truth & DRY**: Date formatting delegates to standard localized short date formatting; duration, speed, and pace formatting route through canonical formatters (`TimeFormatter`, `SpeedFormatter`, `PaceFormatter`).
  5. **100% Clean-Room Test Suite Pass Rate**: Full regression suite (`./gradlew testDebugUnitTest`) must pass without failures.
* **Risk Rating**: **LOW**
  - Self-contained UI presentation component and pure math logic.
  - Zero database schema alterations, zero Android service lifecycle modifications, and zero impact on background GPS tracking.
