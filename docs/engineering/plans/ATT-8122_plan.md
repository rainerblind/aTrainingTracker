# Stage 3: Implementation Plan - ATT-8122: Authoritative Schema Registry & Non-Eager Database Connection Quiescence

**Ticket**: [ATT-8122](https://issue-tracker/ATT-8122) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) | **Tier**: `tier-a` (`2 SP`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-22_01_Früh` (ID `471`) | **Branch**: `feature/ATT-8122`  
**Requirement Mapping**: `REQ-MIG-057` (`REQ-MIG-057.1`–`REQ-MIG-057.4`, `SYSREQ-DEX-005`) | **Test Mapping**: `TST-MIG-057` (`TST-MIG-057.1`–`TST-MIG-057.4`)  
**Author**: Lane 3 Author (`agent1`) | **Date**: 2026-11-22

---

## 1. Architectural Design (SWE.2)
* **Unified Non-Eager Open-Handle Tracking (`com.atrainingtracker.database`, Kotlin-first per `ADR-001`)**:
  * Add `TrackingSQLiteOpenHelper.kt` in `com.atrainingtracker.database` containing:
    1. `object OpenDatabaseTracker`: thread-safe (`synchronized`) weak-referenced registry (`WeakHashMap<BaseDatabaseManager<*>, Boolean>` and `WeakHashMap<TrackingSQLiteOpenHelper, Boolean>`) exposing `register(manager)`, `register(helper)`, and `getOpenDatabases(): List<SQLiteDatabase>` (deduplicated via `LinkedHashSet`, returning **strictly** handles with `db.isOpen == true`).
    2. `abstract class TrackingSQLiteOpenHelper(context, name, factory, version) : SQLiteOpenHelper(...)`: registers `this` in `OpenDatabaseTracker` on construction, caches `@Volatile private var cachedDb: SQLiteDatabase? = null` in `getWritableDatabase()` / `getReadableDatabase()`, clears `cachedDb = null` in `close()`, and exposes `getOpenDatabaseOrNull(): SQLiteDatabase?`.
  * Update `BaseDatabaseManager.kt`: add `init { OpenDatabaseTracker.register(this) }` and `open fun getOpenDatabaseOrNull(): SQLiteDatabase? = mDatabase?.takeIf { it.isOpen }`. This automatically tracks all 10 Java `BaseDatabaseManager` subclasses with **0 lines added** to capped Java monoliths (`WorkoutSamplesDatabaseManager.java` stays `1,498/1,500` LoC!).
  * Switch all **13** `SQLiteOpenHelper` subclasses that open DBs directly (the 12 non-`BaseDatabaseManager` helpers + `ExportStatusDbHelper`, which is called directly via `mDbHelper.getWritableDatabase()` in `ExportStatusDatabaseManager`) from `SQLiteOpenHelper` to `TrackingSQLiteOpenHelper`.
* **Authoritative `DatabaseSchemaRegistry.kt`**: Replace all hardcoded filenames and version numbers across all **22** databases with direct `DB_NAME`/`DB_VERSION` constant references (plus documented legacy alias `"TrackingViews.db" to TrackingViewsDbHelper.DB_VERSION`), after exposing constants cleanly on `ExportStatusDbHelper` (`public static class`), `StravaUploadDbHelper` (`public static final`), `EquipmentDbHelper` (`public static final DB_NAME`), `WorkoutClusterDatabaseManager` (`DB_NAME = "RouteClusters.db"`, `DB_VERSION = 11`), and `SportTypeEquipmentLinkManager` (`DB_NAME = "SportTypeLinks.db"`, `DB_VERSION = 1`).
* **Simplified Non-Eager `DatabaseConnectionManager.kt`**: Replace the 18-entry eager-opening `getActiveDatabases(context)` list with `OpenDatabaseTracker.getOpenDatabases()` (preserving `@JvmStatic fun getActiveDatabases(context: Context): List<SQLiteDatabase>` signature, `databaseLock`, `checkpointAll`, `checkpointWithRetry`, and `quiesceAllDatabases`).
* **SWE.2 / Detailed Design Impact**: No new subsystem/service/prefix family (`Rule 8.8`); `0` `strings*.xml` edits; `0` SQLite schema version increments.

## 2. Non-Negotiable System Invariants
1. **Zero Eager DB Creation**: `getActiveDatabases(context)` / `quiesceAllDatabases(context)` must NEVER instantiate DB singletons or create/open unopened `.db` files on disk (`REQ-STB-024.3` Pebble safety preserved).
2. **Transparent Reopen Post-Quiescence (`ATT-289`)**: All managers (`BaseDatabaseManager`, `Routes`, `WorkoutCluster`, `PeriodSummaries`, and `TrackingSQLiteOpenHelper`-backed managers) must reopen cleanly after `quiesceAllDatabases(context, closeConnections = true)`.
3. **Backward Compatibility & Monolith Budgets**: Keep `"TrackingViews.db"` alias in `DatabaseSchemaRegistry`; add `0` lines to `WorkoutSamplesDatabaseManager.java` (`1,498/1,500`), `WorkoutSummariesDatabaseManager.java`, `TrackingViewsDatabaseManager.java`, `DevicesDatabaseManager.java`; preserve `ADR-001` Kotlin-first rule (`JavaCoreMigrationStrategyContractTest`).

## 3. Atomic Step-by-Step Construction Plan (Stage 4 — Strictly Post-Gate-3)
1. **Step 1 — Gate 3 Check (`Rule 4.1`)**: Execute `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and verify `GATE_PASSED` before editing any file under `app/src/`.
2. **Step 2 — `TrackingSQLiteOpenHelper.kt` & `BaseDatabaseManager.kt` (`REQ-MIG-057.3`)**: Create `app/src/main/java/com/atrainingtracker/database/TrackingSQLiteOpenHelper.kt` (`OpenDatabaseTracker` + `TrackingSQLiteOpenHelper`); add `OpenDatabaseTracker.register(this)` and `getOpenDatabaseOrNull()` in `BaseDatabaseManager.kt`.
3. **Step 3 — Expose Missing Constants & Wire `TrackingSQLiteOpenHelper` on 13 Helpers (`REQ-MIG-057.1`, `.3`)**:
   * Java (4 files): `ExportStatusDatabaseManager.java` (`public static class ExportStatusDbHelper extends TrackingSQLiteOpenHelper`), `StravaUploadDbHelper.java` (`public static final DB_NAME`/`DB_VERSION`, extends `TrackingSQLiteOpenHelper`), `EquipmentDbHelper.java` (`public static final DB_NAME`, extends `TrackingSQLiteOpenHelper`), `ActiveDevicesDbHelper.java` (extends `TrackingSQLiteOpenHelper`).
   * Kotlin (9 files): `WorkoutClusterDatabaseManager.kt` (`const val DB_NAME`/`DB_VERSION` + `TrackingSQLiteOpenHelper`), `SportTypeEquipmentLinkManager.kt` (`const val DB_NAME`/`DB_VERSION` + `TrackingSQLiteOpenHelper`), `RoutesDatabaseManager.kt`, `AthleteMetricsDatabaseManager.kt`, `ClimbsDatabaseManager.kt`, `ElevationCacheDatabaseManager.kt`, `HrvReadinessDatabaseManager.kt`, `WorkoutRrDbHelper.kt`, `PeriodSummariesDatabaseManager.kt` (each helper extends `TrackingSQLiteOpenHelper`).
4. **Step 4 — `DatabaseSchemaRegistry.kt` & `DatabaseConnectionManager.kt` (`REQ-MIG-057.1`, `.3`)**: Replace all hardcoded literals in `DatabaseSchemaRegistry.kt` with authoritative constant refs for all 22 DBs (+ `"TrackingViews.db"` alias); replace `DatabaseConnectionManager.getActiveDatabases(context)` body with `OpenDatabaseTracker.getOpenDatabases()`.
5. **Step 5 — Contract & Integration Tests (`TST-MIG-057.1`–`.3`)**:
   * Create `app/src/test/java/com/atrainingtracker/trainingtracker/migration/DatabaseSchemaRegistryParityContractTest.kt` (classpath scan of all concrete `SQLiteOpenHelper` subclasses $\leftrightarrow$ `DatabaseSchemaRegistry`, plus non-eager open & reopen-after-quiescence verification covering `PeriodSummaries`, `ElevationCache`, `AthleteMetrics`, `PebbleView`, `ExportStatus`, `RouteClusters`, `SportTypeLinks`).
   * Update `BackupMigrationCycleTest.kt` (remove 3-DB filter workaround; seed `Laps.db`, `Routes.db`, `ExportStatus.db`, `StartLocation2Altitude.db`, `RouteClusters.db`, `SportTypeLinks.db` and restore unmodified `.attbackup`), `ClimbsDatabaseRegistryTest.kt`, and `DatabaseSchemaRegistryHrvTest.kt` (verify both unopened exclusion and opened inclusion).
6. **Step 6 — Verification & Governance (`TST-MIG-057.4`)**: Run targeted Gradle suites, live `emulator-5554` smoke test, `verify_requirement_governance.py`, and `auto_gate.py --write-bundle`.

## 4. Targeted Verification & Rollback Plan
* **Verification Command (`--rerun`, never `--rerun-tasks`)**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistryParityContractTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.DatabaseConnectionManagerTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.BackupMigrationCycleTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.BackupManagerRealSqliteTest" \
    --tests "com.atrainingtracker.trainingtracker.migration.MigrationEngineClimbsRestoreTest" \
    --tests "com.atrainingtracker.database.BaseDatabaseManagerTest" \
    --tests "com.atrainingtracker.trainingtracker.database.ClimbsDatabaseRegistryTest" \
    --tests "com.atrainingtracker.trainingtracker.database.hrv.DatabaseSchemaRegistryHrvTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodSummariesDbTest" \
    --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDbMigrationV19Test" \
    --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDbMigrationV22Test" \
    --tests "com.atrainingtracker.trainingtracker.database.AthleteMetricsRealSqliteMigrationTest" \
    --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
    --tests "com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest"
  ```
* **Rollback**: Isolated on `feature/ATT-8122`; cleanly revertible prior to Stage 6 merge.
