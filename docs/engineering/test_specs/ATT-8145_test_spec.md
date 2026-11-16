# Stage 2: Requirement & Test Specification - ATT-8145: Triage Verified requirements and test specs without executable test evidence

**Ticket**: [ATT-8145](https://rainerblind.atlassian.net/browse/ATT-8145)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PRO-101` (*Unbacked Status Triage, Test-Tag Reconciliation & Ratchet Reduction*; extends `REQ-PRO-099.3`)  
**Test Spec ID**: `TST-PRO-101`  
**Branch**: `feature/ATT-8145` | **Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (REQ-PRO-101)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Target Status: `[Planned]` (becomes `[Verified]` upon Stage 5 evidence)
* **REQ-PRO-101.1 (Deterministic Unbacked Triage & Machine-Readable Ledger)**: `tools/verify_requirement_governance.py` SHALL implement `--triage-unbacked` with deterministic CSV export under canonical `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv` (keeping `ALLOWED_ENGINEERING_ENTRIES` intact), classifying all `369` active unbacked `[Verified]` TSTs into `unit-test-backed`, `UI/manual-only`, `obsolete/duplicate`, and `missing test code` and flagging stale already-backed `LEGACY_UNBACKED_VERIFIED_TSTS` entries.
* **REQ-PRO-101.2 (Existing Test-Suite Tag Reconciliation)**: `unit-test-backed` TST specifications (`157` items) whose logic already exists in `*Test.kt`, `*Test.java`, or `tools/test_*.py` suites SHALL retain `[Verified]` strictly after explicit `TST-*` tags are added to test code so `extract_test_code_tags()` binds them deterministically.
* **REQ-PRO-101.3 (Truthful Downgrade & Domain Follow-Up Allocation)**: Unattested `UI/manual-only` (incl. `TST-MIG-006`), `obsolete/duplicate`, `missing test code` TSTs, the `14` `[Verified]` baselined orphans in `tools/traceability_baseline.json`, and `[Verified]` REQs citing only unbacked TSTs SHALL be downgraded to `[Implemented]` or `[Planned]` using strictly `AspiceSweAuditContractTest`-valid tokens (`[Verified|Implemented|Planned|Open]`), with `1–3 SP` domain follow-up tickets logged for missing verification.
* **REQ-PRO-101.4 (Monotonic Ratchet Reduction & Index Parity)**: `LEGACY_UNBACKED_VERIFIED_TSTS` SHALL be reconciled downward to `0` (or a documented, ticket-referenced residual cap), `docs/requirements.md` and `docs/tests.md` synchronized via `--sync-indexes`, and `verify_requirement_governance.py` + `--check-verified-evidence` SHALL exit `0`.
* **REQ-PRO-101.5 (Höchstmaß Mirror Parity & Ratchet Test Maintenance)**: `tools/hoechstmass/tools/verify_requirement_governance.py` (and `test_hoechstmass_tools.py`) SHALL preserve decoupled parity (`HoechstmassToolingContractTest`), and `tools/test_verify_requirement_governance.py` (`test_scenario_60`, `test_scenario_61`) SHALL be updated cleanly using parameterized `baseline_allowlist` / reconciled fixtures.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-099.3` / `TST-PRO-099.3` (`LEGACY_UNBACKED_VERIFIED_TSTS`, `405` items) via net-new `REQ-PRO-101` / `TST-PRO-101`. Targets `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/*.md`, `docs/tests/*.md`, `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`.
* **Historical Origin & Commit Trace**: `ATT-8143` (`f9d9b9e5`, Sprint `2026-46.8`) created `LEGACY_UNBACKED_VERIFIED_TSTS` (`405` IDs); `ATT-8145` (Sprint `2026-47.1`) executes M1 triage remediation.
* **Root Reason for Existing Formulation**: Prevented pre-existing unbacked `[Verified]` items from breaking CI while blocking new unbacked `[Verified]` additions.
* **Preservation of Core Invariants**: Monotonic non-increasing ratchet (`|B_{t+1}| <= |B_t|`), valid `[Verified|Implemented|Planned|Open]` suffixes, `--sync-indexes` master parity, clean-worktree safety (`REQ-PRO-099.5`), zero `app/src/main/` edits, `ANT-Android-SDKs` untouched (`160000`).

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Authoritative Triage Ledger)**: *Given* the 7 living-doc shards and test suites, *When* `--triage-unbacked` runs, *Then* all unbacked `[Verified]` TSTs are deterministically classified and written to `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv` without violating `ALLOWED_ENGINEERING_ENTRIES`.
* **Criterion 2 (Tagging, Downgrade & Ratchet Reduction)**: *Given* reconciled `TST-*` tags on `unit-test-backed` tests and downgraded unbacked TSTs/REQs/orphans, *When* `--sync-indexes` and `--check-verified-evidence` run, *Then* `LEGACY_UNBACKED_VERIFIED_TSTS` is reduced downward to `0` (or documented residual cap), all headings match `AspiceSweAuditContractTest`, and governance exits `0`.
* **Criterion 3 (Test & Höchstmaß Mirror Parity)**: *Given* updated `test_scenario_60`/`61` and `tools/hoechstmass/tools/`, *When* Python unittests, `HoechstmassToolingContractTest`, and `AspiceSweAuditContractTest` run, *Then* all pass with exit code `0`.

---

## 4. Test Specification (TST-PRO-101)
* **TST-PRO-101.1 (Triage Classifier & Ledger Test)**: Verify `--triage-unbacked` deterministic categorization, stale baseline detection, and `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv` compatibility with `verify_engineering_directory_structure()`.
* **TST-PRO-101.2 (Test-Tag Reconciliation Test)**: Verify `extract_test_code_tags()` resolves newly tagged `unit-test-backed` suites and targeted Gradle / Python unit tests pass.
* **TST-PRO-101.3 (Heading Token & Master Parity Test)**: Verify `AspiceSweAuditContractTest` (`testLivingDocShards_allHeadingsStandardized`, `testMasterTableParity_matchesDomainShards`) and `--sync-indexes` pass cleanly after downgrades.
* **TST-PRO-101.4 (Ratchet Reduction & Scenario 60/61 Test)**: Verify `--check-verified-evidence` passes with reduced `LEGACY_UNBACKED_VERIFIED_TSTS` and `test_scenario_60`/`61` pass without brittle `TST-BUG-001` assumptions.
* **TST-PRO-101.5 (Höchstmaß Mirror Parity Test)**: Verify `HoechstmassToolingContractTest` and `tools/hoechstmass/tools/test_hoechstmass_tools.py` pass with zero hardcoded `ATT` identifiers.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-101.1` | Unit | `tools/verify_requirement_governance.py` | `REQ-PRO-101.1` | Planned |
| `TST-PRO-101.2` | Unit | `*Test.kt/.java`, `tools/test_*.py` | `REQ-PRO-101.2` | Planned |
| `TST-PRO-101.3` | Contract | `AspiceSweAuditContractTest.kt` | `REQ-PRO-101.3` | Planned |
| `TST-PRO-101.4` | Unit | `tools/test_verify_requirement_governance.py` | `REQ-PRO-101.4`, `REQ-PRO-101.5` | Planned |
| `TST-PRO-101.5` | Contract | `HoechstmassToolingContractTest.kt` | `REQ-PRO-101.5` | Planned |
