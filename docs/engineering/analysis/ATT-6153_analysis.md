# Stage 1 Analysis: ATT-6153 - [Verbesserung] Remove stale process artifacts from repository

**Ticket**: [ATT-6153](https://rainerblind.atlassian.net/browse/ATT-6153)  
**Sub-task**: [ATT-6619](https://rainerblind.atlassian.net/browse/ATT-6619) (`[Analysis]`)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6153`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
* **Context**: Legacy agent runs accidentally committed transient process leftovers to git: `.active_task.json` (ATT-1268), `implementation_plan.artifact.md` (ATT-409), and `.artifacts/` (`task.artifact.md`, `walkthrough.artifact.md`, and session UUID subdirectories).
* **Gap**: `.gitignore` lacks entries for these ephemeral artifacts, risking re-commit. ASPICE SUP.8 (Finding B-08) requires configuration baselines containing strictly controlled work products.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: Early agent iterations generated working artifacts in repository root and `.artifacts/` prior to codifying canonical `docs/engineering/` paths (Rule 67). `.gitignore` was never updated to ignore them.
* **Independent Peer Review & Verification**:
  * *Hypothesis Audit*: Ticket ATT-6153 suggested "Archive into `docs/engineering/archive` or delete; update `.gitignore`".
  * *Verification*: Core Rule 67 (`ATT-4936`) and `tools/verify_requirement_governance.py` restrict `docs/engineering/` strictly to 5 canonical subdirectories (`analysis`, `test_specs`, `plans`, `walkthroughs`, `retros`), `metrics`, `test_results`, `rule_history_archive.md`, and `workflow_overview.html`.
  * *Resolution*: Creating `docs/engineering/archive` would violate Rule 67. Permanent deletion (`git rm -r`) of stale root leftovers strictly preserves Rule 67 compliance while achieving SUP.8 cleanliness.
  * *Call Site Audit*: Checked `app/`, `tools/`, `.agents/`, and `docs/`; zero production classes or tests depend on `.active_task.json`, `.artifacts/`, or `implementation_plan.artifact.md`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Remove stale tracked artifacts: `.active_task.json`, `implementation_plan.artifact.md`, and `.artifacts/` via `git rm -r`.
  * Update `.gitignore` with ignore patterns for `.active_task.json`, `.artifacts/`, `*.artifact.md`, and `.reviews/`.
  * Formalize `REQ-STP-007` and `TST-STP-007` in `docs/requirements/req_project_governance.md` and `docs/tests/tst_project_governance.md`.
  * Enforce clean pass in `tools/verify_requirement_governance.py`.
* **Out-of-Scope Non-Goals**:
  * Do NOT create `docs/engineering/archive` (violates Rule 67).
  * Do NOT modify existing Android runtime code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-STP-007`). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution
* **Execution Steps**:
  1. Untrack files: `git rm -r .active_task.json implementation_plan.artifact.md .artifacts/`.
  2. Append ignore rules to `.gitignore`: `.active_task.json`, `.artifacts/`, `*.artifact.md`, `.reviews/`.
  3. Document governance invariants under `REQ-STP-007` / `TST-STP-007`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Rule 67 canonical directory structure in `docs/engineering/` remains strictly preserved.
  2. Zero regression in existing unit test suite and governance scripts.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Pure hygiene cleanup of deprecated markdown artifacts and gitignore hardening; zero production code impact.
