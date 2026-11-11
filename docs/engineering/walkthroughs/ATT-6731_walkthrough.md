# Stage 5: Walkthrough & Verification - ATT-6731: Move TranslationParityTest and repo governance guards from source-reading baseline to allow-list and ratchet ceiling

**Ticket**: [ATT-6731](https://rainerblind.atlassian.net/browse/ATT-6731)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`)  
**Test Mapping**: `TST-PRO-049` (`TST-PRO-049.1`–`TST-PRO-049.5`)  
**Branch**: `feature/ATT-6731`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview

ATT-6731 hardens architectural governance around legacy source-reading test debt (`docs/coding_standards.md` §5.4):
1. **Promoted 8 Governance Guards**: Registered 8 repository-wide policy scanners (`LocalizationTestCache.kt`, `TranslationParityTest.kt`, `ThemeColorCentralizationAuditTest.kt`, `RepositoryEngineCleanlinessContractTest.kt`, `RepositoryUiCleanlinessContractTest.kt`, `GlobalDeleteContextMenuAuditTest.kt`, `EditIconConsistencyTest.kt`, `AspiceSweAuditContractTest.kt`) into `LEGITIMATE_GOVERNANCE_GUARDS` (total 22), asserting file existence and `.endsWith("Test.kt") || .endsWith("Cache.kt")`.
2. **Purged Baseline Debt & Ratcheted Ceiling**: Removed 6 misclassified governance guards from `app/source-reading-baseline.txt`, recorded 2 recent UI layout debt tests (`CardioPowerTrendsCardTest.kt`, `SensorFieldViewGraphOnlyLayoutTest.kt`), and established the ratcheted ceiling at 276 entries.
3. **Stream Reader Detection & Stale-Entry Tripwire**: Extended `scanTestSourceFiles` to check `useLines` and `bufferedReader`, and asserted `(baseline - detectedReaders).isEmpty()`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-049.1` | `TST-PRO-049.1` | `SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders` | **PASSED** | `Verified` |
| `REQ-PRO-049.2` | `TST-PRO-049.2` | `SourceTextReadingAuditContractTest.testGovernanceGuards_allExplicitlyAllowed` | **PASSED** | `Verified` |
| `REQ-PRO-049.3` | `TST-PRO-049.3` | `SourceTextReadingAuditContractTest.testBaseline_containsOnlyExpectedLegacyCount` | **PASSED** | `Verified` |
| `REQ-PRO-049.3` | `TST-PRO-049.4` | `SourceTextReadingAuditContractTest.testRatchetingTripwire_detectsUnallowlistedReader` | **PASSED** | `Verified` |
| `REQ-PRO-049.4` | `TST-PRO-049.5` | Tier 1 Targeted Suite (`...SourceTextReadingAuditContractTest`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Suite (`com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest`)
```text
BUILD SUCCESSFUL in 7m 30s
33 actionable tasks: 15 executed, 18 from cache
Test run: 4 passed, 0 failed, 0 skipped, 0 errors
- testGovernanceGuards_allExplicitlyAllowed: PASSED (22 guards verified on disk)
- testBaseline_containsOnlyExpectedLegacyCount: PASSED (276 entries, 0 stale)
- testRatchetingTripwire_detectsUnallowlistedReader: PASSED (synthetic reader caught)
- testSourceReadingAudit_noUnallowlistedSourceReaders: PASSED (0 unallowlisted readers)
```

### Living Documentation Governance Parity
```text
python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-6731_test_spec.md
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero Composable islands detected.
PASS: Zero repository encapsulation violations detected.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Pure architectural test and governance debt enhancement; zero APK runtime production code changes; verified via automated test suite.

### Visual Consistency (Rule 23)
No UI changes.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Production runtime code untouched; targeted suite passed with 100% success rate.
2. **Permanent Invariant Protection**: Legitimate system policy scanners are permanently protected from automated phase-out deletion.
3. **Downward Ratcheting**: Stale baseline entries trigger test failure, ensuring legacy debt monotonically decreases.
4. **Living Documentation Synchronized**: `REQ-PRO-049` and `TST-PRO-049` updated to `Verified`.
