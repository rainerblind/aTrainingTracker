# Living Requirements: Persistence, Migration & Cloud Export

**Domain**: `data_migration_export`  
**Prefixes**: `DAT`, `EXP`, `EXT`, `MIG`  
**Scope**: Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export.  
**Total Requirements**: 119

---

### REQ-DAT-001: Store per-second sensor samples and extrema [Verified]
- **Tests**: `TST-STR-001`
- **Implementation**: `WorkoutSamplesDatabaseManager.java`
- **Rationale**: Granular data and peak performance insights.
- **Description**: Store per-second sensor samples and extrema.

### REQ-DAT-002: Support automatic deletion of old workouts [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `WorkoutDeletionHelper.java`
- **Rationale**: Manage device storage automatically.
- **Description**: Support automatic deletion of old workouts.

### REQ-DAT-003: Provide period-based statistics (W/M/Y) [Verified]
- **Tests**: `TST-MAN-001`, `TST-PERF-004`, `TST-BUG-002`
- **Implementation**: `PeriodsViewModel.kt`, `PeriodsRepository.kt`
- **Rationale**: Enable long-term training volume analysis.
- **Description**: Provide period-based statistics (W/M/Y).

### REQ-DAT-004: Encoded polylines and streams for storage/map [Verified]
- **Tests**: `TST-UNT-001`
- **Implementation**: `NumericalEncodingUtils.kt`
- **Rationale**: Optimize database size and UI performance.
- **Description**: Encoded polylines and streams for storage/map.

### REQ-DAT-005: Dynamic database schema evolution [Verified]
- **Tests**: `TST-STR-001`
- **Implementation**: `TrackerService.java`
- **Rationale**: Support future hardware without migrations.
- **Description**: Dynamic database schema evolution.

### REQ-EXP-001: Export Interoperability [Verified]
- **Tests**: `TST-REG-003`, `TST-EXP-004`
- **Implementation**: `writer/`, `TCXFileWriter.java`
- **Rationale**: The system SHALL support workout export to GPX, TCX, CSV, and GC JSON formats. The TCX export MUST strictly adhere to the Version 2.0 schema, ensuring that specific fields like `<Cadence>`, `<Watts>`, and `<RunCadence>` are formatted as integers to guarantee compatibility with professional analytical tools.

### REQ-EXP-002: Cloud Synchronization [Verified]
- **Tests**: `TST-REG-003`, `TST-REG-008`
- **Implementation**: `ExportManager.java`, `CloudUploadFragment.kt`
- **Rationale**: The system SHALL support automated workout uploads to Strava and Dropbox. The system SHALL correctly persist and reflect the connection state (Authorized/Disconnected) across application restarts and fragment navigation.

### REQ-EXP-003: Selective Upload (exclude specific data) [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `StravaUploader.kt`
- **Rationale**: Provide users control over data privacy.
- **Description**: Selective Upload (exclude specific data).

### REQ-EXP-006: Explicit "No upload" option for Strava mapping [Verified]
- **Tests**: `TST-UNT-006`, `TST-UNT-007`, `TST-INT-002`
- **Implementation**: `SportTypeDatabaseManager.java`, `EditWorkoutViewModel.kt`
- **Rationale**: Allow users to opt-out of Strava sync for specific custom sport types via a dedicated "- No upload -" mapping option.
- **Description**: Explicit "No upload" option for Strava mapping.

### REQ-EXP-007: Support latest Strava sport types [Verified]
- **Tests**: `TST-INT-003`
- **Implementation**: `arrays.xml`, `SportTypeDatabaseManager.java`
- **Rationale**: Ensure compatibility with modern Strava activity classification (e.g., Trail Run, Gravel Ride) for better social integration.
- **Description**: Support latest Strava sport types.

### REQ-EXP-008: Smart Asymmetric Workout Naming Strategy & Pre-Upload Duplicate Discovery for Strava [`StravaUploader.kt`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`]
- **Tests**: workoutName == fileBaseName`), the system SHALL omit the `name` parameter from the update payload, allowing Strava to assign its natural localized time-of-day title.
4. *System Invariants*:
• Sport type mapping (`TYPE`, `SPORT_TYPE`), equipment/gear ID (`GEAR_ID`), workout description (`DESCRIPTION`), trainer flag (`TRAINER`), and commute flag (`COMMUTE`) SHALL continue to be synchronized to Strava during both fresh and duplicate updates.
• TCX file export serialization (`REQ-DAT-013`) and spatial route clustering (`REQ-ROU-001`) MUST NOT be altered.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an old workout with `TIME_START = \"2024-05-12 10:15:30\"` and local name `\"Hausrunde #3\"` where Strava already contains an activity at that timestamp with default title `\"Radfahrt am Morgen\"`,
• *When* `doExport` executes,
• *Then* `findExistingStravaActivity` SHALL discover the existing Strava activity, bypass the TCX file upload (preventing duplicate activity creation on Strava), and preserve local SQLite `WorkoutSummaries.WORKOUT_NAME` as `\"Hausrunde #3\"`.
• *Given* an old workout with `TIME_START = \"2024-05-12 10:15:30\"` and local name `\"Cycling\"` where Strava already contains an activity with custom title `\"Sunday Club Ride\"`,
• *When* `doExport` executes,
• *Then* `findExistingStravaActivity` SHALL discover the existing Strava activity, bypass the TCX file upload, and enrich local SQLite `WorkoutSummaries.WORKOUT_NAME` to `\"Sunday Club Ride\"`.
• *Given* a workout not existing on Strava,
• *When* `doExport` executes,
• *Then* `findExistingStravaActivity` SHALL return null, and the system SHALL proceed to upload the TCX file via `POST /api/v3/uploads`.
• *Given* a workout with `WORKOUT_NAME = \"2024-05-12_10-15-30\"` (`fileBaseName = \"2024-05-12_10-15-30\"`) uploaded to Strava as a fresh activity,
• *When* `doUpdate` executes for the fresh upload,
• *Then* the update request sent to Strava SHALL NOT contain parameter `name`, allowing Strava to auto-name the activity.
• *Given* a workout with custom name `WORKOUT_NAME = \"Mountain Stage\"` (`fileBaseName = \"2024-05-12_10-15-30\"`) uploaded to Strava as a fresh activity,
• *When* `doUpdate` executes for the fresh upload,
• *Then* the update request sent to Strava SHALL contain parameter `name = \"Mountain Stage\"`.
- **Rationale**: The system SHALL implement a smart asymmetric naming strategy and pre-upload duplicate discovery when synchronizing workouts to Strava (`StravaUploader.kt`), distinguishing fresh uploads from existing/duplicate activities:
1. *Pre-Upload Duplicate Discovery (Existing Activity Check)*:
• Before submitting a TCX file to Strava's upload endpoint (`POST /api/v3/uploads`), the system SHALL query Strava's athlete activities API (`GET /api/v3/athlete/activities?after={start - 300}&before={start + 300}`) using the workout's start timestamp (`TIME_START`).
• If an activity already exists on Strava within this temporal window (or if already recorded in `StravaUploadDbHelper`), the system SHALL bypass the TCX file upload entirely, preventing Strava from creating an unwanted duplicate activity in the athlete's feed. The system SHALL link the discovered Strava `activity_id` in `StravaUploadDbHelper` and immediately proceed with the duplicate reconciliation flow.
2. *Duplicate Reconciliation Flow (`isDuplicate == true` or existing Strava activity)*:
• *Strava Authority & Overwrite Protection*: When a workout already exists on Strava (via pre-upload query, local DB record, or upload duplicate error), the system SHALL NOT submit the workout name parameter (`NAME` / `"name"`) in the metadata update request (`doUpdate`). The activity title existing on Strava SHALL be preserved as authoritative and MUST NOT be overwritten by the app.
• *Local Database Enrichment & Strava Default Name Evaluation*: The system SHALL inspect the Strava activity name (`"name"`):
- *Default Strava Name*: If the Strava name matches an auto-generated localized time-of-day default template (e.g., `"Radfahrt am Morgen"`, `"Lauf am Abend ⛅"`, `"Morning Ride"`, `"Afternoon Run"`), the app SHALL preserve the local name generated by the app (e.g. from route clustering or TCX tags) and SHALL NOT overwrite the local name in SQLite.
- *Custom Strava Title*: If the Strava name is different from Strava default templates (e.g., `"Sunday Club Ride"`, `"Schwarzwald Tour"`, `"Berlin Marathon"`), the system SHALL update `WorkoutSummaries.WORKOUT_NAME` in SQLite with the custom title from Strava.
• *Resilient Duplicate Fallback*: In `checkAndUpdateDuplicate()`, the system SHALL inspect both `error` and `status` response properties using regex pattern matching (`"duplicate of.*?(\\d+)"`) as a fallback if pre-upload check was bypassed.
3. *Fresh Upload Flow (`isDuplicate == false`)*:
• *Meaningful Name Serialization*: For newly created Strava activities, the system SHALL include the `name` parameter in the metadata update payload if and only if `workoutName` is non-blank and does NOT equal `fileBaseName`.
• *Strava Auto-Naming Preservation*: If the local workout has no custom name (`workoutName.isNullOrBlank()

### REQ-EXP-009: Strava Duplicate Reconciliation Equipment, Retired Gear & Detailed Sport Type Enrichment [Verified]
- **Tests**: `TST-EXP-006`
- **Implementation**: `StravaUploader.kt`, `EquipmentDbHelper.java`, `SportTypeDatabaseManager.java`, `WorkoutRepository.kt`
- **Rationale**: The system SHALL enrich imported workouts matching existing Strava activities (`isDuplicate == true`) with equipment and detailed sport classification without overwriting Strava cloud data:
1. *Strava Cloud Authority on Duplicate*: When reconciling an existing Strava activity, the system SHALL NOT submit `type`/`sport_type` or `gear_id` to Strava's update API (`doUpdate`), preserving Strava's cloud metadata as authoritative.
2. *Equipment Schema & Reverse Lookup*: `Equipment.db` SHALL maintain a `Retired` column (`INTEGER DEFAULT 0`, version 2 migration) indicating retirement status. `EquipmentDbHelper` SHALL provide reverse lookup `getIdFromStravaId(String stravaId)`.
3. *On-Demand Lazy Fetch for Missing / Retired Gear*: If a duplicate activity has `gear_id != null` that does not exist in local `Equipment.db`, the system SHALL query Strava's `GET /api/v3/gear/{gear_id}`. The returned equipment SHALL be inserted into `Equipment.db` using its original Strava `name`, `strava_id`, `frame_type`, derived `sport_type` (BIKE vs RUN), and `retired` status (`Retired = (retired ? 1 : 0)`).
4. *Local Workout Equipment Enrichment*: If `gear_id` resolves to a local equipment record (either pre-existing or freshly fetched), `WorkoutSummaries.EQUIPMENT_ID` in SQLite SHALL be updated with the resolved local `_ID`.
5. *Detailed Sport Type Enrichment*: If the Strava activity contains `sport_type` or `type`, `SportTypeDatabaseManager` SHALL resolve the local `sport_id` via `getSportTypeIdFromStravaName(stravaName)` (e.g. `MountainBikeRide` -> MTB), falling back to `BSportType` mapping if no direct sub-sport match exists. `WorkoutSummaries.SPORT_ID` in SQLite SHALL be updated with the resolved `sport_id`.
6. *In-Memory Cache Synchronization*: `WorkoutRepository.reloadExportStatusesFor(fileName)` SHALL reload the full workout session from `WorkoutSummariesDatabaseManager`, updating `sportId`, `sportName`, `bSportType`, and `equipmentId` in the in-memory cache so UI views update reactively.
7. *System Invariants*: Fresh uploads (`isDuplicate == false`) SHALL continue to serialize local equipment and sport types. Default title preservation rules from `REQ-EXP-008` SHALL remain intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a duplicated workout where Strava has `gear_id = \"b123\"` corresponding to local bike ID 5,
• *When* duplicate reconciliation executes,
• *Then* `WorkoutSummaries.EQUIPMENT_ID` SHALL be updated to 5, and Strava's cloud gear SHALL NOT be overwritten.
• *Given* a duplicated workout where Strava has `gear_id = \"b999\"` not present in `Equipment.db`,
• *When* duplicate reconciliation executes,
• *Then* `GET /api/v3/gear/b999` is queried, the equipment is saved in `Equipment.db` with original Strava name and `Retired = 1` if retired on Strava, and its new local `_ID` is assigned to `WorkoutSummaries.EQUIPMENT_ID`.
• *Given* a duplicated workout where Strava has `sport_type = \"MountainBikeRide\"` and local workout was imported generically as `BIKE`,
• *When* duplicate reconciliation executes,
• *Then* `WorkoutSummaries.SPORT_ID` SHALL be updated to MTB.
• *Given* a successful reconciliation with enriched equipment and sport type,
• *When* `reloadExportStatusesFor` completes,
• *Then* `WorkoutRepository` in-memory cache reflects the updated `sportName` and `equipmentId`.

### REQ-EXP-010: Strava Segment Personal Best Update & Achievement Presentation []
- **Tests**: currentPrTime <= 0`), the system SHALL update `pr_time = newPrTimeSeconds` in SQLite and return `true`.
• If `stravaSegmentId` does not exist in `StarredSegmentsTable` or the existing PR time is already faster or equal, the database record SHALL NOT be altered and the method SHALL return `false`.
3. *Reactive In-Memory State Propagation (`SegmentsRepository`)*:
• Upon a successful database update (`updateSegmentPrTime == true`), `StravaUploader` SHALL notify `SegmentsRepository.getInstance(context).updateSegmentPr(segmentId, elapsedTimeSec)`.
• `SegmentsRepository` SHALL update the matching segment's `SegmentSummary` (`prTime_raw` and formatted `prTime`) within its internal `_allSegmentsWithPath` flow, immediately propagating the new PR to observers (`SegmentListViewModel`, `LiveSegmentsRepository`) without requiring an application restart or manual re-sync.
4. *Prominent Achievement Presentation in Workout Summary*:
• When a workout contains $\ge 1$ segment efforts qualifying as a new personal best (`prRank == 1
- **Rationale**: The system SHALL update segment Personal Best (PB) records in the local database based on activity feedback received from Strava after workout upload, refresh in-memory segment state reactively, and prominently celebrate new achievements:
1. *Strava Activity Effort Parsing & PB Detection*:
• In `StravaUploader.kt`, upon retrieving detailed activity feedback (`getStravaActivity`) during `doUpdate()`, the system SHALL parse the `segment_efforts` array.
• For each effort having a valid Strava segment ID (`segment.id` or `segment_id`), duration (`elapsed_time > 0`), and representing a personal record (`pr_rank == 1`, `kom_rank == 1`, or achievements array containing `type == "pr"` or `"kom"` with `rank == 1`), the system SHALL trigger a local database update.
2. *Database Persistence (`SegmentsDatabaseManager`)*:
• The system SHALL provide `updateSegmentPrTime(long stravaSegmentId, int newPrTimeSeconds): boolean` in `SegmentsDatabaseManager.java`.
• If `stravaSegmentId` exists in `StarredSegmentsTable` and the new duration is faster than the existing record (`newPrTimeSeconds < currentPrTime

### REQ-EXP-011: Strava Segments Mandatory Daily Automated Synchronization [Verified]
- **Tests**: `TST-EXP-008`
- **Implementation**: `StravaSettingsDialog.kt`, `StravaSegmentsSyncWorker.kt`, `TrainingApplication.java`, `SegmentsRepository.kt`
- **Rationale**: The system SHALL support mandatory automated daily background synchronization of starred Strava segments via Android `WorkManager` (ATT-913, ATT-1178):
1. *Mandatory Daily Scheduling*: When Strava is authenticated (`TrainingApplication.getStravaAccessToken() != null`), `StravaSegmentsSyncWorker.schedule(context)` SHALL enqueue unique periodic work `automated_strava_segments_sync_work` with a fixed interval of 1 day (`1, TimeUnit.DAYS`), `NetworkType.CONNECTED`, and `requiresBatteryNotLow(true)`.
2. *No User-Configurable Intervals ("No need to ask the user")*: The system SHALL NOT prompt the user or allow configuring intervals or disabling automated sync; `StravaSettingsDialog.kt` SHALL omit automated sync toggles and interval dropdowns.
3. *Cancellation upon Disconnect*: When Strava is disconnected, `schedule(context)` SHALL cancel the periodic work via `WorkManager.cancelUniqueWork("automated_strava_segments_sync_work")`.
4. *Execution & TTL Pruning*: In `doWork()`, the worker SHALL execute 7-day TTL cache pruning, then invoke `SegmentsRepository.getInstance(applicationContext).syncStarredSegments(BSportType.UNKNOWN)` and update `last_update_time_of_strava_segments` in SharedPreferences.
5. *Manual Synchronization Cards*: `StravaSettingsDialog` SHALL maintain the `OutlinedCard` for manual segment synchronization (`@string/updateStravaSegments`), displaying the formatted timestamp of the last synchronization.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete authenticated with Strava,
• *When* `StravaSegmentsSyncWorker.schedule(context)` is called,
• *Then* `WorkManager` SHALL have unique periodic work `automated_strava_segments_sync_work` enqueued with fixed 1-day interval and connected network constraints.
• *Given* an athlete disconnected from Strava,
• *When* `schedule(context)` is called,
• *Then* `WorkManager.cancelUniqueWork(\"automated_strava_segments_sync_work\")` SHALL be invoked.
• *Given* an athlete viewing `StravaSettingsDialog`,
• *When* the dialog renders,
• *Then* automated sync toggles and interval dropdowns SHALL NOT be displayed, and manual update cards with timestamps SHALL be displayed.

### REQ-EXP-012: Strava Routes Mandatory Daily Automated Synchronization [Verified]
- **Tests**: `TST-EXP-009`
- **Implementation**: `StravaSettingsDialog.kt`, `StravaRoutesSyncWorker.kt`, `TrainingApplication.java`, `RoutesRepository.kt`
- **Rationale**: The system SHALL support mandatory automated daily background synchronization of Strava routes via Android `WorkManager` (ATT-914, ATT-1178):
1. *Mandatory Daily Scheduling*: When Strava is authenticated (`TrainingApplication.getStravaAccessToken() != null`), `StravaRoutesSyncWorker.schedule(context)` SHALL enqueue unique periodic work `automated_strava_routes_sync_work` with a fixed interval of 1 day (`1, TimeUnit.DAYS`), `NetworkType.CONNECTED`, and `requiresBatteryNotLow(true)`.
2. *No User-Configurable Intervals ("No need to ask the user")*: The system SHALL NOT prompt the user or allow configuring intervals or disabling automated sync; `StravaSettingsDialog.kt` SHALL omit automated sync toggles and interval dropdowns.
3. *Cancellation upon Disconnect*: When Strava is disconnected, `schedule(context)` SHALL cancel the periodic work via `WorkManager.cancelUniqueWork("automated_strava_routes_sync_work")`.
4. *Execution & TTL Pruning*: In `doWork()`, the worker SHALL execute 7-day TTL cache pruning, then invoke `RoutesRepository.getInstance(applicationContext).syncRoutesFromStrava()` and update `lastUpdateTimeOfStravaRoutes` in SharedPreferences.
5. *Manual Synchronization Cards*: `StravaSettingsDialog` SHALL maintain the `OutlinedCard` for manual route synchronization (`@string/updateStravaRoutes`), displaying the formatted timestamp of the last synchronization.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete authenticated with Strava,
• *When* `StravaRoutesSyncWorker.schedule(context)` is called,
• *Then* `WorkManager` SHALL have unique periodic work `automated_strava_routes_sync_work` enqueued with fixed 1-day interval and connected network constraints.
• *Given* an athlete disconnected from Strava,
• *When* `schedule(context)` is called,
• *Then* `WorkManager.cancelUniqueWork(\"automated_strava_routes_sync_work\")` SHALL be invoked.
• *Given* an athlete viewing `StravaSettingsDialog`,
• *When* the dialog renders,
• *Then* automated sync toggles and interval dropdowns SHALL NOT be displayed, and manual update cards with timestamps SHALL be displayed.

### REQ-EXP-013: Strava Activity Feedback Data Minimization to Athlete Achievements [`TST-EXP-010`]
- **Tests**: `StravaActivityData.kt`, `StravaUploader.kt`, `StravaUploadDbHelper.java`, `StravaActivitySection.kt`
- **Implementation**: Comply with Strava API Agreement Section 6.2 (Cache & Retention), eliminate database bloat, and protect UI thread stability via strict CQS.
- **Rationale**: The system SHALL minimize persisted Strava activity response payloads to athlete-specific achievements and preserve strict Command-Query Separation across database interactions (ATT-1190):
1. *Data Minimization to Achievements*: Upon receiving activity feedback (`GET /api/v3/activities/{id}`), `StravaUploader` SHALL NOT persist raw third-party JSON blobs containing transient social, profile, map, or partner fields to SQLite. Instead, the system SHALL extract only the athlete's own achievements and serialize a compact JSON payload (`"v": 2`) containing strictly `id`, `segment_efforts` (`name`, `elapsed_time`, `pr_rank`, `kom_rank`, `starred`, `segment_id`), and `best_efforts` (`name`, `elapsed_time`, `pr_rank`, `distance`). All extraneous fields (kudos, comments, athlete profiles, photos, external URLs, polyline vectors) SHALL be discarded before storage.
2. *Deterministic Omission of Null/Absent Fields*: In the minimized schema (`"v": 2`), optional fields that are null, empty, non-positive, or false (e.g. unranked efforts, unstarred segments, zero distances) SHALL be omitted from serialization rather than written as explicit JSON nulls.
3. *Strict Command-Query Separation (Zero Write-on-Read)*: `StravaUploadDbHelper.getStravaActivityData()` and related query methods SHALL remain strictly read-only and MUST NOT perform implicit write operations, row updates, or database mutations during cursor reads.
4. *Robustness & Truncation Resilience*: `StravaActivityParser.parse()` SHALL handle both legacy uncompacted JSON (v1) and minimized JSON (v2) transparently. If a payload is truncated, corrupted, or malformed, the parser SHALL catch `JSONException`, log a warning, and safely return `null` without throwing unhandled runtime exceptions.
5. *System Invariants*:
• In-memory duplicate reconciliation and segment PR extraction (`processSegmentEffortsForPrs`) in `StravaUploader` SHALL continue to execute on the full in-memory response prior to storage.
• Offline rendering in `StravaActivitySection` (celebration banners, best efforts, segment accordions) SHALL retain 100% visual and functional parity.
• `StravaDataPurgeManager.purgeAllStravaData()` SHALL continue to wipe all local records on deauthorization.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a raw Strava activity response containing kudos, athlete profile, photos, comments, and segment efforts,
• *When* `StravaActivityParser.minimize(...)` or `StravaUploader.doUpdate` processes the payload,
• *Then* the persisted JSON SHALL contain `"v": 2`, athlete achievements (`segment_efforts`, `best_efforts`), and SHALL NOT contain athlete profile or social engagement fields.
• *Given* a segment effort with no PR (`pr_rank == null`),
• *When* serialized to minimized JSON,
• *Then* `"pr_rank"` SHALL be omitted from the effort JSON object.
• *Given* a truncated or malformed JSON string stored in SQLite,
• *When* `StravaActivityParser.parse()` executes,
• *Then* it SHALL return `null` safely without crashing the UI thread.
• *Given* any read invocation of `StravaUploadDbHelper.getStravaActivityData()`,
• *When* executed,
• *Then* zero database write transactions or row updates SHALL be executed.

### REQ-EXP-014: Reactive Strava Segment Synchronization Progress Indicator & Transient State Decoupling [Verified]
- **Tests**: `TST-EXP-011`
- **Implementation**: `SegmentsRepository.kt`, `StravaSettingsDialog.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide reactive in-memory synchronization progress tracking for starred Strava segments and decouple transient UI state from persistent storage (ATT-1219):
1. *Reactive In-Memory State Flow*: `SegmentsRepository` SHALL expose an overarching `val isSyncing: StateFlow<Boolean>` property that transitions to `true` upon the initiation of starred segment synchronization (`syncStarredSegments`) and reliably resets to `false` via a `finally` block upon completion or failure.
2. *Defensive Worker Cleanup*: In `syncStarredSegmentsWorker(bSportType)`, the system SHALL wrap the worker execution in `try ... finally` to ensure `_refreshingSports.update { it - bSportType }` is guaranteed even when network or JSON parsing exceptions occur.
3. *Transient State UI Decoupling (Storage Invariant)*: The system SHALL NOT persist transient progress states or localized temporal indicators (such as `"now"` / `"jetzt"`) to `SharedPreferences` (`SP_LAST_UPDATE_TIME_OF_STRAVA_SEGMENTS`). SharedPreferences SHALL strictly store formatted historical completion timestamps (`DateFormat.getDateTimeInstance().format(Date())`) or remain uninitialized ("never").
4. *Reactive Composable Rendering in Strava Popup*: In `StravaSettingsDialog`, the secondary text under the "Update Strava Segments" card (`@string/updateStravaSegments`) SHALL collect `isSyncing` from `SegmentsRepository`. While `isSyncing` is `true`, the Composable SHALL render `@string/lastUpdateOfSegmentsNow` directly via Compose `stringResource()`. While `isSyncing` is `false`, the Composable SHALL render the formatted completion timestamp (`segmentsLastUpdate`).
5. *Manual Sync Debounce Guard*: `StravaSettingsDialog` SHALL debounce manual sync requests by ignoring card click events while `isSyncing` is `true`, preventing redundant background coroutine dispatches.
6. *Localization Parity (9 Locales)*: String resource `lastUpdateOfSegmentsNow` SHALL be localized across all 9 supported application locales (EN: `"now"`, DE: `"jetzt"`, ES: `"ahora"`, FR: `"maintenant"`, IT: `"adesso"`, JA: `"今"`, NL: `"nu"`, PL: `"teraz"`, PT: `"agora"`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing `StravaSettingsDialog` with an idle segment repository (`isSyncing == false`),
• *When* the athlete taps the "Update Strava Segments" card,
• *Then* `SegmentsRepository.isSyncing` SHALL emit `true`, the card secondary text SHALL immediately display the localized `"now"` string (`@string/lastUpdateOfSegmentsNow`), and `SharedPreferences` SHALL NOT be mutated with `"now"`.
• *Given* active segment synchronization (`isSyncing == true`),
• *When* the athlete taps the "Update Strava Segments" card again,
• *Then* the click handler SHALL be ignored and no additional background sync tasks SHALL be dispatched.
• *Given* active segment synchronization (`isSyncing == true`),
• *When* the synchronization completes successfully,
• *Then* `SharedPreferences` SHALL be updated with the current formatted date-time string, `isSyncing` SHALL emit `false`, and the card secondary text SHALL display the formatted completion timestamp.
• *Given* active segment synchronization (`isSyncing == true`),
• *When* the network call fails or throws an exception,
• *Then* `isSyncing` SHALL reset to `false` via `finally`, `SharedPreferences` SHALL retain the prior uncorrupted timestamp, and the card secondary text SHALL revert to the prior timestamp.
• *Given* any supported device locale (EN, DE, ES, FR, IT, JA, NL, PL, PT),
• *When* segment synchronization is active,
• *Then* the card secondary text SHALL display the correct translation of `"now"`.

### REQ-EXP-015: Reactive Strava Route Synchronization Progress Indicator & Transient State Decoupling [Verified]
- **Tests**: `TST-EXP-012`
- **Implementation**: `RoutesRepository.kt`, `StravaSettingsDialog.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide reactive in-memory synchronization progress tracking for Strava routes and decouple transient UI state from persistent storage (ATT-1230):
1. *Reactive In-Memory State Flow*: `RoutesRepository` SHALL expose an overarching `val isSyncing: StateFlow<Boolean>` property that transitions to `true` upon the initiation of route synchronization (`syncRoutesFromStrava`) and reliably resets to `false` via a `finally` block upon completion or failure.
2. *Defensive Concurrency & Lifecycle Guarantee*: The synchronization execution SHALL be wrapped in `try ... finally` under `syncMutex.withLock` to guarantee that `_isSyncing` resets to `false` even when network, JSON decoding, or database exceptions occur.
3. *Transient State UI Decoupling (Storage Invariant)*: The system SHALL NOT persist transient progress states or localized temporal indicators (such as `"now"` / `"jetzt"`) to `SharedPreferences` (`SP_LAST_UPDATE_TIME_OF_STRAVA_ROUTES`). SharedPreferences SHALL strictly store formatted historical completion timestamps (`DateFormat.getDateTimeInstance().format(Date())`) or remain uninitialized ("never").
4. *Reactive Composable Rendering in Strava Popup*: In `StravaSettingsDialog`, the secondary text under the "Update Strava Routes" card (`@string/updateStravaRoutes`) SHALL collect `isSyncing` from `RoutesRepository`. While `isSyncing` is `true`, the Composable SHALL render `@string/lastUpdateOfRoutesNow` directly via Compose `stringResource()`. While `isSyncing` is `false`, the Composable SHALL render the formatted completion timestamp (`routesLastUpdate`).
5. *Manual Sync Debounce Guard*: `StravaSettingsDialog` SHALL debounce manual sync requests by ignoring card click events while `isSyncing` is `true`, preventing redundant background coroutine dispatches.
6. *Localization Parity (9 Locales)*: String resource `lastUpdateOfRoutesNow` SHALL be localized across all 9 supported application locales (EN: `"now"`, DE: `"jetzt"`, ES: `"ahora"`, FR: `"maintenant"`, IT: `"adesso"`, JA: `"今"`, NL: `"nu"`, PL: `"teraz"`, PT: `"agora"`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing `StravaSettingsDialog` with an idle route repository (`isSyncing == false`),
• *When* the athlete taps the "Update Strava Routes" card,
• *Then* `RoutesRepository.isSyncing` SHALL emit `true`, the card secondary text SHALL immediately display the localized `"now"` string (`@string/lastUpdateOfRoutesNow`), and `SharedPreferences` SHALL NOT be mutated with `"now"`.
• *Given* active route synchronization (`isSyncing == true`),
• *When* the athlete taps the "Update Strava Routes" card again,
• *Then* the click handler SHALL be ignored and no additional background sync tasks SHALL be dispatched.
• *Given* active route synchronization (`isSyncing == true`),
• *When* the synchronization completes successfully,
• *Then* `SharedPreferences` SHALL be updated with the current formatted date-time string, `isSyncing` SHALL emit `false`, and the card secondary text SHALL display the formatted completion timestamp.
• *Given* active route synchronization (`isSyncing == true`),
• *When* the network call fails or throws an exception,
• *Then* `isSyncing` SHALL reset to `false` via `finally`, `SharedPreferences` SHALL retain the prior uncorrupted timestamp, and the card secondary text SHALL revert to the prior timestamp.
• *Given* any supported device locale (EN, DE, ES, FR, IT, JA, NL, PL, PT),
• *When* route synchronization is active,
• *Then* the card secondary text SHALL display the correct translation of `"now"`.

### REQ-EXP-016: Reactive Strava Equipment Synchronization Progress Indicator & Transient State Decoupling [Verified]
- **Tests**: `TST-EXP-013`
- **Implementation**: `EquipmentRepository.kt`, `StravaEquipmentSynchronizeThread.java`, `StravaSettingsDialog.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide reactive in-memory synchronization progress tracking for Strava equipment and decouple transient UI state from persistent storage (ATT-1231):
1. *Reactive In-Memory State Flow*: `EquipmentRepository` SHALL expose an overarching `@JvmStatic val isSyncing: StateFlow<Boolean>` property that transitions to `true` upon the initiation of equipment synchronization (`StravaEquipmentSynchronizeThread`) and reliably resets to `false` via a `finally` block upon completion or failure.
2. *Defensive Concurrency & Lifecycle Guarantee*: The synchronization execution in `StravaEquipmentSynchronizeThread.run()` SHALL be wrapped in `try ... catch (Throwable) ... finally` to guarantee that `EquipmentRepository.setSyncing(false)` is unconditionally executed even when network, JSON decoding, or database exceptions occur.
3. *Two-Tier Concurrency Debounce Guard*: The system SHALL implement a two-tier debounce mechanism to prevent concurrent synchronization jobs: (a) in `StravaSettingsDialog`, click events on the "Update Strava Equipment" card SHALL be ignored while `isSyncing` is `true`; (b) in `StravaEquipmentSynchronizeThread.run()`, the thread SHALL check `EquipmentRepository.isSyncing().getValue()` and immediately return if a synchronization job is already active.
4. *Transient State UI Decoupling (Storage Invariant)*: The system SHALL NOT persist transient progress states or localized temporal indicators (such as `"now"` / `"jetzt"`) to `SharedPreferences` (`SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT`). SharedPreferences SHALL strictly store formatted historical completion timestamps (`DateFormat.getDateTimeInstance().format(Date())`) or remain uninitialized ("never").
5. *Reactive Composable Rendering in Strava Popup*: In `StravaSettingsDialog`, the secondary text under the "Update Strava Equipment" card (`@string/updateStravaEquipment`) SHALL collect `isSyncing` from `EquipmentRepository`. While `isSyncing` is `true`, the Composable SHALL render `@string/lastUpdateOfEquipmentNow` directly via Compose `stringResource()`. While `isSyncing` is `false`, the Composable SHALL render the formatted completion timestamp (`equipmentLastUpdate`).
6. *Localization Parity (9 Locales)*: String resource `lastUpdateOfEquipmentNow` SHALL be localized across all 9 supported application locales (EN: `"now"`, DE: `"jetzt"`, ES: `"ahora"`, FR: `"maintenant"`, IT: `"adesso"`, JA: `"今"`, NL: `"nu"`, PL: `"teraz"`, PT: `"agora"`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing `StravaSettingsDialog` with an idle equipment repository (`isSyncing == false`),
• *When* the athlete taps the "Update Strava Equipment" card,
• *Then* `EquipmentRepository.isSyncing` SHALL emit `true`, the card secondary text SHALL immediately display the localized `"now"` string (`@string/lastUpdateOfEquipmentNow`), and `SharedPreferences` SHALL NOT be mutated with `"now"`.
• *Given* active equipment synchronization (`isSyncing == true`),
• *When* the athlete taps the "Update Strava Equipment" card again,
• *Then* the click handler SHALL be ignored and no additional background sync tasks SHALL be dispatched.
• *Given* active equipment synchronization (`isSyncing == true`),
• *When* `StravaEquipmentSynchronizeThread.run()` is invoked programmatically,
• *Then* the worker thread SHALL exit immediately without dispatching duplicate HTTP requests.
• *Given* active equipment synchronization (`isSyncing == true`),
• *When* the synchronization completes successfully,
• *Then* `SharedPreferences` SHALL be updated with the current formatted date-time string, `isSyncing` SHALL emit `false`, and the card secondary text SHALL display the formatted completion timestamp.
• *Given* active equipment synchronization (`isSyncing == true`),
• *When* the network call fails or throws an exception,
• *Then* `isSyncing` SHALL reset to `false` via `finally`, `SharedPreferences` SHALL retain the prior uncorrupted timestamp, and the card secondary text SHALL revert to the prior timestamp.
• *Given* any supported device locale (EN, DE, ES, FR, IT, JA, NL, PL, PT),
• *When* equipment synchronization is active,
• *Then* the card secondary text SHALL display the correct translation of `"now"`.

### REQ-EXP-017: Non-Blocking Strava Equipment Background Synchronization & Modal Dialog Elimination [Verified]
- **Tests**: `TST-EXP-014`
- **Implementation**: `StravaEquipmentSynchronizeThread.java`, `StravaSettingsDialog.kt`, `EquipmentRepository.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL execute Strava equipment synchronization strictly as a non-blocking background task without presenting any modal progress dialogs, toasts, or window-blocking UI notifications (ATT-1242):
1. *Dialog-Free Background Worker Thread*: `StravaEquipmentSynchronizeThread` SHALL NOT instantiate, configure, show, or dismiss `android.app.ProgressDialog` or any modal dialog. Synchronization HTTP requests, JSON parsing, SQLite updates in `EquipmentDbHelper`, and frame type queries SHALL proceed non-blockingly on the background thread.
2. *Decoupled Context Constructor*: `StravaEquipmentSynchronizeThread` SHALL accept a generic `Context` (`Application` or `Activity`) without requiring an active `Activity` window token or casting to `Activity`.
3. *Clean Progress Handling*: The system SHALL NOT invoke intermediate progress updates or post UI updates to the Android Main Looper during gear synchronization.
4. *Completion Broadcast Preservation (Integration Invariant)*: Upon completion of background synchronization, `StravaEquipmentSynchronizeThread` SHALL broadcast `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED` (`de.rainerblind.trainingtracker.equipment.StravaEquipmentHelper.SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED`) with the application's package name to trigger equipment reload in `EquipmentFragment`.
5. *Completion Timestamp Update (Persistence Invariant)*: Upon completion, `StravaEquipmentSynchronizeThread` SHALL update `TrainingApplication.setLastUpdateTimeOfStravaEquipment(result)` with the formatted timestamp, preserving decoupling from transient state (`REQ-EXP-016`).
6. *Two-Tier Concurrency Debounce & Lifecycle Guarantee*: The thread SHALL enforce the debounce check (`EquipmentRepository.isSyncing().getValue()`) and wrap execution in `try ... catch (Throwable) ... finally { EquipmentRepository.setSyncing(false); }` to guarantee that reactive sync state is released under all completion and failure paths (`REQ-EXP-016`).
7. *Non-Blocking Composable Launch*: In `StravaSettingsDialog`, tapping the 'Update Strava Equipment' card while idle (`isEquipmentSyncing == false`) SHALL launch `StravaEquipmentSynchronizeThread(context).start()` without casting to `Activity` and without blocking Compose interactions or displaying modal dialogs.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing `StravaSettingsDialog` with an idle equipment repository (`isSyncing == false`),
• *When* the athlete taps the "Update Strava Equipment" card,
• *Then* `StravaEquipmentSynchronizeThread` SHALL start in the background, `EquipmentRepository.isSyncing` SHALL emit `true`, the card secondary text SHALL display the localized `"now"` string, and NO modal progress dialog or window-attached view SHALL appear on screen.
• *Given* active equipment synchronization running in the background,
• *When* the athlete interacts with other toggles in `StravaSettingsDialog`, scrolls, or dismisses the bottom sheet,
• *Then* user touch events SHALL NOT be blocked, no window leak warning or `WindowManager.BadTokenException` SHALL occur, and the background synchronization SHALL continue uninterrupted.
• *Given* `StravaEquipmentSynchronizeThread` running in a headless test environment or non-Activity context,
• *When* `run()` executes,
• *Then* it SHALL execute successfully without throwing `WindowManager.BadTokenException` or attempting UI operations.
• *Given* background equipment synchronization completing successfully,
• *When* all gear arrays are processed and written to SQLite,
• *Then* `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED` SHALL be broadcast, `EquipmentRepository.isSyncing` SHALL reset to `false`, and `EquipmentFragment` SHALL receive the broadcast and refresh its equipment list.

### REQ-EXP-019: Automated Background Synchronization for Strava Segments, Routes, and Equipment [`TST-EXP-016`]
- **Tests**: `TrainingApplication.java`, `StravaSegmentsSyncWorker.kt`, `StravaRoutesSyncWorker.kt`, `StravaEquipmentSyncWorker.kt`, `StravaDataPurgeManager.kt`, `strings.xml` (all 9 locales)
- **Implementation**: Fix automated background synchronization for Strava segments, routes, and equipment, honoring user preferences, intervals, and token refresh.
- **Rationale**: The system SHALL reliably execute automated background synchronization for starred Strava segments, routes, and equipment via WorkManager, honoring user preference toggles and interval schedules, preventing startup cancellation race conditions, and proactively refreshing OAuth credentials (ATT-3478, amending `REQ-EXP-011` and `REQ-EXP-012`):
1. *Initialization Ordering & Startup Race Prevention (`TrainingApplication.java`)*:
• The system SHALL guarantee that `PreferenceManager.setDefaultValues()` and `cSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)` execute before `initWorkManager()` and background worker scheduling in `TrainingApplication.onCreate()`.
• `TrainingApplication.getStravaAccessToken()` SHALL NOT return null on startup due to uninitialized preferences, preventing erroneous cancellation of scheduled periodic work.
2. *Preference Reading & Setting Parity (`TrainingApplication.java`)*:
• `isAutomatedStravaSegmentsSyncEnabled()` SHALL evaluate `cSharedPreferences.getBoolean(SP_AUTOMATED_STRAVA_SEGMENTS_SYNC, true)`.
• `getStravaSegmentsSyncIntervalDays()` SHALL evaluate `cSharedPreferences.getString(SP_STRAVA_SEGMENTS_SYNC_INTERVAL_DAYS, "1")`.
• `isAutomatedStravaRoutesSyncEnabled()` SHALL evaluate `cSharedPreferences.getBoolean(SP_AUTOMATED_STRAVA_ROUTES_SYNC, true)`.
• `getStravaRoutesSyncIntervalDays()` SHALL evaluate `cSharedPreferences.getString(SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS, "1")`.
• The system SHALL declare `SP_AUTOMATED_STRAVA_EQUIPMENT_SYNC` and `SP_STRAVA_EQUIPMENT_SYNC_INTERVAL_DAYS`, providing corresponding getters (`isAutomatedStravaEquipmentSyncEnabled`, `getStravaEquipmentSyncIntervalDays`) and setters adhering to default values (`true` and `"1"`).
3. *Automated Strava Equipment Synchronization Worker (`StravaEquipmentSyncWorker.kt`)*:
• The system SHALL provide `StravaEquipmentSyncWorker` extending `CoroutineWorker` registered under unique work name `automated_strava_equipment_sync_work`.
• `StravaEquipmentSyncWorker.Companion.schedule(context)` SHALL enqueue unique periodic work using `ExistingPeriodicWorkPolicy.UPDATE`, connected network constraint (`NetworkType.CONNECTED`), `requiresBatteryNotLow(true)`, and exponential backoff retry criteria.
• If Strava is disconnected or automated equipment sync is disabled, `schedule(context)` SHALL cancel `automated_strava_equipment_sync_work`.
• In `doWork()`, the worker SHALL check that Strava is authenticated and equipment sync is enabled, obtain a refreshed access token via `StravaHelper.getRefreshedAccessToken()`, execute equipment synchronization via `StravaEquipmentSynchronizeThread`, and return `Result.success()`. If sync throws an unhandled exception or returns a failure status, it SHALL return `Result.retry()`.
• `StravaEquipmentSyncWorker.schedule(this)` SHALL be scheduled during `TrainingApplication.onCreate()` and upon successful OAuth authentication in `StravaSettingsDialog.kt`.
4. *Configurable Interval & Toggle Honoring in Schedulers (`StravaSegmentsSyncWorker.kt` & `StravaRoutesSyncWorker.kt`)*:
• Worker `schedule(context)` methods SHALL query the user-configured interval days (`Long` days, defaulting to `1L`).
• If the respective automated sync toggle is disabled in preferences, `schedule(context)` SHALL cancel the periodic work request.
• In `doWork()`, workers SHALL verify the toggle preference and obtain a refreshed access token via `StravaHelper.getRefreshedAccessToken()`, returning `Result.success()` without network churn if disabled or unlinked.
5. *Purge & Disconnect Invariant (`StravaDataPurgeManager.kt`)*:
• `StravaDataPurgeManager.executeLocalDataPurge(context)` SHALL cancel `automated_strava_equipment_sync_work` alongside segments and routes tasks.
6. *100% 9-Language Localization Parity*:
• New string resources for automated equipment sync (`automated_strava_equipment_sync`, `automated_strava_equipment_sync_summary`, `strava_equipment_sync_interval`) SHALL be defined and translated across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-EXP-011` (*Strava Segments Mandatory Daily Automated Synchronization*) and `REQ-EXP-012` (*Strava Routes Mandatory Daily Automated Synchronization*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.2 (`ATT-913`, `ATT-914`, `ATT-1178`), Sprint 2026-42.3 (`ATT-3478`).
3. *Root Reason for Existing Formulation*: Schedulers previously hardcoded 1-day intervals and ignored user preference toggles, equipment lacked a background WorkManager worker, and cold-start preference nullability triggered immediate work cancellation.
4. *Preservation of Core Invariants*: Existing work names (`automated_strava_segments_sync_work`, `automated_strava_routes_sync_work`), `ExistingPeriodicWorkPolicy.UPDATE`, 7-day TTL pruning, and manual update cards in `StravaSettingsDialog` remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete authenticated with Strava with automated sync enabled,
• *When* `TrainingApplication.onCreate()` executes during app startup,
• *Then* `StravaSegmentsSyncWorker`, `StravaRoutesSyncWorker`, and `StravaEquipmentSyncWorker` are enqueued with `ExistingPeriodicWorkPolicy.UPDATE` and are NOT cancelled.
• *Given* an athlete who configures a 3-day sync interval in Strava settings,
• *When* `schedule(context)` executes for segments, routes, or equipment workers,
• *Then* the periodic work request is enqueued with an interval duration of 3 days.
• *Given* an athlete who disables automated sync for a given feature,
• *When* `schedule(context)` executes,
• *Then* the corresponding unique periodic work request is cancelled in WorkManager.
• *Given* an athlete with connected Strava and valid equipment,
• *When* `StravaEquipmentSyncWorker.doWork()` executes,
• *Then* equipment sync executes, updates SQLite, updates `lastUpdateTimeOfStravaEquipment`, sends `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED` broadcast, and returns `Result.success()`.
• *Given* all 9 supported application locales,
• *When* running translation parity tests,
• *Then* all new equipment sync strings are present and non-empty.

**Invariants**: Network constraints, battery constraints, exponential backoff, 7-day TTL cache pruning, and 100% test pass rate MUST NOT be broken.

### REQ-EXP-020: Decommission Obsolete Runkeeper and TrainingPeaks Legacy Exporters, Activities, and Unused Apache HttpClient Dependencies [`TST-EXP-017`]
- **Tests**: `app/build.gradle`, `AndroidManifest.xml`, `MyHelper.java`, `ExportManager.java`, `TrainingApplication.java`, `BaseFileWriter.java`
- **Implementation**: Decommission obsolete Runkeeper and TrainingPeaks classes, manifest entries, dead elevation helpers, and unused Apache HttpClient dependencies.
- **Rationale**: The system SHALL decommission obsolete, unmaintained Runkeeper and TrainingPeaks integrations, delete dead token extraction activities, uploaders, and file writers, remove unused manifest elements and legacy Apache HttpClient dependencies, and eliminate dead elevation helpers in `MyHelper.java`, enforcing Rule 18 (Dependency Cleanliness), Rule 23 (Dead Code Removal), and Rule 36 (Single Source of Truth & DRY) (ATT-3551):
1. *Decommission Dead Runkeeper & TrainingPeaks Classes & Activities*:
• The system SHALL permanently excise the following obsolete source files: `RunkeeperGetAccessTokenActivity.java`, `RunkeeperTestActivity.java`, `TrainingpeaksGetAccessTokenActivity.java`, `BaseGetAccessTokenActivity.java`, `RunkeeperUploader.java`, `TrainingPeaksUploader.java`, and `RunkeeperFileWriter.java`.
• The system SHALL remove `<activity>` declarations for `RunkeeperGetAccessTokenActivity` and `TrainingpeaksGetAccessTokenActivity` from `app/src/main/AndroidManifest.xml`.
2. *Decommission Unused Apache HttpClient Dependencies & Legacy Platform Library*:
• The system SHALL remove `<uses-library android:name="org.apache.http.legacy" android:required="false" />` from `app/src/main/AndroidManifest.xml`.
• The system SHALL remove all 5 `org.apache.httpcomponents:*` dependencies (`fluent-hc`, `httpclient`, `httpclient-cache`, `httpcore`, `httpmime`) from `app/build.gradle`.
• The system SHALL remove `-dontwarn org.apache.http.**` from `app/proguard-rules.pro`.
3. *Purge Dead Elevation Helpers and Imports in `MyHelper.java`*:
• The system SHALL remove dead, uncalled methods `getAltitudeFromUSGS(double, double)` and `getElevationFromGoogleMaps(double, double)` from `MyHelper.java`.
• The system SHALL remove all unused `org.apache.http.*` imports from `MyHelper.java`.
4. *Cleanse Lingering References in Living Production Classes*:
• `ExportManager.java`: The system SHALL remove dead commented-out cases for `RUNKEEPER` and `TRAINING_PEAKS` in `getExporter(ExportInfo)`.
• `BaseFileWriter.java`: The system SHALL remove dead commented-out selective setting blocks for `RUNKEEPER` and `TRAINING_PEAKS`.
• `FileFormat.java`: The system SHALL remove dead commented-out enum entries and arrays for `RUNKEEPER` and `TRAINING_PEAKS`.
• `ExportType.java`: The system SHALL remove dead commented-out entries for `FileFormat.RUNKEEPER` and `FileFormat.TRAINING_PEAKS`.
• `TrainingApplication.java`: The system SHALL remove dead preference constants (`PREFERENCE_SCREEN_RUNKEEPER`, `SP_UPLOAD_TO_RUNKEEPER`, `SP_RUNKEEPER_TOKEN`, `SP_UPLOAD_TO_TRAINING_PEAKS`, `SP_TRAINING_PEAKS_ACCESS_TOKEN`, `SP_TRAINING_PEAKS_REFRESH_TOKEN`, `SP_UPLOAD_RUNKEEPER_*`, `SP_UPLOAD_TRAINING_PEAKS_*`), dead preference accessors/mutators (lines 1093–1186), and dead commented switch cases in `exportFileFormat()` and `uploadToCommunity()`.
5. *Preservation of System Invariants*:
• *Database Schema Invariant*: Column `SportType.RUNKEEPER_NAME = "runkeeperName"` in `SportTypeDatabaseManager.java` is part of the persistent SQLite schema and SHALL NOT be dropped or modified in this ticket.
• *Active Exporter Invariant*: All supported exporters (Strava, Dropbox, Google Drive, TCX, GPX, FIT, CSV, GC) MUST retain 100% operational integrity.
• *Zero Apache Invariant*: Production source code under `app/src/main/` SHALL contain strictly 0 occurrences of `org.apache.http`.
• *Clean-Room Pass Invariant*: The full unit test suite (`./gradlew testDebugUnitTest`) MUST pass with 100% success.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-EXP-020`) targeting dead legacy code excision and dependency pruning across `app/build.gradle`, `AndroidManifest.xml`, `MyHelper.java`, `ExportManager.java`, and `TrainingApplication.java`.
2. *Historical Origin & Commit Trace*: Early git history (2011–2015). Runkeeper and TrainingPeaks were early upload integrations using Apache HttpClient. In Android 6.0 (API 23), Google removed Apache HttpClient from the platform, prompting the addition of `org.apache.httpcomponents:*` dependencies and `org.apache.http.legacy`. As `aTrainingTracker` adopted OkHttp3 and Coroutines for Strava and modern services, the legacy uploaders were commented out rather than deleted.
3. *Root Reason for Existing Formulation*: The code and dependencies were left dormant to avoid build breakage when uploaders were deactivated.
4. *Preservation of Core Invariants*: Retaining SQLite database schema column `SportType.RUNKEEPER_NAME` guarantees zero migration breakage for existing databases. All active exporters remain completely intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the production source files under `app/src/main/java/`,
• *When* scanned for `org.apache.http`,
• *Then* strictly 0 occurrences or imports are found.
• *Given* `app/build.gradle` and `app/src/main/AndroidManifest.xml`,
• *When* evaluated for legacy HTTP client entries,
• *Then* `org.apache.httpcomponents` dependencies and `<uses-library android:name="org.apache.http.legacy"` are completely absent.
• *Given* the file system under `app/src/main/java/`,
• *When* checking the locations of `RunkeeperGetAccessTokenActivity.java`, `RunkeeperTestActivity.java`, `TrainingpeaksGetAccessTokenActivity.java`, `BaseGetAccessTokenActivity.java`, `RunkeeperUploader.java`, `TrainingPeaksUploader.java`, and `RunkeeperFileWriter.java`,
• *Then* none of these files exist on disk.
• *Given* `app/src/main/AndroidManifest.xml`,
• *When* inspected for `<activity>` declarations,
• *Then* neither `RunkeeperGetAccessTokenActivity` nor `TrainingpeaksGetAccessTokenActivity` is present.
• *Given* `SportTypeDatabaseManager.java`,
• *When* inspecting the SQLite table schema definition,
• *Then* `SportType.RUNKEEPER_NAME` remains present and functional to guarantee schema backward compatibility.
• *Given* the full project test suite,
• *When* executing `./gradlew testDebugUnitTest`,
• *Then* all tests pass with 0 errors.

**Invariants**: SQLite schema compatibility (`SportType.RUNKEEPER_NAME`), active exporter functionality, and 100% test pass rate MUST NOT be broken.

### REQ-EXP-021: Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection [Harden Strava upload retry classification on 429/5xx, polling fault tolerance, duplicate discovery matching guard, and SQL parameterized queries.]
- **Tests**: code == 201` check that inadvertently classified rate limits and server outages as permanent fatal errors, and polling aborted on a single glitch.
4. *Preservation of Core Invariants*: True duplicates on Strava continue to bypass TCX upload and preserve Strava cloud metadata, while transient failures are retried and false-starts are rejected.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an upload request to Strava that returns HTTP 429 Too Many Requests,
• *When* `uploadToStrava` executes,
• *Then* `exportResult.retry()` SHALL evaluate to `true`, and rate limit headers SHALL be logged.
• *Given* an upload request that returns HTTP 503 Service Unavailable,
• *When* `uploadToStrava` executes,
• *Then* `exportResult.retry()` SHALL evaluate to `true`.
• *Given* an upload request that returns HTTP 400 Bad Request,
• *When* `uploadToStrava` executes,
• *Then* `exportResult.retry()` SHALL evaluate to `false`.
• *Given* upload polling where attempt 1 returns null due to a transient glitch but attempt 2 returns `status = ready`,
• *When* polling executes,
• *Then* the uploader SHALL NOT abort on attempt 1, and SHALL complete successfully on attempt 2.
• *Given* a 2-hour bike ride and a 2-minute false-start activity recorded 3 minutes prior on Strava,
• *When* `findExistingStravaActivityForWorkout` executes,
• *Then* the 2-minute activity SHALL NOT match, and the 2-hour workout SHALL proceed to TCX file upload.
• *Given* a 1-hour bike ride and a 1-hour run recorded at the same start time on Strava,
• *When* `findExistingStravaActivityForWorkout` executes for the bike ride,
• *Then* the run SHALL NOT match due to sport type mismatch.
• *Given* an existing Strava activity with an unparseable `start_date` string,
• *When* `findExistingStravaActivity` evaluates candidates,
• *Then* it SHALL skip the candidate and NOT treat it as a 0-second difference.
• *Given* batch query `getStravaActivityDataForWorkouts`,
• *When* executed,
• *Then* selection query SHALL use parameterized `?` placeholders.

**Invariants**: Zero regression in existing upload, duplicate reconciliation, and naming tests, and 100% test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL reliably classify HTTP response codes and transient network errors during Strava workout uploads, maintain resilient polling fault tolerance, harden pre-upload duplicate matching against false starts and cross-sport collisions, and enforce secure parameterized SQL queries (ATT-4012):
1. *HTTP Error Classification & Transient Retry Handling (`StravaUploader.kt`)*:
• When uploading a workout file to Strava (`POST /api/v3/uploads`), the system SHALL classify HTTP `429 Too Many Requests` and HTTP `5xx` server errors (`500`, `502`, `503`, `504`) as transient, retryable failures returning `ExportResult(false, true, ...)` to allow WorkManager backoff retry.
• For HTTP `429`, the system SHALL extract and log `X-RateLimit-Limit` and `X-RateLimit-Usage` headers if present in the response.
• Client errors (`400`, `403`, `404`, `422`) SHALL return `ExportResult(false, false, ...)`.
• If HTTP `401 Unauthorized` is encountered, the system SHALL attempt to re-verify authentication via `StravaHelper.getRefreshedAccessToken()`; if token refresh is unavailable or fails, it SHALL return `ExportResult(false, false, ...)` without looping.
2. *Polling Fault Tolerance (`StravaUploader.kt`)*:
• During upload status polling (`getStravaUploadStatus`), a single null response or transient network glitch SHALL NOT immediately abort the polling loop or permanently fail the upload. The system SHALL log a warning and continue polling up to `MAX_REQUESTS`.
• If polling exhausts `MAX_REQUESTS` without reaching a terminal status (`ready`, `error`, `deleted`), the system SHALL return `ExportResult(false, true, "Timeout waiting for Strava processing")`.
3. *Pre-Upload Duplicate Discovery Hardening (`StravaUploader.kt`)*:
• In `findExistingStravaActivity`, candidate activities whose `start_date` cannot be parsed into a positive epoch timestamp (`actEpoch <= 0L`) SHALL be skipped immediately and MUST NOT evaluate to `diff = 0L`.
• *Sport Type Compatibility Guard*: Candidate Strava activities SHALL only match if their activity type/sport_type is compatible with the workout's `BSportType` (e.g. `BSportType.BIKE` matches Strava `Ride`, `MountainBikeRide`, `GravelRide`, `VirtualRide`, `EBikeRide`; `BSportType.RUN` matches `Run`, `TrailRun`, `VirtualRun`), or if the local sport type is unknown.
• *Duration Tolerance Guard*: A candidate Strava activity SHALL NOT match if the workout has a recorded duration (`TIME_TOTAL > 0`) and the candidate's `elapsed_time` differs by more than $\pm 25\%$ (or $\pm 900\text{ s}$), preventing short false-start recordings from swallowing full workouts.
4. *Safe Response Body Handling (`StravaUploader.kt`)*:
• The system SHALL replace all unsafe `response.body!!.string()` calls with null-safe reads (`response.body?.string()?.takeIf { it.isNotBlank() }`).
5. *SQL Security & Concurrency Hardening (`StravaUploadDbHelper.java`)*:
• `getStravaActivityDataForWorkouts` SHALL use parameterized SQL bind arguments (`IN (?, ?, ...)`) rather than string concatenation.
• `updateOrInsert` SHALL execute atomic upsert semantics using `insertWithOnConflict` with `CONFLICT_REPLACE`.
6. *Preservation of System Invariants*:
• Smart asymmetric naming and duplicate reconciliation rules from `REQ-EXP-008` and `REQ-EXP-009` SHALL remain 100% preserved.
• 100% clean-room test suite pass rate (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-EXP-021`), refining pre-upload duplicate discovery in `REQ-EXP-008` (*Smart Asymmetric Workout Naming Strategy & Pre-Upload Duplicate Discovery for Strava*) and hardening error classification in `StravaUploader.kt`.
2. *Historical Origin & Commit Trace*: Independent whole-app architecture review (2026-10-18, commit `0234bfc0`, ticket `ATT-4012`).
3. *Root Reason for Existing Formulation*: `REQ-EXP-008` introduced duplicate discovery using only start timestamps within $\pm 300\text{ s}$ to catch re-uploads. Error handling in `StravaUploader` used a binary `code == 200

### REQ-EXP-022: GPX/TCX Export XML Well-Formedness, Garmin Schema Compliance, Downloads MIME Type & Privacy Protection [`TST-EXP-019`]
- **Tests**: `GPXFileWriter.java`, `TCXFileWriter.java`, `BaseExporter.java`, `BaseFileWriter.java`, `FileFormat.java`
- **Implementation**: GPX/TCX well-formedness, Garmin TCX v2 schema compliance, UTC thread-safe formatting, Downloads MIME types & privacy protection.
- **Rationale**: The system SHALL guarantee well-formed and schema-compliant XML exports for GPX and TCX formats across all workout configurations, ensure thread-safe UTC timestamp formatting, prevent public storage privacy leaks of internal upload files, and provide correct per-format MIME types and null-safe MediaStore handling (ATT-4013):
1. *GPX Segment State Machine & XML Well-Formedness (`GPXFileWriter.java`)*:
• The system SHALL maintain an explicit boolean state flag `isSegmentOpen` (initially `false`).
• Upon encountering a new lap during sample iteration, if `isSegmentOpen` is `true`, the system SHALL emit the closing `</trkseg>` tag before opening a new track segment `<trkseg>`. If `isSegmentOpen` is `false`, the system SHALL emit `<trkseg>` directly without emitting a preceding closing tag.
• Setting `isSegmentOpen = true` SHALL strictly accompany the emission of `<trkseg>`.
• At the conclusion of cursor processing, the system SHALL emit the trailing `</trkseg>` if and only if `isSegmentOpen` is `true`. On zero-sample or empty workouts, zero closing `</trkseg>` tags SHALL be emitted.
• Output GPX files SHALL be 100% well-formed XML under all conditions, including cropped initial laps (`lap > 0`) and empty workouts.
2. *Garmin TCX v2 Schema Compliance (`TCXFileWriter.java`)*:
• In accordance with Garmin's official `TrainingCenterDatabasev2.xsd` schema, `<Creator xsi:type="Device_t">` SHALL be placed strictly inside `<Activity>`, positioned after `<Notes>` (if present) and before `<Extensions>`. `<Creator>` MUST NOT be rendered as a child of `<TrainingCenterDatabase>`.
• When `<Creator xsi:type="Device_t">` is serialized, it SHALL populate valid required schema child elements: `<Name>` (`aTrainingTracker`), `<UnitId>` (`0`), `<ProductID>` (`1`), and `<Version>` (`<VersionMajor>`, `<VersionMinor>`, `<BuildMajor>`, `<BuildMinor>` derived from `BuildConfig.VERSION_NAME` / `VERSION_CODE`).
• When `<Author xsi:type="Application_t">` is serialized under `<TrainingCenterDatabase>`, it SHALL populate valid required schema child elements: `<Name>` (`aTrainingTracker`), `<Build>` containing `<Version>`, `<LangID>` (`en`), and `<PartNumber>` (`000-00000-00`).
3. *Thread Safety & UTC Timezone (`GPXFileWriter.java`, `TCXFileWriter.java`)*:
• Static, mutable `SimpleDateFormat` instances (`msdfFromDb`, `msdfToXML`) SHALL be eliminated and replaced with immutable, thread-safe `java.time.format.DateTimeFormatter` instances configured with explicit UTC timezone (`ZoneOffset.UTC`), preventing concurrent corruption during parallel background exports and eliminating local DST spring-forward timestamp distortions.
4. *Public Downloads Copying, MIME Types & Privacy Protection (`BaseExporter.java`, `BaseFileWriter.java`)*:
• *Preservation of User File Exports*: The system SHALL continue to copy exported workout files to the public `Downloads/` directory on Android 10+ (API 29+) for all user-initiated explicit file exports (`ExportType.FILE`: `CSV`, `TCX`, `GPX`, `FIT`, `GC`).
• *Internal Upload Privacy Protection*: The system SHALL NOT copy internal temporary files created solely for community uploads (`exportInfo.getFileFormat() == FileFormat.STRAVA` or `exportInfo.getExportType() == ExportType.COMMUNITY`) to the shared public `Downloads/` folder.
• *Per-Format MIME Types*: In `FileFormat.java`, the system SHALL define `getMimeType()` returning the standard MIME type: `GPX` -> `application/gpx+xml`, `TCX` / `STRAVA` -> `application/vnd.garmin.tcx+xml`, `FIT` -> `application/vnd.ant.fit`, `CSV` -> `text/csv`, `GC` -> `application/json`. `BaseExporter.copyFileToDownloads` SHALL query `exportInfo.getFileFormat().getMimeType()` rather than hardcoding `application/gpx+xml`.
• *MediaStore Insertion Null Guard*: In `copyFileToDownloads`, the system SHALL verify that `targetUri != null` before calling `resolver.openOutputStream(targetUri)`. If `targetUri` is null, it SHALL log a warning and return gracefully without throwing `NullPointerException`.
5. *Preservation of System Invariants*:
• Custom extension schemas (`att:ActivityExtension`) and naming conventions SHALL remain intact.
• 100% clean-room test suite pass rate (`./gradlew testDebugUnitTest`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-EXP-022`), amending `REQ-EXP-001` (*Export Interoperability*) and refining `BaseExporter.java`, `BaseFileWriter.java`, `GPXFileWriter.java`, and `TCXFileWriter.java`.
2. *Historical Origin & Commit Trace*: Independent whole-app architecture review (2026-10-18, commit `0234bfc0`, ticket `ATT-4013`).
3. *Root Reason for Existing Formulation*: GPX track segment closing was originally implemented via a simple lap inequality check (`lap != BANALService.INIT_LAP_NR`) assuming workouts always start at lap 0. `copyFileToDownloads` was introduced in Android 10 scoped storage migration with a placeholder GPX MIME type and without format or upload-type distinctions. TCX creator block had been placed at document root during initial development.
4. *Preservation of Core Invariants*: Explicit file exports (`ExportType.FILE`) continue to be placed into `Downloads/` for athlete access, while community upload files stay private in app storage; XML tags and extensions remain compatible with third-party fitness platforms.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout whose initial lap 0 has been cropped so the first recorded sample has lap 1,
• *When* exported via `GPXFileWriter`,
• *Then* the resulting GPX file SHALL start the track with `<trkseg>` and SHALL NOT emit a preceding `</trkseg>` tag.
• *Given* a workout with zero track samples,
• *When* exported via `GPXFileWriter`,
• *Then* the output SHALL NOT contain orphan `</trkseg>` tags and SHALL parse as well-formed XML.
• *Given* an exported TCX file,
• *When* validated against Garmin's official `TrainingCenterDatabasev2.xsd`,
• *Then* `<Creator>` SHALL be a child of `<Activity>`, `<Author>` SHALL be a child of `<TrainingCenterDatabase>`, and the document SHALL validate with zero XSD errors.
• *Given* an export of a CSV, FIT, or TCX file to Downloads,
• *When* `copyFileToDownloads` executes,
• *Then* the MediaStore `MIME_TYPE` column SHALL be set to `text/csv`, `application/vnd.ant.fit`, or `application/vnd.garmin.tcx+xml` respectively.
• *Given* a workout export for Strava upload (`ExportType.COMMUNITY` / `FileFormat.STRAVA`),
• *When* export finishes,
• *Then* no copy SHALL be created in the public `Downloads/` directory.
• *Given* a MediaStore insert failure returning null `targetUri`,
• *When* `copyFileToDownloads` executes,
• *Then* no `NullPointerException` SHALL be thrown.

**Invariants**: Explicit file exports continue to appear in Downloads/; custom extension schemas preserved; 100% test pass rate MUST NOT be broken.

### REQ-EXP-005: Exponential backoff for Strava uploads [Verified]
- **Tests**: `TST-STR-001`
- **Implementation**: `StravaUploader.kt`
- **Rationale**: Robust sync even with API issues.
- **Description**: Exponential backoff for Strava uploads.

### REQ-EXT-001: "Powered by Strava" branding on all Strava data [Verified]
- **Tests**: `TST-REG-002`
- **Implementation**: `PoweredByStrava.kt`
- **Rationale**: Comply with mandatory API guidelines.
- **Description**: "Powered by Strava" branding on all Strava data.

### REQ-EXT-002: Official Strava Assets [Verified]
- **Tests**: `TST-REG-002`
- **Implementation**: `StravaUploadFragment.kt`
- **Rationale**: The system SHALL use official Strava \"Connect\" authentication assets to comply with partner brand requirements.

### REQ-EXT-003: Display logo contextually within lists/headers [Verified]
- **Tests**: `TST-REG-002`
- **Implementation**: `SegmentList.kt`
- **Rationale**: Maintain compliance without UI clutter.
- **Description**: Display logo contextually within lists/headers.

### REQ-EXT-004: Integrated Strava Authorization [Verified]
- **Tests**: `TST-EXT-001`
- **Implementation**: `StravaHelper.kt`, `StravaUploadFragment.kt`
- **Rationale**: The system SHALL use Chrome Custom Tabs for Strava authorization to provide a seamless user experience within the application context.
- **Description**: Integrated Strava Authorization.

### REQ-EXT-005: Reactive OAuth Feedback [Verified]
- **Tests**: `TST-EXT-002`
- **Implementation**: `StravaOAuthCallbackActivity.kt`, `StravaAuthViewModel.kt`
- **Rationale**: The system SHALL provide immediate visual feedback (loading/error) during the Strava token exchange process using modern UI components.
- **Description**: Reactive OAuth Feedback.

### REQ-EXT-006: Strava Profile Scope & Equipment Synchronization Integrity [Verified]
- **Tests**: `TST-EXT-003`
- **Implementation**: `StravaHelper.kt`, `StravaEquipmentSynchronizeThread.java`, `EquipmentFragment.kt`
- **Rationale**: The system SHALL request the `profile:read_all` OAuth permission scope during Strava authentication (alongside `read`, `read_all`, `activity:read_all`, and `activity:write`) to ensure athlete gear details (bikes and shoes) are returned by Strava API `/api/v3/athlete`. `StravaEquipmentSynchronizeThread` SHALL validate that an active access token is present before executing requests, accurately extract both `bikes` and `shoes` arrays into `EquipmentDbHelper`, avoid creating duplicate local entries for existing unlinked equipment with identical names, and report a descriptive warning if gear arrays are absent due to missing permissions. Furthermore, `EquipmentFragment` SHALL listen for `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED` to automatically refresh its equipment view upon synchronization completion.

### REQ-EXT-007: Automated & On-Demand Strava Route Synchronization [Verified]
- **Tests**: `TST-EXT-004`
- **Implementation**: `StravaUploadFragment.kt`, `RoutesRepository.kt`, `TrainingApplication.java`, `prefs_strava.xml`
- **Rationale**: The system SHALL automatically initiate synchronization of athlete routes from Strava API (`/api/v3/athletes/{id}/routes`) into `RoutesDatabaseManager` when Strava authorization succeeds (`StravaAuthState.Success`), alongside equipment and segments. The system SHALL provide an on-demand synchronization control `updateStravaRoutes` in Strava settings (`prefs_strava.xml`), displaying the last route synchronization timestamp and triggering route synchronization when tapped. The route synchronization process SHALL extract high-resolution route streams (`/api/v3/routes/{id}/streams`), decode polyline fallback coordinates if streams are unavailable, persist routes with `RouteSource.STRAVA` and bounds in `RoutesDatabaseManager`, update the last route synchronization timestamp, and seed imported routes into `WorkoutClusterEngine` for recurring route pattern discovery. Existing non-Strava routes, user-selected route states, and database schemas MUST remain preserved.

### REQ-EXT-008: Automated Community Upload for Imported Workouts [Verified]
- **Tests**: `TST-EXT-005`
- **Implementation**: `LegacyImportEngine.kt`, `ExportManager.java`, `BackupRestoreViewModel.kt`
- **Rationale**: Upon successful completion of a TCX workout import and statistical recalculation (`LegacyImportEngine.importFromTcx`), the system SHALL evaluate whether community upload to Strava is enabled (`TrainingApplication.uploadToCommunity(FileFormat.STRAVA)`). If enabled, the system SHALL schedule an asynchronous community upload task for the imported workout using `ExportManager`, persisting tracking export status to `ExportStatusDatabaseManager` and queuing the background upload via `WorkManager`. The imported workout record in `WorkoutSummaries` SHALL have `UPLOAD_TO_STRAVA` initialized to reflect the upload request (`1` if requested, or `-1` default). **Invariants**: Import file parsing, local database insertion, and spatial recalculation MUST NOT block on network execution. If network upload fails, the local workout record and stats MUST remain completely intact and available.

### REQ-EXT-009: Strava Data Deletion on Token Invalidation, Deauthorization & Reauthorization Integrity [`TST-EXT-006`]
- **Tests**: `StravaAuthManager.kt`, `StravaSettingsDialog.kt`, `StravaDeauthorizationThread.java`, `StravaDataPurgeManager.kt`, `StravaHelper.kt`, `TrainingApplication.java`, `StravaUploadDbHelper.java`, `SegmentsDatabaseManager.java`, `SegmentsRepository.kt`, `RoutesDatabaseManager.kt`, `RoutesRepository.kt`, `EquipmentDbHelper.java`
- **Implementation**: Comply with Strava API Policy Section 7.4 mandating permanent deletion of Strava data upon token revocation, while protecting native athlete workout recordings and hardware sensor links.
- **Rationale**: The system SHALL ensure strict compliance with the Strava API Policy (Section 7.4 Deletion Obligation & Section 6.2 Cache and Retention) regarding authorization termination, while protecting native athlete data and hardware sensor configurations:
1. *Mandatory Purge on Deauthorization & Revocation*: Upon explicit user disconnection in `StravaSettingsDialog` or automated server-side revocation detection (unrecoverable token refresh failure with `invalid_grant` / HTTP 400 in `StravaHelper.getRefreshedAccessToken`), the system SHALL execute a comprehensive data purge:
• *Credentials & State*: Remove all Strava tokens (`SP_STRAVA_TOKEN`, `SP_STRAVA_REFRESH_TOKEN`, `SP_STRAVA_TOKEN_EXPIRES_AT`, `SP_STRAVA_ATHLETE_ID`) and reset Strava upload toggles (`SP_UPLOAD_TO_STRAVA`) and demo mode to `false` in `SharedPreferences`.
• *Activity Details*: Purge all Strava activity JSON data, segment efforts, best efforts, PR/KOM rankings, and Strava activity IDs (`STRAVA_ACTIVITY_DATA` and `STRAVA_ID`) from `StravaUploadDbHelper` (`StravaUpload.db`).
• *Starred Segments*: Delete all Strava starred segments from `SegmentsDatabaseManager` (`Segments.db`) and clear in-memory cache in `SegmentsRepository`.
• *Strava Routes*: Delete all routes originating from Strava (`ORIGIN = 'STRAVA'` / Strava route IDs) from `RoutesDatabaseManager` (`Routes.db`) and refresh in-memory cache in `RoutesRepository`.
• *Equipment Unlinking*: In `EquipmentDbHelper` (`Equipment.db`), clear `StravaId = NULL` and `StravaName = NULL` across all equipment items, preserving local equipment entities, hardware sensor pairings (ANT+/BLE in `Links` table), and sport type links.
• *Worker Cancellation*: Cancel automated periodic synchronization workers (`StravaSegmentsSyncWorker` and `StravaRoutesSyncWorker`) via Android `WorkManager`.
• *UI State Notification*: Update the UI state across the application to reflect the disconnected state (`isConnected = false`).
2. *Remote Deauthorization*: When initiated by the user, the deauthorization request to Strava (`/oauth/deauthorize`) SHALL be dispatched using modern `OkHttpClient` before local credentials are wiped. If network fails, local data purging MUST still proceed.
3. *Automated Revocation Detection*: When `StravaHelper.getRefreshedAccessToken()` encounters an unrecoverable OAuth error (`invalid_grant` / HTTP 400), it SHALL automatically trigger the local data purge.
4. *Reauthorization Integrity & Deduplication*: Upon subsequent reauthorization (`StravaAuthState.Success`), `StravaEquipmentSynchronizeThread` SHALL match unlinked local equipment by `Name` where `StravaId IS NULL`, restoring `StravaId` and `StravaName` without creating duplicate equipment rows. Starred segments and routes SHALL be freshly synchronized without creating duplicate route or segment rows across concurrent sync tasks or repeated reconnects: `RoutesRepository.syncRoutesFromStrava` and `SegmentsRepository.syncStarredSegments` SHALL serialize concurrent sync executions with a `Mutex`, `RoutesDatabaseManager.insertRoute` and `SegmentsDatabaseManager.addOrUpdateSegment` SHALL idempotently update existing records and purge legacy duplicate rows when matching external/Strava IDs, `SegmentsDatabaseManager.insertSegmentStreams` SHALL delete existing stream records before re-inserting points, `SegmentsDatabaseManager.getAllSegmentSummaries` SHALL filter duplicates in-memory, `SegmentsRepository._allSegmentsWithPath` SHALL update existing segments in-place, and `StravaSettingsDialog` SHALL execute segment synchronization sequentially rather than dispatching concurrent `BIKE` and `RUN` sync jobs.
5. *Interactive Disconnect Confirmation*: When an athlete initiates disconnection in `StravaSettingsDialog`, the system SHALL NOT immediately purge data or deauthorize. It SHALL present a modal confirmation dialog clearly detailing the consequences (deletion of Strava routes, starred segments and times, unlinking of equipment from Strava, and removal of Strava upload status for past workouts) and reassuring the user that native recorded workouts remain untouched. Disconnection and data purging SHALL ONLY proceed upon explicit confirmation, and all dialog strings SHALL be localized across all 9 supported languages.
6. *Invariants*: Native workout sessions, GPS trackpoint streams in `WorkoutSamplesDatabaseManager`, calculated metrics and durations in `WorkoutSummariesDatabaseManager`, and interval records in `LapsDatabaseManager` recorded natively by aTrainingTracker MUST NOT be deleted. Non-Strava local routes, local equipment hardware bindings, and sport types MUST remain completely intact.

### REQ-EXT-010: Strava Route & Segment 7-Day TTL Cache Retention, Orphan Pruning & Route Preservation [`TST-EXT-007`]
- **Tests**: `RoutesDatabaseManager.kt`, `RoutesRepository.kt`, `SegmentsDatabaseManager.java`, `SegmentsRepository.kt`, `StravaRoutesSyncWorker.kt`, `StravaSegmentsSyncWorker.kt`
- **Implementation**: Comply with Strava API Agreement Section 6.2 mandating a 7-day maximum cache retention and prompt orphan deletion, while safeguarding athlete-owned routes, native workout records, and sensor configurations.
- **Rationale**: The system SHALL enforce strict compliance with Section 6.2 ("Cache and Retention") of the Strava API Agreement and Developer Terms across all Strava-originated routes and starred segments:
1. *7-Day Retention Limit & Persistent Timestamping*: The system SHALL NOT retain cached Strava routes (`RouteSource.STRAVA` in `Routes.db`) or starred segments (in `Segments.db`) for longer than 7 days (604,800,000 ms) without fresh synchronization. Both `Routes.db` (`TABLE_ROUTES`) and `Segments.db` (`TABLE_STARRED_SEGMENTS`) SHALL maintain a `synced_at` timestamp column (`INTEGER DEFAULT 0`, Schema v8). Whenever a route or segment is synchronized or updated from the Strava API, its `synced_at` timestamp SHALL be updated to current epoch milliseconds (`System.currentTimeMillis()`).
2. *Automated TTL Expiration & Cascade Eviction*: The system SHALL automatically evict records where `currentTimeMillis() - synced_at > 7 days`. TTL eviction SHALL execute upon repository instantiation (`init`), during background sync worker execution (`StravaRoutesSyncWorker`, `StravaSegmentsSyncWorker`), and on demand. For expired segments, the eviction SHALL cascade-delete associated polyline stream coordinates from `SegmentStreamsTable`.
3. *Orphan & Tombstone Pruning*: During remote synchronization, the system SHALL compare local Strava records against the remote API response. Any local route with `source == RouteSource.STRAVA` whose external Strava ID is absent from the active remote route set SHALL be promptly pruned from `Routes.db`. Similarly, any local starred segment whose Strava ID is absent from the active remote starred segments set SHALL be pruned from `Segments.db` alongside its stream coordinates.
4. *Athlete Route Preservation (Local GPX Duplication)*: To allow athletes to retain desired navigation tracks permanently offline without violating Strava caching limits, the system SHALL provide a "Save as Local Route" / "Als lokale Route speichern" feature. Duplicating a Strava route SHALL create an independent route record in `Routes.db` with `source = RouteSource.LOCAL_GPX`, clear external Strava identifiers, and copy all spatial coordinate points, bounds, and elevation profiles. Converted local routes SHALL be athlete-owned data and SHALL NOT be subject to Strava TTL eviction or orphan pruning.
5. *System Invariants*: Native recorded workouts (`WorkoutSummaries.db`, `WorkoutSamples.db`, `Laps.db`), athlete-owned routes (`RouteSource.LOCAL_GPX` and `RouteSource.WORKOUT`), recurring route clusters in `WorkoutCluster.db`, and hardware sensor bindings in `Equipment.db` MUST NOT be touched, altered, or deleted during TTL eviction or orphan pruning. Eviction operations MUST execute safely in background transactions without blocking UI threads or failing on offline devices.

### REQ-EXT-011: Resilient Strava Token Refresh Concurrency, Expiry Margin, Precise Revocation Discrimination & Safe Header Construction [Protect local Strava data against accidental deletion during token refresh, synchronize concurrent refresh requests, enforce a 300-second expiry margin, reuse OkHttpClient, and eliminate Bearer null headers.]
- **Tests**: field == "refresh_token") && code == "invalid"`, OR response message contains `"revoked"` (case-insensitive).
• If the error response contains `field == "client_secret"`, `field == "client_id"`, or `resource == "Application"`, or any generic parameter/server error, `StravaHelper` SHALL NOT trigger `StravaDataPurgeManager.purgeAllStravaData()`. It SHALL log an error and return `null` without deleting local data.
2. *Thread-Safe Reentrant Token Refresh & Double-Checked Locking (`REQ-EXT-011.2`)*:
• `StravaHelper` SHALL synchronize concurrent invocations of `getRefreshedAccessToken()` via a reentrant lock or monitor (`refreshLock`).
• Inside the synchronized block, `StravaHelper` SHALL re-verify token validity: if a preceding concurrent thread has already refreshed the token such that `now + 300 < expiresAt`, `getRefreshedAccessToken()` SHALL return `TrainingApplication.getStravaAccessToken()` immediately without sending a duplicate network request.
3. *5-Minute (300-Second) Token Expiry Margin (`REQ-EXT-011.3`)*:
• `StravaHelper` SHALL consider a cached access token valid if and only if `System.currentTimeMillis() / 1000 + 300 < TrainingApplication.getStravaTokenExpiresAt()`.
• If remaining token validity is less than or equal to 300 seconds (5 minutes), `StravaHelper` SHALL execute a preemptive token refresh.
4. *Singleton HTTP Client Reuse (`REQ-EXT-011.4`)*:
• `StravaHelper` SHALL reuse a shared singleton `OkHttpClient` instance configured with 15-second connect and read timeouts, eliminating per-call client allocations and thread pool leaks.
5. *Safe Authorization Header Construction (`REQ-EXT-011.5`)*:
• In `StravaUploader.kt`, methods `updateStravaActivity`, `getStravaJson`, and `getStravaJsonArray` SHALL resolve `val accessToken = StravaHelper.getRefreshedAccessToken()` prior to building HTTP requests.
• If `accessToken.isNullOrBlank()`, the method SHALL log an error/warning and return `null` immediately without creating or dispatching an HTTP request containing `"Authorization: Bearer null"`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-EXT-011`, extending `REQ-EXT-009` under Epic `ATT-597` (*Strava Support*). Targets `StravaHelper.kt` and `StravaUploader.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3996`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). `REQ-EXT-009` was introduced in commit `4270d06f` (ATT-1078) to comply with Strava API Policy Section 7.4.
3. *Root Reason for Existing Formulation*: `REQ-EXT-009` specified automated data purging upon unrecoverable OAuth revocation (`invalid_grant` / HTTP 400). However, the implementation in `StravaHelper.kt` used a simplistic substring check `errorBody.contains("invalid")`, causing non-revocation errors (such as `client_secret: invalid`) and unsynchronized concurrent refresh collisions to wipe all local data.
4. *Preservation of Core Invariants*: Full compliance with Strava API Policy Section 7.4 is strictly preserved when the refresh token is truly revoked or invalid. Native workouts, local GPX routes, and hardware pairings in `Equipment.db` remain 100% protected.

**Acceptance Criteria (Given-When-Then)**:
• *Given* Strava API returns HTTP 400 with `field: client_secret, code: invalid` during token refresh,
• *When* `StravaHelper.getRefreshedAccessToken()` executes,
• *Then* it logs an error, returns `null`, and DOES NOT invoke `StravaDataPurgeManager.purgeAllStravaData()`.
• *Given* Strava API returns HTTP 400 with `field: refresh_token, code: invalid` or `invalid_grant`,
• *When* `StravaHelper.getRefreshedAccessToken()` executes,
• *Then* it invokes `StravaDataPurgeManager.purgeAllStravaData()` to fulfill Strava API Policy Section 7.4.
• *Given* an expired token and multiple concurrent threads requesting `getRefreshedAccessToken()`,
• *When* all threads invoke the method simultaneously,
• *Then* exactly one HTTP POST request is sent to Strava, and all threads receive the valid refreshed access token.
• *Given* a token whose `expires_at` is 200 seconds in the future,
• *When* `getRefreshedAccessToken()` is called,
• *Then* it triggers a refresh request because `now + 300 >= expires_at`.
• *Given* a failed token refresh (`getRefreshedAccessToken() == null`),
• *When* `StravaUploader` methods execute,
• *Then* no HTTP request with `Authorization: Bearer null` is dispatched.

**Invariants**: Strava API Policy Section 7.4 compliance preserved on actual user revocation; native athlete recordings and hardware pairings 100% protected; 100% clean-room test pass rate.
- **Rationale**: `StravaHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt`) and `StravaUploader` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`) SHALL guarantee that token refresh operations never trigger accidental local Strava data deletion on application configuration errors, synchronize concurrent token refresh requests with double-checked locking, enforce a 300-second token expiry safety margin, reuse a singleton HTTP client, and prevent dispatching requests with `Authorization: Bearer null` headers (ATT-3996, extending `REQ-EXT-009`):
1. *Precise Revocation Discrimination & Exclusion of Application Errors (`REQ-EXT-011.1`)*:
• `StravaHelper.getRefreshedAccessToken()` SHALL parse OAuth token refresh failure responses (HTTP 400 / HTTP 401) structurally via JSON.
• The system SHALL trigger `StravaDataPurgeManager.purgeAllStravaData()` ONLY IF the response specifically indicates that the `refresh_token` itself is invalid or revoked: standard OAuth error `"invalid_grant"` (without client credential mismatch), OR Strava errors array contains an entry where `(resource == "RefreshToken"

### REQ-EXT-012: Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, Application ID Scheme Callback Scoping, and Legacy Callback Deletion [`TST-EXT-009`, `TST-EXT-012`]
- **Tests**: `AndroidManifest.xml`, `StravaHelper.kt`, `StravaOAuthCallbackActivity.kt`, `StravaOAuthStateManager.kt`, `StravaAuthRepository.kt`
- **Implementation**: Protect Strava OAuth 2.0 flow against Login CSRF via cryptographic state parameter, scope redirect URI scheme by applicationId to eliminate intent disambiguation dialogs between release, debug, and legacy builds, scope token exchange in repository CoroutineScope, and delete dead BaseOAuthCallbackActivity.
- **Rationale**: `StravaHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaHelper.kt`), `StravaOAuthCallbackActivity` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthCallbackActivity.kt`), `StravaOAuthStateManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaOAuthStateManager.kt`), and `StravaAuthRepository` (`app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaAuthRepository.kt`) SHALL protect the Strava OAuth 2.0 authorization code flow against Login Cross-Site Request Forgery (CSRF) via cryptographically random state parameter validation and 10-minute expiration, scope redirect URI scheme by applicationId (`strava.${applicationId}`) to prevent release/debug app chooser collisions and legacy build wildcard path collisions, execute token exchange within a structured repository coroutine scope eliminating `GlobalScope`, safely delete unused `BaseOAuthCallbackActivity.java`, and preserve official branding and permissions (ATT-4022, ATT-4668, ATT-4706, refining `REQ-EXT-004` and `REQ-EXT-005`):
1. *Cryptographic State Generation, Persistence & 10-Minute Expiration (`REQ-EXT-012.1`)*:
• `StravaOAuthStateManager` SHALL generate a 128-bit cryptographically secure random token (`SecureRandom`) encoded as a 32-character hexadecimal string upon initiating authorization in `StravaHelper.requestAccessToken()`.
• `StravaOAuthStateManager` SHALL persist the generated state and current epoch timestamp (`System.currentTimeMillis()`) into `TrainingApplication.getSharedPreferences()` (`SP_STRAVA_OAUTH_STATE`, `SP_STRAVA_OAUTH_STATE_TIMESTAMP`).
• `StravaHelper.getAuthorizationUrl(state: String?)` SHALL append `appendQueryParameter("state", state)` to the authorization request URL.
• A stored state SHALL expire and become invalid after 10 minutes (600,000 ms).
2. *Constant-Time State Validation & Single-Use Consumption (`REQ-EXT-012.2`)*:
• When `StravaOAuthCallbackActivity` receives an incoming redirect URI (`strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io`), it SHALL extract `state = uri.getQueryParameter("state")` and `code = uri.getQueryParameter("code")`.
• `StravaOAuthStateManager.validateAndConsumeState(state: String?): Boolean` SHALL validate that: (a) a non-expired state exists in storage, (b) incoming `state` is non-null, and (c) incoming `state` matches the stored state using constant-time comparison (`MessageDigest.isEqual`) to prevent timing side channels.
• Upon validation (whether successful or failed), `StravaOAuthStateManager` SHALL immediately clear the stored state to enforce single-use semantics and prevent replay attacks.
• If validation fails (missing, mismatched, or expired state, or no flow was in progress), `StravaOAuthCallbackActivity` SHALL reject the callback: log a warning, transition `StravaAuthRepository` to `StravaAuthState.Error`, and `finish()` WITHOUT exchanging `code` for tokens.
3. *Error Handling in Callback (`REQ-EXT-012.3`)*:
• If the redirect URI contains an `error` query parameter (e.g. user cancelled), `StravaOAuthCallbackActivity` SHALL consume and clear the stored state, log the error, transition `StravaAuthRepository` to `StravaAuthState.Error("Authorization cancelled: $error")`, and finish cleanly.
4. *Structured Concurrency in `StravaAuthRepository` (`REQ-EXT-012.4`)*:
• `StravaOAuthCallbackActivity` SHALL NOT invoke `GlobalScope.launch`.
• `StravaAuthRepository` SHALL manage background asynchronous execution using an internal structured coroutine scope: `private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
• `StravaAuthRepository` SHALL expose `fun exchangeCodeForTokenAsync(code: String)` launching within `repositoryScope`.
5. *Deletion of Dead Legacy Callback (`REQ-EXT-012.5`)*:
• `BaseOAuthCallbackActivity.java` SHALL be completely removed from the project, eliminating dead code, deprecated `ProgressDialog`, and legacy `HttpURLConnection`.
6. *Application ID Redirect URI Scheme Scoping & Intent Disambiguation (`REQ-EXT-012.6`)*:
• `StravaHelper.getRedirectUri()` SHALL construct the OAuth redirect URI incorporating the build variant's application ID in the custom URI scheme: `strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io` (`strava.com.atrainingtracker://rainerblind.github.io` for release, `strava.com.atrainingtracker.debug://rainerblind.github.io` for debug).
• `AndroidManifest.xml` intent filter for `StravaOAuthCallbackActivity` SHALL specify `<data android:scheme="strava.${applicationId}" android:host="rainerblind.github.io" />` without `android:path`.
• `StravaOAuthCallbackActivity` SHALL validate that incoming callback URIs match `data.scheme == "strava.${BuildConfig.APPLICATION_ID}" && data.host == "rainerblind.github.io"` (`REDIRECT_URI_PREFIX`), preventing intent collisions and system app chooser prompts between co-installed release, debug, and legacy builds.
7. *Preservation of System Invariants*:
• Callback domain `rainerblind.github.io` (`REQ-EXT-004`), Chrome Custom Tabs launch (`REQ-EXT-004`), OAuth scopes (`read,read_all,profile:read_all,activity:read_all,activity:write`) (`REQ-EXT-006`), token persistence in `TrainingApplication` (`REQ-EXT-006`), reactive `StravaAuthState` flow (`REQ-EXT-005`), and 100% clean-room test pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-EXT-012` (`REQ-EXT-012.2`, `REQ-EXT-012.6`), targeting `AndroidManifest.xml`, `StravaHelper.kt`, `StravaOAuthCallbackActivity.kt`, `StravaOAuthStateManager.kt`, `StravaAuthRepository.kt`, `TrainingApplication.java`, and test suites `StravaHelperTest.kt` / `StravaOAuthCallbackActivityTest.kt` / `StravaOAuthSecurityTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4022`, commit `943dc3b4` (Sprint `2026-42.10`), refining `REQ-EXT-004` and `REQ-EXT-005` under Epic `ATT-597`. Scoping refined to path in ATT-4668 (commit `2ff1f4c74f`, Sprint `2026-43.7`), and further refined to applicationId-scoped custom URI scheme in ATT-4706 (Sprint `2026-43.7`).
3. *Root Reason for Existing Formulation*: `REQ-EXT-012` previously scoped redirect URI by path (`strava://rainerblind.github.io/${applicationId}`). However, co-installed legacy release builds without `android:path` treat omitted path attributes as wildcards (`/*`, `MATCH_DATA_AUTHORITY = 0x308000`) and match with equal priority in Android's `IntentResolver`, triggering `ResolverActivity` ("Öffnen mit"). Scoping the URI scheme itself to `strava.${applicationId}` eliminates collisions because mismatched schemes yield `MATCH_DATA_NONE` (-0x200000).
4. *Preservation of Core Invariants*: Callback domain `rainerblind.github.io`, CSRF state validation (`REQ-EXT-012.1`), repository scope exchange, Custom Tabs integration, and 100% test pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an authorization request initiated from either release or debug variant,
• *When* `StravaHelper.getAuthorizationUrl(state)` constructs the redirect URI,
• *Then* `redirect_uri` parameter SHALL contain `strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io`.
• *Given* co-installed release (`com.atrainingtracker`) and debug (`com.atrainingtracker.debug`) builds on the same device,
• *When* Strava redirects to `strava.com.atrainingtracker.debug://rainerblind.github.io?...`,
• *Then* Android OS SHALL directly route the intent to the debug variant's `StravaOAuthCallbackActivity` without presenting a system app-disambiguation chooser dialog.
• *Given* an incoming redirect callback URI with a mismatched scheme or legacy un-scoped `strava://` scheme,
• *When* `StravaOAuthCallbackActivity` handles the intent,
• *Then* the activity SHALL ignore or reject the callback and finish cleanly without initiating token exchange.
• *Given* an athlete tapping "Connect with Strava",
• *When* the authorization URL is constructed,
• *Then* `state` query parameter is present with a 32-character cryptographic random token, and the token is saved in preferences with an epoch timestamp.
• *Given* a callback without `state` parameter or with a mismatched `state` value,
• *When* `StravaOAuthCallbackActivity` receives the intent,
• *Then* `StravaOAuthStateManager.validateAndConsumeState` returns `false`, no token exchange is initiated, and the activity finishes with an error state.
• *Given* a callback with matching `state` and correct application ID scheme received within 10 minutes,
• *When* `StravaOAuthCallbackActivity` handles the intent,
• *Then* the state is consumed, `exchangeCodeForTokenAsync` is dispatched in repository scope, and tokens are stored upon successful network exchange.

**Invariants**: Custom Tabs launch, callback authority `rainerblind.github.io`, permission scopes, single-variant authentication backwards compatibility, and 100% clean-room test pass rate.

### REQ-DAT-006: Eager Internal Sensor Registration [Verified]
- **Tests**: `TST-BUG-011`
- **Implementation**: `DeviceManager.java`, `DevicesDatabaseManager.java`
- **Rationale**: The `DeviceManager` SHALL ensure that all detectable internal sensors (e.g., Pressure Sensor) are registered in the database *prior* to any pairing checks or device instantiations within its constructor. This guarantees that internal hardware always has a valid database identity and can be correctly auditable in the UI.

### REQ-MIG-018: Sparse Data Import [Verified]
- **Tests**: `TST-MIG-014`
- **Implementation**: `LegacyImportEngine.kt`, `WorkoutSamplesDatabaseManager.java`
- **Rationale**: The TCX import engine SHALL dynamically detect available sensors and only create database columns and summary metrics for telemetry actually present in the source file, preventing the population of zero-filled streams for missing data.

### REQ-DAT-007: Automated Altitude Reference Discovery & Stable Reference Elevation Preservation [`TST-DAT-009`]
- **Tests**: `KnownLocationsDatabaseManager.java`, `AltitudeFromPressureDevice.java`
- **Implementation**: Eliminate weather-induced reference altitude drift by preserving authoritative elevation and tracking location hit counts without running mean altitude corruption.
- **Rationale**: The system SHALL maintain a database of known locations in `StartLocation2Altitude.db` based on workout start points (`ExtremaType.START`) without corrupting them through running averages of barometric pressure:
1. *Zero Altitude Mutation*: In `KnownLocationsDatabaseManager.learnLocation(LatLng pos, Double altitude, ExtremaType type)`, the system SHALL NOT modify the stored `altitude` of an existing location entry within the spatial geofence radius ($200\text{m}$).
2. *Hit Count Frequency Tracking*: When matching an existing known location (including locked locations where `is_locked = 1`), the system SHALL increment `hitCount = existing.hitCount + 1` to track location usage frequency and recency, while strictly preserving stored altitude and coordinates.
3. *Authoritative Reference Preservation*: Reference elevations obtained from authoritative digital elevation models (`INTERNET_DEM`), manual user definitions (`MANUAL_USER`), or verified fallbacks SHALL remain pristine and protected against atmospheric weather pressure variations.
4. *Synchronous Insert Protection*: When encountering an uncalibrated or unknown location (`myLocation == null`), `AltitudeFromPressureDevice` SHALL NOT synchronously insert speculative uncalibrated entries using raw barometric pressure (`mLastRawAltitude`). Instead, it SHALL asynchronously request DEM elevation (`REQ-DAT-014`) and fall back cleanly to GPS altitude.
5. *Atomic Geofence Upsert*: Geofence checking and record creation/updating in `KnownLocationsDatabaseManager` SHALL be encapsulated within an explicit SQLite transaction boundary to eliminate TOCTOU race conditions and prevent duplicate spatial rows under concurrent sensor and asynchronous DEM resolution.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an existing known location entry with reference altitude $520.0\text{m}$ and `hitCount = 3`,
• *When* `learnLocation()` is invoked with coordinates within $200\text{m}$ and an arbitrary uncalibrated pressure altitude (e.g. $480.0\text{m}$),
• *Then* the stored `altitude` SHALL remain strictly $520.0\text{m}$, and `hitCount` SHALL increment to $4$.
• *Given* an existing locked location (`is_locked = 1`, `altitude = 520.0m`, `hitCount = 5`),
• *When* `learnLocation()` is invoked upon starting a workout,
• *Then* `hitCount` SHALL increment to 6, and stored altitude and coordinates SHALL remain strictly unchanged.
• *Given* an unknown location at workout start (`myLocation == null`),
• *When* sensor initialization occurs in `AltitudeFromPressureDevice`,
• *Then* `fetchDemOrFallbackAsync()` SHALL be dispatched without synchronously inserting an uncalibrated `AUTO_LEARNED` record using raw barometric pressure.
• *Given* concurrent execution of `learnLocation()` and asynchronous DEM callback resolution across multiple worker threads,
• *Then* atomic transaction serialization SHALL guarantee that exactly one location row exists in SQLite and no duplicate spatial entries are created.

**Invariants**: Spatial geofence matching radius ($200\text{m}$), schema version (V5), locked location altitude immutability (`REQ-DAT-014`), null-safe correction dispatch (`REQ-CON-013`), and cold-start baseline protection (`REQ-CON-011`) MUST NOT be altered.

### REQ-DAT-008: Atomic Database Upgrades [Verified]
- **Tests**: `TST-UNT-014`
- **Implementation**: `WorkoutSummariesDatabaseManager.java`
- **Rationale**: The system SHALL NOT perform manual transaction management (e.g., `setTransactionSuccessful`) within `onUpgrade` callbacks of `SQLiteOpenHelper`. All schema migrations MUST rely on the automatic transaction wrapper provided by the Android framework to prevent `IllegalStateException` and ensure data integrity.

### REQ-DAT-009: Authoritative Altitude Extrema Reconciliation & Self-Healing [`TST-DAT-003`]
- **Tests**: `WorkoutDataMapper.kt`, `WorkoutSummariesDatabaseManager.java`
- **Implementation**: Eliminate historical multi-sensor pollution and uncalibrated cold-start extrema from legacy workout records, ensuring summary table matches actual physical stream.
- **Rationale**: When loading or mapping workout records (`WorkoutDataMapper.kt`) that contain a non-empty `ALTITUDE_STREAM` (amended by `REQ-DAT-033` in ATT-4003: self-healing writes decoupled from batch read loops), the system SHALL verify that persisted `minAltitude` and `maxAltitude` in `extrema_values` are consistent with the decoded stream points. If stored extrema deviate from the actual stream envelope (specifically, if stored min > stream min [persisted minimum higher than actual lowest stream point] or stored min < stream min - 15.0m [historical pre-ATT-499 GPS startup spike], or stored max < stream max or stored max > stream max + 15.0m), the system SHALL reconcile the authoritative extrema to the decoded stream bounds, update `WorkoutData`, and idempotently persist the corrected extrema to `extrema_values` in SQLite.
**Acceptance Criteria**:
• *Given* a workout recorded with corrupted summary extrema (e.g. Min = -63 m, Max = 60 m, or Min = 368 m with stream min = 360 m),
• *When* the workout is loaded and mapped by `WorkoutDataMapper`,
• *Then* the system SHALL derive the true extrema from the altitude stream, update `extrema_values` in SQLite, and return reconciled values in `WorkoutData`.
**Invariants**: Legitimate workouts where extrema match their stream MUST NOT be re-written; workouts without altitude streams MUST retain existing extrema.

### REQ-DAT-010: Bulk Workout Deletion & Relational Storage Purge []
- **Tests**: ?
- **Rationale**: The system SHALL provide functionality to bulk-delete workout sessions and all associated relational telemetry older than a user-specified retention threshold ($D \ge 0$ days).
1. *Query Selection*: The system SHALL select all workout records from `WorkoutSummaries` where `TIME_START <= datetime('now', '-'

### REQ-DAT-011: Period Database Cache Invalidation & Resynchronization upon Bulk Deletion [`TST-DAT-005`]
- **Tests**: `PeriodsRepository.kt`, `PeriodSummariesDatabaseManager.kt`, `WorkoutRepository.kt`
- **Implementation**: Ensure analytical period summaries, heatmaps, and metrics do not display ghost data or dangling workout references after bulk deletion.
- **Rationale**: Following bulk deletion of workouts older than $D$ days, the system SHALL invalidate and rebuild the persistent period cache (`PeriodSummaries.db`) to ensure that aggregated analytical summaries match the remaining workouts in the database:
1. *Historical Purge*: All fully purged historical periods (Days, Weeks, Months, Years with zero surviving workouts) MUST be completely removed from `PeriodSummaries.db`.
2. *Boundary Recalculation*: Boundary periods spanning across the cutoff retention date MUST be recalculated based strictly on surviving workouts (updating `total_workouts`, `total_duration`, `total_distance`, geographic bounds, and sport breakdowns).
3. *Foreign Reference Integrity*: All foreign references to workout IDs (`longest_workout_id`, bounding anchor IDs `north_workout_id`, `south_workout_id`, `east_workout_id`, `west_workout_id`) MUST point exclusively to active, surviving workout sessions or evaluate to `-1`.
**Acceptance Criteria**:
• *Given* a workout history with periods older and newer than 365 days cached in `PeriodSummaries.db`,
• *When* bulk deletion is completed,
• *Then* `PeriodSummaries.db` SHALL contain zero records for periods whose workouts were 100% purged, and boundary periods SHALL accurately reflect only the surviving workouts.
**Invariants**: Single-workout surgical deletion via `onWorkoutDeleted` MUST remain operational for single-session deletions.

### REQ-DAT-012: TCX Lap Information (Name and Description) Bidirectional Export & Import [`TST-DAT-006`]
- **Tests**: `TCXFileWriter.java`, `LegacyImportEngine.kt`, `LapsDatabaseManager.java`
- **Implementation**: Guarantee complete data preservation and interoperability of customized lap labels, quick tags, and coaching notes across exports, cloud synchronizations, and imports.
- **Rationale**: The system SHALL support bidirectional serialization and deserialization of lap names and descriptions within Garmin TCX v2 files (`TrainingCenterDatabasev2.xsd`), preserving custom lap labels, quick tags, coaching notes, and activity descriptions across export and import cycles:
1. *TCX Export Schema Placement & Formatting (`TCXFileWriter.java`)*:
• In each exported `<Lap>` element, the system SHALL serialize the lap's custom `name` and/or `description` if present.
• *Schema Sequence Compliance*: In accordance with `TrainingCenterDatabasev2.xsd` (`ActivityLap_t`), lap notes and extensions MUST be placed immediately after the closing `</Track>` tag and before `</Lap>`.
• *Universal Human-Readable `<Notes>`*: The system SHALL write a `<Notes>` element formatted as: `[Name] Description` (if both exist), `[Name]` (if only name exists), or `Description` (if only description exists).
• *Lossless Structured `<Extensions>`*: To guarantee 100% round-trip fidelity between aTrainingTracker instances, the system SHALL write a structured extension: `<Extensions><att:LapExtension xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1"><att:Name>...</att:Name><att:Description>...</att:Description></att:LapExtension></Extensions>`.
• *Activity-Level Notes*: If the workout has an overall description (`WorkoutSummaries.DESCRIPTION`), the system SHALL serialize it under `<Activity>` in `<Notes>` immediately following all `<Lap>` elements and before `<Creator>`.
• *XML Character Escaping*: All text content within `<Notes>`, `<att:Name>`, and `<att:Description>` MUST be strictly sanitized for XML special characters (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`).
• *Query Optimization*: The system SHALL pre-load all lap records for the exported workout in a single batch query (`LapsDatabaseManager.getLaps(workoutID)`) to eliminate N+1 SQLite queries during trackpoint processing.
2. *TCX Import Engine Parsing (`LegacyImportEngine.kt`)*:
• The system SHALL extend `ParsedLap` with `name: String?` and `description: String?`.
• *Parser Context Isolation*: The XML parser SHALL isolate lap-level `<Notes>` from activity-level `<Notes>`: a `<Notes>` element parsed while inside a `<Lap>` context MUST be assigned to the active `ParsedLap`, and a `<Notes>` element parsed outside a `<Lap>` context MUST be assigned to the workout's overall `workoutNotes`.
• *Format Parsing*: If `<att:Name>` / `<Name>` or `<att:Description>` / `<Description>` tags are encountered inside `<Lap>`, they SHALL directly populate `ParsedLap.name` and `ParsedLap.description`. Otherwise, when `<Notes>` is parsed, if it matches the bracket format `^\[(.*?)\](?:\s*(.*))?$`, group 1 SHALL be extracted as `name` and group 2 as `description`. If no brackets are present: if the text contains a newline, line 1 (if $\le 40$ chars) SHALL be treated as `name` and the remainder as `description`; if a single line $\le 40$ chars, it SHALL be treated as `name`; otherwise as `description`.
• *Database Persistence*: When creating lap records in `Laps.db` (`LapsDatabaseManager.saveLap(...)`), the system SHALL persist the parsed `name` and `description` alongside split metrics.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with annotated laps (e.g. Lap 1: Name="Warm-up", Desc="Zone 1 spin"; Lap 2: Name="Interval", Desc="Hard effort") and an overall workout description,
• *When* the workout is exported via `TCXFileWriter`,
• *Then* the generated TCX file SHALL contain valid `<Notes>` and `<Extensions>` within each `<Lap>` element strictly after `</Track>`, and `<Notes>` under `<Activity>`, conforming to TCX schema.
• *Given* the generated TCX file,
• *When* it is imported via `LegacyImportEngine.importFromTcx`,
• *Then* all laps in `Laps.db` SHALL be restored with their exact `name` and `description`, and the workout summary SHALL retain the overall description.
• *Given* a third-party TCX file containing standard `<Notes>[Tempo] Cadence 95</Notes>` or `<Notes>Sprint</Notes>`,
• *When* it is imported,
• *Then* the lap name and description SHALL be correctly extracted and stored.
• *Given* lap names or descriptions containing special characters (e.g. `Hill & Climb <Sprint> "Test"`),
• *When* exported and imported,
• *Then* XML escaping SHALL prevent parse errors and restore the exact original text string.

**Invariants**: Quantitative lap metrics (`time_total_s`, `distance_total_m`, `speed_average_mps`) MUST NOT be altered; files lacking lap notes MUST import cleanly with null name/desc; backup database archiving (`.attbackup`) MUST remain unaffected.

### REQ-DAT-013: TCX Workout Name & Description Bidirectional Export & Import []
- **Tests**: currentName.isNullOrEmpty()
- **Rationale**: The system SHALL support bidirectional serialization and deserialization of the workout name (`WorkoutSummaries.WORKOUT_NAME` / `exportName`) and workout description (`WorkoutSummaries.DESCRIPTION`) in Garmin TCX v2 files (`TrainingCenterDatabasev2.xsd`), ensuring that custom workout names and descriptions are preserved across export and import cycles and protected against cluster-name overwriting:
1. *TCX Export Serialization (`BaseFileWriter.java` & `TCXFileWriter.java`)*:
• `BaseFileWriter.getHeaderData()` SHALL query and populate `workoutName` from `WorkoutSummaries.WORKOUT_NAME` (`"exportName"`).
• *Universal Human-Readable `<Activity><Notes>`*: Under `<Activity Sport="...">`, the system SHALL serialize a `<Notes>` element immediately following all `<Lap>` elements (and before `<Creator>` / `<Extensions>`). If both workout name and description exist, it SHALL write `[Workout Name] Description`; if only name exists, `[Workout Name]`; if only description exists, `Description`.
• *Lossless Structured `<Activity><Extensions>`*: In accordance with TCX schema sequence rules (immediately following `<Creator>`, or after `<Lap>` if `<Creator>` omitted), the system SHALL write a structured extension: `<Extensions><att:ActivityExtension xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1"><att:Name>Workout Name</att:Name><att:Description>Description</att:Description></att:ActivityExtension></Extensions>`.
• *XML Character Escaping*: All text content within `<Notes>`, `<att:Name>`, and `<att:Description>` MUST be strictly sanitized for XML special characters (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`).
2. *TCX Import Parsing & Persistence (`LegacyImportEngine.kt`)*:
• The system SHALL track both `workoutName` and `workoutNotes` during TCX parsing.
• *Format Parsing*: If `<att:ActivityExtension>` with `<att:Name>` / `<att:Description>` or Garmin `<Training><Plan><Name>` is encountered, it SHALL populate `workoutName` and `workoutNotes` directly. Otherwise, when activity-level `<Notes>` is parsed, if it matches bracket format `^\[(.*?)\](?:\s*(.*))?$`, group 1 SHALL be extracted as `workoutName` and group 2 as `workoutNotes`. If no brackets are present and a single line $\le 60$ chars or line 1 $\le 60$ chars is encountered, line 1 SHALL be treated as `workoutName` and remainder as `workoutNotes`.
• *Database Persistence*: When creating or updating the workout record in `WorkoutSummaries` (`recalculateStats`), the system SHALL persist the extracted `workoutName` into `WorkoutSummaries.WORKOUT_NAME` (falling back to `baseFileName` only if no name was provided).
• *Cluster Overwrite Immunity*: Because `WorkoutSummaries.WORKOUT_NAME` is restored to the explicit custom name ($\ne `fileBaseName`), `WorkoutClusterEngine.assignClusterToWorkout()` line 627 (`if (forceIdentity

### REQ-DAT-014: Internet Digital Elevation Model (DEM) Reference Altitude Retrieval, Spatial Caching & Legacy Location Healing [`TST-DAT-008`]
- **Tests**: `ElevationService.kt`, `ElevationSource.kt`, `KnownLocationsDatabaseManager.java`, `AltitudeFromPressureDevice.java`
- **Implementation**: Eliminate weather-induced barometric calibration drift by providing authoritative ground-truth elevation from internet DEM datasets with spatial caching, offline resilience, and automatic healing of legacy locations.
- **Rationale**: The system SHALL retrieve topographic ground-truth elevation from an open Digital Elevation Model (DEM) service over the internet to calibrate barometric altitude sensors at workout start points:
1. *Authoritative DEM Retrieval*: When establishing a reference altitude for a workout start location, the system SHALL query the Open-Meteo Elevation API (`https://api.open-meteo.com/v1/elevation?latitude={lat}&longitude={lng}`) asynchronously via `ElevationService` on a background I/O dispatcher (`Dispatchers.IO`) without blocking the sensor dispatch thread or UI Looper.
2. *Coordinate Quantization*: Latitude and longitude parameters submitted to the elevation API and persisted in `StartLocation2Altitude.db` SHALL be rounded to 5 decimal places ($\approx 1.1\text{m}$ precision at the equator) to eliminate microscopic GPS jitter.
3. *Spatial Geofence Caching*: The system SHALL utilize a spatial distance query against the configured location radius ($200\text{m}$). Any workout starting within the radius of an existing cached entry SHALL reuse the stored altitude and MUST NOT trigger redundant network requests.
4. *Database Schema Evolution (V4 to V5)*: In `KnownLocationsDatabaseManager.java`, `StartLocation2Altitude.db` SHALL be upgraded to `DB_VERSION = 5` adding columns `is_locked INTEGER DEFAULT 0` and `source TEXT DEFAULT 'LEGACY_RAW'`. All migrations MUST adhere to `REQ-DAT-008` (atomic framework transactions without manual transaction handling).
5. *Locked Location Immutability Invariant*: Any database entry where `is_locked == 1` MUST be strictly immutable against automated mutations, refinements, or overwrites.
6. *Fault Resilience & Offline Degradation*: The HTTP client MUST enforce a 5-second connection/read timeout. Upon HTTP 429 (rate limit) or 503/5xx (server error) or network absence (offline), the system SHALL degrade gracefully to GPS altitude (persisting with `source = GPS_FALLBACK` and `is_locked = 0`) and trip a circuit breaker to prevent battery or socket exhaustion.
7. *Legacy Location Migration & Asynchronous Batch Healing*: During V4 to V5 migration, all existing records SHALL be initialized with `is_locked = 0` and `source = 'LEGACY_RAW'`. When online connectivity is available, the system SHALL execute an asynchronous batch DEM elevation query for all unlocked legacy records, atomically updating their stored altitudes to ground-truth DEM values and transitioning their source to `'INTERNET_DEM'`. If any legacy location has `is_locked == 1`, it MUST NOT be altered.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active internet connection and a new workout start coordinate (lat 52.52000, lng 13.41000),
• *When* `ElevationService.fetchElevation()` is invoked,
• *Then* the system SHALL parse the response (`elevation[0]`), persist the record with `source = INTERNET_DEM`, and apply the elevation to `AltitudeFromPressureDevice.setAltitudeCorrection()`.
• *Given* a location already present in `StartLocation2Altitude.db` within 200m,
• *When* tracking starts,
• *Then* the system SHALL use the cached altitude immediately with zero network requests.
• *Given* existing legacy records in V4 upon upgrade to V5,
• *When* online connectivity is established,
• *Then* the system SHALL query Open-Meteo in batch, update all unlocked legacy records with DEM elevations, and set `source = INTERNET_DEM`.
• *Given* a location with `is_locked == 1`,
• *When* automated learning or batch healing runs,
• *Then* the stored altitude and metadata SHALL remain unchanged.
• *Given* the device is offline or the request times out after 5 seconds,
• *When* elevation is queried,
• *Then* the system SHALL fall back to GPS altitude without crashing or blocking the sensor thread.

**Invariants**: Cold-start baseline protection (`REQ-CON-011`), null-safe correction dispatch (`REQ-CON-013`), atomic database upgrade transactions (`REQ-DAT-008`), and downstream sample shifts (`REQ-FIL-004`) MUST NOT be altered or regressed.

### REQ-DAT-015: Decoupled Barometric Altimeter Calibration and Authoritative Workout Start Counting [`TST-DAT-010`]
- **Tests**: `AltitudeFromPressureDevice.java`, `KnownLocationsDatabaseManager.java`, `TrackerService.java`
- **Implementation**: Decouple barometric altimeter calibration from workout start counting so that location hit counts exclusively measure athlete-initiated workouts, eliminating false start counts on app launch.
- **Rationale**: The system SHALL decouple barometric altimeter calibration from location visit/start frequency counting, ensuring that location hit counts exclusively reflect athlete-initiated workout recording sessions (ATT-1447):
1. *Read-Only Sensor Warmup Calibration (`AltitudeFromPressureDevice.java`)*:
• During sensor initialization (`initPressureSensor()`), when GPS coordinates (`currentLatLng`) and barometric pressure are available, the system SHALL query `knownLocationsDb.getMyLocation(currentLatLng)` strictly as a read-only lookup to apply `setAltitudeCorrection(myLocation.altitude)` and update `mAltitudeSensor`.
• The system SHALL NOT invoke `knownLocationsDb.learnLocation(...)` or mutate SQLite database records during sensor warmup or application launches.
2. *Authoritative Workout Start Counting (`KnownLocationsDatabaseManager.java`)*:
• The system SHALL provide dedicated, atomic workout start tracking methods: `recordWorkoutStart(@NonNull LatLng pos)` and `recordWorkoutStart(@NonNull LatLng pos, @Nullable Double altitude)`.
• When a workout start is recorded, if `pos` falls within the geofence radius ($200\text{m}$) of an existing known location, the system SHALL increment `hitCount = existing.hitCount + 1` in SQLite within an atomic transaction boundary.
• If `pos` does not match an existing location within $200\text{m}$, the system SHALL create a new location entry with `hitCount = 1`, resolve its human-readable name via `LocationNameResolver`, and store reference elevation (`altitude`).
3. *Single-Shot Workout Start Trigger (`TrackerService.java`)*:
• In `TrackerService`, the system SHALL track session state with a single-shot flag (`mWorkoutStartLocationRecorded = false`).
• When a new workout session starts (`mStartType == StartType.START_NORMAL`), upon acquiring the first valid GPS coordinate (`currentPos != null`) in `sampleAndWriteToDb()`, the system SHALL asynchronously dispatch `recordWorkoutStart(startPos, startAlt)` on the background database executor (`mDbExecutor`) and set `mWorkoutStartLocationRecorded = true`.
• Resumed workouts (`RESUME_BY_USER`, `RESUME_SERVICE_RECREATION`) SHALL initialize `mWorkoutStartLocationRecorded = true`, ensuring that pausing, resuming, or service recreation never increments start counts.
4. *Historical Data Preservation (Variante A)*:
• All existing `hitCount` values in `StartLocation2Altitude.db` SHALL be preserved as-is without destructive database resets or migrations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-DAT-007` (item 2: *Hit Count Frequency Tracking*) in `docs/requirements.md`, targeting `AltitudeFromPressureDevice.java` and `KnownLocationsDatabaseManager.java`.
2. *Historical Origin & Commit Trace*: Introduced in ticket `ATT-1366` via commit `637eaefb` to track location usage frequency without corrupting established reference altitudes through running averages.
3. *Root Reason for Existing Formulation*: `ATT-1366` placed `learnLocation(..., ExtremaType.START)` inside `initPressureSensor()` under the assumption that sensor warmup coincided with workout recording. However, sensor warmup executes on every application cold-start and cockpit display, resulting in every app launch near a known location falsely incrementing the location's "Starts" badge.
4. *Preservation of Core Invariants*: Reference altitude preservation against barometric drift (`REQ-DAT-007`), spatial geofence matching radius ($200\text{m}$), schema version (V5), locked location immutability (`REQ-DAT-014`), null-safe correction dispatch (`REQ-CON-013`), cold-start baseline protection (`REQ-CON-011`), and altimeter calibration functionality (`setAltitudeCorrection`) remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an existing known location with `hitCount = 5` and a device located within its 200m geofence,
• *When* the user launches the application, opens the cockpit, or reviews past workouts without starting a session,
• *Then* `AltitudeFromPressureDevice` SHALL apply reference altitude calibration (`setAltitudeCorrection`), and `hitCount` in `StartLocation2Altitude.db` SHALL remain strictly 5.
• *Given* an existing known location with `hitCount = 5`,
• *When* the user starts a new workout (`START_NORMAL`) and the first valid GPS position is acquired,
• *Then* `TrackerService` SHALL trigger `recordWorkoutStart()`, and `hitCount` in `StartLocation2Altitude.db` SHALL increment to 6.
• *Given* an active workout that is paused and subsequently resumed by the athlete or recreated after service interruption,
• *When* tracking resumes,
• *Then* `mWorkoutStartLocationRecorded` SHALL prevent redundant execution, and `hitCount` SHALL NOT increment.
• *Given* a workout started at a novel coordinate $>200\text{m}$ from any known location,
• *When* tracking starts,
• *Then* a new location record SHALL be created in SQLite with `hitCount = 1`, geocoded name, and reference altitude.

**Invariants**: Altimeter calibration calculations (`setAltitudeCorrection`), 200m geofence radius, SQLite schema V5, locked location altitude protection (`REQ-DAT-014`), and existing database records MUST NOT be modified or regressed.

### REQ-DAT-016: Authoritative Workout Start Count Self-Healing Reconciliation & Reactive Presentation [`TST-DAT-011`]
- **Tests**: `WorkoutSummariesDatabaseManager.java`, `KnownLocationsDatabaseManager.java`, `KnownLocationsRepository.kt`, `KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`
- **Implementation**: Reconcile known location visit counts with authoritative recorded workout starts and synchronize reactive counts across data and presentation layers.
- **Rationale**: The system SHALL reconcile known location visit counts (`hitCount`) with authoritative recorded workout starts and provide synchronized reactive counts across data and presentation layers (ATT-1734):
1. *Authoritative Workout Start Location Extraction (`WorkoutSummariesDatabaseManager.java`)*:
• The system SHALL provide `getAllWorkoutStartLocations(): List<LatLng>` querying `TABLE_EXTREMA_VALUES` where `SENSOR_TYPE == LATITUDE`, `EXTREMA_TYPE == START`, and both `LATITUDE` and `LONGITUDE` columns are non-null.
2. *Elimination of Elevation Healing Hit Count Corruption (`KnownLocationsDatabaseManager.java`)*:
• In `upsertLocationByGeofence()`, the system SHALL NOT increment `hitCount` during DEM elevation updates or healing.
3. *Database-Level Self-Healing Reconciliation (`KnownLocationsDatabaseManager.java`, `KnownLocationsRepository.kt`)*:
• The system SHALL provide `reconcileHitCountsWithWorkoutSummaries(@NonNull List<LatLng> startLocations)`, which evaluates the exact count of coordinates in `startLocations` satisfying `WorkoutClusterEngine.distanceBetween(location.latLng, startPos) <= location.radius` and updates `my_locations.hitCount` in SQLite within an atomic transaction.
• During repository initialization (`KnownLocationsRepository.init`), the system SHALL dispatch this reconciliation asynchronously on `KnownLocationsDB-Thread` (`dbDispatcher`).
4. *Reactive UI Starts Counting (`KnownLocationsViewModel.kt`)*:
• `KnownLocationsViewModel` SHALL observe `WorkoutRepository.allWorkouts` and maintain `startsByLocationId: Map<Long, Int>`.
• For each location, the starts count SHALL equal the count of workouts matching `workout.startLatLng != null && WorkoutClusterEngine.distanceBetween(location.latLng, workout.startLatLng) <= location.radius`.
• Sorting by `KnownLocationSortOrder.STARTS` SHALL sort using `startsByLocationId[it.id] ?: it.hitCount`.
5. *Presentation & Navigation Parity (`KnownLocationsScreen.kt`)*:
• `KnownLocationCard` SHALL display `startsCount = uiState.startsByLocationId[item.id] ?: item.hitCount`, ensuring 100% numerical parity with `WorkoutFilterCriteria`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-DAT-015` (item 4: *Historical Data Preservation (Variante A)*), `REQ-DAT-007` (item 2: *Hit Count Frequency Tracking*), targeting `KnownLocationsDatabaseManager.java` and `KnownLocationsViewModel.kt`.
2. *Historical Origin & Commit Trace*: Introduced in sprint `2026-39.3` (`ATT-1447`) via commit `bcfa9317`.
3. *Root Reason for Existing Formulation*: `ATT-1447` preserved existing historical `hitCount` values without recalculation out of caution to avoid destructive database wipes, leaving pre-ATT-1447 cold-start bloat uncorrected.
4. *Preservation of Core Invariants*: Decoupled sensor warmup (`REQ-DAT-015`), authoritative live workout start tracking (`TrackerService.recordWorkoutStart`), locked location immutability (`REQ-DAT-014`), and single-thread SQLite confinement (`KnownLocationsDB-Thread`) remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a database containing a known location with bloated historical `hitCount = 625` and 45 workouts starting within its 200m radius,
• *When* `reconcileHitCountsWithWorkoutSummaries()` executes,
• *Then* `hitCount` in `my_locations` table SHALL be updated to 45.
• *Given* an existing known location with `hitCount = 10`,
• *When* `upsertLocationByGeofence()` executes following an asynchronous Open-Meteo DEM resolution,
• *Then* `hitCount` SHALL remain strictly 10.
• *Given* `KnownLocationsViewModel` observing 5 workouts at "Zu Hause" (`startsCount = 5`),
• *When* a workout is deleted from `WorkoutRepository`,
• *Then* `startsByLocationId` for "Zu Hause" SHALL immediately emit 4.
• *Given* a `KnownLocationCard` displaying "45 Starts",
• *When* the user taps the Starts badge to filter workouts,
• *Then* the number of workouts displayed in `WorkoutList` SHALL equal exactly 45.

**Invariants**: Altimeter calibration calculations (`setAltitudeCorrection`), 200m geofence radius, SQLite schema V5, locked location altitude protection (`REQ-DAT-014`), and single-thread SQLite confinement MUST NOT be altered.

### REQ-DAT-017: Workout Origin Source Provenance Attribute [`TST-DAT-012`]
- **Tests**: `WorkoutSource.kt`, `WorkoutSummariesDatabaseManager.java`, `WorkoutData.kt`, `WorkoutHeaderData.kt`, `WorkoutDataMapper.kt`, `LegacyImportEngine.kt`, `WorkoutHeader.kt`
- **Implementation**: Provide complete data transparency, traceability, and platform identity for workouts by recording origin source (live tracking vs imported TCX/GPX/FIT) across database, ingestion, domain model, and UI header.
- **Rationale**: The system SHALL store, maintain, and display the origin source of every workout session across the application lifecycle:
1. *Domain Model Definition (`WorkoutSource`)*: The system SHALL define an enum `com.atrainingtracker.trainingtracker.database.WorkoutSource` with values `TRACKED` (live sensor tracking on Android device), `TCX` (Garmin Training Center XML), `GPX` (GPS Exchange Format), and `FIT` (Garmin Flexible and Interoperable Data Transfer). `WorkoutSource` SHALL provide parser `fromString(value: String?)` defaulting to `TRACKED` for null, blank, or unrecognized values.
2. *Database Schema Evolution (`WorkoutSummaries.db` v24)*: In `WorkoutSummariesDatabaseManager.java`, schema version SHALL increment to `DB_VERSION = 24`. Table `workout_summaries` SHALL define column `source text DEFAULT 'TRACKED'`. In `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`, for `oldVersion < 24`, the system SHALL safely add the column using `addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.SOURCE, "text", "'TRACKED'")`. In `updateWorkoutData()`, the system SHALL persist `source` to SQLite.
3. *Ingestion Pipeline Provenance Tagging*: Live tracking (`TrackerService`) SHALL set `SOURCE = "TRACKED"`. TCX import (`LegacyImportEngine.importTcxStream`) SHALL set `SOURCE = "TCX"`. GPX import (`LegacyImportEngine.importGpxWorkoutStream`) SHALL set `SOURCE = "GPX"`. FIT import (`FitFileImporter`) SHALL set `SOURCE = "FIT"`. Database backup imports (`ImportEngine`) SHALL preserve the `SOURCE` column dynamically.
4. *Domain Mapping & In-Memory State*: `WorkoutData.kt` and `WorkoutHeaderData.kt` SHALL expose `val source: WorkoutSource = WorkoutSource.TRACKED`. `WorkoutDataMapper.fromCursor` SHALL extract `SOURCE` safely with fallback to `WorkoutSource.TRACKED`. `WorkoutRepository.saveWorkout` SHALL preserve `source` during in-memory updates.
5. *UI Presentation in WorkoutHeader*: `WorkoutHeader.kt` SHALL render an origin badge for non-tracked workouts (`data.source != WorkoutSource.TRACKED`) in Row A (sport metadata row) using Material 3 tonal surface styling and localized origin labels.
6. *100% 9-Language Localization Parity*: User-facing origin strings (`workout_source_tracked`, `workout_source_tcx`, `workout_source_gpx`, `workout_source_fit`) SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded live via `TrackerService`,
• *When* tracking finishes and commits to SQLite,
• *Then* `WorkoutSummaries.SOURCE` is persisted as `'TRACKED'`.
• *Given* a TCX or GPX file imported via `LegacyImportEngine`,
• *When* the import transaction finishes,
• *Then* `WorkoutSummaries.SOURCE` is persisted as `'TCX'` or `'GPX'` respectively.
• *Given* a v23 database with historical workouts,
• *When* upgraded to v24 via `onUpgrade`,
• *Then* column `source` is created, historical workouts evaluate to `WorkoutSource.TRACKED`, and zero data is lost.
• *Given* a workout with `source = WorkoutSource.TCX`,
• *When* rendered in `WorkoutHeader`,
• *Then* the header displays the origin badge (`TCX`).
• *Given* a workout with `source = WorkoutSource.TRACKED`,
• *When* rendered in `WorkoutHeader`,
• *Then* the header renders without an import badge.

**Invariants**: Zero data loss during v24 migration; immutable provenance across edits; single-source UI consistency in `WorkoutHeader`.

### REQ-DAT-018: Full-Telemetry FIT Workout Exporter via Garmin FIT SDK (`FitFileWriter`) [`TST-DAT-013`]
- **Tests**: `FitFileWriter.java`, `FileFormat.java`, `ExportManager.java`, `TrainingApplication.java`, `ExportSettingsDialog.kt`
- **Implementation**: Provide high-fidelity, compact binary workout exports conforming to the Garmin FIT protocol for broad third-party analysis tool interoperability.
- **Rationale**: The system SHALL export workout sessions to binary Garmin FIT format (`.fit`) utilizing the official Garmin FIT SDK (`com.garmin:fit`):
1. *FIT Protocol & SDK Integration*: The system SHALL utilize `com.garmin.fit.FileEncoder` with protocol `Fit.ProtocolVersion.V2_0` to encode valid, CRC-protected binary `.fit` activity files.
2. *FIT Message Pipeline Hierarchy (`FitFileWriter`)*:
• `FileIdMesg`: Set `type = File.ACTIVITY`, `manufacturer = Manufacturer.DEVELOPMENT`, `product = 1`, `serialNumber = 1L`, and `timeCreated` matching the workout start time.
• `ActivityMesg`: Set `timestamp` matching workout end time, `totalTimerTime` matching elapsed duration in seconds, `numSessions = 1`, and `type = Activity.MANUAL`.
• `SessionMesg`: Set `startTime`, `timestamp` (end time), `totalElapsedTime`, `totalTimerTime`, `totalDistance` in meters, `sport` mapped from `SportType` (`Sport.CYCLING`, `Sport.RUNNING`, `Sport.WALKING`, `Sport.SWIMMING`, `Sport.TRANSITION`, `Sport.FITNESS_EQUIPMENT`, or `Sport.GENERIC`), `subSport = SubSport.GENERIC`, `totalCycles` (total steps/pedal revolutions if available), `totalCalories`, and session summary statistics (`avgSpeed`, `maxSpeed`, `avgHeartRate`, `maxHeartRate`, `avgCadence`, `maxCadence`, `avgPower`, `maxPower`, `totalAscent`, `totalDescent`) extracted from `WorkoutData` and `extrema_values`.
• `LapMesg`: For workouts with laps/intervals in `Laps.db`, the system SHALL encode a `LapMesg` for each lap, setting `startTime`, `timestamp`, `totalElapsedTime`, `totalTimerTime`, `totalDistance`, lap averages/extrema, and `intensity`.
• `RecordMesg`: The system SHALL iterate all recorded sample points (`samplesTable`), encoding sequential `RecordMesg` entries containing: `timestamp`, `positionLat` (degrees converted to semicircles: `degrees * (2^31 / 180.0)`), `positionLong` (semicircles), barometric/calibrated `altitude` (meters), `distance` (cumulative meters), `speed` (m/s), `heartRate` (bpm), `cadence` (rpm), `power` (watts), and ambient `temperature` (Celsius) when present in the sample record.
3. *Export Architecture Integration (`FileFormat`, `ExportManager`, `TrainingApplication`, `ExportSettingsDialog`)*:
• `FileFormat.java` SHALL define `FIT("FIT", ".fit", R.string.FIT)` and include `FIT` in `STANDARD_FILE_FORMATS`.
• `ExportManager.java` SHALL register `case FIT -> new FitFileWriter(context)` in `getExporter(FileFormat)` and include `FileFormat.FIT` in `ExportType.DROPBOX` supported formats.
• `TrainingApplication.java` SHALL provide `SP_EXPORT_FIT = "SP_EXPORT_FIT"`, `exportToFIT()`, and `setExportToFIT(boolean)`. In `exportToFile(FileFormat)`, `case FIT -> exportToFIT()` SHALL be handled.
• `ExportSettingsDialog.kt` SHALL provide an `ExportOptionToggle` for `FIT`.
4. *Binary Stream & File Handling*: `FitFileWriter` SHALL write binary data directly to `new File(getBaseDirFile(mContext), exportInfo.getShortPath())` without text/string conversion.
5. *9-Language Localization Parity*: Add non-translatable string `FIT` (`<string name="FIT" translatable="false">FIT</string>`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-DAT-018`) extending export subsystem (`REQ-DAT-001`, `REQ-DAT-012`, `REQ-DAT-013`) under Epic `ATT-1117` (*FIT File Format Support*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1827`, target release `V4.9.39`, sprint `2026-40.14`.
3. *Root Reason for Existing Formulation*: Prior exporters (TCX, GPX, CSV) are text/XML-based, resulting in high storage footprints and lack of native binary telemetry representation expected by modern analysis engines (Garmin Connect, Intervals.icu, Golden Cheetah, WKO5).
4. *Preservation of Core Invariants*: Existing TCX, GPX, CSV, and GC JSON exporters MUST NOT be modified or regressed; workout database schema remains unaltered; cloud upload pipelines (Dropbox, Google Drive) seamlessly handle `.fit` files alongside other formats.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded with GPS coordinates, heart rate, cadence, and power samples,
• *When* the workout is exported with FIT format enabled,
• *Then* `FitFileWriter` SHALL generate a binary `.fit` file readable and verifiable with 0 errors by Garmin FIT SDK `Decode`.
• *Given* the decoded `.fit` file,
• *When* inspecting messages,
• *Then* `FileIdMesg`, `ActivityMesg`, `SessionMesg`, `LapMesg`, and sequential `RecordMesg` entries SHALL accurately reflect workout telemetry, including semicircles coordinate scaling and sensor metrics.
• *Given* cloud export configured (Dropbox),
• *When* batch export runs,
• *Then* the generated `.fit` file is included in the upload queue.

**Invariants**: Existing exporters (TCX, GPX, CSV) retain identical behavior; thread safety on background export executors; zero data loss.

### REQ-DAT-019: External FIT Workout Importer with Duplicate Detection & Sport Mapping [`TST-DAT-014`]
- **Tests**: `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`, `strings.xml` (9 locales)
- **Implementation**: Enable athletes to import historical and external workouts from Garmin, Wahoo, Hammerhead, and third-party devices with full sensor telemetry, duplicate prevention, and sport mapping.
- **Rationale**: The system SHALL import external binary Garmin FIT activity files (`.fit`) into the application's local workout database utilizing the official Garmin FIT SDK (`com.garmin:fit`):
1. *FIT Stream Decoding (`LegacyImportEngine.importFromFit`)*:
• The system SHALL utilize `com.garmin.fit.Decode` and `com.garmin.fit.MesgBroadcaster` to parse `.fit` input streams with CRC and header integrity checks.
• `RecordMesg`: The system SHALL extract second-by-second trackpoint records, converting semicircles to decimal degrees ($\text{degrees} = \text{semicircles} \times (180.0 / 2^{31})$), with null-safe omission for indoor records lacking GPS fix. The system SHALL buffer and insert samples for `LATITUDE`, `LONGITUDE`, `ALTITUDE`, `SPEED`, `DISTANCE`, `HEART_RATE`, `CADENCE`, `POWER`, and `TEMPERATURE` into `WorkoutSamplesDatabaseManager`.
• `LapMesg`: The system SHALL extract lap intervals and persist them into `LapsDatabaseManager`.
• `SessionMesg` / `ActivityMesg`: The system SHALL extract workout timing (`TIME_START`, elapsed time, timer time), total distance, calories, and averages/extrema for HR, speed, cadence, and power.
2. *Intelligent Sport Mapping*:
• The system SHALL map FIT `Sport` and `SubSport` enums to `BSportType` (`Sport.CYCLING` -> `BIKE`, `Sport.RUNNING` / `WALKING` -> `RUN`, `Sport.SWIMMING` / other -> `OTHER` / `UNKNOWN`) and resolve `SPORT_ID` via `SportTypeDatabaseManager`.
3. *Multi-Dimensional Duplicate Detection & Mutex Confinement*:
• Within `importMutex.withLock`, the system SHALL evaluate `isWorkoutExisting(summaryDb, fileBaseName, timeStart, bSportType)`. If a duplicate exists (exact file name, exact start time, or $\le 180$s epoch proximity with matching sport), the importer SHALL return `ImportStatus.DUPLICATE_SKIPPED` without throwing errors or interrupting batch imports.
4. *Provenance Attribution*:
• The imported workout summary SHALL be stored with `WorkoutSummaries.SOURCE = WorkoutSource.FIT.name`.
5. *Batch & Single UI Ingestion (`BackupRestoreViewModel`, `ImportBackupTabsScreen`)*:
• The system SHALL support single and multiple file selection (`ActivityResultContracts.OpenMultipleDocuments()`) for `.fit` files.
• `BackupRestoreViewModel.importFitFiles` SHALL process selected files sequentially, display progress, trigger post-import repository reconciliation, and present an import summary (imported count, skipped duplicates count, failed count).
6. *9-Language Localization Parity*:
• All user-facing strings SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-DAT-019`) extending import and data sovereignty subsystems (`REQ-DAT-017`, `REQ-MIG-031`) under Epic `ATT-1117` (*FIT File Format Support*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1828`, target release `V4.9.39`, sprint `2026-40.14`.
3. *Root Reason for Existing Formulation*: Athletes record activities on external devices (Garmin, Wahoo, Hammerhead) producing `.fit` files. Previously, only TCX and GPX XML files were importable, lacking native binary FIT ingestion and losing detailed telemetry streams.
4. *Preservation of Core Invariants*: Existing TCX, GPX, and database restore mechanics remain intact; deduplication epoch threshold (180s) preserved; zero regressions on existing database tables.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete selects a valid `.fit` file from a Garmin or Wahoo device,
• *When* initiating the import,
• *Then* a new workout is created in SQLite containing matching session metrics, route coordinates, laps, and time-series sensor samples with `source = 'FIT'`.
• *Given* a workout already exists in aTrainingTracker with matching start time and duration,
• *When* the user imports the identical `.fit` file again,
• *Then* the importer skips the file without creating a duplicate and returns `DUPLICATE_SKIPPED`.
• *Given* a batch of FIT files containing duplicates and valid files,
• *When* running the batch import,
• *Then* the valid new workouts are imported, duplicates skipped cleanly, and a summary is displayed.
• *Given* a FIT file with sport `CYCLING` or `RUNNING`,
• *When* imported,
• *Then* the workout is assigned to the corresponding internal sport type (`BIKE` / `RUN`).

**Invariants**: Existing exporters and import engines retain identical behavior; thread safety on background executors; zero data loss.

### REQ-DAT-020: Google Drive Integration for Automated Workout Export and Database Backup [`TST-DAT-015`]
- **Tests**: `GoogleDriveClient.kt`, `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`, `GoogleDriveSettingsDialog.kt`, `GoogleDriveAuthManager.kt`, `ExportManager.java`, `BackupWorker.kt`, `BackupRestoreViewModel.kt`
- **Implementation**: Provide native Google Drive integration for automated activity file export and SQLite database backup and restore using least-privilege `drive.file` scope with native Google Sign-In and verified OAuth2 token exchange.
- **Rationale**: The system SHALL integrate Google Drive cloud storage for automated workout export and database backup synchronization utilizing the least-privilege `drive.file` scope (ATT-1306):
1. *Native Google Sign-In & Verified OAuth2 Token Exchange (`GoogleDriveAuthManager`, `TrainingApplication`)*:
• The system SHALL authenticate the athlete's Google account via native Google Play Services Sign-In (`play-services-auth`) requesting strictly the `https://www.googleapis.com/auth/drive.file` OAuth scope, guaranteeing the application accesses only files and directories that it creates.
• The system SHALL asynchronously acquire a verified OAuth2 Bearer token via `GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/drive.file")` on `Dispatchers.IO`.
• Manual text input dialogs for credentials and dummy token fallbacks (e.g. `"gdrive_oauth_token"`) are STRICTLY FORBIDDEN.
• The system SHALL mark Google Drive as connected (`uploadToGoogleDrive = true`) IF AND ONLY IF a verified OAuth2 access token and valid Google account email are successfully obtained from Google Play Services.
• Tapping "Trennen" (Disconnect) SHALL invoke `GoogleSignInClient.signOut()` and `revokeAccess()`, delete stored credentials, invalidate cached tokens via `GoogleAuthUtil.clearToken`, and immediately suspend background uploads.
• Unlinked safety: Reading credentials when unlinked SHALL return `null` safely without throwing `NullPointerException`.
2. *Folder Architecture & Idempotent Directory Resolution (`GoogleDriveClient`)*:
• The system SHALL maintain a dedicated folder structure: `aTrainingTracker/` at the root, with subfolders `aTrainingTracker/Workouts/` for exported activity files (FIT, TCX, GPX, CSV) and `aTrainingTracker/Backups/` for full database backups (`aTrainingTracker_backup.attbackup`).
• Directory resolution SHALL be idempotent: existing folder IDs SHALL be reused, or created via Google Drive REST API v3 when missing.
3. *Automated & Manual Workout Export (`GoogleDriveUploader`, `ExportManager`)*:
• `GoogleDriveUploader` SHALL extend `BaseExporter` parallel to `DropboxUploader.java`.
• `ExportType.GOOGLE_DRIVE` SHALL support standard export formats (`CSV`, `GC`, `GPX`, `TCX`, `FIT`).
• WorkManager upload jobs SHALL be enqueued upon workout finalization when `uploadWorkoutsToGoogleDrive == true`.
• Wi-Fi Constraint: When `googleDriveOnlyWifi == true`, WorkManager requests SHALL enforce `NetworkType.UNMETERED` constraint.
4. *Automated & Manual Database Backup & Restore (`GoogleDriveBackupManager`, `BackupWorker`, `BackupRestoreViewModel`)*:
• Automated Backup: `BackupWorker.doWork()` SHALL upload `aTrainingTracker_backup.attbackup` to `aTrainingTracker/Backups/` when Google Drive backup is enabled.
• Manual Backup: `BackupRestoreViewModel.uploadToGoogleDrive()` SHALL create and upload a backup to Google Drive on demand.
• Cloud Restore: `BackupRestoreViewModel.restoreFromGoogleDrive()` SHALL download `aTrainingTracker_backup.attbackup` from `aTrainingTracker/Backups/` and restore application state via `MigrationEngine.performFullRestore()`.
5. *Independent Cloud Coexistence*:
• Google Drive and Dropbox operations SHALL be fully decoupled. A failure or timeout in one provider SHALL NOT affect the other.
6. *Settings & Navigation UI (`GoogleDriveSettingsDialog`, `AppNavigationDrawer`)*:
• Composable bottom sheet conforming to `AppBottomSheetContent` and `AppDialogActions.SaveCancel`.
• Connected account header with brand styling, toggles for workout export, backup synchronization, and Wi-Fi only, plus last sync timestamp and result.
• Conditional Disconnected State: While `isConnected == false`, the synchronization switches (workouts, backup, wifi-only) SHALL be conditionally hidden or disabled, presenting an onboarding prompt instructing the user to connect their Google Drive account.
• Localized Error Presentation: When Google Sign-In or token acquisition fails, `errorMessage` SHALL display a localized user-friendly string across all 9 languages (never raw English or unmapped exception messages).
• Developer Setup / Status 10 Diagnostic: When `ApiException` returns status 10 (`CommonStatusCodes.DEVELOPER_ERROR`), the dialog SHALL display a dedicated localized string explaining that the Google Cloud Console OAuth client configuration (package name / SHA-1) is required.
• Navigation drawer entry `R.id.drawer_google_drive` and `SettingsBottomSheetType.GOOGLE_DRIVE`.
7. *9-Language Localization Parity*:
• All user-facing strings and plural resources SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-DAT-020`) extending cloud export and backup framework (`REQ-DAT-006`, `REQ-DAT-018`, `REQ-MIG-023`) under Epic `ATT-162` (*Cloud integration*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1306`, target release `V4.9.40`, sprint `2026-41.1` (Rework Cycle 3: on-device status 10 diagnostic, human prerequisite, and UI defect remediation).
3. *Root Reason for Existing Formulation*: Initial cycle implemented transport and export pipelines but fell back to developer mock input dialogs. Native Google Sign-In with real OAuth2 token exchange was added in Cycle 2. Cycle 3 adds localized error mapping and conditional suppression of sync toggles when disconnected.
4. *Preservation of Core Invariants*: Existing Dropbox and Strava integrations remain 100% operational; SQLite tables and `.attbackup` format remain unaltered; least-privilege `drive.file` scope and unlinked safety invariants preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete taps \"Verbinden\" in `GoogleDriveSettingsDialog`,
• *When* the sign-in intent is launched,
• *Then* the system SHALL display the native Google Sign-In account selector requesting `drive.file` scope (never a mock manual text field).
• *Given* a selected Google account with granted consent,
• *When* the sign-in intent returns,
• *Then* `GoogleDriveAuthManager` SHALL fetch the verified OAuth2 Bearer token via `GoogleAuthUtil.getToken` on `Dispatchers.IO`, store credentials, and update UI to connected.
• *Given* authentication is cancelled or token acquisition fails,
• *Then* credentials SHALL NOT be saved, `uploadToGoogleDrive` SHALL remain `false`, and an informative, localized error message SHALL be displayed across all 9 supported locales.
• *Given* the athlete is not connected to Google Drive (`isConnected == false`),
• *Then* the synchronization switches (workouts, backup, wifi-only) SHALL NOT be shown active/configurable, and a connect prompt SHALL guide the user.
• *Given* Google Drive workout export is enabled,
• *When* completing a workout,
• *Then* the workout files matching active format selections are uploaded to `aTrainingTracker/Workouts/` in Google Drive.
• *Given* \"Nur über WLAN hochladen\" is active and the device is on cellular data,
• *When* a workout finishes,
• *Then* the upload is queued in WorkManager with unmetered network constraint.
• *Given* a valid backup in `aTrainingTracker/Backups/`,
• *When* the user triggers \"Aus Google Drive wiederherstellen\",
• *Then* the backup file is downloaded, validated, and restored cleanly.
• *Given* both Dropbox and Google Drive are enabled,
• *When* a backup or export occurs,
• *Then* both cloud services process uploads independently without mutual blocking.

**Invariants**: Dropbox, Strava, and local export behavior MUST NOT regress; least-privilege `drive.file` scope strictly enforced; zero data loss.

### REQ-DAT-021: Workout Origin Source Batch Mapping Symmetry and Cursor Snapshot Propagation [`TST-DAT-016`]
- **Tests**: `WorkoutDataMapper.kt`, `WorkoutData.kt`, `WorkoutHeader.kt`
- **Implementation**: Guarantee provenance preservation across batch loading, snapshot streaming, and UI display for imported workouts by enforcing mapping symmetry in `WorkoutDataMapper`.
- **Rationale**: The system SHALL map the `WorkoutSummaries.SOURCE` attribute symmetrically across all cursor mapping pathways in `WorkoutDataMapper.kt` (ATT-2387):
1. *Raw Cursor Snapshot Provenance Support (`WorkoutDataMapper.RawCursorSnapshot`)*:
• `RawCursorSnapshot` SHALL include property `val source: WorkoutSource = WorkoutSource.TRACKED`.
2. *Deterministic Cursor Extraction (`WorkoutDataMapper.readCursorSnapshot`)*:
• In `readCursorSnapshot(cursor: Cursor)`, the system SHALL extract `WorkoutSummaries.SOURCE` via `cursor.getColumnIndex(WorkoutSummaries.SOURCE).takeIf { it >= 0 }?.let { WorkoutSource.fromString(cursor.getString(it)) } ?: WorkoutSource.TRACKED` and pass it to `RawCursorSnapshot(..., source = source)`.
3. *Symmetric Batch & Snapshot Enrichment (`WorkoutDataMapper.fromSnapshot` & `fromCursor`)*:
• In `fromSnapshot(snapshot: RawCursorSnapshot, batch: BatchMetadata)`, the system SHALL explicitly pass `source = snapshot.source` when instantiating `WorkoutData`.
• `fromCursor(cursor: Cursor, batch: BatchMetadata)` SHALL inherit identical mapping via its delegation to `fromSnapshot(readCursorSnapshot(cursor), batch)`.
4. *Architectural Mapping Symmetry Enforcement (Rule 20)*:
• An architectural contract test SHALL verify that all cursor column properties mapped in `fromCursor(cursor)` are also declared in `RawCursorSnapshot`, extracted in `readCursorSnapshot`, and mapped into `WorkoutData` in `fromSnapshot`.
5. *UI Header Badge Presentation for Batch-Loaded Workouts*:
• When workouts are batch-loaded via `loadAllWorkouts()`, `PeriodsRepository`, or `WorkoutClusterRepository`, workouts originating from `TCX`, `GPX`, or `FIT` SHALL preserve `data.source != WorkoutSource.TRACKED` and display their origin source badge in `WorkoutHeader` and workout list cards.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines Clause 4 of `REQ-DAT-017` (*Workout Origin Source Provenance Attribute*) in `docs/requirements.md`, targeting `WorkoutDataMapper.kt` and `WorkoutRepository.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-2186` (commit `4cb82b75`, single-row mapping) and ticket `ATT-2309` (commit `3e710f3c`, snapshot batch decoupling).
3. *Root Reason for Existing Formulation*: Clause 4 of `REQ-DAT-017` stated: *"WorkoutDataMapper.fromCursor SHALL extract SOURCE safely with fallback to WorkoutSource.TRACKED."* It did not explicitly mandate that batch overloads (`fromCursor(cursor, batch)`, `readCursorSnapshot`, and `fromSnapshot`) must map all attributes symmetrically.
4. *Preservation of Core Invariants*: Existing live-tracked workouts continue to have `source = TRACKED` and omit the badge; historical workouts prior to schema v24 evaluate safely to `TRACKED`; batch streaming performance (`ATT-2309` zero cursor-seeking invariant) is preserved with zero overhead.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a SQLite cursor row containing `source = 'TCX'` (or `'GPX'`, `'FIT'`),
• *When* mapped via `WorkoutDataMapper.fromCursor(cursor, batch)` or `fromSnapshot(readCursorSnapshot(cursor), batch)`,
• *Then* the resulting `WorkoutData.source` SHALL equal `WorkoutSource.TCX` (or `GPX`, `FIT`), and `WorkoutData.headerData.source` SHALL equal `WorkoutSource.TCX`.
• *Given* a SQLite cursor row containing `source = 'TCX'`,
• *When* `readCursorSnapshot(cursor)` executes,
• *Then* the returned `RawCursorSnapshot.source` SHALL equal `WorkoutSource.TCX`.
• *Given* a cursor row where `source` is `null`, empty, or unrecognized,
• *When* mapped via `readCursorSnapshot`, `fromSnapshot`, or `fromCursor(cursor, batch)`,
• *Then* `WorkoutData.source` SHALL safely evaluate to `WorkoutSource.TRACKED`.
• *Given* a batch-loaded workout with `source = WorkoutSource.TCX`,
• *When* rendered in `WorkoutHeader`,
• *Then* the header displays the origin badge (`TCX`) in Row A.

**Invariants**: Live-tracked workouts retain `TRACKED`; zero cursor seeking during batch mapping; full symmetry between single and batch mapping pathways.

### REQ-DAT-022: Google Drive Background Token Refresh, 401 Re-Authentication, Folder Hierarchy Resolution Resilience & Concurrency Synchronization [`TST-DAT-017`]
- **Tests**: `GoogleDriveAuthManager.kt`, `GoogleDriveClient.kt`, `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`, `strings.xml` (all 9 locales)
- **Implementation**: Provide automatic Bearer token renewal on HTTP 401, process-wide concurrency synchronization preventing duplicate folders, canonical Google Drive API v3 root folder creation, and actionable localized error reporting during background workout exports and backups.
- **Rationale**: The system SHALL ensure resilient folder hierarchy resolution, automatic token renewal, concurrency synchronization, and user-actionable error reporting during Google Drive background exports and backups (ATT-2631):
1. *Thread-Safe Token Renewal (`GoogleDriveAuthManager.kt`)*:
• The system SHALL provide `refreshTokenSync(context: Context): Result<String>`, clearing the expired token in Google Play Services cache via `GoogleAuthUtil.clearToken()` and acquiring a fresh Bearer token using `GoogleAuthUtil.getToken(context, account, scope)`.
• The refreshed credential SHALL be persisted into `SharedPreferences` via `TrainingApplication.storeGoogleDriveCredential()`.
• `suspend fun refreshToken(context: Context)` SHALL delegate to `refreshTokenSync(context)` on `Dispatchers.IO`.
2. *Automatic 401 Token Refresh & Request Retry (`GoogleDriveClient.kt`)*:
• `GoogleDriveClient` SHALL accept an optional `tokenRefresher: (() -> String?)? = null`.
• When an API request (`findFolderIdByName`, `createFolder`, `findFileIdByName`, `uploadOrOverwriteFile`, `downloadFileById`) receives an HTTP 401 Unauthorized status, `GoogleDriveClient` SHALL invoke `tokenRefresher()`. If a fresh token is acquired, it SHALL automatically retry the HTTP request once with the new Authorization header.
• `GoogleDriveClient` SHALL record `lastHttpCode: Int?` and `lastErrorMessage: String?` from the response body to provide transparent failure diagnostics.
3. *Process-Wide Concurrency Synchronization & Global Folder Cache (`GoogleDriveClient.kt`)*:
• `GoogleDriveClient` SHALL maintain a static `globalFolderIdCache = ConcurrentHashMap<String, String>()` and `hierarchyLock = Any()` in its companion object.
• `ensureFolderHierarchy(folderNames: List<String>)` SHALL synchronize resolution with double-checked locking over `hierarchyLock`. When concurrent WorkManager workers (e.g. TCX and FIT exports) execute in parallel, the first worker creates and caches the folders; subsequent workers immediately reuse the cached or discovered folder ID, preventing duplicate folder creations in Google Drive.
• In `findFolderIdByName`, if multiple folders with the identical name exist under `parentId`, the client SHALL reuse the first returned ID and log a warning.
4. *Canonical Google Drive API v3 Folder Creation (`GoogleDriveClient.kt`)*:
• In `createFolder(folderName: String, parentId: String)`, when `parentId == "root"` or blank, the system SHALL omit the `parents` field from the request payload, adhering strictly to Google Drive API v3 canonical root placement.
• When creating a child folder under an existing folder ID, the system SHALL pass `parents: [parentId]`.
5. *Database Backup Automatic Token Refresh (`GoogleDriveBackupManager.kt`)*:
• In `GoogleDriveBackupManager`, `clientProvider: (Context) -> GoogleDriveClient` SHALL supply `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(context).getOrNull() }`, guaranteeing database backup uploads also benefit from automatic 401 token renewal.
6. *User-Actionable Localized Error Reporting (`GoogleDriveUploader.kt`)*:
• In `GoogleDriveUploader.doExport()`, `GoogleDriveClient` SHALL be created with `tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(mContext).getOrNull() }`.
• If folder resolution fails due to unauthenticated state / token refresh failure (HTTP 401), the export SHALL return `ExportResult(false, false, mContext.getString(R.string.google_drive_error_auth_expired))`.
• If folder resolution fails due to Google Drive API disabled in Google Cloud Console (`HTTP 403` with `SERVICE_DISABLED` or `accessNotConfigured`), the export SHALL return `ExportResult(false, false, mContext.getString(R.string.google_drive_error_api_disabled))`.
• Other folder resolution failures SHALL return `mContext.getString(R.string.google_drive_error_folder_hierarchy)` with HTTP status code suffix.
7. *100% 9-Language Localization Parity (`REQ-LOC-001`)*:
• String resources `google_drive_error_auth_expired`, `google_drive_error_api_disabled`, and `google_drive_error_folder_hierarchy` SHALL be defined across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-DAT-020` (*Google Drive Integration for Automated Workout Export and Database Backup*), targeting `GoogleDriveClient.kt`, `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`, and `GoogleDriveAuthManager.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1306` (Sprint `2026-41.1`, commit `f57007ef`), revised in `ATT-2631` (Sprint `2026-41.3`).
3. *Root Reason for Existing Formulation*: On-device interactive testing revealed: (1) Google Drive API disabled in Google Cloud Console returned HTTP 403 masked as generic folder hierarchy error; (2) parallel WorkManager execution of TCX and FIT exports created duplicate `aTrainingTracker` root folders due to lack of cross-worker synchronization; (3) database backup lacked automatic token renewal.
4. *Preservation of Core Invariants*: Least-privilege `drive.file` scope, independent cloud coexistence, non-blocking coroutine execution, and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an expired Google Drive access token in `SharedPreferences`,
• *When* `GoogleDriveUploader.doExport()` runs and encounters HTTP 401,
• *Then* `GoogleDriveClient` invokes `tokenRefresher()`, acquires a fresh Bearer token, and retries the request with HTTP 200, successfully resolving the folder hierarchy.
• *Given* concurrent execution of TCX and FIT exports on a clean Google Drive,
• *When* both workers call `ensureFolderHierarchy`,
• *Then* exactly ONE `aTrainingTracker` root folder and ONE `Workouts` subfolder are created in Google Drive without duplicates.
• *Given* the Google Drive API is disabled in Google Cloud Console (HTTP 403 `SERVICE_DISABLED`),
• *When* `doExport()` fails,
• *Then* `GoogleDriveUploader` returns a localized error message (`google_drive_error_api_disabled`) guiding the user to enable the API.
• *Given* an unrecoverable auth failure (revoked account / invalid refresh),
• *When* `doExport()` fails with HTTP 401,
• *Then* `GoogleDriveUploader` returns a localized error message (`google_drive_error_auth_expired`) guiding the athlete to reconnect in Settings.

**Invariants**: Least-privilege `drive.file` scope, unlinked safety, zero duplicate folder creation, and 100% test pass rate MUST NOT be broken.

### REQ-DAT-023: Workout Route Preview Map Simplification via Douglas-Peucker & Compact Scalar Streams [`TST-DAT-018`]
- **Tests**: `LegacyImportEngine.kt`, `LiveWorkoutSession.java`, `TrackerService.java`
- **Implementation**: Prevent memory bloat and UI scroll jank in workout summary cards by simplifying raw GPS polylines with Douglas-Peucker and downsampling scalar streams, while preserving full raw curve fidelity and zero database migrations.
- **Rationale**: The system SHALL generate high-fidelity, compact workout route previews (`WorkoutSummaries.MAP_POLYLINE`) and scalar elevation/distance streams (`WorkoutSummaries.ALTITUDE_STREAM`, `WorkoutSummaries.DISTANCE_STREAM`) for both imported and live-tracked workouts (ATT-2667):
1. *Imported Workout Route Simplification (`LegacyImportEngine.kt`)*: In `recalculateStats()`, when generating `WorkoutSummaries.MAP_POLYLINE` from parsed trackpoints (`points: List<LatLng>`), the system SHALL apply the Ramer-Douglas-Peucker simplification algorithm via `PolyUtil.simplify(points, 10.0)` (10.0 meters tolerance) before encoding to polyline string. If `points` contains fewer than 3 coordinates, `PolyUtil.simplify` SHALL return `points` unchanged.
2. *Scalar Stream Downsampling (`LegacyImportEngine.kt`)*: In `recalculateStats()`, the system SHALL downsample parsed `altitudes` and `distances` using `WorkoutSummaries.ENCODING_STEP_SIZE` (20) intervals (`(index + 1) % 20 == 0`, with fallback to the last element if non-empty but fewer than 20 points) before encoding via `NumericalEncodingUtils.encodeDoubles`. The downsampled streams SHALL maintain identical sample counts and delta encoding.
3. *Live-Tracked Workout Raw Point Collection (`LiveWorkoutSession.java`)*: `LiveWorkoutSession` SHALL accumulate every valid 1 Hz GPS fix (`currentPos != null`) into an internal `rawLatLngs` collection across active session ticks. It SHALL expose `getRawLatLngs(): List<LatLng>` and `addRawLatLng(LatLng latLng)`. Incremental 20-second stream updates (`StreamIncrement`) during active tracking SHALL be preserved untouched to ensure real-time in-progress map and elevation preview compatibility.
4. *Live Session Finalization Simplification (`TrackerService.java`)*: In `finalizeLiveSession()`, the system SHALL simplify the full raw 1 Hz GPS track via `PolyUtil.simplify(mLiveSession.getRawLatLngs(), 10.0)` directly (falling back to `mLiveSession.getSampledLatLngs()` only if `rawLatLngs` is empty) and persist the resulting simplified polyline to `WorkoutSummaries.MAP_POLYLINE` in SQLite and `WorkoutRepository`.
5. *Preservation of Core Invariants*: Spatial bounding boxes (`BOUND_MIN_LAT`, `BOUND_MAX_LAT`, `BOUND_MIN_LNG`, `BOUND_MAX_LNG`) SHALL remain calculated from raw GPS coordinates to guarantee accurate zero-latency viewport framing. Raw 1 Hz telemetry samples in `WorkoutSamples` database tables MUST remain full-resolution and unaltered. Zero SQLite database schema upgrade or table rewrite is required (`DB_VERSION = 24` preserved).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-DAT-004` (*Encoded polylines and streams for storage/map*) in `docs/requirements.md`, targeting `LegacyImportEngine.kt`, `TrackerService.java`, and `LiveWorkoutSession.java`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-2667` (Sprint `2026-41.4`), building on `REQ-DAT-004` (Sprint 2015-V15).
3. *Root Reason for Existing Formulation*: Prior import logic encoded 100% of raw 1 Hz points directly to preserve raw fidelity, but caused high memory consumption, visual noise, and scrolling jank in summary preview cards. Live tracking sampled points at 20-second intervals to save storage, but time-based downsampling retained redundant collinear points and cut corners.
4. *Preservation of Core Invariants*: Raw sample storage in `WorkoutSamples` database is 100% preserved; real-time incremental 20s stream updates during active tracking are preserved; bounding box accuracy is preserved; zero database migrations or table rewrites.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an imported workout file (TCX, GPX, or FIT) with 1 Hz trackpoints (~7,000 points),
• *When* the file is imported via `LegacyImportEngine`,
• *Then* `MAP_POLYLINE` is generated from `PolyUtil.simplify(points, 10.0)`, reducing point count to ~150–350 points without losing curve fidelity.
• *Given* an active live tracking session receiving 1 Hz GPS location fixes,
• *When* the athlete completes and finalizes the workout via `TrackerService.finalizeLiveSession()`,
• *Then* the stored `MAP_POLYLINE` is simplified from the full raw GPS points (`mLiveSession.getRawLatLngs()`) using `PolyUtil.simplify(..., 10.0)` rather than simplifying the 20-second sampled points.
• *Given* an imported workout with altitude and distance data,
• *When* `recalculateStats` writes `ALTITUDE_STREAM` and `DISTANCE_STREAM` to `WorkoutSummaries`,
• *Then* the streams are sampled at `ENCODING_STEP_SIZE = 20` intervals.
• *Given* existing workouts in `WorkoutSummaries.db`,
• *When* the app runs with the new simplification logic,
• *Then* zero database schema migration or background table rewrite is triggered.

**Invariants**: SQLite schema for `WorkoutSummaries.db` remains at `DB_VERSION = 24`; raw per-second samples in `WorkoutSamplesDatabaseManager` remain untouched; real-time 20s stream incremental updates remain unaltered.

### REQ-DAT-024: Google Drive Format-Specific Directory Organization for Workout Exports and Imports (FIT, TCX, GPX, CSV, GC) [`TST-DAT-019`]
- **Tests**: `GoogleDriveUploader.kt`, `LegacyImportEngine.kt`
- **Implementation**: Organize Google Drive workout files into format-specific subdirectories (FIT, TCX, GPX, CSV, GC) matching Dropbox parity while retaining backward-compatible import discovery for legacy Workouts folder.
- **Rationale**: The system SHALL organize Google Drive workout activities into format-specific subdirectories matching their `FileFormat.dirName` and Dropbox directory parity, while preserving backward-compatible import discovery for legacy flat folders (ATT-3168):
1. *Format-Specific Destination Hierarchy (`GoogleDriveUploader.kt`)*: When exporting a workout session to Google Drive (`GoogleDriveUploader.doExport`), the system SHALL resolve the destination folder hierarchy dynamically: `listOf("aTrainingTracker", exportInfo.fileFormat.dirName)` (e.g. `["aTrainingTracker", "FIT"]`, `["aTrainingTracker", "TCX"]`, `["aTrainingTracker", "GPX"]`, `["aTrainingTracker", "CSV"]`, `["aTrainingTracker", "GC"]`). If `dirName` is null or empty, the system SHALL fall back defensively to `"Workouts"`.
2. *Format-Scoped Candidate Folder Resolution (`LegacyImportEngine.kt`)*: When initiating a format-specific bulk recovery from Google Drive (`bulkRecoverFromGoogleDrive(context, format, ...)`): (a) For `format == "fit"`, candidate folders SHALL scan `listOf("aTrainingTracker", "FIT")`, followed by legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`. (b) For `format == "tcx"`, candidate folders SHALL scan `listOf("aTrainingTracker", "TCX")`, followed by legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`. (c) For `format == "gpx"`, candidate folders SHALL scan `listOf("aTrainingTracker", "GPX")`, followed by legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`. (d) For `format == "all"`, candidate folders SHALL scan `listOf("aTrainingTracker", "FIT")`, `listOf("aTrainingTracker", "TCX")`, `listOf("aTrainingTracker", "GPX")`, followed by legacy fallback `listOf("aTrainingTracker", "Workouts")` and `listOf("aTrainingTracker")`.
3. *Cross-Directory Base Filename Deduplication*: Discovered entries across format-specific and legacy directories SHALL be deduplicated by base filename (`removeSuffix("-TMP").removeSuffix("~").lowercase()`) per `REQ-MIG-031`, guaranteeing that an activity present in both `aTrainingTracker/<FORMAT>` and `aTrainingTracker/Workouts` is imported at most once.
4. *Preservation of System Invariants*: Least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file` is strictly preserved; database backups remain strictly isolated in `aTrainingTracker/Backups/` (`GoogleDriveBackupManager.kt`); unlinked safety and 100% test pass rate are preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-DAT-020` (*Google Drive Integration for Automated Workout Export and Database Backup*) and `REQ-MIG-034` (*Google Drive Historical Workout File Discovery & Bulk Recovery*), targeting `GoogleDriveUploader.kt` and `LegacyImportEngine.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3168` (Sprint `2026-41.10`), building on `ATT-1306` (Sprint `2026-41.1`) and `ATT-2346` (Sprint `2026-41.9`).
3. *Root Reason for Existing Formulation*: Google Drive was initially designed with a flat `Workouts/` folder for activity files. Dropbox had long established format-specific subdirectories (`FIT/`, `TCX/`, `GPX/`). Unifying Google Drive to format-specific subdirectories establishes cross-cloud parity, eliminates flat directory clutter, and optimizes folder-scoped imports.
4. *Preservation of Core Invariants*: Least-privilege OAuth scope `drive.file`, database backups in `Backups/`, unlinked safety, and backward compatibility for historical workouts in `Workouts/` are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* Google Drive workout auto-export is enabled,
• *When* a workout is exported as FIT, TCX, GPX, CSV, or GC,
• *Then* `GoogleDriveUploader` calls `ensureFolderHierarchy` with `listOf("aTrainingTracker", exportInfo.fileFormat.dirName)` and uploads the file to that folder.
• *Given* Google Drive recovery initiated for format `"fit"`, `"tcx"`, or `"gpx"`,
• *When* `bulkRecoverFromGoogleDrive` executes,
• *Then* candidate folder hierarchies prioritize the format folder (`["aTrainingTracker", "<FORMAT>"]`), followed by legacy fallback `["aTrainingTracker", "Workouts"]` and `["aTrainingTracker"]`.
• *Given* workout files existing in both format-specific folders and the legacy `Workouts/` folder,
• *When* Google Drive scan runs,
• *Then* files from both locations are discovered and deduplicated by base filename without importing duplicates.

**Invariants**: Least-privilege `drive.file` scope, unlinked safety, database backups in `Backups/`, and 100% test pass rate MUST NOT be broken.

### REQ-DAT-025: Authoritative Database Cursor Row and Batch Mappers for Route and Cluster Repositories [`TST-DAT-020`]
- **Tests**: `RoutesDatabaseManager.kt`, `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterRepository.kt`
- **Implementation**: Centralize cursor-to-entity extractions into authoritative row and batch mappers with single-pass index caching, enforcing Rule 20 and Rule 36 across route and cluster repositories.
- **Rationale**: The system SHALL eliminate manual inline cursor column extractions and enforce Rule 20 (Database & DTO Mapping Symmetry) and Rule 36 (Single Source of Truth & DRY) across route and cluster persistence layers (ATT-3366):
1. *Cursor Lifecycle & Positioning Invariants*:
• Row-level mappers (`fromCursor(cursor: Cursor)`) SHALL expect a cursor already positioned at the target row. The mapper MUST NOT advance (`moveToNext`), reset (`moveToFirst`), or close (`close`) the cursor.
• Cursor lifecycle management (opening, transaction handling, and closing via `use { ... }`) remains strictly the responsibility of calling query routines.
• Batch mappers (`fromCursorBatch(cursor: Cursor): List<T>`) SHALL iterate over the cursor from `moveToFirst()` through `while (moveToNext())`, collecting mapped entities into an immutable list, leaving cursor closure to the caller's `.use { ... }` block.
2. *Routes Database Manager Mappers (`RoutesDatabaseManager.kt`)*:
• The system SHALL provide `fun mapCursorToRouteWaypoint(cursor: Cursor): RouteWaypoint`, `internal fun mapCursorToRouteWaypoint(cursor: Cursor, indices: WaypointIndices): RouteWaypoint`, and `fun mapCursorToRouteWaypoints(cursor: Cursor): List<RouteWaypoint>`.
• The system SHALL provide `fun mapCursorToPathPoint(cursor: Cursor): PathPoint`, `internal fun mapCursorToPathPoint(cursor: Cursor, indices: PathPointIndices): PathPoint`, and `fun mapCursorToPathPoints(cursor: Cursor): List<PathPoint>`.
• The system SHALL promote `mapCursorToRouteSummary(cursor: Cursor)` to `internal` and provide `fun mapCursorToRouteSummaries(cursor: Cursor): List<RouteSummary>`.
• `getWaypointsForRoute()` and `getRoutePath()` SHALL delegate strictly through these mappers with zero duplicated inline column resolutions.
3. *Index Caching Architecture*:
• Batch mappers SHALL resolve column indices exactly once per query via lightweight immutable index cache holders (`WaypointIndices`, `PathPointIndices`), passing them to indexed row mappers to eliminate $O(N \times K)$ JNI `getColumnIndexOrThrow` invocations inside row loops.
4. *Workout Cluster Database Manager & Repository (`WorkoutClusterDatabaseManager.kt`, `WorkoutClusterRepository.kt`)*:
• `WorkoutClusterDatabaseManager` SHALL expose `fromCursor(cursor: Cursor): WorkoutCluster` and `fromCursorBatch(cursor: Cursor): List<WorkoutCluster>`.
• `WorkoutClusterRepository.loadWorkoutsWithBatchMetadata` SHALL extract batch keys via `fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys`, resolving column indices (`C_ID`, `FILE_BASE_NAME`, `CLUSTER_ID`) once and populating primitive/string collections directly without intermediate wrapper object allocation per row.
• `WorkoutBatchKey.fromCursor(cursor: Cursor)` SHALL be provided for single-row mapping symmetry.
5. *Preservation of Domain Invariants*:
• `WaypointType` fallbacks (defaulting unparseable strings to `GENERIC`), altitude defaulting to `0.0` when null, and null-safe bounding coordinate handling MUST be preserved 100%.
• SQLite schemas (`Routes.db`, `WorkoutCluster.db`) MUST remain untouched.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-DAT-025`) complementing `REQ-DAT-021`, `REQ-SET-062`, `REQ-SET-068`, and `REQ-MAP-026`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.2 (`RoutesDatabaseManager.kt`) and Sprint 2026-40.12 (`WorkoutClusterRepository.kt`).
3. *Root Reason for Existing Formulation*: Query methods were written iteratively, leaving inline cursor column extractions duplicated across query loops rather than centralized in canonical mapper contracts.
4. *Preservation of Core Invariants*: Waypoint fallback parsing, altitude nullability defaults, cursor positioning ownership, and database schemas remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a SQLite cursor positioned at a waypoint row,
• *When* `mapCursorToRouteWaypoint` is called,
• *Then* all fields are extracted correctly without altering cursor position or closing the cursor.
• *Given* a cursor row with an unknown or invalid `type` string,
• *When* mapped to `RouteWaypoint`,
• *Then* `type` evaluates safely to `WaypointType.GENERIC`.
• *Given* a cursor row with null elevation,
• *When* mapped to `RouteWaypoint`,
• *Then* `altitude` evaluates to `0.0`.
• *Given* a multi-row waypoint cursor,
• *When* `mapCursorToRouteWaypoints` is called,
• *Then* column indices are resolved once and all rows are mapped into a `List<RouteWaypoint>`.
• *Given* a multi-row path point cursor,
• *When* `mapCursorToPathPoints` is called,
• *Then* all rows are mapped into a `List<PathPoint>`.
• *Given* a cursor querying workout summaries for batch metadata,
• *When* `WorkoutClusterRepository.fromCursorBatchKeys` is executed,
• *Then* column indices are resolved once and `idList`, `fileNameList`, and `clusterIdList` are populated accurately.

**Invariants**: Zero database schema alterations; strict cursor lifecycle ownership; 100% unit test pass rate.

### REQ-MIG-001: State Bundling [Proposed]
- **Tests**: `TST-MIG-001`
- **Implementation**: `BackupManager`
- **Rationale**: The system SHALL be able to bundle all internal SQLite databases (including high-fidelity samples) and shared preferences into a single portable archive (`.attbackup`).

### REQ-MIG-002: Multi-Destination Backup [Proposed]
- **Tests**: `TST-MIG-001`
- **Implementation**: `BackupManager`, `DropboxIntegration`
- **Rationale**: The system SHALL support both manual backup (via Android SAF) and direct upload to a linked Dropbox account.

### REQ-MIG-003: Regular Cloud Backups [Proposed]
- **Tests**: `TST-MIG-003`
- **Implementation**: `BackupWorkManager`
- **Rationale**: The system SHALL optionally perform automated background backups to Dropbox to ensure disaster recovery (e.g., lost/stolen device).

### REQ-MIG-004: Full Migration Mode [Proposed]
- **Tests**: `TST-MIG-001`
- **Implementation**: `MigrationEngine`
- **Rationale**: The system SHALL support a "Full Restore" mode that replaces the entire local state with a backup, followed by a forced app restart to ensure consistency.

### REQ-MIG-005: Incremental Import [Proposed]
- **Tests**: `TST-MIG-002`
- **Implementation**: `ImportEngine.kt`, `LegacyImportEngine.kt`
- **Rationale**: The system SHALL support merging backups into an existing database. It MUST perform deduplication using the `FILE_BASE_NAME` as the unique identifier. Legacy recovery SHALL also skip files that already exist in the database.

### REQ-MIG-013: Idempotent Recovery [Verified]
- **Tests**: `TST-MIG-010`
- **Implementation**: `LegacyImportEngine.kt`
- **Rationale**: The legacy recovery process SHALL NOT trigger clustering prompts for workouts that are already present in the database or already have a cluster assigned.

### REQ-MIG-014: Queued User Interaction [Verified]
- **Tests**: `TST-MIG-017`
- **Implementation**: `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`
- **Rationale**: The system SHALL decouple user interaction requests (e.g., cluster naming) from background file importing. While an interaction is pending user resolution, the TCX import engine SHALL continue downloading and processing subsequent files concurrently in the background, queuing up to 3 pending user interactions without blocking progress. **Invariants**: Progress indicators and status updates MUST remain visible during background importing.

### REQ-MIG-006: State Lockdown [Proposed]
- **Tests**: `TST-MIG-001`
- **Implementation**: `MigrationUI`
- **Rationale**: Backup and Restore operations SHALL ONLY be allowed when the system is in a non-tracking state to prevent data corruption.

### REQ-MIG-007: Full Restore Confirmation [Proposed]
- **Tests**: `TST-MIG-004`
- **Implementation**: `BackupRestoreScreen.kt`
- **Rationale**: The system SHALL require explicit user confirmation before initiating a "Full Restore" from Dropbox.

### REQ-MIG-008: Informative Dashboard Labels [Proposed]
- **Tests**: `TST-MIG-005`
- **Implementation**: `BackupRestoreScreen.kt`
- **Rationale**: The Backup \u0026 Restore dashboard SHALL provide clear descriptive text for all operations. The Dropbox backup button SHALL be labeled \"Create \u0026 Upload Backup to Dropbox\". Destructive and merging sections SHALL feature high-level descriptions explaining their impact on local data.

### REQ-MIG-011: Tabbed Import \u0026 Backup UI [Verified]
- **Tests**: `TST-MIG-008`
- **Implementation**: `ImportBackupTabsScreen.kt`
- **Rationale**: The system's data management interface SHALL be organized into a three-tabbed layout: Import, Backup, and Restore. This layout SHALL use the project's standard `HorizontalPager` pattern for consistent navigation. The screen SHALL be prominently labeled \"Import \u0026 Backup\".

### REQ-MIG-012: Responsive Pre-Import Workout Cluster Tuning Bottom Sheet [Verified]
- **Tests**: `TST-MIG-009`
- **Implementation**: `ImportBackupTabsScreen.kt`, `ClusterTuningScreen.kt`, `ClusterInfoDialog.kt`, `BackupRestoreViewModel.kt`
- **Rationale**: The system SHALL provide access to workout clustering parameters (spatial tolerances, sport type firewall, and altitude position extrema) within an interactive Material 3 `ModalBottomSheet` prior to executing a bulk or single-file TCX workout import. The bottom sheet SHALL span the available screen width and dynamically adapt vertically, displaying the title (`cluster_tuning_title`), an integrated educational Info button (`Icons.Outlined.Info`) triggering the `ClusterInfoDialog`, explanatory guidance, and a scrollable `ClusterTuningContent` layout. The bottom sheet SHALL provide explicit confirmation and dismissal actions.

**Acceptance Criteria (Given-When-Then)**:
1. *Given* a single legacy TCX import or bulk TCX archive recovery is triggered from the Import tab,
2. *When* the pre-import tuning interface is displayed,
3. *Then* it SHALL render as a Material 3 `ModalBottomSheet` with a drag handle, header title, Info icon button, and scrollable controls.
4. *When* the user taps the Info button in the header,
5. *Then* the system SHALL present the `ClusterInfoDialog` on top without dismissing the tuning sheet.
6. *When* the user adjusts sensitivity controls and confirms via OK,
7. *Then* the system MUST persist the selected tolerances to `SharedPreferences` and commence file importing.

**Invariants**: User-selected tolerances MUST be persisted to shared preferences upon confirmation. The underlying mathematical clustering engine, Room database entities, and file import workflows MUST remain unaltered.

### REQ-MIG-009: Default Backup Interval [Verified]
- **Tests**: `TST-MIG-006`
- **Implementation**: `prefs_dropbox.xml`, `BackupWorker.kt`
- **Rationale**: The system SHALL default the automated backup interval to 1 day (Daily) to maximize data safety for new users.

### REQ-MIG-010: Enable Backups by Default [Verified]
- **Tests**: `TST-MIG-007`
- **Implementation**: `prefs_dropbox.xml`, `BackupWorker.kt`
- **Rationale**: The system SHALL enable the automated backup feature by default for new installations.

### REQ-MIG-015: Efficient Legacy Recovery [Verified]
- **Tests**: `TST-MIG-011`
- **Implementation**: `LegacyImportEngine.kt`
- **Rationale**: The legacy recovery process SHALL skip already imported workouts BEFORE downloading them from remote storage to ensure optimal performance and bandwidth usage.

### REQ-MIG-016: Paginated & Recursive Cloud Recovery [Verified]
- **Tests**: `TST-MIG-018`
- **Implementation**: `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`
- **Rationale**: The system SHALL robustly scan and paginate cloud storage paths (e.g., `/TCX`, `/apps/Workouts/TCX` and their subdirectories) using `.withRecursive(true)` and `listFolderContinue()` with error recovery to discover all TCX workout files regardless of folder size. **Invariants**: Deduplication (`isWorkoutExisting`) MUST continue using `FILE_BASE_NAME` as the unique identifier.

### REQ-MIG-021: Informative Import Summary [Verified]
- **Tests**: `TST-MIG-018`
- **Implementation**: `BackupRestoreViewModel.kt`, `LegacyImportEngine.kt`, `strings.xml`
- **Rationale**: Upon completing a bulk import, the system SHALL display a detailed summary stating the number of newly imported workouts, the number of skipped/already existing workouts, and the total number of files scanned. **Invariants**: Success and error messages MUST be accurately formatted.

### REQ-MIG-022: Preserve Original Filename During Import [Verified]
- **Tests**: `TST-MIG-019`
- **Implementation**: `LegacyImportEngine.kt`
- **Rationale**: The TCX import engine SHALL preserve the original source filename when storing `FILE_BASE_NAME` and `WORKOUT_NAME` in the database. Temporary download files and worker subdirectories MUST NOT modify, prefix, or append counters to the source filename. **Invariants**: `WorkoutSummaries.FILE_BASE_NAME` MUST exactly equal the source file name without extension.

### REQ-MIG-023: Unlinked Cloud Feature Deactivation [Verified]
- **Tests**: `TST-MIG-020`
- **Implementation**: `ImportBackupTabsScreen.kt`, `TrainingApplication.java`, `BackupRestoreViewModel.kt`, `DropboxUploader.java`
- **Rationale**: When the user is not authenticated or connected to Dropbox, all cloud-dependent features (including scanning/importing legacy TCX files, cloud backups, and cloud restores) SHALL be visually deactivated in the UI (rendered in a muted/grey state). Clicking a deactivated cloud action SHALL present an informational dialog explaining that Dropbox is disconnected. **Invariants**: Credential reading and background jobs MUST NOT throw null pointer exceptions when unlinked.

### REQ-MIG-017: Limited Interaction Queue & Backpressure [Verified]
- **Tests**: `TST-MIG-017`
- **Implementation**: `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`
- **Rationale**: The system SHALL limit the number of concurrent pending UI interactions to a maximum of 3 using a semaphore (`interactionSemaphore(3)`). The background import engine MUST ONLY suspend file downloading and parsing when 3 candidate interactions are active in `_interactionQueue`, automatically resuming as the user resolves queued items. **Invariants**: Queue size MUST NOT exceed 3 items.

### REQ-MIG-018: Sparse Data Import [Verified]
- **Tests**: `TST-MIG-014`
- **Implementation**: `LegacyImportEngine.kt`, `WorkoutSamplesDatabaseManager.java`
- **Rationale**: The TCX import engine SHALL dynamically detect available sensors and only create database columns and summary metrics for telemetry actually present in the source file, preventing the population of zero-filled streams for missing data.

### REQ-MIG-019: Dropbox Wait Feedback [Verified]
- **Tests**: `TST-MIG-015`
- **Implementation**: `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`
- **Rationale**: The TCX import process SHALL provide explicit visual feedback (e.g., status message) when the system is waiting for a response from the Dropbox API (e.g., during folder scanning or file downloading) to ensure the user is aware of the external dependency.

### REQ-MIG-020: Positive Import Branding [Verified]
- **Tests**: `TST-MIG-016`
- **Implementation**: `ImportBackupTabsScreen.kt`, `strings.xml`
- **Rationale**: The workout import feature (formerly Legacy Recovery) SHALL be presented as a first-class feature for migrating data from previous devices. Terminology SHALL avoid negative or technical qualifiers like \"Legacy\" or \"Recovery\" and instead use active, feature-oriented language. Redundant introduction text SHALL be removed from the tabbed interface.

### REQ-MIG-024: High-Fidelity TCX Telemetry & Multi-Lap Structure Import [`TST-MIG-021`]
- **Tests**: `LegacyImportEngine.kt`, `LapsDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`
- **Implementation**: Ensure imported historical workouts match the analytical fidelity and segment granularity of native live recordings, avoiding loss of sensor extensions, split intervals, and activity metadata.
- **Rationale**: The TCX import engine (`LegacyImportEngine`) SHALL parse and persist high-fidelity telemetry and multi-lap structures from TCX activity files:
1. *Trackpoint Extensions (TPX)*: The engine SHALL parse Garmin TrackpointExtension v2 elements, extracting instantaneous speed (`<Speed>` in m/s) and running cadence (`<RunCadence>` in RPM/spm) when present, populating `SensorType.SPEED` and `SensorType.CADENCE` respectively without falling back to lossy delta approximations.
2. *Multi-Lap Preservation*: The engine SHALL preserve all native `<Lap>` elements across the activity. For each `<Lap>`, it SHALL persist a distinct lap record in `LapsDatabaseManager` recording the lap's `lap_nr`, `start_time`, `duration` (`<TotalTimeSeconds>`), `distance` (`<DistanceMeters>`), `avg_speed`, `max_speed` (`<MaximumSpeed>`), and `avg_hr` (`<AverageHeartRateBpm>`). It SHALL assign each buffered trackpoint sample the corresponding `SensorType.LAP_NR` value matching its parent lap.
3. *Active Duration & Pause Derivation*: The engine SHALL derive the total active workout duration by summing `<TotalTimeSeconds>` across all laps, ensuring paused or discontinuous intervals do not artificially inflate active workout time in `WorkoutSummaries`.
4. *Supplementary Metrics & Metadata*: The engine SHALL parse `<Calories>` per lap and record total aggregated calories burned in `WorkoutSummaries`. It SHALL extract activity `<Notes>` or `<Name>` and populate the workout's notes/comments field in the database.
**Acceptance Criteria**:
• *Given* a multi-lap TCX file containing TPX extension speed and running cadence, calories, and pause gaps between laps,
• *When* the workout is imported via `LegacyImportEngine.importFromTcx()`,
• *Then* `LapsDatabaseManager` SHALL contain separate rows for each lap with valid metrics, `WorkoutSamplesDatabaseManager` SHALL contain `SPEED`, `CADENCE`, and accurate `LAP_NR` columns, and `WorkoutSummaries` SHALL record the true active duration, total calories, and notes.
**Invariants**: Sparse stream detection (`REQ-MIG-018`), automated Strava upload triggering (`REQ-EXT-008`), and original file base name preservation (`REQ-MIG-022`) MUST NOT be altered or regressed.

### REQ-MIG-025: TCX Import Cluster Decision Integrity & Unclustered Workout Preservation [`TST-MIG-022`]
- **Tests**: `LegacyImportEngine.kt`, `WorkoutClusterEngine.kt`, `WorkoutRepository.kt`
- **Implementation**: Prevent unwanted cluster creation and preserve user sovereignty over workout categorization during TCX file imports.
- **Rationale**: The system SHALL strictly respect user cluster decisions when importing workouts from TCX or external files:
1. *User Decision Sovereignty*: When a user dismisses or chooses "Leave Unclustered" (`@string/cluster_naming__leave_unclustered`) in `ClusterNamingDialog`, the workout SHALL remain unclustered in the database with `WorkoutSummaries.CLUSTER_ID = -1L`.
2. *Prohibition of Auto-Clustering*: The system MUST NOT automatically create a new `WorkoutCluster` or assign any cluster to a workout that has been explicitly left unclustered or has `clusterId == -1L`. Specifically, `WorkoutClusterEngine.onWorkoutFinished()` MUST NOT generate fallback clusters (e.g. `@string/cluster_default_name_format`, "Workout at %1$s") or assign clusters to workouts where `w.clusterId == -1L`.
3. *Import vs. Live Tracking Lifecycle Disambiguation*: When `WorkoutRepository.reloadWorkoutData()` processes a newly loaded or imported workout, it MUST NOT invoke live cluster creation or discovery logic on workouts whose clustering was already finalized or deliberately rejected during import.
4. *Name & State Preservation*: The imported workout SHALL preserve its original name and file base name, and SHALL appear in the "Unclustered" tab of the Favorite Tracks / Workout Clusters view (`WorkoutClustersTabsScreen`) and in the workout summaries list without an auto-generated cluster name prefix or suffix.
**Acceptance Criteria**:
• *Given* a TCX workout imported via `LegacyImportEngine` that does not match an existing cluster,
• *When* the user selects "Leave Unclustered" in `ClusterNamingDialog`,
• *Then* the workout SHALL be saved with `clusterId = -1L`, NO new cluster SHALL be created in `WorkoutClusterDatabaseManager`, the workout name SHALL NOT be altered to a cluster default name, and the workout SHALL appear under the Unclustered tab.
**Invariants**: User-confirmed cluster assignments during TCX import (selecting an existing cluster or providing a custom route name) MUST continue to link the cluster and increment its hit count; live tracking sessions completed via `TrackerService` MUST continue to update cluster metrics and spatial bounds when a cluster match is found; periods aggregation in `PeriodsRepository` MUST continue to process all newly imported and finished workouts; existing SQLite database schema for `WorkoutSummaries` and `WorkoutCluster` MUST NOT be altered.

### REQ-MIG-026: TCX Import Reactive Period Aggregation, Direct Pipeline Notification & Self-Healing Period Sync [`TST-MIG-023`]
- **Tests**: `LegacyImportEngine.kt`, `WorkoutRepository.kt`, `PeriodsRepository.kt`, `PeriodSummariesDatabaseManager.kt`, `BackupRestoreViewModel.kt`
- **Implementation**: Guarantee that imported workouts are fully integrated into temporal analytics and period summaries regardless of repository instantiation timing or background import mode.
- **Rationale**: The system SHALL ensure that all workouts imported from TCX (single file or cloud bulk recovery) immediately and deterministically appear in the Workout Periods analytics views (Days, Weeks, Months, Years):
1. *Direct Repository Notification Pipeline*: Upon completing statistical recalculation for an imported workout (`recalculateStats`), `LegacyImportEngine` SHALL NOT rely exclusively on unbuffered `LocalBroadcastManager` broadcasts. It SHALL directly invoke `WorkoutRepository.getInstance(app).reloadWorkoutData(workoutId)` and ensure `PeriodsRepository.getInstance(app).onWorkoutFinished(freshWorkoutData)` executes to aggregate the session into its Day period and propagate metrics upward (Day -> Week/Month -> Year).
2. *Self-Healing Period Integrity Check*: `PeriodsRepository` SHALL implement an $O(1)$ fast integrity check comparing the total count of finished workouts in `WorkoutSummaries` against the sum of `total_workouts` across all `DAY` periods in `PeriodSummaries.db`. If a discrepancy is detected (e.g., following background imports, process death, or database restoration), `PeriodsRepository` SHALL automatically trigger asynchronous hierarchical re-synchronization (`performHierarchicalMigration()`), guaranteeing that no imported sessions remain omitted from historical period cards, charts, and heatmaps.
3. *Post-Import Batch Reconciliation*: Upon completion of single-file import or bulk recovery in `BackupRestoreViewModel`, the system SHALL explicitly trigger `periodsRepo.loadPeriods()`, `workoutRepo.loadAllWorkouts()`, and `clusterRepo.refreshClusters()`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an app state where `WorkoutRepository` is not pre-instantiated in memory,
• *When* a historical TCX workout is imported via `LegacyImportEngine`,
• *Then* `PeriodsRepository.onWorkoutFinished()` SHALL be invoked, creating or updating the Day, Week, Month, and Year periods corresponding to the workout's timestamp.
• *Given* an out-of-sync state where `WorkoutSummaries` contains finished workouts lacking entries in `PeriodSummaries.db`,
• *When* `PeriodsRepository` initializes or loads from database,
• *Then* the self-healing count check SHALL detect the discrepancy and trigger `performHierarchicalMigration()`, reconciling all missing periods.

**Invariants**: Existing date grouping logic (`getPeriodSortKey`), spatial boundary aggregation, and O(1) live tracking completion rollups MUST NOT be altered.

### REQ-MIG-027: TCX Import Cluster Assignment, Multi-Sport Heuristics & In-Memory Cache Refresh [`TST-MIG-024`]
- **Tests**: `LegacyImportEngine.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`, `BackupRestoreViewModel.kt`
- **Implementation**: Ensure TCX imports reliably associate with existing favorite routes and keep cluster presentations up-to-date across all screens.
- **Rationale**: The system SHALL ensure that workouts imported from TCX are accurately evaluated against route clusters without false rejections due to unmapped sports, and that cluster state is reactively reflected in the UI:
1. *Multi-Sport & Unknown Sport Inference*: When evaluating cluster candidates in `LegacyImportEngine` (`suggestCluster`), if `bSportType == BSportType.UNKNOWN`, the engine MUST NOT pass `setOf(BSportType.UNKNOWN)` (which incurs a punitive `+5.0` penalty in `calculateSimilarity`). Instead, it SHALL evaluate candidate sports using `EquipmentAndSportTypeDiscoveryManager.getCandidateBSportTypes(bSportType, avgSpeed)` or pass `emptySet()` to enable multi-sport candidate evaluation.
2. *Authoritative Workout Name Heuristic*: `LegacyImportEngine` SHALL pass the extracted `workoutName` (from TCX notes or activity extensions) into `suggestCluster` to enable the 50% similarity bonus (`totalScore *= 0.5`) when the workout name matches an existing cluster name.
3. *Sport Column Persistence*: `LegacyImportEngine` SHALL persist `WorkoutSummaries.B_SPORT` (`bSportType.name()`) alongside `SPORT_ID` in `WorkoutSummaries`.
4. *Reactive In-Memory Cache Refresh*: Whenever a workout is assigned to an existing cluster or seeded into a new cluster during import, `LegacyImportEngine` / `BackupRestoreViewModel` SHALL invoke `WorkoutClusterRepository.getInstance(app).refreshClusters()`, ensuring `allClusters`, `clusterStats`, and `unclusteredWorkouts` in `WorkoutClustersViewModel` update immediately without requiring an application restart.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a TCX workout whose sport attribute is missing or maps to `UNKNOWN`, but whose geometry and speed match an existing `BIKE` or `RUN` cluster,
• *When* imported via `LegacyImportEngine`,
• *Then* the cluster engine SHALL suggest the matching cluster without the `+5.0` penalty, and assign it to the workout.
• *Given* a TCX workout with a name matching an existing cluster (e.g. \"Hausrunde\"),
• *When* evaluated for clustering,
• *Then* the 50% name-match similarity discount SHALL be applied.
• *Given* a cluster assignment during TCX import,
• *When* import completes,
• *Then* `WorkoutClusterRepository.refreshClusters()` SHALL be called, updating the cluster's hit count and previews in memory.

**Invariants**: User decision sovereignty (`REQ-MIG-025`, \"Leave Unclustered\" remains `clusterId = -1`) MUST be preserved; existing spatial tolerances (endpoints, apex, distance, altitude) MUST NOT be altered.

### REQ-MIG-028: Optional Strava Upload on TCX Import [`TST-MIG-025`]
- **Tests**: `LegacyImportEngine.kt`, `TrainingApplication.java`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`
- **Implementation**: Prevent unwanted historical Strava activity flooding, duplicate activities, and API quota exhaustion during single-file or bulk TCX import while giving athletes explicit opt-in agency.
- **Rationale**: The system SHALL allow athletes to choose whether workouts imported from TCX (via single file import or cloud bulk recovery) are automatically uploaded to Strava:
1. *Persistent Configuration*: The system SHALL provide a persistent configuration setting (`SP_IMPORT_TCX_UPLOAD_TO_STRAVA`) in `TrainingApplication` / `SharedPreferences` indicating whether imported workouts should be uploaded to Strava. To prevent unintended historical feed flooding and Strava API quota exhaustion, this setting SHALL default to `false`.
2. *Pre-Import User Control*: Within the pre-import configuration sheet (`PreImportTuningBottomSheet`), when Strava is connected (`TrainingApplication.uploadToStrava() == true`), the system SHALL render an interactive toggle switch allowing the athlete to enable or disable Strava upload for the impending import. Toggling the switch SHALL immediately update the persistent setting. When Strava is not connected, the toggle switch SHALL NOT be displayed.
3. *Engine Parameterization*: The TCX import engine (`LegacyImportEngine.importFromTcx()` and `LegacyImportEngine.bulkRecoverFromDropbox()`) SHALL accept an explicit `uploadToStrava` parameter defaulting to `TrainingApplication.uploadImportedWorkoutsToStrava()`.
4. *Database Flag & Upload Dispatch*: When `uploadToStrava` is `false` (or Strava is not connected), the import engine SHALL insert the workout with `WorkoutSummaries.UPLOAD_TO_STRAVA = 0`, and `LegacyImportEngine.schedulePostImportCommunityUpload()` SHALL bypass Strava upload. When `uploadToStrava` is `true` and Strava is connected, the engine SHALL insert `WorkoutSummaries.UPLOAD_TO_STRAVA = 1` and schedule post-import upload via `ExportManager`.
5. *Manual Upload Availability*: Workouts imported with Strava upload disabled SHALL remain eligible for subsequent manual upload via the Workout Summary or Edit Workout screens.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with Strava connected who imports a TCX workout with Strava upload toggled off (default),
• *When* `LegacyImportEngine.importFromTcx()` executes,
• *Then* `WorkoutSummaries.UPLOAD_TO_STRAVA` SHALL be 0, and NO upload task SHALL be enqueued to `ExportManager` or Strava WorkManager.
• *Given* an athlete with Strava connected who toggles Strava upload ON in the pre-import bottom sheet,
• *When* the TCX import executes,
• *Then* `WorkoutSummaries.UPLOAD_TO_STRAVA` SHALL be 1, and an upload task SHALL be enqueued to `ExportManager` for Strava.
• *Given* an athlete whose Strava account is disconnected,
• *When* the pre-import bottom sheet is presented,
• *Then* the Strava upload toggle switch SHALL NOT be visible.

**Invariants**: Live tracking sessions (`TrackerService`) upload lifecycle MUST NOT be affected; manual upload capability in `WorkoutSummary` and `EditWorkoutScreen` MUST remain fully functional; existing TCX parsing, metric calculations, and cluster heuristics MUST NOT be altered.

### REQ-MIG-029: TCX Import & Spatial Route Cluster Auto-Matching Parity & Lossless Candidate Discovery [`TST-MIG-026`]
- **Tests**: `WorkoutClusterEngine.kt`, `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`
- **Implementation**: Eliminate repetitive, false manual cluster selection prompts during historical TCX file import when existing route clusters match the activity within the configured similarity threshold.
- **Rationale**: The system SHALL guarantee lossless candidate evaluation and scoring parity between automated TCX import cluster matching and interactive route cluster selection:
1. *Lossless Candidate Discovery in `suggestCluster`*: `WorkoutClusterEngine.suggestCluster()` SHALL NOT prune candidate clusters using restrictive bounding box filters that prematurely discard valid matches whose composite similarity score is $< 1.0$. Specifically, `suggestCluster()` SHALL evaluate candidate similarity across all available route clusters (`dbManager.getAllClusters()`), guaranteeing that single-dimension deviations (such as start point displacement $> 1.0 \times \text{endpointTol}$, e.g. 250m–400m) are not pruned before their multi-component composite score is evaluated.
2. *Full Parameter Alignment between Auto-Matching and Interactive Dialog*: The TCX import pipeline (`LegacyImportEngine.kt`), interaction queue (`BackupRestoreViewModel.ClusterInteraction`), and cluster selection interface (`ClusterNamingDialog` / `WorkoutClusterSelectionDialog`) SHALL maintain complete parameter symmetry when invoking `calculateSimilarity()` / `scoreClusters()`. Specifically, `onNewClusterCandidate()` and `ClusterInteraction` SHALL preserve `workoutName`, `candidateSportTypes`, `minAltPos`, and `maxAltPos`. When `ClusterNamingDialog` renders the candidate route picker (`showSelectionDialog = true`), it SHALL pass `state.workoutName`, `state.candidateSportTypes`, `state.minAltPos`, and `state.maxAltPos` into `clusterEngine.scoreClusters()`.
3. *Deterministic Auto-Matching Invariant*: If an imported workout matches an existing route cluster with a composite similarity score $< 1.0$, `suggestCluster()` SHALL deterministically identify and auto-assign the best matching cluster (`minByOrNull { it.second }`) without presenting `ClusterNamingDialog` to the user. Conversely, `ClusterNamingDialog` SHALL only be presented when no existing cluster achieves a similarity score $< 1.0$.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an existing route cluster in the database with start point $S_{cluster}$ and an imported TCX workout whose start point $S_{workout}$ has a displacement of 350m ($> \text{endpointTol}$ default 200m), but whose overall route geometry, apex, and distance yield a composite similarity score $< 1.0$ (e.g. 0.55),
• *When* the TCX workout is imported via `LegacyImportEngine.importFromTcx()`,
• *Then* `suggestCluster()` SHALL evaluate the cluster without premature SQLite pruning, identify it as a match, and auto-assign it without prompting the user in `ClusterNamingDialog`.
• *Given* an imported workout whose spatial signature fails to match any existing cluster (all similarity scores $\ge 1.0$),
• *When* `ClusterNamingDialog` is presented and the user opens the existing route selection dialog,
• *Then* every cluster score displayed in the picker SHALL be $\ge 1.0$, matching the exact parameters (`workoutName`, `candidateSportTypes`, `minAltPos`, `maxAltPos`) evaluated during import.

**Invariants**: User decision sovereignty (`REQ-MIG-025`, "Leave Unclustered" remaining `clusterId = -1L`) MUST be preserved; existing 2D and 3D altitude similarity scoring formulas (`REQ-SET-065`) MUST NOT be altered; live workout tracking completion matching in `TrackerService` MUST NOT be degraded.

### REQ-MIG-030: High-Fidelity GPX Workout Import & Full Telemetry Ingestion Parity [`TST-MIG-027`]
- **Tests**: `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`
- **Implementation**: Enable athletes to import their historical activities from GPX files with full telemetry, lap fidelity, and automatic route clustering.
- **Rationale**: The system SHALL support importing historical workouts from GPX files (single file via file picker and cloud bulk recovery from Dropbox), matching the analytical fidelity, cluster routing, and period aggregation of the TCX import pipeline:
1. *Spatial & Temporal Parsing*: Parse `<trk>` / `<trkseg>` / `<trkpt>` (and fallback `<rte>` / `<rtept>`) elements, extracting latitude (`lat`), longitude (`lon`), elevation (`<ele>`), and UTC timestamps (`<time>`). Compute cumulative geodesic distance (`SensorType.DISTANCE_m`) between consecutive GPS coordinates.
2. *Extended Telemetry (Garmin TPX & Extensions)*: Parse extension telemetry when present, including heart rate (`<gpxtpx:hr>`, `<hr>`, `<heartrate>`), cadence (`<gpxtpx:cad>`, `<cad>`, `<cadence>`), speed (`<gpxtpx:speed>`, `<speed>` in m/s), temperature (`<gpxtpx:atemp>`, `<atemp>`), and power (`<power>`, `<watts>`), populating the corresponding `SensorType` columns without creating zero-filled streams for absent sensors (`REQ-MIG-018`).
3. *Multi-Segment / Multi-Lap Structure*: When a GPX file contains multiple `<trkseg>` elements, the engine SHALL preserve each segment as a distinct lap in `LapsDatabaseManager` and tag buffered samples with `SensorType.LAP_NR`.
4. *Sport Type Detection & Heuristics*: Determine sport type from `<trk><type>`, `<name>`, or `<desc>` (e.g. "Running" -> `BSportType.RUN`, "Cycling"/"Biking"/"Ride" -> `BSportType.BIKE`, etc.), falling back to `BSportType.UNKNOWN` and multi-sport / speed-based heuristics (`REQ-MIG-027`).
5. *Activity Metadata & Naming*: Extract workout name from `<trk><name>` or `<metadata><name>` (or file base name if absent) and activity description/notes from `<trk><desc>` or `<metadata><desc>`.
6. *Pipeline Parity with TCX*: Deduplicate via `FILE_BASE_NAME` (`isWorkoutExisting`), evaluate route clustering (`suggestCluster`), sync periods (`PeriodsRepository`), and respect optional Strava upload configuration (`REQ-MIG-028`).
7. *UI Integration*: Single file import in `ImportBackupTabsScreen` SHALL accept `.gpx` files alongside `.tcx` files seamlessly, and Dropbox recovery SHALL support scanning and importing GPX archives.

### REQ-MIG-031: Multi-Dimensional Workout Deduplication & Concurrent Worker Race Condition Guard in TCX/GPX Import with Extended 3-Minute Same-Sport Tolerance Window (Option 2) [`TST-MIG-028`]
- **Tests**: `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`
- **Implementation**: Prevent duplicate database entries during TCX/GPX import by extending start-time deduplication to a 3-minute same-sport tolerance window (Option 2) alongside mutex concurrency guards and pre-dispatch filtering.
- **Rationale**: The system SHALL prevent duplicate imports of workouts across single-file and cloud bulk recovery flows through multi-dimensional deduplication, atomic synchronization, pre-dispatch filtering, and an extended same-sport start-time tolerance window (ATT-2023 Revision 2):
1. *Extended Same-Sport Tolerance Window (`isWorkoutExisting` in `LegacyImportEngine.kt`)*:
• `isWorkoutExisting` SHALL accept optional parameter `bSportType: BSportType? = null`.
• When `bSportType` is non-null and not `BSportType.UNKNOWN`, the query SHALL evaluate: `${WorkoutSummaries.FILE_BASE_NAME} = ? OR ${WorkoutSummaries.TIME_START} = ? OR (${WorkoutSummaries.B_SPORT} = ? AND ${WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)`.
• When `bSportType` is null or `BSportType.UNKNOWN`, the query SHALL evaluate: `${WorkoutSummaries.FILE_BASE_NAME} = ? OR ${WorkoutSummaries.TIME_START} = ? OR (${WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)`.
• The tolerance threshold SHALL be $180\text{ seconds}$ ($3\text{ minutes}$), reliably preventing dual-phone recordings (e.g. `2015-06-09_065716.tcx` vs `2015-06-09_065821.tcx` with a 65-second offset) from creating duplicate workout records.
2. *Caller Integration (`importFromTcxInternal` & `importFromGpxInternal`)*:
• In `importFromTcxInternal` and `importFromGpxInternal`, `isWorkoutExisting` SHALL be invoked with parsed `bSportType` inside `importMutex.withLock`.
• When `isWorkoutExisting` returns `true`, the candidate file SHALL be skipped without creating sample tables or inserting summary records, returning `ImportStatus.DUPLICATE_SKIPPED`.
3. *Atomic Synchronization & Pre-Dispatch Queue Deduplication*:
• The atomic coroutine `Mutex` (`importMutex`) SHALL continue to guard table creation and database insertion against race conditions across concurrent workers.
• Cloud bulk recovery (`bulkRecoverFromDropbox`) SHALL continue to deduplicate file entries by normalized base name prior to worker queuing.
4. *Accurate Reporting*:
• Skipped duplicate files SHALL increment `skippedCount`, accurately reflecting deduplicated sessions in the final import summary.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-MIG-031`), expanding `REQ-MIG-005` (*Incremental Import*) and `REQ-MIG-016` (*Paginated & Recursive Cloud Recovery*), targeting `LegacyImportEngine.kt` for Epic `ATT-529` (*Import TCX Files*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.12 (`ATT-2023`). Revision 1 introduced a 30s window which missed dual-device recordings offset by 65s.
3. *Root Reason for Existing Formulation*: Athletes occasionally record workouts with backup devices or two phones simultaneously, starting 1–2 minutes apart. The 30s window was insufficient to capture these real-world dual recordings.
4. *Preservation of Core Invariants*: Fast-path `FILE_BASE_NAME` checks remain intact. Workouts of differing sports within 3 minutes are not suppressed.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an existing workout recorded with sport `Biking` at `06:57:16`,
• *When* importing a second file with sport `Biking` at `06:58:21` (offset: 65s, $\le 180\text{s}$),
• *Then* `isWorkoutExisting` SHALL return `true`, skipping insertion and creating zero duplicate records.
• *Given* an existing workout recorded with sport `Running` at `10:00:00`,
• *When* importing a file with sport `Biking` at `10:02:00` (offset: 120s),
• *Then* `isWorkoutExisting` SHALL NOT classify it as a duplicate, and the workout SHALL be imported normally.
• *Given* 3 concurrent coroutine workers processing candidate files on `Dispatchers.IO`,
• *When* two workers process dual-phone recordings of the same workout concurrently,
• *Then* `importMutex.withLock` SHALL synchronize check-and-insert, allowing the first worker to insert and causing the second worker to skip.

**Invariants**: Parsing fidelity for lap structures, sensor extensions, route cluster heuristics, and optional Strava upload parameters MUST NOT be broken.

### REQ-MIG-032: Interactive Post-Import Workout Navigation & Decoupled List Auto-Focus [`TST-MIG-029`]
- **Tests**: `LegacyImportEngine.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`, `WorkoutNavigationEvents.kt`, `WorkoutSummariesTabbedScreen.kt`, `MainActivityWithNavigation.kt`
- **Implementation**: Provide immediate, actionable navigation to newly imported workouts with tab resolution and animated list auto-scroll while preserving existing import contracts and athlete dismissal agency.
- **Rationale**: The system SHALL provide interactive post-import workout navigation and decoupled list auto-focus upon successful activity file import (ATT-2337):
1. *Non-Breaking Import Result Contract (`LegacyImportEngine.kt`)*:
• The engine SHALL define `data class ImportResult(val status: ImportStatus, val workoutId: Long? = null)`.
• The engine SHALL expose enriched import methods `importFromTcxResult`, `importFromGpxResult`, and `importFromFitResult` returning `ImportResult` containing the generated SQLite row ID `workoutId` upon successful insertion into `WorkoutSummaries.TABLE`.
• Existing methods `importFromTcx`, `importFromGpx`, `importFromFit`, and `importFromFitInternal` returning `Boolean` or `ImportStatus` SHALL be preserved as delegating convenience wrappers, guaranteeing 100% binary and behavioral compatibility for all existing tests and callers.
2. *ViewModel State Enrichment (`BackupRestoreViewModel.kt`)*:
• `BackupRestoreViewModel.UiState.Success` SHALL include `importedWorkoutId: Long? = null`.
• Upon single-file import (`importLegacyFile`), the state SHALL be emitted as `UiState.Success(message, result.workoutId)`.
• Upon batch FIT import (`importFitFiles`), if at least one workout was successfully imported, the state SHALL be emitted with `latestImportedWorkoutId`.
3. *Actionable Success Banner & Dismissal Agency (`ImportBackupTabsScreen.kt`)*:
• In `StateOverlaySection`, when `uiState is UiState.Success` and `importedWorkoutId != null`, the banner card SHALL display both `[Anzeigen]` (`action_view_workout`) and `[OK]`.
• Tapping `[Anzeigen]` SHALL dismiss the overlay and trigger navigation callback `onNavigateToWorkout(workoutId)`.
• Tapping `[OK]` SHALL dismiss the overlay without navigating, preserving athlete agency to remain on screen.
4. *Decoupled Navigation Bus (`WorkoutNavigationEvents.kt`)*:
• `WorkoutNavigationEvents` SHALL expose `navigateToWorkout: SharedFlow<Long?>` and `navigateToWorkoutLiveData: LiveData<Long?>` with `triggerNavigateToWorkout(workoutId)` and `resetNavigateToWorkout()`.
• Observing `navigateToWorkoutLiveData` in `MainActivityWithNavigation.kt` SHALL switch to the Workouts list (`R.id.drawer_workouts`).
5. *Tab Resolution & Animated Auto-Scroll (`WorkoutSummariesTabbedScreen.kt`)*:
• Collecting `WorkoutNavigationEvents.navigateToWorkout` SHALL resolve the sport type tab (`BIKE` -> 1, `RUN` -> 2, `UNKNOWN` -> 3, else 0), animate `pagerState.animateScrollToPage(pageIndex)`, and scroll `listState.animateScrollToItem(itemIndex)` directly to the imported workout.
6. *9-Language Localization Parity*:
• `action_view_workout` SHALL maintain 100% parity across all 9 application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-MIG-032`) extending `REQ-MIG-011`, `REQ-MIG-020`, `REQ-MIG-024`, and `REQ-DAT-019` for Epic `ATT-281` (*Data Sovereignty & Migration*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.1, Ticket `ATT-2337`.
3. *Root Reason for Existing Formulation*: Previous import engine discarded created `workoutId` and returned booleans, preventing direct navigation from the success banner.
4. *Preservation of Core Invariants*: Legacy return types, deduplication rules, athlete agency via `[OK]`, and thread safety on `Dispatchers.IO` remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on the Import tab who imports a TCX, GPX, or FIT file,
• *When* the import completes successfully,
• *Then* `StateOverlaySection` displays `[View]` and `[OK]`. Tapping `[View]` navigates to the Workouts list and auto-scrolls to the imported workout.
• *Given* the athlete taps `[OK]`,
• *Then* the overlay clears and the athlete remains on the Import tab.
• *Given* existing tests calling `LegacyImportEngine.importFromTcx(...)`,
• *Then* all tests continue to compile and pass with 100% backward compatibility.

### REQ-MIG-033: Enhanced Altitude & Speed Telemetry Ingestion Parity in FIT Workout Import [`TST-MIG-030`]
- **Tests**: `LegacyImportEngine.kt`
- **Implementation**: Extract complete elevation profiles, ascent/descent calculations, and high-resolution speed streams from modern FIT files via enhanced altitude and speed field parsing.
- **Rationale**: The system SHALL guarantee complete elevation profile extraction, ascent/descent computation, and high-resolution speed parsing when importing Garmin/Wahoo/Hammerhead FIT workout files (ATT-2338):
1. *Enhanced Altitude Extraction & Precedence (`RecordMesg`)*:
• `LegacyImportEngine.kt` SHALL inspect both `record.enhancedAltitude` (field 78) and `record.altitude` (field 2).
• If `record.enhancedAltitude` is non-null, it SHALL take precedence over `record.altitude`.
• If `record.enhancedAltitude` is null, `record.altitude` SHALL serve as fallback.
• Altitude values SHALL be validated for physical plausibility within `-500.0 m .. 10000.0 m`. Invalid sentinel values (e.g. unscaled `0xFFFFFFFF` or out-of-range floats) SHALL be discarded.
• Valid altitude values SHALL populate `SensorType.ALTITUDE.name` in `bufferedSamples`, be appended to `altitudes: MutableList<Double>`, and be evaluated for extrema position tracking (`minAltPos`, `maxAltPos`).
2. *Enhanced Speed Extraction & Precedence (`RecordMesg` & `LapMesg`)*:
• For each `RecordMesg`, speed SHALL be extracted via `(record.enhancedSpeed ?: record.speed)?.toDouble()`.
• Speed values SHALL be validated within `0.0 m/s .. 100.0 m/s`.
• In `rawLaps.forEachIndexed`, `lapMaxSpeed` SHALL be extracted via `(lapMesg.enhancedMaxSpeed ?: lapMesg.maxSpeed)?.toDouble()`.
3. *Session Ascent/Descent Reference Integration (`SessionMesg`)*:
• If `SessionMesg` contains non-null `totalAscent` (field 22) or `totalDescent` (field 23), these values SHALL serve as reference values in cases where sample-level elevation is flat or omitted.
4. *100% Backwards & Invariant Compatibility*:
• Files generated by legacy devices that only populate legacy `altitude` and `speed` fields SHALL continue to import identically.
• TCX and GPX import pipelines SHALL remain completely untouched.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-MIG-033`) extending `REQ-DAT-019` (*Garmin FIT File Import Support*) under Epic `ATT-1117` (*FIT File Format Support*).
2. *Historical Origin & Commit Trace*: Introduced in sprint `2026-41.1` under ticket `ATT-2338`.
3. *Root Reason for Existing Formulation*: Early FIT importer relied solely on `record.altitude`, which was omitted in modern FIT SDK 21.x+ files recorded by modern head units.
4. *Preservation of Core Invariants*: Legacy field fallback ensures older devices continue to parse without disruption.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a FIT file where `RecordMesg.enhancedAltitude` is populated and legacy `RecordMesg.altitude` is null,
• *When* imported via `LegacyImportEngine.importFromFitResult`,
• *Then* `WorkoutSamples` contains `ALTITUDE` records matching the enhanced values, and `WorkoutSummaries` contains non-zero `ALTITUDE_ASCENT`, `ALTITUDE_MIN`, and `ALTITUDE_MAX`.
• *Given* a legacy FIT file where only `RecordMesg.altitude` is populated,
• *When* imported via `LegacyImportEngine.importFromFitResult`,
• *Then* altitude samples are preserved with 100% fidelity.
• *Given* a FIT file where `RecordMesg.enhancedSpeed` is populated and legacy `RecordMesg.speed` is null,
• *When* imported via `LegacyImportEngine.importFromFitResult`,
• *Then* `WorkoutSamples` contains valid `SPEED_mps` records.

### REQ-MIG-034: Google Drive Historical Workout File Discovery, Top-Level Candidates & Bulk Recovery (.fit, .tcx, .gpx) [Enable automated discovery, download, and multi-format bulk importing of historical workout activity files (.fit, .tcx, .gpx) from Google Drive with top-level folder fallback, duplicate skipping, and zero-result diagnostic feedback.]
- **Tests**: token.isNullOrBlank()`), an error state SHALL be set without starting a scan.
• Upon successful import of one or more activities, the system SHALL asynchronously trigger post-recovery reconciliation: `WorkoutRepository.loadAllWorkouts()`, `PeriodsRepository.syncPeriodsIfDiscrepancy()`, `WorkoutClusterRepository.refreshClusters()`.
4. *UI Integration & Connection Gating (`ImportBackupTabsScreen.kt`)*:
• `ImportTabContent` SHALL render a "Scan Google Drive" action button within each format card.
• When Google Drive is connected, clicking "Scan Google Drive" SHALL present the pre-import cluster tuning bottom sheet (`PreImportTuningBottomSheet`), confirming which triggers `viewModel.bulkRecoverGoogleDriveData(context, format)`.
• When Google Drive is disconnected, the button SHALL render in a visually deactivated state, and clicking it SHALL present the Google Drive connection prompt.
5. *100% 9-Language Localization Parity*:
• All user-facing strings (`scan_google_drive`, `legacy_import__downloading_google_drive`, `google_drive_scan_empty_diagnostic`) SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-MIG-034`) in Sprint 2026-41.1 (`ATT-2346`), amended in Sprint 2026-42.1 (`ATT-3315`). Builds upon `REQ-MIG-015`, `REQ-MIG-016`, `REQ-MIG-023`, `REQ-MIG-026`, and `REQ-MIG-031`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-2346` (commit `ec000c11`), amended under `ATT-3315` in Sprint `2026-42.1`.
3. *Root Reason for Existing Formulation*: Athletes maintaining workout archives in Google Drive lacked bulk-recovery support. When externally copied files were uploaded, Google Drive's least-privilege `drive.file` OAuth scope hid them from REST API queries, resulting in 0 files found with generic feedback.
4. *Preservation of Core Invariants*: Least-privilege OAuth scope `drive.file`, Room database schemas, clustering math, and 100% clean-room test pass rate must remain strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a connected Google Drive account with workout files in `aTrainingTracker/Workouts/`, `TCX/`, `FIT/`, or `GPX/`,
• *When* `bulkRecoverFromGoogleDrive` is invoked,
• *Then* candidate folder hierarchies include nested and top-level root folders, and matching files are discovered.
• *Given* workout files in Google Drive that already exist in `WorkoutSummariesDatabase`,
• *When* `bulkRecoverFromGoogleDrive` processes the queue,
• *Then* existing workouts are skipped immediately without executing a network download, and `skippedCount` is incremented.
• *Given* a Google Drive scan where 0 files are accessible due to `drive.file` scope,
• *When* `bulkRecoverGoogleDriveData` completes,
• *Then* `BackupRestoreViewModel` emits `google_drive_scan_empty_diagnostic` explaining the scope restriction and guiding the athlete to use the local file picker.
• *Given* Google Drive is disconnected,
• *When* the athlete views `ImportTabContent`,
• *Then* the "Scan Google Drive" button is visually muted, and clicking it prompts the connection dialog.
- **Rationale**: The system SHALL provide automated discovery, download, and multi-format bulk importing of historical workout activity files (`.fit`, `.tcx`, `.gpx`) from Google Drive (ATT-2346, ATT-3315):
1. *Google Drive API Discovery (`GoogleDriveClient.kt`)*:
• `GoogleDriveClient` SHALL provide recursive file discovery (`listFilesRecursively`) under specified folder hierarchies (`folderId`).
• The query SHALL paginate through results using `nextPageToken` and filter entries strictly matching target extensions: `.fit`, `.tcx`, and `.gpx`.
• `GoogleDriveClient` SHALL provide direct file download by Drive file ID (`downloadFileById(fileId, destinationFile)`).
• Least-privilege OAuth scope `https://www.googleapis.com/auth/drive.file` SHALL be strictly preserved.
2. *Top-Level & Nested Folder Hierarchy Scanning (`LegacyImportEngine.kt`)*:
• `LegacyImportEngine` SHALL implement `bulkRecoverFromGoogleDrive(context, format, listener, uploadToStrava): RecoveryResult`.
• The engine SHALL scan candidate folder hierarchies including nested and top-level root paths:
- For `fit`: `listOf("aTrainingTracker", "FIT")`, `listOf("FIT")`, `listOf("aTrainingTracker", "Workouts")`, `listOf("Workouts")`, `listOf("aTrainingTracker")`.
- For `tcx`: `listOf("aTrainingTracker", "TCX")`, `listOf("TCX")`, `listOf("aTrainingTracker", "Workouts")`, `listOf("Workouts")`, `listOf("aTrainingTracker")`.
- For `gpx`: `listOf("aTrainingTracker", "GPX")`, `listOf("GPX")`, `listOf("aTrainingTracker", "Workouts")`, `listOf("Workouts")`, `listOf("aTrainingTracker")`.
- For `all`: all candidate paths in prioritized order.
• Discovered entries SHALL be deduplicated across scanned paths by base filename (`removeSuffix("-TMP").removeSuffix("~").lowercase()`) per REQ-MIG-031.
• The engine SHALL spawn 3 concurrent workers bounded by `interactionSemaphore(3)`.
• Before downloading, each entry SHALL be checked against `isWorkoutExisting(summaryDb, baseFileName)`. Existing workouts SHALL be skipped immediately without downloading (REQ-MIG-015).
• Discovered files SHALL be routed by extension: `.fit` -> `importFromFitInternal`, `.tcx` -> `importFromTcxInternal`, `.gpx` -> `importFromGpxInternal`.
• Temporary download artifacts SHALL be cleaned up in a `finally` block.
3. *Zero-Result Diagnostic Feedback & Post-Recovery Reconciliation (`BackupRestoreViewModel.kt`)*:
• `BackupRestoreViewModel` SHALL provide `bulkRecoverGoogleDriveData(context, format)`.
• When Google Drive recovery completes with `totalScanned == 0`, `BackupRestoreViewModel` SHALL display localized diagnostic string `google_drive_scan_empty_diagnostic` explaining that under least-privilege `drive.file` scope, files copied from outside cannot be queried via REST API and directs the athlete to use the local multi-document file picker.
• When Google Drive is disconnected (`!TrainingApplication.uploadToGoogleDrive()

### REQ-MIG-035: Dropbox Historical Workout File Discovery & Bulk Recovery for FIT (.fit) [`TST-MIG-032`]
- **Tests**: `LegacyImportEngine.kt`
- **Implementation**: Discover, download, and import historical .fit workout files from Dropbox during bulk recovery alongside legacy XML formats with pre-download duplicate skipping.
- **Rationale**: The system SHALL extend Dropbox cloud recovery in `LegacyImportEngine.bulkRecoverFromDropbox` to discover, download, and import historical `.fit` workout activity files (ATT-2341):
1. *Target Directory Expansion (`LegacyImportEngine.kt`)*:
• When `format` is `"all"` (or default), `possiblePaths` SHALL scan `listOf("/TCX", "/apps/Workouts/TCX", "/GPX", "/apps/Workouts/GPX", "/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")`.
• When `format` is `"fit"`, `possiblePaths` SHALL scan `listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")`.
2. *Target Extension Expansion (`LegacyImportEngine.kt`)*:
• When `format` is `"all"` (or default), `targetExtensions` SHALL filter `listOf(".tcx", ".gpx", ".fit")`.
• When `format` is `"fit"`, `targetExtensions` SHALL filter `listOf(".fit")`.
3. *Worker Channel Routing (`LegacyImportEngine.kt`)*:
• In the background worker channel, discovered files with extension `"fit"` SHALL be routed to `importFromFitInternal(context, tempFile, listener, uploadToStrava)`.
• When `status` returns `ImportStatus.SUCCESS`, `importedCount` SHALL be incremented; when `ImportStatus.DUPLICATE_SKIPPED`, `skippedCount` SHALL be incremented; when `ImportStatus.FAILED`, `failedCount` SHALL be incremented.
4. *Pre-Download Duplicate Skipping & Cross-Folder Deduplication*:
• Discovered entries SHALL be deduplicated across scanned paths by base filename (`removeSuffix("-TMP").removeSuffix("~").lowercase()`) per `REQ-MIG-031`.
• Prior to downloading, each entry SHALL be evaluated via `isWorkoutExisting(summaryDb, baseFileName)`. Existing workouts SHALL be skipped immediately without invoking `downloadFileWithRetry`.
5. *Architectural & Invariant Preservation*:
• Existing TCX and GPX recovery flows SHALL remain completely intact.
• 3-worker channel concurrency bounded by `interactionSemaphore(3)` SHALL remain preserved.
• Temporary file artifacts created under `cacheDir/legacy_recovery/` SHALL be cleaned up.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Expands `REQ-MIG-016` (*Paginated & Recursive Cloud Recovery*), `REQ-MIG-031` (*Multi-Dimensional Workout Deduplication*), and `REQ-DAT-019` (*Garmin FIT File Import Support*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2341`, sprint `2026-41.1`, target release `V4.9.39`, Epic `ATT-1117` (*[Epic] FIT File Format Support*).
3. *Root Reason for Existing Formulation*: Previous implementation was strictly constrained to TCX and GPX formats from legacy migrations.
4. *Preservation of Core Invariants*: Full backward compatibility for TCX and GPX bulk imports is preserved; all existing unit tests asserting Dropbox recovery will continue to pass unmodified.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a bulk recovery request with `format = "fit"`,
• *When* `bulkRecoverFromDropbox` initializes candidate paths and extensions,
• *Then* `possiblePaths` contains `/FIT`, `/apps/Workouts/FIT`, and `/Workouts/FIT`, and `targetExtensions` contains `".fit"`.
• *Given* a bulk recovery request with `format = "all"`,
• *When* `bulkRecoverFromDropbox` scans Dropbox folders,
• *Then* `.fit`, `.tcx`, and `.gpx` files are discovered and queued.
• *Given* a `.fit` file in Dropbox whose base filename matches an existing workout in `WorkoutSummariesDatabase`,
• *When* the recovery worker inspects the queue entry,
• *Then* `downloadFileWithRetry` is NOT invoked, and `skippedCount` is incremented.
• *Given* a new `.fit` workout file downloaded from Dropbox,
• *When* the worker processes the file,
• *Then* it dispatches to `importFromFitInternal`, parsing telemetry and saving the workout to `WorkoutSummaries`.

### REQ-MIG-036: Automated Background Backup WorkManager Scheduling, Monotonic Timer Preservation, Conditional Network Constraints, Status Diagnostics, Unified Manual Cloud Backup, and Dynamic Automated Subtitles [`TST-MIG-033`, `TST-MIG-036`, `TST-DAT-031`, `TST-MIG-045`]
- **Tests**: `BackupWorker.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`, `BackupTabContent.kt`, `RestoreTabContent.kt`, `BackupManager.kt`, `values*/strings.xml`
- **Implementation**: Fix LOCAL_SUCCESS / PARTIAL_SUCCESS status display with per-target breakdown, expose local rolling backup restore in UI, apply conditional UNMETERED constraint in BackupWorker, unify manual cloud backup button across providers, and make automated backup subtitle dynamic, and unify Restore Backup card button labels, icons, and disconnected visibility.
- **Rationale**: The system SHALL execute automated periodic database backups reliably in the background without timer resets on application cold starts, aligning default preference values, guaranteeing co-located atomic file creation with stale cleanup, providing bounded local backup retention, enforcing conditional network constraints, displaying localized per-target status diagnostics, exposing internal rolling backups directly in the UI, and dynamically adapting manual cloud backup triggers and automated backup subtitles to reflect active cloud providers (ATT-3472, ATT-4458, ATT-4518, ATT-4587, amending `REQ-MIG-003` and `REQ-MIG-004`):
1. *Monotonic WorkManager Periodic Scheduling (`BackupWorker.kt`)*:
• `BackupWorker.Companion.schedule(context)` SHALL enqueue the unique periodic work request using `ExistingPeriodicWorkPolicy.UPDATE` for `WORK_NAME = "automated_backup_work"` (replacing legacy `ExistingPeriodicWorkPolicy.REPLACE`).
• The periodic timer and initial delay SHALL NOT be cancelled or reset to the full interval upon application cold starts in `TrainingApplication.onCreate()`, preserving monotonic countdown across daily app launches.
2. *Default Preference Value Parity (`BackupWorker.kt`)*:
• In `BackupWorker.doWork()`, the fallback default for `prefs.getBoolean("automated_backups", true)` SHALL evaluate to `true` (matching `schedule()`, `DropboxSettingsDialog.kt`, and `prefs_dropbox.xml`), guaranteeing that automated backups run on fresh installations without requiring explicit switch manipulation.
3. *Configuration Availability Before Worker Scheduling*:
• The system SHALL guarantee that application preferences (`SharedPreferences`) are fully initialized and accessible prior to invoking WorkManager background worker scheduling, ensuring background tasks read valid configuration parameters without encountering uninitialized state.
4. *Bounded Local Backup Creation, Co-Located Atomic Replacement & Stale Temp Cleanup (`BackupWorker.kt` & `BackupManager.kt`)*:
• When automated backups are enabled (`automated_backups == true`), `BackupWorker.doWork()` SHALL generate a valid local `.attbackup` archive using `BackupManager.createBackup(context)` regardless of whether cloud providers are connected.
• **Stale Temp Cleanup**: Prior to initiating a new backup bundling cycle, `BackupManager` SHALL delete any orphaned staging files (`aTrainingTracker_backup.tmp`) remaining from previous aborted attempts, preventing storage leakage.
• **Same-Directory Atomic File Replacement**: The staging file (`aTrainingTracker_backup.tmp`) SHALL reside in the exact same directory (`context.cacheDir`) as canonical `aTrainingTracker_backup.attbackup`, guaranteeing co-location on the same filesystem mount point. Replacement SHALL execute via `java.nio.file.Files.move` with `StandardCopyOption.ATOMIC_MOVE` (falling back defensively to `StandardCopyOption.REPLACE_EXISTING`), eliminating partial file corruption from process death during zipping.
• **Storage Boundedness**: Local backup generation SHALL be strictly bounded to $O(1)$ storage space by maintaining only the single canonical archive. No unbounded file accumulation is permitted on device disk.
5. *Concurrency Synchronization & Lock Contention Policy*:
• Backup bundling SHALL be synchronized via in-process mutex (`BackupManager.backupMutex`) and file lock (`aTrainingTracker_backup.lock`) to prevent race conditions with concurrent manual backups.
• If the lock is held by a concurrent manual backup, `BackupWorker` SHALL safely yield and return `Result.retry()` with exponential backoff.
6. *Decoupled Cloud Upload & Controlled Retry Policy*:
• If neither Dropbox nor Google Drive is connected, `BackupWorker` SHALL record `last_backup_timestamp = System.currentTimeMillis()`, update `last_backup_status = "LOCAL_SUCCESS"`, and return `Result.success()`.
• If cloud provider(s) are connected, `BackupWorker` SHALL attempt upload. If any connected upload succeeds, it SHALL update `last_backup_timestamp` and set `last_backup_status` to `"SUCCESS"` (or `"PARTIAL_SUCCESS"`). If all connected uploads fail, the atomic local backup remains safely preserved on device, `last_backup_status` is updated to `"Upload failed"`, and the worker returns `Result.retry()` with exponential backoff.
7. *Conditional Network Constraints (`BackupWorker.kt`)*:
• In `BackupWorker.Companion.schedule(context)`, WorkManager network constraints SHALL be dynamically determined based on active cloud backup configurations. If at least one cloud provider (Dropbox or Google Drive) is connected and enabled, constraints SHALL require `NetworkType.UNMETERED` (Wi-Fi). If no cloud provider is enabled, constraints SHALL require `NetworkType.NOT_REQUIRED`, ensuring automated local rolling backups execute reliably on offline or cellular connections.
8. *Per-Target Status Persistence (`BackupWorker.kt`)*:
• In `BackupWorker.doWork()`, the system SHALL persist discrete per-target outcomes for Local, Dropbox, and Google Drive into SharedPreferences key `last_backup_targets` (e.g. `local:SUCCESS;dropbox:FAILED;google_drive:SUCCESS`) alongside `last_backup_status`, preserving fine-grained diagnostic visibility.
9. *Localized Status Display & Diagnostic Clarity (`ImportBackupTabsScreen.kt`)*:
• In `BackupTabContent`, the system SHALL evaluate `lastBackupInfo` and render localized status text for `SUCCESS` (primary color), `LOCAL_SUCCESS` (primary color with "(Local only)"), `PARTIAL_SUCCESS` (tertiary color with "(Partial)"), and failure states (error color).
• The UI SHALL render discrete per-target badge indicators (Local, Dropbox, Google Drive) displaying success or failure icons, eliminating hardcoded English error strings and raw enum displays.
10. *Direct UI Rolling Backup Restoration (`BackupRestoreViewModel.kt`, `RestoreTabContent`)*:
• `RestoreTabContent` SHALL provide a dedicated "Restore from Automatic Local Backup" action showing the timestamp of the internal rolling backup file (`context.filesDir/backups/aTrainingTracker_backup.attbackup`).
• The action SHALL be enabled only when the rolling backup file exists and has non-zero length.
• Initiating the restore SHALL be guarded by the destructive full restore confirmation dialog (`AlertDialog`), and execute `MigrationEngine.performFullRestore` directly against the internal backup file, bypassing the Android document picker.
11. *Unified Provider-Aware Manual Cloud Backup Button & Mutex-Guarded Upload (`BackupTabContent.kt`, `ImportBackupTabsScreen.kt`, `BackupRestoreViewModel.kt`)*:
• The manual cloud backup trigger in `BackupTabContent` SHALL dynamically adapt its label and visual state to reflect connected cloud storage providers: (a) if both Dropbox and Google Drive are connected: `create_and_upload_to_both` ("Create & Upload\n(Dropbox & Google Drive)"); (b) if only Dropbox is connected: `create_and_upload_to_dropbox` ("Create & Upload to Dropbox"); (c) if only Google Drive is connected: `create_and_upload_to_google_drive` ("Create & Upload to Google Drive"); (d) if neither is connected: `create_and_upload_to_cloud` ("Create & Upload to Cloud") rendered in muted styling (`surfaceVariant` container with 38% alpha onSurface content).
• Tapping the cloud upload button when neither provider is connected SHALL display an informational dialog explaining that no cloud storage is connected and directing the athlete to configure Dropbox or Google Drive in Synchronization settings.
• In `BackupRestoreViewModel`, manual cloud upload SHALL execute via `uploadToCloud(context)`, which SHALL acquire `BackupManager.acquireLock(context)`, generate a local backup, dispatch uploads to all enabled cloud providers (Dropbox and/or Google Drive), and persist `last_backup_timestamp`, `last_backup_status` (SUCCESS, PARTIAL_SUCCESS, or Upload failed), and `last_backup_targets` into SharedPreferences with full parity with `BackupWorker`.
• To prevent awkward mid-word wrapping on narrow screens, `create_and_upload_to_both` SHALL format with an explicit literal newline `\n` preceding `(Dropbox & Google Drive)` across all 9 supported locales, and the cloud backup button `Text` composable SHALL enforce centered text alignment (`textAlign = TextAlign.Center`) [ATT-4587].
12. *Dynamic Automated Backups Subtitle (`BackupTabContent.kt`)*:
• In `BackupTabContent`, the summary text under Automated Backups SHALL dynamically describe active backup destinations: (a) if both Dropbox and Google Drive are connected: `automated_backups_summary_both`; (b) if only Dropbox is connected: `automated_backups_summary`; (c) if only Google Drive is connected: `automated_backups_summary_google_drive`; (d) if neither provider is connected: `automated_backups_summary_local_only`.
13. *Restore Backup Button Label, Icon, and Disconnected State Harmonization (`REQ-MIG-036.13`)*:
• In `RestoreTabContent.kt`, all four restore action buttons inside the "Restore Backup" card SHALL adhere to unified naming conventions across all 9 supported locales: `restore_automatic_local_backup` ("Restore from Automatic Local Backup" / "Aus automatischem lokalen Backup wiederherstellen"), `restore_from_file` ("Restore from Local File" / "Aus lokaler Datei wiederherstellen"), `restore_from_dropbox` ("Restore from Dropbox" / "Aus Dropbox wiederherstellen"), and `restore_from_google_drive` ("Restore from Google Drive" / "Aus Google Drive wiederherstellen").
• In `RestoreTabContent.kt`, the Dropbox restore button icon SHALL be `Icons.Default.CloudDownload`, eliminating the misleading `CloudUpload` arrow.
• In `RestoreTabContent.kt`, the Google Drive restore button SHALL render unconditionally (removing `if (isGoogleDriveConnected)` gating), adopting `googleDriveRestoreColors` and `googleDriveRestoreBorder` (dimmed text and border) when disconnected, matching Dropbox restore presentation.
• In `ImportBackupTabsScreen.kt`, tapping either Dropbox or Google Drive restore when disconnected SHALL display an informative dialog (`showDropboxDisconnectedDialog` / `showGoogleDriveDisconnectedDialog`) using the respective service title and disconnected string resource (`R.string.dropbox_disconnected_status` / `R.string.google_drive_disconnected_status`) without launching the destructive restore confirmation dialog.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-MIG-036` (ATT-3472, amending `REQ-MIG-003` and `REQ-MIG-004`). Targets `BackupWorker.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`, `BackupTabContent.kt`, `RestoreTabContent.kt`, `BackupManager.kt`, and `values*/strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.2 (initial `BackupWorker`), Sprint 2026-41.1 (`ATT-1306` Google Drive upload), Sprint 2026-42.3 (`ATT-3472`, commit `9739a5bc`), Sprint 2026-43.4 (`ATT-4458`), Sprint 2026-43.5 (`ATT-4518`), Sprint 2026-43.6 (`ATT-4587`), Sprint 2026-43.7 (`ATT-4664`, Restore Backup card button labels, icons, and disconnected visibility).
3. *Root Reason for Existing Formulation*: Early backup workers and manual backup UI were exclusively cloud-oriented and hardcoded to Dropbox before Google Drive support was introduced in ATT-1306. Manual cloud backup in BackupRestoreViewModel bypassed BackupManager.acquireLock() and failed to persist last_backup_* preferences. ATT-4518 unified button text without explicit line breaks or centered text alignment, leading to mid-word wrapping on narrow displays. In `RestoreTabContent.kt`, Google Drive restore was wrapped in `if (isGoogleDriveConnected)` as a temporary safeguard during initial integration, leaving `googleDriveRestoreColors` unused and hiding the button when disconnected while Dropbox remained visible. Dropbox restore mistakenly referenced the `CloudUpload` icon instead of `CloudDownload`. Button strings in `RestoreTabContent` mixed technical 'Full Restore (Migration)' labels with 'Restore from ...' patterns.
4. *Preservation of Core Invariants*: Monotonic timer preservation (`ExistingPeriodicWorkPolicy.UPDATE`), $O(1)$ bounded local storage, co-located atomic move, destructive restore confirmation dialog, BackupManager concurrency lock, last_backup_targets schema compatibility, 9-language translation parity, and 100% clean-room test pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active periodic work request registered for `automated_backup_work` with remaining time $T_{rem} < T_{period}$,
• *When* `TrainingApplication.onCreate()` executes during app startup and calls `BackupWorker.schedule(context)`,
• *Then* `enqueueUniquePeriodicWork` uses `ExistingPeriodicWorkPolicy.UPDATE`, preserving the monotonic schedule without resetting the timer to $T_{period}$.
• *Given* a fresh installation where `"automated_backups"` is unset in SharedPreferences,
• *When* `BackupWorker.doWork()` executes,
• *Then* `prefs.getBoolean("automated_backups", true)` evaluates to `true` and proceeds with backup creation.
• *Given* an athlete with no cloud provider connected and automated backups enabled,
• *When* `BackupWorker.schedule(context)` executes,
• *Then* WorkManager constraints are built with `NetworkType.NOT_REQUIRED`, permitting backups without Wi-Fi.
• *Given* an athlete with Dropbox or Google Drive backup enabled,
• *When* `BackupWorker.schedule(context)` executes,
• *Then* WorkManager constraints are built with `NetworkType.UNMETERED`, conserving cellular data.
• *Given* an athlete with no cloud provider connected and automated backups enabled,
• *When* `BackupWorker.doWork()` executes,
• *Then* `BackupManager.createBackup(context)` creates a valid local archive bounded to $O(1)$ space via atomic same-mount move, `last_backup_timestamp` is updated, `last_backup_status` is set to `"LOCAL_SUCCESS"`, `last_backup_targets` records `local:SUCCESS`, and the worker returns `Result.success()`.
• *Given* `BackupTabContent` composed with `last_backup_status = "LOCAL_SUCCESS"`,
• *When* rendered,
• *Then* the status text displays localized `last_backup_local_success` in primary color, with no error styling or raw enum.
• *Given* `BackupTabContent` composed with `last_backup_status = "PARTIAL_SUCCESS"`,
• *When* rendered,
• *Then* the status text displays localized `last_backup_partial_success` in tertiary color, and per-target badges indicate individual provider success and failure states.
• *Given* `RestoreTabContent` when a valid internal rolling backup exists,
• *When* composed,
• *Then* the "Restore from Automatic Local Backup" button is enabled and displays the formatted timestamp of the backup file.
• *Given* an athlete tapping "Restore from Automatic Local Backup",
• *When* confirmed via the destructive restore warning dialog,
• *Then* `MigrationEngine.performFullRestore` executes against `context.filesDir/backups/aTrainingTracker_backup.attbackup`.
• *Given* both Dropbox and Google Drive connected,
• *When* `BackupTabContent` renders,
• *Then* the cloud backup button displays 'Create & Upload\n(Dropbox & Google Drive)' centered via `textAlign = TextAlign.Center` and automated backups subtitle displays 'Regularly upload backups to Dropbox and Google Drive' [ATT-4587].
• *Given* only Google Drive connected,
• *When* `BackupTabContent` renders,
• *Then* the cloud backup button displays 'Create & Upload to Google Drive' and automated backups subtitle displays 'Regularly upload backups to Google Drive'.
• *Given* neither provider connected,
• *When* `BackupTabContent` renders,
• *Then* the cloud backup button displays 'Create & Upload to Cloud' with muted styling, automated backups subtitle displays 'Regularly create local rolling backups on this device', and tapping the button opens the no-provider informational dialog.
• *Given* an athlete tapping cloud backup with provider(s) enabled,
• *When* `uploadToCloud` executes,
• *Then* it acquires `BackupManager.acquireLock`, creates the backup, uploads to all connected providers, and persists `last_backup_timestamp`, `last_backup_status`, and `last_backup_targets`.
• *Given* the Restore tab in `ImportBackupTabsScreen` / `RestoreTabContent`,
• *When* the "Restore Backup" card renders,
• *Then* all 4 restore buttons display localized labels following the "Restore from ..." pattern, and Dropbox restore renders with `Icons.Default.CloudDownload`.
• *Given* Google Drive is not connected,
• *When* viewing the "Restore Backup" card in `RestoreTabContent`,
• *Then* the Google Drive restore button remains visible and dimmed with `googleDriveRestoreColors` and `googleDriveRestoreBorder`.
• *Given* Google Drive is not connected,
• *When* the athlete taps the Google Drive restore button,
• *Then* `showGoogleDriveDisconnectedDialog` is displayed showing `R.string.google_drive_disconnected_status`, and `showGoogleDriveRestoreConfirm` is NOT triggered.

**Invariants**: Monotonic timer preservation, bounded $O(1)$ storage space, co-located atomic move, destructive restore confirmation guard, BackupManager concurrency lock, last_backup_targets schema compatibility, 9-language translation parity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-EXP-018: FIT Format Support in Detailed Export Report, Safe Database Upsert & Workout Header Export Menu Integration [`TST-EXP-015`]
- **Tests**: `ExportStatusDatabaseManager.java`, `ExportStatusDataProvider.kt`, `ExportStatusRepository.kt`, `WorkoutHeader.kt`, `strings.xml` (all 9 locales)
- **Implementation**: Include FIT in detailed export report, safe upsert in ExportStatusDatabaseManager, and FIT option in WorkoutHeader export menu.
- **Rationale**: The system SHALL include the FIT binary format in the detailed export status report, ensure database persistence via safe upsert in `ExportStatusDatabaseManager`, and provide direct on-demand FIT export in the workout header overflow menu (ATT-2310):
1. *Safe Database Upsert (`ExportStatusDatabaseManager.java`)*: When `updateExportStatus(contentValues, fileBaseName, exportType, fileFormat)` is called: (a) If `exportType != null`, the system SHALL execute `db.update(...)`. If `0` rows are affected, the system SHALL insert a new row into `ExportStatusDbHelper.TABLE` with `fileBaseName`, `TYPE = exportType.name()`, `FORMAT = fileFormat.name()`, `EXPORT_STATUS = contentValues.getAsString(EXPORT_STATUS)` (defaulting to `UNWANTED`), and `ANSWER = contentValues.getAsString(ANSWER)`. (b) If `exportType == null`, the system SHALL update all rows matching `FILE_BASE_NAME = ? AND FORMAT = ?`. If `0` rows are affected, the system SHALL insert rows for all `ExportType`s that support `fileFormat`. (c) `updateExportStatus(contentValues, exportInfo)` SHALL delegate cleanly to `updateExportStatus(contentValues, exportInfo.getFileBaseName(), exportInfo.getExportType(), exportInfo.getFileFormat())`.
2. *Supported Format Synthesis in UI Data Providers (`ExportStatusDataProvider.kt` & `ExportStatusRepository.kt`)*: In `createGroupData(...)`, when generating details for an `ExportType` that has existing export activity: (a) If any format defined in `exportType.exportToFileFormats` (such as `FileFormat.FIT` on legacy workouts) is absent from the database, the system SHALL synthesize an `ExportRow` with `status = ExportStatus.UNWANTED` and `answer = null`; (b) The rendered `ExportDetail` items in `ExportDetailsDialog` SHALL be ordered consistently according to `exportType.exportToFileFormats`.
3. *FIT Export Option in Workout Header Dropdown Menu (`WorkoutHeader.kt`)*: `standardFormats` in `WorkoutHeader.kt` SHALL include `FileFormat.FIT to R.string.fitWrite` alongside `TCX`, `GPX`, `CSV`, and `GC`. Selecting FIT SHALL invoke `onExport(FileFormat.FIT)`, triggering on-demand FIT export via `WorkoutSummariesViewModel` and `ExportManager`.
4. *100% 9-Language Localization Parity*: String resource `fitWrite` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
5. *Preservation of System Invariants*: Asynchronous WorkManager workers (`ExportWorker`), cloud export options (Dropbox, Google Drive), existing table schemas, and 100% clean-room test suite pass rate MUST NOT be compromised.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded or imported prior to FIT format support,
• *When* the athlete opens the detailed export status bottom sheet (`ExportDetailsDialog`),
• *Then* `FIT` SHALL be rendered in the list with localized status `@string/unwanted` ("Nicht erwünscht").
• *Given* a workout with no existing database row for `(fileBaseName, FILE, FIT)` being exported to FIT,
• *When* `updateExportStatus` is called,
• *Then* `ExportStatusDatabaseManager` SHALL insert a new row in `ExportStatusDbHelper.TABLE` and persist its status.
• *Given* viewing a completed workout summary in `WorkoutDetailsScreen` / `WorkoutSummariesListFragment`,
• *When* opening the 3-dots overflow menu in `WorkoutHeader`,
• *Then* an option labeled with localized `fitWrite` SHALL be visible, and tapping it SHALL trigger export to `FileFormat.FIT`.
• *Given* a WorkManager failure where `updateExportStatus` is called with `exportType == null`,
• *When* executed,
• *Then* the system SHALL NOT throw `NullPointerException` and SHALL safely update or insert failure rows for supported export types.

**Invariants**: Background WorkManager jobs, cloud sync pipelines, database schemas, and 100% test pass rate MUST NOT be broken.

### REQ-MIG-037: Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36) [`TST-MIG-034`]
- **Tests**: `TrackpointXmlParser.kt`, `TcxTrackpointParser.kt`, `GpxTrackpointParser.kt`, `LegacyImportEngine.kt`, `TcxCourseParser.kt`, `GpxRouteImporter.kt`
- **Implementation**: Unified streaming TCX and GPX XML trackpoint parsing engine across workout and route imports (Rule 36).
- **Rationale**: The system SHALL consolidate all duplicated XML trackpoint and course/route parsing logic across workout imports and route imports into a unified, shared streaming XML parser architecture (`com.atrainingtracker.trainingtracker.routes.parsers.TrackpointXmlParser.kt`), enforcing Rule 36 (Single Source of Truth & DRY) and guaranteeing 100% data fidelity between workout imports and route imports (ATT-3362, complementing `REQ-MIG-030`, `REQ-MAP-025`, and `REQ-MAP-026`):
1. *Shared XML Trackpoint Domain DTOs (`TrackpointXmlParser.kt`)*:
• The system SHALL provide immutable domain data transfer objects: `RawTrackpoint(lat: Double, lng: Double, altitude: Double? = null, distanceMeters: Double? = null, time: String? = null, hr: Int? = null, cadence: Int? = null, power: Int? = null, speedMps: Double? = null, temperature: Double? = null)`, `RawCoursePoint(name: String, lat: Double, lng: Double, altitude: Double? = null, type: String? = null, notes: String? = null, time: String? = null)`, `RawLap(lapNr: Long, startTime: String? = null, totalTimeSeconds: Double? = null, distanceMeters: Double? = null, maxSpeed: Double? = null, calories: Int? = null, avgHeartRate: Int? = null, name: String? = null, description: String? = null, trackpoints: List<RawTrackpoint> = emptyList())`, `ParsedTcxData(name: String? = null, notes: String? = null, sport: String? = null, laps: List<RawLap> = emptyList(), coursePoints: List<RawCoursePoint> = emptyList())`, and `ParsedGpxData(name: String? = null, description: String? = null, sport: String? = null, laps: List<RawLap> = emptyList(), waypoints: List<RawCoursePoint> = emptyList())`.
2. *Streaming TCX Parser (`TcxTrackpointParser`)*:
• The system SHALL implement pure Java/Kotlin streaming pull parsing (`org.xmlpull.v1.XmlPullParserFactory`) in `TcxTrackpointParser.parse(inputStream: InputStream): ParsedTcxData`.
• The parser SHALL handle both Garmin Activity documents (`<Activity>`) and Garmin Course documents (`<Course>`), extracting all trackpoints (`<Trackpoint>`), positions (`<LatitudeDegrees>`, `<LongitudeDegrees>`), altitude (`<AltitudeMeters>`), cumulative distance (`<DistanceMeters>`), time (`<Time>`), telemetry (`<HeartRateBpm>`, `<Cadence>`, `<RunCadence>`, `<Watts>`, `<Speed>`), lap boundaries (`<Lap>`), and course points (`<CoursePoint>`).
3. *Streaming GPX Parser (`GpxTrackpointParser`)*:
• The system SHALL implement pure Java/Kotlin streaming pull parsing in `GpxTrackpointParser.parse(inputStream: InputStream): ParsedGpxData`.
• The parser SHALL handle trackpoints (`<trkpt>`), route points (`<rtept>`), multi-segment laps (`<trkseg>`), waypoints (`<wpt>`), and Garmin TPX extensions (`<gpxtpx:TrackPointExtension>`: `hr`, `cad`, `speed`, `atemp`), extracting coordinates (`lat`, `lon`), altitude (`<ele>`), timestamp (`<time>`), and telemetry.
4. *LegacyImportEngine Integration (`LegacyImportEngine.kt`)*:
• In `LegacyImportEngine.kt`, the system SHALL excise the duplicate ~250-line inline TCX XmlPullParser loop and ~150-line inline GPX XmlPullParser loop, delegating stream ingestion to `TcxTrackpointParser.parse()` and `GpxTrackpointParser.parse()`.
• Parsed laps and raw trackpoints SHALL be mapped directly to `ParsedLap`, `ContentValues` sample rows, `points`, and summary metrics with 100% data fidelity.
5. *Route Importers Integration (`TcxCourseParser.kt` & `GpxRouteImporter.kt`)*:
• In `TcxCourseParser.kt`, the system SHALL delegate TCX course and course-point parsing to `TcxTrackpointParser.parse()`, mapping `trackpoints` to `PathPoint` and `coursePoints` to `RouteWaypoint`.
• In `GpxRouteImporter.kt`, the system SHALL route GPX parsing through `GpxTrackpointParser.parse()`, mapping trackpoints to `PathPoint` and waypoints to `RouteWaypoint` with preserved DEM elevation enrichment.
6. *Preservation of System Invariants*:
• 100% test pass rate across `LegacyImportEngineGpxTest`, `TcxHighFidelityImportTest`, `TcxCourseParserTest`, `GpxRouteImporterWaypointTest`, and `GpxRouteImporterElevationTest` MUST be preserved without data loss or regressions.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-MIG-037`). Refactors and unifies implementation of `REQ-MIG-030` (*High-Fidelity GPX Workout Import & Full Telemetry Ingestion Parity*), `REQ-MAP-025` (*Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Data*), and `REQ-MAP-026` (*Support Waypoints, POIs and TCX Course Points*).
2. *Historical Origin & Commit Trace*: Workout TCX/GPX import was authored in Sprint 2026-40.12 (`LegacyImportEngine.kt`), while Route TCX/GPX import was independently authored in Sprint 2026-41.2 (`TcxCourseParser.kt`, `GpxRouteImporter.kt`).
3. *Root Reason for Existing Formulation*: Separate development by different features led to copy-pasting of XML parsing loops and importing external library dependencies (`io.ticofab.androidgpxparser`), violating Rule 36.
4. *Preservation of Core Invariants*: Full telemetry tag extraction (HR, cadence, watts, speed, temp), lap boundary preservation, waypoints/course-points polyline projection, and DEM elevation enrichment strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a Garmin TCX workout or course file,
• *When* parsed by `LegacyImportEngine` or `TcxCourseParser`,
• *Then* both engines delegate to `TcxTrackpointParser`, extracting identical coordinates, timestamps, distances, and telemetry without duplicate XML loop logic.
• *Given* a GPX file containing track segments, waypoints, and Garmin TPX extensions,
• *When* parsed by `LegacyImportEngine` or `GpxRouteImporter`,
• *Then* both engines delegate to `GpxTrackpointParser`, extracting identical trackpoints, waypoints, and telemetry.
• *Given* all existing unit tests in `LegacyImportEngineGpxTest`, `TcxHighFidelityImportTest`, `TcxCourseParserTest`, `GpxRouteImporterWaypointTest`, and `GpxRouteImporterElevationTest`,
• *When* executed on pure JVM,
• *Then* 100% of tests pass without any behavioral regression or data loss.

**Invariants**: Pure streaming XML parser efficiency, Single Source of Truth (Rule 36), and 100% clean-room test pass rate MUST NOT be broken.

### REQ-MIG-038: Modular Workout Format Parsers for TCX, GPX, and FIT Ingestion & LegacyImportEngine Architectural Facade [`TST-MIG-035`]
- **Tests**: `ParsedWorkoutData.kt`, `TcxWorkoutParser.kt`, `GpxWorkoutParser.kt`, `FitWorkoutParser.kt`, `LegacyImportEngine.kt`
- **Implementation**: Modular workout format parsers for TCX, GPX, and FIT ingestion & LegacyImportEngine architectural facade (Rule 20, Rule 23, Rule 36).
- **Rationale**: The system SHALL decompose the monolithic `LegacyImportEngine.kt` by extracting format-specific parsing algorithms into dedicated, single-responsibility parser components (`TcxWorkoutParser`, `GpxWorkoutParser`, `FitWorkoutParser`) under `migration/parsers/`, unifying the post-parsing database persistence pipeline, and preserving 100% backward compatibility of `LegacyImportEngine`'s public and internal APIs, enforcing Rule 20 (Database Mapping Symmetry), Rule 23 (Code Cleanliness), and Rule 36 (Single Source of Truth & DRY) (ATT-3552, complementing `REQ-MIG-030`, `REQ-MIG-036`, and `REQ-MIG-037`):
1. *Structured In-Memory Parsed Workout Model (`ParsedWorkoutData`)*:
• The system SHALL define `ParsedWorkoutData` in `com.atrainingtracker.trainingtracker.migration.parsers` encapsulating: metadata (`workoutName`, `workoutNotes`, `sportName`, `bSportType`, `resolvedSportId`, `firstTime`, `lastTime`), geospatial & elevation extrema (`points`, `altitudes`, `distances`, `minAltVal`, `minAltPos`, `maxAltVal`, `maxAltPos`), telemetry and laps (`parsedLaps`, `bufferedSamples`, `foundSensors`).
2. *Dedicated Modular Format Parsers (`com.atrainingtracker.trainingtracker.migration.parsers`)*:
• `TcxWorkoutParser`: The system SHALL extract TCX XML parsing logic into `TcxWorkoutParser.parse(file: File, context: Context): Result<ParsedWorkoutData>`, delegating XML trackpoint ingestion to `TcxTrackpointParser.parse()`, mapping ISO-8601 timestamps, extracting laps, and resolving sport types via `SportTypeDatabaseManager`.
• `GpxWorkoutParser`: The system SHALL extract GPX XML parsing logic into `GpxWorkoutParser.parse(file: File, context: Context): Result<ParsedWorkoutData>`, delegating trackpoint ingestion to `GpxTrackpointParser.parse()`, extracting laps/segments, and computing cumulative geodesic distances.
• `FitWorkoutParser`: The system SHALL extract Garmin FIT binary parsing into `FitWorkoutParser.parse(file: File, context: Context): Result<ParsedWorkoutData>`, verifying file integrity via `Decode.checkFileIntegrity`, dispatching messages via `MesgBroadcaster`, and mapping session, lap, and record messages to telemetry samples.
3. *Unified Orchestration & Persistence Pipeline in `LegacyImportEngine`*:
• In `LegacyImportEngine.kt`, the system SHALL consolidate redundant database persistence logic across `importFromTcxResult`, `importFromGpxResult`, and `importFromFitResult` into a single helper method `persistParsedWorkout(context, baseFileName, parsedWorkout, source, uploadToStrava, listener): ImportResult`.
• `persistParsedWorkout` SHALL enforce: mutex-guarded deduplication via `importMutex.withLock` and `isWorkoutExisting`, sample table creation via `samplesDbManager.createNewTable(baseFileName, SensorType.values().toList())` and transactional bulk insert of `bufferedSamples`, summary table insertion in `summaryDb` with `EQUIPMENT_ID = -1L`, `FINISHED = 1`, and upload flags, lap insertion in `LapsDatabaseManager`, cumulative distance and bounds enrichment, route cluster candidate discovery via `listener.onNewClusterCandidate`, non-blocking stats recalculation via `recalculateStats`, and Strava upload scheduling via `schedulePostImportCommunityUpload`.
4. *100% Facade API Backward Compatibility*:
• `LegacyImportEngine` SHALL preserve all public and internal function signatures: `importFromTcx`, `importFromTcxResult`, `importFromTcxInternal`, `importFromGpx`, `importFromGpxResult`, `importFromGpxInternal`, `importFromFit`, `importFromFitResult`, `importFromFitInternal`, `bulkRecoverFromDropbox(...)`, and `bulkRecoverFromGoogleDrive(...)`.
5. *Preservation of System Invariants*:
• Deduplication window (3-minute same-sport), dynamic sample table initialization, cluster candidate discovery, and 100% clean-room test pass rate strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new architectural requirement (`REQ-MIG-038`), refactoring and modularizing the internal implementation of `REQ-MIG-030` (GPX), `REQ-MIG-036` (FIT), and `REQ-MIG-037` (Streaming Trackpoint XML).
2. *Historical Origin & Commit Trace*: Sprints 2026-40.12 through 2026-42.3 incrementally grew `LegacyImportEngine.kt` to 1,971 lines. `routes/parsers/TrackpointModels.kt` was added in `ATT-3362`.
3. *Root Reason for Existing Formulation*: Rapid iterative additions placed format parsing directly inside the orchestration engine.
4. *Preservation of Core Invariants*: Concurrency locking (`importMutex`), cloud recovery pipelines, dynamic sample table columns, deduplication rules, and 100% unit test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* package `com.atrainingtracker.trainingtracker.migration.parsers`,
• *When* inspected,
• *Then* `ParsedWorkoutData.kt`, `TcxWorkoutParser.kt`, `GpxWorkoutParser.kt`, and `FitWorkoutParser.kt` exist as dedicated, focused classes.
• *Given* existing callers and unit tests interacting with `LegacyImportEngine`,
• *When* invoking `importFromTcx`, `importFromGpx`, `importFromFit`, or their `*Result` / `*Internal` variants,
• *Then* the method signatures, return types, and execution semantics are 100% identical.
• *Given* valid TCX, GPX, and FIT files,
• *When* parsed via `TcxWorkoutParser.parse()`, `GpxWorkoutParser.parse()`, and `FitWorkoutParser.parse()`,
• *Then* they return `Result.success(ParsedWorkoutData)` containing correct sport types, laps, trackpoints, and sensor telemetry.
• *Given* an already-imported workout file,
• *When* imported again through `LegacyImportEngine`,
• *Then* `ImportStatus.DUPLICATE_SKIPPED` is returned and no duplicate SQLite rows or sample tables are created.
• *Given* all existing migration and import tests,
• *When* executing `./gradlew testDebugUnitTest`,
• *Then* 100% of unit tests pass without regression.

**Invariants**: Facade API backward compatibility, Single Source of Truth (Rule 36), database mapping symmetry (Rule 20), and 100% clean-room test pass rate MUST NOT be broken.

### REQ-DAT-001: Deterministic Resource Management via Try-With-Resources and Bounds Checking on SQLite Cursors Across Legacy Database Managers [`TST-DAT-001`]
- **Tests**: `ExportStatusDatabaseManager.java`, `DevicesDatabaseManager.java`, `SportTypeDatabaseManager.java`, `TrackingViewsDatabaseManager.java`, `PebbleDatabaseManager.java`, `BaseFileWriter.java`
- **Implementation**: Deterministic resource management via try-with-resources and bounds checking on SQLite cursors across legacy database managers.
- **Rationale**: The system SHALL guarantee deterministic resource cleanup and zero cursor leaks by enclosing all internally-consumed `Cursor` instances in `try-with-resources` (`AutoCloseable`) blocks across all Java database managers, database helpers, and file exporters, AND SHALL guarantee crash immunity against `CursorIndexOutOfBoundsException` by validating cursor navigation before accessing column data (ATT-3550, parent epic ATT-431):
1. *Try-With-Resources Allocation (`REQ-DAT-001.1`)*:
• All local queries (`db.query(...)`, `db.rawQuery(...)`) in `ExportStatusDatabaseManager`, `DevicesDatabaseManager`, `SportTypeDatabaseManager`, `TrackingViewsDatabaseManager`, `SegmentsDatabaseManager`, `WorkoutSamplesDatabaseManager`, `PebbleDatabaseManager`, `EquipmentDbHelper`, `ActiveDevicesDbHelper`, `ExportManager`, `BaseFileWriter`, and `GPXFileWriter` SHALL be wrapped in `try (Cursor cursor = ...)`.
• Trailing manual `cursor.close()` calls on try-with-resources cursors SHALL be eliminated.
• Public/protected factory/provider methods returning an open `Cursor` to external components (e.g. `getCursorForAllDevices()`, `getDeviceCursor(long)`) SHALL NOT close the cursor internally, but callers consuming them within internal methods SHALL use try-with-resources.
2. *Comprehensive Navigation Safety & Canonical Sentinel Mapping (`REQ-DAT-001.2`)*:
• All cursor navigation operations (`cursor.moveToFirst()`, `cursor.moveToLast()`, and iteration loops `while (cursor.moveToNext())`) SHALL be conditionally validated before reading column indices.
• If a query yields 0 rows (empty result set), query methods SHALL return deterministic, type-specific canonical empty-state sentinels:
- Collection and List types (`List<T>`, `Set<T>`): SHALL return empty collection (`Collections.emptyList()`, `Collections.emptySet()`, or empty `new ArrayList<>()`).
- Entity models, Objects, and Strings (`DeviceType`, `String`, custom POJO): SHALL return `null` (or empty string `""` where specified by method signature).
- Database Identifiers (`long id`): SHALL return `-1L`.
- Numeric counts and scalar metrics (`int`, `double`): SHALL return `0` / `0.0`.
- Boolean state flags: SHALL return `false`.
• Zero `CursorIndexOutOfBoundsException` or `NullPointerException` crashes SHALL occur on empty or closed cursor states.
3. *Static Architectural Guard & Whitelist-Based CI/CD Contract (`REQ-DAT-001.3`)*:
• The codebase SHALL enforce try-with-resources and cursor safety via an automated JUnit architecture audit test (`TryWithResourcesCursorAuditTest.kt`) executed as part of `./gradlew testDebugUnitTest`, verifying that all local cursor allocations in Java database managers use try-with-resources.
• The static audit test and standalone CLI linter `tools/lint_cursor_safety.py` SHALL utilize an explicit whitelist/method signature detection for provider methods returning `Cursor` (`public/protected Cursor get...`), preventing false positives on intentional cursor factory methods.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-DAT-001`. Fixes resource leak and empty cursor crash vectors in ATT-3550.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3550`). Legacy code was written prior to Android API 19 try-with-resources support, relying on manual cursor closing and assuming non-empty query results.
3. *Root Reason for Existing Formulation*: Unchecked cursor navigation caused `CursorIndexOutOfBoundsException` when rows were missing, and unshielded cursors leaked on runtime exceptions.
4. *Preservation of Core Invariants*: Database schemas, table columns, query filters, and return values remain 100% backward compatible; cursors returning to external callers remain open.

**Acceptance Criteria (Given-When-Then)**:
• *Given* all Java database managers in `app/src/main`,
• *When* querying SQLite tables,
• *Then* all local cursors are enclosed in `try-with-resources`, ensuring automatic closure even if an exception occurs.
• *Given* any query method executed against an empty database table or non-existent record ID,
• *When* `cursor.moveToFirst()` returns `false`,
• *Then* the method returns safely with the specified sentinel value (`emptyList()`, `null`, `-1L`, `0`, or `false`) without throwing `CursorIndexOutOfBoundsException`.
• *Given* the test suite,
• *When* `TryWithResourcesCursorAuditTest` runs,
• *Then* zero unshielded local cursor allocations exist in Java database managers and zero false positives are reported on cursor provider methods.

**Invariants**: Zero cursor leaks, zero `CursorIndexOutOfBoundsException`, deterministic sentinel return mapping, 100% clean-room test pass rate.

### REQ-DAT-026: Targeted Single-Row Query and Bounds-Checked Resume Reconstruction in TrackerService [`TST-DAT-021`]
- **Tests**: `TrackerService.java`
- **Implementation**: Targeted single-row query and bounds-checked resume reconstruction in TrackerService.
- **Rationale**: The system SHALL optimize crash recovery in `TrackerService.java` by constraining database access on `WorkoutSummaries.TABLE` to a targeted projection of required scalar columns and a single latest row, enclosed in try-with-resources with defensive cursor bounds checking (ATT-3549, parent epic ATT-431):
1. *Constrained Projection & Single-Row Index Scan (`REQ-DAT-026.1`)*:
• `TrackerService.recreateValuesWhenResuming()` SHALL NOT execute full-table queries (`SELECT * FROM WorkoutSummaries`).
• The query SHALL project strictly the 7 required scalar columns: `FILE_BASE_NAME`, `C_ID`, `TIME_TOTAL_s`, `TIME_ACTIVE_s`, `CALORIES`, `LAPS`, `DISTANCE_TOTAL_m`.
• The query SHALL order by `WorkoutSummaries.C_ID + " DESC"` with limit `"1"`.
2. *Deterministic Resource Management & Defensive Column Extraction (`REQ-DAT-026.2`)*:
• The `Cursor` instance SHALL be allocated inside a `try (Cursor cursor = ...)` block.
• Cursor navigation SHALL use `if (cursor != null && cursor.moveToFirst())`.
• Column extraction SHALL check column index validity (`getColumnIndexOrThrow`) before invocation of getter methods.
• `mWorkoutID` SHALL be extracted as a 64-bit integer (`cursor.getLong(...)`), aligning with `volatile long mWorkoutID`.
3. *Empty Table & Error Resilience (`REQ-DAT-026.3`)*:
• If `cursor == null` or `cursor.moveToFirst() == false` (empty database), the method SHALL log a warning (`Log.w(TAG, "recreateValuesWhenResuming: No workout summary record found to resume.")`), retain safe canonical sentinel defaults (`mWorkoutID = -1L`, `mBaseFileName = ""`, `mTimeTotal_s = 0`, `mTimeActive_s = 0`, `mDistanceTotal_m = 0.0`), and return cleanly without throwing `CursorIndexOutOfBoundsException` or `NullPointerException`.
4. *Resumed Telemetry Parity (`REQ-DAT-026.4`)*:
• When a valid workout record is present, the values assigned to `mBaseFileName`, `mWorkoutID`, `mTimeTotal_s`, `mTimeActive_s`, `calories`, `lapNr`, and `mDistanceTotal_m`, as well as values passed to `BANALService.setInitialSensorValue(...)`, SHALL match exactly the latest record's data.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-DAT-026`. Targets `TrackerService.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-3549`). Early legacy code used `cursor.moveToLast()` on an unbounded table query, which in modern databases causes memory pressure and crashes on empty tables.
3. *Root Reason for Existing Formulation*: Table size and blob streams grew over time, making full-table queries dangerous for 2MB CursorWindow limits.
4. *Preservation of Core Invariants*: Resume values and initial telemetry state broadcast to `BANALService` match the latest workout with 100% parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an interrupted workout resuming in `TrackerService`,
• *When* `recreateValuesWhenResuming()` executes,
• *Then* it queries strictly the 7 required columns with `ORDER BY _id DESC LIMIT 1` inside try-with-resources and restores state from the latest row.
• *Given* an empty `WorkoutSummaries` table,
• *When* `recreateValuesWhenResuming()` executes,
• *Then* it logs a warning and returns safely without throwing `CursorIndexOutOfBoundsException`.

**Invariants**: Exact state restoration parity, zero cursor leaks, zero out-of-bounds crashes, 100% clean-room test pass rate.

### REQ-DAT-026: Authoritative Cursor Mappers for Workout Cluster Engine and Equipment Data Provider Architecture [`TST-DAT-021`]
- **Tests**: `WorkoutClusterEngine.kt`, `EquipmentDataProvider.kt`, `WorkoutClusterRepository.kt`, `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterMapperTest.kt`
- **Implementation**: Authoritative cursor mappers for WorkoutClusterEngine and EquipmentDataProvider architecture (Rule 20, Rule 36).
- **Rationale**: The system SHALL eliminate manual inline cursor column extractions across workout clustering and equipment data provider layers by introducing typed `fromCursor` DTO mappers (`WorkoutBoundsDto`, `ClusterWorkoutCandidateDto`, `WorkoutEquipmentDto`) and centralizing batch key extraction in `WorkoutClusterDatabaseManager`, enforcing Rule 20 (Database & DTO Mapping Symmetry) and Rule 36 (Single Source of Truth & DRY) (ATT-3577, complementing `REQ-DAT-025`):
1. *Workout Bounds DTO Mapper (`WorkoutBoundsDto`)*:
• The system SHALL define `WorkoutBoundsDto(val minLat: Double?, val minLng: Double?, val maxLat: Double?, val maxLng: Double?)` with `val hasPoints: Boolean get() = minLat != null && minLng != null && maxLat != null && maxLng != null`.
• `WorkoutBoundsDto.companion.fromCursor(cursor: Cursor): WorkoutBoundsDto` SHALL extract `BOUND_MIN_LAT`, `BOUND_MIN_LNG`, `BOUND_MAX_LAT`, `BOUND_MAX_LNG` via `cursor.getColumnIndexOrThrow(...)` and `cursor.isNull(...)`, preserving null safety.
• `WorkoutClusterEngine.recalculateClusterAnchors` SHALL delegate cursor row extraction strictly to `WorkoutBoundsDto.fromCursor(c)`, completely eliminating direct `getColumnIndex` / `getColumnIndexOrThrow` calls.
2. *Cluster Workout Candidate DTO Mapper (`ClusterWorkoutCandidateDto`)*:
• The system SHALL define `ClusterWorkoutCandidateDto(val workoutId: Long, val workoutName: String?, val fileBaseName: String?, val sportId: Long, val distance: Double)`.
• `ClusterWorkoutCandidateDto.companion.fromCursor(cursor: Cursor): ClusterWorkoutCandidateDto` SHALL extract `C_ID`, `WORKOUT_NAME`, `FILE_BASE_NAME`, `SPORT_ID`, `DISTANCE_TOTAL_m` via `cursor.getColumnIndexOrThrow(...)`.
• `WorkoutClusterEngine.clusterExistingWorkouts` SHALL delegate candidate extraction strictly to `ClusterWorkoutCandidateDto.fromCursor(c)`, completely eliminating direct `getColumnIndex` / `getColumnIndexOrThrow` calls.
3. *Workout Equipment DTO Mapper (`WorkoutEquipmentDto`)*:
• The system SHALL define `WorkoutEquipmentDto(val sportId: Long, val equipmentId: Long)`.
• `WorkoutEquipmentDto.companion.fromCursor(cursor: Cursor): WorkoutEquipmentDto` SHALL extract `SPORT_ID` and `EQUIPMENT_ID` via `cursor.getColumnIndexOrThrow(...)`.
• `EquipmentDataProvider.getEquipmentData` SHALL delegate cursor reading strictly to `WorkoutEquipmentDto.fromCursor(cursor)`, completely eliminating direct `getColumnIndex` / `getColumnIndexOrThrow` calls while preserving `equipmentId > 0` short-circuit logic (`REQ-STB-005`).
4. *Clean Architecture Centralization of Batch Key Extraction*:
• `WorkoutClusterDatabaseManager` SHALL declare and implement `WorkoutBatchKey` and `fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys`.
• `WorkoutClusterRepository` SHALL maintain typealiases and delegate `fromCursorBatchKeys` to `WorkoutClusterDatabaseManager.fromCursorBatchKeys(cursor)`.
• `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt` SHALL contain strictly **0 occurrences** of `getColumnIndex` and `getColumnIndexOrThrow`.
5. *Repository Hygiene*:
• Stray scratch and backlog files accidentally added in commit `4f39e595` (`ATT-3362`) SHALL be permanently excised from the repository tree via `git rm`.
6. *Preservation of System Invariants*:
• Cursor lifecycle ownership (mappers do not advance, reset, or close cursors), spatial bounds calculation, cluster matching heuristics, equipment boundary guards, and 100% full-suite unit test pass rate MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Complements `REQ-DAT-025` (*Authoritative Database Cursor Row and Batch Mappers for Route and Cluster Repositories*). Formally tracked as `REQ-DAT-026`. Targets `WorkoutClusterEngine.kt`, `EquipmentDataProvider.kt`, `WorkoutClusterRepository.kt`, `WorkoutClusterDatabaseManager.kt`, and `WorkoutClusterMapperTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.3, commit `776d3fc0` implemented `REQ-DAT-025` for `RoutesDatabaseManager.kt` and `WorkoutClusterRepository.kt`, but left `WorkoutClusterEngine.kt` and `EquipmentDataProvider.kt` with manual cursor extraction blocks and placed mapper helpers directly inside `WorkoutClusterRepository.kt`.
3. *Root Reason for Existing Formulation*: ATT-3366 focused strictly on repository classes, deferring engine and UI provider mapper extraction.
4. *Preservation of Core Invariants*: Bounding coordinate null-safety, cluster matching distance threshold, equipment boundary short-circuiting, cursor lifecycle management, and 100% unit test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt`,
• *When* inspected by source code contract tests in `WorkoutClusterMapperTest.kt`,
• *Then* all three files contain strictly 0 occurrences of `getColumnIndex` or `getColumnIndexOrThrow`.
• *Given* a SQLite cursor positioned at workout bounds, candidate summary, or equipment row,
• *When* calling `WorkoutBoundsDto.fromCursor(cursor)`, `ClusterWorkoutCandidateDto.fromCursor(cursor)`, or `WorkoutEquipmentDto.fromCursor(cursor)`,
• *Then* all fields are mapped faithfully without moving or closing the cursor.
• *Given* `docs/attachments/` and `docs/engineering/backlog/`,
• *When* inspecting the git repository tree,
• *Then* the stray files accidentally added in `4f39e595` are absent.
• *Given* the complete unit test suite,
• *When* executing `./gradlew testDebugUnitTest`,
• *Then* 100% of tests pass without regression.

**Invariants**: Zero database schema alterations; strict cursor lifecycle ownership; 100% unit test pass rate.

### REQ-DAT-027: GoldenCheetah JSON File Export Format and Data Integrity Verification [`TST-DAT-022`]
- **Tests**: `GCFileWriter.java`, `GCFileWriterTest.kt`
- **Implementation**: GoldenCheetah JSON file export format and data integrity verification.
- **Rationale**: The system SHALL modernize and harden `GCFileWriter.java` conforming to the GoldenCheetah Ride JSON specification (ATT-2169):
1. *Pure Numeric JSON Sample Metrics (Locale-Independent)*:
• All sample telemetry metrics in GoldenCheetah JSON export (`KM`, `KPH`, `WATTS`, `CAD`, `NM`, `ALT`, `LRBALANCE`, `LTE`, `RTE`, `LPS`, `RPS`, `LAT`, `LON`) SHALL be serialized into the JSON `SAMPLES` array as raw JSON numeric literals (`Double` or `Int`).
• String formatting with `Locale.getDefault()` and decimal commas (e.g. `"12,345"`) SHALL be completely eliminated.
• `SECS` and `HR` SHALL be serialized as JSON integers.
2. *Complete GPS Sensor Validation and Double Precision*:
• Telemetry validation for GPS samples SHALL validate both `SensorType.LATITUDE.name()` and `SensorType.LONGITUDE.name()` via `dataValid(...)`.
• Longitude coordinate extraction SHALL use `cursor.getDouble(...)`, matching latitude and preserving full 64-bit IEEE 754 precision.
3. *Workout Laps and INTERVALS Block*:
• When recorded laps exist for the workout (`LapsDatabaseManager.getInstance(mContext).getLaps(workoutID)`), `GCFileWriter` SHALL serialize an `"INTERVALS"` JSON array nested under `"RIDE"`.
• Each interval item SHALL contain `NAME` (string, e.g. `"Lap 1"`), `START` (elapsed seconds integer), and `STOP` (elapsed seconds integer).
4. *Metadata Tags Alignment*:
• The `"TAGS"` block SHALL populate `"Sport"` (via `SportTypeDatabaseManager.getGcName`), `"Workout Code"` (trimmed), `"Workout Title"` (when present and non-empty), `"Notes"` (when present and non-empty), `"Device"` (`TrainingApplication.getAppName()`), and `"Data"` (sensor capabilities string).
5. *Thread-Safe, UTC-Anchored Start Time*:
• `STARTTIME` conversion SHALL execute using thread-safe date formatters configured with `TimeZone.getTimeZone("UTC")` and `Locale.US`, generating `"yyyy/MM/dd HH:mm:ss UTC"`.
6. *Resource Safety*:
• `BufferedWriter` and SQLite `Cursor` SHALL be managed within `try-with-resources` blocks to ensure unconditional cleanup upon normal exit or exceptional failure.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-DAT-027` (*GoldenCheetah JSON File Export Format and Data Integrity Verification*). Targets `GCFileWriter.java` and `GCFileWriterTest.kt`.
2. *Historical Origin & Commit Trace*: Early prototype in `GCFileWriter.java` (`FileFormat.GOLDEN_CHEETAH`).
3. *Root Reason for Existing Formulation*: Early implementation relied on `String.format(Locale.getDefault(), ...)` and basic tag structures.
4. *Preservation of Core Invariants*: The output file extension remains `.json`. Exporter contract with `BaseFileWriter`, `ExportManager`, and `ExportInfo` is strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded on a device operating in German locale (`Locale.GERMANY`),
• *When* exported to GoldenCheetah JSON via `GCFileWriter`,
• *Then* the output parses cleanly as standard JSON, telemetry values (`KM`, `KPH`, `WATTS`, `CAD`, `ALT`, `LAT`, `LON`) are JSON numbers (not strings), and no decimal commas appear anywhere in the document.
• *Given* sample data with GPS coordinates,
• *When* exported to GoldenCheetah JSON,
• *Then* both `LATITUDE` and `LONGITUDE` are checked for validity, and longitude retains identical double-precision fidelity as stored in SQLite.
• *Given* a workout with 2 or more recorded laps in `LapsDatabaseManager`,
• *When* exported to GoldenCheetah JSON,
• *Then* the JSON contains an `"INTERVALS"` array with exact start/stop seconds and lap titles matching the recorded laps.
• *Given* the complete unit test suite,
• *When* executing `./gradlew testDebugUnitTest`,
• *Then* 100% of tests pass without regression.

**Invariants**: GoldenCheetah file extension remains `.json`; zero regression in FIT/TCX/GPX exporters; 100% clean-room test pass rate MUST NOT be broken.

### REQ-MIG-039: Offline Power Training Metrics Calculation, Historical Athlete Threshold Resolution, and Import/Finalization Extrema Persistence [`TST-MIG-036`]
- **Tests**: `PowerPhysiologyCalculator.kt`, `AthleteMetricsDatabaseManager.kt`, `SettingsDataStoreJavaHelper.kt`, `ParsedWorkoutData.kt`, `FitWorkoutParser.kt`, `LegacyImportEngine.kt`, `TrackerService.java`, `CyclingPowerMetricsDevice.java`
- **Implementation**: Calculate and persist power-based training metrics (NP, IF, TSS, Work kJ, W/kg) when importing TCX, GPX, or FIT workout files and finalizing live sessions.
- **Rationale**: The system SHALL calculate and persist session-level power training metrics — Normalized Power ($\text{NP}$, in $\text{W}$), Intensity Factor ($\text{IF} = \text{NP} / \text{FTP}$), Training Stress Score ($\text{TSS} = \frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$), Mechanical Work ($\text{Work}$, in $\text{kJ}$), Power-to-Weight ($\text{W/kg}$), and Variability Index ($\text{VI} = \text{NP} / \text{Avg\_Power}$) — across `PowerPhysiologyCalculator`, `AthleteMetricsDatabaseManager`, `SettingsDataStoreJavaHelper`, `FitWorkoutParser`, `LegacyImportEngine.recalculateStats()`, and `TrackerService.finalizeLiveSession()` (ATT-3860, complementing `REQ-MIG-038`, `REQ-TRK-019`, `REQ-TRK-020`, `REQ-TRK-021`, and `REQ-UI-391`):
1. *Canonical Power Training Metrics Domain Calculator (`REQ-MIG-039.1` — Rule 36 & Rule 40)*:
• `PowerPhysiologyCalculator` SHALL implement pure, stateless functions `calculateIntensityFactor(npWatts, ftpWatts)` ($\text{NP} / \text{FTP}$), `calculateTss(activeTimeSeconds, npWatts, ifValue, ftpWatts)` ($\frac{T_{\text{active}} \cdot \text{NP} \cdot \text{IF}}{\text{FTP} \cdot 36.0}$), `calculateWorkKj(powerSamplesWatts, sampleIntervalSec)` ($\frac{\sum P_i \cdot \Delta t}{1000.0}$), `calculatePowerToWeight(powerWatts, weightKg)` ($P / m_{\text{kg}}$), `calculateVariabilityIndex(npWatts, avgPowerWatts)` ($\text{NP} / \text{Avg\_Power}$), and `computePowerTrainingMetrics(...)` returning `PowerTrainingMetricsResult`.
2. *Historical Athlete Profile Threshold Lookup (`REQ-MIG-039.2`)*:
• `AthleteMetricsDatabaseManager` SHALL provide `getEntryEffectiveAt(metricType, timestampMs)` returning the latest entry at or before `timestampMs` (falling back to `getLatestEntry(metricType)`).
• `SettingsDataStoreJavaHelper` SHALL provide `resolveEffectiveFtpAt(context, timestampMs)` and `resolveEffectiveWeightKgAt(context, timestampMs)`, falling back gracefully to `getFtp(context)` and `getAthleteWeightKg(context)`.
3. *FIT Parser Sensor Set & Optional Session Power Summary Extraction (`REQ-MIG-039.3`)*:
• `FitWorkoutParser.parse(...)` SHALL populate `foundSensors` with all detected `SensorType`s (including `SensorType.POWER`) and extract optional FIT `SessionMesg` power summary fields (`sessionNormalizedPowerWatts`, `sessionIntensityFactor`, `sessionTss`, `sessionWorkKj`) into `ParsedWorkoutData`.
4. *Extrema Persistence in Import & Live Finalization (`REQ-MIG-039.4`)*:
• `LegacyImportEngine.recalculateStats(...)` and `TrackerService.finalizeLiveSession()` SHALL compute `PowerTrainingMetricsResult` and persist `SensorType.NORMALIZED_POWER` (`AVG`, `END`), `SensorType.INTENSITY_FACTOR` (`AVG`, `END`), `SensorType.TSS` (`AVG`, `MAX`, `END`), `SensorType.WORK_KJ` (`AVG`, `MAX`, `END`), and `SensorType.POWER_TO_WEIGHT` (`AVG`, `MAX`) into `WorkoutSummariesDatabaseManager` (`TABLE_EXTREMA_VALUES`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-039`, complementing `REQ-MIG-038` (`ATT-3552`), `REQ-TRK-019` (`ATT-1289`), `REQ-TRK-020` (`ATT-1290`), `REQ-TRK-021` (`ATT-1291`), and `REQ-UI-391` (`ATT-3704`).
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-1289`, `ATT-1290`, `ATT-1291`) and Sprint 2026-42.5 (`ATT-3552`, commit `7062af56`).
3. *Root Reason for Existing Formulation*: `LegacyImportEngine.recalculateStats(...)` originally computed `MIN`/`MAX`/`AVG` SQL aggregates only over the 6 raw sample columns (`HR`, `CADENCE`, `POWER`, `SPEED_mps`, `ALTITUDE`, `TEMPERATURE`), before derived power physiology metrics and timestamped Athlete Profile thresholds existed.
4. *Preservation of Core Invariants*: Zero SQLite schema changes (`WorkoutSummaries.db` `DB_VERSION = 24`, `AthleteMetrics.db` `DB_VERSION = 1`), `SensorType` count (`44`), live 1-Hz `CyclingPowerMetricsDevice` warm-up and pause gating (`REQ-TRK-023`), and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a 1-hour (`3600 s`) steady power stream at $250\text{ W}$ with athlete $\text{FTP} = 250\text{ W}$ and body weight $75.0\text{ kg}$,
• *When* `PowerPhysiologyCalculator.computePowerTrainingMetrics` is evaluated,
• *Then* $\text{NP} = 250.0\text{ W}$, $\text{IF} = 1.00$, $\text{TSS} = 100.0$, $\text{Work} = 900.0\text{ kJ}$, $\text{VI} = 1.00$, and $\text{Avg W/kg} = 3.333\text{ W/kg}$.
• *Given* a TCX, GPX, or FIT import or finalized live session with power data,
• *When* `recalculateStats` or `finalizeLiveSession` runs,
• *Then* `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, and `POWER_TO_WEIGHT` extrema are persisted in `TABLE_EXTREMA_VALUES`.

**Invariants**: Zero SQLite schema changes, Rule 36 DRY calculator in `PowerPhysiologyCalculator`, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-MIG-040: Zip-Slip Path Traversal Protection, Symlink Rejection, Top-Level Whitelist & Bounded Stream Extraction in Backup Operations [`TST-MIG-037`]
- **Tests**: `MigrationEngine.kt`, `ImportEngine.kt`, `LegacyImportEngine.kt`, `BackupImportSecurityTest.kt`
- **Implementation**: Validate normalized path boundary (zip-slip defense), reject symbolic links, enforce top-level directory whitelist (databases/, shared_prefs/, datastore/), cap archive expansion with CountingInputStream, and sanitize Google Drive download paths.
- **Rationale**: `MigrationEngine` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/MigrationEngine.kt`), `ImportEngine` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportEngine.kt`), and `LegacyImportEngine` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`) SHALL validate the normalized path of every extracted archive entry against the destination boundary, reject all symbolic link entries and components, enforce a top-level directory whitelist for `.attbackup` archives (`databases/`, `shared_prefs/`, `datastore/`), protect against zip bomb resource exhaustion via streaming counting, and sanitize remote Google Drive file downloads (ATT-3982, extending `REQ-MIG-004` and `REQ-MIG-036`):
1. *Path Normalization & Canonical Boundary Validation (`REQ-MIG-040.1`)*:
• In `MigrationEngine.performFullRestore()`, `ImportEngine.unzipBackup()`, `ImportEngine.analyzeBackup()`, and `ImportEngine.performIncrementalImport()`, the system SHALL verify that for every `entry` in the zip archive, the target path resolves via `destPath.resolve(entry.name).normalize()` and satisfies `entryPath.startsWith(destPath)`.
• If an entry's normalized path falls outside `destPath` (e.g. `../../databases/evil.db`), extraction SHALL immediately abort, delete any partially extracted files in `destDir`, and throw a `SecurityException("Zip-slip path traversal attempt detected: " + entry.name)`.
2. *Symbolic Link Prohibition & TOCTOU Rejection (`REQ-MIG-040.2`)*:
• The system SHALL strictly prohibit extraction of symbolic links. If an archive entry is a symbolic link or if any parent directory of an entry is a symbolic link (`Files.isSymbolicLink(entryPath, LinkOption.NOFOLLOW_LINKS)`), the system SHALL immediately reject the entry and throw a `SecurityException("Symbolic links are strictly prohibited in backup archives: " + entry.name)`.
3. *Top-Level Directory Whitelist Validation (`REQ-MIG-040.3`)*:
• For `.attbackup` archives, all extracted file and directory entries SHALL reside strictly within one of the three canonical top-level directories: `databases/`, `shared_prefs/`, or `datastore/`.
• Any archive entry whose relative path does not start with one of these three canonical prefixes SHALL be rejected with `SecurityException("Invalid backup entry outside whitelisted directories: " + entry.name)`.
4. *CountingInputStream & Zip Bomb Defense (`REQ-MIG-040.4`)*:
• During streaming extraction, the underlying stream SHALL be tracked via a counting stream reader that enforces: (a) maximum entry count of 10,000 entries, (b) maximum single-file uncompressed size of 512 MB, and (c) maximum total uncompressed size of 1,024 MB (1 GB).
• If any limit is exceeded, extraction SHALL immediately terminate with `SecurityException("Archive exceeds maximum permitted size or entry limits")`.
5. *Strict Remote Filename Sanitization in `LegacyImportEngine` (`REQ-MIG-040.5`)*:
• In `LegacyImportEngine.kt:413`, remote Drive filenames SHALL be sanitized by extracting only the simple file name (`File(entry.name).name`). If the sanitized name is blank or contains path separators (`/`, `\`, `..`), the download SHALL be rejected, and the target file SHALL be verified to reside strictly within `workerDir`.
6. *Fail-Safe Staging Cleanup on Exception (`REQ-MIG-040.6`)*:
• In `performFullRestore`, `analyzeBackup`, and `performIncrementalImport`, if extraction fails or throws any `SecurityException` or `IOException`, all contents of the temporary staging directory SHALL be recursively deleted.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-040`, extending `REQ-MIG-004` and `REQ-MIG-036`. Targets `MigrationEngine.kt`, `ImportEngine.kt`, `LegacyImportEngine.kt`, and `BackupImportSecurityTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3982`, Sprint `2026-42.10`, discovered during independent code review of `feature/ATT-3921`.
3. *Root Reason for Existing Formulation*: Original archive unpacking in `MigrationEngine.kt` and `ImportEngine.kt` used standard `ZipInputStream` loops without canonical path validation, creating vulnerability CWE-22 (Zip Slip).
4. *Preservation of Core Invariants*: Valid `.attbackup` archives created by `BackupManager` continue to restore and import with 100% fidelity; temporary staging files cleaned up on failure; full restore and incremental import flows strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an archive with an entry containing `../` relative traversal,
• *When* `MigrationEngine.performFullRestore()` or `ImportEngine.analyzeBackup()` / `performIncrementalImport()` runs,
• *Then* extraction fails immediately, zero files outside `tempDir` are created or modified, and `SecurityException` is thrown / logged.
• *Given* an archive with an entry targeting `lib/libevil.so` or root files,
• *When* extraction runs,
• *Then* the entry is rejected as outside the whitelisted directories.
• *Given* an archive containing a symbolic link,
• *When* extraction runs,
• *Then* extraction aborts immediately with `SecurityException`.
• *Given* a valid `.attbackup` archive from `BackupManager`,
• *When* imported or restored,
• *Then* extraction succeeds cleanly with all databases and preferences placed in the staging directory.

**Invariants**: Valid `.attbackup` extraction fidelity, safe cleanup on failure, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-MIG-041: Pre-Restore Backup Validation, Schema Version Bounds, Connection Teardown, and Atomic Rollback Snapshot [`TST-MIG-038`]
- **Tests**: `MigrationEngine.kt`, `SecureZipExtractor.kt`, `TrainingApplication.java`, `FullRestoreValidationSecurityTest.kt`
- **Implementation**: Guarantee all-or-nothing full restore by validating archive contents, SQLite integrity, and schema versions before touching current data, with connection teardown and rollback snapshot.
- **Rationale**: `MigrationEngine` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/MigrationEngine.kt`), `SecureZipExtractor` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/SecureZipExtractor.kt`), and `TrainingApplication` (`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`) SHALL guarantee that a full restore operation never deletes or modifies current application state before validating archive structure, SQLite integrity, and schema versions; closes active database singletons prior to file replacement; creates a non-destructive rollback snapshot; rolls back automatically on any installation failure; and cleans up all temporary and rollback staging directories (ATT-3989, extending `REQ-MIG-040` and `REQ-MIG-004`):
1. *Pre-Restore Archive Structure Validation (`REQ-MIG-041.1`)*:
• Before deleting, modifying, or moving any production files in `dataDir`, `MigrationEngine` SHALL unpack the archive into a temporary directory and verify that `databases/` exists, is a non-empty directory, and contains core databases (at minimum `WorkoutSummaries.db`).
• If `databases/` is missing or contains zero valid databases, `performFullRestore` SHALL abort immediately, purge the temporary directory, leave existing user data 100% untouched, and return `false`.
2. *SQLite Integrity and Schema Version Compatibility Bounds (`REQ-MIG-041.2`)*:
• For every `.db` file in `tempDir/databases`, `MigrationEngine` SHALL open the database in read-only mode (`SQLiteDatabase.OPEN_READONLY`), execute `PRAGMA quick_check` asserting a result of `"ok"`, and query `PRAGMA user_version`.
• If `PRAGMA quick_check` fails, or if `user_version` of any database exceeds the current application's supported `DB_VERSION` (e.g. `WorkoutSummaries.db` > 25, `Routes.db` > 10, `TrackingViews.db` > 14, `SportType.db` > 2, etc.), `performFullRestore` SHALL reject the backup immediately without modifying current state, preventing infinite `SQLiteDowngradeException` startup crash loops.
3. *Database Connection Checkpointing and Teardown (`REQ-MIG-041.3`)*:
• Prior to moving restored databases into place, `MigrationEngine` SHALL checkpoint WAL files and safely close active database connections held by singletons to prevent SQLite database locks, file handle leaks, or WAL corruption.
4. *Atomic Staging and Rollback Snapshot (`REQ-MIG-041.4`)*:
• Before replacing `databases/`, `shared_prefs/`, and `datastore/`, `MigrationEngine` SHALL move current directories into a temporary rollback folder (`backup_rollback/`).
• Restored directories SHALL then be moved/copied into place.
• If ANY exception occurs during file copying or placement, `MigrationEngine` SHALL purge partially written target files, restore the original files from `backup_rollback/` back into place, clean up staging directories, and return `false` without loss of original user data.
5. *Lifecycle Cleanup and Startup Purge (`REQ-MIG-041.5`)*:
• Temporary unpacking directories (`restore_temp`) and rollback directories (`backup_rollback`) SHALL be cleaned up immediately upon completion or error.
• `SecureZipExtractor.purgeStaleStagingDirs` in `TrainingApplication.java:onCreate()` SHALL purge any lingering `backup_rollback` directories from interrupted executions.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-041`, extending `REQ-MIG-040` (`ATT-3982`) and `REQ-MIG-004`. Targets `MigrationEngine.kt`, `SecureZipExtractor.kt`, `TrainingApplication.java`, and `FullRestoreValidationSecurityTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3989`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921`.
3. *Root Reason for Existing Formulation*: Early implementation prioritized simple replacement and assumed only valid archives generated by the app would be restored, omitting pre-validation and rollback.
4. *Preservation of Core Invariants*: Full restore all-or-nothing atomicity, complete state replacement on success, progress UI reporting, and 100% clean-room test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an archive missing `databases/` or containing a corrupted database,
• *When* `MigrationEngine.performFullRestore` is executed,
• *Then* the restore is rejected, temporary files are cleaned up, and existing databases, preferences, and datastore remain 100% intact.
• *Given* an archive with a database whose `PRAGMA user_version` exceeds the current app's supported version,
• *When* `performFullRestore` is executed,
• *Then* the restore is rejected without modifying local state, preventing startup downgrade crashes.
• *Given* an I/O failure or exception occurs while moving files into place,
• *When* restore fails midway,
• *Then* the rollback snapshot restores the previous state completely and returns `false`.
• *Given* a valid archive matching current or older schema versions,
• *When* `performFullRestore` is executed,
• *Then* all databases, preferences, and datastore files are restored, staging folders are purged, and the app is restarted.

**Invariants**: Zero data loss on failed or invalid restores, all-or-nothing atomicity, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-MIG-042: Samples Table Name Escaping, Case-Insensitive Deduplication, Deterministic Disambiguation & Structural DDL Construction [`TST-MIG-039`]
- **Tests**: `WorkoutSamplesDatabaseManager.java`, `ImportEngine.kt`, `LegacyImportEngine.kt`
- **Implementation**: Guarantee SQL injection immunity, case-insensitive collision protection, and safe structural table construction during workout imports.
- **Rationale**: The system SHALL guarantee SQL injection immunity, case-insensitive collision protection, and safe structural table construction during workout imports (ATT-3991):
1. *Safe Identifier Escaping (`WorkoutSamplesDatabaseManager.java`)*:
• `WorkoutSamplesDatabaseManager.getTableName(String workoutName)` SHALL enclose the workout identifier in double quotes, escaping every embedded double-quote character (`"`) as two double-quotes (`""`) per SQL-92 and SQLite identifier quoting standards.
• Null, empty, blank, or literal "null" inputs MUST be rejected with `IllegalArgumentException`.
2. *Case-Insensitive Duplicate Detection (`ImportEngine.kt`, `LegacyImportEngine.kt`)*:
• Workout duplicate checks querying `WorkoutSummaries.FILE_BASE_NAME` SHALL evaluate candidate names using `COLLATE NOCASE`.
• If a candidate workout has the same `FILE_BASE_NAME` (case-insensitive) AND matches the start timestamp `TIME_START`, the system SHALL classify it as a duplicate and skip importing without modifying existing database rows or sample tables.
3. *Deterministic Collision Disambiguation & Sub-Second Guard (`ImportEngine.kt`, `LegacyImportEngine.kt`)*:
• If an incoming workout collides on `FILE_BASE_NAME` (case-insensitive) or table existence in `WorkoutSamples.db` with a different start timestamp, the system SHALL NOT drop the existing table.
• The system SHALL deterministically disambiguate the base file name using the workout's start epoch seconds (`${baseFileName}_${epochSeconds}`), guarded against sub-second collisions with a monotonic counter, updating both `WorkoutSummaries.FILE_BASE_NAME` and the newly created samples table.
4. *Structural DDL Construction & Injection Elimination (`ImportEngine.kt`)*:
• `ImportEngine.copySamplesTable` SHALL NOT execute raw SQL statements from external backup archives.
• The table schema SHALL be structurally inspected via `PRAGMA table_info`, column identifiers validated against `^[a-zA-Z0-9_]+$`, data types whitelisted against `INTEGER`, `REAL`, `TEXT`, `BLOB`, `NUMERIC`, and the `CREATE TABLE IF NOT EXISTS` statement constructed locally using `WorkoutSamplesDatabaseManager.getTableName(targetBaseName)`.
• The method SHALL NOT execute unconditional `DROP TABLE IF EXISTS` on colliding tables.
5. *Pre-Flight Migration & Dirty State Healing (`WorkoutSummariesDatabaseManager.java`)*:
• On application upgrade, the system SHALL execute a one-time pre-flight check scanning for historical case-insensitive collisions in `WorkoutSummaries`, healing colliding entries deterministically before new imports run.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-042`, extending `REQ-MIG-031` (`ATT-2023`) and `REQ-TRK-026` (`ATT-3980`). Targets `WorkoutSamplesDatabaseManager.java`, `ImportEngine.kt`, and `LegacyImportEngine.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3991`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921`.
3. *Root Reason for Existing Formulation*: Early implementation relied on unescaped identifier wrapping (`"\"" + name + "\""`) and binary collation, allowing case variants (`morning_ride` vs `Morning_Ride`) to trigger `DROP TABLE` on existing tables.
4. *Preservation of Core Invariants*: Preservation of existing file-based table naming scheme without migrating to synthetic names, O(log N) indexed queries via `COLLATE NOCASE`, and 100% clean-room test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an existing workout `morning_ride` with samples in `WorkoutSamples.db`,
• *When* an archive or file with `Morning_Ride` and distinct timestamp is imported,
• *Then* the existing `morning_ride` table is NOT dropped, all existing samples are preserved, and the new workout is disambiguated with its own distinct table.
• *Given* a workout or file name containing embedded double-quotes (e.g. `My "Fast" Ride`),
• *When* `WorkoutSamplesDatabaseManager.getTableName` resolves the table identifier,
• *Then* double-quotes are escaped as `""` and SQLite table creation/queries succeed without syntax errors or injection.
• *Given* an external archive containing malicious DDL in `sqlite_master`,
• *When* `ImportEngine.copySamplesTable` runs,
• *Then* raw SQL execution is bypassed, schema is structurally extracted via `PRAGMA table_info`, and safe table creation is enforced.

**Invariants**: Zero data loss on case-insensitive collisions, strict SQL identifier escaping, structural DDL whitelisting, and 100% clean-room test pass rate.

### REQ-MIG-043: Atomic Cross-Database Import via SQLite ATTACH, Dedicated Scoped Connection, Shared ReentrantLock, Non-Destructive Orphan Repair, insertOrThrow Enforcement & Cooperative Coroutine Responsiveness [`TST-MIG-040`]
- **Tests**: `ImportEngine.kt`, `LegacyImportEngine.kt`, `BackupManager.kt`
- **Implementation**: Enforce atomic cross-database import via dedicated SQLite ATTACH connection, shared ReentrantLock, non-destructive orphan repair diagnostics, insertOrThrow, and cooperative coroutine cancellation.
- **Rationale**: `ImportEngine` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportEngine.kt`), `LegacyImportEngine` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`), and `BackupManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt`) SHALL enforce true cross-database ACID transaction atomicity via SQLite `ATTACH DATABASE` on dedicated non-singleton connections, serialize concurrent database access with a shared `ReentrantLock` during the `ATTACH` window across import and backup operations, provide non-destructive diagnostic orphan detection, eliminate silent `db.insert()` failures with `insertOrThrow()`, enforce static schema intersection with explicit `NOT NULL` defaults, invert legacy import side-effects, and adhere strictly to coroutine cooperative cancellation and non-blocking backoff (ATT-3992, extending `REQ-MIG-042` and `REQ-MIG-031`):
1. *Dedicated Connection Lifecycle & Absolute Path ATTACH (`REQ-MIG-043.1`)*:
• In `ImportEngine`, the system SHALL NOT use the global application singleton connection for `ATTACH DATABASE`. Instead, it SHALL open a dedicated, scoped `SQLiteDatabase` connection (`dedicatedSummariesDb`) strictly for the duration of the multi-database import.
• Target database paths SHALL be validated as canonical absolute paths on the local filesystem before executing `ATTACH DATABASE ? AS lapsDb` and `ATTACH DATABASE ? AS samplesDb`.
• The entire import of each workout (summary insertion in `tblWorkoutSummaries`, related rows in `extrema_values` and `accumulated_sensors`, laps in `lapsDb.tblLaps`, and sample table creation/population in `samplesDb`) SHALL execute inside a single SQLite transaction on this dedicated connection (`dedicatedSummariesDb.beginTransaction()`).
• If ANY failure, constraint violation, or coroutine cancellation occurs, SQLite SHALL automatically abort and roll back all mutations across `main`, `lapsDb`, and `samplesDb` simultaneously, preventing orphan rows or partial sample tables.
• In an outer `finally` block, `dedicatedSummariesDb` SHALL explicitly execute `DETACH DATABASE lapsDb` and `DETACH DATABASE samplesDb` (even if `beginTransaction()` fails or throws), and cleanly close `dedicatedSummariesDb`, guaranteeing zero connection or attachment leakage.
2. *Shared Database Concurrency Lock Across Import and Backup (`REQ-MIG-043.2`)*:
• The system SHALL enforce a shared `ReentrantLock` (`DatabaseConnectionManager.databaseLock` / `importConcurrencyLock`) across `ImportEngine`, `LegacyImportEngine`, and `BackupManager` to serialize access to `WorkoutSummaries.db`, `Laps.db`, and `WorkoutSamples.db` during the `ATTACH` window and backup archives, eliminating WAL lock contention and `SQLiteDatabaseLockedException`.
3. *Non-Destructive Diagnostic Orphan Repair Tool (`REQ-MIG-043.3`)*:
• The system SHALL NOT execute automated, destructive table drops or row deletions on startup.
• Instead, an orphan diagnostic helper (`OrphanDataDiagnosticHelper`) SHALL scan `WorkoutSamples.db` for unreferenced tables and scan child tables for invalid `workout_id <= 0`, logging diagnostic findings and providing a non-destructive repair routine that verifies backup presence before executing safe cleanup.
4. *Strict `insertOrThrow` Enforcement & ID Validation (`REQ-MIG-043.4`)*:
• In `insertSummaryWithResolution`, `copyRelatedRows`, `copyLaps`, and `LegacyImportEngine`, `db.insert()` returning `-1` SHALL be replaced with `db.insertOrThrow()`.
• `insertSummaryWithResolution` and `LegacyImportEngine` SHALL explicitly assert that the returned row ID satisfies `newId > 0`. If non-positive, an `IllegalStateException` or `SQLiteException` SHALL be thrown immediately, preventing downstream copy operations with `workout_id = -1`.
5. *Static Schema Mapping & Explicit `NOT NULL` Column Defaults (`REQ-MIG-043.5`)*:
• In `copyRelatedRows` and `copyLaps`, source row columns SHALL be matched against a static target table schema whitelist. Unrecognized columns SHALL be discarded.
• If required `NOT NULL` columns are missing from older archives, explicit safe default values SHALL be supplied: `WORKOUT_ID = newId`, `SENSOR_TYPE = 0`, `MIN_VALUE = 0.0`, `MAX_VALUE = 0.0`, `AVG_VALUE = 0.0`, `VALUE = 0.0`, `DISTANCE = 0.0`, `LAP_NUMBER = 1`, `START_TIME = 0L`, `DURATION = 0L`.
6. *Legacy Import Pipeline Inversion & Transactional Staging (`REQ-MIG-043.6`)*:
• In `LegacyImportEngine.saveParsedWorkout()`, all input preconditions (specifically `firstTime != null`) SHALL be validated BEFORE performing any database modifications.
• Dynamic sample table creation in `WorkoutSamples.db` MUST NOT occur prior to summary insertion. Summary insertion via `insertOrThrow` and sample table creation/population SHALL be enclosed inside an atomic transaction block.
• If summary insertion fails, statistical recalculation fails, or coroutine cancellation occurs, all partially created tables or rows MUST be rolled back cleanly.
7. *Cooperative Coroutine Cancellation & Non-Blocking Delay (`REQ-MIG-043.7`)*:
• In `LegacyImportEngine.kt`, all worker catch blocks SHALL explicitly catch `CancellationException` and rethrow it immediately without incrementing failure counters or swallowing cancellation.
• In `LegacyImportEngine.downloadFileWithRetry` and `downloadGoogleDriveFileWithRetry`, all blocking `Thread.sleep()` calls SHALL be replaced with suspending `delay()` calls.
• The start of each import loop iteration SHALL invoke `coroutineContext.ensureActive()` to provide immediate responsiveness to user cancellation.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-043`, extending `REQ-MIG-042` (`ATT-3991`) and `REQ-MIG-031` (`ATT-2023`). Targets `ImportEngine.kt`, `LegacyImportEngine.kt`, `BackupManager.kt`, and `WorkoutImportAtomicityTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3992`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921`.
3. *Root Reason for Existing Formulation*: Historical separation into 3 databases without unified transaction handles caused partial commits; `db.insert()` silently returned -1 leading to orphan child rows; legacy download code used `Thread.sleep` from pre-coroutine era.
4. *Preservation of Core Invariants*: Full backup import fidelity, duplicate workout detection, sport/equipment resolution, and 100% clean-room test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an import with invalid or conflicting data in `WorkoutSummaries`,
• *When* `insertSummaryWithResolution` fails,
• *Then* `insertOrThrow` throws an exception, zero rows with `workout_id = -1` are inserted into `extrema_values`, `accumulated_sensors`, or `laps`, and no orphan samples table exists in `WorkoutSamples.db`.
• *Given* an error occurs while copying samples or laps,
• *When* the transaction rolls back,
• *Then* the summary row in `WorkoutSummaries.db`, lap rows in `Laps.db`, and the samples table in `WorkoutSamples.db` are completely rolled back atomically.
• *Given* failure during the `ATTACH DATABASE` command itself,
• *When* the exception is caught,
• *Then* the outer `finally` block executes safe detach/cleanup and closes the connection without leaving dangling transactions.
• *Given* concurrent backup or import requests during the `ATTACH` window,
• *When* `DatabaseConnectionManager.databaseLock` is held,
• *Then* operations serialize cleanly without `SQLiteDatabaseLockedException`.
• *Given* a running bulk import job in `LegacyImportEngine`,
• *When* the parent coroutine job is cancelled,
• *Then* `CancellationException` propagates immediately, workers terminate without executing subsequent downloads, and background threads are not blocked.

**Invariants**: True cross-database ACID atomicity on dedicated scoped connection, shared lock with BackupManager, zero orphan rows, non-blocking coroutine dispatch, and 100% clean-room test pass rate.

### REQ-MIG-044: Non-Destructive SportType Schema Upgrade, Custom Sport Preservation, Post-v2 Data Healing Reconciliation, Placeholder Flagging & Strict Downgrade Guard [`TST-MIG-041`]
- **Tests**: `SportTypeDatabaseManager.java`, `SportTypeDbHelper`, `SportTypeSelectorContract.kt`
- **Implementation**: Ensure sport type database upgrades never drop user-defined sport types, preserve stable primary keys, heal post-v2 corrupted states via resurrected placeholder sport types, protect sqlite_sequence atomically, isolate placeholders from active workout selection, and strictly reject downgrades.
- **Rationale**: `SportTypeDatabaseManager` (`app/src/main/java/com/atrainingtracker/banalservice/database/SportTypeDatabaseManager.java`) and `SportTypeSelectorContract` SHALL guarantee that upgrading `SportType.db` never drops or recreates `SportTypes` table, strictly preserves all user-defined sport types and primary key IDs, atomically migrates `WALK` base sport type to `RUN`, introduces `is_placeholder INTEGER DEFAULT 0` column in Schema v3, heals post-v2 corrupted databases by resurrecting referenced orphan IDs as flagged placeholders, synchronizes `sqlite_sequence` to eliminate primary key collision vulnerabilities, isolates placeholders from active workout selection, and strictly prohibits database downgrades (ATT-3993, extending `REQ-MIG-043`):
1. *Non-Destructive Schema Upgrade & Version 3 Evolution (`REQ-MIG-044.1`)*:
• In `SportTypeDbHelper`, `DB_VERSION` SHALL be incremented to `3`.
• `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)` SHALL execute non-destructively within a single atomic transaction (`db.beginTransaction()`). The legacy destructive statement `db.execSQL("drop table if exists " + SportType.TABLE)` SHALL be permanently eliminated.
• For migrations where `oldVersion < 2`: the system SHALL update `WALK`'s `baseSportType` to `RUN` using an SQL `UPDATE` statement targeting `_id = 4` or matching UI name where `baseSportType = 'UNKNOWN'`.
• For migrations where `oldVersion < 3`: the system SHALL execute `ALTER TABLE SportTypes ADD COLUMN is_placeholder INTEGER DEFAULT 0;` (using schema inspection to ensure idempotency).
• The system SHALL verify and ensure that canonical default sport types 1..5 (`OTHER`, `RUN`, `BIKE`, `WALK`, `MTB`) exist without altering or re-keying any user-defined custom sport types (`_id >= 6`).
2. *Post-v2 Corrupted State Data Healing & Orphan Resurrection (`REQ-MIG-044.2`)*:
• `SportTypeDatabaseManager` SHALL execute a non-destructive data healing scan (`reconcileOrphanSportTypes`).
• The scan SHALL query `WorkoutSummaries.db` (`WorkoutSummaries.SPORT_ID`), `Equipment.db` (`SportTypeEquipmentLinkManager.COLUMN_SPORT_ID`), and `WorkoutClusters.db` (`WorkoutClusterContract.COLUMN_PROBABLE_SPORT_ID`) to collect all referenced `sportId`s.
• If any referenced ID $X$ ($X > 5$) does not exist in `SportTypes`, the system SHALL insert a resurrecting placeholder row with explicit primary key `_id = X`, `UIName = "Restored Sport " + X` (or localized string resource), `baseSportType = "UNKNOWN"`, and `is_placeholder = 1`.
• Workouts and equipment links referencing $X$ SHALL immediately resolve to this entity, preventing fallback to `OTHER` or broken linkages.
3. *Transactional Sequence Atomicity & Protection Against Primary Key Hijacking (`REQ-MIG-044.3`)*:
• The system SHALL execute the `sqlite_sequence` update within the same atomic transaction as the placeholder insertions in `reconcileOrphanSportTypes` or `onUpgrade`. The transaction SHALL calculate `maxId = MAX(MAX(SportTypes._id), MAX(WorkoutSummaries.sportId), 5)`.
• It SHALL synchronize SQLite's auto-increment counter via `INSERT OR REPLACE INTO sqlite_sequence (name, seq) VALUES ('SportTypes', MAX(COALESCE((SELECT seq FROM sqlite_sequence WHERE name = 'SportTypes'), 0), :maxId));`.
• This guarantees that placeholder creation and sequence synchronization commit atomically, ensuring no window where placeholders exist but the sequence is uncommitted, strictly preventing ID collisions with historical workout records.
4. *UI Isolation of Placeholders from Active Workout Pickers (`REQ-MIG-044.4`)*:
• In `SportTypeSelectorContract` / active workout sport selection pickers, sport types having `is_placeholder == 1` SHALL be filtered out of active selection lists.
• In historical workout summary cards and lists, placeholder sport types SHALL render safely displaying their placeholder name without application crash or layout corruption.
• In the Sport Type management settings screen, placeholder entries SHALL display with badge "Restored (Unconfigured)". If the athlete edits and saves the sport type, `is_placeholder` SHALL be set to `0`, graduating it to an active user-managed sport type.
5. *Strict Downgrade Prohibition (`REQ-MIG-044.5`)*:
• `SportTypeDbHelper.onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion)` SHALL throw an explicit `SQLiteException` refusing to downgrade, eliminating schema corruption or accidental table destruction.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-044`, extending `REQ-MIG-043` (`ATT-3992`). Targets `SportTypeDatabaseManager.java`, `SportTypeDbHelper`, and `SportTypeSelectorContract.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3993`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921`. Commit `7339ebc5` (Feb 8, 2026) bumped DB_VERSION to 2 with stubbed `drop table if exists` in `onUpgrade`.
3. *Root Reason for Existing Formulation*: `onUpgrade` was initially scaffolded as a quick stub with `// TODO: alter table instead of deleting!`. When DB_VERSION was bumped to 2 for WALK's base sport change, `onUpgrade` was never updated to incremental migration, causing user custom sport types to be destroyed on upgrade.
4. *Preservation of Core Invariants*: Stable primary keys for custom sport types, WALK base sport type as RUN, default sport types 1..5 canonical IDs, transactional sequence atomicity, and 100% clean-room test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a SportType database at version 1 containing user-created sport types (`_id = 6`, `_id = 7`),
• *When* upgraded to version 3,
• *Then* user-created sport types and their primary key IDs are fully preserved, `WALK` has `baseSportType = 'RUN'`, and `is_placeholder` column is added.
• *Given* a post-v2 database where custom sport types were dropped and `WorkoutSummaries` references `sportId = 6`,
• *When* data healing runs,
• *Then* a placeholder sport type with `_id = 6` and `is_placeholder = 1` is resurrected atomically with `sqlite_sequence` set $\ge 6$.
• *Given* an active workout sport selection picker,
• *When* listing available sport types,
• *Then* sport types with `is_placeholder = 1` are excluded.
• *Given* an attempt to downgrade `SportType.db`,
• *When* `onDowngrade` is invoked,
• *Then* `SQLiteException` is thrown and the database is not dropped or modified.

**Invariants**: Zero data loss on upgrades, stable primary keys, atomic migrations and sequence sync, active picker isolation of placeholders, and 100% clean-room test pass rate.

### REQ-DAT-028: UTC-Anchored Binary FIT Export, Transient Lap Synthesis, Fail-Fast Stream Error Propagation, and Application Context Lifetime [`TST-DAT-023`]
- **Tests**: `FitFileWriter.java`, `FitFileWriterTest.kt`
- **Implementation**: Ensure FIT exports encode true UTC timestamps using thread-safe DateTimeFormatter, synthesize a transient LapMesg for lap-less workouts supporting pauses without modifying Laps.db, fail fast on errors, delete partial files on failure, and use application context.
- **Rationale**: `FitFileWriter` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.java`) SHALL guarantee that all exported Garmin FIT activity files (`.fit`) encode timestamps in true UTC matching SQLite `CURRENT_TIMESTAMP` ground truth regardless of host device timezone using thread-safe `java.time.format.DateTimeFormatter`, synthesize a single transient in-memory `LapMesg` for unsegmented workouts preserving active `totalTimerTime` and wall-clock `totalElapsedTime` (supporting workouts with pauses) to satisfy FIT Protocol V2.0 compliance without mutating `Laps.db`, fail fast with `ExportResult(false)` upon timestamp parse errors, negative durations, or sample read exceptions without publishing truncated files, clean up partially written `.fit` files upon failure, and bind to `getApplicationContext()` to prevent Activity lifecycle leaks (ATT-3994, extending `REQ-DAT-018`):
1. *Strict UTC Timezone Anchoring with Thread-Safe `DateTimeFormatter` (`REQ-DAT-028.1`)*:
• `FitFileWriter` SHALL use thread-safe `java.time.format.DateTimeFormatter` anchored to `ZoneOffset.UTC` (pattern `"yyyy-MM-dd HH:mm:ss"`) for all timestamp conversions (`startDate`, `sampleDate`, `lapStart`, `endDate`), eliminating legacy `SimpleDateFormat` thread-safety and device offset shifting.
• Host device local timezone offsets (e.g. CEST UTC+2, EST UTC-5) SHALL NOT shift epoch timestamps in `FileIdMesg`, `RecordMesg`, `LapMesg`, `SessionMesg`, or `ActivityMesg`.
2. *Transient Synthesized Lap & Timer vs Elapsed Parity (`REQ-DAT-028.2`)*:
• When `LapsDatabaseManager.getInstance(mContext).getLaps(workoutID)` returns null or an empty list, `FitFileWriter` SHALL synthesize and encode exactly one `LapMesg` covering the full workout duration: `startTime = startDate`, `timestamp = sessionEndDate`, `totalElapsedTime = (float) totalElapsedTimeSec`, `totalTimerTime = (float) totalTimerSec`, `totalDistance = (float) distTotal`, `avgSpeed = (float) (distTotal / totalTimerSec)`, `intensity = Intensity.ACTIVE`, `sport = sport`, `subSport = subSport`.
• Workouts with pauses (`totalTimerTime < totalElapsedTime`) SHALL be fully supported and correctly encoded across `LapMesg`, `SessionMesg`, and `ActivityMesg`.
• `sessionMesg.setNumLaps(1)`, `totalElapsedTime`, and `totalTimerTime` SHALL strictly align with the synthesized lap per FIT Protocol V2.0.
• Transient Invariant: The synthesized lap SHALL be emitted solely into the binary `FileEncoder` stream and SHALL NOT mutate, insert, or write back to `Laps.db` (`tblLaps`).
3. *Fail-Fast Error Handling, Metric Sanity & Filesystem Cleanup (`REQ-DAT-028.3`)*:
• If `startTime` is null, empty, or unparseable, or if `totalTime` is negative, `FitFileWriter` SHALL NOT fall back to `new Date()`. It SHALL immediately return `new ExportResult(false, false, ...)` with a descriptive error message.
• If an exception occurs during sample querying or record writing in the `samplesDb.query` cursor loop, `FitFileWriter` SHALL NOT swallow the exception or proceed to write summary messages. It SHALL abort encoding, log the error, and return `new ExportResult(false, false, "FIT export failed during sample encoding: " + e.getMessage())`.
• On any failure or exception, `FitFileWriter` in `finally` SHALL close the `FileEncoder` and clean up any partially created `.fit` file from the output directory (`outputFile.delete()`). If file deletion fails, the `ExportResult` SHALL explicitly report the cleanup failure.
• Failing fast SHALL notify `ExportWorker` (`informOthersFailed`), set `ExportStatus.FINISHED_ERROR`, notify the athlete, and prevent broken/truncated file upload to cloud services (Dropbox, Google Drive, Strava).
4. *Safe Application Context Lifetime (`REQ-DAT-028.4`)*:
• `FitFileWriter` constructor SHALL pass `context.getApplicationContext()` to `super(context.getApplicationContext())`, ensuring long-running export tasks cannot leak Activity instances.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-DAT-028`, extending `REQ-DAT-018` (`ATT-1827`) under Epic `ATT-1117` (*FIT File Format Support*). Targets `FitFileWriter.java` and `FitFileWriterTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3994`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921`.
3. *Root Reason for Existing Formulation*: `FitFileWriter` inherited unconfigured `SimpleDateFormat` pattern from legacy text exporters (`TCXFileWriter`), which inadvertently shifted binary FIT timestamps by the device UTC offset. Defensive try-catches swallowed exceptions, outputting truncated files marked as success.
4. *Preservation of Core Invariants*: Garmin FIT Protocol V2.0 compliance, semicircle GPS scaling ($2^{31}/180.0$), sensor metric encoding, `Laps.db` persistence immutability, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded on a device operating in CEST (UTC+2) with start time "2026-07-01 10:00:00",
• *When* exported to FIT via `FitFileWriter`,
• *Then* `fileIdMesg.timeCreated` and `record.timestamp` decoded by Garmin FIT SDK equal 2026-07-01 10:00:00 UTC (not shifted by 2 hours).
• *Given* a workout with zero laps in `Laps.db`,
• *When* exported to FIT,
• *Then* the output file contains exactly one valid `LapMesg` matching session duration and distance, `sessionMesg.numLaps == 1`, `total_timer_time` and `total_elapsed_time` match between `LapMesg` and `SessionMesg`, and `Laps.db` is not modified.
• *Given* a workout containing substantial pauses (e.g. 10 minutes pause duration),
• *When* exported to FIT,
• *Then* export succeeds, encoding `totalTimerTime < totalElapsedTime` consistently in `LapMesg` and `SessionMesg`.
• *Given* an error occurs during sample traversal or validation fails,
• *When* `FitFileWriter.doExport` executes,
• *Then* it returns `ExportResult.success() == false`, deletes any partial `.fit` file from disk, no truncated file is declared successful, and `ExportWorker` halts cloud uploads.
• *Given* a historical workout previously recorded,
• *When* re-exported,
• *Then* valid UTC timestamps are produced without modifying database records.

**Invariants**: Existing TCX/GPX/CSV/JSON exporters retain behavior; `Laps.db` immutability; 100% clean-room test pass rate.

### REQ-DAT-029: Resilient Background Export & Upload Pipeline, Attempt Capping, Cooperative Cancellation, Transactional Upload Remediation, and Stuck State Recovery [`TST-DAT-024`]
- **Tests**: `BaseExporter.java`, `ExportWorker.kt`, `ExportManager.java`, `ExportStatusDatabaseManager.java`, `WorkoutDeletionHelper.java`
- **Implementation**: Ensure export jobs never retry infinitely, do not get stuck in PROCESSING on uncaught exceptions, atomically fail dependent uploads, cancel cooperatively without orphaned resources, and strictly synchronize invalidation tags.
- **Rationale**: `BaseExporter` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/BaseExporter.java`), `ExportWorker` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportWorker.kt`), `ExportManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportManager.java`), `ExportStatusDatabaseManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/db/ExportStatusDatabaseManager.java`), and `WorkoutDeletionHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelper.java`) SHALL guarantee that export operations never retry infinitely upon deterministic failures, do not abandon database status in `PROCESSING` on uncaught exceptions, atomically fail dependent upload rows upon file generation failure in strict sequence, execute cooperative cancellation at the start of sample loops and immediately after blocking I/O without orphaned resources or partial files, strictly synchronize WorkManager tags and unique work names during invalidation, sanitize inputs at entry, maintain periodic heartbeat timestamps, lazily recover stranded states exceeding `MAX_EXPORT_DURATION_MS` (15 minutes) of silence guarded by double-checked WorkManager liveness without premature false positives on long workouts, and safely delete workouts without `IllegalArgumentException` crashes (ATT-3995, extending `REQ-DAT-028` and `REQ-DAT-018`):
1. *Deterministic vs Retryable Error Classification (`REQ-DAT-029.1`)*:
• `BaseExporter.export()` SHALL catch all `Throwable` instances, preventing any unhandled exception from escaping.
• Deterministic errors (`ParseException`, `JSONException`, `IllegalArgumentException`, `NullPointerException`, `IllegalStateException`, `RuntimeException`) SHALL be classified as non-retryable (`shallRetry = false`).
• Transient `IOException` (e.g. network/socket drops during upload or storage temporarily unavailable) SHALL be permitted to request retry (`shallRetry = true`).
2. *WorkManager Retry Capping & Backoff Policy (`REQ-DAT-029.2`)*:
• `ExportWorker` SHALL define `MAX_RETRIES = 3`.
• When `result.shallRetry()` is true and `runAttemptCount < MAX_RETRIES`, `ExportWorker` SHALL update database status to `WAITING` with retry attempt information and return `Result.retry()`.
• When `runAttemptCount >= MAX_RETRIES` or `result.shallRetry()` is false, `ExportWorker` SHALL invoke `informOthersFailed()` and return `Result.failure()`, terminating the work chain.
3. *Cooperative Cancellation & Resource Cleanup (`REQ-DAT-029.3`)*:
• `BaseExporter` SHALL expose a thread-safe `CancellationSignal` interface and `setCancellationSignal(CancellationSignal)` method.
• `ExportWorker` SHALL wire `isStopped` into `mExporter.setCancellationSignal { isStopped }`.
• All sample cursor loops in file writers (`BaseFileWriter`, `FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`, `CSVFileWriter`, `GCFileWriter`) SHALL check `isCancelled()` at the *start of every loop iteration AND immediately after any blocking I/O operation*, throwing `CancellationException` immediately upon worker cancellation.
• On cancellation, all open streams and SQLite cursors SHALL be safely closed in `finally`/`try-with-resources`, partial output files SHALL be deleted, and `ExportResult(false, false, "Export cancelled")` returned without hanging.
• If `isStopped` is detected in `ExportWorker`, it SHALL exit cleanly with `Result.failure()` without overwriting database status.
4. *Unique Work Enqueuing & Tagged Invalidation (`REQ-DAT-029.4`)*:
• `ExportManager` SHALL centralize unique work naming in `getUniqueWorkName(fileBaseName, fileFormat)` returning `"export_" + fileBaseName + "_" + fileFormat.name()`.
• Every `OneTimeWorkRequest` SHALL be tagged with both `fileBaseName` and `exportInfo.toString()`.
• `ExportManager.startFullExportProcess()` SHALL enqueue work via `WorkManager.beginUniqueWork(getUniqueWorkName(fileBaseName, fileFormat), ExistingWorkPolicy.REPLACE, fileCreationWork)`.
• `invalidateWorkoutExport(fileBaseName)` SHALL synchronously cancel WorkManager tasks by `fileBaseName` tag, unique work names for all formats, and legacy tags before performing file cleanup and resetting database status to `WAITING`.
5. *Atomic Dependent Upload Remediation & Cascading Order (`REQ-DAT-029.5`)*:
• Relationship and Sequencing: `informOthersFailed(answer)` strictly updates the parent row `(fileBaseName, ExportType.FILE, fileFormat)` to `FINISHED_FAILED`.
• When `mExportInfo.exportType == ExportType.FILE` reaches permanent failure, `ExportWorker` SHALL immediately follow by calling `ExportStatusDatabaseManager.markDependentUploadsFailed(fileBaseName, fileFormat, errorMsg)`.
• `markDependentUploadsFailed` SHALL execute within a dedicated native SQLite transaction (`beginTransaction` / `setTransactionSuccessful`), wrapped in a comprehensive `try-catch(Throwable)` logging `fileBaseName` and `fileFormat` with full stack trace on any failure, cascading terminal failure strictly to dependent cloud upload rows (`TYPE != FILE`) for that format currently in `WAITING` status, guaranteeing atomicity and eliminating double-processing risks.
6. *Self-Healing Stuck State Recovery with Decoupled Non-Blocking Double-Checked Liveness Guard, Attempt ID Matching & Entry Sanitation (`REQ-DAT-029.6`)*:
• Entry Sanitation Invariant: `ExportManager` (`newWorkout`, `exportWorkout`, `exportWorkoutTo`) and `ExportStatusDatabaseManager` SHALL sanitize and validate `fileBaseName` upon entry; null, empty, blank, or literal "null" strings MUST be rejected before insertion into `ExportStatus.db`.
• Heartbeat & Attempt Tracking: `ExportStatusDatabaseManager` SHALL persist `last_updated` and `run_attempt_count` on every status mutation, and `ExportWorker` SHALL refresh `last_updated` periodically during active exports via a non-blocking coroutine ticker (interval: 15 seconds or 1,000 samples).
• Centralized Constants: System constants SHALL be centralized (`ExportConfig` / `ExportStatusDatabaseManager`: `MAX_RETRIES = 3`, `HEARTBEAT_INTERVAL_MS = 15_000L`, `MAX_EXPORT_DURATION_MS = 15 * 60 * 1000L`, `RECOVERY_GRACE_PERIOD_MS = 30_000L`).
• Decoupled Non-Blocking 3-Phase Recovery: Stuck state recovery SHALL execute asynchronously without blocking caller threads or holding SQLite locks across WorkManager queries: (1) candidate scan identifies rows in `PROCESSING` older than `MAX_EXPORT_DURATION_MS + RECOVERY_GRACE_PERIOD_MS`; (2) non-blocking WorkManager state query snapshots active worker states without DB locks; (3) short-lived serialized transaction re-verifies that `last_updated` and `run_attempt_count` have not changed. If a worker has progressed, is within the grace period, or a fresh heartbeat was recorded, the transition to `FINISHED_FAILED` is aborted and suppressed, eliminating split-brain races and false positives on long workouts.
• `informOthersFailed()` and `updateStatus()` SHALL be idempotent and protect against late-finishing writes if cancelled.
• `WorkoutDeletionHelper.deleteWorkout(long workoutId)` SHALL guard `fileBaseName` defensively and cancel active WorkManager tasks for `fileBaseName` when deleting a workout.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-DAT-029`, extending `REQ-DAT-028` and `REQ-DAT-018`. Targets `BaseExporter.java`, `ExportWorker.kt`, `ExportManager.java`, `ExportStatusDatabaseManager.java`, and `WorkoutDeletionHelper.java`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3995`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`).
3. *Root Reason for Existing Formulation*: Early implementation mapped all exceptions to retry without attempt caps, omitted cooperative cancellation in cursor loops, and used non-matching WorkManager tags during invalidation.
4. *Preservation of Core Invariants*: Valid exports to all formats (FIT, GPX, TCX, CSV, GC) continue functioning seamlessly; cloud uploads retain transient retry capability up to 3 attempts; 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an export encountering a deterministic error (`ParseException`, `JSONException`, `IllegalArgumentException`),
• *When* `ExportWorker.doWork()` executes,
• *Then* `result.shallRetry()` is false, status transitions to `FINISHED_FAILED`, and the worker returns `Result.failure()` without retrying.
• *Given* a transient `IOException` during cloud upload,
• *When* `ExportWorker.doWork()` executes with `runAttemptCount < 3`,
• *Then* it updates status with retry attempt count and returns `Result.retry()`; when `runAttemptCount >= 3`, it terminates with `FINISHED_FAILED` and `Result.failure()`.
• *Given* an active file export in progress,
• *When* `ExportManager.invalidateWorkoutExport(fileBaseName)` is invoked or the workout is cropped,
• *Then* the running worker detects cancellation at the start of the next sample loop iteration or immediately after blocking I/O, aborts immediately, deletes partial files, and WorkManager cancels pending upload tasks.
• *Given* an export request configured for file export plus cloud uploads (Dropbox, Google Drive, Strava),
• *When* the local file creation fails,
• *Then* `informOthersFailed` writes `FINISHED_FAILED` for the file, and `markDependentUploadsFailed` atomically transitions all dependent cloud upload rows in `ExportStatus.db` from `WAITING` to `FINISHED_FAILED`.
• *Given* an attempt to enqueue an export with corrupted, blank, or `"null"` `fileBaseName`,
• *When* `ExportManager` or `ExportStatusDatabaseManager` receives the call,
• *Then* it rejects the input at entry, preventing invalid rows in `ExportStatus.db`.
• *Given* a legacy workout deletion with invalid `fileBaseName`,
• *When* `WorkoutDeletionHelper.deleteWorkout()` is invoked,
• *Then* it executes safely without throwing `IllegalArgumentException`.

**Invariants**: Zero regression in valid export generators, definitive terminal states for all operations, retry bounds of 3 attempts, cooperative cancellation safety, and 100% clean-room test pass rate.

### REQ-MIG-045: Real SQLite In-Memory & Fixture Migration Testing Framework via Robolectric [`TST-MIG-042`]
- **Tests**: `app/build.gradle`, `app/src/test/`
- **Implementation**: Equip test suite with authentic SQLite execution via Robolectric and AndroidX Test, providing reusable migration test fixture helper and real SQLite verification suites covering WorkoutSummaries, SportType, Equipment, Routes, and TrackingViews.
- **Rationale**: The test harness, build configuration (`app/build.gradle`), and automated unit test suite SHALL equip the project with an authentic SQLite execution environment using Robolectric and AndroidX Test, enabling `SQLiteOpenHelper` implementations to execute DDL, DML, PRAGMA statements, multi-version upgrade chains, and data preservation checks against a genuine SQLite engine during JVM test execution (`./gradlew testDebugUnitTest`), eliminating reliance on mock-only `execSQL` verification (ATT-4005, extending `REQ-MIG-044` and `REQ-MIG-041`):
1. *Robolectric & AndroidX Test Dependency Integration (`REQ-MIG-045.1`)*:
• `app/build.gradle` SHALL include `org.robolectric:robolectric:4.14.1`, `androidx.test:core:1.6.1`, and `androidx.test.ext:junit:1.2.1` under `testImplementation`.
• AGP test configuration SHALL preserve existing test runners, memory heap limits (`maxHeapSize = "768m"`, `minHeapSize = "256m"`), parallel forks (`maxParallelForks`), and strict prohibition of `returnDefaultValues = true` (`docs/coding_standards.md` §5.1).
2. *Deterministic Database Migration Test Harness (`REQ-MIG-045.2`)*:
• The test infrastructure SHALL provide a reusable migration test fixture helper (`RealSqliteMigrationTestHelper`) that operates in an isolated temporary/in-memory database environment per test.
• The helper SHALL provide utilities to scaffold historical database schemas at arbitrary older versions ($V_{\text{old}} < V_{\text{current}}$), insert representative baseline dataset records, trigger `SQLiteOpenHelper.onUpgrade(db, oldVersion, newVersion)`, inspect real SQLite metadata via `PRAGMA table_info(tableName)`, and query and assert data preservation.
3. *Core Database Real SQLite Migration Verification Suites (`REQ-MIG-045.3`)*:
• The test suite SHALL provide real SQLite Robolectric migration tests covering critical database helpers:
• `WorkoutSummariesDbHelper`: incremental migration chains from v22 -> v23 (`RACE`), v23 -> v24 (`SOURCE`), and v24 -> v25 (`COUNT`), asserting column creation, column types, default values, and retention of existing workout rows.
• `SportTypeDbHelper`: real SQLite execution of v1 -> v3 and v2 -> v3 migrations, proving `SportTypes` table is NOT dropped, user-defined custom sports (`_id >= 6`) are preserved, `WALK` is migrated to `baseSportType = 'RUN'`, and column `is_placeholder` is added.
• `EquipmentDbHelper`: real SQLite execution of v1 -> v2 migration, asserting `Retired` column addition and data preservation.
• `RoutesDbHelper`: real SQLite execution of v5 -> v10 migration, asserting `route_waypoints` table creation and spatial bounds.
• `TrackingViewsDbHelper`: real SQLite execution of v13 -> v14 migration, asserting `SectionDividersTable` creation.
4. *Execution Performance & Zero Production Disruption (`REQ-MIG-045.4`)*:
• The Robolectric migration tests SHALL execute entirely within `./gradlew testDebugUnitTest` without requiring an emulator or physical device, maintaining 100% clean-room test pass rate across the full project test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-045` under Epic `ATT-2466` (*Developer Testing Tools*). Targets `app/build.gradle` (test dependencies) and `app/src/test/java/.../database/`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4005`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). Review identified that unit tests across database managers relied exclusively on MockK mocks, leaving genuine SQLite syntax, multi-step migrations, and data preservation unexercised.
3. *Root Reason for Existing Formulation*: Early engineering prioritized fast execution on the JVM without external shadow frameworks. However, this allowed serious schema regressions (`ATT-3993` dropping user sports, `ATT-4009` malformed SQL in `WorkoutSummaries` legacy upgrade) to reach production because mock tests only matched string patterns.
4. *Preservation of Core Invariants*: The ~3,300 existing MockK unit tests MUST continue to run without interference. Fast developer loop (`./gradlew testDebugUnitTest --tests ...`) and clean-room full suite pass rate MUST remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the test project build configuration,
• *When* compiling and executing tests with `@RunWith(RobolectricTestRunner::class)`,
• *Then* the test executes on the JVM with an authentic Android context and native SQLite backing without `Stub!` exceptions.
• *Given* a real SQLite database initialized with `WorkoutSummaries` Schema v22 containing sample workout rows,
• *When* `WorkoutSummariesDbHelper.onUpgrade(db, 22, 25)` executes,
• *Then* `race`, `source`, and `count` columns are present in SQLite metadata, and the sample workout rows are preserved with correct defaults.
• *Given* a real SQLite database initialized with `SportType` Schema v1 containing default sports (1..5) and custom sports (`_id = 6`),
• *When* `SportTypeDbHelper.onUpgrade(db, 1, 3)` executes,
• *Then* custom sport `_id = 6` is preserved, `WALK` has `baseSportType = 'RUN'`, and `is_placeholder` column is added with value `0`.
• *Given* a real SQLite database initialized with `Equipment` Schema v1 containing bike and shoe entries,
• *When* `EquipmentDbHelper.onUpgrade(db, 1, 2)` executes,
• *Then* the `Retired` column is added with default `0` and all existing equipment rows are preserved.
• *Given* real SQLite databases initialized with `Routes` v5 and `TrackingViews` v13,
• *When* their respective `onUpgrade` methods run to current versions,
• *Then* `route_waypoints` and `SectionDividersTable` are created, and all pre-existing records remain readable.
• *Given* the entire project test suite,
• *When* `./gradlew testDebugUnitTest` is executed,
• *Then* all tests pass with zero failures and 100% pass rate.

**Invariants**: Zero modification to production code in `app/src/main/`; memory heap boundaries preserved; clean-room full test pass rate.

### REQ-MIG-046: Legacy SQLite Upgrade DDL Hardening, Synchronous onUpgrade Excision, and Idempotent Cross-Database Backfill Architecture [`TST-MIG-043`]
- **Tests**: `WorkoutSummariesDatabaseManager.java`, `WorkoutClusterDatabaseManager.kt`, `DatabaseBackfillManager.kt`
- **Implementation**: Eliminate fatal legacy upgrade syntax crashes (DEFAULT ???), excise heavy synchronous cross-database backfills from onUpgrade to guarantee fast (< 10ms) open, and implement idempotent background backfills with DAL graceful fallback.
- **Rationale**: `WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`) and `WorkoutClusterDatabaseManager.WorkoutClusterDbHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt`) SHALL eliminate fatal DDL syntax errors (`DEFAULT ???`) and column query defects for legacy database upgrades (`oldVersion < 11`), excise heavy cross-database DML backfills (`migrateExistingWorkouts`, `migrateExtremaPositions`, `migrateSpatialBounds`, `backfillAltitudeExtrema`) from synchronous execution inside `onUpgrade` to guarantee non-blocking database open completing in $< 10\text{ ms}$, execute idempotent background backfills with atomic in-database state tracking resilient to process death, and guarantee non-crashing graceful degradation in the Data Access Layer during the migration window (ATT-4009, extending `REQ-MIG-045`):
1. *Legacy DDL Syntax & Column Guard Hardening (`REQ-MIG-046.1`)*:
• In `WorkoutSummariesDbHelper.onUpgrade` for `oldVersion < 11`, `addColumn(db, "WorkoutNamePatterns", "sportId", "text", "???")` SHALL be replaced with `addColumnIfNotExists(db, "WorkoutNamePatterns", "sportId", "text", "'???'")`, enclosing the default in single quotes per SQLite syntax standards.
• Before querying `WorkoutNamePatterns`, the helper SHALL verify that `WorkoutNamePatterns` exists in `sqlite_master` and contains column `"sport"` via `PRAGMA table_info` before executing query and update operations.
• For `WorkoutSummaries`, `sportId` and `bSport` columns SHALL be added using `addColumnIfNotExists`, and querying column `"sport"` SHALL be guarded by column existence inspection.
• In `WorkoutClusterDbHelper.onUpgrade`, all empty `catch (e: Exception) {}` blocks around `ALTER TABLE` operations (`oldVersion < 7`, `oldVersion < 10`, `oldVersion < 11`) SHALL be replaced with deterministic `addColumnIfNotExists` invocations querying `PRAGMA table_info`.
2. *Synchronous Backfill Excision from `onUpgrade` (`REQ-MIG-046.2`)*:
• `WorkoutSummariesDbHelper.onUpgrade` and `WorkoutClusterDbHelper.onUpgrade` SHALL strictly execute Data Definition Language (DDL) operations (`CREATE TABLE`, `ALTER TABLE ... ADD COLUMN`, `CREATE INDEX`).
• Heavy cross-database Data Manipulation Language (DML) routines (`migrateExistingWorkouts`, `migrateExtremaPositions`, `migrateSpatialBounds`, and `backfillAltitudeExtrema`) SHALL be excised from synchronous execution inside `onUpgrade`, ensuring database opening completes in $< 10\text{ ms}$ on any thread without blocking the UI or causing ANRs.
3. *Idempotent Background Backfill Architecture (`REQ-MIG-046.3`)*:
• The application SHALL provide an asynchronous, off-UI-thread backfill mechanism (`DatabaseBackfillManager` / `DatabaseBackfillWorker`).
• Each backfill operation SHALL be strictly idempotent and self-healing: `migrateSpatialBounds` queries and populates strictly rows `WHERE boundMinLat IS NULL AND mapPolyline IS NOT NULL AND mapPolyline != ''`; `migrateExtremaPositions` queries and populates strictly rows `WHERE latitude IS NULL AND extremaType != 'AVG'`; `migrateExistingWorkouts` queries and populates strictly rows `WHERE mapPolyline = '' OR distanceStream = ''`; `backfillAltitudeExtrema` queries and populates strictly clusters `WHERE min_alt_lat IS NULL`.
• The SQLite row state itself SHALL serve as the atomic source of truth. Any worker cancellation, interruption, or process death SHALL preserve partially migrated rows, and subsequent runs SHALL resume strictly on remaining unpopulated rows without data duplication.
4. *Data Access Layer (DAL) Fallback & Graceful Degradation (`REQ-MIG-046.4`)*:
• During the asynchronous backfill window, queries on unmigrated rows SHALL remain fully stable and non-crashing: workouts with unpopulated spatial bounds SHALL be omitted from bounding-box queries while remaining accessible in chronological lists; elevation and distance charts for workouts with empty stream columns SHALL fall back on-demand to raw points from `WorkoutSamplesDatabaseManager`; extrema queries with null coordinates SHALL gracefully fall back to sample lookup on demand.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-046`, extending `REQ-MIG-045` (`ATT-4005`). Targets `WorkoutSummariesDatabaseManager.java`, `WorkoutClusterDatabaseManager.kt`, and backfill workers.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4009`, Sprint `2026-42.10`, discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). Review identified that `oldVersion < 11` emitted `DEFAULT ???`, causing fatal crashes on legacy database upgrades, and that heavy backfills ran synchronously in `onUpgrade`.
3. *Root Reason for Existing Formulation*: Historical migrations in 2016 introduced `WorkoutNamePatterns` with unquoted defaults. Heavy polyline and extrema backfills were placed directly in `onUpgrade` when dataset sizes were small, leading to ANRs as workout histories grew.
4. *Preservation of Core Invariants*: `REQ-CON-017 / ATT-3990`: `onOpen()` in database helpers remains strictly passive with zero mutations and zero thread allocations; `DB_VERSION = 25` remains active; clean-room test suite pass rate remains 100%.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a SQLite database at version $\le 10$,
• *When* the current app opens and upgrades it to version 25,
• *Then* the upgrade completes without any SQL syntax error, column error, or crash.
• *Given* a database containing hundreds of historical workouts,
• *When* the database is opened for the first time after an update,
• *Then* `onUpgrade` completes in $< 10\text{ ms}$ executing purely DDL, and zero cross-database backfills block the calling or UI thread.
• *Given* unpopulated legacy rows in `WorkoutSummaries`, `WorkoutSummaries_ExtremaValues`, and `WorkoutClusters`,
• *When* the asynchronous `DatabaseBackfillManager` runs,
• *Then* all unpopulated records are accurately computed and populated without duplicate entries.
• *Given* a backfill operation interrupted mid-flight,
• *When* the backfill resumes,
• *Then* it queries only remaining unpopulated (`IS NULL`) rows and converges to completion with zero data loss.
• *Given* rows with unpopulated backfill columns,
• *When* read via DAO, summaries listing, or charts,
• *Then* queries return safely with on-demand fallback and zero exceptions.

**Invariants**: Pure DDL fast onUpgrade, passive onOpen, zero UI thread freezing, atomic SQLite data state, and 100% clean-room test pass rate.

### REQ-DAT-030: Google Drive Query Escaping, Encapsulated Account Cache, Atomic Download Staging & Transparent Scan Results [`TST-DAT-025`]
- **Tests**: `GoogleDriveClient.kt`, `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`, `LegacyImportEngine.kt`
- **Implementation**: Ensure Google Drive query escaping, account-isolated cache with bounded 404 self-healing, atomic download staging with durability flush, and transparent recursive listing error reporting.
- **Rationale**: The system SHALL ensure secure query construction, account-isolated folder caching with self-healing re-creation, atomic download streaming, and transparent directory scans during Google Drive cloud operations (ATT-4014, refining `REQ-DAT-022`):
1. *Centralized Query Construction & Escaping (`GoogleDriveQueryBuilder`, `REQ-DAT-030.1`)*:
• `GoogleDriveQueryBuilder` SHALL provide `escapeQueryValue(value: String): String`, escaping backslashes (`\\`) and single quotes (`\'`) in strict accordance with Google Drive API v3 query syntax.
• `GoogleDriveQueryBuilder` SHALL construct search queries for folders (`buildFolderQuery`), files (`buildFileQuery`), and child entries (`buildChildrenQuery`).
• `GoogleDriveClient` SHALL route all search query generation exclusively through `GoogleDriveQueryBuilder`, eliminating raw string query assembly.
2. *Encapsulated, Thread-Safe, Account-Scoped Folder Cache (`GoogleDriveFolderCache`, `REQ-DAT-030.2`)*:
• `GoogleDriveFolderCache` SHALL encapsulate internal map storage and concurrency synchronization under a dedicated monitor lock.
• In production, `accountProvider` SHALL bind to `TrainingApplication.getGoogleDriveAccountEmail()`, ensuring the cache key remains stable across OAuth token refreshes. In headless test harnesses where `accountProvider` is omitted, the cache key SHALL derive from a token fingerprint; on token rotation, the cache for the client instance SHALL be cleanly cleared to prevent duplicate or stale entries.
• `GoogleDriveFolderCache` SHALL provide atomic eviction methods: `evictFolder(folderId: String)` (atomically removing all entries resolving to that folder ID) and `invalidateHierarchy(accountKey: String, folderNames: List<String>)`.
• `GoogleDriveClient` SHALL expose `evictFolder(folderId: String)` and `invalidateFolderHierarchy(folderNames: List<String>)` delegating to the encapsulated cache.
3. *Bounded Self-Healing Folder Re-Creation (`REQ-DAT-030.3`)*:
• In `GoogleDriveUploader.doExport()` and `GoogleDriveBackupManager`, if a file upload fails with HTTP 404 Not Found, the system SHALL evict the stale folder ID via `client.evictFolder(folderId)`.
• The upload process SHALL invoke `ensureFolderHierarchy` to recreate the remote folder and retry the upload exactly once (`maxAttempts = 1`), preventing infinite loops if permissions are missing. If the second attempt fails, the uploader SHALL return failure immediately.
4. *Atomic Download Staging, Durability Flush & Bounded 24h Orphan Cleanup (`REQ-DAT-030.4`)*:
• In `GoogleDriveClient.downloadFileById(fileId: String, destinationFile: File)`, download streams SHALL write to a temporary staging file (`.part_<timestamp>_<uuid>`) co-located in `destinationFile.parentFile` (guaranteeing same filesystem inode mount point).
• Before closing the output stream, the system SHALL invoke `FileOutputStream.fd.sync()` to durably flush file contents and inode metadata to non-volatile storage. If `fd.sync()` fails with `SyncFailedException` or `IOException`, the staging file SHALL be discarded and the download marked as failed.
• If `contentLength > 0`, the client SHALL verify that the temporary file length matches `contentLength`; mismatches SHALL trigger immediate abort and file deletion.
• Upon successful verification, the client SHALL atomically move the temporary file to `destinationFile` using `java.nio.file.Files.move` with `StandardCopyOption.ATOMIC_MOVE` and `StandardCopyOption.REPLACE_EXISTING`.
• If `AtomicMoveNotSupportedException` or filesystem move failure occurs (e.g. non-POSIX external mount), the client SHALL fall back to `copyTo(destinationFile, overwrite = true)` followed by deleting the temporary file.
• If an exception or stream interruption occurs, the temporary file SHALL be deleted in a `finally` block, leaving `destinationFile` completely untouched.
• On download invocation, `GoogleDriveClient` SHALL lazily scan `destinationFile.parentFile` (bounded to at most 50 matching files to prevent I/O latency) and delete orphaned `.part_*` files older than 24 hours (`86_400_000L`).
5. *Transparent Recursive Directory Traversal (`RecursiveScanResult`, `REQ-DAT-030.5`)*:
• `GoogleDriveClient` SHALL provide `listFilesRecursivelyResult(folderId: String, extensions: List<String>): RecursiveScanResult`, where `RecursiveScanResult` exposes `val entries: List<DriveFileEntry>`, `val isComplete: Boolean`, and `val error: Exception? = null`.
• If an HTTP error or exception occurs on any page or subfolder, `isComplete` SHALL be false and `error` SHALL contain the failure exception, while accumulated entries remain available for diagnostic inspection.
• `listFilesRecursively` SHALL maintain backward compatibility by delegating to `listFilesRecursivelyResult`.
• `LegacyImportEngine` SHALL inspect `RecursiveScanResult.isComplete`; if false, it SHALL log a descriptive warning and update listener status without silently treating the directory as completely processed.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-DAT-030`, refining and extending `REQ-DAT-022` (*Google Drive Background Token Refresh, 401 Re-Authentication, Folder Hierarchy Resolution Resilience & Concurrency Synchronization*). Targets `GoogleDriveClient.kt`, `GoogleDriveUploader.kt`, `GoogleDriveBackupManager.kt`, and `LegacyImportEngine.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4014`, Sprint `2026-42.10`, discovered during independent whole-app architecture review of `feature/ATT-3921` (`0234bfc0`).
3. *Root Reason for Existing Formulation*: `globalFolderIdCache` and `hierarchyLock` were created in `REQ-DAT-022` (`ATT-2631`) to prevent concurrent WorkManager workers from creating duplicate folders. However, the cache key was account-agnostic and un-evictable, causing 404 errors on remote deletions or account switches. Query literals lacked escaping for quotes and backslashes, downloads were non-atomic, and directory scans swallowed errors.
4. *Preservation of Core Invariants*: Concurrency deduplication across workers under the same account is strictly preserved. In addition, queries with special characters execute reliably, cross-account leaks are eliminated, deleted folders recover self-healingly, file downloads are atomic with durability sync and 24h cleanup, and folder scans report errors transparently.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a folder or file name containing single quotes or backslashes (e.g. `"Runner's Track"`, `"Workouts\\2026"`),
• *When* `findFolderIdByName` or `findFileIdByName` executes,
• *Then* the generated query escapes quotes as `\'` and backslashes as `\\`, and the request succeeds with HTTP 200 without HTTP 400 Bad Request parse errors.
• *Given* Account A has resolved `"aTrainingTracker/Workouts"` to folder ID `"folder_a"`,
• *When* the user switches to Account B,
• *Then* `ensureFolderHierarchy` resolves a separate folder ID `"folder_b"` without returning `"folder_a"`.
• *Given* a previously cached Google Drive folder was deleted remotely,
• *When* `GoogleDriveUploader.doExport()` executes an upload and receives HTTP 404,
• *Then* the client evicts the stale folder ID, recreates the folder via `ensureFolderHierarchy`, and successfully uploads the file on the single retry.
• *Given* an active file download via `downloadFileById`,
• *When* the network connection drops or throws an `IOException` mid-stream,
• *Then* the partially written `.part_*` file is deleted, and `destinationFile` is never corrupted or left in a truncated state.
• *Given* a recursive folder scan encountering an HTTP error on page 2,
• *When* `listFilesRecursivelyResult` executes,
• *Then* `isComplete` is false, `error` contains the HTTP failure details, and `LegacyImportEngine` surfaces the incomplete status rather than masking it.

**Invariants**: Least-privilege `drive.file` scope, canonical v3 root folder creation (`parents` omitted), account-level concurrency deduplication, and 100% clean-room test pass rate.

### REQ-DAT-031: Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle [`TST-DAT-026`]
- **Tests**: `BackupManager.kt`, `DatabaseConnectionManager.kt`, `StravaUploadDbHelper.java`, `EquipmentDbHelper.java`, `ActiveDevicesDbHelper.java`
- **Implementation**: Atomic concurrent-safe database backup snapshots via VACUUM INTO on API 30+, storage pre-verification, writer-synchronized fallback on API 26-29, thread-safe singleton helper lifecycle, snapshot integrity checking, and staging cleanup.
- **Rationale**: `BackupManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt`), `DatabaseConnectionManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/DatabaseConnectionManager.kt`), `StravaUploadDbHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/db/StravaUploadDbHelper.java`), `EquipmentDbHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelper.java`), and `ActiveDevicesDbHelper` (`app/src/main/java/com/atrainingtracker/trainingtracker/database/ActiveDevicesDbHelper.java`) SHALL guarantee that backup bundle creation generates atomic, consistent database snapshots without torn page reads or omitted WAL frames under active concurrent tracking writes, pre-verifies available filesystem storage before staging, executes SQLite `VACUUM INTO` on API 30+ without blocking concurrent writers, provides a writer-synchronized fallback checkpoint and retry mechanism on API 26–29, manages all SQLite open helpers as thread-safe singletons without connection or file descriptor leakage, verifies snapshot integrity prior to packaging, and cleans up staging directories on all exit paths (ATT-4011, extending `REQ-MIG-040`, `REQ-MIG-041`, and `REQ-MIG-043`):
1. *Storage Space Pre-Verification (`REQ-DAT-031.1`)*:
• Before snapshotting databases or initiating backup bundling, `BackupManager` SHALL calculate the sum of sizes of all active `.db` and `-wal` files.
• `BackupManager` SHALL assert that available filesystem storage in `context.filesDir.usableSpace` is at least 1.5 times the total database size (`totalDbSize * 1.5`).
• If usable space is insufficient, `createBackup()` SHALL log a warning, notify `ProgressListener` of the error, and abort cleanly returning `null` without partially allocating disk space or exhausting device storage.
2. *API 30+ Primary Snapshot Pipeline via `VACUUM INTO` (`REQ-DAT-031.2`)*:
• On Android 11+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.R`), for each active database registered in `DatabaseConnectionManager`, `BackupManager` SHALL create an atomic standalone snapshot into an isolated temporary staging directory (`backups/temp_db_snapshot/`) using the SQLite statement `VACUUM INTO '<snapshot_file_path>'`.
• `VACUUM INTO` SHALL execute concurrently without holding exclusive application writer locks, guaranteeing that concurrent writer threads (`TrackerService`, `ExportWorker`) are not blocked.
• The produced snapshot file SHALL be an atomic, self-contained, defragmented, non-WAL SQLite database incorporating all committed transactions up to the snapshot instant.
3. *API 26–29 Fallback Snapshot Pipeline & Checkpoint Retry (`REQ-DAT-031.3`)*:
• On Android 8.0–10 (`Build.VERSION.SDK_INT < Build.VERSION_CODES.R`) or when storage is insufficient for `VACUUM INTO`, `BackupManager` SHALL execute a writer-synchronized checkpoint and copy pipeline: acquire `DatabaseConnectionManager.databaseLock` to prevent new write transactions from starting; execute `PRAGMA wal_checkpoint(FULL)` on all active databases (retrying up to 3 times with exponential backoff if `BUSY` is returned); copy clean `.db` files and non-empty `-wal` files into staging; and release `DatabaseConnectionManager.databaseLock`.
4. *Thread-Safe Singleton Helper Lifecycle & Zero Leakage (`REQ-DAT-031.4`)*:
• `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper` SHALL provide a thread-safe singleton accessor `getInstance(Context context)` using double-checked locking and binding to `context.getApplicationContext()`.
• All ad-hoc constructor instantiations across the codebase (`DatabaseConnectionManager.getActiveDatabases`, `LegacyImportEngine`, `WorkoutDeletionHelper`, `StravaUploader`, `TrackerService`, `StravaDataPurgeManager`) SHALL be eliminated and replaced with `getInstance(context)`.
• `DatabaseConnectionManager.getActiveDatabases` SHALL exclusively reference the singleton instances.
• Database helper singletons SHALL expose `@VisibleForTesting fun resetForTesting()` to ensure clean test isolation across unit test suites.
• Zero unmanaged or unclosed `SQLiteOpenHelper` instances SHALL be leaked during application lifecycle or backup execution.
5. *Snapshot Integrity Verification, Atomic Packaging & Cleanup (`REQ-DAT-031.5`)*:
• Before packaging, `BackupManager` SHALL verify each snapshot file in the staging directory using `PRAGMA integrity_check`, ensuring no corrupted or torn database files enter the backup archive.
• `BackupManager` SHALL archive the verified snapshot directory as the `databases/` tree inside `aTrainingTracker_backup.attbackup.tmp` alongside `shared_prefs` and `datastore`.
• The temporary backup file SHALL be atomically renamed to canonical `aTrainingTracker_backup.attbackup` using `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.
• The temporary staging directory (`temp_db_snapshot/`) SHALL be strictly purged in a `finally` block on all execution paths (success, failure, or cancellation).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-DAT-031`, extending `REQ-MIG-040` (`ATT-3982`), `REQ-MIG-041` (`ATT-3989`), and `REQ-MIG-043` (`ATT-3992`). Targets `BackupManager.kt`, `DatabaseConnectionManager.kt`, `StravaUploadDbHelper.java`, `EquipmentDbHelper.java`, and `ActiveDevicesDbHelper.java`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4011`, Sprint `2026-42.10`, discovered during independent whole-app architecture review of `feature/ATT-3921` (`0234bfc0`). `BackupManager.kt` was originally authored to zip the live `databases/` directory while excluding `-wal` files. Commit `f320b92b` (`ATT-3989`) introduced `quiesceAllDatabases`, but ignored `wal_checkpoint` return codes.
3. *Root Reason for Existing Formulation*: Direct zipping was written before WAL mode was pervasive across all databases. Filtering `-wal` files was intended to prevent copying ephemeral lock files (`-shm`), but did not account for uncheckpointed WAL frames or tearing from concurrent writers. `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper` lacked singletons because early versions only accessed them infrequently from single threads.
4. *Preservation of Core Invariants*: The `.attbackup` archive structure (`databases/`, `shared_prefs/`, `datastore/`) remains 100% backward and forward compatible. Full restore (`RestoreManager`) and incremental import (`AttachDatabaseHelper`) operate unchanged. Passive `onOpen()` behavior and 100% clean-room test pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* available disk space in `filesDir` less than 1.5x total active database size,
• *When* `BackupManager.createBackup(context)` is called,
• *Then* backup generation aborts immediately, logs a warning, returns `null`, and allocates zero staging files.
• *Given* Android 11+ (`Build.VERSION.SDK_INT >= 30`) and active concurrent write transactions in progress on `WorkoutSamples.db`,
• *When* `createBackup(context)` executes,
• *Then* databases are snapshotted via SQLite `VACUUM INTO` into temporary staging,
• *And* the snapshot passes `PRAGMA integrity_check` with `"ok"`, contains all committed records, and zero writer threads are blocked.
• *Given* Android 8.0–10 (`SDK_INT < 30`) and a database returning `BUSY` on the initial `wal_checkpoint(FULL)`,
• *When* `createBackup(context)` executes,
• *Then* it acquires `databaseLock`, retries the checkpoint with backoff, copies clean database files to staging, and releases `databaseLock`.
• *Given* multiple components accessing `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper`,
• *When* calling `getInstance(context)` repeatedly across threads,
• *Then* exactly one shared instance is returned per helper class, and `DatabaseConnectionManager.getActiveDatabases(context)` creates zero new helper instances, eliminating SQLite connection leaks.
• *Given* a completed or aborted backup run,
• *When* `createBackup()` returns,
• *Then* `temp_db_snapshot/` is completely purged from disk on all execution paths.

**Invariants**: Archive format compatibility, API 26-35+ compatibility, passive onOpen(), and 100% clean-room test pass rate.

### REQ-DAT-032: OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Policy [`TST-DAT-027`, `TST-DAT-031`]
- **Tests**: `TrainingApplication.java`, `AuthCredentialsStore.kt`, `ExportManager.java`, `BackupManager.kt`, `MigrationEngine.kt`, `BackupWorker.kt`, `BackupRestoreViewModel.kt`, `ImportBackupTabsScreen.kt`, `DropboxUploader.java`, `DropboxBackupManager.kt`, `MainActivityWithNavigation.kt`, `AndroidManifest.xml`
- **Implementation**: Isolate OAuth credentials into dedicated private preferences file, enforce Auto Backup and device-transfer exclusion rules, exclude credentials from .attbackup creation and restore, guard BackupWorker cloud checks, self-heal orphaned toggles, and harden FileProvider surfaces.
- **Rationale**: `TrainingApplication` (`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`), `AuthCredentialsStore` (`app/src/main/java/com/atrainingtracker/trainingtracker/security/AuthCredentialsStore.kt`), `BackupManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupManager.kt`), `MigrationEngine` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/MigrationEngine.kt`), `BackupWorker` (`app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt`), `MainActivityWithNavigation` (`app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`), and `AndroidManifest.xml` SHALL isolate all OAuth authentication credentials into a dedicated private preferences file (`auth_credentials_prefs.xml`), migrate legacy credentials transparently, exclude `auth_credentials_prefs.xml` from Android Auto Backup and device transfers via XML extraction rules, exclude `auth_credentials_prefs.xml` from `.attbackup` archive creation and restoration, require a clean one-time re-authentication after full restore, guard cloud provider connection checks against unauthenticated or missing tokens, self-heal orphaned cloud toggles, and eliminate broad FileProvider attack surfaces (ATT-4023, refined by ATT-4459, extending `REQ-DAT-031`, `REQ-MIG-040`, and `REQ-SEC-041`):
1. *Isolated Authentication Credentials Store & Dual-Key Legacy Migration (`REQ-DAT-032.1`)*:
• `AuthCredentialsStore` SHALL encapsulate all OAuth authentication credentials (`SP_STRAVA_TOKEN`, `SP_STRAVA_REFRESH_TOKEN`, `SP_STRAVA_TOKEN_EXPIRES_AT`, `SP_STRAVA_ATHLETE_ID`, `SP_STRAVA_OAUTH_STATE`, `SP_STRAVA_OAUTH_STATE_TIMESTAMP`, `SP_GOOGLE_DRIVE_ACCOUNT_EMAIL`, `SP_GOOGLE_DRIVE_AUTH_TOKEN`, `SP_DROPBOX_CREDENTIAL`) within a dedicated private `SharedPreferences` file: `auth_credentials_prefs.xml` (`context.getSharedPreferences("auth_credentials_prefs", Context.MODE_PRIVATE)`).
• Upon initialization, `AuthCredentialsStore.migrateLegacyCredentials(context: Context)` SHALL inspect default `SharedPreferences` for both canonical store keys and legacy keys (`LEGACY_KEY_DROPBOX_CREDENTIAL = "dropboxCredential"`, `LEGACY_KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL = "googleDriveAccountEmail"`, `LEGACY_KEY_STRAVA_OAUTH_STATE = "SP_STRAVA_OAUTH_STATE"`, `LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP = "SP_STRAVA_OAUTH_STATE_TIMESTAMP"`). If any legacy credential keys are present, it SHALL atomically map and copy them to `auth_credentials_prefs` and purge all legacy keys immediately from default `SharedPreferences`.
• `AuthCredentialsStore.readDropboxCredential(context: Context)` SHALL query canonical `KEY_DROPBOX_CREDENTIAL` and defensively fall back to legacy `"dropboxCredential"` in `auth_credentials_prefs` if the canonical key is absent.
• `TrainingApplication` credential getters, setters, and deletion methods for Strava, Google Drive, and Dropbox SHALL delegate directly to `AuthCredentialsStore`. General non-sensitive settings (`uploadToStrava`, `uploadToGoogleDrive`, `uploadToDropbox`, `googleDriveOnlyWifi`) SHALL remain in default `SharedPreferences`.
2. *Android Auto Backup & Device Transfer Rules (`REQ-DAT-032.2`)*:
• `AndroidManifest.xml` `<application>` tag SHALL declare `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, and `android:fullBackupContent="@xml/full_backup_content"`.
• `res/xml/data_extraction_rules.xml` (API 31+) SHALL exclude `auth_credentials_prefs.xml` from both `<cloud-backup>` and `<device-transfer>` using `<exclude domain="sharedpref" path="auth_credentials_prefs.xml" />`.
• `res/xml/full_backup_content.xml` (API 23–30) SHALL exclude `auth_credentials_prefs.xml` using `<exclude domain="sharedpref" path="auth_credentials_prefs.xml" />`.
3. *Exclusion of Credentials from `.attbackup` Creation (`REQ-DAT-032.3`)*:
• In `BackupManager.createBackup(context)`, `zipFolder` SHALL explicitly filter out `auth_credentials_prefs.xml` (and any related temp/lock files `auth_credentials_prefs.*`), guaranteeing that zero plaintext OAuth credentials, tokens, or athlete IDs enter `.attbackup` zip archives.
• `createBackup()` SHALL ensure all legacy and canonical credential keys have been purged from default preferences prior to zipping default preferences.
4. *Restore Sanitization & Clean Re-Authentication Invariant (`REQ-DAT-032.4`)*:
• In `MigrationEngine.kt`, when unpacking or staging a restored archive, any `auth_credentials_prefs.xml` file present in the archive SHALL be discarded.
• Restored default `SharedPreferences` and staging directories (`sanitizeStagedPreferencesDirectory`) SHALL be sanitized to purge both canonical and legacy credential keys (`ALL_CREDENTIAL_KEYS_TO_SANITIZE`).
• Following restore, `AuthCredentialsStore` SHALL be cleared, ensuring that restored devices cleanly require a one-time re-authentication with cloud services without leaking previously active tokens.
5. *FileProvider Attack Surface Elimination & Scoping (`REQ-DAT-032.5`)*:
• `GenericFileProvider.java` and `res/xml/provider_paths.xml` SHALL be permanently deleted from the codebase and manifest.
• In `res/xml/file_paths.xml`, `<cache-path name="backups" path="." />` exposing the root cache directory SHALL be removed. FileProvider paths SHALL be strictly limited to `<cache-path name="my_cache_images" path="images/" />` and `<files-path name="internal_backups" path="backups/" />`.
6. *Guarded Cloud Connection State & Preference Self-Healing (`REQ-DAT-032.6`)*:
• In `BackupWorker.kt`, the system SHALL evaluate `val dropboxConnected = TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null`, preventing unauthenticated upload attempts and avoiding false `PARTIAL_SUCCESS` statuses when credentials are missing or unmigrated.
• In `MainActivityWithNavigation.checkPreferences()`, if `TrainingApplication.uploadToDropbox()` is true but `TrainingApplication.readDropboxCredential()` returns null after migration, the preference SHALL be self-healed by setting `TrainingApplication.setUploadToDropbox(false)`.


7. *Granular Dropbox Sync Preferences, Wi-Fi Constraints & Sync Timestamp Enforcement (`REQ-DAT-032.7`)*:
• In `TrainingApplication.java`, the system SHALL expose granular Dropbox preference accessors matching Google Drive parity: `uploadWorkoutsToDropbox()` / `setUploadWorkoutsToDropbox(boolean)` (default `true`), `uploadBackupToDropbox()` / `setUploadBackupToDropbox(boolean)` (default `true`), `uploadToDropboxOnlyOnWifi()` / `setUploadToDropboxOnlyOnWifi(boolean)` (default `true`), `getDropboxLastSyncTimestamp()` / `setDropboxLastSyncTimestamp(long)`, and `getDropboxAccountEmail()` / `setDropboxAccountEmail(String)`.
• `getDropboxAccountEmail()` and `setDropboxAccountEmail()` SHALL store the athlete email in `AuthCredentialsStore.KEY_DROPBOX_ACCOUNT_EMAIL` (`auth_credentials_prefs.xml`) to prevent email/token leakage into `.attbackup` archives or Android Auto Backup, and SHALL be purged upon `deleteDropboxCredential()`.
• In `ExportManager.java`, workout exports to Dropbox SHALL be gated on `TrainingApplication.uploadToDropbox() && TrainingApplication.uploadWorkoutsToDropbox()`, and WorkManager constraints for `ExportType.DROPBOX` SHALL enforce `NetworkType.UNMETERED` when `uploadToDropboxOnlyOnWifi()` is true (or `NetworkType.CONNECTED` when false).
• In `BackupWorker.kt`, `BackupRestoreViewModel.kt`, and `ImportBackupTabsScreen.kt`, Dropbox automated and manual backups SHALL be gated on `TrainingApplication.uploadToDropbox() && TrainingApplication.uploadBackupToDropbox() && TrainingApplication.readDropboxCredential() != null`.
• In `BackupWorker.buildConstraints(context)`, the system SHALL dynamically evaluate active cloud backup providers: if any enabled cloud backup provider requires Wi-Fi only, constraints SHALL specify `NetworkType.UNMETERED`; if cloud backup is enabled without a Wi-Fi-only restriction, constraints SHALL specify `NetworkType.CONNECTED`; if no cloud backup is enabled, constraints SHALL specify `NetworkType.NOT_REQUIRED`.
• In `DropboxUploader.java` and `DropboxBackupManager.kt`, successful uploads SHALL invoke `TrainingApplication.setDropboxLastSyncTimestamp(System.currentTimeMillis())`. Account profile lookups via `DbxClientV2.users().currentAccount` SHALL handle `DbxException` and network failures defensively, returning `null` without throwing or aborting operations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-DAT-032` (ATT-4023), refined by `ATT-4459`. Targets `AndroidManifest.xml`, `TrainingApplication.java`, `AuthCredentialsStore.kt`, `BackupManager.kt`, `MigrationEngine.kt`, `BackupWorker.kt`, and `MainActivityWithNavigation.kt`.
2. *Historical Origin & Commit Trace*: Commit `203baa27bdcf` (Ticket `ATT-4023`, Sprint `2026-42.10`, author: Rainer Blind) refined in Sprint `2026-43.4` (`ATT-4459`), and extended in Sprint `2026-43.7` (`ATT-4665`) to add granular Dropbox preferences, Wi-Fi constraints, and sync timestamp auditing.
3. *Root Reason for Existing Formulation*: When `ATT-4023` introduced `AuthCredentialsStore.kt` to isolate credentials, constant key names (`"sp_dropbox_credential"`, `"sp_google_drive_account_email"`, `"stravaOAuthState"`, `"stravaOAuthStateTimestamp"`) were created without reconciling them against existing constants in `TrainingApplication.java` (`"dropboxCredential"`, `"googleDriveAccountEmail"`, `"SP_STRAVA_OAUTH_STATE"`, `"SP_STRAVA_OAUTH_STATE_TIMESTAMP"`). As a result, `migrateLegacyCredentials()` and sanitization routines missed legacy keys in default `SharedPreferences`, causing credentials to bypass migration into `auth_credentials_prefs.xml`, leak into `.attbackup` archives, and cause `BackupWorker` to fail with `PARTIAL_SUCCESS`.
4. *Preservation of Core Invariants*: Full credential isolation within `auth_credentials_prefs.xml`, Auto Backup exclusion, `.attbackup` exclusion, restore sanitization, backward compatibility without re-login for upgrading users, reliable cloud backups, and 100% clean-room test suite pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* default `SharedPreferences` populated with legacy keys (`"dropboxCredential"`, `"googleDriveAccountEmail"`, `"SP_STRAVA_OAUTH_STATE"`, `"SP_STRAVA_OAUTH_STATE_TIMESTAMP"`),
• *When* `AuthCredentialsStore.migrateLegacyCredentials(context)` executes,
• *Then* all credentials are moved to `auth_credentials_prefs` mapped to canonical keys, and stripped from default preferences.
• *Given* an athlete with `uploadToDropbox = true` but missing Dropbox credentials,
• *When* `BackupWorker.doWork()` executes,
• *Then* `dropboxConnected` evaluates to `false`, Dropbox upload is skipped, and the backup records `LOCAL_SUCCESS` (or `SUCCESS` if Google Drive succeeds) rather than failing with `PARTIAL_SUCCESS`.
• *Given* an athlete with `uploadToDropbox = true` and null credentials resuming `MainActivityWithNavigation`,
• *When* `checkPreferences()` executes,
• *Then* `uploadToDropbox` is automatically reset to `false`.
• *Given* Android Auto Backup XML rule descriptors (`data_extraction_rules.xml` and `full_backup_content.xml`),
• *When* parsed and inspected,
• *Then* `auth_credentials_prefs.xml` is excluded from cloud backup, device transfer, and legacy full backup domains.
• *Given* active tokens in `auth_credentials_prefs` and `BackupManager.createBackup(context)` is called,
• *When* the generated `.attbackup` archive is inspected,
• *Then* `shared_prefs/auth_credentials_prefs.xml` is NOT present, and no OAuth tokens exist inside the archive.
• *Given* a backup archive containing legacy tokens,
• *When* restored via `MigrationEngine`,
• *Then* `auth_credentials_prefs.xml` is ignored, legacy token keys are stripped from restored default preferences and staging files, and cloud services indicate disconnected state awaiting re-authentication.
• *Given* the project manifest and XML resources,
• *When* inspected,
• *Then* `GenericFileProvider` and `provider_paths.xml` do not exist, and `file_paths.xml` contains zero `path="."` root exposure.


• *Given* `uploadToDropbox = true` and `uploadWorkoutsToDropbox = false`,
• *When* workout export is evaluated in `ExportManager`,
• *Then* Dropbox upload work is NOT scheduled.
• *Given* `uploadToDropboxOnlyOnWifi = true`,
• *When* Dropbox export work request is built,
• *Then* constraints specify `NetworkType.UNMETERED`.
• *Given* `uploadToDropbox = true` and `uploadBackupToDropbox = false`,
• *When* `BackupWorker` or `BackupRestoreViewModel` evaluates cloud backup,
• *Then* Dropbox backup upload is skipped.
• *Given* active cloud backup where no active provider requires Wi-Fi only,
• *When* `BackupWorker.buildConstraints()` executes,
• *Then* constraints specify `NetworkType.CONNECTED`.
• *Given* a successful Dropbox workout or backup upload,
• *When* completed,
• *Then* `TrainingApplication.setDropboxLastSyncTimestamp` is updated to current time.

**Invariants**: General preference backup preservation, no deprecated EncryptedSharedPreferences, archive format compatibility, and 100% clean-room test pass rate.

### REQ-DAT-033: High-Performance Batch Workout Mapping, Non-Blocking Cluster Equipment Resolution & Consolidated Samples DB Telemetry Loading [`TST-DAT-028`]
- **Tests**: `WorkoutDataMapper.kt`, `PeriodsRepository.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersViewModel.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `WorkoutRepositoryProjectionTest.kt`
- **Implementation**: Eliminate per-workout N+1 queries, read-time writes, Main-thread Compose queries, and redundant samples table scans across workout mapping, clusters, and Aftermath.
- **Rationale**: The system SHALL eliminate synchronous per-workout N+1 database queries, read-time SQLite writes, Main-thread Compose queries, and redundant samples table scans across workout mapping, period analytics, cluster navigation, and Aftermath loading (ATT-4003, ATT-4707, Implemented in: ATT-4003, Status: Verified, amending `REQ-DAT-009` and `REQ-MAP-020`):
1. *Batch Workout Mapping & Expanded Batch Metadata (`REQ-DAT-033.1`)*:
• `PeriodsRepository.getWorkoutsForRange` SHALL extract `RawCursorSnapshot`s and resolve all workouts in a single pass via `fromSnapshot` using `BatchMetadata`, eliminating the $O(N \times 65)$ `fromCursor` query loop.
• `BatchMetadata` SHALL be expanded to pre-fetch and map `SportType` (BSportType, UI name), `EquipmentDbHelper` names, `KnownLocations` start/end names, and athlete metrics (effective FTP and weight).
• `WorkoutDataMapper.fromSnapshot` SHALL NOT execute auxiliary SQLite database queries during cursor iteration.
2. *Decoupled Self-Healing SQLite Writes (`REQ-DAT-033.2`)*:
• Batch cursor reading (`fromSnapshot` / `PeriodsRepository.getWorkoutsForRange`) SHALL evaluate authoritative apex and altitude extrema in-memory without executing synchronous SQLite writes (`updateExtremaValue`) during read loops.
• Self-healing database writes for legacy corrupted records SHALL remain confined to single-item `fromCursor` loading or background migration.
3. *Non-Blocking Cluster Equipment Resolution in Compose (`REQ-DAT-033.3`)*:
• `WorkoutClustersViewModel` SHALL precompute or cache the sport-to-linked-equipment mapping on `Dispatchers.IO` and expose it as in-memory state or lookup (`StateFlow` or cached map).
• `WorkoutClustersTabsScreen` SHALL evaluate cluster equipment filters in-memory on the Android Main thread without invoking synchronous SQLite queries (`getLinkedEquipmentSet`) during layout or recomposition passes upon GPS fix updates.
4. *Consolidated Projected Samples DB Telemetry Loading & Quoted Column Identifiers (`REQ-DAT-033.4`)*:
• `WorkoutRepository.kt`'s track extraction (`getWorkoutTrackPoints`) and zone distribution calculators (`getHeartRateZoneDistribution`, `getPowerZoneDistribution`) SHALL specify explicit column projections (`String[]` projection) rather than unprojected `SELECT *` (`null` projection).
• Every column identifier in the projection passed to SQLite `db.query(...)` in `WorkoutRepository.kt` (`getWorkoutTrackPoints`, `loadWorkoutDetailTelemetry`, `getHeartRateZoneDistribution`, `getPowerZoneDistribution`) SHALL be quoted as a SQL identifier (`"\"${col.replace("\"", "\"\"")}\""`), preventing SQLite syntax errors and invalid function invocations when querying source-qualified location columns with spaces or parentheses (e.g. `LATITUDE (Google Fused)`, `LATITUDE (GPS)`, `LATITUDE (Network)`).
• `TrackOnMapAftermathViewModel` / `WorkoutRepository` SHALL consolidate multi-pass scans of the workout's samples table (`TrackType.entries`, HR/Power zones) so that track points across track types and zone distributions are extracted without redundant full-table re-scans.
5. *Preservation of Core Invariants (`REQ-DAT-033.5`)*:
• Single-item `fromCursor` and batch `fromSnapshot` mapping parity MUST be strictly preserved (Rule 20, `WorkoutDataMappingSymmetryTest`).
• Cluster equipment filtering logic, sorting, and cluster statistics MUST yield 100% identical outputs.
• Telemetry coordinates, elevation calculations, and HR/Power zone distribution percentages MUST yield identical values.
• In SQLite, `SELECT "LATITUDE (Google Fused)"` produces a cursor whose column name matches the unquoted identifier `LATITUDE (Google Fused)`, strictly preserving `cursor.getColumnIndex(...)` lookups.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-DAT-009` (*Authoritative Altitude Extrema Reconciliation & Self-Healing*) and `REQ-MAP-020` (*Authoritative Workout Max Line Distance (Apex) Calculation & Self-Anchored Displacement Integrity*). Targets `WorkoutDataMapper.kt`, `PeriodsRepository.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersViewModel.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, and `WorkoutRepositoryProjectionTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-508` (`b4f69aef` / `f3122721`) for altitude reconciliation; `ATT-528` (`d4a22c01`) for apex distance/coordinate self-healing; `ATT-359` / `ATT-2309` for batch metadata; `ATT-1402` for cluster equipment filtering. Discovered during code review of `feature/ATT-3921` (`0234bfc0`), tracked in `ATT-4003` (commit `6073cf43`) and hardened in `ATT-4707`.
3. *Root Reason for Existing Formulation*: Lazy self-healing was placed directly in `WorkoutDataMapper` to repair corrupted records on-the-fly when read, and `WorkoutClustersTabsScreen` directly called `viewModel.getLinkedEquipmentSet` in filter lambdas for simplicity. Explicit projections were introduced in `ATT-4003` to eliminate multi-pass table scans, but unquoted column names caused SQLite syntax errors on source-qualified location columns.
4. *Preservation of Core Invariants*: Extrema reconciliation, apex coordinates, single-item/batch mapping symmetry (Rule 20), cluster filtering criteria, unquoted cursor column resolution for `cursor.getColumnIndex`, and zone distribution calculations remain 100% invariant.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a time range containing 300 workouts (e.g. Year view),
• *When* `PeriodsRepository.getWorkoutsForRange` is called,
• *Then* all workouts are loaded via `fromSnapshot` using `BatchMetadata` in a single query pass with zero per-workout N+1 DB queries and zero read-time SQLite writes.
• *Given* the `WorkoutClustersTabsScreen` is displayed,
• *When* new GPS location fixes arrive or the screen recomposes,
• *Then* cluster equipment filtering evaluates in-memory without executing synchronous SQLite queries on the Android Main thread.
• *Given* an activity with recorded samples opened in Aftermath,
• *When* track coordinates and HR/Power zone distributions are loaded,
• *Then* `WorkoutRepository` queries the samples database using explicit column projections without redundant full-table table scans.
• *Given* a workout sample table containing source-qualified location columns (`LATITUDE (Google Fused)`, `LATITUDE (GPS)`, `LATITUDE (Network)`, and matching `LONGITUDE` columns),
• *When* `WorkoutRepository.loadWorkoutDetailTelemetry` or `getWorkoutTrackPoints` queries the samples database,
• *Then* the query executes with quoted column identifiers without throwing `SQLiteException` and returns populated `PathPoint` lists.

**Invariants**: Mapping symmetry (Rule 20), cluster filtering accuracy, telemetry parity, SQLite schema immutability, clean-room 100% test pass rate.

### REQ-DAT-034: Database Performance Optimization, Missing Index Migrations, Routes N+1 Query Elimination & Synchronized Transactional Clustering Engine [`TST-DAT-029`]
- **Tests**: `RoutesDatabaseManager.kt`, `WorkoutSummariesDatabaseManager.java`, `LapsDatabaseManager.java`, `WorkoutClusterEngine.kt`
- **Implementation**: Eliminate full table scans, N+1 route query loops, unbatched SQLite disk syncs, and race conditions in cluster engine.
- **Rationale**: The system SHALL optimize database performance and thread safety across routes, workout summaries, laps, and route clustering by adding missing database indexes across both fresh creation and upgrades, eliminating N+1 route query amplification, batching historical cluster migrations in SQLite transactions with in-memory cluster candidate caching, and serializing cluster mutations via concurrency synchronization (ATT-4008, Implemented in: ATT-4008, Status: Verified):
1. *Database Schema Version Upgrades & Index Migrations (`REQ-DAT-034.1`)*:
• `RoutesDbHelper` SHALL increment `DB_VERSION` from `10` to `11`. In both `onCreate` and `onUpgrade` (`oldVersion < 11`), index `idx_route_points_route_id` on `route_points(route_id_fk)` SHALL be created.
• `WorkoutSummariesDbHelper` SHALL increment `DB_VERSION` from `25` to `26`. In both `onCreate` and `onUpgrade` (`oldVersion < 26`), composite index `idx_extrema_workout_sensor_extrema` on `ExtremumValues(workoutId, sensorType, extremaType)`, index `idx_summaries_cluster_id` on `WorkoutSummaries(clusterId)`, and case-insensitive index `idx_summaries_file_base_name_nocase` on `WorkoutSummaries(fileBaseName COLLATE NOCASE)` SHALL be created.
• `LapsDbHelper` SHALL increment `DB_VERSION` from `2` to `3`. In both `onCreate` and `onUpgrade` (`oldVersion < 3`), index `idx_laps_workout_id` on `Laps(workoutID)` SHALL be created.
2. *Routes N+1 Query Elimination & Batch Loading (`REQ-DAT-034.2`)*:
• `RoutesDatabaseManager` SHALL provide `getAllRouteSummaries(): List<RouteSummary>` which queries `TABLE_ROUTES` and maps rows directly to `RouteSummary` without issuing queries against `route_points` or `route_waypoints`.
• `RoutesDatabaseManager.getAllRoutes(): List<RouteWithPath>` SHALL fetch route summaries, then load path points and waypoints using batched/grouped queries rather than issuing $1 + 2N$ separate queries per route row.
3. *Transactional Batching & In-Memory Cluster Caching (`REQ-DAT-034.3`)*:
• `WorkoutClusterEngine.migrateHistory` SHALL execute phase 1 (route learning) and phase 2 (workout migration) within explicit SQLite transactions (`beginTransaction() / setTransactionSuccessful() / endTransaction()`) on both `RoutesDatabaseManager` and `WorkoutSummariesDatabaseManager`.
• `migrateHistory` SHALL cache existing clusters in memory at the start of migration and update the local in-memory cluster set upon discovering or modifying clusters, eliminating per-workout $O(N)$ re-queries via `getAllClusters()`.
4. *Thread-Safe Concurrency Serialization (`REQ-DAT-034.4`)*:
• `WorkoutClusterEngine` cluster mutation pathways (`migrateHistory`, `learnFromWorkout`, `learnFromRoute`, `assignClusterToWorkout`) SHALL be serialized via internal synchronization lock (`@Synchronized` or mutex), preventing concurrent execution and race hazards between startup background migration, file imports, and tracker finalization.
5. *Preservation of Core Invariants (`REQ-DAT-034.5`)*:
• Cluster matching mathematics, spatial centroid formulas, and sequential naming (`REQ-SET-064`, `REQ-SET-065`, `REQ-SET-066`, `REQ-SET-067`) MUST remain 100% mathematically and functionally invariant.
• Full backward compatibility across existing SQLite databases across all supported upgrade paths MUST be strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* existing databases at version 10 (`Routes.db`), 25 (`WorkoutSummaries.db`), and 2 (`Laps.db`),
• *When* databases are upgraded to versions 11, 26, and 3,
• *Then* `PRAGMA index_list` confirms `idx_route_points_route_id`, `idx_extrema_workout_sensor_extrema`, `idx_summaries_cluster_id`, `idx_summaries_file_base_name_nocase`, and `idx_laps_workout_id` are created and EXPLAIN QUERY PLAN demonstrates indexed lookups.
• *Given* 50 stored routes,
• *When* `getAllRouteSummaries()` is invoked,
• *Then* exactly 1 query is executed against `TABLE_ROUTES` with zero point/waypoint queries.
• *Given* `WorkoutClusterEngine.migrateHistory()` executing on 500 workouts,
• *When* executed,
• *Then* operations run within SQLite transactions with zero per-row `getAllClusters()` DB scans and thread-safe serial execution.

**Invariants**: Cluster scoring formulas, centroid coordinates, and naming sequences remain strictly unchanged; zero regression in existing database migrations or clean-room test suite.

### REQ-DAT-035: Nullable Altitude Telemetry, Sea-Level Fidelity (0.0 m), Crop Stream Gap Resilience & Extrema Reconciliation Integrity []
- **Tests**: power != null
- **Rationale**: The system SHALL support nullable altitude telemetry (`PathPoint.altitude: Double? = null`) to explicitly decouple missing altitude measurements from valid sea-level altitude ($0.0\text{ m}$), eliminate artificial $0.0\text{ m}$ canyon plunge artifacts during workout cropping and downsampling, protect authoritative extrema reconciliation in `WorkoutDataMapper` from resetting valid positive minimum altitudes to $0.0\text{ m}$ upon stream gaps, and render elevation profiles and aftermath statistics accurately for sea-level and trackless workouts (ATT-4017, Implemented in: ATT-4017, Status: Verified, amending `REQ-DAT-009` and `REQ-UI-332`, refining `REQ-UI-126`):
1. *Nullable Altitude DTO & Track Point Extraction (`MapModels.kt`, `WorkoutRepository.kt`, `REQ-DAT-035.1`)*:
• In `MapModels.kt`, `PathPoint.altitude` SHALL be declared as nullable (`val altitude: Double? = null`), permitting call sites to omit altitude or pass `null` when elevation data is unrecorded.
• In `WorkoutRepository.kt` (`getWorkoutTrackPoints` and `getWorkoutTelemetryPoints`), cursor altitude reading SHALL evaluate: `val alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else null`, mapping null database values strictly to `null` rather than `0.0`.
• In `WorkoutRepository.getWorkoutTelemetryPoints`, the sample retention filter SHALL evaluate: `if (hr != null

### REQ-MIG-047: Canonical Destination Resolution, Symlinked Parent Directory Boundary Tolerance & Symlink Rejection Invariant in SecureZipExtractor [`TST-MIG-044`]
- **Tests**: `SecureZipExtractor.kt`, `BackupImportSecurityTest.kt`
- **Implementation**: Canonicalize destination path in SecureZipExtractor to tolerate symlinked parent directories (/data/user/0 -> /data/data) while strictly preserving internal symlink rejection, top-level whitelist, and Zip-Slip defense.
- **Rationale**: The system SHALL resolve and canonicalize the destination directory once (`realDestPath = destPath.toRealPath()`) before resolving archive entry paths, resolve all archive entries directly against `realDestPath` (`entryPath = realDestPath.resolve(entryName).normalize()`), terminate ancestor symlink traversal loops at `realDestPath` (`while (ancestor != null && ancestor != realDestPath)`), and validate real paths (`toRealPath(LinkOption.NOFOLLOW_LINKS)`) relative to `realDestPath`, ensuring that pre-existing OS-level parent symlinks (e.g., Android `/data/user/0 -> /data/data`) do not trigger spurious `SecurityException` failures during full restore or incremental backup import, while strictly preserving rejection of any symbolic links inside the archive or within the destination hierarchy (ATT-4592, parent epic ATT-281, status: Verified, amending `REQ-MIG-040`):
1. *Canonical Destination Initialization & Entry Resolution (`SecureZipExtractor.kt`, `REQ-MIG-047.1`)*:
• In `SecureZipExtractor.extractBackupArchive(backupFile, destDir, allowedTopLevelDirs)`, after `Files.createDirectories(destPath)`, the system SHALL resolve canonical destination path `val realDestPath = destPath.toRealPath()` once to collapse any pre-existing symlinks in the parent hierarchy (such as Android `/data/user/0 -> /data/data`).
• Every archive entry SHALL be resolved directly against `realDestPath`: `val entryPath = realDestPath.resolve(entryName).normalize()`, guaranteeing entry paths share the canonical prefix of `realDestPath`.
2. *Parent Boundary Tolerance & Ancestor Loop Termination (`SecureZipExtractor.kt`, `REQ-MIG-047.2`)*:
• Pre-I/O normalized prefix assertion SHALL verify `entryPath.startsWith(realDestPath)`.
• The ancestor symlink walk SHALL traverse `var ancestor: Path? = entryPath` and terminate at `ancestor == realDestPath` (`while (ancestor != null && ancestor != realDestPath)`), verifying that no archive component or newly created subdirectory within `realDestPath` is a symbolic link (`Files.isSymbolicLink(ancestor)`), while ignoring legitimate OS-level parent symlinks located strictly above `realDestPath`.
• Relative path relativization for top-level whitelist evaluation SHALL compute `realDestPath.relativize(entryPath)`.
3. *NOFOLLOW_LINKS Real-Path Boundary Assertions (`SecureZipExtractor.kt`, `REQ-MIG-047.3`)*:
• For directory entries, `entryPath.toRealPath(LinkOption.NOFOLLOW_LINKS)` SHALL verify `realDirPath.startsWith(realDestPath)`.
• For file entries, `parent.toRealPath(LinkOption.NOFOLLOW_LINKS)` and `entryPath.toRealPath(LinkOption.NOFOLLOW_LINKS)` SHALL verify `realParent.startsWith(realDestPath)` and `realFilePath.startsWith(realDestPath)`. Because `entryPath` is rooted at `realDestPath`, `toRealPath(LinkOption.NOFOLLOW_LINKS)` will not fail or diverge due to parent directory symlinks above `realDestPath`.
4. *Preservation of System Invariants (`REQ-MIG-047.4`)*:
• Full restore (`MigrationEngine.kt`, `REQ-MIG-004`, `REQ-MIG-041`) and incremental import (`ImportEngine.kt`, `REQ-MIG-005`, `REQ-MIG-043`) API contracts and rollback mechanisms MUST remain invariant.
• Strict rejection of any symbolic link entries within backup archives (`REQ-MIG-040.2`) MUST remain 100% enforced.
• Top-level directory whitelist (`databases/`, `shared_prefs/`, `datastore/`, `REQ-MIG-040.3`) and Zip-Bomb streaming counting limits (10,000 entries, 512 MB single-file, 1 GB total uncompressed, `REQ-MIG-040.4`) MUST remain 100% enforced.
• Zero SQLite schema migrations.
• Clean-room full test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MIG-040` (*Zip-Slip Path Traversal Protection, Symlink Rejection, Top-Level Whitelist & Bounded Stream Extraction in Backup Operations*). Targets `SecureZipExtractor.kt`, `MigrationEngine.kt`, `ImportEngine.kt`, and `BackupImportSecurityTest.kt`.
2. *Historical Origin & Commit Trace*: Commit `b87593805db6` (`ATT-3982`, Sprint `2026-42.10`, author: Rainer Blind).
3. *Root Reason for Existing Formulation*: `ATT-3982` hardened archive unpacking against CWE-22 and symlink TOCTOU attacks by comparing `toRealPath(LinkOption.NOFOLLOW_LINKS)` against `realDestPath`. However, `entryPath` was resolved against non-canonical `destPath` (`/data/user/0/...`), causing `toRealPath(NOFOLLOW_LINKS)` to retain `/data/user/0` while `realDestPath` was `/data/data`, resulting in false-positive `SecurityException` failures on standard Android storage layouts.
4. *Preservation of Core Invariants*: Resolving against `realDestPath` and terminating ancestor walks at `realDestPath` strictly preserves Zip-Slip containment, internal symlink prohibition, top-level directory whitelisting, and streaming caps while enabling correct execution on Android storage architectures.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an extraction target residing in a directory whose parent hierarchy contains symbolic links (e.g. `/data/user/0 -> /data/data` or `/tmp/symlink_parent/target`),
• *When* `SecureZipExtractor.extractBackupArchive` unpacks a valid backup archive,
• *Then* extraction succeeds cleanly without throwing `SecurityException: Parent real path escapes destination boundary`.
• *Given* an archive containing a symbolic link entry or an entry attempting path traversal (`../../evil.db`),
• *When* `SecureZipExtractor.extractBackupArchive` executes on a target with a symlinked parent,
• *Then* `SecurityException` is thrown and the destination staging directory is purged.
• *Given* an archive with entries outside whitelisted directories (`databases/`, `shared_prefs/`, `datastore/`),
• *When* `SecureZipExtractor.extractBackupArchive` executes,
• *Then* `SecurityException` is thrown and extraction aborts.

**Invariants**: Zero SQLite schema migrations, 100% clean-room test pass rate, and strict rejection of internal symbolic links.

### REQ-DAT-036: SQLite Schema Evolution, `onCreate`/`onUpgrade` Parity & Automated Migration Verification [`TST-DAT-032`]
- **Tests**: `docs/coding_standards.md`
- **Implementation**: Mandate symmetrical SQLite schema evolution between onCreate and onUpgrade, safe DEFAULT values for added NOT NULL columns, and automated migration contract tests.
- **Rationale**: The system and engineering coding standards SHALL mandate symmetrical SQLite database schema evolution, non-null column safety, and automated schema migration test coverage across all SQLite database helpers (ATT-3534, Parent Epic ATT-232, refining `REQ-DAT-008` and `REQ-PRO-026`):
1. *Symmetrical Evolution (`docs/coding_standards.md` §2.3)*:
• Whenever a database `DB_VERSION` / `C_DB_VERSION` is incremented, the helper's `onCreate()` (fresh install schema) and `onUpgrade()` (incremental migration path) SHALL be updated symmetrically so fresh installs and upgraded databases yield identical schema definitions.
2. *Non-Null Safe Default Constraint*: Any new `NOT NULL` column added to an existing table via `ALTER TABLE ... ADD COLUMN` SHALL define an explicit safe `DEFAULT` value to prevent migration failures on existing populated databases.
3. *Automated Migration Unit Testing*: Every database schema version increment SHALL be protected by an automated migration contract unit test verifying schema equivalence between a fresh `onCreate()` database and a database migrated through `onUpgrade()`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-DAT-036` under Epic `ATT-232` (*Process*), extending `REQ-DAT-008` (*Atomic Database Upgrades*) and `docs/coding_standards.md` §2.
2. *Historical Origin & Commit Trace*: `REQ-DAT-008` in `ATT-103` (commit `28678082`), `docs/coding_standards.md` §2 in `ATT-3323` (commit `6925d864`).
3. *Root Reason for Existing Formulation*: Prior standards enforced framework transaction wrapping (`REQ-DAT-008`) and `DatabaseSchemaRegistry` version matching (Rule 62), but lacked explicit `onCreate`/`onUpgrade` schema parity, safe defaults, and automated migration test verification.
4. *Preservation of Core Invariants*: Android framework transaction wrapping, single source of truth for version constants, and DTO mapping symmetry remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `docs/coding_standards.md` Section 2,
• *When* inspected,
• *Then* Section 2.3 defines SQLite migration parity, safe default constraints on added `NOT NULL` columns, and automated migration test requirements.
• *Given* any future SQLite schema migration,
• *When* updating a database helper,
• *Then* `onCreate()` and `onUpgrade()` are updated symmetrically with safe defaults and verified by an automated migration contract test.

**Invariants**: DTO mapping symmetry (Rule 20) and DatabaseSchemaRegistry version consistency (Rule 62) preserved.
