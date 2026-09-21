# Analysis - ATT-1190: Minimize Strava Activity Feedback Storage to Athlete Achievements

## 1. Problem Description & Background
When a workout is synchronized with Strava via `StravaUploader.kt` (either upon initial upload completion or during pre-upload duplicate discovery), the application queries Strava's `GET /api/v3/activities/{id}` endpoint to retrieve activity details. Currently, line 461 of `StravaUploader.kt` serializes the *entire raw JSON response* directly into SQLite via `StravaUploadDbHelper.updateStravaActivityData(fileBaseName, activityJSON.toString())`.

### Problems with Storing Raw Third-Party JSON:
1. **API Compliance & Data Minimization**:
   - The raw Strava activity JSON contains extensive transient, third-party, and social data fields:
     - Athlete profile details (`athlete`: ID, resource state, username, profile pictures)
     - Social engagement (`kudos_count`, `comment_count`, `athlete_count`)
     - External map tiles, stream URLs, and vector polyline data
     - Photos, gear descriptions, device metadata, external partner integrations
   - Under the Strava API Agreement Section 6.2 ("Cache and Retention") and general data protection principles (GDPR data minimization), caching third-party or social details indefinitely on the client is strongly discouraged when only specific athlete performance metrics are required.
2. **Database Bloat & Storage Overhead**:
   - Raw activity JSON responses from Strava frequently range from 15 KB to 80 KB+ per activity depending on the number of GPS segment efforts, splits, and laps.
   - For an athlete with hundreds of activities, `StravaUpload.db` accumulates megabytes of dead JSON data that is never rendered or used by the app.
3. **App Requirements**:
   - What the application *actually* uses from this stored data:
     - `StravaActivitySection.kt` and `WorkoutSummary.kt` only parse:
       - Activity `id` (to open the activity in the Strava app/web).
       - `segment_efforts`: segment `name`, `elapsed_time`, `pr_rank`, `kom_rank`, `is_starred`, `segment_id`.
       - `best_efforts`: effort `name`, `elapsed_time`, `pr_rank`, `distance`.
     - `StravaUploader.kt`:
       - `processSegmentEffortsForPrs` inspects `segment_efforts` to update local starred segment PR times in `Segments.db`.
       - Duplicate reconciliation inspects `name` and `gear_id` / `gear.id`.
       - Both operations happen *immediately in memory* using `activityJSON` during `doUpdate()`. Only the persisted string in `StravaUploadDbHelper` remains for offline UI display.

---

## 2. Exhaustive Downstream Consumer & Call Site Audit
A comprehensive audit across all code modules, database queries, and background tasks for all references to `StravaUploadDbHelper`, `STRAVA_ACTIVITY_DATA`, and `WorkoutData.stravaActivityData` reveals:
1. `WorkoutRepository.kt` (lines 244, 526): loads `stravaActivityData` from helper and assigns to `WorkoutData`.
2. `PeriodsRepository.kt` (line 192): queries batch map `getStravaActivityDataForWorkouts` for chunk names.
3. `WorkoutClusterRepository.kt` (line 372): queries batch map for cluster member workouts.
4. `WorkoutDataMapper.kt` (lines 83, 220): maps database cursor to `WorkoutData.stravaActivityData`.
5. `WorkoutSummary.kt` (line 166): passes `workoutData.stravaActivityData` to `StravaActivitySection(rawActivityJson = ...)`.
6. `StravaActivitySection.kt` (line 62): passes `rawActivityJson` to `StravaActivityParser.parse(...)`.
7. Background Workers & Services: Zero background services (`TrackerService`, `StravaSegmentsSyncWorker`, `StravaRoutesSyncWorker`, `BackupWorker`) access or parse `StravaActivity` raw data.
8. Direct SQL / ContentProviders: There are NO ContentProviders or raw SQL queries exposing `StravaUpload.db` to external apps or diagnostics. All interactions are strictly encapsulated within `StravaUploadDbHelper.java`.

**Audit Finding**: Zero features or background tasks depend on the discarded fields (athlete profile, kudos, comments, photos, gear descriptions, polyline vectors).

---

## 3. Database Schema & Migration Strategy
* **Schema Integrity**: The SQLite column in `StravaUpload.db` (`StravaUploads` table) is:
  ```sql
  StravaActivity text
  ```
* **Column Format Transparency**: The column stores a JSON string. Because the column type is `TEXT`, shrinking the contents of this JSON string does NOT require an SQLite table recreation, column drop, or SQLite version bump (maintaining `DB_VERSION = 5`).
* **Existing Records Strategy & Lazy Compaction**:
  - *Read Path Tolerance*: `StravaActivityParser.parse(jsonString)` is inherently tolerant of both legacy full-payload blobs and minimized payloads because `optJSONArray("segment_efforts")` and `optJSONArray("best_efforts")` function identically regardless of whether extraneous keys exist.
  - *Write & Lazy Compaction Path*:
    1. All *net-new uploads and duplicate reconciliations* immediately persist minimized JSON.
    2. *Opportunistic / Lazy Compaction on Read*: In `StravaUploadDbHelper.getStravaActivityData(fileBaseName)`, if the retrieved JSON contains legacy raw keys (e.g. `"athlete"`, `"kudos_count"`, or payload length > 4096 bytes), the helper lazily calls `minimize(rawJson)` and asynchronously or directly writes the compacted JSON back to the row. This progressively eliminates database bloat and legacy payloads during natural app browsing without requiring a massive, blocking database migration transaction.
  - *Disconnect Wipe*: Disconnection / deauthorization via `StravaDataPurgeManager` executes `clearAllStravaData()` which wipes all records cleanly (`DELETE FROM StravaUploads`), ensuring 100% compliance with Section 7.4.

---

## 4. Parser Robustness, Null-Safety & Serialization Specification
`StravaActivityParser` will be hardened with explicit validation contracts:

### Exact Minimized JSON Schema & Null-Safety Rules:
To prevent fragmentation and ambiguous states, optional/nullable fields adhere to explicit serialization rules:
* `id`: `Long` (required or omitted if absent).
* `segment_efforts`: `JSONArray` of objects:
  - `name`: `String` (required, defaults to empty string if missing).
  - `elapsed_time`: `Int` (required, defaults to 0).
  - `pr_rank`: `Int` (omitted if null, never serialized as `"pr_rank": null`).
  - `kom_rank`: `Int` (omitted if null, never serialized as `"kom_rank": null`).
  - `starred`: `Boolean` (omitted if false, serialized as `true` only when starred).
  - `segment_id`: `Long` (omitted if null or <= 0).
* `best_efforts`: `JSONArray` of objects:
  - `name`: `String` (required).
  - `elapsed_time`: `Int` (required).
  - `pr_rank`: `Int` (omitted if null).
  - `distance`: `Double` (omitted if <= 0.0).

*Key Design Choice*: Omitting null/false fields achieves maximum JSON compression (typically < 500 bytes per activity) and eliminates `JSONObject.NULL` ambiguity.

### Defensive Exception Handling & Truncation Resilience:
1. **Empty / Null Input**: Returns `null` immediately.
2. **Truncated / Corrupted JSON**:
   - If an interrupted write or disk full condition yields a truncated JSON string (e.g. `{"id": 1234, "segment_efforts": [`), `JSONObject(jsonString)` throws `JSONException`.
   - The parser encapsulates this in a `try ... catch (e: Exception)` block, logs a descriptive warning (`Log.w(TAG, "Failed to parse Strava activity JSON, treating as null", e)`), and returns `null`.
   - The UI gracefully falls back to displaying `@string/strava_uploaded_no_records` rather than crashing.
3. **Serialization Pipeline (`minimize`)**:
   ```kotlin
   fun minimize(rawActivityJson: String?): String?
   fun minimize(activityJson: JSONObject?): String?
   ```
   Parses raw JSON, converts to domain `StravaActivity`, and serializes a clean, minimized `JSONObject` according to the exact schema rules above.

---

## 5. Requirement Traceability & ASPICE Mapping
* **Primary Requirement**: `REQ-EXP-013` (Strava Activity Feedback Data Minimization to Athlete Achievements).
  - Categorized under Export & Cloud Synchronization in `docs/requirements.md`.
  - Defines mandatory minimization of persisted Strava activity payloads to athlete-specific achievements (`id`, `segment_efforts`, `best_efforts`), omitting transient social/profile fields and optional null values.
* **Verification Specification**: `TST-EXP-010` (Minimized Strava Activity Feedback Persistence & Backward-Compatible Rendering Verification).
  - Specified in `docs/tests.md`.
  - Verifies:
    1. Unit tests for `StravaActivityParser.minimize(...)` confirming removal of kudos, athlete objects, and extraneous fields, with correct omission of null ranks.
    2. Resilience tests for malformed and truncated JSON payloads.
    3. Serialization parity ensuring `StravaActivitySection` renders celebration banners, best efforts, and matched segments identically from both legacy and minimized JSON.
    4. Integration verification that `StravaUploader` persists minimized JSON.
    5. Disconnect compliance via `StravaDataPurgeManager`.

---

## 6. System Invariants & Preserved Behavior
1. **Offline Achievement Rendering**: Offline celebration banners (`SegmentPrCelebrationBanner`), best efforts, and segment lists render with 100% visual and functional parity.
2. **In-Memory Upload Workflows**: Immediate duplicate name evaluation and local segment PR updates in `StravaUploader.kt` continue to function without alteration during upload processing.
3. **100% Deauthorization Wipe**: `StravaDataPurgeManager.purgeAllStravaData(...)` continues to execute `clearAllStravaData()`, wiping all stored records on disconnect.
4. **Zero Production Binary Regressions**: Core GPS recording, TCX export, and sensor pipelines remain completely untouched.
