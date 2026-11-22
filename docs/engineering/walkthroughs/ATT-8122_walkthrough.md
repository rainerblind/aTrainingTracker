# Stage 5 Walkthrough: ATT-8122 — Authoritative `DatabaseSchemaRegistry` & Non-Eager `DatabaseConnectionManager` Quiescence

- **Ticket**: `ATT-8122` (`tier-a`, `2 SP`, `severity-major`, Target Release `V5.0.0`)
- **Branch**: `feature/ATT-8122` (Gate 3 `ATT-9598` **PASSED**)
- **Requirements / Tests**: `REQ-MIG-057` (`SYSREQ-DEX-005`) / `TST-MIG-057` (`[Verified]`)
- **Localization Parity**: `N/A - 0 string changes`

## Summary of Changes
1. **Weak-Referenced Open-DB Tracking (`REQ-MIG-057.3`)**:
   - Added Kotlin-first `app/src/main/java/com/atrainingtracker/database/TrackingSQLiteOpenHelper.kt` (`OpenDatabaseTracker` + `TrackingSQLiteOpenHelper` with nullable 4-arg Java-interop constructor, defensive `isDatabaseOpenSafely`, `cachedDb = null` cleanup in `close()`/`getOpenDatabaseOrNull()`, re-registration on open, and `@VisibleForTesting internal fun clearForTesting()`).
   - Wired `BaseDatabaseManager.kt` (`init`, `database`, `setDatabaseForTesting`, `getOpenDatabaseOrNull()`) and all 13 direct-`SQLiteOpenHelper` subclasses (`ExportStatusDbHelper`, `StravaUploadDbHelper`, `EquipmentDbHelper`, `ActiveDevicesDbHelper`, `WorkoutClusterDbHelper`, `LinkDbHelper`, `RoutesDbHelper`, `AthleteMetricsDbHelper`, `ClimbsDbHelper`, `ElevationCacheDbHelper`, `HrvReadinessDbHelper`, `WorkoutRrDbHelper`, `PeriodSummariesDbHelper` with `@JvmOverloads`) with `0` lines added to capped `WorkoutSamplesDatabaseManager.java` (`1,498/1,500` LoC).
2. **Constant-Backed `DatabaseSchemaRegistry` (`REQ-MIG-057.1`)**:
   - Replaced every hardcoded schema version literal and typo in `DatabaseSchemaRegistry.kt` with direct references to all 22 `SQLiteOpenHelper` constants (`Laps.db` v3, `Routes.db` v11, `ExportStatus.db` v2, `StartLocation2Altitude.db` v6, `PebbleView.db` v3, `RouteClusters.db` v11, `SportTypeLinks.db` v1, preserving explicit legacy alias `"TrackingViews.db" to TrackingViewsDbHelper.DB_VERSION`).
3. **Non-Eager `DatabaseConnectionManager` & Rollback Snapshot Guard (`REQ-MIG-057.3`, `REQ-MIG-057.4`)**:
   - Replaced the hardcoded eager-opening list in `DatabaseConnectionManager.getActiveDatabases()` with `OpenDatabaseTracker.getOpenDatabases()`, covering all 22 DBs (including previously omitted `PeriodSummaries.db`, `ElevationCache.db`, `AthleteMetrics.db`, `PebbleView.db`) without eagerly opening or creating closed `.db` files on disk.
   - Hardened `MigrationEngine.performFullRestore()` rollback validation so an empty existing `databases/` directory (uncovered once `cleanupDatabases()` cleanly removes all 22 accurately named DBs without eager reopening) does not throw a spurious `IOException`.

## Verification Evidence
- **Targeted Unit & Contract Tests (`./gradlew :app:testDebugUnitTest --rerun`, `94/94` PASS across 19 suites, `0` fail, `0` err, `0` skip, `0` new compiler warnings)**:
  - `DatabaseSchemaRegistryParityContractTest` (`2/2` PASS): `testAllProductionSqliteOpenHelpers_registeredWithExactVersionInDatabaseSchemaRegistry_TST_MIG_057_1` (discovers & validates all 22 concrete `SQLiteOpenHelper` subclasses), `testDatabaseConnectionManager_tracksOnlyOpenedDatabasesWithoutEagerOpening_TST_MIG_057_2` (non-eager tracking, quiescence, and `ATT-289` reopen).
  - `BackupMigrationCycleTest` (`3/3` PASS): `testFullBackupWipeAndRestoreCycle_bundlesAndRestoresState_TST_MIG_001` (removed 3-DB workaround filter; unmodified `.attbackup` full restore + `ATT-289` reopen without singleton reset).
  - `DatabaseConnectionManagerTest` (`5/5`), `ClimbsDatabaseRegistryTest` (`3/3`), `DatabaseSchemaRegistryHrvTest` (`3/3`), `BaseDatabaseManagerTest` (`5/5`), `BackupManagerRealSqliteTest` (`2/2`), `MigrationEngineClimbsRestoreTest` (`2/2`), `FullRestoreValidationSecurityTest` (`8/8`), `MigrationEngineCredentialSanitizationTest` (`2/2`), `BackupImportSecurityTest` (`14/14`), `PeriodSummariesDbTest` (`7/7`), `TrackingViewsDbMigrationV19Test` (`1/1`), `TrackingViewsDbMigrationV22Test` (`3/3`), `AthleteMetricsRealSqliteMigrationTest` (`3/3`), `ElevationCacheDatabaseManagerTest` (`14/14`), `WorkoutClusterDatabaseConnectionTest` (`1/1`), `ArchitectureConformanceContractTest` (`11/11`), `JavaCoreMigrationStrategyContractTest` (`5/5`).
- **Live Emulator Smoke (`emulator-5554`, `Rule 8.5`)**: Installed `app-debug.apk`, launched `MainActivityWithNavigation`, verified `CRASH_LOGCAT_CLEAN`, visually inspected settled UI screenshot (`../test_evidence/tickets/ATT-8122/screenshots/att8122_emulator_smoke.png`).
- **Evidence Bundle**: [ATT-8122 Evidence Manifest](../test_evidence/tickets/ATT-8122/manifest.json) | [Emulator Smoke Screenshot](../test_evidence/tickets/ATT-8122/screenshots/att8122_emulator_smoke.png).
