# Stage 2: Requirement & Test Specification - ATT-6152: Configuration Management Plan and Submodule Pinning

**Ticket**: [ATT-6152](https://rainerblind.atlassian.net/browse/ATT-6152)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6152`  
**Requirement Mapping**: `REQ-STP-009` (*Configuration Management Plan & Pinned Submodule Baseline*)  
**Test Spec ID**: `TST-STP-009` (*CM Plan Integrity & Submodule Pinning Contract Test*)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-STP-009)

### 1.1 Problem Statement & Rationale
ASPICE SUP.8 (Finding B-07) identified that CM processes lacked a centralized plan and that `.gitmodules` specified a floating branch for `ANT-Android-SDKs`. A version-controlled CM plan (`docs/management/cm_plan.md`) and pinned submodule configuration ensure baseline determinism and auditable configuration control.

### 1.2 Functional & Architectural Requirements
1. **Formal CM Plan (`REQ-STP-009.1`)**:
   - The project SHALL author and maintain `docs/management/cm_plan.md` covering SUP.8.BP1–BP7 (CIs, branching, baselining, submodules, disaster recovery).
2. **Submodule Pinning (`REQ-STP-009.2`)**:
   - `.gitmodules` SHALL NOT specify floating branch tracking (`branch = ...`) for `ANT-Android-SDKs`.
3. **Governance & Canonical Boundaries (`REQ-STP-009.3`)**:
   - `docs/management/` SHALL reside outside `docs/engineering/`, strictly preserving Rule 67 directory integrity.
4. **Preservation of Invariants**:
   - Zero changes to application runtime code in `app/src/main/` or existing build targets.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (CM Plan Completeness)**:
  * *Given* the repository root,
  * *When* inspecting `docs/management/cm_plan.md`,
  * *Then* all mandatory SUP.8 sections (Configuration Items, GitFlow branching, baselines, submodules, disaster recovery) are present.
* **Criterion 2 (Pinned Submodule)**:
  * *Given* `.gitmodules`,
  * *When* checked for branch tracking,
  * *Then* zero `branch =` entries are present for `ANT-Android-SDKs`.
* **Criterion 3 (Rule 67 Invariance)**:
  * *Given* `docs/engineering/`,
  * *When* validated via `tools/verify_requirement_governance.py`,
  * *Then* only canonical engineering directories exist.

### Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-STP-009`). No existing requirements modified or relaxed.

---

## 2. Test Specification (TST-STP-009)

### Test Case 1: CM Plan Structure & Submodule Pinning Contract Test (`TST-STP-009.1`)
* **Scope**: `ReleaseCiAndLintConfigContractTest.kt`.
* **Assertions**: Assert `docs/management/cm_plan.md` exists with all mandatory sections; assert `.gitmodules` contains no `branch =` line for `ANT-Android-SDKs`.

### Test Case 2: Rule 67 Governance Audit (`TST-STP-009.2`)
* **Scope**: `tools/verify_requirement_governance.py`.
* **Assertions**: 100% parity across living doc shards and zero non-canonical entries in `docs/engineering/`.

### Test Case 3: Tier 1 Build Contract Regression (`TST-STP-009.3`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass across all build and architecture contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-STP-009.1]` | Contract | Assert CM plan presence/sections & `.gitmodules` pinning | `REQ-STP-009.1`, `REQ-STP-009.2` | Specified |
| `[TST-STP-009.2]` | Governance | `verify_requirement_governance.py`: Rule 67 purity | `REQ-STP-009.3` | Specified |
| `[TST-STP-009.3]` | Regression | Tier 1 build contract suite (`build.*`) | `REQ-PRO-001` | Specified |
