# Stage 2: Requirement & Test Specification - ATT-8690

**Ticket**: [ATT-8690](https://rainerblind.atlassian.net/browse/ATT-8690) | **Parent**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Sprint**: `2026-11-17_03_Nacht`  
**Branch**: `feature/ATT-8690` | **Requirement Mapping**: `REQ-SET-118` | **Test Spec ID**: `TST-SET-120` | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-SET-118`)
- **Attributes**: Priority: `Critical` | Type: `Functional` | Verification Method: `Unit` | Status: `[Planned]`
- **Rationale**: `polar-ble-sdk` enforces an exact-feature-set singleton in `BDBleApiImpl` (`PolarBleSdkInstanceException` on mismatched subsets) and clears all active sessions in `api.cleanup()`. Centralizing a single 8-feature ref-counted singleton with callback multiplexing and active-HRM gating eliminates singleton collisions, callback clobbering, and sibling teardown (`REQ-SET-118` refines `REQ-SET-104.4`, `108.2`, `109.1`, `110.1`, `112.2`/`.3`, `REQ-CON-045.2`/`.4`).
- **Requirements**:
  1. **`REQ-SET-118.1` (8-Feature Superset Singleton)**: `PolarOfflineGattCoordinator` SHALL manage one process-wide ref-counted `PolarBleApi` singleton initialized with all 8 required Polar features (`FEATURE_HR`, `FEATURE_POLAR_ONLINE_STREAMING`, `FEATURE_BATTERY_INFO`, `FEATURE_DEVICE_INFO`, `FEATURE_POLAR_DEVICE_TIME_SETUP`, `FEATURE_POLAR_H10_EXERCISE_RECORDING`, `FEATURE_POLAR_OFFLINE_RECORDING`, `FEATURE_POLAR_TRAINING_DATA`). `PolarOnlineStreamingBridge`, `PolarH10OfflineController` (`stopAndBackfillWorkout` & `syncPendingWorkout`), and `PolarVeritySenseOfflineController` MUST acquire `PolarBleApi` via the coordinator with zero duplicate or subset `defaultImplementation()` calls, preserving `apiOverride`/`apiFactory` test seams.
  2. **`REQ-SET-118.2` (Multiplexed Callbacks & MAC/8-Hex Matching)**: `PolarOfflineGattCoordinator` SHALL install one global callback multiplexer routing SDK events to listeners matching normalized `MAC` <-> `8-hex Polar ID` bidirectionally without `api.setApiCallback()` clobbering across sibling controllers, live bridges, or concurrent `awaitFeaturesReady` calls. `PolarOnlineStreamingBridge.sdkCallback` MUST filter `deviceConnected` and `deviceDisconnected` events by target device.
  3. **`REQ-SET-118.3` (Ref-Counted Lifecycle)**: `PolarOfflineGattCoordinator` MUST NOT invoke `api.cleanup()` or `api.shutDown()` while sibling controllers or bridges hold active references (`refCount > 0`), preserving `getActiveSession(polarId)` non-null while connected and `null` post-`shutDown()`.
  4. **`REQ-SET-118.4` (Active-Device / Unworn-Sensor Gating)**: `findEnabledPolarDevice()` SHALL skip unworn/disconnected Polar offline arming when another Polar HRM is actively receiving data (`DeviceManager`), while preserving compatibility with existing unit tests using relaxed `DevicesDatabaseManager` / `null` `DeviceManager` mocks.

### Acceptance Criteria (Given-When-Then) & Invariants
- *Given* paired Polar H10 + Verity Sense, *When* streaming and offline arming/backfill/sync run concurrently or sequentially, *Then* a single 8-feature `PolarBleApi` is shared with zero `PolarBleSdkInstanceException` and zero subset `defaultImplementation()` calls.
- *Given* registered listeners (`MAC` vs `8-hex Polar ID`) or concurrent `awaitFeaturesReady` callers, *When* SDK callbacks arrive, *Then* only matching listeners receive events without clobbering sibling callbacks or mutating unrelated `PolarOnlineStreamingBridge` state.
- *Given* `refCount > 0` across active bridges/controllers, *When* one holder completes or releases, *Then* `api.cleanup()`/`shutDown()` is NOT invoked.
- *Given* an active receiving Polar HRM in `DeviceManager`, *When* `findEnabledPolarDevice()` checks an unworn secondary sensor, *Then* it skips arming; *Given* `DeviceManager == null` or relaxed DB mocks, *Then* existing test device resolution passes unchanged.
- **Invariants & Chesterton's Fence**: Net-new `REQ-SET-118` preserves `REQ-SET-104`..`112`/`REQ-CON-045`, `ACC` mutual exclusion (`REQ-SET-112.3`), `BTLEHeartRateDevice` `< API 33` fallback, `withGattYieldAndPolarApi` bridge session preservation, and relaxed DB test mocks. **Localization**: `N/A - no UI strings`.

---

## 2. Test Specification (`TST-SET-120`, Offline Mocked `Rule 5`)
- **`TST-SET-120.1` (8-Feature Superset Singleton)**: Assert via mocked `PolarBleApi` factory (zero real hardware/network) that `PolarOfflineGattCoordinator`, `PolarOnlineStreamingBridge`, `PolarH10OfflineController` (`stopAndBackfillWorkout`/`syncPendingWorkout`), and `PolarVeritySenseOfflineController` share one 8-feature singleton with zero duplicate/subset `defaultImplementation()` calls.
- **`TST-SET-120.2` (Callback Multiplexing & MAC/8-Hex Filtering)**: Assert concurrent `awaitFeaturesReady` + sibling bridges multiplex via one `setApiCallback` without clobbering, match `MAC` <-> `8-hex Polar ID` bidirectionally, and ignore foreign-device `deviceConnected`/`deviceDisconnected` callbacks.
- **`TST-SET-120.3` (Ref-Counted Lifecycle)**: Assert `api.cleanup()`/`shutDown()` are never invoked while `refCount > 0`, and `getActiveSession(polarId)` remains non-null while connected and `null` post-`shutDown()`.
- **`TST-SET-120.4` (Unworn-Sensor Gating & Mock Compatibility)**: Assert `findEnabledPolarDevice()` skips unworn Polar sensors when another Polar HRM is receiving in `DeviceManager`, while preserving existing resolution when `DeviceManager` is `null` or DB mocks are relaxed.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-120.1` | Unit (Mocked) | `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarH10OfflineControllerTest`, `PolarVeritySenseOfflineControllerTest` | `REQ-SET-118.1` | Proposed |
| `TST-SET-120.2` | Unit (Mocked) | `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest` | `REQ-SET-118.2` | Proposed |
| `TST-SET-120.3` | Unit (Mocked) | `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest` | `REQ-SET-118.3` | Proposed |
| `TST-SET-120.4` | Unit (Mocked) | `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarH10OfflineControllerTest`, `PolarVeritySenseOfflineControllerTest` | `REQ-SET-118.4` | Proposed |
