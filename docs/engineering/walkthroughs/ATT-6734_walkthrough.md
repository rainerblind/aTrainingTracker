# Stage 5: Walkthrough & Verification - ATT-6734: Expand ArchitectureConformanceContractTest to physiology/ and banalservice/ui/, strip comments, and guard database/ upward imports

**Ticket**: [ATT-6734](https://rainerblind.atlassian.net/browse/ATT-6734)  
**Sub-task**: [ATT-6882](https://rainerblind.atlassian.net/browse/ATT-6882) (`[Test]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-PRO-050` (*Automated Architecture Conformance Testing*)  
**Test Mapping**: `TST-PRO-050` (*Automated Architecture Conformance Test Suite*)  
**Branch**: `feature/ATT-6734`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview
`ArchitectureConformanceContractTest.kt` was extended to eliminate blind spots, protect against comment false positives, ratchet down legacy UI database debt, and guard the persistence layer against upward dependencies:
1. **Domain & Physiology JVM Purity**: `testDomainLayer_pureJvmIsolation()` was expanded to scan both `domain/` and `physiology/` calculation engines, ensuring zero Android View, Widget, Compose, or DatabaseManager imports.
2. **Comment Stripping & UI Layer Expansion**: `testUiLayer_noUnallowlistedDatabaseManagerReferences()` now strips block (`/* ... */`) and line (`// ...`) comments and scans both `trainingtracker/ui/` and `banalservice/ui/`.
3. **Downward Ratcheting Enforcement**: Ratcheted baseline was adjusted to exactly 23 callers (18 in `trainingtracker/ui` + 5 in `banalservice/ui`), removing 6 refactored files and enforcing `(baseline - detectedCallers).isEmpty()` and `detectedCallers.size <= 23`.
4. **Database Upward Import Guard**: Added `testDatabaseLayer_noUpwardUiOrComposeImports()` verifying zero `androidx.compose.*` or UI screen imports across all `database/` packages.
5. **Persistence Hygiene**: Removed dead `LookaheadScope` in `SportTypeEquipmentLinkManager.kt` and relocated corridor UI models to `ui/clusters/WorkoutClusterCorridorUi.kt`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-050.1`, `REQ-PRO-050.3` | `TST-PRO-050.1` | Automated Contract Test (`testUiLayer_noUnallowlistedDatabaseManagerReferences`) | **PASSED** | `Verified` |
| `REQ-PRO-050.2` | `TST-PRO-050.2` | Automated Contract Test (`testDomainLayer_pureJvmIsolation`) | **PASSED** | `Verified` |
| `REQ-PRO-050.5` | `TST-PRO-050.5` | Automated Contract Test (`testDatabaseLayer_noUpwardUiOrComposeImports`) | **PASSED** | `Verified` |
| `REQ-PRO-050.3` | `TST-PRO-050.3` | Ratcheting Tripwire Tests | **PASSED** | `Verified` |
| `REQ-UI-106` | `TST-PRO-050.Localization` | 9-Language Localization Audit | **PASSED** | `Verified` (0 UI strings modified) |
| `REQ-PRO-001`, `REQ-PRO-043` | `TST-PRO-050.4` | Clean-Room Contract Suite Execution | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`)
```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 12s
33 actionable tasks: 4 executed, 29 up-to-date
```

### Requirement Governance Parity (`python3 tools/verify_requirement_governance.py`)
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
Modified/Altered existing requirement(s) detected: REQ-PRO-050.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
Pure SWE.2 architecture conformance test and structural hygiene enhancement; zero visual or behavioral regressions in runtime app; verified via automated contract test suite.

### Visual Consistency (Rule 23)
No UI changes.

---

## 5. Invariant & Governance Verification
1. **Zero Production Regressions**: All 23 legitimate legacy UI callers continue operating uninterrupted; clean compilation and test execution.
2. **Living Documentation Synchronized**: `REQ-PRO-050` and `TST-PRO-050` updated with 4-field Chesterton's Fence archaeology audit; master index tables maintain 100% parity.
3. **Subtask Completion**: Stage 5 subtask submitted for Gate 5 review.
4. **Parent Ticket Final Review**: Ready for Gate 5 PASS verdict, parallel sprint squash-merge, and transition to Final Review (Human).
