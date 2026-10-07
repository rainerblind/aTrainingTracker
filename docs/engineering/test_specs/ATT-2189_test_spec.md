# Stage 2: Requirement & Test Specification (ATT-2189)

**Ticket**: [ATT-2189](https://atrainingtracker.atlassian.net/browse/ATT-2189)  
**Summary**: [Verbesserung] Relocate pairing buttons to sensor settings and optimize control tracking screen layout  
**Requirement**: `REQ-UI-278`  
**Test Case**: `TST-UI-238`  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-41.1`  
**Target Release**: `V4.9.39`  
**Author**: AI Agent 1 (Implementer)  
**Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-06  

---

## 1. Formal Requirement Specification: REQ-UI-278

### REQ-UI-278: Pairing Triggers Relocation to Sensor Management & Control Tracking Layout Optimization

The system SHALL relocate sensor pairing triggers from the tracking control screen to the sensor management screen ("Meine Sensoren") and optimize the layout geometry of `ControlTrackingScreen.kt` (ATT-2189):

1. **Pairing Triggers Removal from Control Tracking Screen (`ControlTrackingScreen.kt`)**:
   - `ControlTrackingScreen.kt` SHALL remove `PairingButtons` and `DeviceTypeSelectionDialog` from its composition.
   - The primary action button `ControlTrackingButton` (Start / Pause / Stop) and `SportTypeSelector` SHALL remain vertically centered between the upper Information Area (`SearchingArea`, `RemoteDevices`, `LocationCalibrationStatus`) and the bottom boundary via equal-weight spacers (`Modifier.weight(1f)`).
   - `ControlTrackingScreen.kt` SHALL expose a bottom container slot `bottomContent: @Composable () -> Unit = {}` positioned at the bottom of the screen column, reserving the vacated vertical space (~72 dp) to host the Route Selection button/card (`ATT-2458`).

2. **Dedicated Pairing Entry Point in Sensor Management ("Meine Sensoren", `DevicesTabbedScreen.kt`)**:
   - `DevicesTabbedScreen.kt` SHALL provide dedicated user-facing entry points to initiate pairing for both ANT+ and Bluetooth LE sensors across all tabs.
   - The screen SHALL render a Material 3 `FloatingActionButton` anchored to `Alignment.BottomEnd` with `navigationBarsPadding()` and `padding(16.dp)`, styled with `Icons.Default.Add` and content description `@string/devices_pair_sensor`.
   - In addition, an action trigger for pairing SHALL be accessible from the top app bar header.

3. **Streamlined Protocol & Device Type Selection**:
   - Tapping the pairing action SHALL present a Material 3 bottom sheet (`PairingProtocolBottomSheet`) allowing selection between `Protocol.ANT_PLUS` and `Protocol.BLUETOOTH_LE`.
   - Protocol options SHALL display protocol icons, localized names, and reflect system hardware availability (`isAntSupported` via `BANALService.areAllANTServicesInstalled`, `isBluetoothSupported` via `BANALService.isProtocolSupported`).
   - Upon selecting a protocol, the system SHALL present `DeviceTypeSelectionDialog` for that protocol.
   - Upon choosing a device type, the system SHALL execute `tabViewModel.updateFilters(protocol, deviceType)`, animate the pager to Tab 0 (`DeviceFilterType.CONNECTED` / Available), and trigger active scanning via `tabViewModel.startSearching()`.

4. **9-Language Localization Parity**:
   - All newly introduced string keys (`devices_pair_sensor`, `devices_pair_protocol_title`) SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero AAPT2 formatting flaws.

5. **Preserved Invariants**:
   - `ControlTrackingButton` strict precise location gating (`REQ-PRI-004`), permission warning badges, and Material 3 rationale sheet (`REQ-PRI-003`) MUST remain strictly intact.
   - `MainActivityWithNavigation.startPairing` and `DevicesTabbedViewModel.updateFilters` contracts (`REQ-STB-011`) MUST be preserved.
   - Remote device click opening `EditDeviceDialog` (`ControlNavigation.ToEditDevice`) MUST remain intact.

---

## 2. Formal Test Specification: TST-UI-238

### TST-UI-238: Pairing Triggers Relocation to Sensor Management & Control Tracking Layout Optimization Verification

1. **ControlTrackingScreen UI & Layout Contract Tests (`ControlTrackingLayoutContractTest.kt`)**:
   - Verify `ControlTrackingScreen.kt` source and composition do not reference or instantiate `PairingButtons`.
   - Verify `ControlTrackingScreen.kt` provides `bottomContent` slot parameter with default `{}`.
   - Verify Start Tracking button and Sport selector are bounded above and below by `weight(1f)` spacers ensuring vertical center alignment.

2. **DevicesTabbedScreen Pairing Contract Tests (`DevicesTabbedScreenPairingContractTest.kt`)**:
   - Verify `DevicesTabbedScreen.kt` renders a `FloatingActionButton` for sensor pairing.
   - Verify pairing flow invokes `tabViewModel.updateFilters(protocol, deviceType)` upon selection.
   - Verify selecting a device type transitions/scrolls pager to Page 0 (`CONNECTED` / Available).

3. **9-Language Localization Audit (`DevicesPairingLocalizationTest.kt` & `TranslationParityTest.kt`)**:
   - Verify all new string keys (`devices_pair_sensor`, `devices_pair_protocol_title`, etc.) exist and are non-empty across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

4. **Clean-Room Full Suite Regression**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

---

## 3. Acceptance Criteria (Given-When-Then)

- **Scenario 1: ControlTrackingScreen layout optimization**
  - *Given* an athlete navigating to the Control Tracking screen (Page 0 of `TrackingTabsScreen`),
  - *When* the screen renders,
  - *Then* the pairing buttons (ANT+ and Bluetooth LE) SHALL NOT be visible at the bottom of the screen, and the Start Tracking button and Sport selector SHALL remain centered between the Information Area and the bottom boundary.

- **Scenario 2: Dedicated pairing access in Sensor Management**
  - *Given* an athlete in "Meine Sensoren" (`DevicesTabbedScreen`),
  - *When* viewing any tab (Available, Paired, All),
  - *Then* a prominent Floating Action Button with `Icons.Default.Add` and content description "Sensor koppeln" / "Pair Sensor" SHALL be rendered at the bottom-right of the screen above the system navigation bar.

- **Scenario 3: Protocol and Device Type selection flow**
  - *Given* an athlete tapping the pairing FAB in `DevicesTabbedScreen`,
  - *When* tapped,
  - *Then* a bottom sheet SHALL display ANT+ and Bluetooth LE protocol options with their availability state.
  - *When* the athlete chooses Bluetooth LE,
  - *Then* `DeviceTypeSelectionDialog` SHALL present the supported BLE sensor types (Heart Rate, Power, Cadence, Speed, or All).
  - *When* the athlete selects a sensor type (e.g. Heart Rate),
  - *Then* the screen SHALL update its filters to `(Protocol.BLUETOOTH_LE, DeviceType.HEART_RATE)`, animate to the "Available" tab, and initiate device discovery.

- **Scenario 4: 9-Language Localization Parity**
  - *Given* an athlete running the app in any of the 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT),
  - *When* viewing the pairing button and dialogs,
  - *Then* all pairing labels and headers SHALL render in the active locale without untranslated English fallbacks.
