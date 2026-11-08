# Stage 3: Implementation Plan - ATT-5874: Import & Backup UI Polish

**Ticket**: [ATT-5874](https://atrainingtracker.atlassian.net/browse/ATT-5874) | **Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5874`  
**Requirement Mapping**: `REQ-UI-474` (`REQ-UI-474.1`..`4`) | **Test Mapping**: `TST-UI-474` (`TST-UI-474.1`..`5`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
In `ImportBackupTabsScreen.kt`, `BackupTabContent.kt`, and `RestoreTabContent.kt`, four UI/UX inconsistencies exist: (1) Strava archive picker bypasses `PreImportTuningBottomSheet`, preventing clustering review and leaking the Strava upload toggle; (2) Dropbox and Google Drive buttons lack authentic brand icons; (3) incremental merge button uses an upward cloud icon (`Icons.Default.Backup`); and (4) duplicate headings appear directly above identical buttons and switches.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-474` (*Import & Backup UI Polish, Strava Archive Tuning & Heading Deduplication*)
* **Test Mapping**: `TST-UI-474` (*Import & Backup UI Polish and Contract Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Archive Security**: Extension and path traversal validations (`.zip`, `.attbackup`) remain strictly enforced before launching tuning or import.
2. **Destructive Restore Safeguards**: Full restore confirmation dialogs and error styling (`MaterialTheme.colorScheme.error`) remain active.
3. **9-Language Translation Parity**: All new/updated strings maintain 100% parity across 9 locales without inline emojis.
4. **Cloud Backup Worker**: `BackupWorker` background WorkManager triggers and cloud mutex locks remain untouched.

---

## 4. Proposed Architectural Changes
* `ImportBackupTabsScreen.kt`: Store validated Strava URI in `pendingStravaArchiveUri`, launch `PreImportTuningBottomSheet(showStravaUploadOption = false)` before import, and add `dropbox_logo_blue` to `FormatImportCard` scan buttons.
* `RestoreTabContent.kt`: Replace `Icons.Default.Backup` with `Icons.Default.CallMerge`, button label with `merge_from_backup_file`, and cloud icons with `dropbox_logo_blue` and `ic_google_drive` (`tint = Color.Unspecified`, `20.dp`).
* `BackupTabContent.kt`: Deduplicate Card 1 using `backup_now` button label; integrate `Switch` into Card 2 header row next to `automated_backups`, removing duplicate inner row text.
* **UI Consistency (Rule 23)**: Reuses `PreImportTuningBottomSheet`, `FormatImportCard`, `ElevatedCard`, `RoundedCornerShape(16.dp)`, and brand vector drawables with `tint = Color.Unspecified`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Define `merge_from_backup_file` Across All 9 Locales
* Files: `app/src/main/res/values*/strings.xml`
* Changes: Add localized `merge_from_backup_file` string across all 9 languages (`REQ-UI-474.3`).

### Step 2: Implement Pre-Import Tuning & Dropbox Icon in `ImportBackupTabsScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* Changes: Add `pendingStravaArchiveUri`, update `pickStravaArchiveLauncher`, add `showStravaUploadOption = false` to `PreImportTuningBottomSheet`, and add Dropbox brand icon to `FormatImportCard`.

### Step 3: Update Iconography & Phrasing in `RestoreTabContent.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/RestoreTabContent.kt`
* Changes: Replace `Icons.Default.Backup` with `Icons.Default.CallMerge`, button label with `merge_from_backup_file`, and cloud icons with `dropbox_logo_blue` and `ic_google_drive`.

### Step 4: Deduplicate Headings in `BackupTabContent.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupTabContent.kt`
* Changes: Use `R.string.backup_now` on backup button; integrate `Switch` into automated backups header row.

### Step 5: Update Contract Tests & Execute Verification
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/RestoreCardContractTest.kt`
* Changes: Update assertions for brand icons, `Icons.Default.CallMerge`, and `merge_from_backup_file`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted migration and localization test suite, followed by living docs governance audit (`verify_requirement_governance.py`).
* **Rollback**: Branch isolation (`feature/ATT-5874`) allows complete revert via `git restore .`.
