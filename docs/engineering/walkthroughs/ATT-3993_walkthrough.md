# Stage 5 Verification & Walkthrough: ATT-3993 / REQ-MIG-044 / TST-MIG-041

## Overview & Scope
- **Ticket ID**: `ATT-3993`
- **Summary**: `[Bug] [High] SportType database upgrade drops user-defined sport types`
- **Requirement / Test ID**: `REQ-MIG-044` / `TST-MIG-041`
- **Fix Version / Target Release**: `V4.9.40`
- **Epic**: `ATT-281` (`Data Integrity & Storage Architecture`)
- **Key Modules**:
  - `com.atrainingtracker.banalservice.database.SportTypeDatabaseManager`
  - `com.atrainingtracker.banalservice.database.SportTypeReconciliationHelper`
  - `com.atrainingtracker.banalservice.ui.sporttype.SportTypeViewModel`
  - `com.atrainingtracker.banalservice.ui.sporttype.SportTypesTabsScreen`
  - `com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry`
  - `com.atrainingtracker.banalservice.database.SportTypeDatabaseUpgradeTest`

---

## Implemented Architectural Improvements

### 1. In-Place Table Alteration & Schema Evolution Ordering (`REQ-MIG-044.1`)
- Replaced destructive table drop (`DROP TABLE IF EXISTS SportTypes`) with additive non-destructive SQLite schema migration:
  ```sql
  ALTER TABLE SportTypes ADD COLUMN is_placeholder INTEGER DEFAULT 0
  ```
- Guaranteed strict schema ordering: schema evolution (`ALTER TABLE` under `oldVersion < 3`) executes *first* inside the SQLite transaction before any data manipulation (`oldVersion < 2`).
- Hardened default "WALK" base sport type migration: guarded `oldVersion < 2` update by matching `((_id = 4 OR UIName = 'walk' COLLATE NOCASE) AND baseSportType = 'UNKNOWN')`, preventing accidental alteration of user-defined sport types.
- Strict Downgrade Safety: implemented `onDowngrade` throwing strict `SQLiteException` to protect database integrity against rollback anomalies.

### 2. Backward Compatibility & Active Tracking Filter (`REQ-MIG-044.3`, `REQ-MIG-044.8`)
- Active tracking sport pickers (`getSportTypesIdList` and `getSportTypesUiNameList`) filter out placeholder sports using `is_placeholder = 0`.
- System sport threshold defined at `SYSTEM_SPORT_ID_THRESHOLD = 5L`.
- Added public helper `isPlaceholder(long id)` with full JavaDoc documentation.
- When an existing sport type is modified (`updateSportType`), `is_placeholder = 0` is automatically maintained.

### 3. Background Orphan Data Healing & Reconciliation (`REQ-MIG-044.2`, `REQ-MIG-044.5`)
- Implemented `SportTypeReconciliationHelper.kt` to inspect child and link databases for orphaned sport references (`sportId > 5L`):
  - `WorkoutSummaries.db` (`WorkoutSummaries.sportId`)
  - `SportTypeLinks.db` (`SportTypeEquipmentLink.sport_type_id`)
  - `RouteClusters.db` (`RouteClusters.probable_sport_id`)
- Accesses databases exclusively via manager singletons (`WorkoutSummariesDatabaseManager`, `SportTypeEquipmentLinkManager`, `WorkoutClusterDatabaseManager`) or caller-injected connections, avoiding raw SQLite file handles and locks.
- Concurrency Protection: acquires the dual JVM `ReentrantLock` and OS-level `FileLock` (`BackupManager.acquireLock(context)`) and guarantees release in a top-level `finally` block.
- Resurrects missing sport types into placeholder rows with localized display name (`R.string.sport_type_restored_placeholder`, e.g. "Restored Sport %d") and `is_placeholder = 1`.
- SQLite `AUTOINCREMENT` sequence integrity: explicit primary key insertion automatically updates `sqlite_sequence` so subsequent user creations obtain collisions-free IDs.

### 4. Placeholder Claiming & Garbage-Collection Pruning (`REQ-MIG-044.4`)
- Implemented `claimPlaceholder` enforcing case-insensitive name uniqueness across active sport types before transitioning `is_placeholder = 0`.
- Implemented `pruneUnreferencedPlaceholders` protected by `BackupManager.acquireLock(context)` to purge placeholder rows (`is_placeholder = 1`) that are no longer referenced by any workout, equipment, or cluster.

### 5. UI Presentation & 9-Language Localization Parity (`REQ-MIG-044.7`)
- Displayed `sport_type_placeholder_badge` in `SportTypesTabsScreen.kt` using `MaterialTheme.typography.labelMedium` and `MaterialTheme.colorScheme.error`.
- Full localization parity across all 9 supported locales:
  - English (`values/strings.xml`)
  - German (`values-de/strings.xml`)
  - Spanish (`values-es/strings.xml`)
  - French (`values-fr/strings.xml`)
  - Italian (`values-it/strings.xml`)
  - Japanese (`values-ja/strings.xml`)
  - Dutch (`values-nl/strings.xml`)
  - Polish (`values-pl/strings.xml`)
  - Portuguese (`values-pt/strings.xml`)

---

## Verification & Test Results

### 1. Dedicated Targeted Unit Tests (`SportTypeDatabaseUpgradeTest.kt`)
The dedicated test suite covers all clauses of `TST-MIG-041`:
1. `testCleanV1ToV3UpgradePreservesCustomSports` (`TST-MIG-041.1`) — **PASS**
2. `testOrphanSportResurrectionFromSummaries` (`TST-MIG-041.2`) — **PASS**
3. `testEngineNativeSequenceManagementAndCollisionPrevention` (`TST-MIG-041.3`) — **PASS**
4. `testActivePickerFiltersPlaceholdersAndAllowsClaiming` (`TST-MIG-041.4`) — **PASS**
5. `testClaimPlaceholderValidatesNameUniqueness` (`TST-MIG-041.5`) — **PASS**
6. `testPruneUnreferencedPlaceholders` (`TST-MIG-041.6`) — **PASS**
7. `testDowngradeThrowsSQLiteException` (`TST-MIG-041.7`) — **PASS**
8. `testIsPlaceholderHelper` (`TST-MIG-041.8`) — **PASS**
9. `testReconcileOrphanSportTypesAcquiresAndReleasesBackupLock` (`TST-MIG-041.9`) — **PASS**
10. `testPruneUnreferencedPlaceholdersAcquiresAndReleasesBackupLock` (`TST-MIG-041.10`) — **PASS**

### 2. Clean-Room Regression Test Execution (`./gradlew testDebugUnitTest`)
- **Total Tests Executed**: 3,259
- **Failures**: 0
- **Errors**: 0
- **Result**: `BUILD SUCCESSFUL` (100% pass rate)

---

## Living Documentation
- `docs/requirements.md`: `REQ-MIG-044` updated to status `Verified`.
- `docs/tests.md`: `TST-MIG-041` updated to status `Verified`.
- Maintained strict documentation integrity and ASPICE traceability.
