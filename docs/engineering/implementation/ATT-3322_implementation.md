# Stage 4: Implementation Report - ATT-3322: Make navigation hints and fork info opaque and remove transparency setting

**Ticket**: [ATT-3322](https://rainerblind.atlassian.net/browse/ATT-3322)  
**Sub-task**: [ATT-3353](https://rainerblind.atlassian.net/browse/ATT-3353) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-287` (amending Clauses 1, 2, 3, 4), `REQ-UI-321` (amending Clause 3), `REQ-UI-324` (amending Clause 3)  
**Test Mapping**: `TST-UI-247` (amending Clauses 1, 2, 3), `TST-UI-281` (amending Clause 2), `TST-UI-284` (amending Clause 1)  
**Branch**: `feature/ATT-3322`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Summary of Changes

1. **Overlay Solidification & Clean Opacity (`SensorGridScreen.kt`, `TurnPromptBanner.kt`, `ReturnNavigationHud.kt`, `ForkDecisionCard.kt`)**:
   * Removed `overlayAlpha = tuningConfig.navigationCueTransparency` invocations for `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` in `SensorGridScreen.kt`.
   * Enforced permanently solid (100% opaque) surfaces on all navigation overlays, eliminating background map bleed-through under direct sunlight and high-contrast vector terrain.
   * `overlayAlpha` default arguments safely evaluate to `1.0f` (`DEFAULT_NAVIGATION_CUE_TRANSPARENCY`).

2. **Advanced Settings UI Simplification (`NavigationSection.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`)**:
   * Excised the obsolete "Navigation Cue Transparency" (`TuningSliderItem`) slider from `NavigationSection.kt`.
   * Removed `navigationCueTransparency` state and parameter plumbing from `AdvancedTuningDialog.kt`.
   * Updated `TuningSubtitleFormatter.formatNavigationSubtitle` in `AdvancedTuningAccordion.kt` to format the Navigation accordion summary strictly incorporating dismiss duration and route selection radius (e.g. `"4s, 1.0 km"`), omitting the obsolete transparency percentage.

3. **DataStore Invariants & Backward Compatibility (`TuningPreferencesDataStore.kt`)**:
   * Updated `DEFAULT_NAVIGATION_CUE_TRANSPARENCY = 1.00f`.
   * Marked `DEFAULT_NAVIGATION_CUE_TRANSPARENCY`, `KEY_NAVIGATION_CUE_TRANSPARENCY`, `TuningConfig.navigationCueTransparency`, and `updateNavigationCueTransparency()` with `@Deprecated` annotations preserving source/binary compatibility.

4. **9-Language Localization Clean-up (`strings.xml`)**:
   * Removed `tuning_nav_cue_transparency_title` and `tuning_nav_cue_transparency_desc` across all 9 supported locales:
     * `values/strings.xml` (EN)
     * `values-de/strings.xml` (DE)
     * `values-es/strings.xml` (ES)
     * `values-fr/strings.xml` (FR)
     * `values-it/strings.xml` (IT)
     * `values-ja/strings.xml` (JA)
     * `values-nl/strings.xml` (NL)
     * `values-pl/strings.xml` (PL)
     * `values-pt/strings.xml` (PT)

5. **Test Suite Parity & Architectural Contracts**:
   * Updated `TuningConfigTest.kt` to verify `DEFAULT_NAVIGATION_CUE_TRANSPARENCY == 1.00f` and `config.navigationCueTransparency == 1.00f`.
   * Updated `SensorGridScreenRouteIntegrationTest.kt` contract tests to assert solid opacity without passing `overlayAlpha`.
   * Updated `AdvancedTuningAccordionTest.kt` to verify `formatNavigationSubtitle` output.

---

## 2. Verification & Test Results

* Targeted Unit Tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenRouteIntegrationTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest"
  ```
  Result: **BUILD SUCCESSFUL** (100% tests passed).

* 9-Language Localization Audit:
  ```bash
  ./gradlew testDebugUnitTest --tests "*TranslationParityTest*"
  ```
  Result: **BUILD SUCCESSFUL** (100% tests passed across all 9 locales).
