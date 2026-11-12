# Stage 5: Walkthrough & Verification - ATT-6120: Enforce Unique REQ and TST IDs in Governance Verifier

**Ticket**: [ATT-6120](https://rainerblind.atlassian.net/browse/ATT-6120)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-066` | **Test Mapping**: `TST-PRO-066`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
Enforced unique requirement (`### REQ-*`) and test (`### TST-*`) heading identifiers across domain shards in `docs/requirements/*.md` and `docs/tests/*.md`. Implemented `find_duplicate_heading_ids()` and `verify_unique_ids()` in `tools/verify_requirement_governance.py` with exact file/line diagnostic reporting. Established controlled baseline allowlist (`LEGACY_DUPLICATE_IDS`) covering the 11 known pre-existing duplicates so CI remains green until ATT-6123, while rejecting any novel duplicates with exit code 1. Implemented `--enforce-unique` / `--fail-on-duplicates` CLI flag for strict zero-tolerance verification. Verified via 4 new test scenarios (23 total tests in `test_verify_requirement_governance.py`) and live verifier execution.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-066.1` | `TST-PRO-066.1` | Unit: Duplicate ID Detection & File/Line Diagnostics (`test_scenario_20`) | **PASSED** | `Verified` |
| `REQ-PRO-066.2` | `TST-PRO-066.2` | Unit: Legacy Baseline Tolerance & New Duplicate Rejection (`test_scenario_21`) | **PASSED** | `Verified` |
| `REQ-PRO-066.3` | `TST-PRO-066.3` | Unit: Strict Enforcement Mode (`test_scenario_22`) | **PASSED** | `Verified` |
| `REQ-PRO-066` | `TST-PRO-066.4` | Regression: Authentic Living Docs Uniqueness Check (`test_scenario_23`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tooling Unit Test Suite (`python3 -m unittest tools/test_verify_requirement_governance.py`)
```text
Ran 23 tests in 2.074s
OK (100% pass rate: scenarios 1–23 all green)
```

### Clean-Room Governance Pipeline (Standard Mode: `python3 tools/verify_requirement_governance.py`)
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero new duplicate REQ/TST IDs detected (11 legacy duplicates tolerated pending ATT-6123).
WARNING (Legacy duplicate): REQ-DAT-001 appears 2 times: docs/requirements/req_data_migration_export.md:10, docs/requirements/req_data_migration_export.md:2012
WARNING (Legacy duplicate): REQ-DAT-026 appears 2 times: docs/requirements/req_data_migration_export.md:2052, docs/requirements/req_data_migration_export.md:2086
WARNING (Legacy duplicate): REQ-MIG-018 appears 2 times: docs/requirements/req_data_migration_export.md:730, docs/requirements/req_data_migration_export.md:1441
WARNING (Legacy duplicate): REQ-UI-116 appears 2 times: docs/requirements/req_ui_design_system.md:10, docs/requirements/req_ui_design_system.md:388
WARNING (Legacy duplicate): REQ-UI-123 appears 2 times: docs/requirements/req_ui_design_system.md:418, docs/requirements/req_ui_design_system.md:5627
WARNING (Legacy duplicate): REQ-UI-257 appears 2 times: docs/requirements/req_ui_design_system.md:5585, docs/requirements/req_ui_design_system.md:5649
WARNING (Legacy duplicate): TST-DAT-001 appears 2 times: docs/tests/tst_data_migration_export.md:656, docs/tests/tst_data_migration_export.md:1305
WARNING (Legacy duplicate): TST-DAT-021 appears 2 times: docs/tests/tst_data_migration_export.md:1323, docs/tests/tst_data_migration_export.md:1338
WARNING (Legacy duplicate): TST-MAP-055 appears 2 times: docs/tests/tst_maps_navigation.md:778, docs/tests/tst_maps_navigation.md:935
WARNING (Legacy duplicate): TST-MIG-036 appears 2 times: docs/tests/tst_data_migration_export.md:629, docs/tests/tst_data_migration_export.md:1359
WARNING (Legacy duplicate): TST-UI-216 appears 2 times: docs/tests/tst_ui_design_system.md:1159, docs/tests/tst_ui_design_system.md:3210
PASS: Zero Composable islands detected (no modified UI files).
PASS: Zero repository encapsulation violations detected.
PASS: Zero architecture or system specification modifications detected.
Net-new requirement(s) detected: REQ-PRO-066. Bypassing archaeology check cleanly.
```

### Strict Enforcement Mode Execution (`python3 tools/verify_requirement_governance.py --enforce-unique`)
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
ERROR: Duplicate REQ/TST IDs detected (11 duplicates).
ERROR (Duplicate ID): REQ-DAT-001 appears 2 times: docs/requirements/req_data_migration_export.md:10, docs/requirements/req_data_migration_export.md:2012
...
(Exits with code 1 as expected)
```

---

## 4. Hardware / UI Verification
* Pure developer tooling and governance verification enhancement; zero runtime APK bytecode in `app/src/main/`.
* No UI or string resources added; emulator and physical device deployment not required.

---

## 5. Invariant & Governance Verification
1. **Python 3 Standard Library Invariant**: Zero third-party pip dependencies; uses only `re`, `os`, `sys`, `subprocess`, `argparse`, `unittest`.
2. **Deterministic Exit Codes**: 0 on clean/compliant runs, 1 on duplicate violation or novel duplicates outside allowlist.
3. **CI Non-Regression**: Allows parallel sprint development without failure until ATT-6123 executes.
4. **Living Documentation Synchronized**: `REQ-PRO-066` and `TST-PRO-066` updated to `Verified`; master index tables match domain shards with 100% parity.
