# Stage 3: Implementation Plan - ATT-4067: Render historical duration and speed/pace trend chart for Workout Clusters (Lieblingsstrecken)

**Ticket**: [ATT-4067](https://rainerblind.atlassian.net/browse/ATT-4067)  
**Sub-task**: [ATT-4263](https://rainerblind.atlassian.net/browse/ATT-4263) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-SET-078` (*Workout Cluster Historical Effort Trend Visualization, Dual-Metric Progression, and Interactive Inspection*)  
**Test Mapping**: `TST-SET-078` (*Workout Cluster Historical Effort Trend Chart, Dual-Metric Inversion, Reference Lines, and 9-Language Parity Verification Suite*)  
**Branch**: `feature/ATT-4067`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

In `WorkoutClusterHeatmapScreen.kt`, selecting a favorite track cluster presents an aggregate overview (`WorkoutClusterDetailDashboard`) showing hit count, cumulative distance, average speed, and a map polyline heatmap of member workouts.
However, athletes who repeatedly run or ride the same route currently lack an intuitive way to track their longitudinal progression over time. There is no visual timeline displaying whether their efforts are getting faster, where their Personal Best (PR) occurred, or how recent efforts compare to the cluster's historical average.

This plan details the implementation of a lightweight, pure Jetpack Compose progression chart card (`WorkoutClusterTrendChartCard.kt`) powered by decoupled calculation math (`WorkoutClusterTrendMath.kt`), dual metric modes (Duration vs. Speed/Pace) with intuitive "faster-is-higher" coordinate scaling, a cluster average reference line, Personal Best gold accents, interactive point tap inspection, and 9-language localization parity.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters*)
  * `REQ-SET-078.1`: Chronological Effort Progression & Data Cleansing ($N \ge 2$, filtering non-positive duration/velocity).
  * `REQ-SET-078.2`: Sport-Aware Dual Metric Modes & Inverted Scaling (Duration inverted, Running Pace inverted, Speed standard).
  * `REQ-SET-078.3`: Cluster Average Reference Line & Personal Best (PR) Highlight.
  * `REQ-SET-078.4`: Interactive Touch Selection & Peek Launch.
  * `REQ-SET-078.5`: 9-Language Localization Parity.
* **Test Mapping**: `TST-SET-078` (`TST-SET-078.1` to `TST-SET-078.6`)
  * `TST-SET-078.1`: Chronological Sorting & Cleansing Unit Tests.
  * `TST-SET-078.2`: Faster-Is-Higher Coordinate Inversion Tests.
  * `TST-SET-078.3`: Boundary & Edge Case Resilience Tests ($N=0, 1$, identical values).
  * `TST-SET-078.4`: PR Determination & Touch Hit Testing Tests.
  * `TST-SET-078.5`: 9-Language Localization Parity Audit.
  * `TST-SET-078.6`: Clean-Room Full Suite Regression Execution.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database Migrations Required**: All needed workout telemetry (`startTimeS`, `activeTimeSec`, `avgSpeedMps`, `bSportType`) is already present in `WorkoutData` loaded into memory by `WorkoutClustersViewModel.clusterWorkouts`.
2. **"Higher Is Better" Endurance Invariant**: On athletic progression charts, vertical position represents athletic performance. Lower durations (faster times) and lower running paces (faster paces) are inverted so they render higher on the $Y$-axis. Speed renders higher speed at higher $Y$.
3. **Single-Workout Resilience**: If a cluster has fewer than 2 valid member workouts ($N < 2$), the trend card is completely hidden, preventing layout clutter or single-point degenerate graphs.
4. **Zero Draw Allocation**: All `Path`, `Brush`, and geometry objects are pre-computed or calculated outside draw loops, preventing garbage collection stutter on 120 Hz displays.
5. **Single Source of Truth Formatting**: Unit conversion and formatting strictly delegates to canonical formatters (`TimeFormatter`, `SpeedFormatter`, `PaceFormatter`).
6. **9-Language Localization Parity**: All 6 UI string keys are translated across all 9 application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
7. **Clean-Room Test Suite Pass Rate**: All existing 3,421 tests must continue to pass cleanly with zero regressions.

---

## 4. Proposed Architectural Changes

### Component 1: Pure Mathematical & Presentation Domain Model (`WorkoutClusterTrendMath.kt`)
Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendMath.kt`:
* **Data Models**:
  ```kotlin
  enum class ClusterTrendMetricMode {
      DURATION,
      SPEED_OR_PACE
  }

  data class ClusterTrendDataPoint(
      val workoutId: Long,
      val timestampS: Long,
      val rawDurationSec: Long,
      val rawSpeedMps: Double,
      val formattedDate: String,
      val formattedDuration: String,
      val formattedSpeedOrPace: String,
      val isPr: Boolean,
      val normalizedX: Float, // [0f, 1f]
      val normalizedY: Float  // [0f, 1f] where 1f = best/fastest
  )

  data class ClusterTrendSeries(
      val points: List<ClusterTrendDataPoint>,
      val metricMode: ClusterTrendMetricMode,
      val avgNormalizedY: Float,
      val formattedAverage: String,
      val prWorkoutId: Long?,
      val isPace: Boolean
  )
  ```
* **Core Calculation Functions**:
  * `fun buildTrendSeries(workouts: List<WorkoutData>, sportType: BSportType, metricMode: ClusterTrendMetricMode, timeFormatter: TimeFormatter, speedFormatter: SpeedFormatter, paceFormatter: PaceFormatter, dateFormatter: (Long) -> String): ClusterTrendSeries`
    - Filter workouts: `it.activeTimeSec > 0 && it.avgSpeedMps > 0.0`.
    - Sort chronologically: ascending by `it.startTimeS`.
    - If `filtered.size < 2`: return empty series.
    - Compute PR index: index of workout with minimum `activeTimeSec`.
    - Compute $X$ normalization: `index.toFloat() / (filtered.size - 1).toFloat()`.
    - Compute $Y$ normalization:
      - Duration Mode: `(maxDuration - duration) / (maxDuration - minDuration)`. Clamped to `0.5f` if `maxDuration == minDuration`.
      - Speed Mode (Cycling/Other): `(speed - minSpeed) / (maxSpeed - minSpeed)`. Clamped to `0.5f` if `maxSpeed == minSpeed`.
      - Pace Mode (Running, pace = $1.0 / \text{speed}$): `(maxPace - pace) / (maxPace - minPace)`. Clamped to `0.5f` if `maxPace == minPace`.
    - Compute `avgNormalizedY` using cluster mean value projected onto the same min/max range.
  * `fun findNearestPoint(points: List<ClusterTrendDataPoint>, touchX: Float, touchY: Float, widthPx: Float, heightPx: Float, maxDistancePx: Float): ClusterTrendDataPoint?`
    - Maps points to screen pixels $(normX \times width, (1f - normY) \times height)$.
    - Returns the point with minimum Euclidean distance if distance $\le maxDistancePx$; else `null`.

### Component 2: Compose Visualization Component (`WorkoutClusterTrendChartCard.kt`)
Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterTrendChartCard.kt`:
* **Composables**:
  * `WorkoutClusterTrendChartCard(workouts: List<WorkoutData>, sportType: BSportType, onSelectWorkout: (Long) -> Unit, modifier: Modifier = Modifier)`
    - Internal state: `selectedMode: ClusterTrendMetricMode = DURATION`, `selectedPointId: Long?` (initialized to latest effort).
    - Memoized `trendSeries` using `remember(workouts, sportType, selectedMode)`.
    - Returns early if `trendSeries.points.size < 2`.
  * Card structure:
    - **Header**: Title `cluster_trend_title`, toggle buttons between Duration and Speed/Pace (`FilterChip` or segmented style).
    - **Canvas**: Height $160\text{ dp}$.
      - Gradient fill under trend path (`Brush.verticalGradient`).
      - Trend line polyline stroke ($2.5\text{ dp}$, `primary` color).
      - Horizontal dashed reference line at `avgNormalizedY` with label `cluster_trend_avg`.
      - Circles at each data point: regular points ($4\text{ dp}$ radius), PR point decorated with gold outer ring/accent (`#FFD700`), selected point highlighted with ring.
      - Pointer input gesture using `pointerInput` with `detectTapGestures` mapped to `findNearestPoint`.
    - **Callout Card**:
      - Compact info card below canvas: Date, Duration, Speed/Pace, and gold "PR" chip if applicable.
      - Clickable: tapping invokes `onSelectWorkout(point.workoutId)`.

### Component 3: Screen Integration (`WorkoutClusterHeatmapScreen.kt`)
In `WorkoutClusterHeatmapScreen.kt` inside `WorkoutClusterDetailDashboard`:
* Insert `WorkoutClusterTrendChartCard` directly below the existing statistics card:
  ```kotlin
  WorkoutClusterTrendChartCard(
      workouts = clusterWorkouts,
      sportType = cluster.probableSport,
      onSelectWorkout = { workoutId ->
          viewModel.selectWorkoutForPeek(workoutId)
      },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
  )
  ```

### Component 4: Localization Parity
Add 6 string keys across all 9 `res/values*/strings.xml` files:
* `cluster_trend_title`
* `cluster_trend_duration`
* `cluster_trend_speed`
* `cluster_trend_pace`
* `cluster_trend_pr`
* `cluster_trend_avg`

---

## 5. UI Consistency (Rule 23 — mandatory for UI changes)

* **Reference screen / component**: `WorkoutClusterDetailDashboard` in `WorkoutClusterHeatmapScreen.kt`, `ElevationProfile.kt` for Canvas curve drawing, and `ZoneDisplayModeSelector` / `FilterChip` for mode selection.
* **Reused components**: `ElevatedCard`, `FilterChip`, `HorizontalDivider`, `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.outlineVariant`, `MaterialTheme.typography.titleSmall`, `MaterialTheme.typography.labelSmall`.
* **Theme tokens**: Spacing $8\text{ dp}$ / $16\text{ dp}$, card rounded corner radius $12\text{ dp}$ (`shape = MaterialTheme.shapes.medium`), gold accent `#FFD700` (`TTColor.Gold`).
* **Justification for new elements**: Progression canvas requires specialized path rendering with vertical inversion; encapsulated strictly inside `WorkoutClusterTrendChartCard.kt` without external dependencies.

---

## 6. Verification & Test Plan

1. **`WorkoutClusterTrendMathTest.kt`**:
   - `testChronologicalSortingAndCleansing`: Verify ascending sort by `startTimeS`, exclusion of $\le 0$ duration/velocity.
   - `testDurationModeInversion`: Verify faster duration maps to higher `normalizedY`.
   - `testSpeedAndPaceModeInversion`: Verify higher speed and faster pace map to higher `normalizedY`.
   - `testBoundaryResilienceZeroAndOneWorkout`: Verify empty series when $N < 2$.
   - `testBoundaryResilienceIdenticalDurations`: Verify `normalizedY = 0.5f` when all workouts have identical duration.
   - `testPersonalBestIdentification`: Verify minimum duration effort is flagged `isPr = true`.
   - `testNearestPointHitTesting`: Verify hit detection within radius and null beyond radius.
2. **`WorkoutClusterTrendLocalizationTest.kt`**:
   - Verify all 6 string keys are present and non-empty across all 9 supported locales.
3. **Clean-Room Regression Suite**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 7. Step-by-Step Implementation Sequence

1. **Step 1**: Create `WorkoutClusterTrendMath.kt` containing models, `buildTrendSeries`, and `findNearestPoint`.
2. **Step 2**: Author `WorkoutClusterTrendMathTest.kt` and verify pure unit test passes.
3. **Step 3**: Populate all 6 string keys across all 9 `strings.xml` files.
4. **Step 4**: Author `WorkoutClusterTrendLocalizationTest.kt` and verify 9-language parity passes.
5. **Step 5**: Create `WorkoutClusterTrendChartCard.kt` with Compose canvas, toggle chips, PR highlight, and touch callout.
6. **Step 6**: Integrate `WorkoutClusterTrendChartCard` into `WorkoutClusterHeatmapScreen.kt`.
7. **Step 7**: Run full clean-room unit test suite (`./gradlew testDebugUnitTest`).
