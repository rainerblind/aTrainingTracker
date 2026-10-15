# Stage 2 Test Specification - ATT-2859: Faithful Scalable Jetpack Compose Vector Asset Architecture & Multi-Density Migration

**Ticket**: [ATT-2859](https://atrainingtracker.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3726](https://atrainingtracker.atlassian.net/browse/ATT-3726) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://atrainingtracker.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Branch**: `feature/ATT-2859`  
**Requirement Mapping**: `REQ-UI-375` (*Faithful Scalable Jetpack Compose Vector Asset Architecture & Multi-Density Raster Migration*)  
**Test Spec ID**: `TST-UI-338`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Traceability Matrix

| Requirement ID | Test Case ID | Test Class / Verification Target | Scope of Verification | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-375.1` | `TST-UI-338.1` | `VectorAssetResolutionContractTest` | Multi-color sport vector inflation, geometry preservation (`bsport_bike`: black wheels, red frame, blue rider; `bsport_run`: blue runner; `bsport_other`: gold ring, red/green/blue buttons) | Draft |
| `REQ-UI-375.2` | `TST-UI-338.2` | `VectorAssetResolutionContractTest` | ANT+ badge vector inflation, black rounded-square background, white glyph, white bottom label ("HR", "SPD", "CAD", "S & C", "PWR", "SPD", "TEMP") | Draft |
| `REQ-UI-375.3` | `TST-UI-338.3` | `VectorAssetResolutionContractTest` | BLE badge vector inflation, Bluetooth blue (`#0A3D91`) rounded-square background, white glyph, white bottom label ("speed", "cadence", "s & c", "power") | Draft |
| `REQ-UI-375.4` | `TST-UI-338.4` | `DrawableDensityFallbackAuditTest` | Deletion of 16 superseded PNGs across density folders, deletion of dead `_gray.png` and `btle_hr.png`, root fallback audit | Draft |
| `REQ-UI-375.5` | `TST-UI-338.5` | `SportTypeSelectorContractTest` | UI presentation contract test asserting updated `.xml` vector resolution without legacy `.png` or dead `_gray` checks | Draft |
| `REQ-UI-375` | `TST-UI-338.6` | `./gradlew testDebugUnitTest` | Full clean-room unit test suite execution with 100% pass rate and zero regressions | Draft |

---

## 2. Requirement Overview (`REQ-UI-375`)

Modernize the Android drawable asset architecture by migrating legacy multi-density raster drawables to faithful, scalable Android XML VectorDrawables in root `app/src/main/res/drawable/`, preserving their exact multi-color visual design, badge layout, and deleting obsolete density-specific PNG files:
1. **Multi-Color Sport Icons**:
   - `bsport_bike.xml`: Black wheels (`#000000`), bold red frame (`#FF0000`), blue aerodynamic cyclist (`#0000FF`), black saddle (`#000000`).
   - `bsport_run.xml`: Blue stick-figure runner (`#0000FF`) running toward right.
   - `bsport_other.xml`: Gold circular stopwatch ring (`#FFC700`), red top crown (`#FF0000`), green right button (`#008F00`), blue left button (`#00008F`).
2. **ANT+ Sensor Badges**:
   - Black (`#000000`) rounded square ($r \approx 12$ px) on transparent background.
   - White (`#FFFFFF`) glyph in upper half + bold uppercase white text label in lower half (`hr`, `bike_spd`, `bike_cad`, `bike_speed_and_cadence`, `bike_pwr`, `run_spd`, `temp`).
3. **BLE Sensor Badges**:
   - Bluetooth Blue (`#0A3D91`) rounded square ($r \approx 12$ px) on transparent background.
   - White (`#FFFFFF`) glyphs and lowercase bold labels (`bt_hr`, `bt_bike_spd`, `bt_bike_cad`, `bt_bike_speed_and_cadence`, `bt_bike_pwr`, `bt_run`).
4. **Purge of Obsolete Assets**:
   - Delete 16 superseded `.png` files across all density folders (`drawable-ldpi/` through `-xxxhdpi/`).
   - Delete all `bsport_*_gray.png` and `btle_hr.png`.
   - Maintain 100% compliance with `DrawableDensityFallbackAuditTest.kt`.
5. **Binary & Resource ID Stability**:
   - Resource IDs (`R.drawable.bsport_bike`, `R.drawable.hr`, etc.) remain identical with zero call-site signature changes.

---

## 3. Test Specification (`TST-UI-338`)

### 3.1 Test Group 1: Vector Asset Inflation & Geometry Contract Tests (`VectorAssetResolutionContractTest.kt`)
* **Test Case 1.1 (Vector Existence & Valid XML Syntax)**:
  - Assert that all 16 target files exist in `app/src/main/res/drawable/` with `.xml` extension.
  - Parse each file using `javax.xml.parsers.DocumentBuilder` asserting `<vector>` root element with valid `android:width`, `android:height`, `android:viewportWidth`, and `android:viewportHeight`.
* **Test Case 1.2 (Sport Bike Multi-Color Geometry Invariant)**:
  - Parse `bsport_bike.xml`:
    - Assert contains path with `android:fillColor="#000000"` (or `#FF000000`) for wheels / saddle.
    - Assert contains path with `android:fillColor="#FF0000"` (or `#FFFF0000`) for bicycle frame.
    - Assert contains path with `android:fillColor="#0000FF"` (or `#FF0000FF`) for cyclist rider.
* **Test Case 1.3 (Sport Run Single-Color Runner Invariant)**:
  - Parse `bsport_run.xml`:
    - Assert contains path with `android:fillColor="#0000FF"` (or `#FF0000FF`) for runner silhouette.
* **Test Case 1.4 (Sport Other Stopwatch Multi-Color Invariant)**:
  - Parse `bsport_other.xml`:
    - Assert contains gold/yellow ring path (`#FFC700` or `#FFFFC700`).
    - Assert contains red crown button (`#FF0000` or `#FFFF0000`).
    - Assert contains green right button (`#008F00` or `#FF008F00`).
    - Assert contains blue left button (`#00008F` or `#FF00008F`).
* **Test Case 1.5 (ANT+ Badge Invariant)**:
  - For each ANT+ asset (`hr`, `bike_spd`, `bike_cad`, `bike_speed_and_cadence`, `bike_pwr`, `run_spd`, `temp`):
    - Assert contains black rounded-square background path (`#000000` or `#FF000000`).
    - Assert contains white foreground glyph and text paths (`#FFFFFF` or `#FFFFFFFF`).
* **Test Case 1.6 (BLE Badge Invariant)**:
  - For each BLE asset (`bt_hr`, `bt_bike_spd`, `bt_bike_cad`, `bt_bike_speed_and_cadence`, `bt_bike_pwr`, `bt_run`):
    - Assert contains Bluetooth blue background path (`#0A3D91` or `#FF0A3D91`).
    - Assert contains white foreground glyph and text paths (`#FFFFFF` or `#FFFFFFFF`).

### 3.2 Test Group 2: Density Fallback & Raster Elimination Tests (`DrawableDensityFallbackAuditTest.kt`)
* **Test Case 2.1 (Purge of Migrated Raster PNGs)**:
  - Iterate all density directories (`drawable-ldpi`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`, and root `drawable`).
  - Assert that zero `.png` files exist for the 16 migrated asset names.
* **Test Case 2.2 (Purge of Dead Gray & Obsolete Assets)**:
  - Assert that zero files named `bsport_bike_gray.*`, `bsport_run_gray.*`, `bsport_other_gray.*`, or `btle_hr.*` exist in any resource directory.
* **Test Case 2.3 (Root Fallback Compliance)**:
  - Execute `DrawableDensityFallbackAuditTest.kt` asserting that 100% of drawables in density buckets have a corresponding file in root `res/drawable/`.

### 3.3 Test Group 3: UI Presentation Contract Tests (`SportTypeSelectorContractTest.kt`)
* **Test Case 3.1 (SportTypeSelector Asset Resolution)**:
  - Update `SportTypeSelectorContractTest.kt` to verify that `BSportType.BIKE`, `BSportType.RUN`, and `BSportType.UNKNOWN` resolve to `bsport_bike.xml`, `bsport_run.xml`, and `bsport_other.xml`.
  - Remove obsolete assertions checking for `.png` extensions or `_gray` assets.

### 3.4 Test Group 4: Clean-Room Full Suite Regression Execution
* **Test Case 4.1 (Full Test Suite Regression)**:
  - Run `./gradlew testDebugUnitTest`.
  - Assert 100% pass rate with zero regressions across the entire application codebase.

---

## 4. Localization & Resource Audit

This ticket is a pure graphical asset migration. No user-facing text strings are added or modified. All existing strings in `strings.xml` across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) remain unaffected and 100% compliant.

---

## 5. Acceptance Criteria (Given-When-Then)

- **Scenario 1 (Faithful Vector Inflation)**:
  - *Given* the Android project resource directory `app/src/main/res/drawable/`,
  - *When* checking the 16 migrated sport and sensor drawables,
  - *Then* each file exists with extension `.xml`, valid `<vector>` root element, and preserves exact multi-color geometry and badge designs.
- **Scenario 2 (Complete Raster Purge)**:
  - *Given* all resource directories matching `drawable-*`,
  - *When* searching for raster PNGs of the 16 migrated names or obsolete gray assets,
  - *Then* zero matching PNG files are found.
- **Scenario 3 (Contract & Full Suite Verification)**:
  - *Given* `SportTypeSelectorContractTest`, `DrawableDensityFallbackAuditTest`, and `VectorAssetResolutionContractTest`,
  - *When* executed via `./gradlew testDebugUnitTest`,
  - *Then* all contract tests pass with 100% compliance.
