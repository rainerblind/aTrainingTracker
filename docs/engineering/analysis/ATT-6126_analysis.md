# Stage 1 Analysis: ATT-6126 - Introduce evidence-based requirement status model

**Ticket**: [ATT-6126](https://rainerblind.atlassian.net/browse/ATT-6126)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `feature/ATT-6126`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the independent ASPICE 4.0 pre-assessment baseline (develop @ sprint/2026-45.11, Remediation A-07, SWE.1, SWE.6 BP3, GP 2.2), requirement status claims were found to suffer from status inflation: 910 of 917 requirements and 918 of 919 test specifications claimed `[Verified]`, yet 59% of requirements cited no test specification and 273 test specifications (30%) lacked automated test code. "Verified" was ungrounded in concrete test evidence, execution date, or release artifacts.

To achieve ASPICE CL2 work product integrity, the project requires an evidence-based requirement lifecycle:
- `[Planned]` / `[Open]`: Specified, no verified test evidence yet.
- `[Implemented]`: Code exists, but automated test evidence is pending, incomplete, or failing.
- `[Verified]`: Requires $\ge 1$ existing TST specification with passing automated test code or formal verification record.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**:
  1. Sharded living documentation tools (`tools/verify_requirement_governance.py`) historically recognized only binary status: headings containing `[Verified]` were classified as Verified, and all others were defaulted to Planned.
  2. Master index tables (`docs/requirements.md` and `docs/tests.md`) and the contract test (`AspiceSweAuditContractTest.kt`) enforce a 3-column table format (`Total | Verified | Planned / Open`).
  3. Governance verifier lacked validation ensuring that any requirement claiming `[Verified]` actually cites $\ge 1$ authentic, existing TST ID.
* **Independent Peer Review & Verification**:
  - Live codebase audit of `tools/verify_requirement_governance.py` confirms `verify_master_table_parity()` and `recalculate_master_index_tables()` hardcode 3-column tuples `(tot, ver, plan)` and regex matching `\|\s*\d+\s*\|\s*\d+\s*\|\s*\d+\s*\|$`.
  - Codebase scan reveals 546 legacy requirements currently claim `[Verified]` without citing a TST ID. These are scheduled for domain backfilling across tickets ATT-6127..ATT-6135.
  - Therefore, verifier enforcement must strictly reject ungrounded `[Verified]` claims in diffs/net-new requirements and support full-repo gating via `--enforce-evidence` with a legacy allowlist for untouched shards.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Define evidence-based status criteria in process documentation (`docs/project_governance.md` and `.agents/rules/aspice_governance.md`).
  2. Update `tools/verify_requirement_governance.py` to support `[Implemented]` headings and 4-column master table format (`Total | Verified | Implemented | Planned / Open`).
  3. Enforce verifier rule: REQ claiming `[Verified]` must cite $\ge 1$ existing TST ID.
  4. Synchronously update `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt` (`testLivingDocShards_allHeadingsStandardized` and `testMasterTableParity_matchesDomainShards`).
  5. Register `REQ-PRO-073` and `TST-PRO-073` in `req_process_stability.md` and `tst_process_stability.md` with 100% master index parity.
* **Out-of-Scope Non-Goals**:
  1. Backfilling missing test evidence for all 546 legacy requirements (handled in dedicated child tickets ATT-6127..ATT-6135).
  2. Modifying CI release artifact storage (completed in ATT-6137).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirements `REQ-PRO-073` (`REQ-PRO-073.1`–`REQ-PRO-073.4`) and `TST-PRO-073`.
* **Historical Origin & Commit Trace**: Net-new under Epic ATT-6119 (ASPICE CL2). Preceding work: ATT-6122 (commit `687e8388`, traceability coverage reporting) and ATT-6137 (commit `ddd1024a`, release JUnit archival).
* **Root Reason for Existing Formulation**: 3-column master table was established during initial monolithic doc sharding (ATT-4594 / ATT-5431). Expanding to 4 columns represents natural progression to prevent status inflation.
* **Preservation of Core Invariants**: 100% mathematical parity across all 9 domain shards is preserved; existing valid headings remain supported; automated synchronizer (`--sync-indexes`) recalculates all 4 columns deterministically.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new `REQ-PRO-073` (`REQ-PRO-073.1`..`REQ-PRO-073.4`), `TST-PRO-073`.
* **Affected Architecture Elements**:
  - `tools/verify_requirement_governance.py`
  - `tools/test_verify_requirement_governance.py`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`
  - `docs/requirements.md` & `docs/tests.md`
  - `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md`
  - `.agents/rules/aspice_governance.md` & `docs/project_governance.md`
* **Verification Impact & Test Surface**:
  - `python3 tools/test_verify_requirement_governance.py`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`
  - `python3 tools/verify_requirement_governance.py`
* **Risk Assessment & Invariants**: Low risk. Parsing and synchronization logic is fully covered by deterministic unit tests and contract test assertions.

---

## 6. Architectural Strategy & High-Level Solution

1. **Process Documentation**: Author `docs/project_governance.md` and update `.agents/rules/aspice_governance.md` with explicit status definitions and verification criteria.
2. **Governance Tooling (`verify_requirement_governance.py`)**:
   - Parse `[Verified]`, `[Implemented]`, and `[Planned]` / `[Open]`.
   - Update `shard_counts` tuple to `(total, verified, implemented, planned)`.
   - Update `recalculate_master_index_tables()` and `verify_master_table_parity()` for 4-column tables.
   - Implement `verify_requirement_evidence_status()` ensuring `[Verified]` REQs cite $\ge 1$ existing TST ID.
3. **Contract Test (`AspiceSweAuditContractTest.kt`)**:
   - Update regex and assertion logic for 4-column master table parity and `[Implemented]` heading support.
4. **Living Documentation**:
   - Update master indexes in `docs/requirements.md` and `docs/tests.md` to 4 columns.
   - Append `REQ-PRO-073` and `TST-PRO-073`.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit test suites and CI static analysis.
  2. 100% mathematical parity between living doc master tables and domain shards.
  3. Strict standard Python library implementation for tools (no pip dependencies).
* **Risk Rating**: **LOW** (strictly additive governance tooling and documentation).
