# Stage 2: Requirement & Test Specification - ATT-4012: Strava upload misclassifies retryable errors and may attach to wrong activity

**Ticket**: [ATT-4012](https://rainerblind.atlassian.net/browse/ATT-4012)  
**Sub-task**: [ATT-4237](https://rainerblind.atlassian.net/browse/ATT-4237) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-597](https://rainerblind.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-EXP-021` (*Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection*)  
**Test Spec ID**: `TST-EXP-018`  
**Branch**: `feature/ATT-4012`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-EXP-021)

### 1.1 Problem Statement & Rationale
Under current behavior, `StravaUploader.uploadToStrava` treats any response code other than 200/201 as a non-retryable failure (`retry = false`), permanently marking workouts as failed during Strava rate limit events (`429`) or server outages (`5xx`). Polling status checks immediately abort upon receiving a single transient null response. Pre-upload duplicate discovery matches activities within $\pm 300\text{ s}$ without sport type or duration validation, allowing 2-minute false starts to swallow 2-hour workouts, and gives invalid dates (`actEpoch = 0L`) priority. Additionally, unsanitized SQL string concatenation in `StravaUploadDbHelper.java` creates potential vulnerabilities.

### 1.2 Functional & Architectural Requirements
1. **HTTP Status Code Classification**:
   - `StravaUploader.uploadToStrava` SHALL classify HTTP 429 (`Too Many Requests`) and HTTP 5xx (`500`, `502`, `503`, `504`) as retryable failures (`ExportResult(false, true, ...)`).
   - On HTTP 429, `X-RateLimit-Limit` and `X-RateLimit-Usage` headers SHALL be extracted and logged if present.
   - HTTP 4xx client errors (`400`, `403`, `404`, `422`) SHALL return `ExportResult(false, false, ...)`.
   - On HTTP 401 (`Unauthorized`), the uploader SHALL attempt a token refresh via `StravaHelper.getRefreshedAccessToken()`; if unsuccessful or still unauthorized, it SHALL return `ExportResult(false, false, ...)`.
2. **Resilient Polling Loop**:
   - During upload status polling (`getStravaUploadStatus`), transient null responses SHALL NOT immediately terminate the polling loop. The system SHALL log a warning and continue polling up to `MAX_REQUESTS`.
   - If polling exhausts `MAX_REQUESTS` without reaching a terminal status (`ready`, `error`, `deleted`), it SHALL return `ExportResult(false, true, "Timeout waiting for Strava processing")`.
3. **Hardened Pre-Upload Duplicate Discovery**:
   - In `findExistingStravaActivity`, candidate activities whose `start_date` cannot be parsed into a positive epoch timestamp (`actEpoch <= 0L`) SHALL be skipped.
   - *Sport Type Guard*: Candidates SHALL only match if candidate sport type is compatible with the workout's `BSportType` (e.g. `BSportType.BIKE` matches `Ride`, `MountainBikeRide`, `GravelRide`, `VirtualRide`, `EBikeRide`; `BSportType.RUN` matches `Run`, `TrailRun`, `VirtualRun`), or if local sport type is unknown.
   - *Duration Guard*: A candidate SHALL NOT match if the workout has duration `TIME_TOTAL > 0` and the candidate's `elapsed_time` differs by more than $\pm 25\%$ (or $\pm 900\text{ s}$).
4. **Safe Response Body Reads**:
   - All `response.body!!.string()` calls SHALL be replaced with safe calls (`response.body?.string()?.takeIf { it.isNotBlank() }`).
5. **SQL Security & Upsert Concurrency**:
   - `StravaUploadDbHelper.getStravaActivityDataForWorkouts` SHALL use parameterized `?` placeholders.
   - `updateOrInsert` SHALL use `insertWithOnConflict` with `CONFLICT_REPLACE`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Rate Limit Retry)**:
  * *Given* Strava responds with HTTP 429 Too Many Requests,
  * *When* `uploadToStrava` executes,
  * *Then* `exportResult.retry()` SHALL be `true` and rate limit headers SHALL be logged.
* **Criterion 2 (Server Error Retry)**:
  * *Given* Strava responds with HTTP 500/502/503/504,
  * *When* `uploadToStrava` executes,
  * *Then* `exportResult.retry()` SHALL be `true`.
* **Criterion 3 (Client Error Fatal)**:
  * *Given* Strava responds with HTTP 400 Bad Request,
  * *When* `uploadToStrava` executes,
  * *Then* `exportResult.retry()` SHALL be `false`.
* **Criterion 4 (Polling Glitch Tolerance)**:
  * *Given* poll attempt 1 returns null and attempt 2 returns `status = ready`,
  * *When* polling executes,
  * *Then* the uploader SHALL NOT abort on attempt 1 and SHALL succeed on attempt 2.
* **Criterion 5 (False-Start Duplicate Rejection)**:
  * *Given* a 2-hour workout and a 2-minute activity 3 minutes prior on Strava,
  * *When* `findExistingStravaActivityForWorkout` runs,
  * *Then* the 2-minute activity SHALL NOT match and the workout SHALL proceed to TCX upload.
* **Criterion 6 (Cross-Sport Rejection)**:
  * *Given* a 1-hour bike ride and a 1-hour run at the same start time on Strava,
  * *When* `findExistingStravaActivityForWorkout` runs for the bike ride,
  * *Then* the run SHALL NOT match.
* **Criterion 7 (Unparseable Date Skipping)**:
  * *Given* an activity with an unparseable `start_date`,
  * *When* evaluated by `findExistingStravaActivity`,
  * *Then* it SHALL be skipped and NOT match with `diff = 0`.
* **Criterion 8 (SQL Parameterization)**:
  * *Given* `StravaUploadDbHelper.getStravaActivityDataForWorkouts`,
  * *When* invoked with a collection of workout filenames,
  * *Then* it SHALL execute via parameterized `?` bind arguments.

### 1.4 System Invariants
- Preserves existing asymmetric naming rules (`REQ-EXP-008`) and duplicate metadata reconciliation (`REQ-EXP-009`).
- Zero UI regressions; offline caching and celebration banners remain intact.
- 100% clean-room test suite pass rate across all 3,361+ unit tests.

---

## 2. Test Specification (TST-EXP-018)

### Test Case 1: HTTP Error Classification & Rate Limit Headers (`TST-EXP-018.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploaderTest.kt`
* **Preconditions**: Mocked OkHttpClient responding with HTTP 429 (with `X-RateLimit-*` headers) and HTTP 503.
* **Action**: Invoke `uploadToStrava(mockExportInfo, testFile, "token")`.
* **Expected Result**: Returns `ExportResult(false, true, ...)` indicating retryable failure.

### Test Case 2: Polling Resilience & Timeout Handling (`TST-EXP-018.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploaderTest.kt`
* **Preconditions**: Polling endpoint returns null on attempt 1 and `{"status": "ready", "activity_id": 999}` on attempt 2.
* **Action**: Execute upload polling.
* **Expected Result**: Successfully recovers from transient null response and completes update.

### Test Case 3: Duplicate Discovery False-Start & Cross-Sport Guard (`TST-EXP-018.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploaderNamingTest.kt`
* **Preconditions**: Local workout duration = 7200 s (BIKE). Candidate 1: 120 s (BIKE). Candidate 2: 7200 s (RUN). Candidate 3: unparseable date. Candidate 4: 7100 s (BIKE).
* **Action**: Invoke `findExistingStravaActivityForWorkout(fileBaseName)`.
* **Expected Result**: Rejects Candidates 1, 2, and 3; correctly matches Candidate 4.

### Test Case 4: SQL Parameterization & Upsert (`TST-EXP-018.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/db/StravaUploadDbHelperTest.kt`
* **Preconditions**: Database populated with workout activity rows.
* **Action**: Query `getStravaActivityDataForWorkouts` with batch filenames and execute `updateOrInsert`.
* **Expected Result**: Correct map returned using parameterized queries; atomic replace semantics on insert conflict.

### Test Case 5: Clean-Room Regression Suite (`TST-EXP-018.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-EXP-018.1` | Unit | `StravaUploader.uploadToStrava` | `REQ-EXP-021.1` | Specified |
| `TST-EXP-018.2` | Unit | `StravaUploader.uploadToStrava` (polling loop) | `REQ-EXP-021.2` | Specified |
| `TST-EXP-018.3` | Unit | `StravaUploader.findExistingStravaActivityForWorkout` | `REQ-EXP-021.3` | Specified |
| `TST-EXP-018.4` | Unit | `StravaUploadDbHelper.getStravaActivityDataForWorkouts` | `REQ-EXP-021.5` | Specified |
| `TST-EXP-018.5` | Regression | Full unit test suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
