# Stage 3: Implementation Plan - ATT-4023: Exclude OAuth credentials from Auto Backup and .attbackup archives

**Ticket**: [ATT-4023](https://rainerblind.atlassian.net/browse/ATT-4023)  
**Sub-task**: [ATT-4258](https://rainerblind.atlassian.net/browse/ATT-4258) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-DAT-032` (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and `.attbackup` Credential Exclusion Policy*)  
**Test Mapping**: `TST-DAT-027` (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and `.attbackup` Credential Exclusion Verification Suite*)  
**Branch**: `feature/ATT-4023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

During an independent whole-app architecture, privacy, and security review (2026-10-18, branch `feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`), several high-severity data leakage vulnerabilities and attack surfaces were identified:
1. **Unrestricted Android Auto Backup & Device Transfer**: The `<application>` tag in `AndroidManifest.xml` lacked `android:allowBackup`, `android:dataExtractionRules`, and `android:fullBackupContent`. By default, Android backs up all private application files—including default `SharedPreferences` containing plaintext OAuth tokens for Strava, Google Drive, and Dropbox—to cloud backups and device-to-device transfers.
2. **Plaintext OAuth Token Bundling in `.attbackup` Archives**: `BackupManager.createBackup(context)` bundled the entire `shared_prefs` directory without filtering. As a result, plaintext OAuth access and refresh tokens were packaged into `.attbackup` zip archives and uploaded to remote cloud storage or transferred across devices.
3. **Overly Broad `FileProvider` Exposure**: `GenericFileProvider` was an obsolete, unreferenced class declaring `<external-path name="Workouts" path="." />` in `provider_paths.xml`, exposing all external storage. Furthermore, `file_paths.xml` declared `<cache-path name="backups" path="." />`, unnecessarily exposing the entire cache directory.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-032` (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and `.attbackup` Credential Exclusion Policy*)
  * `REQ-DAT-032.1`: Isolated Authentication Credentials Store & Transparent Legacy Migration (`AuthCredentialsStore.kt`)
  * `REQ-DAT-032.2`: Android Auto Backup & Device Transfer XML Rules (`data_extraction_rules.xml`, `full_backup_content.xml`, `AndroidManifest.xml`)
  * `REQ-DAT-032.3`: Exclusion of Credentials from `.attbackup` Archive Creation (`BackupManager.kt`)
  * `REQ-DAT-032.4`: Restore Sanitization & Clean Re-Authentication Invariant (`MigrationEngine.kt`)
  * `REQ-DAT-032.5`: FileProvider Attack Surface Elimination & Scoping (`file_paths.xml`, deletion of `GenericFileProvider.java` and `provider_paths.xml`)
* **Test Mapping**: `TST-DAT-027` (Test cases `TST-DAT-027.1` through `TST-DAT-027.7`)

---

## 3. System Invariants & Preserved Behavior

1. **User Data Sovereignty**: All workouts, tracks, laps, samples, sensors, zones, and application settings must be fully backed up and restored across devices without data loss.
2. **Backward-Compatible Migration**: Existing installations must seamlessly migrate credentials from default `SharedPreferences` to `auth_credentials_prefs.xml` on first launch without requiring existing users to re-login.
3. **Clean Re-Authentication on Restore (Option A)**: After a full restore or device transfer, athlete data is preserved, but cloud services cleanly report disconnected and require a one-time re-authentication, guaranteeing tokens cannot leak across devices.
4. **No Deprecated EncryptedSharedPreferences**: Avoid `androidx.security:security-crypto` on general preference files that participate in `.attbackup`.
5. **Human Gate Invariance**: Terminal completion of the parent ticket remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. UI Consistency & Visual Standards (Rule 23)

- **UI Consistency**: **N/A**
  - This ticket implements internal security storage partitioning, XML backup descriptors, archive filtering, and FileProvider hardening.
  - Zero UI screens, composables, themes, colors, or user-facing strings are modified.

---

## 5. Architectural Decomposition & Component Design

```
+-----------------------------------------------------------------------------------+
|                                 Application Storage                                |
|                                                                                   |
|  [default_preferences.xml]                             [auth_credentials_prefs.xml]|
|  - unit_system, sport_type, ...                       - stravaToken               |
|  - uploadToStrava (boolean)                           - stravaRefreshToken        |
|  - uploadToGoogleDrive (boolean)                      - stravaAthleteId           |
|  - uploadToDropbox (boolean)                          - googleDriveAuthToken      |
|  (Zero plaintext tokens/secrets)                      - dropboxCredential         |
+--------------------------+-------------------------------------+------------------+
                           |                                     |
             +-------------+-------------+                       |
             |                           |                       | EXCLUDED!
             v                           v                       v
+--------------------------+  +--------------------------+  +-----------------------+
|   Android Auto Backup    |  |   .attbackup Archive     |  | Excluded from:        |
|  - Cloud Backup          |  |  BackupManager.kt        |  | 1. Auto Backup Cloud  |
|  - Device Transfer       |  |  - databases/            |  | 2. Device Transfer    |
|  (data_extraction_rules) |  |  - shared_prefs/         |  | 3. .attbackup zip     |
|  (full_backup_content)   |  |    (auth_credentials     |  | 4. Cloud Zip Uploads  |
|                          |  |     filtered out!)       |  |                       |
+--------------------------+  +--------------------------+  +-----------------------+
```

---

## 6. Atomic Implementation Steps

### Step 1: Create `AuthCredentialsStore.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStore.kt`
- Encapsulate access to `context.getSharedPreferences("auth_credentials_prefs", Context.MODE_PRIVATE)`.
- Define keys:
  - Strava: `SP_STRAVA_TOKEN`, `SP_STRAVA_REFRESH_TOKEN`, `SP_STRAVA_TOKEN_EXPIRES_AT`, `SP_STRAVA_ATHLETE_ID`, `SP_STRAVA_OAUTH_STATE`, `SP_STRAVA_OAUTH_STATE_TIMESTAMP`
  - Google Drive: `SP_GOOGLE_DRIVE_ACCOUNT_EMAIL`, `SP_GOOGLE_DRIVE_AUTH_TOKEN`
  - Dropbox: `SP_DROPBOX_CREDENTIAL`
- Implement `fun migrateLegacyCredentials(context: Context)`:
  - Inspect `PreferenceManager.getDefaultSharedPreferences(context)`.
  - Atomically copy any existing legacy keys to `auth_credentials_prefs` and purge them from default `SharedPreferences`.
- Implement strongly typed getters, setters, and removers for Strava, Google Drive, and Dropbox credentials.
- Implement `fun clearAllCredentials(context: Context)` and `fun clearStravaCredentials(context: Context)`.

### Step 2: Update `TrainingApplication.java`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
- In `onCreate()`, invoke `AuthCredentialsStore.migrateLegacyCredentials(this);`
- Redirect credential accessors:
  - `storeDropboxCredential`, `readDropboxCredential`, `deleteDropboxCredential`
  - `storeGoogleDriveCredential`, `getGoogleDriveAccountEmail`, `getGoogleDriveAuthToken`, `deleteGoogleDriveCredential`
  - `getStravaAccessToken`, `setStravaAccessToken`, `getStravaRefreshToken`, `setStravaRefreshToken`, `getStravaTokenExpiresAt`, `setStravaTokenExpiresAt`, `deleteStravaToken`, `injectMockStravaAccount`
- Keep non-sensitive user settings (`uploadToStrava`, `uploadToGoogleDrive`, `uploadToDropbox`, `googleDriveOnlyWifi`, etc.) in default `SharedPreferences`.

### Step 3: Update `StravaOAuthStateManager.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthStateManager.kt`
- Read and write `SP_STRAVA_OAUTH_STATE` and `SP_STRAVA_OAUTH_STATE_TIMESTAMP` from `AuthCredentialsStore` so ephemeral OAuth states are never written to default `SharedPreferences`.

### Step 4: Create Android Auto Backup XML Rules & Update `AndroidManifest.xml`
- Files:
  - `app/src/main/res/xml/data_extraction_rules.xml` (exclude `auth_credentials_prefs.xml` from `<cloud-backup>` and `<device-transfer>`)
  - `app/src/main/res/xml/full_backup_content.xml` (exclude `auth_credentials_prefs.xml` from `<full-backup-content>`)
  - `app/src/main/AndroidManifest.xml` (add attributes `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, `android:fullBackupContent="@xml/full_backup_content"`, and remove `<provider android:name=".trainingtracker.exporter.GenericFileProvider" ...>`)

### Step 5: FileProvider Cleanup & Hardening
- Delete `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/GenericFileProvider.java`.
- Delete `app/src/main/res/xml/provider_paths.xml`.
- Update `app/src/main/res/xml/file_paths.xml`: remove `<cache-path name="backups" path="." />`.

### Step 6: Update `BackupManager.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt`
- In `createBackup(context)`: ensure `AuthCredentialsStore.migrateLegacyCredentials(context)` has executed.
- In `zipFolder(folder, internalPath, zos, includeWal)`: when archiving `shared_prefs`, filter out `auth_credentials_prefs.xml` and any associated temp files.

### Step 7: Update `MigrationEngine.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/MigrationEngine.kt`
- In `restoreFromBackup()`:
  - If a legacy archive contains `auth_credentials_prefs.xml`, discard it during restore staging.
  - Sanitize restored default `SharedPreferences`: strip any legacy token keys.
  - Clear `AuthCredentialsStore.clearAllCredentials(context)` so restored installation cleanly requires one-time re-authentication.

### Step 8: Create Unit Tests & Regression Verification
- `AuthCredentialsStoreTest.kt`: verify migration, accessors, and isolation.
- `BackupRulesXmlValidationTest.kt`: verify XML rules exclude `auth_credentials_prefs.xml`.
- `BackupManagerCredentialExclusionTest.kt`: verify `.attbackup` excludes `auth_credentials_prefs.xml`.
- `MigrationEngineCredentialSanitizationTest.kt`: verify restore discards credentials and purges legacy keys.
- `FileProviderHardeningContractTest.kt`: verify `GenericFileProvider` deleted and no root path exposure.
- Run full suite: `./gradlew testDebugUnitTest`.
