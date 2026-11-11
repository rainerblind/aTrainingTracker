# Stage 3: Implementation Plan - ATT-6569: Phase out source-text-reading tests in banalservice.ui and migration packages

**Ticket**: [ATT-6569](https://rainerblind.atlassian.net/browse/ATT-6569)  
**Sub-task**: `[Impl-Plan]` [ATT-6972](https://rainerblind.atlassian.net/browse/ATT-6972)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-049` (`REQ-PRO-049.1`, `REQ-PRO-049.3`, `REQ-PRO-049.4`)  
**Test Mapping**: `TST-PRO-049` (`TST-PRO-049.1`, `TST-PRO-049.3`, `TST-PRO-049.5`)  
**Branch**: `feature/ATT-6569`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Under ASPICE finding C-12 (SWE.4 BP) and `docs/coding_standards.md` §5.4, unit tests must not inspect production source code (`File.readText()`) to assert on tokens or formatting. 24 legacy tests across `banalservice.ui` (14 tests) and `migration` (10 tests) currently perform raw text reading on `src/main`. These 24 tests must be converted to behavioral, reflection, and `LocalizationTestCache`-backed assertions, ratcheting `app/source-reading-baseline.txt` down from 275 to 251 entries.

---

## 2. Traceability & Requirements Mapping

* **REQ-PRO-049.1**: Eliminate all `readText()`, `readLines()`, and `src/main` reads across the 24 target tests in `banalservice.ui` and `migration`.
* **REQ-PRO-049.3**: Ratchet legacy baseline ceiling down to `<= 251` and enforce `assertEquals(251, baseline.size)` with zero stale entries (`staleBaselineEntries.isEmpty()`).
* **REQ-PRO-049.4**: Track remaining 251 baseline debt entries cleanly.
* **Test Mapping**: `TST-PRO-049.1` (no unallow-listed readers), `TST-PRO-049.3` (baseline ratchet), `TST-PRO-049.5` (targeted regression suite pass).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Test Regressions**: All unit tests pass with 100% success rate.
2. **Localization Parity**: 9-language resource parity across all 9 locales maintained using approved `LocalizationTestCache`.
3. **Monotonic Debt Ratcheting**: Baseline strictly decreases from 275 to 251.
4. **Code Lock Until Gate 3**: Zero modifications to production source code before Gate 3 PASS.

---

## 4. Proposed Architectural Changes

### Component 1: `banalservice.ui` Test Refactoring (14 Tests)
* Localization tests (`DeviceDiscoveryLocalizationTest`, `PairingLocalizationTest`, `AntStatusLocalizationTest`): Replace `File("src/main/res/...").readText()` with `LocalizationTestCache.getStrings(locale)` key-value map lookups and format specifier checks.
* Composable / dialog contracts (`DeviceStatusRowContractTest`, `DeviceTypeSelectionDialogTest`, `SwapOrUpgradeDialogContractTest`, `AntServicesStatusContractTest`, `AntStartupCheckContractTest`, `DeviceListScreenContractTest`, `DevicesTabbedScreenContractTest`, `DevicesTabbedScreenPromptContractTest`, `EditSportTypeDialogContractTest`, `SportTypesTabsScreenContractTest`): Replace raw text grep with Kotlin/Java reflection on compiled `*Kt` classes (`Class.forName`, `declaredMethods`, parameter signatures) and direct state assertions.
* Permission contract (`DevicesTabbedPermissionContractTest`): Remove source reads of `DevicesTabbedScreen.kt` and `AndroidManifest.xml`; strengthen programmatic permission flow and state-machine simulation tests.

### Component 2: `migration` Test Refactoring (10 Tests)
* Localization tests (`ImportFormatFilePickerLocalizationTest`, `ImportFormatLocalizationTest`, `ImportNavigationLocalizationTest`, and string methods in `ImportFormatCardsContractTest`, `RestoreCardContractTest`): Migrate to `LocalizationTestCache`.
* Unified Cloud Backup (`BackupCloudProviderUnificationTest`): Extract stateless resolution functions `resolveCloudButtonTextRes` and `resolveAutomatedBackupSummaryRes` in `BackupTabContent.kt`, testing all 4 provider combinations behaviorally. Use `LocalizationTestCache` for string checks.
* Invariants & Workers (`BackupManagerTest`, `BackupWorkerTest`, `ImportBackupMapResilienceTest`, `ImportFormatCardsContractTest`, `ImportFormatFilePickerContractTest`, `RestoreCardContractTest`): Replace source-grep (`StandardCopyOption.ATOMIC_MOVE`, `ExistingPeriodicWorkPolicy.UPDATE`) with behavioral tests, reflection, and state evaluations.

### Component 3: Baseline & Audit Contract Test
* `app/source-reading-baseline.txt`: Remove the 24 refactored test paths, establishing new baseline of 251 entries.
* `SourceTextReadingAuditContractTest.kt`: Update ratchet bound to `baseline.size <= 251` and `assertEquals(251, baseline.size)`.

### UI Consistency (Rule 23)
* No user-facing UI changes; internal helper extractions in `BackupTabContent.kt` preserve existing UI layout and behavior 100%.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Check Gate 3 Approval
* Command: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK>`

### Step 2: Refactor `banalservice.ui` Tests (14 Files)
* Remove `src/main` reads and `readText()` calls; integrate `LocalizationTestCache` and reflection.

### Step 3: Refactor `migration` Tests & Helper Extraction (10 Files + `BackupTabContent.kt`)
* Extract stateless string resolver functions in `BackupTabContent.kt` if needed; migrate tests to behavioral assertions and `LocalizationTestCache`.

### Step 4: Ratchet Down Baseline Debt
* Files: `app/source-reading-baseline.txt`
* Remove 24 refactored test lines (leaving exactly 251 entries).

### Step 5: Update Audit Contract Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
* Update ceiling assertion to `<= 251` and expected size to `251`.

### Step 6: Targeted Unit Test Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest*"`
* Expected Result: 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests, followed by full `./gradlew testDebugUnitTest` in Stage 5. Run `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch `feature/ATT-6569` is completely isolated; changes can be rolled back via git reset.
