# Stage 2: Requirement & Test Specification - ATT-9064: ERTS TIME_TOTAL Beat Non-Stretching, speedAverage_mps Parity & Stationary Indoor Coordinate Pinning

**Ticket**: `ATT-9064` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-9064` (rebased onto `2d7f1489`)  
**Requirement Mapping**: `REQ-FIL-032` (`REQ-FIL-032.1`–`.3`, refining `REQ-FIL-017.1`/`.3`, `REQ-FIL-025.4`, `REQ-FIL-027.1`/`.3`, `REQ-FIL-028.4`, `REQ-TRK-030.3`)  
**Test Spec ID**: `TST-FIL-032` (`TST-FIL-032.1`–`.3`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-FIL-032`)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Implementation**: `ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsOdometrySmoother.kt`, `UnicycleOdometryEkf.kt`

### 1.2 Functional & Architectural Requirements
1. **Beat-Immune 1-Hz ERTS Timestamp Reconstruction (`REQ-FIL-032.1`)**:
   - `ErtsPostWorkoutRecomputer.readWorkoutSamples()` **SHALL** advance active 1-Hz timestamps via `oneHzTs = prevTs + 1000L` without ratcheting `baseEpochMs` on `TIME_TOTAL` `{0, 2}` clock-beat pairs in any ordering (`0 -> 2`, `2 -> 0`, or separated beats).
   - It **SHALL** detect and re-anchor (`baseEpochMs`, `firstTimeTotalSec`) strictly on genuine `> 5_000L ms` pauses—whether signaled by single-step `deltaTimeTotal > 5L` (or `timeTotalTs - oneHzTs > 5_000L`) OR wall-clock `(parsedTs - oneHzTs) > 5_000L`, including when a `> 5 s` wall-clock pause lands on a `timeTotalSec == prevTimeTotalSec` row or leading/all-`null` `TIME_TOTAL` rows—while preserving `TST-FIL-027.1` `6 s` SQLite batch-flush stall immunity and `30 s`/`25 s` double pauses.
2. **Authoritative `speedAverage_mps` & `ExtremaType.AVG(SPEED_mps)` Parity (`REQ-FIL-032.2`)**:
   - `ErtsPostWorkoutRecomputer.updateWorkoutSummaries()` and `updateExtrema()` **SHALL** compute one unified finite non-negative `avgSpeed` and persist it identically to `WorkoutSummaries.SPEED_AVERAGE_mps` and `ExtremumValues(SensorType.SPEED_mps, ExtremaType.AVG)`.
   - Resolution precedence **MUST** be: (a) `totalDistance / TIME_ACTIVE_s` when persisted `WorkoutSummaries.TIME_ACTIVE_s > 0` (matching `TrackerService.getAverageSpeed()` and `LapsDatabaseManager.syncLapDistancesAndSpeeds()`); (b) else matched active-step ratio `activeDist / activeDt` over steps with `dt in (0.0, MAX_ACTIVE_SAMPLE_GAP_SEC (5.0)]` (preventing pause-gap `deltaDist` inflation when `TIME_ACTIVE_s` is `NULL`/`<= 0`); (c) else finite non-negative unweighted mean (defaulting to `0.0` if empty/non-finite).
3. **Stationary Indoor `[E, N]` Covariance Freeze & Backward RTS Coordinate Pinning (`REQ-FIL-032.3`)**:
   - `UnicycleOdometryEkf.predict()` **SHALL** expose `@JvmOverloads isStationary: Boolean = false` (leaving live `SpeedAndLocationDevice_ATTFused` untouched); when `isStationary == true`, `predict()` **MUST** freeze `x[0..1]`, set `F[0..1, :] = [I_2, 0]`, and set position process noise `Q_POS = 0.0` while preserving heading/bias propagation and positive-definite invertibility of `P_{k+1|k}`.
   - `ExtendedRtsOdometrySmoother.smoothSamples()` **SHALL** pass `isStationary = isStationary` to `ekf.predict()`, and `smoothSnapshots()` **SHALL** pin `smoothedStates[k][0..1] = smoothedStates[k + 1][0..1]` across consecutive stationary epochs (`currStationary && nextStationary`), guaranteeing `0.00 m` Haversine coordinate drift across stationary indoor stops while preserving post-outage GNSS re-acquisition (`TST-FIL-027.3`) and canopy Doppler-dropout (`wheelSpeedMps = 10.0`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (`REQ-FIL-032.1`)**: *Given* a `584`-row 1-Hz `WorkoutSamples.db` table with `78` `TIME_TOTAL` `0/2` beat pairs (`Zum Bäcker #58` profile, both `0 -> 2` and `2 -> 0` orderings), plus a separate timeline with leading/all-`null` `TIME_TOTAL`, `6 s` batch-flush stall, and `> 5 s` pauses (including a `> 5 s` wall pause on `timeTotalSec == prevTimeTotalSec`), *When* `ErtsPostWorkoutRecomputer` reconstructs timestamps, *Then* the `584`-row span is `583 s` (`0 s` stretch, not `662 s`), the `6 s` stall keeps `1 s` steps, and genuine `> 5 s` pauses re-anchor cleanly.
* **AC-2 (`REQ-FIL-032.2`)**: *Given* a workout with `TIME_ACTIVE_s = 584` and smoothed `totalDistance = 1,910.08 m` (plus a legacy/test fixture with `TIME_ACTIVE_s = NULL` containing a `30 s` pause gap), *When* `ErtsPostWorkoutRecomputer.recomputeWorkout()` updates `WorkoutSummaries.db`, `EXTREMA_VALUES`, and `Laps.db`, *Then* `WorkoutSummaries.speedAverage_mps`, `ExtremumValues(SPEED_mps, AVG)`, and `Laps.speedAverage_mps` all agree with `totalDistance / TIME_ACTIVE_s` within `0.02 m/s`, and the `NULL` `TIME_ACTIVE_s` fallback uses matched `activeDist / activeDt` without pause-gap inflation.
* **AC-3 (`REQ-FIL-032.3`)**: *Given* a workout with a `118 s` indoor stationary stop (`speed = 0.00 m/s`, `acc = 48.5 m`) between arrival and `+35.66 m` departure GNSS re-acquisition, *When* `ExtendedRtsOdometrySmoother.smoothSamples()` completes, *Then* `speedMps == 0.0`, `deltaDistanceMeters == 0.0`, and cumulative Haversine coordinate movement across the stationary stop equals `0.00 m` (down from `35.66 m`), while pre-/post-stop moving speeds stay `> 4.0 m/s`.

### 1.4 System Invariants & Localization
- Raw device-attributed columns stay byte-for-byte immutable (`REQ-FIL-017.3`); `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` default preserved; live `UnicycleOdometryEkf` (`isStationary = false`, `MIN_DOPPLER_SPEED_MPS = 3.0`, `ZUPT_SPEED_VARIANCE = 2.0`) untouched; `0` DB schema changes; `0` protected monolith edits (`ErtsPostWorkoutRecomputer`, `ExtendedRtsOdometrySmoother`, `UnicycleOdometryEkf` `< 1,500` LoC); `0` new `src/main` reads in tests (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

---

## 2. Test Specification (`TST-FIL-032`)

* **`TST-FIL-032.1` (Clock-Beat `0/2` Non-Stretching & Pause/Stall Parity — `ErtsPostWorkoutRecomputerTest.kt`)**: Replay `584`-row `78`-pair `0/2` (`2 -> 0` and `0 -> 2`) `TIME_TOTAL` beat sequence asserting reconstructed span `== 583_000L ms` (`0 s` stretch), plus `> 5 s` wall pause landing on `timeTotalSec == prevTimeTotalSec`, `deltaTimeTotal > 5` gap, leading/all-`null` `TIME_TOTAL` pauses, and regression of `testLeadingNullTimeTotal_batchFlushStall6s_andDoubleWallClockPause_tstFil027_1`.
* **`TST-FIL-032.2` (`speedAverage_mps` / `Extrema(AVG)` / `Laps.db` Parity & Matched Fallback — `ErtsPostWorkoutRecomputerTest.kt`)**: Verify `WorkoutSummaries.SPEED_AVERAGE_mps`, `ExtremumValues(SPEED_mps, AVG)`, and `Laps.speedAverage_mps` agree with `totalDistance / TIME_ACTIVE_s` within `0.02 m/s` on a `584 s` ride with an indoor stop, and verify `TIME_ACTIVE_s = NULL` with a `30 s` pause uses matched `activeDist / activeDt` (`<= 5.0 s` steps) without pause-gap inflation, plus zero/empty/non-finite guards.
* **`TST-FIL-032.3` (Stationary Covariance Freeze & `0.00 m` Indoor Haversine Pinning — `UnicycleOdometryEkfTest.kt`, `ExtendedRtsOdometrySmootherTest.kt`, `ErtsPostWorkoutRecomputerTest.kt`)**: Verify `UnicycleOdometryEkf.predict(isStationary = true)` freezes `[E, N]`, zeroes `F[0..1, 2..4]` and `Q_POS` (zero `P_pos` growth over `118 s`, invertible SPD `P_{k+1|k}`), and leaves default `isStationary = false` unchanged; verify `ExtendedRtsOdometrySmoother.smoothSamples()` and `smoothSnapshots()` yield `0.00 m` Haversine drift and `0.00 m` `deltaDistanceMeters` across a `118 s` (`acc = 48.5 m`) indoor stop with `+35.66 m` departure jump while preserving `TST-FIL-027.3` & canopy Doppler-dropout (`wheelSpeedMps = 10.0`).
* **Contract & Regression Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "*ErtsPostWorkoutRecomputerTest" --tests "*ExtendedRtsOdometrySmootherTest" --tests "*UnicycleOdometryEkfTest" --tests "*TrackerServiceErtsFinalizationTest" --tests "*LapsDatabaseManagerTest" --tests "*ArchitectureConformanceContractTest" --tests "*SystemLiveTrackingRequirementsContractTest" --tests "*SourceTextReadingAuditContractTest"`

---

## 3. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-032.1` | Beat-immune 1-Hz timestamping & pauses | `ErtsPostWorkoutRecomputerTest` | `REQ-FIL-032.1`, `REQ-FIL-027.1` | Planned |
| `TST-FIL-032.2` | `speedAverage_mps` / Extrema / Laps parity | `ErtsPostWorkoutRecomputerTest`, `LapsDatabaseManagerTest` | `REQ-FIL-032.2`, `REQ-FIL-025.4`, `REQ-TRK-030.3` | Planned |
| `TST-FIL-032.3` | Stationary covariance freeze & `0.00 m` RTS pin | `UnicycleOdometryEkfTest`, `ExtendedRtsOdometrySmootherTest`, `ErtsPostWorkoutRecomputerTest` | `REQ-FIL-032.3`, `REQ-FIL-027.3`, `REQ-FIL-028.4` | Planned |
| Contract Regression | LoC ceilings, SYS.2/3, zero `src/main` reads | `TrackerServiceErtsFinalizationTest`, `ArchitectureConformanceContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest` | `REQ-PRO-049.1`, `REQ-PRO-129.2`, `REQ-PRO-131.4` | Planned |
