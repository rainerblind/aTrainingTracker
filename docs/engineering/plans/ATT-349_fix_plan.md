# Implementation Plan - ATT-349: Synchronize Import Engine Throttling

Address the issue where background import processing continues despite the UI interaction queue being full by synchronizing the post-processing pipeline.

## User Review Required

> [!IMPORTANT]
> - **Engine Synchronization**: I will refactor the import engine to process workouts in a strictly sequential manner. Currently, it analyzes the next file immediately while the previous one is still being clustered in the background. 
> - **Total Throttling**: This change ensures that if you have 10 naming tasks pending, the app will completely stop downloading or parsing any more files until you clear space.

## Proposed Changes

### 1. Migration Engine: Sequental Processing
Fulfills REQ-MIG-017 | Test: TST-MIG-013

#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- **Refactor `importFromTcx`**:
    - Remove the `importScope.launch` wrapper around the `recalculateStats` call.
    - Call `recalculateStats(...)` directly as a suspend function call.
- **Rationale**: Since `bulkRecoverFromDropbox` already awaits the result of `importFromTcx`, this change will force the main import loop to pause at each file until all post-processing (including the throttled cluster naming) is finished.

## Verification Plan

### Manual Verification (TST-MIG-013)
1. Trigger a bulk TCX recovery with > 10 new routes.
2. **Verify** that the app stops downloading/scanning once the interaction queue hits 10.
3. Observe the Logcat to ensure no new "Importing..." messages appear while the user naming dialog is visible for the 10th item.
4. Resolve one interaction and **verify** that exactly one more file is then processed.
