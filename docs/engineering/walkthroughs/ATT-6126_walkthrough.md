# Stage 5: Walkthrough & Verification - ATT-6126: Introduce Evidence-Based Requirement Status Model

**Ticket**: [ATT-6126](https://rainerblind.atlassian.net/browse/ATT-6126)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `feature/ATT-6126` -> `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-073` (`REQ-PRO-073.1`–`REQ-PRO-073.4`) | **Test Mapping**: `TST-PRO-073` (`TST-PRO-073.1`–`TST-PRO-073.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
Addressed Remediation ID A-07 under ASPICE CL2 (SWE.1, SWE.6 BP3, GP 2.2) to eliminate status inflation and establish an evidence-grounded requirement lifecycle. Previously, 98% of requirements claimed `[Verified]` despite lacking test specifications or executable test code.

Delivered the following technical achievements:
1. **Evidence-Based Status Model Specification**: Documented the 3-state requirement lifecycle in `docs/project_governance.md` and `.agents/rules/aspice_governance.md`:
   - `[Planned]` / `[Open]`: Requirement specified, no verified test evidence yet.
   - `[Implemented]`: Code exists in repository, but automated test evidence (`TST-*`) is pending, incomplete, or failing.
   - `[Verified]`: Requires $\ge 1$ authentic test specification citing passing automated test code or formal archived verification records.
2. **Master Index 4-Column Table Parity**: Updated `docs/requirements.md` and `docs/tests.md` master index tables from 3 columns to 4 columns (`Total | Verified | Implemented | Planned / Open`) reflecting accurate domain shard counts across all 9 domain shards with 100% mathematical parity.
3. **Synchronizer & Governance Verifier Automation**:
   - Updated `tools/verify_requirement_governance.py` (`verify_master_table_parity()`, `recalculate_master_index_tables()`) to parse, validate, and synchronize 4-column master tables via `--sync-indexes`.
   - Implemented evidence verification rule (`verify_requirement_evidence_status()`, `--check-evidence`, `--enforce-evidence`): any requirement claiming `[Verified]` must cite $\ge 1$ existing `TST-*` ID.
   - Configured `LEGACY_UNGROUNDED_VERIFIED_REQS` allowlist (551 legacy requirements) to preserve pipeline stability pending the planned domain backfills in child tickets ATT-6127..ATT-6135, while strictly failing any new or modified requirement claiming `[Verified]` without test evidence.
4. **Contract Test Modernization**: Updated `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`:
   - `testLivingDocShards_allHeadingsStandardized`: Accepts `[Implemented]` alongside `[Verified]`, `[Planned]`, `[Open]`.
   - `testMasterTableParity_matchesDomainShards`: Validates 4-column master table format and asserts mathematical equality between domain shards and master index tables.
   - Fixed domain shard name assertion to `"Store Presence"` reflecting ATT-6140 reorganization.
5. **Standardization Clean-up**: Normalized `REQ-PRO-058` and `TST-PRO-058` from non-standard `[In Progress]` to `[Implemented]`, satisfying both heading standardization and master table parity.
6. **Tooling Unit Test Suite**: Added Scenarios 38 & 39 in `tools/test_verify_requirement_governance.py` covering 4-column synchronization parity and evidence rule enforcement; all 39 tests pass cleanly.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-073.1` | `TST-PRO-073.1` | Contract: Headings standardization accepting `[Implemented]` (`AspiceSweAuditContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-073.2` | `TST-PRO-073.2` | Contract: 4-column master table mathematical parity (`AspiceSweAuditContractTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-073.3` | `TST-PRO-073.3` | Unit: Verifier 4-column table parsing and `--sync-indexes` (`test_verify_requirement_governance.py:test_scenario_38`) | **PASSED** | `Verified` |
| `REQ-PRO-073.4` | `TST-PRO-073.4` | Unit: Evidence rule verification and legacy allowlisting (`test_verify_requirement_governance.py:test_scenario_39`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tooling Unit Test Suite (`python3 -m unittest tools/test_verify_requirement_governance.py`)
```text
Ran 39 tests in 5.234s

OK
```
All 39 scenarios passed with 100% success rate, specifically:
- `test_scenario_38_master_table_parity_and_sync_4_columns`: Verified 4-column extraction, formatting, and index synchronization.
- `test_scenario_39_evidence_status_rule`: Verified that ungrounded verified claims fail without test citation and pass with existing test specs.

### Contract Test Suite (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`)
```text
BUILD SUCCESSFUL in 8s
12 actionable tasks: 1 executed, 11 up-to-date
```
Contract test passed completely:
- `testLivingDocShards_allHeadingsStandardized`: PASSED (all REQ and TST headings in all 9 shards adhere to standard status tokens).
- `testMasterTableParity_matchesDomainShards`: PASSED (master index tables in `docs/requirements.md` and `docs/tests.md` match shard sums exactly across Total, Verified, Implemented, Planned/Open columns).
- `testArchitectureDocument_fulfillsAspiceSwe2`: PASSED.
- `testArchitectureDocument_coversAllDomainShards`: PASSED.

### Live Governance Verifier (`python3 tools/verify_requirement_governance.py --text-file docs/engineering/analysis/ATT-6126_analysis.md`)
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: All REQ and TST heading IDs are unique across living documentation shards.
PASS: Zero new dangling REQ/TST references detected (27 legacy references tolerated pending A-08.x).
PASS: Zero ungrounded [Verified] requirements detected (551 legacy requirements tolerated pending A-08 backfill).
PASS: Zero Composable islands detected (no modified UI files).
PASS: Zero repository encapsulation violations detected.
PASS: Zero architecture or system specification modifications detected.
Modified/Altered existing requirement(s) detected: REQ-PRO-058.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / UI Verification
* Developer tooling, contract test, and governance document update; pure Python 3 standard library and JUnit 4 contract testing.
* Zero runtime bytecode in `app/src/main/`; zero UI rendering or strings modified. Physical hardware / emulator checks not required.

---

## 5. Invariant & Governance Verification
1. **Python 3 Standard Library Invariant**: Zero third-party dependencies; uses only `re`, `os`, `sys`, `json`, `subprocess`, `argparse`, `unittest`.
2. **Deterministic Mathematical Parity**: Total = Verified + Implemented + Planned / Open holds across all rows in both `docs/requirements.md` and `docs/tests.md`.
3. **Legacy Isolation**: 551 ungrounded legacy requirements isolated in `LEGACY_UNGROUNDED_VERIFIED_REQS` awaiting child ticket backfills; pipeline strictly halts regressions on any new or modified requirement.
4. **Living Documentation Synchronized**: `REQ-PRO-073` and `TST-PRO-073` registered as `[Verified]` with 100% master index table parity.
