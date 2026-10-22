# Stage 1 Analysis: ATT-3774 — Unify arrowhead style and size across all 8 turn cue vector icons and fix Turn Left arrowhead centering

## 1. Problem Domain & Root Cause Analysis (RCA)

### 1.1 Symptom & User Observation
During the Sprint 2026-42.5 Joint Review of `ATT-3573`, inspecting the `TurnCueIconsGallery` preview gallery (`TurnPromptBanner.kt`) revealed two visual defects across the 8 `TurnDirection` vector drawables (`app/src/main/res/drawable/ic_turn_*.xml`):
1. **Inconsistent Arrowhead Styles and Sizes Across the 8-Icon Family**:
   - `ic_turn_left.xml` and `ic_turn_right.xml` used large filled triangle arrowheads (`W = 10dp`, `D = 5dp`, overhang `o = 4dp`).
   - `ic_turn_u_turn.xml` used a filled triangle arrowhead (`W = 8dp`, `D = 5dp`, overhang `o = 3dp`) on a separate overlapping subpath (`M3,18l4,5l4,-5...`) extending down to `y = 23`, while its stems extended to `y = 21` instead of the canonical `y = 20` baseline.
   - `ic_turn_sharp_left.xml` and `ic_turn_sharp_right.xml` (`ATT-3573`) used smaller filled right-isosceles triangle arrowheads (`W = 6dp`, `D = 3dp`, overhang `o = 2dp`, leg length `4.24dp`).
   - `ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, and `ic_turn_straight.xml` used open stroked chevron arrowheads instead of filled triangles.
2. **Off-Center & Asymmetric Arrowhead on `ic_turn_left.xml`**:
   - In `ic_turn_left.xml` (`M9,5v4h5c1.66,0 3,1.34 3,3v7h-2v-7c0,-0.55 -0.45,-1 -1,-1H9v4L4,9l5,-4z`), the horizontal shaft occupies `y in [9, 11]` (center axis `y = 10.0`).
   - However, the arrowhead tip was placed at `(4, 9)` (`L4,9l5,-4z`) instead of `(4, 10)`, causing the lower wing slope (`dy = -6`) to be steeper than the upper wing slope (`dy = -4`) and shifting the tip off-center by `1dp` upward.
   - In `ic_turn_right.xml` (`...h5v4l5,-5 -5,-4z`), the tip was at `(20, 10)` (`l5,-5`), followed by `-5,-4z` which stopped at `(15, 6)` before closing to `(15, 5)`, breaking mathematical reflection symmetry (`x_L + x_R = 24.0`, `y_L = y_R`) between `ic_turn_left.xml` and `ic_turn_right.xml`.
   - Furthermore, `ic_turn_left.xml`, `ic_turn_right.xml`, and `ic_turn_straight.xml` grounded their stems at `y = 19` rather than the canonical `y = 20` baseline used by `ic_turn_slight_*` and `ic_turn_sharp_*`.

---

## 2. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-360` (*Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery*, `ATT-3455`) and `REQ-UI-361` (*Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification*, `ATT-3493` / `ATT-3573`), and introduces net-new requirement `REQ-UI-389` (*Unified Filled-Triangle Arrowhead Geometry, Baseline Grounding & Reflection Symmetry Across All 8 Turn Cue Vector Icons*) and `TST-UI-352`.
2. **Historical Origin & Commit Trace**:
   - `ATT-1450` (Sprint 2026-40.16) introduced the initial 8 `ic_turn_*.xml` drawables from disparate Material icon styles (`turn_left`, `straight`, `u_turn_left`).
   - `ATT-3455` (Sprint 2026-42.2) harmonized `ic_turn_slight_left.xml` and `ic_turn_slight_right.xml` and created `TurnCueIconsGallery`.
   - `ATT-3573` (Sprint 2026-42.5) redesigned `ic_turn_sharp_left.xml` and `ic_turn_sharp_right.xml` with acute $135^\circ$ bends and filled triangle arrowheads.
3. **Root Reason for Existing Formulation**:
   - Earlier tickets addressed `slight` (`ATT-3455`) and `sharp` (`ATT-3493` / `ATT-3573`) turn icons individually without unifying arrowhead style and dimensions across all 8 icons simultaneously, leaving `straight` and `slight_*` with stroked chevrons and `left` with its legacy off-center tip `(4, 9)`.
4. **Preservation of Core Invariants**:
   - Standard `24dp x 24dp` (`viewportWidth="24"`, `viewportHeight="24"`) canvas, `#FF1976D2` fill color token, `2.0dp` uniform stem/shaft stroke width, bottom baseline grounding at `y = 20.0`, acute $135^\circ$ bend on sharp turns (`REQ-UI-361`), smooth $45^\circ$ bend on slight turns (`REQ-UI-360`), and exact midline horizontal reflection symmetry (`x_L + x_R = 24.0`, `y_L = y_R`) across all 3 directional pairs (`left`/`right`, `slight_left`/`slight_right`, `sharp_left`/`sharp_right`) are 100% preserved and unified.

---

## 3. Unified Geometric Specification & Scope Bounding

### 3.1 Canonical Unified Arrowhead & Stem Specification (`REQ-UI-389`)
All 8 turn cue vector icons (`ic_turn_left.xml`, `ic_turn_right.xml`, `ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, `ic_turn_sharp_right.xml`, `ic_turn_straight.xml`, `ic_turn_u_turn.xml`) share an identical single-path (`<path>`) construction with:
- **Stem & Shaft Width**: $2.0\text{ dp}$ everywhere, grounded at bottom baseline $y = 20.0\text{ dp}$.
- **Unified Filled Right-Isosceles Triangle Arrowhead**:
  - **Wing overhang beyond the $2.0\text{ dp}$ shaft**: $o = 3.0\text{ dp}$ on each side perpendicular to the shaft axis.
  - **Total base width perpendicular to shaft**: $W = 2.0 + 2 \times 3.0 = 8.0\text{ dp}$ (half-base $R = 4.0\text{ dp}$).
  - **Tip length along shaft axis**: $D = R = 4.0\text{ dp}$ ($90^\circ$ apex angle).
  - **Outer triangle wing edge length**: $L = R\sqrt{2} = 4.0\sqrt{2} \approx 5.66\text{ dp}$ (`l4,-4`/`l-4,-4` on cardinal axes; `l5.66,0`/`l0,5.66` with overhang `2.12,2.12` on $45^\circ$/$135^\circ$ diagonal axes).

### 3.2 In-Scope Deliverables
1. **`app/src/main/res/drawable/ic_turn_left.xml` & `ic_turn_right.xml`**:
   - Ground stems at $y = 20$ ($x \in [15, 17]$ for left, $x \in [7, 9]$ for right).
   - Center arrowhead strictly on horizontal shaft axis $y = 10$ ($y \in [6, 14]$, $W = 8\text{ dp}$, tip at $(5, 10)$ for left and $(19, 10)$ for right, $D = 4\text{ dp}$), achieving exact reflection symmetry ($x_L + x_R = 24.0, y_L = y_R$).
2. **`app/src/main/res/drawable/ic_turn_slight_left.xml` & `ic_turn_slight_right.xml`**:
   - Replace open stroked chevron with the unified filled right-isosceles triangle arrowhead ($o = 3.0\text{ dp} \implies \Delta x = \Delta y = 2.12$, outer wing legs $L = 5.66\text{ dp}$, $W = 8.0\text{ dp}, D = 4.0\text{ dp}$), preserving baseline grounding at $y = 20$, $2\text{ dp}$ stem width, and exact reflection symmetry.
3. **`app/src/main/res/drawable/ic_turn_sharp_left.xml` & `ic_turn_sharp_right.xml`**:
   - Scale the filled right-isosceles triangle arrowhead from $W = 6.0\text{ dp}$ (`1.41` / `4.24`) to the unified $W = 8.0\text{ dp}, D = 4.0\text{ dp}$ (`2.12` / `5.66`), preserving the $135^\circ$ bend, baseline grounding at $y = 20$, and exact reflection symmetry.
4. **`app/src/main/res/drawable/ic_turn_straight.xml`**:
   - Ground stem at $y = 20$ ($x \in [11, 13]$) and replace open chevron with the unified filled triangle arrowhead ($W = 8.0\text{ dp}$ spanning $x \in [8, 16]$ at $y = 8$, tip at $(12, 4)$, $D = 4.0\text{ dp}$).
5. **`app/src/main/res/drawable/ic_turn_u_turn.xml`**:
   - Consolidate into a single closed path grounded at $y = 20$ with right stem $x \in [16, 18]$, $180^\circ$ semicircular arch centered at $(12, 9)$ (inner radius $4\text{ dp}$, outer radius $6\text{ dp}$), left shaft $x \in [6, 8]$, and unified filled triangle arrowhead at $y = 16$ ($x \in [3, 11], W = 8.0\text{ dp}$) with tip at $(7, 20)$ ($D = 4.0\text{ dp}$).
6. **`app/src/test/java/com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt`**:
   - Enforce baseline grounding at $y = 20$, single-path filled geometry, exact horizontal reflection symmetry ($x_L + x_R = 24.0, y_L = y_R$) across all 3 pairs (`LEFT`/`RIGHT`, `SLIGHT_LEFT`/`SLIGHT_RIGHT`, `SHARP_LEFT`/`SHARP_RIGHT`), vertical centering of `LEFT`/`RIGHT` arrowheads at $y = 10.0$, and uniform arrowhead dimensions ($W = 8.0\text{ dp}, D = 4.0\text{ dp}, o = 3.0\text{ dp}$) across all 8 icons.

### 3.3 Out-of-Scope
- Turn-by-turn navigation detection algorithms (`TurnCueDetector.kt`, `TurnByTurnNavigationEngine.kt`).
- `TurnPromptBanner.kt` layout or audio cues (`NavigationAudioAlertManager.kt`).
