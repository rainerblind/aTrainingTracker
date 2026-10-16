# Stage 2 Requirement & Test Specification: ATT-3853 — Show time percentage below each zone segment in ZoneDistributionStackedBar when sufficient horizontal space is available

## 1. Formal Requirement Specification (`REQ-UI-398`)

### `REQ-UI-398`: Space-Aware Per-Segment Percentage Labels Below `ZoneDistributionStackedBar`
The system SHALL render the formatted time percentage (e.g. `24%`) centered directly beneath each active zone segment in `ZoneDistributionStackedBar.kt` if and only if that segment's horizontal width is sufficient to fit the single-line percentage label without horizontal clipping or overlapping (ATT-3853, complementing `REQ-UI-372` and `REQ-UI-387`):
1. **Pure Percentage Formatting & Width-Threshold Calculation (`REQ-UI-398.1`)**:
   - `ZoneDistributionStackedBarMath` SHALL provide `formatSegmentPercentage(percentage: Float, locale: Locale = Locale.getDefault()): String` returning `String.format(locale, "%.0f%%", percentage)`.
   - `ZoneDistributionStackedBarMath` SHALL provide `minRequiredSegmentWidthDp(labelText: String, charWidthDp: Float = 6.5f, horizontalPaddingDp: Float = 4f): Float` returning `labelText.length * charWidthDp + horizontalPaddingDp` and `canFitPercentageLabel(segmentWidthDp: Float, labelText: String, charWidthDp: Float = 6.5f, horizontalPaddingDp: Float = 4f): Boolean` returning `labelText.isNotEmpty() && segmentWidthDp >= minRequiredSegmentWidthDp(labelText, charWidthDp, horizontalPaddingDp)`.
2. **Aligned Weighted Segment Percentage Row (`REQ-UI-398.2`)**:
   - In `ZoneDistributionStackedBar` (`ZoneDistributionStackedBar.kt`), when `activeEntries.isNotEmpty()`, the system SHALL render a horizontal `Row(modifier = Modifier.fillMaxWidth())` directly beneath the `barHeight` stacked bar `Box` whose child cells iterate over `activeEntries` with the exact same `Modifier.weight(entry.percentage)` and `1.dp` inter-segment `Spacer`s as the colored bar segments above.
   - Inside each weighted cell (`BoxWithConstraints(modifier = Modifier.weight(entry.percentage), contentAlignment = Alignment.Center)`), the system SHALL format `val label = ZoneDistributionStackedBarMath.formatSegmentPercentage(entry.percentage)` and render a single-line `Text` (`style = MaterialTheme.typography.labelSmall`, `color = MaterialTheme.colorScheme.onSurfaceVariant`, `maxLines = 1`, `softWrap = false`) centered beneath the segment ONLY when `ZoneDistributionStackedBarMath.canFitPercentageLabel(maxWidth.value, label)` is `true` and `!didOverflowWidth` (`onTextLayout`).
   - Narrow segments whose allocated width is below the required threshold (or whose rendered text overflows horizontally) SHALL cleanly omit their percentage label.

### Acceptance Criteria (Given-When-Then)
- **Given** an active zone segment with `percentage = 24f` (`"24%"`, 3 chars -> min width `23.5dp`),
  **When** evaluated at `segmentWidthDp = 40f` vs. `segmentWidthDp = 15f`,
  **Then** `ZoneDistributionStackedBarMath.canFitPercentageLabel` returns `true` for `40f` (rendering `"24%"` centered beneath the segment) and `false` for `15f` (omitting the label).
- **Given** `ZoneDistributionStackedBar` with multiple active zones,
  **When** rendered,
  **Then** the percentage label row aligns 1:1 with the bar segments above via matching `Modifier.weight(entry.percentage)` and `1.dp` inter-segment spacers.

---

## 2. Test Specification (`TST-UI-361`)

### `TST-UI-361`: `ZoneDistributionStackedBar` Per-Segment Percentage Formatting, Width Threshold & Source Contract Verification Suite
1. **Pure Percentage Formatting & Width-Threshold Unit Tests (`ZoneDistributionStackedBarTest.kt`)**:
   - `testFormatSegmentPercentage_roundsToNearestWholePercent`: Verify `ZoneDistributionStackedBarMath.formatSegmentPercentage` formats `0f`, `5.2f`, `24.4f`, `52.6f`, and `100f` as `"0%"`, `"5%"`, `"24%"`, `"53%"`, and `"100%"`.
   - `testCanFitPercentageLabel_respectsMinimumWidthThreshold`: Verify `ZoneDistributionStackedBarMath.minRequiredSegmentWidthDp` and `canFitPercentageLabel` return `true` when `segmentWidthDp >= labelText.length * 6.5f + 4f` (`"5%"` >= `17.0f`, `"24%"` >= `23.5f`, `"100%"` >= `30.0f`) and `false` when `segmentWidthDp` is below the threshold or `labelText` is empty.
2. **Component Source Contract & 9-Language Localization Parity Audit (`ZoneDistributionStackedBarTest.kt`, `ZoneDistributionStackedBarLocalizationTest.kt`)**:
   - Verify `ZoneDistributionStackedBar.kt` uses `BoxWithConstraints`, `Modifier.weight(entry.percentage)`, `ZoneDistributionStackedBarMath.formatSegmentPercentage(entry.percentage)`, `ZoneDistributionStackedBarMath.canFitPercentageLabel`, `MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, and `didOverflowWidth` overflow guard.
   - Verify `ZoneDistributionStackedBarLocalizationTest` passes across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions.

---

## 3. Bidirectional Traceability Matrix

| Requirement ID | Requirement Summary | Test ID | Verification Artifact |
| :--- | :--- | :--- | :--- |
| `REQ-UI-398.1` | Pure percentage formatting and minimum segment width threshold math | `TST-UI-361` | `ZoneDistributionStackedBarTest.kt` |
| `REQ-UI-398.2` | Aligned weighted segment percentage row with `BoxWithConstraints` and `didOverflowWidth` guard | `TST-UI-361` | `ZoneDistributionStackedBarTest.kt`, `ZoneDistributionStackedBarLocalizationTest.kt` |
