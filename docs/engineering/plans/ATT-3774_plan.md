# Stage 3 Architecture & Implementation Plan: ATT-3774 — Unify arrowhead style and size across all 8 turn cue vector icons and fix Turn Left arrowhead centering

## 1. Architectural Decomposition (SWE.2)

### 1.1 Resource Layer (`app/src/main/res/drawable/ic_turn_*.xml`)
All 8 turn cue vector drawables (`ic_turn_left.xml`, `ic_turn_right.xml`, `ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, `ic_turn_sharp_right.xml`, `ic_turn_straight.xml`, `ic_turn_u_turn.xml`) are pure Android `VectorDrawable` XML assets (`android:width="24dp"`, `android:height="24dp"`, `android:viewportWidth="24"`, `android:viewportHeight="24"`, `android:fillColor="#FF1976D2"`).
Each file will be updated to use a single closed `<path>` (`count('M') == 1`, ending in `V20z`) grounded at $y = 20.0\text{ dp}$ with $2.0\text{ dp}$ stem/shaft thickness and a unified filled right-isosceles triangle arrowhead ($W = 8.0\text{ dp}$ base width, $D = 4.0\text{ dp}$ tip depth, $o = 3.0\text{ dp}$ wing overhang on each side of the $2.0\text{ dp}$ shaft, outer wing edge length $L = 4\sqrt{2} \approx 5.65685\text{ dp}$, encoded to 2 decimal places as `5.66` with overhang $3/\sqrt{2} \approx 2.12132\text{ dp}$ encoded as `2.12`):

| Drawable | Angle | Bounding Box $[x_{\min}, x_{\max}] \times [y_{\min}, y_{\max}]$ | Canonical `<path android:pathData="...">` |
| :--- | :---: | :---: | :--- |
| `ic_turn_left.xml` | $-90^\circ$ ($\leftarrow$) | $[5.0, 17.0] \times [6.0, 20.0]$ | `M17,20h-2v-8c0,-0.55 -0.45,-1 -1,-1H9v3l-4,-4l4,-4v3h5c1.66,0 3,1.34 3,3V20z` |
| `ic_turn_right.xml` | $+90^\circ$ ($\rightarrow$) | $[7.0, 19.0] \times [6.0, 20.0]$ | `M7,20h2v-8c0,-0.55 0.45,-1 1,-1h5v3l4,-4l-4,-4v3H10c-1.66,0 -3,1.34 -3,3V20z` |
| `ic_turn_slight_left.xml` | $-45^\circ$ ($\nwarrow$) | $[7.37, 15.0] \times [5.39, 20.0]$ | `M13,20h2v-7.58c0,-0.53 -0.21,-1.04 -0.59,-1.41l-3.5,-3.5l2.12,-2.12l-5.66,0l0,5.66l2.12,-2.12l3.5,3.5V20z` |
| `ic_turn_slight_right.xml` | $+45^\circ$ ($\nearrow$) | $[9.0, 16.63] \times [5.39, 20.0]$ | `M11,20h-2v-7.58c0,-0.53 0.21,-1.04 0.59,-1.41l3.5,-3.5l-2.12,-2.12l5.66,0l0,5.66l-2.12,-2.12l-3.5,3.5V20z` |
| `ic_turn_sharp_left.xml` | $-135^\circ$ ($\swarrow$) | $[5.69, 18.0] \times [4.0, 20.0]$ | `M18,20h-2V8.5c0,-1.38 -1.12,-2.5 -2.5,-2.5c-0.66,0 -1.3,0.26 -1.77,0.73l-2.5,2.5l2.12,2.12l-5.66,0l0,-5.66l2.12,2.12l2.5,-2.5c0.84,-0.84 1.99,-1.32 3.18,-1.32c2.49,0 4.5,2.01 4.5,4.5V20z` |
| `ic_turn_sharp_right.xml` | $+135^\circ$ ($\searrow$) | $[6.0, 18.31] \times [4.0, 20.0]$ | `M6,20h2V8.5c0,-1.38 1.12,-2.5 2.5,-2.5c-0.66,0 -1.3,0.26 1.77,0.73l2.5,2.5l-2.12,2.12l5.66,0l0,-5.66l-2.12,2.12l-2.5,-2.5c-0.84,-0.84 -1.99,-1.32 -3.18,-1.32c-2.49,0 -4.5,2.01 -4.5,4.5V20z` |
| `ic_turn_straight.xml` | $0^\circ$ ($\uparrow$) | $[8.0, 16.0] \times [4.0, 20.0]$ | `M11,20h2V8h3l-4,-4l-4,4h3V20z` |
| `ic_turn_u_turn.xml` | $180^\circ$ ($\downarrow$) | $[3.0, 18.0] \times [3.0, 20.0]$ | `M18,20h-2V9c0,-2.21 -1.79,-4 -4,-4S8,6.79 8,9v7h3l-4,4l-4,-4h3V9c0,-3.31 2.69,-6 6,-6s6,2.69 6,6V20z` |

### 1.2 Viewport Clearance & `TurnPromptBanner` Layout Stability Audit
- Every single coordinate across all 8 icons is strictly bounded within $[3.0, 19.0] \times [3.0, 20.0]$ inside the $[0, 24] \times [0, 24]$ viewport, guaranteeing at least $3.0\text{ dp}$ of internal viewport padding on all sides (improving upon the previous `ic_turn_u_turn.xml` which reached $y = 23.0$ and `ic_turn_left.xml`/`ic_turn_right.xml` which reached $x = 4.0$ / $x = 20.0$).
- In `TurnPromptBanner.kt`, the icon is rendered via `Icon(..., modifier = Modifier.size(36.dp))` and in `TurnCueIconsGallery` via `Icon(..., modifier = Modifier.size(36.dp))`. Because the `24dp x 24dp` intrinsic size and `24x24` viewport are strictly unchanged and all path geometry stays within $[3.0, 20.0]$, there is zero risk of clipping or layout shift in `TurnPromptBanner`.

### 1.3 Programmatic Validation & Visual Regression Strategy (`TurnDirectionIconContractTest.kt`)
1. **Programmatic Single-Path, Viewport, FillColor & Canonical Constant Validation**:
   - `TurnDirectionIconContractTest.kt` parses all 8 XML files using DOM (`DocumentBuilderFactory`) and evaluates their `android:pathData` command streams into absolute vertex coordinates (`evaluateAbsoluteMilestones`, supporting `M`, `V`, `v`, `H`, `h`, `L`, `l`, `c`, `S`, `s`, `z`, `Z`).
   - Asserts `viewportWidth == "24"`, `viewportHeight == "24"`, `width == "24dp"`, `height == "24dp"`, `paths.length == 1`, `fillColor == "#FF1976D2"`, single subpath (`pathData.count { it == 'M' } == 1`), and terminal closure at $y = 20.0$ (`V20z`).
   - Validates the diagonal arrowhead constants against exact analytical trigonometric values (`abs(2.12 - 3.0 / sqrt(2.0)) < 0.005` and `abs(5.66 - 4.0 * sqrt(2.0)) < 0.005`), confirming that the Euclidean wing overhang is $\sqrt{2.12^2 + 2.12^2} = 2.998\text{ dp}$ ($3.0\text{ dp} \pm 0.01$) and the Euclidean base width is $\sqrt{5.66^2 + 5.66^2} = 8.004\text{ dp}$ ($8.0\text{ dp} \pm 0.01$).
   - Validates that all 8 evaluated milestone sets stay strictly inside $[3.0, 21.0] \times [3.0, 20.0]$ (zero viewport boundary clipping).
2. **Visual Regression & Multi-Theme Preview Gallery**:
   - Renders the 8-icon visual comparison gallery bitmap (`docs/engineering/walkthroughs/img/ATT-3774_turn_icons_gallery.png`) directly from the canonical XML `pathData` strings via Inkscape and preserves `TurnCueIconsGalleryPreview` (Light and Dark themes) in `TurnPromptBanner.kt`.

---

## 2. UI Consistency (Rule 23 & Design Guidelines §5.5)

- **Reference Screen / Gallery**: `TurnCueIconsGallery` and `TurnPromptBanner` in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt`.
- **Directional & Navigation Iconography Family Harmony (`docs/design_guidelines.md` Section 5.5)**:
  - Standard `24x24` viewport (`viewportWidth="24"`, `viewportHeight="24"`).
  - Base stem rising from consistent bottom baseline `y = 20` with `2dp` uniform stroke width.
  - Symmetric, canonical filled right-isosceles triangle arrowheads ($W = 8\text{ dp}, D = 4\text{ dp}, o = 3\text{ dp}$) across all 8 directions.
  - True geometric angle transitions: $0^\circ$ straight, $\pm 45^\circ$ slight turns, $\pm 90^\circ$ normal turns, $\pm 135^\circ$ sharp turns, $180^\circ$ U-turn.
  - Static `#FF1976D2` `android:fillColor` preserved for Layoutlib/Compose `@Preview` compatibility while `Icon(tint = ...)` applies runtime Material 3 theme colors (`MaterialTheme.colorScheme.primary` / `onPrimaryContainer`).

---

## 3. Atomic Step Breakdown

### Step 1: Update Cardinal Turn Cue Vector Drawables (`ic_turn_left.xml`, `ic_turn_right.xml`, `ic_turn_straight.xml`, `ic_turn_u_turn.xml`)
- Update `app/src/main/res/drawable/ic_turn_left.xml`, `ic_turn_right.xml`, `ic_turn_straight.xml`, and `ic_turn_u_turn.xml` with the unified $y = 20$ baseline, $2\text{ dp}$ stem width, and $W = 8\text{ dp}, D = 4\text{ dp}, o = 3\text{ dp}$ filled triangle arrowhead (centering `ic_turn_left.xml` and `ic_turn_right.xml` strictly on $y = 10.0$).

### Step 2: Update Diagonal Turn Cue Vector Drawables (`ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, `ic_turn_sharp_right.xml`)
- Update `app/src/main/res/drawable/ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, and `ic_turn_sharp_right.xml` with the unified $(2.12, 2.12)$ overhang ($o = 3.0\text{ dp}$) and $5.66\text{ dp}$ outer wing legs ($W = 8.0\text{ dp}, D = 4.0\text{ dp}$), preserving safety comment headers.

### Step 3: Update & Expand `TurnDirectionIconContractTest.kt`
- Update `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt` to assert:
  - Single `<path>` (`paths.length == 1`, `M` count == 1), `24x24` viewport, `fillColor == "#FF1976D2"`, baseline grounding at $y = 20.0$ (`V20z`), $2\text{ dp}$ stem width, and viewport bounding-box clearance ($[3.0, 21.0] \times [3.0, 20.0]$) across all 8 icons.
  - Trigonometric constant accuracy ($\epsilon < 0.01\text{ dp}$) for `2.12` ($3/\sqrt{2}$) and `5.66` ($4\sqrt{2}$) and uniform $W = 8.0\text{ dp}, D = 4.0\text{ dp}, o = 3.0\text{ dp}$ filled triangle arrowhead dimensions across all 8 icons.
  - Vertical centering ($y = 10.0$, wings at $y = 6.0$ and $y = 14.0$) and exact horizontal reflection symmetry ($x_L + x_R = 24.0 \pm 0.01, y_L = y_R \pm 0.01$) for `ic_turn_left.xml` and `ic_turn_right.xml`.
  - Exact horizontal reflection symmetry ($x_L + x_R = 24.0 \pm 0.01, y_L = y_R \pm 0.01$) for `ic_turn_slight_left.xml`/`ic_turn_slight_right.xml` and `ic_turn_sharp_left.xml`/`ic_turn_sharp_right.xml`.
- Run targeted test command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.TurnDirectionIconContractTest"
  ```

---

## 4. Non-Negotiable Invariants
1. **Gate 3 Pre-Check (Rule 4)**: Must verify `python3 tools/jira_util.py check-gate ATT-3822` exits with `0` before modifying any file under `app/src/...`.
2. **Viewport & FillColor Invariant**: All 8 icons must retain `viewportWidth="24"`, `viewportHeight="24"`, `width="24dp"`, `height="24dp"`, and `android:fillColor="#FF1976D2"`.
3. **Reflection Symmetry**: `LEFT`/`RIGHT`, `SLIGHT_LEFT`/`SLIGHT_RIGHT`, and `SHARP_LEFT`/`SHARP_RIGHT` must satisfy $x_L + x_R = 24.0 \pm 0.01$ and $y_L = y_R \pm 0.01$ across all path milestones.
4. **Human Decision Gate (Rule 3)**: Never transition parent ticket `ATT-3774` to `Erledigt`.
