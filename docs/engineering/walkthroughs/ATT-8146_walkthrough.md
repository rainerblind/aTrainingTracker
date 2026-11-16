# Stage 5 Walkthrough: ATT-8146 - Fix SYSREQ coverage regex and report true SYS-to-SWE coverage

**Ticket**: [ATT-8146](https://rainerblind.atlassian.net/browse/ATT-8146)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PRO-102` (`REQ-PRO-102.1`..`REQ-PRO-102.3`) [Verified]  
**Test Spec Mapping**: `TST-PRO-102` (`TST-PRO-102.1`..`TST-PRO-102.3`) [Verified]  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Executive Summary & Verification Walkthrough
Remediated ASPICE 4.0 Finding `M2` (`tools/verify_requirement_governance.py`):
1. **Word/Prefix Boundary Regex Hardening (`REQ-PRO-102.1`)**: Replaced `"REQ-" in line` substring check and unbounded `REQ-*` regexes in `verify_system_software_traceability()`, `_collect_test_file_index()`, and `triage_unbacked_verified_tsts()` with `(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])` and `(?<![A-Za-z0-9_-])SYSREQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])`, eliminating false-positive `REQ-DEX-001..006` / `REQ-TRK-001..006` extraction from `SYSREQ-*`.
2. **Strict `SYSTEM_REFINED_PREFIXES` Filtering (`REQ-PRO-102.2`)**: Restricted `sys_to_swe` refinement sets strictly to `SYSTEM_REFINED_PREFIXES` (`CON..ROU`), excluding `REQ-PRO-*` (`REQ-PRO-054..056`, `069`, `076`) and all `SWE_DERIVED_PREFIXES` while preserving inline dangling `SYSREQ` validation across all `REQ-*` sections.
3. **Bidirectional Coverage Reporting (`REQ-PRO-102.3`)**: Populated `swe_refinement_total_count`, `swe_refinement_covered_count`, `swe_refinement_coverage_pct`, `swe_to_sys_map`, and `unlinked_swe_refinement_reqs` in `report`, and updated `--report-sys-coverage` to print both **Forward** (`12/12`, `100.0%`) and **Reverse System-Refinement** (`49/355`, `13.8%`, `306` unlinked) coverage with `--verify-sys-trace` exiting `0`.
4. **Unit Test Coverage**: Updated `test_scenario_40`/`41` (`REQ-TST-*` -> `REQ-TRK-*`) and added `test_scenario_66..68` (`TST-PRO-102.1..102.3`) in `tools/test_verify_requirement_governance.py`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-102.1` | `TST-PRO-102.1` | `test_scenario_66` (`SYSREQ-DEX-001` boundary rejection) | **PASSED** | `Verified` |
| `REQ-PRO-102.2` | `TST-PRO-102.2` | `test_scenario_67` (`REQ-PRO-*` exclusion from `sys_to_swe`) | **PASSED** | `Verified` |
| `REQ-PRO-102.3` | `TST-PRO-102.3` | `test_scenario_68` + `--verify-sys-trace --report-sys-coverage` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence
* **Stage 4 Gradle Tier 1 Scope** *(reused per `Rule 7.5`; zero `.kt`/`.java`/runtime code modified in Stage 5)*:
  `./gradlew :app:testDebugUnitTest` (`AspiceSweAuditContractTest=9`, `SystemDataExchangeRequirementsContractTest=4`, `SystemLiveTrackingRequirementsContractTest=4`, `SystemVerificationProtocolContractTest=7`): **24 / 24 PASSED** (`0` failures, `0` errors, `0` skipped).
* **Stage 5 Python Unit Suites**:
  `python3 -m unittest tools/test_verify_requirement_governance.py tools/test_generate_test_evidence.py tools/test_jira_accounts.py tools/hoechstmass/tools/test_hoechstmass_tools.py`: **165 / 165 PASSED** (`0` failures, `0` errors).
* **Stage 5 Governance CLI Gates**:
  `verify_requirement_governance.py`, `--verify-sys-trace --report-sys-coverage` (**12/12 [100.0%]** forward, **49/355 [13.8%]** reverse, `306` unlinked, `0` dangling `SYSREQ`s), `--check-verified-evidence` (`0` legacy unbacked TSTs), and `--check-test-evidence`: **ALL PASS**.

---

## 4. Hardware / Physical Verification & Invariants
* **Runtime / System-Spec Scope**: Zero `app/src/main/` or `docs/system/*.md` modifications (`Rule 7.5`); `ANT-Android-SDKs` untouched at `160000` (`0e8cea71`).
* **Living Docs & Gate Invariants**: `REQ-PRO-102` & `TST-PRO-102` `[Verified]`, master index parity intact, Rule 3 Human Gate preserved.
