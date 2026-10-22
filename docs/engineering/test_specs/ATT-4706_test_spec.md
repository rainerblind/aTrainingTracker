# Stage 2: Requirement & Test Specification - ATT-4706: Scope Strava OAuth Redirect URI Scheme by applicationId

**Ticket**: [ATT-4706](https://rainerblind.atlassian.net/browse/ATT-4706)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `Sprint 2026-43.7` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4706`  
**Requirement Mapping**: `REQ-EXT-012` (*Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, Application ID Scheme Callback Scoping, and Legacy Callback Deletion*)  
**Test Spec ID**: `TST-EXT-012`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-EXT-012`)

### 1.1 Problem Statement & Rationale
When co-installing release and debug builds, Android's `IntentFilter.matchData()` treats omitted path attributes in legacy builds as wildcards (`/*`, `MATCH_DATA_AUTHORITY = 0x308000`), matching `strava://rainerblind.github.io/${applicationId}` with equal priority in `IntentResolver` and triggering `ResolverActivity` ("Öffnen mit"). Scoping the custom URI scheme to `strava.${applicationId}` (`strava.com.atrainingtracker://...` vs `strava.com.atrainingtracker.debug://...`) eliminates collisions because scheme mismatches yield `MATCH_DATA_NONE` (-0x200000).

### 1.2 Functional & Architectural Requirements
1. **Manifest Scheme Scoping (`REQ-EXT-012.6a`)**: `AndroidManifest.xml` intent filter for `StravaOAuthCallbackActivity` SHALL declare `<data android:scheme="strava.${applicationId}" android:host="rainerblind.github.io" />` without `android:path`. The external Strava app query `<queries><intent><data android:scheme="strava" /></intent></queries>` SHALL remain untouched.
2. **Redirect URI Construction (`REQ-EXT-012.6b`)**: `StravaHelper.getRedirectUri()` SHALL return `"strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io"`.
3. **Intent Callback Validation (`REQ-EXT-012.2b`, `REQ-EXT-012.6c`)**: `StravaOAuthCallbackActivity` SHALL validate incoming URIs using `REDIRECT_URI_PREFIX = "strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io"` (or scheme + host validation), rejecting legacy `strava://` and foreign variant schemes without consuming state or triggering token exchange.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-EXT-012` (`REQ-EXT-012.2`, `REQ-EXT-012.6`), targeting `AndroidManifest.xml`, `StravaHelper.kt`, `StravaOAuthCallbackActivity.kt`, and test suites `StravaHelperTest.kt`, `StravaOAuthCallbackActivityTest.kt`, and `StravaOAuthSecurityTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-4022` (commit `943dc3b4f6`, Sprint `2026-42.10`) introduced CSRF state protection; `ATT-4668` (commit `2ff1f4c74f`, Sprint `2026-43.7`) introduced path scoping; `ATT-4706` (Sprint `2026-43.7`) scopes the URI scheme itself.
* **Root Reason for Existing Formulation**: `REQ-EXT-012` previously used path scoping (`strava://rainerblind.github.io/${applicationId}`), but legacy release builds without `android:path` match with equal priority in `IntentResolver`. Scoping the URI scheme itself to `strava.${applicationId}` guarantees true intent isolation.
* **Preservation of Core Invariants**: Callback authority `rainerblind.github.io` (`REQ-EXT-004`), CSRF state validation (`REQ-EXT-012.1`), repository scope exchange (`REQ-EXT-012.4`), Custom Tabs launch, and 100% clean-room test pass rate are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Auth URL Scheme Scoping)**: *Given* an authorization request, *When* `StravaHelper.getAuthorizationUrl(state)` constructs the redirect URI, *Then* `redirect_uri` parameter contains `strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io`.
* **Criterion 2 (Matching Scheme Acceptance)**: *Given* co-installed release and debug builds, *When* Strava redirects to `strava.com.atrainingtracker.debug://rainerblind.github.io?...`, *Then* Android OS routes the intent directly to debug `StravaOAuthCallbackActivity` without `ResolverActivity`.
* **Criterion 3 (Mismatched / Legacy Scheme Rejection)**: *Given* an incoming redirect URI with mismatched variant scheme or legacy `strava://` scheme, *When* `StravaOAuthCallbackActivity` handles the intent, *Then* the activity finishes cleanly without consuming state or triggering token exchange.
* **Criterion 4 (Manifest Contract)**: *Given* `AndroidManifest.xml`, *When* parsed by DOM, *Then* `StravaOAuthCallbackActivity` intent filter contains `android:scheme="strava.${applicationId}"`, `android:host="rainerblind.github.io"`, and no `android:path`.

---

## 2. Test Specification (`TST-EXT-012`)

### Test Cases
* `TST-EXT-012.1` (**Auth URL Scheme Scoping**): Verify `StravaHelper.getAuthorizationUrl(state)` generates `redirect_uri` with scheme `strava.${BuildConfig.APPLICATION_ID}`.
* `TST-EXT-012.2` (**Callback Activity Matching Scheme**): Verify `StravaOAuthCallbackActivity` consumes state and exchanges code for URI matching `strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io`.
* `TST-EXT-012.3` (**Callback Activity Mismatched Scheme Rejection**): Verify callback with opposite variant scheme (`strava.com.other://...`) is ignored without consuming state.
* `TST-EXT-012.4` (**Callback Activity Legacy Scheme Rejection**): Verify callback with legacy un-scoped scheme `strava://rainerblind.github.io` is ignored without consuming state.
* `TST-EXT-012.5` (**Manifest Scheme Scoping Contract Test**): Verify `AndroidManifest.xml` DOM contains `scheme="strava.${applicationId}"`, `host="rainerblind.github.io"`, and no `path`.
* `TST-EXT-012.6` (**Targeted & Clean-Room Regression Suite**): Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.*"` achieving 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-EXT-012.1` | Unit | `StravaHelper.kt`, `StravaHelperTest.kt`, `StravaOAuthSecurityTest.kt` | `REQ-EXT-012.6b` | Proposed |
| `TST-EXT-012.2` | Unit / Robolectric | `StravaOAuthCallbackActivity.kt`, `StravaOAuthSecurityTest.kt` | `REQ-EXT-012.2b`, `REQ-EXT-012.6c` | Proposed |
| `TST-EXT-012.3` | Unit / Robolectric | `StravaOAuthCallbackActivity.kt`, `StravaOAuthSecurityTest.kt` | `REQ-EXT-012.6c` | Proposed |
| `TST-EXT-012.4` | Unit / Robolectric | `StravaOAuthCallbackActivity.kt`, `StravaOAuthSecurityTest.kt` | `REQ-EXT-012.6c` | Proposed |
| `TST-EXT-012.5` | Contract | `AndroidManifest.xml`, `StravaOAuthSecurityTest.kt` | `REQ-EXT-012.6a` | Proposed |
| `TST-EXT-012.6` | Regression | Package test suite (`com.atrainingtracker.trainingtracker.onlinecommunities.strava.*`) | `REQ-EXT-012.7` | Proposed |
