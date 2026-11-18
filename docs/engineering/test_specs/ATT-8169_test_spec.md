# Stage 2: Requirement & Test Specification - ATT-8169: Run custom lint safety guards in CI and plan detekt baseline reduction

**Ticket**: [ATT-8169](https://rainerblind.atlassian.net/browse/ATT-8169)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-123` (extending `REQ-PRO-046`, `REQ-PRO-047`, `REQ-DAT-043`, `REQ-STB-016`, `REQ-ARC-001`, `REQ-ARC-005`)  
**Test Spec ID**: `TST-PRO-123` | **Branch**: `feature/ATT-8169`  
**Author**: AI Agent 1 (Implementer, `flash`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-PRO-123`)

### 1.1 Attributes & Rationale
- **Priority**: `Medium` | **Type**: `Non-Functional` | **Verification Method**: `Unit / Review`
- **Rationale**: Remediates ASPICE 4.0 Audit Finding `Minor (SWE.4)` (`SWE.4 BP1/BP3`): orphaned Python safety guards in `tools/`, static baseline ceiling slack (`<= 9550` Detekt vs 9,516 actual; `<= 239` Lint vs 238 actual) without base-ref ratcheting or sprint metrics tracking, and undocumented Java static-analysis tooling evaluation for the 120 retained Java files referenced by `ADR-001`.

### 1.2 Normative Requirements (`REQ-PRO-123.1`–`REQ-PRO-123.4`)
1. **`REQ-PRO-123.1` (CI Custom Safety Guard Gating)**: `.github/workflows/android-branch-build.yml` (with `.github/workflows/android-release.yml` parity) SHALL execute `tools/lint_cursor_safety.py`, `tools/lint_lifecycle_safety.py`, and `tools/lint_safe_enum.py` before `:app:assembleDebug` / `:app:assembleRelease` and MUST fail the build on any non-zero exit code (any new violation).
2. **`REQ-PRO-123.2` (Baseline Ratchet & Sprint Metrics Tracking)**: A deterministic baseline ratchet script in `tools/` invoked by CI SHALL parse `app/detekt-baseline.xml` (`9,516` `<ID>` entries) and `app/lint-baseline.xml` (`238` `<issue>` entries), MUST fail the build if either count increases above its base-ref count or ratcheted ceiling (`9516` / `238`), and `tools/sprint_metrics.py` + `tools/hoechstmass/tools/sprint_metrics.py` SHALL report current Detekt/Lint baseline counts and base-ref/previous-sprint deltas.
3. **`REQ-PRO-123.3` (Contract Test Preservation & Verification)**: Existing `ReleaseCiAndLintConfigContractTest.kt` pins (`./gradlew :app:lintDebug`, `./gradlew :app:detekt`, `verify_requirement_governance.py` ordering prior to `:app:assembleDebug`, baseline checks) SHALL remain preserved alongside new contract assertions for the CI safety-guard and ratchet steps plus offline Python unit tests.
4. **`REQ-PRO-123.4` (Java Static Analysis Evaluation & Burn-Down Plan)**: Engineering documentation outside architecture lock (referencing `ADR-001`, `REQ-ARC-005`) SHALL evaluate **PMD vs. SpotBugs vs. Error Prone** for the remaining `120` `.java` files under Gradle `9.6.0` / AGP `9.4.0` / JDK `21` (Java `17` target) and define an incremental Detekt/Lint baseline burn-down schedule.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **Criterion 1 (Safety Linters in CI)**: *Given* `.github/workflows/android-branch-build.yml` and `android-release.yml`, *When* safety linters run on clean `HEAD` vs a synthetic violation fixture, *Then* clean `HEAD` exits `0` and any violation fails the workflow before assembly.
* **Criterion 2 (Baseline Ratchet & Metrics)**: *Given* `app/detekt-baseline.xml` (`9,516`) and `app/lint-baseline.xml` (`238`), *When* a diff adds a baseline entry or `sprint_metrics.py` renders metrics, *Then* the ratchet exits non-zero on baseline growth (allowing `<=` reduction) and `sprint_metrics.py` + mirror report exact counts and deltas.
* **Criterion 3 (Contract & Governance)**: *Given* `ReleaseCiAndLintConfigContractTest.kt` and `verify_requirement_governance.py`, *When* executed, *Then* all preserved pins + new CI contract checks pass and governance exits `0`.
* **Criterion 4 (Java Evaluation)**: *Given* the Java tooling evaluation referencing `ADR-001`, *When* reviewed, *Then* PMD, SpotBugs, and Error Prone trade-offs on AGP 9.4 / JDK 21 and the Detekt/Lint burn-down milestones are explicitly documented.
* **Invariants**: `app/src/main/` untouched; `ANT-Android-SDKs` untouched; `docs/architecture/*.md` untouched by Stage 2; `Localization Parity: N/A - no UI strings`; existing `ReleaseCiAndLintConfigContractTest` pins preserved.

---

## 2. Test Specification (`TST-PRO-123`)

| Test Case ID | Scope & Target File | Procedure (Action) & Expected Outcome |
| :--- | :--- | :--- |
| `TST-PRO-123.1` | CI & Live Dry-Run (`lint_*.py`, workflows) | Execute all 3 `tools/lint_*.py` scripts on `HEAD` (assert `exit 0`); verify `.github/workflows/android-branch-build.yml` & `android-release.yml` invoke all 3 prior to assembly. |
| `TST-PRO-123.2` | Unit (`tools/test_*.py`, `test_sprint_metrics.py`, `test_hoechstmass_tools.py`) | Test ratchet helper on equal, reduced (pass), and increased (fail) Detekt/Lint XML fixtures vs base ref; verify `sprint_metrics.py` & mirror report counts/deltas. |
| `TST-PRO-123.3` | Contract (`ReleaseCiAndLintConfigContractTest.kt`) | Run `./gradlew testDebugUnitTest --tests "*ReleaseCiAndLintConfigContractTest*"`; assert preserved pins + new CI safety-linter & ratchet step assertions pass. |
| `TST-PRO-123.4` | Review & Governance (`verify_requirement_governance.py`) | Verify PMD vs SpotBugs vs Error Prone evaluation (referencing `ADR-001`) + burn-down plan completeness and `verify_requirement_governance.py` exit `0`. |

---

## 3. Traceability Matrix

| Requirement ID | Normative Scope | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-123.1` | Custom Python safety linters wired into CI workflows | `TST-PRO-123.1` | Unit / Review |
| `REQ-PRO-123.2` | Detekt/Lint base-ref ratcheting + `sprint_metrics.py` reporting | `TST-PRO-123.2` | Unit Test |
| `REQ-PRO-123.3` | `ReleaseCiAndLintConfigContractTest.kt` pin preservation & new checks | `TST-PRO-123.3` | Contract Test |
| `REQ-PRO-123.4` | Java static analysis evaluation (referencing `ADR-001`) & burn-down plan | `TST-PRO-123.4` | Review |
