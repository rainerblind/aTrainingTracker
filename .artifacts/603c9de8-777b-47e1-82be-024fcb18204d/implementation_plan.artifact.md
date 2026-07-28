# Implementation Plan - ATT-447: Reactive Cluster Suggestions with Sport-Type Penalty

Ensure that sport-specific similarity penalties are accurately reflected in the cluster suggestions dialog of the Edit Workout screen. This will be achieved by making the suggestion engine reactive to changes in both the workout's Sport Type and Name within the editor.

## User Review Required

> [!IMPORTANT]
> - **Real-Time Accuracy**: Cluster similarity scores will now update dynamically as you change the sport type or edit the workout name.
> - **Performance & Debouncing**: To maintain UI responsiveness, name-based updates will be debounced by 500ms. This prevents the system from recalculating similarities for every single keystroke.
> - **Sport-First Logic**: Changing the sport type will immediately trigger a recalculation (after the debounce) to ensure that the +2.0 or +5.0 penalties are correctly visualized in the candidate list.

## Proposed Changes

### 1. ViewModel Logic: Reactive Suggestions
Fulfills REQ-SET-018 | Test: TST-SET-046

#### [MODIFY] [EditWorkoutViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt)
- **Implement Suggestion Observer**:
    - Add a `viewModelScope` launch block in `init` that observes `workoutData`.
    - Filter for changes in `workoutName` or `bSportType` using `distinctUntilChanged`.
    - Apply `debounce(500)` to the flow.
    - Call `fetchClusterSuggestions` whenever the filtered flow emits.
- **Cleanup**:
    - Remove the manual call to `fetchClusterSuggestions(data)` inside `loadWorkoutData` to avoid redundant initial calculations (the flow will handle the first emission).

## Verification Plan

### Automated Tests
#### [NEW] [TST-SET-046](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)
- **Procedure**:
    1. Open the Edit Workout screen for a 'Running' workout on a path that matches a 'Cycling' cluster.
    2. Open the Cluster Selection dialog and observe the score for the 'Cycling' cluster (it should be > 5.0).
    3. Change the workout's sport type to 'Cycling'.
    4. Re-open the Cluster Selection dialog.
    5. **Verify** that the similarity score for the 'Cycling' cluster has decreased (penalty removed), likely showing a value < 1.0.

### Manual Verification
- Verify that typing in the Name field does not cause UI lag while the background suggestion calculation is running.
- Confirm that the suggestions list remains accurate after multiple sport toggles.
