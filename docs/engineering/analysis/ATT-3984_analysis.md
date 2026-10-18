# Stage 1 Analysis: ATT-3984 - [High] Auto-pause never triggers because ProxySensor reports timestamp 0

**Ticket**: [ATT-3984](https://rainerblind.atlassian.net/browse/ATT-3984)  
**Sub-task**: [ATT-4058](https://rainerblind.atlassian.net/browse/ATT-4058) (`[Analysis]`)  
**Parent Epic**: None (Standalone High-Priority Defect from Code Review 2026-10)  
**Target Release**: `TBD (Assigned at Sprint Review per Rule 14)`  
**Active Sprint**: `2026-42.9`  
**Branch**: `feature/ATT-3984`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

When an athlete enables Auto-Pause (`REQ-TRK-018`) and stops during a workout (e.g. at a traffic light), auto-pause **never triggers** in production. Instead, `AutoPauseManager` permanently transitions to `AutoPauseState.SENSOR_LOST` on the very first 1 Hz evaluation tick.

Furthermore, forensic inspection of the auto-pause pipeline (`ProxySensor.java`, `MySensor.java`, `TrackerService.java`, `TrainingApplication.java`, and `AutoPauseManager.kt`) confirms three closely coupled follow-up defects that prevent reliable auto-pause/resume operation:
1. **`ProxySensor` Timestamp Always `0L`**: `BANALService.getBestSensorData(SensorType.SPEED_mps)` returns the `SensorData` of a `ProxySensor` managed by `MySensorManager`. While `ProxySensor` overrides `getValue()` to delegate to `mSourceSensor`, it inherits `MySensor.getSensorData()`, which reads `this.mLastUpdateTimestamp`. Because `newValue()` is invoked on `mSourceSensor` rather than the `ProxySensor`, `ProxySensor.mLastUpdateTimestamp` remains `0L` forever. `AutoPauseManager.evaluateTick` treats `packetTimestampMs <= 0L` as stale and transitions immediately to `SENSOR_LOST`. Existing unit tests in `AutoPauseManagerTest` passed hand-crafted timestamps directly to `AutoPauseManager.evaluateTick`, masking the broken `MySensorManager` -> `ProxySensor` -> `AutoPauseManager` integration path.
2. **Wheel Sensors Stopping Emission at Standstill Mapped to `SENSOR_LOST` Instead of `0.0 m/s`**: Hardware wheel sensors (ANT+ Bike Speed / Speed & Cadence and BLE CSC in `BTLEBikeDevice`, `ANTBikeSpeedDevice`, `ANTBikeSpeedAndCadenceDevice`) emit speed updates on magnet revolutions and stop emitting packets when the wheel stops rotating at standstill (or `ThresholdSensor` emits `null` after 8 zero ticks, and `SpeedAndLocationDevice` stops scheduling decay ticks once speed decays to `0.0`). When `currentTimestampMs - packetTimestampMs > STALE_PACKET_TIMEOUT_MS` (5000 ms), `AutoPauseManager.evaluateTick` previously transitioned to `SENSOR_LOST` instead of treating staleness of a registered speed source as `0.0 m/s` (standstill). Consequently, a bike equipped with a wheel sensor coming to a stop transitions to `SENSOR_LOST` instead of `AUTO_PAUSED`.
3. **Manual Resume from `AUTO_PAUSED` Fails to Reset `AutoPauseManager` State**: When a workout is in `AUTO_PAUSED` (`TrainingApplication.isAutoPaused() == true`, `mManualPauseLock == false`) and the user manually resumes (via the Cockpit "Resume Now" banner in `SensorGridScreen.kt`, `ControlTrackingViewModel`, or the notification action, all routing through `TrainingApplication.resumeFromPaused()`), `TrainingApplication` clears `mIsAutoPaused = false` and sets `cTrackingMode = TrackingMode.TRACKING`, but `AutoPauseManager.currentState` remains `AutoPauseState.AUTO_PAUSED` (because `AutoPauseManager.evaluateTick` only checked `else if (currentState == AutoPauseState.MANUAL_PAUSED)`). On the next stop, `AutoPauseManager` believes it is already in `AUTO_PAUSED`, never increments `debounceCounter`, and never emits `triggerPause = true`.
4. **`ToneGenerator` Resource Leak**: `DefaultAutoPauseAudioNotifier.playTone()` instantiates a new `ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)` on every `AUTO_PAUSE` and `AUTO_RESUME` cue without ever reusing it or calling `release()`, leaking native Android `AudioTrack` / `AudioFlinger` client resources.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Primary Root Cause: `ProxySensor` Does Not Delegate `mLastUpdateTimestamp` / `getSensorData()`
* In [`MySensor.java:37,66-71,110-112`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensor.java#L37-L112):
  ```java
  protected long mLastUpdateTimestamp = 0L;
  ...
  public void newValue(T value) {
      if (mActivated) {
          mValue = value;
          mLastUpdateTimestamp = System.currentTimeMillis();
          ...
      }
  }
  ...
  public SensorData<T> getSensorData() {
      return new SensorData<T>(getSensorType(), getValue(), getStringValue(), getDeviceName(), mLastUpdateTimestamp);
  }
  ```
* In [`MySensorManager.java:178-194,269-281`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java#L178-L281), every best sensor registered in `MySensorManager` is wrapped in a `ProxySensor(this, sensorType, mySensor)`.
* In [`ProxySensor.java:27-73`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/ProxySensor.java#L27-L73), `ProxySensor` overrides `getValue()` (`return mSourceSensor.getValue();`) and `getDeviceName()` (`return null;`), and registers `SensorListener`s directly on `mSourceSensor`. However, `ProxySensor` **never** overrides `getLastUpdateTimestamp()` or `getSensorData()`, nor is `ProxySensor.newValue()` ever called.
* Therefore, when [`TrackerService.java:1585-1587`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1585-L1587) executes:
  ```java
  SensorData<Number> speedData = banalService.getBestSensorData(SensorType.SPEED_mps);
  Double speedMps = (speedData != null && speedData.getValue() != null) ? speedData.getValue().doubleValue() : null;
  long packetTimestamp = (speedData != null) ? speedData.getTimestamp() : 0L;
  ```
  `packetTimestamp` is always `0L`.
* In [`AutoPauseManager.kt:254-277`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManager.kt#L254-L277), `packetTimestampMs <= 0L` sets `isPacketStale = true`, forcing `currentState = AutoPauseState.SENSOR_LOST` on every tick.

### 2.2 Secondary Root Cause: Registered Speed Source Staleness at Standstill vs. "No Speed Source" (`SENSOR_LOST`)
* In `MySensorManager`, a `ProxySensor` for `SensorType.SPEED_mps` exists in `mSensorManager.getSensor(SensorType.SPEED_mps)` if and only if at least one speed sensor (wheel sensor, footpod, bike power speed sensor, or an available location provider via `SpeedAndLocationDevice.LocationAvailable()`) is currently registered. When all speed sources disconnect or GPS becomes unavailable (`LocationUnavailable()` -> `unregisterSensors()`), `MySensorManager.unregisterSensor()` removes the `SPEED_mps` sensor so `getBestSensorData(SensorType.SPEED_mps)` returns `null`.
* When a wheel sensor (`BTLEBikeDevice`, `ANTBikeSpeedDevice`, `ANTBikeSpeedAndCadenceDevice`) is connected and the bike stops at a traffic light, no magnet revolutions occur at standstill:
  * If the last revolution before stopping had speed $v > 0$ (even $v \ge v_{\text{pause}}$ during rapid braking) and the wheel then stops rotating, no further `mSpeedSensor.newValue(...)` calls occur while stationary, or `ThresholdSensor` calls `super.newValue(null)` after 8 zero updates.
  * Once `currentTimestampMs - packetTimestampMs > STALE_PACKET_TIMEOUT_MS` (`5000 ms`), the packet is stale even though the speed sensor is still registered.
* Previously, `AutoPauseManager.evaluateTick` mapped any `isPacketStale || !hasFiniteSpeed` to `AutoPauseState.SENSOR_LOST`. Instead, when a speed source is registered (`hasSpeedSource == true`), packet staleness (`packetTimestampMs <= 0L || (currentTimestampMs - packetTimestampMs) > STALE_PACKET_TIMEOUT_MS`) or a `null` speed value from a registered sensor (`ThresholdSensor` at standstill) represents **standstill (`0.0 m/s`)** and must be evaluated as `0.0 m/s` so the state transitions through debounce to `AUTO_PAUSED`. Conversely, `SENSOR_LOST` must be reserved for when **no speed source** is available (`hasSpeedSource == false`, e.g. `speedData == null`, or when `currentSpeedMps == null && packetTimestampMs <= 0L` with no registered source, or when a corrupt non-finite value such as `Double.NaN`, `Infinity`, or `< 0.0` is supplied).

### 2.3 Tertiary Root Cause: Manual Resume from `AUTO_PAUSED` Leaves `AutoPauseManager` in `AUTO_PAUSED`
* In [`TrainingApplication.java:1563-1576`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java#L1563-L1576):
  ```java
  public void resumeFromPaused() {
      setManualPauseLock(false, this);
      setAutoPaused(false, this);
      ...
      cTrackingMode = TrackingMode.TRACKING;
      notifyTrackingStateChanged();
  }
  ```
* When a session is in `AUTO_PAUSED` (`mManualPauseLock == false`, `mIsAutoPaused == true`) and the user manually resumes, `TrainingApplication` clears `mIsAutoPaused` to `false` and resumes `TrackingMode.TRACKING`.
* However, neither `TrainingApplication.resumeFromPaused()`, `TrackerService` (`RESUME_BY_USER` / `evaluateAutoPauseTick`), nor `AutoPauseManager.evaluateTick()` resets `AutoPauseManager.currentState` from `AutoPauseState.AUTO_PAUSED` back to `AutoPauseState.TRACKING`.
* In `AutoPauseManager.evaluateTick()`, lines 227–232 only handled `else if (currentState == AutoPauseState.MANUAL_PAUSED)`. Because `currentState` remains `AutoPauseState.AUTO_PAUSED`, subsequent stationary ticks execute the `AutoPauseState.AUTO_PAUSED` branch (lines 365–385), which returns `AutoPauseTransition(previousState = AUTO_PAUSED, newState = AUTO_PAUSED, triggerPause = false)` without counting debounce or emitting `triggerPause = true`.

### 2.4 Quaternary Root Cause: Unbounded `ToneGenerator` Instantiation Without Release
* In [`AutoPauseManager.kt:110-128`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManager.kt#L110-L128), `DefaultAutoPauseAudioNotifier.playTone()` creates `val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)` on every tone event and never releases it. Following the existing canonical pattern in [`NavigationAudioAlertManager.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/NavigationAudioAlertManager.kt) (Rule 36), `DefaultAutoPauseAudioNotifier` must lazily allocate and reuse a single `ToneGenerator` instance and expose a `release()` method invoked from `AutoPauseManager.release()` / `TrackerService.onDestroy()`.

---

## 3. User Scope Grounding (ATT-3984 / ATT-1250)

* **In-Scope Goals**:
  1. Override `getLastUpdateTimestamp()` and `getSensorData()` in `ProxySensor.java` (and add `getLastUpdateTimestamp()` in `MySensor.java` with null-safety in `ProxySensor`) so `ProxySensor` delegates timestamp retrieval to `mSourceSensor`.
  2. Update `AutoPauseManager.kt` and `TrackerService.java` so that staleness (or `null` standstill value) of a registered speed source (`hasSpeedSource == true`) is evaluated as `0.0 m/s` (triggering `AUTO_PAUSED` after debounce), while keeping `SENSOR_LOST` for "no speed source" (`hasSpeedSource == false` or non-finite corrupt values `NaN`/`Infinity`/`< 0.0`).
  3. Reset `AutoPauseManager` state to `AutoPauseState.TRACKING` (and reset `debounceCounter` and `sensorLostRecoveryTicks`) on manual resume from `AUTO_PAUSED` (via `AutoPauseManager.onManualResume()`, `TrackerService.onManualResume()`, `TrainingApplication.resumeFromPaused()`, `TrackerService.onStartCommand(RESUME_BY_USER)`, and external auto-pause state synchronization in `evaluateAutoPauseTick`).
  4. Reuse a single `ToneGenerator` instance in `DefaultAutoPauseAudioNotifier` and release it deterministically via `release()` on service teardown (`TrackerService.onDestroy()`).
  5. Add comprehensive unit and integration tests through `MySensorManager` + `ProxySensor` + `AutoPauseManager` (without hand-crafted timestamps hiding `ProxySensor`) verifying all 3 acceptance criteria and `ToneGenerator` lifecycle management.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to Jetpack Compose UI screens (`SensorGridScreen.kt`, `SensorsGpsFilterSection.kt`) or localization `strings.xml` files.
  * No changes to SQLite database schemas or workout export formats.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-TRK-018` (*Sport-Specific Auto-Pause Engine with Anti-Chatter Hysteresis, Mutex Precedence, Sensor Dropout Guard, Synchronous Ingestion Gating, and Sport-Decoupled Thresholds*), specifically Item 3 (*Speed Hierarchy, 5-Second Stale Packet TTL, SENSOR_LOST State & Audit Trail*) and Item 5 (*Strict Mutex Enforcement, Persistence & Race-Free Reset*). Targets `ProxySensor.java`, `MySensor.java`, `AutoPauseManager.kt`, `TrackerService.java`, `TrainingApplication.java`, and `AutoPauseManagerTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-45` (Sprint `2026-42.4`, commit `53c20db4`) and refined for sport-decoupled integer thresholds in `ATT-3677` (Sprint `2026-42.5`, commit `b90eb463`).
* **Root Reason for Existing Formulation**:
  1. In `ATT-45`, `REQ-TRK-018` Item 3 specified that packets with `System.currentTimeMillis() - packet.timestamp > 5000ms` should transition to `SENSOR_LOST` under the assumption that connected speed sensors continuously emit 1 Hz packets while stationary and that a >5s gap indicates signal loss. However, in `BANALService` architecture, disconnected sensors and unavailable location providers are unregistered from `MySensorManager` (`unregisterSensors()` -> `getBestSensorData(SPEED_mps) == null`), whereas connected wheel sensors (`BTLEBikeDevice`, `ANTBikeSpeedDevice`, `ANTBikeSpeedAndCadenceDevice`) remain registered in `MySensorManager` while legitimately ceasing packet emission when the wheel stops rotating at standstill.
  2. Furthermore, `ATT-45` did not notice that `MySensorManager.getSensor(SPEED_mps)` returns a `ProxySensor` whose `mLastUpdateTimestamp` was never updated, because `AutoPauseManagerTest` bypassed `MySensorManager` and `ProxySensor` by passing synthetic `packetTimestampMs` values directly.
* **Preservation of Core Invariants**:
  1. `ProxySensor.getDeviceName()` continues to return `null` so `TrackerService` raw sample column naming (`SPEED_mps` vs device-specific suffixed columns) remains 100% unchanged.
  2. When no speed source is registered (`getBestSensorData(SPEED_mps) == null` / `hasSpeedSource == false`) or non-finite values (`NaN`, `Infinity`, `< 0.0`) are encountered, `AutoPauseManager` still transitions to `SENSOR_LOST` and requires 2 consecutive fresh valid ticks (`SENSOR_LOST_RECOVERY_TICKS = 2`) to recover to `TRACKING`.
  3. Manual pause sovereignty (`mManualPauseLock`), anti-chatter hysteresis (`+0.5` user units, hard ceiling), sport-specific thresholds/debounces, and synchronous ingestion gating remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution

1. **`MySensor.java` & `ProxySensor.java`**:
   * Add `public long getLastUpdateTimestamp()` to `MySensor.java` returning `mLastUpdateTimestamp`, and update `MySensor.getSensorData()` to use `getLastUpdateTimestamp()`.
   * In `ProxySensor.java`, override `getLastUpdateTimestamp()` to return `mSourceSensor != null ? mSourceSensor.getLastUpdateTimestamp() : 0L`, override `getSensorData()` to construct `new SensorData<T>(getSensorType(), getValue(), getStringValue(), getDeviceName(), getLastUpdateTimestamp())`, and guard `getValue()`, `getSourceDeviceName()`, `getSourceDeviceId()`, `addSensorListener()`, `removeSensorListener()`, and `setSourceSensor()` against a `null` `mSourceSensor`.
2. **`AutoPauseManager.kt`**:
   * Update `evaluateTick` (using `@JvmOverloads` to preserve binary and source compatibility with existing 6-argument callers) with `hasSpeedSource: Boolean = (isSpeedFiniteAndValid(currentSpeedMps) || packetTimestampMs > 0L)`:
     * If `currentSpeedMps != null && !isSpeedFiniteAndValid(currentSpeedMps)` (corrupt `NaN`/`Infinity`/negative speed) OR `!hasSpeedSource`: transition to `SENSOR_LOST` (identical to current `SENSOR_LOST` behavior).
     * If `hasSpeedSource == true`: if `isPacketStale` (`packetTimestampMs <= 0L || (currentTimestampMs - packetTimestampMs) > STALE_PACKET_TIMEOUT_MS`) or `currentSpeedMps == null`, treat the effective speed as `0.0 m/s` (standstill of a registered speed source) and proceed with normal threshold/debounce evaluation so the state transitions to `PAUSE_DEBOUNCE_PENDING` and `AUTO_PAUSED`.
   * Add `fun onManualResume()` to `AutoPauseManager` (which resets `currentState` from `AUTO_PAUSED`, `MANUAL_PAUSED`, or `PAUSE_DEBOUNCE_PENDING` to `TRACKING` and zeroes `debounceCounter` and `sensorLostRecoveryTicks`).
   * Update `DefaultAutoPauseAudioNotifier` and `AutoPauseAudioNotifier` to lazily initialize and reuse a single `ToneGenerator` instance (with injectable `toneGeneratorProvider: (() -> ToneGenerator?)? = null` for deterministic unit testing) and implement `release()` to call `toneGenerator?.release()` and clear the reference. Expose `AutoPauseManager.release()`.
3. **`TrackerService.java` & `TrainingApplication.java`**:
   * In `TrackerService.evaluateAutoPauseTick(banalService)`:
     * Determine `boolean hasSpeedSource = (speedData != null);`.
     * If `mAutoPauseManager.isAutoPaused() && !TrainingApplication.isAutoPaused() && !TrainingApplication.isManualPauseLocked()`, invoke `mAutoPauseManager.onManualResume()` before evaluating the tick so external manual resumes are always synchronized.
     * Pass `hasSpeedSource` to `mAutoPauseManager.evaluateTick(...)`.
   * Add static helper `TrackerService.onManualResume()` (matching `TrackerService.invalidateAutoPauseCache()`) and invoke it from `TrainingApplication.resumeFromPaused()` and `TrackerService.onStartCommand(RESUME_BY_USER)`.
   * Call `mAutoPauseManager.release()` in `TrackerService.onDestroy()`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests (`ProxySensor.getDeviceName()` remains `null`).
  2. Thread safety on sensor and service single-thread dispatchers (`@Synchronized` on `DefaultAutoPauseAudioNotifier`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * Changes are tightly scoped to `ProxySensor`, `MySensor`, `AutoPauseManager`, `TrackerService`, and `TrainingApplication`, backed by deterministic unit and integration tests.
