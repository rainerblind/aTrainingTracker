# Stage 1 Analysis: ATT-8147 - Complete bidirectional SYS-to-SWE traceability for System Refinement domains

**Ticket**: [ATT-8147](https://rainerblind.atlassian.net/browse/ATT-8147)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Branch**: `feature/ATT-8147` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Finding `M2` (SWE.1 BP5, SYS.2/SYS.3 interface), `ATT-8146` fixed `--report-sys-coverage` to reveal that only `49 / 355` (`13.8%`) of software requirements in `SYSTEM_REFINED_PREFIXES` trace to a `SYSREQ-*`. The remaining `306` REQs have neither a structured `SYSREQ-*` parent link nor an explicit `Derived (SW-only)` classification with rationale, preventing bidirectional impact analysis.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Missing Attribute & Enforcement**: `docs/requirements/*.md` REQs have no structured `- **System Requirement(s)**:` attribute (even `REQ-TRK-071` only mentions `SYSREQ-TRK-004` in prose), and `verify_system_software_traceability()` in `tools/verify_requirement_governance.py:3664-3844` has no `Derived (SW-only)` parser or unclassified-REQ ratchet gate.
* **Independent Peer Review & Verification (Live `5a263429`)**:
  * Audited `306` unlinked `SYSTEM_REFINED_PREFIXES` REQs across `3` shards (`49` linked via `docs/system/*.md` + `REQ-TRK-071`):
    * `req_tracking_cockpit.md`: `129` unlinked (`CON` 36, `FIL` 16, `SEN` 8, `TRK` 68, `WEA` 1; `23` linked).
    * `req_data_migration_export.md`: `107` unlinked (`DAT` 31, `EXP` 13, `EXT` 11, `MIG` 52; `27` linked).
    * `req_maps_navigation.md`: `70` unlinked (`MAP` 66, `GEO` 2, `NAV` 2; `0` linked).
  * Found `7` unclassified prefixes (`RAD` 3, `CLM` 4) absent from both `SYSTEM_REFINED_PREFIXES` and `SWE_DERIVED_PREFIXES` (`L3648-3661`).
  * Audited Chesterton's Fence against ticket text (`"process stability"`): `docs/system/system_software_traceability.md` §3.1 and `REQ-PRO-102.2` (`ATT-8146`) already establish `req_process_stability.md` (`REQ-PRO-*`) as `SWE-Derived` and explicitly exclude `REQ-PRO-*` from `sys_to_swe`.
  * Audited `System*ContractTest.kt` (`SystemLiveTrackingRequirementsContractTest`, `SystemDataExchangeRequirementsContractTest`, `SystemVerificationProtocolContractTest`, `SystemRequirementsSpecificationContractTest`) & `verify_architecture_and_system_specs()` (`L3552-3645`): `docs/system/sysreq_*.md` edits must preserve `## 3.` section boundaries, authentic on-disk `.kt`/`.java`/`.xml` filenames, and `declared_ids == allocated_ids`.
  * Audited `tools/test_verify_requirement_governance.py`: `test_scenario_41` and `test_scenario_67` use intentionally unlinked `REQ-TRK-002` in temp dirs, so strict unclassified-REQ failure must use a ratchet/opt-in flag (`--enforce-sys-trace` / `LEGACY_UNLINKED_SYS_REFINEMENT_REQS = frozenset()`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**: (1) Add `- **System Requirement(s)**:` parser (`SYSREQ-*` or `Derived (SW-only) — <rationale>`) + CI check & ratchet in `tools/verify_requirement_governance.py`; (2) Classify all `355` `SYSTEM_REFINED_PREFIXES` REQs (`306` unlinked + `49` existing) across `req_tracking_cockpit.md`, `req_data_migration_export.md`, `req_maps_navigation.md` with `- **System Requirement(s)**:` (`SYSREQ-*` or `Derived (SW-only)`); (3) Sync `docs/system/sysreq_live_tracking.md`, `sysreq_data_exchange.md`, and `system_software_traceability.md` Section 4/2 matrices for tracking & data exchange; (4) Extend `--report-sys-coverage` to list all refining REQs per `SYSREQ` + `Derived (SW-only)` metrics and add unit tests.
* **Out-of-Scope Non-Goals**: Re-adding `REQ-PRO-*` into `SYSREQ` refinements (`REQ-PRO-102.2`); altering `app/src/main/` or `SYSREQ` counts (`12` declared `SYSREQ`s stay fixed).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-076` (`ATT-6163`) & `REQ-PRO-102` (`ATT-8146`) in `docs/requirements/req_process_stability.md`.
* **Historical Origin & Commit Trace**: `ATT-6163` (`SYS.2`/`SWE.1` baseline) & `ATT-8146` (`5a263429`, Sprint `2026-47.1`).
* **Root Reason for Existing Formulation**: Enforced `12/12` forward `SYSREQ` coverage without enforcing reverse `System-Refinement` attributes.
* **Preservation of Core Invariants**: Preserve `12/12` `SYSREQ` parity, `REQ-PRO-102.2` exclusion of `SWE_DERIVED_PREFIXES`, `test_scenario_40..43` & `66..68` compatibility, `System*ContractTest` invariants, and `verify_requirement_governance.py` archaeology checks when modifying existing `REQ-*` entries.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-103` (`req_process_stability.md`) + `- **System Requirement(s)**:` additions across `355` `SYSTEM_REFINED_PREFIXES` REQs in `req_tracking_cockpit.md`, `req_data_migration_export.md`, `req_maps_navigation.md`.
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, `docs/system/system_software_traceability.md`.
* **Verification Impact & Test Surface**: `tools/test_verify_requirement_governance.py`, `AspiceSweAuditContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SystemDataExchangeRequirementsContractTest`, `SystemVerificationProtocolContractTest`, `SystemRequirementsSpecificationContractTest`.
* **Risk Assessment & Invariants**: **MEDIUM** (`3 SP`, touches 3 `req_*.md` shards + 3 `docs/system/*.md` matrices; zero `app/src/main/` edits; `ANT-Android-SDKs` stays `160000`).

---

## 6. Architectural Strategy & High-Level Solution
* Support `- **System Requirement(s)**: <SYSREQ-ID[, ...]|Derived (SW-only): <rationale>>` in `verify_system_software_traceability()` with a `0`-entry ratchet (`LEGACY_UNLINKED_SYS_REFINEMENT_REQS = frozenset()`) on live repo while preserving `test_scenario_41`/`67` synthetic dir isolation.
* Populate `- **System Requirement(s)**:` across all `355` `SYSTEM_REFINED_PREFIXES` REQs (`100%` classified: `SYSREQ-*` or `Derived (SW-only)`) and sync `docs/system/` traceability tables.
