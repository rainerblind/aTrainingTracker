# Stage 5 Walkthrough - ATT-3455: Fix distorted vector icons for slight right and slight left turn navigation cues

**Ticket**: [ATT-3455](https://atrainingtracker.atlassian.net/browse/ATT-3455)  
**Stage 5 Subtask**: [ATT-3490](https://atrainingtracker.atlassian.net/browse/ATT-3490) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-360` (`Verified`)  
**Test Mapping**: `TST-UI-323` (`Verified`)  
**Branch**: `feature/ATT-3455`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Executive Summary & Root Cause

In `aTrainingTracker`, turn-by-turn navigation cues (REQ-MAP-028, REQ-UI-324 / ATT-1450, ATT-2632) visually guide athletes along routes using `TurnPromptBanner.kt` and `TurnDirection` enum drawables. However, the vector path assets for "Halb rechts" (`ic_turn_slight_right.xml`) and "Halb links" (`ic_turn_slight_left.xml`) suffered from severe geometric distortion:
1. The paths outlined an irregular S-curve ribbon that floated in the upper half of the 24x24 canvas, lacking bottom stem grounding ($y=19..20$).
2. The arrowheads lacked geometric symmetry with standard Google Material Design navigation iconography.
3. Developers and designers lacked an instant `@Preview` gallery composable in Android Studio to inspect all 8 turn icons side-by-side in Light and Dark themes.

ATT-3455 successfully resolves these defects by:
- Replacing both drawables with canonical, bottom-anchored Material Design vector geometries ($y=20$ grounding, 2dp stroke, 45° bend).
- Enforcing exact mathematical horizontal reflection ($x' = 24 - x$) for the slight left asset across all 15 vertex and Bézier control points.
- Introducing dynamic theme-aware styling via `android:fillColor="?attr/colorPrimary"`.
- Providing an interactive multi-theme Jetpack Compose `@Preview` gallery in `TurnPromptBanner.kt` displaying all 8 `TurnDirection` symbols.
- Guaranteeing 100% 9-language localization parity for the gallery title across English, German, French, Spanish, Italian, Dutch, Polish, Portuguese, and Japanese.
- Introducing automated contract verification in `TurnDirectionIconContractTest.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-360` (*Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery*) -> **Status: Verified**
* **Test Mapping**: `TST-UI-323` (*Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery Verification*) -> **Status: Verified**
* **Governance Verification**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.2` -> `Net-new requirement(s) detected: REQ-UI-360. Bypassing archaeology check cleanly.`

---

## 3. Preserved Invariants & Invariant Verification

1. **Zero Unintended Regressions**: Existing route guidance math (`TurnByTurnNavigationEngine.kt`), angle classification thresholds ($\pm 45^\circ$ in `TurnCueDetector.kt`), and auditory alerts (`NavigationAudioAlertManager.kt`) remain 100% unaltered.
2. **Resource ID Compatibility & Fallback Stability**: The file paths and names (`res/drawable/ic_turn_slight_right.xml` and `res/drawable/ic_turn_slight_left.xml`) are strictly retained as exact in-place drop-in replacements. R-class IDs (`R.drawable.ic_turn_slight_right` and `R.drawable.ic_turn_slight_left`) are strictly preserved. `TurnDirection` enum mappings (`iconResId`, `displayNameResId`) remain completely unchanged, requiring zero caller re-indexing or enum modifications.
3. **Theme-Aware Dynamic Color & Tinting**: Both vector drawables maintain standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`). Using `android:fillColor="?attr/colorPrimary"` enables dynamic theme switching (Light/Dark mode) in native XML layouts while remaining fully compatible with Compose `Icon(..., tint = MaterialTheme.colorScheme.primary)`.
4. **Visual Regression & Layout Invariance**: `TurnPromptBanner.kt` renders cue icons via standard `Icon(painter = painterResource(id = cue.direction.iconResId), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)`. Because Compose `Icon` uses centered aspect-fit scaling within its 48dp container and no manual padding hacks exist in `TurnPromptBanner`, replacing the vector path geometry requires zero layout adjustments in the banner.
5. **Visual Weight Harmonization**: The canonical 2dp stem width ($x \in [9, 11]$ for right, $x \in [13, 15]$ for left) and standard 45° bend harmonize optical weight with adjacent turn assets (`ic_turn_right`, `ic_turn_sharp_right`, `ic_turn_straight`).
6. **Documentation & Safety Guard**: Both vector XML drawables include an explicit top-level comment header: `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-323 -->` to warn maintainers of contract test dependencies.
7. **Preview Safety & Bloat Prevention**: The `TurnCueIconsGallery` composable is scoped as `private` within `TurnPromptBanner.kt` and dedicated solely to `@Preview` annotations. R8/ProGuard automatically strips private preview composables from release APKs, preventing code bloat in production builds.
8. **Modularity Invariant**: `TurnPromptBanner.kt` is 298 lines, strictly complying with the `< 400` line modularity limit.

---

## 4. Detailed Changes

### Component 1: Canonical Vector Drawables (`ic_turn_slight_right.xml`, `ic_turn_slight_left.xml`)
* `app/src/main/res/drawable/ic_turn_slight_right.xml`:
  ```xml
  <!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-323 -->
  <vector xmlns:android="http://schemas.android.com/apk/res/android"
      android:width="24dp"
      android:height="24dp"
      android:viewportWidth="24"
      android:viewportHeight="24">
      <path
          android:fillColor="?attr/colorPrimary"
          android:pathData="M12.34,6V4H18v5.66h-2V7.41l-5,5V20h-2v-7.58c0,-0.53 0.21,-1.04 0.59,-1.41l5,-5H12.34z" />
  </vector>
  ```
* `app/src/main/res/drawable/ic_turn_slight_left.xml`:
  ```xml
  <!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-323 -->
  <vector xmlns:android="http://schemas.android.com/apk/res/android"
      android:width="24dp"
      android:height="24dp"
      android:viewportWidth="24"
      android:viewportHeight="24">
      <path
          android:fillColor="?attr/colorPrimary"
          android:pathData="M11.66,6V4H6v5.66h2V7.41l5,5V20h2v-7.58c0,-0.53 -0.21,-1.04 -0.59,-1.41l-5,-5H11.66z" />
  </vector>
  ```

### Component 2: Interactive Compose Preview Gallery (`TurnPromptBanner.kt`)
* Added `TurnCueIconsGallery` displaying all 8 `TurnDirection` enum entries in a responsive Material 3 card grid.
* Configured dual previews:
  - `@Preview(name = "Turn Cue Icons Gallery - Light", showBackground = true)`
  - `@Preview(name = "Turn Cue Icons Gallery - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)`
* Localized header text referencing `R.string.turn_cue_gallery_title`.

### Component 3: 9-Language Localization Parity
* Added `turn_cue_gallery_title` across all 9 localized string resource files:
  - `values/strings.xml`: "Turn Cue Direction Icons"
  - `values-de/strings.xml`: "Abbiegehinweis-Symbole"
  - `values-fr/strings.xml`: "Icônes des indications de direction"
  - `values-es/strings.xml`: "Iconos de indicaciones de giro"
  - `values-it/strings.xml`: "Icone delle indicazioni di svolta"
  - `values-nl/strings.xml`: "Afslaginstructiepictogrammen"
  - `values-pl/strings.xml`: "Ikony wskazówek manewrów"
  - `values-pt/strings.xml`: "Ícones de indicações de direção"
  - `values-ja/strings.xml`: "方向転換指示アイコン"

### Component 4: Contract Test Suite (`TurnDirectionIconContractTest.kt`)
* Implemented automated contract suite in `com.atrainingtracker.trainingtracker.routes.TurnDirectionIconContractTest`:
  - `testAllTurnDirectionDrawablesExistAndHaveStandard24dpViewport`: Asserts all 8 drawables exist, have `<vector>` root, and $24 \times 24$ viewport.
  - `testSlightTurnIconsHaveThemeAwareColorAndSafetyHeader`: Asserts `?attr/colorPrimary` and safety comment header without hardcoded hex strings.
  - `testSlightTurnIconsGroundingAndStemWidth`: Asserts $y=20$ baseline grounding and 2dp stem width.
  - `testTurnSlightLeftIsExactHorizontalReflectionOfTurnSlightRight`: Evaluates absolute milestone coordinates and Bézier control points, asserting $x_R + x_L = 24.00 \pm 0.01$ and $y_R = y_L \pm 0.01$ across all 15 coordinate milestones.
  - `testTurnPromptBannerContainsPrivateGalleryAndDualThemePreviews`: Validates private gallery declaration and Light/Dark previews.

---

## 5. Verification Evidence

1. **Unit & Contract Tests**:
   - `TurnDirectionIconContractTest`: 5/5 passed (100%).
   - `TurnCueDetectorTest`: passed (100%).
   - `TurnByTurnNavigationEngineTest`: passed (100%).
2. **Clean-Room Full Suite Regression**:
   - `./gradlew testDebugUnitTest`: **BUILD SUCCESSFUL** (519 test suites, 0 failures, 0 errors).
3. **Target Device Installation**:
   - `./gradlew installDebug`: **BUILD SUCCESSFUL**, installed onto physical test device `Pixel 10 - 17`.
4. **Modularity & Architecture Verification**:
   - `TurnPromptBanner.kt`: 298 lines (< 400 line ceiling).
   - Zero compilation warnings or lint errors introduced.

---

## 6. Rollback & Deployment Strategy

* **Branch Isolation**: All changes are cleanly isolated on `feature/ATT-3455`.
* **Merge Plan**: Fast-forward-free merge into `sprint/2026-42.2` via `git merge --no-ff feature/ATT-3455`.
* **Rollback Plan**: In the unlikely event of an issue, reverting the merge commit on `sprint/2026-42.2` leaves the sprint baseline completely untouched.
