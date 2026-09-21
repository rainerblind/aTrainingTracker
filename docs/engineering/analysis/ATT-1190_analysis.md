# Analysis - ATT-1190: Minimize Strava Activity Feedback Storage to Athlete Achievements

## 1. Problem Description & Background
When a workout is synchronized with Strava via `StravaUploader.kt` (either upon initial upload completion or during pre-upload duplicate discovery), the application queries Strava's `GET /api/v3/activities/{id}` endpoint to retrieve activity details. Currently, line 461 of `StravaUploader.kt` serializes the *entire raw JSON response* directly into SQLite via `StravaUploadDbHelper.updateStravaActivityData(fileBaseName, activityJSON.toString())`.

### Problems with Storing Raw Third-Party JSON:
1. **API Compliance & Data Minimization**:
   - The raw Strava activity JSON contains extensive transient, third-party, and social data fields:
     - Athlete profile details (`athlete`: ID, resource state, username, profile pictures)
     - Social engagement (`kudos_count`, `comment_count`, `athlete_count`)
     - External map tiles and vector polyline URLs
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
       - `processSegmentEffortsForPrs` inspects `segment_efforts` to update local starred segment PR times.
       - Duplicate reconciliation inspects `name` and `gear_id` / `gear.id`.
       - Both operations happen *immediately in memory* using `activityJSON` during `doUpdate()`. Only the persisted string in `StravaUploadDbHelper` remains for offline UI display.

## 2. Technical Scope & Architecture

### Data Minimization Pipeline:
Instead of storing `activityJSON.toString()`, we extract only the essential athlete-centric achievement data and serialize a minimized JSON structure before persisting to `StravaUploadDbHelper`:
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

### Component Interoperability & Backward Compatibility:
1. **`StravaActivityData.kt` (`StravaActivity` & `StravaActivityParser`)**:
   - `StravaActivityParser.parse(jsonString)` is *already* built to parse exactly these fields (`id`, `segment_efforts`, `best_efforts`)!
   - We will add a serializer method to `StravaActivityData.kt` or `StravaActivityParser`:
     ```kotlin
     fun minimize(rawActivityJson: String?): String?
     fun minimize(activityJson: JSONObject?): String?
     fun toJson(activity: StravaActivity): String
     ```
   - If `minimize(...)` is called on a raw Strava JSON, it parses the `StravaActivity` and serializes it to a compact, standardized JSON string containing *only* `id`, `segment_efforts`, and `best_efforts`.
   - Backward compatibility: If an old raw JSON already exists in SQLite from an earlier version, `StravaActivityParser.parse(...)` handles it transparently because `optJSONArray("segment_efforts")` and `optJSONArray("best_efforts")` work identically on both legacy full payloads and newly minimized payloads.
2. **`StravaUploader.kt`**:
   - In `doExport` (pre-existing duplicate discovery):
     - When storing pre-existing activity: minimize before calling `updateAll(...)`.
   - In `doUpdate`:
     - Keep `activityJSON: JSONObject` in memory for `processSegmentEffortsForPrs` and duplicate name/gear reconciliation.
     - When persisting to database:
       ```kotlin
       val minimizedJson = StravaActivityParser.minimize(activityJSON)
       StravaUploadDbHelper(mContext).updateStravaActivityData(exportInfo.fileBaseName, minimizedJson)
       ```
3. **`StravaDataPurgeManager.kt`**:
   - `StravaDataPurgeManager.executeLocalDataPurge(context)` invokes `StravaUploadDbHelper(context).clearAllStravaData()`, which wipes `TABLE` completely (`DELETE FROM StravaUpload`).
   - This remains 100% compliant with Strava API Agreement Section 7.4 (revocation/disconnect wipe).

## 3. Preserved Invariants & Boundary Protection
1. **Offline UI Presentation**: `StravaActivitySection.kt` renders the celebration banner (PR #1 / KOM), best efforts list, starred segments, and accordion expand/collapse without any visual difference or performance penalty.
2. **Zero Loss of Local PR Updates**: `processSegmentEffortsForPrs` in `StravaUploader.kt` runs directly on the live response before persistence, so segment PR times in `Segments.db` remain 100% accurate.
3. **Zero Loss of Gear or Title Reconciliation**: Equipment matching and title evaluation in `doUpdate()` continue to execute on the in-memory response prior to storage.
4. **Purge Parity**: `clearAllStravaData()` continues to clear all persisted records on disconnect/deauthorization.

## 4. Requirement & Verification Roadmap
* **Requirement**: `REQ-EXP-013` (Strava Activity Feedback Data Minimization to Athlete Achievements).
* **Test Specification**: `TST-EXP-010` (Minimized Strava Activity Feedback Persistence & Backward-Compatible Rendering Verification).
