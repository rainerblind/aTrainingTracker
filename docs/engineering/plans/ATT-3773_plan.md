# Stage 3 Architecture & Implementation Plan: ATT-3773 — Configure default Zone Card Display Mode independently for Workout Summary and Workout Details

## 1. Architectural Decomposition (SWE.2)

### 1.1 DataStore & Preference Model Layer (`MyPreferenceManager.kt`)
- **`WorkoutCardSectionPreferences`**:
  - Update default constructor parameter `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.STACKED_BAR` (previously `ZoneCardDisplayMode.COLUMNS`).
  - Update `workoutCardPreferencesFlow` to fall back to `ZoneCardDisplayMode.STACKED_BAR` when `WORKOUT_CARD_ZONE_DISPLAY_MODE` is unset or unparseable, while preserving `"FIVE_ZONES"` -> `ZoneCardDisplayMode.COLUMNS` legacy migration.
- **`WorkoutDetailPreferences`**:
  - Add constructor parameter `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.HISTOGRAM`.
  - Add companion object key `val WORKOUT_DETAIL_ZONE_DISPLAY_MODE = stringPreferencesKey("workout_detail_zone_display_mode")`.
  - In `workoutDetailPreferencesFlow`, deserialize `preferences[WORKOUT_DETAIL_ZONE_DISPLAY_MODE]` using `if (rawMode == "FIVE_ZONES") ZoneCardDisplayMode.COLUMNS else safeEnumValueOf(rawMode, ZoneCardDisplayMode.HISTOGRAM)`, defaulting to `ZoneCardDisplayMode.HISTOGRAM` when unset.
  - In `setWorkoutDetailPreferences(prefs: WorkoutDetailPreferences)`, persist `preferences[WORKOUT_DETAIL_ZONE_DISPLAY_MODE] = prefs.zoneCardDisplayMode.name`.

### 1.2 UI Presentation Layer (`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`, `WorkoutMasksAndCardsSection.kt`)
- **`HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt`**:
  - Add parameter `showModeSelector: Boolean = true` to both composables and document `@param showModeSelector` in KDoc.
  - Gate the bottom `SingleChoiceSegmentedButtonRow` row on `if (showModeSelector && distribution.entries.isNotEmpty())`.
- **`WorkoutSummary.kt`**:
  - Pass `showModeSelector = false` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` in `WorkoutSectionType.ZONES` so summary cards render strictly in the configured summary display mode (`STACKED_BAR` by default) without the bottom switcher row.
- **`TrackOnMapScreen.kt`**:
  - Update lines 157–158 so `effectiveHrZoneDisplayMode = hrZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode` and `effectivePowerZoneDisplayMode = powerZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode` (retaining `showModeSelector = true` default on `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`).
- **`WorkoutMasksAndCardsSection.kt`**:
  - When `workoutCardPrefs.showZoneAnalysis || workoutDetailPrefs.showZoneAnalysis`:
    - If `workoutCardPrefs.showZoneAnalysis`: render a 3-way `SingleChoiceSegmentedButtonRow` labeled `"${stringResource(R.string.settings_zone_card_display_mode_title)} (${stringResource(R.string.tuning_matrix_col_list)})"` updating `onWorkoutCardPrefsChange(workoutCardPrefs.copy(zoneCardDisplayMode = mode))`.
    - If `workoutDetailPrefs.showZoneAnalysis`: render a 3-way `SingleChoiceSegmentedButtonRow` labeled `"${stringResource(R.string.settings_zone_card_display_mode_title)} (${stringResource(R.string.tuning_matrix_col_details)})"` updating `onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(zoneCardDisplayMode = mode))`.
  - Condense the `MatrixFeatureRow` mapping in `WorkoutMasksAndCardsSection.kt` so the file stays strictly `< 400` lines (`REQ-UI-262`).

## 2. UI Consistency (Rule 23 & Rule 38)
- **Reference Screen**: `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`.
- **Reused Components & Theme Tokens**:
  - `SingleChoiceSegmentedButtonRow` and `SegmentedButton` with `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, `MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, `TextOverflow.Ellipsis` (`REQ-UI-386` / Rule 38).
  - Reuses existing 9-locale string keys (`R.string.settings_zone_card_display_mode_title`, `R.string.tuning_matrix_col_list`, `R.string.tuning_matrix_col_details`, `R.string.zone_mode_stacked_bar`, `R.string.zone_mode_columns`, `R.string.zone_mode_histogram`).
- **One-Off Style Justification**: Zero new one-off styles.

## 3. Atomic Implementation Steps
1. **Step 1 (`MyPreferenceManager.kt`)**:
   - Update `WorkoutCardSectionPreferences.zoneCardDisplayMode` default to `ZoneCardDisplayMode.STACKED_BAR` and `workoutCardPreferencesFlow` fallback to `ZoneCardDisplayMode.STACKED_BAR`.
   - Add `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.HISTOGRAM` to `WorkoutDetailPreferences`, add `WORKOUT_DETAIL_ZONE_DISPLAY_MODE`, and wire in `workoutDetailPreferencesFlow` and `setWorkoutDetailPreferences`.
2. **Step 2 (`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`)**:
   - Add `showModeSelector: Boolean = true` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` and gate the segmented button row on `if (showModeSelector && distribution.entries.isNotEmpty())`.
   - Pass `showModeSelector = false` in `WorkoutSummary.kt`.
   - Bind `effectiveHrZoneDisplayMode` and `effectivePowerZoneDisplayMode` in `TrackOnMapScreen.kt` to `activeDetailPrefs.zoneCardDisplayMode`.
3. **Step 3 (`WorkoutMasksAndCardsSection.kt`)**:
   - Render independent Summary (`workoutCardPrefs.copy(zoneCardDisplayMode = mode)`) and Details (`workoutDetailPrefs.copy(zoneCardDisplayMode = mode)`) 3-way `SingleChoiceSegmentedButtonRow` selectors while keeping total file length `< 400` lines.
4. **Step 4 (`WorkoutCardSectionPreferencesTest.kt`, `WorkoutSummaryZoneDisplayModeTest.kt`)**:
   - Update `WorkoutCardSectionPreferencesTest` and `WorkoutSummaryZoneDisplayModeTest` to verify `REQ-UI-388` / `TST-UI-351`, and run targeted tests:
     `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutCardSectionPreferencesTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryZoneDisplayModeTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest"`

## 4. Invariant Protection & Rollback Safety
- Tier 3 domain fallback (`ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(distribution.histogram != null)` -> `COLUMNS`) strictly preserved.
- `WorkoutMasksAndCardsSection.kt` `< 400` line limit strictly preserved (`REQ-UI-262`).
- Single-line non-wrapping `SegmentedButton` layout (`REQ-UI-386` / Rule 38) and legend-free `STACKED_BAR` (`REQ-UI-387`) strictly preserved.
