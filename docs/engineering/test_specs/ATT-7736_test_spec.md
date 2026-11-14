# Stage 2: Requirement & Test Specification - ATT-7736: Rotate ic_radar.xml icon 90 degrees counter-clockwise (left)

**Ticket**: [ATT-7736](https://rainerblind.atlassian.net/browse/ATT-7736)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-UI-451` / `REQ-UI-451.4`  
**Test Spec ID**: `TST-UI-451` / `TST-UI-451.4`  
**Branch**: `feat/ATT-7736`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-UI-451.4 Refinement)

### 1.1 Problem Statement & Rationale
In Sprint 2026-46.5, ATT-7544 authored `app/src/main/res/drawable/ic_radar.xml` to serve as the dedicated 24dp vector icon for Bike Radar sensor telemetry in the Cockpit header and `SensorSourceDialog`. However, the radar waves radiate upwards. For cycling rearview radar, standard visual iconography requires waves radiating horizontally to the left (90° counter-clockwise rotation from upward).

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional / UI Asset
- **Verification Method**: Unit Test / Visual Inspection

### 1.3 Functional & Architectural Requirements
1. `app/src/main/res/drawable/ic_radar.xml` SHALL wrap its path in a `<group>` with `android:pivotX="480"`, `android:pivotY="480"`, and `android:rotation="270"` (or `-90`) rotating the radar wave arcs 90 degrees counter-clockwise (to the left).
2. `ic_radar.xml` SHALL strictly preserve 24dp x 24dp dimensions, 960x960 viewport, and `@android:color/white` fill with tint support.
3. `SensorType.getIconResId()` SHALL continue returning `R.drawable.ic_radar` for all scalar and threat radar sensor types (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Counter-Clockwise Rotation)**:
  * *Given* the `ic_radar.xml` vector drawable,
  * *When* parsed or rendered,
  * *Then* it contains `<group android:pivotX="480" android:pivotY="480" android:rotation="270">` (or `-90`), orienting origin dot at center-right with arcs radiating leftwards.
* **Criterion 2 (Contract & Unit Verification)**:
  * *Given* `SensorStatusTopBarSpacingTest.kt`,
  * *When* tests execute,
  * *Then* `testIcRadarDrawableFile_existsAndIsValidVector` validates file existence, 24dp dimensions, and counter-clockwise rotation attribute.

### 1.5 System Invariants
- Zero layout, padding, or alpha changes in `SensorStatus.kt` or `SensorSourceDialog.kt`.
- Zero database migrations; zero string changes; 100% 9-language localization parity preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-451` (`REQ-UI-451.4`), targeting `app/src/main/res/drawable/ic_radar.xml`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-7544` (commit `a5a9a228d450`), refined in `ATT-7736`.
* **Root Reason for Existing Formulation**: ATT-7544 created the standalone 24dp vector asset with concentric waves, but defaulted to upward-pointing waves.
* **Preservation of Core Invariants**: Reorienting waves to the left preserves the 24dp asset bounds, 960x960 viewport, and vector tinting without regressions.

---

## 2. Test Specification (TST-UI-451.4)

### Test Case 1: `testIcRadarDrawableFile_existsAndIsValidVector` (`TST-UI-451.4a`)
* **Scope**: Unit Test (`SensorStatusTopBarSpacingTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatusTopBarSpacingTest.kt`
* **Assertions**:
  - `assertTrue(target.exists())`
  - `assertTrue(content.contains("<vector"))`
  - `assertTrue(content.contains("android:width=\"24dp\"") && content.contains("android:height=\"24dp\""))`
  - `assertTrue(content.contains("android:rotation=\"270\"") || content.contains("android:rotation=\"-90\""))`

### Test Case 2: `testRadarSensorTypes_mapToDedicatedIcRadarDrawable` (`TST-UI-451.4b`)
* **Scope**: Unit Test (`SensorStatusTopBarSpacingTest.kt`)
* **Assertions**:
  - `assertEquals(R.drawable.ic_radar, SensorType.RADAR_THREAT_COUNT.iconResId)`
  - All 6 radar sensor types verified to resolve to `R.drawable.ic_radar`.

### Test Case 3: Tier 1 Targeted Test Suite (`TST-UI-451.4c`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorStatusTopBarSpacingTest"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-451.4a]` | Unit | `SensorStatusTopBarSpacingTest.testIcRadarDrawableFile_existsAndIsValidVector` | `REQ-UI-451.4` | Specified |
| `[TST-UI-451.4b]` | Unit | `SensorStatusTopBarSpacingTest.testRadarSensorTypes_mapToDedicatedIcRadarDrawable` | `REQ-UI-451.4` | Specified |
| `[TST-UI-451.4c]` | Tier 1 Targeted | `SensorStatusTopBarSpacingTest` | `REQ-UI-451` | Specified |
