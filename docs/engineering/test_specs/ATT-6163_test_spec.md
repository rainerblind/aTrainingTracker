# Stage 2: Requirement & Test Specification - ATT-6163: Trace software requirements to system requirements

**Ticket**: [ATT-6163](https://rainerblind.atlassian.net/browse/ATT-6163)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-076` (*Bidirectional System-to-Software Requirement Traceability and Governance Verification*)  
**Test Spec ID**: `TST-PRO-076`  
**Branch**: `feature/ATT-6163`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-076)

### 1.1 Problem Statement & Rationale
Under ASPICE SYS.2 BP5 (Ensure consistency and bilateral traceability of system requirements to customer requirements and other work products) and SWE.1 BP5 (Ensure consistency and bilateral traceability of software requirements to system requirements), software requirements must not exist in isolation. Every software requirement SHALL either trace to an authentic system requirement (`SYSREQ-*`) or be formally classified as software-derived with documented rationale.

### 1.2 Functional & Architectural Requirements
1. **Bidirectional System-to-Software Traceability Matrix (`REQ-PRO-076.1`)**:
   - `docs/system/sysreq_live_tracking.md` and `docs/system/sysreq_data_exchange.md` (and unified `docs/system/system_software_traceability.md`) SHALL define explicit bidirectional traceability matrices mapping each declared system requirement (`SYSREQ-TRK-001`..`006` and `SYSREQ-DEX-001`..`006`) to refining software requirements (`REQ-TRK-*`, `REQ-CON-*`, `REQ-FIL-*`, `REQ-FUSION-*`, `REQ-SEN-*`, `REQ-DAT-*`, `REQ-EXP-*`, `REQ-EXT-*`, `REQ-MIG-*`, etc.).
2. **Domain & Prefix Tailoring Rules (`REQ-PRO-076.2`)**:
   - Process governance (`docs/project_governance.md` and `docs/system/system_software_traceability.md`) SHALL define explicit tailoring rules classifying requirement prefix families as software-derived:
     - Purely software-internal UX, Compose layouts, themes, and design system contracts (`UI`, `UNT`).
     - Mathematical calculations, period statistics, zone distributions, and graphs (`PER`, `PERF`, `SPT`).
     - Local preferences, sensor/bike profile options, localization strings, and privacy boundaries (`LOC`, `MON`, `PRI`, `SET`).
     - App milestones, reviews, and store presence (`REV`, `STP`).
     - Engineering process, architecture, security invariants, compiler/build contracts, and testing tooling (`ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST`).
3. **Automated Verification of Cited SYSREQ Existence (`REQ-PRO-076.3`)**:
   - `tools/verify_requirement_governance.py` SHALL verify that every `SYSREQ-*` cited in software requirements or traceability matrices exists as a declared requirement heading in `docs/system/sysreq_*.md`. Any dangling or ungrounded SYSREQ citation SHALL fail verification.
4. **SYSREQ Refinement Coverage Verification (`REQ-PRO-076.4`)**:
   - `tools/verify_requirement_governance.py` SHALL verify that 100% of declared system requirements (`SYSREQ-TRK-001`..`006` and `SYSREQ-DEX-001`..`006`) are refined by at least one software requirement, reporting missing coverage and failing verification if any system requirement is unrefined.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Valid Citation / Tailoring)**:
  * *Given* a software requirement (`REQ-*`),
  * *When* governance verification runs,
  * *Then* it either links to an authentic parent `SYSREQ-*`, contains an explicit `Derived` rationale, or belongs to a tailored software-derived prefix family.
* **Criterion 2 (SYSREQ Coverage)**:
  * *Given* the set of declared system requirements in `docs/system/sysreq_*.md`,
  * *When* governance verification runs,
  * *Then* all declared system requirements have verified coverage by $\ge 1$ software requirement, and any uncovered SYSREQ is reported.
* **Criterion 3 (Dangling SYSREQ Detection)**:
  * *Given* a software requirement citing a non-existent `SYSREQ-XXX`,
  * *When* governance verification runs,
  * *Then* it fails with a descriptive error listing the invalid SYSREQ reference.

### 1.4 System Invariants
- Zero external runtime dependencies; pure Python 3 standard library.
- 100% mathematical parity across living documentation master index tables.
- No regression in existing governance verifier rules or CI test suites.

---

## 2. Test Specification (TST-PRO-076)

### Test Case 1: SYSREQ Citation Integrity Unit Test (`TST-PRO-076.1`)
* **Scope**: Tooling Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Mock requirements citing valid and invalid `SYSREQ-*` identifiers.
* **Action**: Invokes `verify_system_software_traceability()`.
* **Expected Result**: Clean pass for valid citations; failure for dangling `SYSREQ-XXX`.

### Test Case 2: SYSREQ Coverage by Software Requirements (`TST-PRO-076.2`)
* **Scope**: Tooling Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Mock system requirements with full vs. incomplete software requirement coverage.
* **Action**: Asserts coverage report correctly flags unrefined SYSREQs.
* **Expected Result**: Reports missing coverage when a SYSREQ has zero refining REQs; passes when 100% coverage is achieved.

### Test Case 3: Domain & Prefix Tailoring Governance Rule Test (`TST-PRO-076.3`)
* **Scope**: Tooling Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Software requirements with derived prefixes (`UI`, `UNT`, `PRO`, etc.).
* **Action**: Verifies requirements are accepted as software-derived without dangling citation errors.
* **Expected Result**: Derived requirements accepted with documented rationale; unknown prefixes without SYSREQ flagged.

### Test Case 4: CLI Integration & Living Documentation Parity (`TST-PRO-076.4`)
* **Scope**: Integration & Contract Test (`tools/verify_requirement_governance.py`, `AspiceSweAuditContractTest.kt`)
* **Preconditions**: Live repository documentation.
* **Action**: Executes `python3 tools/verify_requirement_governance.py --verify-sys-trace` and `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`.
* **Expected Result**: 100% PASS with 12/12 SYSREQs covered and zero dangling references.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-076.1` | Tooling Unit | `test_verify_requirement_governance.py:test_sysreq_citation_integrity` | `REQ-PRO-076.3` | Specified |
| `TST-PRO-076.2` | Tooling Unit | `test_verify_requirement_governance.py:test_sysreq_coverage` | `REQ-PRO-076.4` | Specified |
| `TST-PRO-076.3` | Tooling Unit | `test_verify_requirement_governance.py:test_sys_tailoring_rules` | `REQ-PRO-076.2` | Specified |
| `TST-PRO-076.4` | Integration / Contract | `verify_requirement_governance.py:verify_system_software_traceability` | `REQ-PRO-076.1`, `REQ-PRO-076.4` | Specified |
