# Stage 3: Implementation Plan - ATT-4293: Fold rare high-power spikes into an overflow bar in Power Histogram with interactive Sprint Tail zoom and return bar

**Ticket**: [ATT-4293](https://rainerblind.atlassian.net/browse/ATT-4293)  
**Sub-task**: [ATT-4307](https://rainerblind.atlassian.net/browse/ATT-4307) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-419` (`REQ-UI-419.1`..`REQ-UI-419.6`)  
**Test Mapping**: `TST-UI-382` (`TST-UI-382.1`..`TST-UI-382.7`)  
**Branch**: `feature/ATT-4293`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

In the post-workout analysis view of **aTrainingTracker**, athletes inspect physiological training zone distributions (`Leistungszonen` card in `PowerZoneDistributionCard.kt`) and toggle between three visualization modes (`ZoneCardDisplayMode`: `STACKED_BAR`, `COLUMNS`, and `HISTOGRAM`). When `HISTOGRAM` is selected, `TelemetryHistogramChart.kt` renders fine-grained 10 W frequency bins across the entire recorded power range (`dataMin..dataMax`).

Unlike heart rate telemetry, which naturally follows a symmetric, bounded physiological normal distribution (~80–185 bpm, spanning ~50 bins of 2 bpm), cycling power is characterized by a **heavy right-tailed distribution**:
1. **X-Axis Squeezing on Endurance Rides**: In typical road cycling, gravel, or endurance rides, approximately 99% of active ride time is spent between 0 W and ~350–400 W (Zones 1 through 4/5). However, a handful of brief sprints, steep punchy climbs, or acceleration surges can spike up to 800–1000+ W. Because the current calculation engine (`TelemetryHistogramCalculator.kt`) allocates uniform linear bins from `0 W` to `dataMax` (e.g. 940 W), the chart generates 94+ narrow 10 W bins. Consequently, the right 55–60% of the chart is virtually empty, populated only by 1-pixel baseline dots, while the active 99% of the workout is squished into the leftmost ~40% of the card width (as evidenced in on-device capture `power_zones_histogram.png` on Pixel 10).
2. **Y-Axis Compression for Sprinters**: Even when an athlete attempts to inspect high-wattage sprint efforts in the 400–940 W region, the global vertical Y-axis auto-scaling scales linearly to the maximum bin duration across the whole ride (e.g., a 15-minute steady endurance bin at 200 W = 900 seconds). In comparison, a 5-second sprint bin at 850 W represents less than $0.6\%$ relative height, rendering as an indistinguishable baseline dot. Sprinters are entirely unable to evaluate their high-wattage power profile, cadence surges, or peak interval wattages.

To solve this dual compression problem, the system requires a two-view adaptive histogram:
- **Main View (Default)**: Rare sprint spikes are automatically folded into a distinct rightmost **Overflow Bar (`> X W →`)**, reclaiming the full card width for the primary aerobic and threshold distribution ($0\dots X\text{ W}$, covering 99% of the ride).
- **Sprint Tail View**: Tapping the overflow bar or its readout header chip transitions seamlessly into an expanded **Sprint Tail View ($X\dots\text{Max W}$)** with an independent, local Y-axis scale (elevating 3–15 second sprint efforts into tall, clearly readable bars) and a symmetric leftmost **Return Bar (`← 0–X W`)** and return chip that restores the Main View with a single tap.

---

## 2. Traceability & Requirements Mapping

This implementation plan establishes complete bidirectional traceability between `REQ-UI-419`, test cases `TST-UI-382.1` through `TST-UI-382.7`, and the construction deliverables:

| Requirement ID | Requirement Summary | Test Case ID | Target Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-UI-419.1` | **Statistical 99% Tail Cutoff ($P_{99}$), Zone 4 Floor, 50 W Rounding & Gating**: Compute cumulative active duration to find $P_{99}$, floor at athlete's `thresholds.z4Max`, round up to clean 50 W boundary, and gate on $\ge 100\text{ W}$ tail, $\ge 25\%$ tail excess, and tail duration $> 0$. | `TST-UI-382.1`, `TST-UI-382.2` | `TelemetryHistogramCalculatorTest.kt`: Unit tests verifying cutoff math, boundary rounding, floor enforcement, eligibility gating, and HR linear non-folding. |
| `REQ-UI-419.2` | **Domain Model Extensions & Data Partitioning**: Add `isOverflow` and `isReturn` to `TelemetryHistogramBin`; add `tailCutoff` and `sprintTail` to `TelemetryHistogramData` with backward-compatible defaults; partition Main View and Sprint Tail datasets with mathematical conservation. | `TST-UI-382.3`, `TST-UI-382.4` | `TelemetryHistogramCalculatorTest.kt`, `TelemetryHistogramCardTest.kt`: Unit tests verifying bin aggregation, percentage/duration conservation, and constructor backward compatibility. |
| `REQ-UI-419.3` | **Two-View Interactive Navigation**: Reactive `isSprintTailView` state in `TelemetryHistogramChart.kt`; clickable overflow bar and `> X W` chip in Main View; clickable return bar and `← 0–X W` chip in Sprint Tail View. | `TST-UI-382.5` | `TelemetryHistogramCardTest.kt`: State transition tests and readout string format contract verification. |
| `REQ-UI-419.4` | **Independent Y-Axis Scaling in Sprint Tail View**: Local `maxDuration` computed strictly across sprint bins (`filter { !it.isReturn }`), elevating 3–15 s sprint efforts to full canvas height ($96\text{ dp}$); minimum visible overflow bar height ($\ge 6\text{ dp}$) in Main View. | `TST-UI-382.5` | `TelemetryHistogramCardTest.kt`: Y-axis scale height fraction assertions on sprint bins versus return bar. |
| `REQ-UI-419.5` | **Bin Inspection & Drag Scrubbing Parity**: Continuous drag and tap scrubbing across regular bins displaying exact wattage, duration, percentage, and zone badge (`Z1`–`Z7`) without locking on overflow/return transitions. | `TST-UI-382.5` | `TelemetryHistogramCardTest.kt`: Gesture disambiguation and bin inspection contract tests. |
| `REQ-UI-419.6` | **9-Language Localization & Format Parity**: Localize `power_histogram_overflow_label`, `power_histogram_return_label`, `power_histogram_overflow_desc`, and `power_histogram_return_desc` across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with positional specifiers (`%1$d`, `%2$s`). | `TST-UI-382.6` | `TelemetryHistogramLocalizationTest.kt`: Automated 9-locale parity audit asserting non-empty strings, specifier count and indexing, and Rule 49 compliance. |
| `REQ-PRO-001` | **Clean-Room Regression & Build Integrity**: 100% test pass rate across the full project test suite with zero failures and zero regressions. | `TST-UI-382.7` | `./gradlew testDebugUnitTest` |

---

## 3. System Invariants & Preserved Behavior

1. **Strict Code Lock in Stage 3**: Zero modifications to production source code (`app/src/main/...`) occur during Stage 3. Production construction is strictly deferred to Stage 4 after Gate 3 audit approval (`check-gate`).
2. **Pure Mathematical Engine & Statelessness**: `TelemetryHistogramCalculator.kt` remains a pure, stateless computation object. It performs zero database queries, zero disk I/O, and zero network calls.
3. **Domain Model Backward Compatibility**: All new fields in `TelemetryHistogramBin` (`isOverflow = false`, `isReturn = false`) and `TelemetryHistogramData` (`tailCutoff = null`, `sprintTail = null`) provide default arguments. Existing callers, data mappers, and legacy test instantiations continue to compile and function without modification.
4. **Heart Rate Histogram Invariance**: Heart rate telemetry histograms (`calculateHeartRateHistogram`) remain strictly uniform and linear at 2 bpm bin width. Tail folding is strictly gated to Cycling Power telemetry.
5. **Symmetric / Steady Ride Invariance**: If a cycling workout lacks an extended sprint tail (failing the $\ge 100\text{ W}$ tail width or $\ge 25\%$ tail excess gate), `tailCutoff` and `sprintTail` resolve to `null`, and the chart renders in standard uniform linear mode across `dataMin..dataMax` with zero visual change.
6. **Mathematical Conservation of Duration & Percentage**:
   - In Main View: $\sum \text{regularBins.duration} + \text{overflowBin.duration} = \text{totalActiveTimeSec}$.
   - In Sprint Tail View: $\text{returnBin.duration} + \sum \text{sprintBins.duration} = \text{totalActiveTimeSec}$.
   - Sum of percentages equals $100.0\% \pm 0.1\%$ across both views.
7. **Source Contract Test Preservation (Rule 1)**:
   - Existing contract test `testTelemetryHistogramChart_streamlinedReadoutContract` asserts:
     - `content.contains("\${histogram.dataMin}–\${histogram.dataMax} \$unit")`
     - `content.contains("Δ \${histogram.binWidth} \$unit")`
   - In `TelemetryHistogramChart.kt`, when `histogram.tailCutoff == null` (standard linear mode), the resting header readout renders `${histogram.dataMin}–${histogram.dataMax} $unit` and `Δ ${histogram.binWidth} $unit`, ensuring existing source contract tests remain 100% satisfied.
8. **Layout Container & Height Stability**: `TelemetryHistogramChart` maintains its fixed vertical slot (`height(110.dp)` outer box / `height(96.dp)` canvas) across Main View, Sprint Tail View, and empty states. Switching between views produces zero vertical layout jumping or scroll displacement.
9. **Rule 49 Icon & String Separation**: Localized `<string>` resources in `strings.xml` MUST NOT include leading ASCII arrows (`→`, `←`) or pluses (`+`); navigation directionality is rendered exclusively using Compose vector icons (`Icons.AutoMirrored.Default.ArrowForward`, `Icons.AutoMirrored.Default.ArrowBack`).
10. **Parent Ticket Human Decision Gate (Rule 3)**: Moving parent ticket ATT-4293 to `Erledigt` is an inviolable Human Decision Gate strictly reserved for the human user in Ceremony 2 (Joint Review). Subtasks transition directly to `Erledigt` upon passing automated gate audits via `freigabe`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: Domain Models (`ZoneDistributionModels.kt`)
- **Layer**: Presentation Domain Models (`ui/aftermath/zones/`)
- **Modifications**:
  ```kotlin
  data class TelemetryHistogramBin(
      val binIndex: Int,
      val rangeMin: Int,
      val rangeMax: Int,
      val durationSec: Long,
      val percentage: Float,
      val zoneIndex: Int, // 1..5 (or 1..7 per ATT-4294)
      val color: Color,
      val isOverflow: Boolean = false,
      val isReturn: Boolean = false
  )

  data class TelemetryHistogramData(
      val binWidth: Int,
      val dataMin: Int,
      val dataMax: Int,
      val totalActiveTimeSec: Long,
      val bins: List<TelemetryHistogramBin>,
      val tailCutoff: Int? = null,
      val sprintTail: TelemetryHistogramData? = null
  )
  ```
- **Architectural Role**: Carries bin metadata, flags for overflow and return bars, and encapsulates the linked child dataset `sprintTail`. Default parameter values guarantee zero binary or source breakage for existing consumers (`HeartRateZoneDistributionCard`, `WorkoutSummary`).

### Component 2: Mathematical Engine (`TelemetryHistogramCalculator.kt`)
- **Layer**: Pure Domain Logic (`ui/aftermath/zones/`)
- **Modifications**:
  1. Add private constants:
     - `MIN_TAIL_BINS = 10` (equivalent to $100\text{ W}$ at 10 W bin width)
     - `MIN_TAIL_RATIO = 1.25f`
     - `CUTOFF_ROUNDING_W = 50`
  2. Implement `calculateTailCutoff(bins: List<TelemetryHistogramBin>, thresholds: PowerZoneThresholds, totalDuration: Long): Int?`:
     - Accumulate duration across bins until reaching $0.99 \times \text{totalDuration}$.
     - Obtain candidate bin `rangeMax` ($P_{99}$).
     - Floor candidate at `thresholds.z4Max`.
     - Round up to clean 50 W boundary: `((rawCutoff + CUTOFF_ROUNDING_W - 1) / CUTOFF_ROUNDING_W) * CUTOFF_ROUNDING_W`.
     - Check gating:
       - `dataMax - tailCutoff >= MIN_TAIL_BINS * binWidth`
       - `dataMax >= tailCutoff * MIN_TAIL_RATIO`
       - Active duration in bins $> \text{tailCutoff}$ is $> 0L$.
     - If all pass, return `tailCutoff`; otherwise return `null`.
  3. When `tailCutoff != null`:
     - **Main View Dataset**: Regular bins from `dataMin` to `tailCutoff`, plus rightmost **Overflow Bar (`isOverflow = true`)**:
       - `binIndex = numMainBins`
       - `rangeMin = tailCutoff`
       - `rangeMax = dataMax`
       - `durationSec = tailBins.sumOf { it.durationSec }`
       - `percentage = (durationSec.toFloat() / totalDuration) * 100f`
       - `zoneIndex = 5` (or top zone)
       - `color = TTColor.Zone5`
     - **Sprint Tail Dataset (`sprintTail`)**:
       - Leftmost **Return Bar (`isReturn = true`)**:
         - `binIndex = 0`
         - `rangeMin = dataMin`
         - `rangeMax = tailCutoff`
         - `durationSec = regularBins.sumOf { it.durationSec }`
         - `percentage = (durationSec.toFloat() / totalDuration) * 100f`
         - `zoneIndex = 1`
         - `color = TTColor.Zone1` / outline
       - Sprint bins: All 10 W bins from `tailCutoff` to `dataMax` with re-indexed `binIndex` ($1\dots M$).

### Component 3: UI Presentation & Interaction (`TelemetryHistogramChart.kt`)
- **Layer**: Jetpack Compose UI (`ui/aftermath/zones/`)
- **Modifications**:
  1. Reactive View State:
     ```kotlin
     var isSprintTailView by remember(histogram) { mutableStateOf(false) }
     var selectedBinIndex by remember(histogram, isSprintTailView) { mutableStateOf<Int?>(null) }
     ```
  2. Active Dataset Resolution:
     ```kotlin
     val activeHistogram = if (isSprintTailView && histogram.sprintTail != null) {
         histogram.sprintTail
     } else {
         histogram
     }
     val bins = activeHistogram.bins
     val numBins = bins.size
     ```
  3. Independent Y-Axis Scaling:
     ```kotlin
     val maxDuration = remember(activeHistogram, isSprintTailView) {
         if (isSprintTailView) {
             bins.filter { !it.isReturn }.maxOfOrNull { it.durationSec }?.coerceAtLeast(1L) ?: 1L
         } else {
             bins.maxOfOrNull { it.durationSec }?.coerceAtLeast(1L) ?: 1L
         }
     }
     ```
  4. Readout Header Architecture:
     - When `selectedBin != null`: displays bin range, formatted duration, percentage, and zone pill.
     - When `selectedBin == null`:
       - If `histogram.tailCutoff == null`: displays `${histogram.dataMin}–${histogram.dataMax} $unit` and `Δ ${histogram.binWidth} $unit` (strictly preserving existing grep contract tests).
       - If `!isSprintTailView` (Main View): displays `${histogram.dataMin}–${histogram.tailCutoff} $unit`, interactive overflow chip `> ${histogram.tailCutoff} $unit →` (with `Icons.AutoMirrored.Default.ArrowForward`), and `Δ ${histogram.binWidth} $unit`. Tapping chip sets `isSprintTailView = true`.
       - If `isSprintTailView` (Sprint Tail View): displays interactive return chip `← 0–${histogram.tailCutoff} $unit` (with `Icons.AutoMirrored.Default.ArrowBack`), active sprint range `${histogram.tailCutoff}–${histogram.dataMax} $unit`, and `Δ ${histogram.binWidth} $unit`. Tapping chip sets `isSprintTailView = false`.
  5. Canvas Drawing & Affordances:
     - Return Bar (`bin.isReturn`): Drawn at index 0 with subtle neutral/outline fill and a distinct `Stroke(1.5.dp.toPx())` border.
     - Overflow Bar (`bin.isOverflow`): Drawn at index `numBins - 1` with a guaranteed minimum height (`(heightFraction * totalHeight).coerceAtLeast(6.dp.toPx())`).
     - Regular Bins: Drawn with rounded corners (`CornerRadius(1.5.dp.toPx())`).
  6. Gesture Detection:
     - `detectTapGestures`:
       - If tapped bin `isOverflow`: `isSprintTailView = true; selectedBinIndex = null`.
       - If tapped bin `isReturn`: `isSprintTailView = false; selectedBinIndex = null`.
       - Else: toggle selection `selectedBinIndex = if (selectedBinIndex == clickedIndex) null else clickedIndex`.
     - `detectDragGestures`: Continuous scrubbing updates `selectedBinIndex`.

### Component 4: 9-Language Localization Resources (`res/values*/strings.xml`)
- **Layer**: Android Resources
- **Resource Keys & Values**:
  1. `power_histogram_overflow_label`: `> %1$d %2$s`
  2. `power_histogram_return_label`: `%1$d–%2$d %3$s`
  3. `power_histogram_overflow_desc`:
     - EN: `Switch to sprint tail view above %1$d %2$s`
     - DE: `Zur Sprint-Tail-Ansicht über %1$d %2$s wechseln`
     - ES: `Cambiar a vista de sprint por encima de %1$d %2$s`
     - FR: `Passer à la vue sprint au-dessus de %1$d %2$s`
     - IT: `Passa alla vista sprint sopra %1$d %2$s`
     - JA: `%1$d %2$s 超のスプリント詳細表示に切り替え`
     - NL: `Overschakelen naar sprint-tailweergave boven %1$d %2$s`
     - PL: `Przełącz na widok sprintu powyżej %1$d %2$s`
     - PT: `Mudar para a vista de sprint acima de %1$d %2$s`
  4. `power_histogram_return_desc`:
     - EN: `Return to main histogram 0 to %1$d %2$s`
     - DE: `Zurück zum Haupt-Histogramm 0 bis %1$d %2$s`
     - ES: `Volver al histograma principal de 0 a %1$d %2$s`
     - FR: `Revenir à l\'histogramme principal de 0 à %1$d %2$s`
     - IT: `Torna all\'istogramma principale da 0 a %1$d %2$s`
     - JA: `メインのヒストグラム (0 〜 %1$d %2$s) に戻る`
     - NL: `Terugkeren naar hoofdhistogram 0 tot %1$d %2$s`
     - PL: `Wróć do głównego histogramu od 0 do %1$d %2$s`
     - PT: `Voltar ao histograma principal de 0 a %1$d %2$s`

---

### UI Consistency (Rule 23 — mandatory per docs/design_guidelines.md Section 5)

* **Reference screen / component**:
  `PowerZoneDistributionCard.kt` (`Leistungszonen` card in `DetailedWorkoutView` and `WorkoutSummary`) and `TelemetryHistogramChart.kt` (fine-grained telemetry histogram).
* **Reused components**:
  - Material 3 `Surface` (compact header pill with `shape = RoundedCornerShape(6.dp)` and `color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)`)
  - Material 3 `Text` with `MaterialTheme.typography.labelSmall` and `labelMedium`
  - Material 3 `Icon` with `Icons.AutoMirrored.Default.ArrowForward` and `Icons.AutoMirrored.Default.ArrowBack`
  - Existing Canvas rendering (`drawRoundRect`) with `CornerRadius(1.5.dp.toPx())`
* **Theme tokens**:
  - Shapes: `RoundedCornerShape(6.dp)` for header container, `CornerRadius(1.5.dp.toPx())` for canvas bars, `RoundedCornerShape(4.dp)` for interactive navigation chips
  - Spacing: `6.dp` vertical arrangement between readout header and canvas, `1.dp` horizontal bar spacing, `8.dp` horizontal and `4.dp` vertical header padding
  - Colors: `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.onSurface`, `MaterialTheme.colorScheme.onSurfaceVariant`, `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.outlineVariant`, and `TTColor.Zone*`
* **New one-off styles & justification**:
  None. The design strictly reuses existing Material 3 design tokens, typography styles, and TTColor palette without custom styles or hardcoded colors.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Domain Model Extensions (`ZoneDistributionModels.kt`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionModels.kt`
* **Changes**:
  - Add `val isOverflow: Boolean = false` and `val isReturn: Boolean = false` to `TelemetryHistogramBin`.
  - Add `val tailCutoff: Int? = null` and `val sprintTail: TelemetryHistogramData? = null` to `TelemetryHistogramData`.
  - Add comprehensive KDoc documentation explaining the dual-view hierarchy.

### Step 2: 9-Language Localization Resources Addition
* **Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Changes**:
  - Add string keys `power_histogram_overflow_label`, `power_histogram_return_label`, `power_histogram_overflow_desc`, and `power_histogram_return_desc` with full 9-language translation parity.

### Step 3: Algorithmic Implementation in `TelemetryHistogramCalculator.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculator.kt`
* **Changes**:
  - Implement $P_{99}$ active duration threshold calculation.
  - Floor at `thresholds.z4Max` and ceiling round to 50 W boundary (`((rawCutoff + 49) / 50) * 50`).
  - Implement strict eligibility gating ($\ge 100\text{ W}$ tail width, $\ge 25\%$ tail excess, duration $> 0$).
  - Partition qualifying power datasets into Main View (regular bins + rightmost overflow bar) and Sprint Tail View (leftmost return bar + individual 10 W sprint bins).
  - Enforce mathematical conservation of durations and percentages.
  - Preserve standard linear calculation for heart rate histograms and non-qualifying steady power rides.

### Step 4: Interactive UI & Canvas Implementation in `TelemetryHistogramChart.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramChart.kt`
* **Changes**:
  - Add `isSprintTailView` reactive state and reset `selectedBinIndex` on view changes.
  - Implement independent Y-axis scaling for Sprint Tail View (`maxDuration` excludes return bar).
  - Update readout header with clickable overflow chip (`> X W →`) in Main View and clickable return chip (`← 0–X W`) in Sprint Tail View.
  - Preserve `${histogram.dataMin}–${histogram.dataMax} $unit` and `Δ ${histogram.binWidth} $unit` when `tailCutoff == null` for source contract test compliance.
  - Render distinct canvas styles for return bar (outline) and overflow bar (minimum visible height $\ge 6\text{ dp}$).
  - Implement tap detection on overflow bar and return bar to toggle views.

### Step 5: Unit Tests Construction in `TelemetryHistogramCalculatorTest.kt`
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
* **Changes**:
  - Implement `TST-UI-382.1`: $P_{99}$ cutoff, Zone 4 floor, and clean 50 W boundary rounding tests.
  - Implement `TST-UI-382.2`: Eligibility gating rules (width $< 100\text{ W}$, ratio $< 1.25$, zero tail duration) and Heart Rate linear invariance tests.
  - Implement `TST-UI-382.3`: Main View overflow bin aggregation, Sprint Tail return bin aggregation, and duration/percentage mathematical conservation tests.

### Step 6: Contract & Interaction Tests Construction in `TelemetryHistogramCardTest.kt`
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCardTest.kt`
* **Changes**:
  - Implement `TST-UI-382.4`: Model backward compatibility test with default arguments.
  - Implement `TST-UI-382.5`: Independent Y-axis scaling calculations, resting readout formats, and source contract tests.

### Step 7: Localization Parity Test Construction (`TelemetryHistogramLocalizationTest.kt`)
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramLocalizationTest.kt`
* **Changes**:
  - Implement `TST-UI-382.6`: Automated audit verifying all 4 string keys across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with exact format specifier parity.

### Step 8: Targeted Unit Tests Execution
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*"
  ```
* **Expected Result**: 100% pass rate in ~5–10 seconds.

---

## 6. Verification & Rollback Plan

### Verification Strategy
1. **Gate 3 Verification**: Programmatic verification via `python3 tools/jira_util.py check-gate ATT-4307` confirming Gate 3 pass before any Stage 4 code modification.
2. **Targeted Fast Unit Tests (Stage 4)**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*"` after code construction.
3. **Clean-Room Regression Suite (Stage 5)**: Execute `./gradlew testDebugUnitTest` verifying 100% success across all project modules.
4. **On-Device Inspection (Ceremony 2)**: Deploy build to physical test device (Google Pixel 10) via `./gradlew installDebug` and verify interactive switching between Main View and Sprint Tail View.

### Rollback Strategy
All changes reside on isolated feature branch `feature/ATT-4293` branched from `sprint/2026-43.1`. If any unforeseen architectural defect arises, the branch can be cleanly reset or discarded via `git checkout sprint/2026-43.1 && git branch -D feature/ATT-4293` without affecting `develop` or the active sprint branch.
