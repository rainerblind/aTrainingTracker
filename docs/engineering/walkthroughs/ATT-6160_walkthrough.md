# Stage 5: Walkthrough & Verification - ATT-6160: Specify System Requirements for Live Tracking with Sensors

**Ticket**: [ATT-6160](https://rainerblind.atlassian.net/browse/ATT-6160)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*) | **Story Points**: 3 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6160`  
**Requirement Mapping**: `REQ-PRO-054` (`REQ-PRO-054.1`–`REQ-PRO-054.2`) | **Test Mapping**: `TST-PRO-054` (`TST-PRO-054.1`–`TST-PRO-054.3`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Overview
Formally resolved ASPICE 4.0 pre-assessment finding S-02 (SYS.2 BP1-BP3) by specifying authoritative system-level requirements for live endurance tracking with sensors in `docs/system/sysreq_live_tracking.md` and verifying complete code grounding via automated contract testing:
* **System Requirements Specification**: Authored `docs/system/sysreq_live_tracking.md` defining 6 formal system requirements: `SYSREQ-TRK-001` (Multi-Sensor Acquisition & Profile Interoperability), `SYSREQ-TRK-002` (Automatic Reconnection & Exponential Backoff Resilience), `SYSREQ-TRK-003` (Deterministic 1-Hz Technical Sampling Loop & Time-Series Recording), `SYSREQ-TRK-004` (Multi-Source GNSS Acquisition & Position Telemetry), `SYSREQ-TRK-005` (Background Foreground Service Execution & Power Management), and `SYSREQ-TRK-006` (Real-Time Sensory Alerts & Cockpit Feedback) (`REQ-PRO-054.1`).
* **Attributes & Traceability**: Structured each requirement with non-empty Title, formal Statement (`SHALL`/`MUST`), Rationale, Priority, Verification Method, and Traced Software Components.
* **Contract Testing & Code Grounding**: Implemented `SystemLiveTrackingRequirementsContractTest.kt` asserting document existence, structural completeness, and 100% code grounding parity against production constants in `BluetoothConstants`, `MyBTLEDevice` (1,000 ms initial delay, 16,000 ms max backoff), `BTLERadarDevice` (3,500 ms starvation watchdog), and `TrackerService` (1-Hz fixed-rate loop) (`REQ-PRO-054.2`).
* **ATT-6186 Guard Compliance**: Ensured `SystemLiveTrackingRequirementsContractTest` avoids reading production source files, preserving zero regressions in `SourceTextReadingAuditContractTest`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-054.1` | `TST-PRO-054.1` | `SystemLiveTrackingRequirementsContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-054.2` | `TST-PRO-054.2` | `SystemLiveTrackingRequirementsContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-054` | `TST-PRO-054.3` | Targeted Suite `build.*` (Tier 1) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope
* **Executed Scope**: `Tier 1 targeted: com.atrainingtracker.trainingtracker.build.*`
* **Command**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 \
    ./gradlew testDebugUnitTest --max-workers=2 \
    --tests "com.atrainingtracker.trainingtracker.build.*"
  ```
* **Results**:
  - `testDebugUnitTest`: 100% PASS across all contract tests in `com.atrainingtracker.trainingtracker.build.*` (0 failures, 0 regressions).
  - `SystemLiveTrackingRequirementsContractTest`: All 3 test cases passed (`testSysreqLiveTracking_fileExistsAndContainsAllRequirements`, `testSysreqLiveTracking_allRequirementsHaveMandatorySections`, `testSysreqLiveTracking_codeGroundingParity`).
  - `SourceTextReadingAuditContractTest`: All 4 tests passed (zero unallowlisted source readers, 14 approved governance guards).
* **Lock Coordination**: Run under `/tmp/att_build_merge.lock` via `parallel_sprint_lock.py` to prevent concurrent resource contention.

---

## 4. Key Implementation Highlights
* **`docs/system/sysreq_live_tracking.md`**: Authoritative ASPICE SYS.2 baseline specifying end-to-end phone + sensor + GNSS tracking requirements independent of internal software design.
* **`SystemLiveTrackingRequirementsContractTest.kt`**: Automated JUnit 4 contract test verifying structural integrity, mandatory attributes, and live code grounding.

---

## 5. Invariants & Preserved Behavior
1. **Zero Runtime Code Modification**: No modifications to production source code in `app/src/main/` or database schemas.
2. **Deterministic Time-Base & Persistence**: 1-Hz sampling tick, partial WakeLock, and single-threaded SQLite writing remain completely preserved.
3. **Living Doc Parity**: `REQ-PRO-054` and `TST-PRO-054` updated to `[Verified]` with 100% master index parity.
