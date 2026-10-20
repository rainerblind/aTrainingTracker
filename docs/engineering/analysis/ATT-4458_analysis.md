# Stage 1 Analysis: ATT-4458 - [Bug] Fix LOCAL_SUCCESS / PARTIAL_SUCCESS status display and expose local rolling backup restore in UI

**Ticket**: [ATT-4458](https://rainerblind.atlassian.net/browse/ATT-4458)  
**Sub-task**: [ATT-4488](https://rainerblind.atlassian.net/browse/ATT-4488) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Branch**: `feature/ATT-4458`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During code review and human verification of `ATT-3472` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage*), three related defects and usability barriers were identified in the automated backup and restore architecture:

### 1.1 False Error Display for `LOCAL_SUCCESS` and Raw Enum Display for `PARTIAL_SUCCESS` (`ImportBackupTabsScreen.kt:1261-1266`)
In `BackupWorker.doWork()`, when a scheduled backup executes without cloud providers connected, it successfully creates a local rolling backup (`aTrainingTracker_backup.attbackup`) in `context.filesDir/backups/` and persists `last_backup_status = "LOCAL_SUCCESS"`. When multiple cloud providers are configured and one succeeds while another fails, it records `last_backup_status = "PARTIAL_SUCCESS"`.

However, `ImportBackupTabsScreen.kt` (lines 1264–1265) evaluates backup status using a simplistic binary equality check:
```kotlin
val isSuccess = info.status == "SUCCESS"
Text(
    text = if (isSuccess) stringResource(R.string.last_backup, dateStr) else "Last backup failed: ${info.status}",
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = FontWeight.Bold,
    color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
)
```
Consequently:
- For `LOCAL_SUCCESS`, the UI displays a false error in red text: `"Last backup failed: LOCAL_SUCCESS"`, misleading athletes into believing their automated local backup failed when it actually succeeded.
- For `PARTIAL_SUCCESS`, the UI displays `"Last backup failed: PARTIAL_SUCCESS"` in red text without explaining what partially succeeded.
- Neither `PARTIAL_SUCCESS` nor failures (`"Upload failed"`) indicate *which* target provider (Local, Dropbox, Google Drive) succeeded or failed.
- The failure string `"Last backup failed: ${info.status}"` is hardcoded in English, violating 9-language localization parity.

### 1.2 Internal Rolling Backup (`filesDir/backups/aTrainingTracker_backup.attbackup`) Unreachable from Restore Tab
`BackupManager.createBackup()` creates the canonical rolling backup file inside internal app storage (`context.filesDir/backups/aTrainingTracker_backup.attbackup`).

In `RestoreTabContent`, the *"Local: Full Restore (Migration)"* button invokes Android's Storage Access Framework (`ActivityResultContracts.OpenDocument()`). However, standard Android document pickers cannot browse internal app sandboxes (`context.filesDir`). As a result, athletes have no UI mechanism to restore directly from the automated local rolling backup that the app generates in the background.

### 1.3 Unnecessary Wi-Fi Constraint for Local-Only Backups (`BackupWorker.kt:135`)
In `BackupWorker.schedule(context)`, WorkManager constraints are hardcoded to require unmetered Wi-Fi:
```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.UNMETERED) // Only on Wi-Fi
    .setRequiresBatteryNotLow(true)
    .build()
```
While unmetered network is essential for large cloud uploads to Dropbox and Google Drive, applying this constraint unconditionally prevents local-only rolling backups from running when the athlete has no cloud provider connected and is offline or on cellular data.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 State Evaluation & Hardcoded Failure Presentation in `ImportBackupTabsScreen.kt`
In `ImportBackupTabsScreen.kt`, the `BackupTabContent` composable reads `lastBackupInfo` from `BackupRestoreViewModel`:
```kotlin
val isSuccess = info.status == "SUCCESS"
```
When `ATT-3472` introduced `LOCAL_SUCCESS` and `PARTIAL_SUCCESS` to distinguish local storage from cloud upload states, `ImportBackupTabsScreen.kt` was not updated to recognize these new status tokens. Furthermore, `BackupWorker` only writes a single monolithic string to `last_backup_status` in `SharedPreferences`, discarding the individual success/failure states of connected providers.

### 2.2 Storage Sandbox Isolation vs. Document Picker SAF Architecture
Under Android's security model, `context.filesDir` is private to the application UID (`MODE_PRIVATE`). The Android framework document picker (`ACTION_OPEN_DOCUMENT` / `OpenDocument`) only enumerates document providers registered with `android.content.DocumentsProvider` (such as Downloads, external SD cards, and Google Drive). It cannot navigate to `/data/data/com.atrainingtracker/files/backups/`. 

To restore from the internal rolling backup, the application must provide an explicit UI entry point that directly references `File(BackupManager.getBackupDir(context), BackupManager.BACKUP_FILENAME)` and feeds it into `MigrationEngine.performFullRestore(context, backupFile)`.

### 2.3 Static Network Constraint Gating in `BackupWorker.kt`
In `BackupWorker.Companion.schedule(context)`:
The worker configuration always builds constraints with `setRequiredNetworkType(NetworkType.UNMETERED)`. WorkManager enforces this constraint at the OS level before executing the worker. If the device is offline or on cellular connectivity, the periodic work request remains in `ENQUEUED` state and never triggers `doWork()`. For athletes who do not use cloud backups, background backup execution is completely blocked whenever Wi-Fi is unavailable.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Accurate, Localized Status Messages & Per-Target Breakdown**:
     - Update `BackupWorker.kt` to record `last_backup_status` (`LOCAL_SUCCESS`, `SUCCESS`, `PARTIAL_SUCCESS`, `Upload failed`, `Local backup creation failed`) and serialize per-target statuses (`last_backup_targets = "local:SUCCESS;dropbox:...;google_drive:..."`).
     - Update `BackupRestoreViewModel.LastBackupInfo` to parse `last_backup_targets` into a structured map `targets: Map<String, Boolean>`.
     - Update status display in `BackupTabContent` to render localized status messages for `LOCAL_SUCCESS`, `SUCCESS`, `PARTIAL_SUCCESS`, and failures, with a per-target status row showing Local, Dropbox, and Google Drive outcomes with clear visual indicators.
  2. **Dedicated Restore from Automatic Local Backup**:
     - Add `restoreFromLocalBackup(context: Context)` in `BackupRestoreViewModel.kt` to restore directly from `filesDir/backups/aTrainingTracker_backup.attbackup`.
     - Expose the existence and `lastModified` timestamp of the rolling backup file in `BackupRestoreViewModel`.
     - In `RestoreTabContent`, add a dedicated *"Restore from Automatic Local Backup"* button displaying the timestamp of the rolling backup when present (or an informative disabled state when no backup exists).
     - Protect the action with the existing destructive full restore confirmation dialog (`AlertDialog` with `restore_warning_title` / `restore_warning_message`).
     - Replace hardcoded `"Local: "` and `"Dropbox: "` string concatenations on existing restore buttons with dedicated localized strings (`restore_from_file`, `restore_from_dropbox`).
  3. **Conditional Network Constraint in `BackupWorker.schedule()`**:
     - Dynamically determine network requirements: if at least one cloud provider (Dropbox or Google Drive) is connected and enabled, require `NetworkType.UNMETERED`; if no cloud provider is enabled, set `NetworkType.NOT_REQUIRED`.
  4. **Modularity & 9-Language Localization Parity**:
     - Extract `BackupTabContent` and `RestoreTabContent` (and `ImportMappingDialog`) into modular helper composables under `com.atrainingtracker.trainingtracker.migration` (< 400 lines per file).
     - Preserve existing reflection and source-contract test requirements in `ImportBackupTabsScreen.kt` (`AppDialogActionsIntegrityTest`, `ImportBackupMapResilienceTest`, `ImportFormatCardsContractTest`, `BottomSheetVisualContractTest`, `DarkMapStyleResolutionTest`).
     - Supply full 9-language translations across all supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. **No SQLite Schema or Migration Algorithm Changes**: The underlying restore mechanism (`MigrationEngine.performFullRestore`), database connection management, and rollback snapshots are unchanged.
  2. **No OAuth PKCE or Cloud Sync Changes**: Cloud authentication logic in `DropboxSettingsDialog` and `GoogleDriveSettingsDialog` remains untouched.
  3. **No Changes to FIT/TCX/GPX Import Formats**: The workout import pipelines and format cards in `ImportTabContent` remain unchanged.
  4. **No Removal of SAF Document Picker**: External file restore via `OpenDocument` is retained so athletes can restore backups imported from other devices or folders.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  - Refines `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage*, introduced in `ATT-3472`).
  - Target files: `BackupWorker.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`, `RestoreTabContent.kt`, `BackupTabContent.kt`, `values*/strings.xml`.

* **Historical Origin & Commit Trace**:
  - `ATT-3472` (commit `9739a5bc`, Sprint 2026-42.3): Introduced `ExistingPeriodicWorkPolicy.UPDATE` for monotonic scheduling, decoupled local backups with `LOCAL_SUCCESS`, and co-located atomic move.
  - `ATT-3982` (commit `b8759380`, Sprint 2026-42.10): Added Zip-Slip path traversal protection (`REQ-MIG-040`).
  - `ATT-4011` (commit `71550c60`, Sprint 2026-42.10): Added SQLite `VACUUM INTO` atomic snapshotting and DB helper leak protection (`REQ-DAT-031`).

* **Root Reason for Existing Formulation**:
  - `NetworkType.UNMETERED` was inherited from early versions where automated backups were exclusively cloud uploads to Dropbox. When `ATT-3472` enabled local-only backups, the network constraint was left unmodified in `BackupWorker.schedule()`.
  - In `ImportBackupTabsScreen.kt`, the equality check `info.status == "SUCCESS"` predated the addition of `LOCAL_SUCCESS` and `PARTIAL_SUCCESS` in `ATT-3472`.
  - The document picker (`OpenDocument`) was originally the only restore path because automated backups were intended to be manually shared or exported before local rolling retention was implemented.

* **Preservation of Core Invariants**:
  - Monotonic periodic timer preservation (`ExistingPeriodicWorkPolicy.UPDATE`) is strictly preserved.
  - Single canonical file storage bounds ($O(1)$ disk space) are strictly preserved.
  - Co-located atomic move (`ATOMIC_MOVE`) and stale temp cleanup are strictly preserved.
  - Destructive restore warning confirmation dialog is strictly enforced before initiating any restore.
  - 100% clean-room test pass rate under `./gradlew testDebugUnitTest` is strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Dynamic Network Constraint in `BackupWorker.schedule()`
In `BackupWorker.Companion.schedule(context)`:
```kotlin
val cloudBackupEnabled = (TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null) ||
    (TrainingApplication.uploadToGoogleDrive() && TrainingApplication.uploadBackupToGoogleDrive())

val networkType = if (cloudBackupEnabled) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED

val constraints = Constraints.Builder()
    .setRequiredNetworkType(networkType)
    .setRequiresBatteryNotLow(true)
    .build()
```
When only local backup is active, WorkManager runs the job periodically even without internet/Wi-Fi. When cloud sync is active, it waits for unmetered Wi-Fi.

### 5.2 Per-Target Status Persistence in `BackupWorker.doWork()`
`BackupWorker.doWork()` records both the high-level `last_backup_status` and serialized per-target statuses:
```kotlin
// Build per-target status map:
val targets = mutableMapOf<String, Boolean>()
targets["local"] = (backupFile != null)
if (dropboxConnected) targets["dropbox"] = dbxSuccess
if (googleDriveConnected) targets["google_drive"] = gdSuccess

val targetsStr = targets.entries.joinToString(";") { "${it.key}:${if (it.value) "SUCCESS" else "FAILED"}" }

prefs.edit(commit = true) {
    putLong("last_backup_timestamp", System.currentTimeMillis())
    putString("last_backup_status", aggregateStatus)
    putString("last_backup_targets", targetsStr)
}
```

### 5.3 `BackupRestoreViewModel` Enhancements
1. **Target Status Parsing**: `LastBackupInfo` parses `last_backup_targets` into `targets: Map<String, Boolean>` (with graceful fallback for older preference formats).
2. **Local Rolling Backup Inspection**:
   ```kotlin
   fun getAutomaticBackupTimestamp(context: Context): Long? {
       val file = File(BackupManager.getBackupDir(context), BackupManager.BACKUP_FILENAME)
       return if (file.exists() && file.length() > 0) file.lastModified() else null
   }
   ```
3. **Local Rolling Restore Execution**:
   ```kotlin
   fun restoreFromLocalBackup(context: Context) {
       viewModelScope.launch(Dispatchers.IO) {
           val backupFile = File(BackupManager.getBackupDir(context), BackupManager.BACKUP_FILENAME)
           if (!backupFile.exists()) {
               _uiState.value = UiState.Error(context.getString(R.string.restore_automatic_backup_not_found))
               return@launch
           }
           _uiState.value = UiState.Loading(context.getString(R.string.unpacking_archive))
           val success = MigrationEngine.performFullRestore(context, backupFile, object : MigrationEngine.ProgressListener {
               override fun onProgress(message: String) {
                   _uiState.value = UiState.Loading(message)
               }
           })
           if (!success) {
               _uiState.value = UiState.Error(context.getString(R.string.restore_failed))
           }
       }
   }
   ```

### 5.4 UI Decomposition & Enhancement in `RestoreTabContent` and `BackupTabContent`
1. **Modularity**:
   - Extract `BackupTabContent.kt` and `RestoreTabContent.kt` as separate internal composables in `com.atrainingtracker.trainingtracker.migration`.
   - Keep `ImportBackupTabsScreen.kt` intact with all reflection contracts and map resilience tests.
2. **`BackupTabContent` Status Display**:
   - Status text color and message:
     - `SUCCESS`: `MaterialTheme.colorScheme.primary`, `R.string.last_backup` ("Last backup: %1$s")
     - `LOCAL_SUCCESS`: `MaterialTheme.colorScheme.primary`, `R.string.last_backup_local_success` ("Last backup: %1$s (Local only)")
     - `PARTIAL_SUCCESS`: `MaterialTheme.colorScheme.tertiary`, `R.string.last_backup_partial_success` ("Last backup: %1$s (Partial)")
     - Failures: `MaterialTheme.colorScheme.error`, `R.string.last_backup_failed`
   - Per-target indicators: Row of target badges (Local, Dropbox, Google Drive) with check or cross icons.
3. **`RestoreTabContent` Options**:
   - Primary: *"Restore from Automatic Local Backup"* button showing formatted `lastModified` timestamp. Enabled only when the file exists.
   - Secondary: *"Restore from File"* button launching SAF document picker.
   - Cloud: *"Restore from Dropbox"* button.
   - Cloud: *"Restore from Google Drive"* button (when Google Drive is connected).
   - All destructive restores protected by `AlertDialog` confirmation.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Monotonic WorkManager periodic timer preservation (`ExistingPeriodicWorkPolicy.UPDATE`).
  2. Bounded $O(1)$ disk space for local backup files.
  3. Strict destructive operation guard (confirmation dialog required before any full restore).
  4. Backward compatibility of `last_backup_status` preference strings for existing unit tests (`"LOCAL_SUCCESS"`, `"SUCCESS"`).
  5. Preservation of all existing source-code contract tests for `ImportBackupTabsScreen.kt`.
  6. 9-language localization parity across all 9 application locales.
  7. 100% clean-room test suite pass rate under `./gradlew testDebugUnitTest`.
  8. Parent ticket Human Decision Gate remains strictly enforced.

* **Risk Rating**: **MEDIUM**
  - **Technical Justification**: While this change touches WorkManager scheduling constraints, preference serialization, and UI restore actions, it leaves SQLite transactions, database schemas, and `MigrationEngine` core restoration algorithms completely intact. Risk is mitigated by comprehensive unit test coverage across `BackupWorkerTest`, `BackupWorkerGoogleDriveTest`, and `BackupRestoreViewModelTest`.
