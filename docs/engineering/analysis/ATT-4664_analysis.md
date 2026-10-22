# Stage 1 Analysis: ATT-4664 - Unify Restore Backup card button labels, icons, and disconnected visibility

**Ticket**: [ATT-4664](https://rainerblind.atlassian.net/browse/ATT-4664)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4664`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
In the "Restore Backup" card of `RestoreTabContent.kt` (L60–210), the four restore buttons suffer from inconsistent labeling, mismatched iconography, and asymmetric disconnected visibility:
* **Label Inconsistency**: While automatic local backup and Google Drive use the pattern `"Restore from ..."`, local file and Dropbox restore use legacy technical strings (`"Local: Full Restore (Migration)"` and `"Dropbox: Full Restore (Migration)"`).
* **Icon Mismatch**: Dropbox restore uses `Icons.Default.CloudUpload` (an upload arrow) for a restore/download action, whereas Google Drive uses `Icons.Default.CloudDownload`.
* **Visibility Asymmetry**: When disconnected, Dropbox restore remains visible and dimmed (`dropboxRestoreColors`), triggering an informative dialog when clicked. In contrast, Google Drive restore is conditionally hidden (`if (isGoogleDriveConnected)`), leaving dead styling tokens (`googleDriveRestoreColors`) unexercised and creating visual layout asymmetry.

---

## 2. Root Cause Analysis (Forensic Investigation)
* **Historical Drift**: Google Drive restore was added in `ATT-1306` (Sprint `2026-41.1`) after Dropbox restore. Although `googleDriveRestoreColors` was configured for dimmed disconnected rendering (L63–72), an outer `if (isGoogleDriveConnected)` block was wrapped around the button (L188–201), hiding it completely when disconnected.
* **Icon Copy-Paste**: `RestoreTabContent.kt:182` assigned `Icons.Default.CloudUpload` to Dropbox restore, conflicting with the download/restore semantic.
* **Missing Disconnected Handler**: In `ImportBackupTabsScreen.kt:378`, `onGoogleDriveRestoreClick` directly sets `showGoogleDriveRestoreConfirm = true` without checking `isGoogleDriveConnected` or presenting `showGoogleDriveDisconnectedDialog`, unlike Dropbox (L371).
* **String Nomenclature**: `restore_from_file` and `restore_from_dropbox` were drafted during early database migration work and never aligned with the standardized `"Restore from ..."` pattern.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Harmonize `restore_from_file` (`"Restore from Local File"`) and `restore_from_dropbox` (`"Restore from Dropbox"`) across all 9 supported locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
  * Change Dropbox restore icon to `Icons.Default.CloudDownload` in `RestoreTabContent.kt`.
  * Remove `if (isGoogleDriveConnected)` gating in `RestoreTabContent.kt`, making Google Drive restore unconditionally visible and dimmed when disconnected.
  * Implement `showGoogleDriveDisconnectedDialog` in `ImportBackupTabsScreen.kt` using existing `R.string.google_drive_disconnected_status`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to backup archive extraction, validation, or database restoration engines (`MigrationEngine.kt`, `ImportEngine.kt`).
  * No changes to `BackupTabContent.kt` or backup creation flows.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-MIG-036` (clause 10: *Restore Tab Presentation & Destructive Confirmation*). Targets `RestoreTabContent.kt`, `ImportBackupTabsScreen.kt`, and `values*/strings.xml`.
* **Historical Origin & Commit Trace**: `REQ-MIG-036` established in `ATT-3472` (Sprint `2026-42.3`, commit `9fa41095`), refined in `ATT-4458` (commit `22916f21`) and `ATT-4587`.
* **Root Reason for Existing Formulation**: `if (isGoogleDriveConnected)` was a temporary safeguard during initial Google Drive integration. The presence of `googleDriveRestoreColors` / `googleDriveRestoreBorder` confirms the architectural intent was always to match Dropbox's dimmed-when-disconnected behavior.
* **Preservation of Core Invariants**: Destructive restore confirmation dialog guards (`AlertDialog`), 9-locale parity, and `MigrationEngine` invocation contracts remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution
* **Localization Parity**: Update `restore_from_file` and `restore_from_dropbox` across all 9 `strings.xml` resource files.
* **UI Symmetry in `RestoreTabContent.kt`**:
  * Replace `Icons.Default.CloudUpload` with `Icons.Default.CloudDownload` on line 182.
  * Remove `if (isGoogleDriveConnected)` check on line 188 so Google Drive button renders unconditionally, adopting `googleDriveRestoreColors` and `googleDriveRestoreBorder` when disconnected.
* **Dialog Parity in `ImportBackupTabsScreen.kt`**:
  * In `onGoogleDriveRestoreClick`, check `if (isGoogleDriveConnected) showGoogleDriveRestoreConfirm = true else showGoogleDriveDisconnectedDialog = true`.
  * Render `AlertDialog` for `showGoogleDriveDisconnectedDialog` using `R.string.google_drive` and `R.string.google_drive_disconnected_status`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Destructive full restore confirmation dialogs MUST guard actual restore execution.
  2. All 9 locales maintain 100% translation key and string parity.
  3. Clean-room unit test suite pass rate MUST remain 100%.
* **Risk Rating**: **LOW** — Purely presentation and string harmonization; zero impact on backup file format, data storage, or SQLite schemas.
