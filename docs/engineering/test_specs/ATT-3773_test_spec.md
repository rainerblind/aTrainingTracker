# Stage 2 Requirement & Test Specification: ATT-3773 — Configure default Zone Card Display Mode independently for Workout Summary and Workout Details

## 1. Requirement Specification (`REQ-UI-388`)

### `REQ-UI-388`: Decoupled Default Zone Card Display Modes for Workout Summary and Workout Details & Switcher-Free Summary Cards
The system SHALL configure and persist the default `ZoneCardDisplayMode` independently for the Workout Summary (List / Compact Summary) and Workout Details (`TrackOnMapScreen`) views, and SHALL omit the in-card mode switcher button row in Workout Summary cards to keep the summary compact and dense (`ATT-3773`, amending `REQ-UI-372` and complementing `REQ-UI-386` and `REQ-UI-387`):
1. **Independent Preference Defaults & DataStore Persistence (`REQ-UI-388.1`)**:
   - `WorkoutCardSectionPreferences` in `MyPreferenceManager.kt` SHALL default `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.STACKED_BAR`, and `workoutCardPreferencesFlow` SHALL fall back to `ZoneCardDisplayMode.STACKED_BAR` when `WORKOUT_CARD_ZONE_DISPLAY_MODE` is unset.
   - `WorkoutDetailPreferences` in `MyPreferenceManager.kt` SHALL include `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.HISTOGRAM`, persisted via DataStore key `WORKOUT_DETAIL_ZONE_DISPLAY_MODE = stringPreferencesKey("workout_detail_zone_display_mode")` in `workoutDetailPreferencesFlow` and `setWorkoutDetailPreferences`.
2. **Independent Selectors in Advanced Settings (`REQ-UI-388.2`)**:
   - In `WorkoutMasksAndCardsSection.kt`, when `workoutCardPrefs.showZoneAnalysis || workoutDetailPrefs.showZoneAnalysis`, the system SHALL expose independent 3-way `SingleChoiceSegmentedButtonRow` selectors (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`):
     - When `workoutCardPrefs.showZoneAnalysis == true`: a selector labeled with `R.string.settings_zone_card_display_mode_title` and `R.string.tuning_matrix_col_list` bound to `workoutCardPrefs.zoneCardDisplayMode` (`workoutCardPrefs.copy(zoneCardDisplayMode = mode)`).
     - When `workoutDetailPrefs.showZoneAnalysis == true`: a selector labeled with `R.string.settings_zone_card_display_mode_title` and `R.string.tuning_matrix_col_details` bound to `workoutDetailPrefs.zoneCardDisplayMode` (`workoutDetailPrefs.copy(zoneCardDisplayMode = mode)`).
   - `WorkoutMasksAndCardsSection.kt` SHALL strictly remain under 400 lines (`REQ-UI-262`).
3. **Dense Switcher-Free Workout Summary Cards vs. Interactive Workout Details Cards (`REQ-UI-388.3`)**:
   - `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` SHALL accept parameter `showModeSelector: Boolean = true` and gate the bottom `SingleChoiceSegmentedButtonRow` on `if (showModeSelector && distribution.entries.isNotEmpty())`.
   - In `WorkoutSummary.kt` (Workout Summary list), `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` SHALL be invoked with `showModeSelector = false` and `displayMode = effectiveHrZoneDisplayMode` / `effectivePowerZoneDisplayMode` (`preferences.zoneCardDisplayMode`), omitting the mode switcher row so the summary card stays compact and dense.
   - In `TrackOnMapScreen.kt` (Workout Details), `effectiveHrZoneDisplayMode` and `effectivePowerZoneDisplayMode` SHALL resolve against `activeDetailPrefs.zoneCardDisplayMode` (`hrZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode` and `powerZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode`) with `showModeSelector = true`, preserving interactive in-card mode switching and automatic Tier 3 fallback (`resolveWithHistogramFallback(distribution.histogram != null)` -> `COLUMNS`).

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-UI-388`, amending `REQ-UI-372` (`ATT-3390` & `ATT-3676`). Targets `MyPreferenceManager.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, and `WorkoutMasksAndCardsSection.kt`.
2. **Historical Origin & Commit Trace**: Sprint `2026-42.4` (`ATT-3390` commit `38706240`) and Sprint `2026-42.5` (`ATT-3676` commit `b989e1ce`).
3. **Root Reason for Existing Formulation**: `ATT-3676` wired a single global `WorkoutCardSectionPreferences.zoneCardDisplayMode` across both Workout Summary and Workout Details before the Sprint `2026-42.5` Joint Review established that athletes want a compact, switcher-free `STACKED_BAR` in the Workout Summary list and a detailed `HISTOGRAM` (with switcher) in Workout Details.
4. **Preservation of Core Invariants**: Tier 3 histogram fallback (`HISTOGRAM` -> `COLUMNS` when `distribution.histogram == null`), single-line `SegmentedButton` typography (`REQ-UI-386` / Rule 38), legend-free `STACKED_BAR` (`REQ-UI-387`), and `< 400` line modularity cap (`REQ-UI-262`) are 100% preserved.

### Acceptance Criteria (Given-When-Then)
- **AC-1**: *Given* default `WorkoutCardSectionPreferences()` and `WorkoutDetailPreferences()`, *When* inspected without user overrides, *Then* `WorkoutCardSectionPreferences().zoneCardDisplayMode == ZoneCardDisplayMode.STACKED_BAR` and `WorkoutDetailPreferences().zoneCardDisplayMode == ZoneCardDisplayMode.HISTOGRAM`.
- **AC-2**: *Given* `WorkoutSummary.kt` rendering `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`, *When* Zone Analysis is enabled, *Then* both cards are invoked with `showModeSelector = false` and `displayMode` bound to `preferences.zoneCardDisplayMode`, omitting the bottom `SingleChoiceSegmentedButtonRow`.
- **AC-3**: *Given* `TrackOnMapScreen.kt` rendering `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`, *When* Zone Analysis is enabled, *Then* `effectiveHrZoneDisplayMode` and `effectivePowerZoneDisplayMode` resolve from `activeDetailPrefs.zoneCardDisplayMode` (with `resolveWithHistogramFallback` falling back to `COLUMNS` if `distribution.histogram == null`), and `showModeSelector` remains `true`.
- **AC-4**: *Given* `WorkoutMasksAndCardsSection.kt` in Advanced Settings, *When* Zone Analysis is enabled for Summary and/or Details, *Then* separate 3-way `SingleChoiceSegmentedButtonRow` selectors update `workoutCardPrefs.copy(zoneCardDisplayMode = mode)` and `workoutDetailPrefs.copy(zoneCardDisplayMode = mode)`, and `WorkoutMasksAndCardsSection.kt` has `< 400` lines.

## 2. Test Specification (`TST-UI-351`)

### `TST-UI-351`: Decoupled Summary/Details Zone Card Display Mode & Switcher-Free Summary Verification Suite
1. **Preference Defaults & Immutability Unit Tests (`WorkoutCardSectionPreferencesTest.kt`, `WorkoutSummaryZoneDisplayModeTest.kt`)**:
   - Verify `WorkoutCardSectionPreferences().zoneCardDisplayMode == ZoneCardDisplayMode.STACKED_BAR` and `.copy(...)` across all `ZoneCardDisplayMode` values.
   - Verify `WorkoutDetailPreferences().zoneCardDisplayMode == ZoneCardDisplayMode.HISTOGRAM` and `.copy(...)` across all `ZoneCardDisplayMode` values.
   - Verify `ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(hasHistogram = false) == ZoneCardDisplayMode.COLUMNS`.
2. **Source & Component Wiring Contract Audit (`WorkoutSummaryZoneDisplayModeTest.kt`, `AdvancedTuningModularityTest.kt`)**:
   - Verify `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` declare `showModeSelector: Boolean = true` and gate the segmented button row on `if (showModeSelector && distribution.entries.isNotEmpty())`.
   - Verify `WorkoutSummary.kt` passes `showModeSelector = false` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`.
   - Verify `TrackOnMapScreen.kt` resolves `effectiveHrZoneDisplayMode = hrZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode` and `effectivePowerZoneDisplayMode = powerZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode`.
   - Verify `WorkoutMasksAndCardsSection.kt` mutates both `workoutCardPrefs.copy(zoneCardDisplayMode = mode)` and `workoutDetailPrefs.copy(zoneCardDisplayMode = mode)` and stays `< 400` lines.
3. **9-Language Localization Parity Audit**:
   - Reuses existing verified 9-locale string keys (`settings_zone_card_display_mode_title`, `tuning_matrix_col_list`, `tuning_matrix_col_details`, `zone_mode_stacked_bar`, `zone_mode_columns`, `zone_mode_histogram`) across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

## 3. Bidirectional Traceability Matrix

| Requirement ID | Test Case ID | Verification Artifact / Test Class | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-388` | `TST-UI-351` | `WorkoutCardSectionPreferencesTest.kt`, `WorkoutSummaryZoneDisplayModeTest.kt`, `AdvancedTuningModularityTest.kt` | Specified |
