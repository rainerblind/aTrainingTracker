# Stage 1 Analysis: ATT-4023 - [Verbesserung] [Medium] Exclude OAuth credentials from Auto Backup and .attbackup archives

**Ticket**: [ATT-4023](https://rainerblind.atlassian.net/browse/ATT-4023)  
**Sub-task**: [ATT-4256](https://rainerblind.atlassian.net/browse/ATT-4256) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Branch**: `feature/ATT-4023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Statement & Motivation

During an independent whole-app architecture, privacy, and security review (2026-10-18, branch `feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`), several high-severity data leakage vulnerabilities and attack surfaces were identified in the Android backup configuration, `.attbackup` archiving engine, and FileProvider manifest definitions:

1. **Unrestricted Android Auto Backup & Device Transfer Data Leakage (`REQ-DAT-032.1`, `REQ-DAT-032.2`)**:
   - The `<application>` tag in `AndroidManifest.xml` declared no `android:allowBackup`, `android:dataExtractionRules`, or `android:fullBackupContent` attributes.
   - In Android, `android:allowBackup` defaults to `true`. Without explicit extraction rules, Android Auto Backup uploads the entire application private storage (including default `SharedPreferences` with plaintext OAuth access and refresh tokens for Strava, Dropbox, and Google Drive) to Google Cloud backups and during device-to-device cable/cloud transfers.
   - If a user restores their device or migrates to a new phone, OAuth tokens are transferred unencrypted, creating session hijacking risks across devices and exposing credentials if cloud backups are compromised.

2. **Plaintext OAuth Token Bundling into `.attbackup` Archives (`REQ-DAT-032.3`, `REQ-DAT-032.4`)**:
   - `BackupManager.createBackup(context)` bundled the entire `shared_prefs` directory via `zipFolder(sharedPrefsDir, "shared_prefs", zos)` into `.attbackup` zip archives.
   - Because Strava tokens (`stravaToken`, `stravaRefreshToken`), Dropbox credentials (`sp_dropbox_credential`), and Google Drive tokens (`googleDriveAuthToken`) were stored in the default `SharedPreferences` file (`${applicationId}_preferences.xml`), these credentials were included in plaintext in every `.attbackup` file.
   - When users enabled automated or manual cloud backup uploads (`uploadBackupToGoogleDrive()` or `uploadToDropbox()`), their plaintext OAuth tokens for those very services were zipped and uploaded to remote cloud storage. Furthermore, moving `.attbackup` files across devices transferred active session tokens.

3. **Overly Broad `FileProvider` Exposure (`REQ-DAT-032.5`)**:
   - An obsolete class `GenericFileProvider.java` was declared in `AndroidManifest.xml` pointing to `res/xml/provider_paths.xml`.
   - `provider_paths.xml` declared `<external-path name="Workouts" path="." />`, which exposed the entire root of external storage to external applications through file URIs.
   - `file_paths.xml` (used by `androidx.core.content.FileProvider`) declared `<cache-path name="backups" path="." />`, which needlessly exposed the entire application cache directory instead of specific subfolders.
   - `GenericFileProvider` was commented as obsolete legacy code originally intended for a removed email export feature.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Credential Storage in Default `SharedPreferences`
In `TrainingApplication.java`:
- Dropbox: `SP_DROPBOX_CREDENTIAL` (`"sp_dropbox_credential"`) written directly to `cSharedPreferences` (lines 645-668).
- Google Drive: `SP_GOOGLE_DRIVE_ACCOUNT_EMAIL` (`"sp_google_drive_account_email"`) and `SP_GOOGLE_DRIVE_AUTH_TOKEN` (`"googleDriveAuthToken"`) written to `cSharedPreferences` (lines 713-741).
- Strava: `SP_STRAVA_TOKEN` (`"stravaToken"`), `SP_STRAVA_REFRESH_TOKEN` (`"stravaRefreshToken"`), `SP_STRAVA_TOKEN_EXPIRES_AT` (`"stravaTokenExpiresAt"`), `SP_STRAVA_ATHLETE_ID` (`"stravaAthleteId"`), `SP_STRAVA_OAUTH_STATE` (`"stravaOAuthState"`), and `SP_STRAVA_OAUTH_STATE_TIMESTAMP` (`"stravaOAuthStateTimestamp"`) stored in `cSharedPreferences` (lines 794-850).

Because all credentials resided in the single default `SharedPreferences` file alongside standard user configuration (e.g. `unit_system`, `map_type`, `sport_type`), any export or backup mechanism that included user preferences inevitably bundled sensitive authentication credentials.

### 2.2 Lack of Android Backup Rules
- Android 12+ (API 31+) uses `android:dataExtractionRules` with XML elements `<cloud-backup>` and `<device-transfer>`.
- Android 6.0 to 11 (API 23–30) uses `android:fullBackupContent` with XML element `<full-backup-content>`.
- Neither file existed in `res/xml/`. Consequently, the Android OS backed up all files in `databases/`, `shared_prefs/`, `files/`, and `datastore/` indiscriminately.

### 2.3 Non-Filtering `.attbackup` Archiving and Restoration
In `BackupManager.kt`:
```kotlin
val sharedPrefsDir = File(dataDir, "shared_prefs")
if (sharedPrefsDir.exists()) zipFolder(sharedPrefsDir, "shared_prefs", zos)
```
`zipFolder` recursed through `sharedPrefsDir` without filtering sensitive preferences files. Similarly, `MigrationEngine.kt` unpacked whatever `shared_prefs` existed in the archive directly into production app storage without stripping credential keys.

### 2.4 FileProvider Attack Surface Audit
A project-wide search for `GenericFileProvider` and `provider_paths` confirmed:
- Zero references in Java, Kotlin, or build scripts outside of `AndroidManifest.xml` and `GenericFileProvider.java`.
- `file_paths.xml` only requires:
  - `<cache-path name="my_cache_images" path="images/" />` (used by `ShareUtils.kt` to share summary PNGs).
  - `<files-path name="internal_backups" path="backups/" />` (used by `BackupRestoreViewModel.kt` to share `.attbackup` files).
  - `<cache-path name="backups" path="." />` was redundant and exposed the root cache directory.

---

## 3. Proposed Technical Architecture & Remediation

### 3.1 Dedicated Preferences File for Authentication Credentials (`AuthCredentialsStore.kt`)
1. Create a specialized, encapsulated store: `AuthCredentialsStore.kt` operating on a dedicated private preferences file: `auth_credentials_prefs.xml` (`context.getSharedPreferences("auth_credentials_prefs", Context.MODE_PRIVATE)`).
2. **Transparent One-Time Migration**:
   - On application startup or when `AuthCredentialsStore` is accessed, check if legacy credential keys exist in default `SharedPreferences`.
   - If present, atomically copy `SP_STRAVA_TOKEN`, `SP_STRAVA_REFRESH_TOKEN`, `SP_STRAVA_TOKEN_EXPIRES_AT`, `SP_STRAVA_ATHLETE_ID`, `SP_STRAVA_OAUTH_STATE`, `SP_STRAVA_OAUTH_STATE_TIMESTAMP`, `SP_GOOGLE_DRIVE_ACCOUNT_EMAIL`, `SP_GOOGLE_DRIVE_AUTH_TOKEN`, and `SP_DROPBOX_CREDENTIAL` into `auth_credentials_prefs` and remove them immediately from default `SharedPreferences`.
3. **Redirect Accessors in `TrainingApplication.java`**:
   - Route `getStravaAccessToken()`, `setStravaAccessToken()`, `getStravaRefreshToken()`, `storeGoogleDriveCredential()`, `readDropboxCredential()`, etc., through `AuthCredentialsStore`.
   - General user preferences (e.g. `uploadToStrava`, `uploadToGoogleDrive`, `uploadToDropbox`, `googleDriveOnlyWifi`) remain in default `SharedPreferences` so user settings survive restores.

### 3.2 Android Auto Backup & Device Transfer Rules (`REQ-DAT-032.1`, `REQ-DAT-032.2`)
1. In `AndroidManifest.xml`, configure `<application>`:
   ```xml
   android:allowBackup="true"
   android:dataExtractionRules="@xml/data_extraction_rules"
   android:fullBackupContent="@xml/full_backup_content"
   ```
2. Create `res/xml/data_extraction_rules.xml` (API 31+):
   - Exclude `auth_credentials_prefs.xml` from both `<cloud-backup>` and `<device-transfer>`.
3. Create `res/xml/full_backup_content.xml` (API 23–30):
   - Exclude `auth_credentials_prefs.xml` using `<exclude domain="sharedpref" path="auth_credentials_prefs.xml" />`.

### 3.3 Exclude Credentials from `.attbackup` Creation and Restoration (`REQ-DAT-032.3`, `REQ-DAT-032.4`)
1. In `BackupManager.kt`:
   - When bundling `shared_prefs`, explicitly exclude `auth_credentials_prefs.xml` (and any related temp/lock files).
   - Ensure migration has executed so default `SharedPreferences` contains zero credential keys.
2. In `MigrationEngine.kt`:
   - During restore extraction, discard `auth_credentials_prefs.xml` if present in legacy backup archives.
   - Sanitize restored default `SharedPreferences` by wiping any legacy credential keys.
   - Cleanly clear or reset `AuthCredentialsStore` so the restored installation requires a clean one-time re-authentication, satisfying Architectural Guardrail Option A.

### 3.4 FileProvider Cleanup & Hardening (`REQ-DAT-032.5`)
1. Delete `GenericFileProvider.java` and `res/xml/provider_paths.xml`.
2. Remove `<provider android:name=".trainingtracker.exporter.GenericFileProvider" ...>` from `AndroidManifest.xml`.
3. In `res/xml/file_paths.xml`:
   - Remove `<cache-path name="backups" path="." />`.
   - Retain strictly scoped paths: `<cache-path name="my_cache_images" path="images/" />` and `<files-path name="internal_backups" path="backups/" />`.

---

## 4. Chesterton's Fence & Invariant Archaeology

1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-DAT-032`, extending `REQ-DAT-031` (*Atomic Concurrent-Safe Database Backup Snapshots*), `REQ-MIG-040` (*Secure Zip Path Traversal Protection*), and `REQ-SEC-041` (*Strava Credential Hardening*).
   - Targets `AndroidManifest.xml`, `TrainingApplication.java`, `BackupManager.kt`, `MigrationEngine.kt`, `file_paths.xml`, and creates `AuthCredentialsStore.kt`, `data_extraction_rules.xml`, and `full_backup_content.xml`.
2. **Historical Origin & Commit Trace**:
   - Discovered during independent whole-app architecture and security audit (2026-10-18, branch `feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`).
   - `GenericFileProvider` was created in 2018 for email export and never cleaned up after email export was removed.
   - Auto Backup was left unconfigured from the initial project creation, inheriting Android's permissive defaults.
3. **Root Reason for Existing Formulation**:
   - Early Android apps stored all preferences in `PreferenceManager.getDefaultSharedPreferences()` for simplicity.
   - Auto Backup rules were introduced in Android 6.0 and revised in Android 12, but older apps required explicit XML descriptors that were omitted.
4. **Preservation of Core Invariants**:
   - All user workouts, sensors, zones, tracks, and application settings must be fully backed up and restored across devices without data loss.
   - Cloud integration settings (e.g. WiFi-only upload, auto-sync flags) are preserved, but credentials cleanly require a one-time re-authentication on restored devices.
   - Backward compatibility: Existing installations must seamlessly migrate tokens into `auth_credentials_prefs.xml` on first launch without requiring the user to log in again.
   - The `.attbackup` archive format remains fully compatible with `SecureZipExtractor`.

---

## 5. Scope & Bounding (ATT-1250)

### In-Scope
- Implementing `AuthCredentialsStore.kt` for dedicated, isolated credential storage with transparent one-time legacy migration.
- Redirecting Strava, Google Drive, and Dropbox credential accessors in `TrainingApplication.java` to `AuthCredentialsStore`.
- Creating `data_extraction_rules.xml` and `full_backup_content.xml` and referencing them in `AndroidManifest.xml`.
- Excluding `auth_credentials_prefs.xml` from `.attbackup` bundling in `BackupManager.kt`.
- Stripping legacy credential keys and ignoring `auth_credentials_prefs.xml` during restore in `MigrationEngine.kt`.
- Deleting `GenericFileProvider.java` and `provider_paths.xml`, and narrowing `file_paths.xml`.
- Unit tests verifying credential isolation, migration, exclusion from backup archives, restore sanitization, and FileProvider hardening.

### Out-of-Scope
- Implementing Android Keystore / biometric crypto for local tokens (ticket explicitly mandates Option A: credential exclusion policy; avoids deprecated `EncryptedSharedPreferences` pitfalls).
- Modifying SQLite database schemas or workout data models.
- Changing OAuth authentication endpoints or scopes for Strava, Dropbox, or Google Drive.
