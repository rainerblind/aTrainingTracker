# Stage 2: Requirement & Test Specification - ATT-2858: Eliminate ResourcesNotFoundException crashes by populating fallback drawables and defensive Compose loading

**Ticket**: [ATT-2858](https://atrainingtracker.atlassian.net/browse/ATT-2858)  
**Sub-task**: [ATT-2869](https://atrainingtracker.atlassian.net/browse/ATT-2869) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.4`  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*)  
**Test Spec ID**: `TST-UI-272` (*Root Fallback Drawables Density Audit and Guarded Compose Loading Contract Verification*)  
**Branch**: `improvement/ATT-2858`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-312)

### 1.1 Problem Statement & Rationale
In production release 4.9.38.3 (build 266), Firebase Crashlytics registered fatal crash [ATT-2856](https://atrainingtracker.atlassian.net/browse/ATT-2856) (`android.content.res.Resources$NotFoundException: Resource ID #0x7f080087`, `R.drawable.bsport_run`) during `ControlTrackingScreen` initialization on devices running density-split APKs or non-standard DPI scalings.
A forensic resource audit revealed that 50 bitmap/raster drawables exist exclusively in density-specific folders (`drawable-mdpi`, `-hdpi`, `-xhdpi`, `-xxhdpi`, `-xxxhdpi`, `-nodpi`, `-ldpi`) without default counterparts in `app/src/main/res/drawable/`. Lacking a root fallback, Android's resource resolver throws `Resources$NotFoundException` when the target density bucket is absent, crashing unhandled `painterResource(id)` Compose calls on the main thread.

### 1.2 Functional & Architectural Requirements

1. **Universal Root Fallback Provision**:
   * Every bitmap and raster asset residing in any density-specific directory (`drawable-*`) SHALL have an identical counterpart in the root `app/src/main/res/drawable/` directory.
   * All 50 identified density-only drawables (`bsport_run.png`, `bsport_run_gray.png`, `bsport_bike.png`, `bsport_bike_gray.png`, `bsport_other.png`, `bsport_other_gray.png`, `bike_cad.png`, `bike_spd.png`, `bike_pwr.png`, `bike_speed_and_cadence.png`, `hr.png`, `temp.png`, `run_spd.png`, `bt_*.png`, `ant_logo.png`, `logo_protocol_bluetooth.png`, `connect_with_*.png`, `connected_with_*.png`, `stat_sys_battery_*.png`, `export_*.png`, etc.) SHALL be copied to `res/drawable/` from `drawable-xhdpi` (or `drawable-nodpi`).
2. **Defensive Compose Asset Resolution Helper**:
   * The system SHALL provide a defensive utility `safePainterResource(id: Int, fallback: ImageVector? = null): Painter` in `com.atrainingtracker.trainingtracker.ui.theme` (or `ui.core`).
   * The helper SHALL load the requested drawable using `ContextCompat.getDrawable(context, id)` inside a `try / catch (Resources.NotFoundException)` block.
   * If drawable loading fails or throws `Resources.NotFoundException`, the helper SHALL fall back to rendering the supplied `ImageVector` fallback (or an empty transparent painter) without crashing the UI thread.
3. **High-Risk Call Site Hardening**:
   * The system SHALL harden dynamic and density-sensitive Compose call sites using defensive loading:
     - `SportTypeSelector.kt` (`SportItem`): resolves `bsport_run`, `bsport_bike`, `bsport_other` defensively, providing vector fallbacks (`Icons.Default.DirectionsRun`, `DirectionsBike`, `FitnessCenter`).
     - `RemoteDevices.kt` (`DeviceIcon`): resolves sensor protocol icons (`bike_cad`, `hr`, etc.) safely.
     - `AntServicesStatusCard.kt`: resolves `ant_logo` safely.
     - `AntServicesStatusSheet.kt`: resolves `ant_logo` safely.
4. **Automated Density Fallback CI Guard**:
   * The test suite SHALL include an automated audit test `DrawableDensityFallbackAuditTest` that programmatically inspects all files in `app/src/main/res/drawable-*` and asserts that 100% of raster/bitmap assets exist in root `app/src/main/res/drawable/`.
5. **Resolution Precedence & Visual Invariants**:
   * Android's standard resource qualifier mechanism SHALL continue to prioritize density-specific assets on devices with matching screen densities.
   * Root fallback assets SHALL serve strictly as universal safety nets for APK splits and unmapped densities without altering asset dimensions, aspect ratios, or rendering quality on high-density devices.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Universal Fallback Presence)**:
  * *Given* the Android project resource directories,
  * *When* scanning all density-specific folders (`drawable-mdpi`, `-hdpi`, `-xhdpi`, `-xxhdpi`, `-xxxhdpi`, `-nodpi`),
  * *Then* every raster and bitmap file found in those folders SHALL have an exact counterpart in `app/src/main/res/drawable/`.
* **Criterion 2 (Safe Compose Loading Graceful Degradation)**:
  * *Given* a Jetpack Compose component attempting to render a resource ID that does not exist or fails resolution,
  * *When* `safePainterResource(invalidId, fallbackVector)` is invoked,
  * *Then* the component SHALL render the fallback vector without throwing `Resources$NotFoundException` and without crashing the main UI thread.
* **Criterion 3 (SportTypeSelector Resilience - Crash ATT-2856 Resolution)**:
  * *Given* a device running with any display density or density-split APK,
  * *When* `ControlTrackingScreen` renders `SportTypeSelector`,
  * *Then* all sport icons (`RUN`, `BIKE`, `OTHER`) SHALL render cleanly without crashing.
* **Criterion 4 (Automated Audit Test Verification)**:
  * *Given* `DrawableDensityFallbackAuditTest`,
  * *When* executed via `./gradlew testDebugUnitTest`,
  * *Then* the test SHALL pass with 0 missing fallback violations.

### 1.4 System Invariants

1. **Resolution Invariant**: High-density devices (`xxhdpi`, `xxxhdpi`) continue to load high-res assets from their respective density folders; root `res/drawable/` assets are loaded only when density folders are missing or stripped.
2. **Crash Invariant**: Zero unhandled `Resources$NotFoundException` exceptions thrown on UI threads during icon rendering.
3. **Hotfix Compatibility**: Changes are fully self-contained and apply cleanly to hotfix release `V4.9.38.4`.

---

## 2. Test Specification (TST-UI-272)

### Test Case 1: `testAllDensityDrawablesHaveRootFallback` (`TST-UI-272.1`)
* **Scope**: Automated Resource Architecture Audit Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/testing/DrawableDensityFallbackAuditTest.kt`
* **Preconditions**: Project `app/src/main/res` directory structure.
* **Action**:
  - Scan all subdirectories in `app/src/main/res` matching `drawable-*`.
  - Collect all unique image filenames (`.png`, `.jpg`, `.webp`, `.xml`).
  - Check for existence of each filename in `app/src/main/res/drawable/`.
* **Expected Result**: Set of missing fallback files is empty (`missingFiles.isEmpty() == true`).

### Test Case 2: `testSafePainter_whenResourceNotFound_returnsFallbackVectorWithoutCrashing` (`TST-UI-272.2`)
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/SafePainterResourceContractTest.kt`
* **Preconditions**: Safe painter utility function.
* **Action**:
  - Call safe painter helper with a non-existent resource ID (e.g. `0x7f08ffff`) and a fallback `ImageVector` (`Icons.Default.DirectionsRun`).
* **Expected Result**: Returns a valid non-null `Painter`, no `Resources$NotFoundException` is propagated.

### Test Case 3: `testSportItem_rendersDefensivelyAcrossAllSportTypes` (`TST-UI-272.3`)
* **Scope**: Component Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SportTypeSelectorContractTest.kt`
* **Preconditions**: All `BSportType` values (`BIKE`, `RUN`, `UNKNOWN`).
* **Action**:
  - Assert that `SportItem` and `SportTypeSelector` utilize defensive painter loading and that all drawables associated with `BSportType` exist in root `res/drawable/`.
* **Expected Result**: All sport drawables resolve successfully.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-272.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: **BUILD SUCCESSFUL**, 100% pass rate across the entire test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-272.1` | Resource Audit | `DrawableDensityFallbackAuditTest.kt` | `REQ-UI-312` Clause 1, 4 | Specified |
| `TST-UI-272.2` | Contract Test | `SafePainterResourceContractTest.kt` | `REQ-UI-312` Clause 2 | Specified |
| `TST-UI-272.3` | Contract Test | `SportTypeSelectorContractTest.kt` | `REQ-UI-312` Clause 3 | Specified |
| `TST-UI-272.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
