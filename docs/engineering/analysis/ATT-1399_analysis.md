# Stage 1 Analysis: ATT-1399 - Lieblingsorte: Live Cockpit Location & Barometer Calibration Feedback

**Ticket**: [ATT-1399](https://atrainingtracker.atlassian.net/browse/ATT-1399)  
**Sub-task**: [ATT-1569](https://atrainingtracker.atlassian.net/browse/ATT-1569) (`[Analysis]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Branch**: `feature/ATT-1399`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Statement & Motivation

In `aTrainingTracker`, "Lieblingsorte" (known start locations stored in `StartLocation2Altitude.db`) perform silent, automated barometric altimeter calibration and geofence matching via `AltitudeFromPressureDevice.java` and `KnownLocationsDatabaseManager.java`.

### Current Deficiency
When an athlete steps outside to begin a workout, the app silently resolves their GPS fix against `StartLocation2Altitude.db` or queries DEM elevation, adjusts `mAltitudeCorrection`, and shifts barometric baseline readings. However:
1. **Zero Visual Feedback in Cockpit**: The athlete has no visual indicator or confirmation that their starting location has been identified (e.g. "Zuhause", "Büro", "Olympiapark") and that the barometric altimeter is calibrated to ground truth.
2. **Lack of Sensor Trust**: Athletes observe changing altitude numbers but cannot discern whether the reading is an uncalibrated default barometric drift or a calibrated ground-truth altitude.
3. **Missing Decoupled User Preference**: There is currently no user setting to control whether this feedback appears in the Cockpit, violating the design requirement that UI feedback components must be completely decoupled from the underlying background tracking and calibration engine.

---

## 2. Root Cause Analysis (Forensic Investigation & Gap Analysis)

### Call-Site & Architecture Investigation

1. **Background Calibration Layer (`AltitudeFromPressureDevice.java`)**:
   - Lines 130-158: In `initPressureSensor()`, once GPS latitude and longitude become available:
     ```java
     KnownLocationsDatabaseManager knownLocationsDb = KnownLocationsDatabaseManager.getInstance(mContext);
     KnownLocationsDatabaseManager.MyLocation myLocation = knownLocationsDb.getMyLocation(currentLatLng);
     if (myLocation != null) {
         setAltitudeCorrection(myLocation.altitude);
         mAltitudeSensor.newValue(myLocation.altitude);
         ...
     }
     ```
   - In `setAltitudeCorrection()`, when `mAltitudeCorrection != 0.0`, an Android broadcast `ALTITUDE_CORRECTION_INTENT` is dispatched with `ALTITUDE_CORRECTION_VALUE`.
   - *Gap*: The background calibration engine operates purely in the sensor service domain. There is no reactive UI state exposed indicating location match and calibration status to the presentation layer.

2. **Repository & Reactive Stream Layer (`BANALServiceRepository.kt`, `KnownLocationsRepository.kt`)**:
   - `BANALServiceRepository.kt` exposes `currentLocation: StateFlow<LatLng?>` which updates every second in the sensor observation loop.
   - `KnownLocationsRepository.kt` exposes `locationsFlow: StateFlow<List<KnownLocationItem>>` representing all saved start locations.
   - *Gap*: No reactive pipeline exists combining current GPS position, known location geofencing, and user preference toggle into a unified `LocationCalibrationStatus` for the tracking screens.

3. **Cockpit Presentation Layer (`TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`, `SensorStatus.kt`)**:
   - `TrackingTabsScreen.kt` renders the top header bar with `SensorStatus(activeSensors, ...)` on `ScreenMode.TRACKING` above `PrimaryScrollableTabRow`.
   - *Gap*: No composable chip or badge exists to display recognized location and calibrated altitude.

4. **Preferences & Settings Layer (`TrainingApplication.java`, `DisplaySettingsDialog.kt`)**:
   - `TrainingApplication.java` manages display options via `getDisplayOptions()` and `SP_DISPLAY_OPTIONS`.
   - `DisplaySettingsDialog.kt` provides user toggles for cockpit preferences (`forcePortrait`, `keepScreenOn`, `noUnlocking`, `CockpitThemeMode`, `DisplayBrightnessMode`).
   - *Gap*: Missing dedicated preference key (`SP_LIEBLINGSORT_COCKPIT_FEEDBACK`) and toggle row in `DisplaySettingsDialog.kt`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement preference toggle `TrainingApplication.isLieblingsortCockpitFeedbackEnabled()` (default `true`) and `setLieblingsortCockpitFeedbackEnabled(Boolean)`.
  * Add toggle row to `DisplaySettingsDialog.kt`: "Lieblingsort & Kalibrierungs-Feedback" / "Favorite Location & Calibration Feedback".
  * Expose reactive `locationCalibrationStatus: StateFlow<LocationCalibrationStatus?>` in `TrackingTabsViewModel` that matches `currentLocation` against known location geofences when feedback is enabled.
  * Implement `LocationCalibrationBadge(status: LocationCalibrationStatus?, isVisible: Boolean)` composable in `TrackingTabsScreen.kt` header below `SensorStatus`.
  * Support animated enter/exit transitions (`AnimatedVisibility`).
  * Ensure full 9-language localization parity across all supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  * Comprehensive unit tests for state emission, distance matching, and UI rendering.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Altering the background barometric calibration algorithm or broadcast contracts in `AltitudeFromPressureDevice.java` (strictly preserved).
  * Mutating SQLite schema or altering `KnownLocationsDatabaseManager.java` transaction semantics.
  * Modifying workout start counting logic (`REQ-DAT-015` in `TrackerService.java`).
  * Modifying route clustering or workout auto-naming (`ATT-1398`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Net-New Requirement**: `REQ-UI-167` (*Lieblingsorte Cockpit Location & Barometer Calibration Feedback*).
* **Existing Requirements Audit**:
  * `REQ-CON-011` (*Barometric Cold-Start Baseline Protection*): Preserved. No uncalibrated pressure readings emitted.
  * `REQ-CON-013` (*Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch*): Preserved. Calibration broadcasts and math remain untouched.
  * `REQ-DAT-007` (*Automated Altitude Reference Discovery & Stable Reference Elevation Preservation*): Preserved. No running mean altitude corruption.
  * `REQ-DAT-014` (*Internet DEM Reference Altitude Retrieval, Spatial Caching & Legacy Location Healing*): Preserved. Spatial geofences and caching remain intact.
  * `REQ-DAT-015` (*Decoupled Barometric Altimeter Calibration and Authoritative Workout Start Counting*): Preserved. Hit counts exclusively count on workout starts, never during sensor warmup or cockpit display.
  * `REQ-SET-052` (*Display Settings Modal Sheet Architecture*): Preserved. `DisplaySettingsDialog` preserves `AppModalBottomSheet` and `AppDialogActions.SaveCancel`.

---

## 5. Architectural Strategy & High-Level Solution

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
│   - Checks TrainingApplication preference toggle       │
│   - Calculates distance to geofences                   │
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
│     [AnimatedVisibility: slideIn / fadeOut]            │
└────────────────────────────────────────────────────────┘
```

### Key Data Structures
```kotlin
data class LocationCalibrationStatus(
    val locationId: Long,
    val locationName: String,
    val referenceAltitude: Double,
    val isCalibrated: Boolean = true,
    val source: ElevationSource = ElevationSource.UNKNOWN
)
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. *Decoupled Execution Invariant*: Background tracking service and sensor calibration operate completely independently of whether the UI badge is displayed or disabled.
  2. *Single-Direction Data Flow*: The UI layer strictly observes existing repositories and StateFlows without triggering database writes or network lookups.
  3. *Zero Overhead when Idle or Disabled*: If disabled by user preference or outside any geofence, `locationCalibrationStatus` evaluates to `null` and renders nothing.
  4. *Parent Ticket Human Decision Gate*: Parent ticket `ATT-1399` remains in `Analysis` until all 5 subtasks complete and Human review occurs in Ceremony 2.

* **Risk Rating**: **LOW**  
  *Justification*: Net-new UI composable and read-only StateFlow observation. Background sensor engine and SQLite database structures remain completely untouched.
