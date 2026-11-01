# Stage 3: Implementation Plan - ATT-4840: [Feature] Optionally start in auto paused

**Ticket**: [ATT-4840](https://rainerblind.atlassian.net/browse/ATT-4840)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Active Sprint**: `2026-44.1`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Architectural Design & Layer Boundaries (SWE.2)

```
[AutoPauseTabsScreen (Global Toggle Card)]
             │ (events)
             ▼
[AutoPauseViewModel (updateStartInAutoPauseEnabled)]
             │ (suspend update / StateFlow)
             ▼
[TuningPreferencesDataStore (Jetpack DataStore)]
             │ (toAutoPauseConfig)
             ▼
[AutoPauseConfig.startInAutoPauseEnabled]
             │
             ├──► [TrainingApplication.startTracking()] -> pauseTracking(true) if shouldStartInAutoPause
             └──► [TrackerService.onStartCommand(START_NORMAL)] -> mAutoPauseManager.setState(AUTO_PAUSED)
```

### Layer Responsibilities
- **UI (`AutoPauseTabsScreen.kt`)**: Renders a global *Start in Auto-Paused* `TuningToggleItem` card at the top of the content.
- **ViewModel (`AutoPauseViewModel.kt`)**: Exposes `updateStartInAutoPauseEnabled(Boolean)`.
- **DataStore (`TuningPreferencesDataStore.kt`)**: Persists `KEY_START_IN_AUTO_PAUSE_ENABLED` and resets on global reset.
- **Domain Engine (`AutoPauseManager.kt`)**: Pure static function `shouldStartInAutoPause(config, sportType, currentSpeedMps)`.
- **Service Layer (`TrainingApplication.java`, `TrackerService.java`)**: Synchronously initializes tracking state in `TrackingMode.PAUSED` (`isAutoPaused = true`) and `AutoPauseState.AUTO_PAUSED`.

---

## 2. UI Consistency (Design Guidelines Section 5)

- **Reference Screen**: `AutoPauseTabsScreen.kt`
- **Reused Components**: `TuningToggleItem` from `ui.settings.tuning.TuningFormControls.kt` inside `Card(shape = RoundedCornerShape(12.dp), containerColor = surfaceContainerLow)`.
- **Theme Tokens**: Standard spacing `16.dp`, `MaterialTheme.colorScheme.surfaceContainerLow`, `onSurface`, `onSurfaceVariant`.

---

## 3. Atomic Implementation Steps

1. **Step 1: Defaults & Persistence**:
   - Add `DEFAULT_START_IN_AUTO_PAUSE_ENABLED = false` to `TuningPreferencesDefaults.kt`.
   - Add `startInAutoPauseEnabled` to `TuningConfig.kt`, `AutoPausePreferences`, and DataStore keys in `TuningPreferencesDataStore.kt`.
   - Add `updateStartInAutoPauseEnabled(Boolean)` to `TuningPreferencesDataStore.kt`.
2. **Step 2: AutoPauseConfig & Domain Predicate**:
   - Add `startInAutoPauseEnabled: Boolean = false` to `AutoPauseConfig` in `AutoPauseManager.kt`.
   - Pass `startInAutoPauseEnabled` in `SettingsDataStoreJavaHelper.kt`.
   - Implement `@JvmStatic fun shouldStartInAutoPause(config, sportType, currentSpeedMps): Boolean` in `AutoPauseManager.kt`.
3. **Step 3: Startup Integration**:
   - In `TrainingApplication.startTracking()`: check `shouldStartInAutoPause`; if true, invoke `pauseTracking(true)` instead of `cTrackingMode = TRACKING`.
   - In `TrackerService.onStartCommand()`: ensure `START_NORMAL` checks `TrainingApplication.isAutoPaused()` and sets `mAutoPauseManager.setState(AutoPauseState.AUTO_PAUSED)`.
4. **Step 4: ViewModel & UI**:
   - Add `updateStartInAutoPauseEnabled(Boolean)` to `AutoPauseViewModel.kt`.
   - Add global toggle card at the top of `AutoPauseSportTabContent` in `AutoPauseTabsScreen.kt`.
5. **Step 5: Localization (9 Languages)**:
   - Add `tuning_auto_pause_start_in_auto_pause_title` and `tuning_auto_pause_start_in_auto_pause_desc` to all 9 `strings.xml` files.
6. **Step 6: Unit Tests**:
   - Author `TST-TRK-044.1` in `AutoPauseManagerTest.kt`.
   - Author `TST-TRK-044.3` in `AutoPauseViewModelTest.kt`.
   - Author `TST-TRK-044.4` in `TranslationParityTest.kt`.
   - Run AAPT2 resource verification and targeted unit tests.

---

## 4. Invariants & Verification Checklist

1. **Code Lock**: Verify Gate 3 sign-off before modifying production code.
2. **Zero Regressions**: Existing `AutoPauseManagerTest` (multi-tick resume debounce, SENSOR_LOST, mutex) must remain 100% passing.
3. **Manual Pause Sovereignty**: Manual pause lock remains unalterable.
