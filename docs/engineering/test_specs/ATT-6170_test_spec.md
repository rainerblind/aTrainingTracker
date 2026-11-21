# Stage 2: Requirement & Test Specification - ATT-6170

**Ticket**: `ATT-6170` (`1 SP`, `Bug`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469)  
**Requirement ID**: `REQ-DAT-046` (`[Planned]`) | **Test Spec ID**: `TST-DAT-042` (`[Planned]`)  
**Living Docs Shards**: `docs/requirements/req_data_migration_export_part2.md` (`v1.0.17`), `docs/tests/tst_data_migration_export.md` (`v1.0.19`), `docs/requirements.md`, `docs/tests.md`  
**Localization Parity**: N/A - 0 string changes (`0` edits to `values*/strings*.xml`)  
**Branch**: `feature/ATT-6170` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Requirement Specification (`REQ-DAT-046`)
* **Attributes**: **Priority**: `Medium` | **Type**: `Functional` | **Verification Method**: `Unit` | **System Requirement(s)**: `Derived (SW-only)` — internal in-memory cache merge in `WorkoutRepository.loadWorkout` without external sensor, REST API, or database schema changes.
* **Normative Contract (`REQ-DAT-046.1`–`.3`)**:
  1. `WorkoutRepository.loadWorkout(id)` SHALL continue enriching `radarTrafficSummary` when applicable and posting the freshly mapped `WorkoutData` (including updated SQLite cluster, sport, equipment, and name fields) to `_initialWorkoutLoaded` for `EditWorkoutViewModel`.
  2. `WorkoutRepository.loadWorkout(id)` SHALL delegate the `_allWorkouts` state update to `addOrUpdateWorkout(workout)` rather than overwriting via `newList[index] = workout`. When `id` exists in `_allWorkouts`, `addOrUpdateWorkout(workout)` SHALL preserve existing `exportStatuses` (when `workout.exportStatuses.isEmpty()`), `mapPolyline`, `encodedAltitudes`, `encodedDistances`, and `extremaRows` (including per-row `minLatLng`/`maxLatLng`), while propagating updated persisted fields (`clusterId`, `clusterName`, `workoutName`, `description`, `sportId`, `sportName`, `bSportType`, `equipmentId`, `equipmentName`, `stravaSportName`).
  3. When `id` is absent from `_allWorkouts`, `addOrUpdateWorkout(workout)` SHALL insert `workout` sorted descending by `headerData.startTimeS` under pure functional `_allWorkouts.update` CAS semantics. Sequential callers (`reloadWorkoutData(id)` -> `loadWorkout(id)`) remain safe without introducing a new per-ID lock (`1 SP` scope boundary); `WorkoutDataMapper.fromSnapshot` (`ATT-6038`) remains unchanged (`exportStatuses = emptyList()`).

---

## 2. Given-When-Then Acceptance Criteria & Invariants
| ID | Given | When | Then |
|---|---|---|---|
| `AC-1` | Cached workout in `_allWorkouts` with non-empty `exportStatuses` (Strava/Dropbox/GDrive), `mapPolyline`, `encodedAltitudes`, `encodedDistances`, and `extremaRows` (`minLatLng`/`maxLatLng`) | DB fields (`clusterId`, `clusterName`, `workoutName`, `sportId`, `equipmentId`) update and `loadWorkout(id)` runs | `_allWorkouts` preserves all `exportStatuses` and transient fields while reflecting updated DB fields; `_initialWorkoutLoaded` receives refreshed `WorkoutData`. |
| `AC-2` | Uncached workout absent from `_allWorkouts`, with existing entries at distinct `startTimeS` | `loadWorkout(id)` runs | Workout is inserted in descending `headerData.startTimeS` order in `_allWorkouts` and emitted on `_initialWorkoutLoaded`. |
| `AC-3` | Post-`ATT-6038` batch/symmetry & architecture suites | Regression suites execute | `WorkoutRepositoryBatchLoadTest`, `WorkoutDataMappingSymmetryTest`, and `ArchitectureConformanceContractTest` (`WorkoutRepository.kt <= 5,117` LoC) pass; `0` string changes (`Localization Parity: N/A`). |

* **System Invariants**: `WorkoutRepository.kt <= 5,117` LoC; `_initialWorkoutLoaded` emission preserved; `fromSnapshot` `exportStatuses = emptyList()` untouched (`ATT-6038` parity); pure CAS `_allWorkouts.update`; sequential `reloadWorkoutData -> loadWorkout` safe without new per-ID lock; `0` DB schema or `strings*.xml` edits.

---

## 3. Verification Test Suite (`TST-DAT-042`) & Traceability Matrix
| Test ID | Target File / Suite | Verification Scope | Linked REQ |
|---|---|---|---|
| `TST-DAT-042.1` | `WorkoutRepositoryExportStatusTest.kt` | Cached `loadWorkout(id)` preserves `exportStatuses`, `mapPolyline`, `encodedAltitudes`, `encodedDistances`, `extremaRows` (`minLatLng`/`maxLatLng`), updates DB fields, emits `_initialWorkoutLoaded` | `REQ-DAT-046.1`, `.2` |
| `TST-DAT-042.2` | `WorkoutRepositoryExportStatusTest.kt` | Uncached `loadWorkout(id)` inserts sorted descending by `headerData.startTimeS` and emits `_initialWorkoutLoaded` | `REQ-DAT-046.1`, `.3` |
| `TST-DAT-042.3` | `WorkoutRepositoryBatchLoadTest.kt`, `WorkoutDataMappingSymmetryTest.kt`, `ArchitectureConformanceContractTest.kt` | Batch loading parity, `fromCursor` delegation symmetry, `WorkoutRepository.kt <= 5,117` LoC, `0` string edits (`Localization Parity: N/A`) | `REQ-DAT-046.2`, `.3` |
