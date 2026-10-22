# Stage 1 Analysis: ATT-4370 - [Verbesserung] Align Power Histogram tail cutoff to zone boundary and make header range chip highlighting consistent

**Ticket**: [ATT-4370](https://rainerblind.atlassian.net/browse/ATT-4370)  
**Sub-task**: [ATT-4402](https://rainerblind.atlassian.net/browse/ATT-4402) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Branch**: `feature/ATT-4370`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-43.1 Joint Review of ticket **ATT-4293** (*"Fold rare high-power spikes into overflow bar in Power Histogram with Sprint Tail zoom"*), human on-device verification on Pixel 10 identified two significant user experience and domain modeling defects in the fine-grained Cycling Power frequency histogram (`TelemetryHistogramChart.kt` and `TelemetryHistogramCalculator.kt`):

### 1.1 Inconsistent Header Range Chip Highlighting
In `TelemetryHistogramChart.kt`, the header readout row displays the frequency range and view-switching controls:
1. **Inverted Affordance in Main View (`!isSprintTailView`)**:
   - The active frequency range (`0–X W`, e.g. `0–400 W`) is rendered as unadorned body text in `MaterialTheme.colorScheme.onSurfaceVariant`.
   - The clickable action to navigate to the Sprint Tail View (`> X W`, e.g. `> 400 W →`) is rendered inside a `Surface` with `primaryContainer.copy(alpha = 0.5f)` and bold `primary` text.
   - **Observable Usability Defect**: In Material 3, a container-filled pill badge adjacent to unstyled plain text conveys *active selection*. Consequently, an athlete looking at the Main View (0–400 W) perceives `> 400 W` as the currently selected view.
2. **Missing Active Highlighting in Sprint Tail View (`isSprintTailView`)**:
   - When switching to the Sprint Tail View (e.g. `400–940 W`), the return button (`← 0–400 W`) is styled with `surfaceVariant.copy(alpha = 0.6f)` and `onSurfaceVariant` text.
   - The active sprint range (`400–940 W`) is rendered as unadorned text in `onSurface`.
   - Neither element receives the blue `primaryContainer` tint.
3. **Synthesis**: The active view state is highlighted in neither view, while the clickable trigger is highlighted with a selected-looking background in only one view. Athletes cannot unambiguously discern which range represents active data versus which element is the navigation trigger.

### 1.2 Tail Cutoff Arbitrary Rounding Severing Power Zones
In `TelemetryHistogramCalculator.calculateTailCutoff`, the statistical 99th percentile active duration cutoff ($P_{99}$) is floored at the athlete's Zone 4 upper boundary (`thresholds.z4Max`) and then rounded up solely to the next 50 W multiple:
$$\text{rawCutoff} = \max(P_{99}, \text{thresholds.z4Max})$$
$$\text{tailCutoff} = \lceil \text{rawCutoff} / 50 \rceil \times 50$$

While this guarantees a 50 W rounded number, it completely ignores the athlete's physiological power zone thresholds (`PowerZoneThresholds`):
- **Example 1 (Zone 6 Truncation)**: An athlete has Zone 5 up to 350 W and Zone 6 (Anaerobic Capacity) up to 450 W. If $P_{99}$ is 380 W, the cutoff rounds up to 400 W. Zone 6 is severed right in the middle at 400 W, just 50 W before its upper boundary. The Main View displays 350–400 W of Zone 6, while the remaining 400–450 W is exiled into the Sprint Tail View alongside Zone 7.
- **Example 2 (Zone 5 Slivers)**: An athlete with FTP 280 W has `z4Max = 294 W` (105% FTP) and `z5Max = 336 W` (120% FTP). If $P_{99}$ is 280 W, flooring at `z4Max` (294 W) and rounding to 50 W yields 300 W. This isolates an awkward 6 W sliver of Zone 5 (295–300 W) in the Main View, while the remaining 36 W of Zone 5 is split into the tail.

### 1.3 Resolved User Decision from Sprint-Start Ceremony 1
During Sprint-Start Ceremony 1 for Sprint 2026-43.2, the user resolved these issues with concrete direction:
1. **Header Range Toggle Highlighting (Option A)**: Render the range toggle (`0–X W` vs. `> X W` / `X–Y W`) as a consistent 2-segment control where the **currently active range segment is highlighted** (selected segment styling with `primaryContainer`, bold text, non-clickable) and the inactive range segment is clickable to switch views (unhighlighted `surfaceVariant` styling with navigation arrow), ensuring 100% directional clarity.
2. **Tail Cutoff Alignment to Power Zone Boundary**: Instead of rounding raw $P_{99}$ solely to a 50 W multiple, snap the tail-folding cutoff upward to the enclosing **upper Power Zone boundary** (`z4Max`, `z5Max`, or `z6Max`), falling back to the 50 W multiple if above Zone 6 or if zone thresholds are unavailable, and align to the histogram bin width so that power zones are never awkwardly split just before their boundary.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Inverted and Asymmetric Styling in `TelemetryHistogramChart.kt`
Forensic analysis of `TelemetryHistogramChart.kt` (lines 163–263) reveals the structural cause of the highlighting defect:

```kotlin
// Main View (lines 163-210):
} else if (!isSprintTailView) {
    // Active range rendered as plain text:
    Text(
        text = "${histogram.dataMin}–${histogram.tailCutoff} $unit",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Inactive target rendered with highlighted primaryContainer:
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.clickable {
            isSprintTailView = true
            selectedBinIndex = null
        }
    ) {
        Row(...) {
            Text(
                text = stringResource(R.string.power_histogram_overflow_label, histogram.tailCutoff, unit),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, tint = MaterialTheme.colorScheme.primary)
        }
    }
    Text(text = "Δ ${histogram.binWidth} $unit", ...)
```

In the Sprint Tail View:
```kotlin
// Sprint Tail View (lines 211-262):
} else {
    // Inactive return target rendered with surfaceVariant (no primaryContainer):
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.clickable {
            isSprintTailView = false
            selectedBinIndex = null
        }
    ) {
        Row(...) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(R.string.power_histogram_return_label, histogram.dataMin, histogram.tailCutoff, unit),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    // Active sprint range rendered as plain text (no primaryContainer):
    Text(
        text = "${histogram.tailCutoff}–${histogram.dataMax} $unit",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(text = "Δ ${histogram.binWidth} $unit", ...)
```

**Failure Mechanism**:
- In Main View, the interactive switch chip was given `primaryContainer` styling under the assumption that buttons should look prominent.
- In Sprint Tail View, the return chip was given `surfaceVariant` styling under the assumption that returning is a secondary action.
- Because the active range label in both views was left as plain unstyled `Text`, the UI communicates an inverted semantic model: in Main View, the inactive button looks selected; in Sprint Tail View, neither looks selected.

### 2.2 Blind Heuristic Cutoff Quantization in `TelemetryHistogramCalculator.kt`
Forensic analysis of `TelemetryHistogramCalculator.kt` (lines 223–228) reveals:

```kotlin
// Floor at athlete's Zone 4 upper boundary (thresholds.z4Max)
val rawCutoff = maxOf(p99Candidate, thresholds.z4Max)

// Round up to clean 50 W boundary
val tailCutoff = ((rawCutoff + CUTOFF_ROUNDING_W - 1) / CUTOFF_ROUNDING_W) * CUTOFF_ROUNDING_W
```

**Failure Mechanism**:
- `CUTOFF_ROUNDING_W = 50` was chosen as a naive heuristic during ATT-4293 before the 7-zone Coggan model (`ATT-4294`) was completed.
- Coggan power zones are determined by physiological thresholds ($Z_1 \le 55\%$, $Z_2 \le 75\%$, $Z_3 \le 90\%$, $Z_4 \le 105\%$, $Z_5 \le 120\%$, $Z_6 \le 150\%$ of FTP). These boundaries rarely fall on clean 50 W multiples.
- When `rawCutoff` falls within Zone 5 ($z4Max < \text{rawCutoff} \le z5Max$) or Zone 6 ($z5Max < \text{rawCutoff} \le z6Max$), rounding up to 50 W creates an arbitrary cut point that severs the zone into two fragments across the two views.
- Because a primary purpose of the Power Histogram is inspecting time spent across Coggan training zones, cutting off midway through Zone 5 or Zone 6 degrades the analytical value of both views.

---

## 3. User Scope Grounding (ATT-1250)

To maintain strict alignment with user instructions and prevent scope creep:

### In-Scope Goals:
1. **Consistent 2-Segment Range Indicator (`TelemetryHistogramChart.kt`)**:
   - Refactor the resting header readout in `TelemetryHistogramChart.kt` to present a unified 2-segment range toggle in both views.
   - When in **Main View (`!isSprintTailView`)**:
     - `0–X W` segment is **highlighted** (`primaryContainer`, bold `onPrimaryContainer` / `primary` text), indicating active view state (non-clickable).
     - `> X W` segment is **unhighlighted** (`surfaceVariant`, `onSurfaceVariant` text, clickable with forward arrow icon `Icons.AutoMirrored.Filled.ArrowForward`), indicating navigation action to Sprint Tail View.
   - When in **Sprint Tail View (`isSprintTailView`)**:
     - `0–X W` segment is **unhighlighted** (`surfaceVariant`, `onSurfaceVariant` text, clickable with back arrow icon `Icons.AutoMirrored.Filled.ArrowBack`), indicating navigation action back to Main View.
     - `X–Y W` segment is **highlighted** (`primaryContainer`, bold `onPrimaryContainer` / `primary` text), indicating active view state (non-clickable).
   - Maintain the `Δ 10 W` resolution indicator on the right side of the header.
2. **Zone-Aligned Tail Cutoff Snapping (`TelemetryHistogramCalculator.kt`)**:
   - Refactor `calculateTailCutoff` to snap the candidate cutoff upward to the enclosing upper power zone boundary:
     - If candidate $\le \text{thresholds.z4Max}$: snap to `thresholds.z4Max`.
     - If $\text{thresholds.z4Max} < \text{candidate} \le \text{thresholds.z5Max}$: snap to `thresholds.z5Max`.
     - If $\text{thresholds.z5Max} < \text{candidate} \le \text{thresholds.z6Max}$: snap to `thresholds.z6Max`.
     - If $\text{candidate} > \text{thresholds.z6Max}$ (above Zone 6, into Zone 7) or if zone thresholds are unconfigured/invalid: fall back to rounding up to the nearest 50 W multiple.
   - Align the snapped cutoff to the histogram `binWidth` ($\lceil \text{cutoff} / \text{binWidth} \rceil \times \text{binWidth}$, typically 10 W) so the cutoff cleanly matches frequency bin edges.
   - Preserve all three tail-folding gating checks ($\text{tailWidth} \ge 10 \times \text{binWidth}$, $\text{dataMax} \ge \text{tailCutoff} \times 1.25$, active tail duration $> 0$).
3. **Comprehensive Unit Testing**:
   - Update `TelemetryHistogramCalculatorTest.kt` to verify zone-boundary cutoff snapping (Z4 max, Z5 max, Z6 max, Z7 fallback, and binWidth alignment).
   - Update `TelemetryHistogramCardTest.kt` to verify the consistent 2-segment header toggle layout contracts.
4. **Living Documentation Synchronization**:
   - Synchronize `docs/requirements.md` (`REQ-UI-419` refinement) and `docs/tests.md` (`TST-UI-382` refinement).

### Out-of-Scope Non-Goals (Scope Bounding):
1. **Heart Rate Histogram Invariance**: Do not alter `calculateHeartRateHistogram` or heart rate UI (heart rate remains strictly 5-zone linear, never folded).
2. **Bottom Mode Selector Invariance**: Do not alter the 3-way `SingleChoiceSegmentedButtonRow` mode switcher (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`) in `PowerZoneDistributionCard.kt`.
3. **Canvas Drawing & Scrubbing Invariance**: Do not modify canvas bar drawing geometry, 1 dp bar spacing, touch/drag scrubbing mechanics, or dark mode outline strokes (`outlineVariant`) for Zone 6/7.
4. **Tail Folding Gating Invariance**: Do not alter the gating thresholds ($\ge 100\text{ W}$, $\ge 1.25\times$ ratio, tail duration $> 0$).
5. **No Database Schema or Service Changes**: Zero changes to SQLite databases, repositories, `BANALService`, or background workers.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  - `REQ-UI-419` (*Power Histogram Rare Sprint Spike Folding, Overflow Bar, and Interactive Sprint Tail Zoom Navigation*), specifically:
    - `REQ-UI-419.1` (*Statistical 99% Tail-Folding Cutoff in TelemetryHistogramCalculator.kt*)
    - `REQ-UI-419.3` (*Two-View Interactive Navigation in TelemetryHistogramChart.kt*)
  - Targets:
    - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculator.kt`
    - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramChart.kt`
    - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
    - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCardTest.kt`

* **Historical Origin & Commit Trace**:
  - Commit `5694bca5` (Sprint 2026-43.1, `ATT-4293`): Introduced statistical tail folding with 50 W multiple rounding and asymmetric header chip buttons.
  - Commit `5c5367c3` (Sprint 2026-43.1, `ATT-4294`): Expanded power histogram to support Coggan 7 zones (`Zone 6` brown, `Zone 7` black), surfacing the fact that 50 W rounding severed Zones 5 and 6 arbitrarily.

* **Root Reason for Existing Formulation**:
  - *Why was 50 W rounding built?* During ATT-4293, 50 W rounding was chosen as a quick numerical heuristic to yield round numbers on chart labels (e.g. 350 W, 400 W). At that time, power zones were clamped to 5 zones, so the nuanced interaction with Zones 5, 6, and 7 was not yet fully modeled.
  - *Why was `primaryContainer` put on the clickable chip?* The developer applied `primaryContainer` to make the clickable switch button conspicuous, overlooking that in a 2-segment view context, container-filled styling denotes the *selected* segment rather than a clickable trigger.

* **Preservation of Core Invariants**:
  - Snapping to upper power zone boundaries directly reinforces the core intent of `REQ-UI-419`: keeping the athlete's primary training zones intact in the Main View while isolating rare sprint efforts. By snapping to zone boundaries, the Main View contains complete, unclipped physiological training zones (e.g., all of Zone 1–4, Zone 1–5, or Zone 1–6).
  - Standard linear rides (where `dataMax < tailCutoff * 1.25` or tail $< 100\text{ W}$) retain linear mode (`tailCutoff = null`, `sprintTail = null`).
  - Heart Rate histograms remain 100% linear.
  - Total active duration and percentage conservation ($100.0\% \pm 0.1\%$) are strictly maintained.
  - Symmetrical 2-segment chip styling removes cognitive ambiguity while maintaining full accessibility and clickable touch targets.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Zone Boundary Cutoff Snapping Algorithm
In `TelemetryHistogramCalculator.calculateTailCutoff`:

```kotlin
fun calculateTailCutoff(
    bins: List<TelemetryHistogramBin>,
    totalDuration: Long,
    thresholds: PowerZoneThresholds,
    binWidth: Int,
    dataMax: Int
): Int? {
    if (bins.isEmpty() || totalDuration <= 0L) return null

    val targetDuration = TAIL_PERCENTILE_RATIO * totalDuration.toDouble()
    var accumulatedDuration = 0L
    var p99Candidate = bins.first().rangeMin

    for (bin in bins) {
        accumulatedDuration += bin.durationSec
        if (accumulatedDuration.toDouble() >= targetDuration) {
            p99Candidate = bin.rangeMax
            break
        }
    }

    // Floor at athlete's Zone 4 upper boundary (thresholds.z4Max)
    val candidate = maxOf(p99Candidate, thresholds.z4Max)

    // Snap upward to enclosing upper Power Zone boundary, falling back to 50 W multiple if > z6Max
    val rawCutoff = when {
        candidate <= thresholds.z4Max -> thresholds.z4Max
        candidate <= thresholds.z5Max -> thresholds.z5Max
        candidate <= thresholds.z6Max -> thresholds.z6Max
        else -> ((candidate + CUTOFF_ROUNDING_W - 1) / CUTOFF_ROUNDING_W) * CUTOFF_ROUNDING_W
    }

    // Align to histogram bin width (e.g. 10 W)
    val tailCutoff = if (binWidth > 1) {
        ((rawCutoff + binWidth - 1) / binWidth) * binWidth
    } else {
        rawCutoff
    }

    // Gating Condition 1: At least MIN_TAIL_BINS (10 bins = 100 W) in the tail
    val tailWidth = dataMax - tailCutoff
    if (tailWidth < MIN_TAIL_BINS * binWidth) {
        return null
    }

    // Gating Condition 2: dataMax >= tailCutoff * 1.25
    if (dataMax.toFloat() < tailCutoff * MIN_TAIL_RATIO) {
        return null
    }

    // Gating Condition 3: Active duration in tail is strictly > 0
    val tailDuration = bins.filter { it.rangeMin >= tailCutoff }.sumOf { it.durationSec }
    if (tailDuration <= 0L) {
        return null
    }

    return tailCutoff
}
```

### 5.2 Symmetrical 2-Segment Header Range Toggle
In `TelemetryHistogramChart.kt`:
Both views will render a 2-segment row alongside the resolution indicator (`Δ 10 W`).

```kotlin
Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    // Segment 1: Main Range (0–X W)
    if (!isSprintTailView) {
        // Active: Highlighted with primaryContainer, non-clickable
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
            Text(
                text = "${histogram.dataMin}–${histogram.tailCutoff} $unit",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    } else {
        // Inactive: Clickable switch target with back arrow
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.clickable {
                isSprintTailView = false
                selectedBinIndex = null
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(
                        R.string.power_histogram_return_desc,
                        histogram.tailCutoff,
                        unit
                    ),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.power_histogram_return_label,
                        histogram.dataMin,
                        histogram.tailCutoff,
                        unit
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Segment 2: Sprint Tail Range (> X W / X–Y W)
    if (!isSprintTailView) {
        // Inactive: Clickable switch target with forward arrow
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.clickable {
                isSprintTailView = true
                selectedBinIndex = null
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.power_histogram_overflow_label,
                        histogram.tailCutoff,
                        unit
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(
                        R.string.power_histogram_overflow_desc,
                        histogram.tailCutoff,
                        unit
                    ),
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        // Active: Highlighted with primaryContainer, non-clickable
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
            Text(
                text = "${histogram.tailCutoff}–${histogram.dataMax} $unit",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
```

### Visual State Matrix:

| View Mode | Segment 1 (`0–X W`) | Segment 2 (`> X W` / `X–Y W`) | User Perception |
| :--- | :--- | :--- | :--- |
| **Main View** | **Highlighted** (`primaryContainer`, Bold, No Arrow) | **Clickable** (`surfaceVariant`, Medium, `→` Arrow) | Unambiguously viewing 0–X W; can tap `> X W →` to view sprints |
| **Sprint Tail View** | **Clickable** (`surfaceVariant`, Medium, `←` Arrow) | **Highlighted** (`primaryContainer`, Bold, No Arrow) | Unambiguously viewing X–Y W; can tap `← 0–X W` to return |

---

## 6. System Invariants & Risk Assessment

### Core Invariants:
1. **Heart Rate Histogram Invariance**: Heart Rate histograms remain strictly 5-zone linear and never folded (`tailCutoff = null`, `sprintTail = null`).
2. **Mathematical Duration & Percentage Conservation**: Total active workout duration and percentage sums ($100.0\% \pm 0.1\%$) are mathematically conserved across Main View regular bins + overflow bin, and across Sprint Tail View return bin + sprint bins.
3. **Tail Folding Gating Integrity**: Gating conditions ($\ge 100\text{ W}$ in tail, $\ge 1.25\times$ ratio, active duration $> 0$) remain strictly enforced to prevent folding symmetric or non-skewed workouts.
4. **9-Language Translation Parity**: String resources (`power_histogram_overflow_label`, `power_histogram_return_label`, `power_histogram_overflow_desc`, `power_histogram_return_desc`) and format tokens (`%1$d`, `%2$s`) remain 100% valid and verified across all 9 locales.
5. **Human Decision Gate Authority**: Moving parent ticket `ATT-4370` to `Erledigt` remains an inviolable human-only gate.
6. **Zero Regression**: 100% unit test pass rate across `./gradlew testDebugUnitTest`.

### Risk Assessment:
* **Risk Rating**: **LOW**
* **Technical Justification**:
  - The changes are localized exclusively to two UI presentation and calculation files (`TelemetryHistogramChart.kt` and `TelemetryHistogramCalculator.kt`).
  - No database schemas, data migration, threading/concurrency models, or background service operations are modified.
  - The algorithm changes refine an existing mathematical function (`calculateTailCutoff`) with pure, deterministic branch snapping.
  - All existing unit tests and contracts will be verified with zero regressions.
