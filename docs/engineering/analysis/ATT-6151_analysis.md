# Stage 1 Analysis: ATT-6151 - [Verbesserung] Introduce project risk register

**Ticket**: [ATT-6151](https://rainerblind.atlassian.net/browse/ATT-6151)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6151`  
**Requirement Mapping**: `REQ-STP-010` | **Test Mapping**: `TST-STP-010`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
* **Context**: ASPICE MAN.3 (Finding B-06) identified that the repository lacks a version-controlled project and product risk register. Project, technical, and operational risks (e.g. single human release bottleneck, AI model drift, Google Play policy updates, sensor ecosystem quirks) are currently tracked informally without quantified exposure ratings, formal ownership, or review cadences.
* **Goal**: Author `docs/management/risk_register.md` with structured probability, impact, risk exposure ($P \times I$), mitigation strategies, and owners; integrate a risk register review step into Ceremony 1 (Sprint-Start); and enforce governance via contract testing.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: Engineering focused on in-sprint software construction (SWE.1–SWE.6) without establishing formal project management risk governance (MAN.3 BP).
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Repository Location Audit*: `docs/engineering/` is strictly constrained to canonical subdirectories by `verify_requirement_governance.py` (`Rule 67`). Locating `risk_register.md` in `docs/management/` (alongside `cm_plan.md`) preserves directory invariants while keeping risk records versioned in Git.
  * *Sprint Ceremony Audit*: Inspected `.agents/skills/sprint-planner/SKILL.md` (Ceremony 1). While Step 3 conducts batch ticket audits, it lacks an explicit risk register review checkpoint. Adding an exception-based risk review step ensures review dates and newly surfaced risks are updated at every sprint kickoff without blocking autonomous execution.
  * *High-Exposure Risk Audit*: Four initial risks must be captured: single human bottleneck (mitigated by ATT-6148/ATT-6152/ATT-6141), AI model drift (mitigated by contract tests & prompt freeze), Play policy changes (mitigated by Target SDK audits), and sensor ecosystem fragmentation (mitigated by BANAL mock layer).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Author `docs/management/risk_register.md` defining risk scoring matrix (1–5 scale), exposure thresholds, registered project risks, owners, review stamps, and mitigation ticket links.
  * Update `.agents/skills/sprint-planner/SKILL.md` (Ceremony 1) with an explicit risk register review and timestamp update step.
  * Specify `REQ-STP-010` and `TST-STP-010` in `req_project_governance.md` and `tst_project_governance.md`.
  * Add automated contract test `testProjectRiskRegister_existsAndGoverned()` in `ReleaseCiAndLintConfigContractTest.kt`.
* **Out-of-Scope Non-Goals**:
  * Zero modifications to Android application runtime code in `app/src/main/`.
  * Zero non-canonical files in `docs/engineering/` (Rule 67).
  * No manual interactive blockers during Ceremony 1 (exception-based check only).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: Net-new requirement `REQ-STP-010` / `TST-STP-010` (*Project Risk Management & Register Governance*).
* **Chesterton's Fence Audit**: Ceremony 1 was optimized in `Rule 77` (`ATT-5762`) to eliminate slow ticket-by-ticket walkthroughs. The risk register review must integrate as a fast, exception-based Coordinator audit at Sprint-Start, preserving the full-sprint autonomous execution flow (`Rule 2`).
* **Side-Effect Audit (Rule 68)**: Sharded living documentation changes are completely isolated to `req_project_governance.md` and `tst_project_governance.md`, avoiding merge collisions with other active epics.

---

## 5. Architectural Strategy & High-Level Solution
* **Risk Register Architecture**: Define 5×5 probability/impact matrix, exposure classification (Low 1–6, Medium 8–12, High 15–25), and tabulated risk entries with mandatory mitigation tickets for High exposure.
* **Sprint Planner Integration**: Add risk audit step to Ceremony 1 in `.agents/skills/sprint-planner/SKILL.md`.
* **Automated Contract Test**: Assert existence, required sections, headers, and skill integration in `ReleaseCiAndLintConfigContractTest.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Target Release `V5.0.0`; Rule 67 canonical directory boundaries preserved; zero runtime regressions; 100% contract test pass rate.
* **Risk Rating**: **LOW** — Pure documentation, process skill alignment, and contract test validation; zero production binary changes.
