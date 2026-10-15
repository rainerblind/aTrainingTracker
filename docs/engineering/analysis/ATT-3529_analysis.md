# Stage 1: Problem Domain & Root Cause Analysis - ATT-3529: Eliminate ResourcesNotFoundException crashes by populating high-density drawables and migrating Compose icon call sites to safePainterResource

**Ticket**: [ATT-3529](https://atrainingtracker.atlassian.net/browse/ATT-3529)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary & Production Crash Forensics

### 1.1 Production Crash Evidence
In production release V4.9.38.5 (build 269), Firebase Crashlytics captured a fatal UI crash on device:
```text
Fatal Exception: android.content.res.Resources$NotFoundException: Resource ID #0x7f080079
       at android.content.res.ResourcesImpl.getValue(ResourcesImpl.java:225)
       at android.content.res.Resources.getValue(Resources.java:1428)
       at androidx.compose.ui.res.ResourceIdCache.resolveResourcePath(Resources.android.kt:38)
       at androidx.compose.ui.res.PainterResources_androidKt.painterResource(PainterResources.android.kt:62)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PairingButtonsKt.PairingItem(PairingButtons.kt:92)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PairingButtonsKt.PairingButtons(PairingButtons.kt:58)
```
Resource ID `0x7f080079` resolves to `R.drawable.ant_logo`.

### 1.2 Forensic Analysis & Dual Root Causes
Forensic investigation across the codebase, resource tables, and Android App Bundle packaging revealed two compounding architectural root causes:

1. **Root Cause A: Density Gap in Google Play App Bundles (.aab)**
   - When building and distributing an Android App Bundle (`.aab`) via Google Play, Google's `bundletool` splits raster drawables across density-specific configuration splits (e.g. `split_config.xxhdpi.apk`, `split_config.xxxhdpi.apk`).
   - An audit of `app/src/main/res/drawable*` reveals that `ant_logo.png` exists only in:
     - `drawable-ldpi/` (36x36)
     - `drawable-mdpi/` (48x48)
     - `drawable-hdpi/` (72x72)
     - `drawable-xhdpi/` (96x96)
     - `drawable/` (96x96)
   - It is completely **missing** from `drawable-xxhdpi/` (144x144) and `drawable-xxxhdpi/` (192x192).
   - Similarly:
     - `logo_protocol_bluetooth.png` is missing from `drawable-xxxhdpi/` (192x192).
     - `dropbox_logo_blue.png` is missing from `drawable-xxxhdpi/` (192x192).
     - `logo.png` is missing from `drawable-xxhdpi/` (144x126) and `drawable-xxxhdpi/` (192x168).
   - On modern physical devices operating at `xxhdpi` (480 dpi) or `xxxhdpi` (640 dpi), Android's resource system searches the device's installed density split APK. When the split APK lacks the requested asset and bundletool does not preserve the lower-density raster in that split, `ResourcesImpl.getValue()` fails with `Resources$NotFoundException`.

2. **Root Cause B: Unguarded Jetpack Compose Callsites**
   - Ticket `ATT-2858` previously authored `safePainterResource` in `com.atrainingtracker.trainingtracker.ui.theme.SafePainterResource.kt` to defensively guard against `Resources$NotFoundException` by catching exceptions and rendering an optional fallback `ImageVector` or transparent painter.
   - However, multiple critical Jetpack Compose UI components were never migrated and continue to invoke raw `painterResource(id = ...)`:
     - `PairingButtons.kt:92`: `painter = painterResource(id = iconRes)` (the exact crash site).
     - `PairingProtocolBottomSheet.kt:67, 88`: `painter = painterResource(id = Protocol.BLUETOOTH_LE.iconId)` and `painter = painterResource(id = Protocol.ANT_PLUS.iconId)`.
     - `DeviceTypeSelectionDialog.kt:82`: `painter = painterResource(id = getIconId(type, protocol))`.
     - `DeviceItem.kt:93`: `painter = painterResource(id = device.deviceTypeIconRes)`.
     - `SensorSourceDialog.kt:85, 212`: `iconPainter = painterResource(...)` and `painter = painterResource(id = device.deviceTypeIconRes)`.
     - `SensorStatus.kt:107`: `painter = painterResource(id = type.iconResId)`.
     - `SearchArea.kt:89`: `painter = painterResource(id = iconRes)`.
     - `DropboxConnectionHeader.kt:79`: `painter = painterResource(id = R.drawable.dropbox_logo_blue)`.
     - `DropboxSettingsDialog.kt:96`: `iconPainter = painterResource(id = R.drawable.dropbox_logo_blue)`.
     - `ActivityTypeSelectionDialog.kt:70`: `painter = painterResource(id = type.logoId)`.
     - `ControlTrackingButton.kt:135`: `painter = painterResource(id = iconRes)`.

Because raw `painterResource` throws an unhandled `Resources$NotFoundException`, any missing density bucket causes an immediate, catastrophic crash on the main UI thread.

---

## 2. Chesterton's Fence Requirement Archaeology

1. **Origin of `safePainterResource` (`ATT-2858`)**:
   - `ATT-2858` identified that Compose's `painterResource` is fragile because it assumes compile-time resource ID existence guarantees run-time asset presence across all dynamic APK splits.
   - It created `safePainterResource(id: Int, fallback: ImageVector? = null): Painter` which wraps `ContextCompat.getDrawable` in `try / catch (Resources.NotFoundException)`.
   - **Chesterton's Fence Finding**: The defensive function already exists and is well-tested (`SafePainterResourceContractTest.kt`), but was only adopted in a handful of screens (e.g. `SportTypeSelector.kt`). It was not systematically applied across sensor pairing, device lists, dialogs, and cloud sync headers.

2. **Origin of `DrawableDensityFallbackAuditTest` (`ATT-2858`)**:
   - `ATT-2858` added `DrawableDensityFallbackAuditTest.kt` to ensure every asset in `drawable-*` had a root counterpart in `res/drawable/`.
   - **Chesterton's Fence Finding**: The audit test was unidirectional: it verified that if a file existed in `drawable-mdpi`, it had a file in `drawable/`. It did **not** verify that core assets had complete high-density coverage across `drawable-xxhdpi` and `drawable-xxxhdpi`. Consequently, density gaps in high-DPI buckets went undetected.

---

## 3. Scope Bounding & Proposed Architecture

To permanently eliminate `Resources$NotFoundException` crashes and prevent regressions, the solution consists of two complementary pillars:

```
+-----------------------------------------------------------------------------------+
|                            ATT-3529 Two-Pillar Defense                            |
+-----------------------------------------------------------------------------------+
| Pillar 1: High-Density Asset Gap Resolution (Build-Time / Resource Tree)         |
|   - Generate crisp xxhdpi (144x144 / 3x) and xxxhdpi (192x192 / 4x) PNGs for:   |
|     • ant_logo.png                                                                |
|     • logo_protocol_bluetooth.png (xxxhdpi)                                       |
|     • dropbox_logo_blue.png (xxxhdpi)                                             |
|     • logo.png (xxhdpi 144x126, xxxhdpi 192x168)                                  |
|   - Eliminate all density gaps for core protocol, branding, and status assets.   |
+-----------------------------------------------------------------------------------+
| Pillar 2: Defensive Compose Architecture Migration (Run-Time UI Resilience)      |
|   - Migrate all 11 identified unguarded call sites to safePainterResource(...)    |
|   - Supply meaningful fallback ImageVectors (e.g. Icons.Default.Sensors,         |
|     Icons.Default.Bluetooth, Icons.Default.Cloud, Icons.Default.Sports)           |
|   - Guarantees zero UI thread crashes even under corrupt or missing APK splits.   |
+-----------------------------------------------------------------------------------+
| Automated CI Enforcement:                                                         |
|   - Extend DrawableDensityFallbackAuditTest to enforce high-density coverage.     |
|   - Create ComposeSafePainterContractTest to prevent raw painterResource usage.   |
+-----------------------------------------------------------------------------------+
```

### 3.1 Pillar 1: High-Density Asset Population
Using high-quality Lanczos resampling from the highest available source rasters:
- `ant_logo.png`: Populate `drawable-xxhdpi` (144x144) and `drawable-xxxhdpi` (192x192).
- `logo_protocol_bluetooth.png`: Populate `drawable-xxxhdpi` (192x192) from xxhdpi (144x144).
- `dropbox_logo_blue.png`: Populate `drawable-xxxhdpi` (192x192) from xxhdpi (144x144).
- `logo.png`: Populate `drawable-xxhdpi` (144x126) and `drawable-xxxhdpi` (192x168).

### 3.2 Pillar 2: Systematic Compose Migration to `safePainterResource`
Replace raw `painterResource(id = ...)` with `safePainterResource(id = ..., fallback = ...)` across all 11 identified call sites:
1. `PairingButtons.kt`:
   - `safePainterResource(id = iconRes, fallback = if (isAnt) Icons.Default.Sensors else Icons.Default.Bluetooth)`
2. `PairingProtocolBottomSheet.kt`:
   - `safePainterResource(id = Protocol.BLUETOOTH_LE.iconId, fallback = Icons.Default.Bluetooth)`
   - `safePainterResource(id = Protocol.ANT_PLUS.iconId, fallback = Icons.Default.Sensors)`
3. `DeviceTypeSelectionDialog.kt`:
   - `safePainterResource(id = getIconId(type, protocol), fallback = Icons.Default.Sensors)`
4. `DeviceItem.kt`:
   - `safePainterResource(id = device.deviceTypeIconRes, fallback = Icons.Default.Sensors)`
5. `SensorSourceDialog.kt`:
   - `safePainterResource(id = sensorType.getIconResId(), fallback = Icons.Default.Sensors)`
   - `safePainterResource(id = device.deviceTypeIconRes, fallback = Icons.Default.Sensors)`
6. `SensorStatus.kt`:
   - `safePainterResource(id = type.iconResId, fallback = Icons.Default.Sensors)`
7. `SearchArea.kt`:
   - `safePainterResource(id = iconRes, fallback = Icons.Default.Sensors)`
8. `DropboxConnectionHeader.kt`:
   - `safePainterResource(id = R.drawable.dropbox_logo_blue, fallback = Icons.Default.Cloud)`
9. `DropboxSettingsDialog.kt`:
   - `safePainterResource(id = R.drawable.dropbox_logo_blue, fallback = Icons.Default.Cloud)`
10. `ActivityTypeSelectionDialog.kt`:
    - `safePainterResource(id = type.logoId, fallback = Icons.Default.FitnessCenter)`
11. `ControlTrackingButton.kt`:
    - `safePainterResource(id = iconRes, fallback = Icons.Default.PlayArrow)`

### 3.3 Automated Contract & Audit Tests
1. **`DrawableDensityFallbackAuditTest.kt`**:
   - Add `testCoreDrawablesHaveFullHighDensityCoverage()`: Asserts that core protocol, branding, and cloud drawables (`ant_logo`, `logo_protocol_bluetooth`, `dropbox_logo_blue`, `logo`) are present in both `drawable-xxhdpi` and `drawable-xxxhdpi`.
2. **`ComposeSafePainterContractTest.kt`**:
   - Inspect all Compose UI source files managing sensor pairing, device selection, status, and cloud integration to verify they use `safePainterResource` rather than raw `painterResource`.

---

## 4. Invariants to Preserve

1. **Zero UI Thread Crashes**: No missing, corrupt, or unbundled resource can ever terminate the application process with `Resources$NotFoundException`.
2. **Visual Fidelity & Scaling**: Populated high-density PNG assets must maintain exact aspect ratios, transparent alphachannels, and visual sharpness on QHD+/4K screens.
3. **Binary Resource ID Stability**: All `R.drawable.*` identifiers remain 100% unchanged.
4. **Localization Parity**: UI text labels and descriptions remain untouched.
5. **100% Clean-Room Test Suite Pass Rate**: Full regression suite (`./gradlew testDebugUnitTest`) must execute with zero failures.

---

## 5. Verification Strategy & Next Steps

1. **Stage 2 (Requirement & Test Specification)**:
   - Formulate `REQ-UI-380` (*Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Architecture*).
   - Formulate `TST-UI-339` (*Density Coverage Audit & Safe Painter Call Site Verification Suite*).
2. **Stage 3 (Implementation Plan)**:
   - Atomic tasks: asset generation script, high-density PNG generation, call site migrations, contract test implementation.
3. **Stage 4 (Implementation)**:
   - Generate high-density PNGs and commit them.
   - Refactor the 11 Compose call sites to use `safePainterResource`.
   - Update `DrawableDensityFallbackAuditTest.kt` and author `ComposeSafePainterContractTest.kt`.
4. **Stage 5 (Verification)**:
   - Run targeted tests and full clean-room `./gradlew testDebugUnitTest`.
   - Produce walkthrough and verify requirement governance.
