# Stage 3: Implementation Plan - ATT-6180: Specify Software Interfaces in Architecture

**Ticket**: [ATT-6180](https://rainerblind.atlassian.net/browse/ATT-6180)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6180`  
**Requirement Mapping**: `REQ-PRO-044` (`REQ-PRO-044.1`..`REQ-PRO-044.3`)  
**Test Mapping**: `TST-PRO-044` (`TST-PRO-044.1`..`TST-PRO-044.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
`docs/architecture.md` defines high-level subsystem blocks, dynamic Mermaid models, and 9-shard requirement allocations (ASPICE SWE.2 BP1, BP3, BP4). However, the 14 inter-component arrows in the architectural diagram lack formal Software Interface Specifications (SWE.2 BP2). This plan decomposes the creation of formal interface specifications (`IF-SWE-01`..`IF-SWE-08`) in `docs/architecture/interfaces.md`, its summary integration into `docs/architecture.md` Section 5, and automated verification in `AspiceSweAuditContractTest.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-044` (*ASPICE SWE.2 Software Interface Specification*)
  * `REQ-PRO-044.1`: Create `docs/architecture/interfaces.md` specifying all 8 interface families with mandatory attributes.
  * `REQ-PRO-044.2`: Integrate Section 5 Software Interface Specification Matrix into `docs/architecture.md`.
  * `REQ-PRO-044.3`: Automate interface contract verification in `AspiceSweAuditContractTest.kt`.
* **Test Mapping**: `TST-PRO-044` (`TST-PRO-044.1`..`TST-PRO-044.4`)
  * `TST-PRO-044.1`: File existence & interface ID completeness (`IF-SWE-01`..`IF-SWE-08`).
  * `TST-PRO-044.2`: Interface mandatory attributes completeness (Provider, Consumer, Threading, Error Handling).
  * `TST-PRO-044.3`: Architecture master document Section 5 matrix conformance.
  * `TST-PRO-044.4`: Targeted unit test execution (`AspiceSweAuditContractTest`).

---

## 3. System Invariants & Preserved Behavior
1. **ASPICE SWE.2 Invariants**: Existing layer structure, requirement allocations, Mermaid models, and contract tests in `AspiceSweAuditContractTest` remain 100% satisfied.
2. **Zero Runtime Impact**: Zero modifications to production source code in `app/src/main/` or database schemas.

---

## 4. Proposed Architectural Changes
* **New Specification File (`docs/architecture/interfaces.md`)**:
  * Author comprehensive specifications for `IF-SWE-01` through `IF-SWE-08` covering: Provider, Consumer, Operations/Payloads, Threading Rules, and Error Protocols.
* **Architecture Master Document (`docs/architecture.md`)**:
  * Add Section 5 "Software Interface Specifications (SWE.2.BP2)" with summary matrix table and link to `docs/architecture/interfaces.md`.
* **Contract Test Suite Expansion (`AspiceSweAuditContractTest.kt`)**:
  * Implement `@Test fun testArchitectureDocument_softwareInterfaceSpecifications()` verifying interface definitions, mandatory attributes, and Section 5 matrix integration.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Author `docs/architecture/interfaces.md`)**: Create formal interface specifications for `IF-SWE-01`..`IF-SWE-08` (`REQ-PRO-044.1`, `TST-PRO-044.1`, `TST-PRO-044.2`).
2. **Step 2 (Integrate Section 5 in `docs/architecture.md`)**: Add Software Interface Specification Matrix table in `docs/architecture.md` linking to `interfaces.md` (`REQ-PRO-044.2`, `TST-PRO-044.3`).
3. **Step 3 (Expand `AspiceSweAuditContractTest.kt`)**: Add `testArchitectureDocument_softwareInterfaceSpecifications()` asserting existence and structural completeness of all 8 interface contracts (`REQ-PRO-044.3`, `TST-PRO-044.1`, `TST-PRO-044.2`, `TST-PRO-044.3`).
4. **Step 4 (Targeted Test Execution)**: Run targeted test suite:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"
   ```
   Confirm 100% PASS across all audit contract tests (`TST-PRO-044.4`).

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`.
* **Rollback Plan**: Changes are isolated to `feature/ATT-6180`; can be reverted via `git checkout -- .` and `git clean -fd`.
