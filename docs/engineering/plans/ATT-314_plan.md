# Implementation Plan - ATT-314: TCX Import - Prevent redundant clustering prompts

Fix the issue where users are prompted to cluster workouts that have already been imported or already have a cluster assigned.

## User Review Required

> [!IMPORTANT]
> This fix ensures that the "Legacy Recovery" process is strictly additive. It will skip any files that correspond to workouts already present in the database based on their `FILE_BASE_NAME`.

## Proposed Changes

### Legacy Import Engine

#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- **Early Exit in `importFromTcx`**: Check if the workout already exists using `isWorkoutExisting` at the beginning of the function. If it exists, return `false` immediately to skip parsing and processing.
- **Refinement in `recalculateStats`**: Before suggesting a new cluster or prompting the user, check if the workout already has a `CLUSTER_ID` assigned in the `WorkoutSummaries` table. If it does, skip the clustering logic to prevent redundant prompts.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **Duplicate Import Test**:
    1. Import a TCX file once.
    2. Attempt to import the same file again (or run "Scan TCX" if the file is in Dropbox).
    3. **Expected**: The file is skipped, and no clustering dialog is shown.
- **Clustered Workout Audit**:
    1. Identify a workout that already has a cluster assigned.
    2. (Internal test) Trigger `recalculateStats` for this workout.
    3. **Expected**: The clustering logic detects the existing assignment and does not prompt the user.
