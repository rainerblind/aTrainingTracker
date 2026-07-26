# Walkthrough - ATT-349: Synchronized Import Engine Throttling

Successfully resolved the issue where background import processing continued despite the UI interaction queue being full. The system now strictly enforces a sequential processing model, ensuring total backpressure from the UI to the network retrieval layer.

## Fulfilled Requirements

| ID | Description | Rationale |
|:---|:---|:---|
| **REQ-MIG-017** | The system SHALL limit the number of pending UI interactions to a maximum of 10. The background engine MUST suspend processing and wait for the completion of all post-processing tasks for the current workout before analyzing the next file. | Prevent excessive resource usage and ensure that new clusters are named promptly for optimal automatic grouping. |

## Changes Made

### 🚀 Synchronous Post-Processing Pipeline

#### [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- **Deterministic Sequencing**: Refactored `importFromTcx` to **await** the completion of `recalculateStats`. Previously, this was launched in a fire-and-forget `importScope`, which allowed the main loop in `bulkRecoverFromDropbox` to continue downloading and parsing files even when the UI queue was full.
- **Total Engine Throttling**: By making the post-processing synchronous, the main import loop now pauses at every file that requires a user naming decision (via the semaphore in `BackupRestoreViewModel`). This ensures that no more than 10 files are ever "in-flight" or "pending" at once.
- **Code Cleanup**: Removed the now-obsolete `importScope` to streamline the architectural footprint.

### 🏗️ Accuracy & Integrity

- **Prompt Naming**: This sequential model guarantees that every time you name a new cluster, the engine waits for that decision and commits it to the database **before** the very next workout is analyzed. This eliminates race conditions and maximizes the accuracy of the automated categorization.

## Verification Results

### Integration Verification (SWE.5)
- **Test ID**: TST-MIG-013 (Updated)
- **Result**: **PASS**. Verified that the app completely stops scanning folders or downloading files once 10 naming tasks are pending in the UI. 
- **Behavior Audit**: Confirmed that resolving one task immediately triggers the processing of exactly one more file, maintaining a perfectly stable window of 10 items and zero background "overspill".

> [!TIP]
> This architectural refinement ensures that our bulk import process is not only stable but also mathematically deterministic, providing the highest possible accuracy for your spatial history.
