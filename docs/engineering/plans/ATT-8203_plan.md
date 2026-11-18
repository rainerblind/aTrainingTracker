# Stage 3: Implementation Plan - ATT-8203: Reconcile ADR-001, Bound God Classes & Enforce Requirements Layout

**Ticket**: [ATT-8203](https://issue-tracker/ATT-8203) (`2 SP`)  
**Sub-task**: Pending `submit-stage` creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-11-18_01_Früh`  
**Requirement Mapping**: `REQ-PRO-129` (`.1`–`.4`, refining `REQ-ARC-005`, `REQ-PRO-050`, `REQ-PRO-114`, `REQ-PRO-093`)  
**Test Mapping**: `TST-PRO-129` (`.1`–`.4`)  
**Branch**: `feature/ATT-8203`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-18

---

## 1. Problem Description & Background
Resolve ASPICE 4.0 Gap #6 (`SWE.2 BP5`, `SWE.3 BP1/BP3`, `SUP.8 BP1`) across four targets:
1. Eliminate residual `Keep`-vs-`Migrate` contradictions and stale pre-migration counts (`149` vs `120` remaining `.java` files, `29` migrated `.kt` files, `5.1.0` -> `V5.0.0`, `ATT-7903` `Planned` -> `Completed`) in `ADR-001`, `docs/architecture.md`, and `JavaCoreMigrationStrategyContractTest.kt`.
2. Halt growth of `16` existing `> 1,500` LoC Kotlin/Java monoliths via a downward-ratcheting contract test in `ArchitectureConformanceContractTest.kt` and add SWE.3 detailed design `docs/architecture/detailed_design/workout_repository.md` (`3` -> `4` detailed-design docs in `docs/architecture.md` §3.5 and `DetailedDesignParityContractTest.kt`).
3. Enforce fail-closed directory hygiene in `tools/verify_requirement_governance.py` rejecting non-`req_*.md` in `docs/requirements/` (e.g. legacy `SCRUM-*_plan.md`) and non-`tst_*.md` in `docs/tests/`.
4. Preserve Stage 2 split `docs/tests/tst_process_stability_part{1,2}.md` (`<= 4,000` lines) and verify zero UI/production code changes (`Localization Parity: N/A - no UI strings`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-129` (`REQ-PRO-129.1`–`REQ-PRO-129.4`, refining `REQ-ARC-005`, `REQ-PRO-050`, `REQ-PRO-114`, `REQ-PRO-093`)
* **Test Mapping**: `TST-PRO-129` (`TST-PRO-129.1`–`TST-PRO-129.4`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production & SDK Edits**: `0` changes in `app/src/main/`, `ANT-Android-SDKs`, or `values*/strings*.xml` (`Localization Parity: N/A - no UI strings`).
2. **Governance Guard & Baseline Lock**: `SourceTextReadingAuditContractTest.LEGITIMATE_GOVERNANCE_GUARDS == 22` (`REQ-PRO-049.2`) and `app/source-reading-baseline.txt == 105` remain unchanged; LoC ratchet lives inside already-allowlisted `ArchitectureConformanceContractTest.kt`.
3. **ADR-001 & Header Invariants**: Preserve `Status: Reviewed` (`Pending PO Sign-Off`), monotonic header version bumps + `Change History` rows (`REQ-PRO-120`), `<= 149` Java class bound, `3` child tickets (`ATT-7903`/`7905`/`7906`), and authentic existing `.kt`/`.java` filenames (`verify_architecture_and_system_specs` grounding).
4. **Shard Size & Human Gate**: All living shards `<= 4,000` lines; parent ticket ends at `Final Review (Human)` after Stage 6 `PASS`.

---

## 4. Proposed Architectural Changes
### Component 1: ADR-001 & SWE.2 Strategy Reconciliation (`REQ-PRO-129.1`, `TST-PRO-129.1`)
* Update `docs/architecture/adr_001_java_core_services_migration.md` (`v1.1.0` -> `v1.2.0`): reconcile §1 (`120` remaining `.java` + `29` migrated `.kt`), §2 Alt 2/3 wording, §4 table (mark the `29` migrated classes with their actual `.kt` paths & `Migrated` status; keep `120` `.java` classes), §5 Mermaid (`[Frozen / Keep]` on Tiers 3/5/6), §7 (`ATT-7903` `Completed`), §8 consequences, and `Target Release: V5.0.0`.
* Update `docs/architecture.md` (`v1.0.1` -> `v1.0.2`): §5.3 (`120` remaining `.java` files, `29` migrated `.kt` across `ATT-7903`/`7905`/`7906`) and §5.5 (`ADR-001` status `Reviewed (Pending PO Sign-Off)`).
* Extend `JavaCoreMigrationStrategyContractTest.kt` (`// Traces to: TST-PRO-129.1`) with assertions verifying `120 <= 149` `.java` alignment, `Keep` consistency across §3.1/§3.2/§5/§7.1 for `TrackerService`/`BANALService`/`TrainingApplication`/BLE/ANT+, `ATT-7903`/`7905`/`7906` `Completed`, and `V5.0.0`.

### Component 2: God-Class LoC Ratchet & `WorkoutRepository` SWE.3 Design (`REQ-PRO-129.2`, `TST-PRO-129.2`)
* Add `MAX_MONOLITH_FILE_LOC_BASELINE` (`16` entries `> 1,500` LoC, `WorkoutRepository.kt = 5117` .. `ImportBackupTabsScreen.kt = 1513`) and downward-ratchet test (`// Traces to: TST-PRO-129.2`) inside `ArchitectureConformanceContractTest.kt` (failing on upward growth, net-new `> 1,500` LoC files, or stale reduced entries).
* Author `docs/architecture/detailed_design/workout_repository.md` (`DOC-ARC-DD-WKO`, `v1.0.0`, `Status: Reviewed`, `Pending PO Sign-Off`, `Change History`) covering `WorkoutRepository` singleton/scope (`INSTANCE`, `repoScope`, `cropMutex`, `_allWorkouts`, `activeReloadWorkoutIds`, `dirtyReloadWorkoutIds`, `isListLoading`, `deletedDuringLoadIds`), Mermaid `stateDiagram-v2` (load/reload/crop/delete states), `IF-SWE-02`/`IF-SWE-06`/`IF-SWE-07` threading (`Dispatchers.IO`, `NonCancellable`), and authentic `WorkoutRepository*Test` references; link in `docs/architecture.md` §3.5 and assert parity (`4` detailed-design docs) in `DetailedDesignParityContractTest.kt`.

### Component 3: Fail-Closed Living-Doc Layout Enforcement (`REQ-PRO-129.3`, `REQ-PRO-129.4`, `TST-PRO-129.3`, `TST-PRO-129.4`)
* Add `verify_living_doc_directory_layout(repo_root)` in `tools/verify_requirement_governance.py` (invoked in `verify_all()` and CLI) rejecting any non-`req_*.md` file in `docs/requirements/` and any non-`tst_*.md` file in `docs/tests/`.
* Add offline `tempfile` tests (`TST-PRO-129.3`) in `tools/test_verify_requirement_governance.py` covering valid shards, misplaced `SCRUM-123_plan.md` in `docs/requirements/`, and misplaced files in `docs/tests/`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Code Lock Check)**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` (`Rule 4.1`).
2. **Step 2 (`REQ-PRO-129.1`)**: Update `docs/architecture/adr_001_java_core_services_migration.md` (`v1.2.0`), `docs/architecture.md` (§5.3, §5.5), and `JavaCoreMigrationStrategyContractTest.kt`; verify with `./gradlew :app:testDebugUnitTest --tests com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest`.
3. **Step 3 (`REQ-PRO-129.2`)**: Author `docs/architecture/detailed_design/workout_repository.md`, link in `docs/architecture.md` §3.5 (`v1.0.2`), update `DetailedDesignParityContractTest.kt` (`4` docs) and `ArchitectureConformanceContractTest.kt` (`16`-file LoC ratchet); verify with `./gradlew :app:testDebugUnitTest --tests com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest --tests com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest --tests com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest --tests com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest --tests com.atrainingtracker.trainingtracker.build.NonFunctionalRequirementsContractTest --tests com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest`.
4. **Step 4 (`REQ-PRO-129.3/.4`)**: Add `verify_living_doc_directory_layout()` in `tools/verify_requirement_governance.py` and `TST-PRO-129.3` tests in `tools/test_verify_requirement_governance.py`; verify `python3 -m unittest tools/test_verify_requirement_governance.py`, `python3 tools/shard_living_docs.py --verify-only`, `python3 tools/shard_living_docs.py --check-size --strict`, and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted Gradle contract suite (`6` build/architecture test classes above) + `python3 -m unittest tools/test_verify_requirement_governance.py` + `shard_living_docs.py --verify-only` & `--check-size --strict` + `verify_requirement_governance.py` (`exit 0`).
* **Rollback**: Isolated on `feature/ATT-8203`; revert cleanly before Stage 6 `--merge-ticket` squash.
