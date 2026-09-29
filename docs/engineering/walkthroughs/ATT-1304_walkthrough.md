# Walkthrough: Dedicated Advanced Tuning Preferences Screen (ATT-1304)

* **Parent Ticket**: [ATT-1304](https://rainerblind.atlassian.net/browse/ATT-1304) (*[Feature] [Settings] Dedicated Advanced Tuning Preferences screen with parameter validation and reset-to-defaults*)
* **Sub-Tasks**:
  * [ATT-1630](https://rainerblind.atlassian.net/browse/ATT-1630) (*[Analysis] Problem Domain & Root Cause Analysis*) - `Erledigt`
  * [ATT-1632](https://rainerblind.atlassian.net/browse/ATT-1632) (*[SWE.1 / SWE.4] Requirement & Test Specification*) - `Erledigt`
  * [ATT-1633](https://rainerblind.atlassian.net/browse/ATT-1633) (*[SWE.2 / SWE.3] Implementation Plan*) - `Erledigt`
  * [ATT-1634](https://rainerblind.atlassian.net/browse/ATT-1634) (*[Implementation] Software Construction & Unit Tests*) - `Erledigt`
  * [ATT-1635](https://rainerblind.atlassian.net/browse/ATT-1635) (*[Test] Verification, Clean-Room Regression & Release Verification*) - `In Bearbeitung`
* **Target Version**: `V4.9.38`
* **Requirement**: [`REQ-SET-073`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L116) (*Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults*)
* **Test Specification**: [`TST-SET-062`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L249) (*Advanced Tuning Preferences Clamping, Persistence, and Reset Verification*)
* **Branch**: `feature/ATT-1304`

---

## 1. Overview & Problem Statement

Prior to this feature, key sensor filtering and algorithmic heuristics across the tracking pipeline and battery saving mechanisms were hardcoded as static constants:
1. **Hardcoded Sensor Heuristics**: GPS accuracy threshold (`200m` in [`SpeedAndLocationDevice.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java)), altitude smoothing window (`21s` in [`VerticalSpeedAndSlopeDevice.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDevice.java)), and minimum speed for slope calculation (`0.5 m/s`) were fixed compile-time constants. Athletes with high-precision GPS units or those engaging in steep hiking/mountaineering could not tailor these thresholds to their activity dynamics.
2. **Hardcoded Battery Saver Heuristics**: The AMOLED Battery Saver ([`BatterySaverStateMachine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/batterysaver/BatterySaverStateMachine.kt)) hardcoded dimming factors (`0.25f` full dim, `0.50f` medium dim), slope cutoffs (`2.0%` flat, `5.0%` steep), wake-up duration (`15s`), and downward damping delay (`3s`).
3. **No UI or Safety Reset**: There was no dedicated settings UI to adjust these parameters, nor an atomic factory reset mechanism to recover from extreme configurations.

---

## 2. Architecture & Implementation Summary

### 2.1 Unified DataStore Preferences ([`TuningPreferencesDataStore.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt))
* Encapsulates defaults and strict safety boundaries in `TuningPreferencesDefaults`:
  * Full Dimming Brightness: `0.05f .. 0.50f` (Safety floor 5% prevents unreadable display).
  * Medium Dimming Brightness: `0.20f .. 0.90f` (Guaranteed $\ge$ full dimming factor).
  * Flat Slope Cutoff: `0.0% .. 5.0%` (Guaranteed $\le$ steep slope).
  * Steep Slope Cutoff: `2.0% .. 15.0%` (Guaranteed $\ge$ flat slope).
  * Wake-up Duration: `5s .. 60s`.
  * Downward Delay: `1s .. 15s`.
  * GPS Accuracy Threshold: `10m .. 500m`.
  * Altitude Smoothing Window: `5s .. 60s`.
  * Minimum Speed for Slope: `0.2 .. 2.0 m/s` (Guaranteed $\ge 0.2\text{ m/s}$ to prevent division by zero).
* Exposes reactive `tuningConfigFlow: Flow<TuningConfig>`.
* Implements defensive clamping (`coerceIn`) on read and write, and atomic `resetToDefaults()`.

### 2.2 Synchronous Java Bridge ([`SettingsDataStoreJavaHelper.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelper.kt))
* Added `@JvmStatic` helper methods: `getTuningConfig(context)`, `getGpsAccuracyThreshold(context)`, `getAltitudeFilterWindow(context)`, and `getSlopeMinSpeed(context)`.

### 2.3 AMOLED Battery Saver Dynamic Tuning
* [`BatterySaverModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/batterysaver/BatterySaverModel.kt): Introduced `BatterySaverTuningConfig` data class.
* [`BatterySaverStateMachine.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/batterysaver/BatterySaverStateMachine.kt): Supports dynamic threshold evaluation and dynamic dimming factor mapping with backward-compatible defaults.
* [`BatterySaverController.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/batterysaver/BatterySaverController.kt): Dynamically applies `tuningConfig` for wake-up duration, downward damping hysteresis, and level factors.
* [`TrackingTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt): Collects `tuningConfigFlow` and passes dynamic tuning config to `BatterySaverController` and sensor devices.

### 2.4 Sensor Drivers
* [`SpeedAndLocationDevice.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java): Dynamically queries `SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(mContext)`.
* [`VerticalSpeedAndSlopeDevice.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDevice.java): Dynamically queries `SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` and accepts dynamic `slopeMinSpeedMps`.

### 2.5 Navigation & Modern Compose UI
* **Vector Asset**: Created [`ic_tune.xml`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/drawable/ic_tune.xml).
* **Navigation Drawer**: Added `R.id.drawer_advanced_tuning` under `drawer__settings` in [`ids.xml`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/ids.xml), [`NavRoutes.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt), and [`AppNavigationDrawer.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt).
* **Dialog & BottomSheet**:
  * [`AdvancedTuningDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt): Pure Jetpack Compose bottom sheet with advisory warning notice card, grouped sliders, default tags (`Standard: X`), and atomic "Reset to Defaults" action button.
  * [`AdvancedTuningDialogFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialogFragment.kt): Standard `AppBottomSheetDialogFragment` host.
  * [`DisplaySettingsDialog.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt): Added direct shortcut button to Advanced Tuning in the savings section.
  * [`ATrainingTrackerApp.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt): Integrated `SettingsBottomSheetType.ADVANCED_TUNING` into the root compose navigation graph.

### 2.6 100% 9-Language Localization Parity
* Implemented all 18 strings in `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, and `values-pt`.

---

## 3. Verification & Test Evidence

### 3.1 Unit Test Suite (`TuningConfigTest.kt` & `BatterySaverDynamicTuningTest.kt`)
Added comprehensive test coverage for `REQ-SET-073` / `TST-SET-062`:
1. `TuningConfigTest`:
   - Validates all 9 default parameter values match specifications.
   - Validates lower and upper bound constants.
   - Validates relational invariants (`medium >= full`, `steep >= flat`, safety floors).
   - Validates clamping simulation under extreme inputs.
2. `BatterySaverDynamicTuningTest`:
   - Validates `BatterySaverStateMachine` with customized slope thresholds.
   - Validates custom dimming factor evaluations.
   - Validates `BatterySaverController` dynamic updates, custom downward hysteresis delays, and custom wakeup durations.

**Command**:
```bash
./gradlew testDebugUnitTest --tests "*Tuning*"
```
**Result**: `BUILD SUCCESSFUL` (all tests passed cleanly).

### 3.2 Full Project Regression Suite
Executed the complete clean-room unit test suite:
```bash
./gradlew testDebugUnitTest
```
**Result**: `BUILD SUCCESSFUL` (zero regressions across all application modules).

---

## 4. Traceability & Stage Status

| Artifact / Entity | ID | Status | Notes |
|:---|:---|:---|:---|
| Requirement | `REQ-SET-073` | **Verified** | Traceability updated in `docs/requirements.md` |
| Test Case | `TST-SET-062` | **Verified** | Traceability updated in `docs/tests.md` |
| Subtask Stage 4 | `ATT-1634` | **Erledigt** | Gate 4 Audit Passed |
| Subtask Stage 5 | `ATT-1635` | **In Review** | Ready for Gate 5 audit |
| Parent Ticket | `ATT-1304` | **Test** | Transitioning to Final Review upon sprint merge |
