# Stage 1 Analysis: ATT-5072 - Altitude calibration dialog: make 'Save as Known Location' unchecked by default and only show/enable Location Name when checked

**Ticket**: [ATT-5072](https://rainerblind.atlassian.net/browse/ATT-5072)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5072`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
Following the introduction of manual altitude calibration in ATT-4845 (`REQ-TRK-047`):
1. In `SensorSourceDialog.kt`, the checkbox *"Als Lieblingsort speichern"* (*"Save as Known Location"*) is checked (`true`) by default, and the location name input is unconditionally visible. This causes unwanted landmark clutter in `KnownLocations.db` when athletes simply want to re-baseline their barometric altimeter at arbitrary waypoints without creating a permanent favorite location.
2. When the athlete is already physically present at an existing saved *Lieblingsort*, the dialog fails to recognize or display the matched location name, displays the redundant "Save as Known Location" checkbox, and does not pre-fill or update the existing *Lieblingsort*'s reference altitude upon calibration.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Default State & UI Visibility in `SensorSourceDialog.kt`**:
  * Line 138 hardcodes `var saveLocation by remember { mutableStateOf(true) }`.
  * `locationNameInput` (`OutlinedTextField`, lines 179–185) is unconditionally rendered above the checkbox regardless of `saveLocation`.
* **Missing Matched Location Context in `SensorSourceDialog`**:
  * `TrackingTabsViewModel.kt` already computes and exposes `val locationCalibrationStatus: StateFlow<LocationCalibrationStatus?>`.
  * While `TrackingTabsScreen.kt` collects `locationCalibrationStatus`, it only routes it to `ControlTrackingScreen.kt`, omitting it from `SensorStatus` and `SensorSourceDialog`.
* **Hypothesis Verification (ATT-3534)**:
  * Verified: Unchecked `saveLocation` cleanly bypasses `upsertLocationByGeofence` in `TrackingTabsViewModel.calibrateAltitude:553`, only invoking `banalServiceRepository.calibrateAltimeter`.
  * Verified: `KnownLocationsDatabaseManager.java:320` already provides `updateLocation(id, name, altitude, source, isLocked)`, enabling direct locked elevation updates (`ElevationSource.MANUAL_USER`) for matched locations without geofence duplication.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Set `saveLocation` to `false` by default when `matchedKnownLocation == null`.
  2. Conditionally show the location name input field only when `saveLocation == true`.
  3. When `matchedKnownLocation != null`, display the matched location name, hide the redundant save checkbox, pre-fill altitude with the stored reference altitude, and update the existing location record in `KnownLocations.db` on calibration.
* **Out-of-Scope Non-Goals**:
  1. Altering retroactive elevation history correction math or piecewise-linear drift algorithms (`REQ-TRK-047.2`).
  2. Modifying geofence transition detection radii or barometric sensor hardware interfaces.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-TRK-047.1` (*Cockpit Header Manual Altitude Calibration & Geofence Persistence*).
* **Historical Origin & Commit Trace**: `ATT-4845` (Sprint 2026-44.2) introduced manual altitude calibration in `SensorSourceDialog.kt`.
* **Root Reason for Existing Formulation**: Defaulting `saveLocation` to `true` was intended to facilitate easy landmark creation, but user feedback showed it polluted the landmark database with transient workout coordinates.
* **Preservation of Core Invariants**: `banalServiceRepository.calibrateAltimeter` dispatch, retroactive sample history shift, 9-language translation parity, and unit conversions (`m`/`ft`) remain strictly invariant.

---

## 5. Architectural Strategy & High-Level Solution
* **Dialog UI Adaptation**:
  * Add optional `matchedLocation: LocationCalibrationStatus? = null` parameter to `SensorSourceDialog` and `SensorStatus`.
  * *Case A (`matchedLocation == null`)*: `saveLocation` defaults to `false`; `locationNameInput` renders conditionally when `saveLocation` is true.
  * *Case B (`matchedLocation != null`)*: Display matched location badge/header; hide `saveLocation` checkbox; pre-fill `altitudeInput` from `matchedLocation.referenceAltitude`.
* **ViewModel & Persistence**:
  * Pass `locationCalibrationStatus` to `SensorStatus` in `TrackingTabsScreen.kt`.
  * In `TrackingTabsViewModel.calibrateAltitude`, if `matchedLocation != null`, update existing location in `KnownLocationsDatabaseManager` with `ElevationSource.MANUAL_USER` and `isLocked = true`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `SensorSourceDialogAltitudeCalibrationTest` and contract interfaces remain 100% backward-compatible.
  2. Manual calibrations with `saveLocation == false` never write to `KnownLocations.db`.
* **Risk Rating**: **LOW** — Scope is confined to Compose presentation logic in `SensorSourceDialog` and parameter routing in `TrackingTabsScreen` / `TrackingTabsViewModel`. Zero regression risk for sensor pipelines or background tracking.
