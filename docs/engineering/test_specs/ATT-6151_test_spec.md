# Stage 2: Requirement & Test Specification - ATT-6151: Introduce project risk register

**Ticket**: [ATT-6151](https://rainerblind.atlassian.net/browse/ATT-6151)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6151`  
**Requirement Mapping**: `REQ-STP-010` (*Project Risk Register & Sprint-Start Review Governance*)  
**Test Spec ID**: `TST-STP-010` (*Project Risk Register & Sprint-Start Governance Contract Test*)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-STP-010)

### 1.1 Problem Statement & Rationale
ASPICE MAN.3 (Finding B-06) identified that the repository lacks a version-controlled project risk register and review process (MAN.3.BP5). Authoring `docs/management/risk_register.md` with quantified exposure scores ($P \times I$) and integrating an exception-based risk review into Ceremony 1 (Sprint-Start) satisfies ASPICE MAN.3 while preserving autonomous sprint execution.

### 1.2 Functional & Architectural Requirements
1. **Authoritative Risk Register (`REQ-STP-010.1`)**:
   - The project SHALL maintain `docs/management/risk_register.md` defining a 5x5 scoring matrix, exposure tiers (Low, Medium, High), initial project risks (PR-01 through PR-04), ownership, and mitigation links.
2. **Sprint-Start Governance Integration (`REQ-STP-010.2`)**:
   - `.agents/skills/sprint-planner/SKILL.md` (Ceremony 1) SHALL include an exception-based risk register review checkpoint updating the review timestamp.
3. **Canonical Boundaries & Rule 67 Invariance (`REQ-STP-010.3`)**:
   - `docs/management/` SHALL reside outside `docs/engineering/`, maintaining strict Rule 67 canonical directory boundaries.
4. **Preservation of Invariants**:
   - Zero changes to application runtime binaries in `app/src/main/`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Risk Register Completeness)**:
  * *Given* the repository root,
  * *When* inspecting `docs/management/risk_register.md`,
  * *Then* scoring scales, exposure thresholds, initial risks (`PR-01`..`PR-04`), and High-risk mitigation tickets are present.
* **Criterion 2 (Ceremony 1 Integration)**:
  * *Given* `.agents/skills/sprint-planner/SKILL.md`,
  * *When* inspecting Ceremony 1 procedure,
  * *Then* an explicit risk register review checkpoint is defined.
* **Criterion 3 (Contract & Governance Guard)**:
  * *Given* `ReleaseCiAndLintConfigContractTest.kt`,
  * *When* executed via Gradle,
  * *Then* risk register structure and sprint planner integration are verified with 100% pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-STP-010`). No existing requirements modified or relaxed.

---

## 2. Test Specification (TST-STP-010)

### Test Case 1: Risk Register Structure & Sprint Planner Contract Test (`TST-STP-010.1`)
* **Scope**: `ReleaseCiAndLintConfigContractTest.kt`.
* **Assertions**: Assert `docs/management/risk_register.md` exists with required headers, matrix, and risk entries; assert `.agents/skills/sprint-planner/SKILL.md` contains risk review instructions.

### Test Case 2: Rule 67 Governance Audit (`TST-STP-010.2`)
* **Scope**: `tools/verify_requirement_governance.py`.
* **Assertions**: 100% parity across living doc shards and zero non-canonical entries in `docs/engineering/`.

### Test Case 3: Tier 1 Build Contract Regression (`TST-STP-010.3`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% pass across all build and architecture contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-STP-010.1]` | Contract | Assert risk register presence/sections & Ceremony 1 integration | `REQ-STP-010.1`, `REQ-STP-010.2` | Specified |
| `[TST-STP-010.2]` | Governance | `verify_requirement_governance.py`: Rule 67 purity | `REQ-STP-010.3` | Specified |
| `[TST-STP-010.3]` | Regression | Tier 1 build contract suite (`build.*`) | `REQ-PRO-001` | Specified |
