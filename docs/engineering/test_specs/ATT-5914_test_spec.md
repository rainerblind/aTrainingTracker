# Stage 2: Requirement & Test Specification - ATT-5914

**Ticket**: [ATT-5914](https://rainerblind.atlassian.net/browse/ATT-5914)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-PRO-041` (*Parallel Sprint Execution Protocol for Disjoint Epics*)  
**Test Spec ID**: `TST-PRO-041`  
**Branch**: `feature/ATT-5914`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-PRO-041)

### 1.1 Problem Statement & Rationale
Phase 2 sprint execution is currently strictly sequential: the Coordinator dispatches one ticket at a time. However, tickets from disjoint domain Epics touch isolated code packages and distinct living-doc shards. Enabling concurrent execution of up to 2 tickets belonging to disjoint Parent Epics increases throughput without risking merge conflicts. Shared bottlenecks are mitigated via `Workspace: "share"` worktrees, partitioned `strings_<domain>.xml`, POSIX `flock` serialization for heavy Gradle test runs and sprint squash-merges, and a rebase-then-recalculate living-doc sequence.

### 1.2 Functional & Architectural Requirements
* `REQ-PRO-041.1` (Worktree Isolation): Coordinator SHALL dispatch parallel tickets using `Workspace: "share"` for persistent subagent pairs. Each pair SHALL operate in an isolated Git worktree. Concurrency is strictly capped at 2 tickets across disjoint Epics (zero package/shard overlap); parallel execution within the same Epic is prohibited.
* `REQ-PRO-041.2` (POSIX Flock Serialization): Tooling and governance SHALL provide a mutual exclusion wrapper using POSIX `flock` on `/tmp/att_build_merge.lock` for heavy `./gradlew` test suites and Stage 5 `sprint/<SPRINT_NAME>` squash-merges. Stages 1–3 and Stage 4 authoring run 100% concurrently.
* `REQ-PRO-041.3` (Partitioned Localization): Support domain-scoped string files (`res/values*/strings_<domain>.xml`) across all 9 locales. `TranslationParityTest.kt` SHALL dynamically discover all `strings*.xml` files and enforce 100% parity across locales.
* `REQ-PRO-041.4` (Living Docs Synchronization): Master index recalculations (`recalc-docs`) SHALL be executed under the merge lock after rebasing onto `sprint/<SPRINT_NAME>`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Worktree Isolation)**: *Given* 2 tickets from disjoint Epics, *When* dispatched with `Workspace: "share"`, *Then* each runs in an isolated worktree without branch collisions.
* **Criterion 2 (Flock Serialization)**: *Given* concurrent test runs or squash-merges, *When* invoked under `flock`, *Then* heavy operations serialize without worker collisions or Git race conditions.
* **Criterion 3 (Partitioned Localization)**: *Given* strings in `strings_<domain>.xml`, *When* `TranslationParityTest.kt` runs, *Then* it discovers all partitioned files and verifies 100% parity across 9 locales.

### 1.4 System Invariants
* Zero Android runtime changes in `app/src/main/java/`. Max 2 concurrent tickets across disjoint Epics; same-Epic parallelism remains strictly prohibited. Dual-agent quality gates remain 100% invariant.

---

## 2. Test Specification (TST-PRO-041)

### Test Case 1: Worktree Isolation & Disjoint Epic Guardrail (`TST-PRO-041.1`)
* **Scope**: Governance & Process Audit (`aspice_governance.md`, `jira-workflow`)
* **Action**: Audit subagent dispatch protocol; verify worktree isolation and rejection of same-Epic concurrency.
* **Expected Result**: Clean worktree boundaries; strict 2-ticket disjoint Epic rule codified.

### Test Case 2: POSIX Flock Mutual Exclusion (`TST-PRO-041.2`)
* **Scope**: Integration Test (`tools/test_flock_serialization.py`)
* **Action**: Launch concurrent worker processes competing for `/tmp/att_build_merge.lock`.
* **Expected Result**: Strictly serialized execution, zero worker collision, reliable lock release.

### Test Case 3: Partitioned String Discovery & Parity (`TST-PRO-041.3`)
* **Scope**: Unit & Contract Test (`TranslationParityTest.kt`)
* **Action**: Run `TranslationParityTest` with domain-partitioned string files.
* **Expected Result**: 100% parity across 9 languages, dynamic discovery of all `strings*.xml`.

### Test Case 4: Governance Parity & Rebase Sequence (`TST-PRO-041.4`)
* **Scope**: Clean-Room Governance Verification
* **Action**: Run `python3 tools/verify_requirement_governance.py`.
* **Expected Result**: 100% parity across living doc tables; rebase-then-recalc sequence validated.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Test Class / Procedure | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-041.1` | Governance | `aspice_governance.md` & `jira-workflow` | `REQ-PRO-041.1` | Specified |
| `TST-PRO-041.2` | Tooling | `tools/test_flock_serialization.py` | `REQ-PRO-041.2` | Specified |
| `TST-PRO-041.3` | Localization | `TranslationParityTest.kt` | `REQ-PRO-041.3` | Specified |
| `TST-PRO-041.4` | Audit | `verify_requirement_governance.py` | `REQ-PRO-041.4` | Specified |
