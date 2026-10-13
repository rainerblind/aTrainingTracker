# [Bug] [Navigation] Fix distorted vector icons for slight right and slight left turn navigation cues

**Ticket**: [ATT-3455](https://rainerblind.atlassian.net/browse/ATT-3455)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Status**: Backlog  

---

## Problem Statement & Value Proposition
During active turn-by-turn route navigation testing, visual prompts for "Halb rechts" (`TurnDirection.SLIGHT_RIGHT`) and "Halb links" (`TurnDirection.SLIGHT_LEFT`) display distorted, visually incorrect symbols:
1. In `ic_turn_slight_right.xml` and `ic_turn_slight_left.xml`, the vector path data represents an irregular ribbon/S-curve that does not anchor to the bottom stem (`y=19` or `y=20`), unlike the canonical turn icons (`ic_turn_straight`, `ic_turn_left`, `ic_turn_right`, `ic_turn_sharp_right`).
2. The stem in `ic_turn_slight_right` starts midway across the canvas (near `x=2, y=14`) and bends awkwardly, rather than rising vertically from the bottom center (`x=9..11, y=20`) and gently veering 45° toward the upper-right (1:30 position).
3. The arrowheads are malformed and lack visual symmetry with the Material Design directions iconography standard.
4. Developers and designers currently lack a unified visual overview or gallery to inspect all turn navigation symbols together at a glance.

## User Story
* **As an**: athlete and developer inspecting route navigation cues
* **I want**: the "Halb rechts" and "Halb links" navigation icons to display clear, balanced, standard 45° directional turn arrows, and a dedicated `@Preview` gallery composable showing all 8 turn icons side-by-side
* **So that**: upcoming slight turns are immediately recognizable on the bike or while running, and all navigation symbols can be visually verified instantaneously in Android Studio without manual device routing.

## Scope & Technical Architecture
1. **Vector Drawable Replacement**:
   * Replace `app/src/main/res/drawable/ic_turn_slight_right.xml` with canonical Material `turn_slight_right` vector path:
     * Vertical stem rising from `y=20` with standard 2dp stroke width.
     * Smooth 45° transition curve to upper-right.
     * Standard symmetric arrowhead matching `ic_turn_right` / `ic_turn_straight` geometry.
   * Replace `app/src/main/res/drawable/ic_turn_slight_left.xml` with symmetrical mirrored `turn_slight_left` vector path.
2. **Interactive `@Preview` Gallery Composable**:
   * Create an interactive Jetpack Compose `@Preview` (e.g. `TurnCueIconsGalleryPreview`) in `TurnCue.kt` or `TurnByTurnBanner.kt`.
   * Display all 8 `TurnDirection` enum variants in a balanced grid/row with localized names (`STRAIGHT`, `SLIGHT_RIGHT`, `RIGHT`, `SHARP_RIGHT`, `U_TURN`, `SHARP_LEFT`, `LEFT`, `SLIGHT_LEFT`).
   * Include both Dark Theme and Light Theme preview configurations for instant side-by-side contrast validation.
3. **Visual & Contract Consistency**:
   * Preserve `#FF1976D2` primary fill color token and 24x24 viewport.
   * Ensure flawless rendering across Turn-by-Turn HUD cards, AMOLED battery saver wake-up banners, and route preview cue sheets.
4. **Automated Unit & Contract Testing**:
   * Verify vector XML structure, non-empty paths, and bounding boxes in route navigation test suites.

## Acceptance Criteria (Given-When-Then)
* **Criterion 1: Slight Right Icon Geometry**:
  * *Given* an upcoming navigation cue with `TurnDirection.SLIGHT_RIGHT`
  * *When* rendering `ic_turn_slight_right.xml` in the navigation banner
  * *Then* the icon displays a clean vertical base rising from the bottom and smoothly veering 45° to the upper-right with a symmetric arrowhead.
* **Criterion 2: Slight Left Icon Geometry**:
  * *Given* an upcoming navigation cue with `TurnDirection.SLIGHT_LEFT`
  * *When* rendering `ic_turn_slight_left.xml`
  * *Then* the icon displays the identical, cleanly mirrored 45° veer to the upper-left.
* **Criterion 3: Comprehensive Compose `@Preview` Gallery**:
  * *Given* a developer viewing Compose previews in Android Studio
  * *When* rendering `TurnCueIconsGalleryPreview`
  * *Then* all 8 turn icons are displayed side by side in a structured grid with their titles in both Light and Dark themes for immediate visual verification.
* **Criterion 4: Visual Harmony Across All Cues**:
  * *Given* all 8 `TurnDirection` drawables rendered in the preview gallery
  * *When* inspecting the icon set
  * *Then* stem width (2dp), baseline position (`y=19..20`), and arrowhead proportions are visually harmonious across all directions.

## Parent Epic & Sprint Placement
* **Target Epic**: `ATT-2564` (Navigation: Turn-by-Turn Guidance & Cockpit Prompts)
* **Backlog Placement**: Product Backlog (do NOT assign to active sprint)
