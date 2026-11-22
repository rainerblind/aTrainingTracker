# Stage 3 Implementation Plan: ATT-8128

**Ticket**: `ATT-8128` (`tier-a`, 2 SP) | **Branch**: `feature/ATT-8128` | **Requirements**: `REQ-DAT-048` (`REQ-DAT-048.1`–`.5`) | **Tests**: `TST-DAT-044` (`TST-DAT-044.1`–`.5`)

## 1. Architecture & Invariants (SWE.2 / SWE.3 Guardrails)
- **Zero Schema Version Bumps**: `DB_VERSION` constants across `WorkoutSummaries.db`, `WorkoutSamples.db`, `Devices.db`, and `Routes.db` remain strictly unchanged (`DatabaseSchemaRegistryParityContractTest`).
- **Monolith LoC Ceilings (`ArchitectureConformanceContractTest`)**:
  - `WorkoutSamplesDatabaseManager.java`: **MUST remain `<= 1,500` LoC** (currently `1,498`; merging `START`/`END` into one `LIMIT 1` branch shrinks file by ~25 LoC to `~1,473`).
  - `WorkoutSummariesDatabaseManager.java <= 3,148` (currently `3,058`), `DevicesDatabaseManager.java <= 1,671` (currently `1,633`), `WorkoutRepository.kt <= 5,117` (currently `4,938`).
- **Cursor Safety (`REQ-DAT-043`)**: Every SQLite `Cursor` in Java DB managers MUST be owned by `try-with-resources` (`TryWithResourcesCursorAuditTest` green).
- **Chesterton's Fence Invariants**:
  1. `LastActiveDateFormatter` preserves `ThreadLocal` zero-churn caching (`REQ-STB-021.4`) while emitting canonical UTC ISO-8601 (`yyyy-MM-dd'T'HH:mm:ss'Z'`) and providing backward-compatible `parseToEpochMillis()` for both UTC ISO-8601 and legacy locale `DateFormat` strings.
  2. `WorkoutRepository.reloadWorkoutData(workoutId: Long)` keeps its exact public signature; cluster mutations delegate to `private suspend fun reloadWorkoutDataInternal(workoutId: Long, notifyInitialLoaded: Boolean)` so `EditWorkoutViewModel.kt:179` (`initialWorkoutLoaded`) keeps working on a single DB read.
  3. `ExtremaDataRow.sensorType: SensorType? = null` is a trailing default parameter preserving 100% source compatibility with existing callers/previews/tests.
- **No `.java` File Count Change**: All edits stay within existing `.java`/`.kt` files (`JavaCoreMigrationStrategyContractTest`).
- **Localization Parity**: `N/A - 0 string changes`.

## 2. Atomic Implementation Steps

| Step | Files & Surgical Changes | Trace | Step Verification |
| :--- | :--- | :--- | :--- |
| **1** | **`WorkoutSummariesDatabaseManager.java`**: Add `!cursor.isNull(idx)` guards to `getDouble(String, String)` (L741), `getInt(long, String)` (L783), `getInt(String, String)` (L803 + `if (baseFileName == null) return null;`), `getLong(long, String)` (L823), and `getExtremaValue(SQLiteDatabase, ...)` (L921, using `getColumnIndexOrThrow`). Add unit tests in `WorkoutSummariesDatabaseManagerTest.kt`. | `REQ-DAT-048.1` / `TST-DAT-044.1` | `--tests "*WorkoutSummariesDatabaseManager*Test"` |
| **2** | **`WorkoutSamplesDatabaseManager.java`**: Replace `START`/`END` full-table scans (L151–192) with a shared single-column `db.query(table, new String[]{col}, col + " IS NOT NULL", null, null, null, orderBy, "1")` block; fix L115 bitwise `&` to `&& time > 0`; in `pruneAndShiftSamples` (L471–480), fetch `Set<String> cols = SqliteSchemaUtils.getTableColumnNames(db, table)` once and check `cols.contains(...)`. Verify LoC `<= 1,500`. Add unit tests in `WorkoutSamplesDatabaseManagerTest.kt`. | `REQ-DAT-048.2` / `TST-DAT-044.2` | `--tests "*WorkoutSamplesDatabaseManager*Test"` |
| **3** | **`DevicesDatabaseManager.java`, `LastActiveDateFormatter.kt`, `DeviceStatusRow.kt`, `DeviceSortOrder.kt`, `DeviceDataRepository.kt`**: (a) Rewrite `getCalibrationFactor` with two `try-with-resources` cursors + `!cursor.isNull(0)` fallback; (b) Replace silent `setInt` whitelist with explicit `ALLOWED_INT_COLUMNS` + `throw new IllegalArgumentException(...)`; (c) Upgrade `LastActiveDateFormatter` to ThreadLocal UTC ISO-8601 (`yyyy-MM-dd'T'HH:mm:ss'Z'`) + `parseToEpochMillis(lastSeen)` (UTC ISO -> legacy ISO -> cached `DateFormat`), wiring `DeviceStatusRow.getRelativeLastSeen` and `DeviceSortOrder.parseLastSeenTimestamp`; (d) Add `AND ${PAIRED}=1` to `DeviceDataRepository.getPairedRemoteDevices` SQL `WHERE`. Update `DevicesDatabaseManagerLastActiveTest.kt`, `DeviceSortOrderTest.kt`, `DevicesDatabaseManagerTest.kt`, and add `DeviceDataRepositoryPairedRemoteTest.kt`. | `REQ-DAT-048.3` / `TST-DAT-044.3` | `--tests "*DevicesDatabaseManager*Test" --tests "*DeviceSortOrderTest" --tests "*DeviceDataRepository*Test" --tests "*DeviceManagerTeardownTest"` |
| **4** | **`RoutesDatabaseManager.kt`**: Add `return` after `onCreate(db)` inside `if (oldVersion < 5)` (L940); in `duplicateRouteAsLocal` (L513–525), pass `route.path` and `route.waypoints` directly to `insertRoute` instead of calling `getRoutePath`/`getWaypointsForRoute` again. Add tests in `RoutesDatabaseManagerTTLTest.kt`. | `REQ-DAT-048.4` / `TST-DAT-044.4` | `--tests "*RoutesDatabaseManager*Test"` |
| **5** | **`ExtremaData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`**: (a) Add trailing `val sensorType: SensorType? = null` to `ExtremaDataRow`; (b) Populate `sensorType` in `createExtremaRow` & `createDerivedPaceRow`, replacing string checks (`"0"`, `"0.0"`, `"0,0"`, `"0:00"`) with numeric `rawMinVal`/`speedMax` checks; (c) In `WorkoutRepository.updateExtremaValueInMemory` & `addOrUpdateWorkout`, match by `sensorType` (lazy label fallback only when `sensorType == null`); (d) Extract `private suspend fun reloadWorkoutDataInternal(workoutId: Long, notifyInitialLoaded: Boolean)` (preserving `reloadWorkoutData(Long)` signature and radar enrichment when `notifyInitialLoaded = true`) and call it once in `assignClusterToWorkout`, `unassignClusterFromWorkout`, and both `createNewClusterFromWorkout` overloads, removing the redundant `loadWorkout` call. Add tests in `WorkoutDataMapperExtremaTest.kt` & `WorkoutRepositoryExtremaAndClusterTest.kt`. | `REQ-DAT-048.5` / `TST-DAT-044.5` | `--tests "*WorkoutDataMapper*Test" --tests "*WorkoutRepository*Test" --tests "*EditWorkout*Test"` |
| **6** | **Contract, Evidence & Live Emulator Smoke**: Run full targeted test suite + contract tests (`ArchitectureConformanceContractTest`, `TryWithResourcesCursorAuditTest`, `DetailedDesignParityContractTest`, `DatabaseSchemaRegistryParityContractTest`), verify `0` new compiler warnings, run `INSTALL_REQUIRED=true` live `emulator-5554` boot smoke (`installDebug` + `MainActivityWithNavigation` + `logcat -d -b crash` + `Rule 8.5` PNG inspection), update living docs to `[Verified]`, write `docs/engineering/walkthroughs/ATT-8128_walkthrough.md`, and run `auto_gate.py --write-bundle`. | `REQ-DAT-048` / `TST-DAT-044` | All targeted & contract suites PASS, `auto_gate.py` `PASS`, live smoke `0` crashes |

## 3. Targeted Verification Command (Stage 4/5)
```bash
python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun \
  --tests "*WorkoutSummariesDatabaseManager*Test" \
  --tests "*WorkoutSamplesDatabaseManager*Test" \
  --tests "*DevicesDatabaseManager*Test" \
  --tests "*DeviceSortOrderTest" \
  --tests "*DeviceDataRepository*Test" \
  --tests "*DeviceManagerTeardownTest" \
  --tests "*RoutesDatabaseManager*Test" \
  --tests "*WorkoutDataMapper*Test" \
  --tests "*WorkoutRepositoryExtremaAndClusterTest" \
  --tests "*WorkoutRepositoryConcurrencyTest" \
  --tests "*WorkoutRepositoryExportStatusTest" \
  --tests "*WorkoutRepositorySaveClusteringTest" \
  --tests "*EditWorkoutViewModelTest" \
  --tests "*EditWorkoutClusteringTest" \
  --tests "*ArchitectureConformanceContractTest" \
  --tests "*TryWithResourcesCursorAuditTest" \
  --tests "*DetailedDesignParityContractTest" \
  --tests "*DatabaseSchemaRegistryParityContractTest"
```
**Live Device / Emulator Requirement (`INSTALL_REQUIRED=true`)**: Touching `WorkoutRepository.kt` and core SQLite database managers requires a clean `emulator-5554` `installDebug` + `MainActivityWithNavigation` boot smoke + `logcat -d -b crash` check + `Rule 8.5` inspected PNG screenshot in Stage 5.
