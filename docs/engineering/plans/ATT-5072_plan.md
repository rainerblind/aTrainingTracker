# Stage 3: Implementation Plan - ATT-5072: Altitude Calibration Dialog Known Location Toggle

**Ticket**: [ATT-5072](https://rainerblind.atlassian.net/browse/ATT-5072)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-047` (`REQ-TRK-047.1`)  
**Test Mapping**: `TST-TRK-047` (`TST-TRK-047.6`, `TST-TRK-047.7`, `TST-TRK-047.1`, `TST-TRK-047.5`)  
**Branch**: `feature/ATT-5072`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
In `SensorSourceDialog.kt`, the checkbox *"Als Lieblingsort speichern"* (*"Save as Known Location"*) defaults to `true`, and the location name input is unconditionally visible. This causes unwanted landmark clutter in `KnownLocations.db` during manual altimeter re-baselining at arbitrary coordinates. Additionally, when physically present at an existing saved *Lieblingsort*, the dialog fails to recognize the matched location, displays the redundant checkbox, and does not pre-fill or update the existing *Lieblingsort*'s reference altitude.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-047.1` (*Cockpit Header Manual Altitude Calibration & Geofence Persistence*)
* **Test Mapping**: `TST-TRK-047.6` (default unchecked & conditional name input), `TST-TRK-047.7` (matched location badge, pre-fill & database update), `TST-TRK-047.1` (calibration dispatch), `TST-TRK-047.5` (9-language parity)

---

## 3. System Invariants & Preserved Behavior
1. **Calibration Dispatch**: Manual calibration dispatch via `BANALServiceRepository.calibrateAltimeter` remains immediate and unaltered.
2. **Backward Compatibility**: `SensorSourceDialog` parameters and callback contracts remain backward-compatible with default arguments.
3. **Database Integrity**: Unchecked calibration never writes to `KnownLocations.db`; matched location updates apply `ElevationSource.MANUAL_USER` and `isLocked = true`.
4. **9-Language Parity**: All strings maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (`TranslationParityTest`).

---

## 4. Proposed Architectural Changes

### Component 1: Altitude Calibration Dialog Presentation (`SensorSourceDialog.kt`)
* Add parameter `matchedLocation: LocationCalibrationStatus? = null`.
* Case A (`matchedLocation == null`): Default `saveLocation` to `false`; conditionally render `locationNameInput` only when `saveLocation == true`.
* Case B (`matchedLocation != null`): Display matched location badge (`altitude_calibration_matched_location`); hide `saveLocation` checkbox; pre-fill `altitudeInput` from `matchedLocation.referenceAltitude`.

### Component 2: Plumbing via SensorStatus & TrackingCockpit (`SensorStatus.kt`, `TrackingTabsScreen.kt`)
* Add `matchedLocation: LocationCalibrationStatus? = null` parameter to `SensorStatus.kt` forwarding to `SensorSourceDialog`.
* Pass `locationCalibrationStatus` from `TrackingTabsScreen.kt` to `SensorStatus`.

### Component 3: Matched Location Altitude Updating (`TrackingTabsViewModel.kt`)
* In `calibrateAltitude`, check `locationCalibrationStatus.value`:
  * If matched location exists, update record via `KnownLocationsDatabaseManager.getInstance().updateLocation(id, name, altitude, ElevationSource.MANUAL_USER, true)` and reload locations.
  * Else, execute existing `upsertLocationByGeofence` if `saveAsKnownLocation == true`.

### UI Consistency (Rule 23)
* **Reference screens**: `SensorSourceDialog.kt` / `ControlTrackingScreen.kt` (`LocationCalibrationBadge`).
* **Reused tokens**: `MaterialTheme.colorScheme.surfaceVariant`, `outlineVariant`, `primary`, `typography.bodyMedium`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: String Resources (9-Language Parity)
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Add `altitude_calibration_matched_location` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Step 2: Dialog & SensorStatus Presentation
* Files: `SensorSourceDialog.kt`, `SensorStatus.kt`, `TrackingTabsScreen.kt`
* Implement `matchedLocation` parameter, default unchecked `saveLocation`, conditional name input, and matched location header.

### Step 3: ViewModel Calibration & Persistence
* Files: `TrackingTabsViewModel.kt`
* Update `calibrateAltitude` to update matched location by ID with `ElevationSource.MANUAL_USER` and `isLocked = true`.

### Step 4: Unit & Localization Tests
* Files: `SensorSourceDialogAltitudeCalibrationTest.kt`, `TranslationParityTest.kt`
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogAltitudeCalibrationTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted unit tests and `verify_requirement_governance.py`.
* **Rollback**: Isolated branch `feature/ATT-5072`; clean branch drop if needed.
