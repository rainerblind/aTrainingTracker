# Stage 3: Implementation Plan - ATT-6153: Remove stale process artifacts from repository

**Ticket**: [ATT-6153](https://rainerblind.atlassian.net/browse/ATT-6153)  
**Sub-task**: [ATT-6623](https://rainerblind.atlassian.net/browse/ATT-6623) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-STP-007` (*Stale Process Artifact Elimination & Repository Hygiene Hardening*)  
**Test Mapping**: `TST-STP-007` (*Stale Process Artifacts Excision & Gitignore Hygiene Contract Test*)  
**Branch**: `feature/ATT-6153`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Historical agent sessions left transient task and plan artifacts tracked in the git repository (`.active_task.json`, `implementation_plan.artifact.md`, `.artifacts/`). `.gitignore` lacked exclusion rules, risking re-introduction. This plan specifies the atomic excision of stale tracked artifacts, updates `.gitignore`, enforces repository hygiene via an automated contract test, and preserves Rule 67 canonical directory integrity.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-STP-007` (`REQ-STP-007.1` artifact excision, `REQ-STP-007.2` gitignore coverage, `REQ-STP-007.3` canonical purity, `REQ-STP-007.4` contract test).
* **Test Mapping**: `TST-STP-007` (`TST-STP-007.1` artifact absence, `TST-STP-007.2` gitignore audit, `TST-STP-007.3` governance parity, `TST-STP-007.4` build contract suite).

---

## 3. System Invariants & Preserved Behavior
1. **Rule 67 Canonical Structure**: `docs/engineering/` strictly limited to 5 canonical subdirectories, `metrics`, `test_results`, and approved markdown files. No `docs/engineering/archive` directory created.
2. **Zero Runtime Impact**: Zero modifications to Android runtime source code in `app/src/main/` or existing app resources.
3. **Living Documentation Parity**: 100% mathematical parity in `docs/requirements.md` and `docs/tests.md` across all 9 shards.
4. **Parent Human Gate Invariance**: Advancing parent ticket to `Erledigt` remains reserved exclusively for the human user.

---

## 4. Proposed Architectural Changes
* **Repository Root File Excision**: Remove `.active_task.json`, `implementation_plan.artifact.md`, and `.artifacts/` from git tracking and disk.
* **Gitignore Hardening (`.gitignore`)**: Append ignore rules for `.active_task.json`, `.artifacts/`, `*.artifact.md`, and `.reviews/`.
* **Contract Test (`ReleaseCiAndLintConfigContractTest.kt`)**: Add `testRepositoryHygiene_noStaleProcessArtifactsAndGitignoreCovered()` asserting absence of stale files and presence of required gitignore patterns.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Remove Stale Process Artifacts from Git Tracking
* Files: `.active_task.json`, `implementation_plan.artifact.md`, `.artifacts/`
* Action: Execute `git rm -r .active_task.json implementation_plan.artifact.md .artifacts/`.

### Step 2: Harden Gitignore Configuration
* Files: `.gitignore`
* Action: Add ignore block for `.active_task.json`, `.artifacts/`, `*.artifact.md`, and `.reviews/`.

### Step 3: Implement Repository Hygiene Contract Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* Action: Add `testRepositoryHygiene_noStaleProcessArtifactsAndGitignoreCovered()` asserting non-existence of excised files and presence of ignore patterns in `.gitignore`.

### Step 4: Verification Suite Execution
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted contract test suite and `tools/verify_requirement_governance.py` under the parallel sprint lock.
* **Rollback**: `git reset --hard HEAD` on `feature/ATT-6153` cleanly reverts all changes without affecting `sprint/2026-46.1`.
