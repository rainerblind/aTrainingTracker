# Stage 1: Problem Domain & Root Cause Analysis - ATT-1870: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings

**Ticket**: [ATT-1870](https://rainerblind.atlassian.net/browse/ATT-1870)  
**Sub-task**: [ATT-1937](https://rainerblind.atlassian.net/browse/ATT-1937) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Background

### 1.1 Context & Evolution (Chesterton's Fence Archaeology)
In Sprint 2026-40.7, ticket **ATT-1742** (*High-Aesthetic Lap & Interval Split Visualizer Architecture*) introduced `LapSplitVisualizer.kt` to present proportional tonal split pace bars. In Sprint 2026-40.8, ticket **ATT-1869** (*Lap Split Visualizer Refinements*) refined the visualizer with 3-lap truncation, descriptive/custom lap names, explicit pace units, and fastest-lap rabbit icon alignment.

However, in the Workout Summary card (`WorkoutSummary.kt` and `WorkoutLaps.kt`), both the newly introduced `LapSplitVisualizer` and the classic tabular `LapTableHeader`/`LapRow` view currently render unconditionally stacked one after the other whenever laps exist. 

Showing both representations simultaneously exhibits several ergonomic and UI deficiencies:
1. **Screen Clutter & Redundant Data Presentation**:
   Both components display the same underlying lap metrics (lap index/name, distance, time, and pace/speed), forcing athletes to scroll past duplicate information.
2. **Conflicting Athlete Preferences**:
   Athletes have divergent analytical workflows:
   - Analytical/Traditional athletes prefer the tabular numeric layout (`LapTableHeader` + `LapRow`) with precise columns for distance, elapsed time, average speed/pace, and split description editing.
   - Modern visual athletes prefer the graphical split visualizer (`LapSplitVisualizer`) featuring proportional horizontal pace bars, tonal color grading, and rabbit badge highlighting.
   - Comprehensive reviewers may still desire both views side-by-side.
3. **Lack of Configuration in Advanced Settings**:
   While `WorkoutCardSectionPreferences` (introduced in ATT-1714 / REQ-UI-210 and organized into the Advanced Settings accordion in ATT-1821 / REQ-UI-222) allows toggling the entire Laps section on or off (`showLaps`), it provides no granularity over the representation mode.

---

## 2. Forensic Investigation of Affected Subsystems

### 2.1 Display Component Architecture (`WorkoutLaps.kt`)
In `WorkoutLaps.kt`:
```kotlin
@Composable
fun WorkoutLaps(
    laps: List<LapData>,
    bSportType: BSportType = BSportType.UNKNOWN,
    modifier: Modifier = Modifier,
    onLapClick: ((LapData) -> Unit)? = null
) {
    ...
    // High-Aesthetic Lap & Interval Split Visualizer
    if (splitChartData != null) {
        LapSplitVisualizer(...)
    }

    // Table Header
    LapTableHeader(...)

    // Split Table Rows
    Column(...) {
        displayedLaps.forEachIndexed { ... }
    }
}
```
Currently, `WorkoutLaps` unconditionally renders both the `LapSplitVisualizer` and the table rows without accepting any display mode parameter.

### 2.2 Preferences & DataStore Architecture (`MyPreferenceManager.kt`)
`MyPreferenceManager.kt` defines `WorkoutCardSectionPreferences`:
```kotlin
data class WorkoutCardSectionPreferences(
    val showDescription: Boolean = true,
    val showExtrema: Boolean = true,
    val showLaps: Boolean = true,
    val showStrava: Boolean = true,
    val showMapPreview: Boolean = true,
    val showElevationProfile: Boolean = true,
    val showTelemetryCharts: Boolean = false,
    val showZoneAnalysis: Boolean = false
)
```
- Stored in DataStore (`user_preferences`) and exposed reactively via `workoutCardPreferencesFlow`.
- Collected in `WorkoutSummariesViewModel` and passed down through `WorkoutTabsScreen` -> `WorkoutList` -> `WorkoutSummary`.
- In `WorkoutSummary.kt`:
  ```kotlin
  if (preferences.showLaps && workoutData.laps.isNotEmpty()) {
      WorkoutLaps(
          laps = workoutData.laps,
          bSportType = workoutData.bSportType,
          onLapClick = { lap -> activeEditingLap = lap }
      )
  }
  ```
  `WorkoutCardSectionPreferences` is already directly available at the callsite.

### 2.3 Settings UI Architecture (`AdvancedTuningDialog.kt` & `AdvancedTuningAccordion.kt`)
`AdvancedTuningDialog.kt` manages user configuration across 5 semantic accordion subsections (`TuningSection`):
- `COCKPIT_TYPOGRAPHY`
- `BATTERY_SAVER`
- `SENSORS_GPS`
- `AFTERMATH_ANALYSIS`
- `WORKOUT_MASKS_CARDS`

In `WorkoutMasksAndCardsSection`:
```kotlin
TuningToggleItem(
    title = stringResource(R.string.settings_workout_card_laps),
    isChecked = workoutCardPrefs.showLaps,
    onCheckedChange = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(showLaps = it)) }
)
```
When `workoutCardPrefs.showLaps` is enabled, the UI currently has no sub-setting for lap representation mode.
Adding a dedicated Lap Display Mode selector (`LapDisplayMode`: `TABLE_ONLY`, `VISUALIZER_ONLY`, `BOTH`) directly under this toggle provides immediate contextual control.

---

## 3. Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   Net-new requirement `REQ-UI-229`, refining and extending `REQ-UI-210` (*Workout Card Section Preferences*), `REQ-UI-216` (*Relocation to Advanced Settings*), and `REQ-UI-204` (*Lap Split Visualizer*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. **Historical Origin & Commit Trace**:
   - `56375eeb` (`ATT-1714` / `REQ-UI-210`): Introduced `WorkoutCardSectionPreferences` in DataStore.
   - `5c0a2878` (`ATT-1815` / `REQ-UI-216`): Relocated card section toggles into Advanced Settings.
   - `0fdd3e4e` (`ATT-1821` / `REQ-UI-222`): Structured Advanced Settings into 5 collapsible accordion subsections (`WORKOUT_MASKS_CARDS`).
   - `5307fabe` (`ATT-1869` / `REQ-UI-228`): Refined `LapSplitVisualizer` with 3-lap truncation and custom names.
3. **Root Reason for Existing Formulation**:
   `LapSplitVisualizer` was initially introduced alongside the table in `WorkoutLaps` so both components could be tested during development without breaking existing tabular lap editing. In production on physical devices, displaying both representations creates redundant vertical height and cognitive overload.
4. **Preservation of Core Invariants**:
   - Tabular lap editing via `LapEditBottomSheet` remains 100% functional regardless of the active display mode (tapping either a table row or a visualizer split opens the edit sheet).
   - Default setting for fresh installs and existing users is `BOTH`, ensuring zero visual regressions or unexpected disappearing sections.
   - Map route polyline highlighting in `TrackOnMapScreen.kt` remains completely decoupled and unaffected.
   - Full 9-language localization parity across all supported languages.

---

## 4. Proposed Architectural Solution

### 4.1 Domain Model Definition (`LapDisplayMode.kt`)
Define enum `LapDisplayMode`:
```kotlin
enum class LapDisplayMode {
    TABLE_ONLY,
    VISUALIZER_ONLY,
    BOTH
}
```

### 4.2 DataStore & Persistence Extension (`MyPreferenceManager.kt`)
1. Extend `WorkoutCardSectionPreferences`:
   ```kotlin
   data class WorkoutCardSectionPreferences(
       ...
       val lapDisplayMode: LapDisplayMode = LapDisplayMode.BOTH
   )
   ```
2. Add DataStore key `WORKOUT_CARD_LAP_DISPLAY_MODE = stringPreferencesKey("workout_card_lap_display_mode")`.
3. Map value safely with fallback to `LapDisplayMode.BOTH` on empty or invalid stored strings.
4. Ensure `resetToDefaults()` in `AdvancedTuningDialog` restores `LapDisplayMode.BOTH`.

### 4.3 Composable Conditional Rendering (`WorkoutLaps.kt`)
Update `WorkoutLaps` signature:
```kotlin
@Composable
fun WorkoutLaps(
    laps: List<LapData>,
    bSportType: BSportType = BSportType.UNKNOWN,
    lapDisplayMode: LapDisplayMode = LapDisplayMode.BOTH,
    modifier: Modifier = Modifier,
    onLapClick: ((LapData) -> Unit)? = null
)
```
- If `lapDisplayMode == BOTH || lapDisplayMode == VISUALIZER_ONLY`: Render `LapSplitVisualizer`.
- If `lapDisplayMode == BOTH || lapDisplayMode == TABLE_ONLY`: Render `LapTableHeader`, `LapRow` list, and table expand/collapse button.
- Tapping a split in `LapSplitVisualizer` invokes `onLapClick`, preserving lap editing even in `VISUALIZER_ONLY` mode.

### 4.4 Advanced Settings UI Integration (`AdvancedTuningDialog.kt`)
In `WorkoutMasksAndCardsSection`, when `workoutCardPrefs.showLaps == true`:
Render an indented selector row featuring 3 Material 3 `FilterChip` items:
- `Table` (`R.string.settings_lap_display_mode_table`)
- `Visualizer` (`R.string.settings_lap_display_mode_visualizer`)
- `Both` (`R.string.settings_lap_display_mode_both`)

### 4.5 9-Language Localization Parity
Declare 4 new string tokens across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
- `settings_lap_display_mode_title`
- `settings_lap_display_mode_table`
- `settings_lap_display_mode_visualizer`
- `settings_lap_display_mode_both`

---

## 5. Scope Bounding (`ATT-1250`)

### In-Scope
1. Enum `LapDisplayMode` definition.
2. DataStore persistence and serialization in `MyPreferenceManager.kt`.
3. FilterChip selector integration in `WorkoutMasksAndCardsSection` inside `AdvancedTuningDialog.kt`.
4. Conditional rendering in `WorkoutLaps.kt` and propagation from `WorkoutSummary.kt`.
5. 100% 9-language translation parity for display mode strings.
6. Comprehensive unit and contract tests verifying persistence, default fallback, and conditional rendering.

### Out-of-Scope
1. Altering `TrackOnMapScreen.kt` layout (it exclusively renders `LapSplitVisualizerCard` for GPS route synchrony).
2. Changing the database schema (`lapsTable` or `LapData`).
3. Modifying `LapSplitVisualizer.kt` internal rendering or truncation logic.

---

## 6. Risk Analysis & Mitigation Strategies

| Risk | Impact | Probability | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Existing user visual disruption** | Medium | Low | Default to `LapDisplayMode.BOTH`, preserving exact visual output until the user explicitly changes the setting. |
| **Loss of lap editing in Visualizer-only mode** | High | Low | Wire `onLapClick` on `LapSplitVisualizer` to pass `LapData` to `LapEditBottomSheet`, ensuring editing works identically across all modes. |
| **Corrupted DataStore string value** | Low | Low | Wrap deserialization in defensive `try/catch` with fallback to `LapDisplayMode.BOTH`. |
| **UI overflow in Advanced Settings** | Low | Low | Use standard `FilterChip` with `Modifier.weight(1f)` within a horizontal row matching existing UI patterns. |
