# Stage 3: Implementation Plan - ATT-6568: Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.routes

**Ticket**: [ATT-6568](https://rainerblind.atlassian.net/browse/ATT-6568)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`)  
**Test Mapping**: `TST-PRO-049` (`TST-PRO-049.1`, `TST-PRO-049.3`, `TST-PRO-049.5`)  
**Branch**: `feature/ATT-6568`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Under ASPICE finding C-12 (SWE.4 BP) and `docs/coding_standards.md` §5.4, unit tests must not inspect production source code (`File.readText()`) to assert on tokens, formatting, or private parameters. 15 legacy tests in `com.atrainingtracker.trainingtracker.ui.routes` currently read `src/main` files, resulting in brittle tests that verify zero runtime behavior and trigger false positives during formatting refactors. These 15 tests must be refactored into behavioral, reflection-based, and `LocalizationTestCache`-backed assertions, ratcheting `app/source-reading-baseline.txt` down from 251 to 236 entries.

---

## 2. Traceability & Requirements Mapping

* **REQ-PRO-049.1**: Eliminate all `readText()`, `readLines()`, and `src/main` reads across the 15 target tests in `com.atrainingtracker.trainingtracker.ui.routes`.
* **REQ-PRO-049.3**: Ratchet legacy baseline ceiling down to `<= 236` and enforce `assertEquals(236, baseline.size)` with zero stale entries (`staleBaselineEntries.isEmpty()`).
* **REQ-PRO-049.4**: Track remaining 236 baseline debt entries in living documentation.
* **Test Mapping**: `TST-PRO-049.1` (no unallow-listed readers), `TST-PRO-049.3` (baseline ratchet), `TST-PRO-049.5` (targeted regression suite pass).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Test Regressions**: All 15 refactored test classes and the audit contract test pass with 100% success rate.
2. **Localization Parity**: 9-language translation parity across all 9 application locales strictly preserved using approved `LocalizationTestCache`.
3. **Monotonic Debt Ratcheting**: Baseline strictly decrements from 251 to 236 without introducing regressions.
4. **Code Lock Until Gate 3**: Zero modifications to production source code before Gate 3 PASS verification.

---

## 4. Proposed Architectural Changes

### Component 1: `ui.routes` Test Refactoring (15 Tests)
* **Localization Checks** (`BatchImportSportTypeDialogTest`, `ForkDecisionCardTest`, `RouteItemClickStandardizationTest`, `RoutesScreenContractTest`):
  * Replace direct `strings.xml` XML parsing with `LocalizationTestCache.getStrings(locale)` key-value map lookups across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **Composable & Parameter Contract Tests** (`BatchImportSportTypeDialogTest`, `ForkDecisionCardTest`, `RouteClimbsBreakdownContractTest`, `RouteDetailSheetContractTest`, `RouteDetailsContractTest`, `RouteItemClickStandardizationTest`, `RouteItemWaypointContractTest`, `RouteItemZeroElevationContractTest`, `RouteOnMapScreenClimbContractTest`, `RouteOverlayLayersContractTest`, `RouteSegmentsBreakdownContractTest`, `RouteSelectorMidRideContractTest`, `RouteSelectorSheetTest`, `RoutesScreenContractTest`):
  * Replace raw source grep with Java/Kotlin reflection on compiled `*Kt` classes (`Class.forName`, `declaredMethods`, `parameterTypes`, modifiers) to verify method presence, parameter signatures, and annotations.
  * Verify absence of obsolete classes (`RouteDetailMetricsCard`, `RouteDetailMapCard`, `RouteDetailElevationProfileCard`) via reflection (`runCatching { Class.forName(...) }.isFailure`).
* **Behavioral & Algorithmic Assertions** (`RouteClimbSegmentLayeringContractTest`, `RouteDetailSheetContractTest`, `RouteItemZeroElevationContractTest`, `RouteOverlayLayersContractTest`, `RoutesScreenContractTest`):
  * Verify `ClimbHighlightData` default `zIndex` and `width` via direct object instantiation.
  * Preserve and maintain pure unit tests (`calculateRouteBounds`, `hasValidElevationData`, `ForkDecisionState`, `MapVisualization` z-index hierarchy, `RouteOverlayLayer` enums, background path segment filtering logic).
  * Verify `ClimbDetector` grade formatting by running `detectClimbs` directly on test coordinates.

### Component 2: Baseline & Audit Contract Test
* `app/source-reading-baseline.txt`: Remove all 15 refactored test paths, establishing new ratcheted baseline of 236 entries.
* `SourceTextReadingAuditContractTest.kt`: Update ratchet bound to `baseline.size <= 236` and `assertEquals(236, baseline.size)`.

### UI Consistency (Rule 23)
* No UI changes. Pure test refactoring, reflection adaptation, and baseline ratcheting.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Check Gate 3 Approval
* Command: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK>`

### Step 2: Refactor `ui.routes` Tests (15 Files)
* Replace all `src/main` reads and `readText()` calls with `LocalizationTestCache`, reflection on `*Kt` classes, and direct behavioral assertions.

### Step 3: Ratchet Down Baseline Debt
* File: `app/source-reading-baseline.txt`
* Remove the 15 refactored test lines (leaving exactly 236 entries).

### Step 4: Update Audit Contract Test
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
* Update ceiling assertion to `<= 236` and expected size to `236`.

### Step 5: Targeted Unit Test Verification
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"`
* Expected Result: 100% pass rate across all tests.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests, followed by full `./gradlew testDebugUnitTest` in Stage 5. Verify living doc synchronization with `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch `feature/ATT-6568` is isolated; changes can be rolled back via git reset.
