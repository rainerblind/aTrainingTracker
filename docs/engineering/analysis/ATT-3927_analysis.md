# Stage 1 Analysis: ATT-3927 — Fix TCX Replay Speed Inflation and Timeline Progression for Variable-Interval GPS Tracks in `tools/replay_workout.py`

## 1. Executive Summary & Problem Statement

During Sprint Review 2026-42.7 verification on the Google Pixel 10 using a real-world Garmin Edge / Strava TCX recording (`2013-05-19_0957.tcx`), replaying the workout via `tools/replay_workout.py` exhibited two severe anomalies:
1. **Desktop Replay Dashboard Speed (`0.0 km/h`)**: Whenever `<Extensions>/<TPX>/<Speed>` is absent from a TCX file (common in older Garmin Edge and Strava exports), `TcxParser` leaves `TrackPoint.speed_mps = 0.0`, causing the PyQt6 replay dashboard to display `0.0 km/h` even while distance and GPS coordinates advance.
2. **Phone GPS Speed Inflation ($2\times\text{–}7\times$ at $1\times$ playback, and compounded by the replay speed multiplier at $2\times/5\times/10\times$)**: On the connected Android device, `SpeedAndLocationDevice.java` computed inflated speeds up to $70\text{–}100+\text{ km/h}$ on flat terrain where actual riding speed was $\sim 22\text{ km/h}$, and timeline progression jumped erratically across variable-interval trackpoints.

## 2. Forensic Root Cause Analysis (RCA)

Forensic inspection of `tools/replay_workout.py` (lines 136–305, 1026–1133, 1212–1288, and 1882–1955) and `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java` (lines 286–325) identified three compounding root causes:

### Root Cause 1: `TcxParser.parse_file` Discards Non-`<Position>` Trackpoints and Lacks Fallback Speed Derivation (`tools/replay_workout.py:175-282`)
* In real-world variable-interval TCX recordings (such as `2013-05-19_0957.tcx`), heart rate, cadence, and power are recorded at $1\text{ Hz}$, whereas `<Position>` (`<LatitudeDegrees>` / `<LongitudeDegrees>`) is only written when the GPS receiver records a smart-recording fix (every $2\text{ s}$ to $7\text{ s}$).
* At line 178 (`if pos_elem is None: continue`), `TcxParser.parse_file` unconditionally drops every `<Trackpoint>` lacking a `<Position>` child element:
  - **Loss of $1\text{ Hz}$ HR/Power/Cadence Telemetry**: Intermediate $1\text{ Hz}$ physiological samples between GPS fixes are discarded.
  - **Sparse Variable-Interval Point Array**: The resulting `points` list has irregular time steps ($\Delta t \in [2\text{ s}, 7\text{ s}]$) instead of a continuous $1\text{ Hz}$ timeline.
* Furthermore, at lines 224–235, `speed_mps` is only populated if a `<Speed>` XML node exists in `<Extensions>`. When `<Speed>` is omitted from the TCX file, `speed_mps` remains `0.0` even though `step_d` and $\Delta t = t_i - t_{i-1}$ are known.

### Root Cause 2: `ReplayEngine.step()` Advances by Raw Array Index Instead of Workout Elapsed Time (`tools/replay_workout.py:1259-1273`)
* `ReplayEngine.step()` currently executes:
  ```python
  step_size = max(1, int(round(self.speed_multiplier)))
  self.current_index += step_size
  ```
* Because the GUI `QTimer` ticks once every $1.0\text{ s}$ (`1000 ms`) of wall-clock time, incrementing `current_index` by `1` per tick assumes every adjacent `TrackPoint` is spaced by exactly $\Delta t = 1.0\text{ s}$.
* When consecutive points have $\Delta t = 5.0\text{ s}$ (e.g., sparse GPS trackpoints or variable-interval recordings), advancing `current_index += 1` every $1.0\text{ s}$ of wall-clock time advances $5.0\text{ s}$ of workout time and distance per $1.0\text{ s}$ of real time ($5\times$ timeline compression and $5\times$ distance/speed inflation).
* Additionally, in `run_headless_cli` (`tools/replay_workout.py:1940`), the loop sleeps `1.0 / engine.speed_multiplier` while `engine.step()` ALSO multiplies the step by `engine.speed_multiplier`, resulting in a quadratic ($M^2$) speed-up in CLI mode.

### Root Cause 3: `AdbGpsInjector` Stamps `--time` with Wall-Clock `time.time()` Instead of Workout-Relative Timestamp (`tools/replay_workout.py:1079-1114`)
* On Android 14+ (API 34+), `cmd location providers set-test-provider-location` does not accept `--speed`, so `location.getSpeed()` arrives as `0.0` inside `SpeedAndLocationDevice.java:304`.
* Consequently, `SpeedAndLocationDevice.java:305-315` derives GPS speed from consecutive fix timestamps:
  ```java
  long dt_ms = location.getTime() - mPrevLocation.getTime();
  if (dt_ms > 0 && dt_ms < 30000) {
      double calcSpeed = delta_distance / (dt_ms / 1000.0);
      if (calcSpeed <= 70.0) {
          speed = calcSpeed;
      }
  }
  ```
* In `AdbGpsInjector._worker_loop` (`tools/replay_workout.py:1084`), every injected location is stamped with wall-clock time `now_ms = int(time.time() * 1000)` (~$1000\text{ ms}$ apart between $1\text{ Hz}$ ticks):
  - When a $5\text{ s}$ workout distance step ($\Delta d \approx 30\text{ m}$ at $21.6\text{ km/h}$) is injected $1.0\text{ s}$ later in wall-clock time, `dt_ms` is $1000\text{ ms}$, so Android computes $\text{calcSpeed} = 30\text{ m} / 1.0\text{ s} = 30\text{ m/s} = 108\text{ km/h}$!
  - Even on a $1\text{ Hz}$ file, when the user selects $2\times$, $5\times$, or $10\times$ replay speed, the distance between ticks scales by $M\times$ while wall-clock `dt_ms` remains $1000\text{ ms}$, inflating the phone's calculated speed by $M\times$!
* Stamping `--time` with a monotonic workout-relative timestamp `timestamp_ms = base_epoch_ms + int(round(pt.time_sec * 1000))` ensures that `location.getTime() - mPrevLocation.getTime()` always equals the exact workout elapsed time delta $\Delta t_{\text{workout}} = (t_i - t_{i-1}) \times 1000\text{ ms}$, regardless of wall-clock ADB latency or replay multiplier ($1\times, 2\times, 5\times, 10\times$).

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Amends `REQ-TOOL-002` (*Synchronized TCX Workout Replay Tool with PyQt6 GUI Dashboard, BLE Cycling Power, Cadence, Heart Rate, and ADB Mock GPS Simulation*, `ATT-2969`) and introduces `REQ-TOOL-006` (*Variable-Interval TCX Telemetry Interpolation, Time-Based Playback Stepping & Workout-Relative ADB GPS Timestamp Synchronization*).
2. **Historical Origin & Commit Trace**:
   - `REQ-TOOL-002` was introduced in Sprint 2026-41.6 (`ATT-2969`, commit `1523f683`) and extended by `REQ-TOOL-003` (`ATT-3178`) and `REQ-TOOL-004` (`ATT-3179`).
3. **Root Reason for Existing Formulation**:
   - `tools/replay_workout.py` was initially developed and verified against synthetic $1\text{ Hz}$ TCX files (`tools/sample_workout.tcx`) where every `<Trackpoint>` had a `<Position>` element, a `<Speed>` extension, and uniform $\Delta t = 1.0\text{ s}$ intervals. Under those idealized assumptions, skipping trackpoints without `<Position>`, stepping by array index (`current_index += speed_multiplier`), and using wall-clock `time.time() * 1000` at $1\times$ speed appeared to work.
4. **Preservation of Core Invariants**:
   - All existing `REQ-TOOL-002`, `REQ-TOOL-003`, and `REQ-TOOL-004` capabilities (BlueZ BLE GATT peripheral broadcasting for HR `0x180D`, Power `0x1818`, Battery `0x180F`, per-device kill/revive simulation, Android 14+ `cmd location` syntax compatibility, PyQt6 GUI & headless CLI modes, and zero production code contamination in `app/src/main`) MUST be strictly preserved.

## 4. Scope Bounding (`ATT-1250`)

### In-Scope Objectives
1. **`TcxParser.parse_file` Enhancement (`tools/replay_workout.py`)**:
   - Retain all `<Trackpoint>` entries that have valid timestamps once `<Position>` data exists in the workout, preserving $1\text{ Hz}$ HR, Cadence, Power, Distance, and Altitude telemetry on intermediate trackpoints that omit `<Position>`.
   - Linearly interpolate `lat`, `lon`, and `ele` (when missing) across interior `<Position>` gaps with respect to workout elapsed time `time_sec` (and clamp/carry at leading/trailing boundaries if needed).
   - Compute fallback `speed_mps = step_d / dt` whenever `<Speed>` extension is missing or `speed_mps <= 0.0` and `dt = points[i].time_sec - points[i-1].time_sec > 0.0` (and populate `points[0].speed_mps` from the first non-zero segment speed when `points[0].speed_mps <= 0.0`).
2. **`ReplayEngine` Elapsed-Time Stepping (`tools/replay_workout.py`)**:
   - Track continuous workout playback clock `elapsed_time_sec` in `ReplayEngine`, synchronized on `seek_ratio()`, `seek_time()`, and `stop()`.
   - Advance `elapsed_time_sec += dt_sec * self.speed_multiplier` in `ReplayEngine.step(dt_sec: float = 1.0)` and advance `self.current_index` to the latest trackpoint whose `time_sec <= self.elapsed_time_sec`, ensuring accurate real-time progression across both uniform $1\text{ Hz}$ and variable-interval TCX tracks.
   - Fix `run_headless_cli` tick pacing so `speed_multiplier` is not applied twice.
3. **`AdbGpsInjector` Monotonic Workout-Relative Timestamp Injection (`tools/replay_workout.py`)**:
   - Maintain a monotonic base timestamp `self.base_epoch_ms = int(time.time() * 1000)` on `AdbGpsInjector`.
   - Extend `inject_location(..., time_sec: Optional[float] = None)` to compute `timestamp_ms = self.base_epoch_ms + int(round(time_sec * 1000.0))` when `time_sec` is provided (and adjust `base_epoch_ms` if a backward seek occurs so injected Android location timestamps remain strictly monotonic while preserving forward $\Delta t$ steps).
   - Pass `time_sec=pt.time_sec` from both `ReplayGuiWindow._emit_telemetry` and `run_headless_cli`.
4. **Unit Test Coverage (`tools/test_replay_workout.py`)**:
   - Add unit tests verifying sparse/variable-interval TCX parsing (retaining $1\text{ Hz}$ HR/Power trackpoints without `<Position>`, linear coordinate interpolation, and fallback `speed_mps = step_d / dt` calculation).
   - Verify time-based `ReplayEngine.step()` across variable-interval trackpoints and speed multipliers.
   - Verify `AdbGpsInjector` workout-relative `--time <ms>` timestamp calculation and monotonicity across speed multipliers and seeks.

### Out-of-Scope
- Modifying any Android production code in `app/src/main/...` (`SpeedAndLocationDevice.java` remains untouched).
- Changing BLE GATT service schemas or PyQt6 layout visual styling.
