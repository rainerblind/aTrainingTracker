# Stage 3: Implementation Plan - ATT-4666: Unify DropboxSettingsDialog and GoogleDriveSettingsDialog

**Ticket**: [ATT-4666](https://rainerblind.atlassian.net/browse/ATT-4666)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4666`  
**Requirement Mapping**: `REQ-UI-430` (*Multi-Provider Cloud Settings UI Harmonization*)  
**Test Mapping**: `TST-UI-395` (`TST-UI-395.1`–`5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background
`DropboxSettingsDialog` and `GoogleDriveSettingsDialog` currently present divergent layouts and asymmetric configuration controls: Dropbox exposes only master automated backups and backup interval (even when disconnected) and lacks workout exports, Wi-Fi only, email, and sync/backup timestamps; Google Drive exposes granular toggles and hides them behind a connect prompt when disconnected, but lacks the backup interval dropdown and last backup timestamp. This plan extracts shared composables in `ui.settings.cloud` to establish 100% layout and functional symmetry across both providers per Rule 36 and Rule 62.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-430` (`REQ-UI-430.1`–`6`, refining `REQ-UI-152` and `REQ-DAT-020.6`, aligning with `REQ-DAT-032.7`)
* **Test Mapping**: `TST-UI-395` (`TST-UI-395.1`–`5`, `DropboxSettingsDialogTest`, `GoogleDriveSettingsDialogTest`, `ComposeSafePainterContractTest`, `TranslationParityTest`)

---

## 3. System Invariants & Preserved Behavior
1. **OAuth Protocols**: Dropbox PKCE (`db-iknmdmr31sf64r0`) and Google Sign-In `drive.file` scope remain 100% intact.
2. **Security & Credential Isolation**: OAuth tokens and account emails reside strictly in `AuthCredentialsStore.kt` (`auth_credentials_prefs.xml`) and are purged on disconnect.
3. **Contract Test Integrity**: `ComposeSafePainterContractTest.kt` asserting `safePainterResource` on `DropboxConnectionHeader.kt` and `DropboxSettingsDialog.kt` remains completely green.
4. **Rescheduling Invariant**: Saving staged preferences commits `backup_interval_days` and reschedules `BackupWorker.schedule(context)`.

---

## 4. Proposed Architectural Changes
* **Shared UI Composables (`com.atrainingtracker.trainingtracker.ui.settings.cloud`)**:
  * `CloudConnectionHeader.kt`: Surface card with connection status text, account email, brand-styled connect button (with brand icon), and error-styled disconnect button.
  * `CloudProviderSettingsContent.kt`: Parameterized content containing header, loading indicator, error banner, disconnected connect prompt, and connected switches (`Upload Workouts`, `Upload Backups`, conditional `Backup Interval` `DropdownSelector`, `Wi-Fi Only`, `Last Sync` and `Last Backup` timestamps).
* **Existing Header Delegation**:
  * `DropboxConnectionHeader.kt` & `GoogleDriveConnectionHeader.kt`: Delegate to `CloudConnectionHeader`, preserving signatures and `safePainterResource` contracts.
* **Dialog Integration (`DropboxSettingsDialog.kt`, `GoogleDriveSettingsDialog.kt`)**:
  * Bind to `CloudProviderSettingsContent`. On save, commit provider preferences and `backup_interval_days`, and reschedule `BackupWorker`.
* **UI Consistency (Rule 23)**:
  * Reference screen: `GoogleDriveSettingsDialog.kt` / `DropboxSettingsDialog.kt`.
  * Reused components: `AppBottomSheetContent`, `AppDialogActions.SaveCancel`, `DropdownSelector`, `Switch`.
  * Theme tokens: `RoundedCornerShape(12.dp)`, `surfaceContainerLow`, `TTAlpha.Subtle`, `TTColor.ConnectionConnected`, `TTColor.DropboxBlue`. No new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Pre-Check (Rule 4)
* Verify exit code 0 via `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>`.

### Step 2: 9-Language String Resources & Parity
* **Files**: `res/values*/strings.xml` (all 9 locales)
* **Changes**: Add `dropbox_connect_prompt`, `upload_workouts_to_dropbox_summary`, `upload_backup_to_dropbox_summary`, `last_backup_never`.

### Step 3: Shared Cloud UI Components (`ui.settings.cloud`)
* **Files**: `CloudConnectionHeader.kt`, `CloudProviderSettingsContent.kt`
* **Changes**: Author reusable connection card and settings content with disconnected prompt and connected switches/interval/footers.

### Step 4: Existing Header Compatibility Delegation
* **Files**: `DropboxConnectionHeader.kt`, `GoogleDriveConnectionHeader.kt`
* **Changes**: Delegate to `CloudConnectionHeader`, keeping `safePainterResource` for `ComposeSafePainterContractTest`.

### Step 5: Refactor Dropbox & Google Drive Settings Dialogs
* **Files**: `DropboxSettingsDialog.kt`, `GoogleDriveSettingsDialog.kt`
* **Changes**: Wire `CloudProviderSettingsContent`, stage granular preferences + `backup_interval_days`, and reschedule `BackupWorker` on save.

### Step 6: Automated Unit & Contract Test Verification
* **Files**: `DropboxSettingsDialogTest.kt` (`TST-UI-395.1`), `GoogleDriveSettingsDialogTest.kt` (`TST-UI-395.2`), `ComposeSafePainterContractTest.kt` (`TST-UI-395.3`), `TranslationParityTest.kt` (`TST-UI-395.4`)
* **Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ComposeSafePainterContractTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted Robolectric and contract test suite execution followed by full `./gradlew testDebugUnitTest` and governance check in Stage 5.
* **Rollback**: Clean branch isolation on `feature/ATT-4666`; changes discardable via `git checkout -- . && git clean -fd`.
