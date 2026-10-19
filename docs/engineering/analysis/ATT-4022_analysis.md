# Stage 1 Analysis: ATT-4022 - [Bug] [Medium] Add state parameter to Strava OAuth flow and scope token exchange

**Ticket**: [ATT-4022](https://rainerblind.atlassian.net/browse/ATT-4022)  
**Sub-task**: [ATT-4251](https://rainerblind.atlassian.net/browse/ATT-4251) (`[Analysis]`)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Branch**: `feature/ATT-4022`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Statement & Motivation

During an independent whole-app architecture and security audit (2026-10-18, branch `feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`), significant OAuth 2.0 security, concurrency, and legacy dead-code vulnerabilities were identified in the Strava authentication flow (`StravaHelper.kt`, `StravaOAuthCallbackActivity.kt`, `StravaAuthRepository.kt`, `BaseOAuthCallbackActivity.java`):

1. **OAuth 2.0 Login Cross-Site Request Forgery (CSRF) via Missing `state` Parameter**:
   In `StravaHelper.getAuthorizationUrl()`, the authorization URL is constructed without a `state` parameter:
   ```kotlin
   Uri.Builder().apply {
       scheme(HTTPS)
       authority(STRAVA_AUTHORITY)
       appendPath(OAUTH)
       appendPath(MOBILE)
       appendPath(AUTHORIZE)
       appendQueryParameter(CLIENT_ID, MY_CLIENT_ID)
       appendQueryParameter(REDIRECT_URI, getRedirectUri())
       appendQueryParameter(RESPONSE_TYPE, CODE)
       appendQueryParameter(APPROVAL_PROMPT, AUTO)
       appendQueryParameter(SCOPE, "read,read_all,profile:read_all,activity:read_all,activity:write")
   }.build().toString()
   ```
   Furthermore, the exported callback activity `StravaOAuthCallbackActivity` (`android:exported="true"`) blindly accepts any incoming `code` parameter matching the redirect scheme `strava://rainerblind.github.io`:
   ```kotlin
   val code = data.getQueryParameter(StravaHelper.CODE)
   if (code != null) {
       repository.exchangeCodeForToken(code)
   }
   ```
   **Attack Vector & Failure Scenario**: A malicious website visited in the athlete's browser or another app installed on the device can craft and fire an Intent:
   ```bash
   adb shell am start -a android.intent.action.VIEW -d "strava://rainerblind.github.io?code=ATTACKER_AUTH_CODE"
   ```
   Because `StravaOAuthCallbackActivity` performs zero origin verification or state validation, it immediately exchanges the attacker's authorization code for tokens and overwrites the local athlete ID, access token, and refresh token with the attacker's credentials. Subsequently, all workouts recorded by the athlete (including high-resolution GPS tracks, heart rate, and power telemetry) are automatically uploaded to the attacker's Strava account without the victim's knowledge or consent.

2. **Unstructured Concurrency via `GlobalScope`**:
   In `StravaOAuthCallbackActivity.kt`:
   ```kotlin
   kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
       repository.exchangeCodeForToken(code)
   }
   ```
   The token exchange is dispatched fire-and-forget in `GlobalScope`. This violates Android and Kotlin structured concurrency best practices (`REQ-PRO-002` / `REQ-PRO-013`). Any uncaught exceptions inside the coroutine can cause process instability, and the exchange lifecycle is detached from the repository's lifecycle.

3. **Dead Code & Information Leakage in `BaseOAuthCallbackActivity.java`**:
   The legacy abstract base class `BaseOAuthCallbackActivity.java` (213 lines) contains obsolete `HttpURLConnection` logic, deprecated `ProgressDialog`, and un-sanitized logging. Zero production classes extend or reference it. Retaining dead security-sensitive code increases maintenance burden and attack surface.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 RFC 6749 Section 10.12 (Cross-Site Request Forgery)
RFC 6749 (The OAuth 2.0 Authorization Framework) Section 10.12 explicitly requires:
> "The client MUST implement CSRF protection for its redirection URI. This is typically accomplished by requiring any request to the authorization server to include a value that binds the request to the client's current state... The client MUST verify that the binding value returned from the authorization server matches the value sent."

Because the app generates no `state` during authorization request construction and validates no `state` on callback reception, it is completely unprotected against Login CSRF.

### 2.2 Forensic State Flow & Token Exchange Mechanics
1. **Flow Initiation (`StravaHelper.requestAccessToken`)**:
   - Currently generates static URL without `state`.
   - Opens Chrome Custom Tabs.
2. **Strava Web Flow**:
   - Athlete authenticates and grants permissions.
   - Strava redirects to `strava://rainerblind.github.io?state=<state>&code=<code>&scope=<scope>`.
3. **App Reception (`StravaOAuthCallbackActivity`)**:
   - `intent.data` parsed.
   - If `error` is present: logs and finishes.
   - If `code` is present: directly calls `repository.exchangeCodeForToken(code)`.
4. **Vulnerability Point**:
   Any invocation of `StravaOAuthCallbackActivity` with a valid or attacker-provided `code` will trigger `exchangeCodeForToken`.

### 2.3 Concurrency Architecture in `StravaAuthRepository`
`StravaAuthRepository` is an application-scoped singleton (`getInstance()`). However, `exchangeCodeForToken` is a `suspend fun` invoked from `GlobalScope.launch(Dispatchers.Main)` inside the ephemeral `StravaOAuthCallbackActivity`. When the activity calls `finish()`, the coroutine floats unmanaged. To establish structured concurrency, `StravaAuthRepository` must own a dedicated `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and manage background exchanges reliably.

### 2.4 Codebase Audit of `BaseOAuthCallbackActivity.java`
A repository-wide search confirms:
- Class: `com.atrainingtracker.trainingtracker.onlinecommunities.BaseOAuthCallbackActivity`
- Subclasses: **None** (formerly used before `StravaOAuthCallbackActivity.kt` was introduced).
- Usages: **Zero** in application code and tests.
- Conclusion: Safe to delete in its entirety.

---

## 3. Proposed Technical Architecture & Remediation

### 3.1 Cryptographically Secure OAuth State Management
1. **State Generation**:
   - Use `java.security.SecureRandom` to generate a 128-bit (16-byte) or 256-bit (32-byte) cryptographically secure random token, hex-encoded or base64url-encoded (e.g. 32 alphanumeric hex characters).
2. **State Persistence & Expiration**:
   - Store the generated `state` in `TrainingApplication` / `SharedPreferences` (`SP_STRAVA_OAUTH_STATE`) along with an issuance timestamp (`SP_STRAVA_OAUTH_STATE_TIMESTAMP`).
   - Enforce a 10-minute (600,000 ms) expiration TTL. If a callback arrives after 10 minutes, the state is rejected as expired.
   - Provide a helper/manager: `StravaOAuthStateManager` encapsulating generation, persistence, validation, and single-use clearing.
3. **URL Construction (`StravaHelper.kt`)**:
   - Update `getAuthorizationUrl(state: String? = null)`:
     If `state` is provided (or generated), append `appendQueryParameter("state", state)`.
   - In `requestAccessToken(context: Context)`:
     Generate a fresh state via `StravaOAuthStateManager.generateAndSaveState()`, build the URL with the state, and launch Custom Tabs.

### 3.2 Callback Validation & Rejection
1. In `StravaOAuthCallbackActivity.kt`:
   - Read `returnedState = data.getQueryParameter("state")`.
   - Read `code = data.getQueryParameter(StravaHelper.CODE)`.
   - Validate `returnedState` against `StravaOAuthStateManager.validateAndConsumeState(returnedState)`.
   - Constant-time comparison (`MessageDigest.isEqual`) to protect against timing side channels.
   - If invalid (null, mismatched, expired, or no flow in progress):
     - Log sanitized warning: `Log.w(TAG, "OAuth state validation failed; rejecting callback")`.
     - Update repository auth state: `StravaAuthRepository.getInstance().setAuthState(StravaAuthState.Error("OAuth state validation failed"))`.
     - Call `finish()`.
     - **DO NOT** invoke `exchangeCodeForToken`.
   - If valid:
     - State is consumed (cleared immediately to prevent replay attacks).
     - Dispatch `exchangeCodeForToken` using repository scope.

### 3.3 Structured Concurrency in `StravaAuthRepository`
- Add `private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` inside `StravaAuthRepository`.
- Provide `fun exchangeCodeForTokenAsync(code: String)` or use `repositoryScope.launch` to manage token exchange.
- Remove `GlobalScope` usage completely.

### 3.4 Deletion of Dead Legacy Classes
- Safely delete `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/BaseOAuthCallbackActivity.java`.
- Verify compilation and clean builds.

---

## 4. Chesterton's Fence & Invariant Archaeology

1. **Original Requirement ID & Target**:
   - Refines `REQ-EXT-004` (*Integrated Strava Authorization via Chrome Custom Tabs*) and `REQ-EXT-005` (*Reactive OAuth Feedback*).
   - Targets `StravaHelper.kt`, `StravaOAuthCallbackActivity.kt`, `StravaAuthRepository.kt`, `TrainingApplication.java`, and deletes `BaseOAuthCallbackActivity.java`.
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-4022`, Sprint `2026-42.10`.
   - Originated in commit `f3c3a0df` (ATT-597) during Strava OAuth migration.
3. **Root Reason for Existing Formulation**:
   - The initial prototype focused on getting the Custom Tab redirect working with `strava://rainerblind.github.io`. `state` was left out for simplicity during initial bring-up, and `GlobalScope` was used as a shortcut to launch the exchange from an Activity that calls `finish()`.
4. **Preservation of Core Invariants**:
   - The redirect URI `strava://rainerblind.github.io` registered in Strava Developer Portal must remain valid.
   - Chrome Custom Tabs presentation (`REQ-EXT-004`) must remain intact.
   - OAuth scope `profile:read_all` and permissions (`REQ-EXT-006`) must not be changed.
   - Token storage in `TrainingApplication` and downstream sync trigger (`REQ-EXT-006`, `REQ-EXT-007`) must operate seamlessly upon successful exchange.
   - 100% clean-room unit test pass rate must be preserved.

---

## 5. Scope Bounding & Out-of-Scope Declarations

- **In-Scope**:
  - Secure random OAuth `state` generation, persistence in SharedPreferences, and 10-minute expiration.
  - Adding `state` query parameter to Strava authorize URL.
  - Constant-time `state` validation and single-use consumption in `StravaOAuthCallbackActivity`.
  - Replacing `GlobalScope` with structured `CoroutineScope` in `StravaAuthRepository`.
  - Deleting dead `BaseOAuthCallbackActivity.java`.
  - Comprehensive unit test suite covering state generation, validation, expiration, replay rejection, and mismatched state rejection.
- **Out-of-Scope**:
  - Migrating Strava OAuth redirect URI to Android App Links (HTTPS), as this requires server-side `assetlinks.json` hosting on `rainerblind.github.io` and updates to the live Strava Developer Portal configuration. The custom scheme `strava://rainerblind.github.io` combined with cryptographic `state` validation fully mitigates Login CSRF.
  - Changing token refresh logic or Strava API v3 endpoints (governed by `REQ-EXT-011`).

---

## 6. Risk Analysis & Mitigation Strategy

| Risk | Impact | Likelihood | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Activity recreation drops state** | Low | Medium | Persist `state` in `SharedPreferences` so it survives process death while the user is in Chrome Custom Tabs. |
| **User cancels auth in browser** | Low | High | Handle `?error=` gracefully, clearing persisted state and setting `StravaAuthState.Idle` or `Error`. |
| **Replay of intercepted state** | High | Low | Single-use consumption: clear state immediately upon first validation attempt. |
| **Slow user auth exceeds 10m TTL** | Low | Low | 10 minutes (600s) is generous for completing a standard OAuth prompt; expired states fail safely with a localized error. |

---

## 7. Gate 1 Review Checklist

- [x] Forensic root cause analysis completed with exact code references (`StravaOAuthCallbackActivity.kt`, `StravaHelper.kt`, `BaseOAuthCallbackActivity.java`).
- [x] Chesterton's Fence archaeology documented (originating commit `f3c3a0df`, invariants preserved).
- [x] Clear scope bounding: CSRF state parameter, structured repository scope, and legacy class deletion in-scope; external server-side App Link changes out-of-scope.
- [x] Clear verification strategy defined for Stage 2 & 4.
