# Stage 1 Analysis: ATT-6733 - Fix Master Branch Name, Sprint Tags, Release Contents & CI Inventory in cm_plan.md

**Ticket**: [ATT-6733](https://rainerblind.atlassian.net/browse/ATT-6733)  
**Sub-task**: [ATT-6795](https://rainerblind.atlassian.net/browse/ATT-6795) (`[Analysis]`)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 - Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6733` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-46.1 review of `ATT-6152` (*Write CM plan and pin submodule baseline*), four repo-grounding discrepancies were identified in `docs/management/cm_plan.md`:
1. **Wrong Production Branch Name**: Lines 28, 47, and 58 document the production release branch as `main`, whereas the Git repository authentic baseline is `master` (`origin/master`).
2. **Missing Sprint Milestone Tags**: Omits the immutable `sprint/<SPRINT_NAME>` tags created at sprint close (Ceremony 2) and companion `V<versionName>` tags alongside `V<versionName>__<versionCode>`.
3. **Missing Explicit Release Contents**: Omits formal definition of release deliverables (signed APK, Google Play AAB, `docs/store_presence/` localized release notes, and Jira FixVersion baseline).
4. **Incomplete Configuration Items Inventory**: Omits `docs/architecture/*.md`, `docs/management/*.md`, `config/detekt/`, `tools/`, and `.agents/` skills/rules from the controlled CI catalog.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Investigation**:
  * Git inspection (`git branch -a`) confirms `origin/HEAD -> origin/master` and `origin/master`. The repository never had a `main` branch; `cm_plan.md` was drafted using generic Git template conventions.
  * Tag history (`git tag -l`) confirms sprint milestone tags (`sprint/2026-43.3`..`sprint/2026-46.1`) and release tags (`v4.2`, `v4.2.2`, `v4.9.9__233`) are actively maintained.
  * CI inventory audit shows critical project governance tooling (`tools/`), linter configs (`config/detekt/`), agent process rules (`.agents/`), and management docs (`docs/management/`) were omitted, violating ASPICE `SUP.8.BP2`.
* **Independent Peer Review & Verification**:
  * Verified `docs/management/cm_plan.md` against live repository: confirmed lines 28, 47, 58 incorrectly cite `main`.
  * Verified release workflow in `.agents/skills/release-manager/SKILL.md`: confirmed official releases package signed APK, Google Play AAB, and localized release notes under `docs/store_presence/`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Correct production branch references from `main` to `master` across `docs/management/cm_plan.md`.
  * Document `sprint/<SPRINT_NAME>` milestone tags and `V<versionName>` / `V<versionName>__<versionCode>` dual tagging format.
  * Add a dedicated "Release Contents" subsection specifying signed APK, Play Store AAB, `docs/store_presence/` localized release notes, and Jira `FixVersion` baseline traceability.
  * Expand the Configuration Items table to include `docs/architecture/*.md`, `docs/management/*.md`, `config/detekt/`, `tools/`, and `.agents/`.
* **Out-of-Scope Non-Goals**:
  * Renaming the Git `master` branch to `main` (out of scope; would break existing CI/CD and automation).
  * Modifying Android runtime code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new documentation defect fix under existing requirement `REQ-STP-009` (*Configuration Management Plan & Pinned Submodule Baseline*). No existing requirements in `docs/requirements.md` are modified or relaxed.

---

## 5. Architectural Strategy & High-Level Solution
* Update `docs/management/cm_plan.md` to establish 100% code-grounded parity with authentic repository branching, tagging, and asset inventory.
* Preserve existing document structure while expanding Sections 2 and 3.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero modifications to Android application runtime code (`app/src/main/`).
  2. Git repository branch structure (`master`, `develop`, `sprint/*`) remains untouched.
  3. 100% compliance with ASPICE SUP.8 (Configuration Management).
* **Risk Rating**: **LOW** (Pure documentation alignment; strictly bounded at 1 SP).
