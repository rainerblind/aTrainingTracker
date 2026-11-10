# Stage 2: Requirement & Test Specification - ATT-6153: Remove stale process artifacts from repository

**Ticket**: [ATT-6153](https://rainerblind.atlassian.net/browse/ATT-6153)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6153`  
**Requirement Mapping**: `REQ-STP-007` (*Stale Process Artifact Elimination & Repository Hygiene Hardening*)  
**Test Spec ID**: `TST-STP-007` (*Stale Process Artifacts Excision & Gitignore Hygiene Contract Test*)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-STP-007)

### 1.1 Problem Statement & Rationale
Legacy agent iterations left ephemeral task/plan artifacts tracked in git (`.active_task.json`, `implementation_plan.artifact.md`, `.artifacts/`). `.gitignore` lacked rules to ignore them, violating ASPICE SUP.8 (Finding B-08). Stale artifacts must be excised, ignored, and verified by contract test while strictly adhering to Rule 67 canonical directory structure.

### 1.2 Functional & Architectural Requirements
1. **Artifact Untracking & Deletion (`REQ-STP-007.1`)**:
   - Stale root artifacts (`.active_task.json`, `implementation_plan.artifact.md`) and `.artifacts/` SHALL be removed via `git rm -r`.
2. **Gitignore Hardening (`REQ-STP-007.2`)**:
   - `.gitignore` MUST include entries for `.active_task.json`, `.artifacts/`, `*.artifact.md`, and `.reviews/`.
3. **Canonical Directory Integrity (`REQ-STP-007.3`)**:
   - No non-canonical directories (e.g. `docs/engineering/archive`) SHALL be created (Rule 67).
4. **Preservation of Invariants**:
   - Zero modifications to production source code in `app/src/main/` or existing unit tests.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Absence of Stale Leftovers)**:
  * *Given* the repository root,
  * *When* inspecting tracked git files and filesystem,
  * *Then* `.active_task.json`, `implementation_plan.artifact.md`, and `.artifacts/` do not exist.
* **Criterion 2 (Gitignore Coverage)**:
  * *Given* `.gitignore`,
  * *When* checked for artifact patterns,
  * *Then* `.active_task.json`, `.artifacts/`, `*.artifact.md`, and `.reviews/` are explicitly ignored.
* **Criterion 3 (Rule 67 Governance)**:
  * *Given* `docs/engineering/`,
  * *When* validated via `tools/verify_requirement_governance.py`,
  * *Then* only the 5 canonical subdirectories, `metrics`, `test_results`, and approved markdown files exist.

### Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-STP-007`). No existing requirements modified or relaxed.

---

## 2. Test Specification (TST-STP-007)

### Test Case 1: Artifact Absence & Gitignore Contract Test (`TST-STP-007.1`)
* **Scope**: Build Contract Test (`AspiceSweAuditContractTest.kt` or `ReleaseCiAndLintConfigContractTest.kt`).
* **Assertions**: Assert `assertFalse` for existence of `.active_task.json`, `implementation_plan.artifact.md`, and `.artifacts/`; assert `.gitignore` contains required ignore lines.

### Test Case 2: Governance & Canonical Directory Audit (`TST-STP-007.2`)
* **Scope**: `tools/verify_requirement_governance.py`.
* **Assertions**: 100% parity across living doc shards and zero unexpected entries in `docs/engineering/`.

### Test Case 3: Tier 1 Build Contract Regression (`TST-STP-007.3`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass across all build and architecture contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-STP-007.1]` | Contract | Assert absence of stale artifacts & presence of gitignore patterns | `REQ-STP-007.1`, `REQ-STP-007.2` | Specified |
| `[TST-STP-007.2]` | Governance | `verify_requirement_governance.py`: Rule 67 canonical purity | `REQ-STP-007.3` | Specified |
| `[TST-STP-007.3]` | Regression | Tier 1 build contract suite (`build.*`) | `REQ-PRO-001` | Specified |
