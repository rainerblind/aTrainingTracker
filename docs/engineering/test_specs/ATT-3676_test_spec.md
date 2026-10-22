# Stage 2 Requirement & Test Specification: ATT-3676 - Expose default zone card display mode selector in Advanced Settings and wire it to workout summary zone cards

**Ticket**: [ATT-3676](https://rainerblind.atlassian.net/browse/ATT-3676)  
**Sub-task**: [ATT-3699](https://rainerblind.atlassian.net/browse/ATT-3699) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `None`  
**Active Sprint**: `Sprint 2026-42.5`  
**Branch**: `feature/ATT-3676`  
**Requirement Mapping**: `REQ-UI-372` (amended with `REQ-UI-372.8`, complementing `REQ-UI-262`)  
**Test Mapping**: `TST-UI-335`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. System Requirement Specification (`REQ-UI-372` Amended)

### REQ-UI-372: Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching in Workout Summary Cards

The system SHALL provide a continuous horizontal stacked distribution bar as an alternative display mode alongside the vertical column chart and fine-grained telemetry histogram in workout aftermath zone cards (`HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`), and expose a default display mode selector in Advanced Settings wired reactively to workout summaries (ATT-3390, ATT-3676, amending `REQ-UI-202` and `REQ-UI-203`, and complementing `REQ-UI-231`, `REQ-UI-251`, `REQ-UI-262`, and `REQ-UI-370`):

1. **Continuous Horizontal Stacked Distribution Bar (`REQ-UI-372.1`)**:
   - `ZoneDistributionStackedBar.kt` SHALL expose a composable function `ZoneDistributionStackedBar(distribution: ZoneDistributionData, modifier: Modifier = Modifier, barHeight: Dp = 16.dp, showLegend: Boolean = true)` rendering active workout duration normalized to 100%.
   - Active zone entries ($d_i > 0$ and $p_i > 0.0$) SHALL be laid out horizontally in contiguous order (Zone 1 to Zone 5 for Heart Rate; Zone 1 to Zone 7 for Power).
   - Each active segment's width SHALL be weighted in Compose `Row` using `Modifier.weight(entry.percentage)` directly, guaranteeing that segments sum to exactly 100% of the bar's inner width without layout overflow or rounding clipping.
   - Outer bar container SHALL be clipped with `RoundedCornerShape(6.dp)`, enclosed with a uniform contour border `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`, and filled with background `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`.
   - Active segments SHALL be separated by uniform vertical dividers (`1.dp` width, `color = MaterialTheme.colorScheme.surface`).
   - If total active time is 0 or all zone entries have 0 duration, the component SHALL render an empty neutral container track without throwing layout or weighting exceptions.
   - Zero-duration zones ($d_i = 0$) SHALL be gracefully omitted from the horizontal bar.

2. **Contrast, Border & Theme Uniformity (`REQ-UI-372.2`)**:
   - To eliminate visual jitter and ensure consistent aesthetic rhythm, a uniform border outline `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` SHALL be applied around the entire outer stacked bar container and around all circular legend badge dots (`7.dp`).
   - Compliant contrast ($\ge 3:1$ Non-Text Contrast per WCAG 2.1 AA 1.4.11) across all theme environments: dark swatches (`TTColor.Zone6`, `TTColor.Zone7`) remain distinctly bounded against dark surfaces, and light swatches (`TTColor.Zone1`) remain distinctly bounded against light surfaces.

3. **Compact Integrated Legend & Responsive Wrapping (`REQ-UI-372.3`)**:
   - Below the horizontal bar, a responsive legend SHALL render all configured zones of the metric model (Z1..Z5 for Heart Rate, Z1..Z7 for Cycling Power).
   - Legend items SHALL be arranged in a responsive wrapping layout (`FlowRow` with `horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)`).
   - Each legend item SHALL render: circular color badge (`7.dp`) with matching zone fill and uniform outline border, zone badge text (`Z1`..`Z5` or `Z1`..`Z7`) in `labelSmall` bold, localized zone name, formatted duration (`ZoneDistributionChartMath.formatZoneDuration(entry.durationSec)`), and percentage readout (`String.format(Locale.getDefault(), "%.0f%%", entry.percentage)`).
   - Inactive zones ($d_i = 0$) SHALL be visually dimmed (`alpha = 0.45f`, displaying `0:00 (0%)`).

4. **Expanded Display Modes & Enum Parity (`REQ-UI-372.4`)**:
   - `enum class ZoneCardDisplayMode` in `ZoneDistributionModels.kt` SHALL define: `STACKED_BAR`, `COLUMNS`, `HISTOGRAM`, and backward-compatible legacy alias `FIVE_ZONES` for `COLUMNS` to satisfy existing contract tests.

5. **Card Mode Switching & Segmented Buttons (`REQ-UI-372.5`)**:
   - In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`:
     - When `distribution.histogram != null`: The segmented button row SHALL render 3 buttons: `STACKED_BAR` (R.string.zone_mode_stacked_bar), `COLUMNS` (R.string.zone_mode_columns), and `HISTOGRAM` (R.string.zone_mode_histogram).
     - When `distribution.histogram == null`: The segmented button row SHALL render 2 buttons: `STACKED_BAR` and `COLUMNS`.
     - When `effectiveDisplayMode` is `STACKED_BAR`, the card body renders `ZoneDistributionStackedBar`.
     - When `effectiveDisplayMode` is `COLUMNS` or `FIVE_ZONES`, the card body renders `ZoneDistributionColumnChart`.
     - When `effectiveDisplayMode` is `HISTOGRAM`, the card body renders `TelemetryHistogramChart`.

6. **Preference Persistence (`REQ-UI-372.6`)**:
   - `WorkoutCardSectionPreferences` in `MyPreferenceManager.kt` SHALL include `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.COLUMNS`.
   - Athlete's selected mode SHALL be persisted across application restarts via DataStore key `workout_card_zone_display_mode`.

7. **9-Language Localization Parity (`REQ-UI-372.7`)**:
   - `zone_mode_stacked_bar`, `zone_mode_columns`, `zone_mode_histogram`, and `settings_zone_card_display_mode_title` SHALL be defined across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

8. **Default Zone Card Display Mode Selector in Advanced Settings & Workout Summary Wiring (`REQ-UI-372.8` - ATT-3676)**:
   - The system SHALL expose a dedicated `SingleChoiceSegmentedButtonRow` selector for `ZoneCardDisplayMode` in `WorkoutMasksAndCardsSection.kt` under Advanced Settings, displaying options `STACKED_BAR` (`R.string.zone_mode_stacked_bar`), `COLUMNS` (`R.string.zone_mode_columns`), and `HISTOGRAM` (`R.string.zone_mode_histogram`).
   - Visibility Guard: The selector SHALL be displayed conditionally when either `workoutCardPrefs.showZoneAnalysis == true` or `workoutDetailPrefs.showZoneAnalysis == true`.
   - Title & Localization: The selector SHALL display localized title `settings_zone_card_display_mode_title` across all 9 application locales.
   - State Synchronization & Precedence Architecture:
     - Tier 1 (Interactive Manual Override): If the athlete taps any display mode button on an individual card, that manual selection (`userSelectedMode`) takes precedence for that specific card during the active session.
     - Tier 2 (Reactive Global Default): When `userSelectedMode == null`, the card reactively reflects `preferences.zoneCardDisplayMode`. Mutations to the global preference in Advanced Settings update cards dynamically upon recomposition.
     - Tier 3 (Domain Fallback Guard): If the effective display mode resolves to `HISTOGRAM` on an activity where fine-grained histogram data is absent (`distribution.histogram == null`), `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` SHALL gracefully fall back to `ZoneCardDisplayMode.COLUMNS`, ensuring neither an empty dash nor an unselected segmented button state is rendered.
   - Modularity Invariant (REQ-UI-262): `WorkoutMasksAndCardsSection.kt` SHALL remain strictly $\le 400$ lines.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-372` (*Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching in Workout Summary Cards*) and complements `REQ-UI-262` (*Advanced Tuning Modular Accordion & Settings Hierarchy*). Targets `WorkoutMasksAndCardsSection.kt`, `WorkoutSummary.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `TrackOnMapScreen.kt`, and `strings.xml`.
* **Historical Origin & Commit Trace**: Commit `e3966ebf` (Sprint `2026-40.5`, `ATT-1390`), commit `7b14d9a2` (Sprint `2026-40.8`, `ATT-1845`), commit `c8192a0e` (Sprint `2026-41.2`, `ATT-2147`), and commit `38706240` (Sprint `2026-42.4`, `ATT-3390`).
* **Root Reason for Existing Formulation**: `ATT-3390` introduced the `ZoneDistributionStackedBar` composable, the 3-way interactive segmented button switching on individual cards, and the `WORKOUT_CARD_ZONE_DISPLAY_MODE` DataStore preference key. The UI selector in Advanced Settings and the propagation into `WorkoutSummary.kt` were deferred to keep `ATT-3390` tightly bounded to the graphical presentation component.
* **Preservation of Core Invariants**:
  1. *Interactive Session Toggling*: Per-card interactive switching remains fully functional; user taps on individual cards take precedence for the active session.
  2. *Legacy Compatibility*: `ZoneCardDisplayMode.FIVE_ZONES` remains an alias for `COLUMNS` preserving contract assertions in `TelemetryMetricGraphMarginalBarsContractTest.kt`.
  3. *Modularity Invariant (REQ-UI-262)*: `WorkoutMasksAndCardsSection.kt` must remain strictly $\le 400$ lines.
  4. *Histogram Graceful Fallback*: Activities lacking raw histogram data must never render empty dashes or broken button states.
  5. *100% Clean-Room Test Suite Pass Rate*: Zero regressions across all ~2,954 unit tests.

---

## 3. Acceptance Criteria (Given-When-Then)

### Scenario 1: Default Selector in Advanced Settings
* **Given** an athlete in Advanced Settings navigating to "Workout-Masken & Detailkarten",
* **When** "Zone Analysis" is enabled in either List or Details,
* **Then** a dedicated 3-way segmented button selector titled "Zone Card Display Mode" (`settings_zone_card_display_mode_title`) is displayed with options "Stacked Bar" (`R.string.zone_mode_stacked_bar`), "Columns" (`R.string.zone_mode_columns`), and "Histogram" (`R.string.zone_mode_histogram`).
* **And** selecting any option updates `workoutCardPrefs.zoneCardDisplayMode` immediately and persists to DataStore on Save.

### Scenario 2: Reactive Global Default Propagation in Workout Summary
* **Given** `workoutCardPrefs.zoneCardDisplayMode` configured as `STACKED_BAR`,
* **When** viewing the workout journal list (`WorkoutSummary.kt`),
* **Then** all `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` instances initialize displaying the horizontal stacked bar (`ZoneDistributionStackedBar`).
* **And** when the global preference in DataStore is updated to `COLUMNS`, all cards without manual overrides reactively re-render as column charts (`ZoneDistributionColumnChart`).

### Scenario 3: Interactive Manual Override Precedence
* **Given** a workout summary card currently rendering in `STACKED_BAR` by global default,
* **When** the athlete taps the "Columns" button on that specific card,
* **Then** that card switches to `COLUMNS`.
* **And** when the global preference subsequently changes in the background, this card preserves its manual selection (`COLUMNS`) during the active session.

### Scenario 4: Graceful Histogram Fallback on Telemetry Absence
* **Given** global preference configured as `HISTOGRAM`,
* **When** viewing an activity without fine-grained telemetry histogram data (`distribution.histogram == null`),
* **Then** `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` gracefully resolve to `COLUMNS`.
* **And** the 2-button segmented row (`STACKED_BAR` and `COLUMNS`) highlights "Columns" as selected, avoiding empty dashes or unselected button states.

### Scenario 5: Modularity Invariant Compliance (REQ-UI-262)
* **Given** `WorkoutMasksAndCardsSection.kt` after adding the segmented button row selector,
* **When** `AdvancedTuningModularityTest.testAllTuningSourceFilesUnder400Lines` executes,
* **Then** the file length is strictly $\le 400$ lines.

---

## 4. Verification & Test Specification (`TST-UI-335` Extension)

### 4.1 Unit Test Suite Extensions
1. **`WorkoutCardSectionPreferencesTest.kt`**:
   - Verify `zoneCardDisplayMode` defaults to `ZoneCardDisplayMode.COLUMNS`.
   - Verify serialization/deserialization to DataStore across `STACKED_BAR`, `COLUMNS`, and `HISTOGRAM`.
   - Verify legacy migration fallback: `FIVE_ZONES` string deserializes cleanly to `ZoneCardDisplayMode.COLUMNS`.
2. **`ZoneCardDisplayModeFallbackTest.kt`**:
   - Test resolution helper: `resolveEffectiveZoneCardDisplayMode(mode, hasHistogram)`.
   - Assert `(HISTOGRAM, hasHistogram = false)` returns `COLUMNS`.
   - Assert `(HISTOGRAM, hasHistogram = true)` returns `HISTOGRAM`.
   - Assert `(STACKED_BAR, hasHistogram = false)` returns `STACKED_BAR`.
   - Assert `(COLUMNS, hasHistogram = false)` returns `COLUMNS`.
3. **`WorkoutSummaryZoneDisplayModeTest.kt`**:
   - Verify initial display mode evaluates to `preferences.zoneCardDisplayMode`.
   - Verify manual toggle sets local override.
   - Verify global preference mutation updates card when local override is null.
   - Verify global preference mutation is superseded when local override is set.
4. **`ZoneCardDisplayModeLocalizationTest.kt`**:
   - Audit `settings_zone_card_display_mode_title`, `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram` across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   - Assert non-empty string and zero missing translations.
5. **`AdvancedTuningModularityTest.kt`**:
   - Assert `WorkoutMasksAndCardsSection.kt` line count $\le 400$.

---

## 5. Traceability Matrix

| Requirement Clause | Specification | Test Case | Target Artifact |
|---|---|---|---|
| `REQ-UI-372.6` | DataStore persistence of `zoneCardDisplayMode` | `WorkoutCardSectionPreferencesTest` | `MyPreferenceManager.kt` |
| `REQ-UI-372.8.a` | 3-way segmented button selector in Advanced Settings | `WorkoutMasksAndCardsSectionTest` | `WorkoutMasksAndCardsSection.kt` |
| `REQ-UI-372.8.b` | State precedence: global default vs manual override | `WorkoutSummaryZoneDisplayModeTest` | `WorkoutSummary.kt`, `TrackOnMapScreen.kt` |
| `REQ-UI-372.8.c` | Graceful fallback when `histogram == null` | `ZoneCardDisplayModeFallbackTest` | `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt` |
| `REQ-UI-372.7` | 9-Language localization token parity | `ZoneCardDisplayModeLocalizationTest` | `strings.xml` (all 9 locales) |
| `REQ-UI-262` | Strict modularity line ceiling ($\le 400$ lines) | `AdvancedTuningModularityTest` | `WorkoutMasksAndCardsSection.kt` |
