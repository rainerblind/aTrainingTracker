# Stage 2: Requirement & Test Specification - ATT-8949

**Ticket**: [ATT-8949](https://rainerblind.atlassian.net/browse/ATT-8949) (`3 SP`, `tier-a`) | **Parent**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_03_Nacht` | **Branch**: `feature/ATT-8949` | **Date**: 2026-11-22  
**Requirement Mapping**: `REQ-CON-049` (`REQ-CON-049.1`–`.5`, `SYSREQ-TRK-001`, `SYSREQ-TRK-002`, `SYSREQ-DEX-007`) | **Test Spec ID**: `TST-CON-049`

---

## 1. Requirement Specification (`REQ-CON-049` `[Planned]`)
* **Attributes**: Priority: `High` | Type: `Functional` | Verification Method: `Unit`
* **Implementation**: `BluetoothConstants.java`, `PolarOfflineCapabilityStore.kt`, `PolarOfflineGattCoordinator.kt`, `PolarPmdStreamCoordinator.kt`, `BTSearchForNewDevicesEngine.java`, `MyBTLEDevice.java`, `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`, `EditDeviceDialog.kt` (`DevicesDatabaseManager.java` untouched)
* **`REQ-CON-049.1` (Negative Non-Polar GATT Persistence)**: Whenever BLE HRM GATT discovery succeeds (`0x180D` present) in `BTLEHeartRateDevice.configureServices` or `BTSearchForNewDevicesEngine`, the system SHALL record `PolarOfflineCapabilityStore.recordGattServiceCapabilities(context, mac, hasPmd, hasPftp)` unconditionally—including `(false, false)` for non-Polar HRMs—so `isConfirmedNonPolarGatt(context, mac)` reliably marks confirmed non-Polar sensors.
* **`REQ-CON-049.2` (Polar-Gated `"Sense"` DIS Classification & Schema-Free Store)**: `PolarOfflineCapabilityStore.classifyModelNumber(modelNumber, hasPolarServices)` MUST reject bare `"Sense"` and `"CardioSense"` when `hasPolarServices == false`, while classifying exact `"Sense"` (case-insensitive) as `PolarDeviceType.VERITY_SENSE` when Polar PMD/PFTP GATT capability is confirmed. `DevicesDatabaseManager.DB_VERSION` MUST remain `14`.
* **`REQ-CON-049.3` (Three-Path Hydration & Pre-Rename Legacy Backfill)**: `PolarOnlineStreamingBridge.sdkCallback.disInformationReceived` SHALL match via `matchesBridgeIdentifier(identifier)`. For pre-`ATT-8717` paired BLE HRMs lacking hardware metadata (`PolarDeviceType == UNKNOWN && !isConfirmedNonPolarGatt`), `DeviceDataRepository` (on load and before saving a rename in `updateDevice`) SHALL backfill a hardware-overridable legacy Polar type + 8-hex ID when the persisted pre-rename identity matches Polar H10/Verity Sense, and SHALL record a hardware-overridable legacy-name-heuristic suppression flag when a non-Polar un-reconnected BLE HRM is renamed. Real GATT/DIS/SDK records MUST always supersede legacy seeds and suppression flags.
* **`REQ-CON-049.4` (Non-Polar Hard Gate, Protocol/UI Stability & Empty-Name Resilience)**:
  * `PolarOfflineGattCoordinator` (`isPolarH10Device`, `isPolarVeritySenseDevice`, `isPolarOfflineCapableDevice`) SHALL short-circuit to `false` whenever `isConfirmedNonPolarGatt(context, mac)` is `true`.
  * Legacy name fallback SHALL execute strictly when `PolarDeviceType == UNKNOWN`, `!isConfirmedNonPolarGatt`, `!isLegacyNameHeuristicSuppressed`, and `manufacturer` is blank or contains `"polar"`.
  * `isPolarVeritySenseDevice` MUST NOT require `name.isNotEmpty()` when `PolarDeviceType == VERITY_SENSE` or `hasOfflineRecordingCapability(context, mac)` is `true`.
  * `EditDeviceDialog` (`PolarOfflineDeviceSection`, `isPolarH10Device`/`isPolarVeritySenseDevice(DeviceUiData, ...)`) SHALL gate Polar options strictly on `Protocol.BLUETOOTH_LE` (excluding ANT+/smartphone HRMs) and evaluate legacy fallback against stable persisted pre-edit identity from `EditDeviceViewModel` rather than live keystroke drafts.
* **`REQ-CON-049.5` (Localization & Offline Behavioral Verification)**: `9-Language Localization: N/A` (`0` new/modified UI strings; `values*/strings*.xml` untouched). All tests MUST run offline (`Rule 5`) with `0` `app/src/main` source reads (`REQ-PRO-049.1`) and behavioral Robolectric Compose `createComposeRule()` UI coverage (`REQ-PRO-132`).
* **Given-When-Then Acceptance Criteria**:
  * *Given* a GATT-confirmed non-Polar BLE HRM (`hasPmd=false, hasPftp=false`), an un-reconnected non-Polar BLE HRM, or an ANT+ HRM, *When* renamed `"Polar H10"` in `EditDeviceDialog` (typing or saved + reopened), *Then* `isPolarH10Device` / `isPolarVeritySenseDevice` return `false` and `0` Polar backup/IMU cards render.
  * *Given* an already-paired pre-`ATT-8717` Polar H10 (`"Polar H10 1C3B2421"`) lacking hardware metadata, *When* renamed `"Brustgurt"` and saved in `EditDeviceDialog`, *Then* legacy backfill preserves `H10` + `1C3B2421`, Polar H10 cards stay visible during and after rename, and subsequent GATT/DIS reconnect supersedes cleanly.
  * *Given* a hardware-confirmed or legacy-capable Polar Verity Sense (incl. DIS `0x2A24="Sense"` with PMD/PFTP) or H10, *When* the device name field is cleared to `""` in `EditDeviceDialog`, *Then* Polar Verity Sense / H10 options remain visible.
### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-049` (`REQ-CON-049.1`–`.5`, `docs/requirements/req_tracking_cockpit_part2.md`), refining `REQ-SET-104.3`, `REQ-SET-108.1`, `REQ-SET-112.1/.2`, `REQ-SET-118.2`.
* **Historical Origin & Commit Trace**: `e7201bdb` (`ATT-7923`, introduced `hasCap && name.isNotEmpty()`), `9f9e5967` (`ATT-8717`, introduced `REQ-CON-049`, `PolarOfflineCapabilityStore` DIS/PMD/PFTP persistence, and `isConfirmedNonPolarGatt`).
* **Root Reason for Existing Formulation**: `ATT-7923` used `name.isNotEmpty()` defensively for empty test stubs; `ATT-8717` gated `recordGattServiceCapabilities` on `if (hasPmd || hasPftp)` and rejected `"Sense"` globally to avoid `"CardioSense"` false positives.
* **Preservation of Core Invariants**: Retains `"CardioSense"` rejection, `Devices.db` v14 (`DevicesDatabaseManager.java` untouched), synchronous schema-free `PolarOfflineCapabilityStore`, `ATT-8690` alias multiplexing, `RR_PRIORITY_ECG` vs. `RR_PRIORITY_PPG`, and hardware supremacy over legacy seeds/suppression.

---

## 2. Test Specification (`TST-CON-049` `[Planned]`)
* **`TST-CON-049.1` (`PolarOfflineCapabilityStoreTest`, `PolarOfflineGattCoordinatorCoexistenceTest`)**: Negative non-Polar GATT `(false, false)` blocks `"Polar H10"`/`"Polar Verity Sense"` name bleed; legacy seed & suppression precedence overridden by real GATT/DIS/SDK; `"Sense"` classified as `VERITY_SENSE` only with confirmed Polar service (unconfirmed `"Sense"` & `"CardioSense"` rejected); non-Polar `manufacturer` blocks legacy fallback; `isPolarVeritySenseDevice` returns `true` for `name = ""` with `VERITY_SENSE` or `offline_rec_`.
* **`TST-CON-049.2` (`BTSearchForNewDevicesEngineTest`, `BTLEHeartRateDeviceTest`)**: Polar HRMs persist PMD/PFTP + DIS `0x2A24`/`0x2A25`; non-Polar HRMs exposing `0x180D` without PMD/PFTP persist `(false, false)` -> `isConfirmedNonPolarGatt == true` and reject rename to `"Polar H10"`.
* **`TST-CON-049.3` (`PolarOnlineStreamingBridgeTest`)**: `disInformationReceived` matches via `matchesBridgeIdentifier` (incl. `activeSdkStreamId`) and classifies DIS `0x2A24="Sense"` as `VERITY_SENSE` (`isVeritySense == true`, `RR_PRIORITY_PPG`) alongside pre-connect alias seeding.
* **`TST-CON-049.4` (`PolarH10OfflineControllerTest`, `PolarVeritySenseOfflineControllerTest`, `EditDevicePolarBackupTest`, `EditDevicePolarLiveImuTest`)**: Pre-rename legacy H10 backfill in `DeviceDataRepository` preserves H10 options & ID after rename; non-Polar rename suppression blocks post-save bleed; ANT+ HRM renamed `"Polar H10"` rejected (`Protocol.BLUETOOTH_LE` gate); behavioral Robolectric Compose `createComposeRule()` UI tests verify all 3 `EditDeviceDialog` flows (`N/A - no UI strings`; `0` `src/main` reads).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-049.1` | Unit / Robolectric | `PolarOfflineCapabilityStore`, `PolarOfflineGattCoordinator` | `REQ-CON-049.1`, `.2`, `.4` | Proposed |
| `TST-CON-049.2` | Unit (Mocked GATT) | `BTSearchForNewDevicesEngine`, `BTLEHeartRateDevice` | `REQ-CON-049.1`, `.3` | Proposed |
| `TST-CON-049.3` | Unit (Mocked SDK) | `PolarOnlineStreamingBridge`, `PolarPmdStreamCoordinator` | `REQ-CON-049.2`, `.3`, `.4` | Proposed |
| `TST-CON-049.4` | Unit / Compose UI (`createComposeRule`) | `DeviceDataRepository`, `EditDeviceViewModel`, `EditDeviceDialog`, Offline Controllers | `REQ-CON-049.3`, `.4`, `.5`, `REQ-PRO-049.1`, `REQ-PRO-132` | Proposed |
