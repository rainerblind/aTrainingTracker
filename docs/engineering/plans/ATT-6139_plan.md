# Stage 3: Implementation Plan - ATT-6139: Define quantified non-functional requirements

**Ticket**: [ATT-6139](https://rainerblind.atlassian.net/browse/ATT-6139)  
**Sub-task**: [ATT-7428](https://rainerblind.atlassian.net/browse/ATT-7428) (Passed Gate 2)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Requirement Mapping**: `REQ-NFR-001` through `REQ-NFR-018`, `REQ-PRO-052`  
**Test Mapping**: `TST-NFR-001` through `TST-NFR-018`  
**Branch**: `feature/ATT-6139`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
Remediation ID A-12 resolves the ASPICE pre-assessment finding that NFRs in `docs/requirements/req_process_stability.md` (`REQ-NFR-001..014`) exist only as stub headings or `[Planned]` placeholders without numeric target thresholds, test specifications, or concrete measurement procedures. This plan details the formalization of `REQ-NFR-001..018` and `TST-NFR-001..018`, status updates to `[Verified]`, master table synchronization, and implementation of `NonFunctionalRequirementsContractTest.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-NFR-001` through `REQ-NFR-018`, `REQ-PRO-052`
* **Test Specifications**: `TST-NFR-001` through `TST-NFR-018` in `docs/tests/tst_process_stability.md`
* **Contract Test**: `NonFunctionalRequirementsContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Runtime Code Changes**: No modifications to `app/src/main/`; existing services, database managers, and UI components remain untouched.
2. **Architecture Consistency**: All 12 budget entries in `docs/architecture.md` §4.9 remain 100% aligned with `REQ-NFR-003..014`.
3. **Master Index Table Parity**: `python3 tools/verify_requirement_governance.py` must report 100% parity across all 9 shards.

---

## 4. Proposed Changes

### Component 1: `docs/requirements/req_process_stability.md`
* Expand `REQ-NFR-001` through `REQ-NFR-014` with Given-When-Then, numeric target thresholds, concrete measurement procedures, and bidirectional `TST-NFR-*` links.
* Add `REQ-NFR-015` (Cold Start Latency), `REQ-NFR-016` (GNSS Trajectory Accuracy), `REQ-NFR-017` (Crash-Free Session Rate), and `REQ-NFR-018` (ANR Rate Ceiling).
* Update status from `[Planned]` to `[Verified]`.

### Component 2: `docs/tests/tst_process_stability.md`
* Add comprehensive test specifications for `TST-NFR-001` through `TST-NFR-018` detailing verification method, test procedure, expected result, and bidirectional requirement links.
* Update status header count.

### Component 3: Master Index Tables (`docs/requirements.md` & `docs/tests.md`)
* Recalculate and synchronize master index summary tables via `python3 tools/verify_requirement_governance.py --sync-indexes`.

### Component 4: Build Verification Contract Test
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/NonFunctionalRequirementsContractTest.kt`.
* Assert presence, numeric thresholds, measurement methods, and bidirectional test links for all 18 NFRs.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Check Gate 3)**: Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>`.
2. **Step 2 (Update Living Requirements)**: Update `docs/requirements/req_process_stability.md` with quantified specifications for `REQ-NFR-001..018`.
3. **Step 3 (Update Living Test Specs)**: Add `TST-NFR-001..018` to `docs/tests/tst_process_stability.md`.
4. **Step 4 (Sync Master Indexes)**: Run `python3 tools/verify_requirement_governance.py --sync-indexes` and `--check-unique-ids`.
5. **Step 5 (Implement Contract Test)**: Create `NonFunctionalRequirementsContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/build/`.
6. **Step 6 (Targeted Unit Test Execution)**: Run `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: `NonFunctionalRequirementsContractTest` and `ArchitectureConformanceContractTest` pass 100%; `verify_requirement_governance.py` passes 100%.
* **Rollback**: `git checkout docs/requirements/req_process_stability.md docs/tests/tst_process_stability.md docs/requirements.md docs/tests.md app/src/test/java/com/atrainingtracker/trainingtracker/build/NonFunctionalRequirementsContractTest.kt`.
