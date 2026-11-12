# Stage 3: Implementation Plan - ATT-6135: Backfill test evidence for Store Presence (project_governance shard) requirements

**Ticket**: [ATT-6135](https://rainerblind.atlassian.net/browse/ATT-6135)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-084`  
**Test Mapping**: `TST-PRO-084`  
**Branch**: `subagent-Lane-2-Agent---ATT-6135-self-487a5904`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Remediation ID: A-08.9 under Epic ATT-6119 (ASPICE SWE.6 BP4, SWE.1 BP5). The `store_presence` living documentation shards (`docs/requirements/req_store_presence.md` and `docs/tests/tst_store_presence.md`, formerly `req_project_governance.md` / `tst_project_governance.md` prior to ATT-6140) require test evidence backfill, test code tag grounding in `PlayStoreListingContractTest.kt` (`TST-STP-002`, `TST-STP-004`), attribute population (`REQ-PRO-077`), and registration of `REQ-PRO-084` / `TST-PRO-084` under `process_stability`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-084` (*Store Presence Test Evidence Backfill & Bidirectional Traceability Governance*)
* **Test Mapping**: `TST-PRO-084` (*Store Presence Test Evidence Backfill & Traceability Verification Suite*)
* **Domain REQs**: `REQ-STP-001`..`010` (10 total, 100% [Verified])
* **Domain TSTs**: `TST-STP-001`..`010` (10 total, 100% [Verified])
* **Target Tests**: `PlayStoreListingContractTest.kt`, `ReadmePlayStoreBadgeContractTest.kt`, `PromoVideoAssetsContractTest.kt`, `ReleaseCiAndLintConfigContractTest.kt`

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All build, release, store presence, and lint contract tests maintain 100% pass rate.
2. **Master Index Mathematical Parity**: 100% parity across all 9 domain shards with 4-column summary format (`Total | Verified | Implemented | Planned / Open`).
3. **Traceability Standards**: 100% of `store_presence` REQs cite valid TSTs (10/10), 100% of TSTs have test code tags (10/10), and Category 4 bidirectional gaps for STP are 0.
4. **Zero Production Runtime Risk**: Changes are strictly confined to test code tags, living documentation, and governance scripts.

---

## 4. Proposed Architectural Changes

### Component 1: Test Code Tags (`PlayStoreListingContractTest.kt`)
* Tag `TST-STP-002` on `fullDescriptionsContainMandatoryV5Features()` and `storeListingsDoNotContainUnimplementedBacklogFeatures()`.
* Tag `TST-STP-004` on `storeScreenshotsAreUniqueAndValid()`.
* Update class header docstring to include `REQ-STP-004`, `TST-STP-002`, and `TST-STP-004`.

### Component 2: Living Requirement Shard (`docs/requirements/req_store_presence.md`)
* Populate `Priority`, `Type`, and `Verification Method` for all 10 requirements (`REQ-STP-001`..`010`) per `REQ-PRO-077`.

### Component 3: Living Test Specification Shard (`docs/tests/tst_store_presence.md`)
* Populate `Priority`, `Type`, and `Verification Method` for all 10 test specifications (`TST-STP-001`..`010`).

### Component 4: Process Stability Shards (`req_process_stability.md`, `tst_process_stability.md`)
* Register `REQ-PRO-084` and `TST-PRO-084` with complete ASPICE SWE.1/SWE.6 metadata.

### Component 5: Master Index Tables (`docs/requirements.md`, `docs/tests.md`)
* Recalculate and synchronize master tables via `verify_requirement_governance.py --sync-indexes`.

### UI Consistency
* No UI changes.

---

## 5. Step-by-Step Implementation Plan

### Step 1: Update `PlayStoreListingContractTest.kt`
- Add `(TST-STP-002)` and `(TST-STP-004)` annotations/tags and docstring citations.

### Step 2: Update `docs/requirements/req_store_presence.md`
- Add `Priority`, `Type`, and `Verification Method` attributes to `REQ-STP-001` through `REQ-STP-010`.

### Step 3: Update `docs/tests/tst_store_presence.md`
- Add `Priority`, `Type`, and `Verification Method` attributes to `TST-STP-001` through `TST-STP-010`.

### Step 4: Register `REQ-PRO-084` and `TST-PRO-084`
- Add `REQ-PRO-084` to `docs/requirements/req_process_stability.md`.
- Add `TST-PRO-084` to `docs/tests/tst_process_stability.md`.

### Step 5: Synchronize Master Index Tables
- Run `python3 tools/verify_requirement_governance.py --sync-indexes`.
- Validate 100% parity across `docs/requirements.md` and `docs/tests.md`.

### Step 6: Verification & Governance Audit
- Run `python3 tools/verify_requirement_governance.py --coverage`.
- Run `python3 tools/verify_requirement_governance.py --check-attributes --check-links --check-evidence --check-unique-ids`.
- Run build unit tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`.
