# Stage 2: Requirement & Test Specification - ATT-5072: Altitude Calibration Dialog Known Location Toggle

**Ticket**: [ATT-5072](https://rainerblind.atlassian.net/browse/ATT-5072)  
**Sub-task**: [[ATT-5170]](https://rainerblind.atlassian.net/browse/ATT-5170) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-047` (`REQ-TRK-047.1`)  
**Test Spec ID**: `TST-TRK-047` (`TST-TRK-047.6`, `TST-TRK-047.7`)  
**Branch**: `feature/ATT-5072`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-TRK-047.1)

### 1.1 Problem Statement & Rationale
In `SensorSourceDialog.kt`, *"Als Lieblingsort speichern"* (*"Save as Known Location"*) was checked by default, and the location name input was unconditionally rendered. This caused landmark pollution in `KnownLocations.db` when athletes simply intended to calibrate their barometric altimeter at arbitrary points without creating a favorite location. Additionally, when physically present at an existing saved *Lieblingsort*, the dialog failed to identify the location, displayed the redundant save checkbox, and did not pre-fill or update the existing record's reference altitude.

### 1.2 Functional & Architectural Requirements
1. When `matchedLocation == null`:
   - The "Save as Known Location" checkbox SHALL default to unchecked (`false`).
   - The location name text input field SHALL be conditionally rendered and enabled only when the checkbox is checked (`true`).
   - Confirming calibration with save unchecked SHALL dispatch altimeter calibration directly without writing to `KnownLocations.db`.
2. When `matchedLocation != null`:
   - The dialog SHALL display the matched location name badge.
   - The save checkbox and new location name input field SHALL be hidden.
   - The reference altitude input SHALL pre-fill with the matched location's stored reference altitude.
   - Confirming calibration SHALL update the existing `KnownLocations.db` record with `ElevationSource.MANUAL_USER` and `isLocked = true`, and dispatch altimeter calibration.

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-TRK-047.1` (*Cockpit Header Manual Altitude Calibration & Geofence Persistence*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-4845` (commit `69ba488d`, Sprint 2026-44.2).
* **Root Reason for Existing Formulation**: Defaulting `saveLocation` to true was intended to make landmark bookmarking seamless, but in practice created unwanted landmark clutter.
* **Preservation of Core Invariants**: Immediate calibration dispatch to `AltitudeFromPressureDevice`, piecewise-linear drift correction (`REQ-TRK-047.2`), unit formatting, and 9-language parity remain strictly invariant.

---

## 2. Test Specification & Traceability Matrix

### Test Case 1: Unchecked by Default & Conditional Name Input (TST-TRK-047.6)
* **Scope**: Compose UI & State Unit Test (`SensorSourceDialogAltitudeCalibrationTest.kt`)
* **Expected Result**: When `matchedLocation == null`, `saveLocation` defaults to `false`, location name input is not rendered; checking the box displays the input.

### Test Case 2: Matched Known Location Recognition & Update (TST-TRK-047.7)
* **Scope**: Compose UI & ViewModel Unit Test (`SensorSourceDialogAltitudeCalibrationTest.kt`, `TrackingTabsViewModelTest.kt`)
* **Expected Result**: When `matchedLocation != null`, matched name is shown, save checkbox is hidden, reference altitude is pre-filled, and confirming updates the existing record in `KnownLocations.db`.

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-047.6` | Compose UI / Unit | `SensorSourceDialog` (default unchecked, conditional input) | `REQ-TRK-047.1` | Specified |
| `TST-TRK-047.7` | Compose / ViewModel | `SensorSourceDialog` & `TrackingTabsViewModel.calibrateAltitude` | `REQ-TRK-047.1` | Specified |
| `TST-TRK-047.1` | Regression | Existing altitude calibration dispatch & unit formatting | `REQ-TRK-047.1` | Specified |
| `TST-TRK-047.5` | Localization | `TranslationParityTest` (all altitude calibration strings) | `REQ-TRK-047.4` | Specified |
