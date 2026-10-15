# Stage 1 Analysis: ATT-2859 - Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3620](https://rainerblind.atlassian.net/browse/ATT-3620) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-2859`  
**Requirement Mapping**: `REQ-UI-375` (amending `REQ-UI-312`)  
**Test Mapping**: `TST-UI-338`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

The application currently retains dozens of legacy multi-density PNG raster drawables originating from the app's early Android architecture (dating back to 2011–2017). These include sport type icons (`bsport_bike`, `bsport_run`, `bsport_other`), sensor type icons (`bike_cad`, `bike_spd`, `hr`, `bike_pwr`, `run_spd`, `temp`, `bike_speed_and_cadence`, and their `bt_*` variants), and redundant pre-tinted color variations (`bsport_*_gray.png`).

This legacy raster architecture incurs severe disadvantages on modern Android platforms:
1. **Density Split Fragility & Resource Proliferation**: Maintaining identical raster drawables duplicated across 6 density folders (`drawable-ldpi`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`) introduces maintenance overhead, APK bloat, and vulnerability to `Resources$NotFoundException` if density buckets are stripped or non-standard device DPIs are encountered.
2. **Visual Scalability & Rendering Quality**: Fixed-pixel bitmaps pixelate and blur on modern high-DPI smartphone displays (QHD+, 4K, foldables, bike mount cockpits) compared to mathematical vector curves.
3. **Dead Code & Asset Redundancy**: Pre-tinted assets like `bsport_bike_gray.png`, `bsport_run_gray.png`, and `bsport_other_gray.png` exist in all density directories despite having zero references in application code. In modern Jetpack Compose, state tinting is performed dynamically via `Modifier`, `ColorFilter.tint(...)`, or `Icon(tint = ...)`.
4. **Opaque Background Artifacts in Sensor Bitmaps**: Legacy ANT+ sensor bitmaps (`hr.png`, `bike_spd.png`, `bike_cad.png`, `bike_pwr.png`, `run_spd.png`, `temp.png`) were created as RGB bitmaps with hardcoded off-white backgrounds (`#FAFAFA`) rather than transparent alpha channels, resulting in visible rectangular background bounding boxes when rendered against modern dark theme or dynamic Material 3 surfaces.

Migrating these assets to clean Android XML VectorDrawables in the unified `app/src/main/res/drawable/` directory eliminates raster sprawl, achieves crisp density-independent rendering at arbitrary screen dimensions, restores clean transparency, and reduces APK footprint while preserving 100% binary and resource-ID compatibility for all Java and Kotlin call sites (`R.drawable.*`).

---

## 2. Root Cause & Architectural Gap Analysis (Forensic Investigation)

### 2.1 Codebase Audit of Legacy Drawables

A comprehensive scan of `app/src/main/res/` reveals the following legacy raster assets distributed across density buckets:

#### Category A: Sport Type Drawables
- `bsport_bike.png`, `bsport_run.png`, `bsport_other.png`
- **Locations**: `res/drawable/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, `drawable-xxxhdpi/` (6 folders × 3 assets = 18 files).
- **Referenced By**:
  - `SportTypeSelector.kt`: `SportItemSpec(BSportType.RUN, R.drawable.bsport_run, ...)`
  - `ATrainingTrackerMap.kt`: `R.drawable.bsport_run`, `R.drawable.bsport_bike`
  - `MapLayers.kt`: `R.drawable.bsport_run`, `R.drawable.bsport_bike`
  - `BSportType.java`: `RUN(R.drawable.bsport_run, ...)`, `BIKE(R.drawable.bsport_bike, ...)`, `UNKNOWN(R.drawable.bsport_other, ...)`, `CONFLICT(R.drawable.bsport_other, ...)`
  - `ActivityType.java`: `GENERIC_HR(..., R.drawable.bsport_other)`, `RUN_SPEED_AND_CADENCE(..., R.drawable.bsport_run)`, `BIKE_SPEED_AND_CADENCE(..., R.drawable.bsport_bike)`, `BIKE_POWER(..., R.drawable.bsport_bike)`

#### Category B: Dead Sport Type Gray Assets
- `bsport_bike_gray.png`, `bsport_run_gray.png`, `bsport_other_gray.png`
- **Locations**: `res/drawable/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, `drawable-xxxhdpi/` (18 files).
- **Usage**: **0 code references** across the entire application codebase. Only referenced in `SportTypeSelectorContractTest.kt` line 64–70 as an obsolete file existence check. Safe to eliminate.

#### Category C: ANT+ Sensor Icons
- `hr.png`, `bike_spd.png`, `bike_cad.png`, `bike_speed_and_cadence.png`, `bike_pwr.png`, `run_spd.png`, `temp.png`
- **Locations**: `drawable/`, `drawable-ldpi/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/` (5 folders × 7 assets = 35 files).
- **Referenced By**:
  - `DeviceData.kt`: `getIconId(DeviceType, Protocol)`
  - `UIHelper.java`: `getIconId(DeviceType, Protocol)`
  - `RemoteDevices.kt`: `RemoteDeviceUIData(..., R.drawable.hr)`
  - `ControlTrackingScreen.kt`: Mock / preview data

#### Category D: BLE Sensor Icons
- `bt_hr.png`, `bt_bike_spd.png`, `bt_bike_cad.png`, `bt_bike_speed_and_cadence.png`, `bt_bike_pwr.png`, `bt_run.png`
- **Locations**: `drawable/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/` (5 folders × 6 assets = 30 files).
- **Referenced By**:
  - `DeviceData.kt`: `Protocol.BLUETOOTH_LE` mapping
  - `UIHelper.java`: `Protocol.BLUETOOTH_LE` mapping
  - `RemoteDevices.kt`: `R.drawable.bt_bike_spd`, `R.drawable.bt_bike_pwr`

#### Category E: Dead Sensor Asset
- `btle_hr.png`: Exists in `drawable-xhdpi/` and `drawable/`. Zero code references. Safe to eliminate.

**Total Raster Files to Deprecate/Remove**: ~103 PNG files across density directories.

---

## 3. Chesterton's Fence Requirement Archaeology (`REQ-UI-312`)

Before removing legacy drawables, we perform a rigorous Chesterton's Fence archaeology audit on `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*):

1. **Original Requirement ID & Target**: `REQ-UI-312`, introduced in Sprint 2026-41.5 via ticket `ATT-2858` (Epic `ATT-235`: *No crashs*).
2. **Historical Origin & Commit Trace**: Fixed fatal `Resources$NotFoundException` crashes occurring on Google Play density-split APKs where drawables existed only in specific density folders (e.g. `drawable-xhdpi/`) without fallbacks in root `res/drawable/`.
3. **Root Reason for Existing Formulation**: `ATT-2858` copied raster PNGs into root `res/drawable/` as a quick defensive measure and instituted `DrawableDensityFallbackAuditTest.kt` to ensure no density file lacked a root counterpart.
4. **Preservation of Core Invariants**:
   - `DrawableDensityFallbackAuditTest.kt` explicitly verifies that any asset in a density folder has a root counterpart in `res/drawable/`. It already supports XML vectors:
     ```kotlin
     val rootDrawableBases = rootDrawableDir.listFiles()
         ?.map { it.nameWithoutExtension }
         ?.toSet() ?: emptySet()
     ```
   - When we replace raster PNGs with XML vectors in root `res/drawable/` and delete the density-specific PNGs, the asset continues to resolve seamlessly, root fallback is inherently 100% satisfied, and density splits can never fail to find the asset.
   - Amending `REQ-UI-312` with `REQ-UI-375` elevates the architecture from "fallback raster copies" to "first-class density-independent vector drawables".

---

## 4. Bounded Scope & Call-Site Audit

### 4.1 In Scope
1. **Sport Type Vectors**: Create XML vector drawables in `res/drawable/`:
   - `bsport_bike.xml`
   - `bsport_run.xml`
   - `bsport_other.xml`
2. **ANT+ Sensor Vectors**: Create XML vector drawables in `res/drawable/`:
   - `hr.xml`
   - `bike_spd.xml`
   - `bike_cad.xml`
   - `bike_speed_and_cadence.xml`
   - `bike_pwr.xml`
   - `run_spd.xml`
   - `temp.xml`
3. **BLE Sensor Vectors**: Create XML vector drawables in `res/drawable/`:
   - `bt_hr.xml`
   - `bt_bike_spd.xml`
   - `bt_bike_cad.xml`
   - `bt_bike_speed_and_cadence.xml`
   - `bt_bike_pwr.xml`
   - `bt_run.xml`
4. **Legacy PNG Deletion**: Remove superseded PNGs across `drawable/`, `drawable-ldpi/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, `drawable-xxxhdpi/`.
5. **Dead Asset Cleanup**: Remove all instances of `bsport_bike_gray.png`, `bsport_run_gray.png`, `bsport_other_gray.png`, and `btle_hr.png`.
6. **Contract Test Modernization**:
   - Update `SportTypeSelectorContractTest.kt` to assert the presence of vector drawables (`.xml`) rather than hardcoded `.png` and removed `_gray` assets.
   - Verify `DrawableDensityFallbackAuditTest.kt` passes with 100% compliance.
   - Add dedicated vector drawable contract test verifying inflation and dimension scaling.

### 4.2 Out of Scope (Deferred to Dedicated Tickets)
- Third-party corporate logos (`connected_with_*.png`, `connect_with_*.png`, `logo_square_strava.png`, `logo_square_runkeeper.png`, `dropbox_logo_blue.png`) — handled under external sync/partner branding tickets (e.g. `ATT-3551`).
- Map navigation marker overlays (`arrowhead.png`, `start_logo_map.png`, `stop_logo_map.png`) — handled under map POI harmonization tickets.
- Battery level drawables (`stat_sys_battery_*.png`) — handled under system telemetry modernization.

---

## 5. Architectural & Design Decisions

1. **Format: Android XML VectorDrawable in `res/drawable/`**:
   - Maintains exact resource identifier parity: `R.drawable.bsport_bike`, `R.drawable.hr`, etc.
   - Requires zero modifications to Java legacy callers (`UIHelper.java`, `BSportType.java`, `ActivityType.java`) or Kotlin Compose callers (`SportTypeSelector.kt`, `DeviceData.kt`).
   - Supports transparent backgrounds by default, correcting the legacy `#FAFAFA` bounding box glitch in dark mode.
2. **Unified 24dp Viewport with Scalable Vector Paths**:
   - Standard 24dp × 24dp viewport (`android:viewportWidth="24"` / `android:viewportHeight="24"`).
   - Crisp rendering across all display scales: 16dp, 24dp, 48dp, 96dp, 128dp.
3. **Dynamic Tinting Architecture**:
   - By using `@android:color/white` or `#FFFFFF` for the vector paths without hardcoding `android:tint`, Compose `Icon(tint = ...)` and `Image(colorFilter = ...)` can dynamically tint to any theme color (e.g. `MaterialTheme.colorScheme.onSurface`, `Color.Gray`, primary accent).
   - BLE variants incorporate a Bluetooth indicator glyph cleanly styled within the vector composition.

---

## 6. Acceptance Criteria

- [ ] All 16 migrated sport and sensor icons resolve to valid XML vector drawables in `app/src/main/res/drawable/`.
- [ ] Legacy raster PNG files for migrated assets and obsolete `_gray` files are completely removed across all density folders.
- [ ] No `Resources$NotFoundException` occurs on any density bucket or split configuration.
- [ ] `DrawableDensityFallbackAuditTest` and `SportTypeSelectorContractTest` pass 100%.
- [ ] The full clean-room unit test suite passes with 0 regressions.
