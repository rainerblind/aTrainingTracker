# Stage 5: Walkthrough & Verification - ATT-8203: Reconcile ADR-001, Bound God Classes & Enforce Requirements Layout

**Ticket**: [ATT-8203](https://issue-tracker/ATT-8203) (`2 SP`)  
**Sub-task**: Pending `submit-stage` creation (`[Test]`)  
**Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-11-18_01_Früh`  
**Requirement Mapping**: `REQ-PRO-129` (`REQ-PRO-129.1`–`REQ-PRO-129.4`)  
**Test Mapping**: `TST-PRO-129` (`TST-PRO-129.1`–`TST-PRO-129.4`)  
**Branch**: `feature/ATT-8203`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
Resolved ASPICE 4.0 Gap #6 (`SWE.2 BP5`, `SWE.3 BP1/BP3`, `SUP.8 BP1`) across all four pillars on `feature/ATT-8203` with **zero `app/src/main/` edits**, `ANT-Android-SDKs` untouched (`160000` gitlink), and `Localization Parity: N/A - no UI strings`:
1. **ADR-001 Reconciliation (`REQ-PRO-129.1`)**: Reconciled residual `Keep`-vs-`Migrate` contradictions in `docs/architecture/adr_001_java_core_services_migration.md` (`v1.2.0`, `V5.0.0`, `Status: Reviewed`, `120` remaining `.java` + `29` migrated `.kt`, `ATT-7903`/`7905`/`7906` `Completed`) and `docs/architecture.md` (`v1.0.2` §5.3/§5.5), enforced by `JavaCoreMigrationStrategyContractTest`.
2. **God-Class LoC Ratchet & `WorkoutRepository` SWE.3 Design (`REQ-PRO-129.2`)**: Added 16-file `> 1,500` LoC downward ratchet in `ArchitectureConformanceContractTest.kt` (`LEGITIMATE_GOVERNANCE_GUARDS == 22`), authored `docs/architecture/detailed_design/workout_repository.md` (`DOC-ARC-DD-WKO`, `v1.0.0`, `Status: Reviewed`), indexed it in `docs/architecture.md` §3.5 (`4` SWE.3 units), and verified parity in `DetailedDesignParityContractTest.kt`.
3. **Directory Layout Enforcement (`REQ-PRO-129.3`)**: Added fail-closed `verify_living_doc_directory_layout()` (`--check-living-doc-layout`) in `tools/verify_requirement_governance.py` rejecting non-`req_*.md` / non-`tst_*.md` entries, backed by offline `tempfile` tests in `tools/test_verify_requirement_governance.py` (`Rule 5`).
4. **Shard Split & Parity (`REQ-PRO-129.4`)**: Split `docs/tests/tst_process_stability.md` into `tst_process_stability_part{1,2}.md` (`2,494` / `1,463` lines) and synced master indexes (`1244` REQs / `1251` TSTs).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-129.1` | `TST-PRO-129.1` | Gradle Contract (`JavaCoreMigrationStrategyContractTest`, `5/5`) | **PASSED** | `Verified` |
| `REQ-PRO-129.2` | `TST-PRO-129.2` | Gradle Contract (`ArchitectureConformanceContractTest` `11/11`, `DetailedDesignParityContractTest` `3/3`, `SourceTextReadingAuditContractTest` `4/4`) | **PASSED** | `Verified` |
| `REQ-PRO-129.3` | `TST-PRO-129.3` | Python Offline Unit (`TestLivingDocDirectoryLayout`, `4/4`, `Rule 5`) | **PASSED** | `Verified` |
| `REQ-PRO-129.4` | `TST-PRO-129.4` | Governance & Size Gate (`shard_living_docs.py`, `verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Contract Suites (Reused from Stage 4 Unchanged `app/` SHA, `Rule 7.5`)
```text
JavaCoreMigrationStrategyContractTest:   5 tests, 0 failures, 0 errors, 0 skipped
ArchitectureConformanceContractTest:    11 tests, 0 failures, 0 errors, 0 skipped
DetailedDesignParityContractTest:        3 tests, 0 failures, 0 errors, 0 skipped
SourceTextReadingAuditContractTest:      4 tests, 0 failures, 0 errors, 0 skipped (22 guards, 105 baseline)
NonFunctionalRequirementsContractTest:   3 tests, 0 failures, 0 errors, 0 skipped
SystemArchitectureDesignContractTest:    9 tests, 0 failures, 0 errors, 0 skipped
TOTAL:                                  35 tests, 0 failures, 0 errors, 0 skipped
```

### Python Governance & Living-Doc Gates (`Rule 7.6` Offline Boundary — No Emulator/Device Required)
```text
python3 -m unittest tools.test_verify_requirement_governance.TestLivingDocDirectoryLayout -> 4/4 OK (0.003s)
python3 -m unittest tools/test_verify_requirement_governance.py -> 103/104 OK (1 pre-existing unrelated ATT-8848 TST-TRK-087 failure in docs/tests/tst_tracking_cockpit.md:3410)
python3 tools/shard_living_docs.py --verify-only -> PASS: 28 living shards across 9 domains (exit 0)
python3 tools/shard_living_docs.py --check-size --strict -> PASS: 28/28 shards <= 4000 lines (exit 0)
python3 tools/verify_requirement_governance.py --repo-root . --base-ref 2d89271e --sync-indexes --check-size --check-doc-headers --check-conflict-markers -> exit 0
python3 tools/verify_requirement_governance.py --repo-root . --check-living-doc-layout -> exit 0
python3 tools/verify_requirement_governance.py --repo-root . -> exit 0
has_verified_test_evidence('TST-PRO-129') -> (True, 'Verified by 20 passing testcase(s)')
```

---

## 4. Verification & Clean-Room Audit
* `feature/ATT-8203` remains intact (deferred post-Stage-6 `--merge-ticket` per `Rule 3.3` / `REQ-PRO-125`).
* Zero `app/src/main/` or `values*/strings*.xml` edits (`Localization Parity: N/A - no UI strings`); unrelated PO dirty files in `tools/` untouched.
