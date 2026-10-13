# Stage 2: Requirement & Test Specification - ATT-2626: Keep all device types visible in Sensors view during pairing instead of filtering to selected device type

**Ticket**: [ATT-2626](https://atrainingtracker.atlassian.net/browse/ATT-2626)  
**Sub-task**: [ATT-2691](https://atrainingtracker.atlassian.net/browse/ATT-2691) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.3`  
**Branch**: `feature/ATT-2626`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (`REQ-UI-291`)

### REQ-UI-291: Sensors View Multi-Sensor Inventory Preservation During Active Pairing Scanning

The system SHALL preserve the complete multi-sensor inventory across all Sensors screen tabs ("Verbunden", "Gekoppelt", "Bekannt", "Fahrräder", "Schuhe") during sensor pairing discovery, decouple the hardware wireless discovery target from the UI tab filter specifications, and communicate the active scanning target in the Available tab header (ATT-2626):

1. **Unrestrictive Tab Filter Specifications (`DevicesTabbedScreen.kt` & `DevicesTabbedViewModel.kt`)**:
   - During active sensor pairing discovery, the Sensors screen tabs (`DeviceFilterSpec`s for `CONNECTED`, `PAIRED`, `ALL_KNOWN`) SHALL retain `deviceType = DeviceType.ALL` and `protocol = Protocol.ALL`, ensuring all existing, connected, paired, and known sensors across all device types and protocols remain visible.
   - Initiating a pairing scan SHALL NOT mutate `UiState.DisplayingTabs` to a restrictive `deviceType`.
   - The screen header title SHALL remain "Sensoren" (`R.string.devices_all_sensors`), preserving unified screen context.

2. **Decoupled Hardware Pairing Scan Dispatch (`DevicesTabbedViewModel.kt`)**:
   - `DevicesTabbedViewModel` SHALL provide a decoupled pairing scan initiation method `startPairingScan(protocol: Protocol, deviceType: DeviceType)`.
   - When a pairing scan is initiated, `DevicesTabbedViewModel` SHALL dispatch targeted background wireless discovery via `banalServiceRepository.startSearchingForNewDevices(protocol, deviceType)`.
   - `DevicesTabbedViewModel` SHALL maintain and expose the active scanning target (`scanningProtocol: StateFlow<Protocol?>` and `scanningDeviceType: StateFlow<DeviceType?>`).
   - When scanning is stopped (`stopSearching()`), the active scanning target SHALL reset to `null`.

3. **Context-Aware Searching Header Presentation (`DeviceListScreen.kt`)**:
   - In the "Verbunden" (Available) tab, when `isSearchingForNewDevices == true`:
     - `SearchingHeader` SHALL display the targeted protocol name and device type name currently being scanned for (`devices_searchingForDevice`, e.g. "Suche nach Bluetooth LE Herzfrequenz Sensoren" / "Searching for Bluetooth LE Heart Rate devices").
     - If no specific pairing target is active (e.g. general search), it SHALL fall back gracefully to the tab filter spec names.
   - The device list under `SearchingHeader` SHALL render all discovered and available devices without restrictive type filtering.

4. **Preserved Invariants & Localization**:
   - Zero new string resources required (reuses existing 100% localized `devices_searchingForDevice` format string across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT).
   - SavedStateHandle crash resilience (`REQ-STB-011`) and process death recovery MUST remain fully preserved.
   - Low-level GATT/ANT+ device discovery (`NEW_DEVICE_FOUND_INTENT`) ingestion into `DeviceDataRepository` MUST remain unbroken.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Refines `REQ-STB-011` (*Dynamic Pairing Filter Configuration in DevicesTabbedViewModel*) and `REQ-UI-278` (*Pairing Triggers Relocation to Sensor Management*).
2. **Historical Origin & Commit Trace**:
   - Commit `d4b2e864b7` (SCRUM-37): `DevicesTabbedViewModel.updateFilters` coupled UI tab filtering with scanner starting.
   - Commit `761d72c1` (ATT-2189): Relocated pairing triggers to `DevicesTabbedScreen.kt` and reused `updateFilters`, inadvertently causing all non-target sensors to be filtered out during pairing.
3. **Root Reason for Existing Formulation**: `updateFilters` was designed when device tabs were opened for a single dedicated device type. In the unified Sensors screen, however, an athlete expects to see their entire sensor inventory and not have it filtered away when clicking '+' to pair a new sensor.
4. **Preservation of Core Invariants**:
   - Profile-targeted scanning (`startSearchingForNewDevices(protocol, deviceType)`), device discovery broadcast processing, and full clean-room unit test pass rate remain strictly preserved.

---

### Acceptance Criteria (Given-When-Then)

* **Scenario 1: Initiating Sensor Pairing from '+' FAB**
  - **Given** an athlete in "Meine Sensoren" (`DevicesTabbedScreen`) with paired sensors (e.g. ANT+ HRM, BLE Power Meter),
  - **When** tapping '+' FAB and selecting Bluetooth LE and Heart Rate,
  - **Then** `banalServiceRepository.startSearchingForNewDevices` SHALL be invoked for `(Protocol.BLUETOOTH_LE, DeviceType.HEARTRATE)`.
  - **Then** the tabs ("Verbunden", "Gekoppelt", "Bekannt") SHALL continue to display all device types (`DeviceType.ALL`) and all protocols (`Protocol.ALL`).
  - **Then** the existing ANT+ HRM and BLE Power Meter SHALL remain visible in the "Gekoppelt" and "Bekannt" tabs.

* **Scenario 2: Searching Header Visual Feedback in Available Tab**
  - **Given** an active pairing scan for Bluetooth LE Heart Rate,
  - **When** viewing the "Verbunden" (Available) tab,
  - **Then** `SearchingHeader` SHALL display "Searching for Bluetooth LE Heart Rate devices" / "Suche nach Bluetooth LE Herzfrequenz Sensoren".
  - **Then** newly discovered devices SHALL appear in the list under the header.

* **Scenario 3: Stopping Sensor Search**
  - **Given** active sensor discovery,
  - **When** the screen is disposed or search stopped,
  - **Then** `stopSearching()` SHALL reset `scanningProtocol` and `scanningDeviceType` to null and stop background scanning.

---

## 2. Test Specification (`TST-UI-251`)

### TST-UI-251: Sensors View Multi-Sensor Inventory Preservation During Pairing Verification

1. **ViewModel Decoupling & State Verification (`DevicesTabbedViewModelTest.kt`)**:
   - Verify `startPairingScan(protocol, deviceType)` dispatches `startSearchingForNewDevices` with targeted protocol and device type.
   - Verify `startPairingScan` preserves `_uiState` as `UiState.DisplayingTabs(DeviceType.ALL)` and `protocol` as `Protocol.ALL`.
   - Verify `scanningProtocol` and `scanningDeviceType` emit the target pairing parameters.
   - Verify `stopSearching()` resets `scanningProtocol` and `scanningDeviceType` to `null`.

2. **Screen Tab Filter Spec Contract Verification (`DevicesTabbedScreenContractTest.kt`)**:
   - Verify `DevicesTabbedScreen` configures tabs with unrestrictive filter specs during pairing.
   - Verify `SearchingHeader` in `DeviceListScreen` receives active scanning target.

3. **9-Language Localization Audit (`TranslationParityTest.kt`)**:
   - Verify `devices_searchingForDevice` format string has matching 2 format specifiers (`%1$s`, `%2$s`) across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

4. **Clean-Room Regression Suite Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with 0 regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Target Artifact | Verification Criteria |
| :--- | :--- | :--- | :--- |
| `REQ-UI-291.1` (Unrestrictive Tab Filters) | `TST-UI-251.1` | `DevicesTabbedViewModelTest.kt` | `uiState` remains `DisplayingTabs(DeviceType.ALL)` during pairing. |
| `REQ-UI-291.2` (Decoupled Pairing Scan) | `TST-UI-251.1` | `DevicesTabbedViewModelTest.kt` | `startPairingScan` dispatches targeted scan and emits scanning target state. |
| `REQ-UI-291.3` (Searching Header Presentation) | `TST-UI-251.2` | `DeviceListScreen.kt`, `DevicesTabbedScreenContractTest.kt` | Active scan targets rendered in `SearchingHeader`. |
| `REQ-UI-291.4` (Localization & Invariants) | `TST-UI-251.3` | `TranslationParityTest.kt` | 100% 9-language translation parity for `devices_searchingForDevice`. |
| `REQ-PRO-001` (Zero Regressions) | `TST-UI-251.4` | `./gradlew testDebugUnitTest` | Full suite executes with 100% pass rate. |
