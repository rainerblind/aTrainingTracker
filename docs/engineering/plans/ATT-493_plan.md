# Implementation Plan: ATT-493 - Concurrent TCX Import Background Queuing & Throttling

## 1. Goal Description
Resolve the defect where background TCX import was blocked sequentially during user route cluster naming interactions:
1. **Reduce Semaphore Limit to 3 (`BackupRestoreViewModel.kt`)**: Update `interactionSemaphore` capacity from 10 to 3 (`Semaphore(3)`).
2. **Concurrent Worker Channel (`LegacyImportEngine.kt`)**: Refactor `bulkRecoverFromDropbox()` to process file downloading and parsing concurrently using a channel-based worker pipeline (bounded by `interactionSemaphore(3)`).
3. **Non-Blocking Background Queuing**: Allow background workers to continue downloading and importing subsequent TCX files while previous candidate requests await user resolution in `_interactionQueue`. Backpressure automatically pauses downloading when 3 candidate items accumulate in `_interactionQueue`.

---

## 2. Proposed Changes

### Component 1: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt`
#### [MODIFY] [BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)
- Update `interactionSemaphore`:
  - Change `private val interactionSemaphore = Semaphore(10)` to `private val interactionSemaphore = Semaphore(3)`.

### Component 2: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- Refactor `bulkRecoverFromDropbox(...)`:
  - Create a channel of Dropbox entries and spawn up to 3 concurrent worker coroutines on `Dispatchers.IO`.
  - Each worker consumes entries from the channel, checks for existing workouts, downloads files, and executes `importFromTcx()`.
  - Thread-safely track imported count using `AtomicInteger`.

---

## 3. Verification Plan

### Automated Tests
- Execute `:app:testDebugUnitTest` to verify no regressions in import or database logic.

### Manual Verification Steps (`TST-MIG-017`)
1. **Background Queuing Verification**:
   - Start bulk import of multiple TCX files requiring route cluster naming.
   - Leave the first candidate dialog open without confirming.
   - Observe progress status: subsequent TCX files continue downloading and processing in the background.
2. **Backpressure Throttling Verification**:
   - Observe that background downloading/parsing pauses when 3 items accumulate in `_interactionQueue`.
   - Confirming a dialog releases a permit and allows the background import to resume immediately.
