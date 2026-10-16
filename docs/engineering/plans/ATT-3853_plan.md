# Stage 3 Architecture & Implementation Plan: ATT-3853 — Show time percentage below each zone segment in ZoneDistributionStackedBar when sufficient horizontal space is available

## 1. Architectural Decomposition (SWE.2)
- **UI & Pure Math Layer (`com.atrainingtracker.trainingtracker.ui.aftermath.zones`)**:
  - `ZoneDistributionStackedBarMath` (`ZoneDistributionStackedBar.kt`):
    - Add pure, locale-aware percentage formatting and minimum segment width threshold functions:
      ```kotlin
      const val DEFAULT_CHAR_WIDTH_DP = 6.5f
      const val DEFAULT_HORIZONTAL_PADDING_DP = 4f

      fun formatSegmentPercentage(percentage: Float, locale: Locale = Locale.getDefault()): String {
          return String.format(locale, "%.0f%%", percentage)
      }

      fun minRequiredSegmentWidthDp(
          labelText: String,
          charWidthDp: Float = DEFAULT_CHAR_WIDTH_DP,
          horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING_DP
      ): Float {
          return labelText.length * charWidthDp + horizontalPaddingDp
      }

      fun canFitPercentageLabel(
          segmentWidthDp: Float,
          labelText: String,
          charWidthDp: Float = DEFAULT_CHAR_WIDTH_DP,
          horizontalPaddingDp: Float = DEFAULT_HORIZONTAL_PADDING_DP
      ): Boolean {
          return labelText.isNotEmpty() &&
              segmentWidthDp >= minRequiredSegmentWidthDp(labelText, charWidthDp, horizontalPaddingDp)
      }
      ```
  - `ZoneDistributionStackedBar` (`ZoneDistributionStackedBar.kt`):
    - Wrap the `barHeight` stacked bar `Box` and the new per-segment percentage `Row` inside a tight `Column(verticalArrangement = Arrangement.spacedBy(4.dp))` (or directly in the outer `Column` with `4.dp` spacing between the bar and its percentage labels).
    - When `activeEntries.isNotEmpty()`, render a horizontal `Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)` directly beneath the stacked bar `Box`.
    - Iterate over `activeEntries.forEachIndexed { index, entry -> ... }`:
      - Render a `BoxWithConstraints(modifier = Modifier.weight(entry.percentage), contentAlignment = Alignment.Center)`:
        - Compute `val label = ZoneDistributionStackedBarMath.formatSegmentPercentage(entry.percentage)`.
        - Track `var didOverflowWidth by remember(label, maxWidth) { mutableStateOf(false) }`.
        - When `ZoneDistributionStackedBarMath.canFitPercentageLabel(maxWidth.value, label) && !didOverflowWidth`, render:
          ```kotlin
          Text(
              text = label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Clip,
              onTextLayout = { layoutResult ->
                  if (layoutResult.didOverflowWidth) {
                      didOverflowWidth = true
                  }
              }
          )
          ```
      - Between segments (`if (index < activeEntries.lastIndex)`), render `Spacer(modifier = Modifier.width(1.dp))` matching the `1.dp` inter-segment divider above.
- **Threading & Concurrency**:
  - Pure UI composition on the main thread; `ZoneDistributionStackedBarMath` functions are pure, stateless, and thread-safe.

## 2. UI Consistency (Rule 23 — `docs/design_guidelines.md` Section 5)
- **Closest Existing Reference Component**: `ZoneDistributionStackedBar.kt` (`StackedBarLegendItem`) and `ZoneDistributionChart.kt` (`HeartRateZoneDistributionCard` / `PowerZoneDistributionCard`).
- **Theme Tokens**:
  - Typography: `MaterialTheme.typography.labelSmall` (`maxLines = 1`, `softWrap = false`, Rule 38).
  - Color: `MaterialTheme.colorScheme.onSurfaceVariant` (ensuring subtle, high-legibility contrast across Light, Dark, and AMOLED themes).
  - Spacing: `4.dp` vertical gap between the stacked bar and the percentage row (`docs/design_guidelines.md` Section 5.2).

## 3. Atomic Implementation Steps
1. **Step 1 — Enhance `ZoneDistributionStackedBarMath` & `ZoneDistributionStackedBar` (`ZoneDistributionStackedBar.kt`)**:
   - Add `formatSegmentPercentage`, `minRequiredSegmentWidthDp`, and `canFitPercentageLabel` to `ZoneDistributionStackedBarMath`.
   - Render the aligned `Row` of `BoxWithConstraints(modifier = Modifier.weight(entry.percentage), contentAlignment = Alignment.Center)` and `1.dp` `Spacer`s directly below the stacked bar `Box` when `activeEntries.isNotEmpty()`.
2. **Step 2 — Expand Unit & Contract Tests (`ZoneDistributionStackedBarTest.kt`)**:
   - Add `testFormatSegmentPercentage_roundsToNearestWholePercent` and `testCanFitPercentageLabel_respectsMinimumWidthThreshold`.
   - Update `testStackedBar_componentSourceContract` to verify `BoxWithConstraints`, `ZoneDistributionStackedBarMath.formatSegmentPercentage(entry.percentage)`, `ZoneDistributionStackedBarMath.canFitPercentageLabel`, `MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, and `didOverflowWidth`.
   - Run targeted tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*"`.

## 4. Non-Negotiable Invariants & Rollback Safety
- `showLegend: Boolean = false` default parameter and conditional `FlowRow` legend rendering when `showLegend == true` (`REQ-UI-387` / `TST-UI-350`) MUST remain intact.
- Proportional `Modifier.weight(entry.percentage)`, `1.dp` inter-segment dividers, and `RoundedCornerShape(6.dp)` with `0.5.dp` border (`REQ-UI-372` / `TST-UI-335`) MUST remain intact.
- Single-line non-wrapping text (`maxLines = 1`, `softWrap = false`, Rule 38) MUST be enforced.
