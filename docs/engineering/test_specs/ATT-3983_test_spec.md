# Stage 2: Requirement & Test Specification - ATT-3983: [Highest] Strava OAuth tokens and GPS polylines written to logcat in release builds

**Ticket**: [ATT-3983](https://atrainingtracker.atlassian.net/browse/ATT-3983)  
**Sub-task**: [ATT-4152](https://atrainingtracker.atlassian.net/browse/ATT-4152) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-597](https://atrainingtracker.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-SEC-041` (*Zero-Leak Logging, PII & Credential Sanitization, Build-Scoped Debug Flags, and Proactive Token Invalidation*)  
**Test Spec ID**: `TST-SEC-038`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-SEC-041`)

### 1.1 Problem Statement & Threat Vector
In existing production releases, `StravaHelper.kt:77` logs the entire OAuth JSON response (`Log.i(TAG, "storeJSONData: $jsonObject")`), revealing the `access_token`, `refresh_token`, and athlete profile. Line 197 logs the full token refresh response body, while `WorkoutRepository.kt:1464` logs `updatedWorkout.mapPolyline` (full GPS route coordinates) unconditionally. Additionally, 11 production classes hardcode `DEBUG = true`, preventing release-mode dead-code stripping. This exposes user authentication tokens (granting full Strava write access) and home/commute location coordinates to logcat, system bug reports, and device forensic extractions.

### 1.2 Functional & Architectural Requirements (`REQ-SEC-041`)

1. **OAuth Credential Sanitization (`REQ-SEC-041.1`)**:
   - In `StravaHelper.kt`, `storeJSONData` and token refresh handlers SHALL NOT log raw JSON responses, `access_token` strings, `refresh_token` strings, or full athlete profile JSON payloads.
   - Diagnostic logging SHALL be restricted strictly to non-sensitive booleans and metadata (e.g. `hasAccessToken`, `athleteId`, HTTP status codes).

2. **Location & GPS Polyline PII Sanitization (`REQ-SEC-041.2`)**:
   - In `WorkoutRepository.kt`, `updateWorkoutInList` SHALL NOT log `updatedWorkout.mapPolyline` or raw GPS coordinate tracks.
   - Logging SHALL report only boolean presence (`hasPolyline`) and polyline character length.

3. **HTTP Payload Sanitization in Upload and Sync (`REQ-SEC-041.3`)**:
   - In `StravaUploader.kt:254` and `StravaEquipmentSynchronizeThread.java:138`, unconditional logging of raw HTTP response bodies SHALL be removed or replaced with upload IDs and status codes.

4. **Standardization of Build-Scoped Debug Flags (`REQ-SEC-041.4`)**:
   - All production classes hardcoding `DEBUG = true` (including `ConfigViewFragment`, `StravaHelper`, `ExportStatusDatabaseManager`, `ExportManager`, `LiveSegmentsRepository`, `SegmentsRepository`, `ExportNotificationManager`, `ControlTrackingViewModel`, `EquipmentDbHelper`, `SportTypeEquipmentLinkManager`, `PebbleService`) SHALL bind `DEBUG` to `BuildConfig.DEBUG` (or `TrainingApplication.getDebug(false)`), allowing R8 to dead-code strip diagnostic logs in release builds.

5. **Mandatory Programmatic Credential Invalidation on V4.9.40 Upgrade (`REQ-SEC-041.5`)**:
   - To neutralize any historical refresh tokens leaked into user device logcat buffers prior to V4.9.40, `TrainingApplication` / `StravaHelper` SHALL check a one-time migration key `strava_token_security_invalidated_v4_9_40`. If not set and an active Strava connection exists, the system SHALL asynchronously revoke the existing refresh token with Strava via `StravaDataPurgeManager.purgeAllStravaData(context, alsoRevokeRemote = true)` and set the migration key, requiring athletes to re-authenticate with a fresh, uncompromised token.

6. **Static Lint Guard Test in Build Pipeline (`REQ-SEC-041.6`)**:
   - `LogcatPrivacySecurityTest.kt` SHALL execute as a mandatory unit test in `./gradlew testDebugUnitTest`, asserting zero occurrences of `DEBUG = true` outside comments in `app/src/main/` and static regex verification prohibiting sensitive payload variables in `Log.*` invocations.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-SEC-041`, extending `REQ-EXT-009` (`ATT-1078`). Targets `StravaHelper.kt`, `WorkoutRepository.kt`, `StravaUploader.kt`, `StravaEquipmentSynchronizeThread.java`, `TrainingApplication.java`, and `LogcatPrivacySecurityTest.kt`.
2. **Historical Origin & Commit Trace**: Ticket `ATT-3983`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921`.
3. **Root Reason for Existing Formulation**: Verbose debugging payloads and hardcoded `DEBUG = true` were left over from initial prototyping in 2026-10.
4. **Preservation of Core Invariants**: Strava OAuth flow and sync state machine idempotent transitions remain intact; R8 release stripping enabled; 100% clean-room test pass rate strictly preserved.

---

## 2. Test Specification (`TST-SEC-038`)

### Test Case 1: OAuth Credential Sanitization Test (`TST-SEC-038.1`)
* **Scope**: Security Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/LogcatPrivacySecurityTest.kt`
* **Preconditions**: Mock `Log` static calls; create mock OAuth JSON containing sensitive `access_token = "secret_access_xyz"`, `refresh_token = "secret_refresh_abc"`, and athlete profile.
* **Action**: Invoke `StravaHelper.storeJSONData(jsonObject)`.
* **Expected Result**:
  - `Log.i`, `Log.d`, `Log.v`, `Log.w`, `Log.e` do not contain `"secret_access_xyz"` or `"secret_refresh_abc"`.
  - Credentials in `TrainingApplication` are stored correctly.

### Test Case 2: Location & GPS Polyline PII Sanitization Test (`TST-SEC-038.2`)
* **Scope**: Security Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/LogcatPrivacySecurityTest.kt`
* **Preconditions**: Create mock `WorkoutData` with a unique sensitive polyline string `mapPolyline = "_p~iF~ps|U_ulLnnqC_mqNvxq` (simulating home GPS coordinates).
* **Action**: Trigger repository update/log call.
* **Expected Result**:
  - No `Log.*` call contains the sensitive polyline string.
  - Logging reports metadata (`hasPolyline = true`, length).

### Test Case 3: HTTP Payload Sanitization Test (`TST-SEC-038.3`)
* **Scope**: Security Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/LogcatPrivacySecurityTest.kt`
* **Preconditions**: Mock response payloads for Strava upload and equipment sync.
* **Action**: Verify that log statements in `StravaUploader.kt` and `StravaEquipmentSynchronizeThread.java` do not dump raw response bodies.
* **Expected Result**: Raw JSON responses are excluded from logs.

### Test Case 4: Source Tree Static Lint Guard Test (`TST-SEC-038.4`)
* **Scope**: Static Analysis / Unit Test (Automated Lint Guard)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/LogcatPrivacySecurityTest.kt`
* **Preconditions**: Project repository root.
* **Action**: Scan all Java and Kotlin source files in `app/src/main/`:
  - Assert zero un-commented occurrences of `DEBUG = true` or `DEBUG=true`.
  - Assert zero `Log.*` calls printing `mapPolyline`, `accessToken`, or `refreshToken`.
* **Expected Result**: Scan passes with 0 violations across all production sources.

### Test Case 5: Mandatory Programmatic Credential Invalidation on Upgrade Test (`TST-SEC-038.5`)
* **Scope**: Functional / Security Integration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/security/LogcatPrivacySecurityTest.kt`
* **Preconditions**: Mock `cSharedPreferences` with existing Strava token and `strava_token_security_invalidated_v4_9_40 = false`.
* **Action**: Trigger startup migration check.
* **Expected Result**:
  - Programmatic remote deauthorization is initiated via `StravaDataPurgeManager`.
  - Local credentials purged.
  - Migration key `strava_token_security_invalidated_v4_9_40` is set to `true`.

### Test Case 6: Clean-Room Full Suite Regression Execution (`TST-SEC-038.6`)
* **Scope**: Clean-room regression test
* **Action**: Run `./gradlew testDebugUnitTest`.
* **Expected Result**: 100% test pass rate across the full suite with 0 regressions.

---

## 3. Traceability Matrix

| Requirement | Test Spec | Scope | Test Target | Verification Target |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SEC-041.1` | `TST-SEC-038.1` | Security | `StravaHelper.storeJSONData()` | No access/refresh tokens in logs |
| `REQ-SEC-041.2` | `TST-SEC-038.2` | Privacy | `WorkoutRepository.updateWorkoutInList()` | No GPS polyline in logs |
| `REQ-SEC-041.3` | `TST-SEC-038.3` | Security | `StravaUploader`, `StravaEquipmentSynchronizeThread` | No raw response bodies in logs |
| `REQ-SEC-041.4` | `TST-SEC-038.4` | Code Quality | 11 production classes with `DEBUG = true` | Zero `DEBUG = true` in `app/src/main` |
| `REQ-SEC-041.5` | `TST-SEC-038.5` | Security | `TrainingApplication`, `StravaDataPurgeManager` | Remote deauth & local purge on upgrade |
| `REQ-SEC-041.6` | `TST-SEC-038.4` | CI/CD Lint | Entire `app/src/main/` source tree | Automated blocking regression test |
| `REQ-SEC-041` | `TST-SEC-038.6` | Clean-Room | Full test suite (`testDebugUnitTest`) | 100% pass rate, 0 regressions |
