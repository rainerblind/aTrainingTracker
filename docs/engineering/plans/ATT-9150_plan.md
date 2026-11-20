# Stage 3 Implementation Plan: ATT-9150 — Preserve Explicit `UPLOAD_TO_STRAVA = 0` Opt-Out

**Ticket**: [ATT-9150](https://rainerblind.atlassian.net/browse/ATT-9150) (`1 SP`, Medium) | **Release**: `V5.0.0`  
**Branch**: `feature/ATT-9150` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-20  
**Requirement Mapping**: `REQ-MIG-054` (`[Planned]`, `SYSREQ-DEX-004`) | **Test Mapping**: `TST-MIG-052` (`[Planned]`)

---

## 1. SWE.2 Architecture, Gate 2 LoC Budget & Invariants
- **Layering**: Fix sits cleanly in the storage layer (`WorkoutSummariesDatabaseManager.applyInferredIdentity()`) below `WorkoutRepository` / `ClusterWorkoutDataPort` / `WorkoutClusterEngine` / `TrackerService`, requiring zero API signature changes and zero schema migrations.
- **LoC Ceiling & Cursor Safety Budget (`WorkoutSummariesDatabaseManager.java` `<= 3,148 / 3,148` LoC)**:
  - Currently `3,148` lines (`ArchitectureConformanceContractTest.kt:120` = `3,148` limit -> `0` LoC headroom).
  - Replace the 10-line `getInt(...)` + `currentUpload != 1` + `update(...)` block (`lines 334-343`) with a 9-line `try (Cursor c = getDatabase().query(...))` block (`-1` net line -> **`3,147 <= 3,148` LoC**):
    - If `!c.moveToFirst()` -> `return;` (safe no-op on non-existent `workoutId`).
    - Update `UPLOAD_TO_STRAVA` ONLY when `c.isNull(0) || c.getInt(0) == -1`, preserving explicit `0` (opt-out) and `1` (opt-in) while keeping `getInt()` untouched for legacy callers.
    - Passes `tools/lint_cursor_safety.py` (`try-with-resources`, zero chained cursor calls).
- **UI Consistency & `SYS.2`/`SYS.3` (`REQ-PRO-131.4`)**: `N/A - storage-layer bugfix; zero UI, resource, hardware, or external API changes`. `Localization Parity: N/A - no UI strings`.

---

## 2. Atomic Stage 4 Implementation Steps
| Step | Target File(s) | Atomic Action | Mapped IDs |
| :--- | :--- | :--- | :--- |
| **0** | Jira Gate 3 Lock | Execute `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py --as agent1 check-gate <IMPL_PLAN_SUBTASK_KEY>` (`exit 0`, `Rule 4`) before any `app/` edit (`0` `app/` edits in Stage 3). | Gate 3 Lock |
| **1** | `WorkoutSummariesDatabaseManager.java` (`lines 334-343`) | Replace `getInt()` / `!= 1` guard with `try (Cursor c = ...)` querying `UPLOAD_TO_STRAVA`: return on `!c.moveToFirst()`, put `UPLOAD_TO_STRAVA` only if `c.isNull(0) || c.getInt(0) == -1`, parameterize `update` (`<= 3,147` LoC). | `REQ-MIG-054` |
| **2** | `TcxImportStravaToggleTest.kt` (`lines 181-197`) | Remove the non-empty `WorkoutClusterDatabaseManager.insertCluster(...)` seed workaround so `RouteClusters.db` is empty during `importFromTcxResult(..., uploadToStrava = false)` (`refreshClusters()` -> `migrateHistory()` -> `applyInferredIdentity()`), tag `// REQ-MIG-054` / `// TST-MIG-052`, verify `UPLOAD_TO_STRAVA == 0` and zero Strava export jobs. | `TST-MIG-052` (Case a) |
| **3** | `WorkoutSummariesDatabaseManagerApplyIdentityTest.kt` (new, `0` `src/main` reads) | Add unit tests tagged `// REQ-MIG-054`, `// TST-MIG-052` covering: `-1` live default -> `1` and `0` (`REQ-EXP-006`); SQL `NULL` -> `1`; explicit `0` preserved; explicit `1` preserved (`ATT-1116`); non-existent `workoutId` safe no-op. | `TST-MIG-052` (Cases b–e) |
| **4** | Verification & Linters | Run `tools/lint_cursor_safety.py`, `verify_requirement_governance.py`, and locked incremental Gradle verification (`Rule 7.5`). | `REQ-MIG-054`, `TST-MIG-052` |

---

## 3. Exact Locked Verification Command (`Rule 7.5`)
```bash
python3 tools/lint_cursor_safety.py && \
python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
  --tests "com.atrainingtracker.trainingtracker.migration.TcxImportStravaToggleTest" \
  --tests "com.atrainingtracker.trainingtracker.migration.TcxImportCommunityUploadTest" \
  --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManagerApplyIdentityTest" \
  --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterEngineLearningTest" \
  --tests "com.atrainingtracker.trainingtracker.database.WorkoutClusterRepositoryTest" \
  --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest"
```
*(Never pass `--rerun-tasks` or `-PstampGitHash=true`; keep `source-reading-baseline.txt` and `ui-reflection-baseline.txt` untouched.)*
