# Living Test Specification: Persistence, Migration & Cloud Export

**Domain**: `data_migration_export`  
**Prefixes**: `DAT`, `EXP`, `EXT`, `MIG`  
**Scope**: Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export.  
**Total Tests**: 106

---

### TST-EXP-004: **TCX Schema Compliance Audit** [Exported TCX file strictly uses integer formatting for cadence and power tags.]
- **Requirement(s)**: `REQ-EXP-001`
- **Component**: **TCX Schema Compliance Audit**
- **Jira**: ``ATT-517``
- **Test Description**: 1. Perform a TCX export of a workout containing Cadence and Power data. 2. Inspect the raw XML content. 3. Verify that `<Cadence>`, `<Watts>`, and `<RunCadence>` tags contain ONLY integer values (no decimal points).

### TST-EXP-005: **Smart Asymmetric Strava Naming & Pre-Upload Discovery Test** [All test cases pass; existing Strava activities bypass TCX file upload, Strava titles are never overwritten, local app names are preserved when Strava title is default, custom Strava titles enrich local app database, and fresh uploads auto-name or serialize custom titles properly.]
- **Requirement(s)**: `REQ-EXP-008`
- **Component**: **Smart Asymmetric Strava Naming & Pre-Upload Discovery Test**
- **Jira**: ``ATT-902`, `ATT-1105``
- **Test Description**:
  1. Unit test pre-upload duplicate discovery (`findExistingStravaActivityForWorkout`): verify when Strava API returns an activity matching the workout's start time, TCX file upload (`POST /api/v3/uploads`) is bypassed, avoiding duplicate activity creation on Strava, and `doUpdate(isDuplicate = true)` is invoked.
  2. Unit test pre-upload duplicate discovery when no matching activity exists: verify `findExistingStravaActivityForWorkout` returns null and normal TCX upload proceeds.
  3. Unit test duplicate upload handling (`isDuplicate = true`) where Strava title is a default time-of-day name (e.g. `"Radfahrt am Morgen"`, `"Lauf am Abend ⛅"`, `"Morning Ride"`): verify Strava update request omits `name` (preserving Strava title) and local SQLite `WorkoutSummaries.WORKOUT_NAME` preserves local app workout name (not overwritten by default Strava title).
  4. Unit test duplicate upload handling where Strava title is custom (e.g. `"Sunday Club Ride"`, `"Schwarzwald Tour"`): verify Strava update request omits `name` (preserving Strava title) and local SQLite `WorkoutSummaries.WORKOUT_NAME` is enriched with the custom Strava title.
  5. Unit test `isDefaultStravaName()` regex evaluator across German and English time-of-day templates, with and without weather emojis (`⛅`, `🌧️`, `☀️`), and assert custom titles return `false`.
  6. Unit test duplicate upload detection resilience: verify `checkAndUpdateDuplicate` identifies duplicate activity IDs when returned in `error` or `status` fields across varied phrasings (`"duplicate of activity <id>"`, `"duplicate of <id>"`).
  7. Unit test fresh upload (`isDuplicate = false`) where workout name equals `fileBaseName`: verify Strava update request omits `name` parameter.
  8. Unit test fresh upload where workout name is custom: verify Strava update request contains `name`.
  9. Verify other metadata (`sportName`, `gearId`, `description`, `trainer`, `commute`) continue to be updated in both paths.
  10. Unit test reactive in-memory cache update in `WorkoutRepository`: verify `reloadExportStatusesFor` refreshes `workoutName` in memory when updated in SQLite.

### TST-EXP-006: **Strava Duplicate Reconciliation Equipment, Retired Gear & Detailed Sport Type Enrichment Test** [All test cases pass, confirming accurate equipment and sport type enrichment from Strava, retired gear lazy fetch and storage with original name, preservation of Strava cloud metadata, and in-memory cache reactivity.]
- **Requirement(s)**: `REQ-EXP-009`
- **Component**: **Strava Duplicate Reconciliation Equipment, Retired Gear & Detailed Sport Type Enrichment Test**
- **Jira**: ``ATT-1114``
- **Test Description**:
  1. Unit test `EquipmentDbHelper.getIdFromStravaId`: verify returns matching local equipment `_ID` when present, or `-1` when absent.
  2. Unit test `EquipmentDbHelper` database upgrade to version 2: verify `Retired` column is safely added via `ALTER TABLE` without dropping existing data and defaults to 0.
  3. Unit test `SportTypeDatabaseManager.getSportTypeIdFromStravaName`: verify Strava sport type names (e.g. `"MountainBikeRide"`) map to the correct local `sport_id` (e.g. MTB).
  4. Unit test `StravaUploader.doUpdate(isDuplicate = true)` with known local equipment: verify update payload sent to Strava omits `sport_type`/`type` and `gear_id` (preserving Strava cloud metadata), while local SQLite `WorkoutSummaries.EQUIPMENT_ID` is updated to the matching equipment ID.
  5. Unit test `StravaUploader.doUpdate(isDuplicate = true)` with missing/retired gear: mock Strava `GET /api/v3/gear/{id}` returning `DetailedGear` with `retired = true`; verify new equipment record is created in `Equipment.db` using original Strava name with `Retired = 1`, and local `WorkoutSummaries.EQUIPMENT_ID` is updated.
  6. Unit test `StravaUploader.doUpdate(isDuplicate = true)` with detailed sport type: verify local `WorkoutSummaries.SPORT_ID` is updated to MTB and Strava cloud sport type is not overwritten.
  7. Unit test `WorkoutRepository.reloadExportStatusesFor`: verify `sportId`, `sportName`, `bSportType`, and `equipmentId` are reloaded from cursor into the in-memory cache.
  8. Verify fresh upload (`isDuplicate = false`) continues to send local sport type and equipment to Strava.

### TST-EXP-007: **Strava Segment PB Feedback Ingestion, Database Update & UI Celebration Test** [Segment PB is automatically updated in SQLite and in-memory flow upon Strava upload feedback; new PRs are celebrated with a dedicated banner in Workout Summary; all invariants remain preserved with 0 test failures.]
- **Requirement(s)**: `REQ-EXP-010`
- **Component**: **Strava Segment PB Feedback Ingestion, Database Update & UI Celebration Test**
- **Jira**: ``ATT-912``
- **Test Description**:
  1. Unit Test `SegmentsDatabaseManager.updateSegmentPrTime`: verify returns `true` and updates `pr_time` when `newPrTime < currentPrTime` or `currentPrTime <= 0`; verify returns `false` and leaves row untouched when `newPrTime >= currentPrTime`; verify returns `false` when segment does not exist in `StarredSegmentsTable`.
  2. Unit Test `SegmentsRepository.updateSegmentPr`: verify `allSegmentsWithPath` StateFlow updates the matching segment's `prTime_raw` and formatted `prTime` string reactively.
  3. Unit Test `StravaUploader.doUpdate` with PR feedback: mock `getStravaActivity` returning JSON with segment effort having `pr_rank = 1`; verify `SegmentsDatabaseManager.updateSegmentPrTime` and `SegmentsRepository.updateSegmentPr` are called with correct segment ID and elapsed time.
  4. Unit Test `StravaUploader.doUpdate` without PR feedback (`pr_rank = 2`, `pr_rank = null`): verify neither database nor repository PR methods are invoked.
  5. Unit Test `StravaActivitySection` celebration banner: verify celebration banner renders at the top of the section when $\ge 1$ segment efforts have `prRank == 1` or `komRank == 1`; verify banner is omitted when zero rank-1 efforts are present.
  6. Unit Test Localization Parity: verify celebration banner strings exist and pass translation tests across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  7. Invariants: verify `REQ-EXP-008` (smart naming), `REQ-EXP-009` (duplicate reconciliation), `REQ-UI-131` (segment accordion), and `REQ-UI-144` (best efforts) continue to pass without regression.
  8. Clean-Room Regression: execute `./gradlew testDebugUnitTest`.

### TST-EXP-008: **Strava Segments Mandatory Daily Automated Synchronization Test** [Mandatory daily segments synchronization reliably schedules with WorkManager with fixed 1-day interval and updates segments in the background; UI omits unnecessary interval controls; all tests pass.]
- **Requirement(s)**: `REQ-EXP-011`
- **Component**: **Strava Segments Mandatory Daily Automated Synchronization Test**
- **Jira**: ``ATT-913`, `ATT-1178``
- **Test Description**:
  1. Unit Test `StravaSegmentsSyncWorker.schedule`: verify when Strava is connected (`getStravaAccessToken() != null`), `enqueueUniquePeriodicWork` is called with fixed 1-day periodic request (`1, TimeUnit.DAYS`), `NetworkType.CONNECTED`, and `requiresBatteryNotLow(true)`; verify when Strava is disconnected, `cancelUniqueWork` is called on WorkManager.
  2. Unit Test `StravaSegmentsSyncWorker.doWork`: verify when Strava is disconnected, worker returns `Result.success()` without network calls; verify when connected, worker invokes `SegmentsRepository.syncStarredSegments(BSportType.UNKNOWN)` and updates `SP_LAST_UPDATE_TIME_OF_STRAVA_SEGMENTS` with formatted timestamp upon success; verify worker returns `Result.retry()` on network/API exception.
  3. Unit Test `StravaSettingsDialog` UI state: verify automated segments sync toggle and interval dropdown are omitted; verify manual update card displays formatted timestamp and triggers sync.
  4. Invariants: verify `REQ-EXP-008`, `REQ-EXP-009`, `REQ-EXP-010`, `REQ-LIV-001` - `REQ-LIV-004` remain preserved with 0 regressions.

### TST-EXP-009: **Strava Routes Mandatory Daily Automated Synchronization Test** [Mandatory daily routes synchronization reliably schedules with WorkManager with fixed 1-day interval and updates routes in the background; UI omits unnecessary interval controls; all tests pass.]
- **Requirement(s)**: `REQ-EXP-012`
- **Component**: **Strava Routes Mandatory Daily Automated Synchronization Test**
- **Jira**: ``ATT-914`, `ATT-1178``
- **Test Description**:
  1. Unit Test `StravaRoutesSyncWorker.schedule`: verify when Strava is connected (`getStravaAccessToken() != null`), `enqueueUniquePeriodicWork` is called with fixed 1-day periodic request (`1, TimeUnit.DAYS`), `NetworkType.CONNECTED`, and `requiresBatteryNotLow(true)`; verify when Strava is disconnected, `cancelUniqueWork` is called on WorkManager.
  2. Unit Test `StravaRoutesSyncWorker.doWork`: verify when Strava is disconnected, worker returns `Result.success()` without network calls; verify when connected, worker invokes `RoutesRepository.syncRoutesFromStrava()` and returns `Result.success()` when sync succeeds; verify worker returns `Result.retry()` on network/API exception or when sync returns false.
  3. Unit Test `StravaSettingsDialog` UI state: verify automated routes sync toggle and interval dropdown are omitted; verify manual update card displays formatted timestamp and triggers sync.
  4. Invariants: verify `REQ-EXP-008` (smart naming), `REQ-EXP-009` (duplicate reconciliation), `REQ-EXP-010` (segment PB), `REQ-EXP-011` (segment periodic sync), and `REQ-ROU-001` (route clustering) remain preserved with 0 regressions.

### TST-EXP-010: **Minimized Strava Activity Feedback Persistence & Backward-Compatible Rendering Verification** [Data minimization achieves 90%+ storage reduction; transient social fields are completely eliminated; offline celebration and segment rendering retain 100% parity; CQS, DB_VERSION 6 migration, and disconnect wipe invariants are preserved.]
- **Requirement(s)**: `REQ-EXP-013`
- **Component**: **Minimized Strava Activity Feedback Persistence & Backward-Compatible Rendering Verification**
- **Jira**: ``ATT-1190` / `ATT-1214`, `ATT-1215``
- **Test Description**:
  1. *Unit Test `StravaActivityParser.minimize` Data Stripping*:
  • Provide a realistic raw Strava activity JSON containing athlete profile, kudos, comments, external map polyline URLs, photos, segment efforts, and best efforts.
  • Verify the minimized JSON payload includes `"v": 2` and strictly preserves `id`, `segment_efforts`, and `best_efforts`.
  • Verify all transient social fields (`kudos_count`, `comment_count`, `athlete`, `photos`, `map`) are 100% stripped.
  2. *Unit Test Null & Default Value Omission*:
  • Provide segment efforts and best efforts with null PR/KOM ranks and zero distances.
  • Verify that null ranks and non-positive distances are omitted from the serialized JSON rather than serialized as explicit nulls.
  3. *Unit Test Parser Backward Compatibility & UI Parity*:
  • Parse both legacy full JSON (v1) and minimized JSON (v2) through `StravaActivityParser.parse()`.
  • Verify returned `StravaActivity` models are identical in segment effort counts, PR ranks, starred flags, and best efforts.
  • Verify `SegmentPrCelebrationBanner` renders identical celebration state.
  4. *Unit Test Defensive Exception Handling & Truncation Resilience*:
  • Feed truncated, malformed, and empty JSON strings to `StravaActivityParser.parse()`; verify it returns `null` safely without throwing runtime exceptions.
  5. *Unit Test Strict CQS Invariance*:
  • Verify `StravaUploadDbHelper.getStravaActivityData()` executes zero SQLite write operations or row updates.
  6. *Unit Test SQLite Migration to DB_VERSION 6*:
  • Initialize `StravaUploadDbHelper` at version 5 with legacy uncompacted JSON rows and malformed JSON rows.
  • Trigger `onUpgrade` to version 6; verify legacy rows are migrated in-place to `"v": 2` schema, malformed rows safely become null/tombstone without halting migration, and unhandled disk errors roll back cleanly.
  7. *Unit Test Disconnect Data Purge & WAL Checkpoint*:
  • Verify `StravaDataPurgeManager.purgeAllStravaData()` clears `StravaUploads` table completely and triggers WAL checkpoint.
  8. *Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.

### TST-EXP-011: **Reactive Strava Segment Synchronization Progress & Transient Decoupling Verification** [`SegmentsRepository.isSyncing` reactively reflects sync state; SharedPreferences is never polluted with transient text; UI displays localized "now" during sync; clicks are debounced; 9-language parity verified; 100% test pass rate.]
- **Requirement(s)**: `REQ-EXP-014`
- **Component**: **Reactive Strava Segment Synchronization Progress & Transient Decoupling Verification**
- **Jira**: ``ATT-1219` / `ATT-1226``
- **Test Description**:
  1. *Unit Test `SegmentsRepository.isSyncing` Lifecycle*:
  • Verify `isSyncing` initial value is `false`.
  • Trigger `syncStarredSegments(BSportType.UNKNOWN)` with mocked delay or suspended network fetch; verify `isSyncing` emits `true` during active execution across both Bike and Run worker phases.
  • Verify `isSyncing` emits `false` immediately upon successful completion.
  • Mock network/JSON parsing exception during `syncStarredSegments`; verify `isSyncing` resets cleanly to `false` in `finally`.
  2. *Unit Test `syncStarredSegmentsWorker` Defensive Cleanup*:
  • Mock an unhandled exception inside `syncStarredSegmentsWorker(BSportType.BIKE)`; verify `_refreshingSports` set does NOT retain `BSportType.BIKE` post-exception.
  3. *Unit Test Persistence Invariant & Separation of Concerns*:
  • Verify that during active synchronization (`isSyncing == true`), `TrainingApplication.getLastUpdateTimeOfStravaSegments()` is NOT mutated with `"now"` or any transient UI text.
  • Verify `TrainingApplication.setLastUpdateTimeOfStravaSegments` is called ONLY at the conclusion of successful sync with a formatted date string.
  • Verify if sync fails with an exception, `TrainingApplication.getLastUpdateTimeOfStravaSegments()` remains unchanged from its initial value.
  4. *Unit Test / Compose Verification of `StravaSettingsDialog` UI Binding*:
  • In `StravaSettingsDialog`, simulate `isSegmentsSyncing = true`; verify the rendered secondary text equals `stringResource(R.string.lastUpdateOfSegmentsNow)`.
  • Simulate `isSegmentsSyncing = false`; verify the rendered secondary text equals `segmentsLastUpdate`.
  • Simulate user clicks while `isSegmentsSyncing = true`; verify `syncSegmentsAsync` is not invoked (debounced).
  5. *Unit Test 9-Language Resource Parity*:
  • Verify string resource `lastUpdateOfSegmentsNow` is defined with valid non-empty translation across all 9 locales: `values` ("now"), `values-de` ("jetzt"), `values-es` ("ahora"), `values-fr` ("maintenant"), `values-it` ("adesso"), `values-ja` ("今"), `values-nl` ("nu"), `values-pl` ("teraz"), `values-pt` ("agora").
  6. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.

### TST-EXP-012: **Reactive Strava Route Synchronization Progress & Transient Decoupling Verification** [`RoutesRepository.isSyncing` reactively reflects sync state; SharedPreferences is never polluted with transient text; UI displays localized "now" during sync; clicks are debounced; 9-language parity verified; 100% test pass rate.]
- **Requirement(s)**: `REQ-EXP-015`
- **Component**: **Reactive Strava Route Synchronization Progress & Transient Decoupling Verification**
- **Jira**: ``ATT-1230` / `ATT-1233``
- **Test Description**:
  1. *Unit Test `RoutesRepository.isSyncing` Lifecycle*:
  • Verify `isSyncing` initial value is `false`.
  • Trigger `syncRoutesFromStrava()` with suspended network fetch; verify `isSyncing` emits `true` during active execution.
  • Verify `isSyncing` emits `false` immediately upon successful completion.
  • Mock network/JSON decoding exception during `syncRoutesFromStrava()`; verify `isSyncing` resets cleanly to `false` in `finally`.
  2. *Unit Test Persistence Invariant & Separation of Concerns*:
  • Verify that during active synchronization (`isSyncing == true`), `TrainingApplication.getLastUpdateTimeOfStravaRoutes()` is NOT mutated with `"now"` or any transient UI text.
  • Verify `TrainingApplication.setLastUpdateTimeOfStravaRoutes` is called ONLY at the conclusion of successful sync with a formatted date string.
  • Verify if sync fails with an exception, `TrainingApplication.getLastUpdateTimeOfStravaRoutes()` remains unchanged from its initial value.
  3. *Unit Test / Compose Verification of `StravaSettingsDialog` UI Binding*:
  • In `StravaSettingsDialog`, simulate `isRoutesSyncing = true`; verify the rendered secondary text equals `stringResource(R.string.lastUpdateOfRoutesNow)`.
  • Simulate `isRoutesSyncing = false`; verify the rendered secondary text equals `routesLastUpdate`.
  • Simulate user clicks while `isRoutesSyncing = true`; verify `syncRoutesFromStravaAsync` is not invoked (debounced).
  4. *Unit Test 9-Language Resource Parity*:
  • Verify string resource `lastUpdateOfRoutesNow` is defined with valid non-empty translation across all 9 locales: `values` ("now"), `values-de` ("jetzt"), `values-es` ("ahora"), `values-fr` ("maintenant"), `values-it` ("adesso"), `values-ja` ("今"), `values-nl` ("nu"), `values-pl` ("teraz"), `values-pt` ("agora").
  5. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.

### TST-EXP-013: **Reactive Strava Equipment Synchronization Progress & Transient Decoupling Verification** [`EquipmentRepository.isSyncing` reactively reflects sync state; SharedPreferences is never polluted with transient text; UI displays localized "now" during sync; clicks and worker calls are debounced; 9-language parity verified; 100% test pass rate.]
- **Requirement(s)**: `REQ-EXP-016`
- **Component**: **Reactive Strava Equipment Synchronization Progress & Transient Decoupling Verification**
- **Jira**: ``ATT-1231` / `ATT-1238``
- **Test Description**:
  1. *Unit Test `EquipmentRepository.isSyncing` Lifecycle*:
  • Verify `isSyncing` initial value is `false`.
  • Call `EquipmentRepository.setSyncing(true)`; verify `isSyncing` emits `true`.
  • Call `EquipmentRepository.setSyncing(false)`; verify `isSyncing` emits `false`.
  • Simulate `StravaEquipmentSynchronizeThread` execution: verify `isSyncing` transitions to `true` during active execution and reliably resets to `false` in `finally`.
  • Mock an unhandled exception or `Throwable` inside `getStravaEquipment()`; verify `isSyncing` resets cleanly to `false` in `finally`.
  2. *Unit Test Two-Tier Debounce Guard*:
  • While `EquipmentRepository.isSyncing` is `true`, execute `StravaEquipmentSynchronizeThread.run()`; verify early return without network or DB calls.
  3. *Unit Test Persistence Invariant & Separation of Concerns*:
  • Verify that during active synchronization (`isSyncing == true`), `TrainingApplication.getLastUpdateTimeOfStravaEquipment()` is NOT mutated with `"now"` or any transient UI text.
  • Verify `TrainingApplication.setLastUpdateTimeOfStravaEquipment` is called ONLY at the conclusion of sync with a formatted date string or error result.
  • Verify if sync fails, `SharedPreferences` is never polluted with `"now"`.
  4. *Unit Test / Compose Verification of `StravaSettingsDialog` UI Binding*:
  • In `StravaSettingsDialog`, simulate `isEquipmentSyncing = true`; verify the rendered secondary text equals `stringResource(R.string.lastUpdateOfEquipmentNow)`.
  • Simulate `isEquipmentSyncing = false`; verify the rendered secondary text equals `equipmentLastUpdate`.
  • Simulate user clicks while `isEquipmentSyncing = true`; verify `StravaEquipmentSynchronizeThread` is not launched (debounced).
  5. *Unit Test 9-Language Resource Parity*:
  • Verify string resource `lastUpdateOfEquipmentNow` is defined with valid non-empty translation across all 9 locales: `values` ("now"), `values-de` ("jetzt"), `values-es` ("ahora"), `values-fr` ("maintenant"), `values-it` ("adesso"), `values-ja` ("今"), `values-nl` ("nu"), `values-pl` ("teraz"), `values-pt` ("agora").
  6. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.

### TST-EXP-014: **Non-Blocking Strava Equipment Background Synchronization & Dialog Elimination Verification** [`StravaEquipmentSynchronizeThread` executes fully non-blockingly without any ProgressDialog; broadcasts completion intent; updates formatted timestamp; respects concurrency debounce; zero regressions.]
- **Requirement(s)**: `REQ-EXP-017`
- **Component**: **Non-Blocking Strava Equipment Background Synchronization & Dialog Elimination Verification**
- **Jira**: ``ATT-1242` / `ATT-1249``
- **Test Description**:
  1. *Unit Test Non-Blocking Dialog-Free Execution*:
  • Verify `StravaEquipmentSynchronizeThread` executes on a background thread without creating, showing, or referencing `ProgressDialog`.
  • Verify `StravaEquipmentSynchronizeThread` constructor accepts a mocked or generic `Context` without requiring an `Activity` or throwing window/token exceptions.
  2. *Unit Test JSON Parsing Without Progress Posting*:
  • Verify `fillDbFromJsonObject` processes `shoes` and `bikes` JSON arrays and updates `EquipmentDbHelper` without invoking progress callbacks or attempting to update dialog messages.
  3. *Unit Test Broadcast and Timestamp Invariants*:
  • Verify `mContext.sendBroadcast` is invoked with `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED` and correct package name upon thread completion.
  • Verify `TrainingApplication.setLastUpdateTimeOfStravaEquipment` is called with the formatted timestamp string upon completion.
  4. *Unit Test Lifecycle & Concurrency Debounce*:
  • Verify `EquipmentRepository.isSyncing` transitions to `true` and resets to `false` in `finally`.
  • Verify thread-level debounce exits immediately if `isSyncing` is already `true`.
  5. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.

### TST-EXP-016: **Automated Background Synchronization for Strava Segments, Routes, and Equipment Verification** [WorkManager periodic synchronization for Strava segments, routes, and equipment honors user preferences, configurable intervals, and token refresh; no startup cancellation storms; purge invariant preserved; 100% test pass rate.]
- **Requirement(s)**: `REQ-EXP-019`
- **Component**: **Automated Background Synchronization for Strava Segments, Routes, and Equipment Verification**
- **Jira**: ``ATT-3478``
- **Test Description**:
  1. *Startup Non-Cancellation Contract Tests (`TrainingApplicationTest.kt`)*:
  • Verify `PreferenceManager.setDefaultValues()` and `cSharedPreferences` initialization execute before `initWorkManager()` and worker scheduling in `TrainingApplication.onCreate()`.
  • Verify `TrainingApplication.getStravaAccessToken()` does not return null due to uninitialized preferences on cold start, preserving scheduled WorkManager jobs without accidental cancellation.
  2. *Preference Reading & Setting Parity Tests (`TrainingApplicationTest.kt`)*:
  • Verify `isAutomatedStravaSegmentsSyncEnabled()` reads `cSharedPreferences.getBoolean(SP_AUTOMATED_STRAVA_SEGMENTS_SYNC, true)` instead of hardcoded true.
  • Verify `getStravaSegmentsSyncIntervalDays()` reads `cSharedPreferences.getString(SP_STRAVA_SEGMENTS_SYNC_INTERVAL_DAYS, "1")`.
  • Verify `isAutomatedStravaRoutesSyncEnabled()` reads `cSharedPreferences.getBoolean(SP_AUTOMATED_STRAVA_ROUTES_SYNC, true)`.
  • Verify `getStravaRoutesSyncIntervalDays()` reads `cSharedPreferences.getString(SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS, "1")`.
  • Verify `isAutomatedStravaEquipmentSyncEnabled()` and interval getters/setters operate on `SP_AUTOMATED_STRAVA_EQUIPMENT_SYNC` and `SP_STRAVA_EQUIPMENT_SYNC_INTERVAL_DAYS`.
  3. *Strava Equipment Sync Worker Unit Tests (`StravaEquipmentSyncWorkerTest.kt`)*:
  • Verify `schedule(context)` enqueues unique periodic work for `automated_strava_equipment_sync_work` with `ExistingPeriodicWorkPolicy.UPDATE`, connected network constraints, and user-configured interval.
  • Verify `schedule(context)` cancels unique work when disconnected or when automated equipment sync is disabled.
  • Verify `doWork()` refreshes access token via `StravaHelper.getRefreshedAccessToken()`, executes equipment sync via `StravaEquipmentSynchronizeThread`, and returns `Result.success()`.
  • Verify `doWork()` returns `Result.success()` without network calls when disconnected or disabled.
  • Verify `doWork()` returns `Result.retry()` when sync fails with an unhandled exception.
  4. *Segments and Routes Worker Preference Honoring Tests (`StravaSegmentsSyncWorkerTest.kt`, `StravaRoutesSyncWorkerTest.kt`)*:
  • Verify `schedule()` configures interval matching user preference (e.g. 3 days) and cancels work when toggled off.
  • Verify `doWork()` checks refreshed access token via `StravaHelper.getRefreshedAccessToken()` and returns `Result.success()` if disabled.
  5. *Data Purge Invariant Tests (`StravaDataPurgeManagerTest.kt`)*:
  • Verify `executeLocalDataPurge` cancels `automated_strava_equipment_sync_work`.
  6. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify new equipment sync strings are defined with valid non-empty translations across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules asserting 100% test pass rate with zero regressions.

### TST-EXP-017: **Decommission Obsolete Runkeeper and TrainingPeaks Legacy Exporters, Activities, and Unused Apache HttpClient Dependencies Verification** [Apache HttpClient dependencies and legacy library wrapper completely removed; dead Runkeeper and TrainingPeaks classes, activities, and elevation helpers permanently excised; SQLite schema compatibility preserved; 100% test pass rate.]
- **Requirement(s)**: `REQ-EXP-020`
- **Component**: **Decommission Obsolete Runkeeper and TrainingPeaks Legacy Exporters, Activities, and Unused Apache HttpClient Dependencies Verification**
- **Jira**: ``ATT-3551``
- **Test Description**:
  1. *Static Contract Tests (`LegacyHttpClientPurgeContractTest.kt`)*:
  • Verify 0 occurrences of `org.apache.http` across all `.kt` and `.java` source files in `app/src/main/java/`.
  • Verify 0 occurrences of `org.apache.httpcomponents` dependencies in `app/build.gradle`.
  • Verify 0 occurrences of `org.apache.http.legacy` in `app/src/main/AndroidManifest.xml`.
  • Verify `<activity>` declarations for `RunkeeperGetAccessTokenActivity` and `TrainingpeaksGetAccessTokenActivity` are absent from `AndroidManifest.xml`.
  • Verify deleted legacy classes (`RunkeeperGetAccessTokenActivity.java`, `RunkeeperTestActivity.java`, `TrainingpeaksGetAccessTokenActivity.java`, `BaseGetAccessTokenActivity.java`, `RunkeeperUploader.java`, `TrainingPeaksUploader.java`, `RunkeeperFileWriter.java`) do not exist on disk.
  • Verify `SportTypeDatabaseManager.SportType.RUNKEEPER_NAME` remains present to preserve SQLite database table schema backward compatibility.
  2. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules asserting 100% test pass rate with zero regressions.

### TST-EXP-018: **Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection Verification** [Transient Strava upload failures (429, 5xx) and timeouts trigger WorkManager retry; polling loop survives intermittent null responses; duplicate discovery rejects false starts, unparseable dates, and sport mismatches; SQL queries parameterized; 100% test pass rate.]
- **Requirement(s)**: `REQ-EXP-021`
- **Component**: **Strava Upload Transient Error Resilient Retry, Polling Fault Tolerance & Pre-Upload Duplicate False-Start Protection Verification**
- **Jira**: ``ATT-4012``
- **Test Description**:
  1. *HTTP Error Classification Unit Tests (`StravaUploaderTest.kt`)*:
  • Mock upload response returning HTTP 429 Too Many Requests with headers `X-RateLimit-Limit: 100,1000` and `X-RateLimit-Usage: 100,500`. Verify `uploadToStrava` returns `ExportResult(false, true, ...)`.
  • Mock upload response returning HTTP 500, 502, 503, and 504. Verify all return `ExportResult(false, true, ...)`.
  • Mock upload response returning HTTP 400, 403, 404, and 422. Verify all return `ExportResult(false, false, ...)`.
  • Mock upload response returning HTTP 401 Unauthorized; verify token refresh attempt and clean failure without infinite loop.
  2. *Polling Fault Tolerance Unit Tests (`StravaUploaderTest.kt`)*:
  • Simulate upload status polling where attempt 1 returns `null` (simulating transient network drop) and attempt 2 returns `status = ready`. Verify the loop continues after attempt 1 and completes successfully.
  • Simulate polling where all attempts return `null` or `processing`. Verify after `MAX_REQUESTS` it returns `ExportResult(false, true, "Timeout waiting for Strava processing")`.
  3. *Pre-Upload Duplicate Discovery Unit Tests (`StravaUploaderNamingTest.kt`)*:
  • Verify that when candidate Strava activity has an unparseable `start_date` (`"invalid-date"`), `findExistingStravaActivity` skips the activity and does NOT match with `diff = 0`.
  • Verify that when candidate Strava activity is a 2-minute false start (`elapsed_time = 120`) and local workout is 2 hours (`TIME_TOTAL = 7200`), `findExistingStravaActivityForWorkout` rejects the candidate and proceeds to file upload.
  • Verify that when candidate Strava activity is a `Run` and local workout is `BIKE`, `findExistingStravaActivityForWorkout` rejects the candidate and proceeds to file upload.
  • Verify that when candidate Strava activity is a `Ride` with matching timestamp and duration ($\pm 10\%$), `findExistingStravaActivityForWorkout` discovers the duplicate and bypasses TCX file upload.
  4. *Safe Response Body Handling Unit Tests*:
  • Verify empty or null response bodies in OkHttp calls do not throw `NullPointerException`.
  5. *Database Parameterization Unit Tests (`StravaUploadDbHelperTest.kt`)*:
  • Verify `getStravaActivityDataForWorkouts` executes using parameterized SQL queries.
  • Verify `updateOrInsert` correctly handles duplicate inserts using `CONFLICT_REPLACE`.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules asserting 100% test pass rate with zero regressions.

### TST-EXP-019: **GPX/TCX Export XML Well-Formedness, Garmin Schema Compliance, Downloads MIME Type & Privacy Verification** [GPX and TCX exports are well-formed XML and schema compliant; segment state machine prevents orphan tags; TCX creator placed within Activity; Downloads copies use proper MIME types and omit internal community files; 100% test pass rate.]
- **Requirement(s)**: `REQ-EXP-022`
- **Component**: **GPX/TCX Export XML Well-Formedness, Garmin Schema Compliance, Downloads MIME Type & Privacy Verification**
- **Jira**: ``ATT-4013``
- **Test Description**:
  1. *GPX Segment State Machine Unit Tests (`GPXFileWriterTest.kt`)*:
  • Export workout where initial lap 0 is cropped (samples begin at lap 1). Verify XML does not begin with orphan `</trkseg>` tag, opens with `<trkseg>`, and parses cleanly via XML DOM parser without errors.
  • Export workout with 0 samples. Verify file parses cleanly as valid XML with zero orphan `</trkseg>` tags.
  • Export multi-lap workout (laps 0, 1, 2). Verify matching opening `<trkseg>` and closing `</trkseg>` tags for each lap.
  2. *TCX Garmin v2 Schema Compliance Unit Tests (`TCXFileWriterTest.kt`)*:
  • Export sample workout to TCX. Verify XML is well-formed.
  • Verify `<Creator xsi:type="Device_t">` is serialized as a child of `<Activity>`, following `<Notes>` and preceding `<Extensions>`.
  • Verify `<Creator>` contains required children: `<Name>`, `<UnitId>`, `<ProductID>`, and `<Version>`.
  • Verify `<Author xsi:type="Application_t">` is serialized under `<TrainingCenterDatabase>` with required children: `<Name>`, `<Build>`, `<LangID>`, and `<PartNumber>`.
  • Validate output TCX XML against official Garmin `TrainingCenterDatabasev2.xsd` schema.
  3. *Thread Safety & UTC Formatting Unit Tests*:
  • Concurrently format timestamps across multiple threads and verify deterministic, thread-safe UTC ISO-8601 formatting without race condition corruption or local timezone DST shifts.
  4. *Public Downloads Copying, MIME Types & Privacy Unit Tests (`BaseExporterDownloadsTest.kt`)*:
  • Verify `FileFormat.getMimeType()` returns expected MIME types for GPX, TCX, STRAVA, FIT, CSV, and GC.
  • Verify `copyFileToDownloads` sets MediaStore `MIME_TYPE` corresponding to `exportInfo.getFileFormat()`.
  • Verify internal community uploads (`FileFormat.STRAVA` or `ExportType.COMMUNITY`) do NOT invoke `copyFileToDownloads` or create files in public storage.
  • Verify explicit file exports (`ExportType.FILE`) continue to invoke `copyFileToDownloads`.
  • Verify that if `resolver.insert()` returns null, `copyFileToDownloads` logs a warning and completes gracefully without throwing `NullPointerException`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules asserting 100% test pass rate with zero regressions.

### TST-MIG-001: **Full Migration Cycle** [App state (settings, history, equipment) is perfectly restored.]
- **Requirement(s)**: `REQ-MIG-001`, `002`, `004`, `006`
- **Component**: **Full Migration Cycle**
- **Jira**: ``ATT-117``
- **Test Description**: 1. Generate local backup. 2. Wipe app data. 3. Restore from backup.

### TST-MIG-002: **Deduplication Verification** [Duplicate workouts are identified by `FILE_BASE_NAME` and skipped.]
- **Requirement(s)**: `REQ-MIG-005`
- **Component**: **Deduplication Verification**
- **Jira**: ``ATT-117``
- **Test Description**: 1. Import a backup containing already existing workouts.

### TST-MIG-003: **Automated Dropbox Backup** [Backup is present and valid in `/Backups/` folder.]
- **Requirement(s)**: `REQ-MIG-003`
- **Component**: **Automated Dropbox Backup**
- **Jira**: ``ATT-93``
- **Test Description**: 1. Enable regular backups. 2. Verify archive exists in Dropbox after background task runs.

### TST-MIG-004: **Full Restore Confirmation (Dropbox)** [1. A confirmation dialog appears with a warning about data loss. 2. Tapping 'Cancel' dismisses the dialog. 3. Tapping 'Restore' starts the process.]
- **Requirement(s)**: `REQ-MIG-007`
- **Component**: **Full Restore Confirmation (Dropbox)**
- **Jira**: ``ATT-290``
- **Test Description**: 1. Navigate to 'Backup \u0026 Restore'. 2. Tap \"Dropbox: Restore Backup\".

### TST-MIG-005: **Informative Labels Audit** [Labels are accurate and follow the dual-action naming. Descriptions are clearly visible and explain the operation impact.]
- **Requirement(s)**: `REQ-MIG-008`
- **Component**: **Informative Labels Audit**
- **Jira**: ``ATT-295``
- **Test Description**: 1. Open Backup \u0026 Restore dashboard. 2. Verify Dropbox button label. 3. Verify presence of descriptions in Restore and Import sections.

### TST-MIG-006: **Default Backup Interval Verification** [The interval SHALL be set to 'Daily' by default.]
- **Requirement(s)**: `REQ-MIG-009`
- **Component**: **Default Backup Interval Verification**
- **Jira**: ``ATT-321``
- **Test Description**: 1. Clear application data. 2. Navigate to 'Dropbox' settings. 3. Inspect 'Backup Interval'.

### TST-MIG-007: **Enable Backups by Default Verification** [The switch SHALL be in the 'ON' position by default.]
- **Requirement(s)**: `REQ-MIG-010`
- **Component**: **Enable Backups by Default Verification**
- **Jira**: ``ATT-322``
- **Test Description**: 1. Clear application data. 2. Navigate to 'Dropbox' settings. 3. Inspect 'Automated Backups' switch.

### TST-MIG-008: **Tabbed UI Navigation** [UI is organized into tabs. Navigation is smooth. Titles match requirements.]
- **Requirement(s)**: `REQ-MIG-011`
- **Component**: **Tabbed UI Navigation**
- **Jira**: ``ATT-323``
- **Test Description**: 1. Open drawer and verify 'Import \u0026 Backup' label. 2. Open screen and verify 3 tabs: Import, Backup, Restore. 3. Swipe between tabs.

### TST-MIG-009: **Pre-Import Workout Cluster Tuning Bottom Sheet & Info Action Verification** [The bottom sheet provides a spacious, responsive layout without spatial confinement or clipped controls. The Info button launches `ClusterInfoDialog` properly. Tolerances persist upon confirmation and import commences smoothly.]
- **Requirement(s)**: `REQ-MIG-012`
- **Component**: **Pre-Import Workout Cluster Tuning Bottom Sheet & Info Action Verification**
- **Jira**: ``ATT-793``
- **Test Description**:
  1. Navigate to 'Import & Backup' -> 'Import' tab.
  2. Select a single legacy TCX file or trigger bulk TCX scan to launch pre-import tuning.
  3. Verify interface renders as a Material 3 `ModalBottomSheet` spanning full device width with drag handle, header title, and Info button.
  4. Tap Info button (`Icons.Outlined.Info`) and verify `ClusterInfoDialog` appears with educational content, and dismisses cleanly back to bottom sheet.
  5. Verify smooth scrolling and spacious layout of `ClusterTuningContent` (master slider, detailed tolerance sliders, sport type switch, altitude switch).
  6. Adjust parameters, tap OK, and verify tolerances persist to `SharedPreferences` and import commences.
  7. Re-open sheet, tap Cancel or dismiss sheet via drag, and verify no import commences and unconfirmed edits are discarded.

### TST-MIG-010: **Idempotent TCX Import** [The system SHALL detect the existing workout and skip it. No clustering dialog SHALL appear.]
- **Requirement(s)**: `REQ-MIG-013`
- **Component**: **Idempotent TCX Import**
- **Jira**: ``ATT-331``
- **Test Description**: 1. Import a TCX file. 2. Re-run 'Scan TCX' for the same file.

### TST-MIG-011: **Legacy Recovery Performance Audit** [The skipping messages appear almost instantaneously, without the 2-5 second delay previously caused by file downloads.]
- **Requirement(s)**: `REQ-MIG-015`
- **Component**: **Legacy Recovery Performance Audit**
- **Jira**: ``ATT-337``
- **Test Description**: 1. Ensure several legacy workouts exist in the local database. 2. Run 'Scan TCX' with those same files present in Dropbox.

### TST-MIG-012: **Paginated Cloud Scan Verification** [The app SHALL discover all files and process the entire set.]
- **Requirement(s)**: `REQ-MIG-016`
- **Component**: **Paginated Cloud Scan Verification**
- **Jira**: ``ATT-339``
- **Test Description**: 1. Use a Dropbox account with > 1000 TCX files in the target folder. 2. Trigger 'Scan TCX'.

### TST-MIG-013: **Interaction Queue Size Limiting** [Interaction queue size is strictly enforced (limit: 10). The engine MUST NOT download or parse more files until space is cleared in the queue.]
- **Requirement(s)**: `REQ-MIG-017`
- **Component**: **Interaction Queue Size Limiting**
- **Jira**: ``ATT-401``
- **Test Description**: 1. Start bulk import of > 10 unclustered workouts. 2. Verify engine pauses at 10 queued items. 3. Monitor network/parsing logs.

### TST-MIG-014: **Sparse Data Import** [Missing sensors are NOT displayed in summary and NOT created as columns in samples table.]
- **Requirement(s)**: `REQ-MIG-018`
- **Component**: **Sparse Data Import**
- **Jira**: ``ATT-403``
- **Test Description**: 1. Import a TCX file missing HR/Power. 2. Inspect summary and DB schema.

### TST-MIG-015: **Dropbox Wait Feedback** [Explicit messages like "Waiting for Dropbox..." or "Downloading [File]..." SHALL be visible during network operations.]
- **Requirement(s)**: `REQ-MIG-019`
- **Component**: **Dropbox Wait Feedback**
- **Jira**: ``ATT-405``
- **Test Description**: 1. Trigger 'Scan TCX' from Dropbox. 2. Observe the progress status messages.

### TST-MIG-016: **Positive Import UI Alignment** [1. The redundant introduction message is GONE. 2. The section title is positive (e.g., \"Workout Import\") and avoids recovery/legacy terminology. 3. Buttons have feature-oriented labels.]
- **Requirement(s)**: `REQ-MIG-020`
- **Component**: **Positive Import UI Alignment**
- **Jira**: ``ATT-435``
- **Test Description**: 1. Navigate to 'Import & Backup'. 2. Inspect the 'Import' tab content.

### TST-MIG-017: **Concurrent TCX Import Background Queuing & Throttling** [The TCX import engine SHALL continue downloading and processing subsequent files concurrently in the background while waiting for user interaction, pausing ONLY when 3 items accumulate in the interaction queue.]
- **Requirement(s)**: `REQ-MIG-014`, `REQ-MIG-017`
- **Component**: **Concurrent TCX Import Background Queuing & Throttling**
- **Jira**: ``ATT-549``
- **Test Description**: 1. Start bulk import of multiple TCX files requiring route cluster naming. 2. Leave the first cluster naming dialog open without confirming immediately. 3. Monitor background progress indicators.

### TST-MIG-018: **Recursive Cloud Folder Scanning & Informative Import Summary** [The import engine discovers files across all paths and subdirectories without terminating early. The final message clearly states the number of newly imported workouts, skipped existing workouts, and total files scanned.]
- **Requirement(s)**: `REQ-MIG-016`, `REQ-MIG-021`
- **Component**: **Recursive Cloud Folder Scanning & Informative Import Summary**
- **Jira**: ``ATT-560``
- **Test Description**: 1. Set up a Dropbox folder structure containing TCX files in multiple paths and subdirectories (e.g., `/TCX/2025/` and `/apps/Workouts/TCX/`). 2. Trigger bulk recovery. 3. Inspect final success message.

### TST-MIG-019: **Workout Name Filename Integrity** [The workout name and `FILE_BASE_NAME` in the database match `2026-08-01_10-00-00` exactly. No numeric counter prefix (e.g. `1_`) is prepended.]
- **Requirement(s)**: `REQ-MIG-022`
- **Component**: **Workout Name Filename Integrity**
- **Jira**: ``ATT-571``
- **Test Description**: 1. Import a TCX file named `2026-08-01_10-00-00.tcx`. 2. Inspect the workout list and database entry for `FILE_BASE_NAME` and `WORKOUT_NAME`.

### TST-MIG-020: **Unlinked Dropbox Import Deactivation & Null Safety** [The feature is visibly deactivated in grey, clicking it displays the informational dialog, and credential reading never crashes with NullPointerException.]
- **Requirement(s)**: `REQ-MIG-023`
- **Component**: **Unlinked Dropbox Import Deactivation & Null Safety**
- **Jira**: ``ATT-491``
- **Test Description**: 1. Ensure Dropbox is not linked in settings. 2. Open 'Import & Backup' -> 'Import' tab. 3. Verify 'Scan Dropbox' is styled in grey. 4. Click 'Scan Dropbox' and verify informational dialog appears without crashing. 5. Run automated unit test suite.

### TST-MIG-021: **High-Fidelity TCX Telemetry & Multi-Lap Structure Import** [All laps, instantaneous speed/cadence streams, active durations, calories, and workout notes are faithfully preserved without data loss or pause inflation.]
- **Requirement(s)**: `REQ-MIG-024`
- **Component**: **High-Fidelity TCX Telemetry & Multi-Lap Structure Import**
- **Jira**: ``ATT-618``
- **Test Description**: 1. Construct a multi-lap TCX test fixture containing: 2 distinct `<Lap>` elements with explicit `<TotalTimeSeconds>`, `<DistanceMeters>`, `<Calories>`, and `<MaximumSpeed>`; trackpoints inside `<Extensions><TPX>` containing `<Speed>` and `<RunCadence>`; an activity `<Notes>` field; and a 5-minute pause gap between Lap 1 end and Lap 2 start. 2. Execute `LegacyImportEngine.importFromTcx()` on the fixture. 3. Query `LapsDatabaseManager.getLaps(workoutId)`: Verify 2 distinct lap records exist with matching durations, distances, and metrics. 4. Query `WorkoutSamplesDatabaseManager`: Verify samples have `SensorType.SPEED` and `SensorType.CADENCE` values from the TPX stream, and `SensorType.LAP_NR` values corresponding to 0 and 1. 5. Query `WorkoutSummariesDatabaseManager.getWorkoutSummary(workoutId)`: Verify active duration equals the sum of `<TotalTimeSeconds>` (excluding the 5-minute pause gap), calories are populated, and notes match.

### TST-MIG-022: **TCX Import Leave Unclustered Integrity Verification** [The workout remains unassigned (`clusterId == -1L`), no new cluster is created in `WorkoutClusterDatabaseManager`, the original workout name is preserved, and the workout appears in the Unclustered workouts collection.]
- **Requirement(s)**: `REQ-MIG-025`
- **Component**: **TCX Import Leave Unclustered Integrity Verification**
- **Jira**: ``ATT-741``
- **Test Description**: 1. Construct a spatial workout fixture where `suggestCluster()` finds no existing cluster match. 2. Simulate user selection of "Leave Unclustered" (`Pair(null, null)`). 3. Verify `LegacyImportEngine` leaves `WorkoutSummaries.CLUSTER_ID = -1L`. 4. Trigger `WorkoutRepository.reloadWorkoutData()`. 5. Verify `WorkoutClusterEngine.onWorkoutFinished()` does NOT create an auto-generated fallback cluster, does NOT assign any cluster, and does NOT mutate `WorkoutSummaries.CLUSTER_ID` (it remains `-1L`). 6. Verify the workout name does NOT become "Workout at [date]" / "Einheit bei [date]". 7. Verify the workout is queryable under the Unclustered tab (`clusterId == -1L`).

### TST-MIG-023: **TCX Import Periods Aggregation & Self-Healing Sync** [Imported workouts are immediately visible in all period summaries without requiring broadcast reception or app restart. The self-healing integrity check automatically detects and repairs any discrepancy between finished workouts in SQLite and period summaries.]
- **Requirement(s)**: `REQ-MIG-026`
- **Component**: **TCX Import Periods Aggregation & Self-Healing Sync**
- **Jira**: ``ATT-909``
- **Test Description**:
  1. Clear in-memory `WorkoutRepository` instance to simulate cold repository state during import.
  2. Import a historical TCX workout file via `LegacyImportEngine.importFromTcx()`.
  3. Verify that `WorkoutRepository.reloadWorkoutData(workoutId)` and `PeriodsRepository.onWorkoutFinished()` are invoked directly.
  4. Query `PeriodSummariesDatabaseManager`: verify Day, Week, Month, and Year periods exist for the workout timestamp and contain updated statistics (`total_distance`, `total_duration`, `total_workouts`).
  5. Simulate an out-of-sync state by inserting a finished workout into `WorkoutSummaries` without updating `PeriodSummaries.db`.
  6. Trigger `PeriodsRepository.loadPeriods()`.
  7. Verify that the self-healing count check detects the discrepancy and executes `performHierarchicalMigration()` to reconcile all missing periods.

### TST-MIG-024: **TCX Import Cluster Heuristics & Reactive UI Cache Refresh** [Workouts with unknown/missing sports are accurately matched against route clusters based on spatial geometry and speed heuristics. Cluster names boost similarity scoring. Assigned workouts update cluster hit counts and preview states in real time across the UI.]
- **Requirement(s)**: `REQ-MIG-027`
- **Component**: **TCX Import Cluster Heuristics & Reactive UI Cache Refresh**
- **Jira**: ``ATT-909``
- **Test Description**:
  1. Construct a TCX workout fixture with sport type `UNKNOWN` (e.g. unmapped activity) and average speed typical of cycling/running.
  2. Ensure an existing cluster exists with matching route geometry.
  3. Execute `LegacyImportEngine.importFromTcx()`.
  4. Verify that `suggestCluster()` does NOT pass `setOf(BSportType.UNKNOWN)` and is not penalized with `+5.0` distance penalty.
  5. Verify that extracted workout name is passed to `suggestCluster()` enabling the 50% name-match similarity discount.
  6. Verify `WorkoutSummaries.B_SPORT` is populated with the inferred `BSportType` name.
  7. Verify `WorkoutClusterRepository.refreshClusters()` is invoked and in-memory caches (`allClusters`, `clusterStats`, `unclusteredWorkouts`) immediately update with the newly imported session.

### TST-MIG-025: **TCX Import Optional Strava Upload & UI Switch Verification** [Disabling Strava upload prevents auto-upload and sets `UPLOAD_TO_STRAVA = 0`. Enabling Strava upload schedules export and sets `UPLOAD_TO_STRAVA = 1`. Pre-import bottom sheet conditionally renders the switch only when Strava is connected. All 9 language resource directories satisfy translation parity.]
- **Requirement(s)**: `REQ-MIG-028`
- **Component**: **TCX Import Optional Strava Upload & UI Switch Verification**
- **Jira**: ``ATT-911``
- **Test Description**:
  1. Configure Strava as connected (`TrainingApplication.uploadToCommunity(STRAVA) == true`).
  2. Call `LegacyImportEngine.importFromTcx()` with `uploadToStrava = false`. Verify `WorkoutSummaries.UPLOAD_TO_STRAVA` is saved as `0`, and verify zero Strava export tasks are scheduled in `ExportManager`.
  3. Call `LegacyImportEngine.importFromTcx()` with `uploadToStrava = true`. Verify `WorkoutSummaries.UPLOAD_TO_STRAVA` is saved as `1`, and verify Strava export task is scheduled via `ExportManager.exportWorkoutTo(workoutId, STRAVA)`.
  4. Call `LegacyImportEngine.bulkRecoverFromDropbox()` with `uploadToStrava = false` and verify that the flag is forwarded to individual TCX imports, resulting in `UPLOAD_TO_STRAVA = 0`.
  5. Render `PreImportTuningBottomSheet` when Strava is connected: verify that the Strava upload toggle switch is visible and accurately reflects `BackupRestoreViewModel.uploadToStravaOnImport`. Toggle the switch and verify `TrainingApplication.uploadImportedWorkoutsToStrava()` updates immediately.
  6. Render `PreImportTuningBottomSheet` when Strava is disconnected: verify that the Strava toggle switch is not visible.
  7. Execute `TranslationParityTest` to verify that `import_upload_to_strava_label` and `import_upload_to_strava_summary` exist across all 9 localized `strings.xml` resource files.

### TST-MIG-026: **TCX Import Route Cluster Auto-Matching Parity & Lossless Candidate Discovery** [Workouts with start point deviation $> 1.0\times$ endpoint tolerance but composite similarity $< 1.0$ are auto-matched without manual dialog intervention. `suggestCluster` and `ClusterNamingDialog` compute identical similarity scores.]
- **Requirement(s)**: `REQ-MIG-029`
- **Component**: **TCX Import Route Cluster Auto-Matching Parity & Lossless Candidate Discovery**
- **Jira**: ``ATT-1133``
- **Test Description**:
  1. Create an existing cluster in `WorkoutClusterDatabaseManager` with start point $P_{start}$, end point $P_{end}$, apex $P_{apex}$, distance 10,000m, and name "River Loop".
  2. Construct an imported workout fixture whose start point is displaced by 350m north from $P_{start}$ (exceeding default 200m endpoint tolerance by 1.75x), while end point, apex, and distance match tightly within tolerance, yielding a composite similarity score $\approx 0.55$ (< 1.0).
  3. Invoke `WorkoutClusterEngine.suggestCluster()` with the workout's spatial signature. Verify that the cluster is returned as the matching suggestion without premature SQLite pruning.
  4. Simulate `LegacyImportEngine.importFromTcx()` with this workout: Verify that `suggestCluster()` auto-assigns the workout to the cluster without invoking `listener.onNewClusterCandidate()`.
  5. Construct a workout with 3D altitude extrema points and distinct name: verify that `ClusterInteraction` in `BackupRestoreViewModel` receives and holds `workoutName`, `candidateSportTypes`, `minAltPos`, and `maxAltPos`.
  6. Verify that `ClusterNamingDialog` passes these parameters to `clusterEngine.scoreClusters()`, resulting in 100% numerical parity with `suggestCluster()`.
  7. Construct a workout that genuinely diverges from all existing clusters (composite similarity $\ge 1.0$): verify that `suggestCluster()` returns `null`, `onNewClusterCandidate()` is called, and all candidate scores in `scoreClusters()` are $\ge 1.0$.

### TST-MIG-027: **High-Fidelity GPX Workout Import & Full Telemetry Ingestion Parity** [GPX workouts import seamlessly with high-fidelity telemetry (HR, cadence, speed, power, temp), multi-lap structure, cumulative distance calculation, and full parity with TCX pipeline.]
- **Requirement(s)**: `REQ-MIG-030`
- **Component**: **High-Fidelity GPX Workout Import & Full Telemetry Ingestion Parity**
- **Jira**: ``ATT-1116``
- **Test Description**:
  1. Construct a multi-segment GPX test fixture containing: metadata title and description; multiple `<trkseg>` elements representing laps; trackpoints with latitude, longitude, elevation (`<ele>`), and UTC timestamps (`<time>`); Garmin TrackPointExtension (`gpxtpx:TrackPointExtension`) containing heart rate (`<gpxtpx:hr>`), cadence (`<gpxtpx:cad>`), speed (`<gpxtpx:speed>`), and temperature (`<gpxtpx:atemp>`); power extension (`<power>`); and sport type `<type>running</type>`.
  2. Execute `LegacyImportEngine.importFromGpx()` on the fixture.
  3. Verify that `WorkoutSummaries` records the workout with matching start time, workout name, notes, sport (`BSportType.RUN`), and total distance calculated from cumulative coordinates.
  4. Query `LapsDatabaseManager`: verify multiple distinct lap records are saved corresponding to the `<trkseg>` segments, each with valid duration, distance, and average speed.
  5. Query `WorkoutSamplesDatabaseManager`: verify sample rows contain `LATITUDE`, `LONGITUDE`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `HR`, `CADENCE`, `POWER`, `TEMPERATURE_c`, and corresponding `LAP_NR` values.
  6. Verify deduplication: attempting to re-import the same file skips insertion (`isWorkoutExisting` returns true).
  7. Verify cluster routing and period integration: verify `suggestCluster()` and `PeriodsRepository.onWorkoutFinished()` are called.
  8. Verify `BackupRestoreViewModel.importLegacyFile()` detects `.gpx` files and executes `importFromGpx()` successfully.

### TST-MIG-028: **Multi-Dimensional Workout Deduplication & Concurrent Worker Race Condition Guard Verification (Revision 2: Option 2)** [Workouts are deduplicated across differing filenames by start timestamp ($\pm 180$s for same sport); dual-phone recordings offset by 65s are skipped; duplicate remote folder paths are filtered prior to download; concurrent workers synchronize atomically; 100% test pass rate with zero duplicate records.]
- **Requirement(s)**: `REQ-MIG-031`
- **Component**: **Multi-Dimensional Workout Deduplication & Concurrent Worker Race Condition Guard Verification (Revision 2: Option 2)**
- **Jira**: ``ATT-2023``
- **Test Description**:
  1. *Multi-Dimensional Deduplication Unit Tests (`LegacyImportEngineDeduplicationTest.kt`)*:
  • Verify that when `fileBaseName` matches an existing record, `isWorkoutExisting` returns `true`.
  • Verify that when `fileBaseName` differs (e.g. `activity_12345`) but `timeStart` matches an existing workout's `TIME_START`, `isWorkoutExisting` returns `true`.
  • Verify that when `timeStart` falls within $\pm 180$ seconds (3 minutes) of an existing workout of the same sport, `isWorkoutExisting` returns `true`.
  • Verify that dual-phone recording offsets (65 seconds) evaluate as duplicates.
  • Verify that workouts of different sports within the 3-minute window do not match the sport clause.
  2. *Multi-Folder Scan Base Name Deduplication Tests*:
  • Verify that files discovered in multiple scanned Dropbox folders are deduplicated by base name before worker channel queuing.
  3. *Concurrent Worker Race Condition Tests*:
  • Verify that multiple concurrent coroutine workers attempting to import identical streams under `importMutex` are synchronized.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-MIG-029: **Post-Import Workout Navigation & Decoupled List Auto-Focus Verification** [Success banner displays `[View]` and `[OK]` upon import. Tapping `[View]` fires `WorkoutNavigationEvents` and auto-scrolls to the imported workout in the list. Tapping `[OK]` dismisses without navigation. Legacy return types and deduplication remain intact.]
- **Requirement(s)**: `REQ-MIG-032`
- **Component**: **Post-Import Workout Navigation & Decoupled List Auto-Focus Verification**
- **Jira**: ``ATT-2337``
- **Test Description**:
  1. *Import Engine Result Unit Tests (`LegacyImportEngineResultTest.kt`)*:
  • Verify `importFromTcxResult` returns `ImportResult(status = SUCCESS, workoutId = ...)` with valid rowId.
  • Verify `importFromGpxResult` returns `ImportResult(status = SUCCESS, workoutId = ...)`.
  • Verify `importFromFitResult` returns `ImportResult(status = SUCCESS, workoutId = ...)`.
  • Verify non-breaking delegate methods `importFromTcx(...)` and `importFromGpx(...)` continue returning boolean `true` on success.
  2. *ViewModel Success State Tests (`BackupRestoreViewModelNavigationTest.kt`)*:
  • Verify single-file import emits `UiState.Success(message, importedWorkoutId = 42L)`.
  • Verify batch FIT import with successful files emits `UiState.Success` with latest workout ID.
  • Verify batch import with 0 successful files emits `UiState.Success` with null workout ID.
  3. *Decoupled Bus Tests (`WorkoutNavigationEventsTest.kt`)*:
  • Verify `triggerNavigateToWorkout(id)` emits `id` and `resetNavigateToWorkout()` resets to null.
  4. *9-Language Localization Audit (`ImportNavigationLocalizationTest.kt`)*:
  • Verify all 9 `strings.xml` contain non-empty `action_view_workout`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-MIG-030: **FIT Enhanced Altitude & Speed Ingestion Verification** [Modern FIT files recorded by Garmin/Wahoo/Karoo retain full elevation curves, ascent/descent calculations, and speed samples without regression on legacy devices.]
- **Requirement(s)**: `REQ-MIG-033`
- **Component**: **FIT Enhanced Altitude & Speed Ingestion Verification**
- **Jira**: ``ATT-2338``
- **Test Description**:
  1. *Enhanced Altitude Unit Tests (`LegacyImportEngineFitAltitudeTest.kt`)*:
  • Verify `RecordMesg` with only `enhancedAltitude` populates `ALTITUDE` samples and computes ascent.
  • Verify `RecordMesg` with only `altitude` correctly falls back.
  • Verify `RecordMesg` with both prioritizes `enhancedAltitude`.
  • Verify out-of-range sentinel altitudes are filtered.
  2. *Enhanced Speed Unit Tests*:
  • Verify `RecordMesg` with `enhancedSpeed` populates `SPEED_mps`.
  • Verify `LapMesg` with `enhancedMaxSpeed` populates `maxSpeed`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules.

### TST-MIG-031: **Google Drive Historical Workout File Discovery, Top-Level Candidates & Zero-Result Diagnostic Verification** [Google Drive scanner discovers .fit, .tcx, and .gpx archives across nested and top-level paths; existing workouts are skipped without downloading; zero-result scans emit descriptive guidance; activities are parsed and inserted into WorkoutSummaries; post-import reconciliation updates periods and clusters; 100% test pass rate.]
- **Requirement(s)**: `REQ-MIG-034`
- **Component**: **Google Drive Historical Workout File Discovery, Top-Level Candidates & Zero-Result Diagnostic Verification**
- **Jira**: ``ATT-2346` / `ATT-3315``
- **Test Description**:
  1. *Google Drive Client Unit Tests (`GoogleDriveClientRecursiveListingTest.kt`)*:
  • Verify `listFilesRecursively` queries children of folderId.
  • Verify pagination handling with `nextPageToken`.
  • Verify filtering by extensions (`.fit`, `.tcx`, `.gpx`).
  • Verify `downloadFileById` downloads to destination file.
  2. *Bulk Recovery Contract Tests (`GoogleDriveBulkRecoveryContractTest.kt`)*:
  • Verify `bulkRecoverFromGoogleDrive` probes top-level folder candidates (`listOf("TCX")`, `listOf("FIT")`, etc.) in addition to nested paths.
  • Verify `bulkRecoverFromGoogleDrive` skips already existing workouts prior to download.
  • Verify multi-format routing (`.fit` -> `importFromFitInternal`, `.tcx` -> `importFromTcxInternal`, `.gpx` -> `importFromGpxInternal`).
  • Verify cross-folder base name deduplication.
  3. *ViewModel Google Drive Tests (`BackupRestoreViewModelGoogleDriveTest.kt`)*:
  • Verify disconnected Google Drive sets error message and aborts recovery.
  • Verify zero-result scan (`totalScanned == 0`) emits descriptive diagnostic `google_drive_scan_empty_diagnostic`.
  • Verify successful recovery triggers repository reloads (`loadAllWorkouts`, `syncPeriodsIfDiscrepancy`, `refreshClusters`).
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `scan_google_drive`, `legacy_import__downloading_google_drive`, and `google_drive_scan_empty_diagnostic` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-MIG-032: **Dropbox Historical Workout File Discovery & Bulk Recovery for FIT Verification** [Dropbox cloud recovery scanner discovers, downloads, and imports historical .fit files alongside legacy XML formats with cross-folder deduplication and pre-download duplicate skipping; 100% test pass rate.]
- **Requirement(s)**: `REQ-MIG-035`
- **Component**: **Dropbox Historical Workout File Discovery & Bulk Recovery for FIT Verification**
- **Jira**: ``ATT-2341``
- **Test Description**:
  1. *Contract Tests (`DropboxBulkRecoveryFitContractTest.kt`)*:
  • Verify `possiblePaths` contains `/FIT`, `/apps/Workouts/FIT`, and `/Workouts/FIT` and `targetExtensions` contains `".fit"` when `format = "fit"`.
  • Verify multi-format `format = "all"` scans TCX, GPX, and FIT directories and aggregates `.tcx`, `.gpx`, and `.fit` extensions.
  • Verify base name deduplication when `.fit` files reside across multiple candidate folders.
  • Verify pre-download duplicate skipping when base filename already exists in `WorkoutSummariesDatabase`.
  2. *Worker Routing Integration*:
  • Verify worker channel dispatches `.fit` files to `importFromFitInternal`.
  • Verify `importedCount` increments on success, `skippedCount` on duplicate.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-MIG-033: **Automated Background Backup WorkManager Scheduling, Conditional Constraints, Status Diagnostics and Local UI Restore Verification** [WorkManager periodic backup uses ExistingPeriodicWorkPolicy.UPDATE with conditional UNMETERED network constraints; preference defaults to true; bounded local backup created via co-located atomic move; per-target statuses persisted; LastBackupInfo exposes target breakdown; automatic local restore triggers MigrationEngine; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-MIG-036`
- **Component**: **Automated Background Backup WorkManager Scheduling, Conditional Constraints, Status Diagnostics and Local UI Restore Verification**
- **Jira**: ``ATT-3472` / `ATT-4458``
- **Test Description**:
  1. *WorkManager Scheduling & Constraint Tests (`BackupWorkerTest.kt`)*:
  • Verify `BackupWorker.Companion.schedule` enqueues unique periodic work using `ExistingPeriodicWorkPolicy.UPDATE` for `"automated_backup_work"` (preventing 24h reset storms on cold starts).
  • Verify dynamic network constraints: `NetworkType.NOT_REQUIRED` built when no cloud provider is enabled; `NetworkType.UNMETERED` built when Dropbox or Google Drive is enabled.
  • Verify `schedule` catches and logs exceptions without crashing when WorkManager is unavailable.
  2. *Preference Fallback Default Unit Tests (`BackupWorkerTest.kt`)*:
  • Verify `doWork()` defaults `"automated_backups"` to `true` when unset in SharedPreferences (matching `schedule()` and `prefs_dropbox.xml`).
  3. *Bounded Local Backup Creation & Target Persistence Tests (`BackupWorkerTest.kt`)*:
  • Verify `doWork()` calls `BackupManager.createBackup(context)` and returns `Result.success()` with `last_backup_status = "LOCAL_SUCCESS"` and `last_backup_targets = "local:SUCCESS"` when no cloud provider is connected.
  • Verify `PARTIAL_SUCCESS` and upload failures record per-target outcomes (`local:SUCCESS;dropbox:FAILED;...`) in SharedPreferences.
  • Verify local backup creation is bounded to $O(1)$ disk space via canonical single-file maintenance.
  4. *Co-Located Atomic Move & Stale Temp Cleanup (`BackupManagerTest.kt`)*:
  • Verify staging is written to `cacheDir/aTrainingTracker_backup.tmp` and moved to `aTrainingTracker_backup.attbackup` using `StandardCopyOption.ATOMIC_MOVE`.
  • Verify stale `.tmp` files from previous crashes are deleted before bundling.
  5. *Concurrency Synchronization & Lock Contention Tests (`BackupWorkerTest.kt`)*:
  • Verify lock contention with concurrent manual backup causes worker to yield and return `Result.retry()` with backoff.
  6. *ViewModel Diagnostics & Automatic Local Restore Tests (`BackupRestoreViewModelTest.kt`)*:
  • Verify `LastBackupInfo` parses `last_backup_targets` into a structured map `targets: Map<String, Boolean>`.
  • Verify `getAutomaticBackupTimestamp(context)` returns `file.lastModified()` when `aTrainingTracker_backup.attbackup` exists, and null when absent.
  • Verify `restoreFromLocalBackup(context)` triggers `MigrationEngine.performFullRestore` on the local rolling backup file and propagates progress/completion into `_uiState`.
  7. *Decoupled Cloud Upload & Controlled Retry Tests (`BackupWorkerTest.kt`)*:
  • Verify failed cloud upload returns `Result.retry()` with exponential backoff while preserving local backup.
  8. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `last_backup_local_success`, `last_backup_partial_success`, `last_backup_failed_format`, `restore_automatic_local_backup`, `restore_automatic_local_backup_format`, `restore_automatic_backup_not_found`, `restore_from_file`, `restore_from_dropbox`, `backup_target_local`, `backup_target_dropbox`, `backup_target_google_drive` across EN, DE, ES, FR, IT, JA, NL, PL, PT with 100% translation parity and indexed positional specifiers.
  9. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-MIG-036: **Unified Provider-Aware Manual Cloud Backup, Concurrency Lock & Status Parity, and Dynamic Subtitle Verification Suite** [Dynamic manual cloud backup button label and visual state adapt across 4 provider states; no-cloud prompt dialog opens when neither connected; uploadToCloud acquires BackupManager lock and persists status/targets with worker parity; automated backups subtitle dynamically reflects active targets; multi-line label formats with centered alignment and newline; 9-language translation parity verified.]
- **Requirement(s)**: `REQ-MIG-036`
- **Component**: **Unified Provider-Aware Manual Cloud Backup, Concurrency Lock & Status Parity, and Dynamic Subtitle Verification Suite**
- **Jira**: ``ATT-4518` / `ATT-4587``
- **Test Description**:
  1. *Provider-Aware Manual Cloud Backup Label & State Tests (`BackupTabContentTest.kt`)*:
  • Verify button label displays `create_and_upload_to_both` when both Dropbox and Google Drive are enabled.
  • Verify button label displays `create_and_upload_to_both` with centered text alignment (`textAlign = TextAlign.Center`) when both Dropbox and Google Drive are enabled [ATT-4587].
  • Verify button label displays `create_and_upload_to_dropbox` when only Dropbox is enabled.
  • Verify button label displays `create_and_upload_to_google_drive` when only Google Drive is enabled.
  • Verify button label displays `create_and_upload_to_cloud` with muted colors (surfaceVariant, 38% alpha) when neither provider is enabled.
  • Verify tapping button when neither provider is enabled triggers informational dialog (`cloud_backup_no_provider_dialog_message`).
  2. *ViewModel uploadToCloud Concurrency & Parity Tests (`BackupRestoreViewModelCloudBackupTest.kt`)*:
  • Verify `uploadToCloud()` acquires `BackupManager.acquireLock()` before creating backup.
  • Verify lock contention with concurrent operation aborts/yields safely.
  • Verify `uploadToCloud()` uploads to all enabled providers (Dropbox, Google Drive, or both) and updates `last_backup_timestamp`, `last_backup_status`, and `last_backup_targets` in SharedPreferences matching `BackupWorker` schema.
  3. *Dynamic Automated Backup Subtitle Tests (`BackupTabContentTest.kt`)*:
  • Verify subtitle displays `automated_backups_summary_both` when both cloud providers are connected.
  • Verify subtitle displays `automated_backups_summary` when only Dropbox is connected.
  • Verify subtitle displays `automated_backups_summary_google_drive` when only Google Drive is connected.
  • Verify subtitle displays `automated_backups_summary_local_only` when neither cloud provider is connected.
  4. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify `create_and_upload_to_both`, `create_and_upload_to_google_drive`, `create_and_upload_to_cloud`, `cloud_backup_no_provider_dialog_title`, `cloud_backup_no_provider_dialog_message`, `automated_backups_summary_both`, `automated_backups_summary_google_drive`, `automated_backups_summary_local_only` across EN, DE, ES, FR, IT, JA, NL, PL, PT with 100% translation parity.
  • Verify `create_and_upload_to_both` contains an explicit literal newline preceding `(Dropbox & Google Drive)` across all 9 locales with 100% translation parity [ATT-4587].
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-DAT-001: **Automated Altitude Discovery** [The database SHALL contain an entry for Location A with an average altitude of 105m and a hit count of 2.]
- **Requirement(s)**: `REQ-DAT-007`
- **Component**: **Automated Altitude Discovery**
- **Jira**: ``ATT-423``
- **Test Description**: 1. Record/Import a workout starting at Location A (Alt=100m). 2. Record/Import another workout starting at Location A (Alt=110m). 3. Verify the 'Known Locations' database entry for Location A.

### TST-DAT-002: **Precise Altitude Learning** [Location A MUST be learned as 200m. It MUST NOT be 100m (the session minimum).]
- **Requirement(s)**: `REQ-DAT-007`
- **Component**: **Precise Altitude Learning**
- **Jira**: ``ATT-482``
- **Test Description**: 1. Start workout at Location A (200m). 2. Descend to 100m. 3. Stop workout. 4. Inspect 'Known Locations' DB.

### TST-EXT-001: **Strava Custom Tabs** [The authorization page SHALL open in a Chrome Custom Tab.]
- **Requirement(s)**: `REQ-EXT-004`
- **Component**: **Strava Custom Tabs**
- **Test Description**: 1. Click "Connect to Strava". 2. Verify that a Custom Tab opens.

### TST-EXT-002: **Strava OAuth Flow** [The app SHALL show a non-blocking loading indicator and correctly store the resulting token.]
- **Requirement(s)**: `REQ-EXT-005`
- **Component**: **Strava OAuth Flow**
- **Test Description**: 1. Complete Strava authorization. 2. Observe the loading state.

### TST-EXT-003: **Strava Profile Scope & Equipment Synchronization Flow** [Gear items from Strava athlete payload are parsed and stored accurately into `EquipmentDbHelper` with correct sport and frame types without duplication.]
- **Requirement(s)**: `REQ-EXT-006`
- **Component**: **Strava Profile Scope & Equipment Synchronization Flow**
- **Jira**: ``ATT-588``
- **Test Description**: 1. Verify `StravaHelper.getAuthorizationUrl()` includes `profile:read_all` in the OAuth scope. 2. Provide simulated Strava `/athlete` responses with shoes and bikes. 3. Execute synchronization. 4. Verify equipment is populated in `EquipmentDbHelper` and unlinked duplicate records are avoided. 5. Verify null access token handling.

### TST-EXT-004: **Strava Route Synchronization & Connection Trigger Flow** [Routes from Strava are synchronized automatically on connection and manually on request, stored with detailed geometry in `RoutesDatabaseManager`, update sync timestamp, and seed into `WorkoutClusterEngine`.]
- **Requirement(s)**: `REQ-EXT-007`
- **Component**: **Strava Route Synchronization & Connection Trigger Flow**
- **Jira**: ``ATT-497` (`ATT-607`)`
- **Test Description**: 1. Simulate Strava connection success (`StravaAuthState.Success`) in `StravaUploadFragment` and verify route synchronization is initiated. 2. Trigger on-demand sync via `updateStravaRoutes` in Strava settings. 3. Provide mock Strava routes payload (`GET /athletes/{id}/routes`) and stream responses (`GET /routes/{id}/streams`). 4. Verify routes are parsed, streams are converted to `PathPoint`s, and records are stored in `RoutesDatabaseManager` with `RouteSource.STRAVA`. 5. Verify timestamp updates in `TrainingApplication`. 6. Verify missing token/athlete ID handling.

### TST-EXT-005: **Automated Strava Upload for Imported TCX Workouts** [Successfully imported TCX workouts automatically schedule a Strava upload job via ExportManager when Strava integration is active.]
- **Requirement(s)**: `REQ-EXT-008`
- **Component**: **Automated Strava Upload for Imported TCX Workouts**
- **Jira**: ``ATT-602``
- **Test Description**: 1. Configure Strava upload as enabled (`TrainingApplication.uploadToCommunity(FileFormat.STRAVA)` returns true). 2. Import a valid TCX workout file via `LegacyImportEngine.importFromTcx()`. 3. Verify that an asynchronous upload task is scheduled with `ExportManager`. 4. Verify that `ExportStatusDatabaseManager` records the pending export status for the imported workout. 5. Verify that when Strava upload is disabled, no upload is scheduled. 6. Verify that local database records and statistics remain intact regardless of network status.

### TST-EXT-006: **Strava Data Purge on Deauthorization & Revocation, and Reauthorization Integrity Flow** [All Strava-originated data (credentials, activity details, segments, routes, gear IDs) is thoroughly purged without affecting native workouts or hardware sensor pairings; reauthorization smoothly re-links existing equipment by name without duplicates; interactive disconnect prompts user with clear consequences before executing purge.]
- **Requirement(s)**: `REQ-EXT-009`
- **Component**: **Strava Data Purge on Deauthorization & Revocation, and Reauthorization Integrity Flow**
- **Jira**: ``ATT-1078``
- **Test Description**: 1. Populate mock Strava tokens, athlete ID, and upload preferences in `SharedPreferences`. 2. Insert mock Strava activity JSON and Strava ID in `StravaUploadDbHelper`. 3. Insert mock starred segments in `SegmentsDatabaseManager`. 4. Insert mock Strava route in `RoutesDatabaseManager`. 5. Insert mock equipment in `EquipmentDbHelper` with `StravaId` and `StravaName`, linked to an ANT+ sensor in `LINKS`. 6. Insert mock native workout in `WorkoutSummariesDatabaseManager` with samples and laps. 7. Execute data purge (simulating user disconnect or refresh token rejection). 8. Verify: tokens and athlete ID removed from `SharedPreferences`; `StravaUploadDbHelper` contains zero Strava activity data or Strava IDs; `SegmentsDatabaseManager` contains zero starred segments; `RoutesDatabaseManager` contains zero Strava routes; `EquipmentDbHelper` equipment row still exists with preserved `_id`, `Name`, and sensor links, but `StravaId` and `StravaName` are `NULL`; native workouts in `WorkoutSummariesDatabaseManager`, samples, and laps remain 100% intact; `WorkManager` has canceled sync workers. 9. Simulate reauthorization: set new mock token, execute `StravaEquipmentSynchronizeThread` with matching bike/shoe names, verify equipment row has `StravaId` re-attached without creating new duplicate rows. 10. In `StravaSettingsDialog`, verify tapping 'Disconnect' triggers the confirmation dialog detailing consequences; verify tapping 'Cancel' dismisses without side effects; verify tapping 'Disconnect' executes deauthorization and purge.

### TST-EXT-007: **Strava Route & Segment 7-Day TTL Cache Retention, Orphan Pruning & Route Preservation Flow** [Strava routes and segments older than 7 days are automatically evicted; orphaned Strava routes and segments are pruned during sync; local GPX and workout routes never expire; duplicated local routes survive TTL pruning; native workouts and hardware pairings remain 100% intact.]
- **Requirement(s)**: `REQ-EXT-010`
- **Component**: **Strava Route & Segment 7-Day TTL Cache Retention, Orphan Pruning & Route Preservation Flow**
- **Jira**: ``ATT-1177``
- **Test Description**: 1. Insert mock Strava route (`RouteSource.STRAVA`) with `synced_at = now - 8 days` and another with `synced_at = now - 1 day` in `RoutesDatabaseManager`. 2. Insert mock local route (`RouteSource.LOCAL_GPX`) with `synced_at = 0` / legacy timestamp. 3. Insert mock starred segment and streams with `synced_at = now - 8 days` and another with `synced_at = now - 2 days` in `SegmentsDatabaseManager`. 4. Execute TTL pruning routine. 5. Verify: expired Strava route (> 7 days) is deleted; fresh Strava route (< 7 days) is retained; expired starred segment and its coordinate streams are deleted; fresh segment is retained; local GPX route is completely untouched. 6. Simulate remote sync with orphan detection: populate active Strava route IDs set excluding a currently cached fresh Strava route; execute orphan pruning; verify missing route is deleted. 7. Test route preservation: take an existing Strava route, invoke `duplicateRouteAsLocal`, verify new route is created with `RouteSource.LOCAL_GPX` and identical geometry/bounds; advance simulated time past 7 days and execute TTL pruning; verify duplicated local route survives while original Strava route is evicted. 8. Verify native workout databases (`WorkoutSummaries.db`, `WorkoutSamples.db`, `Laps.db`) and `Equipment.db` are completely untouched throughout all pruning operations.

### TST-EXT-008: **Strava Token Refresh Concurrency, Expiry Margin, Precise Revocation Discrimination & Header Verification Suite** [Protect local Strava data against accidental deletion during token refresh, synchronize concurrent refresh requests, enforce 300-second expiry margin, reuse OkHttpClient, and eliminate Bearer null headers verified across unit test suite.]
- **Requirement(s)**: `REQ-EXT-011`
- **Component**: **Strava Token Refresh Concurrency, Expiry Margin, Precise Revocation Discrimination & Header Verification Suite**
- **Jira**: ``ATT-3996``
- **Test Description**:
  1. *Invalid Client Secret Never Purges Data (`TST-EXT-008.1`, `StravaHelperTest.kt`)*:
  • Mock Strava `/oauth/token` endpoint returning HTTP 400 with `client_secret` invalid JSON.
  • Call `StravaHelper.getRefreshedAccessToken()`.
  • Verify result is `null` and `StravaDataPurgeManager.purgeAllStravaData` is NEVER called.
  2. *True Refresh Token Revocation Triggers Data Purge (`TST-EXT-008.2`, `StravaHelperTest.kt`)*:
  • Mock Strava `/oauth/token` returning HTTP 400 with `refresh_token` invalid JSON.
  • Call `StravaHelper.getRefreshedAccessToken()`.
  • Verify result is `null` and `StravaDataPurgeManager.purgeAllStravaData` is invoked.
  3. *Concurrent Token Refresh Serialized & Deduplicated (`TST-EXT-008.3`, `StravaHelperTest.kt`)*:
  • Set token as expired.
  • Launch 10 concurrent threads invoking `StravaHelper.getRefreshedAccessToken()`.
  • Verify exactly 1 network HTTP POST request is executed, and all 10 threads receive the valid token without starvation or ANR.
  4. *5-Minute (300s) Expiry Margin Refreshes Eagerly (`TST-EXT-008.4`, `StravaHelperTest.kt`)*:
  • Set token expiring in 200s (within 300s margin).
  • Call `StravaHelper.getRefreshedAccessToken()`.
  • Verify refresh request is executed.
  5. *Valid Unexpired Token Returns Immediately Without Network (`TST-EXT-008.5`, `StravaHelperTest.kt`)*:
  • Set token expiring in 3600s.
  • Call `StravaHelper.getRefreshedAccessToken()`.
  • Verify cached token is returned and zero network calls are made.
  6. *StravaUploader Aborts Cleanly on Null Token & Avoids Bearer null (`TST-EXT-008.6`, `StravaUploaderTest.kt`)*:
  • Mock `StravaHelper.getRefreshedAccessToken()` returning `null`.
  • Call `updateStravaActivity`, `getStravaJson`, `getStravaJsonArray`.
  • Verify all return `null` immediately and no HTTP request is dispatched with `Authorization: Bearer null`.
  7. *Non-JSON / Malformed Response Defaults to DO NOT PURGE (`TST-EXT-008.7`, `StravaHelperTest.kt`)*:
  • Mock Strava `/oauth/token` returning HTTP 400/502 with HTML content `<html><body>Cloudflare Error</body></html>`.
  • Call `StravaHelper.getRefreshedAccessToken()`.
  • Verify result is `null` and `StravaDataPurgeManager.purgeAllStravaData` is NEVER called.
  8. *Network Timeout Handling Cleanly Returns Null (`TST-EXT-008.8`, `StravaHelperTest.kt`)*:
  • Mock `httpClient` throwing `SocketTimeoutException`.
  • Call `StravaHelper.getRefreshedAccessToken()`.
  • Verify result is `null` and no exceptions escape or purge data.
  9. *Clean-Room Regression Suite Execution (`TST-EXT-008.9`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-EXT-009: **Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, and Legacy Callback Deletion Verification Suite** [Cryptographically random OAuth state generation, 10-minute expiration, constant-time validation with single-use consumption, callback CSRF rejection, structured repository scope exchange, and legacy callback deletion verified across unit test suite.]
- **Requirement(s)**: `REQ-EXT-012`
- **Component**: **Strava OAuth 2.0 State Parameter CSRF Protection, Structured Repository Scope Exchange, and Legacy Callback Deletion Verification Suite**
- **Jira**: ``ATT-4022``
- **Test Description**:
  1. *OAuth State Cryptographic Generation Test (`TST-EXT-009.1`, `StravaOAuthStateManagerTest.kt`)*:
  • Call `StravaOAuthStateManager.generateAndSaveState()`.
  • Verify state is a 32-character hexadecimal string, cryptographically random, and persisted in preferences with timestamp.
  2. *Authorization URL State Inclusion Test (`TST-EXT-009.2`, `StravaHelperTest.kt`)*:
  • Call `StravaHelper.getAuthorizationUrl(state)` with test state.
  • Assert `state` query parameter is present and matches the supplied state.
  3. *Valid State Validation & Consumption Test (`TST-EXT-009.3`, `StravaOAuthStateManagerTest.kt`)*:
  • Save state token.
  • Assert `validateAndConsumeState(token)` returns `true`, and immediate retry returns `false` (single-use).
  4. *Mismatched State Rejection Test (`TST-EXT-009.4`, `StravaOAuthStateManagerTest.kt`)*:
  • Save state token A.
  • Assert `validateAndConsumeState("token_B")` returns `false` and clears storage.
  5. *Null / Missing State Rejection Test (`TST-EXT-009.5`, `StravaOAuthStateManagerTest.kt`)*:
  • Save state token.
  • Assert `validateAndConsumeState(null)` and `validateAndConsumeState("")` return `false`.
  6. *Expired State (> 10m) Rejection Test (`TST-EXT-009.6`, `StravaOAuthStateManagerTest.kt`)*:
  • Save state with timestamp $t_{\text{now}} - 601\text{s}$.
  • Assert `validateAndConsumeState(token)` returns `false` due to expiration.
  7. *Uninitiated Flow Rejection Test (`TST-EXT-009.7`, `StravaOAuthStateManagerTest.kt`)*:
  • Ensure no state in storage; assert `validateAndConsumeState("random")` returns `false`.
  8. *Callback Activity Missing State Rejects Exchange Test (`TST-EXT-009.8`, `StravaOAuthCallbackActivityTest.kt`)*:
  • Invoke `StravaOAuthCallbackActivity` with URI `strava://rainerblind.github.io?code=attack_code` (no state).
  • Verify auth state transitions to `StravaAuthState.Error` and zero token exchange is executed.
  9. *Callback Activity Mismatched State Rejects Exchange Test (`TST-EXT-009.9`, `StravaOAuthCallbackActivityTest.kt`)*:
  • Save state A; invoke callback activity with URI `strava://rainerblind.github.io?state=state_B&code=attack_code`.
  • Verify rejection: `StravaAuthState.Error` and zero token exchange executed.
  10. *Callback Activity Valid State Dispatches Repository Scope Exchange Test (`TST-EXT-009.10`, `StravaOAuthCallbackActivityTest.kt`)*:
  • Save state A; invoke callback activity with URI `strava://rainerblind.github.io?state=state_A&code=valid_code`.
  • Verify state consumed, `exchangeCodeForTokenAsync(valid_code)` dispatched in repository scope without `GlobalScope`.
  11. *Callback Activity User Cancellation Error Handling Test (`TST-EXT-009.11`, `StravaOAuthCallbackActivityTest.kt`)*:
  • Invoke callback activity with URI `strava://rainerblind.github.io?error=access_denied`.
  • Verify pending state cleared, error handled cleanly, activity finishes.
  12. *Legacy Class Deletion Contract Test (`TST-EXT-009.12`)*:
  • Verify `BaseOAuthCallbackActivity.java` is absent from repo and project compiles cleanly.
  13. *Clean-Room Regression Suite Execution (`TST-EXT-009.13`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-EXT-012: **Strava OAuth Callback Application ID Scheme Scoping & Intent Disambiguation Test Suite** [Strava OAuth redirect URI scheme scoping by applicationId eliminates system app chooser dialogs on co-installed release, debug, and legacy builds, rejects mismatched variant callbacks, and preserves single-variant authentication and CSRF state validation.]
- **Requirement(s)**: `REQ-EXT-012`
- **Component**: **Strava OAuth Callback Application ID Scheme Scoping & Intent Disambiguation Test Suite**
- **Jira**: ``ATT-4668, ATT-4706``
- **Test Description**:
  1. *Authorization URL Scheme Scoping Test (`TST-EXT-012.1`, `StravaHelperTest.kt`, `StravaOAuthSecurityTest.kt`)*:
  • Call `StravaHelper.getAuthorizationUrl(state)`.
  • Verify `redirect_uri` parameter contains `strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io`.
  2. *Callback Activity Application ID Scheme Match Test (`TST-EXT-012.2`, `StravaOAuthCallbackActivityTest.kt`, `StravaOAuthSecurityTest.kt`)*:
  • Invoke `StravaOAuthCallbackActivity` with URI `strava.${BuildConfig.APPLICATION_ID}://rainerblind.github.io?state=$validState&code=$validCode`.
  • Verify state is consumed and token exchange is dispatched in repository scope.
  3. *Callback Activity Mismatched Application ID Scheme Rejection Test (`TST-EXT-012.3`, `StravaOAuthCallbackActivityTest.kt`, `StravaOAuthSecurityTest.kt`)*:
  • Invoke `StravaOAuthCallbackActivity` with URI targeting opposite variant (e.g. `strava.com.other.variant://rainerblind.github.io?state=$validState&code=$validCode`).
  • Verify callback is rejected/ignored and activity finishes without consuming state or triggering token exchange.
  4. *Callback Activity Legacy Un-Scoped Scheme Rejection Test (`TST-EXT-012.4`, `StravaOAuthCallbackActivityTest.kt`, `StravaOAuthSecurityTest.kt`)*:
  • Invoke `StravaOAuthCallbackActivity` with legacy un-scoped URI `strava://rainerblind.github.io?state=$validState&code=$validCode`.
  • Verify callback is rejected/ignored and activity finishes without consuming state or triggering token exchange.
  5. *Manifest Intent Filter Scheme Scoping Contract Test (`TST-EXT-012.5`, `StravaOAuthSecurityTest.kt`)*:
  • Verify `AndroidManifest.xml` intent filter for `StravaOAuthCallbackActivity` declares `<data android:scheme="strava.${applicationId}" android:host="rainerblind.github.io" />` without `android:path`.
  6. *Clean-Room Regression Suite (`TST-EXT-012.6`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-DAT-003: **Authoritative Altitude Extrema Reconciliation & Self-Healing Verification** [Corrupted legacy extrema (both rogue spikes and extrema falling inside stream bounds) are automatically healed from the authoritative stream, satisfying `REQ-DAT-009` and restoring consistency to both summary table and chart.]
- **Requirement(s)**: `REQ-DAT-009`
- **Component**: **Authoritative Altitude Extrema Reconciliation & Self-Healing Verification**
- **Jira**: ``ATT-508``
- **Test Description**:
  1. Construct a workout summary record with decoded altitude stream (e.g., [0.0, 1.0, 2.0, 1.5, 0.5]) and corrupted SQLite extrema (Min = -63.0, Max = 60.0).
  2. Construct a workout summary where stored min is higher than stream min (e.g. stored min = 368.0, stream min = 360.0); verify `reconcileAltitudeExtrema` reconciles min to 360.0 and updates SQLite.
  3. Construct a workout summary where stored max is lower than stream max (e.g. stored max = 375.0, stream max = 385.0); verify `reconcileAltitudeExtrema` reconciles max to 385.0 and updates SQLite.
  4. Verify legitimate workouts matching their stream are not altered.
  5. Execute unit tests in `WorkoutDataMapperAltitudeTest.kt`.

### TST-DAT-004: **Bulk Workout Deletion & Relational Storage Purge Verification** [Sessions older than threshold are fully purged across all databases without leaving orphaned sample tables; surviving sessions and cluster hit counts are preserved.]
- **Requirement(s)**: `REQ-DAT-010`
- **Component**: **Bulk Workout Deletion & Relational Storage Purge Verification**
- **Jira**: ``ATT-296``
- **Test Description**:
  1. Insert mock workouts into SQLite: Workout A (400 days old), Workout B (370 days old), and Workout C (30 days old) with associated laps, extrema, and sample tables (`workout_samples_<fileBaseName>`).
  2. Execute `WorkoutDeletionHelper.deleteOldWorkouts(daysToKeep = 365, callback)`.
  3. Verify `getBaseFileName` is queried prior to summary deletion so `workout_samples_<fileBaseName>` tables for Workouts A and B are dropped.
  4. Verify all rows in `WorkoutSummaries`, `accumulated_sensors`, `extrema_values`, and `laps` for Workouts A and B are completely deleted.
  5. Verify Workout C (30 days old) is completely preserved with its sample table intact.
  6. Verify progress callback is triggered for each deleted workout ID.
  7. Verify `WorkoutRepository.deleteOldWorkouts` invokes `PeriodsRepository.resyncAllPeriods()` and `WorkoutClusterRepository.refreshClusters(forceCheckIntegrity = true)` post-deletion.
  8. Verify surviving workout clusters keep their lifetime `hitCount` untouched, while unlinked zero-workout orphan clusters are purged.
  9. Execute automated unit tests in `WorkoutDeletionHelperTest.kt` and `WorkoutClusterRepositoryTest.kt`.

### TST-DAT-005: **Period Cache Synchronization on Bulk Deletion Verification** [Period cache completely purges orphaned historical periods, recomputes boundary periods, and eliminates dangling foreign keys upon bulk deletion.]
- **Requirement(s)**: `REQ-DAT-011`
- **Component**: **Period Cache Synchronization on Bulk Deletion Verification**
- **Jira**: ``ATT-296``
- **Test Description**:
  1. Populate `WorkoutSummaries` and pre-populate `PeriodSummaries.db` with periods spanning beyond 365 days.
  2. Execute `WorkoutRepository.deleteOldWorkouts(daysToKeep = 365)`.
  3. Verify `PeriodsRepository.resyncAllPeriods()` is executed.
  4. Verify `PeriodSummaries.db` contains 0 records for periods whose underlying workouts were completely purged.
  5. Verify boundary periods have updated metrics matching surviving workouts.
  6. Verify all `longest_workout_id` values point to surviving workouts or are `-1`.
  7. Execute automated unit tests.

### TST-DAT-006: **TCX Lap Serialization, Parsing, Round-Trip & Escaping Verification** [TCX files serialize lap name and description in both human-readable `<Notes>` and structured `<Extensions>`; TCX import accurately parses both formats and populates `Laps.db`; full round-trip preservation is verified with zero regressions.]
- **Requirement(s)**: `REQ-DAT-012`
- **Component**: **TCX Lap Serialization, Parsing, Round-Trip & Escaping Verification**
- **Jira**: ``ATT-892`, `ATT-915`, `ATT-921``
- **Test Description**:
  1. *TCX Export Formatting & Schema Conformance Unit Test (`TCXFileWriterLapTest.kt`)*:
  • Create a mock workout with annotated laps (Lap 1: Name="Warm-up", Desc="10 min easy"; Lap 2: Name="Interval & Hill", Desc="Zone 4 <hard>"; workout description="Morning ride").
  • Execute `TCXFileWriter.doExport(exportInfo)`.
  • Verify generated XML conforms to `TrainingCenterDatabasev2.xsd` with `<Notes>` and `<Extensions>` strictly placed after `</Track>` and before `</Lap>`.
  • Verify Lap 1 contains `<Notes>[Warm-up] 10 min easy</Notes>`.
  • Verify Lap 2 contains XML-escaped `<Notes>[Interval &amp; Hill] Zone 4 &lt;hard&gt;</Notes>`.
  • Verify structured `<Extensions>` contains `<att:LapExtension>` with `<att:Name>` and `<att:Description>` verbatim.
  • Verify `<Activity>` contains `<Notes>Morning ride</Notes>` before `<Creator>`.
  2. *TCX Import Parsing Unit Test (`LegacyImportEngineLapTest.kt`)*:
  • Verify bracket format parsing: `<Notes>[Recovery] Easy spin</Notes>` extracts `name = "Recovery"`, `description = "Easy spin"`.
  • Verify name-only format: `<Notes>[Warm-up]</Notes>` extracts `name = "Warm-up"`, `description = null`.
  • Verify multiline format: extracts line 1 as name and remaining lines as description.
  • Verify structured extension extraction: `<att:Name>` and `<att:Description>` directly populate `ParsedLap`.
  • Verify XML escaping round-trip: characters like `&`, `<`, `>`, `"`, `'` are restored identically.
  • Verify legacy file compatibility: TCX without lap notes imports safely with null name and description.
  3. *Database Insertion & Round-Trip Verification*:
  • Export a workout with custom laps and re-import it via `LegacyImportEngine.importFromTcx`.
  • Query `LapsDatabaseManager.getLaps(importedWorkoutId)` and verify exact equality of lap names and descriptions.
  4. *Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-DAT-007: **TCX Workout Name & Description Bidirectional Export, Import, Round-Trip & Cluster Protection Verification** [TCX files serialize workout name and description in `<Notes>` and `<att:ActivityExtension>`; TCX import restores `WORKOUT_NAME` and `DESCRIPTION`; custom names are immune to cluster overwrites; zero test regressions.]
- **Requirement(s)**: `REQ-DAT-013`
- **Component**: **TCX Workout Name & Description Bidirectional Export, Import, Round-Trip & Cluster Protection Verification**
- **Jira**: ``ATT-922`, `ATT-924``
- **Test Description**:
  1. *TCX Export Serialization Unit Test (`TCXFileWriterWorkoutNameTest.kt`)*:
  • Create mock workout with custom name (`"Tempo Run & Hills"`), description (`"Zone 3-4 <hard>"`), and laps.
  • Verify `BaseFileWriter.getHeaderData()` populates `workoutName` from `WorkoutSummaries.WORKOUT_NAME`.
  • Execute `TCXFileWriter.doExport(exportInfo)`.
  • Verify XML schema compliance: `<Activity Sport="...">` contains `<Notes>[Tempo Run &amp; Hills] Zone 3-4 &lt;hard&gt;</Notes>` before `<Creator>`.
  • Verify structured `<Extensions>` contains `<att:ActivityExtension>` with `<att:Name>Tempo Run &amp; Hills</att:Name>` and `<att:Description>Zone 3-4 &lt;hard&gt;</att:Description>`.
  • Verify export with name only (`[Name]`) and description only (`Description`).
  2. *TCX Import Deserialization Unit Test (`LegacyImportEngineWorkoutNameTest.kt`)*:
  • Parse TCX with `<att:ActivityExtension>`: verify `workoutName` and `description` are correctly extracted.
  • Parse TCX with standard bracket `<Notes>[Morning Ride] Easy pace</Notes>`: verify name="Morning Ride", desc="Easy pace".
  • Parse TCX with `<Training><Plan><Name>Speed Session</Name></Training>`: verify name="Speed Session".
  • Verify XML character unescaping (`&`, `<`, `>`, `"`, `'`).
  • Verify legacy TCX without workout name gracefully falls back to `fileBaseName`.
  3. *Round-Trip & Cluster Overwrite Immunity Test*:
  • Export workout with custom name `"Interval Session"`.
  • Import exported TCX file via `LegacyImportEngine`.
  • Verify `WorkoutSummaries.WORKOUT_NAME` equals `"Interval Session"`.
  • Run `WorkoutClusterEngine.assignClusterToWorkout()` on the imported workout: verify `WORKOUT_NAME` remains `"Interval Session"` and is NOT overwritten by the cluster name.
  4. *Clean-Room Regression Verification*:
  • Execute full unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-DAT-008: **Internet Digital Elevation Model (DEM) Reference Altitude Retrieval, Schema V5, Healing & Resilience Test** [Topographic elevation retrieval, 5-decimal quantization, 200m spatial caching, HTTP error handling (429, 503, timeout), offline GPS fallback, database V4->V5 atomic migration, locked location immutability, and legacy batch healing are 100% verified with 0 regressions.]
- **Requirement(s)**: `REQ-DAT-014`
- **Component**: **Internet Digital Elevation Model (DEM) Reference Altitude Retrieval, Schema V5, Healing & Resilience Test**
- **Jira**: ``ATT-1278``
- **Test Description**:
  1. *Nominal DEM API Retrieval Unit Test (`ElevationServiceTest.kt`)*:
  • Mock Open-Meteo 200 OK response with `{"elevation": [312.5]}`.
  • Invoke `ElevationService.fetchElevation(48.13715, 11.57612)`.
  • Verify returned elevation is 312.5m, coordinates are quantized to 5 decimal places, and result encapsulates `ElevationResult.Success`.
  2. *Spatial Geofence Caching & Quantization Test (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Insert a location at coordinate $C_1$ with radius 200m and altitude 312.5m.
  • Query `getMyLocation` with coordinate $C_2$ located 50m away ($< 200\text{m}$).
  • Verify cache hit returns the existing location with 312.5m.
  • Verify that coordinates with floating-point jitter ($52.5200001$ vs $52.5200004$) quantize to identical 5-decimal representations.
  3. *HTTP Rate-Limit (429) & Backoff Test (`ElevationServiceTest.kt`)*:
  • Mock 429 Too Many Requests response.
  • Verify `ElevationService` handles error cleanly, returns `ElevationResult.RateLimited`, and activates exponential backoff.
  4. *Server Error (503/5xx) & Circuit Breaker Test (`ElevationServiceTest.kt`)*:
  • Mock 503 Service Unavailable response.
  • Verify `ElevationService` returns `ElevationResult.ServerError`, trips the circuit breaker for 5 minutes, and prevents subsequent outbound queries during cooldown.
  5. *Timeout & Offline Fallback Test (`ElevationServiceTest.kt`)*:
  • Simulate socket timeout exceeding 5 seconds / IOException.
  • Verify `ElevationResult.NetworkError` is returned and `AltitudeFromPressureDevice` falls back to GPS altitude with `source = GPS_FALLBACK`.
  6. *Database Schema V4 to V5 Atomic Upgrade Test (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Create a V4 database table with legacy schema.
  • Trigger `onUpgrade(db, 4, 5)`.
  • Verify `is_locked` (default 0) and `source` (default 'LEGACY_RAW') columns are added without data loss or table recreation.
  • Verify adherence to `REQ-DAT-008` (framework automatic transaction wrapper).
  7. *Locked Location Immutability Test (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Set `is_locked = 1` on an existing location.
  • Invoke `learnLocation` with a differing altitude.
  • Verify the stored altitude and hitCount remain strictly unchanged.
  8. *Legacy Location Batch Healing Test (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Trigger batch healing for unlocked `LEGACY_RAW` locations.
  • Verify Open-Meteo multi-coordinate query returns DEM elevations, updates SQLite rows to `source = INTERNET_DEM`, and leaves locked locations untouched.
  9. *Clean-Room Regression Suite*:
  • Execute full unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-DAT-009: **Stable Reference Altitude Preservation, Hit Count Tracking & Concurrency Test** [Reference altitude is strictly preserved without moving average corruption; repeat visits increment `hitCount`; locked records remain immutable; concurrent operations are serialized atomically via SQLite transactions; 0 regressions.]
- **Requirement(s)**: `REQ-DAT-007`
- **Component**: **Stable Reference Altitude Preservation, Hit Count Tracking & Concurrency Test**
- **Jira**: ``ATT-1366``
- **Test Description**:
  1. *Zero Altitude Mutation Unit Test (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Insert an existing location with reference altitude 520.0m, `hitCount = 1`, and `source = INTERNET_DEM`.
  • Invoke `learnLocation(pos, 480.0, ExtremaType.START)`.
  • Verify that the stored altitude remains strictly 520.0m and `hitCount` increments to 2.
  2. *Locked Location Immutability Test (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Verify that for locked locations (`is_locked = 1`), `learnLocation()` strictly preserves altitude and metadata.
  3. *Multi-Threaded Concurrency Test (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Concurrently trigger `learnLocation()` and `upsertLocationByGeofence()` across multiple worker threads using `CountDownLatch`.
  • Verify that SQLite transaction boundaries prevent TOCTOU race conditions and guarantee exactly one spatial record exists without duplicate rows.
  4. *Sensor Initialization Race Elimination Test (`AltitudeFromPressureDeviceTest.kt`)*:
  • Trigger `initPressureSensor()` with `myLocation == null`.
  • Verify `fetchDemOrFallbackAsync()` is queued without synchronously calling `learnLocation()` to insert an uncalibrated `AUTO_LEARNED` entry.
  5. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` to ensure zero regressions across all modules.

### TST-DAT-010: **Decoupled Altimeter Calibration and Authoritative Workout Start Counting Verification** [Altimeter calibration executes as a pure read-only operation during sensor warmup without mutating SQLite; workout starts are recorded atomically once per new session; pause/resume is neutral; existing hit counts are preserved; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-DAT-015`
- **Component**: **Decoupled Altimeter Calibration and Authoritative Workout Start Counting Verification**
- **Jira**: ``ATT-1447`, `ATT-1468`, `ATT-1469`, `ATT-1474``
- **Test Description**:
  1. *Sensor Warmup Read-Only Isolation Unit Test (`AltitudeFromPressureDeviceTest.kt`)*:
  • Configure mock `KnownLocationsDatabaseManager` with a known location.
  • Trigger `initPressureSensor()` with valid GPS coordinates and raw pressure.
  • Verify that `knownLocationsDb.getMyLocation(pos)` is invoked and `setAltitudeCorrection(refAlt)` is applied.
  • Verify that `knownLocationsDb.learnLocation(...)` is NEVER invoked (invocation count = 0).
  2. *Dedicated Workout Start Recording Unit Tests (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Existing location match: Insert location with `hitCount = 3`. Call `recordWorkoutStart(pos, alt)`. Verify `hitCount` increments to 4 and altitude/coordinates remain intact.
  • Locked location match: Insert location with `is_locked = 1, altitude = 520.0, hitCount = 2`. Call `recordWorkoutStart(pos, 480.0)`. Verify `hitCount` increments to 3 and stored altitude remains 520.0m.
  • Novel location discovery: Call `recordWorkoutStart(novelPos, alt)` for position $> 200\text{m}$ from all known locations. Verify a new row is created with `hitCount = 1`, geocoded name, and reference elevation.
  • Null coordinate resilience: Call `recordWorkoutStart(null, alt)`. Verify method returns cleanly without error.
  • Transaction serialization: Verify all database operations execute within SQLite transaction boundaries.
  3. *TrackerService Workout Start Lifecycle Unit Tests (`TrackerServiceWorkoutStartTest.kt`)*:
  • Normal workout start: Trigger `sampleAndWriteToDb()` with `mStartType == START_NORMAL` and valid GPS coordinates. Verify `recordWorkoutStart` is dispatched once on `mDbExecutor` and subsequent GPS samples do NOT dispatch additional calls.
  • Resume neutrality: Initialize workout with `RESUME_BY_USER` or `RESUME_SERVICE_RECREATION`. Trigger `sampleAndWriteToDb()` with GPS coordinates. Verify `recordWorkoutStart` is NEVER invoked.
  4. *On-Device Pixel 10 End-to-End Verification*:
  • Note the start count of the current location in "Lieblingsorte".
  • Force-stop and re-open the app multiple times without starting a workout; verify start count remains strictly unchanged.
  • Start a real workout session, wait for GPS fix, and end session; verify start count increments by exactly 1.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-DAT-011: **Authoritative Workout Start Count Self-Healing Reconciliation & Reactive Presentation Verification** [Start count on KnownLocationCard accurately reconciles with and reflects recorded workout starts in SQLite and reactive UI; DEM healing does not increment hit count; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-DAT-016`
- **Component**: **Authoritative Workout Start Count Self-Healing Reconciliation & Reactive Presentation Verification**
- **Jira**: ``ATT-1734`, `ATT-1780`, `ATT-1781``
- **Test Description**:
  1. *Database Self-Healing Reconciliation Unit Tests (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Reconcile bloated count: Insert location with historical `hitCount = 625` and provide 5 matching start locations within its 200m radius. Verify `hitCount` updates to 5.
  • Unmatched start locations: Verify coordinates $>200\text{m}$ do not increment or match the location.
  • DEM upsert non-mutation: Call `upsertLocationByGeofence()` on existing location with `hitCount = 10`. Verify altitude updates but `hitCount` remains 10.
  2. *Workout Start Extraction Unit Tests (`WorkoutSummariesDatabaseManagerTest.kt`)*:
  • Insert workout extrema records (`TABLE_EXTREMA_VALUES` with `SENSOR_TYPE == LATITUDE` and `EXTREMA_TYPE == START`). Call `getAllWorkoutStartLocations()`. Verify returned list matches non-null start coordinates.
  3. *Reactive ViewModel Starts Counting Unit Tests (`KnownLocationsViewModelTest.kt`)*:
  • Verify `uiState.value.startsByLocationId` computes matching workout counts for each location.
  • Verify sort order `STARTS` orders descending by actual starts count.
  • Verify workout deletion from `WorkoutRepository` immediately updates `startsByLocationId` reactively.
  4. *9-Language Localization & Plurals Parity Audit*:
  • Verify `R.plurals.known_locations_starts` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 0 test failures.

### TST-DAT-012: **Workout Origin Source Provenance Verification** [Origin source attribute is accurately tracked, persisted across SQLite schema v24, mapped safely in domain models, displayed cleanly in WorkoutHeader without cluttering live tracked workouts, and backed by 100% test coverage.]
- **Requirement(s)**: `REQ-DAT-017`
- **Component**: **Workout Origin Source Provenance Verification**
- **Jira**: ``ATT-2186``
- **Test Description**:
  1. *Database Schema Evolution & Historical Default Unit Test (`WorkoutSummariesDatabaseSourceMigrationTest.kt`)*:
  • Initialize SQLite database at schema version 23 containing workouts without `source` column.
  • Execute `dbHelper.onUpgrade(db, 23, 24)`.
  • Verify column `source` exists and all historical workouts return `"TRACKED"`.
  • Verify new insert with `source = "TCX"` persists and queries successfully.
  2. *Domain Mapping & Parser Robustness Unit Test (`WorkoutDataSourceMappingTest.kt`)*:
  • Mock Cursor with various `source` values (`"TRACKED"`, `"TCX"`, `"GPX"`, `"FIT"`, `null`, `"INVALID"`).
  • Verify `WorkoutDataMapper.fromCursor` maps valid strings to enum and falls back safely to `TRACKED` for null/invalid.
  • Verify `workoutData.headerData.source` strictly matches `workoutData.source`.
  3. *Ingestion Tagging Unit Test (`WorkoutIngestionSourceTaggingTest.kt`)*:
  • Import TCX stream via `LegacyImportEngine` -> verify `source = 'TCX'`.
  • Import GPX stream via `LegacyImportEngine` -> verify `source = 'GPX'`.
  4. *Visual Contract & Badge Visibility Unit Test (`WorkoutHeaderSourceBadgeVisualContractTest.kt`)*:
  • Render `WorkoutHeader` with `source = TRACKED`: verify origin badge is absent.
  • Render with `source = TCX`, `GPX`, `FIT`: verify badge is displayed with correct localized label and tonal container.
  5. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify `workout_source_tracked`, `workout_source_tcx`, `workout_source_gpx`, `workout_source_fit` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 0 test failures.

### TST-DAT-013: **Full-Telemetry FIT Workout Exporter via Garmin FIT SDK Verification** [FitFileWriter generates fully compliant, CRC-verified binary FIT activity files with complete sensor telemetry, session/lap summaries, semicircles GPS conversion, and indoor fallback; integrated into export manager, preferences, and settings UI; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-DAT-018`
- **Component**: **Full-Telemetry FIT Workout Exporter via Garmin FIT SDK Verification**
- **Jira**: ``ATT-1827``
- **Test Description**:
  1. *Binary FIT File Structure & Header Unit Tests (`FitFileWriterTest.kt`)*:
  • Initialize mock workout database with full sensor stream (GPS, altitude, HR, speed, distance, cadence, power, temperature).
  • Execute `FitFileWriter.writeFile()`.
  • Verify file exists in export directory, starts with valid 14-byte FIT header, contains ".FIT" signature, and valid 2-byte CRC.
  • Decode file via official `com.garmin.fit.Decode` parser verifying 0 decoding errors.
  2. *FIT Message Hierarchy & Telemetry Accuracy Unit Tests (`FitFileWriterTest.kt`)*:
  • `FileIdMesg`: Verify `type == File.ACTIVITY`, `manufacturer == Manufacturer.DEVELOPMENT`, `product == 1`, `serialNumber == 1L`.
  • `ActivityMesg`: Verify `timestamp` matches workout end time, `totalTimerTime` matches elapsed active seconds, `numSessions == 1`.
  • `SessionMesg`: Verify `startTime`, `totalElapsedTime`, `totalDistance`, sport mapped correctly (`Sport.CYCLING` / `Sport.RUNNING`), summary averages and extrema (avg HR, max HR, avg cadence, max cadence, avg power, max power, ascent, descent) match workout summary.
  • `LapMesg`: Verify sequential laps match `Laps.db` entries with correct start times, durations, distances, and averages.
  • `RecordMesg`: Verify sequential records match `samplesTable` entries. Verify latitude/longitude are accurately converted to semicircles ($\text{round}(\text{deg} \times 2^{31} / 180)$).
  • Indoor / Missing GPS Fallback: Verify that for indoor workouts or records with NaN/missing coordinates, `positionLat` and `positionLong` fields are omitted while HR, cadence, and power are encoded without interruption.
  3. *Export Subsystem & UI Integration Unit Tests (`ExportManagerFitTest.kt`, `ExportSettingsDialogTest.kt`)*:
  • Verify `ExportManager.getExporter(FileFormat.FIT)` instantiates `FitFileWriter`.
  • Verify `FileFormat.FIT` in `STANDARD_FILE_FORMATS` and `ExportType.DROPBOX`.
  • Verify `TrainingApplication.exportToFIT()` / `setExportToFIT()` correctly manipulates `SP_EXPORT_FIT` preference.
  • Verify `ExportSettingsDialog` renders FIT toggle and persists state on save.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify string resource `FIT` (`<string name="FIT" translatable="false">FIT</string>`) is defined.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with 0 regressions.

### TST-DAT-014: **External FIT Workout Importer with Duplicate Detection & Sport Mapping Verification** [External FIT files are parsed and imported cleanly into SQLite with full sensor telemetry, semicircles conversion, indoor safety, automatic sport mapping, and duplicate prevention; batch UI feedback; 100% 9-language localization parity; zero test regressions.]
- **Requirement(s)**: `REQ-DAT-019`
- **Component**: **External FIT Workout Importer with Duplicate Detection & Sport Mapping Verification**
- **Jira**: ``ATT-1828``
- **Test Description**:
  1. *Binary FIT Decoding & Telemetry Ingestion Unit Tests (`LegacyImportEngineFitTest.kt`)*:
  • Initialize mock binary FIT file with `FileEncoder` containing `FileIdMesg`, `SessionMesg` (`Sport.CYCLING`), 2 `LapMesg` intervals, and 10 `RecordMesg` trackpoints with GPS, barometric altitude, speed, distance, HR, cadence, power, and temperature.
  • Execute `LegacyImportEngine.importFromFit(context, fitFile)`.
  • Verify return value is `true` (`ImportStatus.SUCCESS`).
  • Verify `WorkoutSummaries` row is created with matching `TIME_START`, `FINISHED = 1`, and `SOURCE == 'FIT'`.
  • Verify dynamic sample table in `WorkoutSamplesDatabaseManager` contains 10 rows with accurate telemetry.
  • Verify semicircles coordinates scale accurately back to degrees within $10^{-6}$ precision.
  • Verify laps in `LapsDatabaseManager` contain 2 intervals matching lap timestamps and distances.
  2. *Indoor / Missing GPS Null-Safety Unit Tests (`LegacyImportEngineFitTest.kt`)*:
  • Generate binary FIT file with trackpoints omitting `positionLat` and `positionLong` (indoor trainer session).
  • Verify import succeeds (`ImportStatus.SUCCESS`) without throwing SQLite constraint errors; verify HR, cadence, and power are persisted.
  3. *Sport & Subsport Mapping Unit Tests (`FitSportMappingTest.kt`)*:
  • Verify `Sport.CYCLING` -> `BSportType.BIKE`.
  • Verify `Sport.RUNNING` / `WALKING` -> `BSportType.RUN`.
  • Verify `Sport.FITNESS_EQUIPMENT` with `SubSport.INDOOR_CYCLING` -> `BSportType.BIKE`, `INDOOR_RUNNING` -> `BSportType.RUN`.
  • Verify `Sport.SWIMMING` / other -> `BSportType.OTHER` or `UNKNOWN`.
  4. *Multi-Dimensional Deduplication Unit Tests (`LegacyImportEngineFitTest.kt`)*:
  • Import FIT workout -> verify `ImportStatus.SUCCESS`.
  • Import identical file again -> verify `ImportStatus.DUPLICATE_SKIPPED`.
  • Rename file with identical start time -> verify `ImportStatus.DUPLICATE_SKIPPED`.
  • Import file with start time shifted by 60s (within 180s threshold) for same sport -> verify `ImportStatus.DUPLICATE_SKIPPED`.
  • Import file with start time shifted by 600s -> verify `ImportStatus.SUCCESS`.
  5. *Batch Processing & UI Integration Unit Tests (`BackupRestoreViewModelFitTest.kt`)*:
  • Invoke `viewModel.importFitFiles(context, uris)` with a batch of 4 URIs (2 new, 1 duplicate, 1 corrupted).
  • Verify `UiState.Success` contains summary counting 2 imported, 1 skipped duplicate, and 1 failed.
  • Verify reactive post-import sync (`WorkoutRepository.loadAllWorkouts`, `refreshClusters`).
  6. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify all newly added strings (`import_fit_files_title`, `import_fit_files_description`, `import_fit_button`, `fit_import_summary`, etc.) exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with 0 regressions.

### TST-DAT-015: **Google Drive Workout Export and Database Backup Integration Verification** [Google Drive native Google Sign-In with least-privilege `drive.file` scope, REST client, uploader, backup manager, WorkManager Wi-Fi constraints, settings modal dialog with localized error mapping and conditional disconnected state, and viewmodel operations execute with full fidelity, independent coexistence with Dropbox, and 100% test coverage.]
- **Requirement(s)**: `REQ-DAT-020`
- **Component**: **Google Drive Workout Export and Database Backup Integration Verification**
- **Jira**: ``ATT-1306``
- **Test Description**:
  1. *Native Google Sign-In & Verified Token Exchange Unit Tests (`GoogleDriveAuthSafetyTest.kt`, `GoogleDriveAuthManagerTest.kt`)*:
  • Verify `GoogleDriveAuthManager.getSignInOptions()` configures `GoogleSignInOptions` requesting `Scope("https://www.googleapis.com/auth/drive.file")` and email.
  • Verify `GoogleDriveAuthManager.acquireBearerToken()` executes `GoogleAuthUtil.getToken` on `Dispatchers.IO` and returns verified Bearer token.
  • Verify strict prohibition of mock/manual text dialog inputs: credentials persist IF AND ONLY IF Google Play Services authentication succeeds.
  • Test unlinked credential handling: verify `TrainingApplication.getGoogleDriveAuthToken()` returns `null` safely without `NullPointerException`.
  • Test disconnect flow: verify `signOut()`, `revokeAccess()`, `GoogleAuthUtil.clearToken()`, and credential deletion clear all state and suspend sync.
  2. *Google Drive Client & REST API Unit Tests (`GoogleDriveClientTest.kt`)*:
  • Mock OkHttpClient and enqueue responses for root folder lookup, folder creation (`aTrainingTracker`, `Workouts`, `Backups`), and multipart file upload.
  • Verify Bearer authorization token header injection on all requests.
  • Verify folder hierarchy resolution idempotency: existing folder ID reused; new folder created with parent ID.
  • Verify multipart upload body contains metadata JSON and binary stream.
  • Verify file download stream writing directly to destination file.
  3. *Google Drive Uploader Unit Tests (`GoogleDriveUploaderTest.kt`)*:
  • Initialize `GoogleDriveUploader` with mock context.
  • Test unlinked credential handling: verify returns `ExportResult(false, false, ...)` without throwing exceptions.
  • Test file existence guard: verify missing local file returns failure.
  • Test successful upload: verify file is read and uploaded to `aTrainingTracker/Workouts/` and `ExportResult(true, ...)` is returned.
  4. *Google Drive Backup Manager Unit Tests (`GoogleDriveBackupManagerTest.kt`)*:
  • Test `uploadBackup`: verify `.attbackup` file is uploaded to `aTrainingTracker/Backups/` returning `true`.
  • Test `downloadBackup`: verify destination file is populated and returns `true`.
  • Test unlinked / error resilience: verify returns `false` safely.
  5. *Export Subsystem & WorkManager Integration Unit Tests (`ExportManagerGoogleDriveTest.kt`)*:
  • Verify `ExportType.GOOGLE_DRIVE` supported formats include `CSV`, `GC`, `GPX`, `TCX`, `FIT`.
  • Verify `ExportManager.getExporter` instantiates `GoogleDriveUploader` for `ExportType.GOOGLE_DRIVE`.
  • Verify Wi-Fi constraint: when `googleDriveOnlyWifi == true`, work request constraint enforces `NetworkType.UNMETERED`.
  6. *Backup Worker & ViewModel Integration Unit Tests (`BackupWorkerGoogleDriveTest.kt`, `BackupRestoreViewModelGoogleDriveTest.kt`)*:
  • Verify `BackupWorker.doWork` initiates backup when Google Drive backup is enabled.
  • Verify independent cloud service execution: failure in Dropbox does not prevent Google Drive backup upload.
  • In `BackupRestoreViewModel`, verify `uploadToGoogleDrive` and `restoreFromGoogleDrive` dispatch appropriate states (`Loading`, `Success`, `Error`).
  7. *Settings & Navigation Bottom Sheet Contract & Error Mapping Tests (`ModalBottomSheetDialogsIntegrityTest.kt`, `GoogleDriveAuthErrorMappingTest.kt`)*:
  • Verify `GoogleDriveSettingsDialogKt` exists and exposes `@Composable fun GoogleDriveSettingsDialog(onDismiss: () -> Unit)` with native sign-in launcher integration.
  • Verify `GoogleDriveSettingsDialogFragment` extends `AppBottomSheetDialogFragment`.
  • Verify `R.id.drawer_google_drive` maps to `SettingsBottomSheetType.GOOGLE_DRIVE`.
  • Verify `GoogleDriveAuthErrorMapping`: status 10 (`DEVELOPER_ERROR`) maps to localized developer setup string; network errors map to localized network message; unknown status codes map to localized generic failure.
  • Verify disconnected UI state: when `isConnected == false`, sync toggles are conditionally suppressed/inactive and onboarding connect prompt is shown.
  8. *9-Language Localization & Plurals Parity Audit (`TranslationParityTest.kt`)*:
  • Verify string resources (`google_drive`, `google_drive_connect`, `google_drive_disconnect`, `google_drive_connected_status`, `google_drive_disconnected_status`, `google_drive_error_developer_config`, `google_drive_error_network`, `google_drive_error_generic`, `google_drive_connect_prompt`, `upload_workouts_to_google_drive`, `upload_backup_to_google_drive`, `google_drive_only_wifi`, etc.) and plurals (`export_notification__detail__GoogleDrive_waiting`, `ongoing`, `success`, `failed`) exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  9. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with 0 regressions.

### TST-DAT-016: **Workout Origin Source Batch Mapping Symmetry Verification** [Workout origin source attribute is preserved symmetrically across batch cursor mapping and snapshot streaming pipelines; UI origin badges render reliably for imported workouts; 100% test coverage with zero regressions.]
- **Requirement(s)**: `REQ-DAT-021`
- **Component**: **Workout Origin Source Batch Mapping Symmetry Verification**
- **Jira**: ``ATT-2387``
- **Test Description**:
  1. *Batch & Snapshot Symmetrical Mapping Unit Tests (`WorkoutDataSourceMappingTest.kt`)*:
  • Initialize mock cursors for all source types (`TCX`, `GPX`, `FIT`, `TRACKED`, `null`, `'INVALID'`).
  • Verify `WorkoutDataMapper.readCursorSnapshot(cursor)` extracts `source` into `RawCursorSnapshot.source`.
  • Verify `WorkoutDataMapper.fromSnapshot(snapshot, batch)` propagates `snapshot.source` to `WorkoutData.source` and `WorkoutData.headerData.source`.
  • Verify `WorkoutDataMapper.fromCursor(cursor, batch)` yields `WorkoutData.source` identically to single-item `fromCursor(cursor)`.
  • Verify default fallback to `WorkoutSource.TRACKED` for null, blank, or invalid strings across all mapping methods.
  2. *Architectural Mapping Symmetry Contract Test (`WorkoutDataMappingSymmetryTest.kt`)*:
  • Verify adherence to ASPICE Rule 20.
  • Inspect all column extraction operations in `fromCursor(cursor)` and verify corresponding fields are declared in `RawCursorSnapshot`, extracted in `readCursorSnapshot`, and mapped in `fromSnapshot`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with 0 regressions.

### TST-DAT-017: **Google Drive Background Token Refresh, 401 Re-Authentication, Folder Concurrency Synchronization & Diagnostics Verification** [Google Drive automatic token refresh on HTTP 401, process-wide concurrency synchronization preventing duplicate folders, canonical Google Drive API v3 root folder creation, and actionable localized error reporting execute with full fidelity; 100% 9-language parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-DAT-022`
- **Component**: **Google Drive Background Token Refresh, 401 Re-Authentication, Folder Concurrency Synchronization & Diagnostics Verification**
- **Jira**: ``ATT-2631``
- **Test Description**:
  1. *Automatic 401 Token Refresh & Request Retry Unit Tests (`GoogleDriveClientTest.kt`)*:
  • Mock OkHttpClient returning HTTP 401 on initial request and HTTP 200 on retry.
  • Verify `GoogleDriveClient` detects 401, invokes `tokenRefresher()`, updates Authorization header, and transparently retries request.
  • Verify `lastHttpCode` and `lastErrorMessage` capture failure diagnostics when refresh fails.
  2. *Process-Wide Concurrency & Shared Cache Unit Tests (`GoogleDriveClientTest.kt`)*:
  • Execute concurrent/sequential `ensureFolderHierarchy` calls across separate `GoogleDriveClient` instances (`testEnsureFolderHierarchy_concurrentCalls_sharesGlobalCacheWithoutDuplicateCreations`).
  • Verify `globalFolderIdCache` and `hierarchyLock` prevent duplicate folder creation requests, creating the folder exactly once in Google Drive.
  3. *Canonical Root Folder Creation Unit Tests (`GoogleDriveClientTest.kt`)*:
  • Verify `createFolder` with `parentId == "root"` or blank omits `parents` array in request body.
  • Verify child folder creation passes `parents: [parentId]`.
  4. *Database Backup Manager Token Refresher Unit Tests (`GoogleDriveBackupManagerTest.kt`)*:
  • Verify `clientProvider(context)` wires `tokenRefresher` using `GoogleDriveAuthManager.refreshTokenSync`.
  5. *Actionable Localized Error Reporting Unit Tests (`GoogleDriveUploaderTest.kt`)*:
  • Verify `GoogleDriveUploader` returns localized string `R.string.google_drive_error_auth_expired` on HTTP 401.
  • Verify `GoogleDriveUploader` returns localized string `R.string.google_drive_error_api_disabled` on HTTP 403 with `SERVICE_DISABLED` or `accessNotConfigured`.
  • Verify generic folder errors return `R.string.google_drive_error_folder_hierarchy` with HTTP status suffix.
  6. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `google_drive_error_auth_expired`, `google_drive_error_api_disabled`, and `google_drive_error_folder_hierarchy` exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  7. *On-Device Live Verification*: Completed on physical Google Pixel 10 (`66020DLCR002FL`) verifying successful FIT and TCX export uploads to `aTrainingTracker/Workouts`.
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with 0 regressions.

### TST-DAT-018: **Raw GPS Track Douglas-Peucker Simplification and Scalar Stream Downsampling Verification** [Polyline simplification reduces point counts by >90% while preserving curve fidelity; scalar streams are downsampled to 20s intervals; live tracking collects raw 1 Hz fixes and simplifies upon finalization; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-DAT-023`
- **Component**: **Raw GPS Track Douglas-Peucker Simplification and Scalar Stream Downsampling Verification**
- **Jira**: ``ATT-2667``
- **Test Description**:
  1. *Import Engine Polyline Simplification Unit Test (`LegacyImportEngineSimplificationTest.kt`)*:
  • Construct a synthetic GPS route of 7,200 points with straight segments and curves.
  • Run `recalculateStats()` via `LegacyImportEngine`.
  • Verify `WorkoutSummaries.MAP_POLYLINE` decodes to ~150–350 points, demonstrating >90% point reduction.
  • Verify start and end points and apex curve vertices are preserved within 10.0m tolerance.
  2. *Scalar Stream Downsampling Unit Test (`LegacyImportEngineSimplificationTest.kt`)*:
  • Provide 7,200 altitude and distance samples to `recalculateStats()`.
  • Verify `ALTITUDE_STREAM` and `DISTANCE_STREAM` contain exactly $7200 / 20 = 360$ samples.
  • Verify delta encoding matches `NumericalEncodingUtils`.
  • Verify short workouts (< 20 points) retain at least 1 sample without crashing.
  3. *Live Tracking Raw Point Collection Unit Test (`LiveWorkoutSessionTest.kt`)*:
  • Feed 100 sequential 1 Hz `LatLng` fixes to `LiveWorkoutSession.recordStreamPoint()`.
  • Verify `getRawLatLngs()` contains all 100 raw fixes.
  • Verify `getSampledLatLngs()` contains exactly 5 points (1 point per 20 seconds).
  4. *TrackerService Finalization Unit Test (`TrackerServiceFinalizePolylineTest.kt`)*:
  • Mock active `LiveWorkoutSession` with 500 raw points on a straight-and-turn course.
  • Invoke `finalizeLiveSession()`.
  • Verify the simplified polyline written to `WorkoutSummaries.MAP_POLYLINE` uses `PolyUtil.simplify(rawLatLngs, 10.0)` and contains significantly fewer points than raw points while retaining curve fidelity.
  5. *Clean-Room Regression Suite*:
  • Run `./gradlew testDebugUnitTest` verifying 0 test failures.

### TST-DAT-019: **Google Drive Format-Specific Directory Organization for Workout Exports and Imports Verification** [Google Drive workout uploads dynamically target format-specific folders (FIT, TCX, GPX, CSV, GC) matching Dropbox parity; bulk imports prioritize format folders with backward-compatible legacy Workouts fallback and base filename deduplication; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-DAT-024`
- **Component**: **Google Drive Format-Specific Directory Organization for Workout Exports and Imports Verification**
- **Jira**: ``ATT-3168``
- **Test Description**:
  1. *Format-Specific Destination Resolution Unit Tests (`GoogleDriveUploaderTest.kt`)*:
  • Mock `GoogleDriveClient.ensureFolderHierarchy` and invoke `uploader.doExport(exportInfo)` for `FileFormat.FIT`, `FileFormat.TCX`, `FileFormat.GPX`, and `FileFormat.CSV`.
  • Verify `ensureFolderHierarchy` is called with `listOf("aTrainingTracker", format.dirName)` (e.g. `["aTrainingTracker", "FIT"]`, `["aTrainingTracker", "TCX"]`, `["aTrainingTracker", "GPX"]`).
  • Verify `uploadOrOverwriteFile` targets the resolved format folder ID.
  2. *Format-Scoped Candidate Folder Hierarchy Unit Tests (`LegacyImportEngineGoogleDriveFolderResolutionTest.kt`)*:
  • Verify candidate folder hierarchies generated for format `"fit"` scan `["aTrainingTracker", "FIT"]` then `["aTrainingTracker", "Workouts"]` and `["aTrainingTracker"]`.
  • Verify candidate folder hierarchies generated for format `"tcx"` scan `["aTrainingTracker", "TCX"]` then `["aTrainingTracker", "Workouts"]` and `["aTrainingTracker"]`.
  • Verify candidate folder hierarchies generated for format `"gpx"` scan `["aTrainingTracker", "GPX"]` then `["aTrainingTracker", "Workouts"]` and `["aTrainingTracker"]`.
  • Verify candidate folder hierarchies generated for format `"all"` scan `FIT`, `TCX`, `GPX`, then legacy `Workouts`.
  • Verify entries from format folders and legacy `Workouts` folder are deduplicated by base filename without duplicates.
  3. *Clean-Room Regression Suite*:
  • Run `./gradlew testDebugUnitTest` verifying 0 test failures.

### TST-DAT-020: **Authoritative Database Cursor Row and Batch Mappers Verification** [Authoritative cursor row and batch mappers extract entities with 100% fidelity, default fallbacks, and single-pass index caching; cursor lifecycle and positioning invariants preserved; zero regressions across test suite.]
- **Requirement(s)**: `REQ-DAT-025`
- **Component**: **Authoritative Database Cursor Row and Batch Mappers Verification**
- **Jira**: ``ATT-3366``
- **Test Description**:
  1. *RoutesDatabaseManager Cursor Mapper Unit Tests (`RoutesDatabaseManagerMapperTest.kt`)*:
  • Verify `mapCursorToRouteWaypoint(cursor)` correctly extracts all 9 fields with valid coordinates, altitude, distance, and type.
  • Verify fallback to `WaypointType.GENERIC` when `type` column is corrupted, invalid, or null.
  • Verify fallback to `altitude = 0.0` when elevation column is null.
  • Verify `mapCursorToPathPoint(cursor)` extracts `latLng`, `distance`, and `altitude`.
  • Verify `mapCursorToRouteSummary(cursor)` extracts all route summary columns correctly.
  • Verify `mapCursorToRouteWaypoints(cursor)` and `mapCursorToPathPoints(cursor)` return complete lists with identical elements to row-by-row mapping.
  2. *Index Caching Performance & Lifecycle Parity Unit Tests (`RoutesDatabaseManagerMapperTest.kt`)*:
  • Verify row mapper does not mutate cursor position (cursor stays on the same row).
  • Verify cursor is not closed by mapper functions.
  • Verify batch mappers instantiate `WaypointIndices` / `PathPointIndices` once and yield identical entities to un-indexed `fromCursor(cursor)`.
  3. *WorkoutCluster Database Manager & Repository Mapping Tests (`WorkoutClusterMapperTest.kt`)*:
  • Verify `WorkoutClusterDatabaseManager.fromCursor(cursor)` and `fromCursorBatch(cursor)` correctly map cluster rows.
  • Verify `WorkoutClusterRepository.fromCursorBatchKeys(cursor)` gathers all IDs, filenames, and cluster IDs with pre-cached indices, matching raw row values.
  • Verify `WorkoutBatchKey.fromCursor(cursor)` single-row extraction symmetry.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 0 test failures across the full suite.

### TST-EXP-015: **FIT Format Detailed Export Report, Safe Database Upsert & Workout Header Menu Verification** [FIT format is accurately rendered in detailed export report for both legacy and new workouts, ExportStatusDatabaseManager executes safe upserts without NPE, and WorkoutHeader offers direct FIT export; 100% test pass rate.]
- **Requirement(s)**: `REQ-EXP-018`
- **Component**: **FIT Format Detailed Export Report, Safe Database Upsert & Workout Header Menu Verification**
- **Jira**: ``ATT-2310``
- **Test Description**:
  1. *Database Upsert Unit Tests (`ExportStatusDatabaseManagerTest.kt`)*:
  • Verify `updateExportStatus` performs safe `insert` (upsert) when row for `(fileBaseName, exportType, fileFormat)` does not exist.
  • Verify `updateExportStatus` updates existing rows in-place without duplicating rows.
  • Verify `exportType == null` updates all matching format rows and inserts missing rows without `NullPointerException`.
  2. *UI Provider Synthesis Unit Tests (`ExportStatusDataProviderTest.kt`)*:
  • Verify `createGroupData` synthesizes `ExportRow` for `FileFormat.FIT` with status `UNWANTED` when given legacy rows missing `FIT`.
  • Verify `createGroupData` reflects active database status for `FIT` when a row for `FIT` is present in SQLite.
  • Verify rendered `details` are ordered consistently according to `exportType.exportToFileFormats`.
  3. *Workout Header Export Menu Contract Test (`WorkoutHeaderExportMenuContractTest.kt`)*:
  • Verify `WorkoutHeader.kt` defines `FileFormat.FIT to R.string.fitWrite` in `standardFormats`.
  4. *9-Language Localization Audit*:
  • Verify `fitWrite` exists with non-empty translation across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-MIG-034: **Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification** [Unified streaming TCX and GPX XML trackpoint parsing engine verification (Rule 36), ensuring zero XML parsing duplication and 100% backward-compatible workout and route import fidelity; 100% test pass rate.]
- **Requirement(s)**: `REQ-MIG-037`
- **Component**: **Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification**
- **Jira**: ``ATT-3362``
- **Test Description**:
  1. *Dedicated Unit Tests (`TrackpointXmlParserTest.kt`)*:
  • Verify streaming TCX activity and course parsing, position extraction, altitude, cumulative distance, cadence, watts, speed, and course points.
  • Verify streaming GPX track and route parsing, multi-segment laps (`<trkseg>`), coordinates, elevation, timestamp, Garmin TPX extensions (`hr`, `cad`, `speed`, `atemp`), and waypoints (`<wpt>`).
  • Verify handling of malformed XML, empty documents, missing optional telemetry, and mixed namespaces.
  2. *Workout Ingestion Regression Tests (`LegacyImportEngineGpxTest.kt`, `TcxHighFidelityImportTest.kt`)*:
  • Verify high-fidelity workout ingestion from GPX and TCX files, ensuring identical ContentValues, lap splits, cluster suggestions, and sensor presence.
  3. *Route Import Regression Tests (`TcxCourseParserTest.kt`, `GpxRouteImporterWaypointTest.kt`, `GpxRouteImporterElevationTest.kt`)*:
  • Verify TCX course point projection and distance accumulation.
  • Verify GPX waypoint extraction, classification, and DEM elevation enrichment.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-MIG-035: **Modular Workout Format Parsers for TCX, GPX, and FIT Ingestion & LegacyImportEngine Architectural Facade Verification** [Modular workout format parsers for TCX, GPX, and FIT ingestion & LegacyImportEngine architectural facade verification (Rule 20, Rule 23, Rule 36), ensuring dedicated parser components and 100% test pass rate.]
- **Requirement(s)**: `REQ-MIG-038`
- **Component**: **Modular Workout Format Parsers for TCX, GPX, and FIT Ingestion & LegacyImportEngine Architectural Facade Verification**
- **Jira**: ``ATT-3552``
- **Test Description**:
  1. *Dedicated Format Parser Unit Tests (`TcxWorkoutParserTest.kt`, `GpxWorkoutParserTest.kt`, `FitWorkoutParserTest.kt`)*:
  • Verify `TcxWorkoutParser.parse()` correctly parses sample TCX data into `ParsedWorkoutData`, extracting ISO-8601 timestamps, laps, trackpoints, altitude extrema, and sensor types.
  • Verify `GpxWorkoutParser.parse()` correctly parses sample GPX data into `ParsedWorkoutData`, extracting waypoints, multi-segment laps, coordinates, and cumulative distance.
  • Verify `FitWorkoutParser.parse()` correctly parses sample FIT binary data into `ParsedWorkoutData`, validating message decoding, sport mapping, and sample generation.
  2. *Unified Persistence Pipeline & Concurrency Tests (`LegacyImportEngineResultTest.kt`)*:
  • Verify `persistParsedWorkout` safely handles duplicate workouts, returning `ImportStatus.DUPLICATE_SKIPPED` under `importMutex`.
  • Verify sample table creation, bulk insertion, lap metadata persistence, and cluster candidate notification.
  3. *Facade API Invariant & Regression Tests (`LegacyImportEngineFitTest.kt`, `LegacyImportEngineGpxTest.kt`, `TcxHighFidelityImportTest.kt`, `LegacyImportEngineDeduplicationTest.kt`)*:
  • Verify 100% backward compatibility of all existing public and internal entry points.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-DAT-001: **Deterministic Resource Management via Try-With-Resources and Bounds Checking on SQLite Cursors Verification Suite** [Deterministic resource management verified across try-with-resources allocation, empty query bounds immunity, static architectural audit guard, and full clean-room suite.]
- **Requirement(s)**: `REQ-DAT-001`
- **Component**: **Deterministic Resource Management via Try-With-Resources and Bounds Checking on SQLite Cursors Verification Suite**
- **Jira**: ``ATT-3550``
- **Test Description**:
  1. *Checked Cursor Navigation Unit Tests*:
  • Verify queries against empty database tables return appropriate type-specific sentinels (`emptyList()`, `null`, `-1L`, `0`, `false`) without throwing `CursorIndexOutOfBoundsException`.
  • Verify iterative cursor loops (`while (cursor.moveToNext())` and `moveToFirst()`) safely terminate on empty cursors.
  • Verify non-empty query results successfully parse and populate data models.
  2. *Static Architecture Audit Guard (`TryWithResourcesCursorAuditTest.kt`)*:
  • Scan all Java database managers in `app/src/main`.
  • Verify that zero local `Cursor` allocations exist without `try-with-resources`.
  • Enforce whitelist-based exclusion for public/protected provider methods returning `Cursor` to prevent false positives.
  3. *CLI Linter Verification (`tools/lint_cursor_safety.py`)*:
  • Execute python linter script asserting zero unshielded cursor allocations with provider method exclusion.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all 2932+ tests.

### TST-DAT-021: **Targeted Crash-Resume Single-Row Query & Bounds Safety Verification Suite** [Targeted crash-resume single-row query and bounds safety verified across unit tests, empty database handling, static guard, and full clean-room suite.]
- **Requirement(s)**: `REQ-DAT-026`
- **Component**: **Targeted Crash-Resume Single-Row Query & Bounds Safety Verification Suite**
- **Jira**: ``ATT-3549``
- **Test Description**:
  1. *Targeted Resume Query & Data Reconstruction Unit Tests*:
  • Verify `recreateValuesWhenResuming()` queries `WorkoutSummaries.TABLE` using an explicit 7-column projection and `ORDER BY _id DESC LIMIT 1`.
  • Verify active session fields (`mBaseFileName`, `mWorkoutID`, `mTimeTotal_s`, `mTimeActive_s`, `calories`, `lapNr`, `mDistanceTotal_m`) and `BANALService` accumulators are accurately populated from the single latest row.
  2. *Empty Database Bounds Resilience Unit Tests*:
  • Verify calling `recreateValuesWhenResuming()` against an empty `WorkoutSummaries` table logs a warning and returns cleanly without throwing `CursorIndexOutOfBoundsException`.
  3. *Static Architecture Audit Guard*:
  • Assert zero unshielded `cursor.moveToLast()` or unbounded `query(..., null, null, null, null, null, null)` in `TrackerService.java`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-DAT-021: **Workout Cluster Engine, Repository, and Equipment Data Provider Cursor Mapper Verification Suite** [Authoritative cursor row and batch mappers extract entities with 100% fidelity, default fallbacks, and single-pass index caching; zero manual cursor column lookups in WorkoutClusterEngine, WorkoutClusterRepository, and EquipmentDataProvider; zero regressions across test suite.]
- **Requirement(s)**: `REQ-DAT-026`
- **Component**: **Workout Cluster Engine, Repository, and Equipment Data Provider Cursor Mapper Verification Suite**
- **Jira**: ``ATT-3577``
- **Test Description**:
  1. *Source Code Contract Audit (`WorkoutClusterMapperTest.kt`)*:
  • Recursively scan `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt` and assert strictly 0 occurrences of `getColumnIndex(` or `getColumnIndexOrThrow(`.
  2. *Workout Bounds DTO Mapper Unit Tests (`WorkoutClusterMapperTest.kt`)*:
  • Verify `WorkoutBoundsDto.fromCursor(cursor)` extracts valid min/max lat/lng coordinates and computes `hasPoints == true`.
  • Verify null coordinate columns map to `null` and `hasPoints == false`.
  • Verify cursor is not advanced or closed.
  3. *Cluster Workout Candidate DTO Mapper Unit Tests (`WorkoutClusterMapperTest.kt`)*:
  • Verify `ClusterWorkoutCandidateDto.fromCursor(cursor)` extracts `workoutId`, `workoutName`, `fileBaseName`, `sportId`, and `distance`.
  • Verify nullable name and file strings are handled safely.
  • Verify cursor is not advanced or closed.
  4. *Workout Equipment DTO Mapper Unit Tests (`WorkoutClusterMapperTest.kt`)*:
  • Verify `WorkoutEquipmentDto.fromCursor(cursor)` extracts `sportId` and `equipmentId`.
  • Verify `EquipmentDataProvider.getEquipmentData(cursor)` resolves equipment data using `WorkoutEquipmentDto`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-MIG-036: **Offline Power Training Metrics Math, Historical Threshold Resolution & FIT/TCX/GPX Import Persistence Verification Suite** [Offline power training metrics (`NP`, `IF`, `TSS`, `Work kJ`, `W/kg`, `VI`), historical athlete FTP/weight lookup, FIT parser sensor discovery, and import/finalization extrema persistence verified across unit tests, contract tests, and full clean-room regression suite.]
- **Requirement(s)**: `REQ-MIG-039`
- **Component**: **Offline Power Training Metrics Math, Historical Threshold Resolution & FIT/TCX/GPX Import Persistence Verification Suite**
- **Jira**: ``ATT-3860``
- **Test Description**:
  1. *Pure Domain Power Training Metrics Unit Tests (`PowerPhysiologyCalculatorTest.kt`)*:
  • Verify `calculateIntensityFactor`, `calculateTss`, `calculateWorkKj`, `calculatePowerToWeight`, `calculateVariabilityIndex`, and `computePowerTrainingMetrics` across steady power (`250 W` for `3600 s` at `FTP = 250 W`, `weight = 75 kg` -> `NP = 250 W`, `IF = 1.0`, `TSS = 100.0`, `Work = 900.0 kJ`, `VI = 1.0`, `W/kg = 3.333`), variable intervals (`NP > Avg Power`, `VI > 1.0`), FIT session summary fallback seeds, and degenerate/empty streams.
  2. *Historical Athlete Profile Lookup & Import/Finalization Source Contract Tests (`PowerPhysiologyCalculatorTest.kt`)*:
  • Verify `AthleteMetricsDatabaseManager.kt` defines `getEntryEffectiveAt` and `SettingsDataStoreJavaHelper.kt` defines `resolveEffectiveFtpAt` and `resolveEffectiveWeightKgAt`.
  • Verify `FitWorkoutParser.kt` populates `foundSensors` (including `SensorType.POWER`) and extracts optional FIT `SessionMesg` power summary fields into `ParsedWorkoutData`.
  • Verify `LegacyImportEngine.kt` and `TrackerService.java` persist `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, and `POWER_TO_WEIGHT` extrema via `updateExtremaValue`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-DAT-022: **GoldenCheetah JSON File Export Format and Data Integrity Verification Suite** [GoldenCheetah JSON export format, numeric primitives, German locale independence, GPS double-precision fidelity, intervals array, and metadata tags verified in `GCFileWriterTest` and full clean-room suite.]
- **Requirement(s)**: `REQ-DAT-027`
- **Component**: **GoldenCheetah JSON File Export Format and Data Integrity Verification Suite**
- **Jira**: ``ATT-2169``
- **Test Description**:
  1. *Valid JSON Syntax & Numeric Primitives Unit Test (`TST-DAT-022.1`, `GCFileWriterTest.kt`)*:
  • Verify that `GCFileWriter.doExport` generates syntactically valid JSON parseable by `org.json.JSONObject`.
  • Verify that `SAMPLES` array entries contain raw numeric literals (`Number` instances for `KM`, `KPH`, `WATTS`, `CAD`, `NM`, `ALT`, `LAT`, `LON`) and integers for `SECS` and `HR`, without string quotes.
  2. *Locale Independence & Comma Elimination Test (`TST-DAT-022.2`, `GCFileWriterTest.kt`)*:
  • Execute export with default system locale set to `Locale.GERMANY` and assert zero decimal commas appear in numeric sample values or metadata fields.
  3. *Complete GPS Coordinate Validation & Double Precision Test (`TST-DAT-022.3`, `GCFileWriterTest.kt`)*:
  • Verify that both `SensorType.LATITUDE` and `SensorType.LONGITUDE` validity are verified via `dataValid(...)`.
  • Verify that longitude coordinate extraction uses 64-bit IEEE 754 double precision without float truncation jitter.
  4. *Workout Laps & `INTERVALS` Array Generation Test (`TST-DAT-022.4`, `GCFileWriterTest.kt`)*:
  • Configure mock `LapsDatabaseManager` with multiple recorded laps; verify `GCFileWriter` serializes an `"INTERVALS"` array with exact `START` (elapsed seconds), `STOP` (elapsed seconds), and `NAME` attributes.
  5. *Metadata & Tags Alignment Test (`TST-DAT-022.5`, `GCFileWriterTest.kt`)*:
  • Verify `"TAGS"` object correctly populates `"Sport"`, `"Workout Title"`, `"Notes"`, `"Workout Code"`, `"Device"`, and `"Data"`.
  6. *Resource & Stream Safety Test (`TST-DAT-022.6`, `GCFileWriterTest.kt`)*:
  • Verify that SQLite `Cursor` and `BufferedWriter` are safely closed via `try-with-resources` under both successful completion and exceptions.
  7. *9-Language Localization & Clean-Room Full Suite Regression (`TST-DAT-022.7`, `TST-DAT-022.8`)*:
  • Execute `TranslationParityTest` and `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-MIG-037: **Zip-Slip Path Traversal Protection, Top-Level Whitelist, Symlink Rejection & Resource Exhaustion Defense Verification Suite** [Zip-slip path traversal rejection in MigrationEngine and ImportEngine, directory whitelist enforcement, zip bomb defense, symlink rejection, Google Drive filename sanitization, and valid archive integrity verified in BackupImportSecurityTest and clean-room full suite.]
- **Requirement(s)**: `REQ-MIG-040`, `REQ-MIG-004`, `REQ-MIG-036`
- **Component**: **Zip-Slip Path Traversal Protection, Top-Level Whitelist, Symlink Rejection & Resource Exhaustion Defense Verification Suite**
- **Jira**: ``ATT-3982``
- **Test Description**:
  1. *Zip-Slip Path Traversal Rejection in `MigrationEngine` Test (`TST-MIG-037.1`, `BackupImportSecurityTest.kt`)*:
  • Craft a zip archive containing relative traversal entries (`../../databases/evil.db` and `../../shared_prefs/secret.xml`).
  • Invoke `MigrationEngine.performFullRestore()`.
  • Verify that restore returns `false`, no files outside `restore_temp` are created or modified, and `restore_temp` is purged.
  2. *Zip-Slip Path Traversal Rejection in `ImportEngine.analyzeBackup` Test (`TST-MIG-037.2`, `BackupImportSecurityTest.kt`)*:
  • Craft a zip archive with relative traversal entries.
  • Invoke `ImportEngine.analyzeBackup()`.
  • Verify that `SecurityException` is thrown, zero files are created outside the temporary analysis directory, and temporary files are cleaned up.
  3. *Zip-Slip Path Traversal Rejection in `ImportEngine.performIncrementalImport` Test (`TST-MIG-037.3`, `BackupImportSecurityTest.kt`)*:
  • Craft a zip archive with relative traversal entries.
  • Invoke `ImportEngine.performIncrementalImport()`.
  • Verify that import returns `0`, zero workouts are imported, and zero external files are written.
  4. *Top-Level Directory Whitelist Enforcement Test (`TST-MIG-037.4`, `BackupImportSecurityTest.kt`)*:
  • Craft a zip archive with entries outside the whitelist (`lib/libevil.so`, `malicious.sh`, `root_file.txt`).
  • Verify extraction rejects entries outside `databases/`, `shared_prefs/`, and `datastore/` with `SecurityException`.
  5. *Zip Bomb & Resource Cap Protection Test (`TST-MIG-037.5`, `BackupImportSecurityTest.kt`)*:
  • Simulate an archive exceeding entry count (> 10,000) or total uncompressed size (> 1 GB) using streaming counting.
  • Verify extraction terminates immediately with `SecurityException` without disk exhaustion.
  6. *Remote Google Drive Filename Traversal Sanitization Test (`TST-MIG-037.6`, `BackupImportSecurityTest.kt`)*:
  • In `LegacyImportEngine`, pass an entry with name `../../escaped.fit`.
  • Verify the local file is constructed strictly as `workerDir/escaped.fit` without escaping `workerDir`, rejecting path separators.
  7. *Valid Archive Integrity & Extraction Verification (`TST-MIG-037.7`, `BackupImportSecurityTest.kt`)*:
  • Create a canonical `.attbackup` archive containing valid `databases/`, `shared_prefs/`, and `datastore/` structures.
  • Verify extraction succeeds cleanly, preserving all files and subdirectory hierarchies.
  8. *Archive Symbolic Link Rejection Test (`TST-MIG-037.8`, `BackupImportSecurityTest.kt`)*:
  • Craft or simulate a zip archive containing symbolic link entries pointing within or outside the sandbox.
  • Verify extraction strictly rejects symlinks with `SecurityException` (`REQ-MIG-040.2`).
  9. *Clean-Room Full Suite Regression Execution (`TST-MIG-037.9`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-MIG-038: **Pre-Restore Validation, Schema Version Bounds, Integrity Checking & Atomic Rollback Verification Suite** [Missing databases rejection, corrupted SQLite rejection, forward schema version downgrade prevention, atomic rollback snapshot recovery, and valid restore verified in FullRestoreValidationSecurityTest and clean-room full suite.]
- **Requirement(s)**: `REQ-MIG-041`, `REQ-MIG-040`
- **Component**: **Pre-Restore Validation, Schema Version Bounds, Integrity Checking & Atomic Rollback Verification Suite**
- **Jira**: ``ATT-3989``
- **Test Description**:
  1. *Missing Databases Directory Rejection Test (`TST-MIG-038.1`, `FullRestoreValidationSecurityTest.kt`)*:
  • Craft an archive missing `databases/` directory (only `shared_prefs/` or empty).
  • Invoke `MigrationEngine.performFullRestore()`.
  • Verify restore returns `false`, zero local database files are deleted or modified, and `restore_temp` is purged.
  2. *Corrupted SQLite File Rejection Test (`TST-MIG-038.2`, `FullRestoreValidationSecurityTest.kt`)*:
  • Craft an archive with `databases/WorkoutSummaries.db` containing truncated or corrupted bytes failing `PRAGMA quick_check`.
  • Invoke `performFullRestore()`.
  • Verify restore returns `false` and local data is preserved.
  3. *Forward Schema Version Downgrade Prevention Test (`TST-MIG-038.3`, `FullRestoreValidationSecurityTest.kt`)*:
  • Craft an archive where `WorkoutSummaries.db` has `PRAGMA user_version = 99` (exceeding current `DB_VERSION = 25`).
  • Invoke `performFullRestore()`.
  • Verify restore returns `false` and existing databases are untouched.
  4. *Atomic Rollback on Mid-Restore I/O Failure Test (`TST-MIG-038.4`, `FullRestoreValidationSecurityTest.kt`)*:
  • Simulate an I/O failure (or mock exception) during directory installation.
  • Verify that `performFullRestore` restores all original databases and preferences from the rollback snapshot.
  5. *Valid Archive Full Restore & Restart Test (`TST-MIG-038.5`, `FullRestoreValidationSecurityTest.kt`)*:
  • Provide a valid backup archive matching current schema.
  • Verify restore succeeds, files are moved into place, rollback directory is deleted, and restart is triggered.
  6. *Clean-Room Full Suite Regression Execution (`TST-MIG-038.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-MIG-039: **Samples Table Identifier Escaping, Case-Insensitive Collision Disambiguation & Structural DDL Verification** [Samples table identifier quote escaping, case-insensitive collision disambiguation with sub-second guard, true duplicate skip, and structural DDL whitelisting verified.]
- **Requirement(s)**: `REQ-MIG-042`
- **Component**: **Samples Table Identifier Escaping, Case-Insensitive Collision Disambiguation & Structural DDL Verification**
- **Jira**: ``ATT-3991``
- **Test Description**:
  1. *Embedded Double Quote Escaping Unit Test*:
  • Call `WorkoutSamplesDatabaseManager.getTableName("My \"Fast\" Ride")`.
  • Assert result equals `"\"My \"\"Fast\"\" Ride\""`.
  • Verify table creation, sample insertion, and query succeed in SQLite without syntax error.
  2. *Case-Insensitive Collision Disambiguation Unit Test*:
  • Insert existing workout `morning_ride` with samples into database.
  • Import workout `Morning_Ride` with distinct timestamp.
  • Assert that existing table `"morning_ride"` is NOT dropped and retains all original samples.
  • Assert that new workout is inserted with disambiguated name and its samples table created successfully.
  3. *Sub-Second Collision Disambiguation Test*:
  • Simulate two imports sharing identical file base names and identical timestamps.
  • Assert that the second import deterministically advances its suffix (`_1`) and avoids collision.
  4. *True Duplicate Case-Insensitive Skip Test*:
  • Provide workout with same name (differing only in case) and same timestamp.
  • Verify import skips without creating duplicate tables or modifying database.
  5. *Structural DDL Construction & Injection Immunity Test*:
  • Provide archive with crafted/malicious DDL in `sqlite_master` (e.g. multi-statement with `DROP TABLE`, invalid types, or comments).
  • Verify `ImportEngine` ignores raw DDL, inspects schema via `PRAGMA table_info`, whitelists types, and constructs clean table without executing injected commands.
  6. *Clean-Room Regression Suite*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-MIG-040: **Atomic Cross-Database Import via SQLite ATTACH, insertOrThrow Enforcement, Schema Intersection & Cooperative Coroutine Responsiveness Verification Suite** [Atomic cross-database rollback, partial ATTACH resilience, shared locking, non-destructive orphan diagnostics, insertOrThrow enforcement, schema intersection with NOT NULL defaults, inverted legacy staging, and cooperative coroutine cancellation verified in WorkoutImportAtomicityTest and full test suite.]
- **Requirement(s)**: `REQ-MIG-043`
- **Component**: **Atomic Cross-Database Import via SQLite ATTACH, insertOrThrow Enforcement, Schema Intersection & Cooperative Coroutine Responsiveness Verification Suite**
- **Jira**: ``ATT-3992``
- **Test Description**:
  1. *Atomic Cross-Database Rollback via SQLite ATTACH Test (`TST-MIG-040.1`, `WorkoutImportAtomicityTest.kt`)*:
  • Simulate a failure during samples table population or lap copy on an attached database connection.
  • Verify that SQLite automatically rolls back all mutations across `main` (`tblWorkoutSummaries`, `extrema_values`, `accumulated_sensors`), `lapsDb` (`tblLaps`), and `samplesDb` (no orphan table created).
  2. *Partial Failure during ATTACH Resilience Test (`TST-MIG-040.2`, `WorkoutImportAtomicityTest.kt`)*:
  • Provide an invalid or missing database path during `ATTACH DATABASE`.
  • Verify exception is caught, outer `finally` executes safe detach/close, and connection leaves zero dangling transactions.
  3. *Shared Dual Concurrency Lock Serialization Test (`TST-MIG-040.3`, `WorkoutImportAtomicityTest.kt`)*:
  • Simulate concurrent `BackupManager` and `ImportEngine` execution.
  • Verify `BackupManager.acquireLock` serializes access cleanly across threads and processes without `SQLiteDatabaseLockedException`.
  4. *Non-Destructive Diagnostic Orphan Detection Test (`TST-MIG-040.4`, `WorkoutImportAtomicityTest.kt`)*:
  • Populate unreferenced tables in `WorkoutSamples.db` and rows with `workout_id = -1` in child tables.
  • Verify `OrphanDataDiagnosticHelper` detects and logs findings without automated silent deletion.
  5. *Strict `insertOrThrow` & Non-Positive ID Assertion Test (`TST-MIG-040.5`, `WorkoutImportAtomicityTest.kt`)*:
  • Simulate constraint violation on summary insert.
  • Verify `insertOrThrow` throws `SQLiteConstraintException` immediately, preventing invocation of child copy routines with `workout_id = -1`.
  6. *Static Schema Mapping & NOT NULL Defaults Test (`TST-MIG-040.6`, `WorkoutImportAtomicityTest.kt`)*:
  • Import backup containing unexpected source columns and missing required `NOT NULL` columns.
  • Verify surplus columns are safely discarded, required `NOT NULL` columns receive documented default values, and import succeeds without error.
  7. *Inverted Legacy Import Preconditions & Staging Test (`TST-MIG-040.7`, `WorkoutImportAtomicityTest.kt`)*:
  • Call `LegacyImportEngine` with `firstTime = null` or simulate failure in statistical recalculation.
  • Verify zero orphan samples tables are created or left behind.
  8. *Cooperative Coroutine Cancellation & Non-Blocking Delay Test (`TST-MIG-040.8`, `WorkoutImportAtomicityTest.kt`)*:
  • Cancel a running import job while waiting in retry backoff.
  • Verify worker immediately rethrows `CancellationException` and terminates without thread blocking.
  9. *Clean-Room Full Suite Regression Execution (`TST-MIG-040.9`)*:
  • Run `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-MIG-041: **Non-Destructive SportType Schema Upgrade, Custom Sport Preservation, Corrupted State Healing & Downgrade Safety Verification Suite** [Non-destructive v1->v3 upgrade, custom sport type preservation, WALK base sport update, corrupted post-v2 placeholder healing, sequence protection, UI picker filtering, and strict downgrade guard verified in SportTypeDatabaseUpgradeTest and clean-room full test suite.]
- **Requirement(s)**: `REQ-MIG-044`
- **Component**: **Non-Destructive SportType Schema Upgrade, Custom Sport Preservation, Corrupted State Healing & Downgrade Safety Verification Suite**
- **Jira**: ``ATT-3993``
- **Test Description**:
  1. *Clean v1 -> v3 Migration & Invariant Preservation Test (`TST-MIG-041.1`, `SportTypeDatabaseUpgradeTest.kt`)*:
  • Create a fixture database with Schema v1 containing default sports (1..5) with `WALK` having `baseSportType = 'UNKNOWN'`, plus user-defined custom sports `_id = 6` ("Skating") and `_id = 7` ("Rowing").
  • Execute `onUpgrade` to Schema v3.
  • Assert that `SportTypes` table is NOT dropped, custom sports 6 and 7 are fully preserved with identical IDs and attributes, `WALK` is updated to `baseSportType = 'RUN'`, and column `is_placeholder` is added with default `0`.
  2. *Corrupted Post-v2 State Healing & Placeholder Flagging Test (`TST-MIG-041.2`, `SportTypeDatabaseUpgradeTest.kt`)*:
  • Create a database simulating the post-v2 dropped state where custom sports are missing from `SportTypes`, but `WorkoutSummaries` references `sportId = 6` and `sportId = 8`.
  • Execute data healing reconciliation.
  • Assert that placeholder rows are created with `_id = 6` and `_id = 8`, `is_placeholder = 1`, and `UIName` formatted properly.
  • Assert that workouts resolve to these entities instead of falling back to `OTHER`.
  3. *Atomic Transactionality & `sqlite_sequence` Protection Test (`TST-MIG-041.3`, `SportTypeDatabaseUpgradeTest.kt`)*:
  • Following migration and reconciliation, query `sqlite_sequence` for table `SportTypes`.
  • Assert that `seq >= 8`.
  • Insert a new custom sport type and assert that its auto-generated ID is $\ge 9$, eliminating collision with historical workouts.
  4. *UI Isolation & Active Picker Filtering Test (`TST-MIG-041.4`, `SportTypeDatabaseUpgradeTest.kt`)*:
  • Query available sport types through active tracking picker selection.
  • Assert that resurrected sport types with `is_placeholder == 1` are excluded from new workout selection, while non-placeholder sports remain available.
  5. *Strict Downgrade Guard Rejection Test (`TST-MIG-041.5`, `SportTypeDatabaseUpgradeTest.kt`)*:
  • Invoke `onDowngrade(db, 3, 2)`.
  • Assert that `SQLiteException` is thrown, the table is not dropped, and all data remains intact.
  6. *Clean-Room Full Suite Regression Execution (`TST-MIG-041.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-DAT-023: **UTC-Compliant Binary FIT Export, Transient Lap Synthesis, Fail-Fast Error Propagation, Pause Handling & File Cleanup Verification Suite** [True UTC timestamps under all device timezones via DateTimeFormatter, transient lap synthesis with timer consistency without DB mutation, fail-fast error propagation, partial file deletion on failure, pause support, and historical re-export verified in FitFileWriterTest and full clean-room suite.]
- **Requirement(s)**: `REQ-DAT-028`
- **Component**: **UTC-Compliant Binary FIT Export, Transient Lap Synthesis, Fail-Fast Error Propagation, Pause Handling & File Cleanup Verification Suite**
- **Jira**: ``ATT-3994``
- **Test Description**:
  1. *UTC Timezone Invariant & Offset Independence Test with DateTimeFormatter (`TST-DAT-023.1`, `FitFileWriterTest.kt`)*:
  • Execute FIT export under simulated non-UTC default timezones (`GMT+2`, `GMT+9`, `GMT-5`).
  • Verify via Garmin FIT SDK `Decode` that `FileIdMesg.timeCreated`, `RecordMesg.timestamp`, `LapMesg.startTime`, and `SessionMesg.startTime` exactly equal the database UTC timestamp without timezone offset shifting using thread-safe `DateTimeFormatter`.
  2. *Transient Lap Synthesis for Lap-Less Workouts & Timer Consistency Test (`TST-DAT-023.2`, `FitFileWriterTest.kt`)*:
  • Configure mock workout with 0 laps in `LapsDatabaseManager`.
  • Execute FIT export.
  • Verify `lapMesgs.size == 1`, start time matches `startDate`, duration matches `totalTimerSec`, distance matches `distTotal`, `sessionMesg.numLaps == 1`, and `total_timer_time` / `total_elapsed_time` align per FIT SDK 2.0.
  • Verify zero writes or mutations to `Laps.db`.
  3. *Fail-Fast on Corrupted / Missing StartTime Test (`TST-DAT-023.3`, `FitFileWriterTest.kt`)*:
  • Provide summary with null, blank, or unparseable `timeStart`.
  • Execute FIT export.
  • Verify export returns `success == false` and does not fall back to `new Date()`.
  4. *Fail-Fast on Sample Loop Read Exception Test (`TST-DAT-023.4`, `FitFileWriterTest.kt`)*:
  • Simulate an SQLite cursor exception mid-traversal in `samplesDb.query`.
  • Execute FIT export.
  • Verify export returns `success == false` with error message, preventing truncated file publication.
  5. *Historical Workout Re-Export Ground Truth Test (`TST-DAT-023.5`, `FitFileWriterTest.kt`)*:
  • Re-export a workout recorded prior to the fix.
  • Verify exported FIT timestamps match ground truth UTC database records and database state remains unchanged.
  6. *Partial File Cleanup on Export Failure Test (`TST-DAT-023.6`, `FitFileWriterTest.kt`)*:
  • Simulate export failure mid-stream or during validation.
  • Verify that any partially written `.fit` file is completely deleted from the output directory (`outputFile.exists() == false`), and failure to delete is surfaced in the error message.
  7. *Negative Integration Test on Inconsistent Metrics (`TST-DAT-023.7`, `FitFileWriterTest.kt`)*:
  • Provide summary with negative `totalTime` or corrupted summary metrics.
  • Verify export fails fast with a specific error without synthesizing an invalid lap.
  8. *Workout with Significant Pauses Verification Test (`TST-DAT-023.8`, `FitFileWriterTest.kt`)*:
  • Simulate workout containing substantial pause duration (e.g. 10 minutes) where active `totalTimerTime` < wall-clock `totalElapsedTime`.
  • Verify export succeeds and encodes `totalTimerTime` and `totalElapsedTime` correctly without rejection.
  9. *Clean-Room Full Suite Regression Execution (`TST-DAT-023.9`)*:
  • Run `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-DAT-024: **Resilient Export Pipeline, Attempt Capping, Cooperative Cancellation, Transactional Upload Remediation & Stuck State Recovery Verification Suite** [Deterministic vs transient error classification, retry capping at 3 attempts, uncaught runtime exception fail-safe, transactional dependent upload failure, cooperative cancellation in sample loops, WorkManager unique work and invalidation tags, safe workout deletion, and stuck state self-healing with decoupled non-blocking double-checked liveness guard and attempt ID matching verified across test suite.]
- **Requirement(s)**: `REQ-DAT-029`
- **Component**: **Resilient Export Pipeline, Attempt Capping, Cooperative Cancellation, Transactional Upload Remediation & Stuck State Recovery Verification Suite**
- **Jira**: ``ATT-3995``
- **Test Description**:
  1. *Deterministic Errors Non-Retryable Unit Test (`TST-DAT-024.1`, `BaseExporterTest.kt`)*:
  • Configure `BaseExporter` with mocked `doExport` throwing `ParseException`, `JSONException`, and `IllegalArgumentException`.
  • Verify `export(exportInfo)` returns `ExportResult(success=false, shallRetry=false)`.
  2. *Transient IOException Retryable Unit Test (`TST-DAT-024.2`, `BaseExporterTest.kt`)*:
  • Configure `BaseExporter` with `doExport` throwing transient `IOException("Connection reset")`.
  • Verify `export(exportInfo)` returns `ExportResult(success=false, shallRetry=true)`.
  3. *ExportWorker Max Retries Enforcement Test (`TST-DAT-024.3`, `ExportWorkerTest.kt`)*:
  • Instantiate `ExportWorker` with `runAttemptCount = 3` and exporter returning `shallRetry = true`.
  • Execute `doWork()`.
  • Verify worker returns `Result.failure()`, sets `FINISHED_FAILED` in database, and halts retry loops.
  4. *ExportWorker Uncaught RuntimeException Fail-Safe Test (`TST-DAT-024.4`, `ExportWorkerTest.kt`)*:
  • Simulate an uncaught `RuntimeException` or `IllegalStateException` during exporter execution.
  • Execute `doWork()`.
  • Verify exception is caught, database status transitions to `FINISHED_FAILED`, and `Result.failure()` is returned.
  5. *Transactional Dependent Upload Remediation Test (`TST-DAT-024.5`, `ExportStatusDatabaseManagerTest.kt`)*:
  • Populate `ExportStatus.db` with `FILE` row and dependent `DROPBOX` / `GOOGLE_DRIVE` rows in `WAITING`.
  • Call `markDependentUploadsFailed(fileBaseName, fileFormat, errorMsg)`.
  • Verify dependent upload rows are atomically updated to `FINISHED_FAILED` inside SQLite transaction.
  6. *Cooperative Cancellation & Partial File Deletion Test (`TST-DAT-024.6`, `BaseExporterTest.kt`)*:
  • Set `CancellationSignal` returning `true` during file writing.
  • Verify sample loop aborts with `CancellationException` at start of loop iteration, partial output file is deleted, and `ExportResult(false, false, "Export cancelled")` is returned.
  7. *ExportManager Unique Work & Tagged Invalidation Test (`TST-DAT-024.7`, `ExportManagerTest.kt`)*:
  • Verify `ExportManager.startFullExportProcess` enqueues unique work `getUniqueWorkName(fileBaseName, fileFormat)` with `REPLACE` policy and tags `fileBaseName` and `exportInfo.toString()`.
  • Verify `invalidateWorkoutExport` cancels all matching tags and unique names before resetting DB.
  8. *WorkoutDeletionHelper Safe Deletion Test (`TST-DAT-024.8`, `WorkoutDeletionHelperTest.kt`)*:
  • Delete workout with blank or `"null"` base file name.
  • Verify deletion succeeds without `IllegalArgumentException` and cancels pending WorkManager tasks.
  9. *9-Language Localization & Specifier Audit (`TST-DAT-024.9`)*:
  • Verify string presence and format specifier consistency across all 9 locales.
  10. *Clean-Room Full Suite Regression Execution (`TST-DAT-024.10`)*:
  • Run `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.
  11. *Stuck State Self-Healing Recovery Test (`TST-DAT-024.11`, `ExportStatusDatabaseManagerTest.kt`)*:
  • Insert export row in `PROCESSING` with `last_updated` older than 15 minutes of silence.
  • Assert row is lazily transitioned to `FINISHED_FAILED` on query while recent rows are untouched.
  12. *Active Long-Running Export Heartbeat Preservation Test (`TST-DAT-024.12`, `ExportStatusDatabaseManagerTest.kt`)*:
  • Assert export running for > 15 minutes with active periodic heartbeat updates remains in `PROCESSING`.
  13. *Input Sanitization at Entry Test (`TST-DAT-024.13`, `ExportManagerTest.kt`)*:
  • Call `exportWorkoutTo` or `newWorkout` with blank or `"null"` `fileBaseName`.
  • Verify input is rejected and zero invalid rows are written to `ExportStatus.db`.
  14. *Late-Start & Concurrent Heartbeat Race Resilience Test (`TST-DAT-024.14`, `ExportStatusDatabaseManagerTest.kt`)*:
  • Simulate concurrent worker heartbeat update while WorkManager liveness check is in flight.
  • Verify recovery check detects updated heartbeat on re-verification and aborts failure transition, keeping `PROCESSING` state.
  15. *Active Export with Recent Heartbeat Not Transitioned Test (`TST-DAT-024.15`, `ExportStatusDatabaseManagerTest.kt`)*:
  • Verify that an ongoing export with a recent heartbeat is NOT transitioned to FAILED by the recovery logic, even if total duration exceeds the threshold.
  16. *Worker I/O In-Flight Grace Period & Attempt ID Match Test (`TST-DAT-024.16`, `ExportStatusDatabaseManagerTest.kt`)*:
  • Verify that recovery logic does not terminate a worker currently performing I/O within the grace period or when attempt count has progressed.

### TST-MIG-042: **Real SQLite Robolectric Database Migration & Data Preservation Verification Suite** [Real SQLite execution via Robolectric verifying multi-step migration chains, table preservation, column creation with defaults, and data integrity across WorkoutSummaries, SportType, Equipment, Routes, and TrackingViews.]
- **Requirement(s)**: `REQ-MIG-045`
- **Component**: **Real SQLite Robolectric Database Migration & Data Preservation Verification Suite**
- **Jira**: ``ATT-4005``
- **Test Description**:
  1. *WorkoutSummaries Real SQLite Upgrade Chain & Data Preservation Test (`TST-MIG-042.1`, `WorkoutSummariesRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database with v22 schema and insert pre-existing workout rows.
  • Upgrade through `WorkoutSummariesDbHelper.onUpgrade(db, 22, 25)`.
  • Verify via `PRAGMA table_info` that `race`, `source`, and `count` columns exist.
  • Verify pre-existing workouts retain ID, name, timestamps, and receive `race = 0`, `source = 'TRACKED'`.
  • Verify new rows can be inserted and queried with all v25 columns.
  2. *SportType Real SQLite Non-Destructive Upgrade & Custom Sport Preservation Test (`TST-MIG-042.2`, `SportTypeRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database with v1 schema containing default sports 1..5 (`WALK` with `baseSportType = 'UNKNOWN'`) and custom user sport (`_id = 6`, `UIName = 'Rollerblading'`).
  • Execute `SportTypeDbHelper.onUpgrade(db, 1, 3)`.
  • Assert `SportTypes` table was NOT dropped.
  • Assert custom sport `_id = 6` remains present with exact ID and name.
  • Assert `WALK` (`_id = 4`) has `baseSportType = 'RUN'`.
  • Assert `is_placeholder` column is added and defaults to `0`.
  3. *Equipment Real SQLite Upgrade & Retired Column Addition Test (`TST-MIG-042.3`, `EquipmentRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database with v1 schema containing bikes, shoes, and links.
  • Execute `EquipmentDbHelper.onUpgrade(db, 1, 2)`.
  • Assert `Retired` column exists via `PRAGMA table_info`.
  • Assert existing equipment entries remain intact with `Retired = 0`.
  4. *Routes Real SQLite Upgrade & Waypoints Table Creation Test (`TST-MIG-042.4`, `RoutesRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database with v5 schema containing routes and route points.
  • Execute `RoutesDbHelper.onUpgrade(db, 5, 10)`.
  • Assert `route_waypoints` table and spatial bounds columns exist.
  • Assert pre-existing routes remain intact and accessible.
  5. *TrackingViews Real SQLite Upgrade & SectionDividers Table Creation Test (`TST-MIG-042.5`, `TrackingViewsRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database with v13 schema containing cockpits and rows.
  • Execute `TrackingViewsDbHelper.onUpgrade(db, 13, 14)`.
  • Assert `SectionDividersTable` exists in SQLite.
  • Assert existing cockpit tabs and rows are preserved.
  6. *Clean-Room Full Suite Regression Execution (`TST-MIG-042.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across all unit and Robolectric tests.

### TST-MIG-043: **Legacy SQLite Migration Hardening, Fast onUpgrade DDL, Background Backfill Convergence, and DAL Guard Verification Suite** [Legacy v3->v25 and v10->v25 upgrade with quoted defaults, deterministic cluster column additions, non-blocking fast DDL onUpgrade, idempotent background backfill with interruption resilience, and DAL on-demand fallbacks verified.]
- **Requirement(s)**: `REQ-MIG-046`
- **Component**: **Legacy SQLite Migration Hardening, Fast onUpgrade DDL, Background Backfill Convergence, and DAL Guard Verification Suite**
- **Jira**: ``ATT-4009``
- **Test Description**:
  1. *Legacy v3 -> v25 Real SQLite Migration Test (`TST-MIG-043.1`, `WorkoutSummariesRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database at schema version 3 with baseline workout summaries.
  • Upgrade database via `onUpgrade(db, 3, 25)`.
  • Assert DDL completes without exception, all version 25 columns exist, and legacy rows are preserved.
  2. *Legacy v10 -> v25 Syntax & Column Guard Test (`TST-MIG-043.2`, `WorkoutSummariesRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database at schema version 10 containing `WorkoutNamePatterns` table (`sportId int`).
  • Upgrade database via `onUpgrade(db, 10, 25)`.
  • Assert `DEFAULT '???'` and `WorkoutNamePatterns` checks execute without `sqlite3.OperationalError` or `SQLiteException`.
  3. *Cluster Migration v6 -> v11 Deterministic Column Test (`TST-MIG-043.3`, `WorkoutClusterRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database at cluster schema version 6.
  • Upgrade database via `onUpgrade(db, 6, 11)`.
  • Assert all spatial bounds, altitude extrema, and counter columns are added via `addColumnIfNotExists` without swallowed exceptions.
  4. *Non-Blocking Fast Database Open Test (`TST-MIG-043.4`, `WorkoutSummariesRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database at v16 with 200 mock workouts.
  • Trigger `onUpgrade(db, 16, 25)`.
  • Assert `onUpgrade` completes in $< 50\text{ ms}$ on the calling thread without opening `WorkoutSamples.db` or decoding polylines.
  5. *Idempotent Background Backfill Execution & Interruption Test (`TST-MIG-043.5`, `DatabaseBackfillManagerTest.kt`)*:
  • Populate database with workouts having unpopulated spatial bounds and extrema without coordinates.
  • Execute backfill routine, simulate interruption, resume to completion, asserting 100% convergence with zero duplicates.
  6. *DAL Graceful Degradation & On-Demand Fallback Test (`TST-MIG-043.6`, `WorkoutSummariesDatabaseManagerTest.kt`)*:
  • Query unmigrated workouts with `boundMinLat = NULL` and empty stream strings, asserting safe fallback and zero crashes.
  7. *9-Language Localization Audit (`TST-MIG-043.7`)*:
  • Audit `res/values*/strings.xml` confirming zero string modifications.
  8. *Clean-Room Full Suite Regression Execution (`TST-MIG-043.8`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-DAT-025: **Google Drive Query Escaping, Account Cache Isolation, Atomic Download Staging & Transparent Scan Results Verification Suite** [Google Drive query escaping, account cache isolation, bounded 404 self-healing, atomic download staging with durability flush, and transparent recursive listing error reporting verified across test suite.]
- **Requirement(s)**: `REQ-DAT-030`
- **Component**: **Google Drive Query Escaping, Account Cache Isolation, Atomic Download Staging & Transparent Scan Results Verification Suite**
- **Jira**: ``ATT-4014``
- **Test Description**:
  1. *Query Builder Escaping Unit Test (`TST-DAT-025.1`, `GoogleDriveClientTest.kt`)*:
  • Verify `GoogleDriveQueryBuilder.escapeQueryValue` escapes single quotes (`\'`) and backslashes (`\\`) correctly.
  2. *Query URL Encoding Unit Test (`TST-DAT-025.2`, `GoogleDriveClientTest.kt`)*:
  • Verify `ensureFolderHierarchy` encodes escaped queries in network calls when folder names contain quotes/backslashes.
  3. *Account Cache Isolation Unit Test (`TST-DAT-025.3`, `GoogleDriveClientTest.kt`)*:
  • Verify separate `GoogleDriveClient` instances with different account identifiers maintain isolated folder IDs without cache collision.
  4. *Atomic Eviction Unit Test (`TST-DAT-025.4`, `GoogleDriveClientTest.kt`)*:
  • Verify `client.evictFolder(folderId)` removes matching hierarchy entries from the encapsulated cache.
  5. *Bounded Self-Healing 404 Re-creation Unit Test (`TST-DAT-025.5`, `GoogleDriveUploaderTest.kt`)*:
  • Verify `GoogleDriveUploader` handles HTTP 404 by evicting stale folder ID, calling `ensureFolderHierarchy`, and retrying upload once.
  6. *Atomic Staging & Interruption Cleanup Unit Test (`TST-DAT-025.6`, `GoogleDriveClientTest.kt`)*:
  • Verify `downloadFileById` aborts on stream error, deletes the temporary `.part_*` file, and leaves destination file untouched.
  7. *Atomic Move & Durability Flush Unit Test (`TST-DAT-025.7`, `GoogleDriveClientTest.kt`)*:
  • Verify successful download durably writes and renames temporary file to destination file without leaving `.part_*` files.
  8. *Stale Part File 24h Purge Unit Test (`TST-DAT-025.8`, `GoogleDriveClientTest.kt`)*:
  • Verify `downloadFileById` purges `.part_*` files older than 24 hours while keeping recent ones.
  9. *Incomplete Recursive Listing Surface Unit Test (`TST-DAT-025.9`, `GoogleDriveClientRecursiveListingTest.kt`)*:
  • Verify `listFilesRecursivelyResult` returns `RecursiveScanResult` with `isComplete = false` and descriptive error on network/HTTP failure.
  10. *Successful Recursive Listing Unit Test (`TST-DAT-025.10`, `GoogleDriveClientRecursiveListingTest.kt`)*:
  • Verify `listFilesRecursivelyResult` returns all files with `isComplete = true` and `error = null`.
  11. *9-Language Localization & Specifier Audit (`TST-DAT-025.11`, `TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 locales for Google Drive error strings.
  12. *Clean-Room Full Regression Suite (`TST-DAT-025.12`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-DAT-026: **Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle Verification Suite** [Storage space pre-verification, API 30+ VACUUM INTO atomic snapshot under concurrent writes, API 26-29 fallback checkpoint with retry backoff, thread-safe singleton helper lifecycle, zero leaked connections, and clean-room full suite verified.]
- **Requirement(s)**: `REQ-DAT-031`
- **Component**: **Atomic Concurrent-Safe Database Backup Snapshots, Storage Verification, and Singleton Helper Lifecycle Verification Suite**
- **Jira**: ``ATT-4011``
- **Test Description**:
  1. *Storage Space Verification & Guard Test (`TST-DAT-026.1`, `BackupManagerTest.kt`)*:
  • Mock context with `filesDir.usableSpace` less than 1.5x active database sizes.
  • Invoke `BackupManager.createBackup(context)`.
  • Verify method returns `null` and zero staging or temp files remain.
  2. *API 30+ VACUUM INTO Real SQLite Snapshot Test (`TST-DAT-026.2`, `BackupManagerRealSqliteTest.kt`)*:
  • Create real SQLite database in WAL mode with uncheckpointed write transactions.
  • Execute snapshot via `VACUUM INTO`.
  • Verify snapshot passes `PRAGMA integrity_check` returning `"ok"` and all committed rows exist without `-wal` file.
  3. *API 26–29 Fallback Checkpoint & Retry on BUSY Test (`TST-DAT-026.3`, `BackupManagerTest.kt`)*:
  • Configure database returning `BUSY` on initial checkpoint, succeeding on retry.
  • Verify checkpoint retries with exponential backoff and staging captures clean database files.
  4. *Concurrent Writer Non-Interference & Integrity Test (`TST-DAT-026.4`, `BackupManagerConcurrencyTest.kt`)*:
  • Backing `WorkoutSamples.db` with active background writes while `createBackup()` executes.
  • Verify writer completes without locking errors and snapshot passes `PRAGMA integrity_check`.
  5. *Singleton Helper Instance Identity & Reset Test (`TST-DAT-026.5`, `DbHelperSingletonTest.kt`)*:
  • Call `getInstance(context)` on `StravaUploadDbHelper`, `EquipmentDbHelper`, and `ActiveDevicesDbHelper` across threads.
  • Verify identical reference returned, and `resetForTesting()` allows clean recreation.
  6. *Zero Leaked Helpers in DatabaseConnectionManager Test (`TST-DAT-026.6`, `DatabaseConnectionManagerTest.kt`)*:
  • Track helper allocations before and after calling `quiesceAllDatabases(context, closeConnections = false)`.
  • Verify zero new `SQLiteOpenHelper` instances instantiated and no open connections leaked.
  7. *9-Language Localization & Specifier Audit (`TST-DAT-026.7`)*:
  • Verify zero string modifications across all 9 application locales.
  8. *Clean-Room Full Suite Regression Execution (`TST-DAT-026.8`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-DAT-027: **OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Verification Suite** [Dedicated credentials store with legacy migration, Auto Backup and device-transfer exclusion rules, .attbackup credential exclusion, restore sanitization, FileProvider hardening, guarded BackupWorker cloud checks, and self-healing preference cleanup verified across test suite.]
- **Requirement(s)**: `REQ-DAT-032`
- **Component**: **OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Verification Suite**
- **Jira**: ``ATT-4023`, `ATT-4459``
- **Test Description**:
  1. *Legacy Credential Migration Test (`TST-DAT-027.1`, `AuthCredentialsStoreTest.kt`)*:
  • Populate default `SharedPreferences` with Strava tokens, athlete ID, OAuth state, Google Drive email/token, and Dropbox credential.
  • Execute `AuthCredentialsStore.migrateLegacyCredentials(context)`.
  • Verify all credentials exist in `auth_credentials_prefs` and are completely purged from default `SharedPreferences`.
  2. *Credential Read & Write Delegation Test (`TST-DAT-027.2`, `AuthCredentialsStoreTest.kt`)*:
  • Verify `TrainingApplication` getters/setters for Strava, Google Drive, and Dropbox credentials read and write from `AuthCredentialsStore` without writing to default `SharedPreferences`.
  3. *Android Auto Backup XML Rules Validation Test (`TST-DAT-027.3`, `BackupRulesXmlValidationTest.kt`)*:
  • Parse `data_extraction_rules.xml` and `full_backup_content.xml`.
  • Verify `auth_credentials_prefs.xml` is excluded from cloud backup, device transfer, and full backup domains.
  4. *Backup Archive Bundling Exclusion Test (`TST-DAT-027.4`, `BackupManagerCredentialExclusionTest.kt`)*:
  • Create mock storage with `auth_credentials_prefs.xml` populated with tokens.
  • Run `BackupManager.createBackup(context)`.
  • Assert `shared_prefs/auth_credentials_prefs.xml` is absent from the `.attbackup` archive, and zero tokens exist in the zip.
  5. *Restore Engine Sanitization & Re-Auth Test (`TST-DAT-027.5`, `MigrationEngineCredentialSanitizationTest.kt`)*:
  • Restore an archive containing legacy tokens in default preferences and a mock `auth_credentials_prefs.xml`.
  • Assert `auth_credentials_prefs.xml` was discarded, legacy token keys were stripped from default preferences, and cloud status requires re-authentication.
  6. *FileProvider Hardening Contract Test (`TST-DAT-027.6`, `FileProviderHardeningContractTest.kt`)*:
  • Verify `GenericFileProvider` and `provider_paths.xml` do not exist, and `file_paths.xml` contains zero `path="."` root exposure.
  7. *Clean-Room Regression Suite Execution (`TST-DAT-027.7`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.
  8. *Legacy Key Normalization & Dual-Key Migration Test (`TST-DAT-027.8`, `AuthCredentialsStoreTest.kt`)*:
  • Populate default `SharedPreferences` with legacy keys `"dropboxCredential"`, `"googleDriveAccountEmail"`, `"SP_STRAVA_OAUTH_STATE"`, `"SP_STRAVA_OAUTH_STATE_TIMESTAMP"`.
  • Execute `AuthCredentialsStore.migrateLegacyCredentials(context)`.
  • Verify legacy keys are mapped to canonical keys in `auth_credentials_prefs` (`KEY_DROPBOX_CREDENTIAL`, `KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL`, `KEY_STRAVA_OAUTH_STATE`, `KEY_STRAVA_OAUTH_STATE_TIMESTAMP`) and purged from default `SharedPreferences`.
  • Verify `readDropboxCredential()` successfully reads legacy or canonical keys.
  9. *Guarded BackupWorker Cloud Provider Check Test (`TST-DAT-027.9`, `BackupWorkerTest.kt`)*:
  • Set `uploadToDropbox = true` with missing Dropbox credential (`readDropboxCredential() == null`).
  • Execute `BackupWorker.doWork()`.
  • Verify Dropbox upload is skipped and backup completes as `LOCAL_SUCCESS` (or `SUCCESS` if Google Drive succeeds) rather than `PARTIAL_SUCCESS`.
  10. *Defensive Self-Healing Preferences Test (`TST-DAT-027.10`, `MainActivityWithNavigationTest.kt`)*:
  • Configure `uploadToDropbox = true` with null Dropbox credential.
  • Invoke `checkPreferences()`.
  • Verify `uploadToDropbox` is automatically reset to `false`.

### TST-DAT-028: **High-Performance Batch Workout Mapping, Non-Blocking Cluster Equipment Resolution & Consolidated Samples DB Telemetry Loading Verification Suite** [Batch workout mapping eliminates N+1 queries and read-time writes, cluster filtering operates non-blocking in-memory on UI thread, samples queries quote column identifiers and eliminate redundant table scans, and 100% clean-room test pass rate is achieved.]
- **Requirement(s)**: `REQ-DAT-033`, `REQ-DAT-009`, `REQ-MAP-020`
- **Component**: **High-Performance Batch Workout Mapping, Non-Blocking Cluster Equipment Resolution & Consolidated Samples DB Telemetry Loading Verification Suite**
- **Jira**: ``ATT-4003, ATT-4707``
- **Test Description**:
  1. *Batch Workout Mapping & Symmetry Contract Tests (`TST-DAT-028.1`, `WorkoutDataMappingSymmetryTest.kt`, `PeriodsRepositorySyncTest.kt`)*:
  • Verify `PeriodsRepository.getWorkoutsForRange` maps workouts via `RawCursorSnapshot` + `BatchMetadata` + `fromSnapshot` without invoking single-row `fromCursor` loop.
  • Verify `fromSnapshot` with expanded `BatchMetadata` (sport types, equipment names, locations, athlete metrics) produces identical `WorkoutData` fields to `fromCursor` (Rule 20).
  • Verify zero SQLite writes occur during batch cursor iteration over corrupted/legacy extrema.
  2. *Cluster Equipment Non-Blocking In-Memory Filter Tests (`TST-DAT-028.2`, `WorkoutClustersViewModelTest.kt`)*:
  • Verify `WorkoutClustersViewModel` precomputes/caches sport equipment maps on `Dispatchers.IO`.
  • Verify cluster filtering with active criteria executes without blocking the Main thread or invoking SQLite queries during layout/recomposition.
  • Verify identical filtering results between precomputed and direct lookup.
  3. *Consolidated Projected Samples DB Telemetry & Quoted Column Identifiers Tests (`TST-DAT-028.3`, `WorkoutRepositoryTelemetryTest.kt`, `WorkoutRepositoryStreamTest.kt`, `WorkoutRepositoryProjectionTest.kt`)*:
  • Verify `getWorkoutTrackPoints`, `getHeartRateZoneDistribution`, and `getPowerZoneDistribution` use explicit column projections with double-quoted SQL identifiers (`"\"${col.replace("\"", "\"\"")}\""`).
  • Verify in `WorkoutRepositoryProjectionTest` that querying workout samples tables containing source-qualified location columns (`LATITUDE (Google Fused)`, `LATITUDE (GPS)`, `LATITUDE (Network)`, and matching `LONGITUDE` columns) executes cleanly without `SQLiteException` across all `TrackType` entries (`BEST`, `GPS`, `FUSED`, `NETWORK`) and returns populated `PathPoint` lists.
  • Verify consolidated sample queries return identical track coordinates, bounds, and zone distribution percentages to multi-pass queries.
  4. *Clean-Room Regression Suite Execution (`TST-DAT-028.4`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-DAT-029: **Database Schema Migrations, Index Verification, Routes N+1 Query Batching & Synchronized Transactional Clustering Engine Verification Suite** [Database migrations v11 (Routes), v26 (WorkoutSummaries), and v3 (Laps) verified; missing indexes confirmed; routes N+1 queries eliminated; transactional clustering with in-memory caching and concurrency synchronization verified with 100% test pass rate.]
- **Requirement(s)**: `REQ-DAT-034`
- **Component**: **Database Schema Migrations, Index Verification, Routes N+1 Query Batching & Synchronized Transactional Clustering Engine Verification Suite**
- **Jira**: ``ATT-4008``
- **Test Description**:
  1. *Database Schema Migration & Index Verification Unit Tests (`TST-DAT-029.1`, `DatabaseMigrationIndexTest.kt`)*:
  • Verify `RoutesDbHelper.DB_VERSION == 11`, `WorkoutSummariesDbHelper.DB_VERSION == 26`, and `LapsDbHelper.DB_VERSION == 3`.
  • Verify that on fresh `onCreate`, all target indexes exist: `idx_route_points_route_id` on `route_points(route_id_fk)`, `idx_extrema_workout_sensor_extrema` on `ExtremumValues(workoutId, sensorType, extremaType)`, `idx_summaries_cluster_id` on `WorkoutSummaries(clusterId)`, `idx_summaries_file_base_name_nocase` on `WorkoutSummaries(fileBaseName COLLATE NOCASE)`, and `idx_laps_workout_id` on `Laps(workoutID)`.
  • Verify that upgrading from versions 10 -> 11 (`Routes.db`), 25 -> 26 (`WorkoutSummaries.db`), and 2 -> 3 (`Laps.db`) creates all target indexes idempotently.
  • Verify `EXPLAIN QUERY PLAN` confirms index usage for `route_points` foreign key queries, `ExtremumValues` lookup by workoutId/sensorType/extremaType, and `WorkoutSummaries` clusterId / fileBaseName lookups.
  2. *Routes N+1 Query Elimination & Batch Loading Unit Tests (`TST-DAT-029.2`, `RoutesDatabaseManagerOptimizationTest.kt`)*:
  • Verify `getAllRouteSummaries()` returns route summaries without loading coordinates into memory.
  • Verify `getAllRoutes()` returns identical `RouteWithPath` results with batched queries compared to legacy per-route loading.
  3. *Transactional Batching & In-Memory Cluster Caching Unit Tests (`TST-DAT-029.3`, `WorkoutClusterEngineOptimizationTest.kt`)*:
  • Verify `migrateHistory()` executes within an enclosing SQLite transaction without per-row journal syncs.
  • Verify `migrateHistory()` caches clusters in memory and avoids repetitive `getAllClusters()` database queries during candidate evaluation.
  4. *Concurrent Mutation Synchronization Unit Tests (`TST-DAT-029.4`, `WorkoutClusterEngineOptimizationTest.kt`)*:
  • Verify concurrent invocations of `migrateHistory`, `learnFromWorkout`, and `assignClusterToWorkout` across parallel threads execute serially without data races, deadlocks, or duplicate cluster creation.
  5. *Clean-Room Regression Suite Execution (`TST-DAT-029.5`)*:
  • Execute targeted database tests and verify 100% test pass rate across the full project test suite.

### TST-DAT-030: **Nullable Altitude Telemetry, Sea-Level Fidelity (0.0 m), Crop Stream Gap Resilience & Extrema Reconciliation Integrity Verification Suite** [Nullable altitude handling verified: missing altitude represented as null, legitimate sea-level (0.0 m) workouts preserved, crop downsampling canyon plunge eliminated, extrema reconciliation guarded against stream gap corruption, and 100% unit tests pass.]
- **Requirement(s)**: `REQ-DAT-035`
- **Component**: **Nullable Altitude Telemetry, Sea-Level Fidelity (0.0 m), Crop Stream Gap Resilience & Extrema Reconciliation Integrity Verification Suite**
- **Jira**: ``ATT-4017``
- **Test Description**:
  1. *Repository Nullable Altitude Extraction & Telemetry Filter Unit Tests (`TST-DAT-030.1`, `WorkoutRepositoryTelemetryTest.kt`)*:
  • Verify `getWorkoutTrackPoints` and `getWorkoutTelemetryPoints` return `PathPoint.altitude == null` when cursor altitude is null.
  • Verify `getWorkoutTelemetryPoints` retains samples with sea-level altitude (`0.0 m`) even when HR, power, and speed are null.
  • Verify samples with null altitude and null other metrics are excluded.
  2. *Crop Workout Downsampling & Canyon Plunge Prevention Unit Tests (`TST-DAT-030.2`, `WorkoutRepositoryCropTest.kt` / `WorkoutRepositoryStreamTest.kt`)*:
  • Verify `cropWorkout` downsamples altitudes without injecting `0.0` for missing samples.
  • Verify cropped workout with missing altitude does not produce 0 m plunge artifacts in `ALTITUDE_STREAM`.
  • Verify workouts entirely lacking altitude omit `ALTITUDE_STREAM` (`encodedAltitudes == null`).
  3. *Authoritative Extrema Reconciliation Guard Unit Tests (`TST-DAT-030.3`, `WorkoutDataMapperAltitudeTest.kt`)*:
  • Verify `reconcileAltitudeExtrema` does not overwrite positive `recordedMin` (e.g. 450 m) with 0.0 when stream contains zero-gap artifacts.
  • Verify genuine sea-level workouts (`streamMin == 0.0` across contiguous samples) reconcile correctly to 0.0 m.
  • Verify healing of historical GPS startup spikes (`recordedMin < streamMin - 15.0`) remains 100% functional.
  4. *Trackless Aftermath Elevation Visibility Contract Tests (`TST-DAT-030.4`, `TracklessAftermathVisualContractTest.kt`)*:
  • Verify `TrackOnMapScreen` displays elevation profile for trackless workouts with genuine sea-level altitude (`0.0 m`).
  • Verify `TrackOnMapScreen` suppresses elevation profile when all altitude samples are `null`.
  5. *Elevation Smoothing & Bounds Gap Handling Unit Tests (`TST-DAT-030.5`, `ElevationSmoothingMathTest.kt`, `ElevationProfileBoundsTest.kt`)*:
  • Verify `smoothAltitudes` and `calculateElevationBounds` ignore or interpolate `null`/`NaN` points without collapsing minimum bounds to 0.0 m.
  6. *Clean-Room Regression Suite Execution (`TST-DAT-030.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across the full test suite.

### TST-DAT-031: **Granular Dropbox Sync Preferences, Wi-Fi Network Constraints & Sync Timestamp Verification Suite** [Granular Dropbox preferences, isolated email storage in AuthCredentialsStore, export and backup gating parity with Google Drive, dynamic Wi-Fi network constraints, sync timestamp updates, and error resilience verified.]
- **Requirement(s)**: `REQ-DAT-032`, `REQ-MIG-036`
- **Component**: **Granular Dropbox Sync Preferences, Wi-Fi Network Constraints & Sync Timestamp Verification Suite**
- **Jira**: ``ATT-4665``
- **Test Description**:
  1. *TrainingApplication Dropbox Preferences Unit Tests (`TST-DAT-031.1`, `TrainingApplicationDropboxPreferencesTest.kt`)*:
  • Verify default values: `uploadWorkoutsToDropbox() == true`, `uploadBackupToDropbox() == true`, `uploadToDropboxOnlyOnWifi() == true`, `getDropboxLastSyncTimestamp() == 0L`.
  • Verify getters and setters persist changes to `cSharedPreferences`.
  • Verify `getDropboxAccountEmail()` and `setDropboxAccountEmail()` store email in `AuthCredentialsStore.KEY_DROPBOX_ACCOUNT_EMAIL` (`auth_credentials_prefs.xml`) and purge on `deleteDropboxCredential()`.
  2. *ExportManager Dropbox Gating & Wi-Fi Constraints Unit Tests (`TST-DAT-031.2`, `ExportManagerDropboxGatingTest.kt`)*:
  • Verify Dropbox export work request is created when `uploadToDropbox() == true && uploadWorkoutsToDropbox() == true`.
  • Verify Dropbox export work request is skipped when `uploadWorkoutsToDropbox() == false`.
  • Verify `ExportManager.createWorkRequest()` sets `NetworkType.UNMETERED` when `uploadToDropboxOnlyOnWifi() == true` and `NetworkType.CONNECTED` when `false`.
  3. *BackupWorker & BackupRestoreViewModel Dropbox Gating & Constraints Unit Tests (`TST-DAT-031.3`, `BackupWorkerDropboxGatingTest.kt`)*:
  • Verify `BackupWorker.doWork()` and `BackupRestoreViewModel.uploadToCloud()` evaluate `dropboxConnected = uploadToDropbox() && uploadBackupToDropbox() && readDropboxCredential() != null`.
  • Verify `BackupWorker.buildConstraints()` sets `NetworkType.UNMETERED` when any active cloud backup provider requires Wi-Fi only, `NetworkType.CONNECTED` when cloud backup is enabled without Wi-Fi requirement, and `NetworkType.NOT_REQUIRED` when no cloud backup is enabled.
  4. *Dropbox Sync Timestamp & Defensive Account Profile Unit Tests (`TST-DAT-031.4`, `DropboxSyncTimestampTest.kt`)*:
  • Verify `DropboxUploader.uploadFile()` and `DropboxBackupManager.uploadBackup()` update `setDropboxLastSyncTimestamp(System.currentTimeMillis())` upon successful upload.
  • Verify `DbxClientV2.users().currentAccount` failure throws are caught defensively, returning `null` without crashing or failing authentication.
  5. *Clean-Room Regression Suite & Translation Parity Execution (`TST-DAT-031.5`)*:
  • Execute targeted tests, `TranslationParityTest`, and `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-MIG-044: **SecureZipExtractor Symlinked Parent Directory Extraction & Symlink Rejection Boundary Verification Suite** [SecureZipExtractor canonical destination resolution verified: valid archives extract cleanly through symlinked parent directories (/data/user/0 -> /data/data), internal symlinks and zip-slip traversal remain strictly blocked, and 100% clean-room test pass rate.]
- **Requirement(s)**: `REQ-MIG-047`, `REQ-MIG-040`
- **Component**: **SecureZipExtractor Symlinked Parent Directory Extraction & Symlink Rejection Boundary Verification Suite**
- **Jira**: ``ATT-4592``
- **Test Description**:
  1. *Symlinked Parent Directory Valid Archive Extraction Test (`TST-MIG-044.1`, `BackupImportSecurityTest.kt`)*:
  • Create a physical destination directory `real_dest` and a parent symlink `symlink_parent -> physical_parent`.
  • Pass `destDir = File(symlink_parent, "restore_staging")` to `SecureZipExtractor.extractBackupArchive()` with a valid backup archive.
  • Verify extraction succeeds cleanly, all entries are extracted without `SecurityException`, and files are present at the expected real destination.
  2. *Symlinked Parent Directory Traversal & Internal Symlink Rejection Test (`TST-MIG-044.2`, `BackupImportSecurityTest.kt`)*:
  • Using the symlinked parent destination fixture, attempt extraction of an archive containing a relative traversal entry (`../../evil.db`).
  • Verify extraction fails immediately with `SecurityException("Zip-slip path traversal attempt detected")` and destination staging is purged.
  • Attempt extraction of an archive containing an internal symlink entry.
  • Verify extraction fails with `SecurityException("Symbolic links are strictly prohibited in backup archives")`.
  3. *Top-Level Directory Whitelist with Symlinked Parent Test (`TST-MIG-044.3`, `BackupImportSecurityTest.kt`)*:
  • Using the symlinked parent destination fixture, attempt extraction of an archive containing unwhitelisted top-level entries (`malicious/exploit.bin`).
  • Verify extraction throws `SecurityException` indicating invalid backup entry outside whitelisted directories.
  4. *Full Restore & Incremental Import End-to-End Simulation Test (`TST-MIG-044.4`, `BackupImportSecurityTest.kt`)*:
  • Simulate `MigrationEngine.performFullRestore` and `ImportEngine.performIncrementalImport` unpacking into temporary staging paths rooted under a symlinked parent.
  • Verify both engines complete staging extraction without `SecurityException`.
  5. *9-Language Localization Audit (`TST-MIG-044.5`, `TranslationParityTest.kt`)*:
  • Audit `res/values*/strings.xml` verifying 100% parity across all 9 locales (0 new user-facing strings).
  6. *Clean-Room Full Suite Regression Execution (`TST-MIG-044.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

### TST-MIG-045: **Restore Backup Card Button Labels, Icons & Disconnected Visibility Verification Suite** [Restore Backup card button labels unified across all 9 locales, Dropbox restore icon updated to CloudDownload, Google Drive restore rendered unconditionally with dimmed disconnected state, disconnected dialog triggered safely without confirmation dialog, and 100% unit tests pass.]
- **Requirement(s)**: `REQ-MIG-036`
- **Component**: **Restore Backup Card Button Labels, Icons & Disconnected Visibility Verification Suite**
- **Jira**: ``ATT-4664``
- **Test Description**:
  1. *9-Language Localization Parity (`TST-MIG-045.1`, `RestoreCardContractTest.kt` / `TranslationParityTest.kt`)*:
  • Verify all 4 restore button string resources (`restore_automatic_local_backup`, `restore_from_file`, `restore_from_dropbox`, `restore_from_google_drive`) exist across all 9 application locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) with zero missing entries.
  • Verify adherence to the standardized "Restore from ..." / "Aus ... wiederherstellen" nomenclature pattern across all locales.
  2. *RestoreTabContent Layout, Icon & Unconditional Visibility Contract Tests (`TST-MIG-045.2`, `RestoreCardContractTest.kt`)*:
  • Verify Dropbox restore button renders `Icons.Default.CloudDownload` instead of `Icons.Default.CloudUpload`.
  • Verify Google Drive restore button renders unconditionally without `if (isGoogleDriveConnected)` gating.
  • Verify Google Drive restore button applies `googleDriveRestoreColors` and `googleDriveRestoreBorder` (dimmed styling) when `isGoogleDriveConnected == false`.
  3. *ImportBackupTabsScreen Disconnected Google Drive Dialog Trigger Contract Tests (`TST-MIG-045.3`, `RestoreCardContractTest.kt`)*:
  • Verify tapping Google Drive restore when `isGoogleDriveConnected == false` triggers `showGoogleDriveDisconnectedDialog = true` and does NOT launch `showGoogleDriveRestoreConfirm`.
  • Verify tapping Google Drive restore when `isGoogleDriveConnected == true` triggers `showGoogleDriveRestoreConfirm = true`.
  • Verify `showGoogleDriveDisconnectedDialog` renders `AlertDialog` displaying `R.string.google_drive` and `R.string.google_drive_disconnected_status`.
  4. *Clean-Room Regression Suite Execution (`TST-MIG-045.4`)*:
  • Execute targeted migration unit tests and `TranslationParityTest` asserting 100% pass rate.

### TST-DAT-032: **SQLite Schema Evolution, onCreate/onUpgrade Parity & Automated Migration Verification Standard Audit** [Coding standards updated with Section 2.3 SQLite schema migration parity, safe NOT NULL default constraint rules, and automated migration test verification requirements.]
- **Requirement(s)**: `REQ-DAT-036`
- **Component**: **SQLite Schema Evolution, `onCreate`/`onUpgrade` Parity & Automated Migration Verification Standard Audit**
- **Jira**: ``ATT-3534``
- **Test Description**:
  1. *Coding Standards Section 2.3 Verification*:
  • Verify `docs/coding_standards.md` defines §2.3 mandating `onCreate()` and `onUpgrade()` parity for every database version increment.
  2. *Non-Null Safe Default Constraint Specification*:
  • Verify explicit rule requiring safe `DEFAULT` values for new `NOT NULL` columns during `ALTER TABLE` upgrades.
  3. *Automated Migration Unit Test Requirement*:
  • Verify mandated automated schema equivalence unit tests asserting fresh `onCreate()` vs upgraded `onUpgrade()` schema identity.
