# Stage 3: Implementation Plan - ATT-6564: Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.map

**Ticket**: [ATT-6564](https://rainerblind.atlassian.net/browse/ATT-6564)  
**Sub-task**: [ATT-7223](https://rainerblind.atlassian.net/browse/ATT-7223) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-TST-007` (`REQ-TST-007.1`..`REQ-TST-007.3`), refining `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`) & `REQ-TST-002`  
**Test Mapping**: `TST-TST-007` (`TST-TST-007.1`..`TST-TST-007.3`), validating `TST-PRO-049`  
**Branch**: `feature/ATT-6564`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Under ASPICE pre-assessment finding C-12 (SWE.4 BP) and `docs/coding_standards.md` §5.4, unit tests must not inspect production source code (`File.readText()`) to assert on tokens, formatting, or private parameters. Exactly 45 legacy tests across `com.atrainingtracker.trainingtracker.ui.map` (3 `DarkMap*`, 11 `ElevationProfile*`, 9 `MapDetailLayout*`, 8 `TelemetryMetricGraph*`, and 14 Map & Sheet tests) read `src/main` files. These brittle tests verify zero runtime behavior and fail on harmless formatting changes. They must be refactored into behavioral, reflection-based, and `LocalizationTestCache`-backed assertions, ratcheting `app/source-reading-baseline.txt` down from 139 to 94 entries.

---

## 2. Traceability & Requirements Mapping

* **REQ-TST-007.1 / REQ-PRO-049.1**: Eliminate all `readText()`, `readLines()`, and `src/main` reads across the 45 target tests in `ui.map`.
* **REQ-TST-007.2 / REQ-PRO-049.3**: Ratchet legacy baseline ceiling down to `<= 94` and enforce `assertEquals(94, baseline.size)` with `staleBaselineEntries.isEmpty()`.
* **REQ-TST-007.3 / REQ-PRO-049.4**: Maintain ratcheting tripwire and track remaining 94 debt entries in living documentation.
* **Test Mapping**: `TST-TST-007.1` (baseline ratchet), `TST-TST-007.2` (no unallow-listed readers scan), `TST-TST-007.3` (targeted regression suite pass).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Test Regressions**: All 45 refactored test classes and audit contract tests pass with 100% success rate.
2. **Localization Parity**: 9-language translation parity across all 9 application locales strictly preserved using approved `LocalizationTestCache`.
3. **Monotonic Debt Ratcheting**: Baseline strictly decrements from 139 to 94 without introducing stale entries.
4. **Code Lock Until Gate 3**: Zero modifications to production source code before Gate 3 PASS verification (`jira_util.py check-gate`).

---

## 4. Proposed Architectural Changes

### Component 1: `ui.map` Test Refactoring (45 Tests)
* **`DarkMap*` (3 Tests)**: Inspect `DarkMapStyleKt` via reflection; assert style resolution logic behaviorally.
* **`ElevationProfile*` (11 Tests)**: Inspect `ElevationProfileKt` via reflection; assert geometry, climb spans, grade colors, and smoothing behaviorally.
* **`MapDetailLayout*` (9 Tests)**: Inspect `MapDetailLayoutKt` via reflection; assert collapsing headers, viewports, padding, and scrub sync behaviorally.
* **`TelemetryMetricGraph*` (8 Tests)**: Inspect graph composables via reflection; assert marginal bars, zones, and gestures behaviorally.
* **Map & Sheet Components (14 Tests)**: Inspect sheets, markers, and layers via reflection; migrate string checks to `LocalizationTestCache`.

### Component 2: Baseline & Audit Contract Test
* `app/source-reading-baseline.txt`: Remove all 45 refactored test paths, establishing new ratcheted baseline of 94 entries.
* `SourceTextReadingAuditContractTest.kt`: Update ratchet bound to `baseline.size <= 94` and `assertEquals(94, baseline.size)`.
* **UI Consistency (Rule 23)**: No UI changes. Pure test refactoring, reflection adaptation, and debt ratcheting.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Check Gate 3 Approval & Audit Target Tests
* Command: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK>`
* Audit all 45 tests across `com/atrainingtracker/trainingtracker/ui/map/` to identify exact `readText()` and `src/main` call sites.

### Step 2: Replace Text-Reading with Behavioral/Semantic Assertions
* Refactor all 45 tests across map subcategories:
  - Migrate XML string assertions to `LocalizationTestCache.getStrings(locale)`.
  - Replace `.kt` source text grep with JVM reflection (`*Kt::class.java.declaredMethods`, parameters).
  - Retain pure math, coordinate clearance, elevation geometry, and state model behavioral assertions.

### Step 3: Ratchet Down Baseline Debt to <= 94
* Update `app/source-reading-baseline.txt`: Remove 45 refactored paths (leaving exactly 94 entries).
* Update `SourceTextReadingAuditContractTest.kt`: Ratchet ceiling to `<= 94` and `assertEquals(94, baseline.size)`.

### Step 4: Verify Test Suite Execution
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"`
* Expected Result: 100% pass rate across all 45 refactored tests and the audit contract test.

---

## 6. Verification & Rollback Plan

* **Verification & Rollback**: Execute targeted unit tests and Stage 5 full suite; rollback via `git checkout -- .`.
