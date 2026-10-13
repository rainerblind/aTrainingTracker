# Stage 2: Requirement & Test Specification - ATT-3455: Fix distorted vector icons for slight right and slight left turn navigation cues

**Ticket**: [ATT-3455](https://atrainingtracker.atlassian.net/browse/ATT-3455)  
**Sub-task**: [ATT-3486](https://atrainingtracker.atlassian.net/browse/ATT-3486) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-360` (*Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery*)  
**Test Spec ID**: `TST-UI-323`  
**Branch**: `feature/ATT-3455`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Requirement Specification (REQ-UI-360)

### 1.1 Problem Statement & Rationale
During turn-by-turn route navigation (REQ-MAP-028, REQ-UI-324 / ATT-1450, ATT-2632), visual prompts for "Halb rechts" (`TurnDirection.SLIGHT_RIGHT`) and "Halb links" (`TurnDirection.SLIGHT_LEFT`) display distorted, visually incorrect symbols:
1. In `ic_turn_slight_right.xml` and `ic_turn_slight_left.xml`, the vector path data represents an irregular ribbon/S-curve that does not anchor to the bottom stem baseline ($y=19..20$), unlike the canonical turn icons (`ic_turn_straight`, `ic_turn_left`, `ic_turn_right`, `ic_turn_sharp_right`).
2. The stem in `ic_turn_slight_right` starts midway across the canvas (near $x=2, y=14$) and bends awkwardly, rather than rising vertically from the bottom center ($x=9..11, y=20$) and gently veering 45° toward the upper-right (1:30 position).
3. The arrowheads are malformed and lack visual symmetry with the Material Design directions iconography standard.
4. Developers and designers currently lack a unified visual overview or gallery to inspect all turn navigation symbols together at a glance.

### 1.2 Functional & Architectural Requirements
1. *Canonical Vector Path Replacement (`ic_turn_slight_right.xml`, `ic_turn_slight_left.xml`)*:
   * In `ic_turn_slight_right.xml`, the vector path SHALL rise vertically from baseline $y=20$ with standard 2dp stroke width ($x \in [9, 11]$), transition through a smooth 45° curve to the upper right via control point $(9.59, 11.01)$, and conclude with a symmetric 45° arrowhead with 2dp wing thickness:
     `M12.34,6V4H18v5.66h-2V7.41l-5,5V20h-2v-7.58c0,-0.53 0.21,-1.04 0.59,-1.41l5,-5H12.34z`
   * In `ic_turn_slight_left.xml`, the vector path SHALL provide an exact horizontal mirror across the vertical centerline ($x' = 24 - x$):
     `M11.66,6V4H6v5.66h2V7.41l5,5V20h2v-7.58c0,-0.53 -0.21,-1.04 -0.59,-1.41l-5,-5H11.66z`
   * Both assets SHALL preserve standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`, `width="24dp"`, `height="24dp"`) and primary tint fill color token `#FF1976D2`.
2. *Interactive Jetpack Compose `@Preview` Gallery (`TurnPromptBanner.kt`)*:
   * The system SHALL provide `TurnCueIconsGallery` and multi-theme `@Preview` composables (`TurnCueIconsGalleryLightPreview`, `TurnCueIconsGalleryDarkPreview`) in `TurnPromptBanner.kt`.
   * The gallery SHALL display all 8 `TurnDirection` enum entries (`LEFT`, `RIGHT`, `SLIGHT_LEFT`, `SLIGHT_RIGHT`, `SHARP_LEFT`, `SHARP_RIGHT`, `STRAIGHT`, `U_TURN`) in a structured grid/row layout.
   * Each item SHALL display the vector icon using `painterResource(id = direction.iconResId)` and the localized direction title via `stringResource(id = direction.displayNameResId)` with appropriate Material 3 typography and colors.
   * Dual `@Preview` annotations SHALL support Light Theme and Dark Theme (`uiMode = Configuration.UI_MODE_NIGHT_YES`) with `showBackground = true`.
3. *Visual & Structural Contract Testing*:
   * Unit test `TurnDirectionIconContractTest.kt` SHALL programmatically parse each vector XML resource referenced by `TurnDirection.values()`.
   * Test SHALL verify XML root element `<vector>`, valid 24x24 viewport, non-empty `android:pathData`, and bottom-stem grounding ($y=19..20$).
   * Test SHALL verify mathematical horizontal symmetry between `ic_turn_slight_right` and `ic_turn_slight_left`.
4. *Preservation of System Invariants*:
   * Real-time turn-by-turn guidance math (`TurnByTurnNavigationEngine.kt`), angle threshold classification in `TurnCueDetector.kt`, and audio alerting (`NavigationAudioAlertManager.kt`) MUST remain completely unaltered.
   * 100% full clean-room regression pass rate (`./gradlew testDebugUnitTest`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Slight Right Icon Geometry**:
  * *Given* an upcoming navigation cue with `TurnDirection.SLIGHT_RIGHT`
  * *When* rendering `ic_turn_slight_right.xml` in the navigation banner
  * *Then* the icon displays a clean vertical base rising from the bottom baseline $y=20$ and smoothly veering 45° to the upper-right with a symmetric arrowhead.
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
  * *Then* stem width (2dp), baseline position ($y=19..20$), and arrowhead proportions are visually harmonious across all directions.

### 1.4 System Invariants
* Viewport dimensions $24 \times 24$ and primary fill color token `#FF1976D2` MUST be preserved.
* Zero changes to geometric turn angle classification thresholds in `TurnCueDetector.kt` or route tracking algorithms.
* 100% full clean-room unit test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-360`). No existing requirement logic modified. Complements `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, ATT-1450) and `REQ-UI-324` (*Turn-by-Turn Navigation Prompts Top-Level Spatial Overlay Integration & Design Guideline Harmonization*, ATT-2941).
* **Historical Origin & Commit Trace**: Baseline turn cue drawables were originally added during initial turn prompt development in `ATT-1450` (Sprint 2026-40.16). While cardinal turns (`LEFT`, `RIGHT`, `STRAIGHT`) used clean paths, `SLIGHT_RIGHT` and `SLIGHT_LEFT` accidentally used preliminary SVG trace data.
* **Root Reason for Existing Formulation**: The original asset was imported from an unvetted vector snippet that lacked bottom stem grounding.
* **Preservation of Core Invariants**: Viewport 24x24, color `#FF1976D2`, and integration with `TurnDirection` enum and `TurnPromptBanner.kt` remain 100% preserved.

---

## 2. Test Specification (TST-UI-323)

### Test Case 1: `test_allTurnDirections_haveExistingValidVectorDrawables` (`TST-UI-323.1`)
* **Scope**: Vector Drawable Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`
* **Preconditions**: `TurnDirection` enum with 8 directional values.
* **Action**: Iterate over all `TurnDirection.values()`, resolve corresponding XML file in `src/main/res/drawable/`, and parse XML tree.
* **Expected Result**:
  * Every drawable resource exists on disk.
  * Root tag is `<vector>`.
  * `android:width` and `android:height` are `"24dp"`.
  * `android:viewportWidth` and `android:viewportHeight` are `"24"`.
  * `<path>` child tag exists with non-empty `android:pathData`.
  * Fill color is `#FF1976D2`.

### Test Case 2: `test_slightRightAndLeft_areCanonicalMaterialPathsAndMirrored` (`TST-UI-323.2`)
* **Scope**: Mathematical Symmetry & Vector Path Geometry Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`
* **Preconditions**: `ic_turn_slight_right.xml` and `ic_turn_slight_left.xml` on disk.
* **Action**: Read and inspect `pathData` strings.
* **Expected Result**:
  * `ic_turn_slight_right.xml` pathData equals canonical `M12.34,6V4H18v5.66h-2V7.41l-5,5V20h-2v-7.58c0,-0.53 0.21,-1.04 0.59,-1.41l5,-5H12.34z`.
  * `ic_turn_slight_left.xml` pathData equals canonical `M11.66,6V4H6v5.66h2V7.41l5,5V20h2v-7.58c0,-0.53 -0.21,-1.04 -0.59,-1.41l-5,-5H11.66z`.
  * Both paths anchor to baseline $y=20$ (`V20`).
  * Both paths have stem width of exactly 2 units (`h2` / `h-2`).

### Test Case 3: `test_turnCueIconsGallery_previewDeclaration` (`TST-UI-323.3`)
* **Scope**: UI Composable & Preview Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`
* **Preconditions**: `TurnPromptBanner.kt` containing preview composables.
* **Action**: Reflectively/AST verify presence of `TurnCueIconsGallery`, `TurnCueIconsGalleryLightPreview`, and `TurnCueIconsGalleryDarkPreview`.
* **Expected Result**: Previews compile and are annotated with `@Preview` and `showBackground = true`.

### Test Case 4: 9-Language Localization Audit (`TST-UI-323.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Preconditions**: All 8 `turn_cue_*` strings in `strings.xml`.
* **Action**: Execute `TranslationParityTest`.
* **Expected Result**: 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-323.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-323.1` | Vector Contract | `TurnDirection.iconResId` XML structure | `REQ-UI-360` (Cl. 1, 3) | Specified |
| `TST-UI-323.2` | Vector Math | `ic_turn_slight_right` & `left` pathData | `REQ-UI-360` (Cl. 1, 3) | Specified |
| `TST-UI-323.3` | Preview Contract | `TurnCueIconsGallery` | `REQ-UI-360` (Cl. 2) | Specified |
| `TST-UI-323.4` | Localization | `TranslationParityTest` | `REQ-UI-360`, `REQ-UI-106` | Specified |
| `TST-UI-323.5` | Clean-Room Suite | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
