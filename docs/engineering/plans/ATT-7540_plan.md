# Stage 3: Implementation Plan - ATT-7540: Radar Tail Light Controls in EditDeviceDialog

**Ticket**: [ATT-7540](https://rainerblind.atlassian.net/browse/ATT-7540) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7540`  
**Requirement Mapping**: `REQ-CON-031` (`REQ-CON-031.5`)  
**Test Mapping**: `TST-CON-031` (`TST-CON-031.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
When athletes inspect a Cycling Rearview Radar (`DeviceType.BIKE_RADAR`) in `EditDeviceDialog.kt`, they currently have no interface to verify hardware tail light operation or select an active mode before a ride. `EditDeviceDialog.kt` must expose a live `RadarLightControlSection` for connected radars with light control, dispatching live commands directly via `RadarLightController` while isolating dialog draft state.

---

## 2. Traceability & Requirements Mapping
* **REQ-CON-031.5a,e**: Conditional visibility: `RadarLightControlSection` renders if and only if `deviceType == DeviceType.BIKE_RADAR`, `isConnected == true`, and `isLightControlAvailable == true` (`TST-CON-031.5a`).
* **REQ-CON-031.5b**: 1-tap mode selection dispatches immediately via `RadarLightController.setManualLightMode(mode)`, isolating `EditDeviceViewModel` draft data and database saves (`TST-CON-031.5a`).
* **REQ-CON-031.5c**: StVZO hardware renders `SingleChoiceSegmentedButtonRow` (`OFF`, `SOLID`) with `icon = {}` and legal notice banner per Design Guidelines §1.1 (`TST-CON-031.5b`).
* **REQ-CON-031.5d**: Non-StVZO hardware renders 5 1-tap mode rows with colored status dot indicators matching `RadarLightQuickSwitchSheet.kt` (`TST-CON-031.5b`).
* **REQ-CON-031.7**: 100% 9-language localization parity in `strings_radar.xml` (`TST-CON-031.5c`).

---

## 3. System Invariants & Preserved Behavior
1. **Draft State Isolation**: Dialog *Save* (`viewModel.saveChanges()`) and *Cancel* strictly operate on device metadata; tail light mode is live peripheral hardware state.
2. **Zero Regressions for Non-Radar Devices**: Speed, Power, HRM, and Altitude sensors remain completely unaffected.
3. **StVZO Compliance Invariant**: StVZO hardware is strictly prevented from activating flashing modes.
4. **Smart Automation Coordination**: `setManualLightMode` flags `SmartRadarLightEngine.isManualOverridden = true`, preventing ambient automation from immediately overwriting user choice.

---

## 4. Proposed Architectural Changes
* **EditDeviceDialog.kt**:
  - Insert `RadarLightControlSection` in the content `Column` after `PowerMeterSection` and before `LivePreviewSection`.
  - Collect `currentMode`, `isStvzo`, and `isLightControlAvailable` from `RadarLightController` using `collectAsStateWithLifecycle()`.
  - For StVZO: Render legal notice Box and 2-option `SingleChoiceSegmentedButtonRow` (`icon = {}`, `SegmentedButtonDefaults.itemShape`).
  - For Non-StVZO: Render 5 tactile mode rows with circular status dots (10dp) matching `RadarLightQuickSwitchSheet.kt` and single-line German labels.
  - On tap: Invoke `RadarLightController.setManualLightMode(mode)`.
* **UI Consistency (`docs/design_guidelines.md`)**:
  - StVZO mode uses `SingleChoiceSegmentedButtonRow` (2 mutually exclusive options, §1.1).
  - Mode colors reuse canonical theme tokens (`TTColor.RadarThreatRed`, `TTColor.RadarThreatAmber`, `outline`).
  - Single-line German text with `maxLines = 1` and `TextOverflow.Ellipsis` (§2.4).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `RadarLightControlSection` in `EditDeviceDialog.kt`
* Files: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt`
* Add `@Composable private fun RadarLightControlSection(...)` supporting StVZO segmented button and non-StVZO tactile rows.
* Mount conditionally: `if (data.deviceType == DeviceType.BIKE_RADAR && isConnected && isLightControlAvailable)`.

### Step 2: Unit Test Verification of Live Dispatch & StVZO Pruning
* Files: `app/src/test/java/com/atrainingtracker/banalservice/devices/radar/RadarLightControllerTest.kt`
* Add unit tests verifying `setManualLightMode` updates `currentMode`, marks manual override, and verifies StVZO mode pruning.
* Verify `EditDeviceViewModelTest.kt` remains 100% green without draft state pollution.

### Step 3: Verification Suites
* Run targeted unit tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.*"`
* Run resource parity & governance:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and living document index validation.
* **Rollback**: Isolated Compose UI additions in `EditDeviceDialog.kt`; clean `git revert` if issues occur.
