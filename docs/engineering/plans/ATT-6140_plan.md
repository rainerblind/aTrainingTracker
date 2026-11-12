# Stage 3: Implementation Plan - ATT-6140: Correct shard count and rename misnamed governance requirement shards

**Ticket**: [ATT-6140](https://rainerblind.atlassian.net/browse/ATT-6140)  
**Sub-task**: `[Impl-Plan]` (Created on submission)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-028` (`REQ-PRO-028.1`, `REQ-PRO-028.3`, `REQ-PRO-028.4`)  
**Test Mapping**: `TST-PRO-021` (`TST-PRO-021.1`, `TST-PRO-021.4`, `TST-PRO-021.5`)  
**Branch**: `subagent-ATT-6140---ATT-6119-Pro-Agent-self-c43e6b96`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
Master index files `docs/requirements.md` and `docs/tests.md` cite "7 domain-sharded... files" in intro prose while listing 9 shards in index tables. Shards `req_project_governance.md` and `tst_project_governance.md` exclusively hold Google Play Store presence (`STP`) requirements (`REQ-STP-001`..`010`), while process rules reside in `req_process_stability.md`. `verify_requirement_governance.py` validates row parity but omits prose count validation, and `shard_living_docs.py` has outdated 7-domain mappings.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-028` (`REQ-PRO-028.1a` shard rename, `REQ-PRO-028.1b` master index 9 shards, `REQ-PRO-028.3a` verifier preamble checks, `REQ-PRO-028.4a` shard_living_docs 9-domain parity).
* **Test Mapping**: `TST-PRO-021` (`TST-PRO-021.1` link/file integrity, `TST-PRO-021.4` governance & build contract execution, `TST-PRO-021.5` tooling unit tests).

---

## 3. System Invariants & Preserved Behavior
1. **Zero ID Loss**: All 1048 `REQ-*` and 1038 `TST-*` entries preserved with 100% bidirectional links.
2. **Deterministic Exit Codes**: `verify_requirement_governance.py` exits 0 on valid parity, 1 on mismatch.
3. **Runtime Invariance**: Zero modifications to production source code in `app/src/main/`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for human user.

---

## 4. Proposed Architectural Changes

### Component 1: Living Documentation Shards & Master Indexes
* Rename `docs/requirements/req_project_governance.md` -> `req_store_presence.md` and `docs/tests/tst_project_governance.md` -> `tst_store_presence.md`. Update headers (`Domain: store_presence`, `Total: 10`).
* In `docs/requirements.md` and `docs/tests.md`: Update intro line to "9 domain-sharded... files", and table row 9 to `[Store Presence]` (`req_store_presence.md` / `tst_store_presence.md`).
* In `docs/architecture.md`: Update SWE.2 allocation row 9 to `Store Presence`.

### Component 2: Tooling Governance & Sharding Parity
* In `tools/verify_requirement_governance.py`: In `verify_master_table_parity`, assert preamble stated count ("links to the N domain-sharded") and summary count ("across N domain shards") equal `len(shard_counts)`. In `recalculate_master_index_tables`, update numbers dynamically.
* In `tools/jira_util.py`: Align `recalculate_master_index_tables` to preserve/update preamble and summary shard counts.
* In `tools/shard_living_docs.py`: Update `DOMAINS` catalog to 9 shards; map `STP` exclusively to `store_presence`; add `aftermath_reviews` (`REV`); update master index templates to 9 shards.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Shard Renaming & Header Normalization
* Files: `docs/requirements/req_store_presence.md`, `docs/tests/tst_store_presence.md`
* Actions: `git mv docs/requirements/req_project_governance.md docs/requirements/req_store_presence.md` and `git mv docs/tests/tst_project_governance.md docs/tests/tst_store_presence.md`. Update headers (`# Living Requirements/Tests: Store Presence`, `**Domain**: store_presence`, `**Total Requirements/Tests**: 10`).

### Step 2: Master Index Preamble & Table Row Alignment
* Files: `docs/requirements.md`, `docs/tests.md`
* Actions: Change "7 domain-sharded" to "9 domain-sharded" in line 3. Update table row 9 to `[Store Presence]` referencing `req_store_presence.md` and `tst_store_presence.md`.

### Step 3: Normative Requirement & Architecture Parity
* Files: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, `docs/architecture.md`
* Actions: In `REQ-PRO-028.1` & ACs, update text to 9 domain-specific files including `req_store_presence.md`. In `docs/architecture.md`, update row 9 to `Store Presence`.

### Step 4: Governance Verifier & Tooling Synchronization
* Files: `tools/verify_requirement_governance.py`, `tools/jira_util.py`, `tools/shard_living_docs.py`
* Actions: Enhance `verify_master_table_parity` with preamble/summary shard count check; sync recalculation in `jira_util.py`; update `DOMAINS` in `shard_living_docs.py`.

### Step 5: Unit Test Coverage & Verification Run
* Files: `tools/test_verify_requirement_governance.py`
* Actions: Add unit test scenario asserting preamble shard count mismatch fails with code 1. Run unit suite, governance check, and Play Store contract test.
* Commands: `python3 -m unittest tools/test_verify_requirement_governance.py && python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.PlayStoreListingContractTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Unit test suite in `tools/test_verify_requirement_governance.py` (100% pass), `verify_requirement_governance.py` exit code 0, Gradle build contract tests.
* **Rollback**: Atomic git working branch isolation allows `git reset --hard HEAD` revert with zero impact on other branches.
