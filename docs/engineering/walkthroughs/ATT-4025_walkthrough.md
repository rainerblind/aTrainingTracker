# Stage 5 Walkthrough & Verification: ATT-4025

**Ticket**: [ATT-4025](https://rainerblind.atlassian.net/browse/ATT-4025) Run unit tests and lint in release CI and reduce lint baseline  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `Backlog` | **Sprint**: Product Backlog | **Branch**: `feature/ATT-4025`  
**Requirement Mapping**: `REQ-BLD-002` (`REQ-BLD-002.1` – `REQ-BLD-002.5`)  
**Test Mapping**: `TST-BLD-002` (`TST-BLD-002.1` – `TST-BLD-002.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-10-22  

---

## 1. Executive Summary & Verification Overview

Prior to ATT-4025, the release CI workflow (`.github/workflows/android-release.yml`) built and published release APK/AAB artifacts without running unit tests, allowing potential defects to bypass distribution gating. In addition, `AndroidManifest.xml` and `lint-baseline.xml` contained orphaned references to deleted `StravaSegmentsIntentService` (`MissingClass`), while `app/build.gradle` lacked documented technical rationales for active lint suppressions (`DuplicatePlatformClasses`, `MissingTranslation`, `Range`).

Remediation was fully implemented and verified across all components:
1. **Release CI Test Gating**: `.github/workflows/android-release.yml` now executes `./gradlew :app:testDebugUnitTest` prior to `assembleRelease` and `bundleRelease`, halting on failure.
2. **Manifest & Baseline Hygiene**: Excised orphaned `StravaSegmentsIntentService` declaration from `AndroidManifest.xml` and its stale issue entry from `lint-baseline.xml`.
3. **Build Script Documentation**: Added inline technical rationales in `app/build.gradle` explaining why `DuplicatePlatformClasses`, `MissingTranslation` (guarded by `TranslationParityTest`), and `Range` are suppressed.
4. **Contract Verification**: Established `ReleaseCiAndLintConfigContractTest.kt` verifying all configuration contracts deterministically.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Test Class | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-BLD-002.1` | `TST-BLD-002.1` | Release CI workflow gates assembly on `testDebugUnitTest` (`ReleaseCiAndLintConfigContractTest`) | **PASS** | `Verified` |
| `REQ-BLD-002.2` | `TST-BLD-002.3` | `checkReleaseBuilds = true` & documented lint suppressions (`ReleaseCiAndLintConfigContractTest`) | **PASS** | `Verified` |
| `REQ-BLD-002.3` | `TST-BLD-002.2` | Manifest component hygiene without `StravaSegmentsIntentService` (`ReleaseCiAndLintConfigContractTest`) | **PASS** | `Verified` |
| `REQ-BLD-002.4` | `TST-BLD-002.4` | Lint baseline hygiene without stale service issue (`ReleaseCiAndLintConfigContractTest`) | **PASS** | `Verified` |
| `REQ-BLD-002.5` | `TST-BLD-002.1` | Automated contract test verification suite (`ReleaseCiAndLintConfigContractTest`) | **PASS** | `Verified` |
| `REQ-LOC-001` | `TST-STR-018` | 9-language translation parity audit (`TranslationParityTest`) | **PASS** | `Verified` |
| `REQ-PRO-001` | `TST-BLD-002.5` | Clean-room unit test regression execution (`testDebugUnitTest`) | **PASS** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Unit Suites & Contract Tests
```text
ReleaseCiAndLintConfigContractTest (4 tests) - ALL PASSED
  • testReleaseWorkflow_runsUnitTestsBeforeAssembly: PASSED
  • testAndroidManifest_noOrphanedStravaSegmentsIntentService: PASSED
  • testBuildGradle_lintConfigurationAndRationales: PASSED
  • testLintBaseline_noStaleStravaSegmentsIntentService: PASSED

TranslationParityTest (9 locales: values, de, es, fr, it, ja, nl, pl, pt) - ALL PASSED

BUILD SUCCESSFUL in 2s (32 actionable tasks, 0 failures, 0 regressions)
```

---

## 4. Invariant & Governance Verification

- **Requirement Governance**: `python3 tools/verify_requirement_governance.py` passed with exit code 0.
- **Living Documentation**: `REQ-BLD-002` in `docs/requirements.md` and `TST-BLD-002` in `docs/tests.md` updated to `Verified`.
- **System Invariants**: Release APK/AAB signing and asset publishing intact; 100% clean-room test pass rate; zero git commits on `feature/ATT-4025` (Rule 8).
