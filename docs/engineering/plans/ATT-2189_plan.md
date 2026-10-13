# Stage 3: Implementation Plan - ATT-2189: Relocate Pairing Triggers to Sensor Settings and Optimize Control Tracking Screen Layout

**Ticket**: [ATT-2189](https://atrainingtracker.atlassian.net/browse/ATT-2189)  
**Sub-task**: [ATT-2557](https://atrainingtracker.atlassian.net/browse/ATT-2557) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-278` (*Relocate Sensor Pairing to Sensor Management and Optimize Control Tracking Screen Layout*)  
**Test Mapping**: `TST-UI-238` (*Pairing Triggers Relocation to Sensor Management & Control Tracking Layout Optimization Verification*)  
**Branch**: `feature/ATT-2189`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

In the existing application architecture, hardware sensor pairing triggers (`PairingButtons` for ANT+ and Bluetooth LE) were positioned at the bottom of `ControlTrackingScreen.kt`. Sensor pairing is a one-time configuration task rather than a frequent in-workout activity. Permanently placing pairing buttons on the primary workout control cockpit clutters the interface and prevents optimal vertical utilization.

Furthermore, Sprint 2026-41.1 features ticket ATT-2458, which introduces a dedicated Route Selection card/button on the Control Tracking screen. To accommodate the route selector cleanly without shifting the large, athlete-preferred centered "Start Tracking" button, the pairing triggers must be relocated to the dedicated sensor management hub (`DevicesTabbedScreen.kt` / "Meine Sensoren"), and `ControlTrackingScreen.kt` must provide a flexible `bottomContent` slot.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-278` (*Relocate Sensor Pairing to Sensor Management and Optimize Control Tracking Screen Layout*)
  * Relocate pairing entry point from `ControlTrackingScreen.kt` to `DevicesTabbedScreen.kt`.
  * Implement Material 3 Floating Action Button (FAB) in `DevicesTabbedScreen.kt` (`Icons.Default.Add`, `@string/devices_pair_sensor`).
  * Implement `PairingProtocolBottomSheet.kt` for choosing ANT+ or Bluetooth LE protocols, seamlessly cascading to `DeviceTypeSelectionDialog.kt`.
  * Dynamically reconfigure and search via `DevicesTabbedViewModel.updateFilters(protocol, deviceType)`.
  * Refactor `ControlTrackingScreen.kt` to preserve strict vertical centering of `ControlTrackingButton` and expose `bottomContent: @Composable ColumnScope.() -> Unit = {}`.
  * Ensure 100% 9-language translation parity across all newly introduced string resources.
* **Test Mapping**: `TST-UI-238` (*Pairing Triggers Relocation to Sensor Management & Control Tracking Layout Optimization Verification*)
  * `TST-UI-238.1`: Layout and slot contract test for `ControlTrackingScreen.kt` (`ControlTrackingLayoutContractTest.kt`).
  * `TST-UI-238.2`: Sensor management pairing UI contract test for `DevicesTabbedScreen.kt` (`DevicesTabbedScreenContractTest.kt`).
  * `TST-UI-238.3`: 9-language translation parity contract test (`PairingLocalizationTest.kt` & `TranslationParityTest.kt`).
  * `TST-UI-238.4`: Clean-room full suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Production Regressions**: Clean-room execution of `./gradlew testDebugUnitTest` must achieve 100% pass rate.
2. **Centered Control Button Invariant**: The large `ControlTrackingButton` and `SportTypeSelector` must remain vertically centered on `ControlTrackingScreen.kt` using balanced equal-weight spacers (`weight(1f)`).
3. **Location Gating Invariant (REQ-PRI-004)**: The mandatory fine location check, permission rationale cascading, and location warning badge on `ControlTrackingButton` must remain completely intact.
4. **Non-Breaking Call-Site Signature**: `ControlTrackingScreen` parameters for legacy pairing triggers are retained with default no-op arguments to avoid breaking existing previews or external callers while deprecating obsolete triggers.
5. **Subtask Self-Sufficiency**: Subtask [ATT-2557](https://atrainingtracker.atlassian.net/browse/ATT-2557) transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket [ATT-2189](https://atrainingtracker.atlassian.net/browse/ATT-2189) remains reserved for the human user in `Final Review (Human)`.

---

## 4. UI Consistency (Rule 23 Compliance)

In strict accordance with `docs/design_guidelines.md` (Section 5):
* **Closest Existing Reference Screens**:
  * `DevicesTabbedScreen.kt`: Tabbed container for connected, paired, and known sensors.
  * `RouteTabbedScreen.kt`: Uses standard Material 3 FAB (`Icons.Default.Add`) anchored at the bottom end with `navigationBarsPadding()`.
  * `DeviceTypeSelectionDialog.kt`: Uses `AppModalBottomSheet` with `ListItem`, protocol icons, and rounded corner shapes.
* **Reused Components**:
  * `AppModalBottomSheet` (`ui/components/core/AppModalBottomSheet.kt`): For `PairingProtocolBottomSheet.kt`.
  * `DeviceTypeSelectionDialog` (`ui/devices/DeviceTypeSelectionDialog.kt`): Reused directly for device type filtering after protocol selection.
  * `ListItem` with leading protocol icon: Matching the visual pattern established in `DeviceTypeSelectionDialog.kt`.
* **Theme Tokens**:
  * FAB: `MaterialTheme.colorScheme.primaryContainer` and `MaterialTheme.colorScheme.onPrimaryContainer`.
  * Spacing: `16.dp` padding for FAB from screen edges, `8.dp` inter-element spacing.
  * Shapes: Standard Material 3 FAB shape and `RoundedCornerShape(12.dp)` for bottom sheet surfaces.
  * Typography: `MaterialTheme.typography.titleMedium` and `bodyLarge`.
* **Justification for New Styles**: No custom or one-off styles are introduced. All elements leverage canonical Material 3 and theme tokens.

---

## 5. Proposed Architectural Changes (SWE.2 Architecture)

```
┌────────────────────────────────────────────────────────────────────────┐
│ UI Layer: DevicesTabbedScreen.kt                                       │
│ - Houses FloatingActionButton (Icons.Default.Add)                      │
│ - Tapping FAB opens PairingProtocolBottomSheet                         │
│ - Selecting Protocol opens DeviceTypeSelectionDialog                   │
│ - Selecting DeviceType calls tabViewModel.updateFilters(protocol, type)│
│ - Auto-switches pager to Tab 0 (Available Sensors)                     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ ViewModel Layer: DevicesTabbedViewModel.kt                             │
│ - updateFilters(newProtocol, newDeviceType)                            │
│ - Restarts sensor discovery with active protocol & type filter         │
│ - Updates SavedStateHandle for process death resilience                │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│ UI Layer: ControlTrackingScreen.kt                                     │
│ - Removes PairingButtons from column body                              │
│ - Top weight(1f) spacer + Bottom weight(1f) spacer                     │
│ - Exposes bottomContent: @Composable ColumnScope.() -> Unit = {}       │
│ - Center alignment of Start Tracking button preserved perfectly        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Step-by-Step Implementation Sequence

### Step 1: 9-Language Localization Additions
* Add string resources in `res/values/strings.xml` and all 8 localized `strings.xml` files (`values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
  * `devices_pair_sensor`: "Sensor koppeln" / "Pair sensor"
  * `devices_pair_protocol_title`: "Übertragungsprotokoll wählen" / "Select protocol"
  * `devices_pair_ant_plus`: "ANT+ Sensor"
  * `devices_pair_bluetooth_le`: "Bluetooth LE Sensor"

### Step 2: Protocol Selection Bottom Sheet Component (`PairingProtocolBottomSheet.kt`)
* Create `com/atrainingtracker/banalservice/ui/devices/PairingProtocolBottomSheet.kt`:
  * Implemented with `AppModalBottomSheet`.
  * Displays options for `Protocol.ANT_PLUS` and `Protocol.BLUETOOTH_LE`.
  * Includes protocol icons (`R.drawable.ant_black` / `R.drawable.bluetooth_black`) and localized descriptive labels.
  * Invokes `onProtocolSelected(Protocol)` and `onDismiss()`.

### Step 3: Integrate Pairing FAB into `DevicesTabbedScreen.kt` & Connect to `DevicesTabbedViewModel`
* In `DevicesTabbedScreen.kt`:
  * Add state variables: `var showProtocolSheet by remember { mutableStateOf(false) }`, `var selectedPairingProtocol by remember { mutableStateOf<Protocol?>(null) }`.
  * In the main screen layout, render `FloatingActionButton` aligned to `Alignment.BottomEnd` with `Modifier.padding(16.dp).navigationBarsPadding()`.
  * When clicked, sets `showProtocolSheet = true`.
  * If `showProtocolSheet`, renders `PairingProtocolBottomSheet`:
    * On protocol selected, set `selectedPairingProtocol = protocol` and `showProtocolSheet = false`.
  * If `selectedPairingProtocol != null`, renders `DeviceTypeSelectionDialog`:
    * On device type selected: calls `tabViewModel.updateFilters(protocol, deviceType)`, animates pager to page 0 (`pagerState.animateScrollToPage(0)`), and resets dialog state.
* In `DevicesTabbedViewModel.kt`:
  * Ensure `updateFilters(newProtocol, newDeviceType)` restarts device discovery cleanly if currently searching.

### Step 4: Refactor `ControlTrackingScreen.kt` Layout & Introduce `bottomContent` Slot
* In `ControlTrackingScreen.kt`:
  * Add parameter `bottomContent: @Composable ColumnScope.() -> Unit = {}`.
  * Remove `PairingButtons(...)` from the bottom of the Column.
  * Render `bottomContent()` inside the Column after the bottom `Spacer(modifier = Modifier.weight(1f))`.
  * Make pairing callback parameters optional with default no-ops (`onPairingClicked = {}`, `selectingProtocol = null`, etc.).
  * Update Previews (`PreviewControlTrackingScreen`, etc.) to omit unused pairing parameters.

### Step 5: Adapt `TrackingTabsScreen.kt`
* In `TrackingTabsScreen.kt`:
  * Update `ControlTrackingScreen` call site to pass clean parameters and prepare for ATT-2458 route selection slot integration.

### Step 6: Targeted Contract & Unit Tests
* Author `ControlTrackingLayoutContractTest.kt`:
  * Verifies `ControlTrackingScreen.kt` does not render `PairingButtons`.
  * Verifies `ControlTrackingScreen` exposes and renders `bottomContent`.
  * Verifies vertical spacer centering configuration.
  * Verifies `ControlTrackingButton` location permission gating remains intact.
* Author `DevicesTabbedScreenContractTest.kt`:
  * Verifies `DevicesTabbedScreen.kt` renders pairing FAB.
  * Verifies `PairingProtocolBottomSheet` handles ANT+ and BLE selections.
  * Verifies `DevicesTabbedViewModel.updateFilters` reconfigures protocol and device type filters.
* Author `PairingLocalizationTest.kt`:
  * Verifies 100% presence and non-emptiness of all newly added pairing strings across all 9 supported locales.
* Execute targeted unit tests via `./gradlew testDebugUnitTest --tests com.atrainingtracker...`.

### Step 7: Clean-Room Full Suite Regression
* Run `./gradlew testDebugUnitTest` to ensure 100% test pass rate across the entire project test suite.

---

## 7. Verification & Rollback Strategy

* **Targeted Verification**:
  * Execute `ControlTrackingLayoutContractTest`, `DevicesTabbedScreenContractTest`, `PairingLocalizationTest`, and `TranslationParityTest`.
* **Full Regression**:
  * Run complete `./gradlew testDebugUnitTest` suite (expected pass rate: 100%).
* **Rollback Plan**:
  * Should regressions occur, git allows atomic branch reversion (`git checkout sprint/2026-41.1 -- <files>`).
