# Walkthrough: ATT-491 - Unlinked Dropbox Import Deactivation & Null Safety

## 1. Overview
Resolved the unhandled fatal `NullPointerException` that occurred when a user attempted to scan/import legacy TCX files from Dropbox without an active Dropbox connection.

Per user directive:
> *"To fix this, I suggest to deacivate this feature in the UI (grey and an info when the user clicks on it)."*

We implemented a complete solution spanning UI visual deactivation, informative click intercept dialog, and defensive null safety in the engine and exporter layers.

---

## 2. Changes Made

### UI Layer: Deactivated Styling & Click Intercept Dialog
* **[ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)**:
  * Added `isDropboxConnected` state tracking whether Dropbox is authenticated and configured.
  * Added `showDropboxDisconnectedDialog` displaying title *"Dropbox"* and text *"You are not connected to Dropbox"* (`R.string.dropbox_disconnected_status`).
  * In `ImportTabContent`:
    * When `!isDropboxConnected`, styled the *"Scan Dropbox"* button with grey/muted content and border colors (`MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)`).
    * When clicked while disconnected, intercepted the click and displayed `showDropboxDisconnectedDialog`.
  * Applied identical grey deactivation styling and click intercept to:
    * `BackupTabContent`: *"Create & Upload to Dropbox"* button.
    * `RestoreTabContent`: *"Dropbox: Restore Backup"* button.

### Data & Exporter Layers: Robust Null Safety
* **[TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java)**:
  * In `readDropboxCredential()`, added explicit guard `if (credential == null || credential.trim().isEmpty()) return null;` before delegating to Jackson parsing.
  * Replaced narrow `JsonReadException` catch with general `Exception` logging to prevent crashes on any malformed/corrupted preference values.
* **[BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)**:
  * Defensively verified non-null credentials in `bulkRecoverLegacyData()`, `uploadToDropbox()`, and `restoreFromDropbox()`, cleanly emitting `UiState.Error(R.string.dropbox_disconnected_status)`.
* **[DropboxUploader.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/DropboxUploader.java)**:
  * Added null-guard on `readDropboxCredential()` before creating `DbxClientV2`.

### Requirements, Tests & Documentation
* **[docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)**: Added `REQ-MIG-023` (*Unlinked Cloud Feature Deactivation*).
* **[docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)**: Added `TST-MIG-020` (*Unlinked Dropbox Import Deactivation & Null Safety*).
* **[DropboxCredentialSafetyTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/migration/DropboxCredentialSafetyTest.kt)**: Added unit test suite covering null preferences, null credential string, empty/whitespace string, malformed JSON, and valid JSON parsing.

---

## 3. Verification Evidence

### Automated Unit Tests
Executed `:app:testDebugUnitTest`:
```text
BUILD SUCCESSFUL in 10s
32 actionable tasks: 1 executed, 31 up-to-date
```
Test suite `DropboxCredentialSafetyTest`:
* `testNullSharedPreferencesDoesNotCrash`: PASSED
* `testNullCredentialStringDoesNotCrash`: PASSED
* `testEmptyOrBlankCredentialStringDoesNotCrash`: PASSED
* `testMalformedJsonCredentialStringDoesNotCrash`: PASSED
* `testValidCredentialStringParsesSuccessfully`: PASSED

### Requirement Status
* **`REQ-MIG-023` / `TST-MIG-020`**: VERIFIED
