# Stage 1 Analysis: ATT-8145 - Triage Verified requirements and test specs without executable test evidence

**Ticket**: [ATT-8145](https://rainerblind.atlassian.net/browse/ATT-8145)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Branch**: `feature/ATT-8145` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Finding `M1` (SWE.1 BP5, SWE.4 BP4, SWE.6 BP4), `ATT-8143` (`f9d9b9e5`) added `--check-verified-evidence` with a `405`-item ratchet baseline (`LEGACY_UNBACKED_VERIFIED_TSTS`). On `sprint/2026-47.1` (`f62f2283`), `369` `[Verified]` TSTs across `7` shards still lack executable test tags (`793` backed), `332` `[Verified]` REQ headings cite only unbacked TSTs, and `14/15` baselined orphans in `tools/traceability_baseline.json` remain `[Verified]`. Without triaging these items, `[Verified]` status misrepresents true verification coverage.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Origin**: Legacy Stage 5 `auto_verify_living_docs_on_stage5()` flipped `[Verified]` unconditionally prior to `ATT-8143`. Meanwhile `36` of the `405` baseline IDs already became backed (`35` `TST-PRO-*` in `tools/test_*.py`) or non-`[Verified]` (`TST-PRO-088`), leaving `369` stale tolerance entries.
* **Independent Peer Review & Verification**:
  * Verified `tools/verify_requirement_governance.py:54-113` (`LEGACY_UNBACKED_VERIFIED_TSTS`, 405 IDs), `:2296-2350` (`extract_test_code_tags`), and `:2826-2930` (`verify_verified_test_evidence`: 369 unbacked, 793 backed).
  * Categorized all `369` unbacked `[Verified]` TSTs across `docs/tests/*.md`:
    * `unit-test-backed` (`157`): existing `*Test.kt/.java` or `test_*.py` suites lack `TST-*` tags (`tst_ui_design_system` 98, `tst_process_stability` 22, `tst_data_migration_export` 13, `tst_maps_navigation` 12, `tst_settings_profiles` 8, `tst_tracking_cockpit` 4).
    * `UI/manual-only` (`68`): UI/manual procedures without attested protocols, e.g. `TST-MIG-006` (`tst_data_migration_export.md:328`) (`ui` 24, `settings` 18, `aftermath` 9, `data` 8, `process` 7, `maps` 1, `tracking` 1).
    * `obsolete/duplicate` (`7`): superseded specs (`ui` 3, `maps` 2, `process` 2).
    * `missing test code` (`137`): no matching test file (`process` 42, `ui` 40, `settings` 26, `data` 14, `maps` 7, `aftermath` 6, `tracking` 2).
  * Audited cross-cutting `tools/` tests: `tools/test_verify_requirement_governance.py:2386-2432` (`test_scenario_60`, `test_scenario_61`) hardcodes `TST-BUG-001` as a baseline item; `AspiceSweAuditContractTest.kt:89-116` enforces standard heading suffixes `[Verified|Implemented|Planned|Open]`; `HoechstmassToolingContractTest.kt` validates `tools/hoechstmass/`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**: (1) Add deterministic `--triage-unbacked` classification report in `tools/verify_requirement_governance.py`; (2) Tag `unit-test-backed` existing test suites where only the `TST-*` mapping is missing; (3) Downgrade unattested `UI/manual-only` (incl. `TST-MIG-006`), `obsolete/duplicate`, `missing test code`, and `14` `[Verified]` orphans + dependent unbacked `[Verified]` REQs to `[Implemented]`; (4) Lower `LEGACY_UNBACKED_VERIFIED_TSTS` ratchet baseline and update `tools/test_verify_requirement_governance.py` + master index parity (`--sync-indexes`); (5) File domain follow-up backlog tickets (`1-3 SP`) for missing test implementations.
* **Out-of-Scope Non-Goals**: Authoring net-new unit/UI test implementations or manual execution records for the `137` missing-code / `68` UI-manual specs; modifying `app/src/main/` production code.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-099.3` / `TST-PRO-099.3` (`docs/requirements/req_process_stability.md:5277`, `docs/tests/tst_process_stability.md:3282`) and `LEGACY_UNBACKED_VERIFIED_TSTS`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-8143` (`f9d9b9e5`) during Sprint `2026-46.8`.
* **Root Reason for Existing Formulation**: Prevented legacy debt from breaking CI while gating net-new `[Verified]` specs.
* **Preservation of Core Invariants**: Preserve monotonic ratchet (`|B_{t+1}| <= |B_t|`), standard `[Verified|Implemented|Planned|Open]` heading suffixes, master table parity, and clean-worktree safety (`REQ-PRO-099.5`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-PRO-099.3` (ratchet baseline reduction), `14` `[Verified]` orphans in `tools/traceability_baseline.json`, and `[Verified]` REQs in `docs/requirements/*.md` whose cited TSTs are downgraded.
* **Affected Architecture Elements**: `tools/verify_requirement_governance.py`, `docs/tests/*.md`, `docs/requirements/*.md`, `docs/tests.md`, `docs/requirements.md`, existing `*Test.kt/.java` and `tools/test_*.py` tag headers.
* **Verification Impact & Test Surface**: `tools/test_verify_requirement_governance.py` (`test_scenario_60`/`61` baseline fixture), `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `HoechstmassToolingContractTest`, `AspiceSweAuditContractTest`, and Tier-1 unit tests for tagged `.kt`/`.java` classes.
* **Risk Assessment & Invariants**: **MEDIUM** (`3 SP`; touches 7 living-doc shards and cross-cutting `tools/` tests; zero `app/src/main/` changes; `ANT-Android-SDKs` stays `160000`).

---

## 6. Architectural Strategy & High-Level Solution
* **Automated Triage Classifier**: Add `--triage-unbacked` in `tools/verify_requirement_governance.py` to categorize unbacked `[Verified]` TSTs deterministically across shards (`unit-test-backed`, `UI/manual-only`, `obsolete/duplicate`, `missing test code`) and detect already-backed stale entries.
* **Tagging, Downgrade & Ratchet Reduction**: Add missing `TST-*` tags to verified existing test suites, downgrade remaining unbacked TSTs and unbacked `[Verified]` REQs to `[Implemented]`, shrink `LEGACY_UNBACKED_VERIFIED_TSTS`, keep `test_verify_requirement_governance.py` non-brittle, and run `--sync-indexes`.
* **Domain Follow-Up Split**: Log per-domain backlog tickets (`1-3 SP` each) for genuinely missing automated/manual verification so `ATT-8145` remains strictly within `3 SP`.
