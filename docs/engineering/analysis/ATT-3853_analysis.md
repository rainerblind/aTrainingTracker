# Stage 1 Analysis: ATT-3853 — Show time percentage below each zone segment in ZoneDistributionStackedBar when sufficient horizontal space is available

## 1. Problem Statement & User Impact
Following `ATT-3772` (`REQ-UI-387`), the verbose multi-line `FlowRow` per-zone legend beneath `ZoneDistributionStackedBar` was hidden by default (`showLegend: Boolean = false`) in `ZoneCardDisplayMode.STACKED_BAR` so that `HeartRateZoneDistributionCard` (*Herzfrequenzzonen*) and `PowerZoneDistributionCard` (*Leistungszonen*) render a clean, compact single-bar overview.

However, without the bottom legend, the stacked bar currently renders only colored proportional segments (`Box(modifier = Modifier.weight(entry.percentage))`) with zero numerical indicators. Athletes cannot quickly read the percentage of time spent in each dominant zone (e.g., `24%`, `52%`) without switching to `COLUMNS` mode. Displaying the formatted time percentage directly centered beneath each active zone segment—*only* when that segment's horizontal width is sufficient to fit the single-line label without clipping or overlapping—restores immediate numerical clarity while keeping narrow segments uncluttered.

## 2. Technical Root Cause & Gap Analysis
1. **Absence of Aligned Segment Percentage Row in `ZoneDistributionStackedBar.kt`**:
   - Currently, `ZoneDistributionStackedBar` renders:
     1. A `Box` (`height = barHeight = 16.dp`, `RoundedCornerShape(6.dp)`, `0.5.dp` `outlineBorder`) containing a `Row` of `activeEntries` weighted via `Modifier.weight(entry.percentage)` separated by `1.dp` inter-segment `Spacer`s.
     2. An optional `FlowRow` legend (`if (showLegend && distribution.entries.isNotEmpty())`).
   - There is no aligned horizontal `Row` beneath the stacked bar that mirrors the `Modifier.weight(entry.percentage)` and `1.dp` inter-segment spacers of `activeEntries` to display per-segment percentage labels.
2. **Pure Width-Threshold & Visual Overflow Detection (`ZoneDistributionStackedBarMath`)**:
   - A 2-character percentage label like `"5%"` (`labelSmall` ~11sp) requires ~16–18dp of horizontal width, a 3-character label like `"24%"` requires ~22–24dp, and a 4-character label like `"100%"` requires ~28–30dp.
   - If a zone accounts for only 1–4% of workout time (e.g., ~3–12dp wide on a 320dp card), rendering `"2%"` beneath it would either clip, wrap vertically into `"2\n%"` (violating Rule 38), or collide with adjacent labels.
   - Therefore, we need:
     - Deterministic helper methods in `ZoneDistributionStackedBarMath`:
       - `formatSegmentPercentage(percentage: Float, locale: Locale = Locale.getDefault()): String` returning `String.format(locale, "%.0f%%", percentage)`.
       - `minRequiredSegmentWidthDp(labelText: String, charWidthDp: Float = 6.5f, horizontalPaddingDp: Float = 4f): Float` returning `labelText.length * charWidthDp + horizontalPaddingDp`.
       - `canFitPercentageLabel(segmentWidthDp: Float, labelText: String, charWidthDp: Float = 6.5f, horizontalPaddingDp: Float = 4f): Boolean` returning `labelText.isNotEmpty() && segmentWidthDp >= minRequiredSegmentWidthDp(labelText, charWidthDp, horizontalPaddingDp)`.
     - Layout-level verification inside each segment cell using `BoxWithConstraints(modifier = Modifier.weight(entry.percentage), contentAlignment = Alignment.Center)` combined with `onTextLayout` overflow guarding (`!layoutResult.hasVisualOverflow` / `!layoutResult.didOverflowWidth`, `maxLines = 1`, `softWrap = false`) so labels are shown if and only if the segment is wide enough and the rendered glyphs do not overflow horizontally.

## 3. Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-UI-372` (*Continuous Horizontal Stacked Zone Distribution Bar*) and `REQ-UI-387` (*Compact Single-Bar Overview in `STACKED_BAR` Display Mode*), targeting `ZoneDistributionStackedBar.kt` and `ZoneDistributionStackedBarTest.kt`.
- **Historical Origin & Commit Trace**:
  - `ATT-3390` (commit `38706240`, Sprint 2026-42.4): Introduced `ZoneDistributionStackedBar.kt` (`REQ-UI-372`, `TST-UI-335`).
  - `ATT-3772` (commit `cb8056d5`, Sprint 2026-42.6): Changed `showLegend: Boolean = false` by default so `STACKED_BAR` mode omits the multi-line per-zone breakdown list (`REQ-UI-387`, `TST-UI-350`).
- **Root Reason for Existing Formulation**: `REQ-UI-387` hid the multi-line legend list because repeating 5–7 zone names, durations, and percentages in a wrapping `FlowRow` took too much vertical card height in the Workout Summary list. However, hiding the legend removed all percentage numbers from `STACKED_BAR` mode.
- **Preservation of Core Invariants**:
  - `showLegend: Boolean = false` default parameter and conditional `FlowRow` legend rendering when `showLegend == true` (`REQ-UI-387` / `TST-UI-350`) are 100% preserved.
  - Zero-duration zone filtering (`ZoneDistributionStackedBarMath.getActiveEntries`), proportional `Modifier.weight(entry.percentage)` sizing, `1.dp` inter-segment dividers, and `RoundedCornerShape(6.dp)` with `0.5.dp` WCAG 2.1 AA contrast border (`REQ-UI-372` / `TST-UI-335`) are 100% preserved.
  - Single-line non-wrapping text enforcement (`maxLines = 1`, `softWrap = false`, Rule 38) is strictly enforced.

## 4. User Scope Grounding (`ATT-1250`)
- **In-Scope**:
  1. Add `formatSegmentPercentage`, `minRequiredSegmentWidthDp`, and `canFitPercentageLabel` to `ZoneDistributionStackedBarMath` in `ZoneDistributionStackedBar.kt`.
  2. Add an aligned horizontal `Row` directly beneath the stacked bar in `ZoneDistributionStackedBar` (when `activeEntries.isNotEmpty()`) using identical `Modifier.weight(entry.percentage)` cells and `1.dp` inter-segment `Spacer`s.
  3. Inside each weighted cell (`BoxWithConstraints`), format the percentage as `ZoneDistributionStackedBarMath.formatSegmentPercentage(entry.percentage)` using `MaterialTheme.typography.labelSmall` (`color = MaterialTheme.colorScheme.onSurfaceVariant`, `maxLines = 1`, `softWrap = false`) and render it centered beneath the segment only when `ZoneDistributionStackedBarMath.canFitPercentageLabel(maxWidth.value, label)` is true and `!didOverflowWidth`.
  4. Expand `ZoneDistributionStackedBarTest.kt` to test `formatSegmentPercentage`, `minRequiredSegmentWidthDp`, `canFitPercentageLabel` across wide and narrow segments, and the `ZoneDistributionStackedBar.kt` source contract.
- **Out-of-Scope**:
  - Modifying `ZoneDistributionChart.kt` (`COLUMNS` mode) or `ZoneDistributionHistogramChart.kt` (`HISTOGRAM` mode).
  - Re-enabling the multi-line `FlowRow` legend in `HeartRateZoneDistributionCard` or `PowerZoneDistributionCard`.

## 5. Affected Files
- `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBar.kt`
- `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBarTest.kt`
- `docs/requirements.md` (`REQ-UI-398`)
- `docs/tests.md` (`TST-UI-361`)
