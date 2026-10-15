# Stage 3: Implementation Plan - ATT-3390: Provide horizontal stacked distribution bar as alternative display mode in workout summary zone cards

**Ticket**: [ATT-3390](https://rainerblind.atlassian.net/browse/ATT-3390)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*[Epic] Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-372` (*Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching in Workout Summary Cards*)  
**Test Mapping**: `TST-UI-335` (*Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching Verification*)  
**Branch**: `feature/ATT-3390`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Architectural Design & SWE.2 Specification

### 1.1 Structural Decomposition & Modularity
- **Component Layer (`ui/aftermath/zones`)**:
  - `ZoneDistributionStackedBar.kt` (New Component): Standalone composable rendering a continuous horizontal stacked distribution bar representing 100% of active workout time with an integrated, responsive legend.
  - `ZoneDistributionModels.kt`: Expands `enum class ZoneCardDisplayMode` with `STACKED_BAR`, `COLUMNS`, `HISTOGRAM`, and legacy backward-compatible alias `FIVE_ZONES`.
  - `HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt`: Upgraded with 3-way/2-way segmented button switching and rendering `ZoneDistributionStackedBar` when `effectiveDisplayMode == STACKED_BAR`.
- **Telemetry Graph Layer (`ui/map`)**:
  - `TelemetryMetricGraph.kt`: Accepts `ZoneCardDisplayMode`, rendering boundary-aligned marginal strips when mode is `FIVE_ZONES`, `COLUMNS`, or `STACKED_BAR`.
- **Preference Persistence Layer (`MyPreferenceManager.kt`)**:
  - `WorkoutCardSectionPreferences`: Extends data class with `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS`, persisted in DataStore under key `workout_card_zone_display_mode`.
- **Design System & Localization**:
  - 9-language translation parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/` for `zone_mode_stacked_bar` and `zone_mode_columns`.

### 1.2 UI Consistency & Design Guidelines Compliance (Rule 23)
- **Reference Screen**: `ZoneDistributionColumnChart.kt` and `TelemetryHistogramChart.kt` inside `HeartRateZoneDistributionCard.kt`.
- **Reused Components & Tokens**:
  - Shapes: `RoundedCornerShape(6.dp)` for bar container and segment grouping; `CircleShape` for legend badge dots (`7.dp`).
  - Spacing: `spacedBy(8.dp)` horizontal and `spacedBy(4.dp)` vertical rhythm in legend wrapping.
  - Theme Tokens: `MaterialTheme.colorScheme.surfaceVariant` (alpha = 0.35f) for empty track; `MaterialTheme.colorScheme.outlineVariant` (alpha = 0.5f) for contour border; `MaterialTheme.colorScheme.onSurface` and `onSurfaceVariant` for typography.
  - Zone Colors: `TTColor.Zone1..Zone5` for Heart Rate, `TTColor.Zone1..Zone7` for Cycling Power.
  - Border Uniformity: `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` applied uniformly around outer bar container and all legend badge swatches, guaranteeing WCAG 2.1 AA Non-Text Contrast ($\ge 3:1$) across both light and dark themes.

---

## 2. Step-by-Step Implementation Roadmap (Bite-Sized Atomic Steps)

### Step 1: Expand `ZoneCardDisplayMode` in `ZoneDistributionModels.kt`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionModels.kt`
- **Implementation**:
  ```kotlin
  enum class ZoneCardDisplayMode {
      STACKED_BAR,
      COLUMNS,
      HISTOGRAM,
      @Deprecated("Legacy alias for COLUMNS", ReplaceWith("COLUMNS"))
      FIVE_ZONES
  }
  ```
- **Rationale**: Introduces `STACKED_BAR` and modern `COLUMNS` while retaining `FIVE_ZONES` as an enum constant to satisfy existing contract tests (`TelemetryMetricGraphMarginalBarsContractTest.kt`, `TelemetryHistogramCardTest.kt`).

### Step 2: Add 9-Language Localization Strings for Mode Switcher
- **Target Files**:
  - `app/src/main/res/values/strings.xml` (`zone_mode_stacked_bar`: "Stacked Bar", `zone_mode_columns`: "Columns")
  - `app/src/main/res/values-de/strings.xml` (`zone_mode_stacked_bar`: "Balken", `zone_mode_columns`: "Säulen")
  - `app/src/main/res/values-es/strings.xml` (`zone_mode_stacked_bar`: "Barra apilada", `zone_mode_columns`: "Columnas")
  - `app/src/main/res/values-fr/strings.xml` (`zone_mode_stacked_bar`: "Barre empilée", `zone_mode_columns`: "Colonnes")
  - `app/src/main/res/values-it/strings.xml` (`zone_mode_stacked_bar`: "Barra sovrapposta", `zone_mode_columns`: "Colonne")
  - `app/src/main/res/values-ja/strings.xml` (`zone_mode_stacked_bar`: "スタックバー", `zone_mode_columns`: "カラム")
  - `app/src/main/res/values-nl/strings.xml` (`zone_mode_stacked_bar`: "Gestapelde balk", `zone_mode_columns`: "Kolommen")
  - `app/src/main/res/values-pl/strings.xml` (`zone_mode_stacked_bar`: "Pasek skumulowany", `zone_mode_columns`: "Kolumny")
  - `app/src/main/res/values-pt/strings.xml` (`zone_mode_stacked_bar`: "Barra empilhada", `zone_mode_columns`: "Colunas")

### Step 3: Implement `ZoneDistributionStackedBar.kt`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBar.kt`
- **Implementation**:
  - Composable `ZoneDistributionStackedBar(distribution: ZoneDistributionData, modifier: Modifier = Modifier, barHeight: Dp = 16.dp, showLegend: Boolean = true)`.
  - Filter active entries: `val activeEntries = distribution.entries.filter { it.durationSec > 0 && it.percentage > 0f }`.
  - Outer bar: `Box(modifier = Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(6.dp)).border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)))`.
  - Active segment layout: In Compose `Row`, weight each active segment using `Modifier.weight(entry.percentage)`. Between adjacent active segments, insert a `Spacer(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface))`.
  - Responsive legend: `FlowRow` rendering all configured entries (`distribution.entries`). Active entries render with bold badges and full text color; entries with $d_i = 0$ render with `alpha = 0.45f` showing `0:00 (0%)`.
  - All circular badge swatches (`7.dp`) receive `border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), CircleShape)`.

### Step 4: Update `HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt`
- **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt`
- **Changes**:
  - Body `when (effectiveDisplayMode)`:
    - `ZoneCardDisplayMode.STACKED_BAR` -> `ZoneDistributionStackedBar(distribution = distribution, modifier = Modifier.fillMaxWidth())`
    - `ZoneCardDisplayMode.COLUMNS`, `ZoneCardDisplayMode.FIVE_ZONES` -> `ZoneDistributionColumnChart(distribution = distribution, modifier = Modifier.fillMaxWidth())`
    - `ZoneCardDisplayMode.HISTOGRAM` -> `TelemetryHistogramChart(...)`
  - Segmented button row:
    - If `distribution.histogram != null`: count = 3:
      - Index 0: `STACKED_BAR` (`R.string.zone_mode_stacked_bar`)
      - Index 1: `COLUMNS` (`R.string.zone_mode_columns`)
      - Index 2: `HISTOGRAM` (`R.string.zone_mode_histogram`)
    - If `distribution.histogram == null && distribution.entries.isNotEmpty()`: count = 2:
      - Index 0: `STACKED_BAR` (`R.string.zone_mode_stacked_bar`)
      - Index 1: `COLUMNS` (`R.string.zone_mode_columns`)

### Step 5: Update `MyPreferenceManager.kt` for Persistence
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt`
- **Changes**:
  - Add `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS` to `data class WorkoutCardSectionPreferences`.
  - Add `val WORKOUT_CARD_ZONE_DISPLAY_MODE = stringPreferencesKey("workout_card_zone_display_mode")`.
  - In `workoutCardPreferencesFlow`: deserialization with `COLUMNS` default and `FIVE_ZONES` fallback.
  - In `setWorkoutCardPreferences`: persist `preferences[WORKOUT_CARD_ZONE_DISPLAY_MODE] = prefs.zoneCardDisplayMode.name`.

### Step 6: Update `TelemetryMetricGraph.kt` Marginal Strip Evaluation
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
- **Change**:
  - Support `zoneDisplayMode == ZoneCardDisplayMode.FIVE_ZONES || zoneDisplayMode == ZoneCardDisplayMode.COLUMNS || zoneDisplayMode == ZoneCardDisplayMode.STACKED_BAR`.
  - Retains the literal string `zoneDisplayMode == ZoneCardDisplayMode.FIVE_ZONES` satisfying `TelemetryMetricGraphMarginalBarsContractTest.kt`.

### Step 7: Author Unit & Contract Tests
- **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBarTest.kt`:
    - Layout weighting without overflow.
    - Zero-duration omission and empty/zero fallback.
    - 5-zone HR and 7-zone Power support.
    - Uniform contour and swatch borders.
    - Dimmed zero-duration display in legend.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneCardDisplayModeTest.kt`:
    - Enum entries: `STACKED_BAR`, `COLUMNS`, `HISTOGRAM`, `FIVE_ZONES`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBarLocalizationTest.kt`:
    - 9-locale parity for `zone_mode_stacked_bar` and `zone_mode_columns`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutCardSectionPreferencesTest.kt`:
    - Verify `zoneCardDisplayMode` default and copy.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphMarginalBarsContractTest.kt`:
    - Run regression assertions.

### Step 8: Clean-Room Full Suite Verification
- Execute `./gradlew testDebugUnitTest`.

---

## 3. Invariants & Risk Mitigation

1. **Contract Invariant**: `ZoneCardDisplayMode.FIVE_ZONES` MUST NOT be removed, and contract literal `var hrZoneDisplayMode by rememberSaveable { mutableStateOf(ZoneCardDisplayMode.FIVE_ZONES) }` in `TrackOnMapScreen.kt` MUST remain intact.
2. **Layout Invariant**: In Compose `Row`, all active segments use `Modifier.weight(entry.percentage)`. Zero-duration zones MUST NOT receive `Modifier.weight(0f)` to prevent runtime layout exceptions.
3. **WCAG Invariant**: Uniform `0.5.dp` outline borders around the outer bar container and all legend swatches ensure $\ge 3:1$ contrast against both dark and light theme backgrounds.
4. **Database Invariant**: Zero database schema migrations or changes to raw telemetry recording.
