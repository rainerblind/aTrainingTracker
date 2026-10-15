# Stage 5 Walkthrough - ATT-2859: Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Stage 5 Subtask**: [ATT-3624](https://rainerblind.atlassian.net/browse/ATT-3624) (`[Test]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-375` (`Verified`, amending `REQ-UI-312`)  
**Test Mapping**: `TST-UI-338` (`Verified`)  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Root Cause

The application historically retained over 100 legacy raster PNG drawables distributed across 6 density directories (`drawable-ldpi/`, `-mdpi/`, `-hdpi/`, `-xhdpi/`, `-xxhdpi/`, `-xxxhdpi/`), originating from the app's early Android architecture (2011–2017). These included sport type indicators (`bsport_bike`, `bsport_run`, `bsport_other`), ANT+ and BLE sensor icons (`hr`, `bike_spd`, `bike_cad`, `bike_speed_and_cadence`, `bike_pwr`, `run_spd`, `temp`, and `bt_*` variants), and redundant pre-tinted gray assets (`bsport_*_gray.png`).

Maintaining fixed-pixel raster assets incurred:
1. Vulnerability to `Resources$NotFoundException` on Google Play density-split APKs or non-standard device DPIs.
2. Visual pixelation on modern high-DPI smartphone displays, foldables, and bike cockpits.
3. Opaque off-white background artifacts (`#FAFAFA`) on legacy ANT+ sensor icons rendering as unsightly grey boxes against dark theme.
4. APK footprint bloat and repository clutter from superseded density buckets and dead files.

ATT-2859 resolves these issues by migrating all core sport and sensor drawables to clean Android XML VectorDrawables in root `app/src/main/res/drawable/`, deleting 106 obsolete raster PNG files, eliminating dead assets, and enforcing dynamic Compose theming.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-375` (*Scalable Jetpack Compose Vector Asset Architecture & Multi-Density Raster Migration*) -> **Status: Verified**
  * `REQ-UI-375.1`: Unified XML VectorDrawable Assets (`bsport_*`, `hr`, `bike_*`, `run_spd`, `temp`, `bt_*`) in root `res/drawable/` -> **Verified**
  * `REQ-UI-375.2`: Dynamic Compose Theming, Density Cleanup & Dead Asset Elimination -> **Verified**
  * `REQ-UI-375.3`: Binary & Resource ID Compatibility (`R.drawable.*`) -> **Verified**
* **Test Mapping**: `TST-UI-338` (*Scalable Vector Asset Architecture & Multi-Density Raster Migration Verification*) -> **Status: Verified**
  * `[TST-UI-338.1]`: Vector Asset Existence & XML Schema Parsing (`VectorAssetResolutionContractTest.kt`) -> **Verified**
  * `[TST-UI-338.2]`: Multi-Density Raster Deletion & Cleanup Audit (`VectorAssetResolutionContractTest.kt`) -> **Verified**
  * `[TST-UI-338.3]`: Modernized SportTypeSelector Contract Audit (`SportTypeSelectorContractTest.kt`) -> **Verified**
  * `[TST-UI-338.4]`: Density Fallback Audit Compliance (`DrawableDensityFallbackAuditTest.kt`) -> **Verified**
  * `[TST-UI-338.5]`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`) -> **Verified (100% pass rate, 2,812+ tests)**

---

## 3. Preserved Invariants & Invariant Verification

1. **Resource Identifier Invariant**: Resource symbol names (`R.drawable.bsport_bike`, `R.drawable.hr`, `R.drawable.bt_bike_spd`, etc.) remain 100% unchanged. Call sites across Java (`BSportType.java`, `ActivityType.java`, `UIHelper.java`) and Kotlin Compose (`SportTypeSelector.kt`, `DeviceData.kt`, `RemoteDevices.kt`) required zero signature alterations.
2. **Density-Split Immunity**: All 16 migrated assets reside in root `app/src/main/res/drawable/` as XML vector drawables, guaranteeing seamless resolution on all screen densities, custom DPI devices, and Play Store density splits without `Resources$NotFoundException`.
3. **Transparent Alpha Invariant**: All vector drawables feature transparent backgrounds, eliminating legacy `#FAFAFA` dark-mode box artifacts.
4. **Clean-Room Test Pass Rate**: 100% test pass rate across all 2,812+ unit tests (0 regressions).

---

## 4. Verification Results & Test Execution

### 4.1 Targeted Contract Tests
- `VectorAssetResolutionContractTest.testAllMigratedVectorAssetsExistAndParseValidVectorTag`: **PASSED**
- `VectorAssetResolutionContractTest.testNoLegacyRasterPngFilesRemainForMigratedAssets`: **PASSED**
- `VectorAssetResolutionContractTest.testDeadLegacyAssetsArePermanentlyEliminated`: **PASSED**
- `SportTypeSelectorContractTest.testAllSportTypeDrawablesExistInRootDrawableDirectory`: **PASSED**
- `SportTypeSelectorContractTest.testSportTypeSelectorUsesSafePainterResourceAndFallbackVectors`: **PASSED**
- `DrawableDensityFallbackAuditTest.testAllDensityDrawablesHaveRootFallback`: **PASSED**

### 4.2 Full Clean-Room Regression Suite
```bash
./gradlew testDebugUnitTest
```
- **Result**: `BUILD SUCCESSFUL in 3m 27s`
- **Total Tests**: 2,812+ tests passed (100% pass rate, 0 failures, 0 regressions).
