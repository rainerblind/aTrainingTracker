# Stage 5: Walkthrough & Verification - ATT-7540: Live Radar Tail Light Mode Controls in EditDeviceDialog

**Ticket**: [ATT-7540](https://rainerblind.atlassian.net/browse/ATT-7540)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7540`  
**Requirement Mapping**: `REQ-CON-031` (`REQ-CON-031.5`)  
**Test Mapping**: `TST-CON-031` (`TST-CON-031.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Executive Summary & Verification Overview

ATT-7540 introduces live Garmin Varia radar tail light mode controls directly within `EditDeviceDialog.kt` when connected to radar peripherals that support lighting control:
1. **Conditional Visibility Gating (`REQ-CON-031.5a,e`)**:
   - `RadarLightControlSection` renders if and only if `data.deviceType == DeviceType.BIKE_RADAR`, device is connected (`isConnected == true`), and `RadarLightController.isLightControlAvailable == true`.
   - Radar-only units (e.g. Garmin Varia RVR315) and disconnected units hide the section completely with zero UI impact.
2. **Draft State Isolation & Live Hardware Dispatch (`REQ-CON-031.5b`)**:
   - Mode selection invokes `RadarLightController.setManualLightMode(mode)` immediately, dispatching live peripheral writes over BLE/ANT+ and engaging `SmartRadarLightEngine` manual override.
   - `EditDeviceViewModel` draft data (`data.deviceName`, `data.linkedEquipment`) and database persistence remain completely decoupled; canceling or closing the dialog leaves the physical light command intact.
3. **StVZO Compliance & UI Design Parity (`REQ-CON-031.5c,d`)**:
   - StVZO units (`isStvzo == true`): Render StVZO notice banner and 2-option `SingleChoiceSegmentedButtonRow` (`OFF`, `SOLID`) with `icon = {}` per Design Guidelines §1.1.
   - Non-StVZO units (`isStvzo == false`): Render 5 tactile 1-tap mode rows with colored status dot indicators matching `RadarLightQuickSwitchSheet.kt` and single-line labels (§2.4).
4. **9-Language Localization Parity (`REQ-CON-031.7`)**:
   - Reused existing localized resources (`radar_light_sheet_title`, `radar_light_stvzo_notice`, mode names) with 100% key and format parity across all 9 supported languages.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-031.5a,e` | `TST-CON-031.5a` | Unit (`RadarLightControllerTest`, `EditDeviceDialog`) | **PASSED** | Verified |
| `REQ-CON-031.5b` | `TST-CON-031.5a` | Unit (`RadarLightControllerTest`, `EditDeviceViewModelTest`) | **PASSED** | Verified |
| `REQ-CON-031.5c` | `TST-CON-031.5b` | Unit (`RadarLightControllerTest.testRadarLightController_setManualLightMode_stvzoPruning`) | **PASSED** | Verified |
| `REQ-CON-031.5d` | `TST-CON-031.5b` | Unit (`RadarLightModeTest`, `RadarLightControllerTest`) | **PASSED** | Verified |
| `REQ-CON-031.7` | `TST-CON-031.5c` | Parity (`TranslationParityTest`) | **PASSED** | Verified |

---

## 3. Automated Test Evidence

### Targeted Unit Tests
- Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.*"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  - `./gradlew :app:compileDebugAndroidTestKotlin`
- Results:
  - Radar & EditDevice test suites: **100% PASS** (BUILD SUCCESSFUL, 0 failures, 0 errors).
  - Translation Parity test suite: **100% PASS** across all 9 supported locales.
  - AndroidTest compilation: **100% PASS** with zero compile errors.

### Code Quality & Requirement Governance
- Commands:
  - `:app:lintDebug`: **0 errors** in modified files (`EditDeviceDialog.kt`, `RadarLightControllerTest.kt`, `EditDeviceViewModelTest.kt`).
  - `python3 tools/verify_requirement_governance.py`: **PASSED** (0 errors, 0 unmounted Composable islands, repository encapsulation verified).

---

## 4. Invariant & Governance Verification

1. **Draft State Isolation**: Dialog metadata saves operate strictly on device name and equipment; physical light mode commands are decoupled.
2. **Regulatory Parity**: German StVZO restrictions strictly prevent flashing modes from being presented or commanded.
3. **Fail-Safe Degradation**: Radars lacking tail light hardware (RVR315) or disconnected devices show zero UI additions.
4. **Target Release Alignment**: Verified against active Jira unreleased version `V5.0.0`.
