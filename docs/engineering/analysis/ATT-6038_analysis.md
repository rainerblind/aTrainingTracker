# Stage 1 Analysis: ATT-6038 - BatchMetadataLoader & Export-Status N+1 Elimination

**Ticket**: [ATT-6038](https://issue-tracker/ATT-6038) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Architecture & Perf*)  
**Target Release**: `V5.0.0` (`Rule 8.3`, active unreleased version) | **Sprint**: `2026-11-21_01_Früh` (Sprint 468)  
**Branch**: `feature/ATT-6038` (from `sprint/2026-11-21_01_Früh` @ `b7201f71`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Problem Statement & Root Cause
* **N+1 Export-Status Cascade (`8` queries/workout = `400`/50-batch)**: `WorkoutRepository.loadAllWorkouts` (`:3850-3860`) iterates `4` `orderedExportTypes`, calling `ExportStatusDataProvider.createGroupData(fileBaseName, type)`. Each call invokes `ExportStatusRepository.getExportRows` $\rightarrow$ `ExportStatusDatabaseManager.getExportRows` (`:556-585`), executing `healStaleProcessingRows(fileBaseName)` (`:374`) + `SELECT` (`:565`) `4x` for identical rows.
* **Incomplete `BatchMetadata` (~`7-8` queries/workout, incl. `2` full-table scans)**: `loadAllWorkouts` (`:3830-3842`) leaves `sportTypes`, `equipmentNames`, `locationNames`, and `athleteMetrics` empty. `WorkoutDataMapper.fromSnapshot` (`:785-1004`) falls back per workout to `3` uncached `SportTypeDatabaseManager` queries, `getEquipmentNameFromId`, `2x` `KnownLocationsDatabaseManager.getMyLocation` (full-table scan, `:200-243`), and `2x` `SettingsDataStoreJavaHelper.resolveEffective{Ftp,WeightKg}At` (`AthleteMetricsDatabaseManager.getEntryEffectiveAt`, `:368`).
* **Duplicate Mapper & Dead Code**: `fromCursor` (`:79-314`) duplicates `readCursorSnapshot`+`fromSnapshot` (`:676-1004`). `ESDM.getExportStatusMap` overloads (`:490-532`, `:587-625`) have `0` repo callers.

## 2. Independent Peer Review & Verification (Live `sprint/2026-11-21_01_Früh` @ `b7201f71`)
| Finding / Hypothesis | Verdict | Live Code Evidence |
|---|---|---|
| `loadAllWorkouts` N+1 (`~15` queries/workout) | **Confirmed** (shifted lines) | `WorkoutRepository.kt:3786-3885` (not `:1626`); `8` export + up to `8` metadata queries/workout (`~750-800`/50-batch). |
| Dead `getExportStatusMap` overloads | **Confirmed** | `ExportStatusDatabaseManager.java:490, :587` have `0` production/test callers (only cited in `ATT-3995` docs). |
| `fromSnapshot` no-fallback fields | **Confirmed** | `extrema`, `stravaData`, `clusterNames` (`:826`), `laps` (`:1002`) read **only** from `BatchMetadata`; `fromCursor` delegation MUST populate single-workout `BatchMetadata` + `reconcile = true`. |
| `PeriodsRepository` & `WorkoutClusterRepository` | **Confirmed** | `PeriodsRepository.kt:243` (`extrema`+`strava`), `:866` (lacks `athleteMetrics`, `cardioPowerDynamics`); `WorkoutClusterRepository.kt:726` omits `laps`, `sportTypes`, `equipmentNames`, `locationNames`, `athleteMetrics`, `cardioPowerDynamics`. |
| SQLite `IN (...)` & Architecture Guards | **Actionable Gap** | `StravaUploadDbHelper:324` lacks `<=999` bind-arg chunking (`LapsDb:296` chunks at `500`); `ArchitectureConformanceContractTest:118,157,241` forbids net-new `ui/*` DB callers (place `BatchMetadataLoader` in `repositories/`) and caps `WorkoutRepository.kt` (`4,985 <= 5,117` LoC). |
| `ATT-6170` dependency status | **Open (`Zu erledigen`)** | `WorkoutRepository.loadWorkout` (`:3723`) still overwrites `exportStatuses`; keep `loadWorkout` export-chip preservation in `ATT-6170`, preserve all `fromCursor` fields here. |

## 3. Scope Grounding & Complexity (`3 SP`)
* **In-Scope**: (1) `BatchMetadataLoader` in `repositories/` (loading all `BatchMetadata` fields incl. new `AthleteMetricsDatabaseManager` batch history resolution + `SettingsDataStore` fallbacks) wired into `WorkoutRepository.loadAllWorkouts`, `PeriodsRepository` (`:243,:866`), `WorkoutClusterRepository` (`:726`), and single-workout `WorkoutDataMapper.fromCursor` delegation (`reconcile = true`); (2) `ExportStatusDatabaseManager.getExportRowsForWorkouts` (`<= 500` chunked bind args) + single per-batch `healStaleProcessingRows()`, `ExportStatusRepository` batch pass-through, `ExportStatusDataProvider` preloaded-rows overload, and removal of both dead `getExportStatusMap` overloads; (3) Query-count & `WorkoutDataMappingSymmetryTest` verification.
* **Out-of-Scope**: `ATT-6170` `loadWorkout` export-status preservation; `ExportStatusRepository.createGroupData` refactor; DB schema changes.

## 4. Chesterton's Fence Archaeology (`REQ-PRO-022`)
1. **Original Requirement ID & Target**: Amends/refines `REQ-DAT-033` (batch `WorkoutDataMapper`, `ATT-4003` / `ATT-4707`), `REQ-DAT-009` (`ATT-508`), `REQ-MAP-020` (`ATT-528`), `REQ-STB-013`/`018` (`ATT-2309`/`4002`), and `REQ-DAT-029.6` (`ATT-3995` in `ExportStatusDatabaseManager.java`).
2. **Historical Origin & Commit Trace**: `ATT-4003` (`6073cf43`) added `BatchMetadata` in `PeriodsRepository`; `ATT-3995` added query-time 3-phase `healStaleProcessingRows` in `ExportStatusDatabaseManager`; `ATT-508`/`528` (`b4f69aef`, `d4a22c01`) added self-healing extrema writes on single-item `fromCursor`.
3. **Root Reason for Existing Formulation**: `loadAllWorkouts` predated `PeriodsRepository`'s extended `BatchMetadata`, leaving 4 maps empty and invoking `createGroupData` per `(workout, ExportType)` to reuse UI formatting; `fromCursor` retained direct DB reads so `loadWorkout`/`reloadWorkoutData` keep self-healing and all non-fallback fields.
4. **Preservation of Core Invariants**: Single-item `fromCursor` preserves `reconcile = true` self-healing and identical `WorkoutData` fields (`Rule 20`); `loadAllWorkouts` keeps `reconcile = false`, `batchSize = 50`, and `deletedDuringLoadIds` tombstone safety; stale `PROCESSING` rows still heal once per batch load.

## 5. System Invariants & Risk Rating
* **Invariants**: (1) All `IN (...)` bind-arg queries (`ExportStatusDatabaseManager`, `StravaUploadDbHelper`) chunk strictly `<= 999` (e.g., `500`); (2) 100% `WorkoutData` & `ExportStatusGroupData` parity (`athleteMetrics` effective-at `timestamp_ms <= startTimeMs` + `getLatestEntry` + `SettingsDataStore` fallback); (3) All SQLite cursors use `try (Cursor ...)` (`tools/lint_cursor_safety.py`); (4) Repositories expose zero `*DatabaseManager`/`Cursor` getters (`Rule 8.7`); (5) `BatchMetadataLoader` resides in `repositories/` (`WorkoutRepository.kt <= 5,117` LoC).
* **Risk Rating**: **MEDIUM** (`3 SP`) — touches core `WorkoutDataMapper` & `ExportStatusDatabaseManager`; mitigated by `WorkoutDataMappingSymmetryTest` + query-count contract tests.

## 6. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-DAT-033` (shared `BatchMetadataLoader` + `loadAllWorkouts` / `WorkoutClusterRepository` + batch `athleteMetrics`) and `REQ-DAT-029.6` (batch `getExportRowsForWorkouts`, removal of dead `getExportStatusMap`); preserves `REQ-DAT-009`, `REQ-MAP-020`, `REQ-STB-013`, `REQ-DAT-043`. `SYS.2`/`SYS.3`: None. Localization parity: N/A (`0` `strings*.xml` edits).
* **Affected Architecture Elements**: New `BatchMetadataLoader.kt` (`repositories/`); updates to `ExportStatusDatabaseManager.java`, `ExportStatusRepository.kt`, `ExportStatusDataProvider.kt`, `StravaUploadDbHelper.java` (chunking), `AthleteMetricsDatabaseManager.kt` (or batch helper), `WorkoutDataMapper.kt`, `WorkoutRepository.kt`, `PeriodsRepository.kt`, `WorkoutClusterRepository.kt`.
* **Verification Impact & Test Surface**: `WorkoutDataMappingSymmetryTest`, `ExportStatusDataProviderTest`, `ExportStatusDatabaseManagerTest`, `ArchitectureConformanceContractTest`, new `loadAllWorkouts` query-count & `getExportRowsForWorkouts` `>999` chunking tests, plus `python3 tools/lint_cursor_safety.py`.
* **Risk Assessment & Invariants**: **MEDIUM** — key risk is silent field loss in `fromCursor -> fromSnapshot` delegation or `ArchitectureConformanceContractTest` layer failure; both prevented by single-workout `BatchMetadata` assembly and `repositories/` placement.
