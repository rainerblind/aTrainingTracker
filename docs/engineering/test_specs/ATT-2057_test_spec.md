# Stage 2: Requirement & Test Specification - ATT-2057: Hide Research Button When No Real Paired Remote Devices Exist in Database

**Ticket**: [ATT-2057](https://rainerblind.atlassian.net/browse/ATT-2057)  
**Sub-task**: [ATT-2233](https://rainerblind.atlassian.net/browse/ATT-2233) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Requirement Mapping**: `REQ-UI-259` (*Conditional Research Button Visibility Based on Real Paired Remote Devices*)  
**Test Spec ID**: `TST-UI-218`  
**Branch**: `feature/ATT-2057`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-UI-259)

### 1.1 Problem Statement & Rationale
On the tracking control screen (`ControlTrackingScreen`), the "Suchen" (`ResearchButton`) action button triggers background discovery and reconnect cycles for previously paired external remote devices (`startSearchingForPairedDevices()`). When no external sensors have ever been paired (or when all remote sensors have been unpaired/removed), only internal pseudo-devices exist in the SQLite database (`Protocol.SMARTPHONE`, e.g., GPS, Network Location, Google Fused Location, Phone Battery, and Barometric Altimeter). Displaying the "Suchen" button in this empty state provides zero utility, misleads the athlete, and clutters the interface. The system SHALL conditionally render the "Suchen" button if and only if at least one real, paired remote device (`protocol` is `ANT_PLUS` or `BLUETOOTH_LE` and `isPaired == true`) exists in the database.

### 1.2 Functional & Architectural Requirements
1. **Database Precondition Check (`DevicesDatabaseManager.java`)**:
   - `DevicesDatabaseManager` SHALL provide a synchronous query method `hasPairedRemoteDevices(): boolean` executing an optimized `LIMIT 1` query on the `Devices` table:
     `SELECT _id FROM Devices WHERE paired > 0 AND (protocol = 'ANT_PLUS' OR protocol = 'BLUETOOTH_LE') LIMIT 1`.
   - The query SHALL return `true` if $\ge 1$ matching device record exists, and `false` otherwise.
2. **Reactive State Pipeline (`ControlTrackingViewModel.kt`)**:
   - `ControlTrackingViewModel` SHALL expose `val hasPairedRemoteDevices: StateFlow<Boolean>`.
   - The `StateFlow` SHALL derive its value by mapping `devicesRepository.allDevices`:
     `devices.any { it.isPaired && (it.protocol == Protocol.ANT_PLUS || it.protocol == Protocol.BLUETOOTH_LE) }`.
   - To eliminate visual layout flicker upon initial screen display, the initial value of `hasPairedRemoteDevices` SHALL be seeded synchronously using `devicesDatabaseManager.hasPairedRemoteDevices()`.
   - When external sensors are paired, unpaired, added, or deleted during an active session, `hasPairedRemoteDevices` SHALL dynamically emit updated boolean values.
3. **UI Conditional Composition (`ControlTrackingScreen.kt` & `TrackingTabsScreen.kt`)**:
   - `ControlTrackingScreen` SHALL accept a boolean parameter `showResearchButton: Boolean = true` (default `true` for backward compatibility with isolated preview environments).
   - The container `Box(modifier = Modifier.align(Alignment.TopStart)) { ResearchButton(...) }` SHALL be encapsulated in `if (showResearchButton)`.
   - In `TrackingTabsScreen.kt`, `ControlTrackingScreen` SHALL be supplied with `showResearchButton = hasPairedRemoteDevices` collected from `controlViewModel.hasPairedRemoteDevices`.
   - The central information column (`SearchArea` and `RemoteDevices`) SHALL preserve its `Alignment.TopCenter` anchor within the `fillMaxWidth()` container, maintaining visual stability with zero horizontal shift regardless of whether the research button is rendered.
4. **Brand Compliance Invariant (AC-3)**:
   - The authentic brand assets for ANT+ (`R.drawable.ant_logo`) and Bluetooth (`R.drawable.logo_protocol_bluetooth`) are protected registered trademarks and SHALL NOT be altered, distorted, or modified under any circumstances.
5. **100% Localization Parity Across 9 Locales**:
   - The string resource `R.string.research` ("Suchen") SHALL maintain 100% localization parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) without missing translations or broken formatting.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Zero Real Paired Remote Devices)**:
  * *Given* the sensor database contains zero paired remote devices (or only internal `Protocol.SMARTPHONE` entries such as GPS, Network, Fused Location, Battery, Pressure),
  * *When* the athlete views `ControlTrackingScreen`,
  * *Then* the "Suchen" (`ResearchButton`) action button SHALL NOT be rendered.
* **AC-2 (At Least One Real Paired Remote Device)**:
  * *Given* at least one real Bluetooth LE or ANT+ sensor device is registered and paired (`isPaired == true`) in the database,
  * *When* the athlete views `ControlTrackingScreen`,
  * *Then* the "Suchen" (`ResearchButton`) action button SHALL be rendered at the top-left, enabling device search and reconnection.
* **AC-3 (Dynamic State Transition During Session)**:
  * *Given* an athlete on `ControlTrackingScreen` with zero paired remote devices (ResearchButton hidden),
  * *When* the athlete pairs a new Bluetooth LE or ANT+ sensor,
  * *Then* `hasPairedRemoteDevices` SHALL emit `true` and the "Suchen" button SHALL dynamically appear without requiring an application restart.
* **AC-4 (Brand Compliance)**:
  * *Given* the display of Bluetooth and ANT+ logos across the application,
  * *Then* all official trademark logos SHALL remain completely unmodified and preserved in authentic visual form.

### 1.4 System Invariants
1. Mathematical center alignment of the central telemetry status and active remote devices list MUST NOT be disrupted by hiding the research button.
2. Full unit test regression suite pass rate (1529+ tests) MUST NOT regress.
3. No database schema version upgrades or table migrations are introduced.
4. Human Decision Gate on parent tickets remains inviolable.

---

## 2. Test Specification (TST-UI-218)

### Test Case 1: `testHasPairedRemoteDevices_whenOnlySmartphoneDevicesExist_emitsFalse` (`TST-UI-218.1`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModelTest.kt`
* **Preconditions**: `DeviceDataRepository.allDevices` contains only `Protocol.SMARTPHONE` devices (GPS, Battery). `DevicesDatabaseManager.hasPairedRemoteDevices()` returns `false`.
* **Action**: Observe `viewModel.hasPairedRemoteDevices`.
* **Expected Result**: `viewModel.hasPairedRemoteDevices.value` is `false`.

### Test Case 2: `testHasPairedRemoteDevices_whenRealPairedDeviceAdded_emitsTrue` (`TST-UI-218.2`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModelTest.kt`
* **Preconditions**: Initial state has no paired remote devices (`false`).
* **Action**: `DeviceDataRepository.allDevices` emits a list containing a paired Bluetooth or ANT+ device (`isPaired = true, protocol = Protocol.BLUETOOTH_LE`).
* **Expected Result**: `viewModel.hasPairedRemoteDevices.value` updates to `true`.

### Test Case 3: `testHasPairedRemoteDevices_whenRealDeviceIsUnpaired_emitsFalse` (`TST-UI-218.3`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModelTest.kt`
* **Preconditions**: A Bluetooth LE device is in the list, but `isPaired = false`.
* **Action**: Observe `viewModel.hasPairedRemoteDevices`.
* **Expected Result**: `viewModel.hasPairedRemoteDevices.value` evaluates to `false`.

### Test Case 4: `testDevicesDatabaseManager_hasPairedRemoteDevices_sqlQueryVerification` (`TST-UI-218.4`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManagerTest.kt`
* **Preconditions**: Mock SQLite database backing `DevicesDatabaseManager`.
* **Action**: Call `devicesDatabaseManager.hasPairedRemoteDevices()` with cursor returning 0 rows vs 1 row.
* **Expected Result**: Returns `false` for 0 rows, `true` for $\ge 1$ rows, closing cursor properly in all paths.

### Test Case 5: 9-Language Localization & Trademark Integrity Audit (`TST-UI-218.5`)
* **Scope**: Static Resource & Localization Parity Test
* **Goal**:
  - Verify that `R.string.research` ("Suchen") exists in all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  - Verify zero modifications to `ant_logo` and `logo_protocol_bluetooth`.
* **Expected Result**: 100% parity across all 9 languages; trademark assets unchanged.

### Test Case 6: Clean-Room Regression Suite (`TST-UI-218.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all unit tests in the repository.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-218.1` | Unit | `ControlTrackingViewModel.hasPairedRemoteDevices` | `REQ-UI-259` (AC-1) | Specified |
| `TST-UI-218.2` | Unit | `ControlTrackingViewModel.hasPairedRemoteDevices` | `REQ-UI-259` (AC-2, AC-3) | Specified |
| `TST-UI-218.3` | Unit | `ControlTrackingViewModel.hasPairedRemoteDevices` | `REQ-UI-259` (AC-1) | Specified |
| `TST-UI-218.4` | Unit | `DevicesDatabaseManager.hasPairedRemoteDevices` | `REQ-UI-259` (1.2.1) | Specified |
| `TST-UI-218.5` | Localization | `strings.xml` in 9 locales & logo drawables | `REQ-UI-259` (AC-4, REQ-UI-106) | Specified |
| `TST-UI-218.6` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
