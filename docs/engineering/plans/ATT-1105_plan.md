# Implementation Plan: Prevent Overwriting Strava Activity Name on TCX Import Duplicate Sync (ATT-1105)

## 1. Overview & Architectural Motivation
Under **ATT-1105** and **REQ-EXP-008**, the application ensures that importing a TCX file corresponding to an activity already present on Strava never overwrites the user's custom activity title on Strava, and instead adopts the existing Strava title into the app's local database.

### Root Cause Analysis
1. **Local TCX Header Parsing Collision**:
   - In `LegacyImportEngine.kt`, TCX parsing extracts `<Name>` or `<Notes>` tags (e.g., `<Name>Cycling</Name>`) and initializes `WorkoutSummaries.WORKOUT_NAME` with that tag rather than `fileBaseName`.
   - In `StravaUploader.doUpdate()`, local enrichment was guarded by:
     ```kotlin
     if (!stravaName.isNullOrBlank() && (name.isNullOrBlank() || name == exportInfo.fileBaseName))
     ```
   - Because `name` was `"Cycling"` (neither blank nor `fileBaseName`), this condition evaluated to `false`. SQLite was never updated with the user's custom title from Strava.
2. **Duplicate Detection Fragility**:
   - In `StravaUploader.checkAndUpdateDuplicate()`, only `stravaJson.has(ERROR)` was checked, using regex `"duplicate of.*?(?:activity|activities)\\D+(\\d+)"`.
   - When Strava returns duplicate details under the `status` field (e.g. `{"status": "activity.tcx duplicate of activity 119487747"}`) or without the exact words `"activity|activities"` (e.g. `"duplicate of 119487747"`), duplicate detection returned `null`.
   - In `uploadWorkout()`, if the initial upload response had an error/duplicate or if `status` was neither standard enum constant, duplicate handling was bypassed.
3. **Strava Title Overwrite Risk in `doUpdate()`**:
   - If duplicate detection failed and `isDuplicate` remained `false`, `doUpdate()` evaluated:
     ```kotlin
     if (!isDuplicate && !name.isNullOrBlank() && name != exportInfo.fileBaseName) {
         formBuilder.add(NAME, name)
     }
     ```
   - Because `name` was `"Cycling"`, the app sent `NAME: "Cycling"` to Strava, overwriting the user's custom Strava title.

### Proposed Architecture & Logic Fix
1. **Unconditional Duplicate Enrichment in `doUpdate()`**:
   - When `isDuplicate == true`, `stravaName = activityJSON.optString(NAME)`. If `!stravaName.isNullOrBlank()`, `WorkoutSummaries.WORKOUT_NAME` in SQLite is **unconditionally** enriched with `stravaName`, overriding any generic TCX tag (e.g. `"Cycling"`) or fallback timestamp.
   - When `isDuplicate == true`, `NAME` is **strictly omitted** from the metadata update form body sent to Strava, guaranteeing Strava's title is never modified.
2. **Resilient Duplicate Detection in `checkAndUpdateDuplicate()`**:
   - Inspect both `error` and `status` JSON properties:
     ```kotlin
     val errorMsg = if (stravaJson.has(ERROR) && !stravaJson.isNull(ERROR)) stravaJson.optString(ERROR) else ""
     val statusMsg = if (stravaJson.has(STATUS) && !stravaJson.isNull(STATUS)) stravaJson.optString(STATUS) else ""
     val combinedText = "$errorMsg $statusMsg"
     val regex = "duplicate of.*?(\\d+)".toRegex(RegexOption.IGNORE_CASE)
     ```
   - Extract `activityId` and save to `StravaUploadDbHelper`, then invoke `doUpdate(exportInfo, isDuplicate = true)`.
3. **Comprehensive Catch Points in `uploadWorkout()`**:
   - Check `checkAndUpdateDuplicate(exportInfo, uploadResponseJson)` on the initial upload response in case Strava returns duplicate immediately.
   - Check `checkAndUpdateDuplicate(exportInfo, uploadStatusJsonAnswer)` in the polling loop if `error` OR `status` contains duplicate information.
4. **Reactive UI State Synchronization**:
   - Preserved from ATT-902: when `ExportStatusChangedBroadcaster` fires, `WorkoutRepository.reloadExportStatusesFor(fileName)` fetches the updated `WORKOUT_NAME` from SQLite and refreshes the in-memory cache and UI in real time.

---

## 2. Impact Analysis (ASPICE SWE.1.BP.5)

### Slated Target Files & Methods
- `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`:
  - `checkAndUpdateDuplicate(exportInfo: ExportInfo, stravaJson: JSONObject): ExportResult?`
  - `uploadWorkout(exportInfo: ExportInfo): ExportResult`
  - `doUpdate(exportInfo: ExportInfo, isDuplicate: Boolean = false): ExportResult`
- `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploaderNamingTest.kt`:
  - Update and expand test suite covering all 7 procedures of `TST-EXP-005`.

### Mapped Requirements Cross-Check
| Requirement ID | Description | Impact & Preservation Assessment |
| :--- | :--- | :--- |
| **REQ-EXP-008** | Smart Asymmetric Strava Naming Strategy | Primary target; ensures Strava title preservation and unconditional local DB enrichment on duplicates. |
| **REQ-EXP-002** | Cloud Synchronization | Preserved; OAuth, upload pipeline, and DB status helpers untouched. |
| **REQ-EXP-003** | Selective Upload | Preserved; sport type filtering and export info mappings untouched. |
| **REQ-EXP-006** | Explicit "No upload" option | Preserved; unmapped sports exit early without hitting upload pipeline. |
| **REQ-EXP-007** | Modern Strava sport types | Preserved; `TYPE` and `SPORT_TYPE` form builder updates continue to sync. |
| **REQ-DAT-013** | TCX workout name & description export/import | Preserved; TCX file generation and bracket notation import untouched. |
| **REQ-ROU-001** | Route clustering & naming | Preserved; cluster engine assignment untouched. |

### System Risk & Side Effect Evaluation
- **Android Worker / Background Safety**: No changes to thread dispatching or background execution; executes within existing background workers.
- **SQLite Concurrency**: Local update uses `WorkoutSummariesDatabaseManager.database.update()` matching existing write patterns.
- **Strava API Rate Limits**: No additional network calls introduced. Uses existing `getStravaActivity(activityId)` call.

---

## 3. Detailed Implementation Steps

### Step 1: Broaden Duplicate Detection in `StravaUploader.kt`
- In `checkAndUpdateDuplicate(exportInfo: ExportInfo, stravaJson: JSONObject)`:
  - Check both `ERROR` and `STATUS` fields for non-empty text.
  - Apply broad case-insensitive regex `"duplicate of.*?(\\d+)"`.
  - Capture `activityId = matchResult.groupValues[1]`.
  - Update `StravaUploadDbHelper` with `activityId`.
  - Return `doUpdate(exportInfo, isDuplicate = true)`.
- In `uploadWorkout()`:
  - Inspect `uploadResponseJson` immediately after upload POST using `checkAndUpdateDuplicate`.
  - In polling loop, check `checkAndUpdateDuplicate` before evaluating standard status enums.

### Step 2: Unconditional Local DB Enrichment & Name Protection in `doUpdate()`
- In `doUpdate()`:
  ```kotlin
  if (isDuplicate && activityJSON != null) {
      val stravaName = activityJSON.optString(NAME)
      if (!stravaName.isNullOrBlank()) {
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
- In form body creation:
  ```kotlin
  if (!isDuplicate && !name.isNullOrBlank() && name != exportInfo.fileBaseName) {
      formBuilder.add(NAME, name)
  }
  ```
  Ensure `name` is never added when `isDuplicate == true`.

### Step 3: Expand Unit Test Suite `StravaUploaderNamingTest.kt` (TST-EXP-005)
Implement unit tests covering:
1. `testDuplicateWithFallbackNameEnrichesLocalAndProtectsStrava`
2. `testDuplicateWithTcxImportedNameEnrichesLocalAndProtectsStrava`
3. `testDuplicateWithCustomNameProtectsStravaNameAndEnrichesLocal`
4. `testDuplicateDetectionInErrorField`
5. `testDuplicateDetectionInStatusField`
6. `testDuplicateDetectionWithDirectIdPhrasing`
7. `testFreshUploadWithFallbackNameOmitsName`
8. `testFreshUploadWithCustomNameSendsName`
9. `testMetadataPreservedAcrossBothPaths`

---

## 4. Verification Plan

### Automated Unit Tests
- Execute targeted unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.exporter.uploader.StravaUploaderNamingTest
  ```
- Execute full test suite regression:
  ```bash
  ./gradlew testDebugUnitTest
  ```
