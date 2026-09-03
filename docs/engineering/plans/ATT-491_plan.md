# Implementation Plan: ATT-491 - Unlinked Dropbox Import Deactivation & Null Safety

## 1. Problem & Context
When a user attempts to scan or import workouts from Dropbox without being authenticated/connected to Dropbox, the application crashes with a fatal `NullPointerException` in `TrainingApplication.readDropboxCredential()`:
```text
java.lang.NullPointerException: Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference
    at com.fasterxml.jackson.core.JsonFactory.createParser(JsonFactory.java:1217)
    at com.dropbox.core.json.JsonReader.readFully(JsonReader.java:469)
    at com.atrainingtracker.trainingtracker.TrainingApplication.readDropboxCredential(TrainingApplication.java:499)
    at com.atrainingtracker.trainingtracker.migration.LegacyImportEngine.bulkRecoverFromDropbox(LegacyImportEngine.kt:81)
    at com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModel$bulkRecoverLegacyData$1.invokeSuspend(BackupRestoreViewModel.kt:309)
```

Per user decision:
> *"To fix this, I suggest to deacivate this feature in the UI (grey and an info when the user clicks on it)."*

---

## 2. Proposed Changes

### UI Layer: Deactivated Appearance & Explanatory Click Dialog
* **[ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)**:
  * Detect Dropbox connectivity state: `val isDropboxConnected = remember(uiState) { TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null }`.
  * Add state for `showDropboxDisconnectedDialog: Boolean`.
  * In `ImportTabContent`:
    * Deactivate the "Scan Dropbox" (`R.string.scan_tcx`) button when `!isDropboxConnected`:
      * Visual styling: Apply deactivated/grey styling (grey text, icon, and outline border using `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)`).
      * Click interaction: When clicked while disconnected, intercept the action and present an informative `AlertDialog` with title *"Dropbox"* and message *"You are not connected to Dropbox"* (`R.string.dropbox_disconnected_status`). When connected, trigger `onBulkRecoverClick` as normal.
  * In `BackupTabContent` & `RestoreTabContent`:
    * Apply the same deactivated grey styling and explanatory dialog intercept to "Create & Upload to Dropbox" and "Restore from Dropbox" so the entire screen exhibits uniform, predictable behavior.

### Data Layer: Null Safety & Robust Error Handling
* **[TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java)**:
  * In `readDropboxCredential()`:
    * Guard with `if (credential == null || credential.trim().isEmpty()) return null;`.
    * Catch general `Exception` rather than only `JsonReadException` to prevent unhandled crashes on corrupted/partial preference values.
* **[BackupRestoreViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt)**:
  * In `bulkRecoverLegacyData()`, defensively check `TrainingApplication.readDropboxCredential() != null`; if null, set `_uiState.value = UiState.Error(context.getString(R.string.dropbox_disconnected_status))`.
* **[DropboxUploader.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/DropboxUploader.java)**:
  * Guard `DbxClientV2` constructor against null credentials.

### Requirements & Test Traceability
* **[docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)**: Add `REQ-MIG-023` (*Unlinked Cloud Feature Deactivation*).
* **[docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)**: Add `TST-MIG-020` (*Unlinked Dropbox Import Deactivation & Null Safety*).

---

## 3. Verification Plan

### Automated Unit Testing
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/migration/DropboxCredentialSafetyTest.kt` (or similar unit test) to verify null/empty/invalid string handling without exceptions.
* Execute `./gradlew testDebugUnitTest`.

### UI & System Invariant Testing
* Verify deactivated styling on the button when unlinked.
* Verify dialog appears with localized text on click when unlinked.
