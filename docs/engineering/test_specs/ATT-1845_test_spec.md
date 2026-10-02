# Stage 2: Requirement & Test Specification - ATT-1845: Fine-Grained Telemetry Frequency Histogram (BPM & Wattage Bins) with Zone Color Shading

**Ticket**: [ATT-1845](https://rainerblind.atlassian.net/browse/ATT-1845)  
**Sub-task**: [ATT-1949](https://rainerblind.atlassian.net/browse/ATT-1949) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Formal Requirement Specification: `REQ-UI-231`

### 1.1 Requirement Statement
The system SHALL provide a fine-grained telemetry frequency histogram for Heart Rate and Cycling Power in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, allowing athletes to inspect detailed distribution density across narrow metric bins shaded with their active training zone colors (ATT-1845):

1. **Histogram Binning Engine & Data Models (`TelemetryHistogramModels.kt`, `ZoneDistributionModels.kt`, `ZoneDistributionCalculator.kt`)**:
   - The system SHALL define `data class TelemetryHistogramBin(val binIndex: Int, val rangeMin: Int, val rangeMax: Int, val durationSec: Long, val percentage: Float, val zoneIndex: Int, val color: Color)`.
   - The system SHALL define `data class TelemetryHistogramData(val binWidth: Int, val dataMin: Int, val dataMax: Int, val totalActiveTimeSec: Long, val bins: List<TelemetryHistogramBin>)`.
   - `ZoneDistributionData` SHALL include `val histogram: TelemetryHistogramData? = null` with full backward compatibility.
   - The calculation engine SHALL compute continuous bins across $[v_{min}, v_{max}]$ using curated default bin widths ($\Delta = 2\text{ bpm}$ for Heart Rate, $\Delta = 10\text{ W}$ for Power).
   - Each bin duration SHALL accumulate active sample durations ($\Delta t = \min(t_{i+1} - t_i, 5\text{s})$), matching total active workout time.
   - Every histogram bar SHALL be shaded using `TTColor.Zone1`..`TTColor.Zone5` corresponding to the bin midpoint evaluated against active athlete thresholds (`HeartRateZoneThresholds` / `PowerZoneThresholds`).
2. **View Mode Switching in Zone Cards (`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`)**:
   - In the card header row, adjacent to the localized title, the card SHALL render a compact toggle control allowing athletes to switch between 5-Zone summary mode and Detailed Histogram mode.
   - The toggle options SHALL be localized (`zone_mode_5_zones` and `zone_mode_histogram`).
   - Mode state SHALL be maintained locally via `rememberSaveable { mutableStateOf(ZoneCardDisplayMode.FIVE_ZONES) }`.
3. **Histogram Composable & Interactive Scrubbing (`TelemetryHistogramChart.kt`)**:
   - The system SHALL implement `TelemetryHistogramChart` rendering discrete vertical bars with 1 dp spacing.
   - Each bar height SHALL scale proportionally to the maximum bin duration in the workout.
   - Touching, tapping, or dragging across bars SHALL highlight the selected bin and update a compact readout label: `${rangeMin}–${rangeMax} bpm • ${formattedDuration} (${percentage}%) • Z${zone}`.
   - Releasing touch or tapping outside SHALL reset the readout to default summary state.
4. **9-Language Localization Parity**:
   - All toggle tokens (`zone_mode_5_zones`, `zone_mode_histogram`) SHALL be localized across English, German, Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.
5. **Preservation of Core Invariants**:
   - The default 5-zone column view remains 100% backward compatible and unchanged.
   - Single-thread SQLite confinement and `samplesTable` remain untouched.
   - Zero database migrations or mutations.
   - Binning runs asynchronously off the main thread (`Dispatchers.IO`) in `WorkoutRepository`.

---

### 1.2 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-231`), extending `REQ-UI-202` and `REQ-UI-203` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. **Historical Origin & Commit Trace**: Sprint `2026-40.2` (`ATT-1389`, `ATT-1390`).
3. **Root Reason for Existing Formulation**: Existing cards rendered only 5 coarse aggregate blocks (Z1–Z5), hiding distribution density curves (polarized, sweet-spot, pyramidal) and intra-zone boundary shifts.
4. **Preservation of Core Invariants**: Full backward compatibility with default 5-zone mode, theme contrast parity, asynchronous execution, and zero schema mutations are 100% preserved.

---

### 1.3 Given-When-Then Acceptance Criteria

- **AC-1 (Card Header View Mode Toggle)**:
  - *Given* an athlete viewing Heart Rate Zones or Power Zones in Aftermath or Workout Summary,
  - *When* the card renders,
  - *Then* a view mode toggle (`5 Zones` / `Histogram`) SHALL be available in the card header.

- **AC-2 (Fine-Grained Binned Histogram Rendering & Zone Shading)**:
  - *Given* an athlete selecting `Histogram`,
  - *When* inspecting the chart,
  - *Then* fine-grained vertical bars (2 bpm bins for HR, 10 W bins for Power) SHALL be displayed across the active workout span, with each bar shaded in its matching training zone color (`TTColor.Zone1`..`Zone5`).

- **AC-3 (Interactive Touch Scrubbing & Readout)**:
  - *Given* an athlete touching or scrubbing any histogram bar,
  - *When* a bar is highlighted,
  - *Then* the readout line SHALL display the bin range, duration, percentage, and zone name (e.g. `152–154 bpm • 04:12 (12.4%) • Z3`).

- **AC-4 (Toggle Return to 5-Zone Mode)**:
  - *Given* an athlete switching back to `5 Zones`,
  - *When* the toggle is tapped,
  - *Then* the classic 5-column chart SHALL be rendered immediately.

- **AC-5 (9-Language Parity)**:
  - *Given* the app running in any of the 9 supported locales,
  - *When* viewing the toggle controls,
  - *Then* valid localized strings SHALL be rendered without fallbacks or missing entries.

---

## 2. Test Specification: `TST-UI-185`

### 2.1 Scope of Automated Verification
The test suite validates:
1. Pure histogram binning calculation, range clamping, duration accumulation, and zone color mapping in `TelemetryHistogramCalculatorTest.kt`.
2. Composable view mode toggle switching, interactive touch scrubbing, and empty-state fallbacks in `TelemetryHistogramCardTest.kt`.
3. Complete 9-language localization parity in `TelemetryHistogramLocalizationTest.kt`.
4. Clean-room regression across all project modules (`./gradlew testDebugUnitTest`).

---

### 2.2 Detailed Test Cases

#### `TST-UI-185.1`: Pure Histogram Binning Engine Unit Tests
- **Test Class**: `com.atrainingtracker.trainingtracker.ui.aftermath.TelemetryHistogramCalculatorTest`
- **Case 1.1 (`testHeartRateHistogram_binsCorrectlyAt2Bpm`)**:
  Given sample HR stream with known intervals (e.g., 140 bpm for 10s, 142 bpm for 20s, 160 bpm for 30s) and active thresholds:
  Verify continuous bins with `binWidth = 2`, exact durations, correct percentages summing to 100%, and matching zone indices ($1..5$).
- **Case 1.2 (`testPowerHistogram_binsCorrectlyAt10W`)**:
  Given sample Power stream with known intervals (e.g., 180W for 15s, 220W for 45s) and active thresholds:
  Verify continuous bins with `binWidth = 10`, exact durations, and zone assignments.
- **Case 1.3 (`testDurationAccumulation_capsIntervalAt5Seconds`)**:
  Given sample stream with a gap $> 5\text{s}$ between samples:
  Verify sample duration is capped at 5s, identical to `ZoneDistributionCalculator`.
- **Case 1.4 (`testDegenerateInputs_emptyOrSingleSample`)**:
  Verify empty sample list returns empty histogram data (`totalActiveTimeSec = 0`, `bins = emptyList()`), and single sample produces a single valid bin without throwing exceptions.
- **Case 1.5 (`testBinZoneColorAssignment_evaluatesBinMidpoint`)**:
  Verify bar color strictly matches the zone evaluated at $(rangeMin + rangeMax) / 2$.

#### `TST-UI-185.2`: Composable Card Toggle & Histogram UI Contract Tests
- **Test Class**: `com.atrainingtracker.trainingtracker.ui.aftermath.TelemetryHistogramCardTest`
- **Case 2.1 (`testHeartRateCard_defaultsToFiveZonesAndTogglesToHistogram`)**:
  Verify `HeartRateZoneDistributionCard` renders with default `FIVE_ZONES` mode and transitions to `HISTOGRAM` upon toggle interaction.
- **Case 2.2 (`testPowerCard_defaultsToFiveZonesAndTogglesToHistogram`)**:
  Verify `PowerZoneDistributionCard` renders with default `FIVE_ZONES` mode and transitions to `HISTOGRAM` upon toggle interaction.
- **Case 2.3 (`testHistogramChart_interactiveScrubbingUpdatesReadout`)**:
  Verify scrubbing gesture across `TelemetryHistogramChart` updates the selected bin state and renders formatted readout.
- **Case 2.4 (`testHistogramChart_nullOrEmptyDataFallback`)**:
  Verify when `histogram == null` or `bins.isEmpty()`, appropriate placeholder or fallback rendering is presented without crashes.

#### `TST-UI-185.3`: 9-Language Localization Parity Tests
- **Test Class**: `com.atrainingtracker.trainingtracker.ui.aftermath.TelemetryHistogramLocalizationTest`
- **Case 3.1 (`testLocalizationParity_zoneModeStringsExistAcrossAllLocales`)**:
  Verify strings `zone_mode_5_zones` and `zone_mode_histogram` exist in:
  - `app/src/main/res/values/strings.xml` (EN)
  - `app/src/main/res/values-de/strings.xml` (DE)
  - `app/src/main/res/values-es/strings.xml` (ES)
  - `app/src/main/res/values-fr/strings.xml` (FR)
  - `app/src/main/res/values-it/strings.xml` (IT)
  - `app/src/main/res/values-ja/strings.xml` (JA)
  - `app/src/main/res/values-nl/strings.xml` (NL)
  - `app/src/main/res/values-pl/strings.xml` (PL)
  - `app/src/main/res/values-pt/strings.xml` (PT)

#### `TST-UI-185.4`: Clean-Room Regression Suite
- **Command**: `./gradlew testDebugUnitTest`
- **Pass Criteria**: 100% test pass rate across all modules with zero regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Target Artifact | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-231.1` (Histogram Binning Engine & Data Models) | `TST-UI-185.1` | `TelemetryHistogramCalculatorTest.kt` | JVM Unit Test |
| `REQ-UI-231.2` (Card View Mode Toggle) | `TST-UI-185.2` | `TelemetryHistogramCardTest.kt` | Robolectric / Composable Test |
| `REQ-UI-231.3` (Histogram Chart & Scrubbing) | `TST-UI-185.2` | `TelemetryHistogramCardTest.kt` | Robolectric / Composable Test |
| `REQ-UI-231.4` (9-Language Parity) | `TST-UI-185.3` | `TelemetryHistogramLocalizationTest.kt` | Localization Parity Test |
| `REQ-UI-231.5` (Preservation of Core Invariants) | `TST-UI-185.1`, `TST-UI-185.4` | `TelemetryHistogramCalculatorTest.kt`, Full Test Suite | Unit & Regression Suite |
| `REQ-PRO-001` (Clean-Room Regression) | `TST-UI-185.4` | Full Test Suite | Clean-room Gradle run |
