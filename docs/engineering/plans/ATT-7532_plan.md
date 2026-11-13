# Stage 3: Implementation Plan - ATT-7532: Configurable Morning HRV Check on Start Screen

**Ticket**: [ATT-7532](https://rainerblind.atlassian.net/browse/ATT-7532)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-UI-525` (`REQ-UI-525.1`..`REQ-UI-525.5`)  
**Test Mapping**: `TST-UI-525` (`TST-UI-525.1`..`TST-UI-525.6`)  
**Branch**: `feature/ATT-7532` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
Athletes performing morning HRV check-ins require a direct 1-tap entry point on waking. Standalone `drawer_morning_hrv` was retired in `REQ-UI-513`, creating navigation friction. This plan integrates a contextual, smart-gated "Morgendlicher Check" launcher on `ControlTrackingScreen` during `TrackingMode.READY`, auto-hiding during tracking, after daily completion, past morning cutoff hours, or for users without paired heart rate sensors.

---

## 2. Traceability & Requirements Mapping
* `REQ-UI-525.1`: Tuning preference DataStore keys, default values (`true`, `11`), and settings controls $\to$ `TST-UI-525.1`.
* `REQ-UI-525.2`: Paired HRM query in `DevicesDatabaseManager` and `DeviceDataRepository` $\to$ `TST-UI-525.2`.
* `REQ-UI-525.3`: Today's morning check completion check in `HrvReadiness` DB & repo $\to$ `TST-UI-525.3`.
* `REQ-UI-525.4`: Reactive visibility state in `ControlTrackingViewModel` and button in `TrackingTabsScreen` $\to$ `TST-UI-525.4`.
* `REQ-UI-525.5`: 9-language localization parity across all `strings_hrv.xml` shards $\to$ `TST-UI-525.5`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero SQLite Migrations**: Zero schema changes to `devices.db` or `hrv_readiness.db`; queries use indexed columns.
2. **Zero Workout Interference**: Button renders strictly in `TrackingMode.READY` and never during recording/paused modes.
3. **Thread Safety**: DB queries run on `Dispatchers.IO` / synchronous SQLite cursors with defensive null checks.
4. **Design System & Localization Parity**: Adheres strictly to `docs/design_guidelines.md` and 9-language parity.

---

## 4. Proposed Architectural Changes

### Component 1: Tuning Preferences & Settings Controls
* Add `showMorningCheckOnStartScreen: Boolean` (default `true`) and `morningCheckCutoffHour: Int` (default `11`, range 6..15) to `TuningPreferencesDataStore.kt` & `TuningConfig.kt`.
* In `PhysiologyModelsSection.kt`, wire controls using `TuningToggleItem` and `TuningSliderItem` (from `TuningFormControls.kt`). Sync in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`.

### Component 2: Sensor & Readiness Database Gates
* In `DevicesDatabaseManager.java`, add `hasPairedHeartRateDevices(): Boolean` querying `PAIRED > 0 AND DEVICE_TYPE = 'HRM'`. Expose in `DeviceDataRepository.kt`.
* In `HrvReadinessDatabaseManager.kt`, add `hasCompletedMorningCheck(dateIso: String): Boolean`. Expose suspend helper in `HrvReadinessRepository.kt`.

### Component 3: Reactive Start Screen Visibility & CTA Launcher
* In `ControlTrackingViewModel.kt`, expose `hasPairedHeartRateDevices: StateFlow<Boolean>` and `isMorningCheckVisible: StateFlow<Boolean>` combining preferences, sensor pairing, cutoff hour, and today's completion. Refresh completion on `onResume()`.
* In `TrackingTabsScreen.kt` `bottomContent`, render "Morgendlicher Check" `OutlinedButton` when `isMorningCheckVisible == true`, calling `onNavigateToMorningHrv`.

### UI Consistency (`docs/design_guidelines.md` §1 & §5)
* Reference: `hrv_test_cockpit_launcher` in `TrackingTabsScreen.kt`. Reuses `OutlinedButton`, `Icons.Default.Favorite`, `TTColor.HrvHeartRateRed`, `RoundedCornerShape(8.dp)`.
* Settings: Standard `TuningToggleItem` and `TuningSliderItem` in `PhysiologyModelsSection.kt`. Zero raw switches.

---

## 5. Step-by-Step Implementation Sequence

### Step 1: Preferences & Tuning Model (`REQ-UI-525.1`)
* Update `TuningPreferencesDataStore.kt`, `TuningConfig.kt`, `PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`.

### Step 2: Database Query Extensions (`REQ-UI-525.2`, `REQ-UI-525.3`)
* Add `hasPairedHeartRateDevices()` to `DevicesDatabaseManager.java` & `DeviceDataRepository.kt`.
* Add `hasCompletedMorningCheck()` to `HrvReadinessDatabaseManager.kt` & `HrvReadinessRepository.kt`.

### Step 3: Localization Shards (`REQ-UI-525.5`)
* Add `morning_check_start_button`, `tuning_show_morning_check_on_start_screen_*`, `tuning_morning_check_cutoff_hour_*` to 9 `strings_hrv.xml` files.

### Step 4: ViewModel & UI Presentation (`REQ-UI-525.4`)
* Add `isMorningCheckVisible` in `ControlTrackingViewModel.kt`. Wire CTA launcher in `TrackingTabsScreen.kt` and route in `ATrainingTrackerApp.kt`.

### Step 5: Unit & Contract Tests (`TST-UI-525.1`..`TST-UI-525.6`)
* Update and run `TuningConfigTest`, `DevicesDatabaseManagerTest`, `HrvReadinessDatabaseManagerTest`, `ControlTrackingViewModelTest`, `TranslationParityTest`.

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted test suite:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.banalservice.database.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Rollback**: Branch isolation allows instant revert (`git reset --hard HEAD~1`) without affecting `sprint/2026-46.5`.
