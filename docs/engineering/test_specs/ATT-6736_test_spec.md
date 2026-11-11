# Stage 2: Requirement & Test Specification - ATT-6736: Align integration_strategy.md with emulator-5554/real-phone policy, real test doubles, and sprint_bisect.py

**Ticket**: [ATT-6736](https://rainerblind.atlassian.net/browse/ATT-6736)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-045` (*ASPICE SWE.5 Software Integration Verification Strategy*)  
**Test Spec ID**: `TST-PRO-045`  
**Branch**: `feature/ATT-6736`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-045)

### 1.1 Problem Statement & Rationale
`docs/architecture/integration_strategy.md` and Section 6 of `docs/architecture.md` contained several discrepancies against the live codebase: citing `MockWebServer` (not in dependencies), attributing `dbDispatcher` to `WorkoutSamplesDatabaseManager` (belongs strictly to `KnownLocationsRepository`), citing `AftermathViewModel` (authentic class is `TrackOnMapAftermathViewModel`), stating `1200+` tests instead of `3,660+`, and omitting `tools/sprint_bisect.py` and the two-tier emulator / physical phone policy with CI API 34 parity. Reconciling these specifications ensures 100% code-grounded architectural compliance under ASPICE SWE.5.

### 1.2 Functional & Architectural Requirements
* **`REQ-PRO-045.1`**: `docs/architecture/integration_strategy.md` SHALL accurately reflect the two-tier verification policy: local emulator retirement during autonomous in-sprint execution (`Rule 79`), CI smoke testing on Android 14 (`api-level: 34`, `target: google_apis`, `arch: x86_64`) matching `.github/workflows/android-branch-build.yml`, and mandatory physical phone evaluation during Ceremony 2 Joint Review.
* **`REQ-PRO-045.2`**: `integration_strategy.md` and `docs/architecture.md` SHALL eliminate `MockWebServer` references in `INT-SWE-08`, replacing them with authentic test doubles and repository fakes.
* **`REQ-PRO-045.3`**: `integration_strategy.md` and `docs/architecture.md` SHALL correct `dbDispatcher` attribution to `KnownLocationsRepository` (while noting `WorkoutSamplesDatabaseManager` single-threaded database execution) and replace `AftermathViewModel` with `TrackOnMapAftermathViewModel`.
* **`REQ-PRO-045.4`**: `integration_strategy.md` Section 5 SHALL update the Tier 2 full test suite count to `3,660+` and document `tools/sprint_bisect.py` exponential milestone bisection and git bisect workflows.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PRO-045` (*ASPICE SWE.5 Software Integration Verification Strategy*), refining `REQ-PRO-034`. Targets `docs/architecture/integration_strategy.md`, `docs/architecture.md`, and `AspiceSweAuditContractTest.kt`.
2. *Historical Origin & Commit Trace*: Independent ASPICE pre-assessment finding C-09 (2026-11-08) and tickets ATT-6183, ATT-6736.
3. *Root Reason for Existing Formulation*: ATT-6183 initially authored the SWE.5 integration strategy document. Several component references (`AftermathViewModel`, `MockWebServer`) and test counts were draft placeholders not fully cross-referenced against authentic codebase symbols.
4. *Preservation of Core Invariants*: Preserves the complete 8-step bottom-up integration sequence (`INT-SWE-01` through `INT-SWE-08`), 3 hierarchical verification levels, entry/exit criteria, and Two-Tier regression selection. Enhances fidelity to 100% authentic code symbols.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Two-Tier Emulator/Real-Phone Policy & CI API Level Parity)**:
  * *Given* Level 2 in `docs/architecture/integration_strategy.md`,
  * *When* compared against `.github/workflows/android-branch-build.yml` and project governance rules,
  * *Then* the CI emulator API level matches `android-branch-build.yml` (`API 34`, `google_apis`, `x86_64`), local emulator retirement under Rule 79 is documented, and mandatory real-phone evaluation during Ceremony 2 Joint Review is explicitly specified.
* **Criterion 2 (Authentic Test Doubles, Threading, Counts & Bisect Tooling)**:
  * *Given* `docs/architecture/integration_strategy.md`, `docs/architecture.md`, and `docs/architecture/interfaces.md`,
  * *When* audited against `app/build.gradle`, `app/src/`, and `tools/`,
  * *Then* `MockWebServer`, `AftermathViewModel`, and misattributed `dbDispatcher` claims are replaced with authentic test doubles/classes (`TrackOnMapAftermathViewModel`, `KnownLocationsRepository`), Tier 2 test count reflects `3,660+`, and Section 5 documents `tools/sprint_bisect.py`.

### 1.4 System Invariants
1. Zero production code modifications in `app/src/main/`.
2. All 8 interface seams (`IF-SWE-01`..`IF-SWE-08`) and integration steps (`INT-SWE-01`..`INT-SWE-08`) remain intact.
3. 100% unit and contract test pass rate.

---

## 2. Test Specification (TST-PRO-045)

### Test Case 1: `testArchitectureDocument_softwareIntegrationStrategy` (`TST-PRO-045.1`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`
* **Preconditions**: `docs/architecture/integration_strategy.md` and `docs/architecture.md` exist.
* **Action**: Assert core sections, interface mappings (`IF-SWE-01`..`08`), and Section 6 integration summary.
* **Expected Result**: Assertions pass with 100% success.

### Test Case 2: Code Grounding Audit of Architecture & Integration Strategy (`TST-PRO-045.2`)
* **Scope**: Static Code Grounding Audit
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Expected Result**: Exit code 0, 100% parity across living doc shards and master index tables, zero non-existent backticked symbols.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-PRO-045.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "*ReleaseCiAndLintConfigContractTest*" --tests "*AspiceSweAuditContractTest*" --max-workers=2`
* **Expected Result**: 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Command | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-045.1` | Contract | `AspiceSweAuditContractTest.kt` | `REQ-PRO-045.1`, `REQ-PRO-045.2` | Specified |
| `TST-PRO-045.2` | Governance | `verify_requirement_governance.py` | `REQ-PRO-045.1`, `REQ-PRO-045.3` | Specified |
| `TST-PRO-045.3` | Regression | `AspiceSweAuditContractTest` / `ReleaseCiAndLintConfigContractTest` | `REQ-PRO-045.3` | Specified |
