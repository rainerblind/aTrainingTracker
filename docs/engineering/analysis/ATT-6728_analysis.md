# Stage 1 Analysis: ATT-6728 - Ratchet Lint-Baseline Contract Bound to <=239 and Restore Compose Resource Lint Rules to Error

**Ticket**: [ATT-6728](https://rainerblind.atlassian.net/browse/ATT-6728)  
**Sub-task**: [ATT-6944](https://rainerblind.atlassian.net/browse/ATT-6944) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6728`  
**Requirement Mapping**: `REQ-PRO-046` (`REQ-PRO-046.4`, `REQ-PRO-046.5`, `REQ-PRO-046.6`) | **Test Mapping**: `TST-PRO-046` (`TST-PRO-046.3`, `TST-PRO-046.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-46.1 review of ticket ATT-6173 (*Run Android Lint in CI and reduce lint baseline*), `app/lint-baseline.xml` was pruned from 607 down to 238 issues. However, two follow-up quality gate hardenings remained open:
1. **Unratcheted Lint Baseline Contract Bound**: `ReleaseCiAndLintConfigContractTest.testLintBaseline_issueCountWithinBound()` still asserts `issueCount in 1..546`, permitting up to +308 baseline regressions without failing CI, and artificially failing if baseline issues ever drop to 0.
2. **Temporary Compose Locale/Resource Lint Downgrade**: In `app/build.gradle`, the lint options downgrade `NonObservableLocale` and `LocalContextGetResourceValueCall` to `warning` with the comment `// Downgraded to warning to prevent CI breakage while legacy Compose screens are incrementally refactored (ATT-6173)`. Without restoring these to errors (default), non-recomposing locale lookups and direct context resource resolutions continue to slip into Composable bodies unnoticed.
3. **Defensive Sentinel Guard**: Verify and preserve `if (id <= 0) null` guard in `SafePainterResource.kt` to ensure negative sentinel resource IDs (`-1`) never throw exceptions on draw.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

### Technical Investigation
* **Contract Ceiling Ratchet**:
  * In `ReleaseCiAndLintConfigContractTest.kt`, line 240 specifies `issueCount in 1..546`.
  * `app/lint-baseline.xml` currently contains exactly 238 `<issue` entries.
  * Ratcheting the upper bound to `in 0..239` prevents regressions while allowing zero remaining baseline entries without test failure.
* **Compose Lint Rules Enforcement**:
  * Removing `warning 'NonObservableLocale', 'LocalContextGetResourceValueCall'` from `app/build.gradle` restores Android Lint's default `error` severity.
  * Forensic scanning of Composable functions identifies call sites where `context.getString(...)`, `context.resources.getQuantityString(...)`, or non-observable `Locale.getDefault()` are invoked directly in Composable bodies instead of `stringResource(...)`, `pluralStringResource(...)`, or `LocalConfiguration.current.locales`:
    * `PeriodsScreen.kt` (lines 93–98, 103, 130–131): Direct `context.resources.getQuantityString`, `context.getString`, and `Locale.getDefault()` within `PeriodsScreen` Composable body.
    * `ElevationProfile.kt` (lines 772, 776, 831): `context.getString` and `Locale.getDefault()` calls within Composable scope.
    * `SupporterDebugSection.kt` (line 233): `context.getString` inside Composable block.
    * `YearlyContributionGrid.kt` (lines 301, 445): Non-observable `Locale.getDefault()` calls in Composable layout.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  * Independently verified `SafePainterResource.kt`: Confirmed that `if (id <= 0) null` with `@android.annotation.SuppressLint("ResourceType")` is already present (committed in ATT-6173 commit `602b4d9e` and tested in `SafePainterResourceContractTest.kt`).
  * Independently verified `ReleaseCiAndLintConfigContractTest.kt`: Line 240 confirms `issueCount in 1..546`. Ratcheting to `in 0..239` will be verified by running `ReleaseCiAndLintConfigContractTest`.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  1. Ratchet `ReleaseCiAndLintConfigContractTest.testLintBaseline_issueCountWithinBound()` from `in 1..546` to `in 0..239`.
  2. Remove `warning 'NonObservableLocale', 'LocalContextGetResourceValueCall'` from `app/build.gradle` to restore default error enforcement.
  3. Migrate direct `context.getString(...)`, `context.resources.getQuantityString(...)`, and `Locale.getDefault()` calls in Composable bodies to Compose-safe `stringResource(...)`, `pluralStringResource(...)`, and `LocalConfiguration.current`.
  4. Ensure `SafePainterResource.kt` maintains `id <= 0` guard and passes `SafePainterResourceContractTest`.
  5. Verify `./gradlew :app:lintDebug` passes with 0 errors and contract tests pass 100%.
  6. Update living documentation (`req_process_stability.md`, `tst_process_stability.md`).
* **Out-of-Scope Non-Goals**:
  * Mass-refactoring non-Composable utility classes or background services that legitimately use `context.getString()`.
  * Modifying unrelated lint suppressions (`DuplicatePlatformClasses`, `MissingTranslation`, `Range`).
* **Complexity Rating**: 2 SP (Standard). Well bounded to lint configuration, contract test ratcheting, and targeted Composable refactorings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-046` (*Android Lint CI Quality Gate & Baseline Burn-down*), targeting `ReleaseCiAndLintConfigContractTest.kt`, `app/build.gradle`, and `app/lint-baseline.xml`.
* **Historical Origin & Commit Trace**: Introduced in sprint `2026-46.1` via ticket ATT-6173 (commit `08a744c1`).
* **Root Reason for Existing Formulation**: In ATT-6173, the ceiling was set to `546` (a 10% reduction from 607) as an interim milestone. The Compose rules were temporarily downgraded to `warning` to avoid blocking CI while legacy screens were refactored.
* **Preservation of Core Invariants**: Existing suppressions (`DuplicatePlatformClasses`, `MissingTranslation` guarded by `TranslationParityTest`, and `Range`) remain intact. Ratcheting to `<= 239` and restoring Compose resource rules to `error` strengthens rather than weakens static analysis gating.

---

## 5. Architectural Strategy & High-Level Solution
* **Contract Ratchet**: Update `ReleaseCiAndLintConfigContractTest.kt` to check `issueCount in 0..239` and assert informative error messages.
* **Build Configuration**: Delete line `warning 'NonObservableLocale', 'LocalContextGetResourceValueCall'` from `app/build.gradle`.
* **Composable Refactorings**:
  * Replace Composable-body `context.getString(resId, ...)` with `stringResource(resId, ...)`.
  * Replace Composable-body `context.resources.getQuantityString(resId, count, ...)` with `pluralStringResource(resId, count, ...)`.
  * Replace Composable-body `Locale.getDefault()` with `LocalConfiguration.current.locales[0]` or Compose-safe locale resolution.
* **Living Documentation**: Update `REQ-PRO-046` and `TST-PRO-046` to reflect ratcheted `<= 239` bound and restored Compose lint error enforcement.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regressions in existing features and unit tests.
  2. `./gradlew :app:lintDebug` passes with 0 errors (`abortOnError = true`).
  3. Single squashed commit onto `sprint/2026-46.2` under `/tmp/att_build_merge.lock`.
  4. Parent ticket ends in `Final Review (Human)` assigned to Rainer Blind.
* **Risk Rating**: **LOW**. Static analysis quality gate hardening and safe Composable resource resolution cleanups.
