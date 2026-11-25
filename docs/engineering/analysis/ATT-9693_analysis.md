# Stage 1 Analysis — ATT-9693: Fix `KinematicAltitudeSlopeEkf` Negative-Slope DEM Tug-of-War & Walking-Speed ERTS Spikes

- **Ticket**: `ATT-9693` (`3 SP`, Bug, `tier-a`, `severity-major`, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Base**: `sprint/2026-11-25_01_Früh` (`4f9d06c4`)
- **Reserved IDs**: `REQ-FIL-048` (`.1`–`.3`) <-> `TST-FIL-048` (`.1`–`.3`) | **UI/Schema/Strings**: `0` (`N/A - 0 string changes`)

## 1. Forensic Root Cause Analysis
1. **3-State EKF Persistent DEM-vs-Baro Slope Tug-of-War (`KinematicAltitudeSlopeEkf.kt:416-442, 571-578`)**:
   - In 3-State mode `[h, g, b_baro]`, `updateReferenceAltitude()` decouples `k1Eff = 0.0` when `baroTrusted` (`hasBarometerSample && lastBaroVarM2 <= 16.0`), but updates `baroBias` via `k2 = p20 / s`.
   - After sustained walking with `rBaro = 1.5..4.0 m²` (`PhoneCarriageMode.WALKING_TRANSITION` + adaptive jitter), `|p20| < p00`, so `k0 + k2 = (p00 + p02) / s > 0`.
   - When urban SRTM `DEM` plateaus `+10..14 m` above ground for minutes (`2026-11-23_171814` rows `420..840`), `computeStepGatedRefVariance()` inflates only the edge tick and advances `lastRefAltM`. On subsequent ticks, `updateReferenceAltitude()` pulls `altitude` UP (`+k0 * yEff`) faster than `baroBias` shifts DOWN (`k2 * yEff`), injecting a positive offset into `(altitude + baroBias)` even though `baro` is flat.
   - Each following `updateBarometer()` sees persistent negative `y = baro - (altitude + baroBias) < 0` and projects it onto `slope` via `k1Eff = ((p10 + p12) / s) * gainScale > 0`, driving `slope` to **`-7.5%..-14.6%`** while `altitude` is rising! `predict()` then pulls `altitude` down via `slope * ds`, locking a self-sustaining tug-of-war.
   - Additionally, 3-State `updateBarometer()` (`lines 429-441`) uses non-Joseph `P - K H P` subtraction even when `gainScale < 1.0`, risking SPD degradation under damped `k1Eff`.
2. **ERTS Initial QNH Bias Unseeded + DEM Coupling + Walking-Speed Slope Curvature (`ExtendedRtsAltitudeSlopeSmoother.kt:236-260, 340, 424-445`)**:
   - `smoothContiguousSegment()` initializes `x[2] = 0.0` even when `samples[0]` carries both finite `rawAltitudeM` (`-10..-35 m` ISA) and `referenceAltitudeM` (`+92..112 m` MSL). The `> 100 m` QNH offset exceeds `HUBER_K * sqrt(400) = 50 m`, repeatedly Huber-clamping `b_baro` absorption and pinning ERTS `slope` to **`-35.0%`** on real Pixel 10 walks (`2026-11-23_093732`, `_171814`, `_182416`).
   - ERTS reference updates (`line 433`) suffer the same `k2 = p[2][0] / sRef` leak into `(h + b_baro)` and use stiff `DEFAULT_VAR_DEM = 4.0` without baro-vs-DEM step gating, so urban `+12 m` DEM jumps leak `+4..15%` into backward-smoothed slope.
   - Above `LOW_SPEED_SLOPE_THRESHOLD_MPS = 1.2 m/s`, `lowSpeedSlopeGainFactor(v) == 1.0` leaves full `DEFAULT_QG = 1e-4 / m` active at walking speeds (`1.2–1.6 m/s`), so `3–6 s` `0.15–0.20 m/s` vertical noise swings smoothed slope by `±13–21%`.
   - **Baseline Pixel 10 `:app:replayRawTelemetry`**: `093732` ERTS `[-35.0%, +35.0%]`, `171814` ERTS `[-23.3%, +21.0%]`, `182416` ERTS `[-22.0%, +29.0%]`, `081402` ERTS `[-20.5%, +17.2%]`.

## 2. Change Impact Analysis (4 Dimensions)
1. **Behavioral & Algorithmic**: Setting `k2 = -k0` when `baroTrusted` (`KinematicAltitudeSlopeEkf`) / `hasBaroData` (`ExtendedRtsAltitudeSlopeSmoother`) enforces exact datum invariance `Δ(h + b_baro) = 0`, `Δvar(h + b_baro) = 0`, and `Δcov(g, h + b_baro) = 0` across DEM updates; seeding initial `x[2] = baro0 - ref0` eliminates `> 50 m` QNH Huber saturation; smooth walking-speed `q_g` scaling over `[0.3, 2.8] m/s` lengthens spatial slope memory at `0.8–1.6 m/s` while preserving `12%` sustained hiking climbs (`1.2 m/s`, both baro & baro-free) and `10%` cycling descents (`12 m/s`).
2. **API & Callers**: Zero breaking signature changes; `AltitudeAndSlopeDevice_ATTFused.kt`, `ErtsPostWorkoutRecomputer.kt`, and `OfflineTelemetryReplayHarness.kt` inherit improvements transparently; existing `lowSpeedSlopeGainFactor()` contract (`TST-FIL-033.1`) preserved intact.
3. **Persistence, UI & Localization**: Zero DB schema, `WorkoutSamples`, UI, or `strings*.xml` changes.
4. **Performance & Stability**: Pure `O(1)` 3×3 arithmetic; Joseph 3×3 SPD updates verified strictly symmetric positive-definite across `36,000` ticks (10 hours).

## 3. Chesterton's Fence (`REQ-PRO-022`)
- **Refined Legacy Requirements**: `REQ-FIL-016` (`ATT-6279`), `REQ-FIL-017` (`ATT-7750`), `REQ-FIL-023` (`ATT-8880`), `REQ-FIL-033` (`ATT-9063`), `REQ-FIL-037` (`ATT-9156`), `REQ-FIL-040` (`ATT-9602`).
- **Invariants Preserved**: (1) `DEFAULT_VAR_DEM = 25.0`, `DEFAULT_VAR_GPS = 64.0`, `DEFAULT_VAR_BARO = 0.25` in `KinematicAltitudeSlopeEkf`; (2) `ExtendedRtsAltitudeSlopeSmoother.lowSpeedSlopeGainFactor(0.75) == 0.25` & `(1.2) == 1.0` (`TST-FIL-033.1`); (3) barometer-free DEM-only `DEFAULT_VAR_DEM = 4.0` and `k1 > 0` (`TST-FIL-033.3`, `TST-FIL-037.1`); (4) corrupted-baro `varBaro > 16.0` bypass (`TST-FIL-040.2`); (5) delayed-anchor (`TST-FIL-017.2`), pause-gap (`TST-FIL-045.3`), baro-reset (`TST-STB-025.4`), and braking (`TST-FIL-040.4`) paths.

## 4. Independent Peer Review (Self-Audit Pre-Spec)
- Verified numerically via Python 3×3 EKF/ERTS replica: `+12 m` DEM plateau (`rBaro = 4.0`) drops live EKF slope from `-14.56%` -> `0.00%`; thermal weather drift isolation improves (`441.68 m` vs `442.90 m`); `12%` hiking climb (`1.2 m/s`, `100 s`) converges to `+12.00%` (EKF) and `+11.99%` / `+11.96%` (ERTS baro / baro-free); `10%` cycling descent (`12 m/s`) converges to `-10.00%`; real Pixel 10 ERTS walk slopes drop from `-35.0%` to `[-3.0%, +7.2%]`.
