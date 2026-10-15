# Stage 1 Analysis: ATT-45 - Sport-specific auto-pause with configurable speed thresholds and debounce filtering

**Ticket**: [ATT-45](https://rainerblind.atlassian.net/browse/ATT-45)  
**Sub-task**: [ATT-3625](https://rainerblind.atlassian.net/browse/ATT-3625) (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-45`  
**Requirement Mapping**: `REQ-TRK-003` (complementing `REQ-TRK-001`, `REQ-TRK-002`, `REQ-FIL-012`, `REQ-FIL-013`, and `REQ-UI-262`)  
**Test Mapping**: `TST-TRK-003`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

When athletes stop at traffic lights, rail crossings, intersections, or rest stops during outdoor cycling or running workouts, recording elapsed time continuously dilutes average speed and pace calculations. In addition, stationary GPS drift often accumulates artificial distance, erratic trackpoints, and phantom climbing gain during standstill.

While athletes can pause manually, remembering to pause and resume at every junction causes cognitive friction, and forgetting to resume leads to lost activity data.

Introducing an automated **Auto-Pause Engine** (`AutoPauseManager.kt`) that halts active recording when stationary and instantly resumes upon departure preserves pristine telemetry statistics without manual intervention.

### Key Objectives & Invariants:
1. **Sport-Specific Default Activation**:
   - Enabled by default for Cycling (`BSportType.BIKE`).
   - Disabled by default for Running (`BSportType.RUN`) and Other sports (`BSportType.UNKNOWN`), since runners frequently perform standing recovery intervals or track drills where automated pausing would disrupt structured pacing.
2. **Anti-Chatter Hysteresis Deadband & Debounce Filtering**:
   - Configurable speed threshold $v_{\text{pause}}$ ($0.5 \dots 5.0\text{ km/h}$, default $1.5\text{ km/h}$) and pause debounce duration ($1 \dots 5\text{ seconds}$, default $2\text{ seconds}$).
   - **Hysteresis Deadband**: To prevent rapid pause/resume oscillation (chattering) when GPS speed fluctuates near the threshold boundary ($1.4 \dots 1.6\text{ km/h}$), resumption strictly requires $v_{\text{resume}} \ge v_{\text{pause}} + 0.5\text{ km/h}$ (or $\ge 2.0\text{ km/h}$ for the default $1.5\text{ km/h}$ threshold) sustained for a confirmation tick ($1\text{ second}$).
   - **Numerical Sanitization**: Any non-finite speed values (`Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`) are sanitized and rejected prior to evaluation.
3. **Stale Data Guard (5-Second Packet TTL) & Direct High-Priority Evaluation**:
   - **Stale Packet Rejection**: The engine rejects sensor packets where `System.currentTimeMillis() - packet.timestamp > 5000ms`. If the newest speed packet is older than 5 seconds, the engine treats the feed as stale/disconnected and enters `SENSOR_LOST` rather than falsely assuming stationary 0 km/h.
   - **Speed Hierarchy & Direct Listener**: The engine directly accesses `BANALService.getBestSensorData(SensorType.SPEED_mps)` on the primary 1Hz sampling tick. High-precision hardware wheel sensors (ANT+/BLE) take primary precedence, falling back to GPS speed (`GPS_SPEED` / Location provider).
   - **Stationary vs Signal Loss**:
     - *Stationary*: Periodic location/sensor updates continue arriving with low speed ($v < v_{\text{pause}}$) and valid location accuracy ($\le 30\text{ m}$). This evaluates as stationary movement and triggers auto-pause.
     - *Sensor Lost*: When NO valid speed or location packets arrive from any provider within the 5-second TTL, the engine transitions to `SENSOR_LOST`. Auto-pause is suspended (preventing false standstill assumptions), current recording state is maintained, and an amber "Speed Sensor Lost" warning is rendered in the cockpit.
     - *Telemetry Silence Contract*: While in `SENSOR_LOST`, the `WorkoutSamples` database remains strictly **silent** (no synthetic "No Signal" markers or dummy trackpoints inserted), preserving clean FIT/GPX exports for third-party analysis tools.
     - *Recovery Contract*: When valid speed readings resume and remain stable for $\ge 2$ consecutive seconds, `AutoPauseManager` transitions from `SENSOR_LOST` back to `TRACKING` and re-engages regular stationary speed evaluation.
4. **Synchronous Hierarchical Gating & Elevation Drift Protection**:
   - **Sequential Gating Order**: In `VerticalSpeedAndSlopeDevice`, `MyAccumulatorSensor<Double>` (for `SensorType.DISTANCE`), and `LocationManager` callbacks, gating executes sequentially in the sensor ingestion thread:
     1. `if (TrainingApplication.isPaused()) return;` (enforcing manual pause priority).
     2. `if (AutoPauseManager.isAutoPaused()) return;` (enforcing auto-pause gating).
   - Gating is 100% synchronous in memory, preventing any elevation drift or ghost trackpoints before the database persistence completes.
   - Horizontal distance accumulation and vertical elevation gain/loss (`mAscentSensor`, `mDescentSensor`) immediately halt.
   - `ElevationProfile` path-point logging and `WorkoutSamples` database writes are suspended during `AUTO_PAUSED`.
   - Authoritative average speed calculation in `WorkoutSummaries` strictly evaluates $v_{\text{avg}} = \frac{\text{Distance}}{\text{TimeActive}}$ (moving time), completely uncorrupted by stationary pause duration.
5. **Strict Mutex Enforcement (`TrackingLock`), Persistence & Race-Free Reset**:
   - An explicit manual pause sets an immutable `mManualPauseLock = true` in `TrainingApplication`. While `mManualPauseLock` is active, `AutoPauseManager` is strictly blocked from issuing auto-resume events.
   - **Lock Persistence**: `mManualPauseLock` is immediately persisted to `DataStore`, surviving process death or background OS killing.
   - **Centralized Lock Reset**: `mManualPauseLock` is cleared (`mManualPauseLock = false`) and persisted whenever ANY explicit user resume action is dispatched (in-app Cockpit button, "Resume Now" badge button, or background notification action). All actions funnel through `TrainingApplication.resumeFromPaused()`, eliminating race conditions between broadcast receivers and the background state machine.
6. **Strictly Ordered FIFO Asynchronous Flush & Crash Invariants**:
   - **Single-Threaded FIFO Queue**: `mDbExecutor` in `TrackerService` is a dedicated single-threaded executor. The pre-pause task enqueues the final stationary `WorkoutSample` and accumulator snapshots immediately followed by the summary paused-state update. Because `mDbExecutor` is FIFO, the final moving sample is guaranteed to be persisted *before* the paused state is committed, completely preventing out-of-order writes.
   - **Crash Recovery during Debounce**: The `debounceCounter` is transient in-memory. If process death occurs while in `PAUSE_DEBOUNCE_PENDING`, the counter is reset to 0 and the restored session strictly defaults to `TRACKING` (active recording). It NEVER restores into `PAUSE_DEBOUNCE_PENDING`, eliminating false-positive pauses upon recovery.
7. **Crash-Recovery & State Synchronization**:
   - An in-memory `AtomicBoolean isAutoPaused` provides zero-latency state checks for sensor ingestion.
   - State changes asynchronously persist to `DataStore`. If an abrupt crash occurs before `DataStore` flush finishes, `TrackerService` startup reconciliation examines the last timestamped `WorkoutSample` in SQLite: if sample streaming ceased, the session safely restores in `AUTO_PAUSED` state without "zombie" UI discrepancies. In-memory sensor accumulators are re-initialized with the last persisted totals from `WorkoutSummaries` to avoid value jumping/spikes upon resume.

---

## 2. Forensic Architectural Investigation & Call-Site Audit

### 2.1 Existing Pause / Resume Lifecycle
In `aTrainingTracker`:
1. `TrainingApplication.java`:
   - `cTrackingMode = TrackingMode.PAUSED` via `pauseTracking()`.
   - `cTrackingMode = TrackingMode.TRACKING` via `resumeFromPaused()`.
   - `TrainingApplication.isPaused()` is defined as `cTrackingMode != TrackingMode.TRACKING`.
   - `TrainingApplication.isPaused()` is queried by sensors (`MyAccumulatorSensor.java`, `AverageFilter.java`, `MaxValueFilter.java`, `ClockDevice.java`).
2. `TrackerService.java:1050`:
   - When `TrainingApplication.isPaused()` is true, `sampleAndWriteToDb()` updates only `WorkoutSummaries.TIME_TOTAL_s`, skipping trackpoint insertion into `WorkoutSamples`, skipping distance accumulation, and skipping extrema/average recalculations.
3. `VerticalSpeedAndSlopeDevice.java`:
   - `mAscentSensor` and `mDescentSensor` are instantiated as `new MyDoubleAccumulatorSensor(this, SensorType.ASCENT, true)`.
   - The parameter `mRespectPause = true` ensures `increment()` drops elevation deltas while `TrainingApplication.isPaused()`.
4. `BANALServiceRepository.kt`:
   - Listens to `TrainingApplication.TRACKING_STATE_CHANGED_INTENT` and posts updates to `trackingMode: LiveData<TrackingMode>`.
5. `ControlTrackingViewModel.kt` / `ControlTrackingButton.kt`:
   - Dispatches `REQUEST_PAUSE_TRACKING` and `REQUEST_RESUME_FROM_PAUSED` on manual button clicks.

### 2.2 Architectural Gaps for Auto-Pause
1. **Lack of Mutex / TrackingLock**:
   - Currently, `TrainingApplication` only tracks `TrackingMode.PAUSED` without distinguishing manual user pause from automated pause.
   - Solution: Implement `mManualPauseLock` in `TrainingApplication` ensuring auto-resume never overrides a human decision. Persist lock to `DataStore` and reset on any explicit user resume action.
2. **Missing Auto-Pause Engine (`AutoPauseManager.kt`)**:
   - No periodic evaluation of instantaneous speed against debounce and hysteresis thresholds exists.
3. **Missing Sport-Specific Auto-Pause Preferences in DataStore**:
   - `TuningPreferencesDataStore.kt` contains GPS and filtering settings, but lacks keys for:
     - `tuning_auto_pause_bike_enabled` (default `true`)
     - `tuning_auto_pause_run_enabled` (default `false`)
     - `tuning_auto_pause_other_enabled` (default `false`)
     - `tuning_auto_pause_speed_threshold_kmh` (default `1.5f`)
     - `tuning_auto_pause_debounce_sec` (default `2`)
4. **Missing UI Tuning Controls in `SensorsGpsFilterSection.kt`**:
   - Needs sliders for auto-pause threshold and debounce window, and sport toggles.
5. **Missing Cockpit Indicator Badge, Override Button & Audio Chime**:
   - `SensorGridScreen.kt` needs an amber "Auto-Pause" pill banner with a "Resume Now" button, a "Speed Sensor Lost" banner, and audio feedback via `ToneGenerator`.

---

## 3. Scope Bounding (In-Scope vs Out-of-Scope)

### 3.1 In Scope
1. **Auto-Pause Engine (`AutoPauseManager.kt`)**:
   - State machine tracking `TRACKING`, `PAUSE_DEBOUNCE_PENDING`, `AUTO_PAUSED`, `MANUAL_PAUSED`, and `SENSOR_LOST`.
   - Speed hierarchy: Hardware BLE/ANT+ wheel speed -> GPS speed.
   - Stale packet rejection ($> 5000\text{ ms}$).
   - Debounce counter tracking consecutive ticks below threshold $v_{\text{pause}}$ before pausing; resets to `TRACKING` if crashed.
   - Hysteresis resume check requiring $v \ge v_{\text{pause}} + 0.5\text{ km/h}$ for 1 second.
   - Numerical sanitization rejecting `NaN`/`Infinity`.
   - `TrackingLock` mutex persisted to `DataStore`, preventing auto-resume while user has manually paused, cleared on manual resume.
   - Synchronous sequential gating in sensor ingestion loops: manual pause check followed by auto-pause check.
   - Telemetry silence contract in `SENSOR_LOST`.
   - Crash-recovery state persistence backed by `DataStore` with accumulator DB re-initialization.
   - Strictly ordered FIFO asynchronous flush dispatched to single-threaded `mDbExecutor`.
2. **DataStore Persistence (`TuningPreferencesDataStore.kt`)**:
   - Add sport-specific toggles: `autoPauseBikeEnabled`, `autoPauseRunEnabled`, `autoPauseOtherEnabled`.
   - Add tuning thresholds: `autoPauseSpeedThresholdKmh`, `autoPauseDebounceSec`.
   - Synchronous access bridge in `SettingsDataStoreJavaHelper.kt`.
3. **Advanced Settings UI (`SensorsGpsFilterSection.kt` / `AdvancedTuningScreen.kt`)**:
   - Sliders and sport toggles with standard formatters and reset-to-defaults integration.
4. **Cockpit UI & Audio/Haptic Feedback (`SensorGridScreen.kt` / `ToneGenerator`)**:
   - Contextual amber "Auto-Pause" pill badge with "Resume Now" manual override button.
   - Contextual "Speed Sensor Lost" warning when in `SENSOR_LOST`.
   - Audio tone generation via `ToneGenerator` on pause/resume events.
5. **Localization (9 Languages)**:
   - Parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 3.2 Out of Scope
- Auto-pause based on heart rate or cadence alone (speed is the authoritative stationary indicator).
- Auto-stop (stopping activity completely after N hours of pause) — deferred to dedicated ticket.

---

## 4. State Machine & Event Flow with Hysteresis, Mutex & SENSOR_LOST

```
                        [State: TRACKING]
                                │
                                ├── (Packet age > 5s OR Sensor Dropout) ──► [State: SENSOR_LOST]
                                │                                           (Pause evaluation suspended)
                                │                                           (WorkoutSamples database: SILENT)
                                │                                           (Banner: "Speed Sensor Lost")
                                │                                           (When sensor stable >= 2s ──► TRACKING)
                                │
                                ├── (User taps Manual Pause) ─────────────► [mManualPauseLock = true (persisted)]
                                │                                           [State: MANUAL_PAUSED]
                                │
                                │ (speed < v_pause AND packet valid < 5s)
                                ▼
                [State: PAUSE_DEBOUNCE_PENDING]
                (counter: 1..debounceSec)
                        ├── (speed >= v_pause) ───────────────────────────► [Reset counter, Return to TRACKING]
                        │
                        ├── (Packet age > 5s) ────────────────────────────► [Reset counter, Go to SENSOR_LOST]
                        │
                        └── (counter >= debounceSec)
                                │
                                ▼
                        [Synchronous in-memory gating engaged: isAutoPaused = true]
                        [Enqueue FIFO Final Sample & Summary update to mDbExecutor]
                        [Play Chime / Audio Alert]
                        [State: AUTO_PAUSED]
                                │
                                ├── (User taps Manual Pause or Stop) ───────► [mManualPauseLock = true (persisted)]
                                │                                             [isAutoPaused = false]
                                │                                             [State: MANUAL_PAUSED / STOPPED]
                                │
                                ├── (User taps "Resume Now" / Resume) ──────► [mManualPauseLock = false (persisted)]
                                │                                             [isAutoPaused = false]
                                │                                             [State: TRACKING]
                                │
                                └── (speed >= v_pause + 0.5 km/h for 1s AND !mManualPauseLock)
                                        │
                                        ▼
                                [Trigger Auto-Resume: isAutoPaused = false]
                                [Play Resume Chime]
                                [State: TRACKING]
```

### Crash Recovery & Accumulator Re-initialization
```
[App Crash / Process Death while AUTO_PAUSED]
                       │
                       ▼
             [App Restart / Recovery]
                       │
             [Restore State: AUTO_PAUSED via DataStore / DB reconciliation]
             [Restore mManualPauseLock from DataStore]
             [Re-initialize Accumulators from WorkoutSummaries]
             [Display Crash Recovery Banner with Resume/Stop options]
```

---

## 5. Acceptance Criteria

- [ ] When `BSportType.BIKE` is active, Auto-Pause is enabled by default. For `BSportType.RUN` and `UNKNOWN`, disabled by default.
- [ ] Dropping below configured speed threshold $v_{\text{pause}}$ for `debounceSec` transitions session to Auto-Pause.
- [ ] **Anti-Chatter Invariant**: Speed oscillating around the threshold ($1.4 \dots 1.6\text{ km/h}$) does NOT cause rapid pause/resume chattering due to the $0.5\text{ km/h}$ hysteresis deadband and debounce timer.
- [ ] **Sequential Synchronous Ingestion Gating**: `VerticalSpeedAndSlopeDevice`, `MyAccumulatorSensor`, and `LocationManager` check manual `isPaused()` followed by `isAutoPaused()` synchronously within the ingestion thread, completely preventing elevation and distance drift before the asynchronous DB flush.
- [ ] **Strict FIFO Single-Threaded Flush**: Final stationary sample and pause state updates are posted to single-threaded `mDbExecutor`, ensuring strict in-order persistence without race conditions.
- [ ] **Crash Debounce Invariant**: Crash during `PAUSE_DEBOUNCE_PENDING` resets debounce counter and restores to `TRACKING`, preventing false-positive pauses upon restart.
- [ ] **Stale Data Guard (5-Second TTL)**: Speed packets older than 5000ms are rejected; signal outage transitions to `SENSOR_LOST` and suspends auto-pause; when stable speed ($\ge 2\text{ s}$) returns, transitions back to `TRACKING`.
- [ ] **Telemetry Silence in SENSOR_LOST**: No dummy trackpoints or synthetic markers are inserted into `WorkoutSamples` during `SENSOR_LOST`.
- [ ] **Speed Hierarchy & Sanitization**: Hardware wheel speed takes precedence over GPS speed; non-finite (`NaN`/`Inf`) values are discarded.
- [ ] **Elevation Invariant & Drift Protection**: Moving time (`TIME_ACTIVE`), distance accumulation, elevation gain/loss accumulation (`mAscentSensor`, `mDescentSensor`), and `ElevationProfile` trackpoint logging halt completely during Auto-Pause.
- [ ] Authoritative average speed evaluates $v_{\text{avg}} = \frac{\text{Distance}}{\text{TimeActive}}$ without pause dilution.
- [ ] Exceeding $v_{\text{pause}} + 0.5\text{ km/h}$ resumes recording immediately without manual screen touch.
- [ ] **Mutex Precedence, Persistence & Lock Reset (`TrackingLock`)**: Manual pause sets `mManualPauseLock = true` (persisted to DataStore). Auto-resume is strictly prohibited from un-pausing while the manual lock is held. Any explicit user resume clears and persists `mManualPauseLock = false`.
- [ ] Cockpit renders amber pill badge with "Resume Now" manual override button.
- [ ] Unexpected process death while auto-paused restores the session in `AUTO_PAUSED` state and restores `mManualPauseLock`, re-initializing accumulators from `WorkoutSummaries` to prevent value spikes.
- [ ] Advanced settings allow configuring speed threshold (0.5..5.0 km/h) and debounce window (1..5 sec).
- [ ] Full 9-language localization parity and 100% clean-room test pass rate.
