# Stage 2 Requirement & Test Specification: ATT-6716 - FIT HRV Mesg Reset, Incremental Flush & 5s Grid Recompute

**Ticket**: [ATT-6716](https://rainerblind.atlassian.net/browse/ATT-6716)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-034` (`REQ-PER-034.1`, `REQ-PER-034.2`, `REQ-PER-034.3`, `REQ-PER-034.5`)  
**Test Spec ID**: `TST-PER-042` (`TST-PER-042.1`, `TST-PER-042.2`, `TST-PER-042.3`, `TST-PER-042.5`, `TST-PER-042.6`)  
**Branch**: `feature/ATT-6716` | **Author**: AI Agent 1 (Specifier, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PER-034)

### 1.1 Problem Statement & Scope
* `FitWorkoutParser.kt` flattens `HrvMesg` intervals, and `LegacyImportEngine.kt` passes constant `startMs` for all beats. Cumulative RR intervals exceeding `startMs + 2000ms` trigger false drift resets back to `startMs`, collapsing entire sessions into the first 2 seconds and writing `NULL` post-workout HRV streams.
* `TrackerService.java` buffers all live RR intervals in RAM (`mLiveRrQueue`) without incremental flushing, risking complete data loss on termination, and fails to gate pre-start/paused beats or resume `mRrBeatIndexCounter`.
* `HrvPostWorkoutRecomputer.kt` scans all beats quadratically and recalculates DFA $\alpha_1$ at 1 Hz, omitting session `SDNN` and lacking a `UNIQUE(workout_id, beat_index)` index.

### 1.2 Functional & Architectural Requirements
1. **FIT Anchor Reconstruction (`REQ-PER-034.5`)**: Preserve chronological `RecordMesg` timestamp anchors (`FitRrBeat(rrMs, anchorTimestampMs)`) during FIT decoding. Pass active anchors to `reconstructor.reconstruct()`, enabling monotonic accumulation ($t_k = t_{k-1} + \text{round}(rr_k)$) and drift resync strictly on real pauses.
2. **Incremental Flushing & Service Lifecycle (`REQ-PER-034.2`)**: Gate `mRrBeatListener` by active `TRACKING && workoutId > 0`. Flush `mLiveRrQueue` incrementally in batched SQLite transactions (every 30–60s or 100 beats), and restore `mRrBeatIndexCounter` to `MAX(beat_index) + 1` on workout resume.
3. **Two-Pointer 5s Grid & Session SDNN (`REQ-PER-034.3`)**: Implement an $O(N)$ two-pointer sliding window on a 5s grid (latching values to intermediate 1-Hz samples) in `HrvPostWorkoutRecomputer.kt`. Calculate and persist unbiased session `SDNN` ($N \ge 2$, artifact rate $\le 5\%$) to `WorkoutSummaries`, sequence after `endWorkout()` summary finalization, and dispatch `WorkoutRepository.refreshWorkout`.
4. **Unique Index & Repository Routing (`REQ-PER-034.1`)**: Enforce `UNIQUE(workout_id, beat_index)` with v2 schema migration in `WorkoutRrDbHelper.kt`, and route callers through `WorkoutRrRepository`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Multi-Minute FIT Import)**: *Given* a multi-minute FIT file with `HrvMesg` records, *When* imported, *Then* beat timestamps accumulate monotonically across the full workout and centered DFA $\alpha_1$ / rMSSD streams populate without collapsing to the first 2 seconds.
* **AC 2 (Incremental Flush & Resume)**: *Given* an active live workout, *When* tracking progresses or is resumed after pause, *Then* RR intervals flush periodically in batches, non-tracking beats are discarded, and beat indexes resume monotonically without duplicate key collisions.
* **AC 3 (5s Grid & SDNN)**: *Given* a finished workout with RR intervals, *When* recomputed, *Then* two-pointer sliding windows evaluate metrics on a 5s grid with $O(N)$ scaling and session `SDNN` is stored in `WorkoutSummaries`.

### 1.4 System Invariants
* 120s centered DFA $\alpha_1$ and 60s centered rMSSD math remain strictly intact.
* Non-HRV workouts incur zero SQLite storage or recomputation overhead.
* Database upgrade from v1 to v2 preserves all existing recorded intervals without data loss.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-PER-034` (`REQ-PER-034.1`, `REQ-PER-034.2`, `REQ-PER-034.3`, `REQ-PER-034.5`), targeting `FitWorkoutParser.kt`, `LegacyImportEngine.kt`, `TrackerService.java`, `HrvPostWorkoutRecomputer.kt`, `WorkoutRrDbHelper.kt`, and `WorkoutRrRepository.kt`.
2. *Historical Origin & Commit Trace*: Introduced in Sprint `2026-46.1` (`ATT-3543`, commit `cb0d4fb7`).
3. *Root Reason for Existing Formulation*: Initial ATT-3543 prototype used a 2-second synthetic FIT test fixture that masked the constant `startMs` drift collapse; live recording deferred flushing to `endWorkout()` in RAM; recomputation re-filtered the entire beat array at 1 Hz, creating quadratic slowdown on multi-hour workouts.
4. *Preservation of Core Invariants*: 120s centered DFA $\alpha_1$ (with $\le 5\%$ artifact gate) and 60s centered rMSSD math remain strictly intact; 5s grid decimation exactly aligns with `ATT-3543` Scope §2; database upgrade to v2 strictly preserves existing recorded intervals without data loss; non-HRV workouts continue to incur zero overhead.

---

## 3. Test Specification (TST-PER-042)

### Test Case 1: Multi-Minute FIT Import with Interleaved Record Anchors (`TST-PER-042.5`)
* **Scope**: `LegacyImportEngineFitHrvTest.kt`. Verify that a 10-minute synthetic FIT file with `HrvMesg` intervals accumulates monotonic timestamps and generates non-null centered HRV streams across the entire duration without 2000 ms collapse.

### Test Case 2: Incremental Batched Flushing & Resume Counter (`TST-PER-042.2`)
* **Scope**: `TrackerServiceTest.kt` / `WorkoutRrRepositoryTest.kt`. Verify that live beats flush in batches of 100, pre-start/paused beats are rejected, and resuming tracking restores `mRrBeatIndexCounter` to `MAX(beat_index) + 1`.

### Test Case 3: Two-Pointer 5s Grid Scaling & Session SDNN (`TST-PER-042.3`)
* **Scope**: `HrvPostWorkoutRecomputerTest.kt`. Verify that two-pointer sliding window recomputation scales $O(N)$ across a 5s grid, latches intermediate 1-Hz samples, calculates unbiased session SDNN, and persists extrema to `WorkoutSummaries`.

### Test Case 4: Schema Migration v2 & UNIQUE Constraint (`TST-PER-042.1`)
* **Scope**: `WorkoutRrDatabaseManagerTest.kt`. Verify that `WorkoutRrDbHelper` migrates from v1 to v2, enforces `UNIQUE(workout_id, beat_index)`, and handles conflict replacement or duplicate prevention.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-PER-042.6`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.build.*"`.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-042.5]` | Integration | Multi-minute FIT import & interleaved anchor reconstruction | `REQ-PER-034.5` | Specified |
| `[TST-PER-042.2]` | Unit / Service | Incremental batched flushing, state gating & counter resumption | `REQ-PER-034.2` | Specified |
| `[TST-PER-042.3]` | Unit / Algorithmic | Two-pointer 5s grid sliding window & session SDNN persistence | `REQ-PER-034.3` | Specified |
| `[TST-PER-042.1]` | SQLite / Schema | `WorkoutRrDbHelper` v2 migration & `UNIQUE(workout_id, beat_index)` | `REQ-PER-034.1` | Specified |
| `[TST-PER-042.6]` | Regression | Tier 1 clean-room regression across modified packages | `REQ-PRO-001` | Specified |
