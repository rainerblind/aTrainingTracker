# Stage 4: Implementation Deliverable - ATT-3325: Replace persistent option with 10s option for navigation cue dismiss duration

**Ticket**: [ATT-3325](https://rainerblind.atlassian.net/browse/ATT-3325)  
**Sub-task**: [ATT-3370](https://rainerblind.atlassian.net/browse/ATT-3370) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-287` (amending Clauses 1, 2, 3)  
**Test Mapping**: `TST-UI-247` (amending Clauses 1, 2, 3)  
**Branch**: `feature/ATT-3325`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Summary of Changes

In accordance with the approved Stage 3 Plan ([ATT-3325_plan.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/plans/ATT-3325_plan.md)), this implementation replaces the legacy `0` ("Dauerhaft") dismiss duration option with a discrete `10`s option across preferences, tuning UI, subtitle formatting, and localization resources:

1. **Preference Model & Options Definition (`TuningPreferencesDataStore.kt`)**:
   - Updated `TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS` from `listOf(2, 3, 4, 5, 8, 0)` to `listOf(2, 3, 4, 5, 8, 10)`.
   - Existing DataStore validation logic seamlessly coerces legacy `0` (or any unrecognized integer) to `DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC` (4).
2. **Navigation Settings Tuning UI (`NavigationSection.kt`)**:
   - Simplified the header value display to unconditionally format `"${navigationCueDismissDurationSec}s"`.
   - Simplified the `FlowRow` chips to render `Text("${sec}s")` for every option in `NAVIGATION_CUE_DISMISS_OPTIONS` (`2s`, `3s`, `4s`, `5s`, `8s`, `10s`).
3. **Subtitle Formatter (`AdvancedTuningAccordion.kt`)**:
   - Updated `TuningSubtitleFormatter.formatNavigationSubtitle` to evaluate `effectiveDismiss` against `NAVIGATION_CUE_DISMISS_OPTIONS`, falling back to `DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC` (4), and formatting `"${effectiveDismiss}s, ${radiusStr}"` without context string resource lookups.
4. **9-Language String Resource Cleanup (`res/values-*/strings.xml`)**:
   - Safely excised obsolete `tuning_nav_cue_dismiss_persistent` across all 9 supported locales:
     - `values/strings.xml`
     - `values-de/strings.xml`
     - `values-es/strings.xml`
     - `values-fr/strings.xml`
     - `values-it/strings.xml`
     - `values-ja/strings.xml`
     - `values-nl/strings.xml`
     - `values-pl/strings.xml`
     - `values-pt/strings.xml`
5. **Unit Tests & Regression Coverage**:
   - Updated `TuningConfigTest.kt` to assert `NAVIGATION_CUE_DISMISS_OPTIONS == listOf(2, 3, 4, 5, 8, 10)`.
   - Updated `AdvancedTuningAccordionTest.kt` to assert `10s` subtitle formatting and legacy `0` fallback to `4s`.
   - Created `TuningPreferencesDataStoreDismissDurationTest.kt` verifying options list, default configuration, 10s custom retention, legacy `0` coercion, unknown integer coercion, and full option validation.
   - Verified 100% 9-language parity with `TranslationParityTest`.

---

## 2. Targeted Unit Test Results

```
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 13s
32 actionable tasks: 2 executed, 30 up-to-date

> Task :app:testDebugUnitTest --tests "*TranslationParityTest*"
BUILD SUCCESSFUL in 8s
32 actionable tasks: 1 executed, 31 up-to-date
```

* All targeted unit tests passed with 0 errors.
* 100% 9-language translation parity verified.
