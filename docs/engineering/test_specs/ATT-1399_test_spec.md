# Stage 2: Requirement & Test Specification - ATT-1399: Lieblingsorte: Live Cockpit Location & Barometer Calibration Feedback

**Ticket**: [ATT-1399](https://atrainingtracker.atlassian.net/browse/ATT-1399)  
**Sub-task**: [ATT-1570](https://atrainingtracker.atlassian.net/browse/ATT-1570) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Requirement Mapping**: `REQ-UI-183` (*Lieblingsorte: Live Cockpit Location & Barometer Calibration Feedback*)  
**Test Spec ID**: `TST-UI-136`  
**Branch**: `feature/ATT-1399`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Specification (REQ-UI-183)

### 1.1 Problem Statement & Rationale
In `aTrainingTracker`, "Lieblingsorte" (known start locations stored in `StartLocation2Altitude.db`) perform automated barometric altimeter calibration and geofence matching via `AltitudeFromPressureDevice.java` and `KnownLocationsDatabaseManager.java`. However, this process executes silently in the background. The athlete has no visual feedback in the tracking cockpit confirming that their current location (e.g. "Zuhause", "Büro") has been recognized and that the altimeter has calibrated to the authoritative ground-truth reference elevation. Furthermore, the feedback must be decoupled by providing a dedicated user preference toggle in Display Settings, ensuring athletes can enable or disable the indicator without altering background sensor calibration.

### 1.2 Functional & Architectural Requirements
The system SHALL provide visible, reactive feedback in the active workout tracking cockpit confirming when the athlete is located within the geofence of a known start location (Lieblingsort) and that the barometric altimeter is calibrated to reference altitude:

1. **Decoupled Architecture & User Preference Control**:
   - The feedback component SHALL be strictly decoupled from the background tracking and barometric calibration engine (`AltitudeFromPressureDevice`, `TrackerService`).
   - A dedicated preference `SP_LIEBLINGSORT_COCKPIT_FEEDBACK` (`lieblingsortCockpitFeedback`) with default `true` SHALL be exposed in `TrainingApplication.java`:
     - `TrainingApplication.isLieblingsortCockpitFeedbackEnabled(): Boolean`
     - `TrainingApplication.setLieblingsortCockpitFeedbackEnabled(Boolean): Unit`
   - In `DisplaySettingsDialog.kt`, a toggle option titled "Lieblingsort & Kalibrierungs-Feedback" (`@string/prefs_lieblingsort_feedback_title`) SHALL allow athletes to stage, save, or discard this preference.

2. **Reactive State Pipeline (`TrackingTabsViewModel.kt`)**:
   - `TrackingTabsViewModel` SHALL combine `banalServiceRepository.currentLocation` (`StateFlow<LatLng?>`), `knownLocationsRepository.locationsFlow` (`StateFlow<List<KnownLocationItem>>`), and the preference toggle state.
   - When feedback is enabled and `currentLocation` is within the geofence radius ($r$) of an existing known location ($d < r$), the system SHALL emit `LocationCalibrationStatus(locationId, locationName, referenceAltitude, isCalibrated, source)`.
   - When outside any known location geofence, or when feedback is disabled, `locationCalibrationStatus` SHALL emit `null`.

3. **Cockpit Status Presentation & Smooth Transitions (`LocationCalibrationBadge.kt`, `TrackingTabsScreen.kt`)**:
   - In `TrackingTabsScreen.kt`, within the header column above `PrimaryScrollableTabRow` (on `ScreenMode.TRACKING`), the system SHALL render `LocationCalibrationBadge(status = locationCalibrationStatus)`.
   - The badge SHALL feature `AnimatedVisibility` (fade and vertical expand/shrink) to transition smoothly as GPS fix arrives or location changes.
   - The badge SHALL render a theme-aware surface chip containing a location pin icon (`Icons.Default.Place` or `R.drawable.my_locations`) and formatted text: `"%1$s (%2$d m %3$s)"` / `"%1$s (%2$d m kalibriert)"` (`@string/location_calibrated_format`), dynamically reflecting unit preferences (Metric meters vs. Imperial feet).

4. **100% Localization Parity Across 9 Locales**:
   - String resources `prefs_lieblingsort_feedback_title`, `prefs_lieblingsort_feedback_summary`, `location_calibrated_format`, and `calibrated` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Cockpit Badge Display on Geofence Match)**:
  * *Given* an athlete with `isLieblingsortCockpitFeedbackEnabled() == true` and GPS coordinates within 200m of known location "Zuhause" (reference altitude 520m),
  * *When* viewing the tracking cockpit on `ScreenMode.TRACKING`,
  * *Then* `LocationCalibrationBadge` SHALL be visible displaying 📍 "Zuhause (520 m kalibriert)".
* **Criterion 2 (Geofence Exit Transition)**:
  * *Given* the athlete moving away from the known location beyond its geofence radius,
  * *When* `currentLocation` updates,
  * *Then* `locationCalibrationStatus` SHALL emit `null` and `LocationCalibrationBadge` SHALL animate out smoothly.
* **Criterion 3 (Preference Disabling & Decoupled Invariant)**:
  * *Given* an athlete disabling the feedback toggle in `DisplaySettingsDialog`,
  * *When* saving settings,
  * *Then* `LocationCalibrationBadge` SHALL remain hidden, while background calibration in `AltitudeFromPressureDevice` continues to operate uninterrupted.
* **Criterion 4 (Unit Conversion Consistency)**:
  * *Given* the application configured for Imperial units,
  * *When* viewing the badge for a location at 520.0 meters,
  * *Then* the altitude readout SHALL display in feet (e.g. "1706 ft").

### 1.4 System Invariants
* Background barometric calibration calculations and broadcasts in `AltitudeFromPressureDevice.java` (`REQ-CON-011`, `REQ-CON-013`) MUST NOT be altered.
* SQLite schema V5 and start count recording (`REQ-DAT-015`) MUST NOT be altered.
* Database operations in `KnownLocationsRepository` MUST remain serialized on `KnownLocationsDB-Thread`.
* Zero regression across the entire clean-room test suite.

---

## 2. Test Specification (TST-UI-136)

### Test Case 1: `testLocationCalibrationStatus_insideGeofence_emitsStatus` (`TST-UI-136.1`)
* **Scope**: ViewModel Reactive Pipeline Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelLocationTest.kt`
* **Preconditions**: Mock `BANALServiceRepository` emitting `LatLng(48.137, 11.576)`, `KnownLocationsRepository` containing location "Zuhause" at `(48.137, 11.576)` with radius 200m and altitude 520m, feedback enabled.
* **Action**: Observe `locationCalibrationStatus` StateFlow.
* **Expected Result**: Emits `LocationCalibrationStatus` with `locationName == "Zuhause"`, `referenceAltitude == 520.0`, `isCalibrated == true`.

### Test Case 2: `testLocationCalibrationStatus_outsideGeofence_emitsNull` (`TST-UI-136.2`)
* **Scope**: ViewModel Reactive Pipeline Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelLocationTest.kt`
* **Preconditions**: Mock `BANALServiceRepository` emitting `LatLng(48.200, 11.700)` (> 5km away from known location).
* **Action**: Observe `locationCalibrationStatus` StateFlow.
* **Expected Result**: Emits `null`.

### Test Case 3: `testLocationCalibrationStatus_preferenceDisabled_emitsNull` (`TST-UI-136.3`)
* **Scope**: Preference Toggle Decoupling Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelLocationTest.kt`
* **Preconditions**: Location matches known location, but `TrainingApplication.setLieblingsortCockpitFeedbackEnabled(false)`.
* **Action**: Observe `locationCalibrationStatus` StateFlow.
* **Expected Result**: Emits `null`.

### Test Case 4: `testDisplaySettingsDialog_lieblingsortToggle_stagesAndPersists` (`TST-UI-136.4`)
* **Scope**: Compose Dialog Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialogTest.kt`
* **Preconditions**: `DisplaySettingsDialog` rendered with initial preference `true`.
* **Action**: Toggle switch to `false`, tap "Speichern".
* **Expected Result**: `TrainingApplication.isLieblingsortCockpitFeedbackEnabled()` returns `false`.

### Test Case 5: 9-Language Localization Parity Audit (`TST-UI-136.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.java`
* **Goal**: Verify string presence and matching `%s`/`%d` specifiers across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
  * Keys: `prefs_lieblingsort_feedback_title`, `prefs_lieblingsort_feedback_summary`, `location_calibrated_format`, `calibrated`
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 6: Clean-Room Regression Suite (`TST-UI-136.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero broken invariants.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-136.1` | ViewModel | `TrackingTabsViewModel.locationCalibrationStatus` | `REQ-UI-183` | Specified |
| `TST-UI-136.2` | ViewModel | `TrackingTabsViewModel.locationCalibrationStatus` | `REQ-UI-183` | Specified |
| `TST-UI-136.3` | ViewModel | `TrackingTabsViewModel.locationCalibrationStatus` | `REQ-UI-183` | Specified |
| `TST-UI-136.4` | UI / Settings | `DisplaySettingsDialog` | `REQ-UI-183`, `REQ-SET-052` | Specified |
| `TST-UI-136.5` | Localization | `TranslationParityTest` | `REQ-UI-183`, `REQ-UI-106` | Specified |
| `TST-UI-136.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
