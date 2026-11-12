# Stage 1 Analysis: ATT-6140 - Correct shard count and rename misnamed governance requirement shards

**Ticket**: [ATT-6140](https://rainerblind.atlassian.net/browse/ATT-6140)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Branch**: `subagent-ATT-6140---ATT-6119-Pro-Agent-self-c43e6b96`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Master indexes in `docs/requirements.md` and `docs/tests.md` state *"This master index links to the 7 domain-sharded... files"*, yet both index tables list 9 shards. Furthermore, `req_project_governance.md` and `tst_project_governance.md` exclusively contain Google Play Store (`STP`) requirements (`REQ-STP-001`..`010`), while process governance (`REQ-PRO-*`) resides in `req_process_stability.md`. The verifier currently checks table row parity but does not validate prose shard counts.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * Monolithic docs were split into 7 domains in ATT-4594. Later sprints added `req_aftermath_reviews.md` (`REV`) and `req_project_governance.md` (`STP`), expanding to 9 shards without updating intro prose.
  * `req_project_governance.md` was misnamed at inception (ATT-4853); it contains store presence, screenshot contracts, and release packaging, not project process rules.
  * `tools/verify_requirement_governance.py` checks table rows but has a blind spot for prose shard counts. `tools/shard_living_docs.py` still defines only 7 domains.
* **Independent Peer Review & Verification**:
  * Live codebase audit confirms `req_project_governance.md` contains 10 `STP` requirements (`REQ-STP-001`..`010`) and zero `REQ-PRO` requirements.
  * Audit of callers in `app/src/test/` confirms zero tests reference `req_project_governance.md` directly.
  * Live verification: `docs/requirements/` and `docs/tests/` each hold exactly 9 markdown files.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Rename shards: `docs/requirements/req_project_governance.md` -> `req_store_presence.md` and `docs/tests/tst_project_governance.md` -> `tst_store_presence.md`. Update domain header to `store_presence`.
  2. Update master index files `docs/requirements.md` and `docs/tests.md` (shard links, domain title "Store Presence", and intro text "9 domain-sharded").
  3. Update `tools/verify_requirement_governance.py` to assert that prose stated shard counts match actual filesystem counts.
  4. Update `tools/shard_living_docs.py` `DOMAINS` and templates to all 9 domains.
  5. Align requirement allocation table in `docs/architecture.md` (SWE.2).
* **Out-of-Scope Non-Goals**:
  * No modification to requirement or test semantics (`REQ-STP-001`..`010`).
  * No modification to `docs/store_presence/*.md` or production code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-028` (*Living Documentation Sharding, Multi-Line Markdown Section Formatting & Dual-Format Tooling Governance*). Targets `docs/requirements/`, `docs/tests/`, `docs/requirements.md`, `docs/tests.md`, and `tools/verify_requirement_governance.py`.
* **Historical Origin & Commit Trace**: Established in Sprint 2026-44.1 via `ATT-4594` (commit `6828df96`). Refined by `ATT-5431` (`REQ-PRO-034`), which recognized 9 shards for status parity but left `REQ-PRO-028.1` with historical "7 domain-specific files" wording.
* **Root Reason for Existing Formulation**: Sharding was initialized with 7 files. Subsequent domain shard additions (`REV`, `STP`) neglected to increment the hardcoded clause in `REQ-PRO-028.1`.
* **Preservation of Core Invariants**: 100% ID preservation (1048 REQs, 1038 TSTs), bidirectional traceability, and automated verification pass rate remain fully preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Shard Migration**: `git mv` `req_project_governance.md` -> `req_store_presence.md` and `tst_project_governance.md` -> `tst_store_presence.md`. Update `# Living Requirements: Store Presence`, domain to `store_presence`, and total count header to `10`.
2. **Master Indexes**: Update rows to `[Store Presence](requirements/req_store_presence.md)` and `[Store Presence](tests/tst_store_presence.md)`. Correct intro sentence to *"links to the 9 domain-sharded... files"*.
3. **Requirement Formalization**: Amend `REQ-PRO-028.1` in `req_process_stability.md` to specify 9 shards including `req_store_presence.md`.
4. **Governance Tooling**: In `verify_requirement_governance.py`, add validation for prose stated shard count against `len(shard_counts)`. Keep `recalculate_master_index_tables()` synchronized.
5. **Tooling & Architecture Sync**: Update `DOMAINS` in `shard_living_docs.py` and domain row in `docs/architecture.md`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% ID preservation across all living requirements and tests with zero broken links.
  2. `python3 tools/verify_requirement_governance.py` passes cleanly (exit code 0).
  3. Zero changes to production runtime code (`app/src/main/`).
* **Risk Rating**: **LOW** — Isolated documentation renames, index corrections, and governance validation enhancement.
