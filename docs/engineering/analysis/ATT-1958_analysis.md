# Stage 1 Analysis: ATT-1958 - [Bug] [Settings/Aftermath] Fix Lap Display Mode Selection Persistence in Expert Settings and Change Default to Visualizer Only

**Ticket**: [ATT-1958](https://rainerblind.atlassian.net/browse/ATT-1958)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.9`  
**Branch**: `feature/ATT-1958`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

During Sprint Review of ATT-1870 (`REQ-UI-229`), two distinct defects were identified regarding the Lap Display Mode selection in Advanced / Expert Settings (`AdvancedTuningDialog.kt`) and `MyPreferenceManager.kt`:

1. **State Desynchronization in Dialog (Persistence Read Defect)**:
   - When an athlete opens the Advanced Settings dialog, navigates to *Workout-Karten & Eingabemasken*, selects a different `lapDisplayMode` FilterChip (e.g. `Visualizer` or `Table`), and taps *Save*, the preference is saved to disk via DataStore (`preferenceManager.setWorkoutCardPreferences(...)`).
   - The selected mode correctly affects the workout summary in the main UI upon dismissing the dialog.
   - However, when reopening `AdvancedTuningDialog`, the chip selector fails to display the saved preference, erroneously reverting to the fallback state (`BOTH`).

2. **Default Presentation Mode (UX Redundancy)**:
   - Previously, `lapDisplayMode` defaulted to `LapDisplayMode.BOTH` for backward compatibility.
   - However, displaying both the classic `WorkoutLaps` table and the modern `LapSplitVisualizer` stacked simultaneously produces unnecessary visual clutter and redundant split metrics out-of-the-box.
   - As requested, the default mode out-of-the-box for fresh installations and factory resets must be changed to `LapDisplayMode.VISUALIZER_ONLY` (Split Visualizer only). Athletes desiring tabular representation or dual views can opt into them via Advanced Settings.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Bug 1: Flow Initialization Race Condition in `AdvancedTuningDialog.kt`
Forensic analysis of `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt` lines 78–118 reveals the exact asynchronous race condition:

```kotlin
// Line 78:
val persistedWorkoutCardPrefs by preferenceManager.workoutCardPreferencesFlow.collectAsState(initial = WorkoutCardSectionPreferences())
val persistedEditWorkoutPrefs by preferenceManager.editWorkoutFieldPreferencesFlow.collectAsState(initial = EditWorkoutFieldPreferences())

// Lines 94-96:
var workoutCardPrefs by remember { mutableStateOf(WorkoutCardSectionPreferences()) }
var editWorkoutPrefs by remember { mutableStateOf(EditWorkoutFieldPreferences()) }
var isAftermathPrefsInitialized by remember { mutableStateOf(false) }

// Lines 112-118:
LaunchedEffect(persistedWorkoutCardPrefs, persistedEditWorkoutPrefs) {
    if (!isAftermathPrefsInitialized) {
        workoutCardPrefs = persistedWorkoutCardPrefs
        editWorkoutPrefs = persistedEditWorkoutPrefs
        isAftermathPrefsInitialized = true
    }
}
```

**Failure Sequence**:
1. On the very first composition pass (frame 0), `preferenceManager.workoutCardPreferencesFlow.collectAsState(initial = WorkoutCardSectionPreferences())` immediately returns the dummy fallback instance `WorkoutCardSectionPreferences()` before the asynchronous DataStore disk I/O completes.
2. `LaunchedEffect` executes immediately on frame 0. Since `!isAftermathPrefsInitialized` evaluates to `true`, it copies the initial fallback dummy preferences into `workoutCardPrefs` and sets `isAftermathPrefsInitialized = true`.
3. Shortly thereafter (frame 1 / disk callback), DataStore finishes reading the actual persisted preferences from disk and emits the true saved object (e.g. containing `lapDisplayMode = VISUALIZER_ONLY`).
4. Recomposition is triggered, and `LaunchedEffect(persistedWorkoutCardPrefs, persistedEditWorkoutPrefs)` executes again.
5. However, `isAftermathPrefsInitialized` is **already `true`**! The guard `if (!isAftermathPrefsInitialized)` evaluates to `false`, causing the actual persisted preferences to be silently discarded. The dialog remains stuck displaying the default dummy state.

### Bug 2: Outdated Default Constant in `MyPreferenceManager.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt`:
- Line 55: `val lapDisplayMode: LapDisplayMode = LapDisplayMode.BOTH` specifies `BOTH` as the default field value for `WorkoutCardSectionPreferences`.
- Lines 114, 116: The fallback expression for uninitialized or corrupted DataStore values defaults to `LapDisplayMode.BOTH`.
- To establish `VISUALIZER_ONLY` as the default experience out-of-the-box, both the data class default and the fallback logic in `workoutCardPreferencesFlow` must be updated to `LapDisplayMode.VISUALIZER_ONLY`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Fix the state synchronization in `AdvancedTuningDialog.kt`:
     - Collect `workoutCardPreferencesFlow` and `editWorkoutFieldPreferencesFlow` with `initial = null`.
     - Gate initialization on non-null emissions (`persistedWorkoutCardPrefs != null && persistedEditWorkoutPrefs != null`), ensuring that the real persisted values from DataStore are applied to `workoutCardPrefs` and `editWorkoutPrefs`.
  2. Update the default `lapDisplayMode` in `MyPreferenceManager.kt` to `LapDisplayMode.VISUALIZER_ONLY`:
     - In `data class WorkoutCardSectionPreferences`.
     - In `workoutCardPreferencesFlow` fallback handling.
  3. Update existing unit tests (`LapDisplayModePreferencesTest.kt` and `LapDisplayModeSettingsTest.kt`) to verify the new default value `LapDisplayMode.VISUALIZER_ONLY`.
  4. Add unit test verifying that `AdvancedTuningDialog` initializes and persists `lapDisplayMode` accurately without losing DataStore state.

* **Explicitly Out-of-Scope (To Prevent Scope Creep)**:
  - Modifying `LapDisplayMode.kt` enum values or adding new modes.
  - Changing the layout or logic of `LapSplitVisualizer.kt` or `WorkoutLaps.kt`.
  - Modifying any SQLite schemas or room database tables.
  - Modifying other sections of `AdvancedTuningDialog.kt` (typography, battery saver, GPS/sensors).

---

## 4. Chesterton's Fence Requirement Archaeology (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-229` (*Aftermath/Settings: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings*), under Epic `ATT-111` (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `7f747b02`, ATT-1870 / ATT-1869).
3. *Root Reason for Existing Formulation*: In ATT-1870, `LapDisplayMode.BOTH` was originally chosen as default to maintain strict visual backward compatibility with the dual presentation implemented earlier in ATT-1815. However, user feedback during sprint review revealed that stacked dual representations are redundant, and the modern `LapSplitVisualizer` is the preferred primary view. Furthermore, the `isAftermathPrefsInitialized` pattern in `AdvancedTuningDialog.kt` was intended to avoid overwriting user in-dialog edits if background flows emitted, but using a non-null dummy initial object caused a race condition that dropped the first real disk emission.
4. *Preservation of Core Invariants*: Interactive lap editing via `LapEditBottomSheet` across all modes, SQLite single-thread confinement, 9-language localization parity, and independent user selection between `TABLE_ONLY`, `VISUALIZER_ONLY`, and `BOTH` remain 100% strictly preserved.

---

## 5. Proposed Architectural Design & Solution

1. **Reactive State Initialization in `AdvancedTuningDialog.kt`**:
   ```kotlin
   val persistedWorkoutCardPrefs by preferenceManager.workoutCardPreferencesFlow.collectAsState(initial = null)
   val persistedEditWorkoutPrefs by preferenceManager.editWorkoutFieldPreferencesFlow.collectAsState(initial = null)

   var workoutCardPrefs by remember { mutableStateOf(WorkoutCardSectionPreferences()) }
   var editWorkoutPrefs by remember { mutableStateOf(EditWorkoutFieldPreferences()) }
   var isAftermathPrefsInitialized by remember { mutableStateOf(false) }

   LaunchedEffect(persistedWorkoutCardPrefs, persistedEditWorkoutPrefs) {
       val cardPrefs = persistedWorkoutCardPrefs
       val editPrefs = persistedEditWorkoutPrefs
       if (!isAftermathPrefsInitialized && cardPrefs != null && editPrefs != null) {
           workoutCardPrefs = cardPrefs
           editWorkoutPrefs = editPrefs
           isAftermathPrefsInitialized = true
       }
   }
   ```
   By using `initial = null`, `LaunchedEffect` only initializes `workoutCardPrefs` once the asynchronous DataStore flow produces the genuine persisted values from disk, eliminating the race condition.

2. **Default Update in `MyPreferenceManager.kt`**:
   ```kotlin
   data class WorkoutCardSectionPreferences(
       ...
       val lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY
   )
   ...
   lapDisplayMode = try {
       val rawMode = preferences[WORKOUT_CARD_LAP_DISPLAY_MODE]
       if (rawMode != null) LapDisplayMode.valueOf(rawMode) else LapDisplayMode.VISUALIZER_ONLY
   } catch (e: Exception) {
       LapDisplayMode.VISUALIZER_ONLY
   }
   ```

3. **Automated Verification Strategy**:
   - Update `LapDisplayModePreferencesTest.kt`: assert `LapDisplayMode.VISUALIZER_ONLY` on default and fallback scenarios.
   - Update `LapDisplayModeSettingsTest.kt`: assert default and factory reset restore `LapDisplayMode.VISUALIZER_ONLY`.
   - Run targeted unit tests:
     ```bash
     ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.LapDisplayMode*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
     ```
   - Execute full clean-room regression suite in Stage 5.

---

## 6. Deliverable Sign-Off Criteria (Gate 1 Checklist)
- [x] Forensic root cause analysis accurately identifies both the flow initialization race condition and the default value discrepancy.
- [x] Chesterton's Fence Requirement Archaeology completed with all 4 mandatory fields.
- [x] Out-of-scope boundaries clearly defined.
- [x] Proposed solution preserves 100% backward compatibility and test stability.
