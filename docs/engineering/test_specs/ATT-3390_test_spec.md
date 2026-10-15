# Stage 2: Requirement & Test Specification - ATT-3390: Provide horizontal stacked distribution bar as alternative display mode in workout summary zone cards

**Ticket**: [ATT-3390](https://rainerblind.atlassian.net/browse/ATT-3390)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*[Epic] Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Formal System Requirement: REQ-UI-372

### 1.1 Requirement Title
**Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching in Workout Summary Cards**

### 1.2 Specification Clauses
The system SHALL provide a continuous horizontal stacked distribution bar as an alternative display mode alongside the vertical column chart and fine-grained telemetry histogram in workout aftermath zone cards (`HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`) (ATT-3390, amending `REQ-UI-202` and `REQ-UI-203`, and complementing `REQ-UI-231`, `REQ-UI-251`, and `REQ-UI-370`):

1. **Continuous Horizontal Stacked Distribution Bar (`REQ-UI-372.1`)**:
   - `ZoneDistributionStackedBar.kt` SHALL expose a composable function `ZoneDistributionStackedBar(distribution: ZoneDistributionData, modifier: Modifier = Modifier, barHeight: Dp = 16.dp, showLegend: Boolean = true)` rendering active workout duration normalized to 100%.
   - Active zone entries ($d_i > 0$ and $p_i > 0.0$) SHALL be laid out horizontally in contiguous order (Zone 1 to Zone 5 for Heart Rate; Zone 1 to Zone 7 for Power).
   - Each active segment's width SHALL be strictly proportional to its percentage of total duration ($w_i = \max(0.001f, p_i)$).
   - Outer bar container SHALL be clipped with `RoundedCornerShape(6.dp)` and filled with `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`.
   - Active segments SHALL be separated by subtle hairline dividers (`1.dp` width, `color = MaterialTheme.colorScheme.surface`).
   - If total active time is 0 or all zone entries have 0 duration, the component SHALL render an empty neutral container track without throwing layout or weighting exceptions.
   - Zero-duration zones ($d_i = 0$) SHALL be gracefully omitted from the horizontal bar.

2. **Contrast & Theme Compliance (`REQ-UI-372.2`)**:
   - For `TTColor.Zone6` (`#4E342E`) and `TTColor.Zone7` (`#000000`), the bar segment and legend swatches SHALL draw an outline border `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)` to preserve clear visibility against dark and AMOLED theme surfaces (complying with WCAG 2.1 AA 1.4.11 Non-Text Contrast $\ge 3:1$).

3. **Compact Integrated Legend (`REQ-UI-372.3`)**:
   - Below the horizontal bar, a responsive legend SHALL render each zone:
     - Circular color badge (`7.dp`) with matching zone fill and accessibility border for dark colors.
     - Zone badge text (`Z1`..`Z5` or `Z1`..`Z7`) in `labelSmall` bold.
     - Localized zone name (from `entry.zoneLabelResId` or fallback).
     - Formatted duration (`ZoneDistributionChartMath.formatZoneDuration(entry.durationSec)`).
     - Percentage readout (`String.format(Locale.getDefault(), "%.0f%%", entry.percentage)`).
   - Inactive zones ($d_i = 0$) SHALL be displayed in the legend with `0:00 (0%)` in `onSurfaceVariant` to maintain full metabolic zone context.

4. **Expanded Display Modes & Enum Parity (`REQ-UI-372.4`)**:
   - `enum class ZoneCardDisplayMode` in `ZoneDistributionModels.kt` SHALL define:
     - `STACKED_BAR`: Continuous horizontal stacked bar with compact legend.
     - `COLUMNS`: Discrete vertical column histogram (5 columns for HR, up to 7 columns for Power).
     - `HISTOGRAM`: Fine-grained telemetry frequency histogram (2 bpm / 10 W bins).
     - `FIVE_ZONES`: Backward-compatible legacy alias for `COLUMNS` to satisfy existing contract tests.

5. **Card Mode Switching & Segmented Buttons (`REQ-UI-372.5`)**:
   - In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`:
     - When `distribution.histogram != null`: The segmented button row SHALL render 3 buttons: `STACKED_BAR` (R.string.zone_mode_stacked_bar), `COLUMNS` (R.string.zone_mode_columns), and `HISTOGRAM` (R.string.zone_mode_histogram).
     - When `distribution.histogram == null`: The segmented button row SHALL render 2 buttons: `STACKED_BAR` and `COLUMNS`, enabling athletes to switch between the stacked bar and vertical columns even on activities without raw sample histograms.
     - When `effectiveDisplayMode` is `STACKED_BAR`, the card body renders `ZoneDistributionStackedBar`.
     - When `effectiveDisplayMode` is `COLUMNS` or `FIVE_ZONES`, the card body renders `ZoneDistributionColumnChart`.
     - When `effectiveDisplayMode` is `HISTOGRAM`, the card body renders `TelemetryHistogramChart`.

6. **Preference Persistence (`REQ-UI-372.6`)**:
   - `WorkoutCardSectionPreferences` in `MyPreferenceManager.kt` SHALL include `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS`.
   - Athlete's selected mode SHALL be persisted across application restarts via DataStore key `workout_card_zone_display_mode`.

7. **9-Language Localization Parity (`REQ-UI-372.7`)**:
   - Add `zone_mode_stacked_bar` and `zone_mode_columns` across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution*, Sprint 2026-40.5, ATT-1390) and `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution*, Sprint 2026-40.5, ATT-1390).
   - Complements `REQ-UI-231` (*Fine-Grained Telemetry Histograms*, ATT-1845), `REQ-UI-251` (*Marginal Zone Strips in TelemetryMetricGraph*, ATT-2147), and `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*, ATT-3184).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced vertical columns and `ZoneTimeEntry`.
   - Sprint 2026-40.8 (`ATT-1845`, commit `7b14d9a2`) introduced `ZoneCardDisplayMode` with `FIVE_ZONES` and `HISTOGRAM`.
   - Sprint 2026-41.2 (`ATT-2147`, commit `c8192a0e`) introduced `TelemetryMetricGraphMarginalBarsContractTest.kt` verifying `ZoneCardDisplayMode.FIVE_ZONES`.
3. **Root Reason for Existing Formulation**:
   - At the time of ATT-1845, only vertical columns and fine-grained histograms existed, and all zone models were strictly 5 zones. The introduction of 7-zone power in ATT-3184 made `FIVE_ZONES` semantically imprecise, while athletes requested a continuous horizontal stacked representation.
4. **Preservation of Core Invariants**:
   - `ZoneCardDisplayMode.FIVE_ZONES` is strictly retained for full backward compatibility with `TelemetryMetricGraphMarginalBarsContractTest.kt` and `TelemetryHistogramCardTest.kt`.
   - Telemetry metric graph marginal strip rendering (`REQ-UI-251`) remains 100% operational when `zoneDisplayMode == FIVE_ZONES || zoneDisplayMode == COLUMNS || zoneDisplayMode == STACKED_BAR`.
   - Zero SQLite schema changes or database migrations.

---

## 3. Acceptance Criteria (Given-When-Then)

### Criterion 1: Mode Switching to Stacked Bar
- **Given** a workout summary card with Heart Rate or Power telemetry,
- **When** the athlete clicks the "Stacked Bar" ("Balken") button in the segmented button row,
- **Then** the card body switches to `ZoneDistributionStackedBar`, rendering a continuous horizontal bar with matching colors and legend.

### Criterion 2: Proportional Widths & Zero-Duration Handling
- **Given** a workout distribution with active zones (e.g. Z1: 10%, Z2: 50%, Z3: 40%, Z4: 0%, Z5: 0%),
- **When** rendered inside `ZoneDistributionStackedBar`,
- **Then** Z1 occupies 10% of width, Z2 occupies 50%, Z3 occupies 40%, and Z4/Z5 are omitted from the bar without throwing weighting exceptions.

### Criterion 3: Dual Metric Parity (HR 5-Zone and Power 7-Zone)
- **Given** a workout with both HR (5 zones) and Cycling Power (7 zones),
- **When** viewing both cards in `STACKED_BAR` mode,
- **Then** the HR card renders a 5-zone stacked bar and the Power card renders a 7-zone stacked bar using `TTColor.Zone1..Zone7`.

### Criterion 4: Accessibility Contrast on Dark Surfaces
- **Given** the application in dark or AMOLED theme with active Zone 6 or Zone 7 power segments,
- **When** `ZoneDistributionStackedBar` is rendered,
- **Then** Zone 6 and Zone 7 bar segments and legend swatches render a high-contrast outline border.

### Criterion 5: Histogram-Free Workouts Support
- **Given** an activity with aggregate zone entries but no raw sample histogram (`distribution.histogram == null`),
- **When** the card is displayed,
- **Then** a 2-button segmented row (`STACKED_BAR` and `COLUMNS`) is shown, allowing the athlete to toggle between stacked bar and columns.

---

## 4. Test Specification: TST-UI-335

### 4.1 Test Cases & Verifications
1. **`ZoneDistributionStackedBarTest.kt`**:
   - `testStackedBar_proportionalWeightsAndActiveSegments`: Verify active entries generate proportional weights and zero-duration entries are omitted.
   - `testStackedBar_emptyOrZeroDurationFallback`: Verify empty or all-zero distributions render cleanly without throwing exceptions.
   - `testStackedBar_fiveZoneHeartRateData`: Verify full 5-zone HR dataset layout.
   - `testStackedBar_sevenZonePowerData`: Verify full 7-zone Power dataset layout including `Zone6` and `Zone7`.
   - `testStackedBar_accessibilityBordersForDarkZones`: Verify `Zone6` and `Zone7` swatches receive border outlines.
2. **`ZoneCardDisplayModeTest.kt`**:
   - `testZoneCardDisplayMode_values`: Verify `ZoneCardDisplayMode.values()` contains `STACKED_BAR`, `COLUMNS`, `HISTOGRAM`, and `FIVE_ZONES`.
   - `testZoneCardDisplayMode_legacyCompatibility`: Verify `FIVE_ZONES` is compatible with `COLUMNS`.
3. **`WorkoutCardSectionPreferencesTest.kt`**:
   - `testZoneCardDisplayMode_defaultAndCopy`: Verify `WorkoutCardSectionPreferences.zoneCardDisplayMode` defaults to `COLUMNS` and copies cleanly.
4. **`TelemetryMetricGraphMarginalBarsContractTest.kt`**:
   - Verify all contract assertions pass without regressions.
5. **Localization Parity Audit**:
   - Verify `zone_mode_stacked_bar` and `zone_mode_columns` exist and are non-empty across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
6. **Clean-Room Full-Suite Regression**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

---

## 5. Traceability Matrix

| Requirement Clause | Specification Item | Verification Test Case | Status |
|---|---|---|---|
| `REQ-UI-372.1` | Stacked bar proportional widths & zero-duration handling | `ZoneDistributionStackedBarTest.kt` | Ready |
| `REQ-UI-372.2` | WCAG 2.1 AA dark swatch outline border | `ZoneDistributionStackedBarTest.kt` | Ready |
| `REQ-UI-372.3` | Compact integrated legend & duration formatting | `ZoneDistributionStackedBarTest.kt` | Ready |
| `REQ-UI-372.4` | `ZoneCardDisplayMode` enum expansion & `FIVE_ZONES` alias | `ZoneCardDisplayModeTest.kt`, `TelemetryHistogramCardTest.kt` | Ready |
| `REQ-UI-372.5` | 3-way and 2-way segmented button switching | `HeartRateZoneDistributionCardTest.kt`, `PowerZoneDistributionCardTest.kt` | Ready |
| `REQ-UI-372.6` | Preference persistence in `WorkoutCardSectionPreferences` | `WorkoutCardSectionPreferencesTest.kt` | Ready |
| `REQ-UI-372.7` | 9-language localization parity | `ZoneDistributionStackedBarLocalizationTest.kt` | Ready |
