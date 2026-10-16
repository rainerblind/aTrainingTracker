# Stage 3: Implementation Plan - ATT-2859: Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3856](https://rainerblind.atlassian.net/browse/ATT-3856) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-UI-375`  
**Test Mapping**: `TST-UI-338`  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Description & Background

The 16 sport type and ANT+/BLE sensor icons currently exist as 106 multi-density raster PNG files across `drawable`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, and `drawable-xxxhdpi`. Per Rule 37 (`docs/design_guidelines.md` Section 5.5) and user alignment, we must translate the original mathematical Xfig 3.2 (`.fig`), SVG (`.svg`), and GIMP (`.xcf`) source files in `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/` directly into density-independent Android `VectorDrawable` XMLs in `app/src/main/res/drawable/`, reconstruct the 7 ANT+ sensor icons from true analytical geometric primitives matching the `96×96` `xhdpi` reference PNGs, purge all 106 legacy raster PNGs (including `bsport_*_gray.png` and `btle_hr.png`), and verify 1:1 visual fidelity both programmatically and side-by-side (`ATT-2859_vector_comparison.png`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-375` (*Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge*)
* **Test Mapping**: `TST-UI-338` (*Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All 16 `R.drawable.*` resource IDs remain identical (`bsport_bike`, `bsport_run`, `bsport_other`, `bike_cad`, `bike_pwr`, `bike_spd`, `bike_speed_and_cadence`, `hr`, `run_spd`, `temp`, `bt_bike_cad`, `bt_bike_pwr`, `bt_bike_spd`, `bt_bike_speed_and_cadence`, `bt_hr`, `bt_run`).
2. **Rule 37 Mathematical Geometry**: Every vector drawable is constructed from true geometric primitives (`A` arcs/circles, `C`/`Q` Beziers, stroked polylines in back-to-front Z-depth order, and `android:fillType="evenOdd"` for counter-holes) with $\le 45$ `L` commands per `<path>`.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Xfig 3.2 (`.fig`) & GIMP (`.xcf`) Mathematical Vector Compiler (`bsport_*` & `bt_*`)
* **Source Files**:
  * `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/logo.fig` (`bsport_other`, `bsport_bike`, `bsport_run`)
  * `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/Bluetooth/bike.fig` (`bsport_bike`, `bt_bike_*`)
  * `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/Bluetooth/runner.fig` (`bsport_run`, `bt_run`)
  * `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/Bluetooth/BT_HR_just_the_heart.fig` (`bt_hr`)
  * `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/Bluetooth/BT_*.xcf` (`1400×1400` master layer offsets and Pango `Sans Bold` labels)
* **Mathematical Conversion**:
  * Sort all Xfig objects strictly by descending Z-depth (`depth 70` down to `depth 50`) so background layers (stopwatch dial, wheels, bicycle frame tubes) render beneath foreground layers (rider legs, arms, buttons).
  * Convert Xfig object type `1` (Circle/Ellipse at $(cx, cy)$ with radii $(r_x, r_y)$) to exact SVG two-arc circles:
    `M (cx-rx),cy A rx,ry 0 1,0 (cx+rx),cy A rx,ry 0 1,0 (cx-rx),cy Z` with exact scaled `android:strokeWidth` and `android:fillColor`.
  * Convert Xfig object type `5` (Circular arc through $(x_1,y_1), (x_2,y_2), (x_3,y_3)$ with center $(x_c, y_c)$ and radius $R = \hypot(x_1-x_c, y_1-y_c)$) to exact SVG arc `M x1,y1 A R,R 0 largeArc,sweep x3,y3` with `android:strokeLineCap="round"`.
  * Convert Xfig object type `2` (Polylines & Polygons) to `M x0,y0 L x1,y1 ...` with exact scaled `android:strokeWidth`, `android:strokeLineCap` (`round` for cap=1, `butt` for cap=0), and `android:strokeLineJoin` (`round` for join=1, `miter` for join=0).
  * Convert Xfig object type `3` (Open approximated B-splines in `BT_HR_just_the_heart.fig` and closed X-spline in `bike.fig`/`logo.fig`) to exact Quadratic/Cubic Bezier curves (`Q`/`C`) where midpoints $M_i = \frac{1}{2}(P_i + P_{i+1})$ join via `Q P_i M_i`.
  * Extract smooth Cubic Bezier (`C`) + line (`L`) vector font outlines with `android:fillType="evenOdd"` for `cadence`, `power`, `speed`, and `s & c` at their exact `.xcf` layer coordinates inside the `#07377F` rounded-square badge (`144×144` viewport).

### Component 2: ANT+ Sensor Badges Analytical Reconstruction (`bike_cad`, `bike_pwr`, `bike_spd`, `bike_speed_and_cadence`, `hr`, `run_spd`, `temp`)
* Construct all 7 ANT+ sensor XMLs on their native `96×96` viewport with the canonical black (`#000000`) rounded-square badge ($r=10$), white (`#FFFFFF`) analytical geometric emblems (cyclist with hollow wheel rings, symmetrical Cubic Bezier heart outline, Cubic Bezier footprint + 5 elliptical toes, thermometer bulb + stem + 6 rounded tick bars), and bold uppercase geometric vector typography (`CAD`, `PWR`, `SPD`, `S&C`, `HR`, `TEMP`) with `android:fillType="evenOdd"`.

### Component 3: Legacy Raster Purge, Compose Styling & Contract Verification
* Delete all 106 superseded raster `.png` files across `drawable*`.
* Document the `REQ-UI-375` multi-color and dynamic desaturation invariant in `SportTypeSelector.kt`.
* Add `VectorAssetResolutionContractTest.kt` and update `SportTypeSelectorContractTest.kt`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `SportTypeSelector.kt`, `EditDeviceFragment`, `SensorGridScreen.kt` (`xxxhdpi`/`xxhdpi`/`xhdpi` original icons).
* **Reused components**: `Icon(painter = painterResource(...), tint = Color.Unspecified)`.
* **Theme tokens**: Native sRGB emblem colors (`#000000`, `#FF0000`, `#0000FF`, `#F0F0F0`, `#FFC700`, `#000090`, `#009000`, `#07377F`, `#FFFFFF`) per Rule 37.
* **New one-off styles & justification**: None — strict 1:1 mathematical preservation of original icon designs per Rule 37.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Translate Xfig/SVG/XCF Masters & ANT+ Primitives into 16 XML `VectorDrawable`s
* Files: `app/src/main/res/drawable/{bsport_bike,bsport_run,bsport_other,bike_cad,bike_pwr,bike_spd,bike_speed_and_cadence,hr,run_spd,temp,bt_bike_cad,bt_bike_pwr,bt_bike_spd,bt_bike_speed_and_cadence,bt_hr,bt_run}.xml`
* Changes: Generate all 16 XML files directly from the `.fig`/`.xcf` mathematical definitions and analytical primitives, render side-by-side comparison sheet `docs/engineering/walkthroughs/img/ATT-2859_vector_comparison.png`, and verify 1:1 visual alignment.

### Step 2: Purge 106 Superseded Raster PNGs & Update `SportTypeSelector.kt`
* Files: `app/src/main/res/drawable*/*.png`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelector.kt`
* Changes: Remove the 106 raster PNGs (`git rm`) and add KDoc/inline documentation for `REQ-UI-375` in `SportTypeSelector.kt`.

### Step 3: Implement & Run Targeted Contract Tests
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelectorContractTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest" --tests "com.atrainingtracker.trainingtracker.ui.resources.DrawableDensityFallbackAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SportTypeSelectorContractTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted DOM/geometry contract tests and visual comparison in Stage 4, followed by full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-2859` allows clean revert without affecting `sprint/2026-42.7` or `develop`.
