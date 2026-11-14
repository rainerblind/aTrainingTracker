# Stage 1 Analysis: ATT-7564 - Align release and PR CI gates with branch build, including governance script

**Ticket**: [ATT-7564](https://rainerblind.atlassian.net/browse/ATT-7564)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*ASPICE Governance & Process Stability*)  
**Target Release**: `V5.1.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7564`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
ASPICE evaluation finding F7 revealed inconsistent quality gates across CI workflows:
- `.github/workflows/android-release.yml` executes only `:app:testDebugUnitTest`, omitting Android Lint (`:app:lintDebug`), Detekt (`:app:detekt`), Kover coverage verification (`:app:koverVerifyDebug`), and requirement governance validation (`python3 tools/verify_requirement_governance.py`).
- `.github/workflows/android-branch-build.yml` executes lint, detekt, and kover, but completely omits requirement governance validation (`tools/verify_requirement_governance.py`), allowing governance and living doc regressions to pass on PRs.
- Releases can currently be packaged from code that bypassed static analysis, coverage, and governance gates.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Root Cause**: CI pipelines were constructed incrementally across separate sprints (ATT-4025, ATT-6171, ATT-6173, ATT-6175) without a centralized gate unification rule. Release CI focused on packaging speed/signing, while PR builds omitted the governance script because it had originally been treated as a local coordinator CLI tool.
- **Independent Peer Review & Verification**:
  - Inspected `.github/workflows/android-release.yml` (lines 102–136): Only `testDebugUnitTest` and release summary generation are present before `assembleRelease` / `bundleRelease`. Lint, detekt, kover, and requirement governance are absent.
  - Inspected `.github/workflows/android-branch-build.yml` (lines 102–153): Lint, detekt, and kover are present, but `tools/verify_requirement_governance.py` is absent.
  - Inspected `ReleaseCiAndLintConfigContractTest.kt`: Tests only assert lint/detekt/kover on `android-branch-build.yml` and `testDebugUnitTest` on `android-release.yml`. No contract tests enforce requirement governance execution in either workflow or full gate parity in release CI.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `:app:lintDebug`, `:app:detekt`, Kover verify/reports, artifact uploads, and `python3 tools/verify_requirement_governance.py` to `.github/workflows/android-release.yml` prior to release assembly.
  2. Add `python3 tools/verify_requirement_governance.py` to `.github/workflows/android-branch-build.yml`.
  3. Extend `ReleaseCiAndLintConfigContractTest.kt` with unit contract assertions verifying presence and ordering of all gates across both workflows.
* **Out-of-Scope Non-Goals**:
  1. No changes to production application code in `app/src/main/`.
  2. No changes to baseline configurations (`lint-baseline.xml`, `detekt-baseline.xml`) or coverage thresholds.
  3. No restructuring of release signing provider chain.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-090` (*Unified Quality Gates Alignment for PR, Branch, and Release CI Pipelines*), refining and extending `REQ-BLD-002`, `REQ-PRO-046`, `REQ-PRO-047`, and `REQ-PRO-048`. Targets `.github/workflows/android-release.yml`, `.github/workflows/android-branch-build.yml`, and `ReleaseCiAndLintConfigContractTest.kt`.
* **Historical Origin & Commit Trace**: Commit `00155b5d` (ATT-4025, `REQ-BLD-002`) added unit test gating to release CI; commit `08a744c1` (ATT-6173, `REQ-PRO-046`) added lint to branch build; commits for ATT-6175 (`REQ-PRO-047`) and ATT-6171 (`REQ-PRO-048`) added detekt and kover to branch build.
* **Root Reason for Existing Formulation**: Incremental pipeline evolution prioritized packaging verification first, deferring static analysis and governance unification.
* **Preservation of Core Invariants**: Existing release signing and artifact publishing behaviors remain intact; clean-room pass rate preserved; gating ensures defective code fails immediately before assembly or PR merge.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-090` (`REQ-PRO-090.1`–`REQ-PRO-090.4`), extending `REQ-BLD-002`, `REQ-PRO-046`, `REQ-PRO-047`, `REQ-PRO-048`.
* **Affected Architecture Elements**: `.github/workflows/android-release.yml`, `.github/workflows/android-branch-build.yml`, `ReleaseCiAndLintConfigContractTest.kt`.
* **Verification Impact & Test Surface**: `ReleaseCiAndLintConfigContractTest.kt` extended with contract tests; full test suite executed via `./gradlew testDebugUnitTest`.
* **Risk Assessment & Invariants**: Zero runtime risk. Invariants: Release signing parameters, artifact names, report paths, and PR triggers preserved without drift.

---

## 6. Architectural Strategy & High-Level Solution
1. Update `.github/workflows/android-release.yml`:
   - Insert steps for Android Lint (`:app:lintDebug`), Detekt (`:app:detekt`), Kover (`:app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug`), and Requirement Governance (`python3 tools/verify_requirement_governance.py`) after unit testing and before release assembly.
   - Add artifact upload steps for release lint, detekt, and kover reports with `if: always()`.
2. Update `.github/workflows/android-branch-build.yml`:
   - Insert step executing `python3 tools/verify_requirement_governance.py` in `build-debug` job.
3. Update `ReleaseCiAndLintConfigContractTest.kt`:
   - Add contract assertions ensuring both workflows execute all required quality gates in correct order.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% clean-room test pass rate; zero production bytecode changes.
  2. Release keystore and signing secret handling remains untouched.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Declarative CI YAML modifications and unit contract tests only).
