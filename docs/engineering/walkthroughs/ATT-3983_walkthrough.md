# Stage 5 Walkthrough: ATT-3983 - Strava OAuth tokens and GPS polylines written to logcat in release builds

**Ticket**: [ATT-3983](https://atrainingtracker.atlassian.net/browse/ATT-3983)  
**Sub-task**: [ATT-4155](https://atrainingtracker.atlassian.net/browse/ATT-4155) (`[Test]`)  
**Parent Epic**: [ATT-597](https://atrainingtracker.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-SEC-041` (*Zero-Leak Logging, PII & Credential Sanitization, Build-Scoped Debug Flags, and Proactive Token Invalidation*)  
**Test Mapping**: `TST-SEC-038` (`TST-SEC-038.1` – `TST-SEC-038.6`)  
**Branch**: `feature/ATT-3983`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Root Cause Resolution

During whole-codebase security review of `feature/ATT-3921`, a `Highest` severity information disclosure defect was discovered where sensitive athlete credentials and high-precision GPS telemetry were emitted to Android system `logcat` in production release builds. Any third-party app or forensic adb inspection with `READ_LOGS` permissions on Android versions prior to 4.1 or via desktop debugging could harvest OAuth access tokens, refresh tokens, and home/work coordinates from GPS polylines.

### Root Causes
1. **OAuth Credential Dumps (`StravaHelper.kt:77`)**: `storeJSONData` dumped the complete OAuth response JSON via `Log.i(TAG, "storeJSONData: $jsonObject")`, leaking athlete IDs, access tokens, and long-lived refresh tokens.
2. **Refresh & Error Body Dumps (`StravaHelper.kt:197, 206`)**: `refreshAccessToken` logged raw HTTP response bodies and error response payloads.
3. **GPS Polyline Telemetry Dumps (`WorkoutRepository.kt:1464`)**: `updateWorkoutInList` logged `updatedWorkout.mapPolyline` (full encoded GPS coordinate string) at `INFO` level.
4. **Raw HTTP Payload Dumps (`StravaUploader.kt:254`, `StravaEquipmentSynchronizeThread.java:138`)**: Unconditionally dumped raw HTTP response payloads.
5. **Hardcoded Debug Constants**: Exactly 11 production classes hardcoded `DEBUG = true`, disabling release-mode R8 dead-code stripping of diagnostic logging.

### Resolutions
1. **OAuth Credential Sanitization (`StravaHelper.kt`)**: Redacted raw JSON and token dumps; diagnostic logging restricted strictly to boolean presence flags (`hasAthlete`, `hasAccessToken`) and HTTP status codes.
2. **GPS Polyline Redaction (`WorkoutRepository.kt`)**: Replaced `mapPolyline` log with boolean `hasRoute` flag guarded by `com.atrainingtracker.BuildConfig.DEBUG`.
3. **HTTP Payload Suppression (`StravaUploader.kt`, `StravaEquipmentSynchronizeThread.java`)**: Removed payload dumps; logged only HTTP status codes (`responseCode`).
4. **Standardization of Build-Scoped Debug Flags**: Replaced all 11 active `DEBUG = true` declarations across Java and Kotlin classes with `com.atrainingtracker.BuildConfig.DEBUG`.
5. **Non-Destructive Security Migration on V4.9.40 Upgrade (`StravaSecurityMigration.kt`)**: One-time migration key `strava_security_migration_v4_9_40` executed in `TrainingApplication.java:onCreate()`. Clears stored access and refresh tokens non-destructively, requiring athletes to negotiate fresh session tokens without deleting local athlete routes (`Routes.db`), equipment pairings (`Equipment.db`), or segment databases.
6. **Continuous CI/CD Privacy & Logcat Lint Guard (`LogcatPrivacySecurityTest.kt`)**: Static analysis test suite asserting zero `DEBUG = true` literals outside comments, regex validation forbidding sensitive variables (`mapPolyline`, `accessToken`, `refreshToken`, raw `responseBody`/`errorBody`) in all `Log.*` calls, unit test for token safety in `storeJSONData`, and migration idempotency.

---

## 2. Architectural Hardening (SWE.2 / SWE.3)

### 2.1 `StravaHelper.kt`
- Replaced `private const val DEBUG = true` with `private val DEBUG = BuildConfig.DEBUG`.
- Sanitized `storeJSONData(jsonObject)`:
  ```kotlin
  if (DEBUG) {
      Log.d(TAG, "storeJSONData: hasAthlete=${jsonObject.has("athlete")}, hasAccessToken=${jsonObject.has(ACCESS_TOKEN)}")
  }
  ```
- Sanitized `refreshAccessToken()`:
  ```kotlin
  if (DEBUG) Log.d(TAG, "Refresh Response: success (HTTP ${response.code})")
  // ...
  Log.e(TAG, "Refresh failed: HTTP $code ${response.message}")
  ```

### 2.2 `WorkoutRepository.kt`
- Redacted `mapPolyline` from `updateWorkoutInList`:
  ```kotlin
  if (com.atrainingtracker.BuildConfig.DEBUG) {
      val hasRoute = !updatedWorkout.mapPolyline.isNullOrEmpty()
      Log.d(TAG, "updateWorkoutInList: workoutId=$workoutId (${updatedWorkout.fileBaseName}), hasPolyline=$hasRoute, extremaRows: ${updatedWorkout.extremaRows}, exportStatuses: ${updatedWorkout.exportStatuses}")
  }
  ```

### 2.3 `StravaUploader.kt` & `StravaEquipmentSynchronizeThread.java`
- In `StravaUploader.kt`: bound `DEBUG` to `BuildConfig.DEBUG`, replaced raw response body dump with `if (DEBUG) Log.d(TAG, "uploadToStrava completed: responseCode=$responseCode")`.
- In `StravaEquipmentSynchronizeThread.java`: replaced raw payload log with `if (DEBUG) Log.d(TAG, "getStravaEquipment completed: responseCode=" + responseCode);`.

### 2.4 Standardization across 11 Production Classes
Standardized to `com.atrainingtracker.BuildConfig.DEBUG`:
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

### 2.5 Non-Destructive Security Migration (`StravaSecurityMigration.kt`)
- Created `StravaSecurityMigration.kt` with `runMigrationIfRequired(context: Context)`.
- Integrated into `TrainingApplication.java:onCreate()` immediately after database sanity checks.

### 2.6 Static CI/CD Privacy Guard (`LogcatPrivacySecurityTest.kt`)
- Full test suite verifying zero hardcoded debug flags, zero sensitive variable logging, token isolation, and migration idempotency.

---

## 3. Verification & Clean-Room Regression

### 3.1 Targeted Unit Test Suite (`LogcatPrivacySecurityTest.kt`)
* Command: `./gradlew testDebugUnitTest --tests "*LogcatPrivacySecurityTest*"`
* Result: **6 passed, 0 failed, 0 errors, 100% pass rate in 14.36s**
* Test cases:
  1. `testStravaHelperStoreJsonDataDoesNotLeakTokens`: PASSED
  2. `testStravaSecurityMigrationSecondRunIsIdempotent`: PASSED
  3. `verifyZeroHardcodedDebugTrueInMainSourceSet`: PASSED
  4. `testDiagnosticStatusLoggingPreservesStatusCodeWithoutBody`: PASSED
  5. `testStravaSecurityMigrationFirstRunFlagsReauthAndResetsTokens`: PASSED
  6. `verifyNoSensitiveDataPassedToLogcat`: PASSED

### 3.2 Full Clean-Room Regression Test Suite
* Command: `./gradlew testDebugUnitTest`
* Result: **BUILD SUCCESSFUL in 3m 1s, 0 failures across entire suite**

---

## 4. Traceability Matrix

| Requirement | Test Spec | Verification Artifact | Status |
| :--- | :--- | :--- | :--- |
| `REQ-SEC-041.1` (OAuth Credential Sanitization) | `TST-SEC-038.1`, `TST-SEC-038.3` | `StravaHelper.kt`, `LogcatPrivacySecurityTest.kt` | **Verified** |
| `REQ-SEC-041.2` (GPS Polyline PII Sanitization) | `TST-SEC-038.2` | `WorkoutRepository.kt`, `LogcatPrivacySecurityTest.kt` | **Verified** |
| `REQ-SEC-041.3` (HTTP Payload Sanitization) | `TST-SEC-038.3`, `TST-SEC-038.5` | `StravaUploader.kt`, `StravaEquipmentSynchronizeThread.java` | **Verified** |
| `REQ-SEC-041.4` (Standardization of Debug Flags) | `TST-SEC-038.1` | 11 production classes, `LogcatPrivacySecurityTest.kt` | **Verified** |
| `REQ-SEC-041.5` (Non-Destructive Security Migration) | `TST-SEC-038.4`, `TST-SEC-038.4b` | `StravaSecurityMigration.kt`, `TrainingApplication.java` | **Verified** |
| `REQ-SEC-041.6` (Static CI/CD Privacy Guard) | `TST-SEC-038.1` - `TST-SEC-038.6` | `LogcatPrivacySecurityTest.kt` | **Verified** |
