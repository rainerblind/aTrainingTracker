# Stage 2: Requirement & Test Specification - ATT-4370: Align Power Histogram tail cutoff to zone boundary and make header range chip highlighting consistent

**Ticket**: [ATT-4370](https://rainerblind.atlassian.net/browse/ATT-4370)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-419` (*Power Histogram Rare Sprint Spike Folding, Power Zone Boundary Snapping, and Consistent Symmetrical Header Range Toggle Navigation*)  
**Test Spec ID**: `TST-UI-382`  
**Branch**: `feature/ATT-4370`  
**Author**: AI Agent 1 (`subagent_flash` / Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (`REQ-UI-419`)

### 1.1 Problem Statement & Rationale
During Sprint 2026-43.1 human Joint Review of `ATT-4293` (*"Fold rare high-power spikes into overflow bar in Power Histogram with Sprint Tail zoom"*), two key UX and domain modeling defects were identified on-device in `TelemetryHistogramChart.kt` and `TelemetryHistogramCalculator.kt`:
1. **Inverted and Asymmetric Header Range Chip Highlighting**:
   - In the Main View (`0..X W`), the clickable action chip (`> X W →`) was styled with a blue `primaryContainer` background while the active range (`0–X W`) was plain unstyled text, misleading athletes into believing `> X W` was the currently active view.
   - In the Sprint Tail View (`X..Y W`), the return button (`← 0–X W`) used `surfaceVariant` while the active sprint range (`X–Y W`) was plain text with neither receiving `primaryContainer` highlighting.
   - Symmetrical 2-segment toggle styling (Option A from Sprint-Start Ceremony 1) is required so that the **active range segment is always highlighted** (`primaryContainer`, bold text, non-clickable) and the inactive range segment is always clickable (`surfaceVariant`, directional navigation arrow).
2. **Arbitrary 50 W Rounding Severing Power Zones**:
   - The tail cutoff candidate ($P_{99}$, floored at `thresholds.z4Max`) was rounded up solely to the next 50 W multiple, severing Zone 5 or Zone 6 right in the middle just before their physiological boundaries (e.g. cutting Zone 6 off at 400 W when its upper boundary is 450 W).
   - Aligning the cutoff upward to the enclosing upper Power Zone boundary (`thresholds.z4Max`, `thresholds.z5Max`, or `thresholds.z6Max`) and aligning to `binWidth` guarantees that the Main View contains intact, physiologically complete Coggan training zones, while rare sprint efforts above Zone 6 are isolated in the Sprint Tail View.

### 1.2 Functional & Architectural Requirements
The system SHALL satisfy the following technical and functional requirements:

1. **Zone Boundary Snapping in `TelemetryHistogramCalculator.calculateTailCutoff` (`REQ-UI-419.1`)**:
   - The system SHALL locate the candidate 99th percentile active duration cutoff ($P_{99}$) and floor it at the athlete's Zone 4 upper boundary (`thresholds.z4Max`):
     $$\text{candidate} = \max(P_{99}, \text{thresholds.z4Max})$$
   - The system SHALL snap the candidate cutoff upward to the enclosing upper Power Zone boundary:
     - If $\text{candidate} \le \text{thresholds.z4Max}$: $\text{rawCutoff} = \text{thresholds.z4Max}$.
     - If $\text{thresholds.z4Max} < \text{candidate} \le \text{thresholds.z5Max}$: $\text{rawCutoff} = \text{thresholds.z5Max}$.
     - If $\text{thresholds.z5Max} < \text{candidate} \le \text{thresholds.z6Max}$: $\text{rawCutoff} = \text{thresholds.z6Max}$.
     - If $\text{candidate} > \text{thresholds.z6Max}$ (above Zone 6, into Zone 7) or if zone thresholds are unconfigured: fall back to rounding up to the nearest 50 W boundary:
       $$\text{rawCutoff} = \lceil \text{candidate} / 50 \rceil \times 50$$
   - The candidate cutoff SHALL be aligned to the histogram bin width:
     $$\text{tailCutoff} = \lceil \text{rawCutoff} / \text{binWidth} \rceil \times \text{binWidth}$$
   - The system SHALL preserve all three tail-folding gating preconditions:
     - Gating 1: $\text{dataMax} - \text{tailCutoff} \ge 10 \times \text{binWidth}$ ($\ge 100\text{ W}$ in tail);
     - Gating 2: $\text{dataMax} \ge \text{tailCutoff} \times 1.25$ (tail exceeds cutoff by $\ge 25\%$);
     - Gating 3: Active duration in bins $\ge \text{tailCutoff}$ is strictly $> 0$.
   - When gating conditions are not met or when computing Heart Rate histograms (`calculateHeartRateHistogram`), the system SHALL return a standard linear dataset (`tailCutoff = null`, `sprintTail = null`).

2. **Domain Models Backward Compatibility in `ZoneDistributionModels.kt` (`REQ-UI-419.2`)**:
   - `TelemetryHistogramBin` SHALL retain `val isOverflow: Boolean = false` and `val isReturn: Boolean = false`.
   - `TelemetryHistogramData` SHALL retain `val tailCutoff: Int? = null` and `val sprintTail: TelemetryHistogramData? = null`.
   - In Main View, the overflow bin has `isOverflow = true`, `rangeMin = tailCutoff`, `rangeMax = dataMax`, and reflects the peak zone in `tailBins`.
   - In Sprint Tail View, the return bin has `isReturn = true`, `rangeMin = dataMin`, `rangeMax = tailCutoff`, and `color = DarkOutline`.

3. **Symmetrical Two-Segment Header Range Toggle Navigation in `TelemetryHistogramChart.kt` (`REQ-UI-419.3`)**:
   - `TelemetryHistogramChart` SHALL maintain reactive view state `var isSprintTailView by remember(histogram) { mutableStateOf(false) }`.
   - The resting header readout SHALL render a symmetrical 2-segment range toggle alongside the resolution indicator (`Δ ${histogram.binWidth} $unit`), where the active view range is consistently highlighted and the inactive range serves as the clickable view switch action:
     - **Main View (`!isSprintTailView`)**:
       - *Segment 1 (Main Range `${histogram.dataMin}–${histogram.tailCutoff} $unit`)*: Highlighted with `primaryContainer.copy(alpha = 0.7f)`, bold text in `onPrimaryContainer`, non-clickable.
       - *Segment 2 (Sprint Tail Range)*: Unhighlighted clickable chip with `surfaceVariant.copy(alpha = 0.6f)`, text in `onSurfaceVariant`, and trailing forward arrow icon (`Icons.AutoMirrored.Filled.ArrowForward`), displaying `power_histogram_overflow_label` (`> ${histogram.tailCutoff} $unit`). Tapping Segment 2 or the rightmost overflow bar on the canvas switches to Sprint Tail View (`isSprintTailView = true`) and clears `selectedBinIndex`.
     - **Sprint Tail View (`isSprintTailView`)**:
       - *Segment 1 (Main Range)*: Unhighlighted clickable chip with `surfaceVariant.copy(alpha = 0.6f)`, text in `onSurfaceVariant`, and leading back arrow icon (`Icons.AutoMirrored.Filled.ArrowBack`), displaying `power_histogram_return_label` (`${histogram.dataMin}–${histogram.tailCutoff} $unit`). Tapping Segment 1 or the leftmost return bar on the canvas switches to Main View (`isSprintTailView = false`) and clears `selectedBinIndex`.
       - *Segment 2 (Sprint Tail Range `${histogram.tailCutoff}–${histogram.dataMax} $unit`)*: Highlighted with `primaryContainer.copy(alpha = 0.7f)`, bold text in `onPrimaryContainer`, non-clickable.

4. **Independent Y-Axis Scaling & Minimum Visible Heights (`REQ-UI-419.4`)**:
   - In Sprint Tail View, vertical Y-axis scaling `maxDuration` SHALL be computed strictly across sprint bins (`filter { !it.isReturn }`), excluding the bulk duration of the return bar, elevating 3–15 second sprint efforts to full canvas height ($96\text{ dp}$).
   - In Main View, `maxDuration` is computed across all visible bins (`bins.maxOfOrNull { it.durationSec }`), with the overflow bar rendered with a minimum visible height ($\ge 6\text{ dp}$).

5. **Bin Inspection & Drag Scrubbing Parity (`REQ-UI-419.5`)**:
   - Tapping or dragging across regular bins in either view SHALL update `selectedBinIndex` and display that bin's exact wattage range, duration, percentage, and zone badge (`Z1`–`Z7`) in the header readout.
   - Touching the overflow bar in Main View or return bar in Sprint Tail View immediately triggers the respective view transition without sticking in selected mode.

6. **9-Language Localization & Format Parity (`REQ-UI-419.6`)**:
   - String resources (`power_histogram_overflow_label`, `power_histogram_return_label`, `power_histogram_overflow_desc`, `power_histogram_return_desc`) SHALL exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with indexed positional specifiers (`%1$d`, `%2$s`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Zone 5 Snapping)**:
  * *Given* cycling power telemetry where $P_{99}$ active duration candidate falls within Zone 5 (e.g. 320 W where `z4Max = 294 W` and `z5Max = 336 W`),
  * *When* `calculatePowerHistogram` evaluates the tail cutoff,
  * *Then* `tailCutoff` snaps to `thresholds.z5Max` (aligned to `binWidth`, e.g. 340 W if binWidth is 10 W and 336 W rounds up to 340 W, or 336 W exact), keeping all of Zone 5 in the Main View.
* **Criterion 2 (Zone 6 Snapping)**:
  * *Given* cycling power telemetry where $P_{99}$ active duration candidate falls within Zone 6 (e.g. 380 W where `z5Max = 350 W` and `z6Max = 450 W`),
  * *When* `calculatePowerHistogram` evaluates the tail cutoff,
  * *Then* `tailCutoff` snaps to `thresholds.z6Max` (450 W), keeping all of Zone 6 in the Main View and isolating Zone 7 in the tail.
* **Criterion 3 (Zone 7 / Above-Z6 Fallback)**:
  * *Given* cycling power telemetry where $P_{99}$ active duration candidate exceeds `thresholds.z6Max` (e.g. 520 W where `z6Max = 450 W`),
  * *When* `calculatePowerHistogram` evaluates the tail cutoff,
  * *Then* `tailCutoff` falls back to rounding up to the nearest 50 W multiple (550 W).
* **Criterion 4 (Main View Range Toggle Affordance)**:
  * *Given* the Power Histogram in Main View (`!isSprintTailView`),
  * *When* the header readout renders,
  * *Then* Segment 1 (`0–X W`) is highlighted in a `primaryContainer` pill with bold `onPrimaryContainer` text, and Segment 2 (`> X W →`) is rendered as an unhighlighted clickable chip in a `surfaceVariant` container with `onSurfaceVariant` text and forward arrow.
* **Criterion 5 (Sprint Tail View Range Toggle Affordance)**:
  * *Given* the Power Histogram in Sprint Tail View (`isSprintTailView`),
  * *When* the header readout renders,
  * *Then* Segment 1 (`← 0–X W`) is rendered as an unhighlighted clickable chip in a `surfaceVariant` container with `onSurfaceVariant` text and back arrow, and Segment 2 (`X–Y W`) is highlighted in a `primaryContainer` pill with bold `onPrimaryContainer` text.
* **Criterion 6 (Gating & Linear Invariance)**:
  * *Given* a steady workout without extended tail or Heart Rate telemetry,
  * *When* histogram calculations run,
  * *Then* `tailCutoff` remains `null`, `sprintTail` remains `null`, and the chart renders in standard linear mode.

### 1.4 System Invariants
1. **Heart Rate Histogram Invariance**: Heart Rate histograms remain strictly 5-zone linear and never folded (`tailCutoff = null`, `sprintTail = null`).
2. **Mathematical Duration & Percentage Conservation**: Total active workout duration and percentage sums ($100.0\% \pm 0.1\%$) are mathematically conserved across Main View regular bins + overflow bin, and across Sprint Tail View return bin + sprint bins.
3. **Tail Folding Gating Integrity**: Gating conditions ($\ge 100\text{ W}$ in tail, $\ge 1.25\times$ ratio, active duration $> 0$) remain strictly enforced to prevent folding symmetric or non-skewed workouts.
4. **9-Language Translation Parity**: String resources and format tokens (`%1$d`, `%2$s`) remain 100% valid and verified across all 9 locales.
5. **Zero Regression**: 100% unit test pass rate across `./gradlew testDebugUnitTest`.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. *Original Requirement ID & Target*:
   - Complements `REQ-UI-372.5` (`TelemetryHistogramChart` rendered when `effectiveDisplayMode == HISTOGRAM`), introduced in Sprint 2026-40.8 (`ATT-1845`), refined in Sprint 2026-42.4 (`ATT-3390`), decoupled in Sprint 2026-42.5 (`REQ-UI-388`, `ATT-3773`), expanded in Sprint 2026-43.1 (`ATT-4293`, commit `5694bca5` and `ATT-4294`, commit `5c5367c3`), and refined in Sprint 2026-43.2 (`ATT-4370`).
   - Targets: `TelemetryHistogramCalculator.kt`, `TelemetryHistogramChart.kt`, `ZoneDistributionModels.kt`, and `PowerZoneDistributionCard.kt`.

2. *Historical Origin & Commit Trace*:
   - Commit `7b14d9a2` (`ATT-1845`) introduced fine-grained frequency histograms with linear binning.
   - Commit `38706240` (`ATT-3390`) introduced 3-way mode switching on individual zone cards.
   - Commits `b989e1ce` (`ATT-3676`) and `e927c3da` (`ATT-3773`) established `HISTOGRAM` as default in Workout Details.
   - Commit `5694bca5` (`ATT-4293`) introduced tail folding with 50 W multiple rounding and asymmetric header chip buttons.
   - Commit `5c5367c3` (`ATT-4294`) added 7 Coggan power zones (`Zone 6` brown, `Zone 7` black).

3. *Root Reason for Existing Formulation*:
   - 50 W rounding was originally introduced as a simple numerical rounder before full 7-zone modeling, which caused awkward splits through Zone 5 and Zone 6 just before their upper physiological boundaries.
   - The clickable chip in Main View was styled with `primaryContainer` for visual prominence, which unintentionally conveyed active selection in a 2-view comparison, while Sprint Tail View used unhighlighted `surfaceVariant` with plain text.

4. *Preservation of Core Invariants*:
   - Snapping to upper power zone boundaries reinforces `REQ-UI-419` by ensuring the Main View contains complete, unsevered training zones (Z1–Z4, Z1–Z5, or Z1–Z6).
   - Rides without extended tails (`dataMax < tailCutoff * 1.25` or tail $< 100\text{ W}$) retain standard linear mode (`tailCutoff = null`).
   - Heart Rate histograms remain 100% linear.
   - Total duration and percentage sums remain mathematically conserved ($100.0\% \pm 0.1\%$).
   - Existing resting readout string contracts remain satisfied; 3-way mode switching is preserved.

---

## 2. Test Specification (`TST-UI-382`)

### Test Case 1: Zone Boundary Snapping, Upper Boundary Alignment & Fallback (`TST-UI-382.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
* **Preconditions**: Power thresholds configured with `z1Max = 150`, `z2Max = 200`, `z3Max = 250`, `z4Max = 300`, `z5Max = 350`, `z6Max = 450`.
* **Action**:
  1. Test dataset where $P_{99} \le 220\text{ W}$: verify candidate floored at `thresholds.z4Max` snaps to `300 W`.
  2. Test dataset where $P_{99} = 320\text{ W}$ (in Zone 5, $300 < 320 \le 350$): verify `tailCutoff` snaps to `thresholds.z5Max` (`350 W`).
  3. Test dataset where $P_{99} = 380\text{ W}$ (in Zone 6, $350 < 380 \le 450$): verify `tailCutoff` snaps to `thresholds.z6Max` (`450 W`).
  4. Test dataset where $P_{99} = 520\text{ W}$ (above Zone 6, $> 450$): verify fallback to nearest 50 W multiple (`550 W`).
  5. Test bin width alignment: with non-standard threshold (e.g. `z5Max = 336 W` and `binWidth = 10 W`), verify `tailCutoff` aligns upward to `340 W` (`tailCutoff % binWidth == 0`).
* **Expected Result**: All cutoff values snap to upper zone boundaries or clean bin-aligned multiples; zero awkward zone truncations.

### Test Case 2: Tail Folding Gating & Linear Heart Rate Invariance (`TST-UI-382.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
* **Preconditions**: Standard thresholds.
* **Action**:
  1. Gating 1 check: tail width $< 100\text{ W}$ returns `null`.
  2. Gating 2 check: $\text{dataMax} < \text{tailCutoff} \times 1.25$ returns `null`.
  3. Gating 3 check: tail duration $\le 0$ returns `null`.
  4. Heart rate telemetry check: `calculateHeartRateHistogram` returns `tailCutoff == null` and `sprintTail == null`.
* **Expected Result**: Gating rules enforced; heart rate histograms 100% linear.

### Test Case 3: Main View Overflow Bin & Sprint Tail Return Bin Aggregation (`TST-UI-382.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
* **Preconditions**: Telemetry with valid sprint tail.
* **Action**:
  1. Verify Main View contains bins up to `tailCutoff` plus 1 overflow bin with `isOverflow = true`, `rangeMin = tailCutoff`, `rangeMax = dataMax`.
  2. Verify Sprint Tail contains return bin at index 0 with `isReturn = true`, `rangeMin = dataMin`, `rangeMax = tailCutoff`.
  3. Verify mathematical conservation: sum of bin durations equals `totalActiveTimeSec`, percentages sum to $100.0\% \pm 0.1\%$.
* **Expected Result**: Complete duration and percentage conservation; correct overflow/return bin metadata.

### Test Case 4: Model Backward Compatibility & Default Arguments (`TST-UI-382.4`)
* **Scope**: Unit Test / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCardTest.kt`
* **Preconditions**: Construction without optional parameters.
* **Action**:
  1. Construct `TelemetryHistogramBin` without `isOverflow` / `isReturn`: verify defaults `false`.
  2. Construct `TelemetryHistogramData` without `tailCutoff` / `sprintTail`: verify defaults `null`.
* **Expected Result**: Backward compatibility preserved.

### Test Case 5: Symmetrical 2-Segment Header Range Toggle Layout Contract (`TST-UI-382.5`)
* **Scope**: UI Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCardTest.kt`
* **Preconditions**: Source file inspection and layout structure audit of `TelemetryHistogramChart.kt`.
* **Action**:
  1. Verify Main View contract:
     - Segment 1 (`0–X W`) highlighted with `primaryContainer` / `onPrimaryContainer`, bold text, non-clickable.
     - Segment 2 (`> X W`) unhighlighted with `surfaceVariant` / `onSurfaceVariant`, forward arrow icon `Icons.AutoMirrored.Filled.ArrowForward`, clickable.
  2. Verify Sprint Tail View contract:
     - Segment 1 (`0–X W`) unhighlighted with `surfaceVariant` / `onSurfaceVariant`, back arrow icon `Icons.AutoMirrored.Filled.ArrowBack`, clickable.
     - Segment 2 (`X–Y W`) highlighted with `primaryContainer` / `onPrimaryContainer`, bold text, non-clickable.
  3. Verify independent Y-axis scaling math in Sprint Tail View (`maxDuration` excludes return bin).
* **Expected Result**: Symmetrical affordances verified; active range highlighted in both views; clickable switches consistently styled.

### Test Case 6: 9-Language Localization & Format Specifier Parity (`TST-UI-382.6`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramLocalizationTest.kt`
* **Goal**: Verify string presence and matching `%1$d`/`%2$s` format specifiers across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) for:
  - `power_histogram_overflow_label`
  - `power_histogram_return_label`
  - `power_histogram_overflow_desc`
  - `power_histogram_return_desc`
* **Expected Result**: 100% parity, zero missing keys, zero specifier mismatches.

### Test Case 7: Clean-Room Full Suite Regression (`TST-UI-382.7`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.
* **Expected Result**: BUILD SUCCESSFUL, all unit tests pass.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Test Class | Requirement Clauses | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-382.1` | Unit | `TelemetryHistogramCalculatorTest.kt` | `REQ-UI-419.1` | Specified |
| `TST-UI-382.2` | Unit | `TelemetryHistogramCalculatorTest.kt` | `REQ-UI-419.1` | Specified |
| `TST-UI-382.3` | Unit | `TelemetryHistogramCalculatorTest.kt` | `REQ-UI-419.2` | Specified |
| `TST-UI-382.4` | Contract | `TelemetryHistogramCardTest.kt` | `REQ-UI-419.2` | Specified |
| `TST-UI-382.5` | UI Contract | `TelemetryHistogramCardTest.kt` | `REQ-UI-419.3`, `REQ-UI-419.4`, `REQ-UI-419.5` | Specified |
| `TST-UI-382.6` | Localization | `TelemetryHistogramLocalizationTest.kt` | `REQ-UI-419.6`, `REQ-UI-106` | Specified |
| `TST-UI-382.7` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
