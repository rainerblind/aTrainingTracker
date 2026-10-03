# Engineering Analysis - ATT-1308: DevicesTabbedViewModel SavedStateHandle NullPointerException & Default State Fallbacks

**Ticket**: [ATT-1308](https://rainerblind.atlassian.net/browse/ATT-1308)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Component**: `com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedViewModel`  
**Requirement Mapping**: `REQ-STB-011` (Proposed)  
**Test Spec Mapping**: `TST-STB-011` (Proposed)  

---

## 1. Executive Summary & Problem Statement

During physical device testing of the Jetpack Compose Single-Activity architecture (`ATT-1083`), opening the "Meine Sensoren" (Sensors) hub from the navigation drawer (`NavRoutes.SENSORS`) immediately triggers an application crash with `FATAL EXCEPTION: main`:

```text
2026-09-23 19:09:14.106 20380 20380 E AndroidRuntime: FATAL EXCEPTION: main
2026-09-23 19:09:14.106 20380 20380 E AndroidRuntime: Process: com.atrainingtracker.debug, PID: 20380
2026-09-23 19:09:14.106 20380 20380 E AndroidRuntime: java.lang.RuntimeException: An exception happened in constructor of class com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedViewModel
2026-09-23 19:09:14.106 20380 20380 E AndroidRuntime: 	at androidx.lifecycle.SavedStateViewModelFactory_androidKt.newInstance(SavedStateViewModelFactory.android.kt:264)
2026-09-23 19:09:14.106 20380 20380 E AndroidRuntime: 	at androidx.lifecycle.SavedStateViewModelFactory.create(SavedStateViewModelFactory.android.kt:147)
2026-09-23 19:09:14.106 20380 20380 E AndroidRuntime: 	at androidx.lifecycle.viewmodel.compose.ViewModelKt__ViewModelKt.viewModel(ViewModel.kt:104)
2026-09-23 19:09:14.106 20380 20380 E AndroidRuntime: 	at com.atrainingtracker.trainingtracker.ui.navigation.ATrainingTrackerAppKt.ATrainingTrackerApp$lambda$8$1$0$0$0$7(ATrainingTrackerApp.kt:526)
...
2026-09-23 19:09:14.106 20380 20380 E AndroidRuntime: Caused by: java.lang.NullPointerException
2026-09-23 19:09:14.106 20380 20380 E AndroidRuntime: 	at com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedViewModel.<init>(DevicesTabbedViewModel.kt:74)
```

This defect prevents athletes from managing sensors, inspecting connected ANT+/BLE devices, or pairing new equipment from the modernized single-activity user interface.

---

## 2. Root Cause Analysis (RCA)

### 2.1 Unchecked Non-Null Assertion on `SavedStateHandle`
In `DevicesTabbedViewModel.kt:74`:
```kotlin
// Protocol is retrieved once from SavedStateHandle, which gets it from the fragment's arguments.
val protocol: Protocol = Protocol.valueOf(savedStateHandle[BANALService.PROTOCOL]!!)
```
The property initialization performs an unconditional non-null assertion (`!!`) against `savedStateHandle[BANALService.PROTOCOL]`.

### 2.2 Architectural Shift: Legacy Fragment vs. Compose Navigation
- **Legacy Architecture**: `DevicesTabbedContainerFragment.newInstance(protocol, deviceType, startingTab)` explicitly packed `BANALService.PROTOCOL` into the Fragment's `arguments` Bundle. When `by viewModels()` was invoked, `SavedStateViewModelFactory` automatically merged the Fragment's arguments into the `SavedStateHandle`.
- **Compose Single-Activity Architecture**: In `ATrainingTrackerApp.kt:298`:
  ```kotlin
  composable(NavRoutes.SENSORS) {
      val tabViewModel: DevicesTabbedViewModel = viewModel(activity)
      DevicesTabbedScreen(...)
  }
  ```
  `DevicesTabbedViewModel` is resolved through the `activity` `ViewModelStoreOwner`. Because `MainActivityWithNavigation` does not have `BANALService.PROTOCOL` pre-populated in its default `SavedStateHandle`, `savedStateHandle[BANALService.PROTOCOL]` returns `null`, precipitating the fatal `NullPointerException`.

### 2.3 Secondary Latent Defect: Indeterminate Loading State for Missing `DEVICE_TYPE`
In `DevicesTabbedViewModel.kt:89-100`:
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
If `savedDeviceType` is `null`, `_uiState` defaults to `UiState.AwaitingDeviceTypeSelection`. However, in `DevicesTabbedScreen.kt:229-235`:
```kotlin
else -> {
    // Should not happen if started from Control Screen
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
```
When `_uiState` is `AwaitingDeviceTypeSelection`, `DevicesTabbedScreen` renders an indeterminate `CircularProgressIndicator()` indefinitely because the device type picker dialog is not shown. When an athlete navigates to "Meine Sensoren" from the navigation drawer, the intended behavior is to display all known sensors across all categories (`DeviceType.ALL`, `Protocol.ALL`, initial tab index 2: "Known").

---

## 3. Preserved Invariants & Boundary Verification

1. **NPE Immunity Invariant**: `DevicesTabbedViewModel` MUST instantiate cleanly with zero exceptions, regardless of whether `SavedStateHandle` is empty, contains `null`, or contains unparseable string tokens for `BANALService.PROTOCOL` or `BANALService.DEVICE_TYPE`.
2. **Deterministic UI State Invariant**: When navigating to the sensor hub without explicit bundle parameters (e.g. from the navigation drawer), `DevicesTabbedViewModel` MUST default to:
   - `protocol = Protocol.ALL`
   - `deviceType = DeviceType.ALL`
   - `_uiState = UiState.DisplayingTabs(DeviceType.ALL)`
3. **SavedState Idempotency**: Default values established during initial instantiation MUST be written back into `savedStateHandle` so that subsequent process recreation, configuration changes, or background kills deterministically preserve the state.
4. **Legacy Caller Parity**: Legacy invocations passing specific `Protocol` (e.g. `Protocol.ANT_PLUS`, `Protocol.BLUETOOTH_LE`) or specific `DeviceType` (e.g. `HEART_RATE`, `SPEED_AND_CADENCE`) via `DevicesTabbedContainerFragment` or pairing entry points MUST continue to configure the ViewModel with the requested filter criteria.
5. **Clean Hardware Binding Lifecycle**: `BANALServiceRepository.bindToBANALService()` in `init` and `banalServiceRepository.unbindFromBANALService()` in `onCleared()` MUST remain strictly paired to avoid leaking IPC service connections.

---

## 4. Proposed Solution & Architecture

### 4.1 Resilient State Resolution in `DevicesTabbedViewModel`
Refactor property initialization in `DevicesTabbedViewModel.kt` to use defensive parsing with fallback:
```kotlin
val protocol: Protocol = savedStateHandle.get<String>(BANALService.PROTOCOL)?.let { raw ->
    runCatching { Protocol.valueOf(raw) }.getOrNull()
} ?: Protocol.ALL.also { savedStateHandle[BANALService.PROTOCOL] = it.name }
```

In `init`:
```kotlin
val savedDeviceType: DeviceType = savedStateHandle.get<String>(BANALService.DEVICE_TYPE)?.let { raw ->
    runCatching { DeviceType.valueOf(raw) }.getOrNull()
} ?: DeviceType.ALL.also { savedStateHandle[BANALService.DEVICE_TYPE] = it.name }

_uiState.value = UiState.DisplayingTabs(savedDeviceType)
```

### 4.2 Dynamic Filter Updates for Pairing
Introduce a public method on `DevicesTabbedViewModel` allowing callers (such as `MainActivityWithNavigation.startPairing(protocol, deviceType)`) to reconfigure active pairing filters:
```kotlin
fun updateFilters(newProtocol: Protocol, newDeviceType: DeviceType?) {
    savedStateHandle[BANALService.PROTOCOL] = newProtocol.name
    val targetType = newDeviceType ?: DeviceType.ALL
    savedStateHandle[BANALService.DEVICE_TYPE] = targetType.name
    _uiState.value = UiState.DisplayingTabs(targetType)
}
```

---

## 5. Proposed Living Requirement: `REQ-STB-011`

| Requirement ID | Title | Description | Rationale | Target Files | Verification Protocol | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **REQ-STB-011** | **Resilient SavedStateHandle Fallbacks & Crash Immunity in DevicesTabbedViewModel.** | The system SHALL guarantee that `DevicesTabbedViewModel` initializes with complete crash immunity across all navigation paradigms (Compose NavHost, Fragment transactions, or process recreation) when `SavedStateHandle` arguments are missing or malformed:<br>1. *Defensive Protocol Parsing*: `protocol` SHALL safely parse `savedStateHandle[BANALService.PROTOCOL]`. If null, empty, or unparseable, it SHALL default to `Protocol.ALL` and persist the default to `savedStateHandle`.<br>2. *Defensive DeviceType Parsing*: In `init`, `deviceType` SHALL safely parse `savedStateHandle[BANALService.DEVICE_TYPE]`. If absent, it SHALL default to `DeviceType.ALL`, initialize `_uiState` to `UiState.DisplayingTabs(DeviceType.ALL)`, and persist the default to `savedStateHandle`.<br>3. *Dynamic Pairing Configuration*: The ViewModel SHALL provide `updateFilters(protocol, deviceType)` to reconfigure protocol and device type filters when pairing sessions are launched dynamically.<br>4. *Invariants*: BLE/ANT+ discovery and database persistence contracts MUST NOT be degraded. | Prevent fatal `NullPointerException` crashes and indefinite progress stalls when accessing the Sensors screen from Compose navigation. | `DevicesTabbedViewModel.kt`, `ATrainingTrackerApp.kt` | `TST-STB-011` | Proposed |

---

## 6. Verification Plan & Test Strategy (`TST-STB-011`)

1. **Automated Unit Testing (`DevicesTabbedViewModelTest.kt`)**:
   - `testInit_withEmptySavedStateHandle_defaultsToProtocolAllAndTabsAll`: Instantiate ViewModel with an empty `SavedStateHandle()`, assert `protocol == Protocol.ALL`, assert `uiState.value is UiState.DisplayingTabs(DeviceType.ALL)`, assert values persisted in `SavedStateHandle`.
   - `testInit_withInvalidEnumStrings_fallsBackSafelyWithoutException`: Provide corrupted strings (e.g. `"INVALID_PROTO"`), assert safe fallback to `Protocol.ALL` and `DeviceType.ALL`.
   - `testInit_withValidParameters_preservesGivenProtocolAndDeviceType`: Provide `Protocol.ANT_PLUS` and `DeviceType.HEART_RATE`, assert accurate extraction.
   - `testUpdateFilters_updatesSavedStateAndEmitsNewTabsState`: Call `updateFilters(Protocol.BLUETOOTH_LE, DeviceType.POWER)`, verify immediate state emission.
2. **Clean-Room Regression Suite**:
   - Execute `./gradlew testDebugUnitTest` across all modules (target 0 failures).
3. **Physical Hardware Verification**:
   - Launch application on test device (`66020DLCR002FL`).
   - Open drawer and tap "Meine Sensoren".
   - Verify screen opens immediately to the "Bekannt" tab (index 2) showing all paired sensors with zero crashes and zero progress bar deadlocks.
