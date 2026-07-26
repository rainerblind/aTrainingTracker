# Walkthrough - ATT-349: Limit UI Interaction Queue Size

Successfully implemented a "Backpressure Throttling" mechanism for the TCX import UI queue. This ensures that the system remains responsive and clustering accuracy is maintained by forcing timely user decisions during large background migrations.

## Fulfilled Requirements

| ID | Description | Rationale |
|:---|:---|:---|
| **REQ-MIG-017** | The system SHALL limit the number of pending UI interactions to a maximum of 10. | Prevent excessive memory usage and ensure that new clusters are named promptly for optimal automatic grouping. |

## Changes Made

### 🛡️ Semaphore-Based Backpressure

#### [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- **Engine Throttling**: Introduced a `Semaphore(10)` to manage the interaction queue size. 
- **Suspension Logic**: The background import engine now calls `interactionSemaphore.acquire()` before submitting a new "Cluster Naming" request to the UI. If 10 requests are already pending, the engine **suspends** (pauses) its processing.
- **Workflow Resumption**: The semaphore is only released (`interactionSemaphore.release()`) once the user has made a decision (naming or ignoring) for an interaction. This ensures that the engine only proceeds when there is space in the queue, preventing "dialog overload".

### 🏗️ Improved Clustering Accuracy

- **Prompt Naming**: By limiting the queue to 10 items, the user is forced to address new clusters as they are discovered. This allows the engine to immediately use these newly-named clusters for subsequent workouts in the same batch, significantly increasing the precision of the automated migration.

## Verification Results

### Integration Verification (SWE.5)
- **Test ID**: TST-MIG-013
- **Result**: **PASS**. Verified using a large batch of unclustered legacy workouts. The system successfully paused background file processing once 10 interactions were queued. Resolving one task immediately allowed the engine to process the next file and add a new task, maintaining a perfectly stable window of 10 items.

> [!TIP]
> This architectural improvement transforms a potentially overwhelming "bulk import" into a controlled, high-precision migration process that prioritizes data quality and system stability.
