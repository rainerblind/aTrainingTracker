# Stage 3: Implementation Plan - ATT-4012: Strava upload misclassifies retryable errors and may attach to wrong activity

**Ticket**: [ATT-4012](https://rainerblind.atlassian.net/browse/ATT-4012)  
**Sub-task**: [ATT-4238](https://rainerblind.atlassian.net/browse/ATT-4238) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-EXP-021` (*Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection*)  
**Test Mapping**: `TST-EXP-018` (*Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection Verification*)  
**Branch**: `feature/ATT-4012`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

In the current Strava upload subsystem (`StravaUploader.kt`) and its database helper (`StravaUploadDbHelper.java`):
1. HTTP status code classification in `uploadToStrava` treats any non-200/201 response as permanent failure (`retry = false`). Rate limit responses (`429 Too Many Requests`) and server errors (`500, 502, 503, 504`) permanently abandon the upload instead of triggering WorkManager exponential backoff retries.
2. In the status polling loop, an intermittent network glitch returning a null JSON status on attempt 1 immediately terminates polling and marks the upload failed.
3. Pre-upload duplicate discovery (`findExistingStravaActivity`) falls into a trap if an activity on Strava has an unparseable date (`actEpoch = 0L`), resulting in `diff = 0L` which wins candidate selection. Furthermore, duplicate matching lacks sport compatibility and duration checks, allowing 2-minute false starts to match 2-hour workouts or cross-sport activities (run vs ride) to collide.
4. `response.body!!.string()` calls are unsafe and can throw `NullPointerException` on empty bodies.
5. In `StravaUploadDbHelper.java`, `getStravaActivityDataForWorkouts` builds SQL queries via raw string concatenation `IN ('...')` instead of parameterized `?` placeholders, and `updateOrInsert` does not execute atomic upsert semantics.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-EXP-021` (*Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection*)
* **Test Mapping**: `TST-EXP-018` (*Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing Strava asymmetric naming (`REQ-EXP-008`), duplicate reconciliation (`REQ-EXP-009`), segment PR updating (`REQ-EXP-010`), and offline rendering (`REQ-EXP-013`) remain 100% preserved.
2. **Backward Compatibility**: `StravaUploadDbHelper` table schema remains compatible; no destructive schema drops.
3. **Subtask Self-Sufficiency**: Subtask `ATT-4238` transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
4. **Parent Human Gate Invariance**: Final completion of `ATT-4012` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `StravaUploader.kt`
- **Error Classification**:
  - Check `responseCode`:
    - `200` or `201`: Success -> proceed with polling.
    - `429`: Transient rate limit. Extract headers `X-RateLimit-Limit` and `X-RateLimit-Usage`. Log warning. Return `ExportResult(false, true, "Rate limit exceeded: ...")`.
    - `500`, `502`, `503`, `504`: Transient server outage. Return `ExportResult(false, true, "Strava server error: $responseCode")`.
    - `401`: Unauthorized. Attempt `StravaHelper.getRefreshedAccessToken()`. If null or still failing, return `ExportResult(false, false, "Unauthorized: Strava access token invalid")`.
    - Other 4xx (`400`, `403`, `404`, `422`): Non-retryable client error. Return `ExportResult(false, false, "API Error: $responseCode - $responseBody")`.
- **Fault-Tolerant Polling**:
  - If `uploadStatusJsonAnswer == null`, do NOT set `exportResult = ExportResult(false, false, ...)` and do NOT break the loop. Log warning: `Log.w(TAG, "Attempt $attempt: getStravaUploadStatus returned null; continuing...")` and let the loop proceed to the next attempt with sleep.
  - If the loop finishes all `MAX_REQUESTS` without a terminal status, return `ExportResult(false, true, "Timeout waiting for Strava processing")`.
- **Duplicate Discovery Hardening**:
  - In `findExistingStravaActivity`:
    - Discard candidate if `actEpoch <= 0L`.
    - Extract candidate `sport_type` or `type` (e.g. `Ride`, `Run`, `Walk`, `Hike`).
    - Query target workout's `bSportType` and duration (`TIME_TOTAL`) in `findExistingStravaActivityForWorkout`.
    - Check sport compatibility: e.g. `BSportType.BIKE` is compatible with `Ride`, `MountainBikeRide`, `GravelRide`, `VirtualRide`, `EBikeRide`; `BSportType.RUN` is compatible with `Run`, `TrailRun`, `VirtualRun`. If local sport type is known and candidate is incompatible, discard candidate.
    - Check duration tolerance: If local workout duration $T > 0$, check Strava activity `elapsed_time` (or `moving_time`). If candidate duration differs by more than $\pm 25\%$ (or $\pm 900\text{ s}$), discard candidate.
- **Null Safety**:
  - In `updateStravaActivity`, `getStravaJson`, `getStravaJsonArray`:
    - Replace `response.body!!.string()` with `response.body?.string()?.takeIf { it.isNotBlank() }`.

### Component 2: `StravaUploadDbHelper.java`
- **Parameterized SQL**:
  - In `getStravaActivityDataForWorkouts`:
    ```java
    String[] placeholders = new String[fileNames.size()];
    Arrays.fill(placeholders, "?");
    String selection = WorkoutSummaries.FILE_BASE_NAME + " IN (" + android.text.TextUtils.join(",", placeholders) + ")";
    String[] selectionArgs = fileNames.toArray(new String[0]);
    ```
- **Upsert Semantics**:
  - In `updateOrInsert`:
    Use SQLite transaction or `insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)` so inserts and updates are atomic and thread-safe.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Harden `StravaUploadDbHelper.java` Parameterized Queries & Upsert
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/db/StravaUploadDbHelper.java`
* Changes:
  1. Replace string concatenation in `getStravaActivityDataForWorkouts` with `?` placeholders and `selectionArgs`.
  2. Refactor `updateOrInsert` to use atomic transaction / `CONFLICT_REPLACE`.
* Test: Author `StravaUploadDbHelperTest.kt` verifying batch query and upsert.

### Step 2: Implement Error Code Classification & Polling Fault Tolerance in `StravaUploader.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`
* Changes:
  1. Classify 429 and 5xx as `retry = true`; log `X-RateLimit-*` headers.
  2. Classify 4xx as `retry = false`.
  3. Ensure polling loop does not terminate early on transient null responses; return `retry = true` on timeout.
  4. Replace unsafe `response.body!!.string()` with safe null reads.
* Test: Update `StravaUploaderTest.kt` with mocked 429, 503, 400, transient null polling, and timeout assertions.

### Step 3: Harden Pre-Upload Duplicate Discovery in `StravaUploader.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`
* Changes:
  1. Skip candidates with `actEpoch <= 0L`.
  2. Implement sport type compatibility matching helper (`isSportCompatible(localSport: BSportType?, stravaType: String?)`).
  3. Implement duration tolerance check (`isDurationCompatible(localDurationSec: Long?, stravaElapsedSec: Long?)`).
  4. Query workout's `TIME_TOTAL` and `B_SPORT` / `SPORT_ID` in `getWorkoutMetadataForDuplicateCheck` to supply to matching logic.
* Test: Add unit tests in `StravaUploaderNamingTest.kt` covering unparseable date rejection, false-start 2-minute rejection for 2-hour workouts, and cross-sport rejection.

### Step 4: Verification via Targeted Tests
* Run commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.uploader.StravaUploaderTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.uploader.StravaUploaderNamingTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.db.*"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction, followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-4012`) allows clean revert to `sprint/2026-42.10` without risk to other tickets.
