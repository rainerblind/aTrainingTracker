# Stage 2: Requirement & Test Specification - ATT-4294: [Medium] Support all 7 cycling power zones (Z1–Z7) in Power Histogram, TelemetryMetricGraph threshold lines, and Dark Mode chart rendering

**Ticket**: [ATT-4294](https://rainerblind.atlassian.net/browse/ATT-4294)  
**Sub-task**: [ATT-4311](https://rainerblind.atlassian.net/browse/ATT-4311) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-420` (*Support all 7 cycling power zones (Z1–Z7) in Power Histogram, TelemetryMetricGraph threshold lines, and Dark Mode chart rendering*)  
**Test Spec ID**: `TST-UI-383`  
**Branch**: `feature/ATT-4294`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-UI-420)

### 1.1 Problem Statement & Rationale

Cycling power in **aTrainingTracker** is built upon Andrew Coggan's standard 7-zone training model:
* **Zone 1 (Active Recovery)**: $< 55\%$ FTP (`TTColor.Zone1`, Gray `#9E9E9E`)
* **Zone 2 (Endurance)**: $56–75\%$ FTP (`TTColor.Zone2`, Blue `#2196F3`)
* **Zone 3 (Tempo)**: $76–90\%$ FTP (`TTColor.Zone3`, Green `#4CAF50`)
* **Zone 4 (Lactate Threshold)**: $91–105\%$ FTP (`TTColor.Zone4`, Yellow `#FFEB3B`)
* **Zone 5 (VO2 Max)**: $106–120\%$ FTP (`TTColor.Zone5`, Purple/Dark Violet `#9400D3`)
* **Zone 6 (Anaerobic Capacity)**: $121–150\%$ FTP (`TTColor.Zone6`, Warm Earthy Brown `#8B5A2B`, REQ-UI-392)
* **Zone 7 (Neuromuscular Power)**: $> 150\%$ FTP (`TTColor.Zone7`, Pure Black `#000000`)

While the underlying domain model (`SettingsDataStore.ZoneType.PWR_BIKE`, `PowerZoneThresholds`) and aggregate distributions (`ZoneDistributionCalculator.calculatePowerDistribution`) support all 7 zones, forensic analysis in Stage 1 (`docs/engineering/analysis/ATT-4294_analysis.md`) uncovered three critical defects in the post-workout visual analytics pipeline:

1. **Power Histogram Clamped to 5 Zones (`TelemetryHistogramCalculator.kt`)**:
   - `calculatePowerHistogram` (lines 104–114) correctly evaluates power samples into 7 zones (`0..6` using `thresholds.z1Max..z6Max`).
   - However, `ZONE_COLORS` (lines 48–54) defines only 5 colors (`TTColor.Zone1`..`TTColor.Zone5`, omitting `Zone6` and `Zone7`).
   - Furthermore, `calculateHistogram` (line 295) hard-clamps the zone index: `val zoneIdx = getZoneForValue(midpoint).coerceIn(0, 4)`.
   - **Observable Defect**: High-wattage sprint bins in Zone 6 (Anaerobic) and Zone 7 (Neuromuscular Sprint) are forcibly assigned `zoneIndex = 5` and colored with `TTColor.Zone5` (`#9400D3` Purple). Bins exceeding `z5Max` never transition to Brown (`Z6`) or Black (`Z7`).
   - Additionally, the folded rightmost overflow bar introduced in `REQ-UI-419` (`calculatePowerHistogram`, lines 137–138) hardcodes `zoneIndex = 5` and `color = TTColor.Zone5`, rather than dynamically reflecting the peak zone present in the folded sprint tail.

2. **Missing Z5/Z6 and Z6/Z7 Threshold Dashes in Power Graph (`TelemetryMetricGraph.kt`)**:
   - In `TelemetryMetricGraph.kt` (line 216), `zoneBands` calls `TelemetryZoneMath.calculatePowerZoneBands(it, dataMin, dataMax)`, which correctly calculates all 7 background color bands.
   - However, at line 228, `thresholdDashes` invokes the 4-threshold heart-rate helper:
     `TelemetryZoneMath.calculateThresholdDashes(it.z1Max, it.z2Max, it.z3Max, it.z4Max, dataMin, dataMax)`
     instead of `TelemetryZoneMath.calculatePowerThresholdDashes(it, dataMin, dataMax)`.
   - The dedicated 6-threshold power helper `calculatePowerThresholdDashes` already exists in `TelemetryZoneMath.kt:161` but was never connected.
   - **Observable Defect**: Horizontal dashed threshold boundary lines for `z5Max` (Z5/Z6 boundary, 120% FTP) and `z6Max` (Z6/Z7 boundary, 150% FTP) are completely missing from the power time-series chart.

3. **Missing Dark Mode Contrast for `TTColor.Zone7` (`#000000` Pure Black) Across Zone Charts**:
   - `TTColor.Zone7` is defined as `Color(0xFF000000)` (Pure Black).
   - In `ElevationProfile.kt:1174`, `Zone6` and `Zone7` swatches receive a subtle outline (`BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`) so they remain visible on dark surfaces.
   - In `TelemetryHistogramChart.kt`, `ZoneDistributionColumnChart.kt`, and `ZoneDistributionStackedBar.kt`, `Zone7` bars, canvas round-rects, and legend swatches lack a contrast border. On AMOLED and Dark themes, pure black bars blend invisibly into the dark surface (`#121212` / `#1E1E1E`).
   - Additionally, when a Zone 7 bin is selected in `TelemetryHistogramChart.kt`, the header readout text renders with `color = selectedBin.color` (`#000000`), producing unreadable black text on a dark background (1:1 contrast ratio).
   - In `ZoneDistributionModels.kt`, KDoc comments state `// 1..5` rather than documenting `1..7` for cycling power.

---

### 1.2 Functional & Architectural Requirements

1. **7-Zone Power Histogram Binning in `TelemetryHistogramCalculator.kt` (`REQ-UI-420.1`)**:
   - The system SHALL expand the static zone color palette `ZONE_COLORS` to include all 7 standard zone colors:
     `TTColor.Zone1` (Gray), `TTColor.Zone2` (Blue), `TTColor.Zone3` (Green), `TTColor.Zone4` (Yellow), `TTColor.Zone5` (Purple), `TTColor.Zone6` (Warm Earthy Brown `#8B5A2B`), and `TTColor.Zone7` (Pure Black `#000000`).
   - In `calculateHistogram`, the zone index clamp SHALL be updated from `coerceIn(0, 4)` to `coerceIn(0, ZONE_COLORS.lastIndex)` (`0..6`), mapping bins with midpoint $> z5Max$ to Zone 6 (`zoneIndex = 6`, `color = TTColor.Zone6`) and midpoint $> z6Max$ to Zone 7 (`zoneIndex = 7`, `color = TTColor.Zone7`).
   - In `calculatePowerHistogram`, the rightmost folded `overflowBin` (`REQ-UI-419`) SHALL dynamically reflect the highest zone present in `tailBins` (`tailBins.maxByOrNull { it.zoneIndex }?.zoneIndex ?: 7`, `color = peakTailBin?.color ?: TTColor.Zone7`) rather than hardcoding `zoneIndex = 5` and `TTColor.Zone5`.
   - `calculateHeartRateHistogram` SHALL preserve strict 5-zone mapping (`getZoneForValue` returning `0..4`, `zoneIndex` 1..5, `TTColor.Zone1..Zone5`).

2. **6-Threshold Power Dashed Lines in `TelemetryMetricGraph.kt` (`REQ-UI-420.2`)**:
   - In `TelemetryMetricGraph.kt`, the `thresholdDashes` remember block for `TelemetryMetricType.POWER` SHALL call `TelemetryZoneMath.calculatePowerThresholdDashes(it, dataMin, dataMax)`, rendering horizontal dashed boundary lines for all 6 power thresholds (`z1Max`, `z2Max`, `z3Max`, `z4Max`, `z5Max`, `z6Max`) that fall within `dataMin..dataMax`.
   - `TelemetryMetricType.HEART_RATE` SHALL retain `TelemetryZoneMath.calculateThresholdDashes(it.z1Max, it.z2Max, it.z3Max, it.z4Max, dataMin, dataMax)` with exactly 4 dashed lines.

3. **Dark Mode Contrast and Accessibility for `TTColor.Zone7` (`REQ-UI-420.3`)**:
   - In `TelemetryHistogramChart.kt`, when `isSystemInDarkTheme()` is active (or in dark mode) and a bin has color `TTColor.Zone7` (Pure Black) or `TTColor.Zone6` (Brown), the canvas drawing routine SHALL draw an outline stroke (1.dp, `color = MaterialTheme.colorScheme.outlineVariant`) around the bar so pure black bars remain clearly discernible against dark card surfaces.
   - In `TelemetryHistogramChart.kt`, the selected bin header readout label text SHALL NOT render pure black text on dark surfaces: if `isDark && (selectedBin.color == TTColor.Zone7 || selectedBin.color == TTColor.Zone6)`, the readout text color SHALL resolve to `MaterialTheme.colorScheme.onSurface` (or `Color.White`), maintaining $\ge 4.5:1$ WCAG contrast.
   - In `ZoneDistributionColumnChart.kt` and `ZoneDistributionStackedBar.kt`, active bars, stacked segments, and legend swatches with `TTColor.Zone7` or `TTColor.Zone6` SHALL receive a subtle outline border (0.5.dp, `MaterialTheme.colorScheme.outlineVariant`) on dark surfaces.

4. **ZoneDistributionModels Documentation & Parity (`REQ-UI-420.4`)**:
   - KDoc comments in `ZoneDistributionModels.kt` (lines 59, 78, 123) SHALL be updated to document 1..7 zones for cycling power and 1..5 zones for heart rate and pace.

5. **Preservation of System Invariants (`REQ-UI-420.5`)**:
   - Heart rate histograms and threshold lines remain strictly 5 zones.
   - Mathematical conservation of active duration and percentages (summing to $100.0\% \pm 0.1\%$) is strictly preserved.
   - Backward compatibility of `TelemetryHistogramBin` and `TelemetryHistogramData` constructors is preserved.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Zone 6 and Zone 7 Wattage Bin Classification**
  * *Given* cycling power telemetry with samples in Zone 6 (e.g. 380 W where `z5Max = 350 W`, `z6Max = 450 W`),
  * *When* `calculatePowerHistogram` evaluates the samples,
  * *Then* the corresponding histogram bin has `zoneIndex = 6` and `color = TTColor.Zone6` (Brown `#8B5A2B`).
  * *Given* cycling power telemetry with samples in Zone 7 (e.g. 600 W where `z6Max = 450 W`),
  * *When* `calculatePowerHistogram` evaluates the samples,
  * *Then* the corresponding histogram bin has `zoneIndex = 7` and `color = TTColor.Zone7` (Black `#000000`).

* **Criterion 2: Overflow Bar Zone Parity in Sprint Tail Folding**
  * *Given* an active cycling workout with tail folding where sprint tail samples reach Zone 7,
  * *When* `calculatePowerHistogram` builds the folded overflow bin,
  * *Then* `overflowBin.zoneIndex` equals 7 and `overflowBin.color` equals `TTColor.Zone7`.
  * *Given* a workout where sprint tail samples only reach Zone 6,
  * *When* `calculatePowerHistogram` builds the overflow bin,
  * *Then* `overflowBin.zoneIndex` equals 6 and `overflowBin.color` equals `TTColor.Zone6`.

* **Criterion 3: 6 Power Threshold Dashed Lines in TelemetryMetricGraph**
  * *Given* `TelemetryMetricGraph` rendering power telemetry with `PowerZoneThresholds` (`z1Max` through `z6Max`),
  * *When* `thresholdDashes` is remembered,
  * *Then* it invokes `calculatePowerThresholdDashes` and returns dashed lines for all 6 thresholds (`z1Max`..`z6Max`) within `dataMin..dataMax`.

* **Criterion 4: Dark Mode Outline for Zone 7 and Zone 6 Elements**
  * *Given* the app rendered in Dark or AMOLED theme,
  * *When* a Zone 7 (`#000000`) or Zone 6 (`#8B5A2B`) bin/bar is drawn in `TelemetryHistogramChart`, `ZoneDistributionColumnChart`, or `ZoneDistributionStackedBar`,
  * *Then* the element renders with an `outlineVariant` border ensuring clear visual distinction from the dark container background.

* **Criterion 5: Dark Mode Readable Readout Header for Zone 7**
  * *Given* a selected Zone 7 bin in `TelemetryHistogramChart` in Dark theme,
  * *When* the header readout renders,
  * *Then* the zone label text is displayed in `MaterialTheme.colorScheme.onSurface` (or `Color.White`) rather than pure black, ensuring $\ge 4.5:1$ contrast.

* **Criterion 6: Heart Rate 5-Zone Invariance**
  * *Given* heart rate telemetry samples,
  * *When* `calculateHeartRateHistogram` runs,
  * *Then* all bins have `zoneIndex` in 1..5 and `color` in `Zone1`..`Zone5`, and `TelemetryMetricGraph` draws exactly 4 dashed lines.

* **Criterion 7: Mathematical Duration and Percentage Conservation**
  * *Given* any power telemetry dataset evaluated by `calculatePowerHistogram`,
  * *When* bin durations and percentages are summed,
  * *Then* total duration equals active time and percentages sum to $100.0\% \pm 0.1\%$.

---

### 1.4 System Invariants

1. **Pure Mathematical Calculation & Zero Side Effects**:
   - `TelemetryHistogramCalculator.kt` and `TelemetryZoneMath.kt` are pure, stateless calculation engines with zero database or lifecycle side effects.
2. **Heart Rate 5-Zone Invariance**:
   - Heart rate telemetry strictly remains 5 zones (`Z1`–`Z5`) with unchanged colors and 4 threshold boundary lines.
3. **Mathematical Conservation of Duration & Percentage**:
   - Sum of bin durations equals `totalActiveTimeSec`, percentages sum to $100.0\% \pm 0.1\%$.
4. **WCAG 2.1 AA Contrast Compliance**:
   - All zone visuals (including Zone 7 Pure Black and Zone 6 Warm Earthy Brown) maintain $\ge 3.0:1$ non-text contrast against dark theme surfaces and $\ge 4.5:1$ text contrast in readouts.
5. **Parent Ticket Human Decision Gate (Rule 3)**:
   - Moving parent ticket ATT-4294 to `Erledigt` is an inviolable Human Decision Gate reserved exclusively for the human user during Ceremony 2 (Joint Review).

---

### 1.5 Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-420`, complementing `REQ-UI-419` (`ATT-4293`), `REQ-UI-372` (`ATT-1845`), and `REQ-UI-392` (`ATT-3696`).
   - Targets `TelemetryHistogramCalculator.kt`, `TelemetryMetricGraph.kt`, `TelemetryHistogramChart.kt`, `ZoneDistributionColumnChart.kt`, `ZoneDistributionStackedBar.kt`, and `ZoneDistributionModels.kt`.
2. **Historical Origin & Commit Trace**:
   - `REQ-UI-372` (`ATT-1845`, commit `7b14d9a2`): Introduced fine-grained frequency histograms with linear binning, originally designed for 5-zone Heart Rate and then adapted for Power with an initial 5-color array.
   - `REQ-UI-392` (`ATT-3696`, commit `0da639ba`): Refined `TTColor.Zone6` to `#8B5A2B` for $\ge 3.0:1$ WCAG contrast against `TTColor.Zone7` (`#000000`).
   - `REQ-UI-419` (`ATT-4293`, commit `5694bca5`): Introduced sprint tail folding, but inherited the existing 5-zone clamping in `TelemetryHistogramCalculator.kt`.
3. **Root Reason for Existing Formulation**:
   - When `TelemetryHistogramCalculator.kt` was ported from `ZoneDistributionCalculator.kt`, only `calculateHeartRateHistogram` was fully verified. Cycling power was given the same 5-element `ZONE_COLORS` array and `coerceIn(0, 4)` clamp as a temporary placeholder. In `TelemetryMetricGraph.kt`, the threshold line helper call was copy-pasted from the `HEART_RATE` branch.
4. **Preservation of Core Invariants**:
   - Heart rate histograms strictly retain 5 zones; power zone bands already support 7 zones; duration and percentage conservation is maintained; 100% clean-room test pass rate preserved.

---

## 2. Test Specification (TST-UI-383)

### Test Case 1: 7-Zone Power Classification & Histogram Binning Unit Test (`[TST-UI-383.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/telemetry/TelemetryHistogramCalculatorTest.kt`
* **Preconditions**:
  - Sample power thresholds: `z1Max = 150`, `z2Max = 200`, `z3Max = 250`, `z4Max = 300`, `z5Max = 350`, `z6Max = 450`.
* **Action**:
  - Evaluate samples with wattage in Zone 6 (e.g. 380 W) and Zone 7 (e.g. 520 W).
  - Evaluate tail-folded dataset where sprint tail reaches Zone 7 (e.g. 600 W).
  - Evaluate heart rate samples across 80–180 bpm.
* **Expected Result**:
  - Power bin at 380 W has `zoneIndex == 6` and `color == TTColor.Zone6` (`#8B5A2B`).
  - Power bin at 520 W has `zoneIndex == 7` and `color == TTColor.Zone7` (`#000000`).
  - Overflow bin has `zoneIndex == 7` and `color == TTColor.Zone7`.
  - Heart rate bins strictly have `zoneIndex in 1..5` and `color in Zone1..Zone5`.
  - Mathematical conservation: total duration equals sum of samples and percentages sum to $100.0\% \pm 0.1\%$.

### Test Case 2: 6-Threshold Power Dashed Lines Unit Test (`[TST-UI-383.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/telemetry/TelemetryZoneMathTest.kt`
* **Preconditions**:
  - `PowerZoneThresholds(z1Max = 150, z2Max = 200, z3Max = 250, z4Max = 300, z5Max = 350, z6Max = 450)`.
* **Action**:
  - Call `TelemetryZoneMath.calculatePowerThresholdDashes(thresholds, dataMin = 50.0, dataMax = 600.0)`.
  - Call `TelemetryZoneMath.calculatePowerThresholdDashes(thresholds, dataMin = 220.0, dataMax = 380.0)`.
  - Call `TelemetryZoneMath.calculateThresholdDashes(z1Max, z2Max, z3Max, z4Max, dataMin = 50.0, dataMax = 600.0)`.
* **Expected Result**:
  - Full range: returns exactly 6 values `[150.0, 200.0, 250.0, 300.0, 350.0, 450.0]`.
  - Bounded range: returns values within `(220.0, 380.0)` $\implies$ `[250.0, 300.0, 350.0]`.
  - Heart rate call: returns exactly 4 values `[z1Max, z2Max, z3Max, z4Max]`.

### Test Case 3: Dark Mode Contrast, Readability & Model KDoc Parity Contract Test (`[TST-UI-383.3]`)
* **Scope**: JVM Contract / Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/telemetry/TelemetryHistogramCardTest.kt`
* **Preconditions**:
  - Dark theme surface color `#121212`, `outlineVariant` color `#49454F`, `TTColor.Zone7` (`#000000`).
* **Action**:
  - Calculate WCAG 2.1 non-text contrast ratio between `outlineVariant` border and dark surface.
  - Verify readout text color resolution logic for `TTColor.Zone7` when `isDark == true`.
  - Verify KDoc documentation contract in `ZoneDistributionModels.kt`.
* **Expected Result**:
  - Contrast ratio of `outlineVariant` border against dark surface exceeds $3.0:1$ WCAG threshold.
  - Text color for Zone 7 in dark mode resolves to `MaterialTheme.colorScheme.onSurface` (or `Color.White`) instead of `#000000`, guaranteeing $\ge 4.5:1$ text contrast.
  - KDoc comments in `ZoneDistributionModels.kt` document 7 zones for power.

### Test Case 4: 9-Language Localization & String Resource Audit (`[TST-UI-383.4]`)
* **Scope**: Localization Parity Test
* **Goal**: Confirm that NO new user-facing string resources are required across the canonical 9 app locales (`values` (en), `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **Audit Verification Table**:

| UI Element | Source of Text / Graphics | New String Required? | Rationale |
| :--- | :--- | :---: | :--- |
| **Histogram Zone Labels** | `"Z${bin.zoneIndex}"` | **No** | Formatted programmatically using integer index (`1..7`). |
| **Threshold Dashed Lines** | Numeric coordinates | **No** | Drawn directly on `Canvas` as horizontal dashed lines. |
| **Dark Mode Outlines** | `MaterialTheme.colorScheme.outlineVariant` | **No** | Visual `BorderStroke` / Canvas `drawRoundRect` styling. |
| **Readout Text Color** | `MaterialTheme.colorScheme.onSurface` | **No** | Compose color resolution; uses existing theme tokens. |
| **Distribution Card Titles** | Existing strings (`power_zones_title`, etc.) | **No** | Reuses existing localized strings across all 9 locales. |

* **Expected Result**:
  - Exactly 0 new string resources required.
  - `TranslationParityTest.kt` passes with 100% parity across all 9 locales and zero format specifier mismatches.

### Test Case 5: Clean-Room Regression Suite Execution (`[TST-UI-383.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the entire test suite with zero regressions.
* **Expected Result**: All tests pass cleanly (`BUILD SUCCESSFUL`).

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-383.1]` | Unit | `TelemetryHistogramCalculator.calculatePowerHistogram` | `REQ-UI-420.1`, `REQ-UI-420.5` | Specified |
| `[TST-UI-383.2]` | Unit | `TelemetryZoneMath.calculatePowerThresholdDashes` | `REQ-UI-420.2`, `REQ-UI-420.5` | Specified |
| `[TST-UI-383.3]` | Contract | Dark Mode Contrast & `ZoneDistributionModels` | `REQ-UI-420.3`, `REQ-UI-420.4` | Specified |
| `[TST-UI-383.4]` | Localization | 9-Locale String Audit & `TranslationParityTest` | `REQ-UI-420`, `REQ-UI-106` | Specified |
| `[TST-UI-383.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-420` | Specified |
