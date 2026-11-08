# Stage 1 Analysis: ATT-5764 - Höchstmaß (ASPICE Level 4/5 — Part 2/2): Quantitative Scorecard in Ceremony 2 & ASPICE Level 1–5 Mapping

**Ticket**: [ATT-5764](https://rainerblind.atlassian.net/browse/ATT-5764)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5764`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
Following the implementation of the `sprint-metrics` CLI engine and `process_metrics.jsonl` time-series storage in Part 1 (`ATT-5765`), this ticket completes the formal integration of **ASPICE Level 4 (*Predictable*)** and **Level 5 (*Innovating*)**:
1. **Ceremony 2 Integration**: Mandate automated execution of `python3 tools/jira_util.py --as coordinator sprint-metrics --record --post-to-retro` at the start of Ceremony 2 Step 4 (Retrospective), embedding the Quantitative Sprint Scorecard into the retro deliverable.
2. **Closed-Loop Improvement (`PA 4.2` $\rightarrow$ `PA 5.2`)**: Enforce that any `⚠️ PA 4.2 Special Cause Variation` triggers a mandatory root-cause analysis and concrete process/skill corrective action in the retrospective prior to sprint merge.
3. **Maturity Model Specification**: Document the complete **ASPICE Capability Level 1–5 Matrix** (`PA 1.1` to `PA 5.2`) across `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current State**: The `sprint-metrics` command exists in `tools/jira_util.py`, but its invocation in Ceremony 2 is informal. Retrospectives lack a standardized Quantitative Scorecard section, and neither protocol nor README articulates how Höchstmaß maps to the ASPICE Process Assessment Model (PAM).
* **Independent Peer Review & Verification**:
  * Audited `.agents/skills/sprint-planner/SKILL.md`: Step 4 governs the Retrospective ("Retro Before Merge"), making it the canonical injection point for automated scorecard generation and `PA 4.2` tripwire enforcement.
  * Audited `templates/sprint_retro_template.md`: Updating the template ensures future retrospectives systematically record Gate 1–5 FTPRs, Swiss Cheese escape probabilities, and SPC control limits.
  * Audited `docs/engineering/metrics/process_metrics.jsonl`: Contains real baseline data from Sprints `2026-42.2`, `2026-42.3`, `2026-42.4`, and `2026-45.12` ready for continuous SPC evaluation.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Update `.agents/skills/sprint-planner/SKILL.md` and `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md` to mandate `sprint-metrics --record --post-to-retro` and the `PA 4.2 -> PA 5.2` corrective action trigger.
  2. Update `templates/sprint_retro_template.md` (and `tools/hoechstmass/templates/`) with the Quantitative Sprint Scorecard section.
  3. Codify the ASPICE Capability Level 1–5 Matrix (`PA 1.1`–`PA 5.2`) in `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md`.
  4. Refine `REQ-PRO-038` and `TST-PRO-038` in living documentation.
  5. Update `HoechstmassCoreFrameworkContractTest.kt` to assert presence of the ASPICE Level 1–5 Capability Matrix.
* **Out-of-Scope Non-Goals**:
  * Modifying Android app runtime code in `app/src/main/`.
  * Redesigning the underlying metric calculation engine in `tools/sprint_metrics.py`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Requirement Mapping**: Refinement of `REQ-PRO-038` (`req_process_stability.md`), extending sub-clauses for Ceremony 2 retrospective scorecard integration and ASPICE Level 1–5 capability governance.
* **Preservation of Core Invariants**: Retrospective-before-merge invariant preserved; zero runtime impact; 100% build and contract test pass rate.

---

## 5. Architectural Strategy & High-Level Solution
* **Ceremony 2 Workflow**: Step 4 runs `sprint-metrics --record --post-to-retro`; if outlier detected, retro document must define remediation.
* **Process Documentation**: Codify Capability Levels 1 (*Performed*), 2 (*Managed*), 3 (*Established*), 4 (*Predictable*), and 5 (*Innovating*) mapping PAM process attributes to Höchstmaß mechanisms.
* **Contract Test**: Add assertions in `HoechstmassCoreFrameworkContractTest.kt` verifying Level 1–5 matrix and scorecard template sections.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero runtime app modifications; 100% test suite and contract test pass rate; strict retro before merge.
* **Risk Rating**: **LOW** — Pure documentation, ceremony skill, and retro template enhancement.
