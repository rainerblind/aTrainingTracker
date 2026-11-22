# Living Requirements: Persistence, Migration & Cloud Export (Part 2 of 2)

**Document ID**: `DOC-REQ-MIG-P2`  
**Version**: 1.0.37  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `data_migration_export_part2`  
**Prefixes**: `DAT`, `EXP`, `EXT`, `MIG`  
**Scope**: Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export.  
**Total Requirements**: 32

---

### REQ-DAT-033: High-Performance Batch Workout Mapping, Non-Blocking Cluster Equipment Resolution & Consolidated Samples DB Telemetry Loading [Verified]
- **Tests**: `TST-DAT-028`
- **System Requirement(s)**: `SYSREQ-DEX-005`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDataMapper.kt`, `PeriodsRepository.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersViewModel.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `WorkoutRepositoryProjectionTest.kt`
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

### REQ-DAT-034: Database Performance Optimization, Missing Index Migrations, Routes N+1 Query Elimination & Synchronized Transactional Clustering Engine [Verified]
- **Tests**: `TST-DAT-029`
- **System Requirement(s)**: `SYSREQ-DEX-002`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `RoutesDatabaseManager.kt`, `WorkoutSummariesDatabaseManager.java`, `LapsDatabaseManager.java`, `WorkoutClusterEngine.kt`
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

### REQ-DAT-035: Nullable Altitude Telemetry, Sea-Level Fidelity (0.0 m), Crop Stream Gap Resilience & Extrema Reconciliation Integrity [Verified]
- **Tests**: `TST-DAT-030`
- **System Requirement(s)**: `SYSREQ-DEX-005`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Rationale**: The system SHALL support nullable altitude telemetry (`PathPoint.altitude: Double? = null`) to explicitly decouple missing altitude measurements from valid sea-level altitude ($0.0\text{ m}$), eliminate artificial $0.0\text{ m}$ canyon plunge artifacts during workout cropping and downsampling, protect authoritative extrema reconciliation in `WorkoutDataMapper` from resetting valid positive minimum altitudes to $0.0\text{ m}$ upon stream gaps, and render elevation profiles and aftermath statistics accurately for sea-level and trackless workouts (ATT-4017, Implemented in: ATT-4017, Status: Verified, amending `REQ-DAT-009` and `REQ-UI-332`, refining `REQ-UI-126`):
1. *Nullable Altitude DTO & Track Point Extraction (`MapModels.kt`, `WorkoutRepository.kt`, `REQ-DAT-035.1`)*:
• In `MapModels.kt`, `PathPoint.altitude` SHALL be declared as nullable (`val altitude: Double? = null`), permitting call sites to omit altitude or pass `null` when elevation data is unrecorded.
• In `WorkoutRepository.kt` (`getWorkoutTrackPoints` and `getWorkoutTelemetryPoints`), cursor altitude reading SHALL evaluate: `val alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else null`, mapping null database values strictly to `null` rather than `0.0`.
• In `WorkoutRepository.getWorkoutTelemetryPoints`, the sample retention filter SHALL evaluate: `if (hr != null

### REQ-MIG-047: Canonical Destination Resolution, Symlinked Parent Directory Boundary Tolerance & Symlink Rejection Invariant in SecureZipExtractor [Verified]
- **Tests**: `TST-MIG-044`
- **System Requirement(s)**: `SYSREQ-DEX-005`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SecureZipExtractor.kt`, `BackupImportSecurityTest.kt`
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

### REQ-DAT-036: SQLite Schema Evolution, `onCreate`/`onUpgrade` Parity & Automated Migration Verification [Verified]
- **Tests**: `TST-DAT-032`
- **System Requirement(s)**: `SYSREQ-DEX-005`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
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

### REQ-DAT-037: Resilient Google Drive Background Upload Retry, Post-Restore Re-Auth Parity & Stuck Export Recovery [Verified]
- **Tests**: `TST-DAT-033`
- **System Requirement(s)**: `SYSREQ-DEX-001`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `GoogleDriveUploaderTest.kt`, `DropboxUploaderTest.kt`, `GoogleDriveBackupManagerTest.kt`, `AuthCredentialsStoreTest.kt`, `GoogleDriveSettingsDialogTest.kt`, `ExportManagerTest.kt`, `ExportStatusDatabaseManagerTest.kt`, `MainActivityGoogleDriveStartupRecoveryTest.kt`
- **Rationale**: The system SHALL guarantee reliable background cloud workout uploads and backups by attempting silent token acquisition when the cached token is null, preventing zombie connected states post-restore, classifying transient network and rate-limit errors as retryable with WorkManager exponential backoff, and automatically recovering exports previously stuck on credential failures both on re-authentication and asynchronously on application startup (ATT-4922, ATT-5081, Parent Epic ATT-162, amending `REQ-DAT-030` and `REQ-DAT-032`):
1. *Silent Token Acquisition Fallback (`REQ-DAT-037.1`)*:
• When `TrainingApplication.uploadToGoogleDrive()` is enabled (or a signed-in Google account with `DRIVE_FILE_SCOPE` exists) and `TrainingApplication.getGoogleDriveAuthToken()` is null or blank, `GoogleDriveUploader`, `GoogleDriveBackupManager`, `BackupRestoreViewModel`, and `LegacyImportEngine` SHALL attempt silent token refresh via `GoogleDriveAuthManager.refreshTokenSync(context)` before failing with `"Google Drive credential is null or not linked"`.
2. *Post-Restore Zombie-Connected State Elimination (`REQ-DAT-037.2`)*:
• In `AuthCredentialsStore.sanitizeAfterRestore(context)`, the system SHALL verify whether an active Google Sign-In session exists; if unrecoverable or if cloud credentials are wiped without an active session, it SHALL reset `SP_UPLOAD_TO_GOOGLE_DRIVE = false` and `SP_UPLOAD_TO_DROPBOX = false` in default `SharedPreferences` so the UI and `ExportManager` never report cloud providers as connected when credentials have been purged.
• In `GoogleDriveSettingsDialog.kt`, the system SHALL evaluate `isConnected` by checking both `uploadToGoogleDrive()` and credential presence (`getGoogleDriveAuthToken() != null || GoogleSignIn.getLastSignedInAccount(context) != null`), mirroring `DropboxSettingsDialog.kt`.
3. *Transient Error Classification & WorkManager Retry in Cloud Uploaders (`REQ-DAT-037.3`)*:
• In `GoogleDriveUploader.kt`, transient network failures (`IOException`, HTTP 408, HTTP 429, HTTP 5xx, or network-induced null HTTP response code) SHALL return `ExportResult(false, true, ...)` (`shallRetry = true`), allowing `WorkManager` to retry with exponential backoff up to `ExportConfig.MAX_RETRIES`. Non-transient errors (missing local file, unrecoverable 401/403 authorization failures) SHALL return `ExportResult(false, false, ...)` (`shallRetry = false`).
• In `DropboxUploader.java`, transient network errors (`IOException`, `NetworkIOException`, `RetryException`, `ServerException`, `RateLimitException`) SHALL return `ExportResult(false, true, ...)` (`shallRetry = true`), while non-transient errors (missing file, unlinked credential) return `ExportResult(false, false, ...)`.
4. *Stuck Export Recovery & Re-Queueing (`REQ-DAT-037.4`)*:
• When the athlete successfully connects/re-authenticates Google Drive or silent token acquisition succeeds, the system SHALL transition exports stuck in `FINISHED_FAILED` solely with `"Google Drive credential is null or not linked"` back to `WAITING`, ensuring workouts recorded while unlinked are automatically uploaded once connected.
• On application startup (`MainActivityWithNavigation.checkPreferences()`), when `TrainingApplication.uploadToGoogleDrive()` is enabled:
  - If cached token is null/blank and `GoogleSignIn.getLastSignedInAccount(context)` is non-null, the system SHALL silently refresh the token on a background dispatcher via `GoogleDriveAuthManager.refreshTokenSync(context)`.
  - Whenever a valid token is present (or obtained via silent refresh), the system SHALL invoke `ExportManager.recoverStuckGoogleDriveExports(context)` on `ioDispatcher` so any workouts stuck in `FINISHED_FAILED` due to missing credentials automatically recover and re-enqueue in `WorkManager` without requiring manual reconnect (ATT-5081).
5. *Preservation of System Invariants (`REQ-DAT-037.5`)*:
• WorkManager retry limit (`ExportConfig.MAX_RETRIES = 5`) MUST NOT be exceeded.
• OAuth tokens MUST remain strictly confined to `auth_credentials_prefs.xml` (`REQ-DAT-032.1`).
• Non-transient errors MUST never trigger retry loops.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-DAT-030` (*Google Drive Query Escaping, Encapsulated Account Cache, Atomic Download Staging & Transparent Scan Results*) and `REQ-DAT-032` (*OAuth Credential Isolation, Android Auto Backup & Device Transfer Rules, and .attbackup Credential Exclusion Policy*). Targets `MainActivityWithNavigation.kt`, `GoogleDriveUploader.kt`, `DropboxUploader.java`, `GoogleDriveAuthManager.kt`, `GoogleDriveBackupManager.kt`, `BackupRestoreViewModel.kt`, `AuthCredentialsStore.kt`, `GoogleDriveSettingsDialog.kt`, `ExportManager.java`, and `ExportStatusDatabaseManager.kt`.
2. *Historical Origin & Commit Trace*: `931bf45d` (ATT-1306, initial Drive integration); `01b4b49c` & `b2d4e3d6` (ATT-2631, 401 client token refresh); `e3f6de91` (ATT-3995, WorkManager export pipeline & `shallRetry`); `203baa27` (ATT-4023, credential isolation); `8ad50842` (ATT-4459, restore sanitization); `07ac3cd6` (ATT-4666, settings dialog unification); `9a59e825` (ATT-4922, export recovery on reconnect).
3. *Root Reason for Existing Formulation*: Upfront null checks in `GoogleDriveUploader` and `GoogleDriveBackupManager` were defensive guards to prevent unnecessary HTTP attempts without cached tokens, but overlooked that Google Play Services holds valid account sessions capable of silent background token refresh (`GoogleAuthUtil.getToken`). ATT-4922 added recovery during new exports or interactive settings reconnect, but overlooked that previously failed workouts in terminal `FINISHED_FAILED` have no active WorkManager jobs and stay dormant on fresh app launch unless re-queued.
4. *Preservation of Core Invariants*: Credential isolation in `auth_credentials_prefs.xml` (`REQ-DAT-032.1`) and clean re-auth invariant (`REQ-DAT-032.4`) remain inviolate. Permanent errors (missing local file, unrecoverable 401/403) strictly set `shallRetry = false`. Asynchronous dispatch on `ioDispatcher` guarantees zero main-thread blocking on startup.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `uploadToGoogleDrive()` is enabled but cached token is null with an active device Google account,
• *When* `GoogleDriveUploader.doExport()` or `GoogleDriveBackupManager.uploadBackup()` executes,
• *Then* `GoogleDriveAuthManager.refreshTokenSync(context)` is invoked, acquiring a fresh token and proceeding with the upload.
• *Given* a restored backup with wiped credentials and no active Google account,
• *When* `AuthCredentialsStore.sanitizeAfterRestore()` executes,
• *Then* `SP_UPLOAD_TO_GOOGLE_DRIVE` and `SP_UPLOAD_TO_DROPBOX` are reset to `false`, and `GoogleDriveSettingsDialog` displays the disconnected state.
• *Given* a queued Google Drive or Dropbox upload encountering a transient network drop (IOException, HTTP 429, HTTP 5xx),
• *When* the uploader executes,
• *Then* `shallRetry` evaluates to `true`, and `ExportWorker` returns `Result.retry()` up to `MAX_RETRIES`.
• *Given* workouts stuck in `FINISHED_FAILED` with `"Google Drive credential is null or not linked"`,
• *When* Google Drive is connected or re-authenticated,
• *Then* stuck exports are transitioned to `WAITING` and re-queued.
• *Given* the app starts (`MainActivityWithNavigation.checkPreferences()`) with `uploadToGoogleDrive()` enabled and previously stuck `FINISHED_FAILED` exports in `ExportStatus.db`,
• *When* a valid token is present or silently refreshed from an existing Google account,
• *Then* `ExportManager.recoverStuckGoogleDriveExports()` is invoked on `ioDispatcher`, resetting stuck exports to `WAITING` and enqueuing WorkManager upload jobs automatically.

**Invariants**: WorkManager `MAX_RETRIES = 5` cap preserved; credentials isolated in `auth_credentials_prefs.xml`; permanent errors return `shallRetry = false`.

### REQ-MIG-048: Strava Bulk Export Archive Ingestion Pipeline, Bilingual CSV Metadata Enrichment, Transparent GZIP Decompression, Tolerant XML Deserialization, Equipment Sync, and Duplicate Re-Upload Prevention [Verified]
- **Tests**: `TST-MIG-046`, `TST-UI-458`
- **System Requirement(s)**: `SYSREQ-DEX-005`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StravaBulkExportImporter.kt`, `StravaCsvParser.kt`, `LegacyImportEngine.kt`, `ImportBackupTabsScreen.kt`, `TrackpointModels.kt`, `EquipmentDbHelper.java`, `StravaUploadDbHelper.java`
- **Rationale**: Strava provides users with complete account archives (`export_<id>.zip`) containing an authoritative metadata summary (`activities.csv`), user equipment tables (`bikes.csv`, `shoes.csv`), and compressed activity streams (`activities/*.fit.gz`, `*.tcx.gz`, `*.gpx.gz`). To enable seamless migration into `aTrainingTracker` without gigabyte-scale uncompressed disk bloat or XML parser crashes, the system SHALL ingest Strava export archives via streaming ZIP reading, decode compressed stream files transparently, parse bilingual RFC 4180 metadata, sanitize TCX XML declarations against leading whitespace, synchronize equipment, and register Strava activity IDs to prevent duplicate re-upload loops (ATT-4957, extending `REQ-MIG-030`, `REQ-MIG-031`, `REQ-MIG-037`, `REQ-MIG-038`, and `REQ-MIG-040`):
1. *Streaming ZIP Archive & Transparent GZIP Decompression (`REQ-MIG-048.1`)*:
• The system SHALL accept Strava bulk export archives (`.zip`) in `ImportBackupTabsScreen` and process entries on-the-fly via streaming (`ZipFile` / `ZipInputStream` and `GZIPInputStream`), directly feeding decompressed streams into `FitWorkoutParser`, `TcxWorkoutParser`, or `GpxWorkoutParser` without staging uncompressed activity files to disk storage.
• Every archive entry path SHALL be verified against Zip-Slip path traversal vulnerabilities (`REQ-MIG-040`).
2. *Language-Independent & Multi-Line RFC 4180 CSV Metadata Parser (`REQ-MIG-048.2`)*:
• The system SHALL parse `activities.csv`, `bikes.csv`, and `shoes.csv` independent of athlete export language (FR, ES, IT, NL, PT, PL, JA, etc.) via hybrid column resolution: matching existing German/English aliases when present, auto-detecting the stream filename column via `(?i)^activities/.*` content pattern, and falling back to Strava's fixed canonical column indices (Col 0 ID, Col 1 Date, Col 2 Name, Col 3 Type, Col 4 Description, Col 9 Commute, Col 10 Private Note, Col 11 Gear, Col 12 Filename, Col 102 Media; Col 0 Name, Col 1 Brand, Col 2 Model for gear) (ATT-5471).
• Unused summary telemetry metric columns (`elapsedTime`, `movingTime`, `distance`, `elevationGain`, `calories`, `avgHr`, `maxHr`, `avgWatts`, `maxWatts`) SHALL be pruned from `StravaActivityRow` and `StravaCsvParser.kt`.
• The CSV parser SHALL strictly adhere to RFC 4180, handling multiline quoted fields with embedded commas, newlines, and escaped quotes (`""`).
• Each activity row SHALL be indexed by its relative stream filename under `activities/`.
3. *Tolerant XML Stream Whitespace Sanitization (`REQ-MIG-048.3`)*:
• In `TrackpointModels.kt` (and TCX/GPX stream pre-processing), the system SHALL sanitize stream input by skipping leading ASCII whitespace (` `, `\t`, `\r`, `\n`) before the XML declaration (`<?xml`), preventing `XmlPullParserException: Unexpected token` crashes caused by Strava export files with leading spaces (e.g. `activities/3131550008.tcx.gz`).
4. *Rich Metadata Enrichment, Equipment Matching & Strava Re-Upload Suppression (`REQ-MIG-048.4`)*:
• Parsed workouts SHALL be enriched with metadata from `activities.csv`: custom workout title (`WorkoutSummaries.WORKOUT_NAME`), description (`DESCRIPTION`), private training goal (`GOAL`), commute flag (`COMMUTE`), and sport type mapped to canonical `BSportType` / `SportTypeDatabaseManager`.
• Equipment names from `Activity Gear` / `Aktivitätsausrüstung` and gear tables (`bikes.csv`, `shoes.csv`) SHALL be matched or created in `EquipmentDbHelper` (`Equipment.db`), linking the resolved `EQUIPMENT_ID` into `WorkoutSummaries`.
• The Strava `Activity ID` SHALL be recorded in `StravaUploadDbHelper` (with status `Uploaded`) and `WorkoutSummaries.UPLOAD_TO_STRAVA` set to `0`, ensuring imported workouts are never re-uploaded to Strava.
5. *Deduplication, Granular Progress & Non-Regression Invariants (`REQ-MIG-048.5`)*:
• Pre-existing workouts matching start timestamp and sport type SHALL be skipped via `LegacyImportEngine.isWorkoutExisting()` without data corruption (`DUPLICATE_SKIPPED`).
• Progress updates SHALL be reported through `LegacyImportEngine.ProgressListener` with current index, total count, and activity title.
• Existing standalone FIT, TCX, and GPX file/directory import pipelines MUST remain fully functional and unregressed.
6. *Strava Bulk Export Media & Photo Ingestion (`REQ-MIG-048.6`)*:
• The system SHALL stream and parse the pipe-separated `Medien` / `Media` column in `activities.csv` and photo captions from `media.csv` (`Medien-Dateiname,Medienunterschrift` / `media-filename,caption`) (ATT-43).
• Referenced image files (`media/*`) SHALL be extracted directly into app-private workout media storage (`filesDir/workout_photos/<workout_id>/`) with strict Zip-Slip path sanitization (`REQ-MIG-040`).
• If EXIF capture timestamp and GPS metadata are absent (e.g. stripped by Strava export), the photo SHALL default to the workout start timestamp and the corresponding GPS trackpoint coordinate.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-048` under Standalone Ticket `ATT-4957` (Sprint `2026-45.6`), extending `REQ-MIG-030` (*Legacy GPX Import*), `REQ-MIG-031` (*Deduplication*), `REQ-MIG-037` (*Streaming XML Parsing*), `REQ-MIG-038` (*ParsedWorkoutData DTO*), and `REQ-MIG-040` (*Secure Zip Extraction*). Targets `StravaZipImportEngine.kt`, `LegacyImportEngine.kt`, `ImportBackupTabsScreen.kt`, `TrackpointModels.kt`, `EquipmentDbHelper.java`, and `StravaUploadDbHelper.java`.
2. *Historical Origin & Commit Trace*: `ATT-314` (commit `4d5b2c1` - Deduplication), `ATT-3982` (commit `b875938` - `REQ-MIG-040` Secure Zip Extraction), `ATT-4008` (commit `69e6b41` - TCX/GPX stream parsers).
3. *Root Reason for Existing Formulation*: Prior import architecture supported only individual standalone files and directory scanning of uncompressed files. It lacked archive entry indexing, bilingual CSV metadata mapping, in-memory GZIP decompression, and TCX leading-whitespace tolerance.
4. *Preservation of Core Invariants*: Mutex-guarded deduplication, transactional compensatory rollback on failure, Zip-Slip path sanitization, and standalone FIT/TCX/GPX import paths remain strictly invariant.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an official Strava export ZIP archive containing `activities.csv` with German or English headers and `.tcx.gz` files with leading whitespace before `<?xml`,
• *When* the athlete selects the archive in `ImportBackupTabsScreen`,
• *Then* `StravaZipImportEngine` decompresses and parses the streams transparently, strips leading whitespace, imports workouts with user titles, descriptions, and gear, and reports completion progress.
• *Given* an activity in the Strava export matching a workout already stored in local databases,
• *When* the archive import executes,
• *Then* the existing workout is detected and skipped without duplicating entries (`DUPLICATE_SKIPPED`).
• *Given* an imported Strava activity with a valid Strava `Activity ID`,
• *When* the import completes,
• *Then* `StravaUploadDbHelper` records the activity ID with status `Uploaded` and `UPLOAD_TO_STRAVA` is set to `0`, preventing duplicate re-uploads to Strava.

**Invariants**: Zip-Slip protection strictly enforced; zero uncompressed multi-gigabyte disk staging; standalone FIT/TCX/GPX imports remain 100% unregressed.

### REQ-DAT-038: Workout Photo Storage, Symmetrical SQLite Schema v27, and EXIF/Trackpoint Interpolation [Verified]
- **Tests**: `TST-DAT-034`
- **System Requirement(s)**: `SYSREQ-DEX-001`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSummariesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `WorkoutPhotoManager.kt`, `WorkoutDeletionHelper.java`
- **Rationale**: Athletes capture photos during workouts and export archives containing workout media. To persist photos attached to workouts without ballooning SQLite database size, photo metadata SHALL be stored in a dedicated `workout_photos` table in `WorkoutSummaries.db` (v27) while image binaries are stored in app-private filesystem storage (`filesDir/workout_photos/<workout_id>/`), accompanied by EXIF metadata extraction and GPS trackpoint interpolation (ATT-43):
1. *Symmetrical SQLite Schema v27 & DatabaseSchemaRegistry Parity (`REQ-DAT-038.1`)*:
• `WorkoutSummaries.db` SHALL increment `DB_VERSION` from 26 to 27 and create table `workout_photos` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT, `workout_id` INTEGER NOT NULL, `file_path` TEXT NOT NULL, `taken_at_ms` INTEGER, `latitude` REAL, `longitude` REAL, `caption` TEXT).
• Both `onCreate()` and `onUpgrade()` SHALL define identical table schemas, constraints, and index `idx_workout_photos_workout_id` on `workout_photos (workout_id)` (Rule 2.3).
• `DatabaseSchemaRegistry.kt` SHALL reference `WorkoutSummariesDbHelper.DB_VERSION` directly rather than hardcoding a static integer (Rule 2.2).
2. *EXIF Timestamp & GPS Trackpoint Interpolation (`REQ-DAT-038.2`)*:
• When a photo is attached, the system SHALL extract the EXIF capture timestamp (`TAG_DATETIME_ORIGINAL`) and GPS coordinates.
• If EXIF GPS coordinates are absent but `taken_at_ms` falls within the workout start and end time window, the system SHALL interpolate latitude and longitude by querying adjacent timestamped trackpoints in `WorkoutSamples.db`.
3. *Cascading Deletion & Cleanup Invariants (`REQ-DAT-038.3`)*:
• Deleting a single photo SHALL remove its database record and delete the physical image file from storage.
• Deleting a workout via `WorkoutSummariesDatabaseManager.deleteWorkout` or `WorkoutDeletionHelper` SHALL cascade-delete all associated photo rows in `workout_photos` and delete directory `filesDir/workout_photos/<workout_id>/`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-DAT-038` under Standalone Ticket `ATT-43` (Sprint `2026-45.6`), extending `REQ-DAT-034` (*SQLite Schema Indexing*) and `REQ-MIG-040` (*Secure Storage*). Targets `WorkoutSummariesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `WorkoutPhotoManager.kt`, and `WorkoutDeletionHelper.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 (`ATT-4008`, commit `69e6b41` - `DB_VERSION = 26`), Sprint 2026-44.1 (`ATT-4592`, `DatabaseSchemaRegistry`).
3. *Root Reason for Existing Formulation*: `WorkoutSummaries.db` previously stored only scalar telemetry aggregates and encoded polylines, lacking media entity association.
4. *Preservation of Core Invariants*: Zero regression on existing workout summaries; symmetrical `onCreate`/`onUpgrade` evolution; cascading disk cleanup; non-blocking background IO.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an upgraded or fresh installation of `aTrainingTracker`,
• *When* `WorkoutSummariesDbHelper` initializes or upgrades from version 26 to 27,
• *Then* table `workout_photos` and index `idx_workout_photos_workout_id` exist identically in both pathways, and `DatabaseSchemaRegistry` validates version 27.
• *Given* a photo taken during a workout without embedded EXIF GPS coordinates,
• *When* attached to the workout,
• *Then* `taken_at_ms` is matched against `WorkoutSamples.db` trackpoints and interpolated latitude/longitude coordinates are stored.
• *Given* an attached photo or complete workout deleted by the athlete,
• *When* deletion is confirmed,
• *Then* all corresponding rows in `workout_photos` and image files on disk are deleted.

**Invariants**: Symmetrical SQLite evolution, safe file deletion, memory-safe bitmap sampling, 100% clean-room test pass rate.

### REQ-MIG-049: Unified Chronological Ascending Ingestion Order for Bulk Imports & Cloud Recovery [Verified]
- **Tests**: `TST-MIG-047`
- **System Requirement(s)**: `SYSREQ-DEX-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StravaBulkExportImporter.kt`, `SafDirectoryScanner.kt`, `BackupRestoreViewModel.kt`, `LegacyImportEngine.kt`
- **Rationale**: Historical activities ingested through bulk import pipelines (Strava bulk ZIP export, SAF directory scanning, multi-URI file selection, and Dropbox/Google Drive cloud recovery) must be processed in deterministic chronological ascending order (oldest first). Ingesting newest first causes inverted sequential cluster counter numbering (`#1` assigned to modern workouts and `#350` to older ones in `WorkoutClusterEngine`) and SQLite autoincrement `_id` inversion. The system SHALL re-order activities/URIs/entries across all bulk pipelines prior to sequential processing (ATT-6039, extending `REQ-MIG-048`, `REQ-UI-363`, `REQ-MIG-031`, `REQ-MIG-034`, and `REQ-MIG-035`):
1. *Strava Bulk Export Chronological Re-Ordering (`REQ-MIG-049.1`)*:
• `StravaBulkExportImporter` SHALL sort parsed activities from `activities.csv` ascending by numeric `activityId.toLongOrNull() ?: Long.MAX_VALUE` (falling back to `reversed()` when non-numeric) prior to the sequential import loop, ensuring historical activities are processed from oldest to newest.
2. *SAF Directory Scan Lexicographical/Chronological Order (`REQ-MIG-049.2`)*:
• `SafDirectoryScanner.scanDocumentFile` SHALL sort discovered matching URIs ascending by document filename using `String.CASE_INSENSITIVE_ORDER` before returning the list, guaranteeing deterministic oldest-to-newest processing for standard timestamp-formatted workout files (`YYYY-MM-DD_HHMM...`).
3. *Multi-File Import Pre-Sorting (`REQ-MIG-049.3`)*:
• `BackupRestoreViewModel.importWorkoutFilesInternal` SHALL pre-sort incoming file URIs ascending by resolved display name via `ImportFileValidator.resolveDisplayName` or content resolver query using `String.CASE_INSENSITIVE_ORDER` prior to launching sequential file imports.
4. *Cloud Recovery Entry Chronological Order (`REQ-MIG-049.4`)*:
• `LegacyImportEngine.bulkRecoverFromDropbox` and `LegacyImportEngine.bulkRecoverFromGoogleDrive` SHALL sort deduplicated cloud entries ascending by filename using `String.CASE_INSENSITIVE_ORDER` prior to enqueuing into the worker channel.
5. *Invariants & Cluster Numbering Preservation (`REQ-MIG-049.5`)*:
• Sequential cluster numbering `#1..#N` in `WorkoutClusterEngine.assignClusterToWorkout` SHALL assign `#1` to the oldest ingested workout and increment monotonically towards the newest workout.
• SQLite autoincrement `_id` order in `WorkoutSummaries.db` SHALL align naturally with chronological workout progression.
• Core deduplication (`isWorkoutExisting`), Zip-Slip security (`REQ-MIG-040`), equipment matching, and coroutine cooperative cancellation MUST remain fully intact and unregressed.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-049` under Ticket `ATT-6039` (Sprint `2026-45.12`), extending `REQ-MIG-048` (*Strava Bulk Export*), `REQ-UI-363` (*SAF Directory Scanner*), and `REQ-MIG-031`/`034`/`035` (*Cloud Recovery & Deduplication*). Targets `StravaBulkExportImporter.kt`, `SafDirectoryScanner.kt`, `BackupRestoreViewModel.kt`, and `LegacyImportEngine.kt`.
2. *Historical Origin & Commit Trace*: `ATT-4957` (commit `69e6b41` - Strava Bulk Export), `ATT-3491` (SAF Directory Scanner), `ATT-2341` (FIT Cloud Recovery).
3. *Root Reason for Existing Formulation*: Prior implementations focused on streaming format decoding and memory efficiency, leaving entry dispatch ordering implicit and dependent on filesystem traversal or export CSV generation order (reverse-chronological in Strava).
4. *Preservation of Core Invariants*: Deduplication logic, Zip-Slip path traversal defense, coroutine cooperative cancellation, and cluster spatial tolerances remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an official Strava bulk export archive with `activities.csv` listing activities in reverse chronological order (newest first),
• *When* the archive is ingested via `StravaBulkExportImporter`,
• *Then* activities are processed chronologically ascending (oldest first), assigning `#1` to the earliest workout in newly formed clusters.
• *Given* multiple workout files in a SAF directory or multi-file picker selection,
• *When* imported via `SafDirectoryScanner` or `BackupRestoreViewModel`,
• *Then* URIs are processed in ascending filename order.
• *Given* discovered files in Dropbox or Google Drive recovery,
• *When* recovery executes in `LegacyImportEngine`,
• *Then* deduplicated entries are enqueued and processed in ascending filename order.

**Invariants**: Standalone imports unregressed, cluster spatial matching untouched, non-blocking coroutine cancellation preserved.

### REQ-MIG-050: In-Memory Existing Workout Deduplication Snapshot & Zero-I/O Fast-Forward for Bulk Importers [Verified]
- **Tests**: `TST-MIG-048`
- **System Requirement(s)**: `SYSREQ-DEX-004`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ExistingWorkoutsSnapshot.kt`, `StravaBulkExportImporter.kt`, `BackupRestoreViewModel.kt`, `LegacyImportEngine.kt`, `WorkoutSummariesDatabaseManager.java`, `StravaUploadDbHelper.java`
- **Rationale**: When resuming or repeating large bulk imports (Strava bulk ZIP archives, local multi-file/SAF folders, or Dropbox/Google Drive recovery), importers currently perform expensive I/O, file copying to `cacheDir`, and full stream decompression/parsing on already-imported workouts before discovering they already exist in downstream database checks (`isWorkoutExisting`). The system SHALL pre-load an in-memory snapshot of existing workout identifiers and execute zero-I/O upstream pre-filtering to fast-forward bulk imports without stream parsing or file copying (ATT-5902, extending `REQ-MIG-031`, `REQ-MIG-048.5`, and `REQ-MIG-049.5`):
1. *Single-Pass In-Memory Deduplication Snapshot (`REQ-MIG-050.1`)*:
• The system SHALL load an in-memory `ExistingWorkoutsSnapshot` in a single query pass (< 3 ms) prior to executing bulk import batches.
• The snapshot SHALL contain a case-insensitive set of existing `FILE_BASE_NAME`s from `WorkoutSummaries` and existing Strava `ActivityId`s from `StravaUploadDbHelper`.
• Lookups against the snapshot (`containsFileBaseName`, `containsStravaActivityId`) SHALL execute in $O(1)$ time using normalized lowercase comparisons.
2. *Zero-I/O Pre-Filtering & Fast-Forward in Strava Bulk Export (`REQ-MIG-050.2`)*:
• `StravaBulkExportImporter.importArchive` SHALL evaluate `baseFileName` (resolved from filename and activity ID) and `row.activityId` against `ExistingWorkoutsSnapshot` prior to opening archive stream entries, decompressing GZIP (`.gz`) streams, or invoking FIT/TCX/GPX stream parsers.
• Matching pre-existing activities SHALL be fast-forwarded immediately by incrementing `skippedCount` without opening stream I/O or allocating parser buffers.
3. *Zero-Copy Pre-Filtering in Local Multi-File & Folder Imports (`REQ-MIG-050.3`)*:
• `BackupRestoreViewModel.importWorkoutFilesInternal` SHALL evaluate candidate `baseFileName`s resolved from URI display names against `ExistingWorkoutsSnapshot` prior to opening `contentResolver.openInputStream` or writing temporary files into `context.cacheDir`.
• Matching pre-existing files SHALL be skipped immediately without disk I/O, except where filenames start with `legacy_import` (which require stream parsing to determine timestamp-based base names).
4. *In-Memory Deduplication in Cloud Recovery Pipelines (`REQ-MIG-050.4`)*:
• `LegacyImportEngine.bulkRecoverFromDropbox` and `LegacyImportEngine.bulkRecoverFromGoogleDrive` SHALL evaluate entry `baseFileName`s against `ExistingWorkoutsSnapshot` in memory, eliminating per-entry SQLite queries during cloud recovery.
5. *Defense-in-Depth & Non-Regression Invariants (`REQ-MIG-050.5`)*:
• Downstream transactional deduplication (`isWorkoutExisting(summaryDb, ...)`) inside `importMutex.withLock` in `LegacyImportEngine.persistParsedWorkout` MUST remain 100% intact as a defense-in-depth safety net.
• Resuming a bulk import containing 1,000+ already-imported workouts SHALL fast-forward in under 50 ms in memory.
• Deduplication accuracy MUST remain 100% identical to transactional SQLite checks, with zero legitimate workouts erroneously skipped and zero duplicates inserted.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-MIG-050` under Ticket `ATT-5902` (Sprint `2026-45.13`), extending `REQ-MIG-031` (*Multi-Dimensional Workout Deduplication*), `REQ-MIG-048.5` (*Strava Bulk Export Deduplication*), and `REQ-MIG-049.5` (*Bulk Import Invariants*). Targets `ExistingWorkoutsSnapshot.kt`, `StravaBulkExportImporter.kt`, `BackupRestoreViewModel.kt`, `LegacyImportEngine.kt`, `WorkoutSummariesDatabaseManager.java`, and `StravaUploadDbHelper.java`.
2. *Historical Origin & Commit Trace*: `ATT-2023` (commit `9b32948` - 3-min same-sport tolerance), `ATT-4957` (commit `33856f04` - Strava bulk export), `ATT-6039` (commit `163e83a6` - chronological ascending order).
3. *Root Reason for Existing Formulation*: Prior deduplication was localized entirely inside `LegacyImportEngine.persistParsedWorkout` to guarantee atomicity and handle fallback derivations (such as timestamps from parsed streams). Pre-filtering was omitted to avoid maintaining cached state across varied entry points.
4. *Preservation of Core Invariants*: Downstream `isWorkoutExisting` in `persistParsedWorkout` is preserved intact. Upstream in-memory snapshot acts as a pure read-only acceleration layer. Workouts requiring stream-derived names (`legacy_import`) safely bypass filename-only pre-filtering.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a Strava bulk export archive containing already-imported activities,
• *When* `StravaBulkExportImporter.importArchive` processes the archive,
• *Then* already-imported activities are fast-forwarded in memory without opening ZIP entry streams, decompressing GZIP streams, or invoking FIT/TCX/GPX parsers.
• *Given* local workout files that already exist in `WorkoutSummaries`,
• *When* imported via `BackupRestoreViewModel.importWorkoutFilesInternal`,
• *Then* duplicate files are identified and skipped via `baseFileName` pre-check without copying bytes into `cacheDir`.
• *Given* Dropbox or Google Drive recovery with previously recovered files,
• *When* recovery executes in `LegacyImportEngine`,
• *Then* known files are filtered in memory using `ExistingWorkoutsSnapshot` without executing per-entry SQLite queries.

**Invariants**: Downstream transactional `isWorkoutExisting` intact, no false-positive skipping of `legacy_import` files, zero duplicate workout rows created.

### REQ-DAT-039: Point-to-Point (A->B) Route Apex Migration, Import Unification & Mapper Parity [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-DAT-035`
- **System Requirement(s)**: `SYSREQ-DEX-001`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`
- **Rationale**: While `REQ-SET-097` (`ATT-5899`) introduced non-degenerate elliptical apex calculation (`findApexFromPoints`) for live recordings on point-to-point (A->B) routes ($d(\text{start}, \text{end}) \ge 150\text{ m}$), historical workouts in `TABLE_EXTREMA_VALUES` and historical clusters in `WorkoutClusters.db` still store legacy apex coordinates that collapsed onto the destination point ($P_{\text{apex}} \approx P_{\text{end}}$). Furthermore, premature `< 50f` skip filtering prevented healing realistic commute overshoots. The system SHALL perform a self-healing migration across existing point-to-point database records using bipolar product apex resolution and unify apex calculation across import pipelines and data mappers.
- **Description**:
  1. *Self-Healing Database Apex Migration (`REQ-DAT-039.1`)*:
     - `WorkoutClusterEngine` SHALL provide `healLegacyPointToPointApexes()` to scan all point-to-point workouts ($d(\text{start}, \text{end}) \ge 150\text{ m}$) having valid track points or map polylines without premature distance-to-end filtering.
     - For any workout where `recordedApex == null || distanceBetween(recordedApex, trueApex) > 25f` (with `trueApex` computed via bipolar product `findApexFromPoints(start, points, end)`), the system SHALL recompute non-degenerate apex coordinates and persist the corrected `MAX_LINE_DISTANCE_LAT`, `LNG`, and `LINE_DISTANCE_m` in `TABLE_EXTREMA_VALUES`.
     - The migration SHALL recalculate cluster centroids (`maxDispLat`, `maxDispLng`) for all affected clusters.
  2. *Repository SharedPreferences-Guarded Trigger (`REQ-DAT-039.2`)*:
     - `WorkoutClusterRepository.refreshClusters()` SHALL trigger the self-healing migration asynchronously on `Dispatchers.IO`, guarded by a persistent SharedPreferences flag (`SP_KEY_LAST_APEX_REPAIR = "last_apex_repair_v2"`, executing when stored value `< 2` and saving `2`), ensuring the migration executes strictly once without blocking app startup or UI composition.
  3. *Legacy Import Pipeline Unification (`REQ-DAT-039.3`)*:
     - In `LegacyImportEngine.kt`, apex calculation for imported FIT, TCX, GPX, and Strava streams SHALL use `WorkoutClusterEngine.findApexFromPoints(start, points, end)` instead of raw distance-from-start maximization.
     - Imported workouts SHALL persist `MAX_LINE_DISTANCE_LAT`, `LNG`, and `LINE_DISTANCE_m` in `TABLE_EXTREMA_VALUES` and pass `workoutPolyline = points` into `suggestCluster` for corridor-aware cluster assignment.
  4. *Data Mapper Symmetrical Apex Resolution (`REQ-DAT-039.4`)*:
     - In `WorkoutDataMapper.kt`, `resolveAuthoritativeApex` SHALL accept `endLatLng: LatLng?` and delegate to `findApexFromPoints(start, points, end)`, guaranteeing UI representation parity with database records.
  5. *Preservation of System Invariants (`REQ-DAT-039.5`)*:
     - Closed-loop routes ($d(\text{start}, \text{end}) < 150\text{ m}$) SHALL remain completely untouched and preserve distance-from-start apex calculation; no SQLite DDL schema changes; 100% unit test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* existing historical point-to-point workouts with degenerated apex coordinates ($P_{\text{apex}} \approx P_{\text{end}}$ or delta > 25m),
• *When* `WorkoutClusterRepository.refreshClusters()` executes with repair flag < 2,
• *Then* `healLegacyPointToPointApexes()` recalculates non-degenerate apex coordinates using bipolar product `findApexFromPoints`, updates `TABLE_EXTREMA_VALUES` and cluster centroids, and records `last_apex_repair_v2 = 2`.
• *Given* an imported FIT/TCX/GPX point-to-point activity in `LegacyImportEngine`,
• *When* imported into the database,
• *Then* its apex coordinates are computed via `findApexFromPoints`, persisted to extrema tables, and forwarded to `suggestCluster` with polyline stations.
• *Given* `WorkoutDataMapper.resolveAuthoritativeApex` called with track points, start, and end,
• *When* evaluated for a point-to-point route,
• *Then* it resolves the non-degenerate mid-corridor apex matching `findApexFromPoints`.

**Invariants**: Closed-loop route ($d < 150\text{ m}$) apex behavior unmodified; no blocking UI threads; transactional SQLite updates; 100% unit test pass rate.

### REQ-DAT-040: TCX Structured DTO Serialization, Parsing Decoupling & Round-Trip Fidelity [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-DAT-036`
- **System Requirement(s)**: `SYSREQ-DEX-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/tcx/model/`, `app/src/main/java/com/atrainingtracker/trainingtracker/tcx/serializer/`, `app/src/main/java/com/atrainingtracker/trainingtracker/tcx/parser/`, `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/TCXFileWriter.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/migration/parsers/TcxWorkoutParser.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/routes/parsers/TcxTrackpointParser.kt`
- **Rationale**: To prevent schema sequence violations, escaping bugs, and silent field drops during XML interchange, the system SHALL introduce structured, immutable Data Transfer Objects (DTOs) for the Garmin TCX schema (`TrainingCenterDatabasev2.xsd`), decoupling XML serialization and parsing from internal SQLite database representations and providing bit-exact round-trip fidelity across workout export and re-import.
- **Description**:
  1. *Structured TCX DTO Domain Models (`REQ-DAT-040.1`)*:
     - The system SHALL define strongly-typed, immutable Kotlin data classes representing the TCX Activity and Course schemas (`TcxDocument`, `TcxActivity`, `TcxLap`, `TcxTrackpoint`, `TcxPosition`, `TcxHeartRate`, `TcxExtensions`, `TcxDevice`, `TcxAuthor`, `TcxCourse`, `TcxCoursePoint`).
     - Models SHALL encapsulate all standard TCX elements (`Id`, `Lap`, `Notes`, `Creator`, `Extensions`, `Author`), Course elements (`Courses`, `Course`, `CoursePoint`), and Garmin Trackpoint Extension v2 (`TPX`) elements (`Speed`, `RunCadence`, `Watts`).
  2. *Streaming DTO XML Serializer (`REQ-DAT-040.2`)*:
     - The system SHALL provide a dedicated XML serializer (`TcxSerializer`) supporting both full-document serialization and an incremental streaming writer API (`startDocument`, `startActivity`, `startLap`, `writeTrackpoint`, `endLap`, `endActivity`, `endDocument`) allowing trackpoint emission directly to `Writer` with $O(1)$ memory consumption.
     - The serializer MUST enforce Garmin TCX v2 sequence ordering (`Id` -> `Lap`* -> `Notes`? -> `Creator`? -> `Extensions`? -> `Author`?).
     - The serializer MUST automatically escape XML reserved characters (`&`, `<`, `>`, `"`, `'`) in workout names, lap notes, and descriptions.
     - Integer-only constraints for `<Cadence>`, `<Watts>`, and `<RunCadence>` (`REQ-EXP-001`) MUST be strictly enforced.
  3. *Decoupled DTO XML Parser Bridge & Parser Unification (`REQ-DAT-040.3`)*:
     - The system SHALL provide a streaming parser bridge (`TcxParser`) that constructs `TcxDocument` DTOs from TCX XML input streams using `XmlPullParser`, supporting both Activities and Courses (`Course`, `CoursePoint`).
     - `TcxWorkoutParser` SHALL delegate XML ingestion to `TcxParser` and map the resulting DTOs into `ParsedWorkoutData`, preserving existing database persistence pathways in `LegacyImportEngine`.
     - `TcxTrackpointParser` SHALL delegate TCX stream parsing to `TcxParser` and map parsed activities and courses into `ParsedTcxData`, consolidating duplicate `XmlPullParser` state-machine loops (Rule 36 DRY).
     - Proprietary `att:ActivityExtension` / `att:LapExtension` and bracketed name fallback heuristics (`[Name] Description`) MUST be preserved without regression. When extensions are present (`activity.extensions != null`), `TcxWorkoutParser` SHALL resolve `workoutNotes` strictly from `activity.extensions.customDescription` and SHALL NOT fall back to raw `activity.notes`, preventing bracketed workout names from corrupting workout notes.
  4. *Bidirectional Round-Trip Fidelity (`REQ-DAT-040.4`)*:
     - Exporting an activity via `TCXFileWriter` and re-parsing it via `TcxWorkoutParser` SHALL preserve 100% of activity timestamps, workout names, descriptions, lap boundaries, durations, distances, trackpoint coordinates, heart rate, cadence, speed, and power telemetry. Specifically, activities with a custom name and null/blank description SHALL round-trip with `workoutNotes` remaining null.
  5. *Streaming & Memory Safety Invariant (`REQ-DAT-040.5`)*:
     - TCX serialization and parsing MUST operate in a streaming or batch-friendly fashion, ensuring workouts with 50,000+ trackpoints process without `OutOfMemoryError` or Main Thread blocking, and `TCXFileWriter` serializes trackpoints incrementally via `TcxSerializer` streaming API with $O(1)$ heap overhead.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a recorded or imported workout with custom name, notes, multiple laps, and power/cadence telemetry,
• *When* exported to TCX format via `TCXFileWriter`,
• *Then* the resulting XML validates against Garmin `TrainingCenterDatabasev2.xsd`, with strictly ordered elements, integer watts/cadence values, and escaped XML special characters.
• *Given* a valid TCX XML file exported by `aTrainingTracker` or third-party platforms (Garmin Connect, Strava),
• *When* parsed via `TcxWorkoutParser`,
• *Then* the parser deserializes the XML into structured TCX DTOs and maps them into `ParsedWorkoutData` without loss of metadata or telemetry channels.
• *Given* an exported TCX activity re-imported via `LegacyImportEngine`,
• *When* comparing the original workout entities with the re-imported entities,
• *Then* start time, sport type, active duration, lap count, per-lap metrics, and trackpoint streams match within IEEE floating-point tolerance.

**Invariants**: Zero database schema alterations; zero regression in existing GPX or FIT export/import engines; strict Garmin TCX v2 schema compliance; 100% unit test pass rate.

### REQ-DAT-041: Repository-Only Engine, Exporter & Migration Layer Database Access (Phase B) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-DAT-037`
- **System Requirement(s)**: `SYSREQ-DEX-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/StravaUploader.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/FitFileWriter.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/TCXFileWriter.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelper.java`
- **Rationale**: Direct instantiation of SQLite database manager singletons (`*DatabaseManager.getInstance()`) inside processing engines, exporters, and migration helpers violates Clean Architecture (SWE.2) and couples core background pipelines directly to low-level SQLite helpers. The system SHALL eliminate all direct database manager references from Phase B components by routing all database operations through domain repositories (`WorkoutRepository`, `SportTypesRepository`, `EquipmentRepository`, `WorkoutClusterRepository`, `KnownLocationsRepository`, `RoutesRepository`, `SegmentsRepository`, `ExportStatusRepository`).
- **Description**:
  1. *WorkoutClusterEngine Decoupling (`REQ-DAT-041.1`)*:
     - `WorkoutClusterEngine.kt` SHALL execute all cluster centroid calculations, candidate route lookups, sport type filtering, and milestone naming through `WorkoutClusterRepository`, `SportTypesRepository`, `KnownLocationsRepository`, `WorkoutRepository`, and `RoutesRepository`.
  2. *LegacyImportEngine Decoupling (`REQ-DAT-041.2`)*:
     - `LegacyImportEngine.kt` SHALL execute workout, lap, sample, cluster, and export status persistence via `WorkoutRepository`, `SportTypesRepository`, `WorkoutClusterRepository`, and `ExportStatusRepository`.
  3. *StravaUploader Decoupling (`REQ-DAT-041.3`)*:
     - `StravaUploader.kt` SHALL route workout metadata queries, segment detection, equipment resolution, and upload state updates through `WorkoutRepository`, `SportTypesRepository`, `EquipmentRepository`, and `SegmentsRepository`.
  4. *FitFileWriter & TCXFileWriter Decoupling (`REQ-DAT-041.4`)*:
     - `FitFileWriter.java` and `TCXFileWriter.java` SHALL retrieve lap, sample, summary, and sport type telemetry via domain repositories while preserving existing constructors for unit test backward compatibility.
  5. *WorkoutDeletionHelper Decoupling (`REQ-DAT-041.5`)*:
     - `WorkoutDeletionHelper.java` SHALL execute cascade deletions through repository delegates while preserving test constructor compatibility.
  6. *Domain Repository Batch & Mutation Delegates (`REQ-DAT-041.6`)*:
     - Target domain repositories SHALL expose any required batch queries and mutations on `Dispatchers.IO` / repository executors.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutClusterEngine`, `LegacyImportEngine`, `StravaUploader`, `FitFileWriter`, `TCXFileWriter`, and `WorkoutDeletionHelper`,
• *When* auditing source code and AST references,
• *Then* zero occurrences of `*DatabaseManager.getInstance(...)` exist across all Phase B target files.
• *Given* a recorded workout session exported to FIT or TCX or uploaded to Strava,
• *When* processed via the refactored exporters and engines,
• *Then* file contents, Strava payloads, cluster centroids, and deletion cascades remain 100% bit-exact and functionally identical.
• *Given* a multi-sport TCX or GPX backup archive,
• *When* imported via `LegacyImportEngine`,
• *Then* all workouts, laps, and sensor samples are fully persisted with 100% data integrity via domain repository delegates.

**Invariants**: Phase C (`TrackerService`, `BANALService`) strictly untouched; zero SQLite schema alterations; 100% unit test pass rate.

### REQ-DAT-042: True Repository Encapsulation & SQLite Backdoor Elimination (Phase B Hardening) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-DAT-038`
- **System Requirement(s)**: `SYSREQ-DEX-001`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/FitFileWriter.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/TCXFileWriter.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/BaseFileWriter.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelper.java`, `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt`
- **Rationale**: While ATT-6426 decoupled background engines from direct `*DatabaseManager.getInstance()` calls, repositories introduced public and internal backdoor getters exposing raw database managers, SQLiteDatabase handles, and Cursors. This violates SWE.2 encapsulation and coding standards §2.5. Low-level persistence details, table definitions, and Cursor lifecycle must be strictly encapsulated inside domain repositories.
- **Description**:
  1. *Elimination of Repository Backdoor Getters (`REQ-DAT-042.1`)*:
     - `WorkoutRepository`, `WorkoutClusterRepository`, `ExportStatusRepository`, and `RoutesRepository` SHALL make all internal database managers, database open helpers, `SQLiteDatabase` references, and raw `Cursor` getters `private`. Zero public or internal backdoor getters (`workoutSummariesDatabaseManager`, `workoutSamplesDatabaseManager`, `lapsDatabaseManagerInstance`, `getWorkoutCursor`, `getCursorForAllWorkouts`, `databaseManager`, `stravaHelper`, `getDatabase`) SHALL be exposed to engine, exporter, or UI consumers.
  2. *Encapsulated Sample Streaming API for Exporters (`REQ-DAT-042.2`)*:
     - `WorkoutRepository` SHALL provide a memory-bounded streaming API (e.g. `streamSamples(fileBaseName: String, consumer: SampleConsumer)`) allowing `FitFileWriter` and `TCXFileWriter` to iterate over high-frequency workout point records without loading or materializing entire sample point sets into JVM heap memory ($O(1)$ memory overhead).
  3. *Encapsulated Exporter & Uploader Domain Queries (`REQ-DAT-042.3`)*:
     - `WorkoutRepository` SHALL provide strongly-typed domain DTO getters for export header extraction (`BaseFileWriter`), Strava upload duplicate verification metadata (`StravaUploader`), and lap summaries (`FitFileWriter`, `TCXFileWriter`), eliminating raw `Cursor` projections and SQLite column lookups in exporter layers.
  4. *Transactional Batch Encapsulation for LegacyImportEngine (`REQ-DAT-042.4`)*:
     - `WorkoutRepository` and `ExportStatusRepository` SHALL encapsulate all multi-table insert and update operations required during GPX/TCX file imports (workout summaries, lap insertions, dynamic sample table creation and bulk insertion) within repository-managed transactional boundaries (`beginTransaction`/`endTransaction`) on `Dispatchers.IO`, ensuring full atomicity and thread safety.
  5. *Encapsulated Cascade Deletion Orchestration (`REQ-DAT-042.5`)*:
     - Multi-table workout cascade deletion (samples table drop, laps purge, export status cleanup, climb links, summaries deletion) SHALL be fully encapsulated within `WorkoutRepository.deleteWorkoutCascade()` and `WorkoutRepository.deleteOldWorkoutsCascade()`, deprecating and removing external database manager injections in `WorkoutDeletionHelper`.
  6. *Architectural AST and Reflection Governance Contract (`REQ-DAT-042.6`)*:
     - `RepositoryEngineCleanlinessContractTest` SHALL enforce:
       (a) Zero `android.database.Cursor`, `android.database.sqlite.SQLiteDatabase`, or `*DatabaseManager` imports across Phase B target callers (`FitFileWriter`, `TCXFileWriter`, `BaseFileWriter`, `LegacyImportEngine`, `WorkoutDeletionHelper`, `StravaUploader`, `WorkoutClusterEngine`).
       (b) Reflection inspection of domain repositories verifying that no public methods return `Cursor`, `SQLiteDatabase`, or `*DatabaseManager` instances.

**Acceptance Criteria (Given-When-Then)**:
• *Given* domain repositories `WorkoutRepository`, `WorkoutClusterRepository`, `ExportStatusRepository`, and `RoutesRepository`,
• *When* inspecting public and internal member declarations via reflection and AST,
• *Then* zero public getters or methods return `Cursor`, `SQLiteDatabase`, or `*DatabaseManager` types.
• *Given* high-frequency recorded workouts with up to 20,000+ sample points,
• *When* exported to FIT or TCX via `FitFileWriter` and `TCXFileWriter`,
• *Then* samples are streamed via encapsulated repository callbacks with $O(1)$ heap memory overhead and 100% bitwise export fidelity.
• *Given* a multi-sport TCX/GPX backup archive,
• *When* imported via `LegacyImportEngine`,
• *Then* multi-table sample and summary batch operations persist atomically via repository transaction methods without leaking `SQLiteDatabase` handles.
• *Given* single or batch workout deletion requests,
• *When* triggered through `WorkoutRepository` cascade methods,
• *Then* all associated sample tables, laps, climbs, and export statuses are cleaned up symmetrically in correct dependency order.

**Invariants**: Phase C (`TrackerService`, `BANALService`) strictly untouched; zero SQLite schema alterations; 100% test pass rate across all existing exporter, migration, and repository unit tests.

### REQ-EXP-042: Garmin FIT Radar Threat Alert Events & MyBikeTraffic Developer Fields [Verified]
- **Tests**: `TST-EXP-042`
- **System Requirement(s)**: `SYSREQ-DEX-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `FitFileWriter.java`
- **Rationale**: Cyclists exporting rides to Garmin FIT format expect compatibility with third-party cycling radar analytics platforms (such as *MyBikeTraffic*). The system SHALL encode standard FIT `EventMesg` (`Event.RADAR_THREAT_ALERT`) upon vehicle overtaking events and define FIT Protocol V2.0 Developer Fields on `RecordMesg` for 1 Hz radar metrics.
- **Description**:
  1. *Native FIT Radar Threat Event*: During sample streaming in `FitFileWriter.java`, upon detecting an overtaking vehicle pass ($\Delta \text{RADAR\_VEHICLE\_COUNT} > 0$), the encoder SHALL emit a standard FIT `EventMesg` with:
     - `Event.RADAR_THREAT_ALERT`
     - `EventType.MARKER`
     - `setRadarThreatCount()` (threat count)
     - `setRadarThreatLevelMax(RadarThreatLevelType)` (peak threat level)
     - `setRadarThreatMaxApproachSpeed()` (peak approach closing speed in m/s)
  2. *MyBikeTraffic Developer Fields on RecordMesg*: `FitFileWriter.java` SHALL declare FIT Developer Fields using `DeveloperDataIdMesg` and `FieldDescriptionMesg`:
     - `radar_current` (uint8, current threat count)
     - `radar_total` (uint16, cumulative vehicles passed)
     - `passing_speed` (uint16, scale 100, closing speed in m/s)
     - `passing_speed_abs` (uint16, scale 100, absolute vehicle speed $v_{\text{rider}} + v_{\text{approach}}$)
  3. *FIT Protocol V2.0 Compliance*: Developer fields SHALL strictly adhere to FIT Protocol V2.0 specifications without corrupting standard FIT record fields. Non-radar workouts SHALL omit radar developer fields with zero performance overhead.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded with active rearview radar,
• *When* exported to Garmin `.fit` format,
• *Then* `EventMesg` messages are emitted with `Event.RADAR_THREAT_ALERT`, and `RecordMesg` records contain valid developer fields parsed cleanly by Garmin FIT SDK.
• *Given* a workout recorded without radar data,
• *When* exported to `.fit`,
• *Then* zero radar events or developer fields are emitted, preserving standard FIT record encoding.

**Invariants**: FIT Protocol V2.0 conformance; zero regressions in standard FIT fields (Speed, Power, HR, Cadence, GPS).

### REQ-RAD-007: Radar Traffic & Close-Pass Summary in Strava Description & Share Intent [Verified]
- **Tests**: `TST-RAD-007`
- **System Requirement(s)**: `SYSREQ-DEX-003`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarTrafficSummaryFormatter.kt`, `StravaUploader.kt`, `ShareUtils.kt`
- **Rationale**: Cyclists recording rides with rearview radar want to document road traffic safety and motorist overtaking behavior in their Strava activity feeds and social shares without manual copy-pasting. The system SHALL provide an opt-in setting to automatically append a concise, standardized Radar Traffic & Close-Pass Summary to Strava activity descriptions and workout share texts.
- **Description**:
  1. *Opt-In Preference & Configuration*: The system SHALL expose an opt-in toggle (`uploadStravaRadarSummary`, default `false`) in Strava settings (`StravaSettingsDialog.kt`, `prefs_strava.xml`) and Radar settings (`RadarSettingsSection.kt`) backed by `SharedPreferences`.
  2. *Standard Radar Summary Formatter (BLE V1 & ANT+)*: When `uploadStravaRadarSummary` is enabled and a workout contains `RADAR_VEHICLE_COUNT > 0`, the system SHALL generate a concise localized summary line:
     `📡 Radar: <N> vehicles passed (<density>/km) • 🚀 Max <max_veh_spd> km/h (+<max_approach_spd> km/h approach)`
  3. *High-Resolution V2 Radar Summary Formatter (Garmin Varia RearVue 820)*: When high-resolution V2 clearance and classification metrics are present (`closePassCount > 0` or `heavyVehicleCount > 0`), the formatter SHALL include close-pass and heavy-vehicle badges:
     `📡 Radar: <N> vehicles passed (<density>/km) • ⚠️ <M> close passes (<1.5m, min <min_dist>m) • 🚛 <K> heavy vehicles • 🚀 Max <max_veh_spd> km/h`
  4. *Non-Destructive Description Composition & Idempotency*: `StravaUploader.kt` SHALL append the formatted summary line to existing user notes (`WorkoutSummaries.DESCRIPTION`) separated by a double newline (`\n\n`). If no user notes exist, only the summary line SHALL be set. Re-uploads, duplicate reconciliation flows, and retries SHALL NOT duplicate the summary line (idempotent prefix/containment check).
  5. *Zero-Vehicle & Non-Radar Suppression*: Workouts recorded without a radar sensor or with `0` vehicle passes SHALL omit the radar summary string entirely.
  6. *Workout Share Sheet Integration*: `ShareUtils.saveAndShare()` and `WorkoutShareBottomSheet.kt` SHALL support attaching the formatted summary line via `Intent.EXTRA_TEXT` alongside share snapshots.
  7. *9-Language Localization Parity*: All summary components and toggle labels SHALL be 100% localized across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Acceptance Criteria (Given-When-Then)**:
• *Given* `uploadStravaRadarSummary` is enabled and a workout has `114` vehicle passes recorded via BLE V1 or ANT+ radar,
• *When* the workout is uploaded to Strava,
• *Then* `StravaUploader.doUpdate()` appends `📡 Radar: 114 vehicles passed (1.8/km) • 🚀 Max 104 km/h (+68 km/h approach)` to the activity description without altering local or existing user notes.
• *Given* a workout recorded with 0 vehicle passes or without radar sensor,
• *When* uploaded to Strava or shared,
• *Then* zero radar summary text is appended to description or share intent.
• *Given* a workout already containing a radar summary line in description re-uploaded or updated,
• *When* `StravaUploader.doUpdate()` executes,
• *Then* the summary line is not duplicated.

**Invariants**: Zero overwrite or loss of user notes; zero SQLite schema modifications; 100% localization parity across 9 languages.

### REQ-EXP-023: Health Connect Export Status Tracking & Community Display [Verified]
- **Tests**: `TST-EXP-020`
- **System Requirement(s)**: `SYSREQ-DEX-003`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `FileFormat.java`, `ExportType.java`, `TrainingApplication.java`, `HealthConnectManager.kt`, `ExportStatusDataProvider.kt`, `ExportStatusRepository.kt`
- **Rationale**: Synchronizing workouts to Android Health Connect must provide athletes with explicit status visibility (waiting, ongoing, success, or failed error inspection) under the Community group alongside Strava on workout cards and the Export Details bottom sheet.
- **Description**:
  1. *FileFormat & Community Target Registration*: The system SHALL define `FileFormat.HEALTH_CONNECT` under `ONLINE_COMMUNITIES` with string resource `R.string.health_connect_title` and register it in `ExportType.COMMUNITY.getExportToFileFormats()`.
  2. *Application Setting Gate*: `TrainingApplication.uploadToCommunity(FileFormat.HEALTH_CONNECT)` SHALL route directly to `TrainingApplication.syncToHealthConnect()`.
  3. *Workout Initialization Lifecycle*: When a new workout is created (`ExportManager.newWorkout`), the system SHALL record an initial `ExportStatus` row for `(ExportType.COMMUNITY, FileFormat.HEALTH_CONNECT)` set to `TRACKING` if Health Connect sync is enabled, or `UNWANTED` if disabled.
  4. *Health Connect Status Recording & Broadcast*: When `HealthConnectManager.syncWorkout()` executes, the system SHALL update `(ExportType.COMMUNITY, FileFormat.HEALTH_CONNECT)` status in `ExportStatusDatabaseManager` (`PROCESSING` / `WAITING` -> `FINISHED_SUCCESS` or `FINISHED_FAILED` with error details in `ANSWER`), and SHALL invoke `ExportStatusChangedBroadcaster.broadcastExportStatusChanged(context, fileBaseName)` to refresh UI flows in real time.
  5. *Legacy Workout Synthesis & UI Display*: `ExportStatusDataProvider` and `ExportStatusRepository` SHALL synthesize missing `HEALTH_CONNECT` rows on legacy workouts as `UNWANTED` and SHALL render `Health Connect` reactively under the `COMMUNITY` group in workout cards, notifications, and `ExportDetailsDialog`.
  6. *Localization Parity*: The system SHALL preserve 100% 9-language localization parity across all resource configurations.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a completed workout and Health Connect sync enabled,
• *When* the workout is synchronized to Health Connect,
• *Then* `ExportStatus.db` contains a row for `(ExportType.COMMUNITY, FileFormat.HEALTH_CONNECT)` with status `FINISHED_SUCCESS` and the Community export group displays Health Connect as succeeded.
• *Given* a Health Connect sync failure due to missing permissions or API error,
• *When* `HealthConnectManager.syncWorkout()` catches the exception,
• *Then* `ExportStatus.db` contains status `FINISHED_FAILED` with the error message in `ANSWER`, and tapping the workout card opens `ExportDetailsDialog` displaying the error answer.
• *Given* an existing historical workout without a `HEALTH_CONNECT` export status row,
• *When* loaded in `ExportStatusDataProvider` or `ExportStatusRepository`,
• *Then* `FileFormat.HEALTH_CONNECT` is synthesized as `UNWANTED` without crashing or corrupting database rows.

**Invariants**: Strava upload behavior, WorkManager worker pipeline, existing DB schema, and 100% 9-language localization parity MUST NOT be broken.

### REQ-MIG-052: Multi-Device Incremental Cloud Auto-Import & Local Health Connect / Strava Relay [Verified]
- **Tests**: `TST-MIG-050`
- **System Requirement(s)**: `SYSREQ-DEX-004`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `BackupRestoreViewModel.kt`, `AutoImportManager.kt`, `HealthConnectWorkoutSyncWorker.kt`, `LegacyImportEngine.kt`, `TrainingApplication.java`
- **Rationale**: Secondary recording devices (e.g., handlebar bike phones) require automatic, seamless forwarding of newly recorded workouts to primary devices (phones/tablets) via cloud storage (Dropbox / Google Drive) without manual bulk recovery initiation or blocking cluster dialogs, with optional relays to local Health Connect and Strava on the receiving device.
- **Description**:
  1. *Dedicated Auto-Import Tab Navigation (`ImportBackupTabsScreen.kt`)*: The system SHALL insert the `Auto-Import` tab at index 1 (second position), maintaining the exact sequence `1. Import | 2. Auto-Import | 3. Backup | 4. Restore`.
  2. *Independent Cloud Source & Format Controls*: The system SHALL provide independent master and source toggles (`auto_import_enabled`, `auto_import_dropbox`, `auto_import_google_drive`), format selection (`.fit`, `.tcx`, `.gpx`, `all` defaulting to `.fit`), and cloud connection status indicators.
  3. *Zero-I/O Pre-Download Deduplication & Non-Interactive Processing*: The incremental cloud sync engine SHALL query configured cloud folders, pre-filter remote file entries against `ExistingWorkoutsSnapshot` before downloading any file bytes, and parse new files non-interactively without interactive route cluster selection dialogs.
  4. *Local Health Connect Relay Worker*: When `syncImportedWorkoutsToHealthConnect` is enabled, the system SHALL enqueue `HealthConnectWorkoutSyncWorker.enqueue(context, workoutId)` to synchronize newly imported workouts to local Android Health Connect and update `(ExportType.COMMUNITY, FileFormat.HEALTH_CONNECT)` status.
  5. *Optional Dual-Account Strava Relay & Manual Trigger*: When `uploadImportedWorkoutsToStrava` is enabled, the system SHALL schedule Strava upload on the receiving device. The tab SHALL display the last sync timestamp/summary and provide an immediate "Sync Now" trigger.
  6. *Cooldown-Gated App & Workout List Trigger*: The system SHALL evaluate Auto-Import on app start / workout list display subject to a cooldown period to prevent excessive cloud polling.
  7. *9-Language Localization Parity*: All Auto-Import controls and status messages SHALL be localized across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Acceptance Criteria (Given-When-Then)**:
• *Given* the user opens `Import & Backup`,
• *When* the screen renders its top tab row,
• *Then* the tabs are ordered `Import` (0), `Auto-Import` (1), `Backup` (2), and `Restore` (3), and selecting `Auto-Import` presents controls for cloud sources, format selection defaulting to `.fit`, Health Connect/Strava relays, and Sync Now.
• *Given* Auto-Import is enabled and a new `.fit` file is available in Dropbox or Google Drive,
• *When* an auto-import cycle executes,
• *Then* only files whose `fileBaseName` is absent from `ExistingWorkoutsSnapshot` are downloaded, imported non-interactively, and repositories (`WorkoutRepository`, `PeriodsRepository`, `WorkoutClusterRepository`) are reactively refreshed.
• *Given* a workout is auto-imported with `syncImportedWorkoutsToHealthConnect` enabled,
• *When* the import transaction completes,
• *Then* `HealthConnectWorkoutSyncWorker.enqueue(context, workoutId)` is invoked and the Health Connect export status is tracked.

**Invariants**: Zero duplicate workout insertion; zero blocking UI dialogs during auto-import; zero network polling when Auto-Import is disabled.

### REQ-MIG-053: Lossless FIT Round-Trip for Workout Metadata, HRV Protocol Tags & Bike Radar Developer Fields [Verified]
- **Tests**: `TST-MIG-051`
- **System Requirement(s)**: `SYSREQ-DEX-002`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `FitFileWriter.java`, `FitWorkoutParser.kt`, `ParsedWorkoutData.kt`, `LegacyImportEngine.kt`
- **Rationale**: When workouts recorded on one device are exported to `.fit` and imported on a secondary device (e.g. tablet or replacement phone), workout title, notes, gear assignment, commute/trainer/race flags, HRV protocol tags, and Bike Radar metrics must round-trip with 100% fidelity without loss or corruption.
- **Description**:
  1. *FIT Session Metadata Developer Fields (`FitFileWriter.java`)*: The system SHALL define an application metadata Developer Data ID (`APP_METADATA_DEVELOPER_DATA_ID`) and write Developer Fields onto `SessionMesg`: field 0 `workout_name` (string), field 1 `notes` (string), field 2 `equipment_name` (string), field 3 `method` (string), field 4 `goal` (string), field 5 `is_commute` (uint8), field 6 `is_trainer` (uint8), and field 7 `is_race` (uint8).
  2. *Standard FIT Metric Serialization (`FitFileWriter.java`)*: The system SHALL serialize `normalizedPower`, `trainingStressScore`, `intensityFactor`, and `totalWork` onto `SessionMesg` using standard FIT SDK setters when available in workout data.
  3. *FIT Session Metadata & Developer Field Parsing (`FitWorkoutParser.kt`)*: The parser SHALL decode `workoutName`, `workoutNotes`, `equipmentName`, `method`, `goal`, `isCommute`, `isTrainer`, and `isRace` from `SessionMesg` developer fields into `ParsedWorkoutData`.
  4. *Bike Radar Developer Field Ingestion (`FitWorkoutParser.kt`)*: The parser SHALL decode Garmin FIT V2.0 Developer Fields from `RecordMesg` into `bufferedSamples` and `foundSensors` for field 0 (`radar_current` -> `SensorType.RADAR_THREAT_COUNT`), field 1 (`radar_total` -> `SensorType.RADAR_VEHICLE_COUNT`), field 2 (`passing_speed` -> `SensorType.RADAR_APPROACH_SPEED_mps`), field 4 (`passing_distance` -> `SensorType.RADAR_PASSING_DISTANCE_m`), and field 5 (`large_vehicle_count` -> `SensorType.RADAR_LARGE_VEHICLE_COUNT`).
  5. *Persistence & Equipment Resolution (`LegacyImportEngine.kt`)*: When importing parsed FIT data via `importFromFitResult`, the system SHALL persist `workoutName`, `workoutNotes`, match `equipmentName` against `EquipmentDatabaseManager` to resolve `equipmentId`, persist `isCommute`, `isTrainer`, `isRace`, `method`, and `goal`, register radar sensors in `foundSensors`, and calculate radar extrema.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded with title, description, assigned equipment name, commute/trainer/race flags, and HRV protocol tags,
• *When* exported to `.fit` via `FitFileWriter` and parsed via `FitWorkoutParser`,
• *Then* `ParsedWorkoutData` contains exact matching metadata, flags, and HRV tags.
• *Given* a workout with Bike Radar developer telemetry (`radar_current`, `radar_total`, `passing_speed`, `passing_distance`, `large_vehicle_count`),
• *When* exported to `.fit` and parsed by `FitWorkoutParser`,
• *Then* `bufferedSamples` contains all radar sample values and `foundSensors` contains the corresponding radar `SensorType` entries.
• *Given* a third-party `.fit` file lacking developer fields,
• *When* parsed by `FitWorkoutParser`,
• *Then* the parser completes successfully without exceptions and populates standard telemetry with null metadata fallbacks.

**Invariants**: Third-party FIT file backward compatibility preserved; zero data loss during FIT export/import cycle; Garmin FIT SDK V2.0 Developer Field profile compliance.

### REQ-EXP-043: Standard Garmin FIT Pedal Power Balance, Torque Effectiveness & Pedal Smoothness Serialization and Lossless Round-Trip [Verified]
- **Tests**: `TST-EXP-043`
- **System Requirement(s)**: `SYSREQ-DEX-003`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `FitFileWriter.java`, `FitWorkoutParser.kt`, `WorkoutExportHeader.kt`, `WorkoutRepository.kt`
- **Rationale**: Cyclists using dual-sided ANT+ or Bluetooth LE power meters expect recorded Left/Right pedal power balance, torque effectiveness (TE), and pedal smoothness (PS) metrics exported into standard Garmin `.fit` files for analysis on Intervals.icu, Strava, TrainingPeaks, GoldenCheetah, and WKO5, as well as preserved across `.fit` backup/restore cycles without data loss or bilateral inversion.
- **Description**:
  1. *Sample Streaming Extension (`WorkoutExportHeader.kt` & `WorkoutRepository.kt`)*: `WorkoutSampleRecord` SHALL include nullable fields `pedalPowerBalance: Double?`, `torqueEffectivenessL: Double?`, `torqueEffectivenessR: Double?`, `pedalSmoothnessL: Double?`, `pedalSmoothnessR: Double?`, and `pedalSmoothnessCombined: Double?`. `WorkoutRepository.streamSamples(...)` SHALL project `SensorType.PEDAL_POWER_BALANCE`, `TORQUE_EFFECTIVENESS_L`, `TORQUE_EFFECTIVENESS_R`, `PEDAL_SMOOTHNESS_L`, `PEDAL_SMOOTHNESS_R`, and `PEDAL_SMOOTHNESS` while preserving $O(1)$ memory streaming (`REQ-DAT-042.2`) and zero SQLite imports in `FitFileWriter.java` (`REQ-DAT-042.5`).
  2. *Standard FIT Trackpoint Serialization (`FitFileWriter.java`)*: For trackpoint records containing pedal dynamics:
     - `pedalPowerBalance` (normalized Right % `0..100` per `REQ-CON-022`) SHALL be encoded via `record.setLeftRightBalance((short) ((Math.round(clampedRightPct) & LeftRightBalance.MASK) | LeftRightBalance.RIGHT))` where `LeftRightBalance.RIGHT = 0x80` and `LeftRightBalance.MASK = 0x7F`.
     - `torqueEffectivenessL` and `torqueEffectivenessR` (`0.0..100.0%`) SHALL be serialized via `record.setLeftTorqueEffectiveness(Float)` and `record.setRightTorqueEffectiveness(Float)`.
     - `pedalSmoothnessL`, `pedalSmoothnessR`, and combined `pedalSmoothnessCombined` (`0.0..100.0%`) SHALL be serialized via `record.setLeftPedalSmoothness(Float)`, `record.setRightPedalSmoothness(Float)`, and `record.setCombinedPedalSmoothness(Float)`.
  3. *Lap & Session Aggregate Serialization (`FitFileWriter.java`)*: During sample streaming, the exporter SHALL accumulate online sums and counts in $O(1)$ memory. When valid samples exist:
     - `LapMesg` and `SessionMesg` SHALL encode average balance via `setLeftRightBalance(Integer)` using `LeftRightBalance100` (`(Math.round(avgRightPct * 100.0) & LeftRightBalance100.MASK) | LeftRightBalance100.RIGHT`, `LeftRightBalance100.RIGHT = 0x8000`, `LeftRightBalance100.MASK = 0x3FFF`, `0.01%` resolution).
     - `LapMesg` and `SessionMesg` SHALL encode averages via `setAvgLeftTorqueEffectiveness`, `setAvgRightTorqueEffectiveness`, `setAvgLeftPedalSmoothness`, `setAvgRightPedalSmoothness`, and `setAvgCombinedPedalSmoothness`. Workouts lacking these sensors SHALL leave corresponding fields unset (`null`).
  4. *Lossless FIT Ingestion (`FitWorkoutParser.kt`)*: The parser SHALL decode `RecordMesg.getLeftRightBalance()` (extracting Right % when `LeftRightBalance.RIGHT` is set and populating `SensorType.PEDAL_POWER_BALANCE`), `getLeftTorqueEffectiveness()`, `getRightTorqueEffectiveness()`, `getLeftPedalSmoothness()`, `getRightPedalSmoothness()`, and `getCombinedPedalSmoothness()` into `bufferedSamples` and register the corresponding `SensorType` entries in `foundSensors` (`REQ-MIG-053`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a recorded cycling workout containing `PEDAL_POWER_BALANCE = 52.0` (48% L / 52% R), `TORQUE_EFFECTIVENESS_L = 78.5`, `TORQUE_EFFECTIVENESS_R = 81.0`, `PEDAL_SMOOTHNESS_L = 24.0`, and `PEDAL_SMOOTHNESS_R = 25.5`,
• *When* exported to `.fit` via `FitFileWriter` and decoded using Garmin FIT SDK `Decode`,
• *Then* `RecordMesg.getLeftRightBalance()` equals `(52 | LeftRightBalance.RIGHT)`, `getLeftTorqueEffectiveness()` / `getRightTorqueEffectiveness()` equal `78.5f` / `81.0f`, and `getLeftPedalSmoothness()` / `getRightPedalSmoothness()` equal `24.0f` / `25.5f`.
• *Given* a multi-lap workout with recorded L/R balance, TE, and PS samples,
• *When* exported to `.fit`,
• *Then* `LapMesg` and `SessionMesg` populate `left_right_balance` using `LeftRightBalance100` (`0.01%` resolution with `LeftRightBalance100.RIGHT = 0x8000`) and populate average TE and PS fields, while workouts without these sensors omit them cleanly (`null`).
• *Given* a workout with L/R balance, TE, and PS exported to `.fit` by `FitFileWriter`,
• *When* re-imported through `FitWorkoutParser`,
• *Then* `SensorType.PEDAL_POWER_BALANCE`, `TORQUE_EFFECTIVENESS_L/R`, and `PEDAL_SMOOTHNESS_L/R` (or combined `PEDAL_SMOOTHNESS`) are restored into `WorkoutSamples` and `foundSensors` without L/R inversion.

**Invariants**: $O(1)$ heap memory overhead during FIT export stream (`REQ-DAT-042.2`); zero SQLite imports or direct database calls in `FitFileWriter.java` (`REQ-DAT-042.5`); Right-pedal 0..100% normalization preserved (`REQ-CON-022.1`); clean omission for absent sensors without artificial 0% artifacts.

---

### REQ-EXP-044: Polar Raw Sensor Telemetry Sidecar Export & Aftermath Diagnostic ZIP Bundle Sharing (`ATT-8020`) [Verified]
- **Tests**: `TST-EXP-044`
- **System Requirement(s)**: `SYSREQ-DEX-001`, `SYSREQ-DEX-004`, `SYSREQ-DEX-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `FileFormat.java`, `ExportType.java`, `ExportManager.java`, `TrainingApplication.java`, `RawSensorFileExporter.kt`, `DropboxUploader.java`, `GoogleDriveUploader.kt`, `ExportSettingsDialog.kt`, `SensorDiagnosticBundlePackager.kt`, `SensorDiagnosticShareSheet.kt`, `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `docs/architecture.md`
- **Rationale**: Persisting raw sensor telemetry in external `.polar_raw.csv.gz` files (`ATT-8019`) enables algorithm R&D and diagnostic replay; exporting sidecars through `ExportManager` to local public storage, Dropbox, and Google Drive grants data sovereignty, while a 1-tap Aftermath diagnostic bundle share (`.zip`) empowers athletes to report sensor metric discrepancies (e.g., swim turn miscounting, stroke misclassification, running cadence glitches) directly to support with complete raw and computed context and user privacy controls.
- **Description**:
  1. *Multi-Destination Raw Sidecar Export (`REQ-EXP-044.1`)*:
     - `FileFormat.java` SHALL define `RAW_SENSOR("raw_sensor", ".polar_raw.csv.gz", R.string.file_format_raw_sensor)` with MIME type `"application/gzip"`.
     - `ExportManager` SHALL support exporting `RAW_SENSOR` across `ExportType.FILE` (via `RawSensorFileExporter` copying to MediaStore/Public Downloads), `ExportType.DROPBOX`, and `ExportType.GOOGLE_DRIVE`.
     - `TrainingApplication` and `ExportSettingsDialog` SHALL provide an opt-in toggle (`export_raw_sensor`) defaulting to `false`.
     - For workouts lacking a `.polar_raw.csv.gz` sidecar file, `ExportManager`, `DropboxUploader`, and `GoogleDriveUploader` SHALL complete with a graceful skip (status success / skipped) and MUST NOT fail standard FIT/TCX/GPX/CSV exports or crash the export pipeline.
  2. *1-Tap Aftermath Sensor Diagnostic ZIP Packaging (`REQ-EXP-044.2`)*:
     - When a workout has an available `.polar_raw.csv.gz` sidecar (`PolarRawTelemetryPruner.hasSidecar`), Workout Details (*Aftermath*) SHALL present a "Report Raw Sensor Data" action in the header context menu and diagnostic banner.
     - `SensorDiagnosticBundlePackager` SHALL assemble a compressed `.zip` archive inside `cache/diagnostics/` containing:
       (a) the raw `.polar_raw.csv.gz` sidecar stream (`ATT-8019`),
       (b) `workout_summary.json` containing sport, duration, distance, calories, extrema, app version, Android OS version, and sensor model/firmware,
       (c) `laps.json` or `laps.csv` containing computed lap and pool length intervals,
       (d) `samples_1hz.csv` containing 1-Hz computed sensor series from `WorkoutSamplesDatabaseManager`, and
       (e) `diagnostic_metadata.json` including an optional user-entered symptom description note.
  3. *Privacy-Preserving GPS Stripping Toggle (`REQ-EXP-044.3`)*:
     - The diagnostic share sheet SHALL provide an explicit "Include GPS Location" toggle (default `false`).
     - When unchecked, `SensorDiagnosticBundlePackager` SHALL strictly omit latitude and longitude coordinates from `samples_1hz.csv` and `summary.json` prior to zip creation, safeguarding user location privacy.
  4. *Secure FileProvider Sharing & 9-Locale Localization (`REQ-EXP-044.4`)*:
     - `file_paths.xml` SHALL declare `<cache-path name="diagnostics" path="diagnostics/" />`.
     - The diagnostic sheet SHALL dispatch `Intent.ACTION_SEND` (or `mailto:rainer.blind@gmail.com`) granting `FLAG_GRANT_READ_URI_PERMISSION` for the generated `.zip` URI via `FileProvider`.
     - All string resources across all 9 supported locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`) SHALL be defined in `strings_export.xml` or `strings_devices.xml` with zero emojis (`Rule 38`, `ATT-5400`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* raw sensor export is enabled in Export Settings and a finished workout has a `.polar_raw.csv.gz` sidecar,
• *When* `ExportManager` executes for Local Folder, Dropbox, or Google Drive,
• *Then* the sidecar is exported to public downloads and cloud storage without altering standard format exports.
• *Given* a workout recorded without a raw sensor sidecar,
• *When* `ExportManager` executes with raw sensor export enabled,
• *Then* raw sensor export gracefully skips without error, and all standard formats export successfully.
• *Given* a workout with an existing `.polar_raw.csv.gz` sidecar viewed in Workout Details,
• *When* the user taps "Report Raw Sensor Data", enters a description note, unchecks "Include GPS Location", and taps "Share Diagnostic Bundle",
• *Then* a valid `.zip` is generated containing the raw sidecar, computed laps, and anonymized 1-Hz series without coordinates, and an `ACTION_SEND` share intent is launched via `FileProvider`.

**Invariants**: Standard exports (`.fit`, `.tcx`, `.gpx`, `.csv`) completely unaffected by sidecar availability; zero BLE or UI thread blocking during diagnostic zip creation; GPS stripping strictly enforced when opted out; `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

### REQ-MIG-054: Preserve Explicit Strava Upload Opt-Out (`UPLOAD_TO_STRAVA = 0`) During Inferred Identity Application [Verified]
- **Tests**: `TST-MIG-052`
- **System Requirement(s)**: `SYSREQ-DEX-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSummariesDatabaseManager.java`, `TcxImportStravaToggleTest.kt`
- **Rationale**: During TCX/GPX/FIT import with `uploadToStrava = false`, `insertImportedWorkout()` sets `UPLOAD_TO_STRAVA = 0`, while `refreshClusters()` on an empty `RouteClusters.db` triggers `migrateHistory()` -> `assignClusterToWorkout()` -> `applyInferredIdentity()`. Treating `0` as "not yet decided" (`!= 1`) silently overrides explicit opt-outs to `1`.
- **Description**:
  1. *Tri-State Sentinel Guard (`WorkoutSummariesDatabaseManager.applyInferredIdentity()`)*: `applyInferredIdentity()` SHALL query `WorkoutSummaries.UPLOAD_TO_STRAVA` using `try (Cursor ...)` and overwrite `UPLOAD_TO_STRAVA` with `identity.getUploadToStrava()` ONLY when the target row exists AND the cell is undecided (SQL `NULL` via `cursor.isNull(0)` or `-1`, matching DDL `DEFAULT -1`).
  2. *Explicit Decision Preservation*: Existing explicit decisions (`0` = opt-out, `1` = opt-in) MUST NOT be overwritten, while `SPORT_ID`, `B_SPORT`, and `EQUIPMENT_ID` continue to update normally.
  3. *Missing Row Safety & LoC Ceiling*: Non-existent `workoutId` values MUST execute as a safe no-op without throwing, and `WorkoutSummariesDatabaseManager.java` MUST remain `<= 3,148` lines.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an empty `RouteClusters.db` and Strava connected, *When* `LegacyImportEngine.importFromTcxResult(..., uploadToStrava = false)` runs without any cluster seed workaround, *Then* `UPLOAD_TO_STRAVA` remains `0` and zero Strava export jobs are scheduled.
• *Given* a workout row with `UPLOAD_TO_STRAVA = -1` (live default) or SQL `NULL`, *When* `applyInferredIdentity()` runs, *Then* `UPLOAD_TO_STRAVA` updates to `identity.getUploadToStrava()`.
• *Given* a workout row with explicit `0` or `1`, or a non-existent `workoutId`, *When* `applyInferredIdentity()` runs, *Then* `0`/`1` stays unchanged and missing rows are safe no-ops.

**Invariants**: Live workouts (`DEFAULT -1`) still receive inferred `uploadToStrava` (`1` or `0` per `REQ-EXP-006`); explicit `1` opt-ins stay protected (`ATT-1116`); `getInt()` stays unchanged for all other callers; `WorkoutSummariesDatabaseManager.java <= 3,148` LoC.

---

### REQ-MIG-055: Thread- & DST-Safe `java.time` ISO-8601 Timestamp Parsing in GPX/TCX Parsers & `BaseExporter` Column Index-0 Contract (`ATT-6045`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-MIG-055`
- **System Requirement(s)**: `SYSREQ-DEX-004`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutTimestampParser.kt`, `GpxWorkoutParser.kt`, `TcxWorkoutParser.kt`, `LegacyImportEngine.kt`, `BaseExporter.java`
- **Rationale**: `GpxWorkoutParser.kt:45` and `TcxWorkoutParser.kt:42` share unsynchronized `SimpleDateFormat` singletons invoked concurrently across 3 `Dispatchers.IO` workers outside `importMutex` (`LegacyImportEngine.kt:215-248,437,560,623,745`), causing race-condition corruption, DST spring-forward `+1h` shifts via local-timezone lenient parsing, truncation at index 19 (`.SSS` & explicit offsets dropped), and per-sample allocations. Meanwhile `BaseExporter.java:304,322` reject valid column index `0` (`index <= 0`), and `LegacyImportEngine.kt:84` retains an unused `tcxTimeFormat` field (`ATT-6045`).
- **Description**:
  1. *Immutable `java.time` ISO-8601 UTC-Wall-Clock Parsing (`REQ-MIG-055.1`)*:
     - `GpxWorkoutParser` and `TcxWorkoutParser` SHALL replace all `SimpleDateFormat` fields and per-sample/per-lap allocations with shared immutable, thread-safe `java.time` (`DateTimeFormatter`, `OffsetDateTime`/`LocalDateTime`, `ZoneOffset.UTC`, `Duration`) helpers (`minSdk 26`).
     - Timestamp normalization SHALL convert offset-aware ISO-8601 strings (`Z`, `+HH:MM`, `-HH:MM`, optional fractional seconds `.SSS`) via `OffsetDateTime` -> `.withOffsetSameInstant(ZoneOffset.UTC)` and bare offset-less ISO timestamps via `LocalDateTime`, formatting identically to `"yyyy-MM-dd HH:mm:ss"` (UTC-Wall-Clock) outside DST gaps with zero DST gap/fall-back offset distortion, while returning the raw input string unchanged on malformed inputs.
     - `GpxWorkoutParser` SHALL compute lap duration from `"yyyy-MM-dd HH:mm:ss"` bounds via `java.time.Duration` in UTC without allocating `SimpleDateFormat`.
  2. *`BaseExporter` Column Index `0` & Dead-Code Cleanup (`REQ-MIG-055.2`)*:
     - `BaseExporter.myGetStringFromCursor` (`:304`) and `myGetBooleanFromCursor` (`:322`) SHALL check `if (index < 0)` (matching `dataValid` at `:279`) so column index `0` is read cleanly while preserving `"" -> null` and `> 0` boolean semantics.
     - Unused `LegacyImportEngine.tcxTimeFormat` (`:84`) SHALL be removed, while `ExportStatusDatabaseManager.getDatabase()` MUST remain intact for `DatabaseConnectionManager.getActiveDatabases()`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* concurrent calls to `GpxWorkoutParser.parse` and `TcxWorkoutParser.parse` across 3+ threads plus timestamps with `Z`, `+02:00`, `-05:00`, `.SSS`, bare local ISO, and DST spring-forward/fall-back times (`2026-03-29T02:30:00Z`, `2026-10-25T02:30:00Z`) under a DST-observing `TimeZone` (e.g., `Europe/Berlin`), *When* parsed, *Then* outputs are deterministic, byte-identical to `"yyyy-MM-dd HH:mm:ss"` UTC-Wall-Clock outside DST gaps with zero `+1h` DST distortion, and malformed strings fall back to raw text.
• *Given* a `Cursor` projecting target string/boolean columns at column index `0` vs missing `-1`, *When* `BaseExporter.myGetStringFromCursor` / `myGetBooleanFromCursor` are invoked, *Then* index `0` returns the non-empty string / `> 0` boolean and index `-1` returns `null` / `false`.
• *Given* the built codebase, *When* inspected, *Then* `LegacyImportEngine.tcxTimeFormat` is removed, `ExportStatusDatabaseManager.getDatabase()` remains preserved, and `ArchitectureConformanceContractTest` bounds remain intact.

**Invariants**: Byte-identical `"yyyy-MM-dd HH:mm:ss"` UTC-Wall-Clock output outside DST gaps; `FitWorkoutParser` local-time contract (`REQ-MIG-038`); raw-string fallback on malformed timestamps; `ExportStatusDatabaseManager.getDatabase()` (`DatabaseConnectionManager.kt:122`); `BaseExporter` empty-string-to-`null` & `> 0` boolean semantics; `Localization Parity: N/A - no new or modified UI strings`.

---

### REQ-DAT-045: Unified `BatchMetadataLoader`, Chunked Export-Status & Strava Batch Queries, and Lossless `fromCursor` Delegation (`ATT-6038`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-DAT-041`
- **System Requirement(s)**: Derived (SW-only) — Internal SQLite batch metadata & export-status query optimization and cursor-mapper consolidation without external sensor, REST API, or backup schema changes (refining `REQ-DAT-033` and `REQ-DAT-029.6`)
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `BatchMetadataLoader.kt`, `ExportStatusDatabaseManager.java`, `ExportStatusRepository.kt`, `ExportStatusDataProvider.kt`, `StravaUploadDbHelper.java`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`, `PeriodsRepository.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: `WorkoutRepository.loadAllWorkouts` triggers `~15–16` SQLite queries per workout (`~750–800` per 50-workout batch): `4x` repeated `healStaleProcessingRows` + `SELECT` via `ExportStatusDataProvider.createGroupData` (`8` queries/workout), plus `fromSnapshot` fallbacks on empty `sportTypes`, `equipmentNames`, `locationNames` (full-table geofence scans), and `athleteMetrics`. Meanwhile `StravaUploadDbHelper.getStravaActivityDataForWorkouts` lacks `<= 999` SQLite bind-arg chunking, `ExportStatusDatabaseManager` retains two dead `getExportStatusMap` overloads, and `WorkoutDataMapper.fromCursor` duplicates `readCursorSnapshot` + `fromSnapshot`.
- **Description**:
  1. *Chunked Batch Export-Status Queries & Dead Overload Removal (`REQ-DAT-045.1`)*:
     - `ExportStatusDatabaseManager` SHALL expose `getExportRowsForWorkouts(Collection<String> fileBaseNames): Map<String, List<ExportRow>>`, executing `healStaleProcessingRows()` at most **once** per batch call and querying `ExportStatusDbHelper.TABLE` via `WHERE file_base_name IN (...)` with SQLite bind-argument chunks `<= 500` (`<= 999` ceiling) and `try (Cursor ...)` safety (`REQ-DAT-043`).
     - `ExportStatusRepository` SHALL encapsulate `getExportRowsForWorkouts` and `healStaleProcessingRows()` without exposing `*DatabaseManager` or `Cursor` getters (`Rule 8.7`). `ExportStatusDataProvider` SHALL provide a preloaded-rows `createGroupData` path used by `WorkoutRepository.loadAllWorkouts` so `4` export types evaluate in memory with zero per-workout export queries.
     - Both dead `ExportStatusDatabaseManager.getExportStatusMap` overloads (`0` callers) SHALL be removed.
  2. *Chunked Strava Batch Query Parity (`REQ-DAT-045.2`)*:
     - `StravaUploadDbHelper.getStravaActivityDataForWorkouts(Collection<String> fileNames)` SHALL chunk `WHERE file_base_name IN (...)` bind arguments at `<= 500` (`<= 999` SQLite limit) while returning identical `Map<String, String>` results.
  3. *Shared `BatchMetadataLoader` in `repositories/` (`REQ-DAT-045.3`)*:
     - `BatchMetadataLoader` SHALL reside in `com.atrainingtracker.trainingtracker.repositories` (preventing net-new `ui/*` DB bindings per `ArchitectureConformanceContractTest` and keeping `WorkoutRepository.kt <= 5,117` LoC) and populate all `WorkoutDataMapper.BatchMetadata` fields (`extrema`, `stravaData`, `clusterNames`, `laps`, `sportTypes`, `equipmentNames`, `locationNames`, `cardioPowerDynamics`, and `athleteMetrics`).
     - Batch `athleteMetrics` resolution SHALL load `FTP_WATTS` and `WEIGHT_KG` history once per batch (`AthleteMetricsDatabaseManager.getEntriesByType`) and resolve each workout's effective values identically to `SettingsDataStoreJavaHelper.resolveEffectiveFtpAt` / `resolveEffectiveWeightKgAt` (`timestamp_ms <= startTimeMs`, fallback to latest entry, fallback to `SettingsDataStore`).
     - `WorkoutRepository.loadAllWorkouts`, `PeriodsRepository` (`getWorkoutsForRange`, `performHierarchicalMigration`), and `WorkoutClusterRepository` (`loadWorkoutsWithBatchMetadata`) SHALL delegate metadata loading to `BatchMetadataLoader`.
  4. *Lossless `fromCursor` -> `fromSnapshot` Delegation (`REQ-DAT-045.4`)*:
     - `WorkoutDataMapper.fromCursor(Cursor)` SHALL delegate to `readCursorSnapshot(cursor)` + `fromSnapshot(snapshot, singleWorkoutBatch, reconcile = true)` via `BatchMetadataLoader.loadForSingleWorkout(snapshot)` (populating the 5 non-fallback/batch maps `extrema`, `stravaData`, `clusterNames`, `laps`, `cardioPowerDynamics` with `hasCardioPowerDynamics = true` and delegating `sportTypes`, `equipmentNames`, `locationNames`, and `athleteMetrics` to `fromSnapshot`'s indexed single-item resolvers), preserving `REQ-DAT-009` / `REQ-MAP-020` self-healing and `ATT-6170` scope boundaries (`loadWorkout` export-status preservation stays out-of-scope).

**Acceptance Criteria (Given-When-Then)**:
• *Given* `N` workouts (including `N > 999`, e.g. `1,200`) passed to `ExportStatusDatabaseManager.getExportRowsForWorkouts` or `StravaUploadDbHelper.getStravaActivityDataForWorkouts`, *When* queried, *Then* bind arguments execute in `<= 500`-item chunks without `SQLiteException`, `healStaleProcessingRows()` executes at most once, and returned maps match single-item queries.
• *Given* `WorkoutRepository.loadAllWorkouts` loading a 50-workout batch, *When* executed, *Then* `BatchMetadataLoader` and `getExportRowsForWorkouts` populate all `BatchMetadata` fields (incl. `athleteMetrics`) and `exportStatuses` with `O(1)` batch queries (`<= 12` total auxiliary queries per 50-workout batch instead of `~750`) and `0` per-workout `fromSnapshot` DB fallbacks.
• *Given* `PeriodsRepository` and `WorkoutClusterRepository` batch workout loads, *When* executed, *Then* both delegate to `BatchMetadataLoader` with all `BatchMetadata` fields populated identically to single-item mapping.
• *Given* a `WorkoutDataMapper.fromCursor(Cursor)` call, *When* mapped, *Then* it delegates to `fromSnapshot(..., reconcile = true)` with a complete single-workout `BatchMetadata`, producing identical `WorkoutData` fields (`WorkoutDataMappingSymmetryTest`) and preserving `ATT-6170` `loadWorkout` boundaries.

**Invariants**: SQLite `IN (...)` bind-arg chunks strictly `<= 500` (`<= 999`); `try (Cursor ...)` safety (`tools/lint_cursor_safety.py`); zero `*DatabaseManager`/`Cursor` getters exposed by repositories (`Rule 8.7`); `WorkoutRepository.kt <= 5,117` LoC & `0` net-new `ui/*` DB callers (`ArchitectureConformanceContractTest`); `reconcile = true` preserved on `fromCursor`, `reconcile = false` preserved on batch loads; `Localization Parity: N/A - no new or modified UI strings`.

---

### REQ-EXP-045: Bounded Crash-Safe Smartphone Raw Sensor Sidecar Recording, Pure-JVM Replay Schema & Multi-File Cloud Export (`ATT-9348`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-EXP-045`
- **System Requirement(s)**: `SYSREQ-DEX-001`, `SYSREQ-DEX-004`, `SYSREQ-TRK-010`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `SmartphoneRawTelemetryRecorder.kt`, `SmartphoneRawSensorCapture.kt`, `PhoneRawTelemetryRow.kt`, `PhoneRawMetadata.kt`, `PhoneRawSidecarRecovery.kt`, `PhoneRawTelemetryReader.kt`, `RawSensorSidecarFiles.kt`, `SpeedAndLocationDevice.java`, `SpeedAndLocationDevice_ATTFused.kt`, `TrackerService.java`, `TuningPreferencesDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `RawSensorFileExporter.kt`, `BaseExporter.java`, `DropboxUploader.java`, `GoogleDriveUploader.kt`, `PolarRawTelemetryPruner.kt`, `SensorDiagnosticBundlePackager.kt`, `full_backup_content.xml`, `data_extraction_rules.xml`, `strings_devices.xml`
- **Rationale**: Offline validation of `PhoneCarriageClassifier`, `UnicycleOdometryEkf`, `KinematicAltitudeSlopeEkf`, and cadence filters (`ATT-9349`) requires bounded, crash-safe, opt-in recording of raw smartphone IMU, gyroscope, rotation-vector, barometer, step-detector, GNSS, wheel, and sport/carriage state streams alongside workouts with multi-destination export (local Downloads, Dropbox, Google Drive) symmetrical to Polar raw sidecars (`REQ-SET-117`, `REQ-EXP-044`, `REQ-STB-023`, `REQ-STB-025`).
- **Description**:
  1. *Opt-In Bounded Zero-OOM Smartphone Recorder & Sensor Tap (`REQ-EXP-045.1`)*:
     - `TuningPreferencesDataStore` (`<= 2,410` LoC) SHALL persist `recordSmartphoneRawTelemetryEnabled: Boolean` (default `false`, key `tuning_record_smartphone_raw_telemetry`) with a plain-JVM-safe guarded accessor so unit tests without Android DataStore or `filesDir` never throw.
     - When disabled (`false`), `0` files, `0` background writer coroutines, and `0` per-sample recording allocations SHALL occur. When enabled (`true`), `TrackerService` (`<= 3,559` LoC) SHALL start, resume, and stop `SmartphoneRawTelemetryRecorder` symmetrically with `PolarRawTelemetryRecorder` targeting `files/raw_sensor/<fileBaseName>.phone_raw.csv.gz` (active `.phone_raw.csv.gz.tmp`, lazy open on first frame so empty sessions discard cleanly with `0` files).
     - `SmartphoneRawTelemetryRecorder` SHALL buffer frames via `Channel<PhoneRawTelemetryRow>(capacity = 8192, onBufferOverflow = BufferOverflow.DROP_OLDEST, onUndeliveredElement = { droppedFrameCount.incrementAndGet() })` exposing `getDroppedFrameCount(): Long`, drain on `Dispatchers.IO` using a single reusable `StringBuilder` (`appendCsvLine`, **zero `String.format` per row**), periodically flush (`<= 256` rows / `<= 5 s`) into `GZIPOutputStream(..., syncFlush = true)`, and cancel its private `CoroutineScope` after `writeJob?.join()` on `stop()`.
     - `SpeedAndLocationDevice_ATTFused` (`<= 1,500` LoC) SHALL forward `TYPE_ACCELEROMETER`, `TYPE_GYROSCOPE` (registered when `isEffectiveGyroActive || recorderActive` and recorded **before** the `!isEffectiveGyroActive` EKF return guard so EKF gyro opt-out behavior stays intact), `TYPE_GAME_ROTATION_VECTOR`/`TYPE_ROTATION_VECTOR` (`values.size >= 3`, deriving `qw = sqrt(max(0.0, 1.0 - qx*qx - qy*qy - qz*qz))` when `size == 3`), `TYPE_PRESSURE`, `TYPE_STEP_DETECTOR`, GNSS `Location`, `onWheelMeasurement`, and `SportContextHint`/`PhoneCarriageMode` state updates.
  2. *Self-Describing Schema, Crash-Safe `.tmp` Salvage & Pure-JVM `ATT-9349` Reader (`REQ-EXP-045.2`)*:
     - Archives SHALL prepend `# metadata: {"schemaVersion":1,"appVersion":"...","deviceModel":"...","sportId":...,"sessionStartTimeMs":...}` followed by CSV header `elapsedRealtimeNs,epochMs,channel,v0,v1,v2,v3,v4,v5` supporting all 8 channels: `ACC(ax,ay,az)`, `GYRO(gx,gy,gz)`, `ROTVEC(qx,qy,qz,qw)`, `BARO(pressureHpa,altM)`, `STEP()`, `GNSS(lat,lon,altM,speedMps,bearingDeg,accM)`, `WHEEL(speedMps,var)`, `STATE(sportId,bSportCode,carriageCode)`.
     - `PhoneRawSidecarRecovery` SHALL salvage truncated `.phone_raw.csv.gz.tmp` archives via atomic `.salvage` staging, small-chunk `GZIPInputStream` decompression, and strict CSV validation (`PhoneRawTelemetryRow.fromCsvLine`) during both `TrackerService` crash-resume and export-time fallback.
     - Pure-JVM `PhoneRawTelemetryReader` (zero Android framework dependencies) SHALL parse `# metadata` JSON and stream typed `PhoneRawTelemetryRow` entries with graceful truncated-EOF recovery for downstream `ATT-9349`.
  3. *Multi-File `FileFormat.RAW_SENSOR` Export & Cloud Upload Parity (`REQ-EXP-045.3`)*:
     - `FileFormat.RAW_SENSOR` enum constant and SQLite export tables MUST remain unchanged.
     - Shared `RawSensorSidecarFiles.resolveExportableSidecars(context, fileBaseName)` SHALL invoke `.tmp` salvage for both Polar and smartphone sidecars and return all existing non-empty sidecar files (`[.polar_raw.csv.gz, .phone_raw.csv.gz]`).
     - `RawSensorFileExporter` (via `BaseExporter`), `DropboxUploader`, and `GoogleDriveUploader` SHALL export/upload every resolved sidecar (`Polar-only`, `Phone-only`, or `Both`) and return graceful skip/success when neither exists without impacting FIT/TCX/GPX/CSV exports.
  4. *Retention, Cascade Deletion, Diagnostic Bundle Privacy & Auto-Backup Exclusion (`REQ-EXP-045.4`)*:
     - `PolarRawTelemetryPruner` (`hasSidecar`, `deleteSidecar`, `pruneExpiredSidecars`) SHALL recognize and manage BOTH `.polar_raw.csv.gz*` and `.phone_raw.csv.gz*` symmetrically under `RawSensorRetentionPolicy` and workout cascade deletion.
     - `SensorDiagnosticBundlePackager` SHALL include `.phone_raw.csv.gz` ONLY when `includeGps == true` (omitting it when `includeGps == false` so zero GNSS coordinates leak).
     - `full_backup_content.xml` and `data_extraction_rules.xml` (`cloud-backup` and `device-transfer`) SHALL include `<exclude domain="file" path="raw_sensor" />` to protect Android's 25 MB Auto Backup quota.
  5. *Dual-Host Tuning UI & 9-Locale Localization Parity (`REQ-EXP-045.5`)*:
     - `SensorsGpsFilterSection` SHALL expose a `TuningToggleItem` (`design_guidelines.md` §1.4, no raw `Switch`) right above `RawTelemetryRetentionControl`, wired in BOTH `AdvancedTuningScreen` and `AdvancedTuningDialog` with `initial = null` async form hydration (`Rule 82`).
     - All new string keys SHALL be defined across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/` in `strings_devices.xml`) with zero emojis.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `recordSmartphoneRawTelemetryEnabled == false` (default), *When* a workout runs on `SpeedAndLocationDevice_ATTFused`, *Then* `0` `.phone_raw.csv.gz*` files, `0` writers, and `0` per-sample recording allocations occur.
• *Given* recording is enabled and `ACC`, `GYRO` (including when `isEffectiveGyroActive == false`), `ROTVEC` (`size == 3` or `4`), `BARO`, `STEP`, `GNSS`, `WHEEL`, and `STATE` frames stream (including `> 8,192` burst overflow), *When* stopped cleanly or killed mid-stream leaving a truncated `.tmp`, *Then* `DROP_OLDEST` retains newest frames with exact `getDroppedFrameCount()`, `.phone_raw.csv.gz` is finalized or salvaged via `.salvage`, and pure-JVM `PhoneRawTelemetryReader` replays metadata and all 8 channels losslessly.
• *Given* a workout with Polar-only, Phone-only, both, or neither sidecar and `export_raw_sensor == true`, *When* exported to Local Downloads, Dropbox, or Google Drive, *Then* every present sidecar is salvaged and exported/uploaded without breaking standard formats, and absent sidecars skip gracefully.
• *Given* a workout with `.phone_raw.csv.gz`, *When* pruned/deleted via `PolarRawTelemetryPruner`, *Then* `.phone_raw.csv.gz*` is removed; *When* packaged with `includeGps == false`, *Then* zero lat/lon coordinates leak; *When* backup XML rules are validated, *Then* `raw_sensor` is excluded from `full_backup_content.xml` and `data_extraction_rules.xml`.

**Invariants**: Polar `.polar_raw.csv.gz` format/path/recovery/export stays 100% byte-compatible (`REQ-SET-117`, `REQ-EXP-044`); `FileFormat.RAW_SENSOR` enum & SQLite schema stay unchanged; `SpeedAndLocationDevice_ATTFused.kt <= 1,500`, `TrackerService.java <= 3,559`, `TuningPreferencesDataStore.kt <= 2,410`, `TrainingApplication.java` untouched; `0` emojis across 9-locale `strings_devices.xml`; `ANT-Android-SDKs` untouched at `160000`.

---

### REQ-EXP-046: Pure-JVM Offline Simulation Replay Harness for Smartphone and Polar Raw Sensor Sidecars (`ATT-9349`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-EXP-046`
- **System Requirement(s)**: `SYSREQ-TRK-010`, `SYSREQ-DEX-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `PolarRawMetadata.kt`, `PolarRawTelemetryReader.kt`, `OfflineReplayModels.kt`, `OfflineTelemetryReplayHarness.kt`, `OfflineReplayGroundTruth.kt`, `OfflineReplayCli.kt`, `app/build.gradle`
- **Rationale**: Validating parameter adjustments to `PhoneCarriageClassifier`, `UnicycleOdometryEkf`, `KinematicAltitudeSlopeEkf`, ERTS smoothers, and smartphone/Polar cadence & running-dynamics estimators on real recorded `.phone_raw.csv.gz` and `.polar_raw.csv.gz` sidecars (`REQ-SET-117`, `REQ-EXP-045`) requires a fast, deterministic, pure-JVM offline replay engine runnable via Gradle/CLI without an Android device, emulator, or production APK bloat.
- **Description**:
  1. *Pure-JVM Polar Sidecar Metadata & Chunked Reader (`REQ-EXP-046.1`)*:
     - `PolarRawMetadata.kt` and `PolarRawTelemetryReader.kt` SHALL have zero `android.*` and zero `org.json.*` imports so `.polar_raw.csv.gz` sidecars parse natively on standalone pure JVM (`JavaExec`) as well as Android.
     - `PolarRawMetadata.toJsonString()` and `fromJsonString()` SHALL preserve exact JSON keys (`schemaVersion`, `appVersion`, `sensorModel`, `firmwareVersion`, `streamMode`, `accRateHz`, `gyroRateHz`, `sportId`, optional `poolLength_m`), JSON string escaping/unescaping, and safe default fallback on malformed JSON.
     - `PolarRawTelemetryReader.readStream()` SHALL read small chunks directly from `GZIPInputStream` (mirroring `PhoneRawTelemetryReader`), gracefully handle truncated GZIP EOF, and strictly drop non-newline-terminated trailing bytes.
  2. *Deterministic Chronological Multi-Sidecar Replay Engine (`REQ-EXP-046.2`)*:
     - `OfflineTelemetryReplayHarness` (residing strictly under `app/src/test/java/com/atrainingtracker/banalservice/replay/` so `0` bytes are added to the production APK) SHALL ingest a `.phone_raw.csv.gz` sidecar, one or more `.polar_raw.csv.gz` sidecars (Polar H10 and/or Verity Sense routed via `PolarRawMetadata.sensorModel`), or both together.
     - Cross-sidecar timestamps SHALL align deterministically onto a unified relative elapsed-millisecond axis (`tRelMs`: smartphone `CLOCK_BOOTTIME` `elapsedRealtimeNs` offset vs Polar `elapsedMs`, stably tie-broken by `(tRelMs, sourceOrder, seqIndex)`), feeding exact recorded frames through the production pure-Kotlin classes: `PhoneCarriageClassifier`, `QuaternionGyroProjector`, `SmartphoneImuCadenceEstimator`, `KinematicSportTypeDiscriminator`, `UnicycleOdometryEkf`, `KinematicAltitudeSlopeEkf`, `ExtendedRtsOdometrySmoother`, `ExtendedRtsAltitudeSlopeSmoother`, `PolarLiveRunningCadenceEstimator`, and `PolarH10RunningDynamicsEstimator`.
  3. *Quantitative Scorecard, Before-vs-After Diff, 1-Hz CSV & Ground-Truth Assertions (`REQ-EXP-046.3`)*:
     - The harness SHALL emit a deterministic `OfflineReplayScorecard` reporting: carriage-mode `%` distribution & transition count; cadence statistics (`mean`, `count`, pairwise `MAE` & signed bias across Phone IMU vs Polar H10 vs Polar Verity Sense) plus H10 vertical oscillation (cm) & ground contact time (ms) means; live EKF & ERTS total distance (m), ascent/descent (m), min/max slope (%), and cold-start time-to-motion latency (s).
     - `OfflineReplayDiff` SHALL compute structured before-vs-after deltas between two `OfflineReplayScorecard`s for offline parameter benchmarking.
     - The harness SHALL support optional 1-Hz CSV export (`--csv`) and optional pure-JVM JSON ground-truth sidecar assertions (`--truth`, checking expected dominant carriage mode, min dominant %, max transitions, distance/ascent tolerances, and max cadence MAE) returning explicit `GroundTruthCheckResult` pass/fail violations.
  4. *Lazy Gradle Pure-JVM CLI Task (`REQ-EXP-046.4`)*:
     - `app/build.gradle` SHALL register a lazy `JavaExec` task `:app:replayRawTelemetry` (`tasks.register("replayRawTelemetry", JavaExec)`) executing `OfflineReplayCliKt` against debug + unit-test compiled classes (`-PphoneRaw`, `-PpolarRaw`, `-Ppolar2Raw`, `-PcsvOut`, `-Ptruth`, `-PjsonOut`) without busting Gradle's `Rule 8.6` build cache or requiring Android SDK runtime mocks.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a recorded `.phone_raw.csv.gz` and/or `.polar_raw.csv.gz` (H10 and/or Verity Sense) sidecar, *When* replayed via `OfflineTelemetryReplayHarness` or `./gradlew :app:replayRawTelemetry`, *Then* replay executes deterministically on pure JVM with zero Android device/emulator and produces identical scorecards across repeated runs (carriage mode %, transition count, cadence stats & pairwise MAE/bias, H10 VO/GCT, EKF & ERTS distance/ascent/descent/min-max slope, cold-start latency).
• *Given* a parameter change or alternative replay configuration producing a candidate scorecard, *When* compared via `OfflineReplayDiff`, *Then* exact before-vs-after metric deltas are emitted offline alongside optional 1-Hz CSV output.
• *Given* a JSON ground-truth sidecar defining expected carriage mode, distance, ascent, or cadence bounds, *When* evaluated by `OfflineReplayGroundTruth`, *Then* compliant replays pass with zero violations and out-of-tolerance replays fail with explicit violation descriptions.
• *Given* a `.polar_raw.csv.gz` archive containing special JSON characters or a crash-truncated non-newline trailing line, *When* read via `PolarRawTelemetryReader.readFile()` on plain JVM (without Robolectric), *Then* metadata and complete pre-truncation rows parse cleanly with zero `android.util.Log` or `org.json` runtime errors.

**Invariants**: Zero `android.*`/`org.json.*` dependencies in `PolarRawMetadata.kt`, `PolarRawTelemetryReader.kt`, and `app/src/test/.../replay/`; 100% backward compatibility with `PolarRawTelemetryReaderTest` & `PolarRawTelemetryRecorderTest`; `0` bytes added to release APK; `0` edits to `SpeedAndLocationDevice_ATTFused.kt`, `TrackerService.java`, `TuningPreferencesDataStore.kt`, or `TrainingApplication.java`; `Localization Parity: N/A - pure-JVM developer replay tool with no UI strings`.

---

### REQ-DAT-046: Lossless In-Memory Merge in `WorkoutRepository.loadWorkout` Preserving `exportStatuses` and Transient Cache Fields (`ATT-6170`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-DAT-042`
- **System Requirement(s)**: Derived (SW-only) — Internal in-memory repository cache merge in `WorkoutRepository.loadWorkout` without external sensor, REST API, or SQLite schema changes (extending `REQ-DAT-045.4` / `REQ-STB-018.3`).
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutRepository.kt`
- **Rationale**: `WorkoutDataMapper.fromCursor` delegates to `fromSnapshot` (`REQ-DAT-045.4`), which initializes `exportStatuses = emptyList()`. Because `WorkoutRepository.loadWorkout(id)` is invoked both when opening the Edit Workout dialog (`EditWorkoutViewModel.loadWorkoutData`) and directly after `reloadWorkoutData(id)` across cluster actions (`assignClusterToWorkout`, `unassignClusterFromWorkout`, `createNewClusterFromWorkout`, and `WorkoutClusterRepository` move/assign/create/unassign), overwriting `_allWorkouts` via `newList[index] = workout` wipes cached `exportStatuses` chips and transient in-memory fields.
- **Description**:
  1. *Initial Workout Event & Radar Enrichment Preservation (`REQ-DAT-046.1`)*:
     - `WorkoutRepository.loadWorkout(id)` SHALL continue enriching `radarTrafficSummary` when applicable and posting the freshly mapped `WorkoutData` (including refreshed SQLite cluster, sport, equipment, and name fields) to `_initialWorkoutLoaded` for `EditWorkoutViewModel`.
  2. *Surgical Cache Merge via `addOrUpdateWorkout` (`REQ-DAT-046.2`)*:
     - `WorkoutRepository.loadWorkout(id)` SHALL delegate the `_allWorkouts` list update to `addOrUpdateWorkout(workout)` rather than performing `newList[index] = workout`.
     - When `id` already exists in `_allWorkouts`, `addOrUpdateWorkout(workout)` SHALL preserve existing `exportStatuses` (when `workout.exportStatuses.isEmpty()`), `mapPolyline`, `encodedAltitudes`, `encodedDistances`, and `extremaRows` (including per-row `minLatLng`/`maxLatLng`), while propagating all refreshed persisted fields (`clusterId`, `clusterName`, `workoutName`, `description`, `sportId`, `sportName`, `bSportType`, `equipmentId`, `equipmentName`, `stravaSportName`).
  3. *Uncached Insertion, CAS Purity & Scope Boundary (`REQ-DAT-046.3`)*:
     - When `id` is absent from `_allWorkouts`, `addOrUpdateWorkout(workout)` SHALL append `workout` and sort `_allWorkouts` descending by `headerData.startTimeS` under pure functional `_allWorkouts.update` CAS semantics (`REQ-STB-018.3`).
     - Sequential callers (`reloadWorkoutData(id)` followed by `loadWorkout(id)`) remain safe without adding a new per-ID lock (`1 SP` scope boundary); `WorkoutDataMapper.fromSnapshot` SHALL retain `exportStatuses = emptyList()` (`REQ-DAT-045.4`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout cached in `_allWorkouts` with non-empty `exportStatuses`, `mapPolyline`, `encodedAltitudes`, `encodedDistances`, and `extremaRows` (`minLatLng`/`maxLatLng`), *When* SQLite fields (`clusterId`, `clusterName`, `workoutName`, `sportId`, `equipmentId`) change and `WorkoutRepository.loadWorkout(id)` executes (with `fromCursor` returning `exportStatuses = emptyList()`), *Then* `_allWorkouts` preserves all existing `exportStatuses` and transient fields while reflecting the updated SQLite fields, and `_initialWorkoutLoaded` receives the refreshed `WorkoutData`.
• *Given* a workout absent from `_allWorkouts`, *When* `WorkoutRepository.loadWorkout(id)` executes, *Then* `_allWorkouts` inserts it in descending `headerData.startTimeS` order and `_initialWorkoutLoaded` receives it.

**Invariants**: `WorkoutRepository.kt <= 5,117` LoC (`ArchitectureConformanceContractTest`); `_initialWorkoutLoaded` emission preserved (`EditWorkoutViewModel`); `fromSnapshot` `exportStatuses = emptyList()` untouched (`REQ-DAT-045.4`); pure CAS `_allWorkouts.update` (`REQ-STB-018.3`); `0` DB schema or `strings*.xml` edits (`Localization Parity: N/A`).

---

### REQ-MIG-056: Dropbox Remote Filename Path-Traversal Sanitization & `SecureZipExtractor.sanitizeDriveFileName` Hardening (`ATT-6174`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-MIG-056`
- **System Requirement(s)**: `SYSREQ-DEX-004`
- **Priority**: Low
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LegacyImportEngine.kt`, `SecureZipExtractor.kt`
- **Rationale**: `LegacyImportEngine.bulkRecoverFromGoogleDrive` sanitizes remote filenames via `SecureZipExtractor.sanitizeDriveFileName(entry.name, workerDir)`, whereas `bulkRecoverFromDropbox` passes `entry.name` directly into `File(workerDir, entry.name)` before writing via `FileOutputStream`. Additionally, `SecureZipExtractor.sanitizeDriveFileName` does not explicitly reject null bytes (`\u0000`) or single-dot `"."` before path resolution (`targetPath == workerPath` passes `startsWith(workerPath)`).
- **Description**:
  1. *Dropbox Filename Sanitization Parity (`REQ-MIG-056.1`)*:
     - `LegacyImportEngine.bulkRecoverFromDropbox` SHALL validate every remote `entry.name` via `SecureZipExtractor.sanitizeDriveFileName(entry.name, workerDir)` prior to invoking `downloadFileWithRetry`.
     - Upon `SecurityException`, `bulkRecoverFromDropbox` SHALL log the violation, delete the empty `workerDir`, increment `failedCount`, and skip downloading/importing so zero files are written outside `workerDir`.
  2. *`SecureZipExtractor.sanitizeDriveFileName` Edge-Case Hardening (`REQ-MIG-056.2`)*:
     - `SecureZipExtractor.sanitizeDriveFileName(fileName, workerDir)` SHALL retain its exact function name and signature (preserving `BackupImportSecurityTest` and `bulkRecoverFromGoogleDrive` callers) and SHALL additionally reject null bytes (`\u0000`) and self-referential single-dot paths (`safeName == "."` / `targetPath == workerPath`) with `SecurityException`.
     - Existing rejection of `/`, `\`, `..` (including filenames containing literal `..` such as `ride..fit`), and blank strings SHALL remain preserved, while benign filenames (e.g., `2026-10-18-run.tcx`) resolve strictly inside `workerDir`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a Dropbox entry with traversal, separator, `..`, null-byte (`\u0000`), blank, or `"."` name (e.g., `../../evil.tcx`, `sub/evil.tcx`, `sub\evil.tcx`, `ride..fit`), *When* `bulkRecoverFromDropbox` executes, *Then* `SecurityException` is caught, `failedCount` increments, the violation is logged, empty `workerDir` is cleaned up, `downloadFileWithRetry` is never invoked, and zero files are written outside `workerDir`.
• *Given* a valid filename without path separators, `..`, `\u0000`, or `"."`, *When* `bulkRecoverFromDropbox` or `SecureZipExtractor.sanitizeDriveFileName` executes, *Then* the resolved `File` sits directly inside `workerDir` and imports normally.

**Invariants**: `SecureZipExtractor.sanitizeDriveFileName` signature & `BackupImportSecurityTest` compatibility preserved; `bulkRecoverFromGoogleDrive` parity preserved; `0` DB manager calls in `LegacyImportEngine.kt` (`RepositoryEngineCleanlinessContractTest`); `0` `strings*.xml` edits (`Localization Parity: N/A`).

---

### REQ-DAT-047: Consolidated Database Lifecycle, Schema Introspection, Functional DB/View Combinators & Parameterized Stream Summary Calculation (`ATT-6036`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-DAT-043`
- **System Requirement(s)**: Derived (SW-only) — Internal persistence lifecycle, SQLite schema helper, functional combinator, and stream-summary consolidation with zero external hardware, BLE/ANT+, Android FGS/HAL, or cloud API changes (preserving `REQ-DAT-023`, `REQ-DAT-043`, `REQ-TRK-051.1`, `REQ-MIG-041.3`).
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `BaseDatabaseManager.kt`, `SqliteSchemaUtils.kt`, `DatabaseExtensions.kt`, `StreamSummaryCalculator.kt`, `TrackingViewsRepository.kt`, 10 Java `*DatabaseManager.java` classes, 10 `PRAGMA table_info` callers, stream/bounds callers, `docs/architecture.md`
- **Rationale**: Across 10 Java `*DatabaseManager` classes, 10 `PRAGMA table_info` files (23 sites), 25 `TrackingViewsRepository` mutation methods, Kotlin DB transaction loops, and 7+ stream/bounding-box/ascent-descent callers, duplicated boilerplate introduces thread-safety defects (non-`volatile` `mDatabase` in DCL), schema-check drift, and maintenance risk without changing external behavior.
- **Description**:
  1. *Thread-Safe `BaseDatabaseManager<H>` for All 10 Java Database Managers (`REQ-DAT-047.1`)*:
     - `com.atrainingtracker.database.BaseDatabaseManager<H extends SQLiteOpenHelper>` SHALL encapsulate `protected volatile SQLiteDatabase mDatabase` and `protected final H mDbHelper`, implementing double-checked locking `public SQLiteDatabase getDatabase()` with `ATT-289` closed-DB helper close/reopen, `public synchronized void closeDatabase()`, `public synchronized boolean isDatabaseOpen()`, and `@VisibleForTesting public synchronized void setDatabaseForTesting(@Nullable SQLiteDatabase db)`.
     - All 10 Java `*DatabaseManager` classes (`DevicesDatabaseManager`, `SportTypeDatabaseManager`, `KnownLocationsDatabaseManager`, `LapsDatabaseManager`, `TrackingViewsDatabaseManager`, `WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `ExportStatusDatabaseManager`, `SegmentsDatabaseManager`, `PebbleDatabaseManager`) SHALL extend `BaseDatabaseManager<H>`, preserving `public getDatabase()` for `DatabaseConnectionManager.getActiveDatabases()` (`REQ-MIG-041.3`), `PebbleDatabaseManager.close()`/`isDatabaseOpen()`, existing singleton reset hooks, and zero DB schema version bumps.
  2. *Centralized `SqliteSchemaUtils` Across All 10 `PRAGMA table_info` Files / 23 Sites (`REQ-DAT-047.2`)*:
     - `com.atrainingtracker.database.SqliteSchemaUtils` SHALL provide `@JvmStatic` helpers `getTableColumnNames(db, tableName): Set<String>` (case-insensitive `TreeSet`, bypassing Android <=14 statement cache staleness with safe double-quoted table identifier escaping per `ATT-4968`/`REQ-TRK-051.1`), `hasColumn(db, tableName, columnName): Boolean`, fail-fast `addColumnIfNotExists(db, tableName, columnName, columnType, defaultValue = null)` (rethrows `ALTER TABLE` exceptions so SQLite `onUpgrade` transactions roll back instead of silently bumping DB version on failure, `RISK-006`), explicit `addColumnIfNotExistsLenient(...)` (catches `Exception` only, never `Throwable`, for legacy best-effort callers such as `onOpen` self-healing in `ExportStatusDatabaseManager` and legacy upgrade helpers in `WorkoutSummariesDatabaseManager` / `WorkoutClusterDatabaseManager`), and `getTableColumnInfos(db, tableName): List<ColumnInfo>` with deterministic `.use` cursor closure (`REQ-DAT-043`).
     - All 10 files (23 `PRAGMA table_info` sites: `ExportStatusDatabaseManager`, `ImportEngine`, `WorkoutRepository`, `WorkoutSummariesDatabaseManager`, `WorkoutClusterDatabaseManager`, `TrackingViewsDatabaseManager`, `MultisportLapCoordinator`, `TrackerService`, `DevicesDatabaseManager`, `SportTypeDatabaseManager`) SHALL delegate to `SqliteSchemaUtils`; `TrackerService.getTableColumnNames` (`@VisibleForTesting public static`) SHALL remain a delegating facade.
  3. *Functional Combinators (`DatabaseExtensions` & `TrackingViewsRepository.mutateViewConfig`) (`REQ-DAT-047.3`)*:
     - `com.atrainingtracker.database.DatabaseExtensions` SHALL provide `Cursor.mapRows(mapper)` (closing the cursor via `.use` and mapping every row) and `SQLiteDatabase.inTransaction(block)` (executing `beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()` with guaranteed rollback on exception), adopted across Kotlin DB managers (`RoutesDatabaseManager`, `AthleteMetricsDatabaseManager`, `ClimbsDatabaseManager`, `ElevationCacheDatabaseManager`, `HrvReadinessDatabaseManager`, `WorkoutRrDatabaseManager`, `ErtsPostWorkoutRecomputer`, `HrvPostWorkoutRecomputer`, `ImportEngine`, `EquipmentReconciliationHelper`, `ClusterWorkoutDataPort`, `WorkoutClusterRepository`) while preserving Java `CursorUtils`.
     - `TrackingViewsRepository.kt` SHALL introduce `private suspend fun <T> mutateViewConfig(block: () -> T): T` unifying all 25 `withContext(Dispatchers.IO)` + `withContext(Dispatchers.Main) { configUpdateTrigger.value++ }` mutation methods while preserving exact return types (`moveTabView(): Int`, `copyTabView(): List<Long>`) and pure read methods.
  4. *Parameterized `StreamSummaryCalculator` with Golden-Value Parity & Architecture Ratchets (`REQ-DAT-047.4`)*:
     - `com.atrainingtracker.trainingtracker.util.StreamSummaryCalculator` SHALL consolidate: (a) spatial bounding-box computation (`GeoBounds` from `List<LatLng>` or encoded polyline), (b) polyline simplification (`PolyUtil.simplify` with configurable tolerance) and encoding, (c) stream downsampling (step-size stride with optional last-element retention, 5-sample legacy stride, and distance-weighted missing-altitude interpolation) + `NumericalEncodingUtils` encoding, and (d) parameterized ascent/descent computation supporting `RAW_DELTA` (both non-null list and nullable-gap-preserving variant) and `MOVING_AVERAGE` (`windowSize = 300`, `ATT-301`).
     - `WorkoutSummariesDatabaseManager` (`updateMapAndStreams`, `migrateSpatialBoundsStatic`, `shiftAltitudeData` Case A & B, `migrateOneWorkout`), `WorkoutRepository.cropWorkout`, `LegacyImportEngine.recalculateStats`, `TrackerService.finalizeLiveSession`, `ErtsPostWorkoutRecomputer`, `RoutesDatabaseManager`, and `SegmentsDatabaseManager` SHALL delegate to `StreamSummaryCalculator`, producing bit-for-bit Golden-Value parity per caller variant (verified by characterization tests prior to refactoring). `LiveWorkoutSession` 1-Hz incremental streaming and ERTS RTS Kalman math remain unchanged.
     - Package layering MUST preserve `0` net-new `database -> ui.*` imports (`ArchitectureConformanceContractTest`), ratchet down any eliminated baseline entries (`ErtsPostWorkoutRecomputer.kt` upward UI import; `WorkoutSamplesDatabaseManager.java` monolith baseline if `<= 1,500` LoC), pass `TryWithResourcesCursorAuditTest`, and synchronize `docs/architecture.md` (`Rule 8.8`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* any of the 10 Java `*DatabaseManager` singletons (including after `DatabaseConnectionManager.quiesceAllDatabases()` closes the underlying DB), *When* `getDatabase()` is called concurrently or after closure, *Then* `BaseDatabaseManager` returns an open `SQLiteDatabase` via `volatile` double-checked locking with `ATT-289` helper close/reopen, and `DatabaseConnectionManager.getActiveDatabases()` resolves every manager cleanly.
• *Given* any of the 10 `PRAGMA table_info` callers (including quoted/dynamic sample tables per `ATT-4968` and full column metadata in `ImportEngine`), *When* column presence, column names, `ColumnInfo`, or idempotent `ALTER TABLE ADD COLUMN` are requested, *Then* `SqliteSchemaUtils` executes the query with closed cursors and identical case-insensitive semantics.
• *Given* `TrackingViewsRepository` mutation calls and Kotlin DB transaction/cursor blocks, *When* mutations run or exceptions occur inside `inTransaction`, *Then* `mutateViewConfig` executes DB writes on `Dispatchers.IO` and increments `configUpdateTrigger` on `Dispatchers.Main` (returning `T`), `mapRows` closes cursors deterministically, and `inTransaction` commits on success and rolls back on exception.
• *Given* recorded GPS, altitude, and distance sequences across `WorkoutSummariesDatabaseManager` (`updateMapAndStreams`, `shiftAltitudeData` Case A & B, `migrateSpatialBoundsStatic`, `migrateOneWorkout`), `WorkoutRepository.cropWorkout`, `LegacyImportEngine.recalculateStats`, `TrackerService.finalizeLiveSession`, `ErtsPostWorkoutRecomputer`, `RoutesDatabaseManager`, and `SegmentsDatabaseManager`, *When* `StreamSummaryCalculator` computes bounds, downsampled/encoded streams, polyline, and caller-specific ascent/descent, *Then* outputs match each caller's pre-refactor Golden-Value baseline identically.

**Invariants**: Zero DB schema version increments (`DatabaseSchemaRegistry` unchanged); `public SQLiteDatabase getDatabase()` preserved on all 10 Java DB managers (`REQ-MIG-041.3`); `PebbleDatabaseManager.close()`/`isDatabaseOpen()` & `TrackerService.getTableColumnNames()` preserved; `TryWithResourcesCursorAuditTest` (`REQ-DAT-043`) & `ArchitectureConformanceContractTest` (`REQ-PRO-050`, `REQ-PRO-129.2`) pass with downward ratchets locked in; `0` UI/string edits (`Localization Parity: N/A`).

---

### REQ-MIG-057: Authoritative `DatabaseSchemaRegistry` Constants, Classpath Parity & Non-Eager `DatabaseConnectionManager` Quiescence (`ATT-8122`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-MIG-057`
- **System Requirement(s)**: `SYSREQ-DEX-005`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `DatabaseSchemaRegistry.kt`, `DatabaseConnectionManager.kt`, `MigrationEngine.kt`, `BaseDatabaseManager.kt`, `TrackingSQLiteOpenHelper.kt`, `ExportStatusDatabaseManager.java`, `StravaUploadDbHelper.java`, `EquipmentDbHelper.java`, `ActiveDevicesDbHelper.java`, `WorkoutClusterDatabaseManager.kt`, `SportTypeEquipmentLinkManager.kt`, `RoutesDatabaseManager.kt`, `AthleteMetricsDatabaseManager.kt`, `ClimbsDatabaseManager.kt`, `ElevationCacheDatabaseManager.kt`, `HrvReadinessDatabaseManager.kt`, `WorkoutRrDbHelper.kt`, `PeriodSummariesDatabaseManager.kt`
- **Localization Parity**: `N/A - 0 string changes`
- **Rationale**: Hardcoded schema versions and filenames in `DatabaseSchemaRegistry.kt` drifted behind live `SQLiteOpenHelper` constants (`Laps.db` v2 vs v3, `Routes.db` v10 vs v11, `ExportStatus.db` v1 vs v2; wrong `"KnownLocations.db"`/`"Pebble.db"`; missing `"RouteClusters.db"`/`"SportTypeLinks.db"`), causing `MigrationEngine.validateBackupArchive()` to reject valid backups created by the current app, while `DatabaseConnectionManager.getActiveDatabases()` omitted 4 managers (`PeriodSummaries`, `ElevationCache`, `AthleteMetrics`, `Pebble`) and eagerly opened closed `.db` files on disk (`ATT-8122`, refining `REQ-MIG-041.2` & `REQ-MIG-041.3`).
- **Description**:
  1. *Authoritative Constant-Backed `DatabaseSchemaRegistry` (`REQ-MIG-057.1`)*: `DatabaseSchemaRegistry.SUPPORTED_DB_VERSIONS` SHALL reference the authoritative `DB_NAME`/`DATABASE_NAME` and `DB_VERSION`/`DATABASE_VERSION` constants of all 22 production `SQLiteOpenHelper` subclasses directly (zero hardcoded schema version literals), fixing `Laps.db` (v3), `Routes.db` (v11), `ExportStatus.db` (v2), `StartLocation2Altitude.db`, `PebbleView.db`, `RouteClusters.db` (v11), and `SportTypeLinks.db` (v1), while retaining the explicit legacy backup alias `"TrackingViews.db" to TrackingViewsDbHelper.DB_VERSION`.
  2. *Classpath-Driven Drift Prevention Contract (`REQ-MIG-057.2`)*: An automated contract test SHALL discover all concrete production `SQLiteOpenHelper` subclasses from compiled debug classes and verify exact filename and `DB_VERSION` parity against `DatabaseSchemaRegistry`.
  3. *Complete Non-Eager WAL Checkpointing & Quiescence (`REQ-MIG-057.3`)*: `DatabaseConnectionManager.getActiveDatabases(context)` and `quiesceAllDatabases(context, closeConnections)` SHALL track all 22 database managers via weak-referenced open-handle tracking (`OpenDatabaseTracker` across `BaseDatabaseManager` and Kotlin-first `TrackingSQLiteOpenHelper`), returning strictly handles where `db.isOpen == true` and NEVER eagerly instantiating singletons or opening/creating closed `.db` files on disk.
  4. *Unmodified `.attbackup` Restore & Architecture Invariants (`REQ-MIG-057.4`)*: An unmodified `.attbackup` archive created by `BackupManager.createBackup(context)` containing live `Laps.db` (v3), `Routes.db` (v11), `ExportStatus.db` (v2), `StartLocation2Altitude.db`, `RouteClusters.db`, and `SportTypeLinks.db` SHALL pass `MigrationEngine.validateBackupArchive()` and `performFullRestore()` with zero excluded DBs, adding `0` lines to capped Java monoliths (`WorkoutSamplesDatabaseManager.java <= 1,500`) and conforming to `ADR-001`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* all 22 concrete production `SQLiteOpenHelper` subclasses, *When* checked against `DatabaseSchemaRegistry`, *Then* every DB filename and `DB_VERSION` matches exactly and zero current app DBs are rejected.
• *Given* an app process where only a subset of SQLite DBs have been opened, *When* `DatabaseConnectionManager.getActiveDatabases(context)` or `quiesceAllDatabases(context, closeConnections = true)` executes, *Then* all open DBs (including `PeriodSummaries.db`, `ElevationCache.db`, `AthleteMetrics.db`, `PebbleView.db`, `ExportStatus.db`) are checkpointed/closed while unopened DBs are never initialized or created on disk.
• *Given* an unmodified backup from `BackupManager.createBackup(context)` including `Laps.db` v3, `Routes.db` v11, and `ExportStatus.db` v2, *When* restored via `MigrationEngine.performFullRestore`, *Then* validation and restore succeed cleanly.

**Invariants**: Zero DB schema version increments; `ATT-289` transparent reopen post-quiescence preserved; `REQ-STB-024.3` non-opening `PebbleDatabaseManager` preserved; `"TrackingViews.db"` legacy alias preserved; `WorkoutSamplesDatabaseManager.java <= 1,500` LoC; `0` UI/string edits.

---

### REQ-MIG-058: FIT Epoch Lap Lookup & `APP_ID`-Scoped Developer Fields, Allocation-Free HRV Timestamps, GPX Peak `maxSpeed`/XML Escaping & Numeric Health Connect Calories (`ATT-8127`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-MIG-058`
- **System Requirement(s)**: `SYSREQ-DEX-001`, `SYSREQ-DEX-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `FitWorkoutParser.kt`, `FitFileWriter.kt`, `HrvPostWorkoutRecomputer.kt`, `WorkoutTimestampParser.kt`, `GpxWorkoutParser.kt`, `GPXFileWriter.java`, `HealthConnectRecordMapper.kt`
- **Localization Parity**: `N/A - 0 string changes`
- **Rationale**: In live parsers, exporters, and mappers, unnecessary string round-trips and unscoped field numbers cause severe performance overhead and silent data corruption: `FitWorkoutParser` re-parses lap timestamp strings up to 288k times per file and reads Connect-IQ developer fields on `RecordMesg`/`SessionMesg` without checking `applicationId`, `HrvPostWorkoutRecomputer` allocates up to 57.6k `SimpleDateFormat` objects per 4h workout, `GpxWorkoutParser` stores average lap speed in `maxSpeed`, `GPXFileWriter` emits unescaped XML and runs a dead per-lap SQL query loop, and `HealthConnectRecordMapper` retains a legacy `extremaRows` string-scraping fallback that parses German `"1.250"` as `1.25` kcal (`ATT-8127`, refining `REQ-MIG-038`, `REQ-MIG-053`, `REQ-EXP-022`, `REQ-PER-034.3`, `REQ-SET-101.2`).
- **Description**:
  1. *Epoch-Based FIT Lap Matching & `APP_ID`-Scoped Developer Fields (`REQ-MIG-058.1`)*: `FitWorkoutParser.parse()` SHALL store lap start timestamps as `Long` epoch milliseconds (`Long.MIN_VALUE` sentinel for null) and assign per-record `LAP_NR` via upper-bound binary search (rightmost matching index for duplicate timestamps) when all laps are non-null and sorted, or primitive `LongArray` reverse scan (`lapStart != Long.MIN_VALUE && recordEpoch >= lapStart`) otherwise — with zero `SimpleDateFormat.parse` calls inside the record loop. `FitFileWriter` SHALL expose `RADAR_DEVELOPER_DATA_APP_ID` (`6a4e3200-667b-11e3-949a-0800200c9a66`), encode 16-byte `application_id` values unsigned (`0..255`, preventing Garmin FIT SDK `BASE_TYPE_BYTE` clamping of bytes `>= 0x80` to `0xFF`), and expose `LEGACY_RADAR_DEVELOPER_DATA_APP_ID` for pre-`ATT-8127` backward compatibility alongside `APP_METADATA_DEVELOPER_DATA_ID`. `FitWorkoutParser` SHALL register `DeveloperDataIdMesgListener` (removing stale entries when `applicationId` is invalid), reject `Fit.UINT8_INVALID` / non-16-byte / null-element IDs, prefer per-`MESG_DEF`-bound `devField.appId` over the file-wide `devDataAppIds[devIndex]` fallback (preventing chained/redefined-index spoofing), and interpret `RecordMesg` radar fields strictly for `RADAR_DEVELOPER_DATA_APP_ID` / `LEGACY_RADAR_DEVELOPER_DATA_APP_ID` and `SessionMesg` metadata fields strictly for `APP_METADATA_DEVELOPER_DATA_ID`, ignoring foreign Connect-IQ developer fields.
  2. *Allocation-Free `java.time` Timestamps in `HrvPostWorkoutRecomputer` (`REQ-MIG-058.2`)*: `HrvPostWorkoutRecomputer.parseTimestampMs()` SHALL trim input and use static immutable UTC `java.time.format.DateTimeFormatter` instances (supporting space/fractional, ISO local/UTC/offset, and compact formats) with zero per-sample `SimpleDateFormat` / `TimeZone` allocations.
  3. *True Peak `maxSpeed` in `GpxWorkoutParser` (`REQ-MIG-058.3`)*: `GpxWorkoutParser.parse()` SHALL parse raw ISO-8601 timestamps directly to UTC epoch milliseconds via `WorkoutTimestampParser.parseIsoToEpochMillis()` (preserving sub-second >1 Hz precision without 1-second truncation) and compute `ParsedLap.maxSpeed` via `maxOf` over valid trackpoint `<speed>` (`isFinite() && in 0.0..100.0`), falling back to peak valid intra-lap GPS segment speed (`dtMs > 0L`, `segDistM / (dtMs / 1000.0) in 0.0..100.0`, reusing `Location.distanceBetween` when adjacent and advancing `prevLap*` only when `curEpochMs >= prevLapEpochMs`), returning `null` if unavailable — never writing lap average speed (`distanceMeters / duration`).
  4. *XML-Escaped GPX Export & Dead Lap SQL Removal (`REQ-MIG-058.4`)*: `GPXFileWriter.doExport()` SHALL trim `workoutName`/`description` and reuse `TCXFileWriter.escapeXml` to escape `creator`, `<name>` (`workoutName` if non-blank else `startTime`), and optional `<desc>` (valid GPX 1.1 XSD sequence) and SHALL delete the unused `LapsDatabaseManager` query loop and dead `myGet()` helper.
  5. *Numeric-Only Calories in `HealthConnectRecordMapper` (`REQ-MIG-058.5`)*: `HealthConnectRecordMapper.extractCalories(workoutData)` SHALL read strictly numeric `workoutData.calories` (`> 0 -> toDouble()`, else `0.0`) with zero formatted UI string scraping from `extremaRows`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a multi-lap FIT file with both own `FitFileWriter` fields and foreign Connect-IQ developer fields (`num = 0..7`), *When* `FitWorkoutParser.parse` executes, *Then* `LAP_NR` matches numeric epoch bounds with zero `SimpleDateFormat.parse` calls, foreign fields are ignored, and own radar/metadata fields round-trip losslessly.
• *Given* 1-Hz HRV sample timestamps across all 4 supported formats under `Europe/Berlin`, *When* `HrvPostWorkoutRecomputer.parseTimestampMs` / `recomputeWorkout` executes, *Then* UTC epoch timestamps resolve deterministically with zero `SimpleDateFormat` allocations.
• *Given* a GPX file with varying speeds or a workout with `&`, `<`, `>`, `"`, `'` in name/description, *When* imported via `GpxWorkoutParser` or exported via `GPXFileWriter`, *Then* `ParsedLap.maxSpeed` equals true peak speed and GPX output is well-formed XML with `0` `LapsDatabaseManager` queries.
• *Given* `Locale.GERMANY` (`de_DE`) with `calories = 1250` (or `calories = null` with `"1.250"` extrema string), *When* `HealthConnectRecordMapper.extractRecords` runs, *Then* `1250` produces exact `1250.0` kcal and `null` emits no `TotalCaloriesBurnedRecord` (never `1.25` kcal).

**Invariants**: `FitWorkoutParser` local-time DB string contract (`REQ-MIG-038`) and lossless `FitFileWriter` round-trip (`REQ-MIG-053`, `REQ-EXP-042`) preserved; `GPXFileWriter` cropped-lap `<trkseg>` state machine (`ATT-4013`) preserved and `.java` count unchanged (`JavaCoreMigrationStrategyContractTest`); all files `< 1,500` LoC (`ArchitectureConformanceContractTest`); `0` DB schema or `strings*.xml` edits.

---

### REQ-DAT-048: SQLite Null-Safe Summary Getters, `LIMIT 1`/Single-Pragma Sample Queries, Cursor-Safe/UTC `DevicesDatabaseManager`, `paired = 1` Remote Device Filtering, Route Upgrade/Duplication Hygiene & Locale-Independent Extrema Mapping (`ATT-8128`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-DAT-044`
- **System Requirement(s)**: `Derived (SW-only)`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `DevicesDatabaseManager.java`, `LastActiveDateFormatter.kt`, `DeviceStatusRow.kt`, `DeviceSortOrder.kt`, `SensorQuotaReconciliationManager.kt`, `DeviceDataRepository.kt`, `RoutesDatabaseManager.kt`, `ExtremaData.kt`, `WorkoutDataMapper.kt`, `LiveWorkoutCardStateStore.kt`, `WorkoutRepository.kt`
- **Localization Parity**: `N/A - 0 string changes`
- **Rationale**: In live SQLite database managers and aftermath repository/mapper layers, missing `cursor.isNull(idx)` guards silently coerce SQL `NULL` to `0`/`0.0`, `WorkoutSamplesDatabaseManager` loads entire sample tables into a `CursorWindow` for `START`/`END` extrema and fires 10 consecutive `PRAGMA table_info` queries in `pruneAndShiftSamples`, `DevicesDatabaseManager` leaks a fallback `Cursor` in `getCalibrationFactor`, silently drops unknown `setInt` columns, and persists locale-dependent `lastActive` strings that fail parsing across language switches, `DeviceDataRepository.getPairedRemoteDevices` omits `AND paired = 1`, `RoutesDbHelper.onUpgrade` falls through after `onCreate(db)` for `oldVersion < 5` while `duplicateRouteAsLocal` re-queries points/waypoints, `WorkoutDataMapper` scrapes localized `"0,0"`/`"0:00"` strings for `isMinRelevant`, and `WorkoutRepository` matches `ExtremaDataRow` by localized `shortNameId` and triggers duplicate DB reloads on cluster mutations (`ATT-8128`, refining `REQ-DAT-043`, `REQ-DAT-047`, `REQ-STB-021.4`).
- **Description**:
  1. *Null-Safe Summary Getters (`REQ-DAT-048.1`)*: `WorkoutSummariesDatabaseManager` SHALL guard `getDouble(@Nullable String, String)`, `getInt(long, String)`, `getInt(@Nullable String, String)` (also returning `null` on `baseFileName == null`), `getLong(long, String)`, and `getExtremaValue(SQLiteDatabase, long, SensorType, ExtremaType)` with explicit `!cursor.isNull(idx)` checks so SQL `NULL` returns `null` rather than `0`/`0.0`. (`applyInferredIdentity` NULL/`-1` preservation from `ATT-9150` remains intact.)
  2. *`LIMIT 1` Extrema & Single-PRAGMA Crop in `WorkoutSamplesDatabaseManager` (`REQ-DAT-048.2`)*: `calcExtremaValue` for `ExtremaType.START` and `ExtremaType.END` SHALL execute a single-column query `SELECT <col> ... WHERE <col> IS NOT NULL ORDER BY _id ASC|DESC LIMIT 1` (plus safe `time > 0` guard for `AVG` speed). `pruneAndShiftSamples` SHALL fetch table columns once via `SqliteSchemaUtils.getTableColumnNames(db, table)` instead of 10 sequential `existsColumnInTable` calls, while keeping `WorkoutSamplesDatabaseManager.java <= 1,500` LoC.
  3. *Cursor-Safe `DevicesDatabaseManager`, UTC `lastActive` & `paired = 1` Filtering (`REQ-DAT-048.3`)*: `DevicesDatabaseManager.getCalibrationFactor` SHALL close both primary and fallback cursors via `try-with-resources` and fall back cleanly when `CALIBRATION_FACTOR` is missing or SQL `NULL`. `setInt` SHALL validate `key` against an explicit `ALLOWED_INT_COLUMNS` allowlist and throw `IllegalArgumentException` on unsupported keys rather than silently dropping writes. `LastActiveDateFormatter` SHALL persist canonical UTC ISO-8601 (`yyyy-MM-dd'T'HH:mm:ss'Z'`, `Locale.US`, `UTC`) via `ThreadLocal<SimpleDateFormat>` (preserving `REQ-STB-021.4` zero-churn caching) and expose `parseToEpochMillis(lastSeen)` supporting UTC ISO-8601, legacy DB ISO formats, and legacy locale `DateFormat.getDateTimeInstance()` rows for `DeviceStatusRow`, `DeviceSortOrder`, and `SensorQuotaReconciliationManager` (with explicit UTC on extra `'Z'` fallbacks). `DeviceDataRepository.getPairedRemoteDevices(protocol)` SHALL filter SQL directly with `protocol=? AND paired=1`.
  4. *`RoutesDatabaseManager` Upgrade Early-Return & Single-Read Duplication (`REQ-DAT-048.4`)*: `RoutesDbHelper.onUpgrade` SHALL `return` immediately after `onCreate(db)` when `oldVersion < 5` (without bumping `DB_VERSION`), and `duplicateRouteAsLocal` SHALL reuse `route.path` and `route.waypoints` already loaded by `getRouteById(routeId)`.
  5. *Numeric `isMinRelevant`, Enum-Keyed `ExtremaDataRow` & Single-Reload Cluster Mutations (`REQ-DAT-048.5`)*: `ExtremaDataRow` SHALL carry an optional trailing `val sensorType: SensorType? = null` populated by `WorkoutDataMapper.createExtremaRow`, `createDerivedPaceRow`, and `LiveWorkoutCardStateStore.upsertLiveExtremaAvgRow`; `isMinRelevant` SHALL evaluate numeric `rawMinVal` (`rawMinVal != null && rawMinVal.isFinite() && rawMinVal > 0.0`, and numeric `speedMax`/`1.0 / speedMax` for pace) instead of `"0"`/`"0.0"`/`"0,0"`/`"0:00"` string checks. `WorkoutRepository.updateExtremaValueInMemory`, `addOrUpdateWorkout`, and `LiveWorkoutCardStateStore.upsertLiveExtremaAvgRow` SHALL match rows by `sensorType` (falling back to localized `sensorLabel`/icon only when `sensorType == null`). `assignClusterToWorkout`, `unassignClusterFromWorkout`, and both `createNewClusterFromWorkout` overloads SHALL execute a single coalesced reload pass (`reloadWorkoutDataInternal(workoutId, notifyInitialLoaded = true)` with `released`-guarded `finally` cleanup) that updates `_allWorkouts` and posts `_initialWorkoutLoaded` (preserving `EditWorkoutViewModel` identity refresh and `reloadWorkoutData(Long)` binary signature) without calling `loadWorkout(workoutId)` a second time.

**Acceptance Criteria (Given-When-Then)**:
• *Given* SQL `NULL` columns in `WorkoutSummaries.db`, a device lacking a calibration factor in `Devices.db`, and both `paired = 1` and `paired = 0` remote devices, *When* the nullable summary getters, `getCalibrationFactor`, and `DeviceDataRepository.getPairedRemoteDevices` are invoked, *Then* `NULL` values return `null` (never `0`/`0.0`), all `Cursor` instances close cleanly, and only `paired = 1` devices are queried/returned.
• *Given* `LastActiveDateFormatter` writing under `Locale.GERMANY` and reading under `Locale.US` (plus a pre-`ATT-8128` legacy locale-formatted `lastActive` string), *When* `setLastActive`, `DeviceSortOrder.parseLastSeenTimestamp`, and `DeviceStatusRow` relative-time parsing run, *Then* UTC ISO-8601 strings parse identically across locales and legacy rows remain backward-compatible.
• *Given* a workout with thousands of samples, a `Routes.db` v4->v11 upgrade, a route duplication, a runtime language switch (`en` -> `de`), and a cluster assignment, *When* `calcExtremaValue(START/END)`, `pruneAndShiftSamples`, `RoutesDbHelper.onUpgrade`, `duplicateRouteAsLocal`, `updateExtremaValueInMemory`, and `assignClusterToWorkout` execute, *Then* `START`/`END` uses single-column `LIMIT 1`, `pruneAndShiftSamples` runs 1 `PRAGMA table_info`, `onUpgrade` returns after `onCreate`, `duplicateRouteAsLocal` executes 0 extra point/waypoint queries, `ExtremaDataRow` matches via `SensorType` across locales, and cluster assignment reads `WorkoutSummaries.db` once while still notifying `initialWorkoutLoaded`.

**Invariants**: Zero SQLite `DB_VERSION` bumps (`DatabaseSchemaRegistryParityContractTest` unchanged); `WorkoutSamplesDatabaseManager.java <= 1,500`, `WorkoutSummariesDatabaseManager.java <= 3,148`, `DevicesDatabaseManager.java <= 1,671`, `WorkoutRepository.kt <= 5,117` LoC (`ArchitectureConformanceContractTest`); `TryWithResourcesCursorAuditTest` & `DetailedDesignParityContractTest` green; `0` `.java` file count change; `0` `strings*.xml` edits.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.37` | 2026-11-22 | Lane 2 Author (`ATT-8128`, `--as agent1`) | `ATT-8128` | Stage 6 Bounce 1/5 heal: delegated `SensorQuotaReconciliationManager.parseLastSeenTimestamp` to `LastActiveDateFormatter` (fixing non-UTC ranking inversion), guarded `WorkoutRepository.reloadWorkoutDataInternal` `finally` with `released` flag, and added `SensorType`-first matching/population in `LiveWorkoutCardStateStore.upsertLiveExtremaAvgRow` (`163/163` targeted Bounce 1/5 tests PASS across 30 suites, `0` new warnings). |
| `1.0.36` | 2026-11-22 | Lane 2 Author (`ATT-8128`, `--as agent1`) | `ATT-8128` | Promoted `REQ-DAT-048` to `[Verified]` after Stage 4–5 implementation incorporating all 6 Gate 3 (`ATT-9646`) guardrails (`WorkoutSamplesDatabaseManager.java` shrunk to `1,472/1,500` LoC, `WorkoutRepository.kt` `4,975/5,117` LoC with `pendingNotifyInitialLoadedIds` coalescing), `215/215` targeted Tier-1 unit & contract tests PASS across 41 suites (`0` fail, `0` err, `0` skip, `0` new compiler warnings), and `INSTALL_REQUIRED=true` live `emulator-5554` boot smoke (`CRASH_LOGCAT_CLEAN`, `Rule 8.5` PNG inspected). |
| `1.0.35` | 2026-11-22 | Lane 2 Author (`ATT-8128`, `--as agent1`) | `ATT-8128` | Added `[Planned]` `REQ-DAT-048` (`Derived (SW-only)`, `.1`–`.5`) for SQLite null-safe summary getters, `LIMIT 1`/single-PRAGMA sample queries, cursor-safe `getCalibrationFactor` + fail-fast `setInt` + UTC ISO-8601 `LastActiveDateFormatter` (backward-compatible parsing), `paired = 1` remote-device filtering, `RoutesDbHelper.onUpgrade` early return & single-read `duplicateRouteAsLocal`, numeric `isMinRelevant`, `SensorType`-keyed `ExtremaDataRow`, and single-reload cluster mutations (`N/A - 0 string changes`). |
| `1.0.34` | 2026-11-22 | Lane 2 Author (`ATT-8127`, `--as agent1`) | `ATT-8127` | Stage 6 Bounce 1/5 heal: prioritized `MESG_DEF`-bound `devField.appId` + stale `devDataAppIds.remove(devIndex)` in `FitWorkoutParser`, switched `GpxWorkoutParser` to sub-second `WorkoutTimestampParser.parseIsoToEpochMillis()`, expanded unit coverage (`106/106` PASS across 14 suites, `0` new warnings), and verified live `emulator-5554` boot smoke (`INSTALL_REQUIRED=true`, `0` crashes, `Rule 8.5` PNG inspected). |
| `1.0.33` | 2026-11-22 | Lane 2 Author (`ATT-8127`, `--as agent1`) | `ATT-8127` | Promoted `REQ-MIG-058` to `[Verified]` after Stage 4–5 implementation (incl. unsigned FIT `application_id` encoding fixing Garmin SDK `0xFF` high-byte clamping + legacy pre-`ATT-8127` radar compatibility) and `98/98` targeted unit/contract tests PASS across 13 suites (`0` fail, `0` err, `0` skip, `0` new compiler warnings). |
| `1.0.32` | 2026-11-22 | Lane 2 Author (`ATT-8127`, `--as agent1`) | `ATT-8127` | Added `[Planned]` `REQ-MIG-058` (`SYSREQ-DEX-001`, `SYSREQ-DEX-002`, `.1`–`.5`) for FIT epoch lap matching & `APP_ID`-scoped developer fields (`RecordMesg` Radar + `SessionMesg` Metadata), allocation-free `java.time` timestamps in `HrvPostWorkoutRecomputer`, true peak `maxSpeed` in `GpxWorkoutParser`, XML-escaped GPX export with dead Lap SQL removal in `GPXFileWriter`, and numeric-only calories in `HealthConnectRecordMapper` (`N/A - 0 string changes`). |
| `1.0.30` | 2026-11-22 | Lane 3 Author (`ATT-8122`, `--as agent1`) | `ATT-8122` | Promoted `REQ-MIG-057` to `[Verified]` after Stage 4–5 implementation (`OpenDatabaseTracker` + `TrackingSQLiteOpenHelper`, 22-DB constant-backed `DatabaseSchemaRegistry`, empty-`databasesDir` rollback guard in `MigrationEngine`), `94/94` targeted unit/contract tests PASS across 19 suites (`0` fail, `0` skip, `0` new compiler warnings), and live `emulator-5554` boot smoke (`0` crashes). |
| `1.0.29` | 2026-11-22 | Lane 3 Author (`ATT-8122`, `--as agent1`) | `ATT-8122` | Added `[Planned]` `REQ-MIG-057` (`SYSREQ-DEX-005`, `.1`–`.4`) for authoritative constant-backed `DatabaseSchemaRegistry` across all 22 `SQLiteOpenHelper` subclasses, classpath drift-prevention contract, and non-eager `OpenDatabaseTracker` / `TrackingSQLiteOpenHelper` WAL quiescence in `DatabaseConnectionManager` (`N/A - 0 string changes`). |
| `1.0.28` | 2026-11-22 | Lane 1 Author (`ATT-6036`, `--as agent1`) | `ATT-6036` | Promoted `REQ-DAT-047` to `[Verified]` in Stage 5 Bounce 1 after Gate 4 Bounce 1 (`ATT-9592`, `107/107` PASS), Kotlin-first `BaseDatabaseManager.kt` conversion (`ADR-001` / `JavaCoreMigrationStrategyContractTest` `5/5` PASS), `416/416` targeted Tier-1 unit/contract tests PASS, `auto_gate.py` `PASS` bundle (`docs/engineering/test_evidence/tickets/ATT-6036/`), and live `emulator-5554` boot smoke (`0` crashes). |
| `1.0.27` | 2026-11-22 | Lane 1 Author (`ATT-6036`, `--as agent1`) | `ATT-6036` | Stage 6 Bounce 1 heal (`[Implemented]`): made `SqliteSchemaUtils.addColumnIfNotExists` fail-fast by default (rethrows `ALTER TABLE` exceptions for `DevicesDatabaseManager` / `SportTypeDatabaseManager`), added explicit `addColumnIfNotExistsLenient` (catching `Exception`, never `Throwable`) for `ExportStatus`/`WorkoutSummaries`/`WorkoutCluster`, and fixed orphaned KDoc in `TrackingViewsRepository.kt`. |
| `1.0.26` | 2026-11-22 | Lane 1 Author (`ATT-6036`, `--as agent1`) | `ATT-6036` | Promoted `REQ-DAT-047` to `[Verified]` in Rule 7.5 Stage 5 reusing Gate 4 (`ATT-9588`) independently verified `262/262` PASS across 36 suites (`0` fail, `0` skip), `3/3` safety linters PASS, `auto_gate.py` `PASS` bundle (`docs/engineering/test_evidence/tickets/ATT-6036/`), and live `emulator-5554` boot smoke (`0` crashes). |
| `1.0.25` | 2026-11-22 | Lane 1 Author (`ATT-6036`, `--as agent1`) | `ATT-6036` | Promoted `REQ-DAT-047` to `[Implemented]` (`BaseDatabaseManager.java`, `SqliteSchemaUtils.kt`, `DatabaseExtensions.kt`, `StreamSummaryCalculator.kt`, 10 Java DB managers, 23 `PRAGMA` sites, `TrackingViewsRepository.mutateViewConfig`, stream/bounds callers, and `ArchitectureConformanceContractTest` ratchets). |
| `1.0.24` | 2026-11-22 | Lane 1 Author (`ATT-6036`, `--as agent1`) | `ATT-6036` | Added `[Planned]` `REQ-DAT-047` (`Derived (SW-only)`) specifying `BaseDatabaseManager<H>` (`volatile` DCL + `ATT-289` reopen across 10 Java DB managers), `SqliteSchemaUtils` (10 files / 23 `PRAGMA table_info` sites incl. `ATT-4968`), `DatabaseExtensions` (`mapRows`/`inTransaction`) + `TrackingViewsRepository.mutateViewConfig`, and parameterized `StreamSummaryCalculator` with per-caller Golden-Value parity. |
| `1.0.23` | 2026-11-21 | Sprint Coordinator (`ATT-6174`) | `ATT-6174` | Aligned `REQ-MIG-056` `System Requirement(s)` to `SYSREQ-DEX-004` (Cloud Storage Sync parity with `REQ-MIG-054`/`REQ-MIG-055`) to prevent `Derived (SW-only)` + Change-History `SYSREQ-*` double-count in `TST-PRO-103.2`. |
| `1.0.22` | 2026-11-21 | Lane 2 Author (`ATT-6174`, `--as agent1`) | `ATT-6174` | Promoted `REQ-MIG-056` to `[Verified]` in Rule 7.5 doc-only Stage 5 reusing Gate 4 (`ATT-9555`) independently verified `30/30` PASS across 4 suites (`0` fail, `0` skip) + `4/4` linters PASS @ `993be8d5`. |
| `1.0.21` | 2026-11-21 | Lane 2 Author (`ATT-6174`, `--as agent1`) | `ATT-6174` | Promoted `REQ-MIG-056` to `[Implemented]` (`SecureZipExtractor.sanitizeDriveFileName` `\u0000`/`"."` hardening + `LegacyImportEngine.sanitizeRemoteTempFile` in `bulkRecoverFromDropbox` & `bulkRecoverFromGoogleDrive`). |
| `1.0.20` | 2026-11-21 | Lane 2 Author (`ATT-6174`, `--as agent1`) | `ATT-6174` | Added `[Planned]` `REQ-MIG-056` (`Derived (SW-only)`) specifying `SecureZipExtractor.sanitizeDriveFileName` enforcement in `LegacyImportEngine.bulkRecoverFromDropbox` and `\u0000`/`"."` `SecurityException` hardening. |
| `1.0.19` | 2026-11-21 | Lane 2 Author (`ATT-6170`, `--as agent1`) | `ATT-6170` | Promoted `REQ-DAT-046` to `[Verified]` in Rule 7.5 doc-only Stage 5 reusing Gate 4 (`ATT-9533`) independently verified `25/25` PASS across 4 suites (`0` fail, `0` skip) + `4/4` linters PASS @ `36609fff`. |
| `1.0.18` | 2026-11-21 | Lane 2 Author (`ATT-6170`, `--as agent1`) | `ATT-6170` | Promoted `REQ-DAT-046` to `[Implemented]` (`WorkoutRepository.loadWorkout` delegates `_allWorkouts` updates to `addOrUpdateWorkout`). |
| `1.0.17` | 2026-11-21 | Lane 2 Author (`ATT-6170`, `--as agent1`) | `ATT-6170` | Added `[Planned]` `REQ-DAT-046` (`Derived (SW-only)`) specifying lossless `addOrUpdateWorkout` merge in `WorkoutRepository.loadWorkout` preserving `exportStatuses` and transient cache fields while keeping `_initialWorkoutLoaded` and descending insertion intact. |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
| `1.0.1` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Linked refined `REQ-*` entries in-place to net-new `SYSREQ-TRK-007..013` and `SYSREQ-DEX-007..008`. |
| `1.0.2` | 2026-11-20 | Lane A Flash (`ATT-9150`, `--as agent1`) | `ATT-9150` | Added `REQ-MIG-054` (`[Planned]`, `SYSREQ-DEX-004`) preserving explicit `UPLOAD_TO_STRAVA = 0` opt-outs and `1` opt-ins while resolving undecided `-1`/SQL `NULL` rows. |
| `1.0.3` | 2026-11-20 | Lane A Flash (`ATT-9150`, `--as agent1`) | `ATT-9150` | Promoted `REQ-MIG-054` to `[Implemented]` (`WorkoutSummariesDatabaseManager.applyInferredIdentity()` tri-state SQL `NULL`/`-1` guard, `<= 3,148` LoC). |
| `1.0.4` | 2026-11-20 | Lane A Flash (`ATT-9150`, `--as agent1`) | `ATT-9150` | Promoted `REQ-MIG-054` to `[Verified]` reusing Gate 4 verified SHA `aa9a407c` (`46/46` PASS across 7 suites, `Rule 7.5`). |
| `1.0.5` | 2026-11-21 | Lane B Flash (`ATT-6045`, `--as agent1`) | `ATT-6045` | Added `[Planned]` `REQ-MIG-055` (`SYSREQ-DEX-004`) for thread-/DST-safe `java.time` parsing in GPX/TCX parsers, dead `LegacyImportEngine.tcxTimeFormat` removal, and `BaseExporter` `< 0` index-0 fix (`ExportStatusDatabaseManager.getDatabase()` preserved). |
| `1.0.6` | 2026-11-21 | Lane B Flash (`ATT-6045`, `--as agent1`) | `ATT-6045` | Promoted `REQ-MIG-055` to `[Implemented]` following Stage 4 implementation (`WorkoutTimestampParser.kt`, `GpxWorkoutParser.kt`, `TcxWorkoutParser.kt`, `LegacyImportEngine.kt`, `BaseExporter.java`). |
| `1.0.7` | 2026-11-21 | Lane B Flash (`ATT-6045`, `--as agent1`) | `ATT-6045` | Promoted `REQ-MIG-055` to `[Verified]` in Rule 7.5 doc-only Stage 5 reusing Gate 4 (`ATT-9420`) `89/89` PASS across 20 suites + `4/4` linters PASS @ `529a9ee8`, `--check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-6045_walkthrough.md`. |
| `1.0.8` | 2026-11-21 | Lane C Author (`ATT-6038`, `--as agent1`) | `ATT-6038` | Added `[Planned]` `REQ-DAT-045` for `BatchMetadataLoader` (`repositories/`), `<= 500`-chunked `getExportRowsForWorkouts` & `StravaUploadDbHelper`, dead `getExportStatusMap` removal, and lossless `fromCursor` -> `fromSnapshot` delegation. |
| `1.0.9` | 2026-11-21 | Lane C Author (`ATT-6038`, `--as agent1`) | `ATT-6038` | Promoted `REQ-DAT-045` to `[Implemented]` (`BatchMetadataLoader.kt`, `ExportStatusDatabaseManager.java`, `ExportStatusRepository.kt`, `ExportStatusDataProvider.kt`, `StravaUploadDbHelper.java`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`, `PeriodsRepository.kt`, `WorkoutClusterRepository.kt`). |
| `1.0.10` | 2026-11-21 | Lane C Author (`ATT-6038`, `--as agent1`) | `ATT-6038` | Promoted `REQ-DAT-045` to `[Verified]` in Rule 7.5 doc-only Stage 5 reusing Gate 4 (`ATT-9452`) `99/99` PASS across 18 suites @ `df4b48ee`. |
| `1.0.11` | 2026-11-21 | Lane C Author (`ATT-6038`, `--as agent1`) | `ATT-6038` | Stage 6 Bounce 1 remediation: aligned `REQ-DAT-045.3`/`.4` with `PeriodsRepository.performHierarchicalMigration` & `BatchMetadataLoader.loadForSingleWorkout`, set `[Implemented]` for Gate 4 Bounce 1 re-verification. |
| `1.0.12` | 2026-11-21 | Lane C Author (`ATT-6038`, `--as agent1`) | `ATT-6038` | Promoted `REQ-DAT-045` to `[Verified]` in Rule 7.5 doc-only Stage 5 Bounce 1 reusing Gate 4 (`ATT-9463`) `33/33` PASS (`0` warnings) @ `34ef13d5` + `99/99` PASS @ `df4b48ee`. |
| `1.0.13` | 2026-11-21 | Lane B Author (`ATT-9348`, `--as agent1`) | `ATT-9348` | Added & verified `REQ-EXP-045` (`SYSREQ-DEX-001`, `SYSREQ-DEX-004`, `SYSREQ-TRK-010`) for opt-in bounded crash-safe smartphone raw sensor sidecar (`SmartphoneRawTelemetryRecorder`, `SmartphoneRawSensorCapture`, `PhoneRawSidecarRecovery`, `PhoneRawTelemetryReader`, `RawSensorSidecarFiles`), unified `CLOCK_BOOTTIME`, multi-file `RAW_SENSOR` export, pruner/privacy/backup exclusions, and 9-locale dual-host tuning UI (`141/141` PASS @ `94e7f204`). |
| `1.0.14` | 2026-11-21 | Lane B Author (`ATT-9349`, `--as agent1`) | `ATT-9349` | Added `[Planned]` `REQ-EXP-046` (`SYSREQ-TRK-010`, `SYSREQ-DEX-001`) for pure-JVM `PolarRawMetadata`/`PolarRawTelemetryReader`, deterministic multi-sidecar offline replay harness, scorecard/diff/CSV/ground-truth verifier, and lazy Gradle `JavaExec` CLI task `:app:replayRawTelemetry`. |
| `1.0.15` | 2026-11-21 | Lane B Author (`ATT-9349`, `--as agent1`) | `ATT-9349` | Promoted `REQ-EXP-046` to `[Implemented]` following Stage 4 implementation (`PolarRawMetadata.kt`, `PolarRawTelemetryReader.kt`, `app/src/test/.../replay/` harness + CLI, lazy `:app:replayRawTelemetry` task in `app/build.gradle`, `55/55` PASS + live CLI smoke PASS/FAIL). |
| `1.0.16` | 2026-11-21 | Lane B Author (`ATT-9349`, `--as agent1`) | `ATT-9349` | Promoted `REQ-EXP-046` to `[Verified]` in Rule 7.5 doc-only Stage 5 reusing Gate 4 (`ATT-9476`) independently verified `56/56` PASS across 9 suites (`0` warnings) @ `d889e9ca` + live `:app:replayRawTelemetry` PASS (`9s`) / FAIL (`4s`). |
