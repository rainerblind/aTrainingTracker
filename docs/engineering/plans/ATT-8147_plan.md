# Stage 3: Implementation Plan - ATT-8147: Complete bidirectional SYS-to-SWE traceability for System Refinement domains

**Ticket**: [ATT-8147](https://rainerblind.atlassian.net/browse/ATT-8147)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8147`  
**Requirement Mapping**: `REQ-PRO-103` (`REQ-PRO-103.1`..`REQ-PRO-103.3`)  
**Test Mapping**: `TST-PRO-103` (`TST-PRO-103.1`..`TST-PRO-103.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Description & Background
Following `ATT-8146`, `306` of `355` `SYSTEM_REFINED_PREFIXES` REQs lack a structured `SYSREQ-*` or `Derived (SW-only)` classification (`49/355` = `13.8%`), and `7` `RAD` (`3`) / `CLM` (`4`) REQs lack prefix assignment. This plan implements structured `- **System Requirement(s)**:` parsing, `--check-reverse-sys-trace` ratchet gating, 100% classification across all `362` `System-Refinement` REQs (`355 + 7`), and matrix synchronization.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-103` (`REQ-PRO-103.1`..`REQ-PRO-103.3`) in `docs/requirements/req_process_stability.md`
* **Test Mapping**: `TST-PRO-103` (`TST-PRO-103.1`..`TST-PRO-103.3`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime & `SYSREQ` Count Change**: Zero `app/src/main/` edits; `ANT-Android-SDKs` stays `160000`; `12` declared `SYSREQ-*` IDs (`declared == allocated` per `verify_architecture_and_system_specs()`).
2. **`REQ-PRO-*` Exclusion & `System*ContractTest` Safety**: Keep `REQ-PRO-*` in `SWE_DERIVED_PREFIXES` (`REQ-PRO-102.2`); update only Section 4/2 tables and §3.1 prefix rows in `docs/system/*.md` using authentic on-disk filenames (preserving `## 3.` bounds in `SystemLiveTrackingRequirementsContractTest` & `SystemDataExchangeRequirementsContractTest`).
3. **Existing Test & Attribute Parity**: Preserve `test_scenario_40..43`, `66`, `67` compatibility via `fail_on_unclassified_swe=False` default + `--check-reverse-sys-trace` flag; keep valid `[Verified|Implemented|Planned|Open]` headings and `Priority`/`Type`/`Verification Method`.

---

## 4. Proposed Architectural Changes
* **`tools/verify_requirement_governance.py`**:
  - Add `"RAD"`, `"CLM"` to `SYSTEM_REFINED_PREFIXES` and `LEGACY_UNLINKED_SYS_REFINEMENT_REQS = frozenset()`.
  - Parse `- **System Requirement(s)**:` (`SYSREQ-*` IDs or `Derived (SW-only)` + non-empty rationale) in `verify_system_software_traceability()`, recording `swe_derived_sw_only_map`, `unclassified_swe_refinement_reqs`, and `reverse_classified_pct`; gate unclassified REQs when `fail_on_unclassified_swe=True` (`--check-reverse-sys-trace` CLI).
  - Extend `--report-sys-coverage` to print `SYSREQ`-linked, `Derived (SW-only)`, and unclassified totals.
* **`docs/requirements/*.md` & `docs/system/*.md`**:
  - Add `- **System Requirement(s)**:` (`SYSREQ-*` or `Derived (SW-only) — <rationale>`) to all `362` `SYSTEM_REFINED_PREFIXES` REQs across `req_tracking_cockpit.md` (`152` incl. `REQ-TRK-082`), `req_data_migration_export.md` (`134` incl. `RAD`), `req_maps_navigation.md` (`75` incl. `RAD`/`CLM`), and `req_aftermath_periods.md` (`REQ-RAD-005`).
  - Sync `docs/system/sysreq_live_tracking.md` §4, `docs/system/sysreq_data_exchange.md` §4, and `docs/system/system_software_traceability.md` §2/§3.1; update `REQ-PRO-103`/`TST-PRO-103` to `[Implemented]` + `--sync-indexes`.
* **`tools/test_verify_requirement_governance.py`**: Update `test_scenario_68` for reconciled live counts (without breaking `40..43`/`66`/`67`) and add `TST-PRO-103.1`..`TST-PRO-103.3` tests.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Medium Process Risk, Zero Runtime Risk)** — executed by **`subagent_flash`**.
* **Step 1 (`tools/verify_requirement_governance.py`)**: Classify `RAD`/`CLM` in `SYSTEM_REFINED_PREFIXES`, add `- **System Requirement(s)**:` & `Derived (SW-only)` parser, `LEGACY_UNLINKED_SYS_REFINEMENT_REQS = frozenset()`, `--check-reverse-sys-trace`, and extended `--report-sys-coverage`.
* **Step 2 (`docs/requirements/*.md`)**: Populate `- **System Requirement(s)**:` (`SYSREQ-*` or `Derived (SW-only) — <rationale>`) across all `362` `SYSTEM_REFINED_PREFIXES` REQs (`129` + `23` tracking, `107` + `27` data, `70` maps, `7` `RAD`/`CLM`), keeping `REQ-PRO-*` excluded and attributes valid.
* **Step 3 (`docs/system/*.md` & Index Sync)**: Sync Section 4/2 traceability tables and §3.1 `RAD`/`CLM` rows in `sysreq_live_tracking.md`, `sysreq_data_exchange.md`, `system_software_traceability.md`; set `REQ-PRO-103`/`TST-PRO-103` `[Implemented]`; run `--sync-indexes`.
* **Step 4 (Tests & Verification)**: Update `test_scenario_68`, add `TST-PRO-103.1`..`103.3` in `tools/test_verify_requirement_governance.py`, and run Tier 1 commands:
  ```bash
  python3 -m unittest tools/test_verify_requirement_governance.py tools/test_generate_test_evidence.py tools/test_jira_accounts.py tools/hoechstmass/tools/test_hoechstmass_tools.py && \
  python3 tools/verify_requirement_governance.py && \
  python3 tools/verify_requirement_governance.py --verify-sys-trace --check-reverse-sys-trace --report-sys-coverage && \
  python3 tools/verify_requirement_governance.py --check-verified-evidence && \
  python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemDataExchangeRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemRequirementsSpecificationContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemVerificationProtocolContractTest"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: All Python unit tests, governance checks (`0` unclassified `System-Refinement` REQs), and all `5` Gradle contract suites exit `0`.
* **Rollback**: Branch isolation (`feature/ATT-8147`) permits clean revert without affecting `sprint/2026-47.1` or `develop`.
