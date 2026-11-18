# Stage 5: Walkthrough & Verification - ATT-8690: Process-Wide Ref-Counted PolarBleApi Singleton

**Ticket**: `ATT-8690` (`3 SP`, `severity-critical`, Parent `ATT-5910`) | **Target Release**: `V5.0.0` | **Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-SET-118` (`REQ-SET-118.1`..`.4`) | **Test Mapping**: `TST-SET-120` (`TST-SET-120.1`..`.4`)  
**Branch**: `feature/ATT-8690` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-17

---

## 1. Executive Summary & Verification Overview
* Centralized a process-wide ref-counted `PolarBleApi` singleton initialized once with `ALL_POLAR_FEATURES` (8-feature superset) inside `PolarOfflineGattCoordinator`, eliminating `PolarBleSdkInstanceException` and destructive `api.cleanup()` calls across `PolarOnlineStreamingBridge`, `PolarH10OfflineController`, and `PolarVeritySenseOfflineController`.
* Added lock-free `CopyOnWriteArraySet` callback multiplexing with bidirectional `MAC` <-> `8-hex Polar ID` alias matching, device-scoped `PolarOnlineStreamingBridge` event filtering, and active-HRM unworn-sensor gating in `findEnabledPolarDevice()`.
* **UI / Physical Hardware Scope**: `N/A - no UI strings`. Physical Pixel 10 + Polar Verity Sense / H10 hardware tests (`ATT-7924`, `ATT-8000`, `ATT-8001`) are deferred to physical sensor bench / PO acceptance (`Rule 7.6`, `REQ-TST-004`).

---

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method | Result (Offline Mocked) | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-118.1` | `TST-SET-120.1` | Mocked Unit (`PolarOfflineGattCoordinatorCoexistenceTest`, `PolarH10OfflineControllerTest`, `PolarVeritySenseOfflineControllerTest`) | **PASSED** (`80/80 polar.*`) | `Verified` |
| `REQ-SET-118.2` | `TST-SET-120.2` | Mocked Unit (`PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest`) | **PASSED** (`80/80 polar.*`) | `Verified` |
| `REQ-SET-118.3` | `TST-SET-120.3` | Mocked Unit (`PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest`) | **PASSED** (`80/80 polar.*`) | `Verified` |
| `REQ-SET-118.4` | `TST-SET-120.4` | Mocked Unit (`PolarOfflineGattCoordinatorCoexistenceTest`, `PolarH10OfflineControllerTest`, `PolarVeritySenseOfflineControllerTest`) | **PASSED** (`80/80 polar.*`) | `Verified` |

---

## 3. Automated Test Evidence (Tier 1 Targeted — Offline Mocked)
* **Build + Combined Targeted Run** (`python3 tools/parallel_sprint_lock.py ./gradlew :app:assembleDebug :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerService*" --tests "com.atrainingtracker.banalservice.*DeviceManager*" --max-workers=2`):
  * `:app:assembleDebug`: succeeded (`APK` built).
  * `com.atrainingtracker.banalservice.devices.bluetooth_le.polar.*`: **80/80 PASSED** (`PolarVeritySenseOfflineControllerTest`: 24, `PolarH10OfflineControllerTest`: 16, `PolarOnlineStreamingBridgeTest`: 13, `PolarOfflineGattCoordinatorCoexistenceTest`: 5, `PolarLiveRunningCadenceEstimatorTest`: 5, `PolarRawTelemetryRecorderTest`: 5, `PolarH10RunningDynamicsEstimatorTest`: 4, `PolarRawTelemetryPrunerTest`: 4, `PolarRawTelemetryReaderTest`: 3, `WorkoutSamplesHrBackfillTest`: 1).
  * Adjacent `bluetooth_le.*` (`144/144`), `TrackerService*` (`101/101`), `banalservice` misc (`3/3`), `*DeviceManager*` (`15/17` in combined run; `DeviceManagerSearchQueueTest` `2/2` failed in `unmockkAll()` MockK `ConstructorMock.dispose` CME during shared worker teardown and **PASSED 2/2** in isolated re-run, `BUILD SUCCESSFUL in 17s`).
* **Source-Tree Parity (`ATT-8030`)**: `.kt` and `res/` tree unchanged since Gate 4 PASS (`38554d8dc439e6b414d2abded9874fe59af94561`); Stage 5 edited only living docs and this walkthrough.

---

## 4. Verification & Clean-Room Audit
* `python3 tools/verify_requirement_governance.py --sync-indexes`: **PASSED** (100% parity; `Total=1226, Verified=982`; `Total=1233, Verified=1011`).
* `python3 tools/verify_requirement_governance.py --text-file docs/engineering/walkthroughs/ATT-8690_walkthrough.md`: **PASSED**.
* `python3 tools/verify_requirement_governance.py --check-doc-headers --check-commit-hygiene --check-conflict-markers && python3 tools/verify_requirement_governance.py`: **PASSED** (exit `0`).
