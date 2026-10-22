# Stage 3: Implementation Plan - ATT-3983: [Highest] Strava OAuth tokens and GPS polylines written to logcat in release builds (Revised)

**Ticket**: [ATT-3983](https://atrainingtracker.atlassian.net/browse/ATT-3983)  
**Sub-task**: [ATT-4153](https://atrainingtracker.atlassian.net/browse/ATT-4153) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-597](https://atrainingtracker.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-SEC-041` (*Zero-Leak Logging, PII & Credential Sanitization, Build-Scoped Debug Flags, and Proactive Token Invalidation*)  
**Test Mapping**: `TST-SEC-038` (`TST-SEC-038.1` – `TST-SEC-038.6`)  
**Branch**: `feature/ATT-3983`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

In production builds of `aTrainingTracker`, sensitive athlete credentials and location PII are exposed via Android system `logcat`:
1. `StravaHelper.kt:77` logs the entire OAuth JSON response (`Log.i(TAG, "storeJSONData: $jsonObject")`), leaking long-lived `refresh_token`s, `access_token`s, and athlete profiles.
2. `StravaHelper.kt:197` and line 206 log full HTTP response and error bodies during token refresh.
3. `WorkoutRepository.kt:1464` logs `updatedWorkout.mapPolyline` (full GPS route coordinates) unconditionally at `INFO` level.
4. `StravaUploader.kt:254` and `StravaEquipmentSynchronizeThread.java:138` log raw HTTP response bodies.
5. Exactly 11 production classes hardcode `DEBUG = true`, preventing release-mode R8 dead-code stripping of debug logging.

### Auditor Feedback Incorporation (Revision 2)
In response to Independent Auditor feedback on Gate 3, this revised plan addresses:
1. **Non-Destructive Re-Authentication UX (Avoid Silent Data Purge)**: Instead of a destructive background wipe (`purgeAllStravaData`) that could discard local unsynced routes or equipment and break offline use, the system flags existing credentials for lazy re-authentication (`strava_reauth_required_v4_9_40`). Local athlete routes and equipment remain intact. When the user visits Strava settings or initiates an upload, they are prompted to refresh authorization securely.
2. **Robust Diagnostic Logging**: Preserves HTTP status codes, error messages, and endpoint headers (e.g. `Log.w(TAG, "Refresh failed: status=$code, message=${response.message}")`) while strictly omitting response bodies and tokens.
3. **Migration Test Coverage**: Verifies that the re-authentication flag is set idempotently once and does not interfere with active tracking or local databases.
4. **Enhanced Lint Guard**: Ensures `LogcatPrivacySecurityTest.kt` regex patterns detect both string interpolation (`$token`, `${...mapPolyline...}`) and string concatenation (`+ token`, `+ mapPolyline`) across all `Log.*` calls.
5. **Impact Scrutiny on 11 Classes**: Verifies that substituting `DEBUG = BuildConfig.DEBUG` strictly regulates logging without altering any business logic or database operations.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SEC-041`
  * `REQ-SEC-041.1`: OAuth Credential Sanitization (`StravaHelper.kt`)
  * `REQ-SEC-041.2`: Location & GPS Polyline PII Sanitization (`WorkoutRepository.kt`)
  * `REQ-SEC-041.3`: HTTP Payload Sanitization (`StravaUploader.kt`, `StravaEquipmentSynchronizeThread.java`)
  * `REQ-SEC-041.4`: Standardization of Build-Scoped Debug Flags (11 classes)
  * `REQ-SEC-041.5`: Non-Destructive Re-Authentication on V4.9.40 Upgrade (`StravaSecurityMigration`)
  * `REQ-SEC-041.6`: Static Lint Guard Test in Build Pipeline (`LogcatPrivacySecurityTest.kt`)
* **Test Mapping**: `TST-SEC-038` (`TST-SEC-038.1` – `TST-SEC-038.6`)

---

## 3. System Invariants & Preserved Behavior

1. **OAuth Flow Idempotency**: Strava token exchange, token refresh, and synchronization workflows must remain 100% operational.
2. **Diagnostic Visibility**: Developers running debug builds must still observe whether token refresh succeeded or failed with HTTP status codes and error categories without exposing token contents.
3. **Preservation of Local Data**: Local routes (`Routes.db`), athlete equipment (`Equipment.db`), and historical workout summaries are never deleted during security migration.
4. **Repository Reactive State**: Caching and state updates in `WorkoutRepository` must remain unmodified.
5. **Clean-Room Regression**: 100% of existing unit tests must pass without failure.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `StravaHelper.kt`
* Change `private const val DEBUG = true` to `private val DEBUG = BuildConfig.DEBUG`.
* In `storeJSONData(jsonObject)`:
  - Replace `Log.i(TAG, "storeJSONData: $jsonObject")` with:
    ```kotlin
    if (DEBUG) {
        Log.d(TAG, "storeJSONData: hasAthlete=${jsonObject.has("athlete")}, hasAccessToken=${jsonObject.has(ACCESS_TOKEN)}")
    }
    ```
* In `refreshAccessToken()`:
  - Replace `if (DEBUG) Log.d(TAG, "Refresh Response: $responseBody")` with:
    ```kotlin
    if (DEBUG) Log.d(TAG, "Refresh Response: success (HTTP ${response.code})")
    ```
  - In error branch, replace `Log.e(TAG, "Refresh failed: $code ${response.message} body: $errorBody")` with:
    ```kotlin
    Log.e(TAG, "Refresh failed: HTTP $code ${response.message}")
    ```

### Component 2: `WorkoutRepository.kt`
* In `updateWorkoutInList(workoutId: Long, updatedWorkout: WorkoutData)`:
  - Redact `mapPolyline` from `Log.i`:
    ```kotlin
    if (BuildConfig.DEBUG) {
        Log.d(TAG, "updateWorkoutInList: workoutId=$workoutId (${updatedWorkout.fileBaseName}), hasPolyline=${!updatedWorkout.mapPolyline.isNullOrEmpty()}, extremaRows: ${updatedWorkout.extremaRows}, exportStatuses: ${updatedWorkout.exportStatuses}")
    }
    ```

### Component 3: `StravaUploader.kt` & `StravaEquipmentSynchronizeThread.java`
* In `StravaUploader.kt:254`:
  - Replace raw response body log with:
    ```kotlin
    if (BuildConfig.DEBUG) Log.d(TAG, "uploadToStrava completed: responseCode=$responseCode")
    ```
* In `StravaEquipmentSynchronizeThread.java:138`:
  - Replace raw response log with:
    ```java
    if (DEBUG) Log.d(TAG, "getStravaEquipment completed: responseCode=" + responseCode);
    ```

### Component 4: Standardization of Build-Scoped Debug Flags across 11 Classes
Bind static `DEBUG` constants to `BuildConfig.DEBUG`:
1. `app/src/main/java/com/atrainingtracker/trainingtracker/fragments/ConfigViewFragment.java:30`
2. `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt:60`
3. `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/db/ExportStatusDatabaseManager.java:50`
4. `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportManager.java:62`
5. `app/src/main/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepository.kt:469`
6. `app/src/main/java/com/atrainingtracker/trainingtracker/segments/SegmentsRepository.kt:516`
7. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportNotificationManager.kt:202`
8. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt:67`
9. `app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelper.java:68`
10. `app/src/main/java/com/atrainingtracker/trainingtracker/database/SportTypeEquipmentLinkManager.kt:55`
11. `app/src/main/java/com/atrainingtracker/trainingtracker/smartwatch/pebble/PebbleService.java:71`

### Component 5: Non-Destructive Security Migration (`StravaSecurityMigration.kt`)
Create `com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaSecurityMigration`:
```kotlin
object StravaSecurityMigration {
    const val PREF_KEY_SECURITY_CHECK = "strava_security_migration_v4_9_40"

    @JvmStatic
    fun runMigrationIfRequired(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(PREF_KEY_SECURITY_CHECK, false)) {
            prefs.edit().putBoolean(PREF_KEY_SECURITY_CHECK, true).apply()
            // Invalidate existing cached tokens non-destructively so next sync/upload negotiates a fresh session
            if (TrainingApplication.hasStravaAccessToken()) {
                Log.i("StravaSecurityMigration", "V4.9.40 Security: Marking Strava tokens for fresh re-authentication")
                // Clear active in-memory and shared prefs token without wiping athlete database routes or equipment
                TrainingApplication.setStravaAccessToken("")
            }
        }
    }
}
```
Invoked in `TrainingApplication.java:onCreate()`.

### Component 6: CI/CD Static Lint Guard Test (`LogcatPrivacySecurityTest.kt`)
Create `app/src/test/java/com/atrainingtracker/trainingtracker/security/LogcatPrivacySecurityTest.kt`:
1. Recursively scans `app/src/main/` for Java/Kotlin files.
2. Asserts zero active `DEBUG = true` or `DEBUG=true` declarations outside comments.
3. Regex scanner: searches for `Log\.[vdiwe]\([^)]*(\bmapPolyline\b|\baccessToken\b|\brefreshToken\b|responseBody)` across string interpolations and concatenations.
4. Unit tests `StravaHelper.storeJSONData` asserting no tokens are logged.
5. Unit tests `StravaSecurityMigration.runMigrationIfRequired` asserting idempotent execution.

---

## 5. Atomic Implementation Steps (SWE.3)

### Step 1: Sanitize `StravaHelper.kt`
* Bind `DEBUG` to `BuildConfig.DEBUG`.
* Remove token dumps in `storeJSONData` and `refreshAccessToken`, retaining HTTP status codes and error categories.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt`.

### Step 2: Sanitize `WorkoutRepository.kt`
* Redact `mapPolyline` in `updateWorkoutInList`.
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`.

### Step 3: Sanitize `StravaUploader.kt` & `StravaEquipmentSynchronizeThread.java`
* Remove raw response payload dumps, retaining response codes.
* Targets: `StravaUploader.kt`, `StravaEquipmentSynchronizeThread.java`.

### Step 4: Migrate Hardcoded `DEBUG = true` Constants
* Update all 11 production classes to `BuildConfig.DEBUG`.

### Step 5: Implement `StravaSecurityMigration.kt` & Wire into `TrainingApplication.java`
* Non-destructively clear cached tokens on V4.9.40 upgrade and preserve all local routes and equipment.

### Step 6: Implement `LogcatPrivacySecurityTest.kt`
* Implement tests for `TST-SEC-038.1` through `TST-SEC-038.5`.
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/security/LogcatPrivacySecurityTest.kt`.
* Run targeted tests: `./gradlew testDebugUnitTest --tests "*LogcatPrivacySecurityTest*"`.

### Step 7: Full Clean-Room Regression Verification (`TST-SEC-038.6`)
* Run `./gradlew testDebugUnitTest`.
* Assert 100% pass rate across the full suite with 0 regressions.
