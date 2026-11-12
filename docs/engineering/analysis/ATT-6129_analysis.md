# Stage 1 Analysis: ATT-6129 - Backfill test evidence for Aftermath Periods requirements

**Ticket**: [ATT-6129](https://rainerblind.atlassian.net/browse/ATT-6129)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-2-Agent---ATT-6129-self-43cd1c63`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the independent ASPICE 4.0 pre-assessment (baseline develop @ sprint/2026-45.11, Remediation ID: A-08.3, ASPICE SWE.6 BP4, SWE.1 BP5), the `aftermath_periods` requirement and test specification shards (`docs/requirements/req_aftermath_periods.md` and `docs/tests/tst_aftermath_periods.md`) were identified as needing test evidence backfill and bidirectional reference integrity reconciliation:
* Baseline analysis indicates gaps in evidence grounding: `REQ-SPT-002` and `REQ-SPT-005` in `req_aftermath_periods.md` omitted their `Tests:` fields, generating ungrounded `[Verified]` warnings tolerated under the legacy allowlist.
* Header counts in `req_aftermath_periods.md` (*Total Requirements: 35*) and `tst_aftermath_periods.md` (*Total Tests: 44*) are outdated relative to the actual 41 requirements and 50 test specifications.
* Bidirectional citation gaps: Multiple test specifications (`TST-PER-021`, `TST-PER-022`, `TST-PER-025`, `TST-PER-026`, `TST-PER-027`, `TST-PER-028`, `TST-PER-034`, `TST-PERF-004`, `TST-SPT-005`, `TST-SPT-006`) cited requirements that did not cite them back.
* Test code tag gap: Automated tests for `PeriodBarGraphTest.kt` (`TST-PER-024`, `TST-PER-029`, `TST-PER-036`), `WorkoutShareBottomSheetContractTest.kt` (`TST-PER-037`), and `SportingJourneyRepositoryTest.kt` / `SportingJourneyViewModelTest.kt` (`TST-SPT-005`) exist in `app/src/test/` but lacked standardized `TST-*` comment tags.
* All 41 requirements in `req_aftermath_periods.md` lack mandatory metadata attributes (`Priority`, `Type`, `Verification Method`) required by `REQ-PRO-077` (ATT-6136).
* The user story requires every REQ in `req_aftermath_periods.md` to be linked to at least one valid, existing TST with corresponding test code (or downgraded to `[Implemented]`), zero dangling references, 100% bidirectional citation, and master index synchronization.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**:
  1. Shard migration (`ATT-4594`) and subsequent feature implementations (`ATT-4016`, `ATT-4019`, `ATT-4475`, `ATT-4476`, `ATT-4477`, `ATT-4770`, `ATT-7321`, `ATT-7323`) added requirements and test specifications without updating top-level header counts.
  2. `REQ-SPT-002` and `REQ-SPT-005` were integrated with thorough unit tests (`SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt`, `SportingJourneyComponentsTest.kt`, `SportingJourneyLocalizationContractTest.kt`, `SportingJourneyShareHelperTest.kt`), but the `Tests:` fields were inadvertently omitted during living documentation drafting.
  3. `PeriodBarGraphTest.kt` used method suffix notation (`_TST_PER_029_1`, etc.) rather than regex-parseable `TST-PER-029` tags, preventing automated discovery in Category 2.
  4. Test specifications in `tst_aftermath_periods.md` copied historical parent/amended requirement IDs (e.g. `REQ-PER-014`, `REQ-UI-401`, `REQ-STB-018`, `REQ-MIG-026`) instead of focusing strictly on the direct requirement under test, introducing Category 4 bidirectional discrepancies.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Automated test suites exist for the vast majority of period and sporting journey components:
    * `PeriodsAggregationTest.kt`, `PeriodsTemporalKeyTest.kt`, `PeriodsAggregationDstTest.kt` (`TST-PER-013`, `TST-PER-015`, `TST-PER-016`, `TST-PER-021`)
    * `PeriodsRepositorySyncTest.kt`, `PeriodSummariesDbTest.kt` (`TST-PER-020`, `TST-PER-022`, `TST-PER-026`)
    * `PeriodBarGraphTest.kt` (`TST-PER-024`, `TST-PER-029`, `TST-PER-036`)
    * `InteractivePeriodMapTest.kt`, `PeriodMapVisualsTest.kt`, `PeriodCardMapStateTest.kt`, `PeriodVertexBudgetTest.kt`, `PeriodMapAdaptiveLayeringTest.kt` (`TST-PER-018`, `TST-PER-019`, `TST-PER-023`, `TST-PER-025`, `TST-PER-027`, `TST-PER-030`..`TST-PER-035`, `TST-PER-038`..`TST-PER-046`, `TST-RAD-005`)
    * `SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt` (`TST-SPT-005`)
    * `SportingJourneyComponentsTest.kt`, `SportingJourneyLocalizationContractTest.kt`, `SportingJourneyShareHelperTest.kt` (`TST-SPT-006`)
    * `WorkoutShareBottomSheetContractTest.kt` (`TST-PER-037`)
  - All 41 requirements in `req_aftermath_periods.md` have existing, valid test specifications.
  - Adding standard `TST-*` tags to test files closes the remaining automated code-tag gaps.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Reconcile `docs/requirements/req_aftermath_periods.md`:
     - Update header total from 35 to 41.
     - Add `Tests: TST-SPT-005` to `REQ-SPT-002` and `Tests: TST-SPT-006` to `REQ-SPT-005`.
     - Update `REQ-PER-001` Tests to cite `TST-PERF-002`, `TST-PERF-004`.
     - Update `REQ-PER-024` Tests to cite `TST-PER-030`, `TST-PER-032`, `TST-PER-034`.
     - Add mandatory `Priority`, `Type`, and `Verification Method` attributes to all 41 requirements per `REQ-PRO-077`.
  2. Reconcile `docs/tests/tst_aftermath_periods.md`:
     - Update header total from 44 to 50.
     - Align `Requirement(s)` lines for `TST-PER-021`, `TST-PER-022`, `TST-PER-025`, `TST-PER-026`, `TST-PER-027`, `TST-PER-028` to ensure 100% bidirectional parity.
  3. Annotate test source code in `app/src/test/`:
     - `PeriodBarGraphTest.kt` with `TST-PER-024`, `TST-PER-029`, `TST-PER-036`.
     - `WorkoutShareBottomSheetContractTest.kt` with `TST-PER-037`.
     - `SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt`, and `SportingJourneyCalculatorTest.kt` with `TST-SPT-005`.
  4. Remove `REQ-SPT-002` and `REQ-SPT-005` from `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py` and `tools/test_verify_requirement_governance.py`.
  5. Register `REQ-PRO-080` and `TST-PRO-080` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
  6. Recalculate master index tables via `python3 tools/verify_requirement_governance.py --sync-indexes`.
* **Out-of-Scope Non-Goals**:
  - Modifying requirements in unrelated domains (`req_tracking_cockpit.md`, `req_aftermath_reviews.md`, etc.).
  - Altering production runtime code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-080` / `TST-PRO-080` and backfill updates for `REQ-PER-001` through `REQ-PER-038`, `REQ-RAD-005`, `REQ-SPT-002`, and `REQ-SPT-005`. Targets `docs/requirements/req_aftermath_periods.md`, `docs/tests/tst_aftermath_periods.md`, and test code in `app/src/test/`.
* **Historical Origin & Commit Trace**: Sprint 2026-46.4 via ticket `ATT-6129` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.3). Builds on `79ac0b23` (ATT-6127), `571e16ae` (ATT-6128), and `bb7868c9` (ATT-6136).
* **Root Reason for Existing Formulation**: Shard migration and incremental feature development produced minor discrepancies in comment tags, header totals, and bidirectional links across periods requirements.
* **Preservation of Core Invariants**: 100% test pass rate preserved; zero production code modification; living documentation master index mathematical parity strictly preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: `REQ-PER-001`..`038`, `REQ-RAD-005`, `REQ-SPT-002`, `REQ-SPT-005`, net-new `REQ-PRO-080`.
* **Affected Architecture Elements**:
  - `docs/requirements/req_aftermath_periods.md`
  - `docs/tests/tst_aftermath_periods.md`
  - `docs/requirements/req_process_stability.md`
  - `docs/tests/tst_process_stability.md`
  - `docs/requirements.md` & `docs/tests.md`
  - `tools/verify_requirement_governance.py` & `tools/test_verify_requirement_governance.py`
  - `app/src/test/.../PeriodBarGraphTest.kt`
  - `app/src/test/.../WorkoutShareBottomSheetContractTest.kt`
  - `app/src/test/.../sportingjourney/SportingJourney*Test.kt`
* **Verification Impact & Test Surface**:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `python3 tools/verify_requirement_governance.py --check-tests-format`
  - `python3 -m unittest tools/test_verify_requirement_governance.py`
  - `./gradlew testDebugUnitTest --tests "*Period*" --tests "*SportingJourney*"`
* **Risk Assessment & Invariants**: Low risk. Confined to living documentation traceability alignment, metadata attributes, and test code tag comments. Zero risk to Android runtime behavior.

---

## 6. Architectural Strategy & High-Level Solution

1. **Tag Backfill**: Add explicit `TST-*` Javadoc comments in test source files to link them unambiguously to living specifications.
2. **Metadata Enrichment**: Add `Priority`, `Type`, and `Verification Method` to all 41 requirements in `req_aftermath_periods.md`.
3. **Traceability Reconciliation**: Link `REQ-SPT-002` and `REQ-SPT-005` to `TST-SPT-005` and `TST-SPT-006`, and align reciprocal requirement references in `tst_aftermath_periods.md`.
4. **Governance Integration**: Register `REQ-PRO-080` / `TST-PRO-080` in process stability shards and eliminate `REQ-SPT-002` and `REQ-SPT-005` from the legacy ungrounded allowlist.
