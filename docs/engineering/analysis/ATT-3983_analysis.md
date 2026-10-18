# Stage 1: Problem Domain & Root Cause Analysis - ATT-3983: [Highest] Strava OAuth tokens and GPS polylines written to logcat in release builds (Revision 3)

**Ticket**: [ATT-3983](https://atrainingtracker.atlassian.net/browse/ATT-3983)  
**Sub-task**: [ATT-4151](https://atrainingtracker.atlassian.net/browse/ATT-4151) (`[Analysis]`)  
**Parent Epic**: [ATT-597](https://atrainingtracker.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Priority**: `Highest`  
**Labels**: `code-review-2026-10`, `privacy`, `security`, `strava`  
**Requirement Mapping**: `REQ-SEC-041` (*Zero-Leak Logging, PII & Credential Sanitization, Build-Scoped Debug Flags, and Proactive Token Invalidation*)  
**Test Spec ID**: `TST-SEC-038`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Domain & Scope Bounding

### 1.1 Vulnerability Overview
During a security and privacy audit of the codebase, multiple high-severity information disclosure vulnerabilities were identified:
1. **Strava OAuth Token Leakage**:
   - `StravaHelper.kt:77`: `storeJSONData(jsonObject)` executes `Log.i(TAG, "storeJSONData: $jsonObject")` whenever OAuth credentials are exchanged or loaded. `jsonObject` contains athlete profile data, the active `access_token`, and crucially, the long-lived `refresh_token` (granting full `activity:write` permissions).
   - `StravaHelper.kt:197`: `Log.d(TAG, "Refresh Response: $responseBody")` logs the complete HTTP response body of the token refresh call, leaking new `access_token` and `refresh_token` strings.
   - `StravaHelper.kt:206`: `Log.e(TAG, "Refresh failed: $code ${response.message} body: $errorBody")` unconditionally logs raw response bodies on refresh errors.
2. **GPS Polyline / Sensitive Location Leakage**:
   - `WorkoutRepository.kt:1464`: `updateWorkoutInList` executes `Log.i(TAG, "updateWorkoutInList: workoutId=$workoutId (${updatedWorkout.fileBaseName}), mapPolyline: ${updatedWorkout.mapPolyline}, extremaRows: ${updatedWorkout.extremaRows}, exportStatuses: ${updatedWorkout.exportStatuses}")` whenever workout details or export statuses are refreshed. `mapPolyline` contains the encoded GPS coordinate track, exposing exact residential and workplace addresses.
3. **HTTP Response Body Dumps**:
   - `StravaUploader.kt:254`: `Log.i(TAG, "uploadToStrava responseCode=$responseCode, response: $responseBody")` dumps full Strava upload payloads into logcat at `INFO` level unconditionally.
   - `StravaEquipmentSynchronizeThread.java:138`: `if (DEBUG) Log.d(TAG, "getStravaEquipment response: " + response)` logs raw athlete profile and equipment JSON responses.
4. **Hardcoded `DEBUG = true` in Production Sources**:
   - Exactly 11 production Java and Kotlin classes declare `DEBUG = true` as static constants rather than deriving from `BuildConfig.DEBUG` or `TrainingApplication.getDebug(false)`:
     1. `ConfigViewFragment.java:30`
     2. `StravaHelper.kt:60`
     3. `ExportStatusDatabaseManager.java:50`
     4. `ExportManager.java:62`
     5. `LiveSegmentsRepository.kt:469`
     6. `SegmentsRepository.kt:516`
     7. `ExportNotificationManager.kt:202`
     8. `ControlTrackingViewModel.kt:67`
     9. `EquipmentDbHelper.java:68`
     10. `SportTypeEquipmentLinkManager.kt:55`
     11. `PebbleService.java:71`

### 1.2 Severity & Threat Model
* **Threat Actor**: Any third-party application on the device holding `READ_LOGS` (prior to Android 4.1 or on rooted devices), debugging tools over ADB (`adb logcat`), automated crash-reporting libraries, or exported Android bug reports (`bugreport.zip`).
* **Impact**:
  - Full unauthorized hijacking of athlete Strava accounts (ability to upload, alter, or delete workouts via leaked `refresh_token`).
  - Privacy breach of athlete residential, commuting, and recreational GPS tracks through leaked encoded polylines.
  - Release build log pollution.

---

## 2. External SDK Audit & Log Data Lifecycle

### 2.1 External Crash Reporting SDK Inspection
An exhaustive audit of the project dependencies and source code was performed regarding third-party crash reporting and analytics SDKs:
- **Firebase Crashlytics**:
  - `com.google.firebase:firebase-crashlytics` is present in `app/build.gradle`.
  - **Audit Finding**: Grepping for `FirebaseCrashlytics.getInstance().log(...)` and custom log interceptors confirmed that standard `android.util.Log` output is **NOT** forwarded or captured by Firebase Crashlytics. Crashlytics only attaches its own explicitly logged breadcrumbs.
- **Other Logging Libraries**:
  - No instances of `Timber`, `Sentry`, or custom log forwarding trees exist in the codebase. All logging statements invoke standard Android `android.util.Log` primitives, which write strictly to `/dev/log/main` via the Android platform `liblog`.

### 2.2 Device Logcat Ring Buffer Characteristics
The Android OS manages logcat through an in-memory kernel ring buffer (`logd`) partitioned into `main`, `system`, and `crash`. On typical consumer devices, this buffer is dynamically sized between 256 KB and 4 MB. At normal device logging velocity, historical entries are naturally overwritten in FIFO order within minutes to hours. However, in the event that an athlete generates a system bug report (`adb bugreport` or developer options bug report), historical logcat buffers are frozen into persistent zip archives.

---

## 3. Mandatory Remediation Strategy & Threat Neutralization

### 3.1 Mandatory Programmatic Token Invalidation on V4.9.40 Upgrade
Relying on user advisories or FIFO buffer overwrite alone is insufficient to protect athletes whose historical tokens may have been logged. Therefore, the system will execute a mandatory, programmatic token invalidation on upgrade:
1. In `TrainingApplication.onCreate()` or `StravaHelper`:
   - Inspect a one-time migration preference key: `strava_token_security_invalidated_v4_9_40`.
   - If `false` (fresh upgrade) and an existing Strava connection/token is detected:
     - Invoke `StravaDataPurgeManager.purgeAllStravaData(context, alsoRevokeRemote = true)` asynchronously on `Dispatchers.IO`.
     - This sends an HTTP POST to `https://www.strava.com/oauth/deauthorize` with the current token, immediately revoking the refresh token with Strava's servers, and clears local credentials.
     - Set `strava_token_security_invalidated_v4_9_40 = true`.
   - Result: Any previously logged historical `refresh_token` becomes instantly invalid and worthless to an attacker. The athlete simply re-authenticates with fresh, uncompromised credentials when they next use Strava sync.

### 3.2 Call-Site PII & Credential Sanitization
1. **Never Log Sensitive Payloads (Principle of Data Minimization)**:
   - Credentials (access tokens, refresh tokens, auth headers) MUST NEVER be written to `Logcat` under any log level (`v`, `d`, `i`, `w`, `e`), not even in debug builds.
   - GPS tracks, raw coordinates, and encoded polylines MUST NEVER be dumped into logs. Only high-level metadata (e.g. `hasPolyline: true`, coordinate count, or workout ID) may be logged.
2. **Consolidated Call-Site Sanitization**:
   - `StravaHelper.kt`:
     - Line 77: replace `storeJSONData: $jsonObject` with sanitized logging: `hasAccessToken: Boolean`, `athleteId: Int?`.
     - Line 197: replace `Refresh Response: $responseBody` with `Refresh successful: hasToken=${responseJson.has(ACCESS_TOKEN)}`.
     - Line 206: sanitize error logging, omitting raw error bodies if they might contain credentials.
   - `WorkoutRepository.kt`:
     - Line 1464: replace `mapPolyline: ${updatedWorkout.mapPolyline}` with `hasPolyline: ${!updatedWorkout.mapPolyline.isNullOrEmpty()}` and `polylineLength: ${updatedWorkout.mapPolyline?.length ?: 0}`.
   - `StravaUploader.kt`:
     - Line 254: remove raw `response: $responseBody` logging; log only `responseCode` and `activityId` / `uploadId`.
   - `StravaEquipmentSynchronizeThread.java`:
     - Line 138: replace raw `response` logging with equipment count summary.
3. **Standardize Build-Scoped Debug Flags**:
   - All 11 classes with hardcoded `DEBUG = true` must be switched to `BuildConfig.DEBUG` (or `TrainingApplication.getDebug(false)` in Java device drivers).

### 3.3 CI/CD Mandatory Blocking Static Lint Guard Test
Author `LogcatPrivacySecurityTest.kt` to run as a blocking unit test in `./gradlew testDebugUnitTest`:
1. Scans all Java and Kotlin source files in `app/src/main/` and asserts that zero un-commented `DEBUG = true` or `DEBUG=true` declarations exist.
2. Scans AST / regex patterns across all `Log.[vdiwe]` invocations to assert that forbidden variables (`mapPolyline`, `accessToken`, `refreshToken`, raw `responseBody` from OAuth/token endpoints) are strictly absent.

### 3.4 Formal Security Advisory for Release Notes
The V4.9.40 release notes will publish the following formal Security Advisory:
> **SECURITY ADVISORY (ATT-3983)**:
> Previous releases could include Strava OAuth tokens or GPS polylines in temporary local device logcat buffers when developer debugging options were enabled.
> As part of V4.9.40, all sensitive token and coordinate logging has been eliminated, debug flags are bound strictly to debug builds, and all existing Strava authorizations are programmatically revoked and reset upon upgrading. Athletes who use Strava synchronization will be prompted to reconnect once with fresh, secure credentials.

---

## 4. Invariants to Preserve

1. **OAuth Flow Idempotency**: Strava connection, token refresh, and data synchronization workflows must remain 100% functional and idempotent. Removing logs must not affect the HTTP request/response state machine.
2. **Diagnostic Usability**: Developers running debug builds must still be able to determine if token refresh succeeded or failed without exposing token contents (e.g., logging `token_type`, `expires_at`, and HTTP status codes).
3. **Repository Reactive State**: Caching and state updates in `WorkoutRepository` must remain unmodified.
4. **Clean-Room Regression**: 100% of existing unit tests must pass without regressions.
