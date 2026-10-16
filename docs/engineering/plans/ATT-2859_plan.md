# Stage 3: Implementation Plan - ATT-2859: Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3787](https://rainerblind.atlassian.net/browse/ATT-3787) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Active Sprint**: `2026-42.6`  
**Requirement Mapping**: `REQ-UI-375`  
**Test Mapping**: `TST-UI-338`  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Description & Background

`ATT-2859` replaces the 16 legacy multi-density sport and sensor PNG raster drawables (`bsport_bike`, `bsport_run`, `bsport_other`, `hr`, `bike_spd`, `bike_cad`, `bike_speed_and_cadence`, `bike_pwr`, `run_spd`, `temp`, `bt_hr`, `bt_bike_spd`, `bt_bike_cad`, `bt_bike_speed_and_cadence`, `bt_bike_pwr`, `bt_run`) and 18 duplicate `_gray.png` / 5 `btle_hr.png` files (106 raster files total) with 16 canonical XML `VectorDrawable` resources in `app/src/main/res/drawable/` constructed from true geometric primitives (`A` arcs/circles, `C` Cubic Beziers, rounded strokes, back-to-front Z-order, and `android:fillType="evenOdd"`) in strict compliance with Rule 37 (`docs/design_guidelines.md` Section 5.5).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-375` (*Faithful Multi-Color Vector Drawable Migration & True Geometric Primitives Architecture*)
* **Test Mapping**: `TST-UI-338` (*Faithful Multi-Color Vector Drawable Migration & Rule 37 True Geometric Primitives Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Rule 37 True Geometric Primitives**: Every vector asset uses analytical arcs (`A`), Cubic Beziers (`C`), rounded strokes, back-to-front layer composition, and `evenOdd` winding — zero naive `M...L...Z` polygon auto-tracing.
2. **1:1 Visual Fidelity**: Every generated XML `VectorDrawable` matches the exact visual design, multi-color geometry, and typography of its `xxxhdpi`/`xxhdpi`/`xhdpi` reference PNG.
3. **Zero Unintended Regressions**: All `R.drawable.*` IDs remain unchanged; `DrawableDensityFallbackAuditTest` and the full clean-room unit test suite pass 100%.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Multi-Color Sport Type Vector Drawables (`app/src/main/res/drawable/bsport_*.xml`)
Construct `bsport_bike.xml`, `bsport_run.xml`, and `bsport_other.xml` on a `192×192` viewport (`48dp × 48dp` intrinsic size):
- **`bsport_bike.xml`**:
  - Layer 1 (Back — Black `#000000`): Complete rear wheel circular ring $(48.5, 126.0, R=36.5, r=18.5)$ with `evenOdd`, complete front wheel circular ring $(144.5, 129.5, R=35.0, r=18.0)$ with `evenOdd`, and black saddle nose rectangle.
  - Layer 2 (Middle — Red `#FF0000`): Continuous bicycle frame (seat tube, seatstay, chainstay, top tube, down tube, and front fork) drawn behind the rider.
  - Layer 3 (Front — Blue `#0000FF`): Analytical circular head $(147.0, 42.5, r=14.0)$, Cubic Bezier arched torso/back, rounded-stroke upper arm and aerobar forearm, and rounded-stroke front and rear legs/feet.
- **`bsport_run.xml`**:
  - Analytical circular head $(113.0, 29.0, r=15.5)$ and rounded-cap/rounded-join blue (`#0000FF`) paths for torso, rear bent arm, front bent arm, lifted right leg, and extended push-off left leg.
- **`bsport_other.xml`**:
  - Layer 1: Dial face circle $(96.0, 108.5, r=62.0)$ in `#EFEFEF`.
  - Layer 2: Gold stopwatch ring $(96.0, 108.5, R=68.5, r=59.5)$ with `evenOdd` and top/side crown stems in `#FFC000`.
  - Layer 3: Red center crown (`#FF0000`), dark blue left button (`#000080`), and green right button (`#008000`).

### Component 2: ANT+ Sensor Badge Vector Drawables (`app/src/main/res/drawable/{hr,bike_spd,bike_cad,bike_speed_and_cadence,bike_pwr,run_spd,temp}.xml`)
Construct all 7 ANT+ badges on a `96×96` viewport (`48dp × 48dp` intrinsic size):
- Layer 1: Black (`#000000`) rounded-square background path (`M 10,0 L 86,0 A 10,10 0 0 1 96,10 L 96,86 A 10,10 0 0 1 86,96 L 10,96 A 10,10 0 0 1 0,86 L 0,10 A 10,10 0 0 1 10,0 Z`).
- Layer 2: White (`#FFFFFF`) geometric device glyph (cyclist with analytical hollow wheel rings and rounded-join rider body; symmetrical Cubic Bezier heart outline; Cubic Bezier footprint sole + 5 analytical circular/elliptical toes; analytical thermometer bulb + rounded stem + highlight arc + rounded tick marks).
- Layer 3: Crisp bold uppercase white (`#FFFFFF`) geometric vector typography (`CAD`, `PWR`, `SPD`, `S&C`, `HR`, `TEMP`) with `android:fillType="evenOdd"` for all counter-holes (`A`, `D`, `P`, `R`, `&`) and curved `A`/`C` bowl arcs.

### Component 3: Bluetooth LE Sensor Badge Vector Drawables (`app/src/main/res/drawable/bt_*.xml`)
Construct all 6 BLE badges on a `144×144` viewport (`48dp × 48dp` intrinsic size):
- Layer 1: Bluetooth Blue (`#07377F`) rounded-square badge (`(10.5, 10.5)` to `(133.5, 133.5)`, corner radius $r = 13.5$ using `A 13.5,13.5` arcs).
- Layer 2: White (`#FFFFFF`) geometric silhouette (`bsport_bike` geometry scaled to badge upper zone for `bt_bike_cad`, `bt_bike_pwr`, `bt_bike_spd`, `bt_bike_speed_and_cadence`; large symmetrical Cubic Bezier heart outline for `bt_hr`; `bsport_run` runner geometry centered for `bt_run`).
- Layer 3: Crisp bold lowercase white (`#FFFFFF`) geometric vector typography (`cadence`, `power`, `speed`, `s & c`) with `android:fillType="evenOdd"` and curved `A`/`C` bowls.

### Component 4: Legacy Raster Purge & Contract Tests
- Delete the 106 superseded `.png` files across `drawable/`, `drawable-ldpi/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, and `drawable-xxxhdpi/`.
- Update `SportTypeSelector.kt` KDoc and `SportTypeSelectorContractTest.kt` to assert `.xml` vector drawables and absence of `_gray.png`.
- Create `VectorAssetResolutionContractTest.kt` asserting DOM validity, positive dimensions, presence of curved `A`/`C` commands in every vector (Rule 37), multi-color palette fidelity, and complete removal of the 106 legacy PNG files.

### UI Consistency (Rule 23)
* **Reference screen / component**: Existing raster PNGs in `app/src/main/res/drawable-xxxhdpi/` (`bsport_bike.png`, `bsport_run.png`, `bsport_other.png`), `drawable-xxhdpi/` (`bt_*.png`), and `drawable-xhdpi/` (`bike_*.png`, `hr.png`, `run_spd.png`, `temp.png`).
* **Reused components**: `SportTypeSelector.kt`, `DeviceItem.kt`, `RemoteDevices.kt` (no layout changes; existing `safePainterResource` / `painterResource` calls resolve the new XML vectors seamlessly).
* **Theme tokens**: Exact sRGB icon emblem palette (`#000000`, `#FF0000`, `#0000FF`, `#FFC000`, `#EFEFEF`, `#000080`, `#008000`, `#07377F`, `#FFFFFF`) per Rule 37.
* **New one-off styles & justification**: None (1:1 faithful vectorization of existing icons).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Gate 3 Pre-Check (`Rule 4`)
* Run `python3 tools/jira_util.py check-gate ATT-3787` and confirm `GATE_PASSED`.

### Step 2: Construct 16 True-Geometric XML VectorDrawables & Visually Verify Against Reference PNGs
* Files: `app/src/main/res/drawable/{bsport_bike,bsport_run,bsport_other,hr,bike_spd,bike_cad,bike_speed_and_cadence,bike_pwr,run_spd,temp,bt_hr,bt_bike_spd,bt_bike_cad,bt_bike_speed_and_cadence,bt_bike_pwr,bt_run}.xml`
* Render side-by-side comparison sheet against reference PNGs and inspect via `view_file` to verify 1:1 visual fidelity.

### Step 3: Purge 106 Superseded Raster PNGs Across All Density Buckets
* Remove the 16 migrated `.png` files, 18 `bsport_*_gray.png` files, and 5 `btle_hr.png` files across `drawable*` directories.

### Step 4: Update `SportTypeSelectorContractTest.kt` and Create `VectorAssetResolutionContractTest.kt`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelector.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelectorContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SportTypeSelectorContractTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Visual side-by-side comparison sheet + targeted unit tests (`VectorAssetResolutionContractTest`, `DrawableDensityFallbackAuditTest`, `SportTypeSelectorContractTest`) in Stage 4, followed by full `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: `git checkout sprint/2026-42.6` cleanly isolates any branch changes.
