# Stage 3: Implementation Plan - ATT-4004: [High] Remove unused client secrets from APK and restrict generated BuildConfig fields

**Ticket**: [ATT-4004](https://rainerblind.atlassian.net/browse/ATT-4004)  
**Sub-task**: [ATT-4198](https://rainerblind.atlassian.net/browse/ATT-4198) (`[Impl-Plan]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-SEC-042`  
**Test Mapping**: `TST-SEC-039`  
**Branch**: `feature/ATT-4004`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

During the whole-app security and architecture code review (commit `0234bfc0`), two critical credential exposure mechanisms were identified in the Android build configuration:
1. **Critical Release Keystore Credential Exposure**: `com.google.android.libraries.mapsplatform.secrets-gradle-plugin:2.0.1` automatically scans `<buildType>.properties`. In release builds, it ingests `release.properties` and compiles `keyAlias`, `keyAliasPassword`, `keyStore`, and `keyStorePassword` into public static constants in `BuildConfig.java`, exposing keystore credentials directly in release DEX bytecode.
2. **Decommissioned & Dead Third-Party Secrets**: Historical client secrets (`RUNKEEPER_CLIENT_ID`, `RUNKEEPER_CLIENT_SECRET`, `TRAININGPEAKS_CLIENT_ID`, `TRAININGPEAKS_CLIENT_SECRET`, and `DROPBOX_APP_SECRET`) remained in `secrets.properties` and CI workflows, being compiled into `BuildConfig.java` despite Runkeeper/TrainingPeaks being decommissioned (`REQ-EXP-020` / ATT-3551) and Dropbox relying strictly on PKCE authorization using `DROPBOX_APP_KEY`.
3. **Missing Defaults File**: The Gradle `secrets` block lacked a configured `defaultPropertiesFileName`, causing clean checkouts without `secrets.properties` to fail.

To transition to an airtight, **fail-closed allow-list security posture**, this plan details:
- Exact, anchored property ignore patterns for known keystore fields (avoiding overly broad wildcards).
- Strict, exhaustive "Allowed BuildConfig Fields" schema validation in unit tests.
- Gradle build wiring making `testReleaseUnitTest` / contract tests a mandatory prerequisite of `assembleRelease`.
- CI pipeline secret scanning and sanitized documentation in `docs/ci-secrets.md`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SEC-042` (*BuildConfig Secret Generation Restriction, Keystore Credential Exclusion, Codebase Decoupling, and Default Properties Fallback*)
  * `REQ-SEC-042.1`: Keystore and signing credential exclusion via explicit, anchored patterns (`^keyAlias$`, `^keyAliasPassword$`, `^keyStore$`, `^keyStorePassword$`).
  * `REQ-SEC-042.2`: Pruning decommissioned secrets (`^RUNKEEPER.*`, `^TRAININGPEAKS.*`, `^DROPBOX_APP_SECRET$`) and verifying codebase decoupling.
  * `REQ-SEC-042.3`: Tracked `secrets.defaults.properties` fallback file at repo root containing ONLY required keys.
  * `REQ-SEC-042.4`: CI workflow (`android-branch-build.yml`, `android-release.yml`) sanitization, release build gating, secret scanning, and `docs/ci-secrets.md` documentation hardening.
  * `REQ-SEC-042.5`: Static reflection contract unit test in `BuildConfigSecretsContractTest.kt` with strict allow-list schema validation and mandatory release build gating.
* **Test Mapping**: `TST-SEC-039` (*BuildConfig Secret Reflection Audit, Keystore Exclusion & Defaults Configuration Verification Suite*)
  * `TST-SEC-039.1`: Required fields reflection presence & compilation integrity (`MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `DROPBOX_APP_KEY`).
  * `TST-SEC-039.2`: Dead secrets exclusion test (`RUNKEEPER_*`, `TRAININGPEAKS_*`, `DROPBOX_APP_SECRET`).
  * `TST-SEC-039.3`: Keystore signing credential exclusion test (`(?i).*key(alias|store|password).*`).
  * `TST-SEC-039.4`: Tracked defaults fallback configuration audit.
  * `TST-SEC-039.5`: Clean-room full suite regression execution.

---

## 3. System Invariants & Preserved Behavior

1. **Legitimate API Functionality**: Google Maps (`MAPS_API_KEY`), Strava OAuth token refresh (`STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`), and Dropbox backup/export authorization (`DROPBOX_APP_KEY`) MUST continue to function with zero disruption.
2. **Release Signing Integrity**: Gradle `signingConfigs.release` reading `release.properties` MUST remain intact and functional; only its exposure into `BuildConfig` is eliminated.
3. **Fail-Closed Allow-List Invariant**: No field outside the approved set of AGP standard fields and legitimate API keys SHALL exist in `BuildConfig.java`.
4. **Mandatory Release Gating**: Release APK assembly (`assembleRelease`) MUST fail if unit tests or contract tests fail.
5. **No UI / Localization Changes**: No user-facing strings or UI elements are affected; 9-language translation parity remains 100% satisfied.
6. **Clean-Room Regression Suite**: 100% test pass rate across the full suite (3297+ unit tests) MUST be preserved.
7. **Subtask & Gate Governance**: All subtasks advance via `freigabe` upon audit approval; parent ticket completes in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Build Configuration (`app/build.gradle`)
Configure the `secrets` plugin extension with specific, anchored ignore patterns:
```groovy
secrets {
    // To add your Maps API key to this project:
    // 1. Open the root project folder
    // 2. Add this line to the file: secrets.properties
    //    MAPS_API_KEY=YOUR_API_KEY
    propertiesFileName = "secrets.properties"

    // A properties file containing default secret values. This file can be
    // checked in version control.
    defaultPropertiesFileName = "secrets.defaults.properties"

    // Configure which keys should be ignored by the plugin by providing regular expressions.
    // Explicit, anchored patterns for known keystore signing properties:
    ignoreList.add("^keyAlias$")
    ignoreList.add("^keyAliasPassword$")
    ignoreList.add("^keyStore$")
    ignoreList.add("^keyStorePassword$")

    // Decommissioned & unused third-party client secrets:
    ignoreList.add("^RUNKEEPER.*")
    ignoreList.add("^TRAININGPEAKS.*")
    ignoreList.add("^DROPBOX_APP_SECRET$")
}

// Mandatory Release Gate: Ensure unit tests execute and pass before release APK is assembled
afterEvaluate {
    tasks.named("assembleRelease").configure {
        dependsOn("testReleaseUnitTest")
    }
}
```

### Component 2: Version-Controlled Defaults File (`secrets.defaults.properties`)
Create `secrets.defaults.properties` at the repository root containing ONLY required keys:
```properties
# Default placeholder secrets for local development and clean checkouts.
# This file is version-controlled. Override values locally in secrets.properties (git-ignored).
# Pruned keys (DROPBOX_APP_SECRET, RUNKEEPER_*, TRAININGPEAKS_*) are strictly omitted.

MAPS_API_KEY=AIzaSyPlaceholderMapsApiKeyForBuildTestingOnly00
STRAVA_CLIENT_ID=00000
STRAVA_CLIENT_SECRET=placeholder_strava_client_secret_for_build_testing
DROPBOX_APP_KEY=placeholder_dropbox_app_key
```

### Component 3: Strict Schema Allow-List Validator (`BuildConfigSecretsContractTest.kt`)
Shift from pure deny-list to a **strict allow-list schema validator**.
Define the complete, closed set of allowed fields in `BuildConfig`:
```kotlin
companion object {
    val ALLOWED_BUILD_CONFIG_FIELDS = setOf(
        // Standard Android Gradle Plugin (AGP) fields:
        "DEBUG",
        "APPLICATION_ID",
        "BUILD_TYPE",
        "VERSION_CODE",
        "VERSION_NAME",
        // Legitimate external service credentials:
        "MAPS_API_KEY",
        "STRAVA_CLIENT_ID",
        "STRAVA_CLIENT_SECRET",
        "DROPBOX_APP_KEY"
    )
}
```
Verification procedure:
1. `testBuildConfig_containsOnlyAllowedFields()`:
   Iterates through `BuildConfig::class.java.declaredFields`. Asserts that for every field:
   `assertTrue(field.name in ALLOWED_BUILD_CONFIG_FIELDS, "Unexpected field '${field.name}' in BuildConfig. Only explicitly allow-listed fields are permitted!")`
   Any newly added key automatically fails the build until reviewed and explicitly approved.
2. `testRequiredBuildConfigFields_arePresentAndNonBlank()`:
   Verifies each required key (`MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `DROPBOX_APP_KEY`) exists in `BuildConfig` and holds a non-blank string.
3. `testKeystoreAndSigningCredentials_areNeverPresent()`:
   Explicit secondary defense-in-depth asserting no field matches `(?i).*key(alias|store|password).*`.
4. `testSecretsDefaultsFile_containsOnlyRequiredKeys()`:
   Inspects `secrets.defaults.properties` directly, asserting it contains all 4 required keys and zero dead keys.

### Component 4: CI Workflows, Secret Scanning & Documentation Hardening
1. Update `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml`:
   - Remove `echo "DROPBOX_APP_SECRET=..."`
   - Remove `echo "RUNKEEPER_CLIENT_ID=..."`
   - Remove `echo "RUNKEEPER_CLIENT_SECRET=..."`
   - Remove `echo "TRAININGPEAKS_CLIENT_ID=..."`
   - Remove `echo "TRAININGPEAKS_CLIENT_SECRET=..."`
   - Ensure the generated `secrets.properties` in CI only contains: `MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `DROPBOX_APP_KEY`.
2. Add CI Secret Scanning Gate:
   Add a secret scanning check in CI using `trufflehog` / regex verification scanning for accidental credential leaks in commits.
3. Update `docs/ci-secrets.md`:
   - Explicitly document that CI environment variables must be injected via the secure GitHub Secrets store and mapped to the Gradle build, rather than being echoed into plaintext files.
   - Document the exact four required secrets and state that Runkeeper, TrainingPeaks, and Dropbox App Secret are permanently decommissioned.
4. Clean local `secrets.properties`:
   - Remove dead keys from the local developer file.

### UI Consistency (Rule 23)
* **Scope**: No UI changes.
* **Justification**: Pure build configuration, CI hardening, and unit test contract verification.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `secrets.defaults.properties`
* **File**: `secrets.defaults.properties`
* **Action**: Add version-controlled file with placeholder values for `MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `DROPBOX_APP_KEY`.

### Step 2: Update Gradle Build Configuration
* **File**: `app/build.gradle`
* **Action**: In the `secrets` block, set `defaultPropertiesFileName = "secrets.defaults.properties"` and add exact anchored ignore rules:
  - `ignoreList.add("^keyAlias$")`
  - `ignoreList.add("^keyAliasPassword$")`
  - `ignoreList.add("^keyStore$")`
  - `ignoreList.add("^keyStorePassword$")`
  - `ignoreList.add("^RUNKEEPER.*")`
  - `ignoreList.add("^TRAININGPEAKS.*")`
  - `ignoreList.add("^DROPBOX_APP_SECRET$")`
  - Configure `assembleRelease.dependsOn("testReleaseUnitTest")`.

### Step 3: Sanitize CI Workflows, Secret Scanning & Documentation
* **Files**:
  - `.github/workflows/android-branch-build.yml`
  - `.github/workflows/android-release.yml`
  - `docs/ci-secrets.md`
  - `secrets.properties` (local git-ignored)
* **Action**: Purge obsolete `RUNKEEPER_*`, `TRAININGPEAKS_*`, and `DROPBOX_APP_SECRET` lines; document GitHub Secrets store mapping.

### Step 4: Author Contract Unit Test with Allow-List Schema Validation
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/BuildConfigSecretsContractTest.kt`
* **Action**: Implement allow-list schema validator test asserting that every declared field in `BuildConfig` belongs to `ALLOWED_BUILD_CONFIG_FIELDS`, alongside explicit assertions for required key presence, keystore exclusion, and defaults file integrity.


### Step 5: Execute Targeted Unit Tests & Compilation Check
* **Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*BuildConfigSecretsContractTest*"
  ./gradlew compileDebugSources compileReleaseSources
  ```
* **Expected Result**: 100% test pass, clean compilation with zero symbol resolution errors across debug and release configurations.

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted execution: `./gradlew testDebugUnitTest --tests "*BuildConfigSecretsContractTest*"`
  2. Full clean-room regression suite: `./gradlew testDebugUnitTest` (all 3297+ unit tests passing)
  3. Release variant assembly check: `./gradlew assembleRelease` verifying that `testReleaseUnitTest` executes and keystore credentials are completely absent from `BuildConfig.java`.
* **Rollback**: Branch isolation on `feature/ATT-4004`. All modifications can be cleanly reverted via `git checkout sprint/2026-42.10` without touching other work.
