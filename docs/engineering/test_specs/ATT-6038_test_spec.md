# Stage 2: Requirement & Test Specification - ATT-6038

**Ticket**: [ATT-6038](https://issue-tracker/ATT-6038) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Architecture & Perf*)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Sprint**: `2026-11-21_01_Früh` (Sprint 468)  
**Requirement ID**: `REQ-DAT-045` (`[Planned]`) | **Test Spec ID**: `TST-DAT-041` (`[Planned]`)  
**Living Docs Shards**: `docs/requirements/req_data_migration_export_part2.md` (`v1.0.8`), `docs/tests/tst_data_migration_export.md` (`v1.0.9`), `docs/requirements.md`, `docs/tests.md`  
**9-Language Localization Parity**: N/A — internal SQLite batching & mapper consolidation; zero new/modified `values*/strings*.xml` strings.  
**Branch**: `feature/ATT-6038` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Requirement Specification (`REQ-DAT-045`)
* **Attributes**: **Priority**: `Medium` | **Type**: `Non-Functional` | **Verification Method**: `Unit` | **System Requirement(s)**: `Derived (SW-only)` (refining `REQ-DAT-033`, `REQ-DAT-029.6`).
* **Normative Contract (`REQ-DAT-045.1`–`.4`)**:
  1. `ExportStatusDatabaseManager.getExportRowsForWorkouts(Collection<String>)` SHALL run `healStaleProcessingRows()` at most **once** per batch and query `WHERE file_base_name IN (...)` in `<= 500` bind-argument chunks (`try (Cursor ...)`); `ExportStatusRepository` SHALL expose the batch query without `*DatabaseManager`/`Cursor` getters (`Rule 8.7`); `ExportStatusDataProvider` SHALL add a preloaded-rows overload; both dead `getExportStatusMap` overloads SHALL be deleted.
  2. `StravaUploadDbHelper.getStravaActivityDataForWorkouts(Collection<String>)` SHALL chunk `IN (...)` bind arguments at `<= 500` (`<= 999` SQLite limit).
  3. `BatchMetadataLoader` in `com.atrainingtracker.trainingtracker.repositories` (`0` net-new `ui/*` DB callers, `WorkoutRepository.kt <= 5,117` LoC) SHALL batch-load all `BatchMetadata` fields (`extrema`, `stravaData`, `clusterNames`, `laps`, `sportTypes`, `equipmentNames`, `locationNames`, `cardioPowerDynamics`, and `athleteMetrics` matching `resolveEffectiveFtpAt`/`resolveEffectiveWeightKgAt` semantics) for `WorkoutRepository.loadAllWorkouts`, `PeriodsRepository`, and `WorkoutClusterRepository`.
  4. `WorkoutDataMapper.fromCursor(Cursor)` SHALL delegate to `readCursorSnapshot` + `fromSnapshot(..., reconcile = true)` with a complete single-workout `BatchMetadata`, preserving `REQ-DAT-009`/`REQ-MAP-020` self-healing and `ATT-6170` `loadWorkout` boundaries.

## 2. Given-When-Then Acceptance Criteria & Invariants
| ID | Given | When | Then |
|---|---|---|---|
| `AC-1` | `> 999` (`1,200`) export/Strava rows | `getExportRowsForWorkouts` / `getStravaActivityDataForWorkouts` runs | `<= 500` chunked queries succeed without `SQLiteException`; `healStaleProcessingRows()` runs `<= 1x`; `getExportStatusMap` absent. |
| `AC-2` | `50` workouts in `loadAllWorkouts` | Batch metadata + export rows load | `BatchMetadataLoader` + `getExportRowsForWorkouts` execute `<= 12` auxiliary queries total (`0` per-workout fallbacks); `exportStatuses` preserved. |
| `AC-3` | `PeriodsRepository` & `WorkoutClusterRepository` | Batch workout lists load | Both delegate to `BatchMetadataLoader` with full `BatchMetadata` parity. |
| `AC-4` | Single-row cursor in `fromCursor` | Mapped via `fromSnapshot(..., reconcile = true)` | Identical `WorkoutData` + self-healing writes (`WorkoutDataMappingSymmetryTest`), `ATT-6170` `loadWorkout` untouched. |

* **System Invariants**: `IN (...)` chunks `<= 500` (`<= 999`); `try (Cursor ...)` (`lint_cursor_safety.py`); zero repository `*DatabaseManager`/`Cursor` getters (`Rule 8.7`); `WorkoutRepository.kt <= 5,117` LoC & `0` net-new `ui/*` DB callers (`ArchitectureConformanceContractTest`).

## 3. Verification Test Suite (`TST-DAT-041`) & Traceability Matrix
| Test ID | Target Class / File | Verification Scope | Linked REQ |
|---|---|---|---|
| `TST-DAT-041.1` | `ExportStatusDatabaseManagerTest.kt`, `ExportStatusDataProviderTest.kt` | `1,200`-row (`>999`) chunked `getExportRowsForWorkouts`, `<=1x` heal, preloaded `createGroupData`, dead `getExportStatusMap` removal | `REQ-DAT-045.1` |
| `TST-DAT-041.2` | `StravaUploadDbHelperBatchTest.kt` | `1,200`-row (`>999`) chunked `getStravaActivityDataForWorkouts` parity | `REQ-DAT-045.2` |
| `TST-DAT-041.3` | `BatchMetadataLoaderTest.kt` | All 9 `BatchMetadata` maps + `athleteMetrics` (`<= startTimeMs`, latest fallback, `SettingsDataStore` defaults) | `REQ-DAT-045.3` |
| `TST-DAT-041.4` | `WorkoutRepositoryBatchLoadTest.kt` | `50`-workout `loadAllWorkouts` query-count bound (`<= 12` vs `~750`) + `PeriodsRepository`/`WorkoutClusterRepository` wiring | `REQ-DAT-045.3`, `.1` |
| `TST-DAT-041.5` | `WorkoutDataMappingSymmetryTest.kt`, `ArchitectureConformanceContractTest`, `lint_cursor_safety.py` | `fromCursor` -> `fromSnapshot(reconcile=true)` parity, `ATT-6170` boundary, UI/DB layer & `5,117` LoC ceiling | `REQ-DAT-045.4`, `.3` |
