# Stage 5 Walkthrough - ATT-3529: Eliminate ResourcesNotFoundException Crashes by Populating High-Density Drawables & Migrating Compose Icon Call Sites to safePainterResource

**Ticket**: [ATT-3529](https://atrainingtracker.atlassian.net/browse/ATT-3529)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-UI-380` (*Defensive Density-Agnostic Asset Resolution & Compose Crash Immunity Architecture*)  
**Test Spec ID**: `TST-UI-343` (*Defensive Density-Agnostic Asset Resolution & Compose Crash Immunity Test Suite*)  
**Branch**: `feature/ATT-3529`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary

In production release V4.9.38.5 (build 269), a fatal crash occurred via Firebase Crashlytics:
```text
Fatal Exception: android.content.res.Resources$NotFoundException: Resource ID #0x7f080079
       at android.content.res.ResourcesImpl.getValue(ResourcesImpl.java:225)
       at android.content.res.Resources.getValue(Resources.java:1428)
       at androidx.compose.ui.res.ResourceIdCache.resolveResourcePath(Resources.android.kt:38)
       at androidx.compose.ui.res.PainterResources_androidKt.painterResource(PainterResources.android.kt:62)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PairingButtonsKt.PairingItem(PairingButtons.kt:92)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PairingButtonsKt.PairingButtons(PairingButtons.kt:58)
```
Symbol table forensics identified Resource ID `0x7f080079` as `R.drawable.ant_logo`.

The crash resulted from a dual systemic vulnerability:
1. **Google Play App Bundle (.aab) Density Split Incompleteness**: `bundletool` slices drawables into density-specific split APKs (e.g. `split_config.xxhdpi.apk`). Trademark logos (`ant_logo.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`, `logo.png`) and battery/export status icons only existed up to `drawable-xhdpi/` and were missing from `drawable-xxhdpi/` and `drawable-xxxhdpi/`. High-density devices trying to resolve assets from density splits suffered `Resources$NotFoundException`.
2. **Unguarded Raw Compose Call Sites**: While `safePainterResource` had been introduced in ATT-2858, 11 critical Compose components directly invoked standard `painterResource(id)`, failing to intercept `Resources$NotFoundException` runtime exceptions.

Ticket **ATT-3529** establishes comprehensive crash immunity across both pillars:
1. **Architectural Boundary & Chesterton's Fence Forensics**: Honored the ATT-2859 vector migration (which migrated 16 sport and sensor badges to XML VectorDrawables in root `res/drawable/`). ATT-3529 strictly focused on the remaining active raster drawables: trademark logos (`ant_logo.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`, `logo.png`), battery indicators (`stat_sys_battery_*.png`), and export status icons (`export_*.png`).
2. **Lanczos High-Density Asset Generation**: Resampled all active raster assets into `drawable-xxhdpi/` (3.0x) and `drawable-xxxhdpi/` (4.0x) and populated `drawable-nodpi/` (density-agnostic base APK fallback) via `tools/resample_high_density_drawables.py`.
3. **Defensive Compose Call Site Migration**: Migrated all 11 raw `painterResource` call sites across the Compose UI to `safePainterResource(id, fallback)`.
4. **Automated Audit CI Tests**: Extended `DrawableDensityFallbackAuditTest.kt`, authored `ComposeSafePainterContractTest.kt`, and validated crash immunity in `SafePainterResourceContractTest.kt`.
5. **Clean-Room Verification**: Passed the full test suite (`./gradlew testDebugUnitTest`) with 100% pass rate.

---

## 2. Changes Implemented

### 2.1 High-Density & Base APK Split Raster Drawables
Populated `drawable-xxhdpi/`, `drawable-xxxhdpi/`, and `drawable-nodpi/`:
- Trademark Logos:
  * `ant_logo.png`
  * `logo_protocol_bluetooth.png`
  * `dropbox_logo_blue.png`
  * `logo.png`
- Battery Status Assets:
  * `stat_sys_battery_10.png`
  * `stat_sys_battery_20.png`
  * `stat_sys_battery_40.png`
  * `stat_sys_battery_60.png`
  * `stat_sys_battery_80.png`
  * `stat_sys_battery_unknown.png`
- Export Status Assets:
  * `export_error.png`
  * `export_failed.png`
  * `export_success.png`

### 2.2 Defensive Jetpack Compose Call Site Migration
Migrated 11 call sites to use `safePainterResource`:
1. `PairingButtons.kt`: Direct crash site migrated to `safePainterResource(id = iconRes)`.
2. `PairingProtocolBottomSheet.kt`: ANT+ and BLE protocol item icons protected via `safePainterResource(id = Protocol.ANT_PLUS.iconId)` and `safePainterResource(id = Protocol.BLUETOOTH_LE.iconId)`.
3. `DeviceTypeSelectionDialog.kt`: Device type dialog icons migrated to `safePainterResource(id = getIconId(type, protocol))`.
4. `DeviceItem.kt`: Paired device list item icons migrated to `safePainterResource(id = device.deviceTypeIconRes)`.
5. `SensorSourceDialog.kt`: Sensor source picker icons migrated to `safePainterResource(...)`.
6. `SensorStatus.kt`: Sensor status indicator migrated to `safePainterResource(id = type.iconResId)`.
7. `SearchArea.kt`: Search area sensor icons migrated to `safePainterResource(id = iconRes)`.
8. `DropboxConnectionHeader.kt`: Cloud connection header migrated to `safePainterResource(id = R.drawable.dropbox_logo_blue)`.
9. `DropboxSettingsDialog.kt`: Dropbox settings modal migrated to `safePainterResource(id = R.drawable.dropbox_logo_blue)`.
10. `ActivityTypeSelectionDialog.kt`: Activity type selection dialog icons migrated to `safePainterResource(id = activityType.iconId)`.
11. `ControlTrackingButton.kt`: Tracking control state icons migrated to `safePainterResource(id = iconRes)`.

### 2.3 Unit & Contract Testing Suite
- `DrawableDensityFallbackAuditTest.kt`: Added `testCoreDrawablesHaveFullHighDensityCoverage()` asserting that all active non-vector drawables have files present in `drawable-xxhdpi/`, `drawable-xxxhdpi/`, and `drawable-nodpi/`.
- `SafePainterResourceContractTest.kt`: Added `testSafePainterResource_architectureGuaranteesCrashImmunity()` asserting that invalid resource IDs or resolution failures never propagate crashes.
- `ComposeSafePainterContractTest.kt`: Created static/AST audit ensuring zero raw `painterResource` calls exist across the Compose UI layer.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Test Execution
```bash
./gradlew testDebugUnitTest \
  --tests com.atrainingtracker.trainingtracker.resources.DrawableDensityFallbackAuditTest \
  --tests com.atrainingtracker.trainingtracker.ui.theme.SafePainterResourceContractTest \
  --tests com.atrainingtracker.trainingtracker.ui.theme.ComposeSafePainterContractTest
```
Result: **BUILD SUCCESSFUL** (100% pass).

### 3.2 Clean-Room Test Suite Execution
```bash
./gradlew testDebugUnitTest
```
Result: **BUILD SUCCESSFUL in 4m 44s** (32 actionable tasks, 0 failures).

---

## 4. Invariant & Governance Compliance

- **Rule 2 Mandate**: Autonomously proceeded through all 5 ASPICE gates without halting.
- **Rule 3 Human Gate**: Parent ticket strictly advanced to `Final Review (Human)`. Never transitioned parent tickets to `Erledigt`.
- **Chesterton's Fence**: Retained and respected ATT-2859 VectorDrawables in root `res/drawable/` without re-creating obsolete rasters.
- **Rule 6 & Rule 19**: No premature Fix Version assigned; subtasks have no Fix Version.
- **Localization Parity**: All Compose call sites use `safePainterResource` while user strings remain localized via `strings.xml`.
