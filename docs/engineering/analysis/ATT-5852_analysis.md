# Stage 1 Analysis: ATT-5852 - WorkoutClusterTrendChartCard & Dashboard Polish

**Ticket**: [ATT-5852](https://rainerblind.atlassian.net/browse/ATT-5852)  
**Sub-task**: [ATT-6018](https://rainerblind.atlassian.net/browse/ATT-6018) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5852`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
In `WorkoutClusterDetailDashboard` (`WorkoutClusterHeatmapScreen.kt` & `WorkoutClusterTrendChartCard.kt`), high-volume clusters (e.g. `Bike2Work` with 444 workouts) exhibit several visual and readability defects:
1. Average label `Ø 0:29:23` is drawn without a background pill directly over dense trend lines and data markers, making it illegible.
2. Bottom-left Y-axis label (`0:35:04`) and X-axis start date (`20.04.2018`) collide vertically at `x = leftPadding` within cramped `bottomPadding = 28.dp`.
3. The card header redundantly repeats the tab title `📈 Leistungsverlauf`.
4. Plotting hundreds of multi-year workouts on one canvas creates a solid wall of lines; time-range filtering is missing.
5. Cumulative statistics (`Σ Distanz: 4226 km`, `Σ Anstieg: 136730 m`) lack localized thousands grouping separators.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * Visually inspected `detail_07_lieblingsstrecke_trend.png` and `detail_01_lieblingsstrecke_top.png` via `read_file` (Rule 71): confirmed illegible average text, label collision at bottom-left, redundant title, and unformatted numbers.
  * Inspected `WorkoutClusterTrendChartCard.kt` (L269–280): `yAxisBottomLabel` is drawn at `topPadding + plotHeight + 12.dp` and `xAxisStartLabel` at `size.height - 4.dp`, both at `x = leftPadding` (12.dp). Their baselines are only 12.dp apart and collide directly with each other and bottom data markers.
  * Inspected `DistanceFormatter.java` (L68) and `AltitudeFormatter.java` (L65): format templates use `%.0f` instead of `%,.0f`, omitting locale-aware thousands grouping.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Render a semi-opaque `surface` rounded badge behind `formattedAverage` after drawing lines and markers.
  2. Introduce a left Y-axis gutter (`leftPadding = 48.dp`) to host `yAxisTopLabel` and `yAxisBottomLabel`, reserving the bottom margin exclusively for `xAxisStartLabel` and `xAxisEndLabel`.
  3. Replace the redundant card title with compact time-range filter chips (`1J | 3J | Alle`) docked left of `[Dauer | Tempo]`.
  4. Filter trend workouts relative to the latest workout timestamp (`latestTimeS - spanS`) with automatic fallback to full dataset if `< 2` points match.
  5. Format cumulative totals with localized thousands separators in `DistanceFormatter` and `AltitudeFormatter`.
* **Out-of-Scope Non-Goals**:
  - Modifying clustering similarity math, centroid calculation, or SQLite schemas.
* **Complexity Rating**: **2 SP (Standard)**. Refinements across two composables, formatters, and 9-locale string resources.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-SET-078` (`REQ-SET-078.3`, `REQ-SET-078.7`, adding `REQ-SET-078.9` for time-range filtering) and Rule 36 formatters (`DistanceFormatter`, `AltitudeFormatter`).
* **Historical Origin & Commit Trace**: Commit `feat(ATT-4067)` (Sprint 2026-42.10), refined by `feat(ATT-4287)`.
* **Root Reason for Existing Formulation**: ATT-4287 introduced X/Y scale labels but placed both `yAxisBottomLabel` and `xAxisStartLabel` at `x = leftPadding`, without anticipating collision or time-range filtering on high-volume clusters.
* **Preservation of Core Invariants**: "Faster-is-higher" Y-scaling preserved; $N \ge 2$ threshold preserved; gold PR marker and tap-to-peek launch preserved; 9-language localization parity maintained without decorative emojis (`ATT-5400`).

---

## 5. Architectural Strategy & High-Level Solution
* **`WorkoutClusterTrendChartCard.kt`**:
  - Add `selectedTimeRange: ClusterTrendTimeRange` state (`ALL`, `ONE_YEAR`, `THREE_YEARS`).
  - In header row, replace title with `1J | 3J | Alle` `FilterChip` group docked on the left.
  - In Canvas: expand `leftPadding` to `48.dp.toPx()`, render Y-axis labels in the left gutter, and render X-axis dates along the bottom margin.
  - Draw rounded surface badge behind `avgText` on top of trend lines and points.
* **`DistanceFormatter.java` & `AltitudeFormatter.java`**:
  - Update `format_with_units` to use `%,.0f` for localized grouping on large totals.
* **Localization**:
  - Add `cluster_trend_range_1y`, `cluster_trend_range_3y`, `cluster_trend_range_all` across all 9 `strings.xml`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regressions in `MetricFormatterContractTest` or `WorkoutClusterTrendMathTest`.
  2. 100% 9-language parity verified by `TranslationParityTest`.
  3. Presentation-tier changes only; zero database schema impacts.
* **Risk Rating**: **LOW** (Targeted UI formatting, canvas layout decoupling, and localization strings).
