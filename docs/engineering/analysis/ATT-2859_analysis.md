# Stage 1 Analysis: ATT-2859 - Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3854](https://rainerblind.atlassian.net/browse/ATT-3854) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Statement & Motivation

The application currently maintains 106 legacy multi-density PNG raster drawables across `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`, and root `drawable` for the 16 sport type (`bsport_bike`, `bsport_run`, `bsport_other`) and ANT+/BLE sensor icons (`bike_cad`, `bike_pwr`, `bike_spd`, `bike_speed_and_cadence`, `hr`, `run_spd`, `temp`, `bt_bike_cad`, `bt_bike_pwr`, `bt_bike_spd`, `bt_bike_speed_and_cadence`, `bt_hr`, `bt_run`), plus 18 redundant `_gray.png` bitmaps and 5 orphaned `btle_hr.png` bitmaps.

Previous attempts to vectorize these icons failed during Joint Review:
1. **Sprint 2026-42.4 (`8e218312`)**: Replaced custom multi-color sport and sensor badges with generic monochrome Material Design icons — rejected because the original visual design, multi-color geometry, and protocol badges (`#000000` ANT+ and `#07377F` BLE) must be preserved.
2. **Sprint 2026-42.5 (`9709220e`)**: Used naive marching-squares / Ramer-Douglas-Peucker (`M...L...Z`) polygon auto-tracing on color masks — rejected due to jagged polygonal edges, severed background wheel/frame strokes where foreground layers overlapped, and filled-in inner counter-holes.
3. **Sprint 2026-42.6 (`d024cf18`)**: Used hand-estimated coordinates without consulting the original mathematical vector master files in `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/` — bounced back so the icons are generated directly from the original mathematical Xfig (`.fig`), SVG (`.svg`), and GIMP (`.xcf`) source files.

---

## 2. Root Cause Analysis (Forensic Investigation of Original Source Files)

Forensic inspection of `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/` revealed the authoritative mathematical master files from which the original icons were authored:

### 2.1 Xfig 3.2 (`#FIG 3.2`) Mathematical Master Files (`1200` units/inch)
1. **`logo.fig`** (`/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/logo.fig`, paired with `logo.xcf` `4963×4963`):
   - Contains the complete mathematical vector definition of the stopwatch (`bsport_other`), the cyclist (`bsport_bike`), and the runner (`bsport_run`) arranged across explicit Z-depths (`depth 50..70`):
     - **Stopwatch (`bsport_other`)**:
       - Dial background circle (`1 3`, depth `70`): center $(6120, 1523)$, radius $r = 2392$, fill `#f0f0f0` (`color 33`), stroke `#ffc700` (`color 34`), thickness `22`.
       - Three gold stems (`2 1`, depth `55`, stroke `#ffc700`, thickness `30`):
         - Top center stem: $(6120, -900) \to (6120, -1215)$.
         - Top-left stem: $(4896, -535) \to (4738, -808)$.
         - Top-right stem: $(7314, -571) \to (7471, -844)$.
       - Three colored buttons (`2 1`, depth `50`, thickness `50`, butt cap):
         - Top center red crown (`color 4` `#ff0000`): $(6120, -1170) \to (6120, -1620)$.
         - Top-left dark blue button (`color 8` `#000090`): $(4770, -779) \to (4545, -1170)$.
         - Top-right green button (`color 12` `#009000`): $(7466, -827) \to (7691, -1217)$.
     - **Cyclist & Bicycle (`bsport_bike`)**:
       - Rear wheel (`1 3`, depth `55`, color `0` `#000000`, thickness `24`): circle at $(4995, 2520)$, radius $r = 627$.
       - Front wheel (`1 3`, depth `55`, color `0` `#000000`, thickness `20`): circle at $(7155, 2610)$, radius $r = 636$.
       - Saddle (`2 1`, depth `53`, color `0` `#000000`, thickness `7`): $(5265, 1305) \to (5662, 1305)$.
       - Red bicycle frame (`2 1`, color `4` `#ff0000`):
         - Rear stay (depth `50`, thickness `7`): $(5028, 2518) \to (5659, 1582)$.
         - Chainstay (depth `50`, thickness `7`): $(5895, 2655) \to (5028, 2518)$.
         - Top tube (depth `55`, thickness `5`): $(6810, 1671) \to (5659, 1582)$.
         - Front fork (depth `55`, thickness `12`): $(7135, 2607) \to (6791, 1564)$.
         - Down tube (depth `56`, thickness `17`): $(6829, 1726) \to (5805, 2610)$.
         - Seat tube (depth `56`, thickness `18`): $(5838, 2642) \to (5515, 1042)$.
       - Blue cyclist (`color 1` `#0000ff`):
         - Head (`1 1`, depth `50`, thickness `10`, solid fill `20`): circle at $(7189, 672)$, radii $(254, 253)$.
         - Curved back (`5 1` circular arc, depth `58`, thickness `30`, cap `1` round): center $(6549.201, 2018.404)$ through $(6846, 665) \to (6210, 675) \to (5624, 987)$.
         - Torso interior chords & closed X-spline (`depth 50`): polyline $(5715, 1035) \to (6750, 720)$ (thickness `23`, round join), polyline $(6570, 1575) \to (5580, 1170)$ (thickness `26`), and closed interpolated spline (`3 3`, thickness `4`, fill `41`) $(5490, 855) \to (5400, 1035) \to (5445, 1260) \to (5535, 1125) \to (5670, 945)$.
         - Arms & handlebar (`2 1`, depth `50`, thickness `15`, cap `1`, join `1`): $(7560, 1575) \to (6864, 1510) \to (6829, 646)$ and $(6829, 665) \to (6990, 682)$ (thickness `5`).
         - Upper/right pedaling leg (`2 1`, depth `54`, thickness `20`, cap `1`, join `1`): $(5977, 2491) \to (5814, 2169) \to (6660, 1665) \to (5568, 1239)$.
         - Lower/left extended leg (`2 1`, depth `62`, thickness `20`, cap `1`, join `1`): $(5670, 1080) \to (6090, 1887) \to (5838, 2804) \to (6090, 3002)$.
2. **`Bluetooth/bike.fig`**:
   - Exact same Xfig coordinates as the cyclist & bicycle in `logo.fig`, with stroke/fill color set to `7` (`#FFFFFF` pure white) for the BLE bike icons (`bt_bike_*`), exported to `bike.png` (`1313×1067`).
3. **`Bluetooth/runner.fig`**:
   - Exact same Xfig coordinates as the runner in `logo.fig` (`color 1` `#0000ff` in `bsport_run`, `color 7` `#ffffff` in `bt_run`), exported to `runner.png` (`967×1227`):
     - Head (`1 1`, depth `50`, thickness `10`, solid fill `20`): circle at $(9630, 450)$, radii $(254, 253)$.
     - Front lifted leg (`2 1`, depth `50`, thickness `28`, cap `1` round, join `1` round): $(9527, 1933) \to (10356, 2167) \to (9843, 3115)$.
     - Torso & rear push-off leg (`2 1`, depth `50`, thickness `28`, cap `1` round, join `1` round): $(9585, 1035) \to (9488, 1933) \to (8955, 2700) \to (8190, 3330)$.
     - Front bent arm (`2 1`, depth `50`, thickness `17`, cap `1` round, join `1` round): $(9606, 828) \to (9962, 1576) \to (10119, 946)$.
     - Rear bent arm (`2 1`, depth `50`, thickness `17`, cap `1` round, join `1` round): $(9606, 828) \to (8936, 985) \to (9212, 1539)$.
4. **`Bluetooth/BT_HR_just_the_heart.fig`**:
   - Mathematical Xfig 3.2 open approximated B-splines (`3 0`, thickness `20`, color `7` `#ffffff`) for the left and right symmetrical heart lobes from bottom tip $(6300, 8775)$ to top cleft $(6300, 5985)$ with control points $(7470, 7515), (8100, 6120), (7920, 5535), (7515, 5220), (6930, 5175), (6525, 5490)$ and mirrored left points, plus two sharp join-cap polygons (`2 3`) at $(6300, 8775)$ and $(6300, 5985)$.
5. **`Bluetooth/Bluetooth.svg`**:
   - Exact SVG vector definition (`viewBox="0 0 640 976"`, `<rect ry="291" height="976" width="640" fill="#0a3d91"/>`).
6. **`Bluetooth/BT_*.xcf` (`1400×1400` GIMP Master Compositions)**:
   - Each `1400×1400` BLE icon places the blue rounded-square badge (`#07377F`, inset `102..1298` on `1400×1400`, equivalent to `10.5..133.5` on `144×144`), the white `bike.png` (`1313×1067` at offset `(49, 53)`), `runner.png` (at offset `(-78, 0)`), or `BT_HR_just_the_heart.png` (at offset `(3, -3)`), and Pango `Sans Bold` white text layers (`cadence` at `(113, 1087)`, `power` at `(315, 1197)`, `speed` at `(281, 1105)`, `s & c` at `(417, 1149)`).

### 2.2 ANT+ Sensor Badges (`ANT/*.jpeg` & `drawable-xhdpi/*.png` `96×96`)
For the 7 ANT+ sensor icons (`bike_cad`, `bike_pwr`, `bike_spd`, `bike_speed_and_cadence`, `hr`, `run_spd`, `temp`), the highest-resolution reference assets are the `96×96` `drawable-xhdpi` PNGs:
- Black rounded-square container (`#000000`, `96×96` viewport, corner radius $r = 10$).
- Top white geometric emblem (cyclist with hollow wheel rings, heart outline, footprint with 5 toes, thermometer with bulb and 6 rounded tick bars).
- Bottom uppercase white geometric typography (`CAD`, `PWR`, `SPD`, `S&C`, `HR`, `TEMP`) with `android:fillType="evenOdd"` for all counter-holes (`A`, `P`, `R`, `D`, `&`).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Translate the mathematical `.fig` (`logo.fig`, `bike.fig`, `runner.fig`, `BT_HR_just_the_heart.fig`), `.svg`, and `.xcf` master definitions directly into Android `VectorDrawable` XMLs for `bsport_bike.xml`, `bsport_run.xml`, `bsport_other.xml`, `bt_bike_cad.xml`, `bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, and `bt_run.xml`.
  * Reconstruct the 7 ANT+ sensor icons (`bike_cad.xml`, `bike_pwr.xml`, `bike_spd.xml`, `bike_speed_and_cadence.xml`, `hr.xml`, `run_spd.xml`, `temp.xml`) from true analytical geometric primitives (`A` arcs/circles, `C` Cubic Beziers, rounded strokes, and clean vector font outlines with `android:fillType="evenOdd"`) matching the `96×96` `xhdpi` reference PNGs 1:1.
  * Eliminate duplicate `_gray.png` bitmaps (`bsport_bike_gray.png`, `bsport_run_gray.png`, `bsport_other_gray.png`) and remove the 106 superseded raster PNG files across all density buckets.
  * Visually verify rendered bitmaps of all 16 `VectorDrawable`s side-by-side against the reference PNGs (`ATT-2859_vector_comparison.png`).
  * Add `VectorAssetResolutionContractTest.kt` and update `SportTypeSelectorContractTest.kt` to enforce Rule 37 invariants.
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

1. **Direct Mathematical Xfig (`.fig`) & XCF Translation**:
   - Parse the exact Xfig 3.2 primitives in `logo.fig`, `bike.fig`, `runner.fig`, and `BT_HR_just_the_heart.fig`:
     - Type `1` (Circle/Ellipse) $\to$ exact SVG `M ... A rx ry 0 1 1 ... A rx ry 0 1 1 ... Z` path with exact stroke width and Z-depth ordering (`depth 70` down to `depth 50`).
     - Type `5` (Circular Arc through 3 points) $\to$ exact analytical SVG arc `M x1 y1 A R R 0 0 sweep x3 y3` computed from center $(xc, yc)$ and endpoints $(x_1, y_1), (x_2, y_2), (x_3, y_3)$.
     - Type `2` (Polyline) $\to$ exact `M x0 y0 L x1 y1 ...` path with `android:strokeWidth`, `android:strokeLineCap` (`round` for cap=1, `butt` for cap=0), and `android:strokeLineJoin` (`round` for join=1, `miter` for join=0).
     - Type `3` (X-spline / B-spline in `BT_HR_just_the_heart.fig` and `logo.fig`) $\to$ exact mathematical Quadratic/Cubic Bezier spline conversion (`C` / `Q` commands) plus end-cap polygons.
   - Calibrate the exact affine transform $(s_x, s_y, t_x, t_y)$ from Xfig units (`1200` dpi) to the target viewports (`192×192` for `bsport_*` and `1400×1400` / `144×144` for `bt_*`) against the reference PNGs so every wheel, frame tube, limb, stopwatch crown, and heart curve aligns 1:1 to sub-pixel precision.
   - Extract exact vector glyph outlines (`C` / `L` / `Z` with `android:fillType="evenOdd"`) for the Pango `Sans Bold` labels (`cadence`, `power`, `speed`, `s & c`) and ANT+ labels (`CAD`, `PWR`, `SPD`, `S&C`, `HR`, `TEMP`) aligned to the exact pixel coordinates of the master `.xcf` / `.png` files.
2. **1:1 Visual Comparison Verification**:
   - Render all 16 XML `VectorDrawable`s to bitmaps and generate a side-by-side comparison sheet (`docs/engineering/walkthroughs/img/ATT-2859_vector_comparison.png`) against the reference PNGs, verifying pixel accuracy (`IoU` / visual overlay).
3. **Automated Rule 37 Contract Enforcement**:
   - `VectorAssetResolutionContractTest.kt` parses all 16 XML files via DOM and verifies that every file uses true curved geometric commands (`A`/`C`), proper `strokeWidth` / `evenOdd` attributes, and zero density-bucket PNG leftovers.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. 1:1 mathematical and visual fidelity with the original `.fig`/`.xcf` sources and `xxxhdpi`/`xxhdpi`/`xhdpi` reference icons (Rule 37).
  2. Zero `Resources.NotFoundException` across all densities (`DrawableDensityFallbackAuditTest` 100% pass).
  3. Zero regression in existing features and unit tests (`./gradlew testDebugUnitTest` 100% pass).
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Direct mathematical translation from original `.fig`/`.xcf` vector sources with identical `R.drawable.*` IDs, verified both visually against reference PNGs and programmatically via DOM contract tests.
