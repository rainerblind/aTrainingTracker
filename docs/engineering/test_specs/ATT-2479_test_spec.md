# Stage 2 Requirement & Test Specification: ATT-2479 - Sensor-related items in the header part of the tracking control screen overlapping each other

**Ticket**: [ATT-2479](https://atrainingtracker.atlassian.net/browse/ATT-2479)  
**Sub-task**: [ATT-2736](https://atrainingtracker.atlassian.net/browse/ATT-2736) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-278](https://atrainingtracker.atlassian.net/browse/ATT-278) (*Cockpit & Live Telemetry Modernization*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2479`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-UI-301)

### 1.1 Requirement Definition
The system SHALL isolate and structure the sensor header section in `ControlTrackingScreen.kt` using non-overlapping layout containers, preventing UI element collision between search banners, research action buttons, and connected remote sensor tiles under all device connectivity states (ATT-2479):

1. **Dedicated Search Banner Container**:
   - The active sensor search status banner (`SearchArea`) SHALL render in its own full-width layout slot immediately above the device tiles/research action row.
   - It SHALL NOT share an unconstrained horizontal or box container with `ResearchButton` or `RemoteDevices`.

2. **Isolated Non-Overlapping Device and Action Row**:
   - When paired devices are present (`devices.isNotEmpty()`), `RemoteDevices` and `ResearchButton` (if `showResearchButton == true`) SHALL be placed side-by-side in a single `Row` layout with `verticalAlignment = Alignment.CenterVertically`.
   - `RemoteDevices` SHALL receive `Modifier.weight(1f)` so its horizontal list scrolls cleanly without occluding the `ResearchButton`.
   - `ResearchButton` SHALL occupy an intrinsic width slot within the `Row`.

3. **Centered Research Button Fallback When No Devices**:
   - When no paired devices are present (`devices.isEmpty()`), if `showResearchButton == true`, `ResearchButton` SHALL render centered horizontally in the row container.
   - If `showResearchButton == false`, the button SHALL NOT be rendered.

4. **RemoteDevices Component Parameterization and Internal Spacing**:
   - `RemoteDevices` SHALL accept a `modifier: Modifier = Modifier` parameter and apply it to its root `LazyRow` or wrapping layout.
   - In `RemoteDevices`, the inner `LazyRow` SHALL specify `horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)` and appropriate padding to prevent sensor tiles from bunching together.

5. **Visual and Behavioral Invariants**:
   - Tapping `ResearchButton` SHALL continue to trigger `onResearch()`.
   - Sensor search status animation in `SearchArea` SHALL remain unchanged.
   - Tapping individual `RemoteDeviceCard` items in `RemoteDevices` SHALL continue to invoke `onDeviceClick(device)`.

### 1.2 Acceptance Criteria (Given-When-Then)

- **AC-1 (No Paired Devices with Research Button Visible)**:
  - *Given* an athlete on the Control Tracking Screen with 0 connected or paired devices and `showResearchButton == true`,
  - *When* the sensor header renders,
  - *Then* `ResearchButton` SHALL be rendered horizontally centered and no `RemoteDevices` list SHALL be rendered.
- **AC-2 (Paired Devices with Research Button Visible)**:
  - *Given* an athlete with 1 or more paired devices and `showResearchButton == true`,
  - *When* the sensor header renders,
  - *Then* `RemoteDevices` and `ResearchButton` SHALL be arranged side-by-side in a `Row` where `RemoteDevices` occupies the flex weighted width (`weight(1f)`) and `ResearchButton` is pinned to the end without overlapping any device tile.
- **AC-3 (Paired Devices with Research Button Hidden)**:
  - *Given* an athlete with 1 or more paired devices and `showResearchButton == false`,
  - *When* the sensor header renders,
  - *Then* `RemoteDevices` SHALL occupy the full width of the row and `ResearchButton` SHALL NOT be rendered.
- **AC-4 (Active Sensor Search Area Isolation)**:
  - *Given* a sensor search currently in progress (`isSearching == true`),
  - *When* the sensor header renders,
  - *Then* `SearchArea` SHALL render stacked vertically above the device/action row without occluding or clipping device tiles or buttons.
- **AC-5 (Zero Regression on Device Interactions)**:
  - *Given* visible device tiles,
  - *When* an athlete taps a device tile or the research button,
  - *Then* `onDeviceClick` or `onResearch` SHALL be dispatched immediately.

---

## 2. Test Specification (TST-UI-261)

### 2.1 Test Approach
We execute automated unit and Compose contract tests to assert non-overlapping container hierarchy, parameter forwarding, and layout arrangement across all permutations of `devices.isEmpty()`, `showResearchButton`, and `isSearching`.

### 2.2 Test Cases

#### 2.2.1 ControlTrackingSensorHeaderContractTest (`com.atrainingtracker.trainingtracker.ui.control.ControlTrackingSensorHeaderContractTest`)
- **TC-HEADER-01**: *Zero Devices + Show Research Button*:
  - Setup: Empty device list, `showResearchButton = true`.
  - Assertions: `ResearchButton` is displayed, centered; `RemoteDevices` container is not rendered.
- **TC-HEADER-02**: *Paired Devices + Show Research Button*:
  - Setup: 2 devices (e.g. Heart Rate monitor and Cadence sensor), `showResearchButton = true`.
  - Assertions: `RemoteDevices` and `ResearchButton` are rendered within a horizontal `Row` without overlapping bounds; `RemoteDevices` has `weight(1f)`.
- **TC-HEADER-03**: *Paired Devices + Hide Research Button*:
  - Setup: 2 devices, `showResearchButton = false`.
  - Assertions: `RemoteDevices` is rendered; `ResearchButton` is absent.
- **TC-HEADER-04**: *Active Search Area Layout*:
  - Setup: `isSearching = true`.
  - Assertions: `SearchArea` is placed directly above the device/action row, in a distinct vertical sequence.

#### 2.2.2 RemoteDevicesContractTest (`com.atrainingtracker.trainingtracker.ui.control.RemoteDevicesContractTest`)
- **TC-REMOTE-01**: *Modifier Parameter Forwarding*:
  - Verify `RemoteDevices` accepts `modifier: Modifier` and applies it to its root layout.
- **TC-REMOTE-02**: *Tile Spacing and Padding*:
  - Verify `RemoteDevices` specifies `horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`.

#### 2.2.3 Clean-Room Full Suite Regression
- Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full repository test suite.

---

## 3. Traceability Matrix

| Requirement | Test Identifier | Implementation Target | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-301.1` (SearchArea vertical isolation) | `TST-UI-261.1` (`ControlTrackingSensorHeaderContractTest`) | `ControlTrackingScreen.kt` | Specified |
| `REQ-UI-301.2` (Side-by-side Row with weight) | `TST-UI-261.1` (`ControlTrackingSensorHeaderContractTest`) | `ControlTrackingScreen.kt` | Specified |
| `REQ-UI-301.3` (Centered ResearchButton fallback) | `TST-UI-261.1` (`ControlTrackingSensorHeaderContractTest`) | `ControlTrackingScreen.kt` | Specified |
| `REQ-UI-301.4` (RemoteDevices modifier & spacing) | `TST-UI-261.2` (`RemoteDevicesContractTest`) | `RemoteDevices.kt` | Specified |
| `REQ-UI-301.5` (Interaction preservation) | `TST-UI-261.1` & `TST-UI-261.2` | `ControlTrackingScreen.kt`, `RemoteDevices.kt` | Specified |
