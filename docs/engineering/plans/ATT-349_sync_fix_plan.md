# Implementation Plan - ATT-349 Refinement: Enforce Total Engine Throttling

Address the technical oversight where the import engine continued background operations (downloads/parsing) even when the UI interaction queue reached its limit.

## User Review Required

> [!IMPORTANT]
> - **Total Backpressure**: I will refactor the import engine to work in a strictly sequential mode. Currently, it starts the next file while the previous one is still being analyzed in the background.
> - **Zero Background Activity**: This change ensures that once you have 10 naming tasks pending, the app will **completely stop** all network activity and file parsing until you clear some space. This is essential for both battery life and data accuracy.

## Proposed Changes

### 1. Core Logic: Sequental Import Pipeline
Fulfills REQ-MIG-017 | Test: TST-MIG-013

#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- **Synchronize Recalculation**:
    - Remove the fire-and-forget `importScope.launch` call inside `importFromTcx`.
    - Call `recalculateStats(...)` directly as a standard suspend function call.
- **Result**: Because `bulkRecoverFromDropbox` awaits the result of `importFromTcx`, and `importFromTcx` will now await the completion of the (throttled) clustering logic, the main import loop will correctly pause.

## Verification Plan

### Manual Verification (TST-MIG-013 Refined)
1. Prepare a Dropbox folder with > 10 new (unclustered) TCX files.
2. Trigger the bulk recovery.
3. **Verify** that the status messages (Scanning/Downloading) stop appearing as soon as the naming queue hits 10.
4. Resolve one naming task and **verify** that exactly one more download/parse operation is initiated.
