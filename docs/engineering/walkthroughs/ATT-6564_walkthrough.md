# Stage 5: Walkthrough & Verification - ATT-6564: Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.map

**Ticket**: [ATT-6564](https://rainerblind.atlassian.net/browse/ATT-6564)  
**Sub-task**: [ATT-7235](https://rainerblind.atlassian.net/browse/ATT-7235) (`[Test]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-TST-007` (`REQ-TST-007.1`..`REQ-TST-007.3`), refining `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`) & `REQ-TST-002`  
**Test Mapping**: `TST-TST-007` (`TST-TST-007.1`..`TST-TST-007.3`), validating `TST-PRO-049`  
**Branch**: `feature/ATT-6564`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview

ATT-6564 eliminates source-text reading anti-patterns (`File.readText()`, `readLines()`, and raw `src/main` reads) across all 45 legacy tests in `com.atrainingtracker.trainingtracker.ui.map` per ASPICE finding C-12 and `docs/coding_standards.md` §5.4:

1. **`ui.map` Refactoring (45 Tests)**:
   - **`DarkMap*` (3 Tests)**: Migrated dark map style and anti-flash contracts to reflection on `DarkMapStyleKt` and behavioral style resolution assertions.
   - **`ElevationProfile*` (11 Tests)**: Migrated clearance, climb badge clipping, climb spans, gesture states, grade colors, and smoothing to reflection on `ElevationProfileKt` and coordinate geometry evaluations.
   - **`MapDetailLayout*` (9 Tests)**: Refactored collapsing headers, dynamic viewports, metadata slots, padding, and scrub synchronization to reflection and state model assertions.
   - **`TelemetryMetricGraph*` (8 Tests)**: Migrated metric graphs, marginal bars, and zone tests to reflection and pure calculation assertions.
   - **Map & Sheet Components (14 Tests)**: Refactored favorite location sheet, FollowMe padding/anchors, general layers, polyline stability, preview lite mode, scrubbing badges, waypoint badges, and share bottom sheet to reflection and `LocalizationTestCache`.

2. **Monotonic Debt Ratchet & Quality Gate Enforcement**:
   - `app/source-reading-baseline.txt`: Removed all 45 refactored test paths, ratcheting legacy debt ceiling down from 139 to 94.
   - `SourceTextReadingAuditContractTest.kt`: Ratcheted ceiling bound to `baseline.size <= 94` and enforced `assertEquals(94, baseline.size)` with zero stale baseline entries (`staleBaselineEntries.isEmpty()`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TST-007.1` / `REQ-PRO-049.1` | `TST-TST-007.2` / `TST-PRO-049.1` | `SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders` | **PASSED** | `Verified` |
| `REQ-TST-007.2` / `REQ-PRO-049.3` | `TST-TST-007.1` / `TST-PRO-049.3` | `SourceTextReadingAuditContractTest.testBaseline_containsOnlyExpectedLegacyCount` (94 entries) | **PASSED** | `Verified` |
| `REQ-TST-007.3` / `REQ-PRO-049.4` | `TST-TST-007.3` / `TST-PRO-049.5` | Targeted `ui.map.*` unit test suite (45 target test classes + companion suites) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Test Verification
```text
python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.ui.map.*" \
  --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"

BUILD SUCCESSFUL in 2m 18s
All tests PASSED (100% pass rate).
- SourceTextReadingAuditContractTest:
  - testBaseline_containsOnlyExpectedLegacyCount: PASSED (baseline size = 94, ceiling <= 94)
  - testSourceReadingAudit_noUnallowlistedSourceReaders: PASSED (0 unallowlisted readers)
  - testGovernanceGuards_allExplicitlyAllowed: PASSED (22 governance guards)
  - testRatchetingTripwire_detectsUnallowlistedReader: PASSED
- All 45 ui.map test suites PASSED
```

### Living Documentation Governance Parity
```text
python3 tools/verify_requirement_governance.py --text-file docs/engineering/walkthroughs/ATT-6564_walkthrough.md
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

1. **Zero Test Regressions**: All 45 target tests in `ui.map` and companion suites pass with 100% success rate.
2. **Localization Parity**: 9-language translation parity across all 9 supported locales strictly preserved using `LocalizationTestCache`.
3. **Monotonic Debt Ratcheting**: Legacy debt ceiling strictly ratcheted from 139 to 94 with 0 stale baseline entries.
4. **Living Documentation Synchronized**: `REQ-PRO-049` and `TST-PRO-049` updated to `Verified` in `req_process_stability.md`, `tst_process_stability.md`, `docs/requirements.md`, and `docs/tests.md`.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`), refining `REQ-PRO-034`. Targets `SourceTextReadingAuditContractTest.kt`, `app/source-reading-baseline.txt`, and 45 unit test classes in `com.atrainingtracker.trainingtracker.ui.map`.
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding C-12 (2026-11-08), ticket ATT-6186 (`c91e62b6`), refined in ATT-6731 (`88ce1d51`), prior package batches in ATT-6569 (`bed1157d`), ATT-6568 (`17300170`), ATT-6567 (`3b5a5b41`), ATT-6566 (`ef50449a`), and ATT-6565 (`1298a643`).
* **Root Reason for Existing Formulation**: Baseline originally captured all tests accessing `src/main` without discriminating single-file UI grep tests from repository-wide architectural scanners, setting an exact count (280) before ratcheting mechanisms were standardized.
* **Preservation of Core Invariants**: Promoting governance guards to `LEGITIMATE_GOVERNANCE_GUARDS` guarantees that essential system invariants are preserved. Enforcing `(baseline - detectedReaders).isEmpty()` prevents stale debt accumulation. Phasing out legacy readers across `ui.map` replaces brittle source-grep with behavioral assertions and reflection while preserving all test coverage.
