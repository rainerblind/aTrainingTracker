# Stage 1 Analysis: ATT-4666 - Unify DropboxSettingsDialog and GoogleDriveSettingsDialog with shared layout, granular toggles, backup interval, and last backup/sync status

**Ticket**: [ATT-4666](https://rainerblind.atlassian.net/browse/ATT-4666)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4666`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
Currently, `DropboxSettingsDialog.kt` and `GoogleDriveSettingsDialog.kt` present divergent UX, asymmetric configuration granularity, and duplicated layout structures:
* `DropboxSettingsDialog` unconditionally exposes only the master `automated_backups` switch and `backup_interval_days` dropdown (even when disconnected), lacking `Upload Workouts`, `Upload Backups`, `Wi-Fi Only`, account email display, and `Last Sync` / `Last Backup` status timestamps.
* `GoogleDriveSettingsDialog` exposes `Upload Workouts`, `Upload Backups`, `Wi-Fi Only`, account email, and `Last Sync` (and hides switches behind a connect prompt when disconnected), but lacks the `Backup Interval` dropdown and `Last Backup` status timestamp.
* This violates `docs/design_guidelines.md` §3.4 (Rule 62: Multi-Provider Cloud / Sync UI & Capability Symmetry) and `docs/coding_standards.md` §1.1 (Rule 36: DRY).

---

## 2. Root Cause Analysis (Forensic Investigation)
* **Divergent Evolution**: Dropbox was originally built as a legacy full-screen backup toggle (`CloudUploadFragment` -> `DropboxSettingsDialog`), while Google Drive was added in `ATT-1306` (`REQ-DAT-020`) as an activity export and database backup service with OAuth2 account identity and Wi-Fi restriction.
* **Component Duplication**: Both dialogs implement parallel connection headers (`DropboxConnectionHeader.kt`, `GoogleDriveConnectionHeader.kt`), switch rows, and dismiss/save actions instead of reusing a shared parameterized layout.
* **Backend Readiness (`ATT-4665`)**: Granular Dropbox preferences (`uploadWorkoutsToDropbox`, `uploadBackupToDropbox`, `uploadToDropboxOnlyOnWifi`, `getDropboxLastSyncTimestamp`, `getDropboxAccountEmail`) were added to `TrainingApplication.java` in `ATT-4665`, but the UI in `DropboxSettingsDialog.kt` was not yet connected to them.
* **Missing Google Drive Backup Interval**: `GoogleDriveSettingsDialog.kt` toggles `uploadBackupToGoogleDrive` but lacks `backup_interval_days` selection, leaving athletes unable to adjust periodic backup intervals from the Google Drive dialog.
* **Missing Dropbox Status & Disconnected Prompt**: `DropboxSettingsDialog` renders config switches when disconnected, and lacks `dropbox_connect_prompt`, account email display, and last sync/backup status text.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Extract shared, reusable Compose components in `ui.settings.cloud` (`CloudConnectionHeader.kt`, `CloudProviderSettingsContent.kt`) per Rule 36 and Rule 62.
  * Delegate `DropboxConnectionHeader` and `GoogleDriveConnectionHeader` to `CloudConnectionHeader`, preserving `ComposeSafePainterContractTest.kt`.
  * Unify `DropboxSettingsDialog` and `GoogleDriveSettingsDialog` to use `CloudProviderSettingsContent`:
    - *When Disconnected*: Show connection header + localized connect prompt (`dropbox_connect_prompt` / `google_drive_connect_prompt`), suppressing switches.
    - *When Connected*: Show connection header with account email + Disconnect button; `Upload Workouts` switch; `Upload Backups` switch; conditional `Backup Interval` selector (`backup_interval_days`: 1, 3, 7, 30 days) when `Upload Backups` is enabled; `Wi-Fi Only` switch; and `Last Sync` + `Last Backup` status fields at dialog bottom.
    - *On Save*: Commit provider-specific preferences and `backup_interval_days`, and reschedule `BackupWorker.schedule(context)`.
  * Add `dropbox_connect_prompt`, `upload_workouts_to_dropbox_summary`, `upload_backup_to_dropbox_summary`, and `last_backup_never` across all 9 supported locales.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to OAuth authentication protocols (Dropbox PKCE or Google Play Services Sign-In).
  * No modification to background export pipelines, `BackupWorker` scheduling logic, or SQLite schema.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-430` under Epic `ATT-355`, refining and unifying `REQ-UI-152` (`DropboxSettingsDialog.kt`) and `REQ-DAT-020.6` (`GoogleDriveSettingsDialog.kt`). Aligns with `REQ-DAT-032.7`.
* **Historical Origin & Commit Trace**: `REQ-UI-152` in Sprint 2026-40.4 (commit `d93fa11b`), refined in `ATT-4588` (commit `ce9a4913`). `REQ-DAT-020` in Sprint 2026-41.1 (commit `624b5d2e`). Backend accessors added in `ATT-4665` (commit `dfdc602c`).
* **Root Reason for Existing Formulation**: Independent implementation cycles created divergent UI surfaces before backend capabilities were unified.
* **Preservation of Core Invariants**: OAuth PKCE redirect (`db-iknmdmr31sf64r0`), Google Sign-In `drive.file` scope, credential isolation in `auth_credentials_prefs.xml`, `BackupWorker` scheduling semantics, 9-language translation parity, and test contracts (`ComposeSafePainterContractTest`, `DropboxSettingsDialogTest`) remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution
* **Shared UI Components (`ui.settings.cloud`)**:
  * `CloudConnectionHeader.kt`: Parameterized surface with connection status text, account email, brand-styled connect button (with brand icon) and error-styled disconnect button.
  * `CloudProviderSettingsContent.kt`: Parameterized content containing header, loading indicator, error message, disconnected prompt, and connected synchronization switches (`Upload Workouts`, `Upload Backups`, nested `Backup Interval` `DropdownSelector`, `Wi-Fi Only`, `Last Sync` and `Last Backup` status).
* **Dialog Refactoring**:
  * Refactor `DropboxSettingsDialog.kt` and `GoogleDriveSettingsDialog.kt` to bind to `CloudProviderSettingsContent`, managing local state and committing to `TrainingApplication` / `SharedPreferences` + `BackupWorker.schedule(context)` on save.
* **Localization Parity**: Define missing strings with 100% parity across 9 languages, validated via `TranslationParityTest`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Complete capability and layout symmetry across Dropbox and Google Drive dialogs (Rule 62).
  2. Zero breakage of `ComposeSafePainterContractTest.kt` or `DropboxSettingsDialogTest.kt`.
  3. Parent ticket Human Decision Gate strictly enforced.
* **Risk Rating**: **MEDIUM** — Touches user-visible cloud synchronization settings for both cloud providers, but mitigated by pure UI refactoring backed by established, tested `TrainingApplication` preference APIs.
