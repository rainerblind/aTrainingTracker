# Stage 1 Analysis: ATT-6128 - Backfill test evidence for Aftermath Reviews requirements

**Ticket**: [ATT-6128](https://rainerblind.atlassian.net/browse/ATT-6128)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `subagent-Lane-3-Agent---ATT-6128-self-3dc7c28c`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

During the independent ASPICE 4.0 pre-assessment (baseline develop @ sprint/2026-45.11, Remediation ID: A-08.2, ASPICE SWE.6 BP4, SWE.1 BP5), the `aftermath_reviews` requirement and test specification shards (`docs/requirements/req_aftermath_reviews.md` and `docs/tests/tst_aftermath_reviews.md`) were identified as needing test evidence backfill and bidirectional reference integrity reconciliation:
* Traceability baseline analysis indicates gaps in bidirectional citation: `TST-REV-005` in `docs/tests/tst_aftermath_reviews.md` specifies requirements `REQ-REV-002` and `REQ-REV-003`, but `REQ-REV-002` omitted `TST-REV-005` from its `Tests:` field.
* Test code tag gap: `TST-REV-009` (*Generic ZoneDistributionCard Architecture, Wrapper Delegation & Display Mode Contract Test*) was implemented in `ZoneDistributionCardTest.kt`, but lacked explicit Javadoc test tag annotation.
* Header counts in `req_aftermath_reviews.md` (*Total Requirements: 10*) and `tst_aftermath_reviews.md` (*Total Tests: 11*) were outdated relative to the actual 12 requirements and 13 test specifications.
* Shard requirements lacked structured metadata attributes (`Priority`, `Type`, `Verification Method`) required by `REQ-PRO-077` (ATT-6136).
* The user story requires every REQ in `req_aftermath_reviews.md` to be linked to at least one valid, existing TST with corresponding test code (or downgraded to `[Implemented]`), zero dangling references, 100% bidirectional citation, and master index synchronization.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**:
  1. Historical additions of cardio-power dynamics (`ATT-3904`, `ATT-3905`, `ATT-3906`, `ATT-3907`) and zone distribution cards (`ATT-6044`) appended requirements `REQ-REV-008` through `REQ-REV-012` and tests `TST-REV-008` through `TST-REV-013` without updating the top-level total counts in shard headers.
  2. `ZoneDistributionCardTest.kt` was authored as a pure unit test verifying reflection and wrapper isolation, but omitted the standard `TST-REV-009` citation tag in its class header.
  3. `TST-REV-005` encompasses 9-locale localization parity for both milestone title templates (`REQ-REV-002`) and celebration dialog copy (`REQ-REV-003`), but `REQ-REV-002` only cited `TST-REV-002`.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Forensic scan of `app/src/test` demonstrates automated test suites exist for all 13 `TST-REV-*` specifications:
    * `TST-REV-001`, `002`, `004`: `MilestoneAutoNamingManagerTest.kt`
    * `TST-REV-003`, `006`: `FirstJubileeCelebrationDialogTest.kt`
    * `TST-REV-005`: `SupporterCopyContractTest.kt`, `TranslationParityTest.kt`
    * `TST-REV-007`: `MapDetailLayoutSnapshotContractTest.kt`, `WorkoutShareBottomSheetContractTest.kt`
    * `TST-REV-008`: `EditWorkoutClusterTest.kt`
    * `TST-REV-009`: `ZoneDistributionCardTest.kt`, `WorkoutSummaryClickContractTest.kt`
    * `TST-REV-010`: `WorkoutRepositoryCardioPowerBackfillTest.kt`, `PowerHrDynamicsCalculatorTest.kt`
    * `TST-REV-011`: `WorkoutDataMapperCardioPowerTest.kt`, `WorkoutDetailsCardioPowerRowTest.kt`, `TelemetryMetricGraphOverlayTest.kt`
    * `TST-REV-012`: `CardioPowerTrendsPlotMathTest.kt`, `CardioPowerTrendsCardTest.kt`, `AthleteProfileViewModelCardioPowerTest.kt`, `WorkoutSummariesCardioPowerTrendTest.kt`
    * `TST-REV-013`: `TrackingViewModelLiveDriftTest.kt`, `CyclingPowerMetricsDeviceLiveHrDriftTest.kt`, `SignedIntegerFormatterTest.kt`
  - Therefore, all 12 REQs have verified test code backing. No downgrade to `[Implemented]` is necessary for this domain because test evidence genuinely exists across all items.
  - Adding `(REQ-REV-008, TST-REV-009 / ATT-6044)` to `ZoneDistributionCardTest.kt` and adding `TST-REV-005` to `REQ-REV-002` achieves 100% test-code coverage and 100% bidirectional citation for `aftermath_reviews`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Reconcile `docs/requirements/req_aftermath_reviews.md`:
     - Update header total to 12.
     - Add `TST-REV-005` to `REQ-REV-002` Tests list.
     - Add `Priority`, `Type`, and `Verification Method` attributes to all 12 REQs per `REQ-PRO-077`.
     - Confirm all 12 REQs cite valid `TST-REV-*` IDs and retain `[Verified]` status with evidence backing.
  2. Reconcile `docs/tests/tst_aftermath_reviews.md`:
     - Update header total to 13.
     - Verify bidirectional linkage back to `REQ-REV-*`.
  3. Annotate `app/src/test/.../ZoneDistributionCardTest.kt` with `TST-REV-009`.
  4. Register `REQ-PRO-079` and `TST-PRO-079` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
  5. Run `python3 tools/verify_requirement_governance.py --sync-indexes` and `--coverage` to ensure mathematical parity and >= 95% domain coverage.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying other requirement domains (e.g. `req_aftermath_periods.md`, `req_tracking_cockpit.md`, which are handled by dedicated tickets A-08.1 / A-08.3).
  - Production code logic changes in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-079` and backfill updates for `REQ-REV-001` through `REQ-REV-012`. Targets `docs/requirements/req_aftermath_reviews.md`, `docs/tests/tst_aftermath_reviews.md`, and `ZoneDistributionCardTest.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-46.4 via ticket `ATT-6128` under Epic `ATT-6119` (ASPICE CL2, Remediation A-08.2). Historical requirements established across `ATT-2308`, `ATT-6322`, `ATT-6159`, `ATT-6044`, `ATT-3904`, `ATT-3905`, `ATT-3906`, and `ATT-3907`.
* **Root Reason for Existing Formulation**: Shard migration in `ATT-4594` and subsequent feature additions documented functional requirements and test specifications, but left minor tag disconnections in test code and header count drift.
* **Preservation of Core Invariants**: All functional verification semantics, test execution coverage, and 100% master index mathematical parity remain strictly preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: `REQ-REV-001` through `REQ-REV-012`, net-new `REQ-PRO-079`.
* **Affected Architecture Elements**:
  - `docs/requirements/req_aftermath_reviews.md`
  - `docs/tests/tst_aftermath_reviews.md`
  - `docs/requirements/req_process_stability.md`
  - `docs/tests/tst_process_stability.md`
  - `docs/requirements.md` & `docs/tests.md`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCardTest.kt`
* **Verification Impact & Test Surface**:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `python3 tools/verify_requirement_governance.py --sync-indexes`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionCardTest"`
* **Risk Assessment & Invariants**: Low risk. Confined to living documentation traceability alignment, metadata attributes, and test code tag comments. Zero risk to Android runtime behavior.

---

## 6. Architectural Strategy & High-Level Solution

1. **Tag Backfill**:
   - Add `@see <a href="file:///docs/tests/tst_aftermath_reviews.md">TST-REV-009</a>` and `(REQ-REV-008, TST-REV-009 / ATT-6044)` to `ZoneDistributionCardTest.kt`.
2. **Shard Header & Bidirectional Reference Synchronization**:
   - Set `Total Requirements: 12` in `req_aftermath_reviews.md`.
   - Set `Total Tests: 13` in `tst_aftermath_reviews.md`.
   - Update `REQ-REV-002` Tests to `- **Tests**: \`TST-REV-002\`, \`TST-REV-005\``.
   - Backfill `Priority`, `Type`, `Verification Method` on all 12 `REQ-REV-*` requirements.
3. **Process Stability Governance Registration**:
   - Register `REQ-PRO-079` in `req_process_stability.md` and `TST-PRO-079` in `tst_process_stability.md`.
4. **Index Synchronization**:
   - Run `python3 tools/verify_requirement_governance.py --sync-indexes` to verify 100% mathematical parity.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Master table mathematical parity across all 9 domain shards must remain 100% green.
  2. Domain coverage for `aftermath_reviews` must achieve 100% REQs with TST, 100% TSTs with code, 0 dangling references, and 100% bidirectional citation.
  3. Zero production Android runtime changes.
* **Risk Rating**: **LOW**
  - Justification: Non-functional living documentation alignment and test tag backfill; zero production bytecode mutations.
