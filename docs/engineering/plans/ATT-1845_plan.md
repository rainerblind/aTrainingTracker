# Stage 3: Implementation Plan - ATT-1845: Fine-Grained Telemetry Frequency Histogram (BPM & Wattage Bins) with Zone Color Shading

**Ticket**: [ATT-1845](https://rainerblind.atlassian.net/browse/ATT-1845)  
**Sub-task**: [ATT-1950](https://rainerblind.atlassian.net/browse/ATT-1950) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-231`  
**Test Mapping**: `TST-UI-185`  
**Branch**: `feature/ATT-1845`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Architecture Overview (SWE.2)

This plan implements a fine-grained telemetry frequency histogram for Heart Rate ($\Delta = 2\text{ bpm}$) and Cycling Power ($\Delta = 10\text{ W}$) in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, with per-bar zone color shading, interactive scrubbing readout, and header toggle.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│             HeartRateZoneDistributionCard / PowerZoneDistributionCard            │
│                                                                                  │
│   Header Row:                                                                    │
│   [Icon] [Title: Heart Rate Zones]  [Toggle: 5 Zones | Histogram]   [Total Time] │
│                                                                                  │
│   ┌──────────────────────────────────────────────────────────────────────────┐   │
│   │ If Mode == FIVE_ZONES:                                                   │   │
│   │   ZoneDistributionColumnChart.kt (Classic 5-zone aggregate columns)      │   │
│   ├──────────────────────────────────────────────────────────────────────────┤   │
│   │ If Mode == HISTOGRAM:                                                    │   │
│   │   TelemetryHistogramChart.kt (Fine-grained continuous bins)              │   │
│   │                                                                          │   │
│   │   [Readout: 154–156 bpm • 04:30 (11.2%) • Z3 (Aerobic)]                 │   │
│   │   ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐              │   │
│   │   │   │   │   │ Z2│ Z3│ Z3│ Z3│ Z4│ Z4│   │   │   │   │   │ (1dp gap)    │   │
│   │   └───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘              │   │
│   │   130 bpm                                         186 bpm                │   │
│   └──────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────┘
                                          │
                        fed by pure computation engine
                                          ▼
                      ┌───────────────────────────────────────┐
                      │    TelemetryHistogramCalculator.kt    │
                      │  - calculateHeartRateHistogram(...)   │
                      │  - calculatePowerHistogram(...)       │
                      │  - binWidth: HR=2 bpm, PWR=10 W       │
                      │  - accumulates active dt (<= 5s)      │
                      │  - shades bars with TTColor.Zone1..5  │
                      └───────────────────────────────────────┘
                                          │
                              embedded asynchronously
                                          ▼
                      ┌───────────────────────────────────────┐
                      │        WorkoutRepository.kt           │
                      │  - getHeartRateZoneDistribution(...)  │
                      │  - getPowerZoneDistribution(...)      │
                      │  - Dispatchers.IO                     │
                      └───────────────────────────────────────┘
```

---

## 2. Invariants & Safety Guarantees

1. **100% Backward Compatibility of 5-Zone Mode**:
   - `ZoneDistributionData` defaults `histogram = null`.
   - Card default display mode is `ZoneCardDisplayMode.FIVE_ZONES`, ensuring zero disruption to existing users unless explicitly toggled.
2. **Zero SQLite Database Mutations**:
   - Telemetry histograms are derived purely in-memory from existing `samplesTable` records.
   - Zero migrations, zero schema changes, zero database writes.
3. **Strict Thread Confinement**:
   - Histogram calculation runs entirely off the main thread (`Dispatchers.IO`) in `WorkoutRepository`.
4. **9-Language Localization Parity**:
   - Toggle options `zone_mode_5_zones` and `zone_mode_histogram` defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
5. **Deterministic Scrubbing Bounds**:
   - Touch drag gestures are clamped within `0 until bins.size`.
   - Empty or degenerate inputs return safe fallbacks without throwing unhandled exceptions.

---

## 3. Atomic Step-by-Step Implementation Sequence

### Step 1: Extend Data Models (`ZoneDistributionModels.kt`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionModels.kt`
- **Details**:
  - Add `TelemetryHistogramBin(val binIndex: Int, val rangeMin: Int, val rangeMax: Int, val durationSec: Long, val percentage: Float, val zoneIndex: Int, val color: Color)`.
  - Add `TelemetryHistogramData(val binWidth: Int, val dataMin: Int, val dataMax: Int, val totalActiveTimeSec: Long, val bins: List<TelemetryHistogramBin>)`.
  - Add `enum class ZoneCardDisplayMode { FIVE_ZONES, HISTOGRAM }`.
  - Add `val histogram: TelemetryHistogramData? = null` to `ZoneDistributionData`.

### Step 2: Implement Calculation Engine (`TelemetryHistogramCalculator.kt`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculator.kt`
- **Details**:
  - Define `DEFAULT_HR_BIN_WIDTH = 2` bpm, `DEFAULT_POWER_BIN_WIDTH = 10` W.
  - Implement `calculateHeartRateHistogram(samples: List<ZoneSample>, thresholds: HeartRateZoneThresholds, binWidth: Int = DEFAULT_HR_BIN_WIDTH): TelemetryHistogramData?`.
  - Implement `calculatePowerHistogram(samples: List<ZoneSample>, thresholds: PowerZoneThresholds, binWidth: Int = DEFAULT_POWER_BIN_WIDTH): TelemetryHistogramData?`.
  - Calculate continuous bins across $[v_{min}, v_{max}]$ aligned to multiples of `binWidth`.
  - Accumulate sample duration $\Delta t = \min(t_{i+1} - t_i, 5\text{s})$, matching `ZoneDistributionCalculator`.
  - Map each bin midpoint $(rangeMin + rangeMax) / 2$ to active athlete threshold zone ($1..5$) and apply `TTColor.Zone1`..`TTColor.Zone5`.

### Step 3: Integrate with `ZoneDistributionCalculator.kt` & `WorkoutRepository.kt`
- **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCalculator.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
- **Details**:
  - In `ZoneDistributionCalculator.calculateHeartRateDistribution`, attach `histogram = TelemetryHistogramCalculator.calculateHeartRateHistogram(samples, thresholds)`.
  - In `ZoneDistributionCalculator.calculatePowerDistribution`, attach `histogram = TelemetryHistogramCalculator.calculatePowerHistogram(samples, thresholds)`.

### Step 4: Author Pure Math Engine Unit Tests (`TelemetryHistogramCalculatorTest.kt`)
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCalculatorTest.kt`
- **Details**:
  - Test 2 bpm binning for Heart Rate with duration accumulation and percentage calculation.
  - Test 10 W binning for Cycling Power.
  - Test duration clamping ($\Delta t \le 5\text{s}$).
  - Test degenerate inputs (empty samples, single sample, zero-duration workout).
  - Test zone midpoint color assignment matching athlete zone thresholds.

### Step 5: Implement `TelemetryHistogramChart.kt` Composable
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramChart.kt`
- **Details**:
  - Composable parameters: `histogram: TelemetryHistogramData`, `unit: String`, `modifier: Modifier = Modifier`.
  - Scrubbing state: `var selectedBinIndex by remember { mutableStateOf<Int?>(null) }`.
  - Header readout bar: displays active bin details or default summary range.
  - Bar chart rendering: discrete vertical bars with 1 dp spacing, proportional height scaling, touch/drag gesture handling via `pointerInput`.
  - Subtly highlight selected bar with white / surface border or elevation.

### Step 6: Update `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`
- **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt`
- **Details**:
  - Add `var displayMode by rememberSaveable { mutableStateOf(ZoneCardDisplayMode.FIVE_ZONES) }`.
  - In header row, render compact segmented buttons or toggle icons for switching modes.
  - Conditionally render `ZoneDistributionColumnChart` or `TelemetryHistogramChart`.

### Step 7: Add 9-Language String Resources
- **Files**:
  - `app/src/main/res/values/strings.xml` (EN)
  - `app/src/main/res/values-de/strings.xml` (DE)
  - `app/src/main/res/values-es/strings.xml` (ES)
  - `app/src/main/res/values-fr/strings.xml` (FR)
  - `app/src/main/res/values-it/strings.xml` (IT)
  - `app/src/main/res/values-ja/strings.xml` (JA)
  - `app/src/main/res/values-nl/strings.xml` (NL)
  - `app/src/main/res/values-pl/strings.xml` (PL)
  - `app/src/main/res/values-pt/strings.xml` (PT)
- **Keys**: `zone_mode_5_zones`, `zone_mode_histogram`.

### Step 8: Author Composable Contract & Localization Tests
- **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramCardTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/TelemetryHistogramLocalizationTest.kt`
- **Details**:
  - Contract tests for toggle state switching, readout updates, and empty/null handling.
  - XML parsing test validating existence of new keys across all 9 locales.

### Step 9: Targeted Test Verification
- Run:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCalculatorTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCardTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramLocalizationTest"
  ```

### Step 10: Gate 3 Review Audit
- Write `scratch/ATT-1950_desc.md`.
- Update Jira description for `ATT-1950`.
- Transition `ATT-1950` to `in_review`.
- Run Gate 3 audit: `python3 tools/review_agent.py audit ATT-1950` (`BypassSandbox: true`).
