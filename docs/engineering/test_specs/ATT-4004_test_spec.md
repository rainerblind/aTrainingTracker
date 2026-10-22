# Stage 2: Requirement & Test Specification - ATT-4004: [High] Remove unused client secrets from APK and restrict generated BuildConfig fields

**Ticket**: [ATT-4004](https://rainerblind.atlassian.net/browse/ATT-4004)  
**Sub-task**: [ATT-4197](https://rainerblind.atlassian.net/browse/ATT-4197) (`[Req & Test Spec]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-SEC-042` (extending `REQ-SEC-041`)  
**Test Spec ID**: `TST-SEC-039`  
**Branch**: `feature/ATT-4004`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Formal Requirement Specification (`REQ-SEC-042`)

### REQ-SEC-042: BuildConfig Secret Generation Restriction, Keystore Credential Exclusion, Codebase Decoupling, and Default Properties Fallback

The build configuration (`app/build.gradle`), version-controlled default properties file (`secrets.defaults.properties`), and continuous integration workflows SHALL configure `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` to exclude all keystore and signing credentials, prune decommissioned and dead third-party client secrets from compilation into `BuildConfig.java` and APK DEX classes, guarantee that application source code does not reference pruned secrets, supply safe build-time defaults strictly limited to required keys for clean clones and CI environments, and enforce zero credential exposure via static reflection contract unit tests (ATT-4004, extending `REQ-SEC-041`):

1. **Keystore and Signing Property Exclusion (`REQ-SEC-042.1`)**:
   - The `secrets` configuration block in `app/build.gradle` SHALL include `ignoreList.add("key.*")` (or explicit properties `keyAlias`, `keyAliasPassword`, `keyStore`, `keyStorePassword`).
   - Keystore paths, key aliases, and signing passwords read from `release.properties` (or any `<buildType>.properties`) SHALL NEVER be compiled into `BuildConfig.java`, generated as resource strings, or bundled into APK DEX files.
2. **Pruning Decommissioned and Unused Client Secrets & Codebase Decoupling (`REQ-SEC-042.2`)**:
   - The build configuration SHALL configure `secrets.ignoreList` to exclude decommissioned third-party service credentials (specifically `RUNKEEPER.*`, `TRAININGPEAKS.*`) and unused SDK secrets (`DROPBOX_APP_SECRET`).
   - The application source code (`app/src/main`) SHALL NOT reference `BuildConfig.DROPBOX_APP_SECRET`, `BuildConfig.RUNKEEPER_*`, or `BuildConfig.TRAININGPEAKS_*`. (Dropbox integration in `DropboxSettingsDialog.kt` strictly relies on PKCE via `BuildConfig.DROPBOX_APP_KEY`, requiring zero app secret).
   - The system SHALL guarantee that only active, legitimate keys (`MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, and `DROPBOX_APP_KEY`) are generated into `BuildConfig` or injected into `AndroidManifest.xml`.
3. **Tracked Default Properties Fallback File (`REQ-SEC-042.3`)**:
   - The project root SHALL provide a version-controlled default properties file `secrets.defaults.properties` defining non-empty placeholder fallback values strictly limited to the four required keys: `MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, and `DROPBOX_APP_KEY`.
   - `secrets.defaults.properties` SHALL strictly omit `DROPBOX_APP_SECRET`, `RUNKEEPER_*`, and `TRAININGPEAKS_*` to eliminate deceptive dead configuration entries.
   - `app/build.gradle` SHALL bind `defaultPropertiesFileName = "secrets.defaults.properties"` in the `secrets` block so that clean checkouts and CI environments build successfully without failing when local `secrets.properties` is absent.
4. **CI Workflow and Secrets Sanitization (`REQ-SEC-042.4`)**:
   - Continuous integration workflows (`.github/workflows/android-branch-build.yml` and `android-release.yml`) and documentation (`docs/ci-secrets.md`) SHALL remove obsolete `RUNKEEPER_*`, `TRAININGPEAKS_*`, and `DROPBOX_APP_SECRET` environment variables and secrets, preventing dead secret injection during automated builds.
5. **Reflection Contract Unit Test & Compilation Guard (`REQ-SEC-042.5`)**:
   - The test suite SHALL include an automated unit test (`BuildConfigSecretsContractTest.kt`) that inspects `BuildConfig::class.java.declaredFields` via reflection in `./gradlew testDebugUnitTest` to assert:
     - (a) required fields exist: `MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `DROPBOX_APP_KEY`;
     - (b) forbidden fields do NOT exist: `RUNKEEPER_*`, `TRAININGPEAKS_*`, `DROPBOX_APP_SECRET`, and any field matching `(?i).*key(alias|store|password).*`;
     - (c) all application code compiles cleanly without missing symbol errors.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement `REQ-SEC-042`, expanding security hardening initiated in `REQ-SEC-041`. Targets `app/build.gradle`, `secrets.defaults.properties`, `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, and `BuildConfigSecretsContractTest.kt`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-4004`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). `secrets-gradle-plugin:2.0.1` was integrated in commit `0ec8fc29` (ATT-3551) to externalize API keys from source control.
3. **Root Reason for Existing Formulation**: `secrets-gradle-plugin` by default scans `<buildType>.properties`. When `release.properties` was created to hold release signing keys for Gradle `signingConfigs.release`, the plugin automatically converted all properties in `release.properties` (`keyAlias`, `keyAliasPassword`, `keyStore`, `keyStorePassword`) into public static fields in `BuildConfig.java`. Furthermore, historical service keys (Runkeeper, TrainingPeaks, Dropbox app secret) were retained in `secrets.properties` and injected into the build even though their corresponding integrations were decommissioned or switched to PKCE.
4. **Preservation of Core Invariants**: Legitimate integrations (Google Maps rendering, Strava OAuth token exchange via `StravaHelper`, Dropbox backup/export authorization via `DropboxClientFactory` with `DROPBOX_APP_KEY`) MUST continue to function with zero disruption. Release APK signing MUST continue to operate without modification.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Exclusion of Keystore Credentials)**:
  * *Given* a compiled release APK or `BuildConfig` class generated with `release.properties`,
  * *When* inspecting `BuildConfig.java` or decompiled bytecode,
  * *Then* zero keystore credentials (`keyAlias`, `keyAliasPassword`, `keyStore`, `keyStorePassword`) are present.
* **Criterion 2 (Exclusion of Dead Third-Party Secrets)**:
  * *Given* a compiled debug or release APK,
  * *When* inspecting `BuildConfig.java` or decompiled bytecode,
  * *Then* no `DROPBOX_APP_SECRET`, `RUNKEEPER_*`, or `TRAININGPEAKS_*` fields are present.
* **Criterion 3 (Compilation Integrity & Codebase Decoupling)**:
  * *Given* the application source code in `app/src/main`,
  * *When* compiling debug or release variants (`./gradlew compileDebugSources compileReleaseSources`),
  * *Then* compilation succeeds with zero errors, confirming no code references pruned BuildConfig fields.
* **Criterion 4 (Presence of Legitimate Required Keys)**:
  * *Given* the application runtime accessing legitimate external services,
  * *When* querying `BuildConfig.MAPS_API_KEY`, `BuildConfig.STRAVA_CLIENT_ID`, `BuildConfig.STRAVA_CLIENT_SECRET`, or `BuildConfig.DROPBOX_APP_KEY`,
  * *Then* the fields exist, are non-null, and supply configured credentials.
* **Criterion 5 (Default Fallback File for Clean Checkouts)**:
  * *Given* a fresh clone without a local `secrets.properties` file,
  * *When* building via Gradle (`./gradlew assembleDebug`),
  * *Then* the build succeeds cleanly using placeholder values from `secrets.defaults.properties`.

---

## 4. System Invariants

1. **Zero Runtime Disruption**: Legitimate services relying on `BuildConfig` keys (`StravaHelper`, Google Maps, `DropboxClientFactory` / `DropboxSettingsDialog`) MUST continue to resolve required keys without breakage.
2. **Release Signing Integrity**: Gradle release signing configuration (`signingConfigs.release`) reading `release.properties` MUST remain functional.
3. **No UI / Localization Impact**: Build-level configuration changes do not touch UI strings; 9-language translation parity remains 100% satisfied.
4. **100% Test Suite Pass Rate**: All existing unit tests and the new contract test MUST pass with zero regressions.

---

## 5. Concrete Verification Test Specification (`TST-SEC-039`)

### TST-SEC-039: BuildConfig Secret Reflection Audit, Keystore Exclusion & Defaults Configuration Verification Suite

| Test ID | Target File | Verification Scope & Procedure |
| :--- | :--- | :--- |
| `TST-SEC-039.1` | `BuildConfigSecretsContractTest.kt` | **Required BuildConfig Fields Reflection & Compilation Integrity Test**:<br>• Inspect `BuildConfig::class.java.declaredFields` via reflection.<br>• Assert required fields exist: `MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `DROPBOX_APP_KEY`.<br>• Assert each required field has a non-null, non-blank string value.<br>• Assert project compilation (`./gradlew compileDebugSources compileReleaseSources`) succeeds cleanly with zero missing symbol errors after pruning dead keys. |
| `TST-SEC-039.2` | `BuildConfigSecretsContractTest.kt` | **Forbidden Dead Secrets Exclusion Test**:<br>• Inspect all field names in `BuildConfig::class.java.declaredFields`.<br>• Assert that no field matches `RUNKEEPER_.*`, `TRAININGPEAKS_.*`, or `DROPBOX_APP_SECRET`. |
| `TST-SEC-039.3` | `BuildConfigSecretsContractTest.kt` | **Keystore and Signing Credential Exclusion Test**:<br>• Inspect all field names in `BuildConfig::class.java.declaredFields`.<br>• Assert that no field matches `(?i).*key(alias\|store\|password).*`, guaranteeing that keystore signing properties from `release.properties` are never exposed in bytecode. |
| `TST-SEC-039.4` | Static Inspection / Configuration Audit | **Tracked Defaults Fallback Configuration Audit**:<br>• Verify `secrets.defaults.properties` exists at project root and defines ONLY the 4 required keys (`MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `DROPBOX_APP_KEY`), strictly omitting `DROPBOX_APP_SECRET`, `RUNKEEPER_*`, and `TRAININGPEAKS_*`.<br>• Verify `app/build.gradle` defines `defaultPropertiesFileName = "secrets.defaults.properties"`. |
| `TST-SEC-039.5` | Clean-Room Suite | **Clean-Room Full Suite Regression Execution**:<br>• Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions. |

---

## 6. Localization Parity

* **Scope**: Localization Parity: N/A.
* **Justification**: This ticket concerns build script hardening, Gradle secrets plugin configuration, and static contract testing. No user-facing strings, layouts, or resources are introduced or modified. Existing 9-language localization parity (EN, DE, ES, FR, IT, JA, NL, PL, PT) remains 100% intact.

---

## 7. Traceability Matrix

| Requirement Clause | Test Case | Target Artifact | Status |
| :--- | :--- | :--- | :--- |
| `REQ-SEC-042.1` | `TST-SEC-039.3` | `app/build.gradle` | Specified |
| `REQ-SEC-042.2` | `TST-SEC-039.1`, `TST-SEC-039.2` | `app/build.gradle`, `app/src/main/` | Specified |
| `REQ-SEC-042.3` | `TST-SEC-039.4` | `secrets.defaults.properties`, `app/build.gradle` | Specified |
| `REQ-SEC-042.4` | `TST-SEC-039.4` | `.github/workflows/android-*.yml`, `docs/ci-secrets.md` | Specified |
| `REQ-SEC-042.5` | `TST-SEC-039.1`–`TST-SEC-039.3`, `TST-SEC-039.5` | `BuildConfigSecretsContractTest.kt` | Specified |
| `REQ-PRO-001` | `TST-SEC-039.5` | `./gradlew testDebugUnitTest` | Specified |
