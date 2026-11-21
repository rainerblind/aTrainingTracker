# Stage 3: Implementation Plan - ATT-6170: Preserve `exportStatuses` in `WorkoutRepository.loadWorkout`

**Ticket**: `ATT-6170` (`1 SP`, `Bug`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469)  
**Requirement**: `REQ-DAT-046` (`REQ-DAT-046.1`–`.3`) | **Test Spec**: `TST-DAT-042` (`TST-DAT-042.1`–`.3`)  
**Branch**: `feature/ATT-6170` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. SWE.2 Architectural Decomposition, Threading & Governance
* **Layering & Threading**: `WorkoutRepository.loadWorkout(id: Long)` remains confined to the repository layer executing SQLite cursor reads on `Dispatchers.IO`, emitting `_initialWorkoutLoaded.postValue(workout)` (`LiveData` main-safe post), and updating `_allWorkouts` atomically via pure functional `MutableStateFlow.update` CAS (`addOrUpdateWorkout`).
* **SWE.2 / SWE.3 & UI Consistency**:
  * `docs/architecture.md` (`Rule 8.8`): **None** — single-method delegation (`loadWorkout` -> `addOrUpdateWorkout`) inside existing `WorkoutRepository`; zero new subsystems, repositories, DB managers, or prefix families.
  * `docs/architecture/detailed_design/` (`REQ-PRO-114`): **None** — zero `TrackerService`, `BANALService`, or `MyBTLEDevice` state changes.
  * `UI Consistency` (`docs/design_guidelines.md`): **N/A** — zero UI/Compose or `values*/strings*.xml` edits (`Localization Parity: N/A`).

---

## 2. Atomic Construction Steps (Stage 4 — Post-Gate 3 Only)
| Step | Action & Target Files | Mapped Req / Test | Verification / Guardrail |
|---|---|---|---|
| **1** | **Surgical Merge Delegation (`WorkoutRepository.kt:3716-3744`)**: Inside `loadWorkout(id: Long)`, keep cursor read (`:3718`), `mapper.fromCursor(cursor)` (`:3720`), `radarTrafficSummary` enrichment (`:3721-3726`), and `_initialWorkoutLoaded.postValue(workout)` (`:3727`). Replace the manual `_allWorkouts.update { ... newList[index] = workout ... }` block (`:3729-3740`) with `addOrUpdateWorkout(workout)` (`:4363-4396`). Confirm `addOrUpdateWorkout` preserves cached `exportStatuses`, `mapPolyline`, `encodedAltitudes`, `encodedDistances`, and `extremaRows` (`minLatLng`/`maxLatLng`), propagates refreshed DB fields (`clusterId`, `clusterName`, `workoutName`, `description`, `sportId`, `sportName`, `bSportType`, `equipmentId`, `equipmentName`, `stravaSportName`), uses pure CAS `_allWorkouts.update`, and inserts uncached workouts sorted descending by `headerData.startTimeS`. | `REQ-DAT-046.1`, `.2`, `.3` | `WorkoutRepository.kt` shrinks `4,997 -> ~4,987` LoC (`<= 5,117` ceiling in `ArchitectureConformanceContractTest.kt:118`); `fromSnapshot` (`ATT-6038`) untouched; sequential `reloadWorkoutData -> loadWorkout` callers remain safe without new locks. |
| **2** | **Targeted Unit Tests (`WorkoutRepositoryExportStatusTest.kt`)**: Add unit tests for `loadWorkout(id)`: (1) pre-populated cached workout with non-empty `exportStatuses` (e.g., Strava/Dropbox/GDrive) + `mapPolyline` + `encodedAltitudes` + `encodedDistances` + `extremaRows` (`minLatLng`/`maxLatLng`) reloaded after DB cluster/name update -> preserves `exportStatuses` and all transient fields while reflecting updated `clusterId`/`clusterName`/`workoutName` and emitting `_initialWorkoutLoaded`; (2) uncached workout loaded via `loadWorkout(id)` -> inserted into `_allWorkouts` in descending `startTimeS` order and posted to `_initialWorkoutLoaded`. | `TST-DAT-042.1`, `.2` | Deterministic plain-JVM execution via MockK (`mockSummariesDb` cursor/mapper), zero `Thread.sleep`, zero real DataStore/Android context leakage. |
| **3** | **Tier-1 Verification Plan (`Rule 7.5`)**: Run incremental Gradle unit/contract tests (`-Dkotlin.daemon.jvm.options=-Xmx3072m`, `--rerun`, NEVER `--rerun-tasks`): `./gradlew :app:testDebugUnitTest --rerun -Dkotlin.daemon.jvm.options=-Xmx3072m --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryExportStatusTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryBatchLoadTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMappingSymmetryTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`; run `python3 tools/lint_cursor_safety.py`, `python3 tools/check_secrets_hygiene.py`, and `python3 tools/verify_requirement_governance.py`. | `TST-DAT-042.3` | `100%` pass rate (`0` skipped/failed), `WorkoutRepository.kt <= 5,117` LoC, `0` cursor/secret/governance warnings. |

---

## 3. System Invariants & Rollback Safety
* **Invariants**: `_initialWorkoutLoaded.postValue(workout)` (`:3727`) preserved for `EditWorkoutViewModel.kt:165`; `WorkoutDataMapper.fromSnapshot` retains `exportStatuses = emptyList()`; `WorkoutRepository.kt <= 5,117` LoC; pure CAS `_allWorkouts.update`; `0` DB schema or `strings*.xml` edits.
* **Code Lock (`Rule 4.1`) & Rollback**: Zero `app/` edits until `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns `0` (`GATE_PASSED`). Work stays isolated on `feature/ATT-6170` until Stage 6 `PASS`.
