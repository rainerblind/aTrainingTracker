# Stage 3: Implementation Plan - ATT-8169: Run custom lint safety guards in CI and plan detekt baseline reduction

**Ticket**: [ATT-8169](https://rainerblind.atlassian.net/browse/ATT-8169)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-123` (`REQ-PRO-123.1`–`REQ-PRO-123.4`) | **Test Mapping**: `TST-PRO-123` (`TST-PRO-123.1`–`TST-PRO-123.4`)  
**Branch**: `feature/ATT-8169` | **Author**: AI Agent 1 (Implementer, `flash`) | **Date**: 2026-11-17

---

## 1. Problem Description & Background
Resolve ASPICE 4.0 Audit Finding `Minor (SWE.4)` (`SWE.4 BP1/BP3`): wire orphaned `tools/lint_*.py` safety guards into CI, replace slack static baseline caps (`<= 9550` / `<= 239`) with a base-ref ratchet + `sprint_metrics.py` reporting, and document the Java static-analysis tooling evaluation + baseline burn-down schedule referencing `ADR-001` without modifying locked `docs/architecture/*.md`.

## 2. Traceability & Requirements Mapping
| Requirement ID | Normative Scope | Test Case ID | Target Components |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-123.1` | CI custom Python safety guards | `TST-PRO-123.1` | `.github/workflows/android-branch-build.yml`, `android-release.yml`, `tools/lint_*.py` |
| `REQ-PRO-123.2` | Baseline ratchet & metrics | `TST-PRO-123.2` | `tools/check_static_analysis_baselines.py`, `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py` |
| `REQ-PRO-123.3` | CI contract test & offline Python tests | `TST-PRO-123.3` | `ReleaseCiAndLintConfigContractTest.kt`, `tools/test_*.py` |
| `REQ-PRO-123.4` | Java evaluation & burn-down plan | `TST-PRO-123.4` | `docs/management/qa_plan.md` (linking `ADR-001`, zero `docs/architecture/*.md` edits) |

## 3. System Invariants & Preserved Behavior
1. **Zero `app/src/main/` & Architecture Lock Edits**: Production code and `docs/architecture/*.md` remain untouched (`Rule 8` / `verify_requirement_governance.py`).
2. **Contract Pin Preservation**: Keep `./gradlew :app:lintDebug`, `./gradlew :app:detekt`, `python3 tools/verify_requirement_governance.py` before `:app:assembleDebug`/`:app:assembleRelease`, and `0..239` / `1..9550` contract bounds intact.
3. **Metrics Backward Compatibility**: Legacy `process_metrics.jsonl` records lacking `static_analysis` remain cleanly readable by `sprint_metrics.py` and `tools/hoechstmass/tools/sprint_metrics.py`.
4. **Submodule & UI Invariance**: `ANT-Android-SDKs` stays at `0e8cea714a231fb9565170d4ed01eafa868f9798`. **UI Consistency**: No UI changes.

## 4. Proposed Architectural Changes
* **Component 1 — Shared Ratchet & Metrics Helper (`tools/check_static_analysis_baselines.py`)**: Pure stdlib XML parser counting `<ID>` in `app/detekt-baseline.xml` (`9,516`) and `<issue>` in `app/lint-baseline.xml` (`238`). CLI `--base-ref <ref>` (resolving `origin/${GITHUB_BASE_REF}` / `develop` / `HEAD~1` via `git show <ref>:<path>`) and explicit ceiling (`--max-detekt 9516 --max-lint 238`) fail with exit `1` if either count increases. Exposes `collect_baseline_metrics(repo_root, history=None)` imported by `sprint_metrics.py` (with graceful fallback in `tools/hoechstmass/tools/sprint_metrics.py`) to populate `metrics["static_analysis"]` (`detekt_baseline_count`, `lint_baseline_count`, deltas vs prior sprint, targets) and a concise Markdown section in `format_metrics_markdown()`.
* **Component 2 — CI Workflow Wiring (`.github/workflows/android-branch-build.yml`, `android-release.yml`)**: Add two steps prior to assembly: (a) `Run custom safety linters` executing `python3 tools/lint_cursor_safety.py`, `python3 tools/lint_lifecycle_safety.py`, and `python3 tools/lint_safe_enum.py`; (b) `Verify static analysis baseline ratchet` executing `python3 tools/check_static_analysis_baselines.py --base-ref "${{ github.base_ref || 'develop' }}"` in branch build and ceiling check in release build.
* **Component 3 — QA Plan Java Tooling & Burn-Down Addendum (`docs/management/qa_plan.md`)**: Bump `Version` to `1.2.1` (keep `Status: Reviewed`), add Section 6 evaluating **PMD** (recommended incremental source-based analyzer on Java 17/21 with ratcheted baseline) vs **SpotBugs** (AGP 9.4 variant bytecode wiring + large filter noise) vs **Error Prone** (JDK 21 `--add-exports` & `javac` failure risk on 120 legacy `.java` files) referencing `ADR-001` (`REQ-ARC-005`), plus milestone burn-down targets for Detekt (`9,516`) and Lint (`238`).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1 (`REQ-PRO-123.2`): Implement `tools/check_static_analysis_baselines.py` + `sprint_metrics.py` & mirror integration
* Files: `tools/check_static_analysis_baselines.py`, `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`
* Action: Implement XML counting, git base-ref comparison, CLI exit-1 ratchet enforcement, and `static_analysis` metrics + Markdown table rendering with backward-compatible defaults.
### Step 2 (`REQ-PRO-123.1`, `REQ-PRO-123.2`): Wire custom safety linters and baseline ratchet into CI workflows
* Files: `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`
* Action: Add `lint_*.py` execution and `check_static_analysis_baselines.py` steps before `:app:assembleDebug` / `:app:assembleRelease` without altering existing `ReleaseCiAndLintConfigContractTest` pins.
### Step 3 (`REQ-PRO-123.4`): Document Java static-analysis evaluation & burn-down plan in `docs/management/qa_plan.md`
* Files: `docs/management/qa_plan.md`
* Action: Add Section 6 (PMD vs SpotBugs vs Error Prone trade-offs for 120 Java files on Gradle 9.6.0 / AGP 9.4.0 / JDK 21 linking `ADR-001` + Detekt/Lint burn-down plan), bump patch version `1.2.1`, update Change History.
### Step 4 (`REQ-PRO-123.3`): Author Python unit tests & Kotlin CI contract tests
* Files: `tools/test_check_static_analysis_baselines.py`, `tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* Action: Test equal/reduced/increased XML baseline fixtures, `sprint_metrics` + mirror `static_analysis` output, and add `ReleaseCiAndLintConfigContractTest` assertions for the 3 `lint_*.py` scripts and `check_static_analysis_baselines.py` in CI workflows (respecting `Rule 5` / `REQ-PRO-049.1`).
### Step 5: Targeted Verification & Status Promotion
* Commands:
  * `python3 tools/lint_cursor_safety.py && python3 tools/lint_lifecycle_safety.py && python3 tools/lint_safe_enum.py`
  * `python3 tools/check_static_analysis_baselines.py`
  * `python3 -m unittest tools/test_check_static_analysis_baselines.py tools/test_sprint_metrics.py tools/hoechstmass/tools/test_sprint_metrics.py tools/hoechstmass/tools/test_hoechstmass_tools.py`
  * `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
  * `python3 tools/verify_requirement_governance.py`

## 6. Verification & Rollback Plan
* **Verification**: Targeted Python unit tests + `ReleaseCiAndLintConfigContractTest` in Stage 4; clean-room governance & full verification in Stage 5.
* **Rollback**: Isolated on `feature/ATT-8169` — `git checkout develop` or dropping the branch cleanly reverts all changes.
