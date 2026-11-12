# Stage 5: Walkthrough & Verification - ATT-6125: Repair corrupted Tests fields in requirement shards

**Ticket**: [ATT-6125](https://rainerblind.atlassian.net/browse/ATT-6125)  
**Sub-task**: `[Test]` (Created on submission)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-075` (*Requirement Shards Tests Field Format & Machine-Readable Traceability Integrity*)  
**Test Mapping**: `TST-PRO-075` (*Requirement Tests Field Format & Traceability Governance Verifier Test Suite*)  
**Branch**: `subagent-Lane-2-Agent---ATT-6125-self-09c91856`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
All 9 domain requirement shards (`docs/requirements/req_*.md`) have been systematically audited and repaired:
- 452 requirements where Kotlin/Java/XML source files were mistakenly listed in the `Tests:` field have had their source files moved to `Implementation:`, and their `Tests:` fields populated with authentic backticked `TST-*` IDs.
- 100 truncated sentence fragments and dangling boolean/code expressions in `Tests:` fields were restored to valid backticked test IDs.
- 244 trailing inline ticket annotations (`| **Tickets**: ...`) were stripped.
- A strict format validation rule `verify_tests_field_format(repo_root=None)` was integrated into `tools/verify_requirement_governance.py` with CLI flag `--check-tests-format` and automatic execution during `--sync-indexes`.
- 3 new unit test scenarios (Scenarios 38, 39, 40) were added to `tools/test_verify_requirement_governance.py`.
- 100% master table parity and requirement ID uniqueness confirmed across all living docs shards with zero dangling references.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-075.1` | `TST-PRO-075.1` | Unit Test: Conforming Syntax Acceptance | **PASSED** | `Verified` |
| `REQ-PRO-075.1` | `TST-PRO-075.2` | Unit Test: Corrupted & Source Code Rejection | **PASSED** | `Verified` |
| `REQ-PRO-075.2` | `TST-PRO-075.3` | Integration: Clean-Room Shard Audit | **PASSED** | `Verified` |
| `REQ-PRO-075.3` | `TST-PRO-075.4` | Parity: Master Index Table Parity Check | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Governance Test Suite (`python3 tools/test_verify_requirement_governance.py`)
```text
Ran 40 tests in 6.044s
OK
```

### Format Verification & Master Index Parity (`python3 tools/verify_requirement_governance.py --sync-indexes`)
```text
Recalculated master index table in docs/requirements.md: Total=1075, Verified=1071, Planned=4.
Recalculated master index table in docs/tests.md: Total=1079, Verified=1079, Planned=0.
PASS: Master index tables successfully synchronized with 100% parity.
PASS: All Tests fields in requirement shards conform to valid TST ID and placeholder syntax (REQ-PRO-075).
```

### Full Governance Verifier Check (`python3 tools/verify_requirement_governance.py`)
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: All REQ and TST heading IDs are unique across living documentation shards.
PASS: Zero new dangling REQ/TST references detected (27 legacy references tolerated pending A-08.x).
PASS: All Tests fields in requirement shards conform to valid TST ID and placeholder syntax (REQ-PRO-075).
PASS: Zero Composable islands detected (no modified UI files).
PASS: Zero repository encapsulation violations detected.
PASS: Zero architecture or system specification modifications detected.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

### Sharding Regression Test Suite (`python3 tools/test_shard_living_docs.py`)
```text
Ran 8 tests in 0.223s
OK
```

---

## 4. Hardware / Physical Verification (Pixel 10)
Pure developer tooling and documentation governance improvement; zero APK runtime bytecode changes; verified via automated test suite.

### Visual Consistency (Rule 23)
No UI changes.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-075` and test `TST-PRO-075`. References `REQ-PRO-028` and `REQ-PRO-065`.
* **Historical Origin & Commit Trace**: Commit `06990c88` (Sprint 2026-44.1, `ATT-4594`) introduced domain-sharded requirements. Commit `c9c2d428` (`ATT-6124`) repaired the parser in `tools/shard_living_docs.py`.
* **Root Reason for Existing Formulation**: Shards were generated once by the initial script and never systematically retrofitted, leaving historical formatting artifacts in place.
* **Preservation of Core Invariants**: All 1072+ requirement identifiers, statuses, descriptions, and rationales are preserved. Bidirectional linkage to `docs/tests/` is elevated to 100% valid format. Zero production code changes in `app/src/main/`.
