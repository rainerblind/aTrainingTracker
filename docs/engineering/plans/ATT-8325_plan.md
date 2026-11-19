# Stage 3 Implementation Plan: `ATT-8325`

**Ticket**: [ATT-8325](https://rainerblind.atlassian.net/browse/ATT-8325) — `[Verbesserung] Add executable/attested verification for Data Migration & Export (25 TSTs from ATT-8145)`  
**Target Release**: `V5.0.0` | **Risk Rating**: `LOW` | **Scope**: Pure Test Construction (`0` `app/src/main/` Edits, `0` `src/main/` Test Reads)

---

## 1. Architecture, Gate 2 Guards & `UI Consistency`
- **Production Architecture (`SWE.2` / `SWE.3`)**: Zero changes to `app/src/main/` Kotlin/Java, SQLite schemas, `docs/architecture.md`, or `docs/system/*`.
- **`UI Consistency`**: `N/A` (no production UI changes). Robolectric Compose tests configure `@Config(sdk = [34], application = Application::class)` and render `ImportBackupTabsScreen`, `BackupTabContentLayout`, `RestoreTabContent`, and `PreImportTuningBottomSheet` directly under `createComposeRule()`.
- **Mandatory Invariants & Spec Alignments**:
  - `TST-MIG-008`: Verify 4-tab `HorizontalPager` (`Import`, `Auto-Import`, `Backup`, `Restore`; `ImportBackupTabsScreen.kt:117-124`), never `pageCount == 3`.
  - `TST-MIG-013` / `TST-MIG-017`: Verify `Semaphore(3)` backpressure (`BackupRestoreViewModel.kt:95`, `LegacyImportEngine.kt:207`).
  - `TST-DAT-021`: Strictly behavioral `SQLiteDatabase`/`Cursor` assertions (`ORDER BY _id DESC LIMIT 1`, column projection, empty-cursor resilience); zero `src/main/` file reads (`SourceTextReadingAuditContractTest`).
- **Stage 5 Promotion Manifest (Post-PASS Only)**:
  - **Promote `[Implemented]` -> `[Verified]` (`25` TSTs)**: `TST-EXP-004`, `TST-MIG-001..008`, `TST-MIG-010..019`, `TST-MIG-025`, `TST-DAT-001`, `TST-EXT-001..002`, `TST-DAT-021`, `TST-DAT-032`.
  - **Promote `[Implemented]` -> `[Verified]` (`24` REQs)**: `REQ-MIG-001..003`, `REQ-MIG-005..011`, `REQ-MIG-013`, `REQ-MIG-015`, `REQ-MIG-016` (paired `TST-MIG-012`+`018`), `REQ-MIG-017` (paired `TST-MIG-013`+`017`), `REQ-MIG-018..022`, `REQ-MIG-028`, `REQ-EXT-004..005`, `REQ-DAT-026`, `REQ-DAT-036` (`REQ-MIG-004` & `REQ-DAT-007` remain `[Verified]`).
  - **HOLD `[Implemented]` (Multi-TST Guard — `2` REQs)**: `REQ-EXP-001` (blocked by `TST-REG-003`) and `REQ-MIG-014` (blocked by `TST-PRO-004`).

---

## 2. Step-by-Step Implementation & Traceability Matrix

| Step | Target File(s) (`app/src/test/java/com/atrainingtracker/`) | Exact Scope & Implementation Details | Covered `TST-*` / `REQ-*` |
| :--- | :--- | :--- | :--- |
| **1** | `trainingtracker/database/KnownLocationsDatabaseManagerTest.kt`<br>`trainingtracker/tracker/TrackerServiceResumeTest.kt`<br>`trainingtracker/database/*RealSqliteMigrationTest.kt`<br>`banalservice/database/SportTypeRealSqliteMigrationTest.kt`<br>`trainingtracker/database/DatabaseMigrationIndexTest.kt`<br>`trainingtracker/database/SqliteSchemaParityContractTest.kt` *(new, Robolectric)* | Add canonical `TST-DAT-001` KDoc tag (`KnownLocationsDatabaseManagerTest`); add `TST-DAT-021` KDoc tag + verify bounded `ORDER BY _id DESC LIMIT 1` & projection behavioral check without `src/main` reads (`TrackerServiceResumeTest`); add `TST-DAT-032` KDoc tags to existing SQLite migration tests + add `SqliteSchemaParityContractTest` comparing `PRAGMA table_info` / `index_list` between fresh `onCreate()` and incremental `onUpgrade()` for `WorkoutSummariesDbHelper`, `RoutesDbHelper`, `LapsDbHelper`, `EquipmentDbHelper`, `SportTypeDbHelper`, `TrackingViewsDbHelper`, and `WorkoutClusterDbHelper`. | `TST-DAT-001` (`REQ-DAT-007`)<br>`TST-DAT-021` (`REQ-DAT-026`)<br>`TST-DAT-032` (`REQ-DAT-036`) |
| **2** | `trainingtracker/exporter/writer/TCXFileWriterSchemaComplianceTest.kt` *(new)* | Export BIKE (`CADENCE` + `POWER`) and RUN (`CADENCE`) workouts via `TCXFileWriter` using temp dir & Robolectric/MockK DBs; parse XML via `DocumentBuilderFactory` and assert all `<Cadence>`, `<Watts>`, and `<RunCadence>` nodes are strictly integer strings (regex `^\d+$`, zero decimal points). | `TST-EXP-004`<br>(`REQ-EXP-001`) |
| **3** | `trainingtracker/migration/BackupMigrationCycleTest.kt` *(new, Robolectric)*<br>`trainingtracker/migration/LegacyImportEngineDeduplicationTest.kt`<br>`trainingtracker/migration/BackupWorkerTest.kt`<br>`trainingtracker/migration/BackupWorkerDropboxGatingTest.kt`<br>`trainingtracker/migration/DropboxBackupDefaultsTest.kt` *(new, Robolectric)* | Verify `BackupManager.createBackup()` `.attbackup` bundling + `MigrationEngine` Full Restore + tracking-state lockdown (`TST-MIG-001`); add `ImportEngine` merge deduplication + `TST-MIG-002` KDoc tag (`LegacyImportEngineDeduplicationTest` + `BackupMigrationCycleTest`); add `WorkManager` Robolectric `BackupWorker.schedule()` enqueue test + `TST-MIG-003` KDoc tag (`BackupWorkerTest`, `BackupWorkerDropboxGatingTest`); verify `prefs_dropbox.xml` / `BackupRestoreViewModel` / `BackupWorker` defaults `automated_backups == true` & `backup_interval_days == "1"` (`DropboxBackupDefaultsTest`, `TST-MIG-006`, `TST-MIG-007`). | `TST-MIG-001` (`REQ-MIG-001..004,006`)<br>`TST-MIG-002` (`REQ-MIG-005`)<br>`TST-MIG-003` (`REQ-MIG-003`)<br>`TST-MIG-006` (`REQ-MIG-009`)<br>`TST-MIG-007` (`REQ-MIG-010`) |
| **4** | `trainingtracker/migration/ImportBackupTabsUiContractTest.kt` *(new, Robolectric Compose)* | `@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [34], application = Application::class)` + `createComposeRule()`: (a) tap `restore_from_dropbox` on Restore tab with Dropbox connected and assert `restore_warning_title` confirmation `AlertDialog` blocks `restoreFromDropbox` until confirmed (`TST-MIG-004`); (b) verify `backup_now`, provider-aware cloud backup button, and descriptive Restore/Import descriptions (`TST-MIG-005`); (c) verify 4-tab `HorizontalPager` (`Import`, `Auto-Import`, `Backup`, `Restore`) and tab switching (`TST-MIG-008`); (d) assert Import tab UI & `LocalizationTestCache` import labels avoid negative `"Legacy"`/`"Recovery"` wording (`TST-MIG-016`). | `TST-MIG-004` (`REQ-MIG-007`)<br>`TST-MIG-005` (`REQ-MIG-008`)<br>`TST-MIG-008` (`REQ-MIG-011`)<br>`TST-MIG-016` (`REQ-MIG-020`) |
| **5** | `trainingtracker/migration/LegacyImportEngineCloudRecoveryTest.kt` *(new)*<br>`trainingtracker/migration/BackupRestoreInteractionQueueTest.kt` *(new)*<br>`trainingtracker/migration/LegacyImportEngineSparseDataTest.kt` *(new, Robolectric SQLite)*<br>`trainingtracker/migration/LegacyImportEngineWorkoutNameTest.kt`<br>`trainingtracker/migration/TcxImportStravaToggleTest.kt` *(new, Robolectric Compose)* | `LegacyImportEngineCloudRecoveryTest`: test `importFromTcxResult()` `DUPLICATE_SKIPPED` with `0` cluster prompts (`TST-MIG-010`), `bulkRecoverFromDropbox()` pre-download skip via `ExistingWorkoutsSnapshot` (`TST-MIG-011`), `.withRecursive(true)` + `listFolderContinue()` multi-page scan (`TST-MIG-012`), `onStatus` scanning/downloading Dropbox feedback (`TST-MIG-015`), and nested path scan + `BackupRestoreViewModel` summary counts (`TST-MIG-018`). `BackupRestoreInteractionQueueTest`: test `Semaphore(3)` `_interactionQueue.size <= 3` backpressure + resume (`TST-MIG-013`) and concurrent background worker progress while item 1 awaits user decision (`TST-MIG-017`). `LegacyImportEngineSparseDataTest`: Robolectric SQLite import of sparse TCX (GPS/time only) asserting missing HR/Power columns/values are not zero-filled (`TST-MIG-014`). `LegacyImportEngineWorkoutNameTest`: add `2026-08-01_10-00-00.tcx` `FILE_BASE_NAME`/`WORKOUT_NAME` integrity test + `TST-MIG-019` tag. `TcxImportStravaToggleTest`: test `importFromTcx(uploadToStrava=false/true)`, `bulkRecoverFromDropbox(uploadToStrava=false)`, `PreImportTuningBottomSheet` Strava toggle visibility/state, and 9-locale `import_upload_to_strava_*` parity (`TST-MIG-025`). | `TST-MIG-010` (`REQ-MIG-013`)<br>`TST-MIG-011` (`REQ-MIG-015`)<br>`TST-MIG-012` (`REQ-MIG-016`)<br>`TST-MIG-013` (`REQ-MIG-017`)<br>`TST-MIG-014` (`REQ-MIG-018`)<br>`TST-MIG-015` (`REQ-MIG-019`)<br>`TST-MIG-017` (`REQ-MIG-014,017`)<br>`TST-MIG-018` (`REQ-MIG-016,021`)<br>`TST-MIG-019` (`REQ-MIG-022`)<br>`TST-MIG-025` (`REQ-MIG-028`) |
| **6** | `trainingtracker/onlinecommunities/strava/StravaAuthCustomTabsFlowTest.kt` *(new, Robolectric)* | Verify `StravaHelper.requestAccessToken(context)` launches `ACTION_VIEW` `CustomTabsIntent` (`EXTRA_SESSION` present, Strava authorize URI + saved CSRF `state`, `TST-EXT-001`); verify `StravaAuthViewModel` + `StravaAuthRepository` + `StravaOAuthCallbackActivity` `Loading` -> `Success`/`Error` reactive state transitions (`TST-EXT-002`). | `TST-EXT-001` (`REQ-EXT-004`)<br>`TST-EXT-002` (`REQ-EXT-005`) |

---

## 3. System Invariants & Stage 4 Verification Plan (`Rule 7.5`)
- **Invariants**: `0` `app/src/main/` edits; `0` `src/main/` file reads inside tests; deterministic `@After` teardown (`unmockkAll()`, `Dispatchers.resetMain()`, DB deletion & singleton resets); `tools/` dirty files & `ANT-Android-SDKs` untouched.
- **Stage 4 & Gate 4 Incremental Verification Command (`--rerun`, under `python3 tools/parallel_sprint_lock.py`)**:
  ```bash
  python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManagerTest" \
    --tests "com.atrainingtracker.trainingtracker.database.SqliteSchemaParityContractTest" \
    --tests "com.atrainingtracker.trainingtracker.database.DatabaseMigrationIndexTest" \
    --tests "com.atrainingtracker.trainingtracker.database.AthleteMetricsRealSqliteMigrationTest" \
    --tests "com.atrainingtracker.trainingtracker.database.EquipmentRealSqliteMigrationTest" \
    --tests "com.atrainingtracker.trainingtracker.database.RoutesRealSqliteMigrationTest" \
    --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsRealSqliteMigrationTest" \
    --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterRealSqliteMigrationTest" \
    --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummariesRealSqliteMigrationTest" \
    --tests "com.atrainingtracker.banalservice.database.SportTypeRealSqliteMigrationTest" \
    --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResumeTest" \
    --tests "com.atrainingtracker.trainingtracker.exporter.writer.TCXFileWriterSchemaComplianceTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.BackupMigrationCycleTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.LegacyImportEngineDeduplicationTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.BackupWorkerTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.BackupWorkerDropboxGatingTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.DropboxBackupDefaultsTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.ImportBackupTabsUiContractTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.LegacyImportEngineCloudRecoveryTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.BackupRestoreInteractionQueueTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.LegacyImportEngineSparseDataTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.LegacyImportEngineWorkoutNameTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.TcxImportStravaToggleTest" \
    --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaAuthCustomTabsFlowTest" \
    --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaOAuthCallbackActivityTest" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
    --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"
  ```
- Followed in Stage 5 by `python3 tools/verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-19_02_Spät --check-verified-evidence`.
