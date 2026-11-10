# Stage 1 Analysis: ATT-6152 - [Verbesserung] Write configuration management plan and pin submodule baseline

**Ticket**: [ATT-6152](https://rainerblind.atlassian.net/browse/ATT-6152)  
**Sub-task**: [ATT-6644](https://rainerblind.atlassian.net/browse/ATT-6644) (`[Analysis]`)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6152`  
**Requirement Mapping**: `REQ-STP-009` | **Test Mapping**: `TST-STP-009`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
* **Context**: ASPICE SUP.8 (Finding B-07) identified two configuration management gaps: (1) branching, baselines, and release procedures are documented implicitly across governance rules and release skills rather than in a formal Configuration Management Plan, and (2) `.gitmodules` specifies a floating branch (`branch = development/C.B6-P.B6RC`) for the `ANT-Android-SDKs` submodule.
* **Goal**: Author a comprehensive, version-controlled CM plan in `docs/management/cm_plan.md` (addressing SUP.8.BP1–BP7) and pin `ANT-Android-SDKs` to an immutable committed revision in `.gitmodules` to guarantee baseline reproducibility.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: CM practices evolved organically into `.agents/rules/aspice_governance.md` (GitFlow, sprint branches, atomic commits), `.agents/skills/release-manager/SKILL.md` (release branches and tags), and living documentation without a centralized CM plan. Furthermore, `.gitmodules` tracked a floating upstream branch, allowing non-deterministic checkout drift during submodule updates.
* **Independent Verification**:
  * *Hypothesis Audit*: Establishing `docs/management/cm_plan.md` centralizes CM governance without violating Rule 67 (which restricts only `docs/engineering/`).
  * *Submodule Audit*: `ANT-Android-SDKs` is tracked as gitlink `0e8cea7`. Removing `branch = development/C.B6-P.B6RC` from `.gitmodules` prevents `git submodule update --remote` from pulling unverified upstream commits and pins builds to the verified commit.
  * *CM Plan Contents*: Must explicitly define Configuration Items (CIs), GitFlow branching, sprint worktree isolation, release tagging (`V<versionName>__<versionCode>`), living docs parity, and backup/disaster recovery.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Author formal ASPICE SUP.8 Configuration Management Plan in `docs/management/cm_plan.md`.
  * Pin `ANT-Android-SDKs` in `.gitmodules` by removing floating branch configuration.
  * Formulate `REQ-STP-009` and `TST-STP-009` in living documentation shards.
  * Implement automated contract test in `ReleaseCiAndLintConfigContractTest.kt` asserting CM plan presence/sections and submodule pinning.
* **Out-of-Scope Non-Goals**:
  * Zero changes inside `docs/engineering/` outside canonical directories (Rule 67).
  * Zero modifications to Android application runtime code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-STP-009`). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution
* **CM Plan Formulation**: Structure `docs/management/cm_plan.md` across: (1) Scope & Objectives, (2) Configuration Item Registry, (3) Branching & Versioning Strategy, (4) Baseline & Release Integrity, (5) Submodule Management, and (6) Backup & Recovery.
* **Submodule Pinning**: Remove `branch = ...` from `.gitmodules`, ensuring submodules resolve strictly by committed gitlink.
* **Contract Verification**: Add test asserting `docs/management/cm_plan.md` sections and pinned `.gitmodules`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Build reproducibility across fresh clones and CI runners.
  2. Rule 67 canonical directory structure strictly preserved.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Process documentation and configuration pinning; zero runtime code impact.
