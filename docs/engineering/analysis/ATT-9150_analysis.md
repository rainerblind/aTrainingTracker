# Stage 1 Analysis: ATT-9150 — Preserve Explicit `UPLOAD_TO_STRAVA = 0` Opt-Out in `applyInferredIdentity`

**Ticket**: [ATT-9150](https://rainerblind.atlassian.net/browse/ATT-9150) (`1 SP`, Medium, `severity-major`) | **Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-9150` (off `45fe3ef1`) | **Author**: Lane A Flash (`agent1`)  

---

## 1. Root Cause & Call-Site Audit
1. **Defect Location (`WorkoutSummariesDatabaseManager.java:322-344`)**: `applyInferredIdentity(long workoutId, InferredIdentity identity)` reads `Integer currentUpload = getInt(workoutId, WorkoutSummaries.UPLOAD_TO_STRAVA)` (line `335`) and overwrites `UPLOAD_TO_STRAVA` whenever `currentUpload == null || currentUpload != 1` (lines `336-338`).
2. **Trigger Path during Empty-DB TCX/GPX/FIT Import**:
   * `LegacyImportEngine` -> `WorkoutRepository.insertImportedWorkout()` (`WorkoutRepository.kt:1302`) writes `UPLOAD_TO_STRAVA = if (uploadToStrava) 1 else 0` (`0` on opt-out).
   * `LegacyImportEngine` line `1366` invokes `WorkoutClusterRepository.refreshClusters()`. When `RouteClusters.db` is empty (`WorkoutClusterRepository.kt:242-245`), it runs `recalculateClustersWithProgress()` -> `WorkoutClusterEngine.migrateHistory()` -> `assignClusterToWorkout()` -> `workoutRepo.applyInferredIdentity(...)` (`WorkoutClusterEngine.kt:898, 900, 902`).
   * Because `0 != 1`, `applyInferredIdentity` overwrites `UPLOAD_TO_STRAVA` from `0` to `identity.getUploadToStrava()` (`1` when Strava sport mapping exists), violating `REQ-MIG-028`.
   * In `TcxImportStravaToggleTest.kt:181-197`, an unrelated cluster had to be pre-seeded solely to prevent `migrateHistory()` from exposing this bug.
3. **All Callers of `applyInferredIdentity()`**: `TrackerService.java:3420, 3427, 3453`, `WorkoutClusterEngine.kt:898, 900, 902` (via `WorkoutRepository.kt:1710` & `ClusterWorkoutDataPort.kt:180`).

## 2. Independent Peer Review & Verification
* **SQLite Schema & Tri-State Semantics (`WorkoutSummariesDatabaseManager.java:2554, 2857`)**:
  * `UPLOAD_TO_STRAVA` (`"uploadToStrava"`) is defined as `int DEFAULT -1` (`-1: check preferences, 0: no, 1: yes`), both in `CREATE TABLE` (`2554`) and `onUpgrade(oldVersion < 16)` (`2857`).
  * `WorkoutRepository.createNewTrackedWorkout()` (`312-330`) omits `UPLOAD_TO_STRAVA`, so newly live-tracked workouts inherit SQLite `DEFAULT -1` until `TrackerService.java` (or cluster backfill) calls `applyInferredIdentity()` to populate `1` (or `0` when "No upload" per `REQ-EXP-006`).
  * Conversely, `insertImportedWorkout()` (`1302`), `EditWorkoutViewModel.kt:259, 409`, and `updateWorkoutData()` (`198`) explicitly persist `0` or `1` (`0` = explicit opt-out, `1` = explicit opt-in).
* **Hidden `getInt()` `NULL`-to-`0` Blind Spot (`WorkoutSummariesDatabaseManager.java:785-802`)**:
  * `getInt(workoutId, key)` returns `cursor.getInt(...)` without checking `cursor.isNull(...)`, so an explicit SQL `NULL` cell (e.g. legacy custom test schemas without `DEFAULT -1`) returns Java `0` rather than `null`.
  * Checking `cursor.isNull(0)` directly for `UPLOAD_TO_STRAVA` inside `applyInferredIdentity()` (updating ONLY when SQL `NULL` or `-1`) cleanly distinguishes undecided (`NULL`/`-1`) from explicit `0` (opt-out) and `1` (opt-in), while leaving `getInt()` untouched for all other callers.
* **LoC Ceiling Constraint (`ArchitectureConformanceContractTest.kt:120`)**: `WorkoutSummariesDatabaseManager.java` is currently at **`3,148 / 3,148` lines** (`0` LoC headroom). Fix MUST stay `<= 3,148` lines (`<= 0` net lines added).

## 3. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-MIG-028` (`TST-MIG-025`, *Optional Strava Upload on TCX Import*) and `REQ-SET-029`/`REQ-SET-030` (`WorkoutSummariesDatabaseManager.java:334-338`, `TcxImportStravaToggleTest.kt`).
* **Historical Origin & Commit Trace**: `applyInferredIdentity` added in `e0392e39` (`SCRUM-200`) unconditionally overwrote `UPLOAD_TO_STRAVA`; `b58ca19b` (`ATT-1116`) guarded `currentUpload != 1` to avoid downgrading explicit `1`, overlooking that `-1` (not `0`) is the undecided schema sentinel (`line 2554`).
* **Root Reason for Existing Formulation**: `ATT-1116` only tested opt-in preservation (`1` not overwritten to `0`) and did not distinguish undecided `-1`/`NULL` from explicit opt-out `0`.
* **Preservation of Core Invariants**: Live workouts (`UPLOAD_TO_STRAVA = -1`) and `NULL` rows continue receiving `identity.getUploadToStrava()` (`1` or `0`); explicit `1` opt-ins stay protected (`ATT-1116`); `REQ-EXP-006` ("No upload" -> `0`) and `WorkoutSummariesDatabaseManager.java <= 3,148` LoC remain intact.

## 4. Change Impact Analysis
* **Affected Requirements**: `REQ-MIG-028` (`TST-MIG-025`, existing `[Verified]` spec hardened against empty-cluster `migrateHistory()` bug); `REQ-SET-029`, `REQ-SET-030`, `REQ-EXP-006` (invariants preserved). **`SYS.2`/`SYS.3` Impact (`REQ-PRO-131.4`)**: **None** (`0` hardware, sensor, or external REST API changes).
* **Affected Architecture Elements**: `WorkoutSummariesDatabaseManager.applyInferredIdentity()` (`1` Java method) + `TcxImportStravaToggleTest.kt` (remove cluster seed workaround & assert empty-DB opt-out preservation) + unit coverage for `-1`/`NULL`/`0`/`1`.
* **Verification Impact & Test Surface**: Locked incremental `:app:testDebugUnitTest --rerun` targeting `TcxImportStravaToggleTest`, `TcxImportCommunityUploadTest`, `WorkoutSummariesDatabaseManager` / `WorkoutClusterEngine` unit suites, and `ArchitectureConformanceContractTest`. `Localization Parity: N/A - no UI strings`.
* **Risk Assessment & Invariants**: **LOW** (`1 SP`, single-method guard). Replacing `!= 1` with undecided-only check (`SQL NULL` or `-1`) cannot regress live workouts (`DEFAULT -1`) or opt-in (`1`) preservation and carries `0` schema migration risk.
