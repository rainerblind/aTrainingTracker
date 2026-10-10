# Stage 2: Requirement & Test Specification - ATT-3099: Highlight and prioritize newly discovered sensors at top of scan list with notification

**Ticket**: [ATT-3099](https://atrainingtracker.atlassian.net/browse/ATT-3099)  
**Sub-task**: [ATT-3119](https://atrainingtracker.atlassian.net/browse/ATT-3119) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-UI-334` (*Newly Discovered Sensor Prioritization, Visual Highlight Badge & Discovery Feedback Notification*)  
**Test Spec ID**: `TST-UI-294`  
**Branch**: `feature/ATT-3099`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Requirement Specification (REQ-UI-334)

### 1.1 Problem Statement & Rationale
During wireless sensor discovery (e.g. searching for BLE or ANT+ sensors such as HR straps, speed/cadence sensors, or power meters, as observed by the athlete during the Sprint 2026-41.7 Joint Review of ATT-2969), newly discovered sensors silently appear at the very bottom of a long list of known and paired devices.
Because the sensor catalog often contains numerous paired, known, or cached historical devices, the newly discovered device appears "below the fold" off-screen. Consequently:
1. The athlete receives zero immediate tactile, audible, or visual confirmation that a sensor has been detected.
2. The athlete has to scroll all the way to the bottom to verify if pairing discovery succeeded, creating uncertainty and frustration.

When actively searching for sensors in the "Available" / "Sensors" screen:
1. **Prominent List Placement**: Newly discovered sensors during the current active scan must be placed at the very top of the list, sorted ahead of idle or historical devices.
2. **Immediate Discovery Notification & Feedback**: The athlete must receive clear, non-intrusive feedback (e.g. in-app snackbar with device name, visual highlight badge on the device card, haptic vibration feedback, and auto-scroll to the top of the list) immediately when a target device is detected.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-291` (*Sensors View Device Inventory Preservation During Active Pairing Scanning*, ATT-2626) and complements `REQ-UI-140` (*Unified Sensors Tabbed Management*).
* **Historical Origin & Commit Trace**: Ticket `ATT-2626` (commit `ec62b083`) established tab filter preservation and decoupled hardware pairing scan dispatch. Before that, `DeviceItem.kt` (commit `191ba05d`, Sprint 2026-40.16) and `DeviceListViewModel.kt` ordered devices without distinguishing newly discovered sensors from existing catalog devices.
* **Root Reason for Existing Formulation**: Originally, scanning was assumed to occur on clean/sparse lists where only 1 or 2 devices were visible. In real-world usage with extensive sensor histories, new sensors were appended at the end of the list and pushed off-screen, leaving the user with zero visual or tactile confirmation of discovery.
* **Preservation of Core Invariants**: Full inventory visibility across tabs (`REQ-UI-291`), pairing state transitions, device rename/calibration, and full-suite test pass rate remain strictly preserved.

### 1.2 Functional & Architectural Requirements
1. **Newly Discovered Sensor Domain Model & Pipeline Flagging (`DeviceData.kt` & `GetMergedDevicesUseCase.kt`)**:
   * The system SHALL extend `DeviceUiData` in `DeviceData.kt` with property `val isNewlyDiscovered: Boolean = false`.
   * In `GetMergedDevicesUseCase.kt`, when mapping devices from `allDevices` and `newlyFoundDevicesIds`:
     `val isNewlyDiscovered = foundIds.contains(knownDevice.id)`.
     `DeviceUiData` instances mapped for newly discovered sensors during the active scanning session SHALL have `isNewlyDiscovered = true`.
2. **Top-Of-List Scan Priority Sorting (`DeviceListViewModel.kt`)**:
   * In `DeviceListViewModel.getFilteredDevices(spec: DeviceFilterSpec)`, the device list SHALL be ordered using deterministic multi-tier prioritization:
     a. Primary tier: Newly discovered devices (`isNewlyDiscovered == true`) sorted at the top of the list (index 0). Multiple newly discovered devices SHALL be sorted in recency order (most recently discovered first, e.g. reverse index in `foundIds`).
     b. Secondary tier: Connected devices (`isConnected == true`) next.
     c. Baseline tier: Remaining known / idle / paired devices in standard order (by name or ID).
3. **Visual Highlighting & "NEW" Badge Chip (`DeviceItem.kt`)**:
   * When `device.isNewlyDiscovered == true`, `DeviceItem` SHALL display:
     a. A prominent "NEW" badge chip (`devices_newly_discovered_badge`) next to the device name/LED indicator.
     b. An active highlight border around the card: `BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))`.
4. **Immediate Sensor Discovery Feedback & Auto-Scroll (`DevicesTabbedViewModel.kt`, `DevicesTabbedScreen.kt`, `DeviceListScreen.kt`)**:
   * `DevicesTabbedViewModel` SHALL observe device discovery (via `BANALService.NEW_DEVICE_FOUND_INTENT` or `newlyFoundDevicesIds` state flow transitions).
   * Upon detecting a newly discovered sensor, `DevicesTabbedViewModel` SHALL emit a single-shot event `newDeviceDiscoveredEvent: SharedFlow<String>` containing the localized name of the discovered device.
   * In `DevicesTabbedScreen.kt` (and/or `DeviceListScreen.kt`), observing `newDeviceDiscoveredEvent` SHALL:
     a. Display an in-app Material 3 `Snackbar` via `SnackbarHostState`: localized `devices_new_sensor_discovered` ("Neuer Sensor gefunden: %1$s" / "New sensor discovered: %1$s").
     b. Perform haptic vibration feedback via `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`.
     c. Smoothly auto-scroll the `LazyListState` to index 0 (`scrollState.animateScrollToItem(0)`) so the newly prioritized device is brought immediately into view.
5. **100% 9-Language Localization Parity**:
   * String resources `devices_newly_discovered_badge` ("NEU" / "NEW") and `devices_new_sensor_discovered` ("Neuer Sensor gefunden: %1$s" / "New sensor discovered: %1$s") SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries, matching format specifiers (`%1$s`), and literal `\n` linebreaks.
6. **Preservation of System Invariants**:
   * Zero disruption to pairing toggles (`onPairToggle`), device configuration dialogs (`EditDeviceDialog`), protocol/type filtering, or background BLE/ANT+ GATT connection and telemetry streaming.
   * 100% full clean-room unit test pass rate across all unit tests.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Domain Model isNewlyDiscovered Flagging)**:
  * *Given* a sensor discovered during an active search whose ID is in `newlyFoundDevicesIds`,
  * *When* `GetMergedDevicesUseCase` processes the device catalog,
  * *Then* the resulting `DeviceUiData` has `isNewlyDiscovered == true`.
* **Criterion 2 (Top-of-List Priority Sorting)**:
  * *Given* a list containing 5 existing known devices and 2 newly discovered devices,
  * *When* `DeviceListViewModel.getFilteredDevices` evaluates the list,
  * *Then* the 2 newly discovered devices appear at index 0 and 1, ahead of all other devices, with the most recently discovered sensor at index 0.
* **Criterion 3 (Visual Highlight & Badge Presentation)**:
  * *Given* a `DeviceItem` composed with `device.isNewlyDiscovered == true`,
  * *When* rendered on screen,
  * *Then* the card displays a "NEW" badge chip (`devices_newly_discovered_badge`) and a themed highlight border (`1.5.dp`, `primary.copy(alpha = 0.6f)`).
* **Criterion 4 (Sensor Discovery Feedback Notification & Auto-Scroll)**:
  * *Given* an athlete in `DevicesTabbedScreen` while a new sensor is found,
  * *When* `newDeviceDiscoveredEvent` emits the sensor name,
  * *Then* an in-app Snackbar displays `devices_new_sensor_discovered` with the sensor name, haptic feedback triggers, and the list scrolls to item 0.
* **Criterion 5 (9-Language Localization Parity)**:
  * *Given* all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT),
  * *When* evaluating string keys `devices_newly_discovered_badge` and `devices_new_sensor_discovered`,
  * *Then* all 9 locales contain valid non-empty translations with matching `%1$s` specifiers.
* **Criterion 6 (Preservation of Invariants)**:
  * *Given* existing pairing switches, device configuration clicks, and tab filtering,
  * *When* operating normally,
  * *Then* all interactions continue to behave with zero regressions.

---

## 2. Test Specification (TST-UI-294)

### Test Case 1: GetMergedDevicesUseCase Newly Discovered Flagging (`TST-UI-294.1`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/domain/usecase/GetMergedDevicesUseCaseTest.kt`
* **Preconditions**: Mock `dbRepo.allDevices` contains device ID 100, `serviceRepo.newlyFoundDevicesIds` contains `[100L]`.
* **Action**: Collect merged devices flow.
* **Expected Result**: `DeviceUiData` for device ID 100 has `isNewlyDiscovered == true`. For device ID 101 (not in list), `isNewlyDiscovered == false`.

### Test Case 2: DeviceListViewModel Priority Sorting (`TST-UI-294.2`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/devices/DeviceListViewModelPriorityTest.kt`
* **Preconditions**: Catalog with 3 idle devices, 1 connected device, and 2 newly discovered devices (IDs 10 and 20, where 20 was found after 10).
* **Action**: Evaluate filtered device list.
* **Expected Result**:
  * Index 0: Device 20 (`isNewlyDiscovered == true`, most recent).
  * Index 1: Device 10 (`isNewlyDiscovered == true`).
  * Index 2: Connected device.
  * Indices 3-5: Idle known devices.

### Test Case 3: DeviceItem Visual Badge & Border Contract (`TST-UI-294.3`)
* **Scope**: Compose Contract / AST Layout Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/devices/DeviceItemVisualHighlightTest.kt`
* **Preconditions**: Inspect `DeviceItem.kt`.
* **Action**: Verify conditional check for `device.isNewlyDiscovered`.
* **Expected Result**: Renders "NEW" badge chip referencing `R.string.devices_newly_discovered_badge` and applies `BorderStroke` when `isNewlyDiscovered == true`.

### Test Case 4: Discovery Feedback & Notification Event Flow (`TST-UI-294.4`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/devices/DevicesTabbedViewModelDiscoveryTest.kt`
* **Preconditions**: `DevicesTabbedViewModel` initialized.
* **Action**: Simulate detection of newly found device ID.
* **Expected Result**: Emits sensor name to `newDeviceDiscoveredEvent` SharedFlow for snackbar presentation.

### Test Case 5: 9-Language Localization Audit (`TST-UI-294.5`)
* **Scope**: Localization Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/devices/DeviceDiscoveryLocalizationTest.kt`
* **Preconditions**: All 9 `strings.xml` files in `app/src/main/res/`.
* **Action**: Parse XML and assert keys `devices_newly_discovered_badge` and `devices_new_sensor_discovered`.
* **Expected Result**: Present and non-empty in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`. `%1$s` specifier verified.

### Test Case 6: Clean-Room Regression Suite (`TST-UI-294.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-294.1` | Unit | `GetMergedDevicesUseCase` | `REQ-UI-334` (Clause 1) | Specified |
| `TST-UI-294.2` | Unit | `DeviceListViewModel.getFilteredDevices` | `REQ-UI-334` (Clause 2) | Specified |
| `TST-UI-294.3` | Contract | `DeviceItem.kt` | `REQ-UI-334` (Clause 3) | Specified |
| `TST-UI-294.4` | Unit | `DevicesTabbedViewModel.newDeviceDiscoveredEvent` | `REQ-UI-334` (Clause 4) | Specified |
| `TST-UI-294.5` | Localization | `strings.xml` (9 locales) | `REQ-UI-334` (Clause 5) | Specified |
| `TST-UI-294.6` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` (Clause 6) | Specified |
