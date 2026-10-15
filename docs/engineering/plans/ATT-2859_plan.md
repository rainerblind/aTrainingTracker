# Stage 3 Implementation Plan - ATT-2859: Faithful Scalable Jetpack Compose Vector Asset Architecture & Multi-Density Migration

**Ticket**: [ATT-2859](https://atrainingtracker.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3727](https://atrainingtracker.atlassian.net/browse/ATT-3727) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://atrainingtracker.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Branch**: `feature/ATT-2859`  
**Requirement Mapping**: `REQ-UI-375` (*Faithful Scalable Jetpack Compose Vector Asset Architecture & Multi-Density Raster Migration*)  
**Test Spec ID**: `TST-UI-338`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary & Review Post-Mortem

During Sprint 2026-42.4 review, previous merge commit `8e218312` was rejected because it replaced the app's authentic multi-color and badge icons with generic single-color monochrome Material Design icons.
The mandate for this implementation is to **faithfully redraw the existing icons as vector graphics**, preserving:
1. `bsport_bike.xml`: Black wheels (`#000000`), bold red bicycle frame (`#FF0000`), blue aerodynamic time-trial cyclist (`#0000FF`), and black saddle (`#000000`).
2. `bsport_run.xml`: Blue running stick figure (`#0000FF`) running dynamically toward the right.
3. `bsport_other.xml`: Rich gold stopwatch ring (`#FFC700`), red top crown button (`#FF0000`), green right button (`#008F00`), dark blue left button (`#00008F`), and transparent interior dial.
4. ANT+ sensor badges: Black (`#000000`) rounded square container ($r \approx 12$ px, transparent corners) with white (`#FFFFFF`) glyph and bold white text ("HR", "SPD", "CAD", "S & C", "PWR", "SPD", "TEMP").
5. BLE sensor badges: Official Bluetooth Blue (`#0A3D91`) rounded square container ($r \approx 12$ px, transparent corners) with white (`#FFFFFF`) glyphs and bold lowercase labels ("speed", "cadence", "s & c", "power").

---

## 2. Architecture & File Breakdown (SWE.2)

### 2.1 Vector Assets in `app/src/main/res/drawable/`
1. **Sport Icons (3)**:
   - `bsport_bike.xml` (Viewport 192×192, 24dp×24dp, `android:tintMode="src_in"`)
   - `bsport_run.xml` (Viewport 192×192, 24dp×24dp, `android:tintMode="src_in"`)
   - `bsport_other.xml` (Viewport 192×192, 24dp×24dp, `android:tintMode="src_in"`)
2. **ANT+ Sensor Badges (7)**:
   - `hr.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `bike_spd.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `bike_cad.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `bike_speed_and_cadence.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `bike_pwr.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `run_spd.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `temp.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
3. **Bluetooth LE Sensor Badges (6)**:
   - `bt_hr.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `bt_bike_spd.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `bt_bike_cad.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `bt_bike_speed_and_cadence.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `bt_bike_pwr.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)
   - `bt_run.xml` (Viewport 96×96, 24dp×24dp, `android:tintMode="src_in"`)

### 2.2 Tinting & Multi-Color Rendering Strategy
- **Compose Tinting Preservation**: In Jetpack Compose, the `Icon` composable applies `tint = LocalContentColor.current` by default, which flattens multi-color drawables into a single monochrome silhouette. For multi-color sport assets (`bsport_bike`, `bsport_other`) and badge assets, consumers use `Image(painter = painterResource(...), contentDescription = ...)` without a tint filter (or with `Color.Unspecified`) to ensure the multi-color paths render with complete visual fidelity.
- **`android:tintMode="src_in"`**: All XML `<vector>` definitions specify `android:tintMode="src_in"` to maintain predictable blending when legacy XML `ImageView` or Android framework tint attributes are applied.

### 2.3 Files to Purge & Code Audit
- **Code Audit on `_gray` assets**: A comprehensive scan reveals zero production code references to `bsport_bike_gray`, `bsport_run_gray`, or `bsport_other_gray` (only `SportTypeSelectorContractTest.kt` checked the files). `SportTypeSelector.kt` uses Compose alpha / selection state styling rather than separate gray bitmaps.
- **Purge Plan**:
  - Update `SportTypeSelectorContractTest.kt` first to decouple from `_gray` assertions.
  - Delete all 16 migrated `.png` files across `drawable-ldpi/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, `drawable-xxxhdpi/`, and root `drawable/`.
  - Delete all 18 instances of `bsport_bike_gray.png`, `bsport_run_gray.png`, `bsport_other_gray.png`.
  - Delete all instances of `btle_hr.png`.

### 2.4 Performance Budget & Path Node Limits
- Each vector drawable is hand-optimized with minimal control points:
  - Clean geometric primitives: circles, rectangles with `rx`/`ry`, linear strokes, and simplified cubic/quadratic beziers.
  - Path node budget: $\le 15$ commands per path, total file size $< 3\text{ KB}$ per XML.
  - Fast instantiation in recycler/list views (`DeviceDataCard`, `SportTypeSelector`) with zero GC or inflation overhead.

---

## 3. Atomic Step-by-Step Implementation Sequence

### Step 1: Update `SportTypeSelectorContractTest.kt` (Decouple from `_gray`)
- Remove obsolete assertions checking for `bsport_*_gray.png`.
- Update assertions to check for `.xml` vector resources in `app/src/main/res/drawable/`.

### Step 2: Author `VectorAssetResolutionContractTest.kt`
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/resources/VectorAssetResolutionContractTest.kt`.
- Implement tests verifying:
  - XML existence and parser validity for all 16 target assets.
  - `android:tintMode="src_in"` specification across all vector drawables.
  - Multi-color fills in `bsport_bike.xml` (black `#000000`, red `#FF0000`, blue `#0000FF`).
  - Single-color fill in `bsport_run.xml` (blue `#0000FF`).
  - Multi-color fills in `bsport_other.xml` (gold `#FFC700`, red `#FF0000`, green `#008F00`, blue `#00008F`).
  - Black background (`#000000`) and white foreground in ANT+ badges.
  - Bluetooth blue background (`#0A3D91`) and white foreground in BLE badges.

### Step 3: Author Faithful Multi-Color Sport Type Vectors
- Construct `bsport_bike.xml`:
  - Path for black wheels and saddle.
  - Path for red frame tubes.
  - Path for blue aerodynamic cyclist.
- Construct `bsport_run.xml`:
  - Path for blue stick-figure runner.
- Construct `bsport_other.xml`:
  - Path for gold stopwatch ring.
  - Path for red top crown button.
  - Path for green right angled button.
  - Path for dark blue left angled button.

### Step 4: Author Faithful ANT+ Sensor Badge Vectors
- For `hr.xml`, `bike_spd.xml`, `bike_cad.xml`, `bike_speed_and_cadence.xml`, `bike_pwr.xml`, `run_spd.xml`, `temp.xml`:
  - Background: Black (`#000000`) rounded rectangle with $r = 12$.
  - Foreground: White (`#FFFFFF`) glyph and bottom label ("HR", "SPD", "CAD", "S & C", "PWR", "SPD", "TEMP").

### Step 5: Author Faithful BLE Sensor Badge Vectors
- For `bt_hr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_bike_pwr.xml`, `bt_run.xml`:
  - Background: Bluetooth Blue (`#0A3D91`) rounded rectangle with $r = 12$.
  - Foreground: White (`#FFFFFF`) glyphs and lowercase bold labels ("speed", "cadence", "s & c", "power").

### Step 6: Purge Superseded Raster PNG Files
- Delete the 16 migrated `.png` files across all density directories (`drawable-ldpi`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`, `drawable`).
- Delete all `bsport_*_gray.png` and `btle_hr.png` files.

### Step 7: Targeted Test Suite Execution
- Run:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests com.atrainingtracker.trainingtracker.resources.VectorAssetResolutionContractTest \
    --tests com.atrainingtracker.trainingtracker.ui.sporttype.SportTypeSelectorContractTest \
    --tests com.atrainingtracker.trainingtracker.ui.resources.DrawableDensityFallbackAuditTest
  ```

### Step 8: Visual Verification & On-Device Comparison (Rule 23)
- Render new vectors side-by-side with original bitmaps using a contract/snapshot comparison script or on-device capture, confirming 1:1 geometry, proportions, colors, and absence of bounding box artifacts.

### Step 9: Full Clean-Room Regression Suite Execution
- Run `./gradlew testDebugUnitTest`.

---

## 4. UI Consistency (Rule 23)

- **Reference Screens**: `SportTypeSelector.kt`, `DeviceDataCard.kt`, `ControlTrackingScreen.kt`.
- **Visual Design Parity**: The vector assets directly replicate the exact proportions, colors, and line widths of the original 2011–2017 raster assets, eliminating blurriness on modern high-DPI displays.
- **Theme Invariant**: The transparent outer corners eliminate the off-white `#FAFAFA` bounding box artifact that previously plagued dark theme UI in `DeviceDataCard` and sensor dialogs.

---

## 5. Non-Negotiable Invariants

1. **Exact Visual Match**: No generic monochrome icons; must strictly match the original multi-color and badge designs.
2. **Resource ID Stability**: All resource IDs (`R.drawable.bsport_bike`, `R.drawable.hr`, etc.) remain identical with zero call-site signature modifications.
3. **Density-Split Immunity**: 100% of target assets reside in root `res/drawable/` as XML vector drawables.
4. **100% Clean-Room Test Pass Rate**: Full unit test suite passes with zero regressions.
