# Stage 3 Architecture & Implementation Plan: ATT-3773 — Configure default Zone Card Display Mode independently for Workout Summary and Workout Details

## 1. Architectural Decomposition (SWE.2)

### 1.1 DataStore, Migration & Preference Model Layer (`MyPreferenceManager.kt`)
- **`WorkoutCardSectionPreferences` (Summary / List View)**:
  - Update default constructor parameter `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.STACKED_BAR` (replacing `ZoneCardDisplayMode.COLUMNS` per user requirement in `ATT-3773`).
  - **Deserialization & Migration Path (`workoutCardPreferencesFlow`)**:
    - If `preferences[WORKOUT_CARD_ZONE_DISPLAY_MODE]` is present (`rawMode != null`), preserve the user's persisted selection (mapping legacy `"FIVE_ZONES"` to `ZoneCardDisplayMode.COLUMNS`, or `safeEnumValueOf(rawMode, ZoneCardDisplayMode.STACKED_BAR)`).
    - If `WORKOUT_CARD_ZONE_DISPLAY_MODE` is unset (`rawMode == null`), default to `ZoneCardDisplayMode.STACKED_BAR`.
- **`WorkoutDetailPreferences` (Workout Details / `TrackOnMapScreen`)**:
  - Add constructor parameter `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.HISTOGRAM`.
  - Add companion object key `val WORKOUT_DETAIL_ZONE_DISPLAY_MODE = stringPreferencesKey("workout_detail_zone_display_mode")`.
  - **Deserialization & Migration Path (`workoutDetailPreferencesFlow`)**:
    - Read `val rawMode = preferences[WORKOUT_DETAIL_ZONE_DISPLAY_MODE]`.
    - When `rawMode != null`, map `"FIVE_ZONES"` to `ZoneCardDisplayMode.COLUMNS` or `safeEnumValueOf(rawMode, ZoneCardDisplayMode.HISTOGRAM)`.
    - When `rawMode == null` (first launch after update or fresh install), initialize to `ZoneCardDisplayMode.HISTOGRAM` as explicitly specified by the user in `ATT-3773` (*"Workout Details: Default = ZoneCardDisplayMode.HISTOGRAM ('Histogramm', with automatic fallback to COLUMNS if fine-grained histogram data is absent)"*).
  - **Persistence & Disabled `showZoneAnalysis` Retention**:
    - `setWorkoutDetailPreferences(prefs: WorkoutDetailPreferences)` persists `preferences[WORKOUT_DETAIL_ZONE_DISPLAY_MODE] = prefs.zoneCardDisplayMode.name`.
    - When `showZoneAnalysis` is toggled to `false` in either Summary or Details, the `zoneCardDisplayMode` value in `WorkoutCardSectionPreferences` / `WorkoutDetailPreferences` and its underlying DataStore key (`WORKOUT_CARD_ZONE_DISPLAY_MODE` / `WORKOUT_DETAIL_ZONE_DISPLAY_MODE`) are **retained (not cleared)**—merely ignored while `showZoneAnalysis == false`—so re-enabling `showZoneAnalysis` restores the athlete's previously chosen display mode without data loss.

### 1.2 UI Presentation Layer & Parameter Backward Compatibility
- **`HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt`**:
  - Add trailing default parameter `showModeSelector: Boolean = true`:
    ```kotlin
    @Composable
    fun HeartRateZoneDistributionCard(
        distribution: ZoneDistributionData,
        modifier: Modifier = Modifier,
        displayMode: ZoneCardDisplayMode? = null,
        onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null,
        showModeSelector: Boolean = true
    )
    ```
    (and identical signature on `PowerZoneDistributionCard`).
  - Defaulting `showModeSelector: Boolean = true` guarantees 100% source and binary backward compatibility for all existing call sites (`TrackOnMapScreen.kt` and `@Preview` functions).
  - Gate the bottom `SingleChoiceSegmentedButtonRow` row on `if (showModeSelector && distribution.entries.isNotEmpty())`.
- **`WorkoutSummary.kt`**:
  - Pass `showModeSelector = false` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` in `WorkoutSectionType.ZONES` so summary cards render strictly in `effectiveHrZoneDisplayMode` / `effectivePowerZoneDisplayMode` (`preferences.zoneCardDisplayMode`, defaulting to `STACKED_BAR`) without the bottom switcher row.
- **`TrackOnMapScreen.kt`**:
  - Update lines 157–158 so `effectiveHrZoneDisplayMode = hrZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode` and `effectivePowerZoneDisplayMode = powerZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode` (retaining `showModeSelector = true` default on `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`).
- **`WorkoutMasksAndCardsSection.kt` Refactoring Strategy (`< 400` Lines Invariant, `REQ-UI-262`)**:
  - Extract a reusable private helper composable `ZoneDisplayModeSelector(label: String, selectedMode: ZoneCardDisplayMode, onModeSelected: (ZoneCardDisplayMode) -> Unit)` encapsulating the 3-way `SingleChoiceSegmentedButtonRow` (`STACKED_BAR`, `COLUMNS`, `HISTOGRAM`).
  - Condense the 9-branch `when (type)` expression for `MatrixFeatureRow` (lines 144–219) into concise single-expression branches, reducing total file length from 398 lines to ~335 lines (strictly `< 400` lines).
  - When `workoutCardPrefs.showZoneAnalysis || workoutDetailPrefs.showZoneAnalysis`:
    - If `workoutCardPrefs.showZoneAnalysis`: render `ZoneDisplayModeSelector` labeled `"${stringResource(R.string.settings_zone_card_display_mode_title)} (${stringResource(R.string.tuning_matrix_col_list)})"` calling `onWorkoutCardPrefsChange(workoutCardPrefs.copy(zoneCardDisplayMode = mode))`.
    - If `workoutDetailPrefs.showZoneAnalysis`: render `ZoneDisplayModeSelector` labeled `"${stringResource(R.string.settings_zone_card_display_mode_title)} (${stringResource(R.string.tuning_matrix_col_details)})"` calling `onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(zoneCardDisplayMode = mode))`.

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
   - Extract `ZoneDisplayModeSelector` and condense `MatrixFeatureRow` branches to render independent Summary (`workoutCardPrefs.copy(zoneCardDisplayMode = mode)`) and Details (`workoutDetailPrefs.copy(zoneCardDisplayMode = mode)`) selectors while keeping total file length `< 400` lines (~335 lines).
4. **Step 4 (`WorkoutCardSectionPreferencesTest.kt`, `WorkoutSummaryZoneDisplayModeTest.kt`)**:
   - Update unit tests to verify Summary default (`STACKED_BAR`), Details default (`HISTOGRAM`), retention of `zoneCardDisplayMode` when `showZoneAnalysis = false`, `showModeSelector = true` default vs `showModeSelector = false` in `WorkoutSummary.kt`, and `< 400` line count in `AdvancedTuningModularityTest`:
     `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutCardSectionPreferencesTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryZoneDisplayModeTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest"`

## 4. Invariant Protection & Rollback Safety
- Tier 3 domain fallback (`ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(distribution.histogram != null)` -> `COLUMNS`) strictly preserved.
- `showZoneAnalysis == false` retains persisted `zoneCardDisplayMode` preferences without data loss.
- `WorkoutMasksAndCardsSection.kt` `< 400` line limit strictly preserved (`REQ-UI-262`).
- Single-line non-wrapping `SegmentedButton` layout (`REQ-UI-386` / Rule 38) and legend-free `STACKED_BAR` (`REQ-UI-387`) strictly preserved.
