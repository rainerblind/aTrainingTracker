# Stage 3 Implementation Plan: ATT-7565

**Ticket:** ATT-7565 (`[Verbesserung] Slim governance rules into normative rules and history archive, cap shard size`)  
**Parent Epic:** ATT-232 (*Process*) | **Sprint:** 2026-46.6 | **Target Release:** `V5.0.0` | **Story Points:** 3 SP  
**Role:** `agent1` (Subagent Pro) | **Branch:** `feat/ATT-7565` | **Status:** In Progress (Stage 3)

---

## 1. SWE.2 Architecture & Component Decomposition
- **Process Governance Rules (`.agents/rules/aspice_governance.md` & `tools/hoechstmass/...`)**:
  - Restructure the 9 Core Rules into concise, unambiguous normative clauses with stable IDs (`Rule 1.1`–`Rule 9.5`).
  - Strip inline retrospective ticket quotes, anecdotes, and legacy parentheticals, moving them to the archive.
  - Maintain exact compliance with contract tests (`AspiceSweAuditContractTest.kt`, `HoechstmassCoreFrameworkContractTest.kt`, `test_agent_emulator.py`).
- **History Archive (`docs/engineering/rule_history_archive.md`)**:
  - Document complete historical origins, ticket quotes, and rationale for all rules, cross-referencing stable normative rule IDs.
- **Shard Tooling (`tools/shard_living_docs.py` & `tools/test_shard_living_docs.py`)**:
  - Introduce `DEFAULT_MAX_SHARD_LINES = 4000`, `check_shard_sizes()`, and `--check-size` CLI flag.
  - Implement `split_oversized_shard()` for programmatic partitioning of oversized living documentation shards.
  - Add comprehensive automated test cases in `tools/test_shard_living_docs.py`.

---

## 2. Step-by-Step Atomic Implementation Steps

| Step | Action & Target File | Description & Scope | Verification Command |
| :--- | :--- | :--- | :--- |
| **Step 1** | Slim Governance Rules (`.agents/rules/aspice_governance.md`, `tools/hoechstmass/...`) | Refactor into normative statements with stable sub-rule IDs (`Rule 1.1`–`Rule 9.5`); eliminate quotes/stories. | `python3 -m unittest tools/test_agent_emulator.py` |
| **Step 2** | Sync History Archive (`docs/engineering/rule_history_archive.md`) | Consolidate legacy rule rationale, ticket quotes, and retrospective context mapped to normative IDs. | Review diff against `aspice_governance.md` |
| **Step 3** | Capping & Splitting CLI (`tools/shard_living_docs.py`) | Add `DEFAULT_MAX_SHARD_LINES = 4000`, `check_shard_sizes()`, `split_oversized_shard()`, and `--check-size`. | `python3 tools/shard_living_docs.py --check-size` |
| **Step 4** | Automated Unit Tests (`tools/test_shard_living_docs.py`) | Add unit tests for shard size checking, oversized flagging, and compliant section-aware partitioning. | `python3 tools/test_shard_living_docs.py` |
| **Step 5** | Governance Contract Suite | Execute contract tests and verify 100% pass rate with zero regression. | `python3 tools/verify_requirement_governance.py && python3 tools/test_verify_requirement_governance.py` |

---

## 3. System Invariants & Rollback Safety
- **Zero Production Code Changes**: No modifications to `app/src/main/` Android source files.
- **Contract Test Parity**: `AspiceSweAuditContractTest.kt` and `HoechstmassCoreFrameworkContractTest.kt` pass 100%.
- **Traceability Parity**: Master table counts in `docs/requirements.md` and `docs/tests.md` remain strictly synchronized.
- **UI Consistency**: No UI changes (pure process tooling and documentation ticket).
