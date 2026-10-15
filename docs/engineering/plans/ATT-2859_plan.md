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
   - `bsport_bike.xml` (Viewport 192×192, 24dp×24dp)
   - `bsport_run.xml` (Viewport 192×192, 24dp×24dp)
   - `bsport_other.xml` (Viewport 192×192, 24dp×24dp)
2. **ANT+ Sensor Badges (7)**:
   - `hr.xml` (Viewport 96×96, 24dp×24dp)
   - `bike_spd.xml` (Viewport 96×96, 24dp×24dp)
   - `bike_cad.xml` (Viewport 96×96, 24dp×24dp)
   - `bike_speed_and_cadence.xml` (Viewport 96×96, 24dp×24dp)
   - `bike_pwr.xml` (Viewport 96×96, 24dp×24dp)
   - `run_spd.xml` (Viewport 96×96, 24dp×24dp)
   - `temp.xml` (Viewport 96×96, 24dp×24dp)
3. **Bluetooth LE Sensor Badges (6)**:
   - `bt_hr.xml` (Viewport 96×96, 24dp×24dp)
   - `bt_bike_spd.xml` (Viewport 96×96, 24dp×24dp)
   - `bt_bike_cad.xml` (Viewport 96×96, 24dp×24dp)
   - `bt_bike_speed_and_cadence.xml` (Viewport 96×96, 24dp×24dp)
   - `bt_bike_pwr.xml` (Viewport 96×96, 24dp×24dp)
   - `bt_run.xml` (Viewport 96×96, 24dp×24dp)

### 2.2 Files to Purge
- All 16 migrated `.png` files across `drawable-ldpi/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, `drawable-xxxhdpi/`, and root `drawable/`.
- All 18 instances of `bsport_bike_gray.png`, `bsport_run_gray.png`, `bsport_other_gray.png`.
- All instances of `btle_hr.png`.

### 2.3 Test Classes to Create / Update
- `VectorAssetResolutionContractTest.kt` (New): Validates vector XML parsing, viewport bounds, multi-color path fills, and badge background colors.
- `SportTypeSelectorContractTest.kt` (Update): Modernize assertions to verify `.xml` resource resolution instead of legacy `.png` or dead `_gray` assets.
- `DrawableDensityFallbackAuditTest.kt` (Existing): Verify 100% root fallback compliance.

---

## 3. Atomic Step-by-Step Implementation Sequence

### Step 1: Author `VectorAssetResolutionContractTest.kt`
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/resources/VectorAssetResolutionContractTest.kt`.
- Implement tests verifying:
  - XML existence and parser validity for all 16 target assets.
  - Multi-color fills in `bsport_bike.xml` (black `#000000`, red `#FF0000`, blue `#0000FF`).
  - Single-color fill in `bsport_run.xml` (blue `#0000FF`).
  - Multi-color fills in `bsport_other.xml` (gold `#FFC700`, red `#FF0000`, green `#008F00`, blue `#00008F`).
  - Black background (`#000000`) and white foreground in ANT+ badges.
  - Bluetooth blue background (`#0A3D91`) and white foreground in BLE badges.

### Step 2: Author Faithful Multi-Color Sport Type Vectors
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

### Step 3: Author Faithful ANT+ Sensor Badge Vectors
- For `hr.xml`, `bike_spd.xml`, `bike_cad.xml`, `bike_speed_and_cadence.xml`, `bike_pwr.xml`, `run_spd.xml`, `temp.xml`:
  - Background: Black (`#000000`) rounded rectangle with $r = 12$.
  - Foreground: White (`#FFFFFF`) glyph and bottom label ("HR", "SPD", "CAD", "S & C", "PWR", "SPD", "TEMP").

### Step 4: Author Faithful BLE Sensor Badge Vectors
- For `bt_hr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_bike_pwr.xml`, `bt_run.xml`:
  - Background: Bluetooth Blue (`#0A3D91`) rounded rectangle with $r = 12$.
  - Foreground: White (`#FFFFFF`) glyphs and lowercase bold labels ("speed", "cadence", "s & c", "power").

### Step 5: Purge Superseded Raster PNG Files
- Delete the 16 migrated `.png` files across all density directories (`drawable-ldpi`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`, `drawable`).
- Delete all `bsport_*_gray.png` and `btle_hr.png` files.

### Step 6: Update `SportTypeSelectorContractTest.kt`
- Modernize test assertions to check for `.xml` vector resources and remove legacy `_gray` checks.

### Step 7: Targeted Test Suite Execution
- Run:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests com.atrainingtracker.trainingtracker.resources.VectorAssetResolutionContractTest \
    --tests com.atrainingtracker.trainingtracker.ui.sporttype.SportTypeSelectorContractTest \
    --tests com.atrainingtracker.trainingtracker.ui.resources.DrawableDensityFallbackAuditTest
  ```

### Step 8: Full Clean-Room Regression Suite Execution
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
