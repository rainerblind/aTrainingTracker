# Stage 1 Analysis: ATT-3772

## 1. Problem Statement & User Intent
- **Ticket**: `ATT-3772` — *Remove detailed per-zone time breakdown list when Zone Distribution card is in Stacked Bar mode*
- **Observed Issue**: During the Sprint 2026-42.5 Joint Review of `ATT-3676`, when `HeartRateZoneDistributionCard` or `PowerZoneDistributionCard` is displayed in `ZoneCardDisplayMode.STACKED_BAR` (`"Stacked Bar"` / `"Balken"`), `ZoneDistributionStackedBar` renders a multi-row `FlowRow` per-zone time and percentage breakdown list (`StackedBarLegendItem`: `Z1 5:00 (25%) ...`) underneath the horizontal stacked bar.
- **User Intent**: In `ZoneCardDisplayMode.STACKED_BAR` mode, the detailed per-zone time breakdown list underneath the horizontal stacked bar must be omitted so that `STACKED_BAR` mode serves as a clean, compact single-bar visual overview (especially suited for the Workout Summary / List view), while detailed per-zone duration/percentage inspection remains available in `ZoneCardDisplayMode.COLUMNS` mode.

## 2. Forensic Root Cause Analysis & Call-Site Audit
1. **`ZoneDistributionStackedBar.kt` (lines 79–145)**:
   - `ZoneDistributionStackedBar` defines parameter `showLegend: Boolean = true`.
   - When `showLegend` is `true`, lines 131–144 render a `FlowRow` iterating over `distribution.entries` (`5` zones for HR, `7` zones for Power) and rendering `StackedBarLegendItem(entry)` (`Z1 5:00 (25%)`), which wraps across 2–3 rows and adds significant vertical height to the card.
2. **`HeartRateZoneDistributionCard.kt` (lines 103–108)**:
   - In the `when (effectiveDisplayMode)` block for `ZoneCardDisplayMode.STACKED_BAR`, calls `ZoneDistributionStackedBar(distribution = distribution, modifier = Modifier.fillMaxWidth())` without `showLegend = false`.
3. **`PowerZoneDistributionCard.kt` (lines 103–108)**:
   - In the `when (effectiveDisplayMode)` block for `ZoneCardDisplayMode.STACKED_BAR`, calls `ZoneDistributionStackedBar(distribution = distribution, modifier = Modifier.fillMaxWidth())` without `showLegend = false`.
4. **`ZoneDistributionColumnChart.kt` & `TelemetryHistogramChart.kt`**:
   - `ZoneCardDisplayMode.COLUMNS` (`ZoneDistributionColumnChart`) continues to display per-zone durations (`m:ss`), percentages (`%`), and `Z1..Z7` labels for detailed tabular/columnar inspection.

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-372` (*Horizontal Stacked Distribution Bar & 3-Way Zone Display Mode Switching*, introduced in `ATT-3390` and extended in `ATT-3676`), formalized as net-new requirement `REQ-UI-387` (`TST-UI-350`).
   - Targets `ZoneDistributionStackedBar.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, and `ZoneDistributionStackedBarTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-42.4 (`ATT-3390`, commit `63ccd164`) introduced `ZoneDistributionStackedBar` with `showLegend: Boolean = true`.
   - Sprint 2026-42.5 (`ATT-3676`, commit `b989e1ce`) added `ZoneCardDisplayMode.STACKED_BAR` preference support across Workout Summary and Workout Details.
3. **Root Reason for Existing Formulation ("Why was this fence built?")**:
   - `ATT-3390` initially added a `FlowRow` legend underneath the stacked bar before `STACKED_BAR` was designated as the compact summary overview mode in `ATT-3772` / `ATT-3773`.
4. **Preservation of Core Invariants ("Why is it safe to modify now?")**:
   - Changing `showLegend: Boolean = false` as the default in `ZoneDistributionStackedBar` and explicitly passing `showLegend = false` from `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` in `STACKED_BAR` mode removes the redundant multi-row per-zone breakdown list in `STACKED_BAR` mode while preserving full per-zone duration (`m:ss`) and percentage (`%`) inspection in `COLUMNS` mode, preserving the card header total active duration, and keeping optional `showLegend` capability intact if ever explicitly requested.

## 4. Scope Boundaries & System Invariants
- **In Scope**:
  - Change default parameter `showLegend: Boolean = false` on `ZoneDistributionStackedBar` in `ZoneDistributionStackedBar.kt`.
  - Explicitly pass `showLegend = false` in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` when rendering `ZoneCardDisplayMode.STACKED_BAR`.
  - Update `ZoneDistributionStackedBarTest.kt` to verify that `showLegend` defaults to `false` and that `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` invoke `ZoneDistributionStackedBar` with `showLegend = false`.
- **Out of Scope**:
  - Separate default display mode preferences for Workout Summary vs. Workout Details and hiding the mode switcher in Workout Summary (handled in the next ticket, `ATT-3773`).
- **System Invariants**:
  - Continuous horizontal stacked bar proportional weighting (`weight(entry.percentage)`), zero-duration filtering, `16.dp` bar height, `RoundedCornerShape(6.dp)`, and `0.5.dp` WCAG 2.1 AA outline border remain unchanged.
  - `COLUMNS` and `HISTOGRAM` display modes remain unchanged.
  - 100% clean-room unit test pass rate preserved.

## 5. Risk Assessment
- **Risk Level**: **LOW** — Self-contained UI presentation refinement in `ZoneDistributionStackedBar`, `HeartRateZoneDistributionCard`, and `PowerZoneDistributionCard`.
