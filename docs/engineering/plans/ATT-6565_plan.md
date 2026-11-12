# Stage 3: Implementation Plan - ATT-6565: Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.tracking

**Ticket**: [ATT-6565](https://rainerblind.atlassian.net/browse/ATT-6565)  
**Sub-task**: [ATT-7207](https://rainerblind.atlassian.net/browse/ATT-7207) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-TST-006` (`REQ-TST-006.1`..`REQ-TST-006.3`), refining `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`) & `REQ-TST-002`  
**Test Mapping**: `TST-TST-006` (`TST-TST-006.1`..`TST-TST-006.3`), validating `TST-PRO-049`  
**Branch**: `feature/ATT-6565`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Under ASPICE pre-assessment finding C-12 (SWE.4 BP) and `docs/coding_standards.md` §5.4, unit tests must not inspect production source code (`File.readText()`) to assert on tokens, formatting, or private parameters. Exactly 42 legacy tests across `com.atrainingtracker.trainingtracker.ui.tracking` (15 in `controltracking`, 10 in `ui.tracking` root, 9 in `tracking`, 6 in `trackingtabs`, 1 in `editsensorfield`, 1 in `typography`) read `src/main` files. These brittle tests verify zero runtime behavior and fail on harmless formatting changes. They must be refactored into behavioral, reflection-based, and `LocalizationTestCache`-backed assertions, ratcheting `app/source-reading-baseline.txt` down from 181 to 139 entries.

---

## 2. Traceability & Requirements Mapping

* **REQ-TST-006.1 / REQ-PRO-049.1**: Eliminate all `readText()`, `readLines()`, and `src/main` reads across the 42 target tests in `ui.tracking`.
* **REQ-TST-006.2 / REQ-PRO-049.3**: Ratchet legacy baseline ceiling down to `<= 139` and enforce `assertEquals(139, baseline.size)` with `staleBaselineEntries.isEmpty()`.
* **REQ-TST-006.3 / REQ-PRO-049.4**: Maintain ratcheting tripwire and track remaining 139 debt entries in living documentation.
* **Test Mapping**: `TST-TST-006.1` (baseline ratchet), `TST-TST-006.2` (no unallow-listed readers scan), `TST-TST-006.3` (targeted regression suite pass).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Test Regressions**: All 42 refactored test classes and audit contract tests pass with 100% success rate.
2. **Localization Parity**: 9-language translation parity across all 9 application locales strictly preserved using approved `LocalizationTestCache`.
3. **Monotonic Debt Ratcheting**: Baseline strictly decrements from 181 to 139 without introducing stale entries.
4. **Code Lock Until Gate 3**: Zero modifications to production source code before Gate 3 PASS verification (`jira_util.py check-gate`).

---

## 4. Proposed Architectural Changes

### Component 1: `ui.tracking` Test Refactoring (42 Tests)
* **`controltracking` (15 Tests)**: Inspect `ControlTrackingScreenKt`, `PairingButtonsKt`, and dialog composables via reflection; assert sport selector and battery optimizations behaviorally; migrate string checks to `LocalizationTestCache`.
* **Root `ui.tracking` (10 Tests)**: Inspect `SensorFieldViewKt` and `HrvInfoBottomSheetKt` via reflection; assert `BikePowerCockpitDefaults` and zone gauge models behaviorally; migrate tab strings to `LocalizationTestCache`.
* **`tracking` & `trackingtabs` (15 Tests)**: Inspect `SensorGridScreenKt`, `TrackingTabsScreenKt`, and `BikeBellButtonKt` via reflection; assert landscape layouts, map states, and climb suppression behaviorally.
* **`editsensorfield` & `typography` (2 Tests)**: Inspect category selectors and cockpit font models via reflection and typography tokens.

### Component 2: Baseline & Audit Contract Test
* `app/source-reading-baseline.txt`: Remove all 42 refactored test paths, establishing new ratcheted baseline of 139 entries.
* `SourceTextReadingAuditContractTest.kt`: Update ratchet bound to `baseline.size <= 139` and `assertEquals(139, baseline.size)`.
* **UI Consistency (Rule 23)**: No UI changes. Pure test refactoring, reflection adaptation, and debt ratcheting.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Check Gate 3 Approval & Audit Target Tests
* Command: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK>`
* Audit all 42 tests across `com/atrainingtracker/trainingtracker/ui/tracking/` to identify exact `readText()` and `src/main` call sites.

### Step 2: Replace Text-Reading with Behavioral/Semantic Assertions
* Refactor all 42 tests across the 6 subpackages:
  - Migrate XML string assertions to `LocalizationTestCache.getStrings(locale)`.
  - Replace `.kt` source text grep with JVM reflection (`*Kt::class.java.declaredMethods`, parameters).
  - Retain pure math, layout, enum, and state model behavioral assertions.

### Step 3: Ratchet Down Baseline Debt to <= 139
* Update `app/source-reading-baseline.txt`: Remove 42 refactored paths (leaving exactly 139 entries).
* Update `SourceTextReadingAuditContractTest.kt`: Ratchet ceiling to `<= 139` and `assertEquals(139, baseline.size)`.

### Step 4: Verify Test Suite Execution
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"`
* Expected Result: 100% pass rate across all 42 refactored tests and the audit contract test.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests, followed by full `./gradlew testDebugUnitTest` in Stage 5. Verify living docs with `verify_requirement_governance.py`.
* **Rollback**: Branch `feature/ATT-6565` is isolated; rollback via `git checkout -- .`.
