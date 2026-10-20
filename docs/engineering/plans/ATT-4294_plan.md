# Stage 3: Implementation Plan - ATT-4294: [Medium] Support all 7 cycling power zones (Z1–Z7) in Power Histogram, TelemetryMetricGraph threshold lines, and Dark Mode chart rendering

**Ticket**: [ATT-4294](https://rainerblind.atlassian.net/browse/ATT-4294)  
**Sub-task**: [ATT-4312](https://rainerblind.atlassian.net/browse/ATT-4312) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-420` (`REQ-UI-420.1`–`REQ-UI-420.5`)  
**Test Mapping**: `TST-UI-383` (`TST-UI-383.1`–`TST-UI-383.5`)  
**Branch**: `feature/ATT-4294`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

Cycling power in **aTrainingTracker** follows Andrew Coggan's standard 7-zone training model:
* **Zone 1 (Active Recovery)**: $< 55\%$ FTP (`TTColor.Zone1`, Gray `#9E9E9E`)
* **Zone 2 (Endurance)**: $56–75\%$ FTP (`TTColor.Zone2`, Blue `#2196F3`)
* **Zone 3 (Tempo)**: $76–90\%$ FTP (`TTColor.Zone3`, Green `#4CAF50`)
* **Zone 4 (Lactate Threshold)**: $91–105\%$ FTP (`TTColor.Zone4`, Yellow `#FFEB3B`)
* **Zone 5 (VO2 Max)**: $106–120\%$ FTP (`TTColor.Zone5`, Purple/Dark Violet `#9400D3`)
* **Zone 6 (Anaerobic Capacity)**: $121–150\%$ FTP (`TTColor.Zone6`, Warm Earthy Brown `#8B5A2B`, REQ-UI-392)
* **Zone 7 (Neuromuscular Power)**: $> 150\%$ FTP (`TTColor.Zone7`, Pure Black `#000000`)

While the athlete settings datastore (`SettingsDataStore.ZoneType.PWR_BIKE`), domain thresholds (`PowerZoneThresholds`), and distribution calculations (`ZoneDistributionCalculator.calculatePowerDistribution` returning 7 entries) support all 7 zones, forensic analysis in Stage 1 (`docs/engineering/analysis/ATT-4294_analysis.md`) identified three critical defects in the post-workout visual analytics pipeline:

1. **Power Histogram Clamped to 5 Zones (`TelemetryHistogramCalculator.kt`)**:
   - `calculatePowerHistogram` maps samples across 7 zones (`0..6`), but `ZONE_COLORS` only defines 5 colors (`TTColor.Zone1`..`TTColor.Zone5`).
   - In `calculateHistogram`, the zone index is clamped via `.coerceIn(0, 4)`. Consequently, all bins in Zone 6 and Zone 7 are forcibly colored with `TTColor.Zone5` (`#9400D3`) and labeled `"Z5"`.
   - In `calculatePowerHistogram`, the folded rightmost overflow bar (`REQ-UI-419`) hardcodes `zoneIndex = 5` and `color = TTColor.Zone5`, rather than dynamically reflecting the peak zone in the sprint tail.

2. **Missing Z5/Z6 and Z6/Z7 Threshold Lines in Power Graph (`TelemetryMetricGraph.kt`)**:
   - `TelemetryMetricGraph.kt` calculates 7 background zone bands via `calculatePowerZoneBands`, but in the `thresholdDashes` remember block for `POWER`, it inadvertently calls the 4-threshold helper `TelemetryZoneMath.calculateThresholdDashes(it.z1Max, it.z2Max, it.z3Max, it.z4Max, dataMin, dataMax)` instead of the dedicated 6-threshold helper `TelemetryZoneMath.calculatePowerThresholdDashes(it, dataMin, dataMax)`.
   - As a result, the horizontal dashed boundary lines for `z5Max` (Z5/Z6 boundary, 120% FTP) and `z6Max` (Z6/Z7 boundary, 150% FTP) are absent from the power graph.

3. **Missing Dark Mode Contrast for `TTColor.Zone7` (`#000000` Pure Black)**:
   - `TTColor.Zone7` is defined as `Color(0xFF000000)` (Pure Black).
   - In `TelemetryHistogramChart.kt`, `ZoneDistributionColumnChart.kt`, and `ZoneDistributionStackedBar.kt`, `Zone7` bars, canvas round-rects, and legend swatches lack a contrast outline on dark backgrounds, causing black elements to blend into the container surface.
   - In `TelemetryHistogramChart.kt` header readout, the text color for selected Zone 7 bins renders with `color = selectedBin.color` (`#000000`), yielding unreadable black text on dark surfaces.
   - In `ZoneDistributionModels.kt`, KDoc comments state `// 1..5` rather than documenting `1..7` for cycling power.

---

## 2. Traceability & Requirements Mapping

* **Primary Requirement**: `REQ-UI-420` (*Support all 7 cycling power zones (Z1–Z7) in Power Histogram, TelemetryMetricGraph threshold lines, and Dark Mode chart rendering*)
  - `REQ-UI-420.1`: 7-Zone Power Histogram Binning in `TelemetryHistogramCalculator.kt` (expand `ZONE_COLORS` to 7 entries, clamp to `0..ZONE_COLORS.lastIndex`, dynamic overflow bar zone)
  - `REQ-UI-420.2`: 6-Threshold Power Dashed Lines in `TelemetryMetricGraph.kt` (invoke `TelemetryZoneMath.calculatePowerThresholdDashes`)
  - `REQ-UI-420.3`: Dark Mode Contrast & Accessibility for `TTColor.Zone7` (outline stroke on canvas, border on column/bar items, `onSurface` readout text)
  - `REQ-UI-420.4`: `ZoneDistributionModels.kt` Documentation & KDoc Parity (document 1..7 zones for power)
  - `REQ-UI-420.5`: Preservation of System Invariants (HR strictly 5 zones, duration and percentage conservation)

* **Test Mapping**: `TST-UI-383` (*Test Specification for ATT-4294*)
  - `TST-UI-383.1`: 7-Zone Power Classification & Histogram Binning Unit Test (`TelemetryHistogramCalculatorTest.kt`) $\implies$ Maps to `REQ-UI-420.1`, `REQ-UI-420.5`
  - `TST-UI-383.2`: 6-Threshold Power Dashed Lines Unit Test (`TelemetryZoneMathTest.kt`) $\implies$ Maps to `REQ-UI-420.2`, `REQ-UI-420.5`
  - `TST-UI-383.3`: Dark Mode Contrast, Readability & Model KDoc Parity Contract Test (`TelemetryHistogramCardTest.kt`) $\implies$ Maps to `REQ-UI-420.3`, `REQ-UI-420.4`
  - `TST-UI-383.4`: 9-Language Localization & String Resource Audit (`TranslationParityTest.kt`) $\implies$ Maps to `REQ-UI-420`, `REQ-UI-106`
  - `TST-UI-383.5`: Clean-Room Regression Suite Execution (`./gradlew testDebugUnitTest`) $\implies$ Maps to `REQ-PRO-001`, `REQ-UI-420`

---

## 3. System Invariants & Preserved Behavior

1. **Heart Rate 5-Zone Invariance**: Heart rate telemetry strictly remains 5 zones (`Z1`–`Z5`), using `ZONE_COLORS[0..4]` and exactly 4 threshold boundary dashed lines (`z1Max`..`z4Max`).
2. **Mathematical Conservation of Duration & Percentage**: For all histograms, sum of bin durations equals `totalActiveTimeSec`, and percentages sum to $100.0\% \pm 0.1\%$.
3. **Pure Function & Thread Dispatcher Affinity**: `TelemetryHistogramCalculator.kt` and `TelemetryZoneMath.kt` remain stateless pure calculation engines with zero database or lifecycle side effects.
4. **WCAG 2.1 AA Contrast Compliance**: Zone 7 Pure Black (`#000000`) and Zone 6 Warm Earthy Brown (`#8B5A2B`) maintain $\ge 3.0:1$ non-text contrast against dark surfaces via `outlineVariant` borders and $\ge 4.5:1$ text contrast in readouts via `onSurface`.
5. **No Intermediate Commit (Rule 44)**: Stage 3 and Stage 4 changes remain uncommitted on branch `feature/ATT-4294` until the single atomic squash commit at Stage 5.
6. **Subtask Self-Sufficiency & Direct Completion (Rule 3)**: Subtask `ATT-4312` transitions autonomously to `Erledigt` via transition `freigabe` upon passing Gate 3 audit, synchronously advancing parent `ATT-4294` to `Implementation`.
7. **Parent Human Gate Invariance (Rule 3)**: Moving parent ticket `ATT-4294` to `Erledigt` is reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TelemetryHistogramCalculator.kt` (UI / Analytics Layer)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculator.kt`
* **Changes**:
  1. Expand private `ZONE_COLORS` array from 5 to 7 colors:
     ```kotlin
     private val ZONE_COLORS = arrayOf(
         TTColor.Zone1,
         TTColor.Zone2,
         TTColor.Zone3,
         TTColor.Zone4,
         TTColor.Zone5,
         TTColor.Zone6,
         TTColor.Zone7
     )
     ```
  2. In `calculateHistogram` (line 295), change zone index clamping:
     ```kotlin
     val zoneIdx = getZoneForValue(midpoint).coerceIn(0, ZONE_COLORS.lastIndex)
     ```
     This allows power values in Zone 6 (`zoneIdx = 5`) to map to `zoneIndex = 6`, `color = TTColor.Zone6`, and Zone 7 (`zoneIdx = 6`) to map to `zoneIndex = 7`, `color = TTColor.Zone7`.
  3. In `calculatePowerHistogram` (lines 130–141), compute peak tail zone for the folded `overflowBin`:
     ```kotlin
     val peakTailBin = tailBins.maxByOrNull { it.zoneIndex }
     val overflowBin = TelemetryHistogramBin(
         binIndex = regularBins.size,
         rangeMin = tailCutoff,
         rangeMax = linearData.dataMax,
         durationSec = tailDuration,
         percentage = if (totalDuration > 0L) (tailDuration.toFloat() / totalDuration) * 100f else 0f,
         zoneIndex = peakTailBin?.zoneIndex ?: 7,
         color = peakTailBin?.color ?: TTColor.Zone7,
         isOverflow = true,
         isReturn = false
     )
     ```
  4. Ensure `calculateHeartRateHistogram` continues returning `0..4` from its lambda, preserving 5-zone behavior for heart rate.

### Component 2: `TelemetryMetricGraph.kt` (UI / Compose Graph Layer)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* **Changes**:
  - In `thresholdDashes` remember block (lines 222–232), update the `TelemetryMetricType.POWER` branch to call `TelemetryZoneMath.calculatePowerThresholdDashes(it, dataMin, dataMax)`:
    ```kotlin
    val thresholdDashes = remember(metricType, effectiveHrThresholds, effectivePowerThresholds, dataMin, dataMax) {
        when (metricType) {
            TelemetryMetricType.HEART_RATE -> effectiveHrThresholds?.let {
                TelemetryZoneMath.calculateThresholdDashes(it.z1Max, it.z2Max, it.z3Max, it.z4Max, dataMin, dataMax)
            } ?: emptyList()
            TelemetryMetricType.POWER -> effectivePowerThresholds?.let {
                TelemetryZoneMath.calculatePowerThresholdDashes(it, dataMin, dataMax)
            } ?: emptyList()
            else -> emptyList()
        }
    }
    ```
  - This ensures all 6 power thresholds (`z1Max` through `z6Max`) within `dataMin..dataMax` render dashed horizontal guidelines, matching the 7 background bands from `calculatePowerZoneBands`.

### Component 3: Dark Mode Contrast & Accessibility Enhancements (UI Layer)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramChart.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionColumnChart.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBar.kt`
* **Changes**:
  1. **`TelemetryHistogramChart.kt`**:
     - Header readout label text: For `selectedBin.color == TTColor.Zone7` (or `Zone6`), display text in `MaterialTheme.colorScheme.onSurface` (or `Color.White`) instead of pure black:
       ```kotlin
       val readoutColor = if (selectedBin.color == TTColor.Zone7) {
           MaterialTheme.colorScheme.onSurface
       } else {
           selectedBin.color
       }
       ```
       Applied to range title and `"Z${selectedBin.zoneIndex}"` label.
     - Canvas bin drawing: When `!isSelected && (bin.color == TTColor.Zone7 || bin.color == TTColor.Zone6)`, draw an outline stroke around the bar:
       ```kotlin
       if (!isSelected && (bin.color == TTColor.Zone7 || bin.color == TTColor.Zone6)) {
           drawRoundRect(
               color = outlineVariantColor,
               topLeft = Offset(left, top),
               size = Size(barWidthPx, barHeightPx),
               cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
               style = Stroke(width = 0.75.dp.toPx())
           )
       }
       ```
       (When `isSelected`, `Color.White` stroke is already drawn).
  2. **`ZoneDistributionColumnChart.kt`**:
     - In X-axis zone dot badges (line 119):
       ```kotlin
       Box(
           modifier = Modifier
               .size(7.dp)
               .clip(CircleShape)
               .then(
                   if (entry.color == TTColor.Zone7 || entry.color == TTColor.Zone6) {
                       Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                   } else Modifier
               )
               .background(entry.color)
       )
       ```
     - In `ZoneColumnItem` active bar (line 190):
       ```kotlin
       val barShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
       Box(
           modifier = Modifier
               .fillMaxWidth(0.55f)
               .fillMaxHeight(heightFraction)
               .defaultMinSize(minHeight = 4.dp)
               .clip(barShape)
               .then(
                   if (entry.color == TTColor.Zone7 || entry.color == TTColor.Zone6) {
                       Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, barShape)
                   } else Modifier
               )
               .background(entry.color)
       )
       ```
  3. **`ZoneDistributionStackedBar.kt`**:
     - In continuous horizontal bar segments (line 168):
       ```kotlin
       Box(
           modifier = Modifier
               .weight(entry.percentage)
               .fillMaxHeight()
               .then(
                   if (entry.color == TTColor.Zone7 || entry.color == TTColor.Zone6) {
                       Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                   } else Modifier
               )
               .background(entry.color)
       )
       ```
       (Note: `StackedBarLegendItem` already includes `swatchBorder = outlineBorder`).

### Component 4: `ZoneDistributionModels.kt` (KDoc Parity)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionModels.kt`
* **Changes**:
  - Line 59: `val zoneIndex: Int, // 1..5 for HR/Pace, 1..7 for Cycling Power`
  - Line 78: `val zoneIndex: Int, // 1..5 for HR, 1..7 for Cycling Power`
  - Line 121: Update class KDoc to document 7 zones for Cycling Power and 5 zones for Heart Rate and Pace.

---

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `ElevationProfile.kt` (swatch outline at line 1174: `hasOutline = color == TTColor.Zone6 || color == TTColor.Zone7`, `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`), `PowerZoneDistributionCard.kt`, `TelemetryHistogramChart.kt`.
* **Reused components**: Material 3 theme tokens (`MaterialTheme.colorScheme.outlineVariant`, `MaterialTheme.colorScheme.onSurface`, `MaterialTheme.typography.labelSmall`, `labelMedium`), `TTColor.Zone1`..`TTColor.Zone7`.
* **Theme tokens**:
  - Shapes: `CircleShape`, `RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)`, `CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())`
  - Spacing: `0.5.dp`–`1.dp` borders, `4.dp` padding
  - Colors: `MaterialTheme.colorScheme.outlineVariant`, `MaterialTheme.colorScheme.onSurface`, `TTColor.Zone6` (`#8B5A2B`), `TTColor.Zone7` (`#000000`)
* **New one-off styles & justification**: None. All borders and colors reuse established design tokens (`outlineVariant`, `onSurface`, canonical `TTColor`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Expand Palette and Clamping in `TelemetryHistogramCalculator.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculator.kt`
* **Changes**:
  - Expand `ZONE_COLORS` to include `TTColor.Zone6` and `TTColor.Zone7`.
  - Update `calculateHistogram` zone index clamping from `.coerceIn(0, 4)` to `.coerceIn(0, ZONE_COLORS.lastIndex)`.
  - Update `calculatePowerHistogram` folded `overflowBin` to dynamically use `peakTailBin?.zoneIndex ?: 7` and `peakTailBin?.color ?: TTColor.Zone7`.
* **Traceability**: `REQ-UI-420.1`

### Step 2: Wire 6-Threshold Dashes in `TelemetryMetricGraph.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* **Changes**:
  - In `thresholdDashes` remember block, call `TelemetryZoneMath.calculatePowerThresholdDashes(it, dataMin, dataMax)` for `TelemetryMetricType.POWER`.
* **Traceability**: `REQ-UI-420.2`

### Step 3: Dark Mode Contrast Outlines & Accessible Readout Text
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramChart.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionColumnChart.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBar.kt`
* **Changes**:
  - In `TelemetryHistogramChart.kt`, use `onSurface` readout text color for Zone 7 and draw `outlineVariant` canvas outline stroke on unselected Zone 6 & 7 bars.
  - In `ZoneDistributionColumnChart.kt`, apply `outlineVariant` border to Zone 6 & 7 swatches and active bars.
  - In `ZoneDistributionStackedBar.kt`, apply `outlineVariant` border to Zone 6 & 7 segments in horizontal bar.
* **Traceability**: `REQ-UI-420.3`

### Step 4: KDoc Documentation Parity in `ZoneDistributionModels.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionModels.kt`
* **Changes**:
  - Update KDoc comments at lines 59, 78, and 121 to document 1..7 zones for cycling power.
* **Traceability**: `REQ-UI-420.4`

### Step 5: Unit Tests for 7-Zone Histogram & 6-Threshold Dashes
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMathTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCardTest.kt`
* **Changes**:
  - In `TelemetryHistogramCalculatorTest.kt`:
    * Add `testCalculatePowerHistogram_mapsZone6AndZone7Correctly()` verifying wattage in Zone 6 (380 W) maps to `zoneIndex = 6` / `TTColor.Zone6` and Zone 7 (520 W) maps to `zoneIndex = 7` / `TTColor.Zone7`.
    * Add `testCalculatePowerHistogram_overflowBinReflectsPeakSprintZone()` verifying folded overflow bin reflects peak tail zone (Z6 or Z7).
    * Verify Heart Rate calculation remains strictly 5-zone (`Z1`–`Z5`).
  - In `TelemetryZoneMathTest.kt`:
    * Add `testCalculatePowerThresholdDashes_returnsAll6Thresholds()` verifying `calculatePowerThresholdDashes` produces all 6 dashed guidelines within visible data range and correctly bounds them.
  - In `TelemetryHistogramCardTest.kt`:
    * Add `testDarkModeContrast_zone7ReadoutAndOutlineMeetWcagThresholds()` verifying non-text and text contrast invariants.
* **Traceability**: `TST-UI-383.1`, `TST-UI-383.2`, `TST-UI-383.3`

### Step 6: Targeted Unit Test Verification
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCalculatorTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryZoneMathTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCardTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionCalculatorTest"
  ```
* **Expected Result**: All targeted tests pass cleanly within ~10–15s (`BUILD SUCCESSFUL`).
* **Traceability**: `TST-UI-383.1`–`TST-UI-383.4`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit tests during Stage 4 construction (~10s).
  - Localization parity test `TranslationParityTest` verifying 9-locale integrity (`TST-UI-383.4`).
  - Full clean-room regression suite `./gradlew testDebugUnitTest` in Stage 5 (`TST-UI-383.5`).
* **Rollback**:
  - Branch isolation: All modifications are isolated to `feature/ATT-4294`.
  - In the event of an unresolvable defect or regression, the branch can be cleanly reset (`git reset --hard HEAD` or `git checkout sprint/2026-43.1`) without impacting `develop` or the active sprint branch.
