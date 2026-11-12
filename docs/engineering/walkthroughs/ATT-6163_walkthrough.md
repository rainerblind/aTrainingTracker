# Stage 5 Walkthrough: ATT-6163 - Trace software requirements to system requirements

**Ticket**: [ATT-6163](https://rainerblind.atlassian.net/browse/ATT-6163)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-076` (`REQ-PRO-076.1`–`REQ-PRO-076.4`)  
**Test Spec ID**: `TST-PRO-076` (`TST-PRO-076.1`–`TST-PRO-076.4`)  
**Branch**: `feature/ATT-6163`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

Prior to this improvement (Remediation S-04, ASPICE SYS.2 BP5 & SWE.1 BP5), software requirements (`REQ-*`) existed in documentation silos without formal bidirectional derivation from black-box system requirements (`SYSREQ-*`). This change establishes bidirectional traceability from system specifications down to code, formalizes domain/prefix tailoring rules for software-derived requirements, and introduces automated governance verifier enforcement to guarantee zero dangling SYSREQs and 100% system requirement coverage.

---

## 2. Key Changes & Architecture

1. **System Specifications (`docs/system/`)**:
   - `docs/system/sysreq_live_tracking.md`: Added Section 4 mapping `SYSREQ-TRK-001`..`006` to concrete refining software requirements (`REQ-CON-*`, `REQ-TRK-*`, `REQ-FIL-*`).
   - `docs/system/sysreq_data_exchange.md`: Added Section 4 mapping `SYSREQ-DEX-001`..`006` to concrete refining software requirements (`REQ-EXP-*`, `REQ-DAT-*`, `REQ-MIG-*`, `REQ-EXT-*`).
   - `docs/system/system_software_traceability.md`: Created authoritative specification unifying bidirectional matrices and defining normative prefix tailoring rules for software-derived requirements.
2. **Process Governance (`docs/project_governance.md`)**:
   - Added Section 4 defining derivation rules and prefix tailoring for software-derived requirements (`UI`, `UNT`, `PER`, `PERF`, `SPT`, `LOC`, `MON`, `PRI`, `SET`, `REV`, `STP`, and process prefixes).
3. **Governance Tooling (`tools/verify_requirement_governance.py`)**:
   - Implemented `verify_system_software_traceability()`: verifies zero dangling `SYSREQ-*` citations, computes coverage across all 12 declared SYSREQs, and enforces prefix tailoring.
   - Added CLI arguments `--verify-sys-trace` and `--report-sys-coverage`.
   - Wired `verify_system_software_traceability()` into default CI `verify_governance()` check.
4. **Tooling Test Suite (`tools/test_verify_requirement_governance.py`)**:
   - Added test scenarios 40–43 verifying citation integrity, missing coverage detection, prefix tailoring, and live repository traceability.
5. **Living Documentation**:
   - Registered `REQ-PRO-076` and `TST-PRO-076` as `[Verified]` in `req_process_stability.md` and `tst_process_stability.md` with 100% master index parity.

---

## 3. Verification & Test Evidence

* **Tier 1 Targeted Verification**:
  - `python3 tools/test_verify_requirement_governance.py`: **43/43 tests PASS** (100% green in 8.5s).
  - `python3 tools/verify_requirement_governance.py --report-sys-coverage`: **12/12 SYSREQs covered (100.0% coverage)**, 0 dangling references.
  - `python3 tools/verify_requirement_governance.py`: **PASS** across all governance tripwires.
  - `AspiceSweAuditContractTest.kt`: **PASS** under Gradle with 100% master table parity.

| System Requirement | Status | Refining Software Requirements Count |
| :--- | :---: | :--- |
| `SYSREQ-TRK-001` | Covered | 14 REQs (`REQ-CON-001..005`, `REQ-CON-007`, `REQ-CON-028`, `REQ-TRK-001`, `REQ-TRK-002`, `REQ-TRK-006`, etc.) |
| `SYSREQ-TRK-002` | Covered | 6 REQs (`REQ-CON-005`, `REQ-CON-028`, `REQ-TRK-001`, `REQ-TRK-002`, `REQ-TRK-013`, etc.) |
| `SYSREQ-TRK-003` | Covered | 8 REQs (`REQ-TRK-001`, `REQ-TRK-002`, `REQ-TRK-003`, `REQ-TRK-004`, `REQ-TRK-007`, `REQ-FIL-001`, `REQ-FIL-005`, etc.) |
| `SYSREQ-TRK-004` | Covered | 7 REQs (`REQ-TRK-003`, `REQ-TRK-004`, `REQ-TRK-013`, `REQ-TRK-014`, `REQ-TRK-017`, `REQ-TRK-071`, etc.) |
| `SYSREQ-TRK-005` | Covered | 5 REQs (`REQ-TRK-003`, `REQ-TRK-005`, `REQ-TRK-015`, `REQ-TRK-016`, etc.) |
| `SYSREQ-TRK-006` | Covered | 7 REQs (`REQ-CON-028`, `REQ-TRK-001`, `REQ-TRK-006`, `REQ-TRK-018`, `REQ-TRK-019`, `REQ-TRK-073`, etc.) |
| `SYSREQ-DEX-001` | Covered | 13 REQs (`REQ-DAT-018`, `REQ-DAT-027`, `REQ-DAT-028`, `REQ-DEX-001..003`, `REQ-EXP-001`, `REQ-EXP-022`, etc.) |
| `SYSREQ-DEX-002` | Covered | 7 REQs (`REQ-DAT-001`, `REQ-DAT-019`, `REQ-DAT-021`, `REQ-DAT-025`, `REQ-DEX-001`, `REQ-DEX-002`, etc.) |
| `SYSREQ-DEX-003` | Covered | 12 REQs (`REQ-EXP-002`, `REQ-EXP-005`, `REQ-EXP-008`, `REQ-EXP-009`, `REQ-EXP-011`, `REQ-EXP-012`, `REQ-EXT-004`, etc.) |
| `SYSREQ-DEX-004` | Covered | 6 REQs (`REQ-DAT-020`, `REQ-DAT-022`, `REQ-DAT-024`, `REQ-DAT-029`, `REQ-DEX-004`, etc.) |
| `SYSREQ-DEX-005` | Covered | 6 REQs (`REQ-DAT-005`, `REQ-DAT-020`, `REQ-DAT-026`, `REQ-MIG-023`, `REQ-DEX-005`, etc.) |
| `SYSREQ-DEX-006` | Covered | 5 REQs (`REQ-DAT-024`, `REQ-EXP-020`, `REQ-DEX-001`, `REQ-DEX-006`, etc.) |

---

## 4. System Invariants & Rollback Safety

- **Zero Android Production Runtime Code Modification**: Only developer governance tooling, test suites, and documentation altered.
- **Pure Python 3 Standard Library**: Zero external dependencies introduced.
- **Living Documentation Integrity**: Master table counts match domain shards with 100% mathematical parity.
