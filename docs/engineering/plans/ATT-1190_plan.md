# Implementation Plan - ATT-1190: Minimize Strava Activity Feedback Storage to Athlete Achievements

## 1. Problem Description & Background
When a workout is synchronized with Strava via `StravaUploader.kt`, the application queries Strava's `GET /api/v3/activities/{id}` endpoint to retrieve activity details. Currently, line 461 of `StravaUploader.kt` serializes the *entire raw JSON response body* directly into SQLite via `StravaUploadDbHelper.updateStravaActivityData(fileBaseName, activityJSON.toString())`.

This raw JSON payload (typically 15 KB to 80 KB+) contains extensive transient third-party and social data fields (athlete profile, photos, comments, kudos, map stream URLs, and gear metadata). Caching these fields indefinitely causes local SQLite database bloat and conflicts with the Strava API Agreement Section 6.2 ("Cache and Retention") and general data protection hygiene.

In practice, the application only requires:
1. Activity ID (`id`).
2. Segment efforts: `name`, `elapsed_time`, `pr_rank`, `kom_rank`, `starred`, `segment_id`.
3. Best efforts: `name`, `elapsed_time`, `pr_rank`, `distance`.

This ticket minimizes the persisted JSON payload to a compact athlete-centric achievement schema (`"v": 2`, < 500 bytes), implements deterministic SQLite migration in `onUpgrade` with `DB_VERSION = 6`, preserves strict Command-Query Separation (CQS) with zero write-on-read, and enforces a forensic data wipe on deauthorization.

---

## 2. Traceability & Requirements Mapping
* **Primary Requirement**: `REQ-EXP-013` (Strava Activity Feedback Data Minimization to Athlete Achievements) in `docs/requirements.md`.
  - Enforces minimization to achievements (`id`, `segment_efforts`, `best_efforts`).
  - Enforces omission of null/absent/non-positive fields in `"v": 2`.
  - Mandates strict Command-Query Separation (zero write-on-read).
  - Enforces robust truncation and malformed JSON resilience.
  - Preserves offline celebration banner, best efforts, and segment accordion rendering parity.
* **Test Specification**: `TST-EXP-010` in `docs/tests.md`.
  - Unit tests for `StravaActivityParser.minimize(...)` data stripping and null omission.
  - Unit tests for dual-format backward compatibility (`v1` and `v2`) and UI parity.
  - Defensive exception handling tests for malformed/truncated payloads.
  - Strict CQS verification in `StravaUploadDbHelper`.
  - SQLite migration verification from `DB_VERSION = 5` to `6` in `onUpgrade`.
  - Disconnect data purge and WAL checkpoint verification in `StravaDataPurgeManager`.
* **Chesterton's Fence Archaeology**: Documented in `docs/engineering/analysis/ATT-1190_analysis.md` and Gate 2 review deliverable.

---

## 3. System Invariants & Preserved Behavior
1. **Offline UI Parity**: `StravaActivitySection` renders celebration banners (`pr_rank == 1` or `kom_rank == 1`), segment lists, and best efforts with 100% visual and functional parity.
2. **In-Memory Uploader Operations**: In `StravaUploader.kt`, `processSegmentEffortsForPrs` and duplicate reconciliation continue to inspect the full in-memory Strava API response during upload prior to database persistence.
3. **Strict Command-Query Separation (CQS)**: `StravaUploadDbHelper.getStravaActivityData()` and `getStravaActivityDataForWorkouts()` remain strictly read-only; zero write transactions or row mutations occur during read queries.
4. **Deterministic SQLite Migration**: In `StravaUploadDbHelper`, `DB_VERSION` increments from 5 to 6. `onUpgrade` migrates existing legacy rows in-place inside an atomic SQLite transaction. Corrupted legacy rows transition to null or a tombstone (`{"v":2,"corrupted":true}`) without halting the upgrade.
5. **Downgrade & Rollback Safety**: `StravaActivityParser.parse()` tolerates both legacy and minimized schemas. If an APK downgrade occurs, older parsers fail safely or `onDowngrade()` handles the database without data corruption.
6. **Forensic Deauthorization Wipe**: `StravaDataPurgeManager.purgeAllStravaData()` clears `StravaUploads` and executes `PRAGMA wal_checkpoint(FULL)` to ensure zero lingering personal data in WAL pages.
7. **Zero Core Recording & Export Regressions**: Core GPS tracking, sensor sampling, and TCX export remain completely untouched.

---

## 4. Proposed Architectural Changes

### Component 1: Minimized Schema & Parser (`StravaActivityData.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/StravaActivityData.kt`
* **Schema Definition (`v: 2`)**:
  - `v`: 2
  - `id`: Long
  - `segment_efforts`: Array of objects:
    - `name`: String
    - `elapsed_time`: Int
    - `pr_rank`: Int (omitted if null)
    - `kom_rank`: Int (omitted if null)
    - `starred`: Boolean (omitted if false, serialized as true only when true)
    - `segment_id`: Long (omitted if null or <= 0)
  - `best_efforts`: Array of objects:
    - `name`: String
    - `elapsed_time`: Int
    - `pr_rank`: Int (omitted if null)
    - `distance`: Double (omitted if <= 0.0)
* **Methods to Add to `StravaActivityParser`**:
  ```kotlin
  @JvmStatic
  fun minimize(rawActivityJson: String?): String?

  @JvmStatic
  fun minimize(activityJson: JSONObject?): String?
  ```
* **Serialization Extension on `StravaActivity`**:
  ```kotlin
  fun toJson(version: Int = 2): String
  ```
* **Hardened `StravaActivityParser.parse(jsonString: String?)`**:
  - Parse version header `v = json.optInt("v", 1)`.
  - When `v == 2`, parse directly from the minimized structure (fast path).
  - When `v == 1` (legacy), parse existing structure (backward compatibility).
  - Wrap in `try ... catch (e: Exception)` with `Log.w(TAG, "Failed to parse Strava activity JSON, treating as null", e)` returning `null` safely on malformed or truncated input.

---

### Component 2: Uploader Integration (`StravaUploader.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`
* **Changes**:
  - In `doUpdate` (line 461):
    ```kotlin
    // Before:
    StravaUploadDbHelper(mContext).updateStravaActivityData(exportInfo.fileBaseName, activityJSON.toString())

    // After:
    val minimizedData = StravaActivityParser.minimize(activityJSON)
    StravaUploadDbHelper(mContext).updateStravaActivityData(exportInfo.fileBaseName, minimizedData)
    ```
  - Note: `processSegmentEffortsForPrs(activityJSON)` and duplicate reconciliation continue to execute on the in-memory `activityJSON` directly.

---

### Component 3: Database Migration & Schema (`StravaUploadDbHelper.java`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/db/StravaUploadDbHelper.java`
* **Changes**:
  - Increment `DB_VERSION` from `5` to `6`:
    ```java
    static final int DB_VERSION = 6;
    ```
  - In `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`:
    ```java
    if (oldVersion < 6) {
        migrateToMinimizedStravaActivity(db);
    }
    ```
  - Implement `migrateToMinimizedStravaActivity(SQLiteDatabase db)`:
    - Iterate over rows with `StravaActivity IS NOT NULL` where `StravaActivity NOT LIKE '%"v":2%'`.
    - Minimize each record using `StravaActivityParser.minimize()`.
    - If parsed successfully, update row with minimized JSON.
    - If legacy row is corrupt/malformed, set to `null` (or tombstone `{"v":2,"corrupted":true}`).
  - Add safe `onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion)` to prevent crash on APK rollback.
  - Implement WAL checkpoint helper if needed:
    ```java
    public void checkpointWal() {
        try {
            getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)", null).close();
        } catch (Exception e) {
            Log.w(TAG, "WAL checkpoint failed", e);
        }
    }
    ```

---

### Component 4: Disconnect Cleanliness (`StravaDataPurgeManager.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaDataPurgeManager.kt`
* **Changes**:
  - After `uploadDb.clearAllStravaData()`, call `uploadDb.checkpointWal()` to flush unlinked pages from WAL and satisfy forensic data hygiene.

---

## 5. Verification Plan

### Automated Unit Tests
1. **Unit Tests in `StravaActivitySectionTest.kt`**:
   - `testMinimizeStripsSocialAndProfileFields()`: verifies athlete, kudos, comments, photos, maps are completely absent, and `"v": 2` is present.
   - `testMinimizeOmitsNullAndDefaultValues()`: verifies `"pr_rank"` and `"kom_rank"` omitted when null, distance omitted when <= 0.
   - `testBackwardCompatibilityLegacyAndV2Parity()`: parses both formats and asserts identical `StravaActivity` models.
   - `testMalformedAndTruncatedJsonResilience()`: verifies parser safely returns `null` without throwing exceptions.
2. **Database Migration Test (`StravaUploadDbHelperMigrationTest.kt`)**:
   - Creates DB at version 5 with legacy uncompacted JSON rows.
   - Triggers upgrade to version 6.
   - Asserts rows are updated to `"v": 2` minimized schema.
   - Asserts malformed rows do not crash migration and become null/tombstone.
3. **Purge Test (`StravaDataPurgeManagerTest.kt`)**:
   - Verifies `clearAllStravaData()` and `checkpointWal()` execute cleanly.
4. **Full Regression Suite**:
   - Run `./gradlew testDebugUnitTest` across all modules.
