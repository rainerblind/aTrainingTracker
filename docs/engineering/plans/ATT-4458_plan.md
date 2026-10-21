# Stage 3: Implementation Plan - ATT-4458: [Bug] Fix LOCAL_SUCCESS / PARTIAL_SUCCESS status display and expose local rolling backup restore in UI

**Ticket**: [ATT-4458](https://rainerblind.atlassian.net/browse/ATT-4458)  
**Sub-task**: [ATT-4490](https://rainerblind.atlassian.net/browse/ATT-4490) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-MIG-036` (`REQ-MIG-036.7`, `REQ-MIG-036.8`, `REQ-MIG-036.9`, `REQ-MIG-036.10`, `REQ-MIG-036.11`)  
**Test Mapping**: `TST-MIG-033` (`TST-MIG-033.8`, `TST-MIG-033.9`, `TST-MIG-033.10`, `TST-MIG-033.11`, `TST-MIG-033.12`, `TST-MIG-033.13`, `TST-MIG-033.14`, `TST-MIG-033.15`)  
**Branch**: `feature/ATT-4458`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

Identified during code review and human verification of `ATT-3472` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, and Bounded Local Storage*):

1. **False Error Display for `LOCAL_SUCCESS` and Raw Enum Display for `PARTIAL_SUCCESS` (`ImportBackupTabsScreen.kt:1261-1266`)**:
   - `BackupWorker.doWork()` writes `last_backup_status` as `"LOCAL_SUCCESS"` (when no cloud provider is connected), `"PARTIAL_SUCCESS"` (when one connected cloud provider succeeds and another fails), or `"SUCCESS"`.
   - `ImportBackupTabsScreen.kt` lines 1264–1265 strictly checked `val isSuccess = info.status == "SUCCESS"` and rendered red error text (`"Last backup failed: LOCAL_SUCCESS"`, `"Last backup failed: PARTIAL_SUCCESS"`), creating false error alarms for successful local backups.
   - Furthermore, when `PARTIAL_SUCCESS` or `Upload failed` occurred, the UI did not indicate *which* target provider (Local, Dropbox, Google Drive) succeeded or failed.
2. **Internal Rolling Backup (`filesDir/backups/aTrainingTracker_backup.attbackup`) Unreachable from Restore Tab**:
   - `BackupManager.createBackup()` creates the rolling backup inside internal app storage (`context.filesDir/backups/aTrainingTracker_backup.attbackup`).
   - The *Local Restore* button in `RestoreTabContent` opens the Android system document picker (`OpenDocument`), which cannot browse internal `context.filesDir/backups/`. Athletes therefore had no UI action to restore directly from the automated local rolling backup.
3. **Unnecessary Wi-Fi Constraint for Local-Only Backups (`BackupWorker.kt:135`)**:
   - `BackupWorker.schedule()` unconditionally set `NetworkType.UNMETERED`, preventing local-only rolling backups from running when the athlete has no cloud provider enabled and is offline.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-036` (*Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, Conditional Network Constraints, Status Diagnostics and UI Rolling Restore*)
  * `REQ-MIG-036.7`: Conditional Network Constraints — build `NetworkType.UNMETERED` when cloud backup is active; build `NetworkType.NOT_REQUIRED` for local-only backups.
  * `REQ-MIG-036.8`: Per-Target Status Persistence — persist serialized per-target statuses (`last_backup_targets = "local:SUCCESS;..."`) in `SharedPreferences`.
  * `REQ-MIG-036.9`: Localized Status Display & Diagnostic Clarity — evaluate `lastBackupInfo` to display localized strings for `SUCCESS`, `LOCAL_SUCCESS`, `PARTIAL_SUCCESS`, and failures with per-target visual badges.
  * `REQ-MIG-036.10`: Direct UI Rolling Backup Restoration — expose internal rolling backup existence, timestamp, and 1-tap restore in `RestoreTabContent` with confirmation dialog.
  * `REQ-MIG-036.11`: Elimination of Literal String Concatenations — replace `"Local: "` and `"Dropbox: "` with dedicated localized string resources (`restore_from_file`, `restore_from_dropbox`).

* **Test Mapping**: `TST-MIG-033`
  * `[TST-MIG-033.9]`: `testSchedule_whenCloudDisabled_setsNetworkTypeNotRequired` (`BackupWorkerTest.kt`)
  * `[TST-MIG-033.10]`: `testSchedule_whenCloudEnabled_setsNetworkTypeUnmetered` (`BackupWorkerTest.kt`)
  * `[TST-MIG-033.11]`: `testDoWork_persistsTargetBreakdown_onLocalSuccess` (`BackupWorkerTest.kt`)
  * `[TST-MIG-033.12]`: `testDoWork_persistsTargetBreakdown_onPartialSuccessAndFailure` (`BackupWorkerTest.kt`)
  * `[TST-MIG-033.13]`: `testLastBackupInfo_parsesTargetBreakdownMap` (`BackupRestoreViewModelLocalRestoreTest.kt`)
  * `[TST-MIG-033.14]`: `testGetAutomaticBackupTimestamp_returnsTimestampWhenFileExists` (`BackupRestoreViewModelLocalRestoreTest.kt`)
  * `[TST-MIG-033.15]`: `testRestoreFromLocalBackup_triggersMigrationEngineAndUpdatesUiState` (`BackupRestoreViewModelLocalRestoreTest.kt`)
  * `[TST-MIG-033.8]`: 9-Language Localization Audit across all 9 canonical locales (`TranslationParityTest.kt`)
  * `[TST-MIG-033.9]`: Full clean-room test suite regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Monotonic WorkManager Scheduling (`ExistingPeriodicWorkPolicy.UPDATE`)**: WorkManager periodic timer and initial delay countdown across application cold starts are strictly preserved.
2. **Storage Boundedness ($O(1)$ Space)**: Automated local backups maintain strictly a single canonical file in `context.filesDir/backups/`.
3. **Co-Located Atomic Move (`ATOMIC_MOVE`)**: Staging files in `context.cacheDir` move atomically to prevent partial corruption.
4. **Destructive Guard**: Full restore operations MUST always require explicit athlete confirmation via `AlertDialog`.
5. **Contract Test Integrity**: All reflection and source-code contract tests for `ImportBackupTabsScreen.kt` (`AppDialogActionsIntegrityTest`, `ImportBackupMapResilienceTest`, `ImportFormatCardsContractTest`, `BottomSheetVisualContractTest`, `DarkMapStyleResolutionTest`) remain 100% compliant and unbroken.
6. **9-Language Localization Parity**: Zero missing strings, matching positional format specifiers (`%1$s`), and literal `\n` linebreaks across all 9 supported locales.
7. **Clean-Room Test Pass Rate**: 100% pass rate under `./gradlew testDebugUnitTest`.
8. **Parent Human Gate Invariance**: Terminal progression of parent ticket `ATT-4458` to `Erledigt` is reserved exclusively for the human user.

---

## 4. Proposed Architectural Changes (SWE.2)

```
+---------------------------------------------------------------------------------------------------------+
|                                              UI Layer                                                   |
|                                                                                                         |
|  +---------------------------------------------------------------------------------------------------+  |
|  |                                    ImportBackupTabsScreen                                         |  |
|  |  - Preserves contracts: AppDialogActions, MapResilience, FormatCards, DarkMapStyle               |  |
|  |                                                                                                   |  |
|  |  +-----------------------------------------------+  +------------------------------------------+  |  |
|  |  |            BackupTabContent                   |  |            RestoreTabContent             |  |  |
|  |  | - Localized status text:                      |  | - "Restore from Automatic Local Backup"  |  |  |
|  |  |   * SUCCESS (primary)                         |  |   showing backup file timestamp          |  |  |
|  |  |   * LOCAL_SUCCESS (primary, "Local only")     |  | - "Restore from File" (SAF OpenDoc)      |  |  |
|  |  |   * PARTIAL_SUCCESS (tertiary, "Partial")     |  | - "Restore from Dropbox"                 |  |  |
|  |  |   * Failures (error)                          |  | - "Restore from Google Drive"            |  |  |
|  |  | - Target Breakdown badges:                    |  | - Protected by destructive AlertDialog  |  |  |
|  |  |   [Local: OK] [Dropbox: OK] [GDrive: FAIL]    |  |                                          |  |  |
|  |  +-----------------------------------------------+  +------------------------------------------+  |  |
|  +---------------------------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------------------------+
                                                  |
                                                  v
+---------------------------------------------------------------------------------------------------------+
|                                           ViewModel Layer                                               |
|                                                                                                         |
|  +---------------------------------------------------------------------------------------------------+  |
|  |                                     BackupRestoreViewModel                                        |  |
|  | - LastBackupInfo(timestamp, status, targets: Map<String, Boolean>)                                |  |
|  | - getAutomaticBackupTimestamp(context): Long?                                                     |  |
|  | - restoreFromLocalBackup(context): Triggers MigrationEngine.performFullRestore on local file     |  |
|  +---------------------------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------------------------+
                                                  |
                                                  v
+---------------------------------------------------------------------------------------------------------+
|                                    WorkManager & Background Layer                                       |
|                                                                                                         |
|  +---------------------------------------------------------------------------------------------------+  |
|  |                                          BackupWorker                                             |  |
|  | - schedule(context):                                                                              |  |
|  |   if (cloudEnabled) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED                           |  |
|  | - doWork():                                                                                       |  |
|  |   persists "last_backup_status" AND "last_backup_targets" ("local:SUCCESS;dropbox:FAILED;...")    |  |
|  +---------------------------------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------------------------------+
```

### 4.1 Component 1: `BackupWorker.kt` (Dynamic Constraints & Per-Target Status)
1. **Dynamic Constraints**:
   - Extract a helper `buildConstraints(context: Context): Constraints` (or compute directly in `schedule`):
     ```kotlin
     val cloudBackupEnabled = (TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null) ||
         (TrainingApplication.uploadToGoogleDrive() && TrainingApplication.uploadBackupToGoogleDrive())
     val networkType = if (cloudBackupEnabled) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED
     val constraints = Constraints.Builder()
         .setRequiredNetworkType(networkType)
         .setRequiresBatteryNotLow(true)
         .build()
     ```
2. **Per-Target Status Serialization**:
   - In `doWork()`, track outcomes in a map:
     ```kotlin
     val targets = mutableMapOf<String, Boolean>()
     targets["local"] = (backupFile != null)
     if (dropboxConnected) targets["dropbox"] = dbxSuccess
     if (googleDriveConnected) targets["google_drive"] = gdSuccess
     val targetsStr = targets.entries.joinToString(";") { "${it.key}:${if (it.value) "SUCCESS" else "FAILED"}" }
     ```
   - Persist `putString("last_backup_targets", targetsStr)` in `SharedPreferences` whenever `last_backup_status` is updated.

### 4.2 Component 2: `BackupRestoreViewModel.kt` (Target Parsing & Local Restore)
1. **`LastBackupInfo` Enhancement**:
   - Update `data class LastBackupInfo(val timestamp: Long, val status: String, val targets: Map<String, Boolean> = emptyMap())`.
   - In `readLastBackupInfo(p: SharedPreferences)`:
     - Read `targetsStr = p.getString("last_backup_targets", null)`.
     - Parse `targetsStr` into `Map<String, Boolean>`:
       ```kotlin
       val targets = targetsStr?.split(";")?.mapNotNull { entry ->
           val parts = entry.split(":")
           if (parts.size == 2) parts[0].trim() to (parts[1].trim() == "SUCCESS") else null
       }?.toMap() ?: inferTargetsFromStatus(st)
       ```
     - Provide fallback `inferTargetsFromStatus(status)` for legacy preference entries where `last_backup_targets` was not yet written.
2. **Automatic Local Backup Inspection**:
   - Expose `fun getAutomaticBackupTimestamp(context: Context): Long?`:
     ```kotlin
     val backupFile = File(BackupManager.getBackupDir(context), BackupManager.BACKUP_FILENAME)
     return if (backupFile.exists() && backupFile.length() > 0) backupFile.lastModified() else null
     ```
3. **Local Rolling Restore**:
   - Add `fun restoreFromLocalBackup(context: Context)`:
     - Verify file existence.
     - Emit `UiState.Loading`.
     - In `Dispatchers.IO`, call `MigrationEngine.performFullRestore(context, backupFile, progressListener)`.
     - If false, emit `UiState.Error(context.getString(R.string.restore_failed))`.

### 4.3 Component 3: UI Decomposition & Presentation (`RestoreTabContent.kt`, `BackupTabContent.kt`)
1. **Modularity Architecture**:
   - Extract `BackupTabContent.kt` and `RestoreTabContent.kt` as separate internal composables under `com.atrainingtracker.trainingtracker.migration`.
   - Maintain `ImportBackupTabsScreen.kt` line budget strictly within modularity guidelines while preserving all contract test hooks (`AppDialogActionsIntegrityTest`, `ImportBackupMapResilienceTest`, `ImportFormatCardsContractTest`, `BottomSheetVisualContractTest`, `DarkMapStyleResolutionTest`).
2. **`BackupTabContent`**:
   - Render localized status text:
     - `SUCCESS` -> `stringResource(R.string.last_backup, dateStr)` (color: `primary`)
     - `LOCAL_SUCCESS` -> `stringResource(R.string.last_backup_local_success, dateStr)` (color: `primary`)
     - `PARTIAL_SUCCESS` -> `stringResource(R.string.last_backup_partial_success, dateStr)` (color: `tertiary`)
     - Failures -> `stringResource(R.string.last_backup_failed_format, failureReason)` (color: `error`)
   - Target Badges:
     - When `info.targets` is non-empty, render a `FlowRow` or compact `Row` of target chips/pills:
       - `Local`: `stringResource(R.string.backup_target_local)` with `Icons.Default.CheckCircle` (green/primary) or `Icons.Default.Cancel` (error).
       - `Dropbox`: `stringResource(R.string.backup_target_dropbox)` with check or cancel icon.
       - `Google Drive`: `stringResource(R.string.backup_target_google_drive)` with check or cancel icon.
3. **`RestoreTabContent`**:
   - Dedicated *"Restore from Automatic Local Backup"* button:
     - Displays formatted timestamp: `stringResource(R.string.restore_automatic_local_backup_format, dateStr)` when present.
     - Displays `stringResource(R.string.restore_automatic_backup_not_found)` and disabled when no backup file exists.
     - Tapping opens confirmation dialog `showLocalRestoreConfirm = true`.
   - File Restore button:
     - Label: `stringResource(R.string.restore_from_file)` (replaces hardcoded `"Local: "` string concatenation).
   - Dropbox Restore button:
     - Label: `stringResource(R.string.restore_from_dropbox)` (replaces hardcoded `"Dropbox: "` string concatenation).
   - Google Drive Restore button:
     - Label: `stringResource(R.string.restore_from_google_drive)`.
     - Wired to `viewModel.restoreFromGoogleDrive(context)` with destructive confirmation dialog.

### 4.4 UI Consistency (Rule 23)
* **Reference screen / component**: Existing `ImportBackupTabsScreen.kt` cards (`FormatImportCard`, `ElevatedCard`).
* **Reused components**: Material 3 `ElevatedCard`, `Button`, `OutlinedButton`, `ButtonDefaults`, `Icon`, `Text`, `AlertDialog`.
* **Theme tokens**:
  - Shapes: `RoundedCornerShape(16.dp)` on cards; standard Material 3 button shapes.
  - Spacing: `16.dp` card padding, `8.dp` button spacing, `12.dp` status spacing.
  - Colors:
    - Success / Local Success: `MaterialTheme.colorScheme.primary`
    - Partial Success: `MaterialTheme.colorScheme.tertiary` (or `TTColor.Warning`)
    - Failure / Destructive buttons: `MaterialTheme.colorScheme.error`
    - Card Surfaces: `CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)`
* **New one-off styles & justification**: None. Fully conforms to `docs/design_guidelines.md` Sections 5.1–5.4.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Code Check (Code Lock Verification — Rule 4)
* Verify Gate 3 has passed before modifying any production source code:
  ```bash
  python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>
  ```
* Confirm return code `0` (`GATE_PASSED`).

### Step 2: Update `BackupWorker.kt` (Dynamic Constraints & Target Persistence)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt`
* Changes:
  1. In `schedule(context)`:
     - Evaluate `cloudBackupEnabled` from `TrainingApplication.uploadToDropbox()` & credentials, and `uploadToGoogleDrive()` & `uploadBackupToGoogleDrive()`.
     - Set `networkType = if (cloudBackupEnabled) NetworkType.UNMETERED else NetworkType.NOT_REQUIRED`.
     - Expose package-private/internal `@VisibleForTesting fun buildConstraints(context: Context): Constraints` for direct unit testing.
  2. In `doWork()`:
     - Assemble `last_backup_targets` string recording outcome for `local`, `dropbox` (if connected), and `google_drive` (if connected).
     - Write `putString("last_backup_targets", targetsStr)` in every `prefs.edit` block that writes `last_backup_status`.

### Step 3: Update `BackupRestoreViewModel.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModel.kt`
* Changes:
  1. Add `targets: Map<String, Boolean>` to `LastBackupInfo`.
  2. In `readLastBackupInfo(p: SharedPreferences)`, parse `last_backup_targets` (with fallback inference).
  3. Implement `getAutomaticBackupTimestamp(context: Context): Long?`.
  4. Implement `restoreFromLocalBackup(context: Context)`.

### Step 4: UI Decomposition & Presentation (`RestoreTabContent.kt`, `BackupTabContent.kt`)
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupTabContent.kt` (extracted helper composable)
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/RestoreTabContent.kt` (extracted helper composable)
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt` (call-site integration & dialog wiring)
* Changes:
  1. Extract `BackupTabContent` with multi-state localized text and per-target badges.
  2. Extract `RestoreTabContent` with dedicated automatic local restore button, timestamp display, and localized restore buttons.
  3. In `ImportBackupTabsScreen.kt`:
     - Wire `showLocalRestoreConfirm` dialog calling `viewModel.restoreFromLocalBackup(context)`.
     - Wire `showGoogleDriveRestoreConfirm` dialog calling `viewModel.restoreFromGoogleDrive(context)`.
     - Retain all reflection hooks, Google Map lifecycle hooks, and contract test tokens.

### Step 5: Unit Tests for `BackupWorker`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupWorkerTest.kt`
* Changes:
  1. Add `testSchedule_whenCloudDisabled_setsNetworkTypeNotRequired` (`TST-MIG-033.9`).
  2. Add `testSchedule_whenCloudEnabled_setsNetworkTypeUnmetered` (`TST-MIG-033.10`).
  3. Add `testDoWork_persistsTargetBreakdown_onLocalSuccess` (`TST-MIG-033.11`).
  4. Add `testDoWork_persistsTargetBreakdown_onPartialSuccessAndFailure` (`TST-MIG-033.12`).

### Step 6: Unit Tests for `BackupRestoreViewModel`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreViewModelLocalRestoreTest.kt`
* Changes:
  1. Add `testLastBackupInfo_parsesTargetBreakdownMap` (`TST-MIG-033.13`).
  2. Add `testGetAutomaticBackupTimestamp_returnsTimestampWhenFileExists` (`TST-MIG-033.14`).
  3. Add `testRestoreFromLocalBackup_triggersMigrationEngineAndUpdatesUiState` (`TST-MIG-033.15`).

### Step 7: Localization Parity & Resource Verification
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* Verify 100% presence and argument parity for all 11 new keys across all 9 canonical locales.

### Step 8: Fast Targeted Unit Test Execution
* Run targeted unit test suite to verify implementation within 10–20 seconds:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.BackupWorker*" --tests "com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModel*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Stage 4: Fast targeted unit test loop on `BackupWorkerTest`, `BackupWorkerGoogleDriveTest`, and `BackupRestoreViewModelLocalRestoreTest`.
  - Stage 5: Full clean-room test suite execution (`./gradlew testDebugUnitTest`) across all modules.
  - Manual UI walkthrough verifying Light, Dark, and AMOLED themes, timestamp rendering, target badges, and confirmation dialogs.
* **Rollback**:
  - Branch isolation (`feature/ATT-4458`) ensures zero risk to `sprint/2026-43.4` until squashed integration in Stage 5.
  - Any regression during Stage 4 can be cleanly reverted via git checkout on the feature branch.
