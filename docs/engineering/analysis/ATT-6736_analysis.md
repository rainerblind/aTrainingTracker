# Stage 1 Analysis: ATT-6736 - Align integration_strategy.md with emulator-5554/real-phone policy, real test doubles, and sprint_bisect.py

**Ticket**: [ATT-6736](https://rainerblind.atlassian.net/browse/ATT-6736)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6736`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-46.1 review of ATT-6183 (*Write software integration verification strategy*), several repo-grounding discrepancies were identified in `docs/architecture/integration_strategy.md` and Section 6 of `docs/architecture.md`:
1. **Emulator vs. Real-Phone Policy & CI API Parity**: Level 2 currently specifies generic `API 33/34` without documenting the exact GitHub Actions CI smoke gate (`API 34` post-ATT-6737 in `.github/workflows/android-branch-build.yml`), the in-sprint local emulator retirement policy (`Rule 79`), and mandatory real-phone evaluation during `Ceremony 2: Sprint Review`.
2. **Non-Existent `MockWebServer` Dependency**: `INT-SWE-08` claims `MockWebServer` for REST testing, but `MockWebServer` is not a project dependency in `app/build.gradle` (the project relies on authentic repository fakes, test doubles, and `HealthConnectClient` test doubles).
3. **Misattributed Threading & Non-Existent ViewModel**: Line 46 attributes `dbDispatcher` to `WorkoutSamplesDatabaseManager` (which uses `mDbExecutor` / `Dispatchers.IO`), whereas `dbDispatcher` belongs strictly to `KnownLocationsRepository`. Line 53 references a non-existent `AftermathViewModel` (the real ViewModel is `TrackOnMapAftermathViewModel`).
4. **Stale Test Count & Defect Bisection Tooling**: Section 5 references an outdated `1200+` test count (actual count is `3,660+`) and omits `tools/sprint_bisect.py` (`regression-bisect` exponential sprint-milestone bisection).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * Live inspection of `docs/architecture/integration_strategy.md`:
    - Line 46: Claims `WorkoutSamplesDatabaseManager` serializes I/O via `dbDispatcher`. Code audit of `WorkoutSamplesDatabaseManager.java` shows it uses `mDbExecutor` / `Dispatchers.IO`. `dbDispatcher` is defined in `KnownLocationsRepository.kt:88`.
    - Line 52: Claims `MockWebServer for REST`. Code audit of `app/build.gradle` confirms `MockWebServer` is absent from dependencies; cloud tests use authentic test doubles / repository fakes.
    - Line 53: References `AftermathViewModel`. Code audit confirms no `AftermathViewModel` exists; the authentic presentation class is `TrackOnMapAftermathViewModel.kt`.
    - Line 70: States `API 33/34`. post-ATT-6737 CI workflow runs on `api-level: 34`, `target: google_apis`.
    - Line 123: States `1200+ tests`. Actual test suite size across unit and contract tests exceeds `3,660+`.
    - Section 5: Details manual `git bisect` but omits `tools/sprint_bisect.py`.
  * Live inspection of `docs/architecture.md`:
    - Section 6.1 (lines 267 & 273) mirrors the `dbDispatcher` misattribution and `MockWebServer` claim.
  * Live inspection of `docs/architecture/interfaces.md`:
    - Lines 13, 27, 44 cite `AftermathViewModel`, which should be aligned with `TrackOnMapAftermathViewModel`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Align Level 2 in `docs/architecture/integration_strategy.md` with CI API 34 parity (`android-branch-build.yml`), local emulator retirement (`Rule 79`), and mandatory real-phone evaluation in Ceremony 2.
  * Ground `INT-SWE-08` by replacing `MockWebServer` with authentic test doubles and repository fakes in `integration_strategy.md` and `architecture.md`.
  * Clarify `dbDispatcher` attribution to `KnownLocationsRepository` while noting `WorkoutSamplesDatabaseManager` single-thread execution in `integration_strategy.md` and `architecture.md`.
  * Replace hallucinated `AftermathViewModel` with `TrackOnMapAftermathViewModel` across `integration_strategy.md`, `architecture.md`, and `interfaces.md`.
  * Update Tier 2 test count from `1200+` to `3,660+` in `integration_strategy.md`.
  * Document `tools/sprint_bisect.py` in Section 5 of `integration_strategy.md`.
  * Update living documentation `docs/requirements/req_process_stability.md` (`REQ-PRO-045`) and `docs/tests/tst_process_stability.md` (`TST-PRO-045`).
* **Out-of-Scope Non-Goals**:
  * Zero modification to Android production code in `app/src/main/`.
  * Zero modification to Gradle dependency configurations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-045` (*ASPICE SWE.5 Software Integration Verification Strategy*), refining `REQ-PRO-034`. Targets `docs/architecture/integration_strategy.md`, `docs/architecture.md`, and `AspiceSweAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding C-09 (2026-11-08) and tickets ATT-6183, ATT-6736.
* **Root Reason for Existing Formulation**: ATT-6183 initially authored the SWE.5 integration strategy document. Several component references (`AftermathViewModel`, `MockWebServer`) and test counts were draft placeholders not fully cross-referenced against authentic codebase symbols.
* **Preservation of Core Invariants**: Preserves the complete 8-step bottom-up integration sequence (`INT-SWE-01` through `INT-SWE-08`), 3 hierarchical verification levels, entry/exit criteria, and Two-Tier regression selection. Enhances fidelity to 100% authentic code symbols.

---

## 5. Architectural Strategy & High-Level Solution

1. **`docs/architecture/integration_strategy.md`**:
   - Reconcile Section 2 table: Ground `INT-SWE-06` (clarifying `dbDispatcher` for `KnownLocationsRepository`), `INT-SWE-08` (authentic test doubles instead of `MockWebServer`), and `INT-SWE-01` (`TrackOnMapAftermathViewModel`).
   - Reconcile Section 3 Level 2 & 3: Reflect CI API 34 google_apis runner, local in-sprint emulator retirement (Rule 79), and real-phone Joint Review evaluation.
   - Reconcile Section 5: Update test count to `3,660+` and document `tools/sprint_bisect.py` exponential probe bisection.
2. **`docs/architecture.md` Section 6.1**:
   - Update matrix rows for `INT-SWE-06` and `INT-SWE-08` to match code reality.
3. **`docs/architecture/interfaces.md`**:
   - Replace `AftermathViewModel` with `TrackOnMapAftermathViewModel`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero production bytecode alterations in `app/src/main/`.
  2. 100% pass rate across `AspiceSweAuditContractTest` and `ReleaseCiAndLintConfigContractTest`.
  3. All 8 interface seams (`IF-SWE-01`..`IF-SWE-08`) and integration steps (`INT-SWE-01`..`INT-SWE-08`) remain strictly preserved.
* **Risk Rating**: **LOW** (Documentation and architectural alignment only).
