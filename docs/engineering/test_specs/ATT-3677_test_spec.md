# Stage 2 Requirement & Test Specification: ATT-3677 - Configure auto-pause speed thresholds and debounce windows independently per sport (Bike, Run, Other)

**Ticket**: [ATT-3677](https://rainerblind.atlassian.net/browse/ATT-3677)  
**Sub-task**: [ATT-3693](https://rainerblind.atlassian.net/browse/ATT-3693) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `None`  
**Active Sprint**: `Sprint 2026-42.5`  
**Branch**: `feature/ATT-3677`  
**Requirement Mapping**: `REQ-TRK-018` (extending `REQ-TRK-001`, `REQ-TRK-002`, `REQ-FIL-012`, `REQ-FIL-013`, and `REQ-UI-262`)  
**Test Mapping**: `TST-TRK-010`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. System Requirement Specification (`REQ-TRK-018`)

### REQ-TRK-018: Sport-Specific Auto-Pause Engine with Anti-Chatter Hysteresis, Mutex Precedence, Sensor Dropout Guard, Synchronous Ingestion Gating, and Sport-Decoupled Thresholds

The system SHALL provide an automated, sport-specific Auto-Pause Engine (`AutoPauseManager.kt`) that suspends active recording when an athlete becomes stationary and seamlessly resumes recording upon departure, maintaining strict metric integrity, crash resilience, and manual user sovereignty (ATT-45, ATT-3677, extending `REQ-TRK-001` and `REQ-TRK-002`, complementing `REQ-FIL-012`, `REQ-FIL-013`, and `REQ-UI-262`):

1. **Sport-Specific Default Activation**:
   - The system SHALL enable Auto-Pause by default for Cycling (`BSportType.BIKE`).
   - The system SHALL disable Auto-Pause by default for Running (`BSportType.RUN`) and Other sports (`BSportType.UNKNOWN`), ensuring interval workouts and track drills are never inadvertently paused.
   - Sports-specific activation SHALL be independently configurable via `TuningPreferencesDataStore`.

2. **Sport-Decoupled Thresholds, Integer Configuration, Anti-Chatter Hysteresis Deadband & Hard Ceilings (`ATT-3677`)**:
   - The system SHALL provide independent Speed Thresholds and Debounce Windows per sport (`Bike`, `Run`, `Other`), configured strictly as integer (`Int`) values in `TuningPreferencesDataStore`:
     - **Cycling (`BSportType.BIKE`)**:
       - Speed threshold $1 \dots 5$ in user speed unit ($1 \dots 5\text{ km/h}$ Metric, $1 \dots 5\text{ mph}$ Imperial), step $1$, default $3\text{ km/h}$ / $2\text{ mph}$.
       - Debounce window $1 \dots 10\text{ s}$, step $1\text{ s}$, default $2\text{ s}$.
     - **Running (`BSportType.RUN`)**:
       - Speed threshold $1 \dots 5$ in user speed unit ($1 \dots 5\text{ km/h}$ Metric, $1 \dots 5\text{ mph}$ Imperial), step $1$, default $2\text{ km/h}$ / $1\text{ mph}$.
       - Debounce window $1 \dots 20\text{ s}$, step $1\text{ s}$, default $5\text{ s}$.
     - **Other (`BSportType.UNKNOWN`)**:
       - Speed threshold $1 \dots 5$ in user speed unit ($1 \dots 5\text{ km/h}$ Metric, $1 \dots 5\text{ mph}$ Imperial), step $1$, default $1\text{ km/h}$ / $1\text{ mph}$.
       - Debounce window $1 \dots 60\text{ s}$, step $1\text{ s}$, default $10\text{ s}$.
   - **User Interface Integration**: Sliders in `SensorsGpsFilterSection.kt` SHALL be rendered conditionally directly below each sport's auto-pause toggle (collapsible when enabled) and formatted using dynamic speed units ($v\text{ km/h}$ or $v\text{ mph}$) via `MyHelper.getShortUnitsId(SensorType.SPEED_mps)`.
   - **Hysteresis Deadband & Ceiling**: Resumption strictly requires $v_{\text{resume}} \ge \min(v_{\text{pause}} + 0.5\text{ [user unit]}, v_{\text{ceiling}})$ sustained for a confirmation tick ($1\text{ second}$). The upper ceiling is clamped to $6.0\text{ km/h}$ in Metric ($1.667\text{ m/s}$) or $4.0\text{ mph}$ in Imperial, ensuring a deterministic and reachable threshold even at maximum threshold, while suppressing chattering near the pause threshold.
   - **Numerical Sanitization**: Any non-finite speed values (`Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`) SHALL be sanitized and rejected prior to evaluation.

3. **Speed Hierarchy, 5-Second Stale Packet TTL, SENSOR_LOST State & Audit Trail**:
   - Speed Hierarchy: The engine consumes `BANALService.getBestSensorData(SensorType.SPEED_mps)`. High-precision hardware wheel sensors (ANT+/BLE) take primary precedence. If no hardware sensor is connected, the engine seamlessly falls back to GPS speed (`GPS_SPEED` / Location provider).
   - Stale Packet Rejection: The engine rejects sensor packets where `System.currentTimeMillis() - packet.timestamp > 5000ms`. If the newest speed packet is older than 5 seconds, the engine treats the feed as stale/disconnected and transitions to `SENSOR_LOST` rather than falsely assuming stationary 0 km/h.
   - Stationary vs Signal Loss: Periodic location/sensor updates arriving with low speed ($v < v_{\text{pause}}$) and valid accuracy ($\le 30\text{ m}$) evaluate as stationary and trigger auto-pause. Total signal outage ($> 5\text{ s}$) transitions to `SENSOR_LOST`, suspends auto-pause, and maintains recording state.
   - State Change Audit Logging: When transitioning between `TRACKING`, `AUTO_PAUSED`, and `SENSOR_LOST`, the system SHALL persist a state-transition event record in SQLite session metadata for full auditability, while keeping the continuous `WorkoutSamples` trackpoint stream free of synthetic coordinate/speed spikes to preserve clean FIT/GPX exports.
   - Recovery Contract: When valid speed readings resume and remain stable for $\ge 2$ consecutive seconds, `AutoPauseManager` transitions from `SENSOR_LOST` back to `TRACKING` and re-engages regular stationary speed evaluation.

4. **Synchronous Hierarchical Gating & Elevation Drift Protection**:
   - In `VerticalSpeedAndSlopeDevice`, `MyAccumulatorSensor<Double>` (for `SensorType.DISTANCE`), and `LocationManager` callbacks, gating executes sequentially in the sensor ingestion thread: first `if (TrainingApplication.isPaused()) return;` (enforcing manual pause priority), then `if (AutoPauseManager.isAutoPaused()) return;` (enforcing auto-pause gating).
   - Gating is 100% synchronous in memory, preventing any elevation drift or ghost trackpoints before the database persistence completes.
   - Horizontal distance accumulation and vertical elevation gain/loss (`mAscentSensor`, `mDescentSensor`) immediately halt. `ElevationProfile` path-point logging and `WorkoutSamples` database writes are suspended during `AUTO_PAUSED`.
   - Authoritative average speed in `WorkoutSummaries` strictly evaluates $v_{\text{avg}} = \frac{\text{Distance}}{\text{TimeActive}}$ (moving time), completely uncorrupted by stationary pause duration.

5. **Strict Mutex Enforcement (TrackingLock), Persistence & Race-Free Reset**:
   - An explicit manual pause sets `mManualPauseLock = true` in `TrainingApplication`. While `mManualPauseLock` is active, `AutoPauseManager` is strictly blocked from issuing auto-resume events.
   - Lock Persistence: `mManualPauseLock` is immediately persisted to `DataStore`, surviving process death or background OS killing.
   - Centralized Lock Reset: `mManualPauseLock` is cleared (`mManualPauseLock = false`) and persisted whenever ANY explicit user resume action is dispatched (in-app Cockpit button, "Resume Now" badge button, or background notification action). All actions funnel through `TrainingApplication.resumeFromPaused()`.

6. **Strictly Ordered FIFO Asynchronous Flush & Crash Invariants**:
   - Single-Threaded FIFO Queue: `mDbExecutor` in `TrackerService` enqueues the final stationary `WorkoutSample` and accumulator snapshots immediately followed by the summary paused-state update, guaranteeing strict in-order persistence.
   - Crash Recovery during Debounce: The `debounceCounter` is transient in-memory. If process death occurs while in `PAUSE_DEBOUNCE_PENDING`, the counter is reset to 0 and the restored session strictly defaults to `TRACKING` (active recording).

7. **Deterministic Startup State-Recovery Hierarchy**:
   - Upon application restart or crash recovery in `TrackerService`:
     1. Tier 1 (User Sovereignty): If `mManualPauseLock == true` in DataStore, the session MUST restore in `MANUAL_PAUSED` state regardless of the last `WorkoutSample` velocity. The user's explicit manual intent strictly overrides telemetry samples.
     2. Tier 2 (Auto-Paused): If `mManualPauseLock == false` and `isAutoPaused == true`, the session restores in `AUTO_PAUSED`. Sensor evaluation is re-engaged. If immediate fresh speed readings arrive exceeding $v_{\text{resume}}$, the engine smoothly transitions to `TRACKING`.
     3. Tier 3 (Active Tracking): If both flags are false, the session restores to `TRACKING`.
   - In-memory sensor accumulators are re-initialized with the last persisted totals from `WorkoutSummaries` to avoid value jumping/spikes upon resume.

8. **Cockpit UI & Audio/Haptic Feedback**:
   - When in `AUTO_PAUSED` state, `SensorGridScreen.kt` renders an amber pill badge displaying "Auto-Pause" along with an immediate "Resume Now" button.
   - When in `SENSOR_LOST` state, `SensorGridScreen.kt` renders an amber pill badge displaying "Speed Sensor Lost".
   - Tapping "Resume Now" clears `mManualPauseLock`, sets `isAutoPaused = false`, and immediately forces the session back to `TRACKING`. Audio tones are emitted on pause/resume.

9. **Localization (9 Languages)**:
   - Full copy parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all settings sliders, toggles, badge text, and manual override controls.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Extends and refines `REQ-TRK-001` (*Support Start, Stop, Pause with Service persistence*) and `REQ-TRK-002` (*Workout Lap Recording Lifecycle, Manual Splitting & Pause/Resume Neutrality*).
   - Targets `AutoPauseManager.kt`, `TrainingApplication.java`, `TrackerService.java`, `TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt`, and `SensorGridScreen.kt`.

2. **Historical Origin & Commit Trace**:
   - Commit `3c09e3e7` (Initial baseline import, 2011), `ATT-166` Epic (*Filtering to improve sensor values*), `ATT-45` (Sprint 2026-42.4, commit `53c20db4`), and `ATT-3677` (Sprint 2026-42.5).
   - `TrainingApplication.pauseTracking()` and `resumeFromPaused()` historically supported manual user pause only; `ATT-45` introduced auto-pause with global thresholds; `ATT-3677` decouples thresholds and debounces per sport with integer-only sliders.

3. **Root Reason for Existing Formulation**:
   - Manual pause was designed as an absolute binary state to suspend recording active time, trackpoints, and lap splitting per `REQ-TRK-002` when an athlete took a break.
   - When introducing automated speed-drop pausing, naive auto-resume would violate user intent if an athlete deliberately paused at a cafe and walked with their bike.
   - Furthermore, different sports (cycling vs running vs other) operate at vastly different low-speed dynamics, necessitating independent thresholds and debounce windows per sport without fractional float jitter.
   - Therefore, Chesterton's Fence mandates that manual pause semantics must remain sovereign via `mManualPauseLock`, and per-sport thresholds must maintain integer cleanliness while preserving lap neutrality and continuous `TIME_TOTAL_s` accumulation.

4. **Preservation of Core Invariants**:
   - Manual user pause sovereignty via persisted `mManualPauseLock`.
   - Zero lap increments or split disruptions on auto-pause/resume (`REQ-TRK-002`).
   - Continuous `TIME_TOTAL_s` elapsed tracking during pause (`REQ-TRK-009`).
   - Strict moving-time average speed calculation ($v_{\text{avg}} = \frac{\text{Distance}}{\text{TimeActive}}$).
   - Backward compatibility bridges for legacy global properties.
   - 100% full clean-room unit test pass rate strictly preserved.

---

## 2. Verification Specification (`TST-TRK-010`)

### TST-TRK-010: Sport-Specific Auto-Pause Engine, Anti-Chatter Hysteresis, Unit Conversion & Mutex Verification

The test suite validates all facets of `AutoPauseManager`, DataStore settings, sport-specific threshold lookups, metric/imperial unit conversions, debounce windows, sequential sensor gating, hysteresis deadbands, sensor dropout, crash recovery, and UI badge states:

#### Test Cases (Given-When-Then):

1. **TST-TRK-010.1: Sport-Specific Default Activation & Decoupled Thresholds Contract**
   - *Given* default preferences in `TuningPreferencesDataStore`,
   - *When* sports `BSportType.BIKE`, `BSportType.RUN`, and `BSportType.UNKNOWN` are evaluated,
   - *Then* `isAutoPauseEnabledForSport(BIKE)` evaluates to `true`, while `RUN` and `UNKNOWN` evaluate to `false`.
   - *Then* Bike defaults: speed threshold = 3 km/h (Metric) / 2 mph (Imperial), debounce = 2 s.
   - *Then* Run defaults: speed threshold = 2 km/h (Metric) / 1 mph (Imperial), debounce = 5 s.
   - *Then* Other defaults: speed threshold = 1 km/h (Metric) / 1 mph (Imperial), debounce = 10 s.

2. **TST-TRK-010.2: Debounce Counter & Stationary Transition per Sport**
   - *Given* active tracking for Cycling with threshold $3\text{ km/h}$ and debounce $2\text{ s}$,
   - *When* speed drops to $0.0\text{ m/s}$ for 1 second,
   - *Then* engine remains in `PAUSE_DEBOUNCE_PENDING` without triggering pause.
   - *When* speed remains $\le 3\text{ km/h}$ for the 2nd second,
   - *Then* engine triggers `AUTO_PAUSED`, sets `isAutoPaused = true`, and emits auto-pause alert.
   - *Given* active tracking for Running with threshold $2\text{ km/h}$ and debounce $5\text{ s}$,
   - *When* speed drops below $2\text{ km/h}$ for 4 seconds,
   - *Then* engine remains in `PAUSE_DEBOUNCE_PENDING`.
   - *When* the 5th second elapses below $2\text{ km/h}$,
   - *Then* engine triggers `AUTO_PAUSED`.

3. **TST-TRK-010.3: Anti-Chatter Hysteresis Deadband, Ceiling & Unit Conversion Verification**
   - *Given* session in `AUTO_PAUSED` with $v_{\text{pause}} = 3\text{ km/h}$ (Metric),
   - *When* speed flickers to $3.1\text{ km/h}$, $3.3\text{ km/h}$, or $3.4\text{ km/h}$,
   - *Then* session strictly remains `AUTO_PAUSED` (suppressing chatter).
   - *When* speed reaches $\ge 3.5\text{ km/h}$ ($v_{\text{pause}} + 0.5\text{ km/h}$) for 1 second,
   - *Then* engine triggers auto-resume, sets `isAutoPaused = false`, and returns to `TRACKING`.
   - *Given* Imperial configuration with $v_{\text{pause}} = 2\text{ mph}$,
   - *When* speed is evaluated in m/s via `UserUnit2mps(2.0, IMPERIAL)`,
   - *Then* resumption requires $v_{\text{resume}} \ge 2.5\text{ mph}$ ($1.1176\text{ m/s}$).
   - *Given* maximum configured $v_{\text{pause}} = 5$,
   - *Then* $v_{\text{resume}}$ is clamped to physical ceiling ($6.0\text{ km/h}$ Metric / $4.0\text{ mph}$ Imperial).

4. **TST-TRK-010.4: Sequential Synchronous Ingestion Gating & Elevation Protection**
   - *Given* active session transitioning to `AUTO_PAUSED`,
   - *When* altitude or distance sensor samples arrive during pause or exactly during transition,
   - *Then* sequential gating checks `TrainingApplication.isPaused()` then `AutoPauseManager.isAutoPaused()`.
   - *Then* `MyAccumulatorSensor.increment()` and `VerticalSpeedAndSlopeDevice.calculateMetrics()` drop updates synchronously in-memory, resulting in zero accumulated distance and zero elevation gain.

5. **TST-TRK-010.5: Authoritative Moving Average Speed Preservation**
   - *Given* a workout covering 10 km in 30 minutes moving time and 30 minutes auto-paused time (60 min total time),
   - *When* summary metrics in `WorkoutSummaries` are computed,
   - *Then* $v_{\text{avg}} = \frac{10\text{ km}}{0.5\text{ h}} = 20.0\text{ km/h}$ (not $10\text{ km/h}$).

6. **TST-TRK-010.6: Stale Packet Rejection, SENSOR_LOST & Audit Event Logging**
   - *Given* active tracking,
   - *When* no speed packet arrives for $> 5000\text{ ms}$,
   - *Then* engine transitions to `SENSOR_LOST`, logs state transition event in session metadata, and suspends auto-pause triggers.
   - *When* stable speed packets arrive for $\ge 2$ consecutive seconds,
   - *Then* engine transitions back to `TRACKING`.

7. **TST-TRK-010.7: Mutex Precedence (`TrackingLock`) & Manual Override**
   - *Given* user manually pauses tracking via UI button (`mManualPauseLock = true`),
   - *When* speed increases to $25\text{ km/h}$,
   - *Then* auto-resume is strictly blocked.
   - *When* user explicitly taps "Resume" or "Resume Now",
   - *Then* `mManualPauseLock` is cleared and active tracking resumes.

8. **TST-TRK-010.8: Deterministic Startup State-Recovery Hierarchy**
   - *Given* an app restart where `mManualPauseLock == true` in DataStore but the last sample had speed $20\text{ km/h}$,
   - *When* `TrackerService` recovers session,
   - *Then* user sovereignty takes absolute precedence and session restores in `MANUAL_PAUSED`.
   - *Given* an app restart where `mManualPauseLock == false` and `isAutoPaused == true`,
   - *When* session recovers,
   - *Then* session restores in `AUTO_PAUSED` and re-engages sensor evaluation.

9. **TST-TRK-010.9: FIFO Single-Threaded Database Flush Ordering**
   - *Given* transition to `AUTO_PAUSED`,
   - *When* tasks are enqueued on `mDbExecutor`,
   - *Then* final stationary `WorkoutSample` insert is guaranteed to execute before the paused summary update.

10. **TST-TRK-010.10: Crash Recovery Debounce Counter Reset**
    - *Given* process crash while `PAUSE_DEBOUNCE_PENDING`,
    - *When* process recovers,
    - *Then* `debounceCounter` resets to 0 and session restores to `TRACKING`.

11. **TST-TRK-010.11: Non-Finite Speed Sanitization**
    - *Given* speed packets containing `Double.NaN` or `Double.POSITIVE_INFINITY`,
    - *When* evaluated by `AutoPauseManager`,
    - *Then* packets are safely discarded without throwing exceptions or corrupting debounce counters.

12. **TST-TRK-010.12: Preference Migration & Compatibility Bridge**
    - *Given* existing legacy preferences or fresh defaults,
    - *When* `TuningPreferencesDataStore` loads `TuningConfig`,
    - *Then* `autoPauseSpeedThresholdKmh` and `autoPauseDebounceSec` return bike-sport values as backward-compatible bridges, while sport-specific getters return their configured values.

13. **TST-TRK-010.13: Clean-Room Full Suite Regression Execution**
    - *Given* full unit test suite,
    - *When* `./gradlew testDebugUnitTest` executes,
    - *Then* 100% tests pass with zero regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Cases | Verification Method |
| :--- | :--- | :--- |
| `REQ-TRK-018.1` (Sport-Specific Default Activation) | `TST-TRK-010.1` | Unit Test (`AutoPauseManagerTest`, `TuningPreferencesDataStoreAutoPauseTest`) |
| `REQ-TRK-018.2` (Sport-Decoupled Thresholds & Stepping) | `TST-TRK-010.1`, `TST-TRK-010.2`, `TST-TRK-010.3` | Unit Test (`AutoPauseManagerTest`, `TuningPreferencesDataStoreAutoPauseTest`) |
| `REQ-TRK-018.2` (Hysteresis & Clamped Ceiling) | `TST-TRK-010.3` | Unit Test (`AutoPauseManagerTest`) |
| `REQ-TRK-018.2` (Unit Conversion Metric/Imperial) | `TST-TRK-010.3` | Unit Test (`AutoPauseManagerTest`) |
| `REQ-TRK-018.2` (Numerical Sanitization) | `TST-TRK-010.11` | Unit Test (`AutoPauseManagerTest`) |
| `REQ-TRK-018.3` (Stale Packet TTL & SENSOR_LOST) | `TST-TRK-010.6` | Unit Test (`AutoPauseManagerTest`) |
| `REQ-TRK-018.4` (Synchronous Sensor Gating) | `TST-TRK-010.4` | Unit Test (`VerticalSpeedAndSlopeDeviceTest`) |
| `REQ-TRK-018.4` (Moving Average Speed Invariant) | `TST-TRK-010.5` | Unit Test (`WorkoutSummariesTest`) |
| `REQ-TRK-018.5` (Manual Pause Mutex Precedence) | `TST-TRK-010.7` | Unit Test (`AutoPauseManagerTest`, `TrainingApplicationTest`) |
| `REQ-TRK-018.6` (FIFO DB Flush & Crash Debounce Reset) | `TST-TRK-010.9`, `TST-TRK-010.10` | Unit Test (`TrackerServiceTest`, `AutoPauseManagerTest`) |
| `REQ-TRK-018.7` (Deterministic Startup Hierarchy) | `TST-TRK-010.8` | Unit Test (`TrackerServiceTest`) |
| `REQ-TRK-018.9` (9-Language Parity & Dynamic Units) | `TST-TRK-010.13` | Static Localization Audit |
| Invariant Preservation (Clean-Room Stability) | `TST-TRK-010.13` | Clean-Room Unit Test Execution |

---

## 4. 9-Language Localization Audit

No new string resources are required for ATT-3677:
- Speed unit symbols are obtained dynamically via `MyHelper.getShortUnitsId(SensorType.SPEED_mps)` (which references `units_speed_short_metric` and `units_speed_short_imperial`).
- Existing string labels `tuning_auto_pause_speed_threshold_label` and `tuning_auto_pause_debounce_label` are fully translated across all 9 supported locales:
  - `values/`
  - `values-de/`
  - `values-es/`
  - `values-fr/`
  - `values-it/`
  - `values-ja/`
  - `values-nl/`
  - `values-pl/`
  - `values-pt/`
- Zero translation drift or missing string resource errors.
