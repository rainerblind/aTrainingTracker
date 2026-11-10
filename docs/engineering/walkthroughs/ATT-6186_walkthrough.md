# Stage 5: Walkthrough & Verification - ATT-6186: Inventory and Phase Out Source-Text-Reading Tests

**Ticket**: [ATT-6186](https://rainerblind.atlassian.net/browse/ATT-6186)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6186`  
**Requirement Mapping**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`) | **Test Mapping**: `TST-PRO-049` (`TST-PRO-049.1`–`TST-PRO-049.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Overview
Formally closed ASPICE pre-assessment finding C-12 by establishing an architectural audit test and ratcheting baseline preventing new source-text-reading tests while exempting legitimate system governance guards and scheduling atomic phase-out follow-up tickets:
* **Audit Scanner & Allow-List**: Implemented `SourceTextReadingAuditContractTest` in `build/` package scanning `app/src/test/java/` for `.readText()`, `readLines()`, `Files.readString()` referencing `src/main` (`REQ-PRO-049.1`).
* **Governance Guard Exemption**: Registered the 13 legitimate architectural contract tests in `LEGITIMATE_GOVERNANCE_GUARDS` preserving essential system policy guards per `docs/coding_standards.md` §5.4 (`REQ-PRO-049.2`).
* **Legacy Debt Baselining**: Baselined the 280 legacy source-reading tests in `app/source-reading-baseline.txt`, creating a ratcheting tripwire against new violations (`REQ-PRO-049.3`).
* **Follow-Up Backlog Tickets**: Created 6 atomic per-package tickets in Product Backlog under parent Epic `ATT-6142` (`REQ-PRO-049.4`):
  - `ATT-6564`: `ui.map` package (45 tests, 2 SP)
  - `ATT-6565`: `ui.tracking` package (40 tests, 2 SP)
  - `ATT-6566`: `ui.aftermath` package (24 tests, 2 SP)
  - `ATT-6567`: `ui.settings` package (21 tests, 2 SP)
  - `ATT-6568`: `ui.routes` package (15 tests, 2 SP)
  - `ATT-6569`: `banalservice.ui`, `migration`, and other packages (35 tests, 2 SP)

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-049.1`, `REQ-PRO-049.3` | `TST-PRO-049.1` | `SourceTextReadingAuditContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-049.2` | `TST-PRO-049.2` | `SourceTextReadingAuditContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-049.1`, `REQ-PRO-049.3` | `TST-PRO-049.3` | `SourceTextReadingAuditContractTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-049.4` | `TST-PRO-049.4` | Targeted Suite `build.*` & Jira verification | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: 58s under parallel sprint lock)
* **Executed Scope**: `Tier 1 targeted: com.atrainingtracker.trainingtracker.build.*`
* **Command**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 \
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2
  ```
* **Results**:
  - `testDebugUnitTest`: 100% PASS across all 14 contract test classes (85/85 tests passed, 0 failures, 0 regressions).
  - `SourceTextReadingAuditContractTest`: All 4 test methods passed (`testSourceReadingAudit_noUnallowlistedSourceReaders`, `testGovernanceGuards_allExplicitlyAllowed`, `testBaseline_containsOnlyExpectedLegacyCount`, `testRatchetingTripwire_detectsUnallowlistedReader`).
* **Scope Note**: Test governance and architectural tooling ticket; on-device UI rendering not applicable.

---

## 4. Key Implementation Highlights

```kotlin
// SourceTextReadingAuditContractTest.kt
@Test
fun testSourceReadingAudit_noUnallowlistedSourceReaders() {
    val testDir = findTestDir()
    val detectedReaders = scanTestSourceFiles(testDir)
    val baseline = loadBaselineLegacyReaders()
    val allowedSet = LEGITIMATE_GOVERNANCE_GUARDS + baseline
    val unallowedReaders = detectedReaders - allowedSet
    assertTrue(unallowedReaders.isEmpty())
}
```

---

## 5. Invariants & Preserved Behavior
1. **Zero False Positives**: All 13 legitimate architectural governance contract tests remain fully supported without failure.
2. **Quality Gate Tripwire**: Any newly authored test reading `src/main` without allow-list entry fails CI.
3. **Sprint Boundary Protection**: Backlog tickets (`ATT-6564`..`ATT-6569`) remain in Product Backlog without disrupting active sprint.
