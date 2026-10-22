# Stage 5: Walkthrough & Verification - ATT-4031: Split god classes and remove dead code

**Ticket**: [ATT-4031](https://rainerblind.atlassian.net/browse/ATT-4031)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4031`  
**Requirement Mapping**: `REQ-ARC-003` (*God Class Splitting, Dead Code Elimination, Thread-Safe Visibility & API Modernization*)  
**Test Mapping**: `TST-ARC-003` (*Dead Code Elimination, Volatile Visibility & Lock-Screen API Modernization Verification Suite*)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Executive Summary & Verification Overview

Whole-app code review identified structural debt, uncalled dead methods, unreferenced legacy dialog/view classes, non-volatile crash recovery state, and deprecated window lock flags.

Stage 4 Construction & Stage 5 Verification completed all remediations:
1. **TrainingApplication Cleanup & Volatility**: Excised dead methods `todo()`, `setIsSegmentListUpdating()`, `isSegmentListUpdating()`, and unused state `mSegmentListUpdating`. Declared `cResumeFromCrash` as `volatile` for cross-thread memory visibility.
2. **Dead Legacy Class Elimination & Lint Baseline Purge**: Physically deleted `SelectSportTypeDialog.java`, `MultiSelectionSpinner.java`, `DeviceIdAndNameArrayAdapter.java`, and `InstallANTShitDialog.java`. Purged unreferenced `ClickableViewAccessibility` issue from `app/lint-baseline.xml`.
3. **MainActivityWithNavigation Modernization & Contract Alignment**: Excised uncalled `checkBatteryOptimizations()` and `showInstallANTShitDialog()`. Modernized lock-screen flags in `applyDisplaySettings()` to API 27+ `setShowWhenLocked(true/false)` with legacy fallback. Retargeted `AntStartupCheckContractTest.kt` inspection boundary to `observeNavigationEvents`.
4. **Compose UI Preview Visibility Scoping**: Scoped all top-level `@Preview` composables across UI screens and components to `private` visibility.
5. **Contract Suite**: Created `DeadCodeEliminationContractTest.kt` verifying reflective and physical excision, volatile modifiers, API modernization, and private preview scoping (5/5 tests passing).
6. **Physical Deployment Exemption**: Pure backend refactor, dead code excision, and preview scoping with zero visual layout changes or schema modifications; ADB deployment exempted per Rule 60.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-ARC-003.1` (Dead Method & State Excision) | `TST-ARC-003.2`, `TST-ARC-003.3` | `DeadCodeEliminationContractTest.kt` | **PASSED** | `Verified` |
| `REQ-ARC-003.2` (Dead Legacy Class Removal) | `TST-ARC-003.1` | `DeadCodeEliminationContractTest.kt` | **PASSED** | `Verified` |
| `REQ-ARC-003.3` (Volatile State Visibility) | `TST-ARC-003.2` | `DeadCodeEliminationContractTest.kt` | **PASSED** | `Verified` |
| `REQ-ARC-003.4` (Lock Screen API Modernization) | `TST-ARC-003.3` | `DeadCodeEliminationContractTest.kt` | **PASSED** | `Verified` |
| `REQ-ARC-003.5` (Private Preview Scoping) | `TST-ARC-003.5` | `DeadCodeEliminationContractTest.kt` | **PASSED** | `Verified` |
| `REQ-ARC-003.6` (Contract Verification) | `TST-ARC-003.4`, `TST-ARC-003.6` | `AntStartupCheckContractTest.kt`, Targeted Suites | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Contract Verification Suite
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest" \
                            --tests "com.atrainingtracker.banalservice.ui.devices.ant.AntStartupCheckContractTest"
# Result: BUILD SUCCESSFUL (6 tests, 0 failures)
```

### Adjacent Regressions
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingSensorHeaderContractTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldStyleContractTest" \
                            --tests "com.atrainingtracker.trainingtracker.TrainingApplicationTrackingTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.navigation.SingleActivityNavigationTest"
# Result: BUILD SUCCESSFUL (0 failures)
```

### Requirement Governance
```bash
python3 tools/verify_requirement_governance.py
# Result: Net-new requirement(s) detected: REQ-ARC-003. Bypassing archaeology check cleanly.
```
