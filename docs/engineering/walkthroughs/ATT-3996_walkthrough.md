# Stage 5: Walkthrough & Verification - ATT-3996: [High] Strava token refresh wipes all local Strava data on any invalid response

**Ticket**: [ATT-3996](https://rainerblind.atlassian.net/browse/ATT-3996)  
**Sub-task**: [ATT-4195](https://rainerblind.atlassian.net/browse/ATT-4195) (`[Test]`)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-EXT-011` (extending `REQ-EXT-009`)  
**Test Mapping**: `TST-EXT-008` (extending `TST-EXT-006`)  
**Branch**: `feature/ATT-3996`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the end-to-end implementation and clean-room verification of **ATT-3996**, resolving a severe data-loss defect in Strava OAuth token refresh and network request authorization handling:
1. **Accidental Local Data Purge on Application Configuration / Credential Errors**:
   `StravaHelper.kt` previously executed a simplistic substring search `errorBody.contains("invalid")` on HTTP 400/401 errors, wiping all local Strava routes, starred segments, gear mappings, and upload history whenever an application-level credential mismatch (`client_secret: invalid`) occurred.
2. **Refresh Token Concurrency Race**:
   Unsynchronized background workers raced to refresh expiring tokens, causing parallel rotated refresh tokens to fail and trigger accidental data purging.
3. **Zero Expiry Margin**:
   Near-expired tokens were returned without proactive refresh, failing mid-flight during large file uploads or segment syncs.
4. **Per-Call OkHttpClient Allocations**:
   `OkHttpClient` was instantiated on every token refresh, leaking dispatcher threads and connection pools.
5. **Dangling `Authorization: Bearer null` Requests**:
   `StravaUploader` dispatched invalid `Bearer null` headers when token refresh returned null.

### Summary of Verified Changes
1. **Precise Revocation Discrimination & App Error Exclusion (`StravaHelper.kt`)**:
   - Implemented `shouldPurgeOnRefreshError(code: Int, errorBody: String): Boolean`.
   - Application configuration errors (`field == "client_secret"`, `field == "client_id"`, `resource == "Application"`) strictly suppress data purging.
   - Only true refresh token invalidation (`(resource == "RefreshToken" || field == "refresh_token") && code == "invalid"`, `error == "invalid_grant"`, or message containing `"revoked"`) triggers `StravaDataPurgeManager.purgeAllStravaData()`.
   - Non-JSON / malformed responses (e.g. Cloudflare / WAF HTML 502/400) default to DO NOT PURGE.
   - Masked secrets and truncated long responses via `sanitizeForLog(input: String)`.
2. **Thread-Safe Bounded Concurrency & Double-Checked Locking (`StravaHelper.kt`)**:
   - Synchronized `getRefreshedAccessToken()` using `ReentrantLock` with a 15-second bounded acquisition timeout (`tryLock(15, TimeUnit.SECONDS)`).
   - Applied double-checked locking: cached token expiry is checked first without locking, and re-verified inside the lock so racing callers reuse freshly obtained tokens without duplicate network calls.
3. **5-Minute (300s) Token Expiry Margin (`StravaHelper.kt`)**:
   - Defined `TOKEN_EXPIRY_MARGIN_SECONDS = 300L`. If remaining token lifetime $\le$ 300 seconds, proactive refresh is executed.
4. **Lazy Shared Singleton OkHttpClient (`StravaHelper.kt`)**:
   - Centralized `OkHttpClient` instance with explicit 15s connect/read and 20s call timeouts, initialized lazily to isolate classloading from test runners.
   - Handled `SocketTimeoutException` and `IOException` cleanly, returning `null` without throwing or purging data.
5. **Safe Authorization Header Construction (`StravaUploader.kt`)**:
   - In `updateStravaActivity`, `getStravaJson`, and `getStravaJsonArray`, verified `token.isNullOrBlank()`, returning `null` immediately and preventing dispatch of `Authorization: Bearer null` headers.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-EXT-011.1` | `TST-EXT-008.1` | Unit Test: Invalid `client_secret` never purges data (`StravaHelperTest.kt`) | **PASSED** | `Verified` |
| `REQ-EXT-011.1` | `TST-EXT-008.2` | Unit Test: True refresh token revocation (`refresh_token: invalid` & `invalid_grant`) triggers purge (`StravaHelperTest.kt`) | **PASSED** | `Verified` |
| `REQ-EXT-011.1` | `TST-EXT-008.7` | Unit Test: Non-JSON / HTML error bodies default to DO NOT PURGE (`StravaHelperTest.kt`) | **PASSED** | `Verified` |
| `REQ-EXT-011.2` | `TST-EXT-008.3` | Unit Test: 10 concurrent threads serialized to 1 HTTP call, 0 starvation (`StravaHelperTest.kt`) | **PASSED** | `Verified` |
| `REQ-EXT-011.3` | `TST-EXT-008.4` | Unit Test: 300s margin eagerly refreshes token expiring in 200s (`StravaHelperTest.kt`) | **PASSED** | `Verified` |
| `REQ-EXT-011.3` | `TST-EXT-008.5` | Unit Test: Valid unexpired token (now + 3600s) returns immediately without network (`StravaHelperTest.kt`) | **PASSED** | `Verified` |
| `REQ-EXT-011.4` | `TST-EXT-008.8` | Unit Test: Network timeout (`SocketTimeoutException`) safely returns null without crash or purge (`StravaHelperTest.kt`) | **PASSED** | `Verified` |
| `REQ-EXT-011.5` | `TST-EXT-008.6` | Unit Test: StravaUploader aborts cleanly on null/blank token, eliminates `Bearer null` (`StravaUploaderTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-EXT-008.9` | Clean-Room Full Test Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** | `Verified` |

---

## 3. Targeted Test Results

```bash
./gradlew testDebugUnitTest --tests "*Strava*"
```

**Output**:
- 147 tests executed across Strava modules.
- 0 failures, 0 errors, 0 skipped.
- Pass rate: **100%**.

---

## 4. Preserved Invariants & Boundary Verification

1. **Strava API Policy 7.4 Compliance**:
   True user revocation and unrecoverable OAuth invalidation continues to invoke `StravaDataPurgeManager.purgeAllStravaData()` as required by `REQ-EXT-009`.
2. **Local User Data Isolation**:
   Native workouts, local GPX tracks, and sensor pairings remain 100% protected and untouched during any Strava purge.
3. **No UI Changes**:
   No Jetpack Compose layouts, strings, or styles modified (Rule 23).
4. **Single Commit per Ticket**:
   All changes remain uncommitted on `feature/ATT-3996` ready for squash merge onto `sprint/2026-42.10` (Rule 44).
