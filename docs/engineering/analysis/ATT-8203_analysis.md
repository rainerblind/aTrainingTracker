# Stage 1 Analysis: ATT-8203 - Reconcile ADR-001, Bound God Classes & Enforce Requirements Layout

**Ticket**: [ATT-8203](https://issue-tracker/ATT-8203)  
**Sub-task**: Pending `submit-stage` creation (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-18_01_Früh`  
**Branch**: `feature/ATT-8203`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
ASPICE 4.0 Gap #6 (`SWE.2 BP5`, `SWE.3 BP1/BP3`, `SUP.8 BP1`) flagged three architectural/layout governance gaps:
1. `docs/architecture/adr_001_java_core_services_migration.md` still retains contradictory migration language, stale pre-migration counts (`149` vs actual `120` `.java` files), and `ATT-7903` marked `Planned`, while `JavaCoreMigrationStrategyContractTest.kt` does not verify `Keep`-vs-`Migrate` consistency.
2. `16` Kotlin/Java files in `app/src/main/` exceed `1,500` LoC (`WorkoutRepository.kt` at `5,117` LoC, `TrackerService.java` at `3,559` LoC, etc.) with no automated LoC ceiling ratchet, and `WorkoutRepository` lacks an SWE.3 detailed design doc under `docs/architecture/detailed_design/` (`ATT-8159`).
3. Legacy `SCRUM-*_plan.md` plans were historically cited as misplaced in `docs/requirements/`, yet `verify_requirement_governance.py` silently ignores non-`req_*.md` files instead of failing closed.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification** (against live `sprint/2026-11-18_01_Früh` @ `2d89271e`):
  * **ADR-001**: Ticket claims §§3–5 (`L60-287`) mark `TrackerService`, `BANALService`, `TrainingApplication`, and BLE/ANT+ drivers as `Migrate`. **Partially superseded**: commit `195a9f1f` (`ATT-8115`) already flipped §§3–5 tables (`L61-293`) to `Keep`. However, **residual contradictions remain**: (a) §2 Alt 2 (`L46-49`) rejects `Keep` while Alt 3 (`L50-51`) still plans migrating background services; (b) §5 Mermaid (`L256-264`) & §8 (`L338`) still depict Tier 3/5/6 migration to `StateFlow`; (c) `29` classes (`15` formatters `ATT-7903`, `11` filters `ATT-7905`, `FitFileWriter`/`TCXFileWriter`/`BaseFileWriter` `ATT-7906`) already exist as `.kt` (`120` `.java` remaining vs `149` in `L19` & `docs/architecture.md:345`), while `ATT-7903` still reads `Planned` (`L320`) and `Target Release` reads `5.1.0` (`L9`); (d) `JavaCoreMigrationStrategyContractTest.kt` never checks decision consistency (`Keep` vs `Migrate`) across sections.
  * **God Classes & LoC Ratchet**: All cited files grew above ticket estimates (`find app/src/main` `wc -l`): `WorkoutRepository.kt` (`5,117` vs `4,750`), `TrackerService.java` (`3,559` vs `3,405`), `WorkoutSummariesDatabaseManager.java` (`3,148`), `TrackingViewsDatabaseManager.java` (`2,631`), `TuningPreferencesDataStore.kt` (`2,410` vs `2,373`), `TrainingApplication.java` (`2,134`), `EditDeviceDialog.kt` (`2,004`), `AthleteProfileScreen.kt` (`1,975`), `TelemetryMetricGraph.kt` (`1,965`), `SensorGridScreen.kt` (`1,952` vs `1,888`), `ZonesSettingsActivity.kt` (`1,897`), `DeviceManager.java` (`1,708`), `DevicesDatabaseManager.java` (`1,671`), `WorkoutClusterEngine.kt` (`1,635`), `WorkoutSamplesDatabaseManager.java` (`1,533`), `ImportBackupTabsScreen.kt` (`1,513`) — `16` files `> 1,500` LoC. Placing the LoC ratchet inside `ArchitectureConformanceContractTest.kt` (already an approved guard in `SourceTextReadingAuditContractTest.LEGITIMATE_GOVERNANCE_GUARDS`, count `22` per `REQ-PRO-049.2`) avoids violating `REQ-PRO-049` or breaking the `22`-guard assertion.
  * **`SCRUM-*_plan.md` Relocation**: `ls docs/requirements/` and `git log --all -- "docs/requirements/*"` confirm **0** `SCRUM-*_plan.md` or non-`req_*.md` files exist in `docs/requirements/` (all `40` `SCRUM-*_plan.md` files already reside in `docs/engineering/plans/`, and `docs/tests/` contains only `tst_*.md`). Only the enforcement guard in `tools/verify_requirement_governance.py` is missing.
  * **Shard Size Constraint**: `docs/tests/tst_process_stability.md` is **`3,979 / 4,000`** lines (`21` lines headroom). Adding a new `TST-*` spec + Change History rows will exceed `4,000` lines; **Decision**: split via `python3 tools/shard_living_docs.py --split-shard docs/tests/tst_process_stability.md` in Stage 2 (`REQ-PRO-093.3`, `REQ-PRO-127.3`; `NonFunctionalRequirementsContractTest` & `SystemArchitectureDesignContractTest` already use `loadShardContent`, and `req_process_stability_part2.md` is `2,313 / 4,000`).

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals** (`2 SP`, complexity verified `<= 3 SP`):
  * Reconcile residual `ADR-001` contradictions (§§1–8, `120` remaining `.java` + `29` migrated `.kt` status, `V5.0.0`, PO-frozen `Keep` alignment) + `docs/architecture.md` §5.3 + `JavaCoreMigrationStrategyContractTest.kt` consistency assertions.
  * Add downward-ratcheting LoC ceiling contract test (`> 1,500` LoC, `16` files) in `ArchitectureConformanceContractTest.kt` + SWE.3 `docs/architecture/detailed_design/workout_repository.md` (`docs/architecture.md` §3.5 + `DetailedDesignParityContractTest.kt`).
  * Add fail-closed non-`req_*.md` / non-`tst_*.md` directory-hygiene enforcement in `tools/verify_requirement_governance.py` (+ `tools/test_verify_requirement_governance.py`), verifying `0` misplaced `SCRUM-*_plan.md` files.
* **Out-of-Scope Non-Goals**: Refactoring/splitting the `16` `> 1,500` LoC production classes in `app/src/main/`; modifying `LEGITIMATE_GOVERNANCE_GUARDS` (`22`); touching `tools/ble_radar_simulator.py`, `tools/jira_created_vs_resolved.py`, or `ANT-Android-SDKs`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-ARC-005` (`ADR-001` & `JavaCoreMigrationStrategyContractTest.kt`), `REQ-PRO-050` (`ArchitectureConformanceContractTest.kt`), `REQ-PRO-114` (`DetailedDesignParityContractTest.kt` & `detailed_design/`), and `REQ-PRO-093` (`docs/requirements/` & `docs/tests/` layout) via net-new `REQ-PRO-129` / `TST-PRO-129`.
* **Historical Origin & Commit Trace**: `1e95d2c3` (`ATT-7566`), `195a9f1f` (`ATT-8115` PO `Keep` pivot), `2de2a41c` (`ATT-8164` `DOC-ARC-ADR001` header), `ATT-8159` (`REQ-PRO-114` SWE.3 detailed design), `ATT-8206` (`REQ-PRO-127` shard splitting).
* **Root Reason for Existing Formulation**: `REQ-ARC-005` locked `<= 149` Java classes and 3 child tickets (`ATT-7903`/`7905`/`7906`); `REQ-PRO-049.2` locked `22` source-reading guards; `verify_requirement_governance.py` ignored non-`req_*.md` files to tolerate legacy scratch files.
* **Preservation of Core Invariants**: Preserves `<= 149` bound, `3` registered child tickets (`ATT-7903`/`7905`/`7906`), `22` `LEGITIMATE_GOVERNANCE_GUARDS`, `105` `app/source-reading-baseline.txt`, and `<= 4,000`-line shard cap.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-129` (`req_process_stability_part2.md`), refining `REQ-ARC-005`, `REQ-PRO-050`, `REQ-PRO-114`, `REQ-PRO-093`; shard split of `tst_process_stability.md` (`part1`/`part2`) + `docs/tests.md` sync.
* **Affected Architecture Elements**: `docs/architecture/adr_001_java_core_services_migration.md`, `docs/architecture/detailed_design/workout_repository.md`, `docs/architecture.md` (§3.5, §5.3), `tools/verify_requirement_governance.py`.
* **Verification Impact & Test Surface**: `JavaCoreMigrationStrategyContractTest`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, `SourceTextReadingAuditContractTest`, `AspiceSweAuditContractTest`, `tools/test_verify_requirement_governance.py`, `tools/shard_living_docs.py --check-size --strict`.
* **Risk Assessment & Invariants**: Splitting `tst_process_stability.md` affects `232` test specs; `NonFunctionalRequirementsContractTest`, `SystemArchitectureDesignContractTest`, and `shard_living_docs.py --verify-only` already handle `*_partN.md`. Zero `app/src/main/` edits.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `0` `app/src/main/` edits; `Localization Parity: N/A - no UI strings` (`0` string edits); `LEGITIMATE_GOVERNANCE_GUARDS == 22`; `app/source-reading-baseline.txt == 105`; all living shards `<= 4,000` lines; `ADR-001` retains `Status: Reviewed` (`Pending PO Sign-Off`).
* **Risk Rating**: **MEDIUM** — touches architecture contracts (`ADR-001`, `JavaCoreMigrationStrategyContractTest`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`) and splits `tst_process_stability.md` (`3,979` lines) into sub-shards across master indexes.
