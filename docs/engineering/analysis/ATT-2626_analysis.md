# Stage 1 Analysis: ATT-2626 - Keep all device types visible in Sensors view during pairing instead of filtering to selected device type

**Ticket**: [ATT-2626](https://atrainingtracker.atlassian.net/browse/ATT-2626)  
**Sub-task**: [ATT-2690](https://atrainingtracker.atlassian.net/browse/ATT-2690) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2626`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.1 review on physical device (Google Pixel 10), sensor pairing behavior in the Sensors screen ("Meine Sensoren", `DevicesTabbedScreen.kt`) was tested.
When an athlete initiates sensor pairing:
1. The athlete taps the '+' Floating Action Button in `DevicesTabbedScreen.kt`.
2. The athlete selects a wireless protocol (`Protocol.ANT_PLUS` or `Protocol.BLUETOOTH_LE`) via `PairingProtocolBottomSheet`.
3. The athlete selects a specific sensor hardware profile (e.g. `DeviceType.HEARTRATE` or `DeviceType.BIKE_POWER`) via `DeviceTypeSelectionDialog`.
4. Currently, the selection callback calls:
   ```kotlin
   tabViewModel.updateFilters(protocolToPair, chosenDeviceType)
   ```
5. This updates `tabViewModel.protocol = protocolToPair` and `_uiState = UiState.DisplayingTabs(chosenDeviceType)`.
6. In `DevicesTabbedScreen.kt`, the tabs for `DeviceListScreen` are constructed from `protocol` and `deviceType`:
   ```kotlin
   val tabs = remember(protocol, deviceType) {
       listOf(
           DeviceFilterSpec(DeviceFilterType.CONNECTED, protocol, deviceType),
           DeviceFilterSpec(DeviceFilterType.PAIRED, protocol, deviceType),
           DeviceFilterSpec(DeviceFilterType.ALL_KNOWN, protocol, deviceType)
       )
   }
   ```
7. Consequently, `DeviceListViewModel.getFilteredDevices(spec)` filters the device list to show **ONLY** devices matching `chosenDeviceType` and `protocolToPair`.

### Problem Statement
In legacy versions of the app, entering the sensor screen occurred via dedicated type-specific buttons (e.g. "Connect Heart Rate"), so filtering to that specific type was intentional.
However, in the unified navigation model of `aTrainingTracker`:
* The athlete is already on the Sensors screen viewing their complete sensor setup.
* Tapping the '+' FAB is an action to discover a new sensor; it should **NOT** suddenly hide or filter out all their existing, already-connected, paired, or known sensors of other types (power meters, cadences, radars, trainers, etc.).
* Filtering all tabs causes a jarring UI shift where previously visible sensors vanish from the screen.

### Expected Behavior
After selecting a device type for pairing and initiating the scan:
* Active wireless scanning must target the requested device type and protocol (`banalServiceRepository.startSearchingForNewDevices(pairingProtocol, pairingDeviceType)`).
* The Sensors screen tabs ("Verbunden", "Gekoppelt", "Bekannt") must continue to display all device types without applying a restrictive device type filter (`deviceType = DeviceType.ALL`, `protocol = Protocol.ALL`).
* In the "Verbunden" (Available) tab, the prominent `SearchingHeader` banner must accurately announce the target protocol and device type being searched for (e.g. *"Suche nach Bluetooth LE Herzfrequenz Sensoren"* / *"Searching for Bluetooth LE Heart Rate devices"*).
* All existing connected, paired, and known sensors of other types remain visible across their respective tabs.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Architectural Coupling of UI Filter vs. Hardware Scan Target
* In commit `761d72c1` (Sprint 2026-41.1, `ATT-2189`), when pairing triggers were relocated from the tracking control HUD to `DevicesTabbedScreen`, the pairing callback reused `DevicesTabbedViewModel.updateFilters(protocol, deviceType)`:
  ```kotlin
  fun updateFilters(newProtocol: Protocol, newDeviceType: DeviceType?) {
      protocol = newProtocol
      savedStateHandle[BANALService.PROTOCOL] = newProtocol.name
      val targetType = newDeviceType ?: DeviceType.ALL
      savedStateHandle[BANALService.DEVICE_TYPE] = targetType.name
      _uiState.value = UiState.DisplayingTabs(targetType)
      if (isSearching) {
          banalServiceRepository.stopSearchingForNewDevices()
          banalServiceRepository.startSearchingForNewDevices(protocol, targetType)
      }
  }
  ```
* `updateFilters` conflated two orthogonal concepts:
  1. The **UI display filter** for the 5 tabs (`_uiState = UiState.DisplayingTabs(targetType)` and `protocol = newProtocol`).
  2. The **hardware scanning target** for wireless discovery (`banalServiceRepository.startSearchingForNewDevices(protocol, targetType)`).
* Because both concepts were driven by the same properties (`protocol` and `_uiState`), configuring a pairing scan for Heart Rate unavoidably filtered the entire UI inventory to Heart Rate sensors only.

### 2.2 Call Sites Analysis
`updateFilters` is invoked in two places:
1. `DevicesTabbedScreen.kt` (line 332): When the athlete selects a device type from `DeviceTypeSelectionDialog` via the pairing FAB.
2. `MainActivityWithNavigation.kt` (line 898): In `startPairing(protocol, deviceType)`, called when navigating to pairing from the tracking HUD cockpit action.

In both workflows, the athlete desires to initiate pairing discovery for a specific sensor profile while keeping the Sensors screen inventory comprehensive and unconstrained.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Objectives**:
  1. Decouple the hardware pairing scan target from the Sensors tab display filters in `DevicesTabbedViewModel`.
  2. Introduce `startPairingScan(protocol: Protocol, deviceType: DeviceType)` in `DevicesTabbedViewModel` that dispatches targeted wireless discovery to `BANALServiceRepository` while preserving unrestrictive tab display (`DeviceType.ALL`, `Protocol.ALL`).
  3. Maintain and expose the active scanning target (protocol and device type) via StateFlow so that `SearchingHeader` in `DeviceListScreen.kt` accurately displays the protocol and sensor type being searched for.
  4. Ensure all tabs ("Verbunden", "Gekoppelt", "Bekannt", "Fahrräder", "Schuhe") continue displaying all sensor types during active pairing.
  5. Add unit and contract tests in `DevicesTabbedViewModelTest` and `DevicesTabbedScreenContractTest` verifying that pairing initiation does not restrict tab `DeviceFilterSpec`s.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying `DeviceTypeSelectionDialog.kt` (already completed in `ATT-2625`).
  * Changing low-level BLE GATT or ANT+ scanning implementations in `DeviceManager.java` or `BANALService.java`.
  * Modifying equipment sensor matrix logic (`EquipmentSportSensorMatrix.kt`).

---

## 4. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

* **Original Requirement ID & Target**: `REQ-STB-011` (*Dynamic Pairing Filter Configuration in DevicesTabbedViewModel*) and `REQ-UI-278` (*Pairing Triggers Relocation to Sensor Management*).
* **Historical Origin & Commit Trace**:
  - `REQ-STB-011`: Sprint 2026-40.16 (SCRUM-37 / commit `d4b2e864b7`), established `updateFilters` to prevent NPE crashes during dynamic pairing.
  - `REQ-UI-278`: Sprint 2026-41.1 (`ATT-2189` / commit `761d72c1`), relocated pairing triggers to `DevicesTabbedScreen`.
* **Root Reason for Existing Formulation**:
  `updateFilters` originally assumed that if the user entered a pairing flow for Heart Rate, they only wanted to see Heart Rate sensors on screen. However, on-device usage revealed that athletes expect the unified Sensors screen to act as their complete sensor dashboard without hiding already-configured or connected sensors.
* **Preservation of Core Invariants**:
  - Background scanning continues to dispatch targeted discovery intents (`BANALService.START_SEARCHING_FOR_NEW_DEVICES_INTENT`) with the selected `PROTOCOL` and `DEVICE_TYPE`.
  - Discovered sensors are still broadcast via `BANALService.NEW_DEVICE_FOUND_INTENT` and ingested via `deviceDBRepository.handleNewDeviceFound()`.
  - SavedStateHandle resilience (`REQ-STB-011`) for process death recovery remains 100% intact.
  - 100% clean-room test suite pass rate with zero regressions.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 `DevicesTabbedViewModel` Decoupling
1. Add state tracking for the active pairing scan target:
   ```kotlin
   private val _scanningProtocol = MutableStateFlow<Protocol?>(null)
   val scanningProtocol: StateFlow<Protocol?> = _scanningProtocol.asStateFlow()

   private val _scanningDeviceType = MutableStateFlow<DeviceType?>(null)
   val scanningDeviceType: StateFlow<DeviceType?> = _scanningDeviceType.asStateFlow()
   ```
2. Introduce `startPairingScan(targetProtocol: Protocol, targetDeviceType: DeviceType)`:
   * Sets `_scanningProtocol.value = targetProtocol` and `_scanningDeviceType.value = targetDeviceType`.
   * Triggers `banalServiceRepository.startSearchingForNewDevices(targetProtocol, targetDeviceType)`.
   * Preserves `_uiState.value = UiState.DisplayingTabs(DeviceType.ALL)` and `protocol = Protocol.ALL` so that all sensors remain visible.
   * Marks `isSearching = true`.
3. In `stopSearching()`:
   * Resets `_scanningProtocol.value = null` and `_scanningDeviceType.value = null`.
   * Calls `banalServiceRepository.stopSearchingForNewDevices()`.
   * Marks `isSearching = false`.

### 5.2 `DevicesTabbedScreen` Integration
* In `DeviceTypeSelectionDialog.onSelected`:
  Invoke `tabViewModel.startPairingScan(protocolToPair, chosenDeviceType)` instead of mutating tab filters.
* Pass `activeScanningProtocol` and `activeScanningDeviceType` to `DeviceListScreen`.

### 5.3 `DeviceListScreen` Presentation
* In `DeviceListScreen.kt`:
  When `isSearchingForNewDevices` is true, render `SearchingHeader` with `protocolName` and `deviceTypeName` resolved from the active scanning target (defaulting to the tab filter spec if no pairing scan is active).
* The `devices` list in all tabs continues to display all devices because `filterSpec.deviceType == DeviceType.ALL` and `filterSpec.protocol == Protocol.ALL`.
