# Stage 5 Walkthrough: ATT-8147 - Complete bidirectional SYS-to-SWE traceability for System Refinement domains

**Ticket**: [ATT-8147](https://rainerblind.atlassian.net/browse/ATT-8147)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PRO-103` (`REQ-PRO-103.1`..`REQ-PRO-103.3`) [Verified]  
**Test Spec Mapping**: `TST-PRO-103` (`TST-PRO-103.1`..`TST-PRO-103.3`) [Verified]  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Executive Summary & Verification Walkthrough
Remediated ASPICE 4.0 Finding `M2` (`13.8%` -> `100.0%` System-Refinement classification):
1. **Structured `System Requirement(s)` Parser & Prefix Closure (`REQ-PRO-103.1`)**: Added `CLM` (4) and `RAD` (3) to `SYSTEM_REFINED_PREFIXES` (`362` total `System-Refinement` REQs) and `docs/system/system_software_traceability.md` §3.1 while keeping `REQ-PRO-*` in `SWE_DERIVED_PREFIXES`. Added structured `- **System Requirement(s)**:` & `Derived (SW-only)` rationale (`>= 5` chars) parsing in `tools/verify_requirement_governance.py`.
2. **100% Bidirectional Traceability & Matrix Sync (`REQ-PRO-103.2`)**: Annotated all `362` `SYSTEM_REFINED_PREFIXES` REQs (`306` previously unlinked + `49` existing + `7` `RAD`/`CLM`) across `req_tracking_cockpit.md`, `req_data_migration_export.md`, `req_maps_navigation.md`, and `req_aftermath_periods.md` (`346` `SYSREQ`-linked [`95.58%`], `16` `Derived (SW-only)` with rationales, `0` unclassified). Synchronized `docs/system/sysreq_live_tracking.md`, `sysreq_data_exchange.md`, and `system_software_traceability.md`.
3. **Reverse Ratchet Gate & Tests (`REQ-PRO-103.3`)**: Implemented `--check-reverse-sys-trace` (`LEGACY_UNLINKED_SYS_REFINEMENT_REQS = frozenset()`), extended `--report-sys-coverage`, updated `test_scenario_68`, and added `test_scenario_69..71` (`TST-PRO-103.1`..`103.3`) in `tools/test_verify_requirement_governance.py`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-103.1` | `TST-PRO-103.1` | `test_scenario_69` (`System Requirement(s)` & `Derived (SW-only)` parser) | **PASSED** | `Verified` |
| `REQ-PRO-103.2` | `TST-PRO-103.2` | `test_scenario_68`/`70`/`71` + `--verify-sys-trace --check-reverse-sys-trace` | **PASSED** | `Verified` |
| `REQ-PRO-103.3` | `TST-PRO-103.3` | `System*ContractTest` (`29/29`) & `AspiceSweAuditContractTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence
* **Stage 4 Gradle Tier 1 Scope** *(reused per `Rule 7.5`; zero `.kt`/`.java`/runtime code modified in Stage 5)*:
  `./gradlew :app:testDebugUnitTest` (`AspiceSweAuditContractTest`, `SystemDataExchangeRequirementsContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SystemRequirementsSpecificationContractTest`, `SystemVerificationProtocolContractTest`): **29 / 29 PASSED** (`0` failures, `0` errors, `0` skipped).
* **Stage 5 Python Unit Suites**:
  `python3 -m unittest tools/test_verify_requirement_governance.py tools/test_generate_test_evidence.py tools/test_jira_accounts.py tools/hoechstmass/tools/test_hoechstmass_tools.py`: **168 / 168 PASSED** (`0` failures, `0` errors).
* **Stage 5 Governance CLI Gates**:
  `verify_requirement_governance.py`, `--verify-sys-trace --check-reverse-sys-trace --report-sys-coverage` (`12/12 [100.0%]` forward, `346/362 [95.58%]` `SYSREQ`-linked, `16` `Derived (SW-only)`, `362/362 [100.0%]` classified, `0` unclassified), `--check-verified-evidence` (`0` legacy unbacked TSTs), `--check-test-evidence`: **ALL PASS**.

---

## 4. Hardware / Physical Verification & Invariants
* **Runtime / Submodule Scope**: Zero `app/src/main/` edits (`Rule 7.5`); `ANT-Android-SDKs` untouched at `160000` (`0e8cea71`).
* **Living Docs & Gate Invariants**: `REQ-PRO-103` & `TST-PRO-103` `[Verified]`, master index parity intact, Rule 3 Human Gate preserved.
