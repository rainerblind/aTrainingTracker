# Stage 2: Requirement & Test Specification - ATT-8147: Complete bidirectional SYS-to-SWE traceability for System Refinement domains

**Ticket**: [ATT-8147](https://rainerblind.atlassian.net/browse/ATT-8147)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PRO-103` (*Complete Bidirectional SYS-to-SWE Traceability & System-Refinement Classification*; extends `REQ-PRO-076`, `REQ-PRO-102`)  
**Test Spec ID**: `TST-PRO-103`  
**Branch**: `feature/ATT-8147` | **Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (REQ-PRO-103)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Target Status: `[Planned]`
* **REQ-PRO-103.1 (Structured `System Requirement(s)` Attribute & Prefix Closure)**: `verify_system_software_traceability()` in `tools/verify_requirement_governance.py` SHALL parse `- **System Requirement(s)**:` (`SYSREQ-*` IDs or `Derived (SW-only)` with rationale) and classify the `7` `RAD`/`CLM` REQs cleanly in `SYSTEM_REFINED_PREFIXES` / `docs/system/system_software_traceability.md` §3.1 while keeping `REQ-PRO-*` in `SWE_DERIVED_PREFIXES`.
* **REQ-PRO-103.2 (100% System-Refinement Coverage & Matrix Synchronization)**: All `306` unlinked `SYSTEM_REFINED_PREFIXES` REQs (`129` `req_tracking_cockpit.md` incl. `REQ-TRK-082`, `107` `req_data_migration_export.md`, `70` `req_maps_navigation.md`), plus the `49` traced REQs and `7` `RAD`/`CLM` REQs, SHALL carry `- **System Requirement(s)**:` (`SYSREQ-*` or `Derived (SW-only)`), achieving `100.0%` forward (`12/12`) and `100.0%` reverse classification, and synchronizing `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, and `docs/system/system_software_traceability.md` without violating `System*RequirementsContractTest` or `verify_architecture_and_system_specs()`.
* **REQ-PRO-103.3 (Reverse Enforcement Flag & Ratchet Gate)**: `tools/verify_requirement_governance.py` SHALL add `--check-reverse-sys-trace` (with `fail_on_unclassified_swe` and `LEGACY_UNLINKED_SYS_REFINEMENT_REQS = frozenset()` ratchet) and report `SYSREQ`-linked, `Derived (SW-only)`, and unclassified counts in `--report-sys-coverage` without breaking `test_scenario_41` or `test_scenario_67` synthetic fixtures.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-076` (`ATT-6163`) and `REQ-PRO-102` (`ATT-8146`) via net-new `REQ-PRO-103`. Targets `tools/verify_requirement_governance.py`, `req_tracking_cockpit.md`, `req_data_migration_export.md`, `req_maps_navigation.md`, `req_aftermath_periods.md`, `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, and `docs/system/system_software_traceability.md`.
* **Historical Origin & Commit Trace**: `ATT-6163` introduced `REQ-PRO-076`; `ATT-8146` (`5a263429`) exposed the `49/355` (`13.8%`) reverse coverage gap; `ATT-8147` (Sprint `2026-47.1`, Epic `ATT-8138`) completes bidirectional classification.
* **Root Reason for Existing Formulation**: Forward-only `12/12` check omitted `306` product requirements lacking parent `SYSREQ-*` or `Derived (SW-only)` links.
* **Preservation of Core Invariants**: Preserves `12` declared `SYSREQ-*` IDs (`declared == allocated`), `REQ-PRO-102.2` exclusion of `REQ-PRO-*`, `test_scenario_40..43` & `66..68` compatibility, `System*ContractTest` section/filename invariants, zero `app/src/main/` edits, and `ANT-Android-SDKs@160000`.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Complete Classification)**: *Given* every `SYSTEM_REFINED_PREFIXES` REQ (incl. `RAD`/`CLM`), *When* `--check-reverse-sys-trace` runs, *Then* `100.0%` have a valid `SYSREQ-*` link or `Derived (SW-only)` rationale (`0` unclassified) and exit code is `0`.
* **Criterion 2 (Matrix & Contract Integrity)**: *Given* updated `docs/system/*.md` matrices, *When* `--verify-sys-trace --report-sys-coverage` and `System*ContractTest` run, *Then* all `12/12` `SYSREQ`s list their refining `REQ-*`s, reverse classification is `100.0%`, and all contract checks pass.
* **Criterion 3 (Fixture Compatibility)**: *Given* `test_scenario_41`, `test_scenario_67`, and `TST-PRO-103.*`, *When* `tools/test_verify_requirement_governance.py` executes, *Then* all tests pass cleanly.

---

## 4. Test Specification (TST-PRO-103)
* **TST-PRO-103.1 (`System Requirement(s)` Parser & Reverse Gate Test)**: Verify `- **System Requirement(s)**:` parsing for `SYSREQ-*` and `Derived (SW-only)`, `RAD`/`CLM` prefix classification, and rejection of unclassified REQs under `fail_on_unclassified_swe=True`.
* **TST-PRO-103.2 (Live 100% Bidirectional Coverage Test)**: Verify `--check-reverse-sys-trace` and `--verify-sys-trace --report-sys-coverage` pass on live repo with `0` unclassified `SYSTEM_REFINED_PREFIXES` REQs (`100.0%`) and `REQ-PRO-*` excluded.
* **TST-PRO-103.3 (System Contract & Fixture Compatibility Test)**: Verify `SystemLiveTrackingRequirementsContractTest`, `SystemDataExchangeRequirementsContractTest`, `SystemRequirementsSpecificationContractTest`, `SystemVerificationProtocolContractTest`, `AspiceSweAuditContractTest`, and `test_scenario_40..43`/`66..68` pass with exit code `0`.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-103.1` | Unit | `tools/verify_requirement_governance.py` | `REQ-PRO-103.1`, `REQ-PRO-103.3` | Planned |
| `TST-PRO-103.2` | Unit | `tools/test_verify_requirement_governance.py` | `REQ-PRO-103.2`, `REQ-PRO-103.3` | Planned |
| `TST-PRO-103.3` | Contract | `System*ContractTest.kt`, `AspiceSweAuditContractTest.kt` | `REQ-PRO-103.2` | Planned |
