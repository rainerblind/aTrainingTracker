# Walkthrough: Sport-Specific Auto-Pause Engine with Configurable Speed Thresholds and Debounce Filtering (ATT-45)

## Fulfilling REQ-TRK-018 & TST-TRK-010

An automated, sport-specific Auto-Pause Engine (`AutoPauseManager.kt`) was engineered and integrated into the real-time tracking pipeline (`TrackerService.java`), accompanied by DataStore persistence, synchronous sensor ingestion gating, anti-chatter hysteresis, crash recovery guarantees, full 9-language localization, and Compose cockpit HUD integration.

### Implemented Architecture & Changes

#### 1. Preferences & Java Interop Layer
- **`TuningPreferencesDataStore.kt`**:
  - Added sport-specific preference keys: `KEY_AUTO_PAUSE_BIKE_ENABLED` (default `true`), `KEY_AUTO_PAUSE_RUN_ENABLED` (default `false`), `KEY_AUTO_PAUSE_OTHER_ENABLED` (default `false`).
  - Added threshold and debounce keys: `KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH` (default `1.5 km/h`, range `0.5..5.0 km/h`), `KEY_AUTO_PAUSE_DEBOUNCE_SEC` (default `2 s`, range `1..5 s`).
  - Exposed updates through `tuningConfigFlow` and `saveTuningConfig`.
- **`SettingsDataStoreJavaHelper.kt`**:
  - Added `@JvmStatic` blocking read helpers `isAutoPauseEnabled`, `getAutoPauseSpeedThresholdKmh`, and `getAutoPauseDebounceSec` for seamless consumption from Java background services without coroutine bridging overhead.

#### 2. Domain Engine: `AutoPauseManager.kt`
- Implemented state machine transitions: `TRACKING`, `PAUSE_DEBOUNCE_PENDING`, `AUTO_PAUSED`, `SENSOR_LOST`.
- **Anti-Chatter Hysteresis**: Resumption requires $v_{\text{resume}} \ge \min(v_{\text{pause}} + 0.5\text{ km/h}, 6.0\text{ km/h})$ sustained for 1 confirmation tick.
- **Sensor Dropout Guard**: Packets older than 5,000 ms trigger `SENSOR_LOST` state instead of falsely assuming stationary 0 km/h; 2 consecutive valid ticks smoothly recover to `TRACKING`.
- **Numerical Sanitization**: Rejects `NaN`, `Infinity`, and negative speed values prior to evaluation.
- **Audio Feedback**: Dispatches pause and resume tones through `AutoPauseAudioNotifier`.

#### 3. Centralized Mutex & Synchronous Ingestion Gating
- **`TrainingApplication.java`**:
  - Implemented `AtomicBoolean mManualPauseLock` and `AtomicBoolean mIsAutoPaused` with persistent SharedPreferences backing.
  - Manual pause sovereignty strictly blocks automated speed resumption until an explicit human resume event occurs.
  - Sequential gating in `VerticalSpeedAndSlopeDevice.java` and `MyAccumulatorSensor.java` synchronously stops distance accumulation and elevation tracking.

#### 4. Background Service Integration: `TrackerService.java`
- Evaluates auto-pause tick during periodic sample writes.
- Enforces strict FIFO database executor queuing: the final stationary sample is committed before the paused state broadcast is dispatched.
- 3-tier startup recovery hierarchy guarantees deterministic state restoration across process recreation.

#### 5. User Interface & 9-Language Localization
- **`SensorsGpsFilterSection.kt`**: Added sport toggles (Bike, Run, Other) and sliders for speed threshold and debounce delay.
- **`AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`**: Wired two-way state binding and persisted configuration updates while strictly complying with the `< 400 lines` threshold (REQ-UI-262).
- **`SensorGridScreen.kt`**: Added ambient amber `AutoPauseHudBanner` pill with immediate "Resume Now" manual override action.
- **9-Language Parity**: 100% translation parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

### Verification Evidence (TST-TRK-010)

1. **AutoPauseManager Engine Verification (`AutoPauseManagerTest.kt`)**:
   - `TST-TRK-010.1`: Default activation by sport (Bike = true, Run = false, Other = false).
   - `TST-TRK-010.2`: Pause transition after configured debounce window.
   - `TST-TRK-010.3`: Debounce reset upon transient speed spike.
   - `TST-TRK-010.4`: Immediate resume upon exceeding hysteresis threshold.
   - `TST-TRK-010.5`: Hysteresis deadband suppression of borderline fluctuations.
   - `TST-TRK-010.6`: Hard resumption ceiling clamped to 6.0 km/h.
   - `TST-TRK-010.7`: Sensor dropout transitions to SENSOR_LOST after 5,000 ms.
   - `TST-TRK-010.8`: Two-tick recovery contract from SENSOR_LOST to TRACKING.
   - `TST-TRK-010.9`: Numerical sanitization of NaN, Infinity, and negative values.
   - `TST-TRK-010.10`: Audio notification tone triggers on pause and resume.
   - `TST-TRK-010.11`: Manual pause lock strictly overrides speed triggers.
   - *Result: 11 of 11 unit tests passed (100%).*

2. **DataStore Preferences Verification (`TuningPreferencesDataStoreAutoPauseTest.kt`)**:
   - Verified persistence, default values, custom saving, and Java helper accessors.
   - *Result: 100% pass.*

3. **9-Language Localization Audit (`TranslationParityTest.kt`)**:
   - Verified 12 newly introduced string resource keys across all 9 application locales.
   - *Result: 100% pass with 0 missing translations.*

4. **Modularity & File Length Audit (`AdvancedTuningModularityTest.kt`)**:
   - Verified all 14 files in `ui/settings/tuning` remain strictly below 400 lines (AC-1 / REQ-UI-262).
   - *Result: 100% pass.*

5. **Full Clean-Room Regression Suite**:
   - Executed `./gradlew testDebugUnitTest` verifying 0 regressions across the entire project.

---

## Final Status: Verified
Requirement **REQ-TRK-018** and Test Specification **TST-TRK-010** are fully satisfied and verified.
