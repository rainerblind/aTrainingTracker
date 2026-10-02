# Stage 1 Analysis: ATT-1959 - [Aftermath/Zones] Refine Zone Card Header Layout and Streamline Telemetry Histogram Readout

**Ticket**: [ATT-1959](https://rainerblind.atlassian.net/browse/ATT-1959)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Branch**: `feature/ATT-1959`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

During Sprint Review of ATT-1845 (`REQ-UI-231`), on-device inspection on Google Pixel 10 revealed two visual layout defects in the Heart Rate and Cycling Power zone cards (`HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`) and the frequency histogram chart (`TelemetryHistogramChart.kt`):

1. **Header Row Overcrowding & Vertical Text Wrapping**:
   - In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, the header row currently hosts four distinct UI elements on a single horizontal line:
     1. Leading icon (18 dp + 8 dp spacing)
     2. Localized card title (`R.string.aftermath_hr_zones_title` / `R.string.aftermath_power_zones_title`)
     3. Mode switcher toggle (`SingleChoiceSegmentedButtonRow`, ~140–160 dp width)
     4. Total active duration string (e.g. `"1:20:27"`)
   - On standard mobile viewports (360 dp to 412 dp width), this dense layout severely constrains the horizontal margin available for the total duration string. In German (where "Herzfrequenz-Zonen" is longer than English) or with system font scaling enabled, the total duration text is squeezed into a tiny width, forcing characters to wrap vertically into an illegible 1-character-wide column (e.g. `1 \n : \n 2 \n 0 \n : \n 2 \n 7`).

2. **Redundant & Non-Localized Histogram Readout Metric**:
   - In `TelemetryHistogramChart.kt`, the default un-scrubbed readout line displays three metrics:
     - Left: Data range (`"${histogram.dataMin}–${histogram.dataMax} $unit"`)
     - Center: Hardcoded label (`"${histogram.bins.count { it.durationSec > 0 }} active bins"`)
     - Right: Bin width delta (`"Δ ${histogram.binWidth} $unit"`)
   - The middle metric ("X active bins") is hardcoded in English (violating localization standards) and adds unnecessary cognitive clutter. Athletes need to see the active range and bin resolution at rest, and individual bin metrics when interactively touching or scrubbing a bar.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Defect 1: Header Row Horizontal Compounding
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt` (lines 59–115):
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(...)
    Spacer(8.dp)
    Text(text = stringResource(R.string.aftermath_hr_zones_title), ...)
    Spacer(modifier = Modifier.weight(1f))

    if (distribution.histogram != null) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.height(28.dp)) { ... }
        Spacer(modifier = Modifier.width(8.dp))
    }

    Text(text = ZoneDistributionChartMath.formatZoneDuration(distribution.totalActiveTimeSec), ...)
}
```
Because the `SingleChoiceSegmentedButtonRow` consumes significant fixed width, `Spacer(modifier = Modifier.weight(1f))` collapses to 0 dp on standard device widths, and the remaining space allocated to `Text(totalActiveTimeSec)` is narrower than the text's intrinsic measurement.

**Architectural Solution**:
- Decouple the view mode toggle from the header row.
- Keep the header row strictly dedicated to card identity and high-level summary:
  `[Icon] [Card Title] [Flexible Spacer] [Total Active Duration]`
  This guarantees that the title and total duration have 100% of the card width to breathe and will never collide or wrap vertically across any supported language or screen density.
- Place the mode switcher (`SingleChoiceSegmentedButtonRow`) directly below the chart, centered horizontally:
  `[ 5 Zonen | Histogramm ]`
  This aligns with standard Material 3 dashboard ergonomics where view mode toggles or range selectors sit directly beneath the visualization they control, while providing ample room for localized button labels without cramping the chart or header.

### Defect 2: Cluttered Default Readout in `TelemetryHistogramChart.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramChart.kt` (lines 125–129):
```kotlin
Text(
    text = "${histogram.bins.count { it.durationSec > 0 }} active bins",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
```
Removing this hardcoded middle label simplifies the default readout line to:
- Left: Metric span `${histogram.dataMin}–${histogram.dataMax} $unit`
- Right: Bin resolution `Δ ${histogram.binWidth} $unit`
Using `Arrangement.SpaceBetween`, the span is cleanly anchored to the left and the resolution to the right, eliminating text crowding and non-localized strings.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Refactor `HeartRateZoneDistributionCard.kt` header and body layout:
     - Header row contains only icon, title, spacer, and total active duration.
     - Mode toggle (`SingleChoiceSegmentedButtonRow`) is positioned below the chart, centered horizontally.
  2. Refactor `PowerZoneDistributionCard.kt` header and body layout identically.
  3. Streamline `TelemetryHistogramChart.kt`:
     - Remove the middle `"active bins"` label from the default readout header.
     - Preserve range and bin width delta in default state.
     - Preserve dynamic bin metrics (range, duration, percentage, zone) when scrubbing.
  4. Update and add unit/contract tests verifying the refined layout and readout behavior.

* **Explicitly Out-of-Scope (To Prevent Scope Creep)**:
  - Modifying `TelemetryHistogramCalculator.kt` binning math or data models.
  - Changing zone color palettes (`TTColor.Zone1`..`Zone5`).
  - Modifying `ZoneDistributionColumnChart.kt` 5-zone rendering.
  - Adding or removing string resource keys (existing `zone_mode_5_zones` and `zone_mode_histogram` are preserved).

---

## 4. Chesterton's Fence Requirement Archaeology (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-231` (*Aftermath/Zones: Fine-Grained Telemetry Frequency Histogram (BPM & Wattage Bins) with Zone Color Shading*), under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `bf624382`, ATT-1845).
3. *Root Reason for Existing Formulation*: In ATT-1845, the segmented button toggle was placed in the header row adjacent to the title to keep the card compact. However, testing on physical devices revealed that on standard viewports, placing the title, toggle, and total duration on a single line causes catastrophic text wrapping of the duration string in languages with longer words (e.g. German "Herzfrequenz-Zonen"). Placing the toggle below the chart provides ample room for both the header elements and the toggle labels.
4. *Preservation of Core Invariants*: Full backward compatibility with default 5-zone mode, 9-language localization parity, interactive histogram scrubbing, and zero SQLite schema mutations are 100% strictly preserved.

---

## 5. Proposed Architectural Design & Solution

1. **Card Header & Bottom Toggle Architecture**:
   In both `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`:
   ```kotlin
   Column(
       modifier = Modifier.fillMaxWidth().padding(12.dp),
       verticalArrangement = Arrangement.spacedBy(10.dp)
   ) {
       // Header Row: Icon + Title + Spacer + Total Duration
       Row(
           modifier = Modifier.fillMaxWidth(),
           verticalAlignment = Alignment.CenterVertically
       ) {
           Icon(...)
           Spacer(modifier = Modifier.width(8.dp))
           Text(text = stringResource(R.string.aftermath_hr_zones_title), ...)
           Spacer(modifier = Modifier.weight(1f))
           Text(text = ZoneDistributionChartMath.formatZoneDuration(distribution.totalActiveTimeSec), ...)
       }

       // Chart Visualization Body
       when (displayMode) {
           ZoneCardDisplayMode.FIVE_ZONES -> ZoneDistributionColumnChart(...)
           ZoneCardDisplayMode.HISTOGRAM -> TelemetryHistogramChart(...)
       }

       // Bottom Mode Toggle Row (below chart)
       if (distribution.histogram != null) {
           Row(
               modifier = Modifier.fillMaxWidth(),
               horizontalArrangement = Arrangement.Center
           ) {
               SingleChoiceSegmentedButtonRow(modifier = Modifier.height(28.dp)) {
                   SegmentedButton(...) { Text(stringResource(R.string.zone_mode_5_zones)) }
                   SegmentedButton(...) { Text(stringResource(R.string.zone_mode_histogram)) }
               }
           }
       }
   }
   ```

2. **Streamlined Readout in `TelemetryHistogramChart.kt`**:
   ```kotlin
   // Default unscrubbed state:
   Text(
       text = "${histogram.dataMin}–${histogram.dataMax} $unit",
       style = MaterialTheme.typography.labelSmall,
       color = MaterialTheme.colorScheme.onSurfaceVariant
   )
   Text(
       text = "Δ ${histogram.binWidth} $unit",
       style = MaterialTheme.typography.labelSmall,
       fontWeight = FontWeight.Bold,
       color = MaterialTheme.colorScheme.primary
   )
   ```

3. **Automated Verification Strategy**:
   - Update `TelemetryHistogramCardTest.kt` with layout and readout assertions.
   - Run targeted unit test suite:
     ```bash
     ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*"
     ```
   - Run full clean-room regression suite in Stage 5.

---

## 6. Deliverable Sign-Off Criteria (Gate 1 Checklist)
- [x] Forensic root cause analysis accurately identifies header row overcrowding and readout clutter.
- [x] Chesterton's Fence Requirement Archaeology completed with all 4 mandatory fields.
- [x] Out-of-scope boundaries clearly defined.
- [x] Proposed design completely prevents text wrapping on all screen widths and languages.
