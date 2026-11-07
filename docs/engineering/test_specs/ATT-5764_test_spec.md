# Stage 2: Requirement & Test Specification - ATT-5764

**Ticket**: [ATT-5764](https://rainerblind.atlassian.net/browse/ATT-5764)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5764`  
**Requirement Mapping**: `REQ-PRO-038` (*Automated Quantitative Sprint Metrics, SPC & Ceremony 2 Governance*)  
**Test Spec ID**: `TST-PRO-038`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-PRO-038)

### 1.1 Problem Statement & Rationale
Part 1 (`ATT-5765`) delivered the `sprint-metrics` CLI engine and historical time-series storage (`process_metrics.jsonl`). Part 2 operationalizes ASPICE Level 4 (*Predictable*) and Level 5 (*Innovating*) by integrating scorecard automation into Ceremony 2 Step 4 (Retrospective), establishing a closed-loop trigger (`PA 4.2 -> PA 5.2`) for outliers, and codifying the ASPICE Capability Level 1–5 Matrix across framework documentation.

### 1.2 Functional & Architectural Requirements
1. **Ceremony 2 Scorecard Automation**: `.agents/skills/sprint-planner/SKILL.md` (and `tools/hoechstmass/` mirror) SHALL mandate running `python3 tools/jira_util.py --as coordinator sprint-metrics --record --post-to-retro` at the start of Ceremony 2 Step 4, automatically commenting the scorecard to the retrospective ticket.
2. **Closed-Loop Countermeasure (`PA 4.2 -> PA 5.2`)**: Whenever an out-of-control condition (`⚠️ PA 4.2 Special Cause Variation`) is detected, the Retrospective report SHALL define a root-cause analysis (RCA) and concrete process countermeasure before sprint completion.
3. **ASPICE Capability Level 1–5 Matrix**: The framework SHALL formalize the complete capability model mapping Process Attributes (`PA 1.1` to `PA 5.2`) to Höchstmaß operational mechanisms in `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md`.
4. **Template & Contract Enforcement**: `templates/sprint_retro_template.md` (and `tools/hoechstmass/` mirror) SHALL provide the Quantitative Scorecard section, enforced via `HoechstmassCoreFrameworkContractTest.kt`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* Ceremony 2 Step 4 (Retrospective),
* *When* the coordinator executes the retrospective workflow,
* *Then* `sprint-metrics --record --post-to-retro` executes automatically and posts the scorecard to the retrospective issue.
* *Given* a metric exceeding $\mu \pm 2\sigma$ control limits,
* *When* the retrospective report is compiled,
* *Then* the report defines an explicit root-cause analysis and `PA 5.2` corrective countermeasure.
* *Given* framework documentation,
* *When* inspected,
* *Then* all 9 ASPICE process attributes from `PA 1.1` to `PA 5.2` are codified and verified via contract tests.

### 1.4 System Invariants
* Zero modifications to Android application source code in `app/src/main/`.
* Strict sprint retro-before-merge invariant preserved.
* 100% build and contract test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PRO-038` (`tools/jira_util.py`, `docs/engineering/metrics/process_metrics.jsonl`, `tools/hoechstmass/`).
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-45.12 (`ATT-5765`, commit `e65fc444`) for quantitative metric calculation and SPC limits.
3. *Root Reason for Existing Formulation*: Part 1 focused strictly on core calculation logic and time-series persistence, leaving ceremony integration and capability matrix documentation to Part 2.
4. *Preservation of Core Invariants*: Extends `REQ-PRO-038` to close the loop in Ceremony 2 without altering calculation logic, maintaining 100% metric fidelity, zero runtime app impact, and full contract test compliance.

---

## 2. Test Specification (TST-PRO-038)

### Test Case 1: Ceremony 2 Retrospective Scorecard Contract (`TST-PRO-038.5`)
* **Scope**: Skill & Template Verification
* **Assertions**: Assert `sprint-planner/SKILL.md` mandates `sprint-metrics --record --post-to-retro` in Step 4; assert `templates/sprint_retro_template.md` contains Quantitative Scorecard and `PA 4.2 -> PA 5.2` corrective trigger.

### Test Case 2: ASPICE Level 1–5 Capability Matrix Contract (`TST-PRO-038.6`)
* **Scope**: Automated JUnit 4 Contract Test (`HoechstmassCoreFrameworkContractTest.kt`)
* **Assertions**: Assert `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md` contain all 9 Process Attributes (`PA 1.1`–`PA 5.2`) and Höchstmaß operational mechanisms.

### Test Case 3: Tier 1 Targeted Contract & Build Suite (`TST-PRO-038.7`)
* **Scope**: Regression execution
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.Hoechstmass*"`
* **Expected Result**: 100% PASS across all framework contract tests; zero failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-038.5` | Skill/Template | `sprint-planner/SKILL.md`, `sprint_retro_template.md` | `REQ-PRO-038.8`, `REQ-PRO-038.9` | Specified |
| `TST-PRO-038.6` | Contract | `HoechstmassCoreFrameworkContractTest.kt` | `REQ-PRO-038.10`, `REQ-PRO-038.11` | Specified |
| `TST-PRO-038.7` | Regression | Gradle build test runner | `REQ-PRO-038` Invariants | Specified |
