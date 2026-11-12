# Stage 5: Walkthrough & Verification - ATT-6121: Detect Dangling REQ and TST References in Governance Verifier

**Ticket**: [ATT-6121](https://rainerblind.atlassian.net/browse/ATT-6121)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-PRO-070` | **Test Mapping**: `TST-PRO-070`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
Enforced bidirectional reference integrity between requirements and test cases across living documentation domain shards (`docs/requirements/*.md` and `docs/tests/*.md`). Implemented `find_dangling_references()` and `verify_dangling_references()` in `tools/verify_requirement_governance.py` with precise file, line, source ID, and missing target ID diagnostic reporting. Established controlled baseline allowlists (`LEGACY_DANGLING_TST_REFS` and `LEGACY_DANGLING_REQ_REFS`) covering the 27 known pre-existing legacy dangling references so active CI branches remain green pending backfill under A-08.x (ATT-6127..6135), while rejecting any novel dangling reference with exit code 1. Implemented `--enforce-links` / `--fail-on-dangling` CLI flag for strict zero-tolerance verification. Verified via 5 new unit test scenarios (30 total tests in `test_verify_requirement_governance.py` passing 100%), contract test execution, and live verifier CLI verification.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-070.1` | `TST-PRO-070.1` | Unit: Dangling TST detection in REQ `Tests` fields (`test_scenario_26`) | **PASSED** | `Verified` |
| `REQ-PRO-070.2` | `TST-PRO-070.2` | Unit: Dangling REQ detection in TST `Requirements` fields (`test_scenario_27`) | **PASSED** | `Verified` |
| `REQ-PRO-070.3` | `TST-PRO-070.3` | Unit: Legacy baseline tolerance vs novel reference rejection (`test_scenario_28`) | **PASSED** | `Verified` |
| `REQ-PRO-070.4` | `TST-PRO-070.4` | Unit: Strict enforcement mode verification (`test_scenario_29`) | **PASSED** | `Verified` |
| `REQ-PRO-070` | `TST-PRO-070.5` | Regression: Authentic living docs dangling reference check (`test_scenario_30`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tooling Unit Test Suite (`python3 -m unittest tools/test_verify_requirement_governance.py`)
```text
Ran 30 tests in 4.333s
OK (100% pass rate: scenarios 1–30 all green)
```

### Clean-Room Governance Pipeline (Standard Mode: `python3 tools/verify_requirement_governance.py`)
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: All REQ and TST heading IDs are unique across living documentation shards.
PASS: Zero new dangling REQ/TST references detected (27 legacy references tolerated pending A-08.x).
WARNING (Legacy dangling reference): docs/requirements/req_process_stability.md:138 [REQ-NFR-001] cites non-existent TST-STR-010.
WARNING (Legacy dangling reference): docs/requirements/req_process_stability.md:144 [REQ-NFR-002] cites non-existent TST-FUSION-004.
WARNING (Legacy dangling reference): docs/requirements/req_tracking_cockpit.md:778 [REQ-FIL-007] cites non-existent TST-FUSION-001.
WARNING (Legacy dangling reference): docs/requirements/req_tracking_cockpit.md:784 [REQ-FIL-008] cites non-existent TST-FUSION-001.
WARNING (Legacy dangling reference): docs/requirements/req_tracking_cockpit.md:790 [REQ-FIL-009] cites non-existent TST-FUSION-002.
WARNING (Legacy dangling reference): docs/requirements/req_tracking_cockpit.md:796 [REQ-FIL-010] cites non-existent TST-FUSION-003.
WARNING (Legacy dangling reference): docs/requirements/req_ui_design_system.md:97 [REQ-UI-033] cites non-existent TST-UI-041.
WARNING (Legacy dangling reference): docs/requirements/req_ui_design_system.md:103 [REQ-UI-035] cites non-existent TST-UI-041.
WARNING (Legacy dangling reference): docs/requirements/req_ui_design_system.md:109 [REQ-UI-036] cites non-existent TST-UI-041.
WARNING (Legacy dangling reference): docs/requirements/req_ui_design_system.md:133 [REQ-UI-042] cites non-existent TST-UI-044.
WARNING (Legacy dangling reference): docs/requirements/req_ui_design_system.md:379 [REQ-UI-114] cites non-existent TST-INT-007.
WARNING (Legacy dangling reference): docs/tests/tst_maps_navigation.md:446 [TST-MAP-039] cites non-existent REQ-MAP-037.
WARNING (Legacy dangling reference): docs/tests/tst_process_stability.md:41 [TST-INT-004] cites non-existent REQ-UI-009.
WARNING (Legacy dangling reference): docs/tests/tst_process_stability.md:46 [TST-INT-005] cites non-existent REQ-UI-010.
WARNING (Legacy dangling reference): docs/tests/tst_process_stability.md:51 [TST-INT-006] cites non-existent REQ-UI-010.
WARNING (Legacy dangling reference): docs/tests/tst_process_stability.md:152 [TST-STR-001] cites non-existent REQ-UI-008.
WARNING (Legacy dangling reference): docs/tests/tst_process_stability.md:163 [TST-STR-003] cites non-existent REQ-UI-009.
WARNING (Legacy dangling reference): docs/tests/tst_process_stability.md:667 [TST-STB-013] cites non-existent REQ-STB-013.
WARNING (Legacy dangling reference): docs/tests/tst_process_stability.md:1057 [TST-STB-018] cites non-existent REQ-STB-013.
WARNING (Legacy dangling reference): docs/tests/tst_tracking_cockpit.md:860 [TST-TRK-027] cites non-existent REQ-TRK-035.
WARNING (Legacy dangling reference): docs/tests/tst_ui_design_system.md:16 [TST-UNT-002] cites non-existent REQ-UI-002.
WARNING (Legacy dangling reference): docs/tests/tst_ui_design_system.md:171 [TST-UI-029] cites non-existent REQ-UI-009.
WARNING (Legacy dangling reference): docs/tests/tst_ui_design_system.md:251 [TST-UI-071] cites non-existent REQ-UI-062.
WARNING (Legacy dangling reference): docs/tests/tst_ui_design_system.md:390 [TST-UI-070] cites non-existent REQ-UI-166.
WARNING (Legacy dangling reference): docs/tests/tst_ui_design_system.md:548 [TST-UI-088] cites non-existent REQ-UI-135.
WARNING (Legacy dangling reference): docs/tests/tst_ui_design_system.md:1218 [TST-UI-118] cites non-existent REQ-UI-166.
WARNING (Legacy dangling reference): docs/tests/tst_ui_design_system.md:3826 [TST-UI-249] cites non-existent REQ-UI-289.
PASS: Zero Composable islands detected (no modified UI files).
PASS: Zero repository encapsulation violations detected.
PASS: Zero architecture or system specification modifications detected.
Net-new requirement(s) detected: REQ-PRO-070. Bypassing archaeology check cleanly.
```

### Strict Enforcement Mode Execution (`python3 tools/verify_requirement_governance.py --enforce-links`)
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: All REQ and TST heading IDs are unique across living documentation shards.
ERROR: Dangling REQ/TST references detected (27 references, strict enforcement active).
ERROR (Dangling reference): docs/requirements/req_process_stability.md:138 [REQ-NFR-001] cites non-existent TST-STR-010.
...
(Exits with code 1 as expected)
```

---

## 4. Hardware / UI Verification
* Pure developer tooling and governance verifier enhancement; zero runtime APK bytecode in `app/src/main/`.
* No UI or string resources added; emulator and physical device deployment not required.

---

## 5. Invariant & Governance Verification
1. **Python 3 Standard Library Invariant**: Zero third-party pip dependencies; uses only `re`, `os`, `sys`, `argparse`, `unittest`.
2. **Deterministic Exit Codes**: 0 on clean/compliant runs, 1 on novel dangling reference violation or under `--enforce-links`.
3. **CI Non-Regression**: Allows parallel sprint development without failure until A-08.x executes.
4. **Living Documentation Synchronized**: `REQ-PRO-070` and `TST-PRO-070` registered as `[Verified]`; master index tables match domain shards with 100% parity.
