# Stage 5: Walkthrough & Verification - ATT-7564: Align release and PR CI gates with branch build, including governance script

**Ticket**: [ATT-7564](https://rainerblind.atlassian.net/browse/ATT-7564)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*ASPICE Governance & Process Stability*)  
**Target Release**: `V5.1.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-PRO-090` (*Unified CI & Release Quality Gates and Automated Governance Gating*)  
**Test Mapping**: `TST-PRO-090` (*Unified CI & Release Quality Gates Contract Verification Suite*)  
**Branch**: `feat/ATT-7564`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

Resolved ASPICE evaluation finding F7 by unifying and aligning continuous integration quality gates across release and development pipelines:
1. Updated `.github/workflows/android-release.yml` to execute Android Lint (`:app:lintDebug`), Detekt static analysis (`:app:detekt`), Kover coverage report and threshold verification (`:app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug`), and ASPICE requirement governance verification (`python3 tools/verify_requirement_governance.py`) prior to release assembly (`assembleRelease :app:bundleRelease`). Configured report artifact uploading for lint, detekt, and kover with `if: always()`.
2. Updated `.github/workflows/android-branch-build.yml` to execute `python3 tools/verify_requirement_governance.py` in the `build-debug` job on all branch pushes and pull requests before debug assembly.
3. Extended `ReleaseCiAndLintConfigContractTest.kt` with unit contract tests verifying presence, ordering, and artifact uploading across both workflows.
4. Verified that all 30 contract tests pass with 100% success rate and requirement governance passes with exit code 0.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-090.1` | `TST-PRO-090.1` | Release CI Gate Parity & Ordering Contract (`testReleaseWorkflow_runsLintDetektKoverAndGovernanceBeforeAssembly`) | **PASSED** | `Verified` |
| `REQ-PRO-090.2` | `TST-PRO-090.2` | Release CI Report Artifact Archiving Contract (`testReleaseWorkflow_archivesQualityGateReports`) | **PASSED** | `Verified` |
| `REQ-PRO-090.3` | `TST-PRO-090.3` | Branch Build Governance Step Contract (`testBranchBuildWorkflow_runsGovernanceVerification`) | **PASSED** | `Verified` |
| `REQ-PRO-090.4` | `TST-PRO-090.4` | Governance Verification & Living Doc Sync Suite (`python3 tools/verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope: `ReleaseCiAndLintConfigContractTest`
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" --max-workers=2

BUILD SUCCESSFUL in 4m 1s
33 actionable tasks: 19 executed, 9 from cache, 5 up-to-date
```
All 30 contract tests passed with 100% success (0 failures, 0 errors, 0 skipped):
- `testReleaseWorkflow_runsLintDetektKoverAndGovernanceBeforeAssembly` (`TST-PRO-090.1`): PASSED
- `testReleaseWorkflow_archivesQualityGateReports` (`TST-PRO-090.2`): PASSED
- `testBranchBuildWorkflow_runsGovernanceVerification` (`TST-PRO-090.3`): PASSED
- `testReleaseWorkflow_runsUnitTestsBeforeAssembly`: PASSED
- `testReleaseWorkflow_packagesAndAttachesUnitTestResults`: PASSED
- `testBranchBuildWorkflow_runsLintAndUploadsReport`: PASSED
- `testBranchBuildWorkflow_runsDetektAndUploadsReport`: PASSED
- `testBranchBuildWorkflow_runsKoverAndUploadsReport`: PASSED
- `testBranchBuildWorkflow_runsEmulatorTestsAndUploadsReport`: PASSED
- `testBranchBuildWorkflow_archivesUnitTestResultsWithAlwaysCondition`: PASSED
- `testWorkflowYaml_syntaxAndStructure`: PASSED
- (plus 19 other build, lint, and security contract tests): PASSED

### Requirement Governance & Living Documentation Verification
```text
python3 tools/verify_requirement_governance.py

PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: All REQ and TST heading IDs are unique across living documentation shards.
PASS: All cited REQ and TST references resolve to authentic headings.
PASS: Zero ungrounded [Verified] requirements detected (0 legacy requirements tolerated pending A-08 backfill).
PASS: All Tests fields in requirement shards conform to valid TST ID and placeholder syntax (REQ-PRO-075).
PASS: Zero attribute violations detected (119 legacy requirements tolerated without attributes pending backfill).
PASS: 100% REQ-TST bidirectional traceability verified (15 legacy orphans tolerated in baseline).
PASS: Zero Composable islands detected (no modified UI files).
PASS: Zero repository encapsulation violations detected.
PASS: Zero architecture or system specification modifications detected.
PASS: 100% System-to-Software requirement traceability verified (12/12 SYSREQs covered by software requirements, zero dangling SYSREQs).
Net-new requirement(s) detected: REQ-PRO-090. Bypassing archaeology check cleanly.
Exit code: 0
```

---

## 4. Hardware / Physical Verification (Pixel 10)

This change consists strictly of declarative GitHub Actions workflow configuration (`.github/workflows/`), automated unit contract testing (`ReleaseCiAndLintConfigContractTest.kt`), and ASPICE living documentation synchronization. Zero Android application runtime bytecode modified; no UI changes; no device-specific hardware execution required.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Zero modifications to production source code in `app/src/main/`.
2. **Deterministic Release Packaging**: Keystore decoding, signing secret provider chain, and APK/AAB outputs remain untouched.
3. **Forensic Report Archiving**: All static analysis, lint, coverage, and test reports are uploaded with `if: always()`.
4. **Living Documentation Synchronized**: `REQ-PRO-090` and `TST-PRO-090` transitioned to `[Verified]`; master tables re-indexed with 100% parity.
5. **ASPICE SUP.8 / SWE.4 Parity**: Ensures release builds and branch builds enforce identical quality gates, eliminating gate drift.
