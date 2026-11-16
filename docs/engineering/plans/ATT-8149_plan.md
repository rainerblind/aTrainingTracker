# Stage 3: Implementation Plan - ATT-8149: Reserve REQ and TST IDs atomically across parallel sprint lanes

**Ticket**: [ATT-8149](https://rainerblind.atlassian.net/browse/ATT-8149)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.2`  
**Requirement Mapping**: `REQ-PRO-106` (`REQ-PRO-106.1`..`REQ-PRO-106.4`)  
**Test Mapping**: `TST-PRO-106` (`TST-PRO-106.1`..`TST-PRO-106.4`)  
**Branch**: `feature/ATT-8149` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
In multi-worktree sprint execution (`REQ-PRO-041`), parallel lanes (`Workspace: "share"`) operate on isolated working directories. Static regex scans of local living docs are blind to unmerged concurrent ID allocations in sibling lanes, causing duplicate ID allocations (e.g. `ATT-7758` vs `ATT-7999` in sprint `2026-46.7` allocating `REQ-SET-107`/`TST-SET-109`). A shared lock-protected reservation ledger in the common git directory (`.git/`) and automated Gate 2 provenance enforcement are required.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-106.1` $\rightarrow$ `TST-PRO-106.1`**: Common-dir shared ledger (`$(git rev-parse --git-common-dir)/att_id_reservations.json`) under POSIX `flock` on `/tmp/att_id_reservation.lock` for atomic cross-worktree reservation.
* **`REQ-PRO-106.2` $\rightarrow$ `TST-PRO-106.2`**: Monotonic ID allocation taking `max(committed, reserved) + 1` across domain shards and active reservations.
* **`REQ-PRO-106.3` $\rightarrow$ `TST-PRO-106.3`**: Gate 2 verification in `gate_verdict` enforcing deliverable IDs are reserved for the parent ticket.
* **`REQ-PRO-106.4` $\rightarrow$ `TST-PRO-106.4`**: Lifecycle release (`release-id`), TTL expiration, and zero false positives in `verify_requirement_governance.py --check-code-orphans`.

---

## 3. System Invariants & Preserved Behavior
1. **Multi-Worktree Ledger Sharing**: Common git dir resolution via `git rev-parse --path-format=absolute --git-common-dir` ensures all worktrees access the same ledger without dirtying the git working tree.
2. **Build/Merge Lock Independence**: Reservation operations use dedicated `/tmp/att_id_reservation.lock`, avoiding contention with `/tmp/att_build_merge.lock`.
3. **Zero Code Orphans**: Test code and mocks use dummy prefix patterns (`REQ-XXX-*`, `TST-XXX-*`) to prevent orphan alerts in `--check-code-orphans`.
4. **Human Decision Gate**: Rule 3 parent ticket closure remains strictly reserved for the human user.

---

## 4. Proposed Architectural Changes
* **Reservation Engine (`tools/jira_util.py`)**:
  - `resolve_shared_ledger_path()`: Resolves absolute common git dir via `git rev-parse --path-format=absolute --git-common-dir` with fallback to `.git`.
  - `reserve_id(domain, id_type, ticket, count=1, ttl_days=7)`: Locks `/tmp/att_id_reservation.lock`, cleans expired entries, computes `max(committed, reserved) + 1`, and atomically updates `att_id_reservations.json`.
  - `release_id(ticket, id_str=None)`: Releases active reservations for a ticket.
  - `list_reservations(domain=None, ticket=None)`: Formats active reservations.
* **Gate 2 Provenance Enforcement (`tools/jira_util.py`)**:
  - In `gate_verdict` on `[Req & Test Spec]` subtasks, extract net-new `REQ-*`/`TST-*` IDs and assert each is reserved for the parent ticket.
* **UI Consistency**: No UI changes (strictly developer tooling and CLI governance).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (Atomic Reservation Engine)**: Implement `resolve_shared_ledger_path()`, `reserve_id()`, `release_id()`, `list_reservations()`, and CLI commands (`reserve-id`, `release-id`, `list-reservations`) in `tools/jira_util.py`.
* **Step 2 (Gate 2 Provenance Check)**: Add `verify_stage2_id_reservations(subtask_key, text, role)` in `gate_verdict` for `[Req & Test Spec]` subtasks.
* **Step 3 (Audit Trail Correction on ATT-7758)**: Post an audit correction comment on `ATT-7758` / `ATT-8075` explaining the historical renumbering to `REQ-PER-040` / `TST-PER-048`.
* **Step 4 (Targeted Unit Tests)**: Add test suite `TestIdReservationGovernance` in `tools/test_jira_accounts.py` covering multi-process concurrency, monotonic allocation, Gate 2 rejection, release hygiene, and code-orphan immunity.
  - Test Command: `python3 -m unittest tools/test_jira_accounts.py`
* **Step 5 (Living Documentation Promotion & Verification)**: Update `REQ-PRO-106` and `TST-PRO-106` to `[Implemented]` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`, running `python3 tools/verify_requirement_governance.py --sync-indexes`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `tools/test_jira_accounts.py`, test concurrent CLI invocations under lock, and run `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Atomic Git revert of `feature/ATT-8149`; isolated `.git/att_id_reservations.json` can be cleanly purged.
