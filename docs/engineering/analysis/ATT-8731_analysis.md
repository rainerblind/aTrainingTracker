# Stage 1 Analysis: ATT-8731 - User-Facing Bluetooth Bonding Hint & Pair Action on V2-Capable Radar V1 Fallback

**Ticket**: [ATT-8731](https://issue-tracker/ATT-8731)  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-24_02_Spät` | **Branch**: `feature/ATT-8731`  
**Author**: AI Agent 1 (Lane 1 Author) | **Date**: 2026-11-24 | **Risk Tier**: `tier-a` (2 SP)

---

## 1. Problem Statement & Motivation
When a V2-capable BLE cycling radar (exposing `UUID_CHARACTERISTIC_RADAR_V2` `6a4e3204`, e.g., Garmin Varia RearVue 820 or `VariaSim`) connects without Android Bluetooth bonding (`BluetoothDevice.BOND_BONDED`), `BTLERadarDevice.configureServices()` logs a `Log.w(...)` and silently falls back to legacy V1 (`6a4e3203`). Athletes receive zero user-facing explanation why 2D V2 telemetry (vehicle sizing, lateral lane offsets, passing clearance, 175 m range) is missing or how to pair/bond the sensor.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Code-Grounded Gap (`BTLERadarDevice.java` L266, L319–349)**: `configureServices()` publishes `RadarConnectionState.CONNECTED` at L266, then checks `v2Char != null`, `isBonded`, and `isV1Override` at L319–349. When `v2Char != null && !isBonded && !isV1Override`, it only calls `Log.w(...)` and `fallbackToV1()`, without publishing bonding-required status to `RadarStateRepository`.
* **Independent Peer Review & Verification** (verified against `bc8e91bf` on `sprint/2026-11-24_02_Spät` & Gate 3 audit `ATT-9688`):
  1. *Single Source of Truth (`RadarStateRepository`)*: `BTLERadarDevice` MUST NOT cache a local `mIsV2BondingRequired` boolean re-pushed on 4–8 Hz `processTargets()` frames (which would resurrect `true` ~125 ms after `RadarBluetoothBondingHelper` clears `ALREADY_BONDED`). Instead, `RadarStateRepository` (under leaf `threatLock`) owns `(isV2BondingRequired, v2BondingDeviceAddress)` on `sourceStates[BLE]`, preserving it across `CONNECTED` BLE updates and auto-clearing it + resetting the one-shot prompt latch on `DISCONNECTED`/`DISCONNECTED_ERROR`/`CONNECTING`.
  2. *Remote Disconnect & `shutDown()` Teardown (`MyBTLEDevice.java` L671–687, L1136)*: Remote GATT disconnect calls `unregisterSensors()` + private `disconnectFromGattInternal(...)` (bypassing 0-arg `BTLERadarDevice.disconnectFromGatt()`), and `shutDown()` invokes 1-arg `disconnectFromGatt(onTeardownComplete)`. Therefore `BTLERadarDevice` MUST clear bonding state & unregister `mBondStateReceiver` (under dedicated `mBondReceiverLock` checking `mIsShutDown`) in `resetConnectionState()`, overridden `unregisterSensors()`, `mWatchdogRunnable`, V2 packet receipt, `BOND_BONDED`, and the start of `configureServices()`.
  3. *Recomposition Churn & Multi-Radar Disambiguation*: UI consumers collect `.distinctUntilChanged()` `observeV2BondingStatus()` (`0 Hz` churn) and match trimmed case-insensitive MACs via `EditDeviceViewModel.getBluetoothMacAddress()`.
  4. *Android 12+/13+ Permission, Random Static LE MAC & Task Flag*: `RadarBluetoothBondingHelper` checks API 31+ `BLUETOOTH_CONNECT`, uses API 33+ `getRemoteLeDevice(mac, ADDRESS_TYPE_RANDOM|PUBLIC)` (`MyBTLEDevice.isRandomStaticAddress`), catches `SecurityException`, and falls back to `Settings.ACTION_BLUETOOTH_SETTINGS` adding `Intent.FLAG_ACTIVITY_NEW_TASK` whenever `context !is Activity`.
  5. *Plain-JVM Safety & Lane 2 Isolation*: `registerReceiver` guards `Build.VERSION.SDK_INT == 0` (`coding_standards.md` §5.9). Edits in `BTLERadarDevice.java` stay around L101–350 & L621–675, disjoint from Lane 2 (`ATT-8996`) at L788 (`0x2A24`).

## 3. Scope Grounding & Non-Goals
* **In-Scope**: (1) `RadarState.kt` / `RadarStateRepository.kt` SSoT `isV2BondingRequired` + `v2BondingDeviceAddress` + `observeV2BondingStatus()`; (2) `BTLERadarDevice.java` strict detection (`v2Char != null && !isBonded && !isV1Override`), synchronized lazy `ACTION_BOND_STATE_CHANGED` clearing, and full teardown across `unregisterSensors()`/`resetConnectionState()`/`mWatchdogRunnable`; (3) `RadarBluetoothBondingHelper.kt`; (4) `RadarV2BondingHintCard.kt` wired into `EditDeviceDialog.kt`; (5) `READY`-only `RadarV2BondingSnackbarEffect.kt` wired into `DevicesTabbedScreen.kt` & `TrackingTabsScreen.kt`; (6) 9-language `values*/strings_radar.xml` parity & tests.
* **Out-of-Scope**: Forcing mid-session GATT disconnect/reconnect after bonding; modifying `MyBTLEDevice.java`; modifying `0x2A24` model-number parsing (`ATT-8996`); DB schema changes.
* **Sizing & Tier Check**: Verified **2 SP** (Fibonacci `{2}`); **`tier-a`** confirmed.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-CON-061` refining `REQ-CON-030` (`REQ-CON-030.2` automatic unbonded V1 fallback, `REQ-CON-030.8` debug V1 override) in `BTLERadarDevice.java`, `RadarState.kt`, `RadarStateRepository.kt`, and `EditDeviceDialog.kt`.
* **Historical Origin & Commit Trace**: `REQ-CON-030` introduced in `ATT-5095` (`524e937d`), refined for RearVue 820 LESC bonding & 1500 ms fallback in `ATT-7542`, and extended with debug-only `radar_v1_only_override` in `ATT-7659`.
* **Root Reason for Existing Formulation**: Silent automatic fallback to V1 (`6a4e3203`) ensures an unbonded RearVue 820 still delivers life-safety approach alerts instead of stalling on encrypted V2 (`6a4e3204`).
* **Preservation of Core Invariants**: Automatic V1 fallback, 3.5 s starvation watchdog, Battery CCCD priority, V1-only hardware silence (`v2Char == null`), and `radar_v1_only_override` suppression remain 100% intact.

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-CON-061` (`.1`–`.4`), refining `REQ-CON-030.2`/`.8`; `SYS.2`/`SYS.3`: zero new RF profiles, UUIDs, or permissions — traces to `SYSREQ-TRK-002`.
* **Affected Architecture Elements**: `RadarState.kt`, `RadarStateRepository.kt`, `BTLERadarDevice.java` (`1,333 -> ~1,385/1,500` LoC), `RadarBluetoothBondingHelper.kt`, `RadarV2BondingHintCard.kt`, `RadarV2BondingSnackbarEffect.kt`, `EditDeviceDialog.kt` (`1,827 -> ~1,842/2,004` LoC), `DevicesTabbedScreen.kt` (`813 -> ~815/1,500` LoC), `TrackingTabsScreen.kt` (`1,456 -> ~1,460/1,500` LoC), 9× `values*/strings_radar.xml`.
* **Verification Impact & Test Surface**: `TST-CON-061` across `BTLERadarDeviceTest.kt`, `RadarStateRepositoryTest.kt`, `RadarBluetoothBondingHelperTest.kt`, `EditDeviceDialogRadarBondingTest.kt`, `TranslationParityTest.kt`, `ui.theme.ThemeColorCentralizationAuditTest.kt`, `ArchitectureConformanceContractTest.kt`.
* **Risk Assessment & Invariants**: Mitigates flag resurrection, receiver leaks across `unregisterSensors()`/`shutDown()`, Android 12+ `SecurityException`, non-`Activity` `FLAG_ACTIVITY_NEW_TASK` crashes, and live-tracking interruptions.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: (1) `v2Char == null` and `radar_v1_only_override == true` NEVER set `isV2BondingRequired`; (2) `TRACKING`/`PAUSED` modes are NEVER interrupted by modal dialogs or snackbars; (3) `EditDeviceDialog` draft isolation (`REQ-UI-2799.2`) and BLE/ANT+ arbitration (`REQ-CON-041`) remain unchanged; (4) zero SQLite schema edits; (5) 9-language parity with zero inline emojis.
* **Risk Rating**: **HIGH (`tier-a`)** — modifies BLE radar GATT/lifecycle teardown (`BTLERadarDevice.java`), shared `RadarStateRepository`, and Android 12+/13+ Bluetooth bonding APIs.
