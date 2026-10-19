# Stage 3: Implementation Plan: ATT-3996

**Ticket**: [ATT-3996](https://rainerblind.atlassian.net/browse/ATT-3996)  
**Subtask**: `ATT-4193` (`[Impl-Plan]`)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-EXT-011` (*Resilient Strava Token Refresh Concurrency, Expiry Margin, Precise Revocation Discrimination & Safe Header Construction*)  
**Test Case Mapping**: `TST-EXT-008` (`TST-EXT-008.1` – `TST-EXT-008.8`)  
**Branch**: `feature/ATT-3996`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Problem Description

In `com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper` and `com.atrainingtracker.trainingtracker.exporter.uploader.StravaUploader`, the Strava OAuth token refresh and network request dispatch mechanism suffers from critical concurrency, reliability, and security flaws:
1. **Accidental Local Data Purge on Application Configuration / Credential Errors**:
   `StravaHelper.kt:208` catches HTTP 400 and 401 responses and performs a loose substring search: `errorBody.contains("invalid", ignoreCase = true)`. When an application-level configuration error occurs—such as an invalid `client_secret` or `client_id` (e.g. during build flavor misconfiguration, key rotation, or sandbox testing)—Strava returns `{"message":"Bad Request","errors":[{"resource":"Application","field":"client_secret","code":"invalid"}]}`. Because `"invalid"` is matched, `StravaDataPurgeManager.purgeAllStravaData()` is invoked, wiping `StravaUpload.db`, all starred segments in `Segments.db`, all Strava routes in `Routes.db`, and unlinking equipment in `Equipment.db`.
2. **Refresh Token Concurrency Race & Parallel Invalidation**:
   Multiple background sync workers (`StravaRoutesSyncWorker`, `StravaSegmentsSyncWorker`, `StravaEquipmentSyncWorker`, `ExportWorker`) invoke `StravaHelper.getRefreshedAccessToken()` concurrently without locking. When a token expires, multiple threads race to send refresh requests to Strava's OAuth endpoint. Under Strava's refresh token rotation policy, the first request succeeds and issues a new refresh token; the second parallel request fails with `{"message":"Bad Request","errors":[{"resource":"RefreshToken","field":"refresh_token","code":"invalid"}]}` because its refresh token was just rotated. Under the current flawed logic, this causes an immediate accidental data purge and user logout!
3. **Zero Token Expiry Safety Margin**:
   The existing check in `StravaHelper.kt:165` evaluates `now < expiresAt`. If an access token has 10 seconds of validity remaining, it is deemed valid and returned. If the worker subsequently initiates a multi-minute workout upload or large segment sync, the token expires mid-flight, causing an HTTP 401 failure. A 300-second (5-minute) safety margin is required.
4. **Per-Call `OkHttpClient` Instantiation**:
   `StravaHelper.kt:191` instantiates `val client = OkHttpClient()` inside `getRefreshedAccessToken()`. Every refresh creates a new connection pool, dispatcher, and executor threads, risking thread pool leaks and unnecessary socket overhead. A shared singleton `OkHttpClient` with explicit 15s connection/read and 20s call timeouts must be reused.
5. **Dangling `Authorization: Bearer null` Dispatch**:
   In `StravaUploader.kt:597, 635, 656`, methods `updateStravaActivity`, `getStravaJson`, and `getStravaJsonArray` build requests with `.addHeader("Authorization", "Bearer ${StravaHelper.getRefreshedAccessToken()}")`. If token refresh fails and returns `null`, the request is sent with `Authorization: Bearer null`, leaking bad requests to Strava endpoints. The token must be resolved first and validated, cleanly returning `null` if blank.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-EXT-011`
  * `REQ-EXT-011.1`: Precise revocation discrimination & exclusion of application errors. Parse failure JSON structurally with `try-catch(JSONException)`. Non-JSON error responses (e.g. Cloudflare / WAF HTML) strictly default to "do not purge". Abort without purge if `field == "client_secret"`, `field == "client_id"`, or `resource == "Application"`. Purge ONLY IF `(resource == "RefreshToken" || field == "refresh_token") && code == "invalid"`, or `error == "invalid_grant"`, or response message contains `"revoked"`. All logged error bodies are sanitized to avoid credential/PII leakage.
  * `REQ-EXT-011.2`: Thread-safe reentrant token refresh & bounded double-checked locking via `ReentrantLock` with `tryLock(15, TimeUnit.SECONDS)`. If a thread waits and acquires the lock, it re-verifies token expiry so racing threads reuse the freshly obtained token without redundant network calls. If the lock wait times out, re-verify token validity or return `null` safely without hanging worker threads or triggering ANR.
  * `REQ-EXT-011.3`: 300-second (5-minute) token expiry safety margin: `System.currentTimeMillis() / 1000 + 300 < TrainingApplication.getStravaTokenExpiresAt()`.
  * `REQ-EXT-011.4`: Shared singleton `OkHttpClient` instance with 15-second connect/read timeouts and 20-second call timeout. Explicit timeout handling returning `null` cleanly without crashing or purging.
  * `REQ-EXT-011.5`: Safe authorization header construction in `StravaUploader.kt`: resolve `accessToken` before building requests; abort cleanly with `null` if null or blank.
* **Test Case Mapping**: `TST-EXT-008` (`TST-EXT-008.1` through `TST-EXT-008.8`).

---

## 3. System Invariants & Preserved Behavior

1. **Strava API Policy 7.4 Compliance**: When a user truly revokes Strava access or the refresh token is genuinely invalid, `StravaDataPurgeManager.purgeAllStravaData()` is invoked as required by `REQ-EXT-009`.
2. **Local User Data Isolation**: Native workouts, FIT files, local GPX tracks, and hardware sensor configurations are never touched during Strava purges.
3. **No UI Changes**: Background network and token refresh mechanisms do not modify UI layouts or themes (Rule 23).
4. **Clean-Room Test Pass Rate**: Full test suite pass rate remains 100% (`./gradlew testDebugUnitTest`).
5. **Single Commit per Ticket**: Changes remain uncommitted on `feature/ATT-3996` until squashed into `sprint/2026-42.10` in Stage 5 (Rule 44).

---

## 4. Proposed Architectural Design (SWE.2)

### 4.1 Component 1: `StravaHelper.kt` Refactoring

#### 4.1.1 Bounded ReentrantLock Synchronization & Double-Checked Locking (`REQ-EXT-011.2`, `REQ-EXT-011.3`)
To prevent thread starvation and ANRs while avoiding concurrent refresh races, use a `ReentrantLock` with bounded lock acquisition (`tryLock(15, TimeUnit.SECONDS)`):

```kotlin
private val refreshLock = ReentrantLock()
private const val TOKEN_EXPIRY_MARGIN_SECONDS = 300L // 5 minutes
private const val LOCK_WAIT_TIMEOUT_SECONDS = 15L

// Shared singleton OkHttpClient with explicit 15s connect/read and 20s call timeouts
internal var httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(20, TimeUnit.SECONDS)
    .build()

private fun isTokenValid(): Boolean {
    return try {
        val nowSec = System.currentTimeMillis() / 1000
        val expiresAt = TrainingApplication.getStravaTokenExpiresAt()
        if (nowSec + TOKEN_EXPIRY_MARGIN_SECONDS < expiresAt) {
            val token = TrainingApplication.getStravaAccessToken()
            !token.isNullOrEmpty()
        } else {
            false
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed reading cached Strava token status", e)
        false
    }
}

@JvmStatic
fun getRefreshedAccessToken(): String? {
    if (DEBUG) Log.i(TAG, "getRefreshedAccessToken()")
    
    // Fast path: check without acquiring lock
    if (isTokenValid()) {
        return TrainingApplication.getStravaAccessToken()
    }

    // Bounded lock acquisition slow path: serialize refresh without deadlock/starvation
    val acquired = try {
        refreshLock.tryLock(LOCK_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        Log.w(TAG, "Interrupted while waiting for Strava refresh lock", e)
        return null
    }

    if (!acquired) {
        Log.w(TAG, "Timed out waiting for Strava refresh lock; checking if another thread refreshed token")
        return if (isTokenValid()) TrainingApplication.getStravaAccessToken() else null
    }

    try {
        // Re-check validity inside lock (double-checked locking)
        if (isTokenValid()) {
            return TrainingApplication.getStravaAccessToken()
        }
        return performTokenRefresh()
    } finally {
        refreshLock.unlock()
    }
}
```

#### 4.1.2 Safe Network Execution & Timeout Handling (`REQ-EXT-011.4`)
```kotlin
private fun performTokenRefresh(): String? {
    val refreshUrl = "https://www.strava.com/oauth/token"
    val refreshToken = try {
        TrainingApplication.getStravaRefreshToken()
    } catch (e: Exception) {
        null
    } ?: return null

    val formBody = FormBody.Builder()
        .add(CLIENT_ID, MY_CLIENT_ID)
        .add(CLIENT_SECRET, MY_CLIENT_SECRET)
        .add(GRANT_TYPE, REFRESH_TOKEN)
        .add(REFRESH_TOKEN, refreshToken)
        .build()

    val request = Request.Builder()
        .url(refreshUrl)
        .post(formBody)
        .build()

    return try {
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (DEBUG) Log.d(TAG, "Refresh Response: success (HTTP ${response.code})")
                responseBody?.let {
                    val responseJson = JSONObject(it)
                    storeJSONData(responseJson)
                    responseJson.optString(ACCESS_TOKEN, null)
                }
            } else {
                val code = response.code
                val errorBody = response.body?.string().orEmpty()
                val sanitizedError = sanitizeForLog(errorBody)
                Log.e(TAG, "Refresh failed: HTTP $code $sanitizedError")
                if (shouldPurgeOnRefreshError(code, errorBody)) {
                    Log.w(TAG, "Strava authorization permanently revoked. Triggering local data purge (REQ-EXT-009).")
                    TrainingApplication.getAppContext()?.let { ctx ->
                        StravaDataPurgeManager.purgeAllStravaData(ctx, alsoRevokeRemote = false)
                    }
                }
                null
            }
        }
    } catch (e: SocketTimeoutException) {
        Log.w(TAG, "Network timeout during Strava token refresh", e)
        null
    } catch (e: IOException) {
        Log.w(TAG, "I/O error during Strava token refresh", e)
        null
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error during Strava token refresh", e)
        null
    }
}

internal fun sanitizeForLog(input: String): String {
    if (input.isBlank()) return ""
    val trimmed = if (input.length > 200) input.take(200) + "... [truncated]" else input
    // Mask potential token/secret values
    return trimmed.replace(Regex("(?i)(client_secret|access_token|refresh_token)=\\w+"), "$1=***")
}
```

#### 4.1.3 Robust Structural Error Discrimination & Non-JSON Fallback (`REQ-EXT-011.1`)
```kotlin
internal fun shouldPurgeOnRefreshError(code: Int, errorBody: String): Boolean {
    if (code != 400 && code != 401) {
        return false
    }
    if (errorBody.isBlank()) {
        return false
    }

    try {
        val json = JSONObject(errorBody)

        // 1. Check for application/client credential errors -> STRICTLY DO NOT PURGE
        if (json.has("errors")) {
            val errorsArray = json.optJSONArray("errors")
            if (errorsArray != null) {
                var hasAppError = false
                var hasRefreshTokenInvalid = false

                for (i in 0 until errorsArray.length()) {
                    val err = errorsArray.optJSONObject(i) ?: continue
                    val resource = err.optString("resource")
                    val field = err.optString("field")
                    val errCode = err.optString("code")

                    if (field.equals("client_secret", ignoreCase = true) ||
                        field.equals("client_id", ignoreCase = true) ||
                        resource.equals("Application", ignoreCase = true)) {
                        hasAppError = true
                    }

                    if ((resource.equals("RefreshToken", ignoreCase = true) || field.equals("refresh_token", ignoreCase = true)) &&
                        errCode.equals("invalid", ignoreCase = true)) {
                        hasRefreshTokenInvalid = true
                    }
                }

                if (hasAppError) {
                    Log.w(TAG, "Strava token refresh failed due to application credential configuration error; suppressing purge.")
                    return false
                }

                if (hasRefreshTokenInvalid) {
                    Log.w(TAG, "Strava token refresh failed: refresh token permanently invalid.")
                    return true
                }
            }
        }

        // 2. Check top-level OAuth error field (e.g. {"error": "invalid_grant"})
        val error = json.optString("error")
        if (error.equals("invalid_grant", ignoreCase = true)) {
            Log.w(TAG, "Strava token refresh failed with invalid_grant.")
            return true
        }

        // 3. Check message or error description for explicit revocation
        val message = json.optString("message")
        val errorDesc = json.optString("error_description")
        if (message.contains("revoked", ignoreCase = true) || errorDesc.contains("revoked", ignoreCase = true)) {
            Log.w(TAG, "Strava token refresh indicates token revoked.")
            return true
        }

    } catch (e: JSONException) {
        // Non-JSON response (e.g., Cloudflare HTML error page or WAF block) -> STRICTLY DO NOT PURGE
        Log.w(TAG, "Non-JSON error response from Strava token refresh endpoint; defaulting to DO NOT PURGE.")
        return false
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error parsing Strava refresh error response; defaulting to DO NOT PURGE.", e)
        return false
    }

    return false
}
```

### 4.2 Component 2: `StravaUploader.kt` Refactoring (`REQ-EXT-011.5`)

Update `updateStravaActivity`, `getStravaJson`, and `getStravaJsonArray` to ensure no `Authorization: Bearer null` header is constructed:

```kotlin
internal open fun updateStravaActivity(stravaActivityId: String, requestBody: RequestBody): JSONObject? {
    Log.i(TAG, "updateStravaActivity $stravaActivityId")
    val token = StravaHelper.getRefreshedAccessToken()
    if (token.isNullOrBlank()) {
        Log.e(TAG, "Cannot update activity $stravaActivityId: missing valid access token")
        return null
    }

    val request = Request.Builder()
        .url(URL_STRAVA_ACTIVITY + stravaActivityId)
        .put(requestBody)
        .addHeader("Authorization", "Bearer $token")
        .build()

    return try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful && response.body != null) {
                JSONObject(response.body!!.string())
            } else {
                Log.e(TAG, "Update failed: ${response.code} ${response.message}")
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error updating activity", e)
        null
    }
}

private fun getStravaJson(url: String): JSONObject? {
    val token = StravaHelper.getRefreshedAccessToken()
    if (token.isNullOrBlank()) {
        Log.w(TAG, "Cannot fetch JSON from $url: missing valid access token")
        return null
    }

    val request = Request.Builder()
        .url(url)
        .get()
        .addHeader("Authorization", "Bearer $token")
        .build()

    return try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful && response.body != null) {
                JSONObject(response.body!!.string())
            } else {
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching JSON from $url", e)
        null
    }
}

internal open fun getStravaJsonArray(url: String): JSONArray? {
    val token = StravaHelper.getRefreshedAccessToken()
    if (token.isNullOrBlank()) {
        Log.w(TAG, "Cannot fetch JSONArray from $url: missing valid access token")
        return null
    }

    val request = Request.Builder()
        .url(url)
        .get()
        .addHeader("Authorization", "Bearer $token")
        .build()

    return try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful && response.body != null) {
                JSONArray(response.body!!.string())
            } else {
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching JSONArray from $url", e)
        null
    }
}
```

---

## 5. Step-by-Step Implementation Steps

### Step 1: Implement Synchronized Token Refresh and Error Discrimination in `StravaHelper.kt`
- Define `refreshLock = ReentrantLock()`, `TOKEN_EXPIRY_MARGIN_SECONDS = 300L`, `LOCK_WAIT_TIMEOUT_SECONDS = 15L`, and singleton `httpClient` with 15s connect/read and 20s call timeouts.
- Implement `sanitizeForLog(input: String): String`.
- Implement `shouldPurgeOnRefreshError(code: Int, errorBody: String): Boolean` with JSON and non-JSON fallback.
- Refactor `getRefreshedAccessToken()` with `tryLock(15, TimeUnit.SECONDS)`, double-checked locking, 300s expiry margin, and discrimination logic.
- Ensure timeouts return `null` safely without hanging or purging.

### Step 2: Implement Null-Safe Token Authorization in `StravaUploader.kt`
- In `updateStravaActivity`, resolve `val token = StravaHelper.getRefreshedAccessToken()`. If blank, log error and return `null`.
- In `getStravaJson`, resolve `val token = StravaHelper.getRefreshedAccessToken()`. If blank, log warning and return `null`.
- In `getStravaJsonArray`, resolve `val token = StravaHelper.getRefreshedAccessToken()`. If blank, log warning and return `null`.

### Step 3: Author Unit Tests for `StravaHelper` (`StravaHelperTest.kt`)
- Test `TST-EXT-008.1`: Application error (`client_secret: invalid`) returns `null` and NEVER invokes `purgeAllStravaData`.
- Test `TST-EXT-008.2`: Refresh token revocation (`refresh_token: invalid` or `invalid_grant`) returns `null` and invokes `purgeAllStravaData`.
- Test `TST-EXT-008.3`: 10 concurrent threads refreshing expired token execute exactly 1 network request; all 10 receive new token without thread starvation or ANR.
- Test `TST-EXT-008.4`: Token within 300s margin (`now + 200s`) triggers proactive refresh.
- Test `TST-EXT-008.5`: Valid token (`now + 3600s`) returns immediately with 0 network calls.
- Test `TST-EXT-008.7`: Non-JSON malformed error body (e.g. HTML 502/Cloudflare 400) defaults to DO NOT PURGE and returns `null`.
- Test `TST-EXT-008.8`: Network timeout (`SocketTimeoutException`) returns `null` cleanly without crashing or purging.

### Step 4: Author Unit Tests for `StravaUploader` (`StravaUploaderTest.kt`)
- Test `TST-EXT-008.6`: When `StravaHelper.getRefreshedAccessToken()` returns `null`, `updateStravaActivity`, `getStravaJson`, and `getStravaJsonArray` return `null` immediately and no request is dispatched with `Authorization: Bearer null`.

### Step 5: Clean-Room Regression Verification (`TST-EXT-008.9`)
- Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 6. Thread & Dispatcher Architecture

| Component / Method | Thread / Dispatcher | Concurrency & Synchronization Guard |
|---|---|---|
| `StravaHelper.getRefreshedAccessToken()` | Caller Thread (Worker / Repository) | `ReentrantLock` with `tryLock(15, TimeUnit.SECONDS)` double-checked locking monitor |
| Token Expiry Fast Path | Caller Thread | Lock-free check of expiry timestamp |
| Token Expiry Slow Path (inside lock) | Synchronized Caller Thread | Single active HTTP call; subsequent callers reuse cached token; bounded timeout prevents starvation |
| `OkHttpClient` Singleton | Network Dispatcher Pool | Reused singleton client with 15s connect/read and 20s call timeouts |
| `StravaUploader` Network Calls | Background Worker Thread | Null-safe token verification prior to `Request.Builder` |

---

## 7. Verification Commands

1. **Targeted Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest --tests "*StravaHelperTest*" --tests "*StravaUploaderTest*"
   ```
2. **Clean-Room Full Test Suite**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 8. Traceability Matrix

| Requirement Clause | Implementation Component | Implementation Step | Test Case ID | Status |
|---|---|---|---|---|
| `REQ-EXT-011.1` (Precise Revocation Discrimination & App Error Exclusion) | `StravaHelper.kt` | Step 1 | `TST-EXT-008.1`, `TST-EXT-008.2` | Planned |
| `REQ-EXT-011.1` (Non-JSON Error Fallback & Log Sanitization) | `StravaHelper.kt` | Step 1 | `TST-EXT-008.7` | Planned |
| `REQ-EXT-011.2` (Double-Checked Locking, Bounded ReentrantLock & Refresh Deduplication) | `StravaHelper.kt` | Step 1 | `TST-EXT-008.3` | Planned |
| `REQ-EXT-011.3` (300-Second Token Expiry Margin) | `StravaHelper.kt` | Step 1 | `TST-EXT-008.4`, `TST-EXT-008.5` | Planned |
| `REQ-EXT-011.4` (Shared Singleton OkHttpClient & Timeout Resilience) | `StravaHelper.kt` | Step 1 | `TST-EXT-008.1`–`TST-EXT-008.5`, `TST-EXT-008.8` | Planned |
| `REQ-EXT-011.5` (Safe Authorization Header Construction) | `StravaUploader.kt` | Step 2 | `TST-EXT-008.6` | Planned |
| `REQ-PRO-001` (Clean-Room Full Test Suite Regression Pass) | Project Test Suite | Step 5 | `TST-EXT-008.9` | Planned |
