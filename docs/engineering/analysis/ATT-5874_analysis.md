# Stage 1 Analysis: ATT-5874 - Import & Backup UI Polish, Strava Archive Tuning & Heading Deduplication

**Ticket**: [ATT-5874](https://rainerblind.atlassian.net/browse/ATT-5874)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Import, Export & Backup*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5874`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
In `ImportBackupTabsScreen.kt` and its child composables (`BackupTabContent.kt`, `RestoreTabContent.kt`), four workflow and visual consistency flaws exist:
1. `pickStravaArchiveLauncher` (L248–263) bypasses `PreImportTuningBottomSheet`, immediately executing `viewModel.importStravaArchive(...)` without letting athletes review cluster sensitivity.
2. In `Import` tab, Dropbox scan buttons lack leading icons; in `Restore` tab, Dropbox and Google Drive restore buttons use generic `CloudDownload` icons instead of authentic service assets (`dropbox_logo_blue`, `ic_google_drive`).
3. In `Restore` tab, `Inkrementeller Import` uses an upward `Icons.Default.Backup` (cloud-upload) icon instead of an import/merge icon (`Icons.Default.CallMerge`).
4. Duplicate headings sit immediately above identical controls: "Backup erstellen" over "Backup erstellen", "Automatisierte Backups" over "Automatisierte Backups", and "Inkrementeller Import (Zusammenführen)" over "Inkrementeller Import (Zusammenführen)".

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: Strava bulk import was added in ATT-5679 as a specialized launcher bypassing the legacy `PreImportTuningBottomSheet` dispatch pattern. Iconography in `RestoreTabContent.kt` reused generic Material symbols (`Backup`, `CloudDownload`) from early prototypes (ATT-4664). Heading duplication resulted from reusing screen/card titles verbatim as button labels.
* **Independent Peer Review & Verification**:
  * Live code audit confirms `pickStravaArchiveLauncher` directly launches the import while all other 4 import types intercept via `pending*Uri` state.
  * Audit of `StravaBulkExportImporter.kt` confirms it persists via `LegacyImportEngine` clustering, so pre-import tuning is necessary, while Strava re-upload is prohibited (`uploadToStrava = false`), requiring `showStravaUploadOption = false`.
  * Brand asset check confirms `R.drawable.dropbox_logo_blue` and `R.drawable.ic_google_drive` exist and render with `tint = Color.Unspecified` in `AppNavigationDrawer.kt`.
  * `R.string.backup_now` ("Jetzt sichern" / "Backup Now") already exists across all 9 locales in `strings.xml`.
  * `RestoreCardContractTest.kt` explicitly asserts `Icons.Default.CloudDownload` for restore buttons (L149, L172), which must be updated as part of the side-effect audit (`Rule 68`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `pendingStravaArchiveUri` state and show `PreImportTuningBottomSheet(showStravaUploadOption = false)` before importing.
  2. Add `dropbox_logo_blue` to `FormatImportCard` Dropbox buttons, and brand icons to Dropbox/Drive restore buttons with `tint = Color.Unspecified`.
  3. Replace `Icons.Default.Backup` with `Icons.Default.CallMerge` on the incremental import button.
  4. Deduplicate headings: use `R.string.backup_now` on backup button, move automated backup switch into card header row, and add `merge_from_backup_file` button label across 9 locales.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying `StravaBulkExportImporter` parsing or SQLite transaction logic.
  - Changing Google Drive authentication or export settings screens.
* **Complexity Rating**: **2 SP (Standard)**. Multi-tab Compose presentation and string resource updates across 9 locales.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-MIG-036` (*Automated Backups & Restore*) and `REQ-UI-458` (*Format Import Cards*); establishes `REQ-MIG-054`.
* **Historical Origin & Commit Trace**: Commit `8020d902` (ATT-4664, 2026-10-22) and commit `523194a2` (ATT-5679, 2026-11-05).
* **Root Reason for Existing Formulation**: ATT-4664 standardized restore button visibility using generic Material symbols before brand icons were harmonized in Compose.
* **Preservation of Core Invariants**: 100% 9-language translation parity without emojis; unhindered incremental merge; destructive restore warnings preserved.

---

## 5. Architectural Strategy & High-Level Solution
* `ImportBackupTabsScreen.kt`:
  - `pendingStravaArchiveUri`: launches `PreImportTuningBottomSheet(showStravaUploadOption = false)`.
  - `FormatImportCard`: renders `dropbox_logo_blue` leading icon on scan button.
* `RestoreTabContent.kt`:
  - Use `Icons.Default.CallMerge` and `R.string.merge_from_backup_file` on incremental merge button.
  - Use `dropbox_logo_blue` and `ic_google_drive` with `tint = Color.Unspecified` on restore buttons.
* `BackupTabContent.kt`:
  - Card 1: button uses `R.string.backup_now` ("Jetzt sichern").
  - Card 2: integrate switch directly into header row next to title, eliminating duplicate row title.
* Tests & Governance: Update `RestoreCardContractTest.kt` assertions to brand icons and verify 9-locale parity.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Archive validation (`.zip`, `.attbackup`) remains strictly enforced before tuning sheet or import launch.
  2. Full restore destructive warnings (`MaterialTheme.colorScheme.error`) and confirmation dialogs remain active.
  3. Zero regression in WorkManager background backup scheduling or cloud upload mutex locks.
* **Risk Rating**: **LOW** (Declarative UI layout refinement and string resource alignment).
