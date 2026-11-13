# Stage 2: Requirement & Test Specification - ATT-7540: Live Radar Tail Light Mode Controls in EditDeviceDialog

**Ticket**: [ATT-7540](https://rainerblind.atlassian.net/browse/ATT-7540)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-CON-031` (`REQ-CON-031.5`) (*BLE Radar Tail Light Mode Control*)  
**Test Spec ID**: `TST-CON-031` (`TST-CON-031.5`) | **Branch**: `feature/ATT-7540`  
**Author**: AI Agent 2 (Flash) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-CON-031.5)

### 1.1 Problem Statement & Rationale
When athletes configure a Cycling Rearview Radar (`BIKE_RADAR`) in `EditDeviceDialog.kt`, they cannot verify tail light hardware operation or select a light mode before riding. `EditDeviceDialog` must expose a live `RadarLightControlSection` when the connected radar supports light control, dispatching mode changes directly to hardware via `RadarLightController` without polluting dialog draft state.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `EditDeviceDialog.kt` SHALL render `RadarLightControlSection` if and only if `data.deviceType == DeviceType.BIKE_RADAR`, device is connected (`(liveData ?: data).isConnected == true`), and `RadarLightController.isLightControlAvailable == true` (`REQ-CON-031.5a`).
2. Selecting a tail light mode SHALL invoke `RadarLightController.setManualLightMode(mode)` immediately, setting manual override without mutating `EditDeviceViewModel` draft data (`data.deviceName`, `data.linkedEquipment`) or triggering database saves (`REQ-CON-031.5b`).
3. When `RadarLightController.isStvzo` is `true`, the section SHALL render a 2-option `SingleChoiceSegmentedButtonRow` (`OFF`, `SOLID`) with `icon = {}` and StVZO legal notice banner (`REQ-CON-031.5c`).
4. When `RadarLightController.isStvzo` is `false`, the section SHALL render 5 1-tap mode rows (`OFF`, `SOLID`, `PELOTON`, `NIGHT_FLASH`, `DAY_FLASH`) with colored status dot indicators matching `RadarLightQuickSwitchSheet.kt` and single-line labels (`REQ-CON-031.5d`).
5. Radar-only units (`RVR315`) or disconnected devices SHALL omit `RadarLightControlSection` completely with zero UI impact (`REQ-CON-031.5e`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC1 (Live Mode Dispatch & Draft Isolation)**:
  * *Given* an open `EditDeviceDialog` for a connected radar supporting light control,
  * *When* the user taps a tail light mode button and cancels or closes the dialog,
  * *Then* `RadarLightController.setManualLightMode(mode)` is dispatched immediately and `EditDeviceViewModel` draft data remains unmodified.
* **AC2 (StVZO Capability Pruning)**:
  * *Given* a connected StVZO-certified radar peripheral (`isStvzo == true`),
  * *When* viewing `EditDeviceDialog`,
  * *Then* only `OFF` and `SOLID` options are rendered within a `SingleChoiceSegmentedButtonRow` alongside the StVZO legal banner.
* **AC3 (Conditional Visibility Guard)**:
  * *Given* a radar peripheral that is disconnected or lacks light control (e.g. RVR315),
  * *When* `EditDeviceDialog` opens,
  * *Then* `RadarLightControlSection` is completely hidden.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-CON-031` (BLE Radar Tail Light Mode Control), targeting `EditDeviceDialog.kt`, `RadarLightController.kt`, and `RadarLightQuickSwitchSheet.kt`.
* `Historical Origin & Commit Trace`: Introduced in `ATT-5102` (`3532c253`), updated for RearVue 820 in `ATT-7545` (`6bfac996`), extended for `EditDeviceDialog` in `ATT-7540`.
* `Root Reason for Existing Formulation`: Tail light control was initially restricted to in-workout Cockpit quick-switch sheets and global settings.
* `Preservation of Core Invariants`: Zero changes to `EditDeviceViewModel` persistence; StVZO flashing restrictions preserved; zero database migrations; 100% 9-language localization parity (`strings_radar.xml`).

---

## 2. Test Specification (TST-CON-031.5)

### Test Case 1: Conditional Visibility & Draft Decoupling (`TST-CON-031.5a`)
* **Scope**: Unit (`RadarLightControllerTest.kt` / `EditDeviceViewModelTest.kt`)
* **Verification**: Assert `setManualLightMode` updates `currentMode` and sets manual override without interacting with `EditDeviceViewModel` draft properties or database saves.

### Test Case 2: StVZO Mode Filtering & UI Contract (`TST-CON-031.5b`)
* **Scope**: Unit (`RadarLightControllerTest.kt`)
* **Verification**: Assert `availableModes(isStvzo = true)` returns exactly `[OFF, SOLID]` and `availableModes(isStvzo = false)` returns all 5 modes.

### Test Case 3: 9-Language Localization Parity (`TST-CON-031.5c`)
* **Scope**: Unit / Static (`strings_radar.xml`)
* **Verification**: Assert all string resources (`radar_light_sheet_title`, `radar_light_stvzo_notice`, mode titles) exist with 100% parity across all 9 locales.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-031.5a` | Unit | `RadarLightControllerTest.kt` | `REQ-CON-031.5a,b,e` | Specified |
| `TST-CON-031.5b` | Unit | `RadarLightControllerTest.kt` | `REQ-CON-031.5c,d` | Specified |
| `TST-CON-031.5c` | Parity | `strings_radar.xml` (9 locales) | `REQ-CON-031.7` | Specified |
