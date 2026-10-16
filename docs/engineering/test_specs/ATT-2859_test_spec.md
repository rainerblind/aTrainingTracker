# Stage 2: Requirement & Test Specification - ATT-2859: Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3786](https://rainerblind.atlassian.net/browse/ATT-3786) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Active Sprint**: `2026-42.6`  
**Requirement Mapping**: `REQ-UI-375` (*Faithful Multi-Color Vector Drawable Migration & True Geometric Primitives Architecture*)  
**Test Spec ID**: `TST-UI-338`  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Requirement Specification (`REQ-UI-375`)

### 1.1 Problem Statement & Rationale
Legacy multi-density raster PNG drawables for sport types (`bsport_bike`, `bsport_run`, `bsport_other`) and ANT+/Bluetooth LE sensor badges (`hr`, `bike_spd`, `bike_cad`, `bike_speed_and_cadence`, `bike_pwr`, `run_spd`, `temp`, `bt_hr`, `bt_bike_spd`, `bt_bike_cad`, `bt_bike_speed_and_cadence`, `bt_bike_pwr`, `bt_run`) across density buckets (`mdpi`..`xxxhdpi`) are susceptible to density-split omissions, exhibit rasterization blur on high-DPI displays, and duplicate dead `_gray.png` assets. Previous vectorization attempts failed because replacing multi-color badges with monochrome Material icons lost visual identity, and naive pixel polygon auto-tracing (`M...L...Z`) produced jagged wheels, missing occluded frame segments, and filled-in letter holes. Rule 37 mandates 1:1 visual fidelity using true geometric primitives (`A` arcs/circles, `C` Cubic Beziers, rounded strokes, back-to-front layer composition, and `android:fillType="evenOdd"`).

### 1.2 Functional & Architectural Requirements
1. **16 Density-Independent XML VectorDrawables (`REQ-UI-375.1`)**:
   - The system SHALL provide 16 canonical XML `VectorDrawable` resources in `app/src/main/res/drawable/`:
     - Multi-color sport icons (3): `bsport_bike.xml`, `bsport_run.xml`, `bsport_other.xml`.
     - ANT+ sensor badges (7): `hr.xml`, `bike_spd.xml`, `bike_cad.xml`, `bike_speed_and_cadence.xml`, `bike_pwr.xml`, `run_spd.xml`, `temp.xml`.
     - Bluetooth LE sensor badges (6): `bt_hr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_bike_pwr.xml`, `bt_run.xml`.
2. **True Geometric Primitives & Back-to-Front Layer Construction (`REQ-UI-375.2` — Rule 37)**:
   - Every migrated `VectorDrawable` SHALL construct curved geometry (wheels, heads, stopwatch rings, badge rounded corners, hearts, footprint contours, thermometer bulbs, and curved typography glyphs) using true analytical SVG path curve commands (`A`/`a` elliptical arcs or `C`/`c`/`Q`/`q`/`S`/`s` Bezier curves) and/or rounded vector strokes (`android:strokeLineCap="round"`, `android:strokeLineJoin="round"`).
   - Multi-layer icons (`bsport_bike.xml`, `bsport_other.xml`) SHALL order `<path>` elements from back to front so background layers (full circular wheels, continuous diamond bicycle frame, stopwatch dial face and ring) are complete behind foreground layers without polygon fragmentation.
   - Compound paths containing interior counter-holes (hollow wheel rings, stopwatch ring, heart outlines, and typography letters such as `A`, `B`, `D`, `O`, `P`, `R`, `&`, `a`, `d`, `e`, `o`, `p`) SHALL specify `android:fillType="evenOdd"` (or be drawn as stroked paths), strictly preventing filled-in interior holes.
   - Naive polygon-only (`M`/`L`/`Z` only) auto-traced paths are strictly prohibited.
3. **Purge of Obsolete Density-Specific & Duplicate Gray Bitmaps (`REQ-UI-375.3`)**:
   - All 106 superseded `.png` files for these 16 drawables across `drawable/`, `drawable-ldpi/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, and `drawable-xxxhdpi/`, including all 18 `bsport_*_gray.png` files and 5 orphaned `btle_hr.png` files, SHALL be removed from the repository.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirement `REQ-UI-375`, complementing `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*, Sprint 2026-41.5, `ATT-2858`) and `REQ-UI-380` (*Complete High-Density Asset Coverage & Defensive Compose Resource Resolution*, Sprint 2026-42.5, `ATT-3529`). Targets `app/src/main/res/drawable/*.xml`, `SportTypeSelectorContractTest.kt`, and `VectorAssetResolutionContractTest.kt`.
2. **Historical Origin & Commit Trace**: Sprint 2026-41.5 (`ATT-2858`) added root drawable fallbacks; Sprint 2026-42.4 (`8e218312`) and Sprint 2026-42.5 (`9808cc80`, reverted in `ede8a293`) attempted vector migration of these 16 icons.
3. **Root Reason for Existing Formulation**: `REQ-UI-312` required every drawable resource to resolve in the default `res/drawable/` directory so density-split installs never throw `Resources.NotFoundException`.
4. **Preservation of Core Invariants**: Placing all 16 canonical XML `VectorDrawable`s directly in `app/src/main/res/drawable/` and removing the density-specific PNG copies satisfies `DrawableDensityFallbackAuditTest` with 100% compliance, preserves all `R.drawable.*` resource IDs, and satisfies Rule 37 1:1 visual fidelity.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Vector Resolution & Legacy Raster Purge)**:
  * *Given* the 16 sport and sensor drawable resource names,
  * *When* inspecting `app/src/main/res/`,
  * *Then* each exists as a valid XML `<vector>` in `app/src/main/res/drawable/<name>.xml` and zero `.png` copies, `_gray.png` copies, or `btle_hr.png` copies remain in any `drawable*` directory.
* **Criterion 2 (Rule 37 True Geometric Primitives & Winding)**:
  * *Given* each of the 16 XML `<vector>` files in `app/src/main/res/drawable/`,
  * *When* parsed via XML DOM in `VectorAssetResolutionContractTest`,
  * *Then* every vector contains curved geometric path commands (`A`/`a` or `C`/`c`) and never consists solely of straight-line `M`/`L`/`Z` polygon segments, and compound paths with interior holes specify `android:fillType="evenOdd"` or stroke attributes.
* **Criterion 3 (Multi-Color & Protocol Palette Preservation)**:
  * *Given* `bsport_bike.xml`, `bsport_run.xml`, `bsport_other.xml`, ANT+ badges, and BLE badges,
  * *When* inspecting `<path>` fill and stroke colors,
  * *Then* `bsport_bike.xml` preserves black (`#000000`), red (`#FF0000`), and blue (`#0000FF`); `bsport_other.xml` preserves dial gray (`#EFEFEF`), gold (`#FFC000`), red (`#FF0000`), dark blue (`#000080`), and green (`#008000`); ANT+ badges preserve black (`#000000`) and white (`#FFFFFF`); and BLE badges preserve Bluetooth blue (`#07377F`) and white (`#FFFFFF`).

### 1.5 System Invariants
* Zero `Resources.NotFoundException` across any device density (`DrawableDensityFallbackAuditTest` passes 100%).
* 9-language localization parity is unaffected (no string resource changes).
* 100% pass rate across `./gradlew testDebugUnitTest`.

---

## 2. Test Specification (`TST-UI-338`)

### Test Case 1: `VectorAssetResolutionContractTest` (`TST-UI-338.1`)
* **Scope**: Architectural & Geometric Contract Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* **Preconditions**: All 16 XML vector drawables exist in `app/src/main/res/drawable/`.
* **Action**:
  1. Parse all 16 XML files via `DocumentBuilderFactory`.
  2. Assert positive `viewportWidth`, `viewportHeight`, `width`, and `height`.
  3. Assert that every vector file contains curved SVG path commands (`A`, `a`, `C`, `c`) — strictly rejecting polygon-only `M`/`L`/`Z` auto-tracing (Rule 37).
  4. Assert multi-color palette fidelity for `bsport_bike.xml`, `bsport_run.xml`, `bsport_other.xml`, ANT+ black/white badges, and BLE blue/white badges.
  5. Assert that zero `.png` files for the 16 migrated icons, zero `bsport_*_gray.png`, and zero `btle_hr.png` files exist in any `app/src/main/res/drawable*` folder.
* **Expected Result**: All DOM, geometric primitive, color palette, and file purge assertions pass.

### Test Case 2: `SportTypeSelectorContractTest` & `DrawableDensityFallbackAuditTest` (`TST-UI-338.2`)
* **Scope**: Component & Resource Fallback Contract Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelectorContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/DrawableDensityFallbackAuditTest.kt`
* **Expected Result**: `SportTypeSelectorContractTest` verifies `bsport_bike.xml`, `bsport_run.xml`, and `bsport_other.xml` vector assets and `ColorFilter.colorMatrix` dynamic desaturation; `DrawableDensityFallbackAuditTest` passes with zero missing root drawables.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-338.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full unit test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-338.1` | Unit / DOM Contract | `VectorAssetResolutionContractTest` | `REQ-UI-375` | Specified |
| `TST-UI-338.2` | Unit / Resource Audit | `SportTypeSelectorContractTest`, `DrawableDensityFallbackAuditTest` | `REQ-UI-375`, `REQ-UI-312` | Specified |
| `TST-UI-338.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
