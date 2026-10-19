# Stage 3: Implementation Plan - ATT-4022: Add state parameter to Strava OAuth flow and scope token exchange

**Ticket**: [ATT-4022](https://rainerblind.atlassian.net/browse/ATT-4022)  
**Sub-task**: [ATT-4253](https://rainerblind.atlassian.net/browse/ATT-4253) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-EXT-012` (*Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, and Legacy Callback Deletion*)  
**Test Mapping**: `TST-EXT-009` (*Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, and Legacy Callback Deletion Verification Suite*)  
**Branch**: `feature/ATT-4022`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

During an independent whole-app architecture and security review (2026-10-18, branch `feature/ATT-3921` @ `0234bfc0`, label `code-review-2026-10`), three critical security and concurrency vulnerabilities were identified in the Strava OAuth flow:
1. **OAuth 2.0 Login CSRF Vulnerability**: The authorization request URL sends no `state` parameter, and the exported callback activity `StravaOAuthCallbackActivity` (`android:exported="true"`) blindly accepts any incoming `code` over `strava://rainerblind.github.io`. Any malicious app or website can inject an attacker's authorization code via `Intent`, tricking the app into linking the victim's workouts to the attacker's Strava account.
2. **Unstructured Concurrency via `GlobalScope`**: Token exchange is triggered fire-and-forget using `GlobalScope.launch(Dispatchers.Main)`, violating structured concurrency and lifecycle safety.
3. **Dead Code & Information Leakage in `BaseOAuthCallbackActivity.java`**: The 213-line legacy abstract class uses obsolete `HttpURLConnection`, deprecated `ProgressDialog`, and un-sanitized logging without any active subclasses in production code.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-EXT-012` (*Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, and Legacy Callback Deletion*)
  * `REQ-EXT-012.1`: Cryptographic State Generation, Persistence & 10-Minute Expiration (`StravaOAuthStateManager`)
  * `REQ-EXT-012.2`: Constant-Time State Validation & Single-Use Consumption (`validateAndConsumeState`)
  * `REQ-EXT-012.3`: Error Handling in Callback (`StravaOAuthCallbackActivity`)
  * `REQ-EXT-012.4`: Structured Concurrency in `StravaAuthRepository` (`repositoryScope`)
  * `REQ-EXT-012.5`: Deletion of Dead Legacy Callback (`BaseOAuthCallbackActivity.java`)
* **Test Mapping**: `TST-EXT-009` (Test cases `TST-EXT-009.1` through `TST-EXT-009.13`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing Strava sync and upload functions (`StravaEquipmentSyncTest`, `StravaHelperTest`, `StravaDataPurgeManagerTest`) must continue to pass cleanly.
2. **Redirect URI Compatibility**: The redirect URI `strava://rainerblind.github.io` registered in the Strava Developer Portal must remain strictly operational.
3. **OAuth Permission Scopes**: The OAuth scope set (`read,read_all,profile:read_all,activity:read_all,activity:write`) mandated by `REQ-EXT-006` must remain unchanged.
4. **Custom Tabs User Experience**: Authorization continues to use Chrome Custom Tabs (`REQ-EXT-004`).
5. **Human Gate Invariance**: AI agents must never transition parent tickets to `Erledigt`. Terminal completion remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. UI Consistency & Visual Standards (Rule 23)

- **UI Consistency**: **N/A**
  - This ticket implements background OAuth 2.0 security state validation, coroutine scoping, and dead-code deletion.
  - Zero UI screens, composables, themes, colors, or user-facing strings are modified.

---

## 5. Architectural Decomposition & Component Design

```
+------------------------------------+
|         StravaSettingsDialog       |
|    (Taps "Connect with Strava")    |
+-----------------+------------------+
                  |
                  v
+-----------------+------------------------------------------+
|                  StravaHelper.requestAccessToken            |
| 1. StravaOAuthStateManager.generateAndSaveState()          |
|    -> Generates 128-bit SecureRandom hex (32 chars)        |
|    -> Persists state & timestamp in SharedPreferences      |
| 2. getAuthorizationUrl(state)                              |
|    -> Appends &state=<state> to URL                        |
| 3. CustomTabsIntent.launchUrl(context, authUrl)            |
+------------------------------------------------------------+
                  |
                  | (User authorizes in browser)
                  v
+-----------------+------------------------------------------+
|          StravaOAuthCallbackActivity (Exported)            |
| Redirect: strava://rainerblind.github.io?state=...&code=...|
| 1. Handle error param -> clearState(), set Error, finish() |
| 2. StravaOAuthStateManager.validateAndConsumeState(state)  |
|    -> Check non-null, !expired (<=10m), constant-time eq   |
|    -> Clears state immediately (single-use)                |
|    -> If INVALID: set Error state, finish(), RETURN!       |
| 3. If VALID:                                               |
|    -> StravaAuthRepository.getInstance()                   |
|       .exchangeCodeForTokenAsync(code)                     |
|    -> finish()                                             |
+-----------------+------------------------------------------+
                  |
                  v
+-----------------+------------------------------------------+
|              StravaAuthRepository                          |
| - Scope: CoroutineScope(SupervisorJob() + Dispatchers.IO)  |
| - Method: exchangeCodeForTokenAsync(code)                  |
|   -> Dispatches token exchange in repositoryScope          |
|   -> Updates authState (Loading -> Success/Error)          |
+------------------------------------------------------------+
```

---

## 6. Atomic Implementation Steps

### Step 1: Create `StravaOAuthStateManager.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthStateManager.kt`
- Implement `generateAndSaveState()`:
  - Generates 16 random bytes via `SecureRandom`.
  - Formats as 32 lowercase hex characters.
  - Persists in `TrainingApplication.getSharedPreferences()` under `SP_STRAVA_OAUTH_STATE` and `SP_STRAVA_OAUTH_STATE_TIMESTAMP` (`System.currentTimeMillis()`).
- Implement `validateAndConsumeState(state: String?): Boolean`:
  - Retrieves stored state and timestamp.
  - Clears stored state from preferences in a `try/finally` block to guarantee single-use consumption.
  - Checks if stored state is non-null/non-empty.
  - Checks if elapsed time `System.currentTimeMillis() - timestamp <= 10 * 60 * 1000L` (10 minutes).
  - Checks `MessageDigest.isEqual(state?.toByteArray(UTF_8), storedState.toByteArray(UTF_8))`.
  - Returns boolean.
- Implement `clearState()`:
  - Clears `SP_STRAVA_OAUTH_STATE` and `SP_STRAVA_OAUTH_STATE_TIMESTAMP`.
- Implement `@VisibleForTesting fun setStateForTesting(state: String?, timestamp: Long)`:
  - Facilitates unit tests for expiration, replay, and matching.

### Step 2: Update `StravaHelper.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt`
- Update `getAuthorizationUrl(state: String? = null): String` with `@JvmOverloads`:
  - When `state` is provided: `appendQueryParameter("state", state)` in `Uri.Builder`.
- Update `requestAccessToken(context: Context)`:
  - `val state = StravaOAuthStateManager.generateAndSaveState()`
  - `val authUrl = getAuthorizationUrl(state)`
  - Launch Custom Tab.

### Step 3: Update `StravaAuthRepository.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaAuthRepository.kt`
- Add `private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- Add `fun exchangeCodeForTokenAsync(code: String)` launching `exchangeCodeForToken(code)` inside `repositoryScope`.
- Add `fun setAuthState(state: StravaAuthState)` allowing direct state propagation.

### Step 4: Refactor `StravaOAuthCallbackActivity.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthCallbackActivity.kt`
- Replace `GlobalScope.launch` with `StravaAuthRepository.getInstance().exchangeCodeForTokenAsync(code)`.
- Implement state extraction and validation:
  - Extract `val state = data.getQueryParameter("state")`.
  - If `!StravaOAuthStateManager.validateAndConsumeState(state)`:
    - Log warning, update repository state to `StravaAuthState.Error("OAuth state validation failed")`, and `finish()`.
- Handle `error` parameter:
  - Clear state, update repository state to `StravaAuthState.Error("Authorization cancelled: $error")`, and `finish()`.

### Step 5: Delete `BaseOAuthCallbackActivity.java`
- Delete `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/BaseOAuthCallbackActivity.java`.

### Step 6: Create Unit Tests
1. `StravaOAuthStateManagerTest.kt`:
   - `testGenerateAndSaveState_generates32HexCharactersAndPersistsTimestamp`
   - `testValidateAndConsumeState_matchingStateWithinTTL_returnsTrueAndClearsState`
   - `testValidateAndConsumeState_secondInvocation_returnsFalseDueToSingleUse`
   - `testValidateAndConsumeState_mismatchedState_returnsFalseAndClearsState`
   - `testValidateAndConsumeState_nullOrEmptyState_returnsFalse`
   - `testValidateAndConsumeState_expiredState_returnsFalse`
   - `testValidateAndConsumeState_uninitiatedFlow_returnsFalse`
2. `StravaOAuthCallbackActivityTest.kt`:
   - Robolectric test verifying:
     - Missing state: rejects exchange and updates auth state to Error.
     - Mismatched state: rejects exchange and updates auth state to Error.
     - Valid state: dispatches `exchangeCodeForTokenAsync` and finishes.
     - Cancelled/error redirect: clears state and finishes without crash.
3. `StravaHelperTest.kt`:
   - `testAuthorizationUrl_withState_includesStateParameter`
   - `testAuthorizationUrl_withoutState_preservesBackwardCompatibility`
4. `BaseOAuthCallbackActivityDeletionContractTest.kt`:
   - Asserts `BaseOAuthCallbackActivity` class does not exist.

### Step 7: Clean-Room Regression Verification
- Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 7. Gate 3 Review Checklist

- [x] Traceability mapped to `REQ-EXT-012` and `TST-EXT-009`.
- [x] All 5 atomic implementation steps defined with exact file targets.
- [x] Concurrency architecture specified (`CoroutineScope(SupervisorJob() + Dispatchers.IO)`).
- [x] Complete test suite specified (Robolectric + JUnit).
- [x] Invariants and human decision gate preserved.
