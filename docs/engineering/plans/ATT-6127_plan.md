# Stage 3: Implementation Plan - ATT-6127: Backfill test evidence for Tracking Cockpit requirements

**Ticket**: [ATT-6127](https://rainerblind.atlassian.net/browse/ATT-6127)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-078`, `REQ-TRK-035`  
**Test Mapping**: `TST-PRO-078`, `TST-TRK-027`  
**Branch**: `feature/ATT-6127`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Living documentation shard `docs/requirements/req_tracking_cockpit.md` previously suffered from dangling references (`TST-FUSION-001`..`003`, `REQ-TRK-035`), ungrounded `[Verified]` claims citing generic manual tests (`TST-MAN-001`), and broken bidirectional links across 11 test specifications. Under ASPICE SWE.1 BP5 and SWE.6 BP4, all requirements must be linked to valid test specifications, achieve $\ge 95\%$ bidirectional coverage, and downgrade ungrounded status to `[Implemented]` per REQ-PRO-073.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-078` (*Tracking Cockpit Test Evidence Integrity & Bidirectional Traceability Governance*), `REQ-TRK-035` (*Deterministic BANAL State Snapshotting*)
* **Test Mapping**: `TST-PRO-078` (*Tracking Cockpit Verification & Bidirectional Traceability Verification Suite*), `TST-TRK-027` (*Deterministic BANAL State Snapshotting and Bound Lifecycle Preservation Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Mathematical Parity**: 100% parity across all 4 columns of living doc master tables.
3. **Traceability Standards**: Tests fields contain strictly valid backticked IDs or allowed placeholders per REQ-PRO-075.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Tracking Cockpit Requirements & Tests Shards
* Restore `REQ-TRK-035` in `req_tracking_cockpit.md` between `REQ-TRK-034` and `REQ-TRK-036`.
* Register `TST-FUSION-001`, `TST-FUSION-002`, `TST-FUSION-003` in `tst_tracking_cockpit.md` as `[Planned]`.
* Harmonize `Tests:` fields and bidirectional back-links.
* Downgrade requirements citing manual tests without automated evidence to `[Implemented]` per REQ-PRO-073.

### Component 2: Test Evidence Tagging
* Tag `app/src/test/java/com/atrainingtracker/banalservice/filters/FilterNullSafetyTest.kt` with `// TST-FIL-001` for direct evidence linkage.

### Component 3: Governance Verifier Allowlist Cleanup
* Remove resolved references (`TST-FUSION-001`, `TST-FUSION-002`, `TST-FUSION-003`, `REQ-TRK-035`) from `LEGACY_DANGLING_TST_REFS` and `LEGACY_DANGLING_REQ_REFS` in `tools/verify_requirement_governance.py`.

### Component 4: Process Stability Shards
* Register `REQ-PRO-078` in `docs/requirements/req_process_stability.md` and `TST-PRO-078` in `docs/tests/tst_process_stability.md`.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Shard Updates (Tracking Cockpit)
* Files: `docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`
* Changes: Restore `REQ-TRK-035`, add `TST-FUSION-001`..`003`, harmonize `Tests:` fields and bidirectional links, downgrade ungrounded requirements to `[Implemented]`.

### Step 2: Test Code Tagging
* Files: `app/src/test/java/com/atrainingtracker/banalservice/filters/FilterNullSafetyTest.kt`
* Changes: Add `// TST-FIL-001` test code citation.

### Step 3: Governance Tooling Update
* Files: `tools/verify_requirement_governance.py`
* Changes: Remove resolved tracking cockpit items from `LEGACY_DANGLING_TST_REFS` and `LEGACY_DANGLING_REQ_REFS`.

### Step 4: Process Stability Shard Updates
* Files: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`
* Changes: Register `REQ-PRO-078` and `TST-PRO-078`.

### Step 5: Master Table Synchronization & Tier 1 Verification
* Commands:
  - `python3 tools/verify_requirement_governance.py --sync-indexes`
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/test_verify_requirement_governance.py`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute `verify_requirement_governance.py --coverage` ensuring Category 1 $\ge 95\%$, Category 4 $\ge 95\%$, 0 dangling refs; execute `AspiceSweAuditContractTest.kt`.
* **Rollback**: `git checkout .` to revert working directory changes.
