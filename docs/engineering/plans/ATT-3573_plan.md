# Stage 3: Implementation Plan - ATT-3573: Refine sharp turn vector icons to reduce visual resemblance to U-turn

**Ticket**: [ATT-3573](https://rainerblind.atlassian.net/browse/ATT-3573)  
**Sub-task**: [ATT-3706](https://rainerblind.atlassian.net/browse/ATT-3706) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `None`  
**Active Sprint**: `2026-42.5`  
**Requirement Mapping**: `REQ-UI-361`  
**Test Mapping**: `TST-UI-324`  
**Branch**: `feature/ATT-3573`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Problem Description & Background

In `aTrainingTracker`, turn-by-turn route navigation prompts (`REQ-MAP-028`, `REQ-UI-324`, `REQ-UI-360`, `REQ-UI-361`) visually guide athletes along route polylines. When a sharp turn is detected ($120^\circ \le |\Delta\theta| < 155^\circ$), `TurnDirection.SHARP_RIGHT` and `TurnDirection.SHARP_LEFT` are displayed in the cockpit prompt banner (`TurnPromptBanner.kt`).

However, the vector assets introduced in commit `39610f62` (ATT-3493) feature a full $180^\circ$ circular semicircle dome across the top spanning $12\text{dp}$ to $x=18$, followed by a vertical downward right shaft and a vertical downward-pointing arrowhead ($180^\circ$, South $\downarrow$). As a result, athletes glancing at cockpit navigation cues frequently misinterpret sharp turns as **U-turns** (`TurnDirection.U_TURN`), creating confusion and false turn-around reactions during rides and runs.

This implementation plan specifies the drop-in replacement of the vector path data in `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml` with canonical Material Design vector geometries featuring an acute ~135° deflection bend, diagonal downward-pointing arrowheads ($\searrow$ and $\swarrow$), uniform 2dp stroke width, baseline $y=20$ grounding, and strict mathematical reflection symmetry across $x=12$.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-361` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*)
* **Test Mapping**: `TST-UI-324` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*)
* **Living Documentation**: `docs/requirements.md` (amended) and `docs/tests.md` (updated).

---

## 3. UI Consistency & Design Guidelines (Rule 23)

In accordance with Governance Rule 23 and `docs/design_guidelines.md` (Section 5.5, *Directional & Navigation Iconography Family Harmony*):

* **Closest Existing References**:
  - `ic_turn_slight_right.xml` / `ic_turn_slight_left.xml` (ATT-3455): 45° angular deflection, grounded baseline at $y=20$.
  - `ic_turn_right.xml` / `ic_turn_left.xml`: 90° cardinal turn, grounded baseline at $y=20$.
  - `ic_turn_u_turn.xml`: 180° loop, grounded baseline at $y=21$.
  - `TurnCueIconsGalleryPreview` in `TurnPromptBanner.kt`: Compose preview gallery displaying all 8 directional symbols side-by-side in Light and Dark themes.
* **Design Token Compliance**:
  - Standard 24x24dp viewport (`android:width="24dp"`, `android:height="24dp"`, `android:viewportWidth="24"`, `android:viewportHeight="24"`).
  - Consistent bottom baseline grounding at $y=20$ (`M6,20h2` and `V20z` for sharp right; `M18,20h-2` and `V20z` for sharp left).
  - Uniform stroke width of 2dp throughout stem ($x \in [6, 8]$), apex bend ($r_{out} - r_{in} = 4.5 - 2.5 = 2.0\text{dp}$), and diagonal exit shaft.
  - Fill color token `#FF1976D2` preserved for XML and Layoutlib/Compose preview compatibility.
  - Arrowhead proportions match standard Material Design directional arrows with symmetric barb wings perpendicular to the trajectory.
* **Rotational Progression Harmony**:
  - `STRAIGHT`: $0^\circ$ ($\uparrow$)
  - `SLIGHT_RIGHT`: $+45^\circ$ ($\nearrow$)
  - `RIGHT`: $+90^\circ$ ($\rightarrow$)
  - **`SHARP_RIGHT`: $+135^\circ$ ($\searrow$)**
  - `U_TURN`: $+180^\circ$ ($\downarrow$)
  - **`SHARP_LEFT`: $-135^\circ$ ($\swarrow$)**
  - `LEFT`: $-90^\circ$ ($\leftarrow$)
  - `SLIGHT_LEFT`: $-45^\circ$ ($\nwarrow$)
* **Justification for New Path**:
  - The new path replaces the erroneous 180° dome top and downward arrow with a true 135° acute bend and diagonal arrow, eliminating visual ambiguity with U-turn while preserving 100% icon family harmony.

---

## 4. System Invariants & Preserved Behavior

1. **Zero Route Guidance Math Regression**: Turn detection heuristics and curvature angle thresholds ($120^\circ \dots 155^\circ$ in `TurnCueDetector.kt`), route navigation state machines (`TurnByTurnNavigationEngine.kt`), and auditory chimes (`NavigationAudioAlertManager.kt`) remain 100% untouched.
2. **Drop-in Asset Replacement & Resource ID Stability**: File names (`ic_turn_sharp_right.xml`, `ic_turn_sharp_left.xml`) and Android resource IDs (`R.drawable.ic_turn_sharp_right`, `R.drawable.ic_turn_sharp_left`) remain identical. `TurnDirection` enum entries (`iconResId`, `displayNameResId`) require zero changes.
3. **Viewport & Color Integrity**: Both vector drawables maintain standard 24x24dp viewport, `#FF1976D2` fill color token, and explicit contract safety header comments: `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->`.
4. **Mathematical Reflection Parity ($x_R + x_L = 24.00, y_R = y_L$)**: `ic_turn_sharp_left.xml` is the exact horizontal reflection across $x=12$ of `ic_turn_sharp_right.xml` for all 22 milestone vertices.
5. **Clean-Room Regression Invariant**: 100% full suite test pass rate (`./gradlew testDebugUnitTest`).

---

## 5. Proposed Construction Details

### 5.1 Canonical Vector Drawables

#### `app/src/main/res/drawable/ic_turn_sharp_right.xml`
```xml
<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF1976D2"
        android:pathData="M6,20h2V8.5c0,-1.38 1.12,-2.5 2.5,-2.5c0.66,0 1.3,0.26 1.77,0.73l2.5,2.5l-2.5,2.5l6.71,0.3l-0.3,-6.71l-2.5,2.5l-2.5,-2.5c-0.84,-0.84 -1.99,-1.32 -3.18,-1.32c-2.49,0 -4.5,2.01 -4.5,4.5V20z" />
</vector>
```

#### `app/src/main/res/drawable/ic_turn_sharp_left.xml`
```xml
<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF1976D2"
        android:pathData="M18,20h-2V8.5c0,-1.38 -1.12,-2.5 -2.5,-2.5c-0.66,0 -1.3,0.26 -1.77,0.73l-2.5,2.5l2.5,2.5l-6.71,0.3l0.3,-6.71l2.5,2.5l2.5,-2.5c0.84,-0.84 1.99,-1.32 3.18,-1.32c2.49,0 4.5,2.01 4.5,4.5V20z" />
</vector>
```

### 5.2 Mathematical Reflection Symmetry Proof Table ($x_R + x_L = 24.00, y_R = y_L$)

| Step | Right Command & End $(x_R, y_R)$ | Left Command & End $(x_L, y_L)$ | $x_R + x_L$ | $y_R - y_L$ | Status |
| :---: | :--- | :--- | :---: | :---: | :---: |
| 0 | `M6,20` $\to (6.00, 20.00)$ | `M18,20` $\to (18.00, 20.00)$ | **24.00** | **0.00** | Validated |
| 1 | `h2` $\to (8.00, 20.00)$ | `h-2` $\to (16.00, 20.00)$ | **24.00** | **0.00** | Validated |
| 2 | `V8.5` $\to (8.00, 8.50)$ | `V8.5` $\to (16.00, 8.50)$ | **24.00** | **0.00** | Validated |
| 3-5 | `c0,-1.38 1.12,-2.5 2.5,-2.5` $\to (10.50, 6.00)$ | `c0,-1.38 -1.12,-2.5 -2.5,-2.5` $\to (13.50, 6.00)$ | **24.00** | **0.00** | Validated |
| 6-8 | `c0.66,0 1.3,0.26 1.77,0.73` $\to (12.27, 6.73)$ | `c-0.66,0 -1.3,0.26 -1.77,0.73` $\to (11.73, 6.73)$ | **24.00** | **0.00** | Validated |
| 9 | `l2.5,2.5` $\to (14.77, 9.23)$ | `l-2.5,2.5` $\to (9.23, 9.23)$ | **24.00** | **0.00** | Validated |
| 10 | `l-2.5,2.5` $\to (12.27, 11.73)$ | `l2.5,2.5` $\to (11.73, 11.73)$ | **24.00** | **0.00** | Validated |
| 11 | `l6.71,0.3` $\to (18.98, 12.03)$ | `l-6.71,0.3` $\to (5.02, 12.03)$ | **24.00** | **0.00** | Validated |
| 12 | `l-0.3,-6.71` $\to (18.68, 5.32)$ | `l0.3,-6.71` $\to (5.32, 5.32)$ | **24.00** | **0.00** | Validated |
| 13 | `l-2.5,2.5` $\to (16.18, 7.82)$ | `l2.5,2.5` $\to (7.82, 7.82)$ | **24.00** | **0.00** | Validated |
| 14 | `l-2.5,-2.5` $\to (13.68, 5.32)$ | `l2.5,-2.5` $\to (10.32, 5.32)$ | **24.00** | **0.00** | Validated |
| 15-17 | `c-0.84,-0.84 -1.99,-1.32 -3.18,-1.32` $\to (10.50, 4.00)$ | `c0.84,-0.84 1.99,-1.32 3.18,-1.32` $\to (13.50, 4.00)$ | **24.00** | **0.00** | Validated |
| 18-20 | `c-2.49,0 -4.5,2.01 -4.5,4.5` $\to (6.00, 8.50)$ | `c2.49,0 4.5,2.01 4.5,4.5` $\to (18.00, 8.50)$ | **24.00** | **0.00** | Validated |
| 21 | `V20z` $\to (6.00, 20.00)$ | `V20z` $\to (18.00, 20.00)$ | **24.00** | **0.00** | Validated |

All 22 milestone vertices evaluate with $x_R + x_L = 24.00 \pm 0.00$ and $y_R = y_L \pm 0.00$.

---

## 6. Atomic Implementation Steps

### Step 1: Update Vector Drawables
- Modify `app/src/main/res/drawable/ic_turn_sharp_right.xml` with Candidate D vector path.
- Modify `app/src/main/res/drawable/ic_turn_sharp_left.xml` with horizontally reflected vector path.

### Step 2: Contract Test Verification
- Run contract unit test:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.routes.TurnDirectionIconContractTest
  ```
- Verify all 5 test cases pass:
  - `testAllTurnDirectionDrawablesExistAndHaveStandard24dpViewport`
  - `testSharpTurnIconsHaveStandardColorAndSafetyHeader`
  - `testSharpTurnIconsGroundingAndStemWidth`
  - `testTurnSharpLeftIsExactHorizontalReflectionOfTurnSharpRight`
  - `testTurnPromptBannerContainsPrivateGalleryAndDualThemePreviews`

### Step 3: Visual Preview Inspection & Walkthrough Image Generation
- Verify rendered icon set in `TurnCueIconsGalleryPreview` (Light and Dark themes).
- Generate comparison graphic in `docs/engineering/walkthroughs/img/ATT-3573_sharp_turn_icons.png`.

### Step 4: Full Clean-Room Regression Suite Execution
- Run full unit test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- Assert 100% passing tests with zero regressions.

---

## 7. Verification & Rollback Strategy

* **Targeted Unit Test Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.routes.TurnDirectionIconContractTest
  ```
* **Full Clean-Room Regression**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Rollback Plan**:
  Since changes are isolated purely to `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml`, rolling back involves `git checkout origin/sprint/2026-42.5 -- app/src/main/res/drawable/ic_turn_sharp_*.xml` with zero impact on database schemas or Kotlin business logic.
