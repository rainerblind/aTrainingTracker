# Stage 1: Problem Domain & Root Cause Analysis - ATT-3996: [High] Strava token refresh wipes all local Strava data on any invalid response

**Ticket**: [ATT-3996](https://rainerblind.atlassian.net/browse/ATT-3996)  
**Sub-task**: [ATT-4191](https://rainerblind.atlassian.net/browse/ATT-4191) (`[Analysis]`)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-EXT-009` (Refinement: `REQ-EXT-009.7` / `REQ-EXT-010`)  
**Test Mapping**: `TST-EXT-006` (Refinement: `TST-EXT-007`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Problem Statement

Independent whole-app code review of the Strava integration subsystem revealed a critical data-loss defect in `StravaHelper.getRefreshedAccessToken()` and associated API clients:
1. **Overly Broad Purge Condition**: The condition for triggering a permanent local Strava data purge (`StravaDataPurgeManager.purgeAllStravaData`) on token refresh failure is `errorBody.contains("invalid", ignoreCase = true)`. This substring matches application-level and build configuration errors (such as invalid `client_id` or `client_secret` during key rotation or build variants), wiping all athlete Strava upload records, starred segments, routes, and gear links from a background worker thread without user intent or confirmation.
2. **Unsynchronized Parallel Refresh Race Condition**: `StravaHelper.getRefreshedAccessToken()` lacks any concurrency locking or synchronization. When multiple background workers (`StravaRoutesSyncWorker`, `StravaSegmentsSyncWorker`, `StravaEquipmentSyncWorker`, and `ExportWorker` via `StravaUploader`) execute concurrently upon token expiry, multiple refresh requests are posted simultaneously to Strava. In OAuth 2.0 with refresh token rotation, the first request succeeds and invalidates the previous refresh token; the second request arrives with the now-invalidated refresh token, receives HTTP 400 (`refresh_token: invalid`), and triggers an irreversible local data wipe despite the token having just been refreshed successfully.
3. **Absence of Expiry Safety Margin**: The check `System.currentTimeMillis() / 1000 < TrainingApplication.getStravaTokenExpiresAt()` returns tokens expiring in 1–5 seconds. By the time downstream network requests or upload polling loops reach Strava, the token has expired, triggering avoidable 401 Unauthorized errors.
4. **Inefficient Resource Allocation**: A new `OkHttpClient` instance with its own thread pool and connection manager is instantiated on every single refresh invocation (`val client = OkHttpClient()`).
5. **Dangling `Bearer null` Headers**: In `StravaUploader.kt`, methods `updateStravaActivity`, `getStravaJson`, and `getStravaJsonArray` interpolate `${StravaHelper.getRefreshedAccessToken()}` directly into HTTP headers (`Authorization: Bearer null`), sending doomed requests over the wire when refresh fails rather than aborting early.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Overly Broad Substring Matching (`StravaHelper.kt:207-214`)
```kotlin
if (code == 400 || code == 401) {
    if (errorBody.contains("invalid", ignoreCase = true) || errorBody.contains("revoked", ignoreCase = true)) {
        Log.w(TAG, "Strava authorization permanently revoked. Triggering local data purge (REQ-EXT-009).")
        TrainingApplication.getAppContext()?.let { ctx ->
            StravaDataPurgeManager.purgeAllStravaData(ctx, alsoRevokeRemote = false)
        }
    }
}
```
When Strava rejects a refresh request due to an invalid client secret (e.g. key rotation or misconfigured build), it responds with HTTP 400:
```json
{
  "message": "Bad Request",
  "errors": [
    {"resource": "Application", "field": "client_secret", "code": "invalid"}
  ]
}
```
Because `"invalid"` is contained within `"code": "invalid"`, the system erroneously concludes the athlete deauthorized the app and wipes:
- `TrainingApplication.deleteStravaToken()`
- All upload activity records in `StravaUpload.db` via `StravaUploadDbHelper.clearAllStravaData()`
- All starred segments in `Segments.db` via `SegmentsDatabaseManager.deleteAllTables()`
- All Strava routes in `Routes.db` via `RoutesDatabaseManager.deleteRoutesBySource(RouteSource.STRAVA)`
- All Strava gear links in `Equipment.db` via `EquipmentDbHelper.unlinkAllStravaEquipment()`
- All recurring synchronization WorkManager jobs.

### 2.2 Unsynchronized Concurrency & Refresh Token Rotation Race
Strava issues a new `refresh_token` upon each token refresh and immediately revokes the previous refresh token. Without mutual exclusion:
- Thread 1 (e.g. `StravaSegmentsSyncWorker`) and Thread 2 (e.g. `StravaRoutesSyncWorker`) both read `now >= expiresAt`.
- Thread 1 posts `refresh_token_A`.
- Thread 2 posts `refresh_token_A`.
- Strava processes Thread 1: issues `access_token_1` and `refresh_token_B`, invalidates `refresh_token_A`.
- Strava processes Thread 2: `refresh_token_A` is now invalid -> HTTP 400 `refresh_token: invalid`.
- Thread 2's handler executes `purgeAllStravaData()`, wiping the user's data even though Thread 1 successfully obtained valid credentials.

### 2.3 Expiry Margin Gap
Tokens near expiration (`expires_at - now < 300 s`) should be refreshed eagerly before initiating multipart file uploads or polling loops that may take several minutes.

### 2.4 Null Token Header Propagation
In `StravaUploader.kt:597, 635, 656`:
```kotlin
.addHeader("Authorization", "Bearer ${StravaHelper.getRefreshedAccessToken()}")
```
If refresh returns `null`, the header sent is literally `"Authorization: Bearer null"`.

---

## 3. Chesterton's Fence Archaeology (`REQ-EXT-009`)

```text
git log -S REQ-EXT-009 docs/requirements.md
```
1. **Original Requirement ID & Target**: `REQ-EXT-009` (*Strava Data Deletion on Token Invalidation, Deauthorization & Reauthorization Integrity*). Targets `StravaHelper.kt`, `StravaDataPurgeManager.kt`, and `StravaUploader.kt`.
2. **Historical Origin & Commit Trace**: Commit `4270d06f` (ATT-1078) added automated revocation detection to comply with Strava API Policy Section 7.4 ("Deletion Obligation").
3. **Root Reason for Existing Formulation**: Strava API Policy mandates permanent deletion of athlete data when the athlete deauthorizes the application or the token is permanently revoked. The implementation attempted to detect revocation via HTTP 400 responses, but used an overly broad `contains("invalid")` check and omitted concurrency protection.
4. **Preservation of Core Invariants**:
   - Compliance with Strava API Policy Section 7.4 must be strictly maintained when the athlete **actually** deauthorizes the application (`refresh_token` is revoked or invalid, or explicit user disconnect).
   - Application configuration issues (`client_id`, `client_secret`, server-side 5xx errors) MUST NEVER trigger data purging.
   - Native athlete recordings (`WorkoutSummaries.db`, `WorkoutSamples.db`, `Laps.db`), local GPX routes, and hardware sensor pairings MUST remain 100% untouched.

---

## 4. Scope Bounding & Proposed Architecture

### 4.1 In-Scope Objectives
1. **Precise OAuth Error Classification (`StravaHelper.kt`)**:
   - Parse error response JSON structurally.
   - Trigger `purgeAllStravaData` ONLY if the error specifically targets `refresh_token` (`resource == "RefreshToken" || field == "refresh_token"` with `code == "invalid"`, or `error == "invalid_grant"`), AND explicitly exclude application errors (`field == "client_secret" || field == "client_id" || resource == "Application"`).
2. **Mutual Exclusion & Double-Checked Locking (`StravaHelper.kt`)**:
   - Protect `getRefreshedAccessToken()` with a reentrant lock (`refreshLock`).
   - Re-check token validity inside the lock (`expiresAt > now + 300 s`) before issuing an HTTP request, ensuring concurrent callers reuse the newly refreshed token without duplicate network requests.
3. **5-Minute Expiry Safety Margin (`StravaHelper.kt`)**:
   - Refresh token if `System.currentTimeMillis() / 1000 + 300 >= TrainingApplication.getStravaTokenExpiresAt()`.
4. **Singleton `OkHttpClient` Instance (`StravaHelper.kt`)**:
   - Reuse a centralized `OkHttpClient` with configured 15s connection/read timeouts.
5. **Null-Safe Authorization Headers (`StravaUploader.kt`)**:
   - Verify `accessToken != null` before constructing requests; abort cleanly if token cannot be refreshed instead of sending `Bearer null`.
6. **Automated Unit Testing (`StravaHelperTest.kt`)**:
   - Test invalid client secret does NOT trigger purge.
   - Test true refresh token revocation DOES trigger purge.
   - Test concurrent callers trigger exactly one network refresh request.
   - Test expiry margin triggers refresh before token expiration.
   - Test `StravaUploader` avoids `Bearer null` requests.

### 4.2 Out-of-Scope (Guarded Against Scope Creep)
- Modifying Strava OAuth web consent flow or Chrome Custom Tabs (`requestAccessToken`).
- Altering the Strava upload polling loop retry intervals or exponential backoff parameters.
- Re-architecting database schemas for `StravaUpload.db` or `Equipment.db`.

---

## 5. Acceptance Criteria (Given-When-Then)

- **Criterion 1 (Bad Client Secret Never Purges Data)**:
  - *Given* Strava API returns HTTP 400 with `field: client_secret, code: invalid` during token refresh,
  - *When* `StravaHelper.getRefreshedAccessToken()` executes,
  - *Then* it logs an error, returns `null`, and DOES NOT invoke `StravaDataPurgeManager.purgeAllStravaData()`.
- **Criterion 2 (True Token Revocation Triggers Purge)**:
  - *Given* Strava API returns HTTP 400 with `field: refresh_token, code: invalid` or `invalid_grant`,
  - *When* `StravaHelper.getRefreshedAccessToken()` executes,
  - *Then* it invokes `StravaDataPurgeManager.purgeAllStravaData()` to fulfill Strava API Policy Section 7.4.
- **Criterion 3 (Serialized Concurrent Refresh)**:
  - *Given* an expired token and multiple concurrent threads requesting `getRefreshedAccessToken()`,
  - *When* all threads invoke the method simultaneously,
  - *Then* exactly one HTTP POST request is sent to Strava, and all threads receive the valid refreshed access token.
- **Criterion 4 (Proactive 5-Minute Expiry Margin)**:
  - *Given* a token whose `expires_at` is 200 seconds in the future,
  - *When* `getRefreshedAccessToken()` is called,
  - *Then* it triggers a refresh request because `now + 300 >= expires_at`.
- **Criterion 5 (No `Bearer null` Headers)**:
  - *Given* a failed token refresh (`getRefreshedAccessToken() == null`),
  - *When* `StravaUploader` methods execute,
  - *Then* no HTTP request with `Authorization: Bearer null` is dispatched.
