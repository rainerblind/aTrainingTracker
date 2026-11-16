# Stage 3: Implementation Plan - ATT-8146: Fix SYSREQ coverage regex and report true SYS-to-SWE coverage

**Ticket**: [ATT-8146](https://rainerblind.atlassian.net/browse/ATT-8146)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8146`  
**Requirement Mapping**: `REQ-PRO-102` (`REQ-PRO-102.1`..`REQ-PRO-102.3`)  
**Test Mapping**: `TST-PRO-102` (`TST-PRO-102.1`..`TST-PRO-102.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Description & Background
`verify_system_software_traceability()` in `tools/verify_requirement_governance.py:3710-3716` checks `"REQ-" in line` and `re.findall(r"REQ-[A-Z0-9_-]+", line)`, extracting bogus `REQ-DEX-001..006` / `REQ-TRK-001..006` from `SYSREQ-*` IDs. It also counts `5` `REQ-PRO-*` process specs as system refinements and omits reverse `SWE -> SYSREQ` coverage (`49/355` = `13.8%`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-102` (`REQ-PRO-102.1`..`REQ-PRO-102.3`) in `docs/requirements/req_process_stability.md`
* **Test Mapping**: `TST-PRO-102` (`TST-PRO-102.1`..`TST-PRO-102.3`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime & System-Spec Edits**: Zero `app/src/main/` or `docs/system/*.md` changes; `ANT-Android-SDKs` stays `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
2. **Forward Gate & Schema Stability**: `--verify-sys-trace` remains non-blocking (`exit 0` with `12/12` = `100.0%` forward `SYSREQ` coverage, `0` dangling `SYSREQ`s); existing `report` keys (`sysreq_count`, `covered_count`, `coverage_pct`, `sys_to_swe_map`) preserved.
3. **Dangling Check Scope**: Inline `SYSREQ-*` dangling validation still inspects all `docs/requirements/*.md` headings (including `REQ-PRO-*`).
4. **Master Table Parity**: Living-doc status updates (`[Planned]` -> `[Implemented]`) synchronized via `--sync-indexes`.

---

## 4. Proposed Architectural Changes
* **`tools/verify_requirement_governance.py`**:
  - Define boundary regex `(?<![A-Za-z0-9_-])REQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])` and `(?<![A-Za-z0-9_-])SYSREQ-[A-Z0-9_-]+(?![A-Za-z0-9_-])`; remove `"REQ-" in line` substring check at `L3710-3716`.
  - Harden secondary `REQ-*` regexes in `_collect_test_file_index()` (`L2965`) and `triage_unbacked_verified_tsts()` (`L3077`) with `(?<![A-Za-z0-9_-])`.
  - Filter `sys_to_swe` strictly to `prefix in SYSTEM_REFINED_PREFIXES` (excluding `REQ-PRO-*` and `SWE_DERIVED_PREFIXES`).
  - Compute reverse metrics (`swe_refinement_total_count`, `swe_refinement_covered_count`, `swe_refinement_coverage_pct`, `swe_to_sys_map`, `unlinked_swe_refinement_reqs`) and print both forward and reverse coverage in `--report-sys-coverage`.
* **`tools/test_verify_requirement_governance.py`**:
  - Update `test_scenario_40`/`41` (`REQ-TST-001`/`002` -> `REQ-TRK-001`/`002`, since `TST` is in `SWE_DERIVED_PREFIXES`).
  - Add `TST-PRO-102.1`..`TST-PRO-102.3` unit tests; keep `test_scenario_42`/`43` passing.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Low Process Risk, Zero Runtime Risk)** — executed by **`subagent_flash`**.
* **Step 1 (Boundary Regex Hardening)**: Apply `(?<![A-Za-z0-9_-])` boundary regex in `verify_system_software_traceability()` (`L3710-3716`), `_collect_test_file_index()` (`L2965`), and `triage_unbacked_verified_tsts()` (`L3077`).
* **Step 2 (Prefix Filter & Bidirectional Metrics)**: Restrict `sys_to_swe` to `SYSTEM_REFINED_PREFIXES`, add reverse `System-Refinement` metrics to `report`, and print both directions in `--report-sys-coverage` (`L4249-4255`).
* **Step 3 (Unit Tests & Living Docs)**: Update `test_scenario_40`/`41` (`REQ-TRK-001`/`002`), add `TST-PRO-102.1`..`102.3` tests in `tools/test_verify_requirement_governance.py`, set `REQ-PRO-102`/`TST-PRO-102` to `[Implemented]`, run `--sync-indexes`.
* **Tier 1 Test Commands**:
  ```bash
  python3 -m unittest tools/test_verify_requirement_governance.py tools/test_generate_test_evidence.py tools/test_jira_accounts.py tools/hoechstmass/tools/test_hoechstmass_tools.py && \
  python3 tools/verify_requirement_governance.py && \
  python3 tools/verify_requirement_governance.py --verify-sys-trace --report-sys-coverage && \
  python3 tools/verify_requirement_governance.py --check-verified-evidence && \
  python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemDataExchangeRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemVerificationProtocolContractTest"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: All Python unit tests, `--verify-sys-trace --report-sys-coverage` (`12/12` = `100.0%` forward, `49/355` = `13.8%` reverse), `--check-verified-evidence`, and Gradle contract tests exit `0`.
* **Rollback**: Branch isolation (`feature/ATT-8146`) allows instant revert without impacting `sprint/2026-47.1` or `develop`.
