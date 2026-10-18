# Stage 5 Walkthrough & Verification Report: ATT-3927

## 1. Executive Summary
- **Ticket**: `ATT-3927` — Fix TCX replay speed inflation and timeline progression for variable-interval GPS tracks
- **Feature Branch**: `feature/ATT-3927` (merged into `sprint/2026-42.8`)
- **Requirements Verified**: [`REQ-TOOL-006`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) (`Verified`)
- **Tests Verified**: [`TST-TOOL-006`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) (`Verified`)

---

## 2. Root Cause & Architectural Solution Summary

### 2.1 Root Cause
In [`tools/replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/replay_workout.py), TCX files without embedded `<ns3:Speed>` extensions previously fell back to computing `speed_mps` as raw point-to-point distance `dist_step` without dividing by the elapsed time interval `dt = t_curr - t_prev`, implicitly assuming `dt = 1.0s`. On multi-second GPS intervals (`dt > 1.0s`), this inflated speed by a factor of `dt`. Furthermore, `ReplayController._replay_loop()` advanced the timeline by index (`idx += int(step)`) every wall-clock second (`1.0 / speed_factor`), compressing multi-second TCX tracks in time and inflating cumulative BLE CSC wheel revolutions.

### 2.2 Solution Implemented (`REQ-TOOL-006`)
1. **Timestamp-Aware Fallback Velocity & Distance-Rate Priority (`parse_tcx`)**:
   - Parsed ISO-8601 `<Time>` tags (both `Z` and explicit timezone offsets) via `_parse_iso_time()` to derive `dt = (curr_dt - prev_dt).total_seconds()`.
   - When `speed_mps == 0.0` and `dist_step > 0.0`:
     - If `dt is not None and dt >= 0.001`: computed `derived_mps = dist_step / dt` with finite math guards (`math.isfinite(derived_mps)` and `0.0 <= derived_mps <= 100.0`).
     - If `dt is not None and dt < 0.001`: suppressed zero/negative/sub-millisecond intervals (`speed_mps = 0.0`) to prevent division-by-zero or infinity spikes.
     - If timestamps are absent (`dt is None`): preserved legacy `dist_step` (`1.0s`) fallback.
   - Added `resolve_speed_mps(explicit_speed_mps, dist_step, dt)` so that when `<DistanceMeters>` is present (`dist_step > 0.0`) and `dt >= 0.001`, cumulative distance rate (`dist_step / dt`) is prioritized over noisy instantaneous `<ns3:Speed>` tags (`> 15%` divergence) to guarantee distance parity between replay and import.
2. **Timestamp-Driven Timeline Progression (`find_trackpoint_index_for_time` & `ReplayController._replay_loop`)**:
   - Added `find_trackpoint_index_for_time(points, target_time_sec, start_idx)` and updated `_replay_loop()` to advance `target_time_sec` by `1.0 * speed_factor` per wall-clock second rather than incrementing `idx` by 1 per second.
3. **Replay Session Epoch (`TrackPointInjector.reset_epoch` & `inject`)**:
   - Added `reset_epoch(initial_time_sec)` to `TrackPointInjector` and invoked it at the start of `_replay_loop()` and on interactive user `seek()` operations.
   - Computed `dt_sim = max(0.0, float(pt.time_sec - self._last_time_sec))` in `TrackPointInjector.inject(pt)` and passed `elapsed_sec=dt_sim` into `virtual_ble.update_metrics(..., elapsed_sec=dt_sim)` when `dt_sim > 0.0` (falling back to `1.0` when `dt_sim == 0.0`).
   - Updated `VirtualBleBroadcaster.update_metrics(..., elapsed_sec=1.0)` so cumulative wheel revolutions advance by `(speed_mps * elapsed_sec) / wheel_circumference_m`, Event Time advances by `int(1024 * elapsed_sec)`, and Crank Revolutions advance by `(cadence_rpm / 60.0) * elapsed_sec`.

---

## 3. Clean-Room Verification Results

### 3.1 Python Unit Test Suite (`TST-TOOL-006`)
Executed `python3 -m unittest tools/test_replay_workout.py`:
- **Total Tests**: 37 (including 10 new tests in `TestVariableIntervalSpeedAndTimeline`)
- **Result**: `OK` (100% pass rate, 0 failures, 0 errors)

### 3.2 Full Android Unit Test Regression Suite
Executed `./gradlew testDebugUnitTest`:
- **Result**: `BUILD SUCCESSFUL` (100% pass rate across all project unit tests)
