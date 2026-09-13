# Walkthrough: ATT-493 - Concurrent TCX Import Background Queuing & Throttling

## 1. Overview
Resolved the issue where background TCX file importing was blocked sequentially during user route candidate dialogs:
1. **Concurrent Import Pipeline (`LegacyImportEngine.kt`)**: Refactored `bulkRecoverFromDropbox()` to process file downloading and parsing concurrently using a 3-worker channel pipeline on `Dispatchers.IO`. While a previous item awaits user cluster candidate resolution in `_interactionQueue`, background workers continue downloading and parsing subsequent TCX files.
2. **Backpressure Throttling (`BackupRestoreViewModel.kt`)**: Restricted `interactionSemaphore` to 3 (`Semaphore(3)`). The background pipeline automatically pauses when 3 items accumulate in `_interactionQueue` and resumes as the user confirms dialogs.

---

## 2. Changes Made

### Migration & View Layer
- **[BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)**: Updated `interactionSemaphore` limit from 10 to 3.
- **[LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)**: Refactored `bulkRecoverFromDropbox()` to use a 3-worker channel pipeline for concurrent background file downloading and importing.

---

## 3. Verification Evidence

### Automated Unit Tests
Executed `:app:testDebugUnitTest`:
```text
BUILD SUCCESSFUL
14 passed, 0 skipped, 0 failed
```

### Requirements & Test Status
- **`REQ-MIG-014` / `TST-MIG-017`**: VERIFIED (Concurrent background TCX downloading & importing while user dialogs wait in queue).
- **`REQ-MIG-017` / `TST-MIG-017`**: VERIFIED (Backpressure strictly enforced at 3 pending interaction items).
