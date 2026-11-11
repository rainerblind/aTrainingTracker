# Stage 3: Implementation Plan - ATT-6567: Phase out source-text-reading tests in com.atrainingtracker.trainingtracker.ui.settings

**Ticket**: [ATT-6567](https://rainerblind.atlassian.net/browse/ATT-6567)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`)  
**Test Mapping**: `TST-PRO-049` (`TST-PRO-049.1`, `TST-PRO-049.3`, `TST-PRO-049.5`)  
**Branch**: `feature/ATT-6567`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Under ASPICE finding C-12 (SWE.4 BP) and `docs/coding_standards.md` §5.4, unit tests must not inspect production source code (`File.readText()`) to assert on tokens, formatting, or private parameters. 25 legacy tests across `com.atrainingtracker.trainingtracker.ui.settings` (`autopause`, `cloud`, `display`, `strava`, `supporter`, and `tuning`) currently read `src/main` files, resulting in brittle tests that verify zero runtime behavior and trigger false positives during formatting refactors. These 25 tests must be refactored into behavioral, reflection-based, and `LocalizationTestCache`-backed assertions, ratcheting `app/source-reading-baseline.txt` down from 236 to 211 entries.

---

## 2. Traceability & Requirements Mapping

* **REQ-PRO-049.1**: Eliminate all `readText()`, `readLines()`, and `src/main` reads across the 25 target tests in `com.atrainingtracker.trainingtracker.ui.settings`.
* **REQ-PRO-049.3**: Ratchet legacy baseline ceiling down to `<= 211` and enforce `assertEquals(211, baseline.size)` with zero stale entries (`staleBaselineEntries.isEmpty()`).
* **REQ-PRO-049.4**: Track remaining 211 baseline debt entries in living documentation.
* **Test Mapping**: `TST-PRO-049.1` (no unallow-listed readers), `TST-PRO-049.3` (baseline ratchet), `TST-PRO-049.5` (targeted regression suite pass).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Test Regressions**: All 25 refactored test classes and the audit contract test pass with 100% success rate.
2. **Localization Parity**: 9-language translation parity across all 9 application locales strictly preserved for both `strings.xml` and `strings_monetization.xml` using approved `LocalizationTestCache`.
3. **Monotonic Debt Ratcheting**: Baseline strictly decrements from 236 to 211 without introducing regressions.
4. **Code Lock Until Gate 3**: Zero modifications to production source code before Gate 3 PASS verification.

---

## 4. Proposed Architectural Changes

### Component 1: `ui.settings` Test Refactoring (25 Tests)
* **Subpackages `autopause`, `cloud`, `display`, `strava`, `supporter` (8 Tests)**:
  * `AutoPauseRetirementContractTest`: Inspect `SensorsGpsFilterSectionKt` via JVM reflection to assert auto-pause parameter absence.
  * `AutoPauseTabsScreenContractTest`: Inspect `AutoPauseTabsScreenKt` via reflection and query string resources via `LocalizationTestCache`.
  * `CloudSettingsComponentContractTest`: Inspect `CloudConnectionHeaderKt`, `CloudProviderSettingsContentKt`, and provider dialogs via reflection.
  * `DisplaySettingsCleanupTest`: Inspect `DisplaySettingsDialogKt` via reflection to verify absence of Aftermath model parameters.
  * `DisplaySettingsTest`: Replace string extraction helper with `LocalizationTestCache.getStrings(locale)`.
  * `StravaSettingsDialogContractTest`: Inspect `StravaSettingsDialogKt` via reflection for action bars and `StravaManualSyncCard`.
  * `SupporterCopyContractTest`: Query monetization strings via `LocalizationTestCache.getStrings(locale, "strings_monetization.xml")`.
  * `SupporterPlanCardHierarchyTest`: Migrate monetization XML checks to `LocalizationTestCache` and composable checks to reflection on `SupporterComponentsKt` and `SupporterScreenKt`.
* **Subpackage `tuning` (17 Tests)**:
  * `AdvancedTuningAftermathContractTest`, `AdvancedTuningScreenTest`, `AdvancedTuningVisualContractTest`: Inspect `AdvancedTuningScreenKt` via reflection.
  * `AdvancedTuningElevationSmoothingContractTest`, `AdvancedTuningPaceCeilingContractTest`, `ClusterTuningContractTest`, `PhysiologyModelsVisibilityContractTest`, `PhysiologyTuningSectionContractTest`, `WorkoutMasksAndCardsLayoutTest`, `WorkoutSectionReorderContractTest`: Inspect section composables via reflection on compiled `*Kt` classes and verify string keys with `LocalizationTestCache`.
  * `AdvancedTuningModularityTest`, `CockpitPreviewTileContractTest`, `TuningFormControlsTest`: Verify public composables, parameters, and form controls via reflection.
  * `CockpitTypographyLocalizationTest`, `DisplayAndExpertSettingsLocalizationContractTest`, `ZoneCardDisplayModeLocalizationTest`: Migrate to `LocalizationTestCache.getStrings(locale)` across all 9 locales.
  * `LapDisplayModeSettingsTest`: Test `LapDisplayMode` enum values and inspect composable via reflection.

### Component 2: Baseline & Audit Contract Test
* `app/source-reading-baseline.txt`: Remove all 25 refactored test paths, establishing new ratcheted baseline of 211 entries.
* `SourceTextReadingAuditContractTest.kt`: Update ratchet bound to `baseline.size <= 211` and `assertEquals(211, baseline.size)`.

### UI Consistency (Rule 23)
* No UI changes. Pure test refactoring, reflection adaptation, and baseline ratcheting.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Check Gate 3 Approval
* Command: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK>`

### Step 2: Refactor `autopause`, `cloud`, `display`, `strava`, `supporter` Tests (8 Files)
* Replace all `src/main` reads and `readText()` calls with `LocalizationTestCache` and reflection on `*Kt` classes.

### Step 3: Refactor `tuning` Tests (17 Files)
* Replace all `src/main` reads and `readText()` calls with `LocalizationTestCache`, reflection on category/tuning `*Kt` classes, and behavioral assertions.

### Step 4: Ratchet Down Baseline Debt
* File: `app/source-reading-baseline.txt`
* Remove the 25 refactored test lines (leaving exactly 211 entries).

### Step 5: Update Audit Contract Test
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
* Update ceiling assertion to `<= 211` and expected size to `211`.

### Step 6: Targeted Unit Test Verification
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.*" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"`
* Expected Result: 100% pass rate across all tests.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests, followed by full `./gradlew testDebugUnitTest` in Stage 5. Verify living doc synchronization with `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch `feature/ATT-6567` is isolated; changes can be rolled back via git reset.
