# Stage 3: Implementation Plan - ATT-6163: Trace software requirements to system requirements

**Ticket**: [ATT-6163](https://rainerblind.atlassian.net/browse/ATT-6163)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `feature/ATT-6163`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Architectural Strategy & Design (SWE.2)

To satisfy ASPICE SYS.2 BP5 and SWE.1 BP5, this implementation establishes bidirectional traceability between system requirements (`SYSREQ-TRK-*`, `SYSREQ-DEX-*`) and software requirements (`REQ-*`), supplemented by normative domain/prefix tailoring rules and automated governance verification.

### Key Architectural Elements:
1. **Traceability Specifications (`docs/system/`)**:
   - `docs/system/sysreq_live_tracking.md`: Expand Section 4 with an explicit System-to-Software Requirement Traceability Matrix linking `SYSREQ-TRK-001`..`006` to refining `REQ-TRK-*`, `REQ-CON-*`, `REQ-FIL-*`, `REQ-FUSION-*`, `REQ-SEN-*` requirements.
   - `docs/system/sysreq_data_exchange.md`: Expand Section 4 with an explicit System-to-Software Requirement Traceability Matrix linking `SYSREQ-DEX-001`..`006` to refining `REQ-DAT-*`, `REQ-EXP-*`, `REQ-EXT-*`, `REQ-MIG-*` requirements.
   - `docs/system/system_software_traceability.md`: Unified bidirectional traceability document synthesizing SYS-to-SWE matrices and normative prefix tailoring rules.
2. **Process Governance Tailoring (`docs/project_governance.md`)**:
   - Classify internal UI (`UI`, `UNT`), mathematical/period analytics (`PER`, `PERF`, `SPT`), settings/profiles (`LOC`, `MON`, `PRI`, `SET`), reviews/store presence (`REV`, `STP`), and process/tooling (`ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST`) as software-derived (`[SWE-Derived]`) with architectural rationale.
3. **Governance Tooling (`tools/verify_requirement_governance.py`)**:
   - Implement `verify_system_software_traceability()`:
     a) Parses all declared SYSREQs from `docs/system/sysreq_*.md`.
     b) Parses all SYSREQ references across living requirements and traceability matrices.
     c) Asserts all cited SYSREQs exist in declared sets (flags dangling `SYSREQ-XXX`).
     d) Computes SYSREQ coverage by SWE REQs (asserts 100% of declared SYSREQs have $\ge 1$ refining SWE REQ).
     e) Enforces prefix tailoring rules.
4. **Tooling Test Suite (`tools/test_verify_requirement_governance.py`)**:
   - Implement unit test scenarios validating citation integrity, coverage computation, tailoring classification, and error detection.

---

## 2. Step-by-Step Implementation Sequence

| Step | Scope | Target File(s) | Description | Verification Method |
| :--- | :--- | :--- | :--- | :--- |
| **Step 1** | System Specs | `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, `docs/system/system_software_traceability.md` | Author bidirectional SYS-to-SWE traceability matrices and cross-references. | `verify_architecture_and_system_specs` |
| **Step 2** | Governance | `docs/project_governance.md`, `docs/system/system_software_traceability.md` | Formalize domain/prefix tailoring rules for software-derived requirements. | Documentation inspection |
| **Step 3** | Tooling | `tools/verify_requirement_governance.py` | Add `verify_system_software_traceability()`, `--verify-sys-trace`, and integrate into CI gate. | `python3 tools/verify_requirement_governance.py --verify-sys-trace` |
| **Step 4** | Unit Tests | `tools/test_verify_requirement_governance.py` | Add test scenarios for citation integrity, SYSREQ coverage, and prefix tailoring rules. | `python3 tools/test_verify_requirement_governance.py` |
| **Step 5** | Living Docs | `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md` | Transition `REQ-PRO-076` and `TST-PRO-076` to `[Verified]`, re-sync indexes. | `python3 tools/verify_requirement_governance.py --sync-indexes` |
| **Step 6** | Contract Tests | `AspiceSweAuditContractTest.kt` | Run contract test asserting 100% parity across living doc master index tables. | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"` |

---

## 3. Invariants & Rollback Safety

- **Code Lock**: Strict enforcement of Rule 4 (`check-gate`) before production code/tooling modifications.
- **Pure Python Standard Library**: Zero external dependencies added to `verify_requirement_governance.py`.
- **Zero Production APK Bytecode Modification**: Only developer governance tooling, test suites, and documentation are altered.
- **Parity Preservation**: Living documentation master tables maintain 100% mathematical parity with domain shards.
