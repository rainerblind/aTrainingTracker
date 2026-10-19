# Stage 2: Requirement & Test Specification - ATT-4067: Render historical duration and speed/pace trend chart for Workout Clusters (Lieblingsstrecken)

**Ticket**: [ATT-4067](https://rainerblind.atlassian.net/browse/ATT-4067)  
**Sub-task**: [ATT-4262](https://rainerblind.atlassian.net/browse/ATT-4262) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-SET-078` (*Workout Cluster Historical Effort Trend Visualization, Dual-Metric Progression, and Interactive Inspection*)  
**Test Mapping**: `TST-SET-078` (*Workout Cluster Historical Effort Trend Chart, Dual-Metric Inversion, Reference Lines, and 9-Language Parity Verification Suite*)  
**Branch**: `feature/ATT-4067`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Formal Requirements Specification (`REQ-SET-078`)

### `REQ-SET-078.1`: Progression Model & Chronological Ordering
The system SHALL extract and sort all member workouts for a `WorkoutCluster` into an ascending chronological effort progression series based on `startTimeS`:
1. Workouts with missing, non-positive, or corrupted duration (`activeTimeSec <= 0`) or velocity (`avgSpeedMps <= 0.0`) SHALL be excluded from the trend plot.
2. If the count of valid member workouts is strictly less than 2 ($N < 2$), the trend chart card SHALL be hidden completely from `WorkoutClusterDetailDashboard`.
3. If $N \ge 2$, the progression chart SHALL display all efforts plotted sequentially across the horizontal axis.

### `REQ-SET-078.2`: Sport-Aware Dual Metric Modes & Inverted "Faster-Is-Higher" Scaling
The progression chart SHALL offer a two-segment toggle between **Duration** and **Speed / Pace**:
1. **Duration Mode**:
   - Plots `activeTimeSec` on the $Y$-axis.
   - The vertical axis SHALL be inverted such that lower/faster durations are rendered higher on the canvas, preserving the fundamental athlete invariant that "higher on the chart represents better performance."
2. **Speed / Pace Mode**:
   - If the cluster's probable sport is Running (`BSportType.RUN`), the metric SHALL be Pace ($1000 / \text{avgSpeedMps}$) formatted via `PaceFormatter` (min/km or min/mi). Faster/lower pace values SHALL be rendered higher on the canvas.
   - If the cluster's probable sport is Cycling (`BSportType.BIKE`) or other sports, the metric SHALL be Speed ($\text{avgSpeedMps} \times 3.6$) formatted via `SpeedFormatter` (km/h or mph). Higher speed values SHALL be rendered higher on the canvas.
3. Unit conversions (metric vs imperial) SHALL strictly delegate to `TimeFormatter`, `SpeedFormatter`, and `PaceFormatter`.

### `REQ-SET-078.3`: Cluster Average Reference Line & Personal Best (PR) Highlight
1. **Cluster Average Reference Line**:
   - The chart SHALL render a horizontal dashed reference line across the plot width representing the cluster's average value:
     - In Duration mode: `avgDurationSec`.
     - In Speed mode: `avgSpeedKmh` (or imperial equivalent).
     - In Pace mode: `avgPaceSecPerKm` (or imperial equivalent).
   - The line SHALL be styled in a muted secondary color (`onSurfaceVariant.copy(alpha = 0.5f)`) with a subtle label ("Ø").
2. **Personal Best (PR) Highlight**:
   - The effort with the fastest time (minimum `activeTimeSec`) SHALL be decorated with a distinctive gold marker (`TTColor.Gold` / `#FFD700`) and trophy badge.

### `REQ-SET-078.4`: Interactive Point Touch & Workout Peeking
1. **Point Tap Detection**:
   - The chart SHALL accept tap gestures on data points within a touch tolerance radius of $24\text{dp}$.
   - Tapping an effort SHALL select that effort, drawing an active highlight indicator ring.
2. **Callout / Tooltip**:
   - Selecting a point SHALL render a compact callout showing:
     - Formatted Date (`formattedDate` or `formatMediumDate`)
     - Formatted Duration (`timeFormatter.format(activeTimeSec)`)
     - Formatted Speed or Pace
     - "PR" badge if the effort represents the best duration.
3. **Peek Navigation**:
   - Tapping the selected point or callout card SHALL dispatch `viewModel.selectWorkoutForPeek(workout.id)`, opening the workout peek sheet and track on map.

### `REQ-SET-078.5`: 9-Language Localization Parity
All user-facing strings introduced for the trend chart card SHALL be localized with 100% parity across all 9 supported languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-nl/`, `values-pl/`, `values-pt/`, `values-ru/`):
- `cluster_trend_title` ("Performance Trend" / "Leistungsverlauf")
- `cluster_trend_duration` ("Duration" / "Dauer")
- `cluster_trend_speed` ("Speed" / "Tempo")
- `cluster_trend_pace` ("Pace" / "Pace")
- `cluster_trend_pr` ("PR" / "Bestzeit")
- `cluster_trend_avg` ("Avg" / "Ø")

---

## 2. Acceptance Criteria (Given-When-Then)

### Criterion 1: Single Workout or Empty Cluster Hides Chart
- **Given** a Workout Cluster with $N \le 1$ matched workouts,
- **When** opening `WorkoutClusterHeatmapScreen.kt`,
- **Then** the trend chart card is not displayed in the dashboard, and zero exceptions or layout artifacts occur.

### Criterion 2: Multiple Workouts Render Chronological Trend
- **Given** a Workout Cluster with $N \ge 2$ matched workouts,
- **When** opening `WorkoutClusterHeatmapScreen.kt`,
- **Then** the `WorkoutClusterTrendChartCard` is rendered with all points ordered chronologically from left to right.

### Criterion 3: Faster-Is-Higher Scaling in Both Modes
- **Given** the trend chart is displayed,
- **When** viewing Duration mode,
- **Then** faster efforts (lower seconds) are rendered higher on the $Y$-axis than slower efforts.
- **When** switching to Speed mode on a Cycling cluster,
- **Then** faster efforts (higher km/h) are rendered higher on the $Y$-axis.

### Criterion 4: Cluster Average and PR Highlighting
- **Given** the trend chart is displayed,
- **When** inspecting the chart canvas,
- **Then** a horizontal dashed line is visible at the cluster average value, and the fastest effort is distinctly highlighted with gold accent.

### Criterion 5: Interactive Effort Selection & Peeking
- **Given** the trend chart is displayed,
- **When** the user taps an effort point,
- **Then** the callout card updates with that effort's date and performance metrics, and tapping the callout dispatches `selectWorkoutForPeek(id)`.

---

## 3. Test Specification (`TST-SET-078`)

| Test Spec ID | Target Component | Description & Verification Flow | Pass Criteria |
| :--- | :--- | :--- | :--- |
| `TST-SET-078.1` | `WorkoutClusterTrendMathTest.kt` | Verify chronological sorting by `startTimeS`, filtering out non-positive durations, and projection coordinate mapping. | Points sorted ascending; zero/negative durations filtered out; coordinates in $[0, 1]$ bounds. |
| `TST-SET-078.2` | `WorkoutClusterTrendMathTest.kt` | Verify vertical $Y$-inversion: ensure faster duration has higher normalized $Y$, and higher speed has higher normalized $Y$. | `normY(fast_time) > normY(slow_time)` and `normY(fast_speed) > normY(slow_speed)`. |
| `TST-SET-078.3` | `WorkoutClusterTrendMathTest.kt` | Verify boundary resilience: test $N = 0$, $N = 1$, and identical durations (min == max). | Returns empty series or clamped safe middle $Y = 0.5f$ without division-by-zero or NaN. |
| `TST-SET-078.4` | `WorkoutClusterTrendMathTest.kt` | Verify PR determination and nearest-point touch hit testing within radius $R$. | Correctly identifies min-duration index as PR; returns closest point within radius or null if too far. |
| `TST-SET-078.5` | `WorkoutClusterTrendLocalizationTest.kt` | Verify all 9 locale `strings.xml` define trend chart string keys with 100% parity. | Zero missing keys across all 9 languages. |
| `TST-SET-078.6` | `Full Test Suite` | Clean-room execution of `./gradlew testDebugUnitTest`. | 100% pass rate with zero regressions. |

---

## 4. Invariants & Guardrails

1. **Rule 23 (UI & Theme Integrity)**: Use semantic `MaterialTheme.colorScheme` tokens, `TTAlpha` constants, and `TTColor` constants.
2. **Rule 29 (9-Language Parity)**: All strings defined across all 9 locales.
3. **Zero Draw Allocation**: Pre-compute `Path`, `Brush`, and text measurements before or outside draw scope.
4. **Single Source of Truth**: Format values using `TimeFormatter`, `SpeedFormatter`, and `PaceFormatter`.
