# Stage 3: Implementation Plan - ATT-6728: Ratchet lint-baseline contract bound to <=239 and restore Compose resource Lint rules to error

**Ticket**: [ATT-6728](https://rainerblind.atlassian.net/browse/ATT-6728)  
**Sub-task**: [ATT-6947](https://rainerblind.atlassian.net/browse/ATT-6947) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-046` (`REQ-PRO-046.4`, `REQ-PRO-046.5`, `REQ-PRO-046.6`)  
**Test Mapping**: `TST-PRO-046` (`TST-PRO-046.3`, `TST-PRO-046.4`, `TST-PRO-046.5`, `TST-PRO-046.6`, `TST-PRO-046.7`)  
**Branch**: `feature/ATT-6728`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
In ATT-6173, `app/lint-baseline.xml` was reduced to 238 issues, but the contract test ceiling remained at `<= 546`, permitting regressions and artificially prohibiting a 0-issue baseline. Simultaneously, `app/build.gradle` temporarily downgraded `NonObservableLocale` and `LocalContextGetResourceValueCall` to `warning`. This plan executes the ratcheting of the contract bound to `<= 239` (allowing burn-down to 0), refactors identified Composable call sites to Compose-safe resource and locale lookups, removes the warning overrides in `app/build.gradle` restoring error enforcement, and verifies `SafePainterResource.kt` sentinel guarding under clean CI lint and unit test gating.

---

## 2. Traceability & Requirements Mapping
* **REQ-PRO-046.4** (*Lint Baseline Ratchet & Zero-Safe Bound*): Ratchet ceiling in `ReleaseCiAndLintConfigContractTest.testLintBaseline_issueCountWithinBound` to `issueCount in 0..239` (`TST-PRO-046.3`).
* **REQ-PRO-046.5** (*Automated CI & Lint Governance Contract*): Expand `ReleaseCiAndLintConfigContractTest.kt` with `testBuildGradle_composeLintRulesNotDowngraded` asserting absence of warning overrides (`TST-PRO-046.5`).
* **REQ-PRO-046.6** (*Compose Resource & Locale Lint Enforcement*): Remove `warning 'NonObservableLocale', 'LocalContextGetResourceValueCall'` from `app/build.gradle`, migrate Composable call sites, preserve `SafePainterResource.kt` `id <= 0` guard (`TST-PRO-046.6`), and pass `./gradlew :app:lintDebug` with 0 errors (`TST-PRO-046.7`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Regressions**: 100% existing unit tests pass cleanly without behavioral changes.
2. **Preserve Justified Suppressions**: Maintain `DuplicatePlatformClasses`, `MissingTranslation` (guarded by `TranslationParityTest`), and `Range` suppressions in `app/build.gradle`.
3. **Strict Code Lock**: No production code modifications before Gate 3 review PASS.
4. **Clean Merge & Human Gate**: 1 squashed commit onto `sprint/2026-46.2` under `/tmp/att_build_merge.lock`; parent ticket ends in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Build Governance Contract (`ReleaseCiAndLintConfigContractTest.kt`)
* Ratchet `testLintBaseline_issueCountWithinBound()` assertion from `issueCount in 1..546` to `issueCount in 0..239`.
* Add `testBuildGradle_composeLintRulesNotDowngraded()` asserting `app/build.gradle` does not contain `NonObservableLocale` or `LocalContextGetResourceValueCall` warning overrides.

### Component 2: Build Script (`app/build.gradle`)
* Delete lines 111–113 (`warning 'NonObservableLocale', 'LocalContextGetResourceValueCall'`).

### Component 3: Compose Resource & Locale Resolution Refactoring
* `PeriodsScreen.kt`: Migrate `context.resources.getQuantityString` to `pluralStringResource`, `context.getString` to `stringResource`, and `Locale.getDefault()` to `LocalConfiguration.current.locales[0]`.
* `ElevationProfile.kt`: Migrate `context.getString(labelRes)` to `stringResource(labelRes)` and `Locale.getDefault()` in composable scope to `LocalConfiguration.current.locales[0]`.
* `SupporterDebugSection.kt`: Migrate `context.getString` to `stringResource`.
* `YearlyContributionGrid.kt`: Migrate `Locale.getDefault()` in Composable layouts to `LocalConfiguration.current.locales[0]`.
* `SafePainterResource.kt`: Maintain `id <= 0` guard and `@SuppressLint("ResourceType")`.

### UI Consistency (Rule 23)
* No visual UI changes. This is an internal static analysis and resource resolution refactoring preserving identical visual output.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Ratchet Baseline Ceiling and Contract Test
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`
* Change: Ratchet `in 1..546` to `in 0..239`; add `testBuildGradle_composeLintRulesNotDowngraded()`.
* Verification: `./gradlew testDebugUnitTest --tests "*ReleaseCiAndLintConfigContractTest*"`

### Step 2: Remove Compose Lint Downgrades in Build Script
* File: `app/build.gradle`
* Change: Remove `warning 'NonObservableLocale', 'LocalContextGetResourceValueCall'`.

### Step 3: Refactor Composable Call Sites
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/YearlyContributionGrid.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SupporterDebugSection.kt`
* Changes: Replace `context.getString` with `stringResource`, `context.resources.getQuantityString` with `pluralStringResource`, `Locale.getDefault()` with `LocalConfiguration.current.locales[0]`.

### Step 4: Verify SafePainterResource Sentinel Guard
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/SafePainterResourceContractTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "*SafePainterResourceContractTest*"`

### Step 5: Android Lint & Contract Suite Verification
* Command:
  - `python3 tools/parallel_sprint_lock.py ./gradlew :app:lintDebug`
  - `./gradlew testDebugUnitTest --tests "*ReleaseCiAndLintConfigContractTest*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "*ReleaseCiAndLintConfigContractTest*" --tests "*SafePainterResourceContractTest*"` and `./gradlew :app:lintDebug` with 0 failures/errors.
* **Rollback**: Isolated branch `feature/ATT-6728`; reset via `git checkout -- .` and `git clean -fd`.
