# Stage 3: Implementation Plan - ATT-8949: Polar Detection Non-Polar Bleed, Renamed Existing H10 & Empty-Name Fix

**Ticket**: `ATT-8949` (`3 SP`, `tier-a`, Parent `ATT-5910`) | **Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_03_Nacht`  
**Requirement Mapping**: `REQ-CON-049` (`REQ-CON-049.1`–`.5`) | **Test Mapping**: `TST-CON-049` (`TST-CON-049.1`–`.4`)  
**Branch**: `feature/ATT-8949` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-22

---

## 1. Problem Description & Background
Three defects surfaced during Sprint Review of `ATT-8717` (`REQ-CON-049`): (1) renaming a non-Polar HR sensor to `"Polar H10"` exposes Polar H10 backup/IMU options because `(false, false)` GATT capability is never persisted, `isConfirmedNonPolarGatt` is not short-circuited in `PolarOfflineGattCoordinator`, ANT+ HRMs are not gated out, and `EditDeviceDialog` evaluates live draft keystrokes; (2) renaming an already-paired pre-`ATT-8717` Polar H10 loses its options because no pre-rename legacy backfill runs before `Devices.db.NAME` is overwritten; (3) clearing the name to `""` hides Verity Sense options because `PolarOfflineGattCoordinator.kt:959` checks `hasCap && name.isNotEmpty()` and bare `"Sense"` DIS `0x2A24` is rejected even with confirmed Polar GATT services.

## 2. Traceability & Requirements Mapping
| Requirement | Architectural Step(s) | Test Spec | Target Test File(s) |
| :--- | :--- | :--- | :--- |
| `REQ-CON-049.1`, `.2` | Steps 1, 2, 3, 4 | `TST-CON-049.1`, `.2` | `PolarOfflineCapabilityStoreTest`, `PolarOfflineGattCoordinatorCoexistenceTest`, `BTLEHeartRateDeviceTest`, `BTSearchForNewDevicesEngineTest` |
| `REQ-CON-049.3` | Steps 3, 4, 5 | `TST-CON-049.2`, `.3`, `.4` | `BTLEHeartRateDeviceTest`, `BTSearchForNewDevicesEngineTest`, `PolarOnlineStreamingBridgeTest`, `EditDevicePolarLiveImuTest` |
| `REQ-CON-049.4`, `.5` | Steps 2, 5, 6, 7 | `TST-CON-049.1`, `.4` | `PolarOfflineCapabilityStoreTest`, `PolarVeritySenseOfflineControllerTest`, `EditDevicePolarBackupTest`, `EditDevicePolarLiveImuTest` |

## 3. System Invariants, Upgrade Behaviour & Preserved Boundaries
1. **Zero `DevicesDatabaseManager.java` / Schema Changes**: `Devices.db` remains at `DB_VERSION = 14`; `DevicesDatabaseManager.java`, `MyBTLEDevice.java`, `DeviceManager.java`, and `TrackerService.java` are **untouched** (zero Lane 1 `ATT-6036` overlap; zero `detailed_design/` churn).
2. **Hardware Truth Supremacy & Upgrade Parity (`docs/coding_standards.md` §5.6)**: Real BLE GATT `(hasPmd, hasPftp)`, DIS `0x2A24`/`0x2A25`, and Polar SDK features ALWAYS supersede legacy backfill seeds and rename-suppression flags. Existing paired Polar H10/Verity users keep their Polar options seamlessly upon rename without needing prior reconnect; `"CardioSense"` and unconfirmed bare `"Sense"` remain rejected.
3. **ATT-8690, Modality & LoC Budgets**: Preserves `PolarBleApi` singleton/multiplexer, `RR_PRIORITY_ECG` vs. `RR_PRIORITY_PPG`, and file-size ceilings (`PolarOnlineStreamingBridge.kt` shrinks by ~6 LoC to `<= 1,479 / 1,500`; `EditDeviceDialog.kt` stays `<= 1,790 / 2,004`).
4. **UI Consistency (`docs/design_guidelines.md`) & Hygiene**: Zero new widgets, zero layout/color/copy changes, `N/A - no UI strings` (`values*/strings*.xml` untouched); existing `PolarBackupRecordingCard` and `PolarLiveImuCard` inside `AppModalBottomSheet` remain visually identical, verified via behavioral `createComposeRule()` UI tests (`REQ-PRO-132`) and `Rule 8.5` Light/Dark PNG inspection with `0` `app/src/main` reads (`REQ-PRO-049.1`).

## 4. Proposed Architectural Changes (SWE.2)
* **Precedence Model (`PolarOfflineCapabilityStore.kt`, `PolarOfflineGattCoordinator.kt`)**:
  1. `isConfirmedNonPolarGatt(ctx, mac)` (`pmd_svc_ == false && pftp_svc_ == false`) -> **hard veto `false`** before any name heuristic or legacy seed.
  2. Hardware-confirmed `PolarDeviceType` (`polar_type_` / DIS `0x2A24` / SDK feature) -> deterministic `H10` or `VERITY_SENSE`.
  3. Hardware-overridable backfilled legacy type (`legacy_polar_type_`, seeded from pre-`ATT-8717` paired BLE HRM rows before rename/on load).
  4. Legacy `offline_rec_` (`hasOfflineRecordingCapability`) -> `VERITY_SENSE` regardless of `name.isEmpty()` (excluding legacy H10 names).
  5. Legacy name fallback -> strictly when `UNKNOWN`, `!isConfirmedNonPolarGatt`, `!isLegacyNameHeuristicSuppressed`, and `manufacturer` is blank or contains `"polar"`.
* **No Synthetic GATT Flags**: Un-reconnected non-Polar renames set a dedicated `suppress_name_heur_<MAC>` flag (cleared automatically if real GATT/DIS/SDK later confirms Polar), never fabricating `pmd_svc_`/`pftp_svc_` booleans.
* **UI Layer (`EditDeviceViewModel.kt`, `EditDeviceDialog.kt`)**: Expose immutable `persistedDeviceIdentity: StateFlow<DeviceUiData?>` captured on `loadInitialDeviceData` (not mutated by `onDeviceNameChanged`) and gate `PolarOfflineDeviceSection` & `isPolar*Device(DeviceUiData, ...)` on `Protocol.BLUETOOTH_LE`.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction — Post Gate 3 `check-gate`)
1. **Step 1 (`REQ-CON-049.1`, `.2`, `.3`) — `PolarOfflineCapabilityStore.kt`**:
   - Add `legacyTypeCache`/`KEY_LEGACY_TYPE_PREFIX` (`seedLegacyDeviceTypeIfUnknown`), `suppressNameCache`/`KEY_SUPPRESS_NAME_PREFIX` (`suppressLegacyNameHeuristicIfUnknown`, `isLegacyNameHeuristicSuppressed`), and `hasHardwareDeviceType`.
   - Extend `classifyModelNumber(modelNumber, hasPolarServices = false)` (`@JvmOverloads`) so exact `"sense"` returns `VERITY_SENSE` strictly when `hasPolarServices == true`; wire `confirmedPolarService = false` into `recordDisMetadata`; auto-clear `suppress_name_heur_` and re-evaluate cached `modelNumber` when `recordGattServiceCapabilities(..., hasPmd || hasPftp)` records Polar services.
2. **Step 2 (`REQ-CON-049.1`, `.4`) — `PolarOfflineGattCoordinator.kt`**:
   - Short-circuit `isConfirmedNonPolarGatt(context, macAddress) -> false` in `isPolarH10Device`, `isPolarVeritySenseDevice`, and `isPolarOfflineCapableDevice`.
   - Reject explicitly non-Polar `manufacturer` (`isNotBlank() && !contains("polar", true)`) and `isLegacyNameHeuristicSuppressed` in legacy name fallback.
   - Remove `&& name.isNotEmpty()` from `isPolarVeritySenseDevice` (`hasCap` returns `true` for empty names).
3. **Step 3 (`REQ-CON-049.1`, `.3`) — `BTLEHeartRateDevice.java` & `BTSearchForNewDevicesEngine.java`**:
   - Persist `recordGattServiceCapabilities(mContext, addr, hasPmd, hasPftp)` unconditionally whenever HRM service `0x180D` is discovered (`hasPmd || hasPftp || hasHrService`), while keeping DIS `0x2A24`/`0x2A25` reads strictly gated on `hasPmd || hasPftp`.
4. **Step 4 (`REQ-CON-049.2`, `.3`) — `PolarOnlineStreamingBridge.kt` & `PolarPmdStreamCoordinator.kt`**:
   - Use `matchesBridgeIdentifier(identifier)` in both `sdkCallback.disInformationReceived` overloads (`-6` LoC).
   - Pass `confirmedPolarService = true` in `PolarPmdStreamCoordinator.recordDisCharacteristic()` so Polar SDK `"Sense"` DIS `0x2A24` classifies deterministically as `VERITY_SENSE`.
5. **Step 5 (`REQ-CON-049.3`) — `DeviceDataRepository.kt`**:
   - Add `backfillLegacyPolarIdentityIfNeeded(device)` (called during `loadAllDevices()` and before rename in `updateDevice()`): seeds `H10`/`VERITY_SENSE` + 8-hex ID for pre-`ATT-8717` Polar BLE HRMs lacking hardware metadata; if a non-Polar un-reconnected BLE HRM is renamed in `updateDevice()`, calls `suppressLegacyNameHeuristicIfUnknown(application, mac)`.
6. **Step 6 (`REQ-CON-049.4`) — `EditDeviceViewModel.kt` & `EditDeviceDialog.kt`**:
   - Capture `persistedDeviceIdentity` on `loadInitialDeviceData` (triggering `backfillLegacyPolarIdentityIfNeeded`); gate `isPolarH10Device`/`isPolarVeritySenseDevice(DeviceUiData, ...)` and `PolarOfflineDeviceSection` on `Protocol.BLUETOOTH_LE` and evaluate against `persistedDeviceIdentity` (never live keystroke draft `data.deviceName`).
7. **Step 7 (`TST-CON-049.1`–`.4`, `REQ-CON-049.5`) — Offline Unit & Behavioral Compose UI Tests**:
   - Extend `PolarOfflineCapabilityStoreTest`, `PolarOfflineGattCoordinatorCoexistenceTest`, `BTLEHeartRateDeviceTest`, `BTSearchForNewDevicesEngineTest`, `PolarOnlineStreamingBridgeTest`, `PolarVeritySenseOfflineControllerTest`, `EditDevicePolarBackupTest`, and `EditDevicePolarLiveImuTest` (adding behavioral `createComposeRule()` UI tests for all 3 `EditDeviceDialog` flows + `Rule 8.5` Light/Dark PNG verification, `0` `src/main` reads).

## 6. Verification & Rollback Plan
* **Tier 1 Verification**: `./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDeviceTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.search_new.BTSearchForNewDevicesEngineTest" --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.*"` + `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-11-21_03_Nacht`.
* **Rollback**: Isolated on `feature/ATT-8949` with zero DB migrations; clean revert prior to post-Stage-6 squash merge.
