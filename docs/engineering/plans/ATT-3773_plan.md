# Stage 3 Architecture & Implementation Plan: ATT-3773 — Configure default Zone Card Display Mode independently for Workout Summary and Workout Details

## 1. Architectural Decomposition (SWE.2)

### 1.1 DataStore, Migration, Downgrade Safety & Preference Model Layer (`MyPreferenceManager.kt`)
- **`parseZoneCardDisplayMode(rawMode: String?, defaultMode: ZoneCardDisplayMode): ZoneCardDisplayMode`**:
  - Extract a deterministic top-level function in `MyPreferenceManager.kt`:
    ```kotlin
    internal fun parseZoneCardDisplayMode(
        rawMode: String?,
        defaultMode: ZoneCardDisplayMode
    ): ZoneCardDisplayMode {
        if (rawMode == null) return defaultMode
        if (rawMode == "FIVE_ZONES") return ZoneCardDisplayMode.COLUMNS
        return safeEnumValueOf(rawMode, defaultMode)
    }
    ```
  - **Legacy Migration & Forward/Downgrade Safety**:
    - When `rawMode == null` (unconfigured or fresh install), returns `defaultMode` (`ZoneCardDisplayMode.STACKED_BAR` for Summary, `ZoneCardDisplayMode.HISTOGRAM` for Details).
    - When `rawMode == "FIVE_ZONES"` (legacy schema), maps deterministically to `ZoneCardDisplayMode.COLUMNS` in both Summary and Details contexts.
    - When `rawMode` contains an unknown/future enum string (e.g., after an app version downgrade), `safeEnumValueOf(rawMode, defaultMode)` safely falls back to `defaultMode` without throwing `IllegalArgumentException`.
- **`WorkoutCardSectionPreferences` (Summary / List View)**:
  - Update default constructor parameter `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.STACKED_BAR`.
  - In `workoutCardPreferencesFlow`, resolve `zoneCardDisplayMode = parseZoneCardDisplayMode(preferences[WORKOUT_CARD_ZONE_DISPLAY_MODE], ZoneCardDisplayMode.STACKED_BAR)`.
- **`WorkoutDetailPreferences` (Workout Details / `TrackOnMapScreen`)**:
  - Add constructor parameter `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.HISTOGRAM`.
  - Add companion object key `val WORKOUT_DETAIL_ZONE_DISPLAY_MODE = stringPreferencesKey("workout_detail_zone_display_mode")`.
  - In `workoutDetailPreferencesFlow`, resolve `zoneCardDisplayMode = parseZoneCardDisplayMode(preferences[WORKOUT_DETAIL_ZONE_DISPLAY_MODE], ZoneCardDisplayMode.HISTOGRAM)`.
  - In `setWorkoutDetailPreferences(prefs: WorkoutDetailPreferences)`, persist `preferences[WORKOUT_DETAIL_ZONE_DISPLAY_MODE] = prefs.zoneCardDisplayMode.name`.
  - **Disabled `showZoneAnalysis` Retention**: When `showZoneAnalysis` is toggled to `false` in either Summary or Details, the persisted `WORKOUT_CARD_ZONE_DISPLAY_MODE` / `WORKOUT_DETAIL_ZONE_DISPLAY_MODE` keys are retained in DataStore (merely ignored while hidden) so re-enabling `showZoneAnalysis` restores the user's selected mode.

### 1.2 UI Presentation Layer, State Synchronization & Modular Extraction
- **`HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt`**:
  - Add trailing default parameter `showModeSelector: Boolean = true` (preserving 100% source and binary backward compatibility for all existing call sites) and gate the bottom `SingleChoiceSegmentedButtonRow` row on `if (showModeSelector && distribution.entries.isNotEmpty())`.
- **`WorkoutSummary.kt` (Workout Summary List)**:
  - Pass `showModeSelector = false` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` in `WorkoutSectionType.ZONES` so summary cards render strictly in `effectiveHrZoneDisplayMode` / `effectivePowerZoneDisplayMode` (`preferences.zoneCardDisplayMode`, defaulting to `STACKED_BAR`) without the bottom switcher row.
- **`TrackOnMapScreen.kt` (Workout Details View — State Synchronization)**:
  - Key the local session override state against `activeDetailPrefs.zoneCardDisplayMode`:
    ```kotlin
    var hrZoneDisplayMode by rememberSaveable(activeDetailPrefs.zoneCardDisplayMode) { mutableStateOf<ZoneCardDisplayMode?>(null) }
    var powerZoneDisplayMode by rememberSaveable(activeDetailPrefs.zoneCardDisplayMode) { mutableStateOf<ZoneCardDisplayMode?>(null) }
    val effectiveHrZoneDisplayMode = hrZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode
    val effectivePowerZoneDisplayMode = powerZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode
    ```
  - **Lifecycle & Settings Synchronization Behavior**:
    1. While viewing a workout in `TrackOnMapScreen`, manual taps on the card's `SingleChoiceSegmentedButtonRow` (`showModeSelector = true`) update `hrZoneDisplayMode` / `powerZoneDisplayMode` across configuration changes (e.g., screen rotation).
    2. When the user navigates away from `TrackOnMapScreen`, the composition state is disposed so subsequent workouts open in the configured `activeDetailPrefs.zoneCardDisplayMode` default.
    3. Because `rememberSaveable` is keyed on `activeDetailPrefs.zoneCardDisplayMode`, changing the global Details default in Advanced Settings automatically resets any local override to `null` and immediately applies the new default.
- **`WorkoutMasksAndCardsSection.kt` Modularity Refactoring (`< 400` Lines, `REQ-UI-262`)**:
  - Extract a dedicated factory function `private fun buildMatrixFeatureRow(type: WorkoutSectionType, workoutCardPrefs: WorkoutCardSectionPreferences, onWorkoutCardPrefsChange: (WorkoutCardSectionPreferences) -> Unit, workoutDetailPrefs: WorkoutDetailPreferences, onWorkoutDetailPrefsChange: (WorkoutDetailPreferences) -> Unit): MatrixFeatureRow` and a dedicated composable `@Composable private fun ZoneDisplayModeSelector(label: String, selectedMode: ZoneCardDisplayMode, onModeSelected: (ZoneCardDisplayMode) -> Unit)`.
  - When `workoutCardPrefs.showZoneAnalysis || workoutDetailPrefs.showZoneAnalysis`:
    - If `workoutCardPrefs.showZoneAnalysis`: invoke `ZoneDisplayModeSelector` with `"${stringResource(R.string.settings_zone_card_display_mode_title)} (${stringResource(R.string.tuning_matrix_col_list)})"` and `{ mode -> onWorkoutCardPrefsChange(workoutCardPrefs.copy(zoneCardDisplayMode = mode)) }`.
    - If `workoutDetailPrefs.showZoneAnalysis`: invoke `ZoneDisplayModeSelector` with `"${stringResource(R.string.settings_zone_card_display_mode_title)} (${stringResource(R.string.tuning_matrix_col_details)})"` and `{ mode -> onWorkoutDetailPrefsChange(workoutDetailPrefs.copy(zoneCardDisplayMode = mode)) }`.

## 2. UI Consistency (Rule 23 & Rule 38)
- **Reference Screen**: `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`.
- **Reused Components & Theme Tokens**:
  - `SingleChoiceSegmentedButtonRow` and `SegmentedButton` with `icon = {}`, `contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, `MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, `TextOverflow.Ellipsis` (`REQ-UI-386` / Rule 38).
- **One-Off Style Justification**: Zero new one-off styles.

## 3. Atomic Implementation Steps
1. **Step 1 (`MyPreferenceManager.kt`)**:
   - Add `parseZoneCardDisplayMode(rawMode, defaultMode)` handling `null` -> `defaultMode`, `"FIVE_ZONES"` -> `COLUMNS`, and `safeEnumValueOf` downgrade safety.
   - Update `WorkoutCardSectionPreferences.zoneCardDisplayMode` default to `ZoneCardDisplayMode.STACKED_BAR` and wire `workoutCardPreferencesFlow`.
   - Add `val zoneCardDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.HISTOGRAM` to `WorkoutDetailPreferences`, add `WORKOUT_DETAIL_ZONE_DISPLAY_MODE`, and wire `workoutDetailPreferencesFlow` and `setWorkoutDetailPreferences`.
2. **Step 2 (`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`)**:
   - Add `showModeSelector: Boolean = true` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` and gate the segmented button row on `if (showModeSelector && distribution.entries.isNotEmpty())`.
   - Pass `showModeSelector = false` in `WorkoutSummary.kt`.
   - Bind `effectiveHrZoneDisplayMode` and `effectivePowerZoneDisplayMode` in `TrackOnMapScreen.kt` to `activeDetailPrefs.zoneCardDisplayMode` with `rememberSaveable(activeDetailPrefs.zoneCardDisplayMode)`.
3. **Step 3 (`WorkoutMasksAndCardsSection.kt`)**:
   - Extract `buildMatrixFeatureRow` and `ZoneDisplayModeSelector` to render independent Summary and Details selectors cleanly within `< 400` lines.
4. **Step 4 (`WorkoutCardSectionPreferencesTest.kt`, `WorkoutSummaryZoneDisplayModeTest.kt`)**:
   - Add migration & downgrade unit tests for `parseZoneCardDisplayMode` (`null`, `"FIVE_ZONES"`, `"STACKED_BAR"`, `"COLUMNS"`, `"HISTOGRAM"`, `"UNKNOWN_FUTURE_MODE"`) alongside Summary/Details default, `showModeSelector`, and `< 400` line modularity tests:
     `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutCardSectionPreferencesTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryZoneDisplayModeTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningModularityTest"`

## 4. Invariant Protection & Rollback Safety
- Tier 3 domain fallback (`ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(distribution.histogram != null)` -> `COLUMNS`) strictly preserved.
- Legacy `"FIVE_ZONES"` migration and downgrade fallback (`safeEnumValueOf`) strictly preserved.
- `WorkoutMasksAndCardsSection.kt` `< 400` line limit strictly preserved (`REQ-UI-262`).
- Single-line non-wrapping `SegmentedButton` layout (`REQ-UI-386` / Rule 38) and legend-free `STACKED_BAR` (`REQ-UI-387`) strictly preserved.
