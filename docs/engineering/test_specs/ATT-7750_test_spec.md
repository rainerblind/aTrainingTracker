# ATT-7750: Stage 2 Requirement & Test Specification — Extended Rauch-Tung-Striebel (ERTS/RTS) Smoother

**Epic**: ATT-166 (*Filtering to improve sensor values (especially locations)*)  
**Ticket**: ATT-7750 (*Post-workout forward-backward Extended RTS (ERTS) zero-phase track, speed & altitude smoothing reusing ATT-131 & ATT-6279 models (with Expert Settings toggle)*)  
**Requirement Mapping**: `REQ-FIL-017` (*Post-Workout Extended Rauch-Tung-Striebel (ERTS/RTS) Zero-Phase Smoothing Engine*)  
**Test Suite**: `TST-FIL-017` (*Extended Rauch-Tung-Striebel (ERTS/RTS) Zero-Phase Smoothing Verification Suite*)

---

## 1. Requirement Specification (REQ-FIL-017)

### 1.1 `REQ-FIL-017.1`: 2D Trajectory & Speed Extended RTS Pass
- The system SHALL execute a forward EKF replay using the 5-state unicycle odometry model $[E, N, v, \psi, b_{\omega_z}]^T$, recording prior states $x_{k|k-1}$, prior covariances $P_{k|k-1}$, state-transition Jacobians $F_{k-1}$, posterior states $x_{k|k}$, and posterior covariances $P_{k|k}$ across discrete 1-Hz sample points $k = 0 \dots N$.
- The backward recursion SHALL execute:
  $$C_k = P_{k|k} F_k^T (P_{k+1|k})^{-1}$$
  $$x_{k|N} = x_{k|k} + C_k \left(x_{k+1|N} - x_{k+1|k}\right)$$
  $$P_{k|N} = P_{k|k} + C_k \left(P_{k+1|N} - P_{k+1|k}\right) C_k^T$$
  for $k = N-1 \dots 0$, normalizing angular heading differences $\psi_{k+1|N} - \psi_{k+1|k}$ to $[-\pi, \pi)$ via `normalizeAngle`.
- Backward smoothing SHALL propagate post-outage GNSS position and Doppler speed fixes backward through outages (e.g. 45s curved tunnel or canopy dropout) to eliminate trajectory steps and speed dropouts without rounding genuine sharp turns or hairpins.
- The 5x5 matrix inversion $(P_{k+1|k})^{-1}$ and recursion across 14,400 samples (4-hour workout) SHALL execute in $< 100\text{ ms}$ with zero external matrix libraries.

### 1.2 `REQ-FIL-017.2`: Kinematic Altitude & Slope Extended RTS Pass
- The system SHALL execute a forward-backward RTS pass over vertical elevation and terrain slope using the exact kinematic model $[h_k, g_k, b_{baro,k}]^T$ (or 2-State $[h_k, g_k]^T$ if DEM/GPS reference is absent), driven by smoothed horizontal displacements $\Delta s_{k|N} = v_{k|N} \cdot \Delta t_k$.
- Backward recursion SHALL eliminate causal startup transients and filter phase delay, yielding zero-phase altitude $h_{k|N}$, terrain slope $g_{k|N}$, vertical speed $v_{z,k|N} = \frac{h_{k|N} - h_{k-1|N}}{\Delta t_k}$, and cumulative ascent/descent.
- Standstill segments ($v < 0.3\text{ m/s}$) SHALL clamp slope process noise ($Q_g = 0$) and prevent false climb accumulation.

### 1.3 `REQ-FIL-017.3`: Strict Column Scope Invariant
- In `WorkoutSamples.db`, the post-workout smoother SHALL update *exclusively* the canonical unsuffixed columns:
  `LATITUDE`, `LONGITUDE`, `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `BEARING`, `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, and `DESCENT`.
- Every source-attributed per-device column (`<SensorType> (<SourceName>)`), explicitly including `LATITUDE (GPS)`, `SPEED_mps (GPS)`, `SPEED_mps (Google Fused)`, `SPEED_mps (att_fused)`, `ALTITUDE (Höhe aus Luftdruck)`, `ALTITUDE (Topografische Höhe (DEM))`, and `ALTITUDE (att_fused_altitude_slope)`, SHALL remain 100% byte-for-byte untouched.

### 1.4 `REQ-FIL-017.4`: Workout Summaries & Stream Synchronization
- Upon successful ERTS smoothing of `WorkoutSamples.db`, the system SHALL update `WorkoutSummaries.db` via `WorkoutSummariesDatabaseManager.updateMapAndStreams` with the re-encoded smoothed polyline, altitude stream, and distance stream, and update summary metrics (total distance, total ascent/descent, max/avg speed).

### 1.5 `REQ-FIL-017.5`: Expert Settings Opt-In Configuration
- The smoother SHALL be guarded by a preference `postWorkoutErtsSmoothingEnabled` in `TuningPreferencesDataStore.kt` (`KEY_POST_WORKOUT_ERTS_SMOOTHING`), defaulting to `false`.
- In `SensorsGpsFilterSection.kt` and `AdvancedTuningScreen.kt`, a dedicated `TuningToggleItem` SHALL expose this setting ("Post-Workout ERTS-Glättung (Strecke, Geschwindigkeit & Höhe)" / "Post-workout ERTS smoothing (track, speed & altitude)"), localized across all 9 supported languages with zero text clipping.
- When the preference is disabled, post-workout finalization SHALL bypass ERTS smoothing with zero behavioral change from baseline.

---

## 2. Test Specification (TST-FIL-017)

### 2.1 `TST-FIL-017.1`: Unicycle Odometry Extended RTS 5-State Simulation Test
- **Target**: `com.atrainingtracker.banalservice.odometry.ExtendedRtsOdometrySmootherTest`
- **Scenarios**:
  1. *Curved Tunnel Outage Replay*: Simulate a 45s $90^\circ$ curved tunnel at $10\text{ m/s}$ with IMU yaw-rate and wheel speed, followed by a 15m GNSS position jump at the exit. Verify the backward RTS pass eliminates the step at the exit and smoothly bends the trajectory through the tunnel without jagged discontinuities.
  2. *Doppler Dropout Replay*: Simulate a 10s canopy Doppler speed dropout ($v_{GNSS} = 0$). Verify smoothed speed $v_{k|N}$ remains smooth and continuous at $\sim 10\text{ m/s}$.
  3. *Performance Benchmark*: Replay 14,400 1-Hz synthetic samples (4-hour ride); verify the complete forward-backward pass finishes in $< 100\text{ ms}$.

### 2.2 `TST-FIL-017.2`: Kinematic Altitude & Slope RTS Simulation Test
- **Target**: `com.atrainingtracker.banalservice.odometry.ExtendedRtsAltitudeSlopeSmootherTest`
- **Scenarios**:
  1. *Delayed Anchor Convergence*: Simulate a hill climb (+8% grade) where barometric altitude drifts by $+20\text{ m}$ in the first 60s before DEM lock stabilizes. Verify RTS backward pass propagates DEM anchor backward, eliminating initial elevation bias.
  2. *Standstill Clamping*: Simulate a 5-minute coffee stop ($v=0$) with barometric pressure drop ($1\text{ hPa} \approx 8\text{ m}$). Verify smoothed elevation remains flat and false climb accumulation is 0.0 m.

### 2.3 `TST-FIL-017.3`: Strict Column Scope Invariant & Database Recomputation Test
- **Target**: `com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputerTest`
- **Scenarios**:
  1. *Column Invariant Verification*: Populate a SQLite `WorkoutSamples.db` table with canonical columns and 6 device columns (`LATITUDE (GPS)`, `SPEED_mps (GPS)`, `SPEED_mps (Google Fused)`, `SPEED_mps (att_fused)`, `ALTITUDE (Höhe aus Luftdruck)`, `ALTITUDE (Topografische Höhe (DEM))`). Run `ErtsPostWorkoutRecomputer.recomputeWorkout(...)`.
  2. Assert all device-attributed columns are byte-for-byte identical before and after recomputation.
  3. Assert canonical `LATITUDE`, `LONGITUDE`, `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `ALTITUDE`, `SLOPE`, `ASCENT`, `DESCENT` are updated with smoothed values.
  4. Assert `WorkoutSummaries.db` polyline, streams, distance, and extrema are refreshed.

### 2.4 `TST-FIL-017.4`: TrackerService Lifecycle & Toggle Gating Test
- **Target**: `com.atrainingtracker.trainingtracker.tracker.TrackerServiceErtsFinalizationTest`
- **Scenarios**:
  1. Finalize workout with toggle disabled: verify `ErtsPostWorkoutRecomputer` is never invoked.
  2. Finalize workout with toggle enabled: verify `ErtsPostWorkoutRecomputer` runs asynchronously and dispatches `refreshWorkout`.

### 2.5 `TST-FIL-017.5`: Expert Settings UI & 9-Language Localization Contract Test
- **Target**: `com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningErtsContractTest`
- **Scenarios**:
  1. Verify `TuningPreferencesDataStore` default value is `false` for `KEY_POST_WORKOUT_ERTS_SMOOTHING`.
  2. Verify toggle item rendered in `SensorsGpsFilterSection` and `AdvancedTuningScreen`.
  3. Verify 9-language localization parity across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-pt`, `values-ru`, `values-uk`.

### 2.6 `TST-FIL-017.6`: Targeted Unit & Regression Suite Execution
- **Target**: Full regression check across odometry, database, and settings packages.
- Command: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.*" --tests "com.atrainingtracker.trainingtracker.database.*"`

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case | Target Class / File | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-017.1` | 2D Trajectory & Speed Extended RTS Pass | `TST-FIL-017.1` | `ExtendedRtsOdometrySmoother.kt` | Specified |
| `REQ-FIL-017.2` | Kinematic Altitude & Slope Extended RTS Pass | `TST-FIL-017.2` | `ExtendedRtsAltitudeSlopeSmoother.kt` | Specified |
| `REQ-FIL-017.3` | Strict Column Scope Invariant | `TST-FIL-017.3` | `ErtsPostWorkoutRecomputer.kt` | Specified |
| `REQ-FIL-017.4` | Workout Summaries & Stream Synchronization | `TST-FIL-017.3` | `WorkoutSummariesDatabaseManager.java` | Specified |
| `REQ-FIL-017.5` | Expert Settings Opt-In Configuration | `TST-FIL-017.4`, `TST-FIL-017.5` | `TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt` | Specified |
