# Stage 1 Analysis: ATT-8949 - Polar Detection (ATT-8717): Non-Polar Bleed, Renamed Existing H10 Loss & Empty-Name Verity Bug

**Ticket**: `ATT-8949` (`3 SP`, `tier-a`, Parent `ATT-5910`) | **Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_03_Nacht` | **Branch**: `feature/ATT-8949` | **Author**: AI Agent 1 | **Date**: 2026-11-22

## 1. Problem Statement & Motivation
On Pixel 10 (`5.0.0-dev-19d61134`) during Sprint Review of `ATT-8717` (`REQ-CON-049`), three Polar detection defects surfaced in `EditDeviceDialog`:
1. **Non-Polar HR bleed**: Renaming a non-Polar HR sensor to `"Polar H10"` falsely exposes Polar H10 backup recording and Live-IMU controls.
2. **Renamed existing H10 loss**: Renaming an already-paired Polar H10 hides its Polar H10 options in `EditDeviceDialog`.
3. **Empty-name Verity Sense bug**: Clearing the device name field to `""` hides Polar Verity Sense options, whereas any non-empty name keeps them visible.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Cause 1 — Non-Polar GATT Confirmation Dead Code & Fallback Bleed**:
  * `BTLEHeartRateDevice.java:254-257` and `BTSearchForNewDevicesEngine.java:146-155,655-661` guard `PolarOfflineCapabilityStore.recordGattServiceCapabilities(...)` with `if (hasPmd || hasPftp)`, so `(false, false)` is **never persisted** for non-Polar BLE HRMs; `isConfirmedNonPolarGatt()` (`PolarOfflineCapabilityStore.kt:134-147`) is dead in production.
  * Even when `isConfirmedNonPolarGatt()` is `true`, `PolarOfflineCapabilityStore.getDeviceType()` (`:266-268`) returns `UNKNOWN`, causing `PolarOfflineGattCoordinator.isPolarH10Device()` (`:919-923`) and `isPolarVeritySenseDevice()` (`:953-960`) to fall straight through to `isPolarH10Name()` / `isPolarVeritySenseName()`.
  * `EditDeviceDialog.kt:220-222,1545-1557` does not gate `PolarOfflineDeviceSection` on `data.protocol == Protocol.BLUETOOTH_LE` (so ANT+ HRMs bleed when renamed `"Polar H10"`) and evaluates live draft `data.deviceName` keystrokes via `isPolarH10Name()` (which ignores non-Polar `manufacturer` such as `"Garmin"`/`"Wahoo"`).
* **Cause 2 — Missing Pre-Rename Legacy Backfill & Live-Draft Evaluation on Existing H10**:
  * Pre-`ATT-8717` paired H10 rows in `Devices.db` lack `PolarOfflineCapabilityStore` entries (`polar_type_`/`model_num_`) until first reconnect. Opening `EditDeviceDialog` and renaming `"Polar H10 XXXXXXXX"` mutates `data.deviceName` live (`EditDeviceViewModel.kt:326-328`) and overwrites `Devices.db.NAME` (`DeviceDataRepository.kt:530-532`) without backfilling the H10 identity first, permanently losing H10 classification until a future BLE reconnect.
* **Cause 3 — Explicit `name.isNotEmpty()` Gate & Bare `"Sense"` DIS Rejection**:
  * `PolarOfflineGattCoordinator.kt:959` returns `isKnownModel || (hasCap && name.isNotEmpty())`: for legacy-paired Verity Sense rows with `offline_rec_<MAC>=true` (`ATT-7923`) and `PolarDeviceType.UNKNOWN`, any non-empty string passes `hasCap && name.isNotEmpty()` while clearing the field to `""` immediately returns `false`.
  * `PolarOfflineCapabilityStore.classifyModelNumber()` (`:300-310`) rejects bare `"Sense"` even when Polar PMD/PFTP GATT capability is confirmed (`hasPolarServiceCapability == true`), leaving real Verity Sense units whose DIS `0x2A24` is `"Sense"` stuck in `UNKNOWN` + legacy `offline_rec_` fallback.
* **Independent Peer Review & Verification (Live `ac4c954a` Codebase Audit)**:
  * **Verified**: All 3 on-device symptoms map 1:1 to verified defects across `PolarOfflineCapabilityStore.kt`, `PolarOfflineGattCoordinator.kt`, `BTLEHeartRateDevice.java`, `BTSearchForNewDevicesEngine.java`, `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`, and `DeviceDataRepository.kt`.
  * **Additional Blind Spots Found**: (1) ANT+ HRMs (`Protocol.ANT`) pass `polarMac = null` into `isPolarH10Device` and bleed on rename; (2) `PolarOnlineStreamingBridge.kt:795-811` checks only `polarId`/`macAddress` in `disInformationReceived` instead of `matchesBridgeIdentifier(identifier)`; (3) `DevicesDatabaseManager.java` requires **zero** edits (`DB_VERSION = 14` untouched, zero conflict with Lane 1 `ATT-6036`).

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals (`3 SP`, `tier-a` Confirmed)**:
  * Persist negative non-Polar GATT service results `(false, false)` on confirmed HRM discovery and short-circuit `isConfirmedNonPolarGatt` before all name heuristics.
  * Gate Polar UI/classification on `Protocol.BLUETOOTH_LE`, reject explicitly non-Polar `manufacturer` in legacy name fallback, evaluate `EditDeviceDialog` against stable persisted identity (not live draft keystrokes), and suppress name-heuristic bleed when renaming an un-reconnected non-Polar HRM.
  * Backfill legacy Polar type + 8-hex ID for pre-`ATT-8717` paired Polar H10/Verity Sense rows before rename (and on repository load) while allowing hardware GATT/DIS/SDK truth to supersede legacy seeds.
  * Remove `&& name.isNotEmpty()` in `isPolarVeritySenseDevice`, classify `"Sense"` as `VERITY_SENSE` when Polar GATT service capability is confirmed, and align `PolarOnlineStreamingBridge.disInformationReceived` identifier matching.
* **Out-of-Scope Non-Goals**: `Devices.db` schema changes (`DevicesDatabaseManager.java` untouched), Polar SDK upgrades, UI copy / `strings*.xml` changes, physical hardware sign-off (`Rule 7.6`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-049` (`REQ-CON-049.1`–`.5`, `docs/requirements/req_tracking_cockpit_part2.md:2887-2930`), refining `REQ-SET-104.3`, `REQ-SET-108.1`, `REQ-SET-112.1/.2`, `REQ-SET-118.2`.
* **Historical Origin & Commit Trace**: `e7201bdb` (`ATT-7923`, introduced `hasCap && name.isNotEmpty()` and `offline_rec_`), `9f9e5967` (`ATT-8717`, introduced `REQ-CON-049`, `PolarOfflineCapabilityStore` DIS/PMD/PFTP persistence, and `isConfirmedNonPolarGatt`).
* **Root Reason for Existing Formulation**: `ATT-7923` used `name.isNotEmpty()` defensively for empty test stubs; `ATT-8717` gated `recordGattServiceCapabilities` on `if (hasPmd || hasPftp)` and rejected `"Sense"` globally to avoid `"CardioSense"` false positives.
* **Preservation of Core Invariants**: Retains `CardioSense` non-Polar rejection (both via `hasPolarServiceCapability` gate and exact-token check), `Devices.db` `DB_VERSION = 14`, synchronous schema-free `PolarOfflineCapabilityStore`, `ATT-8690` alias multiplexing, `RR_PRIORITY_ECG` vs. `RR_PRIORITY_PPG` (`DeviceManager.java:525-547`), and `0` `src/main` reads in tests.

## Change Impact Analysis
* **Affected Requirements**: Refines `REQ-CON-049` (`REQ-CON-049.1`, `.2`, `.3`, `.4`) for non-Polar negative GATT confirmation, `Protocol.BLUETOOTH_LE` + stable-identity UI classification, pre-rename legacy backfill, empty-name resilience, and Polar-service-gated `"Sense"` DIS model classification. **`SYS.2`/`SYS.3` Impact Check (`REQ-PRO-131.4`)**: Existing `SYSREQ-TRK-001`, `SYSREQ-TRK-002`, `SYSREQ-DEX-007` and `SYS-EL-01`/`SYS-EL-02`/`SYS-IF-01` allocations remain valid with no new external interfaces or protocols.
* **Affected Architecture Elements**: `PolarOfflineCapabilityStore.kt`, `PolarOfflineGattCoordinator.kt`, `BTLEHeartRateDevice.java`, `BTSearchForNewDevicesEngine.java`, `PolarOnlineStreamingBridge.kt`, `PolarPmdStreamCoordinator.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`, `EditDeviceDialog.kt` (`DevicesDatabaseManager.java` untouched).
* **Verification Impact & Test Surface**: `TST-CON-049`; extend `PolarOfflineCapabilityStoreTest`, `BTLEHeartRateDeviceTest`, `BTSearchForNewDevicesEngineTest`, `PolarOnlineStreamingBridgeTest`, `PolarVeritySenseOfflineControllerTest`, `EditDevicePolarBackupTest`, `EditDevicePolarLiveImuTest` (incl. behavioral Robolectric Compose `createComposeRule()` UI tests for `EditDeviceDialog` rename/clear flows; `N/A - no UI strings`).
* **Risk Assessment & Invariants**: **HIGH** (`tier-a`, BLE GATT discovery, sensor classification, offline backup & live IMU routing). Mitigated by strict precedence (`Confirmed Non-Polar GATT` > `Hardware DIS/SDK Type` > `Backfilled/Persisted Legacy Type` > `Protocol/Mfg-Guarded Name Fallback`), never fabricating GATT flags for non-connected devices, and preserving `Devices.db` v14 & `ATT-8690` invariants.

## 6. Architectural Strategy & High-Level Solution
1. **Negative GATT Confirmation**: Record `recordGattServiceCapabilities(ctx, mac, hasPmd, hasPftp)` whenever HRM service (`0x180D`) discovery succeeds in `BTLEHeartRateDevice.configureServices` and `BTSearchForNewDevicesEngine`, and short-circuit `isConfirmedNonPolarGatt(ctx, mac) -> false` at the top of `isPolarH10Device`, `isPolarVeritySenseDevice`, and `isPolarOfflineCapableDevice`.
2. **Authoritative vs. Legacy Precedence & Backfill**: Add `seedLegacyDeviceTypeIfUnknown` and `suppressLegacyNameHeuristicIfUnknown` (both strictly overridable by real GATT/DIS/SDK records) in `PolarOfflineCapabilityStore`; backfill pre-`ATT-8717` paired BLE HRMs in `DeviceDataRepository` (on load and prior to rename in `updateDevice`) and suppress legacy name fallback when a non-Polar un-reconnected BLE HRM is renamed; accept exact `"Sense"` DIS `0x2A24` when Polar PMD/PFTP service capability is confirmed.
3. **Stable `EditDeviceDialog` Classification & Empty-Name Resilience**: Remove `&& name.isNotEmpty()` from `isPolarVeritySenseDevice`; gate `PolarOfflineDeviceSection` and `isPolarH10Device`/`isPolarVeritySenseDevice(DeviceUiData, ...)` on `Protocol.BLUETOOTH_LE`; reject non-blank non-Polar `manufacturer` in legacy name fallback; expose stable initial persisted device identity from `EditDeviceViewModel` so typing `"Polar H10"` or clearing the name field in `EditDeviceDialog` never mutates hardware eligibility.

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) Real GATT/DIS/SDK hardware evidence always supersedes legacy seeds/suppression; (2) `Devices.db` `DB_VERSION = 14` & `DevicesDatabaseManager.java` untouched; (3) `ATT-8690` singleton/alias multiplexing & `RR_PRIORITY_ECG`/`PPG` intact; (4) `EditDeviceDialog` Compose UI tested behaviorally (`createComposeRule()`) with zero `src/main` reads.
* **Risk Rating**: **HIGH** (`tier-a`) — Modifies BLE GATT capability persistence, Polar sensor routing across controllers/bridge/repository, and `EditDeviceDialog` state.
