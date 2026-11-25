# Stage 2 Test Specification — ATT-9693: Datum-Invariant 3-State Altitude EKF & Walking-Speed ERTS Regularization

- **Ticket**: `ATT-9693` (`3 SP`, Bug, `tier-a`, Epic `ATT-166`) | **Target Release**: `V5.0.0`
- **Traceability**: `REQ-FIL-048` (`.1`–`.3`) `[Planned]` <-> `TST-FIL-048` (`.1`–`.3`) `[Planned]` (`SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-005`)
- **Localization Parity**: `N/A - 0 string changes` (pure-JVM odometry/smoothing algorithm fix)

## 1. Requirements & Test Case Traceability Matrix

| REQ ID | TST ID | Verification Scope | Primary Test Suites |
|---|---|---|---|
| `REQ-FIL-048.1` | `TST-FIL-048.1` | Live 3-State `KinematicAltitudeSlopeEkf` datum-invariant `k2 = -k0` when `baroTrusted` + exact 3×3 Joseph SPD in `updateBarometer`; zero `-12%..-25%` tug-of-war on 7-min `+10..14 m` urban DEM plateau; thermal drift & corrupted-baro (`> 16 m²`) & baro-free preservation | `KinematicAltitudeSlopeEkfTest`, `AltitudeAndSlopeDevice_ATTFusedTest` |
| `REQ-FIL-048.2` | `TST-FIL-048.2` | `ExtendedRtsAltitudeSlopeSmoother` initial QNH `baroBias` seeding (`x[2] = baro0 - ref0` when both finite at `k=0`), datum-invariant `k2 = -k0` + baro-armed `16.0 m²` DEM variance & step-discrepancy gating, and smooth `walkingSpeedSlopeProcessScale(v)` over `[0.3, 2.8] m/s` preserving `lowSpeedSlopeGainFactor()` contract | `ExtendedRtsAltitudeSlopeSmootherTest`, `ErtsPostWorkoutRecomputerTest` |
| `REQ-FIL-048.3` | `TST-FIL-048.3` | Cross-terrain & cross-device parity: sustained `12%` hiking climb at `1.2 m/s` (`+14.4 m / 100 s`) and `10%` cycling descent at `12 m/s` (both baro-equipped and baro-free), plus offline Pixel 10 `:app:replayRawTelemetry` validation across `2026-11-23_093732`, `_171814`, `_182416`, `2026-11-22_081402` | `KinematicAltitudeSlopeEkfTest`, `ExtendedRtsAltitudeSlopeSmootherTest`, `OfflineTelemetryReplayHarnessTest`, `:app:replayRawTelemetry` |

## 2. Detailed Given-When-Then Acceptance Criteria

### `TST-FIL-048.1`: Zero Contradictory Negative Slope Under Persistent Urban DEM Offset (`REQ-FIL-048.1`)
- **Given** a 3-State `KinematicAltitudeSlopeEkf` warmed up on a flat walk (`v = 1.25 m/s`, `baro` flat within `±0.5 m`, tested at both `varBaro = 1.5` and `4.0 m²`), followed by a `420 s` (`7 min`) urban SRTM `DEM` plateau `+12 m` above ground (`440 m -> 452 m`),
- **When** 1-Hz `predict()`, `updateBarometer()`, and `updateReferenceAltitude()` execute across all `420` ticks,
- **Then** live `slopePercent` stays strictly within `[-3.0%, +3.0%]` (never `-12%..-25%` while `altitude` rises), `baroBias` absorbs the datum shift smoothly (`Δ(h + b_baro) ≈ 0`), and `assertSymmetricSpd3x3(ekf)` holds on every tick (including damped `gainScale < 1.0` in `updateBarometer()` and `36,000`-tick stability).
- **Given** corrupted pocket baro (`varBaro = 1000.0 > 16.0`) or barometer-free (`!hasBarometerSample`) streams,
- **When** `updateReferenceAltitude()` executes,
- **Then** `baroTrusted == false` preserves standard `k2 = p20 / s` and DEM-only slope estimation (`TST-FIL-040.2`, `TST-FIL-037.1` unchanged).

### `TST-FIL-048.2`: Walking-Speed Flat-City ERTS Stability & Large-QNH Startup Immunity (`REQ-FIL-048.2`)
- **Given** a flat city walk (`v = 1.0–1.3 m/s`, `1000 s`) with `0.15–0.20 m/s` vertical baro fluctuations, a `108 m` initial ISA-to-MSL QNH offset (`baro = -10 m`, `ref = 98 m`), and a `420 s` `+12 m` urban DEM plateau,
- **When** `ExtendedRtsAltitudeSlopeSmoother.smooth()` runs,
- **Then** smoothed `slopePercent` stays strictly within realistic flat-city bounds (`abs(slopePercent) < 5.0%`, zero `-35%` QNH saturation and zero `+18% / -15%` walking spikes), `lowSpeedSlopeGainFactor(0.75) == 0.25` and `(1.2) == 1.0` remain unchanged (`TST-FIL-033.1`), and `walkingSpeedSlopeProcessScale()` returns `0.0` for `NaN`/`< 0.3`, `~0.16` at `1.2 m/s`, and `1.0` at `>= 2.8 m/s`.

### `TST-FIL-048.3`: Genuine Steep Hiking/Cycling Preservation & Pixel 10 Replay (`REQ-FIL-048.3`)
- **Given** (a) a sustained `+12%` hiking climb at `1.2 m/s` (`+14.4 m` over `100 s` / `120 m`) and (b) a `-10%` cycling descent at `12.0 m/s` (`-120 m` over `100 s`), evaluated both **with barometer + DEM** and **barometer-free (`rawAltitudeM = Double.NaN`, DEM-only)**,
- **When** processed by `KinematicAltitudeSlopeEkf` and `ExtendedRtsAltitudeSlopeSmoother`,
- **Then** both live and smoothed `slopePercent` converge cleanly to `+12.0% ± 2.0%` (climb) and `-10.0% ± 2.0%` (descent) without excessive lag.
- **Given** archived Pixel 10 raw logs (`2026-11-23_093732`, `_171814`, `_182416`, `2026-11-22_081402`),
- **When** replayed via `./gradlew :app:replayRawTelemetry`,
- **Then** ERTS flat-city walk slope extremes drop dramatically from `-35%..+35%` into realistic bounds while `2026-11-22_081402` cycling grade tracking stays accurate.
