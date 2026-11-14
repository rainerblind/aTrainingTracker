# Stage 2 Test Specification: ATT-7565

**Ticket:** ATT-7565 (`[Verbesserung] Slim governance rules into normative rules and history archive, cap shard size`)  
**Parent Epic:** ATT-232 (*Process*) | **Sprint:** 2026-46.6 | **Target Release:** `V5.0.0` | **Story Points:** 3 SP  
**Role:** `agent1` (Subagent Flash) / Review: `agent2` (Subagent Pro) | **Branch:** `feat/ATT-7565` | **Status:** In Progress (Stage 2)

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-093` | `TST-PRO-093` | Unit / Review | `.agents/rules/aspice_governance.md`, `tools/shard_living_docs.py`, `tools/test_shard_living_docs.py` | Planned |

---

## 2. Requirement Specification (`REQ-PRO-093`)

### REQ-PRO-093: Normative Governance Rules Restructuring and Living Document Shard Size Capping [Planned]
- **Tests**: `TST-PRO-093`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `.agents/rules/aspice_governance.md`, `tools/hoechstmass/.agents/rules/aspice_governance.md`, `docs/engineering/rule_history_archive.md`, `tools/shard_living_docs.py`, `tools/test_shard_living_docs.py`
- **Rationale**: Under ASPICE PA 3.1 and evaluation finding F8, process governance rules must be concise, normative (who/what/when), and free of embedded ticket quotes, retrospective stories, and superseded contradictions, while preserving a permanent historical cross-reference archive. Furthermore, living documentation shards must have an enforced maximum line limit and automated tooling checks to flag and partition oversized shards.
- **Description**:
  1. *Normative Rule Consolidation (`REQ-PRO-093.1`)*: `.agents/rules/aspice_governance.md` and `tools/hoechstmass/.agents/rules/aspice_governance.md` SHALL define the 9 Core Rules strictly as concise normative requirements with stable sub-rule IDs (`Rule 1.1`–`Rule 9.5`), removing all inline ticket quotes and historical anecdotes.
  2. *Rule History & Rationale Archive (`REQ-PRO-093.2`)*: `docs/engineering/rule_history_archive.md` SHALL maintain the complete historical mapping of Rules 1–84+, capturing historical origin commits, retrospective rationales, and exact quotes.
  3. *Shard Size Limit & Tooling Check (`REQ-PRO-093.3`)*: `tools/shard_living_docs.py` SHALL define a default maximum shard size (`DEFAULT_MAX_SHARD_LINES = 4000`), provide a `--check-size` inspection CLI, and flag any requirement shard that exceeds the configured limit.
  4. *Oversized Shard Partitioning Facility (`REQ-PRO-093.4`)*: `tools/shard_living_docs.py` SHALL provide automated partitioning facilities (`split_oversized_shard`) to split oversized requirement shards into compliant sub-shards while strictly preserving 100% ID uniqueness, master index table parity, and bidirectional traceability.
  5. *Governance Contract Invariance (`REQ-PRO-093.5`)*: All existing governance contract tests (`AspiceSweAuditContractTest.kt`, `HoechstmassCoreFrameworkContractTest.kt`, `test_agent_emulator.py`, `test_verify_requirement_governance.py`) SHALL pass with 100% success.

**Acceptance Criteria (Given-When-Then)**:
- *Given* `.agents/rules/aspice_governance.md`,
- *When* inspected,
- *Then* each rule consists of concise normative clauses with stable IDs, and retrospective quotes/history live in `docs/engineering/rule_history_archive.md`.
- *Given* a requirement shard exceeding the maximum line limit,
- *When* `tools/shard_living_docs.py --check-size` is executed,
- *Then* the tool flags the oversized shard with line counts.
- *Given* the automated test suite,
- *When* `python3 tools/test_shard_living_docs.py` and `python3 tools/verify_requirement_governance.py` are executed,
- *Then* all tests pass with exit code 0.

**Invariants**: Zero Android runtime code changes; existing master table parity intact; Human Decision Gate inviolable.

---

## 3. Test Specification (`TST-PRO-093`)

### TST-PRO-093: Governance Rules Normative Structure and Shard Size Capping Enforcement Test [Planned]
- **Verifies**: `REQ-PRO-093`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/shard_living_docs.py`, `tools/test_shard_living_docs.py`, `.agents/rules/aspice_governance.md`, `docs/engineering/rule_history_archive.md`
- **Jira**: `ATT-7565`
- **Test Procedures**:
  1. *Shard Size Verification Test (`tools/test_shard_living_docs.py`)*: Assert `check_shard_sizes()` flags temporary shards exceeding `max_lines` and passes for shards within limits.
  2. *Oversized Shard Splitting Test (`tools/test_shard_living_docs.py`)*: Verify `split_oversized_shard()` partitions an oversized shard into sub-shards with zero ID loss or format corruption.
  3. *Governance Contract Preservation*: Run `test_verify_requirement_governance.py`, `test_agent_emulator.py`, and `AspiceSweAuditContractTest.kt`, asserting all tests pass without regression.
  4. *Living Documentation Parity*: Execute `python3 tools/verify_requirement_governance.py` confirming 100% master table parity and zero dangling references.
