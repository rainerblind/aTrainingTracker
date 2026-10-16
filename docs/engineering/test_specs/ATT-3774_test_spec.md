# Stage 2 Requirement & Test Specification: ATT-3774 — Unify arrowhead style and size across all 8 turn cue vector icons and fix Turn Left arrowhead centering

## 1. Requirement Specification (`REQ-UI-389`)

### `REQ-UI-389`: Unified Filled-Triangle Arrowhead Geometry, Baseline Grounding & Reflection Symmetry Across All 8 Turn Cue Vector Icons
- **Parent Ticket**: `ATT-3774` (amending `REQ-UI-360` and `REQ-UI-361`)
- **Statement**: The system SHALL unify all 8 `TurnDirection` vector drawables (`ic_turn_left.xml`, `ic_turn_right.xml`, `ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, `ic_turn_sharp_right.xml`, `ic_turn_straight.xml`, `ic_turn_u_turn.xml`) so that every icon uses a single closed `<path>` grounded at baseline $y = 20.0\text{ dp}$ with uniform $2.0\text{ dp}$ stem/shaft thickness and an identical filled right-isosceles triangle arrowhead ($W = 8.0\text{ dp}$ base width, $D = 4.0\text{ dp}$ tip depth, $o = 3.0\text{ dp}$ wing overhang on each side of the $2.0\text{ dp}$ shaft), and SHALL center `ic_turn_left.xml`'s arrowhead strictly on the horizontal shaft axis $y = 10.0\text{ dp}$ in exact reflection symmetry with `ic_turn_right.xml`:
  1. **Unified Filled Right-Isosceles Triangle Arrowhead (`REQ-UI-389.1`)**:
     - Cardinal icons (`ic_turn_left.xml`, `ic_turn_right.xml`, `ic_turn_straight.xml`, `ic_turn_u_turn.xml`): perpendicular wing overhang $o = 3.0\text{ dp}$ (`v3`/`v-3` or `h3`/`h-3`) on each side of the $2.0\text{ dp}$ shaft, total base width $W = 8.0\text{ dp}$, and tip legs `(±4.0dp, ±4.0dp)` ($D = 4.0\text{ dp}$, outer wing length $L = 4\sqrt{2} \approx 5.66\text{ dp}$).
     - Diagonal $45^\circ$/$135^\circ$ icons (`ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, `ic_turn_sharp_right.xml`): perpendicular wing overhang `(±2.12dp, ±2.12dp)` ($o = 3.0\text{ dp}$) on each side of the $2.0\text{ dp}$ diagonal shaft and axis-aligned outer wing legs `±5.66dp` ($W = 8.0\text{ dp}, D = 4.0\text{ dp}$).
  2. **Turn Left & Turn Right Vertical Centering & Reflection Symmetry (`REQ-UI-389.2`)**:
     - `ic_turn_right.xml` (`M7,20h2v-8c0,-0.55 0.45,-1 1,-1h5v3l4,-4l-4,-4v3H10c-1.66,0 -3,1.34 -3,3V20z`) and `ic_turn_left.xml` (`M17,20h-2v-8c0,-0.55 -0.45,-1 -1,-1H9v3l-4,-4l4,-4v3h5c1.66,0 3,1.34 3,3V20z`) SHALL center their horizontal shaft on $y \in [9, 11]$ (axis $y = 10.0$), place their arrowhead wings at $y = 6.0$ and $y = 14.0$, place their tips at $(19.0, 10.0)$ and $(5.0, 10.0)$, and satisfy exact horizontal reflection symmetry ($x_L + x_R = 24.0, y_L = y_R$).
  3. **Uniform Baseline Grounding & Single-Path Construction (`REQ-UI-389.3`)**:
     - All 8 `ic_turn_*.xml` icons SHALL ground their vertical base stem at $y = 20.0\text{ dp}$ with $2.0\text{ dp}$ stem width and consist of a single closed `<path>` (`android:fillColor="#FF1976D2"`) inside a `24dp x 24dp` (`viewportWidth="24"`, `viewportHeight="24"`) viewport.

### Acceptance Criteria (Given-When-Then)
- **AC-1 (Unified Arrowhead Style & Dimensions)**:
  - *Given* all 8 `ic_turn_*.xml` vector drawables,
  - *When* parsed and evaluated by `TurnDirectionIconContractTest.kt`,
  - *Then* every icon has a single closed `<path>` grounded at $y = 20.0$ with $2.0\text{ dp}$ stem width and a filled right-isosceles triangle arrowhead of base width $W = 8.0\text{ dp} \pm 0.05$, tip depth $D = 4.0\text{ dp} \pm 0.05$, and wing overhang $o = 3.0\text{ dp} \pm 0.05$.
- **AC-2 (Turn Left Centering & 3-Pair Reflection Symmetry)**:
  - *Given* `ic_turn_left.xml` and `ic_turn_right.xml`,
  - *When* evaluated by `TurnDirectionIconContractTest.kt`,
  - *Then* both arrowheads have upper wing $y = 6.0$, lower wing $y = 14.0$, and tip $y = 10.0$ (strictly centered on shaft $y \in [9, 11]$), and every milestone vertex across `LEFT`/`RIGHT`, `SLIGHT_LEFT`/`SLIGHT_RIGHT`, and `SHARP_LEFT`/`SHARP_RIGHT` satisfies $x_L + x_R = 24.0 \pm 0.01$ and $y_L = y_R \pm 0.01$.

---

## 2. Test Specification (`TST-UI-352`)

### `TST-UI-352`: Unified 8-Icon Turn Cue Arrowhead Geometry, Baseline Grounding & Reflection Symmetry Verification Suite
- **Test File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`
- **Test Cases**:
  1. `testAllTurnDirectionDrawablesExistAndHaveStandard24dpViewport`:
     - Asserts all 8 `ic_turn_*.xml` files exist, have `24dp x 24dp` (`24x24`) viewport, and contain a single `<path>` with `fillColor="#FF1976D2"`.
  2. `testAll8TurnIconsAreGroundedAtBaselineY20With2dpStemAndUnified8x4FilledTriangleArrowhead`:
     - Asserts every one of the 8 icons ends at `V20z` (grounded at $y = 20.0$) with a $2.0\text{ dp}$ stem cut (`h2` or `h-2`), contains a single subpath (`M` count == 1), and encodes the unified $W = 8.0\text{ dp}, D = 4.0\text{ dp}, o = 3.0\text{ dp}$ filled triangle arrowhead (`l-4,-4l4,-4` / `l4,-4l-4,-4` / `l-4,-4l-4,4` / `l-4,4l-4,-4` for cardinal icons; `2.12` overhang and `5.66` legs for diagonal icons).
  3. `testTurnLeftAndTurnRightArrowheadVerticalCenteringAndExactReflectionSymmetry`:
     - Evaluates absolute milestones of `ic_turn_left.xml` and `ic_turn_right.xml`, asserting upper wing $y = 6.0$, lower wing $y = 14.0$, tip $y = 10.0$ (midpoint of shaft $y \in [9, 11]$), and $x_L + x_R = 24.0 \pm 0.01, y_L = y_R \pm 0.01$ across all vertices.
  4. `testTurnSlightLeftIsExactHorizontalReflectionOfTurnSlightRight` & `testTurnSharpLeftIsExactHorizontalReflectionOfTurnSharpRight`:
     - Asserts exact horizontal reflection symmetry ($x_L + x_R = 24.0 \pm 0.01, y_L = y_R \pm 0.01$) across all milestone vertices for slight and sharp turns.

---

## 3. Bidirectional Traceability Matrix

| Requirement ID | Sub-Clause | Test ID | Test Method (`TurnDirectionIconContractTest.kt`) |
| :--- | :--- | :--- | :--- |
| `REQ-UI-389` | `REQ-UI-389.1`, `REQ-UI-389.3` | `TST-UI-352` | `testAll8TurnIconsAreGroundedAtBaselineY20With2dpStemAndUnified8x4FilledTriangleArrowhead` |
| `REQ-UI-389` | `REQ-UI-389.2` | `TST-UI-352` | `testTurnLeftAndTurnRightArrowheadVerticalCenteringAndExactReflectionSymmetry` |
| `REQ-UI-389` | `REQ-UI-389.1`, `REQ-UI-389.2` | `TST-UI-352` | `testTurnSlightLeftIsExactHorizontalReflectionOfTurnSlightRight`, `testTurnSharpLeftIsExactHorizontalReflectionOfTurnSharpRight` |
