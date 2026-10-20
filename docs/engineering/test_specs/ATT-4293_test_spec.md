# Stage 2: Requirement & Test Specification - ATT-4293: Fold rare high-power spikes into an overflow bar in Power Histogram with interactive Sprint Tail zoom and return bar

**Ticket**: [ATT-4293](https://rainerblind.atlassian.net/browse/ATT-4293)  
**Sub-task**: [ATT-4306](https://rainerblind.atlassian.net/browse/ATT-4306) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-419` (*Power Histogram Rare Sprint Spike Folding, Overflow Bar, and Interactive Sprint Tail Zoom Navigation*)  
**Test Spec ID**: `TST-UI-382`  
**Branch**: `feature/ATT-4293`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-UI-419)

### 1.1 Problem Statement & Rationale

In the post-workout analysis view of **aTrainingTracker**, athletes inspect physiological training zone distributions (`Leistungszonen` card in `PowerZoneDistributionCard.kt`) and toggle between three visualization modes (`ZoneCardDisplayMode`: `STACKED_BAR`, `COLUMNS`, and `HISTOGRAM`). When `HISTOGRAM` is active, `TelemetryHistogramChart.kt` renders fine-grained 10 W frequency bins across the entire recorded power range (`dataMin..dataMax`).

Unlike heart rate telemetry, which naturally follows a symmetric, bounded physiological normal distribution (~80–185 bpm, spanning ~50 bins of 2 bpm), cycling power is characterized by a **heavy right-tailed distribution**:
1. **X-Axis Squeezing on Endurance Rides**: In typical road cycling, gravel, or endurance rides, approximately 99% of active ride time is spent between 0 W and ~350–400 W (Zones 1 through 4/5). However, a handful of brief sprints, steep punchy climbs, or acceleration surges can spike up to 800–1000+ W. Because the current calculation engine (`TelemetryHistogramCalculator.kt`) allocates uniform linear bins from `0 W` to `dataMax` (e.g. 940 W), the chart generates 94+ narrow 10 W bins. Consequently, the right 55–60% of the chart is virtually empty, populated only by 1-pixel baseline dots, while the active 99% of the workout is squished into the leftmost ~40% of the card width (as evidenced in on-device capture `power_zones_histogram.png` on Pixel 10).
2. **Y-Axis Compression for Sprinters**: Even when an athlete attempts to inspect high-wattage sprint efforts in the 400–940 W region, the global vertical Y-axis auto-scaling scales linearly to the maximum bin duration across the whole ride (e.g., a 15-minute steady endurance bin at 200 W = 900 seconds). In comparison, a 5-second sprint bin at 850 W represents less than $0.6\%$ relative height, rendering as an indistinguishable baseline dot. Sprinters are entirely unable to evaluate their high-wattage power profile, cadence surges, or peak interval wattages.

To solve this dual compression problem, the system requires a two-view adaptive histogram:
- **Main View (Default)**: Rare sprint spikes are automatically folded into a distinct rightmost **Overflow Bar (`> X W →`)**, reclaiming the full card width for the primary aerobic and threshold distribution ($0\dots X\text{ W}$, covering 99% of the ride).
- **Sprint Tail View**: Tapping the overflow bar or its readout header chip transitions seamlessly into an expanded **Sprint Tail View ($X\dots\text{Max W}$)** with an independent, local Y-axis scale (elevating 3–15 second sprint efforts into tall, clearly readable bars) and a symmetric leftmost **Return Bar (`← 0–X W`)** and return chip that restores the Main View with a single tap.

---

### 1.2 Functional & Architectural Requirements

1. **Statistical 99% Tail-Folding Cutoff ($P_{99}$) in `TelemetryHistogramCalculator.kt` (`REQ-UI-419.1`)**:
   - For Cycling Power histograms (`calculatePowerHistogram`), the system SHALL compute cumulative active duration across all frequency bins to identify the 99th percentile duration threshold ($P_{99}$):
     $$P_{99} = \text{bin.rangeMax where } \sum_{i=0}^{k} d_i \ge 0.99 \times T_{\text{total}}$$
   - The cutoff candidate SHALL be floored at the athlete's Zone 4 upper boundary (`thresholds.z4Max`) to guarantee that the full aerobic and threshold spectrum (Zones 1–4) remains permanently visible in the Main View:
     $$\text{rawCutoff} = \max(P_{99}, \text{thresholds.z4Max})$$
   - The cutoff candidate SHALL be rounded up to the nearest clean 50 W boundary:
     $$\text{tailCutoff} = \left\lceil \frac{\text{rawCutoff}}{50} \right\rceil \times 50$$
   - Bounded Eligibility Gating: Tail folding SHALL be gated and enabled IF AND ONLY IF:
     1. $\text{dataMax} - \text{tailCutoff} \ge 10 \times \text{binWidth}$ ($\ge 100\text{ W}$ in the tail);
     2. $\text{dataMax} \ge \text{tailCutoff} \times 1.25$ (tail exceeds cutoff by at least 25%); and
     3. Active duration above `tailCutoff` is strictly $> 0$ ($\sum_{P_i > \text{tailCutoff}} d_i > 0$).
   - If gating conditions are not met, or when calculating Heart Rate histograms (`calculateHeartRateHistogram`), the system SHALL return the standard linear dataset (`tailCutoff = null`, `sprintTail = null`).

2. **Domain Model Extensions in `ZoneDistributionModels.kt` (`REQ-UI-419.2`)**:
   - `TelemetryHistogramBin` SHALL declare:
     - `val isOverflow: Boolean = false`
     - `val isReturn: Boolean = false`
     with default arguments preserving 100% binary and source backward compatibility.
   - `TelemetryHistogramData` SHALL declare:
     - `val tailCutoff: Int? = null`
     - `val sprintTail: TelemetryHistogramData? = null`
     with default arguments preserving 100% binary and source backward compatibility.
   - When tail folding is active:
     - The **Main View dataset** (`TelemetryHistogramData`) SHALL contain regular bins from `dataMin` to `tailCutoff`, followed by a single rightmost overflow bin (`isOverflow = true`, `binIndex = numMainBins`, `rangeMin = tailCutoff`, `rangeMax = dataMax`, `durationSec = \sum_{P_i > \text{tailCutoff}} d_i`, `percentage = (\text{duration} / T_{\text{total}}) \times 100\%`, `zoneIndex = 5` or highest zone, `color = TTColor.Zone5`).
     - The **Sprint Tail dataset** (`sprintTail`) SHALL contain a single leftmost return bin (`isReturn = true`, `binIndex = 0`, `rangeMin = dataMin`, `rangeMax = tailCutoff`, `durationSec = \sum_{P_i \le \text{tailCutoff}} d_i`, `percentage = (\text{duration} / T_{\text{total}}) \times 100\%`, `color = MaterialTheme.colorScheme.outlineVariant` / subtle neutral), followed by all individual 10 W sprint bins from `tailCutoff` to `dataMax`.

3. **Two-View Interactive Navigation in `TelemetryHistogramChart.kt` (`REQ-UI-419.3`)**:
   - `TelemetryHistogramChart` SHALL maintain reactive view state:
     `var isSprintTailView by remember(histogram) { mutableStateOf(false) }`
     and reset `selectedBinIndex` whenever `histogram` or `isSprintTailView` transitions.
   - **Main View (`!isSprintTailView`, Default)**:
     - Renders regular bins $0\dots\text{tailCutoff}$ plus the rightmost overflow bar.
     - Resting header readout displays range `${dataMin}–${tailCutoff} $unit`, a clickable overflow chip `> ${tailCutoff} $unit` (with trailing forward arrow icon `Icons.AutoMirrored.Default.ArrowForward`), and resolution `Δ ${binWidth} $unit`.
     - Navigation: Tapping either the rightmost overflow bar on the canvas OR the overflow header chip toggles `isSprintTailView = true` and clears `selectedBinIndex`.
   - **Sprint Tail View (`isSprintTailView`)**:
     - Renders the leftmost return bar followed by individual 10 W bins from `tailCutoff` to `dataMax`.
     - Resting header readout displays a clickable return chip `${dataMin}–${tailCutoff} $unit` (with leading back arrow icon `Icons.AutoMirrored.Default.ArrowBack`), active sprint range `${tailCutoff}–${dataMax} $unit`, and resolution `Δ ${binWidth} $unit`.
     - Navigation: Tapping either the leftmost return bar on the canvas OR the return header chip toggles `isSprintTailView = false` and clears `selectedBinIndex`.

4. **Independent Y-Axis Scaling in Sprint Tail View (`REQ-UI-419.4`)**:
   - In Sprint Tail View, vertical Y-axis scaling `maxDuration` SHALL be computed strictly across sprint bins (`currentBins.filter { !it.isReturn }.maxOfOrNull { it.durationSec }?.coerceAtLeast(1L) ?: 1L`), completely excluding the bulk duration of the return bar, elevating 3–15 second sprint efforts to full canvas height ($96\text{ dp}$).
   - In Main View, `maxDuration` SHALL be computed across all visible bins (`currentBins.maxOfOrNull { it.durationSec }?.coerceAtLeast(1L) ?: 1L`), with the overflow bar guaranteed a minimum visible height ($\ge 6\text{ dp}$) so it is immediately noticeable and tap-accessible.

5. **Bin Inspection & Drag Scrubbing Parity (`REQ-UI-419.5`)**:
   - Tapping or dragging across regular bins in either view SHALL update `selectedBinIndex` and display that bin's exact wattage range, duration, percentage, and zone badge (`Z1`–`Z7`) in the header readout.
   - Touching the overflow bar in Main View or return bar in Sprint Tail View immediately triggers the respective view transition without sticking in selected mode.

6. **100% 9-Language Localization & Format Parity (`REQ-UI-419.6`)**:
   - All chip labels and accessibility descriptions SHALL exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with indexed positional specifiers (`%1$d`, `%2$s`).
   - In compliance with Rule 49, localized `<string>` resources MUST NOT include leading ASCII arrows or pluses; icon graphics are hosted strictly in Compose `Icon` slots.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Default Main View with Overflow Bar on Tail-Heavy Rides**
  * *Given* a cycling workout with a long high-power sprint tail (e.g. 99% of time spent $\le 380\text{ W}$ and rare sprints spiking up to $940\text{ W}$ with `z4Max = 300 W`),
  * *When* the athlete views the Power Zones Histogram (`ZoneCardDisplayMode.HISTOGRAM`),
  * *Then* `tailCutoff` is computed as $400\text{ W}$ (rounded up to $50\text{ W}$ boundary from $380\text{ W}$),
  * *And* the chart renders in the **Main View ($0\dots 400\text{ W}$)** utilizing the full card width,
  * *And* a rightmost **Overflow Bar (`> 400 W`)** is rendered aggregating all time above $400\text{ W}$,
  * *And* the resting readout header renders `0–400 W`, an interactive `> 400 W` overflow chip, and `Δ 10 W`.

* **Criterion 2: Forward Transition to Sprint Tail View & Y-Axis Rescaling**
  * *Given* the Power Histogram in Main View,
  * *When* the athlete taps either the rightmost `> 400 W` overflow bar on the canvas or the `> 400 W` header chip,
  * *Then* the chart transitions to the **Sprint Tail View ($400\dots 940\text{ W}$)**,
  * *And* a leftmost **Return Bar (`0–400 W`)** is rendered at index 0,
  * *And* the Y-axis rescales to the maximum duration strictly among the sprint bins ($400\dots 940\text{ W}$), elevating short 3–15 second sprint efforts into tall, readable bars,
  * *And* the resting readout header renders an interactive `0–400 W` return chip, active sprint range `400–940 W`, and `Δ 10 W`.

* **Criterion 3: Symmetric Return to Main View**
  * *Given* the Power Histogram in Sprint Tail View,
  * *When* the athlete taps either the leftmost `0–400 W` return bar on the canvas or the `0–400 W` return chip in the readout header,
  * *Then* the chart returns to the **Main View ($0\dots 400\text{ W}$)** with the original global Y-axis scale and regular bin distribution.

* **Criterion 4: Symmetric / Steady Rides Non-Folding Invariant**
  * *Given* a steady workout without an extended sprint tail (e.g., an indoor ergometer ride where `dataMax = 320 W` and $P_{99} = 300\text{ W}$, failing the $\ge 100\text{ W}$ tail or $1.25\times$ threshold),
  * *When* `calculatePowerHistogram` evaluates the samples,
  * *Then* `tailCutoff` is `null` and `sprintTail` is `null`,
  * *And* the chart renders in standard uniform linear mode across $0\dots 320\text{ W}$ with zero visual disruption.

* **Criterion 5: Heart Rate Telemetry Invariance**
  * *Given* heart rate telemetry samples (e.g. spanning 80 to 185 bpm),
  * *When* `calculateHeartRateHistogram` evaluates the samples,
  * *Then* tail folding is never applied, `tailCutoff` is `null`, and the histogram renders strictly linearly at 2 bpm bin width.

---

### 1.4 System Invariants

1. **Pure Mathematical Calculation & Zero Side Effects**:
   - `TelemetryHistogramCalculator.kt` is a pure, stateless computation engine.
   - It performs zero database operations, zero network requests, and zero mutations to tracking lifecycles.
2. **Model Backward Compatibility (Zero Breaking Changes)**:
   - All new parameters in `TelemetryHistogramBin` (`isOverflow = false`, `isReturn = false`) and `TelemetryHistogramData` (`tailCutoff = null`, `sprintTail = null`) provide default arguments.
   - Existing callers, serializers, and contract tests continue to compile and function without modification.
3. **Mathematical Conservation of Duration & Percentage**:
   - In Main View: $\sum \text{regularBins.duration} + \text{overflowBin.duration} = \text{totalActiveTimeSec}$.
   - In Sprint Tail View: $\text{returnBin.duration} + \sum \text{sprintBins.duration} = \text{totalActiveTimeSec}$.
   - Sum of percentages equals $100.0\% \pm 0.1\%$ across all views.
4. **Heart Rate Histogram Invariance**:
   - Heart rate telemetry remains strictly uniform and linear; zero tail folding is applied.
5. **Layout Container & Height Stability**:
   - `TelemetryHistogramChart` maintains its fixed vertical slot (`height(110.dp)` / `height(96.dp)` canvas) across Main View, Sprint Tail View, and empty states.
   - Zero layout jump or scroll position displacement occurs during view switching.
6. **Parent Ticket Human Decision Gate (Rule 3)**:
   - Moving parent ticket ATT-4293 to `Erledigt` is an inviolable Human Decision Gate strictly reserved for the human user during Ceremony 2 (Joint Review).

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - Complements `REQ-UI-372.5` (`TelemetryHistogramChart` rendered when `effectiveDisplayMode == HISTOGRAM`), introduced in Sprint 2026-40.8 (`ATT-1845`), refined in Sprint 2026-42.4 (`ATT-3390`), and decoupled in Sprint 2026-42.5 (`REQ-UI-388`, `ATT-3773`).
  - Targets `TelemetryHistogramCalculator.kt`, `TelemetryHistogramChart.kt`, `ZoneDistributionModels.kt`, and `PowerZoneDistributionCard.kt`.
* **Historical Origin & Commit Trace**:
  - Commit `7b14d9a2` (`ATT-1845`) introduced fine-grained frequency histograms with linear binning.
  - Commit `38706240` (`ATT-3390`) introduced 3-way mode switching on individual zone cards.
  - Commits `b989e1ce` (`ATT-3676`) and `e927c3da` (`ATT-3773`) established `HISTOGRAM` as the default mode in Workout Details.
* **Root Reason for Existing Formulation ("Why was this fence built?")**:
  - The initial histogram implementation applied identical uniform linear binning across both Heart Rate and Power metrics. Heart rate telemetry naturally spans a narrow, bounded physiological interval (~80–185 bpm), which maps cleanly onto a uniform linear X-axis. Cycling power was initially assumed to follow a similar structure, without accounting for the fact that power exhibits extreme right-skewed kurtosis where 99% of time is spent at moderate wattages and 1% in sprint spikes up to 1000+ W.
* **Preservation of Core Invariants ("Why is it safe to alter now?")**:
  1. *Linear Mode for Non-Skewed Rides*: If a ride does not exhibit an extended sprint tail (e.g., steady indoor trainer ride where `dataMax` is within 25% of $P_{99}$), `tailCutoff` resolves to `null`, and the chart renders in standard linear mode with zero visual difference.
  2. *Heart Rate Parity*: Heart rate histograms are unaffected and retain their existing calculation and linear layout.
  3. *Source Contract Test Preservation*: Contract tests asserting resting readout strings (`content.contains("\${histogram.dataMin}–\${histogram.dataMax} \$unit")` and `content.contains("Δ \${histogram.binWidth} \$unit")`) remain 100% satisfied.
  4. *Zero Regressions in Mode Switching*: 3-way mode switching (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`) in `PowerZoneDistributionCard.kt` and `WorkoutSummary.kt` remains 100% preserved.

---

## 2. Test Specification (TST-UI-382)

### Test Case 1: Statistical 99% Tail Cutoff, Floor & 50 W Rounding Unit Test (`TST-UI-382.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
* **Preconditions**:
  - Configured `PowerZoneThresholds` with `z4Max = 300 W`.
  - Synthetic power sample datasets with known duration distributions.
* **Action**:
  1. Dataset A: 99% of duration spent $\le 380\text{ W}$, with high-power spikes extending to $940\text{ W}$.
     - Evaluate `TelemetryHistogramCalculator.calculatePowerHistogram(samplesA, pwrThresholds)`.
     - Verify `data.tailCutoff == 400` (floored at $z4Max = 300\text{ W}$, ceiling rounded to 50 W boundary from $380\text{ W}$).
  2. Dataset B: 99% of duration spent $\le 220\text{ W}$, with high-power spikes extending to $800\text{ W}$.
     - Evaluate `TelemetryHistogramCalculator.calculatePowerHistogram(samplesB, pwrThresholds)`.
     - Verify `data.tailCutoff == 300` (floored at athlete's Zone 4 upper boundary `z4Max = 300 W`).
  3. Clean 50 W Rounding Check:
     - Verify raw cutoff $312\text{ W}$ rounds up to $350\text{ W}$.
     - Verify raw cutoff $350\text{ W}$ remains $350\text{ W}$.
     - Verify raw cutoff $351\text{ W}$ rounds up to $400\text{ W}$.
* **Expected Result**:
  - `tailCutoff` calculates accurately with strict Zone 4 floor and clean 50 W boundary rounding.

---

### Test Case 2: Tail Folding Gating & Symmetric Dataset Non-Folding Test (`TST-UI-382.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
* **Preconditions**:
  - Synthetic power and heart rate datasets.
* **Action**:
  1. Gating Condition 1 Violation ($< 100\text{ W}$ in tail):
     - Dataset with $P_{99} = 300\text{ W}$ and `dataMax = 370 W` ($\text{tailCutoff} = 300\text{ W}$, tail width $70\text{ W} < 100\text{ W}$).
     - Verify `result.tailCutoff == null` and `result.sprintTail == null`.
  2. Gating Condition 2 Violation ($< 25\%$ tail excess):
     - Dataset with $\text{tailCutoff} = 400\text{ W}$ and `dataMax = 480 W` ($480 / 400 = 1.20 < 1.25$).
     - Verify `result.tailCutoff == null` and `result.sprintTail == null`.
  3. Gating Condition 3 Violation (Zero duration in tail):
     - Dataset where samples above `tailCutoff` have duration 0.
     - Verify `result.tailCutoff == null`.
  4. Heart Rate Histogram Parity:
     - Dataset with heart rate samples spanning 90 to 180 bpm.
     - Evaluate `TelemetryHistogramCalculator.calculateHeartRateHistogram(samples, hrThresholds)`.
     - Verify `result.tailCutoff == null` and `result.sprintTail == null`.
* **Expected Result**:
  - Non-qualifying power workouts and all heart rate workouts return standard linear histogram datasets.

---

### Test Case 3: Main View Overflow Bin & Sprint Tail Return Bin Aggregation Test (`TST-UI-382.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
* **Preconditions**:
  - Power dataset qualifying for tail folding: $0\dots 400\text{ W}$ endurance/tempo duration ($3500\text{ s}$) + $400\dots 940\text{ W}$ sprint surges ($50\text{ s}$). Total duration: $3550\text{ s}$.
* **Action**:
  1. Inspect Main View Dataset (`result`):
     - Verify regular bins span $0\dots 400\text{ W}$ in 10 W increments (40 regular bins).
     - Verify bin 40 is the rightmost **Overflow Bin**:
       - `bin.isOverflow == true`
       - `bin.rangeMin == 400`
       - `bin.rangeMax == 940`
       - `bin.durationSec == 50L`
       - `bin.percentage == (50.0 / 3550.0) * 100.0`
       - `bin.color == TTColor.Zone5`
     - Verify duration sum of all Main View bins equals $3550\text{ s}$.
     - Verify percentage sum of all Main View bins equals $100.0\% \pm 0.1\%$.
  2. Inspect Sprint Tail Dataset (`result.sprintTail`):
     - Verify `sprintTail` is non-null.
     - Verify bin 0 is the leftmost **Return Bin**:
       - `bin.isReturn == true`
       - `bin.binIndex == 0`
       - `bin.rangeMin == 0`
       - `bin.rangeMax == 400`
       - `bin.durationSec == 3500L`
       - `bin.percentage == (3500.0 / 3550.0) * 100.0`
     - Verify remaining bins are 10 W sprint bins spanning $400\dots 940\text{ W}$ (54 sprint bins).
     - Verify duration sum of all Sprint Tail bins equals $3550\text{ s}$.
     - Verify percentage sum of all Sprint Tail bins equals $100.0\% \pm 0.1\%$.
* **Expected Result**:
  - Perfect mathematical conservation of durations and percentages across both views.

---

### Test Case 4: Model Backward Compatibility & Default Arguments Contract Test (`TST-UI-382.4`)
* **Scope**: Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCardTest.kt`
* **Preconditions**:
  - Existing legacy test callers and mock instantiations.
* **Action**:
  1. Instantiate `TelemetryHistogramBin` using legacy 7-parameter constructor:
     `TelemetryHistogramBin(binIndex = 0, rangeMin = 100, rangeMax = 110, durationSec = 10L, percentage = 10f, zoneIndex = 1, color = Color.Red)`
     - Verify `isOverflow == false`
     - Verify `isReturn == false`
  2. Instantiate `TelemetryHistogramData` using legacy 5-parameter constructor:
     `TelemetryHistogramData(binWidth = 10, dataMin = 100, dataMax = 200, totalActiveTimeSec = 100L, bins = listOf(bin))`
     - Verify `tailCutoff == null`
     - Verify `sprintTail == null`
* **Expected Result**:
  - Legacy instantiation compiles cleanly without argument errors, preserving 100% binary and source compatibility.

---

### Test Case 5: Readout Formatting, Independent Y-Scaling & Interaction State Contract Test (`TST-UI-382.5`)
* **Scope**: Contract & Layout Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCardTest.kt`
* **Preconditions**:
  - Folded power histogram dataset with `tailCutoff = 400 W`, sprint tail $400\dots 940\text{ W}$, return bar duration $3500\text{ s}$, max sprint bin duration $15\text{ s}$.
* **Action**:
  1. Evaluate independent Y-scaling calculation for Sprint Tail:
     - Filter out return bin: `sprintTail.bins.filter { !it.isReturn }`.
     - Max duration resolves to $15\text{ s}$ (NOT $3500\text{ s}$).
     - Verify a 15 s sprint bin scales to `heightFraction = 1.0f` ($96\text{ dp}$), while a 5 s sprint bin scales to `heightFraction = 0.333f` ($32\text{ dp}$).
  2. Evaluate Main View resting readout string formats:
     - Displays `${dataMin}–${tailCutoff} $unit` (`0–400 W`).
     - Overflow chip label: `> 400 W`.
     - Resolution label: `Δ 10 W`.
  3. Evaluate Sprint Tail resting readout string formats:
     - Return chip label: `0–400 W`.
     - Active sprint range: `400–940 W`.
     - Resolution label: `Δ 10 W`.
* **Expected Result**:
  - Sprint bins achieve full vertical resolution, and readout strings adhere to the contract.

---

### Test Case 6: 9-Language Localization & Format Specifier Parity Audit (`TST-UI-382.6`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramLocalizationTest.kt`
* **Goal**: Validate presence, non-blank values, and matching format specifiers across all 9 supported locales:
  - Default (EN), German (DE), Spanish (ES), French (FR), Italian (IT), Japanese (JA), Dutch (NL), Polish (PL), Portuguese (PT).

#### Localized String Definitions Table

| Key | Format Specifiers | EN | DE | ES | FR | IT | JA | NL | PL | PT |
| :--- | :---: | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `power_histogram_overflow_label` | `%1$d`, `%2$s` | `> %1$d %2$s` | `> %1$d %2$s` | `> %1$d %2$s` | `> %1$d %2$s` | `> %1$d %2$s` | `> %1$d %2$s` | `> %1$d %2$s` | `> %1$d %2$s` | `> %1$d %2$s` |
| `power_histogram_return_label` | `%1$d`, `%2$d`, `%3$s` | `%1$d–%2$d %3$s` | `%1$d–%2$d %3$s` | `%1$d–%2$d %3$s` | `%1$d–%2$d %3$s` | `%1$d–%2$d %3$s` | `%1$d–%2$d %3$s` | `%1$d–%2$d %3$s` | `%1$d–%2$d %3$s` | `%1$d–%2$d %3$s` |
| `power_histogram_overflow_desc` | `%1$d`, `%2$s` | `Switch to sprint tail view above %1$d %2$s` | `Zur Sprint-Tail-Ansicht über %1$d %2$s wechseln` | `Cambiar a vista de sprint por encima de %1$d %2$s` | `Passer à la vue sprint au-dessus de %1$d %2$s` | `Passa alla vista sprint sopra %1$d %2$s` | `%1$d %2$s 超のスプリント詳細表示に切り替え` | `Overschakelen naar sprint-tailweergave boven %1$d %2$s` | `Przełącz na widok sprintu powyżej %1$d %2$s` | `Mudar para a vista de sprint acima de %1$d %2$s` |
| `power_histogram_return_desc` | `%1$d`, `%2$s` | `Return to main histogram 0 to %1$d %2$s` | `Zurück zum Haupt-Histogramm 0 bis %1$d %2$s` | `Volver al histograma principal de 0 a %1$d %2$s` | `Revenir à l\'histogramme principal de 0 à %1$d %2$s` | `Torna all\'istogramma principale da 0 a %1$d %2$s` | `メインのヒストグラム (0 〜 %1$d %2$s) に戻る` | `Terugkeren naar hoofdhistogram 0 tot %1$d %2$s` | `Wróć do głównego histogramu od 0 do %1$d %2$s` | `Voltar ao histograma principal de 0 a %1$d %2$s` |

* **Expected Result**: 100% parity across all 9 locales, zero missing entries, zero format specifier mismatches, and compliance with Rule 49 (no leading ASCII symbol prefixes in strings).

---

### Test Case 7: Clean-Room Full Suite Regression Execution (`TST-UI-382.7`)
* **Scope**: Clean-Room Full Regression Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full project test suite with zero failures and zero regressions.
* **Expected Result**: 100% tests pass.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-382.1` | Unit | `TelemetryHistogramCalculator.calculatePowerHistogram` | `REQ-UI-419.1` | Specified |
| `TST-UI-382.2` | Unit | `TelemetryHistogramCalculator.calculatePowerHistogram` | `REQ-UI-419.1` | Specified |
| `TST-UI-382.3` | Unit | `TelemetryHistogramCalculator.calculatePowerHistogram` | `REQ-UI-419.1`, `REQ-UI-419.2` | Specified |
| `TST-UI-382.4` | Contract | `TelemetryHistogramBin`, `TelemetryHistogramData` | `REQ-UI-419.2` | Specified |
| `TST-UI-382.5` | Contract | `TelemetryHistogramChart` | `REQ-UI-419.3`, `REQ-UI-419.4`, `REQ-UI-419.5` | Specified |
| `TST-UI-382.6` | Localization | `TelemetryHistogramLocalizationTest` | `REQ-UI-419.6`, `REQ-UI-106` | Specified |
| `TST-UI-382.7` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |

