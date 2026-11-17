# Stage 5: Walkthrough & Verification - ATT-8169: Run custom lint safety guards in CI and plan detekt baseline reduction

**Ticket**: [ATT-8169](https://rainerblind.atlassian.net/browse/ATT-8169)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-PRO-123` (`REQ-PRO-123.1`–`REQ-PRO-123.4`) | **Test Mapping**: `TST-PRO-123` (`TST-PRO-123.1`–`TST-PRO-123.4`)  
**Branch**: `feature/ATT-8169` | **Author**: AI Agent 1 (Implementer, `flash`) | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
Resolved ASPICE 4.0 Audit Finding `Minor (SWE.4)` (`SWE.4 BP1/BP3`) without altering `app/src/main/` or `docs/architecture/*.md`:
1. **CI Custom Safety Guards (`REQ-PRO-123.1`)**: Wired `tools/lint_cursor_safety.py`, `tools/lint_lifecycle_safety.py`, and `tools/lint_safe_enum.py` into `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml` prior to assembly.
2. **Monotonic Baseline Ratchet & Sprint Metrics (`REQ-PRO-123.2`)**: Added `tools/check_static_analysis_baselines.py` (`9,516` Detekt `<ID>` entries, `238` Lint `<issue>` entries, Git base-ref check with shallow-clone ceiling fallback) and integrated `static_analysis` counts/deltas + Markdown section into `tools/sprint_metrics.py` and `tools/hoechstmass/tools/sprint_metrics.py` (19-row `process_metrics.jsonl` backward compatible).
3. **Java Evaluation & Burn-Down Plan (`REQ-PRO-123.4`)**: Added `docs/management/qa_plan.md` Section 8 (`v1.2.1`) evaluating PMD (`Recommended`), SpotBugs (`Deferred`), and Error Prone (`Rejected`) for 120 `.java` files on Gradle `9.6.0` / AGP `9.4.0` / JDK `21` (referencing `ADR-001`) plus Detekt (`-250/sprint`) and Lint (`-20/sprint`) burn-down targets.

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-123.1` | `TST-PRO-123.1` | Live `lint_*.py` dry-run (`exit 0`, 0 violations) + CI workflow contract | **PASSED** | `Verified` |
| `REQ-PRO-123.2` | `TST-PRO-123.2` | Live `check_static_analysis_baselines.py` + Python `unittest` suites | **PASSED** | `Verified` |
| `REQ-PRO-123.3` | `TST-PRO-123.3` | Gradle contract tests (`49/49` on `da822e27`, unchanged `.kt`/`.yml`/tools) | **PASSED** | `Verified` |
| `REQ-PRO-123.4` | `TST-PRO-123.4` | `qa_plan.md` Section 8 review + `verify_requirement_governance.py` (`exit 0`) | **PASSED** | `Verified` |

## 3. Automated Test Evidence
* **Live Safety Linters & Baseline Ratchet (post-final-edit)**:
  * `python3 tools/lint_cursor_safety.py && python3 tools/lint_lifecycle_safety.py && python3 tools/lint_safe_enum.py` $\rightarrow$ `exit 0` (`0` violations across all 3).
  * `python3 tools/check_static_analysis_baselines.py --base-ref develop` $\rightarrow$ `exit 0` (`app/detekt-baseline.xml = 9516 <ID>`, `app/lint-baseline.xml = 238 <issue>`, `base=9516/238`, `ceiling=9516/238`).
* **Offline Python Unit Tests (post-final-edit)**:
  * Targeted (`test_check_static_analysis_baselines.py`, `test_sprint_metrics.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`): **74 tests OK**.
  * Full Python discovery (`tools/test_*.py`: **470 tests OK**; `tools/hoechstmass/tools/test_*.py`: **54 tests OK**) executed cleanly on `da822e27` (unchanged `.py` files).
* **Gradle Contract Tests (`ATT-8030` `.kt`/`.yml`/tool parity vs `da822e27` — zero code changes in Stage 5)**:
  * `./gradlew :app:testDebugUnitTest --tests "...ReleaseCiAndLintConfigContractTest" --tests "...HoechstmassToolingContractTest" --tests "...AspiceSweAuditContractTest"`: **BUILD SUCCESSFUL**, **49/49 PASSED** (`ReleaseCiAndLintConfigContractTest` 32/32, `HoechstmassToolingContractTest` 4/4, `AspiceSweAuditContractTest` 13/13; 0 failed, 0 skipped).
* **Governance & Living Docs (post-final-edit)**:
  * `python3 tools/verify_requirement_governance.py --text-file docs/engineering/walkthroughs/ATT-8169_walkthrough.md --check-doc-headers --check-commit-hygiene --check-conflict-markers --check-metrics` and full `verify_requirement_governance.py` $\rightarrow$ `exit 0` (`1227` reqs / `983` verified; `1234` tests / `1012` verified).

## 4. Hardware / Physical Verification (Pixel 10)
Pure CI/tooling/process enhancement; zero APK runtime bytecode or UI changes (`No UI changes.`); verified via live Python guards, offline Python `unittest` suites, and Gradle contract tests.

## 5. Invariant & Governance Verification
1. **Zero `app/src/main/` & Architecture Lock Edits**: Confirmed `git diff de6620a5..HEAD` touches zero `app/src/main/` or `docs/architecture/*.md` files.
2. **Contract Pins & Metrics Compatibility**: All existing `ReleaseCiAndLintConfigContractTest` pins preserved; 19 historical `process_metrics.jsonl` records validated cleanly.
3. **Submodule Integrity**: `ANT-Android-SDKs` verified at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
