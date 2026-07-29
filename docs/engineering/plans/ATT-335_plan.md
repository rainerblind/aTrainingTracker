# Implementation Plan - ATT-335: Optimize legacy recovery by skipping existing files before download

Optimize the performance of the legacy TCX recovery process by checking if a workout already exists in the database before downloading it from Dropbox.

## User Review Required

> [!NOTE]
> This change focuses purely on performance and bandwidth efficiency. There is no change to the underlying data processing or clustering logic.

## Proposed Changes

### Legacy Import Engine

#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- **Refactor `bulkRecoverFromDropbox`**:
    - Inside the `entries.forEachIndexed` loop, extract the `baseFileName` from the `entry.name`.
    - Use `isWorkoutExisting` to check if this file has already been imported.
    - If it exists, skip the download and processing for this entry.
    - Post progress even for skipped files to maintain a responsive UI.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **TST-MIG-011**:
    1. Ensure several legacy workouts exist in the local database.
    2. Run "Scan TCX" with those same files present in Dropbox.
    3. Observe the logcat for "Skipping X: Workout already exists" messages.
    4. **Expected**: The skipping messages appear almost instantaneously, without the 2-5 second delay previously caused by file downloads.
