# Stage 3: Implementation Plan - ATT-3529: Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Architecture

**Ticket**: [ATT-3529](https://atrainingtracker.atlassian.net/browse/ATT-3529)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-UI-380` (*Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Architecture*)  
**Test Spec ID**: `TST-UI-339` (*Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Verification Suite*)  
**Branch**: `feature/ATT-3529`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary & Architectural Design (SWE.2)

To permanently eliminate `Resources$NotFoundException` crashes (e.g. `R.drawable.ant_logo` on physical devices running split APKs) and harden all Jetpack Compose dynamic image rendering against resource lookup failures, this plan establishes a dual-pillar defense:

```
+----------------------------------------------------------------------------------------------------+
|                                    Two-Pillar Architecture Overview                                |
+----------------------------------------------------------------------------------------------------+
| Pillar 1: High-Density Asset Gap Resolution (Resource Tree)                                        |
|   1. Cross-Ticket Boundary & Chesterton's Fence with ATT-2859:                                     |
|      • The 16 sensor/sport icons (bike_cad, bike_pwr, bike_spd, bike_speed_and_cadence, hr,        |
|        run_spd, temp, bt_bike_cad, bt_bike_pwr, bt_bike_spd, bt_bike_speed_and_cadence, bt_hr,     |
|        bt_run, bsport_bike, bsport_run, bsport_other) were permanently migrated to XML             |
|        VectorDrawables in root res/drawable/ in ATT-2859 and their PNGs purged. They are density-  |
|        independent and immune to density splits. They MUST NOT be re-introduced as PNGs.           |
|      • All remaining active raster drawables with high-density gaps are systematically populated:  |
|        - ant_logo.png -> drawable-xxhdpi (144x144), drawable-xxxhdpi (192x192)                    |
|        - logo_protocol_bluetooth.png -> drawable-xxxhdpi (192x192)                                |
|        - dropbox_logo_blue.png -> drawable-xxxhdpi (192x192)                                      |
|        - logo.png -> drawable-xxhdpi (144x126), drawable-xxxhdpi (192x168)                       |
|        - stat_sys_battery_*.png (10, 20, 40, 60, 80, unknown) -> xxhdpi, xxxhdpi                   |
|        - export_*.png (error, failed, success) -> xxhdpi, xxxhdpi                                  |
|   2. Deterministic Lanczos Resampling & Visual Alpha Sanity Checks:                                |
|      • Pillow Lanczos resampling preserving RGBA mode, non-zero alpha, and exact aspect ratios.   |
+----------------------------------------------------------------------------------------------------+
| Pillar 2: Systematic Compose Migration to safePainterResource (Runtime Crash Immunity)            |
|   Migrate all 11 dynamic Compose icon call sites to safePainterResource(id, fallback) with         |
|   strictly compiled ImageVector fallbacks (compiled directly in DEX, immune to ResourcesNotFound): |
|     1. PairingButtons.kt                                                                           |
|     2. PairingProtocolBottomSheet.kt                                                               |
|     3. DeviceTypeSelectionDialog.kt                                                                |
|     4. DeviceItem.kt                                                                               |
|     5. SensorSourceDialog.kt                                                                       |
|     6. SensorStatus.kt                                                                             |
|     7. SearchArea.kt                                                                               |
|     8. DropboxConnectionHeader.kt                                                                  |
|     9. DropboxSettingsDialog.kt                                                                    |
|    10. ActivityTypeSelectionDialog.kt                                                              |
|    11. ControlTrackingButton.kt                                                                    |
+----------------------------------------------------------------------------------------------------+
| CI / Architectural Enforcement:                                                                    |
|   - DrawableDensityFallbackAuditTest.kt: testCoreDrawablesHaveFullHighDensityCoverage()            |
|   - ComposeSafePainterContractTest.kt: AST/source code verification enforcing safePainterResource  |
+----------------------------------------------------------------------------------------------------+
```

---

## 2. Chesterton's Fence Boundary with ATT-2859

The parent ticket description originally listed sensor drawables (`bike_cad.png`, `hr.png`, `bt_hr.png`, etc.) alongside `ant_logo.png`.
**Critical Architectural Invariant**:
- Under `ATT-2859` (`REQ-UI-375`), all 16 sport and sensor icons were faithfully migrated to scalable Android XML VectorDrawables in root `res/drawable/`, and their 106 legacy multi-density raster files were purged.
- VectorDrawables are XML-based, density-independent, and always packaged into the base APK; they can never trigger density-split `Resources$NotFoundException` crashes.
- Therefore, `tools/resample_high_density_drawables.py` MUST NOT generate raster `.png` files for the 16 migrated vector drawables.
- For all remaining active raster assets that have density gaps (`ant_logo.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`, `logo.png`, battery indicators, and export icons), full high-density coverage across `drawable-xxhdpi/` and `drawable-xxxhdpi/` is generated.

---

## 3. Atomic Implementation Steps

### Step 1: Resampling Tooling with Automated Visual & Alpha Sanity Validation
- Create `tools/resample_high_density_drawables.py`:
  - Uses `PIL.Image.Resampling.LANCZOS` (Pillow >= 10.0.0).
  - Target assets:
    - `ant_logo.png`: `drawable-xxhdpi` (144x144), `drawable-xxxhdpi` (192x192).
    - `logo_protocol_bluetooth.png`: `drawable-xxxhdpi` (192x192).
    - `dropbox_logo_blue.png`: `drawable-xxxhdpi` (192x192).
    - `logo.png`: `drawable-xxhdpi` (144x126), `drawable-xxxhdpi` (192x168).
    - `stat_sys_battery_10.png`, `20`, `40`, `60`, `80`, `unknown`: `drawable-xxhdpi` (72x72), `drawable-xxxhdpi` (96x96).
    - `export_error.png`, `export_failed.png`, `export_success.png`: `drawable-xxhdpi` (72x72), `drawable-xxxhdpi` (96x96).
  - **Automated Visual Validation**:
    - Asserts that every generated PNG has mode `RGBA`.
    - Asserts that alpha channel contains non-zero transparent and semi-transparent pixels (preserving anti-aliased edges and transparency).
    - Asserts that aspect ratio matches source raster within 0.1%.
    - Asserts that byte size is > 0 and files are loadable by Pillow.

### Step 2: High-Density Asset Execution & Commit
- Run `python3 tools/resample_high_density_drawables.py`.
- Verify the generated files in `drawable-xxhdpi/` and `drawable-xxxhdpi/`.

### Step 3: Compose Migration to `safePainterResource`
Migrate each of the 11 identified call sites, replacing raw `painterResource(...)` with `safePainterResource(...)`:
1. **`PairingButtons.kt`**:
   - `painter = safePainterResource(id = iconRes, fallback = Icons.Default.Sensors)`
2. **`PairingProtocolBottomSheet.kt`**:
   - `painter = safePainterResource(id = Protocol.BLUETOOTH_LE.iconId, fallback = Icons.Default.Bluetooth)`
   - `painter = safePainterResource(id = Protocol.ANT_PLUS.iconId, fallback = Icons.Default.Sensors)`
3. **`DeviceTypeSelectionDialog.kt`**:
   - `painter = safePainterResource(id = getIconId(type, protocol), fallback = Icons.Default.Sensors)`
4. **`DeviceItem.kt`**:
   - `painter = safePainterResource(id = device.deviceTypeIconRes, fallback = Icons.Default.Sensors)`
5. **`SensorSourceDialog.kt`**:
   - `iconPainter = safePainterResource(id = sensorType.getIconResId(), fallback = Icons.Default.Sensors)`
   - `painter = safePainterResource(id = device.deviceTypeIconRes, fallback = Icons.Default.Sensors)`
6. **`SensorStatus.kt`**:
   - `painter = safePainterResource(id = type.iconResId, fallback = Icons.Default.Sensors)`
7. **`SearchArea.kt`**:
   - `painter = safePainterResource(id = iconRes, fallback = Icons.Default.Sensors)`
8. **`DropboxConnectionHeader.kt`**:
   - `painter = safePainterResource(id = R.drawable.dropbox_logo_blue, fallback = Icons.Default.Cloud)`
9. **`DropboxSettingsDialog.kt`**:
   - `iconPainter = safePainterResource(id = R.drawable.dropbox_logo_blue, fallback = Icons.Default.Cloud)`
10. **`ActivityTypeSelectionDialog.kt`**:
    - `painter = safePainterResource(id = type.logoId, fallback = Icons.Default.FitnessCenter)`
11. **`ControlTrackingButton.kt`**:
    - `painter = safePainterResource(id = iconRes, fallback = Icons.Default.PlayArrow)`

### Step 4: Fallback Invariant Enforcement
- `safePainterResource` fallback parameter is strictly typed as `ImageVector? = null`.
- Material Design `ImageVector` objects (`Icons.Default.*`) are compiled directly into the application's DEX bytecode. They do NOT query `android.content.res.Resources` and are 100% immune to `Resources$NotFoundException`.
- If both the requested resource ID is unresolvable and `fallback` is null, `safePainterResource` returns `ColorPainter(Color.Transparent)`, guaranteeing zero UI thread crashes.

### Step 5: Automated CI Contract Tests
1. **`DrawableDensityFallbackAuditTest.kt`**:
   - Add canonical list `CORE_DENSITY_ASSETS = listOf("ant_logo", "logo_protocol_bluetooth", "dropbox_logo_blue", "logo")`.
   - Implement `testCoreDrawablesHaveFullHighDensityCoverage()`: Asserts that every asset in `CORE_DENSITY_ASSETS` exists in both `drawable-xxhdpi/` and `drawable-xxxhdpi/`.
2. **`ComposeSafePainterContractTest.kt`**:
   - Create new test in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/ComposeSafePainterContractTest.kt`.
   - Inspects the source code of all 11 target components and asserts:
     - Target files do not import `androidx.compose.ui.res.painterResource` for dynamic drawable IDs.
     - Target files invoke `safePainterResource(`.
     - Fallbacks supplied are `ImageVector` instances or null.

### Step 6: Regression & Governance Verification
- Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests com.atrainingtracker.trainingtracker.resources.DrawableDensityFallbackAuditTest \
    --tests com.atrainingtracker.trainingtracker.ui.theme.SafePainterResourceContractTest \
    --tests com.atrainingtracker.trainingtracker.ui.theme.ComposeSafePainterContractTest
  ```
- Run full clean-room regression suite: `./gradlew testDebugUnitTest`.
- Run requirement governance: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.5`.

---

## 4. Preserved Invariants & Boundary Verification

1. **Crash-Free Guarantee**: `safePainterResource` guarantees zero uncaught `Resources.NotFoundException` exceptions on the UI thread under any conditions.
2. **Visual Fidelity & Alpha Preservation**: Automated Pillow checks verify non-zero alpha and aspect ratio parity.
3. **Symbol & API Stability**: All `R.drawable.*` resource symbols remain identical.
4. **Clean-Room Test Pass Rate**: 100% full suite pass rate strictly preserved.
