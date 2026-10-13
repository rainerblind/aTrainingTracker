# Stage 3: Implementation Plan - ATT-3455: Fix distorted vector icons for slight right and slight left turn navigation cues

**Ticket**: [ATT-3455](https://atrainingtracker.atlassian.net/browse/ATT-3455)  
**Sub-task**: [ATT-3487](https://atrainingtracker.atlassian.net/browse/ATT-3487) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `Sprint 2026-42.2`  
**Requirement Mapping**: `REQ-UI-360`  
**Test Mapping**: `TST-UI-323`  
**Branch**: `feature/ATT-3455`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Description & Background

In `aTrainingTracker`, turn-by-turn navigation cues (REQ-MAP-028, REQ-UI-324 / ATT-1450, ATT-2632) visually guide athletes along routes using `TurnPromptBanner.kt` and `TurnDirection` enum drawables. However, the vector path assets for "Halb rechts" (`ic_turn_slight_right.xml`) and "Halb links" (`ic_turn_slight_left.xml`) suffer from severe geometric distortion:
1. The paths outline an irregular S-curve ribbon that floats in the upper half of the 24x24 canvas, lacking bottom stem grounding ($y=19..20$).
2. The arrowheads lack geometric symmetry with standard Google Material Design navigation iconography.
3. Developers and designers currently lack an instant `@Preview` gallery composable in Android Studio to inspect all 8 turn icons side-by-side in Light and Dark themes.

ATT-3455 replaces both drawables with canonical, bottom-anchored Material Design vector paths, adopts theme-aware color attributes, and introduces an interactive multi-theme Compose `@Preview` gallery with automated contract testing.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-360` (*Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery*)
* **Test Mapping**: `TST-UI-323` (*Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing route guidance math (`TurnByTurnNavigationEngine.kt`), angle classification thresholds ($\pm 45^\circ$ in `TurnCueDetector.kt`), and auditory alerts (`NavigationAudioAlertManager.kt`) remain 100% unaltered.
2. **Resource ID Compatibility & Fallback Strategy**: The file paths and names (`res/drawable/ic_turn_slight_right.xml` and `res/drawable/ic_turn_slight_left.xml`) remain 100% identical drop-in replacements. R-class IDs (`R.drawable.ic_turn_slight_right` and `R.drawable.ic_turn_slight_left`) are strictly preserved. `TurnDirection` enum mappings (`iconResId`, `displayNameResId`) remain completely unchanged, requiring zero caller re-indexing or enum modifications.
3. **Theme-Aware Dynamic Color & Tinting**: Both vector drawables maintain standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`). Instead of hardcoded hex values, `android:fillColor="?attr/colorPrimary"` is used. This enables dynamic theme switching (Light/Dark mode) in native XML layouts while remaining fully compatible with Compose `Icon(..., tint = MaterialTheme.colorScheme.primary)`.
4. **Visual Regression & Layout Invariance**: `TurnPromptBanner.kt` renders cue icons via standard `Icon(painter = painterResource(id = cue.direction.iconResId), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)`. Because Compose `Icon` uses centered aspect-fit scaling within its 48dp container and no manual padding hacks exist in `TurnPromptBanner`, replacing the vector path geometry requires zero layout adjustments in the banner.
5. **Visual Weight Harmonization**: The canonical 2dp stem width ($x \in [9, 11]$ for right, $x \in [13, 15]$ for left) and standard 45° bend harmonize optical weight with adjacent turn assets (`ic_turn_right`, `ic_turn_sharp_right`, `ic_turn_straight`).
6. **Mathematical Reflection Parity ($x' = 24 - x$, $y' = y$)**: `ic_turn_slight_left.xml` is derived via point-by-point horizontal coordinate reflection of `ic_turn_slight_right.xml` across the vertical midline $x = 12$. For every absolute vertex $(x_R, y_R)$ and cubic Bézier control point $(x_{R,c}, y_{R,c})$, the corresponding left point satisfies $x_L = 24 - x_R$ and $y_L = y_R$.
7. **Documentation & Safety Guard**: Both vector XML drawables include an explicit top-level comment header: `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-323 -->` to warn maintainers of contract test dependencies.
8. **Preview Safety & Bloat Prevention**: The `TurnCueIconsGallery` composable is scoped as `private` within `TurnPromptBanner.kt` and dedicated solely to `@Preview` annotations. R8/ProGuard automatically strips private preview composables from release APKs, preventing code bloat in production builds.
9. **Modularity Invariant**: `TurnPromptBanner.kt` is currently 209 lines and will remain strictly < 400 lines after adding the preview gallery composable.
10. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
11. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Canonical Vector Drawables (`ic_turn_slight_right.xml`, `ic_turn_slight_left.xml`)
* Replace `app/src/main/res/drawable/ic_turn_slight_right.xml`:
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
* Replace `app/src/main/res/drawable/ic_turn_slight_left.xml`:
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

#### Detailed Mathematical Reflection Proof Table ($x_L + x_R = 24.00, y_L = y_R$)
Tracing both paths from start to close demonstrates 100% geometric symmetry for every absolute coordinate, including both Bézier control points:

| Segment | Right Command & End $(x_R, y_R)$ | Left Command & End $(x_L, y_L)$ | $x_R + x_L$ | $y_R - y_L$ | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Start** | `M12.34,6` $\to (12.34, 6.00)$ | `M11.66,6` $\to (11.66, 6.00)$ | **24.00** | **0.00** | Validated |
| **Top bar** | `V4` $\to (12.34, 4.00)$ | `V4` $\to (11.66, 4.00)$ | **24.00** | **0.00** | Validated |
| **Tip outer** | `H18` $\to (18.00, 4.00)$ | `H6` $\to (6.00, 4.00)$ | **24.00** | **0.00** | Validated |
| **Arrow down** | `v5.66` $\to (18.00, 9.66)$ | `v5.66` $\to (6.00, 9.66)$ | **24.00** | **0.00** | Validated |
| **Arrow notch** | `h-2` $\to (16.00, 9.66)$ | `h2` $\to (8.00, 9.66)$ | **24.00** | **0.00** | Validated |
| **Inner notch** | `V7.41` $\to (16.00, 7.41)$ | `V7.41` $\to (8.00, 7.41)$ | **24.00** | **0.00** | Validated |
| **Stem upper** | `l-5,5` $\to (11.00, 12.41)$ | `l5,5` $\to (13.00, 12.41)$ | **24.00** | **0.00** | Validated |
| **Stem base R** | `V20` $\to (11.00, 20.00)$ | `V20` $\to (13.00, 20.00)$ | **24.00** | **0.00** | Validated |
| **Stem width** | `h-2` $\to (9.00, 20.00)$ | `h2` $\to (15.00, 20.00)$ | **24.00** | **0.00** | Validated |
| **Stem base L** | `v-7.58` $\to (9.00, 12.42)$ | `v-7.58` $\to (15.00, 12.42)$ | **24.00** | **0.00** | Validated |
| **Curve C1** | $dx_1=0, dy_1=-0.53 \to (9.00, 11.89)$ | $dx_1=0, dy_1=-0.53 \to (15.00, 11.89)$ | **24.00** | **0.00** | Validated |
| **Curve C2** | $dx_2=0.21, dy_2=-1.04 \to (9.21, 11.38)$ | $dx_2=-0.21, dy_2=-1.04 \to (14.79, 11.38)$ | **24.00** | **0.00** | Validated |
| **Curve End** | $dx=0.59, dy=-1.41 \to (9.59, 11.01)$ | $dx=-0.59, dy=-1.41 \to (14.41, 11.01)$ | **24.00** | **0.00** | Validated |
| **Stem angle** | `l5,-5` $\to (14.59, 6.01)$ | `l-5,-5` $\to (9.41, 6.01)$ | **24.00** | **0.00** | Validated |
| **Close loop** | `H12.34z` $\to (12.34, 6.01)$ | `H11.66z` $\to (11.66, 6.01)$ | **24.00** | **0.00** | Validated |

*Note on Cubic Bézier Reflection*: The curve command in `ic_turn_slight_right` is `c0,-0.53 0.21,-1.04 0.59,-1.41`. Under the reflection matrix $\begin{bmatrix} dx' \\ dy' \end{bmatrix} = \begin{bmatrix} -1 & 0 \\ 0 & 1 \end{bmatrix} \begin{bmatrix} dx \\ dy \end{bmatrix}$, the negated $x$-offsets are $dx_1' = -0 = 0$, $dx_2' = -0.21$, and $dx' = -0.59$, yielding `c0,-0.53 -0.21,-1.04 -0.59,-1.41`. As shown above, this precisely mirrors control points $C_1(9.00, 11.89) \leftrightarrow C_1(15.00, 11.89)$, $C_2(9.21, 11.38) \leftrightarrow C_2(14.79, 11.38)$, and End $(9.59, 11.01) \leftrightarrow (14.41, 11.01)$.

### Component 2: Interactive Compose Preview Gallery (`TurnPromptBanner.kt`)
* Implement private `TurnCueIconsGallery` displaying all 8 `TurnDirection` enum entries in a structured 4-column or 2-row grid/card layout.
* Each tile renders:
  * Icon using `painterResource(id = direction.iconResId)` with `tint = MaterialTheme.colorScheme.primary` (40.dp).
  * Localized label using `stringResource(id = direction.displayNameResId)` with `MaterialTheme.typography.labelSmall`.
* Dual preview configurations:
  * `@Preview(name = "Turn Cue Icons Gallery - Light", showBackground = true)`
  * `@Preview(name = "Turn Cue Icons Gallery - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)`
* Scoped `private` to ensure complete R8 removal from production APK artifacts.

### Component 3: Unit & Contract Test Suite (`TurnDirectionIconContractTest.kt`)
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`:
  * **Root Element Type**: Asserts root element is `<vector xmlns:android="http://schemas.android.com/apk/res/android">` for all 8 `TurnDirection` drawables.
  * **Viewport Dimensions**: Explicitly asserts `android:width="24dp"`, `android:height="24dp"`, `android:viewportWidth="24"`, and `android:viewportHeight="24"` for all 8 direction drawables.
  * **Dynamic Theme Color**: Explicitly asserts `android:fillColor="?attr/colorPrimary"` (disallowing hardcoded hex color strings like `#FF...` or `#...`) on `ic_turn_slight_right.xml` and `ic_turn_slight_left.xml`.
  * **Safety Comment Header**: Explicitly asserts presence of `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-323 -->` comment header in both slight turn drawable files.
  * **Path Non-Emptiness & Baseline Anchoring**: Validates non-empty `pathData` and asserts stem grounding at $y=20$ (`V20`) and 2dp stem width for slight turns.
  * **Segment-by-Segment Geometric Reflection Test**: Implements an automated path evaluator that parses `pathData`, computes absolute vertices and Bézier control points, and asserts $x_L = 24.00 - x_R \pm 0.01$ and $y_L = y_R \pm 0.01$ across all 15 coordinate milestones.
  * **AST / Preview Verification**: Verifies presence of `@Preview` annotations for `TurnCueIconsGallery` in `TurnPromptBanner.kt`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `TurnPromptBanner.kt` (`TurnCueCard` / `OffRouteCard`).
* **Reused components**: Material 3 `Card`, `Icon`, `Text`, `Surface`, `RoundedCornerShape(12.dp)`.
* **Theme tokens**:
  * Shapes: `RoundedCornerShape(12.dp)` for gallery cards.
  * Spacing: `8.dp` grid spacing, `12.dp` inner padding, `16.dp` outer padding.
  * Colors: `MaterialTheme.colorScheme.surface`, `surfaceContainer`, `primary`, `onSurfaceVariant`.
  * Typography: `MaterialTheme.typography.titleMedium` for header, `labelSmall` / `labelMedium` for direction captions.
* **New one-off styles & justification**: None. Uses strictly standard Material 3 tokens matching Section 5 of `docs/design_guidelines.md`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Slight Right Vector Asset
* **Target File**: `app/src/main/res/drawable/ic_turn_slight_right.xml`
* **Changes**: Drop-in replacement with canonical Material Design path data `M12.34,6V4H18v5.66h-2V7.41l-5,5V20h-2v-7.58c0,-0.53 0.21,-1.04 0.59,-1.41l5,-5H12.34z`, `android:fillColor="?attr/colorPrimary"`, and contract safety header.

### Step 2: Update Slight Left Vector Asset
* **Target File**: `app/src/main/res/drawable/ic_turn_slight_left.xml`
* **Changes**: Drop-in replacement with mirrored canonical Material Design path data `M11.66,6V4H6v5.66h2V7.41l5,5V20h2v-7.58c0,-0.53 -0.21,-1.04 -0.59,-1.41l-5,-5H11.66z`, derived via mathematical reflection ($x' = 24 - x$), `android:fillColor="?attr/colorPrimary"`, and contract safety header.

### Step 3: Implement Interactive `@Preview` Gallery in `TurnPromptBanner.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt`
* **Changes**:
  * Add `@Preview @Composable private fun TurnCueIconsGallery(modifier: Modifier = Modifier)`.
  * Add Light and Dark preview configurations.
  * Maintain file length under 400 lines (currently 209 lines, expected ~290 lines).

### Step 4: Create Contract Test Suite
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`
* **Changes**:
  * Test drawable existence, `<vector>` root, 24x24 viewport for all 8 `TurnDirection` values.
  * Test slight right and slight left path data, $y=20$ baseline anchoring, horizontal symmetry ($x_L = 24 - x_R$, $y_L = y_R$), safety headers, and dynamic `?attr/colorPrimary` fill color attributes.
  * Test preview declarations in `TurnPromptBanner.kt`.

### Step 5: Clean Build & AAPT2 Cache Invalidation
* **Command**: `./gradlew clean`
* **Rationale**: Invalidates Gradle AAPT2 incremental resource caches and forces full recompilation of modified XML vector assets and R-class generation.

### Step 6: Visual Weight & Design Verification
* **Target**: Compare rendered assets across all 8 directions in `TurnCueIconsGallery` preview (Light and Dark) against Section 5 of `docs/design_guidelines.md` to confirm optical weight parity before Stage 4 sign-off.

### Step 7: Execute Targeted Tests
* **Command**: `./gradlew testDebugUnitTest --tests "*TurnDirectionIcon*" --tests "*TurnCueDetector*" --tests "*TurnByTurnNavigationEngine*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  * Unit test suite `./gradlew testDebugUnitTest --tests "*TurnDirectionIcon*"` passes with 100%.
  * Stage 5 full suite `./gradlew testDebugUnitTest` passes 100% (2573+ tests).
  * Build deployed to physical Pixel 10 via `./gradlew installDebug`.
* **Rollback Plan**:
  * All work isolated on branch `feature/ATT-3455`. Reverting branch via `git checkout sprint/2026-42.2` leaves sprint baseline completely untouched.
