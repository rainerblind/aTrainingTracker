# Stage 2: Requirement & Test Specification - ATT-6134: Backfill test evidence for Data Migration and Export requirements

**Ticket**: [ATT-6134](https://rainerblind.atlassian.net/browse/ATT-6134)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-083` (*Data Migration and Export Test Evidence Backfill & Bidirectional Traceability Governance*), `REQ-DAT-010`  
**Test Spec ID**: `TST-PRO-083`, `TST-DAT-004`  
**Branch**: `subagent-Lane-3-Agent---ATT-6134-self-ec87964e`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-083)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.8 (ATT-6134), all requirements in `docs/requirements/req_data_migration_export.md` must link to valid, existing test specifications in `docs/tests/tst_data_migration_export.md` with verified automated test code implementations in `app/src/test/` or be downgraded to `[Implemented]`. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-083.1`)*:
   • Every requirement in `docs/requirements/req_data_migration_export.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field matching headings across living doc shards.
   • `REQ-DAT-010` (*Bulk Workout Deletion & Relational Storage Purge*) SHALL cite `TST-DAT-004`, backed by automated unit tests in `WorkoutDeletionHelperTest.kt`. All 130 requirements in `req_data_migration_export.md` SHALL maintain `[Verified]` status backed by automated unit tests in `app/src/test/`.
2. *Bidirectional Citation Integrity & Category 4 Gap Elimination (`REQ-PRO-083.2`)*:
   • Every test specification in `tst_data_migration_export.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field.
   • `REQ-DAT-007` SHALL cite `TST-DAT-001`, `TST-DAT-002`, `TST-DAT-009`.
   • `REQ-DAT-009` SHALL cite `TST-DAT-003`, `TST-DAT-028`.
   • `REQ-DAT-010` SHALL cite `TST-DAT-004`.
   • `TST-DAT-028` SHALL prune extraneous non-domain citation `REQ-MAP-020`.
   • `REQ-MIG-016` SHALL cite `TST-MIG-012`, `TST-MIG-018`.
   • `REQ-MIG-017` SHALL cite `TST-MIG-013`, `TST-MIG-017`.
   • `REQ-MIG-004` SHALL cite `TST-MIG-001`, `TST-MIG-037`.
   • `REQ-MIG-036` SHALL cite `TST-MIG-033`, `TST-MIG-036`, `TST-DAT-031`, `TST-MIG-045`, `TST-MIG-037`.
   • `REQ-MIG-039` SHALL cite `TST-MIG-049`.
3. *Test Code Tag Synchronization (`REQ-PRO-083.3`)*:
   • `WorkoutDeletionHelperTest.kt` SHALL be annotated with `(REQ-DAT-010, TST-DAT-004 / ATT-296)` to eliminate Category 2 untagged test code gaps.
4. *Attribute Compliance & Header Parity (`REQ-PRO-083.4`)*:
   • All 130 requirements in `req_data_migration_export.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
   • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 130`, `Total Tests: 117`) in 100% mathematical parity with master index tables.
5. *Tooling Allowlist Pruning (`REQ-PRO-083.5`)*:
   • `REQ-DAT-010` SHALL be removed from `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-DAT-010` (*Bulk Workout Deletion & Relational Storage Purge*). Targets `WorkoutDeletionHelper.java`, `WorkoutRepository.kt`, `WorkoutDeletionHelperTest.kt`.
* **Historical Origin & Commit Trace**: Implemented in Sprint 2026-38.2 (`ATT-296`), tested in `WorkoutDeletionHelperTest.kt`.
* **Root Reason for Existing Formulation**: Sharding in `ATT-4594` left `Tests: Planned` as an ungrounded placeholder.
* **Preservation of Core Invariants**: 100% unit test pass rate, pure Python 3 stdlib verifier execution, zero production Android APK modifications, and 100% master index mathematical parity remain strictly preserved.

### 1.5 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Traceability & Coverage)**:
  * *Given* `docs/requirements/req_data_migration_export.md`,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  * *Then* 100% of requirements cite valid TSTs (0 ungrounded claims), and 100% of domain TSTs are cited back by all referenced REQs (0 Category 4 gaps in domain).
* **Criterion 2 (Link Integrity & Governance)**:
  * *Given* living documentation shards,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --check-links --check-evidence --check-attributes`,
  * *Then* zero dangling references in data_migration_export, zero ungrounded Verified statuses, and zero missing attributes are detected.
* **Criterion 3 (Master Index Parity)**:
  * *Given* `tools/verify_requirement_governance.py --sync-indexes`,
  * *When* executed,
  * *Then* master index tables achieve 100% mathematical parity.

---

## 2. Test Specification (TST-PRO-083)

### Test Case 1: Coverage & Bidirectional Traceability Audit (`TST-PRO-083.1`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: `req_data_migration_export.md`, `tst_data_migration_export.md`, `WorkoutDeletionHelperTest.kt` updated.
* **Action**: `python3 tools/verify_requirement_governance.py --coverage`
* **Expected Result**: Category 1: 100% (130/130 REQs with TST). Category 4: 0 domain bidirectional citation gaps.
* **Pass Criteria**: `REQ-DAT-010` grounded, 0 domain gaps in Category 4.

### Test Case 2: Link Integrity, Evidence Status & Attribute Audit (`TST-PRO-083.2`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: Shards updated with attributes and bidirectional links.
* **Action**:
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
* **Expected Result**: Zero dangling references in data_migration_export, zero ungrounded Verified statuses, zero missing attributes.

### Test Case 3: Unit Test Execution (`TST-PRO-083.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelperTest"` and `python3 -m unittest tools/test_verify_requirement_governance.py`
* **Expected Result**: 100% test pass rate with zero failures.

---

## 3. Traceability Matrix

| Requirement | Description | Test Specification | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-083.1` | Complete TST Coverage & Evidence Verification | `TST-PRO-083.1` | Planned |
| `REQ-PRO-083.2` | Bidirectional Citation Integrity & Cat 4 Gap Elimination | `TST-PRO-083.1`, `TST-PRO-083.2` | Planned |
| `REQ-PRO-083.3` | Test Code Tag Synchronization | `TST-PRO-083.1`, `TST-PRO-083.3` | Planned |
| `REQ-PRO-083.4` | Attribute Compliance & Header Parity | `TST-PRO-083.2` | Planned |
| `REQ-PRO-083.5` | Tooling Allowlist Pruning | `TST-PRO-083.2` | Planned |
| `REQ-DAT-010` | Bulk Workout Deletion & Relational Storage Purge | `TST-DAT-004`, `TST-PRO-083.3` | Planned |
