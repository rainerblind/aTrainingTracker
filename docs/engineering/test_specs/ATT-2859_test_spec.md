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

### 1.2 Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)

| Mandatory Field | Audit Analysis |
| :--- | :--- |
| **Original Requirement ID & Target** | Refines `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*, `ATT-2858`) and `REQ-UI-380` (*Complete High-Density Asset Coverage & Defensive Compose Resource Resolution*, `ATT-3529`). Introduces `REQ-UI-375`. |
| **Historical Origin & Commit Trace** | Sprint `2026-41.5` (`ATT-2858`, commit `d82a9115`) duplicated density PNGs into root `res/drawable/` as an emergency stopgap against Play Store AAB density-split `Resources$NotFoundException` crashes. Sprint `2026-42.5` (`ATT-3529`, commit `1b4a62ef`) carved out these 16 sport and sensor icons for vector migration under `ATT-2859`. |
| **Root Reason for Existing Formulation** | Pre-Compose Android (`2011–2015`) lacked `VectorDrawable` support (`API 21+`) and Compose dynamic `ColorFilter` / alpha styling, requiring pre-rendered PNGs across 5 density buckets (`mdpi`..`xxxhdpi`) plus separate `_gray.png` bitmaps for unselected states. |
| **Preservation of Core Invariants** | Replacing all 106 density-qualified raster PNGs with 16 canonical XML `VectorDrawable`s in root `app/src/main/res/drawable/` guarantees 100% base-APK inclusion (satisfying `DrawableDensityFallbackAuditTest` and `REQ-UI-312` with zero AAB split stripping risk), preserves all 16 `R.drawable.*` resource IDs, and achieves 1:1 visual fidelity from the original `.fig`/`.svg`/`.xcf` vector masters. |

### 1.3 Functional & Architectural Requirements
1. **Direct Mathematical Translation from `.fig` / `.svg` / `.xcf` Masters (`REQ-UI-375.1` — Rule 37)**:
   * `bsport_bike.xml`, `bsport_run.xml`, and `bsport_other.xml` SHALL be generated directly by translating the mathematical Xfig 3.2 primitives in `/home/rainer/Dropbox/workspace/TrainingTracker_with_BT_and_Map5/Bilder/logo.fig`, `Bluetooth/bike.fig`, and `Bluetooth/runner.fig` (`1` analytical circles, `5` three-point circular arcs, `2` stroked polylines with exact `strokeWidth`, `strokeLineCap`, `strokeLineJoin`, and `3` X-splines) in back-to-front Z-depth order (`depth 70` down to `depth 50`) with exact multi-color palettes (`bsport_bike`: `#000000` wheels/saddle, `#FF0000` frame, `#0000FF` rider; `bsport_run`: `#0000FF` runner; `bsport_other`: `#F0F0F0` dial, `#FFC700` ring/stems, `#FF0000` crown, `#000090` left button, `#009000` right button).
   * `bt_bike_cad.xml`, `bt_bike_pwr.xml`, `bt_bike_spd.xml`, `bt_bike_speed_and_cadence.xml`, `bt_hr.xml`, and `bt_run.xml` SHALL be generated directly from the `1400×1400` GIMP `.xcf` master compositions (`BT_BIKE_CADENCE.xcf`, `BT_BIKE_POWER.xcf`, `BT_BIKE_SPEED.xcf`, `BT_BIKE_SPEED_AND_CADENCE.xcf`, `BT_HR.xcf`, `BT_RUN.xcf`) combining the Bluetooth Blue (`#07377F`) rounded-square badge with the mathematical white Xfig geometry from `bike.fig`, `runner.fig`, and `BT_HR_just_the_heart.fig` (quadratic B-spline heart contour + end-cap polygons) and exact vector font outlines (`android:fillType="evenOdd"`) for `cadence`, `power`, `speed`, and `s & c`.
   * **Anti-Polygon-Tracing Complexity Bound**: No `<path>` element in any of the 16 XML files may contain naive polygon auto-tracing (`L`-command count per `<path>` SHALL be strictly bounded $\le 45$, and every `<vector>` SHALL contain true analytical curve commands `A`, `C`, or `Q`).
2. **Analytical Geometric Reconstruction of ANT+ Sensor Badges (`REQ-UI-375.2` — Rule 37)**:
   * `bike_cad.xml`, `bike_pwr.xml`, `bike_spd.xml`, `bike_speed_and_cadence.xml`, `hr.xml`, `run_spd.xml`, and `temp.xml` SHALL use the canonical black (`#000000`) rounded-square container (`96×96` viewport, corner radius $r = 10$) with white (`#FFFFFF`) analytical geometric emblems (circles, arcs, Cubic Beziers, rounded strokes) and clean vector font outlines (`CAD`, `PWR`, `SPD`, `S&C`, `HR`, `TEMP`) with `android:fillType="evenOdd"` for all counter-holes.
3. **Elimination of Redundant Raster & `_gray` Assets & Dynamic State Styling (`REQ-UI-375.3`)**:
   * All 106 superseded raster PNG files across `drawable`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, and `drawable-xxxhdpi` (83 active icon PNGs, 18 dead `bsport_*_gray.png` PNGs, and 5 orphaned `btle_hr.png` PNGs) SHALL be deleted from the source tree so zero legacy raster copies are packaged into the APK/AAB.
   * `SportTypeSelector.kt` SHALL preserve native multi-color rendering (`tint = Color.Unspecified`) in active states while supporting programmatic Compose alpha/desaturation (`ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })` / container styling) without requiring duplicate `_gray.png` raster files.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Mathematical VectorDrawable Presence & Path Complexity Bound)**:
  * *Given* the 16 migrated icon resource names in `app/src/main/res/drawable/`,
  * *When* inspected by `VectorAssetResolutionContractTest`,
  * *Then* each exists as a valid XML `<vector>` drawable containing true curved SVG path commands (`A`, `C`, or `Q`), proper `strokeWidth` or `fillType="evenOdd"` attributes, and strictly $\le 45$ `L` commands per `<path>` (preventing naive polygon auto-tracing).
* **Criterion 2 (Complete Deletion of 106 Legacy Raster PNGs)**:
  * *Given* all `app/src/main/res/drawable*` directories (`drawable`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`),
  * *When* scanned by `VectorAssetResolutionContractTest`,
  * *Then* strictly `0` `.png` files exist for the 16 migrated icons, `bsport_bike_gray`, `bsport_run_gray`, `bsport_other_gray`, and `btle_hr`.
* **Criterion 3 (Programmatic Pixel/IoU Fidelity & Active/Gray State Verification)**:
  * *Given* each of the 16 XML `VectorDrawable`s rasterized at reference resolution and compared against the original `xxxhdpi`/`xxhdpi`/`xhdpi` master PNGs,
  * *When* evaluated by the programmatic verification harness and `SportTypeSelectorContractTest`,
  * *Then* every icon matches the reference geometry and color palette (`IoU >= 0.85` / 1:1 visual overlay in `ATT-2859_vector_comparison.png`), and `SportTypeSelector.kt` preserves native multi-color rendering (`tint = Color.Unspecified`) with documented gray/desaturation capability.

### 1.5 System Invariants
* 100% pass rate on `DrawableDensityFallbackAuditTest` and `ComposeSafePainterContractTest`.
* Zero `Resources$NotFoundException` across all screen densities.
* 100% pass rate on `./gradlew testDebugUnitTest`.

---

## 2. Test Specification (`TST-UI-338`)

### Test Case 1: `VectorAssetResolutionContractTest` — DOM, Curve Complexity & Raster Deletion Audit (`TST-UI-338.1`)
* **Scope**: Unit / DOM & Geometry Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* **Preconditions**: Repository resource tree `app/src/main/res/`.
* **Action**:
  1. **Existence & Provenance**: Verify all 16 `.xml` vector drawables exist in `app/src/main/res/drawable/`, parse cleanly as XML `<vector>` documents with valid positive `viewportWidth`/`viewportHeight`, and include provenance headers (`REQ-UI-375`, `TST-UI-338`).
  2. **Rule 37 Curve & Path Complexity Check**: Verify every `<vector>` contains curved geometric primitives (`A`, `a`, `C`, `c`, `Q`, `q`) and either stroked Xfig primitives (`android:strokeWidth`) or compound `evenOdd` paths (`android:fillType="evenOdd"`), AND assert that no individual `<path>` exceeds `45` `L` line-to commands (enforcing the "No Naive Polygon Auto-Tracing" rule).
  3. **Multi-Color Palette Fidelity**: Verify exact hex colors in `bsport_bike.xml` (`#000000`, `#FF0000`, `#0000FF`), `bsport_run.xml` (`#0000FF`), `bsport_other.xml` (`#F0F0F0`, `#FFC700`, `#FF0000`, `#000090`, `#009000`), ANT+ badges (`#000000` + `#FFFFFF`), and BLE badges (`#07377F` + `#FFFFFF`).
  4. **Verification of Deletion (All 106 Legacy PNGs)**: Scan all 6 `drawable*` directories (`drawable`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`) and assert that strictly `0` `.png` files exist for any of the 16 migrated icon names, `bsport_bike_gray`, `bsport_run_gray`, `bsport_other_gray`, or `btle_hr`, ensuring zero legacy raster assets can be packaged into the APK/AAB.
* **Expected Result**: All DOM, path-complexity, palette, and raster-deletion assertions pass 100%.

### Test Case 2: `SportTypeSelectorContractTest` & Programmatic Visual Regression Audit (`TST-UI-338.2`)
* **Scope**: UI Contract, Active/Desaturated State & Visual Regression Verification
* **Target Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelectorContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/DrawableDensityFallbackAuditTest.kt`
* **Action**:
  1. Verify `SportTypeSelector.kt` preserves native multi-color rendering (`tint = Color.Unspecified`) in active states, documents the `REQ-UI-375` dynamic desaturation contract (`ColorMatrix().apply { setToSaturation(0f) }` replacing legacy `bsport_*_gray.png`), and passes `SportTypeSelectorContractTest`.
  2. Execute programmatic bitmap rasterization and pixel overlap (`IoU` / bounding-box & color channel correlation) verification across all 16 generated XML `VectorDrawable`s against the original `xxxhdpi`/`xxhdpi`/`xhdpi` PNGs, producing `docs/engineering/walkthroughs/img/ATT-2859_vector_comparison.png`.
  3. Execute `DrawableDensityFallbackAuditTest` and `ComposeSafePainterContractTest` verifying 100% resource resolution parity.
* **Expected Result**: 100% pass rate across all contract, visual fidelity, and fallback audit tests.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-338.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-338.1` | DOM, Path Complexity & Raster Deletion | `VectorAssetResolutionContractTest` | `REQ-UI-375.1`, `REQ-UI-375.2`, `REQ-UI-375.3` | Specified |
| `TST-UI-338.2` | Active/Gray State, Pixel Fidelity & Density Audit | `SportTypeSelectorContractTest`, `DrawableDensityFallbackAuditTest` | `REQ-UI-375.1`, `REQ-UI-375.3`, `REQ-UI-312` | Specified |
| `TST-UI-338.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
