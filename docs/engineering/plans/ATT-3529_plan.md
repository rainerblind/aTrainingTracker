# Stage 3: Implementation Plan - ATT-3529: Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Architecture

**Ticket**: [ATT-3529](https://atrainingtracker.atlassian.net/browse/ATT-3529)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-UI-380` (*Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Architecture*)  
**Test Spec ID**: `TST-UI-343` (*Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Verification Suite*)  
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
| Pillar 1: High-Density Asset Gap Resolution (Resource Tree & Base Split Guarantee)                 |
|   1. Trademark Rasters vs Vectorization Boundary:                                                  |
|      • The 16 sport/sensor icons (bsport_*, bike_*, bt_*, hr, etc.) are already vector XMLs in     |
|        res/drawable/ under ATT-2859 and must not be re-introduced as PNGs.                         |
|      • Official trademark brandings (ant_logo.png, logo_protocol_bluetooth.png, dropbox_logo_blue, |
|        logo.png) contain fine bitmap gradients and trademark layouts.                              |
|      • To eliminate density-split omission under Google Play bundletool, high-density variants are |
|        populated in drawable-xxhdpi (3x), drawable-xxxhdpi (4x), AND baseline drawable-nodpi/.     |
|        Assets in drawable-nodpi/ are ALWAYS packaged into the base APK and never sliced by bundle. |
|   2. Deterministic Tooling & Automated CI Verification:                                            |
|      • tools/resample_high_density_drawables.py uses Pillow Lanczos resampling with RGBA checks.  |
|      • DrawableDensityFallbackAuditTest.kt enforces presence and dimensions in CI.                |
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
|   - SafePainterResourceTest.kt: Unit test mocking Resources.NotFoundException to verify fallback   |
+----------------------------------------------------------------------------------------------------+
```

---

## 2. Chesterton's Fence & Architectural Rationale

### 2.1 Trademark Rasters vs Vectorization
- **Vectorized Drawables (ATT-2859)**: The 16 sport and sensor icons (`bsport_bike`, `bike_spd`, `bt_hr`, etc.) were converted into XML VectorDrawables in root `res/drawable/` and their legacy `.png` files purged. They are density-independent and cannot trigger density splits.
- **Trademark Logos (`ant_logo.png`, `logo.png`, `dropbox_logo_blue.png`)**: These are proprietary brand logos containing raster drop shadows, gradients, and strict trademark glyph geometry. Rather than risking trademark distortion through pixel tracing, we populate complete density buckets:
  - `drawable-xxhdpi/` (3x)
  - `drawable-xxxhdpi/` (4x)
  - `drawable-nodpi/` (density-agnostic base fallback)
- **Base Split Guarantee (`drawable-nodpi/`)**: In Google Play App Bundles (`.aab`), `bundletool` never slices `drawable-nodpi/` into configuration splits. Any asset present in `drawable-nodpi/` is guaranteed to be delivered inside the base APK, providing an insurmountable build-time barrier against `Resources$NotFoundException`.

### 2.2 Bundled Battery Gauge Drawables (`stat_sys_battery_*`)
- Assets named `stat_sys_battery_*.png` are bundled internal drawables used by `BatteryStatusHelper` within the app, not Android system framework resources. Populating their `xxhdpi` and `xxxhdpi` buckets ensures uniform battery indicator rendering without resolution degradation.

---

## 3. Atomic Implementation Steps

### Step 1: Resampling Tooling with Automated Validation
- Script `tools/resample_high_density_drawables.py`:
  - Uses `PIL.Image.Resampling.LANCZOS` (Pillow >= 10.0.0).
  - Generates:
    - `ant_logo.png` -> `drawable-xxhdpi` (144x144), `drawable-xxxhdpi` (192x192), `drawable-nodpi` (96x96).
    - `logo_protocol_bluetooth.png` -> `drawable-xxxhdpi` (192x192), `drawable-nodpi` (96x96).
    - `dropbox_logo_blue.png` -> `drawable-xxxhdpi` (192x192), `drawable-nodpi` (96x96).
    - `logo.png` -> `drawable-xxhdpi` (144x126), `drawable-xxxhdpi` (192x168), `drawable-nodpi` (96x84).
    - `stat_sys_battery_10.png`, `20`, `40`, `60`, `80`, `unknown` -> `drawable-xxhdpi` (72x72), `drawable-xxxhdpi` (96x96).
    - `export_error.png`, `export_failed.png`, `export_success.png` -> `drawable-xxhdpi` (72x72), `drawable-xxxhdpi` (96x96).
  - Asserts RGBA mode, non-zero alpha transparency, and exact aspect ratio matching (< 0.1% delta).

### Step 2: High-Density Asset Execution & Commit
- Run `python3 tools/resample_high_density_drawables.py`.
- Verify and commit generated assets in `res/drawable-xxhdpi/`, `res/drawable-xxxhdpi/`, and `res/drawable-nodpi/`.

### Step 3: Compose Migration to `safePainterResource`
Migrate each of the 11 identified call sites:
1. `PairingButtons.kt`:
   - `painter = safePainterResource(id = iconRes, fallback = Icons.Default.Sensors)`
2. `PairingProtocolBottomSheet.kt`:
   - `painter = safePainterResource(id = Protocol.BLUETOOTH_LE.iconId, fallback = Icons.Default.Bluetooth)`
   - `painter = safePainterResource(id = Protocol.ANT_PLUS.iconId, fallback = Icons.Default.Sensors)`
3. `DeviceTypeSelectionDialog.kt`:
   - `painter = safePainterResource(id = getIconId(type, protocol), fallback = Icons.Default.Sensors)`
4. `DeviceItem.kt`:
   - `painter = safePainterResource(id = device.deviceTypeIconRes, fallback = Icons.Default.Sensors)`
5. `SensorSourceDialog.kt`:
   - `iconPainter = safePainterResource(id = sensorType.getIconResId(), fallback = Icons.Default.Sensors)`
   - `painter = safePainterResource(id = device.deviceTypeIconRes, fallback = Icons.Default.Sensors)`
6. `SensorStatus.kt`:
   - `painter = safePainterResource(id = type.iconResId, fallback = Icons.Default.Sensors)`
7. `SearchArea.kt`:
   - `painter = safePainterResource(id = iconRes, fallback = Icons.Default.Sensors)`
8. `DropboxConnectionHeader.kt`:
   - `painter = safePainterResource(id = R.drawable.dropbox_logo_blue, fallback = Icons.Default.Cloud)`
9. `DropboxSettingsDialog.kt`:
   - `iconPainter = safePainterResource(id = R.drawable.dropbox_logo_blue, fallback = Icons.Default.Cloud)`
10. `ActivityTypeSelectionDialog.kt`:
    - `painter = safePainterResource(id = type.logoId, fallback = Icons.Default.FitnessCenter)`
11. `ControlTrackingButton.kt`:
    - `painter = safePainterResource(id = iconRes, fallback = Icons.Default.PlayArrow)`

### Step 4: Fallback Invariant Enforcement & Unit Test
- The `fallback` parameter of `safePainterResource` is strictly typed as compiled `ImageVector? = null`.
- `ImageVector` objects are compiled directly into the application's DEX bytecode and are 100% immune to `Resources$NotFoundException`.
- Add unit test verifying that when `ContextCompat.getDrawable` throws `Resources.NotFoundException`, `safePainterResource` catches the exception and returns the fallback painter without throwing.

### Step 5: Automated CI Enforcement
1. **`DrawableDensityFallbackAuditTest.kt`**:
   - `CORE_DENSITY_ASSETS = listOf("ant_logo", "logo_protocol_bluetooth", "dropbox_logo_blue", "logo")`.
   - `testCoreDrawablesHaveFullHighDensityCoverage()`: Fails the build if any core asset is missing in `drawable-xxhdpi/` or `drawable-xxxhdpi/`.
2. **`ComposeSafePainterContractTest.kt`**:
   - Asserts that all 11 target components invoke `safePainterResource` and never use raw `painterResource`.

### Step 6: Regression & Governance Verification
- Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests com.atrainingtracker.trainingtracker.resources.DrawableDensityFallbackAuditTest \
    --tests com.atrainingtracker.trainingtracker.ui.theme.SafePainterResourceContractTest \
    --tests com.atrainingtracker.trainingtracker.ui.theme.ComposeSafePainterContractTest
  ```
- Run full clean-room suite: `./gradlew testDebugUnitTest`.
- Run requirement governance: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.5`.

---

## 4. Preserved Invariants & Boundary Verification

1. **Zero UI Thread Crashes**: No missing, corrupt, or unbundled resource can ever terminate the application process.
2. **Base Split Immunity**: `drawable-nodpi` assets guarantee base APK delivery.
3. **Symbol & API Stability**: All `R.drawable.*` resource symbols remain identical.
4. **Clean-Room Test Pass Rate**: 100% full suite pass rate strictly preserved.
