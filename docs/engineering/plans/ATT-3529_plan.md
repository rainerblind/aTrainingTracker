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

To permanently resolve `Resources$NotFoundException` crashes (e.g. `R.drawable.ant_logo` on physical devices running split APKs) and harden all Jetpack Compose dynamic image rendering against resource lookup failures, this plan establishes a dual-pillar defense:

```
+----------------------------------------------------------------------------------------------------+
|                                    Two-Pillar Architecture Overview                                |
+----------------------------------------------------------------------------------------------------+
| Pillar 1: High-Density Asset Gap Resolution (Resource Tree)                                        |
|   Deterministic high-quality Lanczos resampling script (tools/resample_high_density_drawables.py)  |
|   populates missing high-density raster buckets:                                                   |
|     - ant_logo.png -> drawable-xxhdpi (144x144), drawable-xxxhdpi (192x192)                        |
|     - logo_protocol_bluetooth.png -> drawable-xxxhdpi (192x192)                                    |
|     - dropbox_logo_blue.png -> drawable-xxxhdpi (192x192)                                          |
|     - logo.png -> drawable-xxhdpi (144x126), drawable-xxxhdpi (192x168)                           |
+----------------------------------------------------------------------------------------------------+
| Pillar 2: Systematic Compose Migration to safePainterResource (Runtime Crash Immunity)            |
|   Migrate all 11 dynamic Compose icon call sites to safePainterResource(id, fallback) with         |
|   meaningful semantic Material Design ImageVector fallbacks:                                       |
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

## 2. Atomic Implementation Steps

### Step 1: Deterministic Resampling Tooling
- Author `tools/resample_high_density_drawables.py`:
  - Uses `PIL.Image.Resampling.LANCZOS` to resample from the highest available source raster.
  - Generates:
    - `app/src/main/res/drawable-xxhdpi/ant_logo.png` (144x144) from `drawable-xhdpi/ant_logo.png` (96x96).
    - `app/src/main/res/drawable-xxxhdpi/ant_logo.png` (192x192) from `drawable-xhdpi/ant_logo.png` (96x96).
    - `app/src/main/res/drawable-xxxhdpi/logo_protocol_bluetooth.png` (192x192) from `drawable-xxhdpi/` (144x144).
    - `app/src/main/res/drawable-xxxhdpi/dropbox_logo_blue.png` (192x192) from `drawable-xxhdpi/` (144x144).
    - `app/src/main/res/drawable-xxhdpi/logo.png` (144x126) from `drawable-xhdpi/logo.png` (96x84).
    - `app/src/main/res/drawable-xxxhdpi/logo.png` (192x168) from `drawable-xhdpi/logo.png` (96x84).
  - Verifies exact output dimensions and non-empty byte size.

### Step 2: High-Density Asset Execution & Commit
- Execute `python3 tools/resample_high_density_drawables.py`.
- Verify the generated files in `drawable-xxhdpi/` and `drawable-xxxhdpi/`.

### Step 3: Compose Migration to `safePainterResource`
Migrate each of the 11 identified call sites, replacing `painterResource(...)` with `safePainterResource(...)`:
1. **`PairingButtons.kt`**:
   - Import `com.atrainingtracker.trainingtracker.ui.theme.safePainterResource`.
   - In `PairingItem`: `painter = safePainterResource(id = iconRes, fallback = Icons.Default.Sensors)`.
2. **`PairingProtocolBottomSheet.kt`**:
   - In Bluetooth LE item: `painter = safePainterResource(id = Protocol.BLUETOOTH_LE.iconId, fallback = Icons.Default.Bluetooth)`.
   - In ANT+ item: `painter = safePainterResource(id = Protocol.ANT_PLUS.iconId, fallback = Icons.Default.Sensors)`.
3. **`DeviceTypeSelectionDialog.kt`**:
   - In leading icon: `painter = safePainterResource(id = getIconId(type, protocol), fallback = Icons.Default.Sensors)`.
4. **`DeviceItem.kt`**:
   - In leading device type icon: `painter = safePainterResource(id = device.deviceTypeIconRes, fallback = Icons.Default.Sensors)`.
5. **`SensorSourceDialog.kt`**:
   - In sheet title: `iconPainter = safePainterResource(id = sensorType.getIconResId(), fallback = Icons.Default.Sensors)`.
   - In device row: `painter = safePainterResource(id = device.deviceTypeIconRes, fallback = Icons.Default.Sensors)`.
6. **`SensorStatus.kt`**:
   - In indicator row: `painter = safePainterResource(id = type.iconResId, fallback = Icons.Default.Sensors)`.
7. **`SearchArea.kt`**:
   - In search icon row: `painter = safePainterResource(id = iconRes, fallback = Icons.Default.Sensors)`.
8. **`DropboxConnectionHeader.kt`**:
   - In connect button: `painter = safePainterResource(id = R.drawable.dropbox_logo_blue, fallback = Icons.Default.Cloud)`.
9. **`DropboxSettingsDialog.kt`**:
   - In sheet title: `iconPainter = safePainterResource(id = R.drawable.dropbox_logo_blue, fallback = Icons.Default.Cloud)`.
10. **`ActivityTypeSelectionDialog.kt`**:
    - In sport type row: `painter = safePainterResource(id = type.logoId, fallback = Icons.Default.FitnessCenter)`.
11. **`ControlTrackingButton.kt`**:
    - In control action button: `painter = safePainterResource(id = iconRes, fallback = Icons.Default.PlayArrow)`.

### Step 4: Automated CI Contract Tests
1. **`DrawableDensityFallbackAuditTest.kt`**:
   - Add `testCoreDrawablesHaveFullHighDensityCoverage()`: Asserts that `ant_logo.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`, and `logo.png` exist in both `drawable-xxhdpi/` and `drawable-xxxhdpi/`.
2. **`ComposeSafePainterContractTest.kt`**:
   - Author new contract test in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/ComposeSafePainterContractTest.kt`.
   - Scans the 11 target Kotlin files.
   - Asserts that zero raw `painterResource(` calls are used for dynamic drawables and all call sites use `safePainterResource`.

### Step 5: Regression & Governance Verification
- Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests com.atrainingtracker.trainingtracker.resources.DrawableDensityFallbackAuditTest \
    --tests com.atrainingtracker.trainingtracker.ui.theme.SafePainterResourceContractTest \
    --tests com.atrainingtracker.trainingtracker.ui.theme.ComposeSafePainterContractTest
  ```
- Run full clean-room suite: `./gradlew testDebugUnitTest`.
- Run governance tool: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.5`.

---

## 3. Preserved Invariants & Boundary Verification

1. **Crash-Free Guarantee**: `safePainterResource` guarantees zero uncaught `Resources.NotFoundException` exceptions on the UI thread under any conditions (missing splits, custom DPIs, unbundled dynamic features).
2. **Visual Fidelity**: Aspect ratios, transparencies, and icon color filters remain 100% pixel-faithful.
3. **Symbol & API Stability**: All `R.drawable.*` resource symbols remain identical.
4. **Clean-Room Test Pass Rate**: 100% full suite pass rate strictly preserved.
