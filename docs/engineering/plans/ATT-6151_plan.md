# Stage 3: Implementation Plan - ATT-6151: Introduce Project Risk Register

**Ticket**: [ATT-6151](https://rainerblind.atlassian.net/browse/ATT-6151)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-STP-010` (*Project Risk Register & Sprint-Start Review Governance*)  
**Test Mapping**: `TST-STP-010` (*Project Risk Register & Sprint-Start Governance Contract Test*)  
**Branch**: `feature/ATT-6151`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
ASPICE MAN.3 (Finding B-06) identified that the repository lacks a version-controlled project risk register (MAN.3.BP5) and a recurring risk review mechanism. This plan operationalizes risk governance by establishing `docs/management/risk_register.md` with quantified exposure scores ($P \times I$), integrating an exception-based risk review step into Ceremony 1 (Sprint-Start) in `.agents/skills/sprint-planner/SKILL.md`, and enforcing integrity via automated contract tests.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-STP-010` (`REQ-STP-010.1` Authoritative Risk Register, `REQ-STP-010.2` Sprint-Start Governance Integration, `REQ-STP-010.3` Canonical Boundaries & Rule 67 Invariance, `REQ-STP-010.4` Contract Test).
* **Test Mapping**: `TST-STP-010` (`TST-STP-010.1` Risk Register & Structure Audit, `TST-STP-010.2` Ceremony 1 Sprint-Start Audit, `TST-STP-010.3` Rule 67 Boundary Guard, `TST-STP-010.4` Build Contract Suite Execution).

---

## 3. System Invariants & Preserved Behavior
1. **Rule 67 Canonical Structure**: `docs/management/` resides outside `docs/engineering/`; zero unauthorized files/directories in `docs/engineering/`.
2. **Autonomous Sprint Execution (Rule 2 & Rule 77)**: Risk review in Ceremony 1 is exception-based for the Coordinator and does not block autonomous in-sprint execution.
3. **High-Exposure Mitigation Mandate**: Every registered risk with Exposure $\ge 15$ MUST link to an active or planned Jira remediation ticket.
4. **Zero Runtime Binary Impact**: Zero modifications to application runtime source code in `app/src/main/`.

---

## 4. Proposed Architectural Changes
* **Project Risk Register (`docs/management/risk_register.md`)**:
  * Define 5x5 scoring matrix (Probability 1..5, Impact 1..5, Exposure $E = P \times I$: Low 1–6, Medium 8–12, High 15–25).
  * Review Log table (`Last Review Date`, `Sprint`, `Reviewer`).
  * Tabulated initial risks (`RISK-001`..`RISK-006`: Single Human Bottleneck, AI Model Drift, Play Policy Shifts, Sensor Ecosystem Quirks, Third-Party APIs, SQLite Migrations) with Owner, Strategy, and Jira Mitigation links for all high exposures.
* **Sprint Planner Skill (`.agents/skills/sprint-planner/SKILL.md`)**:
  * Add Step 3.1 in Ceremony 1 defining the risk register inspection and review date update.
* **Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)**:
  * Implement `testProjectRiskRegister_existsAndHighExposureMitigated()` asserting file existence, mandatory headers/columns, initial risk keys, mitigation links for High exposures, and skill check.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Author Authoritative Project Risk Register
* Files: `docs/management/risk_register.md`
* Action: Formulate complete ASPICE MAN.3.BP5 Risk Register with scoring rubric, review log, and `RISK-001`..`RISK-006` entries.

### Step 2: Integrate Risk Review Checkpoint in Sprint-Start Ceremony
* Files: `.agents/skills/sprint-planner/SKILL.md`
* Action: Add Step 3.1 in Ceremony 1 instructing Coordinator to review `docs/management/risk_register.md` and stamp review date.

### Step 3: Implement Automated Contract Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* Action: Add `testProjectRiskRegister_existsAndHighExposureMitigated()` asserting document structure, high-exposure mitigations, and skill instructions.

### Step 4: Verification Suite Execution
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted build contract tests and living-doc parity tool.
* **Rollback**: `git checkout feature/ATT-6151 && git reset --hard HEAD` cleanly reverts all working changes.
