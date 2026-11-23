# Stage 3 Implementation Plan — ATT-8131: Replace PeriodSummaries.db with Reactive In-Memory Projection

- **Ticket**: `ATT-8131` (`3 SP`, `Verbesserung`, `tier-a`, Epic `ATT-8120`) | **Target Release**: `V5.0.0` | **Risk Rating**: `HIGH`
- **Traceability**: `REQ-PER-056` (`REQ-PER-056.1`–`.4`) <-> `TST-PER-056` (`TST-PER-056.1`–`.4`) | `Derived (SW-only)`
- **Database / UI / Monolith Impact**: Deletes 1 SQLite DB (`PeriodSummaries.db`, `21 -> 20` helpers); ratchets `BASELINE_ALLOWED_UI_DB_CALLERS` (`22 -> 20`); net `~-1,400` LoC (`PeriodsRepository.kt` `1,132 -> ~250` LoC, `PeriodSummariesDatabaseManager.kt` `-519` LoC); `0` UI layout or `strings*.xml` edits (`Localization Parity: N/A`).

## 1. SWE.2 Architectural Decomposition & Threading Model

- **Stateless Reactive Projection (`PeriodsRepository.kt`, ~240–270 LoC)**:
  - Constructor: `@VisibleForTesting internal constructor(application: Application, private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default, private val workoutRepo: WorkoutRepository = WorkoutRepository.getInstance(application))` (companion `getInstance(context)` calls default `PeriodsRepository(app)`).
  - Scope: `internal val scope = CoroutineScope(defaultDispatcher + SupervisorJob())` (zero `Dispatchers.IO`, zero SQLite/mapper/mutex fields).
  - Pipeline (`groupedPeriods: StateFlow<List<List<PeriodSummary>>>`):
    ```kotlin
    workoutRepo.allWorkouts
        .map { list -> list.filter { it.finished && it.startTimeS > 0L } }
        .distinctUntilChangedBy { finished -> finished.map { it.toPeriodProjectionKey() } }
        .mapLatest { finished -> buildGroupedPeriods(finished) }
        .flowOn(defaultDispatcher)
        .stateIn(scope, SharingStarted.Eagerly, listOf(emptyList(), emptyList(), emptyList(), emptyList()))
    ```
    - `distinctUntilChangedBy` guarantees **0** recomputations on 1-Hz unfinished ticks (`REQ-STB-020.3`) and non-period edits (`REQ-PER-016.1`), while immediately projecting any crop, metric edit, sport change, delete, import, or finish transition (`REQ-PER-056.3`).
    - `mapLatest` coalesces rapid bursts cleanly without artificial `200ms` debounce latency; `flowOn(defaultDispatcher)` guarantees all aggregation/grouping/region-curation runs off the Main thread.
  - Pure aggregation helper `internal fun buildGroupedPeriods(workouts: List<WorkoutData>, zone: ZoneId = ZoneId.systemDefault()): List<List<PeriodSummary>>`:
    - Groups valid finished workouts once per level (`DAY`, `WEEK`, `MONTH`, `YEAR`) by `getPeriodSortKey(w.startTimeS, type, zone)`, computes exact DST-safe `[startS, endS]` + active-locale `periodLabel`/`periodDateRange`, builds base `PeriodSummary` (totals, `sportStats` + `DetailedStats` + `LongestWorkout`, spatial bounds, N/S/E/W & longest IDs), runs shared `internal fun enrich(summary, groupWorkouts)` (which also deduplicates the `YEAR` vs `WEEK`/`MONTH` `DailySportActivity` aggregation helper while preserving `REQ-PER-025`/`027`/`039`/`046` & `PeriodsRepositoryMemoryTest` compatibility), and sorts each level `sortedByDescending { it.sortKey }`.
  - `val migrationStatus: StateFlow<MigrationStatus?> = MutableStateFlow(null)` (constant `null`, preserves `PeriodsViewModel`/`PeriodsScreen` contract).
  - `fun getWorkoutsForRange(startS: Long, endS: Long): List<WorkoutData>`: filters `workoutRepo.allWorkouts.value` for `it.finished && it.startTimeS in startS..endS` sorted by `it.startTimeS` (zero SQLite cursor or `WorkoutDataMapper` duplication).
  - Obsolete disk cleanup utility in companion (`@JvmStatic fun cleanupObsoletePeriodDatabasesAsync(context: Context)` & `@VisibleForTesting internal fun cleanupObsoletePeriodDatabases(context: Context, force: Boolean = false): Int`):
    - Gated by fresh preference key `PREF_PERIOD_SUMMARIES_DB_DECOMMISSIONED_CLEANED = "period_summaries_db_decommissioned_cleaned"` (bypassed when `force = true` for post-restore calls).
    - Scans `File(context.applicationInfo.dataDir, "databases")` (fallback `context.getDatabasePath("PeriodSummaries.db").parentFile`) and deletes every file whose name starts with `"PeriodSummaries"` (covers `PeriodSummaries.db`, `-wal`, `-shm`, `-journal`, and `PeriodSummaries_v4..v25.db*`).
- **Backup/Restore & Startup Hygiene**:
  - `DatabaseSchemaRegistry.kt`: remove `PeriodSummariesDatabaseManager.DATABASE_NAME` entry (`21 -> 20` DBs). Because `isVersionSupported()` returns `true` for unregistered legacy DB names, restoring old `.attbackup` archives containing `PeriodSummaries.db` succeeds cleanly.
  - `MigrationEngine.kt`: after `performFullRestore` completes database directory swap (Step 5), call `PeriodsRepository.cleanupObsoletePeriodDatabases(context, force = true)` so restored archives never leave dead `PeriodSummaries*` files behind.
  - `TrainingApplication.java`: invoke `PeriodsRepository.cleanupObsoletePeriodDatabasesAsync(this)` on a background thread during `onCreate()`.

## 2. Atomic Implementation Steps

| Step | Target File(s) | Atomic Change | Req / Test |
|---|---|---|---|
| **1** | `ui/aftermath/periodlist/PeriodSummariesDatabaseManager.kt` (DELETE), `migration/DatabaseSchemaRegistry.kt` | Delete `PeriodSummariesDatabaseManager.kt` (`-519` LoC) and remove its entry from `DatabaseSchemaRegistry.SUPPORTED_DB_VERSIONS`. | `REQ-PER-056.1` / `TST-PER-056.1` |
| **2** | `ui/aftermath/periodlist/PeriodsRepository.kt` | Rewrite `PeriodsRepository` (~250 LoC) as pure reactive in-memory projection over `workoutRepo.allWorkouts` (`flowOn(defaultDispatcher)` + `distinctUntilChangedBy` + `mapLatest`), single-pass `buildGroupedPeriods` + `enrich`, in-memory `getWorkoutsForRange`, constant `null` `migrationStatus`, and `cleanupObsoletePeriodDatabases[Async]`. | `REQ-PER-056.1`–`.4` / `TST-PER-056.1`–`.4` |
| **3** | `ui/aftermath/WorkoutRepository.kt`, `migration/BulkImportBatchContext.kt`, `ui/aftermath/periodlist/PeriodsViewModel.kt`, `migration/LegacyImportEngine.kt`, `repositories/BatchMetadataLoader.kt`, `tracker/TrackerService.java`, `database/WorkoutSummariesDatabaseManager.java` | Remove obsolete imperative push/sync calls (`onWorkoutFinished`, `onWorkoutSportChanged`, `onWorkoutDeleted`, `resyncAllPeriods`, `syncPeriodsIfDiscrepancy`, `checkIntegrityAndSync`) and unused `PeriodsRepository` imports/comments; simplify `PeriodsViewModel.loadPeriods()` to `workoutRepo.loadAllWorkouts()`. Keep `WorkoutSummariesDatabaseManager.getFinishedWorkoutCount()` only if referenced elsewhere, else remove dead method. | `REQ-PER-056.1`, `.3` / `TST-PER-056.3` |
| **4** | `TrainingApplication.java`, `migration/MigrationEngine.kt` | Wire one-time background `PeriodsRepository.cleanupObsoletePeriodDatabasesAsync(this)` in `TrainingApplication.onCreate()` and forced post-restore `PeriodsRepository.cleanupObsoletePeriodDatabases(context, force = true)` in `MigrationEngine.performFullRestore()`. | `REQ-PER-056.4` / `TST-PER-056.4` |
| **5** | `docs/architecture.md` (§2, §4.9) | Update `PeriodsRepository` description and §4.9 entry (`Rule 8.7`, `Rule 8.8`) to document stateless reactive in-memory projection on `Dispatchers.Default`, decommissioning of `PeriodSummaries.db`, and preserved cache/vertex budgets. | `REQ-PER-056` |
| **6** | `PeriodReactiveProjectionTest.kt` (NEW), `PeriodSummariesDbTest.kt` (DELETE), `DatabaseSchemaRegistryParityContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `PeriodsRepositorySyncTest.kt`, `PeriodsRepositoryMemoryTest.kt`, `TcxImportPeriodAndClusterIntegrationTest.kt`, `AutoImportManagerTest.kt`, `BackupRestoreViewModelFitTest.kt`, `BackupRestoreViewModelFormatScopingTest.kt`, `LegacyImportEngine*Test.kt`, `TcxImportStravaToggleTest.kt`, `WorkoutRepositoryConcurrencyTest.kt`, `WorkoutRepositoryExtremaAndClusterTest.kt`, `WorkoutDeletionProgressTest.kt`, `MigrationEngineTest.kt` | Replace obsolete SQLite DB tests with comprehensive `PeriodReactiveProjectionTest` (`TST-PER-056.1`–`.4`, incl. 5,000-workout perf check, crop/edit instant refresh, 3,600-tick zero-recompute, legacy DB cleanup, `.attbackup` restore); remove deleted `PeriodSummariesDatabaseManager` & imperative sync mocks across affected tests; ratchet `DatabaseSchemaRegistryParityContractTest` (`21 -> 20`) and `ArchitectureConformanceContractTest` (`22 -> 20`). | `TST-PER-056.1`–`.4` |

## 3. UI Consistency, Invariants & Verification Command

- **UI Consistency (`docs/design_guidelines.md`)**: `N/A` — zero UI composable or `strings*.xml` edits; `PeriodsScreen` & `PeriodBarGraph` receive identical `PeriodSummary` contracts and will be smoke-verified live on `emulator-5554` with `Rule 8.5` PNG inspection.
- **Invariants**: `PeriodSummary` / `SportStats` / `DetailedStats` / `LongestWorkout` fields unchanged; `sortKey DESC` preserved; DST bounds (`REQ-PER-015`), non-period edit dedup (`REQ-PER-016.1`), zero-rebuild 1-Hz live ticks (`REQ-STB-020.3`), OOM caps (`REQ-PER-039`), primary region (`REQ-PER-046`), and `.attbackup` backward compatibility (`REQ-MIG-041`) strictly preserved; zero `app/src/` edits prior to Gate 3 PASS (`Rule 4.1`); `ANT-Android-SDKs` intact (`160000`).
- **Stage 4 Verification Command**:
  `python3 tools/parallel_sprint_lock.py -- ./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutDeletionProgressTest" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.tracker.LiveWorkoutCardStateStoreTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.ExceptionHandlingHygieneTest"`
