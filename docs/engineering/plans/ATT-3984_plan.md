# Stage 3: Implementation Plan - ATT-3984: [High] Auto-pause never triggers because ProxySensor reports timestamp 0

**Ticket**: [ATT-3984](https://rainerblind.atlassian.net/browse/ATT-3984)  
**Sub-task**: [ATT-4060](https://rainerblind.atlassian.net/browse/ATT-4060) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-028`, `REQ-TRK-018`  
**Test Mapping**: `TST-TRK-020`, `TST-TRK-010`  
**Branch**: `feature/ATT-3984`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

In production, `BANALService.getBestSensorData(SensorType.SPEED_mps)` returns `mSensorManager.getBestSensor(SensorType.SPEED_mps).getSensorData()`, where `getBestSensor(...)` is a `ProxySensor` wrapping the highest-priority speed `MySensor`. Because `ProxySensor` never called `super.newValue()` and never overrode `getLastUpdateTimestamp()` or `getSensorData()`, `ProxySensor.getSensorData().getTimestamp()` permanently returned `0L`. Consequently, `AutoPauseManager.evaluateTick(...)` treated every speed sample as stale (`packetTimestampMs <= 0L`) and permanently locked the engine in `AutoPauseState.SENSOR_LOST`, preventing auto-pause from ever triggering.

In addition, holistic lifecycle analysis in Stage 1 (`ATT-4058`) identified three accompanying auto-pause reliability gaps:
1. **Registered Speed Source Standstill vs. `SENSOR_LOST`**: Wheel speed sensors (`ThresholdSensor` after 8 zeros) and `SpeedAndLocationDevice` stop emitting updates once stationary, while `MySensorManager` unregisters `SPEED_mps` (`speedData == null`) when all speed sources disconnect or GPS becomes unavailable (`LocationUnavailable()`). `AutoPauseManager` must distinguish a registered speed source at standstill (`hasSpeedSource = true` with stale timestamp or `null` speed -> effective speed `0.0 m/s` -> `AUTO_PAUSED`) from an absent/unregistered speed source or corrupt telemetry (`!hasSpeedSource` or `NaN`/`Infinity`/`< 0.0` -> `SENSOR_LOST`).
2. **Manual Resume from `AUTO_PAUSED`**: When a user manually resumes from `AUTO_PAUSED` via `TrainingApplication.resumeFromPaused()`, `AutoPauseManager.currentState` stayed `AUTO_PAUSED`, preventing auto-pause on subsequent stops unless speed first rose above `resumeThresholdMps`.
3. **`ToneGenerator` Reuse & Release (Rule 36)**: `DefaultAutoPauseAudioNotifier` created a new un-released `ToneGenerator` on every pause/resume tone instead of lazily reusing a single instance and releasing it in `TrackerService.onDestroy()` (the canonical pattern in `NavigationAudioAlertManager.kt`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-028` (*`ProxySensor` Timestamp Delegation, Registered-Source Standstill Evaluation, Manual Resume State Synchronization & `ToneGenerator` Lifecycle Management*), refining `REQ-TRK-018`.
* **Test Mapping**: `TST-TRK-020` (*`ProxySensor` Timestamp Delegation, End-to-End `MySensorManager` + `AutoPauseManager` Integration, Manual Resume Sync & `ToneGenerator` Lifecycle Verification Suite*), complementing `TST-TRK-010`.

---

## 3. System Invariants & Preserved Behavior

1. **`ProxySensor.getDeviceName()` Invariant**: `ProxySensor.getDeviceName()` and `ProxySensor.getSensorData().getDeviceName()` MUST continue returning `null` so `TrackerService.sampleAndWriteToDb()` writes the best sensor column as `SPEED_mps` without a device-name suffix (`TrackerService.java:1424`).
2. **`SENSOR_LOST` & 2-Tick Recovery Invariant**: Whenever no speed source is registered (`hasSpeedSource == false` / `speedData == null`) or corrupt speed values (`NaN`, `Infinity`, `< 0.0`) arrive, `AutoPauseManager` transitions to `AutoPauseState.SENSOR_LOST` and requires `SENSOR_LOST_RECOVERY_TICKS = 2` consecutive fresh valid speed ticks to recover to `TRACKING`.
3. **Manual Pause Lock Sovereignty (`mManualPauseLock`)**: When `TrainingApplication.isManualPauseLocked()` is `true`, `AutoPauseManager` never overrides the manual pause or triggers auto-resume.
4. **Hysteresis Deadband & Stale Resume Guard**: Auto-resume from `AUTO_PAUSED` requires a fresh (`!isPacketStale`), non-null finite speed reading $v \ge \min(v_{\text{pause}} + 0.5\text{ [user unit]}, v_{\text{ceiling}})$.
5. **Parent Human Gate Invariance**: Terminal completion of `ATT-3984` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MySensor.java` & `ProxySensor.java` (`REQ-TRK-028.1`)
* **`MySensor.java`** (`app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensor.java`):
  - Add `public long getLastUpdateTimestamp() { return mLastUpdateTimestamp; }` with JavaDoc.
  - Update `MySensor.getSensorData()` to pass `getLastUpdateTimestamp()` into `new SensorData<>(...)`.
* **`ProxySensor.java`** (`app/src/main/java/com/atrainingtracker/banalservice/sensor/ProxySensor.java`):
  - Override `getLastUpdateTimestamp()`:
    ```java
    @Override
    public long getLastUpdateTimestamp() {
        return mSourceSensor != null ? mSourceSensor.getLastUpdateTimestamp() : 0L;
    }
    ```
  - Override `getSensorData()`:
    ```java
    @Override
    public SensorData<T> getSensorData() {
        T value = getValue();
        return new SensorData<>(
                getSensorType(),
                value,
                getStringValue(),
                getSensorType().getUnitId(),
                getDeviceName(),
                getLastUpdateTimestamp());
    }
    ```
  - Guard `setSourceSensor`, `getValue`, `increment`, `reset`, `onRegisterListener`, and `onUnregisterListener` against `mSourceSensor == null`.

### Component 2: `AutoPauseManager.kt` (`REQ-TRK-028.2`, `REQ-TRK-028.3`, `REQ-TRK-028.4`)
* **Registered Speed Source Standstill vs. `SENSOR_LOST` (`REQ-TRK-028.2`)**:
  - Add `@JvmOverloads` to `evaluateTick` with trailing parameter:
    `hasSpeedSource: Boolean = (isSpeedFiniteAndValid(currentSpeedMps) || packetTimestampMs > 0L)`
  - Check corrupt speed or absent speed source:
    ```kotlin
    val isSpeedCorrupt = currentSpeedMps != null && !isSpeedFiniteAndValid(currentSpeedMps)
    val isPacketStale = packetTimestampMs <= 0L || (currentTimestampMs - packetTimestampMs) > STALE_PACKET_TIMEOUT_MS
    if (isSpeedCorrupt || !hasSpeedSource) {
        handleSensorLost(currentTimestampMs)
        return currentState
    }
    ```
  - When `hasSpeedSource == true` and speed is not corrupt:
    - `val hasFreshValidReading = !isPacketStale && currentSpeedMps != null`
    - `val effectiveSpeedMps = if (hasFreshValidReading) currentSpeedMps!! else 0.0`
    - If `currentState == AutoPauseState.SENSOR_LOST`: require `hasFreshValidReading` for `SENSOR_LOST_RECOVERY_TICKS` (2) consecutive ticks before recovering to `TRACKING` and evaluating `effectiveSpeedMps` (if `!hasFreshValidReading`, reset `sensorLostRecoveryTicks = 0` and remain in `SENSOR_LOST`).
    - In `TRACKING` / `PAUSE_DEBOUNCE_PENDING`: evaluate `effectiveSpeedMps <= pauseThresholdMps` so stationary registered sensors (including wheel sensors whose last packet aged $> 5\text{ s}$ or whose `ThresholdSensor` emitted `null` at standstill) advance `debounceCounter` and transition to `AUTO_PAUSED`.
    - In `AUTO_PAUSED`: auto-resume only when `hasFreshValidReading && effectiveSpeedMps >= resumeThresholdMps`.
* **Manual Resume State Synchronization (`REQ-TRK-028.3`)**:
  - Add `@Synchronized fun onManualResume()` on `AutoPauseManager`: resets `debounceCounter = 0`, `sensorLostRecoveryTicks = 0`, and if `currentState == AutoPauseState.AUTO_PAUSED || currentState == AutoPauseState.PAUSE_DEBOUNCE_PENDING`, sets `currentState = AutoPauseState.TRACKING`.
* **`ToneGenerator` Reuse & Release (`REQ-TRK-028.4`, Rule 36)**:
  - Add `fun release() {}` to `AutoPauseAudioNotifier`.
  - Update `DefaultAutoPauseAudioNotifier(private val toneGeneratorProvider: (() -> ToneGenerator?)? = null)` to lazily create and reuse `private var toneGenerator: ToneGenerator? = null` across `playTone` calls and release it in `@Synchronized override fun release()`.
  - Add `fun release()` on `AutoPauseManager` delegating to `audioNotifier.release()`.

### Component 3: `TrackerService.java` & `TrainingApplication.java` (`REQ-TRK-028.2`, `REQ-TRK-028.3`, `REQ-TRK-028.4`)
* **`TrackerService.java`** (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`):
  - Add `public static void onManualResume()` that safely checks `TrackerService instance = sInstance; if (instance != null && instance.mAutoPauseManager != null) { instance.mAutoPauseManager.onManualResume(); }`.
  - Call `mAutoPauseManager.onManualResume()` in `onStartCommand()` when `mStartType == StartType.RESUME_BY_USER`.
  - In `evaluateAutoPauseTick(@Nullable BANALServiceComm banalService)`:
    - If `mAutoPauseManager.isAutoPaused() && !TrainingApplication.isAutoPaused() && !TrainingApplication.isManualPauseLocked()`, call `mAutoPauseManager.onManualResume()`.
    - Pass `hasSpeedSource = (speedData != null)` as the 6th argument to `mAutoPauseManager.evaluateTick(...)`.
  - In `onDestroy()`: call `if (mAutoPauseManager != null) { mAutoPauseManager.release(); }`.
* **`TrainingApplication.java`** (`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`):
  - In `resumeFromPaused()`: call `TrackerService.onManualResume();` after clearing `mManualPauseLock` and `mIsAutoPaused`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Delegate Timestamp and `SensorData` in `MySensor.java` and `ProxySensor.java`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensor.java`
  - `app/src/main/java/com/atrainingtracker/banalservice/sensor/ProxySensor.java`
* **Changes**:
  - Add `public long getLastUpdateTimestamp()` in `MySensor.java` and use it in `MySensor.getSensorData()`.
  - Override `getLastUpdateTimestamp()` and `getSensorData()` in `ProxySensor.java` with null-safe `mSourceSensor` checks across all methods.

### Step 2: Update `AutoPauseManager.kt` for Standstill vs. `SENSOR_LOST`, `onManualResume()`, and `ToneGenerator` Reuse/Release
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManager.kt`
* **Changes**:
  - Add `@JvmOverloads` `hasSpeedSource: Boolean` parameter to `evaluateTick(...)`.
  - Implement registered-source standstill (`effectiveSpeedMps = 0.0`) vs. `!hasSpeedSource` / corrupt speed (`SENSOR_LOST`).
  - Add `@Synchronized fun onManualResume()` and `fun release()`.
  - Implement lazy `ToneGenerator` reuse and `@Synchronized override fun release()` in `DefaultAutoPauseAudioNotifier`.

### Step 3: Wire `hasSpeedSource`, `onManualResume()`, and `release()` in `TrackerService.java` and `TrainingApplication.java`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* **Changes**:
  - Pass `speedData != null` to `mAutoPauseManager.evaluateTick(...)` and sync manual resume state in `TrackerService.evaluateAutoPauseTick()`.
  - Add `TrackerService.onManualResume()`, invoke it from `TrainingApplication.resumeFromPaused()` and `RESUME_BY_USER`, and invoke `mAutoPauseManager.release()` in `TrackerService.onDestroy()`.

### Step 4: Implement Unit & Integration Tests (`TST-TRK-020_1`..`6` and `TST-TRK-010_6`) in `AutoPauseManagerTest.kt`
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManagerTest.kt`
* **Changes**:
  - Update `TST-TRK-010_6` to test both `!hasSpeedSource` (`SENSOR_LOST` + 2-tick recovery) and `hasSpeedSource = true` stale packet standstill (`AUTO_PAUSED`).
  - Add `TST-TRK-020_1` (`ProxySensor` timestamp, value, `getDeviceName() == null`, and null-source delegation).
  - Add `TST-TRK-020_2` (Criterion 1 end-to-end integration via `MySensorManager` + `ProxySensor` + `AutoPauseManager` + `TrainingApplication.isRecordingSuspended()`).
  - Add `TST-TRK-020_3` (Registered speed source standstill with stale/null packet -> `AUTO_PAUSED`, and stale speed packet blocked from false auto-resume).
  - Add `TST-TRK-020_4` (Criterion 2 end-to-end integration via `MySensorManager` + `ProxySensor` + `AutoPauseManager` hysteresis auto-resume).
  - Add `TST-TRK-020_5` (Criterion 3 manual pause lock precedence & `onManualResume()` / `TrainingApplication.resumeFromPaused()` state synchronization).
  - Add `TST-TRK-020_6` (`DefaultAutoPauseAudioNotifier` `ToneGenerator` reuse across multiple tones and `release()` verification).

### Step 5: Execute Targeted Unit Tests
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.autopause.*" --tests "com.atrainingtracker.banalservice.sensor.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit and integration tests in Stage 4 (`AutoPauseManagerTest` and `banalservice.sensor.*`), followed by the full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3984` allows clean revert without affecting `sprint/2026-42.9` or `develop`.
