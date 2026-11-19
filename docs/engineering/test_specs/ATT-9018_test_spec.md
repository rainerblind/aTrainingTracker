# Stage 2: Requirement & Test Specification - ATT-9018: Fix Bulk Strava Import OOM from Climbs.db Double-Deserialization & Per-Workout Reloads

**Ticket**: `ATT-9018` (`3 SP`, `Bug`, `severity-critical`) | **Parent Epic**: `ATT-235` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_01_Früh` | **Requirement Mapping**: `REQ-STB-021` | **Test Spec ID**: `TST-STB-021` | **Status**: `[Verified]`  
**Branch**: `feature/ATT-9018` | **Sub-task**: `[Req & Test Spec]` | **Author**: AI Agent 1 (`Pro`) | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-STB-021`)

### 1.1 Problem Statement & Rationale
During bulk Strava archive import (`3,287` rows, ~201 new workouts) against a `5.5 MB` `Climbs.db`, Java heap hit `509 / 512 MB` (`OutOfMemoryError`, `REQ-NFR-005`) from four compounding sources: (1) eager `Climbs.db` loading + `String.split` churning ~140k 13-field `PathPoint` objects per workout, (2) per-workout `LiveClimbsRepository.refreshClimbs()` reloading `Climbs.db` and duplicating `_allStoredClimbs`/`_allClimbs` lists, (3) `LegacyImportEngine.recalculateStats` calling direct `reloadWorkoutData` + `refreshClusters()` + `WORKOUT_UPDATED_INTENT` (triggering a 2nd `reloadWorkoutData`), and (4) `DevicesDatabaseManager.getLastActiveString()` allocating `DateFormat.getDateTimeInstance()` (`ZoneInfo.clone()`) per `setLastActive()`.

### 1.2 Attributes
- **Priority**: Critical | **Type**: Non-Functional | **Verification Method**: Unit / Integration
- **`SYS.2` / `SYS.3` Impact (`REQ-PRO-131`)**: None (`STB` is `SWE_DERIVED_PREFIXES`; external sensors, BLE/ANT+, FGS/WakeLock, and cloud APIs unchanged).
- **9-Language Localization**: `Localization Parity: N/A - no UI strings`.

### 1.3 Functional & Architectural Requirements
1. **`REQ-STB-021.1` (Zero-Split Deserialization, Lazy `Climbs.db` Deduplication & Sublist Detachment)**: `ClimbsDatabaseManager.deserializePathPoints` SHALL scan `;`/`,` by index without `String.split(...)` allocations, preserving `"null"` altitudes and numeric parity. `insertClimbWithDeduplication`, `insertClimbsWithDeduplicationBatch`, and `deduplicateExistingClimbs` SHALL query lightweight metadata (excluding `COLUMN_PATH_POLYLINE`), resolve endpoint matches (`<= effThreshold`) and reject non-overlapping climbs via heading (`<= 45°`), gain compatibility, and triangle-inequality ellipse slack ($\frac{1}{2}\sqrt{\max(0, L^2 - d_{se}^2)} + \text{effThreshold}$) WITHOUT loading `path_polyline`, lazily loading `COLUMN_PATH_POLYLINE` for both climbs ONLY when Steps 0–3 pass without endpoint match. `ClimbDetector.createClimb` (`:189`) SHALL detach `subList(...).toList()`.
2. **`REQ-STB-021.2` (`LiveClimbsRepository` Memory Deduplication & Serialized Refresh)**: `LiveClimbsRepository.refreshClimbs(activeRouteId)` SHALL skip `.copy(...)` when resolved category matches, deduplicate `_allStoredClimbs` once, share the single deduplicated `List<Climb>` reference across `_allStoredClimbs` and `_allClimbs` when `activeRouteId == null` (and in `applyFormula`), and serialize refreshes via a `Mutex`.
3. **`REQ-STB-021.3` (Eliminate Duplicate `reloadWorkoutData` & Defer Bulk Refreshes)**: `LegacyImportEngine.recalculateStats` SHALL NOT send `WORKOUT_UPDATED_INTENT` after direct `reloadWorkoutData` (`app != null`). Bulk paths (`StravaBulkExportImporter.importArchive`, multi-file `BackupRestoreViewModel.importWorkoutFilesInternal`, `bulkRecoverFromDropbox`, `bulkRecoverFromGoogleDrive`, `AutoImportManager.syncIncremental`, `backfillHistoricalClimbs`) SHALL use a coroutine-scoped `BulkImportBatchContext` (keeping existing MockK arities intact) to defer `reloadWorkoutData` / `refreshClusters()` / `refreshClimbs()` per workout while persisting climbs via `WorkoutClimbIngestionCoordinator`, executing at most one post-batch `loadAllWorkouts()` + `syncPeriodsIfDiscrepancy()` + `refreshClusters()` + conditional `refreshClimbs()` (only if climbs were persisted).
4. **`REQ-STB-021.4` (`ThreadLocal` `DateFormat` Caching)**: `DevicesDatabaseManager.getLastActiveString()` SHALL reuse a thread-local cached `DateFormat.getDateTimeInstance()` helper (`LastActiveDateFormatter`) keyed on `(Locale.getDefault(), TimeZone.getDefault())`, preserving `DeviceStatusRow`/`DeviceSortOrder` parsing parity.
5. **`REQ-STB-021.5` (Layering & Monolith LoC Ceilings)**: Extracted climb-ingestion helper (`WorkoutClimbIngestionCoordinator`) MUST reside outside `ui/` and `database/` (`climbs/`) so `ArchitectureConformanceContractTest` baselines (`BASELINE_ALLOWED_UI_DB_CALLERS == 22`, `BASELINE_ALLOWED_DATABASE_UPWARD_UI_IMPORTS <= 10`, `banalservice/database/` `0` `.ui.*` imports) remain intact; `WorkoutRepository.kt <= 4,973` LoC; `DevicesDatabaseManager.java <= 1,671` LoC.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-STB-021` (`REQ-STB-021.1`–`.5`) in `req_process_stability_part2.md`, refining `REQ-MAP-064.1`, `REQ-CLM-011.1/.2/.3`, and `REQ-MIG-026`, enforcing `REQ-NFR-005`.
* **Historical Origin & Commit Trace**: `70667799` (`ATT-909`), `6a07fa22` (`ATT-2766`), `64c23679` (`ATT-6691`), `fbcfcdeb` (`ATT-7311`), `845818c2` (`getLastActiveString`).
* **Root Reason for Existing Formulation**: `ATT-909` retained `WORKOUT_UPDATED_INTENT` alongside direct reload; `ATT-2766` refreshed `LiveClimbsRepository` per workout; `ATT-7311` loaded full `Climb` rows for `ClimbDeduplicator`.
* **Preservation of Core Invariants**: Single-file `TST-MIG-023` & `TST-MAP-066.1` immediacy preserved; 100m/70%/45° deduplication parity (including curved climbs) preserved; `Climbs.db` v1 schema, timestamp parsing parity, and monolith LoC ceilings preserved.

---

## 3. Acceptance Criteria (Given-When-Then) & Invariants
* **Criterion 1 (Zero-Split & Lazy Deduplication)**: *Given* `> 5 MB` `Climbs.db`, *When* `insertClimbsWithDeduplicationBatch` runs, *Then* distant climbs & endpoint matches never load `path_polyline`, curved corridor matches lazily hydrate `pathPoints`, and `deserializePathPoints` runs with `0` `String.split` calls.
* **Criterion 2 (No Per-Workout Reload/Refresh in Bulk)**: *Given* `200+`-workout bulk import, *When* workouts persist and stats recalculate, *Then* `LiveClimbsRepository.refreshClimbs()` runs `<= 1` post-batch, `reloadWorkoutData` runs `0` times in-loop (`1` time for single-file), and `WORKOUT_UPDATED_INTENT` triggers `0` duplicate reloads.
* **Criterion 3 (Heap & Formatter Stability)**: *Given* `200+`-workout bulk import and repeated `setLastActive()` calls under `512 MB` heap, *When* processed sequentially, *Then* heap stays bounded without OOM, `getLastActiveString()` reuses cached formatters, and `WorkoutRepository.kt <= 4,973` / `DevicesDatabaseManager.java <= 1,671` LoC hold.

---

## 4. Test Specification (`TST-STB-021`)
* **`TST-STB-021.1` (`ClimbsDatabaseManagerTest`, `ClimbDetectorTest`)**: Verify zero-split `deserializePathPoints` parity (`"null"` altitudes, whitespace, trailing `;`); verify metadata-only projection skips `COLUMN_PATH_POLYLINE` for distant climbs & endpoint matches while lazily loading `COLUMN_PATH_POLYLINE` for curved/switchback corridor candidates via ellipse slack; verify `ClimbDetector` returns detached `pathPoints`.
* **`TST-STB-021.2` (`LiveClimbsRepositoryTest`)**: Assert `_allStoredClimbs.value === _allClimbs.value` when `activeRouteId == null`, skip redundant `.copy(...)` when category matches, and serialize concurrent `refreshClimbs()` calls.
* **`TST-STB-021.3` (`TcxImportPeriodAndClusterIntegrationTest`, `StravaBulkExportImporterTest`, `BackupRestoreViewModel*Test`, `AutoImportManagerTest`, `WorkoutHistoricalClimbBackfillTest`, `WorkoutClimbIngestionTest`)**: Assert single-file `reloadWorkoutData` executes strictly once (`0` duplicate broadcast reloads); assert `200+`-workout bulk import runs `0` in-loop `reloadWorkoutData`/`refreshClusters()`/`refreshClimbs()`, persists climbs, and triggers `<= 1` post-batch reconciliation + conditional `refreshClimbs()` with bounded heap.
* **`TST-STB-021.4` (`DevicesDatabaseManagerTest`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`)**: Assert `ThreadLocal` `DateFormat` reuse, `(Locale, TimeZone)` invalidation, and `DeviceStatusRow`/`DeviceSortOrder` parsing parity; assert `WorkoutRepository.kt <= 4,973` LoC, `DevicesDatabaseManager.java <= 1,671` LoC, `BASELINE_ALLOWED_UI_DB_CALLERS == 22`, `BASELINE_ALLOWED_DATABASE_UPWARD_UI_IMPORTS <= 10`, `0` new `src/main/` test reads, `N/A` l10n, and governance `exit 0`.

---

## 5. Traceability Matrix

| Requirement | Description | Test Case | Target Suites | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STB-021.1` | Zero-split parse, lazy `Climbs.db` dedup + ellipse slack, detached sublist | `TST-STB-021.1` | `ClimbsDatabaseManagerTest`, `ClimbDetectorTest` | `[Verified]` |
| `REQ-STB-021.2` | `LiveClimbsRepository` list sharing & `Mutex`-serialized refresh | `TST-STB-021.2` | `LiveClimbsRepositoryTest` | `[Verified]` |
| `REQ-STB-021.3` | Zero duplicate `reloadWorkoutData` & bulk post-batch reconciliation | `TST-STB-021.3` | `TcxImportPeriodAndClusterIntegrationTest`, `StravaBulkExportImporterTest`, `BackupRestoreViewModel*Test`, `AutoImportManagerTest`, `WorkoutHistoricalClimbBackfillTest` | `[Verified]` |
| `REQ-STB-021.4` | `ThreadLocal` cached `DateFormat` & parsing parity | `TST-STB-021.4` | `DevicesDatabaseManagerTest`, `DevicesDatabaseManagerLastActiveTest` | `[Verified]` |
| `REQ-STB-021.5` | Helper in `climbs/`, `WorkoutRepository <= 4,973`, `DevicesDb <= 1,671`, `N/A` l10n | `TST-STB-021.4` | `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, `verify_requirement_governance.py` | `[Verified]` |
