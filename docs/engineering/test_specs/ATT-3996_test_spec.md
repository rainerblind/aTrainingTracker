# Stage 2: Requirement & Test Specification - ATT-3996: [High] Strava token refresh wipes all local Strava data on any invalid response

**Ticket**: [ATT-3996](https://rainerblind.atlassian.net/browse/ATT-3996)  
**Sub-task**: [ATT-4192](https://rainerblind.atlassian.net/browse/ATT-4192) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-EXT-011` (extending `REQ-EXT-009`)  
**Test Mapping**: `TST-EXT-008` (extending `TST-EXT-006`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Formal Requirement Specification (`REQ-EXT-011`)

### REQ-EXT-011: Resilient Strava Token Refresh Concurrency, Expiry Margin, Precise Revocation Discrimination & Safe Header Construction

`StravaHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt`) and `StravaUploader` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`) SHALL guarantee that token refresh operations never trigger accidental local Strava data deletion on application configuration errors, synchronize concurrent token refresh requests with double-checked locking, enforce a 300-second token expiry safety margin, reuse a singleton HTTP client, and prevent dispatching requests with `Authorization: Bearer null` headers (ATT-3996, extending `REQ-EXT-009`):

1. **Precise Revocation Discrimination & Exclusion of Application Errors (`REQ-EXT-011.1`)**:
   - `StravaHelper.getRefreshedAccessToken()` SHALL parse OAuth token refresh failure responses (HTTP 400 / HTTP 401) structurally via JSON.
   - The system SHALL trigger `StravaDataPurgeManager.purgeAllStravaData()` ONLY IF the response specifically indicates that the `refresh_token` itself is invalid or revoked:
     - Standard OAuth error `"invalid_grant"` (without client credential mismatch), OR
     - Strava errors array contains an entry where `(resource == "RefreshToken" || field == "refresh_token") && code == "invalid"`, OR
     - Response message contains `"revoked"` (case-insensitive).
   - If the error response contains `field == "client_secret"`, `field == "client_id"`, or `resource == "Application"`, or any generic parameter/server error, `StravaHelper` SHALL NOT trigger `StravaDataPurgeManager.purgeAllStravaData()`. It SHALL log an error and return `null` without deleting local data.
2. **Thread-Safe Reentrant Token Refresh & Double-Checked Locking (`REQ-EXT-011.2`)**:
   - `StravaHelper` SHALL synchronize concurrent invocations of `getRefreshedAccessToken()` via a reentrant lock or monitor (`refreshLock`).
   - Inside the synchronized block, `StravaHelper` SHALL re-verify token validity: if a preceding concurrent thread has already refreshed the token such that `now + 300 < expiresAt`, `getRefreshedAccessToken()` SHALL return `TrainingApplication.getStravaAccessToken()` immediately without sending a duplicate network request.
3. **5-Minute (300-Second) Token Expiry Margin (`REQ-EXT-011.3`)**:
   - `StravaHelper` SHALL consider a cached access token valid if and only if `System.currentTimeMillis() / 1000 + 300 < TrainingApplication.getStravaTokenExpiresAt()`.
   - If remaining token validity is less than or equal to 300 seconds (5 minutes), `StravaHelper` SHALL execute a preemptive token refresh.
4. **Singleton HTTP Client Reuse (`REQ-EXT-011.4`)**:
   - `StravaHelper` SHALL reuse a shared singleton `OkHttpClient` instance configured with 15-second connect and read timeouts, eliminating per-call client allocations and thread pool leaks.
5. **Safe Authorization Header Construction (`REQ-EXT-011.5`)**:
   - In `StravaUploader.kt`, methods `updateStravaActivity`, `getStravaJson`, and `getStravaJsonArray` SHALL resolve `val accessToken = StravaHelper.getRefreshedAccessToken()` prior to building HTTP requests.
   - If `accessToken.isNullOrBlank()`, the method SHALL log an error/warning and return `null` immediately without creating or dispatching an HTTP request containing `"Authorization: Bearer null"`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement `REQ-EXT-011`, extending `REQ-EXT-009` under Epic `ATT-597` (*Strava Support*). Targets `StravaHelper.kt` and `StravaUploader.kt`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-3996`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). `REQ-EXT-009` was introduced in commit `4270d06f` (ATT-1078) to comply with Strava API Policy Section 7.4.
3. **Root Reason for Existing Formulation**: `REQ-EXT-009` specified automated data purging upon unrecoverable OAuth revocation (`invalid_grant` / HTTP 400). However, the implementation in `StravaHelper.kt` used a simplistic substring check `errorBody.contains("invalid")`, causing non-revocation errors (such as `client_secret: invalid`) and unsynchronized concurrent refresh collisions to wipe all local data.
4. **Preservation of Core Invariants**: Full compliance with Strava API Policy Section 7.4 is strictly preserved when the refresh token is truly revoked or invalid. Native workouts, local GPX routes, and hardware pairings in `Equipment.db` remain 100% protected.

---

## 3. Concrete Verification Test Specification (`TST-EXT-008`)

### TST-EXT-008: Strava Token Refresh Concurrency, Expiry Margin, Precise Revocation Discrimination & Header Verification Suite

| Test ID | Method / Target | Description & Verification Steps |
| :--- | :--- | :--- |
| `TST-EXT-008.1` | `StravaHelperTest.kt` | **Invalid Client Secret Never Purges Data**:<br>• Mock Strava `/oauth/token` endpoint returning HTTP 400 with `{"message":"Bad Request","errors":[{"resource":"Application","field":"client_secret","code":"invalid"}]}`.<br>• Call `StravaHelper.getRefreshedAccessToken()`.<br>• Verify result is `null` and `StravaDataPurgeManager.purgeAllStravaData` is NEVER called. |
| `TST-EXT-008.2` | `StravaHelperTest.kt` | **True Refresh Token Revocation Triggers Data Purge**:<br>• Mock Strava `/oauth/token` returning HTTP 400 with `{"message":"Bad Request","errors":[{"resource":"RefreshToken","field":"refresh_token","code":"invalid"}]}`.<br>• Call `StravaHelper.getRefreshedAccessToken()`.<br>• Verify result is `null` and `StravaDataPurgeManager.purgeAllStravaData` is invoked exactly once. |
| `TST-EXT-008.3` | `StravaHelperTest.kt` | **Concurrent Token Refresh Serialized & Deduplicated**:<br>• Set token as expired.<br>• Launch 10 concurrent threads invoking `StravaHelper.getRefreshedAccessToken()`.<br>• Mock network call with simulated latency (50 ms).<br>• Verify exactly 1 network HTTP POST request is executed, and all 10 threads receive the new token. |
| `TST-EXT-008.4` | `StravaHelperTest.kt` | **5-Minute (300s) Expiry Margin Refreshes Eagerly**:<br>• Set token `expires_at = (now + 200s)` (still valid in raw epoch, but within 300s margin).<br>• Call `StravaHelper.getRefreshedAccessToken()`.<br>• Verify a refresh request is initiated to obtain a fresh token. |
| `TST-EXT-008.5` | `StravaHelperTest.kt` | **Valid Unexpired Token Returns Immediately Without Network**:<br>• Set token `expires_at = (now + 3600s)`.<br>• Call `StravaHelper.getRefreshedAccessToken()`.<br>• Verify cached token is returned and zero network calls are made. |
| `TST-EXT-008.6` | `StravaUploaderTest.kt` | **StravaUploader Aborts Cleanly on Null Token & Avoids Bearer null**:<br>• Mock `StravaHelper.getRefreshedAccessToken()` returning `null`.<br>• Call `updateStravaActivity`, `getStravaJson`, `getStravaJsonArray`.<br>• Verify all return `null` immediately and no HTTP request is dispatched with `Authorization: Bearer null`. |
| `TST-EXT-008.7` | `StravaHelperTest.kt` | **Non-JSON / Malformed Response Defaults to DO NOT PURGE**:<br>• Mock Strava `/oauth/token` returning HTTP 400/502 with HTML content `<html><body>Cloudflare Error</body></html>`.<br>• Call `StravaHelper.getRefreshedAccessToken()`.<br>• Verify result is `null` and `StravaDataPurgeManager.purgeAllStravaData` is NEVER called. |
| `TST-EXT-008.8` | `StravaHelperTest.kt` | **Network Timeout Handling Cleanly Returns Null**:<br>• Mock `httpClient` throwing `SocketTimeoutException`.<br>• Call `StravaHelper.getRefreshedAccessToken()`.<br>• Verify result is `null` and no exceptions escape or purge data. |
| `TST-EXT-008.9` | Clean-Room Suite | **Clean-Room Regression Suite Execution**:<br>• Execute `./gradlew testDebugUnitTest` verifying 100% pass rate. |

---

## 4. Traceability Matrix

| Requirement | Test Spec | Target Class / Module |
| :--- | :--- | :--- |
| `REQ-EXT-011.1` | `TST-EXT-008.1`, `TST-EXT-008.2`, `TST-EXT-008.7` | `StravaHelper.kt` |
| `REQ-EXT-011.2` | `TST-EXT-008.3` | `StravaHelper.kt` |
| `REQ-EXT-011.3` | `TST-EXT-008.4`, `TST-EXT-008.5` | `StravaHelper.kt` |
| `REQ-EXT-011.4` | `TST-EXT-008.1`–`TST-EXT-008.5`, `TST-EXT-008.8` | `StravaHelper.kt` |
| `REQ-EXT-011.5` | `TST-EXT-008.6` | `StravaUploader.kt` |
| `REQ-PRO-001` | `TST-EXT-008.9` | Full test suite |

