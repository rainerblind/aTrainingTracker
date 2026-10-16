# Stage 2: Requirement & Test Specification - ATT-2859: Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3855](https://rainerblind.atlassian.net/browse/ATT-3855) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.7`  
**Requirement Mapping**: `REQ-UI-375` (*Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge*)  
**Test Spec ID**: `TST-UI-338`  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Requirement Specification (`REQ-UI-375`)

### 1.1 Problem Statement & Rationale
Legacy multi-density PNG raster drawables for the 16 sport type and ANT+/BLE sensor icons (`bsport_bike`, `bsport_run`, `bsport_other`, `bike_cad`, `bike_pwr`, `bike_spd`, `bike_speed_and_cadence`, `hr`, `run_spd`, `temp`, `bt_bike_cad`, `bt_bike_pwr`, `bt_bike_spd`, `bt_bike_speed_and_cadence`, `bt_hr`, `bt_run`) across `drawable-mdpi`, `-hdpi`, `-xhdpi`, `-xxhdpi`, `-xxxhdpi`, and root `drawable` suffer from density-split fragility, blurry scaling on high-DPI displays, and redundant `_gray.png` bitmaps. Meanwhile, the authoritative mathematical Xfig 3.2 (`.fig`), SVG (`.svg`), and GIMP (`.xcf`) master files exist in `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/` and must be translated directly into density-independent Android `VectorDrawable` XML assets in `app/src/main/res/drawable/` in strict compliance with Rule 37 (`docs/design_guidelines.md` Section 5.5).

### 1.2 Functional & Architectural Requirements
1. **Direct Mathematical Translation from `.fig` / `.svg` / `.xcf` Masters (`REQ-UI-375.1` — Rule 37)**:
   * `bsport_bike.xml`, `bsport_run.xml`, and `bsport_other.xml` SHALL be generated directly by translating the mathematical Xfig 3.2 primitives in `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/logo.fig`, `Bluetooth/bike.fig`, and `Bluetooth/runner.fig` (`1` analytical circles, `5` three-point circular arcs, `2` stroked polylines with exact `strokeWidth`, `strokeLineCap`, `strokeLineJoin`, and `3` X-splines) in back-to-front Z-depth order (`depth 70` down to `depth 50`) with exact multi-color palettes (`bsport_bike`: `#000000` wheels/saddle, `#FF0000` frame, `#0000FF` rider; `bsport_run`: `#0000FF` runner; `bsport_other`: `#F0F0F0` dial, `#FFC700` ring/stems, `#FF0000` crown, `#000090` left button, `#009000` right button).
   * `bt_bike_cad.xml`, `bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, and `bt_run.xml` SHALL be generated directly from the `1400×1400` GIMP `.xcf` master compositions (`BT_BIKE_CADENCE.xcf`, `BT_BIKE_POWER.xcf`, `BT_BIKE_SPEED.xcf`, `BT_BIKE_SPEED_AND_CADENCE.xcf`, `BT_HR.xcf`, `BT_RUN.xcf`) combining the Bluetooth Blue (`#07377F`) rounded-square badge with the mathematical white Xfig geometry from `bike.fig`, `runner.fig`, and `BT_HR_just_the_heart.fig` (quadratic B-spline heart contour + end-cap polygons) and exact vector font outlines (`android:fillType="evenOdd"`) for `cadence`, `power`, `speed`, and `s & c`.
2. **Analytical Geometric Reconstruction of ANT+ Sensor Badges (`REQ-UI-375.2` — Rule 37)**:
   * `bike_cad.xml`, `bike_pwr.xml`, `bike_spd.xml`, `bike_speed_and_cadence.xml`, `hr.xml`, `run_spd.xml`, and `temp.xml` SHALL use the canonical black (`#000000`) rounded-square container (`96×96` viewport, corner radius $r = 10$) with white (`#FFFFFF`) analytical geometric emblems (circles, arcs, Cubic Beziers, rounded strokes) and clean vector font outlines (`CAD`, `PWR`, `SPD`, `S&C`, `HR`, `TEMP`) with `android:fillType="evenOdd"` for all counter-holes.
3. **Elimination of Redundant Raster & `_gray` Assets (`REQ-UI-375.3`)**:
   * All 106 superseded raster PNG files across `drawable`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, and `drawable-xxxhdpi` (including the 18 dead `bsport_*_gray.png` files and 5 orphaned `btle_hr.png` files) SHALL be deleted.
   * `SportTypeSelector.kt` SHALL preserve multi-color rendering (`tint = Color.Unspecified`) and document the Rule 37 multi-color emblem invariant.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Mathematical VectorDrawable Presence & Rule 37 Compliance)**:
  * *Given* the 16 migrated icon resource names in `app/src/main/res/drawable/`,
  * *When* inspected by `VectorAssetResolutionContractTest`,
  * *Then* each exists as a valid XML `<vector>` drawable containing true curved SVG path commands (`A` and/or `C`) and proper `strokeWidth` or `fillType="evenOdd"` attributes, with zero density-qualified PNG duplicates remaining in `drawable*`.
* **Criterion 2 (1:1 Visual Fidelity with Master PNGs)**:
  * *Given* each of the 16 XML `VectorDrawable`s rendered to a raster bitmap at reference resolution,
  * *When* compared side-by-side against the original `xxxhdpi`/`xxhdpi`/`xhdpi` PNGs (`ATT-2859_vector_comparison.png`),
  * *Then* every icon matches the original visual design, multi-color geometry, Z-order layering, and typography 1:1.
* **Criterion 3 (Multi-Color Unchanged Rendering in UI)**:
  * *Given* `SportTypeSelector.kt`,
  * *When* rendering sport icons,
  * *Then* `Icon` specifies `tint = Color.Unspecified` so multi-color vector paths render with their native sRGB colors.

### 1.4 System Invariants
* 100% pass rate on `DrawableDensityFallbackAuditTest` and `ComposeSafePainterContractTest`.
* Zero `Resources$NotFoundException` across all screen densities.
* 100% pass rate on `./gradlew testDebugUnitTest`.

---

## 2. Test Specification (`TST-UI-338`)

### Test Case 1: `VectorAssetResolutionContractTest` (`TST-UI-338.1`)
* **Scope**: Unit / DOM Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* **Preconditions**: Repository resource tree `app/src/main/res/`.
* **Action**:
  1. Verify all 16 `.xml` vector drawables exist in `app/src/main/res/drawable/`, parse cleanly as XML `<vector>` documents, and include provenance headers (`REQ-UI-375`, `TST-UI-338`).
  2. Verify every `<vector>` contains curved geometric primitives (`A`, `a`, `C`, `c`, `Q`, `q`) and either stroked Xfig primitives (`android:strokeWidth`) or compound `evenOdd` paths (`android:fillType="evenOdd"`), enforcing Rule 37.
  3. Verify multi-color fidelity in `bsport_bike.xml` (`#000000`, `#FF0000`, `#0000FF`), `bsport_run.xml` (`#0000FF`), `bsport_other.xml` (`#F0F0F0`, `#FFC700`, `#FF0000`, `#000090`, `#009000`), ANT+ badges (`#000000` + `#FFFFFF`), and BLE badges (`#07377F` + `#FFFFFF`).
  4. Verify zero `.png` files remain for the 16 migrated icons, `bsport_*_gray.png`, or `btle_hr.png` across all `drawable*` directories.
* **Expected Result**: All DOM contract assertions pass 100%.

### Test Case 2: `SportTypeSelectorContractTest` & `DrawableDensityFallbackAuditTest` (`TST-UI-338.2`)
* **Scope**: Architectural Contract & Resource Density Fallback Tests
* **Target Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelectorContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/DrawableDensityFallbackAuditTest.kt`
* **Action**: Verify `SportTypeSelector.kt` preserves `tint = Color.Unspecified` with `REQ-UI-375` documentation and `DrawableDensityFallbackAuditTest` passes with zero missing root fallbacks.
* **Expected Result**: 100% pass rate.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-338.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-338.1` | DOM & Geometry Contract | `VectorAssetResolutionContractTest` | `REQ-UI-375` | Specified |
| `TST-UI-338.2` | UI & Density Audit | `SportTypeSelectorContractTest`, `DrawableDensityFallbackAuditTest` | `REQ-UI-375`, `REQ-UI-312` | Specified |
| `TST-UI-338.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
