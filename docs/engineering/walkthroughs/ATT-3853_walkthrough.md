# Stage 5 Verification & Walkthrough: ATT-3853 — Show time percentage below each zone segment in ZoneDistributionStackedBar when sufficient horizontal space is available

## 1. Summary of Changes (`REQ-UI-398`)
- **`ZoneDistributionStackedBarMath` (`ZoneDistributionStackedBar.kt`)**:
  - Added `formatSegmentPercentage(percentage: Float, locale: Locale = Locale.getDefault()): String` returning `String.format(locale, "%.0f%%", percentage)`.
  - Added `minRequiredSegmentWidthDp(labelText: String, charWidthDp: Float = 6.5f, horizontalPaddingDp: Float = 4f): Float` and `canFitPercentageLabel(segmentWidthDp: Float, labelText: String, ...)`.
- **`ZoneDistributionStackedBar` (`ZoneDistributionStackedBar.kt`)**:
  - Directly beneath the `barHeight` (`16.dp`) stacked bar `Box` inside a `4.dp`-spaced `Column`, rendered an aligned horizontal `Row(modifier = Modifier.fillMaxWidth())` when `activeEntries.isNotEmpty()`.
  - Each active zone segment cell uses `BoxWithConstraints(modifier = Modifier.weight(entry.percentage), contentAlignment = Alignment.Center)` and `1.dp` inter-segment `Spacer`s matching the colored bar above.
  - Inside each cell, the formatted percentage (`MaterialTheme.typography.labelSmall`, `color = MaterialTheme.colorScheme.onSurfaceVariant`, `maxLines = 1`, `softWrap = false`) is centered beneath the segment if and only if `ZoneDistributionStackedBarMath.canFitPercentageLabel(maxWidth.value, label)` is true and `!didOverflowWidth` (`onTextLayout`).

## 2. Verification & Regression Suite Results (`TST-UI-361`)
1. **Targeted Package Tests (`com.atrainingtracker.trainingtracker.ui.aftermath.zones.*`)**:
   - `ZoneDistributionStackedBarTest`: Verified `testFormatSegmentPercentage_roundsToNearestWholePercent`, `testCanFitPercentageLabel_respectsMinimumWidthThreshold`, `testStackedBar_componentSourceContract`, `testActiveEntries_filtersZeroDurationOrPercentage`, `testIsAllZeroDuration_handlesEmptyAndZeroDistributions`, `testStackedBar_sevenZonePowerDatasetParity`, and `testZoneDistributionCards_stackedBarModeOmitsDetailedBreakdownList` — **100% PASS**.
   - `ZoneDistributionStackedBarLocalizationTest`: Verified 9-language localization parity across all 9 locales — **100% PASS**.
2. **Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)**:
   - Executed full unit test suite across the entire project — **100% PASS (`0` failures)**.
