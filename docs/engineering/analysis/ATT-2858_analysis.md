# Stage 1 Analysis: ATT-2858 - Eliminate ResourcesNotFoundException crashes by populating fallback drawables and defensive Compose loading

**Ticket**: [ATT-2858](https://atrainingtracker.atlassian.net/browse/ATT-2858)  
**Sub-task**: [ATT-2868](https://atrainingtracker.atlassian.net/browse/ATT-2868) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.4`  
**Active Sprint**: `2026-41.5`  
**Branch**: `improvement/ATT-2858`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During production execution of version 4.9.38.3 (build 266), Firebase Crashlytics reported a fatal crash ([ATT-2856](https://atrainingtracker.atlassian.net/browse/ATT-2856)):
```text
Fatal Exception: android.content.res.Resources$NotFoundException: Resource ID #0x7f080087
       at android.content.res.ResourcesImpl.getValue(ResourcesImpl.java:225)
       at android.content.res.Resources.getValue(Resources.java:1428)
       at androidx.compose.ui.res.ResourceIdCache.resolveResourcePath(Resources.android.kt:38)
       at androidx.compose.ui.res.PainterResources_androidKt.painterResource(PainterResources.android.kt:62)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SportTypeSelectorKt.SportItem(SportTypeSelector.kt:92)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SportTypeSelectorKt.SportTypeSelector(SportTypeSelector.kt:68)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreenKt.ControlTrackingScreen(ControlTrackingScreen.kt:162)
```

The underlying issue is a systemic architectural vulnerability across the Android resource directories:
1. A forensic resource audit revealed that **50 bitmap and drawable assets exist solely within screen-density folders** (`drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`, `drawable-nodpi`, `drawable-ldpi`), without baseline fallback counterparts in the root `app/src/main/res/drawable/` directory.
2. When Google Play delivers split APKs stripped of unused densities, or when devices run with custom/interpolated display scalings (such as foldables, tablets, tvdpi, or accessibility display scalings), Android attempts to fall back to the root `res/drawable/` directory.
3. If no fallback exists, the asset resolver fails with `android.content.res.Resources$NotFoundException`.
4. In Jetpack Compose, calling `painterResource(id)` directly throws an unhandled `Resources$NotFoundException` on the main UI thread, immediately crashing the app.

Ticket **ATT-2858** provides a comprehensive blanket resolution by:
- Populating root `app/src/main/res/drawable/` fallbacks for all 50 density-only assets.
- Introducing a guarded Compose asset loading utility with safe fallback vector rendering.
- Implementing an automated regression audit test to permanently guard all drawables referenced by UI components against missing fallbacks.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 The Android Density-Fallback Resolver Mechanism
Android's resource system resolves drawables by evaluating the device's display density bucket:
$$\text{Device DPI} \longrightarrow \text{Bucket} \in \{\text{mdpi}, \text{hdpi}, \text{xhdpi}, \text{xxhdpi}, \text{xxxhdpi}\}$$
When an asset is requested:
1. The framework checks the best-matching density bucket folder.
2. If absent or stripped (as in APK splits where Google Play delivers only the primary target density), the framework searches remaining buckets and ultimately falls back to the default `res/drawable/` directory.
3. If the asset does not exist in `res/drawable/` and the device DPI bucket was not bundled in the split APK, `ResourcesImpl.getValue()` throws `Resources$NotFoundException: Resource ID #0x...`.

### 2.2 Affected Assets Inventory
An automated audit of `app/src/main/res/` identified exactly 50 drawables missing from `res/drawable/`:
1. **Sport Type Badges (Crashing in ATT-2856)**:
   - `bsport_run.png`, `bsport_run_gray.png`
   - `bsport_bike.png`, `bsport_bike_gray.png`
   - `bsport_other.png`, `bsport_other_gray.png`
2. **Sensor Hardware Icons (`RemoteDevices.kt`)**:
   - `bike_cad.png`, `bike_spd.png`, `bike_pwr.png`, `bike_speed_and_cadence.png`
   - `hr.png`, `temp.png`, `run_spd.png`
   - `bt_bike_cad.png`, `bt_bike_spd.png`, `bt_bike_pwr.png`, `bt_bike_speed_and_cadence.png`
   - `bt_hr.png`, `bt_run.png`, `btle_hr.png`
3. **Protocol & Branding Icons (`AntServicesStatusCard.kt`, `PairingButtons.kt`)**:
   - `ant_logo.png`, `logo_protocol_bluetooth.png`
4. **Partner Service Badges (`UploadActivity`, `Settings`)**:
   - `connect_with_strava.png`, `connected_with_strava.png`
   - `connect_with_runkeeper.png`, `connected_with_runkeeper.png`, `logo_square_runkeeper.png`
   - `connect_with_training_peaks.png`, `connected_with_training_peaks.png`, `logo_square_training_peaks.png`
5. **Map & Navigation Overlay Assets**:
   - `start_logo_map.png`, `stop_logo_map.png`, `arrowhead.png`, `max_line_distance_logo_map.png`
6. **UI Controls & Battery Icons**:
   - `research_icon.png`, `icon_configure_views.png`, `ic_phone_android_black_48dp.png`, `icon_pebble.png`, `logo.png`, `menu_header_background.jpg`
   - `stat_sys_battery_10.png`, `stat_sys_battery_20.png`, `stat_sys_battery_40.png`, `stat_sys_battery_60.png`, `stat_sys_battery_80.png`, `stat_sys_battery_unknown.png`
   - `export_error.png`, `export_failed.png`, `export_success.png`, `logo_512.png`

### 2.3 Unguarded Jetpack Compose Calls
Jetpack Compose's standard `painterResource(id)` offers no fallback or recovery mechanism when `Resources$NotFoundException` occurs.
In `SportTypeSelector.kt:92`:
```kotlin
Icon(
    painter = painterResource(id = iconRes),
    contentDescription = null,
    modifier = Modifier.size(24.dp),
    tint = Color.Unspecified
)
```
If `iconRes` cannot be resolved by `ResourcesImpl`, the entire Compose hierarchy crashes immediately. A guarded approach using `ContextCompat.getDrawable(...)` and a defensive fallback (`ImageVector` or fallback drawable) is necessary at high-risk call sites.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Populate baseline assets in `app/src/main/res/drawable/` for all 50 drawables currently restricted to density folders.
  2. Baseline assets will be sourced from `drawable-xhdpi` (standard baseline) or `drawable-nodpi`.
  3. Introduce a safe Compose painter loading helper (e.g. `safePainterResource` or guarded `rememberSafePainter`) with fallback support.
  4. Harden `SportTypeSelector.kt`, `RemoteDevices.kt`, and `AntServicesStatusCard.kt` with defensive loading.
  5. Implement `DrawableDensityFallbackAuditTest` in the automated test suite to permanently enforce fallback asset presence.
  6. Directly resolve and close field crash [ATT-2856](https://atrainingtracker.atlassian.net/browse/ATT-2856).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Vectorizing existing raster assets: converting all 50 PNGs to VectorDrawables is out-of-scope for an emergency hotfix.
  2. Modifying sensor pairing logic or sport selection state management.
  3. Altering density bucket artwork or scaling dimensions.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Net-New Requirement**:
  - `REQ-UI-312`: *Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*.
  - Verification ID: `TST-UI-272`.
* **Chesterton's Fence Audit**:
  - No existing requirements in `docs/requirements.md` are modified or weakened.
  - Adding assets to `app/src/main/res/drawable/` strictly enhances Android resource resolution by providing default fallbacks without affecting density-specific asset selection on high-DPI screens (Android always prefers density-specific folders when present).

---

## 5. Architectural Strategy & High-Level Solution

```text
┌────────────────────────────────────────────────────────────────────────┐
│ Android Resource Resolution Pipeline (Hardened)                       │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Request: context.resources.getDrawable(R.drawable.bsport_run)       │
│    │                                                                   │
│    ├──► Density match found in drawable-xxhdpi/xhdpi/etc.?             │
│    │    └──► YES: Resolve density-specific asset                       │
│    │                                                                   │
│    └──► NO (APK Split, non-standard DPI, tablet/foldable):             │
│         └──► FALLBACK: Resolve from app/src/main/res/drawable/         │
│              (Populated for all 50 drawables - guaranteed resolution)   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Defensive Compose Asset Loading Layer (safePainterResource)            │
├────────────────────────────────────────────────────────────────────────┤
│ try {                                                                  │
│     ContextCompat.getDrawable(context, id)?.let {                      │
│         rememberDrawablePainter(it)                                    │
│     } ?: rememberVectorPainter(fallbackVector)                         │
│ } catch (e: Resources.NotFoundException) {                              │
│     rememberVectorPainter(fallbackVector)                              │
│ }                                                                      │
└────────────────────────────────────────────────────────────────────────┘
```

1. **Root Fallback Asset Copying**:
   - Sourced from `app/src/main/res/drawable-xhdpi/` (or `drawable-nodpi` for battery/export assets) into `app/src/main/res/drawable/`.
2. **Defensive Utility**:
   - Create `SafePainterResource.kt` in `ui.theme` or `ui.core` providing `safePainterResource(id: Int, fallback: ImageVector): Painter`.
3. **Automated Audit Test**:
   - `DrawableDensityFallbackAuditTest` asserts that every raster asset residing in any `drawable-*` folder also has an exact counterpart in `app/src/main/res/drawable/`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Android's preference for density-specific drawables on matching screens remains 100% intact.
  2. UI appearance, icon dimensions, and aspect ratios remain unchanged.
  3. Zero regressions across existing unit and contract test suites.
  4. Fix applies cleanly to hotfix release `V4.9.38.4`.

* **Risk Rating**: **LOW**
  - Populating fallback drawables is an additive, purely protective Android resource change.
  - Sourcing from `drawable-xhdpi` provides a universally compatible baseline.
