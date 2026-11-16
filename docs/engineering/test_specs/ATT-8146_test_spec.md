# Stage 2: Requirement & Test Specification - ATT-8146: Fix SYSREQ coverage regex and report true SYS-to-SWE coverage

**Ticket**: [ATT-8146](https://rainerblind.atlassian.net/browse/ATT-8146)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PRO-102` (*Boundary-Safe SYSREQ Traceability & Bidirectional System-Refinement Coverage Reporting*; extends `REQ-PRO-076.4`)  
**Test Spec ID**: `TST-PRO-102`  
**Branch**: `feature/ATT-8146` | **Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (REQ-PRO-102)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Target Status: `[Planned]`
* **REQ-PRO-102.1 (Word/Prefix Boundary Enforcement)**: `verify_system_software_traceability()` (`L3710-3716`) and secondary `REQ-*` scanners (`_collect_test_file_index`, `triage_unbacked_verified_tsts`) in `tools/verify_requirement_governance.py` SHALL enforce `(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])` (and eliminate `"REQ-" in line` substring checks) so `SYSREQ-DEX-001` / `SYSREQ-TRK-001` never false-match as `REQ-DEX-001` / `REQ-TRK-001`.
* **REQ-PRO-102.2 (Strict `SYSTEM_REFINED_PREFIXES` Filtering)**: `verify_system_software_traceability()` SHALL restrict `sys_to_swe` refinement sets strictly to `SYSTEM_REFINED_PREFIXES`, excluding `REQ-PRO-*` and all other `SWE_DERIVED_PREFIXES`.
* **REQ-PRO-102.3 (Bidirectional Forward & Reverse Coverage Reporting)**: `verify_system_software_traceability()` and `--report-sys-coverage` SHALL compute and report BOTH forward (`SYSREQ -> SWE`, `12/12` = `100.0%`) and authentic reverse `System-Refinement` (`SWE -> SYSREQ`, `49/355` = `13.8%`) coverage without breaking `--verify-sys-trace` CI pass when forward coverage is 100% and zero dangling `SYSREQ`s exist.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-076.4` / `TST-PRO-076`. Targets `tools/verify_requirement_governance.py` (`verify_system_software_traceability`, `_collect_test_file_index`, `triage_unbacked_verified_tsts`, `--report-sys-coverage`).
* **Historical Origin & Commit Trace**: `REQ-PRO-076` introduced in `ATT-6163` (Sprint `2026-46.4`); amended in `ATT-8146` (Sprint `2026-47.1`, Epic `ATT-8138`) for Finding M2.
* **Root Reason for Existing Formulation**: Enforced `12/12` forward `SYSREQ` coverage and zero dangling `SYSREQ` references.
* **Preservation of Core Invariants**: Preserves `--verify-sys-trace` CI pass (`12/12` forward `SYSREQ`s, zero dangling `SYSREQ`s), existing `report` keys (`sysreq_count`, `covered_count`, `coverage_pct`, `sys_to_swe_map`) for `test_scenario_40..43`, pure Python stdlib, and zero `app/src/main/` or `docs/system/*.md` edits.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Boundary Rejection)**: *Given* a document containing only `SYSREQ-DEX-001`, *When* `verify_system_software_traceability()` parses it, *Then* `REQ-DEX-001` is not extracted into `sys_to_swe`.
* **Criterion 2 (Derived Prefix Exclusion)**: *Given* `REQ-PRO-*` citing `SYSREQ-*`, *When* coverage is computed, *Then* `REQ-PRO-*` is excluded from `sys_to_swe` and `swe_refinement_total_count`.
* **Criterion 3 (Bidirectional Live Report)**: *Given* the live repo, *When* `--report-sys-coverage` runs, *Then* it reports `12/12` (`100.0%`) forward and `49/355` (`13.8%`) reverse `System-Refinement` coverage, and `--verify-sys-trace` exits `0`.

---

## 4. Test Specification (TST-PRO-102)
* **TST-PRO-102.1 (SYSREQ Boundary False-Positive Test)**: In `tools/test_verify_requirement_governance.py`, assert `SYSREQ-DEX-001` / `SYSREQ-TRK-001` never match `REQ-DEX-001` / `REQ-TRK-001` in `verify_system_software_traceability()` or `_collect_test_file_index()`.
* **TST-PRO-102.2 (Derived Prefix Exclusion Test)**: Assert `REQ-PRO-*` specs citing `SYSREQ-*` are excluded from `sys_to_swe` and `swe_refinement_total_count`.
* **TST-PRO-102.3 (Bidirectional Coverage & Live Report Test)**: Assert `swe_refinement_total_count`, `swe_refinement_covered_count`, `swe_refinement_coverage_pct`, and `swe_to_sys_map` match authentic counts (`49/355` = `13.8%`), `--verify-sys-trace` exits `0`, and `test_scenario_40..43` pass.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-102.1` | Unit | `tools/verify_requirement_governance.py` | `REQ-PRO-102.1` | Planned |
| `TST-PRO-102.2` | Unit | `tools/verify_requirement_governance.py` | `REQ-PRO-102.2` | Planned |
| `TST-PRO-102.3` | Unit | `tools/test_verify_requirement_governance.py` | `REQ-PRO-102.3` | Planned |
