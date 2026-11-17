# Stage 2: Requirement & Test Specification - ATT-8159: Living Detailed Design State Machines

**Ticket**: [ATT-8159](https://rainerblind.atlassian.net/browse/ATT-8159) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Active Sprint**: `2026-11-17_01_Früh` | **SP**: 3 SP | **Status**: Planned | **Author**: AI Agent 1 | **Date**: 2026-11-17
**Requirement Mapping**: `REQ-PRO-114` (`REQ-PRO-114.1..3`)
**Test Spec Mapping**: `TST-PRO-114` (`TST-PRO-114.1..3`)

---

## 1. Requirement Specification (`REQ-PRO-114`)
* **Attributes**: Priority: `Medium` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **REQ-PRO-114.1 (`Unit Detailed Design Documents & State Machine Models`)**: `docs/architecture/detailed_design/` SHALL maintain `tracker_service.md`, `banal_service.md`, and `my_btle_device.md`, each containing a Mermaid `stateDiagram-v2`, event/guard/transition tables, interface tables (`IF-SWE-*`), threading/concurrency contracts (`mScheduler`, `mDbExecutor`, `mRrFlushExecutor`, `mGattLock`, `mHandler`), error-handling & fault-recovery mechanisms, and traceability to `ATT-7902` characterisation and unit tests.
* **REQ-PRO-114.2 (`SWE.2 Cross-Reference & Stage 3 Living Design Rule`)**: `docs/architecture.md` SHALL link `docs/architecture/detailed_design/`, and `.agents/skills/stage3-impl-plan/SKILL.md` SHALL mandate scheduling a living detailed-design update step whenever touching state handling, transitions, or concurrency of `TrackerService`, `BANALService` / `DeviceManager`, or `MyBTLEDevice`.
* **REQ-PRO-114.3 (`Automated Code-Parity Contract Verification`)**: `DetailedDesignParityContractTest.kt` SHALL parse `docs/architecture/detailed_design/{tracker_service,banal_service,my_btle_device}.md` and verify every documented enum/state constant (`TrackingMode`, `AutoPauseState`, `TrackerService.StartType`, `MyBTLEDevice.State`), lifecycle/concurrency field & method symbol, and cited test class resolves verbatim in `app/src/main/` and `app/src/test/`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-114` refining `REQ-PRO-034`, `REQ-PRO-043`, and `REQ-ARC-008` in `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, `docs/architecture.md`, and `.agents/skills/stage3-impl-plan/SKILL.md`.
* **Historical Origin & Commit Trace**: `053531cb` (`ATT-5431`), `0d437b9c` (`ATT-6727`), `ATT-7902` (`REQ-ARC-008`); extended in Sprint `2026-11-17_01_Früh` via `ATT-8159`.
* **Root Reason for Existing Formulation**: SWE.2 architecture (`docs/architecture.md`, `interfaces.md`) and Golden Master tests (`ATT-7902`) preceded SWE.3 unit dynamic models so behavioral baselines were locked down first.
* **Preservation of Core Invariants**: Preserves all `AspiceSweAuditContractTest` / `ArchitectureConformanceContractTest` checks, keeps `app/src/main/` and `ANT-Android-SDKs` (`0e8cea71`) untouched, and models authentic code reality (no `SearchingState` enum in `BANALService`; 4-state `MyBTLEDevice.State`; composite `TrackerService` state).

---

## 3. Acceptance Criteria (Given-When-Then)
* **AC-1 (`TST-PRO-114.1` — Structural & Mermaid Completeness)**: *Given* `docs/architecture/detailed_design/{tracker_service,banal_service,my_btle_device}.md`, *When* inspected, *Then* each includes `stateDiagram-v2`, transition/interface/threading/error tables, and `ATT-7902` test links.
* **AC-2 (`TST-PRO-114.2` — Code-Parity Verification)**: *Given* `DetailedDesignParityContractTest.kt` under `./gradlew testDebugUnitTest`, *When* comparing documented enums, states, symbols, and test classes to `app/src/main/` and `app/src/test/`, *Then* 100% resolve verbatim with zero drift.
* **AC-3 (`TST-PRO-114.3` — Stage 3 Rule & Governance)**: *Given* updated `docs/architecture.md` and `.agents/skills/stage3-impl-plan/SKILL.md`, *When* audited by `DetailedDesignParityContractTest.kt` and `verify_requirement_governance.py`, *Then* Stage 3 mandates detailed-design updates for the 3 units and governance exits `0`.

---

## 4. Test Specifications & Traceability Matrix (`9-Locale Parity`: N/A - no UI strings)

| Test ID | Scope / Method | Target Class / Symbol & Verification | REQ Link | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-114.1` | Unit / Review | `DetailedDesignParityContractTest.kt`: verifies `docs/architecture/detailed_design/{tracker_service,banal_service,my_btle_device}.md` structure, `stateDiagram-v2`, transition/interface/threading/error tables, and cited tests (`TrackerServiceStateMachineCharacterisationTest`, `TrackerServiceCrashRecoveryCharacterisationTest`, `BANALServiceArbitrationCharacterisationTest`, `BANALServiceLifecycleTest`, `MyBTLEDeviceLifecycleTest`, `MyBTLEDeviceQueueTest`, `MyBTLEDeviceAddressAndSearchTest`) | `REQ-PRO-114.1` | Planned |
| `TST-PRO-114.2` | Unit | `DetailedDesignParityContractTest.kt`: asserts verbatim presence in `TrackerService.java`, `TrackingMode.kt`, `AutoPauseManager.kt`, `BANALService.java`, `DeviceManager.java`, `MyRemoteDevice.java`, `MyBTLEDevice.java` for all documented states (`IDLE`/`READY`/`TRACKING`/`PAUSED`, `AutoPauseState`, `StartType`, `MyBTLEDevice.State`), fields/flags (`mIsStopping`, `mTrackingInterrupted`, `mIsRehydrating`, `mSessionGeneration`, `mDbExecutor`, `mScheduler`, `mRrFlushExecutor`, `cMyRemoteDeviceCurrentlySearchingFor`, `mSearchQueue`, `mMyRemoteDeviceTries`, `mGattQueue`, `mGattLock`), and methods (`pauseGattOperations`, `resumeGattOperations`, `startScanAssistance`, `readNextCharacteristic`, `configureServices`) | `REQ-PRO-114.3` | Planned |
| `TST-PRO-114.3` | Unit / Review | `DetailedDesignParityContractTest.kt` + `verify_requirement_governance.py`: verifies `docs/architecture.md` cross-reference, `.agents/skills/stage3-impl-plan/SKILL.md` living detailed-design maintenance rule, and 100% shard/index parity | `REQ-PRO-114.2` | Planned |
