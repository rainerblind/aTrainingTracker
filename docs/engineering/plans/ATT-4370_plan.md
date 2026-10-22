# Stage 3: Implementation Plan - ATT-4370: Align Power Histogram tail cutoff to zone boundary and make header range chip highlighting consistent

**Ticket**: [ATT-4370](https://rainerblind.atlassian.net/browse/ATT-4370)  
**Sub-task**: [ATT-4404](https://rainerblind.atlassian.net/browse/ATT-4404) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-419` (`REQ-UI-419.1`, `REQ-UI-419.3`)  
**Test Mapping**: `TST-UI-382` (`TST-UI-382.1`, `TST-UI-382.5`)  
**Branch**: `feature/ATT-4370`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In the post-workout analysis view of **aTrainingTracker**, athletes inspect physiological training zone distributions (`Leistungszonen` card in `PowerZoneDistributionCard.kt`) and toggle between three visualization modes (`ZoneCardDisplayMode`: `STACKED_BAR`, `COLUMNS`, and `HISTOGRAM`). When `HISTOGRAM` is selected, `TelemetryHistogramChart.kt` renders fine-grained 10 W frequency bins across the recorded power telemetry.

During Sprint 2026-43.1 human Joint Review of `ATT-4293` (*"Fold rare high-power spikes into overflow bar in Power Histogram with Sprint Tail zoom"*), on-device testing revealed two significant user experience and mathematical consistency flaws:
1. **Asymmetric and Inverted Header Range Highlighting**:
   - In the Main View (`0..X W`), the clickable navigation chip (`> X W →`) was styled with a blue `primaryContainer` pill background, while the active range (`0–X W`) was rendered as unadorned text. This created an inverted selection affordance: `> X W` appeared to be the active view.
   - In the Sprint Tail View (`X..Y W`), the return button (`← 0–X W`) used unhighlighted `surfaceVariant`, and the active sprint range (`X–Y W`) was plain text. Neither segment received `primaryContainer` highlighting.
   - Per Sprint-Start Ceremony 1 resolved decision (Option A), the range indicator must be rendered as a symmetrical 2-segment control where the **currently active range is highlighted** (`primaryContainer`, bold text, non-clickable) and the inactive range is clickable (`surfaceVariant`, directional navigation arrow) across both views.
2. **Arbitrary 50 W Rounding Severing Power Zones**:
   - In `TelemetryHistogramCalculator.calculateTailCutoff`, the candidate cutoff ($P_{99}$, floored at `thresholds.z4Max`) was rounded up solely to the next 50 W multiple (`((rawCutoff + 49) / 50) * 50`).
   - This arbitrary quantization severed Coggan power zones right in the middle just before their upper boundaries (e.g. cutting Zone 6 at 400 W when its upper boundary is 450 W).
   - Per Sprint-Start Ceremony 1, the cutoff candidate must snap upward to the enclosing **upper Power Zone boundary** (`thresholds.z4Max`, `thresholds.z5Max`, or `thresholds.z6Max`), falling back to the 50 W multiple if above Zone 6, and align to the histogram `binWidth`.

---

## 2. Traceability & Requirements Mapping

This implementation plan establishes complete bidirectional traceability between `REQ-UI-419`, test cases `TST-UI-382.1` through `TST-UI-382.7`, and the construction deliverables:

| Requirement ID | Requirement Clause | Test Case ID | Target Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-UI-419.1` | **Zone Boundary Cutoff Snapping & Bin Width Alignment**: Snap candidate cutoff ($P_{99}$ floored at `z4Max`) upward to enclosing upper zone boundary (`z4Max`, `z5Max`, or `z6Max`), falling back to 50 W multiple if $> \text{z6Max}$, and align to `binWidth`. Preserve gating rules ($\ge 100\text{ W}$, $\ge 1.25\times$, duration $> 0$). | `TST-UI-382.1`, `TST-UI-382.2` | `TelemetryHistogramCalculatorTest.kt`: Unit tests verifying Z4/Z5/Z6 boundary snapping, Z7 50 W fallback, bin width alignment, and gating invariance. |
| `REQ-UI-419.2` | **Dataset Aggregation & Conservation**: Rightmost overflow bin and leftmost return bin aggregation with mathematical conservation of duration and percentages ($100.0\% \pm 0.1\%$). | `TST-UI-382.3`, `TST-UI-382.4` | `TelemetryHistogramCalculatorTest.kt`: Unit tests asserting conservation of duration and percentage sums across regular, overflow, and return bins. |
| `REQ-UI-419.3` | **Symmetrical 2-Segment Header Range Toggle Navigation**: Render consistent 2-segment toggle in `TelemetryHistogramChart.kt` where the active range segment is highlighted (`primaryContainer`, bold, non-clickable) and inactive segment is clickable (`surfaceVariant`, navigation arrow). | `TST-UI-382.5` | `TelemetryHistogramCardTest.kt`: UI contract tests verifying symmetrical styling tokens and state transitions. |
| `REQ-UI-419.4` | **Independent Y-Axis Scaling**: Local `maxDuration` in Sprint Tail View computed strictly across sprint bins (`filter { !it.isReturn }`). | `TST-UI-382.5` | `TelemetryHistogramCardTest.kt`: Y-axis height fraction assertions on sprint bins versus return bar. |
| `REQ-UI-419.5` | **Bin Inspection & Drag Scrubbing Parity**: Continuous tap and drag scrubbing across regular bins displaying exact wattage, duration, percentage, and zone badge (`Z1`–`Z7`). | `TST-UI-382.5` | `TelemetryHistogramCardTest.kt`: Gesture disambiguation and bin inspection contract tests. |
| `REQ-UI-419.6` | **9-Language Localization & Format Parity**: Maintain `power_histogram_overflow_label`, `power_histogram_return_label`, `power_histogram_overflow_desc`, and `power_histogram_return_desc` across all 9 canonical locales. | `TST-UI-382.6` | `TelemetryHistogramLocalizationTest.kt`: Automated 9-locale parity audit asserting non-empty strings and format specifier compatibility. |
| `REQ-PRO-001` | **Clean-Room Regression Suite**: 100% test pass rate across the full project test suite with zero failures and zero regressions. | `TST-UI-382.7` | `./gradlew testDebugUnitTest` |

---

## 3. System Invariants & Preserved Behavior

1. **Strict Code Lock in Stage 3 (Core Rule 4)**: Zero modifications to production source code (`app/src/main/...`) occur during Stage 3. Production construction is strictly deferred to Stage 4 after Gate 3 audit approval (`check-gate`).
2. **Pure Mathematical Calculation & Statelessness**: `TelemetryHistogramCalculator.kt` remains a pure, stateless computation object with zero database queries, zero Android framework dependencies, and zero disk I/O.
3. **Heart Rate Histogram Invariance**: Heart rate frequency histograms (`calculateHeartRateHistogram`) remain strictly 5-zone uniform and linear at 2 bpm bin width. Tail folding is strictly gated to Cycling Power telemetry.
4. **Tail Folding Gating Preconditions**: If a cycling workout lacks an extended sprint tail (failing the $\ge 100\text{ W}$ tail width or $\ge 25\%$ tail excess ratio), `tailCutoff` and `sprintTail` resolve to `null`, and the chart renders in standard uniform linear mode with zero visual change.
5. **Mathematical Conservation of Duration & Percentage**:
   - In Main View: $\sum \text{regularBins.duration} + \text{overflowBin.duration} = \text{totalActiveTimeSec}$.
   - In Sprint Tail View: $\text{returnBin.duration} + \sum \text{sprintBins.duration} = \text{totalActiveTimeSec}$.
   - Sum of percentages equals $100.0\% \pm 0.1\%$ across both views.
6. **Existing Readout Grep Contract Tests**:
   - The contract test `testTelemetryHistogramChart_streamlinedReadoutContract` checks for:
     - `content.contains("${histogram.dataMin}–${histogram.dataMax} $unit")`
     - `content.contains("Δ ${histogram.binWidth} $unit")`
     - `content.contains("power_histogram_overflow_label")`
     - `content.contains("power_histogram_return_label")`
     - `content.contains("power_histogram_overflow_desc")`
     - `content.contains("power_histogram_return_desc")`
     - `content.contains("isSprintTailView")`
   - All string tokens and source contracts will remain fully preserved.

---

## 4. Proposed Architectural Changes

### Component 1: `TelemetryHistogramCalculator.kt` (Pure Calculation Engine)
- **Layer**: UI / Domain Mathematical Calculation
- **Package**: `com.atrainingtracker.trainingtracker.ui.aftermath.zones`
- **Method Modified**: `calculateTailCutoff(bins, totalDuration, thresholds, binWidth, dataMax): Int?`
- **Algorithm Design**:
  1. Determine candidate $P_{99}$ percentile from cumulative bin durations:
     $$\text{accumulatedDuration} \ge 0.985 \times \text{totalDuration}$$
  2. Enforce Zone 4 floor:
     $$\text{candidate} = \max(P_{99}, \text{thresholds.z4Max})$$
  3. Snap upward to enclosing upper Power Zone boundary:
     - If $\text{candidate} \le \text{thresholds.z4Max} \implies \text{rawCutoff} = \text{thresholds.z4Max}$.
     - If $\text{candidate} \le \text{thresholds.z5Max} \implies \text{rawCutoff} = \text{thresholds.z5Max}$.
     - If $\text{candidate} \le \text{thresholds.z6Max} \implies \text{rawCutoff} = \text{thresholds.z6Max}$.
     - If $\text{candidate} > \text{thresholds.z6Max} \implies \text{rawCutoff} = \lceil \text{candidate} / 50 \rceil \times 50$ (50 W rounding fallback).
  4. Align to histogram bin width:
     $$\text{tailCutoff} = \text{if } (\text{binWidth} > 1) \; \lceil \text{rawCutoff} / \text{binWidth} \rceil \times \text{binWidth} \; \text{else} \; \text{rawCutoff}$$
  5. Apply standard gating:
     - $\text{dataMax} - \text{tailCutoff} \ge 10 \times \text{binWidth}$
     - $\text{dataMax} \ge \text{tailCutoff} \times 1.25$
     - Active duration $\ge \text{tailCutoff}$ is $> 0$.

### Component 2: `TelemetryHistogramChart.kt` (Jetpack Compose View)
- **Layer**: UI Presentation (Jetpack Compose)
- **Package**: `com.atrainingtracker.trainingtracker.ui.aftermath.zones`
- **Composable Modified**: `TelemetryHistogramChart(histogram, unit, modifier)`
- **Header Readout Structure**:
  In both Main View (`!isSprintTailView`) and Sprint Tail View (`isSprintTailView`), replace the asymmetric text-and-button layout with a symmetrical 2-segment row:
  - **Segment 1 (`0–X W` Main Range)**:
    - *Main View (Active)*: Rendered in `Surface` with `shape = RoundedCornerShape(4.dp)`, `color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)`, containing `Text("${histogram.dataMin}–${histogram.tailCutoff} $unit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))`. Non-clickable.
    - *Sprint Tail View (Inactive Switch Action)*: Rendered in `Surface` with `shape = RoundedCornerShape(4.dp)`, `color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)`, `modifier = Modifier.clickable { isSprintTailView = false; selectedBinIndex = null }`, containing a `Row` with `Icon(Icons.AutoMirrored.Filled.ArrowBack, ...)` and `Text(stringResource(R.string.power_histogram_return_label, histogram.dataMin, histogram.tailCutoff, unit), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)`.
  - **Segment 2 (`> X W` / `X–Y W` Sprint Tail Range)**:
    - *Main View (Inactive Switch Action)*: Rendered in `Surface` with `shape = RoundedCornerShape(4.dp)`, `color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)`, `modifier = Modifier.clickable { isSprintTailView = true; selectedBinIndex = null }`, containing a `Row` with `Text(stringResource(R.string.power_histogram_overflow_label, histogram.tailCutoff, unit), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)` and `Icon(Icons.AutoMirrored.Filled.ArrowForward, ...)`.
    - *Sprint Tail View (Active)*: Rendered in `Surface` with `shape = RoundedCornerShape(4.dp)`, `color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)`, containing `Text("${histogram.tailCutoff}–${histogram.dataMax} $unit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))`. Non-clickable.
  - **Resolution Indicator**:
    - Retains `Text("Δ ${histogram.binWidth} $unit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)`.

---

### UI Consistency (Rule 23 — mandatory per docs/design_guidelines.md Section 5)

* **Reference screen / component**:
  `PowerZoneDistributionCard.kt` (`Leistungszonen` card in `DetailedWorkoutView` and `WorkoutSummary`) and `TelemetryHistogramChart.kt` (fine-grained telemetry histogram).
* **Reused components**:
  - Material 3 `Surface` (compact pill container with `shape = RoundedCornerShape(4.dp)`)
  - Material 3 `Text` with `MaterialTheme.typography.labelSmall`
  - Material 3 `Icon` with `Icons.AutoMirrored.Filled.ArrowForward` and `Icons.AutoMirrored.Filled.ArrowBack`
  - Standard `Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically)`
* **Theme tokens**:
  - Shapes: `RoundedCornerShape(6.dp)` for header container, `RoundedCornerShape(4.dp)` for segment chips
  - Spacing: `4.dp` horizontal gap between segments, `6.dp` horizontal and `2.dp` vertical padding inside segment pills
  - Colors:
    - Active Highlighted Segment: `color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)`, `color = MaterialTheme.colorScheme.onPrimaryContainer`
    - Inactive Clickable Segment: `color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)`, `color = MaterialTheme.colorScheme.onSurfaceVariant`
    - Resolution text: `color = MaterialTheme.colorScheme.primary`
* **New one-off styles & justification**: None. Employs canonical Material 3 container tokens and typography standards.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Zone-Boundary Cutoff Snapping in `TelemetryHistogramCalculator.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculator.kt`
* **Changes**:
  - Update `calculateTailCutoff`:
    1. Floor `candidate` at `thresholds.z4Max`.
    2. Snap `rawCutoff` upward to `thresholds.z4Max`, `thresholds.z5Max`, `thresholds.z6Max`, or 50 W fallback.
    3. Align `tailCutoff` to `binWidth`: `((rawCutoff + binWidth - 1) / binWidth) * binWidth`.
    4. Validate gating conditions against `tailCutoff`.
* **Verification**: Code compiles without errors.

### Step 2: Implement Symmetrical 2-Segment Header Range Toggle in `TelemetryHistogramChart.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramChart.kt`
* **Changes**:
  - Refactor lines 163–262:
    1. Render 2-segment row for Main View: Segment 1 (`0–X W`) highlighted in `primaryContainer`, Segment 2 (`> X W →`) clickable in `surfaceVariant`.
    2. Render 2-segment row for Sprint Tail View: Segment 1 (`← 0–X W`) clickable in `surfaceVariant`, Segment 2 (`X–Y W`) highlighted in `primaryContainer`.
    3. Ensure existing grep contract strings (`${histogram.dataMin}–${histogram.dataMax} $unit`, `Δ ${histogram.binWidth} $unit`, etc.) remain satisfied.
* **Verification**: Code compiles without errors.

### Step 3: Update Unit Tests in `TelemetryHistogramCalculatorTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
* **Changes**:
  - Update `testCalculatePowerHistogram_p99CutoffAndFloor_tst_ui_382_1`:
    - Update Dataset A assertion from 400 to 450 W (snapping to `z6Max = 450 W`).
    - Add test case for `z5Max` snapping (e.g. $P_{99} = 320\text{ W} \implies 350\text{ W}$).
    - Add test case for above-Z6 50 W fallback (e.g. $P_{99} = 520\text{ W} \implies 550\text{ W}$).
    - Add test case for bin width alignment (`z5Max = 336\text{ W}`, `binWidth = 10\text{ W}` $\implies 340\text{ W}$).
  - Update `testCalculatePowerHistogram_mainAndSprintTailAggregation_tst_ui_382_3`:
    - Update expected `tailCutoff` from 400 to 450 W.
    - Update bin count from 41 to 46 bins (45 regular 10 W bins covering 0..450 W + 1 overflow bin).
    - Update overflow bin `rangeMin = 450` and return bin `rangeMax = 450`.
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCalculatorTest"
  ```

### Step 4: Update UI Contract Tests in `TelemetryHistogramCardTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCardTest.kt`
* **Changes**:
  - Update `testTelemetryHistogramChart_sprintTailReadoutAndSourceContract_tst_ui_382_5`:
    - Verify presence of `onPrimaryContainer` / `primaryContainer` highlighting for active range segment.
    - Verify presence of `surfaceVariant` / `onSurfaceVariant` for inactive clickable switch segment.
    - Verify presence of directional icons `ArrowForward` and `ArrowBack`.
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCardTest"
  ```

### Step 5: Full Clean-Room Regression Suite Execution
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Goal**: Assert 100% test pass rate across the complete project test suite with zero failures and zero regressions.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Execute targeted zone unit tests after each construction step.
  - Run full clean-room unit test suite (`./gradlew testDebugUnitTest`) verifying 100% pass rate.
  - Verify requirement governance compliance with `python3 tools/verify_requirement_governance.py`.
* **Rollback Plan**:
  - The changes are strictly isolated on branch `feature/ATT-4370`.
  - In the event of unexpected regressions, changes can be cleanly reverted via `git checkout -- .` without affecting `develop` or sprint integration branches.
