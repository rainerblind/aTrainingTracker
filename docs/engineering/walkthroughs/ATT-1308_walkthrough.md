# Stage 4 Walkthrough: Defend DevicesTabbedViewModel Against SavedStateHandle NPE (ATT-1308)

**Ticket**: [ATT-1308](https://jira.example.com/browse/ATT-1308) - `[Bug] DevicesTabbedViewModel.<init>`
**Sub-task**: [ATT-1315](https://jira.example.com/browse/ATT-1315) - `[Implementation] DevicesTabbedViewModel.<init>`
**Parent Epic**: ATT-235 (No crashes)
**Target Version**: `V4.9.38`
**Requirement**: `REQ-STB-011`
**Verification Test**: `TST-STB-011`

---

## 1. Problem & Root Cause Summary
Following the Compose single-activity migration (ATT-1083), navigating to the Sensors screen ("Meine Sensoren") or launching pairing instantiated `DevicesTabbedViewModel` without arguments in `SavedStateHandle`.
The ViewModel constructor force-unwrapped `savedStateHandle[BANALService.PROTOCOL]!!`, resulting in an immediate `NullPointerException` on the main thread and a fatal crash.

Furthermore:
- If `BANALService.DEVICE_TYPE` was absent, `DevicesTabbedViewModel` entered `UiState.AwaitingDeviceTypeSelection`, causing an unexpected prompt instead of displaying known devices (`DeviceType.ALL`).
- When launching pairing externally via `MainActivityWithNavigation.startPairing(protocol, deviceType)`, there was no API to reconfigure the protocol and device type filters on an already-resolved ViewModel.

---

## 2. Changes Implemented

### 1. Hardened ViewModel State Initialization & Safe Fallbacks (`DevicesTabbedViewModel.kt`)
- Replaced forced unwrap of `savedStateHandle[BANALService.PROTOCOL]!!` with safe parsing:
  - Defaults to `Protocol.ALL` if key is null or malformed.
  - Automatically writes the resolved default back into `savedStateHandle` for process-death idempotency.
- Safely parses `savedStateHandle[BANALService.DEVICE_TYPE]`:
  - Defaults to `DeviceType.ALL` if null or malformed.
  - Automatically writes the resolved default back into `savedStateHandle`.
  - Sets initial UI state directly to `UiState.DisplayingTabs(savedDeviceType)`, avoiding the unwanted device type picker dialog.
- Added public API `updateFilters(newProtocol: Protocol, newDeviceType: DeviceType?)`:
  - Reconfigures `protocol` and `savedStateHandle` dynamically.
  - Safely falls back null `deviceType` to `DeviceType.ALL`.
  - Updates `_uiState` to `UiState.DisplayingTabs(targetType)`.

### 2. Pairing Coordination Integration (`MainActivityWithNavigation.kt`)
- Updated `startPairing(protocol: Protocol, deviceType: DeviceType?)` to retrieve `DevicesTabbedViewModel` via `ViewModelProvider(this)[DevicesTabbedViewModel::class.java]` and invoke `updateFilters(protocol, deviceType)` before navigating to the sensors drawer item.

### 3. Unit Test Verification Suite (`DevicesTabbedViewModelTest.kt`)
- Created `DevicesTabbedViewModelTest` validating `TST-STB-011`:
  - `testInit_withEmptySavedStateHandle_defaultsToProtocolAllAndTabsAll`: Verifies clean initialization with empty `SavedStateHandle`.
  - `testInit_withMalformedStringTokens_fallsBackSafely`: Verifies safe fallback on unrecognized enum strings.
  - `testInit_withExplicitValidParameters_preservesGivenValues`: Verifies preservation of valid arguments.
  - `testUpdateFilters_updatesSavedStateAndEmitsNewTabsState`: Verifies dynamic reconfiguration of protocol and device type filters.
  - `testUpdateFilters_withNullDeviceType_defaultsToDeviceTypeAll`: Verifies null safety when reconfiguring filters.

---

## 3. Verification & Test Evidence

### Targeted Unit Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedViewModelTest"
```
Output:
`BUILD SUCCESSFUL` (5 tests completed, 0 failed, 0 skipped).

### Full Clean-Room Regression Suite
```bash
./gradlew testDebugUnitTest
```
Output:
`BUILD SUCCESSFUL` (32 actionable tasks, 0 failures, 0 regressions).

### Requirement Governance
```bash
python3 tools/verify_requirement_governance.py
```
Output:
`Net-new requirement(s) detected: REQ-STB-011. Bypassing archaeology check cleanly.`
Exit code: `0`.
