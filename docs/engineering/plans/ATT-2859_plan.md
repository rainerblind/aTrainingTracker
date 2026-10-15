# Stage 3: Implementation Plan - ATT-2859: Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3622](https://rainerblind.atlassian.net/browse/ATT-3622) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-375` (*Scalable Vector Asset Architecture & Multi-Density Raster Migration*, amending `REQ-UI-312`)  
**Test Mapping**: `TST-UI-338` (*Scalable Vector Asset Architecture & Multi-Density Raster Migration Verification*)  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Description & Background

The application retains over 100 legacy raster PNG drawables distributed across 6 density folders (`drawable-ldpi/`, `-mdpi/`, `-hdpi/`, `-xhdpi/`, `-xxhdpi/`, `-xxxhdpi/`). These include core sport type indicators (`bsport_bike`, `bsport_run`, `bsport_other`), ANT+ and BLE sensor icons, and 18 unused pre-tinted gray assets (`bsport_*_gray.png`).

Maintaining fixed-pixel raster assets incurs:
1. Vulnerability to `Resources$NotFoundException` on density-split APKs or non-standard device DPIs.
2. Pixelation on modern high-DPI smartphone displays, foldables, and bike cockpits.
3. Opaque off-white background artifacts (`#FAFAFA`) on legacy ANT+ sensor icons rendering as unsightly grey boxes in dark theme.
4. Redundant APK bloat from superseded density buckets and dead files.

This implementation plan specifies the end-to-end migration of these assets to clean, scalable Android XML VectorDrawables in root `app/src/main/res/drawable/`, the removal of all obsolete PNG files across density folders, the elimination of dead assets, and comprehensive automated contract test coverage.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-375` (*Scalable Jetpack Compose Vector Asset Architecture & Multi-Density Raster Migration*, amending `REQ-UI-312`)
  * `REQ-UI-375.1`: Unified XML VectorDrawable Assets (`bsport_*`, `hr`, `bike_*`, `run_spd`, `temp`, `bt_*`) in root `res/drawable/`
  * `REQ-UI-375.2`: Dynamic Compose Theming, Density Cleanup & Dead Asset Elimination
  * `REQ-UI-375.3`: Binary & Resource ID Compatibility (`R.drawable.*`)
* **Test Mapping**: `TST-UI-338` (*Scalable Vector Asset Architecture & Multi-Density Raster Migration Verification*)
  * `[TST-UI-338.1]`: Vector Asset Existence & XML Schema Parsing (`VectorAssetResolutionContractTest.kt`)
  * `[TST-UI-338.2]`: Multi-Density Raster Deletion & Cleanup Audit (`VectorAssetResolutionContractTest.kt`)
  * `[TST-UI-338.3]`: Modernized SportTypeSelector Contract Audit (`SportTypeSelectorContractTest.kt`)
  * `[TST-UI-338.4]`: Density Fallback Audit Compliance (`DrawableDensityFallbackAuditTest.kt`)
  * `[TST-UI-338.5]`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. UI Consistency & Design Guidelines Compliance (Rule 23)

* **Closest Reference Screens**: `SportTypeSelector.kt` (Control Tracking tab), `RemoteDevices.kt` (Sensor status row), `DeviceItem.kt` / `DevicesTabbedScreen.kt` (Sensor management tabs).
* **Reused Components / Primitives**: Vector drawables are loaded directly via `safePainterResource(id)` into Jetpack Compose `Icon` and `Image` composables.
* **Theme Tokens & Color Harmony**:
  - Vector paths use neutral `#FFFFFFFF` (white) fill without hardcoded `android:tint`, allowing Jetpack Compose `Icon(..., tint = ...)` or `Modifier` color filters to apply dynamic theme tokens (`MaterialTheme.colorScheme.onSurface`, `Color.Gray`, primary accent).
  - Background is transparent (`alpha = 0`), eliminating legacy dark-mode rectangular box artifacts.
* **Justification for Style**: Adheres directly to Android Material 3 vector design guidelines and Compose single-source-of-truth theming.

---

## 4. System Invariants & Preserved Behavior

1. **Resource Identifier Invariant**: Resource symbol names (`R.drawable.bsport_bike`, `R.drawable.hr`, `R.drawable.bt_bike_spd`, etc.) SHALL remain 100% unchanged. No caller signatures in Java or Kotlin will be modified.
2. **Density-Split Immunity**: All 16 migrated assets reside in root `app/src/main/res/drawable/` as XML vector drawables, guaranteeing seamless resolution on all screen densities, custom DPI devices, and Play Store density splits without `Resources$NotFoundException`.
3. **Transparent Alpha Invariant**: All vector drawables feature transparent backgrounds.
4. **Clean-Room Test Pass Rate**: 100% test pass rate across all 2,811+ unit tests.

---

## 5. Proposed Architectural Changes & Atomic Steps

### Step 1: Sport Type VectorDrawables (`app/src/main/res/drawable/`)
Create crisp, scalable Android XML VectorDrawables (24dp × 24dp viewport) with white fill paths:
- `bsport_bike.xml` (Cycling icon)
- `bsport_run.xml` (Running icon)
- `bsport_other.xml` (Fitness / multisport icon)

### Step 2: ANT+ Sensor VectorDrawables (`app/src/main/res/drawable/`)
Create clean, transparent Android XML VectorDrawables:
- `hr.xml` (Heart rate monitor glyph)
- `bike_spd.xml` (Speedometer / speed gauge glyph)
- `bike_cad.xml` (Cadence / crank glyph)
- `bike_speed_and_cadence.xml` (Combined speed and cadence glyph)
- `bike_pwr.xml` (Power / bolt glyph)
- `run_spd.xml` (Running shoe / footpod glyph)
- `temp.xml` (Thermometer / temperature glyph)

### Step 3: BLE Sensor VectorDrawables (`app/src/main/res/drawable/`)
Create matching Bluetooth-enabled XML VectorDrawables incorporating a Bluetooth protocol indicator badge:
- `bt_hr.xml`
- `bt_bike_spd.xml`
- `bt_bike_cad.xml`
- `bt_bike_speed_and_cadence.xml`
- `bt_bike_pwr.xml`
- `bt_run.xml`

### Step 4: Deletion of Obsolete Raster Assets
Delete all obsolete `.png` files for the 16 migrated drawables across:
- `app/src/main/res/drawable/`
- `app/src/main/res/drawable-ldpi/`
- `app/src/main/res/drawable-mdpi/`
- `app/src/main/res/drawable-hdpi/`
- `app/src/main/res/drawable-xhdpi/`
- `app/src/main/res/drawable-xxhdpi/`
- `app/src/main/res/drawable-xxxhdpi/`

### Step 5: Deletion of Dead Legacy Assets
Delete all 18 instances of unused pre-tinted gray files and unused sensor assets:
- `bsport_bike_gray.png`
- `bsport_run_gray.png`
- `bsport_other_gray.png`
- `btle_hr.png`

### Step 6: Modernize `SportTypeSelectorContractTest.kt`
Update `testAllSportTypeDrawablesExistInRootDrawableDirectory` to verify `bsport_run.xml`, `bsport_bike.xml`, and `bsport_other.xml` in root `res/drawable/` instead of hardcoded `.png` and removed `_gray` files.

### Step 7: Author `VectorAssetResolutionContractTest.kt`
Create unit test in `com.atrainingtracker.trainingtracker.resources`:
- Assert all 16 vector drawables exist in `app/src/main/res/drawable/` and have valid `<vector>` root elements.
- Assert that no legacy `.png` files for these 16 assets exist in any `drawable*` folder.
- Assert that no dead `_gray.png` or `btle_hr.png` files exist anywhere in the repository.

### Step 8: Targeted Verification & Clean-Room Regression
Run:
- `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.resources.VectorAssetResolutionContractTest`
- `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.resources.DrawableDensityFallbackAuditTest`
- `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SportTypeSelectorContractTest`
- `./gradlew testDebugUnitTest` (full suite pass).
