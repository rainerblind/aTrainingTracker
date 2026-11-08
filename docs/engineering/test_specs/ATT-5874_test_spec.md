# Stage 2: Requirement & Test Specification - ATT-5874: Import & Backup UI Polish

**Ticket**: [ATT-5874](https://atrainingtracker.atlassian.net/browse/ATT-5874)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5874`  
**Requirement Mapping**: `REQ-UI-474` (*Import & Backup UI Polish, Strava Archive Tuning & Heading Deduplication*)  
**Test Spec ID**: `TST-UI-474` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-474)

### 1.1 Problem Statement & Rationale
In `ImportBackupTabsScreen.kt` and its child composables (`BackupTabContent.kt`, `RestoreTabContent.kt`), workflow and UI consistency gaps existed: (1) Strava archive picker bypassed `PreImportTuningBottomSheet`, preventing tuning review and leaking re-upload options; (2) format import and restore buttons lacked authentic brand icons; (3) incremental import in `RestoreTabContent` inappropriately used an upward backup icon (`Icons.Default.Backup`); and (4) duplicate headings were displayed immediately above identical action buttons.

### 1.2 Functional & Architectural Requirements
1. **Strava Archive Pre-Import Tuning Integration (`REQ-UI-474.1`)**: In `ImportBackupTabsScreen.kt`, selecting a valid Strava export archive (`.zip`) SHALL store the URI in `pendingStravaArchiveUri` and display `PreImportTuningBottomSheet`. `PreImportTuningBottomSheet` SHALL support `showStravaUploadOption: Boolean = true`, passing `false` for Strava archive imports to prohibit re-uploading Strava data back to Strava.
2. **Authentic Cloud Brand Iconography & Merge Icon (`REQ-UI-474.2`)**: In `FormatImportCard`, Dropbox scan buttons SHALL render leading `R.drawable.dropbox_logo_blue` with `tint = Color.Unspecified`. In `RestoreTabContent.kt`, Dropbox restore SHALL render `R.drawable.dropbox_logo_blue` and Google Drive restore SHALL render `R.drawable.ic_google_drive`, both with `tint = Color.Unspecified`. Incremental import button SHALL display `Icons.Default.CallMerge` (replacing `Icons.Default.Backup`).
3. **Heading Deduplication & Action Button Phrasing Parity (`REQ-UI-474.3`)**: In `BackupTabContent.kt`, the primary backup button SHALL use `R.string.backup_now` ("Jetzt sichern" / "Backup Now"), deduplicating it from the card title `R.string.create_backup`. The automated backup card SHALL integrate the `Switch` directly into the card header row alongside `R.string.automated_backups`, eliminating duplicate inner row text. In `RestoreTabContent.kt`, the incremental import button SHALL use `R.string.merge_from_backup_file` across all 9 supported locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`), deduplicating it from card title `R.string.import_workouts`.
4. **Contract Test Synchronization (`REQ-UI-474.4`)**: `RestoreCardContractTest.kt` SHALL verify authentic brand drawables (`dropbox_logo_blue`, `ic_google_drive`) and `CallMerge` icon contracts.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Strava Archive Tuning)**:
  * *Given* an athlete selects a Strava `.zip` export file,
  * *When* the archive is validated,
  * *Then* `PreImportTuningBottomSheet` is displayed with cluster tolerance sliders and without the Strava upload toggle (`showStravaUploadOption = false`).
* **Criterion 2 (Iconography & Merge Button)**:
  * *Given* `RestoreTabContent` and `FormatImportCard`,
  * *When* rendered,
  * *Then* Dropbox/Google Drive buttons display authentic brand drawables (`tint = Color.Unspecified`) and incremental merge displays `Icons.Default.CallMerge` with label `merge_from_backup_file`.
* **Criterion 3 (Heading Deduplication)**:
  * *Given* `BackupTabContent`,
  * *When* rendered,
  * *Then* the backup button displays `backup_now` and the automated backup switch is positioned in the card header without duplicate row text.

### 1.4 System Invariants & Chesterton's Fence
* Preserves `REQ-MIG-036` and `REQ-UI-458`: 100% 9-language translation parity without emojis; archive safety validation before tuning launch; destructive restore confirmation dialogs strictly preserved.
* 100% clean-room test pass rate.

---

## 2. Test Specification (TST-UI-474)

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-474.1]` | Localization | `TranslationParityTest`: zero missing entries across 9 locales for `merge_from_backup_file` and `backup_now` | `REQ-UI-474.3` | Specified |
| `[TST-UI-474.2]` | Contract | `RestoreCardContractTest`: verifies `dropbox_logo_blue`, `ic_google_drive`, `Icons.Default.CallMerge`, and `merge_from_backup_file` | `REQ-UI-474.2`..`4` | Specified |
| `[TST-UI-474.3]` | Layout | `BackupTabContentLayout`: verifies `R.string.backup_now` on button and header-integrated automated backup switch | `REQ-UI-474.3` | Specified |
| `[TST-UI-474.4]` | Integration | `ImportBackupTabsScreen`: verifies `pendingStravaArchiveUri` triggers `PreImportTuningBottomSheet(showStravaUploadOption = false)` | `REQ-UI-474.1` | Specified |
| `[TST-UI-474.5]` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.localization.*"` | `REQ-UI-474.1`..`4` | Specified |
