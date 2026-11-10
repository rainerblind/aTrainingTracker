# Stage 2: Requirement & Test Specification - ATT-6182: Add Automated Architecture Conformance Tests

**Ticket**: [ATT-6182](https://rainerblind.atlassian.net/browse/ATT-6182)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6182`  
**Requirement Mapping**: `REQ-PRO-050` (*Automated Architecture Conformance Testing*)  
**Test Spec ID**: `TST-PRO-050`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PRO-050)

### 1.1 Problem Statement & Rationale
ASPICE pre-assessment finding C-08 flagged that layering rules in `docs/architecture.md` lack automated CI test enforcement, allowing architectural drift (such as direct UI-to-DatabaseManager bindings) to accumulate unnoticed. Introducing `ArchitectureConformanceAuditTest` enforces layer boundaries and dependency directions (Presentation -> Domain -> Services -> Data -> Persistence) while ratcheting down existing baseline debt and maintaining pure domain logic isolation.

### 1.2 Functional & Technical Requirements
* **REQ-PRO-050.1** (*UI Layer Isolation & DatabaseManager Ban*): Pure Compose UI screens and ViewModels in `ui/` SHALL NOT directly import or reference raw SQLite `*DatabaseManager` or `*DbHelper` classes, except for those explicitly captured in an approved ratcheted baseline set.
* **REQ-PRO-050.2** (*Domain Calculation Layer Purity*): Domain calculators and mathematical calculation engines in `domain/` SHALL NOT import Android UI frameworks (`android.view`, `androidx.compose`, `android.widget`) or database persistence classes, maintaining pure JVM domain isolation.
* **REQ-PRO-050.3** (*Ratcheted Architecture Baseline & Tripwire*): The architecture test suite SHALL isolate existing baseline violations in a recorded baseline set, failing deterministically if any new, unallow-listed layer violation is introduced.
* **REQ-PRO-050.4** (*Architectural Governance Registration*): The architecture conformance test SHALL be registered as a legitimate governance contract test in `SourceTextReadingAuditContractTest` (`LEGITIMATE_GOVERNANCE_GUARDS`) per `docs/coding_standards.md` §5.4.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (UI Layer Isolation)**: *Given* `app/src/main/java/**/ui/`, *When* `ArchitectureConformanceAuditTest` executes, *Then* zero unallow-listed classes SHALL import or reference `*DatabaseManager` / `*DbHelper`.
* **Criterion 2 (Domain Layer Purity)**: *Given* `app/src/main/java/**/domain/`, *When* `ArchitectureConformanceAuditTest` executes, *Then* zero domain calculation engines SHALL reference Android UI or SQLite persistence classes.
* **Criterion 3 (Ratcheting Tripwire)**: *Given* a new, unallow-listed layering violation, *When* `ArchitectureConformanceAuditTest` runs, *Then* the test SHALL fail deterministically.
* **Criterion 4 (Governance Registration & Suite Execution)**: *Given* `ArchitectureConformanceAuditTest`, *When* executed in the unit test suite, *Then* it SHALL pass with 100% success and be registered in `LEGITIMATE_GOVERNANCE_GUARDS`.

---

## 2. Test Specification (TST-PRO-050)

### Test Case 1: UI Layer DatabaseManager Isolation Test (`TST-PRO-050.1`)
* **Scope**: Architectural Audit (`ArchitectureConformanceAuditTest.kt`)
* **Assertion**: Verify UI layer files contain 0 unallow-listed references to `DatabaseManager` / `DbHelper` outside the baseline set.

### Test Case 2: Domain Layer Purity Conformance Test (`TST-PRO-050.2`)
* **Scope**: Architectural Audit (`ArchitectureConformanceAuditTest.kt`)
* **Assertion**: Verify pure domain calculation engines contain 0 imports from `android.view`, `androidx.compose`, or database singletons.

### Test Case 3: Ratcheting Tripwire Violation Detection Test (`TST-PRO-050.3`)
* **Scope**: Unit Test (`ArchitectureConformanceAuditTest.kt`)
* **Assertion**: Verify synthetic layer violations trigger deterministic assertion failures.

### Test Case 4: Governance Registration & Targeted Suite Execution (`TST-PRO-050.4`)
* **Scope**: Contract Test & Targeted Suite (`SourceTextReadingAuditContractTest.kt`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.architecture.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-050.1` | Audit | `ArchitectureConformanceAuditTest.kt` | `REQ-PRO-050.1`, `REQ-PRO-050.3` | Planned |
| `TST-PRO-050.2` | Audit | `ArchitectureConformanceAuditTest.kt` | `REQ-PRO-050.2` | Planned |
| `TST-PRO-050.3` | Unit | `ArchitectureConformanceAuditTest.kt` | `REQ-PRO-050.3` | Planned |
| `TST-PRO-050.4` | Contract | `ArchitectureConformanceAuditTest.kt`, `SourceTextReadingAuditContractTest.kt` | `REQ-PRO-050.4` | Planned |
