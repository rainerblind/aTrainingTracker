# Stage 3: Implementation Plan - ATT-7544: Bike Radar Sensor Status Icon & Source Dialog

**Ticket**: [ATT-7544](https://rainerblind.atlassian.net/browse/ATT-7544) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7544`  
**Requirement Mapping**: `REQ-UI-451` (`REQ-UI-451.1`–`REQ-UI-451.6`) | **Test Mapping**: `TST-UI-451` (`TST-UI-451.1`–`TST-UI-451.7`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
The Cockpit header bar (`SensorStatus`) currently renders 8 core sensor indicators. With Cycling Rearview Radar (`DeviceType.BIKE_RADAR`) integrated, Cockpit requires a dedicated radar status indicator and source inspection dialog fitted cleanly within 360 dp portrait viewports without clipping.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-451` (*Cockpit Top Bar 16 dp Screen Edge Offset and Centering*)
  - `REQ-UI-451.4`: Standalone `ic_radar.xml` 24dp vector, `SensorType.getIconResId()` mapping, and 9-icon `SensorStatus` layout.
  - `REQ-UI-451.5`: `SensorSourceDialog` localized "Bike Radar" title, device listing, and scalar suppression for `RADAR_THREAT_COUNT`.
  - `REQ-UI-451.6`: 9-language translation parity for strings and accessibility descriptions.
* **Test Mapping**: `TST-UI-451` (`TST-UI-451.1`–`TST-UI-451.7` in `docs/tests/tst_ui_design_system.md`).

---

## 3. System Invariants & Preserved Behavior
1. **Header Geometry & Centering**: Symmetrical 32 dp action buttons, 16 dp outer display margin, and mathematical centering of the sensor row preserved across all supporter tiers.
2. **Display Budget Invariant**: In portrait mode on 360 dp displays, 9 icons fit within 272 dp available central width (`9 * 30.dp = 270.dp <= 272.dp`).
3. **Sensor Redundancy & Logic Isolation**: Zero changes to SQLite schemas, sensor acquisition pipelines, or `RadarStateRepository`.

---

## 4. Proposed Architectural Changes
* **Asset Layer (`res/drawable/ic_radar.xml`)**: Clean 24dp vector drawable (960x960 viewport, concentric radar waves + origin point) resolving ATT-7518 defects.
* **Domain Layer (`SensorType.java`)**: Return `R.drawable.ic_radar` in `getIconResId()` for radar sensor types.
* **UI Cockpit Layer (`SensorStatus.kt`)**: Append `SensorType.RADAR_THREAT_COUNT` as 9th item in `sensorDefinitions`; set portrait icon horizontal padding to `4.dp`.
* **UI Source Dialog Layer (`SensorSourceDialog.kt`)**: Format title with capitalized `R.string.device_type_bike_radar` and omit scalar `valueWithUnit` for `RADAR_THREAT_COUNT`.
* **UI Consistency (`docs/design_guidelines.md`)**: Reuses standard `SensorStatus` and `SensorSourceDialog` components and tokens (`TTColor.SensorSourceActive`, `TTColor.SensorBackupActive`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Author `res/drawable/ic_radar.xml`
* **File**: `app/src/main/res/drawable/ic_radar.xml`
* **Changes**: Create 24dp monochrome vector drawable with concentric radar arcs and center dot.

### Step 2: Map Radar Types in `SensorType.java`
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`
* **Changes**: Return `R.drawable.ic_radar` in `getIconResId()` for `RADAR_THREAT_COUNT` and scalar radar types.

### Step 3: Integrate 9-Icon Layout in `SensorStatus.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt`
* **Changes**: Add `SensorType.RADAR_THREAT_COUNT` to `sensorDefinitions`; adjust portrait icon padding to `4.dp`.

### Step 4: Adapt `SensorSourceDialog.kt` for Bike Radar
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialog.kt`
* **Changes**: Display "Bike Radar" title and pass `valueWithUnit = null` for `SensorType.RADAR_THREAT_COUNT`.

### Step 5: Update Unit & Contract Tests
* **Files**: `SensorStatusTopBarSpacingTest.kt`, `SensorSourceDialogContractTest.kt`
* **Changes**: Add tests for 9-icon 270 dp budget, `ic_radar` mapping, dialog title, and scalar omission.

### Step 6: Run Verification Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*" --tests "com.atrainingtracker.banalservice.sensor.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`TST-UI-451.1`–`TST-UI-451.7`) followed by full test suite regression in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-7544`) allows complete revert to `sprint/2026-46.5` without side effects.
