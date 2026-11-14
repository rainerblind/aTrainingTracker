# Stage 3: Implementation Plan - ATT-7564: Align release and PR CI gates with branch build, including governance script

**Ticket**: [ATT-7564](https://rainerblind.atlassian.net/browse/ATT-7564)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*ASPICE Governance & Process Stability*)  
**Target Release**: `V5.1.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-PRO-090` (*Unified CI & Release Quality Gates and Automated Governance Gating*)  
**Test Mapping**: `TST-PRO-090` (*Unified CI & Release Quality Gates Contract Verification Suite*)  
**Branch**: `feat/ATT-7564`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
ASPICE finding F7 highlighted a critical gap: `.github/workflows/android-release.yml` previously ran only `:app:testDebugUnitTest`, skipping lint, detekt, kover, and requirement governance before producing release binaries. Furthermore, `.github/workflows/android-branch-build.yml` omitted `python3 tools/verify_requirement_governance.py`. This plan details atomic modifications to align gates across both CI workflows and enforce them via automated contract tests.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-090` (`REQ-PRO-090.1`–`REQ-PRO-090.4`)
* **Test Mapping**: `TST-PRO-090` (`TST-PRO-090.1`–`TST-PRO-090.4`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Code Regression**: No modifications to `app/src/main/` production source code.
2. **Deterministic Release Packaging**: Keystore base64 decoding, password provider chain, and APK/AAB naming remain 100% identical.
3. **Report Evidence Integrity**: All test and static analysis reports in release CI are archived with `if: always()` to ensure forensic availability even on gate failure.
4. **Governance Parity**: Living documentation master tables and traceability baselines remain synchronized and validated by `verify_requirement_governance.py`.

---

## 4. Proposed Architectural Changes

### Component 1: Release CI Workflow (`.github/workflows/android-release.yml`)
- Insert lint (`:app:lintDebug`), detekt (`:app:detekt`), kover (`:app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug`), and requirement governance (`python3 tools/verify_requirement_governance.py`) steps before `assembleRelease :app:bundleRelease`.
- Insert report artifact upload steps for lint, detekt, and kover with `if: always()`.

### Component 2: Branch Build CI Workflow (`.github/workflows/android-branch-build.yml`)
- Insert `python3 tools/verify_requirement_governance.py` step in `build-debug` job after Kover and before `assembleDebug`.

### Component 3: CI Governance Contract Suite (`ReleaseCiAndLintConfigContractTest.kt`)
- Add unit contract assertions validating presence, ordering, and artifact archival for all quality gates across both workflows.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Align Release CI Workflow Gates (`REQ-PRO-090.1`, `REQ-PRO-090.2`)
* **File**: `.github/workflows/android-release.yml`
* **Changes**:
  - Insert `:app:lintDebug` + lint report upload step.
  - Insert `:app:detekt` + detekt report upload step.
  - Insert `:app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug` + kover report upload step.
  - Insert `python3 tools/verify_requirement_governance.py` step.
  - Ensure all gate steps precede `:app:assembleRelease :app:bundleRelease`.

### Step 2: Add Governance Script to Branch CI Workflow (`REQ-PRO-090.3`)
* **File**: `.github/workflows/android-branch-build.yml`
* **Changes**:
  - Insert `python3 tools/verify_requirement_governance.py` step in `build-debug` job.

### Step 3: Extend Automated Contract Tests (`REQ-PRO-090.4`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* **Changes**:
  - Add `testReleaseWorkflow_runsLintDetektKoverAndGovernanceBeforeAssembly()`.
  - Add `testReleaseWorkflow_archivesQualityGateReports()`.
  - Add `testBranchBuildWorkflow_runsGovernanceVerification()`.
  - Update test header KDoc citing `REQ-PRO-090`, `TST-PRO-090`, `ATT-7564`.

### Step 4: Verification & Clean-Room Gating
* **Commands**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"`
  - `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `ReleaseCiAndLintConfigContractTest` and `verify_requirement_governance.py`.
* **Rollback**: Clean git branch checkout; `git checkout feat/ATT-7564` can be reset to `sprint/2026-46.6` if any defect occurs.
