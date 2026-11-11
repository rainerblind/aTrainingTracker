# Stage 2: Requirement & Test Specification - ATT-6734: Expand ArchitectureConformanceContractTest to physiology/ and banalservice/ui/, strip comments, and guard database/ upward imports

**Ticket**: [ATT-6734](https://rainerblind.atlassian.net/browse/ATT-6734)  
**Sub-task**: [ATT-6874](https://rainerblind.atlassian.net/browse/ATT-6874) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-PRO-050` (*Automated Architecture Conformance Testing*)  
**Test Spec ID**: `TST-PRO-050`  
**Branch**: `feature/ATT-6734`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-050)

### 1.1 Problem Statement & Rationale
`ArchitectureConformanceContractTest.kt` previously omitted `physiology/` from JVM purity checks and `banalservice/ui/` from UI DB isolation. It also scanned unstripped comments (risking false positives), lacked downward baseline ratcheting enforcement, and left `database/` unguarded against upward Compose and UI screen imports.

### 1.2 Functional & Architectural Requirements
* `testDomainLayer_pureJvmIsolation()` SHALL scan both `domain/` and `physiology/`, asserting zero Android View/Widget/Compose or DatabaseManager imports.
* `testUiLayer_noUnallowlistedDatabaseManagerReferences()` SHALL strip block and line comments prior to `DB_PATTERN` matching and scan both `trainingtracker/ui/` and `banalservice/ui/`.
* UI database baseline SHALL be ratcheted down to 23 callers (18 in `trainingtracker/ui` + 5 in `banalservice/ui`), enforcing `(baseline - detectedCallers).isEmpty()` and `detectedCallers.size <= 23`.
* A new test `testDatabaseLayer_noUpwardUiOrComposeImports()` SHALL assert that `database/` packages contain zero `androidx.compose.*` or UI screen imports.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Domain & UI Expansion with Comment Stripping)**:
  * *Given* `domain/`, `physiology/`, `trainingtracker/ui/`, and `banalservice/ui/`,
  * *When* `ArchitectureConformanceContractTest` runs,
  * *Then* comments are stripped, physiology engines remain pure JVM, and UI callers match the 23-caller ratcheted baseline with zero unallowlisted references.
* **Criterion 2 (Database Layer Upward Import Guard)**:
  * *Given* all `database/` packages,
  * *When* inspected by contract testing,
  * *Then* zero classes import `androidx.compose.*` or UI screens.

### 1.4 System Invariants
* All 23 legitimate legacy UI callers continue functioning without behavioral modification.
* 100% unit test pass rate; zero regressions on `ArchitectureConformanceContractTest`.
* Master requirement and test tables maintain 100% mathematical parity.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-050` (*Automated Architecture Conformance Testing*), refining `REQ-PRO-034` (*SWE.1/2/4/6 Hardening*). Targets `ArchitectureConformanceContractTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.1 under ATT-6182; refined in Sprint 2026-46.2 under ATT-6734.
* **Root Reason for Existing Formulation**: ATT-6182 provisionally scoped checks to `domain/` and `trainingtracker/ui/` with a static count of 24. It did not strip comments or guard `database/` against upward imports. ATT-6734 expands coverage to `physiology/` and `banalservice/ui/`, ratchets the baseline down to 23, strips comments, and enforces downward ratcheting.
* **Preservation of Core Invariants**: The ratcheted 23-caller baseline accurately preserves legitimate legacy callers while preventing architectural regression.

---

## 2. Test Specification (TST-PRO-050)

### Test Case 1: `testDomainLayer_pureJvmIsolation` (`TST-PRO-050.2`)
* **Scope**: Contract Test (`ArchitectureConformanceContractTest.kt`)
* **Action**: Scans `domain/` and `physiology/` asserting pure JVM isolation.
* **Expected Result**: 0 forbidden imports (`android.view`, `android.widget`, `androidx.compose`, `*DatabaseManager`, `*DbHelper`).

### Test Case 2: `testUiLayer_noUnallowlistedDatabaseManagerReferences` (`TST-PRO-050.1`, `TST-PRO-050.3`)
* **Scope**: Contract Test (`ArchitectureConformanceContractTest.kt`)
* **Action**: Strips comments, evaluates `trainingtracker/ui/` and `banalservice/ui/`, and checks baseline downward ratchet.
* **Expected Result**: Exactly 23 detected callers matching baseline; `(baseline - detectedCallers).isEmpty()`.

### Test Case 3: `testDatabaseLayer_noUpwardUiOrComposeImports` (`TST-PRO-050.5`)
* **Scope**: Contract Test (`ArchitectureConformanceContractTest.kt`)
* **Action**: Scans `database/` packages for forbidden `androidx.compose.*` and UI screens.
* **Expected Result**: 0 upward imports.

### Test Case 4: Governance Parity & Tier 1 Targeted Regression Suite (`TST-PRO-050.4`)
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-050.1` | Contract | `testUiLayer_noUnallowlistedDatabaseManagerReferences` | `REQ-PRO-050.1`, `REQ-PRO-050.3` | Specified |
| `TST-PRO-050.2` | Contract | `testDomainLayer_pureJvmIsolation` | `REQ-PRO-050.2` | Specified |
| `TST-PRO-050.3` | Contract | Ratcheting tripwires | `REQ-PRO-050.3` | Specified |
| `TST-PRO-050.5` | Contract | `testDatabaseLayer_noUpwardUiOrComposeImports` | `REQ-PRO-050.5` | Specified |
| `TST-PRO-050.4` | Regression | `ArchitectureConformanceContractTest` | `REQ-PRO-050`, `REQ-PRO-034` | Specified |
