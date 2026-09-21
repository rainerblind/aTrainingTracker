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

## 2. Downstream Consumer Audit (Zero Dependencies on Discarded Fields)
A comprehensive grep audit was conducted across the entire codebase for all consumers of `StravaUploadDbHelper.getStravaActivityData` and `WorkoutData.stravaActivityData`:
1. `WorkoutRepository.kt` (lines 244, 526): loads `stravaActivityData` and attaches it to `WorkoutData`.
2. `PeriodsRepository.kt` (line 192): loads batch map `getStravaActivityDataForWorkouts` for chunk names.
3. `WorkoutClusterRepository.kt` (line 372): loads batch map for cluster member workouts.
4. `WorkoutDataMapper.kt` (lines 83, 220): maps database cursor to `WorkoutData.stravaActivityData`.
5. `WorkoutSummary.kt` (line 166): passes `workoutData.stravaActivityData` to `StravaActivitySection(rawActivityJson = ...)`.
6. `StravaActivitySection.kt` (line 62): passes `rawActivityJson` to `StravaActivityParser.parse(...)`.

**Audit Finding**: Zero modules, features, or latent code paths in the application access athlete profiles, kudos, comments, photos, gear descriptions, or external polylines from the persisted `StravaActivity` column. All UI rendering and offline features depend strictly on `id`, `segment_efforts`, and `best_efforts`.

---

## 3. Database Schema & Migration Strategy
* **Schema Integrity**: The SQLite column in `StravaUpload.db` (`StravaUploads` table) is:
  ```sql
  StravaActivity text
  ```
* **Column Format Transparency**: The column stores a JSON string. Because the column type is `TEXT`, shrinking the contents of this JSON string does NOT require an SQLite table recreation, column drop, or SQLite version bump (maintaining `DB_VERSION = 5`).
* **Existing Records Strategy (Transparent Dual-Format Tolerance & Optional Lazy Minimization)**:
  - *Read Path*: `StravaActivityParser.parse(jsonString)` is inherently tolerant of both legacy full-payload blobs and minimized payloads because `optJSONArray("segment_efforts")` and `optJSONArray("best_efforts")` function identically regardless of whether extraneous keys exist.
  - *Write / Migration Path*:
    - All *net-new uploads and duplicate reconciliations* immediately persist minimized JSON.
    - An optional one-time background / lazy minimization on read can prune legacy rows without database locking.
    - Disconnection / deauthorization via `StravaDataPurgeManager` executes `clearAllStravaData()` which wipes all records cleanly.
  - *Result*: Zero risk of `SQLException`, `ClassCastException`, or table schema incompatibility.

---

## 4. Parser Robustness & Exception Handling Contract
`StravaActivityParser` will be hardened with explicit validation contracts:
1. **Empty / Null Safety**: If `rawJson` is null, empty, or whitespace, `parse()` returns `null` safely without exception.
2. **Malformed JSON Handling**: Encapsulated in `try { ... } catch (e: Exception)` logging an informative warning without throwing runtime exceptions.
3. **Missing / Partial Arrays**:
   - Missing `segment_efforts` or `best_efforts` safely default to `emptyList()`.
   - Missing or non-numeric scalar values (`elapsed_time`, `pr_rank`, `kom_rank`, `distance`) safely default to null or safe zero values.
4. **Serialization Pipeline (`minimize`)**:
   ```kotlin
   fun minimize(rawActivityJson: String?): String?
   fun minimize(activityJson: JSONObject?): String?
   ```
   Parses raw JSON, converts to a domain `StravaActivity` instance, and serializes a clean, minimized `JSONObject` containing only:
   ```json
   {
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

---

## 5. Requirement Traceability & ASPICE Mapping
* **Primary Requirement**: `REQ-EXP-013` (Strava Activity Feedback Data Minimization to Athlete Achievements).
  - Categorized under Export & Cloud Synchronization in `docs/requirements.md`.
  - Defines mandatory minimization of persisted Strava activity payloads to athlete-specific achievements (`id`, `segment_efforts`, `best_efforts`), discarding transient social/profile fields.
* **Verification Specification**: `TST-EXP-010` (Minimized Strava Activity Feedback Persistence & Backward-Compatible Rendering Verification).
  - Specified in `docs/tests.md`.
  - Verifies:
    1. Unit tests for `StravaActivityParser.minimize(...)` confirming removal of kudos, athlete objects, and extraneous fields.
    2. Serialization parity ensuring `StravaActivitySection` renders celebration banners, best efforts, and matched segments identically from both legacy and minimized JSON.
    3. Parser robustness under malformed or empty payloads.
    4. Integration verification that `StravaUploader` persists minimized JSON.
    5. Disconnect compliance via `StravaDataPurgeManager`.

---

## 6. System Invariants & Preserved Behavior
1. **Offline Achievement Rendering**: Offline celebration banners (`SegmentPrCelebrationBanner`), best efforts, and segment lists render with 100% visual and functional parity.
2. **In-Memory Upload Workflows**: Immediate duplicate name evaluation and local segment PR updates in `StravaUploader.kt` continue to function without alteration during upload processing.
3. **100% Deauthorization Wipe**: `StravaDataPurgeManager.purgeAllStravaData(...)` continues to execute `clearAllStravaData()`, wiping all stored records on disconnect.
4. **Zero Production Binary Regressions**: Core GPS recording, TCX export, and sensor pipelines remain completely untouched.
