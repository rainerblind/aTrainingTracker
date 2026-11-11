# Stage 3: Implementation Plan - ATT-6733: Fix master branch name, sprint tags, release contents, and CI inventory in cm_plan.md

**Ticket**: [ATT-6733](https://rainerblind.atlassian.net/browse/ATT-6733)  
**Sub-task**: [ATT-6801](https://rainerblind.atlassian.net/browse/ATT-6801) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-057` (*Configuration Management Repository Grounding*)  
**Test Mapping**: `TST-PRO-057` (*cm_plan.md Parity Contract Test Suite*)  
**Branch**: `feature/ATT-6733`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
ASPICE SUP.8 (Finding B-07) mandates absolute fidelity between the Configuration Management Plan (`docs/management/cm_plan.md`) and authentic repository configuration. Currently, `cm_plan.md` errantly references `main` instead of `master`, omits sprint milestone tags (`sprint/<SPRINT_NAME>`), lacks an explicit Release Contents specification, and omits secondary controlled configuration items (`docs/architecture/`, `docs/management/`, `config/detekt/`, `tools/`, `.agents/`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-057` (*Configuration Management Repository Grounding*)
* **Test Mapping**: `TST-PRO-057` (`ReleaseCiAndLintConfigContractTest.kt`, `verify_requirement_governance.py`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Regressions**: Zero code changes in `app/src/main/`; existing feature test suites pass cleanly.
2. **Repository Stability**: Git branches (`master`, `develop`, `sprint/*`) and tag formats remain structurally untouched.
3. **Canonical Boundaries**: `docs/management/` remains outside `docs/engineering/` (Rule 67).
4. **Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `docs/management/cm_plan.md`
* Update GitFlow hierarchy lines 28, 47, 58 from `main` to `master` (`origin/master`).
* Document immutable sprint milestone tagging (`sprint/<SPRINT_NAME>`) and dual release tagging (`V<versionName>__<versionCode>` and `V<versionName>`).
* Expand Section 2 CIs table to include `docs/architecture/*.md`, `docs/management/*.md`, `config/detekt/`, `tools/`, and `.agents/`.
* Add formal Release Contents subsection defining signed APK, Play Store AAB, `docs/store_presence/` 9-language notes, and Jira FixVersion traceability.

### Component 2: `app/src/test/.../build/ReleaseCiAndLintConfigContractTest.kt`
* Add contract test verifying `master` branch grounding, sprint milestone tagging, release contents, and expanded CI entries in `cm_plan.md`.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Ground Production Branch Name in `docs/management/cm_plan.md`
* Files: `docs/management/cm_plan.md`
* Changes: Replace all occurrences of `main` as branch name with `master` (lines 28, 47, 58).

### Step 2: Document Sprint Milestone & Dual Release Tag Conventions
* Files: `docs/management/cm_plan.md`
* Changes: Add `sprint/<SPRINT_NAME>` close tags and `V<versionName>` / `V<versionName>__<versionCode>` formats.

### Step 3: Expand Configuration Items Inventory & Document Release Contents
* Files: `docs/management/cm_plan.md`
* Changes: Add missing repository asset rows to Section 2 table; add Section 4.2 Formal Release Deliverables package.

### Step 4: Author Contract Test Assertions
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* Changes: Implement `testConfigurationManagementPlan_repositoryGroundingAndReleaseContents()` verifying all REQ-PRO-057 clauses.

### Step 5: Verification & Governance Run
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
* Command: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted contract test suite and `verify_requirement_governance.py` in Stage 4; clean-room run in Stage 5.
* **Rollback**: Atomic squash commit on isolated branch `feature/ATT-6733` allows complete branch discard if needed.
