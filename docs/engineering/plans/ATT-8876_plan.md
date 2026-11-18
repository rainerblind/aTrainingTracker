# Stage 3: Implementation Plan - ATT-8876: Fix Cross-Directory `parallel_sprint_lock` Shadowing & Höchstmaß Trailer Parity

**Ticket**: [ATT-8876](https://rainerblind.atlassian.net/browse/ATT-8876)  
**Sub-task**: [TBD](https://rainerblind.atlassian.net/browse/ATT-8876) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-18_02_Spät`  
**Requirement Mapping**: `REQ-PRO-108` (`REQ-PRO-108.3`)  
**Test Mapping**: `TST-PRO-108` (`TST-PRO-108.3`)  
**Branch**: `feature/ATT-8876`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-18  

---

## 1. Problem Description & Background
- Combined Tier-2 Python runs failed with `AttributeError: module 'parallel_sprint_lock' has no attribute 'format_commit_with_trailers'` at `test_tst_pro_108_3_git_commit_trailers_formatting` (`tools/test_jira_accounts.py:2215`) due to: (a) missing `REQ-PRO-108.3` commit-trailer support in `tools/hoechstmass/tools/parallel_sprint_lock.py`, and (b) bare `import parallel_sprint_lock` colliding with `sys.modules["parallel_sprint_lock"]` / `sys.path[0]` from `tools/hoechstmass/tools/`.

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-108` (`REQ-PRO-108.3`, `docs/requirements/req_process_stability_part2.md`)
* **Test Mapping**: `TST-PRO-108` (`TST-PRO-108.3`, `docs/tests/tst_process_stability_part2.md`)
* **SYS.2 / SYS.3 Impact (`REQ-PRO-131.4`)**: None (`tools/` & `tools/hoechstmass/tools/` only; zero `app/`, `docs/system_*`, or `docs/architecture/` changes).

## 3. System Invariants & Preserved Behavior
1. **Project-Agnostic Höchstmaß Contract**: Zero `ATT-`/proprietary IDs and stdlib-only imports in `tools/hoechstmass/tools/parallel_sprint_lock.py` (`HoechstmassToolingContractTest`).
2. **Android & Submodule Non-Impact**: Zero `app/` source changes; `ANT-Android-SDKs` untouched at `160000`; 4 pre-existing untracked `tools/` files untouched.
3. **POSIX Flock & CLI Parity**: Preserves `REQ-PRO-041.2` flock locking, exit-code propagation, and `REQ-PRO-108.1`–`108.4` invariants.

## 4. Proposed Architectural Changes
* **Component 1 (`tools/hoechstmass/tools/parallel_sprint_lock.py`)**: Add `format_commit_with_trailers()`, `validate_commit_trailers()`, and `agent_role`/`agent_model`/`reviewed_by` plumbing in `_merge_ticket_impl()`, `run_merge_ticket()`, and `main()` using generic defaults (`"agent1"`, `"flash"`, `"Agent 2 (Reviewer)"`).
* **Component 2 (Sibling-Scoped Loading & `sys.path` Hygiene)**: Replace bare `parallel_sprint_lock` imports in `tools/test_jira_accounts.py` (`L2214`) and `tools/test_flock_serialization.py` (`L19, L323`) with a small helper checking `__file__` against sibling `tools/parallel_sprint_lock.py` (falling back to `importlib.util.spec_from_file_location`), and clean `hm_tools_dir` from `sys.path` in `tools/test_jira_datacenter.py` (`L32`, matching `tools/test_jira_epics.py:32-35`).
* **UI Consistency**: No UI changes (`N/A` — Python CLI/test tooling only).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Sync Commit Trailer Support to Höchstmaß `parallel_sprint_lock.py`
* Files: `tools/hoechstmass/tools/parallel_sprint_lock.py`
* Changes: Add `format_commit_with_trailers`, `validate_commit_trailers`, `--agent-role`/`--agent-model`/`--reviewed-by` args, and wire `full_commit_msg` in `_merge_ticket_impl` / `run_merge_ticket` (`REQ-PRO-108.3`).

### Step 2: Isolate Sibling `parallel_sprint_lock` Imports & Add Regression Tests
* Files: `tools/test_jira_accounts.py`, `tools/test_flock_serialization.py`, `tools/test_jira_datacenter.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
* Changes: Load sibling `tools/parallel_sprint_lock.py` deterministically; strip `hm_tools_dir` from `sys.path` in `test_jira_datacenter.py`; add `test_tst_pro_108_3_isolates_cross_directory_sys_modules_cache` in `test_jira_accounts.py` and `test_hoechstmass_parallel_sprint_lock_commit_trailers` in `test_hoechstmass_tools.py`.

### Step 3: Targeted Tests & Living Documentation Promotion
* Commands:
  - `python3 -m unittest -v tools.test_jira_accounts tools.test_flock_serialization tools.test_jira_datacenter`
  - `python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py" -v`
  - Combined single-process cross-directory check (`tools.hoechstmass.tools.test_hoechstmass_tools` + `tools.test_jira_accounts` + `tools.test_flock_serialization` + `tools.test_jira_datacenter`)
  - `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest"`
* Docs: Promote `REQ-PRO-108` / `TST-PRO-108` to `[Implemented]` and run `verify_requirement_governance.py --repo-root . --sync-indexes`.

## 6. Verification & Rollback Plan
* **Verification**: All isolated, Höchstmaß, combined cross-directory Python suites, and `HoechstmassToolingContractTest` exit `0`.
* **Rollback**: Branch isolation on `feature/ATT-8876` enables single-branch revert without impacting `sprint/2026-11-18_02_Spät` or `develop`.
