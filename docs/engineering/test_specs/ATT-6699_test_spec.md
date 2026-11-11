# Stage 2: Requirement & Test Specification - ATT-6699: Design and wire dedicated ANT+ (bike_radar) and BLE (bt_bike_radar) device vector logos for DeviceType.BIKE_RADAR

**Ticket**: [ATT-6699](https://rainerblind.atlassian.net/browse/ATT-6699)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-514` (*Dedicated ANT+ and Bluetooth LE Vector Sensor Badges for DeviceType.BIKE_RADAR*)  
**Test Spec ID**: `TST-UI-514`  
**Branch**: `feature/ATT-6699`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-UI-514`)

### 1.1 Problem Statement & Rationale
`DeviceType.BIKE_RADAR` currently lacks dedicated protocol-badged vector drawables, falling back to generic protocol icons (`ant_logo` / `logo_protocol_bluetooth`). The system SHALL provide mathematically defined vector drawables (`bike_radar.xml` and `bt_bike_radar.xml`) matching the visual fidelity, color tokens, and Rule 37 constraints of the sensor badge family (`REQ-UI-375`, `REQ-UI-442`).

### 1.2 Functional & Architectural Requirements
1. **ANT+ Radar Vector Badge (`REQ-UI-514.1` — Rule 37)**:
   - `app/src/main/res/drawable/bike_radar.xml` SHALL define `48dp x 48dp` ($96 \times 96$ viewport) with `#000000` rounded badge ($r=10.0$), centered white silhouette cyclist, rear radar wave emission arcs behind saddle, and bold uppercase `RDR` label at $y \in [75.5, 90.5]$ using `evenOdd` fillType.
2. **Bluetooth LE Radar Vector Badge (`REQ-UI-514.2` — Rule 37)**:
   - `app/src/main/res/drawable/bt_bike_radar.xml` SHALL define `48dp x 48dp` ($144 \times 144$ viewport) with full-bleed `#0A3D91` rounded badge ($r=22.0$), centered cyclist (`translateX="18.18"`), top-left canonical radar mini-icon (`mini_icon_radar`), rear radar emission arcs, and bold uppercase `RDR` label at $y \in [113.25, 135.75]$ using `evenOdd` fillType.
3. **Protocol Icon Resolution (`REQ-UI-514.3`)**:
   - `UIHelper.getIconId(DeviceType.BIKE_RADAR, Protocol.ANT_PLUS)` and `DeviceData.getIconId(DeviceType.BIKE_RADAR, Protocol.ANT_PLUS)` SHALL return `R.drawable.bike_radar`.
   - `UIHelper.getIconId(DeviceType.BIKE_RADAR, Protocol.BLUETOOTH_LE)` and `DeviceData.getIconId(DeviceType.BIKE_RADAR, Protocol.BLUETOOTH_LE)` SHALL return `R.drawable.bt_bike_radar`.
4. **Rule 37 Vector Quality & Contract Test Coverage (`REQ-UI-514.4`)**:
   - All `<path>` elements MUST contain $\le 45$ `L` commands and use analytical curve commands (`A`, `C`, or `Q`).
   - `VectorAssetResolutionContractTest.kt` SHALL verify DOM well-formedness, Rule 37 complexity, color tokens, and resource resolution.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-375` (`ATT-2859`) and `REQ-UI-442` (`ATT-4956`, `ATT-5201`). Targets `app/src/main/res/drawable/bike_radar.xml`, `app/src/main/res/drawable/bt_bike_radar.xml`, `UIHelper.java`, `DeviceData.kt`, and `VectorAssetResolutionContractTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-2859` (`15c74685`, Sprint 2026-42.7: ANT+ badges), `ATT-4956` (`be03a5bc14`, Sprint 2026-45.1: BLE badges), `ATT-5201` (`43de6fd0`, Sprint 2026-45.5: Official `#0A3D91` color and centered cyclist).
* **Root Reason for Existing Formulation**: When `DeviceType.BIKE_RADAR` was introduced in `ATT-5083`, dedicated vector assets were deferred, causing fallback to generic protocol logos.
* **Preservation of Core Invariants**: Full-bleed geometry ($0..144$, $r=22.0$), official `#0A3D91` and `#000000` tokens, uppercase `evenOdd` typography, Rule 37 primitive limits, and 100% backward compatibility preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (ANT+ Resolution)**:
  * *Given* `DeviceType.BIKE_RADAR` on `Protocol.ANT_PLUS`,
  * *When* querying `UIHelper.getIconId` or `DeviceData.getIconId`,
  * *Then* `R.drawable.bike_radar` is returned.
* **Criterion 2 (BLE Resolution)**:
  * *Given* `DeviceType.BIKE_RADAR` on `Protocol.BLUETOOTH_LE`,
  * *When* querying `UIHelper.getIconId` or `DeviceData.getIconId`,
  * *Then* `R.drawable.bt_bike_radar` is returned.
* **Criterion 3 (Rule 37 & Visual Contract)**:
  * *Given* `bike_radar.xml` and `bt_bike_radar.xml`,
  * *When* inspected by `VectorAssetResolutionContractTest`,
  * *Then* each path has $\le 45$ `L` commands, analytical curves are present, color tokens match, and bottom `RDR` typography uses `evenOdd`.

---

## 2. Test Specification (`TST-UI-514`)

### Test Case 1: Vector DOM & Rule 37 Complexity (`TST-UI-514.1`)
* **Scope**: Contract Test (`VectorAssetResolutionContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* **Preconditions**: `bike_radar.xml` and `bt_bike_radar.xml` present in `app/src/main/res/drawable/`.
* **Action**: Parse vector XML files with `DocumentBuilder`, assert root is `<vector>`, width/height are `48dp`, viewports match ($96 \times 96$ / $144 \times 144$), and each path has $\le 45$ `L` commands with analytical curves.
* **Expected Result**: 100% compliance with Rule 37 geometry constraints.

### Test Case 2: Color Tokens, Bounds & Typography (`TST-UI-514.2`)
* **Scope**: Contract Test (`VectorAssetResolutionContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* **Preconditions**: Both radar drawables deployed.
* **Action**: Assert `#000000` on `bike_radar.xml`, full-bleed `#0A3D91` on `bt_bike_radar.xml`, `evenOdd` fillType on `RDR` label paths, and `translateX="18.18"` centered cyclist.
* **Expected Result**: Visual fidelity assertions pass without regressions.

### Test Case 3: DeviceType.BIKE_RADAR Resource Resolution (`TST-UI-514.3`)
* **Scope**: Contract Test (`VectorAssetResolutionContractTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* **Preconditions**: `UIHelper.java` and `DeviceData.kt` updated.
* **Action**: Assert `UIHelper.getIconId` and `DeviceData.getIconId` return `R.drawable.bike_radar` for ANT+ and `R.drawable.bt_bike_radar` for BLE.
* **Expected Result**: Exact mapping match for both helper classes.

### Test Case 4: Fast Targeted Regression (`TST-UI-514.4`)
* **Scope**: Targeted Unit Test Regression
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*"`
* **Expected Result**: 100% pass rate across targeted test suites.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-514.1` | ANT+ `bike_radar.xml` geometry, motif & `RDR` label | `TST-UI-514.1`, `TST-UI-514.2` | `VectorAssetResolutionContractTest` |
| `REQ-UI-514.2` | BLE `bt_bike_radar.xml` full-bleed badge, mini-icon & `RDR` label | `TST-UI-514.1`, `TST-UI-514.2` | `VectorAssetResolutionContractTest` |
| `REQ-UI-514.3` | `UIHelper` & `DeviceData` icon resolution | `TST-UI-514.3` | `VectorAssetResolutionContractTest` |
| `REQ-UI-514.4` | Rule 37 constraints & regression suite | `TST-UI-514.1`, `TST-UI-514.4` | `VectorAssetResolutionContractTest`, Targeted Suite |
