# Stage 2: Requirement & Test Specification - ATT-4023: Exclude OAuth credentials from Auto Backup and .attbackup archives

**Ticket**: [ATT-4023](https://rainerblind.atlassian.net/browse/ATT-4023)  
**Sub-task**: [ATT-4257](https://rainerblind.atlassian.net/browse/ATT-4257) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-DAT-032` (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and `.attbackup` Credential Exclusion Policy*)  
**Test Spec ID**: `TST-DAT-027`  
**Branch**: `feature/ATT-4023`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-DAT-032)

### 1.1 Problem Statement & Rationale
During an independent whole-app architecture, privacy, and security review (2026-10-18, branch `feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`), several high-severity data leakage vulnerabilities and attack surfaces were identified:
1. **Unrestricted Android Auto Backup & Device Transfer**: The `<application>` tag in `AndroidManifest.xml` lacked `android:allowBackup`, `android:dataExtractionRules`, and `android:fullBackupContent`. By default, Android backs up all private application files—including default `SharedPreferences` containing plaintext OAuth tokens for Strava, Google Drive, and Dropbox—to cloud backups and device-to-device transfers.
2. **Plaintext OAuth Token Bundling in `.attbackup` Archives**: `BackupManager.createBackup(context)` bundled the entire `shared_prefs` directory without filtering. As a result, plaintext OAuth access and refresh tokens were packaged into `.attbackup` zip archives and uploaded to remote cloud storage or transferred across devices.
3. **Overly Broad `FileProvider` Exposure**: `GenericFileProvider` was an obsolete, unreferenced class declaring `<external-path name="Workouts" path="." />` in `provider_paths.xml`, exposing all external storage. Furthermore, `file_paths.xml` declared `<cache-path name="backups" path="." />`, unnecessarily exposing the entire cache directory.

### 1.2 Functional & Architectural Requirements
`TrainingApplication` (`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`), `AuthCredentialsStore` (`app/src/main/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStore.kt`), `BackupManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt`), `MigrationEngine` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/MigrationEngine.kt`), and `AndroidManifest.xml` SHALL isolate all OAuth authentication credentials into a dedicated private preferences file (`auth_credentials_prefs.xml`), migrate legacy credentials transparently, exclude `auth_credentials_prefs.xml` from Android Auto Backup and device transfers via XML extraction rules, exclude `auth_credentials_prefs.xml` from `.attbackup` archive creation and restoration, require a clean one-time re-authentication after full restore, and eliminate broad FileProvider attack surfaces (`REQ-DAT-032`, extending `REQ-DAT-031`, `REQ-MIG-040`, and `REQ-SEC-041`):

1. **Isolated Authentication Credentials Store (`REQ-DAT-032.1`)**:
   - `AuthCredentialsStore` SHALL encapsulate all OAuth authentication credentials (`SP_STRAVA_TOKEN`, `SP_STRAVA_REFRESH_TOKEN`, `SP_STRAVA_TOKEN_EXPIRES_AT`, `SP_STRAVA_ATHLETE_ID`, `SP_STRAVA_OAUTH_STATE`, `SP_STRAVA_OAUTH_STATE_TIMESTAMP`, `SP_GOOGLE_DRIVE_ACCOUNT_EMAIL`, `SP_GOOGLE_DRIVE_AUTH_TOKEN`, `SP_DROPBOX_CREDENTIAL`) within a dedicated private `SharedPreferences` file: `auth_credentials_prefs.xml` (`context.getSharedPreferences("auth_credentials_prefs", Context.MODE_PRIVATE)`).
   - Upon first initialization, `AuthCredentialsStore.migrateLegacyCredentials(context: Context)` SHALL check default `SharedPreferences`. If any legacy credential keys are present, it SHALL atomically copy them to `auth_credentials_prefs` and purge them immediately from default `SharedPreferences`.
   - `TrainingApplication` credential getters, setters, and deletion methods for Strava, Google Drive, and Dropbox SHALL delegate directly to `AuthCredentialsStore`. General non-sensitive settings (e.g. `uploadToStrava`, `uploadToGoogleDrive`, `uploadToDropbox`, `googleDriveOnlyWifi`) SHALL remain in default `SharedPreferences`.

2. **Android Auto Backup & Device Transfer Rules (`REQ-DAT-032.2`)**:
   - `AndroidManifest.xml` `<application>` tag SHALL declare `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, and `android:fullBackupContent="@xml/full_backup_content"`.
   - `res/xml/data_extraction_rules.xml` (API 31+) SHALL exclude `auth_credentials_prefs.xml` from both `<cloud-backup>` and `<device-transfer>` using `<exclude domain="sharedpref" path="auth_credentials_prefs.xml" />`.
   - `res/xml/full_backup_content.xml` (API 23–30) SHALL exclude `auth_credentials_prefs.xml` using `<exclude domain="sharedpref" path="auth_credentials_prefs.xml" />`.

3. **Exclusion of Credentials from `.attbackup` Creation (`REQ-DAT-032.3`)**:
   - In `BackupManager.createBackup(context)`, `zipFolder` SHALL explicitly filter out `auth_credentials_prefs.xml` (and any related temp/lock files `auth_credentials_prefs.*`), guaranteeing that zero plaintext OAuth credentials, tokens, or athlete IDs enter `.attbackup` zip archives.
   - `createBackup()` SHALL ensure legacy credential keys have been migrated prior to zipping default preferences.

4. **Restore Sanitization & Clean Re-Authentication Invariant (`REQ-DAT-032.4`)**:
   - In `MigrationEngine.kt`, when unpacking or staging a restored archive, any `auth_credentials_prefs.xml` file present in the archive SHALL be discarded.
   - Restored default `SharedPreferences` SHALL be sanitized to purge any legacy credential keys (`SP_STRAVA_TOKEN`, `SP_STRAVA_REFRESH_TOKEN`, `SP_DROPBOX_CREDENTIAL`, `SP_GOOGLE_DRIVE_AUTH_TOKEN`, etc.).
   - Following restore, `AuthCredentialsStore` SHALL be cleared, ensuring that restored devices cleanly require a one-time re-authentication with cloud services without leaking previously active tokens.

5. **FileProvider Attack Surface Elimination & Scoping (`REQ-DAT-032.5`)**:
   - `GenericFileProvider.java` and `res/xml/provider_paths.xml` SHALL be permanently deleted from the codebase and manifest.
   - In `res/xml/file_paths.xml`, `<cache-path name="backups" path="." />` exposing the root cache directory SHALL be removed. FileProvider paths SHALL be strictly limited to `<cache-path name="my_cache_images" path="images/" />` and `<files-path name="internal_backups" path="backups/" />`.

6. **Preservation of System Invariants**:
   - Workout databases, sensors, zones, and application settings MUST be fully backed up and restored across devices without data loss.
   - `androidx.security:security-crypto` (`EncryptedSharedPreferences`) MUST NOT be used on general preference files that participate in `.attbackup` (Option A compliance).
   - 100% clean-room test pass rate MUST be maintained.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Legacy Credential Migration)**:
  * *Given* default `SharedPreferences` populated with legacy Strava, Google Drive, or Dropbox tokens,
  * *When* `AuthCredentialsStore.migrateLegacyCredentials(context)` executes,
  * *Then* all credentials are moved to `auth_credentials_prefs` and stripped from default preferences.
* **Criterion 2 (Android Auto Backup Rules Validation)**:
  * *Given* Android Auto Backup XML rule descriptors (`data_extraction_rules.xml` and `full_backup_content.xml`),
  * *When* parsed and inspected,
  * *Then* `auth_credentials_prefs.xml` is excluded from cloud backup, device transfer, and legacy full backup domains.
* **Criterion 3 (.attbackup Archive Excludes Credentials)**:
  * *Given* active tokens in `auth_credentials_prefs` and `BackupManager.createBackup(context)` is called,
  * *When* the generated `.attbackup` archive is inspected,
  * *Then* `shared_prefs/auth_credentials_prefs.xml` is NOT present, and no OAuth tokens exist inside the archive.
* **Criterion 4 (Restore Sanitization & Clean Re-Authentication)**:
  * *Given* a backup archive containing legacy tokens,
  * *When* restored via `MigrationEngine`,
  * *Then* `auth_credentials_prefs.xml` is ignored, legacy token keys are stripped from restored default preferences, and cloud services indicate disconnected state awaiting re-authentication.
* **Criterion 5 (FileProvider Hardening)**:
  * *Given* the project manifest and XML resources,
  * *When* inspected,
  * *Then* `GenericFileProvider` and `provider_paths.xml` do not exist, and `file_paths.xml` contains zero `path="."` root exposure.

---

## 2. Test Specification (TST-DAT-027)

| Test ID | Target Component | Test Description | Verification Method |
| :--- | :--- | :--- | :--- |
| `TST-DAT-027.1` | `AuthCredentialsStore` | Legacy Migration: moves all credentials to `auth_credentials_prefs` and purges from default prefs | Unit Test (`AuthCredentialsStoreTest.kt`) |
| `TST-DAT-027.2` | `TrainingApplication` / `AuthCredentialsStore` | Read & Write Delegation: tokens read/written via `AuthCredentialsStore`, not default prefs | Unit Test (`AuthCredentialsStoreTest.kt`) |
| `TST-DAT-027.3` | XML Configuration / Manifest | Android Auto Backup Rules: verifies XML rules exclude `auth_credentials_prefs.xml` | Unit Test (`BackupRulesXmlValidationTest.kt`) |
| `TST-DAT-027.4` | `BackupManager` | `.attbackup` Creation: verifies `auth_credentials_prefs.xml` is excluded from zip archive | Robolectric Test (`BackupManagerCredentialExclusionTest.kt`) |
| `TST-DAT-027.5` | `MigrationEngine` | Restore Sanitization: discards `auth_credentials_prefs.xml` and strips legacy keys on restore | Robolectric Test (`MigrationEngineCredentialSanitizationTest.kt`) |
| `TST-DAT-027.6` | Manifest / FileProvider | FileProvider Hardening: contract test verifying `GenericFileProvider` deleted and no root path exposure | Reflection / XML Audit Test (`FileProviderHardeningContractTest.kt`) |
| `TST-DAT-027.7` | Entire Test Suite | Clean-room full regression run (`./gradlew testDebugUnitTest`) | Gradle Clean-Room Execution |

---

## 3. Traceability Matrix

| Requirement Clause | Test Case ID | Test Class / Method |
| :--- | :--- | :--- |
| `REQ-DAT-032.1` (Isolated Credentials Store & Migration) | `TST-DAT-027.1`, `TST-DAT-027.2` | `AuthCredentialsStoreTest.testLegacyCredentialMigration`, `AuthCredentialsStoreTest.testCredentialReadWriteDelegation` |
| `REQ-DAT-032.2` (Android Auto Backup & Device Transfer Rules) | `TST-DAT-027.3` | `BackupRulesXmlValidationTest.testBackupRulesExcludeCredentialsPrefs` |
| `REQ-DAT-032.3` (Exclusion of Credentials from .attbackup) | `TST-DAT-027.4` | `BackupManagerCredentialExclusionTest.testCreateBackup_ExcludesAuthCredentialsPrefs` |
| `REQ-DAT-032.4` (Restore Sanitization & Re-Authentication) | `TST-DAT-027.5` | `MigrationEngineCredentialSanitizationTest.testRestore_DiscardsAuthCredentialsAndSanitizesPrefs` |
| `REQ-DAT-032.5` (FileProvider Attack Surface Elimination) | `TST-DAT-027.6` | `FileProviderHardeningContractTest.testGenericFileProviderDeleted_AndFilePathsNarrowed` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-DAT-027.7` | `./gradlew testDebugUnitTest` |

---

## 4. Localization & String Resource Parity Audit

- **Audit Result**: Zero new UI strings required. All existing cloud integration strings (`R.string.strava_disconnected_status`, `R.string.dropbox_disconnected_status`, etc.) already exist with full 9-language translation parity.
- Zero modifications to `res/values*/strings.xml`.
