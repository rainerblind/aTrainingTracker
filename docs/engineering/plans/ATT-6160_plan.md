# Stage 3: Implementation Plan - ATT-6160: Specify system requirements for live tracking with sensors

**Ticket**: [ATT-6160](https://rainerblind.atlassian.net/browse/ATT-6160)  
**Sub-task**: [ATT-6635](https://rainerblind.atlassian.net/browse/ATT-6635) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-PRO-054` (*System Requirements for Live Tracking with Sensors*)  
**Test Mapping**: `TST-PRO-054`  
**Branch**: `feature/ATT-6160`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE 4.0 pre-assessment finding S-02 (SYS.2 BP1-BP3) identified that aTrainingTracker lacks formal system requirements (`SYSREQ-*`) defining end-to-end behavior for phone, sensors, and GNSS positioning. While individual software classes exist, black-box system requirements specifying multi-sensor acquisition, exponential backoff reconnection, 1-Hz deterministic sampling, GNSS positioning, foreground execution, power governance, and sensory alerts are missing. Specifying these capabilities in `docs/system/sysreq_live_tracking.md` and verifying code grounding via contract tests closes this ASPICE CL2 gap.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-054` (*System Requirements for Live Tracking with Sensors*)
* **Test Mapping**: `TST-PRO-054` (*System Requirements for Live Tracking Contract Test Suite*)
* **Traceability Matrix**:
  - `REQ-PRO-054.1` -> `TST-PRO-054.1`, `TST-PRO-054.2` (`docs/system/sysreq_live_tracking.md` specification & code grounding)
  - `REQ-PRO-054.2` -> `TST-PRO-054.2`, `TST-PRO-054.3` (`SystemLiveTrackingRequirementsContractTest.kt` targeted unit suite execution)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: No changes to production classes in `app/src/main/` or database schemas.
2. **Deterministic Time-Base**: 1-Hz technical sampling tick and single-thread SQLite serialization remain unaltered.
3. **Power & Lifecycle Contracts**: Partial WakeLock and dynamic Foreground Service permissions remain preserved.
4. **Clean-Room Verification**: 100% test pass rate across targeted contract test suites and requirement index parity.

---

## 4. Proposed Architectural Changes
* **System Requirements Specification (`docs/system/sysreq_live_tracking.md`)**:
  - Author authoritative system-level requirements document under `docs/system/`.
  - Specify 6 formal requirements:
    1. `SYSREQ-TRK-001`: Multi-Sensor Acquisition & Profile Interoperability (BLE GATT + ANT+).
    2. `SYSREQ-TRK-002`: Automatic Reconnection & Exponential Backoff Resilience (1 s initial, 16 s max, 3.5 s radar watchdog).
    3. `SYSREQ-TRK-003`: Deterministic 1-Hz Technical Sampling Loop & Time-Series Recording.
    4. `SYSREQ-TRK-004`: Multi-Source GNSS Acquisition & Position Telemetry (GPS provider + Fused Location).
    5. `SYSREQ-TRK-005`: Background Foreground Service Execution & Power Management (Partial WakeLock, battery exemption).
    6. `SYSREQ-TRK-006`: Real-Time Sensory Alerts & Cockpit Feedback (Compose tiles, radar threat warnings).
  - Include attributes for each: ID, Title, Formal Statement (`SHALL`/`MUST`), Rationale, Priority, Verification Method, Traced Software Components.
* **System Requirements Contract Test (`SystemLiveTrackingRequirementsContractTest.kt`)**:
  - Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`.
  - Assert existence and completeness of `docs/system/sysreq_live_tracking.md`.
  - Assert 1:1 code grounding against constants in `BluetoothConstants.java`, `MyBTLEDevice.java`, `BTLERadarDevice.java`, and `TrackerService.java`.
* **UI Consistency**: No UI changes (system-level documentation & contract test only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Create `docs/system/sysreq_live_tracking.md` (REQ-PRO-054.1)
* Author `docs/system/sysreq_live_tracking.md` defining all 6 `SYSREQ-TRK-*` requirements with required sections and code grounding.

### Step 2: Implement `SystemLiveTrackingRequirementsContractTest.kt` (REQ-PRO-054.2)
* Author `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`.
* Implement `testSysreqLiveTracking_fileExistsAndContainsAllRequirements()`, `testSysreqLiveTracking_allRequirementsHaveMandatorySections()`, and `testSysreqLiveTracking_codeGroundingParity()`.
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest"`

### Step 3: Living Doc Verification & Parity Check (TST-PRO-054.3)
* Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-46.1` to assert 100% living documentation parity.

---

## 6. Verification & Rollback Plan
* **Local Verification**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest"`
* **Rollback Plan**: Branch isolation on `feature/ATT-6160` ensures clean revert with zero impact on `sprint/2026-46.1`.
