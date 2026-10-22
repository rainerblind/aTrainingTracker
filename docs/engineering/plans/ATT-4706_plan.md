# Stage 3: Implementation Plan - ATT-4706: Scope Strava OAuth Redirect URI Scheme by applicationId

**Ticket**: [ATT-4706](https://rainerblind.atlassian.net/browse/ATT-4706)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `Sprint 2026-43.7` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4706`  
**Requirement Mapping**: `REQ-EXT-012` (`REQ-EXT-012.2b`, `REQ-EXT-012.6a`–`c`)  
**Test Mapping**: `TST-EXT-012` (`TST-EXT-012.1`–`6`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Architectural Context
Co-installing release and debug builds on device causes Android's `IntentResolver` to match both filters because legacy release builds without `android:path` treat omitted paths as wildcards (`MATCH_DATA_AUTHORITY = 0x308000`), triggering `ResolverActivity` ("Öffnen mit") with equal priority (0). Scoping the URI scheme itself to `strava.${applicationId}` (`strava.com.atrainingtracker.debug` vs `strava.com.atrainingtracker`) produces distinct schemes where mismatches return `MATCH_DATA_NONE` (-0x200000), eliminating collisions while preserving Strava callback domain validation (`rainerblind.github.io`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-EXT-012` (*Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, Application ID Scheme Callback Scoping, and Legacy Callback Deletion*)
* **Test Mapping**: `TST-EXT-012` (*Strava OAuth Callback Application ID Scheme Scoping & Intent Disambiguation Test Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **OAuth Domain Compliance**: Host remains `rainerblind.github.io`, conforming to Strava Developer Settings.
2. **External Strava App Queries**: Keep `<queries><intent><data android:scheme="strava" /></intent></queries>` intact for querying the external Strava app (`com.strava`).
3. **CSRF State Security**: Cryptographic `state` token generation, 10-minute expiry, and constant-time validation (`REQ-EXT-012.1-2`) are fully preserved.
4. **Structured Concurrency**: Token exchange executes within `StravaAuthRepository` repository scope (`REQ-EXT-012.4`).

---

## 4. Proposed Architectural Changes
* **Manifest Layer (`app/src/main/AndroidManifest.xml`)**:
  * Set `<data android:scheme="strava.${applicationId}" android:host="rainerblind.github.io" />` on `StravaOAuthCallbackActivity`, removing `android:path`.
* **OAuth URI Construction (`StravaHelper.kt`)**:
  * Set `getRedirectUri()` to `"strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io"`.
* **Callback Security & Validation (`StravaOAuthCallbackActivity.kt`)**:
  * Set `REDIRECT_URI_PREFIX = "strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io"`, validating `data.scheme == "strava.${BuildConfig.APPLICATION_ID}" && data.host == "rainerblind.github.io"`.
* **Test Suite Updates**:
  * Update `StravaHelperTest.kt`, `StravaOAuthCallbackActivityTest.kt`, and `StravaOAuthSecurityTest.kt` for scheme-based scoping.
* **UI Consistency**: No UI changes (Headless manifest intent routing and OAuth redirect URI construction).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Verification (Rule 4)
* Verify exit code 0 on `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>`.

### Step 2: Manifest Intent Filter Scheme Scoping
* **File**: `app/src/main/AndroidManifest.xml` (L189–192)
* **Change**: Replace `<data android:scheme="strava" android:host="rainerblind.github.io" android:path="/${applicationId}" />` with `<data android:scheme="strava.${applicationId}" android:host="rainerblind.github.io" />`. Ensure `<queries>` retains `android:scheme="strava"`.

### Step 3: Redirect URI Generation Scoping
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt` (L121–123)
* **Change**: Return `"strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io"` from `getRedirectUri()`.

### Step 4: Callback Activity URI Prefix Scoping & Scheme/Host Validation
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthCallbackActivity.kt` (L48–78, L83)
* **Change**: Set `REDIRECT_URI_PREFIX = "strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io"`. Validate `uri.scheme == "strava.${BuildConfig.APPLICATION_ID}" && uri.host == "rainerblind.github.io"`.

### Step 5: Test Suite Updates & Scoped Security Tests
* **Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelperTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthCallbackActivityTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthSecurityTest.kt`
* **Changes**: Update redirect URI assertions, callback activity intent tests, and manifest DOM verification (`scheme="strava.${applicationId}"`, `host="rainerblind.github.io"`, without `path`).

### Step 6: Targeted Incremental Test Execution & Governance Verification
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.*" --tests "com.atrainingtracker.trainingtracker.build.*"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit & contract tests (`StravaHelperTest`, `StravaOAuthCallbackActivityTest`, `StravaOAuthSecurityTest`), followed by Tier 2 clean-room checks.
* **Rollback**: Discard uncommitted changes via `git checkout -- . && git clean -fd`.
