# Stage 2: Requirement & Test Specification - ATT-3322: Make navigation hints and fork info opaque and remove transparency setting

**Ticket**: [ATT-3322](https://rainerblind.atlassian.net/browse/ATT-3322)  
**Sub-task**: [ATT-3351](https://rainerblind.atlassian.net/browse/ATT-3351) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*[Epic] Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-287` (amending Clauses 1, 2, 3, 4), `REQ-UI-321` (amending Clause 3), `REQ-UI-324` (amending Clause 3)  
**Test Mapping**: `TST-UI-247` (amending Clauses 1, 2, 3), `TST-UI-281` (amending Clause 2), `TST-UI-284` (amending Clause 1)  
**Branch**: `feature/ATT-3322`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Detailed Requirements & Architectural Constraints

### 1.1 Formal Specification (REQ-UI-287 Amendment & Overlay Solidification)

The system SHALL render all in-ride navigation cue banners, return navigation HUDs, and fork decision cards with permanently solid (100% opaque) container surfaces, and SHALL remove the navigation cue transparency slider from Advanced Settings (ATT-3322, amending `REQ-UI-287`, `REQ-UI-321`, `REQ-UI-324`, and `docs/design_guidelines.md` § 5.7):

1. **Permanently Solid Overlay Presentation (`TurnPromptBanner.kt`, `ReturnNavigationHud.kt`, `ForkDecisionCard.kt`, `SensorGridScreen.kt`)**:
   * `TurnPromptBanner.kt`:
     * Container surfaces for `TurnCueCard` (approaching: `MaterialTheme.colorScheme.surfaceContainer`, immediate: `MaterialTheme.colorScheme.primaryContainer`) and `OffRouteCard` (`MaterialTheme.colorScheme.errorContainer`) SHALL render solid without opacity degradation (`overlayAlpha` defaults to `1.0f`).
     * Border accent for `TurnCueCard` SHALL apply solid Royal Blue `TTColor.RouteActiveNavigation.copy(alpha = 0.5f)` per `docs/design_guidelines.md` Section 5.4.
   * `ReturnNavigationHud.kt`:
     * Container surface SHALL render solid `MaterialTheme.colorScheme.secondaryContainer` (`overlayAlpha` defaults to `1.0f`).
   * `ForkDecisionCard.kt`:
     * Container surfaces for `ForkDecisionCard` Surface and `ForkDirectionGroup` Surface SHALL render solid `MaterialTheme.colorScheme.surface` (`overlayAlpha` defaults to `1.0f`).
   * `SensorGridScreen.kt`:
     * Overlay composable invocations for `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` SHALL NOT pass a reduced alpha multiplier and SHALL render at 100% opacity.

2. **Removal of Transparency Slider from Advanced Settings UI (`NavigationSection.kt` & `AdvancedTuningDialog.kt`)**:
   * In `NavigationSection.kt`:
     * The `TuningSliderItem` for "Navigation Cue Transparency" (*Transparenz der Navigationshinweise*) SHALL be completely removed.
     * Parameters `navigationCueTransparency: Float` and `onTransparencyChange: (Float) -> Unit` SHALL be removed.
   * In `AdvancedTuningDialog.kt`:
     * The remember state `navigationCueTransparency` SHALL be removed.
     * `applyConfig(config)` and `onSave` SHALL no longer track or persist user adjustments for `navigationCueTransparency`.
   * In `AdvancedTuningAccordion.kt`:
     * `TuningSubtitleFormatter.formatNavigationSubtitle` SHALL format the Navigation accordion summary strictly incorporating dismiss duration and route selection radius (e.g. `"4s, 1.0 km"`), omitting the transparency percentage token.

3. **Preferences DataStore Architecture (`TuningPreferencesDataStore.kt`)**:
   * `TuningConfig`:
     * `navigationCueTransparency` SHALL default to `1.0f` and be marked `@Deprecated("Overlays are permanently solid (1.0f)")` to preserve binary and source compatibility for existing callers.
   * `TuningPreferencesDataStore`:
     * `KEY_NAVIGATION_CUE_TRANSPARENCY` and `updateNavigationCueTransparency()` SHALL be deprecated / cleaned up.
     * DataStore initialization and reset SHALL ensure `navigationCueTransparency` evaluates to `1.0f`.

4. **Design Guidelines Harmonization (`docs/design_guidelines.md` § 5.7)**:
   * Section 5.7 SHALL be updated to mandate solid (100% opaque) surfaces for all in-ride HUD prompts, replacing the obsolete requirement for 80% semi-transparency and the expert settings transparency slider.

5. **9-Language Localization Parity**:
   * Obsolete string resources `tuning_nav_cue_transparency_title` and `tuning_nav_cue_transparency_desc` SHALL be cleanly removed from all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 orphaned entries.

6. **Preservation of System Invariants**:
   * Auto-dismiss duration options (`[2, 3, 4, 5, 8, 0]`) and turn cue countdown distance slider (`[30m .. 300m]`, default 100m) MUST remain 100% operational.
   * Top-level spatial overlay architecture, non-displacement of telemetry tiles, and strict per-tab `state.showNavigationHints` gating MUST be strictly preserved.
   * 100% clean-room test pass rate across all unit and integration test suites.

---

### 1.2 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Solid Opaque Overlays in Cockpit)**:
  * *Given* active route navigation on `SensorGridScreen`,
  * *When* `TurnPromptBanner`, `ReturnNavigationHud`, or `ForkDecisionCard` appears,
  * *Then* the card container renders 100% opaque, with zero map polyline or underlying text bleeding through the card background.

* **Criterion 2 (Removed Transparency Setting in Advanced Settings)**:
  * *Given* an athlete navigating to **Einstellungen -> Experten-Einstellungen -> Navigation**,
  * *When* viewing the navigation settings accordion,
  * *Then* the "Navigation Cue Transparency" / "Transparenz der Navigationshinweise" slider is no longer present,
  * *And* the Navigation accordion subtitle displays dismissal duration and radius without transparency percentage (e.g. `"4s, 1.0 km"`).

* **Criterion 3 (Uncompromised Dismissal & Countdown Control)**:
  * *Given* the athlete in **Experten-Einstellungen -> Navigation**,
  * *When* adjusting auto-dismiss duration chips (`2s`, `3s`, `4s`, `5s`, `8s`, `Persistent`) or the cue distance slider (`30 m` - `300 m`),
  * *Then* all preferences persist and function identically.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-287` (*Navigation Cue Overlay Transparency, Dismiss Duration & Expert Settings Configuration*), amending Clauses 1, 2, 3, 4, and `REQ-UI-321` Clause 3, `REQ-UI-324` Clause 3.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2632`, commit `8e6bcfc5`), and integrated into top-level spatial overlays in Sprint 2026-41.3 (`ATT-2874`, commit `00687108` / `REQ-UI-321`; `ATT-2938`, commit `479c3d9a` / `REQ-MAP-039`; `ATT-2941`, commit `68b48574` / `REQ-UI-324`).
* **Root Reason for Existing Formulation**: In ATT-2632, semi-transparency (default 80%) was introduced with the intention of allowing underlying telemetry and map contours to remain faintly discernable behind HUD prompts. However, extensive physical on-device field testing in varied real-world lighting conditions (direct sunlight, tree shadows, high-contrast map vector roads) proved that semi-transparent container surfaces severely reduce text contrast and legibility, creating cognitive load and visual clutter while riding at speed. Athletes do not benefit from faint background bleed-through behind high-priority turn cues.
* **Preservation of Core Invariants**: Auto-dismiss duration options (`[2, 3, 4, 5, 8, 0]`), turn countdown distance tuning (`[30m .. 300m]`, default 100m), non-displacing top-level spatial overlay architecture, strict `state.showNavigationHints` per-tab gating, audio cues, and 100% full-suite unit test pass rate remain strictly preserved.

---

## 2. Concrete Test Specifications & Verification Procedures

### 2.1 UI & Integration Tests (`SensorGridScreenRouteIntegrationTest.kt`)

* **TST-UI-281.2 & TST-UI-281.3**: Assert `ForkDecisionCard` renders at `Alignment.TopCenter` and applies solid opacity (no transparency wiring).
* **TST-MAP-041.4 & TST-MAP-041.5**: Assert `ReturnNavigationHud` renders at `Alignment.TopCenter`, is gated by `showNavigationHints`, and renders solid.
* **TST-UI-284.1**: Assert `TurnPromptBanner` renders at `Alignment.TopCenter`, is gated by `showNavigationHints`, consumes `dismissDurationSec`, and renders solid.

### 2.2 Preference & DataStore Unit Tests (`TuningConfigTest.kt` & `TuningPreferencesDataStoreTest.kt`)

* **TST-UI-247.1**: Verify `TuningConfig().navigationCueTransparency == 1.0f`.
* **TST-UI-247.2**: Verify `TuningSubtitleFormatter.formatNavigationSubtitle` formats dismiss duration and route radius cleanly without transparency percentage.

### 2.3 9-Language Localization Parity

* **TST-UI-247.3**: Verify absence of obsolete `tuning_nav_cue_transparency_title` and `tuning_nav_cue_transparency_desc` across all 9 supported locales.

### 2.4 Traceability Matrix

| Requirement ID | Test Identifier | Verification Level | Target Components | Expected Result |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-287.1` | `TST-UI-247.1` | Unit Test | `TuningConfig.kt` | `navigationCueTransparency` defaults to `1.0f` |
| `REQ-UI-287.2` | `TST-UI-247.2` | Unit Test | `NavigationSection.kt`, `AdvancedTuningAccordion.kt` | Transparency slider removed, subtitle formatted cleanly |
| `REQ-UI-287.3` | `TST-UI-284.1` | Integration / Layout | `SensorGridScreen.kt`, `TurnPromptBanner.kt` | Turn cues render completely solid (100% opaque) |
| `REQ-UI-321.3` | `TST-UI-281.3` | Integration / Layout | `ForkDecisionCard.kt`, `SensorGridScreen.kt` | Fork decision card renders completely solid |
| `REQ-UI-324.3` | `TST-UI-284.2` | Integration / Layout | `TurnPromptBanner.kt` | Card containers render solid Material 3 surfaces |
| `REQ-UI-287.5` | `TST-UI-247.3` | Localization Audit | `strings.xml` (all 9 locales) | 100% clean string parity |
