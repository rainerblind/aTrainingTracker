# Stage 2 Requirement & Test Specification: ATT-3573 - Refine sharp turn vector icons to reduce visual resemblance to U-turn

**Ticket**: [ATT-3573](https://rainerblind.atlassian.net/browse/ATT-3573)  
**Sub-task**: [ATT-3705](https://rainerblind.atlassian.net/browse/ATT-3705) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `None`  
**Active Sprint**: `2026-42.5`  
**Branch**: `feature/ATT-3573`  
**Requirement Mapping**: `REQ-UI-361` (amended)  
**Test Mapping**: `TST-UI-324` (updated)  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. System Requirement Specification (`REQ-UI-361` Amended)

### REQ-UI-361: Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification

The system SHALL refine the vector path drawables for sharp right (`ic_turn_sharp_right.xml`) and sharp left (`ic_turn_sharp_left.xml`) with canonical, bottom-anchored Material Design vector geometries featuring an acute ~135° deflection bend and diagonal downward-pointing arrowheads ($\searrow$ and $\swarrow$), completely eliminating visual resemblance to the 180° circular dome loop of U-turns, and SHALL enforce visual and mathematical reflection symmetry in contract testing (ATT-3493, ATT-3573, complementing `REQ-MAP-028`, `REQ-UI-324`, and `REQ-UI-360`):

1. *Canonical Vector Path Replacement (`ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml`)*:
   - In `ic_turn_sharp_right.xml`, the vector path SHALL rise vertically from baseline $y=20$ with standard 2dp stroke width ($x \in [6, 8]$) to $y=8.5$, bend sharply around an acute ~135° deflection arc ($cx=10.5, cy=8.5$, outer radius $R=4.5$, inner radius $r=2.5$), extend diagonally along $(+1, +1)$, and terminate with a diagonal downward-pointing $135^\circ$ arrowhead pointing South-East ($\searrow$):  
     `M6,20h2V8.5c0,-1.38 1.12,-2.5 2.5,-2.5c0.66,0 1.3,0.26 1.77,0.73l2.5,2.5l-2.5,2.5l6.71,0.3l-0.3,-6.71l-2.5,2.5l-2.5,-2.5c-0.84,-0.84 -1.99,-1.32 -3.18,-1.32c-2.49,0 -4.5,2.01 -4.5,4.5V20z`
   - In `ic_turn_sharp_left.xml`, the vector path SHALL provide an exact horizontal reflection across the vertical centerline ($x' = 24 - x, y' = y$):  
     `M18,20h-2V8.5c0,-1.38 -1.12,-2.5 -2.5,-2.5c-0.66,0 -1.3,0.26 -1.77,0.73l-2.5,2.5l2.5,2.5l-6.71,0.3l0.3,-6.71l2.5,2.5l2.5,-2.5c0.84,-0.84 1.99,-1.32 3.18,-1.32c2.49,0 4.5,2.01 4.5,4.5V20z`
   - Both assets SHALL preserve standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`, `width="24dp"`, `height="24dp"`), primary tint fill color token `#FF1976D2`, and contract safety header comment `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->`.

2. *Mathematical Reflection Symmetry & Baseline Grounding Contract Tests*:
   - Unit test `TurnDirectionIconContractTest.kt` SHALL programmatically parse each vector XML resource for sharp right and sharp left.
   - Test SHALL verify XML root element `<vector>`, valid 24x24 viewport, non-empty `android:pathData`, and bottom-stem grounding at $y=20$ (`M6,20h2` and `V20z` for right; `M18,20h-2` and `V20z` for left).
   - Test SHALL verify mathematical horizontal reflection symmetry between `ic_turn_sharp_right` and `ic_turn_sharp_left` across midline $x=12$ ($x_R + x_L = 24.0 \pm 0.01$, $|y_R - y_L| \le 0.01$).

3. *Rotational Progression & Preview Gallery Compatibility*:
   - The diagonal $135^\circ$ / $-135^\circ$ arrowheads SHALL complete the consistent rotational progression of the turn icon family in `TurnCueIconsGallery` (`Straight 0°` $\uparrow$ $\to$ `Slight Right 45°` $\nearrow$ $\to$ `Right 90°` $\rightarrow$ $\to$ `Sharp Right 135°` $\searrow$ $\to$ `U-Turn 180°` $\downarrow$ $\to$ `Sharp Left -135°` $\swarrow$ $\to$ `Left -90°` $\leftarrow$ $\to$ `Slight Left -45°` $\nwarrow$).
   - Sharp turn cues MUST exhibit distinct visual separation from U-turn cues (`ic_turn_u_turn.xml`), preventing false turn-around interpretations by athletes.

4. *Preservation of System Invariants*:
   - Real-time turn-by-turn guidance math (`TurnByTurnNavigationEngine.kt`), angle threshold classification in `TurnCueDetector.kt`, and audio alerting (`NavigationAudioAlertManager.kt`) MUST remain completely unaltered.
   - 100% full clean-room regression pass rate (`./gradlew testDebugUnitTest`).

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   Amends `REQ-UI-361` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*, ATT-3493) and `TST-UI-324`. Complements `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, ATT-1450), `REQ-UI-324` (*Turn-by-Turn Navigation Prompts Overlay*, ATT-2941), and `REQ-UI-360` (*Turn-by-Turn Navigation Slight Turn Harmonization*, ATT-3455).
2. **Historical Origin & Commit Trace**:
   Originally introduced in commit `39610f62` (ATT-3493, Sprint 2026-42.3).
3. **Root Reason for Existing Formulation**:
   The author in ATT-3493 sought to ground sharp turns to baseline $y=20$ and add downward arrowheads, but accidentally formulated a $180^\circ$ semicircle arch spanning across to $x=18$ paired with a vertical downward shaft and a downward-pointing arrow tip. This unintentionally mirrored the exact visual silhouette of `ic_turn_u_turn.xml`.
4. **Preservation of Core Invariants**:
   - Baseline grounding at $y=20$ with uniform 2dp stroke width is preserved.
   - Standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`) is preserved.
   - Tint fill token `#FF1976D2` and safety header `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->` are preserved.
   - Route navigation engine heuristics, angle thresholds in `TurnCueDetector.kt`, and Compose banner padding in `TurnPromptBanner.kt` remain 100% untouched.

---

### Acceptance Criteria (Given-When-Then)

* **Criterion 1: Sharp Right Icon Directional Clarity**:
  - *Given* an upcoming navigation cue with `TurnDirection.SHARP_RIGHT`,
  - *When* rendering `ic_turn_sharp_right.xml` in the navigation banner or cue sheet,
  - *Then* the icon displays a clean vertical base rising from bottom baseline $y=20$, looping through an acute ~135° bend, and exiting with a diagonal shaft and arrowhead pointing South-East ($135^\circ$, $\searrow$), with zero resemblance to a $180^\circ$ U-turn.

* **Criterion 2: Sharp Left Icon Reflection Symmetry**:
  - *Given* an upcoming navigation cue with `TurnDirection.SHARP_LEFT`,
  - *When* rendering `ic_turn_sharp_left.xml`,
  - *Then* the icon displays the identical, cleanly mirrored acute bend and diagonal arrowhead pointing South-West ($-135^\circ$, $\swarrow$).

* **Criterion 3: Full Family Rotational Harmony in Preview Gallery**:
  - *Given* all 8 turn icons displayed side-by-side in `TurnCueIconsGallery`,
  - *When* visually inspecting the rotational sequence from Straight ($0^\circ$) to U-Turn ($180^\circ$),
  - *Then* each step progresses in consistent, unambiguous angular increments with harmonious 2dp stroke widths, baseline grounding, and canonical arrowheads.

* **Criterion 4: Contract Test Verification (`TST-UI-324`)**:
  - *Given* the vector definitions of `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml`,
  - *When* parsed by `TurnDirectionIconContractTest.kt`,
  - *Then* all 22 path milestone coordinates reflect across midline $x=12$ with zero divergence ($x_R + x_L = 24.0 \pm 0.01$, $|y_R - y_L| \le 0.01$), baseline grounding is verified at $y=20$, and stroke width is verified as 2dp.

---

## 2. Test Specification (`TST-UI-324` Updated)

### Test Environment & Tools
- **Test Class**: `com.atrainingtracker.trainingtracker.routes.TurnDirectionIconContractTest`
- **Execution Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.routes.TurnDirectionIconContractTest
  ```

### Detailed Test Cases

1. **`testAllTurnDirectionDrawablesExistAndHaveStandard24dpViewport`**:
   - Parses XML files for all 8 `TurnDirection` values.
   - Asserts root element is `<vector>`.
   - Asserts `android:width="24dp"`, `android:height="24dp"`, `android:viewportWidth="24"`, `android:viewportHeight="24"`.
   - Asserts `android:pathData` is non-null and non-blank.

2. **`testSharpTurnIconsHaveStandardColorAndSafetyHeader`**:
   - Asserts `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml` contain contract comment `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->`.
   - Asserts `android:fillColor` is `#FF1976D2`.

3. **`testSharpTurnIconsGroundingAndStemWidth`**:
   - Asserts `ic_turn_sharp_right.xml` starts stem at `y=20` (`M6,20`) and closes at `y=20` (`V20z`).
   - Asserts `ic_turn_sharp_left.xml` starts stem at `y=20` (`M18,20`) and closes at `y=20` (`V20z`).
   - Asserts stem baseline cut width is 2dp (`h2` for right, `h-2` for left).

4. **`testTurnSharpLeftIsExactHorizontalReflectionOfTurnSharpRight`**:
   - Programmatically evaluates absolute milestones for both paths via `evaluateAbsoluteMilestones()`.
   - Asserts identical milestone count (22 vertices).
   - Iterates through all milestone indices $i \in [0, 21]$ asserting:
     - $x_R + x_L = 24.0 \pm 0.01$
     - $|y_R - y_L| \le 0.01$

5. **`testTurnPromptBannerContainsPrivateGalleryAndDualThemePreviews`**:
   - Asserts `TurnPromptBanner.kt` contains private composable `TurnCueIconsGallery`.
   - Asserts dual-theme `@Preview` declarations for Light and Dark (`UI_MODE_NIGHT_YES`).

6. **9-Language Localization Audit (`TranslationParityTest.kt`)**:
   - Asserts strings `turn_cue_sharp_right` and `turn_cue_sharp_left` exist, are non-empty, and match across all 9 application locales:
     `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 3. Traceability Matrix

| Requirement Clause | Verification Test Case | Test File | Target Artifact | Status |
| :--- | :--- | :--- | :--- | :---: |
| `REQ-UI-361.1` (Vector Path Replacement & Material Geometry) | `testAllTurnDirectionDrawablesExistAndHaveStandard24dpViewport`, `testSharpTurnIconsGroundingAndStemWidth` | `TurnDirectionIconContractTest.kt` | `ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml` | Specified |
| `REQ-UI-361.1` (Viewport, Color, Safety Header) | `testSharpTurnIconsHaveStandardColorAndSafetyHeader` | `TurnDirectionIconContractTest.kt` | `ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml` | Specified |
| `REQ-UI-361.2` (Reflection Symmetry Across x=12) | `testTurnSharpLeftIsExactHorizontalReflectionOfTurnSharpRight` | `TurnDirectionIconContractTest.kt` | `ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml` | Specified |
| `REQ-UI-361.3` (Rotational Progression & Preview Gallery) | `testTurnPromptBannerContainsPrivateGalleryAndDualThemePreviews` | `TurnDirectionIconContractTest.kt` | `TurnPromptBanner.kt` | Specified |
| `REQ-UI-361.4` (Clean-Room Test Pass & Zero Route Math Regressions) | Full test suite execution (`./gradlew testDebugUnitTest`) | Entire test suite | Navigation engine & routes | Specified |

---

## 4. Invariants & Guardrails

1. **Pure Vector XML Assets**: No Kotlin code or runtime shader modifications needed.
2. **Resource Stability**: Resource IDs `R.drawable.ic_turn_sharp_right` and `R.drawable.ic_turn_sharp_left` remain stable drop-in replacements.
3. **No Navigation Heuristic Changes**: `TurnCueDetector.kt` and `TurnByTurnNavigationEngine.kt` thresholds remain unaltered.
4. **Clean-Room Regression**: 100% unit test pass rate (`./gradlew testDebugUnitTest`).
