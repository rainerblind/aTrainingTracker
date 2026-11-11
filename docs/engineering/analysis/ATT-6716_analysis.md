# Stage 1 Analysis: ATT-6716 - Fix FIT HrvMesg Reset, Incremental RR Flush & 5s Grid Recomputation

**Ticket**: [ATT-6716](https://rainerblind.atlassian.net/browse/ATT-6716)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6716` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Follow-up from Sprint `2026-46.1` Joint Review of ATT-3543 reveals three critical defects across the post-workout and live HRV pipeline:
1. **FIT HrvMesg Timestamp Reset Bug**: Garmin FIT file import collapses multi-minute/hour RR intervals into `[startMs, startMs + 2000ms]`. `FitWorkoutParser.kt` flattens `HrvMesg` into an unanchored list, and `LegacyImportEngine.kt:844` passes constant `startMs` for every beat. When elapsed time exceeds 2000 ms, `RrBeatTimestampReconstructor` resets timestamps back to `startMs`, corrupting timestamps and writing `NULL` for `HRV_DFA_A1` and `HRV_RMSSD` across the rest of the workout.
2. **Memory Accumulation & End-of-Workout Race Condition**: `TrackerService.java` buffers all live RR intervals in RAM (`mLiveRrQueue`) without incremental flushing, risking complete data loss on process termination. Pre-start and paused beats are queued even when `mWorkoutID <= 0`, `mRrBeatIndexCounter` resets to 0 on resume (violating uniqueness), and `HrvPostWorkoutRecomputer` is spawned asynchronously in `ForkJoinPool` during `endWorkout()` without sequencing or `WorkoutRepository.refreshWorkout` dispatch.
3. **Quadratic Post-Workout Bottleneck & Missing Constraints**: `HrvPostWorkoutRecomputer.kt` scans all beats via `filteredBeats.filter { ... }` and evaluates `DfaCalculator.computeDfaAlpha1` at 1 Hz, generating $>2 \cdot 10^8$ operations on a 3-hour workout. Session SDNN is never persisted to `WorkoutSummaries`, `WorkoutRrDbHelper.kt` lacks a `UNIQUE(workout_id, beat_index)` constraint, and `WorkoutRrRepository` is bypassed.

---

## 2. Root Cause Analysis & Independent Forensic Verification
* **FIT HrvMesg Reconstructor Collapse**: In `FitWorkoutParser.kt:105, 308`, `HrvMesg` records are decoded independently of `RecordMesg` order. In `LegacyImportEngine.kt:844`, `reconstructor.reconstruct(startMs, rrMs)` passes `startMs` constantly. Once `t_candidate > startMs + 2000ms`, `drift > 2000ms` fires continuously, resetting every subsequent beat timestamp to `startMs`.
* **RAM Buffering & Resume Counter Collision**: In `TrackerService.java:263-279`, `onRrBeat` does not check `mStatus == STATUS.TRACKING` or `mWorkoutID > 0`. `mLiveRrQueue` only drains in `endWorkout()`. On user resume or service recreation, `mRrBeatIndexCounter` defaults to 0, producing collision against previously persisted beats.
* **1-Hz Full-Scan Recompute**: In `HrvPostWorkoutRecomputer.kt:142-180`, each 1-Hz trackpoint re-filters the entire beat list twice and invokes `computeDfaAlpha1`. Instead of an $O(N)$ two-pointer sliding window on a 5s grid (ATT-3543 Scope §2), CPU is pegged for tens of seconds post-workout.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Preserve chronological `RecordMesg` timestamp anchors during FIT decoding (`FitRrBeat(rrMs, anchorTimestampMs)`) and pass the active anchor to `RrBeatTimestampReconstructor.reconstruct(anchorMs, rrMs)`.
  * Add multi-minute FIT round-trip test (`LegacyImportEngineFitHrvTest.kt`) verifying monotonic timestamps and valid centered HRV recomputation.
  * Flush `mLiveRrQueue` periodically (every 100 beats or 30s) in batched transactions in `TrackerService.java`, guard `onRrBeat` by `mStatus == STATUS.TRACKING && mWorkoutID > 0`, and initialize `mRrBeatIndexCounter` to `MAX(beat_index) + 1` on resume.
  * Cleanly sequence `HrvPostWorkoutRecomputer` in `endWorkout()` and invoke `WorkoutRepository.refreshWorkout(workoutId)` on completion.
  * Implement two-pointer sliding window evaluated on a 5s grid in `HrvPostWorkoutRecomputer.kt`, calculate and persist session `SDNN` into `WorkoutSummaries`, enforce `UNIQUE(workout_id, beat_index)` with v2 schema migration in `WorkoutRrDbHelper.kt`, and route callers through `WorkoutRrRepository`.
* **Out-of-Scope Non-Goals**:
  * Changing live cockpit rolling DFA $\alpha_1$ window algorithm or morning readiness checks.
  * Modifying Garmin FIT export protocol (`FitFileWriter.java` already writes interleaved `HrvMesg`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement IDs**: `REQ-PER-034.1` (Dedicated SQLite Storage), `REQ-PER-034.3` (Centered-Window Recompute), `REQ-PER-034.5` (Garmin FIT Export/Import) in `docs/requirements/req_aftermath_periods.md`.
* **Historical Origin**: Introduced in Sprint `2026-46.1` (`ATT-3543`, commit `cb0d4fb7`).
* **Root Reason for Existing Formulation**: ATT-3543 established initial end-to-end wiring. However, the FIT test used a 2-second fixture that never exceeded the 2000 ms drift threshold, masking the reconstructor reset bug. Live streaming deferred flushing to `endWorkout()` as an initial simplification.
* **Preservation of Core Invariants**: 120s centered DFA $\alpha_1$ (with $\le 5\%$ artifact gate) and 60s centered rMSSD math remain strictly intact; 5s grid decimation exactly aligns with `ATT-3543` Scope §2; database upgrade strictly preserves existing recorded intervals.

---

## 5. Architectural Strategy & High-Level Solution
1. **Interleaved FIT Parsing**: In `FitWorkoutParser.kt`, track active `RecordMesg` timestamp as `currentAnchorTimestampMs` during `MesgBroadcaster` iteration. Associate each `HrvMesg` interval with this anchor. In `LegacyImportEngine.kt`, pass `beat.anchorTimestampMs` to `reconstructor.reconstruct()`, enabling monotonic accumulation ($t_k = t_{k-1} + \text{round}(rr_k)$) and drift resync strictly across real pauses.
2. **Incremental Flushing & Service Lifecycle**: In `TrackerService.java`, add periodic batch drain of `mLiveRrQueue` (e.g. in `updateSensorsAndSamples` or threshold size $\ge 100$). Gate `onRrBeat` with active tracking checks. On `RESUME_BY_USER`, query `getWorkoutRrRepository().getMaxBeatIndex(workoutId) + 1`. Sequence `HrvPostWorkoutRecomputer` after summary finalization and dispatch `WorkoutRepository.refreshWorkout(workoutId)`.
3. **Two-Pointer 5s Grid Recomputation**: In `HrvPostWorkoutRecomputer.kt`, sort beats monotonically, maintain two advancing pointers for `[t - 60s, t + 60s]` and `[t - 30s, t + 30s]`, and compute DFA $\alpha_1$ and rMSSD on a 5s grid, step-latching values to intermediate 1-Hz samples.
4. **Session SDNN & Schema Upgrade**: Calculate unbiased session SDNN across non-artifact beats ($N \ge 2$, artifact rate $\le 5\%$) and persist to `WorkoutSummaries` via `updateExtremaValue` (`SensorType.HRV_SDNN` or session extrema). Bump `WorkoutRrDbHelper.DB_VERSION` to 2 with `UNIQUE(workout_id, beat_index)` index.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Lossless beat preservation: no valid beat interval discarded during import or tracking.
  2. Crash tolerance: in-progress workouts retain batched RR intervals on abnormal process exit.
  3. Strict schema backward compatibility and database downgrade prevention (Rule 62, Rule 64).
* **Risk Rating**: **MEDIUM**. Touches SQLite schema (`WorkoutRr.db`), background service lifecycle (`TrackerService.java`), and FIT parsing, but fully bounded with zero public API breaks and protected by JVM unit/Robolectric test suites.
