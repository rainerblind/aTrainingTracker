# Stage 2: Requirement & Test Specification - ATT-8149: Reserve REQ and TST IDs atomically across parallel sprint lanes

**Ticket**: [ATT-8149](https://rainerblind.atlassian.net/browse/ATT-8149)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.2`  
**Requirement Mapping**: `REQ-PRO-106` (*Atomic REQ and TST ID Reservation Across Parallel Sprint Lanes*; extends `REQ-PRO-041`, `REQ-PRO-075`)  
**Test Spec ID**: `TST-PRO-106`  
**Branch**: `feature/ATT-8149` | **Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (REQ-PRO-106)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Target Status: `[Planned]`
* **REQ-PRO-106.1 (Common-Dir Cross-Worktree ID Reservation Registry)**: `tools/jira_util.py` SHALL implement atomic ID reservation (`reserve-id <DOMAIN> <REQ|TST|BOTH> <TICKET_KEY> [--count N]`) backed by POSIX `flock` on `/tmp/att_id_reservation.lock`. Reservations SHALL be persisted to a shared JSON ledger located in the common git directory (`$(git rev-parse --git-common-dir)/att_id_reservations.json`), guaranteeing instantaneous cross-worktree visibility across all concurrent `Workspace: "share"` branches.
* **REQ-PRO-106.2 (Next-ID Calculation with Active Reservation Awareness)**: When allocating next available IDs, the tooling SHALL evaluate both committed living documentation headings across `docs/requirements/*.md` and `docs/tests/*.md` as well as active reservations in the common-dir ledger, assigning strictly greater monotonic IDs (`max(committed, reserved) + 1`).
* **REQ-PRO-106.3 (Gate 2 Reservation Provenance Verification)**: In `gate-verdict` on `[Req & Test Spec]` subtasks, `tools/jira_util.py` SHALL parse all newly introduced `REQ-*` and `TST-*` IDs in the deliverable and verify that each ID is registered to the target parent ticket in the shared reservation ledger, rejecting unreserved IDs to prevent collisions and uncoordinated drift.
* **REQ-PRO-106.4 (Reservation Hygiene & Expiration Management)**: The tooling SHALL support explicit release (`release-id <TICKET_KEY>`) upon ticket completion or cancellation, clean reconciliation upon merge into sprint branches, and stale reservation expiration safeguards, while ensuring reservation files never trigger false-positive code orphan detections under `verify_requirement_governance.py --check-code-orphans`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-041` (Parallel sprint execution / `parallel_sprint_lock.py`) and `REQ-PRO-075` (Syntax governance) via net-new `REQ-PRO-106`. Targets `tools/jira_util.py` and `tools/test_jira_accounts.py`.
* **Historical Origin & Commit Trace**: `ATT-6163` (`REQ-PRO-041`), `ATT-6129` (`REQ-PRO-075`), `ATT-7758` / `ATT-8075` (`608c4e31`), `ATT-8149`.
* **Root Reason for Existing Formulation**: Prior tooling relied exclusively on local static regex scanning of worktree files, leaving concurrent sprint lanes blind to sibling allocations until Stage 5 merge.
* **Preservation of Core Invariants**: Build/merge locks on `/tmp/att_build_merge.lock` remain untouched; existing baselined and legacy requirement numbers remain immutable; `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Cross-Worktree Reservation Ledger)**: *Given* concurrent sprint lanes running in disjoint worktrees, *When* `jira_util.py reserve-id` is called simultaneously for the same domain prefix, *Then* POSIX `flock` serializes access, each lane receives unique non-overlapping monotonic IDs, and the shared common-dir ledger is updated atomically.
* **Criterion 2 (Monotonic Reservation Allocation)**: *Given* committed requirements up to `REQ-PRO-106` and an active reservation for `REQ-PRO-107`, *When* a sibling worktree requests the next ID, *Then* the reservation engine allocates `REQ-PRO-108`.
* **Criterion 3 (Gate 2 Provenance Enforcement)**: *Given* a Stage 2 deliverable containing newly introduced `REQ-*` and `TST-*` IDs, *When* `gate-verdict` executes for `[Req & Test Spec]`, *Then* the audit verifies that all new IDs are properly reserved for the parent ticket, passing if registered and rejecting with an informative error if unreserved.
* **Criterion 4 (Governance & Code-Orphan Immunity)**: *Given* a reservation ledger file, *When* `verify_requirement_governance.py --check-code-orphans` executes, *Then* zero false-positive code orphan violations are reported.

---

## 4. Test Specification (TST-PRO-106)
* **TST-PRO-106.1 (Atomic Cross-Worktree ID Reservation Test)**: Verify `reserve_id()` locks `/tmp/att_id_reservation.lock`, persists reservations to `$(git rev-parse --git-common-dir)/att_id_reservations.json`, guarantees cross-worktree visibility across simulated parallel worktrees, and prevents duplicate allocations under concurrent threads/processes.
* **TST-PRO-106.2 (Monotonic Next-ID Calculation with Active Reservation Awareness Test)**: Verify next ID selection considers both living documentation files and active ledger reservations, correctly producing `max(committed, reserved) + 1` for single and multi-count allocations.
* **TST-PRO-106.3 (Gate 2 Reservation Provenance Verification Test)**: Verify `gate-verdict` on Stage 2 subtasks passes when all new REQ/TST IDs match the parent ticket reservation and rejects with a governance error when an unreserved ID is detected.
* **TST-PRO-106.4 (Reservation Hygiene, Expiration & Code Orphan Parity Test)**: Verify reservation release on ticket lifecycle completion, ledger cleanup, and verify `verify_requirement_governance.py --check-code-orphans` reports zero false positives.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-106.1` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-106.1` | Planned |
| `TST-PRO-106.2` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-106.2` | Planned |
| `TST-PRO-106.3` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-106.3` | Planned |
| `TST-PRO-106.4` | Review / Unit | `tools/test_jira_accounts.py`, `tools/verify_requirement_governance.py` | `REQ-PRO-106.4` | Planned |
