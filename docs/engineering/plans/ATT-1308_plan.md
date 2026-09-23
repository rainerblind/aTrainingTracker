# Engineering Implementation Plan - ATT-1308: DevicesTabbedViewModel SavedStateHandle Fallbacks & Crash Immunity

**Ticket**: [ATT-1308](https://rainerblind.atlassian.net/browse/ATT-1308)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Component**: `com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedViewModel`  
**Requirement Mapping**: `REQ-STB-011` (`docs/requirements.md`)  
**Test Spec Mapping**: `TST-STB-011` (`docs/tests.md`)  
**Branch**: `feature/ATT-1308`  

---

## 1. Executive Summary & Objective

The objective of this task is to eliminate the fatal `NullPointerException` crash during `DevicesTabbedViewModel.<init>` when navigating to "Meine Sensoren" in the Jetpack Compose Single-Activity architecture (`ATT-1083`), and resolve the secondary latent UI stall on `UiState.AwaitingDeviceTypeSelection`.

The implementation will introduce defensive fallbacks (`Protocol.ALL`, `DeviceType.ALL`), idempotently persist default values into `SavedStateHandle`, provide dynamic filter updating for pairing sessions via `updateFilters()`, and verify stability with dedicated clean-room unit tests.

---

## 2. Requirements Traceability Matrix

| Requirement Clause | Architecture / Component | Implementation Detail | Test Verification (`TST-STB-011`) |
| :--- | :--- | :--- | :--- |
| **`REQ-STB-011.1`** (Protocol Fallback) | `DevicesTabbedViewModel.kt` | Safe parse `savedStateHandle[PROTOCOL]`, fallback to `Protocol.ALL`, persist back to `savedStateHandle`. | `DevicesTabbedViewModelTest.testInit_withEmptySavedStateHandle_defaultsToProtocolAllAndTabsAll` |
| **`REQ-STB-011.2`** (DeviceType Fallback) | `DevicesTabbedViewModel.kt` | Safe parse `savedStateHandle[DEVICE_TYPE]`, fallback to `DeviceType.ALL`, initialize `_uiState` to `UiState.DisplayingTabs(DeviceType.ALL)`, persist back. | `DevicesTabbedViewModelTest.testInit_withEmptySavedStateHandle_defaultsToProtocolAllAndTabsAll` |
| **`REQ-STB-011.3`** (Dynamic Pairing Reconfiguration) | `DevicesTabbedViewModel.kt`, `MainActivityWithNavigation.kt` | Public `updateFilters(newProtocol, newDeviceType)` updating state and `savedStateHandle`; invoked from `startPairing`. | `DevicesTabbedViewModelTest.testUpdateFilters_updatesSavedStateAndEmitsNewTabsState` |
| **`REQ-STB-011.4`** (Malformed Fallback) | `DevicesTabbedViewModel.kt` | `runCatching { Protocol.valueOf(raw) }.getOrNull()` preventing `IllegalArgumentException` on corrupted state. | `DevicesTabbedViewModelTest.testInit_withMalformedString_fallsBackSafely` |

---

## 3. Step-by-Step Implementation Changes

### 3.1 Component 1: `DevicesTabbedViewModel.kt`
1. **Defensive Protocol Parsing**:
   Replace:
   ```kotlin
   val protocol: Protocol = Protocol.valueOf(savedStateHandle[BANALService.PROTOCOL]!!)
   ```
   With:
   ```kotlin
   val protocol: Protocol = savedStateHandle.get<String>(BANALService.PROTOCOL)?.let { raw ->
       runCatching { Protocol.valueOf(raw) }.getOrNull()
   } ?: Protocol.ALL.also { savedStateHandle[BANALService.PROTOCOL] = it.name }
   ```
2. **Defensive DeviceType Parsing & Immediate Tabs State**:
   In `init`, replace:
   ```kotlin
   val savedDeviceType: DeviceType? = savedStateHandle.get<String>(BANALService.DEVICE_TYPE)?.let {
       DeviceType.valueOf(it)
   }

   if (savedDeviceType != null) {
       _uiState.value = UiState.DisplayingTabs(savedDeviceType)
   } else {
       _uiState.value = UiState.AwaitingDeviceTypeSelection
   }
   ```
   With:
   ```kotlin
   val savedDeviceType: DeviceType = savedStateHandle.get<String>(BANALService.DEVICE_TYPE)?.let { raw ->
       runCatching { DeviceType.valueOf(raw) }.getOrNull()
   } ?: DeviceType.ALL.also { savedStateHandle[BANALService.DEVICE_TYPE] = it.name }

   _uiState.value = UiState.DisplayingTabs(savedDeviceType)
   ```
3. **Dynamic Filter Updating Method**:
   ```kotlin
   /**
    * Dynamically reconfigures protocol and device type filters for pairing sessions.
    */
   fun updateFilters(newProtocol: Protocol, newDeviceType: DeviceType?) {
       savedStateHandle[BANALService.PROTOCOL] = newProtocol.name
       val targetType = newDeviceType ?: DeviceType.ALL
       savedStateHandle[BANALService.DEVICE_TYPE] = targetType.name
       _uiState.value = UiState.DisplayingTabs(targetType)
   }
   ```

### 3.2 Component 2: `MainActivityWithNavigation.kt`
In `startPairing(protocol: Protocol, deviceType: DeviceType?)`:
```kotlin
fun startPairing(protocol: Protocol, deviceType: DeviceType?) {
    if (DEBUG) Log.d(TAG, "startPairing: $protocol, deviceType: $deviceType")
    try {
        val tabViewModel: DevicesTabbedViewModel = ViewModelProvider(this)[DevicesTabbedViewModel::class.java]
        tabViewModel.updateFilters(protocol, deviceType)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to pre-configure DevicesTabbedViewModel filters for pairing", e)
    }
    navigateToDrawerItem(R.id.drawer_my_sensors)
}
```

### 3.3 Component 3: Automated Unit Test Suite (`DevicesTabbedViewModelTest.kt`)
Create `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModelTest.kt` validating:
1. Clean instantiation with empty `SavedStateHandle()` asserting `protocol == Protocol.ALL`, `uiState == UiState.DisplayingTabs(DeviceType.ALL)`, and persisted defaults.
2. Malformed string tokens (e.g. `"BAD_PROTOCOL"`, `"BAD_DEVICE"`) safely falling back without exceptions.
3. Explicit valid parameters (`Protocol.ANT_PLUS`, `DeviceType.HEART_RATE`) preserved accurately.
4. `updateFilters` updating both live state and `SavedStateHandle`.

---

## 4. Preserved Invariants & Safety Constraints

- **Hardware Discovery Integrity**: `BANALServiceRepository.bindToBANALService()` and `unbindFromBANALService()` calls remain strictly balanced.
- **Process Death & Configuration Change**: Re-persisting defaults into `SavedStateHandle` guarantees state restoration across Android system process kills.
- **Thread Safety**: State transitions occur strictly on the main thread via `MutableLiveData.setValue`.
- **Zero Schema or Dependency Drift**: No changes to database schemas or third-party libraries.

---

## 5. Verification & Rollout Plan

1. Execute targeted unit test suite:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.*"
   ```
2. Execute full repository clean-room regression:
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. Physical hardware smoke test on `66020DLCR002FL`:
   - Launch app -> Open drawer -> Tap "Meine Sensoren".
   - Confirm screen opens cleanly to the "Bekannt" tab (index 2) with zero crashes.
