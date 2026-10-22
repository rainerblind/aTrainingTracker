# Stage 5 Walkthrough - ATT-3493: Improve vector icons for sharp right and sharp left turn navigation cues

**Ticket**: [ATT-3493](https://rainerblind.atlassian.net/browse/ATT-3493)  
**Stage 5 Subtask**: [ATT-3505](https://rainerblind.atlassian.net/browse/ATT-3505) (`[Test]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-361` (`Verified`)  
**Test Mapping**: `TST-UI-324` (`Verified`)  
**Branch**: `feature/ATT-3493`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Defect Remediation

In `aTrainingTracker`, turn-by-turn route navigation prompts (REQ-MAP-028, REQ-UI-324, REQ-UI-360 / ATT-1450, ATT-2632, ATT-3455, ATT-3494) visually guide athletes along route polylines. When a sharp turn is detected ($120^\circ \le |\Delta\theta| < 155^\circ$), `TurnDirection.SHARP_RIGHT` and `TurnDirection.SHARP_LEFT` are displayed in the cockpit prompt banner.

Prior to ATT-3493, the vector assets for sharp turns suffered from two critical defects:
1. **Unanchored Floating Stem**: In `ic_turn_sharp_right.xml`, the vector path data (`M18,13.5l-5,-5v3.5c-3.31,0 -6,2.69 -6,6v3h2v-3c0,-2.21 1.79,-4 4,-4h5v3.5L18,13.5z`) ended at $y=17$, floating without connection to the standard baseline $y=20$. All other directional cues (`ic_turn_straight`, `ic_turn_slight_right`, `ic_turn_slight_left`, `ic_turn_left`, `ic_turn_right`, and `ic_turn_u_turn`) were firmly grounded at $y=20$.
2. **Conflicting Arrowhead Direction ($90^\circ$ East)**: The legacy arrowhead pointed horizontally East ($90^\circ$), visually conflicting with a normal right turn. An acute turn requires a downward-angled arrowhead ($135^\circ$ South-East $\searrow$ for sharp right; $-135^\circ$ South-West $\swarrow$ for sharp left) to provide unambiguous, immediate recognition.

ATT-3493 resolves both defects with canonical Material Design acute-loop vector geometries:
- Stems rise vertically from baseline $y=20$ with standard 2dp stroke width.
- Acute top loop arches with outer radius $R=6$ and inner radius $r=4$ matching `ic_turn_u_turn.xml`.
- Downward $135^\circ$ / $-135^\circ$ arrowheads complete the rotational progression across all 8 turn directions:
  * Straight ($0^\circ$) $\to$ Slight Right ($45^\circ$) $\to$ Right ($90^\circ$) $\to$ **Sharp Right ($135^\circ$)** $\to$ U-Turn ($180^\circ$)
- Mathematical reflection symmetry across $x=12$ ($x_R + x_L = 24.0, y_R = y_L$) verified across all coordinate milestones.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-361` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*) $\to$ **Status: Verified**
* **Test Mapping**: `TST-UI-324` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*) $\to$ **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Zero Route Guidance Math Regression**: Turn detection heuristics and curvature angle thresholds in `TurnCueDetector.kt`, navigation state transitions in `TurnByTurnNavigationEngine.kt`, and audio alerts in `NavigationAudioAlertManager.kt` remain 100% untouched.
2. **Drop-in Asset Replacement & Resource ID Stability**: File names (`ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml`) and resource IDs (`R.drawable.ic_turn_sharp_right`, `R.drawable.ic_turn_sharp_left`) remain identical. `TurnDirection` enum mappings are completely unaffected.
3. **Viewport & Color Integrity**: Standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`) and `#FF1976D2` fill color token preserved. Safety comment header `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->` included.
4. **Layout Invariance in TurnPromptBanner**: `TurnPromptBanner.kt` renders cue icons via `Modifier.size(48.dp)` with centered aspect-fit scaling, requiring zero padding adjustments.
5. **Zero Full-Suite Regressions**: Full clean-room test suite `./gradlew testDebugUnitTest` executed with 100% pass rate across the entire codebase.

---

## 4. Test Verification Results

### Unit Test Suite: `TurnDirectionIconContractTest.kt`
All 7 unit tests in `TurnDirectionIconContractTest` pass cleanly:

| Test Case | Description | Result |
| :--- | :--- | :--- |
| `testAllTurnDirectionDrawablesExistAndHaveStandard24dpViewport` | Verifies all 8 turn icons declare valid 24x24dp viewport and non-empty pathData | **PASS** |
| `testSlightTurnIconsHaveStandardColorAndSafetyHeader` | Verifies TST-UI-323 safety header and #FF1976D2 fill color on slight turn icons | **PASS** |
| `testSlightTurnIconsGroundingAndStemWidth` | Verifies baseline y=20 grounding and 2dp stem width on slight turn icons | **PASS** |
| `testTurnSlightLeftIsExactHorizontalReflectionOfTurnSlightRight` | Verifies mathematical reflection symmetry (x' = 24 - x) on slight turn icons | **PASS** |
| `testSharpTurnIconsHaveStandardColorAndSafetyHeader` | Verifies TST-UI-324 safety header and #FF1976D2 fill color on sharp turn icons | **PASS** |
| `testSharpTurnIconsGroundingAndStemWidth` | Verifies baseline y=20 grounding (M6,20 / M18,20, V20z) and 2dp stem width (h2 / h-2) on sharp turn icons | **PASS** |
| `testTurnSharpLeftIsExactHorizontalReflectionOfTurnSharpRight` | Verifies milestone-by-milestone mathematical reflection symmetry across all vertices and control points | **PASS** |

### Clean-Room Full Suite Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (0 failures, 0 errors, 0 regressions across all modules).
