# Stage 3: Implementation Plan - ATT-4668: Scope Strava OAuth redirect URI by applicationId

**Ticket**: [ATT-4668](https://rainerblind.atlassian.net/browse/ATT-4668)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4668`  
**Requirement Mapping**: `REQ-EXT-012` (`REQ-EXT-012.6a`–`d`)  
**Test Mapping**: `TST-EXT-012` (`TST-EXT-012.1`–`6`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background
When release (`com.atrainingtracker`) and debug (`com.atrainingtracker.debug`) builds are co-installed on the same device, returning from Strava OAuth triggers an Android disambiguation chooser (*"Open with..."*) due to identical intent filters. If the user selects the wrong variant, authentication fails because the CSRF `state` token resides in the initiating variant's storage. Scoping the redirect URI path by `/${applicationId}` isolates intent resolution to the initiating variant.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-EXT-012` (*Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, Application ID Callback Scoping, and Legacy Callback Deletion*)
* **Test Mapping**: `TST-EXT-012` (*Strava OAuth Callback Application ID Path Scoping & Intent Disambiguation Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **OAuth Domain Compliance**: Callback domain remains `rainerblind.github.io` (Strava API Agreement).
2. **CSRF State Security**: Cryptographic `state` token generation, 10-minute expiry, and single-use validation (`REQ-EXT-012.1`, `REQ-EXT-012.2`) remain strictly intact.
3. **Structured Concurrency**: Repository scope token exchange (`REQ-EXT-012.4`) is preserved without `GlobalScope`.
4. **Single-Variant Compatibility**: Clean installation of a single APK functions identically.

---

## 4. Proposed Architectural Changes
* **Manifest Layer (`app/src/main/AndroidManifest.xml`)**:
  * Add `android:path="/${applicationId}"` to `StravaOAuthCallbackActivity`'s `<data>` intent filter.
* **OAuth URI Construction (`StravaHelper.kt`)**:
  * Update `getRedirectUri()` to return `"strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}"`.
* **Callback Security & Validation (`StravaOAuthCallbackActivity.kt`)**:
  * Set `REDIRECT_URI_PREFIX = "strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}"` and enforce path matching.
* **Security & Contract Test Suite (`StravaOAuthSecurityTest.kt`)**:
  * Introduce test suite asserting URI encoding, variant matching, mismatched/missing path rejection, and manifest XML configuration.
* **UI Consistency**: No UI changes (Headless manifest intent routing and background OAuth redirect URI scoping).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Verification (Rule 4)
* Verify exit code 0 on `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>`.

### Step 2: Manifest Intent Filter Scoping
* **File**: `app/src/main/AndroidManifest.xml` (L185–192)
* **Change**: Add `android:path="/${applicationId}"` to `<data android:scheme="strava" android:host="rainerblind.github.io" />`.

### Step 3: Redirect URI Generation Scoping
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt`
* **Change**: Update `getRedirectUri()` to `"strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}"`.

### Step 4: Callback Activity URI Prefix Scoping
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthCallbackActivity.kt`
* **Change**: Update `REDIRECT_URI_PREFIX` to `"strava://rainerblind.github.io/${BuildConfig.APPLICATION_ID}"`.

### Step 5: Test Suite Updates & Scoped Security Tests
* **Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelperTest.kt` (update URI assertions)
  * `app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthCallbackActivityTest.kt` (update test URIs)
  * `app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthSecurityTest.kt` (new test suite for `TST-EXT-012.1`–`5`)

### Step 6: Incremental Test Execution & Validation
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.*" --tests "com.atrainingtracker.trainingtracker.build.*"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted tests (`StravaHelperTest`, `StravaOAuthCallbackActivityTest`, `StravaOAuthSecurityTest`, `ManifestAndBuildConfigContractTest`), followed by Tier 2 regression check.
* **Rollback**: Branch isolation allows full discard via `git checkout -- . && git clean -fd`.
