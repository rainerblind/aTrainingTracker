# Stage 1 Analysis: Post-Workout ERTS Pause-Gap Distance & EKF/RTS Decoupling (`ATT-9242`)

- **Ticket**: `ATT-9242` (`2 SP`, `tier-a`, Bug `severity-major`) | **Epic**: `ATT-166` | **Target Release**: `V5.0.0`
- **Reserved IDs**: `REQ-FIL-045` (`.1`–`.3`) / `TST-FIL-045` (`.1`–`.3`) | **Localization Parity**: `N/A - 0 string changes`

## 1. Problem Statement & Grounded Root Causes (`Rule 8.7`)
Pixel 10 ride `Zum Bäcker #59` (`workoutID = 2419`, `2026-11-20_055902`, `391 s` active / `570 s` total) paused cleanly in live tracking (`DISTANCE_m_att_fused = 1,948.17 m`, `GPS = 1,952.28 m`, `Haversine = 1,925.28 m`), yet post-workout ERTS inflated distance to **`2,413.42 m`** (`+465 m` / `+24%`) and `speedAverage_mps` to **`6.17 m/s`** (`22.2 km/h`) while slipping past `isOdometrySane()` (`1.24x < 3.0x`):
1. **Unbounded Pause-Gap Distance Integration (`ExtendedRtsOdometrySmoother.kt:315-320`)**: `smoothSnapshots()` computes `dt = (snapshots[k].timestampMs - snapshots[k - 1].timestampMs) / 1000.0` and `deltaDist = sanitizeNonNegativeFinite(xk[2] * dt)` with no pause-gap guard, multiplying resume speed across the entire pause (`Row 218`: `3.84 m/s * 134 s = +529.5 m`; `Row 199`: `0.88 m/s * 17 s = +15.9 m`).
2. **Cross-Pause Forward EKF, Backward RTS & Heading-Origin Assumptions (`ExtendedRtsOdometrySmoother.kt:238-306, 396-556, 674-708`)**: `smoothSamples()` runs a single `ekf.predict(dtSec = 134.0)` and bridges backward RTS across pause boundaries; furthermore `resolveInitialHeadingRad()` (`:692-696`) assumed `segment[firstValidIdx]` sits at global ENU `(0, 0)`, so Segment 2+ must subtract `(firstEast, firstNorth)` to avoid 180° heading inversion on out-and-back rides.
3. **Missing Short Auto-Pause (`<= 5 s`) Detection & Timing Sync (`ErtsPostWorkoutRecomputer.kt:238, 334-382, 801, 916`)**: `readWorkoutSamples()` ignores `SensorType.TIME_ACTIVE` (`ClockDevice.java:107`, `respectPause = true`) vs `SensorType.TIME_TOTAL` (`:109`, `respectPause = false`), missing `3–5 s` auto-pauses (`4 s` pause: `d(TIME_TOTAL) - d(TIME_ACTIVE) = 4 s > 2 s`) and leaving `oneHzTs` unsynchronized across short pauses; `computeAuthoritativeAverageSpeedMps()` also omits `pauseGapMask` in both `updateWorkoutSummaries()` (`:801`) and `updateExtrema()` (`:916`).
4. **Altitude/Slope Cross-Pause Integration, 1-Sample Tail & Baro Reset-Step Coupling (`ExtendedRtsAltitudeSlopeSmoother.kt:127-142, 185, 196, 250-267, 376-456`; `ErtsPostWorkoutRecomputer.kt:389-493`)**: Across a `134 s` pause at `3.84 m/s`, `deltaDist = 514 m` injects `x[0] += x[1] * 514`, bridges backward RTS and hysteresis, divides `vertSpeedMps` across `dt_pause`, smears `BarometricAltitudeFilter` across `<= 10 s` pauses, and leaks `detectAndCompensateBaroResetSteps()` `cumulativeShift` / `preVals`/`postVals` across pause boundaries.

## 2. Chesterton's Fence & Preserved Invariants
- **`REQ-FIL-017.3` / `REQ-FIL-019.1`–`.4`**: Strict canonical column whitelist (`ALLOWED_CANONICAL_COLUMNS`), untouched device-attributed `" ("` columns, Null-Island/leading-null SQLite `NULL` preservation, and `1:1` output cardinality (`smoothed.size == rawRows.size`).
- **`REQ-FIL-025.3`–`.4` / `REQ-FIL-032.2`**: `Laps.db` / `DISTANCE_m_LAP` / `WorkoutSummaries` / `Extrema(AVG)` parity, `computeAuthoritativeAverageSpeedMps` precedence (`totalDistance / TIME_ACTIVE_s`).
- **`REQ-FIL-027.1` / `REQ-FIL-032.1`**: Beat-immune `{0, 2}` `TIME_TOTAL` reconstruction (`583_000 ms` across 78 pairs) and `<= 6 s` SQLite batch-flush stall immunity. **Intentional Test Alignment**: `ErtsPostWorkoutRecomputerTest.testLeadingNullTimeTotal_batchFlushStall6s_andDoubleWallClockPause_tstFil027_1` (`:1264, :1272`) used `deltaDist > 100 m` across synthetic 30 s/25 s pauses as a timestamp-preservation proxy — that proxy IS the `ATT-9242` bug and will be updated to assert direct `reconstructTimestamps()` `31 s`/`26 s` preservation + `0.0 m` pause distance.
- **`REQ-FIL-027.3` / `REQ-FIL-032.3` / `REQ-FIL-036.3`**: Continuous-recording indoor-stop ZUPT pinning (`0.00 m` Haversine drift, `dt = 1 s`) and post-unpin speed envelope remain unchanged.
- **`REQ-FIL-033.1`–`.3` / `REQ-FIL-040.4` / `REQ-STB-025.4`**: Delayed DEM anchor convergence (0 pauses -> 1 segment), barometer-free DEM-only workouts, low-speed braking protection, and `<= 5 s` non-pause crash-recovery barometer reset compensation remain intact.

## 3. Change-Request Impact Analysis
1. **Requirements & Specifications**: Adds `REQ-FIL-045` / `TST-FIL-045` (`[Planned]`) in `req_tracking_cockpit_part3.md` and `tst_tracking_cockpit_part2.md`; refines pause semantics of `REQ-FIL-017.1`–`.3`, `REQ-FIL-027.1`, and `REQ-FIL-032.1` without relaxing any existing safety invariant.
2. **Architecture & Interfaces**: Pure Kotlin additive changes across 3 files (`ErtsPostWorkoutRecomputer.kt` `1,123` LoC, `ExtendedRtsOdometrySmoother.kt` `920` LoC, `ExtendedRtsAltitudeSlopeSmoother.kt` `656` LoC — all `<= 1,500` LoC). Trailing default args and `@JvmOverloads` preserve 100% source/binary compatibility.
3. **Verification & Tests**: Targeted JVM/Robolectric tests in `ErtsPostWorkoutRecomputerTest`, `ExtendedRtsOdometrySmootherTest`, `ExtendedRtsAltitudeSlopeSmootherTest`, plus adjacent ERTS/Laps/Contract regression suites; zero UI/Compose/string changes.
4. **Release, Data & Compatibility**: Zero SQLite schema changes; works on modern DBs (with `TIME_ACTIVE`) and legacy DBs (`dt > 5 s` fallback); `Target Release: V5.0.0`.

## 4. Independent Peer Review Sign-Off
| Stage / Gate | Reviewer | Status | Notes |
| :--- | :--- | :--- | :--- |
| Gate 3 Rev 1 (`ATT-9679`) | `--as agent2` | `REVISION` | 4 pre-impl catches: Segment 2+ heading offset, short-pause `ts`/null-`TIME_ACTIVE` reset, altitude `n==1`/DEM bias handoff, per-segment baro reset & `updateExtrema` parity |
| Gate 3 Rev 2 (`ATT-9679`) | `--as agent2` | Pending | All 4 findings incorporated under `Rule 4.1` Code Lock (`0` `app/src/` edits) |
