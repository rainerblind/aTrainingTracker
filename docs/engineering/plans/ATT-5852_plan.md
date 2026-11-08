# Stage 3: Implementation Plan - ATT-5852: Workout Cluster Trend Chart & Dashboard Polish

**Ticket**: [ATT-5852](https://rainerblind.atlassian.net/browse/ATT-5852)  
**Sub-task**: [ATT-6020](https://rainerblind.atlassian.net/browse/ATT-6020) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-490` (`REQ-UI-490.1`–`REQ-UI-490.4`)  
**Test Mapping**: `TST-UI-490` (`TST-UI-490.1`–`TST-UI-490.6`)  
**Branch**: `feature/ATT-5852`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
In `WorkoutClusterDetailDashboard` (`WorkoutClusterHeatmapScreen.kt` & `WorkoutClusterTrendChartCard.kt`), high-volume clusters (e.g. `Bike2Work` with 444 workouts) suffer from: (1) unreadable average text `Ø ...` obscured by dense lines; (2) bottom Y-axis label colliding with start date at `x = leftPadding`; (3) redundant card title `📈 Leistungsverlauf`; (4) lack of time-range filtering; and (5) unformatted cumulative distance and ascent totals (`4226 km`, `136730 m`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-490` (*Workout Cluster Trend Chart & Dashboard Polish*, `REQ-UI-490.1`–`REQ-UI-490.4`)
* **Test Mapping**: `TST-UI-490` (*Workout Cluster Trend Chart & Dashboard Verification Suite*, `TST-UI-490.1`–`TST-UI-490.6`)

---

## 3. System Invariants & Preserved Behavior
1. **Athletic Scaling Invariant**: "Faster-is-higher" Y-inversion and Tukey IQR outlier fencing strictly preserved.
2. **Interactive Selection**: PR gold highlight, point touch selection, and direct peek launch remain 100% intact.
3. **Formatter Parity**: Sub-1000m and sub-100km formatting in `DistanceFormatter` and `AltitudeFormatter` remain unchanged.
4. **Localization Parity**: 100% 9-language translation parity maintained with zero inline emojis (`ATT-5400`).

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutClusterTrendChartCard.kt` (UI Layer)
* Introduce `enum class ClusterTrendTimeRange { ALL, ONE_YEAR, THREE_YEARS }`.
* Filter workouts by `latestTimeS - spanS`, falling back safely to all workouts if filtered size $< 2$.
* Replace redundant `📈 Leistungsverlauf` heading with `FilterChip` group (`1J | 3J | Alle`) docked left of `[Dauer | Tempo]`.
* Expand `leftPadding` to `48.dp.toPx()`: render `yAxisTopLabel` and `yAxisBottomLabel` in the left gutter; render `xAxisStartLabel` and `xAxisEndLabel` along bottom baseline.
* Draw rounded surface pill badge (`surfaceColor.copy(alpha = 0.90f)`) behind `avgText` after drawing lines and markers.
* Update `pointerInput` touch hit-testing to use `leftPadding = 48.dp.toPx()`.

### Component 2: `DistanceFormatter.java` & `AltitudeFormatter.java` (Sensor Formatters)
* In `DistanceFormatter.format_with_units`: use `%,.0f km` / `%,.0f mile` for distances $\ge 1,000\text{ km}$.
* In `AltitudeFormatter.format_with_units`: use `%,.0f %s` for altitude/elevation values.

### Component 3: Unit & Contract Tests
* Add `WorkoutClusterTrendCardPolishTest.kt` asserting gutter separation, badge drawing, time-range filtering, and deduplicated header.
* Extend `MetricFormatterContractTest.kt` asserting thousands grouping in `DistanceFormatter` and `AltitudeFormatter`.

### UI Consistency (Rule 23)
* **Reference**: `WorkoutClusterTrendChartCard` & `WorkoutClusterDetailDashboard`.
* **Reused**: `FilterChip`, `MaterialTheme.colorScheme.surface`, `primary`, `onSurfaceVariant`.
* **Theme tokens**: `4.dp` / `10.dp` rounded shapes, `labelSmall`. New one-off styles: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Formatters & Implement Layout Polish
* Files: `DistanceFormatter.java`, `AltitudeFormatter.java`, `WorkoutClusterTrendChartCard.kt`
* Changes: Add `%,.0f` thousands grouping; add `ClusterTrendTimeRange`, header filter chips, 48dp left gutter, and average text badge.

### Step 2: Implement Unit & Contract Tests
* Files: `WorkoutClusterTrendCardPolishTest.kt`, `MetricFormatterContractTest.kt`
* Changes: Verify gutter separation, badge rendering, time-range filtering, and thousands grouping.

### Step 3: Targeted Unit Tests & Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.banalservice.sensor.formater.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suite and verify living docs via `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Clean branch isolation on `feature/ATT-5852` allows complete revert via `git restore .`.
