# Stage 3: Implementation Plan - ATT-6736: Align integration_strategy.md with emulator-5554/real-phone policy, real test doubles, and sprint_bisect.py

**Ticket**: [ATT-6736](https://rainerblind.atlassian.net/browse/ATT-6736)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-045`  
**Test Mapping**: `TST-PRO-045`  
**Branch**: `feature/ATT-6736`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Sprint 2026-46.1 review identified discrepancies in `docs/architecture/integration_strategy.md`, `docs/architecture.md`, and `docs/architecture/interfaces.md`:
1. Level 2 emulator environment lacks parity with the CI runner (`API 34`, `google_apis`, `x86_64` post-ATT-6737 in `.github/workflows/android-branch-build.yml`), local in-sprint emulator retirement (`Rule 79`), and mandatory real-phone evaluation in Ceremony 2.
2. `MockWebServer` is cited as a REST double despite not being a project dependency.
3. `dbDispatcher` is misattributed to `WorkoutSamplesDatabaseManager` instead of `KnownLocationsRepository`.
4. Hallucinated `AftermathViewModel` is cited instead of authentic `TrackOnMapAftermathViewModel`.
5. Tier 2 test count states `1200+` instead of `3,660+`, and Section 5 omits `tools/sprint_bisect.py`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-045` (*ASPICE SWE.5 Software Integration Verification Strategy*)
* **Test Mapping**: `TST-PRO-045` (*ASPICE SWE.5 Software Integration Verification Strategy Contract Test Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Production Regressions**: No modifications to `app/src/main/` production source code.
2. **Interface Seam Invariance**: All 8 interface definitions (`IF-SWE-01`..`IF-SWE-08`) and integration steps (`INT-SWE-01`..`INT-SWE-08`) remain fully preserved.
3. **Subtask Self-Sufficiency & Human Gate**: Subtasks transition to `Erledigt` via `freigabe`; parent ticket transitions to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `docs/architecture/integration_strategy.md`
* Section 2 (`Integration Sequence Matrix`):
  - `INT-SWE-06`: Ground single-threaded DB execution and attribute `dbDispatcher` to `KnownLocationsRepository`.
  - `INT-SWE-08`: Replace `MockWebServer` with authentic test doubles / repository fakes.
  - `INT-SWE-01`: Replace `AftermathViewModel` with `TrackOnMapAftermathViewModel`.
* Section 3 (`Verification Levels`):
  - Level 2: Document CI smoke gate on Android 14 (`api-level: 34`, `target: google_apis`, `arch: x86_64`) matching `.github/workflows/android-branch-build.yml`, and local in-sprint emulator retirement (`Rule 79`).
  - Level 3: Document mandatory physical phone evaluation during Ceremony 2 Joint Review.
* Section 5 (`Regression Selection & Defect Bisection`):
  - Update Tier 2 test count from `1200+` to `3,660+`.
  - Document `tools/sprint_bisect.py` exponential milestone probing (`probes`, `deploy-probe`, `bisect-start`, `analyze`).

### Component 2: `docs/architecture.md` (Section 6.1)
* Update Section 6.1 table rows for `INT-SWE-06` and `INT-SWE-08` to reflect authentic test doubles and dispatcher attribution.

### Component 3: `docs/architecture/interfaces.md`
* Replace `AftermathViewModel` references in lines 13, 27, and 44 with `TrackOnMapAftermathViewModel`.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `docs/architecture/integration_strategy.md`
* Reconcile Section 2 matrix, Section 3 Level 2/3 definitions, and Section 5 test counts / `sprint_bisect.py`.

### Step 2: Update `docs/architecture.md`
* Reconcile Section 6.1 matrix rows for `INT-SWE-06` and `INT-SWE-08`.

### Step 3: Update `docs/architecture/interfaces.md`
* Reconcile `IF-SWE-01` and `IF-SWE-02` ViewModel references to `TrackOnMapAftermathViewModel`.

### Step 4: Verification
* Run unit contract tests:
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "*AspiceSweAuditContractTest*" --tests "*ReleaseCiAndLintConfigContractTest*" --max-workers=2`
* Run requirement governance check:
  `python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-6736_test_spec.md`

---

## 6. Verification & Rollback Plan

* **Verification**: `AspiceSweAuditContractTest` and `ReleaseCiAndLintConfigContractTest` validate document structure and section completeness.
* **Rollback**: Clean branch isolation on `feature/ATT-6736` allows reverting all changes cleanly.
