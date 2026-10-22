# Stage 3: Implementation Plan - ATT-3493: Improve vector icons for sharp right and sharp left turn navigation cues

**Ticket**: [ATT-3493](https://rainerblind.atlassian.net/browse/ATT-3493)  
**Sub-task**: [ATT-3503](https://rainerblind.atlassian.net/browse/ATT-3503) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-361`  
**Test Mapping**: `TST-UI-324`  
**Branch**: `feature/ATT-3493`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Description & Background

In `aTrainingTracker`, turn-by-turn route navigation prompts (REQ-MAP-028, REQ-UI-324, REQ-UI-360 / ATT-1450, ATT-2632, ATT-3455, ATT-3494) visually guide athletes along route polylines. When a sharp turn is detected ($120^\circ \le |\Delta\theta| < 155^\circ$), `TurnDirection.SHARP_RIGHT` and `TurnDirection.SHARP_LEFT` are displayed in the cockpit prompt banner.

However, the legacy vector assets for sharp turns suffer from two critical visual and architectural defects:
1. **Unanchored Floating Stem**: In `ic_turn_sharp_right.xml`, the vector path (`M18,13.5l-5,-5v3.5c-3.31,0 -6,2.69 -6,6v3h2v-3c0,-2.21 1.79,-4 4,-4h5v3.5L18,13.5z`) contains only a short 3dp vertical stem ending at $y=17$, floating high above the standard baseline $y=20$. All other 6 directional cues (`ic_turn_straight`, `ic_turn_slight_right`, `ic_turn_slight_left`, `ic_turn_left`, `ic_turn_right`, and `ic_turn_u_turn`) are firmly grounded at baseline $y=20$.
2. **Conflicting Arrowhead Direction ($90^\circ$ East)**: The legacy arrowhead points horizontally East ($90^\circ$), identical to a standard right turn. An acute turn ($> 120^\circ$) requires a downward-pointing arrowhead ($135^\circ$ South-East $\searrow$ for sharp right; $-135^\circ$ South-West $\swarrow$ for sharp left) to provide unambiguous, at-a-glance cognitive distinction for athletes navigating on bike handlebars or on foot.

This plan specifies the drop-in replacement of `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml` with canonical Material Design vector paths, grounded at baseline $y=20$, featuring standard 2dp stroke width, acute loop top arching, downward $135^\circ$ / $-135^\circ$ arrowheads, and mathematical horizontal reflection symmetry ($x' = 24 - x$), enforced via automated contract testing in `TurnDirectionIconContractTest.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-361` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*)
* **Test Mapping**: `TST-UI-324` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Route Guidance Math Regression**: Turn detection heuristics and curvature angle thresholds ($120^\circ \dots 155^\circ$ in `TurnCueDetector.kt`), route navigation state machines (`TurnByTurnNavigationEngine.kt`), and auditory chimes (`NavigationAudioAlertManager.kt`) remain 100% untouched.
2. **Drop-in Asset Replacement & Resource ID Stability**: File names (`ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml`) and Android resource IDs (`R.drawable.ic_turn_sharp_right`, `R.drawable.ic_turn_sharp_left`) remain identical. `TurnDirection` enum entries (`iconResId`, `displayNameResId`) require zero changes.
3. **Viewport & Color Integrity**: Both vector drawables maintain standard 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`), `#FF1976D2` fill color token (matching Compose and preview rendering requirements), and explicit contract safety header comments: `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->`.
4. **Layout Invariance in TurnPromptBanner**: `TurnPromptBanner.kt` renders turn cue icons inside a 48dp container using centered aspect-fit scaling (`Modifier.size(48.dp)`). Replacing the vector geometry requires zero layout padding modifications.
5. **Rotational Progression Harmony**: The sharp turn icons complete the unified angular sequence of the turn cue family:
   * Straight: $0^\circ$ ($\uparrow$)
   * Slight Right: $45^\circ$ ($\nearrow$)
   * Right: $90^\circ$ ($\rightarrow$)
   * **Sharp Right: $135^\circ$ ($\searrow$)**
   * U-Turn: $180^\circ$ ($\downarrow$)
   * **Sharp Left: $-135^\circ$ ($\swarrow$)**
   * Left: $-90^\circ$ ($\leftarrow$)
   * Slight Left: $-45^\circ$ ($\nwarrow$)
6. **Mathematical Reflection Parity ($x_R + x_L = 24.0, y_R = y_L$)**: `ic_turn_sharp_left.xml` is the exact horizontal reflection across $x=12$ of `ic_turn_sharp_right.xml`.
7. **Clean-Room Regression Invariant**: 100% full suite test pass rate (`./gradlew testDebugUnitTest`).

---

## 4. Proposed Architectural Changes

### Component 1: Canonical Vector Drawables (`ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml`)

* Update `app/src/main/res/drawable/ic_turn_sharp_right.xml`:
  ```xml
  <!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->
  <vector xmlns:android="http://schemas.android.com/apk/res/android"
      android:width="24dp"
      android:height="24dp"
      android:viewportWidth="24"
      android:viewportHeight="24">
      <path
          android:fillColor="#FF1976D2"
          android:pathData="M6,20h2V9c0,-2.21 1.79,-4 4,-4s4,1.79 4,4v2.59l-1.59,-1.59L13,11.41l4,4l4,-4l-1.41,-1.41L18,11.59V9c0,-3.31 -2.69,-6 -6,-6S6,5.69 6,9V20z" />
  </vector>
  ```

* Update `app/src/main/res/drawable/ic_turn_sharp_left.xml`:
  ```xml
  <!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->
  <vector xmlns:android="http://schemas.android.com/apk/res/android"
      android:width="24dp"
      android:height="24dp"
      android:viewportWidth="24"
      android:viewportHeight="24">
      <path
          android:fillColor="#FF1976D2"
          android:pathData="M18,20h-2V9c0,-2.21 -1.79,-4 -4,-4s-4,1.79 -4,4v2.59l1.59,-1.59L11,11.41l-4,4l-4,-4l1.41,-1.41L6,11.59V9c0,-3.31 2.69,-6 6,-6s6,2.69 6,9V20z" />
  </vector>
  ```

#### Detailed Mathematical Reflection Proof Table ($x_R + x_L = 24.00, y_R = y_L$)

| Step | Right Command & Absolute End $(x_R, y_R)$ | Left Command & Absolute End $(x_L, y_L)$ | $x_R + x_L$ | $y_R - y_L$ | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Start** | `M6,20` $\to (6.00, 20.00)$ | `M18,20` $\to (18.00, 20.00)$ | **24.00** | **0.00** | Validated |
| **Stem width** | `h2` $\to (8.00, 20.00)$ | `h-2` $\to (16.00, 20.00)$ | **24.00** | **0.00** | Validated |
| **Stem rise** | `V9` $\to (8.00, 9.00)$ | `V9` $\to (16.00, 9.00)$ | **24.00** | **0.00** | Validated |
| **Inner arch 1** | `c0,-2.21 1.79,-4 4,-4` $\to (12.00, 5.00)$ | `c0,-2.21 -1.79,-4 -4,-4` $\to (12.00, 5.00)$ | **24.00** | **0.00** | Validated |
| **Inner arch 2** | `s4,1.79 4,4` $\to (16.00, 9.00)$ | `s-4,1.79 -4,4` $\to (8.00, 9.00)$ | **24.00** | **0.00** | Validated |
| **Right shaft** | `v2.59` $\to (16.00, 11.59)$ | `v2.59` $\to (8.00, 11.59)$ | **24.00** | **0.00** | Validated |
| **Inner wing** | `l-1.59,-1.59` $\to (14.41, 10.00)$ | `l1.59,-1.59` $\to (9.59, 10.00)$ | **24.00** | **0.00** | Validated |
| **Inner notch** | `L13,11.41` $\to (13.00, 11.41)$ | `L11,11.41` $\to (11.00, 11.41)$ | **24.00** | **0.00** | Validated |
| **Arrow tip** | `l4,4` $\to (17.00, 15.41)$ | `l-4,4` $\to (7.00, 15.41)$ | **24.00** | **0.00** | Validated |
| **Outer wing** | `l4,-4` $\to (21.00, 11.41)$ | `l-4,-4` $\to (3.00, 11.41)$ | **24.00** | **0.00** | Validated |
| **Outer notch** | `l-1.41,-1.41` $\to (19.59, 10.00)$ | `l1.41,-1.41` $\to (4.41, 10.00)$ | **24.00** | **0.00** | Validated |
| **Shaft return** | `L18,11.59` $\to (18.00, 11.59)$ | `L6,11.59` $\to (6.00, 11.59)$ | **24.00** | **0.00** | Validated |
| **Shaft up** | `V9` $\to (18.00, 9.00)$ | `V9` $\to (6.00, 9.00)$ | **24.00** | **0.00** | Validated |
| **Outer arch 1** | `c0,-3.31 -2.69,-6 -6,-6` $\to (12.00, 3.00)$ | `c0,-3.31 2.69,-6 6,-6` $\to (12.00, 3.00)$ | **24.00** | **0.00** | Validated |
| **Outer arch 2** | `S6,5.69 6,9` $\to (6.00, 9.00)$ | `s6,2.69 6,9` $\to (18.00, 9.00)$ | **24.00** | **0.00** | Validated |
| **Stem down** | `V20z` $\to (6.00, 20.00)$ | `V20z` $\to (18.00, 20.00)$ | **24.00** | **0.00** | Validated |

All 16 milestone vertices and control points reflect with mathematical precision across $x=12.00$.

---

### Component 2: Unit & Contract Test Suite Extension (`TurnDirectionIconContractTest.kt`)

* Extend `evaluateAbsoluteMilestones` in `TurnDirectionIconContractTest.kt` to parse smooth cubic Bézier commands (`s` and `S`):
  * `s`: relative smooth cubic curve (`tokens[i++]` $dx_2$, `tokens[i++]` $dy_2$, `tokens[i++]` $dx$, `tokens[i++]` $dy$). Reflected first control point $C_1 = (x + (x - cpX_{prev}), y + (y - cpY_{prev}))$.
  * `L`: absolute line command (`tokens[i++]` $x$, `tokens[i++]` $y$).
* Add dedicated test methods:
  * `testSharpTurnIconsHaveStandardColorAndSafetyHeader()`: Asserts safety comment `DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324` and `android:fillColor="#FF1976D2"`.
  * `testSharpTurnIconsGroundingAndStemWidth()`: Asserts baseline grounding at $y=20$ (`M6,20` / `M18,20`, `V20z`) and 2dp stem width (`h2` / `h-2`).
  * `testTurnSharpLeftIsExactHorizontalReflectionOfTurnSharpRight()`: Asserts horizontal reflection ($x_R + x_L = 24.0, y_R = y_L$) across all coordinate milestones.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Sharp Right Vector Asset
* **Target File**: `app/src/main/res/drawable/ic_turn_sharp_right.xml`
* **Changes**: Drop-in replacement with canonical acute loop path data, baseline $y=20$ grounding, downward $135^\circ$ arrowhead, and contract safety header comment `TST-UI-324`.

### Step 2: Update Sharp Left Vector Asset
* **Target File**: `app/src/main/res/drawable/ic_turn_sharp_left.xml`
* **Changes**: Drop-in replacement with horizontally mirrored acute loop path data, baseline $y=20$ grounding, downward $-135^\circ$ arrowhead, and contract safety header comment `TST-UI-324`.

### Step 3: Enhance Milestone Evaluator & Add Contract Tests
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`
* **Changes**:
  * Enhance `evaluateAbsoluteMilestones` with `s`, `S`, and `L` support.
  * Add `testSharpTurnIconsHaveStandardColorAndSafetyHeader()`.
  * Add `testSharpTurnIconsGroundingAndStemWidth()`.
  * Add `testTurnSharpLeftIsExactHorizontalReflectionOfTurnSharpRight()`.

### Step 4: Run Targeted Contract Tests
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.TurnDirectionIconContractTest"`

### Step 5: Clean-Room Regression Test Run
* **Command**: `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**:
  * `TurnDirectionIconContractTest` passes 100%.
  * Full suite regression `./gradlew testDebugUnitTest` passes 100% with zero regressions.
* **Rollback Plan**:
  * Work is isolated on `feature/ATT-3493`. Reverting git branch restores pristine sprint baseline.
