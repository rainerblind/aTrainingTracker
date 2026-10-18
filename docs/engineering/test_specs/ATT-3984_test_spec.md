# Stage 2: Requirement & Test Specification (`ATT-3984`)

* **Parent Ticket**: `ATT-3984` (`[High] Auto-pause never triggers because ProxySensor reports timestamp 0`)
* **Stage 1 Analysis Subtask**: `ATT-4058`
* **Stage 2 Subtask**: `ATT-4059`
* **Target Sprint**: `2026-42.9`

---

## 1. Formal Requirement Specification

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines Clause 3 of `REQ-TRK-018` (*Sport-Specific Auto-Pause Engine with Anti-Chatter Hysteresis, Mutex Precedence, Sensor Dropout Guard, Synchronous Ingestion Gating, and Sport-Decoupled Thresholds*) and introduces `REQ-TRK-028` (*ProxySensor Timestamp Delegation, Registered-Source Standstill Auto-Pause Evaluation, Manual Resume State Synchronization & ToneGenerator Reuse*). Targets `MySensor.java`, `ProxySensor.java`, `AutoPauseManager.kt`, `TrackerService.java`, `TrainingApplication.java`, and `AutoPauseManagerTest.kt`.
* **Historical Origin & Commit Trace**: `ProxySensor.java` was created in commit `b1f7bd79` (*ATT-237: Filter ALL Sensor values!*) when `MySensor.java` had no timestamp field. Commit `53c20db4` (*ATT-45: Auto-Pause engine*, Sprint `2026-42.4`) added `mLastUpdateTimestamp` and `getSensorData()` to `MySensor.java` and introduced `AutoPauseManager.kt`, `TrackerService.evaluateAutoPauseTick()`, and `DefaultAutoPauseAudioNotifier`.
* **Root Reason for Existing Formulation**: `ProxySensor` overrides `getValue()` to delegate to `mSourceSensor.getValue()` and overrides `getDeviceName()` to return `null` so `TrackerService` writes the best sensor column as `SPEED_mps` without a device-name suffix (`TrackerService.java:1424`), but `ATT-45` overlooked overriding `getLastUpdateTimestamp()` and `getSensorData()` in `ProxySensor`, causing `getBestSensorData(SensorType.SPEED_mps)` to always report timestamp `0L`; additionally, `REQ-TRK-018` Clause 3 assumed active sensors emit 1 Hz packets when stationary whereas wheel speed sensors (`ThresholdSensor`) and `SpeedAndLocationDevice` stop emitting packets at standstill while `MySensorManager` unregisters `SPEED_mps` (`speedData == null`) when all speed sources disconnect.
* **Preservation of Core Invariants**: `ProxySensor.getDeviceName()` MUST continue returning `null` so `TrackerService` column naming (`SPEED_mps` without device suffix) remains 100% intact; `SENSOR_LOST` state and 2-tick recovery (`SENSOR_LOST_RECOVERY_TICKS = 2`) remain active whenever no speed source is registered (`hasSpeedSource == false` / `getBestSensorData(SPEED_mps) == null`) or when corrupt/non-finite speed values (`NaN`, `Infinity`, `< 0.0`) arrive; manual pause lock sovereignty (`mManualPauseLock`), sport-specific thresholds and debounces (`ATT-3677`), anti-chatter hysteresis deadband (`+0.5` user units capped at `6.0 km/h` / `4.0 mph`), synchronous sensor gating, and moving-time average speed calculation remain 100% preserved.

---

### Net-New Requirement: `REQ-TRK-028`

* **ID**: `REQ-TRK-028`
* **Title**: **ProxySensor Timestamp Delegation, Registered-Source Standstill Evaluation, Manual Resume State Synchronization & ToneGenerator Lifecycle Management.**
* **Description**:
  The system SHALL ensure end-to-end auto-pause and auto-resume reliability across `MySensor`, `ProxySensor`, `AutoPauseManager`, `TrackerService`, and `TrainingApplication` (`ATT-3984`, refining `REQ-TRK-018`):
  1. *`MySensor` & `ProxySensor` Timestamp and `SensorData` Delegation (`REQ-TRK-028.1`)*:
     - `MySensor.java` SHALL expose `public long getLastUpdateTimestamp()` returning `mLastUpdateTimestamp`, and `MySensor.getSensorData()` SHALL use `getLastUpdateTimestamp()` when constructing `SensorData`.
     - `ProxySensor.java` SHALL override `getLastUpdateTimestamp()` to delegate to `mSourceSensor != null ? mSourceSensor.getLastUpdateTimestamp() : 0L`.
     - `ProxySensor.java` SHALL override `getSensorData()` to return a `SensorData<T>` containing the delegated value (`getValue()`), string value (`getStringValue()`), unit (`getSensorType().getUnitId()`), `getDeviceName()` (`null`, preserving `TrackerService` primary column naming), and delegated timestamp (`getLastUpdateTimestamp()`).
     - `ProxySensor.java` SHALL guard all delegations (`setSourceSensor`, `getValue`, `increment`, `reset`, `onRegisterListener`, `onUnregisterListener`) against a `null` `mSourceSensor`.
  2. *Registered Speed Source Standstill vs. `SENSOR_LOST` Discrimination (`REQ-TRK-028.2`)*:
     - `AutoPauseManager.evaluateTick(...)` SHALL accept `@JvmOverloads hasSpeedSource: Boolean = (isSpeedFiniteAndValid(currentSpeedMps) || packetTimestampMs > 0L)`.
     - `TrackerService.evaluateAutoPauseTick(banalService)` SHALL pass `hasSpeedSource = (speedData != null)`, reflecting whether `MySensorManager` currently has a registered `ProxySensor` for `SensorType.SPEED_mps`.
     - When `(currentSpeedMps != null && !isSpeedFiniteAndValid(currentSpeedMps))` (corrupt `NaN`, `Infinity`, or negative speed) OR `!hasSpeedSource` (no speed source registered or all speed sources unregistered/unavailable): `AutoPauseManager` SHALL transition to `AutoPauseState.SENSOR_LOST`, clear `isAutoPaused` if previously auto-paused, and require `SENSOR_LOST_RECOVERY_TICKS` (2 consecutive ticks with fresh valid speed packets) to recover to `TRACKING`.
     - When `hasSpeedSource == true` and `currentSpeedMps` is either `null` or finite $\ge 0.0$: if `currentSpeedMps == null` or the packet timestamp is stale (`packetTimestampMs <= 0L || (currentTimestampMs - packetTimestampMs) > STALE_PACKET_TIMEOUT_MS`), `AutoPauseManager` SHALL treat the effective speed as `0.0 m/s` (stationary registered speed source whose last packet aged out or whose `ThresholdSensor` zeroed out at standstill) and evaluate it against `pauseThresholdMps` so the pause debounce counter advances and transitions to `AUTO_PAUSED`.
     - While in `AUTO_PAUSED`, auto-resume SHALL require a fresh (`!isPacketStale`), non-null finite speed reading $v \ge v_{\text{resume}}$.
  3. *Manual Resume State Synchronization from `AUTO_PAUSED` (`REQ-TRK-028.3`)*:
     - `AutoPauseManager` SHALL expose `fun onManualResume()` that resets `currentState = AutoPauseState.TRACKING`, `debounceCounter = 0`, and `sensorLostRecoveryTicks = 0` when `currentState == AutoPauseState.AUTO_PAUSED` or `PAUSE_DEBOUNCE_PENDING`.
     - `TrackerService` SHALL expose `public static void onManualResume()` (delegating to the active instance's `mAutoPauseManager.onManualResume()`), invoked from `TrainingApplication.resumeFromPaused()` and `TrackerService.onStartCommand(RESUME_BY_USER)`.
     - Additionally, `TrackerService.evaluateAutoPauseTick()` SHALL synchronize state before `evaluateTick()`: if `mAutoPauseManager.isAutoPaused() && !TrainingApplication.isAutoPaused() && !TrainingApplication.isManualPauseLocked()`, it SHALL invoke `mAutoPauseManager.onManualResume()`.
  4. *`ToneGenerator` Instance Reuse & Lifecycle Release (`REQ-TRK-028.4`, Rule 36)*:
     - `AutoPauseAudioNotifier` SHALL declare `fun release() {}`.
     - `DefaultAutoPauseAudioNotifier` SHALL lazily initialize and reuse a single `ToneGenerator` instance across `playAutoPauseTone()` and `playAutoResumeTone()` calls, and implement `@Synchronized override fun release()` to call `toneGenerator?.release()` and clear the reference.
     - `AutoPauseManager` SHALL expose `fun release()` delegating to `audioNotifier.release()`, and `TrackerService.onDestroy()` SHALL invoke `mAutoPauseManager.release()`.

---

## 2. Acceptance Criteria (Given-When-Then)

1. **AC-1 (`ProxySensor` Timestamp Delegation & Criterion 1 — Sub-Threshold Speed Triggers Auto-Pause)**:
   - *Given* a workout in `TRACKING` mode with auto-pause enabled for Cycling (`pauseThreshold = 3 km/h`, `debounceSeconds = 2s`) and a `ProxySensor` wrapping a speed `MySensor` in `MySensorManager`,
   - *When* the source speed sensor receives values below `pauseThresholdMps` (e.g., `0.5 m/s` = `1.8 km/h` or `0.0 m/s`) at timestamp $t > 0$ for `debounceSeconds` consecutive 1 Hz ticks,
   - *Then* `ProxySensor.getSensorData().getTimestamp()` returns $t > 0$ (matching the underlying `MySensor`), `ProxySensor.getSensorData().getDeviceName()` returns `null`, `AutoPauseManager` transitions `TRACKING -> PAUSE_DEBOUNCE_PENDING -> AUTO_PAUSED`, `TrainingApplication.isAutoPaused()` becomes `true`, and `ClockDevice` does not increment `TIME_ACTIVE` on subsequent ticks.
2. **AC-2 (Registered Speed Source Standstill with Stale/Null Packet Triggers Auto-Pause)**:
   - *Given* a workout in `TRACKING` mode with a registered speed source (`hasSpeedSource = true`) whose last speed update occurred at $t_0$ and stopped emitting packets at standstill (or `ThresholdSensor` emitted `null`),
   - *When* $t - t_0 > 5000\text{ ms}$ (or `currentSpeedMps == null`) for `debounceSeconds` ticks while `hasSpeedSource == true`,
   - *Then* `AutoPauseManager` evaluates effective speed as `0.0 m/s` and transitions to `AUTO_PAUSED` instead of `SENSOR_LOST`.
3. **AC-3 (Criterion 2 — Speed Recovery Above Hysteresis Triggers Auto-Resume)**:
   - *Given* a workout in `AUTO_PAUSED` state (`TrainingApplication.isAutoPaused() == true`, `mManualPauseLock == false`),
   - *When* a fresh speed reading arrives through `ProxySensor` at or above `resumeThresholdMps` ($\ge \min(v_{\text{pause}} + 0.5\text{ km/h}, 6.0\text{ km/h})$),
   - *Then* `AutoPauseManager` transitions `AUTO_PAUSED -> TRACKING`, `TrainingApplication.isAutoPaused()` becomes `false`, and `ClockDevice` resumes incrementing `TIME_ACTIVE`.
4. **AC-4 (Criterion 3 — Manual Pause Precedence & Manual Resume from `AUTO_PAUSED`)**:
   - *Given* a workout manually paused by the user (`mManualPauseLock == true`),
   - *When* speed readings fluctuate below and above `resumeThresholdMps`,
   - *Then* `AutoPauseManager` does not override the manual pause or auto-resume; AND *Given* a workout in `AUTO_PAUSED` that is manually resumed by the user via `TrainingApplication.resumeFromPaused()`, *When* speed subsequently drops below `pauseThresholdMps` for `debounceSeconds` ticks, *Then* `AutoPauseManager` cleanly transitions to `AUTO_PAUSED` again.
5. **AC-5 (`SENSOR_LOST` on Unregistered Speed Source or Corrupt Telemetry)**:
   - *Given* a workout in `TRACKING` mode where all speed sources are unregistered (`hasSpeedSource = false` / `speedData == null`) or corrupt speed (`NaN`, `Infinity`, `< 0.0`) is reported,
   - *When* `evaluateTick` executes,
   - *Then* `AutoPauseManager` transitions to `SENSOR_LOST` and recovers to `TRACKING` after 2 consecutive fresh valid speed ticks.
6. **AC-6 (`ToneGenerator` Reuse & Release on Service Destruction)**:
   - *Given* `DefaultAutoPauseAudioNotifier` and `AutoPauseManager`,
   - *When* multiple auto-pause and auto-resume tones are played followed by `autoPauseManager.release()`,
   - *Then* a single `ToneGenerator` instance is reused across all tones and `ToneGenerator.release()` is invoked on `release()`.

---

## 3. Formal Test Specification (`TST-TRK-020`)

* **Test ID**: `TST-TRK-020`
* **Title**: **`ProxySensor` Timestamp Delegation, End-to-End `MySensorManager` + `AutoPauseManager` Integration, Manual Resume Sync & `ToneGenerator` Lifecycle Verification Suite**
* **Traces To**: `REQ-TRK-028`, `REQ-TRK-018`
* **Test File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManagerTest.kt`
* **Test Cases**:
  1. **`TST-TRK-020_1` (`ProxySensor` Timestamp, Value, DeviceName & Null-Source Delegation Test)**:
     - Verify `MySensor.newValue(v)` updates `getLastUpdateTimestamp() > 0L` and `ProxySensor.getLastUpdateTimestamp()` / `ProxySensor.getSensorData().getTimestamp()` return the exact timestamp of `mSourceSensor`.
     - Verify `ProxySensor.getSensorData().getDeviceName()` returns `null` even when `mSourceSensor` belongs to a named `MyDevice`.
     - Verify `ProxySensor` with `mSourceSensor == null` safely returns `null` value and `0L` timestamp without `NullPointerException`.
  2. **`TST-TRK-020_2` (Criterion 1 End-to-End Integration: Sub-Threshold Speed via `MySensorManager` & `ProxySensor` Triggers `AUTO_PAUSED` & Halts `TIME_ACTIVE`)**:
     - Register a speed sensor on a test `MyDevice` in `MySensorManager`, feed moving speed (`5.0 m/s`), then feed sub-threshold speed (`0.5 m/s` / `0.0 m/s`), read `getBestSensorData(SensorType.SPEED_mps)` through `ProxySensor`, pass into `AutoPauseManager.evaluateTick`, and verify transition `TRACKING -> PAUSE_DEBOUNCE_PENDING -> AUTO_PAUSED`, `TrainingApplication.isAutoPaused() == true`, and `TrainingApplication.isRecordingSuspended() == true`.
  3. **`TST-TRK-020_3` (Registered Speed Source Standstill with Stale/Null Packet Triggers `AUTO_PAUSED`)**:
     - Verify that when `hasSpeedSource == true` and packet age exceeds `5000 ms` (or `currentSpeedMps == null` from `ThresholdSensor` after 8 zeros), `AutoPauseManager` treats effective speed as `0.0 m/s`, increments debounce, and transitions to `AUTO_PAUSED` rather than `SENSOR_LOST`.
     - Verify that while in `AUTO_PAUSED`, a stale speed packet with `currentSpeedMps = 5.0 m/s` does NOT trigger false auto-resume until a fresh packet arrives.
  4. **`TST-TRK-020_4` (Criterion 2 End-to-End Integration: Speed Recovery Above Hysteresis via `ProxySensor` Triggers Auto-Resume)**:
     - From `AUTO_PAUSED`, feed speed inside hysteresis deadband (`3.2 km/h`) through `ProxySensor` and verify session remains `AUTO_PAUSED`; then feed fresh speed at/above `resumeThresholdMps` (`3.6 km/h` = `1.0 m/s`) through `ProxySensor` and verify transition `AUTO_PAUSED -> TRACKING` and `TrainingApplication.isAutoPaused() == false`.
  5. **`TST-TRK-020_5` (Criterion 3: Manual Pause Precedence & Manual Resume Synchronization from `AUTO_PAUSED`)**:
     - Verify that when `isManualPauseLocked == true`, speed changes through `ProxySensor` never trigger auto-pause or auto-resume.
     - Verify that when a session in `AUTO_PAUSED` is manually resumed (`onManualResume()` / `TrainingApplication.resumeFromPaused()`), `AutoPauseManager.currentState` resets to `TRACKING` and subsequent stationary ticks cleanly re-trigger `AUTO_PAUSED`.
  6. **`TST-TRK-020_6` (`DefaultAutoPauseAudioNotifier` `ToneGenerator` Reuse & `release()` Lifecycle Test)**:
     - Inject a mock `ToneGenerator` provider into `DefaultAutoPauseAudioNotifier`, trigger multiple pause and resume tones, verify the provider is invoked only once (reusing the same `ToneGenerator`), and verify `autoPauseManager.release()` calls `ToneGenerator.release()`.

---

## 4. Localization & Resource Impact
* Zero net-new UI strings required. Existing 9-language auto-pause strings (`REQ-TRK-018`) remain untouched.
