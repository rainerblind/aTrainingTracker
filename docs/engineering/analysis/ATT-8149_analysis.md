# Stage 1 Analysis: ATT-8149 - Reserve REQ and TST IDs atomically across parallel sprint lanes

**Ticket**: [ATT-8149](https://rainerblind.atlassian.net/browse/ATT-8149)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.2`  
**Branch**: `feature/ATT-8149` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Audit 2026-11-15 Finding `Minor (Jira sample ATT-7758)` (SWE.1 BP1, SUP.8 BP4), parallel sprint execution across isolated git worktrees (`REQ-PRO-041`) introduces specification-time ID collision hazards:
1. **Worktree Isolation**: Each lane operates on an isolated filesystem worktree (`Workspace: "share"`). Local living documentation scans (`docs/requirements/*.md`, `docs/tests/*.md`) have zero visibility into concurrent unmerged ID allocations in sibling lanes.
2. **Incident Evidence (Sprint 2026-46.7)**: Lane 1 (`ATT-7999`, Swimming) and Lane 2 (`ATT-7758`, RouteClusters) simultaneously allocated `REQ-SET-107` and `TST-SET-109`. Gate 2 on `ATT-8075` approved `REQ-SET-107`/`TST-SET-109`. Subsequently, `ATT-7758` was silently renumbered to `REQ-PER-040`/`TST-PER-048` before merge, leaving the Jira Gate 2 record inconsistent and undocumented.
3. **Missing Enforcement**: Gate 2 does not verify ID reservation or ownership, allowing uncoordinated IDs to pass audit.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Local Scan Blindness)**: Agents determine next IDs via local regex scans (`max(id) + 1`). Because worktree files are decoupled until Stage 5 merge, parallel lanes compute identical candidate IDs.
* **RCA 2 (No Shared Reservation Ledger)**: `parallel_sprint_lock.py` serializes Gradle runs and Stage 5 merges, but no shared lock or ledger exists for Stage 2 ID allocation.
* **RCA 3 (Gate 2 Enforcement Gap)**: `gate-verdict` on `[Req & Test Spec]` subtasks validates zero ID provenance.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited Jira `ATT-8075` (Gate 2 of `ATT-7758`): Description and Agent 2 verdict explicitly approved `REQ-SET-107` and `TST-SET-109`. Commit `608c4e31` actually merged `REQ-PER-040` / `TST-PER-048`. The collision and undocumented drift are verified.
  * Audited `git rev-parse --git-common-dir`: All worktrees resolve to `/home/rainer/AndroidStudioProjects/aTrainingTracker/.git`. Storing reservations in a shared file under the common git dir (`att_id_reservations.json`) guarantees instantaneous cross-worktree visibility.
  * Audited `tools/verify_requirement_governance.py`: `--check-code-orphans` scans `.kt`, `.java`, `.xml`, `.py`. A JSON file (`tools/id_reservations.json`) will not trigger code-orphan failures, but test/tool regexes must avoid referencing uncommitted literal IDs.
  * Next IDs on HEAD `598255cc`: Max `REQ-PRO-105` / `TST-PRO-105`. Next available are `REQ-PRO-106` / `TST-PRO-106`. Per `REQ-PRO-104.3`, `- **Description**:` is mandatory.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `jira_util.py reserve-id <DOMAIN> <REQ|TST|BOTH> <TICKET> [--count N]` under POSIX `flock` on `/tmp/att_id_reservation.lock`, persisting atomically to common git dir (`.git/att_id_reservations.json`) and syncing `tools/id_reservations.json`.
  2. Add Gate 2 check in `tools/jira_util.py`: verify all net-new `REQ-*`/`TST-*` IDs in Stage 2 deliverables are reserved for that parent ticket.
  3. Post an audit correction comment on `ATT-7758` / `ATT-8075` explaining the historical renumbering.
  4. Specify `REQ-PRO-106` / `TST-PRO-106` in living documentation with mandatory `- **Description**:`.
  5. Add unit tests for atomic reservation and Gate 2 enforcement in `tools/test_jira_accounts.py`.
* **Out-of-Scope Non-Goals**:
  - Rewriting git commit history or changing git worktree topology.
  - Renumbering already-merged requirements across earlier sprints.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-041` (Parallel sprint execution / `parallel_sprint_lock.py`) and `REQ-PRO-075` (Syntax governance) via net-new `REQ-PRO-106`.
* **Historical Origin & Commit Trace**: `ATT-6163` (`REQ-PRO-041`), `ATT-6129` (`REQ-PRO-075`), `ATT-8148` (`REQ-PRO-104`).
* **Root Reason for Existing Formulation**: `parallel_sprint_lock.py` serialized build/merge phases but omitted early Stage 2 ID allocation.
* **Preservation of Core Invariants**: Existing build/merge locks remain untouched; non-reserved legacy IDs in base continue to pass; `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-106` / `TST-PRO-106` in `req_process_stability.md` & `tst_process_stability.md`.
* **Affected Architecture Elements**: `tools/jira_util.py`, `tools/test_jira_accounts.py`, `tools/id_reservations.json`, and Jira issues `ATT-7758`/`ATT-8075`.
* **Verification Impact & Test Surface**: `test_jira_accounts.py` (concurrency & Gate 2 validation), `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** (`2 SP`, developer tooling & governance; zero Android runtime impact; Rule 3 human gate preserved).

---

## 6. Architectural Strategy & High-Level Solution
* Implement `reserve_id(domain, id_type, ticket_key, count=1)` in `tools/jira_util.py` under POSIX `flock` (`/tmp/att_id_reservation.lock`).
* Read existing IDs from living docs (`docs/requirements/*.md`, `docs/tests/*.md`) and shared ledger (`.git/att_id_reservations.json`), allocate `max + 1`, and update the ledger.
* In `gate_verdict` for `[Req & Test Spec]` subtasks, extract newly introduced IDs and verify ownership in the ledger before allowing `PASS`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) Atomic reservation uniqueness across concurrent processes; (2) Zero code orphans in `verify_requirement_governance.py`; (3) Human Gate 3 and Gate 6 invariants preserved.
* **Risk Rating**: **LOW** (Dev/process tooling only; zero APK runtime code changes).
