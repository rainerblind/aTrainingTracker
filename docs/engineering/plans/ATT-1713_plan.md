# Stage 3 Implementation Plan: ATT-1713

## 1. Ticket & Metadata
- **Parent Ticket**: [ATT-1713](https://atrainingtracker.atlassian.net/browse/ATT-1713) - `[Feature] [Aftermath/EditWorkout] Configurable Fields in Edit Workout Dialog`
- **Subtask**: [ATT-1807](https://atrainingtracker.atlassian.net/browse/ATT-1807) - `Stage 3: Implementation Plan`
- **Target Version**: `V4.9.38`
- **Target Branch**: `feature/ATT-1713`
- **Author**: AI Agent 1 (Implementer)
- **Date**: 2026-10-01

---

## 2. Architectural Overview (SWE.2)

```
                       DisplaySettingsDialog
                                │ (User configures 6 field toggles)
                                ▼
                       MyPreferenceManager (DataStore)
                     [EditWorkoutFieldPreferences]
                                │
                                ▼
                      EditWorkoutViewModel
                                │
                                ▼
                      EditWorkoutScreen
        ┌─────────────────────────────────────────────────┐
        │ 1. Workout Name (ALWAYS MANDATORY)              │
        │ 2. Route / Cluster (if showCluster)              │
        │ 3. Sport & Equipment (ALWAYS MANDATORY)         │
        │ 4. Commute & Trainer (if showCommuteTrainer)    │
        │ 5. Strava Upload (if showStravaUpload)          │
        │ 6. Description (if showDescription)            │
        │ 7. Goal (if showGoal)                           │
        │ 8. Method (if showMethod)                       │
        │ [Cancel] [Save]                                 │
        └─────────────────────────────────────────────────┘
```

The architecture provides complete customization of optional metadata fields while guaranteeing data preservation of unedited hidden values and ensuring core fields remain permanently anchored.

---

## 3. Atomic Implementation Steps

### Step 1: Preferences Data Model & DataStore Persistence
- **Target File**: [MyPreferenceManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt)
- **Modifications**:
  1. Define `EditWorkoutFieldPreferences` data class:
     ```kotlin
     data class EditWorkoutFieldPreferences(
         val showCluster: Boolean = true,
         val showCommuteTrainer: Boolean = true,
         val showStravaUpload: Boolean = true,
         val showDescription: Boolean = true,
         val showGoal: Boolean = true,
         val showMethod: Boolean = true
     )
     ```
  2. Define preference keys:
     - `EDIT_WORKOUT_SHOW_CLUSTER`
     - `EDIT_WORKOUT_SHOW_COMMUTE_TRAINER`
     - `EDIT_WORKOUT_SHOW_STRAVA_UPLOAD`
     - `EDIT_WORKOUT_SHOW_DESCRIPTION`
     - `EDIT_WORKOUT_SHOW_GOAL`
     - `EDIT_WORKOUT_SHOW_METHOD`
  3. Expose `val editWorkoutFieldPreferencesFlow: Flow<EditWorkoutFieldPreferences>`.
  4. Implement `suspend fun setEditWorkoutFieldPreferences(prefs: EditWorkoutFieldPreferences)`.

### Step 2: 9-Language Localization Definitions
- **Target Files**: `app/src/main/res/values*/strings.xml` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
- **Resource Keys**:
  - `settings_edit_workout_title`: "Training bearbeiten" / "Edit Workout"
  - `settings_edit_workout_cluster`: "Strecke / Route" / "Route / Cluster"
  - `settings_edit_workout_commute_trainer`: "Pendeln & Trainer" / "Commute & Trainer"
  - `settings_edit_workout_strava`: "Strava-Upload" / "Strava Upload"
  - `settings_edit_workout_description`: "Beschreibung & Notizen" / "Description & Notes"
  - `settings_edit_workout_goal`: "Ziel" / "Goal"
  - `settings_edit_workout_method`: "Methode" / "Method"

### Step 3: Settings UI Integration in `DisplaySettingsDialog.kt`
- **Target File**: [DisplaySettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt)
- **Modifications**:
  - Add state `currentEditWorkoutPrefs` buffered locally in the dialog.
  - Render configuration section with 6 `DisplayOptionToggle` switches under `settings_edit_workout_title`.
  - Persist updated `currentEditWorkoutPrefs` on save.

### Step 4: ViewModel Wiring in `EditWorkoutViewModel.kt`
- **Target File**: [EditWorkoutViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt)
- **Modifications**:
  - Expose `fieldPreferences: StateFlow<EditWorkoutFieldPreferences>` mapped from `MyPreferenceManager`.

### Step 5: Conditional Field Layout in `EditWorkoutScreen.kt`
- **Target File**: [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt)
- **Modifications**:
  - Collect `fieldPreferences` from `viewModel`.
  - Condition:
    - Route / Cluster selection on `fieldPreferences.showCluster`.
    - Commute / Trainer checkboxes on `fieldPreferences.showCommuteTrainer`.
    - Strava upload checkbox on `fieldPreferences.showStravaUpload`.
    - Description OutlinedTextField on `fieldPreferences.showDescription`.
    - Goal OutlinedTextField on `fieldPreferences.showGoal`.
    - Method OutlinedTextField on `fieldPreferences.showMethod`.
  - Maintain Workout Name, Sport, Equipment as mandatory core fields.

### Step 6: Unit Testing & Localization Verification
- **Target Test Files**:
  - `EditWorkoutFieldPreferencesTest.kt`: Tests defaults, copy immutability, and full-fidelity custom values.
  - `EditWorkoutFieldsLayoutTest.kt`: Tests structural contract, conditional field rendering, and mandatory core anchors.
  - `EditWorkoutDataPreservationTest.kt`: Verifies that hidden fields preserve existing database values on save.
  - `EditWorkoutSettingsLocalizationTest.kt`: Verifies all 7 tokens across all 9 locales.
- **Verification Execution**:
  - Run targeted unit tests:
    ```bash
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.display.EditWorkoutSettingsLocalizationTest"
    ```
  - Run full clean-room suite:
    ```bash
    ./gradlew testDebugUnitTest
    ```

---

## 4. Invariant Protection & Scope Bounding
1. **Core Anchors Invariant**: Workout Name, Sport Type, and Equipment dropdowns must never be hidden or configurable.
2. **Data Preservation Invariant**: Hidden fields must not have their existing database values erased or corrupted when saving.
3. **Card Visibility Untouched**: Detailed card sections in `WorkoutSummary.kt` remain isolated to `REQ-UI-210` (`ATT-1714`).
4. **9-Language Translation Parity**: 100% complete across all 9 locales with 0 missing translations.
