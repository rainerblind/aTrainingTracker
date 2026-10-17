# Stage 2 Requirement & Test Specification: ATT-3927 — Fix TCX Replay Speed Inflation and Timeline Progression for Variable-Interval GPS Tracks in `tools/replay_workout.py`

## 1. Requirement Specification (`REQ-TOOL-006`)

### `REQ-TOOL-006`: Variable-Interval TCX Telemetry Interpolation, Time-Based Playback Stepping & Workout-Relative ADB GPS Timestamp Synchronization in `tools/replay_workout.py`

The TCX workout replay tool (`tools/replay_workout.py`) SHALL preserve $1\text{ Hz}$ physiological telemetry across variable-interval GPS `<Trackpoint>` entries, compute fallback speed from distance/time deltas with zero-division / `NaN` / `Inf` guards when `<Speed>` extensions are absent, advance `ReplayEngine` by workout elapsed time (`time_sec`) rather than raw array index, and stamp ADB mock GPS fixes with Replay Session Epoch workout-relative timestamps so Android `SpeedAndLocationDevice.java` calculates accurate speed regardless of GPS recording interval or replay multiplier (ATT-3927, amending `REQ-TOOL-002`):

1. **Variable-Interval TCX Trackpoint Preservation, Linear Coordinate Interpolation & Safe Fallback Speed (`REQ-TOOL-006.1`)**:
   - In `TcxParser.parse_file()`, whenever a `.tcx` file contains at least one valid `<Position>` (`<LatitudeDegrees>`, `<LongitudeDegrees>`), the parser SHALL retain intermediate `<Trackpoint>` elements that have a valid `<Time>` even when `<Position>` is omitted on those intermediate points, preserving their $1\text{ Hz}$ `hr_bpm`, `cadence_rpm`, `power_watts`, `ele`, and `distance_m` telemetry.
   - For `<Trackpoint>` elements missing `<Position>`, `TcxParser.parse_file()` SHALL linearly interpolate `lat` and `lon` (and `ele` when `<AltitudeMeters>` is missing) with respect to workout elapsed time `time_sec` between the bounding known GPS fixes (and carry the first/last known fix at leading/trailing boundaries).
   - After computing cumulative `distance_m` and `step_d`, for every point $i \ge 1$ where `<Speed>` was not provided in `<Extensions>` (or `speed_mps <= 0.0`):
     - If $\Delta t = \text{points}[i].\text{time\_sec} - \text{points}[i-1].\text{time\_sec} \ge 0.001$, `TcxParser` SHALL compute `raw_speed = step_d / dt` and set `points[i].speed_mps = raw_speed if math.isfinite(raw_speed) else 0.0`.
     - If $\Delta t < 0.001$ (duplicate or malformed timestamps), `points[i].speed_mps` SHALL be set to `0.0` (preventing `ZeroDivisionError`, `NaN`, or `Inf`).
     - If `points[0].speed_mps <= 0.0` and `len(points) > 1`, `points[0].speed_mps` SHALL be initialized to the first subsequent finite non-zero `speed_mps` (or `0.0`).
2. **Elapsed-Time Playback Stepping in `ReplayEngine` (`REQ-TOOL-006.2`)**:
   - `ReplayEngine` SHALL maintain `self.elapsed_time_sec: float` initialized to `self.points[0].time_sec` (or `0.0`) and synchronized with `self.points[self.current_index].time_sec` on `seek_ratio()`, `seek_time()`, and `stop()`.
   - `ReplayEngine.step(dt_sec: float = 1.0)` SHALL advance `self.elapsed_time_sec += dt_sec * self.speed_multiplier` and advance `self.current_index` to the highest index $k \le \text{len(points)} - 1$ where `self.points[k].time_sec <= self.elapsed_time_sec`, cleanly clamping to `len(self.points) - 1` and transitioning to `PAUSED` at the final point without index-out-of-bounds errors.
   - In `run_headless_cli`, the loop SHALL tick at a fixed $1.0\text{ s}$ base interval (`sleep_t = max(0.01, 1.0 - elapsed)`) so `speed_multiplier` is applied once in `engine.step()` rather than squared.
3. **Replay Session Epoch Workout-Relative ADB GPS Timestamps in `AdbGpsInjector` (`REQ-TOOL-006.3`)**:
   - `AdbGpsInjector` SHALL maintain a Replay Session Epoch `self.base_epoch_ms: int = int(time.time() * 1000)` and expose `reset_epoch(self, time_sec: float = 0.0)` which sets `self.base_epoch_ms = int(time.time() * 1000) - int(round(time_sec * 1000.0))` (invoked on `stop()`, `load_file()`, or explicit seek so `base_epoch_ms` maintains the exact relative `time_sec` offset from the new current position without artificial `+1000ms` stuttering).
   - `AdbGpsInjector.inject_location(self, lat: float, lon: float, ele: float = 0.0, speed_mps: float = 0.0, bearing_deg: float = 0.0, time_sec: Optional[float] = None)` SHALL compute `timestamp_ms = self.base_epoch_ms + int(round(time_sec * 1000.0))` when `time_sec is not None` (falling back to `int(time.time() * 1000)` when `time_sec is None`), preserving exact $\Delta t_{\text{workout}} = (t_i - t_{i-1}) \times 1000\text{ ms}$ across all playback multipliers.
   - Both `ReplayGuiWindow._emit_telemetry` and `run_headless_cli` SHALL pass `time_sec=pt.time_sec` to `gps_injector.inject_location(...)`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-TOOL-006`, amending `REQ-TOOL-002` (*Synchronized TCX Workout Replay Tool with PyQt6 GUI Dashboard, BLE Cycling Power, Cadence, Heart Rate, and ADB Mock GPS Simulation*, `ATT-2969`) Clauses 1, 2, and 4. Targets `tools/replay_workout.py` and `tools/test_replay_workout.py`.
2. **Historical Origin & Commit Trace**:
   - Introduced in Sprint 2026-41.6 (`ATT-2969`, commit `1523f683`) and extended in Sprint 2026-42.0 (`ATT-3178`, `ATT-3179`).
3. **Root Reason for Existing Formulation (Why was this fence built?)**:
   - `tools/replay_workout.py` was originally implemented against synthetic $1\text{ Hz}$ TCX files (`tools/sample_workout.tcx`) where every `<Trackpoint>` had `<Position>` and `<Speed>` at uniform $1.0\text{ s}$ intervals.
4. **Preservation of Core Invariants (Why is it safe to modify now?)**:
   - Interpolating missing `<Position>` coordinates by `time_sec`, deriving fallback speed from `step_d / dt` (guarded by `dt >= 0.001` and `math.isfinite`), advancing `ReplayEngine` by `elapsed_time_sec`, and stamping ADB `--time` with `base_epoch_ms + int(round(time_sec * 1000))` eliminates speed inflation and timeline jumps on variable-interval TCX files while behaving identically on uniform $1\text{ Hz}$ TCX files and keeping `app/src/main` 100% untouched.

---

## 3. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Variable-Interval TCX Interpolation & Safe Fallback Speed (`REQ-TOOL-006.1`)**
  - *Given* a variable-interval `.tcx` file where `<Trackpoint>` elements appear at $t = 0, 1, 2, 3, 4, 5\text{ s}$ with HR/Power at every second, `<Position>` only at $t = 0\text{ s}$ and $t = 5\text{ s}$, and no `<Speed>` extension,
  - *When* parsed by `TcxParser.parse_file()`,
  - *Then* all 6 trackpoints ($t = 0..5\text{ s}$) are retained with their $1\text{ Hz}$ HR/Power values, intermediate `lat`/`lon` coordinates at $t = 1..4\text{ s}$ are linearly interpolated by time, `speed_mps` is derived from `step_d / dt > 0`, and duplicate timestamps (`dt < 0.001`) safely yield `speed_mps = 0.0` without `ZeroDivisionError`, `NaN`, or `Inf`.

- **Scenario 2: Elapsed-Time Playback Stepping & Final Tick Termination (`REQ-TOOL-006.2`)**
  - *Given* `ReplayEngine` loaded with trackpoints at $t = 0, 5, 10, 15\text{ s}$ at $1\times$ speed,
  - *When* `engine.step()` ($1.0\text{ s}$) is invoked 4 times ($t_{\text{elapsed}} = 4.0\text{ s}$), 5 times ($t_{\text{elapsed}} = 5.0\text{ s}$), and up to $t_{\text{elapsed}} \ge 15.0\text{ s}$,
  - *Then* `current_index` remains `0` at $4.0\text{ s}$, advances to `1` ($t = 5.0\text{ s}$) on the 5th step, and cleanly clamps to `len(points) - 1` (`3`) and transitions to `PAUSED` without index-out-of-bounds errors.

- **Scenario 3: Replay Session Epoch Workout-Relative ADB GPS Timestamps (`REQ-TOOL-006.3`)**
  - *Given* `AdbGpsInjector.inject_location(..., time_sec=t)` called at $t = 0.0\text{ s}$ and $t = 5.0\text{ s}$ regardless of replay speed multiplier ($1\times, 2\times, 5\times, 10\times$),
  - *When* inspecting the queued/executed ADB timestamps,
  - *Then* `timestamp_ms(5.0) - timestamp_ms(0.0) == 5000`, and calling `reset_epoch(time_sec)` resets `base_epoch_ms` to maintain the exact relative `time_sec` offset from the new current position.

---

## 4. Test Specification (`TST-TOOL-006`) & Traceability Matrix

| Test ID | Requirement ID | Test Class / Suite | Verification Scope |
| :--- | :--- | :--- | :--- |
| **TST-TOOL-006.1** | `REQ-TOOL-006.1` | `TestTcxParser` (`tools/test_replay_workout.py`) | Verify `TcxParser.parse_file()` retains $1\text{ Hz}$ trackpoints lacking `<Position>`, linearly interpolates `lat`/`lon`/`ele` across gaps by `time_sec`, computes fallback `speed_mps = step_d / dt` when `<Speed>` is absent, and safely sets `speed_mps = 0.0` when `dt < 0.001` (duplicate timestamps) without `ZeroDivisionError`/`NaN`/`Inf`. |
| **TST-TOOL-006.2** | `REQ-TOOL-006.2` | `TestReplayEngine` (`tools/test_replay_workout.py`) | Verify `ReplayEngine.step()` advances by `elapsed_time_sec += dt_sec * speed_multiplier` across both $1\text{ Hz}$ and variable-interval trackpoints, transitions to `PAUSED` at `len(points) - 1` without index-out-of-bounds errors, and synchronizes `elapsed_time_sec` on `seek_ratio()`, `seek_time()`, and `stop()`. |
| **TST-TOOL-006.3** | `REQ-TOOL-006.3` | `TestAdbGpsInjector` (`tools/test_replay_workout.py`) | Verify `AdbGpsInjector.inject_location(..., time_sec)` stamps `base_epoch_ms + int(round(time_sec * 1000))`, preserves exact workout $\Delta t$ across multipliers, and `reset_epoch(time_sec)` maintains relative `time_sec` offset on reset/seek. |

## 5. 9-Language Localization Impact
- `tools/replay_workout.py` is an external Python developer utility and introduces zero changes to Android `app/src/main/res/values*/strings.xml`; all 9 application locales remain 100% in parity.
