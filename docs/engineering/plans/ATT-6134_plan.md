# Stage 3: Implementation Plan - ATT-6134: Backfill test evidence for Data Migration and Export requirements

**Ticket**: [ATT-6134](https://rainerblind.atlassian.net/browse/ATT-6134)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-083`, `REQ-DAT-010`  
**Test Mapping**: `TST-PRO-083`, `TST-DAT-004`  
**Branch**: `subagent-Lane-3-Agent---ATT-6134-self-ec87964e`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Remediation ID: A-08.8 under Epic ATT-6119 (ASPICE SWE.6 BP4, SWE.1 BP5). The Data Migration and Export domain (`docs/requirements/req_data_migration_export.md` and `docs/tests/tst_data_migration_export.md`) requires test evidence backfill, grounding unverified claim `REQ-DAT-010` with existing test `TST-DAT-004` and `WorkoutDeletionHelperTest.kt`, eliminating 8 bidirectional Category 4 citation gaps, backfilling mandatory metadata attributes (`Priority`, `Type`, `Verification Method` per `REQ-PRO-077`) across all 130 requirements, updating shard header counts, and pruning `REQ-DAT-010` from verifier allowlists.

---

## 2. Traceability & Requirements Mapping

* **Process Requirement**: `REQ-PRO-083` (*Data Migration and Export Test Evidence Backfill & Bidirectional Traceability Governance*)
* **Process Test Spec**: `TST-PRO-083` (*Data Migration and Export Test Evidence Backfill & Bidirectional Traceability Verification Suite*)
* **Grounded Requirement**: `REQ-DAT-010` (*Bulk Workout Deletion & Relational Storage Purge*)
* **Grounded Test Spec**: `TST-DAT-004` (*Bulk Workout Deletion & Relational Storage Purge Verification*)
* **Domain REQs**: `REQ-DAT-001` through `REQ-DAT-042`, `REQ-EXP-001`..`022`, `REQ-EXT-001`..`012`, `REQ-MIG-001`..`048` (Total: 130)
* **Domain TSTs**: `TST-DAT-001`..`040`, `TST-EXP-004`..`042`, `TST-EXT-001`..`012`, `TST-MIG-001`..`049`, `TST-RAD-007` (Total: 117)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All unit tests in `app/src/test/` continue to pass cleanly.
2. **Master Index Mathematical Parity**: Master table parity across all 9 domain shards in `docs/requirements.md` and `docs/tests.md` remains 100% synchronized in 4-column format.
3. **Traceability Standards**: 100% of `data_migration_export` REQs cite valid TSTs, zero dangling references in domain, zero ungrounded Verified statuses, and 100% bidirectional citation for mapped TSTs.
4. **Zero Production Runtime Risk**: Changes are strictly confined to living documentation, test tags, verifier allowlist pruning, and process governance records.

---

## 4. Proposed Architectural Changes

### Component 1: Test Code Annotation (`WorkoutDeletionHelperTest.kt`)
* Annotate class header with `(REQ-DAT-010, TST-DAT-004 / ATT-296)` so `verify_requirement_governance.py --coverage` resolves `TST-DAT-004` to active test code.

### Component 2: Living Requirement Shard (`docs/requirements/req_data_migration_export.md`)
* Correct shard header to `Total Requirements: 130`.
* Ground `REQ-DAT-010` by setting `- **Tests**: TST-DAT-004`.
* Harmonize bidirectional test citations:
  - `REQ-DAT-007`: `TST-DAT-001`, `TST-DAT-002`, `TST-DAT-009`.
  - `REQ-DAT-009`: `TST-DAT-003`, `TST-DAT-028`.
  - `REQ-MIG-016`: `TST-MIG-012`, `TST-MIG-018`.
  - `REQ-MIG-017`: `TST-MIG-013`, `TST-MIG-017`.
  - `REQ-MIG-004`: `TST-MIG-001`, `TST-MIG-037`.
  - `REQ-MIG-036`: `TST-MIG-033`, `TST-MIG-036`, `TST-DAT-031`, `TST-MIG-045`, `TST-MIG-037`.
  - `REQ-MIG-039`: `TST-MIG-049`.
* Backfill `Priority`, `Type`, and `Verification Method` on all 130 requirements per `REQ-PRO-077`.

### Component 3: Living Test Specification Shard (`docs/tests/tst_data_migration_export.md`)
* Correct shard header to `Total Tests: 117`.
* Harmonize `TST-DAT-028` requirements field by pruning extraneous non-domain `REQ-MAP-020`, keeping `REQ-DAT-033`, `REQ-DAT-009`.

### Component 4: Verifier Tooling (`tools/verify_requirement_governance.py`)
* Remove `REQ-DAT-010` from `LEGACY_UNGROUNDED_VERIFIED_REQS`.

### Component 5: Process Stability Shards (`req_process_stability.md`, `tst_process_stability.md`)
* Register `REQ-PRO-083` and `TST-PRO-083` with complete ASPICE SWE.1/SWE.6 metadata.

### Component 6: Master Index Tables (`docs/requirements.md`, `docs/tests.md`)
* Recalculate and synchronize master tables via `verify_requirement_governance.py --sync-indexes`.

### UI Consistency
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Annotate WorkoutDeletionHelperTest.kt
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelperTest.kt`
* Changes: Add test tag `TST-DAT-004` and requirement reference `REQ-DAT-010` in class documentation.
* Verification: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelperTest"`

### Step 2: Update req_data_migration_export.md
* Files: `docs/requirements/req_data_migration_export.md`
* Changes: Header total to 130. Link `REQ-DAT-010` to `TST-DAT-004`. Harmonize test citations for `REQ-DAT-007`, `REQ-DAT-009`, `REQ-MIG-016`, `REQ-MIG-017`, `REQ-MIG-004`, `REQ-MIG-036`, `REQ-MIG-039`. Backfill attributes for all 130 REQs.
* Verification: `python3 tools/verify_requirement_governance.py --check-attributes`

### Step 3: Update tst_data_migration_export.md
* Files: `docs/tests/tst_data_migration_export.md`
* Changes: Header total to 117. Clean up `TST-DAT-028` requirements field.
* Verification: `python3 tools/verify_requirement_governance.py --check-links`

### Step 4: Prune Verifier Legacy Allowlist
* Files: `tools/verify_requirement_governance.py`
* Changes: Remove `REQ-DAT-010` from `LEGACY_UNGROUNDED_VERIFIED_REQS`.
* Verification: `python3 -m unittest tools/test_verify_requirement_governance.py`

### Step 5: Register REQ-PRO-083 and TST-PRO-083
* Files: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`
* Changes: Append `REQ-PRO-083` and `TST-PRO-083`.
* Verification: `python3 tools/verify_requirement_governance.py --check-links`

### Step 6: Synchronize Master Indexes & Recalculate
* Files: `docs/requirements.md`, `docs/tests.md`
* Changes: Run `python3 tools/verify_requirement_governance.py --sync-indexes`.
* Verification: `python3 tools/verify_requirement_governance.py --coverage`
