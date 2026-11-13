# Stage 1 Analysis: ATT-7540 - Add Radar Tail Light Mode Control Buttons to EditDeviceDialog

**Ticket**: [ATT-7540](https://rainerblind.atlassian.net/browse/ATT-7540)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7540`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
Opening `EditDeviceDialog.kt` for a Cycling Rearview Radar (`DeviceType.BIKE_RADAR`) currently only displays generic device properties, equipment links, and scalar telemetry. Users pairing or configuring a radar with integrated tail light (Garmin Varia RearVue 820, RTL515, RTL516, RCT715) cannot verify hardware operation or set light mode before riding. `EditDeviceDialog.kt` must expose a live `RadarLightControlSection` when the connected radar supports light control:
1. **Instant Hardware Verification**: 1-tap mode selection sends commands immediately over BLE/ANT+, confirming pairing.
2. **Pre-Ride Setup**: Directly mirrors specialized sections like `CalibrationSection` and `PowerMeterSection`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  - `EditDeviceDialog.kt:161-177`: Dialog sequentially renders `EquipmentSection`, `CalibrationSection`, `PowerMeterSection`, `AltitudeExplanationSection`, and `LivePreviewSection`. Inserting `RadarLightControlSection` between `PowerMeterSection` and `LivePreviewSection` maintains clean component hierarchy.
  - `RadarLightController.kt:50-68`: Exposes reactive `currentMode: StateFlow<RadarLightMode>`, `isLightControlAvailable: StateFlow<Boolean>`, and `isStvzo: StateFlow<Boolean>`.
  - **Draft State Decoupling Invariant**: Mode switching must NOT touch `EditDeviceViewModel` or `DeviceUiData` draft state. `Save` persists device name and equipment; tail light mode is live physical hardware state dispatched via `RadarLightController.setManualLightMode(mode)`.
  - **Smart Light Automation Interaction**: `setManualLightMode(mode)` sets `SmartRadarLightEngine.isManualOverridden = true`, preventing ambient automation from immediately overwriting user selection.
  - **Conditional Visibility Guard**: The section must render ONLY when `data.deviceType == DeviceType.BIKE_RADAR`, device is connected (`currentDevice.isConnected == true`), and `isLightControlAvailable == true` (hiding for RVR315 or when disconnected).
  - **StVZO & UI Guidelines Parity**: When `isStvzo == true`, render `SingleChoiceSegmentedButtonRow` with `icon = {}` for 2 legal modes (`OFF`, `SOLID`) per `docs/design_guidelines.md §1.1`. When `isStvzo == false`, render 5-mode 1-tap rows with colored status indicator dots matching `RadarLightQuickSwitchSheet.kt:145-185` and single-line German labels per §2.4.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Add `RadarLightControlSection` in `EditDeviceDialog.kt` for connected, controllable `BIKE_RADAR` devices.
  - Implement 2-option `SingleChoiceSegmentedButtonRow` for StVZO and 5-option 1-tap mode rows for international hardware.
  - Dispatch real-time mode changes via `RadarLightController.setManualLightMode(mode)`.
  - Add unit tests verifying visibility logic, StVZO pruning, and non-persisted live dispatch.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Zero changes to `EditDeviceViewModel` persistence, Room entities, or `DeviceUiData` schema.
  - Modifying `RadarLightQuickSwitchSheet.kt` or Cockpit edge overlays.
  - Refactoring unrelated `EditDeviceDialog` sections.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-CON-031` (*BLE Radar Tail Light Mode Control*). Refined via `REQ-CON-031.5`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-5102` (`69fec8e7`), upgraded for RearVue 820 in `ATT-7545` (`6bfac996`).
* **Root Reason for Existing Formulation**: Tail light control was originally confined to in-workout Cockpit quick-switch sheets.
* **Preservation of Core Invariants**: Section strictly hidden when disconnected or for radar-only devices; StVZO flashing restrictions preserved; zero database migrations; 100% 9-language translation parity (`strings_radar.xml`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-031.5` (refining `REQ-CON-031` in `docs/requirements/req_tracking_cockpit.md`).
* **Affected Architecture Elements**: `EditDeviceDialog.kt`, `RadarLightControlSection.kt`.
* **Verification Impact & Test Surface**: Compose UI tests in `EditDeviceDialogTest.kt` and `RadarLightControllerTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Scoped strictly to UI composition in `EditDeviceDialog`, gated by device type and connection state.

---

## 6. Architectural Strategy & High-Level Solution
- Create `@Composable private fun RadarLightControlSection(isConnected: Boolean)` in `EditDeviceDialog.kt`.
- Collect `currentMode`, `isLightControlAvailable`, and `isStvzo` from `RadarLightController` using `collectAsStateWithLifecycle()`.
- Guard visibility: `if (data.deviceType == DeviceType.BIKE_RADAR && isConnected && isLightControlAvailable)`.
- Render section title `R.string.radar_light_sheet_title` and mode selector (segmented button for StVZO, tactile rows with dots for standard).
- On mode click, call `RadarLightController.setManualLightMode(mode)`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression on existing device dialog features (power, calibration, equipment).
  2. Dialog draft state (`viewModel.saveChanges()`) completely isolated from tail light mode state.
  3. StVZO flashing mode restrictions strictly enforced.
  4. Radar-only units (`RVR315`) and disconnected devices show zero UI changes.
* **Risk Rating**: **LOW** (additive Compose UI within an existing dialog).
