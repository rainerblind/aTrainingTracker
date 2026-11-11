# Stage 2: Requirement & Test Specification - ATT-6743: Enforce Rule 81 via Deterministic Python Tripwires & Sync REQ-PRO-041.1 Concurrency=3

**Ticket**: [ATT-6743](https://rainerblind.atlassian.net/browse/ATT-6743)  
**Sub-task**: [ATT-6785](https://rainerblind.atlassian.net/browse/ATT-6785) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-041` (*Parallel Sprint Execution Protocol for Disjoint Epics*)  
**Test Spec ID**: `TST-PRO-041` | **Branch**: `feature/ATT-6743`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-041)

### 1.1 Problem Statement & Rationale
Prose instructions (`Rule 81`) cannot prevent defect escapes under multi-ticket subagent context load without automated, deterministic tripwires. In addition, living requirement `REQ-PRO-041.1` must synchronize with PO retrospective calibration from `concurrency = 2` to `concurrency = 3` across disjoint Epics, with POSIX `flock` serialization for Gradle, device installations, and squash-merges (`REQ-PRO-041.2`).

### 1.2 Functional & Architectural Requirements
1. **Subagent Worktree Isolation (`REQ-PRO-041.1`)**: Coordinator SHALL orchestrate parallel sprint ticket execution using `Workspace: "share"` with concurrency bounded at at most 3 concurrent tickets across strictly disjoint Parent Epics.
2. **POSIX File Lock Mutual Exclusion (`REQ-PRO-041.2`)**: Tooling SHALL provide mutual exclusion via `/tmp/att_build_merge.lock` for heavy Gradle tests, `installDebug` + `adb`, and Stage 5 squash-merges.
3. **Deterministic Governance Tripwires (`REQ-PRO-041.5`)**: `tools/verify_requirement_governance.py` SHALL enforce:
   - *Zero-Caller Composable Island Check*: Exit 1 if a modified `app/src/main/**/*.kt` file introduces a non-preview, non-private `@Composable` with zero production call sites in `app/src/main/`.
   - *Repository Encapsulation Guard*: Exit 1 if a modified `*Repository.kt` or `*Repository.java` exposes non-private methods/properties returning `*DatabaseManager`, `*DbHelper`, `SQLiteDatabase`, or `Cursor`.
   - *Architecture & System Spec Grounding*: Exit 1 if modified architecture/system markdown files cite non-existent source files or if declared `SYSREQ-*` IDs in `sysreq_*.md` do not equal allocated IDs in `system_architecture.md`.

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-PRO-041` (`REQ-PRO-041.1`, `REQ-PRO-041.2`, `REQ-PRO-041.5`). Targets `tools/verify_requirement_governance.py`, `aspice_governance.md`, and `req_process_stability.md`.
* `Historical Origin & Commit Trace`: Commit `d002a9bd` (`ATT-5914`, Sprint 2026-45.2); calibrated in Sprint 2026-46.1 retro (`ATT-6289` / `Rule 81`).
* `Root Reason for Existing Formulation`: Concurrency was conservatively capped at 2 to validate 'Workspace: share' worktree isolation and avoid host resource exhaustion during the initial rollout.
* `Preservation of Core Invariants`: Bumping concurrency to 3 across disjoint Epics preserves worktree isolation, POSIX `flock` mutual exclusion on `./gradlew` and `adb`, zero cross-epic collisions, and 100% human gate governance.

---

## 2. Test Specification (TST-PRO-041)

### Test Case 1: Composable Island Check (`TST-PRO-041.5a`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Synthesize diff adding `@Composable fun UnmountedCard()` without callers vs. with caller in `MainActivityWithNavigation.kt`.
* **Action**: Invoke `verify_composable_islands(diff_text, repo_root)`.
* **Expected Result**: Returns `(False, ["..."])` on uncalled composable; returns `(True, [])` when call site exists or when function is annotated with `@Preview` or declared `private`.

### Test Case 2: Repository Encapsulation Guard (`TST-PRO-041.5b`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Synthesize diff adding `fun getDbManager(): WorkoutDatabaseManager` to a repository file.
* **Action**: Invoke `verify_repository_encapsulation(diff_text)`.
* **Expected Result**: Returns `(False, ["..."])` for public/internal backdoor getters; returns `(True, [])` for private properties or pure domain DTO returns.

### Test Case 3: Architecture & System Spec Grounding & SYSREQ Parity (`TST-PRO-041.5c`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Preconditions**: Synthesize diff touching `docs/system/sysreq_test.md` with non-existent `NonExistentFile.kt` and missing allocation.
* **Action**: Invoke `verify_architecture_and_system_specs(diff_text, repo_root)`.
* **Expected Result**: Returns `(False, ["..."])` on missing file or `declared != allocated` SYSREQ IDs; returns `(True, [])` on valid references.

### Test Case 4: Master Index Parity & Tooling Suite (`TST-PRO-041.6`)
* **Command**: `python3 tools/test_verify_requirement_governance.py` & `python3 tools/verify_requirement_governance.py`
* **Expected Result**: 100% test pass rate with exit code 0; zero external pip dependencies.

---

## 3. Traceability Matrix

| Test Case | Scope | Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-041.5a` | Unit | `verify_composable_islands` | `REQ-PRO-041.5` | Specified |
| `TST-PRO-041.5b` | Unit | `verify_repository_encapsulation` | `REQ-PRO-041.5` | Specified |
| `TST-PRO-041.5c` | Unit | `verify_architecture_and_system_specs` | `REQ-PRO-041.5` | Specified |
| `TST-PRO-041.1` | Governance | `invoke_subagent(Workspace="share")` | `REQ-PRO-041.1` | Specified |
| `TST-PRO-041.2` | Concurrency | `/tmp/att_build_merge.lock` | `REQ-PRO-041.2` | Specified |
| `TST-PRO-041.6` | Suite | `verify_governance` | `REQ-PRO-041` | Specified |
