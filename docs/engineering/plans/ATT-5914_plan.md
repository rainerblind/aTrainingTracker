# Stage 3 Implementation Plan: ATT-5914 - Parallel Sprint Execution (2 Disjoint Epics): Use 'Workspace: share' worktrees, conflict-free strings/docs & flock build/merge serialization

**Ticket**: [ATT-5914](https://rainerblind.atlassian.net/browse/ATT-5914)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-PRO-041` (`041.1`–`041.4`)  
**Test Spec Mapping**: `TST-PRO-041` (`041.1`–`041.5`)  
**Branch**: `feature/ATT-5914`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Technical Architecture & SWE.2 Design
* **Decomposition & Concurrency Model**:
  - **Worktree Isolation (`REQ-PRO-041.1`)**: Coordinator invokes persistent subagent pairs using `Workspace: "share"`. Each pair operates in an isolated Git worktree backed by the shared repository, eliminating branch checkout and working tree collisions. Concurrency strictly capped at 2 tickets from disjoint Parent Epics; same-Epic parallelism prohibited.
  - **Flock Mutual Exclusion (`REQ-PRO-041.2`)**: Author `tools/locked_build_merge.py` (and mirror in `tools/hoechstmass/tools/locked_build_merge.py`) using POSIX `fcntl.flock` on `/tmp/att_build_merge.lock`. Serializes heavy `./gradlew` tests and Stage 5 `sprint/<SPRINT_NAME>` squash-merges while Stages 1–3 and Stage 4 code authoring run 100% concurrently.
  - **Partitioned String Discovery (`REQ-PRO-041.3`)**: Refactor `TranslationParityTest.kt` to dynamically discover all `strings*.xml` and `arrays*.xml` across `res/values*/` instead of hardcoding filenames, enabling domain-scoped `strings_<domain>.xml` additions across 9 locales without EOF git conflicts.
  - **Deterministic Living Docs Sync (`REQ-PRO-041.4`)**: Codify Stage 5 rebase-then-recalculate sequence under the merge lock, running `python3 tools/jira_util.py recalc-docs` before squash-merging onto `sprint/<SPRINT_NAME>`.
  - **Rule 36 DRY**: Full parity maintained across root `.agents/` & `tools/` and `tools/hoechstmass/`.

---

## 2. Atomic Implementation Steps

| Step | Component | Target Files | Action & Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Build & Merge Lock | `tools/locked_build_merge.py`<br>`tools/hoechstmass/tools/locked_build_merge.py` | Implement `run_locked(cmd)` and CLI helper using `fcntl.flock` on `/tmp/att_build_merge.lock`. | `REQ-PRO-041.2`<br>`TST-PRO-041.2` |
| **2** | Test Lock Utility | `tools/test_flock_serialization.py`<br>`tools/hoechstmass/tools/test_hoechstmass_tools.py` | Author unit tests verifying lock acquisition, process queuing, and clean release under failure. | `REQ-PRO-041.2`<br>`TST-PRO-041.2` |
| **3** | Dynamic Strings Parity | `TranslationParityTest.kt` | Refactor `resXmlFiles` discovery to dynamically scan all `strings*.xml` and `arrays*.xml` across 9 locales. | `REQ-PRO-041.3`<br>`TST-PRO-041.3` |
| **4** | Governance Rules | `.agents/rules/aspice_governance.md`<br>`tools/hoechstmass/.../aspice_governance.md` | Codify 2-ticket parallel dispatch protocol across disjoint Epics via `Workspace: "share"` and `flock`. | `REQ-PRO-041.1-2`<br>`TST-PRO-041.1` |
| **5** | Stage Skills | `.agents/skills/{jira-workflow,stage4-implementation,stage5-verification,sprint-planner}/SKILL.md`<br>`tools/hoechstmass/.../SKILL.md` mirrors | Update skills with worktree dispatch instructions, locked test execution, and rebase-then-recalc merge protocol. | `REQ-PRO-041.1-4`<br>`TST-PRO-041.1,4` |
| **6** | Verification | Automated Test Suite | Run `python3 -m unittest tools/test_flock_serialization.py`, `./gradlew testDebugUnitTest --tests "...TranslationParityTest"`, and `python3 tools/verify_requirement_governance.py`. | `REQ-PRO-041.1-4`<br>`TST-PRO-041.5` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: Zero Android runtime code changes in `app/src/main/java/`.
* **Human Gate Integrity (`REQ-PRO-016`)**: Inviolable human decision gates preserved; agents never close parent tickets.
* **Strict Concurrency Bounding**: Max 2 concurrent tickets strictly restricted to disjoint Parent Epics.
* **Tooling Parity (Rule 36 DRY)**: Root files and `tools/hoechstmass/` remain 100% synchronized.
* **Risk Rating**: **LOW** (Workflow governance, process tooling, test framework dynamic discovery).
