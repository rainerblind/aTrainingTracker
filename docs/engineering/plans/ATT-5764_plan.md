# Stage 3: Implementation Plan - ATT-5764: Quantitative Scorecard in Ceremony 2 & ASPICE Level 1–5 Matrix

**Ticket**: [ATT-5764](https://rainerblind.atlassian.net/browse/ATT-5764)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5764`  
**Requirement Mapping**: `REQ-PRO-038` | **Test Mapping**: `TST-PRO-038`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Architecture
Operationalize ASPICE Level 4 (*Predictable*) and Level 5 (*Innovating*) across Höchstmaß ceremonies, templates, and living documentation:
1. **Ceremony 2 Integration**: Mandate `python3 tools/jira_util.py --as coordinator sprint-metrics --record --post-to-retro` in Step 4 of Ceremony 2 (`sprint-planner/SKILL.md` and `tools/hoechstmass/` mirror), automatically recording and commenting the quantitative scorecard.
2. **Closed-Loop Process Improvement (`PA 4.2 -> PA 5.2`)**: Enforce that any `⚠️ PA 4.2 Special Cause Variation` triggers an explicit root-cause analysis (RCA) and concrete process/skill countermeasure in the retrospective deliverable prior to sprint merge.
3. **Template Harmonization**: Add Section 2 (Quantitative Sprint Scorecard & SPC Status) to `templates/sprint_retro_template.md` and `tools/hoechstmass/templates/sprint_retro_template.md`.
4. **ASPICE Capability Level 1–5 Matrix**: Codify the formal mapping of Process Attributes (`PA 1.1` to `PA 5.2`) to Höchstmaß operational mechanisms in `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md`.
5. **Contract Test Hardening**: Extend `HoechstmassCoreFrameworkContractTest.kt` to assert presence of the Capability Level 1–5 Matrix, Ceremony 2 scorecard rules, and retro template sections.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-038` (*Automated Quantitative Sprint Metrics, SPC & Ceremony 2 Governance*)
* **Test Mapping**: `TST-PRO-038.5` (Ceremony 2 Scorecard & Template Contract), `TST-PRO-038.6` (ASPICE Level 1–5 Matrix Contract), `TST-PRO-038.7` (Build & regression suite)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime App Impact**: Zero modifications to production Android code in `app/src/main/`.
2. **Retro Before Merge Invariant**: Retrospective must be completed and signed off prior to merging sprint into `develop`.
3. **Contract Test Compliance**: 100% pass rate across all `Hoechstmass*` JUnit contract tests and Python unit suites.

---

## 4. Proposed Architectural Changes (SWE.2)
* **Skills & Templates**:
  * Update `sprint-planner/SKILL.md` (root & hoechstmass) with the mandatory `sprint-metrics` step and `PA 4.2 -> PA 5.2` rule.
  * Update `sprint_retro_template.md` (root & hoechstmass) with the Quantitative Sprint Scorecard section.
* **Protocol & Framework Documentation**:
  * Add ASPICE Capability Level 1–5 Matrix table (`PA 1.1`–`PA 5.2`) to `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md`.
* **Contract Test**:
  * Add test cases in `HoechstmassCoreFrameworkContractTest.kt` asserting Capability Levels 1–5 (`PA 1.1` to `PA 5.2`) in README and protocol, and scorecard section in retro template.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Ceremony 2 Skill & Retro Template Update)**: Update `sprint-planner/SKILL.md` and `sprint_retro_template.md` in root and `tools/hoechstmass/`.
2. **Step 2 (ASPICE Level 1–5 Matrix Codification)**: Add capability matrix to `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md`.
3. **Step 3 (Contract Test Hardening)**: Update `HoechstmassCoreFrameworkContractTest.kt` to assert the capability matrix and template sections.
4. **Step 4 (Test Execution & Governance)**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.Hoechstmass*"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: `HoechstmassCoreFrameworkContractTest` (100% PASS) + `verify_requirement_governance.py` (100% parity).
* **Rollback Plan**: `git checkout -- .` on `feature/ATT-5764`.
