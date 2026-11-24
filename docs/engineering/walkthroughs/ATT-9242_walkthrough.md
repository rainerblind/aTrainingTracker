# Stage 5 Walkthrough: Post-Workout ERTS Pause-Gap Decoupling (`ATT-9242`)

- **Ticket**: `ATT-9242` (`2 SP`, `tier-a`) | **Epic**: `ATT-166` | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-9242`
- **Verified Specs**: `REQ-FIL-045` (`.1`–`.3`) / `TST-FIL-045` (`.1`–`.3`) | **Gate 3**: `ATT-9679` (`GATE_PASSED`)
- **Evidence Bundle**: `docs/engineering/test_evidence/tickets/ATT-9242/` | **Localization Parity**: `N/A - 0 string changes`

## 1. Summary of Production Changes (`<= 1,500` LoC Cap Preserved)
- **`ErtsPostWorkoutRecomputer.kt` (`1,252 / 1,500` LoC — `REQ-FIL-045.1`)**:
  - Reads `SensorType.TIME_ACTIVE` alongside `TIME_TOTAL` and `time`; added `reconstructTimingWithPauses()` merging wall (`> 5 s`), `TIME_TOTAL` (`extraGap >= 5 s`), and paired `TIME_TOTAL`/`TIME_ACTIVE` (`pauseDiff > 2 s && extraPairGap >= 2 s`) pauses via `max(...)` with overflow-safe `ExtendedRtsOdometrySmoother.isTimestampPauseGap()` and stale `prevPair*` reset whenever any pause lands on a null `TIME_TOTAL`/`TIME_ACTIVE` row.
  - Propagates `isPauseGap` into `RawSampleRow`, `RawOdometrySample`, and `RawAltitudeSample`; resets `BarometricAltitudeFilter` at pause boundaries; isolates `detectAndCompensateBaroResetSteps()` independently per pause-delimited segment; prevents `inferLegacyCarriageModeAt()` from crossing pauses; and passes `pauseGapMask` to `computeAuthoritativeAverageSpeedMps()` in both `updateWorkoutSummaries()` and `updateExtrema()` plus `!isPauseBoundary()` in `estimateRawReferenceDistanceMeters()`.
- **`ExtendedRtsOdometrySmoother.kt` (`1,042 / 1,500` LoC — `REQ-FIL-045.2`)**:
  - Added `RawOdometrySample.isPauseGap`, `MAX_ACTIVE_SAMPLE_GAP_MS = 5_000L`, and overflow-safe `isTimestampPauseGap()`.
  - Fixed `resolveInitialHeadingRad()` to subtract `(firstEast, firstNorth)` from `segment[firstValidIdx]` (preventing 180° heading inversion on Segment 1+ out-and-back rides) and applies explicit `initialHeadingRad` strictly to Segment 0.
  - Partitioned `smoothSamples()` into independent contiguous active segments sharing the global `(refLat, refLon)` frame (preserving `1:1` cardinality, 1-sample segments, and no-GNSS segment fallback); added `@JvmOverloads pauseGapMask` with backward RTS decoupling (`C_k = 0`) and `deltaDistanceMeters = 0.0` across pauses in `smoothSnapshots()`.
- **`ExtendedRtsAltitudeSlopeSmoother.kt` (`757 / 1,500` LoC — `REQ-FIL-045.3`)**:
  - Added `RawAltitudeSample.isPauseGap`, `MAX_ACTIVE_SAMPLE_GAP_MS = 5_000L`, and overflow-safe `isTimestampPauseGap()`.
  - Partitioned `smooth()` into independent contiguous active segments emitting `deltaDist = 0.0` and `verticalSpeedMps = 0.0` at every pause resume, preserving cumulative `ascentM`/`descentM` monotonically across both `n == 1` and `n > 1` segments, using `fallbackInitialAlt` on all-`NaN` segments, and carrying `inheritedBaroBias` across `n == 1` and DEM-only segments into baro-only segments.

## 2. Targeted Verification Evidence (`106 / 106` PASS, `0` New Compiler Warnings)
- **Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "*ErtsPostWorkoutRecomputer*" --tests "*ExtendedRtsOdometrySmoother*" --tests "*ExtendedRtsAltitudeSlopeSmoother*" --tests "*UnicycleOdometryEkf*" --tests "*BarometricAltitudeFilter*" --tests "*TrackerServiceErtsFinalization*" --tests "*LapsDatabaseManager*" --tests "*AdvancedTuningErtsContract*" --tests "*ArchitectureConformanceContractTest*" --tests "*InterfaceSignatureDriftContractTest*"`
- **Suites**: `ErtsPostWorkoutRecomputerTest` (`22/22`), `ExtendedRtsOdometrySmootherTest` (`15/15`), `ExtendedRtsAltitudeSlopeSmootherTest` (`11/11`), `UnicycleOdometryEkfTest` (`21/21`), `BarometricAltitudeFilterTest` (`7/7`), `TrackerServiceErtsFinalizationTest` (`2/2`), `LapsDatabaseManagerTest` (`11/11`), `WorkoutDataMapperLapsTest` (`1/1`), `AdvancedTuningErtsContractTest` (`2/2`), `ArchitectureConformanceContractTest` (`11/11`), `InterfaceSignatureDriftContractTest` (`3/3`).
- **Key Verified Assertions**:
  - `TST-FIL-045.1`: `Zum Bäcker #59` (`4 s`, `17 s`, `134 s`, `610 s` pauses across 2 laps) emits `0.0 m` `DISTANCE_m` delta & `0 m/h` `VERTICAL_SPEED` on every pause row, keeps `DISTANCE_TOTAL_m` within `±3%` of live active distance, aligns `SPEED_AVERAGE_mps` / `Extrema(AVG)` / `Laps.db` / `DISTANCE_m_LAP`, preserves device columns byte-for-byte, syncs repeated `4 s` auto-pauses, resets `prevPair*` across null-`TIME_ACTIVE` pauses, isolates per-segment baro reset steps, and keeps `{0, 2}` beat & `6 s` flush-stall immunity (`TST-FIL-027.1` aligned).
  - `TST-FIL-045.2`: Out-and-back Segment 2 resolves West (`~±PI`), zero cross-pause RTS/EKF bleed, direct `smoothSnapshots()` decoupling, 1-sample & no-GNSS segments, and `Long` overflow safety.
  - `TST-FIL-045.3`: Zero `g * v * dt_pause` altitude jump across `134 s` / `610 s` pauses, trailing 1-sample `ascentM`/`descentM`/`fallbackInitialAlt` preservation, and DEM-only -> baro-only bias handoff.
- **Automated Gate**: Verified via `verify_requirement_governance.py` (`exit 0`) and `auto_gate.py --ticket ATT-9242 --tier tier-a --base-ref sprint/2026-11-24_01_Früh --write-bundle`.
