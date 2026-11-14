# Stage 1 Analysis: ATT-7565

**Ticket:** ATT-7565 (`[Verbesserung] Slim governance rules into normative rules and history archive, cap shard size`)  
**Parent Epic:** ATT-232 (*Process*) | **Sprint:** 2026-46.6 | **Target Release:** `V5.0.0` | **Story Points:** 3 SP  
**Role:** `agent1` (Subagent Pro) | **Branch:** `feat/ATT-7565` | **Status:** In Progress (Stage 1)

---

## 1. Problem Statement & Motivation
- **ASPICE F8 Finding**: `.agents/rules/aspice_governance.md` packs 80+ numbered rules, historical ticket quotes, and retrospective anecdotes into long paragraphs across 9 Core Rules. This makes auditing difficult and causes process drift under ASPICE PA 3.1.
- **Requirement Shard Bloat**: Requirement shards have grown massive (e.g., `req_tracking_cockpit.md` > 4,800 lines, `req_ui_design_system.md` > 17,000 lines) with no size cap or automated tooling check to flag oversized shards.

## 2. Root Cause & Independent Hypothesis Verification
- **Audit of `.agents/rules/aspice_governance.md`**: Embedded ticket quotes (e.g. `"Why are there so many tickets left in Implementation?"`, `"Tickets sollten von den Agenten erst dann nach Analyse geschoben werden..."`) and legacy cross-references (`Rule 81 — ATT-6289`, `Rule 66 — ATT-4864`) clutter normative governance statements.
- **Audit of `docs/engineering/rule_history_archive.md`**: Already hosts legacy rules 1–84, but needs complete synchronization so all retrospective quotes and historical rationales reside in the archive while normative rules remain concise.
- **Audit of `tools/shard_living_docs.py`**: Sharding tool lacks shard size limits, size-checking commands, and partitioning facilities for oversized shards.
- **Hypothesis Verification**: Verified against sprint codebase. De-cluttering governance rules into normative clauses (Rule 1.1–9.5) and capping shard size in tooling satisfies ASPICE PA 3.1 without breaking any existing contract tests (`AspiceSweAuditContractTest.kt`, `HoechstmassCoreFrameworkContractTest.kt`).

## 3. User Scope Grounding (ATT-1250)
- **In-Scope**:
  1. Restructure `.agents/rules/aspice_governance.md` and `tools/hoechstmass/.agents/rules/aspice_governance.md` into normative clauses with stable IDs, moving rationale/history to `docs/engineering/rule_history_archive.md`.
  2. Implement shard size capping (`DEFAULT_MAX_SHARD_LINES = 4000`), `--check-size` mode, and oversized shard partitioning tooling in `tools/shard_living_docs.py`.
  3. Author automated test coverage in `tools/test_shard_living_docs.py`.
- **Out-of-Scope**: Altering Android runtime code in `app/src/main/` or modifying disjoint active living-doc shards during parallel sprint execution.

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Target**: Net-new requirement `REQ-PRO-093` refining `REQ-PRO-026` (*Document Triad Architecture*), `REQ-PRO-027` (*Agile Agentic ASPICE Governance*), and `REQ-PRO-028` (*Living Documentation Sharding*).
- **Historical Origin**: `ATT-3323` (commit `6925d864`), `ATT-3534` (commit `39b3df08`), `ATT-4594` (commit `06990c88`).
- **Root Reason**: Incremental retrospective rules accumulated in-line notes to preserve context; now consolidated into the permanent history archive.
- **Invariants**: Human decision gates, two-tier device policy, 100% contract test pass rate, and zero runtime regression preserved.

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Net-new living requirement `REQ-PRO-093` (`docs/requirements/req_process_stability.md`).
- **Affected Architecture Elements**: `.agents/rules/aspice_governance.md`, `tools/hoechstmass/.agents/rules/aspice_governance.md`, `docs/engineering/rule_history_archive.md`, `tools/shard_living_docs.py`, and `tools/test_shard_living_docs.py`.
- **Verification Impact & Test Surface**: `TST-PRO-093`, `tools/test_shard_living_docs.py`, `tools/verify_requirement_governance.py`, and `AspiceSweAuditContractTest.kt`.
- **Risk Assessment & Invariants**: Low risk. Tooling and documentation only; zero Android runtime changes.

## 6. Architectural Strategy
- Codify stable normative sub-rule identifiers (`Rule 1.1` to `Rule 9.5`) in `aspice_governance.md`.
- Ensure `AspiceSweAuditContractTest.kt` string assertions (`read_file`, `Documentation Code-Grounding`, `SWE.2`, `Two-Tier Device Policy`, `emulator-5554`) remain strictly satisfied.
- Enhance `shard_living_docs.py` with `DEFAULT_MAX_SHARD_LINES = 4000`, `check_shard_sizes()`, `--check-size`, and `split_oversized_shard()` functionality.

## 7. System Invariants & Risk Assessment
- **Core Invariants**: Zero regression in unit tests; existing living-doc master parity intact; Human Decision Gate inviolable.
- **Risk Rating**: **LOW** (process governance and tooling enhancement).
