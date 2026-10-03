# Stage 3 Implementation Plan: ATT-1714

## 1. Ticket & Metadata
- **Parent Ticket**: [ATT-1714](https://atrainingtracker.atlassian.net/browse/ATT-1714) - `[Feature] [Aftermath/WorkoutSummary] Configurable Sections in Detailed Workout Cards (Charts, Zones, Map, Elevation, Extrema)`
- **Subtask**: [ATT-1802](https://atrainingtracker.atlassian.net/browse/ATT-1802) - `Stage 3: Implementation Plan`
- **Target Version**: `V4.9.38`
- **Target Branch**: `feature/ATT-1714`
- **Author**: AI Agent 1 (Implementer)
- **Date**: 2026-10-01

---

## 2. Architectural Overview (SWE.2)

```
                       DisplaySettingsDialog
                                │ (User edits 8 section toggles)
                                ▼
                       MyPreferenceManager (DataStore)
                     [WorkoutCardSectionPreferences]
                                │
                                ▼
                    WorkoutSummariesViewModel
                                │
                                ▼
                        WorkoutTabsScreen
                                │
                                ▼
                           WorkoutList
                                │
                                ▼
                          WorkoutSummary
        ┌─────────────────────────────────────────────────┐
        │ 1. WorkoutHeader (ALWAYS MANDATORY)             │
        │ 2. WorkoutDescription (if showDescription)       │
        │ 3. WorkoutDetails (ALWAYS MANDATORY)            │
        │ 4. WorkoutExtrema (if showExtrema)               │
        │ 5. WorkoutLaps (if showLaps)                     │
        │ 6. StravaActivitySection (if showStrava)         │
        │ 7. Decoupled Media:                              │
        │    - PathPreviewMap (if showMapPreview)          │
        │    - ElevationProfile (if showElevationProfile)  │
        │ 8. TelemetryMetricGraph (if showTelemetryCharts) │
        │    - Heart Rate, Speed/Pace, Power curves        │
        │ 9. ZoneDistributionCards (if showZoneAnalysis)   │
        │    - HR & Power 5-Zone Column Charts             │
        │ 10. ExportStatus (badges)                        │
        └─────────────────────────────────────────────────┘
```

The design enables complete flexibility for athletes to customize card height and information density. Data for telemetry charts and zone distributions is loaded asynchronously on `Dispatchers.IO` via `produceState` only when the card is composed on screen, ensuring zero memory bloat or scroll stutter.

---

## 3. Atomic Implementation Steps

### Step 1: Preferences Data Model & DataStore Persistence
- **Target File**: [MyPreferenceManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt)
- **Modifications**:
  1. Define `WorkoutCardSectionPreferences` data class:
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
  2. In `MyPreferenceManager`:
     - Define `Preferences.Key` constants:
       `WORKOUT_CARD_SHOW_DESCRIPTION`, `WORKOUT_CARD_SHOW_EXTREMA`, `WORKOUT_CARD_SHOW_LAPS`, `WORKOUT_CARD_SHOW_STRAVA`, `WORKOUT_CARD_SHOW_MAP`, `WORKOUT_CARD_SHOW_ELEVATION`, `WORKOUT_CARD_SHOW_CHARTS`, `WORKOUT_CARD_SHOW_ZONES`.
     - Expose `workoutCardPreferencesFlow: Flow<WorkoutCardSectionPreferences>`.
     - Implement `suspend fun setWorkoutCardPreferences(prefs: WorkoutCardSectionPreferences)`.

### Step 2: 9-Language Localization Definitions
- **Target Files**:
  - `app/src/main/res/values*/strings.xml` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
- **Resource Keys**:
  - `settings_workout_card_title`: Category heading ("Trainingsliste (Detail-Karten)" / "Workout List (Detailed Cards)")
  - `settings_workout_card_map`: "Karten-Vorschau" / "Map Preview"
  - `settings_workout_card_elevation`: "Höhenprofil" / "Elevation Profile"
  - `settings_workout_card_extrema`: "Extremwerte" / "Extrema Values"
  - `settings_workout_card_laps`: "Runden-Übersicht" / "Laps Overview"
  - `settings_workout_card_strava`: "Strava-Aktivitätsdaten" / "Strava Activity"
  - `settings_workout_card_description`: "Beschreibung & Notizen" / "Description & Notes"
  - `settings_workout_card_charts`: "Telemetrie-Diagramme" / "Telemetry Charts"
  - `settings_workout_card_zones`: "Zonenauswertung (HF & Power)" / "Zone Analysis (HR & Power)"

### Step 3: Settings UI Integration in `DisplaySettingsDialog.kt`
- **Target File**: [DisplaySettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt)
- **Modifications**:
  - Instantiate and remember mutable state for `WorkoutCardSectionPreferences`.
  - In `DisplaySettingsDialog`:
    - Add divider and category title: `Text(text = stringResource(R.string.settings_workout_card_title), ...)`.
    - Render 8 `DisplayOptionToggle` switches for each configurable dimension.
    - In `onSave`: persist updated `WorkoutCardSectionPreferences` to `MyPreferenceManager`.

### Step 4: ViewModel Flow & Screen Wiring
- **Target Files**:
  - [WorkoutSummariesViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesViewModel.kt)
  - [WorkoutTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt)
  - [WorkoutList.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt)
- **Modifications**:
  - In `WorkoutSummariesViewModel`:
    ```kotlin
    val workoutCardPreferences: StateFlow<WorkoutCardSectionPreferences> = prefManager.workoutCardPreferencesFlow
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = WorkoutCardSectionPreferences())
    ```
  - Forward `workoutCardPreferences` through `WorkoutTabsScreen` into `WorkoutList` and `WorkoutSummary`.

### Step 5: Decoupled & Conditional Section Rendering in `WorkoutSummary.kt`
- **Target File**: [WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt)
- **Modifications**:
  1. Add parameter `preferences: WorkoutCardSectionPreferences = WorkoutCardSectionPreferences()` to `WorkoutSummary`.
  2. Condition `WorkoutDescription` on `preferences.showDescription`.
  3. Condition `WorkoutExtrema` on `preferences.showExtrema`.
  4. Condition `WorkoutLaps` on `preferences.showLaps`.
  5. Condition `StravaActivitySection` on `preferences.showStrava`.
  6. Decouple `WorkoutMediaSection`:
     - If both `preferences.showMapPreview` and `preferences.showElevationProfile` are true: render combined map + elevation layout (300.dp).
     - If only `preferences.showMapPreview`: render `PathPreviewMap` independently (200.dp).
     - If only `preferences.showElevationProfile`: render `ElevationProfile` independently (100.dp).
     - If neither: omit media area.
  7. When `preferences.showTelemetryCharts`:
     - Lazily load `points` for `workoutData.id` on `Dispatchers.IO` using `produceState`.
     - Render `TelemetryMetricGraph` for Heart Rate, Speed/Pace, and Power when sensor samples exist.
  8. When `preferences.showZoneAnalysis`:
     - Lazily load `hrDistribution` and `powerDistribution` on `Dispatchers.IO` using `produceState`.
     - Render `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` when zone data exists.

### Step 6: Unit Testing & Localization Verification
- **Target Test Files**:
  - `WorkoutCardSectionPreferencesTest.kt`: Verifies default values, DataStore reading/writing, and JSON serialization.
  - `WorkoutSummarySectionsTest.kt`: Structural contract test verifying conditional rendering, decoupling of map/elevation, and invariant preservation.
  - `WorkoutCardSettingsLocalizationTest.kt`: Verifies presence and completeness of all 9 string tokens across all 9 localized resource files.
- **Verification Execution**:
  - Run targeted unit tests:
    ```bash
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutCardSectionPreferencesTest"
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummarySectionsTest"
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.display.WorkoutCardSettingsLocalizationTest"
    ```
  - Run clean-room regression test suite:
    ```bash
    ./gradlew testDebugUnitTest
    ```

---

## 4. Invariant Protection & Scope Bounding

1. **Mandatory Anchors Protected**: `WorkoutHeader` and `WorkoutDetails` are non-configurable and always rendered on every card.
2. **Compact View Untouched**: `WorkoutSummaryCompact.kt` remains unmodified.
3. **Edit Workout Dialog Untouched**: Configurable fields in edit workout dialog remain isolated to companion ticket [ATT-1713](https://atrainingtracker.atlassian.net/browse/ATT-1713).
4. **Scroll Performance Protected**: All track telemetry and zone calculations execute asynchronously on `Dispatchers.IO` and are automatically cancelled when cards scroll off-screen.
5. **9-Language Translation Parity**: 100% complete across all 9 locales with 0 missing translations.
