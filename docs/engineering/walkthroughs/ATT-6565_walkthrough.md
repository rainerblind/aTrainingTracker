# Stage 5: Walkthrough & Verification - ATT-6565: Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.tracking

**Ticket**: [ATT-6565](https://rainerblind.atlassian.net/browse/ATT-6565)  
**Sub-task**: [ATT-7220](https://rainerblind.atlassian.net/browse/ATT-7220) (`[Test]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-TST-006` (`REQ-TST-006.1`..`REQ-TST-006.3`), refining `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`) & `REQ-TST-002`  
**Test Mapping**: `TST-TST-006` (`TST-TST-006.1`..`TST-TST-006.3`), validating `TST-PRO-049`  
**Branch**: `feature/ATT-6565`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview

ATT-6565 eliminates source-text reading anti-patterns (`File.readText()`, `readLines()`, and raw `src/main` reads) across all 42 legacy tests in `com.atrainingtracker.trainingtracker.ui.tracking` per ASPICE finding C-12 and `docs/coding_standards.md` §5.4:

1. **`ui.tracking` Refactoring (42 Tests)**:
   - **`controltracking` (15 Tests)**: Replaced source text grep on `ControlTrackingScreen.kt`, `PairingButtons.kt`, and permission dialogs with JVM reflection on compiled `*Kt` classes, behavioral sport type selector assertions, and `LocalizationTestCache` 9-locale string verification.
   - **Root `ui.tracking` (10 Tests)**: Migrated `SensorFieldView.kt` and `HrvInfoBottomSheet.kt` inspections to reflection; verified bike power defaults, gauge bar layout models, and tab localization behaviorally.
   - **`tracking` & `trackingtabs` (15 Tests)**: Refactored `SensorGridScreen.kt`, `TrackingTabsScreen.kt`, and `BikeBellButton.kt` layout tests to reflection and pure behavioral state assertions.
   - **`editsensorfield` & `typography` (2 Tests)**: Migrated category selector and font expansion checks to reflection and typography token checks.

2. **Monotonic Debt Ratchet & Quality Gate Enforcement**:
   - `app/source-reading-baseline.txt`: Removed all 42 refactored test paths, ratcheting legacy debt ceiling down from 181 to 139.
   - `SourceTextReadingAuditContractTest.kt`: Ratcheted ceiling bound to `baseline.size <= 139` and enforced `assertEquals(139, baseline.size)` with zero stale baseline entries (`staleBaselineEntries.isEmpty()`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TST-006.1` / `REQ-PRO-049.1` | `TST-TST-006.2` / `TST-PRO-049.1` | `SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders` | **PASSED** | `Verified` |
| `REQ-TST-006.2` / `REQ-PRO-049.3` | `TST-TST-006.1` / `TST-PRO-049.3` | `SourceTextReadingAuditContractTest.testBaseline_containsOnlyExpectedLegacyCount` (139 entries) | **PASSED** | `Verified` |
| `REQ-TST-006.3` / `REQ-PRO-049.4` | `TST-TST-006.3` / `TST-PRO-049.5` | Targeted `ui.tracking.*` unit test suite (42 target test classes + companion suites) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Test Verification
```text
python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" \
  --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"

BUILD SUCCESSFUL in 1m 18s
All tests PASSED (100% pass rate).
- SourceTextReadingAuditContractTest:
  - testBaseline_containsOnlyExpectedLegacyCount: PASSED (baseline size = 139, ceiling <= 139)
  - testSourceReadingAudit_noUnallowlistedSourceReaders: PASSED (0 unallowlisted readers)
  - testGovernanceGuards_allExplicitlyAllowed: PASSED (22 governance guards)
  - testRatchetingTripwire_detectsUnallowlistedReader: PASSED
- All 42 ui.tracking test suites PASSED
```

### Living Documentation Governance Parity
```text
python3 tools/verify_requirement_governance.py --text-file docs/engineering/walkthroughs/ATT-6565_walkthrough.md
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero Composable islands detected.
PASS: Zero repository encapsulation violations detected.
PASS: Zero architecture or system specification modifications detected.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Unit test refactoring, baseline ratcheting, reflection assertions, and string cache integration. Zero production UI layout or user-facing visual modifications.
* **Visual Consistency (Rule 23)**: 100% UI layout and runtime behavior preserved.

---

## 5. Invariant & Governance Verification

1. **Zero Test Regressions**: All 42 target tests in `ui.tracking` and companion suites pass with 100% success rate.
2. **Localization Parity**: 9-language translation parity across all 9 supported locales strictly preserved using `LocalizationTestCache`.
3. **Monotonic Debt Ratcheting**: Legacy debt ceiling strictly ratcheted from 181 to 139 with 0 stale baseline entries.
4. **Living Documentation Synchronized**: `REQ-PRO-049` and `TST-PRO-049` updated to `Verified` in `req_process_stability.md`, `tst_process_stability.md`, `docs/requirements.md`, and `docs/tests.md`.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), refining `REQ-PRO-034`. Targets `SourceTextReadingAuditContractTest.kt`, `app/source-reading-baseline.txt`, and 42 unit test classes in `com.atrainingtracker.trainingtracker.ui.tracking`.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding C-12 (2026-11-08), ticket ATT-6186 (`c91e62b6`), refined in ATT-6731 (`88ce1d51`), prior package batches in ATT-6569 (`bed1157d`), ATT-6568 (`17300170`), ATT-6567 (`3b5a5b41`), and ATT-6566 (`ef50449a`).
* **Root Reason for Existing Formulation**: Baseline originally captured all tests accessing `src/main` without discriminating single-file UI grep tests from repository-wide architectural scanners, setting an exact count (280) before ratcheting mechanisms were standardized.
* **Preservation of Core Invariants**: Promoting governance guards to `LEGITIMATE_GOVERNANCE_GUARDS` guarantees that essential system invariants are preserved. Enforcing `(baseline - detectedReaders).isEmpty()` prevents stale debt accumulation. Phasing out legacy readers across `ui.tracking` replaces brittle source-grep with behavioral assertions and reflection while preserving all test coverage.
