# Stage 3 Implementation Plan: ATT-45 - Sport-specific auto-pause with configurable speed thresholds and debounce filtering

**Ticket**: [ATT-45](https://rainerblind.atlassian.net/browse/ATT-45)  
**Sub-task**: [ATT-3627](https://rainerblind.atlassian.net/browse/ATT-3627) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-45`  
**Requirement Mapping**: `REQ-TRK-018` (extending `REQ-TRK-001` and `REQ-TRK-002`, complementing `REQ-FIL-012`, `REQ-FIL-013`, and `REQ-UI-262`)  
**Test Mapping**: `TST-TRK-010`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Architectural Design & SWE.2 Component Architecture

```
                                  ┌────────────────────────┐
                                  │   TuningPreferences    │
                                  │       DataStore        │
                                  └───────────┬────────────┘
                                              │ reads config
                                              ▼
┌─────────────────────────┐       ┌────────────────────────┐       ┌────────────────────────┐
│     BANALService        │       │   AutoPauseManager     │◄──────┤  TrainingApplication   │
│ (BestSensorData: SPEED) ├──────►│     State Machine      │       │ (mManualPauseLock)     │
└─────────────────────────┘       └───────────┬────────────┘       └───────────┬────────────┘
                                              │                                │
                                              │ triggers pause/resume          │ synchronous
                                              ▼                                │ in-memory
                                  ┌────────────────────────┐                   │ gating
                                  │    TrackerService      │◄──────────────────┘
                                  │ (mDbExecutor FIFO Seq) │
                                  └───────────┬────────────┘
                                              │
                         ┌────────────────────┴────────────────────┐
                         ▼                                         ▼
            ┌─────────────────────────┐               ┌─────────────────────────┐
            │   WorkoutSamples (DB)   │               │  SensorGridScreen (UI)  │
            │  (Final sample flush)   │               │ (Amber Pill Badge HUD)  │
            └─────────────────────────┘               └─────────────────────────┘
```

### Component Breakdown:

1. **`AutoPauseManager.kt` (`com.atrainingtracker.trainingtracker.tracker.autopause`)**:
   - `enum class AutoPauseState { TRACKING, PAUSE_DEBOUNCE_PENDING, AUTO_PAUSED, MANUAL_PAUSED, SENSOR_LOST }`
   - Data class `AutoPauseConfig(val bikeEnabled: Boolean, val runEnabled: Boolean, val otherEnabled: Boolean, val speedThresholdKmh: Float, val debounceSec: Int)`
   - Pure, deterministic state evaluation function:
     `fun evaluateTick(currentSpeedMps: Double?, timestampMs: Long, sportType: BSportType, config: AutoPauseConfig, manualPauseLocked: Boolean): AutoPauseTransition`
   - Hysteresis calculation:
     $v_{\text{pause}} = \text{speedThresholdKmh} / 3.6$
     $v_{\text{resume}} = \min(v_{\text{pause}} + (0.5 / 3.6), 6.0 / 3.6)$
   - Stale packet rejection: if `timestampMs - packetTimestamp > 5000ms`, transition to `SENSOR_LOST`.
   - Recovery contract: 2 consecutive seconds of stable speed $\implies$ transition to `TRACKING`.
   - Audio feedback trigger: dispatches tone via `ToneGenerator` upon entering `AUTO_PAUSED` and resuming to `TRACKING`.

2. **`TrainingApplication.java` Mutex & Centralized State**:
   - `private static final AtomicBoolean mManualPauseLock = new AtomicBoolean(false);`
   - `private static final AtomicBoolean mIsAutoPaused = new AtomicBoolean(false);`
   - Public accessors:
     - `public static boolean isManualPauseLocked()`
     - `public static void setManualPauseLock(boolean locked, Context context)` (persists to DataStore / SharedPreferences)
     - `public static boolean isAutoPaused()`
     - `public static void setAutoPaused(boolean autoPaused)`
   - Sequential synchronous gating:
     `public static boolean isRecordingSuspended() { return isPaused() || isAutoPaused(); }`
   - Centralized resume handler: `resumeFromPaused()` atomically sets `mManualPauseLock.set(false)` and `mIsAutoPaused.set(false)`.

3. **`TuningPreferencesDataStore.kt` & `SettingsDataStoreJavaHelper.kt`**:
   - Keys in `TuningConfig`:
     - `autoPauseBikeEnabled: Boolean = true`
     - `autoPauseRunEnabled: Boolean = false`
     - `autoPauseOtherEnabled: Boolean = false`
     - `autoPauseSpeedThresholdKmh: Float = 1.5f` (clamped $[0.5, 5.0]$)
     - `autoPauseDebounceSec: Int = 2` (clamped $[1, 5]$)
   - Synchronous Java helpers in `SettingsDataStoreJavaHelper.kt`:
     - `isAutoPauseEnabled(Context, BSportType)`
     - `getAutoPauseSpeedThresholdKmh(Context)`
     - `getAutoPauseDebounceSec(Context)`
   - Integration into `resetToDefaults()`.

4. **`TrackerService.java` Integration & Flush Ordering**:
   - In 1Hz `sampleAndWriteToDb()`:
     - Feeds `BANALService.getBestSensorData(SensorType.SPEED_mps)` into `AutoPauseManager`.
     - On transition to `AUTO_PAUSED`:
       Enqueues to single-threaded `mDbExecutor`:
       1. Final stationary `WorkoutSample` insert with current GPS coordinates, altitude, and accumulated distance.
       2. Summary update for paused state and session state change audit log record.
     - On transition to `TRACKING`:
       Emits `BANALService.RESUME_FROM_PAUSED_INTENT`.
   - In startup recovery:
     - Enforces deterministic 3-tier hierarchy:
       1. Tier 1: `mManualPauseLock == true` $\implies$ restore `MANUAL_PAUSED`.
       2. Tier 2: `mManualPauseLock == false && isAutoPaused == true` $\implies$ restore `AUTO_PAUSED`.
       3. Tier 3: Default $\implies$ restore `TRACKING`.
     - Re-initializes sensor accumulators from `WorkoutSummaries` row to avoid jumping values.

5. **`SensorsGpsFilterSection.kt` (Advanced Settings UI)**:
   - Accordion section "Sensoren & GPS-Filterung" (`TuningSection.SENSORS_GPS`).
   - Group title: `R.string.tuning_auto_pause_title`.
   - Sport toggles: Cycling (default true), Running (default false), Other (default false).
   - Sliders:
     - Speed threshold: $0.5 \dots 5.0\text{ km/h}$, step $0.1\text{ km/h}$, format `%.1f km/h`.
     - Debounce duration: $1 \dots 5\text{ s}$, step $1\text{ s}$, format `%d s`.

6. **Cockpit HUD Integration (`SensorGridScreen.kt`)**:
   - In `SensorGridScreen.kt`, observes `isAutoPaused` and `autoPauseState`.
   - Renders floating amber pill badge above sensor grid / below status:
     - `AUTO_PAUSED`: "Auto-Pause" pill badge with inline "Resume Now" button.
     - `SENSOR_LOST`: "Speed Sensor Lost" pill badge.
   - Clicking "Resume Now" invokes `controlViewModel.requestResumeFromPaused()`.

7. **Localization (9 Languages)**:
   - Translation keys added to `values/strings.xml`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 2. Rule 23 UI Consistency Analysis

- **Theme Compliance**:
  - The Cockpit amber badge uses `TTColor.Amber` (`#FFC107`) with surface container alpha in dark/light modes.
  - Dark Mode: Surface `#16161A` with amber text and outline.
  - AMOLED Mode: Pure Black `#000000` with amber contour outline `BorderStroke(0.5.dp, TTColor.Amber.copy(alpha = 0.6f))`.
- **Zero Layout Displacement**:
  - The badge is composed inside a floating overlay or dedicated status banner row without resizing or shifting active sensor tiles.
- **Typography & Dimensions**:
  - Badge text uses `MaterialTheme.typography.labelMedium`.
  - Button uses compact `AssistChip` or `SuggestionChip` with minimum $32\text{dp}$ touch target.

---

## 3. Step-by-Step Atomic Implementation Schedule

### Phase 1: DataStore Preferences & Synchronous Java Helper
- Modify `TuningPreferencesDataStore.kt` to add the 5 new keys and default values.
- Add getter methods in `SettingsDataStoreJavaHelper.kt`.
- Write unit tests in `TuningPreferencesDataStoreTest.kt`.

### Phase 2: Domain Engine (`AutoPauseManager.kt`) & Unit Tests
- Create `com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManager.kt`.
- Implement state transitions, debounce counting, hysteresis deadband, 6.0 km/h ceiling, 5-second stale packet drop, `NaN`/`Inf` sanitization.
- Write exhaustive unit tests in `AutoPauseManagerTest.kt` verifying all 12 test cases of `TST-TRK-010`.

### Phase 3: Mutex & Gating Integration
- Update `TrainingApplication.java`:
  - Add `mManualPauseLock` and `mIsAutoPaused` `AtomicBoolean` fields.
  - Implement lock persistence to DataStore/SharedPreferences.
  - Update `resumeFromPaused()` to clear lock and auto-pause.
- Update `VerticalSpeedAndSlopeDevice.java` and `MyAccumulatorSensor.java`:
  - Ensure sequential synchronous check `isPaused() || isAutoPaused()`.

### Phase 4: `TrackerService.java` Integration & FIFO Flush
- Integrate `AutoPauseManager` into 1Hz `sampleAndWriteToDb()`.
- Enqueue final stationary sample flush and paused summary update to `mDbExecutor`.
- Implement 3-tier startup recovery hierarchy and DB accumulator re-initialization.

### Phase 5: UI Settings in `SensorsGpsFilterSection.kt`
- Add Auto-Pause subsection with sport toggles and sliders for threshold and debounce.
- Wire with `TuningConfig` StateFlow.

### Phase 6: Cockpit Amber Pill Badge in `SensorGridScreen.kt`
- Compose the floating amber badge with "Resume Now" button.
- Wire to `controlViewModel.requestResumeFromPaused()`.

### Phase 7: 9-Language Localization Parity
- Add string tokens to all 9 `strings.xml` files with exact format specifier parity.

### Phase 8: Verification & Clean-Room Regression
- Run targeted tests: `AutoPauseManagerTest`, `TuningPreferencesDataStoreTest`.
- Run full test suite: `./gradlew testDebugUnitTest`.

---

## 4. Preserved Invariants & Boundary Guards

1. **User Sovereignty**: Explicit manual pause can NEVER be overridden by auto-resume (`mManualPauseLock = true`).
2. **Metric Integrity**: $v_{\text{avg}} = \frac{\text{Distance}}{\text{TimeActive}}$ is unaffected by auto-paused standstill.
3. **Elevation Invariant**: Zero climbing gain accumulated during auto-pause.
4. **Anti-Chatter**: Speed jitter within $[v_{\text{pause}}, v_{\text{pause}} + 0.5\text{ km/h}]$ cannot cause state toggling.
5. **Crash Resilience**: Session cleanly recovers in `AUTO_PAUSED` or `MANUAL_PAUSED` without value jumps.
6. **100% Test Pass Rate**: Full test suite must pass with 0 failures and 0 regressions.
