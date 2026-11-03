# Stage 1 Analysis: ATT-5200 - Profile & Zones (Zonendarstellung): rename zone_display_background from "Heller Hintergrund" / "Light background" to "Eingefärbter Hintergrund" / "Tinted background" across all 9 locales

**Ticket**: [ATT-5200](https://rainerblind.atlassian.net/browse/ATT-5200)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5200`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Statement & Motivation

In `ZonesSettingsActivity.kt`, the "Zonendarstellung" section allows configuring 4 visual toggles for training zone metrics (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`).
The toggle for `showBackground` uses string resource `zone_display_background`, currently localized as:
* EN: `"Light background"`
* DE: `"Heller Hintergrund"`
* ES: `"Fondo claro"`
* FR: `"Arrière-plan clair"`
* IT: `"Sfondo chiaro"`
* JA: `"明るい背景"`
* NL: `"Lichte achtergrond"`
* PL: `"Jasne tło"`
* PT: `"Fundo claro"`

This terminology is misleading in Dark Mode and on AMOLED screens. Athletes interpret "Light background" (*Heller Hintergrund*) as forcing a light/white UI surface or daylight theme, rather than tinting the tile with the active zone color at 12% opacity (`zoneColor.copy(alpha = 0.12f)`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * **Code Verification**: In `ZonesSettingsActivity.kt:537`, the row label is bound directly to `R.string.zone_display_background`. Its backing property `displayOptions.showBackground` controls `zoneColor.copy(alpha = 0.12f)` on `SensorFieldView.kt`. When disabled, the tile renders neutral surface (pure black `#000000` in AMOLED dark mode).
  * **Historical Context**: The wording "Light background" was chosen during initial implementation (ATT-1265) to denote "lightly tinted" rather than "darkly filled". However, in standard Material 3 / AMOLED dark theme, "light" is universally read as the opposite of "dark" (i.e. white surface).
  * **Accurate Terminology across Locales**: Renaming to "Tinted background" (*Eingefärbter Hintergrund*) accurately conveys that the container is tinted with the metric's zone color, resolving confusion in both Light and Dark themes.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Update string resource `zone_display_background` across all 9 `strings.xml` resource files:
    * `values` (EN): `"Tinted background"`
    * `values-de` (DE): `"Eingefärbter Hintergrund"`
    * `values-es` (ES): `"Fondo tintado"`
    * `values-fr` (FR): `"Arrière-plan teinté"`
    * `values-it` (IT): `"Sfondo tinto"`
    * `values-ja` (JA): `"色付き背景"`
    * `values-nl` (NL): `"Getinte achtergrond"`
    * `values-pl` (PL): `"Zabarwione tło"`
    * `values-pt` (PT): `"Fundo matizado"`
  * Update living documentation in `docs/requirements/req_ui_design_system.md` (`REQ-UI-172`) and `docs/tests/tst_ui_design_system.md` (`TST-UI-124`).
  * Verify 100% translation parity via `TranslationParityTest` and `ZoneDisplayLocalizationTest`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not rename string key `zone_display_background` itself (preserves API/R-class stability).
  * Do not alter boolean flag name `showBackground` in `ZoneDisplayOptions` or DataStore preferences keys.
  * Do not modify other toggle labels (`zone_display_left_bar`, `zone_display_right_bar`, `zone_display_text_color`).
  * Do not alter tile rendering alpha (`0.12f`) or AMOLED color logic in `SensorFieldView.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-172` (*Configurable Zone Visualization Options and Theme-Aware Live Preview*). Targets `app/src/main/res/values*/strings.xml`, `ZoneDisplayLocalizationTest.kt`, and `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: `ATT-1265` (commit `a48c8a38`, Sprint 2026-40.5).
* **Root Reason for Existing Formulation**: "Light background" originally meant "lightly colored" (low alpha tint), but became semantically ambiguous with the introduction of AMOLED Pure Black and dynamic dark theme cockpits.
* **Preservation of Core Invariants**: Key identifier `R.string.zone_display_background`, default `showBackground = true`, DataStore preference storage, and 9-language translation parity are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Localization Parity Updates (9 Locales)**:
   * Update string value for `name="zone_display_background"` across all 9 `res/values*/strings.xml` files.
2. **Contract and Unit Testing**:
   * Run `ZoneDisplayLocalizationTest` and `TranslationParityTest` to confirm all 9 locales possess non-empty, valid string entries.
3. **Living Documentation Alignment**:
   * Update `REQ-UI-172` and `TST-UI-124` references from "Light background" to "Tinted background" / "Eingefärbter Hintergrund".

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. String key `R.string.zone_display_background` remains unchanged; zero Java/Kotlin/Compose symbol breakages.
  2. DataStore persistence and serialization symmetry remain intact.
  3. 9-language localization parity strictly maintained (0 missing translations).
* **Side-Effect & Cross-Caller Impact Audit (Rule 68)**:
  * Single UI consumption site: `ZonesSettingsActivity.kt:537`.
  * Verified zero hardcoded string assertions in test suite for old text values.
* **Risk Rating**: **LOW** — Pure string resource localization refinement; zero logic, schema, or lifecycle impact.
