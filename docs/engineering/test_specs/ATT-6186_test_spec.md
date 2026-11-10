# Stage 2: Requirement & Test Specification - ATT-6186: Inventory and Phase Out Source-Text-Reading Tests

**Ticket**: [ATT-6186](https://rainerblind.atlassian.net/browse/ATT-6186)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6186`  
**Requirement Mapping**: `REQ-PRO-049` (*Source-Text Reading Test Prohibition & Audit Governance*)  
**Test Spec ID**: `TST-PRO-049`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PRO-049)

### 1.1 Problem Statement & Rationale
ASPICE pre-assessment finding C-12 flagged ~290 test files that read production source files (`.readText()`, `Files.readString()`) to grep for literals or tokens instead of verifying runtime behaviour, violating `docs/coding_standards.md` §5.4. Introducing an automated audit test (`SourceTextReadingAuditTest`) with an allow-list for legitimate architectural guards and a ratcheted baseline for existing legacy tests prevents new violations while preserving build stability.

### 1.2 Functional & Technical Requirements
* **REQ-PRO-049.1** (*Audit Test Implementation*): An automated architectural audit test (`SourceTextReadingAuditTest`) SHALL scan `app/src/test/java/` and detect any test reading `app/src/main/` production source files (`.readText()`, `readLines()`, `Files.readString()`).
* **REQ-PRO-049.2** (*Governance Guard Allow-List*): The audit test SHALL allow-list legitimate system policy and architectural contract tests (`TryWithResourcesCursorAuditTest`, `ManifestSecurityContractTest`, `TranslationParityTest`, `ReleaseCiAndLintConfigContractTest`).
* **REQ-PRO-049.3** (*Legacy Baseline Debt Isolation*): The audit test SHALL isolate 280 legacy source-reading tests in a baseline set, failing deterministically if any new or unallow-listed test reads production code.
* **REQ-PRO-049.4** (*Domain Inventory*): A structured inventory of baseline legacy tests grouped by domain/package SHALL be maintained in living documentation for downstream phase-out.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Audit Scanner)**: *Given* `app/src/test/java/`, *When* `SourceTextReadingAuditTest` executes, *Then* zero unallow-listed tests SHALL read `app/src/main/` source code.
* **Criterion 2 (Governance Guards)**: *Given* approved architectural contract tests, *When* inspected, *Then* they SHALL be present in the audit allow-list.
* **Criterion 3 (Ratcheting Tripwire)**: *Given* a new, unallow-listed test reading `src/main/`, *When* the audit test runs, *Then* the test SHALL fail.
* **Criterion 4 (Regression Verification)**: *Given* the test suite, *When* running `SourceTextReadingAuditTest`, *Then* it SHALL pass with 100% success.

---

## 2. Test Specification (TST-PRO-049)

### Test Case 1: Audit Scanner Execution & Baseline Conformance (`TST-PRO-049.1`)
* **Scope**: Architectural Audit (`SourceTextReadingAuditTest.kt`)
* **Assertion**: Verify scanning `app/src/test/java` yields 0 unallow-listed violations against the recorded 280-file baseline.

### Test Case 2: Governance Guard Allow-List Conformance (`TST-PRO-049.2`)
* **Scope**: Unit Test (`SourceTextReadingAuditTest.kt`)
* **Assertion**: Verify that all 13 legitimate architectural governance guards are explicitly registered and exempted.

### Test Case 3: Ratcheting Tripwire Failure Detection (`TST-PRO-049.3`)
* **Scope**: Unit Test (`SourceTextReadingAuditTest.kt`)
* **Assertion**: Verify the detection logic flags unallow-listed test sources reading `src/main/`.

### Test Case 4: Clean-Room Targeted Test Suite Execution (`TST-PRO-049.4`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.architecture.SourceTextReadingAuditTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-049.1` | Audit | `SourceTextReadingAuditTest.kt` | `REQ-PRO-049.1`, `REQ-PRO-049.3` | Planned |
| `TST-PRO-049.2` | Unit | `SourceTextReadingAuditTest.kt` | `REQ-PRO-049.2` | Planned |
| `TST-PRO-049.3` | Unit | `SourceTextReadingAuditTest.kt` | `REQ-PRO-049.1`, `REQ-PRO-049.3` | Planned |
| `TST-PRO-049.4` | Regression | `SourceTextReadingAuditTest.kt` | `REQ-PRO-049.4` | Planned |
