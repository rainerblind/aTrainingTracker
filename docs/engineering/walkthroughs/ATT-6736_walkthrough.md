# Stage 5: Walkthrough & Verification - ATT-6736: Align integration_strategy.md with emulator-5554/real-phone policy, real test doubles, and sprint_bisect.py

**Ticket**: [ATT-6736](https://rainerblind.atlassian.net/browse/ATT-6736)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-045` (*ASPICE SWE.5 Software Integration Verification Strategy*)  
**Test Mapping**: `TST-PRO-045`  
**Branch**: `feature/ATT-6736`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview

ATT-6736 reconciles `docs/architecture/integration_strategy.md`, `docs/architecture.md`, and `docs/architecture/interfaces.md` with authentic codebase reality (ASPICE SWE.5):
1. **Two-Tier Emulator & Physical Phone Policy**: Level 2 explicitly defines the GitHub Actions CI smoke gate running on Android 14 (`api-level: 34`, `target: google_apis`, `arch: x86_64`) post-ATT-6737, documents local in-sprint emulator retirement under Rule 79, and designates Level 3 for mandatory physical phone evaluation (Pixel 10, Android 16) during Ceremony 2 Joint Review.
2. **Authentic Test Doubles**: Removed hallucinated `MockWebServer` claims from `INT-SWE-08` across `integration_strategy.md` and `architecture.md`, replacing them with authentic test doubles and repository fakes.
3. **Dispatcher & ViewModel Code Grounding**: Corrected `INT-SWE-06` to attribute `dbDispatcher` strictly to `KnownLocationsRepository` (while noting single-thread DB executor serialization), and replaced hallucinated `AftermathViewModel` with `TrackOnMapAftermathViewModel` across `integration_strategy.md`, `architecture.md`, and `interfaces.md`.
4. **Test Counts & Defect Bisection**: Updated Tier 2 test count to `3,660+` and documented `tools/sprint_bisect.py` exponential milestone bisection in Section 5.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-045.1` | `TST-PRO-045.1` | Contract Test (`AspiceSweAuditContractTest`) | **PASSED** | `Verified` |
| `REQ-PRO-045.2` | `TST-PRO-045.1` | Contract Test (`AspiceSweAuditContractTest`) | **PASSED** | `Verified` |
| `REQ-PRO-045.3` | `TST-PRO-045.2` | Governance & Symbol Audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PRO-045.3` | Targeted Contract Regression Suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Contract Regression Suite (`AspiceSweAuditContractTest` & `ReleaseCiAndLintConfigContractTest`)
```text
$ python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "*AspiceSweAuditContractTest*" --tests "*ReleaseCiAndLintConfigContractTest*" --max-workers=2

> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 3m 27s
33 actionable tasks: 12 executed, 21 up-to-date
```

### Requirement Governance & Symbol Grounding Verification
```text
$ python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-6736_test_spec.md

PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero Composable islands detected (no modified UI files).
PASS: Zero repository encapsulation violations detected.
PASS: Architecture & system specification code-grounding and SYSREQ parity verified.
Modified/Altered existing requirement(s) detected: REQ-PRO-045.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

* Pure architectural documentation and integration specification alignment; zero APK runtime bytecode changes; verified via automated contract test suites.
* **Visual Consistency (Rule 23)**: No UI changes.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: All contract tests pass cleanly with 100% success.
2. **Architecture Code Grounding**: All referenced class names (`TrackOnMapAftermathViewModel`, `KnownLocationsRepository`) and tools (`sprint_bisect.py`) verified against production codebase.
3. **Living Documentation Synchronized**: Status in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md` maintained as `Verified`.
4. **Subtask Completion**: Stage 5 subtask transitioned to `Erledigt` via `freigabe`.
5. **Parent Ticket Final Review**: Parent ticket transitioned to `Final Review (Human)` and assigned to `human` for final release sign-off.
