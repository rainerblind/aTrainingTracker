# Stage 3: Implementation Plan - ATT-1399: Lieblingsorte: Live Cockpit Location & Barometer Calibration Feedback

**Ticket**: [ATT-1399](https://atrainingtracker.atlassian.net/browse/ATT-1399)  
**Sub-task**: [ATT-1571](https://atrainingtracker.atlassian.net/browse/ATT-1571) (`[Design] Architecture & Implementation Plan`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Requirement Mapping**: `REQ-UI-183`  
**Test Mapping**: `TST-UI-136`  
**Branch**: `feature/ATT-1399`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

In `aTrainingTracker`, "Lieblingsorte" (known start locations stored in `StartLocation2Altitude.db`) perform automated barometric altimeter calibration and geofence matching via `AltitudeFromPressureDevice.java` and `KnownLocationsDatabaseManager.java`.

Currently, this calibration executes silently in the background:
* Athletes have no visual indicator or confirmation that their starting location has been identified (e.g. "Zuhause", "Büro", "Olympiapark") and that the barometric altimeter has calibrated to the authoritative ground-truth reference elevation.
* The feedback component must be completely decoupled from the underlying tracking and calibration engine via a user preference toggle in Display Settings, ensuring it can be enabled or disabled without affecting barometric calibration.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-183` (*Lieblingsorte: Live Cockpit Location & Barometer Calibration Feedback*)
* **Test Mapping**: `TST-UI-136` (*Live Cockpit Location & Barometer Calibration Feedback Verification*)
* **Living Documentation**: `docs/requirements.md`, `docs/tests.md`

---

## 3. System Invariants & Preserved Behavior

1. **Decoupled Architecture & Background Engine Immunity**:
   - The underlying barometric altitude calibration logic in `AltitudeFromPressureDevice.java` and `TrackerService.java` MUST NOT be altered.
   - The UI indicator is purely observational (read-only) and observes existing reactive streams.
2. **Preference Decoupling Invariant**:
   - Toggling the cockpit feedback setting to `false` in Display Settings MUST NOT disable, suppress, or modify the background barometric calibration mechanism.
3. **Database Concurrency & Schema Safety**:
   - Zero SQLite schema alterations (`DB_VERSION = 5` preserved).
   - All spatial calculations operate in memory via reactive flows without database locks or blocking calls on the UI thread.
4. **Parent Ticket Human Decision Gate**:
   - Subtasks transition to `Erledigt` upon passing review audits; the parent ticket `ATT-1399` advances to `Final Review (Human)` and is never marked `Erledigt` by automated agents.

---

## 4. Proposed Architectural Changes

```
┌────────────────────────────────────────────────────────┐
│                   Data / State Layer                   │
├─────────────────────────┬──────────────────────────────┤
│ BANALServiceRepository  │ KnownLocationsRepository     │
│   currentLocation       │   locationsFlow              │
│   (StateFlow<LatLng?>)  │   (StateFlow<List<...>>)     │
└────────────┬────────────┴──────────────┬───────────────┘
             │                           │
             ▼                           ▼
┌────────────────────────────────────────────────────────┐
│ TrackingTabsViewModel                                  │
│   - Combines currentLocation & locationsFlow           │
│   - Evaluates isLieblingsortCockpitFeedbackEnabled     │
│   - Computes distance to geofences (d < radius)        │
│   - Emits locationCalibrationStatus: StateFlow<...>    │
└────────────────────────────┬───────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────┐
│ TrackingTabsScreen (UI Header)                         │
│   - Surface / Column (ScreenMode.TRACKING)             │
│   - SensorStatus(...)                                  │
│   - LocationCalibrationBadge(...)                      │
│     📍 "Zuhause (520 m kalibriert)"                    │
│     [AnimatedVisibility: expandVertically + fadeIn]    │
└────────────────────────────────────────────────────────┘
```

### 4.1 Preferences Layer (`TrainingApplication.java`)
* Add SharedPreferences key: `SP_LIEBLINGSORT_COCKPIT_FEEDBACK = "lieblingsortCockpitFeedback"`.
* Default: `DEFAULT_LIEBLINGSORT_COCKPIT_FEEDBACK = true`.
* Methods:
  - `public static boolean isLieblingsortCockpitFeedbackEnabled()`
  - `public static void setLieblingsortCockpitFeedbackEnabled(boolean enabled)`

### 4.2 Settings Presentation Layer (`DisplaySettingsDialog.kt`)
* Stage `isLieblingsortFeedbackEnabled` state from `TrainingApplication`.
* Add `DisplayOptionToggle` row: "Lieblingsort & Kalibrierungs-Feedback" (`@string/prefs_lieblingsort_feedback_title`).
* On save: persist to `TrainingApplication.setLieblingsortCockpitFeedbackEnabled(...)`.

### 4.3 Reactive State Layer (`TrackingTabsViewModel.kt`)
* Model:
  ```kotlin
  data class LocationCalibrationStatus(
      val locationId: Long,
      val locationName: String,
      val referenceAltitude: Double,
      val isCalibrated: Boolean = true,
      val source: ElevationSource = ElevationSource.UNKNOWN
  )
  ```
* Pipeline in `TrackingTabsViewModel`:
  - Observe `banalServiceRepository.currentLocation` and `knownLocationsRepository.locationsFlow`.
  - For each non-null `currentLocation`, evaluate spatial distance against known locations:
    $$\text{distance} = \text{distanceBetween}(\text{currentLocation}, \text{item.latLng})$$
  - If $\text{distance} < \text{item.radius}$ and feedback is enabled, select closest matching location and emit `LocationCalibrationStatus`.
  - If outside all geofences or feedback disabled, emit `null`.

### 4.4 UI Presentation Layer (`LocationCalibrationBadge.kt`, `TrackingTabsScreen.kt`)
* Composable `LocationCalibrationBadge(status: LocationCalibrationStatus?, isMetric: Boolean, modifier: Modifier = Modifier)`.
* Wraps content in `AnimatedVisibility(visible = status != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically())`.
* Styled as a subtle Material 3 chip with rounded corners (8.dp), surfaceContainerHigh container, primary tinted pin icon (`Icons.Default.Place`), and formatted text via `stringResource(R.string.location_calibrated_format, status.locationName, formattedAltitude)`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization Resources
* **Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Keys Added**:
  - `prefs_lieblingsort_feedback_title`
  - `prefs_lieblingsort_feedback_summary`
  - `location_calibrated_format` (with `%1$s` and `%2$s`)

### Step 2: Preference Storage & Settings Toggle
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt`
* **Changes**:
  - Add `isLieblingsortCockpitFeedbackEnabled()` and `setLieblingsortCockpitFeedbackEnabled(boolean)`.
  - Add toggle switch in `DisplaySettingsDialog.kt`.

### Step 3: Domain Model & Reactive Pipeline in ViewModel
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/LocationCalibrationStatus.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt`
* **Changes**:
  - Implement `LocationCalibrationStatus` data class.
  - Inject or access `KnownLocationsRepository`.
  - Implement reactive `locationCalibrationStatus: StateFlow<LocationCalibrationStatus?>`.

### Step 4: Composable UI Badge & Cockpit Header Integration
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/LocationCalibrationBadge.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* **Changes**:
  - Author `LocationCalibrationBadge` composable with `AnimatedVisibility`.
  - Render badge in `TrackingTabsScreen.kt` header below `SensorStatus`.

### Step 5: Unit Tests
* **Target Test Classes**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelLocationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/LocationCalibrationBadgeTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialogTest.kt`
* **Targeted Test Execution**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelLocationTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsDialogTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Execute targeted unit tests for ViewModel, UI badge, and Display Settings.
  - Execute `TranslationParityTest` for 100% 9-language parity.
  - Execute clean-room full suite regression (`./gradlew testDebugUnitTest`).
* **Rollback Plan**:
  - Changes are isolated to feature branch `feature/ATT-1399`. If regressions or unexpected issues occur, the branch can be cleanly reset or reverted without impacting the sprint branch `sprint/2026-40.3`.
