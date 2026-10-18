# Stage 3 Architecture & Implementation Plan: ATT-3927 — Fix TCX Replay Speed Inflation and Timeline Progression for Variable-Interval GPS Tracks in `tools/replay_workout.py`

## 1. Architectural Overview (SWE.2)

This plan implements `REQ-TOOL-006` (`ATT-3927`) entirely within the external developer replay tool `tools/replay_workout.py` and its unit test suite `tools/test_replay_workout.py`, maintaining 100% production code isolation (`app/src/main` is not modified).

| Component | Target File | Responsibility |
| :--- | :--- | :--- |
| **`TcxParser`** | `tools/replay_workout.py` | Parse all `<Trackpoint>` elements with valid `<Time>` when at least one `<Position>` exists in the file; linearly interpolate missing `lat`, `lon`, and `ele` across interior `<Position>` gaps by `time_sec`; compute safe fallback `speed_mps = step_d / dt` when `<Speed>` is absent (`dt >= 0.001` and `math.isfinite`). |
| **`ReplayEngine`** | `tools/replay_workout.py` | Track `elapsed_time_sec: float`; advance `elapsed_time_sec += dt_sec * speed_multiplier` in `step(dt_sec=1.0)` and resolve `current_index` by `time_sec`; synchronize `elapsed_time_sec` in `seek_ratio()`, `seek_time()`, and `stop()`; clamp to `len(points) - 1` and transition to `PAUSED`. |
| **`AdbGpsInjector`** | `tools/replay_workout.py` | Maintain Replay Session Epoch `base_epoch_ms: int = int(time.time() * 1000)` and `reset_epoch(time_sec=0.0)`; stamp `--time <timestamp_ms>` using `base_epoch_ms + int(round(time_sec * 1000.0))` in `inject_location(..., time_sec=pt.time_sec)`. |
| **GUI / CLI Callers** | `tools/replay_workout.py` | Pass `time_sec=pt.time_sec` to `gps_injector.inject_location(...)` in `ReplayGuiWindow._emit_telemetry` and `run_headless_cli`, call `gps_injector.reset_epoch(...)` on stop/seek/file-load, and fix `run_headless_cli` sleep interval to `1.0s` so `speed_multiplier` is not applied twice. |
| **Unit Test Suite** | `tools/test_replay_workout.py` | Implement `TST-TOOL-006` covering variable-interval TCX interpolation, fallback speed with `dt == 0` guard, time-based `ReplayEngine.step()`, and Replay Session Epoch ADB GPS timestamps. |

---

## 2. Atomic Step Breakdown

### Step 1: Variable-Interval TCX Trackpoint Preservation, Coordinate Interpolation & Safe Fallback Speed (`REQ-TOOL-006.1`)
- **Target File**: `tools/replay_workout.py` (`TcxParser.parse_file`)
- **Changes**:
  1. During raw `<Trackpoint>` extraction, parse `<Time>` first (`dt_obj = cls.parse_iso_time(time_str)`). Extract `lat: Optional[float]` and `lon: Optional[float]` if `<Position>` is present (otherwise `None`), and extract `ele: Optional[float]`, `dist_m: Optional[float]`, `hr_bpm`, `cadence_rpm`, `power_watts`, and `has_speed` / `speed_mps`.
  2. Verify that at least one parsed raw entry has valid `(lat, lon)` (`any(r["lat"] is not None and r["lon"] is not None for r in raw_records)`). If zero GPS positions exist in the entire file, raise `ValueError` as before.
  3. Compute `time_sec = max(0.0, (dt_obj - t0).total_seconds())` for all raw records.
  4. Resolve missing `(lat, lon)` (and missing `ele`) across raw records:
     - Leading records before the first known GPS fix copy the first known `(lat, lon)`.
     - Trailing records after the last known GPS fix copy the last known `(lat, lon)`.
     - Interior records between known GPS fixes $A = (t_a, \text{lat}_a, \text{lon}_a)$ and $B = (t_b, \text{lat}_b, \text{lon}_b)$ linearly interpolate by time ratio $\alpha = (t - t_a) / (t_b - t_a)$ when $t_b - t_a \ge 0.001$ (or copy $A$ when $t_b - t_a < 0.001$).
     - Similarly, if `<AltitudeMeters>` is missing on interior points between known altitudes, linearly interpolate `ele` by $\alpha$ (or carry forward/backward).
  5. Compute cumulative `distance_m` and `step_d`:
     - When `raw_dist` is missing on an interpolated point or does not advance across an interpolated segment while `haversine_m` > 0, accumulate `step_d = haversine_m(prev_lat, prev_lon, lat, lon)`.
  6. Compute fallback `speed_mps` when `<Speed>` was not present in `<Extensions>` (or `speed_mps <= 0.0`):
     - For $i \ge 1$, let $\Delta t = \text{points}[i].\text{time\_sec} - \text{points}[i-1].\text{time\_sec}$ and $\Delta d = \max(0.0, \text{points}[i].\text{distance\_m} - \text{points}[i-1].\text{distance\_m})$.
     - If $\Delta t \ge 0.001$: `raw_speed = delta_d / dt`; set `points[i].speed_mps = raw_speed if math.isfinite(raw_speed) else 0.0`.
     - If $\Delta t < 0.001$: set `points[i].speed_mps = 0.0`.
     - If `len(points) > 1` and `points[0].speed_mps <= 0.0`: initialize `points[0].speed_mps` to the first subsequent finite non-zero `speed_mps` (or `0.0`).
- **Verification**: `python3 -m unittest tools/test_replay_workout.py`

### Step 2: Elapsed-Time Playback Stepping in `ReplayEngine` & CLI Pacing Fix (`REQ-TOOL-006.2`)
- **Target File**: `tools/replay_workout.py` (`ReplayEngine`, `run_headless_cli`)
- **Changes**:
  1. In `ReplayEngine.__init__`: initialize `self.elapsed_time_sec: float = self.points[0].time_sec if self.points else 0.0`.
  2. In `ReplayEngine.stop()`: reset `self.current_index = 0` and `self.elapsed_time_sec = self.points[0].time_sec if self.points else 0.0`.
  3. In `ReplayEngine.seek_ratio(ratio)` and `ReplayEngine.seek_time(target_sec)`: synchronize `self.elapsed_time_sec = self.points[self.current_index].time_sec` (or clamped `target_sec`).
  4. In `ReplayEngine.step(dt_sec: float = 1.0) -> TrackPoint`:
     - When `self.state != self.STATE_PLAYING`: return `self.current_point`.
     - Advance `self.elapsed_time_sec += dt_sec * self.speed_multiplier`.
     - Advance `idx = self.current_index` while `idx + 1 < len(self.points)` and `self.points[idx + 1].time_sec <= self.elapsed_time_sec + 1e-6`.
     - Set `self.current_index = idx`.
     - If `self.current_index >= len(self.points) - 1` and `self.elapsed_time_sec >= self.points[-1].time_sec`: clamp `self.current_index = len(self.points) - 1`, set `self.elapsed_time_sec = self.points[-1].time_sec`, and set `self.state = self.STATE_PAUSED`.
  5. In `run_headless_cli`: sleep at fixed $1.0\text{ s}$ tick (`sleep_t = max(0.01, 1.0 - elapsed)`) so `speed_multiplier` is applied only once inside `engine.step()`.
- **Verification**: `python3 -m unittest tools/test_replay_workout.py`

### Step 3: Replay Session Epoch Workout-Relative ADB GPS Timestamps (`REQ-TOOL-006.3`)
- **Target File**: `tools/replay_workout.py` (`AdbGpsInjector`, `ReplayGuiWindow`, `run_headless_cli`)
- **Changes**:
  1. In `AdbGpsInjector.__init__`: initialize `self.base_epoch_ms: int = int(time.time() * 1000)`.
  2. Add `AdbGpsInjector.reset_epoch(self, time_sec: float = 0.0) -> None`: sets `self.base_epoch_ms = int(time.time() * 1000) - int(round(time_sec * 1000.0))`.
  3. Update `AdbGpsInjector.inject_location(self, lat: float, lon: float, ele: float = 0.0, speed_mps: float = 0.0, bearing_deg: float = 0.0, time_sec: Optional[float] = None) -> None`:
     - Compute `timestamp_ms = (self.base_epoch_ms + int(round(time_sec * 1000.0))) if time_sec is not None else int(time.time() * 1000)`.
     - Enqueue `(lat, lon, timestamp_ms)` on `self._coord_queue`.
  4. Update `AdbGpsInjector._worker_loop`: unpack `(lat, lon, timestamp_ms)` from `self._coord_queue` and pass `--time {timestamp_ms}` to `cmd location providers set-test-provider-location`.
  5. Update `ReplayGuiWindow._emit_telemetry` and `run_headless_cli` to pass `time_sec=pt.time_sec` to `gps_injector.inject_location(...)` and call `gps_injector.reset_epoch(pt.time_sec)` on stop/seek/file-load.
- **Verification**: `python3 -m unittest tools/test_replay_workout.py`

### Step 4: Unit Test Suite Expansion (`TST-TOOL-006`)
- **Target File**: `tools/test_replay_workout.py`
- **Changes**:
  1. Add tests in `TestTcxParser` for variable-interval `<Trackpoint>` elements without `<Position>`, linear coordinate/altitude interpolation by `time_sec`, fallback `speed_mps = step_d / dt` derivation when `<Speed>` is omitted, and `dt == 0` duplicate timestamp safety (`speed_mps == 0.0`, finite).
  2. Update and expand `TestReplayEngine` to verify elapsed-time stepping across both $1\text{ Hz}$ and $5\text{ s}$ variable-interval tracks, speed multiplier scaling ($2\times, 5\times$), `seek_ratio`/`seek_time`/`stop` `elapsed_time_sec` synchronization, and final-point clamping / `PAUSED` transition at `len(points) - 1`.
  3. Add tests in `TestAdbGpsInjector` verifying `inject_location(..., time_sec=t)` computes `base_epoch_ms + int(round(t * 1000.0))`, preserves exact $\Delta t_{\text{workout}} \times 1000\text{ ms}$ across multipliers, and `reset_epoch(time_sec)` anchors `base_epoch_ms` cleanly.
- **Verification**: `python3 -m unittest tools/test_replay_workout.py`

---

## 3. Invariants & Safety Checklist
- **Production Code Isolation**: Zero files under `app/src/main` are modified.
- **Backward Compatibility**: All existing CLI flags (`--no-ble`, `--no-gps`, `--headless`, `--speed`, `--hr-battery`, `--power-battery`, `--kill-*-at`, `--revive-*-at`), BlueZ GATT services, and Android 14+ `cmd location` syntax remain 100% preserved.
- **UI Consistency (Rule 23)**: Not applicable to Android Jetpack Compose UI; PyQt6 desktop dashboard styling remains unchanged.
