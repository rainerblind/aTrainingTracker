# Stage 1 Analysis: ATT-6127 - Backfill test evidence for Tracking Cockpit requirements

**Ticket**: [ATT-6127](https://rainerblind.atlassian.net/browse/ATT-6127)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `feature/ATT-6127`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the ASPICE 4.0 pre-assessment baseline (sprint/2026-45.11, Remediation A-08.1, SWE.6 BP4, SWE.1 BP5), the Tracking Cockpit domain (`req_tracking_cockpit.md`) was found to have traceability gaps:
1. Multiple requirements cite non-existent/dangling test IDs (e.g., `REQ-FIL-007`..`REQ-FIL-010` citing dangling `TST-FUSION-001`..`TST-FUSION-003`).
2. Test specification `TST-TRK-027` cites non-existent requirement `REQ-TRK-035`.
3. 24 requirements cite generic external or manual test IDs (such as `TST-MAN-001` or `TST-STR-001`) instead of concrete domain specifications.
4. 11 local test specifications are not cited back by their referenced requirements, violating bidirectional traceability (Category 4 coverage gap).
5. Requirements claiming `[Verified]` without automated test code in `app/src/test/` must be grounded by linking existing tests or downgraded to `[Implemented]` per REQ-PRO-073 (ATT-6126).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Causes**:
  1. *Omission during Living Doc Sharding*: When monolithic documentation was sharded (`ATT-4594`), requirement `REQ-TRK-035` (*Deterministic BANAL State Snapshotting*, authored in `ATT-4099` commit `aa0e9097`) was inadvertently dropped from the markdown headings, leaving `TST-TRK-027` dangling in `LEGACY_DANGLING_REQ_REFS`.
  2. *Unsharded / Dangling Planned Specs*: `REQ-FIL-007` through `REQ-FIL-010` (*EKF Sensor Fusion*, *Body-to-World Transformation*, *Dead Reckoning*, *Auto-Calibration*) were marked `[Planned]`, citing `TST-FUSION-001`..`003`, but corresponding test specification headings were never created in `docs/tests/tst_tracking_cockpit.md`.
  3. *Unidirectional References*: Several TSTs (`TST-TRK-025`, `TST-TRK-026`, `TST-FIL-014`, `TST-FIL-015`, `TST-CON-021`) cite auxiliary requirements in their `Requirements:` header, but the corresponding requirements only cite their primary test spec.
  4. *Ungrounded Status Claims*: Pre-sprint documentation defaulted requirements to `[Verified]`, even when only manual testing or partial unit testing was available. Under REQ-PRO-073, requirements without automated test evidence must be downgraded to `[Implemented]`.
* **Independent Peer Review & Verification**:
  - Live execution of `calculate_traceability_coverage()` confirms Tracking Cockpit domain currently has:
    * Category 1 (REQs with TST citation): 130/130 (100%).
    * Category 2 (TSTs with test code): 96/104 (92.31%).
    * Category 4 (TSTs cited back by REQs): 92/104 (88.46%, 12 gaps).
  - Verifier check confirms `TST-FUSION-001`..`003` and `REQ-TRK-035` are currently tolerated as legacy dangling references.
  - Test suite scan confirms extensive unit tests exist in `app/src/test/.../banalservice` and `.../tracker` that can be mapped to domain test specifications.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Restore missing `REQ-TRK-035` in `docs/requirements/req_tracking_cockpit.md` and resolve the dangling reference in `TST-TRK-027`.
  2. Create `[Planned]` test specifications for `TST-FUSION-001`, `TST-FUSION-002`, `TST-FUSION-003` in `docs/tests/tst_tracking_cockpit.md` to resolve dangling references and enable removal from `LEGACY_DANGLING_TST_REFS`.
  3. Ensure $\ge 95\%$ of requirements in `req_tracking_cockpit.md` cite valid, local backticked `TST-*` IDs.
  4. Ground bidirectional traceability (Category 4 target $\ge 95\%$) so cited TSTs reference their REQs back.
  5. Downgrade any requirements lacking automated test evidence to `[Implemented]` per REQ-PRO-073.
  6. Add test code tags (e.g. `TST-FIL-001` in `FilterNullSafetyTest.kt`) where existing tests directly verify requirements.
  7. Register `REQ-PRO-078` and `TST-PRO-078` in `req_process_stability.md` and `tst_process_stability.md`.
  8. Synchronize master index tables with 100% mathematical parity.
* **Out-of-Scope Non-Goals**:
  1. Backfilling other domain shards (e.g. aftermath, ui, maps, settings), which are handled in tickets ATT-6128..ATT-6135.
  2. Writing net-new production logic or altering existing sensor/tracking behavior.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-TRK-035` (*Deterministic BANAL State Snapshotting and Bound Lifecycle Preservation During Asynchronous Workout Finalization*). Targets `TrackerService.java`, `TrackerServiceFinalizationTest.kt`.
* **Historical Origin & Commit Trace**: Commit `aa0e9097` (`ATT-4099`), authored on 2026-10-18.
* **Root Reason for Existing Formulation**: Snapshotting BANAL state on the main thread prior to dispatching `endWorkout()` to `mDbExecutor` prevents loss of the final workout lap and sensor accumulation data when `BANALService` unbinds.
* **Preservation of Core Invariants**: Restoring `REQ-TRK-035` restores documented truth matching actual production code in `TrackerService.java` and existing unit tests in `TrackerServiceFinalizationTest.kt`.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**:
  - `docs/requirements/req_tracking_cockpit.md`: Restore `REQ-TRK-035`; update `Tests:` fields and status (`[Verified]` vs `[Implemented]`).
  - `docs/tests/tst_tracking_cockpit.md`: Add `TST-FUSION-001`..`003`; harmonize `Requirement(s):` fields.
  - `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md`: Register `REQ-PRO-078` and `TST-PRO-078`.
  - `tools/verify_requirement_governance.py`: Remove resolved items from legacy allowlists.
* **Affected Architecture Elements**: Living documentation markdown shards, `verify_requirement_governance.py`.
* **Verification Impact & Test Surface**:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/test_verify_requirement_governance.py`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`
* **Risk Assessment & Invariants**: Low risk. Zero production code logic changes. 100% master table mathematical parity preserved.

---

## 6. Architectural Strategy & High-Level Solution

1. **Restore `REQ-TRK-035`**: Reinsert the complete specification from commit `aa0e9097` into `req_tracking_cockpit.md` between `REQ-TRK-034` and `REQ-TRK-036`.
2. **Define Planned Fusion Specs**: Add `TST-FUSION-001`..`TST-FUSION-003` to `tst_tracking_cockpit.md` as `[Planned]`.
3. **Harmonize Tracking Cockpit Traceability**:
   - Link requirements citing generic `TST-MAN-001` / `TST-STR-001` to appropriate concrete sensor and cockpit test specifications where available, or downgrade to `[Implemented]`.
   - Update `Tests:` fields to ensure bidirectional back-links for `TST-CON-021`, `TST-FIL-014`, `TST-FIL-015`, `TST-TRK-020`, `TST-TRK-025`, `TST-TRK-026`, `TST-TRK-027`, `TST-TRK-028`, `TST-TRK-029`, `TST-TRK-031`.
4. **Tag Automated Test Evidence**: Add `TST-FIL-001` tag to `FilterNullSafetyTest.kt`.
5. **Process Stability Registration**: Add `REQ-PRO-078` and `TST-PRO-078` to `req_process_stability.md` and `tst_process_stability.md`.
6. **Master Table Synchronization**: Run `--sync-indexes` and verify with 100% parity.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero production runtime behavior alteration.
  2. 100% master table mathematical parity (`docs/requirements.md`, `docs/tests.md`).
  3. Clean-room unit test suite remains 100% passing.
* **Risk Rating**: **LOW** (Documentation and traceability backfill with full automated verification).
