# Stage 2: Requirement & Test Specification - ATT-8731: User-Facing Bluetooth Bonding Hint & Pair Action on V2-Capable Radar V1 Fallback

**Ticket**: [ATT-8731](https://issue-tracker/ATT-8731) | **Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-24_02_Spät` | **Branch**: `feature/ATT-8731`  
**Requirement Mapping**: `REQ-CON-061` | **Test Spec ID**: `TST-CON-061`  
**Author**: AI Agent 1 (Lane 1 Author) | **Date**: 2026-11-24 (Gate 3 Revision 1)

---

## 1. Requirement Specification (`REQ-CON-061`)

### 1.1 Rationale & Attributes
- **System Requirement(s)**: `SYSREQ-TRK-002` (refining `REQ-CON-030.2` / `REQ-CON-030.8`)
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Rationale**: Unbonded V2-capable BLE radars (e.g., RearVue 820) silently fall back to V1 (`6a4e3203`), leaving cyclists unaware why 2D vehicle sizing and passing clearance are unavailable. Exposing single-source-of-truth bonding-required state, a permission-safe pair action, and a non-interrupting UI hint enables cyclists to pair/bond their radar without disrupting active rides or affecting legacy V1 hardware.

### 1.2 Functional & Architectural Requirements
1. *Single-Source-of-Truth V2 Bonding Detection, Lifecycle & Synchronized Receiver Teardown (`REQ-CON-061.1`)*:
   - `RadarThreatState` and `RadarState` SHALL expose trailing `@JvmOverloads` defaults `isV2BondingRequired: Boolean = false` and `v2BondingDeviceAddress: String? = null`; `RadarStateRepository` (under leaf `threatLock`) SHALL be the sole source of truth: `updateThreatLocked()` preserves `sourceStates[BLE]`'s `(isV2BondingRequired, v2BondingDeviceAddress)` across `CONNECTED` BLE updates (preventing 4–8 Hz resurrection after external `ALREADY_BONDED` clears) and auto-clears both + resets the one-shot prompt latch on `DISCONNECTED`/`DISCONNECTED_ERROR`/`CONNECTING` while re-arbitrating `BLE > ANT_PLUS`.
   - `RadarStateRepository` SHALL expose `RadarV2BondingStatus(isRequired, deviceAddress)`, `.distinctUntilChanged()` `observeV2BondingStatus()`, `getV2BondingStatus()`, `setV2BondingRequired(required, deviceAddress)` (re-arbitrating under `threatLock` and resetting the prompt latch on `false`), and `consumeV2BondingPromptIfPending(deviceAddress)`.
   - `BTLERadarDevice` SHALL NOT cache a local `mIsV2BondingRequired` boolean. `configureServices()` SHALL clear prior bonding state at entry, then set `setV2BondingRequiredState(true)` (publishing `mAddress` & lazily registering `ACTION_BOND_STATE_CHANGED` with `RECEIVER_EXPORTED`, guarded for `SDK_INT == 0` under dedicated `mBondReceiverLock` checking `!mIsShutDown`) strictly when `v2Char != null && !isBonded && !isV1Override`, and `false` for `v2Char == null`, bonded V2 radars, or `radar_v1_only_override == true`.
   - `BTLERadarDevice` SHALL clear bonding state AND unregister `mBondStateReceiver` under `mBondReceiverLock` via `LifecycleUtils.safeUnregisterReceiver` upon V2 notification receipt, trimmed case-insensitive matching-MAC `BOND_BONDED` (`handleBondStateChanged`), `unregisterSensors()` (covering `MyBTLEDevice` remote `STATE_DISCONNECTED`), `resetConnectionState()` (`disconnectFromGatt()` & `shutDown()`), and `mWatchdogRunnable` expiry.
2. *Android 12+/13+ Permission-Safe Bond Initiation & Task-Safe Settings Fallback (`REQ-CON-061.2`)*:
   - `RadarBluetoothBondingHelper.requestBondOrOpenSettings()` SHALL verify `BLUETOOTH_CONNECT` on API 31+, resolve `BluetoothDevice` via `getRemoteLeDevice(mac, ADDRESS_TYPE_RANDOM|PUBLIC)` on API 33+ (`MyBTLEDevice.isRandomStaticAddress`) or `getRemoteDevice(mac)` below API 33, call `RadarStateRepository.setV2BondingRequired(false, null)` and return `ALREADY_BONDED` if already `BOND_BONDED`, or call `device.createBond()`.
   - When `BLUETOOTH_CONNECT` is missing, Bluetooth is unavailable/disabled, `SecurityException`/`IllegalArgumentException` occurs, or `createBond()` returns `false`, it MUST launch `Settings.ACTION_BLUETOOTH_SETTINGS` adding `Intent.FLAG_ACTIVITY_NEW_TASK` whenever `context !is Activity` (guarded against `ActivityNotFoundException`).
3. *Inline Guidance Card in `EditDeviceDialog` & Non-Interrupting `READY`-Only Snackbar (`REQ-CON-061.3`)*:
   - `EditDeviceDialog` SHALL display `RadarV2BondingHintCard` for `DeviceType.BIKE_RADAR` (`Protocol.BLUETOOTH_LE`) strictly when `isV2BondingRequired == true` and trimmed case-insensitive MACs match (when both non-blank), using luminance-aware `LightWarning*`/`DarkWarning*` tokens, full-width single-line Pair CTA (`maxLines = 1`), zero emojis, and clear copy stating V2 activates once paired / on next connection.
   - `RadarV2BondingSnackbarEffect(snackbarHostState, isAllowedToShow)` SHALL key `LaunchedEffect(snackbarHostState, isAllowedToShow)`, check `isAllowedToShow` BEFORE calling `consumeV2BondingPromptIfPending(mac)` (dismissing any active snackbar on `READY -> TRACKING` without burning the latch during `TRACKING`/`PAUSED`), and mount in `DevicesTabbedScreen` (`true`) and `TrackingTabsScreen` (`trackingMode == TrackingMode.READY`).
4. *9-Language Localization Parity & Architectural Budgets (`REQ-CON-061.4`)*:
   - All 6 `radar_v2_bonding_*` keys SHALL have 100% parity (zero emojis) across all 9 `app/src/main/res/values*/strings_radar.xml` files; all files MUST remain under `ArchitectureConformanceContractTest` LoC ceilings.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1**: *Given* a BLE radar exposing `6a4e3204` (`!isBonded && !isV1Override`), *When* `configureServices()` runs, *Then* `RadarState.isV2BondingRequired == true`, survives 4–8 Hz `processTargets()`, NEVER resurrects after `setV2BondingRequired(false, null)` (`ALREADY_BONDED`), and clears on trimmed case-insensitive `BOND_BONDED`, V2 packet, `unregisterSensors()` remote disconnect, `shutDown()`, or 3.5 s watchdog; `v2Char == null` and `isV1Override == true` keep it `false`.
* **AC-2**: *Given* `isV2BondingRequired == true`, *When* opening `EditDeviceDialog` for the matching BLE radar, *Then* `RadarV2BondingHintCard` renders and Pair invokes `createBond()` or `ACTION_BLUETOOTH_SETTINGS` (with `FLAG_ACTIVITY_NEW_TASK` for non-`Activity` contexts); non-matching MACs or ANT+ radars hide the card.
* **AC-3**: *Given* `TrackingMode.TRACKING` or `PAUSED`, *When* `isV2BondingRequired` becomes `true`, *Then* zero modal dialogs/snackbars appear and the one-shot latch is NOT consumed; transitioning `READY -> TRACKING` cancels any active snackbar.

---

## 2. Test Specification (`TST-CON-061`)

| Test Case | Target Suite | Procedure & Assertions |
| :--- | :--- | :--- |
| `TST-CON-061.1` | `BTLERadarDeviceTest.kt`, `RadarStateRepositoryTest.kt` | Verify `configureServices()` sets `isV2BondingRequired=true` + MAC strictly on `(v2Char!=null && !isBonded && !isV1Override)`, stays `false` for `v2Char==null`/`BOND_BONDED`/`isV1Override=true`; verify 4–8 Hz `processTargets()` preserves `true` AND does NOT resurrect `true` after external `setV2BondingRequired(false, null)`; verify clear + receiver unregister on trimmed case-insensitive `BOND_BONDED`, V2 packet, `unregisterSensors()` remote disconnect, `shutDown()`, `disconnectFromGatt()`, watchdog, and repeated `configureServices()`; verify `RadarStateRepository` BLE/ANT+ arbitration & latch reset on `DISCONNECTED`/`DISCONNECTED_ERROR`/`CONNECTING`. |
| `TST-CON-061.2` | `RadarBluetoothBondingHelperTest.kt` | Verify API 33+ Random Static vs Public `getRemoteLeDevice`, API <33 `getRemoteDevice`, `createBond()==true` $\to$ `BONDING_INITIATED`, `BOND_BONDED` $\to$ `ALREADY_BONDED` + repo clear, and fallback to `ACTION_BLUETOOTH_SETTINGS` (missing `BLUETOOTH_CONNECT`, `SecurityException`, `createBond()==false`) verifying `FLAG_ACTIVITY_NEW_TASK` is added for non-`Activity` contexts (`applicationContext`) and omitted for `Activity` contexts. |
| `TST-CON-061.3` | `EditDeviceDialogRadarBondingTest.kt` | Verify `shouldShowRadarV2BondingHint` trimmed case-insensitive MAC matrix (BLE match/whitespace/case, mismatch, ANT+, non-radar); render `RadarV2BondingHintCard` in Light/Dark/DE-360dp asserting title, V2-on-next-connection copy, single-line CTA, click callback, and `BONDING_INITIATED` feedback; verify `RadarV2BondingSnackbarEffect` suppresses & preserves latch during `TRACKING`/`PAUSED`, fires once in `READY`, and cancels on `READY -> TRACKING`; capture `Rule 8.5` Light/Dark PNG. |
| `TST-CON-061.4` | `TranslationParityTest.kt`, `ui.theme.ThemeColorCentralizationAuditTest.kt`, `ArchitectureConformanceContractTest.kt` | Verify 9/9 `strings_radar.xml` parity for all 6 `radar_v2_bonding_*` keys (zero emojis), zero hardcoded colors, and LoC ceilings. |

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-CON-061.1` | SSoT `RadarStateRepository` bonding flag, zero resurrection, `BTLERadarDevice` synchronized receiver & `unregisterSensors()`/`shutDown()` teardown | `TST-CON-061.1` | Unit (`BTLERadarDeviceTest`, `RadarStateRepositoryTest`) |
| `REQ-CON-061.2` | Android 12+/13+ permission-safe `createBond()` & task-safe `ACTION_BLUETOOTH_SETTINGS` fallback | `TST-CON-061.2` | Unit (`RadarBluetoothBondingHelperTest`) |
| `REQ-CON-061.3` | `EditDeviceDialog` inline card & `READY`-only non-burning cancellable snackbar | `TST-CON-061.3` | Robolectric Compose UI (`EditDeviceDialogRadarBondingTest`) |
| `REQ-CON-061.4` | 9-language `strings_radar.xml` parity, color tokens & LoC budgets | `TST-CON-061.4` | Contract (`TranslationParityTest`, `ui.theme.ThemeColor*`, `ArchitectureConformance*`) |
