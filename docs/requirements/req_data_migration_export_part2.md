# Living Requirements: Persistence, Migration & Cloud Export (Part 2 of 2)

**Document ID**: `DOC-REQ-MIG-P2`  
**Version**: 1.0.19  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `data_migration_export_part2`  
**Prefixes**: `DAT`, `EXP`, `EXT`, `MIG`  
**Scope**: Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export.  
**Total Requirements**: 8

---

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

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
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
