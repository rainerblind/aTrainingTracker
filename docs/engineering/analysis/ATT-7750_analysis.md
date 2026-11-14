# ATT-7750: Stage 1 Analysis — Extended Rauch-Tung-Striebel (ERTS/RTS) Smoother

## 1. Problem Domain & Root Cause Analysis (RCA) / Gap Analysis
### 1.1 Problem Domain: Causal Phase Lag & GNSS Outage Artifacts in Live Filtering
During real-time workout tracking, online state-estimation filters (such as the 5-state `UnicycleOdometryEkf` from ATT-131 / ATT-7751 and the 2-state/3-state `KinematicAltitudeSlopeEkf` from ATT-6279) are inherently causal: they operate strictly under the information constraint $t \le k$.
- **2D Trajectory & Speed Outage Snapping**: During temporary GNSS outages (e.g. traversing curved tunnels, underpasses, dense tree canopies, or urban canyons), the live EKF integrates forward using wheel speed $\Delta s_{wheel}$ and quaternion-projected world yaw-rate $\omega_{ENU,z}$. Because IMU bias and minor calibration tolerances persist, a slight dead-reckoning drift (3–8 m) accumulates over 30–60 s. When open-sky GNSS lock is reacquired at the tunnel exit, the causal filter must reconcile this innovation, producing an abrupt exit step or transient trajectory kink.
- **Elevation & Slope Startup Transients**: Causal barometric/DEM altitude fusion experiences initial convergence delays (e.g., GPS vertical accuracy stabilization and initial DEM altitude lookup during the first 30–60 s). Furthermore, live causal low-pass or Kalman filtering introduces slight temporal delay in grade estimation relative to true spatial inflection points.
- **Post-Workout Full Information Availability**: Once the user stops tracking (`STOP_TRACKING` / workout finalization), the entire discrete time series ($k = 0 \dots N$) is preserved in `WorkoutSamples.db`. By executing a post-workout zero-phase Rauch-Tung-Striebel (ERTS/RTS) backward smoothing pass over the recorded trajectory, future measurements $k > t$ are propagated backward in time. Tunnel-exit and post-canopy GNSS fixes eliminate dead-reckoning divergence without rounding genuine hairpins, producing seamless, kinematically self-consistent trajectories and elevation/grade profiles.

### 1.2 Mathematical Formulation & Algorithmic Design
#### 1.2.1 2D Odometry ERTS Backward Pass (Reusing ATT-131 Model)
The forward filter executes the 5-state unicycle kinematic model:
$$x_k = \begin{bmatrix} E_k \\ N_k \\ v_k \\ \psi_k \\ b_{\omega_z, k} \end{bmatrix}$$
with state transition Jacobian $F_k = \frac{\partial f}{\partial x}\Big|_{x_{k|k}}$ and process noise $Q_k$.
During the forward replay of recorded samples, the filter records step snapshots:
$$\{x_{k|k-1}, P_{k|k-1}, F_{k-1}, x_{k|k}, P_{k|k}\}$$
The backward RTS recursion runs for $k = N-1, N-2, \dots, 0$:
$$C_k = P_{k|k} F_k^T (P_{k+1|k})^{-1}$$
$$x_{k|N} = x_{k|k} + C_k \left(x_{k+1|N} - x_{k+1|k}\right)$$
$$P_{k|N} = P_{k|k} + C_k \left(P_{k+1|N} - P_{k+1|k}\right) C_k^T$$
where the angular difference $\psi_{k+1|N} - \psi_{k+1|k}$ is normalized to $[-\pi, \pi)$ via `normalizeAngle`.
The resulting smoothed states yield zero-phase coordinates ($E_{k|N}, N_{k|N}$), forward speed ($v_{k|N}$), and bearing ($\psi_{k|N}$).

#### 1.2.2 Altitude & Kinematic Slope RTS Backward Pass (Reusing ATT-6279 Model)
The vertical/slope engine operates on:
$$x_{alt, k} = \begin{bmatrix} h_k \\ g_k \\ b_{baro, k} \end{bmatrix} \quad \text{or in 2-State mode} \quad \begin{bmatrix} h_k \\ g_k \end{bmatrix}$$
driven by horizontal step displacement $\Delta s_{k|N} = v_{k|N} \Delta t_k$ computed directly from the smoothed odometry pass:
$$h_k = h_{k-1} + g_{k-1} \Delta s_{k|N}$$
State transition matrix:
$$F_{alt, k} = \begin{bmatrix} 1 & \Delta s_{k|N} & 0 \\ 0 & 1 & 0 \\ 0 & 0 & 1 \end{bmatrix}$$
Backward smoothing computes $x_{alt, k|N}$, yielding zero-phase altitude $h_{k|N}$, terrain slope $g_{k|N}$, and vertical speed $v_{z, k} = \frac{h_{k|N} - h_{k-1|N}}{\Delta t_k}$.

#### 1.2.3 Strict Column Scope Invariant
In accordance with Epic ATT-166 governance:
1. **Canonical Columns Only**: Post-workout smoothing writes *exclusively* to canonical unsuffixed columns in `WorkoutSamples.db`:
   `LATITUDE`, `LONGITUDE`, `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `BEARING`, `ALTITUDE`, `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT`.
2. **Device Columns Untouched**: Every device-attributed column (e.g. `LATITUDE (GPS)`, `SPEED_mps (GPS)`, `SPEED_mps (Google Fused)`, `SPEED_mps (att_fused)`, `ALTITUDE (Höhe aus Luftdruck)`, `ALTITUDE (Topografische Höhe (DEM))`, `ALTITUDE (att_fused_altitude_slope)`) remains 100% byte-for-byte identical to the raw live recording.
3. This guarantees that post-workout analysis and offline evaluation (ATT-168) can directly benchmark raw sensor recordings against causal fused estimates and post-workout ERTS smoothed metrics side-by-side.

---

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)
### 2.1 Live Codebase Verification
An independent audit of the current codebase confirms:
- **Snapshot Infrastructure Exists**: `UnicycleOdometryEkf.kt` already defines `OdometrySnapshot` and stores prior/posterior states and Jacobians. However, `commitSnapshot()` is currently called on both prediction and measurement update steps. For the discrete RTS smoother step $k \to k+1$, snapshots must cleanly represent step $k$ posterior and step $k+1$ prior transition. `ExtendedRtsOdometrySmoother` can either ingest a dedicated step snapshot sequence or run a self-contained forward-backward replay pass over the raw sample points.
- **Kinematic Coupled Motion Model Ready**: `KinematicAltitudeSlopeEkf.kt` implements exact 2-State and 3-State formulation with standstill clamping. Replaying this filter using smoothed horizontal speed increments $\Delta s_{k|N}$ guarantees tight spatial coupling without artificial climb accumulation during stops.
- **Tuning Preference Integration**: `AdvancedTuningScreen.kt` and `SensorsGpsFilterSection.kt` provide the canonical UI container for sensor and filter settings. Adding a `TuningToggleItem` for post-workout ERTS smoothing aligns with established UX conventions.
- **Lifecycle Integration**: `TrackerService.java` currently executes post-workout recomputation (`HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(...)`) asynchronously on a worker thread before refreshing the workout repository. Running `ErtsPostWorkoutRecomputer` within this asynchronous completion pipeline ensures non-blocking UI behavior while ensuring summary and polyline updates complete prior to user export triggers.

---

## 3. Chesterton's Fence Archaeology & Requirement Evolution
- **Historical Requirements**:
  - `REQ-TRK-010` / `REQ-TRK-030`: Workout finalization lifecycle, `FINISHED = 1` transaction isolation, and asynchronous post-processing.
  - `REQ-FIL-007` / `REQ-FIL-009`: Multi-sensor unicycle odometry fusion (ATT-131).
  - `REQ-FIL-016`: Kinematic terrain-slope altitude fusion (ATT-6279).
  - `REQ-SET-080`: Advanced tuning preferences in `TuningPreferencesDataStore.kt`.
- **Fence Rationale**:
  Prior implementations relied strictly on causal live filters because memory and CPU constraints on older Android devices discouraged dual-pass processing. Modern mobile hardware easily computes 14,400 5x5 matrix operations in under 30 ms.
- **Invariant Preservation**:
  - Disabled by default (`postWorkoutErtsSmoothingEnabled = false`) to preserve standard baseline behavior unless explicitly enabled in Expert Settings.
  - Live sensor attribution columns remain unmodified, preventing loss of forensic telemetry.

---

## 4. Side-Effect & Cross-Caller Impact Analysis (Rule 68)
- **Database Concurrency & Transaction Safety**: Post-workout smoothing executes in a single SQLite transaction on `WorkoutSamples.db`, avoiding table lock contention.
- **Workout Summaries Parity**: When canonical coordinates and elevations are smoothed, `WorkoutSummariesDatabaseManager.updateMapAndStreams` updates the encoded map polyline, distance stream, and elevation stream, ensuring map previews and analytics reflect the smoothed trajectory.
- **Export & Third-Party Sync**: Because export occurs asynchronously after finalization, smoothed canonical metrics are automatically reflected in GPX, TCX, and FIT exports when the option is enabled.
- **Zero Live Overhead**: When running during a workout, ERTS post-processing does not execute; CPU and battery impact during tracking is exactly 0%.

---

## 5. Scope Bounding & Proposed Architecture
### 5.1 In-Scope Deliverables
1. **Core Smoother Engine (`ExtendedRtsOdometrySmoother.kt`)**: Pure Kotlin forward-backward RTS smoother for 5-state unicycle odometry and 2-state/3-state altitude/slope models with zero external matrix libraries.
2. **Post-Workout Recomputation Runner (`ErtsPostWorkoutRecomputer.kt`)**: Orchestrates sample query from `WorkoutSamples.db`, forward-backward execution, canonical column updates, and summary stream re-encoding.
3. **Lifecycle Hook in `TrackerService.java`**: Integrates ERTS smoothing into post-workout background finalization, guarded by the Expert Settings toggle.
4. **Settings & UI Integration**: Adds `KEY_POST_WORKOUT_ERTS_SMOOTHING` to `TuningPreferencesDataStore.kt`, wire toggle in `SensorsGpsFilterSection.kt` and `AdvancedTuningScreen.kt` with 9-language localization.
5. **Living Documentation & Governance**: Net-new requirement `REQ-FIL-017` and test specification `TST-FIL-017` in living docs, with 100% index parity.

### 5.2 Out-of-Scope (Guarded Against Feature Creep)
- Modifying live causal filter algorithms in `UnicycleOdometryEkf` or `KinematicAltitudeSlopeEkf`.
- Modifying raw or per-device columns in `WorkoutSamples.db`.
- Automatic offline replay during active live workouts.
