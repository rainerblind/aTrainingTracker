# Stage 3: Implementation Plan - ATT-4664: Unify Restore Backup card button labels, icons, and disconnected visibility

**Ticket**: [ATT-4664](https://rainerblind.atlassian.net/browse/ATT-4664)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4664`  
**Requirement Mapping**: `REQ-MIG-036` (`REQ-MIG-036.13a`–`d`)  
**Test Mapping**: `TST-MIG-045` (`TST-MIG-045.1`–`4`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background
In `RestoreTabContent.kt`, the four restore buttons feature asymmetric labeling (`"Local: Full Restore (Migration)"` and `"Dropbox: Full Restore (Migration)"` vs `"Restore from ..."`), inverted Dropbox iconography (`Icons.Default.CloudUpload` instead of `CloudDownload`), and disparate disconnected visibility (Dropbox is always visible and dimmed, whereas Google Drive is conditionally hidden via `if (isGoogleDriveConnected)`). This plan harmonizes button nomenclature, icons, and visibility across all providers and locales.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-MIG-036` (`REQ-MIG-036.13a`: nomenclature across 9 locales; `13b`: Dropbox icon; `13c`: unconditional Google Drive visibility; `13d`: disconnected dialog dispatch).
* **Test Mapping**: `TST-MIG-045` (`TST-MIG-045.1`: 9-language parity; `TST-MIG-045.2`: RestoreTabContent layout & icon contract; `TST-MIG-045.3`: ImportBackupTabsScreen dialog contract; `TST-MIG-045.4`: clean-room regression).

---

## 3. System Invariants & Preserved Behavior
1. **Destructive Guard**: Destructive full restore confirmation dialogs (`AlertDialog`) remain strictly enforced before executing `MigrationEngine.performFullRestore()`.
2. **Localization Parity**: 100% key and semantic parity across all 9 supported locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
3. **Data & Schema Integrity**: Zero alterations to SQLite schemas, `.attbackup` archive formats, or restore engines.
4. **Clean-Room Test Pass Rate**: Full test suite pass rate under `./gradlew testDebugUnitTest` remains 100%.

---

## 4. Proposed Architectural Changes
* **Localization (`res/values*/strings.xml`)**:
  * Harmonize `restore_from_file` to `"Restore from Local File"` / localized equivalents.
  * Harmonize `restore_from_dropbox` to `"Restore from Dropbox"` / localized equivalents.
* **Icon & Visibility (`RestoreTabContent.kt`)**:
  * Replace `Icons.Default.CloudUpload` with `Icons.Default.CloudDownload` on line 182.
  * Remove `if (isGoogleDriveConnected)` check (L188) to render Google Drive button unconditionally using `googleDriveRestoreColors` and `googleDriveRestoreBorder`.
* **Disconnected Dialog Dispatch (`ImportBackupTabsScreen.kt`)**:
  * Add `var showGoogleDriveDisconnectedDialog by remember { mutableStateOf(false) }`.
  * Update `onGoogleDriveRestoreClick` callback: if connected, set `showGoogleDriveRestoreConfirm = true`; else set `showGoogleDriveDisconnectedDialog = true`.
  * Render `AlertDialog` for `showGoogleDriveDisconnectedDialog` with `R.string.google_drive` and `R.string.google_drive_disconnected_status`.
* **UI Consistency**: Reuses existing `OutlinedButton`, `AlertDialog`, `googleDriveRestoreColors`, `MaterialTheme.colorScheme`, and standard 8.dp/16.dp spacing scale (`docs/design_guidelines.md` §5). Zero new one-off tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Verification (Rule 4)
* Verify exit code 0 on `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>`.

### Step 2: 9-Locale String Harmonization
* **Files**: `app/src/main/res/values*/strings.xml` (all 9 locales).
* **Changes**: Update `restore_from_file` and `restore_from_dropbox` to canonical `"Restore from ..."` translations.

### Step 3: Icon & Unconditional Visibility in RestoreTabContent
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/RestoreTabContent.kt`
* **Changes**: Change Dropbox icon to `Icons.Default.CloudDownload`; remove `if (isGoogleDriveConnected)` condition.

### Step 4: Disconnected Google Drive Dialog in ImportBackupTabsScreen
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* **Changes**: Add `showGoogleDriveDisconnectedDialog` state, wire `onGoogleDriveRestoreClick`, and render `AlertDialog`.

### Step 5: Unit & Contract Test Implementation
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/RestoreCardContractTest.kt`
* **Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.RestoreCardContractTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted contract test (`TST-MIG-045.2`–`3`), `TranslationParityTest` (`TST-MIG-045.1`), full `./gradlew testDebugUnitTest` (`TST-MIG-045.4`).
* **Rollback**: Clean git branch isolation on `feature/ATT-4664`; discard via `git checkout -- . && git clean -fd`.
