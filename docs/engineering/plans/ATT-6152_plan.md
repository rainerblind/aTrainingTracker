# Stage 3: Implementation Plan - ATT-6152: Configuration Management Plan and Submodule Pinning

**Ticket**: [ATT-6152](https://rainerblind.atlassian.net/browse/ATT-6152)  
**Sub-task**: [ATT-6648](https://rainerblind.atlassian.net/browse/ATT-6648) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-STP-009` (*Configuration Management Plan & Pinned Submodule Baseline*)  
**Test Mapping**: `TST-STP-009` (*CM Plan Integrity & Submodule Pinning Contract Test*)  
**Branch**: `feature/ATT-6152`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Configuration management procedures currently exist across disparate rules and skills without a consolidated ASPICE SUP.8 Configuration Management Plan, and `.gitmodules` specifies a floating remote branch for `ANT-Android-SDKs` (Finding B-07). This plan details the creation of `docs/management/cm_plan.md`, submodule pinning in `.gitmodules`, and enforcement via automated contract testing.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-STP-009` (`REQ-STP-009.1` formal CM plan, `REQ-STP-009.2` submodule pinning, `REQ-STP-009.3` Rule 67 canonical boundaries, `REQ-STP-009.4` contract test).
* **Test Mapping**: `TST-STP-009` (`TST-STP-009.1` CM plan audit, `TST-STP-009.2` submodule pinning audit, `TST-STP-009.3` Rule 67 governance, `TST-STP-009.4` build contract suite).

---

## 3. System Invariants & Preserved Behavior
1. **Rule 67 Canonical Structure**: `docs/management/` placed strictly outside `docs/engineering/`; zero unauthorized files or directories created in `docs/engineering/`.
2. **Submodule Determinism**: Submodule `ANT-Android-SDKs` checked out to exact committed revision (`0e8cea7`) across all clones.
3. **Zero Runtime Impact**: Zero modifications to application runtime source code in `app/src/main/`.
4. **Parent Human Gate Invariance**: Advancing parent ticket to `Erledigt` remains reserved exclusively for the human user.

---

## 4. Proposed Architectural Changes
* **Configuration Management Plan (`docs/management/cm_plan.md`)**: Formalize ASPICE SUP.8.BP1–BP7 covering CIs, GitFlow branching/tagging (`V<versionName>__<versionCode>`), sprint worktree isolation, baseline parity, and disaster recovery.
* **Submodule Configuration (`.gitmodules`)**: Remove `branch = development/C.B6-P.B6RC` to freeze submodule tracking to committed gitlink SHA.
* **Contract Testing (`ReleaseCiAndLintConfigContractTest.kt`)**: Add `testConfigurationManagementPlan_existsAndSubmodulePinned()` asserting CM plan sections and absence of floating branch lines in `.gitmodules`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Author Formal Configuration Management Plan
* Files: `docs/management/cm_plan.md`
* Action: Formulate complete SUP.8 CM Plan defining configuration items, branch/tag nomenclature, baseline criteria, and disaster recovery.

### Step 2: Pin Submodule in Git Configuration
* Files: `.gitmodules`
* Action: Remove `branch = development/C.B6-P.B6RC` directive under `[submodule "ANT-Android-SDKs"]`.

### Step 3: Implement Automated CM Contract Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* Action: Add `testConfigurationManagementPlan_existsAndSubmodulePinned()` asserting CM plan presence, mandatory sections, and `.gitmodules` branch absence.

### Step 4: Verification Suite Execution
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted contract test suite and `verify_requirement_governance.py`.
* **Rollback**: `git reset --hard HEAD` on `feature/ATT-6152` cleanly reverts working tree modifications.
