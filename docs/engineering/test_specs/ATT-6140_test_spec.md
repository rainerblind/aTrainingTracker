# Stage 2: Requirement & Test Specification - ATT-6140: Correct shard count and rename misnamed governance requirement shards

**Ticket**: [ATT-6140](https://rainerblind.atlassian.net/browse/ATT-6140)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-028` (*Living Documentation Sharding, Multi-Line Markdown Section Formatting & Dual-Format Tooling Governance*)  
**Test Spec ID**: `TST-PRO-021`  
**Branch**: `subagent-ATT-6140---ATT-6119-Flash-Agent-self-23156d9c`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-PRO-028`)

### 1.1 Problem Statement & Rationale
`docs/requirements.md` and `docs/tests.md` master indices state in their intro text: *"This master index links to the 7 domain-sharded... files"*, yet both index tables list 9 shards. In addition, `req_project_governance.md` and `tst_project_governance.md` exclusively specify Google Play Store presence (`STP`) requirements and tests (`REQ-STP-001`..`010`), while process stability requirements (`REQ-PRO-*`) reside in `req_process_stability.md`. Currently, `tools/verify_requirement_governance.py` validates table row parity but has a blind spot for prose shard counts in preambles and summary lines. Furthermore, `tools/shard_living_docs.py` has an outdated 7-domain list where `STP` was misassigned to `tracking_cockpit`.

### 1.2 Functional & Architectural Requirements
1. **Shard Renaming & Domain Correction (`REQ-PRO-028.1a`)**: Shard files `docs/requirements/req_project_governance.md` and `docs/tests/tst_project_governance.md` SHALL be renamed to `req_store_presence.md` and `tst_store_presence.md`. The domain header within both files SHALL be updated to `store_presence` and the title to `Store Presence`.
2. **Master Index Shard Count & Link Synchronization (`REQ-PRO-028.1b`)**: `docs/requirements.md` and `docs/tests.md` SHALL state 9 domain-sharded files in intro preambles, link to `requirements/req_store_presence.md` and `tests/tst_store_presence.md` under the name "Store Presence", and reflect 9 domain shards in total summary lines.
3. **Governance Verifier Shard Count Enforcement (`REQ-PRO-028.3a`)**: `tools/verify_requirement_governance.py` (`verify_master_table_parity`) and `tools/jira_util.py` (`recalculate_master_index_tables`) SHALL parse and enforce that stated shard counts in preambles ("links to the N domain-sharded") and summary lines ("across N domain shards") match the actual filesystem count `len(shard_counts)`.
4. **Sharding Script & Domain Mapping Parity (`REQ-PRO-028.4a`)**: `tools/shard_living_docs.py` `DOMAINS` array SHALL define all 9 domain shards (`tracking_cockpit`, `aftermath_periods`, `maps_navigation`, `data_migration_export`, `settings_profiles`, `ui_design_system`, `process_stability`, `aftermath_reviews`, `store_presence`), with `STP` correctly mapped to `store_presence`.
5. **SWE.2 Architecture Document Parity (`REQ-PRO-028.5a`)**: `docs/architecture.md` Living Requirements Allocation table SHALL reference the `Store Presence` domain (`STP`).

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-PRO-028` (`REQ-PRO-028.1`, `REQ-PRO-028.3`). Targets `docs/requirements.md`, `docs/tests.md`, `docs/requirements/`, `docs/tests/`, `tools/verify_requirement_governance.py`, `tools/jira_util.py`, `tools/shard_living_docs.py`, and `docs/architecture.md`.
* `Historical Origin & Commit Trace`: `ATT-4594` (commit `6828df96`, Sprint 2026-44.1) sharded monolithic documentation into 7 domain files. `ATT-5431` (`REQ-PRO-034`) recognized 9 shards for status parity but left prose counts at 7. `req_project_governance.md` was created in `ATT-4853`.
* `Root Reason for Existing Formulation`: Sharding was initialized with 7 files. Subsequent additions of `aftermath_reviews` and `project_governance` (purely store presence `STP`) expanded shards to 9 without updating prose or verifier assertions on prose.
* `Preservation of Core Invariants`: Zero loss of requirement or test IDs (1048 REQs, 1038 TSTs), 100% bidirectional traceability, clean-room test suite passes, zero modifications to runtime production code in `app/src/main/`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Shard Renaming & Clean Links)**:
  * *Given* `docs/requirements/` and `docs/tests/`
  * *When* inspected
  * *Then* `req_store_presence.md` and `tst_store_presence.md` exist containing 10 `STP` requirements/tests, and legacy `*project_governance.md` files no longer exist.
* **AC-2 (Master Index Count & Preamble Parity)**:
  * *Given* `docs/requirements.md` and `docs/tests.md`
  * *When* checked
  * *Then* preambles state 9 domain-sharded files, summary lines state 9 domain shards, and table rows link to `req_store_presence.md` and `tst_store_presence.md`.
* **AC-3 (Automated Verifier Enforcement)**:
  * *Given* `tools/verify_requirement_governance.py`
  * *When* run with mismatched preamble or table shard count
  * *Then* it fails with exit code 1; *When* run against synchronized 9 shards, *Then* it passes with exit code 0.
* **AC-4 (Tooling & Architecture Parity)**:
  * *Given* `tools/shard_living_docs.py` and `docs/architecture.md`
  * *When* inspected
  * *Then* all 9 domains are accurately declared and mapped without orphan prefixes.

---

## 2. Test Specification (`TST-PRO-021`)

### Test Case 1: Domain Shard Structure & Master Index Verification (`TST-PRO-021.1`)
* **Scope**: Living Documentation Inspection
* **Target Files**: `docs/requirements.md`, `docs/tests.md`, `docs/requirements/`, `docs/tests/`
* **Preconditions**: Shard files renamed to `req_store_presence.md` and `tst_store_presence.md`.
* **Action**: Audit filesystem and master tables to confirm 9 shards exist and match table links.
* **Expected Result**: 9 domain files exist in each directory; links to `req_store_presence.md` and `tst_store_presence.md` resolve cleanly.

### Test Case 2: Automated Shard Count Preamble & Verifier Unit Tests (`TST-PRO-021.5`)
* **Scope**: Tooling Unit Test
* **Target File**: `tools/test_verify_requirement_governance.py`
* **Preconditions**: `verify_requirement_governance.py` updated with preamble shard count checks.
* **Action**: Execute `python3 -m unittest tools/test_verify_requirement_governance.py`.
* **Expected Result**: 100% pass rate, asserting that preamble prose shard count mismatches are caught and reported by `verify_master_table_parity`.

### Test Case 3: Tier 1 Targeted Regression & Clean-Room Governance Suite (`TST-PRO-021.4`)
* **Scope**: Governance & Regression Test
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.PlayStoreListingContractTest"`
* **Goal**: Verify requirement governance exits with code 0 and store listing contract tests pass 100%.
* **Expected Result**: Exit code 0, 100% pass rate across living doc governance and contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-021.1` | Living Doc Inspection | File audit across `docs/requirements/` & `docs/tests/` | `REQ-PRO-028.1a`, `REQ-PRO-028.1b` | Specified |
| `TST-PRO-021.5` | Tooling Unit Test | `tools/test_verify_requirement_governance.py` | `REQ-PRO-028.3a`, `REQ-PRO-028.4a` | Specified |
| `TST-PRO-021.4` | Governance / Regression | `tools/verify_requirement_governance.py`, `PlayStoreListingContractTest` | `REQ-PRO-028.5a`, `REQ-PRO-001` | Specified |
