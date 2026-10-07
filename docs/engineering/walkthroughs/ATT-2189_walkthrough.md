# Stage 5: Walkthrough & Verification - ATT-2189: Relocate pairing buttons to sensor settings and optimize control tracking screen layout

**Ticket**: [ATT-2189](https://atrainingtracker.atlassian.net/browse/ATT-2189)  
**Sub-task**: [ATT-2559](https://atrainingtracker.atlassian.net/browse/ATT-2559) (`[Test]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `Sprint 2026-41.1`  
**Requirement Mapping**: `REQ-UI-278` (*Pairing Triggers Relocation to Sensor Management & Control Tracking Layout Optimization*)  
**Test Mapping**: `TST-UI-238` (*Pairing Triggers Relocation to Sensor Management & Control Tracking Layout Optimization Verification*)  
**Branch**: `feature/ATT-2189`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Executive Summary & Verification Overview

Previously, sensor pairing triggers (`PairingButtons` for ANT+ and Bluetooth LE) were positioned permanently at the bottom of the tracking control screen (`ControlTrackingScreen.kt`). Sensor pairing is an infrequent, one-time hardware configuration activity rather than a regular workout task, and its persistent presence on the main workout cockpit cluttered the interface and wasted vertical screen real estate. Furthermore, Sprint 2026-41.1 introduces Route Selection (`ATT-2458`), which requires vertical space at the bottom of the control tracking screen without shifting the prominent centered Start Tracking button.

ATT-2189 delivers complete resolution by:
1. **Removing Pairing Buttons from Control Tracking**: Removed `PairingButtons` from `ControlTrackingScreen.kt` body while keeping default backward-compatible signatures for existing callers.
2. **Flexible Bottom Content Container Slot**: Introduced `bottomContent: @Composable ColumnScope.() -> Unit = {}` at the bottom of `ControlTrackingScreen.kt`, perfectly reserving the vacated space (~72 dp) for sibling component `ATT-2458` (Route Selection).
3. **Preserving Centered Button Layout Invariants**: Preserved the athlete-preferred vertical centering of the `ControlTrackingButton` (Start / Pause / Stop) and `SportTypeSelector` via balanced equal-weight spacers (`Modifier.weight(1f)`).
4. **Relocating Dedicated Pairing Access to Sensor Management ("Meine Sensoren")**: Added a Material 3 `FloatingActionButton` (`Icons.Default.Add`, `@string/devices_pair_sensor`) anchored to `Alignment.BottomEnd` with `navigationBarsPadding()` in `DevicesTabbedScreen.kt`.
5. **Streamlined Pairing Protocol & Sensor Type Flow**: Implemented `PairingProtocolBottomSheet.kt` using `AppModalBottomSheet` with ANT+ and BLE options, cascading to `DeviceTypeSelectionDialog.kt`, dynamically reconfiguring filters via `tabViewModel.updateFilters(protocol, deviceType)`, restarting sensor discovery, and auto-scrolling to Tab 0 (Available sensors).
6. **100% 9-Language Localization Parity**: Added and verified string resources (`devices_pair_sensor`, `devices_pair_protocol_title`, `devices_pair_protocol_ant`, `devices_pair_protocol_ble`) across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-278` (1: Control Tracking Layout Optimization) | `TST-UI-238.1` | `ControlTrackingLayoutContractTest.kt` | **PASSED** (4/4) | `Verified` |
| `REQ-UI-278` (2: Sensor Management Pairing FAB & Flow) | `TST-UI-238.2` | `DevicesTabbedScreenContractTest.kt` | **PASSED** (2/2) | `Verified` |
| `REQ-UI-278` (3: 9-Language Localization Parity) | `TST-UI-238.3` | `PairingLocalizationTest.kt` | **PASSED** (9/9 locales, 4 keys) | `Verified` |
| `REQ-PRO-001` (Clean-Room Full Suite Regression) | `TST-UI-238.4` | `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.PairingLocalizationTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingLayoutContractTest" \
                            --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedScreenContractTest"
```
**Output**:
```text
BUILD SUCCESSFUL in 7s
32 actionable tasks: 2 executed, 30 up-to-date
```

- `ControlTrackingLayoutContractTest`: Verified absence of `PairingButtons` in `ControlTrackingScreen` body, verified exposure and invocation of `bottomContent` slot, verified dual `weight(1f)` spacers preserving vertical centering, and verified strict fine location permission gating (`ACCESS_FINE_LOCATION`, `handleStartClick`, and warning badge).
- `DevicesTabbedScreenContractTest`: Verified presence of Material 3 pairing FAB with `Icons.Default.Add` and `@string/devices_pair_sensor`, verified integration with `PairingProtocolBottomSheet` and `DeviceTypeSelectionDialog`, verified auto-scroll to Page 0, and verified `DevicesTabbedViewModel.updateFilters` reconfigures protocol and device type while cleanly restarting active discovery.
- `PairingLocalizationTest`: Verified 100% presence and non-empty translations for `devices_pair_sensor`, `devices_pair_protocol_title`, `devices_pair_protocol_ant`, and `devices_pair_protocol_ble` across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

---

## 4. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room test suite passed with 100% success rate.
2. **Strict Centering & Geometry Preservation**: Start button and sport selector remain vertically centered without layout jumpiness.
3. **Location Gating Invariant (REQ-PRI-004)**: Mandatory fine location check, permission rationale cascading, and location warning badge remain completely intact.
4. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-278`) and `docs/tests.md` (`TST-UI-238`) updated to `Verified`.
5. **Governance Script Passed**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.1` passed cleanly.
6. **Subtask Self-Sufficiency**: Stage 5 subtask `ATT-2559` transitioned to `Erledigt` upon passing Gate 5 audit.
7. **Parent Ticket Final Review**: Parent ticket `ATT-2189` transitioned to `Final Review (Human)` and assigned to `human` for human sign-off.
