# Stage 2: Requirement & Test Specification - ATT-8203

**DOC-ID**: `DOC-TST-ATT-8203` | **Ticket**: [ATT-8203](https://rainerblind.atlassian.net/browse/ATT-8203) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8203`  
**Requirement Mapping**: `REQ-PRO-129` (`[Planned]`, refining `REQ-ARC-005`, `REQ-PRO-050`, `REQ-PRO-114`, `REQ-PRO-093`) | **Test Spec ID**: `TST-PRO-129` (`[Planned]`)  
**Localization Parity**: `N/A - no UI strings` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-PRO-129` `[Planned]`)
* **Attributes**: Priority: `Medium` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **Implementation**: `docs/architecture/adr_001_java_core_services_migration.md`, `docs/architecture.md`, `docs/architecture/detailed_design/workout_repository.md`, `JavaCoreMigrationStrategyContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/tests/tst_process_stability_part{1,2}.md`
* **REQ-PRO-129.1 (ADR-001 `Keep`-vs-`Migrate` & Java Count Reconciliation — Risk: MEDIUM)**: `docs/architecture/adr_001_java_core_services_migration.md` (§§1–8) and `docs/architecture.md` (§5.3, §5.5) SHALL consistently reflect the PO-frozen `Keep` decision (`ATT-8115`) alongside the `29` migrated `.kt` classes (`ATT-7903`/`7905`/`7906` `Completed`), reconcile `.java` inventory to `120` remaining `.java` files (`<= 149`), set `Target Release` to `V5.0.0`, preserve `Status: Reviewed` (`Pending PO Sign-Off`), and extend `JavaCoreMigrationStrategyContractTest.kt` with `Keep`-vs-`Migrate` consistency and count assertions.
* **REQ-PRO-129.2 (God-Class LoC Ratchet & `WorkoutRepository` SWE.3 Design)**: `ArchitectureConformanceContractTest.kt` (`LEGITIMATE_GOVERNANCE_GUARDS == 22`) SHALL enforce a downward-ratcheting LoC ceiling across all `16` `> 1,500` LoC `.kt`/`.java` files in `app/src/main/` (`WorkoutRepository.kt` `5,117` .. `ImportBackupTabsScreen.kt` `1,513`); `docs/architecture/detailed_design/workout_repository.md` (`Status: Reviewed`) + `docs/architecture.md` §3.5 SHALL add `WorkoutRepository` SWE.3 design (`3` -> `4` docs), enforced by `DetailedDesignParityContractTest.kt`.
* **REQ-PRO-129.3 (Fail-Closed Requirements/Tests Directory Layout Enforcement)**: `tools/verify_requirement_governance.py` SHALL reject non-`req_*.md` entries in `docs/requirements/` (e.g. `SCRUM-*_plan.md`) and non-`tst_*.md` entries in `docs/tests/`, backed by offline `tempfile` unit tests in `tools/test_verify_requirement_governance.py` (`Rule 5`).
* **REQ-PRO-129.4 (Living Doc Shard Split & Parity)**: `docs/tests/tst_process_stability.md` SHALL be split via `tools/shard_living_docs.py` into `docs/tests/tst_process_stability_part{1,2}.md` (`<= 4,000` lines per shard under `--check-size --strict`), with `docs/requirements.md` and `docs/tests.md` synced via `--sync-indexes`.
* **Given-When-Then Acceptance Criteria**:
  * *Given* reconciled `ADR-001` and `docs/architecture.md`, *When* `JavaCoreMigrationStrategyContractTest` runs, *Then* `Keep`-vs-`Migrate` consistency, `120 <= 149` `.java` count, and `3` `Completed` child tickets pass while contradictory `Migrate` claims fail.
  * *Given* `16` `> 1,500` LoC files and `workout_repository.md`, *When* `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, and `SourceTextReadingAuditContractTest` run, *Then* upward LoC growth or net-new `> 1,500` LoC files fail, `4`-doc SWE.3 parity passes, and `LEGITIMATE_GOVERNANCE_GUARDS == 22`.
  * *Given* `docs/requirements/` and `docs/tests/` (live vs `tempfile` non-`req_*.md`/`non-tst_*.md` fixtures) and split `tst_process_stability_part{1,2}.md`, *When* `test_verify_requirement_governance.py`, `shard_living_docs.py --check-size --strict`, and `verify_requirement_governance.py` run, *Then* misplaced fixtures fail closed and live governance/size checks exit `0`.
* **System Invariants**: Zero `app/src/main/` edits; `ANT-Android-SDKs` untouched; `LEGITIMATE_GOVERNANCE_GUARDS == 22`; `app/source-reading-baseline.txt == 105`; `Localization Parity: N/A - no UI strings`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-129` (`.1`–`.4`), refining `REQ-ARC-005` (`ADR-001`), `REQ-PRO-050` (`ArchitectureConformanceContractTest`), `REQ-PRO-114` (`DetailedDesignParityContractTest`), and `REQ-PRO-093` (`docs/requirements/` & `docs/tests/` layout, shard split).
* **Historical Origin & Commit Trace**: `1e95d2c3` (`ATT-7566`), `195a9f1f` (`ATT-8115` PO `Keep` pivot), `2de2a41c` (`ATT-8164`), `ATT-8159` (`REQ-PRO-114`), `ATT-8206` (`REQ-PRO-127`).
* **Root Reason for Existing Formulation**: `REQ-ARC-005` locked `<= 149` Java classes and 3 child tickets (`ATT-7903`/`7905`/`7906`); `REQ-PRO-049.2` locked `22` source-reading guards; `verify_requirement_governance.py` tolerated legacy non-`req_*.md` files during migration.
* **Preservation of Core Invariants**: Preserves `<= 149` bound, `3` child tickets, `22` `LEGITIMATE_GOVERNANCE_GUARDS`, `105` `app/source-reading-baseline.txt`, `<= 4,000`-line shard cap, and zero `app/src/main/` edits.

---

## 2. Test Specification (`TST-PRO-129` `[Planned]`)
* **`TST-PRO-129.1` (`JavaCoreMigrationStrategyContractTest.kt`)**: Assert `ADR-001` §§1–8 & `docs/architecture.md` §5.3/§5.5 enforce PO `Keep` consistency, `120 <= 149` `.java` count reconciliation, `ATT-7903`/`7905`/`7906` `Completed`, and `V5.0.0`.
* **`TST-PRO-129.2` (`ArchitectureConformanceContractTest.kt` & `DetailedDesignParityContractTest.kt`)**: Verify `16`-file `> 1,500` LoC downward ratchet (`WorkoutRepository.kt` `5,117` .. `ImportBackupTabsScreen.kt` `1,513`), `workout_repository.md` SWE.3 Mermaid/reflection parity across `4` detailed-design docs, `LEGITIMATE_GOVERNANCE_GUARDS == 22`, and `AspiceSweAuditContractTest`.
* **`TST-PRO-129.3` (`tools/test_verify_requirement_governance.py`)**: Offline `tempfile` unit tests (`Rule 5`) verifying fail-closed rejection of non-`req_*.md` in `docs/requirements/` and non-`tst_*.md` in `docs/tests/`, plus live repo pass.
* **`TST-PRO-129.4` (`tools/shard_living_docs.py` & `tools/verify_requirement_governance.py`)**: Verify `tst_process_stability_part{1,2}.md` `<= 4,000` lines (`--check-size --strict`) and full governance `exit 0` (`Localization Parity: N/A - no UI strings`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-129.1` | Gradle Contract | `JavaCoreMigrationStrategyContractTest.kt`, `ADR-001`, `docs/architecture.md` | `REQ-PRO-129.1`, `REQ-ARC-005` | Planned |
| `TST-PRO-129.2` | Gradle Contract | `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, `workout_repository.md` | `REQ-PRO-129.2`, `REQ-PRO-050`, `REQ-PRO-114` | Planned |
| `TST-PRO-129.3` | Python Unit (Offline) | `verify_requirement_governance.py` layout check & `test_verify_requirement_governance.py` | `REQ-PRO-129.3`, `REQ-PRO-093` | Planned |
| `TST-PRO-129.4` | Governance + Size Gate | `tst_process_stability_part{1,2}.md`, `--check-size --strict`, `--sync-indexes` | `REQ-PRO-129.4`, `REQ-PRO-093.3/.4` | Planned |
