# Stage 3: Implementation Plan - ATT-6566: Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.aftermath

**Ticket**: [ATT-6566](https://rainerblind.atlassian.net/browse/ATT-6566)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`)  
**Test Mapping**: `TST-PRO-049` (`TST-PRO-049.1`, `TST-PRO-049.3`, `TST-PRO-049.5`)  
**Branch**: `feature/ATT-6566`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Under ASPICE finding C-12 (SWE.4 BP) and `docs/coding_standards.md` §5.4, unit tests must not inspect production source code (`File.readText()`) to assert on tokens, formatting, or private parameters. 30 legacy tests across `com.atrainingtracker.trainingtracker.ui.aftermath` (10 in root, 1 in `crop`, 2 in `editworkout`, 2 in `header`, 1 in `periodlist`, 2 in `photos`, 1 in `splits`, 6 in `workoutlist`, 5 in `zones`) currently read `src/main` files, resulting in brittle tests that verify zero runtime behavior and trigger false positives during formatting refactors. These 30 tests must be refactored into behavioral, reflection-based, and `LocalizationTestCache`-backed assertions, ratcheting `app/source-reading-baseline.txt` down from 211 to 181 entries.

---

## 2. Traceability & Requirements Mapping

* **REQ-PRO-049.1**: Eliminate all `readText()`, `readLines()`, and `src/main` reads across the 30 target tests in `com.atrainingtracker.trainingtracker.ui.aftermath`.
* **REQ-PRO-049.3**: Ratchet legacy baseline ceiling down to `<= 181` and enforce `assertEquals(181, baseline.size)` with zero stale entries (`staleBaselineEntries.isEmpty()`).
* **REQ-PRO-049.4**: Track remaining 181 baseline debt entries in living documentation.
* **Test Mapping**: `TST-PRO-049.1` (no unallow-listed readers), `TST-PRO-049.3` (baseline ratchet), `TST-PRO-049.5` (targeted regression suite pass).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Test Regressions**: All 30 refactored test classes and the audit contract test pass with 100% success rate.
2. **Localization Parity**: 9-language translation parity across all 9 application locales strictly preserved using approved `LocalizationTestCache`.
3. **Monotonic Debt Ratcheting**: Baseline strictly decrements from 211 to 181 without introducing regressions.
4. **Code Lock Until Gate 3**: Zero modifications to production source code before Gate 3 PASS verification.

---

## 4. Proposed Architectural Changes

### Component 1: `ui.aftermath` Test Refactoring (30 Tests)
* **Root `aftermath` (10 Tests)**:
  * `TrackOnMapScreen*` (4 tests): Inspect `TrackOnMapScreenKt` via JVM reflection to verify routing parameters, slotting composables, and detail preference integration without source grep.
  * `WorkoutDetail*` (3 tests): Inspect `WorkoutDetailTabbedContentKt` and `WorkoutDetailTabsKt` via reflection and assert tab enums/callbacks behaviorally.
  * `WorkoutPhotoUiContractTest`: Inspect photo UI composables via reflection.
  * `WorkoutRepositoryCropTest` & `WorkoutRepositoryDeleteTest`: Replace source text grep on repository files with direct unit test behavior assertions.
* **Subpackages `crop`, `editworkout`, `header`, `periodlist`, `photos`, `splits` (9 Tests)**:
  * `CropWorkoutScreenContractTest`, `EditWorkoutFieldsLayoutTest`, `EditWorkoutLayoutContractTest`: Inspect editing and crop composables via reflection.
  * `WorkoutHeaderRaceBadgeVisualContractTest`, `WorkoutHeaderSourceBadgeVisualContractTest`: Inspect badge composables via reflection.
  * `PeriodMapAdaptiveLayeringTest`: Assert layering constants and reflect on period map composables.
  * `PhotoThumbnailSizeTest`, `WorkoutTimePhotoPickerContractTest`: Assert thumbnail tokens and reflect on photo picker composables.
  * `LapSplitVisualizerTest`: Assert split calculations and reflect on `LapSplitVisualizerKt`.
* **Subpackages `workoutlist` and `zones` (11 Tests)**:
  * `ActiveFilterChipsRowTest`, `WorkoutFilterBottomSheetClusterRemovalTest`, `WorkoutSummariesTabbedScreenTest`, `WorkoutSummaryClickContractTest`, `WorkoutSummaryDynamicOrderContractTest`, `WorkoutSummaryZoneDisplayModeTest`: Inspect filter sheet, tabbed screens, and summary cards via reflection and behavior.
  * `GradeZoneDistributionCardTest`, `TelemetryHistogramCardTest`, `ZoneDistributionCardTest`, `ZoneDistributionStackedBarTest`: Test card data models behaviorally and reflect on zone card composables.
  * `ZoneDistributionStackedBarLocalizationTest`: Migrate to `LocalizationTestCache.getStrings(locale)` across all 9 locales.

### Component 2: Baseline & Audit Contract Test
* `app/source-reading-baseline.txt`: Remove all 30 refactored test paths, establishing new ratcheted baseline of 181 entries.
* `SourceTextReadingAuditContractTest.kt`: Update ratchet bound to `baseline.size <= 181` and `assertEquals(181, baseline.size)`.

### UI Consistency (Rule 23)
* No UI changes. Pure test refactoring, reflection adaptation, and baseline ratcheting.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Check Gate 3 Approval
* Command: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK>`

### Step 2: Refactor Root `aftermath` Tests (10 Files)
* Replace all `src/main` reads and `readText()` calls with `LocalizationTestCache` and reflection on `*Kt` classes.

### Step 3: Refactor `crop`, `editworkout`, `header`, `periodlist`, `photos`, `splits` Tests (9 Files)
* Replace all `src/main` reads and `readText()` calls with reflection and behavioral assertions.

### Step 4: Refactor `workoutlist` and `zones` Tests (11 Files)
* Replace all `src/main` reads and `readText()` calls with `LocalizationTestCache`, reflection, and data model tests.

### Step 5: Ratchet Down Baseline Debt
* File: `app/source-reading-baseline.txt`
* Remove the 30 refactored test lines (leaving exactly 181 entries).

### Step 6: Update Audit Contract Test
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
* Update ceiling assertion to `<= 181` and expected size to `181`.

### Step 7: Targeted Unit Test Verification
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"`
* Expected Result: 100% pass rate across all tests.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests, followed by full `./gradlew testDebugUnitTest` in Stage 5. Verify living doc synchronization with `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch `feature/ATT-6566` is isolated; changes can be rolled back via git reset.
