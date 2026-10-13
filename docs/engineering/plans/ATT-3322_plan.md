# Stage 3: Implementation Plan - ATT-3322: Make navigation hints and fork info opaque and remove transparency setting

**Ticket**: [ATT-3322](https://rainerblind.atlassian.net/browse/ATT-3322)  
**Sub-task**: [ATT-3352](https://rainerblind.atlassian.net/browse/ATT-3352) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*[Epic] Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-287` (amending Clauses 1, 2, 3, 4), `REQ-UI-321` (amending Clause 3), `REQ-UI-324` (amending Clause 3)  
**Test Mapping**: `TST-UI-247` (amending Clauses 1, 2, 3), `TST-UI-281` (amending Clause 2), `TST-UI-284` (amending Clause 1)  
**Branch**: `feature/ATT-3322`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Description & Background

In-ride navigation prompts (`TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`) currently support user-configurable semi-transparency via `overlayAlpha = tuningConfig.navigationCueTransparency` (defaulting to 80% opacity), tuned by a dedicated slider in **Einstellungen -> Experten-Einstellungen -> Navigation**.

In real-world athletic conditions—especially outdoor cycling and running under direct sunlight, overcast glare, and against high-contrast vector map terrain (road lines, topographic contours, dark route polylines)—semi-transparent container backgrounds allow underlying map features to bleed through the banner text and directional turn icons. This compromises contrast, creates visual clutter, and degrades glanceability at speed.

Furthermore, offering a dedicated slider for cue transparency in Advanced Settings adds unnecessary configuration friction without offering practical benefit, as athletes universally need solid, high-contrast readability when approaching turns. Making all navigation overlays permanently solid (100% opaque) maximizes contrast, ensures crisp legibility across all ambient lighting conditions, and simplifies the Advanced Settings menu by excising the obsolete transparency slider.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-287` (*Navigation Cue Auto-Dismiss Duration, Countdown Distance & Expert Settings Configuration (Solid Opaque Overlays)*, amending Clauses 1, 2, 3, 4; `REQ-UI-321` Clause 3; `REQ-UI-324` Clause 3; `docs/design_guidelines.md` § 5.7)
* **Test Mapping**: `TST-UI-247` (*Navigation Cue Auto-Dismiss Duration, Countdown Distance & Solid Opaque Overlays Verification*, amending Clauses 1, 2, 3; `TST-UI-281` Clause 2; `TST-UI-284` Clause 1)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly with 100% pass rate.
2. **Overlay Anchoring & Gating**: Navigation prompts continue to float as non-displacing top-level overlays anchored at `Alignment.TopCenter` within `SensorGridScreen`, conditionally gated strictly behind `state.showNavigationHints`.
3. **Auto-Dismiss & Countdown Tuning**: Auto-dismiss duration selector chips (`[2, 3, 4, 5, 8, 0]` where 0 = Persistent) and turn cue countdown distance slider (`[30m .. 300m]`, default 100m) remain 100% functional and persisted in DataStore.
4. **DataStore Single Source of Truth**: DataStore persistence patterns and backward-compatible default fallback remain unbroken.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TuningPreferencesDataStore.kt` & `TuningPreferencesDefaults`
* Update `DEFAULT_NAVIGATION_CUE_TRANSPARENCY` constant to `1.0f`.
* Mark `DEFAULT_NAVIGATION_CUE_TRANSPARENCY`, `KEY_NAVIGATION_CUE_TRANSPARENCY`, `TuningConfig.navigationCueTransparency`, and `updateNavigationCueTransparency()` with `@Deprecated("Navigation cue overlays are permanently solid (1.0f) per REQ-UI-287 / ATT-3322")` to maintain binary and source compatibility for any external or historical callers.
* Update DataStore preference reading and `resetToDefaults()` to guarantee `navigationCueTransparency` resolves to `1.0f`.

### Component 2: `SensorGridScreen.kt` & Overlay Composables (`TurnPromptBanner.kt`, `ReturnNavigationHud.kt`, `ForkDecisionCard.kt`)
* In `SensorGridScreen.kt`:
  * Remove `overlayAlpha = tuningConfig.navigationCueTransparency` arguments from `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` invocations.
* In `TurnPromptBanner.kt`:
  * Default `overlayAlpha: Float = 1.0f`.
  * Ensure `TurnCueCard`, `OffRouteCard`, and card border strokes render with solid 100% opaque surface container colors (`surfaceContainer`, `primaryContainer`, `errorContainer`) without alpha degradation. Border accent for `TurnCueCard` applies Royal Blue `TTColor.RouteActiveNavigation.copy(alpha = 0.5f)` per § 5.4.
* In `ReturnNavigationHud.kt`:
  * Default `overlayAlpha: Float = 1.0f`, rendering solid `MaterialTheme.colorScheme.secondaryContainer`.
* In `ForkDecisionCard.kt`:
  * Default `overlayAlpha: Float = 1.0f`, rendering solid `MaterialTheme.colorScheme.surface` for `ForkDecisionCard` and `ForkDirectionGroup`.

### Component 3: Navigation Tuning Settings UI (`NavigationSection.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`)
* In `NavigationSection.kt`:
  * Remove the `TuningSliderItem` for navigation cue transparency.
  * Remove parameters `navigationCueTransparency: Float` and `onTransparencyChange: (Float) -> Unit`.
* In `AdvancedTuningDialog.kt`:
  * Remove the local remember state `navigationCueTransparency`.
  * Remove `navigationCueTransparency` from `applyConfig`, `onSave`, and the `NavigationSection` call.
* In `AdvancedTuningAccordion.kt`:
  * Update `TuningSubtitleFormatter.formatNavigationSubtitle` to format the summary strictly from dismiss duration and route selection radius (e.g. `"$dismissStr, $radiusStr"`), omitting the obsolete transparency percentage token.

### Component 4: 9-Language Resource Cleanup (`strings.xml`)
* Remove `tuning_nav_cue_transparency_title` and `tuning_nav_cue_transparency_desc` from all 9 supported locales:
  * `values/strings.xml` (EN)
  * `values-de/strings.xml` (DE)
  * `values-es/strings.xml` (ES)
  * `values-fr/strings.xml` (FR)
  * `values-it/strings.xml` (IT)
  * `values-ja/strings.xml` (JA)
  * `values-nl/strings.xml` (NL)
  * `values-pl/strings.xml` (PL)
  * `values-pt/strings.xml` (PT)

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: Existing `NavigationSection.kt` and `Experten-Einstellungen` accordion cards in `AdvancedTuningDialog.kt`.
* **Reused components**: `TuningSliderItem`, `TuningToggleItem`, `AppChipSelector`.
* **Theme tokens**: Surfaces (`MaterialTheme.colorScheme.surfaceContainer`, `primaryContainer`, `secondaryContainer`, `surface`), borders (`TTColor.RouteActiveNavigation.copy(alpha = 0.5f)`, `MaterialTheme.colorScheme.outlineVariant`), typography (`MaterialTheme.typography.titleMedium`, `bodyMedium`).
* **New one-off styles & justification**: None. Overlays strictly use standard M3 container colors at solid 1.0f opacity.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: DataStore and Default Constant Updates
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* Changes:
  * Update `DEFAULT_NAVIGATION_CUE_TRANSPARENCY = 1.00f`.
  * Deprecate `KEY_NAVIGATION_CUE_TRANSPARENCY` and mutator.
  * Guarantee `navigationCueTransparency` resolves to `1.0f`.

### Step 2: Overlay Solidification in Cockpit & HUD Composables
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ReturnNavigationHud.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ForkDecisionCard.kt`
* Changes:
  * Remove `overlayAlpha = tuningConfig.navigationCueTransparency` from `SensorGridScreen.kt`.
  * Set default `overlayAlpha: Float = 1.0f` on overlay composables.
  * Ensure card containers render 100% solid surfaces.

### Step 3: Remove Transparency Slider from Navigation Tuning UI & Subtitle
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`
* Changes:
  * Excise transparency slider from `NavigationSection.kt`.
  * Remove `navigationCueTransparency` parameter passing and mutable state in `AdvancedTuningDialog.kt`.
  * Update `formatNavigationSubtitle` in `AdvancedTuningAccordion.kt` to omit transparency percentage.

### Step 4: Excising Obsolete String Resources across 9 Locales
* Files: `app/src/main/res/values*/strings.xml` (EN, DE, ES, FR, IT, JA, NL, PL, PT)
* Changes:
  * Remove `tuning_nav_cue_transparency_title` and `tuning_nav_cue_transparency_desc` from all 9 files.

### Step 5: Unit Test Suite Updates & Verification
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningConfigTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenRouteIntegrationTest.kt`
* Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenRouteIntegrationTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run targeted unit tests in Stage 4 to verify DataStore defaults, UI layout contracts, and localization parity.
  - Run full clean-room unit test suite `./gradlew testDebugUnitTest` in Stage 5.
  - Deploy debug APK to Pixel 10 test hardware (`66020DLCR002FL`), navigate to **Einstellungen -> Experten-Einstellungen -> Navigation**, and verify absence of the transparency slider and solid rendering.
* **Rollback**: Branch isolation on `feature/ATT-3322` allows full revert via `git reset --hard HEAD` without affecting `sprint/2026-42.1` or `develop`.
