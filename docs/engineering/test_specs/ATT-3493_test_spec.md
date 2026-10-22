# Stage 2: Requirement & Test Specification - ATT-3493: Improve vector icons for sharp right and sharp left turn navigation cues

**Ticket**: [ATT-3493](https://rainerblind.atlassian.net/browse/ATT-3493)  
**Sub-task**: [ATT-3502](https://rainerblind.atlassian.net/browse/ATT-3502) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-361` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*)  
**Test Spec ID**: `TST-UI-324`  
**Branch**: `feature/ATT-3493`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-UI-361)

### 1.1 Problem Statement & Rationale
During turn-by-turn route navigation (REQ-MAP-028, REQ-UI-324 / ATT-1450, ATT-2632, ATT-3494), visual prompts for sharp turns ("Scharf rechts" / `TurnDirection.SHARP_RIGHT` and "Scharf links" / `TurnDirection.SHARP_LEFT`) exhibit two significant UI/UX defects in their legacy vector assets:
1. **Lack of Bottom-Stem Grounding**: In `ic_turn_sharp_right.xml`, the vector path data (`M18,13.5l-5,-5v3.5c-3.31,0 -6,2.69 -6,6v3h2v-3c0,-2.21 1.79,-4 4,-4h5v3.5L18,13.5z`) contains only a short 3dp vertical stem ending abruptly at $y=17$, floating without contact to the canonical baseline $y=20$. This clashes with all other directional cue icons (`ic_turn_straight`, `ic_turn_slight_right`, `ic_turn_slight_left`, `ic_turn_left`, `ic_turn_right`, and `ic_turn_u_turn`), which are grounded at $y=20$.
2. **Horizontal Heading Misorientation**: The legacy arrowhead points purely East ($90^\circ$, identical to a normal right turn) with a flat horizontal shaft at $y=13.5$. An acute turn ($> 120^\circ$) requires a downward-angled arrowhead ($135^\circ$, south-east $\searrow$ for sharp right; $-135^\circ$, south-west $\swarrow$ for sharp left) to provide immediate cognitive differentiation from a standard $90^\circ$ right/left turn.

Athletes glancing at cockpit prompts on handlebars or while running require immediate, unambiguous visual clarity. The sharp turn icons must be harmonized with the established Material Design route direction iconography language, grounded at baseline $y=20$, styled with standard 2dp stroke width, curved over an acute loop, oriented downward at $135^\circ$ / $-135^\circ$, and proven mathematically symmetric.

---

### 1.2 Functional & Architectural Requirements

1. *Canonical Vector Path Replacement (`ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml`)*:
   * In `ic_turn_sharp_right.xml`, the vector path SHALL rise vertically from baseline $y=20$ with standard 2dp stroke width ($x \in [6, 8]$) to $y=9$, arch smoothly over top ($cx=12, cy=9$, outer radius $R=6$, inner radius $r=4$), descend with right shaft, and terminate with a downward-pointing $135^\circ$ arrowhead (wing width 4dp):
     `M6,20h2V9c0,-2.21 1.79,-4 4,-4s4,1.79 4,4v2.59l-1.59,-1.59L13,11.41l4,4l4,-4l-1.41,-1.41L18,11.59V9c0,-3.31 -2.69,-6 -6,-6S6,5.69 6,9V20z`
   * In `ic_turn_sharp_left.xml`, the vector path SHALL provide an exact horizontal reflection across the vertical centerline ($x' = 24 - x$):
     `M18,20h-2V9c0,-2.21 -1.79,-4 -4,-4s-4,1.79 -4,4v2.59l1.59,-1.59L11,11.41l-4,4l-4,-4l1.41,-1.41L6,11.59V9c0,-3.31 2.69,-6 6,-6s6,2.69 6,9V20z`
   * Both assets SHALL declare standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`, `width="24dp"`, `height="24dp"`), primary fill color token `#FF1976D2`, and include the safety comment header `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->`.

2. *Mathematical Reflection Symmetry & Baseline Grounding*:
   * Grounding: Path data MUST begin and end grounded at bottom baseline $y=20$ (`h2V9` / `V20z` for right, `h-2V9` / `V20z` for left).
   * Symmetry: For every coordinate $(x_R, y_R)$ in `ic_turn_sharp_right.xml`, the corresponding coordinate in `ic_turn_sharp_left.xml` MUST satisfy $x_L = 24.0 - x_R$ and $y_L = y_R$.

3. *Contract & Regression Testing*:
   * Unit test `TurnDirectionIconContractTest.kt` SHALL be extended to verify:
     1. Presence and correctness of contract safety comments (`TST-UI-324`).
     2. Baseline grounding at $y=20$ and 2dp stem width.
     3. Mathematical horizontal reflection symmetry across $x=12$.
   * 100% full clean-room unit test pass rate (`./gradlew testDebugUnitTest`).

4. *Preservation of System Invariants*:
   * Navigation engine logic (`TurnByTurnNavigationEngine.kt`), curvature thresholds in `TurnCueDetector.kt`, and audio prompting (`NavigationAudioAlertManager.kt`) MUST remain completely unaltered.
   * Compose `@Preview` gallery in `TurnPromptBanner.kt` renders updated icons seamlessly without layout breakage.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Sharp Right Icon Geometry & Grounding**:
  * *Given* an upcoming navigation cue with `TurnDirection.SHARP_RIGHT`,
  * *When* rendering `ic_turn_sharp_right.xml` in the navigation banner,
  * *Then* the icon displays a clean vertical base rising from baseline $y=20$, arches over the top loop, and directs an arrowhead downwards at $135^\circ$ ($\searrow$) with 2dp stroke width and 4dp wings.

* **Criterion 2: Sharp Left Icon Geometry & Grounding**:
  * *Given* an upcoming navigation cue with `TurnDirection.SHARP_LEFT`,
  * *When* rendering `ic_turn_sharp_left.xml` in the navigation banner,
  * *Then* the icon displays the identical, cleanly mirrored base and loop with an arrowhead pointing downwards at $-135^\circ$ ($\swarrow$).

* **Criterion 3: Visual Angular Progression Harmony**:
  * *Given* all 8 turn icons rendered in `TurnCueIconsGalleryPreview`,
  * *When* comparing arrow directions (Straight $0^\circ \to$ Slight Right $45^\circ \to$ Right $90^\circ \to$ Sharp Right $135^\circ \to$ U-Turn $180^\circ$),
  * *Then* the rotational progression of arrowheads is continuous, consistent, and instantly recognizable.

* **Criterion 4: Architectural & Mathematical Symmetry**:
  * *Given* the vector definitions of `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml`,
  * *When* parsed by `TurnDirectionIconContractTest.kt`,
  * *Then* all path milestone coordinates reflect across $x=12$ with zero divergence ($x_R + x_L = 24.0$, $y_R = y_L$).

---

### 1.4 System Invariants

1. **Pure Vector XML Assets**: No external raster dependencies, mipmaps, or third-party vector libraries.
2. **24x24 Viewport & Color Invariant**: Viewport dimensions $24 \times 24$ and `#FF1976D2` fill token preserved.
3. **Zero Navigation Math Impact**: Turn cue detection thresholds, route progression math, and audio alerting remain completely untouched.
4. **100% Full Clean-Room Regression Pass Rate**.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-361`). No existing requirement logic modified. Complements `REQ-MAP-028` (*Visual & Auditory Turn-by-Turn Navigation Prompts*, ATT-1450), `REQ-UI-324` (*Turn-by-Turn Navigation Prompts Overlay*, ATT-2941), and `REQ-UI-360` (*Turn-by-Turn Navigation Slight Turn Harmonization*, ATT-3455).
* **Historical Origin & Commit Trace**: Baseline turn cue drawables were originally added during turn prompt development in `ATT-1450` (Sprint 2026-40.16). While cardinal turns and slight turns were modernized, sharp turns retained legacy incomplete SVG paths that lacked bottom grounding and pointed horizontally ($90^\circ$).
* **Root Reason for Existing Formulation**: The original asset was imported from an unvetted vector snippet where the stem ended at $y=17$ without grounding to $y=20$.
* **Preservation of Core Invariants**: Viewport 24x24, color `#FF1976D2`, and integration with `TurnDirection` enum and `TurnPromptBanner.kt` remain 100% preserved.

---

## 2. Test Specification (TST-UI-324)

### Test Case 1: `testSharpTurnIconsHaveStandardColorAndSafetyHeader` (`TST-UI-324.1`)
* **Scope**: Vector Drawable Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`
* **Preconditions**: `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml` on disk.
* **Action**: Parse XML tree and verify safety header comment and `android:fillColor`.
* **Expected Result**:
  * Both files contain `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->`.
  * `android:fillColor` equals `#FF1976D2`.

### Test Case 2: `testSharpTurnIconsGroundingAndStemWidth` (`TST-UI-324.2`)
* **Scope**: Vector Geometry & Grounding Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`
* **Preconditions**: `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml` on disk.
* **Action**: Verify baseline coordinate grounding and stem width.
* **Expected Result**:
  * `ic_turn_sharp_right.xml` starts at baseline $y=20$ (`M6,20h2`) and closes at $y=20$ (`V20z`).
  * `ic_turn_sharp_left.xml` starts at baseline $y=20$ (`M18,20h-2`) and closes at $y=20$ (`V20z`).
  * Baseline stem width is 2dp (`h2` for right, `h-2` for left).

### Test Case 3: `testTurnSharpLeftIsExactHorizontalReflectionOfTurnSharpRight` (`TST-UI-324.3`)
* **Scope**: Mathematical Symmetry & Vector Path Geometry Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`
* **Preconditions**: `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml` on disk.
* **Action**: Parse path tokens, evaluate coordinate milestones, and compare points.
* **Expected Result**:
  * Identical milestone count.
  * For all milestones $i$: $x_{R,i} + x_{L,i} = 24.0$ ($\pm 0.01$) and $|y_{R,i} - y_{L,i}| < 0.01$.

### Test Case 4: `testAllTurnDirectionDrawablesExistAndHaveStandard24dpViewport` (`TST-UI-324.4`)
* **Scope**: Comprehensive Turn Icon Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`
* **Preconditions**: All 8 `TurnDirection` enum entries.
* **Action**: Verify file existence, root `<vector>`, width/height `24dp`, and viewport `24x24`.
* **Expected Result**: All 8 icons pass contract assertions.

### Test Case 5: 9-Language Localization Audit (`TST-UI-324.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Preconditions**: `turn_cue_sharp_right` and `turn_cue_sharp_left` string definitions.
* **Action**: Execute `TranslationParityTest`.
* **Expected Result**: 100% parity across all 9 application locales with zero missing entries.

### Test Case 6: Clean-Room Regression Suite (`TST-UI-324.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-324.1` | Vector Contract | `ic_turn_sharp_right` & `left` header & color | `REQ-UI-361` (Cl. 1) | Specified |
| `TST-UI-324.2` | Vector Geometry | `ic_turn_sharp_right` & `left` baseline & stem | `REQ-UI-361` (Cl. 1, 2) | Specified |
| `TST-UI-324.3` | Vector Math | `ic_turn_sharp_left` reflection symmetry | `REQ-UI-361` (Cl. 2) | Specified |
| `TST-UI-324.4` | Viewport Contract | All 8 `TurnDirection` XML drawables | `REQ-UI-361` (Cl. 1) | Specified |
| `TST-UI-324.5` | Localization | `TranslationParityTest` | `REQ-UI-361`, `REQ-UI-106` | Specified |
| `TST-UI-324.6` | Clean-Room Suite | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
