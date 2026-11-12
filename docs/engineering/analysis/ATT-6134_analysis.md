# Stage 1 Analysis: ATT-6134 - Backfill test evidence for Data Migration and Export requirements

**Ticket**: [ATT-6134](https://rainerblind.atlassian.net/browse/ATT-6134)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-3-Agent---ATT-6134-self-ec87964e`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 pre-assessment baseline (baseline develop @ sprint/2026-45.11, Remediation ID: A-08.8, ASPICE SWE.6 BP4, SWE.1 BP5), the Data Migration and Export requirement and test specification shards (`docs/requirements/req_data_migration_export.md` and `docs/tests/tst_data_migration_export.md`) were identified as needing test evidence backfill, bidirectional citation integrity reconciliation, and attribute governance:
* **Ungrounded Verified Claim**: `REQ-DAT-010` (*Bulk Workout Deletion & Relational Storage Purge*) claims `[Verified]` while citing `Tests: Planned`, and is currently tolerated in `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.
* **Missing Test Tag in Code (Category 2 Gap)**: `TST-DAT-004` (*Bulk Workout Deletion & Relational Storage Purge Verification*) corresponds to `WorkoutDeletionHelperTest.kt`, but the test file is missing the explicit `(REQ-DAT-010, TST-DAT-004 / ATT-296)` tag.
* **Bidirectional Traceability Gaps (Category 4 Gaps)**: 8 test specifications in `tst_data_migration_export.md` have unreciprocated requirement citations:
  - `TST-DAT-001` & `TST-DAT-002` (cites `REQ-DAT-007`, but `REQ-DAT-007` only cites `TST-DAT-009`).
  - `TST-DAT-004` (cites `REQ-DAT-010`, but `REQ-DAT-010` cites `Planned`).
  - `TST-DAT-028` (cites `REQ-DAT-009` and extraneous cross-shard `REQ-MAP-020`, while `REQ-DAT-009` only cites `TST-DAT-003`).
  - `TST-MIG-012` (cites `REQ-MIG-016`, but `REQ-MIG-016` only cites `TST-MIG-018`).
  - `TST-MIG-013` (cites `REQ-MIG-017`, but `REQ-MIG-017` only cites `TST-MIG-017`).
  - `TST-MIG-037` (cites `REQ-MIG-004` and `REQ-MIG-036`, but neither cites `TST-MIG-037`).
  - `TST-MIG-049` (cites `REQ-MIG-039`, but `REQ-MIG-039` cites `TST-MIG-036` due to historical typo).
* **Missing Mandatory Metadata Attributes**: All 130 requirements in `req_data_migration_export.md` lack `Priority`, `Type`, and `Verification Method` attributes required by `REQ-PRO-077` (ATT-6136).
* **Domain Test Link Completeness**: Align all 16 legacy cross-domain citations with authentic domain test specifications in `tst_data_migration_export.md` to guarantee 100% domain TST coverage.
* **Shard Header Count Drift**: Header counts (`Total Requirements: 119`, `Total Tests: 106`) are outdated relative to actual shard contents (130 REQs, 117 TSTs).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Causes**:
  1. *Omission during Living Doc Sharding (`ATT-4594`)*: In sprint 2026-41.3, when monolithic documentation was partitioned into domain shards, `REQ-DAT-010` retained its draft placeholder `Tests: Planned`, even though `TST-DAT-004` and `WorkoutDeletionHelperTest.kt` had already been implemented in `ATT-296`.
  2. *Asymmetric Citation Authoring*: Several multi-faceted test specifications (`TST-DAT-001`, `TST-DAT-002`, `TST-MIG-012`, `TST-MIG-013`, `TST-MIG-037`, `TST-MIG-049`) linked back to their parent requirements, but the requirements were never updated to cite all of their verifying test specs.
  3. *Cross-Shard Reference Leakage*: `TST-DAT-028` accidentally cited `REQ-MAP-020` in addition to `REQ-DAT-033` and `REQ-DAT-009`, because `REQ-DAT-033`'s rationale discussed decoupling apex writes.
  4. *Attribute Backfill Deferred*: `REQ-PRO-077` (ATT-6136) introduced mandatory requirement attributes, but deferred shard-level population to the A-08 remediation tickets.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Running `python3 tools/verify_requirement_governance.py --coverage` confirms:
    * `REQ-DAT-010` is the single Category 1 ungrounded requirement in the entire repository.
    * Exactly 8 domain test specifications in `tst_data_migration_export.md` constitute Category 4 bidirectional gaps.
    * `WorkoutDeletionHelperTest.kt` directly exercises `WorkoutDeletionHelper.deleteOldWorkouts` and `deleteWorkoutCascade` matching `REQ-DAT-010` and `TST-DAT-004`.
  - Automated tests already exist in `app/src/test/` for all verified data migration and export features (`WorkoutDeletionHelperTest.kt`, `MigrationEngineCredentialSanitizationTest.kt`, `BackupManagerTest.kt`, `ExportManagerTest.kt`, `FitFileWriterTest.kt`, `TCXRoundTripTest.kt`, `CSVFileWriterTest.kt`, `StravaHelperTest.kt`, `StravaUploadDbHelperTest.kt`, `WorkoutSamplesDatabaseManagerTest.kt`, etc.).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Link `REQ-DAT-010` to `TST-DAT-004` in `docs/requirements/req_data_migration_export.md`.
  2. Annotate `WorkoutDeletionHelperTest.kt` with `(REQ-DAT-010, TST-DAT-004 / ATT-296)` tag.
  3. Remove `REQ-DAT-010` from `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.
  4. Resolve all 8 bidirectional Category 4 gaps:
     - Update `REQ-DAT-007` Tests to `TST-DAT-001`, `TST-DAT-002`, `TST-DAT-009`.
     - Update `REQ-DAT-009` Tests to `TST-DAT-003`, `TST-DAT-028`.
     - Prune extraneous `REQ-MAP-020` from `TST-DAT-028` Requirement(s) list.
     - Update `REQ-MIG-016` Tests to `TST-MIG-012`, `TST-MIG-018`.
     - Update `REQ-MIG-017` Tests to `TST-MIG-013`, `TST-MIG-017`.
     - Update `REQ-MIG-004` Tests to `TST-MIG-001`, `TST-MIG-037`.
     - Update `REQ-MIG-036` Tests to `TST-MIG-033`, `TST-MIG-036`, `TST-DAT-031`, `TST-MIG-045`, `TST-MIG-037`.
     - Update `REQ-MIG-039` Tests to `TST-MIG-049`.
  5. Backfill `Priority`, `Type`, and `Verification Method` attributes across all 130 requirements in `req_data_migration_export.md`.
  6. Update shard headers: `req_data_migration_export.md` (Total Requirements: 130) and `tst_data_migration_export.md` (Total Tests: 117).
  7. Register `REQ-PRO-083` and `TST-PRO-083` in `req_process_stability.md` and `tst_process_stability.md`.
  8. Synchronize master index tables (`--sync-indexes`) to 100% mathematical parity.
* **Out-of-Scope Non-Goals**:
  - Modifying non-migration domain shards (`req_ui_design_system.md`).
  - Production code edits in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-DAT-010` (*Bulk Workout Deletion & Relational Storage Purge*). Targets `WorkoutDeletionHelper.java`, `WorkoutRepository.kt`, `WorkoutDeletionHelperTest.kt`.
* **Historical Origin & Commit Trace**: Implemented in Sprint 2026-38.2 (`ATT-296`) and tested in `WorkoutDeletionHelperTest.kt`.
* **Root Reason for Existing Formulation**: When monolithic documentation was sharded in `ATT-4594`, `REQ-DAT-010`'s Tests line was left as `Planned` despite passing test code.
* **Preservation of Core Invariants**: 100% unit test pass rate, pure Python 3 stdlib verifier execution, zero production Android APK modifications, and 100% master index mathematical parity remain strictly preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**:
  - `docs/requirements/req_data_migration_export.md`: Update `REQ-DAT-010` tests link; resolve bidirectional citations; backfill attributes for all 130 REQs.
  - `docs/tests/tst_data_migration_export.md`: Clean up `TST-DAT-028` requirements field; update header total to 117.
  - `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md`: Register `REQ-PRO-083` and `TST-PRO-083`.
  - `tools/verify_requirement_governance.py`: Remove `REQ-DAT-010` from `LEGACY_UNGROUNDED_VERIFIED_REQS`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelperTest.kt`: Add `(REQ-DAT-010, TST-DAT-004 / ATT-296)` tag.
* **Affected Architecture Elements**: Living documentation markdown shards, governance verifier tooling.
* **Verification Impact & Test Surface**:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --enforce-unique --enforce-links`
  - `python3 tools/verify_requirement_governance.py --sync-indexes`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelperTest"`
  - `python3 -m unittest tools/test_verify_requirement_governance.py`
* **Risk Assessment & Invariants**: Low risk. Living doc and test annotation changes only. Zero runtime APK logic modified.

---

## 6. Architectural Strategy & High-Level Solution

* **Step 1**: Update `WorkoutDeletionHelperTest.kt` with explicit test tag `(REQ-DAT-010, TST-DAT-004 / ATT-296)`.
* **Step 2**: Remove `REQ-DAT-010` from `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.
* **Step 3**: Reconcile citations in `docs/requirements/req_data_migration_export.md` and `docs/tests/tst_data_migration_export.md`, eliminating all 8 Category 4 gaps.
* **Step 4**: Populate mandatory attributes (`Priority`, `Type`, `Verification Method`) across all 130 requirements in `req_data_migration_export.md`.
* **Step 5**: Register `REQ-PRO-083` and `TST-PRO-083` in `req_process_stability.md` and `tst_process_stability.md`.
* **Step 6**: Run `--sync-indexes` and verify governance with `--enforce-unique --enforce-links`.
