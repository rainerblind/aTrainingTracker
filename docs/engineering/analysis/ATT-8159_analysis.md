# Stage 1 Analysis: ATT-8159 - Living Detailed Design State Machines

**Ticket**: [ATT-8159](https://issue-tracker/ATT-8159) | **Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)
**Branch**: `feature/ATT-8159` | **Sprint**: `sprint/2026-11-17_01_Früh` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-17

## 1. Problem Statement & Motivation
ASPICE 4.0 Audit Finding `M9` (`SWE.3 BP1/BP2/BP4`): `docs/architecture/` contains `adr_001_java_core_services_migration.md`, `integration_strategy.md`, `interfaces.md`, and `sensor_units.md`, while `docs/architecture.md` §3.1 only models top-level `TrackingMode`. No unit-level living detailed design documents or state machine models exist for `TrackerService`, `BANALService`, and `MyBTLEDevice`, and `.agents/skills/stage3-impl-plan/SKILL.md` only mandates maintaining SWE.2 `docs/architecture.md` without unit-level SWE.3 design updates.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**: Audited live code & tests against ticket hypothesis:
  * **Blind Spots Found**: (1) `BANALService.java` has NO `SearchingState` enum; search state lives in `DeviceManager.java` (`cMyRemoteDeviceCurrentlySearchingFor`, `mSearchQueue`, `mMyRemoteDeviceTries`) and `MyRemoteDevice.mSearching`. (2) `MyBTLEDevice.State` has 4 states (`DISCONNECTED`, `SEARCHING`, `CONNECTED_TO_GATT`, `CONNECTED_WITH_SERVICE`) with no explicit bonded/handshake state (SMP relies on `WRITE_TYPE_DEFAULT`). (3) `TrackerService` state is split across `TrackingMode`, `AutoPauseState`, `StartType`, and volatile lifecycle flags.
  * **Unit 1 — `TrackerService.java` (`3,515` LOC)**:
    * **States/Enums/Flags**: `TrackingMode` (`IDLE`, `READY`, `TRACKING`, `PAUSED`), `AutoPauseState` (`TRACKING`, `PAUSE_DEBOUNCE_PENDING`, `AUTO_PAUSED`, `MANUAL_PAUSED`, `SENSOR_LOST`), `TrackerService.StartType` (`START_NORMAL`, `RESUME_BY_USER`, `RESUME_AUTOMATICALLY`, `RESUME_SERVICE_RECREATION`), flags `mIsStopping`, `mTrackingInterrupted`, `mIsRehydrating`, `mSearching`, `mBanalBound`, `mSessionGeneration`, `mTableInitializationFuture`, `mFinalizationFuture`.
    * **Threading & Errors**: Main Looper (`onCreate`, `onStartCommand`, `handleActionStop`, `runOnMainThread`), `mScheduler` (`ScheduledExecutorService(1)` 1-Hz `tracker`), `mDbExecutor` (single-thread FIFO init/rehydrate/`endWorkout`), `mRrFlushExecutor` (`mLiveRrQueue`). Guards: missing bg-location abort, FGS start catch (`handleTrackingAborted`), `mDiagnosticCollector`, `mSessionGeneration` teardown guard.
    * **Tests**: `TrackerServiceStateMachineCharacterisationTest` (6 tests), `TrackerServiceCrashRecoveryCharacterisationTest` (6 tests), `TrackerServiceIntegrationTest`, `TrackerServiceFinalizationTest`, `TrackerServiceResilienceTest`, `TrackerServiceAsyncInitTest`, `TrackerServiceResumeTest`, `TrackerServiceRrFlushTest`, `TrackerServiceExportGatingTest`.
  * **Unit 2 — `BANALService.java` (`949` LOC) + `DeviceManager.java` (`1,451` LOC)**:
    * **States/Flags**: Service lifecycle (`onCreate` -> `onBind` [`BANALServiceComm`] -> `onDestroy`) manages static singletons (`cServiceContext`, `cSensorManager`, `cDeviceManager`, `cFilterManager`), sport/indoor state (`mHaveUserSelectedSportType`, `mUserSelectedSportTypeId`, `mIsIndoorMode`, `mActivityTypeMax`). Discovery state: `DeviceManager.cMyRemoteDeviceCurrentlySearchingFor`, `mSearchQueue`, `mMyRemoteDeviceTries`, `MyRemoteDevice.mSearching`, broadcasts `SEARCHING_STARTED_FOR_ALL_INTENT`, `SEARCHING_STARTED_FOR_ONE_INTENT`, `SEARCHING_STOPPED_FOR_ONE_INTENT`, `SEARCHING_FINISHED_FOR_ALL_INTENT`.
    * **Threading & Errors**: Main/Binder thread + `BroadcastReceiver`s (`mStartSearchForPairedDevices`, `mNewLapReceiver`, `mSearchingStoppedForOneReceiver`), `MySensorManager`/`ProxySensor` priority arbitration (`getBestSensorData`, `getActivityType`), null-guarded static accessors, `LifecycleUtils.safeUnregisterReceiver`.
    * **Tests**: `BANALServiceArbitrationCharacterisationTest` (5 tests), `BANALServiceLifecycleTest` (3 tests).
  * **Unit 3 — `MyBTLEDevice.java` (`1,177` LOC)**:
    * **States/Enums/Flags**: `MyBTLEDevice.State` (`DISCONNECTED`, `SEARCHING`, `CONNECTED_TO_GATT`, `CONNECTED_WITH_SERVICE`) guarded by `mGattLock`; flags `mIsShutDown`, `mIsGattPaused`, `mIsScanning`, `mHasConnectedInSession`, `mGattOperationPending`.
    * **Transitions/Threading/Errors**: `startSearching()` -> `startScanAssistance()` (quota `MAX_SCANS_IN_WINDOW=4` / `30s`) + `triggerGattConnect()` (`TRANSPORT_LE`, `getRemoteLeDevice` on API 33+); `mGattCallback` -> `discoverServices()` -> `readNextCharacteristic()` -> `configureServices()`; serialized `mGattQueue` (`WriteDescriptorOp`, `ReadCharacteristicOp`, `WriteCharacteristicOp`, `RequestMtuOp`, `GATT_OP_TIMEOUT_MS=2500L`); backoff `mReconnectRunnable` (`1s..16s`), suppressed by `pauseGattOperations()`/`shutDown()`.
    * **Tests**: `MyBTLEDeviceLifecycleTest` (10 tests), `MyBTLEDeviceQueueTest` (8 tests), `MyBTLEDeviceAddressAndSearchTest` (10 tests).

## 3. Scope Grounding & Non-Goals
* **In-Scope**: Create `docs/architecture/detailed_design/` (`tracker_service.md`, `banal_service.md`, `my_btle_device.md`) with Mermaid `stateDiagram-v2`, interface/threading/error tables, and test traceability; add JVM code-parity contract test verifying all documented states/enums/symbols exist in source; update `.agents/skills/stage3-impl-plan/SKILL.md` to require detailed-design updates when modifying these 3 units; add `REQ-PRO-*` / `TST-PRO-*` and `docs/architecture.md` links.
* **Out-of-Scope**: Any production code edits in `app/src/main/` or `ANT-Android-SDKs`.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-034`, `REQ-PRO-043`, `REQ-ARC-008` in `docs/requirements/req_process_stability.md`, `docs/architecture.md`, `.agents/skills/stage3-impl-plan/SKILL.md`.
* **Historical Origin & Commit Trace**: `053531cb` (`ATT-5431` SWE.2 `docs/architecture.md` & Stage 3 rule), `0d437b9c` (`ATT-6727` symbol parity), `ATT-7902` (`REQ-ARC-008` Golden Master characterisation tests).
* **Root Reason for Existing Formulation**: SWE.2 architecture & interface specs were established first; SWE.3 unit state machines waited for `ATT-7902` characterisation scaffolding.
* **Preservation of Core Invariants**: Preserve all existing `AspiceSweAuditContractTest` / `ArchitectureConformanceContractTest` checks, zero `app/src/main/` runtime edits, and `TrackingMode` / `AutoPauseState` / `MyBTLEDevice.State` contracts.

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: New `REQ-PRO-*` (living SWE.3 detailed design & Stage 3 maintenance rule); preserves `REQ-PRO-034`, `REQ-PRO-043`, `REQ-ARC-008`.
* **Affected Architecture Elements**: New `docs/architecture/detailed_design/{tracker_service,banal_service,my_btle_device}.md`, `docs/architecture.md` cross-reference, `.agents/skills/stage3-impl-plan/SKILL.md`.
* **Verification Impact & Test Surface**: New `TST-PRO-*` & code-parity contract test in `app/src/test/.../build/`; existing characterisation & `MyBTLEDevice*Test` / `BANALService*Test` suites referenced and kept 100% green.
* **Risk Assessment & Invariants**: Low risk (docs, skill, and contract test only); parity test must parse real Java/Kotlin declarations cleanly without brittle formatting assumptions.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero edits to `app/src/main/` and `ANT-Android-SDKs`; every documented state/transition/method verified against actual source; `verify_requirement_governance.py` passes cleanly.
* **Risk Rating**: **LOW** — strictly documentation, Stage 3 skill governance, and a JVM code-parity contract test (`3 SP`).
