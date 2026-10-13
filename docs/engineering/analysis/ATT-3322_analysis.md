# Stage 1 Analysis: ATT-3322 - Make navigation hints and fork info opaque and remove transparency setting

**Ticket**: [ATT-3322](https://rainerblind.atlassian.net/browse/ATT-3322)  
**Sub-task**: [ATT-3350](https://rainerblind.atlassian.net/browse/ATT-3350) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*[Epic] Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Branch**: `feature/ATT-3322`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Statement & Motivation

During active route navigation, navigation hint banners (`TurnPromptBanner.kt`), return navigation HUD (`ReturnNavigationHud.kt`), and fork decision prompts (`ForkDecisionCard.kt`) are composed at the top of the tracking screen as top-level spatial overlays. Currently, these composables are rendered with configurable semi-transparency via `overlayAlpha = tuningConfig.navigationCueTransparency` (defaulting to 80% opacity, adjustable in Advanced Settings between 20% and 100%).

In real-world athletic conditions—especially outdoor cycling and running under direct sunlight, through dappled tree shadows, or against high-contrast vector map terrain—semi-transparent overlays allow underlying map roads, topographic contour lines, and colored route polylines to bleed through the banner background. This background bleed-through severely degrades text contrast, compromises directional arrow glanceability, and introduces visual noise at the precise moments athletes need rapid, unambiguous guidance.

Furthermore, physical on-device evaluation across sprints revealed that athletes virtually never benefit from reducing overlay opacity below 100%. Maintaining a dedicated slider for cue transparency in **Advanced Settings -> Navigation** clutters the UI with an unnecessary parameter that provides negligible practical utility while actively encouraging suboptimal contrast configurations.

Making all navigation overlays permanently solid (100% opaque) maximizes contrast, ensures crisp legibility across all ambient lighting conditions, aligns with Material 3 elevated card container paradigms, and simplifies the Advanced Settings menu by eliminating the obsolete transparency slider.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap)

1. **Current Overlay Rendering Pipeline**:
   - `SensorGridScreen.kt`:
     - Lines 609–628 explicitly pass `overlayAlpha = tuningConfig.navigationCueTransparency` to `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`.
   - `TurnPromptBanner.kt`:
     - In `TurnCueCard`, applies `MaterialTheme.colorScheme.surfaceContainer.copy(alpha = overlayAlpha)` (or `primaryContainer.copy(alpha = overlayAlpha)` when `isTurnNow`).
     - In `OffRouteCard`, applies `MaterialTheme.colorScheme.errorContainer.copy(alpha = overlayAlpha)`.
     - Defaults parameter `overlayAlpha = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY` (0.80f).
   - `ReturnNavigationHud.kt`:
     - In `ReturnNavigationHud`, applies `MaterialTheme.colorScheme.secondaryContainer.copy(alpha = overlayAlpha)`.
     - Defaults parameter `overlayAlpha = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY` (0.80f).
   - `ForkDecisionCard.kt`:
     - In `ForkDecisionCard` Surface and `ForkDirectionGroup` Surface, applies `MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`.
     - Defaults parameter `overlayAlpha = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY` (0.80f).

2. **Advanced Settings UI & State Binding**:
   - `NavigationSection.kt`:
     - Exposes a `TuningSliderItem` for `tuning_nav_cue_transparency_title` (20% to 100%, 15 steps, 5% increments).
     - Accepts `navigationCueTransparency: Float` and callback `onTransparencyChange: (Float) -> Unit`.
   - `AdvancedTuningDialog.kt`:
     - Declares `var navigationCueTransparency by remember { mutableFloatStateOf(...) }`.
     - Binds `navigationCueTransparency` to `persistedConfig.navigationCueTransparency` in `applyConfig(config)`.
     - Passes `navigationCueTransparency` into `TuningSubtitleFormatter.formatNavigationSubtitle(...)`.
     - Passes `navigationCueTransparency` into `newConfig = TuningConfig(...)` on save.
   - `AdvancedTuningAccordion.kt`:
     - `TuningSubtitleFormatter.formatNavigationSubtitle` incorporates `$transPercent%` as the first token of the Navigation accordion summary.

3. **Preferences DataStore Architecture**:
   - `TuningPreferencesDefaults.kt`:
     - Defines `DEFAULT_NAVIGATION_CUE_TRANSPARENCY = 0.80f`, `MIN_NAVIGATION_CUE_TRANSPARENCY = 0.20f`, `MAX_NAVIGATION_CUE_TRANSPARENCY = 1.00f`, `STEP_NAVIGATION_CUE_TRANSPARENCY = 0.05f`.
   - `TuningPreferencesDataStore.kt`:
     - Defines `KEY_NAVIGATION_CUE_TRANSPARENCY = floatPreferencesKey("tuning_navigation_cue_transparency")`.
     - Includes `KEY_NAVIGATION_CUE_TRANSPARENCY` in `ALL_KEYS`.
     - Maps `navigationCueTransparency` in `TuningConfig`.
     - Exposes `suspend fun updateNavigationCueTransparency(transparency: Float)`.
   - `TuningConfig.kt`:
     - Holds `val navigationCueTransparency: Float = TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY`.

4. **Integration Tests & Design Guidelines**:
   - `SensorGridScreenRouteIntegrationTest.kt`:
     - Contract tests explicitly assert that `content.contains("overlayAlpha = tuningConfig.navigationCueTransparency")` for `ForkDecisionCard`, `ReturnNavigationHud`, and `TurnPromptBanner`.
   - `docs/design_guidelines.md` § 5.7:
     - Explicitly mandates semi-transparency (`~80–85% opacity`) and an Expert Settings customization slider (`50%–100%`). This section must be updated to mandate solid opaque presentation.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Solid Overlay Presentation (`TurnPromptBanner.kt`, `ReturnNavigationHud.kt`, `ForkDecisionCard.kt`)**:
    - Update container backgrounds in `TurnCueCard`, `OffRouteCard`, `ReturnNavigationHud`, `ForkDecisionCard`, and `ForkDirectionGroup` to render solid (100% opaque) Material 3 container colors without alpha transparency reduction.
    - Set default parameter `overlayAlpha: Float = 1.0f` (or remove alpha multipliers) to guarantee 100% opacity.
  * **Overlay Invocations in `SensorGridScreen.kt`**:
    - Update `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` in `SensorGridScreen.kt` to omit `overlayAlpha` or pass `1.0f`.
  * **Remove Transparency Slider from Advanced Settings UI**:
    - Remove the `TuningSliderItem` for navigation cue transparency from `NavigationSection.kt`.
    - Remove `navigationCueTransparency` and `onTransparencyChange` parameters from `NavigationSection`.
    - Remove `navigationCueTransparency` local state from `AdvancedTuningDialog.kt`.
    - Update `TuningSubtitleFormatter.formatNavigationSubtitle` in `AdvancedTuningAccordion.kt` to omit transparency percentage, formatting dismissal duration and route radius cleanly.
  * **Preferences & DataStore Harmonization**:
    - Deprecate or remove `KEY_NAVIGATION_CUE_TRANSPARENCY` and `updateNavigationCueTransparency()` in `TuningPreferencesDataStore.kt`.
    - In `TuningConfig`, default `navigationCueTransparency: Float = 1.0f` (deprecated) to preserve ABI compatibility with any existing callers without breaking named arguments.
    - Remove obsolete string resources (`tuning_nav_cue_transparency_title`, `tuning_nav_cue_transparency_desc`) across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  * **Design Guidelines & Documentation Harmonization**:
    - Update `docs/design_guidelines.md` § 5.7 to document solid opaque presentation for all in-ride navigation cues and HUD overlays.
    - Synchronize living documentation in `docs/requirements.md` (`REQ-UI-287`, `REQ-UI-321`, `REQ-UI-324`) and `docs/tests.md` (`TST-UI-247`, `TST-UI-281`, `TST-UI-284`).
  * **Test Suite Verification**:
    - Update contract tests in `SensorGridScreenRouteIntegrationTest.kt` to assert solid opacity and absence of transparency wiring.
    - Update `TuningConfigTest.kt` to assert `navigationCueTransparency == 1.0f`.
    - Execute clean-room full test suite `./gradlew testDebugUnitTest` with 100% pass rate.
    - Verify on physical test hardware (Pixel 10).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to navigation cue auto-dismiss countdown durations (`navigationCueDismissDurationSec`).
  * No changes to turn cue countdown distance slider (`turnCueCountdownDistanceMeters` added in ATT-3321).
  * No modification to turn maneuver synthesis or audio chime management.
  * No database schema migrations or changes to `Routes.db`.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-287` (*Navigation Cue Overlay Transparency, Dismiss Duration, Countdown Distance & Expert Settings Configuration*), targeting `TurnPromptBanner.kt`, `SensorGridScreen.kt`, `ReturnNavigationHud.kt`, `ForkDecisionCard.kt`, `NavigationSection.kt`, `AdvancedTuningDialog.kt`, and `docs/design_guidelines.md` Section 5.7.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2632`, commit `8e6bcfc5`), and integrated into top-level spatial overlays in Sprint 2026-41.3 (`ATT-2874`, commit `00687108` / `REQ-UI-321`; `ATT-2938`, commit `479c3d9a` / `REQ-MAP-039`; `ATT-2941`, commit `68b48574` / `REQ-UI-324`).
* **Root Reason for Existing Formulation**: In ATT-2632, semi-transparency (default 80%) was introduced with the intention of allowing underlying telemetry and map contours to remain faintly discernable behind HUD prompts. However, extensive physical on-device field testing in varied real-world lighting conditions (direct sunlight, tree shadows, high-contrast map vector roads) proved that semi-transparent container surfaces severely reduce text contrast and legibility, creating cognitive load and visual clutter while riding at speed. Athletes do not benefit from faint background bleed-through behind high-priority turn cues.
* **Preservation of Core Invariants**: Auto-dismiss duration options (`[2, 3, 4, 5, 8, 0]`), turn countdown distance tuning (`[30m .. 300m]`, default 100m), non-displacing top-level spatial overlay architecture, strict `state.showNavigationHints` per-tab gating, audio cues, and 100% full-suite unit test pass rate remain strictly preserved.
