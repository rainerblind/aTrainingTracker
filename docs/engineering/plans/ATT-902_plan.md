# Implementation Plan: Smart Asymmetric Workout Naming Strategy for Strava Uploads (ATT-902)

## 1. Overview & Architectural Motivation
Under **ATT-902** and **REQ-EXP-008**, the application implements a *Smart Asymmetric Naming Strategy* when synchronizing workouts to Strava (`StravaUploader.kt`).

Historically:
1. When Strava detected a duplicate (`"duplicate of activity <id>"`), `StravaUploader.doUpdate()` unconditionally posted the local `WorkoutSummaries.WORKOUT_NAME` to Strava. If the workout was imported without an embedded name, `WORKOUT_NAME` defaulted to `baseFileName` (e.g. `2024-05-12_10-15-30`), irreversibly clobbering the user's custom title on Strava.
2. For fresh uploads, if `name == baseFileName`, sending `NAME` forced Strava to display an ugly timestamp rather than Strava's smart, localized default ("Morning Ride").

The **Smart Asymmetric Strategy** resolves both issues:
- **Duplicate Flows (`isDuplicate = true`)**:
  - Strava title is preserved as authoritative; `name` parameter is **never** sent to Strava.
  - The local database is enriched from Strava: `activityJSON.optString("name")` is extracted. If valid, and local `WORKOUT_NAME` is empty, blank, or equals `fileBaseName`, the local record in `WorkoutSummaries` is updated with Strava's title.
- **Fresh Upload Flows (`isDuplicate = false`)**:
  - If `name` is custom (`!name.isNullOrBlank() && name != fileBaseName`), `name` is sent to Strava.
  - If `name` is missing or equals `fileBaseName`, `name` is omitted, allowing Strava's smart title generator to assign a natural title.

---

## 2. Impact Analysis (ASPICE SWE.1.BP.5)

### Slated Target Files & Methods
- `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`:
  - `checkAndUpdateDuplicate(exportInfo: ExportInfo, stravaJson: JSONObject): ExportResult?`
  - `doUpdate(exportInfo: ExportInfo, isDuplicate: Boolean = false): ExportResult`

### Mapped Requirements Cross-Check
| Requirement ID | Description | Impact & Preservation Assessment |
| :--- | :--- | :--- |
| **REQ-EXP-002** | Automated workout uploads to Strava | Preserved; upload triggering and polling logic unchanged. |
| **REQ-EXP-003** | Selective Upload | Preserved; export info and sport type mapping filtering unchanged. |
| **REQ-EXP-005** | Exponential backoff for Strava uploads | Preserved; retry and backoff loops untouched. |
| **REQ-EXP-006** | Explicit "No upload" option | Preserved; early exit for unmapped sports untouched. |
| **REQ-EXP-007** | Support latest Strava sport types | Preserved; `TYPE` and `SPORT_TYPE` form builder updates untouched. |
| **REQ-DAT-013** | TCX workout name & description export/import | Preserved; TCX file generation and bracket notation import untouched. |
| **REQ-ROU-001** | Route clustering & naming | Preserved; cluster engine assignment and hit counters untouched. |

### System Risk & Side Effect Evaluation
- **Android System & Worker Lifecycle**: `StravaUploader` executes asynchronously on background worker threads (`WorkManager` / `ExportManager`). Local SQLite database write is minimal and instantaneous.
- **Data Integrity**: Local database update targets strictly `WorkoutSummaries.WORKOUT_NAME` by `FILE_BASE_NAME` and only when `WORKOUT_NAME` is unassigned or equals `fileBaseName`. Existing user-customized names are never overwritten.
- **Network Resilience**: Strava network communication uses existing `getStravaActivity` and `updateStravaActivity` pipelines with safe null checks.

---

## 3. Detailed Implementation Steps

### Step 1: Update `doUpdate` Parameter & Duplicate Call Site in `StravaUploader.kt`
- In `StravaUploader.kt`, update `doUpdate`:
  ```kotlin
  protected fun doUpdate(exportInfo: ExportInfo, isDuplicate: Boolean = false): ExportResult
  ```
- In `checkAndUpdateDuplicate()` (line 233):
  ```kotlin
  return doUpdate(exportInfo, isDuplicate = true)
  ```
- In `handleUploadResponse()` (line 191):
  ```kotlin
  exportResult = doUpdate(exportInfo, isDuplicate = false)
  ```

### Step 2: Implement Smart Asymmetric Reconciler in `doUpdate()`
- After fetching `activityJSON = getStravaActivity(activityId)`:
  ```kotlin
  // If duplicate, enrich local database from Strava if local name is unassigned or raw timestamp
  if (isDuplicate && activityJSON != null) {
      val stravaName = activityJSON.optString(NAME)
      if (!stravaName.isNullOrBlank() && (name.isNullOrBlank() || name == exportInfo.fileBaseName)) {
          val updateValues = ContentValues().apply {
              put(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME, stravaName)
          }
          db.update(
              WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
              updateValues,
              "${WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME}=?",
              arrayOf(exportInfo.fileBaseName)
          )
          if (DEBUG) Log.i(TAG, "Enriched local workout name from Strava: '$stravaName'")
      }
  }
  ```
- When assembling `formBuilder`:
  Replace:
  ```kotlin
  if (!name.isNullOrEmpty()) {
      formBuilder.add(NAME, name)
  }
  ```
  With:
  ```kotlin
  if (!isDuplicate && !name.isNullOrBlank() && name != exportInfo.fileBaseName) {
      formBuilder.add(NAME, name)
  }
  ```

### Step 3: Implement Unit Test Suite `StravaUploaderNamingTest.kt` (TST-EXP-005)
Create `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploaderNamingTest.kt` verifying:
1. `testDuplicateWithFallbackNameEnrichesLocalAndProtectsStrava`: `isDuplicate = true`, local name = `fileBaseName`. Strava update request contains NO `name`. SQLite `WorkoutSummaries` receives Strava's `name`.
2. `testDuplicateWithCustomNameProtectsStrava`: `isDuplicate = true`, local name = `"My Custom Run"`. Strava update request contains NO `name`.
3. `testFreshUploadWithFallbackNameOmitsName`: `isDuplicate = false`, local name = `fileBaseName`. Strava update request contains NO `name`.
4. `testFreshUploadWithCustomNameSendsName`: `isDuplicate = false`, local name = `"Morning Tempo"`. Strava update request contains `name = "Morning Tempo"`.
5. `testMetadataPreservedAcrossBothPaths`: Verifies `gearId`, `description`, `trainer`, `commute`, and `sport_type` remain present in both cases.

---

## 4. Verification Plan

### Automated Verification
```bash
./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.exporter.uploader.StravaUploaderNamingTest
./gradlew testDebugUnitTest
```
Verify zero regressions across entire unit test suite.
