# Stage 2: Requirement & Test Specification - ATT-2859: Migrate legacy multi-density raster drawables to scalable Jetpack Compose vector assets

**Ticket**: [ATT-2859](https://rainerblind.atlassian.net/browse/ATT-2859)  
**Sub-task**: [ATT-3621](https://rainerblind.atlassian.net/browse/ATT-3621) (`[Test-Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-375` (*Scalable Vector Asset Architecture & Multi-Density Raster Migration*, amending `REQ-UI-312`)  
**Test Spec ID**: `TST-UI-338` (*Scalable Vector Asset Architecture & Multi-Density Raster Migration Verification*)  
**Branch**: `feature/ATT-2859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Requirement Specification (REQ-UI-375)

### 1.1 Problem Statement & Rationale
The application historically maintained 100+ raster PNG drawables distributed across 6 legacy density folders (`drawable-ldpi/`, `-mdpi/`, `-hdpi/`, `-xhdpi/`, `-xxhdpi/`, `-xxxhdpi/`). This raster architecture incurs major drawbacks:
1. **Density Split Fragility**: Multi-density folders risk `Resources$NotFoundException` crashes on Google Play density-split APKs or custom DPI devices when specific density folders are absent.
2. **Visual Scalability**: Fixed-dimension raster bitmaps pixelate and blur on modern high-DPI smartphone displays (QHD+, foldables, bike cockpits).
3. **Dead Code & Asset Redundancy**: 18 pre-tinted gray files (`bsport_*_gray.png`) exist across density folders with 0 application code references.
4. **Opaque Background Glitches**: Legacy sensor bitmaps were saved as RGB bitmaps with hardcoded `#FAFAFA` off-white backgrounds, causing unsightly rectangular bounding boxes in dark theme and dynamic Material 3 surfaces.

`REQ-UI-375` elevates the codebase by migrating core sport type and sensor assets to clean, density-independent XML VectorDrawables residing exclusively in root `app/src/main/res/drawable/`, deleting obsolete density-specific PNG files, and enforcing dynamic Compose tinting.

---

### 1.2 Functional & Architectural Requirements

#### 1.2.1 Unified XML VectorDrawable Assets (`REQ-UI-375.1`)
1. The system SHALL replace raster PNG implementations of core sport and sensor assets with clean Android XML VectorDrawables residing in root `app/src/main/res/drawable/`.
2. **Migrated Sport Type Assets**:
   - `bsport_bike.xml` (Cycling sport indicator)
   - `bsport_run.xml` (Running sport indicator)
   - `bsport_other.xml` (General / multisport indicator)
3. **Migrated ANT+ Sensor Assets**:
   - `hr.xml` (Heart rate monitor)
   - `bike_spd.xml` (Bicycle speed sensor)
   - `bike_cad.xml` (Bicycle cadence sensor)
   - `bike_speed_and_cadence.xml` (Bicycle speed & cadence combined sensor)
   - `bike_pwr.xml` (Bicycle power meter)
   - `run_spd.xml` (Running speed / footpod)
   - `temp.xml` (Environment / temperature sensor)
4. **Migrated BLE Sensor Assets**:
   - `bt_hr.xml` (Bluetooth heart rate monitor)
   - `bt_bike_spd.xml` (Bluetooth bicycle speed sensor)
   - `bt_bike_cad.xml` (Bluetooth bicycle cadence sensor)
   - `bt_bike_speed_and_cadence.xml` (Bluetooth combined sensor)
   - `bt_bike_pwr.xml` (Bluetooth bicycle power meter)
   - `bt_run.xml` (Bluetooth footpod)
5. **Coordinate & Scaling Invariant**:
   - All vector drawables SHALL define standard scalable viewports (e.g. `viewportWidth="24"`, `viewportHeight="24"`, `width="24dp"`, `height="24dp"` or proportional 48dp / 96dp geometries) ensuring distortion-free scaling to arbitrary dimensions (16dp to 256dp).
6. **Transparent Alpha Invariant**:
   - All vector drawables SHALL have transparent backgrounds, eliminating opaque rectangular `#FAFAFA` bounding boxes in dark theme.

#### 1.2.2 Dynamic Theming & Dead Asset Elimination (`REQ-UI-375.2`)
1. **Dynamic Compose Tinting**:
   - Vector paths SHALL use neutral fills (e.g. `@android:color/white` or `#FFFFFFFF` without hardcoded `android:tint`) to enable Compose `Icon(..., tint = ...)` and `Image(..., colorFilter = ColorFilter.tint(...))` to apply theme-appropriate colors dynamically.
2. **Dead Asset Removal**:
   - All instances of `bsport_bike_gray.png`, `bsport_run_gray.png`, `bsport_other_gray.png`, and `btle_hr.png` across all density folders (`drawable/`, `drawable-ldpi/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, `drawable-xxxhdpi/`) SHALL be permanently deleted.
3. **Obsolete Multi-Density PNG Deletion**:
   - For all 16 migrated assets, density-specific `.png` files in `drawable-ldpi/`, `drawable-mdpi/`, `drawable-hdpi/`, `drawable-xhdpi/`, `drawable-xxhdpi/`, `drawable-xxxhdpi/`, and root `drawable/` SHALL be completely deleted, leaving the XML VectorDrawable in root `drawable/` as the single authoritative source of truth.

#### 1.2.3 Binary & Resource ID Compatibility (`REQ-UI-375.3`)
1. Resource symbol names (`R.drawable.bsport_bike`, `R.drawable.hr`, `R.drawable.bt_bike_pwr`, etc.) SHALL remain 100% identical.
2. Zero modifications to Java/Kotlin call-site signatures (`BSportType.java`, `ActivityType.java`, `UIHelper.java`, `DeviceData.kt`, `SportTypeSelector.kt`, `RemoteDevices.kt`, `ATrainingTrackerMap.kt`, `MapLayers.kt`).

---

### 1.3 Chesterton's Fence Audit (`REQ-PRO-022`)

1. **Original Requirement ID & Target**: Amends `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*).
2. **Historical Origin & Commit Trace**: Ticket `ATT-2858` (Sprint `2026-41.5`, Epic `ATT-235`).
3. **Root Reason for Existing Formulation**: `ATT-2858` duplicated raster PNG files into root `res/drawable/` as a quick defensive measure against density-split crashes.
4. **Preservation of Core Invariants**:
   - Density-split immunity: A vector drawable in root `res/drawable/` satisfies resolution for all screen densities automatically.
   - `DrawableDensityFallbackAuditTest.kt` passes with 100% compliance since any remaining density drawables have root counterparts, and migrated assets have their density variants removed.
   - `SportTypeSelectorContractTest.kt` is modernized to assert valid drawable resource resolution (`.xml` or `.png`) without hardcoded raster filename constraints.

---

## 2. Test Specification (TST-UI-338)

### 2.1 Test Cases

#### TST-UI-338.1: Vector Asset Existence & Resource Resolution Contract
- **Class**: `VectorAssetResolutionContractTest`
- **Objective**: Verify that all 16 migrated sport type and sensor assets exist as XML vector drawables in `app/src/main/res/drawable/` and contain valid `<vector>` root elements.
- **Verification Steps**:
  1. Inspect `app/src/main/res/drawable/` for:
     - `bsport_bike.xml`, `bsport_run.xml`, `bsport_other.xml`
     - `hr.xml`, `bike_spd.xml`, `bike_cad.xml`, `bike_speed_and_cadence.xml`, `bike_pwr.xml`, `run_spd.xml`, `temp.xml`
     - `bt_hr.xml`, `bt_bike_spd.xml`, `bt_bike_cad.xml`, `bt_bike_speed_and_cadence.xml`, `bt_bike_pwr.xml`, `bt_run.xml`
  2. Parse XML content of each file and verify that the root tag is `<vector>` with valid `android:viewportWidth` and `android:viewportHeight`.

#### TST-UI-338.2: Multi-Density Raster Deletion & Cleanup Audit
- **Class**: `VectorAssetResolutionContractTest`
- **Objective**: Verify that no obsolete `.png` files remain for the 16 migrated assets or dead gray files across any density directory.
- **Verification Steps**:
  1. Scan all density folders: `drawable-ldpi`, `drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`, `drawable`.
  2. Assert that for each of the 16 migrated names, `<name>.png` does not exist.
  3. Assert that `bsport_*_gray.png` and `btle_hr.png` do not exist anywhere in `res/`.

#### TST-UI-338.3: Modernized SportTypeSelector Contract Audit
- **Class**: `SportTypeSelectorContractTest`
- **Objective**: Verify that `SportTypeSelector` safely loads sport type drawables via `safePainterResource` and that the sport type vector assets exist in root `res/drawable/`.
- **Verification Steps**:
  1. Execute `testSportTypeSelectorUsesSafePainterResourceAndFallbackVectors`.
  2. Execute modernized `testAllSportTypeDrawablesExistInRootDrawableDirectory` confirming `bsport_run.xml`, `bsport_bike.xml`, `bsport_other.xml` exist.

#### TST-UI-338.4: Density Fallback Audit Compliance
- **Class**: `DrawableDensityFallbackAuditTest`
- **Objective**: Execute existing `testAllDensityDrawablesHaveRootFallback` and assert that 0 missing fallbacks exist across all density directories.

---

### 2.2 Traceability Matrix

| Requirement Clause | Test Case | Target Class / File | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-375.1` (Unified XML Vector Assets) | `TST-UI-338.1` | `VectorAssetResolutionContractTest.kt` | Planned |
| `REQ-UI-375.2` (Density Cleanup & Dead File Deletion) | `TST-UI-338.2` | `VectorAssetResolutionContractTest.kt` | Planned |
| `REQ-UI-375.3` (Sport Type Modernization & Contract) | `TST-UI-338.3` | `SportTypeSelectorContractTest.kt` | Planned |
| `REQ-UI-375.3` (Density Split Fallback Compliance) | `TST-UI-338.4` | `DrawableDensityFallbackAuditTest.kt` | Planned |

---

### 2.3 Acceptance Criteria (Given-When-Then)

- **Scenario 1: Vector drawable existence & parsing**
  - *Given* the Android resource directory `app/src/main/res/drawable/`,
  - *When* checking the 16 migrated sport and sensor drawable files,
  - *Then* each file exists with extension `.xml` and valid `<vector>` XML root tag.

- **Scenario 2: Density folder cleanup**
  - *Given* all resource directories matching `drawable-*`,
  - *When* searching for raster PNGs of the 16 migrated names or obsolete gray assets,
  - *Then* zero matching PNG files are found.

- **Scenario 3: Zero regression in contract tests**
  - *Given* `SportTypeSelectorContractTest` and `DrawableDensityFallbackAuditTest`,
  - *When* executed via `./gradlew testDebugUnitTest`,
  - *Then* all contract tests pass with 100% compliance.
