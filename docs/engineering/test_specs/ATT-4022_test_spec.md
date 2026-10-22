# Stage 2: Requirement & Test Specification - ATT-4022: Add state parameter to Strava OAuth flow and scope token exchange

**Ticket**: [ATT-4022](https://rainerblind.atlassian.net/browse/ATT-4022)  
**Sub-task**: [ATT-4252](https://rainerblind.atlassian.net/browse/ATT-4252) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-EXT-012` (*Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, and Legacy Callback Deletion*)  
**Test Spec ID**: `TST-EXT-009`  
**Branch**: `feature/ATT-4022`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-EXT-012)

### 1.1 Problem Statement & Rationale
An independent whole-app architecture and security code review (2026-10-18, branch `feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`) identified three critical vulnerabilities and architectural flaws in the Strava OAuth 2.0 integration:
1. **OAuth 2.0 Login CSRF Vulnerability**: The authorization request URL sends no `state` parameter, and the exported callback activity `StravaOAuthCallbackActivity` (`android:exported="true"`) blindly accepts any incoming `code` over `strava://rainerblind.github.io`. Any malicious app or website can inject an attacker's authorization code via `Intent`, tricking the app into linking the victim's workouts to the attacker's Strava account.
2. **Unstructured Concurrency via `GlobalScope`**: Token exchange is triggered fire-and-forget using `GlobalScope.launch(Dispatchers.Main)`, violating structured concurrency and lifecycle safety.
3. **Dead Code & Information Leakage in `BaseOAuthCallbackActivity.java`**: The 213-line legacy abstract class uses obsolete `HttpURLConnection`, deprecated `ProgressDialog`, and un-sanitized logging without any active subclasses in production code.

### 1.2 Functional & Architectural Requirements
`StravaHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt`), `StravaOAuthCallbackActivity` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthCallbackActivity.kt`), `StravaOAuthStateManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthStateManager.kt`), and `StravaAuthRepository` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaAuthRepository.kt`) SHALL protect the Strava OAuth 2.0 authorization code flow against Login Cross-Site Request Forgery (CSRF) via cryptographically random state parameter validation and 10-minute expiration, execute token exchange within a structured repository coroutine scope eliminating `GlobalScope`, safely delete unused `BaseOAuthCallbackActivity.java`, and preserve official branding and permissions (`REQ-EXT-012`, refining `REQ-EXT-004` and `REQ-EXT-005`):

1. **Cryptographic State Generation, Persistence & 10-Minute Expiration (`REQ-EXT-012.1`)**:
   - `StravaOAuthStateManager` SHALL generate a 128-bit cryptographically secure random token (`SecureRandom`) encoded as a 32-character hexadecimal string upon initiating authorization in `StravaHelper.requestAccessToken()`.
   - `StravaOAuthStateManager` SHALL persist the generated state and current epoch timestamp (`System.currentTimeMillis()`) into `TrainingApplication.getSharedPreferences()` (`SP_STRAVA_OAUTH_STATE`, `SP_STRAVA_OAUTH_STATE_TIMESTAMP`).
   - `StravaHelper.getAuthorizationUrl(state: String?)` SHALL append `appendQueryParameter("state", state)` to the authorization request URL.
   - A stored state SHALL expire and become invalid after 10 minutes (600,000 ms).

2. **Constant-Time State Validation & Single-Use Consumption (`REQ-EXT-012.2`)**:
   - When `StravaOAuthCallbackActivity` receives an incoming redirect URI (`strava://rainerblind.github.io`), it SHALL extract `state = uri.getQueryParameter("state")` and `code = uri.getQueryParameter("code")`.
   - `StravaOAuthStateManager.validateAndConsumeState(state: String?): Boolean` SHALL validate that:
     (a) a non-expired state exists in storage,
     (b) incoming `state` is non-null, and
     (c) incoming `state` matches the stored state using constant-time comparison (`MessageDigest.isEqual`) to prevent timing side channels.
   - Upon validation (whether successful or failed), `StravaOAuthStateManager` SHALL immediately clear the stored state to enforce single-use semantics and prevent replay attacks.
   - If validation fails (missing, mismatched, or expired state, or no flow was in progress), `StravaOAuthCallbackActivity` SHALL reject the callback: log a warning, transition `StravaAuthRepository` to `StravaAuthState.Error`, and `finish()` WITHOUT exchanging `code` for tokens.

3. **Error Handling in Callback (`REQ-EXT-012.3`)**:
   - If the redirect URI contains an `error` query parameter (e.g. user cancelled), `StravaOAuthCallbackActivity` SHALL consume and clear the stored state, log the error, transition `StravaAuthRepository` to `StravaAuthState.Error("Authorization cancelled: $error")`, and finish cleanly.

4. **Structured Concurrency in `StravaAuthRepository` (`REQ-EXT-012.4`)**:
   - `StravaOAuthCallbackActivity` SHALL NOT invoke `GlobalScope.launch`.
   - `StravaAuthRepository` SHALL manage background asynchronous execution using an internal structured coroutine scope: `private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
   - `StravaAuthRepository` SHALL expose `fun exchangeCodeForTokenAsync(code: String)` launching within `repositoryScope`.

5. **Deletion of Dead Legacy Callback (`REQ-EXT-012.5`)**:
   - `BaseOAuthCallbackActivity.java` SHALL be completely removed from the project, eliminating dead code, deprecated `ProgressDialog`, and legacy `HttpURLConnection`.

6. **Preservation of System Invariants**:
   - Canonical redirect URI `strava://rainerblind.github.io` (`REQ-EXT-004`), Chrome Custom Tabs launch (`REQ-EXT-004`), OAuth scopes (`read,read_all,profile:read_all,activity:read_all,activity:write`) (`REQ-EXT-006`), token persistence in `TrainingApplication` (`REQ-EXT-006`), reactive `StravaAuthState` flow (`REQ-EXT-005`), and 100% clean-room test pass rate MUST NOT be broken.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (State Generation & URL Inclusion)**:
  * *Given* an athlete tapping "Connect with Strava",
  * *When* the authorization URL is constructed,
  * *Then* `state` query parameter is present with a 32-character cryptographic random token, and the token is saved in preferences with an epoch timestamp.
* **Criterion 2 (CSRF Protection - Missing or Mismatched State)**:
  * *Given* a callback without `state` parameter or with a mismatched `state` value,
  * *When* `StravaOAuthCallbackActivity` receives the intent,
  * *Then* `StravaOAuthStateManager.validateAndConsumeState` returns `false`, no token exchange is initiated, and the activity finishes with an error state.
* **Criterion 3 (Successful Flow with Valid State)**:
  * *Given* a callback with matching `state` received within 10 minutes,
  * *When* `StravaOAuthCallbackActivity` handles the intent,
  * *Then* the state is consumed, `exchangeCodeForTokenAsync` is dispatched in repository scope, and tokens are stored upon successful network exchange.
* **Criterion 4 (Expired State Rejection)**:
  * *Given* a callback arriving after 10 minutes from issuance,
  * *When* validated,
  * *Then* `validateAndConsumeState` returns `false` due to expiration.
* **Criterion 5 (Dead Code & Scope Cleanup)**:
  * *Given* the codebase,
  * *When* inspected,
  * *Then* `BaseOAuthCallbackActivity.java` is absent and `GlobalScope` is not referenced in Strava OAuth code.

---

## 2. Test Specification (TST-EXT-009)

| Test ID | Target Component | Test Description | Verification Method |
| :--- | :--- | :--- | :--- |
| `TST-EXT-009.1` | `StravaOAuthStateManager` | State Generation: 32 hex chars, cryptographically random, timestamped | Unit Test (`StravaOAuthStateManagerTest.kt`) |
| `TST-EXT-009.2` | `StravaHelper` | Authorization URL: includes `state` query parameter alongside standard params | Unit Test (`StravaHelperTest.kt`) |
| `TST-EXT-009.3` | `StravaOAuthStateManager` | Valid state validation & single-use consumption (second call returns false) | Unit Test (`StravaOAuthStateManagerTest.kt`) |
| `TST-EXT-009.4` | `StravaOAuthStateManager` | Mismatched state rejection & storage cleared | Unit Test (`StravaOAuthStateManagerTest.kt`) |
| `TST-EXT-009.5` | `StravaOAuthStateManager` | Null / empty state rejection | Unit Test (`StravaOAuthStateManagerTest.kt`) |
| `TST-EXT-009.6` | `StravaOAuthStateManager` | Expired state (> 10m) rejection | Unit Test (`StravaOAuthStateManagerTest.kt`) |
| `TST-EXT-009.7` | `StravaOAuthStateManager` | Uninitiated flow (empty storage) rejection | Unit Test (`StravaOAuthStateManagerTest.kt`) |
| `TST-EXT-009.8` | `StravaOAuthCallbackActivity` | Missing state rejects token exchange, sets Error state | Unit Test (`StravaOAuthCallbackActivityTest.kt`) |
| `TST-EXT-009.9` | `StravaOAuthCallbackActivity` | Mismatched state rejects token exchange, sets Error state | Unit Test (`StravaOAuthCallbackActivityTest.kt`) |
| `TST-EXT-009.10` | `StravaOAuthCallbackActivity` | Valid state dispatches `exchangeCodeForTokenAsync` via repository scope | Unit Test (`StravaOAuthCallbackActivityTest.kt`) |
| `TST-EXT-009.11` | `StravaOAuthCallbackActivity` | User cancellation (`error=access_denied`) clears state and finishes cleanly | Unit Test (`StravaOAuthCallbackActivityTest.kt`) |
| `TST-EXT-009.12` | Build System / Codebase | `BaseOAuthCallbackActivity.java` deleted, zero references in repo | Git / Reflection Audit |
| `TST-EXT-009.13` | Entire Test Suite | Clean-room full regression run (`./gradlew testDebugUnitTest`) | Gradle Clean-Room Execution |

---

## 3. Traceability Matrix

| Requirement Clause | Test Case ID | Test Class / Method |
| :--- | :--- | :--- |
| `REQ-EXT-012.1` (State Generation & URL Inclusion) | `TST-EXT-009.1`, `TST-EXT-009.2` | `StravaOAuthStateManagerTest.testGenerateAndSaveState_*`, `StravaHelperTest.testAuthorizationUrl_IncludesStateParameter` |
| `REQ-EXT-012.2` (State Validation & Rejection) | `TST-EXT-009.3` - `TST-EXT-009.9` | `StravaOAuthStateManagerTest.testValidateAndConsumeState_*`, `StravaOAuthCallbackActivityTest.testHandleIntent_MissingOrMismatchedState_RejectsExchange` |
| `REQ-EXT-012.3` (Error Handling in Callback) | `TST-EXT-009.11` | `StravaOAuthCallbackActivityTest.testHandleIntent_WithError_ClearsStateAndFinishes` |
| `REQ-EXT-012.4` (Structured Repository Scope) | `TST-EXT-009.10` | `StravaOAuthCallbackActivityTest.testHandleIntent_ValidState_DispatchesExchangeCodeForTokenAsync` |
| `REQ-EXT-012.5` (Legacy Callback Deletion) | `TST-EXT-009.12` | `BaseOAuthCallbackActivityDeletionContractTest.testBaseOAuthCallbackActivity_DoesNotExist` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-EXT-009.13` | `./gradlew testDebugUnitTest` |

---

## 4. Localization & String Resource Parity Audit

- **Audit Result**: Zero new UI strings required. All internal error states in `StravaAuthRepository` and `StravaOAuthCallbackActivity` use technical diagnostics or existing localized error strings (`R.string.strava_error_*`).
- Zero changes to `res/values*/strings.xml`.
