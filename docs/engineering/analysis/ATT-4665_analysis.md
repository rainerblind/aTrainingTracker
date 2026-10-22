# Stage 1 Analysis: ATT-4665 - Add granular Dropbox sync preferences and enforce in ExportManager & BackupWorker

**Ticket**: [ATT-4665](https://rainerblind.atlassian.net/browse/ATT-4665)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4665`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
While Google Drive supports fine-grained preferences in `TrainingApplication.java` (`uploadWorkoutsToGoogleDrive`, `uploadBackupToGoogleDrive`, `uploadToGoogleDriveOnlyOnWifi`, `getGoogleDriveLastSyncTimestamp`, `getGoogleDriveAccountEmail`), Dropbox currently relies solely on a single coarse `uploadToDropbox` flag. Athletes cannot independently toggle workout exports versus automated backups, enforce Wi-Fi-only transfers, or view account email and sync timestamps for Dropbox.

---

## 2. Root Cause Analysis (Forensic Investigation)
* **Missing Granular Accessors**: `TrainingApplication.java` provides granular Google Drive accessors (L708–806), but Dropbox only exposes coarse `uploadToDropbox()` (L650) and credential storage (L660–693).
* **Export Gating & Wi-Fi Enforcement**: `ExportManager.java` gates Dropbox exports solely on `uploadToDropbox()` (L149, L330) and restricts Wi-Fi only to `ExportType.GOOGLE_DRIVE` (L421).
* **Backup Gating & Constraints**: `BackupWorker.kt` (L39) and `BackupRestoreViewModel.kt` (L255) evaluate `dropboxConnected` without checking if backups are enabled (`uploadBackupToDropbox()`). `BackupWorker.buildConstraints()` (L129) unconditionally forces `NetworkType.UNMETERED` whenever cloud backup is connected rather than evaluating active provider Wi-Fi preferences.
* **Sync Timestamp Updates**: `DropboxUploader.java` and `DropboxBackupManager.kt` succeed without recording `setDropboxLastSyncTimestamp()`, unlike `GoogleDriveUploader.kt` (L102) and `GoogleDriveBackupManager.kt` (L105).
* **Account Info Scope Tolerance**: Calling `DbxClientV2.users().currentAccount.email` can throw `DbxException` if legacy OAuth tokens lack `account_info.read` scope or network is offline; this requires defensive `try/catch` to degrade gracefully (`null`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add `uploadWorkoutsToDropbox()`, `uploadBackupToDropbox()`, `uploadToDropboxOnlyOnWifi()`, `get/setDropboxLastSyncTimestamp()`, and `get/setDropboxAccountEmail()` to `TrainingApplication.java` and `AuthCredentialsStore.kt`.
  * Enforce `uploadWorkoutsToDropbox()` and `uploadToDropboxOnlyOnWifi()` in `ExportManager.java`.
  * Gate Dropbox backups on `uploadBackupToDropbox()` in `BackupWorker.kt`, `BackupRestoreViewModel.kt`, and `ImportBackupTabsScreen.kt`.
  * Enforce dynamic Wi-Fi network constraints in `BackupWorker.kt` based on active cloud backup provider preferences.
  * Update `setDropboxLastSyncTimestamp()` in `DropboxUploader.java` and `DropboxBackupManager.kt`.
  * Wrap `DbxClientV2.users().currentAccount` in defensive error handling to return `null` without failing authentication or upload.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No redesign of Dropbox OAuth PKCE authentication flow or token storage mechanism.
  * No SQLite schema alterations or modifications to `.attbackup` archive formats.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-DAT-032` (`REQ-DAT-032.1`, `REQ-DAT-032.6`) and `REQ-MIG-036` (item 11). Targets `TrainingApplication.java`, `AuthCredentialsStore.kt`, `ExportManager.java`, `BackupWorker.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`, `DropboxUploader.java`, and `DropboxBackupManager.kt`.
* **Historical Origin & Commit Trace**: `REQ-DAT-032` introduced in `ATT-4023` (commit `203baa27`, Sprint `2026-42.10`), refined in `ATT-4459` (commit `8ad50842`). `REQ-MIG-036` introduced in `ATT-3472` (commit `9fa41095`), refined in `ATT-4458` (commit `22916f21`) and `ATT-4587`.
* **Root Reason for Existing Formulation**: Initial implementations focused on introducing Dropbox as a monolithic destination before Google Drive introduced granular sync switches.
* **Preservation of Core Invariants**: Credential isolation in `auth_credentials_prefs.xml` (REQ-DAT-032.1), backward-compatible defaults (`true`), and `BackupManager.acquireLock` concurrency invariants remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution
* **Preferences & Security**: Add preference keys with default `true` to `TrainingApplication.java`. Store Dropbox email in `AuthCredentialsStore` (`KEY_DROPBOX_ACCOUNT_EMAIL`) to prevent `.attbackup` token leakage; clear on disconnect.
* **Export Pipeline**: In `ExportManager.java`, gate Dropbox exports on `uploadToDropbox() && uploadWorkoutsToDropbox()` and apply `NetworkType.UNMETERED` when `uploadToDropboxOnlyOnWifi()` is true.
* **Backup Pipeline**: In `BackupWorker.kt` and `BackupRestoreViewModel.kt`, gate Dropbox backups on `uploadToDropbox() && uploadBackupToDropbox() && readDropboxCredential() != null`. Set `NetworkType.UNMETERED` if any active cloud provider requires Wi-Fi only, `CONNECTED` if cloud active but allows cellular, or `NOT_REQUIRED` if local only.
* **Sync Auditing**: In `DropboxUploader.java` and `DropboxBackupManager.kt`, call `TrainingApplication.setDropboxLastSyncTimestamp(System.currentTimeMillis())` upon successful uploads.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Default values (`true`) ensure zero behavior breakage for existing connected Dropbox users.
  2. Account email isolated in `AuthCredentialsStore.kt` and purged on credential deletion.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **MEDIUM** — Modifies background export and automated backup dispatching, but risk is minimized by following the established Google Drive architectural pattern.
