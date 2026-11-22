# Stage 1 Analysis: ATT-8122 - Fix Stale Schema Registry Constants, Wrong DB Filenames & Eager-Open Checkpointing

**Ticket**: [ATT-8122](https://issue-tracker/ATT-8122) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) | **Tier**: `tier-a` (`2 SP`, `severity-major`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-22_01_Früh` (ID `471`) | **Branch**: `feature/ATT-8122` (`603458a8`)  
**Author**: Lane 3 Author (`agent1`) | **Date**: 2026-11-22

---

## 1. Problem Statement & Forensic Root Cause Analysis
* **Self-Rejecting `.attbackup` Restore (`DatabaseSchemaRegistry.kt:29-56`)**: Hardcoded version literals lag behind live `SQLiteOpenHelper` constants for **3** databases — `Laps.db` (`2` vs live `3`), `Routes.db` (`10` vs live `11`), and `ExportStatus.db` (`1` vs live `2`) — causing `MigrationEngine.validateBackupArchive()` to reject valid backups created by the current app (`BackupMigrationCycleTest.kt:141-142` even had to strip those 3 DBs to pass!).
* **Wrong Filenames & Missing Databases in `DatabaseSchemaRegistry.kt`**: `"KnownLocations.db"` (`:34`) is actually `"StartLocation2Altitude.db"`, `"Pebble.db"` (`:42`) is actually `"PebbleView.db"`, and both `"RouteClusters.db"` (`WorkoutClusterDbHelper`, v11) and `"SportTypeLinks.db"` (`SportTypeEquipmentLinkManager`, v1) are completely omitted. Seven other entries still hardcode filename/version literals instead of helper constants (`Rule 62`).
* **Incomplete & Eager-Opening WAL Quiescence (`DatabaseConnectionManager.kt:109-130`)**: `getActiveDatabases(context)` calls `.getInstance(context).database` / `.writableDatabase` on 18 managers — (a) **eagerly opening/creating** closed `.db` files on disk just to checkpoint and close them, and (b) **omitting 4 managers**: `PeriodSummariesDatabaseManager`, `ElevationCacheDatabaseManager`, `AthleteMetricsDatabaseManager`, and `PebbleDatabaseManager`.

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)
* **Additional Bugs Confirmed Beyond Ticket Prompt**:
  1. `Laps.db` (`2` vs `3`, confirmed in Jira comment) is a **3rd** self-rejecting stale version alongside `Routes.db` (`10` vs `11`) and `ExportStatus.db` (`1` vs `2`).
  2. `DatabaseConnectionManager` omits **4** managers — not just `PeriodSummariesDatabaseManager` & `ElevationCacheDatabaseManager`, but also **`AthleteMetricsDatabaseManager`** (`AthleteMetrics.db`) and **`PebbleDatabaseManager`** (`PebbleView.db`).
  3. **`ExportStatusDatabaseManager.java`** extends `BaseDatabaseManager` (`ATT-6036`), **but** still calls `mDbHelper.getWritableDatabase()` / `getReadableDatabase()` directly at 12 call sites (`:105..654`), bypassing `BaseDatabaseManager.mDatabase`. Making `ExportStatusDbHelper` extend a shared `TrackingSQLiteOpenHelper` guarantees tracking regardless of access path.
  4. **Monolith Ceiling Guard (`ArchitectureConformanceContractTest`)**: `WorkoutSamplesDatabaseManager.java` is `1,498/1,500` LoC. Using a weak-referenced `OpenDatabaseTracker` registered inside `BaseDatabaseManager.kt` (`init`) and `TrackingSQLiteOpenHelper.kt` tracks all 10 Java `BaseDatabaseManager` subclasses with **0** lines added to any capped Java manager!
  5. **Legacy Alias & Test Callers**: 4 existing `TrackingViews` migration tests assert `DatabaseSchemaRegistry.getMaxSupportedVersion("TrackingViews.db") == TrackingViewsDbHelper.DB_VERSION`; preserve `"TrackingViews.db"` as an explicit legacy alias alongside `TrackingViewsDbHelper.DB_NAME` (`"FlexibleGenericView.db"`). Two existing tests (`DatabaseSchemaRegistryHrvTest:58`, `ClimbsDatabaseRegistryTest:56`) call `getActiveDatabases(context)` without opening their DB first and must be updated to test both unopened exclusion and opened inclusion.

## 3. Scope Grounding & Requirement Archaeology (Chesterton's Fence)
* **In-Scope (`2 SP`, `tier-a`)**: (1) Reference authoritative `DB_NAME`/`DB_VERSION` constants for all 22 `SQLiteOpenHelper` subclasses in `DatabaseSchemaRegistry.kt` (promoting visibility/constants on 5 helpers); (2) Introduce Kotlin `OpenDatabaseTracker` + `TrackingSQLiteOpenHelper` (`com.atrainingtracker.database`) integrated with `BaseDatabaseManager.kt` and all 13 direct-`SQLiteOpenHelper` subclasses so `DatabaseConnectionManager.getActiveDatabases()` returns **strictly** already-open handles across all 22 DBs with zero eager-open; (3) Add classpath-reflection parity & non-eager-open contract tests (`DatabaseSchemaRegistryParityContractTest.kt`), remove the 3-DB filter workaround in `BackupMigrationCycleTest.kt`, and update `ClimbsDatabaseRegistryTest` / `DatabaseSchemaRegistryHrvTest`.
* **Out-of-Scope**: Removing `PeriodSummaries.db` (`ATT-8120`), SQLite schema migrations (`0` `DB_VERSION` bumps), UI/strings (`0` strings).
* **Chesterton's Fence Audit**: Refines `REQ-MIG-041.2`/`.3` (`ATT-3989`, `SYSREQ-DEX-005`) and `REQ-DAT-047.1` (`ATT-6036`) via net-new `REQ-MIG-057`; preserves `ATT-289` transparent reopen-after-quiescence, `REQ-STB-024.3` non-opening `PebbleDatabaseManager`, `BackupManager` unmanaged `.db` copy fallback, and `"TrackingViews.db"` legacy alias.

## Impact Analysis
* **Affected Requirements**: Net-new `REQ-MIG-057` (`docs/requirements/req_data_migration_export_part2.md`, `SYSREQ-DEX-005`). `SYS.2`/`SYS.3`: No external interface change (`SYSREQ-DEX-005` satisfied internally). Localization: `N/A - 0 string changes`.
* **Complete Verified Inventory of All 22 `SQLiteOpenHelper` Subclasses (`app/src/main/`)**:

| # | Manager / Helper | Live `DB_NAME` | Live `DB_VER` | Registry Action | Tracking Path |
| :-: | :--- | :--- | :-: | :--- | :--- |
| 1 | `WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper` | `WorkoutSummaries.db` | `31` | Use `DB_NAME` + `DB_VERSION` | `BaseDatabaseManager` |
| 2 | `WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper` | `WorkoutSamples.db` | `1` | Use `DB_NAME` + `DB_VERSION` | `BaseDatabaseManager` |
| 3 | `LapsDatabaseManager.LapsDbHelper` | `Laps.db` | `3` | Fix stale `2 -> DB_VERSION (3)` | `BaseDatabaseManager` |
| 4 | `RoutesDatabaseManager.RoutesDbHelper` | `Routes.db` | `11` | Fix stale `10 -> DB_VERSION (11)` | `TrackingSQLiteOpenHelper` |
| 5 | `KnownLocationsDatabaseManager.KnownLocationsDbHelper` | `StartLocation2Altitude.db` | `6` | Fix wrong `KnownLocations.db` -> `DB_NAME`/`DB_VERSION` | `BaseDatabaseManager` |
| 6 | `TrackingViewsDatabaseManager.TrackingViewsDbHelper` | `FlexibleGenericView.db` | `22` | Use `DB_NAME`/`DB_VERSION` + `"TrackingViews.db"` alias | `BaseDatabaseManager` |
| 7 | `DevicesDatabaseManager.DevicesDbHelper` | `Devices.db` | `14` | Use `DB_NAME` + `DB_VERSION` | `BaseDatabaseManager` |
| 8 | `SportTypeDatabaseManager.SportTypeDbHelper` | `SportType.db` | `3` | Use `DB_NAME` + `DB_VERSION` | `BaseDatabaseManager` |
| 9 | `SegmentsDatabaseManager.SegmentsDbHelper` | `Segments.db` | `9` | Use `DB_NAME` + `DB_VERSION` | `BaseDatabaseManager` |
| 10 | `StravaUploadDbHelper` | `StravaUpload.db` | `6` | Promote `public` `DB_NAME`/`DB_VERSION` | `TrackingSQLiteOpenHelper` |
| 11 | `EquipmentDbHelper` | `Equipment.db` | `5` | Promote `public` `DB_NAME` + `DB_VERSION` | `TrackingSQLiteOpenHelper` |
| 12 | `PebbleDatabaseManager.PebbleDbHelper` | `PebbleView.db` | `3` | Fix wrong `Pebble.db` -> `DB_NAME`/`DB_VERSION` | `BaseDatabaseManager` |
| 13 | `AthleteMetricsDbHelper` (`AthleteMetricsDatabaseManager.kt`) | `AthleteMetrics.db` | `2` | Use `DB_NAME` + `DB_VERSION` (add to ConnMgr!) | `TrackingSQLiteOpenHelper` |
| 14 | `ActiveDevicesDbHelper` | `ActiveDevices.db` | `1` | Use `DB_NAME` + `DB_VERSION` | `TrackingSQLiteOpenHelper` |
| 15 | `ExportStatusDatabaseManager.ExportStatusDbHelper` | `ExportStatus.db` | `2` | Promote `public` class; fix stale `1 -> DB_VERSION (2)` | `TrackingSQLiteOpenHelper` + `BaseDatabaseManager` |
| 16 | `ElevationCacheDbHelper` (`ElevationCacheDatabaseManager.kt`) | `ElevationCache.db` | `1` | Already constant-ref (add to ConnMgr!) | `TrackingSQLiteOpenHelper` |
| 17 | `PeriodSummariesDatabaseManager.PeriodSummariesDbHelper` | `PeriodSummaries.db` | `27` | Already constant-ref (add to ConnMgr!) | `TrackingSQLiteOpenHelper` |
| 18 | `HrvReadinessDbHelper` (`HrvReadinessDatabaseManager.kt`) | `HrvReadiness.db` | `3` | Already constant-ref | `TrackingSQLiteOpenHelper` |
| 19 | `WorkoutRrDbHelper` | `WorkoutRr.db` | `2` | Already constant-ref | `TrackingSQLiteOpenHelper` |
| 20 | `ClimbsDbHelper` (`ClimbsDatabaseManager.kt`) | `Climbs.db` | `1` | Already constant-ref | `TrackingSQLiteOpenHelper` |
| 21 | `WorkoutClusterDatabaseManager.WorkoutClusterDbHelper` | `RouteClusters.db` | `11` | Add `DB_NAME`/`DB_VERSION` constants & register! | `TrackingSQLiteOpenHelper` |
| 22 | `SportTypeEquipmentLinkManager.LinkDbHelper` | `SportTypeLinks.db` | `1` | Add `DB_NAME`/`DB_VERSION` constants & register! | `TrackingSQLiteOpenHelper` |

* **Verification Impact & Risk**: **MEDIUM-HIGH (`tier-a`)** — touches backup/restore validation & SQLite connection quiescence; verified by classpath-scanning reflection parity contract test, real-SQLite `BackupMigrationCycleTest` (unmodified archive restore incl. `Laps.db`/`Routes.db`/`ExportStatus.db`), non-eager-open quiescence tests, and `ArchitectureConformanceContractTest` + `JavaCoreMigrationStrategyContractTest`.
