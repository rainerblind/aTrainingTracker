# Stage 1 Analysis: ATT-6135 - Backfill test evidence for Store Presence (project_governance shard) requirements

**Ticket**: [ATT-6135](https://rainerblind.atlassian.net/browse/ATT-6135)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-2-Agent---ATT-6135-self-487a5904`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the independent ASPICE 4.0 pre-assessment (baseline develop @ sprint/2026-45.11, Remediation ID: A-08.9, ASPICE SWE.6 BP4, SWE.1 BP5), the `store_presence` living documentation shards (historically referred to as `project_governance` prior to the ATT-6140 shard rename, located at `docs/requirements/req_store_presence.md` and `docs/tests/tst_store_presence.md`) were audited for evidence integrity, bidirectional traceability, and mandatory attribute compliance:
* **Shard Historical Context**: Originally created under `req_project_governance.md`, the shard was corrected to `req_store_presence.md` / `tst_store_presence.md` in `ATT-6140` because it exclusively contains Store Presence (`STP`) requirements (`REQ-STP-001`..`010`).
* **Category 2 Code Tag Gaps**: While all 10 requirements cite valid `TST-STP-*` test specifications, `TST-STP-002` (Feature Messaging & Privacy Audit) and `TST-STP-004` (Screenshot Dimensions & Uniqueness) lack explicit `@Test` method annotations/tags in `PlayStoreListingContractTest.kt`, despite comprehensive assertion code already existing in `fullDescriptionsContainMandatoryV5Features`, `storeListingsDoNotContainUnimplementedBacklogFeatures`, and `storeScreenshotsAreUniqueAndValid`.
* **Mandatory Attribute Gaps (REQ-PRO-077, SWE.1 BP2)**: None of the 10 requirements in `req_store_presence.md` declare mandatory `Priority`, `Type`, and `Verification Method` attributes.
* **Evidence Grounding Verification (REQ-PRO-073)**: All 10 requirements claim `[Verified]`. Verification confirms all 10 are backed by authentic automated contract tests in `com.atrainingtracker.trainingtracker.build.*` (`PlayStoreListingContractTest`, `ReadmePlayStoreBadgeContractTest`, `PromoVideoAssetsContractTest`, `ReleaseCiAndLintConfigContractTest`). Zero downgrades to `[Implemented]` are required.
* **Process Stability Registration**: Registration of `REQ-PRO-084` and `TST-PRO-084` is required under `req_process_stability.md` and `tst_process_stability.md`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**:
  1. Shard evolution (`ATT-4853`, `ATT-2274`, `ATT-2371`, `ATT-6140`, `ATT-6148`, `ATT-6151`, `ATT-6152`, `ATT-6153`) introduced automated build and release contract tests, but tag annotations in `PlayStoreListingContractTest.kt` omitted `TST-STP-002` and `TST-STP-004`.
  2. Legacy living document syntax did not enforce structured attributes (`Priority`, `Type`, `Verification Method`), which were introduced in `REQ-PRO-077` (ATT-6126).
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Audited `PlayStoreListingContractTest.kt`:
    * Line 145: `fun fullDescriptionsContainMandatoryV5Features()` verifies .FIT, ANT+, BLE, Strava, Google Drive, and local SQLite data sovereignty (`TST-STP-002`).
    * Line 182: `fun storeListingsDoNotContainUnimplementedBacklogFeatures()` verifies absence of banned keywords (Radar, Mader, Glykogen) (`TST-STP-002`).
    * Line 200: `fun storeScreenshotsAreUniqueAndValid()` asserts 6 unique screenshot hashes, $1080 \times 2424$ resolution, and disk existence (`TST-STP-004`).
  - Audited `ReadmePlayStoreBadgeContractTest.kt`: Enforces `REQ-STP-005` / `TST-STP-005` with 100% pass.
  - Audited `PromoVideoAssetsContractTest.kt`: Enforces `REQ-STP-006` / `TST-STP-006` with 100% pass.
  - Audited `ReleaseCiAndLintConfigContractTest.kt`: Enforces `REQ-STP-007` (`TST-STP-007`), `REQ-STP-008` (`TST-STP-008`), `REQ-STP-009` (`TST-STP-009`), and `REQ-STP-010` (`TST-STP-010`).
  - Audited `verify_requirement_governance.py`: Confirmed zero dangling references exist in `store_presence` (Category 4 has 0 gaps for STP).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Populate mandatory attributes (`Priority`, `Type`, `Verification Method`) across all 10 requirements in `docs/requirements/req_store_presence.md`.
  2. Add `TST-STP-002` and `TST-STP-004` tags to `PlayStoreListingContractTest.kt`, bringing Category 2 coverage for `store_presence` to 10/10 (100%).
  3. Align `docs/tests/tst_store_presence.md` with priority, type, and verification method attributes.
  4. Register `REQ-PRO-084` and `TST-PRO-084` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
  5. Run `python3 tools/verify_requirement_governance.py --sync-indexes` to ensure 100% mathematical parity.
* **Out-of-Scope Non-Goals**:
  - Modifying non-contract Android runtime logic in `app/src/main/`.
  - Touching unrelated living document shards (`req_tracking_cockpit.md`, `req_ui_design_system.md`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new governance requirement `REQ-PRO-084` / `TST-PRO-084` and backfill for `REQ-STP-001`..`010` / `TST-STP-001`..`010`. Targets `req_store_presence.md`, `tst_store_presence.md`, `req_process_stability.md`, `tst_process_stability.md`, and `PlayStoreListingContractTest.kt`.
* **Historical Origin & Commit Trace**: Established in Sprint 2026-44.1 (`ATT-4853`, `ATT-2274`, `ATT-2371`), updated in Sprint 2026-46.3 (`ATT-6096`), and sharded in Sprint 2026-46.4 (`ATT-6140`).
* **Root Reason for Existing Formulation**: Store metadata, release hygiene, and contract testing requirements were codified to prevent Google Play rejections and security disclosure in CI baselines.
* **Preservation of Core Invariants**: 100% contract test suite pass rate; master table mathematical parity; zero modification to production Android source files.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: `REQ-STP-001`..`010`, net-new `REQ-PRO-084`.
* **Affected Architecture Elements**:
  - `docs/requirements/req_store_presence.md`
  - `docs/tests/tst_store_presence.md`
  - `docs/requirements/req_process_stability.md`
  - `docs/tests/tst_process_stability.md`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/build/PlayStoreListingContractTest.kt`
  - `docs/requirements.md` & `docs/tests.md`
* **Verification Impact & Test Surface**:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `python3 tools/verify_requirement_governance.py --check-tests-format`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Risk Assessment & Invariants**: Low risk. Confined strictly to documentation metadata, test code tags, and index synchronization.

---

## 6. Architectural Strategy & High-Level Solution

1. **Tag Contract Tests**: Add `(TST-STP-002)` and `(TST-STP-004)` docstrings and tags in `PlayStoreListingContractTest.kt`.
2. **Attribute Enrichment**: Add `Priority`, `Type`, and `Verification Method` to `REQ-STP-001`..`010` in `req_store_presence.md`.
3. **Test Spec Enrichment**: Add `Priority`, `Type`, and `Verification Method` to `TST-STP-001`..`010` in `tst_store_presence.md`.
4. **Register Process Stability Requirement**: Register `REQ-PRO-084` and `TST-PRO-084`.
5. **Synchronize Indexes**: Run `tools/verify_requirement_governance.py --sync-indexes`.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. 100% pass rate in `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`.
  2. 100% mathematical parity across master tables and living shards.
  3. Zero dangling references and zero ungrounded Verified statuses.
* **Risk Rating**: **LOW** (governance documentation and test code tag alignment).
