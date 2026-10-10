# Stage 1: Problem Domain & Root Cause Analysis - ATT-3099: Highlight and prioritize newly discovered sensors at top of scan list with notification

**Ticket**: [ATT-3099](https://atrainingtracker.atlassian.net/browse/ATT-3099)  
**Sub-task**: [ATT-3118](https://atrainingtracker.atlassian.net/browse/ATT-3118) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-UI-294` (*Newly Discovered Sensor Prioritization, Visual Highlight Badge & Discovery Feedback Notification*)  
**Test Spec ID**: `TST-UI-294`  
**Branch**: `feature/ATT-3099`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Problem Domain

### 1.1 The User Problem
During wireless sensor discovery (e.g. searching for BLE or ANT+ sensors such as HR straps, speed/cadence sensors, or power meters, as observed by the athlete during the Sprint 2026-41.7 Joint Review of ATT-2969), newly discovered sensors silently appear at the very bottom of a long list of known and paired devices.
Because the sensor catalog often contains numerous paired, known, or cached historical devices, the newly discovered device appears "below the fold" off-screen. Consequently:
1. The athlete receives zero immediate tactile, audible, or visual confirmation that a sensor has been detected.
2. The athlete has to scroll all the way to the bottom to verify if pairing discovery succeeded, creating uncertainty and frustration.

### 1.2 User Intent
When actively searching for sensors in the "Available" / "Sensors" screen:
1. **Prominent List Placement**: Newly discovered sensors during the current active scan must be placed at the very top of the list, sorted ahead of idle or historical devices.
2. **Immediate Discovery Notification / Feedback**: The athlete must receive clear, non-intrusive feedback (e.g. in-app snackbar with device name, visual highlight badge on the device card, haptic feedback) immediately when a target device is detected.

---

## 2. Forensic Investigation & Architectural Gap Analysis

### 2.1 Low-Level Discovery Mechanism (`DeviceManager.java` & `BANALService.java`)
- When a wireless peripheral (Bluetooth LE or ANT+) is detected during scanning:
  - `DeviceManager.java` (lines 201-202, 231-232) inserts the device into SQLite via `DevicesDatabaseManager` and adds the `deviceId` to `mNewlyFoundDevices.add(deviceId)`.
  - It broadcasts `BANALService.NEW_DEVICE_FOUND_INTENT` with `intent.putExtra(BANALService.DEVICE_ID, deviceId)`.
  - In `BANALServiceRepository.kt`, periodic polling reads `binder.getIdsOfNewlyFoundDevices()` into `_newlyFoundDevicesIds: StateFlow<List<Long>>`.

### 2.2 Data Pipeline & Sorting Gap (`GetMergedDevicesUseCase.kt` & `DeviceListViewModel.kt`)
- `GetMergedDevicesUseCase.kt` combines `dbRepo.allDevices`, `serviceRepo.allActiveDevicesTelemetry`, and `serviceRepo.newlyFoundDevicesIds`.
- It evaluates `val isFound = foundIds.contains(knownDevice.id)`, but only sets `isConnected = true` and `lastSeen = "now"`. It does **not** expose a dedicated `isNewlyDiscovered` flag on `DeviceUiData`.
- Crucially, `DeviceListViewModel.getFilteredDevices` preserves the raw database query ordering (`dbList.map { ... }`), where newly inserted device records (highest ID) are placed at the bottom.
- There is currently **zero sorting logic** prioritizing newly discovered sensors at the top of the list.

### 2.3 UI & Feedback Gap (`DevicesTabbedScreen.kt` & `DeviceItem.kt`)
- `DevicesTabbedViewModel.kt` listens for `BANALService.NEW_DEVICE_FOUND_INTENT` and triggers `deviceDBRepository.handleNewDeviceFound()`, but does **not** emit an event to notify the UI about the newly discovered device name.
- `DevicesTabbedScreen.kt` has no `SnackbarHost` or haptic notification mechanism for device discovery events.
- `DeviceItem.kt` renders all connected items identically with a standard green LED dot; there is no visual "NEW" badge or highlight container border to distinguish sensors found in the current scanning session from already-known connected sensors.
- `DeviceListScreen.kt` does not auto-scroll to the top (`scrollState.animateScrollToItem(0)`) when a new sensor is discovered during active scanning.

---

## 3. Chesterton's Fence & Requirement Archaeology

* **Original Requirement ID & Target**: Amends and complements `REQ-UI-291` (*Sensors View Device Inventory Preservation During Active Pairing Scanning*, Sprint 2026-41.2, `ATT-2626`) and `REQ-UI-140` (*Universal FastScrollbar Integration Across All Application Collection Views*).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2626`, commit `7cb87a1d`). Ticket ATT-2626 modernized `SearchingHeader` and preserved multi-sensor inventory during pairing, but left the device list order un-prioritized.
* **Root Reason for Existing Formulation**: Previously, devices were loaded directly from SQLite Cursor order. In small catalogs (1–2 sensors), this was unnoticeable. With realistic athlete setups (multiple bikes, shoes, HR monitors, cadence, speed, power meters), newly discovered sensors ended up off-screen below the fold.
* **Preservation of Core Invariants**: Multi-sensor inventory preservation across all tabs, un-filtered display during pairing (`REQ-UI-291`), fast scrollbar operation (`REQ-UI-140`), tap-to-configure, and 100% full clean-room unit test pass rate MUST be strictly preserved.

---

## 4. Proposed Architectural Changes (SWE.1 / SWE.2)

### 4.1 Domain Model Enhancement (`DeviceData.kt`)
- Add property `val isNewlyDiscovered: Boolean = false` to `DeviceUiData`.
- In `GetMergedDevicesUseCase.kt`, populate `isNewlyDiscovered = foundIds.contains(knownDevice.id)`.

### 4.2 Priority Ordering in `DeviceListViewModel.kt`
- In `DeviceListViewModel.getFilteredDevices(spec: DeviceFilterSpec)`:
  Sort filtered devices with a deterministic comparator:
  1. `isNewlyDiscovered == true`: placed at the very top of the list. If multiple newly discovered devices exist, order by reverse index in `foundIds` (most recently found first).
  2. `isConnected == true`: active/connected devices come next.
  3. Idle/known devices: preserved in default ordering.

### 4.3 Visual Highlight & "NEW" Badge (`DeviceItem.kt`)
- When `device.isNewlyDiscovered == true`:
  - Render a prominent Material 3 "NEW" chip / badge (`MaterialTheme.colorScheme.primaryContainer`, bold text) in the item header next to the device name.
  - Apply an active highlight border (`BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))`) to draw glanceable visual attention.

### 4.4 Discovery Notification & Tactile Feedback (`DevicesTabbedViewModel.kt` & `DevicesTabbedScreen.kt`)
- In `DevicesTabbedViewModel.kt`:
  - When `BANALService.NEW_DEVICE_FOUND_INTENT` is received:
    - Extract `deviceId = intent.getLongExtra(BANALService.DEVICE_ID, -1L)`.
    - Fetch device name and emit to a reactive event channel `newDeviceDiscoveredEvent: SharedFlow<String>`.
- In `DevicesTabbedScreen.kt`:
  - Integrate a bottom-anchored `SnackbarHost(snackbarHostState)`.
  - Collect `tabViewModel.newDeviceDiscoveredEvent`:
    - Trigger `HapticFeedbackType.LongPress` via `LocalHapticFeedback`.
    - Display non-intrusive snackbar: `stringResource(R.string.devices_new_sensor_discovered, deviceName)`.
- In `DeviceListScreen.kt`:
  - Automatically scroll `scrollState.animateScrollToItem(0)` when a newly discovered device appears so the user immediately sees it in the top viewport without manual scrolling.

### 4.5 9-Language Localization
- Introduce new string tokens across all 9 supported locales:
  - `devices_newly_discovered_badge`: "NEW" (DE: "NEU", ES: "NUEVO", FR: "NOUVEAU", IT: "NUOVO", JA: "新規", NL: "NIEUW", PL: "NOWY", PT: "NOVO").
  - `devices_new_sensor_discovered`: "New sensor discovered: %1$s" (DE: "Neuer Sensor gefunden: %1$s", ES: "Nuevo sensor detectado: %1$s", etc.).

---

## 5. Target Scope Bounding (In-Scope vs Out-of-Scope)

### In-Scope
* Adding `isNewlyDiscovered` to `DeviceUiData` and mapping it in `GetMergedDevicesUseCase`.
* Sorting newly discovered sensors at the top in `DeviceListViewModel`.
* Rendering "NEW" badge and highlight border in `DeviceItem.kt`.
* Emitting discovery events from `DevicesTabbedViewModel` on `NEW_DEVICE_FOUND_INTENT`.
* Displaying discovery snackbar, performing haptic feedback, and auto-scrolling to top in `DevicesTabbedScreen.kt` / `DeviceListScreen.kt`.
* Complete 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* Unit tests verifying sorting, badge state, and discovery event emission.

### Out-of-Scope
* Changing low-level BlueZ GATT scanner or ANT+ async search algorithms.
* Modifying SQLite schema for `DEVICES` table.
* Altering equipment linking or sport type discovery matrix.

---

## 6. Risk Assessment & Invariant Guardrails

| Risk | Mitigation |
| :--- | :--- |
| Disruption of device ordering on non-Available tabs (e.g. Paired or Known) | The sorting prioritizes `isNewlyDiscovered` primarily when devices are actively found during search; for devices with `isNewlyDiscovered == false`, baseline ordering is strictly maintained. |
| Excessive snackbar spam if many BLE devices advertise simultaneously | `SharedFlow` with `BufferOverflow.DROP_OLDEST` and debounce/distinct device ID filtering ensures clean, non-intrusive presentation. |
| Unintended scroll jumps during manual inspection | Auto-scroll to item 0 is triggered only upon a new device discovery event while actively searching. |

---

## 7. Stage 1 Sign-off Checklist
- [x] Problem statement grounded in Sprint 2026-41.7 user observation.
- [x] Forensic investigation across `DeviceManager.java`, `BANALServiceRepository.kt`, `GetMergedDevicesUseCase.kt`, `DeviceListViewModel.kt`, and `DevicesTabbedScreen.kt`.
- [x] 4-field Chesterton's Fence archaeology audit completed against `REQ-UI-291`.
- [x] Scope bounded to UI sorting, visual badge, discovery snackbar, and haptic cue.
- [x] 9-language localization requirement specified.
