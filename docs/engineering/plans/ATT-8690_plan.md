# Stage 3: Implementation Plan - ATT-8690: Process-Wide Ref-Counted PolarBleApi Singleton & Coordination

**Ticket**: `ATT-8690` (`3 SP`, `severity-critical`, Parent `ATT-5910`) | **Target Release**: `V5.0.0` | **Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-SET-118` (`REQ-SET-118.1`..`.4`) | **Test Mapping**: `TST-SET-120` (`TST-SET-120.1`..`.4`)  
**Branch**: `feature/ATT-8690` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-17

---

## 1. Problem Description & Background
`polar-ble-sdk:8.6.0` `BDBleApiImpl$Companion.getInstance()` enforces exact `Set<PolarBleSdkFeature>` equality (`PolarBleSdkInstanceException` on subsets), `cleanup()` calls `BleDeviceListener.removeAllSessions()` without clearing `instance`, `setApiCallback()` overwrites a single global slot, `extractPolarId()` falls back to MAC while SDK callbacks emit `polarDeviceId`, and `findEnabledPolarDevice()` triggers unworn-sensor scans when another Polar HRM is actively tracking.

## 2. Traceability & Requirements Mapping
| Requirement | Architectural Step(s) | Test Spec | Target Test File(s) |
| :--- | :--- | :--- | :--- |
| `REQ-SET-118.1` | Steps 1, 3, 4 | `TST-SET-120.1` | `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarH10OfflineControllerTest`, `PolarVeritySenseOfflineControllerTest` |
| `REQ-SET-118.2` | Steps 1, 2, 3 | `TST-SET-120.2` | `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest` |
| `REQ-SET-118.3` | Steps 1, 3, 4 | `TST-SET-120.3` | `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest` |
| `REQ-SET-118.4` | Step 5 | `TST-SET-120.4` | `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarH10OfflineControllerTest`, `PolarVeritySenseOfflineControllerTest` |

## 3. System Invariants & Preserved Behavior
1. **Session & Coexistence Invariants**: `getActiveSession(polarId)` returns non-null while a bridge is connected and `null` post-`shutDown()`; `withGattYieldAndPolarApi` preserves active bridge sessions (`ACC` mutual exclusion `REQ-SET-112.3`).
2. **Legacy & Test Seam Invariants**: `< API 33` `BTLEHeartRateDevice` fallback untouched; `apiOverride` / `apiFactory` test seams preserved; `findEnabledPolarDevice()` preserves relaxed `DevicesDatabaseManager` / `null` `DeviceManager` unit tests.
3. **Scope & Boundary Invariants**: Zero `Devices.db` schema changes, zero UI/string edits (`N/A - no UI strings`), zero `ANT-Android-SDKs` edits, zero `src/main` reads in unit tests (`REQ-PRO-049.1`).

## 4. Proposed Architectural Changes (SWE.2)
* **`PolarOfflineGattCoordinator.kt`** (`banalservice/.../polar/`):
  * Centralize `ALL_POLAR_FEATURES` (8-feature superset) and synchronized ref-counted `sharedApi` + `activeLeaseCount` (`acquirePolarApi` / `releasePolarApi`, `acquirePersistentSession` / `releasePersistentSession`, `withGattYieldAndPolarApi`). Never call `api.cleanup()`; invoke `api.shutDown()` ONLY when `activeLeaseCount` reaches `0` (and never on `apiOverride`).
  * Install one global `PolarBleApiCallbackProvider` multiplexer dispatching events to registered listeners by normalized bidirectional `MAC` <-> `8-hex Polar ID` aliases (`registerDeviceAlias`, auto-linked from `PolarDeviceInfo`), replacing per-caller `api.setApiCallback(...)` in `awaitFeaturesViaCallback` and `PolarOnlineStreamingBridge`.
  * Guard `findEnabledPolarDevice()` via `BANALService.getDeviceManager()?.remoteDeviceList`: when any HRM `isReceivingData()`, restrict candidates to active receiving HRMs; otherwise preserve DB lookup.
* **`PolarOnlineStreamingBridge.kt`**: Acquire/release persistent session & callback listener through `PolarOfflineGattCoordinator` (zero direct `defaultImplementation` / `setApiCallback` / `cleanup`), and filter `deviceConnected`/`deviceDisconnected` by target device (`MAC` / `polarId`).
* **`PolarH10OfflineController.kt` & `PolarVeritySenseOfflineController.kt`**: Route `stopAndBackfillWorkout()` and `syncPendingWorkout()` in `PolarH10OfflineController` through `PolarOfflineGattCoordinator.withGattYieldAndPolarApi()` (removing duplicate `defaultImplementation`/`cleanup` blocks); keep `PolarVeritySenseOfflineController` wired via `withGattYieldAndPolarApi`.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-SET-118.1`, `.3`) — Centralized 8-Feature Singleton & Ref-Counting**: Update `PolarOfflineGattCoordinator.kt` with `ALL_POLAR_FEATURES`, synchronized `sharedApi` + `activeLeaseCount`, `acquirePersistentSession`/`releasePersistentSession`, and `withGattYieldAndPolarApi` (preserving `apiOverride`/`apiFactory`, `getActiveSession`, and `resetForTesting`).
2. **Step 2 (`REQ-SET-118.2`) — Multiplexed Callbacks & Bidirectional MAC/8-Hex Matching**: Add `deviceAliases`, `registerDeviceAlias`, `matchesDeviceIdentifier`, and `CopyOnWriteArraySet` callback multiplexer in `PolarOfflineGattCoordinator.kt`; refactor `awaitFeaturesViaCallback()` to register/unregister a transient listener without touching `api.setApiCallback()`.
3. **Step 3 (`REQ-SET-118.1`, `.2`, `.3`) — Wire `PolarOnlineStreamingBridge.kt`**: Replace `PolarBleApiDefaultImpl.defaultImplementation`, `api.setApiCallback(sdkCallback)`, and `api.cleanup()` with coordinator lease & multiplexer APIs; filter `deviceConnected`/`deviceDisconnected` by `matchesDeviceIdentifier`.
4. **Step 4 (`REQ-SET-118.1`, `.3`) — Wire `PolarH10OfflineController.kt` & Verify Verity**: Refactor `stopAndBackfillWorkout()` and `syncPendingWorkout()` to use `PolarOfflineGattCoordinator.withGattYieldAndPolarApi()`, removing inline `defaultImplementation`/`cleanup`; verify `PolarVeritySenseOfflineController.kt` uses the same path.
5. **Step 5 (`REQ-SET-118.4`) — Active-HRM Unworn-Sensor Guard**: Update `PolarOfflineGattCoordinator.findEnabledPolarDevice()` to skip disconnected/unworn Polar sensors when `DeviceManager` has an active receiving HRM while preserving `null` `DeviceManager` / relaxed DB mock compatibility.
6. **Step 6 (`TST-SET-120.1`..`.4`) — Offline Mocked Unit Tests & Docs Sync**: Add mocked unit tests in `PolarOfflineGattCoordinatorCoexistenceTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `PolarH10OfflineControllerTest.kt`, and `PolarVeritySenseOfflineControllerTest.kt`; update `docs/architecture.md` §Polar narrative if needed (`Rule 8.8`); run Tier 1 `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.*"`.

## 6. Verification & Rollback Plan
* **Verification**: Targeted Tier 1 unit tests (`./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.*"`) + `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Isolated on `feature/ATT-8690` (`git reset` / branch discard before Stage 5 squash merge).
