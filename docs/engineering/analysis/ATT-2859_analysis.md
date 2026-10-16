# Stage 1 Analysis: ATT-2859 - Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3785](https://rainerblind.atlassian.net/browse/ATT-3785) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Active Sprint**: `2026-42.6`  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

The application currently maintains 106 legacy multi-density PNG raster drawables distributed across `drawable/`, `drawable-ldpi/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, and `drawable-xxxhdpi/` for its 16 core sport type icons (`bsport_bike`, `bsport_run`, `bsport_other`), ANT+ sensor badges (`hr`, `bike_spd`, `bike_cad`, `bike_speed_and_cadence`, `bike_pwr`, `run_spd`, `temp`), and Bluetooth LE sensor badges (`bt_hr`, `bt_bike_spd`, `bt_bike_cad`, `bt_bike_speed_and_cadence`, `bt_bike_pwr`, `bt_run`), plus 18 dead `_gray.png` files and 5 orphaned `btle_hr.png` files.

Two previous vectorization attempts were rejected during Sprint Reviews:
1. **Sprint 2026-42.4 Rejection (`8e218312`)**: Replaced the custom multi-color sport icons and protocol-coded ANT+/BLE sensor badges with generic monochrome Material Design icons, destroying the app's visual identity and protocol differentiation (black ANT+ vs. blue BLE badges).
2. **Sprint 2026-42.5 Rejection (`9808cc80` / `ede8a293`)**: Used a naive marching-squares + Ramer-Douglas-Peucker (`M...L...Z` straight-line polygon) pixel-mask auto-tracer (`tools/generate_all_vectors.py`). Because pixel masks were traced independently per color without `android:fillType="evenOdd"`, without circular/arc/Cubic Bezier (`A`, `C`) primitives, and without back-to-front layer reconstruction:
   - Circles (wheels, stopwatch ring, heads, thermometer bulb, toes) were reduced to jagged polygons (`L` segments).
   - Occluded background layers (e.g., the bicycle rear wheel and red frame behind the blue cyclist's leg in `bsport_bike`) were severed into disconnected polygon fragments with visible gaps at color boundaries.
   - Inner counter-holes (wheel interiors, stopwatch face, heart interiors, and letter counters in `R`, `P`, `D`, `A`, `B`, `&`, `e`, `a`, `d`, `p`, `o`) were either filled solid or distorted.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Why Naive Pixel Auto-Tracing Fails on Multi-Layer Icons & Badges
When an icon is composed of overlapping geometric layers (such as `bsport_bike`, where a blue rider sits in front of a red bicycle frame, which in turn sits in front of two black wheels), a raster PNG only stores the top-most visible pixel at each $(x, y)$ coordinate.
- **Layer Occlusion Severing**: Tracing the black pixel mask alone yields two crescent fragments instead of a complete circular rear wheel, and tracing the red pixel mask yields four tiny disconnected triangles instead of a continuous diamond frame. When rendered at arbitrary DPI scales by Android's `VectorDrawable` renderer, anti-aliasing seams appear between the abutting polygon fragments.
- **Absence of Analytical Primitives (`A`, `C`)**: Approximating circles and smooth curves with piecewise linear `L` segments creates visible facet corners ("polygonal wheels").
- **Winding Rule Omission (`nonZero` vs. `evenOdd`)**: Compound paths with inner holes (such as hollow wheels, hollow heart outlines, and typographic counters in `CAD`, `PWR`, `SPD`, `S&C`, `HR`, `TEMP`, `cadence`, `power`, `speed`, `s & c`) require either explicit `android:fillType="evenOdd"` or opposing clockwise/counter-clockwise subpath winding, or explicit `<path>` strokes (`android:strokeWidth`, `android:strokeColor`, `android:strokeLineCap="round"`, `android:strokeLineJoin="round"`).

### 2.2 Exact Visual & Geometric Inventory of the 16 Reference Icons

#### Category A: Multi-Color Sport Type Icons (`192×192` reference canvas)
1. **`bsport_bike.xml`**:
   - **Layer 1 (Back — Black `#000000` Wheels & Saddle)**:
     - Rear wheel: Analytical circular ring centered at $(48.5, 126.0)$, outer radius $R = 36.5$, inner radius $r = 18.5$ (or stroked circle $r = 27.5$, `strokeWidth="18"`).
     - Front wheel: Analytical circular ring centered at $(144.5, 129.5)$, outer radius $R = 35.0$, inner radius $r = 18.0$ (or stroked circle $r = 26.5$, `strokeWidth="17"`).
     - Saddle nose: Horizontal black bar at $(60, 68.5)$ to $(74, 73.5)$.
   - **Layer 2 (Middle — Red `#FF0000` Frame & Fork)**:
     - Complete continuous bicycle frame drawn behind the rider:
       - Seat tube from saddle $(73, 71)$ down to bottom bracket $(90, 131)$ (`strokeWidth="13"`).
       - Seatstay from seat tube $(75, 89)$ to rear hub $(50, 125)$ (`strokeWidth="5.5"`).
       - Chainstay from rear hub $(50, 125)$ to bottom bracket $(90, 131)$ (`strokeWidth="5.5"`).
       - Top tube from seat tube $(75, 83)$ to head tube $(128, 86)$ (`strokeWidth="5"`).
       - Down tube from head tube $(130, 88)$ to bottom bracket $(90, 131)$ (`strokeWidth="13.5"`).
       - Front fork from head tube $(129, 82)$ straight down-right to front hub $(143.5, 129.5)$ (`strokeWidth="9"`).
   - **Layer 3 (Front — Blue `#0000FF` Time-Trial Cyclist)**:
     - Head: Analytical circle centered at $(147.0, 42.5)$, radius $r = 14.0$.
     - Arched torso/back: Smooth Cubic Bezier / rounded thick stroke from hip $(74.5, 59.5)$ arching through $(102, 41)$ to shoulder $(132, 42)$ with rounded cap.
     - Upper arm & forearm on aerobars: From shoulder $(130.5, 48)$ down to elbow $(132, 79.5)$ and forward to hand $(163, 82.5)$ (`strokeWidth="9.5"`, `strokeLineCap="round"`, `strokeLineJoin="round"`).
     - Front (right) leg: Thigh from hip $(75, 61)$ to knee $(120, 86)$ (`strokeWidth="17"`), lower leg from knee $(120, 86)$ to ankle $(86.5, 111.5)$ (`strokeWidth="15"`), foot to $(90.5, 121.5)$ (`strokeWidth="15"`).
     - Rear (left) leg: Lower leg & foot extending down to $(90, 138.5)$ and $(98, 146)$ (`strokeWidth="13"`).
2. **`bsport_run.xml`** (`192×192` reference canvas):
   - Pure blue (`#0000FF`) runner constructed from analytical circle head at $(113.0, 29.0)$, $r = 15.5$, forward-leaning torso from $(109.5, 48)$ to $(104.5, 96)$ (`strokeWidth="17.5"`), rear bent arm $(109, 49) \to (81, 55.5) \to (92.5, 79)$ (`strokeWidth="13"`), front bent arm $(112, 49) \to (129, 80.5) \to (136.5, 52.5)$ (`strokeWidth="11.5"`), lifted front leg $(105, 96) \to (148.5, 109.5) \to (123, 157.5)$ (`strokeWidth="18.5"`), and extended push-off rear leg $(104, 96) \to (81.5, 134.5) \to (44, 165)$ (`strokeWidth="18.5"`), all with `strokeLineCap="round"` and `strokeLineJoin="round"`.
3. **`bsport_other.xml`** (`192×192` reference canvas):
   - Stopwatch constructed in clean back-to-front layers:
     - Dial interior: Analytical circle centered at $(96.0, 108.5)$, $r = 62.0$, fill `#EFEFEF`.
     - Gold/yellow stopwatch ring & stems (`#FFC000`): Outer circle $R = 68.5$, inner circle $r = 59.5$ (`evenOdd`), plus center neck $(90, 34.5)..\,(102, 42)$, left angled stem at $-30^\circ$, and right angled stem at $+30^\circ$.
     - Top center red crown (`#FF0000`): Crisp rectangle $(85.5, 26.5)$ to $(105.5, 38.5)$.
     - Top-left dark blue button (`#000080`): Rotated rectangle tilted at $-30^\circ$.
     - Top-right green button (`#008000`): Rotated rectangle tilted at $+30^\circ$.

#### Category B: ANT+ Sensor Badges (Black `#000000` Rounded Square with White `#FFFFFF` Foreground)
All 7 ANT+ sensor badges share an identical canonical black rounded-square container (`96×96` viewport, rounded rectangle from $(0, 0)$ to $(96, 96)$ with corner radius $r = 10$), topped with crisp white geometric iconography and bottom uppercase geometric typography (`CAD`, `PWR`, `SPD`, `S&C`, `HR`, `TEMP`) using `android:fillType="evenOdd"` for all counter-holes:
4. **`bike_cad.xml`**: White cyclist icon (head circle, two hollow circular wheel rings, bent rider torso/arm/leg stroke) + bold white `CAD`.
5. **`bike_pwr.xml`**: Identical white cyclist icon + bold white `PWR`.
6. **`bike_spd.xml`**: Identical white cyclist icon + bold white `SPD`.
7. **`bike_speed_and_cadence.xml`**: Identical white cyclist icon + bold white `S&C`.
8. **`hr.xml`**: Symmetrical white Cubic Bezier heart outline + bold white `HR`.
9. **`run_spd.xml`**: Smooth Cubic Bezier white footprint sole contour + 5 analytical circular/elliptical toes + bold white `SPD`.
10. **`temp.xml`**: Analytical white thermometer outline (circular bulb + vertical rounded stem + inner bulb highlight arc + 2 left & 4 right horizontal tick bars with rounded caps) + bold white `TEMP`.

#### Category C: Bluetooth LE Sensor Badges (Blue `#07377F` Rounded Square with White `#FFFFFF` Foreground)
All 6 BLE sensor badges share an identical canonical Bluetooth Blue (`#07377F`) rounded-square badge (`144×144` viewport, inset $(10.5, 10.5)$ to $(133.5, 133.5)$, corner radius $r = 13.5$) with pure white (`#FFFFFF`) foreground:
11. **`bt_bike_cad.xml`**: White `bsport_bike` silhouette (constructed from analytical wheel rings, frame, and cyclist with `evenOdd` holes) + bold white `cadence`.
12. **`bt_bike_pwr.xml`**: Identical white `bsport_bike` silhouette + bold white `power`.
13. **`bt_bike_spd.xml`**: Identical white `bsport_bike` silhouette + bold white `speed`.
14. **`bt_bike_speed_and_cadence.xml`**: Identical white `bsport_bike` silhouette + bold white `s & c`.
15. **`bt_hr.xml`**: Large symmetrical white Cubic Bezier heart outline filling the blue badge.
16. **`bt_run.xml`**: White `bsport_run` runner silhouette centered inside the blue badge.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Construct all 16 sport and sensor vector drawables in `app/src/main/res/drawable/` using true geometric primitives (`A` arcs/circles, `C` Cubic Beziers, rounded strokes, back-to-front Z-ordering for multi-layer icons, and `android:fillType="evenOdd"` for compound counter-holes) in strict compliance with Rule 37 (`docs/design_guidelines.md` Section 5.5).
  * Render and visually verify all 16 vector drawables side-by-side against the original `xxxhdpi`/`xxhdpi`/`xhdpi` PNGs to confirm 1:1 visual fidelity.
  * Delete the 106 superseded density-specific raster PNG files (including the 18 dead `_gray.png` files and 5 orphaned `btle_hr.png` files).
  * Update `SportTypeSelectorContractTest.kt` and add `VectorAssetResolutionContractTest.kt` enforcing Rule 37 geometric primitive invariants (verifying presence of curved `A`/`C` commands, `evenOdd` or stroked geometry, and absence of naive polygon-only tracing).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Third-party OAuth/brand bitmaps (`connect_with_strava.png`, `dropbox_logo_blue.png`, etc.).
  * Map directional arrowhead / start / stop bitmaps (`arrowhead.png`, `start_logo_map.png`, `stop_logo_map.png`).
  * Legacy status bar battery icons (`stat_sys_battery_*.png`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*, Sprint 2026-41.5, `ATT-2858`) and `REQ-UI-380` (*Complete High-Density Asset Coverage & Defensive Compose Resource Resolution*, Sprint 2026-42.5, `ATT-3529`). Introduces formal requirement `REQ-UI-375`.
* **Historical Origin & Commit Trace**: `ATT-2858` copied raster PNGs into root `res/drawable/` as a stopgap against Play Store density-split crashes and added `DrawableDensityFallbackAuditTest.kt`. `ATT-3529` explicitly carved out the 16 sport/sensor icons governed by `ATT-2859`.
* **Root Reason for Existing Formulation**: `DrawableDensityFallbackAuditTest` ensures that no density-qualified drawable lacks a root `res/drawable/` fallback.
* **Preservation of Core Invariants**: Replacing all density-qualified PNG variants of the 16 icons with canonical XML `VectorDrawable`s in root `app/src/main/res/drawable/` satisfies `DrawableDensityFallbackAuditTest` with 0 density-split risk, preserves all `R.drawable.*` IDs, and satisfies Rule 37 1:1 visual fidelity.

---

## 5. Architectural Strategy & High-Level Solution

1. **Analytical Geometric Construction**:
   - Build each of the 16 XML `VectorDrawable`s using exact analytical circles (`A rx ry 0 1 1 ... A rx ry 0 1 1 ...`), smooth Cubic Beziers (`C`), rounded strokes (`android:strokeLineCap="round"`, `android:strokeLineJoin="round"`), back-to-front layer composition (`bsport_bike.xml`, `bsport_other.xml`), and clean `evenOdd` vector typography (`CAD`, `PWR`, `SPD`, `S&C`, `HR`, `TEMP`, `cadence`, `power`, `speed`, `s & c`).
2. **1:1 Visual Comparison Verification**:
   - Render every generated XML `VectorDrawable` to a bitmap and compare it side-by-side against the reference `xxxhdpi`/`xxhdpi`/`xhdpi` PNG to verify visual fidelity and confirm zero jagged polygon edges, zero missing wheel/frame segments, and zero filled-in letter holes.
3. **Automated Rule 37 Contract Enforcement**:
   - `VectorAssetResolutionContractTest.kt` will parse all 16 XML files via DOM and assert that every file uses true curved geometric commands (`A`, `a`, `C`, `c`, `Q`, `q`, `S`, `s`) and proper `evenOdd` / stroke attributes, permanently preventing regression to polygon-only `M...L...Z` auto-tracing.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. 1:1 visual fidelity with the original `xxxhdpi`/`xxhdpi`/`xhdpi` reference icons (Rule 37).
  2. Zero `Resources.NotFoundException` across all densities (`DrawableDensityFallbackAuditTest` 100% pass).
  3. Zero regression in existing features and unit tests (`./gradlew testDebugUnitTest` 100% pass).
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Resource-only replacement with identical `R.drawable.*` IDs, verified both visually against reference PNGs and programmatically via DOM contract tests.
