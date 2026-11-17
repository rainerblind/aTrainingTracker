# Stage 3: Implementation Plan - ATT-8159: Living Detailed Design State Machines

**Ticket**: [ATT-8159](https://rainerblind.atlassian.net/browse/ATT-8159) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `sprint/2026-11-17_01_Früh` | **Branch**: `feature/ATT-8159` | **Author**: AI Agent 1 | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-114` (`REQ-PRO-114.1..3`) | **Test Mapping**: `TST-PRO-114` (`TST-PRO-114.1..3`)

---

## 1. Problem Description & Background
ASPICE 4.0 Audit Finding `M9` (`SWE.3 BP1/BP2/BP4`): no unit-level living detailed-design documents or dynamic state machine models exist for `TrackerService`, `BANALService`, and `MyBTLEDevice`, and `.agents/skills/stage3-impl-plan/SKILL.md` mandates SWE.2 `docs/architecture.md` maintenance without unit-level SWE.3 design updates. Stage 4 authors 3 code-grounded detailed-design specs under `docs/architecture/detailed_design/`, links them from `docs/architecture.md`, updates the Stage 3 skill, and adds `DetailedDesignParityContractTest.kt` to enforce zero drift.

## 2. Traceability & Requirements Mapping
* `REQ-PRO-114.1` (3 detailed-design docs with Mermaid `stateDiagram-v2`, transition/interface/threading/error tables, `ATT-7902` test links) -> `TST-PRO-114.1` (`DetailedDesignParityContractTest.kt`).
* `REQ-PRO-114.2` (`docs/architecture.md` SWE.3 cross-reference & `.agents/skills/stage3-impl-plan/SKILL.md` living design rule) -> `TST-PRO-114.3` (`DetailedDesignParityContractTest.kt`, `verify_requirement_governance.py`).
* `REQ-PRO-114.3` (Automated JVM code-parity verification of enums, states, symbols, and cited tests) -> `TST-PRO-114.2` (`DetailedDesignParityContractTest.kt`).

## 3. System Invariants & Preserved Behavior
1. **Strict Zero Production Impact & Submodule Lock**: Zero edits to `app/src/main/`, `app/src/androidTest/`, `strings*.xml`, or `ANT-Android-SDKs` (`0e8cea714a231fb9565170d4ed01eafa868f9798`).
2. **SWE.2 & Contract Compatibility**: Preserve all existing checks in `AspiceSweAuditContractTest` and `ArchitectureConformanceContractTest` (all symbols in `docs/architecture.md` must resolve in `app/src/main/` or `tools/`).
3. **Authentic Code Grounding**: Model real code constructs only — no invented `SearchingState` enum in `BANALService` (search state lives in `DeviceManager.cMyRemoteDeviceCurrentlySearchingFor` / `mSearchQueue` / `mMyRemoteDeviceTries` / `MyRemoteDevice.mSearching`), 4-state `MyBTLEDevice.State`, and composite `TrackerService` state (`TrackingMode`, `AutoPauseState`, `TrackerService.StartType`, volatile flags).
4. **UI Consistency**: N/A (zero UI/Compose or `strings*.xml` modifications).

## 4. Proposed Architectural Changes (SWE.3 Living Detailed Design)
* **`docs/architecture/detailed_design/tracker_service.md`**: Models `TrackingMode` (`IDLE`, `READY`, `TRACKING`, `PAUSED`), `AutoPauseState` (`TRACKING`, `PAUSE_DEBOUNCE_PENDING`, `AUTO_PAUSED`, `MANUAL_PAUSED`, `SENSOR_LOST`), `TrackerService.StartType`, lifecycle flags (`mIsStopping`, `mTrackingInterrupted`, `mIsRehydrating`, `mSearching`, `mBanalBound`, `mSessionGeneration`), executors (`mScheduler`, `mDbExecutor`, `mRrFlushExecutor`), `IF-SWE-03/05/06` interfaces, error recovery (`mDiagnosticCollector`, `handleTrackingAborted`, `mSessionGeneration` guard), and `ATT-7902` characterisation tests.
* **`docs/architecture/detailed_design/banal_service.md`**: Models `BANALService` lifecycle (`onCreate` -> `onBind` [`BANALServiceComm`] -> `onDestroy`), static managers (`cServiceContext`, `cSensorManager`, `cDeviceManager`, `cFilterManager`), `DeviceManager` sequential search state (`cMyRemoteDeviceCurrentlySearchingFor`, `mSearchQueue`, `mMyRemoteDeviceTries`, `MyRemoteDevice.mSearching`), `MySensorManager`/`ProxySensor` priority arbitration, `IF-SWE-03/04` interfaces, and `BANALServiceArbitrationCharacterisationTest` / `BANALServiceLifecycleTest`.
* **`docs/architecture/detailed_design/my_btle_device.md`**: Models `MyBTLEDevice.State` (`DISCONNECTED`, `SEARCHING`, `CONNECTED_TO_GATT`, `CONNECTED_WITH_SERVICE`), `mGattLock`, scan-assist quota (`MAX_SCANS_IN_WINDOW = 4`), `triggerGattConnect()` (`getRemoteLeDevice`), serialized `mGattQueue` (`WriteDescriptorOp`, `ReadCharacteristicOp`, `WriteCharacteristicOp`, `RequestMtuOp`, `GATT_OP_TIMEOUT_MS = 2500L`), `mReconnectRunnable` (`1s..16s`), `pauseGattOperations()` / `resumeGattOperations()`, and `MyBTLEDevice{Lifecycle,Queue,AddressAndSearch}Test`.
* **Governance & Contract Wiring**: Add SWE.3 cross-reference in `docs/architecture.md`, Stage 3 living detailed-design maintenance rule in `.agents/skills/stage3-impl-plan/SKILL.md`, and `DetailedDesignParityContractTest.kt` under `app/src/test/java/com/atrainingtracker/trainingtracker/build/`.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-PRO-114.1` -> `TST-PRO-114.1`)**: Author `docs/architecture/detailed_design/tracker_service.md`, `banal_service.md`, and `my_btle_device.md` with Mermaid `stateDiagram-v2`, transition/guard tables, interface & threading tables, error-handling protocols, and `ATT-7902`/unit test traceability.
2. **Step 2 (`REQ-PRO-114.2` -> `TST-PRO-114.3`)**: Add SWE.3 detailed-design cross-reference in `docs/architecture.md` (without introducing unresolvable symbols for `AspiceSweAuditContractTest`) and add the living detailed-design maintenance step to `.agents/skills/stage3-impl-plan/SKILL.md`.
3. **Step 3 (`REQ-PRO-114.3` -> `TST-PRO-114.1..3`)**: Author `app/src/test/java/com/atrainingtracker/trainingtracker/build/DetailedDesignParityContractTest.kt` (`// Linked test specs: TST-PRO-114`) verifying structural completeness, verbatim existence of all documented enums, states, fields, methods, and test classes in `app/src/main/` and `app/src/test/`, `docs/architecture.md` cross-reference, and Stage 3 skill mandate.
4. **Step 4 (Verification & Sync)**: Run targeted Gradle tests under `tools/parallel_sprint_lock.py` and `python3 tools/verify_requirement_governance.py`; promote `REQ-PRO-114` / `TST-PRO-114` to `[Verified]` in Stage 5 upon verified execution.

## 6. Verification & Rollback Plan
* **Verification**: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` plus `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Isolated on `feature/ATT-8159` (`git checkout --` / `git reset` cleanly restores prior state).

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: `REQ-PRO-034`, `REQ-PRO-043`, `REQ-ARC-008`, refined by `REQ-PRO-114` (`docs/architecture.md`, `.agents/skills/stage3-impl-plan/SKILL.md`, `docs/requirements/req_process_stability.md`).
2. **Historical Origin & Commit Trace**: `053531cb` (`ATT-5431`), `0d437b9c` (`ATT-6727`), `ATT-7902` (`REQ-ARC-008`), extended via `ATT-8159`.
3. **Root Reason for Existing Formulation**: SWE.2 architecture (`docs/architecture.md`, `interfaces.md`) and `ATT-7902` characterisation tests were created first before SWE.3 unit-level dynamic models.
4. **Preservation of Core Invariants**: Preserves all existing `AspiceSweAuditContractTest` / `ArchitectureConformanceContractTest` invariants, zero `app/src/main/` changes, and `ANT-Android-SDKs` (`0e8cea71`).
