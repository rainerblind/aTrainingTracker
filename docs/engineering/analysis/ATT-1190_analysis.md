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
### Chesterton's Fence Archaeology:
1. **Original Requirement ID & Target**:
   - `REQ-EXP-001` / `REQ-EXP-007` (Initial Strava integration & activity feedback storage). Target was simply to cache the Strava activity response payload locally so that subsequent offline views could display activity feedback without repeating network calls.
2. **Historical Origin & Commit Trace**:
   - Commit history in `StravaUploader.java` / `StravaUploader.kt` (`StravaUploadDbHelper.java` added with column `Feedback`, subsequently renamed to `StravaActivity` in `DB_VERSION = 5`).
   - Line 461: `StravaUploadDbHelper(mContext).updateStravaActivityData(exportInfo.fileBaseName, activityJSON.toString())`.
3. **Root Reason for Existing Formulation ("Why was this fence built?")**:
   - Storing `activityJSON.toString()` was an expedient, zero-parsing shortcut implemented when the Strava API feedback was first introduced. Dumping the raw JSON string avoided writing a dedicated serialization parser or schema transformer at the upload boundary.
4. **Preservation of Core Invariants ("Why is it safe to modify now?")**:
   - Comprehensive downstream caller audit reveals that the application strictly consumes `id`, `segment_efforts`, and `best_efforts`. None of the UI components or background workers read athlete profiles, kudos, comments, or external map polylines.
   - Replacing the raw JSON blob with a minimized achievement schema (`"v": 2`) preserves 100% of the UI rendering state, offline celebration banner functionality, and duplicate handling while drastically reducing storage size and complying with the Strava API Agreement Section 6.2 ("Cache and Retention").

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

## 3. Database Schema, Command-Query Separation & Migration Strategy
* **Schema Integrity & Database Versioning (DB_VERSION = 6)**:
  - To establish formal schema migration boundaries, prevent concurrency race conditions from background jobs, and provide clean upgrade/downgrade semantics, `StravaUploadDbHelper` increments `DB_VERSION` from `5` to `6`.
  - In `SQLiteOpenHelper.onUpgrade(db, oldVersion, newVersion)`:
    - When upgrading from `oldVersion < 6`:
      - All existing rows in `StravaUploads` where `StravaActivity IS NOT NULL` are migrated deterministically in-place within the upgrade transaction:
        ```java
        if (oldVersion < 6) {
            // Deterministic row migration from legacy raw JSON to minimized v:2 JSON
            migrateToMinimizedStravaActivity(db);
        }
        ```
      - Deterministic Migration Pipeline (`migrateToMinimizedStravaActivity`):
        - Iterates over legacy rows in small, memory-efficient batches via raw SQLite cursor (without triggering UI models).
        - Parses each legacy JSON blob with `StravaActivityParser.minimize()`.
        - If parsing succeeds, updates `StravaActivity` with the compact `"v": 2` JSON payload.
        - If parsing fails due to truncated/corrupt legacy JSON, it safely writes `null` (or `{ "v": 2, "corrupted": true }`), ensuring unparseable data does not halt the migration.
        - The entire `onUpgrade` step runs inside SQLite's built-in transaction. If an unrecoverable SQLite disk error occurs, the upgrade aborts cleanly and rolls back.
  - Downgrade & Rollback Safety:
    - `StravaActivityParser.parse()` maintains dual-format read tolerance. If an APK downgrade occurs to an older version that expects raw JSON, the parser's defensive try/catch gracefully treats unexpected schema structures as null/empty records without crashing. In addition, `onDowngrade()` in `StravaUploadDbHelper` implements non-destructive rollback handling.
* **Strict Command-Query Separation (CQS) — Zero Write-on-Read**:
  - All read operations (`getStravaActivityData`, `getStravaActivityDataForWorkouts`) remain strictly read-only queries. Zero implicit writes or compactions occur during reads.
* **Deterministic Schema Versioning**:
  - The minimized JSON schema includes a root-level version marker:
    ```json
    "v": 2
    ```
  - Legacy payloads (Version 1) lack `"v": 2`. Minimized payloads explicitly declare `"v": 2`.
  - This eliminates heuristic string matching (such as searching for `"athlete"` or checking string length) and guarantees deterministic detection.
* **Data Disconnect Compliance & Forensic Cleanliness**:
  - `StravaDataPurgeManager.purgeAllStravaData(...)` executes `clearAllStravaData()` (`DELETE FROM StravaUploads`), followed by an explicit `VACUUM` / WAL checkpoint (`PRAGMA wal_checkpoint(FULL)`) to ensure all deleted personal and social data is immediately unlinked and removed from database storage pages.
* **Immediate Ingestion Minimization**:
  - All newly uploaded workouts and newly reconciled duplicate activities in `StravaUploader.kt` are minimized *before* writing to `StravaUploadDbHelper`, immediately stemming any new storage bloat or privacy leaks.

---

## 4. Parser Robustness, Null-Safety & Serialization Specification
`StravaActivityParser` will be hardened with explicit validation contracts:

### Exact Minimized JSON Schema (Version 2) & Null-Safety Rules:
To eliminate ambiguity, memory bloat, and `JSONObject.NULL` issues, optional/nullable fields adhere to strict omission rules:
* `v`: `Int` = 2 (Root schema version marker).
* `id`: `Long` (Strava activity ID; omitted if null).
* `segment_efforts`: `JSONArray` of objects:
  - `name`: `String` (required, non-null).
  - `elapsed_time`: `Int` (required, seconds > 0).
  - `pr_rank`: `Int` (omitted if null, never serialized as `"pr_rank": null`).
  - `kom_rank`: `Int` (omitted if null, never serialized as `"kom_rank": null`).
  - `starred`: `Boolean` (omitted if false, serialized as `true` only when starred).
  - `segment_id`: `Long` (omitted if null or <= 0).
* `best_efforts`: `JSONArray` of objects:
  - `name`: `String` (required).
  - `elapsed_time`: `Int` (required).
  - `pr_rank`: `Int` (omitted if null).
  - `distance`: `Double` (omitted if <= 0.0).

### Sample Minimized JSON (v: 2):
```json
{
  "v": 2,
  "id": 1234567890,
  "segment_efforts": [
    {
      "name": "Alpe d'Huez",
      "elapsed_time": 3600,
      "pr_rank": 1,
      "kom_rank": 1,
      "starred": true,
      "segment_id": 998877
    }
  ],
  "best_efforts": [
    {
      "name": "5k",
      "elapsed_time": 1200,
      "pr_rank": 1,
      "distance": 5000.0
    }
  ]
}
```

### Defensive Exception Handling & Truncation Resilience:
1. **Empty / Null Input**: Returns `null` immediately.
2. **Truncated / Corrupted JSON**:
   - If an interrupted write or disk full condition yields a truncated JSON string (e.g. `{"v": 2, "segment_efforts": [`), `JSONObject(jsonString)` throws `JSONException`.
   - The parser encapsulates this in a `try ... catch (e: Exception)` block, logs an informative warning (`Log.w(TAG, "Failed to parse Strava activity JSON, treating as null", e)`), and returns `null`.
   - Downstream UI (`StravaActivitySection`) gracefully falls back to displaying `@string/strava_uploaded_no_records` rather than crashing.
3. **Serialization Pipeline (`minimize`)**:
   ```kotlin
   fun minimize(rawActivityJson: String?): String?
   fun minimize(activityJson: JSONObject?): String?
   ```
   Parses the input, converts to domain `StravaActivity`, and serializes a clean, minimized `JSONObject` according to the Version 2 schema specification.

---

## 5. Requirement Traceability & ASPICE Mapping
* **Primary Requirement**: `REQ-EXP-013` (Strava Activity Feedback Data Minimization to Athlete Achievements).
  - Categorized under Export & Cloud Synchronization in `docs/requirements.md`.
  - Defines mandatory minimization of persisted Strava activity payloads to athlete-specific achievements (`id`, `segment_efforts`, `best_efforts`), omitting transient social/profile fields and optional null values.
  - Mandates strict Command-Query Separation with zero write-on-read.
* **Verification Specification**: `TST-EXP-010` (Minimized Strava Activity Feedback Persistence & Backward-Compatible Rendering Verification).
  - Specified in `docs/tests.md`.
  - Verifies:
    1. Unit tests for `StravaActivityParser.minimize(...)` confirming removal of kudos, athlete objects, and extraneous fields, with correct omission of null ranks and inclusion of `"v": 2`.
    2. Resilience tests for malformed and truncated JSON payloads.
    3. Serialization parity ensuring `StravaActivitySection` renders celebration banners, best efforts, and matched segments identically from both legacy (v1) and minimized (v2) JSON.
    4. Integration verification that `StravaUploader` persists minimized JSON.
    5. CQS verification: read methods execute zero SQLite updates or database locks.
    6. Disconnect compliance via `StravaDataPurgeManager`.

---

## 6. System Invariants & Preserved Behavior
1. **Offline Achievement Rendering**: Offline celebration banners (`SegmentPrCelebrationBanner`), best efforts, and segment lists render with 100% visual and functional parity.
2. **In-Memory Upload Workflows**: Immediate duplicate name evaluation and local segment PR updates in `StravaUploader.kt` continue to function without alteration during upload processing.
3. **Strict CQS Invariance**: Reading cached Strava activity data is 100% side-effect free; zero database updates or transaction locks occur during read calls.
4. **100% Deauthorization Wipe**: `StravaDataPurgeManager.purgeAllStravaData(...)` continues to execute `clearAllStravaData()`, wiping all stored records on disconnect.
5. **Zero Production Binary Regressions**: Core GPS recording, TCX export, and sensor pipelines remain completely untouched.
