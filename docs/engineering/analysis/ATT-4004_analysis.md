# Stage 1: Analysis & Problem Domain - ATT-4004: Remove unused client secrets from APK and restrict generated BuildConfig fields

**Ticket**: [ATT-4004](https://rainerblind.atlassian.net/browse/ATT-4004)  
**Sub-task**: `ATT-4196` (`[Analysis]`)  
**Parent Epic**: [ATT-171](https://rainerblind.atlassian.net/browse/ATT-171) (*General Improvements and Bugs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Problem Formulation

During independent Whole-App Security & Code Review on branch `feature/ATT-3921` (`0234bfc0`), ticket **ATT-4004** was reported highlighting that the `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` generates unused third-party API client secrets and credentials into `BuildConfig.java` for release builds.

### Forensic Investigation & Reproduction Evidence
A forensic audit of `app/build/generated/source/buildConfig/release/com/atrainingtracker/BuildConfig.java` confirms the finding and uncovers an even more critical vulnerability:
```java
public final class BuildConfig {
  public static final boolean DEBUG = false;
  public static final String APPLICATION_ID = "com.atrainingtracker";
  public static final String BUILD_TYPE = "release";
  public static final int VERSION_CODE = 271;
  public static final String VERSION_NAME = "4.9.40-dev";
  public static final String DROPBOX_APP_KEY = "...";
  public static final String DROPBOX_APP_SECRET = "..."; // [CRITICAL UNUSED LEAK]
  public static final String MAPS_API_KEY = "...";
  public static final String RUNKEEPER_CLIENT_ID = "..."; // [DEAD LEGACY LEAK]
  public static final String RUNKEEPER_CLIENT_SECRET = "..."; // [DEAD LEGACY LEAK]
  public static final String STRAVA_CLIENT_ID = "...";
  public static final String STRAVA_CLIENT_SECRET = "...";
  public static final String TRAININGPEAKS_CLIENT_ID = "..."; // [DEAD LEGACY LEAK]
  public static final String TRAININGPEAKS_CLIENT_SECRET = "..."; // [DEAD LEGACY LEAK]
  public static final String keyAlias = "..."; // [CRITICAL SIGNING CREDENTIAL LEAK]
  public static final String keyAliasPassword = "..."; // [CRITICAL SIGNING CREDENTIAL LEAK]
  public static final String keyStore = "..."; // [CRITICAL SIGNING CREDENTIAL LEAK]
  public static final String keyStorePassword = "..."; // [CRITICAL SIGNING CREDENTIAL LEAK]
}
```

### Detailed Forensic Findings
1. **Critical Keystore Password Exposure (`keyAlias`, `keyAliasPassword`, `keyStore`, `keyStorePassword`)**:
   - `secrets-gradle-plugin:2.0.1` automatically scans the root directory for build-type specific properties files matching `<buildType>.properties`.
   - When building a release artifact (`generateReleaseBuildConfig`), the plugin discovers `release.properties` (which contains keystore paths and passwords used by `android.signingConfigs.release`) and unconditionally generates **all** of its key-value pairs as `public static final String` fields in `BuildConfig.java`.
   - As a result, the private signing keystore password and alias password are baked directly into the release DEX of the distributed application!
2. **Decommissioned Exporter Secrets (`RUNKEEPER_*`, `TRAININGPEAKS_*`)**:
   - RunKeeper and TrainingPeaks integrations were permanently decommissioned in ATT-3551 / `REQ-EXP-020`, excising all runtime classes and activities.
   - However, legacy credentials remained in `secrets.properties` and CI generation scripts, causing the secrets plugin to continue injecting them into every build.
3. **Unused Dropbox App Secret (`DROPBOX_APP_SECRET`)**:
   - Dropbox API v2 integration utilizes standard OAuth2 with client app key authorization (`DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY)`).
   - `DROPBOX_APP_SECRET` is never referenced in source code, yet was generated as a public field in `BuildConfig`.
4. **Legitimately Required Runtime Keys**:
   - `MAPS_API_KEY`: Required by Google Maps SDK (referenced via manifest placeholder in `AndroidManifest.xml`).
   - `STRAVA_CLIENT_ID` & `STRAVA_CLIENT_SECRET`: Required by `StravaHelper.kt` and `StravaAuthRepository.kt` for mobile OAuth token refresh.
   - `DROPBOX_APP_KEY`: Required by `DropboxUploader.java`, `LegacyImportEngine.kt`, and `DropboxBackupManager.kt`.

---

## 2. Root Cause Analysis

1. **Unrestricted Secrets Plugin Property Ingestion**:
   In `app/build.gradle:206-208`:
   ```groovy
   secrets {
       propertiesFileName = "secrets.properties"
   }
   ```
   No `ignoreList` was configured. By default, `secrets-gradle-plugin` injects every property found in `propertiesFileName` and any discovered `<buildType>.properties` file (such as `release.properties`).
2. **Missing `defaultPropertiesFileName`**:
   The plugin had no fallback properties file (`secrets.defaults.properties`) committed to version control, necessitating ad-hoc CI dummy creation scripts that propagated obsolete keys.
3. **Dead Configuration Retention**:
   When legacy features were removed in ATT-3551, configuration files (`secrets.properties`, CI workflow templates) were not purged of orphaned keys.

---

## 3. Chesterton's Fence & Archaeology

1. **Why was `secrets-gradle-plugin` introduced?**
   - Introduced to securely externalize `MAPS_API_KEY` from source control and inject it into `AndroidManifest.xml` and `BuildConfig` without committing sensitive credentials into Git.
2. **Why were `release.properties` keys injected?**
   - The plugin authors designed build-type specific property loading so developers could have different API keys for debug vs. release (e.g. `release.properties` containing production API keys).
   - In this project, `release.properties` was used exclusively by Gradle for APK/AAB cryptographic signing (`android.signingConfigs.release`). Because the file shared the exact name `<buildType>.properties`, the plugin treated it as a secrets container and exposed the signing passwords.
3. **Why did RunKeeper and TrainingPeaks keys persist?**
   - They were historical remnants from the initial OAuth integration over a decade ago. Their removal from code in ATT-3551 left the properties file unpruned.
4. **Preservation Invariant**:
   - Legitimate functionality must not break: Google Maps must continue displaying tiles, Strava authentication and token refresh must operate seamlessly, and Dropbox backups/uploads must function without disruption.

---

## 4. Scope & Technical Architecture (SWE.1 / SWE.2)

### 4.1 Required Changes

1. **Plugin Configuration Hardening (`app/build.gradle`)**:
   Configure `secrets` block with strict ignore rules and default properties:
   ```groovy
   secrets {
       propertiesFileName = "secrets.properties"
       defaultPropertiesFileName = "secrets.defaults.properties"
       // Prevent leaking keystore signing credentials into BuildConfig
       ignoreList.add("key.*")
       // Prevent generating decommissioned or unused third-party secrets
       ignoreList.add("RUNKEEPER.*")
       ignoreList.add("TRAININGPEAKS.*")
       ignoreList.add("DROPBOX_APP_SECRET")
   }
   ```
2. **Default Properties File (`secrets.defaults.properties`)**:
   Create a tracked repository file containing safe dummy placeholders:
   ```properties
   MAPS_API_KEY=
   STRAVA_CLIENT_ID=
   STRAVA_CLIENT_SECRET=
   DROPBOX_APP_KEY=
   ```
3. **Local Secrets Hygiene (`secrets.properties`)**:
   Purge dead keys (`RUNKEEPER_*`, `TRAININGPEAKS_*`, `DROPBOX_APP_SECRET`) from `secrets.properties`.
4. **CI Workflow Hygiene (`.github/workflows/`)**:
   Update `android-branch-build.yml` and `android-release.yml` to remove dummy creation for `RUNKEEPER_*` and `TRAININGPEAKS_*`.
5. **Documentation Update (`docs/ci-secrets.md`)**:
   Remove decommissioned third-party credentials from the CI setup guide.
6. **Automated Verification Test**:
   Author a unit/contract test `BuildConfigSecretsContractTest.kt` verifying via reflection on `BuildConfig::class` that `RUNKEEPER_*`, `TRAININGPEAKS_*`, `DROPBOX_APP_SECRET`, `keyAlias`, and `keyStore*` are NEVER present in `BuildConfig`.

---

## 5. System Invariants & Preserved Behavior

1. **Signing Invariant**: Release APK and AAB signing via `release.properties` in `app/build.gradle` continues to function with zero disruption.
2. **Operational Invariant**: `MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, and `DROPBOX_APP_KEY` remain fully accessible to their respective runtime components.
3. **Build Reproducibility**: Fresh clones or CI runners without a local `secrets.properties` build cleanly via `secrets.defaults.properties`.
4. **Zero Keystore Leakage**: No signing credentials, passwords, or keystore paths are ever generated into Java source, DEX bytecode, or APK manifests.
5. **No UI Changes**: Purely a build configuration and security hardening task (Rule 23).
6. **Single Atomic Commit**: Changes remain uncommitted on `feature/ATT-4004` until squashed into `sprint/2026-42.10` in Stage 5 (Rule 44).
