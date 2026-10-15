# Stage 1: Problem Domain & Architectural Analysis - ATT-3390: Provide horizontal stacked distribution bar as alternative display mode in workout summary zone cards

**Ticket**: [ATT-3390](https://rainerblind.atlassian.net/browse/ATT-3390)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*[Epic] Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Problem Domain

In the workout summary and aftermath analytics views (`WorkoutSummary`, `TrackOnMapScreen`, `MapDetailLayout`), physiological training zones are displayed within `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`. Currently, these cards support two display modes defined in `ZoneCardDisplayMode`:
1. `FIVE_ZONES`: A vertical column chart (`ZoneDistributionColumnChart.kt`) rendering 5 discrete columns for Heart Rate (and up to 7 columns for Power per ATT-3184).
2. `HISTOGRAM`: A fine-grained telemetry frequency histogram (`TelemetryHistogramChart.kt`) binning raw sensor samples into 2 bpm or 10 W increments.

While vertical column charts are effective for comparing the relative durations between individual zones, endurance athletes (runners and cyclists) frequently request a continuous, horizontal stacked distribution bar (standardized across platforms like Strava, TrainingPeaks, and Garmin Connect). 

### Benefits of Horizontal Stacked Distribution Bar:
- **Instant Metabolic Effort Overview**: A single horizontal bar normalized to 100% of active workout time immediately communicates the proportion of aerobic endurance (Zone 1/2), tempo/threshold (Zone 3/4), and anaerobic/neuromuscular stress (Zone 5/6/7) without requiring the athlete to mentally sum vertical column heights.
- **Vertical Space Efficiency**: Consumes significantly less vertical space than vertical columns while delivering high visual impact in compact summary cards.
- **Dynamic 5-Zone and 7-Zone Support**: Scales seamlessly between 5-zone Heart Rate models and the newly introduced 7-zone Coggan Cycling Power model (`REQ-UI-370` / ATT-3184).
- **Graceful Zero-Time Handling**: Zones with 0 seconds duration are cleanly omitted from the bar without distorting adjacent proportions.

This ticket introduces `ZoneDistributionStackedBar.kt`, expands `ZoneCardDisplayMode` to include `STACKED_BAR` (alongside `COLUMNS` / `FIVE_ZONES` and `HISTOGRAM`), provides a 3-way segmented button switcher on `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`, integrates user display mode persistence via `WorkoutCardSectionPreferences`, and guarantees full theme contrast compliance for dark segments (`TTColor.Zone6`, `TTColor.Zone7`).

---

## 2. Forensic Gap Analysis & Root Cause Analysis

### 2.1 Gap Analysis Against Existing Architecture
1. **Missing Stacked Horizontal Bar Component**:
   - `ZoneDistributionColumnChart.kt` only provides vertical columns.
   - There is currently no component in `com.atrainingtracker.trainingtracker.ui.aftermath.zones` that renders a contiguous horizontal segmented bar with proportional widths based on `ZoneTimeEntry.percentage`.
2. **Restricted `ZoneCardDisplayMode` Enum**:
   - `enum class ZoneCardDisplayMode` in `ZoneDistributionModels.kt` only declares `FIVE_ZONES` and `HISTOGRAM`.
   - Lacks `STACKED_BAR`.
   - In addition, the naming `FIVE_ZONES` is historically outdated since ATT-3184 introduced 7-zone Cycling Power; a modernized identifier `COLUMNS` should be introduced while preserving `FIVE_ZONES` as a backward-compatible alias to avoid breaking existing contract tests.
3. **Card Mode Switcher Restricted to 2 Options**:
   - `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` implement `SingleChoiceSegmentedButtonRow` hardcoded with `count = 2` (`FIVE_ZONES` and `HISTOGRAM`), and this switcher is only displayed if `distribution.histogram != null`.
   - When a workout has no fine-grained histogram data, athletes cannot switch display formats at all, even though the stacked bar is fully computable from `distribution.entries`.
4. **Preference Persistence Gap**:
   - Currently, `hrZoneDisplayMode` and `powerZoneDisplayMode` are held in transient `rememberSaveable` state in `TrackOnMapScreen.kt` and `WorkoutSummary.kt`, resetting when navigating away from the activity or re-opening the application.
   - `WorkoutCardSectionPreferences` in `MyPreferenceManager.kt` persists section toggles and `lapDisplayMode`, but does not yet persist the preferred `zoneCardDisplayMode`.

---

## 3. Chesterton's Fence Archaeological Review (`REQ-PRO-022`)

### 3.1 Historical Precedent & Existing Commit Trace
1. **ATT-1739 (Sprint 2026-40.5, Commit `e3966ebf`)**:
   - Introduced `ZoneDistributionColumnChart.kt` with 5 vertical columns, `ZoneTimeEntry`, and `ZoneDistributionChartMath.formatZoneDuration`.
2. **ATT-1845 (Sprint 2026-40.8, Commit `7b14d9a2`)**:
   - Introduced fine-grained telemetry histograms (`TelemetryHistogramChart.kt`), `TelemetryHistogramData`, and `ZoneCardDisplayMode` (`FIVE_ZONES`, `HISTOGRAM`).
3. **ATT-2147 (Sprint 2026-41.2, Commit `c8192a0e`)**:
   - Introduced marginal zone strips in `TelemetryMetricGraph.kt` aligned with Y-axis boundaries (`REQ-UI-251`).
   - Established strict contract test `TelemetryMetricGraphMarginalBarsContractTest.kt` verifying exact source tokens:
     - `zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES`
     - `zoneDisplayMode == ZoneCardDisplayMode.FIVE_ZONES`
     - `var hrZoneDisplayMode by rememberSaveable { mutableStateOf(ZoneCardDisplayMode.FIVE_ZONES) }`
4. **ATT-3184 (Sprint 2026-42.4)**:
   - Expanded Cycling Power to Andy Coggan's 7-zone model (`REQ-UI-370`), adding `TTColor.Zone6` and `TTColor.Zone7`.
   - Updated `ZoneDistributionColumnChart.kt` to dynamically layout up to 7 columns.

### 3.2 Chesterton's Fence Findings & Invariance Mandates
- **Preservation of `ZoneCardDisplayMode.FIVE_ZONES`**: Renaming or deleting `FIVE_ZONES` would instantly break `TelemetryMetricGraphMarginalBarsContractTest.kt` and `TelemetryHistogramCardTest.kt`. Therefore, `FIVE_ZONES` must remain a valid enum entry (or backward-compatible alias for `COLUMNS`).
- **Telemetry Graph Marginal Strip Preservation**: `TelemetryMetricGraph.kt` draws horizontal marginal bars when `zoneDisplayMode == FIVE_ZONES || zoneDisplayMode == COLUMNS || zoneDisplayMode == STACKED_BAR`. The marginal strip on the graph's right Y-axis reflects threshold intervals; when the card is in `STACKED_BAR` mode, the telemetry graph continues to draw boundary-aligned marginal strips seamlessly.
- **Contract Signature Preservation**: In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, `displayMode: ZoneCardDisplayMode? = null` and `onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null` signatures must be preserved.

---

## 4. Scope Bounding (`ATT-1250`)

### 4.1 In-Scope Deliverables
1. **New Component `ZoneDistributionStackedBar.kt`**:
   - Responsive Jetpack Compose component rendering a contiguous horizontal bar.
   - Width of each segment proportional to active time percentage (`weight(entry.percentage)`).
   - Rounded container corners (`clip(RoundedCornerShape(6.dp))`).
   - Subtle 1.dp dividers between adjacent segments.
   - Outline border for dark swatches (`TTColor.Zone6`, `TTColor.Zone7`) for WCAG 2.1 AA contrast compliance on dark surfaces.
   - Compact legend below the bar rendering zone badges (`Z1..Z5` / `Z1..Z7`), localized zone labels, formatted durations (`m:ss` / `h:mm:ss`), and percentage readouts.
   - Graceful zero-duration handling (zero-duration zones omitted from the bar; if total duration is 0, renders a neutral placeholder bar).
2. **Model Expansion in `ZoneDistributionModels.kt`**:
   - Add `STACKED_BAR` and `COLUMNS` to `ZoneCardDisplayMode`, retaining `FIVE_ZONES`.
3. **Card Enhancements in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`**:
   - Support rendering `ZoneDistributionStackedBar` when mode is `STACKED_BAR`.
   - Support rendering `ZoneDistributionColumnChart` when mode is `COLUMNS` or `FIVE_ZONES`.
   - Support rendering `TelemetryHistogramChart` when mode is `HISTOGRAM`.
   - Update `SingleChoiceSegmentedButtonRow`:
     - If `distribution.histogram != null`: 3 segmented buttons (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`).
     - If `distribution.histogram == null`: 2 segmented buttons (`STACKED_BAR`, `COLUMNS`).
4. **Preference Persistence & Defaults in `MyPreferenceManager.kt`**:
   - Add `zoneCardDisplayMode` to `WorkoutCardSectionPreferences` with default `COLUMNS`.
   - Persist display mode across app launches and workout cards via DataStore.
5. **Localization Parity Across All 9 Supported Locales**:
   - Add localized strings for `zone_mode_stacked_bar` and `zone_mode_columns` across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### 4.2 Out-of-Scope
- No modifications to SQLite schema or raw workout database tables (all zone distributions are computed dynamically on-the-fly).
- No alterations to `calculateHeartRateDistribution()` or `calculatePowerDistribution()` math (they already produce accurate `ZoneDistributionData`).
- No changes to real-time cockpit tracking tiles.

---

## 5. SWE.1 / SWE.2 Architectural Specification

### 5.1 Component Hierarchy
```
WorkoutSummary / TrackOnMapScreen
  ├── HeartRateZoneDistributionCard
  │     ├── Header: Title ("Heart Rate Zones") + Total Duration
  │     ├── Body (when effectiveDisplayMode):
  │     │     ├── STACKED_BAR -> ZoneDistributionStackedBar(distribution)
  │     │     ├── COLUMNS / FIVE_ZONES -> ZoneDistributionColumnChart(distribution)
  │     │     └── HISTOGRAM -> TelemetryHistogramChart(histogram, unit = "bpm")
  │     └── Footer: SingleChoiceSegmentedButtonRow
  │           ├── SegmentedButton: STACKED_BAR ("Balken" / "Stacked Bar")
  │           ├── SegmentedButton: COLUMNS ("Säulen" / "Columns")
  │           └── [Optional] SegmentedButton: HISTOGRAM ("Histogramm" / "Histogram")
  └── PowerZoneDistributionCard
        ├── Header: Title ("Power Zones") + Total Duration
        ├── Body:
        │     ├── STACKED_BAR -> ZoneDistributionStackedBar(distribution) (5 or 7 zones)
        │     ├── COLUMNS / FIVE_ZONES -> ZoneDistributionColumnChart(distribution)
        │     └── HISTOGRAM -> TelemetryHistogramChart(histogram, unit = "W")
        └── Footer: SingleChoiceSegmentedButtonRow (Stacked Bar / Columns / Histogram)
```

### 5.2 Stacked Bar Mathematical Invariants
Let $T_{\text{total}}$ be the total active workout duration in seconds, and $E = \{e_1, e_2, \dots, e_N\}$ be the list of zone entries ($N=5$ for HR, $N=7$ for Power).
- Each active zone entry $e_i$ has duration $d_i \ge 0$ and percentage $p_i \in [0.0, 100.0]$.
- Segments with $d_i = 0$ or $p_i \le 0.0$ are omitted from layout weighting to prevent Compose `weight must be > 0` runtime exceptions:
  $$\text{activeEntries} = \{ e_i \in E \mid d_i > 0 \text{ and } p_i > 0.0 \}$$
- For each active entry, segment weight is normalized:
  $$w_i = \max(0.001f, p_i)$$
- Outer container applies `Modifier.clip(RoundedCornerShape(6.dp))`.
- Internal dividers between adjacent active segments use `Spacer(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface))`.
- If $\sum d_i = 0$ or active entries is empty, render a neutral placeholder bar with `surfaceVariant.copy(alpha = 0.35f)`.

### 5.3 Accessibility & WCAG 2.1 AA Compliance
- **Dark Swatch Border**: `TTColor.Zone6` (`#4E342E`) and `TTColor.Zone7` (`#000000`) in dark or AMOLED themes can blend into the card surface (`#121212`). For these segments and their corresponding legend badges, an outline border `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)` is drawn to maintain distinct visual separation.
- **Legend Contrast**: Legend text labels and duration readouts use `MaterialTheme.colorScheme.onSurface` and `onSurfaceVariant`, achieving $> 10:1$ contrast against the card background.

---

## 6. Verification & Test Strategy

1. **Unit Tests (`ZoneDistributionStackedBarTest.kt`)**:
   - Verify layout contract and calculation: proportional weights, zero-duration omission, neutral fallback on empty distribution.
   - Verify 5-zone HR dataset and 7-zone Power dataset rendering.
   - Verify dark swatch border outline application on `Zone6` and `Zone7`.
2. **Display Mode Enum Tests (`TelemetryHistogramCardTest.kt`, `ZoneCardDisplayModeTest.kt`)**:
   - Verify `ZoneCardDisplayMode` contains `STACKED_BAR`, `COLUMNS`, `HISTOGRAM`, and `FIVE_ZONES`.
   - Verify mode mapping and backward-compatibility with `FIVE_ZONES`.
3. **Contract Regression Tests (`TelemetryMetricGraphMarginalBarsContractTest.kt`)**:
   - Verify that all existing contract tests pass with 100% assertions satisfied.
4. **Preference Persistence Tests (`WorkoutCardSectionPreferencesTest.kt`)**:
   - Verify `zoneCardDisplayMode` default state and copy behavior.
5. **Localization Parity Test**:
   - Verify `zone_mode_stacked_bar` and `zone_mode_columns` exist and are non-empty across all 9 application locales.
6. **Clean-Room Regression**:
   - Execute `./gradlew testDebugUnitTest` ensuring 0 regressions across the entire test suite.

---

## 7. Localization Matrix (9 Supported Locales)

| Locale | `zone_mode_stacked_bar` | `zone_mode_columns` |
|---|---|---|
| `values/` (EN) | Stacked Bar | Columns |
| `values-de/` (DE) | Balken | Säulen |
| `values-es/` (ES) | Barra apilada | Columnas |
| `values-fr/` (FR) | Barre empilée | Colonnes |
| `values-it/` (IT) | Barra sovrapposta | Colonne |
| `values-ja/` (JA) | スタックバー | カラム |
| `values-nl/` (NL) | Gestapelde balk | Kolommen |
| `values-pl/` (PL) | Pasek skumulowany | Kolumny |
| `values-pt/` (PT) | Barra empilhada | Colunas |
