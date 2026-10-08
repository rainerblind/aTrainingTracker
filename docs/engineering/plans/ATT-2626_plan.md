# Implementation Plan - ATT-2626: Keep all device types visible in Sensors view during pairing instead of filtering to selected device type

**Ticket**: [ATT-2626](https://atrainingtracker.atlassian.net/browse/ATT-2626)  
**Sub-task**: ATT-2692 (`[Impl-Plan]`)  
**Date**: 2026-10-08  
**Author**: Antigravity (AI Assistant)  
**Reviewer**: Agent 2 (Auditor) / Rainer Blind (Human User)  
**Target Branch**: `feature/ATT-2626`  

---

## 1. Executive Summary & Architectural Design (SWE.2)

### 1.1 Goal
During sensor pairing setup in "Meine Sensoren" (`DevicesTabbedScreen.kt`), the athlete taps the '+' FAB, chooses a protocol (ANT+ or BLE), and selects a device profile (Heart Rate, Power, etc.). Previously, the dialog callback invoked `tabViewModel.updateFilters(protocol, chosenDeviceType)`, which mutated `tabViewModel.uiState` to `UiState.DisplayingTabs(chosenDeviceType)` and updated `tabViewModel.protocol = protocol`. As a consequence, `DeviceListViewModel.getFilteredDevices(spec)` filtered the lists across all tabs ("Verbunden", "Gekoppelt", "Bekannt") so that all sensors of different types (and other protocols) were abruptly hidden from view.

This implementation plan decouples the wireless hardware discovery scan from the UI tab filtering:
1. `DevicesTabbedViewModel` provides `startPairingScan(protocol, deviceType)`, which initiates targeted background scanning via `BANALServiceRepository.startSearchingForNewDevices(protocol, deviceType)` and tracks the active scanning target via `StateFlow`s, while keeping tab display state at `UiState.DisplayingTabs(DeviceType.ALL)` and `protocol = Protocol.ALL`.
2. `DevicesTabbedScreen.kt` invokes `startPairingScan` upon device type selection, collects the active scanning target, and passes it to `DeviceListScreen`.
3. `DeviceListScreen.kt` renders `SearchingHeader` reflecting the targeted protocol and sensor type being searched for ("Searching for Bluetooth LE Heart Rate devices"), while the list beneath and all other tabs continue displaying all sensors without restriction.

---

### 1.2 Architectural Changes & Layered Boundaries

```mermaid
graph TD
    UI[DevicesTabbedScreen.kt] -->|startPairingScan(protocol, deviceType)| VM[DevicesTabbedViewModel.kt]
    VM -->|startSearchingForNewDevices(protocol, deviceType)| REPO[BANALServiceRepository.kt]
    REPO -->|START_SEARCHING_FOR_NEW_DEVICES_INTENT| SVC[BANALService / DeviceManager]
    VM -->|scanningProtocol, scanningDeviceType StateFlows| UI
    UI -->|scanningProtocol, scanningDeviceType| DLS[DeviceListScreen.kt]
    DLS -->|Display targeted search banner| SH[SearchingHeader]
    DLS -->|Display all sensors unconstrained| LIST[DeviceList]
```

1. **`DevicesTabbedViewModel.kt`**:
   - Expose `scanningProtocol: StateFlow<Protocol?>` and `scanningDeviceType: StateFlow<DeviceType?>`.
   - Add `startPairingScan(protocolToPair: Protocol, deviceTypeToPair: DeviceType)`.
   - Update `stopSearching()` to reset `scanningProtocol` and `scanningDeviceType` to `null`.
   - Maintain `startSearching()` and `updateFilters(...)` for backward compatibility.
2. **`DevicesTabbedScreen.kt`**:
   - In `DeviceTypeSelectionDialog.onSelected`: invoke `tabViewModel.startPairingScan(protocolToPair, chosenDeviceType)` and scroll pager to page 0 (`Available`).
   - Collect `scanningProtocol` and `scanningDeviceType` and supply them to `DeviceListScreen`.
3. **`DeviceListScreen.kt`**:
   - Add parameters `scanningProtocol: Protocol? = null` and `scanningDeviceType: DeviceType? = null`.
   - In `SearchingHeader`, resolve display names using `scanningProtocol ?: filterSpec.protocol` and `scanningDeviceType ?: filterSpec.deviceType`.
4. **`MainActivityWithNavigation.kt`**:
   - In `startPairing(protocol, deviceType)`: if `deviceType != null`, invoke `tabViewModel.startPairingScan(protocol, deviceType)`.

---

## 2. UI Consistency (Rule 23)

* **Reference Screen / Components**:
  - `DevicesTabbedScreen.kt` (Sensors screen) and `DeviceListScreen.kt` (`SearchingHeader`).
* **Design Tokens & Theme Compliance**:
  - `SearchingHeader` uses `MaterialTheme.colorScheme.surfaceContainerLow` with `TTAlpha.Medium`, rounded with `MaterialTheme.shapes.medium`.
  - Indicator uses `CircularProgressIndicator` with `MaterialTheme.colorScheme.primary`.
  - Typography adheres to `MaterialTheme.typography.bodyMedium` with `FontWeight.Medium` and `MaterialTheme.colorScheme.onSurfaceVariant`.
* **Zero Layout Regressions**:
  - The tab header title remains "Sensoren" (`R.string.devices_all_sensors`), preserving consistent title semantics without jumping icons.

---

## 3. Invariants & Guardrails

1. **Hardware Discovery Invariant**: Targeted background scanning via `BANALServiceRepository.startSearchingForNewDevices(protocol, deviceType)` MUST continue to pass the exact selected `Protocol` and `DeviceType` to the Android service intent.
2. **All Sensor Inventory Invariant**: Tab `DeviceFilterSpec`s for `CONNECTED`, `PAIRED`, and `ALL_KNOWN` MUST retain `DeviceType.ALL` and `Protocol.ALL` during pairing flows so existing sensors remain fully accessible.
3. **SavedStateHandle Resilience (`REQ-STB-011`)**: SavedStateHandle defensive fallbacks for missing or unparseable tokens MUST remain intact.
4. **Localization Parity**: Reuses existing `R.string.devices_searchingForDevice` with 100% 9-language translation parity.
5. **Clean-Room Test Pass Rate**: 100% pass rate with zero regressions across `./gradlew testDebugUnitTest`.

---

## 4. Implementation Steps (Atomic Work Breakdown)

### Step 1: ViewModel Decoupling & Scanning State Tracking
- **File**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModel.kt`
- Add `_scanningProtocol` and `_scanningDeviceType` `MutableStateFlow`s with public read-only `StateFlow` accessors.
- Implement `startPairingScan(protocolToPair: Protocol, deviceTypeToPair: DeviceType)`.
- Update `stopSearching()` to clear scanning target state.
- Ensure `_uiState` remains `UiState.DisplayingTabs(DeviceType.ALL)` during pairing.

### Step 2: Screen Integration & SearchingHeader Presentation
- **File**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`
- Collect `scanningProtocol` and `scanningDeviceType`.
- In `DeviceTypeSelectionDialog.onSelected`: dispatch `tabViewModel.startPairingScan(protocolToPair, chosenDeviceType)`.
- Pass `scanningProtocol` and `scanningDeviceType` to `DeviceListScreen`.
- **File**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListScreen.kt`
- Update `SearchingHeader` invocation to utilize the active scanning parameters when non-null.

### Step 3: MainActivity Navigation Integration
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`
- Update `startPairing(protocol, deviceType)` to call `startPairingScan` when `deviceType != null`.

### Step 4: Unit & Contract Test Coverage
- **File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModelTest.kt`
- Author tests for:
  - `startPairingScan` dispatches targeted protocol/deviceType while keeping `uiState == DisplayingTabs(DeviceType.ALL)`.
  - `scanningProtocol` and `scanningDeviceType` state emission and reset upon `stopSearching()`.
- **File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreenContractTest.kt`
- Validate contract bindings for `startPairingScan` and unrestrictive tabs.

### Step 5: Verification & Full Clean-Room Regression
- Execute targeted unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.*"
  ```
- Execute full clean-room unit test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 5. Verification & Gate Governance
- Review against Gate 3 criteria:
  - SWE.2 architectural alignment and clean layering.
  - Traceability to `REQ-UI-291` and `TST-UI-251`.
  - Invariants preserved and risk assessed as LOW.
