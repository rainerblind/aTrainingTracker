# Stage 2: Requirement & Test Specification - ATT-8122: Authoritative Schema Registry & Non-Eager Database Connection Quiescence

**Ticket**: [ATT-8122](https://issue-tracker/ATT-8122) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) | **Tier**: `tier-a` (`2 SP`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-22_01_Früh` (ID `471`) | **Branch**: `feature/ATT-8122`  
**Requirement Mapping**: `REQ-MIG-057` (`REQ-MIG-057.1`–`REQ-MIG-057.4`, `SYSREQ-DEX-005`) | **Test Spec ID**: `TST-MIG-057` (`TST-MIG-057.1`–`TST-MIG-057.4`)  
**Author**: Lane 3 Author (`agent1`) | **Date**: 2026-11-22

---

## 1. Requirement Specification (`REQ-MIG-057`)

### 1.1 Attributes
- **System Requirement(s)**: `SYSREQ-DEX-005` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration | **Localization Parity**: `N/A - 0 string changes`

### 1.2 Normative Clauses
1. **Authoritative Single-Source-of-Truth `DatabaseSchemaRegistry` (`REQ-MIG-057.1`)**: `DatabaseSchemaRegistry.SUPPORTED_DB_VERSIONS` SHALL reference the authoritative `DB_NAME`/`DATABASE_NAME` and `DB_VERSION`/`DATABASE_VERSION` constants of all **22** production `SQLiteOpenHelper` subclasses directly (zero hardcoded schema version literals), fixing `Laps.db` (`v3`), `Routes.db` (`v11`), `ExportStatus.db` (`v2`), `StartLocation2Altitude.db` (replacing typo `"KnownLocations.db"`), `PebbleView.db` (replacing typo `"Pebble.db"`), and registering `"RouteClusters.db"` (`v11`) and `"SportTypeLinks.db"` (`v1`). The legacy alias `"TrackingViews.db" to TrackingViewsDbHelper.DB_VERSION` SHALL be retained alongside `TrackingViewsDbHelper.DB_NAME` (`"FlexibleGenericView.db"`) for backward compatibility.
2. **Automated Classpath Drift Prevention (`REQ-MIG-057.2`)**: An automated reflection/classpath contract test SHALL discover every concrete production subclass of `android.database.sqlite.SQLiteOpenHelper` from compiled debug classes and assert 1:1 presence and exact `DB_VERSION` equality in `DatabaseSchemaRegistry`, preventing any future schema bump or new database from drifting out of sync.
3. **Complete Non-Eager WAL Checkpointing & Quiescence (`REQ-MIG-057.3`)**: `DatabaseConnectionManager.getActiveDatabases(context)` and `quiesceAllDatabases(context, closeConnections)` SHALL cover all **22** production databases (adding `PeriodSummariesDatabaseManager`, `ElevationCacheDatabaseManager`, `AthleteMetricsDatabaseManager`, and `PebbleDatabaseManager`) via weak-referenced open-handle tracking (`OpenDatabaseTracker` across `BaseDatabaseManager` and `TrackingSQLiteOpenHelper`), returning **strictly** handles that are already open (`db.isOpen == true`) and NEVER eagerly instantiating singletons or opening/creating closed `.db` files on disk.
4. **Full Backup/Restore Round-Trip & Architecture Invariants (`REQ-MIG-057.4`)**: An unmodified `.attbackup` archive created by `BackupManager.createBackup(context)` containing live `Laps.db` (v3), `Routes.db` (v11), `ExportStatus.db` (v2), `StartLocation2Altitude.db` (v6), `RouteClusters.db` (v11), and `SportTypeLinks.db` (v1) SHALL pass `MigrationEngine.validateBackupArchive()` and `MigrationEngine.performFullRestore()` with zero database exclusions. All new database infrastructure SHALL be Kotlin-first (`ADR-001`), adding `0` lines to capped Java monoliths (`WorkoutSamplesDatabaseManager.java <= 1,500`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC1 (`TST-MIG-057.1`)**: *Given* all 22 concrete production `SQLiteOpenHelper` subclasses, *When* inspected via classpath discovery and checked against `DatabaseSchemaRegistry`, *Then* every helper's live database filename and `DB_VERSION` match `DatabaseSchemaRegistry.getMaxSupportedVersion(dbName)` exactly, `isVersionSupported(dbName, version)` is `true`, and `isVersionSupported(dbName, version + 1)` is `false`.
* **AC2 (`TST-MIG-057.2`)**: *Given* a fresh app context where only a subset of SQLite databases (e.g., `WorkoutSummaries.db`, `ElevationCache.db`, `PeriodSummaries.db`, `AthleteMetrics.db`, `ExportStatus.db`) have been opened, *When* `DatabaseConnectionManager.getActiveDatabases(context)` and `quiesceAllDatabases(context, closeConnections = true)` run, *Then* every opened database is checkpointed and closed while unopened databases (`PebbleView.db`, `Climbs.db`, `StravaUpload.db`, `Routes.db`, etc.) are neither returned nor created on disk, and reopened handles work cleanly post-quiescence.
* **AC3 (`TST-MIG-057.3`)**: *Given* a live backup archive created by `BackupManager.createBackup(context)` with populated `WorkoutSummaries.db`, `Laps.db` (v3), `Routes.db` (v11), `ExportStatus.db` (v2), `StartLocation2Altitude.db`, `RouteClusters.db`, and `SportTypeLinks.db`, *When* passed unmodified to `MigrationEngine.performFullRestore(context, backupFile)`, *Then* schema validation and full restore succeed without excluding any database.

---

## 2. Test Specification (`TST-MIG-057`)

### Test Case 1: Classpath-Discovered `SQLiteOpenHelper` $\leftrightarrow$ `DatabaseSchemaRegistry` Parity (`TST-MIG-057.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/DatabaseSchemaRegistryParityContractTest.kt` (new, `@RunWith(RobolectricTestRunner::class)`)
* **Procedure**: Scan compiled debug class directories for all concrete non-abstract subclasses of `android.database.sqlite.SQLiteOpenHelper` (asserting `>= 22` discovered); instantiate each helper against Robolectric context, read its real `databaseName` and live `readableDatabase.version`, assert `DatabaseSchemaRegistry.getMaxSupportedVersion(name) == version`, `isVersionSupported(name, version) == true`, `isVersionSupported(name, version + 1) == false`, and assert typos `"KnownLocations.db"` and `"Pebble.db"` are absent while `"TrackingViews.db"` alias equals `TrackingViewsDbHelper.DB_VERSION`.

### Test Case 2: Non-Eager `DatabaseConnectionManager` Coverage & Quiescence (`TST-MIG-057.2`)
* **Target Files**: `DatabaseSchemaRegistryParityContractTest.kt`, `DatabaseConnectionManagerTest.kt`, `ClimbsDatabaseRegistryTest.kt`, `DatabaseSchemaRegistryHrvTest.kt`
* **Procedure**: On a clean Robolectric context with zero open DBs, assert `getActiveDatabases(context)` is empty and creates zero `.db` files on disk. Open a subset including `PeriodSummaries.db`, `ElevationCache.db`, `AthleteMetrics.db`, `PebbleView.db`, `ExportStatus.db` (via direct `mDbHelper` method), and `WorkoutSummaries.db`; assert `getActiveDatabases(context)` returns strictly those opened DBs; invoke `quiesceAllDatabases(context, closeConnections = true)`, assert `getActiveDatabases(context)` becomes empty and unopened DB files still do not exist on disk; assert reopening works cleanly.

### Test Case 3: Unmodified Full `.attbackup` Backup-Wipe-Restore Cycle (`TST-MIG-057.3`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupMigrationCycleTest.kt`
* **Procedure**: Remove the legacy `excluded = setOf("databases/Laps.db", "databases/Routes.db", "databases/ExportStatus.db")` workaround; seed `WorkoutSummaries.db`, `Laps.db` (v3), `Routes.db` (v11), `ExportStatus.db` (v2), `StartLocation2Altitude.db`, `RouteClusters.db`, and `SportTypeLinks.db`; create backup via `BackupManager.createBackup(context)` and pass the unmodified archive directly to `MigrationEngine.performFullRestore(context, backupFile)`, asserting `true` and full data survival.

### Test Case 4: Architectural & Governance Contract Verification (`TST-MIG-057.4`)
* **Target Suites**: `BaseDatabaseManagerTest`, `BackupManagerRealSqliteTest`, `MigrationEngineClimbsRestoreTest`, `PeriodSummariesDbTest`, `TrackingViewsDbMigrationV19Test`, `TrackingViewsDbMigrationV22Test`, `AthleteMetricsRealSqliteMigrationTest`, `ArchitectureConformanceContractTest`, `JavaCoreMigrationStrategyContractTest`.

---

## 3. Traceability Matrix

| Requirement ID | Normative Scope | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-MIG-057.1` | Authoritative constant refs for all 22 DBs + legacy `TrackingViews.db` alias | `TST-MIG-057.1` | Robolectric Contract Test |
| `REQ-MIG-057.2` | Classpath discovery preventing future `SQLiteOpenHelper` / registry drift | `TST-MIG-057.1` | Reflection/Classpath Contract |
| `REQ-MIG-057.3` | Non-eager open-handle tracking across all 22 DB managers | `TST-MIG-057.2` | Unit + Robolectric Test |
| `REQ-MIG-057.4` | Unmodified `.attbackup` full restore + `ADR-001` / monolith LoC invariants | `TST-MIG-057.3`, `TST-MIG-057.4` | Integration + Contract Tests |
