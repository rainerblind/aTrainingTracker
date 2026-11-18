# Stage 2: Requirement & Test Specification - ATT-8705

**Ticket**: [ATT-8705](https://rainerblind.atlassian.net/browse/ATT-8705) (`3 SP`, `severity-major`) | **Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) | **Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-FIL-025` (refining `REQ-FIL-007`, `REQ-FIL-017`, `REQ-FIL-019.4`; preserving `REQ-FIL-023.1`)  
**Test Spec ID**: `TST-FIL-025` | **Branch**: `feature/ATT-8705` | **Author**: AI Agent 1 | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-FIL-025`)
* **Attributes**: Priority: `High` | Type: `Functional` | Verification Method: `Unit`
* **REQ-FIL-025.1 (Offline Heading Init & Running-Pace Anchoring)**: `ExtendedRtsOdometrySmoother.smoothSamples()` SHALL initialize `initialHeadingRad` from the first valid GNSS movement chord (never hardcoded `PI / 2.0` when motion exists) and anchor heading at running speeds (`~2.0 m/s`) via offline GNSS displacement-chord/bearing updates, while `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` MUST remain strictly enforced for live `updateDopplerHeading()` (`ATT-8576`).
* **REQ-FIL-025.2 (1-Hz Yaw-Rate Anti-Aliasing & Non-Negative Speed)**: `ExtendedRtsOdometrySmoother` SHALL mitigate 1-Hz instantaneous `WORLD_YAW_RATE_radps` snapshot aliasing in offline RTS replay and MUST prevent non-physical negative velocity state collapse (`x[2] < 0`) and false `0.0 m/s` clamping, preserving `ExtendedRtsAltitudeSlopeSmoother` standstill gating (`speed < 0.3 m/s`) accuracy.
* **REQ-FIL-025.3 (Symmetric Sanity Bounds & `att_fused` Preservation)**: `ErtsPostWorkoutRecomputer.isOdometrySane()` SHALL enforce symmetric lower and upper distance divergence bounds (pause-aware) against raw reference distance, preserving live `att_fused` canonical columns, summaries, and `Laps.db` when smoothed output is rejected (`REQ-FIL-017.3`, `REQ-FIL-019.4`).
* **REQ-FIL-025.4 (`Laps.db` Post-Workout Synchronization)**: Whenever `ErtsPostWorkoutRecomputer` commits updated smoothed totals to `WorkoutSummaries.db`, it SHALL synchronize `Laps.db` (`LapsDatabaseManager` / `Laps.TABLE`) lap distances and average speeds from smoothed `WorkoutSamples.db` `LAP_NR` segments (with proportional fallback).
* **Given-When-Then Acceptance Criteria**:
  * *Given* a `~2.0 m/s` running track starting South/West with 1-Hz `WORLD_YAW_RATE_radps` swing noise, *When* `ExtendedRtsOdometrySmoother.smoothSamples()` runs, *Then* heading initializes from the first valid GNSS chord, remains aligned via offline displacement/bearing updates, `x[2]` stays non-negative without `0.0 m/s` freezes, distance matches ground truth, and live `UnicycleOdometryEkf.updateDopplerHeading()` at `<= 3.0 m/s` remains gated.
  * *Given* an offline smoothed trajectory collapsing below the lower divergence bound (accounting for pauses) or exceeding `3.0x`, *When* `ErtsPostWorkoutRecomputer.isOdometrySane()` evaluates it, *Then* it returns `false` and leaves live `att_fused` columns, summaries, and `Laps.db` untouched.
  * *Given* a multi-lap workout committed by `ErtsPostWorkoutRecomputer`, *When* `updateWorkoutSummaries()` finishes, *Then* `Laps.db` lap distances and speeds match smoothed `LAP_NR` segments and sum consistently with `WorkoutSummaries.DISTANCE_TOTAL_m`.
* **System Invariants**: Live `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` & `ATT-8576` stationary/ZUPT gating preserved; `ExtendedRtsAltitudeSlopeSmoother` standstill gating preserved; `REQ-FIL-017.3` raw device-column immutability & `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` preserved; zero DB schema changes; `N/A - no UI strings`.

---

## 2. Test Specification (`TST-FIL-025`)
* **Test Case 1 (`TST-FIL-025.1` — `ExtendedRtsOdometrySmootherTest.kt`, `UnicycleOdometryEkfTest.kt`)**: Simulate South/West `~2.0 m/s` running track; verify first-chord heading initialization, running-pace displacement/bearing heading anchoring, accurate distance, and preserved live `MIN_DOPPLER_SPEED_MPS = 3.0` gate.
* **Test Case 2 (`TST-FIL-025.2` — `ExtendedRtsOdometrySmootherTest.kt`)**: Inject 1-Hz `WORLD_YAW_RATE_radps` swing noise during `~2.2 m/s` running; verify no backward heading flip, no negative `x[2]` / `0.0 m/s` clamp, and preserved `ExtendedRtsAltitudeSlopeSmoother` standstill gating (`< 0.3 m/s`) accuracy.
* **Test Case 3 (`TST-FIL-025.3` — `ErtsPostWorkoutRecomputerTest.kt`)**: Verify `isOdometrySane()` rejects collapsed (< lower bound) and runaway (`> 3.0x`) distances while allowing paused workouts, preserving live `att_fused` columns/summaries/`Laps.db` on rejection.
* **Test Case 4 (`TST-FIL-025.4` — `ErtsPostWorkoutRecomputerTest.kt`)**: Run `recomputeWorkoutBlocking()` on a multi-lap Robolectric DB; verify `Laps.TABLE` distances & average speeds sync with smoothed `LAP_NR` segments and `WorkoutSummaries.DISTANCE_TOTAL_m`.
* **Test Case 5 (`TST-FIL-025.5` — Offline Regression & Parity)**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.*" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedTest" --tests "com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputerTest"` (strictly offline JUnit/Robolectric/MockK; `REQ-PRO-049.1` zero `src/main` reads; `N/A - no UI strings`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-025.1` | Unit | `ExtendedRtsOdometrySmoother`, `UnicycleOdometryEkf` | `REQ-FIL-025.1`, `REQ-FIL-023.1` | Specified |
| `TST-FIL-025.2` | Unit | `ExtendedRtsOdometrySmoother`, `ExtendedRtsAltitudeSlopeSmoother` | `REQ-FIL-025.2`, `REQ-FIL-017.2` | Specified |
| `TST-FIL-025.3` | Robolectric/MockK | `ErtsPostWorkoutRecomputer.isOdometrySane` | `REQ-FIL-025.3`, `REQ-FIL-019.4` | Specified |
| `TST-FIL-025.4` | Robolectric/SQLite | `ErtsPostWorkoutRecomputer`, `LapsDatabaseManager` | `REQ-FIL-025.4` | Specified |
| `TST-FIL-025.5` | Regression (Tier 1) | Odometry + `ATTFused` + `ErtsPostWorkoutRecomputer` suites | `REQ-PRO-049.1`, `REQ-FIL-025` | Specified |
