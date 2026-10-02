# Stage 3: Implementation Plan - ATT-1870: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings

**Ticket**: [ATT-1870](https://rainerblind.atlassian.net/browse/ATT-1870)  
**Sub-task**: [ATT-1939](https://rainerblind.atlassian.net/browse/ATT-1939) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-229`, `REQ-UI-210`, `REQ-UI-204`  
**Test Mapping**: `TST-UI-183`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Architectural Overview & Component Decomposition (SWE.2)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        AdvancedTuningDialog                            │
│  • TuningSection.WORKOUT_MASKS_CARDS (WorkoutMasksAndCardsSection)     │
│    ├── TuningToggleItem: Rundenübersicht (showLaps: Boolean)           │
│    └── Animated/Indented Selector: Runden-Anzeigemodus                 │
│        [ Tabelle ]        [ Visualizer ]        [ Beide ]              │
│        (TABLE_ONLY)      (VISUALIZER_ONLY)        (BOTH)               │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ DataStore (user_preferences)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     MyPreferenceManager.kt                             │
│  • WorkoutCardSectionPreferences(..., lapDisplayMode = BOTH)           │
│  • WORKOUT_CARD_LAP_DISPLAY_MODE: Preferences.Key<String>              │
│  • Defensive deserialization with fallback to BOTH                     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ StateFlow<WorkoutCardSectionPreferences>
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       WorkoutSummary.kt                                │
│  • preferences.showLaps == true                                        │
│  • passes lapDisplayMode = preferences.lapDisplayMode to WorkoutLaps   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         WorkoutLaps.kt                                 │
│  • lapDisplayMode == BOTH:                                             │
│    ├── LapSplitVisualizer (Proportional Tonal Bars)                   │
│    └── LapTableHeader + LapRows + Table Expansion Button              │
│  • lapDisplayMode == VISUALIZER_ONLY:                                  │
│    └── LapSplitVisualizer ONLY (onLapClick -> LapEditBottomSheet)      │
│  • lapDisplayMode == TABLE_ONLY:                                       │
│    └── LapTableHeader + LapRows + Table Expansion Button ONLY         │
└────────────────────────────────────────────────────────────────────────┘
```

### Component Roles & Boundaries
1. **`LapDisplayMode.kt` (`ui/components/workoutlaps/`)**:
   - Pure domain enum (`TABLE_ONLY`, `VISUALIZER_ONLY`, `BOTH`).
2. **`MyPreferenceManager.kt`**:
   - Stores `lapDisplayMode` in `WorkoutCardSectionPreferences` backed by DataStore key `workout_card_lap_display_mode`.
   - Ensures fallback to `BOTH` if null, empty, or unparseable.
3. **`AdvancedTuningDialog.kt` (`ui/settings/tuning/`)**:
   - Displays 3-way `FilterChip` selector in `WorkoutMasksAndCardsSection` when `showLaps == true`.
   - Restores `LapDisplayMode.BOTH` upon "Werkseinstellungen" reset.
4. **`WorkoutLaps.kt` (`ui/components/workoutlaps/`)**:
   - Accepts `lapDisplayMode: LapDisplayMode = LapDisplayMode.BOTH`.
   - Conditionally renders `LapSplitVisualizer` and/or `LapTableHeader` + rows.
   - Preserves lap editing across all modes.
5. **`WorkoutSummary.kt` (`ui/aftermath/workoutlist/`)**:
   - Forwards `preferences.lapDisplayMode` into `WorkoutLaps`.

---

## 2. Atomic Implementation Step Sequence

### Step 1: Domain Model Definition (`LapDisplayMode.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapDisplayMode.kt`
* **Changes**:
  - Define `enum class LapDisplayMode { TABLE_ONLY, VISUALIZER_ONLY, BOTH }`.

### Step 2: DataStore Persistence Extension (`MyPreferenceManager.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt`
* **Changes**:
  - Extend `WorkoutCardSectionPreferences`:
    ```kotlin
    data class WorkoutCardSectionPreferences(
        val showDescription: Boolean = true,
        val showExtrema: Boolean = true,
        val showLaps: Boolean = true,
        val showStrava: Boolean = true,
        val showMapPreview: Boolean = true,
        val showElevationProfile: Boolean = true,
        val showTelemetryCharts: Boolean = false,
        val showZoneAnalysis: Boolean = false,
        val lapDisplayMode: LapDisplayMode = LapDisplayMode.BOTH
    )
    ```
  - In `companion object`:
    ```kotlin
    val WORKOUT_CARD_LAP_DISPLAY_MODE = stringPreferencesKey("workout_card_lap_display_mode")
    ```
  - In `workoutCardPreferencesFlow`:
    ```kotlin
    lapDisplayMode = try {
        val rawMode = preferences[WORKOUT_CARD_LAP_DISPLAY_MODE]
        if (rawMode != null) LapDisplayMode.valueOf(rawMode) else LapDisplayMode.BOTH
    } catch (e: Exception) {
        LapDisplayMode.BOTH
    }
    ```
  - In `setWorkoutCardPreferences`:
    ```kotlin
    preferences[WORKOUT_CARD_LAP_DISPLAY_MODE] = prefs.lapDisplayMode.name
    ```

### Step 3: 9-Language Localization String Tokens
* **Target Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **String Tokens**:
  - `settings_lap_display_mode_title` ("Lap Display Mode" / "Runden-Anzeigemodus")
  - `settings_lap_display_mode_table` ("Table" / "Tabelle")
  - `settings_lap_display_mode_visualizer` ("Visualizer" / "Visualizer")
  - `settings_lap_display_mode_both` ("Both" / "Beide")

### Step 4: Settings UI Selector in `AdvancedTuningDialog.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* **Changes**:
  - In `WorkoutMasksAndCardsSection`, immediately following the `showLaps` toggle:
    ```kotlin
    if (workoutCardPrefs.showLaps) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_lap_display_mode_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.TABLE_ONLY,
                    onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.TABLE_ONLY)) },
                    label = { Text(stringResource(R.string.settings_lap_display_mode_table)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.VISUALIZER_ONLY,
                    onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY)) },
                    label = { Text(stringResource(R.string.settings_lap_display_mode_visualizer)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = workoutCardPrefs.lapDisplayMode == LapDisplayMode.BOTH,
                    onClick = { onWorkoutCardPrefsChange(workoutCardPrefs.copy(lapDisplayMode = LapDisplayMode.BOTH)) },
                    label = { Text(stringResource(R.string.settings_lap_display_mode_both)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    ```

### Step 5: Conditional Rendering in `WorkoutLaps.kt` & Integration in `WorkoutSummary.kt`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
* **Changes**:
  - In `WorkoutLaps.kt`:
    - Add parameter: `lapDisplayMode: LapDisplayMode = LapDisplayMode.BOTH`.
    - Guard `LapSplitVisualizer` with `if (splitChartData != null && (lapDisplayMode == LapDisplayMode.BOTH || lapDisplayMode == LapDisplayMode.VISUALIZER_ONLY))`.
    - Guard `LapTableHeader` and `displayedLaps` rows and table toggle button with `if (lapDisplayMode == LapDisplayMode.BOTH || lapDisplayMode == LapDisplayMode.TABLE_ONLY)`.
  - In `WorkoutSummary.kt`:
    - Pass `lapDisplayMode = preferences.lapDisplayMode` into `WorkoutLaps`.

### Step 6: Automated Test Construction & Validation
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/LapDisplayModePreferencesTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLapsDisplayModeTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/LapDisplayModeSettingsTest.kt`
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.settings.LapDisplayModePreferencesTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.components.workoutlaps.WorkoutLapsDisplayModeTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.LapDisplayModeSettingsTest"
  ```

---

## 3. Invariants & Risk Mitigation

1. **Zero Visual Regressions**: Default mode remains `LapDisplayMode.BOTH`; existing and fresh installs render identical visual representations until athlete chooses otherwise.
2. **Lap Editing Preservation**: Split clicks on `LapSplitVisualizer` dispatch to `onLapClick`, opening `LapEditBottomSheet` seamlessly even when table rows are hidden (`VISUALIZER_ONLY`).
3. **Defensive Serialization**: Unknown or null DataStore string values fall back to `LapDisplayMode.BOTH`.
4. **Decoupled Architecture**: `TrackOnMapScreen.kt` and `LiveSegmentSheet.kt` remain completely decoupled and unaffected.
